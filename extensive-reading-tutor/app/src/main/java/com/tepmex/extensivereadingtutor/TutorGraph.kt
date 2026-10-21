package com.tepmex.extensivereadingtutor

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tepmex.extensivereadingtutor.books.BookFileStore
import com.tepmex.extensivereadingtutor.books.BookService
import com.tepmex.extensivereadingtutor.books.EpubExtractor
import com.tepmex.extensivereadingtutor.domain.AppClock
import com.tepmex.extensivereadingtutor.domain.AppResult
import com.tepmex.extensivereadingtutor.domain.DomainError
import com.tepmex.extensivereadingtutor.domain.LlmCapabilities
import com.tepmex.extensivereadingtutor.domain.ModelSnapshot
import com.tepmex.extensivereadingtutor.domain.ModelSource
import com.tepmex.extensivereadingtutor.domain.ProviderConfig
import com.tepmex.extensivereadingtutor.domain.ProviderKind
import com.tepmex.extensivereadingtutor.domain.UuidIds
import com.tepmex.extensivereadingtutor.llm.LlmMessage
import com.tepmex.extensivereadingtutor.llm.LlmRequest
import com.tepmex.extensivereadingtutor.learner.LearnerService
import com.tepmex.extensivereadingtutor.lessons.LessonService
import com.tepmex.extensivereadingtutor.llm.GatewayLessonAi
import com.tepmex.extensivereadingtutor.llm.OkHttpTransport
import com.tepmex.extensivereadingtutor.llm.OpenAiCompatibleProvider
import com.tepmex.extensivereadingtutor.llm.RetryingCalls
import com.tepmex.extensivereadingtutor.security.AndroidKeystoreSecretStore
import com.tepmex.extensivereadingtutor.security.SecretStore
import com.tepmex.extensivereadingtutor.storage.room.TutorDatabase
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tutorSettings by preferencesDataStore(name = "tutor_settings")

class HoldingModel(initial: ModelSnapshot) : ModelSource {
    @Volatile var snapshot: ModelSnapshot = initial
    override fun current(): ModelSnapshot = snapshot
}

class TutorGraph(
    val books: BookService,
    val lessons: LessonService,
    val learner: LearnerService,
    val secrets: SecretStore,
    val models: HoldingModel,
    private val settings: SettingsStore,
    private val store: com.tepmex.extensivereadingtutor.storage.TutorStore,
) {
    suspend fun <T> read(block: (com.tepmex.extensivereadingtutor.storage.StoreSession) -> T): T = store.read(block)

    suspend fun hasApiKey(): Boolean = !secrets.get(DEFAULT_PROVIDER).isNullOrBlank()

    suspend fun loadSettings(): Settings = settings.read()

    suspend fun probeModel(): AppResult<String> {
        val current = settings.read()
        if (current.baseUrl.isBlank() || current.model.isBlank()) {
            return AppResult.Err(DomainError.ModelNotConfigured)
        }
        val key = secrets.get(DEFAULT_PROVIDER).orEmpty()
        val provider = OpenAiCompatibleProvider(LlmCapabilities(), current.baseUrl, key, OkHttpTransport())
        return when (
            val result = provider.generate(
                LlmRequest(
                    model = current.model,
                    messages = listOf(LlmMessage("user", "Reply with the single word ok.")),
                    temperature = 0.0,
                    maxOutputTokens = 16,
                    jsonObjectMode = false,
                    timeoutMillis = 20_000,
                ),
            )
        ) {
            is AppResult.Ok -> AppResult.Ok(result.value.content.trim())
            is AppResult.Err -> result
        }
    }

    suspend fun saveSettings(baseUrl: String, model: String, apiKey: String?) {
        settings.write(baseUrl.trim(), model.trim())
        val config = ProviderConfig(
            id = DEFAULT_PROVIDER,
            displayName = "OpenAI-compatible",
            kind = ProviderKind.OPENAI_COMPATIBLE,
            baseUrl = baseUrl.trim(),
            capabilities = LlmCapabilities(),
        )
        store.transaction { it.upsertProvider(config) }
        if (apiKey != null) secrets.put(DEFAULT_PROVIDER, apiKey)
        models.snapshot = ModelSnapshot(DEFAULT_PROVIDER, ProviderKind.OPENAI_COMPATIBLE.name, model.trim())
    }

    companion object {
        const val DEFAULT_PROVIDER = "default"

        fun create(context: Context): TutorGraph {
            val database = TutorDatabase.open(context)
            val store = com.tepmex.extensivereadingtutor.storage.room.RoomTutorStore(database)
            val clock = AppClock { Instant.now() }
            val ids = UuidIds()
            val secrets = AndroidKeystoreSecretStore(File(context.filesDir, "secrets"))
            val settings = SettingsStore(context)
            val models = HoldingModel(ModelSnapshot(DEFAULT_PROVIDER, ProviderKind.OPENAI_COMPATIBLE.name, ""))
            val http = OkHttpTransport()
            val ai = GatewayLessonAi(
                providers = { configId ->
                    val config = store.read { it.provider(configId) }
                    if (config == null) {
                        com.tepmex.extensivereadingtutor.domain.AppResult.Err(
                            com.tepmex.extensivereadingtutor.domain.DomainError.ModelNotConfigured,
                        )
                    } else {
                        val key = secrets.get(configId).orEmpty()
                        com.tepmex.extensivereadingtutor.domain.AppResult.Ok(
                            OpenAiCompatibleProvider(config.capabilities, config.baseUrl, key, http),
                        )
                    }
                },
                retry = RetryingCalls(sleeper = { kotlinx.coroutines.delay(it) }),
            )
            val learner = LearnerService(store, clock = clock, ids = ids)
            val files = DirectoryBookFiles(File(context.filesDir, "books"))
            return TutorGraph(
                books = BookService(store, files, EpubExtractor, clock, ids, learner),
                lessons = LessonService(store, ai, learner, clock, ids, models),
                learner = learner,
                secrets = secrets,
                models = models,
                settings = settings,
                store = store,
            )
        }
    }
}

class DirectoryBookFiles(private val root: File) : BookFileStore {
    override fun put(bookId: String, bytes: ByteArray): String {
        root.mkdirs()
        val file = File(root, "$bookId.epub")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    override fun delete(localRef: String) {
        File(localRef).delete()
    }
}

class SettingsStore(private val context: Context) {
    suspend fun read(): Settings {
        val prefs = context.tutorSettings.data.first()
        return Settings(
            baseUrl = prefs[BASE].orEmpty(),
            model = prefs[MODEL].orEmpty(),
        )
    }

    suspend fun write(baseUrl: String, model: String) {
        context.tutorSettings.edit {
            it[BASE] = baseUrl
            it[MODEL] = model
        }
    }

    fun modelFlow() = context.tutorSettings.data.map { it[MODEL].orEmpty() }

    private companion object {
        val BASE = stringPreferencesKey("base_url")
        val MODEL = stringPreferencesKey("model")
    }
}

data class Settings(val baseUrl: String, val model: String)

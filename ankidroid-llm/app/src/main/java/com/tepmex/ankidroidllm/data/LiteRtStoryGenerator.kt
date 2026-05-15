package com.tepmex.ankidroidllm.data

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File

class LiteRtStoryGenerator(private val context: Context) {

    suspend fun generate(
        modelPath: String,
        systemPrompt: String,
        userMessage: String,
        onToken: suspend (String) -> Unit,
    ) = withContext(Dispatchers.Default) {
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            cacheDir = context.cacheDir.absolutePath,
        )
        Engine(engineConfig).use { engine ->
            engine.initialize()
            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 0.85, seed = 0),
            )
            engine.createConversation(conversationConfig).use { conversation ->
                conversation.sendMessageAsync(userMessage)
                    .catch { throw it }
                    .cancellable()
                    .collect { message ->
                        val delta = textDelta(message)
                        if (delta.isNotEmpty()) {
                            onToken(delta)
                        }
                    }
            }
        }
    }

    fun localModelFile(context: Context): File {
        val dir = File(context.filesDir, "litert_models")
        dir.mkdirs()
        return File(dir, "gemma-4-e2b-it.litertlm")
    }

    private fun textDelta(message: Message): String {
        return message.contents.contents.joinToString("") { part ->
            (part as? Content.Text)?.text.orEmpty()
        }
    }
}

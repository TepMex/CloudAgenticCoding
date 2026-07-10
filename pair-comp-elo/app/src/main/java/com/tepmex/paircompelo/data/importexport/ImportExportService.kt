package com.tepmex.paircompelo.data.importexport

import androidx.room.withTransaction
import com.tepmex.paircompelo.core.AppClock
import com.tepmex.paircompelo.data.dao.ItemComparisonDao
import com.tepmex.paircompelo.data.dao.ListComparisonDao
import com.tepmex.paircompelo.data.dao.PreferenceItemDao
import com.tepmex.paircompelo.data.dao.PreferenceListDao
import com.tepmex.paircompelo.data.db.PairCompEloDatabase
import com.tepmex.paircompelo.data.mapper.toDomain
import com.tepmex.paircompelo.data.mapper.toEntity
import com.tepmex.paircompelo.data.prefs.SettingsDataStore
import com.tepmex.paircompelo.data.repository.PreferenceRepository
import com.tepmex.paircompelo.domain.model.ComparisonOutcome
import com.tepmex.paircompelo.domain.model.ItemComparison
import com.tepmex.paircompelo.domain.model.ListComparison
import com.tepmex.paircompelo.domain.model.PairSelectionStrategy
import com.tepmex.paircompelo.domain.model.PreferenceItem
import com.tepmex.paircompelo.domain.model.PreferenceList
import com.tepmex.paircompelo.domain.model.RankingSettings
import com.tepmex.paircompelo.domain.validation.NameValidator
import com.tepmex.paircompelo.domain.validation.SettingsValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ExportBundle(
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: String,
    val settings: SettingsDto,
    val lists: List<ListDto>,
    val items: List<ItemDto>,
    val itemComparisons: List<ItemComparisonDto>,
    val listComparisons: List<ListComparisonDto>,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class SettingsDto(
    val initialRating: Double,
    val kFactor: Double,
    val ratingScale: Double,
    val decayEnabled: Boolean,
    val decayRatePerDay: Double,
    val minimumComparisonsBeforeStable: Int,
    val pairSelectionStrategy: String,
    val allowDraws: Boolean,
    val allowSkipping: Boolean,
    val showRatingsDuringComparison: Boolean,
)

@Serializable
data class ListDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String? = null,
    val rating: Double,
    val ratingUpdatedAt: String,
    val comparisonCount: Int,
)

@Serializable
data class ItemDto(
    val id: String,
    val listId: String,
    val name: String,
    val description: String? = null,
    val notes: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String? = null,
    val rating: Double,
    val ratingUpdatedAt: String,
    val comparisonCount: Int,
    val winCount: Int,
    val lossCount: Int,
    val skipCount: Int,
    val sortOrder: Int = 0,
)

@Serializable
data class ItemComparisonDto(
    val id: String,
    val listId: String,
    val leftItemId: String,
    val rightItemId: String,
    val winnerItemId: String? = null,
    val outcome: String,
    val comparedAt: String,
    val leftRatingBefore: Double,
    val rightRatingBefore: Double,
    val leftRatingAfter: Double,
    val rightRatingAfter: Double,
    val kFactorUsed: Double,
    val decayFactorUsed: Double,
    val isReverted: Boolean = false,
)

@Serializable
data class ListComparisonDto(
    val id: String,
    val leftListId: String,
    val rightListId: String,
    val winnerListId: String? = null,
    val outcome: String,
    val comparedAt: String,
    val leftRatingBefore: Double,
    val rightRatingBefore: Double,
    val leftRatingAfter: Double,
    val rightRatingAfter: Double,
    val kFactorUsed: Double,
    val decayFactorUsed: Double,
    val isReverted: Boolean = false,
)

enum class ImportMode { REPLACE, MERGE }

data class ImportReport(
    val listsImported: Int,
    val itemsImported: Int,
    val itemComparisonsImported: Int,
    val listComparisonsImported: Int,
    val skipped: List<String>,
)

@Singleton
class ImportExportService @Inject constructor(
    private val db: PairCompEloDatabase,
    private val listDao: PreferenceListDao,
    private val itemDao: PreferenceItemDao,
    private val itemComparisonDao: ItemComparisonDao,
    private val listComparisonDao: ListComparisonDao,
    private val settingsDataStore: SettingsDataStore,
    private val repository: PreferenceRepository,
    private val clock: AppClock,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val settings = settingsDataStore.settings.first()
        val lists = listDao.getAll().map { it.toDomain() }
        val items = itemDao.getAll().map { it.toDomain() }
        val itemComparisons = itemComparisonDao.getAll().map { it.toDomain() }
        val listComparisons = listComparisonDao.getAll().map { it.toDomain() }
        val bundle = ExportBundle(
            exportedAt = clock.now().toString(),
            settings = settings.toDto(),
            lists = lists.map { it.toDto() },
            items = items.map { it.toDto() },
            itemComparisons = itemComparisons.map { it.toDto() },
            listComparisons = listComparisons.map { it.toDto() },
        )
        json.encodeToString(ExportBundle.serializer(), bundle)
    }

    suspend fun importJson(raw: String, mode: ImportMode): ImportReport = withContext(Dispatchers.IO) {
        val bundle = try {
            json.decodeFromString(ExportBundle.serializer(), raw)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed JSON: ${e.message}", e)
        }
        if (bundle.schemaVersion > ExportBundle.SCHEMA_VERSION) {
            throw IllegalArgumentException(
                "Unsupported schema version ${bundle.schemaVersion}",
            )
        }

        val skipped = mutableListOf<String>()
        val parsedLists = mutableListOf<PreferenceList>()
        val parsedItems = mutableListOf<PreferenceItem>()
        val parsedItemComparisons = mutableListOf<ItemComparison>()
        val parsedListComparisons = mutableListOf<ListComparison>()

        for (dto in bundle.lists) {
            runCatching { dto.toDomain() }
                .onSuccess { parsedLists += it }
                .onFailure { skipped += "list ${dto.id}: ${it.message}" }
        }
        val listIds = parsedLists.map { it.id }.toSet()

        for (dto in bundle.items) {
            runCatching {
                val item = dto.toDomain()
                require(item.listId in listIds) { "unknown listId" }
                item
            }.onSuccess { parsedItems += it }
                .onFailure { skipped += "item ${dto.id}: ${it.message}" }
        }
        val itemIds = parsedItems.map { it.id }.toSet()

        for (dto in bundle.itemComparisons) {
            runCatching {
                val c = dto.toDomain()
                require(c.listId in listIds) { "unknown listId" }
                require(c.leftItemId in itemIds && c.rightItemId in itemIds) { "unknown item ref" }
                require(c.leftItemId != c.rightItemId) { "entities must be distinct" }
                c
            }.onSuccess { parsedItemComparisons += it }
                .onFailure { skipped += "itemComparison ${dto.id}: ${it.message}" }
        }

        for (dto in bundle.listComparisons) {
            runCatching {
                val c = dto.toDomain()
                require(c.leftListId in listIds && c.rightListId in listIds) { "unknown list ref" }
                require(c.leftListId != c.rightListId) { "entities must be distinct" }
                c
            }.onSuccess { parsedListComparisons += it }
                .onFailure { skipped += "listComparison ${dto.id}: ${it.message}" }
        }

        val settings = runCatching { bundle.settings.toDomain() }
            .getOrElse {
                skipped += "settings: ${it.message}"
                RankingSettings.Defaults
            }

        db.withTransaction {
            when (mode) {
                ImportMode.REPLACE -> {
                    itemComparisonDao.deleteAll()
                    listComparisonDao.deleteAll()
                    itemDao.deleteAll()
                    listDao.deleteAll()
                    listDao.upsertAll(parsedLists.map { it.toEntity() })
                    itemDao.upsertAll(parsedItems.map { it.toEntity() })
                    itemComparisonDao.upsertAll(parsedItemComparisons.map { it.toEntity() })
                    listComparisonDao.upsertAll(parsedListComparisons.map { it.toEntity() })
                }
                ImportMode.MERGE -> {
                    val existingListIds = listDao.getAll().map { it.id }.toSet()
                    val existingItemIds = itemDao.getAll().map { it.id }.toSet()
                    val existingItemCompIds = itemComparisonDao.getAll().map { it.id }.toSet()
                    val existingListCompIds = listComparisonDao.getAll().map { it.id }.toSet()

                    val newLists = parsedLists.filter { it.id.toString() !in existingListIds }
                    val newItems = parsedItems.filter { it.id.toString() !in existingItemIds }
                    val newItemComps = parsedItemComparisons.filter { it.id.toString() !in existingItemCompIds }
                    val newListComps = parsedListComparisons.filter { it.id.toString() !in existingListCompIds }

                    parsedLists.filter { it.id.toString() in existingListIds }
                        .forEach { skipped += "list ${it.id}: already exists (merge skip)" }
                    parsedItems.filter { it.id.toString() in existingItemIds }
                        .forEach { skipped += "item ${it.id}: already exists (merge skip)" }

                    listDao.upsertAll(newLists.map { it.toEntity() })
                    itemDao.upsertAll(newItems.map { it.toEntity() })
                    itemComparisonDao.upsertAll(newItemComps.map { it.toEntity() })
                    listComparisonDao.upsertAll(newListComps.map { it.toEntity() })
                }
            }
        }

        settingsDataStore.update(settings)
        repository.recalculateAllRankings()

        ImportReport(
            listsImported = parsedLists.size,
            itemsImported = parsedItems.size,
            itemComparisonsImported = parsedItemComparisons.size,
            listComparisonsImported = parsedListComparisons.size,
            skipped = skipped,
        )
    }
}

private fun RankingSettings.toDto() = SettingsDto(
    initialRating = initialRating,
    kFactor = kFactor,
    ratingScale = ratingScale,
    decayEnabled = decayEnabled,
    decayRatePerDay = decayRatePerDay,
    minimumComparisonsBeforeStable = minimumComparisonsBeforeStable,
    pairSelectionStrategy = pairSelectionStrategy.name,
    allowDraws = allowDraws,
    allowSkipping = allowSkipping,
    showRatingsDuringComparison = showRatingsDuringComparison,
)

private fun SettingsDto.toDomain(): RankingSettings {
    val settings = RankingSettings(
        initialRating = initialRating,
        kFactor = kFactor,
        ratingScale = ratingScale,
        decayEnabled = decayEnabled,
        decayRatePerDay = decayRatePerDay,
        minimumComparisonsBeforeStable = minimumComparisonsBeforeStable,
        pairSelectionStrategy = PairSelectionStrategy.valueOf(pairSelectionStrategy),
        allowDraws = allowDraws,
        allowSkipping = allowSkipping,
        showRatingsDuringComparison = showRatingsDuringComparison,
    )
    return SettingsValidator.validate(settings).getOrThrow()
}

private fun PreferenceList.toDto() = ListDto(
    id = id.toString(),
    name = name,
    description = description,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    archivedAt = archivedAt?.toString(),
    rating = rating,
    ratingUpdatedAt = ratingUpdatedAt.toString(),
    comparisonCount = comparisonCount,
)

private fun ListDto.toDomain(): PreferenceList {
    val name = NameValidator.normalizeName(name).getOrThrow()
    return PreferenceList(
        id = UUID.fromString(id),
        name = name,
        description = NameValidator.normalizeOptionalText(
            description,
            NameValidator.MAX_DESCRIPTION_LENGTH,
        ).getOrThrow(),
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        archivedAt = archivedAt?.let(Instant::parse),
        rating = rating,
        ratingUpdatedAt = Instant.parse(ratingUpdatedAt),
        comparisonCount = comparisonCount,
    )
}

private fun PreferenceItem.toDto() = ItemDto(
    id = id.toString(),
    listId = listId.toString(),
    name = name,
    description = description,
    notes = notes,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    archivedAt = archivedAt?.toString(),
    rating = rating,
    ratingUpdatedAt = ratingUpdatedAt.toString(),
    comparisonCount = comparisonCount,
    winCount = winCount,
    lossCount = lossCount,
    skipCount = skipCount,
    sortOrder = sortOrder,
)

private fun ItemDto.toDomain(): PreferenceItem = PreferenceItem(
    id = UUID.fromString(id),
    listId = UUID.fromString(listId),
    name = NameValidator.normalizeName(name).getOrThrow(),
    description = NameValidator.normalizeOptionalText(
        description,
        NameValidator.MAX_DESCRIPTION_LENGTH,
    ).getOrThrow(),
    notes = NameValidator.normalizeOptionalText(notes, NameValidator.MAX_NOTES_LENGTH).getOrThrow(),
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt),
    archivedAt = archivedAt?.let(Instant::parse),
    rating = rating,
    ratingUpdatedAt = Instant.parse(ratingUpdatedAt),
    comparisonCount = comparisonCount,
    winCount = winCount,
    lossCount = lossCount,
    skipCount = skipCount,
    sortOrder = sortOrder,
)

private fun ItemComparison.toDto() = ItemComparisonDto(
    id = id.toString(),
    listId = listId.toString(),
    leftItemId = leftItemId.toString(),
    rightItemId = rightItemId.toString(),
    winnerItemId = winnerItemId?.toString(),
    outcome = outcome.name,
    comparedAt = comparedAt.toString(),
    leftRatingBefore = leftRatingBefore,
    rightRatingBefore = rightRatingBefore,
    leftRatingAfter = leftRatingAfter,
    rightRatingAfter = rightRatingAfter,
    kFactorUsed = kFactorUsed,
    decayFactorUsed = decayFactorUsed,
    isReverted = isReverted,
)

private fun ItemComparisonDto.toDomain(): ItemComparison = ItemComparison(
    id = UUID.fromString(id),
    listId = UUID.fromString(listId),
    leftItemId = UUID.fromString(leftItemId),
    rightItemId = UUID.fromString(rightItemId),
    winnerItemId = winnerItemId?.let(UUID::fromString),
    outcome = ComparisonOutcome.valueOf(outcome),
    comparedAt = Instant.parse(comparedAt),
    leftRatingBefore = leftRatingBefore,
    rightRatingBefore = rightRatingBefore,
    leftRatingAfter = leftRatingAfter,
    rightRatingAfter = rightRatingAfter,
    kFactorUsed = kFactorUsed,
    decayFactorUsed = decayFactorUsed,
    isReverted = isReverted,
)

private fun ListComparison.toDto() = ListComparisonDto(
    id = id.toString(),
    leftListId = leftListId.toString(),
    rightListId = rightListId.toString(),
    winnerListId = winnerListId?.toString(),
    outcome = outcome.name,
    comparedAt = comparedAt.toString(),
    leftRatingBefore = leftRatingBefore,
    rightRatingBefore = rightRatingBefore,
    leftRatingAfter = leftRatingAfter,
    rightRatingAfter = rightRatingAfter,
    kFactorUsed = kFactorUsed,
    decayFactorUsed = decayFactorUsed,
    isReverted = isReverted,
)

private fun ListComparisonDto.toDomain(): ListComparison = ListComparison(
    id = UUID.fromString(id),
    leftListId = UUID.fromString(leftListId),
    rightListId = UUID.fromString(rightListId),
    winnerListId = winnerListId?.let(UUID::fromString),
    outcome = ComparisonOutcome.valueOf(outcome),
    comparedAt = Instant.parse(comparedAt),
    leftRatingBefore = leftRatingBefore,
    rightRatingBefore = rightRatingBefore,
    leftRatingAfter = leftRatingAfter,
    rightRatingAfter = rightRatingAfter,
    kFactorUsed = kFactorUsed,
    decayFactorUsed = decayFactorUsed,
    isReverted = isReverted,
)

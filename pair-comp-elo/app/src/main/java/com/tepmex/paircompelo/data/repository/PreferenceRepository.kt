package com.tepmex.paircompelo.data.repository

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
import com.tepmex.paircompelo.domain.elo.ComparisonEngine
import com.tepmex.paircompelo.domain.model.ComparisonOutcome
import com.tepmex.paircompelo.domain.model.ItemComparison
import com.tepmex.paircompelo.domain.model.ListComparison
import com.tepmex.paircompelo.domain.model.PreferenceItem
import com.tepmex.paircompelo.domain.model.PreferenceList
import com.tepmex.paircompelo.domain.model.Rateable
import com.tepmex.paircompelo.domain.model.RankingSettings
import com.tepmex.paircompelo.domain.pairing.PairSelector
import com.tepmex.paircompelo.domain.ranking.RankingRecalculator
import com.tepmex.paircompelo.domain.validation.NameValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ListSummary(
    val list: PreferenceList,
    val activeItemCount: Int,
    val itemComparisonCount: Int,
    val topItemName: String?,
    val topItemRating: Double?,
)

@Singleton
class PreferenceRepository @Inject constructor(
    private val db: PairCompEloDatabase,
    private val listDao: PreferenceListDao,
    private val itemDao: PreferenceItemDao,
    private val itemComparisonDao: ItemComparisonDao,
    private val listComparisonDao: ListComparisonDao,
    private val settingsDataStore: SettingsDataStore,
    private val clock: AppClock,
    private val pairSelector: PairSelector,
) {
    private val _recalculationProgress = MutableStateFlow<RecalculationProgress?>(null)
    val recalculationProgress: StateFlow<RecalculationProgress?> = _recalculationProgress.asStateFlow()

    fun observeActiveLists(): Flow<List<PreferenceList>> =
        listDao.observeActive().map { rows -> rows.map { it.toDomain() } }

    fun observeArchivedLists(): Flow<List<PreferenceList>> =
        listDao.observeArchived().map { rows -> rows.map { it.toDomain() } }

    fun observeList(id: UUID): Flow<PreferenceList?> =
        listDao.observeById(id.toString()).map { it?.toDomain() }

    fun observeActiveItems(listId: UUID): Flow<List<PreferenceItem>> =
        itemDao.observeActiveByList(listId.toString()).map { rows -> rows.map { it.toDomain() } }

    fun observeItems(listId: UUID): Flow<List<PreferenceItem>> =
        itemDao.observeByList(listId.toString()).map { rows -> rows.map { it.toDomain() } }

    fun observeItem(id: UUID): Flow<PreferenceItem?> =
        itemDao.observeById(id.toString()).map { it?.toDomain() }

    fun observeItemComparisons(listId: UUID, limit: Int = 100, offset: Int = 0): Flow<List<ItemComparison>> =
        itemComparisonDao.observeByList(listId.toString(), limit, offset)
            .map { rows -> rows.map { it.toDomain() } }

    fun observeAllItemComparisons(limit: Int = 200, offset: Int = 0): Flow<List<ItemComparison>> =
        itemComparisonDao.observeAll(limit, offset).map { rows -> rows.map { it.toDomain() } }

    fun observeListComparisons(limit: Int = 200, offset: Int = 0): Flow<List<ListComparison>> =
        listComparisonDao.observeAll(limit, offset).map { rows -> rows.map { it.toDomain() } }

    fun observeItemComparisonCount(listId: UUID): Flow<Int> =
        itemComparisonDao.observeCountByList(listId.toString())

    fun observeActiveItemCount(listId: UUID): Flow<Int> =
        itemDao.observeActiveCount(listId.toString())

    fun observeTopItem(listId: UUID): Flow<PreferenceItem?> =
        itemDao.observeTopRated(listId.toString()).map { it?.toDomain() }

    fun observeSettings(): Flow<RankingSettings> = settingsDataStore.settings

    suspend fun createList(name: String, description: String?): PreferenceList {
        val now = clock.now()
        val settings = settingsDataStore.settings.first()
        val list = PreferenceList(
            id = UUID.randomUUID(),
            name = NameValidator.normalizeName(name).getOrThrow(),
            description = NameValidator.normalizeOptionalText(
                description,
                NameValidator.MAX_DESCRIPTION_LENGTH,
            ).getOrThrow(),
            createdAt = now,
            updatedAt = now,
            rating = settings.initialRating,
            ratingUpdatedAt = now,
        )
        listDao.insert(list.toEntity())
        return list
    }

    suspend fun updateList(id: UUID, name: String, description: String?) {
        val existing = listDao.getById(id.toString())?.toDomain()
            ?: error("List not found")
        val updated = existing.copy(
            name = NameValidator.normalizeName(name).getOrThrow(),
            description = NameValidator.normalizeOptionalText(
                description,
                NameValidator.MAX_DESCRIPTION_LENGTH,
            ).getOrThrow(),
            updatedAt = clock.now(),
        )
        listDao.update(updated.toEntity())
    }

    suspend fun archiveList(id: UUID) {
        val existing = listDao.getById(id.toString())?.toDomain() ?: error("List not found")
        listDao.update(existing.copy(archivedAt = clock.now(), updatedAt = clock.now()).toEntity())
    }

    suspend fun restoreList(id: UUID) {
        val existing = listDao.getById(id.toString())?.toDomain() ?: error("List not found")
        listDao.update(existing.copy(archivedAt = null, updatedAt = clock.now()).toEntity())
    }

    suspend fun deleteList(id: UUID) {
        listDao.deleteById(id.toString())
    }

    suspend fun createItem(
        listId: UUID,
        name: String,
        description: String?,
        notes: String?,
    ): PreferenceItem {
        val list = listDao.getById(listId.toString()) ?: error("List not found")
        require(list.archivedAt == null) { "Cannot add items to an archived list" }
        val now = clock.now()
        val settings = settingsDataStore.settings.first()
        val sortOrder = itemDao.maxSortOrder(listId.toString()) + 1
        val item = PreferenceItem(
            id = UUID.randomUUID(),
            listId = listId,
            name = NameValidator.normalizeName(name).getOrThrow(),
            description = NameValidator.normalizeOptionalText(
                description,
                NameValidator.MAX_DESCRIPTION_LENGTH,
            ).getOrThrow(),
            notes = NameValidator.normalizeOptionalText(
                notes,
                NameValidator.MAX_NOTES_LENGTH,
            ).getOrThrow(),
            createdAt = now,
            updatedAt = now,
            rating = settings.initialRating,
            ratingUpdatedAt = now,
            sortOrder = sortOrder,
        )
        itemDao.insert(item.toEntity())
        listDao.update(
            list.copy(updatedAt = now.toEpochMilli()).let {
                // keep other fields
                listDao.getById(listId.toString())!!.copy(updatedAt = now.toEpochMilli())
            },
        )
        return item
    }

    suspend fun updateItem(id: UUID, name: String, description: String?, notes: String?) {
        val existing = itemDao.getById(id.toString())?.toDomain() ?: error("Item not found")
        val updated = existing.copy(
            name = NameValidator.normalizeName(name).getOrThrow(),
            description = NameValidator.normalizeOptionalText(
                description,
                NameValidator.MAX_DESCRIPTION_LENGTH,
            ).getOrThrow(),
            notes = NameValidator.normalizeOptionalText(
                notes,
                NameValidator.MAX_NOTES_LENGTH,
            ).getOrThrow(),
            updatedAt = clock.now(),
        )
        itemDao.update(updated.toEntity())
    }

    suspend fun archiveItem(id: UUID) {
        val existing = itemDao.getById(id.toString())?.toDomain() ?: error("Item not found")
        itemDao.update(existing.copy(archivedAt = clock.now(), updatedAt = clock.now()).toEntity())
    }

    suspend fun restoreItem(id: UUID) {
        val existing = itemDao.getById(id.toString())?.toDomain() ?: error("Item not found")
        itemDao.update(existing.copy(archivedAt = null, updatedAt = clock.now()).toEntity())
    }

    suspend fun deleteItem(id: UUID) {
        // Deleting an item cascades its comparisons; recalculate remaining items in the list.
        val existing = itemDao.getById(id.toString())?.toDomain() ?: return
        val listId = existing.listId
        db.withTransaction {
            itemDao.deleteById(id.toString())
        }
        recalculateItemRankings(listId)
    }

    suspend fun reorderItems(listId: UUID, orderedIds: List<UUID>) {
        val items = itemDao.getByList(listId.toString()).map { it.toDomain() }
        val byId = items.associateBy { it.id }
        val now = clock.now()
        val updated = orderedIds.mapIndexedNotNull { index, id ->
            byId[id]?.copy(sortOrder = index, updatedAt = now)?.toEntity()
        }
        itemDao.updateAll(updated)
    }

    suspend fun selectNextItemPair(listId: UUID): Pair<PreferenceItem, PreferenceItem>? {
        val settings = settingsDataStore.settings.first()
        val items = itemDao.getActiveByList(listId.toString()).map { it.toDomain() }
        if (items.size < 2) return null
        val rateables = items.map {
            Rateable(it.id, it.rating, it.ratingUpdatedAt, it.comparisonCount)
        }
        val recent = itemComparisonDao.getRecentByList(listId.toString(), 20)
        val recentKeys = recent.map {
            PairSelector.pairKey(UUID.fromString(it.leftItemId), UUID.fromString(it.rightItemId))
        }
        val h2h = mutableMapOf<String, Int>()
        for (i in items.indices) {
            for (j in i + 1 until items.size) {
                val key = PairSelector.pairKey(items[i].id, items[j].id)
                val count = itemComparisonDao.headToHeadCount(
                    listId.toString(),
                    items[i].id.toString(),
                    items[j].id.toString(),
                )
                if (count > 0) h2h[key] = count
            }
        }
        val pair = pairSelector.select(
            rateables,
            settings.pairSelectionStrategy,
            PairSelector.PairHistory(recentKeys, h2h),
        ) ?: return null
        val left = items.first { it.id == pair.left.id }
        val right = items.first { it.id == pair.right.id }
        return left to right
    }

    suspend fun selectNextListPair(): Pair<PreferenceList, PreferenceList>? {
        val settings = settingsDataStore.settings.first()
        val lists = listDao.getActive().map { it.toDomain() }
        if (lists.size < 2) return null
        val rateables = lists.map {
            Rateable(it.id, it.rating, it.ratingUpdatedAt, it.comparisonCount)
        }
        val recent = listComparisonDao.getRecent(20)
        val recentKeys = recent.map {
            PairSelector.pairKey(UUID.fromString(it.leftListId), UUID.fromString(it.rightListId))
        }
        val h2h = mutableMapOf<String, Int>()
        for (i in lists.indices) {
            for (j in i + 1 until lists.size) {
                val key = PairSelector.pairKey(lists[i].id, lists[j].id)
                val count = listComparisonDao.headToHeadCount(
                    lists[i].id.toString(),
                    lists[j].id.toString(),
                )
                if (count > 0) h2h[key] = count
            }
        }
        val pair = pairSelector.select(
            rateables,
            settings.pairSelectionStrategy,
            PairSelector.PairHistory(recentKeys, h2h),
        ) ?: return null
        val left = lists.first { it.id == pair.left.id }
        val right = lists.first { it.id == pair.right.id }
        return left to right
    }

    suspend fun recordItemComparison(
        listId: UUID,
        leftItemId: UUID,
        rightItemId: UUID,
        outcome: ComparisonOutcome,
    ): ItemComparison = withContext(Dispatchers.IO) {
        require(leftItemId != rightItemId) { "Comparison entities must be distinct" }
        val settings = settingsDataStore.settings.first()
        if (outcome == ComparisonOutcome.DRAW) require(settings.allowDraws) { "Draws are disabled" }
        if (outcome == ComparisonOutcome.SKIPPED) require(settings.allowSkipping) { "Skipping is disabled" }

        db.withTransaction {
            val left = itemDao.getById(leftItemId.toString())?.toDomain()
                ?: error("Left item not found")
            val right = itemDao.getById(rightItemId.toString())?.toDomain()
                ?: error("Right item not found")
            require(left.listId == listId && right.listId == listId) {
                "Entities in an item comparison must belong to the same list"
            }
            require(!left.isArchived && !right.isArchived) {
                "Archived entities must not appear in new comparisons"
            }
            val list = listDao.getById(listId.toString())?.toDomain()
                ?: error("List not found")
            require(!list.isArchived) { "Cannot compare items in an archived list" }

            val now = clock.now()
            val engineResult = ComparisonEngine.apply(
                left = ComparisonEngine.Participant(left.id, left.rating, left.ratingUpdatedAt),
                right = ComparisonEngine.Participant(right.id, right.rating, right.ratingUpdatedAt),
                outcome = outcome,
                settings = settings,
                comparedAt = now,
            )

            var leftNext = left.copy(
                rating = engineResult.leftRatingAfter,
                ratingUpdatedAt = now,
                comparisonCount = left.comparisonCount + 1,
                updatedAt = now,
            )
            var rightNext = right.copy(
                rating = engineResult.rightRatingAfter,
                ratingUpdatedAt = now,
                comparisonCount = right.comparisonCount + 1,
                updatedAt = now,
            )
            when (outcome) {
                ComparisonOutcome.LEFT_WINS -> {
                    leftNext = leftNext.copy(winCount = leftNext.winCount + 1)
                    rightNext = rightNext.copy(lossCount = rightNext.lossCount + 1)
                }
                ComparisonOutcome.RIGHT_WINS -> {
                    rightNext = rightNext.copy(winCount = rightNext.winCount + 1)
                    leftNext = leftNext.copy(lossCount = leftNext.lossCount + 1)
                }
                ComparisonOutcome.DRAW -> Unit
                ComparisonOutcome.SKIPPED -> {
                    leftNext = leftNext.copy(skipCount = leftNext.skipCount + 1)
                    rightNext = rightNext.copy(skipCount = rightNext.skipCount + 1)
                }
            }

            val comparison = ItemComparison(
                id = UUID.randomUUID(),
                listId = listId,
                leftItemId = leftItemId,
                rightItemId = rightItemId,
                winnerItemId = engineResult.winnerId,
                outcome = outcome,
                comparedAt = now,
                leftRatingBefore = engineResult.leftRatingBefore,
                rightRatingBefore = engineResult.rightRatingBefore,
                leftRatingAfter = engineResult.leftRatingAfter,
                rightRatingAfter = engineResult.rightRatingAfter,
                kFactorUsed = engineResult.kFactorUsed,
                decayFactorUsed = engineResult.decayFactorUsed,
            )

            itemDao.update(leftNext.toEntity())
            itemDao.update(rightNext.toEntity())
            itemComparisonDao.insert(comparison.toEntity())
            listDao.update(list.copy(updatedAt = now).toEntity())
            comparison
        }
    }

    suspend fun recordListComparison(
        leftListId: UUID,
        rightListId: UUID,
        outcome: ComparisonOutcome,
    ): ListComparison = withContext(Dispatchers.IO) {
        require(leftListId != rightListId) { "Comparison entities must be distinct" }
        val settings = settingsDataStore.settings.first()
        if (outcome == ComparisonOutcome.DRAW) require(settings.allowDraws) { "Draws are disabled" }
        if (outcome == ComparisonOutcome.SKIPPED) require(settings.allowSkipping) { "Skipping is disabled" }

        db.withTransaction {
            val left = listDao.getById(leftListId.toString())?.toDomain()
                ?: error("Left list not found")
            val right = listDao.getById(rightListId.toString())?.toDomain()
                ?: error("Right list not found")
            require(!left.isArchived && !right.isArchived) {
                "Archived entities must not appear in new comparisons"
            }

            val now = clock.now()
            val engineResult = ComparisonEngine.apply(
                left = ComparisonEngine.Participant(left.id, left.rating, left.ratingUpdatedAt),
                right = ComparisonEngine.Participant(right.id, right.rating, right.ratingUpdatedAt),
                outcome = outcome,
                settings = settings,
                comparedAt = now,
            )

            val leftNext = left.copy(
                rating = engineResult.leftRatingAfter,
                ratingUpdatedAt = now,
                comparisonCount = left.comparisonCount + 1,
                updatedAt = now,
            )
            val rightNext = right.copy(
                rating = engineResult.rightRatingAfter,
                ratingUpdatedAt = now,
                comparisonCount = right.comparisonCount + 1,
                updatedAt = now,
            )

            val comparison = ListComparison(
                id = UUID.randomUUID(),
                leftListId = leftListId,
                rightListId = rightListId,
                winnerListId = engineResult.winnerId,
                outcome = outcome,
                comparedAt = now,
                leftRatingBefore = engineResult.leftRatingBefore,
                rightRatingBefore = engineResult.rightRatingBefore,
                leftRatingAfter = engineResult.leftRatingAfter,
                rightRatingAfter = engineResult.rightRatingAfter,
                kFactorUsed = engineResult.kFactorUsed,
                decayFactorUsed = engineResult.decayFactorUsed,
            )

            listDao.update(leftNext.toEntity())
            listDao.update(rightNext.toEntity())
            listComparisonDao.insert(comparison.toEntity())
            comparison
        }
    }

    suspend fun undoLatestItemComparison(listId: UUID) {
        val latest = itemComparisonDao.getLatestByList(listId.toString()) ?: return
        db.withTransaction {
            itemComparisonDao.markReverted(latest.id)
        }
        recalculateItemRankings(listId)
    }

    suspend fun undoLatestListComparison() {
        val latest = listComparisonDao.getLatest() ?: return
        db.withTransaction {
            listComparisonDao.markReverted(latest.id)
        }
        recalculateListRankings()
    }

    suspend fun deleteItemComparison(id: UUID) {
        val existing = itemComparisonDao.getById(id.toString()) ?: return
        val listId = UUID.fromString(existing.listId)
        db.withTransaction {
            itemComparisonDao.deleteById(id.toString())
        }
        recalculateItemRankings(listId)
    }

    suspend fun deleteListComparison(id: UUID) {
        listComparisonDao.getById(id.toString()) ?: return
        db.withTransaction {
            listComparisonDao.deleteById(id.toString())
        }
        recalculateListRankings()
    }

    suspend fun recalculateAllRankings() = withContext(Dispatchers.Default) {
        _recalculationProgress.value = RecalculationProgress(0f, "Recalculating item rankings…")
        val listIds = listDao.getAll().map { UUID.fromString(it.id) }
        listIds.forEachIndexed { index, listId ->
            recalculateItemRankings(listId)
            _recalculationProgress.value = RecalculationProgress(
                (index + 1).toFloat() / (listIds.size + 1).coerceAtLeast(1),
                "Recalculating item rankings…",
            )
        }
        _recalculationProgress.value = RecalculationProgress(0.95f, "Recalculating list rankings…")
        recalculateListRankings()
        _recalculationProgress.value = null
    }

    suspend fun recalculateItemRankings(listId: UUID) = withContext(Dispatchers.Default) {
        val settings = settingsDataStore.settings.first()
        val items = itemDao.getByList(listId.toString()).map { it.toDomain() }
        val comparisons = itemComparisonDao.getActiveByListChronological(listId.toString())
            .map { it.toDomain() }
        val result = RankingRecalculator.recalculateItems(
            items = items,
            comparisons = comparisons,
            settings = settings,
            createdAtFallback = clock.now(),
        )
        db.withTransaction {
            itemDao.updateAll(result.items.map { it.toEntity() })
            // Rewrite rating snapshots on active comparisons
            val byId = result.comparisons.associateBy { it.id }
            val entities = itemComparisonDao.getActiveByListChronological(listId.toString()).map { entity ->
                val rewritten = byId[UUID.fromString(entity.id)]
                if (rewritten != null) rewritten.toEntity() else entity
            }
            itemComparisonDao.updateAll(entities)
        }
    }

    suspend fun recalculateListRankings() = withContext(Dispatchers.Default) {
        val settings = settingsDataStore.settings.first()
        val lists = listDao.getAll().map { it.toDomain() }
        val comparisons = listComparisonDao.getAllActiveChronological().map { it.toDomain() }
        val result = RankingRecalculator.recalculateLists(
            lists = lists,
            comparisons = comparisons,
            settings = settings,
            createdAtFallback = clock.now(),
        )
        db.withTransaction {
            listDao.updateAll(result.lists.map { it.toEntity() })
            val byId = result.comparisons.associateBy { it.id }
            val entities = listComparisonDao.getAllActiveChronological().map { entity ->
                val rewritten = byId[UUID.fromString(entity.id)]
                if (rewritten != null) rewritten.toEntity() else entity
            }
            listComparisonDao.updateAll(entities)
        }
    }

    suspend fun updateSettings(settings: RankingSettings) {
        settingsDataStore.update(settings)
    }

    suspend fun resetSettings() {
        settingsDataStore.resetToDefaults()
    }

    suspend fun deleteAllData() {
        db.withTransaction {
            itemComparisonDao.deleteAll()
            listComparisonDao.deleteAll()
            itemDao.deleteAll()
            listDao.deleteAll()
        }
        settingsDataStore.resetToDefaults()
    }

    suspend fun buildListSummaries(): List<ListSummary> {
        val lists = listDao.getActive().map { it.toDomain() }
        return lists.map { list ->
            val activeCount = itemDao.getActiveByList(list.id.toString()).size
            val comparisonCount = itemComparisonDao.getActiveByListChronological(list.id.toString()).size
            val topItem = itemDao.getTopRated(list.id.toString())
            ListSummary(
                list = list,
                activeItemCount = activeCount,
                itemComparisonCount = comparisonCount,
                topItemName = topItem?.name,
                topItemRating = topItem?.rating,
            )
        }
    }
}

data class RecalculationProgress(
    val fraction: Float,
    val message: String,
)

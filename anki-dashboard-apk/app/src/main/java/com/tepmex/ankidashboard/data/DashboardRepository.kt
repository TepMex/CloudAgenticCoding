package com.tepmex.ankidashboard.data

import android.content.Context
import com.tepmex.ankidashboard.data.sync.CollectionStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DashboardRepository(
    private val context: Context,
    private val collection: CollectionReader,
    private val preferences: AppPreferences = AppPreferences(context),
) {

    suspend fun hasDataSource(collectionUri: String?): Boolean = withContext(Dispatchers.IO) {
        CollectionStore.hasCollection(context) ||
            !collectionUri.isNullOrBlank() ||
            CollectionReader.defaultCollectionPaths().any { path ->
                val file = File(path)
                file.isFile && file.canRead()
            }
    }

    suspend fun ensureCollectionOpen(collectionUri: String?): Boolean = withContext(Dispatchers.IO) {
        if (collection.isOpen()) return@withContext true
        // Prefer AnkiWeb download — a stale manual pick should not shadow a fresh sync.
        if (CollectionStore.hasCollection(context) && collection.openCachedCollection()) {
            return@withContext true
        }
        if (!collectionUri.isNullOrBlank() && collection.openFromUri(collectionUri)) {
            return@withContext true
        }
        collection.openDefaultPath()
    }

    suspend fun loadDashboard(
        selectedDecks: List<String>,
        collectionUri: String?,
    ): Result<DashboardData> = withContext(Dispatchers.IO) {
        if (!ensureCollectionOpen(collectionUri)) {
            return@withContext Result.failure(NoCollectionException())
        }

        val deckNamesAndIds = collection.deckNamesAndIds()
        if (deckNamesAndIds.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Synced collection has no decks — try syncing again."),
            )
        }

        if (selectedDecks.isEmpty()) {
            return@withContext Result.success(
                DashboardData(
                    deckNamesAndIds = deckNamesAndIds,
                    intervals = emptyList(),
                    totalCards = 0,
                    reviewScore = 0.0,
                    totalHoursSpent = 0.0,
                    longMemory = 0,
                    debt = 0,
                    debtHistoryData = emptyList(),
                    plotData = emptyList(),
                    mistakesData = emptyList(),
                    reviewsData = emptyList(),
                    newVocabPerMonthData = emptyList(),
                    reviewsStats = emptyList(),
                    leeches = emptyList(),
                    deckFieldOptions = emptyMap(),
                    historyAvailable = true,
                    statusMessage = null,
                ),
            )
        }

        val cardIds = ArrayList<Long>()
        for (deckName in selectedDecks) {
            cardIds.addAll(collection.findCards(buildDeckSearch(deckName)))
        }
        val distinctCardIds = cardIds.distinct()

        val intervals = collection.getIntervals(distinctCardIds)
        val leechCardIds = selectedDecks.flatMap { deck ->
            collection.findCards(buildLeechSearch(deck))
        }.distinct()
        val deckFieldOptions = buildFieldOptionsFromCollection(
            selectedDecks,
            distinctCardIds,
            leechCardIds,
        )

        var plotData = emptyList<Pair<String, Int>>()
        var mistakesData = emptyList<Pair<String, Int>>()
        var reviewsData = emptyList<Pair<String, Int>>()
        var newVocabPerMonthData = emptyList<Pair<String, Int>>()
        var reviewsStats = emptyList<Pair<String, Int>>()
        var reviewScore = 0.0
        var totalHoursSpent = 0.0
        var longMemory = 0
        var cardReviews = emptyMap<Long, List<CardReview>>()

        if (distinctCardIds.isNotEmpty()) {
            cardReviews = collection.getReviewsOfCards(distinctCardIds)
            reviewsStats = collection.getNumCardsReviewedByDay()
            val (start, end) = DashboardAnalytics.plotDateRange()
            plotData = DashboardAnalytics.buildWordsLearnedSeries(cardReviews, start, end)
            val mistakesMap = DashboardAnalytics.mistakesByDay(cardReviews)
            mistakesData = plotData.map { (day, _) -> day to (mistakesMap[day] ?: 0) }
            val reviewsMap = reviewsStats.toMap()
            reviewsData = plotData.map { (day, _) -> day to (reviewsMap[day] ?: 0) }
            newVocabPerMonthData = DashboardAnalytics.newVocabByMonth(cardReviews)
            reviewScore = DashboardAnalytics.calculateReviewScore(cardReviews)
            totalHoursSpent = DashboardAnalytics.calculateTotalHoursSpent(cardReviews)
            longMemory = DashboardAnalytics.calculateLongMemory(cardReviews)
        }

        val leeches = buildLeeches(selectedDecks, cardReviews, leechCardIds)

        val debt = collection.getReviewQueueCount(selectedDecks)
        val deckKey = AppPreferences.debtDeckKey(selectedDecks)
        preferences.recordDebtSnapshot(deckKey, debt)
        val debtHistoryData = preferences.getDebtHistory(deckKey)

        val statusMessage = if (distinctCardIds.isEmpty()) {
            STATUS_NO_CARDS_IN_DECKS
        } else {
            null
        }

        Result.success(
            DashboardData(
                deckNamesAndIds = deckNamesAndIds,
                intervals = intervals,
                totalCards = distinctCardIds.size,
                reviewScore = reviewScore,
                totalHoursSpent = totalHoursSpent,
                longMemory = longMemory,
                debt = debt,
                debtHistoryData = debtHistoryData,
                plotData = plotData,
                mistakesData = mistakesData,
                reviewsData = reviewsData,
                newVocabPerMonthData = newVocabPerMonthData,
                reviewsStats = reviewsStats,
                leeches = leeches,
                deckFieldOptions = deckFieldOptions,
                historyAvailable = true,
                statusMessage = statusMessage,
            ),
        )
    }

    private fun buildFieldOptionsFromCollection(
        selectedDecks: List<String>,
        cardIds: List<Long>,
        leechCardIds: List<Long> = emptyList(),
    ): Map<String, List<String>> {
        val sampleIds = (leechCardIds + cardIds).distinct().take(40)
        val infos = collection.cardsInfo(sampleIds)
        val out = linkedMapOf<String, MutableSet<String>>()
        selectedDecks.forEach { out[it] = linkedSetOf() }
        for (info in infos) {
            val deckKey = resolveSelectedDeck(selectedDecks, info.deckName) ?: continue
            info.noteFields.keys.forEach { out.getOrPut(deckKey) { linkedSetOf() }.add(it) }
        }
        return out.mapValues { (_, set) -> set.sorted() }
    }

    private fun buildLeeches(
        selectedDecks: List<String>,
        cardReviews: Map<Long, List<CardReview>>,
        leechCardIds: List<Long>,
    ): List<LeechCard> {
        return collection.cardsInfo(leechCardIds).map { row ->
            val deckKey = resolveSelectedDeck(selectedDecks, row.deckName) ?: row.deckName
            LeechCard(
                id = row.cardId,
                deckName = deckKey,
                fields = row.noteFields,
                reviewCount = cardReviews[row.cardId]?.size ?: 0,
            )
        }
    }

    /** Matches web [CollectionDataSource] search: `"deck:${deckName}"`. */
    private fun buildDeckSearch(deckName: String): String = "\"deck:$deckName\""

    private fun buildLeechSearch(deckName: String): String = "\"deck:$deckName tag:leech\""

    private fun resolveSelectedDeck(selectedDecks: List<String>, deckName: String): String? {
        if (deckName.isEmpty()) return null
        selectedDecks.find { it == deckName }?.let { return it }
        return selectedDecks.find { deckName.startsWith("$it::") }
    }

    companion object {
        const val STATUS_NO_CARDS_IN_DECKS =
            "No cards found for the selected deck(s). Try re-selecting them — names must match your synced collection."
    }
}

class NoCollectionException : Exception(
    "No collection available. Sync from AnkiWeb in the menu or pick collection.anki2 manually.",
)

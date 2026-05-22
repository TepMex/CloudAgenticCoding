package com.tepmex.ankidashboard.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
class DashboardRepository(
    private val context: Context,
    private val anki: AnkiDroidRepository,
    private val collection: CollectionReader,
) {

    suspend fun ensureCollectionOpen(collectionUri: String?): Boolean = withContext(Dispatchers.IO) {
        if (collection.isOpen()) return@withContext true
        if (!collectionUri.isNullOrBlank() && collection.openFromUri(collectionUri)) {
            return@withContext true
        }
        collection.openDefaultPath()
    }

    suspend fun loadDashboard(
        selectedDecks: List<String>,
        collectionUri: String?,
    ): Result<DashboardData> = withContext(Dispatchers.IO) {
        if (!anki.hasAnkiInstalled()) {
            return@withContext Result.failure(IllegalStateException("anki_missing"))
        }
        if (!anki.hasAnkiPermission()) {
            return@withContext Result.failure(SecurityException("anki_permission"))
        }

        val hasCollection = ensureCollectionOpen(collectionUri)
        val deckNamesAndIds = if (hasCollection) {
            collection.deckNamesAndIds().ifEmpty { anki.loadDeckNamesAndIds() }
        } else {
            anki.loadDeckNamesAndIds()
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
                    plotData = emptyList(),
                    mistakesData = emptyList(),
                    reviewsData = emptyList(),
                    newVocabPerMonthData = emptyList(),
                    reviewsStats = emptyList(),
                    leeches = emptyList(),
                    deckFieldOptions = emptyMap(),
                    historyAvailable = hasCollection,
                    statusMessage = if (!hasCollection) {
                        "History charts need collection.anki2 — open Settings to pick your AnkiDroid folder file."
                    } else {
                        null
                    },
                ),
            )
        }

        val cardIds = ArrayList<Long>()
        for (deckName in selectedDecks) {
            val ids = if (hasCollection) {
                collection.findCards(buildDeckSearch(deckName))
            } else {
                anki.findCardIds(deckName)
            }
            cardIds.addAll(ids)
        }
        val distinctCardIds = cardIds.distinct()

        val intervals = if (hasCollection) {
            collection.getIntervals(distinctCardIds)
        } else {
            anki.getIntervals(distinctCardIds)
        }

        val deckFieldOptions = if (hasCollection) {
            buildFieldOptionsFromCollection(selectedDecks, distinctCardIds)
        } else {
            anki.sampleFieldNamesForDecks(selectedDecks)
        }

        var plotData = emptyList<Pair<String, Int>>()
        var mistakesData = emptyList<Pair<String, Int>>()
        var reviewsData = emptyList<Pair<String, Int>>()
        var newVocabPerMonthData = emptyList<Pair<String, Int>>()
        var reviewsStats = emptyList<Pair<String, Int>>()
        var reviewScore = 0.0
        var totalHoursSpent = 0.0
        var longMemory = 0
        var cardReviews = emptyMap<Long, List<CardReview>>()

        if (hasCollection && distinctCardIds.isNotEmpty()) {
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

        val leeches = buildLeeches(
            selectedDecks = selectedDecks,
            hasCollection = hasCollection,
            cardReviews = cardReviews,
        )

        Result.success(
            DashboardData(
                deckNamesAndIds = deckNamesAndIds,
                intervals = intervals,
                totalCards = distinctCardIds.size,
                reviewScore = reviewScore,
                totalHoursSpent = totalHoursSpent,
                longMemory = longMemory,
                plotData = plotData,
                mistakesData = mistakesData,
                reviewsData = reviewsData,
                newVocabPerMonthData = newVocabPerMonthData,
                reviewsStats = reviewsStats,
                leeches = leeches,
                deckFieldOptions = deckFieldOptions,
                historyAvailable = hasCollection,
                statusMessage = if (!hasCollection) {
                    "History charts need collection.anki2 — open Settings to pick your AnkiDroid folder file."
                } else {
                    null
                },
            ),
        )
    }

    private fun buildFieldOptionsFromCollection(
        selectedDecks: List<String>,
        cardIds: List<Long>,
    ): Map<String, List<String>> {
        val sampleIds = cardIds.take(20)
        val infos = collection.cardsInfo(sampleIds)
        val out = linkedMapOf<String, MutableSet<String>>()
        selectedDecks.forEach { out[it] = linkedSetOf() }
        for (info in infos) {
            val deckKey = resolveSelectedDeck(selectedDecks, info.deckName) ?: continue
            info.noteFields.keys.forEach { out.getOrPut(deckKey) { linkedSetOf() }.add(it) }
        }
        return out.mapValues { (_, set) -> set.sorted() }
    }

    private suspend fun buildLeeches(
        selectedDecks: List<String>,
        hasCollection: Boolean,
        cardReviews: Map<Long, List<CardReview>>,
    ): List<LeechCard> {
        if (hasCollection) {
            val ids = selectedDecks.flatMap { deck ->
                collection.findCards("${buildDeckSearch(deck)} tag:leech")
            }.distinct()
            return collection.cardsInfo(ids).map { row ->
                val deckKey = resolveSelectedDeck(selectedDecks, row.deckName) ?: row.deckName
                LeechCard(
                    id = row.cardId,
                    deckName = deckKey,
                    fields = row.noteFields,
                    reviewCount = cardReviews[row.cardId]?.size ?: 0,
                )
            }
        }
        return anki.loadLeechCardsInfo(selectedDecks).map { card ->
            val deckKey = resolveSelectedDeck(selectedDecks, card.deckName) ?: card.deckName
            LeechCard(
                id = card.cardId,
                deckName = deckKey,
                fields = card.noteFields,
                reviewCount = card.reps,
            )
        }
    }

    private fun buildDeckSearch(deckName: String): String {
        val escaped = deckName.replace("\"", "\\\"")
        return """deck:"$escaped""""
    }

    private fun resolveSelectedDeck(selectedDecks: List<String>, deckName: String): String? {
        if (deckName.isEmpty()) return null
        selectedDecks.find { it == deckName }?.let { return it }
        return selectedDecks.find { deckName.startsWith("$it::") }
    }

    companion object {
        private const val TAG = "DashboardRepo"
    }
}

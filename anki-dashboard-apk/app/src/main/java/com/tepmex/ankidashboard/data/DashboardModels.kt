package com.tepmex.ankidashboard.data

data class DayCount(val date: String, val count: Int)

data class LeechCard(
    val id: Long,
    val deckName: String,
    val fields: Map<String, String>,
    val reviewCount: Int,
)

data class DashboardData(
    val deckNamesAndIds: Map<String, Long>,
    val intervals: List<Int>,
    val totalCards: Int,
    val reviewScore: Double,
    val totalHoursSpent: Double,
    val longMemory: Int,
    val debt: Int,
    val debtHistoryData: List<Pair<String, Int>>,
    val plotData: List<Pair<String, Int>>,
    val mistakesData: List<Pair<String, Int>>,
    val reviewsData: List<Pair<String, Int>>,
    val newVocabPerMonthData: List<Pair<String, Int>>,
    val reviewsStats: List<Pair<String, Int>>,
    val leeches: List<LeechCard>,
    val deckFieldOptions: Map<String, List<String>>,
    val historyAvailable: Boolean,
    val statusMessage: String? = null,
)

data class CardReview(
    val id: Long,
    val ease: Int,
    val time: Int,
    val ivl: Int,
    /** Revlog type: 0=learn, 1=review, 2=relrn, 3=cram */
    val type: Int,
)

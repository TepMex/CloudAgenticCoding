package com.tepmex.paircompelo.ui.components

import com.tepmex.paircompelo.domain.model.PreferenceItem
import com.tepmex.paircompelo.domain.model.PreferenceList
import com.tepmex.paircompelo.domain.model.RankingSettings
import java.time.Instant
import java.util.UUID

object PreviewData {
    private val now = Instant.parse("2026-07-10T12:00:00Z")
    val listId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    val list = PreferenceList(
        id = listId,
        name = "Films",
        description = "Movies I might rewatch",
        createdAt = now,
        updatedAt = now,
        rating = 1012.5,
        ratingUpdatedAt = now,
        comparisonCount = 4,
    )
    val items = listOf(
        PreferenceItem(
            id = UUID.fromString("22222222-2222-2222-2222-222222222221"),
            listId = listId,
            name = "Arrival",
            description = "Quiet sci-fi",
            createdAt = now,
            updatedAt = now,
            rating = 1040.0,
            ratingUpdatedAt = now,
            comparisonCount = 3,
            winCount = 2,
            lossCount = 1,
        ),
        PreferenceItem(
            id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            listId = listId,
            name = "Heat",
            description = "Crime epic",
            createdAt = now,
            updatedAt = now,
            rating = 990.0,
            ratingUpdatedAt = now,
            comparisonCount = 3,
            winCount = 1,
            lossCount = 2,
        ),
    )
    val settings = RankingSettings.Defaults
}

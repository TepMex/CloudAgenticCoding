package com.tepmex.paircompelo.domain.validation

import com.tepmex.paircompelo.domain.model.PairSelectionStrategy
import com.tepmex.paircompelo.domain.model.RankingSettings

object NameValidator {
    const val MAX_NAME_LENGTH = 200
    const val MAX_DESCRIPTION_LENGTH = 2_000
    const val MAX_NOTES_LENGTH = 4_000

    fun normalizeName(raw: String): Result<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Name cannot be blank"))
        if (trimmed.length > MAX_NAME_LENGTH) {
            return Result.failure(IllegalArgumentException("Name must be at most $MAX_NAME_LENGTH characters"))
        }
        return Result.success(trimmed)
    }

    fun normalizeOptionalText(raw: String?, max: Int): Result<String?> {
        if (raw == null) return Result.success(null)
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Result.success(null)
        if (trimmed.length > max) {
            return Result.failure(IllegalArgumentException("Text must be at most $max characters"))
        }
        return Result.success(trimmed)
    }
}

object SettingsValidator {
    fun validate(settings: RankingSettings): Result<RankingSettings> {
        if (!settings.initialRating.isFinite()) {
            return Result.failure(IllegalArgumentException("Initial rating must be a finite number"))
        }
        if (settings.kFactor <= 0.0 || !settings.kFactor.isFinite()) {
            return Result.failure(IllegalArgumentException("K-factor must be greater than zero"))
        }
        if (settings.ratingScale <= 0.0 || !settings.ratingScale.isFinite()) {
            return Result.failure(IllegalArgumentException("Rating scale must be greater than zero"))
        }
        if (settings.decayRatePerDay <= 0.0 || settings.decayRatePerDay > 1.0 || !settings.decayRatePerDay.isFinite()) {
            return Result.failure(IllegalArgumentException("Daily decay rate must be greater than 0 and at most 1"))
        }
        if (settings.minimumComparisonsBeforeStable < 0) {
            return Result.failure(IllegalArgumentException("Minimum comparisons cannot be negative"))
        }
        // Ensure strategy is a known value
        PairSelectionStrategy.entries.firstOrNull { it == settings.pairSelectionStrategy }
            ?: return Result.failure(IllegalArgumentException("Unknown pair selection strategy"))
        return Result.success(settings)
    }
}

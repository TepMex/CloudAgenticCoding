package com.tepmex.paircompelo.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tepmex.paircompelo.domain.model.PairSelectionStrategy
import com.tepmex.paircompelo.domain.model.RankingSettings
import com.tepmex.paircompelo.domain.validation.SettingsValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<RankingSettings> = dataStore.data.map { prefs ->
        RankingSettings(
            initialRating = prefs[Keys.INITIAL_RATING] ?: RankingSettings.DEFAULT_INITIAL_RATING,
            kFactor = prefs[Keys.K_FACTOR] ?: RankingSettings.DEFAULT_K_FACTOR,
            ratingScale = prefs[Keys.RATING_SCALE] ?: RankingSettings.DEFAULT_RATING_SCALE,
            decayEnabled = prefs[Keys.DECAY_ENABLED] ?: true,
            decayRatePerDay = prefs[Keys.DECAY_RATE] ?: RankingSettings.DEFAULT_DECAY_RATE,
            minimumComparisonsBeforeStable = prefs[Keys.MIN_COMPARISONS]
                ?: RankingSettings.DEFAULT_MIN_COMPARISONS,
            pairSelectionStrategy = prefs[Keys.PAIR_STRATEGY]?.let {
                runCatching { PairSelectionStrategy.valueOf(it) }.getOrNull()
            } ?: PairSelectionStrategy.BALANCED_ADAPTIVE,
            allowDraws = prefs[Keys.ALLOW_DRAWS] ?: true,
            allowSkipping = prefs[Keys.ALLOW_SKIPPING] ?: true,
            showRatingsDuringComparison = prefs[Keys.SHOW_RATINGS] ?: true,
        )
    }

    suspend fun update(settings: RankingSettings) {
        val validated = SettingsValidator.validate(settings).getOrThrow()
        dataStore.edit { prefs ->
            prefs[Keys.INITIAL_RATING] = validated.initialRating
            prefs[Keys.K_FACTOR] = validated.kFactor
            prefs[Keys.RATING_SCALE] = validated.ratingScale
            prefs[Keys.DECAY_ENABLED] = validated.decayEnabled
            prefs[Keys.DECAY_RATE] = validated.decayRatePerDay
            prefs[Keys.MIN_COMPARISONS] = validated.minimumComparisonsBeforeStable
            prefs[Keys.PAIR_STRATEGY] = validated.pairSelectionStrategy.name
            prefs[Keys.ALLOW_DRAWS] = validated.allowDraws
            prefs[Keys.ALLOW_SKIPPING] = validated.allowSkipping
            prefs[Keys.SHOW_RATINGS] = validated.showRatingsDuringComparison
        }
    }

    suspend fun resetToDefaults() {
        update(RankingSettings.Defaults)
    }

    private object Keys {
        val INITIAL_RATING = doublePreferencesKey("initial_rating")
        val K_FACTOR = doublePreferencesKey("k_factor")
        val RATING_SCALE = doublePreferencesKey("rating_scale")
        val DECAY_ENABLED = booleanPreferencesKey("decay_enabled")
        val DECAY_RATE = doublePreferencesKey("decay_rate")
        val MIN_COMPARISONS = intPreferencesKey("min_comparisons")
        val PAIR_STRATEGY = stringPreferencesKey("pair_strategy")
        val ALLOW_DRAWS = booleanPreferencesKey("allow_draws")
        val ALLOW_SKIPPING = booleanPreferencesKey("allow_skipping")
        val SHOW_RATINGS = booleanPreferencesKey("show_ratings")
    }
}

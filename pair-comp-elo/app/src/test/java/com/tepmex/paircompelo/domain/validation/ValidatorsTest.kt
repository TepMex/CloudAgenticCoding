package com.tepmex.paircompelo.domain.validation

import com.google.common.truth.Truth.assertThat
import com.tepmex.paircompelo.domain.model.RankingSettings
import org.junit.Test

class ValidatorsTest {
    @Test
    fun blankName_fails() {
        assertThat(NameValidator.normalizeName("   ").isFailure).isTrue()
    }

    @Test
    fun trimsName() {
        assertThat(NameValidator.normalizeName("  Hello  ").getOrNull()).isEqualTo("Hello")
    }

    @Test
    fun invalidSettings_fail() {
        assertThat(
            SettingsValidator.validate(RankingSettings.Defaults.copy(kFactor = 0.0)).isFailure,
        ).isTrue()
        assertThat(
            SettingsValidator.validate(RankingSettings.Defaults.copy(decayRatePerDay = 1.5)).isFailure,
        ).isTrue()
        assertThat(
            SettingsValidator.validate(RankingSettings.Defaults.copy(ratingScale = -1.0)).isFailure,
        ).isTrue()
    }

    @Test
    fun defaults_valid() {
        assertThat(SettingsValidator.validate(RankingSettings.Defaults).isSuccess).isTrue()
    }
}

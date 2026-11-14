package dev.eareye.t.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Scenarios 8–16, 19 and 20. Durations are checked as exact millisecond totals.
 */
class EarAndStatsTest {
    @Test
    fun audioStartPauseResumeStopProducesTwoIntervals() {
        val reducer = AudioAggregateReducer()
        val transitions = mutableListOf<AudioTransitionRecord>()
        fun feed(elapsed: Long, count: Int) {
            val active = reducer.onCount(count) ?: return
            transitions += transition(elapsed, active)
        }
        feed(1_000, 1)
        feed(4_000, 0)
        feed(5_000, 1)
        feed(9_000, 0)
        feed(9_500, 0)
        val ear = reconstructEar(listOf(session(lastElapsed = 10_000)), transitions)
        assertEquals(listOf(TimeInterval(1_000, 4_000), TimeInterval(5_000, 9_000)), ear.active)
        assertEquals(7_000L, totalDuration(ear.active))
        assertEquals(listOf(TimeInterval(0, 10_000)), ear.coverage)
    }

    @Test
    fun twoSimultaneousPlayersCountOnce() {
        val reducer = AudioAggregateReducer()
        val transitions = mutableListOf<AudioTransitionRecord>()
        fun feed(elapsed: Long, count: Int) {
            val active = reducer.onCount(count) ?: return
            transitions += transition(elapsed, active)
        }
        feed(0, 1)
        feed(1_000, 2)
        feed(2_000, 1)
        feed(3_000, 0)
        assertEquals(2, transitions.size)
        val ear = reconstructEar(listOf(session(lastElapsed = 3_000)), transitions)
        assertEquals(listOf(TimeInterval(0, 3_000)), ear.active)
        assertEquals(3_000L, totalDuration(ear.active))
    }

    @Test
    fun ignoredCategoriesDoNotCount() {
        val selection = AudioCategorySelection()
        assertTrue(selection.allows(AudioUsages.MEDIA))
        assertTrue(selection.allows(AudioUsages.GAME))
        assertFalse(selection.allows(AudioUsages.NOTIFICATION))
        assertFalse(selection.allows(AudioUsages.ALARM))
        assertFalse(selection.allows(AudioUsages.RINGTONE))
        assertFalse(selection.allows(AudioUsages.VOICE_COMMUNICATION))
        assertFalse(selection.allows(AudioUsages.SONIFICATION))
        assertFalse(selection.allows(AudioUsages.ASSISTANT))
        assertEquals(0, qualifyingUsageCount(listOf(AudioUsages.NOTIFICATION, AudioUsages.ALARM), selection))
        assertEquals(2, qualifyingUsageCount(listOf(AudioUsages.MEDIA, AudioUsages.GAME, AudioUsages.RINGTONE), selection))
        assertTrue(selection.copy(assistant = true).allows(AudioUsages.ASSISTANT))
    }

    @Test
    fun eyeAndEarOverlapUsesTheUnionFormula() {
        val minute = 60_000L
        val eye = listOf(TimeInterval(0, 10 * minute), TimeInterval(60 * minute, 80 * minute))
        val ear = listOf(TimeInterval(20 * minute, 50 * minute), TimeInterval(60 * minute, 80 * minute))
        val window = TimeInterval(0, 80 * minute)
        val stats = calculateDailyStatistics(
            eyeActive = eye,
            eyeUnknown = emptyList(),
            earActive = ear,
            listenerCoverage = listOf(window),
            poweredOff = emptyList(),
            window = window,
        )
        assertEquals(30 * minute, stats.eyeMillis)
        assertEquals(50 * minute, stats.earMillis)
        assertEquals(20 * minute, stats.overlapMillis)
        assertEquals(10 * minute, stats.eyeOnlyMillis)
        assertEquals(30 * minute, stats.earOnlyMillis)
        assertEquals(60 * minute, stats.unionMillis)
        assertEquals(stats.bothKnownEyeMillis + stats.bothKnownEarMillis - stats.overlapMillis, stats.unionMillis)
        assertFalse(stats.incomplete)
    }

    @Test
    fun videoBrowsingAndMusicExamplesStayExact() {
        val minute = 60_000L
        val browser = statsOf(eye = listOf(TimeInterval(0, 10 * minute)), ear = emptyList(), window = TimeInterval(0, 10 * minute))
        assertEquals(10 * minute, browser.eyeMillis)
        assertEquals(0L, browser.earMillis)
        assertEquals(10 * minute, browser.unionMillis)

        val music = statsOf(eye = emptyList(), ear = listOf(TimeInterval(0, 30 * minute)), window = TimeInterval(0, 30 * minute))
        assertEquals(0L, music.eyeMillis)
        assertEquals(30 * minute, music.earMillis)
        assertEquals(30 * minute, music.earOnlyMillis)
        assertEquals(0L, music.overlapMillis)

        val video = statsOf(
            eye = listOf(TimeInterval(0, 20 * minute)),
            ear = listOf(TimeInterval(0, 20 * minute)),
            window = TimeInterval(0, 20 * minute),
        )
        assertEquals(20 * minute, video.eyeMillis)
        assertEquals(20 * minute, video.earMillis)
        assertEquals(20 * minute, video.overlapMillis)
        assertEquals(20 * minute, video.unionMillis)
        assertEquals(0L, video.eyeOnlyMillis)
    }

    @Test
    fun localMidnightSplitsChronologicalTime() {
        val zone = ZoneId.of("Europe/Moscow")
        val start = ZonedDateTime.of(2026, 5, 1, 23, 0, 0, 0, zone)
        val end = start.plusHours(2)
        val interval = TimeInterval(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli())
        val parts = splitByLocalDays(interval, zone)
        assertEquals(2, parts.size)
        assertEquals(3_600_000L, parts[0].second.durationMillis)
        assertEquals(3_600_000L, parts[1].second.durationMillis)
        assertEquals(LocalDate.of(2026, 5, 1), parts[0].first)
        assertEquals(LocalDate.of(2026, 5, 2), parts[1].first)
        assertEquals(interval.durationMillis, parts.sumOf { it.second.durationMillis })

        val day = periodWindow(LocalDate.of(2026, 5, 1), zone, end.toInstant().toEpochMilli())
        val stats = calculateDailyStatistics(
            eyeActive = listOf(interval),
            eyeUnknown = emptyList(),
            earActive = emptyList(),
            listenerCoverage = listOf(day),
            poweredOff = emptyList(),
            window = day,
        )
        assertEquals(3_600_000L, stats.eyeMillis)
    }

    @Test
    fun daylightSavingUsesInstantDurationNotLocalClockLabels() {
        val zone = ZoneId.of("America/New_York")
        val fallBack = localDayBounds(LocalDate.of(2026, 11, 1), zone)
        val spring = localDayBounds(LocalDate.of(2026, 3, 8), zone)
        assertEquals(25 * 3_600_000L, fallBack.durationMillis)
        assertEquals(23 * 3_600_000L, spring.durationMillis)

        val start = ZonedDateTime.of(2026, 11, 1, 0, 30, 0, 0, zone)
        val end = start.plusHours(4)
        assertEquals(4 * 3_600_000L, end.toInstant().toEpochMilli() - start.toInstant().toEpochMilli())
        assertEquals(3L, java.time.Duration.between(start.toLocalTime(), end.toLocalTime()).toHours())

        val stats = calculateDailyStatistics(
            eyeActive = listOf(TimeInterval(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli())),
            eyeUnknown = emptyList(),
            earActive = emptyList(),
            listenerCoverage = listOf(fallBack),
            poweredOff = emptyList(),
            window = fallBack,
        )
        assertEquals(4 * 3_600_000L, stats.eyeMillis)
    }

    @Test
    fun killedProcessDoesNotExtendAudioPastLastConfirmation() {
        val session = session(lastElapsed = 60_000)
        val ear = reconstructEar(
            sessions = listOf(session),
            transitions = listOf(transition(0, true)),
            live = null,
        )
        assertEquals(listOf(TimeInterval(0, 60_000)), ear.active)
        val window = TimeInterval(0, 3_600_000)
        val stats = calculateDailyStatistics(
            eyeActive = emptyList(),
            eyeUnknown = emptyList(),
            earActive = ear.active,
            listenerCoverage = ear.coverage,
            poweredOff = emptyList(),
            window = window,
        )
        assertEquals(60_000L, stats.earMillis)
        assertEquals(3_600_000L - 60_000L, stats.possiblyUndercountedEarMillis)
        assertEquals(3_600_000L - 60_000L, stats.unknownMillis)
        assertTrue(stats.incomplete)
        assertEquals(60_000L, stats.unionMillis)
    }

    @Test
    fun rebootDoesNotBridgeElapsedClocks() {
        val first = SessionRecord(
            sessionId = 1,
            bootCount = 1,
            connectedWallMillis = 0,
            connectedElapsedMillis = 100,
            lastConfirmedWallMillis = 4_900,
            lastConfirmedElapsedMillis = 5_000,
        )
        val second = SessionRecord(
            sessionId = 2,
            bootCount = 2,
            connectedWallMillis = 100_000,
            connectedElapsedMillis = 10,
            lastConfirmedWallMillis = 100_000,
            lastConfirmedElapsedMillis = 10,
        )
        val foreign = AudioTransitionRecord(
            sessionId = 2,
            wallMillis = 100_000,
            elapsedMillis = 5_000,
            bootCount = 1,
            active = true,
        )
        val ear = reconstructEar(
            sessions = listOf(first, second),
            transitions = listOf(transition(100, true, sessionId = 1), foreign),
        )
        assertEquals(listOf(TimeInterval(0, 4_900)), ear.active)
        assertEquals(listOf(TimeInterval(0, 4_900)), ear.coverage)
        val split = observationSplit(
            ClockSample(0, 5_000, bootCount = 1),
            ClockSample(100_000, 10, bootCount = 2),
        )
        assertEquals(SessionEndReason.REBOOT, split)
    }

    @Test
    fun lostMonitoringLeavesAnUnknownGapUntilTheNextSession() {
        val first = session(id = 1, lastElapsed = 1_000)
        val second = SessionRecord(
            sessionId = 2,
            bootCount = 1,
            connectedWallMillis = 5_000,
            connectedElapsedMillis = 5_000,
            lastConfirmedWallMillis = 8_000,
            lastConfirmedElapsedMillis = 8_000,
        )
        val ear = reconstructEar(
            sessions = listOf(first, second),
            transitions = listOf(transition(0, true, sessionId = 1)),
        )
        assertEquals(listOf(TimeInterval(0, 1_000)), ear.active)
        assertEquals(listOf(TimeInterval(0, 1_000), TimeInterval(5_000, 8_000)), ear.coverage)
        val window = TimeInterval(0, 8_000)
        val stats = calculateDailyStatistics(
            eyeActive = emptyList(),
            eyeUnknown = emptyList(),
            earActive = ear.active,
            listenerCoverage = ear.coverage,
            poweredOff = emptyList(),
            window = window,
        )
        assertEquals(4_000L, stats.unknownMillis)
        assertEquals(1_000L, stats.earMillis)
        assertTrue(stats.incomplete)
    }

    @Test
    fun longAudioWithoutFurtherCallbacksKeepsTheConfirmedDuration() {
        val threeHours = 3L * 3_600_000L
        val ear = reconstructEar(
            sessions = listOf(session(lastElapsed = threeHours)),
            transitions = listOf(transition(0, true)),
            live = null,
        )
        assertEquals(threeHours, totalDuration(ear.active))
        val extended = reconstructEar(
            sessions = listOf(session(lastElapsed = threeHours)),
            transitions = listOf(transition(0, true)),
            live = LiveObservation(sessionId = 1, wallMillis = threeHours + 60_000, elapsedMillis = threeHours + 60_000),
            maxContinuousGapMillis = 120_000,
        )
        assertEquals(threeHours + 60_000, totalDuration(extended.active))
    }

    @Test
    fun clockJumpDoesNotStretchTheAnchoredInterval() {
        val session = SessionRecord(
            sessionId = 1,
            bootCount = 4,
            connectedWallMillis = 1_000_000,
            connectedElapsedMillis = 0,
            lastConfirmedWallMillis = 1_010_000,
            lastConfirmedElapsedMillis = 10_000,
        )
        val ear = reconstructEar(
            sessions = listOf(session),
            transitions = listOf(
                AudioTransitionRecord(1, 1_000_000, 0, 4, true),
                AudioTransitionRecord(1, 1_000_000 + 10_000 + 3_600_000, 10_000, 4, false),
            ),
        )
        assertEquals(listOf(TimeInterval(1_000_000, 1_010_000)), ear.active)
        assertEquals(10_000L, totalDuration(ear.active))
        assertEquals(
            SessionEndReason.CLOCK,
            observationSplit(
                ClockSample(1_000_000, 0, 4),
                ClockSample(1_000_000 + 3_600_000, 1_000, 4),
            ),
        )
        assertNull(
            observationSplit(
                ClockSample(1_000, 1_000, 4),
                ClockSample(1_000 + 4_000, 5_000, 4),
            ),
        )
    }

    @Test
    fun incompleteAudioIsNotReportedAsExactUnion() {
        val window = TimeInterval(0, 100)
        val stats = calculateDailyStatistics(
            eyeActive = listOf(window),
            eyeUnknown = emptyList(),
            earActive = listOf(TimeInterval(0, 40)),
            listenerCoverage = listOf(TimeInterval(0, 40)),
            poweredOff = emptyList(),
            window = window,
        )
        assertEquals(100L, stats.eyeMillis)
        assertEquals(40L, stats.earMillis)
        assertEquals(40L, stats.overlapMillis)
        assertEquals(40L, stats.unionMillis)
        assertEquals(60L, stats.eyeDuringAudioUnknownMillis)
        assertEquals(60L, stats.possiblyUndercountedEarMillis)
        assertEquals(60L, stats.unknownMillis)
        assertEquals(0.4, stats.coverageFraction, 0.000_000_1)
        assertTrue(stats.incomplete)
        assertEquals(stats.bothKnownEyeMillis + stats.bothKnownEarMillis - stats.overlapMillis, stats.unionMillis)
        assertEquals(stats.eyeMillis, stats.eyeOnlyMillis + stats.overlapMillis + stats.eyeDuringAudioUnknownMillis)
    }

    @Test
    fun gapBeyondTheGracePeriodIsNotLiveCoverage() {
        assertEquals(
            SessionEndReason.GAP,
            observationSplit(
                ClockSample(0, 0, 1),
                ClockSample(ObservationPolicy.MAX_CONTINUOUS_GAP_MILLIS + 1, ObservationPolicy.MAX_CONTINUOUS_GAP_MILLIS + 1, 1),
            ),
        )
        assertNull(
            observationSplit(
                ClockSample(0, 0, 1),
                ClockSample(ObservationPolicy.MAX_CONTINUOUS_GAP_MILLIS, ObservationPolicy.MAX_CONTINUOUS_GAP_MILLIS, 1),
            ),
        )
    }

    private fun statsOf(eye: List<TimeInterval>, ear: List<TimeInterval>, window: TimeInterval): DailyStatistics {
        return calculateDailyStatistics(
            eyeActive = eye,
            eyeUnknown = emptyList(),
            earActive = ear,
            listenerCoverage = listOf(window),
            poweredOff = emptyList(),
            window = window,
        )
    }

    private fun session(id: Long = 1, lastElapsed: Long): SessionRecord = SessionRecord(
        sessionId = id,
        bootCount = 1,
        connectedWallMillis = 0,
        connectedElapsedMillis = 0,
        lastConfirmedWallMillis = lastElapsed,
        lastConfirmedElapsedMillis = lastElapsed,
    )

    private fun transition(elapsed: Long, active: Boolean, sessionId: Long = 1): AudioTransitionRecord {
        return AudioTransitionRecord(
            sessionId = sessionId,
            wallMillis = elapsed,
            elapsedMillis = elapsed,
            bootCount = 1,
            active = active,
        )
    }
}

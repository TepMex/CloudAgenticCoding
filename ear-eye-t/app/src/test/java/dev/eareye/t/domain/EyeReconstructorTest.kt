package dev.eareye.t.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Scenarios 1–7 and 17 from the MVP list, plus shutdown and a long known-idle gap.
 * Times are epoch milliseconds. Eye is active only when the screen is interactive
 * and the keyguard is known to be hidden.
 */
class EyeReconstructorTest {
    @Test
    fun simpleUnlockedScreenSessionIsTenMinutes() {
        val events = withStableOrdinals(
            listOf(
                0L to ScreenEventType.SCREEN_INTERACTIVE,
                0L to ScreenEventType.KEYGUARD_HIDDEN,
                600_000L to ScreenEventType.SCREEN_NON_INTERACTIVE,
            ),
        )
        val eye = reconstructEye(events, TimeInterval(0, 600_000), trustedUntilMillis = 600_000)
        assertEquals(600_000L, totalDuration(eye.active))
        assertEquals(listOf(TimeInterval(0, 600_000)), eye.active)
        assertEquals(0L, totalDuration(eye.unknown))
    }

    @Test
    fun unlockAfterLockScreenStartsEyeTime() {
        val events = withStableOrdinals(
            listOf(
                0L to ScreenEventType.SCREEN_INTERACTIVE,
                0L to ScreenEventType.KEYGUARD_SHOWN,
                5_000L to ScreenEventType.KEYGUARD_HIDDEN,
                15_000L to ScreenEventType.SCREEN_NON_INTERACTIVE,
            ),
        )
        val eye = reconstructEye(events, TimeInterval(0, 15_000), trustedUntilMillis = 15_000)
        assertEquals(listOf(TimeInterval(5_000, 15_000)), eye.active)
        assertEquals(0L, totalDuration(eye.unknown))
    }

    @Test
    fun interactiveLockScreenIsNotEyeTime() {
        val events = withStableOrdinals(
            listOf(
                0L to ScreenEventType.SCREEN_INTERACTIVE,
                0L to ScreenEventType.KEYGUARD_SHOWN,
                30_000L to ScreenEventType.SCREEN_NON_INTERACTIVE,
            ),
        )
        val eye = reconstructEye(events, TimeInterval(0, 30_000), trustedUntilMillis = 30_000)
        assertEquals(0L, totalDuration(eye.active))
        assertEquals(0L, totalDuration(eye.unknown))
    }

    @Test
    fun screenOffEndsEyeBeforeTheFollowingLock() {
        val events = withStableOrdinals(
            listOf(
                0L to ScreenEventType.SCREEN_INTERACTIVE,
                0L to ScreenEventType.KEYGUARD_HIDDEN,
                5_000L to ScreenEventType.SCREEN_NON_INTERACTIVE,
                8_000L to ScreenEventType.KEYGUARD_SHOWN,
            ),
        )
        val eye = reconstructEye(events, TimeInterval(0, 10_000), trustedUntilMillis = 10_000)
        assertEquals(listOf(TimeInterval(0, 5_000)), eye.active)
        assertEquals(5_000L, totalDuration(eye.active))
    }

    @Test
    fun unknownInitialStateIsNotAssumedUnlocked() {
        val eye = reconstructEye(emptyList(), TimeInterval(0, 10_000), trustedUntilMillis = 10_000)
        assertEquals(0L, totalDuration(eye.active))
        assertEquals(listOf(TimeInterval(0, 10_000)), eye.unknown)

        val partial = reconstructEye(
            withStableOrdinals(listOf(1_000L to ScreenEventType.SCREEN_INTERACTIVE)),
            TimeInterval(0, 10_000),
            trustedUntilMillis = 10_000,
        )
        assertEquals(0L, totalDuration(partial.active))
        assertEquals(10_000L, totalDuration(partial.unknown))
    }

    @Test
    fun duplicateEventsDoNotSplitTheInterval() {
        val events = withStableOrdinals(
            listOf(
                0L to ScreenEventType.SCREEN_INTERACTIVE,
                0L to ScreenEventType.KEYGUARD_HIDDEN,
                1_000L to ScreenEventType.SCREEN_INTERACTIVE,
                1_000L to ScreenEventType.SCREEN_INTERACTIVE,
                4_000L to ScreenEventType.SCREEN_NON_INTERACTIVE,
            ),
        )
        val eye = reconstructEye(events, TimeInterval(0, 4_000), trustedUntilMillis = 4_000)
        assertEquals(listOf(TimeInterval(0, 4_000)), eye.active)
    }

    @Test
    fun sameTimestampEventsKeepAContinuousDuration() {
        val events = withStableOrdinals(
            listOf(
                0L to ScreenEventType.SCREEN_INTERACTIVE,
                0L to ScreenEventType.KEYGUARD_HIDDEN,
                5_000L to ScreenEventType.SCREEN_NON_INTERACTIVE,
                5_000L to ScreenEventType.SCREEN_INTERACTIVE,
            ),
        )
        val eye = reconstructEye(events, TimeInterval(0, 8_000), trustedUntilMillis = 8_000)
        assertEquals(8_000L, totalDuration(eye.active))
        assertEquals(listOf(TimeInterval(0, 8_000)), eye.active)
    }

    @Test
    fun intervalThatStartedBeforeTheWindowIsClippedToTheWindow() {
        val events = withStableOrdinals(
            listOf(
                500L to ScreenEventType.SCREEN_INTERACTIVE,
                500L to ScreenEventType.KEYGUARD_HIDDEN,
                1_500L to ScreenEventType.SCREEN_NON_INTERACTIVE,
            ),
        )
        val eye = reconstructEye(events, TimeInterval(1_000, 2_000), trustedUntilMillis = 2_000)
        assertEquals(listOf(TimeInterval(1_000, 1_500)), eye.active)
        assertEquals(0L, totalDuration(eye.unknown))
    }

    @Test
    fun revokedPermissionStopsExtrapolationAtTheTrustedHorizon() {
        val events = withStableOrdinals(
            listOf(
                0L to ScreenEventType.SCREEN_INTERACTIVE,
                0L to ScreenEventType.KEYGUARD_HIDDEN,
                9_000L to ScreenEventType.SCREEN_NON_INTERACTIVE,
            ),
        )
        val eye = reconstructEye(events, TimeInterval(0, 10_000), trustedUntilMillis = 5_000)
        assertEquals(listOf(TimeInterval(0, 5_000)), eye.active)
        assertEquals(listOf(TimeInterval(5_000, 10_000)), eye.unknown)
    }

    @Test
    fun shutdownAndStartupBracketKnownOffTimeAndThenUnknown() {
        val events = withStableOrdinals(
            listOf(
                0L to ScreenEventType.SCREEN_INTERACTIVE,
                0L to ScreenEventType.KEYGUARD_HIDDEN,
                4_000L to ScreenEventType.DEVICE_SHUTDOWN,
                7_000L to ScreenEventType.DEVICE_STARTUP,
                9_000L to ScreenEventType.SCREEN_INTERACTIVE,
                9_000L to ScreenEventType.KEYGUARD_HIDDEN,
                12_000L to ScreenEventType.SCREEN_NON_INTERACTIVE,
            ),
        )
        val window = TimeInterval(0, 15_000)
        val eye = reconstructEye(events, window, trustedUntilMillis = 15_000)
        assertEquals(listOf(TimeInterval(0, 4_000), TimeInterval(9_000, 12_000)), eye.active)
        assertEquals(listOf(TimeInterval(7_000, 9_000)), eye.unknown)
        assertEquals(listOf(TimeInterval(4_000, 7_000)), poweredOffIntervals(events, window, 15_000))
    }

    @Test
    fun startupWithoutShutdownInvalidatesTheOpenSegment() {
        val events = withStableOrdinals(
            listOf(
                0L to ScreenEventType.SCREEN_INTERACTIVE,
                0L to ScreenEventType.KEYGUARD_HIDDEN,
                5_000L to ScreenEventType.DEVICE_STARTUP,
            ),
        )
        val eye = reconstructEye(events, TimeInterval(0, 10_000), trustedUntilMillis = 10_000)
        assertEquals(0L, totalDuration(eye.active))
        assertEquals(10_000L, totalDuration(eye.unknown))
    }

    @Test
    fun longScreenOffStretchStaysKnownIdle() {
        val events = withStableOrdinals(
            listOf(
                0L to ScreenEventType.SCREEN_NON_INTERACTIVE,
                0L to ScreenEventType.KEYGUARD_SHOWN,
            ),
        )
        val fiveHours = 5L * 3_600_000L
        val eye = reconstructEye(events, TimeInterval(0, fiveHours), trustedUntilMillis = fiveHours)
        assertEquals(0L, totalDuration(eye.active))
        assertEquals(0L, totalDuration(eye.unknown))
    }

    @Test
    fun unclosedShutdownIsNotTreatedAsPoweredOffUntilStartup() {
        val events = withStableOrdinals(listOf(4_000L to ScreenEventType.DEVICE_SHUTDOWN))
        assertEquals(emptyList<TimeInterval>(), poweredOffIntervals(events, TimeInterval(0, 20_000), 20_000))
    }
}

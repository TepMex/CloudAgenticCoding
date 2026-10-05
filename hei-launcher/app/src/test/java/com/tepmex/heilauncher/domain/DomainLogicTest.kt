package com.tepmex.heilauncher.domain

import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainLogicTest {
    @Test
    fun octoberFifth2026MatchesTheReferenceScreen() {
        val date = LocalDate.of(2026, 10, 5)
        val time = LocalTime.of(13, 2)
        assertEquals(75, DayYearProgress.year(date).percent)
        assertEquals(0.75, DayYearProgress.year(date).fraction, 0.0001)
        assertEquals(54, DayYearProgress.day(time).percent)
        assertEquals(46920 / 86400.0, DayYearProgress.day(time).fraction, 0.0001)
        assertEquals("Monday, 5th October", formatHomeDate(date, Locale.ENGLISH))
        assertEquals("понедельник, 5 октября", formatHomeDate(date, Locale("ru")))
        assertEquals("1:02 PM", formatClock(time, ClockFormat.H12, systemIs24Hour = true, Locale.US))
        assertEquals("13:02", formatClock(time, ClockFormat.H24, systemIs24Hour = false, Locale.US))
    }

    @Test
    fun yearEndsAt100AndStartsAt0() {
        assertEquals(0, DayYearProgress.year(LocalDate.of(2026, 1, 1)).percent)
        assertEquals(100, DayYearProgress.year(LocalDate.of(2026, 12, 31)).percent)
        assertEquals(1.0, DayYearProgress.year(LocalDate.of(2026, 12, 31)).fraction, 0.0001)
    }

    @Test
    fun ordinals() {
        assertEquals("1st", ordinal(1))
        assertEquals("2nd", ordinal(2))
        assertEquals("3rd", ordinal(3))
        assertEquals("4th", ordinal(4))
        assertEquals("11th", ordinal(11))
        assertEquals("12th", ordinal(12))
        assertEquals("13th", ordinal(13))
        assertEquals("21st", ordinal(21))
        assertEquals("22nd", ordinal(22))
        assertEquals("23rd", ordinal(23))
    }

    @Test
    fun searchRanksSubstringMatches() {
        val apps = listOf(
            app("com.android.chrome", "Chrome"),
            app("pkg.chinese", "Chinese GG"),
            app("org.telegram.messenger", "Telegram X"),
            app("com.ichi2.anki", "AnkiDroid"),
            app("com.google.android.apps.maps", "Maps"),
        )
        assertEquals(
            listOf("Chinese GG", "Chrome", "AnkiDroid"),
            filterApps(apps, "ch").map { it.label },
        )
        assertEquals(listOf("Chinese GG"), filterApps(apps, "gg").map { it.label })
        assertEquals(listOf("Telegram X"), filterApps(apps, "x").map { it.label })
        assertEquals(listOf("AnkiDroid"), filterApps(apps, "ANKI").map { it.label })
        assertEquals(listOf("Maps"), filterApps(apps, "apps.maps").map { it.label })
        assertEquals(apps.map { it.label }, filterApps(apps, "  ").map { it.label })
    }

    @Test
    fun soleSearchMatchRequiresExactlyOneHit() {
        val apps = listOf(
            app("com.android.chrome", "Chrome"),
            app("com.google.android.calendar", "Calendar"),
            app("com.android.deskclock", "Clock"),
        )
        assertNull(soleSearchMatch("", filterApps(apps, "")))
        assertNull(soleSearchMatch("   ", filterApps(apps, "   ")))
        assertNull(soleSearchMatch("c", filterApps(apps, "c")))
        assertEquals("Calendar", soleSearchMatch("cal", filterApps(apps, "cal"))?.label)
        assertNull(soleSearchMatch("missing", filterApps(apps, "missing")))
        val only = listOf(app("only", "Only"))
        assertEquals(listOf("Only"), filterApps(only, "").map { it.label })
        assertNull(soleSearchMatch("", filterApps(only, "")))
        assertEquals("Only", soleSearchMatch("on", filterApps(only, "on"))?.label)
    }

    @Test
    fun defaultClockAndCalendarIntents() {
        val calendar = DefaultApps.calendar(1_700_000_000_000L)
        assertEquals(DefaultApps.ACTION_MAIN, calendar[0].action)
        assertEquals(listOf(DefaultApps.CATEGORY_APP_CALENDAR), calendar[0].categories)
        assertEquals(false, calendar[0].openLauncher)
        assertEquals(DefaultApps.ACTION_VIEW, calendar[1].action)
        assertEquals("content://com.android.calendar/time/1700000000000", calendar[1].data)
        val clock = DefaultApps.clock()
        assertEquals(DefaultApps.ACTION_MAIN, clock[0].action)
        assertEquals(listOf(DefaultApps.CATEGORY_APP_CLOCK), clock[0].categories)
        assertEquals(false, clock[0].openLauncher)
        assertEquals(DefaultApps.ACTION_SHOW_ALARMS, clock[1].action)
        assertEquals(true, clock[1].openLauncher)
        assertEquals(350L, SOLE_MATCH_OPEN_DELAY_MS)
        assertEquals(true, isSystemChooser(null, null))
        assertEquals(true, isSystemChooser("android", "com.android.internal.app.ResolverActivity"))
        assertEquals(true, isSystemChooser("com.android.intentresolver", "com.android.intentresolver.ChooserActivity"))
        assertEquals(false, isSystemChooser("com.android.deskclock", "com.android.deskclock.DeskClock"))
    }

    @Test
    fun alphabetJumpsToTheNextPresentLetter() {
        val apps = listOf(
            app("a", "AnkiDroid"),
            app("c", "Chrome"),
            app("v", "Вызовы"),
            app("z", "智谱清言"),
        )
        assertEquals("A", sectionKey("AnkiDroid"))
        assertEquals("АБВ", sectionKey("Вызовы"))
        assertEquals("АБВ", sectionKey("ёлка"))
        assertEquals("中文", sectionKey("智谱清言"))
        assertEquals("#", sectionKey("123 Go"))
        assertEquals("•", sectionKey("Αθήνα"))
        assertEquals(0, jumpIndex(apps, "A"))
        assertEquals(1, jumpIndex(apps, "B"))
        val head = listOf(app("a", "AnkiDroid"), app("c", "Chrome"))
        assertEquals(1, jumpIndex(head, "Z"))
        assertEquals(2, jumpIndex(apps, "АБВ"))
        assertEquals(3, jumpIndex(apps, "中文"))
        val rail = railLetters(apps)
        assertEquals("#", rail.first().key)
        assertEquals("Z", rail[26].key)
        assertEquals("АБВ", rail[27].key)
        assertEquals("中文", rail[28].key)
        assertEquals(29, rail.size)
        assertEquals(false, rail.first { it.key == "B" }.enabled)
        assertEquals(true, rail.first { it.key == "АБВ" }.enabled)
        assertEquals(true, rail.first { it.key == "中文" }.enabled)
        assertEquals(0, railIndexAt(0f, 100f, rail.size))
        assertEquals(rail.lastIndex, railIndexAt(100f, 100f, rail.size))
    }

    @Test
    fun unfilteredListGroupsDigitsLatinCyrillicThenCjk() {
        val sorted = sortApps(
            listOf(
                app("zh", "微信"),
                app("ru", "Яндекс"),
                app("en", "Chrome"),
                app("num", "123 Go"),
                app("a", "AnkiDroid"),
            ),
        )
        assertEquals(listOf("123 Go", "AnkiDroid", "Chrome", "Яндекс", "微信"), sorted.map { it.label })
    }

    @Test
    fun favouritesToggleAndMove() {
        assertEquals(listOf("a", "b"), toggleFavourite(listOf("a"), "b"))
        assertEquals(listOf("a"), toggleFavourite(listOf("a", "b"), "b"))
        assertEquals(listOf("b", "a", "c"), moveFavourite(listOf("a", "b", "c"), "a", 1))
        assertEquals(listOf("a", "b", "c"), moveFavourite(listOf("a", "b", "c"), "a", -1))
        val apps = listOf(app("b", "B"), app("a", "A"), app("c", "C"))
        assertEquals(listOf("b", "c"), resolveFavourites(listOf("b", "missing", "c"), apps).map { it.component })
    }

    @Test
    fun batteryTextAndEstimate() {
        val threeHours = estimateRemainingMillis(3_000_000L, 1_000_000L)
        assertEquals(10_800_000L, threeHours)
        assertNull(estimateRemainingMillis(0L, 1_000_000L))
        assertNull(estimateRemainingMillis(3_000_000L, Long.MIN_VALUE))
        assertEquals(
            "66% 3h",
            formatBattery(BatteryStatus(66, charging = false, remainingMillis = threeHours)),
        )
        assertEquals(
            "66%",
            formatBattery(BatteryStatus(66, charging = true, remainingMillis = threeHours)),
        )
        assertEquals(
            "40% 40m",
            formatBattery(BatteryStatus(40, charging = false, remainingMillis = 40 * 60_000L)),
        )
    }

    @Test
    fun openMeteoParsesTheCurrentBlock() {
        val body = """
            {"current":{"time":"2026-10-05T13:00","temperature_2m":5.2,"weather_code":2}}
        """.trimIndent()
        val weather = parseOpenMeteo(body)
        assertEquals(Weather(5, 2), weather)
        assertEquals("5°C переменная облачность", formatWeather(weather!!, Locale("ru")))
        assertEquals("5°C partly cloudy", formatWeather(weather, Locale.ENGLISH))
    }

    private fun app(component: String, label: String) = LaunchableApp(
        component = component,
        packageName = component,
        label = label,
    )
}

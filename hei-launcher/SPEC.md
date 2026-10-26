# hei-launcher — Specification

## Purpose

**hei-launcher** is a personal Android home screen. It opens apps from large text labels, with no icons and no decorative chrome, so the list stays fast and hard to get lost in.

| Field | Value |
|-------|-------|
| App name | hei-launcher |
| Package | `com.tepmex.heilauncher` |
| Min SDK | 34 (Android 14+) |
| Target / compile SDK | 36 |

Four surfaces, in pager order (left to right):

1. **Fitness averages** — one swipe right past the day clock. A headerless table of Mi Fitness steps and sleep, using the same Xiaomi sign-in as the day clock.
2. **Day clock** — swipe favourites right. Same behaviour as [ideal-timing](../ideal-timing/SPEC.md): Xiaomi / Mi Fitness wake time, 16-hour four-sector dial, sunrise and sunset, meal and dog-walk markers, NFC check-in, and same-day cue notifications.
3. **Favourites** — the page the launcher opens on. Clock, date, life, year, and day progress, battery, weather, pinned apps.
4. **All apps** — swipe favourites left. Substring search and an alphabet rail.

A gear at the bottom of favourites opens the device Settings app. An ellipsis beside it opens this launcher’s own settings.

## Requirements

1. Act as a HOME screen (`MAIN` / `HOME` / `DEFAULT`) and also be launchable from another launcher while it is not the default.
2. Favourites screen, top to bottom, with a tight header:
   - Clock (`h:mm a` or `H:mm`, following the setting below).
   - Date. English: `Monday, 5th October`. Russian: `понедельник, 5 октября`. Other locales: localized `EEEE, d MMMM`.
   - Life progress, above the year and day bars, always shown. One pixel per local day from **21.01.1993** through **21.01.2083** inclusive (**32873** pixels), packed down each column, then into the next column on the right, so filled days grow from the left edge. Column heights differ by at most one pixel. Pixels through today are filled (`#F2F2F2`). Pixels after today through **21.01.2063** inclusive are unfilled and lighter (`#8A8A8A`) than the unfilled pixels after that date (`#3A3A3A`). To the right of the bar, the whole civil hours until **21.01.2063 00:00** local, truncated, with no unit and no grouping. Partial hours are dropped; daylight-saving shifts are ignored (every local day is 24 hours). At 2026-10-21 13:02 the number is **317770**. On that date the bar has **12327** filled pixels, **13241** lighter unfilled pixels, and **7305** darker pixels. The bar’s column count is the pixel width left after the hour number, and its row count is whatever is needed to place every day, so the number stays on the row. No caption.
   - Year progress: dithered bar only. No “year in progress” caption. The truncated percent (`75%`) sits in small type to the right of the bar. Percent is completed local days: `(dayOfYear - 1) * 100 / lengthOfYear`, truncated. The last day of the year is 100%. The bar uses that same percent. 5 October 2026 is **75%**.
   - Day progress: dithered bar only. No “day in progress” caption. The truncated percent (`54%`) sits in small type to the right of the bar. Percent is truncated elapsed local seconds: `secondOfDay * 100 / 86400`. 13:02 is **54%**. The bar uses the exact fraction of the day.
   - Battery `66%` plus a coarse remaining-time estimate when discharging (`3h`, or `40m` under an hour). Charging shows percent only.
   - Weather `5°C переменная облачность` when a fix and a successful fetch exist. Open-Meteo, no API key. Hidden when there is nothing to show. No error text on the home screen.
   - Internet, on the next line under battery and weather. One status from HTTPS probes of `ya.ru`, `google.com`, and `youtube.com`. All three reachable: **СВОБОДНЫЙ** / **FREE**. Only ya.ru: **БЕЛЫЙ СПИСОК** / **WHITELIST**. ya.ru and google.com, without youtube: **РОССИЙСКИЙ** / **RUSSIAN**. None: **НЕТ ИНТЕРНЕТА** / **NO INTERNET**. Every other mix: **ИНОСТРАННЫЙ** / **FOREIGN**. Any completed HTTP response counts as reachable, including redirects and error statuses; the redirect is not followed. Timeout, TLS failure, and connection failure count as unreachable. Each site is limited to 30 seconds, and the three probes run together. The row refreshes when favourites is the settled page and the activity is started, again when Home is pressed while favourites is already showing, and when the refresh icon on that row is tapped. It does not refresh on the clock tick. A probe already running ignores another tap; the previous status stays until the new one arrives. Before the first result the row shows an ellipsis.
3. Pinned apps are text only. Never call `loadIcon` or decode drawables for the list.
4. Each app row is the label only. Nothing is drawn beside the name: no time since last open, no counts, no badges.
5. Pager order is fitness averages, day clock, favourites, all apps. The launcher opens on favourites. Swipe favourites left to the full launcher-activity list. Swipe favourites right to the day clock, and right again to fitness averages. From any other page, swipe back or Back returns to favourites. The Home intent returns to favourites and clears the query. A saved pager index from the older three-page order is discarded once, so an upgrade opens on favourites.
6. Search filters by case-insensitive substring on the label and the package name. Rank: label prefix, then word prefix (split on space, hyphen, underscore, dot), then label contains, then package contains. Empty query keeps script-grouped order: non-letters, Latin, Cyrillic, CJK, then any other script. Within a group, the device collator, then component name.
7. The alphabet rail is a fixed strip that always fits on screen: `#`, `A`–`Z`, `АБВ`, `中文`. `#` jumps to the first name that does not start with a letter. Each Latin letter jumps to the first name starting with that letter. `АБВ` jumps to the first Cyrillic name. `中文` jumps to the first CJK name. Keys with no apps are dim and jump to the next present section. The rail is hidden while the query is non-empty.
8. The day-clock page implements [ideal-timing](../ideal-timing/SPEC.md): browser or WebView Xiaomi sign-in, persisted wake time, 16-hour dial that freezes at 16h, sector labels, offline sunrise/sunset, meal and 19:00 dog-walk markers, same-day exact-alarm cues, and foreground NFC check-in. NFC reader mode is on only while that page is the current page and the activity is resumed. Notification and location prompts are requested when the user opens the page, not on every visit to favourites. Pref files are `hei_timing_auth`, `hei_timing_wake`, `hei_timing_geo`, `hei_timing_nfc_checkin`, and `hei_timing_section_alarms`.
9. Tap an app to launch its launcher activity. Long-press a favourite to unpin it. Long-press a row in the full list to pin or unpin. New pins go to the end.
10. Tap the favourites clock to open the default clock. Tap the favourites date to open the default calendar. Clock resolution order: `MAIN` + `android.intent.category.APP_CLOCK`, then the launcher activity of the app that handles `SHOW_ALARMS` (the alarms intent itself if that app has no launcher activity, or a chooser when several clocks are installed and none is the default). Calendar resolution order: `MAIN` + `CATEGORY_APP_CALENDAR`, then `VIEW` of `content://com.android.calendar/time/{now}`. If nothing resolves, show the existing “Can’t open” toast.
11. Gear opens `Settings.ACTION_SETTINGS`. Ellipsis opens the launcher settings screen.
12. Launcher settings (v1):
    - Clock: system / 12-hour / 24-hour.
    - Show or hide year progress, day progress, battery, weather.
    - Name size S / M / L (default L).
    - Reorder and remove favourites.
    - Shortcuts: coarse location, request the Home role.
13. Persist preferences and favourite order in `SharedPreferences` so the first frame does not wait on DataStore.
14. Cache resolved labels in memory. Invalidate on package added / removed / changed. Do not reload labels on every resume.
15. Poll clock and battery only while the home activity is started. Weather network at most every 30 minutes when a cached reading exists, and at most one failed attempt every 10 minutes. The internet probe is not part of that poll: it runs on entering favourites and from the refresh icon, with a 30 second cap per site.
16. Favourites and the app list use a solid black window. No wallpaper, badges, folders, widgets, or icon packs. The day-clock page uses the ideal-timing parchment dial.
17. On the all-apps page, a non-empty query that matches exactly one app opens that app after a 350 ms pause. More typing before the pause cancels the open. The keyboard Done action opens that single app immediately. The query is cleared after a successful open, so leaving the page and coming back does not open it again. A blank query never auto-opens, including when only one app is installed. The open runs only while the all-apps page is settled and the activity is still resumed.
18. Sign release (and debug when the keystore is present) with the shared committed sideload keystore. Publish a GitHub Pages landing at `/hei-launcher/` with `hei-launcher.apk`.
19. Fitness averages page, to the left of the day clock. Black background, white text, no column header and no row labels. Three columns, left to right: mean for the calendar year, the calendar month, and the Monday-start week, each through today. Two rows: steps, then sleep duration in hours with two digits after the decimal separator. Days with no Mi Fitness record are left out of the mean (a recorded `0` steps day counts; a sleep record with no duration does not). Steps are shown as a rounded integer. Each number is compared with the same mean computed for the window that ended yesterday. If the displayed value rose, a small upward triangle sits to the right of the number; if it fell, a downward triangle. No triangle when the displayed values match, when today’s window is empty, or when yesterday’s window has no records. On 1 January the year and month windows ending yesterday are the previous year and December. The page uses the day clock’s Xiaomi session (`hei_timing_auth`). Signed out: one line telling the user to sign in on the day clock. Fetch runs while the page is the current page, at most every 30 minutes, and again when the local date changes. Requests are `get_aggregated_fitness_data_by_time` with `tag=daily_report`, key `steps` or `sleep`, in 30-day chunks from the earliest day any current or yesterday window needs. The table is not persisted.

## Interfaces

### Android

| Intent | Use |
|--------|-----|
| `MAIN` + `HOME` + `DEFAULT` | Home screen |
| `MAIN` + `LAUNCHER` | Open from another launcher |
| `Settings.ACTION_SETTINGS` | Gear |
| `RoleManager.ROLE_HOME` | Default-launcher row |
| `MAIN` + `LAUNCHER` component | Open a pinned or listed app |
| `MAIN` + `android.intent.category.APP_CLOCK`, then `SHOW_ALARMS` | Favourites clock tap. `SHOW_ALARMS` opens that app’s launcher activity |
| `MAIN` + `CATEGORY_APP_CALENDAR`, then `VIEW content://com.android.calendar/time/{now}` | Favourites date tap |

Permissions: `QUERY_ALL_PACKAGES`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `INTERNET`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `VIBRATE`, `NFC` (hardware optional).

Day-clock network, sleep parsing, dial math, and cue copy follow [ideal-timing](../ideal-timing/SPEC.md). The section-change broadcast action is `com.tepmex.heilauncher.timing.action.SECTION_CHANGE`. Notification taps open this launcher.

Fitness averages use the same encrypted health client and the same `relative_uid`:

`GET /app/v1/data/get_aggregated_fitness_data_by_time` with `key=steps` or `key=sleep`, `tag=daily_report`, `start_time`, `end_time`, `limit`.

The day is the aggregate item’s `time`. Steps are `value.steps`. Sleep hours are `value.total_duration` minutes divided by 60 (segment `duration` minutes when `total_duration` is missing or zero).

### Weather

`GET https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=temperature_2m,weather_code`

WMO `weather_code` 2 maps to **переменная облачность** / **partly cloudy**. Temperature is rounded half-up to whole degrees.

### Internet

`GET https://ya.ru/`, `GET https://google.com/`, and `GET https://youtube.com/`, in parallel. Call, connect, and read timeouts are 30 seconds. No retry and no redirect following. A response status of any kind means the site is up.

| ya.ru | google.com | youtube.com | Status |
|-------|------------|-------------|--------|
| yes | yes | yes | СВОБОДНЫЙ / FREE |
| yes | no | no | БЕЛЫЙ СПИСОК / WHITELIST |
| yes | yes | no | РОССИЙСКИЙ / RUSSIAN |
| no | no | no | НЕТ ИНТЕРНЕТА / NO INTERNET |
| anything else | | | ИНОСТРАННЫЙ / FOREIGN |

### Battery estimate

While discharging, `BATTERY_PROPERTY_CHARGE_COUNTER` (µAh) / `|BATTERY_PROPERTY_CURRENT_NOW|` (µA) = hours. Discard non-positive, unknown (`Long.MIN_VALUE`), or out-of-range results (under 3 minutes or over 200 hours).

## Data model

| Entity | Store | Fields |
|--------|-------|--------|
| `LauncherPrefs` | `SharedPreferences` `hei_launcher` | clock format, four visibility flags, name size, ordered favourite component names (`package/class` joined by newlines) |
| `LaunchableApp` | memory cache | component, package, label |
| Weather cache | same prefs file | rounded °C, WMO code, fetched-at epoch |
| Internet status | memory, while the process lives | last probe result; not written to prefs |
| Fitness averages | memory only, while the page is open | per-day steps and sleep hours, then year / month / week means |

Uninstalled components are dropped from the favourite list the next time the package set loads non-empty.

## UI / UX

- Black background, white text, no app icons.
- Favourites: compact header (clock, date, life pixel bar with the hour count on the right, year and day bars with a small percent on the right, battery and weather on one line, internet status and a refresh icon on the next line), scrolling names with nothing beside them, gear and `...` pinned to the bottom above the navigation bar. The clock and the date are buttons: clock opens the default clock app, date opens the default calendar app. The refresh icon runs the internet probe again.
- All apps: underlined search field (placeholder **Search apps** / **Поиск приложений**), scrolling names with nothing beside them, a rail of `#` `A`–`Z` `АБВ` `中文` that fits the viewport only while the query is empty. A query that leaves exactly one app opens it on its own.
- Fitness averages: a centered 3×2 table on black. No header. Upward or downward triangle to the right of a number when that mean rose or fell against yesterday’s window.
- Day clock: ideal-timing login, then the parchment 16-hour dial. Status-bar icons switch to dark on that page and back to light on the black pages, including fitness averages.
- Settings uses the same black text UI. System back closes it.
- Empty favourites: one line telling the user to swipe left and hold an app.
- Name sizes: favourites 28 / 36 / 44 sp, drawer 22 / 28 / 34 sp.

## Out of scope

- Icon packs, widgets, folders, badges, wallpapers, gestures other than swipe and long-press.
- Time since an app was last opened, or any other number beside an app name.
- Hiding apps, work-profile separation, search of contacts or web.
- Editing weather providers, themes, or custom fonts.
- A removable “×” on the progress widgets.
- Predicting battery time while charging.

## Acceptance criteria

1. `./gradlew test assembleRelease` succeeds. Release APK is signed with the shared sideload certificate (`android/verify-apk-sideload-cert.sh`).
2. Unit tests cover year 75% and day 54% at 2026-10-05 13:02, English ordinal date, search rank, a unique search hit versus a blank query, the default clock and calendar intent order, alphabet jump (`#`, Latin, `АБВ`, `中文`) and script grouping, battery text, Open-Meteo parsing (`weather_code` 2 → переменная облачность), the life-span pixel counts and **317770** hours at 2026-10-21 13:02, fitness year/month/week means and yesterday-window triangles, steps and sleep JSON parsing, internet status for every ya.ru / google.com / youtube.com combination (including the Russian labels), a 30 second HTTPS probe cap, and the ideal-timing clock, sleep, sun, cue, and NFC cases.
3. Favourites opens in the middle. Swipe left into the full list; swipe right into the day clock; swipe right again into fitness averages; Back from any of those returns to favourites. Search keeps only substring matches; a single match opens that app; gear opens device settings; ellipsis opens launcher settings. The favourites clock opens the clock app and the date opens the calendar app.
4. The app list path does not load icons.

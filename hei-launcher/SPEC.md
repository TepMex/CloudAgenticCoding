# hei-launcher — Specification

## Purpose

**hei-launcher** is a personal Android home screen. It opens apps from large text labels, with no icons and no decorative chrome, so the list stays fast and hard to get lost in.

| Field | Value |
|-------|-------|
| App name | hei-launcher |
| Package | `com.tepmex.heilauncher` |
| Min SDK | 34 (Android 14+) |
| Target / compile SDK | 36 |

Two surfaces:

1. **Favourites** — clock, date, year and day progress, battery, weather, pinned apps.
2. **All apps** — reached by swiping the favourites screen left. Substring search and an alphabet rail.

A gear at the bottom of favourites opens the device Settings app. An ellipsis beside it opens this launcher’s own settings.

## Requirements

1. Act as a HOME screen (`MAIN` / `HOME` / `DEFAULT`) and also be launchable from another launcher while it is not the default.
2. Favourites screen, top to bottom:
   - Clock (`h:mm a` or `H:mm`, following the setting below).
   - Date. English: `Monday, 5th October`. Russian: `понедельник, 5 октября`. Other locales: localized `EEEE, d MMMM`.
   - Year progress label and dithered bar. Percent is completed local days: `(dayOfYear - 1) * 100 / lengthOfYear`, truncated. The last day of the year is 100%. The bar uses that same percent. 5 October 2026 is **75%**.
   - Day progress label and dithered bar. Percent is truncated elapsed local seconds: `secondOfDay * 100 / 86400`. 13:02 is **54%**. The bar uses the exact fraction of the day.
   - Battery `66%` plus a coarse remaining-time estimate when discharging (`3h`, or `40m` under an hour). Charging shows percent only.
   - Weather `5°C переменная облачность` when a fix and a successful fetch exist. Open-Meteo, no API key. Hidden when there is nothing to show. No error text on the home screen.
3. Pinned apps are text only. Never call `loadIcon` or decode drawables for the list.
4. When usage access is granted and the preference is on, show time since last use on the right: `<1m`, `26m`, `1h 38m`, `3h`, `2d`.
5. Swipe favourites left to the full launcher-activity list. Swipe right, or Back, returns to favourites. The Home intent returns to favourites and clears the query.
6. Search filters by case-insensitive substring on the label and the package name. Rank: label prefix, then word prefix (split on space, hyphen, underscore, dot), then label contains, then package contains. Empty query keeps alphabetical order.
7. An alphabet rail (`#`, `A`–`Z`, present Cyrillic letters, and `•` for other scripts) jumps the unfiltered list. Letters with no apps are dim and jump to the next present section.
8. Tap an app to launch its launcher activity. Long-press a favourite to unpin it. Long-press a row in the full list to pin or unpin. New pins go to the end.
9. Gear opens `Settings.ACTION_SETTINGS`. Ellipsis opens the launcher settings screen.
10. Launcher settings (v1):
    - Clock: system / 12-hour / 24-hour.
    - Show or hide year progress, day progress, battery, weather, time since open.
    - Name size S / M / L (default L).
    - Reorder and remove favourites.
    - Shortcuts: usage access, coarse location, request the Home role.
11. Persist preferences and favourite order in `SharedPreferences` so the first frame does not wait on DataStore.
12. Cache resolved labels in memory. Invalidate on package added / removed / changed. Do not reload labels on every resume.
13. Poll clock, battery, and usage only while the home activity is started. Weather network at most every 30 minutes when a cached reading exists, and at most one failed attempt every 10 minutes.
14. Solid black window. No wallpaper, badges, folders, widgets, or icon packs.
15. Sign release (and debug when the keystore is present) with the shared committed sideload keystore. Publish a GitHub Pages landing at `/hei-launcher/` with `hei-launcher.apk`.

## Interfaces

### Android

| Intent | Use |
|--------|-----|
| `MAIN` + `HOME` + `DEFAULT` | Home screen |
| `MAIN` + `LAUNCHER` | Open from another launcher |
| `Settings.ACTION_SETTINGS` | Gear |
| `Settings.ACTION_USAGE_ACCESS_SETTINGS` | Usage-access row |
| `RoleManager.ROLE_HOME` | Default-launcher row |
| `MAIN` + `LAUNCHER` component | Open a pinned or listed app |

Permissions: `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS` (special, granted in system settings), `ACCESS_COARSE_LOCATION`, `INTERNET`.

### Weather

`GET https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=temperature_2m,weather_code`

WMO `weather_code` 2 maps to **переменная облачность** / **partly cloudy**. Temperature is rounded half-up to whole degrees.

### Battery estimate

While discharging, `BATTERY_PROPERTY_CHARGE_COUNTER` (µAh) / `|BATTERY_PROPERTY_CURRENT_NOW|` (µA) = hours. Discard non-positive, unknown (`Long.MIN_VALUE`), or out-of-range results (under 3 minutes or over 200 hours).

## Data model

| Entity | Store | Fields |
|--------|-------|--------|
| `LauncherPrefs` | `SharedPreferences` `hei_launcher` | clock format, five visibility flags, name size, ordered favourite component names (`package/class` joined by newlines) |
| `LaunchableApp` | memory cache | component, package, label, optional `lastUsedAt` |
| Weather cache | same prefs file | rounded °C, WMO code, fetched-at epoch |

Uninstalled components are dropped from the favourite list the next time the package set loads non-empty.

## UI / UX

- Black background, white text, no app icons.
- Favourites: fixed header, scrolling names, gear and `...` pinned to the bottom above the navigation bar.
- All apps: underlined search field (placeholder **Search apps** / **Поиск приложений**), scrolling names, alphabet rail only while the query is empty.
- Settings uses the same black text UI. System back closes it.
- Empty favourites: one line telling the user to swipe left and hold an app.
- Name sizes: favourites 28 / 36 / 44 sp, drawer 22 / 28 / 34 sp.

## Out of scope

- Icon packs, widgets, folders, badges, wallpapers, gestures other than swipe and long-press.
- Hiding apps, work-profile separation, search of contacts or web.
- Editing weather providers, themes, or custom fonts.
- A removable “×” on the progress widgets.
- Predicting battery time while charging.

## Acceptance criteria

1. `./gradlew test assembleRelease` succeeds. Release APK is signed with the shared sideload certificate (`android/verify-apk-sideload-cert.sh`).
2. Unit tests cover year 75% and day 54% at 2026-10-05 13:02, English ordinal date, relative time, search rank, alphabet jump, battery text, and Open-Meteo parsing (`weather_code` 2 → переменная облачность).
3. Favourites swipe left into the full list; search keeps only substring matches; gear opens device settings; ellipsis opens launcher settings.
4. The app list path does not load icons.

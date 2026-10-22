# НЕДЕЛЯ (My Week) — Specification

## Purpose

**НЕДЕЛЯ** is a personal Android checklist for one weekly sprint. On Monday the user builds the week as colored blocks of emoji tasks. From Tuesday through Sunday they only tick tasks off. The next Monday archives that week with its results and opens a new draft copied from it, with every tick cleared.

The UI is Russian. The app is local-only and built so a year of history stays a single flat list, not a nested scroll.

| Field | Value |
| ----- | ----- |
| App name | НЕДЕЛЯ |
| Package | `com.tepmex.myweek` |
| Min / target / compile SDK | 36 |
| UI | Jetpack Compose, Material 3, dynamic color |

## Requirements

1. Planning a sprint is possible only on Monday, until local midnight on the device clock. Tuesday through Sunday the same sprint stays open for ticking tasks, and its blocks and tasks cannot be added, edited, or deleted.
2. A block has a name and a color. The color is the block background at high transparency (about 18% in light theme, about 38% in dark theme) so the checklist text stays readable. The user picks the color while planning.
3. A plus control on a block opens a dialog to add a task. A task has an emoji and a description. While planning is open, a task can be edited or deleted, and a block can be renamed, recolored, or deleted.
4. Each task is a checklist row. Ticks can be changed every day of that sprint, including Monday.
5. When Monday arrives, the previous sprint is archived with its ticks intact. The archive is a feed of every archived sprint, with a date separator for the Monday that sprint started. Older weeks are above, the newest week is at the bottom. Opening the archive lands on the newest week. The user scrolls up for earlier weeks.
6. The same Monday creates the new sprint immediately by copying the previous sprint’s blocks, names, colors, emoji, and descriptions. All ticks are cleared. If there has never been a sprint, Monday creates an empty one. If the app is opened after Monday (Tuesday–Sunday) and a previous sprint exists, that rollover still happens: the old sprint is archived and the current week is the copy, but planning stays closed because Monday is over. The first install on a non-Monday does not invent an empty locked sprint.
7. Settings contain **Отладочный режим**. While it is on, the screen shows test controls: **Новый понедельник** (jump to the next Monday and run rollover), **Обычный день** (if today is Monday, move to Tuesday of that week; otherwise leave the date and say planning is already closed), **Следующий день**, **Реальное время** (drop the override and use the device date), and **Очистить данные**. The home screen shows the date the app is using. Debug mode writes into the same database. Turning debug off returns to the device date.
8. Release and debug builds sign with the shared sideload keystore when it is present. GitHub Pages publishes `/my-week/my-week.apk`.

## Interfaces

- Launcher activity `com.tepmex.myweek.MainActivity` (`singleTop`).
- No network permission, no account, no widget, no reminder alarm.
- Storage: Room database `my-week.db` (write-ahead logging). Preferences file `my_week_settings` for the debug flag and the override epoch day.
- Clock: `ZoneId.systemDefault()` and `LocalDate`. A sprint’s identity is the Monday of its week, stored as an epoch day. Debug override replaces that date until it is cleared.
- Rollover runs when the process starts, when the activity resumes, and at the next local midnight if the screen stays on and the clock is not overridden. It is idempotent.

## Data model

`Sprint`

| Field | Meaning |
| ----- | ------- |
| `id` | Stable row id |
| `weekStart` | Monday the sprint belongs to, local date |
| `archived` | Set when a later Monday rolls the sprint out of the current week |

At most one sprint exists per `weekStart`.

`Block`: `sprintId`, `title`, `colorKey`, `position`.

`Task`: `blockId`, `emoji`, `title`, `done`, `position`.

Color keys: `blue`, `teal`, `green`, `amber`, `orange`, `rose`, `violet`, `slate`.

Rollover plan for a local date `today`, with `monday = weekStart(today)`:

- Archive every open sprint whose Monday is before `monday`.
- If a sprint for `monday` exists and is archived, un-archive it (clock moved backward onto that week).
- If no sprint exists for `monday`, create one when `today` is Monday or any earlier sprint exists. The copy source is the latest sprint before `monday`. A null source means an empty sprint. Copied tasks are not done.
- A sprint whose Monday is still in the future relative to `today` is left untouched.

`canPlan(today, weekStart, archived)` is true only when the sprint is not archived, `today` is Monday, and `weekStart` is that Monday.

`canCheck` is true for the open sprint of the current week on every weekday.

## UI / UX

- Bottom navigation: **Неделя**, **Архив**, **Настройки**.
- **Неделя** is one `LazyColumn`: week range, phase, progress count, then blocks. Block headers and task rows are separate list items (no nested lazy list) so a long week scrolls as one list. While planning, **Новый блок** stays pinned above the navigation bar, and each block has add-task and edit controls.
- Task and block dialogs scroll, grow with the system font, and keep fields above the keyboard. Save stays disabled while the name or description is blank. Emoji defaults to `•` when the field is empty.
- **Архив** is one `LazyColumn` of date separators, blocks, and read-only checklist rows, oldest week first. The initial position is the last row.
- **Настройки** holds the debug switch, the date the app is using, the test buttons, and the version.
- Dynamic color follows the system theme. Checklist ticks use the primary color. Done rows stay readable and are drawn in a quieter tone.
- Edge-to-edge. Text uses scalable Material styles. Rows are at least 48dp. No fixed text height.

## Out of scope

- Accounts, sync, backup export, widgets, notifications, and Telegram import.
- Reordering blocks or tasks by drag.
- Editing an archived sprint.
- More than one sprint per week, subtasks, due times, and notes beyond emoji plus description.
- Filling skipped weeks with extra archived copies. Only the current week is created when several Mondays were missed.
- A locked empty sprint on a first install that happens after Monday.

## Acceptance criteria

1. On Monday, with no history, the app opens an empty sprint and allows blocks and tasks to be created, edited, and deleted. Ticks can be set the same day.
2. On Tuesday of that week the same tasks are visible, ticks still change, and there is no way to add, edit, or delete blocks or tasks.
3. The next Monday archives the week with its ticks, shows it in the archive under that Monday’s date, and opens a new sprint with the same blocks and tasks and no ticks. Planning is open again.
4. Opening the app on Wednesday after missing Monday, when an older sprint exists, archives the older sprint and shows the copied current week with planning closed.
5. A first launch on Wednesday with an empty database shows no sprint and does not accept structural edits.
6. The archive lists weeks from oldest to newest, with the newest at the bottom, and opens already scrolled to that newest week.
7. With debug mode on, **Новый понедельник**, **Обычный день**, **Следующий день**, and **Реальное время** move the app date as specified and the sprint rules follow that date. **Очистить данные** removes every sprint. With debug mode off, those controls are hidden and the device date is used.
8. `./gradlew test` passes the rollover, copy, and feed-order tests. The release APK is signed with the shared sideload certificate.

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
2. A block has a name, a default emoji, and a color. The color is the block background at high transparency (about 18% in light theme, about 38% in dark theme) so the checklist text stays readable. The user picks the color and the default emoji while planning. The block header shows that emoji in front of the name. Changing the block emoji does not rewrite tasks that already exist.
3. A plus control on a block opens a dialog to add a task. A task has an emoji and a description. The emoji field opens with the block’s default emoji. While planning is open, a task can be edited or deleted, and a block can be renamed, recolored, given another default emoji, or deleted.
4. Each task is a checklist row. Ticks can be changed every day of that sprint, including Monday.
5. When Monday arrives, the previous sprint is archived with its ticks intact. The archive is a feed of every archived sprint, with a date separator for the Monday that sprint started. Older weeks are above, the newest week is at the bottom. Opening the archive lands on the newest week. The user scrolls up for earlier weeks.
6. The same Monday creates the new sprint immediately by copying the previous sprint’s blocks, names, colors, default emoji, task emoji, and descriptions. All ticks are cleared. If there has never been a sprint, Monday creates an empty one. If the app is opened after Monday (Tuesday–Sunday) and a previous sprint exists, that rollover still happens: the old sprint is archived and the current week is the copy, but planning stays closed because Monday is over. The first install on a non-Monday does not invent an empty locked sprint.
7. The emoji picker offers a fixed built-in set plus any emoji the user has saved on a task or as a block default. An emoji typed into either field is remembered after a successful save, including after the tasks that used it are gone. The list is ordered by how many stored tasks currently use each emoji, highest first. Equal counts keep the built-in order, then the order the extra emoji was first remembered. Emoji that already exist on tasks but were never written to the remembered list still appear, so an upgrade does not hide them. **Очистить данные** forgets the remembered list. At most 64 remembered emoji are kept; the oldest drop off.
8. Settings contain **Отладочный режим**. While it is on, the screen shows test controls: **Новый понедельник** (jump to the next Monday and run rollover), **Обычный день** (if today is Monday, move to Tuesday of that week; otherwise leave the date and say planning is already closed), **Следующий день**, **Реальное время** (drop the override and use the device date), and **Очистить данные**. The home screen shows the date the app is using. Debug mode writes into the same database. Turning debug off returns to the device date.
9. Release and debug builds sign with the shared sideload keystore when it is present. GitHub Pages publishes `/my-week/my-week.apk`.
10. Each archived week can be copied as plain text. The copy includes the Monday date, every block emoji and name, and every task emoji, description, and status (`выполнено` or `не выполнено`).
11. **Шаг** offers the current week’s unfinished tasks for pairwise comparison and shows their current rating. Numbers at the start or end of a description are ignored, so numbered variants of one affair are one option. The emoji is kept. A finished task is not listed and is not paired. The option stays while at least one task in the group is unfinished. **Неделя** stays the tab selected at launch.

## Interfaces

- Launcher activity `com.tepmex.myweek.MainActivity` (`singleTop`).
- No network permission, no account, no widget, no reminder alarm.
- Storage: Room database `my-week.db` (write-ahead logging), schema version 3. Preferences file `my_week_settings` for the debug flag, the override epoch day, and `remembered_emoji` (a JSON array of custom emoji, oldest first). `step_ratings` stores one Elo row per normalized affair. `step_comparisons` stores the pair history that pairing and undo read. **Очистить данные** deletes both tables with the sprints.
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

`Block`: `sprintId`, `title`, `colorKey`, `emoji`, `position`. `emoji` is the default for new tasks in that block. Existing databases gain the column as `•`.

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

- Bottom navigation: **Неделя**, **Шаг**, **Архив**, **Настройки**. **Неделя** is the selected tab when the process starts.
- **Неделя** is one `LazyColumn`: week range, phase, progress count, then blocks. Block headers and task rows are separate list items (no nested lazy list) so a long week scrolls as one list. While planning, **Новый блок** stays pinned above the navigation bar, and each block has add-task and edit controls.
- Task and block dialogs scroll, grow with the system font, and keep fields above the keyboard. Save stays disabled while the name or description is blank. An empty emoji field is stored as `•`. A new block starts with `•`. A new task starts with its block’s emoji. Both dialogs show the same frequency-sorted emoji row and a text field for typing another one.
- **Шаг** sits between **Неделя** and **Архив**. It chooses the next affair from the current week’s unfinished tasks. Descriptions that differ only by a number at the start or the end are one option: "20к шагов" and "40к шагов" are "к шагов"; "Книга 1" and "Книга 2" are "Книга". The emoji stays, both in the label and in the identity, so the same words with a different emoji are different options. A number in the middle of the description stays. The option remains while any task in the group is unfinished. If "Книга 1" is done and "Книга 2" is not, "Книга" is still offered. When every task in the group is done, the option leaves the rating and is not paired. Two modes: **Сравнение** (tap the affair to do next, or call a draw or a skip) and **Рейтинг** (current Elo, highest first). Pairing uses the balanced adaptive weights from `pair-comp-elo`: fewer comparisons, closer ratings, fewer head-to-head meetings, and avoiding the last 20 pairs. A choice updates Elo with K = 32 and scale = 400, starting from 1000. A skip does not move the rating. **Отменить выбор** restores the last comparison. Ratings are kept by the normalized key across weeks. There is no time decay.
- **Архив** is one `LazyColumn` of date separators, blocks, and read-only checklist rows, oldest week first. The initial position is the last row. Each week has **Скопировать план**. It puts that week’s whole plan on the clipboard as plain text: the Monday date, each block emoji and name, and each task emoji, description, and status `выполнено` or `не выполнено`.
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
8. `./gradlew test` passes the rollover, copy, feed-order, and emoji-order tests. The release APK is signed with the shared sideload certificate.
9. Creating or editing a block sets its default emoji. The header shows that emoji. A new task in the block opens with it. The next Monday copies the block emoji along with the tasks. Editing the block emoji leaves existing task emoji unchanged.
10. Saving a task with an emoji that is not in the built-in set adds that emoji to the picker. The picker lists the most-used emoji first, counting every stored task in the open week and the archive. A remembered emoji with no remaining tasks stays at the unused end of the list. Clearing data removes the remembered list.
11. On an archived week, **Скопировать план** copies that week’s full plan as plain text, including every emoji and either `выполнено` or `не выполнено` on each task.
12. **Шаг** is between **Неделя** and **Архив**. Opening the app selects **Неделя**. Unfinished tasks of the current week are paired with the balanced adaptive rule from pair-comp-elo, after edge numbers are removed and the emoji is kept. Finished tasks are absent from the rating and from pairing. A group with one finished task and one unfinished task still appears under the shared label. **Рейтинг** lists the current Elo of those open groups.

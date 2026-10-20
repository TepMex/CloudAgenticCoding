# INBOX — Specification

## Purpose

**INBOX** is a personal Android capture app. It stores thoughts, ideas, reminders, and arbitrary text in one place, with the capture path kept as short as a single field and one tap.

The UI is Russian. The layout is built for the largest system font size: text uses scalable styles, controls grow with their labels, and nothing important is clipped to a fixed height.

| Field | Value |
|-------|-------|
| App name | INBOX |
| Package | `com.tepmex.inbox` |
| Min / target / compile SDK | 36 (Android 16) |
| UI | Jetpack Compose, Material 3, dynamic color |

## Requirements

1. Open on the capture screen: a multiline text field and an **Отправить** button. The button is enabled only when the field is not blank (after trim).
2. Sending saves the trimmed text, clears the field immediately, and keeps focus so the next thought can be typed. The saved text appears in **Входящие**.
3. Bottom menu order, left to right: reminders (clock), inbox, center action, archive, settings (gear). The center control is always larger than the other four (`ooOoo`). Capture is the default screen.
4. On the capture screen the center control is a paper plane and sends the current text. It is inactive while the field is blank. On any other screen the center control is a written sheet and opens the capture screen. The draft is kept across screens.
5. **Входящие** lists unprocessed captures, newest first. Tap one to open **Обработать**, which shows the full text and two actions: **Обработано** and **Напомнить**.
6. **Обработано** removes the item from the inbox and adds it to the archive with the processing time.
7. **Напомнить** asks for a date, then a time (system dialogs, 24-hour mode follows the device). A future time removes the item from the inbox, shows it under reminders, and schedules an exact alarm that posts a high-importance notification. A time that is not in the future is rejected and the item stays in the inbox.
8. The notification permission is requested the first time the user chooses **Напомнить**, not at launch. If permission is denied, the reminder is still stored. The notification is posted only when permission allows it.
9. **Напоминания** lists reminders that have not fired yet, soonest first. **Отменить** removes the alarm and returns the text to the inbox.
10. When the alarm fires, the app posts the notification (title «Напоминание», body is the saved text) and moves the item to the archive. The archive time is the moment the alarm was delivered. Tapping the notification opens the archive. A second delivery for the same item does nothing.
11. After reboot or an app update, pending reminders are scheduled again. An alarm whose time has already passed is delivered immediately.
12. **Архив** lists closed items, newest close-time first. A processed note shows the creation time and the processing time. A fired reminder shows the creation time and the notification time. Long-press copies the text.
13. **Настройки** shows notification and exact-alarm status, toggles for save haptic, keyboard on open, and lock-screen text, live counts, archive clear with confirmation, and the version name.
14. A shared `text/plain` payload is saved straight into the inbox and the inbox screen is shown. Launch does not ask for notification permission.
15. Release and debug builds sign with the shared sideload keystore when it is present. GitHub Pages publishes `/inbox/inbox.apk`.

## Interfaces

- Launcher activity `com.tepmex.inbox.MainActivity` (`singleTop`).
- Share intent: `ACTION_SEND` + `text/plain` + `EXTRA_TEXT`.
- Notification content intent: `MainActivity` extra `inbox_open_archive_id` (the capture id).
- Reminder broadcast: explicit `ReminderReceiver` with extra `capture_id` and data `inbox://reminder/{id}`.
- Boot broadcast: `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` → reschedule.
- Permissions: `POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`.
- Storage: Room database `inbox.db`, table `captures`. Preferences file `inbox_settings`.
- Alarm: `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP)`. If exact alarms are refused, `setAndAllowWhileIdle`.

## Data model

`Capture`:

| Field | Meaning |
|-------|---------|
| `id` | Stable id, also the alarm identity |
| `text` | Trimmed capture |
| `createdAtMillis` | When it was sent |
| `place` | `INBOX`, `SCHEDULED`, or `ARCHIVE` |
| `processedAtMillis` | When **Обработано** was tapped |
| `reminderAtMillis` | Chosen reminder time |
| `notifiedAtMillis` | When the alarm was delivered |

Transitions:

- send → `INBOX`
- process → `ARCHIVE` + `processedAtMillis`
- remind → `SCHEDULED` + `reminderAtMillis` (future only, from `INBOX` only)
- cancel → `INBOX`, reminder time cleared, alarm cancelled
- fire → `ARCHIVE` + `notifiedAtMillis` (from `SCHEDULED` only)

Archive sort key: `processedAtMillis` or `notifiedAtMillis`, descending. Inbox sort: `createdAtMillis` descending. Reminders sort: `reminderAtMillis` ascending.

Active inbox rows and pending reminders are not duplicated in the archive. The archive is the set of items that have left those queues by being processed or by a delivered reminder.

## UI / UX

- Capture fills the space above **Отправить** and the menu. The field uses `bodyLarge` so the system font scale does the sizing. No fixed text heights.
- Bottom menu is icon-only (clock, inbox, plane or sheet, archive, gear) with TalkBack labels. Side targets are at least 56dp; the center circle is 72dp. The bar grows with insets instead of clipping.
- Screen titles name Напоминания, Входящие, Архив, Настройки, and Обработать. Capture has no title, so the field stays large.
- Process actions are full-width and stacked. Date and time use the platform pickers so large fonts stay in the system dialog.
- Edge-to-edge. The menu stays above the navigation bar and the IME.
- Tab changes and the process screen have no enter/exit animation. Back from process returns to the inbox; back from a tab returns to capture; back from capture leaves the app.
- Dynamic color follows the system theme. Save haptic uses `HapticFeedbackConstants.CONFIRM` when the toggle is on (default on).
- Keyboard opens on the capture screen when that toggle is on (default on). Autofill is suppressed on the activity so it does not cover the field.

## Out of scope

- Editing a saved capture, search, folders, widgets, cloud sync, recurring reminders, direct boot, and any API below 36.
- Play Store exact-alarm policy review. The app is sideloaded and declares `USE_EXACT_ALARM` because reminders are a core function.

## Acceptance criteria

1. Sending non-blank text clears the field and shows the text in the inbox; blank or whitespace does not create a row.
2. **Обработано** moves that row to the archive with both dates and removes it from the inbox.
3. **Напомнить** with a future time moves the row to reminders and an exact alarm is registered. Cancel returns it to the inbox and drops the alarm.
4. Delivering the alarm moves the row to the archive, stores the delivery time, and posts a notification when permission is granted. A second delivery does not duplicate the row.
5. Archive order is newest close-time first. Pending reminders and open inbox rows are absent.
6. Center plane sends only from capture and only when there is text. Center sheet from any other screen opens capture without clearing the draft.
7. At font scale 1.3 and 2.0 the five menu controls remain visible and tappable, the send button stays above the keyboard, and process actions stay on screen.
8. `./gradlew test` passes and `./gradlew assembleRelease` produces a sideload-signed APK.

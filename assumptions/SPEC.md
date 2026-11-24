# assumptions — Specification

## Purpose

**assumptions** (launcher name **Допущения**) is a personal Android journal for emotions, expectations, and apprehensions.

An apprehension is not a separate record. It is an expectation whose pleasure score is negative: the same object covers both looking forward to something and dreading it.

| Field | Value |
|-------|-------|
| App name | Допущения |
| Project | assumptions |
| Package | `com.tepmex.assumptions` |
| Language | Russian UI |
| Min SDK | 34 (Android 14+) |
| Target / compile SDK | 35 |
| Storage | Room, on device only |

## Requirements

1. Record an emotion by choosing it on an inverted Plutchik wheel (see Wheel).
2. Each saved emotion is one timestamped entry. The wheel can be used again immediately to record another emotion.
3. Create an expectation with three fields:
   - the future event (text)
   - the expected outcome (text)
   - attitude toward it: pleasure on the integer scale **−5…+5**, including **0**
4. The scale has eleven points. −5…−1 are unpleasant (an apprehension), 0 is neutral, +1…+5 are pleasant. The user must pick a point explicitly; nothing is preselected.
5. After the event happens, record attitude again on the same scale. A comment is optional.
6. Show a report as a **2×2 matrix**. Rows are the expectation beforehand (negative or positive). Columns are the feeling afterward (negative or positive). Each cell shows how many resolved expectations fall there.
7. Quadrant colors:
   - negative → negative: red
   - positive → positive: bright green
   - negative → positive: dark green
   - positive → negative: gray
8. A score of **0** is neither positive nor negative. If the before-score or the after-score is 0, the expectation is counted as neutral and is not placed in a quadrant. Expectations with no after-score are counted as not yet happened, also outside the matrix.
9. Delete an emotion entry or an expectation after confirmation.
10. Data stays on the device. No account, no network.
11. Release and debug builds sign with the shared sideload keystore when it is present. GitHub Pages publishes `/assumptions/` and `assumptions.apk`. `scripts/ci-apps.sh` lists `assumptions` in `ANDROID_APPS`. The root README links that page.

## Wheel

The reference wheel has eight primary families. Each family has three intensities: mild, medium, intense. Between neighboring families sit eight primary dyads (the emotions “at the intersection”).

Clockwise from the top, matching the reference drawing (joy at 12 o’clock):

| # | Family | Mild (starts visible) | Medium | Intense | Dyad clockwise of this family |
|---|--------|----------------------|--------|---------|-------------------------------|
| 0 | Joy | Спокойствие | Радость | Экстаз | Любовь (joy + trust) |
| 1 | Trust | Признание | Доверие | Восхищение | Подчинение (trust + fear) |
| 2 | Fear | Опасение | Страх | Ужас | Трепет (fear + surprise) |
| 3 | Surprise | Отвлечение | Удивление | Изумление | Неодобрение (surprise + sadness) |
| 4 | Sadness | Задумчивость | Печаль | Горе | Сожаление (sadness + disgust) |
| 5 | Disgust | Скука | Брезгливость | Отвращение | Презрение (disgust + anger) |
| 6 | Anger | Досада | Гнев | Ярость | Агрессия (anger + anticipation) |
| 7 | Anticipation | Интерес | Ожидание | Бдительность | Оптимизм (anticipation + joy) |

The classic wheel shows every intensity at once, mild on the outside and intense at the center. This wheel is inverted in disclosure:

1. The first screen is a full circle split into the **eight mild** sectors only: Интерес, Спокойствие, Досада, and the other mild names. Dyads and deeper intensities are hidden. A small paper hub sits at the center.
2. Pressing a sector opens **one** level inward on that sector (the medium emotion) and opens the **two intersection emotions** on its sides (the dyads with the previous and next family). Other sectors stay closed.
3. Moving the finger onto that newly opened inward level opens the **next** level (the intense emotion). A tap on the opened level does the same thing: the level under the finger reveals the next one. Nothing beyond the intense band exists.
4. Dyads have a single level. Touching a dyad selects it and does not open a further level.
5. The emotion under the finger is the current selection (mild, medium, intense, or dyad). Touching the center hub collapses the wheel back to the eight mild sectors and clears the selection.
6. Touching a different mild sector moves the focus there and opens only one level on the new sector.
7. Saving is explicit. The wheel is for choosing; **Записать** writes the selected emotion. Exploring does not create entries.

Geometry used by both drawing and hit testing:

- Closed: eight sectors, each 45°, inner radius 0.15, outer radius 1. Sector 0 is centered at the top.
- Open: the focused sector is 32° and stays centered on its axis. A 24° dyad sits on each side. The other seven sectors share the remaining arc equally.
- One level open: mild band radius 0.58–1, medium band 0.15–0.58. Dyads occupy radius 0.42–1.
- Two levels open: mild 0.74–1, medium 0.46–0.74, intense 0.15–0.46.
- At most one additional depth is revealed per pointer event, so a single tap cannot skip from mild straight to intense.

## Interfaces

There is no network API and no exported provider.

| Surface | Behavior |
|---------|----------|
| Launcher | `com.tepmex.assumptions`, label «Допущения» |
| Emotions tab | Wheel, selection, «Записать», reverse-chronological journal grouped as Сегодня / Вчера / date |
| Expectations tab | «Новое ожидание», list split into «Ещё впереди» and «Уже произошли», «Случилось» opens the after-score |
| Report tab | 2×2 matrix, legend, pending count, neutral count. Tapping a cell lists the expectations in that quadrant |
| Back | Closes the expectation editor, then clears a selected quadrant |
| Pages | `https://tepmex.github.io/CloudAgenticCoding/assumptions/` serves `site/index.html` and `assumptions.apk` |

Pleasure control: eleven equal segments labeled −5…+5, ends captioned «неприятно» and «приятно».

## Data model

Database file: `assumptions.db`.

### emotion_entries

| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER PK | autoincrement |
| emotionId | TEXT | stable id, `joy-0` … `anticipation-2`, or `dyad-love` and the other dyad ids |
| createdAtEpochMs | INTEGER | device clock at save |

### expectations

| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER PK | autoincrement |
| eventText | TEXT | trimmed, required |
| expectedOutcome | TEXT | trimmed, required |
| beforeScore | INTEGER | −5…+5 |
| afterScore | INTEGER NULL | null until the event has happened |
| afterNote | TEXT | optional comment, empty string if absent |
| createdAtEpochMs | INTEGER | |
| resolvedAtEpochMs | INTEGER NULL | set together with afterScore |

Emotion ids and Russian names live in the app catalog, not in a table.

### Quadrant rule

Let sign(score) be positive when score > 0, negative when score < 0, and undefined at 0.

| Before | After | Quadrant | Color |
|--------|-------|----------|-------|
| negative | negative | NegativeNegative | red `#E23B3B` |
| negative | positive | NegativePositive | dark green `#1B5E20` |
| positive | negative | PositiveNegative | gray `#9E9E9E` |
| positive | positive | PositivePositive | bright green `#2FBF4A` |
| either score is 0 | | neutral, not in the matrix | |
| afterScore is null | | pending, not in the matrix | |

## UI / UX

Three tabs, in order: **Эмоции**, **Ожидания**, **Отчёт**.

Visual direction: warm paper (`#F3EEE6`) and ink (`#1C1915`), serif titles, sans body. The wheel supplies the color. The chrome does not use the default Material purple.

The emotion under the finger is named in a bar under the wheel, because narrow dyad wedges cannot always fit a long Russian word. «Записать» sits in that bar and is available only when something is selected. After a successful save the wheel collapses and the new row appears at the top of the journal.

Expectation cards show the event, the expected outcome, the before-score, and the after-score once it exists. Negative scores are red, positive scores are green, zero is neutral ink.

The report matrix reads row-then-column: the top-left cell is negative→negative (red), the top-right is negative→positive (dark green), the bottom-left is positive→negative (gray), the bottom-right is positive→positive (bright green). A sentence above the matrix states that rows are the expectation beforehand and columns are the feeling afterward.

## Out of scope

- Secondary and tertiary dyads (families two or three apart, including opposites)
- More than three intensities, or intensity levels on dyads
- Several emotions in a single entry, notes on an emotion, or editing a saved emotion
- Editing the text or scores of an expectation in place (delete and create again)
- Reminders, notifications, widgets, accounts, backup, export
- English UI

## Acceptance criteria

1. With the wheel closed, hit testing at the eight sector centers returns the eight mild emotions, in the clockwise order in the table above, and returns no dyad and no medium or intense emotion.
2. Pressing the top sector (Спокойствие) reveals exactly one deeper band (Радость) plus Любовь and Оптимизм, and does not reveal Экстаз.
3. A further hit on that opened band reveals Экстаз. A second hit on the mild band does not.
4. A hit on Любовь selects it and leaves the opened depth unchanged.
5. A hit on the hub returns the wheel to the eight mild sectors and clears the selection.
6. Pressing a different sector moves focus and opens only one level there.
7. «Записать» persists the selected emotion id and the time, then clears the wheel. The journal lists it under the local day.
8. An expectation cannot be saved with a blank event, a blank outcome, or without a score. Scores outside −5…+5 are rejected.
9. Resolving an expectation stores the after-score. The card moves from «Ещё впереди» to «Уже произошли».
10. Report counts match the quadrant table, including the zero and pending cases. Cell colors are the four colors above.
11. `./gradlew :app:testDebugUnitTest` covers the wheel reducer, the layout, and the report counts. `./gradlew :app:assembleRelease` produces the sideload APK that Pages publishes.
12. `assumptions/site/index.html` links `assumptions.apk` and contains the `deploy-footer` marker. Changing `assumptions/**` selects this app in the deploy plan.

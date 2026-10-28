# 听力 · tingli-stories — SPEC

## Purpose

Android listening practice for a learner of Chinese. The user imports a zip of stories (Chinese text, questions, reference answers, and mp3 audio), listens, then types answers. An OpenAI-compatible model the user configures (bring your own key) grades the attempt. Finished stories stay finished until the user resets them. After a grade, further plays do not increase the listen count. A reset clears the answers, the grade, and the listen count, and the story is not completed. Statistics is a bar chart of answered stories: the height of a bar is the percent of correct answers divided by the listen count. A bar is green when that value is not below the current mean and gray when it is strictly below, in the same colors as the library circles. Stories without a grade are left out.

## Requirements

1. Project folder **tingli-stories**. Launcher name **听力**. Application id `com.tepmex.tinglistories`.
2. minSdk 34, compileSdk/targetSdk 36. Kotlin, Jetpack Compose, Material 3.
3. The user imports a zip (`ACTION_OPEN_DOCUMENT`, and `ACTION_VIEW` for zip MIME types). The archive is copied into app storage; the document URI is not kept.
4. The zip format is the one used by `hsk4_sample.zip` at the monorepo root:
   - A UTF-8 JSON document with `level`, `type`, and `exercises` (a bare array, or a `stories` array, is also accepted).
   - Each exercise has `id`, `title`, `text`, `questions` (`q` / `a`), `audio_story`, and `audio_questions`.
   - Audio entries may sit at the referenced path, at the file name alone, or under `audio/<file name>`. Matching is case-insensitive after the exact path misses. `__MACOSX` and `._*` entries are ignored.
   - `hsk4_sample.zip` references `audio/story_001.mp3` while the mp3 bytes live at `story_001.mp3`. That pair must resolve. Stories whose audio is absent stay in the library and open as text.
5. The release APK bundles `app/src/main/assets/hsk4_sample.zip`, the same bytes as `hsk4_sample.zip` at the monorepo root. **Пример HSK 4** imports that asset.
6. Replacing a pack keeps the listen count for the same story id. The saved answers and the grade are kept only when the story text and the questions (prompt and reference) are unchanged.
7. Listening screen: **Play · история** and **Play · вопросы**. Each enabled press counts as one listen for that story, including a press that restarts audio already playing, until the story has a stored grade. After that grade, presses of either Play control do not increase the count. A missing file does not show an enabled Play control. Stop and leaving the screen do not count. Text and question wording stay hidden while story audio exists. Without story audio, the text is shown and labeled as such. While a clip is playing, the screen shows the elapsed time, the duration, and a slider to move to any moment, plus controls that jump 10 seconds backward or forward. A seek does not add a listen.
8. **Ответить** opens one field per question. Until the story is graded, that field shows only the question number when question audio exists; the wording stays hidden. Without question audio the wording is shown, because there is nothing to listen to. The reference and the story text stay hidden either way. **Проверить** sends the story, the references, and the learner’s answers to the configured model. A successful grade marks the story completed and reveals the title, the question wording, the text, the references, and the comments. Beside that story text, **Прослушать ещё раз** is shown when story audio exists. It plays the story from the start and does not change the listen count. A failed request leaves the story incomplete and keeps the draft.
9. The library and the statistics list show a story title only after that story has a grade. Until then the row shows the catalog number. The listening and answer screens hide the title the same way. **Сбросить ответ** hides the title and, when question audio exists, the question wording again. Each library row ends in a circle and does not say «Пройдена» or «Не пройдена». After a grade the circle is filled, from the top, by score divided by the question count, and the listen count is the number inside it, with no word. A listen count of zero is left blank. Before a grade the circle is empty and shows no number. A graded story whose indicator `(score / max) × 100 / listenCount` is strictly below the mean of that indicator is drawn muted: the disk and the arc use the outline color, and the number uses the quieter text color. The spoken label adds «ниже среднего». The mean is the unweighted average over every graded story that has an indicator — the same set as **Статистика**. A story with no indicator, and a story at or above the mean, keep the ordinary circle.
10. **Настройки** stores the OpenAI-compatible base URL, model name, and access token. The token is optional and, when set, is sent only as `Authorization: Bearer`. It is not written into logs. The call is `POST {base}/v1/chat/completions`, or `{base}/chat/completions` when the base URL already ends at one of those suffixes. Cleartext HTTP is allowed for a local endpoint. Missing base URL or model does not call the network.
11. Completed stories stay completed across restarts. They can be played again, and those plays do not increase the listen count. **Сбросить ответ** clears the answers and the grade, sets the listen count to zero, and the story is not completed. Plays after a reset count again from zero.
12. After a grade, **Следующая история** opens the next story in catalog order. On the last story the control returns to the library.
13. **Статистика** shows only a bar chart. There is no story list, no totals, and no sliding mean. Each bar is a story that has an indicator, in catalog order. The height is `(score / max) × 100 / listenCount`: the percent of correct answers divided by how many times Play was pressed before the grade. Score is clamped to `0..max`. The indicator is absent when the story has no grade, the grade max is 0, or the listen count is 0, and that story has no bar. The mean is the unweighted average of the indicators on the chart. A bar is green when its indicator is greater than or equal to that mean, and gray when it is strictly below — the same split as a library circle. Green is the secondary color of a bright circle. Gray is the quieter text color of a circle below the mean. Axis labels are the catalog positions of the included stories.
14. Sign release and debug (when the keystore is present) with the shared sideload keystore. GitHub Pages landing at `/tingli-stories/` serves `tingli-stories.apk`.
15. **Экспорт JSON** on the library and on **Статистика** opens a save dialog (`ACTION_CREATE_DOCUMENT`, suggested name `tingli-stories.json`). The file is one JSON document, version 1, with every story: catalog number, id, title, text, listen count, and each question’s prompt, reference, and the learner’s answer. A graded story also stores score, max, summary, and per-question correct/comment. An unfinished story includes the current unsaved draft when that story is the open draft; otherwise it includes the saved answers. Titles, texts, and references are written even while the screens still hide them. `statistics` repeats the chart: `mean` is the unweighted average of the indicators, and `bars` lists catalog, id, indicator, and `green` for the same stories, in the same order, with the same green/gray split. Stories without an indicator stay in `stories` and are absent from `bars`. `mean` is null when there are no bars. The API token is not in the file.

## Interfaces

| Interface | Detail |
| --------- | ------ |
| Launcher | `com.tepmex.tinglistories.MainActivity` |
| Import | `ACTION_OPEN_DOCUMENT`; `ACTION_VIEW` for `application/zip` and `application/x-zip-compressed` |
| Sample | Asset `hsk4_sample.zip`, identical to the monorepo root archive |
| LLM | OpenAI-compatible chat completions. Grade JSON in the message content |
| Settings | DataStore `tingli_settings` |
| Library | `filesDir/tingli/library.json` and `filesDir/tingli/audio/<id>/story.<ext>` plus `questions.<ext>` |
| Permission | `INTERNET` |
| Gradle | `./gradlew test assembleRelease` |
| Pages | `https://<host>/<repo>/tingli-stories/tingli-stories.apk` |
| Export | `ACTION_CREATE_DOCUMENT` `application/json`, suggested name `tingli-stories.json` |

Grade JSON:

```json
{"score":3,"max":4,"summary":"Кратко по-русски.","items":[{"index":1,"correct":true,"comment":"Смысл совпал."}]}
```

`score` is how many answers match the reference in meaning. `max` is the number of questions. Markdown fences around the object are accepted.

## Data model

- **Story** — id, title, Chinese text, ordered questions, and whether each audio file was in the zip.
- **Question** — prompt (`q`) and reference answer (`a`). The reference is not shown before a grade. The prompt is not shown before a grade when the story has question audio.
- **Progress** — listen count, saved answers, and the grade. Completed means a grade is stored.
- **Listen** — one press of an enabled Play control before the story is graded. Story audio and question audio share one counter per story. Presses after a grade, including **Прослушать ещё раз**, do not count. A reset sets the counter to zero.
- **Content key** — story text plus each prompt and reference. A reimport with the same id and a different key keeps the listen count and drops the grade.

The API token lives in DataStore and is not copied into `library.json` or the export file.

Export JSON (pretty-printed, UTF-8):

```json
{
  "version": 1,
  "level": "HSK 4",
  "kind": "listening",
  "stories": [
    {
      "catalog": 1,
      "id": 1,
      "title": "换工作",
      "text": "…",
      "listenCount": 3,
      "completed": true,
      "score": 2,
      "max": 4,
      "summary": "Кратко по-русски.",
      "indicator": 16.666666666666668,
      "green": false,
      "questions": [
        {"index": 1, "question": "…", "reference": "…", "answer": "…", "correct": true, "comment": "Смысл совпал."}
      ]
    }
  ],
  "statistics": {
    "mean": 22.22222222222222,
    "bars": [{"catalog": 1, "id": 1, "indicator": 16.666666666666668, "green": false}]
  }
}
```

`score`, `max`, `summary`, `indicator`, `green`, `correct`, and `comment` are JSON `null` when that story or question has no grade. `indicator` is the same value as a chart bar: `(score / max) × 100 / listenCount`.

## UI / UX

Paper background, cinnabar controls, large Chinese titles once a story is graded. Before that, lists show the catalog number only. On the library, a circle replaces the listen phrase and the passed line: the disk fills with the share of correct answers, and the listen count sits in the circle after a grade. Zero stays blank, and a story with no grade is an empty circle. When a graded story’s indicator is strictly below the current mean of that indicator, the circle is muted and its spoken label says it is below the mean. Statistics is a bar chart of that indicator and nothing else on the screen. A bar at or above the mean is green, and a bar below the mean is gray, using the same colors as the library circles. Stories that are not completed are omitted. Once a story is graded, the answer screen places **Прослушать ещё раз** beside the story text. That replay does not count. Resetting an answer returns the listen count to zero. The listening screen shows a playback position while audio plays: current time, duration, a slider, and ±10 second jumps. Settings is three fields and a save action. Import of a second pack asks before replacing the current one. **Экспорт JSON** sits in the library toolbar and in the statistics toolbar. It asks where to save one file that holds the texts, the questions, the answers, and the chart.

## Out of scope

- More than one pack visible at a time
- Accounts, sync, or a backend proxy
- Playback speed or a background player
- Editing stories or generating audio
- Showing the transcript before the grade when story audio exists
- Play Store / App Bundle
- Encrypted token storage

## Acceptance criteria

1. `hsk4_sample.zip` parses as level `HSK 4`, 100 exercises, first title `换工作`, four questions, story audio resolved to `story_001.mp3`, and question audio resolved to `questions_001.mp3`. Exercise 2 has no audio bytes in the zip.
2. An `audio/...` reference resolves to a root entry of the same file name, and a bare file name resolves to `audio/<file name>` when that is the only match. `__MACOSX` JSON is ignored when a real catalog is present.
3. Importing the sample writes a non-empty `1/story.mp3` and `1/questions.mp3` and does not invent audio for story 2. A second import keeps a listen count and a grade. An import of the same id with different question text keeps the listen count and clears the grade.
4. `recordListen` adds one listen and does not complete the story. After a grade, `recordListen` does not add another listen. `resetAnswer` clears the grade and the answers and sets the count to 0. A later `recordListen` starts again at 1.
5. Listen labels: `1 прослушивание`, `2 прослушивания`, `5 прослушиваний`, `11 прослушиваний`, `21 прослушивание`.
6. A fenced grade payload `{score: 3, max: 99, summary, items}` becomes score 3, max equal to the question count, and the item comments. Prose without an object fails the parse.
7. `https://api.openai.com/v1` becomes `https://api.openai.com/v1/chat/completions`. A base that already ends in `/chat/completions` is left as that endpoint. Message `content` may be a string or an array of text parts.
8. Play is counted only when that side of the story has audio. The next id after the last story is absent. Stories without story audio are the ones allowed to show text before an answer. A story title is shown only when that story is completed. Question wording is shown before a grade only when the story has no question audio.
9. Playback time `0` is `0:00`, `42000` is `0:42`, and `125000` is `2:05`. A seek is clamped to `0..duration`. A non-positive duration does not clamp the upper bound.
10. `./gradlew test assembleRelease` succeeds and the APK verifies with `android/verify-apk-sideload-cert.sh`.
11. `scripts/ci-apps.sh` lists `tingli-stories` in `ANDROID_APPS`.
12. A library mark before a grade has no count and no fill, including when the listen count is already positive. A grade of 2 out of 4 with 31 listens is count 31 and fill one half. A graded story with zero listens has no count. A score of zero keeps a positive listen count and an empty fill.
13. For listen counts 3, 2, 10, 4 with grades 2/4, 4/4, none, and 0/4, the bars are catalog numbers 1, 2, and 4. Their indicators are 50/3, 50, and 0. The mean is 22,2. Bar 2 is green. Bars 1 and 4 are gray. The unanswered 10 listens are excluded. A grade with max 0 adds no bar. A graded story with zero listens adds no bar. A score above max counts as 100 percent, so 8 of 4 with 2 listens is 50. On that same library the muted circles are stories 1 and 4. Story 2 stays bright, and the unanswered story stays bright.
14. Two graded stories with the same indicator are both bright on the library and both green on the chart. A single scored story is bright and its bar is green. A perfect grade with zero listens is left out of the mean and stays bright. Indicators 100, 0, and 50 mute only the story at 0, because 50 equals the mean; on the chart only that story’s bar is gray.
15. For listen counts 3, 2, 10, 4 with grades 2/4, 4/4, none, and 0/4, the export lists all four texts. The unfinished story’s answer is the open draft. Its indicator and `green` are null, and it is absent from `statistics.bars`. The bars are catalogs 1, 2, and 4, with indicators 50/3, 50, and 0. The mean formats as 22,2. Bar 2 is green; bars 1 and 4 are not. A graded story keeps its saved answer when a draft for that same id is also present. An empty library exports no stories, a null mean, and no bars.

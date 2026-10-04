# 听力 · tingli-stories — SPEC

## Purpose

Android listening practice for a learner of Chinese. The user imports a zip of stories (Chinese text, questions, reference answers, and mp3 audio), listens, then types answers. An OpenAI-compatible model the user configures (bring your own key) grades the attempt. Finished stories stay finished until the user resets them. A statistics screen counts every press of Play.

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
7. Listening screen: **Play · история** and **Play · вопросы**. Each enabled press counts as one listen for that story, including a press that restarts audio already playing. A missing file does not show an enabled Play control. Stop and leaving the screen do not count. Text and question wording stay hidden while story audio exists. Without story audio, the text is shown and labeled as such.
8. **Ответить** opens one field per question and shows the question wording, not the reference and not the story text. **Проверить** sends the story, the references, and the learner’s answers to the configured model. A successful grade marks the story completed and reveals the text, the references, and the comments. A failed request leaves the story incomplete and keeps the draft.
9. **Настройки** stores the OpenAI-compatible base URL, model name, and access token. The token is optional and, when set, is sent only as `Authorization: Bearer`. It is not written into logs. The call is `POST {base}/v1/chat/completions`, or `{base}/chat/completions` when the base URL already ends at one of those suffixes. Cleartext HTTP is allowed for a local endpoint. Missing base URL or model does not call the network.
10. Completed stories stay completed across restarts. They can be played again. **Сбросить ответ** clears the answers and the grade and keeps the listen count.
11. After a grade, **Следующая история** opens the next story in catalog order. On the last story the control returns to the library.
12. **Статистика** lists every story in catalog order with its listen count, plus the total and how many are completed. A listen is one Play press, whether it started the story or the questions.
13. Sign release and debug (when the keystore is present) with the shared sideload keystore. GitHub Pages landing at `/tingli-stories/` serves `tingli-stories.apk`.

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

Grade JSON:

```json
{"score":3,"max":4,"summary":"Кратко по-русски.","items":[{"index":1,"correct":true,"comment":"Смысл совпал."}]}
```

`score` is how many answers match the reference in meaning. `max` is the number of questions. Markdown fences around the object are accepted.

## Data model

- **Story** — id, title, Chinese text, ordered questions, and whether each audio file was in the zip.
- **Question** — prompt (`q`) and reference answer (`a`). The reference is not shown before a grade.
- **Progress** — listen count, saved answers, and the grade. Completed means a grade is stored.
- **Listen** — one press of an enabled Play control. Story audio and question audio share one counter per story.
- **Content key** — story text plus each prompt and reference. A reimport with the same id and a different key keeps the listen count and drops the grade.

The API token lives in DataStore and is not copied into `library.json`.

## UI / UX

Paper background, cinnabar controls, large Chinese titles. The library shows completion and the listen count. Statistics repeats the counts with the rule stated on screen: each Play press is one listen. Settings is three fields and a save action. Import of a second pack asks before replacing the current one.

## Out of scope

- More than one pack visible at a time
- Accounts, sync, or a backend proxy
- Seek, speed, or a background player
- Editing stories or generating audio
- Showing the transcript before the grade when story audio exists
- Play Store / App Bundle
- Encrypted token storage

## Acceptance criteria

1. `hsk4_sample.zip` parses as level `HSK 4`, 100 exercises, first title `换工作`, four questions, story audio resolved to `story_001.mp3`, and question audio resolved to `questions_001.mp3`. Exercise 2 has no audio bytes in the zip.
2. An `audio/...` reference resolves to a root entry of the same file name, and a bare file name resolves to `audio/<file name>` when that is the only match. `__MACOSX` JSON is ignored when a real catalog is present.
3. Importing the sample writes a non-empty `1/story.mp3` and `1/questions.mp3` and does not invent audio for story 2. A second import keeps a listen count and a grade. An import of the same id with different question text keeps the listen count and clears the grade.
4. `recordListen` adds one listen and does not complete the story. `resetAnswer` clears the grade and the answers and keeps the count.
5. Listen labels: `1 прослушивание`, `2 прослушивания`, `5 прослушиваний`, `11 прослушиваний`, `21 прослушивание`.
6. A fenced grade payload `{score: 3, max: 99, summary, items}` becomes score 3, max equal to the question count, and the item comments. Prose without an object fails the parse.
7. `https://api.openai.com/v1` becomes `https://api.openai.com/v1/chat/completions`. A base that already ends in `/chat/completions` is left as that endpoint. Message `content` may be a string or an array of text parts.
8. Play is counted only when that side of the story has audio. The next id after the last story is absent. Stories without story audio are the ones allowed to show text before an answer.
9. `./gradlew test assembleRelease` succeeds and the APK verifies with `android/verify-apk-sideload-cert.sh`.
10. `scripts/ci-apps.sh` lists `tingli-stories` in `ANDROID_APPS`.

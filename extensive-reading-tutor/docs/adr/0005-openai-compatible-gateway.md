# One OpenAI-compatible adapter sits behind the lesson gateway

The lesson service calls `LessonAi.prepareLesson` and `LessonAi.evaluateAnswers`. Those methods return typed drafts. Only `OpenAiCompatibleProvider` speaks HTTP, and it does so through `POST .../chat/completions`.

That one protocol already covers OpenAI, OpenRouter, and most local servers the learner can point a key at. Provider-specific SDKs would leak into the lesson code. Capabilities (JSON mode, usage, system role) live on the provider config so a later adapter can decline a feature without a new lesson API. Keys stay in the keystore store and are not columns in the lesson history.

Provider-specific body fields (reasoning effort, thinking, and the like) are not a second adapter. The learner writes them as one JSON object, and the gateway merges that object into the Chat Completions body. `model` and `messages` stay owned by the app. Each HTTP attempt is appended to a redacted exchange file so the settings screen can show the call.

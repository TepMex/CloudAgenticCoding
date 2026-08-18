# Goal prompt для Codex — DuoShaoGame

Ты работаешь над новым проектом **DuoShaoGame**.

Нужно не просто создать scaffold, а довести приложение до работающего MVP, который можно запустить локально, открыть в мобильном браузере и реально сыграть голосом.

## Product goal

DuoShaoGame — небольшая browser game для тренировки быстрого произнесения чисел по-китайски.

Основной сценарий — подготовка к поездкам по Китаю: цены, деньги, покупки и необходимость быстро воспринимать арабское число как китайскую числовую фразу.

Игрок видит падающие сверху вниз денежные пиктограммы с суммами, например:

`¥300`

Чтобы уничтожить объект, игрок должен произнести в микрофон соответствующую сумму по-китайски:

`三百元`

Если распознанная речь соответствует сумме объекта, объект исчезает.

Если хотя бы один объект достигает нижней границы игрового поля и игрок не успел правильно назвать его до этого момента — игра заканчивается.

Игра должна быть mobile-first и нормально работать в portrait orientation смартфона.

---

## Technology

Используй:

* Bun
* TypeScript
* React
* Vite или другой минимальный React build setup, хорошо работающий с Bun
* обычный CSS / CSS Modules; не подключай тяжёлый UI framework без необходимости
* Web Audio API для microphone capture
* Web Worker для тяжёлой STT inference, чтобы recognition не блокировал animation/UI

Не нужен backend.

Приложение должно быть пригодно для статического хостинга.

---

## Speech recognition architecture

Главный production STT target для MVP:

**Paraformer INT8 через sherpa-onnx WebAssembly.**

При этом STT должен быть полностью изолирован от игровой логики интерфейсом примерно такого уровня:

```ts
interface SpeechRecognizer {
  initialize(): Promise<void>;
  start(): Promise<void>;
  stop(): Promise<void>;
  dispose(): Promise<void>;

  onSpeechStart?: (event: SpeechTimingEvent) => void;
  onSpeechEnd?: (event: SpeechTimingEvent) => void;
  onResult?: (result: SpeechRecognitionResult) => void;
  onError?: (error: Error) => void;
}
```

Не привязывай React components напрямую к sherpa-onnx.

Должно быть возможно позднее добавить:

```ts
ParaformerSpeechRecognizer
SenseVoiceSpeechRecognizer
MockSpeechRecognizer
```

без изменений game engine.

### Model delivery

Не коммить большие ASR weights в основной git repository.

Сделай model URL/config отдельной конфигурацией.

При первом запуске:

1. показать экран загрузки speech model;
2. показать progress;
3. загрузить необходимые assets;
4. по возможности кешировать их browser-side;
5. только после готовности модели разрешить начать игру.

Повторный запуск должен использовать browser cache, если assets уже доступны.

Если полноценная production-интеграция sherpa-onnx требует дополнительного build step, добавь его как понятный script и подробно зафиксируй в README.

---

## Microphone UX

После нажатия Start запросить microphone permission.

Во время игры microphone stream открыт, но звук записывается только по явному push-to-talk.

Игрок удерживает кнопку во время каждого ответа. Не используй VAD, пороги громкости, интервалы тишины или автоматическое ограничение длительности utterance.

```text
player presses and holds the talk button
→ collect utterance while the button is held
→ player releases the button
→ send utterance to ASR
→ get transcript
→ try to hit target
```

Ожидаемые ответы короткие, обычно около 0.5–2 секунд.

Не записывай и не отправляй аудио на backend.

На экране должен быть небольшой ненавязчивый microphone status:

* loading
* ready / hold to talk
* recording
* recognizing
* error

Последний распознанный transcript кратко показывай в HUD. Это важно для debugging и для игрока: он должен понимать, что услышела модель.

---

## Critical latency rule

Нельзя проигрывать только потому, что ASR долго вычислял ответ.

У каждого utterance должны быть timestamps:

```ts
speechStartedAt
speechEndedAt
recognitionCompletedAt
```

Если объект пересёк нижнюю границу после того, как игрок уже начал произносить потенциальный ответ, game over для этого объекта нужно временно отложить, пока соответствующий recognition request не завершится.

Пример:

```text
10.000 player starts saying 三百元
10.500 speech ends
10.700 target crosses bottom
11.100 ASR returns 三百元
```

Это должно считаться успешным попаданием, а не проигрышем.

Добавь разумный timeout, чтобы зависший recognition request не мог навсегда остановить game over.

Game engine должен измерять реакцию игрока, а не производительность телефона.

---

## Recognition matching

НЕ используй:

```ts
transcript === expectedText
```

Вместо этого создай отдельный слой:

```text
raw ASR transcript
↓
normalize
↓
parse spoken Chinese money expression
↓
ParsedAnswer
↓
compare numeric amount with active targets
```

Например:

```ts
interface ParsedAnswer {
  amount: number;
  raw: string;
  normalized: string;
}
```

### Normalization

Учитывай как минимум:

* whitespace
* punctuation
* `元`
* `块`
* `块钱`
* Arabic digits, если ASR сделал inverse text normalization
* `两` как допустимый вариант `二` перед 百/千

Примеры допустимых эквивалентов:

```text
300
三百
三百元
三百块
三百块钱
```

→ `amount = 300`

```text
200
二百元
两百元
二百块
两百块
```

→ `amount = 200`

При этом не делай matcher чрезмерно permissive.

Фраза вроде:

```text
我觉得这个应该是三百元
```

не должна автоматически считаться чистым ответом.

Parser должен распознавать именно короткую Chinese numeral / money expression.

---

## Chinese number domain

Для MVP поддержи целые значения:

```text
1..9999
```

Decimal prices пока не нужны.

Canonical form используй с `元`.

Примеры canonical answers:

```text
1       一元
9       九元
10      十元
11      十一元
20      二十元
25      二十五元
100     一百元
101     一百零一元
105     一百零五元
110     一百一十元
200     二百元
250     二百五十元
999     九百九十九元
1000    一千元
1001    一千零一元
1010    一千零一十元
1100    一千一百元
2010    二千零一十元
9999    九千九百九十九元
```

Canonical generator и spoken-number parser должны быть отдельными pure functions с хорошими unit tests.

Допускай `两百` / `两千` как альтернативное правильное произношение соответствующих значений.

Не поддерживай пока разговорные сокращения, в которых пропущенный разряд приходится угадывать из контекста.

---

## Gameplay

Основной экран — одно игровое поле на весь viewport.

Сверху появляются денежные пиктограммы.

Для MVP не имитируй настоящие китайские банкноты подробно. Используй стилизованную generic money/banknote card:

```text
┌─────────────┐
│     ¥       │
│    300      │
└─────────────┘
```

Главное — крупное и мгновенно читаемое Arabic number.

Объекты плавно движутся сверху вниз.

Используй `requestAnimationFrame` или другую time-based animation, не зависящую от frame rate.

У каждого объекта должны быть примерно такие данные:

```ts
interface FallingTarget {
  id: string;
  amount: number;
  spawnedAt: number;
  fallDurationMs: number;
  state: "falling" | "hit" | "pending-game-over";
}
```

Position рассчитывай от времени, а не простым `y += N` на каждый frame.

---

## Target matching

Когда ASR вернул ParsedAnswer:

1. найти активный falling target с `target.amount === answer.amount`;
2. если найден один — уничтожить его;
3. начислить score;
4. показать короткую hit animation;
5. продолжить игру.

Не создавай одновременно два active targets с одинаковым `amount`.

Если распознанная сумма отсутствует среди targets:

* ничего не уничтожать;
* кратко показать transcript как miss;
* не ставить игру на pause;
* не отнимать отдельную жизнь.

Главное наказание за ошибку — потерянное время.

---

## Loss condition

MVP использует sudden death:

**один target достиг нижней границы → game over.**

Не добавляй три жизни.

Исключение — описанное выше правило pending speech recognition: если потенциальный utterance был начат своевременно, сначала дождись его результата.

После game over:

* остановить spawn;
* остановить движение;
* остановить microphone recognition;
* показать score;
* показать количество successful hits;
* показать пропущенную сумму;
* показать canonical Chinese answer для неё;
* кнопка Play again.

---

## Score

Для MVP достаточно простой модели:

```text
+100 за правильный target
+ небольшой reaction bonus за быстрый ответ
```

Показывай:

```text
Score
Hits
Current level
```

Можно добавить combo, но только если это не усложняет core implementation.

Не добавляй currency, shop, achievements, account system и backend.

---

## Difficulty progression

Difficulty должна учить числа постепенно, а не просто ускорять падение.

Используй stages примерно такой структуры:

### Stage 1

Числа:

```text
1..10
```

Очень медленное падение.

Один target одновременно большую часть времени.

### Stage 2

```text
11..99
```

### Stage 3

Круглые сотни и простые сотни:

```text
100
200
300
...
900
120
350
```

### Stage 4

Произвольные:

```text
100..999
```

включая формы с `零`:

```text
101
205
309
```

### Stage 5

Простые тысячи.

### Stage 6

```text
1000..9999
```

Не переходи на новый stage слишком быстро.

Ориентир: около 8–12 successful hits на stage.

Одновременно со stage слегка:

* увеличивай falling speed;
* уменьшай spawn interval;
* разрешай больше simultaneous targets.

Но основной рост сложности должен идти через числа.

Начальная скорость должна быть очень спокойной: абсолютный новичок должен успевать увидеть `7`, вспомнить `七元`, произнести и дождаться ASR.

---

## Spawn rules

Создай отдельный `DifficultyManager`.

Он определяет:

```ts
numberGenerator
fallDuration
spawnInterval
maxConcurrentTargets
```

Не размазывай difficulty constants по React components.

Generator должен избегать:

* одинаковых active amounts;
* слишком частого повторения последнего числа;
* резких скачков сложности.

Randomness можно injectable/seedeable сделать там, где это упрощает unit tests.

---

## Visual design

Стиль:

* clean mobile arcade
* минималистично
* крупная typography
* хороший contrast
* без визуального шума
* интерфейс должен быть понятен без tutorial wall of text

Portrait smartphone — главный target.

Проверь минимум примерно:

```text
360 × 640
390 × 844
430 × 932
desktop browser
```

Game board должен использовать доступную высоту корректно при browser chrome/mobile viewport resizing.

Используй `100dvh`, если это уместно.

Не делай основной игровой интерфейс через `<canvas>`, если DOM/CSS достаточно: React DOM упростит responsive UI и accessibility.

---

## Screens

Нужны только:

### Start / model-loading screen

Название:

**DuoShaoGame**

Можно использовать китайское:

**多少**

Короткий текст, объясняющий механику.

Status загрузки ASR.

Start button.

### Game screen

* score
* level
* microphone indicator
* falling objects
* краткий last transcript

### Game over overlay

* score
* hits
* missed amount
* correct Chinese answer
* Play again

---

## Developer mode

Это обязательная часть MVP.

Реальное распознавание речи не должно быть необходимо для разработки game logic.

Добавь mock mode, например:

```text
?stt=mock
```

или dev configuration.

В mock mode:

* microphone не требуется;
* есть небольшой debug input;
* developer вводит transcript вроде `三百元`;
* Enter отправляет его в тот же pipeline, что production ASR.

Важно:

```text
Mock transcript
→ same normalizer
→ same parser
→ same game matching
```

Не создавай отдельную cheat-логику.

Добавь также возможность искусственной задержки mock recognition, например:

```text
?stt=mock&latency=800
```

чтобы проверить pending-ASR/game-over fairness.

---

## Suggested architecture

Структура может выглядеть примерно так:

```text
src/
  app/
    App.tsx

  game/
    engine.ts
    types.ts
    difficulty.ts
    spawn.ts

  chinese/
    chineseNumber.ts
    parseChineseMoney.ts
    normalizeTranscript.ts

  speech/
    types.ts
    createSpeechRecognizer.ts
    ParaformerSpeechRecognizer.ts
    MockSpeechRecognizer.ts
    worker/
      speech.worker.ts

  audio/
    microphone.ts

  components/
    StartScreen.tsx
    GameScreen.tsx
    FallingMoney.tsx
    Hud.tsx
    MicIndicator.tsx
    TranscriptToast.tsx
    GameOver.tsx

  styles/
```

Это не жёсткое требование к именам файлов, но сохраняй разделение:

```text
game engine
≠
Chinese-number logic
≠
speech recognition
≠
React UI
```

---

## Tests

Используй подходящий lightweight test runner, совместимый с Bun.

Особенно хорошо протестируй pure logic.

Минимальный набор unit cases для number generation/parser:

```text
1
9
10
11
20
25
100
101
105
110
200
250
999
1000
1001
1010
1100
2000
2010
9999
```

Для альтернатив:

```text
二百元    → 200
两百元    → 200
两千块    → 2000
三百块钱  → 300
300元     → 300
```

Проверь rejects:

```text
random unrelated Chinese text
empty string
multiple numbers in one phrase
unsupported decimals
```

Game engine tests должны покрывать:

* spawning;
* no duplicate active amounts;
* successful hit;
* miss;
* score;
* progression;
* crossing bottom;
* game over;
* recognition started before boundary;
* recognition finished after boundary but correct;
* recognition finished after boundary and incorrect;
* recognition timeout.

---

## Performance

Игра предназначена прежде всего для smartphone.

Поэтому:

* не вызывай expensive React rerender для каждого audio frame;
* STT inference вынеси из main UI thread;
* не создавай большие transient audio allocations без необходимости;
* освобождай microphone/audio resources после game over;
* корректно dispose WebAssembly/worker resources;
* animation должна оставаться плавной во время recognition.

Добавь development instrumentation для:

```text
utterance duration
ASR inference duration
end-of-speech → result latency
```

Не нужно показывать эти значения обычному игроку.

---

## Error handling

Обработай нормально:

* microphone permission denied;
* microphone unavailable;
* model download failed;
* model initialization failed;
* WASM unsupported / initialization error;
* speech worker crashed.

Игрок должен видеть понятное сообщение и возможность retry.

Приложение не должно падать в blank screen.

---

## README

README должен объяснять:

```text
что такое DuoShaoGame
как установить зависимости
как запустить через Bun
как запустить tests
как включить mock STT
как настроить model URL
как подготовить/получить Paraformer assets
как устроен speech pipeline
```

Если для sherpa-onnx/WASM нужны дополнительные build/generated artifacts, документируй их воспроизводимое получение.

---

## Implementation strategy

Работай вертикальными срезами.

Сначала добейся полностью играбельной версии с MockSpeechRecognizer:

```text
start
→ targets fall
→ enter Chinese answer
→ target dies
→ difficulty progresses
→ game over works
→ restart works
```

После этого подключай microphone/push-to-talk/Paraformer к уже существующему `SpeechRecognizer`.

Не пытайся одновременно писать game engine и debugging низкоуровневого WASM.

---

## Out of scope

Не реализовывать сейчас:

* login/accounts
* backend/database
* leaderboards
* multiplayer
* real Chinese banknote artwork
* decimal prices
* 元/角/分 arithmetic
* measure words besides money
* listening comprehension
* TTS
* shops or NPCs
* achievements
* localization system
* analytics
* cloud STT

Архитектура может позволять дальнейшее расширение, но не проектируй сложную abstraction hierarchy ради гипотетического будущего.

---

## Definition of done

MVP считается готовым, когда:

1. `bun install` и documented command запускают приложение.
2. Проект проходит typecheck и tests.
3. В mock mode можно провести полноценную игровую сессию.
4. Targets корректно падают независимо от FPS.
5. Chinese number parser корректно работает для `1..9999`.
6. Speech result может уничтожить любой matching active target.
7. Ошибочный speech result не уничтожает target.
8. Difficulty постепенно усложняет числа и ускоряет игру.
9. Пересечение нижней границы вызывает game over.
10. Pending recognition не создаёт несправедливый проигрыш из-за latency.
11. Play again полностью сбрасывает состояние.
12. Production speech adapter работает через локальный Paraformer/sherpa-onnx WebAssembly либо, если конкретное окружение Codex не позволяет реально загрузить модель, вся production integration реализована до границы model assets и снабжена точными воспроизводимыми инструкциями, а MockSpeechRecognizer остаётся полностью рабочим.
13. Интерфейс пригоден для мобильного portrait browser.
14. Нет обязательного backend/cloud dependency.

Не останавливайся после scaffold или TODO-комментариев. Самостоятельно запускай приложение, typecheck и tests, исправляй найденные ошибки и оставь repository в рабочем состоянии.

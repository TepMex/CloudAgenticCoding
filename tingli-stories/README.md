# 听力 · tingli-stories

Android-тренажёр аудирования. В zip лежат истории на китайском, вопросы и mp3. После прослушивания ответы проверяет OpenAI-совместимая модель, адрес и ключ которой пользователь указывает сам.

Формат архива — `hsk4_sample.zip` в корне монорепы. Кнопка **Пример HSK 4** импортирует этот файл из APK. В примере озвучены история 1 и вопросы к ней; остальные 99 историй открываются текстом.

Название истории и формулировки вопросов скрыты, пока ответ не проверен. Во время воспроизведения видны текущее время и длительность, ползунок перематывает к нужному месту, кнопки сдвигают запись на 10 секунд.

## Сборка

Нужен Android SDK 36. Пример в APK — копия `hsk4_sample.zip` из корня монорепы (`app/src/main/assets/hsk4_sample.zip`).

```bash
cd tingli-stories
cp local.properties.example local.properties   # sdk.dir
./gradlew test assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

Подпись — общий sideload keystore репозитория. Проверка: `android/verify-apk-sideload-cert.sh`.

## Требования

Android 14 (API 34) и новее. Сеть нужна только для запроса к LLM. HTTP разрешён для локального endpoint.

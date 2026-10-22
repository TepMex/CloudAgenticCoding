# Extensive Reading Tutor

Локальный Android-наставник для чтения настоящей книги на изучаемом языке. Приложение хранит позицию, занятие, ответы и наблюдения. Модель только предлагает введение, вопросы и разбор. Первый практический контур — китайский текст и русские объяснения, ключ пользователя к OpenAI-совместимому API.

Экран — телефонный интерфейс: полка, книга, занятие одной лентой (фрагмент остаётся сверху, введение, вопросы и разбор дописываются ниже), знания и настройки OpenAI-совместимой модели. Состояние занятия по-прежнему ведёт приложение.

## Сборка

Нужен Android SDK 36.

```bash
cd extensive-reading-tutor
cp local.properties.example local.properties   # sdk.dir
./gradlew test assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

Подпись — общий sideload keystore репозитория. Проверка: `android/verify-apk-sideload-cert.sh`.

## Контракты

Учебный цикл и инварианты описаны в `SPEC.md`, словарь в `CONTEXT.md`, решения в `docs/adr/`, схема компонентов в `docs/ARCHITECTURE.md`.

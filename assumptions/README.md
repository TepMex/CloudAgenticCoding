# Допущения (assumptions)

Personal Android journal for emotions, expectations, and apprehensions.

An apprehension is an expectation with a negative pleasure score. Emotions are picked on an inverted Plutchik wheel: the circle starts as the eight mild sectors (Спокойствие, Интерес, Досада, …). Pressing a sector opens one intensity inward and the two intersection emotions beside it. Moving onto that opened level reveals the next. Expectations are rated from −5 to +5 before the event and again after it. The report is a 2×2 matrix of those two signs.

See [SPEC.md](SPEC.md).

## Build

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Package: `com.tepmex.assumptions`. Min SDK 34.

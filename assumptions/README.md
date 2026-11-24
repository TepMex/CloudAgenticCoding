# Допущения (assumptions)

Personal Android journal for emotions, expectations, and apprehensions.

An apprehension is an expectation with a negative pleasure score. Emotions are picked on an inverted Plutchik wheel: the circle starts as the eight mild sectors (Спокойствие, Интерес, Досада, …). Pressing a sector opens one intensity inward and the two intersection emotions beside it. Moving onto that opened level reveals the next. Expectations are rated from −5 to +5 before the event and again after it. The report is a 2×2 matrix of those two signs.

See [SPEC.md](SPEC.md).

## Build

```bash
cd assumptions
./gradlew :app:testDebugUnitTest assembleRelease
```

Package: `com.tepmex.assumptions`. Min SDK 34.

The release APK is signed with the shared sideload keystore when `sideload.keystore` is present. Install the new APK over the previous build. Uninstall once only if an older build was signed with a different key.

On push to `master`, `.github/workflows/deploy.yml` builds the release APK when `assumptions/**` changes, checks the sideload certificate, and publishes the landing page and `assumptions.apk` at `/assumptions/`.

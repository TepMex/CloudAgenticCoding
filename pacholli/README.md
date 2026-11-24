# Pacholli

Personal double-entry ledger for Android. Chart of accounts with active, passive, and mixed accounts, commodities priced in one operating currency, and Beancount import/export.

```bash
cd pacholli
./gradlew :app:testDebugUnitTest assembleRelease
```

The release APK is signed with the shared sideload keystore when `sideload.keystore` is present. Install over the previous build; uninstall once only if an older build was signed with a different key.

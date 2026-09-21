# duoshao-qian (多少钱) — SPEC

## Purpose

Offline Android listening game for a Russian-speaking traveler learning to **hear Chinese prices**. The player sees a tourist snack or drink, taps **多少钱?**, hears a realistic yuan price in Chinese, then tenders that amount with **stylized circulating RMB banknotes**.

Audience: someone preparing for shops and street stalls in China who already knows some hanzi and needs listening practice for money.

## Requirements

1. Display name **多少钱**; project / Pages path **duoshao-qian**; application id `com.tepmex.duoshaoqian`.
2. Game loop:
   1. Show one product a tourist might buy in China (drink, fruit, or food) with a generated photo, Chinese name, pinyin, and Russian gloss. Do **not** show the numeric price before payment.
   2. Tap **多少钱?** to play bundled Chinese audio of that product’s price.
   3. Assemble the heard amount from all currently circulating RMB banknotes and tap **付款**.
3. Prices must be plausible for the product (a water bottle is a few yuan, not ¥100).
4. Banknote picker includes every current paper denomination: **¥1, ¥5, ¥10, ¥20, ¥50, ¥100**. Designs are stylized but color/layout-similar to the fifth-series notes. Tapping a note adds it to the tender; tapping a tendered note removes it. Show the running total in yuan.
5. **付款** succeeds only on an exact match with the spoken price. Wrong amounts stay on the same product so the player can listen again. After a correct payment, score updates and a new product appears.
6. Bundle spoken numbers from `chinese_money_numbers.json` (base64 audio fields). The app plays those clips; it does not call a network TTS API at runtime. Only prices that have an audio entry may be asked.
7. minSdk 34, compile/targetSdk 36; Kotlin + Jetpack Compose + Material 3. Offline after install.
8. Sign release (and debug when the keystore is present) with the shared committed sideload keystore. Publish a GitHub Pages landing at `/duoshao-qian/` with `duoshao-qian.apk`.

## Interfaces

| Interface | Detail |
| --------- | ------ |
| Launcher activity | `com.tepmex.duoshaoqian.MainActivity` |
| Audio catalog | `file:///android_asset/chinese_money_numbers.json` (UTF-8 JSON; number keys or list entries with base64 audio) |
| Product catalog | in-code catalog + `assets/products/*.png` |
| Audio playback | `MediaPlayer` from a decoded temp file / in-memory source |
| Gradle | `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk` |
| Pages download | `https://<host>/<repo>/duoshao-qian/duoshao-qian.apk` |

No accounts, no network, no custom URL scheme in v1.

## Data model

- **MoneyClip** — `amountFen` (integer fen, 100 fen = 1 yuan) + optional hanzi/pinyin + audio bytes.
- **AudioCatalog** — map of amount → clip parsed from `chinese_money_numbers.json`.
- **Product** — id, hanzi, pinyin, russian, image asset, `priceYuanOptions` (whole yuan amounts that exist in the audio catalog and fit the product).
- **Banknote** — denomination yuan (1, 5, 10, 20, 50, 100), palette, labels.
- **Round** — product + secret `priceYuan` chosen from that product’s options.
- **Tender** — multiset of banknotes; `sumYuan` is their total.
- **Session** — correct count, current streak, current round.

Persistence: none in v1. Uninstall loses score; that is acceptable.

## UI / UX

1. Cold start → shop screen: title **多少钱**, product photo, names, **多少钱?** (replayable), banknote wallet, tender tray, total, **清空**, **付款**.
2. Listening: tapping **多少钱?** plays the clip; no Arabic price is revealed.
3. Paying: tap wallet notes to add; tap notes in the tray to remove; **付款** checks the sum.
4. Feedback: short Chinese + Russian snackbar (`对了!` / `不对，再听一次`). After success, the next goods appear.
5. Landing page: brand 多少钱 / duoshao-qian, APK download, update note.

## Out of scope

- Coins (jiao) and change-making / 找零
- Speech recognition (that is the separate `duoshao` web game)
- Accounts, leaderboards, SRS
- Play Store / App Bundle
- Live TTS or downloaded voices

## Acceptance criteria

1. A round shows a product image and never prints the secret price before a correct **付款**.
2. **多少钱?** plays Chinese audio from the bundled JSON for that price.
3. Water-class drinks are priced in a low single-digit yuan band; meals can be tens of yuan, never ¥100 for a bottle of water.
4. Wallet shows ¥1, ¥5, ¥10, ¥20, ¥50, and ¥100 notes. Exact tender pays; a wrong sum keeps the same product.
5. `./gradlew test assembleRelease` succeeds and the APK verifies with `android/verify-apk-sideload-cert.sh`.
6. Deploy workflow includes `duoshao-qian` in `ANDROID_APPS` and rebuilds when `duoshao-qian/**` changes.

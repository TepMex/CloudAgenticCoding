# Pacholli — Specification

## Purpose

**Pacholli** is an offline Android ledger for personal finances kept in **double-entry** form. The user builds their own chart of accounts, records each movement as a debit and a credit, and reads the three standard statements: balance sheet, cash flow, and financial result.

The UI follows the attached reference (dark chrome, day journal, teal add button, debit/credit rows, calculator keypad, dark navigation drawer). The books themselves are Beancount: every entry can be viewed and edited as Beancount text, and the whole book imports and exports as a `.beancount` file. Language of the syntax: [Beancount](https://github.com/beancount/beancount).

| Field | Value |
| --- | --- |
| App name | Pacholli |
| Package | `com.tepmex.pacholli` |
| Min SDK | 34 |
| Target / compile SDK | 35 |
| UI | Jetpack Compose, Russian |

## Requirements

1. On first launch, create a book titled **Личные финансы** with operating commodity **RUB** and a small personal chart (cash, card, property, credit card, opening equity, salary, other income, groceries, transport, housing, other expenses).
2. The user can create, rename, and delete accounts. Each account has:
   - display name
   - Beancount name (`Assets:Cash`, at least root plus one component)
   - **тип**: активный, пассивный, or активно-пассивный
   - **раздел**: активы, обязательства, капитал, доходы, or расходы (this selects the Beancount root and which statement the account belongs to)
   - whether **субсчета** are allowed
   - whether it is a **денежный** счёт (included in the cash-flow statement)
   - open date and optional close date
3. An account with children cannot be used in a new entry. Only accounts without subaccounts can be debited or credited. If subaccounts are disallowed, a child cannot be created.
4. A child uses the same раздел as its parent. Moving or renaming an account rewrites the Beancount names of its descendants.
5. An entry is always two postings: one **дебет** and one **кредит** of equal value in the operating currency. The same account cannot stand on both sides.
6. Commodities (валюты, тикеры, квартира и любое другое имя) can be created with a code and a price history in the **single** operating currency. The operating commodity’s price is always 1 and cannot be deleted. A posting in another commodity stores quantity and the booked value in the operating currency (quantity × price used for that entry).
7. The entry screen edits debit, credit, amount, commodity, price, date, time, payee, and narration. A calculator keypad (digits, `.`, `+ − × /`, AC, DEL, OK) edits the focused amount. `×` and `/` bind tighter than `+` and `−`.
8. The same entry can be shown as Beancount text and saved from that text. Invalid Beancount, an unbalanced entry, or any shape other than exactly two postings cannot be saved.
9. Journal is grouped by day inside the chosen span: день, неделя (Monday–Sunday), месяц, год, всё. The drawer opens search, chart of accounts, commodities, the three reports, settings (book title), and import/export.
10. Reports:
    - **Баланс** on a date. Open quantities of balance-sheet accounts are valued at the latest commodity price on or before that date (cost, if no price). Realized price differences are part of equity via the financial result. Unrealized differences are the line **Переоценка**. The two sides are equal.
    - **Движение денежных средств** for a period, direct method, in the operating currency at booked amounts. Cash accounts only. Counterpart раздел: доходы/расходы → операционная, прочие активы → инвестиционная, обязательства/капитал → финансовая. Cash-to-cash is a transfer. Opening plus flows equals closing.
    - **Финансовый результат** for a period: доходы minus расходы, plus realized price and currency differences.
11. Import reads a `.beancount` file and, after confirmation, replaces the book. Export writes a `.beancount` file the importer accepts again. Unsupported directives that would change balances (`balance`, `pad`, `include`, `plugin`, `note`, `document`, `event`, `query`, `custom`) are errors, so a file is not applied partially.
12. Sign release and debug with the shared sideload keystore when it is present. GitHub Pages publishes `/pacholli/` and `pacholli.apk`. `scripts/ci-apps.sh` lists `pacholli` in `ANDROID_APPS`.

## Interfaces

| Interface | Detail |
| --- | --- |
| Launcher | `com.tepmex.pacholli.MainActivity` |
| Storage | Room database `pacholli.db` on the device |
| Export | `CreateDocument("text/plain")`, suggested name `pacholli.beancount` |
| Import | `OpenDocument`, UTF-8 text |
| Gradle | `./gradlew :app:testDebugUnitTest assembleRelease` |
| Pages | `https://<host>/<repo>/pacholli/` |

Beancount subset written and read:

- `option "title"`, `option "operating_currency"`
- `open`, `close`, `commodity`, `price`
- a transaction flagged `*`, `!`, or `txn`, with payee and narration
- postings `ACCOUNT  AMOUNT CCY`, `{price CCY}`, `@ price CCY`, `@@ total CCY`, and one omitted amount
- metadata `name`, `type` (`active` / `passive` / `active-passive`), `allows-subaccounts`, `cash`, `id`, `time` (`HH:MM`)
- `;` comments

Debit is a positive posting amount. Credit is a negative posting amount. Account roots are `Assets`, `Liabilities`, `Equity`, `Income`, `Expenses`. A price’s quote currency is the operating currency.

Account metadata and commodity metadata are how a round trip keeps display names, the three account types, the cash flag, and ids. A missing `type` defaults from the root: Assets and Expenses are active; Liabilities, Equity, and Income are passive.

## Data model

- **Commodity** — id, code, name, operating flag, open date. Exactly one operating commodity.
- **Quote** — commodity, date, price in the operating currency. One quote per commodity per date.
- **Account** — id, parent id, display name, Beancount name, type, section, allows-subaccounts, cash, open date, close date.
- **Entry** — id, date, optional minutes from midnight, payee, narration, debit posting, credit posting.
- **Posting** — account, side, positive quantity, commodity, positive booked amount in the operating currency.

Positions on balance-sheet accounts use average cost. Reducing a position realizes the difference between the booked proceeds and the relieved cost. That difference is part of the financial result, not a third posting. The entry itself stays two-legged, which is the limit of this version: a sale at a price other than cost does not need a separate gain account.

## UI / UX

1. Journal: dark bar (menu, span title, overflow with **Сегодня**), date strip with chevrons, cash delta for the span, entry list, teal circular button that starts an entry.
2. Drawer, dark, icon and label: Поиск, Счета, Единицы, Отчёты, Настройки, Импорт / Экспорт.
3. Entry: large amount, commodity chip, cyan underline, date and time, green **Дебет** and **Кредит** rows, price when the commodity is not the operating currency, payee, narration, keypad. **Текст** switches to the Beancount editor. Save is disabled while that text is invalid, and the error is shown above the keypad.
4. Chart: tree by раздел, with type and whether subaccounts are allowed. Editor for the fields in requirement 2.
5. Commodities: code, name, price history. Operating commodity has no price editor.
6. Reports: tabs Баланс, Движение денег, Результат, with a date or a period.
7. Import shows parse errors and does not change the book. A successful parse asks before replacing every account, commodity, price, and entry.

Palette from the reference: charcoal bar `#2C2C2C`, paper background, teal `#148F82` for the add button, green account names, red outflow, blue digit keys, orange operators, red AC, gray DEL, green OK.

## Out of scope

- More than two postings in one entry
- Beancount `balance`, `pad`, `include`, plugins, and the note/document/event/query/custom directives
- Several books, or more than one operating currency
- Budgets, envelopes, subscriptions, and a category axis separate from the chart of accounts
- Cloud sync and a home-screen widget

## Acceptance criteria

1. A new book has one operating commodity and the seeded chart. The user can add an account of each type, forbid or allow subaccounts, and add a child only when the parent allows it.
2. After a parent has a child, a new entry cannot use the parent and can use the child.
3. Saving an entry requires a debit account, a credit account, and equal operating-currency value. The Beancount text of that entry parses back to the same two postings. Invalid text is refused.
4. A commodity price changes the balance-sheet value of an open quantity. The balance sheet’s two sides stay equal. The financial result includes a realized difference when a quantity is reduced at another booked price, and does not include unrealized revaluation.
5. Cash flow opening plus classified flows equals closing cash, in the operating currency.
6. Export then import of the same file restores title, commodities, quotes, accounts (including type, cash, subaccounts flag, parent), and entries.
7. `./gradlew :app:testDebugUnitTest assembleRelease` succeeds. With the sideload keystore present, the release APK matches `android/verify-apk-sideload-cert.sh`.

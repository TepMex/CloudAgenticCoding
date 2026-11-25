import { useMemo, useState } from "react";
import {
  entriesWord,
  gloss,
  kindLabel,
  neighbors,
  sectionFor,
  syntheticCode,
  type Account,
  type Entry,
} from "./lib/catalog";
import { QUADRANTS, classifyEntry, isDouble, type Classified } from "./lib/classify";
import { matrixNote, type QuadrantId } from "./lib/poles";

export function AccountPage({ account }: { account: Account }) {
  const [query, setQuery] = useState("");
  const { prev, next } = neighbors(account.code);
  const needle = query.trim().toLocaleLowerCase("ru");
  const grouped = useMemo(() => groupEntries(account, needle), [account, needle]);
  const shown = QUADRANTS.reduce((sum, item) => sum + grouped[item.id].length, 0);
  const singles = account.entries.filter((entry) => !isDouble(entry) && entryMatches(entry, needle));

  return (
    <article className="account">
      <div className="topbar">
        <a className="back" href="#chart">
          ← Все счета
        </a>
        <nav className="pager" aria-label="Соседние счета">
          {prev ? (
            <a href={`#${prev.code}`}>
              ← {prev.code}
            </a>
          ) : (
            <span />
          )}
          {next ? (
            <a href={`#${next.code}`}>
              {next.code} →
            </a>
          ) : (
            <span />
          )}
        </nav>
      </div>
      <header className="account-head">
        <p className="eyebrow">{sectionFor(account.code).title}</p>
        <h1 id="account-title" tabIndex={-1}>
          <span className={`code-badge cell-${account.offBalance ? "active" : account.kind}`}>{account.code}</span>
          {account.name}
        </h1>
        <p className={`kind-flag kind-${account.offBalance ? "off" : account.kind}`}>{kindLabel(account)}</p>
        <p className="meaning">{account.meaning}</p>
      </header>
      <div className="signs">
        <SignCard side="debit" account={account} text={account.debitMeaning} />
        <SignCard side="credit" account={account} text={account.creditMeaning} />
      </div>
      {account.notes ? (
        <aside className="notes">
          <h2>Особенности</h2>
          <p>{account.notes}</p>
        </aside>
      ) : null}
      <p className="board-lead">{matrixNote(account)}</p>
      {account.offBalance ? (
        <OffBalance entries={singles} account={account} />
      ) : (
        <>
          <div className="board-tools">
            <label className="search">
              <span>Найти проводку</span>
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="поставщик, 60, сторно"
              />
            </label>
            <p className="count">
              {needle ? `${shown} из ${account.entries.length}` : entriesWord(account.entries.length)}
            </p>
          </div>
          <nav className="jumps" aria-label="Клетки проводок">
            {QUADRANTS.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => {
                  document.getElementById(`q-${account.code}-${item.id}`)?.scrollIntoView({ behavior: "smooth", block: "start" });
                }}
              >
                {item.badges.map((badge) => badge.text).join(" · ")}
                <span>{grouped[item.id].length}</span>
              </button>
            ))}
          </nav>
          <div className="quads quads-board">
            {QUADRANTS.map((item) => (
              <section key={item.id} id={`q-${account.code}-${item.id}`} className={`quad wash-${item.wash}`}>
                <h2>{item.title}</h2>
                <div className="badges">
                  {item.badges.map((badge) => (
                    <span key={badge.text} className={`badge pole-${badge.pole === "A" ? "a" : "p"}`}>
                      {badge.text}
                    </span>
                  ))}
                </div>
                <p className="rule">{item.rule}</p>
                {grouped[item.id].length === 0 ? (
                  <p className="empty">
                    {needle
                      ? "Нет совпадений в этой клетке."
                      : "В справочнике для этого счёта таких проводок нет."}
                  </p>
                ) : (
                  <ol className="postings">
                    {grouped[item.id].map((entry) => (
                      <Posting key={entry.id} entry={entry} current={account.code} />
                    ))}
                  </ol>
                )}
              </section>
            ))}
          </div>
        </>
      )}
      {account.sources.length > 0 ? (
        <aside className="sources">
          <h2>Источники</h2>
          <ul>
            {account.sources.map((source) => (
              <li key={source.url}>
                <a href={source.url} target="_blank" rel="noreferrer">
                  {source.title}
                </a>
              </li>
            ))}
          </ul>
        </aside>
      ) : null}
    </article>
  );
}

function SignCard({ side, account, text }: { side: "debit" | "credit"; account: Account; text: string }) {
  const grows = signGrows(account, side);
  return (
    <section className={`sign ${grows === true ? "sign-up" : grows === false ? "sign-down" : "sign-mixed"}`}>
      <h2>
        {grows === true ? <span className="plus">+</span> : null}
        {grows === false ? <span className="minus">−</span> : null}
        {side === "debit" ? "Дебет" : "Кредит"}
      </h2>
      <p>{text}</p>
    </section>
  );
}

function signGrows(account: Account, side: "debit" | "credit"): boolean | null {
  if (account.kind === "active-passive") return null;
  const debitUp = account.kind === "active" || account.offBalance;
  return side === "debit" ? debitUp : !debitUp;
}

function OffBalance({ entries, account }: { entries: Entry[]; account: Account }) {
  const debits = entries.filter((entry) => entry.debit);
  const credits = entries.filter((entry) => entry.credit);
  return (
    <div className="off-board">
      <section className="quad wash-blue">
        <h2>Приняли на контроль</h2>
        <div className="badges">
          <span className="badge pole-a">Дт {account.code}</span>
        </div>
        <p className="rule">Односторонняя запись: за балансом стало больше.</p>
        <EntryList entries={debits} current={account.code} />
      </section>
      <section className="quad wash-blue">
        <h2>Сняли с контроля</h2>
        <div className="badges">
          <span className="badge pole-a">Кт {account.code}</span>
        </div>
        <p className="rule">Односторонняя запись: за балансом стало меньше.</p>
        <EntryList entries={credits} current={account.code} />
      </section>
    </div>
  );
}

function EntryList({ entries, current }: { entries: Entry[]; current: string }) {
  if (entries.length === 0) return <p className="empty">Таких записей нет.</p>;
  return (
    <ol className="postings">
      {entries.map((entry) => (
        <Posting key={entry.id} entry={entry} current={current} />
      ))}
    </ol>
  );
}

function Posting({ entry, current }: { entry: Entry; current: string }) {
  const placed = isDouble(entry) ? classifyEntry(entry) : null;
  return (
    <li className={entry.storno ? "posting is-storno" : "posting"}>
      {entry.storno ? (
        <p className="storno">Сторно — отрицательная сумма, а не обратная проводка. Знаки ниже — у записи без минуса.</p>
      ) : null}
      <div className="pair">
        {entry.debit ? <Side token={entry.debit} side="debit" current={current} placed={placed} /> : null}
        {entry.credit ? <Side token={entry.credit} side="credit" current={current} placed={placed} /> : null}
      </div>
      <p className="posting-text">{entry.text}</p>
      {placed ? (
        <p className="movement">
          {gloss(entry.debit!)} — {placed.debitPole === "A" ? "актив" : "пассив"} {placed.debitGrows ? "растёт" : "уменьшается"}
          {" · "}
          {gloss(entry.credit!)} — {placed.creditPole === "A" ? "актив" : "пассив"} {placed.creditGrows ? "растёт" : "уменьшается"}
        </p>
      ) : null}
      {entry.tags.length > 0 ? (
        <ul className="tags">
          {entry.tags.map((tag) => (
            <li key={tag}>{tag}</li>
          ))}
        </ul>
      ) : null}
    </li>
  );
}

function Side({
  token,
  side,
  current,
  placed,
}: {
  token: string;
  side: "debit" | "credit";
  current: string;
  placed: Classified | null;
}) {
  const pole = side === "debit" ? placed?.debitPole : placed?.creditPole;
  const up = placed ? (side === "debit" ? placed.debitGrows : placed.creditGrows) : side === "debit";
  const here = syntheticCode(token) === current;
  const code = syntheticCode(token);
  return (
    <div className={`side ${pole === "P" ? "is-p" : "is-a"}${here ? " is-current" : ""}`}>
      <span className="side-label">
        <b className={up ? "plus" : "minus"}>{up ? "+" : "−"}</b> {side === "debit" ? "Дебет" : "Кредит"}
      </span>
      {here ? <strong className="side-code">{token}</strong> : <a className="side-code" href={`#${code}`}>{token}</a>}
      <span className="side-gloss">{gloss(token)}</span>
    </div>
  );
}

function groupEntries(account: Account, needle: string): Record<QuadrantId, Entry[]> {
  const grouped: Record<QuadrantId, Entry[]> = { "da-ka": [], "dp-kp": [], "da-kp": [], "dp-ka": [] };
  for (const entry of account.entries) {
    if (!isDouble(entry) || !entryMatches(entry, needle)) continue;
    grouped[classifyEntry(entry).quadrant].push(entry);
  }
  return grouped;
}

function entryMatches(entry: Entry, needle: string): boolean {
  if (!needle) return true;
  return [entry.debit, entry.credit, entry.text, entry.storno ? "сторно" : "", ...entry.tags]
    .filter(Boolean)
    .join(" ")
    .toLocaleLowerCase("ru")
    .includes(needle);
}

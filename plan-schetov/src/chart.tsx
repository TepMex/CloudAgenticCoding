import { useMemo, useState, type FormEvent } from "react";
import { accountMatchesQuery, byCode, cellCode, kindLetter, MATRIX_ROWS, type Account, type Kind } from "./lib/catalog";

type KindFilter = "all" | Kind;

const FILTERS: { id: KindFilter; label: string }[] = [
  { id: "all", label: "Все" },
  { id: "active", label: "Активные" },
  { id: "passive", label: "Пассивные" },
  { id: "active-passive", label: "Активно-пассивные" },
];

function matches(account: Account, query: string, filter: KindFilter): boolean {
  if (filter !== "all" && account.kind !== filter) return false;
  return accountMatchesQuery(account, query);
}

export function Chart({ unknown }: { unknown: string | null }) {
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<KindFilter>("all");
  const balance = useMemo(() => catalogBalance(), []);
  const visible = balance.filter((account) => matches(account, query, filter));
  const visibleOff = catalogOff().filter((account) => matches(account, query, filter));
  const filtering = query.trim() !== "" || filter !== "all";

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    const hits = [...visible, ...visibleOff];
    const exact = hits.find((account) => account.code === query.trim());
    const target = exact ?? (hits.length === 1 ? hits[0] : null);
    if (target) location.hash = target.code;
  }

  return (
    <div className="chart-page">
      {unknown ? (
        <p className="banner" role="status">
          Счёта {unknown} в этом справочнике нет. В матрице такой номер — пропуск плана № 94н.
        </p>
      ) : null}
      <section className="chart-section" aria-labelledby="chart-title">
        <div className="section-head">
          <h2 id="chart-title">Счета, матрица 10×10</h2>
          <p>
            Номер — это ряд и столбец: счёт 51 стоит в ряду 5 и столбце 1. Пустая клетка значит, что такого номера в плане нет.
          </p>
        </div>
        <form className="tools" onSubmit={onSubmit} role="search">
          <label className="search">
            <span>Найти счёт</span>
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="10, касса, зарплата"
              enterKeyHint="search"
            />
          </label>
          <div className="filters" role="group" aria-label="Тип счёта">
            {FILTERS.map((item) => (
              <button
                key={item.id}
                type="button"
                aria-pressed={filter === item.id}
                onClick={() => setFilter(item.id)}
              >
                {item.label}
              </button>
            ))}
          </div>
        </form>
        <p className="count">
          {filtering ? `Показаны ${visible.length} из ${balance.length} балансовых` : `${balance.length} балансовых счёта`}
        </p>
        <div className="legend">
          <span><i className="swatch swatch-a" /> Активный</span>
          <span><i className="swatch swatch-p" /> Пассивный</span>
          <span><i className="swatch swatch-ap" /> Активно-пассивный</span>
          <span><i className="swatch swatch-gap" /> Пропуск</span>
        </div>
        <div className="chart-scroll">
          <div className="chart">
            <div className="corner" />
            {Array.from({ length: 10 }, (_, col) => (
              <div key={`col-${col}`} className="colhead">
                {col}
              </div>
            ))}
            {MATRIX_ROWS.map((row) => (
              <Row
                key={row.row}
                row={row.row}
                title={row.title}
                hint={row.hint}
                query={query}
                filter={filter}
                filtering={filtering}
              />
            ))}
          </div>
        </div>
      </section>
      <section className="off-section" aria-labelledby="off-title">
        <div className="section-head">
          <h2 id="off-title">За балансом</h2>
          <p>Счета 001–011 не занимают клетки 01–11. Те клетки — балансовые счета, а эти ведутся одной стороной, без пары.</p>
        </div>
        <div className="off-grid">
          {catalogOff().map((account) => {
            const dim = filtering && !matches(account, query, filter);
            return (
              <a
                key={account.code}
                href={`#${account.code}`}
                className={`cell cell-active${dim ? " is-dim" : ""}`}
              >
                <span className="code">{account.code}</span>
                <span className="name">{account.name}</span>
              </a>
            );
          })}
        </div>
      </section>
    </div>
  );
}

function Row({
  row,
  title,
  hint,
  query,
  filter,
  filtering,
}: {
  row: number;
  title: string;
  hint?: string;
  query: string;
  filter: KindFilter;
  filtering: boolean;
}) {
  return (
    <>
      <div className="rail" title={hint ? `${title}, ${hint}` : title}>
        <b>{row}</b>
        <span>{title}</span>
        {hint ? <span className="rail-hint">{hint}</span> : null}
      </div>
      {Array.from({ length: 10 }, (_, col) => {
        const code = cellCode(row, col);
        const account = byCode.get(code);
        if (!account) {
          return <div key={code} className="cell cell-gap" aria-hidden="true" />;
        }
        const dim = filtering && !matches(account, query, filter);
        const hit = query.trim() === account.code;
        return (
          <a
            key={code}
            href={`#${code}`}
            className={`cell cell-${account.kind}${dim ? " is-dim" : ""}${hit ? " is-hit" : ""}`}
            aria-label={`${code} ${account.name}, ${kindLetter(account)}`}
            title={`${code} ${account.name}`}
          >
            <span className="code">
              {code}
              <small>{kindLetter(account)}</small>
            </span>
            <span className="name">{account.name}</span>
          </a>
        );
      })}
    </>
  );
}

function catalogBalance(): Account[] {
  return [...byCode.values()].filter((account) => !account.offBalance);
}

function catalogOff(): Account[] {
  return [...byCode.values()].filter((account) => account.offBalance);
}

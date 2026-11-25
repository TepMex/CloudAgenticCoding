import { useEffect, useState } from "react";
import { AccountPage } from "./account";
import { Chart } from "./chart";
import { Cheatsheet } from "./cheatsheet";
import { byCode, catalog } from "./lib/catalog";

function useHash(): string {
  const [hash, setHash] = useState(() => window.location.hash);
  useEffect(() => {
    const onChange = () => setHash(window.location.hash);
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);
  return hash;
}

function codeFromHash(hash: string): string {
  const raw = decodeURIComponent(hash.replace(/^#/, ""));
  if (raw === "chart" || raw === "top") return "";
  if (byCode.has(raw)) return raw;
  if (raw.startsWith("a") && byCode.has(raw.slice(1))) return raw.slice(1);
  return raw;
}

export function App() {
  const hash = useHash();
  const raw = codeFromHash(hash);
  const account = byCode.get(raw) ?? null;

  useEffect(() => {
    document.title = account ? `${account.code} · ${account.name} · План счетов` : "План счетов · дебет и кредит";
    if (!account && hash === "#chart") {
      document.getElementById("chart")?.scrollIntoView();
      return;
    }
    window.scrollTo(0, 0);
  }, [account, hash]);

  return (
    <div className="page">
      <a className="skip" href={account ? "#account-title" : "#chart"}>
        К содержанию
      </a>
      {account ? (
        <AccountPage account={account} />
      ) : (
        <>
          <header className="hero">
            <p className="eyebrow">Учебный справочник · план № 94н</p>
            <h1>План счетов</h1>
            <p className="lede">
              Для тех, кто только разбирает дебет и кредит. {catalog.balanceCount} балансовых счёта стоят в матрице 10×10,
              пустые номера оставлены пропусками. Проводки каждого счёта разложены по четырём клеткам шпаргалки.
            </p>
          </header>
          <Cheatsheet />
          <div id="chart">
            <Chart unknown={/^\d{2,3}$/.test(raw) ? raw : null} />
          </div>
        </>
      )}
      <footer className="site-footer">
        <p>{catalog.basis}</p>
        <p>
          Дебет и кредит здесь показывают смысл записи. Сторно — отрицательная сумма, а не обратная проводка. Субсчета в части
          примеров совпадают с 1С и могут отличаться от рабочего плана организации. Одна операция часто требует нескольких
          проводок. Суммы и ставки не разбираются. Это не учётная политика и не консультация.
        </p>
      </footer>
    </div>
  );
}

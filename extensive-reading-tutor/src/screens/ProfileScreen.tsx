import { Header } from "../components.tsx";
import { DEPLOYED_AT, COMMIT_MESSAGE } from "../lib/deploy-info.ts";
import { buildHeatmap } from "../lib/heatmap.ts";
import { plural } from "../lib/text.ts";
import { useApp } from "../state.tsx";

export function ProfileScreen() {
  const { state, go } = useApp();
  const heat = buildHeatmap(state.activity);
  const words = state.books.reduce((sum, book) => sum + book.words, 0);
  const chars = state.books.reduce((sum, book) => sum + book.chars, 0);
  const observations = state.books.reduce((sum, book) => sum + book.observations, 0);
  const reading = state.books.filter(book => book.status === "reading").length;

  return (
    <div className="screen" data-screen="profile">
      <Header
        large={
          <div className="title-row">
            <h1 className="large-title">Профиль</h1>
            <button className="linkish" type="button" onClick={() => go({ name: "settings" })}>
              Изменить
            </button>
          </div>
        }
      />
      <div className="content">
        <div className="section-label">Мой прогресс</div>
        <div className="stat4">
          <div>
            <b>{reading}</b>
            <span>
              книги
              <br />
              (самостоятельно)
            </span>
          </div>
          <div>
            <b>{words}</b>
            <span>новых слов</span>
          </div>
          <div>
            <b>{chars}</b>
            <span>знаков</span>
          </div>
          <div>
            <b>{observations}</b>
            <span>{plural(observations, "наблюдение", "наблюдения", "наблюдений")}</span>
          </div>
        </div>
        <button className="chapter-row" type="button" onClick={() => go({ name: "settings" })}>
          <i className="dot current" />
          <span>
            <strong>Настройки ИИ и обучения</strong>
            <div className="muted">{state.settings.model} · {state.settings.hsk} · {state.settings.explainLanguage}</div>
          </span>
        </button>
        <div className="section-label">Активность</div>
        <div className="card" style={{ padding: 12 }}>
          <div className="heat-scroll">
            <div className="heat-months" style={{ gridTemplateColumns: `repeat(${heat.months.length}, 14px)` }}>
              {heat.months.map((label, index) => (
                <span key={index}>{label}</span>
              ))}
            </div>
            <div className="heat" aria-label="Календарь чтения">
              {heat.cells.map(cell => (
                <b key={cell.date} className={cell.level === 0 ? "lv0" : cell.level === 1 ? "lv1" : "lv2"} title={cell.date} />
              ))}
            </div>
          </div>
          <div className="legend">
            <span><i className="lv2" /> Занятие</span>
            <span><i className="lv1" /> Лёгкое чтение</span>
            <span><i className="lv0" /> Нет активности</span>
          </div>
        </div>
        <footer className="deploy">
          <div>Сборка {DEPLOYED_AT}</div>
          <div>{COMMIT_MESSAGE}</div>
        </footer>
      </div>
    </div>
  );
}

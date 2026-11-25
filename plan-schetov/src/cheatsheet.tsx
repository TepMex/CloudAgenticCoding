import { QUADRANTS } from "./lib/classify";

function Coins() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <ellipse cx="12" cy="7" rx="7" ry="3" fill="none" stroke="currentColor" strokeWidth="1.7" />
      <path d="M5 7v4c0 1.7 3.1 3 7 3s7-1.3 7-3V7" fill="none" stroke="currentColor" strokeWidth="1.7" />
      <path d="M5 11v4c0 1.7 3.1 3 7 3s7-1.3 7-3v-4" fill="none" stroke="currentColor" strokeWidth="1.7" />
    </svg>
  );
}

function Sheet() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M7 3.5h7l4 4V20a1.5 1.5 0 0 1-1.5 1.5h-9.5A1.5 1.5 0 0 1 5.5 20V5A1.5 1.5 0 0 1 7 3.5z" fill="none" stroke="currentColor" strokeWidth="1.7" />
      <path d="M14 3.8V8h4.2M8 12h8M8 16h6" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
    </svg>
  );
}

export function Cheatsheet() {
  return (
    <section className="poster" aria-labelledby="poster-title">
      <h2 id="poster-title">Шпаргалка по проводкам</h2>
      <p className="poster-sub">Четыре клетки, в которые ниже встают проводки каждого счёта.</p>
      <div className="poles">
        <p className="pole pole-a">
          <Coins />
          <span>
            <b>Актив:</b> <span className="plus">+</span> по дебету, <span className="minus">−</span> по кредиту
          </span>
        </p>
        <p className="pole pole-p">
          <Sheet />
          <span>
            <b>Пассив:</b> <span className="plus">+</span> по кредиту, <span className="minus">−</span> по дебету
          </span>
        </p>
      </div>
      <div className="quads">
        {QUADRANTS.map((item) => (
          <article key={item.id} className={`quad wash-${item.wash}`}>
            <h3>{item.title}</h3>
            <div className="badges">
              {item.badges.map((badge) => (
                <span key={badge.text} className={`badge pole-${badge.pole === "A" ? "a" : "p"}`}>
                  {badge.text}
                </span>
              ))}
            </div>
            <p className="rule">{item.rule}</p>
            <p className="example">
              <b>Например:</b> {item.example}
            </p>
          </article>
        ))}
      </div>
      <div className="memory">
        <p className="memory-title">Как запомнить</p>
        <ul>
          <li>
            <span className="check" aria-hidden="true">
              ✓
            </span>
            Дебет увеличивает <span className="pill pill-a">актив</span> и уменьшает <span className="pill pill-p">пассив</span>
          </li>
          <li>
            <span className="check" aria-hidden="true">
              ✓
            </span>
            Кредит уменьшает <span className="pill pill-a">актив</span> и увеличивает <span className="pill pill-p">пассив</span>
          </li>
        </ul>
      </div>
    </section>
  );
}

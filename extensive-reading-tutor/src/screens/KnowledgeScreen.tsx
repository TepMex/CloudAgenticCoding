import { useState } from "react";
import { Header, Segmented, kindLabel, statusLabel } from "../components.tsx";
import { useApp } from "../state.tsx";
import type { WordStatus } from "../lib/types.ts";

type StatusFilter = "all" | WordStatus;

export function KnowledgeScreen() {
  const { state, go, openWord, cycleSkill } = useApp();
  const tab = state.route.name === "knowledge" ? state.route.tab : "words";
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<StatusFilter>("all");
  const needle = query.trim().toLowerCase();
  const words = state.glosses.filter(gloss => gloss.kind !== "grammar");
  const grammar = state.glosses.filter(gloss => gloss.kind === "grammar");
  const match = (hay: string) => !needle || hay.toLowerCase().includes(needle);

  const wordRows = words.filter(gloss => {
    if (filter === "repeat" || filter === "known") {
      if (gloss.status !== filter) return false;
    } else if (filter === "learning" && gloss.status !== "learning") return false;
    return match(`${gloss.hanzi} ${gloss.pinyin} ${gloss.gloss}`);
  });

  return (
    <div className="screen" data-screen="knowledge">
      <Header large={<h1 className="large-title">Мои знания</h1>} />
      <div className="content">
        <Segmented
          value={tab}
          onChange={next => go({ name: "knowledge", tab: next })}
          options={[
            { id: "words", label: "Слова и выражения" },
            { id: "grammar", label: "Грамматика" },
            { id: "skills", label: "Навыки" },
          ]}
        />
        <input className="search" name="query" value={query} onChange={event => setQuery(event.target.value)} placeholder="Поиск по словам и выражениям" />
        {tab === "words" && (
          <>
            <div className="chips" style={{ marginTop: 10 }}>
              {(
                [
                  ["all", `Все (${words.length})`],
                  ["repeat", `Нужно повторить (${words.filter(gloss => gloss.status === "repeat").length})`],
                  ["known", `Знаю (${words.filter(gloss => gloss.status === "known").length})`],
                ] as const
              ).map(([id, label]) => (
                <button key={id} type="button" className={filter === id ? "chip on" : "chip"} onClick={() => setFilter(id)}>
                  {label}
                </button>
              ))}
            </div>
            {wordRows.map(gloss => (
              <button key={gloss.id} type="button" className="know-row" onClick={() => openWord(gloss.id)}>
                <span>
                  <h3>{gloss.hanzi}</h3>
                  <p>
                    {gloss.gloss}. Последнее: {gloss.lastSeen}
                  </p>
                </span>
                <span className="know-meta">
                  {gloss.kind !== "word" && <span className={`badge ${gloss.kind}`}>{kindLabel(gloss.kind)}</span>}
                  <span className={`badge ${gloss.status}`}>{statusLabel(gloss.status)}</span>
                </span>
              </button>
            ))}
            {wordRows.length === 0 && <p className="muted">Ничего не нашлось.</p>}
          </>
        )}
        {tab === "grammar" &&
          grammar
            .filter(gloss => match(`${gloss.hanzi} ${gloss.gloss}`))
            .map(gloss => (
              <button key={gloss.id} type="button" className="know-row" onClick={() => openWord(gloss.id)}>
                <span>
                  <h3>{gloss.hanzi}</h3>
                  <p>{gloss.gloss}</p>
                </span>
                <span className={`badge ${gloss.status}`}>{statusLabel(gloss.status)}</span>
              </button>
            ))}
        {tab === "skills" &&
          state.skills
            .filter(skill => match(`${skill.title} ${skill.detail}`))
            .map(skill => (
              <button key={skill.id} type="button" className="know-row" onClick={() => cycleSkill(skill.id)}>
                <span>
                  <h3 style={{ fontFamily: "var(--ui)" }}>{skill.title}</h3>
                  <p>{skill.detail}</p>
                </span>
                <span className={`badge ${skill.status}`}>{statusLabel(skill.status)}</span>
              </button>
            ))}
        {tab === "skills" && <p className="help">Нажатие меняет статус: повторить → в процессе → знаю.</p>}
      </div>
    </div>
  );
}

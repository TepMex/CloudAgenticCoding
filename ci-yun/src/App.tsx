import { useEffect, useMemo, useRef, useState, type ComponentType, type KeyboardEvent } from "react";
import { glossOf, sourceLine } from "./lib/gloss";
import { rankWords } from "./lib/search";
import type { Cloud, Mode, Word } from "./lib/types";
import { CloudStage, type CloudStageProps } from "./scene/CloudStage";

type AppProps = {
  cloud: Cloud;
  Stage?: ComponentType<CloudStageProps>;
};

export default function App({ cloud, Stage = CloudStage }: AppProps) {
  const [mode, setMode] = useState<Mode>("3d");
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(0);
  const [focusId, setFocusId] = useState<number | null>(null);
  const [activeCluster, setActiveCluster] = useState<number | null>(null);
  const [resetToken, setResetToken] = useState(0);
  const boxRef = useRef<HTMLDivElement>(null);

  const matches = useMemo(() => rankWords(cloud.words, query), [cloud.words, query]);
  const activeIndex = matches.length === 0 ? 0 : Math.min(highlight, matches.length - 1);
  const focusWord = focusId == null ? undefined : cloud.words[focusId];
  const legend = useMemo(() => [...cloud.clusters].sort((left, right) => right.size - left.size), [cloud.clusters]);

  useEffect(() => {
    function onPointerDown(event: PointerEvent) {
      if (!boxRef.current?.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, []);

  function center(word: Word) {
    setFocusId(word.id);
    setActiveCluster(null);
    setQuery(word.simplified);
    setOpen(false);
  }

  function resetView() {
    setFocusId(null);
    setActiveCluster(null);
    setQuery("");
    setOpen(false);
    setResetToken((value) => value + 1);
  }

  function chooseCluster(id: number) {
    setFocusId(null);
    setQuery("");
    setOpen(false);
    setActiveCluster((current) => (current === id ? null : id));
    setResetToken((value) => value + 1);
  }

  function centerById(id: number) {
    const word = cloud.words[id];
    if (word) center(word);
  }

  function onQueryKey(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setOpen(true);
      setHighlight((value) => Math.min(value + 1, Math.max(matches.length - 1, 0)));
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setOpen(true);
      setHighlight((value) => Math.max(value - 1, 0));
    } else if (event.key === "Enter") {
      event.preventDefault();
      const chosen = matches[activeIndex];
      if (chosen) center(chosen);
    } else if (event.key === "Escape") {
      setOpen(false);
    }
  }

  const listId = "word-suggestions";
  const missing = query.trim().length > 0 && matches.length === 0;

  return (
    <>
      <Stage
        cloud={cloud}
        mode={mode}
        focusId={focusId}
        activeCluster={activeCluster}
        resetToken={resetToken}
        onPick={centerById}
      />
      <header className="hud">
        <div className="title-row">
          <div>
            <p className="mark">词云</p>
            <h1>Облако лексики</h1>
          </div>
          <div className="modes" role="radiogroup" aria-label="Проекция">
            <button type="button" role="radio" aria-checked={mode === "2d"} onClick={() => setMode("2d")}>
              Плоскость
            </button>
            <button type="button" role="radio" aria-checked={mode === "3d"} onClick={() => setMode("3d")}>
              Пространство
            </button>
          </div>
        </div>
        <p className="meta">
          {cloud.words.length.toLocaleString("ru-RU")} слов · {cloud.clusters.length} кластеров · EmbeddingGemma 2
        </p>
        <div className="search" ref={boxRef}>
          <input
            role="combobox"
            aria-expanded={open && matches.length > 0}
            aria-controls={listId}
            aria-autocomplete="list"
            aria-activedescendant={open && matches[activeIndex] ? `word-opt-${matches[activeIndex]!.id}` : undefined}
            placeholder="Слово из списка, например 经济"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setHighlight(0);
              setOpen(true);
            }}
            onFocus={() => setOpen(true)}
            onKeyDown={onQueryKey}
          />
          {missing ? <p className="missing">Нет в списке</p> : null}
          {open && matches.length > 0 ? (
            <ul id={listId} role="listbox">
              {matches.map((word, index) => (
                <li key={word.id} role="presentation">
                  <button
                    type="button"
                    role="option"
                    id={`word-opt-${word.id}`}
                    aria-selected={index === activeIndex}
                    onMouseEnter={() => setHighlight(index)}
                    onClick={() => center(word)}
                  >
                    <span className="hanzi">{word.simplified}</span>
                    <span className="suggest-meta">
                      {word.pinyin}
                      {glossOf(word) ? ` · ${glossOf(word)}` : ""}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
        {focusWord ? null : <p className="hint">Введите слово — облако встанет вокруг него.</p>}
        {focusWord ? (
          <article className="card" data-centered={focusWord.simplified}>
            <p className="hanzi">{focusWord.simplified}</p>
            {focusWord.traditional && focusWord.traditional !== focusWord.simplified ? (
              <p className="traditional">{focusWord.traditional}</p>
            ) : null}
            <p className="pinyin">{focusWord.pinyin}</p>
            <p className="gloss">{glossOf(focusWord)}</p>
            {focusWord.pos ? <p className="pos">{focusWord.pos}</p> : null}
            <p className="source">{sourceLine(focusWord)}</p>
            <p className="cluster-name">
              <i style={{ background: cloud.clusters[focusWord.cluster]?.color }} />
              {cloud.clusters[focusWord.cluster]?.name}
            </p>
            {focusWord.neighbors.length > 0 ? (
              <div className="neighbors">
                {focusWord.neighbors.map((id) => {
                  const neighbor = cloud.words[id];
                  if (!neighbor) return null;
                  return (
                    <button type="button" key={id} onClick={() => center(neighbor)}>
                      {neighbor.simplified}
                    </button>
                  );
                })}
              </div>
            ) : null}
          </article>
        ) : null}
        <button type="button" className="reset" onClick={resetView}>
          Всё облако
        </button>
      </header>
      <nav className="legend" aria-label="Кластеры">
        {legend.map((cluster) => (
          <button
            type="button"
            key={cluster.id}
            className="chip"
            aria-pressed={activeCluster === cluster.id}
            onClick={() => chooseCluster(cluster.id)}
          >
            <i style={{ background: cluster.color }} />
            {cluster.name}
            <span>{cluster.size}</span>
          </button>
        ))}
      </nav>
    </>
  );
}

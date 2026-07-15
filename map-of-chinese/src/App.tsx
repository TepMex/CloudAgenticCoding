import { useEffect, useMemo, useRef, useState, type KeyboardEvent, type WheelEvent } from "react";
import charactersJson from "./data/characters.json";
import cellsJson from "./data/syllable-cells.json";
import searchJson from "./data/search-index.json";
import manifestJson from "./data/data-manifest.json";
import type { CharacterRecord, Hsk3Level, SearchEntry, SyllableCell, Tone } from "./data/schema";
import { displayFinal, FINAL_GROUPS, INITIALS, normalizePinyinForSearch } from "./lib/pinyin";

const characters = charactersJson as CharacterRecord[];
const cells = cellsJson as SyllableCell[];
const searchIndex = searchJson as SearchEntry[];
const manifest = manifestJson as typeof manifestJson & { reconciliation: { issue: string; policy: string; unreconciledLevel6Count: number } };
const characterById = new Map(characters.map((record) => [record.character, record]));
const cellByKey = new Map(cells.map((cell) => [cell.key, cell]));
const allFinals = FINAL_GROUPS.flatMap((group) => [...group.finals]);
const toneOptions: Array<{ value: "all" | Tone; label: string; short: string }> = [
  { value: "all", label: "All tones", short: "All" },
  { value: 1, label: "First tone", short: "1st" },
  { value: 2, label: "Second tone", short: "2nd" },
  { value: 3, label: "Third tone", short: "3rd" },
  { value: 4, label: "Fourth tone", short: "4th" },
  { value: 5, label: "Neutral tone", short: "Neutral" },
];

type Scope = "basic" | "hsk" | "all";
type HskSystem = "none" | "hsk2" | "hsk3";
type Density = "overview" | "examples" | "expanded";

function cellKey(initial: string, final: string): string {
  const normalizedFinal = final === "i" && ["z", "c", "s", "zh", "ch", "sh", "r"].includes(initial) ? "apical-i" : final;
  return `${initial || "∅"}|${normalizedFinal}`;
}

function hskOrder(level: number | Hsk3Level): number {
  return level === "7-9" ? 7 : level;
}

function uniqueEntries(cell: SyllableCell, tone: "all" | Tone): SyllableCell["entries"] {
  const seen = new Set<string>();
  return cell.entries.filter((entry) => {
    if (tone !== "all" && entry.tone !== tone) return false;
    if (seen.has(entry.character)) return false;
    seen.add(entry.character);
    return true;
  });
}

function Segment<T extends string | number>({ value, current, label, onSelect }: { value: T; current: T; label: string; onSelect: (value: T) => void }) {
  return <button type="button" className={current === value ? "segment active" : "segment"} aria-pressed={current === value} onClick={() => onSelect(value)}>{label}</button>;
}

function Badge({ children, muted = false }: { children: React.ReactNode; muted?: boolean }) {
  return <span className={muted ? "badge muted" : "badge"}>{children}</span>;
}

function App() {
  const [scope, setScope] = useState<Scope>("basic");
  const [tone, setTone] = useState<"all" | Tone>("all");
  const [hskSystem, setHskSystem] = useState<HskSystem>("none");
  const [hskLevels, setHskLevels] = useState<Set<string>>(new Set());
  const [cumulative, setCumulative] = useState(false);
  const [includeNotHsk, setIncludeNotHsk] = useState(false);
  const [density, setDensity] = useState<Density>("examples");
  const [query, setQuery] = useState("");
  const [selectedCellKey, setSelectedCellKey] = useState<string | null>(null);
  const [selectedCharacter, setSelectedCharacter] = useState<string | null>(null);
  const [showAbout, setShowAbout] = useState(false);
  const matrixRef = useRef<HTMLDivElement>(null);

  const passesScope = (record: CharacterRecord) => {
    if (scope === "basic") return record.inBasic3500;
    if (scope === "hsk") return record.hsk2Level !== null || record.hsk3_2026Level !== null;
    return true;
  };

  const passesHsk = (record: CharacterRecord) => {
    if (hskSystem === "none" || (hskLevels.size === 0 && !includeNotHsk)) return true;
    const level = hskSystem === "hsk2" ? record.hsk2Level : record.hsk3_2026Level;
    if (level === null) return includeNotHsk;
    if (hskLevels.size === 0) return false;
    if (cumulative) {
      const max = Math.max(...[...hskLevels].map((item) => hskOrder(item === "7-9" ? "7-9" : Number(item))));
      return hskOrder(level) <= max;
    }
    return hskLevels.has(String(level));
  };

  const filteredCells = useMemo(() => {
    const result = new Map<string, SyllableCell["entries"]>();
    for (const cell of cells) {
      const entries = uniqueEntries(cell, tone).filter((entry) => {
        const record = characterById.get(entry.character);
        return record ? passesScope(record) && passesHsk(record) : false;
      });
      if (entries.length) result.set(cell.key, entries);
    }
    return result;
    // Sets are replaced, not mutated in place.
  }, [scope, tone, hskSystem, hskLevels, cumulative, includeNotHsk]);

  const normalizedQuery = query.trim().toLowerCase();
  const pinyinQuery = normalizePinyinForSearch(normalizedQuery);
  const searchResults = useMemo(() => {
    if (!normalizedQuery) return [];
    return searchIndex.filter((entry) => {
      if (entry.searchable.includes(normalizedQuery)) return true;
      return pinyinQuery.length > 0 && entry.searchable.includes(pinyinQuery);
    }).slice(0, 20);
  }, [normalizedQuery, pinyinQuery]);
  const highlightedCharacters = useMemo(() => new Set(searchResults.map((entry) => entry.character)), [searchResults]);
  const highlightedCells = useMemo(() => new Set(searchResults.flatMap((entry) => entry.cellKeys)), [searchResults]);
  const totalVisible = useMemo(() => new Set([...filteredCells.values()].flat().map((entry) => entry.character)).size, [filteredCells]);

  const activeCell = selectedCellKey ? cellByKey.get(selectedCellKey) ?? null : null;
  const activeCellEntries = selectedCellKey ? filteredCells.get(selectedCellKey) ?? [] : [];
  const activeCharacter = selectedCharacter ? characterById.get(selectedCharacter) ?? null : null;

  useEffect(() => {
    const close = (event: globalThis.KeyboardEvent) => {
      if (event.key === "Escape") {
        setSelectedCharacter(null);
        setSelectedCellKey(null);
        setShowAbout(false);
      }
    };
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, []);

  const toggleHskLevel = (level: string) => {
    setHskLevels((previous) => {
      const next = new Set(previous);
      if (next.has(level)) next.delete(level);
      else next.add(level);
      return next;
    });
  };

  const focusCharacter = (character: string) => {
    const entry = searchIndex.find((item) => item.character === character);
    const firstVisible = entry?.cellKeys.find((key) => filteredCells.has(key)) ?? entry?.cellKeys[0];
    if (firstVisible) setSelectedCellKey(firstVisible);
    setSelectedCharacter(character);
  };

  const navigateToReading = (record: CharacterRecord, readingIndex: number) => {
    const reading = record.readings[readingIndex];
    if (!reading) return;
    const key = `${reading.initial || "∅"}|${reading.final}`;
    setSelectedCellKey(key);
    setShowAbout(false);
    requestAnimationFrame(() => document.querySelector<HTMLElement>(`[data-cell-key="${CSS.escape(key)}"]`)?.focus());
  };

  const onCellKeyDown = (event: KeyboardEvent<HTMLButtonElement>, row: number, column: number) => {
    const directions: Record<string, [number, number]> = { ArrowUp: [-1, 0], ArrowDown: [1, 0], ArrowLeft: [0, -1], ArrowRight: [0, 1] };
    const delta = directions[event.key];
    if (!delta) return;
    event.preventDefault();
    const nextRow = Math.max(0, Math.min(INITIALS.length - 1, row + delta[0]));
    const nextColumn = Math.max(0, Math.min(allFinals.length - 1, column + delta[1]));
    matrixRef.current?.querySelector<HTMLElement>(`[data-row="${nextRow}"][data-column="${nextColumn}"]`)?.focus();
  };

  const cycleDensity = (direction: number) => {
    const options: Density[] = ["overview", "examples", "expanded"];
    const next = Math.max(0, Math.min(options.length - 1, options.indexOf(density) + direction));
    setDensity(options[next] ?? density);
  };

  const onMatrixWheel = (event: WheelEvent<HTMLDivElement>) => {
    if (!(event.ctrlKey || event.metaKey)) return;
    event.preventDefault();
    cycleDensity(event.deltaY > 0 ? -1 : 1);
  };

  if (showAbout) {
    return <AboutScreen onBack={() => setShowAbout(false)} />;
  }

  const hskOptions = hskSystem === "hsk2" ? ["1", "2", "3", "4", "5", "6"] : ["1", "2", "3", "4", "5", "6", "7-9"];

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand-block">
          <div className="mark" aria-hidden="true">中文</div>
          <div><h1>Map of Chinese</h1><p>Mandarin syllables, mapped</p></div>
        </div>
        <div className="search-wrap">
          <label htmlFor="global-search">Search hanzi, pinyin, meaning, or HSK level</label>
          <div className="search-box"><span aria-hidden="true">⌕</span><input id="global-search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Try 行, xíng, xing2, travel, hsk3:4" /><kbd>⌘ K</kbd></div>
          {searchResults.length > 0 && <div className="search-results" role="listbox" aria-label="Search results">
            {searchResults.slice(0, 8).map((entry) => {
              const record = characterById.get(entry.character);
              return <button key={entry.character} type="button" role="option" onClick={() => focusCharacter(entry.character)}>
                <span className="search-hanzi">{entry.character}</span>
                <span>{record?.readings.slice(0, 3).map((reading) => reading.pinyinMarked).join(" · ") || "No reading"}</span>
                <small>{record?.definitions[0] || "Open character details"}</small>
              </button>;
            })}
          </div>}
        </div>
        <button type="button" className="about-button" onClick={() => setShowAbout(true)}>Data &amp; About</button>
      </header>

      <section className="control-deck" aria-label="Map filters">
        <div className="control-group wide">
          <span className="control-label">Scope</span>
          <div className="segments">
            <Segment value="basic" current={scope} label="Basic 3500" onSelect={setScope} />
            <Segment value="hsk" current={scope} label="All HSK" onSelect={setScope} />
            <Segment value="all" current={scope} label="All loaded" onSelect={setScope} />
          </div>
        </div>
        <div className="control-group wide">
          <span className="control-label">Tone layer</span>
          <div className="segments tone-segments">
            {toneOptions.map((option) => <Segment key={option.label} value={option.value} current={tone} label={option.short} onSelect={setTone} />)}
          </div>
        </div>
        <div className="control-group hsk-group">
          <span className="control-label">HSK system</span>
          <select aria-label="HSK system" value={hskSystem} onChange={(event) => { setHskSystem(event.target.value as HskSystem); setHskLevels(new Set()); }}>
            <option value="none">None</option><option value="hsk2">Old HSK 2.0</option><option value="hsk3">New HSK 3.0 (2026)</option>
          </select>
          {hskSystem !== "none" && <div className="hsk-options">
            <span className="provenance-label">{hskSystem === "hsk2" ? "Derived from vocabulary lists" : "Official recognition-character list"}</span>
            <div className="level-buttons" aria-label="HSK levels">
              {hskOptions.map((level) => <button type="button" key={level} aria-pressed={hskLevels.has(level)} className={hskLevels.has(level) ? "level active" : "level"} onClick={() => toggleHskLevel(level)}>{level === "7-9" ? "7–9" : level}</button>)}
            </div>
            <label><input type="checkbox" checked={cumulative} onChange={(event) => setCumulative(event.target.checked)} /> Up to highest selected</label>
            <label><input type="checkbox" checked={includeNotHsk} onChange={(event) => setIncludeNotHsk(event.target.checked)} /> Not in this HSK</label>
          </div>}
        </div>
        <div className="control-group zoom-group">
          <span className="control-label">Density</span>
          <div className="zoom-controls">
            <button type="button" aria-label="Decrease map density" onClick={() => cycleDensity(-1)}>−</button>
            <span>{density[0]?.toUpperCase()}{density.slice(1)}</span>
            <button type="button" aria-label="Increase map density" onClick={() => cycleDensity(1)}>+</button>
          </div>
          <small>⌘ / Ctrl + wheel</small>
        </div>
      </section>

      <div className="map-status">
        <p><strong>{totalVisible.toLocaleString()}</strong> characters visible · <strong>{filteredCells.size}</strong> valid syllable cells</p>
        <p className="legend"><span className="density-dot low" /> sparse <span className="density-dot medium" /> medium <span className="density-dot high" /> dense</p>
      </div>
      <div className="sr-only" role="status" aria-live="polite">Filters updated. {totalVisible} characters are visible.</div>

      <main className={selectedCellKey || activeCharacter ? "workspace drawer-open" : "workspace"}>
        <div className={`matrix-scroll density-${density}`} ref={matrixRef} onWheel={onMatrixWheel} aria-label="Pinyin initial and final matrix">
          <div className="matrix-grid" style={{ gridTemplateColumns: `72px repeat(${allFinals.length}, var(--cell-width))` }}>
            <div className="corner sticky-both">Initial<br /><span>Final →</span></div>
            {FINAL_GROUPS.map((group) => group.finals.map((final, index) => <div key={final} className={`column-head sticky-top ${index === 0 ? "family-start" : ""}`} title={final === "i" ? "i; after z, c, s, zh, ch, sh, and r this column represents apical-i" : group.label}><span>{final}</span><small>{index === 0 ? group.label : ""}</small></div>))}
            {INITIALS.map((initial, row) => [
              <div key={`head-${initial || "zero"}`} className="row-head sticky-left"><strong>{initial || "∅"}</strong><small>{initial ? "initial" : "zero"}</small></div>,
              ...allFinals.map((final, column) => {
                const key = cellKey(initial, final);
                const sourceCell = cellByKey.get(key);
                const entries = filteredCells.get(key) ?? [];
                const impossible = !sourceCell;
                const highlighted = highlightedCells.has(key) || entries.some((entry) => highlightedCharacters.has(entry.character));
                const representativeCount = density === "overview" ? 0 : density === "examples" ? 6 : 12;
                const representatives = entries.slice(0, representativeCount);
                const densityLevel = Math.min(1, entries.length / 24);
                const syllable = sourceCell?.baseSyllable ?? `${initial}${displayFinal(final)}`;
                return <button
                  type="button"
                  key={`${initial}-${final}`}
                  className={`matrix-cell ${impossible ? "impossible" : ""} ${highlighted ? "highlighted" : ""} ${selectedCellKey === key ? "selected" : ""}`}
                  aria-label={`${syllable}, ${entries.length} matching characters${impossible ? ", not a standard syllable combination" : ""}`}
                  aria-disabled={impossible}
                  data-row={row}
                  data-column={column}
                  data-cell-key={key}
                  onKeyDown={(event) => onCellKeyDown(event, row, column)}
                  onClick={() => { if (sourceCell) { setSelectedCellKey(key); setSelectedCharacter(null); } }}
                >
                  <span className="cell-top"><b>{sourceCell ? syllable : "·"}</b>{sourceCell && <em>{entries.length}</em>}</span>
                  <span className="density-bar" aria-hidden="true"><i style={{ width: `${Math.max(5, densityLevel * 100)}%` }} /></span>
                  {representatives.length > 0 && <span className="representatives">{representatives.map((entry) => <span key={entry.character} className={highlightedCharacters.has(entry.character) ? "character-hit" : ""}>{entry.character}{density === "expanded" && <small>{entry.tone}</small>}</span>)}</span>}
                  {entries.length > representativeCount && representativeCount > 0 && <span className="more">+{entries.length - representativeCount}</span>}
                </button>;
              }),
            ])}
          </div>

          {cells.some((cell) => cell.final.startsWith("special:")) && <section className="special-group" aria-labelledby="special-title">
            <div><p className="eyebrow">Rare forms</p><h2 id="special-title">Special syllabic readings</h2><p>Valid source readings that do not fit the teaching matrix remain visible here.</p></div>
            <div className="special-cells">{cells.filter((cell) => cell.final.startsWith("special:")).map((cell) => {
              const entries = filteredCells.get(cell.key) ?? [];
              return <button type="button" key={cell.key} onClick={() => setSelectedCellKey(cell.key)}><strong>{displayFinal(cell.final)}</strong><span>{entries.slice(0, 5).map((entry) => entry.character).join(" ") || "—"}</span><small>{entries.length} characters</small></button>;
            })}</div>
          </section>}
        </div>

        {(activeCell || activeCharacter) && <DetailsPanel
          cell={activeCell}
          entries={activeCellEntries}
          record={activeCharacter}
          onClose={() => { setSelectedCharacter(null); setSelectedCellKey(null); }}
          onCharacter={setSelectedCharacter}
          onReading={navigateToReading}
        />}
      </main>
    </div>
  );
}

function DetailsPanel({ cell, entries, record, onClose, onCharacter, onReading }: {
  cell: SyllableCell | null;
  entries: SyllableCell["entries"];
  record: CharacterRecord | null;
  onClose: () => void;
  onCharacter: (character: string | null) => void;
  onReading: (record: CharacterRecord, index: number) => void;
}) {
  return <aside className="details-panel" aria-label="Character details">
    <div className="drawer-handle" aria-hidden="true" />
    <button type="button" className="close-button" aria-label="Close details" onClick={onClose}>×</button>
    {record ? <>
      <button type="button" className="back-to-cell" onClick={() => onCharacter(null)}>← Back to {cell?.baseSyllable ?? "cell"}</button>
      <div className="character-hero"><span>{record.character}</span><div><p>{record.definitions[0] || "Definition unavailable"}</p><code>{record.codePoint}</code></div></div>
      <div className="badge-row">
        {record.inBasic3500 ? <Badge>Basic rank {record.standardRank}</Badge> : <Badge muted>Outside basic 3500</Badge>}
        {record.hsk2Level && <Badge>Old HSK {record.hsk2Level} · derived</Badge>}
        {record.hsk3_2026Level && <Badge>HSK 2026 {record.hsk3_2026Level}</Badge>}
      </div>
      {(record.traditional.length > 0 || record.simplified !== record.character) && <dl className="forms"><div><dt>Simplified</dt><dd>{record.simplified}</dd></div><div><dt>Traditional</dt><dd>{record.traditional.join(" · ") || "—"}</dd></div></dl>}
      <section className="drawer-section"><h3>Readings</h3><div className="reading-list">
        {record.readings.map((reading, index) => <button type="button" key={reading.pinyinNumbered} onClick={() => onReading(record, index)}>
          <strong>{reading.pinyinMarked}</strong><span>{reading.initial || "∅"} + {displayFinal(reading.final)}</span><Badge muted>Tone {reading.tone}</Badge>{reading.preferred && <Badge>Preferred</Badge>}<small>{reading.sources.join(" · ")}</small>
        </button>)}
      </div></section>
      {record.definitions.length > 0 && <section className="drawer-section"><h3>Definitions</h3><ul>{record.definitions.map((definition) => <li key={definition}>{definition}</li>)}</ul></section>}
      {record.exampleWords.length > 0 && <section className="drawer-section"><h3>Example words</h3><div className="example-list">{record.exampleWords.map((word) => <div key={word.simplified}><strong>{word.simplified}</strong><span>{word.pinyin}</span><small>{word.definition}</small></div>)}</div></section>}
      {record.hsk2Level && <section className="drawer-section source-note"><h3>Old HSK evidence</h3><p>Character level is derived from its earliest appearance in the 2015 vocabulary lists.</p><p>{record.hsk2EvidenceWords.join(" · ")}</p></section>}
    </> : <>
      <p className="eyebrow">Syllable cell</p><h2>{cell?.baseSyllable}</h2><p className="cell-description"><strong>{entries.length}</strong> matching characters in the current layers.</p>
      <div className="drawer-character-grid">{entries.map((entry) => {
        const item = characterById.get(entry.character);
        return <button type="button" key={entry.character} onClick={() => onCharacter(entry.character)}><strong>{entry.character}</strong><span>{entry.pinyinMarked}</span><small>{item?.definitions[0] || "Open details"}</small></button>;
      })}</div>
    </>}
  </aside>;
}

function AboutScreen({ onBack }: { onBack: () => void }) {
  const counts = manifest.counts;
  return <div className="about-page">
    <header><button type="button" onClick={onBack}>← Back to map</button><span>Map of Chinese / Data &amp; About</span></header>
    <main>
      <p className="eyebrow">A phonetic atlas</p><h1>How this map is made</h1>
      <p className="lede">Map of Chinese places Mandarin characters in a stable initial × final matrix, with tones as switchable reading layers. Character membership and pronunciation remain separate, so polyphonic characters can inhabit more than one place.</p>
      <section className="stat-grid"><div><strong>{counts.basic3500.toLocaleString()}</strong><span>official first-tier characters</span></div><div><strong>{counts.readingRecords.toLocaleString()}</strong><span>normalized reading records</span></div><div><strong>{counts.syllableCells}</strong><span>populated syllable cells</span></div><div><strong>17.0</strong><span>Unicode / Unihan version</span></div></section>
      <section><h2>Source hierarchy</h2><div className="source-cards">
        <article><span>01</span><div><h3>通用规范汉字表</h3><p>Ministry of Education / State Language Commission, 2013. The official ordering supplies ranks 1–3,500.</p></div></article>
        <article><span>02</span><div><h3>Unicode Unihan 17.0</h3><p><code>kHanyuPinyin</code> supplies dictionary readings; <code>kMandarin</code> identifies customary readings. Variant properties supply script mappings.</p></div></article>
        <article><span>03</span><div><h3>HSK 2.0 / 2015</h3><p>Character levels are derived from the earliest vocabulary-list occurrence and retain evidence words. They are not an official character classification.</p></div></article>
        <article><span>04</span><div><h3>hsk3_2026</h3><p>Direct recognition-character lists from the November 2025 syllabus, represented incrementally with one combined 7–9 band.</p></div></article>
      </div></section>
      <section className="reconciliation"><p className="eyebrow">Open reconciliation</p><h2>Count-contract discrepancy</h2><p>{manifest.reconciliation.issue}</p><p>{manifest.reconciliation.policy}</p><p><strong>{manifest.reconciliation.unreconciledLevel6Count}</strong> level-6 source records remain named in the validation report for review.</p></section>
      <section><h2>Licensing and provenance</h2><p>Unicode data is used under the Unicode Terms of Use. CC-CEDICT is an optional enrichment source and, when included, requires CC BY-SA 4.0 attribution. This snapshot currently uses Unihan definitions and HSK evidence examples; see <code>NOTICE.md</code> and <code>data/sources/README.md</code> for checksums and detailed notices.</p></section>
    </main>
  </div>;
}

export default App;

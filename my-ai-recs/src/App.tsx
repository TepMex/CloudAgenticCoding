import { useEffect, useMemo, useRef, useState } from 'react'
import {
  SHEET_PAGE_URL,
  SheetError,
  groupEntries,
  loadCatalog,
  otherSectionMatches,
  queryCount,
  ruCount,
  visibleEntries,
  type Entry,
  type Section,
} from './catalog'

type Phase = 'loading' | 'ready' | 'error'

export default function App() {
  const [phase, setPhase] = useState<Phase>('loading')
  const [sections, setSections] = useState<Section[]>([])
  const [error, setError] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  const [nonce, setNonce] = useState(0)
  const [query, setQuery] = useState('')
  const [sectionId, setSectionId] = useState(() => window.location.hash.replace(/^#/, ''))
  const [facet, setFacet] = useState('')
  const [status, setStatus] = useState('')
  const loaded = useRef(false)

  useEffect(() => {
    function syncHash() {
      setSectionId(window.location.hash.replace(/^#/, ''))
    }
    window.addEventListener('hashchange', syncHash)
    window.addEventListener('popstate', syncHash)
    return () => {
      window.removeEventListener('hashchange', syncHash)
      window.removeEventListener('popstate', syncHash)
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    setRefreshing(true)
    if (!loaded.current) setPhase('loading')
    loadCatalog()
      .then((list) => {
        if (cancelled) return
        loaded.current = true
        setSections(list)
        setError(null)
        setPhase('ready')
      })
      .catch((caught) => {
        if (cancelled) return
        const message = caught instanceof SheetError ? caught.message : 'Не удалось прочитать таблицу.'
        setError(message)
        setPhase(loaded.current ? 'ready' : 'error')
      })
      .finally(() => {
        if (!cancelled) setRefreshing(false)
      })
    return () => {
      cancelled = true
    }
  }, [nonce])

  if (phase === 'loading') {
    return (
      <main className="gate">
        <div className="gate-card" aria-busy="true">
          <p className="kicker">подборка</p>
          <h1>Мои рекомендации</h1>
          <p className="range">Открываю таблицу…</p>
        </div>
      </main>
    )
  }

  if (phase === 'error') {
    return (
      <main className="gate">
        <div className="gate-card">
          <p className="kicker">подборка</p>
          <h1>Мои рекомендации</h1>
          <p className="gate-error" role="alert">
            {error}
          </p>
          <button type="button" className="primary" onClick={() => setNonce((value) => value + 1)}>
            Повторить
          </button>
        </div>
      </main>
    )
  }

  return (
    <Catalog
      sections={sections}
      query={query}
      sectionId={sectionId}
      facet={facet}
      status={status}
      refreshing={refreshing}
      error={error}
      onQuery={setQuery}
      onSection={setSectionId}
      onFacet={setFacet}
      onStatus={setStatus}
      onRefresh={() => setNonce((value) => value + 1)}
    />
  )
}

function Catalog({
  sections,
  query,
  sectionId,
  facet,
  status,
  refreshing,
  error,
  onQuery,
  onSection,
  onFacet,
  onStatus,
  onRefresh,
}: {
  sections: Section[]
  query: string
  sectionId: string
  facet: string
  status: string
  refreshing: boolean
  error: string | null
  onQuery: (query: string) => void
  onSection: (id: string) => void
  onFacet: (facet: string) => void
  onStatus: (status: string) => void
  onRefresh: () => void
}) {
  const active = sections.find((section) => section.id === sectionId) ?? sections[0]
  const shown = useMemo(
    () => (active ? visibleEntries(active, query, facet, status) : []),
    [active, query, facet, status],
  )
  const groups = useMemo(() => groupEntries(shown, active?.facetLabel ?? null), [shown, active])
  const others = useMemo(
    () => (active ? otherSectionMatches(sections, active.id, query) : []),
    [sections, active, query],
  )
  const total = sections.reduce((sum, section) => sum + section.entries.length, 0)

  useEffect(() => {
    if (!active) return
    revealInIndex(active.id)
  }, [active])

  useEffect(() => {
    if (!active) return
    if (facet && !active.facets.some((item) => item.value === facet)) onFacet('')
    if (status && !active.statuses.some((item) => item.value === status)) onStatus('')
  }, [active, facet, status, onFacet, onStatus])

  function select(id: string) {
    onSection(id)
    onFacet('')
    onStatus('')
    const next = `#${id}`
    if (window.location.hash !== next) history.pushState(null, '', next)
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    window.scrollTo({ top: 0, behavior: reduce ? 'auto' : 'smooth' })
  }

  if (!active) return null

  return (
    <main className="journal">
      <header className="mast">
        <div>
          <p className="kicker">подборка</p>
          <h1>Мои рекомендации</h1>
          <p className="range">
            {ruCount(sections.length, 'раздел', 'раздела', 'разделов')} · {ruCount(total, 'запись', 'записи', 'записей')}
          </p>
        </div>
        <button type="button" className="ghost" onClick={onRefresh} disabled={refreshing}>
          {refreshing ? 'Обновляю…' : 'Обновить'}
        </button>
      </header>
      {error ? (
        <p className="banner" role="alert">
          {error}
        </p>
      ) : null}
      <div className="layout">
        <aside className="rail">
          <label className="find">
            <span>Найти</span>
            <input
              type="search"
              value={query}
              placeholder="название, автор, тема"
              onChange={(event) => onQuery(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Escape') onQuery('')
              }}
              autoCapitalize="off"
              autoCorrect="off"
              spellCheck={false}
              enterKeyHint="search"
            />
          </label>
          <nav className="sections" aria-label="Разделы">
            {sections.map((section) => {
              const count = queryCount(section, query)
              const current = section.id === active.id
              return (
                <button
                  key={section.id}
                  type="button"
                  data-id={section.id}
                  className={['section-link', current ? 'on' : '', query.trim() && count === 0 ? 'quiet' : '']
                    .filter(Boolean)
                    .join(' ')}
                  aria-current={current ? 'true' : undefined}
                  onClick={() => select(section.id)}
                >
                  <span className="section-name">{section.name}</span>
                  <span className="section-count">{count}</span>
                </button>
              )
            })}
          </nav>
        </aside>
        <div className="reading" aria-busy={refreshing}>
          <header className="section-head">
            <h2>{active.name}</h2>
            <p>{countLine(shown.length, active.entries.length)}</p>
          </header>
          {active.facetLabel && active.facets.length > 1 ? (
            <FilterRow
              label={active.facetLabel}
              value={facet}
              options={active.facets}
              onChange={onFacet}
            />
          ) : null}
          {active.statuses.length > 1 ? (
            <FilterRow label="Статус" value={status} options={active.statuses} onChange={onStatus} />
          ) : null}
          {others.length > 0 ? (
            <p className="elsewhere">
              <span>Ещё в других разделах</span>
              {others.map((item) => (
                <button key={item.id} type="button" onClick={() => select(item.id)}>
                  {item.name} · {item.count}
                </button>
              ))}
            </p>
          ) : null}
          {shown.length === 0 ? <p className="empty">Ничего не нашлось.</p> : null}
          {groups.map((group) => (
            <div key={group.label || 'all'} className="group">
              {group.label ? (
                <p className="group-title">
                  <span>{group.label}</span>
                  <span className="group-count">{group.entries.length}</span>
                </p>
              ) : null}
              <div className="cards">
                {group.entries.map((entry) => (
                  <Card key={entry.id} entry={entry} />
                ))}
              </div>
            </div>
          ))}
          <p className="colophon">
            <a href={SHEET_PAGE_URL} target="_blank" rel="noreferrer">
              Таблица рекомендаций
            </a>
          </p>
        </div>
      </div>
    </main>
  )
}

function FilterRow({
  label,
  value,
  options,
  onChange,
}: {
  label: string
  value: string
  options: { value: string; count: number }[]
  onChange: (value: string) => void
}) {
  return (
    <div className="filters" role="group" aria-label={label}>
      <button type="button" aria-pressed={value === ''} onClick={() => onChange('')}>
        Все
      </button>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          aria-pressed={value === option.value}
          onClick={() => onChange(value === option.value ? '' : option.value)}
        >
          {option.value}
          <span>{option.count}</span>
        </button>
      ))}
    </div>
  )
}

function Card({ entry }: { entry: Entry }) {
  const marked = entry.chips.some((chip) => chip.tone === 'status')
  return (
    <article className={marked ? 'card is-marked' : 'card'}>
      {entry.chips.length > 0 ? (
        <p className="chips">
          {entry.chips.map((chip, index) => (
            <span key={`${chip.tone}-${index}`} className={`chip ${chip.tone}`}>
              {chip.label}
            </span>
          ))}
        </p>
      ) : null}
      <h3>{entry.href ? <a href={entry.href} target="_blank" rel="noreferrer">{entry.title}</a> : entry.title}</h3>
      {entry.original ? <p className="original">{entry.original}</p> : null}
      {entry.person ? <p className="person">{entry.person}</p> : null}
      {entry.lead ? <p className="lead">{entry.lead}</p> : null}
      {entry.notes.map((note, index) => (
        <p key={`${note.label}-${index}`} className="note">
          <span>{note.label}</span>
          {note.text}
        </p>
      ))}
      {entry.comment ? <blockquote>{entry.comment}</blockquote> : null}
      {entry.footer.length > 0 ? (
        <p className="card-foot">
          {entry.footer.map((item) =>
            item.href ? (
              <a key={item.label} href={item.href} target="_blank" rel="noreferrer">
                {item.value}
              </a>
            ) : (
              <span key={item.label}>
                <em>{item.label}</em> {item.value}
              </span>
            ),
          )}
        </p>
      ) : null}
    </article>
  )
}

function countLine(shown: number, total: number): string {
  if (shown === total) return ruCount(total, 'запись', 'записи', 'записей')
  return `${ruCount(shown, 'запись', 'записи', 'записей')} из ${total}`
}

function revealInIndex(id: string) {
  const button = document.querySelector<HTMLElement>(`.section-link[data-id="${CSS.escape(id)}"]`)
  const scroller = button?.parentElement
  if (!button || !scroller) return
  const item = button.getBoundingClientRect()
  const frame = scroller.getBoundingClientRect()
  if (item.top < frame.top) scroller.scrollTop -= frame.top - item.top
  else if (item.bottom > frame.bottom) scroller.scrollTop += item.bottom - frame.bottom
  if (item.left < frame.left) scroller.scrollLeft -= frame.left - item.left
  else if (item.right > frame.right) scroller.scrollLeft += item.right - frame.right
}

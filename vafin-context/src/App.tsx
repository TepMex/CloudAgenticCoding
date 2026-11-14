import { useEffect, useMemo, useRef, useState } from 'react'
import {
  SHEET_PAGE_URL,
  SheetError,
  loadEpisodes,
  ruCount,
  selectEpisodes,
  type Episode,
  type ListMode,
} from './episodes'

type Phase = 'loading' | 'ready' | 'error'

export default function App() {
  const [phase, setPhase] = useState<Phase>('loading')
  const [episodes, setEpisodes] = useState<Episode[]>([])
  const [error, setError] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  const [nonce, setNonce] = useState(0)
  const [query, setQuery] = useState('')
  const [mode, setMode] = useState<ListMode>('all')
  const [activeId, setActiveId] = useState<string | null>(null)
  const loaded = useRef(false)

  useEffect(() => {
    let cancelled = false
    setRefreshing(true)
    if (!loaded.current) setPhase('loading')
    loadEpisodes()
      .then((list) => {
        if (cancelled) return
        loaded.current = true
        setEpisodes(list)
        setError(null)
        setActiveId((current) => current ?? list[0]?.id ?? null)
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
          <p className="kicker">подкаст</p>
          <h1>Говорит Вафин</h1>
          <p className="range">Открываю таблицу…</p>
        </div>
      </main>
    )
  }

  if (phase === 'error') {
    return (
      <main className="gate">
        <div className="gate-card">
          <p className="kicker">подкаст</p>
          <h1>Говорит Вафин</h1>
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
    <Journal
      episodes={episodes}
      query={query}
      mode={mode}
      activeId={activeId}
      refreshing={refreshing}
      error={error}
      onQuery={setQuery}
      onMode={setMode}
      onActive={setActiveId}
      onRefresh={() => setNonce((value) => value + 1)}
    />
  )
}

function Journal({
  episodes,
  query,
  mode,
  activeId,
  refreshing,
  error,
  onQuery,
  onMode,
  onActive,
  onRefresh,
}: {
  episodes: Episode[]
  query: string
  mode: ListMode
  activeId: string | null
  refreshing: boolean
  error: string | null
  onQuery: (query: string) => void
  onMode: (mode: ListMode) => void
  onActive: (id: string) => void
  onRefresh: () => void
}) {
  const visible = useMemo(() => selectEpisodes(episodes, query, mode), [episodes, query, mode])
  const withContext = episodes.filter((episode) => episode.paragraphs.length > 0).length

  useEffect(() => {
    if (visible.length === 0) return
    if (visible.some((episode) => episode.id === activeId)) return
    onActive(visible[0].id)
  }, [visible, activeId, onActive])

  useEffect(() => {
    const nodes = visible
      .map((episode) => document.getElementById(episode.id))
      .filter((node): node is HTMLElement => node !== null)
    if (nodes.length === 0) return
    const observer = new IntersectionObserver(
      (entries) => {
        const hit = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0]
        if (hit) onActive(hit.target.id)
      },
      { rootMargin: '-12% 0px -55% 0px', threshold: [0.1, 0.25, 0.5] },
    )
    for (const node of nodes) observer.observe(node)
    return () => observer.disconnect()
  }, [visible, onActive])

  useEffect(() => {
    if (!activeId) return
    revealInIndex(activeId)
  }, [activeId])

  function select(id: string) {
    onActive(id)
    const node = document.getElementById(id)
    if (!node) return
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const narrow = window.matchMedia('(max-width: 860px)').matches
    const strip = narrow ? document.querySelector('.rail') : null
    const cover = strip instanceof HTMLElement ? strip.getBoundingClientRect().height + 12 : 0
    const top = node.getBoundingClientRect().top + window.scrollY - cover
    window.scrollTo({ top: Math.max(0, top), behavior: reduce ? 'auto' : 'smooth' })
  }

  return (
    <main className="journal">
      <header className="mast">
        <div>
          <p className="kicker">подкаст</p>
          <h1>Говорит Вафин</h1>
          <p className="range">
            Личные контексты · {ruCount(episodes.length, 'выпуск', 'выпуска', 'выпусков')} · {withContext} с контекстом
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
        <div className="rail">
          <label className="find">
            <span>Найти</span>
            <input
              type="search"
              value={query}
              placeholder="номер, название, воспоминание"
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
          <div className="modes" role="group" aria-label="Какие выпуски показывать">
            <button type="button" aria-pressed={mode === 'all'} onClick={() => onMode('all')}>
              Все
            </button>
            <button type="button" aria-pressed={mode === 'with-context'} onClick={() => onMode('with-context')}>
              С контекстом
            </button>
          </div>
          {visible.length > 0 && visible.length !== episodes.length ? (
            <p className="found">{ruCount(visible.length, 'выпуск', 'выпуска', 'выпусков')}</p>
          ) : null}
          <nav className="days" aria-label="Выпуски">
            {visible.map((episode) => (
              <button
                key={episode.id}
                type="button"
                data-id={episode.id}
                className={[
                  'day-link',
                  episode.id === activeId ? 'on' : '',
                  episode.paragraphs.length === 0 ? 'quiet' : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                aria-current={episode.id === activeId ? 'true' : undefined}
                onClick={() => select(episode.id)}
              >
                <span className="day-link-date">{episode.number}</span>
                <span className="day-link-place">{episode.title}</span>
              </button>
            ))}
          </nav>
        </div>
        <div className="reading">
          {visible.length === 0 ? <p className="empty">Ничего не нашлось.</p> : null}
          {visible.map((episode) => (
            <section key={episode.id} id={episode.id} className={episode.paragraphs.length === 0 ? 'day is-empty' : 'day'}>
              <p className="day-date">{episode.number}</p>
              <h2>{episode.title}</h2>
              {episode.paragraphs.length === 0 ? (
                <p className="gap">без контекста</p>
              ) : (
                episode.paragraphs.map((paragraph, index) => <p key={index}>{paragraph}</p>)
              )}
            </section>
          ))}
          <p className="colophon">
            <a href={SHEET_PAGE_URL} target="_blank" rel="noreferrer">
              Таблица выпусков
            </a>
          </p>
        </div>
      </div>
    </main>
  )
}

function revealInIndex(id: string) {
  const button = document.querySelector<HTMLElement>(`.day-link[data-id="${CSS.escape(id)}"]`)
  const scroller = button?.parentElement
  if (!button || !scroller) return
  const item = button.getBoundingClientRect()
  const frame = scroller.getBoundingClientRect()
  if (item.top < frame.top) scroller.scrollTop -= frame.top - item.top
  else if (item.bottom > frame.bottom) scroller.scrollTop += item.bottom - frame.bottom
  if (item.left < frame.left) scroller.scrollLeft -= frame.left - item.left
  else if (item.right > frame.right) scroller.scrollLeft += item.right - frame.right
}

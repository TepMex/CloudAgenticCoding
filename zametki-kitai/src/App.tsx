import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { journalAssetUrl } from './asset'
import { UnlockError, openSealed } from './codec'
import type { Journal } from './types'

type Phase = 'locked' | 'opening' | 'open'

type ViewBlock = { t: 'p'; text: string } | { t: 'img'; bytes: Uint8Array }

export default function App() {
  const [password, setPassword] = useState('')
  const [phase, setPhase] = useState<Phase>('locked')
  const [error, setError] = useState<string | null>(null)
  const [journal, setJournal] = useState<Journal | null>(null)
  const [images, setImages] = useState<Uint8Array | null>(null)
  const [activeId, setActiveId] = useState<string | null>(null)

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPhase('opening')
    setError(null)
    try {
      const response = await fetch(journalAssetUrl())
      if (!response.ok) throw new Error('load')
      const bytes = new Uint8Array(await response.arrayBuffer())
      const opened = await openSealed(bytes, password)
      setJournal(opened.journal)
      setImages(opened.images)
      setPassword('')
      setActiveId(opened.journal.days[0]?.id ?? null)
      setPhase('open')
    } catch (caught) {
      setJournal(null)
      setImages(null)
      setPhase('locked')
      setError(caught instanceof UnlockError ? 'Неверный пароль' : 'Не удалось открыть заметки')
    }
  }

  function close() {
    setJournal(null)
    setImages(null)
    setPassword('')
    setError(null)
    setActiveId(null)
    setPhase('locked')
  }

  if (phase === 'open' && journal && images) {
    return (
      <JournalView
        journal={journal}
        images={images}
        activeId={activeId}
        onActive={setActiveId}
        onClose={close}
      />
    )
  }

  return (
    <main className="gate">
      <form className="gate-card" onSubmit={onSubmit} aria-busy={phase === 'opening'}>
        <p className="kicker">сентябрь 2026</p>
        <h1>Путевые заметки</h1>
        <label className="field">
          <span>Пароль</span>
          <input
            type="password"
            name="password"
            value={password}
            onChange={(event) => {
              setPassword(event.target.value)
              if (error) setError(null)
            }}
            autoFocus
            autoComplete="current-password"
            autoCapitalize="off"
            autoCorrect="off"
            spellCheck={false}
            required
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? 'gate-error' : undefined}
          />
        </label>
        {error ? (
          <p id="gate-error" className="gate-error" role="alert">
            {error}
          </p>
        ) : null}
        <button type="submit" className="primary" disabled={phase === 'opening' || password.length === 0}>
          {phase === 'opening' ? 'Открываю…' : 'Открыть'}
        </button>
      </form>
    </main>
  )
}

function JournalView({
  journal,
  images,
  activeId,
  onActive,
  onClose,
}: {
  journal: Journal
  images: Uint8Array
  activeId: string | null
  onActive: (id: string) => void
  onClose: () => void
}) {
  const days = useMemo(() => materialize(journal, images), [journal, images])

  useEffect(() => {
    const nodes = days
      .map((day) => document.getElementById(day.id))
      .filter((node): node is HTMLElement => node !== null)
    if (nodes.length === 0) return
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0]
        if (visible) onActive(visible.target.id)
      },
      { rootMargin: '-12% 0px -55% 0px', threshold: [0.1, 0.25, 0.5] },
    )
    for (const node of nodes) observer.observe(node)
    return () => observer.disconnect()
  }, [days, onActive])

  function select(id: string) {
    onActive(id)
    const node = document.getElementById(id)
    if (!node) return
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const narrow = window.matchMedia('(max-width: 860px)').matches
    const strip = narrow ? document.querySelector('.days') : null
    const cover = strip instanceof HTMLElement ? strip.getBoundingClientRect().height + 20 : 0
    const top = node.getBoundingClientRect().top + window.scrollY - cover
    window.scrollTo({ top: Math.max(0, top), behavior: reduce ? 'auto' : 'smooth' })
  }

  return (
    <main className="journal">
      <header className="mast">
        <div>
          <p className="kicker">{journal.kicker}</p>
          <h1>{journal.title}</h1>
          <p className="range">{journal.range}</p>
        </div>
        <button type="button" className="ghost" onClick={onClose}>
          Закрыть
        </button>
      </header>
      <div className="layout">
        <nav className="days" aria-label="Дни поездки">
          {days.map((day) => (
            <button
              key={day.id}
              type="button"
              className={day.id === activeId ? 'day-link on' : 'day-link'}
              aria-current={day.id === activeId ? 'true' : undefined}
              onClick={() => select(day.id)}
            >
              <span className="day-link-date">{day.navDate}</span>
              <span className="day-link-place">{day.navPlace}</span>
            </button>
          ))}
        </nav>
        <div className="reading">
          {days.map((day) => (
            <section key={day.id} id={day.id} className="day" aria-labelledby={`${day.id}-title`}>
              <p className="day-date">{day.heading}</p>
              <h2 id={`${day.id}-title`}>{day.place}</h2>
              {day.blocks.map((block, index) =>
                block.t === 'p' ? (
                  <p key={index}>{block.text}</p>
                ) : (
                  <Photo key={index} bytes={block.bytes} />
                ),
              )}
            </section>
          ))}
        </div>
      </div>
    </main>
  )
}

function Photo({ bytes }: { bytes: Uint8Array }) {
  const [url, setUrl] = useState<string | null>(null)
  useEffect(() => {
    const copy = new ArrayBuffer(bytes.byteLength)
    new Uint8Array(copy).set(bytes)
    const next = URL.createObjectURL(new Blob([copy], { type: 'image/jpeg' }))
    setUrl(next)
    return () => URL.revokeObjectURL(next)
  }, [bytes])
  if (!url) return null
  return (
    <figure className="photo">
      <img src={url} alt="Фотография" />
    </figure>
  )
}

function materialize(journal: Journal, images: Uint8Array): Array<Omit<Journal['days'][number], 'blocks'> & { blocks: ViewBlock[] }> {
  return journal.days.map((day) => ({
    ...day,
    blocks: day.blocks.map((block) => {
      if (block.t === 'p') return block
      return { t: 'img' as const, bytes: images.slice(block.o, block.o + block.n) }
    }),
  }))
}

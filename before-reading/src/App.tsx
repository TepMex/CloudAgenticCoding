import { useEffect, useRef, useState } from 'react'
import { buildCards, gradeQueue, shuffleCards, type Card, type Grade } from './cards'
import { ankiStatusLabel, ruCards, ruWords, unitTypeLabel } from './labels'
import { examplePinyin } from './phrase-pinyin'
import { canSpeak, speakChinese, speakSequence, speechLines, stopSpeaking } from './speak'
import { readCachedEntries, resolveWorkbookUrl, sameEntries, workbookHtmlUrl, writeCachedEntries } from './source'
import {
  entriesForFragment,
  fragmentChoices,
  loadEntries,
  SHEET_NAME,
  visibleExample,
  vocabErrorText,
  type VocabEntry,
} from './vocab'

type Session = {
  queue: Card[]
  known: number
  total: number
  againCount: number
}

type Pick = { kind: 'pending' } | { kind: 'ready'; fragmentId: string | null }

export function App() {
  const url = resolveWorkbookUrl(window.location.search)
  const tableUrl = workbookHtmlUrl(url)
  const [entries, setEntries] = useState<VocabEntry[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [banner, setBanner] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [pick, setPick] = useState<Pick>({ kind: 'pending' })
  const [session, setSession] = useState<Session | null>(null)
  const [revealed, setRevealed] = useState(false)
  const [confirm, setConfirm] = useState<'reset' | 'reload' | null>(null)
  const [speech, setSpeech] = useState(canSpeak)
  const entriesRef = useRef<VocabEntry[] | null>(null)
  const sessionRef = useRef<Session | null>(null)
  entriesRef.current = entries
  sessionRef.current = session

  function showEntries(next: VocabEntry[], announce: boolean) {
    const current = entriesRef.current
    if (current && sameEntries(current, next)) return
    const progressed = (sessionRef.current?.known ?? 0) + (sessionRef.current?.againCount ?? 0) > 0
    if (current && progressed) {
      setBanner('В таблице новый список. «Обновить», чтобы перейти на него.')
      return
    }
    entriesRef.current = next
    setEntries(next)
    setError(null)
    setBanner(announce && current ? 'Список слов обновился.' : null)
    openEntries(next, setPick, setSession, setRevealed)
  }

  useEffect(() => {
    const cached = readCachedEntries(window.localStorage, url)
    if (cached) {
      showEntries(cached, false)
      setLoading(false)
    }

    const controller = new AbortController()
    loadEntries(url, controller.signal)
      .then((next) => {
        writeCachedEntries(window.localStorage, url, next)
        showEntries(next, true)
      })
      .catch((reason: unknown) => {
        if (isAbort(reason)) return
        if (entriesRef.current) setBanner('Не удалось обновить, показан сохранённый список.')
        else setError(vocabErrorText(reason))
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [url])

  useEffect(() => {
    if (!canSpeak()) return
    const update = () => setSpeech(canSpeak())
    window.speechSynthesis.addEventListener('voiceschanged', update)
    return () => window.speechSynthesis.removeEventListener('voiceschanged', update)
  }, [])

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.repeat || event.metaKey || event.ctrlKey || event.altKey) return
      const card = session?.queue[0]
      if (!card) return
      if ((event.key === ' ' || event.key === 'Enter') && !revealed) {
        event.preventDefault()
        reveal()
      }
      if (!revealed) return
      if (event.key === '1') grade('again')
      if (event.key === '2' || event.key === '3') grade('good')
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  function reveal() {
    if (revealed) return
    const card = session?.queue[0]
    setRevealed(true)
    if (!card) return
    speakSequence(speechLines(card.entry.expression, visibleExample(card.entry)))
  }

  function grade(verdict: Grade) {
    stopSpeaking()
    setRevealed(false)
    setConfirm(null)
    setSession((current) => {
      if (!current) return current
      const queue = gradeQueue(current.queue, verdict)
      return {
        queue,
        known: current.known + (verdict === 'good' ? 1 : 0),
        total: current.total,
        againCount: current.againCount + (verdict === 'again' ? 1 : 0),
      }
    })
  }

  function restart() {
    if (!entries) return
    const fragmentId = pick.kind === 'ready' ? pick.fragmentId : null
    setSession(startSession(entriesForFragment(entries, fragmentId)))
    setRevealed(false)
    setConfirm(null)
    setBanner(null)
    stopSpeaking()
  }

  async function reload() {
    setConfirm(null)
    setLoading(true)
    try {
      const next = await loadEntries(url)
      writeCachedEntries(window.localStorage, url, next)
      entriesRef.current = next
      setEntries(next)
      setError(null)
      setBanner(null)
      openEntries(next, setPick, setSession, setRevealed)
    } catch (reason) {
      setBanner(vocabErrorText(reason))
    } finally {
      setLoading(false)
    }
  }

  const choices = entries ? fragmentChoices(entries) : []
  const card = session?.queue[0]
  const finished = session !== null && session.queue.length === 0 && session.total > 0
  const showPicker = entries !== null && entries.length > 0 && choices.length > 1 && pick.kind === 'pending'

  return (
    <div className="app">
      {finished && session ? (
        <DoneScreen session={session} tableUrl={tableUrl} onAgain={restart} />
      ) : card && session ? (
        <Study
          card={card}
          session={session}
          revealed={revealed}
          confirm={confirm}
          banner={banner}
          speech={speech}
          showFragment={choices.length > 1 && (pick.kind !== 'ready' || pick.fragmentId === null)}
          onReveal={reveal}
          onGrade={grade}
          onConfirm={setConfirm}
          onRestart={restart}
          onReload={() => void reload()}
        />
      ) : (
        <Gate
          loading={loading && !entries}
          error={error}
          entries={entries}
          showPicker={showPicker}
          choices={choices}
          tableUrl={tableUrl}
          banner={banner}
          onPick={(fragmentId) => {
            if (!entries) return
            setPick({ kind: 'ready', fragmentId })
            setSession(startSession(entriesForFragment(entries, fragmentId)))
            setRevealed(false)
          }}
          onReload={() => void reload()}
        />
      )}
    </div>
  )
}

function Study({
  card,
  session,
  revealed,
  confirm,
  banner,
  speech,
  showFragment,
  onReveal,
  onGrade,
  onConfirm,
  onRestart,
  onReload,
}: {
  card: Card
  session: Session
  revealed: boolean
  confirm: 'reset' | 'reload' | null
  banner: string | null
  speech: boolean
  showFragment: boolean
  onReveal: () => void
  onGrade: (grade: Grade) => void
  onConfirm: (value: 'reset' | 'reload' | null) => void
  onRestart: () => void
  onReload: () => void
}) {
  const example = visibleExample(card.entry)
  const showExample = card.direction === 'expression' && example.length > 0
  const progress = session.total === 0 ? 0 : (session.known / session.total) * 100
  const long = card.prompt.length > 8 || card.prompt.includes(' ')

  return (
    <>
      <header className="top">
        <div aria-label={`знаю ${session.known} из ${session.total}`}>
          {session.known} / {session.total}
        </div>
        {confirm ? (
          <div className="confirm">
            <span>{confirm === 'reset' ? 'Заново?' : 'Обновить?'}</span>
            <button type="button" onClick={confirm === 'reset' ? onRestart : onReload}>
              Да
            </button>
            <button type="button" onClick={() => onConfirm(null)}>
              Нет
            </button>
          </div>
        ) : (
          <div className="top-actions">
            <button type="button" onClick={() => onConfirm('reset')}>
              Заново
            </button>
            <button type="button" onClick={() => onConfirm('reload')}>
              Обновить
            </button>
          </div>
        )}
      </header>
      <p className="direction">{card.direction === 'expression' ? 'иероглиф → значение' : 'значение → иероглиф'}</p>
      <div className="meter" aria-hidden="true">
        <span style={{ width: `${progress}%` }} />
      </div>
      {banner ? <p className="banner" role="status">{banner}</p> : null}
      <main className="stage">
        {revealed ? (
          <Back card={card} example={example} showFragment={showFragment} />
        ) : (
          <div className="front-body">
            <h1 className={long ? 'prompt long' : 'prompt'} lang={card.direction === 'expression' ? 'zh-CN' : 'ru'}>
              {card.prompt}
            </h1>
            {showExample ? (
              <>
                <hr className="rule" />
                <p className="example" lang="zh-CN">
                  {example}
                </p>
              </>
            ) : null}
          </div>
        )}
      </main>
      <div className="dock">
        {revealed ? (
          <>
            {speech ? <PlayButtons card={card} example={example} /> : null}
            <div className="grades">
              <button type="button" className="again" onClick={() => onGrade('again')}>
                Снова
              </button>
              <button type="button" className="good" onClick={() => onGrade('good')}>
                Хорошо
              </button>
            </div>
          </>
        ) : (
          <button type="button" className="show" onClick={onReveal}>
            Показать ответ
          </button>
        )}
      </div>
    </>
  )
}

function Back({ card, example, showFragment }: { card: Card; example: string; showFragment: boolean }) {
  const { entry } = card
  const meta = [
    entry.occurrences ? `в тексте ${entry.occurrences}` : '',
    ankiStatusLabel(entry.ankiStatus),
    showFragment && entry.fragmentId ? entry.fragmentId : '',
  ].filter(Boolean)

  return (
    <div className="back-body">
      {card.direction === 'meaning' && entry.glosses.length > 1 ? <p className="asked">{card.prompt}</p> : null}
      <p className="hanzi" lang="zh-CN">
        {entry.expression}
      </p>
      {entry.pinyin ? (
        <p className="pinyin" lang="zh-Latn">
          {entry.pinyin}
        </p>
      ) : null}
      {entry.meaning ? (
        <p className="meaning" lang="ru">
          {entry.meaning}
        </p>
      ) : null}
      {entry.unitType ? <p className="kind">{unitTypeLabel(entry.unitType)}</p> : null}
      {example ? (
        <>
          <hr className="rule" />
          <p className="example" lang="zh-CN">
            {example}
          </p>
          <PhrasePinyin example={example} entry={entry} />
        </>
      ) : null}
      {entry.notes ? <p className="notes">{entry.notes}</p> : null}
      {meta.length > 0 ? <p className="meta">{meta.join(' · ')}</p> : null}
    </div>
  )
}

function PhrasePinyin({ example, entry }: { example: string; entry: Card['entry'] }) {
  const reading = examplePinyin(example, entry.expression, entry.pinyin)
  if (!reading) return null
  return (
    <p className="pinyin phrase-pinyin" lang="zh-Latn">
      {reading}
    </p>
  )
}

function PlayButtons({ card, example }: { card: Card; example: string }) {
  return (
    <div className="plays">
      <button type="button" aria-label={`Произнести ${card.entry.expression}`} onClick={() => speakChinese(card.entry.expression)}>
        <PlayIcon />
      </button>
      {example ? (
        <button type="button" aria-label="Произнести пример" onClick={() => speakChinese(example)}>
          <PlayIcon />
        </button>
      ) : null}
    </div>
  )
}

function PlayIcon() {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
      <path fill="currentColor" d="M8 5.5v13l11-6.5z" />
    </svg>
  )
}

function Gate({
  loading,
  error,
  entries,
  showPicker,
  choices,
  tableUrl,
  banner,
  onPick,
  onReload,
}: {
  loading: boolean
  error: string | null
  entries: VocabEntry[] | null
  showPicker: boolean
  choices: { id: string; count: number }[]
  tableUrl: string
  banner: string | null
  onPick: (fragmentId: string | null) => void
  onReload: () => void
}) {
  if (loading) {
    return (
      <main className="gate">
        <p className="kicker">перед чтением</p>
        <h1>Загружаю вокабуляр</h1>
      </main>
    )
  }
  if (error) {
    return (
      <main className="gate">
        <p className="kicker">перед чтением</p>
        <h1>Не открылось</h1>
        <p>{error}</p>
        <button type="button" className="primary" onClick={onReload}>
          Повторить
        </button>
        <TableLink href={tableUrl} />
      </main>
    )
  }
  if (showPicker && entries) {
    return (
      <main className="gate">
        <p className="kicker">{SHEET_NAME}</p>
        <h1>Какой фрагмент</h1>
        {banner ? <p className="banner" role="status">{banner}</p> : null}
        <div className="choices">
          {choices.map((choice) => (
            <button type="button" key={choice.id || 'none'} onClick={() => onPick(choice.id)}>
              <span>{choice.id || 'Без номера'}</span>
              <span>{ruWords(choice.count)}</span>
            </button>
          ))}
          <button type="button" onClick={() => onPick(null)}>
            <span>Все фрагменты</span>
            <span>{ruWords(entries.length)}</span>
          </button>
        </div>
        <TableLink href={tableUrl} />
      </main>
    )
  }
  return (
    <main className="gate">
      <p className="kicker">{SHEET_NAME}</p>
      <h1>Пока нет слов</h1>
      <p>Текущий лист пуст. Когда в нём появятся строки, они станут карточками.</p>
      <button type="button" className="primary" onClick={onReload}>
        Обновить
      </button>
      <TableLink href={tableUrl} />
    </main>
  )
}

function TableLink({ href }: { href: string }) {
  return (
    <a className="text-link" href={href} target="_blank" rel="noreferrer">
      Таблица
    </a>
  )
}

function isAbort(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError'
}

function openEntries(
  entries: VocabEntry[],
  setPick: (pick: Pick) => void,
  setSession: (session: Session | null) => void,
  setRevealed: (revealed: boolean) => void,
) {
  const choices = fragmentChoices(entries)
  setRevealed(false)
  stopSpeaking()
  if (entries.length === 0) {
    setPick({ kind: 'pending' })
    setSession(null)
    return
  }
  if (choices.length > 1) {
    setPick({ kind: 'pending' })
    setSession(null)
    return
  }
  const fragmentId = choices[0]?.id ?? null
  setPick({ kind: 'ready', fragmentId })
  setSession(startSession(entriesForFragment(entries, fragmentId)))
}

function startSession(entries: VocabEntry[]): Session {
  const cards = shuffleCards(buildCards(entries), Math.random)
  return { queue: cards, known: 0, total: cards.length, againCount: 0 }
}

export function DoneScreen({
  session,
  tableUrl,
  onAgain,
}: {
  session: Session
  tableUrl: string
  onAgain: () => void
}) {
  return (
    <main className="gate">
      <p className="kicker">сессия</p>
      <h1>Можно читать</h1>
      <p>{ruCards(session.known)} отмечено «хорошо».</p>
      {session.againCount > 0 ? <p>Нажатий «Снова»: {session.againCount}.</p> : null}
      <button type="button" className="primary" onClick={onAgain}>
        Ещё проход
      </button>
      <TableLink href={tableUrl} />
    </main>
  )
}

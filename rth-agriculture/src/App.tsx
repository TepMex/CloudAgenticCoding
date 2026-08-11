import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import HanziWriter from 'hanzi-writer'
import { ArrowLeft, BarChart3, Flower2, HelpCircle, Leaf, Sparkles } from 'lucide-react'
import { plots, type CharacterDefinition, type PlotDefinition } from './data/model'
import { initialSave, loadSave, persistSave, type SaveGame } from './db'
import { plotInfection } from './garden'
import { loadHanziCharData } from './hanziData'
import { isCardDue, reviewCard, type ReviewEvent } from './learning'
import { WorldMap } from './map/WorldMap'
import { initialCamera, type CameraState } from './map/cameraMath'
import { StatisticsScreen } from './stats/StatisticsScreen'

type Screen = 'map' | 'battle' | 'stats'

function getInputDevice(): ReviewEvent['inputDevice'] {
  if (navigator.maxTouchPoints > 0) return 'touch'
  return 'mouse'
}

function strokeLabel(count: number): string {
  if (count % 10 === 1 && count % 100 !== 11) return `${count} штрих остался`
  if ([2, 3, 4].includes(count % 10) && ![12, 13, 14].includes(count % 100)) return `${count} штриха осталось`
  return `${count} штрихов осталось`
}

function MapScreen({
  save,
  camera,
  onCameraChange,
  onEnter,
  onStatistics,
}: {
  save: SaveGame
  camera: CameraState
  onCameraChange: (camera: CameraState) => void
  onEnter: (plot: PlotDefinition) => void
  onStatistics: () => void
}) {
  const learned = save.seenCharacterIds.length
  const due = plots.flatMap((plot) => plot.characters).filter((character) => isCardDue(save.cards[character.id])).length

  return (
    <main className="map-screen">
      <header className="map-header">
        <div className="brand-mark"><Leaf size={18} /><span>Сад памяти</span></div>
        <div className="world-summary">
          <span>{learned} изучено</span>
          <span>{due} на повторение</span>
          <button className="map-stats-button" onClick={onStatistics}><BarChart3 size={17} /> Статистика</button>
        </div>
      </header>
      <WorldMap save={save} camera={camera} onCameraChange={onCameraChange} onEnterPlot={onEnter} />
    </main>
  )
}

function BattleScreen({
  plot,
  save,
  onSave,
  onExit,
}: {
  plot: PlotDefinition
  save: SaveGame
  onSave: (save: SaveGame) => void
  onExit: () => void
}) {
  const dueCharacters = useMemo(
    () => plot.characters.filter((character) => isCardDue(save.cards[character.id])),
    [plot, save],
  )
  const [activeCharacterId, setActiveCharacterId] = useState<string | null>(dueCharacters[0]?.id ?? null)
  const activeCharacter = plot.characters.find((character) => character.id === activeCharacterId) ?? null
  const writerTarget = useRef<HTMLDivElement>(null)
  const writerRef = useRef<HanziWriter | null>(null)
  const saveRef = useRef(save)
  const mistakesRef = useRef(0)
  const hintUsedRef = useRef(false)
  const startedAtRef = useRef(Date.now())
  const completingRef = useRef(false)
  const correctStrokesRef = useRef(0)
  const [mistakes, setMistakes] = useState(0)
  const [correctStrokes, setCorrectStrokes] = useState(0)
  const [hitPulse, setHitPulse] = useState(0)
  const [feedback, setFeedback] = useState('Проведите первый штрих')
  const [destroyed, setDestroyed] = useState(false)

  useEffect(() => { saveRef.current = save }, [save])

  const finishCharacter = useCallback((character: CharacterDefinition, totalMistakes: number) => {
    if (completingRef.current) return
    completingRef.current = true
    const current = saveRef.current
    const result = reviewCard(current.cards[character.id], totalMistakes, hintUsedRef.current)
    const seen = new Set(current.seenCharacterIds)
    seen.add(character.id)
    const mastered = new Set(current.masteredPlotIds)
    const unlocked = new Set(current.unlockedPlotIds)
    const plotMastered = plot.characterIds.every((id) => seen.has(id))
    if (plotMastered) {
      mastered.add(plot.id)
      plot.neighbors.forEach((id) => unlocked.add(id))
    }
    const event: ReviewEvent = {
      id: crypto.randomUUID(),
      characterId: character.id,
      timestamp: Date.now(),
      rating: result.rating,
      totalMistakes,
      hintUsed: hintUsedRef.current,
      durationMs: Date.now() - startedAtRef.current,
      inputDevice: getInputDevice(),
    }
    const nextSave: SaveGame = {
      ...current,
      unlockedPlotIds: [...unlocked],
      masteredPlotIds: [...mastered],
      seenCharacterIds: [...seen],
      cards: { ...current.cards, [character.id]: result.card },
      reviewEvents: [...current.reviewEvents.slice(-499), event],
      updatedAt: Date.now(),
    }
    saveRef.current = nextSave
    onSave(nextSave)
    setDestroyed(true)
    setFeedback(result.rating === 'good' ? 'Сорняк рассыпается в пепел' : 'Сорняк отступил, но вернётся скорее')

    window.setTimeout(() => {
      const nextCharacter = plot.characters.find(
        (candidate) => candidate.id !== character.id && isCardDue(nextSave.cards[candidate.id]),
      )
      setActiveCharacterId(nextCharacter?.id ?? null)
      setDestroyed(false)
    }, 1050)
  }, [plot, onSave])

  useEffect(() => {
    if (!writerTarget.current || !activeCharacter) return
    mistakesRef.current = 0
    hintUsedRef.current = false
    correctStrokesRef.current = 0
    startedAtRef.current = Date.now()
    completingRef.current = false
    setMistakes(0)
    setCorrectStrokes(0)
    setFeedback('Проведите первый штрих')
    setDestroyed(false)
    writerTarget.current.replaceChildren()

    const writer = HanziWriter.create(writerTarget.current, activeCharacter.hanzi, {
      width: Math.round(writerTarget.current.clientWidth) || 300,
      height: Math.round(writerTarget.current.clientWidth) || 300,
      padding: 18,
      showCharacter: false,
      showOutline: false,
      renderer: 'svg',
      drawingColor: '#25201c',
      drawingWidth: 7,
      strokeColor: 'rgba(70, 54, 46, .18)',
      radicalColor: 'rgba(70, 54, 46, .18)',
      highlightColor: '#6d5269',
      highlightCompleteColor: '#4e6c56',
      acceptBackwardsStrokes: false,
      // XHR: Fetch is blocked for file:// in Android WebView / Chromium.
      charDataLoader: (char, onComplete, onError) => {
        loadHanziCharData(char).then(onComplete).catch(onError)
      },
    })
    writerRef.current = writer
    writer.quiz({
      leniency: 1,
      acceptBackwardsStrokes: false,
      showHintAfterMisses: 3,
      highlightOnComplete: false,
      onCorrectStroke: (data) => {
        correctStrokesRef.current = data.strokeNum + 1
        setCorrectStrokes(data.strokeNum + 1)
        setHitPulse((value) => value + 1)
        setFeedback(data.strokesRemaining ? 'Точный удар' : 'Последний корень перерублен')
      },
      onMistake: (data) => {
        mistakesRef.current = data.totalMistakes
        setMistakes(data.totalMistakes)
        setFeedback(data.totalMistakes >= 3 ? 'Чернила показывают следующий след' : 'Чернила рассеялись — попробуйте ещё')
      },
      onComplete: (summary) => finishCharacter(activeCharacter, summary.totalMistakes),
    })

    const syncWriterSize = () => {
      const target = writerTarget.current
      if (!target) return
      const raw = target.clientWidth
      if (raw < 32) return
      const size = Math.min(430, Math.max(180, Math.round(raw)))
      writer.updateDimensions({ width: size, height: size })
      const svg = target.querySelector('svg')
      if (svg) {
        // Keep pixel width/height for Hanzi Writer hit-testing; CSS scales the paint box.
        svg.style.maxWidth = '100%'
        svg.style.maxHeight = '100%'
        svg.style.overflow = 'hidden'
      }
    }
    // Layout can settle after first paint in Android WebView (immersive bars / insets).
    const frame = window.requestAnimationFrame(syncWriterSize)
    const resizeObserver = typeof ResizeObserver !== 'undefined'
      ? new ResizeObserver(() => syncWriterSize())
      : null
    resizeObserver?.observe(writerTarget.current)
    window.addEventListener('resize', syncWriterSize)
    window.visualViewport?.addEventListener('resize', syncWriterSize)
    return () => {
      window.cancelAnimationFrame(frame)
      resizeObserver?.disconnect()
      window.removeEventListener('resize', syncWriterSize)
      window.visualViewport?.removeEventListener('resize', syncWriterSize)
      writer.cancelQuiz()
      writerTarget.current?.replaceChildren()
      writerRef.current = null
    }
  }, [activeCharacter, finishCharacter])

  const useHint = () => {
    if (!activeCharacter || !writerRef.current) return
    hintUsedRef.current = true
    writerRef.current.highlightStroke(correctStrokesRef.current)
    setFeedback('Запомните движение кисти')
  }

  const infection = plotInfection(plot, save.cards)
  const remaining = activeCharacter ? Math.max(0, activeCharacter.strokeCount - correctStrokes) : 0
  const weedDamage = activeCharacter ? correctStrokes / activeCharacter.strokeCount : 1

  return (
    <main className={`battle-screen ${destroyed ? 'is-destroyed' : ''}`}>
      <div className="battle-backdrop" aria-hidden="true" />
      <button className="back-button" onClick={onExit} aria-label="Вернуться к карте"><ArrowLeft /></button>

      <header className="prompt-scroll">
        <span>Целевое значение</span>
        <strong>{activeCharacter?.keyword.ru.toLocaleUpperCase('ru') ?? 'ПОЛЕ ОЧИЩЕНО'}</strong>
      </header>

      {activeCharacter ? (
        <>
          <div
            className="weed-core"
            key={hitPulse}
            style={{ '--damage': weedDamage } as React.CSSProperties}
            aria-hidden="true"
          >
            <span /><span /><span />
          </div>
          <div className="writing-circle" ref={writerTarget} aria-label={`Напишите иероглиф со значением ${activeCharacter.keyword.ru}`} />
          <div className="battle-progress" aria-label={strokeLabel(remaining)}>
            {Array.from({ length: activeCharacter.strokeCount }, (_, index) => (
              <i key={index} className={index < correctStrokes ? 'is-cut' : ''} />
            ))}
          </div>
          <button className="hint-button" onClick={useHint}><HelpCircle size={16} /> Показать следующий штрих</button>
          <div className={`stroke-feedback ${mistakes ? 'has-mistake' : ''}`}>
            <span>{feedback}</span>
            <small>{mistakes ? `Ошибок: ${mistakes}` : strokeLabel(remaining)}</small>
          </div>
        </>
      ) : (
        <section className="cleared-state">
          <Flower2 />
          <h1>Сад снова дышит</h1>
          <p>Все доступные сорняки уничтожены. Новые повторения появятся здесь по расписанию памяти.</p>
          <button className="primary-button" onClick={onExit}>Вернуться к карте <Sparkles size={17} /></button>
        </section>
      )}

      <div className="field-cleanliness" title="Здоровье участка">
        <Leaf size={15} /><span><i style={{ width: `${(1 - infection) * 100}%` }} /></span>
      </div>
    </main>
  )
}

function Welcome({ onEnter }: { onEnter: () => void }) {
  return (
    <div className="welcome-screen">
      <div className="welcome-vignette" />
      <section className="welcome-card">
        <div className="seal"><span>忆</span></div>
        <p className="eyebrow">Наследие хранителя</p>
        <h1>Сад памяти</h1>
        <p>Забытые иероглифы пустили корни. Вспоминайте значение, пишите каждый штрих и возвращайте земле цвет.</p>
        <div className="welcome-rule"><span /> кисть помнит путь <span /></div>
        <button className="primary-button" onClick={onEnter}>Войти в сад <Leaf size={18} /></button>
      </section>
    </div>
  )
}

export default function App() {
  const [save, setSave] = useState<SaveGame>(initialSave)
  const [loaded, setLoaded] = useState(false)
  const [welcomed, setWelcomed] = useState(() => sessionStorage.getItem('memory-garden-welcomed') === 'yes')
  const [screen, setScreen] = useState<Screen>('map')
  const [activePlot, setActivePlot] = useState<PlotDefinition | null>(null)
  const [camera, setCamera] = useState<CameraState>(initialCamera)

  useEffect(() => {
    loadSave().then((stored) => {
      setSave(stored)
      setLoaded(true)
    })
  }, [])

  const updateSave = useCallback((nextSave: SaveGame) => {
    setSave(nextSave)
    void persistSave(nextSave)
  }, [])

  const enterPlot = (plot: PlotDefinition) => {
    if (!save.unlockedPlotIds.includes(plot.id)) return
    // The source data contains one one-character list. Its second half is an
    // intentional empty plot under the required midpoint split, so it is
    // immediately mastered when reached and cannot block world progression.
    if (plot.characterIds.length === 0) {
      const mastered = new Set(save.masteredPlotIds)
      const unlocked = new Set(save.unlockedPlotIds)
      mastered.add(plot.id)
      plot.neighbors.forEach((id) => unlocked.add(id))
      updateSave({
        ...save,
        masteredPlotIds: [...mastered],
        unlockedPlotIds: [...unlocked],
        updatedAt: Date.now(),
      })
      return
    }
    setActivePlot(plot)
    setScreen('battle')
  }

  if (!loaded) return <div className="loading-screen"><Leaf /> Сад пробуждается…</div>
  if (!welcomed) return <Welcome onEnter={() => {
    sessionStorage.setItem('memory-garden-welcomed', 'yes')
    setWelcomed(true)
  }} />

  if (screen === 'battle' && activePlot) {
    return <BattleScreen plot={activePlot} save={save} onSave={updateSave} onExit={() => setScreen('map')} />
  }

  if (screen === 'stats') return <StatisticsScreen save={save} onBack={() => setScreen('map')} />

  return <MapScreen
    save={save}
    camera={camera}
    onCameraChange={setCamera}
    onEnter={enterPlot}
    onStatistics={() => setScreen('stats')}
  />
}

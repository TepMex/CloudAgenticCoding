import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import HanziWriter from 'hanzi-writer'
import { ArrowLeft, BarChart3, Flower2, HelpCircle, Leaf, Sparkles } from 'lucide-react'
import { plots, type CharacterDefinition, type PlotDefinition } from './data/model'
import { battleArtworkForGarden, battleBackdropStage } from './data/battleFieldArt'
import { initialSave, loadSave, persistSave, type SaveGame } from './db'
import { plotInfection } from './garden'
import { loadHanziCharData } from './hanziData'
import { isCardDue, reviewCard, type ReviewEvent } from './learning'
import { WorldMap } from './map/WorldMap'
import { initialCamera, type CameraState } from './map/cameraMath'
import { StatisticsScreen } from './stats/StatisticsScreen'
import { streakHighlightColor, streakHighlightOpacity, streakIntensity } from './streak'
import { assetUrl } from './assetUrl'
import { writingInkForBackdrop } from './battleInk'

type Screen = 'map' | 'battle' | 'stats'

const NORMAL_HINT_COLOR = '#6d5269'
const STREAK_GRADIENT_ID = 'streak-jade-highlight'

function setStreakGradient(target: HTMLDivElement, intensity: number): () => void {
  const svg = target.querySelector('svg')
  const highlightGroup = svg?.querySelectorAll(':scope > g > g')[2] as SVGGElement | undefined
  const defs = svg?.querySelector(':scope > defs')
  if (!svg || !highlightGroup || !defs) return () => {}

  let gradient = defs.querySelector(`#${STREAK_GRADIENT_ID}`) as SVGLinearGradientElement | null
  if (!gradient) {
    gradient = document.createElementNS('http://www.w3.org/2000/svg', 'linearGradient')
    gradient.id = STREAK_GRADIENT_ID
    gradient.setAttribute('gradientUnits', 'userSpaceOnUse')
    gradient.setAttribute('x1', '90')
    gradient.setAttribute('y1', '260')
    gradient.setAttribute('x2', '910')
    gradient.setAttribute('y2', '520')
    defs.appendChild(gradient)
  }

  const progress = (intensity - 1) / 9
  const mutedJade = [
    [157, 179, 165], [145, 172, 153], [128, 160, 139], [144, 150, 112], [113, 142, 124],
  ]
  const deepJade = [
    [12, 58, 50], [17, 90, 74], [26, 119, 92], [37, 102, 76], [10, 63, 53],
  ]
  const mix = (from: number, to: number) => Math.round(from + (to - from) * progress)
  const stops = ['0%', '31%', '58%', '78%', '100%'].map((offset, index) => [
    offset,
    `rgb(${mix(mutedJade[index]![0], deepJade[index]![0])}, ${mix(mutedJade[index]![1], deepJade[index]![1])}, ${mix(mutedJade[index]![2], deepJade[index]![2])})`,
  ])
  gradient.replaceChildren(...stops.map(([offset, color]) => {
    const stop = document.createElementNS('http://www.w3.org/2000/svg', 'stop')
    stop.setAttribute('offset', offset)
    stop.setAttribute('stop-color', color)
    return stop
  }))

  highlightGroup.classList.add('streak-highlight')
  highlightGroup.style.setProperty('--streak-ink-opacity', String(streakHighlightOpacity(intensity)))
  return () => {
    highlightGroup.classList.remove('streak-highlight')
    highlightGroup.style.removeProperty('--streak-ink-opacity')
  }
}

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
  const hintMistakesRef = useRef(0)
  const hintUsedRef = useRef(false)
  const startedAtRef = useRef(Date.now())
  const completingRef = useRef(false)
  const correctStrokesRef = useRef(0)
  const streakHighlightRef = useRef(0)
  const clearStreakGradientRef = useRef<(() => void) | null>(null)
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
    const initialInk = writingInkForBackdrop('fullDirty')
    mistakesRef.current = 0
    hintMistakesRef.current = 0
    hintUsedRef.current = false
    correctStrokesRef.current = 0
    streakHighlightRef.current = 0
    clearStreakGradientRef.current?.()
    clearStreakGradientRef.current = null
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
      drawingColor: initialInk.drawingColor,
      drawingWidth: 7,
      strokeColor: initialInk.completedStrokeColor,
      radicalColor: initialInk.completedStrokeColor,
      highlightColor: NORMAL_HINT_COLOR,
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
        const completedStrokes = data.strokeNum + 1
        const nextBackdropStage = battleBackdropStage(activeCharacter.strokeCount, completedStrokes)
        const nextInk = writingInkForBackdrop(nextBackdropStage)
        correctStrokesRef.current = completedStrokes
        setCorrectStrokes(completedStrokes)
        // Hanzi Writer keeps completed paths in strokeColor. Update it at the
        // same time as the backdrop so existing and newly drawn ink retain contrast.
        writer.updateColor('drawingColor', nextInk.drawingColor, { duration: 0 })
        writer.updateColor('strokeColor', nextInk.completedStrokeColor, { duration: 0 })
        writer.updateColor('radicalColor', nextInk.completedStrokeColor, { duration: 0 })
        if (mistakesRef.current === 0) {
          const highlightId = ++streakHighlightRef.current
          const intensity = streakIntensity(activeCharacter.strokeCount, completedStrokes)
          clearStreakGradientRef.current?.()
          const clearStreakGradient = setStreakGradient(writerTarget.current!, intensity)
          clearStreakGradientRef.current = clearStreakGradient
          writer.updateColor('highlightColor', streakHighlightColor(intensity), { duration: 0 })
          writer.highlightStroke(data.strokeNum, {
            onComplete: () => {
              // Do not let an earlier animation reset the colour of a newer streak.
              if (streakHighlightRef.current !== highlightId) return
              clearStreakGradient()
              clearStreakGradientRef.current = null
              writer.updateColor('highlightColor', NORMAL_HINT_COLOR, { duration: 0 })
            },
          })
        }
        setHitPulse((value) => value + 1)
        setFeedback(data.strokesRemaining ? 'Точный удар' : 'Последний корень перерублен')
      },
      onMistake: (data) => {
        const totalMistakes = data.totalMistakes + hintMistakesRef.current
        mistakesRef.current = totalMistakes
        setMistakes(totalMistakes)
        setFeedback(totalMistakes >= 3 ? 'Чернила показывают следующий след' : 'Чернила рассеялись — попробуйте ещё')
      },
      onComplete: (summary) => {
        const totalMistakes = summary.totalMistakes + hintMistakesRef.current
        if (totalMistakes === 0) {
          // The completed perfect character remains as opaque ink until the next one appears.
          const cleanInk = writingInkForBackdrop('clean')
          writer.updateColor('strokeColor', cleanInk.completedStrokeColor, { duration: 120 })
          writer.updateColor('radicalColor', cleanInk.completedStrokeColor, { duration: 120 })
        }
        finishCharacter(activeCharacter, totalMistakes)
      },
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
    hintMistakesRef.current += 1
    mistakesRef.current += 1
    setMistakes(mistakesRef.current)
    streakHighlightRef.current += 1
    clearStreakGradientRef.current?.()
    clearStreakGradientRef.current = null
    writerRef.current.updateColor('highlightColor', NORMAL_HINT_COLOR, { duration: 0 })
    writerRef.current.highlightStroke(correctStrokesRef.current)
    setFeedback('Подсказка использована — streak сброшен')
  }

  const infection = plotInfection(plot, save.cards)
  const remaining = activeCharacter ? Math.max(0, activeCharacter.strokeCount - correctStrokes) : 0
  const weedDamage = activeCharacter ? correctStrokes / activeCharacter.strokeCount : 1
  const artwork = battleArtworkForGarden(plot.gardenId)
  const backdropStage = activeCharacter
    ? battleBackdropStage(activeCharacter.strokeCount, correctStrokes)
    : 'clean'
  const backdropUrl = assetUrl(artwork.backgrounds[backdropStage])

  useEffect(() => {
    // All four variants are ready before the first successful stroke, avoiding
    // a flash when the battlefield changes cleanliness state.
    Object.values(artwork.backgrounds).forEach((path) => {
      const image = new Image()
      image.src = assetUrl(path)
    })
  }, [artwork])

  return (
    <main className={`battle-screen ${destroyed ? 'is-destroyed' : ''}`}>
      <div
        className="battle-backdrop"
        style={{ backgroundImage: `url(${JSON.stringify(backdropUrl)})` }}
        aria-hidden="true"
      />
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

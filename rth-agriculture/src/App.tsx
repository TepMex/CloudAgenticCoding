import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import HanziWriter from 'hanzi-writer'
import { ArrowLeft, Brush, Flower2, HelpCircle, Leaf, LockKeyhole, Minus, Plus, RotateCcw, Sparkles } from 'lucide-react'
import { fieldById, fields, type CharacterDefinition, type FieldDefinition } from './data/model'
import { initialSave, loadSave, persistSave, resetSave, type SaveGame } from './db'
import { isCardDue, reviewCard, type ReviewEvent } from './learning'

type Screen = 'map' | 'battle'

const averageCharacters = fields.reduce((sum, field) => sum + field.characters.length, 0) / fields.length

function infectionFor(field: FieldDefinition, save: SaveGame): number {
  if (!save.unlockedFieldIds.includes(field.id)) return 1
  const totalWeight = field.characters.reduce((sum, character) => sum + character.strokeCount, 0)
  const dueWeight = field.characters.reduce(
    (sum, character) => sum + (isCardDue(save.cards[character.id]) ? character.strokeCount : 0),
    0,
  )
  return totalWeight ? dueWeight / totalWeight : 0
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
  onEnter,
  onReset,
}: {
  save: SaveGame
  onEnter: (field: FieldDefinition) => void
  onReset: () => void
}) {
  const [zoom, setZoom] = useState(1)
  const [selectedId, setSelectedId] = useState('field-001')
  const selected = fieldById.get(selectedId) ?? fields[0]
  const selectedUnlocked = save.unlockedFieldIds.includes(selected.id)
  const selectedInfection = infectionFor(selected, save)
  const selectedDue = selected.characters.filter((character) => isCardDue(save.cards[character.id])).length
  const cleanedCharacters = Object.keys(save.cards).length

  return (
    <main className="map-screen">
      <div className="map-backdrop" aria-hidden="true" />
      <header className="map-header">
        <div className="brand-mark"><Leaf size={18} /><span>Сад памяти</span></div>
        <div className="world-summary">
          <span><Flower2 size={16} /> {save.masteredFieldIds.length} цветущих полей</span>
          <span><Brush size={16} /> {cleanedCharacters} изучено</span>
        </div>
      </header>

      <section className="map-stage" aria-label="Карта сада">
        <div className="map-canvas" style={{ transform: `scale(${zoom})` }}>
          <div className="field-grid">
            {fields.map((field) => {
              const unlocked = save.unlockedFieldIds.includes(field.id)
              const infection = infectionFor(field, save)
              const weedLevel = Math.ceil(infection * 10)
              const scale = Math.max(0.72, Math.min(1.08, (field.characters.length / averageCharacters) ** 0.22))
              return (
                <button
                  className={`field-hotspot ${unlocked ? 'is-unlocked' : 'is-locked'} ${selectedId === field.id ? 'is-selected' : ''}`}
                  key={field.id}
                  style={{
                    gridRow: field.row + 1,
                    gridColumn: field.column + 1,
                    '--infection': infection,
                    '--plot-scale': scale,
                  } as React.CSSProperties}
                  onClick={() => {
                    setSelectedId(field.id)
                    if (unlocked && selectedId === field.id) onEnter(field)
                  }}
                  onDoubleClick={() => unlocked && onEnter(field)}
                  aria-label={`${field.title}, ${unlocked ? `уровень зарастания ${weedLevel}` : 'закрыто'}`}
                  title={unlocked ? `${field.title} · зарастание ${weedLevel}/10` : 'Эта часть сада ещё скрыта туманом'}
                >
                  <span className="field-status">
                    {unlocked ? (infection === 0 ? <Flower2 /> : <span className="weed-orb" />) : <LockKeyhole />}
                  </span>
                </button>
              )
            })}
          </div>
        </div>
      </section>

      <aside className="field-panel">
        <p className="eyebrow">Участок {selected.row * 11 + selected.column + 1} из 110</p>
        <h1>{selectedUnlocked ? selected.plantStyle : 'Земля в тумане'}</h1>
        <p className="field-subtitle">{selectedUnlocked ? selected.title : 'Очистите соседний участок, чтобы открыть путь.'}</p>
        {selectedUnlocked ? (
          <>
            <div className="health-line"><span style={{ width: `${Math.max(4, (1 - selectedInfection) * 100)}%` }} /></div>
            <div className="field-meta">
              <span>{selected.characters.length} знаков</span>
              <span>{selectedDue ? `${selectedDue} сорняков` : 'сад чист'}</span>
            </div>
            <button className="primary-button" onClick={() => onEnter(selected)}>
              {selectedDue ? 'Войти и очистить' : 'Пройти через сад'} <Sparkles size={17} />
            </button>
          </>
        ) : <div className="locked-note"><LockKeyhole size={17} /> Путь пока закрыт</div>}
      </aside>

      <div className="zoom-controls" aria-label="Масштаб карты">
        <button onClick={() => setZoom((value) => Math.max(0.88, value - 0.06))} aria-label="Уменьшить"><Minus /></button>
        <button onClick={() => setZoom((value) => Math.min(1.18, value + 0.06))} aria-label="Увеличить"><Plus /></button>
      </div>

      <button className="reset-button" onClick={onReset} title="Начать сад заново"><RotateCcw size={15} /> Сбросить</button>
      <p className="map-hint">Нажмите выбранное поле ещё раз, чтобы войти</p>
    </main>
  )
}

function BattleScreen({
  field,
  save,
  onSave,
  onExit,
}: {
  field: FieldDefinition
  save: SaveGame
  onSave: (save: SaveGame) => void
  onExit: () => void
}) {
  const dueCharacters = useMemo(
    () => field.characters.filter((character) => isCardDue(save.cards[character.id])),
    [field, save],
  )
  const [activeCharacterId, setActiveCharacterId] = useState<string | null>(dueCharacters[0]?.id ?? null)
  const activeCharacter = field.characters.find((character) => character.id === activeCharacterId) ?? null
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
    const mastered = new Set(current.masteredFieldIds)
    const unlocked = new Set(current.unlockedFieldIds)
    const fieldMastered = field.characterIds.every((id) => seen.has(id))
    if (fieldMastered) {
      mastered.add(field.id)
      field.neighbors.forEach((id) => unlocked.add(id))
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
      unlockedFieldIds: [...unlocked],
      masteredFieldIds: [...mastered],
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
      const nextCharacter = field.characters.find(
        (candidate) => candidate.id !== character.id && isCardDue(nextSave.cards[candidate.id]),
      )
      setActiveCharacterId(nextCharacter?.id ?? null)
      setDestroyed(false)
    }, 1050)
  }, [field, onSave])

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
      width: 430,
      height: 430,
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
      charDataLoader: (char, onComplete) => {
        fetch(`./hanzi/${encodeURIComponent(char)}.json`)
          .then((response) => {
            if (!response.ok) throw new Error(`Нет данных для ${char}`)
            return response.json()
          })
          .then(onComplete)
          .catch((error) => console.error(error))
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

    const onResize = () => {
      const size = Math.min(430, Math.max(250, writerTarget.current?.clientWidth ?? 430))
      writer.updateDimensions({ width: size, height: size })
    }
    onResize()
    window.addEventListener('resize', onResize)
    return () => {
      window.removeEventListener('resize', onResize)
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

  const infection = infectionFor(field, save)
  const remaining = activeCharacter ? Math.max(0, activeCharacter.strokeCount - correctStrokes) : 0
  const weedDamage = activeCharacter ? correctStrokes / activeCharacter.strokeCount : 1

  return (
    <main className={`battle-screen ${destroyed ? 'is-destroyed' : ''}`}>
      <div className="battle-backdrop" aria-hidden="true" />
      <button className="back-button" onClick={onExit} aria-label="Вернуться к карте"><ArrowLeft /></button>

      <header className="prompt-scroll">
        <span>Целевое значение</span>
        <strong>{activeCharacter?.keyword.ru.toLocaleUpperCase('ru') ?? 'ПОЛЕ ОЧИЩЕНО'}</strong>
        {activeCharacter && activeCharacter.keyword.ru === activeCharacter.keyword.en && (
          <small>{activeCharacter.keyword.en}</small>
        )}
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

      <div className="field-cleanliness" title="Здоровье поля">
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
  const [activeField, setActiveField] = useState<FieldDefinition | null>(null)

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

  const enterField = (field: FieldDefinition) => {
    if (!save.unlockedFieldIds.includes(field.id)) return
    setActiveField(field)
    setScreen('battle')
  }

  const handleReset = async () => {
    if (!window.confirm('Вернуть сад к первоначальному состоянию? История повторений будет удалена.')) return
    const fresh = await resetSave()
    setSave(fresh)
  }

  if (!loaded) return <div className="loading-screen"><Leaf /> Сад пробуждается…</div>
  if (!welcomed) return <Welcome onEnter={() => {
    sessionStorage.setItem('memory-garden-welcomed', 'yes')
    setWelcomed(true)
  }} />

  if (screen === 'battle' && activeField) {
    return <BattleScreen field={activeField} save={save} onSave={updateSave} onExit={() => setScreen('map')} />
  }

  return <MapScreen save={save} onEnter={enterField} onReset={handleReset} />
}

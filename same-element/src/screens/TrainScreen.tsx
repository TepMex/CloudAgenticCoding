import { useEffect, useRef, useState } from 'react'
import HanziWriter from 'hanzi-writer'
import { categoryById, type CatalogCharacter } from '../data/catalog'
import { loadHanziCharData } from '../data/hanziData'
import { rememberResult } from '../storage'
import { splitStrokes } from '../strokeMatch/splitStrokes'

const GIVEN_INK = '#2a5680'
const USER_INK = '#1c1612'
const OUTLINE = '#c9bfb2'
const HINT = '#a33b32'

export type RoundResult = CatalogCharacter & {
  mistakes: number
  revealed: boolean
}

export function TrainScreen({
  categoryId,
  queue,
  onExit,
  onFinish,
}: {
  categoryId: string
  queue: CatalogCharacter[]
  onExit: () => void
  onFinish: (results: RoundResult[]) => void
}) {
  const category = categoryById(categoryId)
  const [index, setIndex] = useState(0)
  const [results, setResults] = useState<RoundResult[]>([])
  const character = queue[index]

  if (!category || !character) {
    return (
      <main className="screen">
        <p>Список пуст.</p>
        <button className="text-button" onClick={onExit}>Назад</button>
      </main>
    )
  }

  const advance = (result: RoundResult) => {
    rememberResult(result.hanzi, result.mistakes === 0 && !result.revealed)
    const nextResults = [...results, result]
    if (index + 1 >= queue.length) {
      onFinish(nextResults)
      return
    }
    setResults(nextResults)
    setIndex(index + 1)
  }

  return (
    <Drill
      key={`${categoryId}:${character.hanzi}`}
      categoryId={categoryId}
      elementLabel={category.element}
      character={character}
      position={index + 1}
      total={queue.length}
      onExit={onExit}
      onAdvance={advance}
    />
  )
}

function Drill({
  categoryId,
  elementLabel,
  character,
  position,
  total,
  onExit,
  onAdvance,
}: {
  categoryId: string
  elementLabel: string
  character: CatalogCharacter
  position: number
  total: number
  onExit: () => void
  onAdvance: (result: RoundResult) => void
}) {
  const givenRef = useRef<HTMLDivElement>(null)
  const quizRef = useRef<HTMLDivElement>(null)
  const sheetRef = useRef<HTMLDivElement>(null)
  const writerRef = useRef<ReturnType<typeof HanziWriter.create> | null>(null)
  const givenWriterRef = useRef<ReturnType<typeof HanziWriter.create> | null>(null)
  const strokeRef = useRef(0)
  const mistakesRef = useRef(0)
  const revealedRef = useRef(false)
  const finishedRef = useRef(false)
  const [status, setStatus] = useState('Допишите то, чем знак отличается')
  const [mistakes, setMistakes] = useState(0)
  const [givenCount, setGivenCount] = useState(0)
  const [restCount, setRestCount] = useState(0)
  const [done, setDone] = useState(false)
  const [revealed, setRevealed] = useState(false)
  const [outlineOn, setOutlineOn] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const givenNode = givenRef.current
    const quizNode = quizRef.current
    const sheet = sheetRef.current
    if (!givenNode || !quizNode || !sheet) return
    let cancelled = false
    writerRef.current = null
    strokeRef.current = 0
    mistakesRef.current = 0
    revealedRef.current = false
    finishedRef.current = false

    const sizeOf = () => {
      const raw = sheet.clientWidth
      return Math.max(180, Math.min(460, Math.round(raw)))
    }

    loadHanziCharData(character.hanzi)
      .then((data) => {
        if (cancelled) return
        const indices = data.elements[categoryId] ?? []
        const { given, rest } = splitStrokes(data, indices)
        setGivenCount(given.strokes.length)
        setRestCount(rest.strokes.length)
        givenNode.replaceChildren()
        quizNode.replaceChildren()
        if (given.strokes.length === 0) {
          setStatus('Общий элемент не выделен — напишите знак целиком')
        }
        const size = sizeOf()
        const shared = {
          width: size,
          height: size,
          padding: 16,
          showOutline: false,
          renderer: 'svg',
          strokeFadeDuration: 0,
          delayBetweenStrokes: 0,
        }
        const givenWriter = given.strokes.length
          ? HanziWriter.create(givenNode, character.hanzi, {
              ...shared,
              showCharacter: true,
              strokeColor: GIVEN_INK,
              radicalColor: GIVEN_INK,
              charDataLoader: (_char: string, onComplete: (value: unknown) => void) => onComplete(given),
            })
          : null
        givenWriterRef.current = givenWriter
        givenWriter?.showCharacter({ duration: 0 })
        if (rest.strokes.length === 0) {
          setStatus('У этого знака нет отдельной части')
          setDone(true)
          return
        }

        const writer = HanziWriter.create(quizNode, character.hanzi, {
          ...shared,
          showCharacter: false,
          drawingColor: USER_INK,
          strokeColor: USER_INK,
          outlineColor: OUTLINE,
          highlightColor: HINT,
          drawingWidth: 8,
          leniency: 1.05,
          charDataLoader: (_char: string, onComplete: (value: unknown) => void) => onComplete(rest),
        })
        writerRef.current = writer
        writer.quiz({
          leniency: 1.05,
          acceptBackwardsStrokes: false,
          showHintAfterMisses: 3,
          highlightOnComplete: false,
          onCorrectStroke: (stroke) => {
            strokeRef.current = stroke.strokeNum + 1
            setStatus(stroke.strokesRemaining ? 'Так' : 'Знак собран')
          },
          onMistake: (stroke) => {
            mistakesRef.current = stroke.totalMistakes
            setMistakes(stroke.totalMistakes)
            setStatus(stroke.totalMistakes >= 3 ? 'След штриха подсвечен' : 'Мимо — штрих не совпал')
          },
          onComplete: (summary) => {
            if (finishedRef.current) return
            finishedRef.current = true
            mistakesRef.current = summary.totalMistakes
            setMistakes(summary.totalMistakes)
            setDone(true)
            setStatus(summary.totalMistakes === 0 && !revealedRef.current ? 'Знак собран' : 'Собрано, но были ошибки')
          },
        })
        const fit = () => {
          const next = sizeOf()
          writer.updateDimensions({ width: next, height: next })
          givenWriter?.updateDimensions({ width: next, height: next })
        }
        fit()
        const observer = new ResizeObserver(fit)
        observer.observe(sheet)
        cleanupResize = () => observer.disconnect()
      })
      .catch((reason: unknown) => {
        if (!cancelled) setError(reason instanceof Error ? reason.message : 'Не удалось загрузить штрихи')
      })

    let cleanupResize = () => {}
    return () => {
      cancelled = true
      cleanupResize()
      writerRef.current?.cancelQuiz()
      givenWriterRef.current = null
      givenNode.replaceChildren()
      quizNode.replaceChildren()
    }
  }, [categoryId, character.hanzi])

  const hint = () => {
    const writer = writerRef.current
    if (!writer || done) return
    writer.highlightStroke(strokeRef.current)
    setStatus('След следующего штриха')
  }

  const toggleOutline = () => {
    const writer = writerRef.current
    if (!writer || done) return
    if (outlineOn) {
      writer.hideOutline({ duration: 0 })
      setOutlineOn(false)
      setStatus('Контур скрыт')
    } else {
      writer.showOutline({ duration: 150 })
      setOutlineOn(true)
      revealedRef.current = true
      setRevealed(true)
      setStatus('Контур отличия — это уже подсказка')
    }
  }

  const reveal = () => {
    revealedRef.current = true
    setRevealed(true)
    writerRef.current?.showOutline({ duration: 150 })
    setOutlineOn(true)
    setStatus('Знак открыт. Можно дописать или идти дальше')
  }

  const next = () => {
    onAdvance({
      ...character,
      mistakes: mistakesRef.current,
      revealed: revealedRef.current,
    })
  }

  return (
    <main className="screen train-screen">
      <header className="topbar">
        <button className="text-button" onClick={onExit}>Список</button>
        <p className="topbar-title">{position} из {total}</p>
        <span className="topbar-meta">{mistakes ? `ошибки ${mistakes}` : 'чисто'}</span>
      </header>
      <section className="prompt">
        <h1>{character.ru}</h1>
        <p className="pinyin">
          {character.pinyin}
          {revealed || done ? <span className="revealed-hanzi" lang="zh">{character.hanzi}</span> : null}
        </p>
        <p className="status">{status}</p>
      </section>
      <div className="sheet-wrap">
        <div className="practice-sheet" ref={sheetRef}>
          <div className="writer-layer given" ref={givenRef} />
          <div className="writer-layer quiz" ref={quizRef} />
        </div>
        <p className="sheet-caption">
          <span className="swatch given-swatch" /> уже есть {givenCount}
          <span className="swatch user-swatch" /> дописать {restCount}
          <span lang="zh"> · {elementLabel}</span>
        </p>
        {error ? <p className="error">{error}</p> : null}
      </div>
      <footer className="dock train-dock">
        <button className="chip" onClick={hint} disabled={done || Boolean(error)}>Подсказка</button>
        <button className="chip" onClick={toggleOutline} disabled={done || Boolean(error)}>
          {outlineOn ? 'Скрыть контур' : 'Контур'}
        </button>
        <button className="chip" onClick={reveal} disabled={revealed || done}>Не помню</button>
        {done || revealed ? (
          <button className="primary" onClick={next}>{position === total ? 'Итог' : 'Дальше'}</button>
        ) : null}
      </footer>
    </main>
  )
}


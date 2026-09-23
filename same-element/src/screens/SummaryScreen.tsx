import type { RoundResult } from './TrainScreen'

export function SummaryScreen({
  results,
  onRetryMisses,
  onRetryAll,
  onBack,
}: {
  results: RoundResult[]
  onRetryMisses: () => void
  onRetryAll: () => void
  onBack: () => void
}) {
  const misses = results.filter((result) => result.mistakes > 0 || result.revealed)
  const clean = results.length - misses.length
  return (
    <main className="screen summary-screen">
      <header className="masthead">
        <p className="eyebrow">круг закончен</p>
        <h1>{clean} чисто · {misses.length} с ошибкой</h1>
        <p className="lede">
          {misses.length === 0
            ? 'Эти знаки разошлись по своим отличиям.'
            : 'Ошибки — те знаки, которые ещё сливаются. Имеет смысл пройти только их.'}
        </p>
      </header>
      {misses.length > 0 ? (
        <ul className="miss-list">
          {misses.map((result) => (
            <li key={result.hanzi}>
              <span lang="zh">{result.hanzi}</span>
              <span>
                <strong>{result.pinyin}</strong>
                <small>{result.ru}</small>
              </span>
            </li>
          ))}
        </ul>
      ) : null}
      <footer className="dock stack-dock">
        {misses.length > 0 ? (
          <button className="primary" onClick={onRetryMisses}>Повторить ошибки</button>
        ) : null}
        <button className="chip" onClick={onRetryAll}>Ещё раз весь список</button>
        <button className="text-button" onClick={onBack}>К списку знаков</button>
      </footer>
    </main>
  )
}

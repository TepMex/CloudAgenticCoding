import { ArrowLeft, ArrowRight, Check, RotateCcw, Volume2, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { heroImage } from '../../domain/heroes'
import { getCity } from '../../domain/cities'
import { completeTask, levelOf, type TaskResult } from '../../domain/progress'
import { buildQuiz, getTask, PASS_SCORE } from '../../domain/tasks'
import { navigate } from '../../router'
import { canSpeak, speakChinese } from '../../speech'
import { useAccount } from '../../store'
import { useToast } from '../../components/Toast'
import { CoinIcon } from '../../components/icons'
import { ProgressBar } from '../../components/common'

export function QuizPage({ taskId }: { taskId: string }) {
  const [account, update] = useAccount()
  const toast = useToast()
  const task = getTask(taskId)
  const city = task ? getCity(task.cityId) : undefined
  const [attempt, setAttempt] = useState(0)
  const quiz = useMemo(() => (task ? buildQuiz(task) : []), [task, attempt])
  const [index, setIndex] = useState(0)
  const [chosen, setChosen] = useState<string | null>(null)
  const [score, setScore] = useState(0)
  const [result, setResult] = useState<TaskResult | null>(null)

  const q = quiz[index]
  const locked = city ? levelOf(account.xp) < city.level - 1 : true

  useEffect(() => {
    if (q?.speak && account.sound) speakChinese(q.speak)
  }, [q, account.sound])

  if (!task || !city || locked) {
    return (
      <div className="page">
        <p>Задание недоступно.</p>
        <a href="#/app/tasks">К заданиям</a>
      </div>
    )
  }

  function choose(option: string) {
    if (chosen) return
    setChosen(option)
    if (option === q.answer) setScore((s) => s + 1)
  }

  function next() {
    if (index + 1 < quiz.length) {
      setIndex(index + 1)
      setChosen(null)
      return
    }
    const r = completeTask(account, task!.id, score)
    update(() => r.account)
    setResult(r)
    if (r.leveledUp) toast(`Новый уровень: ${levelOf(r.account.xp)}!`, 'ok')
  }

  function retry() {
    setAttempt((n) => n + 1)
    setIndex(0)
    setChosen(null)
    setScore(0)
    setResult(null)
  }

  return (
    <div className="page quiz-page">
      <header className="quiz__top">
        <button className="btn btn--ghost btn--sm" onClick={() => navigate('/app/tasks')}>
          <ArrowLeft size={16} /> Задания
        </button>
        <div className="quiz__crumbs">
          <span className="tag">
            {city.hanzi} · {city.name}
          </span>
          <b>{task.title}</b>
        </div>
        <div className="quiz__progress">
          <ProgressBar value={result ? quiz.length : index} max={quiz.length} label="Вопрос" />
          <small>
            {result ? quiz.length : index + 1} / {quiz.length}
          </small>
        </div>
      </header>

      {result ? (
        <section className="panel quiz__result">
          <img src={heroImage(account.heroId)} alt="" className="quiz__hero" />
          <div>
            <h2 className="title-serif">{result.passed ? (score === quiz.length ? 'Идеально! 太棒了!' : 'Отлично! 很好!') : 'Почти получилось!'}</h2>
            <p className="quiz__score">
              Правильных ответов: <b>{score}</b> из {quiz.length}
            </p>
            {result.firstTime ? (
              <p className="quiz__reward">
                +{result.xpGained} XP · +{result.coinsGained} <CoinIcon size={18} />
              </p>
            ) : result.passed ? (
              <p className="muted">Задание уже было выполнено — награда выдаётся один раз, но повторение укрепляет память!</p>
            ) : (
              <p className="muted">Нужно хотя бы {PASS_SCORE} правильных ответа. Попробуй ещё раз — у тебя получится!</p>
            )}
            <div className="quiz__actions">
              <button className="btn btn--ghost" onClick={retry}>
                <RotateCcw size={18} /> Ещё раз
              </button>
              <button className="btn btn--primary" onClick={() => navigate('/app/map')}>
                На карту <ArrowRight size={18} />
              </button>
            </div>
          </div>
        </section>
      ) : (
        <section className="panel quiz">
          <p className="quiz__subtitle">{task.subtitle}</p>
          {q.speak ? (
            <button className="quiz__listen" onClick={() => speakChinese(q.speak!)} disabled={!canSpeak()}>
              <Volume2 size={40} />
              <span>{canSpeak() ? q.prompt : 'Озвучка недоступна в этом браузере'}</span>
            </button>
          ) : (
            <div className={`quiz__prompt ${task.kind === 'reverse' ? 'quiz__prompt--ru' : ''}`}>{q.prompt}</div>
          )}
          {(q.hint && task.kind === 'meaning') || (q.speak && (chosen || !canSpeak())) ? <p className="quiz__hint">{q.hint}</p> : null}
          <div className="quiz__options">
            {q.options.map((o) => {
              const state = !chosen ? '' : o === q.answer ? 'option--right' : o === chosen ? 'option--wrong' : 'option--dim'
              const hanziOption = task.kind === 'reverse' || task.kind === 'listen'
              return (
                <button key={o} className={`option ${hanziOption ? 'option--hanzi' : ''} ${state}`} onClick={() => choose(o)} disabled={!!chosen}>
                  {o}
                  {chosen && o === q.answer && <Check size={20} strokeWidth={3} />}
                  {chosen && o === chosen && o !== q.answer && <X size={20} strokeWidth={3} />}
                </button>
              )
            })}
          </div>
          {chosen && (
            <div className={`quiz__feedback ${chosen === q.answer ? 'quiz__feedback--ok' : 'quiz__feedback--bad'}`}>
              <span>
                {chosen === q.answer ? 'Верно!' : 'Не совсем.'} <b>{q.word.hanzi}</b> ({q.word.pinyin}) — {q.word.ru}
              </span>
              <button className="btn btn--primary btn--sm" onClick={next} autoFocus>
                {index + 1 < quiz.length ? 'Дальше' : 'Результат'} <ArrowRight size={16} />
              </button>
            </div>
          )}
        </section>
      )}
    </div>
  )
}

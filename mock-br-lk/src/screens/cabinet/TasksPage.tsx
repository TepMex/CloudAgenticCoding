import { ArrowRight, Check, Ear, Languages, Lock, PenLine, Search } from 'lucide-react'
import { CITIES } from '../../domain/cities'
import { cityDone, currentCity, isCityUnlocked, levelOf, TASK_COINS, TASK_XP } from '../../domain/progress'
import { tasksForCity, type TaskKind } from '../../domain/tasks'
import { navigate } from '../../router'
import { useAccount } from '../../store'
import { Page } from './Page'

const KIND_ICON: Record<TaskKind, typeof Ear> = { meaning: Languages, pinyin: PenLine, reverse: Search, listen: Ear }

export function TasksPage() {
  const [account] = useAccount()
  const level = levelOf(account.xp)
  const here = currentCity(account)
  const open = CITIES.filter((c) => isCityUnlocked(c, level))
  const nextLocked = CITIES.find((c) => !isCityUnlocked(c, level))

  return (
    <Page title="Задания" subtitle={`Каждое задание — ${TASK_XP} XP и ${TASK_COINS} монет. Для зачёта нужно ответить верно хотя бы на 3 вопроса из 4.`}>
      {[here, ...open.filter((c) => c.id !== here.id).reverse()].map((city) => (
        <section key={city.id} className={`panel task-city ${city.id === here.id ? 'task-city--here' : ''}`}>
          <header className="task-city__head">
            <span className="task-city__hanzi">{city.hanzi}</span>
            <div>
              <h2>
                {city.name} {city.id === here.id && <span className="tag">Ты здесь</span>}
              </h2>
              <p className="muted">
                {city.theme} · выполнено {cityDone(city, account)} из 4
              </p>
            </div>
          </header>
          <div className="task-cards">
            {tasksForCity(city).map((t) => {
              const Icon = KIND_ICON[t.kind]
              const done = account.completedTasks.includes(t.id)
              return (
                <button key={t.id} className={`task-card ${done ? 'task-card--done' : ''}`} onClick={() => navigate(`/app/tasks/${t.id}`)}>
                  <span className="task-card__icon">{done ? <Check size={22} strokeWidth={3} /> : <Icon size={22} />}</span>
                  <b>{t.title}</b>
                  <small>{t.subtitle}</small>
                  <span className="task-card__go">
                    {done ? 'Повторить' : `+${TASK_XP} XP`} <ArrowRight size={16} />
                  </span>
                </button>
              )
            })}
          </div>
        </section>
      ))}
      {nextLocked && (
        <section className="panel task-city task-city--locked">
          <Lock size={22} />
          <p>
            Следующий город — <b>{nextLocked.name}</b> ({nextLocked.theme.toLowerCase()}). Откроется на {nextLocked.level - 1}-м уровне.
          </p>
        </section>
      )}
    </Page>
  )
}

import { ArrowRight, Check, Lock, Volume2 } from 'lucide-react'
import { Modal } from '../../components/common'
import type { City } from '../../domain/cities'
import { isCityUnlocked, levelOf } from '../../domain/progress'
import { QUESTIONS_PER_TASK, tasksForCity } from '../../domain/tasks'
import { navigate } from '../../router'
import { speakChinese } from '../../speech'
import { useAccount } from '../../store'

export function CityDialog({ city, onClose }: { city: City; onClose: () => void }) {
  const [account] = useAccount()
  const unlocked = isCityUnlocked(city, levelOf(account.xp))

  return (
    <Modal title={city.name} onClose={onClose} wide>
      <div className="city">
        <div className="city__head">
          <span className="city__hanzi">{city.hanzi}</span>
          <div>
            <p className="city__theme">Тема: {city.theme}</p>
            <p className="city__fact">{city.fact}</p>
          </div>
        </div>
        {unlocked ? (
          <>
            <div className="city__words">
              {city.words.map((w) => (
                <button key={w.hanzi} className="word-chip" onClick={() => account.sound && speakChinese(w.hanzi)} title="Послушать">
                  <b>{w.hanzi}</b>
                  <small>{w.pinyin}</small>
                  <span>{w.ru}</span>
                  {account.sound && <Volume2 size={14} className="word-chip__sound" />}
                </button>
              ))}
            </div>
            <ul className="task-list">
              {tasksForCity(city).map((t) => {
                const done = account.completedTasks.includes(t.id)
                const best = account.taskScores[t.id]
                return (
                  <li key={t.id} className={`task-row ${done ? 'task-row--done' : ''}`}>
                    <span className="task-row__status">{done ? <Check size={18} strokeWidth={3} /> : null}</span>
                    <span className="task-row__text">
                      <b>{t.title}</b>
                      <small>
                        {t.subtitle}
                        {best !== undefined && ` · лучший результат ${best}/${QUESTIONS_PER_TASK}`}
                      </small>
                    </span>
                    <button className={`btn ${done ? 'btn--ghost' : 'btn--primary'} btn--sm`} onClick={() => navigate(`/app/tasks/${t.id}`)}>
                      {done ? 'Повторить' : 'Начать'} <ArrowRight size={16} />
                    </button>
                  </li>
                )
              })}
            </ul>
          </>
        ) : (
          <div className="city__locked">
            <Lock size={28} />
            <p>
              Город откроется, когда твой герой достигнет <b>{city.level - 1}-го уровня</b>. Сейчас у тебя {levelOf(account.xp)}-й.
            </p>
            <p className="muted">Выполняй задания в открытых городах, чтобы набрать опыт.</p>
          </div>
        )}
      </div>
    </Modal>
  )
}

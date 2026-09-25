import { CalendarDays, Copy, PartyPopper, PenTool, Presentation, Volume2 } from 'lucide-react'
import { useState } from 'react'
import { useToast } from '../../components/Toast'
import { CITIES } from '../../domain/cities'
import { heroImage } from '../../domain/heroes'
import { collectedWords, levelOf } from '../../domain/progress'
import { EVENTS, FRIENDS } from '../../domain/social'
import { speakChinese } from '../../speech'
import { useAccount } from '../../store'
import { Page } from './Page'

export function CollectionsPage() {
  const [account] = useAccount()
  const words = collectedWords(account)
  const total = new Set(CITIES.flatMap((c) => c.words.map((w) => w.hanzi))).size
  const [flipped, setFlipped] = useState<string | null>(null)

  return (
    <Page title="Коллекция слов" subtitle={`Собрано ${words.length} из ${total}. Начни задания в новом городе, чтобы получить его карточки.`}>
      {words.length === 0 ? (
        <div className="panel empty">
          <p>Пока пусто. Выполни первое задание в Пекине — и здесь появятся карточки!</p>
          <a className="btn btn--primary" href="#/app/tasks">
            К заданиям
          </a>
        </div>
      ) : (
        <div className="cards-grid">
          {words.map((w) => (
            <button
              key={w.hanzi}
              className={`word-card ${flipped === w.hanzi ? 'word-card--flipped' : ''}`}
              onClick={() => {
                setFlipped(flipped === w.hanzi ? null : w.hanzi)
                if (account.sound) speakChinese(w.hanzi)
              }}
            >
              <span className="word-card__front">
                <b>{w.hanzi}</b>
                <Volume2 size={14} />
              </span>
              <span className="word-card__back">
                <b>{w.pinyin}</b>
                <span>{w.ru}</span>
              </span>
            </button>
          ))}
        </div>
      )}
    </Page>
  )
}

export function FriendsPage() {
  const [account] = useAccount()
  const toast = useToast()
  const me = { name: `${account.studentName} (ты)`, heroId: account.heroId ?? 'fox', heroName: account.heroName, level: levelOf(account.xp), online: true, city: '' }
  const list = [...FRIENDS, me].sort((a, b) => b.level - a.level)

  function invite() {
    const link = `${location.origin}${location.pathname}#/register`
    navigator.clipboard?.writeText(link).catch(() => {})
    toast('Ссылка-приглашение скопирована')
  }

  return (
    <Page
      title="Друзья"
      subtitle="Учиться вместе веселее! Рейтинг класса по уровню героя."
      actions={
        <button className="btn btn--primary" onClick={invite}>
          <Copy size={18} /> Пригласить друга
        </button>
      }
    >
      <ol className="panel leaderboard">
        {list.map((f, i) => (
          <li key={f.name} className={f === me ? 'leaderboard__me' : ''}>
            <span className="leaderboard__place">{i + 1}</span>
            <span className="leaderboard__avatar">
              <img src={heroImage(f.heroId)} alt="" />
              {f.online && <i className="online" aria-label="в сети" />}
            </span>
            <span className="leaderboard__name">
              <b>{f.name}</b>
              <small>
                {f.heroName}
                {f.city && ` · ${f.city}`}
              </small>
            </span>
            <span className="leaderboard__level">Ур. {f.level}</span>
          </li>
        ))}
      </ol>
    </Page>
  )
}

const EVENT_ICON = { lesson: Presentation, festival: PartyPopper, contest: PenTool }
const EVENT_LABEL = { lesson: 'Урок', festival: 'Праздник', contest: 'Конкурс' }

export function EventsPage() {
  const toast = useToast()
  const [account, update] = useAccount()
  const joined = account.joinedEvents
  return (
    <Page title="События" subtitle="Живые уроки, праздники и конкурсы школы BRY.">
      <div className="events">
        {EVENTS.map((e) => {
          const Icon = EVENT_ICON[e.kind]
          const on = joined.includes(e.id)
          return (
            <article key={e.id} className={`panel event event--${e.kind}`}>
              <span className="event__hanzi">{e.hanzi}</span>
              <div className="event__body">
                <span className="tag">
                  <Icon size={14} /> {EVENT_LABEL[e.kind]}
                </span>
                <h2>{e.title}</h2>
                <p className="muted">{e.description}</p>
                <p className="event__date">
                  <CalendarDays size={16} /> {e.date}
                </p>
              </div>
              <button
                className={`btn btn--sm ${on ? 'btn--ghost' : 'btn--primary'}`}
                onClick={() => {
                  update((a) => ({ ...a, joinedEvents: on ? a.joinedEvents.filter((x) => x !== e.id) : [...a.joinedEvents, e.id] }))
                  if (!on) toast('Напомним о событии заранее!', 'info')
                }}
              >
                {on ? 'Вы записаны' : 'Записаться'}
              </button>
            </article>
          )
        })}
      </div>
    </Page>
  )
}

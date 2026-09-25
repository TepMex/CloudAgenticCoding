import { BookOpen, Flame, MapPinned, Target } from 'lucide-react'
import { CITIES } from '../../domain/cities'
import { collectedWords, isCityComplete, isCityUnlocked, levelOf, skillLevels } from '../../domain/progress'
import { QUESTIONS_PER_TASK } from '../../domain/tasks'
import { useAccount } from '../../store'
import { Page } from './Page'

export function StatsPage() {
  const [account] = useAccount()
  const skills = skillLevels(account)
  const scores = Object.values(account.taskScores)
  const accuracy = scores.length ? Math.round((scores.reduce((s, x) => s + x, 0) / (scores.length * QUESTIONS_PER_TASK)) * 100) : 0
  const level = levelOf(account.xp)

  const tiles = [
    { icon: BookOpen, value: collectedWords(account).length, label: 'Слов в коллекции' },
    { icon: MapPinned, value: `${CITIES.filter((c) => isCityUnlocked(c, level)).length} / ${CITIES.length}`, label: 'Городов открыто' },
    { icon: Target, value: `${accuracy}%`, label: 'Точность ответов' },
    { icon: Flame, value: CITIES.filter((c) => isCityComplete(c, account)).length, label: 'Городов пройдено' },
  ]

  return (
    <Page title="Характеристики" subtitle="Навыки растут с каждым выполненным заданием — незаметно для тебя.">
      <div className="stat-grid">
        {tiles.map(({ icon: Icon, value, label }) => (
          <div key={label} className="panel stat-card">
            <Icon size={26} />
            <b>{value}</b>
            <span>{label}</span>
          </div>
        ))}
      </div>
      <section className="panel skills">
        <h2>Навыки</h2>
        {skills.map((s) => (
          <div key={s.kind} className="skill">
            <span className="skill__name">{s.name}</span>
            <div className="skill__bar">
              <div style={{ width: `${Math.max(2, s.value)}%` }} />
            </div>
            <span className="skill__val">{s.value}%</span>
          </div>
        ))}
        <p className="muted">Каждый навык прокачивается своим типом заданий: «Что это значит?», «Как это читается?», «Найди иероглиф» и «Слушай внимательно».</p>
      </section>
    </Page>
  )
}

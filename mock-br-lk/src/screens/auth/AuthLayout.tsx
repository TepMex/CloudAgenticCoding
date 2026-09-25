import { BookOpenCheck, ChartNoAxesColumnIncreasing, Compass, GraduationCap, Globe, Heart, Star, Users } from 'lucide-react'
import type { ReactNode } from 'react'
import { asset } from '../../asset'
import { Brand, FeatureBar, HandNote } from '../../components/common'

const PERKS = [
  { icon: BookOpenCheck, text: 'Увлекательная игровая форма' },
  { icon: ChartNoAxesColumnIncreasing, text: 'Реальный прогресс и результаты' },
  { icon: Users, text: 'Заботливые преподаватели' },
  { icon: Star, text: 'Новые горизонты и возможности' },
]

const FOOTER = [
  { icon: GraduationCap, text: 'Китайский язык' },
  { icon: Globe, text: 'Культура и традиции' },
  { icon: Compass, text: 'Развитие и уверенность' },
  { icon: Heart, text: 'Друзья и сообщество' },
]

export function AuthLayout({ topRight, children }: { topRight: ReactNode; children: ReactNode }) {
  return (
    <div className="screen auth" style={{ backgroundImage: `url(${asset('img/bg-register.webp')})` }}>
      <div className="auth__top">
        <Brand />
        <div className="auth__top-right">{topRight}</div>
      </div>
      <main className="auth__main">
        <section className="auth__card">{children}</section>
        <HandNote className="auth__note-left" rotate={-10}>
          Маленькие
          <br />
          шаги приводят
          <br />к большим
          <br />
          свершениям!
        </HandNote>
        <aside className="auth__perks">
          <HandNote className="auth__note-right" rotate={-8}>
            Больше,
            <br />
            чем просто
            <br />
            китайский!
          </HandNote>
          <ul>
            {PERKS.map(({ icon: Icon, text }) => (
              <li key={text}>
                <span className="perk-icon">
                  <Icon size={22} />
                </span>
                {text}
              </li>
            ))}
          </ul>
        </aside>
        <div className="signpost" aria-hidden="true">
          <div className="signpost__hanzi">加油！</div>
          <div className="signpost__text">У тебя всё получится!</div>
        </div>
      </main>
      <FeatureBar items={FOOTER} note="Вперёд к новым вершинам!" />
    </div>
  )
}

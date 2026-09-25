import { ArrowRight, BookOpen, ChartNoAxesColumnIncreasing, Compass, Heart, Mountain, Trophy, Users } from 'lucide-react'
import { asset } from '../../asset'
import { Brand, FeatureBar, HandNote } from '../../components/common'
import { getHero } from '../../domain/heroes'
import { navigate } from '../../router'
import { useAccount } from '../../store'

const POINTS = [
  { icon: Mountain, text: 'Исследуй удивительный мир Китая' },
  { icon: BookOpen, text: 'Развивайся, выполняя интересные задания' },
  { icon: Trophy, text: 'Открывай новые города, получай награды и становись сильнее!' },
]

const FOOTER = [
  { icon: Compass, text: <>Новый мир<br />ждёт тебя</> },
  { icon: ChartNoAxesColumnIncreasing, text: <>Каждое занятие<br />делает тебя сильнее</> },
  { icon: Users, text: <>Здесь ты не один —<br />мы рядом</> },
  { icon: Heart, text: <>Знания сегодня —<br />большие возможности завтра</> },
]

export function Welcome() {
  const [account, update] = useAccount()

  function start() {
    update((a) => ({ ...a, onboarding: 'hero' }))
    navigate('/hero')
  }

  function skip() {
    const fox = getHero('fox')
    update((a) => ({ ...a, onboarding: 'done', heroId: fox.id, heroName: fox.defaultName }))
    navigate('/app/map')
  }

  return (
    <div className="screen welcome" style={{ backgroundImage: `url(${asset('img/bg-welcome.webp')})` }}>
      <div className="welcome__brand">
        <Brand dark />
      </div>
      <div className="wood-sign" aria-hidden="true">
        Большое
        <br />
        путешествие
        <br />
        начинается
        <br />с первого
        <br />
        шага!
      </div>
      <HandNote className="welcome__note" rotate={-8}>
        Маленькие
        <br />
        шаги —
        <br />к большим
        <br />
        свершениям!
      </HandNote>
      <div className="banner" aria-hidden="true">
        <span>学</span>
        <span>无</span>
        <span>止</span>
        <span>境</span>
        <small>Учиться без границ</small>
      </div>
      <main className="welcome__card">
        <h1>
          Добро пожаловать
          <br />в BRY Chinese{account.studentName ? `, ${account.studentName}` : ''}!
        </h1>
        <p className="welcome__lead">Ты делаешь первый шаг в увлекательное путешествие по миру китайского языка!</p>
        <ul className="welcome__points">
          {POINTS.map(({ icon: Icon, text }) => (
            <li key={text}>
              <span className="perk-icon perk-icon--light">
                <Icon size={22} />
              </span>
              {text}
            </li>
          ))}
        </ul>
        <button className="btn btn--primary btn--wide" onClick={start}>
          Создать своего героя <ArrowRight size={20} />
        </button>
        <button className="link-btn" onClick={skip}>
          Пропустить знакомство
        </button>
      </main>
      <FeatureBar items={FOOTER} />
    </div>
  )
}

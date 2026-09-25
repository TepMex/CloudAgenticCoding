import { ArrowRight, Dices, Map, Pencil, Sparkles } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { asset } from '../../asset'
import { Brand, HandNote, Stepper } from '../../components/common'
import { TRAIT_ICONS } from '../../components/icons'
import { CITIES } from '../../domain/cities'
import { getHero, heroImage } from '../../domain/heroes'
import { levelOf } from '../../domain/progress'
import { navigate } from '../../router'
import { useAccount } from '../../store'

const SUFFIXES = ['Линь', 'Бао', 'Ху', 'Лун', 'Мин', 'Сяо', 'Фэй', 'Юнь', 'Тао', 'Лэй', 'Чжу', 'Хуа']

export function HeroCreated() {
  const [account, update] = useAccount()
  const hero = getHero(account.heroId)
  const changing = account.onboarding === 'done'
  const [name, setName] = useState(account.heroName || hero.defaultName)
  const TraitIcon = TRAIT_ICONS[hero.traitIcon]

  function randomName() {
    const s = SUFFIXES[Math.floor(Math.random() * SUFFIXES.length)]
    setName(`${hero.name} ${s}`)
  }

  function finish(e: FormEvent) {
    e.preventDefault()
    const heroName = name.trim() || hero.defaultName
    update((a) => ({ ...a, heroName, onboarding: 'done' }))
    navigate(changing ? '/app/hero' : '/app/map')
  }

  return (
    <div className="screen created" style={{ backgroundImage: `url(${asset('img/bg-choose.webp')})` }}>
      <header className="onb-top">
        <div className="onb-top__brand">
          <Brand />
        </div>
        {!changing && <Stepper current={4} />}
        <span />
      </header>
      <main className="created__main">
        <div className="created__portrait">
          <div className="created__glow" />
          <img src={heroImage(hero.id)} alt={hero.name} />
          <span className="created__badge">
            <Sparkles size={16} /> Герой создан!
          </span>
        </div>
        <form className="created__card" onSubmit={finish}>
          <p className="created__eyebrow">
            <TraitIcon size={18} /> {hero.trait}
          </p>
          <h1 className="title-serif">Познакомься со своим героем!</h1>
          <p className="created__story">{hero.story}</p>
          <label className="field">
            <span className="field__label">Как зовут твоего героя?</span>
            <span className="field__box">
              <Pencil size={18} />
              <input value={name} onChange={(e) => setName(e.target.value)} maxLength={24} aria-label="Имя героя" />
              <button type="button" className="icon-btn icon-btn--ghost" onClick={randomName} aria-label="Случайное имя" title="Случайное имя">
                <Dices size={20} />
              </button>
            </span>
          </label>
          <div className="created__stats">
            <div>
              <b>{levelOf(account.xp)}</b>
              <span>Уровень</span>
            </div>
            <div>
              <b>{account.coins}</b>
              <span>{changing ? 'Монет' : 'Монет в подарок'}</span>
            </div>
            <div>
              <b>{CITIES.length}</b>
              <span>Городов впереди</span>
            </div>
          </div>
          <button className="btn btn--primary btn--block">
            {changing ? 'Сохранить' : 'Отправиться в путь'} {changing ? <ArrowRight size={20} /> : <Map size={20} />}
          </button>
          {!changing && (
            <button type="button" className="link-btn" onClick={() => navigate('/hero')}>
              Выбрать другого героя
            </button>
          )}
        </form>
      </main>
      <HandNote className="created__note" rotate={-7}>
        千里之行，
        <br />
        始于足下
      </HandNote>
    </div>
  )
}

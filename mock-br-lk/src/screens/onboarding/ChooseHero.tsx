import { ArrowLeft, ArrowRight, ChartNoAxesColumnIncreasing, Gamepad2, Star, Trophy } from 'lucide-react'
import { useState } from 'react'
import { asset } from '../../asset'
import { Brand, FeatureBar, HandNote, Stepper } from '../../components/common'
import { TRAIT_ICONS } from '../../components/icons'
import { getHero, heroImage, HEROES, type HeroId } from '../../domain/heroes'
import { navigate } from '../../router'
import { useAccount } from '../../store'

const FOOTER = [
  { icon: Star, text: <>Уникальные герои<br />и их истории</> },
  { icon: Gamepad2, text: <>Интересные задания<br />и игровые миры</> },
  { icon: ChartNoAxesColumnIncreasing, text: <>Развитие навыков<br />незаметно для тебя</> },
  { icon: Trophy, text: <>Настоящие награды<br />и достижения</> },
]

export function ChooseHero() {
  const [account, update] = useAccount()
  const changing = account.onboarding === 'done'
  const [picked, setPicked] = useState<HeroId>(account.heroId ?? 'fox')

  function confirm() {
    const hero = getHero(picked)
    update((a) => {
      const keepName = a.heroName && a.heroName !== getHero(a.heroId).defaultName
      return {
        ...a,
        heroId: hero.id,
        heroName: keepName ? a.heroName : hero.defaultName,
        onboarding: a.onboarding === 'done' ? 'done' : 'named',
      }
    })
    navigate('/hero-created')
  }

  return (
    <div className="screen choose" style={{ backgroundImage: `url(${asset('img/bg-choose.webp')})` }}>
      <header className="onb-top">
        <div className="onb-top__brand">
          <Brand />
        </div>
        {changing ? (
          <button className="btn btn--ghost" onClick={() => navigate('/app/hero')}>
            <ArrowLeft size={18} /> Назад в кабинет
          </button>
        ) : (
          <Stepper current={3} />
        )}
        <HandNote className="choose__note" rotate={-6}>
          Твой герой.
          <br />
          Твоя история.
          <br />
          Твой Китай!
        </HandNote>
      </header>
      <div className="vertical-scroll" aria-hidden="true">
        <span>选择你的英雄</span>
        <small>Выбери своего героя</small>
      </div>
      <main className="choose__main">
        <h1 className="title-serif">Выбери своего героя</h1>
        <p className="choose__lead">
          Каждый герой уникален. У каждого свой характер и путь.
          <br />
          Кто будет сопровождать тебя в путешествии по Китаю?
        </p>
        <div className="hero-grid" role="radiogroup" aria-label="Герои">
          {HEROES.map((h) => {
            const Icon = TRAIT_ICONS[h.traitIcon]
            const active = h.id === picked
            return (
              <button
                key={h.id}
                role="radio"
                aria-checked={active}
                className={`hero-card ${active ? 'hero-card--active' : ''}`}
                onClick={() => setPicked(h.id)}
                onDoubleClick={confirm}
              >
                <div className="hero-card__img">
                  <img src={heroImage(h.id)} alt={h.name} loading="lazy" />
                  <span className="hero-card__hanzi">{h.hanzi}</span>
                </div>
                <div className="hero-card__body">
                  <h3>{h.name}</h3>
                  <p>{h.description}</p>
                  <Icon className="hero-card__icon" size={30} strokeWidth={1.4} />
                  <span className="hero-card__trait">{h.trait}</span>
                </div>
              </button>
            )
          })}
        </div>
        <button className="btn btn--primary btn--wide" onClick={confirm}>
          Выбрать героя <ArrowRight size={20} />
        </button>
        <p className="choose__hint">Ты всегда сможешь изменить выбор позже</p>
      </main>
      <HandNote className="choose__note-left" rotate={-8}>
        Большие
        <br />
        цели начинаются
        <br />с маленького шага!
      </HandNote>
      <div className="wood-sign wood-sign--small choose__sign" aria-hidden="true">
        Вместе
        <br />к большим
        <br />
        возможностям!
      </div>
      <FeatureBar items={FOOTER} />
    </div>
  )
}

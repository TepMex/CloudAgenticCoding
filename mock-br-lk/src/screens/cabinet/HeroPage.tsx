import { Pencil, RefreshCw, Zap } from 'lucide-react'
import { ProgressBar } from '../../components/common'
import { ITEM_ICONS, TRAIT_ICONS } from '../../components/icons'
import { getHero, heroImage } from '../../domain/heroes'
import { currentCity, heroPower, levelOf, XP_PER_LEVEL, xpInLevel } from '../../domain/progress'
import { getItem, MAX_EQUIPPED } from '../../domain/shop'
import { navigate } from '../../router'
import { useAccount } from '../../store'
import { Page } from './Page'

export function HeroPage() {
  const [account] = useAccount()
  const hero = getHero(account.heroId)
  const TraitIcon = TRAIT_ICONS[hero.traitIcon]
  const slots = Array.from({ length: MAX_EQUIPPED }, (_, i) => account.equipped[i])

  return (
    <Page title="Мой герой">
      <div className="hero-page">
        <div className="hero-page__portrait" style={{ ['--accent' as string]: hero.accent }}>
          <img src={heroImage(hero.id)} alt={hero.name} />
          <span className="hero-page__hanzi">{hero.hanzi}</span>
        </div>
        <section className="panel hero-page__info">
          <p className="created__eyebrow">
            <TraitIcon size={18} /> {hero.trait}
          </p>
          <h2 className="title-serif hero-page__name">
            {account.heroName}
            <a href="#/hero-created" className="icon-btn" aria-label="Переименовать">
              <Pencil size={18} />
            </a>
          </h2>
          <p>{hero.description}</p>
          <p className="muted">{hero.story}</p>
          <div className="hero-page__level">
            <b>Уровень {levelOf(account.xp)}</b>
            <ProgressBar value={xpInLevel(account.xp)} max={XP_PER_LEVEL} label="Опыт" />
            <small>
              {xpInLevel(account.xp)} / {XP_PER_LEVEL} XP до следующего уровня
            </small>
          </div>
          <div className="stat-tiles">
            <div>
              <Zap size={20} />
              <b>{heroPower(account)}</b>
              <span>Мощь героя</span>
            </div>
            <div>
              <b>{account.completedTasks.length}</b>
              <span>Заданий выполнено</span>
            </div>
            <div>
              <b>{currentCity(account).name}</b>
              <span>Сейчас в городе</span>
            </div>
          </div>
          <h3>Снаряжение</h3>
          <div className="slots">
            {slots.map((id, i) => {
              const item = id ? getItem(id) : undefined
              const Icon = item ? ITEM_ICONS[item.icon] : null
              return (
                <a key={i} href="#/app/treasury" className={`slot ${item ? 'slot--filled' : ''}`} title={item?.name ?? 'Пустой слот'}>
                  {Icon ? <Icon size={28} /> : '+'}
                  <small>{item ? `+${item.power}` : 'пусто'}</small>
                </a>
              )
            })}
          </div>
          <button className="btn btn--outline" onClick={() => navigate('/hero')}>
            <RefreshCw size={18} /> Сменить героя
          </button>
        </section>
      </div>
    </Page>
  )
}

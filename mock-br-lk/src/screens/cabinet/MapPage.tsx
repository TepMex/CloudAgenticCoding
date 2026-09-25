import { Check, Gift, Lock, MapPin } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { asset } from '../../asset'
import { ProgressBar } from '../../components/common'
import { CITIES, getCity, ROUTES, type City } from '../../domain/cities'
import { heroImage } from '../../domain/heroes'
import { cityDone, currentCity, isCityComplete, isCityUnlocked, levelOf, nextLockedCity } from '../../domain/progress'
import { tasksForCity } from '../../domain/tasks'
import { useAccount } from '../../store'
import { CityDialog } from './CityDialog'

export function MapPage() {
  const [account] = useAccount()
  const [openCity, setOpenCity] = useState<City | null>(null)
  const level = levelOf(account.xp)
  const here = currentCity(account)
  const next = nextLockedCity(account)
  const done = cityDone(here, account)
  const total = tasksForCity(here).length
  const scroller = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = scroller.current
    const map = el?.firstElementChild as HTMLElement | null
    if (!el || !map || map.offsetWidth <= el.clientWidth) return
    el.scrollLeft = (here.x / 100) * map.offsetWidth - el.clientWidth / 2
  }, [here.x])

  return (
    <div className="map-page" ref={scroller}>
      <div className="map">
        <img className="map__img" src={asset('img/map-china.webp')} alt="Карта Китая" />
        <svg className="map__routes" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
          {ROUTES.map(([a, b]) => {
            const ca = getCity(a)!
            const cb = getCity(b)!
            const open = isCityUnlocked(ca, level) && isCityUnlocked(cb, level)
            return <line key={a + b} x1={ca.x} y1={ca.y} x2={cb.x} y2={cb.y} className={open ? 'route route--open' : 'route'} />
          })}
        </svg>

        <div className="map__title scroll-banner">
          <h1>Путешествие по Китаю</h1>
          <p>Изучай язык. Открывай города. Собирай знания. Становись сильнее!</p>
        </div>

        <div className="map__motto" aria-hidden="true">
          <b>
            学中文
            <br />
            看世界
          </b>
          <span>Китайский язык — больше возможностей в мире!</span>
        </div>

        {CITIES.map((c) => {
          const unlocked = isCityUnlocked(c, level)
          const complete = isCityComplete(c, account)
          const isHere = c.id === here.id
          const cls = isHere ? 'pin pin--here' : complete ? 'pin pin--done' : unlocked ? 'pin pin--open' : 'pin pin--locked'
          return (
            <button key={c.id} className={cls} style={{ left: `${c.x}%`, top: `${c.y}%` }} onClick={() => setOpenCity(c)} aria-label={`${c.name}, уровень ${c.level}${unlocked ? '' : ', закрыт'}`}>
              {isHere ? (
                <span className="pin__marker">
                  <img src={heroImage(account.heroId)} alt="" />
                  <MapPin className="pin__drop" size={22} fill="currentColor" />
                </span>
              ) : (
                <span className="pin__icon">{complete ? <Check size={14} strokeWidth={3} /> : unlocked ? <span className="pin__num">{c.level}</span> : <Lock size={13} />}</span>
              )}
              <span className="pin__label">
                <b>
                  {isHere && <span className="pin__num pin__num--inline">{c.level}</span>}
                  {c.name}
                </b>
                <small>Уровень {c.level}</small>
              </span>
            </button>
          )
        })}

        <div className="map__proverb wood-sign wood-sign--flat" aria-hidden="true">
          «Большое путешествие начинается с первого шага!»
          <b>千里之行，始于足下。</b>
        </div>

        <button className="map__quest" onClick={() => setOpenCity(here)}>
          <span className="map__quest-text">
            {done < total
              ? next && here.level + 1 === next.level
                ? `Пройди ${here.name}, чтобы открыть ${next.name}!`
                : `Продолжай путешествие: ${here.name}`
              : next
                ? `Набери опыт, чтобы открыть ${next.name}`
                : 'Ты открыл весь Китай!'}
          </span>
          <ProgressBar value={done} max={total} label="Прогресс города" />
          <small>
            {done * 25} / {total * 25} XP
          </small>
          <Gift className="map__chest" size={34} />
        </button>

        <div className="map__cta scroll-banner scroll-banner--small" aria-hidden="true">
          Открывай Китай
          <br />
          вместе с BRY!
        </div>
      </div>
      {openCity && <CityDialog city={openCity} onClose={() => setOpenCity(null)} />}
    </div>
  )
}

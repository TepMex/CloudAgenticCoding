import {
  BookOpen,
  CalendarDays,
  ChartColumn,
  ClipboardCheck,
  Gem,
  Map as MapIcon,
  Mail,
  Package,
  Pencil,
  Settings,
  Trophy,
  User,
  Users,
  Zap,
  Compass,
  type LucideIcon,
} from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { asset } from '../../asset'
import { Brand, ProgressBar } from '../../components/common'
import { CoinIcon, GemIcon } from '../../components/icons'
import { claimableCount } from '../../domain/achievements'
import { heroImage } from '../../domain/heroes'
import { heroPower, levelOf, XP_PER_LEVEL, xpInLevel } from '../../domain/progress'
import { MAIL } from '../../domain/social'
import { navigate, useRoute } from '../../router'
import { useAccount } from '../../store'
import { MailDialog } from './MailDialog'
import { SettingsDialog } from './SettingsDialog'

interface NavItem {
  path: string
  label: string
  icon: LucideIcon
  badge?: number
}

export function Shell({ children }: { children: ReactNode }) {
  const [account] = useAccount()
  const route = useRoute()
  const [dialog, setDialog] = useState<'mail' | 'settings' | null>(null)
  const unread = MAIL.filter((m) => !account.readMail.includes(m.id)).length
  const claimable = claimableCount(account)
  const level = levelOf(account.xp)

  const tabs: NavItem[] = [
    { path: '/app/map', label: 'Карта', icon: MapIcon },
    { path: '/app/hero', label: 'Герой', icon: User },
    { path: '/app/tasks', label: 'Задания', icon: ClipboardCheck },
    { path: '/app/stats', label: 'Характеристики', icon: ChartColumn },
    { path: '/app/rewards', label: 'Награды', icon: Trophy, badge: claimable },
    { path: '/app/treasury', label: 'Сокровищница', icon: Package },
  ]
  const side: NavItem[] = [
    { path: '/app/map', label: 'Мой путь', icon: Compass },
    { path: '/app/rewards', label: 'Достижения', icon: Trophy, badge: claimable },
    { path: '/app/collections', label: 'Коллекции', icon: BookOpen },
    { path: '/app/friends', label: 'Друзья', icon: Users },
    { path: '/app/events', label: 'События', icon: CalendarDays },
  ]
  const isActive = (p: string) => route === p || (p === '/app/tasks' && route.startsWith('/app/tasks/'))

  return (
    <div className="cabinet" style={{ ['--cabinet-bg' as string]: `url(${asset('img/map-china.webp')})` }}>
      <header className="topbar">
        <a href="#/app/map" className="topbar__brand">
          <Brand />
        </a>
        <nav className="tabs" aria-label="Разделы">
          {tabs.map(({ path, label, icon: Icon, badge }) => (
            <a key={path} href={`#${path}`} className={`tab ${isActive(path) ? 'tab--active' : ''}`} aria-current={isActive(path) ? 'page' : undefined}>
              <Icon size={24} />
              <span>{label}</span>
              {!!badge && <i className="dot-badge">{badge}</i>}
            </a>
          ))}
        </nav>
        <div className="topbar__right">
          <a href="#/app/treasury" className="currency" title="Монеты">
            <CoinIcon /> <b>{account.coins}</b>
          </a>
          <a href="#/app/treasury" className="currency currency--gem" title="Кристаллы">
            <GemIcon /> <b>{account.gems}</b>
          </a>
          <button className="square-btn" onClick={() => setDialog('mail')} aria-label="Почта">
            <Mail size={22} />
            {unread > 0 && <i className="dot-badge">{unread}</i>}
          </button>
          <button className="square-btn" onClick={() => setDialog('settings')} aria-label="Настройки">
            <Settings size={22} />
          </button>
        </div>
      </header>

      <aside className="sidebar">
        <div className="profile">
          <button className="profile__avatar" onClick={() => navigate('/app/hero')} aria-label="Открыть героя">
            <img src={heroImage(account.heroId)} alt="" />
          </button>
          <div className="profile__info">
            <div className="profile__name">
              {account.heroName}
              <a href="#/hero-created" className="profile__edit" aria-label="Переименовать героя">
                <Pencil size={14} />
              </a>
            </div>
            <div className="profile__level">Уровень {level}</div>
            <ProgressBar value={xpInLevel(account.xp)} max={XP_PER_LEVEL} label="Опыт" />
            <div className="profile__xp">
              {xpInLevel(account.xp)} / {XP_PER_LEVEL} XP
            </div>
          </div>
          <div className="profile__power">
            <Zap size={18} fill="currentColor" /> Мощь героя <b>{heroPower(account)}</b>
          </div>
        </div>
        <nav className="sidenav" aria-label="Мой путь">
          {side.map(({ path, label, icon: Icon, badge }) => (
            <a key={label} href={`#${path}`} className={`sidenav__item ${route === path ? 'sidenav__item--active' : ''}`}>
              <Icon size={26} strokeWidth={1.6} />
              <span>{label}</span>
              {!!badge && <i className="dot-badge">{badge}</i>}
            </a>
          ))}
        </nav>
        <div className="sidebar__student">
          <Gem size={14} /> Ученик: {account.studentName}
        </div>
      </aside>

      <main className="cabinet__main">{children}</main>

      {dialog === 'mail' && <MailDialog onClose={() => setDialog(null)} />}
      {dialog === 'settings' && <SettingsDialog onClose={() => setDialog(null)} />}
    </div>
  )
}

import { X, type LucideIcon } from 'lucide-react'
import { useEffect, type ReactNode } from 'react'
import { asset } from '../asset'

export function Brand({ dark = false, compact = false }: { dark?: boolean; compact?: boolean }) {
  return (
    <div className={`brand ${dark ? 'brand--dark' : ''} ${compact ? 'brand--compact' : ''}`}>
      <img className="brand__logo" src={asset('img/logo-fox.webp')} alt="" />
      <div>
        <div className="brand__name">BRY CHINESE</div>
        {!compact && <div className="brand__tag">воспитание через образование</div>}
      </div>
    </div>
  )
}

export interface Feature {
  icon: LucideIcon
  text: ReactNode
}

export function FeatureBar({ items, note }: { items: Feature[]; note?: string }) {
  return (
    <footer className="feature-bar">
      {items.map(({ icon: Icon, text }, i) => (
        <div className="feature-bar__item" key={i}>
          <Icon size={30} strokeWidth={1.4} />
          <span>{text}</span>
        </div>
      ))}
      {note && <div className="feature-bar__note hand">{note}</div>}
    </footer>
  )
}

export function HandNote({ children, className = '', rotate = -6 }: { children: ReactNode; className?: string; rotate?: number }) {
  return (
    <div className={`hand-note hand ${className}`} style={{ transform: `rotate(${rotate}deg)` }}>
      {children}
      <svg className="hand-note__line" viewBox="0 0 120 10" preserveAspectRatio="none" aria-hidden="true">
        <path d="M2 7 C 30 2, 70 2, 118 5" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" />
      </svg>
    </div>
  )
}

const STEPS = ['Регистрация', 'Приветствие', 'Выбор героя', 'Герой создан']

export function Stepper({ current }: { current: number }) {
  return (
    <ol className="stepper" aria-label="Шаги регистрации">
      {STEPS.map((label, i) => {
        const n = i + 1
        const state = n < current ? 'done' : n === current ? 'active' : 'todo'
        return (
          <li key={label} className={`stepper__step stepper__step--${state}`} aria-current={state === 'active' ? 'step' : undefined}>
            <span className="stepper__num">{n}</span>
            <span className="stepper__label">{label}</span>
            {n < STEPS.length && <span className="stepper__arrow" aria-hidden="true" />}
          </li>
        )
      })}
    </ol>
  )
}

export function Modal({ title, onClose, children, wide = false }: { title: string; onClose: () => void; children: ReactNode; wide?: boolean }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal ${wide ? 'modal--wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <header className="modal__head">
          <h2>{title}</h2>
          <button className="icon-btn" onClick={onClose} aria-label="Закрыть">
            <X size={20} />
          </button>
        </header>
        <div className="modal__body">{children}</div>
      </div>
    </div>
  )
}

export function ProgressBar({ value, max, label }: { value: number; max: number; label?: string }) {
  const pct = max > 0 ? Math.min(100, (value / max) * 100) : 0
  return (
    <div className="pbar" role="progressbar" aria-valuemin={0} aria-valuemax={max} aria-valuenow={value} aria-label={label}>
      <div className="pbar__fill" style={{ width: `${pct}%` }} />
    </div>
  )
}

export type SocialProvider = 'vk' | 'google' | 'apple' | 'yandex'

export const SOCIAL_LABEL: Record<SocialProvider, string> = {
  vk: 'VK ID',
  google: 'Google',
  apple: 'Apple',
  yandex: 'Яндекс ID',
}

export function SocialButtons({ onPick }: { onPick: (p: SocialProvider) => void }) {
  return (
    <div className="social">
      <button type="button" className="social__btn" onClick={() => onPick('vk')} aria-label="Войти через VK ID">
        <span className="social__vk">VK</span>
      </button>
      <button type="button" className="social__btn" onClick={() => onPick('google')} aria-label="Войти через Google">
        <svg width="24" height="24" viewBox="0 0 48 48" aria-hidden="true">
          <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3 0 5.8 1.1 7.9 3l5.7-5.7C34 6.1 29.3 4 24 4 13 4 4 13 4 24s9 20 20 20 20-9 20-20c0-1.2-.1-2.4-.4-3.5z" />
          <path fill="#FF3D00" d="m6.3 14.7 6.6 4.8C14.7 15.1 19 12 24 12c3 0 5.8 1.1 7.9 3l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" />
          <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-8l-6.5 5C9.5 39.6 16.2 44 24 44z" />
          <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.2-.1-2.4-.4-3.5z" />
        </svg>
      </button>
      <button type="button" className="social__btn" onClick={() => onPick('apple')} aria-label="Войти через Apple">
        <svg width="22" height="22" viewBox="0 0 24 24" aria-hidden="true">
          <path
            fill="#111"
            d="M16.4 12.6c0-2.5 2-3.7 2.1-3.7-1.2-1.7-3-1.9-3.6-2-1.5-.2-3 .9-3.8.9-.8 0-2-.9-3.3-.9-1.7 0-3.3 1-4.2 2.5-1.8 3.1-.5 7.7 1.3 10.2.8 1.2 1.8 2.6 3.1 2.5 1.2 0 1.7-.8 3.2-.8s1.9.8 3.2.8c1.3 0 2.2-1.2 3-2.4.9-1.4 1.3-2.7 1.3-2.8 0 0-2.3-.9-2.3-4.3zM14 5.2c.7-.8 1.1-1.9 1-3-1 0-2.1.7-2.8 1.5-.6.7-1.2 1.8-1 2.9 1.1.1 2.1-.6 2.8-1.4z"
          />
        </svg>
      </button>
      <button type="button" className="social__btn" onClick={() => onPick('yandex')} aria-label="Войти через Яндекс ID">
        <span className="social__ya">Я</span>
      </button>
    </div>
  )
}

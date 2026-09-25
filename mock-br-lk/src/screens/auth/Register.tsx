import { ArrowRight, Eye, EyeOff, LockKeyhole, Mail, Sprout, User } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { SocialButtons } from '../../components/common'
import { register } from '../../db'
import { navigate } from '../../router'
import { useStore } from '../../store'
import { AuthLayout } from './AuthLayout'
import { useSocialSignIn } from './social'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function Register() {
  const { setAccount } = useStore()
  const social = useSocialSignIn()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [agree, setAgree] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [exists, setExists] = useState(false)
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setExists(false)
    if (!name.trim()) return setError('Напиши, как зовут ученика')
    if (!EMAIL_RE.test(email.trim())) return setError('Проверь e-mail родителя')
    if (!agree) return setError('Нужно согласие с условиями')
    setError(null)
    setBusy(true)
    const r = await register(email, name)
    setBusy(false)
    if (!r.ok) {
      setExists(true)
      return setError('Такой e-mail уже зарегистрирован.')
    }
    setAccount(r.account)
    navigate('/welcome')
  }

  return (
    <AuthLayout
      topRight={
        <>
          Уже есть аккаунт? <a href="#/login">Войти</a>
        </>
      }
    >
      <h1 className="auth__title">Создай свой аккаунт</h1>
      <p className="auth__lead">
        Начни увлекательное путешествие
        <br />в мир китайского языка вместе с BRY!
      </p>
      <form className="form" onSubmit={onSubmit} noValidate>
        <label className="field">
          <span className="field__box">
            <User size={20} />
            <input placeholder="Имя ученика" value={name} onChange={(e) => setName(e.target.value)} autoComplete="given-name" />
          </span>
          <span className="field__hint">Например, Алексей</span>
        </label>
        <label className="field">
          <span className="field__box">
            <Mail size={20} />
            <input type="email" placeholder="E-mail родителя" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
          </span>
          <span className="field__hint">Например, name@mail.ru</span>
        </label>
        <label className="field">
          <span className="field__box">
            <LockKeyhole size={20} />
            <input
              type={showPwd ? 'text' : 'password'}
              placeholder="Пароль"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="new-password"
            />
            <button type="button" className="icon-btn icon-btn--ghost" onClick={() => setShowPwd((v) => !v)} aria-label={showPwd ? 'Скрыть пароль' : 'Показать пароль'}>
              {showPwd ? <EyeOff size={20} /> : <Eye size={20} />}
            </button>
          </span>
        </label>
        {error && (
          <div className="form__error" role="alert">
            {error} {exists && <a href="#/login">Войти в аккаунт</a>}
          </div>
        )}
        <button className="btn btn--primary btn--block" disabled={busy}>
          Зарегистрироваться <ArrowRight size={20} />
        </button>
        <label className="check">
          <input type="checkbox" checked={agree} onChange={(e) => setAgree(e.target.checked)} />
          <span>
            Я согласен(а) с условиями использования
            <br />и политикой конфиденциальности
          </span>
        </label>
      </form>
      <div className="divider">или</div>
      <SocialButtons onPick={social} />
      <p className="auth__foot">
        <Sprout size={18} /> BRY CHINESE — это игра, знания и большие возможности!
      </p>
      <p className="auth__join">Присоединяйся!</p>
    </AuthLayout>
  )
}

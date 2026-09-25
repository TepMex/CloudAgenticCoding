import { ArrowRight, Eye, EyeOff, Info, LockKeyhole, Mail } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { SocialButtons } from '../../components/common'
import { useToast } from '../../components/Toast'
import { signIn } from '../../db'
import { navigate } from '../../router'
import { routeForAccount } from '../../routes'
import { useStore } from '../../store'
import { AuthLayout } from './AuthLayout'
import { useSocialSignIn } from './social'

export function Login() {
  const { setAccount } = useStore()
  const toast = useToast()
  const social = useSocialSignIn()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!email.includes('@')) return setError('Введи e-mail родителя')
    const { account, created } = await signIn(email)
    setAccount(account)
    toast(created ? `Создали новый аккаунт для ${account.email}` : `С возвращением, ${account.studentName}!`, created ? 'info' : 'ok')
    navigate(routeForAccount(account))
  }

  return (
    <AuthLayout
      topRight={
        <>
          Ещё нет аккаунта? <a href="#/register">Регистрация</a>
        </>
      }
    >
      <h1 className="auth__title">С возвращением!</h1>
      <p className="auth__lead">
        Твой герой уже ждёт.
        <br />
        Продолжим путешествие по Китаю?
      </p>
      <form className="form" onSubmit={onSubmit} noValidate>
        <label className="field">
          <span className="field__box">
            <Mail size={20} />
            <input type="email" placeholder="E-mail родителя" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" autoFocus />
          </span>
        </label>
        <label className="field">
          <span className="field__box">
            <LockKeyhole size={20} />
            <input
              type={showPwd ? 'text' : 'password'}
              placeholder="Пароль"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
            />
            <button type="button" className="icon-btn icon-btn--ghost" onClick={() => setShowPwd((v) => !v)} aria-label={showPwd ? 'Скрыть пароль' : 'Показать пароль'}>
              {showPwd ? <EyeOff size={20} /> : <Eye size={20} />}
            </button>
          </span>
        </label>
        {error && (
          <div className="form__error" role="alert">
            {error}
          </div>
        )}
        <button className="btn btn--primary btn--block">
          Войти <ArrowRight size={20} />
        </button>
        <p className="demo-note">
          <Info size={16} /> Это макет: подойдёт любой пароль, а новый e-mail сразу получит аккаунт.
        </p>
      </form>
      <div className="divider">или</div>
      <SocialButtons onPick={social} />
      <p className="auth__join">
        <a href="#/register">Создать новый аккаунт</a>
      </p>
    </AuthLayout>
  )
}

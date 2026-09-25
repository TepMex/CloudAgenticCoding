import { LogOut, RotateCcw, Volume2 } from 'lucide-react'
import { useState } from 'react'
import { Modal } from '../../components/common'
import { useToast } from '../../components/Toast'
import { resetProgress, signOut } from '../../db'
import { navigate } from '../../router'
import { useAccount, useStore } from '../../store'

export function SettingsDialog({ onClose }: { onClose: () => void }) {
  const [account, update] = useAccount()
  const { setAccount } = useStore()
  const toast = useToast()
  const [name, setName] = useState(account.studentName)
  const [confirmReset, setConfirmReset] = useState(false)

  async function logout() {
    await signOut()
    setAccount(null)
    navigate('/login')
  }

  async function reset() {
    setAccount(await resetProgress(account))
    toast('Прогресс сброшен', 'info')
    onClose()
    navigate('/app/map')
  }

  return (
    <Modal title="Настройки" onClose={onClose}>
      <div className="settings">
        <label className="field">
          <span className="field__label">Имя ученика</span>
          <span className="field__box">
            <input value={name} onChange={(e) => setName(e.target.value)} onBlur={() => name.trim() && update((a) => ({ ...a, studentName: name.trim() }))} />
          </span>
        </label>
        <label className="field">
          <span className="field__label">E-mail родителя</span>
          <span className="field__box field__box--readonly">{account.email}</span>
        </label>
        <label className="switch">
          <input type="checkbox" checked={account.sound} onChange={(e) => update((a) => ({ ...a, sound: e.target.checked }))} />
          <span className="switch__track" />
          <Volume2 size={18} /> Озвучивать китайские слова
        </label>
        <p className="settings__meta">Аккаунт создан {new Date(account.createdAt).toLocaleDateString('ru-RU')}. Данные хранятся только в этом браузере (IndexedDB).</p>
        <div className="settings__actions">
          {confirmReset ? (
            <>
              <span>Точно сбросить весь прогресс?</span>
              <button className="btn btn--danger" onClick={reset}>
                Да, сбросить
              </button>
              <button className="btn btn--ghost" onClick={() => setConfirmReset(false)}>
                Отмена
              </button>
            </>
          ) : (
            <button className="btn btn--ghost" onClick={() => setConfirmReset(true)}>
              <RotateCcw size={18} /> Сбросить прогресс
            </button>
          )}
          <button className="btn btn--outline" onClick={logout}>
            <LogOut size={18} /> Выйти
          </button>
        </div>
      </div>
    </Modal>
  )
}

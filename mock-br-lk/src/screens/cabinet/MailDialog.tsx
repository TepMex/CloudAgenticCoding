import { MailOpen } from 'lucide-react'
import { useState } from 'react'
import { Modal } from '../../components/common'
import { MAIL } from '../../domain/social'
import { useAccount } from '../../store'

export function MailDialog({ onClose }: { onClose: () => void }) {
  const [account, update] = useAccount()
  const [openId, setOpenId] = useState<string | null>(null)

  function open(id: string) {
    setOpenId(id === openId ? null : id)
    if (!account.readMail.includes(id)) update((a) => ({ ...a, readMail: [...a.readMail, id] }))
  }

  return (
    <Modal title="Почта" onClose={onClose}>
      <ul className="mail">
        {MAIL.map((m) => {
          const unread = !account.readMail.includes(m.id)
          return (
            <li key={m.id} className={`mail__item ${unread ? 'mail__item--unread' : ''}`}>
              <button className="mail__head" onClick={() => open(m.id)} aria-expanded={openId === m.id}>
                <MailOpen size={20} />
                <span className="mail__subject">
                  <b>{m.subject}</b>
                  <small>
                    {m.from} · {m.date}
                  </small>
                </span>
                {unread && <i className="dot" aria-label="Не прочитано" />}
              </button>
              {openId === m.id && <p className="mail__body">{m.body}</p>}
            </li>
          )
        })}
      </ul>
    </Modal>
  )
}

import { Check, Gift } from 'lucide-react'
import { ProgressBar } from '../../components/common'
import { ACHIEVEMENT_ICONS, CoinIcon, GemIcon } from '../../components/icons'
import { useToast } from '../../components/Toast'
import { ACHIEVEMENTS, claimAchievement, isAchieved } from '../../domain/achievements'
import { canOpenDailyChest, DAILY_CHEST_COINS, openDailyChest } from '../../domain/progress'
import { useAccount } from '../../store'
import { Page } from './Page'

export function RewardsPage() {
  const [account, update] = useAccount()
  const toast = useToast()
  const chestReady = canOpenDailyChest(account)

  function openChest() {
    update((a) => openDailyChest(a))
    toast(`Сундук открыт: +${DAILY_CHEST_COINS} монет!`)
  }

  function claim(id: string, gems: number) {
    update((a) => claimAchievement(a, id))
    toast(`Награда получена: +${gems} кристалл.`)
  }

  return (
    <Page title="Награды и достижения" subtitle="Выполняй задания, открывай города и забирай кристаллы за достижения.">
      <section className={`panel chest ${chestReady ? 'chest--ready' : ''}`}>
        <Gift size={48} className="chest__icon" />
        <div>
          <h2>Ежедневный сундук</h2>
          <p className="muted">{chestReady ? `Внутри ${DAILY_CHEST_COINS} монет. Заходи каждый день!` : 'Сегодня сундук уже открыт. Возвращайся завтра!'}</p>
        </div>
        <button className="btn btn--primary" disabled={!chestReady} onClick={openChest}>
          {chestReady ? (
            <>
              Открыть <CoinIcon size={18} />
            </>
          ) : (
            'Открыт'
          )}
        </button>
      </section>
      <div className="ach-grid">
        {ACHIEVEMENTS.map((ach) => {
          const Icon = ACHIEVEMENT_ICONS[ach.icon]
          const [cur, target] = ach.progress(account)
          const done = isAchieved(account, ach)
          const claimed = account.claimedAchievements.includes(ach.id)
          return (
            <div key={ach.id} className={`panel ach ${done ? 'ach--done' : ''} ${claimed ? 'ach--claimed' : ''}`}>
              <span className="ach__icon">
                <Icon size={28} />
              </span>
              <b>{ach.title}</b>
              <small>{ach.description}</small>
              <ProgressBar value={cur} max={target} label={ach.title} />
              <div className="ach__foot">
                <span>
                  {cur} / {target}
                </span>
                {claimed ? (
                  <span className="ach__claimed">
                    <Check size={16} /> Получено
                  </span>
                ) : (
                  <button className="btn btn--sm btn--primary" disabled={!done} onClick={() => claim(ach.id, ach.gems)}>
                    +{ach.gems} <GemIcon size={16} />
                  </button>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </Page>
  )
}

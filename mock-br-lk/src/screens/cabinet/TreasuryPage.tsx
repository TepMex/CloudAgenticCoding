import { Check } from 'lucide-react'
import { CoinIcon, GemIcon, ITEM_ICONS } from '../../components/icons'
import { useToast } from '../../components/Toast'
import { buyItem } from '../../domain/progress'
import { MAX_EQUIPPED, SHOP_ITEMS, type ShopItem } from '../../domain/shop'
import { toggleEquip } from '../../domain/progress'
import { useAccount } from '../../store'
import { Page } from './Page'

export function TreasuryPage() {
  const [account, update] = useAccount()
  const toast = useToast()

  function buy(item: ShopItem) {
    const r = buyItem(account, item.id)
    if (r === 'funds') return toast(item.currency === 'coins' ? 'Не хватает монет — выполни пару заданий!' : 'Не хватает кристаллов — получи их за достижения!', 'warn')
    if (typeof r === 'string') return
    update(() => toggleEquip(r, item.id))
    toast(`«${item.name}» теперь у тебя!`)
  }

  return (
    <Page title="Сокровищница" subtitle={`Покупай снаряжение за монеты и кристаллы. Надетые предметы (до ${MAX_EQUIPPED}) увеличивают мощь героя.`}>
      <div className="shop-grid">
        {SHOP_ITEMS.map((item) => {
          const Icon = ITEM_ICONS[item.icon]
          const owned = account.inventory.includes(item.id)
          const equipped = account.equipped.includes(item.id)
          const affordable = account[item.currency] >= item.price
          return (
            <div key={item.id} className={`panel shop-item ${equipped ? 'shop-item--equipped' : ''}`}>
              <span className={`shop-item__icon shop-item__icon--${item.currency}`}>
                <Icon size={34} />
              </span>
              <b>{item.name}</b>
              <small>{item.description}</small>
              <span className="shop-item__power">+{item.power} к мощи</span>
              {owned ? (
                <button className={`btn btn--sm ${equipped ? 'btn--primary' : 'btn--outline'}`} onClick={() => update((a) => toggleEquip(a, item.id))}>
                  {equipped ? (
                    <>
                      <Check size={16} /> Надето
                    </>
                  ) : (
                    'Надеть'
                  )}
                </button>
              ) : (
                <button className={`btn btn--sm ${affordable ? 'btn--gold' : 'btn--ghost'}`} onClick={() => buy(item)}>
                  {item.price} {item.currency === 'coins' ? <CoinIcon size={16} /> : <GemIcon size={16} />}
                </button>
              )}
            </div>
          )
        })}
      </div>
    </Page>
  )
}

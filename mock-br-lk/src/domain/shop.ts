export type ItemIcon = 'hat' | 'lantern' | 'fan' | 'kite' | 'compass' | 'scroll' | 'brush' | 'drum'

export interface ShopItem {
  id: string
  name: string
  description: string
  icon: ItemIcon
  price: number
  currency: 'coins' | 'gems'
  power: number
}

export const SHOP_ITEMS: ShopItem[] = [
  { id: 'compass', name: 'Компас путешественника', description: 'Никогда не даст сбиться с пути.', icon: 'compass', price: 40, currency: 'coins', power: 5 },
  { id: 'lantern', name: 'Фонарик 福', description: 'Освещает дорогу и приносит удачу.', icon: 'lantern', price: 60, currency: 'coins', power: 8 },
  { id: 'fan', name: 'Шёлковый веер', description: 'Помогает сохранять спокойствие на экзамене.', icon: 'fan', price: 80, currency: 'coins', power: 10 },
  { id: 'hat', name: 'Бумажный зонтик', description: 'Защищает от солнца и дождя в долгих странствиях.', icon: 'hat', price: 100, currency: 'coins', power: 12 },
  { id: 'brush', name: 'Кисть каллиграфа', description: 'Каждая черта получается ровной и красивой.', icon: 'brush', price: 150, currency: 'coins', power: 15 },
  { id: 'kite', name: 'Воздушный змей', description: 'Поднимает настроение выше облаков.', icon: 'kite', price: 2, currency: 'gems', power: 20 },
  { id: 'drum', name: 'Праздничный барабан', description: 'Звучит, когда ты получаешь новый уровень.', icon: 'drum', price: 3, currency: 'gems', power: 25 },
  { id: 'scroll', name: 'Свиток мудрости', description: 'Хранит все выученные слова.', icon: 'scroll', price: 5, currency: 'gems', power: 40 },
]

export const MAX_EQUIPPED = 3

export function getItem(id: string): ShopItem | undefined {
  return SHOP_ITEMS.find((i) => i.id === id)
}

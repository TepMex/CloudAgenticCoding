import { asset } from '../asset'

export type HeroId = 'fox' | 'panda' | 'tiger' | 'dragon' | 'crane' | 'monkey'

export interface Hero {
  id: HeroId
  name: string
  /** Default display name offered on the "hero created" step. */
  defaultName: string
  hanzi: string
  description: string
  trait: string
  traitIcon: 'compass' | 'bamboo' | 'mountain' | 'cloud' | 'lotus' | 'sun'
  story: string
  accent: string
}

export const HEROES: Hero[] = [
  {
    id: 'fox',
    name: 'Лис',
    defaultName: 'Лис Линь',
    hanzi: '狐',
    description: 'Любопытный, умный, всегда готов к новым открытиям.',
    trait: 'Исследует мир',
    traitIcon: 'compass',
    story:
      'Лис Линь обошёл все рынки Пекина и знает, где продают самые вкусные баоцзы. Он верит, что каждое новое слово — это ключ к новой двери.',
    accent: '#e8742a',
  },
  {
    id: 'panda',
    name: 'Панда',
    defaultName: 'Панда Бао',
    hanzi: '熊',
    description: 'Спокойный, добрый, настойчивый. Сила в постоянстве.',
    trait: 'Достигает целей',
    traitIcon: 'bamboo',
    story:
      'Панда Бао вырос в бамбуковых лесах Сычуани. Он учится понемногу, но каждый день — и поэтому всегда доходит до цели.',
    accent: '#3f8a4f',
  },
  {
    id: 'tiger',
    name: 'Тигр',
    defaultName: 'Тигр Ху',
    hanzi: '虎',
    description: 'Смелый, решительный, всегда идёт вперёд.',
    trait: 'Преодолевает трудности',
    traitIcon: 'mountain',
    story:
      'Тигр Ху не боится сложных тонов и длинных иероглифов. Если задача трудная — значит, она стоит того!',
    accent: '#c0392b',
  },
  {
    id: 'dragon',
    name: 'Дракон',
    defaultName: 'Дракон Лун',
    hanzi: '龙',
    description: 'Мудрый, вдохновляющий, верит в твой потенциал.',
    trait: 'Открывает новые горизонты',
    traitIcon: 'cloud',
    story:
      'Дракон Лун живёт среди облаков над горами Хуаншань. Он помнит древние легенды и с радостью делится ими с учениками.',
    accent: '#2e9e6a',
  },
  {
    id: 'crane',
    name: 'Журавль',
    defaultName: 'Журавль Хэ',
    hanzi: '鹤',
    description: 'Внимательный, гармоничный, ценит красоту знаний.',
    trait: 'Стремится к совершенству',
    traitIcon: 'lotus',
    story:
      'Журавль Хэ любит каллиграфию: каждую черту иероглифа он выводит так, будто рисует картину.',
    accent: '#3a78b5',
  },
  {
    id: 'monkey',
    name: 'Обезьяна',
    defaultName: 'Обезьяна Укун',
    hanzi: '猴',
    description: 'Весёлый, изобретательный, находит решения.',
    trait: 'Делает обучение увлекательным',
    traitIcon: 'sun',
    story:
      'Обезьяна Укун превращает любое упражнение в игру. С ним даже повторение слов — настоящее приключение.',
    accent: '#b7862f',
  },
]

export function getHero(id: HeroId | null | undefined): Hero {
  return HEROES.find((h) => h.id === id) ?? HEROES[0]
}

export function heroImage(id: HeroId | null | undefined): string {
  return asset(`img/hero-${getHero(id).id}.webp`)
}

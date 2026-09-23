import raw from '../../data/elements.json'

export type CatalogCharacter = {
  hanzi: string
  pinyin: string
  ru: string
}

export type ElementCategory = {
  id: string
  element: string
  radical: string
  radical_no: number
  name_ru: string
  description: string
  count: number
  characters: CatalogCharacter[]
}

type CatalogFile = {
  meta: { title: string; total_characters: number; note: string }
  categories: ElementCategory[]
}

const catalog = raw as CatalogFile

export const categories: ElementCategory[] = catalog.categories

export function categoryById(id: string): ElementCategory | undefined {
  return categories.find((category) => category.id === id)
}

export function foldPinyin(value: string): string {
  return value.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()
}

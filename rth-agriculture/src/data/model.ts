import rawCharacters from './rth.json'

export type CharacterDefinition = {
  id: string
  hanzi: string
  keyword: { ru: string; en: string }
  frame: number
  fieldId: string
  strokeCount: number
  writingDataId: string
}

export type FieldDefinition = {
  id: string
  rthListId: string
  title: string
  characterIds: string[]
  characters: CharacterDefinition[]
  plantStyle: string
  seed: number
  neighbors: string[]
  row: number
  column: number
}

type RawCharacter = {
  frame: number
  hanzi: string
  keyword: string
  rth_list: string
  lesson: number
  strokes: number
  rth_list_name: string
}

const russianKeywords: Record<string, string> = {
  one: 'один', two: 'два', three: 'три', four: 'четыре', five: 'пять', six: 'шесть',
  seven: 'семь', eight: 'восемь', nine: 'девять', ten: 'десять', mouth: 'рот', day: 'день',
  month: 'месяц', 'rice field': 'рисовое поле', eye: 'глаз', ancient: 'древний', leaf: 'лист',
  'I (literary)': 'я (книжн.)', companion: 'товарищ', bright: 'яркий', sing: 'петь',
  sparkling: 'сверкающий', goods: 'товары', prosperous: 'процветающий', early: 'рано',
  'rising sun': 'восходящее солнце', generation: 'поколение', stomach: 'желудок',
  daybreak: 'рассвет', concave: 'вогнутый', convex: 'выпуклый', oneself: 'сам', white: 'белый',
  hundred: 'сто', middle: 'середина', thousand: 'тысяча', above: 'наверху', below: 'внизу',
  left: 'слева', right: 'справа', large: 'большой', small: 'маленький', water: 'вода',
  fire: 'огонь', tree: 'дерево', person: 'человек', woman: 'женщина', child: 'ребёнок',
}

const plantStyles = [
  'цветущая слива', 'золотой бамбук', 'лотосы', 'синие гортензии', 'хризантемы',
  'серебряный тростник', 'пионы', 'чайные кусты', 'магнолии', 'огненные лилии',
  'нефритовый мох', 'звёздные колокольчики',
]

const source = rawCharacters as RawCharacter[]
const listNames = [...new Set(source.map((item) => item.rth_list))]
const fieldIdByList = new Map(listNames.map((name, index) => [name, `field-${String(index + 1).padStart(3, '0')}`]))

export const characters: CharacterDefinition[] = source.map((item) => {
  const fieldId = fieldIdByList.get(item.rth_list)!
  return {
    id: `rsh-${String(item.frame).padStart(4, '0')}`,
    hanzi: item.hanzi,
    keyword: {
      ru: russianKeywords[item.keyword] ?? item.keyword,
      en: item.keyword,
    },
    frame: item.frame,
    fieldId,
    strokeCount: item.strokes,
    writingDataId: item.hanzi,
  }
})

const charactersByField = new Map<string, CharacterDefinition[]>()
for (const character of characters) {
  const entries = charactersByField.get(character.fieldId) ?? []
  entries.push(character)
  charactersByField.set(character.fieldId, entries)
}

export const fields: FieldDefinition[] = listNames.map((list, index) => {
  const id = fieldIdByList.get(list)!
  const row = Math.floor(index / 11)
  const column = index % 11
  const neighborIndexes = [index - 1, index + 1, index - 11, index + 11].filter((candidate) => {
    if (candidate < 0 || candidate >= listNames.length) return false
    if (Math.abs((candidate % 11) - column) > 1) return false
    return true
  })
  const fieldCharacters = charactersByField.get(id) ?? []

  return {
    id,
    rthListId: list,
    title: list.replace(/^RSH-L\d+:\s*/, ''),
    characterIds: fieldCharacters.map((character) => character.id),
    characters: fieldCharacters,
    plantStyle: plantStyles[index % plantStyles.length],
    seed: (index + 1) * 2654435761,
    neighbors: neighborIndexes.map((neighbor) => `field-${String(neighbor + 1).padStart(3, '0')}`),
    row,
    column,
  }
})

export const fieldById = new Map(fields.map((field) => [field.id, field]))
export const characterById = new Map(characters.map((character) => [character.id, character]))


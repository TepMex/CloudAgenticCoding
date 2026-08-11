import rawCharacters from './rth.json'
import {
  cellsForPlotIndex,
  gardenRegions,
  regionForCell,
  type GardenRegion,
  type GridCell,
} from './mapLayout'

export type CharacterDefinition = {
  id: string
  hanzi: string
  keyword: { ru: string; en: string }
  frame: number
  plotId: string
  strokeCount: number
  writingDataId: string
}

export type PlotDefinition = {
  id: string
  sourceRthListId: string
  sourceHalf: 0 | 1
  gardenId: string
  characterIds: string[]
  characters: CharacterDefinition[]
  cells: GridCell[]
  neighbors: string[]
  seed: number
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

type PlotDraft = Omit<PlotDefinition, 'characters' | 'characterIds' | 'neighbors'> & {
  rawCharacters: RawCharacter[]
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

const source = rawCharacters as RawCharacter[]
export const sourceRthListIds = [...new Set(source.map((item) => item.rth_list))]

/** Stable v1 IDs are retained only for lossless save migration. */
export const legacyFieldIdBySourceRthListId = new Map(
  sourceRthListIds.map((listId, index) => [listId, `field-${String(index + 1).padStart(3, '0')}`]),
)

const rawCharactersByList = new Map<string, RawCharacter[]>()
for (const item of source) {
  const list = rawCharactersByList.get(item.rth_list) ?? []
  list.push(item)
  rawCharactersByList.set(item.rth_list, list)
}

const drafts: PlotDraft[] = []
for (const [listIndex, listId] of sourceRthListIds.entries()) {
  const listCharacters = [...(rawCharactersByList.get(listId) ?? [])].sort((left, right) => left.frame - right.frame)
  const splitIndex = Math.ceil(listCharacters.length / 2)
  for (const sourceHalf of [0, 1] as const) {
    const plotIndex = drafts.length
    const cells = cellsForPlotIndex(plotIndex)
    drafts.push({
      id: `plot-${String(plotIndex + 1).padStart(3, '0')}`,
      sourceRthListId: listId,
      sourceHalf,
      gardenId: regionForCell(cells[0]!).id,
      rawCharacters: sourceHalf === 0 ? listCharacters.slice(0, splitIndex) : listCharacters.slice(splitIndex),
      cells,
      seed: (plotIndex + 1) * 2654435761,
    })
  }
  // This makes an accidental reordering above fail loudly during development.
  if (drafts.length !== (listIndex + 1) * 2) throw new Error('Each RTH list must create exactly two plots')
}

const characterByPlotAndFrame = new Map<string, CharacterDefinition>()
for (const draft of drafts) {
  for (const item of draft.rawCharacters) {
    const character: CharacterDefinition = {
      id: `rsh-${String(item.frame).padStart(4, '0')}`,
      hanzi: item.hanzi,
      keyword: { ru: russianKeywords[item.keyword] ?? item.keyword, en: item.keyword },
      frame: item.frame,
      plotId: draft.id,
      strokeCount: item.strokes,
      writingDataId: item.hanzi,
    }
    characterByPlotAndFrame.set(`${draft.id}:${item.frame}`, character)
  }
}

function buildNeighbors(draftsToLink: readonly PlotDraft[]): Map<string, string[]> {
  const plotIdByCell = new Map<string, string>()
  for (const draft of draftsToLink) {
    for (const cell of draft.cells) {
      const key = `${cell.x}:${cell.y}`
      if (plotIdByCell.has(key)) throw new Error(`Overlapping map cell ${key}`)
      plotIdByCell.set(key, draft.id)
    }
  }

  const neighbors = new Map(draftsToLink.map((draft) => [draft.id, new Set<string>()]))
  for (const draft of draftsToLink) {
    for (const cell of draft.cells) {
      for (const [deltaX, deltaY] of [[-1, 0], [1, 0], [0, -1], [0, 1]] as const) {
        const adjacentId = plotIdByCell.get(`${cell.x + deltaX}:${cell.y + deltaY}`)
        if (adjacentId && adjacentId !== draft.id) {
          neighbors.get(draft.id)!.add(adjacentId)
          neighbors.get(adjacentId)!.add(draft.id)
        }
      }
    }
  }
  return new Map([...neighbors].map(([id, ids]) => [id, [...ids].sort()]))
}

const neighborIdsByPlotId = buildNeighbors(drafts)
export const plots: PlotDefinition[] = drafts.map(({ rawCharacters, ...draft }) => {
  const characters = rawCharacters.map((item) => characterByPlotAndFrame.get(`${draft.id}:${item.frame}`)!)
  return {
    ...draft,
    characters,
    characterIds: characters.map((character) => character.id),
    neighbors: neighborIdsByPlotId.get(draft.id)!,
  }
})

export const characters = [...plots]
  .flatMap((plot) => plot.characters)
  .sort((left, right) => left.frame - right.frame)

export const plotById = new Map(plots.map((plot) => [plot.id, plot]))
export const characterById = new Map(characters.map((character) => [character.id, character]))
export const plotIdsByLegacyFieldId = new Map<string, [string, string]>(
  sourceRthListIds.map((listId, index) => [
    legacyFieldIdBySourceRthListId.get(listId)!,
    [plots[index * 2]!.id, plots[index * 2 + 1]!.id],
  ]),
)

export { gardenRegions }
export type { GardenRegion }

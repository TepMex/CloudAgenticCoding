export type Region = 'cn' | 'kz' | 'ru'

export type Place = {
  id: string
  label: string
  lat: number
  lon: number
  region: Region
  stems: string[]
}

export const PLACES: Place[] = [
  { id: 'isilkul', label: 'Исилькуль', lat: 54.909, lon: 71.261, region: 'ru', stems: ['исилькул'] },
  { id: 'petropavl', label: 'Петропавловск', lat: 54.873, lon: 69.143, region: 'kz', stems: ['петропавловск'] },
  { id: 'astana', label: 'Астана', lat: 51.128, lon: 71.43, region: 'kz', stems: ['астан'] },
  { id: 'urumqi', label: 'Урумчи', lat: 43.825, lon: 87.617, region: 'cn', stems: ['урумчи'] },
  { id: 'turpan', label: 'Турфан', lat: 42.951, lon: 89.19, region: 'cn', stems: ['турфан'] },
  { id: 'hami', label: 'Хами', lat: 42.819, lon: 93.515, region: 'cn', stems: ['хами'] },
  { id: 'jiayuguan', label: 'Цзяюйгуань', lat: 39.773, lon: 98.289, region: 'cn', stems: ['цзяюйгуан'] },
  { id: 'xining', label: 'Синин', lat: 36.617, lon: 101.778, region: 'cn', stems: ['синин'] },
  { id: 'chengdu', label: 'Чэнду', lat: 30.66, lon: 104.064, region: 'cn', stems: ['чэнду'] },
  { id: 'sanxingdui', label: 'Сансиньдуй', lat: 31, lon: 104.2, region: 'cn', stems: ['сансиньду', 'саньсинду'] },
  { id: 'dujiangyan', label: 'Дуцзянъян', lat: 30.988, lon: 103.647, region: 'cn', stems: ['дуцзянъян'] },
  { id: 'xichang', label: 'Сичан', lat: 27.895, lon: 102.264, region: 'cn', stems: ['сичан'] },
  { id: 'lushan', label: 'Лушань', lat: 27.835, lon: 102.265, region: 'cn', stems: ['лушан'] },
  { id: 'kunming', label: 'Куньмин', lat: 25.038, lon: 102.718, region: 'cn', stems: ['куньмин'] },
  { id: 'chengjiang', label: 'Чэнцзян', lat: 24.675, lon: 102.904, region: 'cn', stems: ['чэнцзян', 'чжэнцзян'] },
  { id: 'guiyang', label: 'Гуйян', lat: 26.648, lon: 106.63, region: 'cn', stems: ['гуйян', 'гуйан'] },
  { id: 'chongqing', label: 'Чунцин', lat: 29.563, lon: 106.551, region: 'cn', stems: ['чунцин'] },
]

const BY_ID = new Map(PLACES.map((place) => [place.id, place]))

/**
 * Stops for each day, in travel order. A stop is drawn only when its name
 * actually occurs in that day's title or prose, so a passing mention on
 * another day does not light the city up.
 */
export const DAY_STOPS: Record<string, string[]> = {
  '2026-09-05': ['isilkul', 'petropavl', 'astana'],
  '2026-09-06': ['astana', 'urumqi'],
  '2026-09-07': ['urumqi'],
  '2026-09-08': ['turpan'],
  '2026-09-09': ['turpan', 'hami'],
  '2026-09-10': ['jiayuguan'],
  '2026-09-11': ['jiayuguan', 'xining'],
  '2026-09-12': ['xining', 'chengdu'],
  '2026-09-13': ['chengdu'],
  '2026-09-14': ['chengdu'],
  '2026-09-15': ['chengdu', 'sanxingdui'],
  '2026-09-16': ['chengdu'],
  '2026-09-17': ['dujiangyan', 'chengdu', 'xichang'],
  '2026-09-18': ['xichang'],
  '2026-09-19': ['xichang', 'lushan'],
  '2026-09-20': ['xichang'],
  '2026-09-21': ['xichang', 'kunming'],
  '2026-09-22': ['kunming'],
  '2026-09-23': ['kunming'],
  '2026-09-24': ['kunming', 'chengjiang'],
  '2026-09-25': ['kunming', 'guiyang', 'chongqing'],
  '2026-09-26': ['chongqing'],
  '2026-09-27': ['chongqing', 'urumqi', 'astana'],
  '2026-09-28': ['astana'],
  '2026-09-29': ['astana'],
}

export function placeById(id: string): Place {
  const place = BY_ID.get(id)
  if (!place) throw new Error(`Unknown place ${id}`)
  return place
}

export function mentioned(text: string, place: Place): boolean {
  const hay = text.toLocaleLowerCase('ru-RU')
  return place.stems.some((stem) => new RegExp(`(?:^|[^а-яё])${stem}`).test(hay))
}

export function stopsFromTitle(placeLine: string): Place[] {
  const hay = placeLine.toLocaleLowerCase('ru-RU')
  const hits: { index: number; place: Place }[] = []
  for (const place of PLACES) {
    let index = -1
    for (const stem of place.stems) {
      const match = new RegExp(`(?:^|[^а-яё])${stem}`).exec(hay)
      if (match && (index < 0 || match.index < index)) index = match.index
    }
    if (index >= 0) hits.push({ index, place })
  }
  hits.sort((a, b) => a.index - b.index)
  const stops: Place[] = []
  for (const hit of hits) {
    if (stops.at(-1)?.id !== hit.place.id) stops.push(hit.place)
  }
  return stops
}

export function stopsForDay(dayId: string, placeLine: string, paragraphs: string[]): Place[] {
  const text = [placeLine, ...paragraphs].join('\n')
  const plan = DAY_STOPS[dayId]
  if (plan) {
    const found = plan.map(placeById).filter((place) => mentioned(text, place))
    if (found.length > 0) return found
  }
  return stopsFromTitle(placeLine)
}

export type NeighborId = 'kz' | 'ru' | 'mn'

export function neighborsFor(stops: Place[]): NeighborId[] {
  const neighbors: NeighborId[] = []
  if (stops.some((stop) => stop.region === 'kz' || stop.region === 'ru')) neighbors.push('kz')
  if (stops.some((stop) => stop.region === 'ru')) neighbors.push('ru')
  if (stops.some((stop) => stop.id === 'hami')) neighbors.push('mn')
  return neighbors
}

import { parseCsv } from './csv'

export const SHEET_CSV_URL =
  'https://docs.google.com/spreadsheets/d/e/2PACX-1vSTD5LYtSb-z1PUme0rtCdGbiGatjkCtEz-xIDTwKXnn3sg4-rSFf5NOym4vVJajMRdd8EOKtjHtf88/pub?gid=1068391721&single=true&output=csv'

export const SHEET_PAGE_URL =
  'https://docs.google.com/spreadsheets/d/e/2PACX-1vSTD5LYtSb-z1PUme0rtCdGbiGatjkCtEz-xIDTwKXnn3sg4-rSFf5NOym4vVJajMRdd8EOKtjHtf88/pubhtml?gid=1068391721&single=true'

export type Episode = {
  id: string
  number: number
  title: string
  paragraphs: string[]
}

export type ListMode = 'all' | 'with-context'

export class SheetError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'SheetError'
  }
}

export function contextParagraphs(context: string): string[] {
  return context
    .replace(/\r\n/g, '\n')
    .replace(/\r/g, '\n')
    .split(/\n{2,}/)
    .map((part) => part.replace(/[ \t]*\n[ \t]*/g, ' ').replace(/[ \t]{2,}/g, ' ').trim())
    .filter((part) => part.length > 0)
}

export function episodesFromCsv(text: string): Episode[] {
  const rows = parseCsv(text)
  if (rows.length === 0) return []
  const header = rows[0].map((cell) => cell.trim().toLocaleLowerCase('ru'))
  const numberIdx = columnIndex(header, 'номер', 0)
  const titleIdx = columnIndex(header, 'назван', 1)
  const contextIdx = columnIndex(header, 'контекст', 2)
  const episodes: Array<Omit<Episode, 'id'>> = []

  for (const row of rows.slice(1)) {
    const rawNumber = (row[numberIdx] ?? '').trim()
    if (!/^\d+$/.test(rawNumber)) continue
    const title = (row[titleIdx] ?? '').trim() || 'Без названия'
    const paragraphs = contextParagraphs(row[contextIdx] ?? '')
    episodes.push({ number: Number(rawNumber), title, paragraphs })
  }

  return assignIds(episodes)
}

export function assignIds(episodes: Array<Omit<Episode, 'id'>>): Episode[] {
  const counts = new Map<number, number>()
  for (const episode of episodes) counts.set(episode.number, (counts.get(episode.number) ?? 0) + 1)
  const seen = new Map<number, number>()
  return episodes.map((episode) => {
    const n = (seen.get(episode.number) ?? 0) + 1
    seen.set(episode.number, n)
    const id = (counts.get(episode.number) ?? 1) > 1 ? `ep-${episode.number}-${n}` : `ep-${episode.number}`
    return { ...episode, id }
  })
}

export function selectEpisodes(episodes: Episode[], query: string, mode: ListMode): Episode[] {
  const base = mode === 'with-context' ? episodes.filter((episode) => episode.paragraphs.length > 0) : episodes
  const needle = query.trim().toLocaleLowerCase('ru')
  if (!needle) return base
  return base.filter((episode) => {
    const haystack = `${episode.number}\n${episode.title}\n${episode.paragraphs.join('\n')}`.toLocaleLowerCase('ru')
    return haystack.includes(needle)
  })
}

export function ruCount(n: number, one: string, few: string, many: string): string {
  const abs = Math.abs(n) % 100
  const last = abs % 10
  const word = abs > 10 && abs < 20 ? many : last === 1 ? one : last >= 2 && last <= 4 ? few : many
  return `${n} ${word}`
}

export async function loadEpisodes(fetchImpl: typeof fetch = fetch, url = SHEET_CSV_URL): Promise<Episode[]> {
  let response: Response
  try {
    response = await fetchImpl(url, { cache: 'no-store' })
  } catch {
    throw new SheetError('Не удалось прочитать таблицу.')
  }
  if (!response.ok) throw new SheetError('Не удалось прочитать таблицу.')
  const text = await response.text()
  if (text.trimStart().startsWith('<')) throw new SheetError('Не удалось прочитать таблицу.')
  const episodes = episodesFromCsv(text)
  if (episodes.length === 0) throw new SheetError('В таблице нет выпусков.')
  return episodes
}

function columnIndex(header: string[], token: string, fallback: number): number {
  const found = header.findIndex((cell) => cell.includes(token))
  return found >= 0 ? found : fallback
}

import { readWorkbook, type WorkbookRow, type WorkbookSheet } from './xlsx'

export const SHEET_XLSX_URL =
  'https://docs.google.com/spreadsheets/d/e/2PACX-1vTigrK-gA08YChI5-2ZmPEWbQXT9T5RxoJcUOZtyAZPwA90MxLvALM81I0Q0VFnzlr15XlMg2_ujagq/pub?output=xlsx'

export const SHEET_PAGE_URL =
  'https://docs.google.com/spreadsheets/d/e/2PACX-1vTigrK-gA08YChI5-2ZmPEWbQXT9T5RxoJcUOZtyAZPwA90MxLvALM81I0Q0VFnzlr15XlMg2_ujagq/pubhtml'

const HIDDEN = new Set(['не отмечено', 'не оценено', 'не подтверждена'])

const RU: Record<string, string> = {
  а: 'a', б: 'b', в: 'v', г: 'g', д: 'd', е: 'e', ё: 'e', ж: 'zh', з: 'z', и: 'i', й: 'y',
  к: 'k', л: 'l', м: 'm', н: 'n', о: 'o', п: 'p', р: 'r', с: 's', т: 't', у: 'u', ф: 'f',
  х: 'kh', ц: 'ts', ч: 'ch', ш: 'sh', щ: 'shch', ъ: '', ы: 'y', ь: '', э: 'e', ю: 'yu', я: 'ya',
}

export type Chip = {
  label: string
  tone: 'status' | 'rating' | 'priority' | 'meta'
}

export type Note = {
  label: string
  text: string
}

export type FooterItem = {
  label: string
  value: string
  href?: string
}

export type Entry = {
  id: string
  title: string
  href?: string
  person: string
  original: string
  lead: string
  notes: Note[]
  comment: string
  chips: Chip[]
  footer: FooterItem[]
  facet: string
  status: string
  search: string
}

export type Counted = {
  value: string
  count: number
}

export type Section = {
  id: string
  name: string
  facetLabel: string | null
  facets: Counted[]
  statuses: Counted[]
  entries: Entry[]
}

export type EntryGroup = {
  label: string
  entries: Entry[]
}

export class SheetError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'SheetError'
  }
}

export function slugify(name: string): string {
  let out = ''
  for (const char of name.trim().toLocaleLowerCase('ru')) out += RU[char] ?? char
  const slug = out.replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
  return slug || 'sheet'
}

export function fold(value: string): string {
  return value.toLocaleLowerCase('ru').replaceAll('ё', 'е')
}

export function ruCount(n: number, one: string, few: string, many: string): string {
  const abs = Math.abs(n) % 100
  const last = abs % 10
  const word = abs > 10 && abs < 20 ? many : last === 1 ? one : last >= 2 && last <= 4 ? few : many
  return `${n} ${word}`
}

export function catalogFromWorkbook(bytes: Uint8Array): Section[] {
  const used = new Map<string, number>()
  return readWorkbook(bytes).map((sheet) => {
    const base = slugify(sheet.name)
    const seen = used.get(base) ?? 0
    used.set(base, seen + 1)
    const id = seen === 0 ? base : `${base}-${seen + 1}`
    return sectionFromSheet(sheet, id)
  })
}

export function visibleEntries(section: Section, query: string, facet: string, status: string): Entry[] {
  const needle = fold(query.trim())
  return section.entries.filter((entry) => {
    if (facet && entry.facet !== facet) return false
    if (status && entry.status !== status) return false
    if (needle && !entry.search.includes(needle)) return false
    return true
  })
}

export function groupEntries(entries: Entry[], facetLabel: string | null): EntryGroup[] {
  if (!facetLabel) return [{ label: '', entries }]
  const order: string[] = []
  const grouped = new Map<string, Entry[]>()
  const unlabeled: Entry[] = []
  for (const entry of entries) {
    if (!entry.facet) {
      unlabeled.push(entry)
      continue
    }
    const list = grouped.get(entry.facet)
    if (list) list.push(entry)
    else {
      grouped.set(entry.facet, [entry])
      order.push(entry.facet)
    }
  }
  const groups = order.map((label) => ({ label, entries: grouped.get(label) ?? [] }))
  if (unlabeled.length > 0) groups.push({ label: groups.length > 0 ? 'Без пометы' : '', entries: unlabeled })
  return groups
}

export function otherSectionMatches(
  sections: Section[],
  currentId: string,
  query: string,
): { id: string; name: string; count: number }[] {
  const needle = fold(query.trim())
  if (!needle) return []
  return sections
    .filter((section) => section.id !== currentId)
    .map((section) => ({
      id: section.id,
      name: section.name,
      count: section.entries.filter((entry) => entry.search.includes(needle)).length,
    }))
    .filter((section) => section.count > 0)
}

export function queryCount(section: Section, query: string): number {
  const needle = fold(query.trim())
  if (!needle) return section.entries.length
  return section.entries.filter((entry) => entry.search.includes(needle)).length
}

export async function loadCatalog(fetchImpl: typeof fetch = fetch, url = SHEET_XLSX_URL): Promise<Section[]> {
  let response: Response
  try {
    response = await fetchImpl(url, { cache: 'no-store' })
  } catch {
    throw new SheetError('Не удалось прочитать таблицу.')
  }
  if (!response.ok) throw new SheetError('Не удалось прочитать таблицу.')
  const bytes = new Uint8Array(await response.arrayBuffer())
  if (bytes.length < 4 || bytes[0] !== 0x50 || bytes[1] !== 0x4b) throw new SheetError('Не удалось прочитать таблицу.')
  let sections: Section[]
  try {
    sections = catalogFromWorkbook(bytes)
  } catch {
    throw new SheetError('Не удалось прочитать таблицу.')
  }
  if (sections.length === 0 || sections.every((section) => section.entries.length === 0)) {
    throw new SheetError('В таблице нет рекомендаций.')
  }
  return sections
}

function sectionFromSheet(sheet: WorkbookSheet, id: string): Section {
  const header = sheet.rows[0]
  const headers = (header?.cells ?? []).map((cell) => cell.text.trim())
  const data = sheet.rows.slice(1)
  const titleAt = titleColumn(headers)
  const personAt = headers.findIndex((name, index) => index !== titleAt && /автор|исполнител|ведущ/i.test(name))
  const originalAt = headers.findIndex((name) => /международ/i.test(name))
  const facetAt = facetColumn(headers, data)
  let leadAt = -1
  let leadScore = 99
  headers.forEach((name, index) => {
    const rank = leadRank(name)
    if (rank > 0 && rank < leadScore && index !== titleAt) {
      leadScore = rank
      leadAt = index
    }
  })
  const entries: Entry[] = []
  for (const row of data) {
    const entry = entryFromRow(row, headers, id, { titleAt, personAt, originalAt, facetAt, leadAt })
    if (entry) entries.push(entry)
  }
  return {
    id,
    name: sheet.name,
    facetLabel: facetAt >= 0 ? headers[facetAt] ?? null : null,
    facets: counts(entries, (entry) => entry.facet),
    statuses: counts(entries, (entry) => entry.status),
    entries,
  }
}

function entryFromRow(
  row: WorkbookRow,
  headers: string[],
  sectionId: string,
  cols: { titleAt: number; personAt: number; originalAt: number; facetAt: number; leadAt: number },
): Entry | null {
  if (row.cells.every((cell) => !cell.text.trim() && !cell.href)) return null
  const titleCell = row.cells[cols.titleAt]
  const title = titleCell?.text.trim() || 'Без названия'
  const linkAt = headers.findIndex((name) => /ссылк/i.test(name))
  const linkCell = linkAt >= 0 ? row.cells[linkAt] : undefined
  const href = httpUrl(linkCell?.href) || httpUrl(linkCell?.text) || httpUrl(titleCell?.href)

  const chips: Chip[] = []
  const notes: Note[] = []
  const footer: FooterItem[] = []
  let person = ''
  let original = ''
  let lead = ''
  let comment = ''
  let facet = ''
  let status = ''

  for (let index = 0; index < headers.length; index += 1) {
    const header = headers[index] ?? ''
    if (!header || index === cols.titleAt) continue
    const cell = row.cells[index] ?? { text: '' }
    const text = cell.text.trim()
    const lower = header.toLocaleLowerCase('ru')
    if (/^id$/i.test(header)) continue
    if (index === cols.personAt) {
      person = text
      continue
    }
    if (index === cols.originalAt) {
      original = text && text !== title ? text : ''
      continue
    }
    if (index === cols.facetAt) facet = text
    if (index === cols.leadAt) {
      lead = hidden(text) ? '' : text
      continue
    }
    if (index === cols.facetAt) continue
    if (!text && !cell.href) continue
    if (/коммент/.test(lower)) {
      if (!hidden(text)) comment = text
      continue
    }
    if (/ссылк/.test(lower)) continue
    if (/статус/.test(lower)) {
      status = text
      if (!hidden(text)) chips.push({ label: text, tone: 'status' })
      continue
    }
    if (/оценка|понравил/.test(lower)) {
      if (!hidden(text)) chips.push({ label: text, tone: 'rating' })
      continue
    }
    if (/дата/.test(lower)) {
      if (!hidden(text)) footer.push({ label: header, value: text })
      continue
    }
    if (/длительност/.test(lower) && !/проверк/.test(lower)) {
      if (!hidden(text)) footer.push({ label: header, value: text })
      continue
    }
    if (/проверк|публикац|подборк|источник/.test(lower)) {
      if (!hidden(text)) footer.push({ label: header, value: text })
      continue
    }
    if (/приоритет/.test(lower)) {
      if (hidden(text)) continue
      if (text.length <= 48) chips.push({ label: text, tone: 'priority' })
      else notes.push({ label: header, text })
      continue
    }
    if (/язык|формат|раздел|тип/.test(lower) && text.length <= 48 && !hidden(text)) {
      chips.push({ label: text, tone: 'meta' })
      continue
    }
    if (!hidden(text)) notes.push({ label: header, text })
  }

  if (href) footer.push({ label: 'Ссылка', value: 'Открыть', href })

  const search = fold(
    [
      title,
      person,
      original,
      lead,
      comment,
      facet,
      status,
      ...notes.flatMap((note) => [note.label, note.text]),
      ...chips.map((chip) => chip.label),
      ...footer.map((item) => `${item.label} ${item.value}`),
      ...row.cells.map((cell) => `${cell.text} ${cell.href ?? ''}`),
    ].join('\n'),
  )

  return {
    id: `${sectionId}-${row.index}`,
    title,
    href,
    person,
    original,
    lead,
    notes,
    comment,
    chips,
    footer,
    facet,
    status,
    search,
  }
}

function titleColumn(headers: string[]): number {
  const rules = [/лекци/i, /^трек$/i, /назван/i]
  for (const rule of rules) {
    const index = headers.findIndex((header) => rule.test(header.trim()))
    if (index >= 0) return index
  }
  const fallback = headers.findIndex((header) => header.trim() && !/^id$/i.test(header.trim()))
  return fallback >= 0 ? fallback : 0
}

function facetColumn(headers: string[], rows: WorkbookRow[]): number {
  const tokens = ['раздел', 'язык', 'формат', 'тематик', 'тип', 'приоритет']
  for (const token of tokens) {
    const index = headers.findIndex((header) => {
      const name = header.toLocaleLowerCase('ru')
      if (!name.includes(token)) return false
      if (token === 'приоритет' && name.includes('уточн')) return false
      return true
    })
    if (index < 0) continue
    const values = new Set<string>()
    let tooLong = false
    for (const row of rows) {
      const value = row.cells[index]?.text.trim() ?? ''
      if (!value) continue
      if (value.length > 48) tooLong = true
      values.add(value)
    }
    if (tooLong) continue
    if (values.size >= 2 && values.size <= 14) return index
  }
  return -1
}

function leadRank(header: string): number {
  const name = header.toLocaleLowerCase('ru')
  if (name.includes('тема и причина')) return 1
  if (name.includes('почему')) return 2
  if (name.includes('причина')) return 3
  if (name.includes('основан')) return 4
  if (name.includes('тематик')) return 5
  if (name === 'тема' || name.startsWith('тема ')) return 6
  return 0
}

function counts(entries: Entry[], pick: (entry: Entry) => string): Counted[] {
  const order: string[] = []
  const map = new Map<string, number>()
  for (const entry of entries) {
    const value = pick(entry).trim()
    if (!value) continue
    if (!map.has(value)) order.push(value)
    map.set(value, (map.get(value) ?? 0) + 1)
  }
  return order.map((value) => ({ value, count: map.get(value) ?? 0 }))
}

function hidden(value: string): boolean {
  return HIDDEN.has(value.trim().toLocaleLowerCase('ru'))
}

function httpUrl(value: string | undefined): string | undefined {
  if (!value) return undefined
  try {
    const url = new URL(value.trim())
    if (url.protocol === 'http:' || url.protocol === 'https:') return url.toString()
  } catch {
    return undefined
  }
  return undefined
}

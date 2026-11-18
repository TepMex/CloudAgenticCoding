import { splitGlosses, uniqueGlosses } from './gloss'
import { readWorkbook, type WorkbookSheet, WorkbookError } from './xlsx'

export const SHEET_NAME = '12_NewVocabCurrent'

export const DEFAULT_WORKBOOK_URL =
  'https://docs.google.com/spreadsheets/d/e/2PACX-1vRstuLuxoVAzP2ZB-b_rBjRU3-GnyHAJ4TTfeQ-hEmDyJ89VDf0iWlabwTdo30YWxvwY725A7q0vIF-/pub?output=xlsx'

export type VocabEntry = {
  row: number
  expression: string
  pinyin: string
  meaning: string
  glosses: string[]
  unitType: string
  occurrences: string
  fragmentId: string
  ankiStatus: string
  example: string
  notes: string
}

export type VocabErrorCode = 'download' | 'workbook' | 'sheet' | 'columns'

export class VocabError extends Error {
  readonly code: VocabErrorCode

  constructor(code: VocabErrorCode) {
    super(code)
    this.name = 'VocabError'
    this.code = code
  }
}

export function vocabErrorText(error: unknown): string {
  if (error instanceof VocabError) {
    switch (error.code) {
      case 'download':
        return 'Не удалось скачать таблицу.'
      case 'workbook':
        return 'Файл таблицы не читается.'
      case 'sheet':
        return `В таблице нет листа ${SHEET_NAME}.`
      case 'columns':
        return 'На листе нет колонок expression и meaning_ru.'
      default:
        return 'Не удалось открыть таблицу.'
    }
  }
  return 'Не удалось открыть таблицу.'
}

export function entriesFromWorkbook(bytes: Uint8Array): VocabEntry[] {
  let sheets: WorkbookSheet[]
  try {
    sheets = readWorkbook(bytes)
  } catch (error) {
    if (error instanceof WorkbookError) throw new VocabError('workbook')
    throw error
  }
  const sheet = sheets.find((item) => item.name === SHEET_NAME)
  if (!sheet) throw new VocabError('sheet')
  return entriesFromSheet(sheet)
}

export function entriesFromSheet(sheet: WorkbookSheet): VocabEntry[] {
  const header = sheet.rows[0]
  if (!header) return []
  const columns = new Map<string, number>()
  header.cells.forEach((cell, index) => {
    const name = cell.text.trim()
    if (name && !columns.has(name)) columns.set(name, index)
  })
  if (!columns.has('expression') || !columns.has('meaning_ru')) throw new VocabError('columns')

  const cell = (row: WorkbookSheet['rows'][number], name: string) => {
    const index = columns.get(name)
    if (index === undefined) return ''
    return (row.cells[index]?.text ?? '').trim()
  }

  const entries: VocabEntry[] = []
  for (const row of sheet.rows.slice(1)) {
    const expression = cell(row, 'expression')
    if (!expression) continue
    const meaning = cell(row, 'meaning_ru')
    entries.push({
      row: row.index,
      expression,
      pinyin: cell(row, 'pinyin'),
      meaning,
      glosses: uniqueGlosses(splitGlosses(meaning)),
      unitType: cell(row, 'unit_type'),
      occurrences: normalizeCount(cell(row, 'occurrences_in_fragment')),
      fragmentId: cell(row, 'fragment_id'),
      ankiStatus: cell(row, 'anki_status'),
      example: cell(row, 'context_example'),
      notes: cell(row, 'notes'),
    })
  }
  return entries
}

export function fragmentChoices(entries: VocabEntry[]): { id: string; count: number }[] {
  const counts = new Map<string, number>()
  for (const entry of entries) counts.set(entry.fragmentId, (counts.get(entry.fragmentId) ?? 0) + 1)
  return [...counts.entries()].map(([id, count]) => ({ id, count }))
}

export function entriesForFragment(entries: VocabEntry[], fragmentId: string | null): VocabEntry[] {
  if (fragmentId === null) return entries
  return entries.filter((entry) => entry.fragmentId === fragmentId)
}

export function visibleExample(entry: VocabEntry): string {
  const example = entry.example.trim()
  if (!example || example === entry.expression) return ''
  return example
}

export async function loadEntries(url: string, signal?: AbortSignal): Promise<VocabEntry[]> {
  let response: Response
  try {
    response = await fetch(url, { signal, cache: 'no-store' })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new VocabError('download')
  }
  if (!response.ok) throw new VocabError('download')
  return entriesFromWorkbook(new Uint8Array(await response.arrayBuffer()))
}

export function normalizeCount(value: string): string {
  const text = value.trim()
  if (!text) return ''
  const number = Number(text)
  if (!Number.isFinite(number)) return text
  if (Math.abs(number - Math.round(number)) < 1e-8) return String(Math.round(number))
  return String(number)
}

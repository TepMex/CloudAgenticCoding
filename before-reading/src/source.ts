import { DEFAULT_WORKBOOK_URL, type VocabEntry } from './vocab'

const CACHE_KEY = 'before-reading:entries:v1'

export function resolveWorkbookUrl(search: string, fallback = DEFAULT_WORKBOOK_URL): string {
  const raw = new URLSearchParams(search).get('src')?.trim()
  if (!raw) return fallback
  try {
    const url = new URL(raw)
    if (url.protocol !== 'https:') return fallback
    return url.toString()
  } catch {
    return fallback
  }
}

export function workbookHtmlUrl(xlsxUrl: string): string {
  const url = new URL(xlsxUrl)
  if (url.searchParams.get('output') === 'xlsx') url.searchParams.set('output', 'html')
  return url.toString()
}

export function sameEntries(left: readonly VocabEntry[], right: readonly VocabEntry[]): boolean {
  return JSON.stringify(left) === JSON.stringify(right)
}

export function readCachedEntries(storage: Pick<Storage, 'getItem'>, url: string): VocabEntry[] | null {
  const raw = storage.getItem(CACHE_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as { url?: unknown; entries?: unknown }
    if (parsed.url !== url || !Array.isArray(parsed.entries)) return null
    const entries = parsed.entries.filter(isEntry)
    return entries.length > 0 ? entries : null
  } catch {
    return null
  }
}

export function writeCachedEntries(storage: Pick<Storage, 'setItem'>, url: string, entries: readonly VocabEntry[]): void {
  if (entries.length === 0) return
  try {
    storage.setItem(CACHE_KEY, JSON.stringify({ url, entries }))
  } catch {
    // The session still works if the browser refuses to store the sheet.
  }
}

function isEntry(value: unknown): value is VocabEntry {
  if (!value || typeof value !== 'object') return false
  const entry = value as Partial<VocabEntry>
  return (
    typeof entry.row === 'number' &&
    typeof entry.expression === 'string' &&
    entry.expression.length > 0 &&
    typeof entry.pinyin === 'string' &&
    typeof entry.meaning === 'string' &&
    Array.isArray(entry.glosses) &&
    entry.glosses.every((gloss) => typeof gloss === 'string') &&
    typeof entry.unitType === 'string' &&
    typeof entry.occurrences === 'string' &&
    typeof entry.fragmentId === 'string' &&
    typeof entry.ankiStatus === 'string' &&
    typeof entry.example === 'string' &&
    typeof entry.notes === 'string'
  )
}

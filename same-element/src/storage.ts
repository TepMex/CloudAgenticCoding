const PICK_PREFIX = 'same-element:pick:'
const RESULTS_KEY = 'same-element:results'

export type CharacterMemory = { clean: number; missed: number }

function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return fallback
    return JSON.parse(raw) as T
  } catch {
    return fallback
  }
}

export function loadPick(categoryId: string): string[] {
  const value = readJson<unknown>(PICK_PREFIX + categoryId, [])
  return Array.isArray(value) ? value.filter((item) => typeof item === 'string') : []
}

export function savePick(categoryId: string, hanzi: string[]): void {
  localStorage.setItem(PICK_PREFIX + categoryId, JSON.stringify(hanzi))
}

export function loadMemory(): Record<string, CharacterMemory> {
  const value = readJson<unknown>(RESULTS_KEY, {})
  if (!value || typeof value !== 'object') return {}
  return value as Record<string, CharacterMemory>
}

export function rememberResult(hanzi: string, clean: boolean): Record<string, CharacterMemory> {
  const memory = loadMemory()
  const previous = memory[hanzi] ?? { clean: 0, missed: 0 }
  memory[hanzi] = clean
    ? { clean: previous.clean + 1, missed: previous.missed }
    : { clean: previous.clean, missed: previous.missed + 1 }
  localStorage.setItem(RESULTS_KEY, JSON.stringify(memory))
  return memory
}

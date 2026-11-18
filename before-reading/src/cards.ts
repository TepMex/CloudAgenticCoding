import type { VocabEntry } from './vocab'

export const AGAIN_GAP = 3

export type Direction = 'expression' | 'meaning'

export type Card = {
  id: string
  direction: Direction
  prompt: string
  entry: VocabEntry
}

export type Grade = 'again' | 'good'

export function buildCards(entries: VocabEntry[]): Card[] {
  const cards: Card[] = []
  for (const entry of entries) {
    cards.push({
      id: `${entry.row}:expression`,
      direction: 'expression',
      prompt: entry.expression,
      entry,
    })
    entry.glosses.forEach((gloss, index) => {
      cards.push({
        id: `${entry.row}:meaning:${index}`,
        direction: 'meaning',
        prompt: gloss,
        entry,
      })
    })
  }
  return cards
}

export function shuffleCards<T>(items: readonly T[], random: () => number): T[] {
  const next = items.slice()
  for (let i = next.length - 1; i > 0; i -= 1) {
    const j = Math.floor(random() * (i + 1))
    const swap = next[i]
    next[i] = next[j] as T
    next[j] = swap as T
  }
  return next
}

/** Again comes back a few cards later. Good leaves the session. */
export function gradeQueue(queue: readonly Card[], grade: Grade, gap = AGAIN_GAP): Card[] {
  const current = queue[0]
  if (!current) return []
  const rest = queue.slice(1)
  if (grade === 'good') return rest
  const at = Math.min(Math.max(gap, 0), rest.length)
  return [...rest.slice(0, at), current, ...rest.slice(at)]
}

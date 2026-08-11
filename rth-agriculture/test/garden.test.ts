import { describe, expect, test } from 'bun:test'
import { plotInfection } from '../src/garden'
import type { CardState } from '../src/learning'

const plot = {
  characters: [
    { id: 'new-character', strokeCount: 2 },
    { id: 'reviewed-character', strokeCount: 6 },
  ],
}

describe('plotInfection', () => {
  test('treats every new plot as fully overgrown without an access flag', () => {
    expect(plotInfection(plot, {})).toBe(1)
  })

  test('uses due stroke weight and not plot accessibility', () => {
    const cards = {
      'reviewed-character': { due: new Date('2100-01-01T00:00:00Z') } as CardState,
    }
    expect(plotInfection(plot, cards, new Date('2026-08-08T00:00:00Z'))).toBe(0.25)
  })

  test('marks a plot as clean when none of its characters are due', () => {
    const futureCard = { due: new Date('2100-01-01T00:00:00Z') } as CardState
    expect(plotInfection(plot, { 'new-character': futureCard, 'reviewed-character': futureCard }, new Date('2026-08-08T00:00:00Z'))).toBe(0)
  })
})

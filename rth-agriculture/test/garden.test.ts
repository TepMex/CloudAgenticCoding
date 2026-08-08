import { describe, expect, test } from 'bun:test'
import { fieldInfection } from '../src/garden'
import type { CardState } from '../src/learning'

const field = {
  characters: [
    { id: 'new-character', strokeCount: 2 },
    { id: 'reviewed-character', strokeCount: 6 },
  ],
}

describe('fieldInfection', () => {
  test('treats every never-cleared field as fully overgrown without an access flag', () => {
    expect(fieldInfection(field, {})).toBe(1)
  })

  test('uses due stroke weight and not field accessibility', () => {
    const cards = {
      'reviewed-character': { due: new Date('2100-01-01T00:00:00Z') } as CardState,
    }

    expect(fieldInfection(field, cards, new Date('2026-08-08T00:00:00Z'))).toBe(0.25)
  })

  test('marks a field as cleared when none of its characters are due', () => {
    const futureCard = { due: new Date('2100-01-01T00:00:00Z') } as CardState
    const cards = {
      'new-character': futureCard,
      'reviewed-character': futureCard,
    }

    expect(fieldInfection(field, cards, new Date('2026-08-08T00:00:00Z'))).toBe(0)
  })
})

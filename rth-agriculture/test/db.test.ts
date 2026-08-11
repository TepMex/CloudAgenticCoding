import { describe, expect, test } from 'bun:test'
import { migrateV1Save, type SaveGameV1 } from '../src/db'
import type { CardState } from '../src/learning'

describe('v1 save migration', () => {
  test('preserves review history and maps old field progress to both halves', () => {
    const card = { due: new Date('2099-01-01T00:00:00Z') } as CardState
    const event = { id: 'event-1', characterId: 'rsh-0001', timestamp: 1, rating: 'good' as const, totalMistakes: 0, hintUsed: false, durationMs: 100, inputDevice: 'mouse' as const }
    const v1: SaveGameV1 = {
      id: 'main', version: 1, unlockedFieldIds: ['field-001'], masteredFieldIds: ['field-002'],
      seenCharacterIds: ['rsh-0001'], cards: { 'rsh-0001': card }, reviewEvents: [event], updatedAt: 42,
    }
    const migrated = migrateV1Save(v1)
    expect(migrated.version).toBe(2)
    expect(migrated.unlockedPlotIds).toEqual(['plot-001', 'plot-002'])
    expect(migrated.masteredPlotIds).toEqual(['plot-003', 'plot-004'])
    expect(migrated.cards).toBe(v1.cards)
    expect(migrated.seenCharacterIds).toBe(v1.seenCharacterIds)
    expect(migrated.reviewEvents).toBe(v1.reviewEvents)
  })
})

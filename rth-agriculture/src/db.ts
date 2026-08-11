import Dexie, { type EntityTable } from 'dexie'
import { plotIdsByLegacyFieldId } from './data/model'
import type { CardState, ReviewEvent } from './learning'

export type SaveGame = {
  id: 'main'
  version: 2
  unlockedPlotIds: string[]
  masteredPlotIds: string[]
  seenCharacterIds: string[]
  cards: Record<string, CardState>
  reviewEvents: ReviewEvent[]
  updatedAt: number
}

export type SaveGameV1 = {
  id: 'main'
  version: 1
  unlockedFieldIds: string[]
  masteredFieldIds: string[]
  seenCharacterIds: string[]
  cards: Record<string, CardState>
  reviewEvents: ReviewEvent[]
  updatedAt: number
}

type StoredSave = SaveGame | SaveGameV1

function migrateFieldIds(fieldIds: readonly string[]): string[] {
  return [...new Set(fieldIds.flatMap((fieldId) => plotIdsByLegacyFieldId.get(fieldId) ?? []))]
}

/** Pure export keeps the data-loss-sensitive migration independently testable. */
export function migrateV1Save(save: SaveGameV1): SaveGame {
  return {
    id: 'main',
    version: 2,
    unlockedPlotIds: migrateFieldIds(save.unlockedFieldIds),
    masteredPlotIds: migrateFieldIds(save.masteredFieldIds),
    seenCharacterIds: save.seenCharacterIds,
    cards: save.cards,
    reviewEvents: save.reviewEvents,
    updatedAt: save.updatedAt,
  }
}

function isV1Save(save: StoredSave): save is SaveGameV1 {
  return save.version === 1
}

const database = new Dexie('memory-garden') as Dexie & {
  saves: EntityTable<StoredSave, 'id'>
}

database.version(1).stores({ saves: 'id, version, updatedAt' })
database.version(2).stores({ saves: 'id, version, updatedAt' }).upgrade((transaction) => {
  return transaction.table('saves').toCollection().modify((stored: StoredSave) => {
    if (!isV1Save(stored)) return
    const migrated = migrateV1Save(stored)
    Object.assign(stored, migrated)
    delete (stored as Partial<SaveGameV1>).unlockedFieldIds
    delete (stored as Partial<SaveGameV1>).masteredFieldIds
  })
})

export const initialSave: SaveGame = {
  id: 'main',
  version: 2,
  unlockedPlotIds: ['plot-001'],
  masteredPlotIds: [],
  seenCharacterIds: [],
  cards: {},
  reviewEvents: [],
  updatedAt: Date.now(),
}

export async function loadSave(): Promise<SaveGame> {
  const stored = await database.saves.get('main')
  if (!stored) return structuredClone(initialSave)
  if (!isV1Save(stored)) return stored
  const migrated = migrateV1Save(stored)
  await database.saves.put(migrated)
  return migrated
}

export async function persistSave(save: SaveGame): Promise<void> {
  await database.saves.put({ ...save, updatedAt: Date.now() })
}

export async function resetSave(): Promise<SaveGame> {
  await database.saves.delete('main')
  return structuredClone(initialSave)
}

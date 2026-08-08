import Dexie, { type EntityTable } from 'dexie'
import type { CardState, ReviewEvent } from './learning'

export type SaveGame = {
  id: 'main'
  version: 1
  unlockedFieldIds: string[]
  masteredFieldIds: string[]
  seenCharacterIds: string[]
  cards: Record<string, CardState>
  reviewEvents: ReviewEvent[]
  updatedAt: number
}

const database = new Dexie('memory-garden') as Dexie & {
  saves: EntityTable<SaveGame, 'id'>
}

database.version(1).stores({
  saves: 'id, version, updatedAt',
})

export const initialSave: SaveGame = {
  id: 'main',
  version: 1,
  unlockedFieldIds: ['field-001'],
  masteredFieldIds: [],
  seenCharacterIds: [],
  cards: {},
  reviewEvents: [],
  updatedAt: Date.now(),
}

export async function loadSave(): Promise<SaveGame> {
  const save = await database.saves.get('main')
  return save ?? structuredClone(initialSave)
}

export async function persistSave(save: SaveGame): Promise<void> {
  await database.saves.put({ ...save, updatedAt: Date.now() })
}

export async function resetSave(): Promise<SaveGame> {
  await database.saves.delete('main')
  return structuredClone(initialSave)
}


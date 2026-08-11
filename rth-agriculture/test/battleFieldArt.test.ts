import { describe, expect, test } from 'bun:test'
import { existsSync } from 'node:fs'
import {
  battleArtworkByGardenId,
  battleArtworkForGarden,
  battleBackdropStage,
} from '../src/data/battleFieldArt'

describe('battle field artwork', () => {
  test('reserves one independently replaceable artwork set for every field in row-major order', () => {
    expect(battleArtworkByGardenId).toHaveLength(15)
    expect(battleArtworkForGarden('garden-01')).toEqual({
      fieldId: 'field1',
      backgrounds: {
        fullDirty: 'assets/battle-fields/field1/full_dirty.webp',
        halfDirty: 'assets/battle-fields/field1/half_dirty.webp',
        quarterDirty: 'assets/battle-fields/field1/quorter_dirty.webp',
        clean: 'assets/battle-fields/field1/clean.webp',
      },
    })
    expect(battleArtworkForGarden('garden-06').fieldId).toBe('field6')
    expect(battleArtworkForGarden('garden-15').fieldId).toBe('field15')
    // Gardens without unique art yet reuse the field1 placeholder files.
    expect(battleArtworkForGarden('garden-06').backgrounds.clean).toBe(
      'assets/battle-fields/field1/clean.webp',
    )
    for (const artwork of battleArtworkByGardenId.values()) {
      Object.values(artwork.backgrounds).forEach((path) => {
        expect(existsSync(`public/${path}`)).toBe(true)
      })
    }
  })

  test('changes background after the specified stroke thresholds', () => {
    expect(battleBackdropStage(10, 0)).toBe('fullDirty')
    expect(battleBackdropStage(10, 5)).toBe('fullDirty')
    expect(battleBackdropStage(10, 6)).toBe('halfDirty')
    expect(battleBackdropStage(10, 7)).toBe('quarterDirty')
    expect(battleBackdropStage(10, 10)).toBe('clean')
  })
})

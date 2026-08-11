import { gardenRegions } from './mapLayout'

/** A distinct artwork set is reserved for every garden field, numbered row by row. */
export type BattleBackdropStage = 'fullDirty' | 'halfDirty' | 'quarterDirty' | 'clean'

export type BattleFieldArtwork = {
  fieldId: string
  backgrounds: Record<BattleBackdropStage, string>
}

const stageFileNames: Record<BattleBackdropStage, string> = {
  fullDirty: 'full_dirty.webp',
  halfDirty: 'half_dirty.webp',
  quarterDirty: 'quorter_dirty.webp',
  clean: 'clean.webp',
}

function artworkForField(fieldNumber: number): BattleFieldArtwork {
  const fieldId = `field${fieldNumber}`
  const directory = `assets/battle-fields/${fieldId}`
  return {
    fieldId,
    backgrounds: {
      fullDirty: `${directory}/${stageFileNames.fullDirty}`,
      halfDirty: `${directory}/${stageFileNames.halfDirty}`,
      quarterDirty: `${directory}/${stageFileNames.quarterDirty}`,
      clean: `${directory}/${stageFileNames.clean}`,
    },
  }
}

/**
 * `gardenRegions` is already ordered left-to-right, then top-to-bottom, so
 * field1 is the upper-left field and field15 is the lower-right field.
 * Replace only the files under a field directory when its bespoke artwork is ready.
 */
export const battleArtworkByGardenId = new Map(
  gardenRegions.map((garden) => [garden.id, artworkForField(garden.index + 1)]),
)

export function battleArtworkForGarden(gardenId: string): BattleFieldArtwork {
  const artwork = battleArtworkByGardenId.get(gardenId)
  if (!artwork) throw new Error(`No battle artwork registered for ${gardenId}`)
  return artwork
}

/**
 * Decide the visible cleaning state from the one-based count of correct strokes.
 * The clean state has priority so it remains visible between completing one
 * character and starting the next one.
 */
export function battleBackdropStage(
  totalStrokes: number,
  correctStrokes: number,
): BattleBackdropStage {
  if (totalStrokes <= 0 || correctStrokes >= totalStrokes) return 'clean'
  if (correctStrokes * 10 >= totalStrokes * 7) return 'quarterDirty'
  if (correctStrokes * 2 > totalStrokes) return 'halfDirty'
  return 'fullDirty'
}

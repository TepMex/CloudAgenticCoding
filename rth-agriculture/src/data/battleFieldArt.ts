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

/**
 * Only fields listed here ship unique art under `public/assets/battle-fields/fieldN/`.
 * All other gardens reuse field1 as a compact placeholder so the Android APK stays
 * under GitHub’s 100 MB push limit (duplicate PNG placeholders were ~180 MB alone).
 */
const fieldsWithUniqueArt = new Set([1])

function artworkDirectory(fieldNumber: number): string {
  const artField = fieldsWithUniqueArt.has(fieldNumber) ? fieldNumber : 1
  return `assets/battle-fields/field${artField}`
}

function artworkForField(fieldNumber: number): BattleFieldArtwork {
  const fieldId = `field${fieldNumber}`
  const directory = artworkDirectory(fieldNumber)
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
 * Add a field number to `fieldsWithUniqueArt` and drop files under its directory
 * when bespoke artwork is ready.
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

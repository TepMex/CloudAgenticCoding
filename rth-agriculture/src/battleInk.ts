import type { BattleBackdropStage } from './data/battleFieldArt'

export type BattleInkPalette = {
  drawingColor: string
  completedStrokeColor: string
}

const FULL_DIRTY_WRITING_INK: BattleInkPalette = {
  // Warm parchment ink reads clearly above the near-black stone at Y≈91.
  drawingColor: '#fff2ca',
  completedStrokeColor: 'rgba(255, 239, 198, .98)',
}

const HALF_DIRTY_WRITING_INK: BattleInkPalette = {
  drawingColor: '#ffedbd',
  completedStrokeColor: 'rgba(250, 230, 180, .96)',
}

const QUARTER_DIRTY_WRITING_INK: BattleInkPalette = {
  drawingColor: '#3d2e23',
  completedStrokeColor: 'rgba(52, 39, 30, .82)',
}

const CLEAN_WRITING_INK: BattleInkPalette = {
  drawingColor: '#25201c',
  completedStrokeColor: '#25201c',
}

/**
 * Kept at the battle-screen seam so every state change can use the same
 * palette as Hanzi Writer, rather than styling only the SVG after the fact.
 */
export function writingInkForBackdrop(stage: BattleBackdropStage): BattleInkPalette {
  switch (stage) {
    case 'fullDirty': return FULL_DIRTY_WRITING_INK
    case 'halfDirty': return HALF_DIRTY_WRITING_INK
    case 'quarterDirty': return QUARTER_DIRTY_WRITING_INK
    case 'clean': return CLEAN_WRITING_INK
  }
}

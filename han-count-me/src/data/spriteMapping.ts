import type { Noun, SpriteFrameMapping } from '../types';

export const GRID_COLUMNS = 4;
export const GRID_ROWS = 4;
export const CELLS_PER_SHEET = GRID_COLUMNS * GRID_ROWS;
export const EXPECTED_SHEET_COUNT = 10;

export function buildSpriteFrameMappings(nouns: readonly Noun[]): SpriteFrameMapping[] {
  if (nouns.length > EXPECTED_SHEET_COUNT * CELLS_PER_SHEET) {
    throw new Error(`Для ${nouns.length} существительных недостаточно ${EXPECTED_SHEET_COUNT} сеток 4×4`);
  }
  return nouns.map((noun, index) => {
    const cellIndex = index % CELLS_PER_SHEET;
    return {
      nounId: noun.noun_id,
      sheetIndex: Math.floor(index / CELLS_PER_SHEET),
      cellIndex,
      row: Math.floor(cellIndex / GRID_COLUMNS),
      column: cellIndex % GRID_COLUMNS,
    };
  });
}

export function frameRectangle(width: number, height: number, row: number, column: number) {
  const x = Math.round((column * width) / GRID_COLUMNS);
  const y = Math.round((row * height) / GRID_ROWS);
  const right = Math.round(((column + 1) * width) / GRID_COLUMNS);
  const bottom = Math.round(((row + 1) * height) / GRID_ROWS);
  return { x, y, width: right - x, height: bottom - y };
}

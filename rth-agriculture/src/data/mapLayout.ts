export type GardenCulture =
  | 'bamboo'
  | 'rice'
  | 'lotus'
  | 'tea'
  | 'blossom'
  | 'peony'
  | 'chrysanthemum'
  | 'pine'
  | 'persimmon'
  | 'orchid'
  | 'berries'
  | 'rapeseed'
  | 'wheat'
  | 'wisteria'
  | 'herbs'

export type NormalizedRect = { x: number; y: number; width: number; height: number }
export type GridCell = { x: number; y: number }

export type GardenRegion = {
  id: string
  index: number
  culture: GardenCulture
  mapRect: NormalizedRect
}

export const MAP_ASPECT_RATIO = 4 / 3
export const WORLD_WIDTH = 1600
export const WORLD_HEIGHT = WORLD_WIDTH / MAP_ASPECT_RATIO
export const GRID_COLUMNS = 15
export const GRID_ROWS = 15
export const REGION_COLUMNS = 5
export const REGION_ROWS = 3
export const REGION_CELL_COLUMNS = 3
export const REGION_CELL_ROWS = 5
export const MIN_ZOOM = 1
export const MAX_ZOOM = 12
export const ENTER_ZOOM_THRESHOLD = 4.5

const cultures: GardenCulture[] = [
  'bamboo', 'rice', 'lotus', 'tea', 'blossom',
  'peony', 'chrysanthemum', 'pine', 'persimmon', 'orchid',
  'berries', 'rapeseed', 'wheat', 'wisteria', 'herbs',
]

/*
 * The playable beds live inside the framed garden artwork.  Keep these as
 * normalized rectangles so replacing the high-resolution artwork does not
 * change camera or hit-area code; only this tuning table may need adjustment.
 */
const ESTATE_RECT: NormalizedRect = { x: 0.094, y: 0.133, width: 0.812, height: 0.748 }
const REGION_INSET = 0.035
const CELL_GAP = 0.035

export const gardenRegions: GardenRegion[] = cultures.map((culture, index) => {
  const column = index % REGION_COLUMNS
  const row = Math.floor(index / REGION_COLUMNS)
  return {
    id: `garden-${String(index + 1).padStart(2, '0')}`,
    index,
    culture,
    mapRect: {
      x: ESTATE_RECT.x + column * (ESTATE_RECT.width / REGION_COLUMNS),
      y: ESTATE_RECT.y + row * (ESTATE_RECT.height / REGION_ROWS),
      width: ESTATE_RECT.width / REGION_COLUMNS,
      height: ESTATE_RECT.height / REGION_ROWS,
    },
  }
})

export function regionForCell(cell: GridCell): GardenRegion {
  const column = Math.floor(cell.x / REGION_CELL_COLUMNS)
  const row = Math.floor(cell.y / REGION_CELL_ROWS)
  return gardenRegions[row * REGION_COLUMNS + column]!
}

export function cellRect(cell: GridCell): NormalizedRect {
  const region = regionForCell(cell)
  const localColumn = cell.x % REGION_CELL_COLUMNS
  const localRow = cell.y % REGION_CELL_ROWS
  const usableWidth = region.mapRect.width * (1 - REGION_INSET * 2)
  const usableHeight = region.mapRect.height * (1 - REGION_INSET * 2)
  const width = usableWidth / REGION_CELL_COLUMNS
  const height = usableHeight / REGION_CELL_ROWS
  const gapX = width * CELL_GAP
  const gapY = height * CELL_GAP
  return {
    x: region.mapRect.x + region.mapRect.width * REGION_INSET + localColumn * width + gapX / 2,
    y: region.mapRect.y + region.mapRect.height * REGION_INSET + localRow * height + gapY / 2,
    width: width - gapX,
    height: height - gapY,
  }
}

/** The visual location of plot N (zero based).  The first five span 2 cells. */
export function cellsForPlotIndex(index: number): GridCell[] {
  if (index < 5) return [{ x: 0, y: index }, { x: 1, y: index }]
  if (index < 10) return [{ x: 2, y: index - 5 }]

  const remaining = index - 10
  const regionIndex = 1 + Math.floor(remaining / 15)
  const insideRegion = remaining % 15
  const regionColumn = regionIndex % REGION_COLUMNS
  const regionRow = Math.floor(regionIndex / REGION_COLUMNS)
  return [{
    x: regionColumn * REGION_CELL_COLUMNS + (insideRegion % REGION_CELL_COLUMNS),
    y: regionRow * REGION_CELL_ROWS + Math.floor(insideRegion / REGION_CELL_COLUMNS),
  }]
}

export function plotBounds(cells: readonly GridCell[]): NormalizedRect {
  const rects = cells.map(cellRect)
  const x = Math.min(...rects.map((rect) => rect.x))
  const y = Math.min(...rects.map((rect) => rect.y))
  const right = Math.max(...rects.map((rect) => rect.x + rect.width))
  const bottom = Math.max(...rects.map((rect) => rect.y + rect.height))
  return { x, y, width: right - x, height: bottom - y }
}

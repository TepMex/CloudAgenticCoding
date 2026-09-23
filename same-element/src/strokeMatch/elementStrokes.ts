/** Median polyline from Make Me a Hanzi, in the 1024×1024 character box. */
export type Median = number[][]

export type StrokeMatch = {
  indices: number[]
  shapeCost: number
  layoutCost: number
  template: string
}

const SAMPLES = 8

/** Arc-length resample so stroke shape can be compared independent of point density. */
export function resampleMedian(points: Median, count = SAMPLES): Median {
  if (points.length === 0) return []
  if (points.length === 1 || count <= 1) return [points[0]!.slice()]
  const lengths = [0]
  for (let i = 1; i < points.length; i++) {
    const dx = points[i]![0]! - points[i - 1]![0]!
    const dy = points[i]![1]! - points[i - 1]![1]!
    lengths.push(lengths[i - 1]! + Math.hypot(dx, dy))
  }
  const total = lengths[lengths.length - 1] || 1
  const out: Median = []
  let cursor = 0
  for (let sample = 0; sample < count; sample++) {
    const target = (total * sample) / (count - 1)
    while (cursor < lengths.length - 2 && lengths[cursor + 1]! < target) cursor++
    const span = lengths[cursor + 1]! - lengths[cursor]! || 1
    const t = Math.min(1, Math.max(0, (target - lengths[cursor]!) / span))
    const a = points[cursor]!
    const b = points[Math.min(cursor + 1, points.length - 1)]!
    out.push([a[0]! + (b[0]! - a[0]!) * t, a[1]! + (b[1]! - a[1]!) * t])
  }
  return out
}

function centroid(points: Median): [number, number] {
  let x = 0
  let y = 0
  for (const point of points) {
    x += point[0]!
    y += point[1]!
  }
  const n = points.length || 1
  return [x / n, y / n]
}

/** Translation- and scale-invariant stroke shape. Direction is preserved. */
export function normalizeShape(points: Median): Median {
  const sampled = resampleMedian(points, SAMPLES)
  const [cx, cy] = centroid(sampled)
  let energy = 0
  for (const point of sampled) {
    energy += (point[0]! - cx) ** 2 + (point[1]! - cy) ** 2
  }
  const scale = Math.sqrt(energy / sampled.length) || 1
  return sampled.map((point) => [(point[0]! - cx) / scale, (point[1]! - cy) / scale])
}

export function shapeDistance(a: Median, b: Median): number {
  const left = normalizeShape(a)
  const right = normalizeShape(b)
  let sum = 0
  for (let i = 0; i < left.length; i++) {
    const dx = left[i]![0]! - right[i]![0]!
    const dy = left[i]![1]! - right[i]![1]!
    sum += dx * dx + dy * dy
  }
  return sum / left.length
}

/**
 * How well matched stroke centers reproduce the template's layout
 * under uniform scale and translation (no rotation). 0 is a perfect copy.
 * Single-stroke templates have no internal layout, so the cost is 0.
 */
export function layoutCost(templateCenters: Array<[number, number]>, matchedCenters: Array<[number, number]>): number {
  const n = templateCenters.length
  if (n < 2 || matchedCenters.length !== n) return 0
  let mx = 0
  let my = 0
  let tx = 0
  let ty = 0
  for (let i = 0; i < n; i++) {
    mx += matchedCenters[i]![0]
    my += matchedCenters[i]![1]
    tx += templateCenters[i]![0]
    ty += templateCenters[i]![1]
  }
  mx /= n
  my /= n
  tx /= n
  ty /= n
  let num = 0
  let den = 0
  for (let i = 0; i < n; i++) {
    const dx = templateCenters[i]![0] - tx
    const dy = templateCenters[i]![1] - ty
    num += dx * (matchedCenters[i]![0] - mx) + dy * (matchedCenters[i]![1] - my)
    den += dx * dx + dy * dy
  }
  const scale = den === 0 ? 1 : num / den
  if (scale <= 0) return 10
  let error = 0
  let spread = 0
  for (let i = 0; i < n; i++) {
    const px = scale * (templateCenters[i]![0] - tx) + mx
    const py = scale * (templateCenters[i]![1] - ty) + my
    const dx = px - matchedCenters[i]![0]
    const dy = py - matchedCenters[i]![1]
    error += dx * dx + dy * dy
    const sx = matchedCenters[i]![0] - mx
    const sy = matchedCenters[i]![1] - my
    spread += sx * sx + sy * sy
  }
  const rms = Math.sqrt(error / n)
  const size = Math.sqrt(spread / n) || 1
  return rms / size
}

function medianCentroid(median: Median): [number, number] {
  if (median.length === 0) return [0, 0]
  return centroid(median)
}

export type MatchThresholds = {
  shapeMax: number
  layoutMax: number
}

export const DEFAULT_THRESHOLDS: MatchThresholds = {
  shapeMax: 0.22,
  layoutMax: 0.4,
}

/** Dictionary radical strokes are trusted when their shapes match, even if the component was stretched. */
export const RADICAL_SHAPE_MAX = 0.18

/** Free search of long radicals (金 is 8 strokes) is combinatorially expensive and unnecessary. */
const MAX_FREE_SEARCH_STROKES = 5

function shapeCostMatrix(characterMedians: Median[], templateMedians: Median[]): number[][] {
  const shapes = characterMedians.map((median) => normalizeShape(median))
  const templateShapes = templateMedians.map((median) => normalizeShape(median))
  return templateShapes.map((shape) =>
    shapes.map((other) => {
      let sum = 0
      for (let i = 0; i < shape.length; i++) {
        const dx = shape[i]![0]! - other[i]![0]!
        const dy = shape[i]![1]! - other[i]![1]!
        sum += dx * dx + dy * dy
      }
      return sum / shape.length
    }),
  )
}

/**
 * Lowest-shape assignment whose stroke centers still reproduce the template layout.
 * A pure shape optimum often swaps two similar strokes and mirrors the component.
 */
function bestCoherentAssignment(
  costs: number[][],
  characterCenters: Array<[number, number]>,
  templateCenters: Array<[number, number]>,
  thresholds: MatchThresholds,
): { indices: number[]; shapeCost: number; layoutCost: number } | null {
  const templates = costs.length
  const strokes = costs[0]?.length ?? 0
  if (templates === 0 || strokes < templates) return null

  let bestShape = Number.POSITIVE_INFINITY
  let bestLayout = Number.POSITIVE_INFINITY
  let bestIndices: number[] | null = null
  const used = new Array<boolean>(strokes).fill(false)
  const current = new Array<number>(templates).fill(-1)

  const visit = (templateIndex: number, running: number) => {
    if (running / templates > thresholds.shapeMax) return
    if (bestIndices && running / templates > bestShape) return
    if (templateIndex === templates) {
      const shapeCost = running / templates
      const layout = layoutCost(
        templateCenters,
        current.map((index) => characterCenters[index]!),
      )
      if (layout > thresholds.layoutMax) return
      if (shapeCost < bestShape - 1e-9 || (shapeCost <= bestShape + 1e-9 && layout < bestLayout)) {
        bestShape = shapeCost
        bestLayout = layout
        bestIndices = current.slice()
      }
      return
    }
    for (let stroke = 0; stroke < strokes; stroke++) {
      if (used[stroke]) continue
      used[stroke] = true
      current[templateIndex] = stroke
      visit(templateIndex + 1, running + costs[templateIndex]![stroke]!)
      used[stroke] = false
    }
  }

  visit(0, 0)
  if (!bestIndices) return null
  return { indices: bestIndices, shapeCost: bestShape, layoutCost: bestLayout }
}

/**
 * Find the strokes of `templateMedians` inside `characterMedians`.
 * Returns null when no assignment is both shaped like the element and laid out like it.
 */
export function matchElementStrokes(
  characterMedians: Median[],
  templateMedians: Median[],
  templateName: string,
  thresholds: MatchThresholds = DEFAULT_THRESHOLDS,
): StrokeMatch | null {
  if (templateMedians.length === 0 || characterMedians.length < templateMedians.length) return null
  const permutationOnly = characterMedians.length === templateMedians.length
  if (!permutationOnly && templateMedians.length > MAX_FREE_SEARCH_STROKES) return null
  const costs = shapeCostMatrix(characterMedians, templateMedians)
  const assignment = bestCoherentAssignment(
    costs,
    characterMedians.map((median) => medianCentroid(median)),
    templateMedians.map((median) => medianCentroid(median)),
    thresholds,
  )
  if (!assignment) return null
  return {
    indices: assignment.indices.slice().sort((a, b) => a - b),
    shapeCost: assignment.shapeCost,
    layoutCost: assignment.layoutCost,
    template: templateName,
  }
}

export type ElementTemplate = {
  name: string
  medians: Median[]
}

/**
 * Prefer the dictionary radical strokes when they actually look like one of the
 * category templates. Otherwise search the whole character — needed when the
 * shared element is not the character's Kangxi radical (渠 in 木, 杰 in 灬).
 */
export function resolveElementStrokes(
  characterMedians: Median[],
  radicalIndices: number[] | undefined,
  templates: ElementTemplate[],
  thresholds: MatchThresholds = DEFAULT_THRESHOLDS,
): StrokeMatch | null {
  let radicalMatch: StrokeMatch | null = null
  if (radicalIndices && radicalIndices.length > 0) {
    for (const template of templates) {
      if (radicalIndices.length !== template.medians.length) continue
      const scored = scoreFixedIndices(characterMedians, template.medians, radicalIndices, template.name)
      if (!scored || scored.shapeCost > RADICAL_SHAPE_MAX) continue
      if (!radicalMatch || scored.shapeCost < radicalMatch.shapeCost) {
        radicalMatch = { ...scored, indices: radicalIndices.slice().sort((a, b) => a - b) }
      }
    }
  }
  if (radicalMatch) return radicalMatch

  let searched: StrokeMatch | null = null
  for (const template of templates) {
    const match = matchElementStrokes(characterMedians, template.medians, template.name, thresholds)
    if (!match) continue
    if (!searched || match.shapeCost < searched.shapeCost) searched = match
  }
  return searched
}

/** Score a known stroke subset (for example Make Me a Hanzi `radStrokes`) against a template. */
export function scoreFixedIndices(
  characterMedians: Median[],
  templateMedians: Median[],
  indices: number[],
  templateName: string,
): StrokeMatch | null {
  if (indices.length !== templateMedians.length || indices.length === 0) return null
  if (indices.some((index) => index < 0 || index >= characterMedians.length)) return null
  const subset = indices.map((index) => characterMedians[index]!)
  const paired = matchElementStrokes(subset, templateMedians, templateName, {
    shapeMax: Number.POSITIVE_INFINITY,
    layoutMax: Number.POSITIVE_INFINITY,
  })
  if (!paired) return null
  return {
    indices: paired.indices.map((local) => indices[local]!).sort((a, b) => a - b),
    shapeCost: paired.shapeCost,
    layoutCost: paired.layoutCost,
    template: templateName,
  }
}

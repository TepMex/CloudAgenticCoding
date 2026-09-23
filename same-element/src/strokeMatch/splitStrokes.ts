import type { Median } from './elementStrokes'

export type StrokePaths = {
  strokes: string[]
  medians: Median[]
}

/** Keep element strokes and the strokes the learner still has to write, in original order. */
export function splitStrokes(data: StrokePaths, elementIndices: number[]): { given: StrokePaths; rest: StrokePaths } {
  const givenSet = new Set(elementIndices)
  const given: StrokePaths = { strokes: [], medians: [] }
  const rest: StrokePaths = { strokes: [], medians: [] }
  data.strokes.forEach((stroke, index) => {
    const bucket = givenSet.has(index) ? given : rest
    bucket.strokes.push(stroke)
    bucket.medians.push(data.medians[index] ?? [])
  })
  return { given, rest }
}

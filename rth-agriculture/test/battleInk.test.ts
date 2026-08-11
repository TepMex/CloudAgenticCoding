import { describe, expect, test } from 'bun:test'
import { writingInkForBackdrop } from '../src/battleInk'

function rgbaParts(color: string): [number, number, number, number] {
  const match = /^rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)$/.exec(color)
  if (!match) throw new Error(`Expected an rgba colour, received ${color}`)
  return [Number(match[1]), Number(match[2]), Number(match[3]), Number(match[4])]
}

describe('battle writing ink', () => {
  test('keeps completed strokes opaque and bright on the two dark battle states', () => {
    for (const stage of ['fullDirty', 'halfDirty'] as const) {
      const [red, green, blue, opacity] = rgbaParts(writingInkForBackdrop(stage).completedStrokeColor)
      const brightness = red * 0.2126 + green * 0.7152 + blue * 0.0722

      expect(opacity).toBeGreaterThanOrEqual(.94)
      expect(brightness).toBeGreaterThanOrEqual(220)
    }
  })
})

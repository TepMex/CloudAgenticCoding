import { describe, expect, test } from 'bun:test'
import { clampCamera, clampZoom, screenToWorld, worldToScreen, zoomAroundPoint } from '../src/map/cameraMath'

const viewport = { width: 1000, height: 800 }

describe('map camera math', () => {
  test('clamps zoom to supported limits', () => {
    expect(clampZoom(-2)).toBe(1)
    expect(clampZoom(50)).toBe(12)
  })

  test('keeps the world point under the cursor when zooming', () => {
    const camera = { x: 50, y: -20, zoom: 2 }
    const cursor = { x: 713, y: 169 }
    const before = screenToWorld(cursor, camera, viewport)
    const after = screenToWorld(cursor, zoomAroundPoint(camera, cursor, 5, viewport), viewport)
    expect(after.x).toBeCloseTo(before.x, 8)
    expect(after.y).toBeCloseTo(before.y, 8)
  })

  test('converts coordinates round-trip and constrains a lost camera', () => {
    const camera = { x: 82, y: -31, zoom: 3 }
    const point = { x: 400, y: 720 }
    expect(screenToWorld(worldToScreen(point, camera, viewport), camera, viewport)).toEqual(point)
    const clamped = clampCamera({ x: 999999, y: -999999, zoom: 3 }, viewport)
    expect(Math.abs(clamped.x)).toBeLessThan(3000)
    expect(Math.abs(clamped.y)).toBeLessThan(3000)
  })
})

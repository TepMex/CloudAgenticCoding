import { describe, expect, test } from 'bun:test'
import { readFileSync } from 'fs'
import { resolve } from 'path'

/**
 * Node-side check that stroke JSON parses. Browser file:// XHR behavior is
 * covered by scripts/check-battle-input.mjs.
 */
describe('hanzi stroke fixtures', () => {
  test('includes stroke data for 一 (battle smoke target)', () => {
    const path = resolve(import.meta.dir, '../public/hanzi/一.json')
    const data = JSON.parse(readFileSync(path, 'utf8')) as {
      strokes: string[]
      medians: number[][][]
    }
    expect(data.strokes.length).toBe(1)
    expect(data.medians.length).toBe(1)
  })
})

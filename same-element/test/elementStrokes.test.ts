import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { expect, test } from 'bun:test'
import { CATEGORY_TEMPLATES } from '../src/data/categoryTemplates.ts'
import { resolveElementStrokes, type Median } from '../src/strokeMatch/elementStrokes.ts'
import { splitStrokes } from '../src/strokeMatch/splitStrokes.ts'

type StrokeFile = { strokes: string[]; medians: Median[]; radStrokes?: number[] }

const require = createRequire(import.meta.url)
const dataRoot = require.resolve('hanzi-writer-data/package.json').replace(/package\.json$/, '')
const cache = new Map<string, StrokeFile>()

function loadChar(char: string): StrokeFile {
  const cached = cache.get(char)
  if (cached) return cached
  const file = JSON.parse(readFileSync(`${dataRoot}${char}.json`, 'utf8')) as StrokeFile
  cache.set(char, file)
  return file
}

function templatesFor(categoryId: string) {
  return (CATEGORY_TEMPLATES[categoryId] ?? []).map((name) => ({
    name,
    medians: loadChar(name).medians,
  }))
}

const cases: Array<[string, string, number[]]> = [
  ['water', '江', [0, 1, 2]],
  ['water', '河', [0, 1, 2]],
  ['water', '渠', [0, 1, 2]],
  ['speech', '认', [0, 1]],
  ['fire', '杰', [4, 5, 6, 7]],
  ['fire', '热', [6, 7, 8, 9]],
  ['fire', '蒸', [9, 10, 11, 12]],
  ['grass', '蒸', [0, 1, 2]],
  ['wood', '渠', [7, 8, 9, 10]],
  ['wood', '林', [0, 1, 2, 3]],
  ['enclosure', '国', [0, 1, 7]],
  ['road', '边', [2, 3, 4]],
  ['road', '巡', [3, 4, 5]],
  ['hand', '打', [0, 1, 2]],
  ['hand', '拿', [6, 7, 8, 9]],
  ['silk', '红', [0, 1, 2]],
  ['heart', '忙', [0, 1, 2]],
  ['heart', '想', [9, 10, 11, 12]],
  ['heart', '恭', [6, 7, 8, 9]],
  ['heart', '惫', [8, 9, 10, 11]],
  ['sun', '明', [0, 1, 2, 3]],
  ['metal', '针', [0, 1, 2, 3, 4]],
  ['roof', '宇', [0, 1, 2]],
  ['roof', '牢', [0, 1, 2]],
  ['woman', '妈', [0, 1, 2]],
  ['earth', '地', [0, 1, 2]],
]

test('shared element strokes match the component, not the whole character', () => {
  for (const [categoryId, hanzi, expected] of cases) {
    const file = loadChar(hanzi)
    const match = resolveElementStrokes(file.medians, file.radStrokes, templatesFor(categoryId))
    expect(match?.indices, `${categoryId} ${hanzi}`).toEqual(expected)
    const { given, rest } = splitStrokes(file, match?.indices ?? [])
    expect(given.strokes.length, hanzi).toBe(expected.length)
    expect(rest.strokes.length, hanzi).toBe(file.strokes.length - expected.length)
    expect(rest.strokes.length, hanzi).toBeGreaterThan(0)
  }
})

test('water and wood selections of 渠 do not share the same strokes', () => {
  const file = loadChar('渠')
  const water = resolveElementStrokes(file.medians, file.radStrokes, templatesFor('water'))
  const wood = resolveElementStrokes(file.medians, file.radStrokes, templatesFor('wood'))
  expect(water?.indices).toEqual([0, 1, 2])
  expect(wood?.indices).toEqual([7, 8, 9, 10])
})

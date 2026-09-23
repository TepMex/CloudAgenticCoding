import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { CATEGORY_TEMPLATES } from '../src/data/categoryTemplates.ts'
import { resolveElementStrokes, type Median } from '../src/strokeMatch/elementStrokes.ts'

type StrokeFile = { strokes: string[]; medians: Median[]; radStrokes?: number[] }
type Catalog = {
  categories: Array<{ id: string; characters: Array<{ hanzi: string }> }>
}

const require = createRequire(import.meta.url)
const dataRoot = require.resolve('hanzi-writer-data/package.json').replace(/package\.json$/, '')

function loadChar(char: string): StrokeFile {
  return JSON.parse(readFileSync(`${dataRoot}${char}.json`, 'utf8')) as StrokeFile
}

const templateMedians = new Map<string, Median[]>()
for (const names of Object.values(CATEGORY_TEMPLATES)) {
  for (const name of names) {
    if (!templateMedians.has(name)) templateMedians.set(name, loadChar(name).medians)
  }
}

const catalog = JSON.parse(readFileSync(new URL('../data/elements.json', import.meta.url), 'utf8')) as Catalog
const byHanzi = new Map<string, { file: StrokeFile; elements: Record<string, number[]> }>()

for (const category of catalog.categories) {
  const templates = (CATEGORY_TEMPLATES[category.id] ?? []).flatMap((name) => {
    const medians = templateMedians.get(name)
    return medians ? [{ name, medians }] : []
  })
  for (const character of category.characters) {
    let entry = byHanzi.get(character.hanzi)
    if (!entry) {
      entry = { file: loadChar(character.hanzi), elements: {} }
      byHanzi.set(character.hanzi, entry)
    }
    const match = resolveElementStrokes(entry.file.medians, entry.file.radStrokes, templates)
    if (!match) {
      throw new Error(`No element strokes for ${category.id} ${character.hanzi}`)
    }
    entry.elements[category.id] = match.indices
  }
}

const outDir = new URL('../public/hanzi/', import.meta.url)
mkdirSync(outDir, { recursive: true })
for (const [hanzi, entry] of byHanzi) {
  const payload = {
    strokes: entry.file.strokes,
    medians: entry.file.medians,
    elements: entry.elements,
  }
  writeFileSync(new URL(`${hanzi}.json`, outDir), JSON.stringify(payload))
}

console.log(`prepared ${byHanzi.size} hanzi stroke files`)

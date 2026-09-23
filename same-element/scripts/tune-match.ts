import { readdirSync, readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { resolveElementStrokes, type Median } from '../src/strokeMatch/elementStrokes.ts'

type StrokeFile = { strokes: string[]; medians: Median[]; radStrokes?: number[] }
type Catalog = {
  categories: Array<{
    id: string
    element: string
    characters: Array<{ hanzi: string }>
  }>
}

const require = createRequire(import.meta.url)
const dataRoot = require.resolve('hanzi-writer-data/package.json').replace(/package\.json$/, '')

const TEMPLATES: Record<string, string[]> = {
  water: ['氵'],
  speech: ['讠'],
  fire: ['灬'],
  enclosure: ['囗'],
  road: ['辶'],
  hand: ['扌', '手'],
  silk: ['纟'],
  heart: ['忄', '心', '⺗'],
  wood: ['木'],
  sun: ['日'],
  metal: ['钅', '金'],
  grass: ['艹'],
  roof: ['宀'],
  woman: ['女'],
  earth: ['土'],
}

function loadChar(char: string): StrokeFile | null {
  try {
    return JSON.parse(readFileSync(`${dataRoot}${char}.json`, 'utf8')) as StrokeFile
  } catch {
    return null
  }
}

const catalog = JSON.parse(readFileSync(new URL('../data/elements.json', import.meta.url), 'utf8')) as Catalog
const templateMedians = new Map<string, Median[]>()
for (const names of Object.values(TEMPLATES)) {
  for (const name of names) {
    const file = loadChar(name)
    if (!file) {
      console.log('MISSING TEMPLATE', name)
      continue
    }
    templateMedians.set(name, file.medians)
    console.log(`template ${name} strokes=${file.medians.length}`)
  }
}

let missing = 0
const failures: string[] = []
const disagreements: string[] = []
for (const category of catalog.categories) {
  let ok = 0
  let fail = 0
  let agree = 0
  let comparable = 0
  const examples: string[] = []
  for (const character of category.characters) {
    const file = loadChar(character.hanzi)
    if (!file) {
      missing++
      fail++
      continue
    }
    const templates = (TEMPLATES[category.id] ?? []).flatMap((name) => {
      const medians = templateMedians.get(name)
      return medians ? [{ name, medians }] : []
    })
    const match = resolveElementStrokes(file.medians, file.radStrokes, templates)
    if (!match) {
      fail++
      if (failures.length < 40) failures.push(`${category.id} ${character.hanzi} rad=${JSON.stringify(file.radStrokes)} n=${file.medians.length}`)
      continue
    }
    ok++
    const rad = (file.radStrokes ?? []).slice().sort((a, b) => a - b)
    const got = match.indices
    if (rad.length === got.length) {
      comparable++
      const same = rad.every((index, i) => index === got[i])
      if (same) agree++
      else if (disagreements.length < 30) {
        disagreements.push(`${category.id} ${character.hanzi} via ${match.template} got=${got.join(',')} rad=${rad.join(',')} shape=${match.shapeCost.toFixed(3)} layout=${match.layoutCost.toFixed(3)}`)
      }
    }
    if (examples.length < 3) {
      examples.push(`${character.hanzi}:${match.template}[${got.join(',')}] s=${match.shapeCost.toFixed(2)} l=${match.layoutCost.toFixed(2)}`)
    }
  }
  console.log(
    `${category.id.padEnd(12)} ok=${ok} fail=${fail} radAgree=${agree}/${comparable} ex ${examples.join(' | ')}`,
  )
}
console.log('missing stroke files', missing)
console.log('failures', failures.join('\n'))
console.log('disagreements', disagreements.join('\n'))
console.log('data files', readdirSync(dataRoot).filter((name) => name.endsWith('.json')).length)

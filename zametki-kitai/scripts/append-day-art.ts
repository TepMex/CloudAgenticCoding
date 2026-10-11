import { readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { encryptPacked, openSealed, packJournal } from '../src/codec'
import { appendDayIllustrations } from '../src/day-art'

const artDir = process.argv[2]
const binPath = process.argv[3]
const password = process.env.NOTES_PASSWORD ?? ''

if (!artDir || !binPath || !password) {
  console.error('Usage: NOTES_PASSWORD=… bun scripts/append-day-art.ts <art-dir> <journal.bin>')
  process.exit(1)
}

const art = new Map<string, Uint8Array>()
for (const name of readdirSync(artDir)) {
  const match = /^(\d{4}-\d{2}-\d{2})\.jpe?g$/i.exec(name)
  if (!match?.[1]) continue
  art.set(match[1], new Uint8Array(readFileSync(join(artDir, name))))
}

const sealed = new Uint8Array(readFileSync(binPath))
const opened = await openSealed(sealed, password)
const before = counts(opened.journal)
const next = appendDayIllustrations(opened.journal, opened.images, art)
const after = counts(next.journal)
if (after.paragraphs !== before.paragraphs || after.photos !== before.photos) {
  throw new Error('Original paragraphs or photographs changed')
}

const destination = resolve(binPath)
writeFileSync(destination, await encryptPacked(packJournal(next.journal, next.images), password))
console.log(`days: ${after.days}`)
console.log(`paragraphs: ${after.paragraphs}`)
console.log(`photos: ${after.photos}`)
console.log(`illustrations: ${after.illustrations}`)
console.log(`wrote ${destination}`)

function counts(journal: { days: { blocks: { t: string; k?: string }[] }[] }) {
  let paragraphs = 0
  let photos = 0
  let illustrations = 0
  for (const day of journal.days) {
    for (const block of day.blocks) {
      if (block.t === 'p') paragraphs += 1
      else if (block.k === 'day') illustrations += 1
      else photos += 1
    }
  }
  return { days: journal.days.length, paragraphs, photos, illustrations }
}

import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { encryptPacked, packJournal } from '../src/codec'
import { packNotes } from '../src/pack'
import { parseNoteHtml } from '../src/parse-note'
import type { RawBlock } from '../src/types'

const notesDir = process.argv[2]
const outPath = process.argv[3]
const password = process.env.NOTES_PASSWORD ?? ''

if (!notesDir || !outPath || !password) {
  console.error('Usage: NOTES_PASSWORD=… bun scripts/seal-notes.ts <notes-dir> <journal.bin>')
  process.exit(1)
}

const files = readdirSync(notesDir)
  .filter((name) => name.toLowerCase().endsWith('.html'))
  .sort()

if (files.length === 0) {
  console.error(`No HTML notes in ${notesDir}`)
  process.exit(1)
}

let paragraphs = 0
let photos = 0
const drafts = files.map((name) => {
  const html = readFileSync(join(notesDir, name), 'utf8')
  const note = parseNoteHtml(html)
  const blocks: RawBlock[] = note.blocks.map((block) => {
    if (block.t === 'p') {
      paragraphs += 1
      return block
    }
    photos += 1
    return { t: 'img' as const, bytes: pngToJpeg(block.bytes) }
  })
  return { title: note.title, blocks }
})

const packed = packNotes(drafts)
const plain = packJournal(packed.journal, packed.images)
const sealed = await encryptPacked(plain, password)
const destination = resolve(outPath)
mkdirSync(join(destination, '..'), { recursive: true })
writeFileSync(destination, sealed)

console.log(`days: ${packed.journal.days.length}`)
console.log(`paragraphs: ${paragraphs}`)
console.log(`photos: ${photos}`)
console.log(`bytes: ${sealed.length}`)
console.log(`wrote ${destination}`)

function pngToJpeg(png: Uint8Array): Uint8Array {
  const dir = mkdtempSync(join(tmpdir(), 'zametki-img-'))
  try {
    const source = join(dir, 'in.png')
    const destination = join(dir, 'out.jpg')
    writeFileSync(source, png)
    const result = spawnSync(
      'ffmpeg',
      ['-y', '-loglevel', 'error', '-i', source, '-vf', "scale='min(1400,iw)':-2", '-q:v', '4', destination],
      { encoding: 'utf8' },
    )
    if (result.status !== 0) {
      throw new Error(result.stderr || 'ffmpeg failed')
    }
    return new Uint8Array(readFileSync(destination))
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
}

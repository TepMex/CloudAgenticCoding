import { dayHeading, formatRange, navDateLabel, parseTitle } from './dates'
import type { Journal, RawBlock } from './types'

export type NoteDraft = {
  title: string
  blocks: RawBlock[]
}

export function packNotes(notes: NoteDraft[]): { journal: Journal; images: Uint8Array } {
  const dated = notes.map((note) => {
    const date = parseTitle(note.title)
    if (!date) throw new Error(`Unreadable note title: ${note.title}`)
    return { note, date }
  })
  dated.sort((a, b) => a.date.iso.localeCompare(b.date.iso))
  if (dated.length === 0) throw new Error('No notes to pack')

  let imageBytes = 0
  for (const entry of dated) {
    for (const block of entry.note.blocks) {
      if (block.t === 'img') imageBytes += block.bytes.length
    }
  }
  const images = new Uint8Array(imageBytes)
  let offset = 0
  const days = dated.map(({ note, date }) => ({
    id: date.iso,
    navDate: navDateLabel(date),
    navPlace: date.place,
    heading: dayHeading(date),
    place: date.place,
    blocks: note.blocks.map((block) => {
      if (block.t === 'p') return { t: 'p' as const, text: block.text }
      const start = offset
      images.set(block.bytes, offset)
      offset += block.bytes.length
      return { t: 'img' as const, o: start, n: block.bytes.length }
    }),
  }))

  const first = dated[0]!.date
  const last = dated[dated.length - 1]!.date
  return {
    journal: {
      title: 'Китай',
      kicker: 'Путевые заметки',
      range: formatRange(first, last),
      days,
    },
    images,
  }
}

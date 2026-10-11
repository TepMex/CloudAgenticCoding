import type { Journal } from './types'

/**
 * Appends one closing illustration per day. Bytes are concatenated after the
 * photographs already in the journal, and the new block is marked `k: "day"`
 * so the reader can tell it from a photograph. Days that already end with
 * that mark are left untouched.
 */
export function appendDayIllustrations(
  journal: Journal,
  images: Uint8Array,
  artByDay: ReadonlyMap<string, Uint8Array>,
): { journal: Journal; images: Uint8Array } {
  const chunks: Uint8Array[] = [images]
  let offset = images.length
  const days = journal.days.map((day) => {
    if (day.blocks.some((block) => block.t === 'img' && block.k === 'day')) return day
    const art = artByDay.get(day.id)
    if (!art || art.length === 0) throw new Error(`Missing day illustration for ${day.id}`)
    const start = offset
    chunks.push(art)
    offset += art.length
    return {
      ...day,
      blocks: [...day.blocks, { t: 'img' as const, o: start, n: art.length, k: 'day' as const }],
    }
  })

  const nextImages = new Uint8Array(offset)
  let cursor = 0
  for (const chunk of chunks) {
    nextImages.set(chunk, cursor)
    cursor += chunk.length
  }
  return { journal: { ...journal, days }, images: nextImages }
}

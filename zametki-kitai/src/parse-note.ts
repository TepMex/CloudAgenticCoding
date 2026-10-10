import type { RawBlock, RawNote } from './types'

const IMAGE =
  /<img\b[^>]*?\bsrc\s*=\s*"data:image\/[a-zA-Z0-9.+-]+;base64,([^"]*)"[^>]*>/gi

export function parseNoteHtml(html: string): RawNote {
  const withoutStyle = html.replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, '')
  const titleMatch = withoutStyle.match(/<h1\b[^>]*>([\s\S]*?)<\/h1>/i)
  if (!titleMatch) throw new Error('Note is missing a title')
  const title = visibleText(titleMatch[1] ?? '')
  const body = withoutStyle.slice(titleMatch.index! + titleMatch[0].length)
  const blocks: RawBlock[] = []
  let cursor = 0
  for (const match of body.matchAll(IMAGE)) {
    const index = match.index ?? 0
    pushText(blocks, body.slice(cursor, index))
    const bytes = decodeBase64(match[1] ?? '')
    if (bytes.length > 0) blocks.push({ t: 'img', bytes })
    cursor = index + match[0].length
  }
  pushText(blocks, body.slice(cursor))
  return { title, blocks }
}

export function decodeBase64(value: string): Uint8Array {
  const clean = value.replace(/\s+/g, '')
  if (!clean) return new Uint8Array()
  const binary = atob(clean)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes
}

function pushText(blocks: RawBlock[], chunk: string) {
  const parts = chunk.split(/<br\b[^>]*>|<\/div>|<\/p>/gi)
  for (const part of parts) {
    const text = visibleText(part)
    if (text) blocks.push({ t: 'p', text })
  }
}

function visibleText(fragment: string): string {
  const stripped = fragment.replace(/<[^>]+>/g, '')
  return decodeEntities(stripped).replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim()
}

function decodeEntities(value: string): string {
  return value
    .replace(/&nbsp;/gi, ' ')
    .replace(/&#160;/g, ' ')
    .replace(/&#x0*a0;/gi, ' ')
    .replace(/&quot;/gi, '"')
    .replace(/&#(\d+);/g, (_, digits: string) => String.fromCodePoint(Number(digits)))
    .replace(/&#x([0-9a-f]+);/gi, (_, hex: string) => String.fromCodePoint(parseInt(hex, 16)))
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&amp;/gi, '&')
}

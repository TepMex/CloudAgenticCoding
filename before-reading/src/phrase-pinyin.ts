import { pinyin } from 'pinyin-pro'

const INITIALS = ['zh', 'ch', 'sh', 'b', 'p', 'm', 'f', 'd', 't', 'n', 'l', 'g', 'k', 'h', 'j', 'q', 'x', 'r', 'z', 'c', 's', 'y', 'w']

const FINALS = new Set([
  'iang', 'iong', 'uang', 'ueng', 'üan',
  'ang', 'eng', 'ing', 'ong', 'iao', 'ian', 'uai', 'uan',
  'ia', 'ie', 'iu', 'in', 'ua', 'uo', 'ui', 'un', 'ue', 'üe', 'ün',
  'ai', 'ei', 'ao', 'ou', 'an', 'en', 'er',
  'a', 'o', 'e', 'i', 'u', 'ü',
  'ng', 'n', 'm',
])

const TONE_TO_PLAIN: Record<string, string> = {
  ā: 'a', á: 'a', ǎ: 'a', à: 'a',
  ē: 'e', é: 'e', ě: 'e', è: 'e',
  ī: 'i', í: 'i', ǐ: 'i', ì: 'i',
  ō: 'o', ó: 'o', ǒ: 'o', ò: 'o',
  ū: 'u', ú: 'u', ǔ: 'u', ù: 'u',
  ǖ: 'ü', ǘ: 'ü', ǚ: 'ü', ǜ: 'ü',
  ń: 'n', ň: 'n', ǹ: 'n',
  ḿ: 'm',
}

const HAN = /\p{Script=Han}/u
const LETTER = /[A-Za-zĀÁǍÀĒÉĚÈĪÍǏÌŌÓǑÒŪÚǓÙǕǗǙǛÜüāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]/

type Reading = { origin: string; result: string }

/** Split sheet pinyin (`bǎoyòu`, `lièzǔ lièzōng`, `tái'àn`) into syllables. */
export function splitPinyin(input: string): string[] | null {
  const trimmed = input.trim()
  if (!trimmed) return []
  const syllables: string[] = []
  for (const chunk of trimmed.split(/[\s·]+/)) {
    for (const piece of chunk.split(/['’]/)) {
      if (!piece) continue
      const split = splitMarked(piece)
      if (!split) return null
      syllables.push(...split)
    }
  }
  return syllables
}

/** Toned pinyin for a context phrase. The sheet reading wins for the headword span. */
export function examplePinyin(example: string, expression = '', expressionPinyin = ''): string {
  const text = example.trim()
  if (!text) return ''
  const tokens = pinyin(text, {
    type: 'all',
    toneType: 'symbol',
    toneSandhi: false,
    nonZh: 'consecutive',
  }).map((token) => ({ origin: token.origin, result: token.result }))
  applyHeadword(text, tokens, expression, expressionPinyin)
  const reading = joinReadings(tokens.map((token) => token.result))
  return LETTER.test(reading) ? reading : ''
}

function applyHeadword(text: string, tokens: Reading[], expression: string, expressionPinyin: string) {
  const word = expression.trim()
  const chars = [...word]
  if (chars.length === 0 || !chars.every((char) => HAN.test(char))) return
  const syllables = splitPinyin(expressionPinyin)
  if (!syllables || syllables.length !== chars.length) return
  const found = occurrences(text, word)
  if (found === 0) return
  if (chars.length === 1 && found !== 1) return
  const textChars = [...text]
  if (textChars.length !== tokens.length || textChars.some((char, index) => tokens[index]?.origin !== char)) return

  let from = 0
  while (from <= text.length) {
    const at = text.indexOf(word, from)
    if (at < 0) break
    const start = [...text.slice(0, at)].length
    for (let offset = 0; offset < chars.length; offset += 1) {
      const token = tokens[start + offset]
      const syllable = syllables[offset]
      if (token && syllable) token.result = syllable
    }
    from = at + word.length
  }
}

function occurrences(text: string, word: string): number {
  let count = 0
  let from = 0
  while (from <= text.length) {
    const at = text.indexOf(word, from)
    if (at < 0) return count
    count += 1
    from = at + word.length
  }
  return count
}

function joinReadings(parts: string[]): string {
  let out = ''
  for (const raw of parts) {
    const part = raw.trim()
    if (!part) continue
    if (!out) {
      out = part
      continue
    }
    const left = out[out.length - 1] ?? ''
    const right = part[0] ?? ''
    out += LETTER.test(left) && LETTER.test(right) ? ` ${part}` : part
  }
  return out
}

function splitMarked(marked: string): string[] | null {
  const chars = [...marked]
  const plain = chars.map((char) => TONE_TO_PLAIN[char] ?? (char === 'v' || char === 'V' ? 'ü' : char.toLowerCase())).join('')
  if ([...plain].length !== chars.length) return null
  const syllables: string[] = []
  let index = 0
  while (index < plain.length) {
    const size = longestSyllable(plain.slice(index))
    if (size === 0) return null
    syllables.push(chars.slice(index, index + size).join(''))
    index += size
  }
  return syllables
}

function longestSyllable(plain: string): number {
  const max = Math.min(plain.length, 6)
  for (let size = max; size >= 1; size -= 1) {
    if (isSyllable(plain.slice(0, size))) return size
  }
  return 0
}

function isSyllable(plain: string): boolean {
  if (FINALS.has(plain)) return true
  for (const initial of INITIALS) {
    if (plain.startsWith(initial) && FINALS.has(plain.slice(initial.length))) return true
  }
  return false
}

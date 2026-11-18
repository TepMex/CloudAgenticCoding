/** Split a Russian gloss field into study prompts. Parentheses stay intact. */
export function splitGlosses(meaning: string): string[] {
  const source = meaning.trim()
  if (!source) return []
  const parts: string[] = []
  let buf = ''
  let depth = 0

  const push = () => {
    const text = buf.trim()
    buf = ''
    if (text) parts.push(text)
  }

  for (let i = 0; i < source.length; i += 1) {
    const ch = source[i] ?? ''
    if (ch === '(' || ch === '（') {
      depth += 1
      buf += ch
      continue
    }
    if ((ch === ')' || ch === '）') && depth > 0) {
      depth -= 1
      buf += ch
      continue
    }
    if (depth === 0 && (ch === ',' || ch === '，' || ch === ';' || ch === '；' || ch === '、')) {
      push()
      continue
    }
    if (depth === 0 && (ch === '—' || ch === '–') && spacedDash(source, i)) {
      push()
      continue
    }
    if (depth === 0 && ch === '/' && source[i - 1] === ' ' && source[i + 1] === ' ') {
      push()
      continue
    }
    buf += ch
  }
  push()
  return parts
}

export function uniqueGlosses(glosses: string[]): string[] {
  const seen = new Set<string>()
  const unique: string[] = []
  for (const gloss of glosses) {
    if (seen.has(gloss)) continue
    seen.add(gloss)
    unique.push(gloss)
  }
  return unique
}

function spacedDash(source: string, index: number): boolean {
  const prev = source[index - 1]
  const next = source[index + 1]
  return (prev === ' ' || prev === undefined) && (next === ' ' || next === undefined)
}

/** RFC 4180 CSV, including quotes, escaped quotes, and newlines inside a cell. */
export function parseCsv(text: string): string[][] {
  const src = text.replace(/^\uFEFF/, '')
  const rows: string[][] = []
  let row: string[] = []
  let field = ''
  let inQuotes = false

  for (let i = 0; i < src.length; i++) {
    const ch = src[i]
    if (inQuotes) {
      if (ch === '"') {
        if (src[i + 1] === '"') {
          field += '"'
          i++
        } else {
          inQuotes = false
        }
        continue
      }
      const span = newlineSpan(src, i)
      if (span > 0) {
        field += '\n'
        i += span - 1
        continue
      }
      field += ch
      continue
    }

    if (ch === '"') {
      inQuotes = true
      continue
    }
    if (ch === ',') {
      row.push(field)
      field = ''
      continue
    }
    const span = newlineSpan(src, i)
    if (span > 0) {
      row.push(field)
      rows.push(row)
      row = []
      field = ''
      i += span - 1
      continue
    }
    field += ch
  }

  if (inQuotes) row.push(field)
  else if (field.length > 0 || row.length > 0) row.push(field)
  if (row.length > 0) rows.push(row)
  return rows
}

function newlineSpan(src: string, index: number): number {
  const ch = src[index]
  if (ch === '\r' && src[index + 1] === '\n') return 2
  if (ch === '\n' || ch === '\r') return 1
  return 0
}

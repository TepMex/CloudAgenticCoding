import { strFromU8, unzipSync } from 'fflate'

export class WorkbookError extends Error {
  constructor(message = 'bad workbook') {
    super(message)
    this.name = 'WorkbookError'
  }
}

export type WorkbookCell = {
  text: string
  href?: string
}

export type WorkbookRow = {
  index: number
  cells: WorkbookCell[]
}

export type WorkbookSheet = {
  name: string
  rows: WorkbookRow[]
}

type FormatKind = 'date' | 'time' | 'number'

/** Excel 1900 system: serial 61 is 1900-03-01. The 1899-12-30 epoch absorbs the fake leap day. */
const EXCEL_1900_EPOCH = Date.UTC(1899, 11, 30)
const EXCEL_1904_EPOCH = Date.UTC(1904, 0, 1)

export function readWorkbook(bytes: Uint8Array): WorkbookSheet[] {
  let packed: Record<string, Uint8Array>
  try {
    packed = unzipSync(bytes)
  } catch {
    throw new WorkbookError()
  }
  const files = new Map<string, string>()
  for (const [name, data] of Object.entries(packed)) {
    files.set(name.replace(/^\/+/, ''), stripBom(strFromU8(data)))
  }

  const workbook = files.get('xl/workbook.xml')
  if (!workbook) throw new WorkbookError()
  const rels = files.get('xl/_rels/workbook.xml.rels') ?? ''
  const targets = relationshipTargets(rels)
  const date1904 = /<workbookPr\b[^>]*\bdate1904="1"/.test(workbook)
  const strings = sharedStrings(files.get('xl/sharedStrings.xml') ?? '')
  const formats = cellFormats(files.get('xl/styles.xml') ?? '')

  const sheets: WorkbookSheet[] = []
  for (const tag of workbook.matchAll(/<sheet\b([^>]*)>/g)) {
    const sheet = attrs(tag[1] ?? '')
    const state = sheet.state ?? 'visible'
    if (state === 'hidden' || state === 'veryHidden') continue
    const rel = sheet['r:id']
    const target = rel ? targets.get(rel) : undefined
    if (!sheet.name || !target || /^https?:/i.test(target)) throw new WorkbookError()
    const path = packagePath(target, 'xl')
    const xml = files.get(path)
    if (!xml) throw new WorkbookError()
    const links = hyperlinks(xml, targetsFromSheet(files.get(relsPath(path)) ?? ''))
    sheets.push({ name: sheet.name, rows: sheetRows(xml, strings, formats, links, date1904) })
  }
  return sheets
}

function sharedStrings(xml: string): string[] {
  const strings: string[] = []
  for (const item of xml.matchAll(/<si\b[^>]*>([\s\S]*?)<\/si>/g)) {
    const inner = (item[1] ?? '').replace(/<rPh\b[^>]*>[\s\S]*?<\/rPh>/g, '')
    let text = ''
    for (const part of inner.matchAll(/<t\b[^>]*>([\s\S]*?)<\/t>/g)) text += decodeXml(part[1] ?? '')
    strings.push(text)
  }
  return strings
}

function cellFormats(xml: string): FormatKind[] {
  const codes = new Map<string, string>()
  const listed = xml.match(/<numFmts\b[^>]*>([\s\S]*?)<\/numFmts>/)
  if (listed) {
    for (const fmt of listed[1].matchAll(/<numFmt\b([^>]*)>/g)) {
      const item = attrs(fmt[1] ?? '')
      if (item.numFmtId) codes.set(item.numFmtId, item.formatCode ?? '')
    }
  }
  const block = xml.match(/<cellXfs\b[^>]*>([\s\S]*?)<\/cellXfs>/)
  if (!block) return []
  const kinds: FormatKind[] = []
  for (const xf of block[1].matchAll(/<xf\b([^>]*)>/g)) {
    const id = attrs(xf[1] ?? '').numFmtId ?? '0'
    kinds.push(formatKind(id, codes.get(id)))
  }
  return kinds
}

function formatKind(numFmtId: string, formatCode: string | undefined): FormatKind {
  const id = Number(numFmtId)
  const code = (formatCode ?? '').toLowerCase()
  if (id === 164 || (id >= 14 && id <= 17) || code.includes('yy')) return 'date'
  if ((id >= 18 && id <= 21) || (id >= 45 && id <= 47) || code.includes('[h]') || code.includes('h:')) return 'time'
  return 'number'
}

function relationshipTargets(xml: string): Map<string, string> {
  const map = new Map<string, string>()
  for (const tag of xml.matchAll(/<Relationship\b([^>]*)>/g)) {
    const item = attrs(tag[1] ?? '')
    if (item.Id && item.Target && (item.Type ?? '').endsWith('/worksheet')) map.set(item.Id, item.Target)
  }
  return map
}

function targetsFromSheet(xml: string): Map<string, string> {
  const map = new Map<string, string>()
  for (const tag of xml.matchAll(/<Relationship\b([^>]*)>/g)) {
    const item = attrs(tag[1] ?? '')
    if (item.Id && item.Target && (item.Type ?? '').endsWith('/hyperlink')) map.set(item.Id, item.Target)
  }
  return map
}

function hyperlinks(sheetXml: string, targets: Map<string, string>): Map<string, string> {
  const map = new Map<string, string>()
  for (const tag of sheetXml.matchAll(/<hyperlink\b([^>]*)>/g)) {
    const item = attrs(tag[1] ?? '')
    const ref = (item.ref ?? '').split(':')[0] ?? ''
    const target = item['r:id'] ? targets.get(item['r:id']) : undefined
    const href = safeHttp(target)
    if (ref && href) map.set(ref, href)
  }
  return map
}

function sheetRows(
  xml: string,
  strings: string[],
  formats: FormatKind[],
  links: Map<string, string>,
  date1904: boolean,
): WorkbookRow[] {
  const data = xml.match(/<sheetData\b[^>]*>([\s\S]*?)<\/sheetData>/)
  if (!data) return []
  const pending: { index: number; cells: Map<number, WorkbookCell> }[] = []
  let width = 0
  for (const row of data[1].matchAll(/<row\b([^>]*)>([\s\S]*?)<\/row>/g)) {
    const index = Number(attrs(row[1] ?? '').r ?? '0')
    const cells = new Map<number, WorkbookCell>()
    for (const cell of (row[2] ?? '').matchAll(/<c\b([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
      const meta = attrs(cell[1] ?? '')
      const ref = splitRef(meta.r ?? '')
      if (!ref) continue
      const text = cellText(meta.t, cell[2] ?? '', strings, formats, meta.s, date1904)
      const href = links.get(meta.r ?? '') ?? safeHttp(text)
      const value: WorkbookCell = href ? { text, href } : { text }
      cells.set(ref.col, value)
      if (ref.col > width) width = ref.col
    }
    if (index > 0) pending.push({ index, cells })
  }
  pending.sort((a, b) => a.index - b.index)
  return pending.map((row) => {
    const cells: WorkbookCell[] = []
    for (let col = 1; col <= width; col += 1) cells.push(row.cells.get(col) ?? { text: '' })
    return { index: row.index, cells }
  })
}

function cellText(
  type: string | undefined,
  inner: string,
  strings: string[],
  formats: FormatKind[],
  style: string | undefined,
  date1904: boolean,
): string {
  if (type === 'inlineStr') {
    let text = ''
    for (const part of inner.matchAll(/<t\b[^>]*>([\s\S]*?)<\/t>/g)) text += decodeXml(part[1] ?? '')
    return text
  }
  const raw = decodeXml(inner.match(/<v\b[^>]*>([\s\S]*?)<\/v>/)?.[1] ?? '')
  if (!raw) return ''
  if (type === 's') {
    const index = Number(raw)
    const text = strings[index]
    if (text === undefined) throw new WorkbookError()
    return text
  }
  if (type === 'str') return raw
  if (type === 'b') return raw === '1' ? 'TRUE' : 'FALSE'
  if (type === 'e') return ''
  const kind = style !== undefined ? (formats[Number(style)] ?? 'number') : 'number'
  return formatNumber(raw, kind, date1904)
}

function formatNumber(raw: string, kind: FormatKind, date1904: boolean): string {
  const value = Number(raw)
  if (!Number.isFinite(value)) return raw
  if (kind === 'date') return formatExcelDate(value, date1904)
  if (kind === 'time') return formatExcelTime(value)
  return formatPlainNumber(value)
}

function formatExcelDate(serial: number, date1904: boolean): string {
  const whole = Math.floor(serial)
  if (whole <= 0) return ''
  const utc = (date1904 ? EXCEL_1904_EPOCH : EXCEL_1900_EPOCH) + whole * 86400000
  const date = new Date(utc)
  const dd = String(date.getUTCDate()).padStart(2, '0')
  const mm = String(date.getUTCMonth() + 1).padStart(2, '0')
  return `${dd}.${mm}.${date.getUTCFullYear()}`
}

function formatExcelTime(fraction: number): string {
  if (!Number.isFinite(fraction)) return ''
  let seconds = Math.round(fraction * 86400)
  if (seconds <= 0) return ''
  const hours = Math.floor(seconds / 3600)
  seconds -= hours * 3600
  const minutes = Math.floor(seconds / 60)
  const rest = seconds - minutes * 60
  return `${hours}:${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
}

function formatPlainNumber(value: number): string {
  if (Object.is(value, -0)) return '0'
  if (Math.abs(value - Math.round(value)) < 1e-8) return String(Math.round(value))
  return String(value)
}

function splitRef(ref: string): { col: number; row: number } | null {
  const match = /^([A-Z]+)(\d+)/.exec(ref)
  if (!match) return null
  let col = 0
  for (const char of match[1] ?? '') col = col * 26 + (char.charCodeAt(0) - 64)
  return { col, row: Number(match[2]) }
}

function packagePath(target: string, base: string): string {
  const parts = target.startsWith('/') ? [] : base.split('/')
  for (const part of target.split('/')) {
    if (!part || part === '.') continue
    if (part === '..') parts.pop()
    else parts.push(part)
  }
  return parts.join('/')
}

function relsPath(sheetPath: string): string {
  const slash = sheetPath.lastIndexOf('/')
  return `${sheetPath.slice(0, slash)}/_rels/${sheetPath.slice(slash + 1)}.rels`
}

function attrs(source: string): Record<string, string> {
  const out: Record<string, string> = {}
  for (const match of source.matchAll(/([\w:]+)="([^"]*)"/g)) {
    out[match[1] ?? ''] = decodeXml(match[2] ?? '')
  }
  return out
}

function decodeXml(value: string): string {
  return value.replace(/&(#[0-9]+|#x[0-9a-fA-F]+|[a-zA-Z]+);/g, (all, entity: string) => {
    switch (entity) {
      case 'amp':
        return '&'
      case 'lt':
        return '<'
      case 'gt':
        return '>'
      case 'quot':
        return '"'
      case 'apos':
        return "'"
      default: {
        if (!entity.startsWith('#')) return all
        const hex = entity[1] === 'x' || entity[1] === 'X'
        const code = Number.parseInt(entity.slice(hex ? 2 : 1), hex ? 16 : 10)
        if (!Number.isFinite(code) || code < 0) return all
        try {
          return String.fromCodePoint(code)
        } catch {
          return all
        }
      }
    }
  })
}

function safeHttp(value: string | undefined): string | undefined {
  if (!value) return undefined
  try {
    const url = new URL(value.trim())
    if (url.protocol === 'http:' || url.protocol === 'https:') return url.toString()
  } catch {
    return undefined
  }
  return undefined
}

function stripBom(value: string): string {
  return value.charCodeAt(0) === 0xfeff ? value.slice(1) : value
}

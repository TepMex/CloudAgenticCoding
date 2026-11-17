import { zipSync, strToU8 } from 'fflate'
import { expect, test } from 'bun:test'
import {
  catalogFromWorkbook,
  groupEntries,
  loadCatalog,
  ruCount,
  SheetError,
  slugify,
  visibleEntries,
} from '../src/catalog'
import { readWorkbook, WorkbookError } from '../src/xlsx'

const STRINGS = [
  'Название',
  'Автор',
  'Статус',
  'Оценка',
  'Ссылка',
  'Дата рекомендации',
  'Длительность',
  'Раздел',
  'Почему рекомендовано',
  'Подборка',
  'A & B',
  'Кейт',
  'Смотрю',
  'Очень понравилось',
  'сайт',
  'Фэнтези',
  'Потому что тихо',
  'Не отмечено',
  'Не оценено',
  'Исполнитель',
  'Трек',
  'Язык',
  'Казахский',
  'Ақпен бірге',
  'Moldanazar',
  'Основание рекомендации',
  'Стартовая',
  'Ёлка',
  'Русский',
  'История',
]

const richAt = STRINGS.length
const historyAt = STRINGS.indexOf('История')

function escapeXml(text: string): string {
  return text.replaceAll('&', '&amp;').replaceAll('<', '&lt;')
}

function workbookBytes(): Uint8Array {
  const shared = `<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">${STRINGS.map((text) => `<si><t>${escapeXml(text)}</t></si>`).join('')}<si><r><t>Две</t></r><r><t xml:space="preserve"> части</t></r></si></sst>`
  const books = `<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheetData>
    <row r="1">
      <c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c>
      <c r="D1" t="s"><v>3</v></c><c r="E1" t="s"><v>4</v></c><c r="F1" t="s"><v>5</v></c>
      <c r="G1" t="s"><v>6</v></c><c r="H1" t="s"><v>7</v></c><c r="I1" t="s"><v>8</v></c>
      <c r="J1" t="s"><v>9</v></c>
    </row>
    <row r="2">
      <c r="A2" t="s"><v>10</v></c><c r="B2" t="s"><v>11</v></c><c r="C2" t="s"><v>12</v></c>
      <c r="D2" t="s"><v>13</v></c><c r="E2" t="s"><v>14</v></c><c r="F2" s="1"><v>46341</v></c>
      <c r="G2" s="2"><v>0.08945601851851852</v></c><c r="H2" t="s"><v>15</v></c>
      <c r="I2" t="s"><v>16</v></c><c r="J2"><v>2.0</v></c>
    </row>
    <row r="4">
      <c r="A4" t="s"><v>${richAt}</v></c><c r="C4" t="s"><v>17</v></c><c r="D4" t="s"><v>18</v></c>
      <c r="E4" t="inlineStr"><is><t>javascript:alert(1)</t></is></c><c r="H4" t="s"><v>${historyAt}</v></c>
    </row>
  </sheetData>
  <hyperlinks>
    <hyperlink ref="E2" r:id="rId1"/>
    <hyperlink ref="E4" r:id="rId2"/>
  </hyperlinks>
</worksheet>`
  const music = `<?xml version="1.0" encoding="UTF-8"?>
<worksheet>
  <sheetData>
    <row r="1">
      <c r="A1" t="s"><v>19</v></c><c r="B1" t="s"><v>20</v></c><c r="C1" t="s"><v>21</v></c><c r="D1" t="s"><v>25</v></c>
    </row>
    <row r="2">
      <c r="A2" t="s"><v>24</v></c><c r="B2" t="s"><v>23</v></c><c r="C2" t="s"><v>22</v></c><c r="D2" t="s"><v>26</v></c>
    </row>
    <row r="3">
      <c r="B3" t="s"><v>27</v></c><c r="C3" t="s"><v>28</v></c>
    </row>
  </sheetData>
</worksheet>`
  const hidden = `<?xml version="1.0" encoding="UTF-8"?><worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Секрет</t></is></c></row><row r="2"><c r="A2" t="inlineStr"><is><t>Не показывать</t></is></c></row></sheetData></worksheet>`
  const files: Record<string, string> = {
    'xl/workbook.xml': `<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Книги" sheetId="1" r:id="rId1"/>
    <sheet name="Черновик" sheetId="2" state="hidden" r:id="rId2"/>
    <sheet name="Музыка" sheetId="3" r:id="rId3"/>
  </sheets>
</workbook>`,
    'xl/_rels/workbook.xml.rels': `<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>`,
    'xl/sharedStrings.xml': shared,
    'xl/styles.xml': `<?xml version="1.0" encoding="UTF-8"?>
<styleSheet>
  <numFmts count="1"><numFmt numFmtId="164" formatCode="dd.mm.yyyy"/></numFmts>
  <cellXfs count="3">
    <xf numFmtId="0"><alignment/></xf>
    <xf numFmtId="164"><alignment/></xf>
    <xf numFmtId="46"><alignment/></xf>
  </cellXfs>
</styleSheet>`,
    'xl/worksheets/sheet3.xml': books,
    'xl/worksheets/_rels/sheet3.xml.rels': `<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="https://example.com/a?x=1&amp;y=2" TargetMode="External"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="javascript:alert(1)" TargetMode="External"/>
</Relationships>`,
    'xl/worksheets/sheet2.xml': hidden,
    'xl/worksheets/sheet1.xml': music,
  }
  const zipped: Record<string, Uint8Array> = {}
  for (const [name, text] of Object.entries(files)) zipped[name] = strToU8(text)
  return zipSync(zipped)
}

function headerOnlyBytes(): Uint8Array {
  const files: Record<string, string> = {
    'xl/workbook.xml': `<?xml version="1.0"?><workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Пусто" sheetId="1" r:id="rId1"/></sheets></workbook>`,
    'xl/_rels/workbook.xml.rels': `<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>`,
    'xl/worksheets/sheet1.xml': `<?xml version="1.0"?><worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Название</t></is></c></row></sheetData></worksheet>`,
  }
  const zipped: Record<string, Uint8Array> = {}
  for (const [name, text] of Object.entries(files)) zipped[name] = strToU8(text)
  return zipSync(zipped)
}

test('reads sheets in workbook order and skips a hidden sheet', () => {
  const sheets = readWorkbook(workbookBytes())
  expect(sheets.map((sheet) => sheet.name)).toEqual(['Книги', 'Музыка'])
  expect(sheets[0]?.rows[1]?.cells[0]?.text).toBe('A & B')
  expect(sheets[0]?.rows[1]?.cells[4]?.href).toBe('https://example.com/a?x=1&y=2')
  expect(sheets[0]?.rows[1]?.cells[5]?.text).toBe('15.11.2026')
  expect(sheets[0]?.rows[1]?.cells[6]?.text).toBe('2:08:49')
  expect(sheets[0]?.rows[1]?.cells[9]?.text).toBe('2')
  expect(sheets[0]?.rows[2]?.cells[0]?.text).toBe('Две части')
  expect(JSON.stringify(sheets)).not.toContain('Не показывать')
})

test('shapes cards, filters, and folds ё into е', () => {
  const sections = catalogFromWorkbook(workbookBytes())
  expect(sections.map((section) => section.id)).toEqual(['knigi', 'muzyka'])
  const books = sections[0]
  const first = books?.entries[0]
  expect(first?.title).toBe('A & B')
  expect(first?.person).toBe('Кейт')
  expect(first?.href).toBe('https://example.com/a?x=1&y=2')
  expect(first?.lead).toBe('Потому что тихо')
  expect(first?.chips.map((chip) => chip.label)).toEqual(['Смотрю', 'Очень понравилось'])
  expect(first?.footer).toEqual([
    { label: 'Дата рекомендации', value: '15.11.2026' },
    { label: 'Длительность', value: '2:08:49' },
    { label: 'Подборка', value: '2' },
    { label: 'Ссылка', value: 'Открыть', href: 'https://example.com/a?x=1&y=2' },
  ])
  expect(first?.id).toBe('knigi-2')
  const second = books?.entries[1]
  expect(second?.title).toBe('Две части')
  expect(second?.href).toBeUndefined()
  expect(second?.chips).toEqual([])
  expect(second?.status).toBe('Не отмечено')
  expect(books?.facetLabel).toBe('Раздел')
  expect(visibleEntries(books!, 'КЕЙТ', 'Фэнтези', 'Смотрю').map((entry) => entry.title)).toEqual(['A & B'])
  expect(visibleEntries(books!, '', 'Фэнтези', '')).toHaveLength(1)
  expect(visibleEntries(books!, '', '', '')).toHaveLength(2)
  expect(groupEntries(books!.entries, books!.facetLabel).map((group) => group.label)).toEqual(['Фэнтези', 'История'])

  const music = sections[1]
  expect(music?.entries[0]?.title).toBe('Ақпен бірге')
  expect(music?.entries[0]?.person).toBe('Moldanazar')
  expect(music?.entries[0]?.lead).toBe('Стартовая')
  expect(music?.facetLabel).toBe('Язык')
  expect(visibleEntries(music!, 'елка', '', '').map((entry) => entry.title)).toEqual(['Ёлка'])
})

test('slug and russian counts', () => {
  expect(slugify('Глубокие лекции')).toBe('glubokie-lektsii')
  expect(slugify('Фильмы и сериалы')).toBe('filmy-i-serialy')
  expect(ruCount(1, 'запись', 'записи', 'записей')).toBe('1 запись')
  expect(ruCount(2, 'запись', 'записи', 'записей')).toBe('2 записи')
  expect(ruCount(5, 'запись', 'записи', 'записей')).toBe('5 записей')
  expect(ruCount(21, 'раздел', 'раздела', 'разделов')).toBe('21 раздел')
})

test('rejects a broken download and an empty sheet', () => {
  expect(() => readWorkbook(new Uint8Array([1, 2, 3, 4]))).toThrow(WorkbookError)
  expect(catalogFromWorkbook(headerOnlyBytes())[0]?.entries).toEqual([])
})

test('loadCatalog reports a closed sheet and an empty one', async () => {
  const html = new Response('<html>login</html>', { status: 200 })
  await expect(loadCatalog(async () => html)).rejects.toBeInstanceOf(SheetError)
  await expect(loadCatalog(async () => new Response('', { status: 503 }))).rejects.toThrow('Не удалось прочитать таблицу.')
  await expect(loadCatalog(async () => {
    throw new Error('offline')
  })).rejects.toThrow('Не удалось прочитать таблицу.')
  const empty = new Response(headerOnlyBytes(), { status: 200 })
  await expect(loadCatalog(async () => empty)).rejects.toThrow('В таблице нет рекомендаций.')
})

import { strToU8, zipSync } from 'fflate'

export function workbookBytes(files: Record<string, string>): Uint8Array {
  const zipped: Record<string, Uint8Array> = {}
  for (const [name, text] of Object.entries(files)) zipped[name] = strToU8(text)
  return zipSync(zipped)
}

export function sheetWorkbook(sheetName: string, rowsXml: string): Uint8Array {
  return workbookBytes({
    'xl/workbook.xml': `<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="00_Dashboard" sheetId="1" r:id="rId1"/>
    <sheet name="${sheetName}" sheetId="2" r:id="rId2"/>
  </sheets>
</workbook>`,
    'xl/_rels/workbook.xml.rels': `<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
</Relationships>`,
    'xl/worksheets/sheet1.xml': `<?xml version="1.0"?><worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>ignore</t></is></c></row></sheetData></worksheet>`,
    'xl/worksheets/sheet2.xml': `<?xml version="1.0" encoding="UTF-8"?><worksheet><sheetData>${rowsXml}</sheetData></worksheet>`,
  })
}

export function inlineRow(row: number, values: string[]): string {
  const cells = values
    .map((value, index) => {
      const column = String.fromCharCode(65 + index)
      return `<c r="${column}${row}" t="inlineStr"><is><t>${escapeXml(value)}</t></is></c>`
    })
    .join('')
  return `<row r="${row}">${cells}</row>`
}

export function escapeXml(value: string): string {
  return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
}

export const HEADER = ['expression', 'pinyin', 'meaning_ru', 'unit_type', 'occurrences_in_fragment', 'fragment_id', 'anki_status', 'context_example', 'notes']

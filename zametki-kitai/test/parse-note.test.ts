import { expect, test } from 'bun:test'
import { decodeBase64, parseNoteHtml } from '../src/parse-note'

const FIXTURE = `<html><body><meta charset="utf-8"><style>
.title { font-weight: bold; content: "ignore me"; }
</style><h1 class="title">09.09.2026 Турфан</h1><p></p><div align="left">
 <span style="font-size:16">Первая&nbsp;строка.</span>
</div><div align="left">
 <div><img src="data:image/png;base64,aGk=" style="height: auto">
 </div>
</div><div align="left">
 <span style="font-size:16">Вторая строка<br>и третья.</span>
</div></body></html>`

test('keeps paragraphs and photos in reading order', () => {
  const note = parseNoteHtml(FIXTURE)
  expect(note.title).toBe('09.09.2026 Турфан')
  expect(note.blocks.map((block) => (block.t === 'p' ? block.text : 'img'))).toEqual([
    'Первая строка.',
    'img',
    'Вторая строка',
    'и третья.',
  ])
  const photo = note.blocks[1]
  expect(photo?.t).toBe('img')
  if (photo?.t === 'img') expect(photo.bytes).toEqual(decodeBase64('aGk='))
})

test('does not keep stylesheet text', () => {
  const note = parseNoteHtml(FIXTURE)
  const texts = note.blocks.filter((block) => block.t === 'p').map((block) => block.text)
  expect(texts.join(' ')).not.toContain('font-weight')
  expect(texts.join(' ')).not.toContain('ignore me')
})

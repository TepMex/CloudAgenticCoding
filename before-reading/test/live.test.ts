import { expect, test } from 'bun:test'
import { buildCards } from '../src/cards'
import { examplePinyin, splitPinyin } from '../src/phrase-pinyin'
import { DEFAULT_WORKBOOK_URL, loadEntries, SHEET_NAME, visibleExample } from '../src/vocab'

const HAN = /^\p{Script=Han}+$/u

test('the published current-vocab sheet becomes cards in both directions', async () => {
  const entries = await loadEntries(DEFAULT_WORKBOOK_URL)
  expect(entries.length).toBeGreaterThan(0)
  for (const entry of entries) {
    expect(entry.expression.length).toBeGreaterThan(0)
    const cards = buildCards([entry])
    expect(cards.filter((card) => card.direction === 'expression')).toHaveLength(1)
    expect(cards.filter((card) => card.direction === 'meaning').map((card) => card.prompt)).toEqual(entry.glosses)
    if (HAN.test(entry.expression)) {
      expect(splitPinyin(entry.pinyin), entry.expression).toHaveLength([...entry.expression].length)
    }
    const example = visibleExample(entry)
    if (example) expect(examplePinyin(example, entry.expression, entry.pinyin).length, example).toBeGreaterThan(0)
  }
  expect(SHEET_NAME).toBe('12_NewVocabCurrent')
}, 20000)

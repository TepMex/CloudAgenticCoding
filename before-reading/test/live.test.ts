import { expect, test } from 'bun:test'
import { buildCards } from '../src/cards'
import { DEFAULT_WORKBOOK_URL, loadEntries, SHEET_NAME } from '../src/vocab'

test('the published current-vocab sheet becomes cards in both directions', async () => {
  const entries = await loadEntries(DEFAULT_WORKBOOK_URL)
  expect(entries.length).toBeGreaterThan(0)
  for (const entry of entries) {
    expect(entry.expression.length).toBeGreaterThan(0)
    const cards = buildCards([entry])
    expect(cards.filter((card) => card.direction === 'expression')).toHaveLength(1)
    expect(cards.filter((card) => card.direction === 'meaning').map((card) => card.prompt)).toEqual(entry.glosses)
  }
  expect(SHEET_NAME).toBe('12_NewVocabCurrent')
}, 20000)

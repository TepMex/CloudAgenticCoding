import { expect, test } from 'bun:test'
import { buildCards } from '../src/cards'
import { splitGlosses } from '../src/gloss'
import { ankiStatusLabel, ruCards, ruWords, unitTypeLabel } from '../src/labels'
import { readCachedEntries, resolveWorkbookUrl, workbookHtmlUrl, writeCachedEntries } from '../src/source'
import { entriesFromWorkbook, fragmentChoices, visibleExample, VocabError } from '../src/vocab'
import { HEADER, inlineRow, sheetWorkbook, workbookBytes } from './workbook'

const header = inlineRow(1, HEADER)

test('splits gloss variants and keeps parenthetical commas', () => {
  expect(splitGlosses('оберегать, благословлять')).toEqual(['оберегать', 'благословлять'])
  expect(splitGlosses('в, во время (чего-либо)')).toEqual(['в', 'во время (чего-либо)'])
  expect(splitGlosses('дым благовоний (не сигареты здесь)')).toEqual(['дым благовоний (не сигареты здесь)'])
  expect(splitGlosses('Гу Юэ — название клана')).toEqual(['Гу Юэ', 'название клана'])
  expect(splitGlosses('одетый во что-либо; весь наряд')).toEqual(['одетый во что-либо', 'весь наряд'])
  expect(splitGlosses('род / клан')).toEqual(['род', 'клан'])
  expect(splitGlosses('  ')).toEqual([])
  expect(splitGlosses('а, а')).toEqual(['а', 'а'])
})

test('builds a forward card and one reverse card per gloss', () => {
  const bytes = sheetWorkbook(
    '12_NewVocabCurrent',
    [
      header,
      inlineRow(2, ['保佑', 'bǎoyòu', 'оберегать, благословлять', 'word', '1.0', 'F-1', 'absent_from_anki', '列祖列宗保佑', '']),
      inlineRow(3, ['古月', 'gǔyuè', 'Гу Юэ — название клана', 'proper_name', '2', 'F-1', 'anki_unstarted', '古月', 'клан']),
      inlineRow(4, ['', 'x', 'пусто', 'word', '', 'F-1', '', '', '']),
      inlineRow(5, ['两鬓微霜', 'liǎngbìn wēishuāng', '', 'literary_phrase', '', 'F-2', 'absent_from_anki', '', '']),
    ].join(''),
  )
  const entries = entriesFromWorkbook(bytes)
  expect(entries.map((entry) => entry.expression)).toEqual(['保佑', '古月', '两鬓微霜'])
  expect(entries[0]?.glosses).toEqual(['оберегать', 'благословлять'])
  expect(entries[0]?.occurrences).toBe('1')
  expect(entries[1]?.occurrences).toBe('2')
  expect(entries[1]?.notes).toBe('клан')
  expect(visibleExample(entries[0]!)).toBe('列祖列宗保佑')
  expect(visibleExample(entries[1]!)).toBe('')
  expect(fragmentChoices(entries)).toEqual([
    { id: 'F-1', count: 2 },
    { id: 'F-2', count: 1 },
  ])

  const cards = buildCards(entries.filter((entry) => entry.expression === '保佑'))
  expect(cards.map((card) => [card.direction, card.prompt])).toEqual([
    ['expression', '保佑'],
    ['meaning', 'оберегать'],
    ['meaning', 'благословлять'],
  ])
  expect(buildCards([entries[2]!]).map((card) => card.direction)).toEqual(['expression'])
})

test('ignores other sheets and reports a missing current sheet', () => {
  expect(() => entriesFromWorkbook(sheetWorkbook('11_NewVocab', header))).toThrow(VocabError)
  try {
    entriesFromWorkbook(sheetWorkbook('11_NewVocab', header))
  } catch (error) {
    expect(error).toBeInstanceOf(VocabError)
    expect((error as VocabError).code).toBe('sheet')
  }
})

test('requires the expression and meaning columns', () => {
  const bytes = sheetWorkbook('12_NewVocabCurrent', inlineRow(1, ['expression', 'pinyin']))
  expect(() => entriesFromWorkbook(bytes)).toThrow(VocabError)
})

test('rejects a workbook that is not a spreadsheet', () => {
  expect(() => entriesFromWorkbook(workbookBytes({ 'hello.txt': 'nope' }))).toThrow(VocabError)
})

test('labels the fields that are already in the sheet', () => {
  expect(unitTypeLabel('fixed_expression')).toBe('устойчивое выражение')
  expect(unitTypeLabel('future_kind')).toBe('future kind')
  expect(ankiStatusLabel('anki_unstarted')).toBe('в Anki, ещё не начато')
  expect(ankiStatusLabel('')).toBe('')
  expect(ruWords(1)).toBe('1 слово')
  expect(ruWords(2)).toBe('2 слова')
  expect(ruWords(5)).toBe('5 слов')
  expect(ruCards(11)).toBe('11 карточек')
  expect(ruCards(21)).toBe('21 карточка')
})

test('keeps the published workbook url unless an https source is given', () => {
  const fallback = 'https://example.com/book.xlsx'
  expect(resolveWorkbookUrl('', fallback)).toBe(fallback)
  expect(resolveWorkbookUrl('?src=http://example.com/a.xlsx', fallback)).toBe(fallback)
  expect(resolveWorkbookUrl('?src=javascript:alert(1)', fallback)).toBe(fallback)
  expect(resolveWorkbookUrl('?src=https://example.com/other.xlsx?output=xlsx', fallback)).toBe(
    'https://example.com/other.xlsx?output=xlsx',
  )
  expect(workbookHtmlUrl('https://example.com/book?output=xlsx')).toBe('https://example.com/book?output=html')
})

test('round-trips the cached sheet for the same url', () => {
  const memory = new MemoryStorage()
  const entries = entriesFromWorkbook(
    sheetWorkbook('12_NewVocabCurrent', [header, inlineRow(2, ['中', 'zhōng', 'в, во время (чего-либо)', 'word', '1', 'F-9', 'absent_from_anki', '大典中', ''])].join('')),
  )
  writeCachedEntries(memory, 'https://example.com/a.xlsx', entries)
  expect(readCachedEntries(memory, 'https://example.com/a.xlsx')?.[0]?.glosses).toEqual(['в', 'во время (чего-либо)'])
  expect(readCachedEntries(memory, 'https://example.com/b.xlsx')).toBeNull()
  memory.setItem('before-reading:entries:v1', '{')
  expect(readCachedEntries(memory, 'https://example.com/a.xlsx')).toBeNull()
})

class MemoryStorage {
  private readonly values = new Map<string, string>()
  getItem(key: string): string | null {
    return this.values.get(key) ?? null
  }
  setItem(key: string, value: string) {
    this.values.set(key, value)
  }
}

import { expect, test } from 'bun:test'
import { buildCards, gradeQueue, shuffleCards, type Card } from '../src/cards'
import type { VocabEntry } from '../src/vocab'

function entry(row: number, expression: string, glosses: string[]): VocabEntry {
  return {
    row,
    expression,
    pinyin: 'pīn',
    meaning: glosses.join(', '),
    glosses,
    unitType: 'word',
    occurrences: '1',
    fragmentId: 'F-1',
    ankiStatus: 'absent_from_anki',
    example: '',
    notes: '',
  }
}

function ids(cards: readonly Card[]): string[] {
  return cards.map((card) => card.id)
}

test('again returns a few cards later and good removes the card', () => {
  const cards = buildCards([entry(2, '保佑', ['оберегать', 'благословлять']), entry(3, '此次', ['в этот раз'])])
  expect(ids(cards)).toEqual(['2:expression', '2:meaning:0', '2:meaning:1', '3:expression', '3:meaning:0'])

  const afterAgain = gradeQueue(cards, 'again', 3)
  expect(ids(afterAgain)).toEqual(['2:meaning:0', '2:meaning:1', '3:expression', '2:expression', '3:meaning:0'])

  const afterGood = gradeQueue(cards, 'good')
  expect(ids(afterGood)).toEqual(['2:meaning:0', '2:meaning:1', '3:expression', '3:meaning:0'])
})

test('the last card stays when it is marked again', () => {
  const cards = buildCards([entry(8, '中', ['в'])]).slice(0, 1)
  expect(ids(gradeQueue(cards, 'again'))).toEqual(['8:expression'])
  expect(gradeQueue(cards, 'good')).toEqual([])
  expect(gradeQueue([], 'good')).toEqual([])
})

test('shuffle uses the provided random source', () => {
  const cards = buildCards([entry(1, '甲', ['а']), entry(2, '乙', ['б'])])
  let cursor = 0
  const sequence = [0, 0, 0.99]
  const shuffled = shuffleCards(cards, () => sequence[cursor++] ?? 0)
  expect(ids(shuffled)).not.toEqual(ids(cards))
  expect(shuffled).toHaveLength(cards.length)
})

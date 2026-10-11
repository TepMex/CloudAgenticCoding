import { expect, test } from 'bun:test'
import { appendDayIllustrations } from '../src/day-art'
import { packJournal, unpackJournal } from '../src/codec'
import type { Journal } from '../src/types'

const journal: Journal = {
  title: 'Китай',
  kicker: 'Путевые заметки',
  range: '5–6 сентября 2026',
  days: [
    {
      id: '2026-09-05',
      navDate: '5 сен',
      navPlace: 'Исилькуль',
      heading: 'суббота, 5 сентября',
      place: 'Исилькуль',
      blocks: [
        { t: 'p', text: 'Слойка' },
        { t: 'img', o: 0, n: 3 },
      ],
    },
    {
      id: '2026-09-06',
      navDate: '6 сен',
      navPlace: 'Астана',
      heading: 'воскресенье, 6 сентября',
      place: 'Астана',
      blocks: [{ t: 'p', text: 'Перелёт' }],
    },
  ],
}

test('appends one illustration after the original blocks of each day', () => {
  const images = new Uint8Array([1, 2, 3])
  const art = new Map<string, Uint8Array>([
    ['2026-09-05', new Uint8Array([9, 9])],
    ['2026-09-06', new Uint8Array([8, 8, 8])],
  ])
  const next = appendDayIllustrations(journal, images, art)
  expect(next.journal.days[0]?.blocks).toEqual([
    { t: 'p', text: 'Слойка' },
    { t: 'img', o: 0, n: 3 },
    { t: 'img', o: 3, n: 2, k: 'day' },
  ])
  expect(next.journal.days[1]?.blocks.at(-1)).toEqual({ t: 'img', o: 5, n: 3, k: 'day' })
  expect(Array.from(next.images)).toEqual([1, 2, 3, 9, 9, 8, 8, 8])

  const round = unpackJournal(packJournal(next.journal, next.images))
  expect(round.journal.days[1]?.blocks.at(-1)).toEqual({ t: 'img', o: 5, n: 3, k: 'day' })
  expect(Array.from(round.images)).toEqual([1, 2, 3, 9, 9, 8, 8, 8])
})

test('leaves a day that already has an illustration untouched', () => {
  const once = appendDayIllustrations(journal, new Uint8Array([1, 2, 3]), new Map([
    ['2026-09-05', new Uint8Array([4])],
    ['2026-09-06', new Uint8Array([5])],
  ]))
  const twice = appendDayIllustrations(once.journal, once.images, new Map())
  expect(twice.journal.days.map((day) => day.blocks.filter((block) => block.t === 'img' && block.k === 'day'))).toEqual(
    once.journal.days.map((day) => day.blocks.filter((block) => block.t === 'img' && block.k === 'day')),
  )
  expect(twice.images).toEqual(once.images)
})

test('refuses to seal a day with no illustration', () => {
  expect(() => appendDayIllustrations(journal, new Uint8Array([1, 2, 3]), new Map())).toThrow(/2026-09-05/)
})

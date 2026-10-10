import { expect, test } from 'bun:test'
import { dayHeading, formatRange, navDateLabel, parseTitle, weekdayName } from '../src/dates'

test('parses an export title into a calendar date and place', () => {
  const date = parseTitle('05.09.2026 Исилькуль, Петропавловск, Астана')
  expect(date).toEqual({
    iso: '2026-09-05',
    place: 'Исилькуль, Петропавловск, Астана',
    day: 5,
    month: 9,
    year: 2026,
  })
  expect(date && weekdayName(date.year, date.month, date.day)).toBe('суббота')
  expect(date && navDateLabel(date)).toBe('5 сен')
  expect(date && dayHeading(date)).toBe('Суббота, 5 сентября')
})

test('formats a range inside one month', () => {
  const first = parseTitle('05.09.2026 Астана')
  const last = parseTitle('29.09.2026 Астана')
  expect(first && last && formatRange(first, last)).toBe('5–29 сентября 2026')
})

test('rejects a title without a date', () => {
  expect(parseTitle('просто текст')).toBeNull()
})

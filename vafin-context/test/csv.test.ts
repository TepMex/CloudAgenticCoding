import { expect, test } from 'bun:test'
import { parseCsv } from '../src/csv'

const SHEET = [
  'Номер,Название выпуска,Контекст и воспоминания',
  '78,Динозавры,',
  '77,Кайнозойская эра,Начал слушать когда возвращался со стрижки 6 августа 2026.',
  '76,Про грибы,"Слушал когда ходил в качалку в 25 июля 2026,болела голова, думал о переговорах с клиникой ""Будь здоров""."',
  '75,Чтение,"Первый абзац.',
  '',
  'Второй абзац."',
  '38,"Наука это дворовые слухи, коммунистка в лагерях",',
].join('\r\n') + '\r\n'

test('keeps quotes, commas, and a blank line inside a cell', () => {
  const rows = parseCsv('\uFEFF' + SHEET)
  expect(rows).toHaveLength(6)
  expect(rows[0]).toEqual(['Номер', 'Название выпуска', 'Контекст и воспоминания'])
  expect(rows[1]).toEqual(['78', 'Динозавры', ''])
  expect(rows[3][2]).toBe('Слушал когда ходил в качалку в 25 июля 2026,болела голова, думал о переговорах с клиникой "Будь здоров".')
  expect(rows[4][2]).toBe('Первый абзац.\n\nВторой абзац.')
  expect(rows[5][1]).toBe('Наука это дворовые слухи, коммунистка в лагерях')
})

test('does not invent a row after the final newline', () => {
  expect(parseCsv('a,b\r\n1,2\r\n')).toEqual([
    ['a', 'b'],
    ['1', '2'],
  ])
})

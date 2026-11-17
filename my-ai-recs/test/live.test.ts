import { expect, test } from 'bun:test'
import { loadCatalog } from '../src/catalog'

test('published workbook becomes one section per sheet', async () => {
  const sections = await loadCatalog()
  expect(sections.map((section) => section.name)).toEqual([
    'Глубокие лекции',
    'Музыка',
    'Книги',
    'Аниме',
    'Фильмы и сериалы',
    'Подкасты',
    'Игры',
  ])
  const lectures = sections[0]
  const china = lectures?.entries.find((entry) => entry.title === 'Китай и история. Особые отношения')
  expect(china?.person).toBe('Сергей Дмитриев')
  expect(china?.chips.some((chip) => chip.label === 'Смотрю')).toBe(true)
  expect(china?.footer.find((item) => item.label === 'Длительность')?.value).toBe('2:08:49')
  expect(china?.href).toContain('youtube.com')

  const books = sections.find((section) => section.name === 'Книги')
  expect(books?.facetLabel).toBe('Раздел')
  const novel = books?.entries.find((entry) => entry.title === 'Reverend Insanity')
  expect(novel?.href).toContain('webnovel.com')

  const anime = sections.find((section) => section.name === 'Аниме')
  const frieren = anime?.entries.find((entry) => entry.title === 'Фрирен')
  expect(frieren?.original).toBe('Sousou no Frieren')
  expect(anime?.facetLabel).toBe('Формат')

  const games = sections.find((section) => section.name === 'Игры')
  expect(games?.entries.some((entry) => entry.title === 'Turing Complete')).toBe(true)
  expect(games?.facetLabel).toBe('Тип / учебная задача')
  expect(sections.every((section) => section.entries.every((entry) => entry.title !== 'Не отмечено'))).toBe(true)
}, 20000)
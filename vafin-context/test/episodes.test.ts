import { expect, test } from 'bun:test'
import { episodesFromCsv, loadEpisodes, ruCount, selectEpisodes, SheetError } from '../src/episodes'

const SHEET = [
  'Номер,Название выпуска,Контекст и воспоминания ',
  '78,Динозавры, ',
  '77,Кайнозойская эра,Начал слушать когда возвращался со стрижки.',
  '76,Про грибы,"Слушал в качалке, думал о клинике ""Будь здоров""."',
  '75,Чтение,"Гулял до метромоста.',
  '',
  'Островские улицы как на даче."',
  '38,"Наука это дворовые слухи, коммунистка в лагерях",',
  '77,Повтор,Ещё один 77.',
  ',пустая строка,',
].join('\r\n')

test('reads episodes in sheet order and splits paragraphs', () => {
  const episodes = episodesFromCsv(SHEET)
  expect(episodes.map((episode) => episode.number)).toEqual([78, 77, 76, 75, 38, 77])
  expect(episodes[0]?.paragraphs).toEqual([])
  expect(episodes[0]?.id).toBe('ep-78')
  expect(episodes[1]?.id).toBe('ep-77-1')
  expect(episodes[5]?.id).toBe('ep-77-2')
  expect(episodes[2]?.paragraphs[0]).toContain('"Будь здоров"')
  expect(episodes[3]?.paragraphs).toEqual(['Гулял до метромоста.', 'Островские улицы как на даче.'])
  expect(episodes[4]?.title).toBe('Наука это дворовые слухи, коммунистка в лагерях')
})

test('search and the context switch narrow the list', () => {
  const episodes = episodesFromCsv(SHEET)
  expect(selectEpisodes(episodes, '  остров ', 'all').map((episode) => episode.number)).toEqual([75])
  expect(selectEpisodes(episodes, '77', 'all')).toHaveLength(2)
  expect(selectEpisodes(episodes, '', 'with-context').some((episode) => episode.number === 78)).toBe(false)
  expect(selectEpisodes(episodes, 'дино', 'with-context')).toEqual([])
})

test('declines выпуск', () => {
  expect(ruCount(1, 'выпуск', 'выпуска', 'выпусков')).toBe('1 выпуск')
  expect(ruCount(2, 'выпуск', 'выпуска', 'выпусков')).toBe('2 выпуска')
  expect(ruCount(5, 'выпуск', 'выпуска', 'выпусков')).toBe('5 выпусков')
  expect(ruCount(11, 'выпуск', 'выпуска', 'выпусков')).toBe('11 выпусков')
  expect(ruCount(21, 'выпуск', 'выпуска', 'выпусков')).toBe('21 выпуск')
})

test('loadEpisodes rejects a closed sheet and an empty one', async () => {
  const html = new Response('<html>login</html>', { status: 200, headers: { 'content-type': 'text/html' } })
  await expect(loadEpisodes(async () => html)).rejects.toBeInstanceOf(SheetError)

  const empty = new Response('Номер,Название,Контекст\n', { status: 200, headers: { 'content-type': 'text/csv' } })
  await expect(loadEpisodes(async () => empty)).rejects.toThrow('В таблице нет выпусков.')

  await expect(loadEpisodes(async () => new Response('', { status: 503 }))).rejects.toThrow('Не удалось прочитать таблицу.')
  await expect(
    loadEpisodes(async () => {
      throw new Error('offline')
    }),
  ).rejects.toThrow('Не удалось прочитать таблицу.')
})

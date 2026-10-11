import { expect, test } from 'bun:test'
import { neighborsFor, stopsForDay, stopsFromTitle } from '../src/places'
import { buildTripMap } from '../src/trip-map'

const TITLES: Array<[string, string, string[]]> = [
  ['2026-09-05', 'Исилькуль, Петропавловск, Астана', ['isilkul', 'petropavl', 'astana']],
  ['2026-09-06', 'Астана, перелет в Урумчи', ['astana', 'urumqi']],
  ['2026-09-07', 'Урумчи', ['urumqi']],
  ['2026-09-08', 'Турфан', ['turpan']],
  ['2026-09-09', 'Турфан', ['turpan']],
  ['2026-09-10', 'Цзяюйгуань', ['jiayuguan']],
  ['2026-09-11', 'Цзяюйгуань - Синин', ['jiayuguan', 'xining']],
  ['2026-09-12', 'Синин - Чэнду', ['xining', 'chengdu']],
  ['2026-09-13', 'Чэнду день первый', ['chengdu']],
  ['2026-09-14', 'Чэнду день второй', ['chengdu']],
  ['2026-09-15', 'Чэнду 3 Сансиньдуй', ['chengdu', 'sanxingdui']],
  ['2026-09-16', 'Чэнду, усталость', ['chengdu']],
  ['2026-09-17', 'Дуцзянъян, Чэнду - Сичан', ['dujiangyan', 'chengdu', 'xichang']],
  ['2026-09-18', 'Сичан', ['xichang']],
  ['2026-09-19', 'Сичан, подъем на Лушан', ['xichang', 'lushan']],
  ['2026-09-20', 'Сичан, усталость', ['xichang']],
  ['2026-09-21', 'Сичан - Куньмин', ['xichang', 'kunming']],
  ['2026-09-22', 'Куньмин, прогулки', ['kunming']],
  ['2026-09-23', 'Куньмин, чайный рынок', ['kunming']],
  ['2026-09-24', 'Куньмин, Кембрийский взрыв и цветочный рынок', ['kunming']],
  ['2026-09-25', 'Куньмин - Чунцин', ['kunming', 'chongqing']],
  ['2026-09-26', 'Чунцин', ['chongqing']],
  ['2026-09-27', 'Чунцин - Урумчи - Астана', ['chongqing', 'urumqi', 'astana']],
  ['2026-09-28', 'Астана', ['astana']],
  ['2026-09-29', 'Астана', ['astana']],
]

test('reads cities from the note title, in travel order', () => {
  for (const [, title, ids] of TITLES) {
    expect(stopsFromTitle(title).map((place) => place.id)).toEqual(ids)
  }
})

test('adds a city from the prose only when that day actually goes there', () => {
  const hami = stopsForDay('2026-09-09', 'Турфан', ['купил билеты до Хами'])
  expect(hami.map((place) => place.id)).toEqual(['turpan', 'hami'])

  const planned = stopsForDay('2026-09-08', 'Турфан', ['ночёвка в отеле в Хами ещё впереди'])
  expect(planned.map((place) => place.id)).toEqual(['turpan'])

  const guiyang = stopsForDay('2026-09-25', 'Куньмин - Чунцин', ['станция Гуйань'])
  expect(guiyang.map((place) => place.id)).toEqual(['kunming', 'guiyang', 'chongqing'])

  const chengjiang = stopsForDay('2026-09-24', 'Куньмин, Кембрийский взрыв и цветочный рынок', [
    'Музей Чэнцзяна',
  ])
  expect(chengjiang.map((place) => place.id)).toEqual(['kunming', 'chengjiang'])
})

test('declined city names still count', () => {
  const stops = stopsForDay('2026-09-11', 'Цзяюйгуань - Синин', ['ехали до Синина и гуляли по Синину'])
  expect(stops.map((place) => place.id)).toEqual(['jiayuguan', 'xining'])
})

test('neighbor countries appear only when the day leaves China or reaches Hami', () => {
  expect(neighborsFor(stopsFromTitle('Урумчи'))).toEqual([])
  expect(neighborsFor(stopsFromTitle('Астана, перелет в Урумчи'))).toEqual(['kz'])
  expect(neighborsFor(stopsFromTitle('Исилькуль, Петропавловск, Астана'))).toEqual(['kz', 'ru'])
  expect(neighborsFor(stopsForDay('2026-09-09', 'Турфан', ['до Хами']))).toEqual(['mn'])
})

test('the map marks stops, draws arrows for real hops, and names the route', () => {
  const stay = buildTripMap('2026-09-07', 'Урумчи', [])
  expect(stay?.pins).toHaveLength(1)
  expect(stay?.routes).toHaveLength(0)
  expect(stay?.lands.some((land) => land.id === 'kz')).toBe(false)
  expect(stay?.aria).toBe('Карта: Урумчи')

  const hop = buildTripMap('2026-09-06', 'Астана, перелет в Урумчи', [])
  expect(hop?.routes).toHaveLength(1)
  expect(hop?.lands.map((land) => land.id)).toEqual(['kz', 'cn'])
  expect(hop?.aria).toBe('Карта: Астана → Урумчи')
  const nums = hop!.routes[0]!.match(/-?\d+\.\d+/g)!.map(Number)
  const endX = nums.at(-2)!
  const endY = nums.at(-1)!
  const astanaPin = hop!.pins[0]!
  const urumqiPin = hop!.pins[1]!
  const toUrumqi = Math.hypot(endX - urumqiPin.x, endY - urumqiPin.y)
  const toAstana = Math.hypot(endX - astanaPin.x, endY - astanaPin.y)
  expect(toUrumqi).toBeLessThan(toAstana)

  const climb = buildTripMap('2026-09-19', 'Сичан, подъем на Лушан', [])
  expect(climb?.pins).toHaveLength(2)
  expect(climb?.routes).toHaveLength(0)
  const climbLabels = climb!.labels.filter((label) => label.kind === 'city')
  expect(climbLabels.map((label) => label.text)).toEqual(['Сичан', 'Лушань'])
  expect(Math.hypot(climbLabels[0]!.x - climbLabels[1]!.x, climbLabels[0]!.y - climbLabels[1]!.y)).toBeGreaterThan(12)

  const sideTrip = buildTripMap('2026-09-15', 'Чэнду 3 Сансиньдуй', [])
  expect(sideTrip?.routes).toHaveLength(1)

  const urumqi = stay!.pins[0]!
  const chengdu = buildTripMap('2026-09-13', 'Чэнду день первый', [])!.pins[0]!
  expect(urumqi.x).toBeLessThan(chengdu.x)
  expect(urumqi.y).toBeLessThan(chengdu.y)
})

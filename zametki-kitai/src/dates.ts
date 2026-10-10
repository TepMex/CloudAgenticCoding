const MONTHS = [
  'января',
  'февраля',
  'марта',
  'апреля',
  'мая',
  'июня',
  'июля',
  'августа',
  'сентября',
  'октября',
  'ноября',
  'декабря',
]

const MONTHS_SHORT = [
  'янв',
  'фев',
  'мар',
  'апр',
  'мая',
  'июн',
  'июл',
  'авг',
  'сен',
  'окт',
  'ноя',
  'дек',
]

const WEEKDAYS = [
  'воскресенье',
  'понедельник',
  'вторник',
  'среда',
  'четверг',
  'пятница',
  'суббота',
]

export type NoteDate = {
  iso: string
  place: string
  day: number
  month: number
  year: number
}

export function parseTitle(title: string): NoteDate | null {
  const match = title.match(/^(\d{2})\.(\d{2})\.(\d{4})\s+(.+)$/)
  if (!match) return null
  const day = Number(match[1])
  const month = Number(match[2])
  const year = Number(match[3])
  if (month < 1 || month > 12 || day < 1 || day > 31) return null
  return {
    iso: `${match[3]}-${match[2]}-${match[1]}`,
    place: match[4].trim(),
    day,
    month,
    year,
  }
}

export function weekdayName(year: number, month: number, day: number): string {
  const date = new Date(Date.UTC(year, month - 1, day))
  return WEEKDAYS[date.getUTCDay()] ?? ''
}

export function navDateLabel(date: NoteDate): string {
  return `${date.day} ${MONTHS_SHORT[date.month - 1]}`
}

export function dayHeading(date: NoteDate): string {
  const weekday = weekdayName(date.year, date.month, date.day)
  const capital = weekday.charAt(0).toUpperCase() + weekday.slice(1)
  return `${capital}, ${date.day} ${MONTHS[date.month - 1]}`
}

export function formatRange(first: NoteDate, last: NoteDate): string {
  const firstMonth = MONTHS[first.month - 1]
  const lastMonth = MONTHS[last.month - 1]
  if (first.year === last.year && first.month === last.month) {
    return `${first.day}–${last.day} ${firstMonth} ${first.year}`
  }
  if (first.year === last.year) {
    return `${first.day} ${firstMonth} – ${last.day} ${lastMonth} ${first.year}`
  }
  return `${first.day} ${firstMonth} ${first.year} – ${last.day} ${lastMonth} ${last.year}`
}

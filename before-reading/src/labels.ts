const UNIT_TYPES: Record<string, string> = {
  word: 'слово',
  fixed_expression: 'устойчивое выражение',
  book_term: 'термин книги',
  proper_name: 'имя собственное',
  literary_phrase: 'литературный оборот',
}

const ANKI_STATUS: Record<string, string> = {
  absent_from_anki: 'нет в Anki',
  anki_unstarted: 'в Anki, ещё не начато',
}

export function unitTypeLabel(value: string): string {
  const key = value.trim()
  if (!key) return ''
  return UNIT_TYPES[key] ?? key.replaceAll('_', ' ')
}

export function ankiStatusLabel(value: string): string {
  const key = value.trim()
  if (!key) return ''
  return ANKI_STATUS[key] ?? key.replaceAll('_', ' ')
}

export function ruWords(count: number): string {
  return `${count} ${ruPlural(count, 'слово', 'слова', 'слов')}`
}

export function ruCards(count: number): string {
  return `${count} ${ruPlural(count, 'карточка', 'карточки', 'карточек')}`
}

function ruPlural(count: number, one: string, few: string, many: string): string {
  const n10 = count % 10
  const n100 = count % 100
  if (n10 === 1 && n100 !== 11) return one
  if (n10 >= 2 && n10 <= 4 && (n100 < 12 || n100 > 14)) return few
  return many
}

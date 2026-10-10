export type ParagraphBlock = { t: 'p'; text: string }

export type PhotoBlock = { t: 'img'; o: number; n: number }

export type Block = ParagraphBlock | PhotoBlock

export type Day = {
  id: string
  navDate: string
  navPlace: string
  heading: string
  place: string
  blocks: Block[]
}

export type Journal = {
  title: string
  kicker: string
  range: string
  days: Day[]
}

export type RawBlock = { t: 'p'; text: string } | { t: 'img'; bytes: Uint8Array }

export type RawNote = {
  title: string
  blocks: RawBlock[]
}

import { CHINA, KAZAKHSTAN, MONGOLIA, RUSSIA_BORDER } from './land'
import { neighborsFor, stopsForDay, type NeighborId, type Place } from './places'

export const MAP_WIDTH = 520
const PAD = 36
const FONT = 16
const CHAR_W = 8.6

const CHINA_FRAME = { lon0: 73.0, lon1: 135.3, lat0: 17.4, lat1: 53.9 }

type Frame = { lon0: number; lon1: number; lat0: number; lat1: number }
type Pt = { x: number; y: number }
type Box = { x: number; y: number; w: number; h: number }

export type MapLabel = {
  text: string
  x: number
  y: number
  anchor: 'start' | 'end' | 'middle'
  kind: 'city' | 'country'
  leader: { x1: number; y1: number; x2: number; y2: number } | null
}

export type TripMapModel = {
  viewBox: string
  width: number
  height: number
  aria: string
  lands: { id: string; d: string }[]
  routes: string[]
  pins: { id: string; x: number; y: number }[]
  labels: MapLabel[]
}

export function buildTripMap(dayId: string, placeLine: string, paragraphs: string[]): TripMapModel | null {
  const stops = stopsForDay(dayId, placeLine, paragraphs)
  if (stops.length === 0) return null
  const neighbors = neighborsFor(stops)
  const frame = frameFor(stops)
  const height = frameHeight(frame)
  const project = projector(frame, height)
  const lands = landPaths(neighbors, project)
  const projected = stops.map((stop) => project(stop.lon, stop.lat))
  const placed = spreadCloseHops(stops, projected)
  const pins = stops.map((stop, index) => ({ id: stop.id, ...placed[index]! }))
  const routes = routePaths(stops, placed)
  const labels = layoutLabels(stops, pins, neighbors, project, height)
  return {
    viewBox: `0 0 ${MAP_WIDTH} ${height.toFixed(1)}`,
    width: MAP_WIDTH,
    height,
    aria: ariaLabel(stops),
    lands,
    routes,
    pins,
    labels,
  }
}

export function frameFor(stops: Place[]): Frame {
  let lon0 = CHINA_FRAME.lon0
  let lon1 = CHINA_FRAME.lon1
  let lat0 = CHINA_FRAME.lat0
  let lat1 = CHINA_FRAME.lat1
  for (const stop of stops) {
    lon0 = Math.min(lon0, stop.lon - 3.4)
    lon1 = Math.max(lon1, stop.lon + 2.2)
    lat0 = Math.min(lat0, stop.lat - 1.4)
    lat1 = Math.max(lat1, stop.lat + (stop.lat > 54 ? 4.4 : 1.8))
  }
  return { lon0, lon1, lat0, lat1 }
}

function frameHeight(frame: Frame): number {
  const cos = Math.cos((((frame.lat0 + frame.lat1) / 2) * Math.PI) / 180)
  const worldW = (frame.lon1 - frame.lon0) * cos
  const worldH = frame.lat1 - frame.lat0
  const scale = (MAP_WIDTH - PAD * 2) / worldW
  return worldH * scale + PAD * 2
}

function projector(frame: Frame, height: number) {
  const cos = Math.cos((((frame.lat0 + frame.lat1) / 2) * Math.PI) / 180)
  const worldW = (frame.lon1 - frame.lon0) * cos
  const worldH = frame.lat1 - frame.lat0
  const scale = Math.min((MAP_WIDTH - PAD * 2) / worldW, (height - PAD * 2) / worldH)
  return (lon: number, lat: number): Pt => ({
    x: PAD + (lon - frame.lon0) * cos * scale,
    y: PAD + (frame.lat1 - lat) * scale,
  })
}

function landPaths(neighbors: NeighborId[], project: (lon: number, lat: number) => Pt) {
  const lands: { id: string; d: string }[] = []
  if (neighbors.includes('ru')) lands.push({ id: 'ru', d: ringsPath(RUSSIA_BORDER, project) })
  if (neighbors.includes('kz')) lands.push({ id: 'kz', d: ringsPath(KAZAKHSTAN, project) })
  if (neighbors.includes('mn')) lands.push({ id: 'mn', d: ringsPath(MONGOLIA, project) })
  lands.push({ id: 'cn', d: ringsPath(CHINA, project) })
  return lands
}

function ringsPath(rings: Array<Array<[number, number]>>, project: (lon: number, lat: number) => Pt): string {
  return rings
    .map((ring) => {
      const cmds = ring.map(([lon, lat], index) => {
        const point = project(lon, lat)
        return `${index === 0 ? 'M' : 'L'}${point.x.toFixed(1)} ${point.y.toFixed(1)}`
      })
      return `${cmds.join('')}Z`
    })
    .join('')
}

function spreadCloseHops(stops: Place[], pins: Pt[]): Pt[] {
  const out = pins.map((pin) => ({ ...pin }))
  for (let index = 0; index < stops.length - 1; index++) {
    if (km(stops[index]!, stops[index + 1]!) < 30) continue
    const a = out[index]!
    const b = out[index + 1]!
    let dx = b.x - a.x
    let dy = b.y - a.y
    let len = Math.hypot(dx, dy)
    const min = 46
    if (len >= min) continue
    if (len < 1) {
      dx = 1
      dy = -0.45
      len = Math.hypot(dx, dy)
    }
    const ux = dx / len
    const uy = dy / len
    const midX = (a.x + b.x) / 2
    const midY = (a.y + b.y) / 2
    a.x = midX - ux * (min / 2)
    a.y = midY - uy * (min / 2)
    b.x = midX + ux * (min / 2)
    b.y = midY + uy * (min / 2)
  }
  return out
}

function routePaths(stops: Place[], pins: Pt[]): string[] {
  const paths: string[] = []
  for (let index = 0; index < stops.length - 1; index++) {
    if (km(stops[index]!, stops[index + 1]!) < 30) continue
    const path = bowed(pins[index]!, pins[index + 1]!, index)
    if (path) paths.push(path)
  }
  return paths
}

function bowed(a: Pt, b: Pt, index: number): string | null {
  const dx = b.x - a.x
  const dy = b.y - a.y
  const len = Math.hypot(dx, dy)
  if (len < 10) return null
  const ux = dx / len
  const uy = dy / len
  const trim = Math.min(9, len * 0.16)
  const x1 = a.x + ux * trim
  const y1 = a.y + uy * trim
  const x2 = b.x - ux * (trim + 3)
  const y2 = b.y - uy * (trim + 3)
  const bow = Math.min(18, len * 0.18) * (index % 2 === 0 ? 1 : -1)
  const mx = (x1 + x2) / 2 + -uy * bow
  const my = (y1 + y2) / 2 + ux * bow
  return `M${x1.toFixed(1)} ${y1.toFixed(1)} Q${mx.toFixed(1)} ${my.toFixed(1)} ${x2.toFixed(1)} ${y2.toFixed(1)}`
}

function km(a: Place, b: Place): number {
  const cos = Math.cos((((a.lat + b.lat) / 2) * Math.PI) / 180)
  const dx = (a.lon - b.lon) * 111 * cos
  const dy = (a.lat - b.lat) * 111
  return Math.hypot(dx, dy)
}

const COUNTRY_ANCHORS: { id: NeighborId | 'cn'; text: string; lon: number; lat: number }[] = [
  { id: 'cn', text: 'КИТАЙ', lon: 114.6, lat: 32.4 },
  { id: 'kz', text: 'КАЗАХСТАН', lon: 75.4, lat: 47.6 },
  { id: 'ru', text: 'РОССИЯ', lon: 76.2, lat: 55.6 },
  { id: 'mn', text: 'МОНГОЛИЯ', lon: 103.2, lat: 47.2 },
]

function layoutLabels(
  stops: Place[],
  pins: { id: string; x: number; y: number }[],
  neighbors: NeighborId[],
  project: (lon: number, lat: number) => Pt,
  height: number,
): MapLabel[] {
  const boxes: Box[] = []
  const labels: MapLabel[] = []
  const pinPts = pins.map((pin) => ({ x: pin.x, y: pin.y }))

  stops.forEach((stop, index) => {
    const pin = pins[index]!
    const placed = chooseCityLabel(stop.label, pin, boxes, pinPts, height)
    labels.push(placed.label)
    boxes.push(placed.box)
  })

  const shown = new Set<NeighborId | 'cn'>(['cn', ...neighbors])
  for (const anchor of COUNTRY_ANCHORS) {
    if (!shown.has(anchor.id)) continue
    const point = project(anchor.lon, anchor.lat)
    if (point.x < 8 || point.x > MAP_WIDTH - 8 || point.y < 8 || point.y > height - 8) continue
    const box = boxFor(point.x, point.y, 'middle', anchor.text, 13)
    if (box.x < 2 || box.x + box.w > MAP_WIDTH - 2 || box.y < 2 || box.y + box.h > height - 2) continue
    if (boxes.some((other) => overlaps(box, other, 8))) continue
    if (pinPts.some((pin) => Math.hypot(pin.x - point.x, pin.y - point.y) < 36)) continue
    labels.push({ text: anchor.text, x: point.x, y: point.y, anchor: 'middle', kind: 'country', leader: null })
    boxes.push(box)
  }
  return labels
}

function hitsRoute(box: Box, pins: Pt[]): boolean {
  for (let index = 0; index < pins.length - 1; index++) {
    const a = pins[index]!
    const b = pins[index + 1]!
    const len = Math.hypot(b.x - a.x, b.y - a.y)
    const steps = Math.max(4, Math.ceil(len / 7))
    for (let step = 1; step < steps; step++) {
      const t = step / steps
      const x = a.x + (b.x - a.x) * t
      const y = a.y + (b.y - a.y) * t
      if (x >= box.x - 3 && x <= box.x + box.w + 3 && y >= box.y - 3 && y <= box.y + box.h + 3) return true
    }
  }
  return false
}

function chooseCityLabel(
  text: string,
  pin: Pt,
  boxes: Box[],
  pins: Pt[],
  height: number,
): { label: MapLabel; box: Box } {
  const options: { dx: number; dy: number; anchor: MapLabel['anchor'] }[] = [
    { dx: 10, dy: 5, anchor: 'start' },
    { dx: -10, dy: 5, anchor: 'end' },
    { dx: 0, dy: -12, anchor: 'middle' },
    { dx: 0, dy: 20, anchor: 'middle' },
    { dx: 14, dy: -14, anchor: 'start' },
    { dx: -14, dy: -14, anchor: 'end' },
    { dx: 14, dy: 18, anchor: 'start' },
    { dx: -14, dy: 18, anchor: 'end' },
    { dx: 22, dy: -26, anchor: 'start' },
    { dx: -22, dy: -26, anchor: 'end' },
  ]
  let best: { label: MapLabel; box: Box; score: number } | null = null
  for (const option of options) {
    const x = pin.x + option.dx
    const y = pin.y + option.dy
    const box = boxFor(x, y, option.anchor, text, FONT)
    let score = 0
    if (box.x < 3 || box.y < 3 || box.x + box.w > MAP_WIDTH - 3 || box.y + box.h > height - 3) score += 80
    if (box.y < 16) score += 28
    for (const other of boxes) if (overlaps(box, other, 4)) score += 24
    if (hitsRoute(box, pins)) score += 40
    for (const other of pins) {
      if (Math.hypot(other.x - pin.x, other.y - pin.y) < 0.5) continue
      if (circleHits(other, 7, box)) score += 18
    }
    if (circleHits(pin, 6, box)) score += 10
    score += Math.hypot(option.dx, option.dy) * 0.08
    if (option.anchor === 'start') score -= 0.4
    const leader = leaderLine(pin, box)
    const label: MapLabel = { text, x, y, anchor: option.anchor, kind: 'city', leader }
    if (!best || score < best.score) best = { label, box, score }
  }
  return { label: best!.label, box: best!.box }
}

function boxFor(x: number, y: number, anchor: MapLabel['anchor'], text: string, font: number): Box {
  const w = text.length * (font === FONT ? CHAR_W : 7.4)
  const h = font
  const left = anchor === 'start' ? x : anchor === 'end' ? x - w : x - w / 2
  return { x: left, y: y - font + 3, w, h }
}

function overlaps(a: Box, b: Box, pad: number): boolean {
  return a.x < b.x + b.w + pad && a.x + a.w + pad > b.x && a.y < b.y + b.h + pad && a.y + a.h + pad > b.y
}

function circleHits(center: Pt, radius: number, box: Box): boolean {
  const x = Math.max(box.x, Math.min(center.x, box.x + box.w))
  const y = Math.max(box.y, Math.min(center.y, box.y + box.h))
  return Math.hypot(center.x - x, center.y - y) < radius
}

function leaderLine(pin: Pt, box: Box): MapLabel['leader'] {
  const cx = Math.max(box.x, Math.min(pin.x, box.x + box.w))
  const cy = Math.max(box.y, Math.min(pin.y, box.y + box.h))
  if (Math.hypot(pin.x - cx, pin.y - cy) < 14) return null
  return { x1: pin.x, y1: pin.y, x2: cx, y2: cy }
}

function ariaLabel(stops: Place[]): string {
  const names = stops.map((stop) => stop.label).join(' → ')
  return `Карта: ${names}`
}

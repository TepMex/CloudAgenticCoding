import type { Block, Journal } from './types'

export const MAGIC = 'ZN26'
export const VERSION = 1
export const KDF_ITERATIONS = 100_000

const HEADER_BYTES = 37

export class UnlockError extends Error {
  constructor() {
    super('Неверный пароль')
    this.name = 'UnlockError'
  }
}

export type OpenedJournal = {
  journal: Journal
  images: Uint8Array
}

export function packJournal(journal: Journal, images: Uint8Array): Uint8Array {
  const json = new TextEncoder().encode(JSON.stringify(journal))
  const header = new Uint8Array(4)
  new DataView(header.buffer).setUint32(0, json.length)
  return concat(header, json, images)
}

export function unpackJournal(plain: Uint8Array): OpenedJournal {
  if (plain.length < 4) throw new Error('Journal payload is truncated')
  const view = dataView(plain)
  const jsonLength = view.getUint32(0)
  if (jsonLength > plain.length - 4) throw new Error('Journal JSON is truncated')
  const jsonBytes = plain.subarray(4, 4 + jsonLength)
  const images = plain.slice(4 + jsonLength)
  let parsed: unknown
  try {
    parsed = JSON.parse(new TextDecoder().decode(jsonBytes))
  } catch {
    throw new Error('Journal JSON is unreadable')
  }
  if (!isJournal(parsed)) throw new Error('Journal shape is unexpected')
  for (const day of parsed.days) {
    for (const block of day.blocks) {
      if (block.t === 'img' && (block.o < 0 || block.n < 0 || block.o + block.n > images.length)) {
        throw new Error('Photo is outside the journal payload')
      }
    }
  }
  return { journal: parsed, images }
}

export async function encryptPacked(
  plain: Uint8Array,
  password: string,
  iterations = KDF_ITERATIONS,
): Promise<Uint8Array> {
  const salt = crypto.getRandomValues(new Uint8Array(16))
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const key = await deriveKey(password, salt, iterations)
  const cipher = new Uint8Array(
    await crypto.subtle.encrypt({ name: 'AES-GCM', iv: copyBytes(iv) }, key, copyBytes(plain)),
  )
  const iter = new Uint8Array(4)
  new DataView(iter.buffer).setUint32(0, iterations)
  return concat(new TextEncoder().encode(MAGIC), new Uint8Array([VERSION]), iter, salt, iv, cipher)
}

export async function openSealed(file: Uint8Array, password: string): Promise<OpenedJournal> {
  if (file.length < HEADER_BYTES + 16) throw new Error('Sealed journal is truncated')
  const magic = new TextDecoder().decode(file.subarray(0, 4))
  if (magic !== MAGIC) throw new Error('Sealed journal has an unexpected header')
  if (file[4] !== VERSION) throw new Error('Sealed journal version is unsupported')
  const view = dataView(file)
  const iterations = view.getUint32(5)
  const salt = file.slice(9, 25)
  const iv = file.slice(25, 37)
  const cipher = file.slice(37)
  const key = await deriveKey(password, salt, iterations)
  let plain: ArrayBuffer
  try {
    plain = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: copyBytes(iv) }, key, copyBytes(cipher))
  } catch (error) {
    if (isAuthFailure(error)) throw new UnlockError()
    throw error
  }
  return unpackJournal(new Uint8Array(plain))
}

async function deriveKey(password: string, salt: Uint8Array, iterations: number): Promise<CryptoKey> {
  const material = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(password),
    'PBKDF2',
    false,
    ['deriveKey'],
  )
  return crypto.subtle.deriveKey(
    { name: 'PBKDF2', salt: copyBytes(salt), iterations, hash: 'SHA-256' },
    material,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  )
}

function isJournal(value: unknown): value is Journal {
  if (!value || typeof value !== 'object') return false
  const journal = value as Partial<Journal>
  if (typeof journal.title !== 'string' || typeof journal.kicker !== 'string' || typeof journal.range !== 'string') {
    return false
  }
  if (!Array.isArray(journal.days)) return false
  return journal.days.every((day) => {
    if (!day || typeof day !== 'object') return false
    const candidate = day as Partial<Journal['days'][number]>
    return (
      typeof candidate.id === 'string' &&
      typeof candidate.navDate === 'string' &&
      typeof candidate.navPlace === 'string' &&
      typeof candidate.heading === 'string' &&
      typeof candidate.place === 'string' &&
      Array.isArray(candidate.blocks) &&
      candidate.blocks.every(isBlock)
    )
  })
}

function isBlock(value: unknown): value is Block {
  if (!value || typeof value !== 'object') return false
  const block = value as Partial<Block>
  if (block.t === 'p') return typeof (block as { text?: unknown }).text === 'string'
  if (block.t === 'img') {
    const photo = block as Partial<Extract<Block, { t: 'img' }>>
    return typeof photo.o === 'number' && typeof photo.n === 'number'
  }
  return false
}

function isAuthFailure(error: unknown): boolean {
  return typeof error === 'object' && error !== null && 'name' in error && error.name === 'OperationError'
}

function dataView(bytes: Uint8Array): DataView {
  return new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
}

function copyBytes(bytes: Uint8Array): Uint8Array<ArrayBuffer> {
  return Uint8Array.from(bytes)
}

function concat(...parts: Uint8Array[]): Uint8Array {
  const length = parts.reduce((sum, part) => sum + part.length, 0)
  const out = new Uint8Array(length)
  let offset = 0
  for (const part of parts) {
    out.set(part, offset)
    offset += part.length
  }
  return out
}

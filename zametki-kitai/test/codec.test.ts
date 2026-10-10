import { expect, test } from 'bun:test'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { UnlockError, encryptPacked, openSealed, packJournal } from '../src/codec'
import { packNotes } from '../src/pack'

test('seals and opens a journal, and rejects the wrong passphrase', async () => {
  const packed = packNotes([
    {
      title: '06.09.2026 Астана',
      blocks: [
        { t: 'p', text: 'Утро' },
        { t: 'img', bytes: new Uint8Array([9, 8, 7]) },
      ],
    },
    {
      title: '05.09.2026 Исилькуль',
      blocks: [{ t: 'p', text: 'Слойка' }],
    },
  ])
  expect(packed.journal.days.map((day) => day.id)).toEqual(['2026-09-05', '2026-09-06'])
  expect(packed.journal.range).toBe('5–6 сентября 2026')
  const photo = packed.journal.days[1]?.blocks[1]
  expect(photo).toEqual({ t: 'img', o: 0, n: 3 })
  expect(Array.from(packed.images)).toEqual([9, 8, 7])

  const sealed = await encryptPacked(packJournal(packed.journal, packed.images), 'secret', 1_000)
  const opened = await openSealed(sealed, 'secret')
  expect(opened.journal.days[0]?.place).toBe('Исилькуль')
  expect(opened.images).toEqual(packed.images)
  await expect(openSealed(sealed, 'nope')).rejects.toBeInstanceOf(UnlockError)
})

test('the published blob is sealed rather than the raw export', () => {
  const bytes = new Uint8Array(readFileSync(join(import.meta.dir, '../public/journal.bin')))
  expect(new TextDecoder().decode(bytes.subarray(0, 4))).toBe('ZN26')
  expect(bytes[4]).toBe(1)
  expect(bytes.length).toBeGreaterThan(1_000_000)
  const latin = Buffer.from(bytes).toString('latin1')
  expect(latin.includes('<html')).toBe(false)
  expect(latin.includes('data:image')).toBe(false)
  expect(latin.includes('&nbsp;')).toBe(false)
})

test('rejects a truncated header', async () => {
  await expect(openSealed(new Uint8Array([1, 2, 3]), 'secret')).rejects.toThrow(/truncated/i)
})

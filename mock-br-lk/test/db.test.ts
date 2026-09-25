import 'fake-indexeddb/auto'
import { describe, expect, test } from 'bun:test'
import { getAccount, loadSession, register, resetProgress, saveAccount, signIn, signOut } from '../src/db'

describe('IndexedDB persistence', () => {
  test('register stores the account and opens a session', async () => {
    const r = await register('Mom@Mail.ru', 'Алексей')
    expect(r.ok).toBe(true)
    expect((await loadSession())?.studentName).toBe('Алексей')
    expect(await register('mom@mail.ru', 'Другой')).toEqual({ ok: false, reason: 'exists' })
  })

  test('sign-in accepts any e-mail and keeps progress across sessions', async () => {
    const first = await signIn('dad@mail.ru')
    expect(first.created).toBe(true)
    await saveAccount({ ...first.account, xp: 150, heroId: 'panda' })
    await signOut()
    expect(await loadSession()).toBeNull()

    const again = await signIn('DAD@mail.ru')
    expect(again.created).toBe(false)
    expect(again.account.xp).toBe(150)
  })

  test('reset keeps hero and onboarding but clears progress', async () => {
    const { account } = await signIn('reset@mail.ru')
    await saveAccount({ ...account, xp: 300, coins: 5, heroId: 'tiger', heroName: 'Тигр Ху', onboarding: 'done' })
    const fresh = await resetProgress((await getAccount('reset@mail.ru'))!)
    expect(fresh.xp).toBe(0)
    expect(fresh.coins).toBe(120)
    expect(fresh.heroName).toBe('Тигр Ху')
    expect((await getAccount('reset@mail.ru'))?.onboarding).toBe('done')
  })
})

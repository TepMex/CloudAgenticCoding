import { expect, test } from 'bun:test'
import { newAccount } from '../src/domain/account'
import { guard } from '../src/routes'

test('guests may only see register and login', () => {
  expect(guard('/register', null)).toBeNull()
  expect(guard('/login', null)).toBeNull()
  expect(guard('/app/map', null)).toBe('/register')
})

test('onboarding steps cannot be skipped forward', () => {
  const a = newAccount('p@x.ru', 'A')
  expect(guard('/welcome', a)).toBeNull()
  expect(guard('/hero', a)).toBe('/welcome')
  expect(guard('/app/map', { ...a, onboarding: 'hero' })).toBe('/hero')
  expect(guard('/welcome', { ...a, onboarding: 'named' })).toBeNull()
  expect(guard('/hero-created', { ...a, onboarding: 'named' })).toBeNull()
})

test('finished students go to the cabinet but may change their hero', () => {
  const a = { ...newAccount('p@x.ru', 'A'), onboarding: 'done' as const }
  expect(guard('/register', a)).toBe('/app/map')
  expect(guard('/welcome', a)).toBe('/app/map')
  expect(guard('/hero', a)).toBeNull()
  expect(guard('/app/tasks/beijing:meaning', a)).toBeNull()
})

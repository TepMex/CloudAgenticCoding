import type { Account } from './domain/account'

const ONBOARDING_ROUTE: Record<Account['onboarding'], string> = {
  welcome: '/welcome',
  hero: '/hero',
  named: '/hero-created',
  done: '/app/map',
}

export function routeForAccount(a: Account): string {
  return ONBOARDING_ROUTE[a.onboarding]
}

const PUBLIC = ['/register', '/login']
const ONBOARDING = ['/welcome', '/hero', '/hero-created']

/** Where to send the user if `path` is not allowed for them, or null if it is. */
export function guard(path: string, account: Account | null): string | null {
  if (!account) return PUBLIC.includes(path) ? null : '/register'
  if (account.onboarding !== 'done') {
    const expected = routeForAccount(account)
    const allowed = ONBOARDING.slice(0, ONBOARDING.indexOf(expected) + 1)
    return allowed.includes(path) ? null : expected
  }
  if (path.startsWith('/app/') || path === '/hero' || path === '/hero-created') return null
  return '/app/map'
}

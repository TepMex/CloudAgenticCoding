import { migrateAccount, nameFromEmail, newAccount, normalizeEmail, type Account } from './domain/account'

const DB_NAME = 'mock-br-lk'
const DB_VERSION = 1
const ACCOUNTS = 'accounts'
const META = 'meta'
const SESSION_KEY = 'session'

let dbPromise: Promise<IDBDatabase> | null = null

function openDb(): Promise<IDBDatabase> {
  if (!dbPromise) {
    dbPromise = new Promise((resolve, reject) => {
      const req = indexedDB.open(DB_NAME, DB_VERSION)
      req.onupgradeneeded = () => {
        const db = req.result
        if (!db.objectStoreNames.contains(ACCOUNTS)) db.createObjectStore(ACCOUNTS, { keyPath: 'email' })
        if (!db.objectStoreNames.contains(META)) db.createObjectStore(META)
      }
      req.onsuccess = () => resolve(req.result)
      req.onerror = () => reject(req.error)
    })
  }
  return dbPromise
}

async function run<T>(store: string, mode: IDBTransactionMode, op: (s: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(store, mode)
    const req = op(tx.objectStore(store))
    tx.oncomplete = () => resolve(req.result)
    tx.onerror = () => reject(tx.error)
    tx.onabort = () => reject(tx.error)
  })
}

export async function getAccount(email: string): Promise<Account | null> {
  const raw = await run<Account | undefined>(ACCOUNTS, 'readonly', (s) => s.get(normalizeEmail(email)))
  return raw ? migrateAccount(raw) : null
}

export async function saveAccount(account: Account): Promise<void> {
  await run(ACCOUNTS, 'readwrite', (s) => s.put(account))
}

export async function getSessionEmail(): Promise<string | null> {
  const v = await run<string | undefined>(META, 'readonly', (s) => s.get(SESSION_KEY))
  return v ?? null
}

export async function setSessionEmail(email: string | null): Promise<void> {
  if (email) await run(META, 'readwrite', (s) => s.put(normalizeEmail(email), SESSION_KEY))
  else await run(META, 'readwrite', (s) => s.delete(SESSION_KEY))
}

export async function loadSession(): Promise<Account | null> {
  const email = await getSessionEmail()
  return email ? getAccount(email) : null
}

export type RegisterResult = { ok: true; account: Account } | { ok: false; reason: 'exists' }

export async function register(email: string, studentName: string): Promise<RegisterResult> {
  if (await getAccount(email)) return { ok: false, reason: 'exists' }
  const account = newAccount(email, studentName)
  await saveAccount(account)
  await setSessionEmail(account.email)
  return { ok: true, account }
}

/** Any password is accepted; an unknown e-mail gets a fresh account so the mock never dead-ends. */
export async function signIn(email: string): Promise<{ account: Account; created: boolean }> {
  const existing = await getAccount(email)
  const account = existing ?? newAccount(email, nameFromEmail(email))
  if (!existing) await saveAccount(account)
  await setSessionEmail(account.email)
  return { account, created: !existing }
}

export async function signOut(): Promise<void> {
  await setSessionEmail(null)
}

export async function resetProgress(account: Account): Promise<Account> {
  const fresh: Account = {
    ...newAccount(account.email, account.studentName, account.createdAt),
    onboarding: account.onboarding,
    heroId: account.heroId,
    heroName: account.heroName,
  }
  await saveAccount(fresh)
  return fresh
}

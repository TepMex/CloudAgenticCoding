import type { HeroId } from './heroes'

/** Where the student is in the sign-up flow; `done` means the cabinet is open. */
export type Onboarding = 'welcome' | 'hero' | 'named' | 'done'

export interface Account {
  /** Parent e-mail, lower-cased; primary key in IndexedDB. */
  email: string
  studentName: string
  createdAt: number
  onboarding: Onboarding
  heroId: HeroId | null
  heroName: string
  xp: number
  coins: number
  gems: number
  completedTasks: string[]
  /** Best score per task id, 0..questions. */
  taskScores: Record<string, number>
  inventory: string[]
  equipped: string[]
  claimedAchievements: string[]
  readMail: string[]
  joinedEvents: string[]
  /** `YYYY-MM-DD` of the last opened daily chest. */
  lastDailyChest: string | null
  sound: boolean
}

export const START_COINS = 120

export function newAccount(email: string, studentName: string, now = Date.now()): Account {
  return {
    email: normalizeEmail(email),
    studentName: studentName.trim(),
    createdAt: now,
    onboarding: 'welcome',
    heroId: null,
    heroName: '',
    xp: 0,
    coins: START_COINS,
    gems: 0,
    completedTasks: [],
    taskScores: {},
    inventory: [],
    equipped: [],
    claimedAchievements: [],
    readMail: [],
    joinedEvents: [],
    lastDailyChest: null,
    sound: true,
  }
}

export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase()
}

/** Fills fields added after an account was first saved. */
export function migrateAccount(raw: Partial<Account> & { email: string }): Account {
  return { ...newAccount(raw.email, raw.studentName ?? ''), ...raw }
}

export function nameFromEmail(email: string): string {
  const local = normalizeEmail(email).split('@')[0] ?? ''
  const word = local.split(/[._\-+0-9]/).find(Boolean) ?? 'Ученик'
  return word.charAt(0).toUpperCase() + word.slice(1)
}

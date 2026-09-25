import type { Account } from './account'
import { CITIES, type City, type Word } from './cities'
import { getItem, MAX_EQUIPPED } from './shop'
import { getTask, PASS_SCORE, TASK_KINDS, tasksForCity, type TaskKind } from './tasks'

export const XP_PER_LEVEL = 100
export const TASK_XP = 25
export const TASK_COINS = 10
export const DAILY_CHEST_COINS = 20

export function levelOf(xp: number): number {
  return Math.floor(xp / XP_PER_LEVEL)
}

export function xpInLevel(xp: number): number {
  return xp % XP_PER_LEVEL
}

export function isCityUnlocked(city: City, level: number): boolean {
  return level >= city.level - 1
}

export function cityDone(city: City, account: Pick<Account, 'completedTasks'>): number {
  return tasksForCity(city).filter((t) => account.completedTasks.includes(t.id)).length
}

export function isCityComplete(city: City, account: Pick<Account, 'completedTasks'>): boolean {
  return cityDone(city, account) === tasksForCity(city).length
}

/** First unlocked city that still has tasks, else the last unlocked one. */
export function currentCity(account: Pick<Account, 'xp' | 'completedTasks'>): City {
  const level = levelOf(account.xp)
  const unlocked = CITIES.filter((c) => isCityUnlocked(c, level))
  return unlocked.find((c) => !isCityComplete(c, account)) ?? unlocked[unlocked.length - 1]
}

export function nextLockedCity(account: Pick<Account, 'xp'>): City | undefined {
  const level = levelOf(account.xp)
  return CITIES.find((c) => !isCityUnlocked(c, level))
}

export function heroPower(account: Pick<Account, 'xp' | 'equipped' | 'completedTasks'>): number {
  const items = account.equipped.reduce((sum, id) => sum + (getItem(id)?.power ?? 0), 0)
  return levelOf(account.xp) * 10 + account.completedTasks.length * 2 + items
}

export interface TaskResult {
  account: Account
  passed: boolean
  firstTime: boolean
  xpGained: number
  coinsGained: number
  leveledUp: boolean
}

export function completeTask(account: Account, taskId: string, score: number): TaskResult {
  const passed = score >= PASS_SCORE
  const firstTime = passed && !account.completedTasks.includes(taskId)
  const best = Math.max(account.taskScores[taskId] ?? 0, score)
  const xpGained = firstTime ? TASK_XP : 0
  const coinsGained = firstTime ? TASK_COINS : 0
  const next: Account = {
    ...account,
    taskScores: { ...account.taskScores, [taskId]: best },
    completedTasks: firstTime ? [...account.completedTasks, taskId] : account.completedTasks,
    xp: account.xp + xpGained,
    coins: account.coins + coinsGained,
  }
  return {
    account: next,
    passed,
    firstTime,
    xpGained,
    coinsGained,
    leveledUp: levelOf(next.xp) > levelOf(account.xp),
  }
}

/** Words from cities where the student has finished at least one task. */
export function collectedWords(account: Pick<Account, 'completedTasks'>): Word[] {
  const seen = new Set<string>()
  const words: Word[] = []
  for (const city of CITIES) {
    if (cityDone(city, account) === 0) continue
    for (const w of city.words) {
      if (seen.has(w.hanzi)) continue
      seen.add(w.hanzi)
      words.push(w)
    }
  }
  return words
}

export function skillLevels(account: Pick<Account, 'completedTasks' | 'taskScores'>): { kind: TaskKind; name: string; value: number }[] {
  const kinds = Object.keys(TASK_KINDS) as TaskKind[]
  return kinds.map((kind) => {
    const done = account.completedTasks.filter((id) => getTask(id)?.kind === kind).length
    return { kind, name: TASK_KINDS[kind].skill, value: Math.min(100, Math.round((done / CITIES.length) * 100)) }
  })
}

export function todayKey(now = new Date()): string {
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function canOpenDailyChest(account: Pick<Account, 'lastDailyChest'>, today = todayKey()): boolean {
  return account.lastDailyChest !== today
}

export function openDailyChest(account: Account, today = todayKey()): Account {
  if (!canOpenDailyChest(account, today)) return account
  return { ...account, coins: account.coins + DAILY_CHEST_COINS, lastDailyChest: today }
}

export type BuyError = 'owned' | 'funds' | 'unknown'

export function buyItem(account: Account, itemId: string): Account | BuyError {
  const item = getItem(itemId)
  if (!item) return 'unknown'
  if (account.inventory.includes(itemId)) return 'owned'
  if (account[item.currency] < item.price) return 'funds'
  return {
    ...account,
    [item.currency]: account[item.currency] - item.price,
    inventory: [...account.inventory, itemId],
  }
}

/** Equips or unequips; when slots are full the oldest equipped item is replaced. */
export function toggleEquip(account: Account, itemId: string): Account {
  if (!account.inventory.includes(itemId)) return account
  if (account.equipped.includes(itemId)) {
    return { ...account, equipped: account.equipped.filter((id) => id !== itemId) }
  }
  const equipped = [...account.equipped, itemId]
  return { ...account, equipped: equipped.slice(Math.max(0, equipped.length - MAX_EQUIPPED)) }
}

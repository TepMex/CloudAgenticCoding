import type { Account } from './account'
import { CITIES, getCity } from './cities'
import { collectedWords, isCityComplete, levelOf } from './progress'

export type AchievementIcon = 'footprints' | 'landmark' | 'book' | 'shirt' | 'map' | 'star' | 'flame' | 'crown'

export interface Achievement {
  id: string
  title: string
  description: string
  icon: AchievementIcon
  gems: number
  /** Returns [current, target]. */
  progress: (a: Account) => [number, number]
}

const beijing = getCity('beijing')!

export const ACHIEVEMENTS: Achievement[] = [
  { id: 'first-step', title: 'Первый шаг', description: 'Выполни первое задание', icon: 'footprints', gems: 1, progress: (a) => [Math.min(1, a.completedTasks.length), 1] },
  { id: 'beijing', title: 'Знаток Пекина', description: 'Пройди все задания в Пекине', icon: 'landmark', gems: 2, progress: (a) => [isCityComplete(beijing, a) ? 1 : 0, 1] },
  { id: 'words-20', title: 'Коллекционер слов', description: 'Собери 20 слов в коллекцию', icon: 'book', gems: 2, progress: (a) => [Math.min(20, collectedWords(a).length), 20] },
  { id: 'shopper', title: 'Модник', description: 'Купи предмет в сокровищнице', icon: 'shirt', gems: 1, progress: (a) => [Math.min(1, a.inventory.length), 1] },
  { id: 'traveller', title: 'Путешественник', description: 'Полностью пройди 3 города', icon: 'map', gems: 3, progress: (a) => [Math.min(3, CITIES.filter((c) => isCityComplete(c, a)).length), 3] },
  { id: 'level-5', title: 'Восходящая звезда', description: 'Достигни 5-го уровня', icon: 'star', gems: 3, progress: (a) => [Math.min(5, levelOf(a.xp)), 5] },
  { id: 'perfect', title: 'Без ошибок', description: 'Ответь верно на все вопросы в 5 заданиях', icon: 'flame', gems: 2, progress: (a) => [Math.min(5, Object.values(a.taskScores).filter((s) => s >= 4).length), 5] },
  { id: 'all-china', title: 'Покоритель Китая', description: 'Пройди все 13 городов', icon: 'crown', gems: 10, progress: (a) => [CITIES.filter((c) => isCityComplete(c, a)).length, CITIES.length] },
]

export function isAchieved(a: Account, ach: Achievement): boolean {
  const [cur, target] = ach.progress(a)
  return cur >= target
}

export function claimableCount(a: Account): number {
  return ACHIEVEMENTS.filter((ach) => isAchieved(a, ach) && !a.claimedAchievements.includes(ach.id)).length
}

export function claimAchievement(a: Account, id: string): Account {
  const ach = ACHIEVEMENTS.find((x) => x.id === id)
  if (!ach || !isAchieved(a, ach) || a.claimedAchievements.includes(id)) return a
  return { ...a, gems: a.gems + ach.gems, claimedAchievements: [...a.claimedAchievements, id] }
}

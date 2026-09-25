import { describe, expect, test } from 'bun:test'
import { newAccount, nameFromEmail } from '../src/domain/account'
import { claimAchievement, claimableCount } from '../src/domain/achievements'
import { CITIES, getCity } from '../src/domain/cities'
import {
  buyItem,
  canOpenDailyChest,
  collectedWords,
  completeTask,
  currentCity,
  heroPower,
  isCityUnlocked,
  levelOf,
  nextLockedCity,
  openDailyChest,
  toggleEquip,
} from '../src/domain/progress'
import { buildQuiz, getTask, QUESTIONS_PER_TASK, tasksForCity } from '../src/domain/tasks'

function seeded(seed = 1) {
  return () => {
    seed = (seed * 16807) % 2147483647
    return (seed - 1) / 2147483646
  }
}

describe('levels and cities', () => {
  test('a new student is level 0 with only Beijing open', () => {
    const a = newAccount('Parent@Mail.ru', 'Алексей')
    expect(a.email).toBe('parent@mail.ru')
    expect(levelOf(a.xp)).toBe(0)
    expect(CITIES.filter((c) => isCityUnlocked(c, 0)).map((c) => c.id)).toEqual(['beijing'])
    expect(currentCity(a).id).toBe('beijing')
    expect(nextLockedCity(a)?.id).toBe('xian')
  })

  test('finishing all four Beijing tasks reaches level 1 and opens Xi\'an', () => {
    let a = newAccount('p@x.ru', 'A')
    const results = tasksForCity(getCity('beijing')!).map((t) => {
      const r = completeTask(a, t.id, 4)
      a = r.account
      return r
    })
    expect(a.xp).toBe(100)
    expect(a.coins).toBe(120 + 40)
    expect(results.at(-1)!.leveledUp).toBe(true)
    expect(currentCity(a).id).toBe('xian')
  })

  test('a failed or repeated task gives no reward but keeps the best score', () => {
    const a = newAccount('p@x.ru', 'A')
    const failed = completeTask(a, 'beijing:meaning', 2)
    expect(failed.passed).toBe(false)
    expect(failed.account.xp).toBe(0)
    expect(failed.account.taskScores['beijing:meaning']).toBe(2)

    const passed = completeTask(failed.account, 'beijing:meaning', 3)
    const again = completeTask(passed.account, 'beijing:meaning', 4)
    expect(again.firstTime).toBe(false)
    expect(again.account.xp).toBe(25)
    expect(again.account.taskScores['beijing:meaning']).toBe(4)
  })
})

describe('quiz', () => {
  test('each question has four unique options including the answer', () => {
    for (const task of tasksForCity(getCity('xian')!)) {
      const quiz = buildQuiz(task, seeded(7))
      expect(quiz).toHaveLength(QUESTIONS_PER_TASK)
      for (const q of quiz) {
        expect(q.options).toContain(q.answer)
        expect(new Set(q.options).size).toBe(4)
      }
    }
  })

  test('listen tasks speak the hanzi', () => {
    const quiz = buildQuiz(getTask('beijing:listen')!, seeded(3))
    for (const q of quiz) expect(q.speak).toBe(q.answer)
  })
})

describe('shop, chest and achievements', () => {
  test('buying spends the right currency and refuses duplicates or poverty', () => {
    const a = newAccount('p@x.ru', 'A')
    const bought = buyItem(a, 'lantern')
    if (typeof bought === 'string') throw new Error(bought)
    expect(bought.coins).toBe(60)
    expect(buyItem(bought, 'lantern')).toBe('owned')
    expect(buyItem(bought, 'kite')).toBe('funds')
  })

  test('at most three items are equipped and they add power', () => {
    let a = { ...newAccount('p@x.ru', 'A'), inventory: ['compass', 'lantern', 'fan', 'hat'] }
    for (const id of a.inventory) a = toggleEquip(a, id)
    expect(a.equipped).toEqual(['lantern', 'fan', 'hat'])
    expect(heroPower(a)).toBe(8 + 10 + 12)
    a = toggleEquip(a, 'fan')
    expect(a.equipped).toEqual(['lantern', 'hat'])
  })

  test('the daily chest opens once per day', () => {
    const a = openDailyChest(newAccount('p@x.ru', 'A'), '2026-09-25')
    expect(a.coins).toBe(140)
    expect(canOpenDailyChest(a, '2026-09-25')).toBe(false)
    expect(openDailyChest(a, '2026-09-25').coins).toBe(140)
    expect(canOpenDailyChest(a, '2026-09-26')).toBe(true)
  })

  test('achievements are claimed once for gems', () => {
    const a = completeTask(newAccount('p@x.ru', 'A'), 'beijing:meaning', 4).account
    expect(claimableCount(a)).toBe(1)
    const claimed = claimAchievement(a, 'first-step')
    expect(claimed.gems).toBe(1)
    expect(claimAchievement(claimed, 'first-step').gems).toBe(1)
    expect(claimableCount(claimed)).toBe(0)
  })

  test('collection holds words of started cities', () => {
    const a = completeTask(newAccount('p@x.ru', 'A'), 'beijing:meaning', 4).account
    expect(collectedWords(a).map((w) => w.hanzi)).toContain('你好')
    expect(collectedWords(a)).toHaveLength(6)
  })
})

test('name from e-mail', () => {
  expect(nameFromEmail('ivan.petrov@mail.ru')).toBe('Ivan')
  expect(nameFromEmail('123@x.ru')).toBe('Ученик')
})

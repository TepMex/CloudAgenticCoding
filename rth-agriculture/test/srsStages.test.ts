import { describe, expect, test } from 'bun:test'
import { getSrsStage } from '../src/stats/srsStages'
import type { CardState } from '../src/learning'

function card(interval: number): CardState {
  return { scheduled_days: interval, due: new Date('2100-01-01T00:00:00Z') } as CardState
}

describe('display SRS stages', () => {
  test('classifies every documented interval boundary without changing FSRS data', () => {
    expect(getSrsStage().id).toBe('new')
    expect(getSrsStage(card(0)).id).toBe('step-1')
    expect(getSrsStage(card(1)).id).toBe('step-2')
    expect(getSrsStage(card(2)).id).toBe('step-3')
    expect(getSrsStage(card(4)).id).toBe('novice')
    expect(getSrsStage(card(7)).id).toBe('apprentice')
    expect(getSrsStage(card(14)).id).toBe('guru')
    expect(getSrsStage(card(30)).id).toBe('master')
    expect(getSrsStage(card(90)).id).toBe('enlightened')
    expect(getSrsStage(card(180)).id).toBe('rooted')
  })
})

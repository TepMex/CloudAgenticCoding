import { describe, expect, test } from 'bun:test'
import { streakHighlightColor, streakHighlightOpacity, streakIntensity } from '../src/streak'

describe('perfect-character streaks', () => {
  test('uses the requested intensity formula within the 1–10 range', () => {
    expect(streakIntensity(4, 1)).toBe(7)
    expect(streakIntensity(4, 4)).toBe(10)
    expect(streakIntensity(1, 1)).toBe(10)
    expect(streakIntensity(12, 1)).toBe(1)
    expect(streakIntensity(12, 12)).toBe(10)
  })

  test('makes the maximum streak a deeper jade than the minimum', () => {
    expect(streakHighlightColor(1)).toBe('rgb(157, 179, 165)')
    expect(streakHighlightColor(10)).toBe('rgb(13, 70, 57)')
  })

  test('makes early streaks a subtle cue and the last stroke solid jade', () => {
    expect(streakHighlightOpacity(1)).toBe(.18)
    expect(streakHighlightOpacity(5)).toBe(.62)
    expect(streakHighlightOpacity(10)).toBe(1)
  })
})

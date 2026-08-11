const MIN_STREAK_INTENSITY = 1
const MAX_STREAK_INTENSITY = 10
const STREAK_OPACITIES = [.18, .27, .38, .5, .62, .73, .82, .9, .96, 1]

/**
 * Converts the one-based stroke position into the visible streak intensity.
 * Long characters would otherwise produce values below the promised 1–10 range.
 */
export function streakIntensity(strokeCount: number, strokePosition: number): number {
  return Math.min(
    MAX_STREAK_INTENSITY,
    Math.max(MIN_STREAK_INTENSITY, MAX_STREAK_INTENSITY - strokeCount + strokePosition),
  )
}

/** A jade reward colour that becomes darker and more saturated with the streak. */
export function streakHighlightColor(intensity: number): string {
  const progress = (Math.min(MAX_STREAK_INTENSITY, Math.max(MIN_STREAK_INTENSITY, intensity)) - MIN_STREAK_INTENSITY)
    / (MAX_STREAK_INTENSITY - MIN_STREAK_INTENSITY)
  const from = { r: 157, g: 179, b: 165 }
  const to = { r: 13, g: 70, b: 57 }
  const mix = (start: number, end: number) => Math.round(start + (end - start) * progress)

  return `rgb(${mix(from.r, to.r)}, ${mix(from.g, to.g)}, ${mix(from.b, to.b)})`
}

/** Deliberately stepped: each flawless stroke feels more substantial than the last. */
export function streakHighlightOpacity(intensity: number): number {
  const bounded = Math.min(MAX_STREAK_INTENSITY, Math.max(MIN_STREAK_INTENSITY, intensity))
  return STREAK_OPACITIES[bounded - 1]!
}

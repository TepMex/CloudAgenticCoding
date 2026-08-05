import type { Classifier, Noun } from '../types';
import type { GameCatalog } from '../data/catalog';

export const UNIVERSAL_CLASSIFIER_ID = 'CL001';

export function damageFor(catalog: GameCatalog, nounId: string, classifierId: string): number {
  if (classifierId === UNIVERSAL_CLASSIFIER_ID) return 1;
  return catalog.pair(nounId, classifierId)?.game_damage ?? 0;
}

export function maxHpFor(noun: Pick<Noun, 'difficulty'>): number {
  return 4 + noun.difficulty * 2;
}

export function unlockedTier(wave: number, highestWave: number): 1 | 2 | 3 {
  const effectiveWave = Math.max(wave, highestWave);
  if (effectiveWave >= 6) return 3;
  if (effectiveWave >= 3) return 2;
  return 1;
}

export interface WaveRules {
  enemyCount: number;
  spawnDelayMs: number;
  baseSpeed: number;
  tier: 1 | 2 | 3;
}

/** Horizontal clearance between enemy sprites (same lane or vertically overlapping). */
export const ENEMY_MIN_SPACING = 190;

/**
 * Vertical half-extent of an enemy (HP bar through label). Lane centers must be
 * at least this far apart so adjacent lines do not visually intersect.
 */
export const ENEMY_VERTICAL_FOOTPRINT = 152;

export function rulesForWave(wave: number, highestWave = 0): WaveRules {
  const safeWave = Math.max(1, Math.floor(wave));
  return {
    enemyCount: Math.min(18, 3 + safeWave * 2),
    spawnDelayMs: Math.max(700, 1900 - safeWave * 100),
    baseSpeed: Math.min(190, 18 + safeWave * 2),
    tier: unlockedTier(safeWave, highestWave),
  };
}

/** All enemies in a wave share one speed — no per-noun or random variance. */
export function waveEnemySpeed(rules: Pick<WaveRules, 'baseSpeed'>): number {
  return rules.baseSpeed;
}

export interface LaneOccupant {
  x: number;
  y: number;
}

/** True when two lane centers are close enough that sprites would overlap vertically. */
export function lanesVerticallyOverlap(
  laneY: number,
  otherY: number,
  footprint: number = ENEMY_VERTICAL_FOOTPRINT,
): boolean {
  return Math.abs(laneY - otherY) < footprint;
}

/**
 * Spawn X so the new enemy does not overlap others on the same or vertically
 * conflicting lanes. Equal wave speed keeps that gap for the rest of the march.
 */
export function spawnXForLane(
  defaultX: number,
  laneY: number,
  existing: readonly LaneOccupant[],
  minSpacing: number = ENEMY_MIN_SPACING,
  verticalFootprint: number = ENEMY_VERTICAL_FOOTPRINT,
): number {
  let x = defaultX;
  for (const occupant of existing) {
    if (lanesVerticallyOverlap(laneY, occupant.y, verticalFootprint)) {
      x = Math.max(x, occupant.x + minSpacing);
    }
  }
  return x;
}

/**
 * Prefer the lane that lets the enemy spawn furthest left (least queue depth).
 * Ties are broken with `random` so lane choice stays unpredictable.
 */
export function pickLaneIndex(
  laneYs: readonly number[],
  existing: readonly LaneOccupant[],
  defaultSpawnX: number,
  random: () => number = Math.random,
  minSpacing: number = ENEMY_MIN_SPACING,
  verticalFootprint: number = ENEMY_VERTICAL_FOOTPRINT,
): number {
  if (laneYs.length === 0) return 0;
  const spawnXs = laneYs.map((laneY) =>
    spawnXForLane(defaultSpawnX, laneY, existing, minSpacing, verticalFootprint),
  );
  const bestX = Math.min(...spawnXs);
  const candidates = spawnXs
    .map((x, index) => ({ x, index }))
    .filter((item) => item.x === bestX);
  const pick = Math.floor(random() * candidates.length);
  return candidates[Math.min(pick, candidates.length - 1)]!.index;
}

export function scoreForHit(damage: number, combo: number): number {
  if (damage <= 0) return 0;
  return damage * 10 + Math.min(combo, 20) * 2;
}

export function scoreForKill(noun: Pick<Noun, 'difficulty'>, combo: number): number {
  return 60 + noun.difficulty * 40 + Math.min(combo, 20) * 5;
}

export function nextCombo(current: number, damage: number): number {
  return damage > 0 ? current + 1 : 0;
}

export function hintChargesAtWaveStart(unspentCharges: number): number {
  return Math.max(0, Math.floor(unspentCharges)) + 1;
}

export function hintChargesAfterUse(charges: number): number {
  return Math.max(0, Math.floor(charges) - 1);
}

export function choicesForTarget(
  catalog: GameCatalog,
  nounId: string,
  tier: 1 | 2 | 3,
  random: () => number = Math.random,
  limit = 6,
): Classifier[] {
  const unlocked = catalog.seed.classifiers.filter((item) => item.unlock_level <= tier);
  const effective = unlocked
    .filter((item) => item.classifier_id !== UNIVERSAL_CLASSIFIER_ID && catalog.pair(nounId, item.classifier_id))
    .sort((a, b) => damageFor(catalog, nounId, b.classifier_id) - damageFor(catalog, nounId, a.classifier_id));
  const chosen = new Map<string, Classifier>();
  const universal = catalog.classifierById.get(UNIVERSAL_CLASSIFIER_ID);
  if (universal) chosen.set(universal.classifier_id, universal);
  effective.slice(0, 2).forEach((item) => chosen.set(item.classifier_id, item));

  const distractors = unlocked.filter((item) => !chosen.has(item.classifier_id));
  while (chosen.size < limit && distractors.length) {
    const index = Math.floor(random() * distractors.length);
    const [item] = distractors.splice(index, 1);
    chosen.set(item.classifier_id, item);
  }
  return [...chosen.values()].sort((a, b) => a.classifier_id === UNIVERSAL_CLASSIFIER_ID ? -1 : b.classifier_id === UNIVERSAL_CLASSIFIER_ID ? 1 : random() - 0.5);
}

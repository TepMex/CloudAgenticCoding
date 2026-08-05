import { describe, expect, it } from 'vitest';
import seed from '../data/chinese_classifier_game_seed.json';
import { GameCatalog } from '../src/data/catalog';
import {
  choicesForTarget,
  damageFor,
  ENEMY_MIN_SPACING,
  ENEMY_VERTICAL_FOOTPRINT,
  hintChargesAfterUse,
  hintChargesAtWaveStart,
  lanesVerticallyOverlap,
  maxHpFor,
  nextCombo,
  pickLaneIndex,
  rulesForWave,
  scoreForHit,
  spawnXForLane,
  waveEnemySpeed,
} from '../src/game/rules';
import type { GameSeed } from '../src/types';

const catalog = new GameCatalog(seed as GameSeed);

describe('damageFor', () => {
  it('всегда возвращает ровно 1 для 个', () => {
    expect(damageFor(catalog, 'N001', 'CL001')).toBe(1);
    expect(damageFor(catalog, 'N154', 'CL001')).toBe(1);
    expect(damageFor(catalog, 'несуществующий', 'CL001')).toBe(1);
  });

  it('берёт game_damage для существующей пары', () => {
    const pair = seed.pairs.find((item) => item.classifier_id !== 'CL001')!;
    expect(damageFor(catalog, pair.noun_id, pair.classifier_id)).toBe(pair.game_damage);
  });

  it('возвращает 0 для отсутствующей пары', () => {
    expect(damageFor(catalog, 'N001', 'CL045')).toBe(0);
  });
});

describe('progression rules', () => {
  it('повышает HP с трудностью', () => {
    expect(maxHpFor({ difficulty: 1 })).toBe(6);
    expect(maxHpFor({ difficulty: 3 })).toBe(10);
  });

  it('ускоряет и расширяет волны в безопасных пределах', () => {
    expect(rulesForWave(1).baseSpeed).toBe(20);
    expect(rulesForWave(2).baseSpeed - rulesForWave(1).baseSpeed).toBe(2);
    expect(rulesForWave(2).enemyCount).toBeGreaterThan(rulesForWave(1).enemyCount);
    expect(rulesForWave(8).spawnDelayMs).toBeLessThan(rulesForWave(1).spawnDelayMs);
    expect(rulesForWave(100).spawnDelayMs).toBe(700);
  });

  it('даёт одинаковую скорость всем врагам одной волны', () => {
    const rules = rulesForWave(5);
    expect(waveEnemySpeed(rules)).toBe(rules.baseSpeed);
    expect(waveEnemySpeed(rules)).toBe(waveEnemySpeed(rulesForWave(5)));
  });

  it('сбрасывает серию только на нулевом уроне', () => {
    expect(nextCombo(4, 0)).toBe(0);
    expect(nextCombo(4, 1)).toBe(5);
    expect(scoreForHit(5, 4)).toBeGreaterThan(scoreForHit(1, 4));
  });

  it('всегда предлагает 个 и хотя бы одну подходящую меру', () => {
    const choices = choicesForTarget(catalog, 'N001', 2, () => 0.25);
    expect(choices.some((item) => item.classifier_id === 'CL001')).toBe(true);
    expect(choices.some((item) => damageFor(catalog, 'N001', item.classifier_id) > 1)).toBe(true);
  });

  it('добавляет одну подсказку в начале волны и сохраняет неиспользованные', () => {
    expect(hintChargesAtWaveStart(0)).toBe(1);
    expect(hintChargesAtWaveStart(1)).toBe(2);
    expect(hintChargesAtWaveStart(hintChargesAfterUse(2))).toBe(2);
    expect(hintChargesAfterUse(0)).toBe(0);
  });
});

describe('enemy lane spacing', () => {
  const lanes = [200, 360, 520];
  const defaultX = 1390;

  it('игровые полосы разведены шире вертикального силуэта врага', () => {
    expect(lanes[1]! - lanes[0]!).toBeGreaterThanOrEqual(ENEMY_VERTICAL_FOOTPRINT);
    expect(lanes[2]! - lanes[1]!).toBeGreaterThanOrEqual(ENEMY_VERTICAL_FOOTPRINT);
    expect(lanesVerticallyOverlap(lanes[0]!, lanes[1]!)).toBe(false);
  });

  it('не ставит врага поверх уже занятого места на полосе', () => {
    const existing = [{ x: defaultX, y: lanes[0]! }];
    expect(spawnXForLane(defaultX, lanes[0]!, existing)).toBe(defaultX + ENEMY_MIN_SPACING);
    expect(spawnXForLane(defaultX, lanes[1]!, existing)).toBe(defaultX);
  });

  it('сдвигает X, если соседняя полоса слишком близко по вертикали', () => {
    const crowded = [220, 300, 500];
    const existing = [{ x: defaultX, y: crowded[0]! }];
    expect(lanesVerticallyOverlap(crowded[0]!, crowded[1]!)).toBe(true);
    expect(spawnXForLane(defaultX, crowded[1]!, existing)).toBe(defaultX + ENEMY_MIN_SPACING);
  });

  it('выбирает свободную полосу вместо удлинения очереди', () => {
    const existing = [{ x: defaultX, y: lanes[0]! }];
    expect(pickLaneIndex(lanes, existing, defaultX, () => 0)).toBe(1);
  });

  it('сохраняет зазор при очереди на всех полосах', () => {
    const existing = lanes.map((y) => ({ x: defaultX, y }));
    const lane = pickLaneIndex(lanes, existing, defaultX, () => 0.99);
    const x = spawnXForLane(defaultX, lanes[lane]!, existing);
    expect(x).toBe(defaultX + ENEMY_MIN_SPACING);
    for (const occupant of existing) {
      if (lanesVerticallyOverlap(lanes[lane]!, occupant.y)) {
        expect(x - occupant.x).toBeGreaterThanOrEqual(ENEMY_MIN_SPACING);
      }
    }
  });
});

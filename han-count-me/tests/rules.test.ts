import { describe, expect, it } from 'vitest';
import seed from '../data/chinese_classifier_game_seed.json';
import { GameCatalog } from '../src/data/catalog';
import { choicesForTarget, damageFor, hintChargesAfterUse, hintChargesAtWaveStart, maxHpFor, nextCombo, rulesForWave, scoreForHit } from '../src/game/rules';
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

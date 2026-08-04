import { describe, expect, it } from 'vitest';
import seed from '../data/chinese_classifier_game_seed.json';
import { frameRectangle, buildSpriteFrameMappings } from '../src/data/spriteMapping';
import { SeedValidationError, validateSeed } from '../src/data/validation';
import type { GameSeed } from '../src/types';

describe('seed validation', () => {
  it('принимает исходную базу', () => expect(() => validateSeed(seed)).not.toThrow());

  it('отклоняет неверный урон 个', () => {
    const broken = structuredClone(seed);
    broken.pairs.find((pair) => pair.classifier_id === 'CL001')!.game_damage = 2;
    expect(() => validateSeed(broken)).toThrow(SeedValidationError);
  });
});

describe('sprite mapping', () => {
  it('сопоставляет все 154 ID последовательно', () => {
    const mappings = buildSpriteFrameMappings((seed as GameSeed).nouns);
    expect(mappings).toHaveLength(154);
    expect(mappings[0]).toMatchObject({ nounId: 'N001', sheetIndex: 0, row: 0, column: 0 });
    expect(mappings[15]).toMatchObject({ nounId: 'N016', sheetIndex: 0, row: 3, column: 3 });
    expect(mappings[153]).toMatchObject({ nounId: 'N154', sheetIndex: 9, cellIndex: 9, row: 2, column: 1 });
  });

  it('полностью покрывает неделимый на 4 размер 1254', () => {
    const cells = Array.from({ length: 4 }, (_, column) => frameRectangle(1254, 1254, 0, column));
    expect(cells[0].x).toBe(0);
    expect(cells.at(-1)!.x + cells.at(-1)!.width).toBe(1254);
    expect(cells.reduce((sum, cell) => sum + cell.width, 0)).toBe(1254);
  });
});

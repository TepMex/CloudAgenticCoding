import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { validateSeed } from '../src/data/validation.ts';
import { buildSpriteFrameMappings } from '../src/data/spriteMapping.ts';
import type { GameSeed } from '../src/types.ts';

const dataUrl = new URL('../data/chinese_classifier_game_seed.json', import.meta.url);
const seed = JSON.parse(await readFile(fileURLToPath(dataUrl), 'utf8')) as unknown;
validateSeed(seed);
const mappings = buildSpriteFrameMappings((seed as GameSeed).nouns);
if (mappings.length !== 154 || mappings.at(-1)?.nounId !== 'N154' || mappings.at(-1)?.sheetIndex !== 9 || mappings.at(-1)?.cellIndex !== 9) {
  throw new Error('Сопоставление спрайтов N001–N154 некорректно');
}
console.log(`✓ JSON валиден: ${(seed as GameSeed).nouns.length} существительных, ${(seed as GameSeed).classifiers.length} классификатор, ${(seed as GameSeed).pairs.length} пар`);
console.log('✓ Сетки: N001–N144 используют листы 1–9, N145–N154 — первые 10 ячеек листа 10');

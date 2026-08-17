export interface DifficultySettings {
  level: number;
  fallDurationMs: number;
  spawnIntervalMs: number;
  maxConcurrentTargets: number;
}

export type RandomSource = () => number;

const HITS_PER_STAGE = 10;

export class DifficultyManager {
  settingsForHits(hits: number): DifficultySettings {
    const level = Math.min(6, Math.floor(hits / HITS_PER_STAGE) + 1);
    return {
      level,
      fallDurationMs: [0, 12_000, 11_000, 10_000, 9_000, 8_250, 7_500][level],
      spawnIntervalMs: [0, 8_000, 7_000, 6_200, 5_400, 4_800, 4_200][level],
      maxConcurrentTargets: level < 3 ? 1 : level < 5 ? 2 : 3,
    };
  }

  generateAmount(level: number, random: RandomSource): number {
    const integer = (min: number, max: number) => min + Math.floor(random() * (max - min + 1));
    switch (level) {
      case 1: return integer(1, 10);
      case 2: return integer(11, 99);
      case 3: {
        const values = [100, 200, 300, 400, 500, 600, 700, 800, 900, 120, 150, 250, 350, 560, 780];
        return values[integer(0, values.length - 1)];
      }
      case 4: return integer(100, 999);
      case 5: {
        const thousands = integer(1, 9) * 1000;
        return random() < 0.65 ? thousands : thousands + integer(1, 9) * 100;
      }
      default: return integer(1000, 9999);
    }
  }
}

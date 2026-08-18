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
      fallDurationMs: [0, 24_000, 22_000, 20_000, 18_000, 16_500, 15_000][level],
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

  generateUnusedAmount(level: number, random: RandomSource, used: ReadonlySet<number>): number | null {
    for (let tries = 0; tries < 100; tries += 1) {
      const amount = this.generateAmount(level, random);
      if (!used.has(amount)) return amount;
    }

    const available = this.amountsForLevel(level).filter((amount) => !used.has(amount));
    if (available.length === 0) return null;
    return available[Math.min(available.length - 1, Math.floor(random() * available.length))];
  }

  private amountsForLevel(level: number): number[] {
    if (level === 1) return Array.from({ length: 10 }, (_, index) => index + 1);
    if (level === 2) return Array.from({ length: 89 }, (_, index) => index + 11);
    if (level === 3) return [100, 200, 300, 400, 500, 600, 700, 800, 900, 120, 150, 250, 350, 560, 780];
    if (level === 4) return Array.from({ length: 900 }, (_, index) => index + 100);
    if (level === 5) {
      return Array.from({ length: 9 }, (_, index) => (index + 1) * 1000).flatMap(
        (thousands) => [thousands, ...Array.from({ length: 9 }, (_, index) => thousands + (index + 1) * 100)],
      );
    }
    return Array.from({ length: 9000 }, (_, index) => index + 1000);
  }
}

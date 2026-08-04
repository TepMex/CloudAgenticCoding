export interface PlayerProgress {
  bestScore: number;
  highestWave: number;
  tutorialSeen: boolean;
  discoveredNouns: string[];
  soundEnabled: boolean;
  hintsEnabled: boolean;
}

const STORAGE_KEY = 'han-count-me:progress:v1';

const defaults: PlayerProgress = {
  bestScore: 0,
  highestWave: 0,
  tutorialSeen: false,
  discoveredNouns: [],
  soundEnabled: true,
  hintsEnabled: true,
};

export function loadProgress(storage: Pick<Storage, 'getItem'> = localStorage): PlayerProgress {
  try {
    const parsed = JSON.parse(storage.getItem(STORAGE_KEY) ?? '{}') as Partial<PlayerProgress>;
    return {
      ...defaults,
      ...parsed,
      discoveredNouns: Array.isArray(parsed.discoveredNouns) ? [...new Set(parsed.discoveredNouns.filter((id): id is string => typeof id === 'string'))] : [],
    };
  } catch {
    return { ...defaults };
  }
}

export function saveProgress(progress: PlayerProgress, storage: Pick<Storage, 'setItem'> = localStorage): void {
  storage.setItem(STORAGE_KEY, JSON.stringify(progress));
}

export function recordRun(progress: PlayerProgress, score: number, wave: number, discovered: Iterable<string>): PlayerProgress {
  const next = {
    ...progress,
    bestScore: Math.max(progress.bestScore, score),
    highestWave: Math.max(progress.highestWave, wave),
    discoveredNouns: [...new Set([...progress.discoveredNouns, ...discovered])],
  };
  saveProgress(next);
  return next;
}

import seedUrl from '../../data/chinese_classifier_game_seed.json?url';

const sourceSheets = import.meta.glob('../../sprites/*.png', {
  eager: true,
  query: '?url',
  import: 'default',
}) as Record<string, string>;

const numberInName = (path: string): number => Number(path.match(/\((\d+)\)\.png$/)?.[1] ?? 0);

export const SEED_URL = seedUrl;
export const SPRITE_SHEET_URLS = Object.entries(sourceSheets)
  .sort(([pathA], [pathB]) => numberInName(pathA) - numberInName(pathB))
  .map(([, url]) => url);

if (SPRITE_SHEET_URLS.length !== 10) {
  throw new Error(`Ожидалось 10 сеток спрайтов, найдено ${SPRITE_SHEET_URLS.length}`);
}

export const sheetTextureKey = (index: number): string => `noun-sheet-${index + 1}`;

import seed from '../data/chinese_classifier_game_seed.json';
import { SPRITE_SHEET_URLS } from './game/assets';
import type { Classifier, Noun, NounClassifierPair } from './types';
import './presentation.css';

interface CatalogEntry {
  noun: Noun;
  suitable: NounClassifierPair[];
  best: NounClassifierPair[];
  searchText: string;
}

const classifiers = new Map<string, Classifier>(
  seed.classifiers.map((classifier) => [classifier.classifier_id, classifier as Classifier]),
);

const pairsByNoun = new Map<string, NounClassifierPair[]>();
for (const rawPair of seed.pairs) {
  const pair = rawPair as NounClassifierPair;
  const nounPairs = pairsByNoun.get(pair.noun_id) ?? [];
  nounPairs.push(pair);
  pairsByNoun.set(pair.noun_id, nounPairs);
}

const entries: CatalogEntry[] = seed.nouns.map((rawNoun) => {
  const noun = rawNoun as Noun;
  const pairs = pairsByNoun.get(noun.noun_id) ?? [];
  const visiblePairs = pairs.filter((pair) => pair.game_damage >= 2);
  const searchText = [
    noun.noun_ru,
    noun.noun_zh,
    noun.noun_pinyin,
    noun.category,
    ...visiblePairs.flatMap((pair) => {
      const classifier = classifiers.get(pair.classifier_id);
      return [pair.classifier_hanzi, classifier?.pinyin ?? '', pair.example, pair.relation_type];
    }),
  ]
    .join(' ')
    .toLocaleLowerCase('ru');

  return {
    noun,
    suitable: pairs.filter((pair) => pair.game_damage >= 2 && pair.game_damage <= 4),
    best: pairs.filter((pair) => pair.game_damage === 5),
    searchText,
  };
});

const body = requiredElement<HTMLTableSectionElement>('catalog-body');
const searchInput = requiredElement<HTMLInputElement>('search');
const categorySelect = requiredElement<HTMLSelectElement>('category');
const resultCount = requiredElement<HTMLOutputElement>('result-count');
const emptyState = requiredElement<HTMLDivElement>('empty-state');
const toTop = requiredElement<HTMLButtonElement>('to-top');

const categoryNames = [...new Set(entries.map((entry) => entry.noun.category))].sort((a, b) =>
  a.localeCompare(b, 'ru'),
);
for (const category of categoryNames) {
  const option = document.createElement('option');
  option.value = category;
  option.textContent = category;
  categorySelect.append(option);
}

function requiredElement<T extends HTMLElement>(id: string): T {
  const element = document.getElementById(id);
  if (!element) throw new Error(`Не найден элемент #${id}`);
  return element as T;
}

function normalizedQuery(value: string): string {
  const pinyinWithoutTones: Record<string, string> = {
    ā: 'a', á: 'a', ǎ: 'a', à: 'a',
    ē: 'e', é: 'e', ě: 'e', è: 'e',
    ī: 'i', í: 'i', ǐ: 'i', ì: 'i',
    ō: 'o', ó: 'o', ǒ: 'o', ò: 'o',
    ū: 'u', ú: 'u', ǔ: 'u', ù: 'u',
    ǖ: 'u', ǘ: 'u', ǚ: 'u', ǜ: 'u', ü: 'u',
  };
  return [...value.trim().toLocaleLowerCase('ru').replaceAll('ё', 'е')]
    .map((character) => pinyinWithoutTones[character] ?? character)
    .join('');
}

function nounArtwork(noun: Noun): HTMLElement {
  const nounIndex = Number.parseInt(noun.noun_id.slice(1), 10) - 1;
  const sheetIndex = Math.floor(nounIndex / 16);
  const cellIndex = nounIndex % 16;
  const row = Math.floor(cellIndex / 4);
  const column = cellIndex % 4;

  const frame = document.createElement('div');
  frame.className = 'noun-art';
  frame.setAttribute('role', 'img');
  frame.setAttribute('aria-label', `Иллюстрация: ${noun.noun_ru}`);
  frame.style.backgroundImage = `url("${SPRITE_SHEET_URLS[sheetIndex]}")`;
  frame.style.backgroundPosition = `${(column / 3) * 100}% ${(row / 3) * 100}%`;
  return frame;
}

function nounCell(noun: Noun): HTMLTableCellElement {
  const cell = document.createElement('td');
  cell.dataset.label = 'Предмет';

  const figure = document.createElement('figure');
  figure.className = 'noun-card';
  figure.append(nounArtwork(noun));

  const caption = document.createElement('figcaption');
  const chinese = document.createElement('strong');
  chinese.lang = 'zh-Hans';
  chinese.textContent = noun.noun_zh;
  const russian = document.createElement('span');
  russian.textContent = noun.noun_ru;
  const pinyin = document.createElement('small');
  pinyin.textContent = noun.noun_pinyin;
  caption.append(chinese, russian, pinyin);
  figure.append(caption);
  cell.append(figure);
  return cell;
}

function classifierCard(pair: NounClassifierPair, kind: 'suitable' | 'best' | 'fallback'): HTMLElement {
  const classifier = classifiers.get(pair.classifier_id);
  const card = document.createElement('article');
  card.className = `classifier-card ${kind}`;

  const glyph = document.createElement('span');
  glyph.className = 'classifier-glyph';
  glyph.lang = 'zh-Hans';
  glyph.textContent = pair.classifier_hanzi;

  const details = document.createElement('span');
  details.className = 'classifier-details';
  const pinyin = document.createElement('b');
  pinyin.textContent = classifier?.pinyin ?? '';
  const relation = document.createElement('small');
  relation.textContent = kind === 'fallback' ? 'универсальный вариант' : pair.relation_type;
  const example = document.createElement('span');
  example.className = 'example';
  example.lang = 'zh-Hans';
  example.textContent = pair.example;
  details.append(pinyin, relation, example);

  card.append(glyph, details);
  return card;
}

function classifierCell(
  pairs: NounClassifierPair[],
  kind: 'suitable' | 'best',
  nounId: string,
): HTMLTableCellElement {
  const cell = document.createElement('td');
  cell.dataset.label = kind === 'best' ? 'Наиболее подходящее' : 'Подходящее';
  if (pairs.length === 0) {
    const fallback = pairsByNoun.get(nounId)?.find((pair) => pair.classifier_id === 'CL001');
    if (fallback) cell.append(classifierCard(fallback, 'fallback'));
    return cell;
  }

  const list = document.createElement('div');
  list.className = 'classifier-list';
  for (const pair of pairs) list.append(classifierCard(pair, kind));
  cell.append(list);
  return cell;
}

function rowFor(entry: CatalogEntry, index: number): HTMLTableRowElement {
  const row = document.createElement('tr');
  row.style.setProperty('--row-index', String(index));
  row.append(
    nounCell(entry.noun),
    classifierCell(entry.suitable, 'suitable', entry.noun.noun_id),
    classifierCell(entry.best, 'best', entry.noun.noun_id),
  );
  return row;
}

function render(): void {
  const query = normalizedQuery(searchInput.value);
  const category = categorySelect.value;
  const filtered = entries.filter((entry) => {
    const normalizedSearchText = normalizedQuery(entry.searchText);
    return (!query || normalizedSearchText.includes(query)) && (!category || entry.noun.category === category);
  });

  const fragment = document.createDocumentFragment();
  filtered.forEach((entry, index) => fragment.append(rowFor(entry, index)));
  body.replaceChildren(fragment);
  resultCount.value = `${filtered.length} из ${entries.length}`;
  resultCount.textContent = `${filtered.length} из ${entries.length}`;
  emptyState.hidden = filtered.length !== 0;
}

searchInput.addEventListener('input', render);
categorySelect.addEventListener('change', render);
toTop.addEventListener('click', () => window.scrollTo({ top: 0, behavior: 'smooth' }));

render();

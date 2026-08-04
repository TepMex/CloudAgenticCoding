import type { GameSeed } from '../types';

export class SeedValidationError extends Error {
  readonly issues: string[];

  constructor(issues: string[]) {
    super(`База данных не прошла валидацию:\n${issues.map((issue) => `- ${issue}`).join('\n')}`);
    this.name = 'SeedValidationError';
    this.issues = issues;
  }
}

const splitIds = (value: string): string[] =>
  value.split(';').map((item) => item.trim()).filter(Boolean);

const hasUniqueValues = (values: string[]): boolean => new Set(values).size === values.length;

export function validateSeed(input: unknown): asserts input is GameSeed {
  const issues: string[] = [];
  if (!input || typeof input !== 'object') throw new SeedValidationError(['корневое значение должно быть объектом']);
  const seed = input as Partial<GameSeed>;
  if (!Array.isArray(seed.nouns)) issues.push('nouns должен быть массивом');
  if (!Array.isArray(seed.classifiers)) issues.push('classifiers должен быть массивом');
  if (!Array.isArray(seed.pairs)) issues.push('pairs должен быть массивом');
  if (!Array.isArray(seed.sources)) issues.push('sources должен быть массивом');
  if (issues.length) throw new SeedValidationError(issues);

  const nouns = seed.nouns!;
  const classifiers = seed.classifiers!;
  const pairs = seed.pairs!;
  const sources = seed.sources!;
  if (nouns.length !== 154) issues.push(`ожидалось 154 существительных, найдено ${nouns.length}`);
  if (classifiers.length !== 51) issues.push(`ожидался 51 классификатор, найдено ${classifiers.length}`);
  if (pairs.length !== 360) issues.push(`ожидалось 360 пар, найдено ${pairs.length}`);

  const nounIds = nouns.map((noun) => noun.noun_id);
  const classifierIds = classifiers.map((classifier) => classifier.classifier_id);
  const pairIds = pairs.map((pair) => pair.pair_id);
  const sourceIds = sources.map((source) => source.source_id);
  if (!hasUniqueValues(nounIds)) issues.push('noun_id должны быть уникальны');
  if (!hasUniqueValues(classifierIds)) issues.push('classifier_id должны быть уникальны');
  if (!hasUniqueValues(pairIds)) issues.push('pair_id должны быть уникальны');
  if (!hasUniqueValues(sourceIds)) issues.push('source_id должны быть уникальны');

  nouns.forEach((noun, index) => {
    const expectedId = `N${String(index + 1).padStart(3, '0')}`;
    if (noun.noun_id !== expectedId) issues.push(`nouns[${index}]: ожидался ${expectedId}, найден ${noun.noun_id}`);
    if (!noun.noun_zh || !noun.noun_pinyin || !noun.noun_ru || !noun.icon_key) issues.push(`${noun.noun_id}: обязательные строки пусты`);
    if (![1, 2, 3].includes(noun.difficulty)) issues.push(`${noun.noun_id}: difficulty должен быть 1–3`);
    if (!['да', 'нет'].includes(noun.gameplay_eligible)) issues.push(`${noun.noun_id}: gameplay_eligible должен быть «да» или «нет»`);
    if (!Number.isInteger(noun.ge_naturalness) || noun.ge_naturalness < 1 || noun.ge_naturalness > 5) issues.push(`${noun.noun_id}: ge_naturalness должен быть целым 1–5`);
  });

  const classifierById = new Map(classifiers.map((classifier) => [classifier.classifier_id, classifier]));
  classifiers.forEach((classifier, index) => {
    const expectedId = `CL${String(index + 1).padStart(3, '0')}`;
    if (classifier.classifier_id !== expectedId) issues.push(`classifiers[${index}]: ожидался ${expectedId}`);
    if (!classifier.hanzi || !classifier.pinyin) issues.push(`${classifier.classifier_id}: hanzi/pinyin пусты`);
    if (![1, 2, 3].includes(classifier.unlock_level)) issues.push(`${classifier.classifier_id}: unlock_level должен быть 1–3`);
    splitIds(classifier.source_ids).forEach((id) => {
      if (!sourceIds.includes(id)) issues.push(`${classifier.classifier_id}: неизвестный source_id ${id}`);
    });
  });

  const pairKeys = new Set<string>();
  pairs.forEach((pair) => {
    const classifier = classifierById.get(pair.classifier_id);
    if (!nounIds.includes(pair.noun_id)) issues.push(`${pair.pair_id}: неизвестный noun_id ${pair.noun_id}`);
    if (!classifier) issues.push(`${pair.pair_id}: неизвестный classifier_id ${pair.classifier_id}`);
    if (classifier && pair.classifier_hanzi !== classifier.hanzi) issues.push(`${pair.pair_id}: classifier_hanzi не совпадает со справочником`);
    if (!Number.isInteger(pair.linguistic_fit) || pair.linguistic_fit < 1 || pair.linguistic_fit > 5) issues.push(`${pair.pair_id}: linguistic_fit должен быть целым 1–5`);
    if (!Number.isInteger(pair.game_damage) || pair.game_damage < 1 || pair.game_damage > 5) issues.push(`${pair.pair_id}: game_damage должен быть целым 1–5`);
    if (pair.classifier_id === 'CL001' && pair.game_damage !== 1) issues.push(`${pair.pair_id}: 个 обязан наносить ровно 1 урон`);
    const key = `${pair.noun_id}:${pair.classifier_id}`;
    if (pairKeys.has(key)) issues.push(`${pair.pair_id}: дублирующая пара ${key}`);
    pairKeys.add(key);
    splitIds(pair.source_ids).forEach((id) => {
      if (!sourceIds.includes(id)) issues.push(`${pair.pair_id}: неизвестный source_id ${id}`);
    });
  });

  if (classifierById.get('CL001')?.hanzi !== '个') issues.push('CL001 должен быть классификатором 个');
  if (issues.length) throw new SeedValidationError(issues);
}

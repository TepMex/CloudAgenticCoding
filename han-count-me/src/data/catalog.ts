import type { Classifier, GameSeed, Noun, NounClassifierPair } from '../types';
import { validateSeed } from './validation';

export class GameCatalog {
  readonly nounById: Map<string, Noun>;
  readonly classifierById: Map<string, Classifier>;
  readonly pairByKey: Map<string, NounClassifierPair>;

  constructor(readonly seed: GameSeed) {
    validateSeed(seed);
    this.nounById = new Map(seed.nouns.map((noun) => [noun.noun_id, noun]));
    this.classifierById = new Map(seed.classifiers.map((classifier) => [classifier.classifier_id, classifier]));
    this.pairByKey = new Map(seed.pairs.map((pair) => [`${pair.noun_id}:${pair.classifier_id}`, pair]));
  }

  pair(nounId: string, classifierId: string): NounClassifierPair | undefined {
    return this.pairByKey.get(`${nounId}:${classifierId}`);
  }
}

let catalog: GameCatalog | undefined;

export function initializeCatalog(seed: unknown): GameCatalog {
  validateSeed(seed);
  catalog = new GameCatalog(seed);
  return catalog;
}

export function getCatalog(): GameCatalog {
  if (!catalog) throw new Error('GameCatalog accessed before BootScene initialization');
  return catalog;
}

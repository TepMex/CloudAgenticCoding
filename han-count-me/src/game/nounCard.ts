import Phaser from 'phaser';
import type { GameCatalog } from '../data/catalog';
import type { Noun } from '../types';
import { sheetTextureKey } from './assets';
import { addButton, addPanel, CINNABAR, GAME_HEIGHT, GAME_WIDTH } from './theme';

export interface NounCardOptions {
  eyebrow?: string;
  closeLabel?: string;
  onClose?: () => void;
}

export interface NounCardOverlay {
  container: Phaser.GameObjects.Container;
  close: () => void;
}

export function showNounCard(
  scene: Phaser.Scene,
  catalog: GameCatalog,
  noun: Noun,
  options: NounCardOptions = {},
): NounCardOverlay {
  const overlay = scene.add.container(0, 0).setDepth(50);
  const shade = scene.add.rectangle(GAME_WIDTH / 2, GAME_HEIGHT / 2, GAME_WIDTH, GAME_HEIGHT, 0x111510, 0.68).setInteractive();
  const panel = addPanel(scene, GAME_WIDTH / 2, GAME_HEIGHT / 2, 840, 500, 0.99);
  const index = Number(noun.noun_id.slice(1)) - 1;
  const image = scene.add.image(360, 355, sheetTextureKey(Math.floor(index / 16)), noun.noun_id).setDisplaySize(255, 255);
  const pairs = catalog.seed.pairs.filter((pair) => pair.noun_id === noun.noun_id)
    .sort((a, b) => b.game_damage - a.game_damage || b.linguistic_fit - a.linguistic_fit);
  const children: Phaser.GameObjects.GameObject[] = [shade, panel, image];

  if (options.eyebrow) {
    children.push(scene.add.text(530, 128, options.eyebrow, {
      fontFamily: 'system-ui, sans-serif', fontSize: '14px', color: '#943a31', fontStyle: 'bold',
    }));
  }

  const heading = scene.add.text(530, 170, `${noun.noun_zh}  ·  ${noun.noun_pinyin}`, {
    fontFamily: '"Noto Serif SC", serif', fontSize: '34px', color: '#282b26', fontStyle: 'bold',
  });
  const info = scene.add.text(530, 222, `${noun.noun_ru}\nКатегория: ${noun.category}\nСложность: ${'●'.repeat(noun.difficulty)}${'○'.repeat(3 - noun.difficulty)}`, {
    fontFamily: 'system-ui, sans-serif', fontSize: '16px', color: '#575249', lineSpacing: 8,
  });
  const pairText = pairs.slice(0, 5)
    .map((pair) => `${pair.classifier_hanzi}  ${catalog.classifierById.get(pair.classifier_id)?.pinyin ?? ''}  ·  ${pair.game_damage} урон  ·  ${pair.example}`)
    .join('\n');
  const combinations = scene.add.text(530, 330, pairText || 'Нет боевых сочетаний', {
    fontFamily: 'system-ui, sans-serif', fontSize: '16px', color: '#39463f', lineSpacing: 10, wordWrap: { width: 450 },
  });

  let closed = false;
  const close = (): void => {
    if (closed) return;
    closed = true;
    overlay.destroy(true);
    options.onClose?.();
  };
  const closeButton = addButton(scene, 910, 552, 210, 50, options.closeLabel ?? 'Закрыть', close, { fill: CINNABAR, fontSize: 18 });
  children.push(heading, info, combinations, closeButton);
  overlay.add(children);

  return { container: overlay, close };
}

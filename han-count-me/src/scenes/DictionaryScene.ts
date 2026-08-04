import Phaser from 'phaser';
import { getCatalog } from '../data/catalog';
import { sheetTextureKey } from '../game/assets';
import { loadProgress } from '../game/storage';
import { addBackButton, addButton, addInkBackdrop, addPanel, CINNABAR, GAME_WIDTH, INK, JADE_DARK, PAPER_LIGHT } from '../game/theme';
import type { Noun } from '../types';

const PAGE_SIZE = 12;

export class DictionaryScene extends Phaser.Scene {
  private page = 0;
  private pageContainer?: Phaser.GameObjects.Container;

  constructor() { super('DictionaryScene'); }

  create(): void {
    const progress = loadProgress();
    addInkBackdrop(this, 'Словарь', `词典 · ${progress.discoveredNouns.length} из 154 встречены в бою`);
    addBackButton(this, () => this.scene.start('MenuScene'));
    this.input.on('wheel', (_pointer: Phaser.Input.Pointer, _objects: unknown, _dx: number, dy: number) => {
      this.changePage(dy > 0 ? 1 : -1);
    });
    this.input.keyboard?.on('keydown-LEFT', () => this.changePage(-1));
    this.input.keyboard?.on('keydown-RIGHT', () => this.changePage(1));
    this.renderPage();
  }

  private changePage(delta: number): void {
    const pages = Math.ceil(getCatalog().seed.nouns.length / PAGE_SIZE);
    this.page = Phaser.Math.Wrap(this.page + delta, 0, pages);
    this.renderPage();
  }

  private renderPage(): void {
    this.pageContainer?.destroy(true);
    const catalog = getCatalog();
    const progress = loadProgress();
    const discovered = new Set(progress.discoveredNouns);
    const nouns = catalog.seed.nouns.slice(this.page * PAGE_SIZE, this.page * PAGE_SIZE + PAGE_SIZE);
    const container = this.add.container(0, 0);
    this.pageContainer = container;

    nouns.forEach((noun, index) => {
      const col = index % 4;
      const row = Math.floor(index / 4);
      const x = 206 + col * 290;
      const y = 190 + row * 143;
      const seen = discovered.has(noun.noun_id);
      const card = this.add.rectangle(x, y, 264, 124, PAPER_LIGHT, seen ? 0.95 : 0.72).setStrokeStyle(2, seen ? JADE_DARK : INK, seen ? 0.52 : 0.18);
      card.setInteractive({ useHandCursor: true }).on('pointerup', () => this.showDetails(noun));
      const image = this.add.image(x - 78, y, sheetTextureKey(Math.floor((Number(noun.noun_id.slice(1)) - 1) / 16)), noun.noun_id)
        .setDisplaySize(105, 105).setAlpha(seen ? 1 : 0.58);
      const zh = this.add.text(x - 8, y - 40, noun.noun_zh, {
        fontFamily: '"Noto Serif SC", serif', fontSize: '26px', color: '#272a25', fontStyle: 'bold',
      });
      const py = this.add.text(x - 8, y - 7, noun.noun_pinyin, {
        fontFamily: 'system-ui, sans-serif', fontSize: '13px', color: '#8a3c32',
      });
      const ru = this.add.text(x - 8, y + 17, noun.noun_ru, {
        fontFamily: 'system-ui, sans-serif', fontSize: '13px', color: '#555149', wordWrap: { width: 125 },
      });
      const mark = this.add.text(x + 112, y - 52, seen ? '已' : '·', {
        fontFamily: 'serif', fontSize: '13px', color: seen ? '#943a31' : '#9a9283',
      }).setOrigin(1, 0);
      container.add([card, image, zh, py, ru, mark]);
    });

    const pages = Math.ceil(catalog.seed.nouns.length / PAGE_SIZE);
    container.add(addButton(this, 165, 650, 160, 46, '←', () => this.changePage(-1), { fontSize: 20 }));
    container.add(this.add.text(GAME_WIDTH / 2, 650, `${this.page + 1} / ${pages}`, {
      fontFamily: 'system-ui, sans-serif', fontSize: '15px', color: '#554f46',
    }).setOrigin(0.5));
    container.add(addButton(this, 1115, 650, 160, 46, '→', () => this.changePage(1), { fontSize: 20 }));
  }

  private showDetails(noun: Noun): void {
    const catalog = getCatalog();
    const overlay = this.add.container(0, 0).setDepth(20);
    const shade = this.add.rectangle(GAME_WIDTH / 2, 360, GAME_WIDTH, 720, 0x111510, 0.68).setInteractive();
    const panel = addPanel(this, GAME_WIDTH / 2, 360, 840, 500, 0.99);
    const index = Number(noun.noun_id.slice(1)) - 1;
    const image = this.add.image(360, 355, sheetTextureKey(Math.floor(index / 16)), noun.noun_id).setDisplaySize(255, 255);
    const pairs = catalog.seed.pairs.filter((pair) => pair.noun_id === noun.noun_id)
      .sort((a, b) => b.game_damage - a.game_damage || b.linguistic_fit - a.linguistic_fit);
    const text = this.add.text(530, 170, `${noun.noun_zh}  ·  ${noun.noun_pinyin}`, {
      fontFamily: '"Noto Serif SC", serif', fontSize: '34px', color: '#282b26', fontStyle: 'bold',
    });
    const info = this.add.text(530, 222, `${noun.noun_ru}\nКатегория: ${noun.category}\nСложность: ${'●'.repeat(noun.difficulty)}${'○'.repeat(3 - noun.difficulty)}`, {
      fontFamily: 'system-ui, sans-serif', fontSize: '16px', color: '#575249', lineSpacing: 8,
    });
    const pairText = pairs.slice(0, 5).map((pair) => `${pair.classifier_hanzi}  ${catalog.classifierById.get(pair.classifier_id)?.pinyin ?? ''}  ·  ${pair.game_damage} урон  ·  ${pair.example}`).join('\n');
    const combinations = this.add.text(530, 330, pairText || 'Нет боевых сочетаний', {
      fontFamily: 'system-ui, sans-serif', fontSize: '16px', color: '#39463f', lineSpacing: 10, wordWrap: { width: 450 },
    });
    const close = addButton(this, 910, 552, 180, 50, 'Закрыть', () => overlay.destroy(true), { fill: CINNABAR, fontSize: 18 });
    overlay.add([shade, panel, image, text, info, combinations, close]);
  }
}

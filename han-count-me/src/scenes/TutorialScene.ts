import Phaser from 'phaser';
import { loadProgress, saveProgress } from '../game/storage';
import { addBackButton, addButton, addInkBackdrop, addPanel, CINNABAR, GAME_WIDTH, INK } from '../game/theme';
import { sheetTextureKey } from '../game/assets';

interface TutorialPage {
  kicker: string;
  title: string;
  body: string;
  glyph: string;
}

const pages: TutorialPage[] = [
  {
    kicker: 'СВИТОК I · ЦЕЛЬ',
    title: 'Не дай словам пройти ворота',
    body: 'Враги несут существительные. Они движутся справа налево. Коснись врага, чтобы выбрать цель, затем выбери подходящее счётное слово внизу.',
    glyph: '门',
  },
  {
    kicker: 'СВИТОК II · УРОН',
    title: 'Точность сильнее спешки',
    body: 'Канонические и естественные пары наносят урон из базы game_damage. 个 — безопасный запасной знак и всегда наносит ровно 1. Несуществующая пара не наносит урона.',
    glyph: '个',
  },
  {
    kicker: 'СВИТОК III · СЕРИЯ',
    title: 'Сохраняй ритм туши',
    body: 'Каждое попадание с уроном продолжает серию и увеличивает очки. Нулевая пара обрывает серию. После зачистки волны появится более сложная.',
    glyph: '连',
  },
  {
    kicker: 'СВИТОК IV · УПРАВЛЕНИЕ',
    title: 'Мышь, касание или клавиши',
    body: 'Нажимай карточки мер или клавиши 1–6. P и Esc ставят игру на паузу. Три врага, прошедшие ворота, завершают попытку.',
    glyph: '战',
  },
];

export class TutorialScene extends Phaser.Scene {
  private page = 0;
  private pageObjects: Phaser.GameObjects.GameObject[] = [];

  constructor() { super('TutorialScene'); }

  create(): void {
    addInkBackdrop(this, 'Учение хранителя', '四卷入门 · четыре коротких свитка');
    addBackButton(this, () => this.scene.start('MenuScene'));
    addPanel(this, GAME_WIDTH / 2, 378, 1030, 480, 0.86);
    this.renderPage();
  }

  private renderPage(): void {
    this.pageObjects.forEach((object) => object.destroy());
    this.pageObjects = [];
    const item = pages[this.page];
    const add = <T extends Phaser.GameObjects.GameObject>(object: T): T => { this.pageObjects.push(object); return object; };

    add(this.add.text(165, 185, item.kicker, {
      fontFamily: 'system-ui, sans-serif', fontSize: '13px', color: '#943a31', letterSpacing: 2,
    }));
    add(this.add.text(165, 225, item.title, {
      fontFamily: '"Noto Serif SC", Georgia, serif', fontSize: '34px', color: '#292b26', fontStyle: 'bold',
    }));
    add(this.add.text(165, 292, item.body, {
      fontFamily: 'system-ui, sans-serif', fontSize: '20px', color: '#4d4b43', lineSpacing: 11, wordWrap: { width: 595 },
    }));
    add(this.add.rectangle(913, 360, 250, 250, 0xdfd2b7, 0.5).setStrokeStyle(2, INK, 0.2));
    if (this.page === 0) {
      add(this.add.image(913, 370, sheetTextureKey(0), 'N001').setDisplaySize(220, 220));
    } else {
      add(this.add.text(913, 355, item.glyph, {
        fontFamily: '"Noto Serif SC", serif', fontSize: '130px', color: this.page === 1 ? '#943a31' : '#384b42', fontStyle: 'bold',
      }).setOrigin(0.5));
    }
    add(this.add.text(913, 498, this.page === 1 ? 'всегда 1 урон' : `${this.page + 1} / ${pages.length}`, {
      fontFamily: 'system-ui, sans-serif', fontSize: '14px', color: '#685f51',
    }).setOrigin(0.5));

    if (this.page > 0) add(addButton(this, 285, 585, 220, 56, '← Предыдущий', () => { this.page -= 1; this.renderPage(); }, { fontSize: 18 }));
    const isLast = this.page === pages.length - 1;
    add(addButton(this, isLast ? 845 : 915, 585, isLast ? 300 : 220, 56, isLast ? 'К воротам →' : 'Следующий →', () => {
      if (isLast) {
        const progress = loadProgress();
        progress.tutorialSeen = true;
        saveProgress(progress);
        this.scene.start('GameScene');
      } else {
        this.page += 1;
        this.renderPage();
      }
    }, { fill: isLast ? CINNABAR : undefined, fontSize: 18 }));
  }
}

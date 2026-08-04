import Phaser from 'phaser';
import { loadProgress } from '../game/storage';
import { addButton, addInkBackdrop, addSeal, CINNABAR, GAME_WIDTH, INK, PAPER_LIGHT } from '../game/theme';

export class MenuScene extends Phaser.Scene {
  constructor() { super('MenuScene'); }

  create(): void {
    const progress = loadProgress();
    addInkBackdrop(this);

    addSeal(this, 220, 245, '量', 108).setRotation(-0.04);
    this.add.text(303, 175, '量词守门人', {
      fontFamily: '"Noto Serif SC", "Songti SC", Georgia, serif', fontSize: '64px', color: '#252722', fontStyle: 'bold',
    });
    this.add.text(308, 251, 'HAN COUNT ME', {
      fontFamily: 'Georgia, serif', fontSize: '18px', color: '#943a31', letterSpacing: 8,
    });
    this.add.text(310, 292, 'Защити ворота точными китайскими счётными словами', {
      fontFamily: 'system-ui, sans-serif', fontSize: '18px', color: '#625e53',
    });

    const bestPanel = this.add.rectangle(1035, 188, 280, 126, PAPER_LIGHT, 0.88).setStrokeStyle(2, INK, 0.22);
    bestPanel.setRotation(0.01);
    this.add.text(1035, 157, 'ЛУЧШИЙ РЕЗУЛЬТАТ', {
      fontFamily: 'system-ui, sans-serif', fontSize: '12px', color: '#776e5d', letterSpacing: 2,
    }).setOrigin(0.5);
    this.add.text(1035, 194, String(progress.bestScore), {
      fontFamily: 'Georgia, serif', fontSize: '36px', color: '#2d342f', fontStyle: 'bold',
    }).setOrigin(0.5);
    this.add.text(1035, 226, `Лучшая волна: ${progress.highestWave}`, {
      fontFamily: 'system-ui, sans-serif', fontSize: '13px', color: '#776e5d',
    }).setOrigin(0.5);

    addButton(this, 340, 430, 330, 72, progress.highestWave ? 'Продолжить защиту' : 'Начать защиту', () => {
      this.scene.start(progress.tutorialSeen ? 'GameScene' : 'TutorialScene', progress.tutorialSeen ? undefined : { fromMenu: true });
    }, { fill: CINNABAR, hoverFill: 0xaa493e, fontSize: 25, subtitle: progress.tutorialSeen ? 'Новая попытка · прогресс сохранён' : 'Сначала короткое обучение' });

    addButton(this, 340, 526, 330, 60, 'Как играть', () => this.scene.start('TutorialScene'), { fontSize: 20 });
    addButton(this, 700, 430, 300, 60, 'Словарь', () => this.scene.start('DictionaryScene'), { fontSize: 20, subtitle: `Открыто: ${progress.discoveredNouns.length} / 154` });
    addButton(this, 700, 510, 300, 60, 'Настройки', () => this.scene.start('SettingsScene'), { fontSize: 20 });

    this.add.text(GAME_WIDTH / 2, 675, 'Коснитесь меры, чтобы выпустить чернильный знак · клавиши 1–6 тоже работают', {
      fontFamily: 'system-ui, sans-serif', fontSize: '13px', color: '#6f685a',
    }).setOrigin(0.5);
  }
}

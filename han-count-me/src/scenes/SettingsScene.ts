import Phaser from 'phaser';
import { loadProgress, saveProgress } from '../game/storage';
import { addBackButton, addButton, addInkBackdrop, addPanel, CINNABAR, GAME_WIDTH, JADE_DARK } from '../game/theme';

export class SettingsScene extends Phaser.Scene {
  constructor() { super('SettingsScene'); }

  create(): void {
    addInkBackdrop(this, 'Настройки', '调音定心 · игра хранит их только на этом устройстве');
    addBackButton(this, () => this.scene.start('MenuScene'));
    addPanel(this, GAME_WIDTH / 2, 365, 760, 420);
    this.renderOptions();
  }

  private renderOptions(): void {
    this.children.getByName('options')?.destroy();
    const progress = loadProgress();
    const group = this.add.container(0, 0).setName('options');
    const titleStyle: Phaser.Types.GameObjects.Text.TextStyle = {
      fontFamily: '"Noto Serif SC", serif', fontSize: '25px', color: '#2d302a', fontStyle: 'bold',
    };
    const hintStyle: Phaser.Types.GameObjects.Text.TextStyle = {
      fontFamily: 'system-ui, sans-serif', fontSize: '15px', color: '#6d6557',
    };
    group.add(this.add.text(345, 230, 'Звуковые знаки', titleStyle));
    group.add(this.add.text(345, 266, 'Короткая обратная связь при попадании и ошибке', hintStyle));
    group.add(addButton(this, 855, 252, 190, 58, progress.soundEnabled ? 'Включены' : 'Выключены', () => {
      progress.soundEnabled = !progress.soundEnabled; saveProgress(progress); this.renderOptions();
    }, { fill: progress.soundEnabled ? JADE_DARK : CINNABAR, fontSize: 18 }));

    group.add(this.add.text(345, 340, 'Подсказки урона', titleStyle));
    group.add(this.add.text(345, 376, 'Показывать результат пары после выстрела', hintStyle));
    group.add(addButton(this, 855, 362, 190, 58, progress.hintsEnabled ? 'Включены' : 'Выключены', () => {
      progress.hintsEnabled = !progress.hintsEnabled; saveProgress(progress); this.renderOptions();
    }, { fill: progress.hintsEnabled ? JADE_DARK : CINNABAR, fontSize: 18 }));

    group.add(this.add.text(GAME_WIDTH / 2, 503, `Рекорд: ${progress.bestScore} · Лучшая волна: ${progress.highestWave} · Открыто слов: ${progress.discoveredNouns.length}/154`, {
      ...hintStyle, fontSize: '16px', color: '#4f554d',
    }).setOrigin(0.5));
  }
}

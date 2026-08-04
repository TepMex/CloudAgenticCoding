import Phaser from 'phaser';
import { addButton, CINNABAR, GAME_HEIGHT, GAME_WIDTH, PAPER_LIGHT } from '../game/theme';

interface PauseData { score: number; wave: number }

export class PauseScene extends Phaser.Scene {
  constructor() { super('PauseScene'); }

  create(data: PauseData): void {
    this.add.rectangle(GAME_WIDTH / 2, GAME_HEIGHT / 2, GAME_WIDTH, GAME_HEIGHT, 0x151815, 0.72).setInteractive();
    this.add.rectangle(GAME_WIDTH / 2, 350, 500, 430, PAPER_LIGHT, 0.98).setStrokeStyle(3, CINNABAR, 0.8);
    this.add.text(GAME_WIDTH / 2, 220, '停', {
      fontFamily: '"Noto Serif SC", serif', fontSize: '62px', color: '#943a31', fontStyle: 'bold',
    }).setOrigin(0.5);
    this.add.text(GAME_WIDTH / 2, 282, 'Пауза', {
      fontFamily: '"Noto Serif SC", Georgia, serif', fontSize: '35px', color: '#292b26', fontStyle: 'bold',
    }).setOrigin(0.5);
    this.add.text(GAME_WIDTH / 2, 330, `Очки: ${data.score} · Волна: ${data.wave}`, {
      fontFamily: 'system-ui, sans-serif', fontSize: '16px', color: '#655f54',
    }).setOrigin(0.5);
    addButton(this, GAME_WIDTH / 2, 410, 290, 62, 'Продолжить', () => this.resumeGame(), { fontSize: 21 });
    addButton(this, GAME_WIDTH / 2, 494, 290, 56, 'В главное меню', () => {
      this.scene.stop('GameScene');
      this.scene.start('MenuScene');
    }, { fill: CINNABAR, fontSize: 18 });
    this.input.keyboard?.once('keydown-ESC', () => this.resumeGame());
    this.input.keyboard?.once('keydown-P', () => this.resumeGame());
  }

  private resumeGame(): void {
    this.scene.resume('GameScene');
    this.scene.stop();
  }
}

import Phaser from 'phaser';
import { addButton, addInkBackdrop, addPanel, addSeal, CINNABAR, GAME_WIDTH } from '../game/theme';

interface GameOverData {
  score: number;
  wave: number;
  combo: number;
  bestScore: number;
  isRecord: boolean;
  discovered: number;
}

export class GameOverScene extends Phaser.Scene {
  constructor() { super('GameOverScene'); }

  create(data: GameOverData): void {
    addInkBackdrop(this, 'Ворота пали', '败而后学 · поражение тоже оставляет знание');
    addPanel(this, GAME_WIDTH / 2, 378, 900, 500, 0.92);
    addSeal(this, 340, 330, data.isRecord ? '冠' : '败', 126).setRotation(-0.035);
    this.add.text(470, 192, data.isRecord ? 'Новый рекорд' : 'Попытка завершена', {
      fontFamily: '"Noto Serif SC", Georgia, serif', fontSize: '40px', color: '#292b26', fontStyle: 'bold',
    });
    this.add.text(470, 264, String(data.score), {
      fontFamily: 'Georgia, serif', fontSize: '72px', color: '#943a31', fontStyle: 'bold',
    });
    this.add.text(475, 348, `Лучший счёт: ${data.bestScore}\nДостигнута волна: ${data.wave}\nСерия при поражении: ×${data.combo}\nВстречено слов за попытку: ${data.discovered}`, {
      fontFamily: 'system-ui, sans-serif', fontSize: '18px', color: '#565249', lineSpacing: 11,
    });
    addButton(this, 555, 555, 270, 62, 'Ещё попытка', () => this.scene.start('GameScene'), { fill: CINNABAR, fontSize: 21 });
    addButton(this, 865, 555, 270, 62, 'Главное меню', () => this.scene.start('MenuScene'), { fontSize: 20 });
  }
}

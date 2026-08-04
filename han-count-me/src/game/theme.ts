import Phaser from 'phaser';

export const GAME_WIDTH = 1280;
export const GAME_HEIGHT = 720;

export const INK = 0x252722;
export const SOFT_INK = 0x55584e;
export const PAPER = 0xeadfc4;
export const PAPER_LIGHT = 0xf4ead2;
export const JADE = 0x526d62;
export const JADE_DARK = 0x33483f;
export const CINNABAR = 0x943a31;
export const GOLD = 0xb89a5a;

export interface ButtonOptions {
  fill?: number;
  hoverFill?: number;
  stroke?: number;
  textColor?: string;
  fontSize?: number;
  subtitle?: string;
  enabled?: boolean;
}

export function addInkBackdrop(scene: Phaser.Scene, title?: string, subtitle?: string): void {
  const g = scene.add.graphics();
  g.fillGradientStyle(PAPER_LIGHT, PAPER_LIGHT, PAPER, 0xd8caa9, 1);
  g.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);

  g.fillStyle(0x79857a, 0.13);
  g.beginPath();
  g.moveTo(0, 420);
  g.lineTo(150, 250);
  g.lineTo(260, 380);
  g.lineTo(430, 190);
  g.lineTo(610, 420);
  g.lineTo(770, 260);
  g.lineTo(980, 430);
  g.lineTo(1120, 240);
  g.lineTo(1280, 390);
  g.lineTo(1280, 560);
  g.lineTo(0, 560);
  g.closePath();
  g.fillPath();

  g.lineStyle(2, INK, 0.12);
  for (let i = 0; i < 9; i += 1) {
    g.beginPath();
    g.moveTo(0, 510 + i * 14);
    g.lineTo(210, 494 + i * 16);
    g.lineTo(430, 532 + i * 11);
    g.lineTo(670, 508 + i * 15);
    g.lineTo(900, 536 + i * 10);
    g.lineTo(1090, 500 + i * 16);
    g.lineTo(1280, 510 + i * 14);
    g.strokePath();
  }
  g.fillStyle(CINNABAR, 0.8);
  g.fillRect(34, 32, 8, 70);

  if (title) {
    scene.add.text(65, 37, title, {
      fontFamily: '"Noto Serif SC", "Songti SC", Georgia, serif',
      fontSize: '38px', color: '#282923', fontStyle: 'bold',
    });
  }
  if (subtitle) {
    scene.add.text(68, 83, subtitle, {
      fontFamily: 'system-ui, sans-serif', fontSize: '15px', color: '#665f53',
      letterSpacing: 1,
    });
  }
}

export function addButton(
  scene: Phaser.Scene,
  x: number,
  y: number,
  width: number,
  height: number,
  label: string,
  onPress: () => void,
  options: ButtonOptions = {},
): Phaser.GameObjects.Container {
  const fill = options.fill ?? JADE_DARK;
  const hoverFill = options.hoverFill ?? JADE;
  const stroke = options.stroke ?? GOLD;
  const enabled = options.enabled ?? true;
  const bg = scene.add.rectangle(0, 0, width, height, fill, enabled ? 0.96 : 0.4)
    .setStrokeStyle(2, stroke, enabled ? 0.7 : 0.25);
  const text = scene.add.text(0, options.subtitle ? -9 : 0, label, {
    fontFamily: '"Noto Serif SC", "Songti SC", Georgia, serif',
    fontSize: `${options.fontSize ?? 22}px`, color: options.textColor ?? '#f2e5c9', fontStyle: 'bold',
    align: 'center',
  }).setOrigin(0.5);
  const children: Phaser.GameObjects.GameObject[] = [bg, text];
  if (options.subtitle) {
    children.push(scene.add.text(0, 16, options.subtitle, {
      fontFamily: 'system-ui, sans-serif', fontSize: '12px', color: '#d5c9af', align: 'center',
    }).setOrigin(0.5));
  }
  const button = scene.add.container(x, y, children);
  if (enabled) {
    bg.setInteractive({ useHandCursor: true })
      .on('pointerover', () => { bg.setFillStyle(hoverFill, 1); button.setScale(1.02); })
      .on('pointerout', () => { bg.setFillStyle(fill, 0.96); button.setScale(1); })
      .on('pointerdown', () => { button.setScale(0.98); })
      .on('pointerup', () => { button.setScale(1.02); onPress(); });
  }
  return button;
}

export function addBackButton(scene: Phaser.Scene, onPress: () => void): void {
  addButton(scene, 1175, 55, 145, 48, '← Назад', onPress, { fontSize: 18, fill: SOFT_INK });
}

export function addSeal(scene: Phaser.Scene, x: number, y: number, text: string, size = 58): Phaser.GameObjects.Container {
  const square = scene.add.rectangle(0, 0, size, size, CINNABAR, 0.95).setStrokeStyle(2, PAPER_LIGHT, 0.9);
  const glyph = scene.add.text(0, 0, text, {
    fontFamily: '"Noto Serif SC", serif', fontSize: `${Math.round(size * 0.56)}px`, color: '#f3dec0', fontStyle: 'bold',
  }).setOrigin(0.5);
  return scene.add.container(x, y, [square, glyph]);
}

export function addPanel(scene: Phaser.Scene, x: number, y: number, width: number, height: number, alpha = 0.92): Phaser.GameObjects.Rectangle {
  return scene.add.rectangle(x, y, width, height, PAPER_LIGHT, alpha).setStrokeStyle(2, SOFT_INK, 0.3);
}

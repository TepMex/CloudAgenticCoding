import Phaser from "phaser";

export class HanziEnemy extends Phaser.GameObjects.Container {
  static readonly RADIUS = 36;

  readonly hanzi: string;
  readonly expectedPinyin: string;

  frozen = false;

  constructor(
    scene: Phaser.Scene,
    x: number,
    y: number,
    hanzi: string,
    expectedPinyin: string,
    showPinyinHint: boolean,
  ) {
    super(scene, x, y);

    this.hanzi = hanzi;
    this.expectedPinyin = expectedPinyin;

    const g = scene.add.graphics();
    g.fillStyle(0x16213e, 1);
    g.lineStyle(3, 0xffffff, 0.95);
    g.fillCircle(0, 0, HanziEnemy.RADIUS);
    g.strokeCircle(0, 0, HanziEnemy.RADIUS);

    const hanziText = scene.add.text(0, 0, hanzi, {
      fontFamily: '"Noto Sans SC", sans-serif',
      fontSize: "32px",
      color: "#ffffff",
    });
    hanziText.setOrigin(0.5);

    const parts: Phaser.GameObjects.GameObject[] = [g];
    if (showPinyinHint) {
      const hint = scene.add.text(0, -HanziEnemy.RADIUS - 18, expectedPinyin, {
        fontFamily: "system-ui, sans-serif",
        fontSize: "15px",
        color: "#aabbcc",
      });
      hint.setOrigin(0.5);
      parts.push(hint);
    }
    parts.push(hanziText);

    this.add(parts);

    this.setSize(HanziEnemy.RADIUS * 2, HanziEnemy.RADIUS * 2);
    this.setInteractive(
      new Phaser.Geom.Circle(0, 0, HanziEnemy.RADIUS),
      Phaser.Geom.Circle.Contains,
    );
  }

  moveToward(cx: number, cy: number, pixels: number): void {
    if (this.frozen) return;
    const d = Phaser.Math.Distance.Between(this.x, this.y, cx, cy);
    if (d < 1) return;
    const t = Math.min(pixels / d, 1);
    this.x = Phaser.Math.Linear(this.x, cx, t);
    this.y = Phaser.Math.Linear(this.y, cy, t);
  }
}

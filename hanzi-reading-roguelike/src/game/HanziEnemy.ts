import Phaser from "phaser";
import {
  CHARACTER_CELL_HEIGHT,
  type CharacterDef,
} from "../characters";
import { THEME } from "../theme";

export class HanziEnemy extends Phaser.GameObjects.Container {
  static readonly DISPLAY_HEIGHT = 104;
  static readonly RADIUS = 52;

  readonly hanzi: string;
  /** All accepted toneless readings. */
  readonly expectedPinyins: string[];
  readonly keyword: string;
  readonly character: CharacterDef;

  frozen = false;

  constructor(
    scene: Phaser.Scene,
    x: number,
    y: number,
    hanzi: string,
    expectedPinyins: string[],
    keyword: string,
    hintText: string | null,
    character: CharacterDef,
  ) {
    super(scene, x, y);

    this.hanzi = hanzi;
    this.expectedPinyins = expectedPinyins;
    this.keyword = keyword;
    this.character = character;

    const scale = HanziEnemy.DISPLAY_HEIGHT / CHARACTER_CELL_HEIGHT;
    const sprite = scene.add.image(0, 0, character.textureKey);
    sprite.setScale(scale);

    const hanziX = character.hanziOffsetX * scale;
    const hanziY = character.hanziOffsetY * scale;
    const hanziText = scene.add.text(hanziX, hanziY, hanzi, {
      fontFamily: '"Noto Sans SC", sans-serif',
      fontSize: "22px",
      color: THEME.hanziOnSprite,
      fontStyle: "bold",
    });
    hanziText.setOrigin(0.5);

    const parts: Phaser.GameObjects.GameObject[] = [sprite];
    if (hintText) {
      const hint = scene.add.text(0, -HanziEnemy.DISPLAY_HEIGHT / 2 - 14, hintText, {
        fontFamily: "system-ui, sans-serif",
        fontSize: "14px",
        color: THEME.textMuted,
        backgroundColor: "rgba(250, 243, 230, 0.85)",
        padding: { x: 6, y: 2 },
        wordWrap: { width: 120 },
        align: "center",
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

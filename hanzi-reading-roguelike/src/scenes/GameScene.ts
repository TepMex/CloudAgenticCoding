import Phaser from "phaser";
import {
  CHARACTER_TEXTURE_URLS,
  pickRandomCharacter,
} from "../characters";
import { HANZI_DATA } from "../hanziData";
import {
  bumpEncounter,
  getEncounterCount,
  loadEncounters,
  type EncounterMap,
} from "../encounters";
import { HanziEnemy } from "../game/HanziEnemy";
import { normalizePinyin, promptPinyin } from "../pinyinModal";
import { THEME } from "../theme";
import { SCENE_GAME, SCENE_MENU } from "./sceneKeys";

/** Movement update cadence — balances smooth motion and CPU on mobile. */
const MOVE_TICK_MS = 120;
const SPAWN_BASE_MS = 2600;
const SPAWN_MIN_MS = 720;
const CORE_FAIL_DIST = 26;
const DIFFICULTY_INTERVAL_MS = 14000;

export default class GameScene extends Phaser.Scene {
  private encounterMap: EncounterMap = {};
  private enemies: HanziEnemy[] = [];
  private answering = false;
  private gameOver = false;
  private kills = 0;
  private difficulty = 0;
  private currentSpawnInterval = SPAWN_BASE_MS;

  private tickEvent?: Phaser.Time.TimerEvent;
  private difficultyEvent?: Phaser.Time.TimerEvent;
  private spawnTimer?: Phaser.Time.TimerEvent;

  private coreGraphics?: Phaser.GameObjects.Graphics;
  private hud?: Phaser.GameObjects.Text;
  private gameOverLayer?: Phaser.GameObjects.Container;

  constructor() {
    super(SCENE_GAME);
  }

  preload(): void {
    for (const [key, url] of Object.entries(CHARACTER_TEXTURE_URLS)) {
      this.load.image(key, url);
    }
  }

  create(): void {
    this.encounterMap = loadEncounters();
    this.enemies = [];
    this.answering = false;
    this.gameOver = false;
    this.kills = 0;
    this.difficulty = 0;
    this.currentSpawnInterval = SPAWN_BASE_MS;

    this.drawCore();
    this.hud = this.add
      .text(16, 16, "", {
        fontFamily: "system-ui, sans-serif",
        fontSize: "15px",
        color: THEME.hud,
      })
      .setScrollFactor(0)
      .setDepth(10);

    this.tickEvent = this.time.addEvent({
      delay: MOVE_TICK_MS,
      loop: true,
      callback: () => this.onMoveTick(),
    });

    this.difficultyEvent = this.time.addEvent({
      delay: DIFFICULTY_INTERVAL_MS,
      loop: true,
      callback: () => this.bumpDifficulty(),
    });

    this.queueSpawnLoop();
    this.time.delayedCall(350, () => {
      if (!this.gameOver && !this.answering) {
        this.spawnEnemy();
      }
    });

    this.input.on("pointerdown", this.onPointerDown, this);

    this.scale.on("resize", this.onResize, this);
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.input.off("pointerdown", this.onPointerDown, this);
      this.scale.off("resize", this.onResize, this);
    });

    this.updateHud();
  }

  private queueSpawnLoop(): void {
    if (this.spawnTimer) {
      this.spawnTimer.remove(false);
      this.spawnTimer = undefined;
    }
    this.spawnTimer = this.time.addEvent({
      delay: this.currentSpawnInterval,
      callback: () => {
        if (this.gameOver) return;
        if (!this.answering) {
          this.spawnEnemy();
        }
        this.queueSpawnLoop();
      },
    });
  }

  private bumpDifficulty(): void {
    if (this.gameOver) return;
    this.difficulty += 1;
    this.currentSpawnInterval = Math.max(
      SPAWN_MIN_MS,
      Math.floor(this.currentSpawnInterval * 0.92),
    );
    this.queueSpawnLoop();
    this.updateHud();
  }

  private getPixelsPerTick(): number {
    return 2.6 + this.difficulty * 0.4;
  }

  private drawCore(): void {
    if (this.coreGraphics) {
      this.coreGraphics.destroy();
    }
    const cx = this.cameras.main.centerX;
    const cy = this.cameras.main.centerY;
    const g = this.add.graphics();
    const len = 16;
    g.lineStyle(2, 0xb91c1c, 1);
    g.lineBetween(cx - len, cy, cx + len, cy);
    g.lineBetween(cx, cy - len, cx, cy + len);
    this.coreGraphics = g;
  }

  private onResize(): void {
    this.drawCore();
    if (this.gameOverLayer) {
      this.showGameOverUi();
    }
  }

  private spawnEnemy(): void {
    const w = this.scale.width;
    const h = this.scale.height;
    const pad = HanziEnemy.RADIUS + 8;
    const edge = Phaser.Math.Between(0, 3);
    let x = 0;
    let y = 0;
    switch (edge) {
      case 0:
        x = Phaser.Math.Between(pad, w - pad);
        y = pad;
        break;
      case 1:
        x = w - pad;
        y = Phaser.Math.Between(pad, h - pad);
        break;
      case 2:
        x = Phaser.Math.Between(pad, w - pad);
        y = h - pad;
        break;
      default:
        x = pad;
        y = Phaser.Math.Between(pad, h - pad);
        break;
    }

    const entry = Phaser.Utils.Array.GetRandom(HANZI_DATA) as (typeof HANZI_DATA)[number];
    const seen = getEncounterCount(this.encounterMap, entry.hanzi);
    const showHint = seen < 5;
    this.encounterMap = bumpEncounter(this.encounterMap, entry.hanzi);

    const character = pickRandomCharacter();
    const enemy = new HanziEnemy(
      this,
      x,
      y,
      entry.hanzi,
      entry.pinyin,
      showHint,
      character,
    );
    this.add.existing(enemy);
    this.enemies.push(enemy);
  }

  private onMoveTick(): void {
    if (this.gameOver || this.answering) return;

    const cx = this.cameras.main.centerX;
    const cy = this.cameras.main.centerY;
    const px = this.getPixelsPerTick();

    for (const e of this.enemies) {
      e.moveToward(cx, cy, px);
      if (Phaser.Math.Distance.Between(e.x, e.y, cx, cy) < CORE_FAIL_DIST) {
        this.triggerGameOver();
        return;
      }
    }
    this.updateHud();
  }

  private updateHud(): void {
    if (!this.hud) return;
    this.hud.setText(
      `Cleared: ${this.kills}\nDifficulty: ${this.difficulty}\nSpawn: ${(this.currentSpawnInterval / 1000).toFixed(1)}s`,
    );
  }

  private onPointerDown(pointer: Phaser.Input.Pointer): void {
    if (this.gameOver || this.answering) return;

    const wx = pointer.worldX;
    const wy = pointer.worldY;

    for (let i = this.enemies.length - 1; i >= 0; i--) {
      const e = this.enemies[i];
      if (!e.active) continue;
      const d = Phaser.Math.Distance.Between(wx, wy, e.x, e.y);
      if (d <= HanziEnemy.RADIUS + 6) {
        void this.beginAnswer(e);
        break;
      }
    }
  }

  private async beginAnswer(enemy: HanziEnemy): Promise<void> {
    if (!this.enemies.includes(enemy)) return;

    this.answering = true;
    for (const e of this.enemies) {
      e.frozen = true;
    }

    const res = await promptPinyin(enemy.hanzi);

    this.answering = false;
    for (const e of this.enemies) {
      e.frozen = false;
    }

    if (this.gameOver) return;

    if (!res.ok) {
      return;
    }

    const ok =
      normalizePinyin(res.answer) === normalizePinyin(enemy.expectedPinyin);

    if (ok) {
      this.kills += 1;
      this.enemies = this.enemies.filter((x) => x !== enemy);
      enemy.destroy();
      this.cameras.main.flash(90, 60, 200, 100, false);
    } else {
      this.cameras.main.shake(140, 0.004);
    }

    this.updateHud();
  }

  private stopGameplayTimers(): void {
    this.tickEvent?.destroy();
    this.difficultyEvent?.destroy();
    this.spawnTimer?.destroy();
    this.tickEvent = undefined;
    this.difficultyEvent = undefined;
    this.spawnTimer = undefined;
  }

  private triggerGameOver(): void {
    if (this.gameOver) return;
    this.gameOver = true;
    this.stopGameplayTimers();
    this.showGameOverUi();
    this.cameras.main.shake(220, 0.012);
  }

  private showGameOverUi(): void {
    if (this.gameOverLayer) {
      this.gameOverLayer.destroy(true);
    }

    const w = this.scale.width;
    const h = this.scale.height;
    const cx = w / 2;
    const cy = h / 2;

    const layer = this.add.container(0, 0);
    layer.setDepth(100);

    const dim = this.add.graphics();
    dim.fillStyle(0x3d2914, 0.55);
    dim.fillRect(0, 0, w, h);

    const msg = this.add
      .text(cx, cy - 56, "A hanzi reached the center", {
        fontFamily: "system-ui, sans-serif",
        fontSize: "24px",
        color: THEME.text,
        align: "center",
      })
      .setOrigin(0.5);

    const stats = this.add
      .text(cx, cy - 8, `Cleared: ${this.kills}`, {
        fontFamily: "system-ui, sans-serif",
        fontSize: "16px",
        color: THEME.textMuted,
      })
      .setOrigin(0.5);

    const mkBtn = (label: string, y: number, onClick: () => void) => {
      const t = this.add
        .text(cx, y, label, {
          fontFamily: "system-ui, sans-serif",
          fontSize: "20px",
          color: THEME.panel,
          backgroundColor: THEME.accent,
          padding: { x: 22, y: 12 },
        })
        .setOrigin(0.5)
        .setInteractive({ useHandCursor: true });
      t.on("pointerover", () => t.setStyle({ backgroundColor: THEME.accentHover }));
      t.on("pointerout", () => t.setStyle({ backgroundColor: THEME.accent }));
      t.on("pointerup", onClick);
      return t;
    };

    const retry = mkBtn(cx, cy + 48, () => {
      this.scene.restart();
    });

    const menu = mkBtn(cx, cy + 112, () => {
      this.scene.start(SCENE_MENU);
    });

    layer.add([dim, msg, stats, retry, menu]);
    this.gameOverLayer = layer;
  }
}

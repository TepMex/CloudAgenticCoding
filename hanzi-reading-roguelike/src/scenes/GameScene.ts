import Phaser from "phaser";
import {
  CHARACTER_TEXTURE_URLS,
  pickRandomCharacter,
} from "../characters";
import {
  getRthList,
  rthListCount,
  type HanziEntry,
  type RthList,
} from "../hanziData";
import {
  bumpEncounter,
  getEncounterCount,
  loadEncounters,
  type EncounterMap,
} from "../encounters";
import { HanziEnemy } from "../game/HanziEnemy";
import {
  isListComplete,
  pixelsPerTickForList,
  spawnIntervalForList,
} from "../listProgress";
import { normalizePinyin, promptPinyin } from "../pinyinModal";
import { THEME } from "../theme";
import { SCENE_GAME, SCENE_MENU } from "./sceneKeys";

/** Movement update cadence — balances smooth motion and CPU on mobile. */
const MOVE_TICK_MS = 120;
const CORE_FAIL_DIST = 26;

export default class GameScene extends Phaser.Scene {
  private encounterMap: EncounterMap = {};
  private enemies: HanziEnemy[] = [];
  private answering = false;
  private gameOver = false;
  private kills = 0;

  /** Current RTH list index — difficulty rises only when this advances. */
  private listIndex = 0;
  /** Unique hanzi cleared from the current list this run. */
  private clearedInList = new Set<string>();
  private currentSpawnInterval = SPAWN_BASE_MS;

  private tickEvent?: Phaser.Time.TimerEvent;
  private spawnTimer?: Phaser.Time.TimerEvent;

  private coreGraphics?: Phaser.GameObjects.Graphics;
  private hud?: Phaser.GameObjects.Text;
  private gameOverLayer?: Phaser.GameObjects.Container;
  private listBanner?: Phaser.GameObjects.Text;

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
    this.listIndex = 0;
    this.clearedInList = new Set();
    this.currentSpawnInterval = spawnIntervalForList(this.listIndex);

    this.drawCore();
    this.hud = this.add
      .text(16, 16, "", {
        fontFamily: "system-ui, sans-serif",
        fontSize: "15px",
        color: THEME.hud,
      })
      .setScrollFactor(0)
      .setDepth(10);

    this.listBanner = this.add
      .text(this.cameras.main.centerX, 16, "", {
        fontFamily: "system-ui, sans-serif",
        fontSize: "13px",
        color: THEME.textMuted,
        align: "center",
        wordWrap: { width: Math.min(this.scale.width - 32, 320) },
      })
      .setOrigin(0.5, 0)
      .setScrollFactor(0)
      .setDepth(10);

    this.tickEvent = this.time.addEvent({
      delay: MOVE_TICK_MS,
      loop: true,
      callback: () => this.onMoveTick(),
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

  private currentList(): RthList | undefined {
    return getRthList(this.listIndex);
  }

  private unclearedEntries(list: RthList): HanziEntry[] {
    return list.entries.filter((e) => !this.clearedInList.has(e.hanzi));
  }

  private applyListDifficulty(): void {
    this.currentSpawnInterval = spawnIntervalForList(this.listIndex);
    this.queueSpawnLoop();
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

  private getPixelsPerTick(): number {
    return pixelsPerTickForList(this.listIndex);
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
    if (this.listBanner) {
      this.listBanner.setPosition(this.cameras.main.centerX, 16);
      this.listBanner.setStyle({
        wordWrap: { width: Math.min(this.scale.width - 32, 320) },
      });
    }
    if (this.gameOverLayer) {
      this.showGameOverUi();
    }
  }

  private spawnEnemy(): void {
    const list = this.currentList();
    if (!list) return;

    const pool = this.unclearedEntries(list);
    if (pool.length === 0) {
      // Waiting for on-screen leftovers; next list unlocks after last kill.
      return;
    }

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

    const entry = Phaser.Utils.Array.GetRandom(pool) as HanziEntry;
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
    const list = this.currentList();
    const total = list?.entries.length ?? 0;
    const done = this.clearedInList.size;
    const listLabel = list ? `${list.id}` : "—";
    const listOrdinal = `${this.listIndex + 1}/${rthListCount()}`;

    this.hud.setText(
      `Cleared: ${this.kills}\nList: ${listLabel} (${listOrdinal})\nProgress: ${done}/${total}\nDifficulty: ${this.listIndex}\nSpawn: ${(this.currentSpawnInterval / 1000).toFixed(1)}s`,
    );

    if (this.listBanner && list) {
      this.listBanner.setText(list.name);
    }
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

  private matchesPinyin(answer: string, expected: string[]): boolean {
    const n = normalizePinyin(answer);
    return expected.some((p) => normalizePinyin(p) === n);
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

    const ok = this.matchesPinyin(res.answer, enemy.expectedPinyins);

    if (ok) {
      this.kills += 1;
      this.clearedInList.add(enemy.hanzi);
      this.enemies = this.enemies.filter((x) => x !== enemy);
      enemy.destroy();
      this.cameras.main.flash(90, 60, 200, 100, false);

      // Remove other on-screen copies of the same hanzi once cleared from the list.
      this.enemies = this.enemies.filter((e) => {
        if (e.hanzi === enemy.hanzi) {
          e.destroy();
          return false;
        }
        return true;
      });

      this.tryAdvanceList();
    } else {
      this.cameras.main.shake(140, 0.004);
    }

    this.updateHud();
  }

  private tryAdvanceList(): void {
    const list = this.currentList();
    if (!list) return;
    if (!isListComplete(list.entries.length, this.clearedInList)) return;

    const next = this.listIndex + 1;
    if (next >= rthListCount()) {
      this.triggerVictory();
      return;
    }

    this.listIndex = next;
    this.clearedInList = new Set();
    this.applyListDifficulty();
    this.flashListUnlock();
    this.updateHud();
  }

  private flashListUnlock(): void {
    const list = this.currentList();
    if (!list) return;
    const cx = this.cameras.main.centerX;
    const cy = this.cameras.main.centerY * 0.35;
    const t = this.add
      .text(cx, cy, `List unlocked\n${list.id}: ${list.name}`, {
        fontFamily: "system-ui, sans-serif",
        fontSize: "18px",
        color: THEME.text,
        align: "center",
        backgroundColor: "rgba(61, 41, 20, 0.75)",
        padding: { x: 14, y: 10 },
      })
      .setOrigin(0.5)
      .setDepth(50)
      .setAlpha(0);

    this.tweens.add({
      targets: t,
      alpha: 1,
      duration: 220,
      yoyo: true,
      hold: 1200,
      onComplete: () => t.destroy(),
    });
  }

  private stopGameplayTimers(): void {
    this.tickEvent?.destroy();
    this.spawnTimer?.destroy();
    this.tickEvent = undefined;
    this.spawnTimer = undefined;
  }

  private triggerVictory(): void {
    if (this.gameOver) return;
    this.gameOver = true;
    this.stopGameplayTimers();
    this.showVictoryUi();
  }

  private triggerGameOver(): void {
    if (this.gameOver) return;
    this.gameOver = true;
    this.stopGameplayTimers();
    this.showGameOverUi();
    this.cameras.main.shake(220, 0.012);
  }

  private showVictoryUi(): void {
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
      .text(cx, cy - 56, "All RTH lists cleared!", {
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

    const retry = mkBtn("Play again", cy + 48, () => {
      this.scene.restart();
    });

    const menu = mkBtn("Menu", cy + 112, () => {
      this.scene.start(SCENE_MENU);
    });

    layer.add([dim, msg, stats, retry, menu]);
    this.gameOverLayer = layer;
  }

  private showGameOverUi(): void {
    if (this.gameOverLayer) {
      this.gameOverLayer.destroy(true);
    }

    const w = this.scale.width;
    const h = this.scale.height;
    const cx = w / 2;
    const cy = h / 2;
    const list = this.currentList();

    const layer = this.add.container(0, 0);
    layer.setDepth(100);

    const dim = this.add.graphics();
    dim.fillStyle(0x3d2914, 0.55);
    dim.fillRect(0, 0, w, h);

    const msg = this.add
      .text(cx, cy - 72, "A hanzi reached the center", {
        fontFamily: "system-ui, sans-serif",
        fontSize: "24px",
        color: THEME.text,
        align: "center",
      })
      .setOrigin(0.5);

    const stats = this.add
      .text(
        cx,
        cy - 16,
        `Cleared: ${this.kills}\nList: ${list?.id ?? "—"} (${this.clearedInList.size}/${list?.entries.length ?? 0})`,
        {
          fontFamily: "system-ui, sans-serif",
          fontSize: "16px",
          color: THEME.textMuted,
          align: "center",
        },
      )
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

    const retry = mkBtn("Retry", cy + 56, () => {
      this.scene.restart();
    });

    const menu = mkBtn("Menu", cy + 120, () => {
      this.scene.start(SCENE_MENU);
    });

    layer.add([dim, msg, stats, retry, menu]);
    this.gameOverLayer = layer;
  }
}

import Phaser from "phaser";
import { GAME_EVENTS } from "../events";
import { HINT_VISIBLE_FOR_ENCOUNTERS, HanziEntry, seedHanziData } from "../hanziData";

const GAME_WIDTH = 390;
const GAME_HEIGHT = 844;
const CENTER_POINT = new Phaser.Math.Vector2(GAME_WIDTH / 2, GAME_HEIGHT / 2);
const CENTER_RADIUS = 32;
const ENEMY_RADIUS = 36;
const SPAWN_EVERY_MS = 3500;
const TICK_EVERY_MS = 1000;
const STEP_PER_TICK = 6;
const MAX_ACTIVE_ENEMIES = 12;

type EnemyState = {
  id: number;
  entry: HanziEntry;
  circle: Phaser.GameObjects.Arc;
  hanziText: Phaser.GameObjects.Text;
  hintText?: Phaser.GameObjects.Text;
  encounteredCount: number;
};

export class GameScene extends Phaser.Scene {
  private enemies = new Map<number, EnemyState>();
  private selectedEnemyId: number | null = null;
  private nextEnemyId = 1;
  private score = 0;
  private encounterByHanzi = new Map<string, number>();
  private spawnTimer?: Phaser.Time.TimerEvent;
  private tickTimer?: Phaser.Time.TimerEvent;
  private gameFinished = false;

  constructor() {
    super("GameScene");
  }

  create(): void {
    this.gameFinished = false;
    this.score = 0;
    this.selectedEnemyId = null;
    this.nextEnemyId = 1;
    this.enemies.clear();
    this.encounterByHanzi.clear();

    this.cameras.main.setBackgroundColor(0x121d37);
    this.drawCenterMarker();

    this.spawnTimer = this.time.addEvent({
      delay: SPAWN_EVERY_MS,
      loop: true,
      callback: () => {
        if (this.selectedEnemyId === null && this.enemies.size < MAX_ACTIVE_ENEMIES) {
          this.spawnEnemy();
        }
      }
    });

    this.tickTimer = this.time.addEvent({
      delay: TICK_EVERY_MS,
      loop: true,
      callback: () => this.stepEnemiesTowardCenter()
    });

    this.game.events.emit(GAME_EVENTS.enemyCleared);
    this.emitStats();
    this.input.on("pointerdown", this.handlePointerDown, this);

    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => this.cleanupScene());
  }

  submitAnswer(rawInput: string): boolean {
    if (this.gameFinished || this.selectedEnemyId === null) {
      return false;
    }

    const enemy = this.enemies.get(this.selectedEnemyId);
    if (!enemy) {
      this.selectedEnemyId = null;
      this.game.events.emit(GAME_EVENTS.enemyCleared);
      return false;
    }

    const normalizedInput = this.normalizePinyin(rawInput);
    const expected = this.normalizePinyin(enemy.entry.pinyin);
    if (normalizedInput.length === 0) {
      return false;
    }

    if (normalizedInput === expected) {
      this.score += 1;
      this.destroyEnemy(enemy.id);
      this.game.events.emit(GAME_EVENTS.enemyCleared);
      this.game.events.emit(GAME_EVENTS.feedback, {
        text: "Correct!"
      });
      this.emitStats();
      return true;
    }

    this.game.events.emit(GAME_EVENTS.feedback, {
      text: `Try again: ${enemy.entry.hanzi}`,
      isError: true
    });
    this.tweens.add({
      targets: [enemy.circle, enemy.hanziText, enemy.hintText].filter(Boolean),
      scaleX: 1.1,
      scaleY: 1.1,
      yoyo: true,
      duration: 90,
      repeat: 1
    });
    return false;
  }

  private drawCenterMarker(): void {
    this.add.circle(CENTER_POINT.x, CENTER_POINT.y, CENTER_RADIUS, 0x243b6b, 1).setStrokeStyle(2, 0x6ea6ff);
    const cross = this.add.graphics();
    cross.lineStyle(4, 0xeff5ff, 1);
    cross.beginPath();
    cross.moveTo(CENTER_POINT.x - 16, CENTER_POINT.y);
    cross.lineTo(CENTER_POINT.x + 16, CENTER_POINT.y);
    cross.moveTo(CENTER_POINT.x, CENTER_POINT.y - 16);
    cross.lineTo(CENTER_POINT.x, CENTER_POINT.y + 16);
    cross.strokePath();
  }

  private spawnEnemy(): void {
    const entry = Phaser.Utils.Array.GetRandom(seedHanziData);
    const encounteredCount = (this.encounterByHanzi.get(entry.hanzi) ?? 0) + 1;
    this.encounterByHanzi.set(entry.hanzi, encounteredCount);

    const spawnPoint = this.randomSpawnPoint();
    const enemyId = this.nextEnemyId++;
    const enemy = this.createEnemyNode(enemyId, entry, encounteredCount, spawnPoint.x, spawnPoint.y);
    this.enemies.set(enemyId, enemy);
    this.emitStats();
  }

  private createEnemyNode(id: number, entry: HanziEntry, encounteredCount: number, x: number, y: number): EnemyState {
    const circle = this.add
      .circle(x, y, ENEMY_RADIUS, 0x193050, 1)
      .setStrokeStyle(3, 0x8db4ff, 1);

    const hanziText = this.add
      .text(x, y + (encounteredCount <= HINT_VISIBLE_FOR_ENCOUNTERS ? -11 : 0), entry.hanzi, {
        color: "#ffffff",
        fontFamily: "Arial",
        fontSize: "34px",
        fontStyle: "bold"
      })
      .setOrigin(0.5);

    let hintText: Phaser.GameObjects.Text | undefined;

    if (encounteredCount <= HINT_VISIBLE_FOR_ENCOUNTERS) {
      hintText = this.add
        .text(x, y + 19, entry.pinyin, {
          color: "#d9ecff",
          fontFamily: "Arial",
          fontSize: "16px"
        })
        .setOrigin(0.5);
    }

    return {
      id,
      entry,
      circle,
      hanziText,
      hintText,
      encounteredCount
    };
  }

  private selectEnemy(id: number): void {
    if (this.gameFinished) {
      return;
    }

    const enemy = this.enemies.get(id);
    if (!enemy) {
      return;
    }

    if (this.selectedEnemyId !== null && this.enemies.has(this.selectedEnemyId)) {
      const previous = this.enemies.get(this.selectedEnemyId);
      if (previous) {
        this.clearEnemySelectionVisual(previous);
      }
    }

    this.selectedEnemyId = id;
    enemy.circle.setStrokeStyle(4, 0xf2f7ff, 1);
    this.setEnemyScale(enemy, 1.12);
    this.tweens.add({
      targets: [enemy.circle, enemy.hanziText, enemy.hintText].filter(Boolean),
      alpha: 0.72,
      yoyo: true,
      repeat: -1,
      duration: 380
    });

    const hint = enemy.encounteredCount <= HINT_VISIBLE_FOR_ENCOUNTERS ? enemy.entry.pinyin : undefined;
    this.game.events.emit(GAME_EVENTS.enemySelected, {
      hanzi: enemy.entry.hanzi,
      hint
    });
  }

  private stepEnemiesTowardCenter(): void {
    if (this.gameFinished) {
      return;
    }
    if (this.selectedEnemyId !== null) {
      return;
    }

    for (const enemy of this.enemies.values()) {
      const pos = new Phaser.Math.Vector2(enemy.circle.x, enemy.circle.y);
      const toCenter = CENTER_POINT.clone().subtract(pos);
      const distance = toCenter.length();

      if (distance <= CENTER_RADIUS + ENEMY_RADIUS * 0.45) {
        this.endGame();
        return;
      }

      const step = Math.min(STEP_PER_TICK, distance);
      const move = toCenter.normalize().scale(step);
      this.setEnemyPosition(enemy, pos.x + move.x, pos.y + move.y);
    }
  }

  private randomSpawnPoint(): Phaser.Math.Vector2 {
    const margin = ENEMY_RADIUS + 8;
    const side = Phaser.Math.Between(0, 3);

    switch (side) {
      case 0:
        return new Phaser.Math.Vector2(Phaser.Math.Between(margin, GAME_WIDTH - margin), margin);
      case 1:
        return new Phaser.Math.Vector2(GAME_WIDTH - margin, Phaser.Math.Between(margin, GAME_HEIGHT - margin));
      case 2:
        return new Phaser.Math.Vector2(Phaser.Math.Between(margin, GAME_WIDTH - margin), GAME_HEIGHT - margin);
      default:
        return new Phaser.Math.Vector2(margin, Phaser.Math.Between(margin, GAME_HEIGHT - margin));
    }
  }

  private endGame(): void {
    if (this.gameFinished) {
      return;
    }
    this.gameFinished = true;
    this.cleanupScene();
    this.scene.start("GameOverScene", {
      score: this.score
    });
    this.game.events.emit(GAME_EVENTS.gameEnded, { score: this.score });
  }

  private emitStats(): void {
    this.game.events.emit(GAME_EVENTS.statsUpdated, {
      score: this.score,
      enemyCount: this.enemies.size
    });
  }

  private cleanupScene(): void {
    this.spawnTimer?.remove(false);
    this.tickTimer?.remove(false);
    this.input.off("pointerdown", this.handlePointerDown, this);
    this.spawnTimer = undefined;
    this.tickTimer = undefined;
    this.selectedEnemyId = null;
    this.game.events.emit(GAME_EVENTS.enemyCleared);
    this.tweens.killAll();
    for (const enemy of this.enemies.values()) {
      enemy.circle.destroy();
      enemy.hanziText.destroy();
      enemy.hintText?.destroy();
    }
    this.enemies.clear();
  }

  private normalizePinyin(raw: string): string {
    return raw
      .toLowerCase()
      .trim()
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/\s+/g, "");
  }

  private setEnemyPosition(enemy: EnemyState, x: number, y: number): void {
    enemy.circle.setPosition(x, y);
    enemy.hanziText.setPosition(x, y + (enemy.hintText ? -11 : 0));
    enemy.hintText?.setPosition(x, y + 19);
  }

  private setEnemyScale(enemy: EnemyState, scale: number): void {
    enemy.circle.setScale(scale);
    enemy.hanziText.setScale(scale);
    enemy.hintText?.setScale(scale);
  }

  private clearEnemySelectionVisual(enemy: EnemyState): void {
    this.tweens.killTweensOf([enemy.circle, enemy.hanziText, enemy.hintText].filter(Boolean));
    enemy.circle.setStrokeStyle(3, 0x8db4ff, 1);
    this.setEnemyScale(enemy, 1);
    enemy.circle.setAlpha(1);
    enemy.hanziText.setAlpha(1);
    enemy.hintText?.setAlpha(1);
  }

  private destroyEnemy(enemyId: number): void {
    const enemy = this.enemies.get(enemyId);
    if (!enemy) {
      return;
    }
    this.tweens.killTweensOf([enemy.circle, enemy.hanziText, enemy.hintText].filter(Boolean));
    enemy.circle.destroy();
    enemy.hanziText.destroy();
    enemy.hintText?.destroy();
    this.enemies.delete(enemyId);
    if (this.selectedEnemyId === enemyId) {
      this.selectedEnemyId = null;
    }
  }

  private handlePointerDown(pointer: Phaser.Input.Pointer): void {
    if (this.gameFinished) {
      return;
    }
    const cameraPoint = pointer.positionToCamera(this.cameras.main) as Phaser.Math.Vector2 | null;
    const x = cameraPoint?.x ?? pointer.x;
    const y = cameraPoint?.y ?? pointer.y;

    const hitEnemy =
      this.findEnemyAtPoint(x, y, ENEMY_RADIUS * 1.8) ??
      this.findEnemyAtPoint(pointer.worldX, pointer.worldY, ENEMY_RADIUS * 1.8) ??
      this.findNearestEnemyWithin(x, y, ENEMY_RADIUS * 2.7);
    if (hitEnemy) {
      this.selectEnemy(hitEnemy.id);
    }
  }

  private findEnemyAtPoint(x: number, y: number, hitRadius: number): EnemyState | null {
    let closest: EnemyState | null = null;
    let minDistance = Number.POSITIVE_INFINITY;

    for (const enemy of this.enemies.values()) {
      const distance = Phaser.Math.Distance.Between(x, y, enemy.circle.x, enemy.circle.y);
      if (distance <= hitRadius && distance < minDistance) {
        closest = enemy;
        minDistance = distance;
      }
    }

    return closest;
  }

  private findNearestEnemyWithin(x: number, y: number, maxDistance: number): EnemyState | null {
    let closest: EnemyState | null = null;
    let minDistance = Number.POSITIVE_INFINITY;

    for (const enemy of this.enemies.values()) {
      const distance = Phaser.Math.Distance.Between(x, y, enemy.circle.x, enemy.circle.y);
      if (distance < minDistance) {
        minDistance = distance;
        closest = enemy;
      }
    }

    if (closest && minDistance <= maxDistance) {
      return closest;
    }
    return null;
  }
}

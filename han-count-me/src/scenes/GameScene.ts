import Phaser from 'phaser';
import { getCatalog, type GameCatalog } from '../data/catalog';
import { sheetTextureKey } from '../game/assets';
import { showNounCard, type NounCardOverlay } from '../game/nounCard';
import { choicesForTarget, damageFor, hintChargesAfterUse, hintChargesAtWaveStart, maxHpFor, nextCombo, rulesForWave, scoreForHit, scoreForKill, type WaveRules } from '../game/rules';
import { loadProgress, recordRun, type PlayerProgress } from '../game/storage';
import { addButton, CINNABAR, GAME_HEIGHT, GAME_WIDTH, GOLD, INK, JADE, JADE_DARK, PAPER, PAPER_LIGHT, SOFT_INK } from '../game/theme';
import type { Classifier, Noun } from '../types';

const GATE_X = 118;
const LANES = [220, 360, 500];
const HINT_TIME_SCALE = 1 / 3;
const WEAPON_START_X = 91;
const WEAPON_GAP = 183;
const WEAPON_WIDTH = 158;

class EnemyView extends Phaser.GameObjects.Container {
  readonly noun: Noun;
  hp: number;
  readonly maxHp: number;
  readonly speed: number;
  private readonly hpFill: Phaser.GameObjects.Rectangle;
  private readonly ring: Phaser.GameObjects.Ellipse;

  constructor(scene: Phaser.Scene, noun: Noun, laneY: number, speed: number) {
    super(scene, GAME_WIDTH + 110, laneY);
    this.noun = noun;
    this.maxHp = maxHpFor(noun);
    this.hp = this.maxHp;
    this.speed = speed;

    this.ring = scene.add.ellipse(0, 10, 164, 150).setStrokeStyle(4, CINNABAR, 0).setFillStyle(0xffffff, 0);
    const shadow = scene.add.ellipse(0, 62, 124, 22, INK, 0.16);
    const index = Number(noun.noun_id.slice(1)) - 1;
    const image = scene.add.image(0, -3, sheetTextureKey(Math.floor(index / 16)), noun.noun_id).setDisplaySize(145, 145);
    const labelBg = scene.add.rectangle(0, 67, 168, 47, PAPER_LIGHT, 0.94).setStrokeStyle(1, INK, 0.2);
    const hanzi = scene.add.text(0, 57, noun.noun_zh, {
      fontFamily: '"Noto Serif SC", serif', fontSize: '19px', color: '#252722', fontStyle: 'bold',
    }).setOrigin(0.5);
    const russian = scene.add.text(0, 77, noun.noun_ru, {
      fontFamily: 'system-ui, sans-serif', fontSize: '10px', color: '#605a50',
    }).setOrigin(0.5);
    const hpTrack = scene.add.rectangle(0, -83, 142, 10, INK, 0.22);
    this.hpFill = scene.add.rectangle(-71, -83, 142, 10, CINNABAR, 0.95).setOrigin(0, 0.5);
    this.add([this.ring, shadow, image, labelBg, hanzi, russian, hpTrack, this.hpFill]);
    this.setSize(174, 176).setInteractive({ useHandCursor: true });
    scene.add.existing(this);
  }

  setSelected(selected: boolean): void {
    this.ring.setStrokeStyle(4, CINNABAR, selected ? 0.9 : 0);
  }

  applyDamage(amount: number): void {
    this.hp = Math.max(0, this.hp - amount);
    this.hpFill.width = 142 * (this.hp / this.maxHp);
  }
}

export class GameScene extends Phaser.Scene {
  private catalog!: GameCatalog;
  private progress!: PlayerProgress;
  private enemies: EnemyView[] = [];
  private target?: EnemyView;
  private choices: Classifier[] = [];
  private weaponContainer?: Phaser.GameObjects.Container;
  private scoreText!: Phaser.GameObjects.Text;
  private waveText!: Phaser.GameObjects.Text;
  private comboText!: Phaser.GameObjects.Text;
  private gateText!: Phaser.GameObjects.Text;
  private targetText!: Phaser.GameObjects.Text;
  private feedbackText!: Phaser.GameObjects.Text;
  private score = 0;
  private combo = 0;
  private wave = 0;
  private gateHp = 3;
  private remainingSpawns = 0;
  private pendingProjectiles = 0;
  private waveAdvanceScheduled = false;
  private runEnded = false;
  private discovered = new Set<string>();
  private fireLockedUntil = 0;
  private hintCharges = 0;
  private gameplayTimeScale = 1;
  private hintCard?: NounCardOverlay;
  private hotkeyHandler?: (event: KeyboardEvent) => void;

  constructor() { super('GameScene'); }

  create(): void {
    this.resetRunState();
    this.applyGameplayTimeScale(1);
    this.catalog = getCatalog();
    this.progress = loadProgress();
    this.drawArena();
    this.createHud();
    this.createWeaponTray();

    addButton(this, 1206, 43, 112, 46, 'Ⅱ Пауза', () => this.pauseGame(), { fontSize: 15, fill: SOFT_INK });
    this.hotkeyHandler = (event: KeyboardEvent) => {
      if (event.key === 'Escape' || event.key.toLowerCase() === 'p') this.pauseGame();
      if (event.key.toLowerCase() === 'h') this.useHint();
      const index = Number(event.key) - 1;
      if (index >= 0 && index < this.choices.length) this.fire(this.choices[index]);
    };
    this.input.keyboard?.on('keydown', this.hotkeyHandler);
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      if (this.hotkeyHandler) this.input.keyboard?.off('keydown', this.hotkeyHandler);
      this.applyGameplayTimeScale(1);
      this.hintCard = undefined;
      if (this.wave > 0 && !this.runEnded) {
        this.progress = recordRun(this.progress, this.score, this.wave, this.discovered);
      }
    });
    this.startNextWave();
  }

  private resetRunState(): void {
    this.enemies = [];
    this.target = undefined;
    this.choices = [];
    this.weaponContainer = undefined;
    this.score = 0;
    this.combo = 0;
    this.wave = 0;
    this.gateHp = 3;
    this.remainingSpawns = 0;
    this.pendingProjectiles = 0;
    this.waveAdvanceScheduled = false;
    this.runEnded = false;
    this.discovered = new Set<string>();
    this.fireLockedUntil = 0;
    this.hintCharges = 0;
    this.gameplayTimeScale = 1;
    this.hintCard = undefined;
    this.hotkeyHandler = undefined;
  }

  update(_time: number, delta: number): void {
    if (this.runEnded) return;
    // Mobile browsers may resume a throttled tab with a multi-second delta.
    // Clamping prevents an entire wave from teleporting through the gate.
    const frameDelta = Math.min(delta, 50) * this.gameplayTimeScale;
    for (const enemy of [...this.enemies]) {
      enemy.x -= enemy.speed * (frameDelta / 1000);
      if (enemy.x <= GATE_X) this.enemyBreached(enemy);
    }
    if (!this.target || !this.enemies.includes(this.target)) this.selectTarget(this.closestEnemy());
    if (!this.waveAdvanceScheduled && this.remainingSpawns === 0 && this.enemies.length === 0 && this.pendingProjectiles === 0) {
      this.waveAdvanceScheduled = true;
      this.time.delayedCall(1300, () => {
        if (!this.runEnded) { this.waveAdvanceScheduled = false; this.startNextWave(); }
      });
    }
  }

  private drawArena(): void {
    const g = this.add.graphics();
    g.fillGradientStyle(PAPER_LIGHT, PAPER_LIGHT, PAPER, 0xd1c3a2, 1);
    g.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
    g.fillStyle(0x617367, 0.16);
    g.beginPath();
    g.moveTo(0, 300); g.lineTo(180, 145); g.lineTo(320, 280); g.lineTo(500, 115); g.lineTo(690, 310); g.lineTo(900, 135); g.lineTo(1100, 300); g.lineTo(1280, 160); g.lineTo(1280, 550); g.lineTo(0, 550); g.closePath(); g.fillPath();
    g.fillStyle(0xe9dfc8, 0.78);
    LANES.forEach((y) => g.fillRoundedRect(96, y - 62, 1170, 124, 25));
    g.lineStyle(1, INK, 0.16);
    LANES.forEach((y) => { g.beginPath(); g.moveTo(105, y + 62); g.lineTo(1270, y + 62); g.strokePath(); });
    g.fillStyle(JADE_DARK, 0.96); g.fillRect(0, 0, GAME_WIDTH, 88);
    g.fillStyle(INK, 0.96); g.fillRect(0, 570, GAME_WIDTH, 150);

    g.fillStyle(0x453b30, 1); g.fillRect(75, 117, 54, 438);
    g.fillStyle(CINNABAR, 1); g.fillRect(66, 115, 72, 18); g.fillRect(66, 538, 72, 18);
    g.lineStyle(4, GOLD, 0.75); g.strokeRect(82, 137, 40, 396);
    this.add.text(102, 336, '守\n门', {
      fontFamily: '"Noto Serif SC", serif', fontSize: '28px', color: '#d8bd7e', align: 'center', lineSpacing: 12,
    }).setOrigin(0.5);
  }

  private createHud(): void {
    const hudStyle: Phaser.Types.GameObjects.Text.TextStyle = { fontFamily: 'system-ui, sans-serif', fontSize: '14px', color: '#cfc3a7' };
    this.add.text(28, 15, 'ОЧКИ', hudStyle);
    this.scoreText = this.add.text(28, 38, '0', { fontFamily: 'Georgia, serif', fontSize: '30px', color: '#f1dfbd', fontStyle: 'bold' });
    this.add.text(230, 15, 'ВОЛНА', hudStyle);
    this.waveText = this.add.text(230, 40, '1', { fontFamily: 'Georgia, serif', fontSize: '26px', color: '#f1dfbd', fontStyle: 'bold' });
    this.add.text(378, 15, 'СЕРИЯ', hudStyle);
    this.comboText = this.add.text(378, 40, '×0', { fontFamily: 'Georgia, serif', fontSize: '26px', color: '#d99d81', fontStyle: 'bold' });
    this.add.text(520, 15, 'ВОРОТА', hudStyle);
    this.gateText = this.add.text(520, 40, '◆ ◆ ◆', { fontFamily: 'serif', fontSize: '21px', color: '#d99d81' });
    this.targetText = this.add.text(708, 22, 'Ждём первую цель…', {
      fontFamily: '"Noto Serif SC", system-ui, sans-serif', fontSize: '18px', color: '#efe2c5',
    });
    this.feedbackText = this.add.text(GAME_WIDTH / 2, 548, '', {
      fontFamily: 'system-ui, sans-serif', fontSize: '15px', color: '#493d32', backgroundColor: '#eadfc4dd', padding: { x: 14, y: 6 },
    }).setOrigin(0.5).setDepth(8).setVisible(false);
  }

  private createWeaponTray(): void {
    this.weaponContainer?.destroy(true);
    const container = this.add.container(0, 0).setDepth(10);
    this.weaponContainer = container;
    const target = this.target;
    if (!target) {
      container.add(this.add.text(500, 645, 'Меры появятся вместе с целью', {
        fontFamily: 'system-ui, sans-serif', fontSize: '17px', color: '#bdb39d',
      }).setOrigin(0.5));
      container.add(addButton(this, WEAPON_START_X + 6 * WEAPON_GAP, 646, WEAPON_WIDTH, 104, '提示', () => this.useHint(), {
        fill: CINNABAR,
        fontSize: 32,
        subtitle: `H · зарядов: ${this.hintCharges}`,
        enabled: false,
      }));
      this.choices = [];
      return;
    }
    const rules = rulesForWave(this.wave, this.progress.highestWave);
    this.choices = choicesForTarget(this.catalog, target.noun.noun_id, rules.tier, Math.random, 6);
    this.choices.forEach((classifier, index) => {
      const damage = damageFor(this.catalog, target.noun.noun_id, classifier.classifier_id);
      const x = WEAPON_START_X + index * WEAPON_GAP;
      const button = addButton(this, x, 646, WEAPON_WIDTH, 104, classifier.hanzi, () => this.fire(classifier), {
        fill: classifier.classifier_id === 'CL001' ? CINNABAR : JADE_DARK,
        hoverFill: classifier.classifier_id === 'CL001' ? 0xae4b41 : JADE,
        fontSize: 36,
        subtitle: `${index + 1} · ${classifier.pinyin}${this.progress.hintsEnabled && damage > 0 ? ` · ${damage} ур.` : ''}`,
      });
      container.add(button);
    });
    container.add(addButton(this, WEAPON_START_X + 6 * WEAPON_GAP, 646, WEAPON_WIDTH, 104, '提示', () => this.useHint(), {
      fill: CINNABAR,
      hoverFill: 0xae4b41,
      fontSize: 32,
      subtitle: this.hintCharges > 0 ? `H · зарядов: ${this.hintCharges}` : 'H · нет зарядов',
      enabled: this.hintCharges > 0 && !this.hintCard,
    }));
  }

  private startNextWave(): void {
    this.wave += 1;
    this.hintCharges = hintChargesAtWaveStart(this.hintCharges);
    const rules = rulesForWave(this.wave, this.progress.highestWave);
    this.waveText.setText(String(this.wave));
    this.remainingSpawns = rules.enemyCount;
    this.createWeaponTray();
    const waveMessage = rules.tier > 1 ? `Открыты меры уровня ${rules.tier}` : 'Соблюдай ритм';
    this.showBanner(`Волна ${this.wave}`, `Подсказок: ${this.hintCharges} · ${waveMessage}`);
    this.time.addEvent({
      delay: rules.spawnDelayMs,
      repeat: rules.enemyCount - 1,
      startAt: rules.spawnDelayMs - 250,
      callback: () => this.spawnEnemy(rules),
    });
  }

  private spawnEnemy(rules: WaveRules): void {
    const pool = this.catalog.seed.nouns.filter((noun) => {
      if (noun.gameplay_eligible !== 'да' || noun.difficulty > rules.tier) return false;
      return this.catalog.seed.pairs.some((pair) => {
        const classifier = this.catalog.classifierById.get(pair.classifier_id);
        return pair.noun_id === noun.noun_id && pair.game_damage > 1 && classifier && classifier.unlock_level <= rules.tier;
      });
    });
    const noun = Phaser.Utils.Array.GetRandom(pool);
    const lane = Phaser.Math.Between(0, LANES.length - 1);
    const speed = rules.baseSpeed + noun.difficulty * 7 + Phaser.Math.Between(-7, 9);
    const enemy = new EnemyView(this, noun, LANES[lane], speed);
    enemy.on('pointerup', () => this.selectTarget(enemy));
    this.enemies.push(enemy);
    this.remainingSpawns -= 1;
    this.discovered.add(noun.noun_id);
    if (!this.target) this.selectTarget(enemy);
  }

  private closestEnemy(): EnemyView | undefined {
    return [...this.enemies].sort((a, b) => a.x - b.x)[0];
  }

  private selectTarget(enemy: EnemyView | undefined): void {
    if (enemy === this.target) return;
    this.target?.setSelected(false);
    this.target = enemy;
    this.target?.setSelected(true);
    if (enemy) {
      this.targetText.setText(`Цель: ${enemy.noun.noun_zh} · ${enemy.noun.noun_pinyin} · ${enemy.noun.noun_ru}`);
    } else {
      this.targetText.setText('Волна очищена — приготовься');
    }
    this.createWeaponTray();
  }

  private fire(classifier: Classifier): void {
    const enemy = this.target;
    if (!enemy || !this.enemies.includes(enemy) || this.time.now < this.fireLockedUntil || this.runEnded || this.hintCard) return;
    this.fireLockedUntil = this.time.now + 160;
    this.pendingProjectiles += 1;
    const projectile = this.add.container(153, enemy.y).setDepth(12);
    const disk = this.add.circle(0, 0, 27, classifier.classifier_id === 'CL001' ? CINNABAR : JADE_DARK, 0.95).setStrokeStyle(2, GOLD, 0.8);
    const glyph = this.add.text(0, -1, classifier.hanzi, {
      fontFamily: '"Noto Serif SC", serif', fontSize: '28px', color: '#f4e7ca', fontStyle: 'bold',
    }).setOrigin(0.5);
    projectile.add([disk, glyph]);
    this.tweens.add({
      targets: projectile,
      x: enemy.x,
      y: enemy.y - 15,
      angle: 360,
      duration: Phaser.Math.Clamp((enemy.x - 130) * 0.5, 260, 620),
      ease: 'Sine.easeIn',
      onComplete: () => {
        projectile.destroy();
        this.pendingProjectiles -= 1;
        if (this.enemies.includes(enemy)) this.resolveHit(enemy, classifier);
      },
    });
  }

  private resolveHit(enemy: EnemyView, classifier: Classifier): void {
    const damage = damageFor(this.catalog, enemy.noun.noun_id, classifier.classifier_id);
    this.combo = nextCombo(this.combo, damage);
    this.comboText.setText(`×${this.combo}`).setColor(damage > 0 ? '#e8ae8b' : '#968f82');
    if (damage > 0) {
      enemy.applyDamage(damage);
      this.score += scoreForHit(damage, this.combo);
      this.scoreText.setText(String(this.score));
      this.cameras.main.shake(55, damage * 0.00045);
      this.playTone(220 + damage * 75, 0.055);
    } else {
      this.playTone(115, 0.11);
    }
    this.floatText(enemy.x, enemy.y - 95, damage > 0 ? `−${damage}` : '无效 · 0', damage > 0 ? '#943a31' : '#55584e');
    const pair = this.catalog.pair(enemy.noun.noun_id, classifier.classifier_id);
    if (this.progress.hintsEnabled) {
      const result = classifier.classifier_id === 'CL001' ? 'запасной 个' : pair ? pair.example : 'такой пары нет в базе';
      this.showFeedback(`${classifier.hanzi} + ${enemy.noun.noun_zh}: ${damage} урон · ${result}`);
    }
    if (enemy.hp <= 0) this.destroyEnemy(enemy);
  }

  private destroyEnemy(enemy: EnemyView): void {
    this.score += scoreForKill(enemy.noun, this.combo);
    this.scoreText.setText(String(this.score));
    this.enemies = this.enemies.filter((item) => item !== enemy);
    if (this.target === enemy) this.target = undefined;
    for (let i = 0; i < 9; i += 1) {
      const fleck = this.add.circle(enemy.x, enemy.y, Phaser.Math.Between(2, 7), i % 3 === 0 ? CINNABAR : INK, 0.7).setDepth(11);
      this.tweens.add({ targets: fleck, x: enemy.x + Phaser.Math.Between(-80, 80), y: enemy.y + Phaser.Math.Between(-80, 80), alpha: 0, duration: 500, onComplete: () => fleck.destroy() });
    }
    enemy.destroy();
    this.selectTarget(this.closestEnemy());
  }

  private enemyBreached(enemy: EnemyView): void {
    this.enemies = this.enemies.filter((item) => item !== enemy);
    if (this.target === enemy) this.target = undefined;
    enemy.destroy();
    this.gateHp -= 1;
    this.combo = 0;
    this.gateText.setText(`${'◆ '.repeat(this.gateHp)}${'◇ '.repeat(3 - this.gateHp)}`.trim()).setColor('#e29278');
    this.comboText.setText('×0');
    this.cameras.main.shake(300, 0.012);
    this.showFeedback('Враг прошёл ворота — серия потеряна');
    this.playTone(82, 0.2);
    if (this.gateHp <= 0) this.endRun();
    else this.selectTarget(this.closestEnemy());
  }

  private pauseGame(): void {
    if (this.runEnded || this.scene.isPaused()) return;
    this.scene.launch('PauseScene', { score: this.score, wave: this.wave });
    this.scene.pause();
  }

  private endRun(): void {
    if (this.runEnded) return;
    this.runEnded = true;
    this.closeHintCard();
    const previousBest = this.progress.bestScore;
    this.progress = recordRun(this.progress, this.score, this.wave, this.discovered);
    this.time.delayedCall(380, () => {
      this.scene.start('GameOverScene', {
        score: this.score,
        wave: this.wave,
        combo: this.combo,
        bestScore: this.progress.bestScore,
        isRecord: this.score > previousBest,
        discovered: this.discovered.size,
      });
    });
  }

  private useHint(): void {
    const enemy = this.target;
    if (!enemy || !this.enemies.includes(enemy) || this.hintCharges <= 0 || this.hintCard || this.runEnded) return;
    this.hintCharges = hintChargesAfterUse(this.hintCharges);
    this.applyGameplayTimeScale(HINT_TIME_SCALE);
    this.hintCard = showNounCard(this, this.catalog, enemy.noun, {
      eyebrow: '提示 · ПОДСКАЗКА · игровое время ×⅓',
      closeLabel: 'Вернуться в бой',
      onClose: () => this.finishHint(),
    });
    this.createWeaponTray();
  }

  private finishHint(): void {
    this.hintCard = undefined;
    this.applyGameplayTimeScale(1);
    if (!this.runEnded) this.createWeaponTray();
  }

  private closeHintCard(): void {
    const card = this.hintCard;
    this.hintCard = undefined;
    if (card) card.close();
    else this.applyGameplayTimeScale(1);
  }

  private applyGameplayTimeScale(scale: number): void {
    this.gameplayTimeScale = scale;
    this.time.timeScale = scale;
    this.tweens.timeScale = scale;
  }

  private showBanner(title: string, subtitle: string): void {
    const group = this.add.container(GAME_WIDTH / 2, 335).setDepth(30).setAlpha(0);
    const bg = this.add.rectangle(0, 0, 430, 112, PAPER_LIGHT, 0.95).setStrokeStyle(3, CINNABAR, 0.7);
    const heading = this.add.text(0, -18, title, { fontFamily: '"Noto Serif SC", serif', fontSize: '30px', color: '#292b26', fontStyle: 'bold' }).setOrigin(0.5);
    const sub = this.add.text(0, 23, subtitle, { fontFamily: 'system-ui, sans-serif', fontSize: '14px', color: '#736a5b' }).setOrigin(0.5);
    group.add([bg, heading, sub]);
    this.tweens.add({ targets: group, alpha: 1, y: 350, duration: 250, yoyo: true, hold: 800, onComplete: () => group.destroy() });
  }

  private showFeedback(message: string): void {
    this.feedbackText.setText(message).setVisible(true).setAlpha(1);
    this.tweens.killTweensOf(this.feedbackText);
    this.tweens.add({ targets: this.feedbackText, alpha: 0, delay: 1050, duration: 350, onComplete: () => this.feedbackText.setVisible(false) });
  }

  private floatText(x: number, y: number, text: string, color: string): void {
    const object = this.add.text(x, y, text, {
      fontFamily: 'system-ui, sans-serif', fontSize: '22px', color, fontStyle: 'bold', stroke: '#f4ead2', strokeThickness: 5,
    }).setOrigin(0.5).setDepth(20);
    this.tweens.add({ targets: object, y: y - 45, alpha: 0, duration: 620, onComplete: () => object.destroy() });
  }

  private playTone(frequency: number, duration: number): void {
    if (!this.progress.soundEnabled || typeof AudioContext === 'undefined') return;
    try {
      const context = new AudioContext();
      const oscillator = context.createOscillator();
      const gain = context.createGain();
      oscillator.type = 'sine'; oscillator.frequency.value = frequency;
      gain.gain.setValueAtTime(0.035, context.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, context.currentTime + duration);
      oscillator.connect(gain); gain.connect(context.destination);
      oscillator.start(); oscillator.stop(context.currentTime + duration);
      oscillator.addEventListener('ended', () => { void context.close(); });
    } catch { /* Браузер может запретить звук до первого явного жеста. */ }
  }
}

import Phaser from "phaser";
import {
  loadSettings,
  quizModeLabel,
  saveSettings,
  type QuizMode,
} from "../settings";
import { THEME } from "../theme";
import { SCENE_MENU, SCENE_SETTINGS } from "./sceneKeys";

export default class SettingsScene extends Phaser.Scene {
  private title?: Phaser.GameObjects.Text;
  private modeLabel?: Phaser.GameObjects.Text;
  private btnReading?: Phaser.GameObjects.Text;
  private btnMeaning?: Phaser.GameObjects.Text;
  private btnBack?: Phaser.GameObjects.Text;
  private quizMode: QuizMode = "reading";

  constructor() {
    super(SCENE_SETTINGS);
  }

  create(): void {
    this.quizMode = loadSettings().quizMode;
    this.layout();

    this.scale.on("resize", this.layout, this);
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.scale.off("resize", this.layout, this);
    });
  }

  private setMode(mode: QuizMode): void {
    this.quizMode = mode;
    saveSettings({ quizMode: mode });
    this.refreshModeUi();
  }

  private refreshModeUi(): void {
    if (this.modeLabel) {
      this.modeLabel.setText(`Ask for: ${quizModeLabel(this.quizMode)}`);
    }
    this.styleModeButton(this.btnReading, this.quizMode === "reading");
    this.styleModeButton(this.btnMeaning, this.quizMode === "meaning");
  }

  private styleModeButton(btn: Phaser.GameObjects.Text | undefined, selected: boolean): void {
    if (!btn) return;
    btn.setStyle({
      backgroundColor: selected ? THEME.accent : THEME.panel,
      color: selected ? THEME.panel : THEME.text,
    });
  }

  private layout = (): void => {
    const w = this.scale.width;
    const h = this.scale.height;
    const cx = w / 2;
    const wrap = Math.min(w - 32, 360);

    if (!this.title) {
      this.title = this.add
        .text(0, 0, "Settings", {
          fontFamily: "system-ui, sans-serif",
          fontSize: "28px",
          color: THEME.text,
          fontStyle: "bold",
        })
        .setOrigin(0.5);
    }
    this.title.setPosition(cx, h * 0.16);

    if (!this.modeLabel) {
      this.modeLabel = this.add
        .text(0, 0, "", {
          fontFamily: "system-ui, sans-serif",
          fontSize: "15px",
          color: THEME.textMuted,
          wordWrap: { width: wrap },
          align: "center",
        })
        .setOrigin(0.5);
    }
    this.modeLabel.setPosition(cx, h * 0.28);
    this.modeLabel.setStyle({ wordWrap: { width: wrap } });

    const mkToggle = (
      existing: Phaser.GameObjects.Text | undefined,
      label: string,
      mode: QuizMode,
    ): Phaser.GameObjects.Text => {
      if (existing) return existing;
      const t = this.add
        .text(0, 0, label, {
          fontFamily: "system-ui, sans-serif",
          fontSize: "20px",
          color: THEME.text,
          backgroundColor: THEME.panel,
          padding: { x: 24, y: 12 },
        })
        .setOrigin(0.5)
        .setInteractive({ useHandCursor: true });
      t.on("pointerup", () => this.setMode(mode));
      return t;
    };

    this.btnReading = mkToggle(this.btnReading, "Reading (pinyin)", "reading");
    this.btnMeaning = mkToggle(this.btnMeaning, "Meaning (keyword)", "meaning");
    this.btnReading.setPosition(cx, h * 0.42);
    this.btnMeaning.setPosition(cx, h * 0.42 + 64);

    if (!this.btnBack) {
      this.btnBack = this.add
        .text(0, 0, "Back", {
          fontFamily: "system-ui, sans-serif",
          fontSize: "20px",
          color: THEME.panel,
          backgroundColor: THEME.accent,
          padding: { x: 28, y: 12 },
        })
        .setOrigin(0.5)
        .setInteractive({ useHandCursor: true });
      this.btnBack.on("pointerover", () =>
        this.btnBack?.setStyle({ backgroundColor: THEME.accentHover }),
      );
      this.btnBack.on("pointerout", () =>
        this.btnBack?.setStyle({ backgroundColor: THEME.accent }),
      );
      this.btnBack.on("pointerup", () => {
        this.scene.start(SCENE_MENU);
      });
    }
    this.btnBack.setPosition(cx, h * 0.42 + 148);

    this.refreshModeUi();
  };
}

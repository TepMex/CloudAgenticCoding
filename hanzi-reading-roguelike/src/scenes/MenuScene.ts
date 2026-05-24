import Phaser from "phaser";
import { SCENE_MENU, SCENE_GAME } from "./sceneKeys";

export default class MenuScene extends Phaser.Scene {
  private title?: Phaser.GameObjects.Text;
  private subtitle?: Phaser.GameObjects.Text;
  private hint?: Phaser.GameObjects.Text;
  private btnNew?: Phaser.GameObjects.Text;
  private btnExit?: Phaser.GameObjects.Text;

  constructor() {
    super(SCENE_MENU);
  }

  create(): void {
    this.layout();

    this.scale.on("resize", this.layout, this);
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.scale.off("resize", this.layout, this);
    });
  }

  private layout = (): void => {
    const w = this.scale.width;
    const h = this.scale.height;
    const cx = w / 2;

    if (!this.title) {
      this.title = this.add
        .text(0, 0, "Hanzi Roguelike", {
          fontFamily: "system-ui, sans-serif",
          fontSize: "28px",
          color: "#ffffff",
          fontStyle: "bold",
        })
        .setOrigin(0.5);
    }
    this.title.setPosition(cx, h * 0.18);

    if (!this.subtitle) {
      this.subtitle = this.add
        .text(0, 0, "Circles drift to the center — type pinyin to clear them.", {
          fontFamily: "system-ui, sans-serif",
          fontSize: "14px",
          color: "#aabbcc",
          wordWrap: { width: Math.min(w - 32, 360) },
          align: "center",
        })
        .setOrigin(0.5);
    }
    this.subtitle.setPosition(cx, h * 0.28);
    this.subtitle.setStyle({ wordWrap: { width: Math.min(w - 32, 360) } });

    if (!this.btnNew) {
      this.btnNew = this.add
        .text(0, 0, "New Game", {
          fontFamily: "system-ui, sans-serif",
          fontSize: "22px",
          color: "#1a1a2e",
          backgroundColor: "#e94560",
          padding: { x: 28, y: 14 },
        })
        .setOrigin(0.5)
        .setInteractive({ useHandCursor: true });

      this.btnNew.on("pointerover", () => this.btnNew?.setStyle({ backgroundColor: "#ff6b81" }));
      this.btnNew.on("pointerout", () => this.btnNew?.setStyle({ backgroundColor: "#e94560" }));
      this.btnNew.on("pointerup", () => {
        this.scene.start(SCENE_GAME);
      });
    }
    this.btnNew.setPosition(cx, h * 0.45);

    if (!this.btnExit) {
      this.btnExit = this.add
        .text(0, 0, "Exit", {
          fontFamily: "system-ui, sans-serif",
          fontSize: "22px",
          color: "#1a1a2e",
          backgroundColor: "#e94560",
          padding: { x: 28, y: 14 },
        })
        .setOrigin(0.5)
        .setInteractive({ useHandCursor: true });

      this.btnExit.on("pointerover", () => this.btnExit?.setStyle({ backgroundColor: "#ff6b81" }));
      this.btnExit.on("pointerout", () => this.btnExit?.setStyle({ backgroundColor: "#e94560" }));
      this.btnExit.on("pointerup", () => {
        window.close();
        if (!this.hint) {
          this.hint = this.add
            .text(0, 0, "You can close this tab.", {
              fontFamily: "system-ui, sans-serif",
              fontSize: "14px",
              color: "#8899aa",
            })
            .setOrigin(0.5);
        }
        this.hint.setPosition(cx, h * 0.45 + 80);
      });
    }
    this.btnExit.setPosition(cx, h * 0.45 + 64);

    if (this.hint) {
      this.hint.setPosition(cx, h * 0.45 + 80);
    }
  };
}

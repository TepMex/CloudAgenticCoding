import Phaser from "phaser";

const BG_COLOR = 0x0d1324;
const GAME_WIDTH = 390;
const GAME_HEIGHT = 844;

export class MainMenuScene extends Phaser.Scene {
  constructor() {
    super("MainMenuScene");
  }

  create(): void {
    this.cameras.main.setBackgroundColor(BG_COLOR);

    this.add
      .text(GAME_WIDTH / 2, 130, "Hanzi Reading Roguelike", {
        color: "#f4f1de",
        fontFamily: "Arial",
        fontSize: "34px",
        align: "center",
        wordWrap: { width: GAME_WIDTH - 40 }
      })
      .setOrigin(0.5);

    this.add
      .text(GAME_WIDTH / 2, 210, "Tap enemies -> type pinyin -> survive", {
        color: "#c9d6ea",
        fontFamily: "Arial",
        fontSize: "18px",
        align: "center"
      })
      .setOrigin(0.5);

    this.createButton(GAME_HEIGHT * 0.38, "New Game", () => {
      this.scene.start("GameScene");
    });

    this.createButton(GAME_HEIGHT * 0.48, "How To Play", () => {
      this.showHowToPlay();
    });

    this.createButton(GAME_HEIGHT * 0.58, "Exit", () => {
      window.close();
      this.showMessage("Use browser back/home to exit.");
    });
  }

  private createButton(y: number, label: string, onClick: () => void): void {
    const button = this.add
      .rectangle(GAME_WIDTH / 2, y, 230, 64, 0x1d2d50, 1)
      .setStrokeStyle(2, 0x89a6fb)
      .setInteractive({ useHandCursor: true });

    const text = this.add
      .text(button.x, button.y, label, {
        color: "#fefefe",
        fontFamily: "Arial",
        fontSize: "28px"
      })
      .setOrigin(0.5);

    button.on("pointerdown", onClick);
    button.on("pointerover", () => button.setFillStyle(0x273d67));
    button.on("pointerout", () => button.setFillStyle(0x1d2d50));

    text.on("pointerdown", onClick);
    text.setInteractive({ useHandCursor: true });
  }

  private showHowToPlay(): void {
    this.showMessage("Tap a circle, type its pinyin, then submit before it reaches the center.");
  }

  private showMessage(message: string): void {
    const box = this.add.rectangle(GAME_WIDTH / 2, GAME_HEIGHT - 110, GAME_WIDTH - 30, 90, 0x000000, 0.78);
    const text = this.add
      .text(box.x, box.y, message, {
        color: "#f7f7f7",
        fontFamily: "Arial",
        fontSize: "18px",
        align: "center",
        wordWrap: { width: box.width - 20 }
      })
      .setOrigin(0.5);

    this.time.delayedCall(2200, () => {
      box.destroy();
      text.destroy();
    });
  }
}

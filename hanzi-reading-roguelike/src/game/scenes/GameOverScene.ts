import Phaser from "phaser";

const GAME_WIDTH = 390;

export class GameOverScene extends Phaser.Scene {
  constructor() {
    super("GameOverScene");
  }

  create(data: { score?: number }): void {
    this.cameras.main.setBackgroundColor(0x200b14);

    this.add
      .text(GAME_WIDTH / 2, 220, "Game Over", {
        color: "#ffe0e7",
        fontFamily: "Arial",
        fontSize: "52px",
        fontStyle: "bold"
      })
      .setOrigin(0.5);

    this.add
      .text(GAME_WIDTH / 2, 320, `Score: ${data.score ?? 0}`, {
        color: "#ffffff",
        fontFamily: "Arial",
        fontSize: "34px"
      })
      .setOrigin(0.5);

    this.createButton(450, "Retry", () => this.scene.start("GameScene"));
    this.createButton(540, "Main Menu", () => this.scene.start("MainMenuScene"));
  }

  private createButton(y: number, label: string, onClick: () => void): void {
    const button = this.add
      .rectangle(GAME_WIDTH / 2, y, 240, 64, 0x461b2d, 1)
      .setStrokeStyle(2, 0xfd9ab6)
      .setInteractive({ useHandCursor: true });

    const text = this.add
      .text(button.x, button.y, label, {
        color: "#fff",
        fontFamily: "Arial",
        fontSize: "28px"
      })
      .setOrigin(0.5);

    button.on("pointerdown", onClick);
    text.setInteractive({ useHandCursor: true }).on("pointerdown", onClick);
  }
}

import Phaser from "phaser";
import MenuScene from "./scenes/MenuScene";
import GameScene from "./scenes/GameScene";

import "./style.css";

const config: Phaser.Types.Core.GameConfig = {
  type: Phaser.AUTO,
  parent: "game-container",
  backgroundColor: "#1a1a2e",
  scale: {
    mode: Phaser.Scale.RESIZE,
    autoCenter: Phaser.Scale.CENTER_BOTH,
    width: Math.max(window.innerWidth, 320),
    height: Math.max(window.innerHeight, 480),
  },
  scene: [MenuScene, GameScene],
  input: {
    activePointers: 2,
  },
};

new Phaser.Game(config);

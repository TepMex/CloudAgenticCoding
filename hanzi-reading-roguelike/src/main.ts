import Phaser from "phaser";
import MenuScene from "./scenes/MenuScene";
import GameScene from "./scenes/GameScene";
import SettingsScene from "./scenes/SettingsScene";
import { THEME } from "./theme";

import "./style.css";

const config: Phaser.Types.Core.GameConfig = {
  type: Phaser.AUTO,
  parent: "game-container",
  backgroundColor: THEME.background,
  scale: {
    mode: Phaser.Scale.RESIZE,
    autoCenter: Phaser.Scale.CENTER_BOTH,
    width: Math.max(window.innerWidth, 320),
    height: Math.max(window.innerHeight, 480),
  },
  scene: [MenuScene, SettingsScene, GameScene],
  input: {
    activePointers: 2,
  },
};

new Phaser.Game(config);

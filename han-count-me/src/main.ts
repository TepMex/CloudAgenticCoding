import Phaser from 'phaser';
import './style.css';
import { GAME_HEIGHT, GAME_WIDTH } from './game/theme';
import { BootScene } from './scenes/BootScene';
import { DictionaryScene } from './scenes/DictionaryScene';
import { GameOverScene } from './scenes/GameOverScene';
import { GameScene } from './scenes/GameScene';
import { MenuScene } from './scenes/MenuScene';
import { PauseScene } from './scenes/PauseScene';
import { SettingsScene } from './scenes/SettingsScene';
import { TutorialScene } from './scenes/TutorialScene';

const game = new Phaser.Game({
  type: Phaser.AUTO,
  parent: 'app',
  width: GAME_WIDTH,
  height: GAME_HEIGHT,
  backgroundColor: '#e8ddc2',
  scene: [BootScene, MenuScene, TutorialScene, GameScene, PauseScene, GameOverScene, DictionaryScene, SettingsScene],
  scale: {
    mode: Phaser.Scale.FIT,
    autoCenter: Phaser.Scale.CENTER_BOTH,
    width: GAME_WIDTH,
    height: GAME_HEIGHT,
  },
  input: {
    activePointers: 4,
  },
  render: {
    antialias: true,
    pixelArt: false,
    roundPixels: false,
  },
  banner: false,
});

window.addEventListener('resize', () => game.scale.refresh());
const app = document.querySelector('#app');
if (app && typeof ResizeObserver !== 'undefined') {
  new ResizeObserver(() => game.scale.refresh()).observe(app);
}

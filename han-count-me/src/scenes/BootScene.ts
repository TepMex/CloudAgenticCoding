import Phaser from 'phaser';
import { initializeCatalog } from '../data/catalog';
import { buildSpriteFrameMappings, frameRectangle } from '../data/spriteMapping';
import { SEED_URL, SPRITE_SHEET_URLS, sheetTextureKey } from '../game/assets';
import { CINNABAR, GAME_HEIGHT, GAME_WIDTH, INK, PAPER, PAPER_LIGHT } from '../game/theme';

export class BootScene extends Phaser.Scene {
  constructor() { super('BootScene'); }

  preload(): void {
    this.cameras.main.setBackgroundColor(PAPER);
    const title = this.add.text(GAME_WIDTH / 2, 270, '量词守门人', {
      fontFamily: '"Noto Serif SC", "Songti SC", serif', fontSize: '54px', color: '#252722', fontStyle: 'bold',
    }).setOrigin(0.5);
    this.add.text(GAME_WIDTH / 2, 330, 'HAN COUNT ME', {
      fontFamily: 'Georgia, serif', fontSize: '16px', color: '#7c392f', letterSpacing: 7,
    }).setOrigin(0.5);
    const track = this.add.rectangle(GAME_WIDTH / 2, 420, 430, 8, INK, 0.15);
    const bar = this.add.rectangle(track.x - 215, 420, 1, 8, CINNABAR, 1).setOrigin(0, 0.5);
    const status = this.add.text(GAME_WIDTH / 2, 450, 'Открываем свитки…', {
      fontFamily: 'system-ui, sans-serif', fontSize: '14px', color: '#625e53',
    }).setOrigin(0.5);
    this.load.on('progress', (value: number) => { bar.width = Math.max(1, 430 * value); });
    this.load.on('loaderror', (file: Phaser.Loader.File) => { status.setText(`Не удалось загрузить: ${file.key}`); status.setColor('#943a31'); });
    title.setShadow(0, 2, '#f4ead2', 2);

    this.load.json('game-seed', SEED_URL);
    SPRITE_SHEET_URLS.forEach((url, index) => this.load.image(sheetTextureKey(index), url));
  }

  create(): void {
    try {
      const catalog = initializeCatalog(this.cache.json.get('game-seed'));
      const mappings = buildSpriteFrameMappings(catalog.seed.nouns);
      mappings.forEach((mapping) => {
        const texture = this.textures.get(sheetTextureKey(mapping.sheetIndex));
        const source = texture.getSourceImage() as HTMLImageElement;
        const frame = frameRectangle(source.width, source.height, mapping.row, mapping.column);
        texture.add(mapping.nounId, 0, frame.x, frame.y, frame.width, frame.height);
      });
      this.scene.start('MenuScene');
    } catch (error) {
      this.add.rectangle(GAME_WIDTH / 2, GAME_HEIGHT / 2, 1000, 220, PAPER_LIGHT, 0.98).setStrokeStyle(3, CINNABAR);
      this.add.text(GAME_WIDTH / 2, GAME_HEIGHT / 2, error instanceof Error ? error.message : String(error), {
        fontFamily: 'monospace', fontSize: '18px', color: '#742c28', align: 'left', wordWrap: { width: 930 },
      }).setOrigin(0.5);
    }
  }
}

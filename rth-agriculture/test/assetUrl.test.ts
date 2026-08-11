import { describe, expect, test, afterEach } from 'bun:test'
import {
  resolveAssetUrl,
  resolveRemoteAssetUrl,
  isHeavyArtAsset,
  usesRemoteHeavyArt,
  getRemoteAssetBase,
  resetHeavyArtObjectUrlsForTests,
} from '../src/assetUrl'

afterEach(() => {
  resetHeavyArtObjectUrlsForTests()
})

describe('resolveAssetUrl', () => {
  test('relative Vite base resolves against the document, not the CSS bundle folder', () => {
    expect(
      resolveAssetUrl('./', 'assets/cleaning-court-clear.png', 'file:///android_asset/www/index.html'),
    ).toBe('file:///android_asset/www/assets/cleaning-court-clear.png')
  })

  test('does not double the assets segment the way CSS var() url resolution would', () => {
    const href = resolveAssetUrl(
      './',
      'assets/cleaning-court-clear.png',
      'http://127.0.0.1:8765/index.html',
    )
    expect(href).toBe('http://127.0.0.1:8765/assets/cleaning-court-clear.png')
    expect(href.includes('assets/assets/')).toBe(false)
  })

  test('absolute Pages base stays rooted at the site path', () => {
    expect(
      resolveAssetUrl(
        '/CloudAgenticCoding/rth-agriculture/',
        'assets/garden-map.png',
        'https://example.com/CloudAgenticCoding/rth-agriculture/index.html',
      ),
    ).toBe('https://example.com/CloudAgenticCoding/rth-agriculture/assets/garden-map.png')
  })
})

describe('remote heavy art', () => {
  test('classifies map and battle backdrops as heavy', () => {
    expect(isHeavyArtAsset('assets/garden-map.png')).toBe(true)
    expect(isHeavyArtAsset('assets/battle-fields/field3/clean.png')).toBe(true)
    expect(isHeavyArtAsset('hanzi/一.json')).toBe(false)
  })

  test('builds GitHub raw URLs under the configured remote base', () => {
    expect(
      resolveRemoteAssetUrl(
        'https://raw.githubusercontent.com/TepMex/CloudAgenticCoding/master/rth-agriculture/public',
        'assets/garden-map.png',
      ),
    ).toBe(
      'https://raw.githubusercontent.com/TepMex/CloudAgenticCoding/master/rth-agriculture/public/assets/garden-map.png',
    )
  })

  test('usesRemoteHeavyArt follows VITE_REMOTE_ASSET_BASE', () => {
    expect(usesRemoteHeavyArt(undefined)).toBe(false)
    expect(usesRemoteHeavyArt('')).toBe(false)
    expect(
      usesRemoteHeavyArt(
        'https://raw.githubusercontent.com/TepMex/CloudAgenticCoding/master/rth-agriculture/public/',
      ),
    ).toBe(true)
    expect(
      getRemoteAssetBase(
        'https://raw.githubusercontent.com/TepMex/CloudAgenticCoding/master/rth-agriculture/public',
      ),
    ).toBe(
      'https://raw.githubusercontent.com/TepMex/CloudAgenticCoding/master/rth-agriculture/public/',
    )
  })
})

/**
 * Resolve a Vite public-asset path against a document base URI.
 * Kept pure so Android `base: './'` CSS custom-property URLs can be unit-tested.
 */
export function resolveAssetUrl(viteBase: string, path: string, documentBase: string): string {
  const relative = `${viteBase}${path.replace(/^\//, '')}`
  return new URL(relative, documentBase).href
}

function ensureTrailingSlash(base: string): string {
  return base.endsWith('/') ? base : `${base}/`
}

/**
 * Heavy map/battle art that would blow the Android APK past GitHub’s 100 MB
 * push limit. When `VITE_REMOTE_ASSET_BASE` is set (Android sync), these load
 * from the monorepo on GitHub instead of `file:///android_asset/`.
 */
export function isHeavyArtAsset(path: string): boolean {
  const normalized = path.replace(/^\.\//, '').replace(/^\//, '')
  return (
    normalized === 'assets/garden-map.png' ||
    normalized === 'assets/garden-map_negative.png' ||
    normalized === 'assets/cleaning-court.png' ||
    normalized === 'assets/cleaning-court-clear.png' ||
    normalized.startsWith('assets/battle-fields/')
  )
}

/** Map layers needed before the world map can paint. */
export const CRITICAL_HEAVY_ART_ASSETS = [
  'assets/garden-map.png',
  'assets/garden-map_negative.png',
] as const

const HEAVY_ART_CACHE = 'rth-heavy-art-v1'

/** In-memory blob: URLs populated after download / cache hydrate. */
const heavyArtObjectUrls = new Map<string, string>()

export function getRemoteAssetBase(
  envBase: string | undefined = import.meta.env.VITE_REMOTE_ASSET_BASE,
): string | undefined {
  const trimmed = envBase?.trim()
  return trimmed ? ensureTrailingSlash(trimmed) : undefined
}

export function usesRemoteHeavyArt(
  envBase: string | undefined = import.meta.env.VITE_REMOTE_ASSET_BASE,
): boolean {
  return Boolean(getRemoteAssetBase(envBase))
}

export function resolveRemoteAssetUrl(remoteBase: string, path: string): string {
  return new URL(path.replace(/^\//, ''), ensureTrailingSlash(remoteBase)).href
}

/**
 * Public-folder URLs for Vite `base` (Pages absolute path or `./` for Android).
 *
 * Must return an absolute URL (or root-absolute path). Relative `./assets/...`
 * values assigned to CSS custom properties are re-resolved against the bundled
 * stylesheet location (`…/assets/index-*.css`), which doubles the `assets/`
 * segment and 404s the battle/map backgrounds under `base: './'`.
 *
 * When the Android build sets `VITE_REMOTE_ASSET_BASE`, heavy art prefers a
 * downloaded blob URL (offline after first fetch) or the GitHub raw URL.
 */
export function assetUrl(path: string): string {
  const normalized = path.replace(/^\.\//, '').replace(/^\//, '')
  const cached = heavyArtObjectUrls.get(normalized)
  if (cached) return cached

  const remoteBase = getRemoteAssetBase()
  if (remoteBase && isHeavyArtAsset(normalized)) {
    return resolveRemoteAssetUrl(remoteBase, normalized)
  }

  return resolveAssetUrl(import.meta.env.BASE_URL, normalized, document.baseURI)
}

async function readCachedResponse(url: string): Promise<Response | undefined> {
  if (!('caches' in globalThis)) return undefined
  try {
    const cache = await caches.open(HEAVY_ART_CACHE)
    return (await cache.match(url)) ?? undefined
  } catch {
    return undefined
  }
}

async function storeCachedResponse(url: string, response: Response): Promise<void> {
  if (!('caches' in globalThis)) return
  try {
    const cache = await caches.open(HEAVY_ART_CACHE)
    await cache.put(url, response)
  } catch {
    // Cache API can be flaky on some WebView builds; network URL still works.
  }
}

/**
 * Fetch one heavy art path into an object URL and Cache Storage so later
 * launches can paint without waiting on the network when the cache hits.
 */
export async function ensureHeavyArtAsset(
  path: string,
  envBase: string | undefined = import.meta.env.VITE_REMOTE_ASSET_BASE,
): Promise<string> {
  const normalized = path.replace(/^\.\//, '').replace(/^\//, '')
  const existing = heavyArtObjectUrls.get(normalized)
  if (existing) return existing

  const remoteBase = getRemoteAssetBase(envBase)
  if (!remoteBase || !isHeavyArtAsset(normalized)) {
    return resolveAssetUrl(import.meta.env.BASE_URL, normalized, document.baseURI)
  }

  const remoteUrl = resolveRemoteAssetUrl(remoteBase, normalized)
  const cached = await readCachedResponse(remoteUrl)
  const response = cached ?? (await fetch(remoteUrl))
  if (!response.ok) {
    throw new Error(`Failed to download heavy art ${normalized}: HTTP ${response.status}`)
  }
  if (!cached) {
    await storeCachedResponse(remoteUrl, response.clone())
  }

  const objectUrl = URL.createObjectURL(await response.blob())
  heavyArtObjectUrls.set(normalized, objectUrl)
  return objectUrl
}

/** Keep the welcome/map CSS custom property in sync after blob hydrate. */
export function applyGardenMapCssVar(): void {
  document.documentElement.style.setProperty(
    '--bg-garden-map',
    `url(${JSON.stringify(assetUrl('assets/garden-map.png'))})`,
  )
}

/** Download (or hydrate) the map layers required before entering the garden. */
export async function prepareCriticalHeavyArt(
  envBase: string | undefined = import.meta.env.VITE_REMOTE_ASSET_BASE,
): Promise<void> {
  if (!usesRemoteHeavyArt(envBase)) return
  await Promise.all(CRITICAL_HEAVY_ART_ASSETS.map((path) => ensureHeavyArtAsset(path, envBase)))
  applyGardenMapCssVar()
}

/** Test helper: clear in-memory object URLs between cases. */
export function resetHeavyArtObjectUrlsForTests(): void {
  for (const url of heavyArtObjectUrls.values()) {
    URL.revokeObjectURL(url)
  }
  heavyArtObjectUrls.clear()
}

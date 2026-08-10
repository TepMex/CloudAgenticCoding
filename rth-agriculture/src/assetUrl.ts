/**
 * Resolve a Vite public-asset path against a document base URI.
 * Kept pure so Android `base: './'` CSS custom-property URLs can be unit-tested.
 */
export function resolveAssetUrl(viteBase: string, path: string, documentBase: string): string {
  const relative = `${viteBase}${path.replace(/^\//, '')}`
  return new URL(relative, documentBase).href
}

/**
 * Public-folder URLs for Vite `base` (Pages absolute path or `./` for Android).
 *
 * Must return an absolute URL (or root-absolute path). Relative `./assets/...`
 * values assigned to CSS custom properties are re-resolved against the bundled
 * stylesheet location (`…/assets/index-*.css`), which doubles the `assets/`
 * segment and 404s the battle/map backgrounds under `base: './'`.
 */
export function assetUrl(path: string): string {
  return resolveAssetUrl(import.meta.env.BASE_URL, path, document.baseURI)
}

import type { Median } from '../strokeMatch/elementStrokes'

export type HanziCharacterJson = {
  strokes: string[]
  medians: Median[]
  elements: Record<string, number[]>
}

/**
 * Load stroke JSON for a character.
 * XHR, not fetch: Chromium WebView blocks Fetch against file:// inside the APK.
 * Status 0 is a successful file:// read.
 */
export function loadHanziCharData(char: string): Promise<HanziCharacterJson> {
  const url = new URL(`./hanzi/${encodeURIComponent(char)}.json`, document.baseURI).href
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('GET', url, true)
    if (xhr.overrideMimeType) xhr.overrideMimeType('application/json')
    xhr.onload = () => {
      if (xhr.status !== 200 && xhr.status !== 0) {
        reject(new Error(`Нет штрихов для ${char}`))
        return
      }
      try {
        resolve(JSON.parse(xhr.responseText) as HanziCharacterJson)
      } catch (error) {
        reject(error instanceof Error ? error : new Error(String(error)))
      }
    }
    xhr.onerror = () => reject(new Error(`Нет штрихов для ${char}`))
    xhr.send(null)
  })
}

#!/usr/bin/env bun
/**
 * Regression: with Vite base `./` (Android APK sync), battle backdrop must paint
 * the parchment writing field — not 404 via CSS-resolved `assets/assets/...`.
 *
 * Usage: bun scripts/check-battle-canvas.mjs [baseUrl]
 * Default baseUrl: http://127.0.0.1:8765
 */
import { chromium } from 'playwright'
import { spawnSync } from 'child_process'

const baseUrl = (process.argv[2] ?? 'http://127.0.0.1:8765').replace(/\/$/, '')
const OUT = process.env.BATTLE_CANVAS_SCREENSHOT ?? '/tmp/cursor/artifacts/battle-canvas-check.png'

const browser = await chromium.launch({ headless: true })
const page = await (
  await browser.newContext({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 2,
    isMobile: true,
    hasTouch: true,
  })
).newPage()

const failed = []
page.on('requestfailed', (request) => failed.push(request.url()))

await page.goto(`${baseUrl}/`, { waitUntil: 'networkidle' })
await page.getByRole('button', { name: /Войти в сад/i }).click()
await page.locator('button.primary-button').first().click()
await page.waitForSelector('.battle-screen .writing-circle')
await page.waitForTimeout(400)

const metrics = await page.evaluate(() => {
  const backdrop = document.querySelector('.battle-backdrop')
  const circle = document.querySelector('.writing-circle')
  const bg = backdrop ? getComputedStyle(backdrop).backgroundImage : ''
  const match = /url\(["']?([^"')]+)["']?\)/.exec(bg)
  const rect = circle?.getBoundingClientRect()
  return {
    url: match?.[1] ?? '',
    rect: rect
      ? { x: rect.left, y: rect.top, w: rect.width, h: rect.height }
      : null,
    dpr: window.devicePixelRatio || 1,
  }
})

if (!metrics.url) {
  console.error('FAIL: battle-backdrop has no background-image')
  process.exit(1)
}
if (metrics.url.includes('assets/assets/')) {
  console.error(`FAIL: doubled assets path: ${metrics.url}`)
  process.exit(1)
}

const probe = await page.evaluate(async (url) => {
  try {
    const response = await fetch(url)
    return { ok: response.ok, status: response.status }
  } catch (error) {
    return { ok: false, status: 0, error: String(error) }
  }
}, metrics.url)

if (!probe.ok) {
  console.error(`FAIL: backdrop image not loadable (${probe.status}): ${metrics.url}`, probe.error ?? '')
  process.exit(1)
}

await page.screenshot({ path: OUT })

if (!metrics.rect) {
  console.error('FAIL: missing writing circle')
  process.exit(1)
}

const py = `
from PIL import Image
img = Image.open(${JSON.stringify(OUT)})
w, h = img.size
dpr = ${metrics.dpr}
r = ${JSON.stringify(metrics.rect)}
cx = int((r['x'] + r['w']/2) * dpr)
cy = int((r['y'] + r['h']/2) * dpr)
samples = []
for y in range(cy-24, cy+24, 2):
  for x in range(cx-24, cx+24, 2):
    if 0 <= x < w and 0 <= y < h:
      p = img.getpixel((x, y))
      samples.append(0.299*p[0] + 0.587*p[1] + 0.114*p[2])
avg = sum(samples)/len(samples)
print(avg)
if avg < 120:
  raise SystemExit(f'writing field too dark (Y={avg:.1f}); backdrop not painting')
`
const result = spawnSync('python3', ['-c', py], { encoding: 'utf8' })
if (result.status !== 0) {
  console.error(result.stdout)
  console.error(result.stderr)
  console.error('FAIL: battle canvas brightness check')
  process.exit(1)
}

const avg = Number(result.stdout.trim())
console.log(`OK: battle backdrop ${metrics.url}`)
console.log(`OK: writing field brightness Y=${avg.toFixed(1)}`)
if (failed.some((url) => url.includes('cleaning-court') || url.includes('garden-map'))) {
  console.error('FAIL: background asset request failed', failed)
  process.exit(1)
}
await browser.close()

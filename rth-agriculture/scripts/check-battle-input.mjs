#!/usr/bin/env bun
/**
 * Regression: Android APK loads via file:// — Fetch cannot read hanzi JSON, so
 * quiz/hint stay dead. This script boots the relative-base build from file://
 * and asserts draw + hint work (XHR loader).
 *
 * Usage: bun scripts/check-battle-input.mjs [/path/to/www]
 */
import { chromium } from 'playwright'
import path from 'path'
import { existsSync } from 'fs'

const www = path.resolve(process.argv[2] ?? '/tmp/rth-www')
const index = 'file://' + path.join(www, 'index.html')
if (!existsSync(path.join(www, 'index.html'))) {
  console.error(`FAIL: missing ${www}/index.html — build with base ./ first`)
  process.exit(1)
}

const browser = await chromium.launch({
  headless: true,
  args: ['--allow-file-access-from-files', '--disable-web-security'],
})
const page = await (
  await browser.newContext({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 2,
    hasTouch: true,
    isMobile: true,
  })
).newPage()

const errors = []
page.on('console', (msg) => {
  if (msg.type() === 'error') errors.push(msg.text())
})
page.on('pageerror', (err) => errors.push(err.message))

await page.goto(index, { waitUntil: 'domcontentloaded' })
await page.getByRole('button', { name: /Войти в сад/i }).click()
await page.locator('button.primary-button').first().click()
await page.waitForSelector('.battle-screen .writing-circle svg')
// Allow XHR char data to load
await page.waitForTimeout(800)

const loaded = await page.evaluate(async () => {
  const url = new URL(`./hanzi/${encodeURIComponent('一')}.json`, document.baseURI).href
  return await new Promise((resolve) => {
    const xhr = new XMLHttpRequest()
    xhr.open('GET', url, true)
    xhr.onload = () => resolve({ status: xhr.status, ok: xhr.status === 200 || xhr.status === 0, len: xhr.responseText.length })
    xhr.onerror = () => resolve({ status: xhr.status, ok: false, len: 0 })
    xhr.send()
  })
})
if (!loaded.ok) {
  console.error('FAIL: XHR cannot read hanzi JSON', loaded)
  process.exit(1)
}

await page.locator('.hint-button').click()
await page.waitForTimeout(600)
const afterHint = await page.evaluate(() => ({
  feedback: document.querySelector('.stroke-feedback span')?.textContent,
  // Hanzi Writer animates highlight into path elements with non-zero opacity
  animated: [...document.querySelectorAll('.writing-circle svg path')].some((path) => {
    const opacity = getComputedStyle(path).opacity
    const stroke = getComputedStyle(path).stroke
    return Number(opacity) > 0.05 && stroke !== 'none'
  }),
  pathCount: document.querySelectorAll('.writing-circle svg path').length,
}))

if (afterHint.feedback !== 'Запомните движение кисти') {
  console.error('FAIL: hint feedback missing', afterHint)
  process.exit(1)
}
if (afterHint.pathCount < 1) {
  console.error('FAIL: hint did not create stroke paths (char data not loaded?)', afterHint)
  process.exit(1)
}

// Fresh battle character may advance after hint-only — reload battle for draw test
await page.locator('.back-button').click()
await page.waitForSelector('.map-screen')
await page.locator('button.primary-button').first().click()
await page.waitForSelector('.writing-circle svg')
await page.waitForTimeout(800)

const box = await page.locator('.writing-circle').boundingBox()
if (!box) {
  console.error('FAIL: no writing circle')
  process.exit(1)
}
const y = box.y + box.height * 0.5
const x1 = box.x + box.width * 0.18
const x2 = box.x + box.width * 0.82
await page.mouse.move(x1, y)
await page.mouse.down()
for (let i = 1; i <= 12; i++) {
  await page.mouse.move(x1 + (x2 - x1) * (i / 12), y)
}
await page.mouse.up()
await page.waitForTimeout(800)

const afterDraw = await page.evaluate(() => ({
  feedback: document.querySelector('.stroke-feedback span')?.textContent,
  cut: document.querySelectorAll('.battle-progress i.is-cut').length,
}))

const drawOk =
  afterDraw.cut >= 1 || /пепел|Точный|отступил|корень/i.test(afterDraw.feedback || '')
if (!drawOk) {
  console.error('FAIL: stroke not accepted under file://', afterDraw)
  console.error('console errors:', errors.slice(0, 10))
  process.exit(1)
}

if (errors.some((line) => /Fetch API cannot load file:/i.test(line))) {
  console.error('FAIL: fetch still used for file:// hanzi load', errors)
  process.exit(1)
}

console.log('OK: file:// hint + draw', { afterHint, afterDraw })
await browser.close()

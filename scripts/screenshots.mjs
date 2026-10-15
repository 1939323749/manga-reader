// Takes screenshots of the main screens against a running server (used in CI).
// Usage: node scripts/screenshots.mjs http://127.0.0.1:4567 out-dir
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const base = (process.argv[2] || 'http://127.0.0.1:4567').replace(/\/$/, '')
const out = process.argv[3] || 'screenshots'
mkdirSync(out, { recursive: true })

const api = async (path, body) => {
  const res = await fetch(base + path, body ? { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) } : {})
  return res.json()
}

const sources = await api('/api/sources')
// the smoke test leaves MangaDex (via jar) installed; Mangapill ends up on the optional APK build
const pill = sources.find((s) => s.name === 'MangaDex' && s.lang === 'en') || sources[0]
const popular = await api(`/api/sources/${pill.id}/popular?page=1`)
const manga = popular.mangas[0]
const detail = await api(`/api/sources/${pill.id}/manga`, manga)
const chapter = detail.chapters[detail.chapters.length - 1]

const q = (o) => new URLSearchParams(o).toString()
const shots = [
  ['sources', '/'],
  ['extensions', '/extensions'],
  ['browse', `/source/${pill.id}`],
  ['manga', `/source/${pill.id}/manga?${q({ url: manga.url })}`],
  ['reader', `/source/${pill.id}/read?${q({ manga: manga.url, chapter: chapter.url })}`],
  ['recent', '/recent'],
]

const browser = await chromium.launch()
const errors = []
for (const [device, viewport] of [
  ['desktop', { width: 1280, height: 860 }],
  ['mobile', { width: 390, height: 844 }],
]) {
  for (const scheme of device === 'desktop' ? ['light', 'dark'] : ['light']) {
    const context = await browser.newContext({ viewport, colorScheme: scheme, deviceScaleFactor: 1 })
    const page = await context.newPage()
    page.on('pageerror', (e) => errors.push(`${device}/${scheme}: ${e.message}`))
    page.on('console', (m) => m.type() === 'error' && errors.push(`${device}/${scheme} console: ${m.text()}`))
    for (const [name, path] of shots) {
      // the manga page relies on the list page having stored the manga object
      await page.goto(base + path, { waitUntil: 'networkidle', timeout: 60000 }).catch((e) => errors.push(`${name}: ${e.message}`))
      await page.waitForTimeout(name === 'reader' ? 2500 : 800)
      await page.screenshot({ path: `${out}/${device}-${scheme}-${name}.png`, fullPage: false })
      console.log('shot', device, scheme, name)
    }
    await context.close()
  }
}
await browser.close()
if (errors.length) {
  console.log('page errors:')
  for (const e of errors) console.log(' ', e)
}

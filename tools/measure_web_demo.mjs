// measure_web_demo.mjs <url|dir> [runs] [throttle]: first load of the Wasm demo in Chromium (#78).
// Each run launches a fresh browser (cold HTTP and GPU shader caches: a first visit), loads <url>, and
// records per request the transferred bytes and timing, every console error and uncaught page error, and
// when the page first shows something other than its background: the loader (if the page has one) and
// the first frame Compose painted (a screenshot with app pixels in it). A run that paints nothing within
// 90 s (MEASURE_TIMEOUT_S) is reported as "never rendered". On a page with the loader it also records, on
// the page's own clock (ms since navigation): the first contentful paint (the loader), `started`, when main() ran (the Wasm downloaded and
// instantiated: the loader says "Preparing the glass"), and `ready`, when the loader was dismissed after
// settled frames (the time to a usable app), plus the longest gap between animation frames before that.
// `context` is when Skia's runtime had loaded and the first WebGL context was made (the whole download is
// in by then). MEASURE_UNTIL=started|context ends a run at that mark.
// throttle: none (default), fast4g (9 Mbit/s down, 40 ms), slow4g (1.6 Mbit/s down, 150 ms).
// A directory (e.g. apps/web/build/dist/wasmJs/productionExecutable) is served on localhost the way
// GitHub Pages serves the demo: gzip for text and wasm, `Cache-Control: max-age=600`. Append the page's
// hash after the path: `apps/web/build/dist/wasmJs/productionExecutable#material3`.
// Playwright comes from ports/web (npm ci there), else from a global `playwright` install. The installed
// Chrome is used (`channel: 'chrome'`); MEASURE_CHANNEL=bundled uses Playwright's own Chromium instead
// (a machine without Chrome, such as a cloud container: software GL, so shader timings differ).
//   node tools/measure_web_demo.mjs https://liquidglass.shayxo.dev/demo/#material3 3 fast4g
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import fs from 'node:fs'
import http from 'node:http'
import zlib from 'node:zlib'

const require = createRequire(path.join(path.dirname(fileURLToPath(import.meta.url)), '../ports/web/package.json'))
let chromium
try { ({ chromium } = require('@playwright/test')) } catch { ({ chromium } = createRequire(import.meta.url)('playwright')) }
const channel = process.env.MEASURE_CHANNEL === 'bundled' ? undefined : (process.env.MEASURE_CHANNEL ?? 'chrome')

const [target, runsArg = '3', throttle = 'none'] = process.argv.slice(2)
let url = target
if (!target) { console.error('usage: node tools/measure_web_demo.mjs <url> [runs] [none|fast4g|slow4g]'); process.exit(2) }
const profiles = {
    none: null,
    fast4g: { latency: 40, downloadThroughput: 9_000_000 / 8, uploadThroughput: 1_500_000 / 8 },
    slow4g: { latency: 150, downloadThroughput: 1_600_000 / 8, uploadThroughput: 750_000 / 8 },
}
if (!(throttle in profiles)) { console.error(`unknown throttle ${throttle}`); process.exit(2) }

let server = null
if (!/^https?:/.test(target)) {
    const [dir, hash = ''] = target.split('#')
    const types = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.wasm': 'application/wasm', '.json': 'application/json' }
    const root = path.resolve(dir)
    server = http.createServer((req, res) => {
        const file = path.resolve(root, '.' + decodeURIComponent(new URL(req.url, 'http://x').pathname).replace(/\/$/, '/index.html'))
        if (file !== root && !file.startsWith(root + path.sep)) { res.writeHead(403).end(); return }
        fs.readFile(file, (err, body) => {
            if (err) { res.writeHead(404).end(); return }
            const type = types[path.extname(file)] ?? 'application/octet-stream'
            const zip = /text|javascript|wasm|json/.test(type)
            res.writeHead(200, { 'Content-Type': type, 'Cache-Control': 'max-age=600', ...(zip ? { 'Content-Encoding': 'gzip' } : {}) })
            res.end(zip ? zlib.gzipSync(body, { level: 6 }) : body)
        })
    })
    await new Promise(r => server.listen(0, '127.0.0.1', r))
    url = `http://127.0.0.1:${server.address().port}/${hash ? '#' + hash : ''}`
}

for (let run = 1; run <= Number(runsArg); run++) {
    // A new browser per run: within one browser the GPU process keeps compiled shaders across contexts.
    const browser = await chromium.launch({ channel, headless: true })
    const context = await browser.newContext({ viewport: { width: 1280, height: 800 } })
    const page = await context.newPage()
    const cdp = await context.newCDPSession(page)
    await cdp.send('Network.enable')
    await cdp.send('Network.setCacheDisabled', { cacheDisabled: true })
    if (profiles[throttle]) await cdp.send('Network.emulateNetworkConditions', { offline: false, ...profiles[throttle] })
    const requests = new Map()
    cdp.on('Network.requestWillBeSent', e => requests.set(e.requestId, { url: e.request.url, start: e.timestamp }))
    cdp.on('Network.responseReceived', e => { const r = requests.get(e.requestId); if (r) { r.status = e.response.status; r.encoding = e.response.headers['content-encoding'] ?? e.response.headers['Content-Encoding'] } })
    cdp.on('Network.loadingFinished', e => { const r = requests.get(e.requestId); if (r) { r.end = e.timestamp; r.transferred = e.encodedDataLength } })
    cdp.on('Network.loadingFailed', e => { const r = requests.get(e.requestId); if (r) { r.failed = e.errorText } })
    const errors = []
    page.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') errors.push(`${m.type()}: ${m.text()}`) })
    page.on('pageerror', e => errors.push(`pageerror: ${e.message}`))

    // Page-clock marks: the main thread can be busy for seconds compiling shaders, so polling from here
    // (and screenshots) can lag the moment itself.
    await page.addInitScript(() => {
        const marks = window.__lgLoad = { gaps: [] }
        new MutationObserver(() => {
            const loader = document.getElementById('loader'); if (!loader) return
            if (marks.started === undefined && /Preparing/.test(document.getElementById('loader-title')?.textContent ?? '')) marks.started = performance.now()
            const state = loader.dataset.state
            if ((state === 'ready' || state === 'failed') && marks[state] === undefined) marks[state] = performance.now()
        }).observe(document, { subtree: true, childList: true, attributes: true, characterData: true })
        // Skia's runtime finishes loading after main() starts; its first WebGL context marks that.
        const getContext = HTMLCanvasElement.prototype.getContext
        HTMLCanvasElement.prototype.getContext = function (type) {
            const context = getContext.apply(this, arguments)
            if (context && /webgl/.test(type) && marks.context === undefined) marks.context = performance.now()
            return context
        }
        let last = null
        const tick = now => { if (last !== null && marks.ready === undefined) marks.gaps.push(Math.round(now - last)); last = now; requestAnimationFrame(tick) }
        requestAnimationFrame(tick)
    })
    const timeout = Number(process.env.MEASURE_TIMEOUT_S ?? 90) * 1000
    const t0 = Date.now()
    await page.goto(url, { waitUntil: 'commit' })
    let loader = null, firstFrame = null, screenshotFailures = 0
    while (Date.now() - t0 < timeout) {
        const state = await page.evaluate(() => {
            const loader = document.getElementById('loader')
            return {
                loader: !!loader && loader.dataset.state !== 'ready' && loader.getBoundingClientRect().height > 0,
                canvas: !!document.querySelector('canvas') || [...document.querySelectorAll('*')].some(e => e.shadowRoot?.querySelector('canvas')),
                failed: document.getElementById('loader')?.dataset.state === 'failed',
            }
        }).catch(() => ({}))
        if (state.loader && loader === null) loader = Date.now() - t0
        if (state.failed) { errors.push('loader reported failure'); break }
        // MEASURE_UNTIL=started|context stops at that mark: for a machine whose frames are not representative
        // (software GL), where only the download and instantiation can be compared.
        const until = process.env.MEASURE_UNTIL
        if (until && await page.evaluate(m => window.__lgLoad?.[m] !== undefined, until).catch(() => false)) break
        if (state.canvas) {
            // A frame counts once enough of the window differs from the dark page background.
            // A page busy compiling shaders can miss the screenshot deadline: count it, wait, and try again.
            // Any other screenshot error is recorded and ends the run.
            let failure = null
            const shot = await page.screenshot({ type: 'jpeg', quality: 50, timeout: 5000 }).catch(e => { failure = e; return null })
            if (failure?.name === 'TimeoutError') { screenshotFailures++; await page.waitForTimeout(100); continue }
            if (failure) { errors.push(`screenshot failed: ${failure.message?.split('\n')[0] ?? failure}`); break }
            const bright = await page.evaluate(async b64 => {
                const img = new Image(); img.src = 'data:image/jpeg;base64,' + b64; await img.decode()
                const c = new OffscreenCanvas(160, 100); const g = c.getContext('2d'); g.drawImage(img, 0, 0, 160, 100)
                const d = g.getImageData(0, 0, 160, 100).data; let n = 0
                for (let i = 0; i < d.length; i += 4) if (d[i] + d[i + 1] + d[i + 2] > 60) n++
                return n / (160 * 100)
            }, shot.toString('base64'))
            if (bright > 0.2) { firstFrame = Date.now() - t0; break }
        }
        await page.waitForTimeout(100)
    }
    const first = Math.min(...[...requests.values()].map(r => r.start))
    const files = [...requests.values()].filter(r => r.end && r.url.startsWith('http')).map(r => ({
        file: r.url.split('/').pop().split('#')[0] || '(page)', kB: Math.round((r.transferred ?? 0) / 1024),
        at: Math.round((r.start - first) * 1000),
        ms: Math.round((r.end - r.start) * 1000), enc: r.encoding ?? '-',
    }))
    const total = files.reduce((s, f) => s + f.kB, 0)
    const marks = await page.evaluate(() => ({ ...window.__lgLoad,
        fcp: performance.getEntriesByName('first-contentful-paint')[0]?.startTime })).catch(() => null)
    const ms = v => v === undefined || v === null ? '-' : Math.round(v)
    const gaps = marks?.gaps ?? []
    console.log(`run ${run} [${throttle}] loader=${loader ?? '-'} ms  first frame=${firstFrame ?? `never rendered (${timeout / 1000} s)`} ms  transferred=${total} kB`)
    if (marks) console.log(`    page clock: first paint=${ms(marks.fcp)} ms  started=${ms(marks.started)} ms  context=${ms(marks.context)} ms  ready=${ms(marks.ready)} ms  frames before ready=${gaps.length}  longest gaps=${[...gaps].sort((a, b) => b - a).slice(0, 5).join(', ')} ms`)
    for (const f of files) console.log(`    ${f.file.padEnd(28)} ${String(f.kB).padStart(6)} kB  start ${String(f.at).padStart(6)} ms ${String(f.ms).padStart(6)} ms  ${f.enc}`)
    if (screenshotFailures) errors.push(`${screenshotFailures} screenshot(s) timed out while the page was busy`)
    for (const e of errors) console.log(`    ${e.slice(0, 300)}`)
    await browser.close()
}
server?.close()

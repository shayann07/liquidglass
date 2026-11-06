// measure_web_demo_shaders.mjs <dist> [hash] [visits]: how long the Wasm demo's first load spends linking
// WebGL programs (#78). Skia links a program the first time a draw needs it and waits for the link on the
// main thread, so this is where a first visit's seconds go. Every `linkProgram` and the first
// `getProgramParameter(LINK_STATUS)` after it are timed on the page's own clock; the program's fragment
// source length stands in for its size. `ready` is when the page's loader was dismissed.
// One persistent Chrome profile is used for all visits, so visit 2 and on show the browser's own shader
// cache at work (a new profile per run would be a first visit every time).
//   node tools/measure_web_demo_shaders.mjs apps/web/build/dist/wasmJs/productionExecutable material3 3
// Playwright comes from ports/web (npm ci there), else from a global `playwright` install. The installed
// Chrome is used, with a GPU if the machine has one; timings differ by GPU and driver.
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import http from 'node:http'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
const require = createRequire(path.join(path.dirname(fileURLToPath(import.meta.url)), '../ports/web/package.json'))
let chromium
try { ({ chromium } = require('@playwright/test')) } catch { ({ chromium } = createRequire(import.meta.url)('playwright')) }
const [dist, hash = 'material3', visitsArg = '2'] = process.argv.slice(2)
if (!dist) { console.error('usage: node tools/measure_web_demo_shaders.mjs <dist> [hash] [visits]'); process.exit(2) }
const root = path.resolve(dist)
const types = { '.html': 'text/html', '.js': 'text/javascript', '.wasm': 'application/wasm', '.json': 'application/json' }
const server = http.createServer((req, res) => {
    const file = path.resolve(root, '.' + decodeURIComponent(new URL(req.url, 'http://x').pathname).replace(/\/$/, '/index.html'))
    if (file !== root && !file.startsWith(root + path.sep)) { res.writeHead(403).end(); return }
    fs.readFile(file, (err, body) => {
        if (err) { res.writeHead(404).end(); return }
        // The browser's shader cache is keyed on the shader source, not on how the page was served.
        res.writeHead(200, { 'Content-Type': types[path.extname(file)] ?? 'application/octet-stream' }).end(body)
    })
})
await new Promise(r => server.listen(0, '127.0.0.1', r))
const url = `http://127.0.0.1:${server.address().port}/#${hash}`
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'lg-shader-probe-'))

// Wraps the GL calls before the page's own scripts run.
const hook = () => {
    window.__links = []
    for (const Ctx of [WebGLRenderingContext, WebGL2RenderingContext]) {
        const text = new WeakMap(), attached = new WeakMap()
        const shaderSource = Ctx.prototype.shaderSource
        Ctx.prototype.shaderSource = function (s, t) { text.set(s, t); return shaderSource.call(this, s, t) }
        const attach = Ctx.prototype.attachShader
        Ctx.prototype.attachShader = function (p, s) { (attached.get(p) ?? attached.set(p, []).get(p)).push(text.get(s) ?? ''); return attach.call(this, p, s) }
        const link = Ctx.prototype.linkProgram
        Ctx.prototype.linkProgram = function (p) {
            const t0 = performance.now(), r = link.call(this, p)
            window.__links.push({ p, at: Math.round(t0), call: +(performance.now() - t0).toFixed(1), chars: Math.max(0, ...(attached.get(p) ?? []).map(s => s.length)) })
            return r
        }
        const status = Ctx.prototype.getProgramParameter
        Ctx.prototype.getProgramParameter = function (p, name) {
            const t0 = performance.now(), r = status.call(this, p, name)
            const e = window.__links.find(l => l.p === p)
            if (e && name === this.LINK_STATUS && e.wait === undefined) e.wait = +(performance.now() - t0).toFixed(1)
            return r
        }
    }
}

try {
    for (let visit = 1; visit <= Number(visitsArg); visit++) {
        const context = await chromium.launchPersistentContext(profile, { channel: 'chrome', headless: true, viewport: { width: 1280, height: 800 } })
        const page = await context.newPage()
        await page.addInitScript(hook)
        const t0 = Date.now()
        await page.goto(url)
        const ready = await page.waitForFunction(() => document.getElementById('loader')?.dataset.state === 'ready', null, { timeout: 180_000 })
            .then(() => Date.now() - t0).catch(() => null)
        const links = (await page.evaluate(() => window.__links.map(({ p, ...r }) => r))).map(l => ({ ...l, ms: l.call + (l.wait ?? 0) }))
        const total = links.reduce((a, l) => a + l.ms, 0)
        console.log(`visit ${visit}: ready=${ready ?? 'never'} ms  programs=${links.length}  link time=${total.toFixed(0)} ms`)
        for (const l of links.sort((a, b) => b.ms - a.ms).slice(0, 6)) console.log(`    ${l.ms.toFixed(0).padStart(6)} ms  fragment ${String(l.chars).padStart(6)} chars  at ${l.at} ms`)
        await context.close()
    }
} finally {
    server.close()
    fs.rmSync(profile, { recursive: true, force: true })
}

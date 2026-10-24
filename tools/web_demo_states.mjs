// web_demo_states.mjs <dist> <outdir>: forces each of the web demo's loader states in Playwright's Chromium
// and screenshots it (#78): loading, no WebAssembly GC (`WebAssembly.validate` stubbed), a failed download
// (every .wasm request aborted), an uncaught error while starting, and a lost WebGL context. Prints the
// loader's state and text and how many .wasm requests each made (none, for a browser that cannot run it).
//   node tools/web_demo_states.mjs apps/web/build/dist/wasmJs/productionExecutable /tmp/states
// Playwright comes from ports/web (npm ci there), else from a global `playwright` install.
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
const require = createRequire(path.join(path.dirname(fileURLToPath(import.meta.url)), '../ports/web/package.json'))
let chromium
try { ({ chromium } = require('@playwright/test')) } catch { ({ chromium } = createRequire(import.meta.url)('playwright')) }
const [dist, out] = process.argv.slice(2)
if (!dist || !out) { console.error('usage: node tools/web_demo_states.mjs <dist> <outdir>'); process.exit(2) }
fs.mkdirSync(out, { recursive: true })
const types = { '.html': 'text/html', '.js': 'text/javascript', '.wasm': 'application/wasm', '.json': 'application/json' }
const server = http.createServer((req, res) => {
    const file = path.join(dist, decodeURIComponent(new URL(req.url, 'http://x').pathname).replace(/\/$/, '/index.html'))
    fs.readFile(file, (err, body) => {
        if (err) { res.writeHead(404).end(); return }
        res.writeHead(200, { 'Content-Type': types[path.extname(file)] ?? 'application/octet-stream' }).end(body)
    })
})
await new Promise(r => server.listen(0, '127.0.0.1', r))
const url = `http://127.0.0.1:${server.address().port}/#material3`

async function scenario(name, setup, after) {
    const browser = await chromium.launch()
    const page = await browser.newPage({ viewport: { width: 960, height: 600 } })
    const wasm = []
    page.on('request', r => { if (r.url().endsWith('.wasm')) wasm.push(r.url().split('/').pop()) })
    await setup?.(page)
    await page.goto(url)
    const state = await after(page)
    const loader = await page.evaluate(() => {
        const l = document.getElementById('loader')
        return { state: l.dataset.state ?? 'loading', title: document.getElementById('loader-title').textContent, detail: document.getElementById('loader-detail').textContent }
    })
    await page.screenshot({ path: path.join(out, `${name}.png`), timeout: 60_000 }).catch(e => console.log(`  screenshot failed: ${e.message.split('\n')[0]}`))
    console.log(`${name}: ${JSON.stringify(loader)} wasm requests=${wasm.length} (${[...new Set(wasm)].length} files) ${state ?? ''}`)
    await browser.close()
}

const waitState = s => page => page.waitForFunction(v => document.getElementById('loader').dataset.state === v, s, { timeout: 120_000 }).then(() => '').catch(() => '(timed out)')

await scenario('no-wasm-gc', p => p.addInitScript(() => { WebAssembly.validate = () => false }), async p => { await p.waitForTimeout(1500); return '' })
await scenario('download-fail', p => p.route(/\.wasm$/, r => r.abort()), waitState('failed'))
await scenario('startup-error', p => p.addInitScript(() => {
    // An uncaught error while starting, after the downloads.
    addEventListener('DOMContentLoaded', () => setTimeout(() => { throw new Error('Simulated startup error') }, 300))
}), waitState('failed'))
await scenario('loading', null, async p => { await p.waitForTimeout(300); return '' })
await scenario('context-lost', null, async p => {
    // Lose the app's WebGL context once Skia has made it.
    await p.waitForFunction(() => [...document.querySelectorAll('*')].some(e => e.shadowRoot?.querySelector('canvas')), null, { timeout: 120_000 })
    await p.waitForTimeout(500)
    await p.evaluate(() => {
        const c = [...document.querySelectorAll('*')].map(e => e.shadowRoot?.querySelector('canvas')).find(Boolean)
        const gl = c.getContext('webgl2') || c.getContext('webgl')
        gl.getExtension('WEBGL_lose_context').loseContext()
    })
    return waitState('failed')(p)
})
server.close()

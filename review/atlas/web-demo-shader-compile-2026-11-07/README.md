# The web demo's first load is shader compilation (#78) — 7 November 2026

Source: branch `perf/78-web-demo-load` from `main` 13ec13b, a production `wasmJsBrowserDistribution` build.
Installed Chrome (headless, ANGLE/D3D11) on an AMD integrated GPU, Windows 11; the page served from localhost
without compression. **Chrome on one machine.** Firefox, Safari, phones and other GPUs are not measured.
Screenshots are the demo's own loader (`slow-start.png` and the other states, from `tools/web_demo_states.mjs`).

## Finding

On a first visit the demo is ready after **11-17 s**, and almost all of that is the main thread waiting for
WebGL programs to link, not downloading. With nothing throttled the download and instantiation finish by
~0.6 s (`context` mark of `tools/measure_web_demo.mjs`: 622 and 568 ms) and the loader is dismissed at
**17.8 s and 14.7 s** (two runs); the page's longest gaps between animation frames before that are
5.6 / 5.3 / 4.8 / 1.4 s.

`tools/measure_web_demo_shaders.mjs` (new) times every `linkProgram` and the wait for its link status. Three
visits in one Chrome profile (visit 1 has an empty shader cache):

| Visit | ready | programs | time spent linking | three largest programs |
| :-- | --: | --: | --: | --: |
| 1 (cold cache), this run | 10.9 s | 49 | 9.8 s | 2.76 + 2.67 + 2.60 s (40.4k, 39.1k, 39.2k fragment characters) |
| 2 | 1.16 s | 49 | 0.06 s | 2 ms each |
| 3 | 1.23 s | 49 | 0.07 s | 2 ms each |

Earlier cold runs of the same build, same machine: ready 16.7 s (12.8 s in the three largest programs: 4.4,
4.2, 4.2 s) and 14.1 s (4.1, 3.9, 2.9 s); then repeat visits 1.3 s and 1.3 s. A cold visit varies by a few
seconds between runs on this machine, so read the ranges: **cold 11-17 s, repeat ~1.2-1.3 s, 49 programs
every time**. The `raw/` folder holds the probe's output.

- Three programs of ~39-41k characters account for 85% of the linking (about 4 s each on the slowest run), two
  more of ~22k for about 1 s; the other 44 take milliseconds. All five are Skia's wrapping of the glass shaders
  (the material and the pose/selector shader): the same ~39k-character body under three different child
  setups (the dumped sources differ in a colour-space transform or a plain matrix on an input image and in
  decal/clamp helpers), so Skia links each as its own program.
- The browser's shader cache makes the second visit fast; that is why the page "works" for a reviewer who has
  seen it before and looks hung to one who has not.
- Plausible cause of the "never renders" report (not reproduced as a failure): the same three links on a
  slower GPU or a phone take far longer than 4 s each, with the page showing nothing but the loader. Not
  measured on one.

## What changed

- **The loader says what it is waiting for.** After 6 s without the app being ready, its detail line reads "Still
  compiling the glass shaders for your GPU. This happens once per browser and later visits are much faster. A
  slow GPU or a phone can take a minute or more." The first line (before 6 s) now says "takes a few seconds or
  more" (it said "can take a few seconds"). Nothing else in the loader or the failure states moved
  (`raw/states.txt`: no-wasm-gc 0 downloads, download-fail, startup-error, context-lost unchanged; the new
  `slow-start` state holds `atlas.js` for 8 s to show the notice, `slow-start.png`).
- **Tooling:** `tools/measure_web_demo_shaders.mjs` (above) and the `slow-start` scenario and
  `MEASURE_CHANNEL` in `tools/web_demo_states.mjs`.
- Nothing about shaders, optics or the download changed.

## Not done, and why

- **Fewer or cheaper programs is the real fix**, and the measurement says how much is at stake: if the
  three ~40k variants linked as one, and cost about the same, roughly two thirds of this machine's first visit
  would go (an estimate, not a measurement). It means changing how
  the glass inputs are bound (Skia's child wrapping) or the shader's size, which touches the optical path and
  every port, so it needs its own design and a pixel-identity gate against the desktop renders (the desktop
  JVM uses the same Skiko code). Recorded in `dev/TODO.md`; not attempted in this PR.
- **Brotli** (22% smaller: 4.59 → 3.55 MB measured here, `br` quality 11) needs a host that negotiates it; GitHub
  Pages does not. A client-side decoder would be a new vendored dependency for about 1 MB less transfer;
  left for the owner's CDN decision.
- **Caching** headers cannot be set on Pages (`max-age=600`); the files are content-hashed, so a repeat visit
  revalidates at most. The shader cache, not the HTTP cache, is what makes repeat visits fast.

## Reproduce

```
cd ports/web && npm ci && cd ../..
./gradlew :web:wasmJsBrowserDistribution
node tools/measure_web_demo_shaders.mjs apps/web/build/dist/wasmJs/productionExecutable material3 3
MEASURE_CHANNEL=chrome node tools/web_demo_states.mjs apps/web/build/dist/wasmJs/productionExecutable /tmp/states
```

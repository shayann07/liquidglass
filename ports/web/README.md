# Web preview

This dependency-free ES module renders the **continuous lens** over a source image, canvas or video.
It can be called from vanilla JS, React, Vue, Svelte, Angular, a web component or a WebView. It does not
capture arbitrary HTML and is not yet the full Compose material/navigation renderer. No npm package
has been published; use the checked-in source. Code is Apache-2.0 under the repository's LICENSE/NOTICE.

```js
import { createGlassRenderer } from './liquidglass.mjs';
const glass = createGlassRenderer(outputCanvas);
glass.setSource(yourBackdropCanvas); // origin-clean; excludes the glass itself
glass.render({ width: 1000, height: 520, x: 500, y: 250,
  radius: 100, magnification: 1.25, pixelRatio: devicePixelRatio });
// Update the texture when backdrop content changes; render again when the lens moves.
glass.dispose(); // on component unmount
```

Serve this directory with `python -m http.server 8765 --bind 127.0.0.1` and open `index.html`.
The example supports pointer drag and keyboard arrows. `core.mjs` contains renderer-independent
source mapping, critical springs and material-feedback math. Its numbers are authored, not an Apple API.

Run `npm test` for the numerical contract and timestamped interaction tests. `browser-test.html` compiles and tests the
real GLSL shader, reads pixels, resizes, simulates context loss and checks cleanup. Serve it over HTTP;
file URLs have different origin rules. A test that remains `RUNNING` is not a pass.

For the full hosted browser suite, install this package and the sibling `ports/skia` package, then
run `npm run test:browser`. It also exercises the production Skia painter at `/skia/`; that separate
example uses CanvasKit, while this module remains dependency-free at runtime.

Canvas dimensions are CSS pixels; `pixelRatio` controls backing resolution (1–4). The source fills
the canvas; supply matching aspect ratio to avoid stretching. Positions have a top-left origin.
Upload a new image only when it changes, not on every pointer event. Animated video must be uploaded
by its host per frame. WebGL contexts are owned by the adapter and must not be reused by another renderer.

The adapter restores its resources after context loss. If WebGL is unavailable, render a normal readable
surface, optionally with CSS `backdrop-filter`; that is a fallback, not optical equivalence. DOM controls
and their accessible names, focus and hit targets should remain ordinary HTML above the drawing. Respect
`prefers-reduced-motion` before applying the optional feedback math. For framework lifecycle examples,
source security and other native backends see [the porting guide](../../docs/porting.md).

`interaction.mjs` exports `createCalmInteraction` for whole-surface feedback. Feed monotonic seconds
and cumulative pointer displacement, apply its centred matrix to the material, and leave labels and
hit targets fixed. It includes bounded pull, independent press/release, stale-frame handling, resize,
viewport containment and reduced motion. It owns no events or timers and does not implement selector
travel or illumination. The [portable feedback guide](../../docs/porting.md#portable-calm-feedback)
defines the API; the sibling Skia browser example uses it with the production renderer.
For the Compose Calm bar role use `pressScale: 1.05, pressGrowth: 0, pullShape: 'area-preserving'`.
Press amplitude remains independent of the coupled narrowing/stretch model. Defaults remain `1.03` and
`2` logical pixels per edge for general surfaces. A host still owns selector travel and navigation.
`pullShape` defaults to `'adaptive'`, preserving the large-card extension cap. The area-preserving
option bounds log strain at 0.047 before viewport constraints and leaves the material centre fixed.

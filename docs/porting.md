# Porting LiquidGlass

The goal is reusable rendering, independent of the product around it. **A renderer still needs access
to its backdrop.** No library can silently read protected content or another application's pixels.
Separate the material, motion and host integration instead of duplicating a sample app.

## Available paths

| Host | Integration today | Status |
| --- | --- | --- |
| Android Compose / Compose Desktop | `GlassScene` or explicit state/modifiers | Main implementation; shader and interaction suites |
| Android Views | Place a ComposeView over a Compose-recorded backdrop, or supply an independent renderer | Bridge work required for a View-owned backdrop; no transparent capture of arbitrary Views |
| React, Vue, Svelte, Angular, vanilla HTML | ES module with a supplied canvas/image/video texture | Continuous lens preview in `ports/web`; not the full measured material |
| CanvasKit / another Skia RuntimeEffect host | Generated production material, content and aperture shaders in `ports/skia` | CanvasKit software rendering verified against JVM pixels; host capture/input remain explicit |
| Electron, Tauri, browser WebViews | Same web module, where WebGL and origin-clean textures are available | Host lifecycle/permissions must be tested |
| SwiftUI, Flutter, React Native, Qt, Unity, native desktop | Implement the renderer contract below or embed a supported surface | No native adapter shipped yet; not a claim of drop-in support |

The [web preview source](https://github.com/shayann07/liquidglass/tree/main/ports/web) has no runtime
package dependency. Copy the modules with their Apache-2.0 notices, or vendor them at a pinned revision.
The package is intentionally unpublished while its API is experimental.

For the production shader path, the [Skia adapter](https://github.com/shayann07/liquidglass/tree/main/ports/skia)
exports the evaluated Kotlin shader strings and binds uniforms by reflected name/offset. A JVM test
guards against source drift. CanvasKit tests compile every pass and compare the four material profiles
with independent JVM reference pixels. This avoids rewriting the material for every Skia-based framework.
It adds the CanvasKit WASM runtime; the smaller WebGL lens preview remains dependency-free.

For a simple production-shader lens, `createGlassPainter(CanvasKit, sources)` compiles once and
owns uniform binding and temporary resources. Call `setSource(backdropImage)`, then
`drawLens(canvas, {x, y, width, magnification})` for each lens. The source remains caller-owned;
`dispose()` releases the painter. The [executable terminal example](https://github.com/shayann07/liquidglass/blob/main/ports/skia/example.mjs)
uses this exact API. This high-level path renders clear magnifiers; custom material/ink/compositor
integration still uses the lower-level adapter.
Its browser example demonstrates an explicit Skia WebGL surface, keyboard/pointer controls and context
recreation. Serve it locally with `node ports/web/serve.mjs`, then open `/skia/`; install the pinned
CanvasKit dependency in `ports/skia` first. No framework wrapper or external runtime service is needed.

The raw shaders also work as a starting point for C++ or other bindings of
[Skia Runtime Effects](https://skia.org/docs/user/sksl/). A shared shader is not a complete host adapter:
backdrop capture, blur-strip construction, compositing, gestures, accessibility and GPU lifecycle still
belong to the integration. No arbitrary-view capture or drop-in support for untested frameworks is claimed.

## The small contract

1. **Record the backdrop once.** Supply an opaque texture for the region behind glass, with a declared
   top-left origin, pixel density and colour space. Exclude the glass overlays to avoid recursive feedback.
2. **Supply geometry in one frame.** A surface has local dimensions, shape, a transform into the backdrop
   and padding for displaced samples. Keep hit testing and ordinary text in layout coordinates.
3. **Evaluate material and ink consistently.** A standalone lens uses the same source map for both.
   A navigation selector has a separate role-specific model; do not substitute it for the magnifier.
4. **Compose premultiplied output once.** Apply coverage once after combining material and optional
   refracted foreground. Double masking creates dark rims. Document alpha and colour conversions.
5. **Feed timestamped input.** Keep press, motion, strain and navigation selection separate. Cancelled
   gestures must recover without selecting. Bound targets before spring integration, including huge drags.
6. **Own lifecycle and accessibility.** Release textures/listeners, recreate resources after context loss,
   respect reduced motion, keep text readable with reduced transparency, and preserve semantic controls.

The Compose implementation records a padded layer and uses SkSL/AGSL; the web preview accepts a caller-owned
texture and draws the continuous lens in GLSL. Browser texture uploads follow origin/CORS restrictions:
see [MDN's texture guide](https://developer.mozilla.org/en-US/docs/Web/API/WebGL_API/Tutorial/Using_textures_in_WebGL).
Resources must be rebuilt after [WebGL context restoration](https://developer.mozilla.org/en-US/docs/Web/API/HTMLCanvasElement/webglcontextrestored_event).

## Web integration

```js
import { createGlassRenderer } from './liquidglass.mjs';
const renderer = createGlassRenderer(outputCanvas);
renderer.setSource(backdropCanvas);
renderer.render({
  width: 800, height: 600, x: 400, y: 300,
  radius: 100, magnification: 1.25, pixelRatio: window.devicePixelRatio,
});
// Upload again when the backdrop changes; movement only needs render().
// On unmount:
renderer.dispose();
```

Coordinates are CSS pixels; `pixelRatio` controls the backing buffer (1–4). Supply a matching source
aspect ratio. Keep DOM text/buttons above the canvas for accessibility. For arbitrary DOM content,
CSS `backdrop-filter` is an available visual fallback; it does not implement this refractive source map.
Do not scrape screenshots of the user's browser or claim this fallback is the measured material.

React can create/dispose the renderer in `useEffect`; Vue in `onMounted`/`onUnmounted`; Svelte in `onMount`
with its returned cleanup. Keep the renderer outside render/template evaluation. None requires a dedicated
framework wrapper. The example's keyboard and pointer handlers are ordinary browser events.

## Portable lens mathematics

For a point `p` centred on an ellipse with half sizes `h`, let:

```text
r² = clamp(dot(p/h, p/h), 0, 1)
m  = 1 - 1 / centreMagnification
source(p) = p × (1 - m × (1-r²)²)
```

The supported magnification range is 1–2.5. The map equals identity at the ellipse boundary, with
identity first derivative there. Its radial derivative is positive throughout that range: no reversed
or skipped annulus. This is an authored continuous magnifier, not a recovered Apple lens prescription.
The source map is shared by the production material/ink shaders and ported to the ES module.

`core.mjs` also exposes an exact critical spring step and settled calm deformation math in logical pixels.
It does not own pointer arbitration, animation scheduling or viewport containment. Hosts must implement
those contracts; the Compose modifier already does. This separation makes native ports possible without
requiring Compose, but those ports still need implementation and verification.

## Acceptance for a new backend

Run the same ramp/line/text targets at multiple densities and sizes. Verify source orientation, alpha,
centre gain, boundary continuity and foreground registration. Test all four screen edges, diagonals,
reversal, cancellation, resize and recovery. Report rendering and presented-frame performance separately.
Capture only the app under test, label the backend and publish the exact source revision. Do not call a
backend supported until an executable example and its renderer/gesture tests pass on that backend.

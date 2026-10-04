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
uses this exact API. For a rounded in-app card, the same painter provides:

```js
glass.drawSurface(canvas, { x: 40, y: 240, width: 260, height: 88, radius: 22 },
  { dark: true, tintAmount: 50 });
```

It owns the fine/wide kernel setup and maps the production `GlassStyle.inApp` parameters, checked
against Kotlin-generated fixtures. The wide tone image is built once per source/density/canvas and
reused while controls move; it is released on replacement or disposal. A native-canvas identity check
prevents repeated JavaScript wrappers from causing a blur rebuild each frame. No CPU readback is used.
`density` scales dp material constants; geometry stays in device pixels. Radius can be one value or
four corner values. Host preferences map to `reducedTransparency` and `increasedContrast` (0–1).
See the [surface API](https://github.com/shayann07/liquidglass/blob/main/ports/skia/README.md#draw-a-custom-material)
for all defaults, memory cost and remaining host responsibilities. Refracted foreground, navigation,
arbitrary path fields and other material roles still use the lower-level adapter.
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

## Combining refracted ink with glass

For a selector that refracts foreground, keep these inputs premultiplied and in the same local,
padded pixel coordinates: opaque material `B1`, selected ink `(c1, a1)`, and aperture coverage `m`.
The final output must be `(m * (c1 + (1-a1) * B1), m)`. Source-over that output onto the ordinary
page once. Masking the ink and material separately creates a cross term at the edge.

The production endpoint shader now accepts three children: `endpoint`, `ink`, and `field`. Bind
all three even when a branch does not sample them. With `uInkStrip = 0`, `endpoint` is the already
composed opaque `C1`; this preserves the original path. With `uInkStrip > 0`, `endpoint` supplies
opaque `B1` and `ink` supplies unmasked premultiplied ink at the **same coordinates**. Composition
and coverage happen in float before one output write. `uInkStrip` also gives the visible padded
height; rows at or below it return transparent. A host with separate image shaders can bind them
directly without building a packed texture.

Compose Desktop packs these inputs into two vertical strips in one recording. Its image-filter
graph offsets the ink input by the negative padded height. That offset must be declared in the
graph: the zero-radius [Skia RuntimeShader filter](https://api.skia.org/classSkImageFilters.html)
assumes same-coordinate sampling. An offset hidden inside the shader lost selected labels near
the native window edge, despite passing uncropped software tests. The corrected graph preserves
offscreen ink. Do not copy the rejected shader-offset approach into a host adapter.

`GlassTabBarStyle.Calm()` selects this path on Desktop. Android's current Java RenderEffect API
exposes one dynamic runtime-shader input, so it retains the original compositor and its known
rounding limitation. This is explicit backend scope, not a claim of equal precision on Android.
The packed desktop recording has twice the endpoint-layer area, before backend allocation overhead;
the material and ink layers themselves are unchanged. See the [research record](research/atlas-stabilization.md)
for the measured error and native redraw evidence. The simpler magnifier painter does not use this path.

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
The timestamped `createCalmInteraction` controller adds animated press/pull, viewport containment,
cancellation, resizing and reduced motion without depending on a UI framework. It does not own pointer
arbitration, illumination or animation scheduling, and it is not the navigation-selector travel model.

## Portable calm feedback

```js
import { createCalmInteraction } from './interaction.mjs';

const motion = createCalmInteraction({
  width: 320, height: 72, radius: 36,
  x: 20, y: 20, viewportWidth: 360, viewportHeight: 112,
  reducedMotion: false, // supply the host's preference
});
const now = () => performance.now() / 1000;

motion.press(now());               // pointer-down, only once your host owns the gesture
motion.press(now(), deltaX, deltaY); // cumulative displacement since down, in logical pixels
const state = motion.sample(now()); // call from your host's animation loop
motion.release(now());             // pointer-up OR pointer-cancel; no navigation selection
```

The optional `pressScale` (default `1.03`, supported range `1…2`) and `pressGrowth` (default `2`,
nonnegative logical pixels per edge) tune only press amplitude. Positive growth caps each axis by
both limits; zero selects percentage-only growth. For the Compose Calm **navigation bar** role use
`pressScale: 1.05, pressGrowth: 0, pullShape: 'area-preserving'`. This gives 195.3px against the original 196±1px ordinary-held bar
at 186px rest height. Timing stays Calm; this does not add selector travel.
`pullShape` defaults to `'adaptive'`. `'area-preserving'` instead couples bounded growth and narrowing
in log space, matching the Compose navigation surface model. The saturated 834×186px fixture measures
835.5×204.7px, compared with about 834× 205–206px in the original prolonged drag. This is an authored
spatial fit; the original's upward visible-centre shift remains unmatched. Viewport constraints may
reduce the area-preserving transform when necessary to contain the surface.
Viewport limits constrain the press target before spring interpolation, preserving gradual feedback
near an edge. Defaults for generic buttons and cards are unchanged.

`deltaX` and `deltaY` come from your gesture handler; these lines illustrate separate events, not a
complete event loop. Times are monotonic **seconds**, with one clock for input and drawing. Browser
timestamps are normally milliseconds: divide by 1000. A queued frame older than the latest input is
ignored; out-of-order input is rejected. Advance the old target before changing it, so extra redraws
cannot change the spring response. See [requestAnimationFrame timing](https://developer.mozilla.org/en-US/docs/Web/API/Window/requestAnimationFrame).

`state.matrix` is the row-major 2×2 `[a, b, c, d]` transform: `x' = a*x + b*y`, `y' = c*x + d*y`.
Apply it **about the original glass centre**, to the material drawing only. Keep layout, hit testing,
ordinary labels and accessibility outside it. For a CanvasKit canvas:

```js
const [a, b, c, d] = state.matrix;
canvas.save();
try {
  canvas.translate(centreX, centreY);
  canvas.concat([a, b, 0, c, d, 0, 0, 0, 1]);
  canvas.translate(-centreX, -centreY);
  glass.drawLens(canvas, bounds);
} finally {
  canvas.restore();
}
// Draw ordinary labels here, outside the material transform.
```

Keep the matrix and geometry in the same logical coordinate system. Apply device density to the page
as a whole. The [Skia browser example](https://github.com/shayann07/liquidglass/blob/main/ports/skia/demo.mjs)
contains the complete pointer, keyboard, redraw and preference lifecycle using this controller.
The canvas-transform API is provided by [CanvasKit](https://skia.org/docs/user/modules/canvaskit/).

| Method / result | Contract |
| --- | --- |
| `press(time, dx=0, dy=0)` | Press and retarget bounded cumulative pull; returns current state without snapping |
| `release(time)` | Release or cancel; recover from current geometry and velocity |
| `sample(time)` | Advance analytically to the frame time; returns fresh state |
| `resize(partialGeometry, time)` | Update dimensions/position/viewport; cancel the gesture and reset geometry |
| `setReducedMotion(enabled, time)` | Cancel and reset; reduced motion returns identity geometry |
| `state.active` | Request another frame while settling; new input must wake your loop again |
| `state.held`, `state.press` | Gesture ownership and geometric press amount; separate from glow or selection |

Supply both viewport dimensions for edge containment. Initially contained surfaces remain contained
under press and diagonal pull; initially offscreen/oversized layouts keep the host's clipping policy.
An enormous over-pull is bounded **before** spring integration, so it cannot store invisible travel
that delays recovery. Wide bars keep their axis level, large surfaces retain the 2-pixel drag-extension
cap, and smaller controls retain more deformation. These are authored Calm choices shared with the
Compose implementation, not measured Apple timing or a recovered native animation implementation.

The controller owns no listeners, timers or GPU resources. Stop your frame loop and remove host event
listeners on unmount. Cancel on lost pointer capture and scrolling arbitration. A browser host should
observe changes to `prefers-reduced-motion`, not only read it once. Illumination, reduced transparency,
semantic navigation and platform-specific accessible controls remain separate host responsibilities.

## Acceptance for a new backend

Run the same ramp/line/text targets at multiple densities and sizes. Verify source orientation, alpha,
centre gain, boundary continuity and foreground registration. Test all four screen edges, diagonals,
reversal, cancellation, resize and recovery. Report rendering and presented-frame performance separately.
Capture only the app under test, label the backend and publish the exact source revision. Do not call a
backend supported until an executable example and its renderer/gesture tests pass on that backend.

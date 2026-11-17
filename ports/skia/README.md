# Skia adapter preview

Use the **production material, content and aperture shaders without Compose**. The raw `.sksl`
files can be compiled by a compatible Skia RuntimeEffect host. The included JavaScript adapter
uses CanvasKit 0.42.0 and runs in Node or a browser. Other Skia bindings require host integration;
this is not a ready-made Flutter, SwiftUI, Unity or React Native component.

The source is Apache-2.0; included LICENSE/NOTICE copies match the repository root. CanvasKit is a separate BSD-3-Clause
dependency with its own bundled third-party licenses; preserve its notices when redistributing.
No hosted service, subscription or runtime network request is required. Vendor the pinned files.

## Install and verify

```sh
cd ports/skia
npm ci --ignore-scripts
npm test
npm run example # writes build/example.png with the same high-level API below
```

The terminal tests compile all three passes, render the real material on a software Skia surface,
test lens continuity/alpha and compare **all four profiles** with independent JVM Skia pixels.
This verifies the supplied source/adapter combination, not browser GPU performance or iOS parity.

The interactive browser example is `index.html` with `demo.mjs`. From the repository root:

```sh
npm ci --ignore-scripts --prefix ports/skia
node ports/web/serve.mjs
# Open http://127.0.0.1:8766/skia/
```

It loads the local pinned CanvasKit runtime, uses an explicit WebGL-backed Skia surface, and demonstrates
drag/keyboard movement, an anchored calm card, backdrop replacement and graphics-context recreation. The loopback preview
server exposes only the example files and the two required CanvasKit runtime files. CI exercises the
example through Chromium; it is not a browser performance or cross-browser compatibility guarantee.
The card uses the dependency-free `createCalmInteraction` controller from `ports/web/interaction.mjs`.
Press Space or hold and pull the card; its material deforms while the label stays fixed. System reduced
motion disables geometry immediately. This is separate whole-surface feedback, not selector travel.
See the [portable feedback API](../../docs/porting.md#portable-calm-feedback) for other hosts.

## Start with one scene

`createGlassScene` combines the painter and calm feedback controller. Add surfaces once; the scene
applies their drawing transforms and tells your host when another animation frame is needed.
Use it with any UI framework that can supply a CanvasKit canvas and an opaque source image:

```js
import { createGlassScene } from 'liquidglass-skia-preview';

const scene = createGlassScene(CanvasKit, { width: 800, height: 600, pixelRatio: 2 });
scene.setSource(backdropImage); // 1600×1200 opaque image, borrowed from the host
const card = scene.addSurface({ x: 24, y: 440, width: 320, height: 96, radius: 24 }, { dark: true });
const lens = scene.addLens({ x: 240, y: 100, width: 180, magnification: 1.25 });

// From your existing input handlers: monotonic seconds, total displacement from pointer-down.
card.press(time, totalDx, totalDy);
card.release(time); // also use on cancellation or when scrolling wins the gesture

// From your host's frame callback:
const animateAgain = scene.draw(canvas, time);
drawYourLabelsAndControls(canvas); // stay in the host's layout; not transformed by the glass
surface.flush();
// Schedule another frame while animateAgain is true, or when source/input/layout changes.

card.setBounds({ x: 40, width: 360 }, time); // layout change cancels stale pointer coordinates
card.setMaterial({ dark: false, increasedContrast: 1 });
lens.setBounds({ magnification: 1.5 }, time);
scene.setReducedMotion(prefersReducedMotion, time);
scene.dispose(); // on unmount/context loss, before the host deletes its source/surface
```

Initialize CanvasKit and the host surface using its [official setup](https://skia.org/docs/user/modules/canvaskit/).
For this preview, install the checked-out directory as a local dependency, or import `./index.mjs`.
Nothing is published to npm. The root scene, painter and interaction exports include TypeScript declarations,
checked against strict consumer examples. `./scene` and `./interaction` also provide typed subpath imports.

Scene bounds and displacement use **logical pixels**. Supply a canvas/image at `pixelRatio` times those
dimensions (ratio in `(0, 4]`); the scene converts material coordinates and density exactly once.
`scene.resize({width, height, pixelRatio}, time)` updates containment and cancels existing gestures.
Resize/recreate your backing surface and replace the source yourself. The image is sampled at its native
size; the scene does not stretch it to cover a differently sized canvas. Source replacement requires
`scene.setSource(newImage)` before deleting the previous image.

`scene.draw` paints the source followed by glass in insertion order; `{drawBackdrop: false}` skips the
source draw when your host already painted it. Glass samples the supplied backdrop, excluding other
glass overlays. Labels, layout, hit testing, semantics and gesture arbitration remain in the host.
It owns no DOM listeners, automatic screenshot capture, timers or render surface; a navigation layer
reports selection through `onSelect` and never owns it.
`layer.bounds` and `layer.feedback` are defensive snapshots; feedback never writes layout. Use `remove()`
when a layer leaves your UI. Dispose the scene once its graphics context is lost and recreate it on
restoration, as the browser example does.

The package bundles the existing portable feedback source, with a drift check rather than a second
maintained implementation. After changing `ports/web/core.mjs` or `interaction.mjs`, run
`npm run generate:interaction` here. An isolation test copies only package runtime files to a temporary
directory and renders without the sibling web checkout. LICENSE/NOTICE are included and checked against
the repository copies. This is a reusable CanvasKit integration, not a native binding for every framework.

## Navigation: the Calm selector

`scene.addNavigation(bounds, {count, selected, onSelect})` adds a Calm navigation bar: the bar
glass, the selector that travels on a tap, lifts into a lens on a hold, follows a drag and lands
against the bar's end, and the gesture protocol of the Compose `GlassTabBar` (touch-down aims, a
hold or horizontal slop starts a drag, a vertical scroll before the hold cancels, a flick carries one
tab further). Feed `down`, `move`, `up` or `cancel` with logical page coordinates and monotonic
seconds; `onSelect(index)` reports a tap or committed drag and the host answers with `select(index)`.
`layer.selector` exposes the body's extents, formation and pose for drawing ink or labels.

```js
const nav = scene.addNavigation({x: 24, y: 520, width: 300, height: 62},
  {count: 3, onSelect: index => { route(index); nav.select(index); }});
canvas.addEventListener('pointerdown', e => nav.down(e.offsetX, e.offsetY, e.timeStamp / 1000));
```

`calmSelectorBar({..., slotWidth})` lays the tabs on equal slots of that width centred in the bar,
as Compose's `GlassTabBarStyle.slotWidth` does: the end tabs close up until their pill sits
`pillInset` inside the bar's end. Draw your items at `centreOf(bar, i)`.

`clear: true` on the bar material (`calmBarMaterial({clear})`, `painter.drawCalmBar(..., {clear})`,
or `material: {clear: true}` on `addNavigation`) draws Compose's `GlassTabBarStyle.CalmClear()` bar:
the clearer glass of WhatsApp's and the App Store's dark iOS bars (black at 0.10 instead of 0.41,
same lift, legibility 2 and a 7 dp blur; light appearance unchanged). Pass the same bar as `through` so the lens follows it.

`drawSelector` follows Compose's held lens fully (18 October): its rim band is the material's
`bodyBandScale` share of the body's own (0.3 in Calm, so the bar's outline does not show inside it as a
second capsule), and light Calm's lens casts its contact shadow (tight, 5 dp down: on the page under it
and, at half strength, where it overhangs the page); `shadow: false` skips it. `lensFuse({bar, button,
formation, lensFusion})` gives `drawSelector(..., {fuse})` a held lens's own neck to the free button
(Compose's `GlassTabBarAccessory.lensFusion`; the sample uses 12 dp), null until the lens has formed.
`navigation.state().itemFollow` is how much of a press transform the host applies to the bar its items
should share: 0 on a tap, 1 once a hold has formed the lens, as Compose's `itemsFollowBarPress = false`.
Blurs follow Compose's radius-to-sigma conversion (`0.577 r + 0.5`).

A free button beside the bar (Compose's `GlassTabBarAccessory.link`, the Phone search button) fuses
with it: `accessoryFuse({bar: {width, height, pressX, pressY}, button: {x, y, width, height},
transform: freeButton.sample(t), smoothing})` gives `drawCalmBar(..., {fuse})` the rect the bar's
outline reaches for (button rect bar-local, device px), so pressing the button closes the gap through a
neck and pulling it away breaks it. Multiply `smoothing` by `1 - accessoryYield(formation, lensEdge,
barWidth, barHeight)` so the neck gives way as a formed lens arrives at that end. Checked against a
Kotlin-exported fixture (`fixtures/accessory-fuse.json`).

The motion is a line-by-line port of the Compose pose controller (`selector.mjs`:
`CalmSelectorController`, `calmSelectorBar`, `centreOf`, `nearestTab`, `createCalmNavigation`), checked frame by frame against
trajectories the Kotlin controller exports (`fixtures/selector-calm.json`: taps, a held throw into
the wall, held reversals, a stationary hold; 0.05 px). The navigation layer draws its bar in the
Compose Calm bar material (`painter.drawCalmBar`; it lifts for any touch as dark Calm's does, from
the navigation state's `touch`) and the selector through the production shader's
pose body in the Compose Calm selector material (`painter.drawSelector`, `calmSelectorMaterial`:
the production pill, lens and bar styles exported by the Kotlin tests and blended by the same rules,
checked against 96 Kotlin-computed uniform sets). Like Compose, the lens looks through the bar:
`drawSelector(canvas, bar, state, {through})` takes the page with the bar's glass drawn over it,
which the scene layer renders for you (one extra page-sized pass per navigation layer and frame).
For the selected ink, render your items in their selected appearance into an image (transparent
ground, device px, the bar's origin) and pass it with `layer.setSelectedInk(image)` or
`painter.drawSelectorInk(...)`: it is drawn through the production content pass, so it shows only
under the selector, refracted and folded with it. Labels and accessibility stay with the host. [Evidence](../../review/atlas/ports-selector-2026-10-14/README.md).

## A free button (the Phone search button)

`createFreeButton({size, density})` is Compose's `GlassInteraction.Pullable` with `GlassPullShape.Free`:
pressed, the button grows 8 pt a side and lights up; pulled, it leaves its place with the finger,
stretching along the pull; released, it springs back and lands squashed against its resting edge.
Feed it `down(t)`, `move(t, dx, dy)` (total travel since down) and `up(t)` in seconds, and draw your
button with `sample(t)`: scale by `pressScale`, stretch by `along` / `across` about `angle` (degrees),
offset by `tx` / `ty`, and lift the material by `lift`. Its trajectories match Compose's frame by frame
(`fixtures/free-button.json`, exported by the Kotlin tests). The bar's outline does not follow it here.

## Draw a lens without managing uniforms

Initialize CanvasKit using its [official setup](https://skia.org/docs/user/modules/canvaskit/).
The painter includes the production shaders: no shader file loading, fetch setup or bundler plugin
is required. It works on any CanvasKit canvas, independent
of React, Vue, Compose or another UI framework:

```js
import { createGlassPainter } from './painter.mjs';

const glass = createGlassPainter(CanvasKit);
glass.setSource(backdropImage); // a caller-owned, opaque CanvasKit Image
const canvas = surface.getCanvas();
canvas.drawImage(backdropImage, 0, 0);
glass.drawLens(canvas, { x: 80, y: 100, width: 180, magnification: 1.25 });
glass.drawLens(canvas, { x: 320, y: 140, width: 200, height: 120 });
surface.flush();
// On shutdown:
glass.dispose();
backdropImage.delete();
```

`x` and `y` are the lens's top-left position in the page, not its centre. Every coordinate is in
device pixels. The source and destination share one page coordinate system: scale the page and glass
together when drawing in logical units. The painter preserves the canvas's transform and clipping.
It compiles once, draws multiple lenses, validates input and releases its temporary shaders after each
draw. It never deletes the borrowed source. After content changes, call `setSource(newImage)` before
deleting the old image. Moving a lens does not require recapturing an unchanged backdrop.

Advanced hosts can still pass `{material, content, endpoint}` as the second argument to supply
shader strings explicitly. The default strings are generated from `shaders/*.sksl`; `npm test`
rejects a stale bundle, and the JVM test verifies those exports against Kotlin's production strings.
After regenerating the production exports, run `npm run generate:shaders` here and commit both.

The package also exports `createGlassPainter`. For a local dependency installed from this
directory, use `import { createGlassPainter } from 'liquidglass-skia-preview'`. The preview remains
private/unpublished; no npm registry package or remote CDN is required or implied. `./renderer`
exposes the lower-level bindings and `./shaders` exports the immutable `glassShaderSources` object.
CanvasKit's runtime/WASM setup, source image and host canvas still belong to the application.

The painter uses the minimal clear configuration below: a circular lens for square bounds, with an
elliptical source map inside capsule coverage for unequal dimensions. By default it does not add the Compose
clear-lens preset's decorative lighting or run its gesture controller.

Over flat or low-detail content that bare lens is hard to see: nothing marks where it is. Ask for the Compose
clear-lens preset (`GlassStyle.clearLens`, what `Glass.Lens` draws) with `lighting: true`: a rim highlight, a thin
white tint (1.5 %) and a contact shadow under the lens, with no uniforms bound by hand. It is an option, off by
default, so the bare lens is unchanged:

```js
glass.drawLens(canvas, { x: 80, y: 100, width: 180, magnification: 1.4 }, { lighting: true });
// options: lighting (false), shadow (true: the contact shadow), density (1: px per dp, scales the dp terms),
// light ([-0.35, -0.94]: toward the light, y up negative)
const lens = scene.addLens({ x: 80, y: 100, width: 180 }, { lighting: true }); // logical px; the scene applies pixelRatio
lens.setMaterial({ shadow: false });
```

The preset is authored in Compose (its KDoc: "Authored, not an Apple calibration"), not measured, and the port makes
no iOS claim for it. Over a flat grey 128 backdrop the rim reads up to +64 levels at the top and +22 at the bottom
and the interior stays within 4 levels of the page; over black +42 and +15; over pure white it is invisible (white
light on white, and a 2 % shadow), as in Compose. The values are read from `GlassStyle.clearLens`, not exported
from Kotlin: `lens.test.mjs` checks the authored numbers against that source where the repository is present.

In a browser, `CanvasKit.MakeImageFromCanvasImageSource(backdropCanvas)` can create the source from
an application-owned canvas. Origin restrictions still apply. Keep interactive DOM controls above the
canvas, or use your host's accessible controls; the painter only renders. Recreate it with the source
after GPU context loss, as `demo.mjs` illustrates. Terminal tests verify software Skia. Hosted browser
checks are recorded separately in the review record; headless WebGL does not establish physical GPU
performance. The separate `ports/web` renderer remains the smaller dependency-free lens preview.

## Draw a custom material

For a rounded in-app surface, use the same painter without binding shader uniforms:

```js
glass.drawSurface(canvas, {
  x: 40, y: 240, width: 260, height: 88, radius: 22,
}, { dark: true, tintAmount: 50 });
```

This follows `GlassStyle.inApp`: the production measured profile, fine detail kernel, wide tone
kernel, per-appearance tint and edge lighting. `radius` is one pixel value or four values in
top-left, top-right, bottom-right, bottom-left order. Each is limited to the shape's inradius.
Options are `dark` (default false), `tintAmount` (0–100, default 50), `density` (positive, default 1),
`materialize`, `reducedTransparency` and `increasedContrast` (each 0–1; defaults 1, 0, 0), and
`tint` ([r, g, b, a], sRGB 0–1; default none): Compose's `Glass.tint(color)`, which replaces the tint
colour and opacity (quantized to 8 bits, as Compose's Color is) and keeps the dark lift. `light`
([x, y], toward the light, y up negative) defaults to Compose's `GlassLight.Default` (-0.35, -0.94),
as `Glass.Regular` binds it; [0, -1] is iOS 27's measured overhead light. The
`<liquid-glass>` web component's canvas engine uses it ([ports/npm](../npm/README.md)).
Geometry remains in device pixels; density scales the material's dp constants. Feed accessibility
preferences from the host. Ordinary text stays outside the material draw, as in the browser example.

The painter constructs the two-kernel backdrop. It caches one quarter-resolution Gaussian tone
image on a surface compatible with the target canvas. Changing the source, density or native canvas
rebuilds it; moving/resizing a panel and changing tint reuse it. Calling `setSource` invalidates it.
The cache owns about one sixteenth of a full-size RGBA image plus backend overhead, retains only
one version, and is released on `dispose`. No CPU pixel readback occurs in the drawing path.
Repeated `getCanvas()` wrappers are recognized as the same native canvas. Use one painter per live
graphics context and recreate it after context loss.

The tone sampler extends source-edge colors before blurring. Even a one-pixel opaque source stays
opaque; small images and dimensions not divisible by four do not introduce a dark edge. This is
clamped sampling, not capture of content beyond your supplied image.

Parameter fixtures generated from the Kotlin factory guard drift across both appearances and nine
tint settings. Pixel tests check material plateaus, corners, source replacement, coordinates, and
the blur's attenuation of a sinusoidal signal against the independent Gaussian equation. These are
adapter checks, not a claim that all Compose host behaviour is reproduced. Arbitrary path fields,
system-backdrop roles, automatic inversion, refracted foreground and navigation still use host or
lower-level integration. The supplied opaque backdrop must exclude the glass itself.

### Lower-level material control

Initialize CanvasKit using its [official setup](https://skia.org/docs/user/modules/canvaskit/).
Load `shaders/material.sksl`, `content.sksl` and `endpoint.sksl` as text using your build system,
filesystem or same-origin fetch. Then:

```js
import { compileGlassEffects, clearMaterialUniforms } from './renderer.mjs';

const effects = compileGlassEffects(CanvasKit, { material, content, endpoint });
const uniforms = clearMaterialUniforms(effects, {
  width: 160, height: 160, pad: 32, magnification: 1.25,
});
// backdropShader samples an OPAQUE 224 × 224 padded source, origin at its top left.
// The visible 160 × 160 circle starts at (32, 32). The unused field child must be bound.
const shader = effects.shader('material', uniforms, {
  content: backdropShader, field: backdropShader,
});
const paint = new CanvasKit.Paint();
try {
  paint.setShader(shader);
  canvas.drawPaint(paint); // output is premultiplied and already has shape coverage
} finally {
  paint.delete();
  shader.delete();
}
// Reuse effects across surfaces. On shutdown, after deleting any child shaders/images:
effects.dispose();
```

The helpers accept named values and inspect the compiled shader's uniform offsets. Unknown,
missing, incorrectly sized and nonfinite uniforms fail before rendering. `layout(pass)` exposes
that ABI. Dimensions and padding are **device pixels**; convert logical units in your host.

Reflection happens once per compiled pass. `layout` returns defensive copies; modifying one
cannot change later bindings. Run `node benchmark-bindings.mjs` to measure CPU binding overhead.
An optional file-path argument compares another compatible `renderer.mjs` in alternating batches
with the same runtime and inputs. This benchmark does not draw or measure frame rate; do not use
its result as a GPU performance claim or a timing-sensitive CI assertion.
Create effects once, reuse textures until the backdrop changes, and rebuild GPU resources after
context/device loss. Returned shaders and caller-owned sources must be deleted by their owner.

`clearMaterialUniforms` configures a simple clear lens. It does **not** recreate the Compose
toolbar style; `clearLensUniforms(effects, {width, height, magnification, density, light})` (from `material.mjs`)
is the Compose clear-lens preset's uniforms behind `drawLens(..., {lighting: true})`. Advanced callers can set the production uniforms documented in each shader,
including refraction, blur, shape fields and pose geometry. Provide a correctly constructed
wide-blur strip before enabling `uWideStrip`; setting a number does not create that texture.

For refracted foreground, execute the material/content/endpoint passes following the
[endpoint contract](../../docs/porting.md#combining-refracted-ink-with-glass). The endpoint shader
requires `endpoint`, `ink` and `field` children. With `uInkStrip > 0` (the padded visible height),
bind opaque material and unmasked ink separately at matching local coordinates; the shader composes
and masks them before one output write. With `uInkStrip = 0`, pass already-composed `C1` through
`endpoint` and still bind the unused `ink` child. This preserves legacy rounding. Ordinary controls and
accessible labels remain in the host UI. The adapter does not capture DOM/native views, arbitrate
gestures, compute the navigation controller, or own your platform's colour management.

## Regenerate, do not hand-edit

The checked-in shaders are evaluated Kotlin production constants, including all shared source
maps. Their JVM test checks both source identity and reference pixels on every CI build.

```powershell
./tools/workspace.ps1 library :liquidglass:jvmTest --tests '*GlassShaderBundleTest' '-Pliquidglass.exportShaders=true'
./tools/workspace.ps1 library :liquidglass:jvmTest --tests '*GlassPortableMaterialSpecTest' '-Pliquidglass.exportShaders=true'
```

Review generated changes with their Kotlin source. The uniform ABI is experimental and may change
before a stable portable package is published. Native bindings can use the same `.sksl` sources via
[Skia Runtime Effects](https://skia.org/docs/user/sksl/); each binding must pass the shared fixtures
and its lifecycle/input tests before being advertised as supported.

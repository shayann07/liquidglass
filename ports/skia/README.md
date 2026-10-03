# Skia adapter preview

Use the **production material, content and aperture shaders without Compose**. The raw `.sksl`
files can be compiled by a compatible Skia RuntimeEffect host. The included JavaScript adapter
uses CanvasKit 0.42.0 and runs in Node or a browser. Other Skia bindings require host integration;
this is not a ready-made Flutter, SwiftUI, Unity or React Native component.

The source is Apache-2.0 under the root LICENSE/NOTICE. CanvasKit is a separate BSD-3-Clause
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

## Draw a lens without managing uniforms

Initialize CanvasKit using its [official setup](https://skia.org/docs/user/modules/canvaskit/), then
load the three files in `shaders/` as strings. The painter works on any CanvasKit canvas, independent
of React, Vue, Compose or another UI framework:

```js
import { createGlassPainter } from './painter.mjs';

const glass = createGlassPainter(CanvasKit, { material, content, endpoint });
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
The painter uses the minimal clear configuration below: a circular lens for square bounds, with an
elliptical source map inside capsule coverage for unequal dimensions. It does not add the Compose
clear-lens preset's decorative lighting or run its gesture controller.

In a browser, `CanvasKit.MakeImageFromCanvasImageSource(backdropCanvas)` can create the source from
an application-owned canvas. Origin restrictions still apply. Keep interactive DOM controls above the
canvas, or use your host's accessible controls; the painter only renders. Recreate it with the source
after GPU context loss, as `demo.mjs` illustrates. Terminal tests verify software Skia. Hosted browser
checks are recorded separately in the review record; headless WebGL does not establish physical GPU
performance. The separate `ports/web` renderer remains the smaller dependency-free lens preview.

## Draw a custom material

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
Create effects once, reuse textures until the backdrop changes, and rebuild GPU resources after
context/device loss. Returned shaders and caller-owned sources must be deleted by their owner.

`clearMaterialUniforms` configures a simple clear lens. It does **not** recreate the Compose
toolbar style. Advanced callers can set the production uniforms documented in each shader,
including refraction, blur, shape fields and pose geometry. Provide a correctly constructed
wide-blur strip before enabling `uWideStrip`; setting a number does not create that texture.

For refracted foreground, execute the material/content/endpoint passes following the
[endpoint contract](../../docs/porting.md#the-small-contract): build the opaque material endpoint,
draw unmasked premultiplied ink over it, and apply aperture coverage once. Ordinary controls and
accessible labels remain in the host UI. The adapter does not capture DOM/native views, arbitrate
gestures, compute the navigation controller, or own your platform's colour management.

## Regenerate, do not hand-edit

The checked-in shaders are evaluated Kotlin production constants, including all shared source
maps. Their JVM test checks both source identity and reference pixels on every CI build.

```powershell
./tools/workspace.ps1 library :liquidglass:jvmTest --tests '*GlassShaderBundleTest' '-Pliquidglass.exportShaders=true'
```

Review generated changes with their Kotlin source. The uniform ABI is experimental and may change
before a stable portable package is published. Native bindings can use the same `.sksl` sources via
[Skia Runtime Effects](https://skia.org/docs/user/sksl/); each binding must pass the shared fixtures
and its lifecycle/input tests before being advertised as supported.

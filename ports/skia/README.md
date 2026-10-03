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
```

The terminal tests compile all three passes, render the real material on a software Skia surface,
test lens continuity/alpha and compare **all four profiles** with independent JVM Skia pixels.
This verifies the supplied source/adapter combination, not browser GPU performance or iOS parity.

## Draw a material

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

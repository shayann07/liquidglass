# liquiglass

Liquid Glass for Flutter, using the shader the [liquidglass](https://github.com/shayann07/liquidglass)
Compose library runs, measured against iOS recordings. `GlassSurface` refracts whatever Flutter paints
behind it, live, every frame. Lists scroll under it and video plays under it, with no snapshots. It
needs Impeller, Flutter's renderer on Android. Elsewhere it draws a frosted stand-in. iOS is not a
target: on iOS 26 the system's own glass is the native choice.

![A light glass card and a dark glass bar refracting a scrolling list on a Pixel 7](https://raw.githubusercontent.com/shayann07/liquidglass/main/ports/flutter/doc/pixel7.png)

*Our capture: the example on a Pixel 7 (Android 17), Impeller on Vulkan.*

## Install

```bash
flutter pub add liquiglass
```

## Use

```dart
import 'package:liquiglass/liquiglass.dart';

Stack(children: [
  ListView(children: rows),                     // anything: it is refracted live
  Positioned(
    left: 24, right: 24, bottom: 24, height: 72,
    child: GlassSurface(
      radius: 36,
      dark: true,
      child: Row(children: icons),              // sharp, above the glass
    ),
  ),
])
```

| `GlassSurface` | Default | |
| :-- | :-- | :-- |
| `radius` | 20 | corner radius, logical pixels |
| `dark` | false | the dark material |
| `tintAmount` | 50 | 0 to 100 |
| `tint` | none | replaces the tint colour and opacity, as Compose's `Glass.tint(color)` |
| `light` | `Offset(0, -1)` | toward the light; straight overhead, as every measured resting iOS 27 rim is lit |
| `materialize` | 1 | glass materializes in and out rather than fading |
| `reducedTransparency`, `increasedContrast` | 0 | 0 to 1 |

Call `LiquidGlass.ensureInitialized()` in `main` to load the shader before the first frame. Until it
loads, glass draws the stand-in.

## How it works

- **The shader is generated.** `tool/generate_shader.mjs` translates
  `ports/skia/shaders/material.sksl`, the export of the Compose library's material shader, into the
  GLSL Flutter compiles, mechanically. The translation changes the types, the two texture reads and the
  entry point. `--check` fails when the copy drifts.
- **The backdrop is live.** Each `GlassSurface` paints a `BackdropFilterLayer` whose filter is
  `ImageFilter.shader` running that shader. The live backdrop is its input, clipped to the glass's
  rounded rect plus two pixels for the outer contour.
- **One part is approximated: the wide tone kernel.** Compose and the CanvasKit port read it from a
  quarter-scale, Gaussian-blurred copy of the backdrop. A backdrop filter has a single input, so here
  it is thirteen taps of a Gaussian with the same sigma. It sets the material's tone, never its detail.
- **The material's numbers are shared.** `lib/src/material.dart` is a port of the CanvasKit port's
  in-app material. `test/material_fixture.json`, generated from that JavaScript, pins every uniform.

## Checks

- `node tool/generate_shader.mjs --check` and `node tool/generate_fixture.mjs --check` check the
  generated files.
- `flutter test` checks three things:
  - the generated shader compiles with Flutter's shader compiler and loads;
  - the Dart uniforms equal the CanvasKit port's, to 1e-6, for three surfaces;
  - the widget lays out and paints without Impeller.
- The example (`example/`) runs the material on a Pixel 7 on Impeller's Vulkan backend, and on the
  Android 17 emulator on its OpenGL ES backend. See [the evidence](https://github.com/shayann07/liquidglass/blob/main/review/atlas/flutter-2026-10-19/README.md).

## Platforms

| Platform | Status |
| :-- | :-- |
| Android (Impeller, Vulkan and OpenGL ES) | live glass; checked on a Pixel 7 and the Android 17 emulator |
| Linux, macOS, Windows, web | compiles; draws the frosted stand-in unless Impeller runs; not checked on devices |
| iOS | not a target |

Apache-2.0. Not affiliated with Apple; "Liquid Glass" is used descriptively (see NOTICE).

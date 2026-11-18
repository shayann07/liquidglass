---
description: What LiquidGlass promises from 1.0 on - semantic versioning across every registry, the stable API on each stack, supported platforms and minimum versions, and how deprecation and visual changes are handled.
---

# Support and compatibility (since 1.0)

LiquidGlass 1.0 is the first release with a compatibility promise. This page says what is promised,
for which stacks, and what is deliberately outside it.

## Versioning

One version number covers every artifact, on every registry: Maven Central, npm, NuGet and pub.dev.
From 1.0.0 it follows [Semantic Versioning](https://semver.org):

- **Patch** (1.0.x): fixes. No API change, and no change to how a default looks beyond fixing a defect.
- **Minor** (1.x.0): additions, and refinements of the default material or motion (see
  [Visual changes](#visual-changes)). Existing code keeps compiling and linking.
- **Major** (2.0.0): removals and incompatible changes, each deprecated in at least one earlier minor
  release first, with the replacement named in the deprecation message.

## What is stable

| Stack | Artifacts | Stable surface |
| :-- | :-- | :-- |
| Compose Multiplatform | `dev.shayxo.liquidglass:liquidglass`, `:liquidglass-material3`, `:liquidglass-bom`; Gradle plugin `dev.shayxo.liquidglass` | Every public declaration, checked: the ABI is committed under each module's `api/` and CI fails on an unrecorded change (`checkKotlinAbi`). The plugin's `liquidGlassAdopt` task and its extension. |
| Web | npm `@shayxo.dev/liquidglass` | The `<liquid-glass>` element: its attributes, properties and events, and the module's typed exports (`index.d.mts`). |
| React Native | npm `@shayxo.dev/liquidglass-react-native` | `GlassSurface`, `GlassLens` and their props; `createGlassPainter`, `recordGlass` (`index.d.ts`). |
| Flutter | pub.dev `liquiglass` | `GlassSurface`, `GlassMaterial`, `LiquidGlass.ensureInitialized`. |
| .NET | NuGet `LiquidGlass`, `.WinForms`, `.Wpf`, `.Avalonia`, `.Maui` | Every public type and member. |

**Previews, outside the promise until they are verified on their platform:** the Qt Quick port
(`ports/qt`, rendered only in software in CI) and the Unity package (`ports/unity`, never run in Unity).
They are distributed from source, not from a registry, and may change in any release.

**Never stable:** anything marked `@LiquidGlassDiagnosticApi` (it opts in at error level and exists for
measurement rigs and tests), anything `internal`, and the shader sources' uniform layout (each port
regenerates it).

## Visual changes

The material and the motion are measured against iOS recordings, and getting closer to iOS is the
project's purpose, so the default look may be refined in minor releases. Every such change:

- is scored against the recordings, and the release notes list it under "Default look changed" with what
  moved and by how much;
- is on by default, with no switch: no setting restores the old pixels. A few earlier looks remain as named
  presets (for example `GlassTabBarStyle.CalmRecordedSpace()`), but a measured fix adds none;
- never re-fits a measured constant without saying what it was scored against.

To keep a look exactly as it is, pin the liquidglass version.

## Platforms

| Platform | Minimum | Rendering |
| :-- | :-- | :-- |
| Android | API 24 | API 33+ (Android 13): the full shader (AGSL). API 24-32: a tinted fallback with rim lighting, no refraction. |
| Desktop JVM | JDK 11 | Skia (Skiko). Windows and Linux checked; macOS not yet checked. |
| Web (Compose) | browsers with WebAssembly GC | Skia (Skiko), the same code as desktop. |
| Web component | evergreen browsers | Live refraction in Chromium; frosted glass in Firefox and Safari; the canvas engine everywhere CanvasKit runs. |
| React Native | React Native 0.78, React 19, React Native Skia 2 | Android and React Native Web. |
| Flutter | Flutter 3.35, Dart 3.9 | Live glass on Impeller (Android); a frosted stand-in elsewhere. |
| .NET | .NET 8 (`LiquidGlass`, Avalonia); .NET 10 (WinForms, WPF, MAUI) | SkiaSharp 4. MAUI on Android and Windows. |
| Qt (preview) | Qt 6 | `ShaderEffect` through Qt's RHI. |
| Unity (preview) | Unity 2022.3 | Built-in pipeline and URP. |
| iOS | not a target | iOS 26 draws Liquid Glass natively. |

Compose Multiplatform 1.12 and Kotlin 2.4 are the versions 1.0 is built and tested with.

## Known differences from iOS

The library is close to iOS, not identical. The measured differences are listed with their numbers in
[the replay record](https://github.com/shayann07/liquidglass/blob/main/review/atlas/reference-replay-2026-10-13/README.md#remaining-differences),
and what the library does not do is in [Limitations](limitations.md). They are open work, not part of
the promise.

<h1 align="center">LiquidGlass</h1>

<p align="center"><b>Liquid Glass for every stack, measured against iOS.</b><br>
Apple's iOS 26 glass for Android, desktop, web, React Native, Flutter and .NET: real refraction of
whatever is behind it, the tab bar whose selection becomes a lens you can drag and throw, and glass
versions of every Material 3 component.</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/dev.shayxo.liquidglass/liquidglass"><img alt="Maven Central" src="https://img.shields.io/maven-central/v/dev.shayxo.liquidglass/liquidglass?label=Maven%20Central"></a>
  <a href="https://www.npmjs.com/package/@shayxo.dev/liquidglass"><img alt="npm" src="https://img.shields.io/npm/v/@shayxo.dev/liquidglass?label=npm"></a>
  <a href="https://www.nuget.org/packages/LiquidGlass"><img alt="NuGet" src="https://img.shields.io/nuget/v/LiquidGlass?label=NuGet"></a>
  <a href="https://github.com/shayann07/liquidglass/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/shayann07/liquidglass/actions/workflows/ci.yml/badge.svg"></a>
  <a href="https://liquidglass.shayxo.dev/"><img alt="Docs" src="https://img.shields.io/badge/docs-liquidglass.shayxo.dev-0a84ff"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-Apache--2.0-blue"></a>
</p>

<p align="center">
  <img src="docs/assets/navigation.gif" width="780" alt="The tab bar: a tapped selector travels as glass, a held selector lifts into a lens, a thrown lens lands against the end of the bar">
</p>
<p align="center"><sub>Rendered by the library (desktop Skia, a deterministic 60 fps replay of a tap, a hold and a throw; shown at 30 fps).</sub></p>

<p align="center"><b><a href="https://liquidglass.shayxo.dev/demo/">Try the live demo in your browser</a></b> ·
<a href="https://liquidglass.shayxo.dev/">Documentation</a> ·
<a href="docs/getting-started.md">Getting started</a></p>

## Pick your stack

| Stack | Install | Guide |
| :-- | :-- | :-- |
| **Compose Multiplatform** (Android, desktop, web) | `plugins { id("dev.shayxo.liquidglass") version "1.0.0" }` | [Getting started](docs/getting-started.md) |
| **Material 3** (Compose) | change the imports, or `./gradlew liquidGlassAdopt` | [Material 3](docs/material3.md) |
| **Web**, any framework | `npm install @shayxo.dev/liquidglass` | [`<liquid-glass>`](docs/web-component.md) |
| **React Native** (Android, web) | `npm install @shayxo.dev/liquidglass-react-native` | [React Native](docs/react-native.md) |
| **Flutter** (Android) | `flutter pub add liquiglass` | [Flutter](docs/flutter.md) |
| **.NET** (WinForms, WPF, Avalonia, MAUI) | `dotnet add package LiquidGlass` + `LiquidGlass.Maui` / `.Wpf` / `.Avalonia` / `.WinForms` | [.NET](docs/native-dotnet.md) |
| **Qt Quick** (QML, C++, PySide6) | `import LiquidGlass` from [`ports/qt`](ports/qt/README.md) | [Qt](docs/qt.md) |
| **Unity** (Built-in, URP) | Package Manager → git URL `…/liquidglass.git?path=/ports/unity/com.shayxo.liquidglass` (not yet run in Unity) | [Unity](docs/unity.md) |

iOS is not a target on any stack: iOS 26 draws Liquid Glass natively.

### Compose: one modifier, SwiftUI's names

```kotlin
LiquidGlassTheme {                       // once, around the app, as MaterialTheme is
    App()
}

Icon(Icons.Default.Edit, null,
    Modifier.size(44.dp).glassEffect(Glass.Regular.interactive(), CircleShape))

GlassEffectContainer(spacing = 20.dp) {  // close pieces melt into one
    Row { /* glassEffect pieces */ }
}

GlassTabBar(itemCount = 3, selectedIndex = selected, onSelected = { selected = it }) { index, isSelected ->
    Tab(index, tint = if (isSelected) Accent else Ink)
}
```

### Material 3: change the imports

```kotlin
import com.wexpa.liquidglass.material3.Scaffold          // was androidx.compose.material3.Scaffold
import com.wexpa.liquidglass.material3.NavigationBar
import com.wexpa.liquidglass.material3.NavigationBarItem
```

<p align="center">
  <img src="review/atlas/material3-polish-2026-10-20/showcase-scrolled-light.png" width="300" alt="A Material 3 music app in glass, light scheme: glass top bar, segmented control, buttons, chips, a frosted mini player and the floating tab bar over album art">
  <img src="review/atlas/material3-polish-2026-10-20/showcase-scrolled-dark.png" width="300" alt="The same app, dark scheme">
</p>
<p align="center"><sub>Rendered by the library: desktop Skia raster (<code>MaterialShowcaseRenderTest</code>), 390 × 844 dp at density 3, scrolled 170 dp. Not a device or browser capture; a Pixel 7 capture and the limits are in the <a href="review/atlas/material3-polish-2026-10-20/README.md">evidence</a>.</sub></p>

### Web, React Native, Flutter, .NET

```html
<!-- after `import '@shayxo.dev/liquidglass'` in your bundle, which registers the element -->
<liquid-glass interactive><button>Back</button><button>Share</button></liquid-glass>
```

```jsx
<Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
  <GlassSurface source={backdrop} x={16} y={height - 100} width={width - 32} height={84} radius={28} dark />
</Canvas>
```

```dart
GlassSurface(radius: 36, dark: true, child: Row(children: icons))   // over a ListView, refracted live
```

```csharp
var bar = new GlassSurface { Backdrop = new GlassBackdrop(rows), Dark = true, Radius = 36, Content = tabs };
```


## On every stack

<table>
<tr><td align="center" valign="top"><img src="review/atlas/android-material3-2026-10-18/material3-pixel7.png" width="200" alt="Material 3 components in glass on a Pixel 7"><br><sub><b>Compose, Material 3</b><br>Pixel 7, Android 17</sub></td><td align="center" valign="top"><img src="review/atlas/react-native-2026-10-19/react-native-pixel7.png" width="200" alt="React Native: a lens, a light surface and a dark bar on a Pixel 7"><br><sub><b>React Native</b><br>Pixel 7, React Native Skia</sub></td><td align="center" valign="top"><img src="review/atlas/qt-2026-10-20/qt-glass.png" width="200" alt="Qt Quick from PySide6: a glass card and a dark bar refracting a list"><br><sub><b>Qt Quick</b> (preview)<br>PySide6, Mesa software render in CI</sub></td></tr>
<tr><td align="center" valign="top"><img src="review/atlas/flutter-2026-10-19/flutter-pixel7-bar-crop.png" width="260" alt="Flutter on a Pixel 7: the dark glass bar refracting a list row"><br><sub><b>Flutter</b><br>Pixel 7, Impeller</sub></td><td align="center" valign="top"><img src="review/atlas/maui-2026-10-20/maui-bar-crop.png" width="260" alt=".NET MAUI on a Pixel 7: the dark glass bar refracting native views"><br><sub><b>.NET MAUI</b><br>Pixel 7</sub></td><td align="center" valign="top"><img src="review/web-component-2026-10-18/dark-card-2x.png" width="260" alt="The web component: a dark glass card with HTML text and a button"><br><sub><b>Web component</b><br>Chromium, headless capture</sub></td></tr>
<tr><td align="center" valign="top"><img src="review/atlas/wpf-2026-10-19/wpf-glass.png" width="260" alt="WPF: a frosted glass card with WPF labels and a lens bending colour bands"><br><sub><b>WPF</b><br>Windows 11</sub></td><td align="center" valign="top"><img src="review/atlas/avalonia-2026-10-19/avalonia-glass.png" width="260" alt="Avalonia: a frosted glass card with Avalonia labels and a lens bending colour bands"><br><sub><b>Avalonia</b><br>Windows 11</sub></td><td align="center" valign="top"><img src="review/atlas/unity-2026-10-20/unity-warp.png" width="260" alt="The Unity shaders rendered on Direct3D 11 WARP: a lens and a bar over stripes"><br><sub><b>Unity</b> (preview)<br>D3D11 WARP in CI, not yet run in Unity</sub></td></tr>
</table>

<sub>Each image is the library rendering on that stack, captured by its demo app; the review folder next
to each one gives the revision, the backend, how it was captured and its limits. Qt and Unity are previews:
rendered in CI only, never yet on a GPU or in the Unity editor.</sub>

## Why this one

**Measured, not styled.** The material's levels, rim, refraction and the tab bar's motion are fitted
to iOS 26 screen recordings and scored against them, frame by frame, through the real renderer.
Every number says whether it was measured, inherited or authored: [the research
record](docs/research/README.md), [the latest comparison](review/atlas/reference-replay-2026-10-13/README.md).
The motion is close to iOS but **not yet identical**, and the [remaining
differences](review/atlas/reference-replay-2026-10-13/README.md#remaining-differences) are listed.

<p align="center">
  <img src="review/atlas/ios-side-by-side-2026-10-20/held-drag.gif" width="640" alt="iOS 27 above, LiquidGlass below: the same held drag of the tab bar's lens from Keypad to Calls and back">
</p>
<p align="center"><sub>iOS 27 (top, the owner's screen recording) and LiquidGlass (bottom, replaying the finger path
inferred from it). The motion is close; iOS still bends the icons under the lens more strongly, and that is
open work. <a href="review/atlas/ios-side-by-side-2026-10-20/README.md">Source, method and limits</a>.</sub></p>

<p align="center">
  <img src="review/atlas/ios-side-by-side-2026-10-20/tap.gif" width="400" alt="iOS 26 above, LiquidGlass below: a tap moves the selection between tabs">
  <img src="review/atlas/ios-side-by-side-2026-10-20/throw.gif" width="400" alt="iOS 26 above, LiquidGlass below: the held lens thrown along the bar">
</p>
<p align="center"><sub>A tap (left) and a throw (right): iOS 26 on top, LiquidGlass below.</sub></p>

**Accessible by default.** Glass follows the platform's Reduce Motion, Increase Contrast and Reduce
Transparency settings unless you override them. The tab bar ticks a selection haptic, draws a focus ring for
keyboard users, lifts the tab under a hovering mouse or trackpad, and at large text sizes shows a held tab magnified, as iOS's Large
Content Viewer does. In right-to-left layouts its shared-width slots mirror, and press, drag and the selector follow
the tab you touch (fixed `slotWidth` layouts keep the positions you gave them). See [Accessibility](docs/accessibility.md) and [Right-to-left layouts](docs/tab-bar.md#right-to-left-layouts).

**One material, every stack.** Compose, the web component, React Native, Flutter and .NET all run the
same production shader, generated from one source and checked against each other in CI.

There are excellent libraries for single stacks, and they may suit you better:

| Library | Stack | What it states |
| :-- | :-- | :-- |
| [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) | Compose Multiplatform | "Compose Multiplatform Liquid Glass effect" |
| [chrisbanes/haze](https://github.com/chrisbanes/haze) | Compose Multiplatform | background blurring |
| [skydoves/Cloudy](https://github.com/skydoves/Cloudy) | Compose Multiplatform | blur and liquid glass, with a CPU fallback |
| [rdev/liquid-glass-react](https://github.com/rdev/liquid-glass-react) | React (web) | "Apple's Liquid Glass effect for React" |
| [callstack/liquid-glass](https://github.com/callstack/liquid-glass) | React Native | iOS 26's native glass, on iOS |
| [liquid_glass_widgets](https://github.com/sdegenaar/liquid_glass_widgets), [liquid_glass_renderer](https://github.com/whynotmake-it/flutter_liquid_glass) | Flutter | shader-based glass widgets |

Their descriptions are quoted from their own repositories (checked 20 October 2026); none of them
is claimed here to lack anything.

## Platforms

| Platform | Status |
| :-- | :-- |
| Android 13+ | Full shader (AGSL). Checked on a Pixel 7 |
| Android below 13 | Tinted fallback with rim lighting; no refraction |
| Desktop JVM | Full shader (Skia). Windows and Linux tested; macOS untested ([help wanted](https://github.com/shayann07/liquidglass/issues/63)) |
| Web, Compose (Wasm) | Full shader (Skia), the same code as desktop ([live demo](https://liquidglass.shayxo.dev/demo/)) |
| Web, any framework | [`<liquid-glass>`](ports/npm/README.md): refracts live HTML in Chromium, frosts elsewhere, or draws the production shaders over a source ([evidence](review/web-component-2026-10-18/README.md)); [CanvasKit port](ports/skia/README.md) |
| React Native | [`GlassSurface`, `GlassLens`](ports/react-native/README.md) on React Native Skia, Android and web ([evidence](review/atlas/react-native-2026-10-19/README.md)) |
| Flutter | [`GlassSurface`](ports/flutter/README.md) over the live backdrop on Impeller, Android ([evidence](review/atlas/flutter-2026-10-19/README.md)) |
| .NET | [SkiaSharp painter and tab bar; WinForms, WPF, Avalonia and MAUI adapters](ports/dotnet/README.md) ([MAUI evidence](review/atlas/maui-2026-10-20/README.md)) |
| Qt Quick | [`GlassSurface`](ports/qt/README.md), a `ShaderEffect` over the live item behind it; rendered offscreen in CI ([evidence](review/atlas/qt-2026-10-20/README.md)) |
| Unity | [Built-in and URP shaders, `GlassSurface`](ports/unity/README.md); shader compiled with FXC and rendered on D3D11 WARP in CI, not yet run in Unity ([evidence](review/atlas/unity-2026-10-20/README.md)) |
| iOS | Not a target: iOS 26 draws Liquid Glass natively |

## Atlas Studio

![Atlas Studio: a magnifier, a top bar, a card and the tab bar over a moving night sky](docs/assets/atlas-sky.jpg)

The demo app, on desktop, Android and [in your browser](https://liquidglass.shayxo.dev/demo/): a
scene that keeps moving under every piece of glass. `./gradlew :desktop:run`.

## Help wanted

The maintainer has one Windows machine and one Android phone. If you have a Mac, a Samsung, Safari, or
a Linux desktop with a real GPU, a ten-minute check helps a lot: see the
[`needs-device` issues](https://github.com/shayann07/liquidglass/issues?q=is%3Aopen+label%3Aneeds-device).
The iOS parity work is tracked in the
[1.1.0 milestone](https://github.com/shayann07/liquidglass/milestones). Issues labelled
[`needs-dataset`](https://github.com/shayann07/liquidglass/issues?q=is%3Aopen+label%3Aneeds-dataset) depend on the
maintainer's private iOS recordings and are assigned to them; the other `parity` issues carry measured targets and,
where it helps, [short clips](review/atlas/issue-clips-2026-10-26/README.md) to work from.
Questions and show-and-tell go in [Discussions](https://github.com/shayann07/liquidglass/discussions).
[Contributing](CONTRIBUTING.md) explains the evidence a change needs.

## License

Apache-2.0: see [LICENSE](LICENSE) and [NOTICE](NOTICE). Not affiliated with or endorsed by Apple.
"Liquid Glass" names the design this resembles. No Apple code, shaders or assets are used; reference
recordings are research material and are not distributed. [Security](SECURITY.md) ·
[Changelog](CHANGELOG.md) · [Limitations](docs/limitations.md)

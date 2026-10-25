# Development status and visual guide

Updated **20 October 2026**. This describes 1.0.0, the first release with a [compatibility promise](compatibility.md).
Full iOS parity is not established: the measured differences are open as issues (#70-#73). The owner
accepted the navigation selector on 20 October. Historical
research remains available; this page identifies which later changes supersede its implementation.

## Main after 1.0 (24 October)

- Material 3 sliders mirror right to left: the fill now grows from the right edge to the thumb, and the
  range fill sits between the mirrored thumbs (#89). Desktop renders before and after:
  [evidence](https://github.com/shayann07/liquidglass/blob/main/review/atlas/material3-slider-rtl-2026-10-24/README.md).
- Custom-path field builds are about 15% faster on the desktop JVM, with bit-identical fields (paired
  run of `tools/benchmarks/pathfield`).
- A resize of a custom-path panel builds its field off composition and keeps the previous field until
  the new one is ready; the first field and any shape or padding change still build synchronously, so
  no frame shows the wrong outline. Bit-identical fields; seven new tests hold the build and check every
  composed frame. A bounded-resolution build (#81) is still open:
  [evidence](https://github.com/shayann07/liquidglass/blob/main/review/atlas/pathfield-off-composition-2026-10-24/README.md).

## Web demo first load (20 October, #78)

- The demo showed a black page for 7.6 s unthrottled and 30 s on slow 4G on a first visit (a 4.46 MB gzip
  download, three quarters of it Skia's runtime, then about 2 s per opening frame compiling shaders). A
  loader now shows within 0.05-0.2 s; the time to a usable app is unchanged. Missing WebAssembly GC,
  failed downloads and lost WebGL contexts are reported on the page. Chromium only:
  [measurements](https://github.com/shayann07/liquidglass/blob/main/review/atlas/web-demo-load-2026-10-20/README.md).
- 24 October: both `.wasm` downloads start with the page (preloaded where WebAssembly GC exists), so Skia
  is ready 0.34-0.35 s sooner on emulated 4G (4.60 → 4.26 s fast 4G, 23.6 → 23.3 s slow 4G; unchanged
  unthrottled). Shader compilation is untouched and was not measurable on the software-GL machine used;
  brotli (22% smaller) awaits a hosting decision.
  [Measurements](https://github.com/shayann07/liquidglass/blob/main/review/atlas/web-demo-load-2026-10-24/README.md).

## First tap after install (20 October, #71)

- The first tab bar tap after install had one 326-352 ms frame on a Pixel 7: the selector's layers were
  reallocated as its pad followed the deforming body, and the first frame at a new size compiled a new GPU
  pipeline for the glass shader (265-286 ms, traced). The pad is now held at its largest: the slowest
  first-tap frame is 67-91 ms, cold start unchanged (medians 135.5 and 136 ms). The library baseline profile is
  removed (no measurable gain). [Evidence](https://github.com/shayann07/liquidglass/blob/main/review/atlas/first-tap-fix-2026-10-20/README.md).

## Material 3 polish (20 October, #79)

- `Slider` and `RangeSlider` tracks run under the thumb's whole travel; they stopped half a thumb short
  of each end. `GlassSliderTrackTest` pins it. The showcase's mini player is inset from the card's edge
  and shows a pause glyph; its hero cover is capped at 280 dp on wide windows. Desktop Skia renders before
  and after, and a Pixel 7 capture: [evidence](https://github.com/shayann07/liquidglass/blob/main/review/atlas/material3-polish-2026-10-20/README.md).
  Browser captures of the web demo (Chrome) show the same fix.

## Compose maintenance on main

Issues #80 and #82 are addressed by active nested-theme view lifetime and matching
cache/build geometry: whole-pixel size and exact recording-grid padding, including fractional
origins at reduced render scales. The first part of #81 adds shader-capability gating and transform-buffer
reuse capped at 256k retained pixels (~4.25 MiB). Larger fields use temporary storage.
Size-only rebuilds now run off composition (24 October); bounded-resolution preparation remains open
under #81. Measurements are desktop allocation evidence, not physical-device frame-time evidence; the
first build and shape changes remain synchronous.
See [verification and limits](https://github.com/shayann07/liquidglass/blob/main/review/atlas/compose-cache-field-2026-10-20/README.md).

## 1.0 (20 October)

- **Parity scoreboard.** All five iOS recordings replayed through the 1.0 build and scored in display
  colours: resting bar 0 on all five, tap pill +0.8 / -2 (was -8.9 / -7.7), held lens -0.1 / +1 on black
  pages and -5 over content (was -13), tracking unchanged. No region regressed.
  [Record](https://github.com/shayann07/liquidglass/blob/main/review/atlas/reference-replay-2026-10-13/README.md#remaining-differences).
- **First use on a Pixel 7.** One 317-370 ms frame on the first tap after installing (GPU shader
  compilation, cached per install); later cold starts 29-65 ms. A library baseline profile ships; it did not
  change these numbers measurably. [Evidence](https://github.com/shayann07/liquidglass/blob/main/review/atlas/first-use-2026-10-20/README.md).
- **API.** Kotlin ABI dumps under `api/`, checked in CI. Nothing was deprecated, so nothing was removed.

## Qt Quick and Unity (20 October)

- **Qt Quick** (`ports/qt`): `GlassSurface` over a live `ShaderEffectSource` of the item behind it.
  Shaders generated from the production SkSL, compiled with `qsb` and rendered offscreen under Xvfb on
  Mesa's software OpenGL in CI, with pixel checks. Not checked: a GPU, Windows/macOS hosts, frame timing.
  [Guide](qt.md).
- **Unity** (`ports/unity`): Built-in and URP shaders plus `GlassSurface`. The generated HLSL compiles
  with FXC (`ps_5_0`) and renders on D3D11 WARP in CI; the material equals the CanvasKit port's. **Never
  run in Unity** (not installed here): ShaderLab, GrabPass, URP and the component are unverified.
  [Guide](unity.md).

## .NET MAUI (20 October)

`LiquidGlass.Maui` (`ports/dotnet/LiquidGlass.Maui`, unreleased) puts `GlassSurface` over the app's own
MAUI views on Android and Windows. The backdrop is drawn from the element's native view. The demo runs on a
Pixel 7, with the glass following the page as it scrolls. The first device run found that MAUI's
`CaptureAsync` copies window pixels on Android, so the glass refracted itself; it now draws the view.
Not run: the Windows target (it builds and packs in CI). Not measured: frame timing.
[Guide](native-dotnet.md#net-maui),
[evidence](https://github.com/shayann07/liquidglass/blob/main/review/atlas/maui-2026-10-20/README.md).

## Flutter on pub.dev (20 October)

The Flutter package is publishable as `liquiglass` (pub.dev refuses `liquidglass` as too close to
`liquid_glass`): pub.dev metadata, a package CHANGELOG, a screenshot, platforms without iOS, and
`flutter pub publish --dry-run` clean. Its version follows `VERSION_NAME` (`-SNAPSHOT` as `-dev`).
A `pub-dev` job in the publish workflow publishes each tag through pub.dev's automated publishing, once
the owner's first upload exists ([publishing](publishing.md#pubdev-once)). 1.0.0 was uploaded by hand on
20 October: <https://pub.dev/packages/liquiglass>.

## Flutter (19 October)

`liquiglass`, liquidglass for Flutter (`ports/flutter`) puts `GlassSurface` over the live backdrop on
Impeller: a backdrop filter running the production shader, generated from its SkSL export. The Dart
material equals the CanvasKit port's uniforms to 1e-6, the shader compiles and loads in `flutter test`,
and the example refracts a scrolling list on the Android 17 emulator (OpenGL ES). Approximated: the
wide tone kernel (thirteen taps). On 20 October it ran on a Pixel 7 on Impeller's Vulkan backend too. Not yet
measured: frame timing. [Guide](flutter.md),
[evidence](https://github.com/shayann07/liquidglass/blob/main/review/atlas/flutter-2026-10-19/README.md).

## React Native (19 October)

`@shayxo.dev/liquidglass-react-native` (`ports/react-native`, on npm from 0.2.1) puts `GlassSurface` and `GlassLens` in a
React Native Skia `<Canvas>` on Android and React Native Web, with the production shaders: a small
adapter runs the CanvasKit painter unchanged on React Native Skia's API. In React Native Skia's headless
build (CanvasKit, software) the adapter's glass equals CanvasKit's, no channel off by more than 1, and
the components render in an element tree. The demo app runs on the Android 17 emulator (a first run
there found surfaces drawing nothing, fixed by raster offscreen surfaces) and, on 20 October, on a Pixel 7. The backdrop
is a snapshot.
[Guide](react-native.md), [evidence](https://github.com/shayann07/liquidglass/blob/main/review/atlas/react-native-2026-10-19/README.md).

## WPF and Avalonia (19 October)

- `LiquidGlass.Wpf` (new): glass over native WPF content. The host's own WPF subtree is captured as
  the backdrop, and the glass is drawn into a `WriteableBitmap` under ordinary WPF labels.
  `WpfDemo --capture` renders it offscreen and checks the pixels; it runs in CI on Windows.
  [Evidence](https://github.com/shayann07/liquidglass/blob/main/review/atlas/wpf-2026-10-19/README.md).
- `LiquidGlass.Avalonia` (new, .NET 8): the same over native Avalonia 12 content on Windows, macOS
  and Linux. Avalonia 12 accepts the painter's SkiaSharp 4. `AvaloniaDemo` renders headlessly with
  Avalonia's Skia renderer and checks the pixels, in CI on Linux and Windows.
  [Evidence](https://github.com/shayann07/liquidglass/blob/main/review/atlas/avalonia-2026-10-19/README.md).

## Public Compose API (18 October)

The Compose API was replaced on 18 October by one root and one modifier, named after SwiftUI's:
`LiquidGlassTheme { App() }` around the app, `Modifier.glassEffect(glass, shape)` on any element,
`Glass` variants (`Regular`, `Clear`, `Identity`, and the measured `Lens`, `TabBar`, `Toolbar`),
`GlassEffectContainer` and `glassEffectID`. `GlassTabBar` takes no state. The state-and-modifier
API and `GlassScene` are internal now; the [API reference](api-reference.md#removed-18-october)
maps each removed name to its replacement.

Glass is drawn in an overlay above the theme's recording, on every platform, so it never samples
itself. Two consequences are open limits: glass draws above content that is not glass, and an
ancestor's alpha does not fade it ([Limitations](limitations.md#glass-draws-above-content-that-is-not-glass)).
Skia's own backdrop (desktop and web) was considered and set aside on 19 October: it fixes the
stacking but blanks glass under any translucent ancestor, which every fade animation creates
([spikes](https://github.com/shayann07/liquidglass/blob/main/review/atlas/backdrop-spikes-2026-10-18/README.md)). The overlay stays on every platform.

Evidence: `GlassEffectTest` in the desktop render suite checks that a lens magnifies the page and
not its own content at the top level, under a `graphicsLayer` and inside a scrolled column; that a
click reaches the element under interactive glass; that `Glass.Identity` draws no glass; the
stand-in without a theme; merging in a container; and a `glassEffectID` morph. These are desktop
JVM renders, not a physical-device record; this page links no device record for the new API yet.

## Web component (18 October)

`<liquid-glass>` (`ports/npm`, npm package `@shayxo.dev/liquidglass`) brings the material to any
web page or framework as a custom element; its content stays ordinary HTML. `engine="html"` refracts
the live page with `backdrop-filter`: an SVG displacement map computed from the shader's own geometry in
Chromium, a frosted blur in Firefox and Safari. It is a CSS approximation of the material, with
inherited numbers checked against the Kotlin sources and the CSS-only values labelled authored.
`engine="canvas"` draws the production shaders through the CanvasKit painter over a source the page
supplies. [Guide](web-component.md).

Evidence: 27 Node unit tests and 7 Playwright tests in Chromium 156 (headless shell), and captures of
both engines in light and dark
([record](https://github.com/shayann07/liquidglass/blob/main/review/web-component-2026-10-18/README.md)).
Firefox 157 and WebKit 27.2 (Playwright, headless) upgrade the element, select the frosted path and
run the canvas engine, but their headless builds render no `backdrop-filter` blur at all, so the frosted
look there is unverified. Not checked: Safari, mobile browsers, a physical device, comparison with iOS.
The ports' surfaces bind `Glass.Regular`'s light, `GlassLight.Default` (-0.35, -0.94), with a `light` option (19 October).

## Material 3, the plugin and the BOM (18 October)

- `liquidglass-material3` (new artifact): glass versions of the Material 3 components with the same
  names and parameters ([guide](material3.md)). Default look: iOS 26 shapes and motion in the app's
  colour scheme; bars take `layout = GlassLayout.Material`. Dialogs, sheets and menus are drawn in the
  app's window (`GlassOverlay`), so their glass refracts the app behind them. The tab bar, segmented
  controls and tab rows use the measured Calm tab bar; the other components' looks are authored, not
  measured against iOS.
- `liquidglass-bom` (new) and the Gradle plugin `dev.shayxo.liquidglass` (new): one line adds the right
  artifacts; `liquidGlassAdopt` switches an app's Material 3 imports once. Published in 0.2.0.
- Release pipeline (19 October): one tag publishes the library, Material 3, the BOM and the plugin to
  Maven Central (the plugin through its marker, no Plugin Portal account needed), the web component to
  npm and the four .NET packages to NuGet, then creates the GitHub release. Pull requests touching it
  run it as a dry run. Rehearsed locally: a new desktop project applied the plugin by its ID, adopted a
  `Button` and compiled. NuGet publishes through trusted publishing; npm uses a token for its first
  release, then trusted publishing too ([publishing](publishing.md)).
- Evidence: [Material 3 renders](https://github.com/shayann07/liquidglass/blob/main/review/atlas/material3-2026-10-18/README.md).
  Android: the showcase and Atlas render on a Pixel 7 (Android 17) with every glass element and its
  content, at the same frame rate as the pre-Phase-2 build in a paired run, and on the Android 37.1
  emulator ([evidence](https://github.com/shayann07/liquidglass/blob/main/review/atlas/android-material3-2026-10-18/README.md)).
  The browser build runs (live demo, `#material3`).

## Android frost fix (19 October)

- Tall frosted glass on Android (a sheet at the bottom of the screen) showed the page only in a band at
  its top. Fixed by blurring the backdrop in a nested layer on Android; desktop and web unchanged.
  [Evidence](https://github.com/shayann07/liquidglass/blob/main/review/atlas/android-frost-2026-10-19/README.md),
  on a Pixel 7.

## Material 3 coverage (19 October)

- Every Material 3 component with a container now has a glass version. New this pass: icon toggle
  buttons, elevated chips, `RangeSlider`, `Surface`, tooltips, drawer sheets and items, the date and
  time picker dialogs, and the leading-icon and scrollable tab rows. The codemod knows them all.
- Two limits closed:
  - Back dismisses the glass dialogs, sheets, menus and tooltips.
  - `ModalBottomSheet` has its partially expanded stop (half the window).
  - Both are covered by `GlassMaterialComponentsTest.backDismissesOverlaysAndATallSheetStopsHalfway`.
- Evidence: [coverage renders](https://github.com/shayann07/liquidglass/blob/main/review/atlas/m3-coverage-2026-10-19/README.md),
  and an interaction test for the toggle and the tooltip.

## iOS 27's rim (19 October)

- `GlassStyle.outerContour` (new, opt-in): iOS 27's dark contour outside the outline, strongest at
  the unlit sides, with measured values (0.61 dark, 0.31 light). The Material 3 components adopt it
  with the measured overhead light and zero dispersion, which is an authored choice of measured
  values. Core presets are unchanged and the core suite's tolerances still hold.
- Evidence: [iOS 27 rim renders](https://github.com/shayann07/liquidglass/blob/main/review/atlas/ios27-rim-2026-10-19/README.md).
  Lone elements and `GlassEffectContainer`'s merged shapes draw it. The tab bar's measured styles
  keep their own cap contour and are unchanged.

## Glass quality pass (18 October)

- Material 3 panels (cards, dialogs, sheets, menus, snackbars, the search results panel) are frosted
  with the measured system-backdrop sigma, 7.7 dp (`GlassMaterialDefaults.panel`). Using it for
  in-app panels is authored. Controls are unchanged. No measured constant changed and the core
  library is untouched.
- The showcase is redesigned (music library, mesh wallpaper, detailed covers).
- Evidence: [glass quality renders](https://github.com/shayann07/liquidglass/blob/main/review/atlas/glass-quality-2026-10-18/README.md),
  on a Pixel 7 and on desktop. They are not a calibrated comparison against iOS. Rim and specular
  work toward iOS is next. It will be opt-in.

## Atlas and the current material

![Native Windows Atlas grid scene](https://raw.githubusercontent.com/shayann07/liquidglass/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/review/atlas/wide-input-2026-10-11/static.png)

Native app-buffer capture from the clipped-input checkpoint. The grid shows continuous magnification;
the card and navigation remain anchored. This resting view is not proof of continuous motion or FPS.
Atlas now selects a discrete GPU where available and prints the actual adapter. The owner's tested
hybrid machine selected NVIDIA RTX 3050; an OpenGL override selected Intel and was not an equivalent
backend comparison. [GPU evidence](https://github.com/shayann07/liquidglass/blob/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/review/atlas/gpu-2026-10-11/README.md).

## Features and integration paths

| Use case | Entry point | Responsibility and scope |
| --- | --- | --- |
| Compose app | `LiquidGlassTheme` at the root and `Modifier.glassEffect` | One recording per theme; the element keeps layout, actions and semantics |
| Compose for Web | the same Compose APIs, `wasmJs` target | Same Skia actuals as desktop (`skikoMain`); `:web` runs Atlas in the browser ([record](https://github.com/shayann07/liquidglass/blob/main/review/atlas/web-2026-10-18/README.md)) |
| Full optical control | `Glass(style = GlassStyle(...))`, `GlassEffectContainer`, `glassEffectID` | Every optical parameter and the historical presets; the explicit state API was removed on 18 October |
| Free magnifier | `GlassStyle.clearLens` / native `DrawLens` | Continuous mapping; distinct from a held navigation selector |
| Calm navigation | `GlassTabBarStyle.Calm()` | Tap/held travel, landing recovery, whole-bar off-axis strain and separate appearance |
| Portable production renderer | CanvasKit `createGlassScene` / `createGlassPainter` | Production shaders, explicit source, density, lifecycle and input |
| Any web page or framework | `<liquid-glass>` (npm `@shayxo.dev/liquidglass`) | HTML engine: the live page, a CSS approximation (refraction in Chromium only); canvas engine: production shaders over a supplied source |
| Lightweight browser lens | `ports/web` | Dependency-free lens preview; not the full material |
| Native .NET | `GlassPainter`, `CalmInteraction`, `GlassFeedback` | SkiaSharp material and whole-surface input; host schedules redraws |
| Existing WinForms controls | `ControlBackdrop.Capture` | Owned control subtree only, explicit invalidation/lifetime and supported-type limits |

[Compose setup](getting-started.md), [API](api-reference.md), [generic interaction](generic-interaction.md),
[cross-stack contracts](porting.md) and [.NET setup/examples](https://github.com/shayann07/liquidglass/blob/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/ports/dotnet/README.md).
No adapter silently captures the system desktop or another application.

## Navigation shape and appearance

**13 October: first continuous comparison.** Both iOS recordings were replayed gesture by gesture
through the real renderer at a deterministic 60 fps and compared as sequences (taps, stationary
hold, early and formed throws into both walls, held reversals, the soft landing on Contacts).
Five visible defects in the handoff build were corrected in `GlassTabBarStyle.Calm()`:

- a tapped selector now travels as refracting glass instead of fading to an invisible ghost;
- a thrown lens stays formed through travel and the wall impact, then becomes the inset;
- wall landings are a wide flattened capsule, not the tall circle of the former impact model
  (kept as `GlassPoseSpec.CalmImpact` for comparison);
- the grasp follows a fast throw closely (default 78/s instead of the authored 40/s);
- the held rim is a soft refractive band, not a white specular outline;

and the dark material was recalibrated to the recordings. A second pass added
`GlassTabBarAccessory`: the held bar now fuses with a neighbouring search button through a liquid
neck, as iOS 26's Phone bar does. A third pass matched fast held reversals: the lens stays under
the finger through Video 2's sweeps and a hold forms in about 80 ms. A fourth pass matched the lens's rim, band glow and
interior clarity, and the bar's rim, to measured grey levels; a fifth gave the moving lens a
reflective rim at its caps and selected ink under the whole lens: bar 45 at rest and 53 held, resting
selector 28 against the reference's 24–30 (previously 6–8). A sixth pass (14 October) made wall
landings press into the bar's end and narrow from behind (241 → 285 px, as measured) instead of
flying past it, and corrected the comparison scene's resting pill to the measured 95 pt. A seventh
pass corrected the inferred input (hold onset and wall-throw lift), enlarged the moving lens's
cap swirl with faint fringes, made the held bar reach toward the finger (edges within 2–5 px of
iOS) and redesigned Atlas Studio. An eighth pass matched the bar's release after lift, so the
search-button neck parts on time. A ninth pass (14 October, second half) scored every wall landing
rather than one: after the lift the trailing edge travels on underdamped (rms 4.9 px over nine
landings, 15.4 before; the 9.9 s landing now narrows like the others), a formed lens presses past
the bar's end as iOS's does, the moving lens's caps draw content out radially (and a clamp that had
halved the rim mirror was fixed), and the search button's neck gives way to an arriving lens. A tenth
pass scored the left-wall landings too (rms 4.1 px); an eleventh made the bar relax while its lens is
dragged, as iOS's does, which brought the search-button neck to a median 0 ms of iOS's (67 before),
and the ports now draw the selected ink inside the selector. A twelfth pass fixed the held lens's
selected ink (scaled about the wrong point, 17 px off) and a frame-loop wake that left throws from a
still hold 1.5 frames behind the finger, made the moving lens widen and flatten as iOS's does
(458 x 145 px at its fastest reversal, ours 427 x 144), gave the held lens a thin grey outline
instead of a white ring, and let a tapped selector bend icons along its top and bottom edges. A
thirteenth placed the held lens on the bar's pressed tab with the bar no longer reaching past it,
restored its inward glow as a floor, and matched the page text seen through it. A fourteenth
matched what the held lens shows of the page beneath it and corrected the inferred reversal
input from the recording's own icon-crossing times. The Calm selector's motion, gestures and material are ported to the
CanvasKit and .NET packages, checked against the Kotlin controller and material factory. On Android, the
selector and Atlas Studio were verified on an API 37 emulator through the AGSL path
([emulator record](https://github.com/shayann07/liquidglass/blob/main/review/atlas/android-emulator-2026-10-14/README.md));
no physical device was available.

![Before and after at the same replay instants](https://raw.githubusercontent.com/shayann07/liquidglass/main/review/atlas/reference-replay-2026-10-13/before-after.png)

Our renders only; the private reference is not published. Finger timing is inferred from the
recordings. Method, numbers and the remaining differences (bar/search fusion while held, weaker
stretch on fast reversals, later hold onset, smaller ink magnification in transit, more smeared
text under the bar) are in the
[reference-replay record](https://github.com/shayann07/liquidglass/blob/main/review/atlas/reference-replay-2026-10-13/README.md).

Earlier work this builds on: resting tap travel and held formation are separate; press expansion
and whole-bar strain are separate, and the selector gets no independent perpendicular squeeze;
layout and hit targets stay fixed. The 12 October tap-appearance calibration (25 interior patches,
repeated short-tap width residual up to 20.64 px) predates this comparison and is superseded for
tap optics by the change above.

### Evidence map

| Change / investigation | Primary record |
| --- | --- |
| Continuous magnifier, calm controls, Atlas redesign, original extreme envelopes | [Stabilization record](https://github.com/shayann07/liquidglass/blob/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/review/ATLAS-STABILIZATION.md) |
| Coupled short/long tap shape | [Paired original/source comparison](https://github.com/shayann07/liquidglass/blob/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/review/atlas/phone-tap-volume-2026-10-09/README.md) |
| Both endpoint landings and remaining cap recovery | [Landing sequence](https://github.com/shayann07/liquidglass/blob/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/review/atlas/phone-landing-sequence-2026-10-09/README.md), [shape-aware edge](https://github.com/shayann07/liquidglass/blob/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/review/atlas/phone-edge-2026-10-09/README.md) |
| Rejected dark-disc appearance and corrected substrate tint | [Native before/after](https://github.com/shayann07/liquidglass/blob/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/review/atlas/inset-material-2026-10-10/README.md) |
| Repeated adjacent-trip timing and residual width error | [Short-tap audit](https://github.com/shayann07/liquidglass/blob/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/review/atlas/short-tap-timing-2026-10-10/README.md) |
| Separate tap pigment/ink, calibration and verification | [Initial optical audit](https://github.com/shayann07/liquidglass/blob/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/review/atlas/tap-optics-audit-2026-10-11/README.md), [candidate](https://github.com/shayann07/liquidglass/blob/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/review/atlas/tap-optics-candidate-2026-10-11/README.md), [long-tap recovery](https://github.com/shayann07/liquidglass/blob/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/review/atlas/long-tap-appearance-2026-10-12/README.md) |
| GPU clipping seam and retained blur/input behavior | [Clipped-input regression and native proof](https://github.com/shayann07/liquidglass/blob/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/review/atlas/wide-input-2026-10-11/README.md) |
| Rendering cost, natural recording, retained selection failure | [Frame/input investigation](https://github.com/shayann07/liquidglass/blob/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/review/atlas/frame-cost-2026-10-13/README.md) |
| Portable production shaders and scene | [CanvasKit guide](https://github.com/shayann07/liquidglass/blob/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/ports/skia/README.md) |
| Native .NET material, motion and controls | [Painter](https://github.com/shayann07/liquidglass/blob/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/review/atlas/dotnet-2026-10-10/README.md), [calm interaction](https://github.com/shayann07/liquidglass/blob/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/review/atlas/dotnet-calm-2026-10-13/README.md), [native backdrops](https://github.com/shayann07/liquidglass/blob/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/review/atlas/dotnet-controls-2026-10-13/README.md) |
| Contact shadow inside the backdrop at `renderScale` below 1 (fixed 17 October) | [Performance guide](performance.md#rendering-at-reduced-resolution), `GlassContactShadowScaleTest` |

These links are pinned to a published source checkpoint so the evidence remains reproducible.
For later status, follow the repository's [progress](https://github.com/shayann07/liquidglass/blob/main/dev/PROGRESS.md)
and [full checklist](https://github.com/shayann07/liquidglass/blob/main/dev/TODO.md).

## Native .NET interaction and controls

![Nine native interaction phases](https://raw.githubusercontent.com/shayann07/liquidglass/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/review/atlas/dotnet-calm-2026-10-13/native-phases.png)

`CalmInteraction` takes logical bounds, cumulative displacement and monotonic seconds. It preserves
release/re-grab continuity, viewport containment, reduced-motion reset and resize cancellation.
The native implementation matches 144 portable states and 107,520 independent outline probes. Twelve
native check groups and nine app-owned phases pass. Held/release images match exactly; recovery
and reduced motion return to baseline, with 1,092 foreground pixels remaining anchored.

![Native controls: before, text change, state change, glass and resize](https://raw.githubusercontent.com/shayann07/liquidglass/b46207d665725a0aa841102bfbdcbe74aaf4ac3c/review/atlas/dotnet-controls-2026-10-13/comparison.png)

The separate WinForms adapter records an owned Panel/Label/TextBox/CheckBox subtree. Actual TextBox
glyph changes alter 1,522 pixels within its bounds, independently of background changes. Identity
lens pixels, refraction, resize, retained-source failure and UI-thread checks pass. The example
keeps native controls interactive beside their glass preview. RichTextBox/ActiveX are rejected;
hidden children, custom controls and overlapping container order retain documented limits.

This completes usable whole-surface feedback and one native control-capture path; the native Calm
navigation selector (motion, gestures and the Compose material) is also ported. Additional
frameworks, high-level portable refracted foreground, ported selected ink and universal widget
capture remain incomplete. No NuGet package was published.

Packaging (18 October): the painter is packed as `LiquidGlass` and the Windows Forms host as
`LiquidGlass.WinForms`, at the library's version, with Source Link and symbols. CI packs both on Linux
and Windows and checks each package's readme, NOTICE, licence, version, assembly and embedded shaders.
Publishing waits for the owner.

## Verification and performance

| Evidence | What it proves | What it does not prove |
| --- | --- | --- |
| Inspected cea1913 hosted XML: 344 library passes, 27 optional skips, 3 Atlas passes; Android build | Exact regression/build checkpoint | Full iOS equivalence or physical Android validation |
|43 inspected NVIDIA native motion phases | App-owned rendering and selection/layout assertions | Continuous matching trajectory or presented FPS |
| af333fa: all ten required hosted checks green | Native calm update passes Windows/Linux, docs/security and existing build gates | Later commits need their own CI |
| b46207d: all ten required checks green, including native Windows controls; five local states inspected | Standard-control capture, glyph updates, identity/refraction and failure guards | Other control types, hosts and display performance remain unverified |
| Controlled wide-input old/new/new/old comparison | About 3 ms additional median redraw submission cost | Display latency or all-scene performance |
| Natural recording and JFR | UI picture-recording/dispatch observations; 491 ms Direct3D lock wait inside one forced redraw | GPU execution/presentation timing or a complete lag diagnosis |

The original local V3 gesture timeout is preserved alongside its unchanged-source passing retry.
One natural replay failed bottom-right selection; two later observed replays passed. That failure's
cause remains unresolved. Windows refused the process-filtered PresentMon ETW session; no display
trace was produced and no permission changes were made. See [performance](performance.md).

## What remains

1. Remaining differences from the continuous comparison: the 9.9 s landing peaks 6 px short of
   iOS's and the left width series' first frames narrow up to 11.2 px less, both of the size of one
   frame of the inferred lift; the neck parts 100 ms late at one release (34.8 s). In display
   colours (since 15 October, five recordings) the rest, held and tap levels are within 0-4 grey
   levels; since pass 18 the pill and lens pass page content as iOS's do and text under the bar is
   one soft copy. Since 17 October the held lens no longer passes the raw page, so text under it
   is veiled as under iOS's; it adds the raw page blurred (`GlassStyle.rawBlur`, 0.5 / 50 dp since 18 October), so
   it lifts over content as iOS's does (text p50 83 against 82, black unchanged); its top band near
   the rim still reads 11 below iOS's. `GlassTabBarStyle.slotWidth` lays tabs on set slots as
   WhatsApp's bar does (all five centres within 2 px), in Compose and both ports.
   `GlassTabBarStyle.CalmClear()` is the clearer bar of WhatsApp and the App Store (bar 53 against
   iOS's 54 over a grey card, Calm 46), in Compose and both ports; with `legibility` 2.5 it compresses
   text under it (p50 / p75 67 / 98 against iOS's 69 / 80). WhatsApp's motion lands 14/14 and settles a
   median 21 ms early, read by one tracker on both sides with inferred input. Light `Calm()` is measured
   on the owner's light recording (bar 0.59 of the page + 115, rim, shadow, pill, lens outline, tap
   optics, and since 18 October the held lens's shadow, cast by the lens rather than its node).
   The owner's device check (18 October) changed five things: icons hold still on taps, taps travel
   as a lens, the held lens no longer shows the bar's outline as a second capsule, the search button
   follows a swipe less, and a held lens joins the search button through a neck; the bar's rounded
   ends lens what lies beyond them (`capFold`). The dark held lens is within 4 levels of iOS's in every
   region (2.8 rms), and the search button's sideways release springs as iOS's does (x damping 0.3).
   Text under a tab bar is one soft copy
   in both appearances (IMG_7049), as the Calm presets draw it.
   The search button presses, pulls and lands as iOS's does since pass 17 (centre 2-3 px rms); the
   CanvasKit and .NET ports have its press, pull and landing (`createFreeButton` / `FreeButton`, checked frame
   by frame against Kotlin) and, since 17 October, the bar's fusion with it (`accessoryFuse` /
   `AccessoryFuse`). The owner accepted the Calm tab bar on the device on 18 October, by finger, after the
   six-point check (icons, tap travel, held lens rim, search feel, lens neck, bar ends).
2. Hardware: the physical-device check ran on 17 October (Pixel 7, dark and light: tap, hold, drag,
   throw, the search button pulled 250 px in eight directions, off-axis bar drags; no artefacts;
   [record](https://github.com/shayann07/liquidglass/blob/main/review/atlas/android-device-2026-10-17/README.md)).
   The owner's own-finger check followed on 18 October and was accepted. Landing timing is within one
   frame of the 60 fps recordings (WhatsApp's paths: best shift -17 ms, was -42), its resolution; no
   calibration of the inferred lift moves it further.
3. Performance on a wider range of devices (Pixel 7 at 90 Hz keeps the display rate: UI thread 6.9 ms,
   GPU 8.5 ms p50 per frame, 2-3% janky; `renderScale` barely changes it; the dark lens's wider raw blur
   of 18 October adds 2 ms at GPU p90 and nothing at p50). Atlas Studio wide (1400 x 900) and narrow
   (420 x 880) windows were captured and inspected on both GPUs on 18 October
   ([record](https://github.com/shayann07/liquidglass/blob/main/review/atlas/desktop-2026-10-18/README.md)): every scene intact, the
   compact layout under 480 dp as designed; static redraw submission ~10 ms p50 on the NVIDIA RTX 3050 and
   21-29 ms on Intel UHD, slower than the 11 October record (4.5 / 8.9 ms). A 14 October build times the same
   as this one, so the 18 October changes did not cause it; its cause is not established. Owner visual
   acceptance of the desktop windows is still open.
4. Additional host integrations, with executable evidence.
5. No release/tag is authorized.

The current checklist has 27 completed and 22 open entries of unequal size; this is not a progress
percentage. A passing checkpoint does not narrow the full goal.

## Keeping this documentation current

Each behavior/API change must update its user guide, this status/evidence map, CHANGELOG and the
relevant limitation before publication. Visible changes require app-owned screenshots at stated
size/backend, captions that distinguish measured source from candidate output, reproduction commands
and an exact verification record. Failed attempts stay labelled and historical claims retain their
original scope. Raw/private recordings and full profile traces stay outside the published site.
The maintainer checklist is in [CONTRIBUTING](https://github.com/shayann07/liquidglass/blob/main/CONTRIBUTING.md).

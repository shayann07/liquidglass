# Changelog

This file records user-visible development changes. Published artifacts are identified separately;
A version heading means that version is published; anything above the newest one is on `main` only.

Flutter: `liquiglass` 1.0.0 is on pub.dev (<https://pub.dev/packages/liquiglass>), uploaded by hand on
20 October; later tags publish it from the release workflow.

- Custom-path fields: a change of size alone is now built off composition, on
  `Dispatchers.Default`, and the panel keeps its previous field until the new one is ready; resizes
  during a build are conflated to the latest and a pending build is dropped when the panel leaves. The
  first field, a field replacing an empty one, and any change of shape, padding, band, density or
  direction still build during composition, so no frame draws the wrong outline or none. Fields stay
  bit-identical; the build's own cost is unchanged. On Wasm the background dispatcher is the main
  thread (#81). [Evidence](review/atlas/pathfield-off-composition-2026-10-24/README.md).
- Material 3: `Slider` and `RangeSlider` fills follow the thumbs right to left; the track was drawn
  unmirrored, so in RTL layouts the fill grew from the left edge, away from the thumb (#89).
  [Evidence](review/atlas/material3-slider-rtl-2026-10-24/README.md).
- Custom-path fields build about 15% faster (paired desktop JVM benchmark, phone-width and larger
  panels), with bit-identical output: the distance transform no longer calls a closure with a bounds
  check per neighbour, and the mask is read from packed pixels instead of a `Color` per pixel (#81).
- Compose: release nested-theme recording views when their last consumer changes settings,
  leaves composition or is abandoned; shared active consumers keep receiving backdrop updates (#80).
- Skip custom-path field generation without shaders and reuse bounded distance-transform scratch
  buffers. Full-resolution geometry and odd-pad alignment are preserved; computation is still
  synchronous. Correct the half-resolution documentation and add an opt-in allocation probe (#81).
- Key and build remembered path fields from the same whole-pixel size and exact recording-grid
  padding so equal padded dimensions cannot reuse stale geometry. Preserve fractional padding at
  reduced render scales to match the shader's origin; style changes within a grid bucket reuse
  the field (#82). Cap retained transform buffers at 256k pixels (~4.25 MiB); oversized builds
  use temporary buffers (#81).
- Verification and measurement limits:
  [Compose cache/field evidence](review/atlas/compose-cache-field-2026-10-20/README.md).
- Material 3: the `Slider` and `RangeSlider` tracks run under the thumb's whole travel, as iOS's do.
  They stopped half a thumb short of each end, so the thumb hung past the track. The showcase's mini
  player keeps its controls clear of the card's edge and shows a pause glyph while playing, and its
  hero cover is capped at 280 dp on wide windows, where it filled the screen with sky (#79).
  [Evidence](review/atlas/material3-polish-2026-10-20/README.md).
- Android: the first tab bar tap after install no longer stalls for one 326-352 ms frame on a Pixel 7
  (67-91 ms now). The selector's record pad followed its deforming body, so its layers were reallocated
  mid-gesture and the first frame at a new size compiled a new GPU pipeline for the glass shader; the pad
  is now held at its largest (the body's pad only: a fuse's neck margin is never held, so a
  long press that joins the trailing button does not keep a fused-size layer). Cold start is unchanged. The library baseline profile is removed: it showed
  no measurable gain before or after this fix (#71).
  [Evidence](review/atlas/first-tap-fix-2026-10-20/README.md).
- Web demo: a loader shows from the first paint instead of a black page while the 4.5 MB download and
  the first visit's shader compilation run; a browser without WebAssembly GC, a failed download or a lost
  graphics context is reported on the page. The docs load each embedded demo lazily, as it approaches the
  viewport (#78).
  [Measurements](review/atlas/web-demo-load-2026-10-20/README.md).

## 1.0.0 (2026-10-20)

The first release with a compatibility promise ([support and compatibility](docs/compatibility.md)):
semantic versioning across Maven Central, npm and NuGet, and a public Kotlin API checked against a committed
dump. It adds .NET MAUI, publishes Flutter as `liquiglass` (first pub.dev upload pending), and previews Qt
Quick and Unity. Release notes: [.github/release-notes/v1.0.0.md](.github/release-notes/v1.0.0.md).

### Added — 1.0 (20 October)

- `docs/compatibility.md`: what 1.0 promises per stack, minimum versions, deprecation and visual-change
  policy; the Qt and Unity ports are previews outside the promise.
- Kotlin ABI validation for `liquidglass` and `liquidglass-material3` (`api/`, `checkKotlinAbi` in CI).
- A library baseline profile (`liquidglass/src/androidMain/baselineProfiles/baseline-prof.txt`). On a
  Pixel 7 it did not measurably change the first tap; the one-time first-use frame (317-370 ms, shader
  compilation) is #71 ([evidence](review/atlas/first-use-2026-10-20/README.md)).
- The sample's release build is signed with the debug key, so it installs from the command line for timing.
- Parity scoreboard of all five recordings on this build
  ([record](review/atlas/reference-replay-2026-10-13/README.md#remaining-differences)); the open TODO rows
  are closed with their evidence or tracked as issues #70-#75.
- CodeRabbit reviews pull requests (`.coderabbit.yaml`).

### Added — Qt Quick and Unity (20 October)

- **Qt Quick** (`ports/qt`, new): `GlassSurface`, a `ShaderEffect` over a live `ShaderEffectSource` of
  the item behind it, for QML in C++ or PySide6 apps (Qt 6). Its GLSL 440 shaders and `GlassEffect.qml`
  are generated from the production SkSL (`tool/generate_shader.mjs --check`); `material.js` equals the
  CanvasKit port's material. CI compiles the shaders with `qsb` on Linux and renders the PySide6 example
  offscreen under Xvfb, checking the glass's pixels ([evidence](review/atlas/qt-2026-10-20/README.md)).
- **Unity** (`ports/unity`, new, UPM `com.shayxo.liquidglass`): the material as generated HLSL, shaders
  for the Built-in pipeline (GrabPass) and URP (opaque texture), and a `GlassSurface` component for quads
  and UI. **Not yet run in Unity.** CI checks the material against the shared fixture, compiles the HLSL
  with FXC as `ps_5_0` and renders it on Direct3D 11 WARP ([evidence](review/atlas/unity-2026-10-20/README.md)).

### Changed — repository layout (20 October)

- The apps moved under `apps/` (`apps/sample`, `apps/desktop`, `apps/web`); their Gradle paths are
  unchanged (`:sample`, `:desktop`, `:web`). Maintainer notes moved under `dev/` (`NEXT-SESSION.md`,
  `TODO.md`, `PROGRESS.md`, `WORKSPACE.md`, `archive/`), and `launch_desktop.bat` under `tools/`. Every
  link was rewritten; records written before this date use the old paths.

### Documentation — every stack, discoverable (20 October)

- New positioning: **Liquid Glass for every stack, measured against iOS.** The README is rewritten
  around a "pick your stack" table with one install line and snippet per stack, an honest list of
  single-stack alternatives (quoted from their own repositories), and a help-wanted section.
- The docs site has a "Liquid Glass for …" page per stack (Android and Compose, Flutter, React
  Native, the web, .NET), meta descriptions, OpenGraph and Twitter link previews with a social card,
  and the sitemap at `/sitemap.xml`.
- Device checks this machine cannot do are open as `needs-device` issues (#62 to #67).

### Added — .NET MAUI (20 October)

- `LiquidGlass.Maui` (new NuGet package, .NET 10, Android and Windows; not iOS): `GlassSurface`, a
  `ContentView` whose glass refracts a `GlassBackdrop`, one of the app's own MAUI elements drawn from its
  native view. Glass follows the element as it scrolls, with no new capture. `builder.UseLiquidGlass()`
  registers SkiaSharp's handlers. Demo `ports/dotnet/MauiDemo` runs on a Pixel 7
  ([evidence](review/atlas/maui-2026-10-20/README.md)); CI builds all three targets and the APK,
  packs the package and checks it carries no iOS assets; the publish workflow packs it with the others.

### Added — Flutter on pub.dev (20 October)

- The Flutter package is ready for pub.dev as **`liquiglass`**: pub.dev refuses `liquidglass` because
  `liquid_glass` exists. Import `package:liquiglass/liquiglass.dart` (was `liquid_glass.dart`). The
  package has pub.dev metadata, its own CHANGELOG, a Pixel 7 screenshot, and declares Android, Linux,
  macOS, Windows and web (not iOS); `flutter pub publish --dry-run` is clean and runs in CI.
- `set_version.py` writes the package's version (`-SNAPSHOT` as `-dev`); CI fails if it drifts.
- `publish.yml` gains a `pub-dev` job: pub.dev automated publishing through GitHub Actions OIDC, no
  stored credential. It stands down until the owner's first upload ([publishing](docs/publishing.md#pubdev-once)).

## 0.2.2 (2026-10-20)

0.2.1 with npm on [trusted publishing](https://docs.npmjs.com/trusted-publishers): the npm packages are
published through the workflow's GitHub identity, and no npm token is stored. The library is
unchanged. Release notes: [.github/release-notes/v0.2.2.md](.github/release-notes/v0.2.2.md).

## 0.2.1 (2026-10-20)

0.2.0's code on every registry: Maven Central (`liquidglass`, `liquidglass-material3`,
`liquidglass-bom` and the Gradle plugin `dev.shayxo.liquidglass`), npm (`@shayxo.dev/liquidglass` and
`@shayxo.dev/liquidglass-react-native`) and NuGet (`LiquidGlass`, `LiquidGlass.WinForms`,
`LiquidGlass.Wpf`, `LiquidGlass.Avalonia`). Release notes:
[.github/release-notes/v0.2.1.md](.github/release-notes/v0.2.1.md).

- **npm packages are scoped.** npm refused the names `liquidglass` and `liquidglass-react-native` as
  too similar to existing packages. Install `@shayxo.dev/liquidglass` (import
  `'@shayxo.dev/liquidglass'`) and `@shayxo.dev/liquidglass-react-native`.
- **NuGet upload fixed.** The publish step passed a quoted wildcard that `dotnet nuget push` could not
  resolve on the Windows runner; bash now expands it, and a dry run pushes to a local feed so the
  step is exercised on every pull request that touches the release files.

## 0.2.0 (2026-10-19)

The first release since 0.1.0. It reached **Maven Central only** (`liquidglass`,
`liquidglass-material3`, `liquidglass-bom` and the Gradle plugin `dev.shayxo.liquidglass`): npm refused
its package names and the NuGet upload step failed, both fixed in 0.2.1. The Flutter port is source
only. Release notes:
[.github/release-notes/v0.2.0.md](.github/release-notes/v0.2.0.md). Breaking: 0.1.0's
`LiquidGlassScene`/`LiquidGlassState`/`liquidGlassSource`/`liquidGlass` API is removed in favour of
`LiquidGlassTheme` and `Modifier.glassEffect`. The development log of everything in it follows, newest
first, down to 0.1.0.

### Atlas stabilization

### Added — one tag publishes everything (19 October)

- The release workflow publishes every artifact from one tag: the library, Material 3, the BOM and,
  new, the Gradle plugin `dev.shayxo.liquidglass` on Maven Central; the `liquidglass` web component and
  `liquidglass-react-native` on npm (with provenance); the four NuGet packages; and a GitHub release. It refuses before uploading
  unless the tag matches the version, the commit is on `main` and passed CI, and the release notes
  exist. Pull requests that touch the release files run it as a dry run.
- `.github/scripts/set_version.py` sets the one version everywhere it is written down.
- Rehearsed end to end on 19 October: a new desktop project applied `id("dev.shayxo.liquidglass")`
  from a local Maven repository, got `liquidglass` and `liquidglass-material3`, `liquidGlassAdopt`
  switched its `Button` import to the glass one, and it compiled. NuGet publishes through trusted
  publishing, with no stored key; npm uses a token for its first release, then trusted publishing too
  ([publishing](docs/publishing.md)).

### Checked — Flutter and React Native on a phone (20 October)

- The React Native demo runs on a Pixel 7 (Android 17) as on the emulator: the lens, a light surface
  and a dark bar. [Evidence](review/atlas/react-native-2026-10-19/README.md#on-a-phone-pixel-7--20-october).
- The Flutter example runs on a Pixel 7 (Android 17) on Impeller's Vulkan backend, the right way up
  and refracting the list live, as on the emulator's OpenGL ES backend.
  [Evidence](review/atlas/flutter-2026-10-19/README.md#pixel-7-vulkan--20-october).

### Added — Flutter (19 October)

- `liquidglass` for Flutter (`ports/flutter`, new, unpublished): `GlassSurface` draws the material over
  the live backdrop on Impeller, through `ImageFilter.shader`, with the shader generated from the
  production SkSL (`tool/generate_shader.mjs`). Its uniforms equal the CanvasKit port's to 1e-6; it
  runs on the Android 17 emulator (OpenGL ES). The wide tone kernel is approximated with thirteen
  Gaussian taps; Vulkan devices are not checked yet. [Guide](docs/flutter.md).

### Changed — Skia's own backdrop is not planned (19 October)

- Docs: the overlay stays on every platform. Skia's own backdrop would fix the stacking on desktop and
  web, but the 18 October spike shows glass drawn in place goes blank under any translucent ancestor
  (every fade animation); the guides had said it would also lift the alpha limit, which it would not.

### Fixed — React Native on Android (19 October)

- `liquidglass-react-native`: on Android the surfaces drew nothing. The painter's offscreen buffer was a
  GPU surface made on the JS thread, whose texture a picture replayed on React Native Skia's render
  thread cannot draw; the adapter now makes raster surfaces. Found and confirmed on the Android 17
  emulator; the docs now say the source must be opaque.

### Added — React Native (19 October)

- `liquidglass-react-native` (`ports/react-native`, new, on npm): `GlassSurface` and `GlassLens`
  for a React Native Skia `<Canvas>`, on Android and React Native Web. A small adapter runs the
  CanvasKit painter and the production shaders unchanged on React Native Skia's API. In React Native
  Skia's headless build its glass equals CanvasKit's (no channel off by more than 1). Not yet run on a
  device; the backdrop is a snapshot (`makeImageFromView`). [Guide](docs/react-native.md).

### Changed — `GlassTabBarStyle.Ios27` is measured (19 October)

- The preset's guessed numbers are replaced with iOS 27's measured rim: the outer contour (0.61), the
  bar's lit edge scaled to iOS 27's peak and half-depth, and no dispersion. `Dark` is unchanged.

### Changed — the showcase scrolls under its glass; tooltip fix (19 October)

- `MaterialShowcase(startScroll = …)` starts scrolled, so the album art passes under the top bar's
  glass. The README and docs show it that way, and the star button has a glass tooltip.
- A long press that shows a glass tooltip no longer also clicks its anchor; the release is consumed,
  as in Material.
- The glass `DropdownMenu` stays inside the window, as Material's does. Where there's no room after
  its anchor it ends at the anchor's end, and where there's no room below it opens above. Before, a
  menu under an action at the right edge ran off the screen.

### Fixed — Android: tall frosted glass lost its backdrop (19 October)

- On Android, a tall frosted panel near the bottom of the screen showed the page only in a band at its
  top. A Material sheet is one example. The frost blur was chained ahead of the shader and evaluated
  only inside the window, while the shader also samples a strip recorded below the element. Android
  now blurs the backdrop in a nested layer instead; desktop and the web are unchanged. Evidence:
  `review/atlas/android-frost-2026-10-19`.

### Added — Avalonia (19 October)

- `LiquidGlass.Avalonia` (new, .NET 8, Windows, macOS and Linux): glass over native Avalonia 12 content,
  the WPF adapter's shape (`VisualBackdrop.Capture`, `GlassBitmap`). The capture honours the platform's
  channel order. `AvaloniaDemo` renders headlessly with Avalonia's Skia renderer and checks the pixels,
  in CI on Linux and Windows; the package is packed (not published).

### Added — WPF (19 October)

- `LiquidGlass.Wpf` (new, .NET 10, Windows): `VisualBackdrop.Capture` renders the host's own WPF subtree
  into the painter's backdrop, and `GlassBitmap` is a `WriteableBitmap` the painter draws glass into,
  shown with an `Image` under ordinary WPF labels. `WpfDemo` has a glass card and a draggable lens over
  WPF content; `--capture` renders it offscreen and checks the pixels, in CI on Windows. Packed (not
  published) with the other NuGet packages.

### Added — the rest of Material 3 in glass (19 October)

- Glass versions of the remaining Material 3 components that have a container:
  - the four icon toggle buttons and the three elevated chips;
  - `RangeSlider` and `Surface`;
  - tooltips (`TooltipBox`, `PlainTooltip`, `RichTooltip`), drawn in the app's window;
  - the drawer sheets and `NavigationDrawerItem` (iPadOS's sidebar);
  - `DatePickerDialog`, `TimePickerDialog`, and `DatePicker` and `DateRangePicker` without their
    opaque container;
  - `LeadingIconTab`, `PrimaryScrollableTabRow` and `SecondaryScrollableTabRow`.

  They take Material's parameters, and `liquidGlassAdopt` switches their imports.
- The top app bar's buttons, the toolbars, the rail and the segmented capsules draw
  `GlassMaterialDefaults` glass (new `toolbar`), so they carry the iOS 27 rim like the rest.
- Back dismisses glass dialogs, sheets, menus and tooltips, through Compose's navigation-event
  dispatcher: the system back on Android, Escape on desktop and the web.
- Ports: a surface binds Compose's `GlassLight.Default` (-0.35, -0.94), as `Glass.Regular` does, where
  the CanvasKit and .NET painters bound (0, -1). `light` (Skia, npm) and `LightX`/`LightY` (.NET) choose
  another, for example iOS 27's measured overhead light (0, -1). The canvas engine of `<liquid-glass>`
  now lights as its HTML engine does.
- `ModalBottomSheet` has a partially expanded stop: a sheet taller than half the window opens there
  (iOS's medium detent). Dragging settles at the nearest stop, and `hasPartiallyExpandedState` is new.

### Added — iOS 27's rim (19 October)

- `GlassStyle.outerContour` and `outerContourFloor` (new, opt-in, 0 by default): iOS 27's dark
  contour, drawn outside the outline and strongest where the rim is unlit. The values are measured
  on iOS 27's 44 pt buttons over white: 0.61 of the backdrop at the sides in a dark appearance,
  0.31 in a light one, and zero at the lit poles (`docs/research/ios27-measurements-raw.md`).
  Drawn by `glassEffect`; the Skia, .NET and npm ports carry the same shader.
- Material 3 components use iOS 27's measured rim by default (`GlassMaterialDefaults.control`).
  That means the contour, light straight overhead instead of 20 degrees left of it, and iOS 27's
  chamfered edge highlight with its broad lobe (+65 levels in the outermost pixel, power 2.6). It also
  means no dispersion. The core's `Glass.Regular` is unchanged.

### Changed — Material 3 panels are frosted; a new showcase (18 October)

- `GlassMaterialDefaults.panel` (new): cards, dialogs, sheets, menus, snackbars and the search results
  panel draw `regular` glass over a backdrop blurred at the measured system-backdrop sigma (7.7 dp).
  Using it for in-app panels is authored. Without the frost, a panel's wide rim compressed the text
  beside it into a smeared copy along the edge. Controls keep unblurred `regular`, except on a panel,
  where `regular` and `prominent()` take the panel's frost. `SearchBar` and
  `DockedSearchBar` take `panelGlass` for the results panel.
- The Material 3 showcase (Android sample, desktop, web demo) is a music library over a mesh
  wallpaper with detailed album covers, so there is something for the glass to bend.
  `MaterialShowcaseRenderTest` renders it on desktop.
### Added (18 October, Phase 6: the web component)

- `ports/npm`: `<liquid-glass>`, a standard custom element for any page or framework (npm package
  `liquidglass`, **unpublished**: `"private": true` until the owner publishes). Slotted content stays
  ordinary, accessible HTML above the glass. Attributes `variant` (`regular`, `clear`), `shape`
  (`capsule`, `rect`), `radius`, `tint` (as `Glass.tint`), `scheme` (`auto`, `light`, `dark`), `engine`,
  `interactive` (the Calm press and a glow under the pointer; honours reduced motion) and `lensing`.
- Two engines. `engine="html"` refracts the live page through `backdrop-filter`: in Chromium an SVG
  displacement filter whose map is computed by ports of the shader's geometry (the measured
  resting-corner map for Regular, the Snell bevel for Clear); elsewhere a frosted blur. It is an
  approximation; the material numbers are inherited from `GlassStyle.inApp` / `GlassStyle.Clear` and
  checked against the Kotlin sources by a unit test, and the CSS-only values are labelled authored.
  `engine="canvas"` draws the production shaders through the CanvasKit painter over a `source` image,
  canvas or video; CanvasKit is an optional peer dependency loaded only when used.
- Tests: 27 Node unit tests (attribute parsing, engine selection, lensing detection, displacement and
  rim map pixels, the ported functions evaluated against the production SkSL text, provenance against
  Kotlin) and 7 Playwright tests in Chromium plus an opt-in capture test; a `web-component` CI job runs both. Evidence:
  [review/web-component-2026-10-18](review/web-component-2026-10-18/README.md). Guide:
  [docs/web-component.md](docs/web-component.md).
- CanvasKit port: `drawSurface`'s material takes an optional `tint` ([r, g, b, a]), Compose's
  `Glass.tint(color)`: it replaces the tint colour and opacity (8-bit, as Compose stores it) and keeps
  the dark lift. Default output is unchanged.

### Added — NuGet packages (18 October, Phase 6)

- The .NET port is packaged as `LiquidGlass` (the SkiaSharp painter, .NET 8) and `LiquidGlass.WinForms`
  (.NET 10), at the library's version, with Source Link and symbols. CI packs both and checks their
  contents on Linux and Windows. Not published.

### Added — `liquidglass-material3` (18 October, Phase 3)

- A new artifact with Liquid Glass versions of the Material 3 components, with the same names and
  parameters, iOS 26 shapes and motion, and the app's colour scheme: `Scaffold`, `NavigationBar` and
  `NavigationBarItem` (iOS's floating tab bar with its lens, and an optional fused search button), the
  top and bottom app bars, `NavigationRail`, every button, icon button and FAB, cards, chips, `Switch`
  and `Slider` (thumbs that turn into glass lenses), segmented buttons and tab rows (iOS's segmented
  control), `SearchBar`, `AlertDialog`, `ModalBottomSheet`, `DropdownMenu` and `Snackbar`. Every
  component takes a `glass` parameter; the bars take `layout = GlassLayout.Material`.
- `GlassOverlay { }` (new, core): composes content above everything under the theme, in the same
  window, where glass draws in place. Dialogs, sheets and menus use it.
- `GlassTabBarTestTag` (new, core): the tab bar's gesture node, for UI tests.
- `liquidglass-bom` (new): one version for every artifact. Gradle plugin `dev.shayxo.liquidglass`
  (new): adds the artifacts, and `./gradlew liquidGlassAdopt` switches an app's Material 3 imports to
  the glass versions once (`--dry-run` to preview). Not published yet.
- Live demo: `#material3` shows a Material 3 app built from `liquidglass-material3` beside Atlas Studio.

### Changed (18 October)

- `liquidglass-material3` depends on Material 3 `1.9.0`, the stable release Compose Multiplatform
  1.12.1 pairs with, so it never pulls an alpha Compose into an app (the sample keeps its 1.13 alpha).

### Fixed (18 October)

- A glass element composed after a tab bar (Atlas's and the Material bar's search button) drew its
  glass but not its icon under `LiquidGlassTheme`: the overlay drew every element before every portal.
  It now draws elements and portals in the order they entered composition, as an ordinary layout would
  (`GlassThemeComponentsTest.anAccessoryComposedAfterTheBarDrawsOverIt`).
- Android: the sample opens the Material 3 showcase (shared with desktop and the web demo); first
  Android run of the new API, on a Pixel 7 and an emulator ([evidence](https://github.com/shayann07/liquidglass/blob/main/review/atlas/android-material3-2026-10-18/README.md)).
- `liquidglass-material3`: checked multi-choice segments use `onPrimary` text on their tinted glass;
  `Snackbar` is drawn by the library, without Material's surface shadow inside the glass.

### Changed — new public API (18 October, Phase 2)

Breaking: the library now has one way in, named after SwiftUI's Liquid Glass API.

- `LiquidGlassTheme { App() }` (new): wrap the app once, as `MaterialTheme` is. It records the screen;
  every glass below it refracts what is behind it, and nothing has to be marked as a backdrop. Nested
  themes change the defaults, appearance and accessibility settings (`reduceTransparency`,
  `increaseContrast`) for their subtree.
- `Modifier.glassEffect(glass, shape = CapsuleShape)` (new): any composable becomes glass, at any depth:
  inside scrolled lists and under layers, its content on top, its clicks its own. Without a theme it
  draws a frosted stand-in and warns once.
- `Glass` (new): `Glass.Regular`, `Glass.Clear`, `Glass.Identity`, and the measured `Glass.Lens(m)`,
  `Glass.TabBar` and `Glass.Toolbar`, refined with `.tint(color)` and `.interactive()`. `Glass(style)` takes
  every optical parameter.
- `GlassEffectContainer(spacing)` (new): glass inside merges with a liquid neck when close.
  `Modifier.glassEffectID(id, namespace)` with `rememberGlassNamespace()`: a piece that reappears morphs
  from where it was.
- `GlassTabBar` takes no state.
- Removed: `GlassScene`, `LiquidGlassScene`, `ProvideLiquidGlassState`, `LocalLiquidGlassState`,
  `currentLiquidGlassState`, `LiquidGlassState`, `rememberLiquidGlassState`, `Modifier.liquidGlassSource`,
  `Modifier.liquidGlass`, `LiquidGlassContainer`/`glassMember` and `rememberBackdropInversion` (internal now).
- How glass gets its backdrop: on every platform it draws over the root's recording with its content on
  top, so it never samples itself. Limits: glass draws above other content, and an ancestor's alpha does
  not fade it. Skia's own backdrop (desktop and web) is the planned replacement there; the spikes are in
  [review/atlas/backdrop-spikes-2026-10-18](review/atlas/backdrop-spikes-2026-10-18/README.md).

### Added (18 October, Compose for Web)

- A `wasmJs` target: the library runs on Compose for Web with the full shader. Web draws through
  the same Skia as desktop, so desktop's actuals moved to a shared `skikoMain` source set; the optics
  are unchanged. `:web` is Atlas Studio in the browser (`./gradlew :web:wasmJsBrowserDevelopmentRun`),
  checked in Chromium ([record](review/atlas/web-2026-10-18/README.md)).
- Atlas Studio's tab icons are drawn rather than typed: the web build's font has no ✦ or ▦.

### Changed (18 October, the owner's device check)

- `GlassStyle.capEdgeShadow` (new, default 0): the edge contour darkens by `1 + capEdgeShadow * nx^2`
  where the outline faces sideways. Light `Calm()`'s bar uses 2: its rounded ends' outermost point reads
  177-186 against iOS's 178-182 (was 236-241; left end 12 levels rms, was 45; IMG_7048 36.5 s). Ports
  follow through the exported material.
- The sample's search button: lift share 0.65 (was 0.6), scored on IMG_6756's search episodes with their
  scripts re-inferred for the 0.2 drag gain: level 6.4 levels rms (7.1), the rise within 1-2 px of iOS's
  (3-6 short). The ports' free-button default follows.
- App Store scene: its drawn glyphs 1.12x about their centre and its labels SemiBold, closer to the
  recording's icons (LOLL8185 43.9 s); layout unchanged. The band under the bar reads p50 / p75 68 / 80
  against iOS's 69 / 80; its p90, on the icons' edges, moves from 133 to 236 against 207.
- Atlas Studio: `-Patlas.windowSize=WxH` (dp) opens a window of that size instead of maximized. Wide
  (1400 x 900) and narrow (420 x 880) captures on both GPUs are in
  [review/atlas/desktop-2026-10-18](review/atlas/desktop-2026-10-18/README.md).

- Ports (CanvasKit, .NET): everything from the device check that the ports draw. The held lens's rim band
  (`bodyBandScale`), light Calm's held-lens contact shadow, a held lens's neck to the free button
  (`lensFuse` / `AccessoryFuse.LensFuse`), and `itemFollow` / `ItemFollow` on the navigation state (items
  share the bar's press only on a hold). Their blurs now convert a Compose radius to a Skia sigma as
  Compose does (`0.577 r + 0.5`); the raw-page blur had been about 1.7 times too strong. Checked: CanvasKit
  44/44, .NET 16/16, material reproduced from the Kotlin fixture.

- Landing timing (18 October, measurement): WhatsApp's motion paths agree within one frame of the 60 fps
  recording (best shift -17 ms, was -42; path rms 62.7 px, was 79.9). A sweep of the inferred lift does
  not move it further.

- Owner acceptance (18 October): the owner checked the build by finger on a Pixel 7 and accepted all six
  points of their device check. Device cost with the dark lens's wider raw blur: GPU p90 +2 ms (15), p50
  and janky share unchanged.

- Dark `Calm()` held lens: the raw page wider and stronger (`rawShare` 0.5 at `rawBlur` 50 dp, was 0.22 at
  20 dp). Scored by region on docs/2 5.75 s and IMG_6756 35.47 s, iOS / ours: top band 64/60 (was 51),
  bottom 75/73, text p90 102/101, p50 82/86, black 47/48; five regions 2.8 rms (6.6).
- `GlassInteraction.pullReleaseDampingX` (new, default NaN = the shared spring); `Pullable` uses 0.3.
  Released sideways, iOS's search button springs further past its place than upward (IMG_6756 19-34 s):
  centre 2.3 px rms (2.6), half-width 2.2 (2.7), its narrowest 83 px (86; iOS 78). Ports follow.

- `GlassTabBarStyle.Calm()`: the bar's rounded ends lens what lies beyond them (`GlassStyle.capFold`,
  new, default 0; 1 in `Calm()`), as iOS's do (an icon's edge outside the bar shows ~30 px inside its cap,
  IMG_7049); the straight runs keep their rest offset, since a fold there garbled text. Before, the caps
  sampled inward only and mirrored the letters under them. Ports follow through the exported material
  and shaders.

- `GlassTabBarStyle.Calm()`: the tab icons stay where they are laid out on a tap and follow the bar's
  growth only while the lens is held (`GlassTabBarStyle.itemsFollowBarPress`, new, false in `Calm()`).
  iOS's icons hold within 0.1 px through a tap and move 14 px out (4% larger) on a hold (IMG_7048);
  ours slid 7 px and back on every tap, which the owner saw as the icons jumping.
- `GlassTabBarStyle.Calm()`: a tapped selector travels with the held lens's optics, as a hold or a throw
  does (`GlassTabBarStyle.tapTransitFold`, new, 0 in `Calm()`). The fold it replaced smeared the icon it
  carried and lingered after landing. `CalmRecordedSpace()` keeps the fold.
- `GlassTabBarStyle.Calm()`: the held lens refracts its rim over 0.3 of its own band
  (`GlassStyle.bodyBandScale`, new, default 1). With the full band the bar's straight edges and rounded
  end showed through as a second capsule inside the lens ("two glasses"); iOS's lens fills with the bar
  nearly to its rim (IMG_6756 35.47 s, docs/2 5.75 s). Region levels move by 2 at most.
- `GlassInteraction.Pullable`: drag gain 0.2 (was 0.35) and elongation 0.124 (was 0.217), keeping the
  measured 0.62 shape-to-travel ratio. The owner found the held search button far too eager to follow a
  swipe; the recordings carry no finger trace to fit this gain. Ports follow.
- `GlassTabBarAccessory.lensFusion` (new, default 0): a held lens at the accessory's end joins it through
  a neck of its own, as iOS's Phone bar held at the search end shows (IMG_7048 38.5 / 42 s). The shader's
  fuse now also joins selector bodies. The sample's Phone scene uses 12 dp.
- Light `Calm()`: resting pill 10/255 below the bar (Phone 245 over white; was 13/255 = 242); light
  `CalmClear()` carries WhatsApp's 19/255 (236); the App Store scene's pill is clear over white (255).
- Light `Calm()` held lens shadow: tight and offset down (`GlassStyle.contactShadowBlur` 6 dp,
  `contactShadowOffset` 5 dp, `contactShadowInside` 0.5, all new; `contactShadow` 0.45): no shadow above
  the lens, 242 below it as iOS's; profile rms 2.6 against 3.7.
- `CalmClear()` dark: text under the bar is one soft copy (`blurRadius` 7 dp, `legibility` 2): band p50 /
  p75 68 / 79 against iOS's 69 / 80 (was 67 / 98, with dark halos). Ports follow.

### Changed (13 October, continuous iOS comparison)

- `GlassTabBarStyle.Calm()`: a tapped selector travels as refracting glass (`tapOptics`); a
  thrown lens stays formed while travelling (`GlassPoseSpec.releaseFormationSpeed`); wall
  landings no longer convert speed into a tall circle (previous model kept as
  `GlassPoseSpec.CalmImpact`); the grasp uses the default 78/s response; held-lens specular 0.2.
- Calm dark material recalibrated to the owner's two iOS recordings: bar 45 at rest / 53 held,
  resting selector black 0.42 without negative lift. `V3()` and older presets are unchanged.
- Calm held travel follows video 2's fast reversals: the lens stays under the finger (grasp
  120/s, was 40) and a hold forms in about 80 ms (held formation rise 50/s; taps unchanged).
  Held stretch and squeeze are unchanged to keep the measured IMG6735 flattened pair.
- Calm lens and bar edges measured against video 2: bar rim halved (the "white outline" was the
  bar's rim seen through the lens), held lens edge line .26, a stronger and wider rim glow and
  `rawShare` .28 so the page shows crisply through the lens.
- Calm moving lens: a reflective rim at its caps (icons wrap mirrored along the curve, as in
  iOS) instead of the clipped held map, and every icon under the lens shows its selected ink.
  A still held lens keeps the measured held map and blends to the rim mirror as it travels.
- Calm wall landings (14 October): the pill presses against the bar's end and narrows from
  behind before widening back (`GlassPoseSpec.releaseTrailingEdge`, trailing edge 13/s), matching
  video 2's measured 241 → 285 px recovery without growing taller; the leading edge no longer
  flies past the end. The previous landing is kept as `GlassPoseSpec.CalmFreeLanding`.
- Sample: `PhoneActivity` hosts the Calm comparison scene on Android (verified on an API 37 emulator).
- `PhoneScene` geometry: a 95 pt resting pill with 8 pt ends, measured in video 2.
- Calm moving lens (14 October): a deeper reflective rim (`rimMirror` 1.0, the whole band; up to 1 is now allowed)
  gives a larger swirl at the caps, with a faint colour split confined to the mirrored band.
- Calm dark bar reaches toward the finger while held (`GlassInteraction.pressReach` 0.03): the
  finger's side grows further, the far side as before; edges within 2–5 px of iOS.
- Calm dark bar snaps back after lift like iOS's (0.12 s, slight overshoot), so the search-button
  neck parts on time (`GlassInteraction.releaseStiffness` / `releaseDamping`).
- Atlas Studio redesigned: one full-window scene moving under all the glass, the Calm bar fused
  with a magnifier button as the only scene switcher, a glass top bar and an anchored card.
- Repository description and README rewritten for developers; lab detail moved to linked records.
- Calm wall landings, second pass (14 October): after the lift the trailing edge travels on to the
  wall underdamped and keeps a tenth of its speed once the leading edge meets the end (`GlassPoseSpec.releaseTrailingInOmega` /
  `releaseTrailingInZeta` / `releaseTrailingCarry`), and a still-formed lens may press past the
  bar's end (`releaseWallGive`). Scored on all ten right-wall landings of video 2: rms 4.9 px
  (15.4 before); the 9.9 s landing now narrows like the others. The first two-edge landing is
  kept as `GlassPoseSpec.CalmCriticalLanding`.
- Fixed: `GlassStyle.rimMirror` above 0.5 was clamped to 0.5 when building uniforms, so the moving
  selector's rim mirrored half the band intended since 14 October.
- Calm moving selector: cap content is drawn out radially toward the rim (`rimMirrorGain` 0.4), the
  stronger swirl of iOS's moving lens.
- Phone and Atlas Studio: the search button's neck gives way to a lens pressing into its end
  (`GlassTabBarAccessory.yieldsToLens`); with the bar's release spring now starting one frame in,
  the neck parts a median 17 ms after iOS's (67 before).
- Calm dark bar: once its lens is dragged it keeps 70% of its press, reach and pull
  (`GlassInteraction.dragPressShare`), as iOS's does; the search-button neck now parts with
  iOS's (median 0 ms). The moving selector's rim colour split is .05 (was .025).
- Ports: `drawSelectorInk` / `DrawSelectorInk` and the scene's `setSelectedInk` draw the host's
  selected ink inside the selector through the production content pass.
- Calm landings: a more stretched lens closes faster (`releaseTrailingStiffening`), scored on the
  left-wall landings too (rms 4.1 px left, 5.5 right); a lens released still keeps its glass
  briefly (`releaseStillHoldSeconds`), as iOS's does after a stationary hold.
- CanvasKit and .NET selectors use the Compose Calm bar and selector material itself
  (`calmSelectorMaterial`, `drawCalmBar`, `through`), checked value by value against the Kotlin
  factory; the resting inset reads 33 over a dark page (12 with the earlier approximation).
- Pass 12 (14 October), dark Calm against both recordings:
  - Fixed: the selected ink inside a held lens was scaled about the wrong point (the node's origin
    instead of the bar's centre), so it sat about 17 px left and 10 px up of where the bar's own
    press puts it. It now lands on iOS's position (203 px against 204 at 1.8 s and 5.75 s).
    The selected ink also no longer follows the bar's reach toward the finger.
  - Fixed: after a still hold, the pose frame loop woke behind the frame it presents, so a throw
    from rest ran about 1.5 frames behind the finger throughout. A woken loop now presents its
    frame's own time, catching up from the waking input's timestamp (only when the two clocks are
    0-50 ms apart; travel-only specs).
  - The held lens's outline is a ~65-level grey line over any ground (`GlassStyle.heldEdgeTone`)
    with its inward glow off (`heldGlow` 0, was 2), instead of a bright white ring.
  - A held lens travelling fast widens and flattens at constant area (`GlassPoseSpec.heldTravelStrain`
    .5 at 10/s; held reference speed 8, squeeze .52): 427 x 144 px at the 19.0 s reversal against
    iOS's 458 x 145 (375 x 182 before). Wall-landing scores move from 5.3 / 4.3 to 5.7 / 5.1 px rms.
    The previous Calm is kept as `GlassPoseSpec.CalmWallLanding`.
  - A tapped selector's fold also runs, at a quarter strength, along its straight top and bottom
    (`foldAtCorners` .75 in transit), bending icons and labels at those edges as iOS's does.
  - Comparison scene: neutral placeholder page with iOS-like call-log typography and glyphs.
- Pass 13 (15 October), the pass 12 residuals:
  - The held lens sits where the bar's press puts its tab (203 px at Calls, was the finger's 219),
    and the bar no longer reaches toward the finger (`pressReach` 0, was .03): its cap now shows
    iOS's dark band between the lens edge and the bar's end instead of the bar poking past it.
  - The held lens's inward glow is back as a floor (`GlassStyle.heldGlowFloor`; glow 1.5, 5 dp):
    the measured band over the black page (ours 26, 25, 22 ... 10 against iOS's 36, 22, 20 ... 9)
    without bright lines over the bar. The outline is two pixels wide.
  - The lens shows less of the raw page (`rawShare` .05, was .28): text under it reads p95/p99
    82/105 against iOS's 84/109 (108/112 before).
  - Held travel strain .6 with squeeze back at .47: 444 x 145 at the 19.0 s reversal (iOS about
    444 x 141 at this bar size). Wall landings 6.0 / 5.4 px rms; landing series 2.8 / 5.2.
- Pass 14 (15 October), the pass 13 residuals:
  - The held lens lays a grey veil over what it shows (tint .55 of 0x3A, raw page share .22):
    the page avatar under it reads 82.5 against iOS's 80 (98 before), text p95/p99 80/105
    against 84/109, plain interior 62 against 64.
  - A held lens's tint mask is read where its refracted view comes from, so the band of black
    page it pulls in beyond the bar is not tinted (15.4 against iOS's 15.3-15.8).
  - The inferred finger for the first second of video 2's held reversals moves rightward 20 ms
    earlier (`tools/reference-replay/reversal-script.txt`): the lens reaches each icon a mean
    2 ms after iOS's going right (10 ms before) and 3 ms going left, over the whole episode.
- Pass 15 (15 October): the owner's recordings are HDR10 and had been compared decoded as if
  they were sRGB, which raised every dark level. Every comparison is now made in display colours
  over five recordings (docs/1, docs/2 and three more Phone-bar recordings in the private
  dataset), and the dark Calm material is re-fitted there:
  - Bar at rest 32/255 lift (was 45), resting pill black .55 (was .42), held lift 10/255 (was 8):
    the bar reads 33 against iOS's 33 over 44 rest moments (was 46), the pill 15 against 10 on a
    black page and 15 against 15 on a Calls page (26-27 before).
  - The previous calibration stays available as `GlassTabBarStyle.CalmRecordedSpace()`; the
    Atlas replay's `calm-recorded` variant renders it with the old scene palette.
  - Review fixes: the lift's own position reaches the pose controller before the release (Compose,
    CanvasKit, .NET); a lens thrown from a hold keeps its travelling cap mirror while it flies; the
    pose body's cap weight is full from where the cap begins; the fold's colour split stays at the
    caps and a held lens's fringe order follows its rim mirror; both ports keep the move that crosses
    the touch slop; CanvasKit no longer blurs an unused copy of the page each frame; the press reach
    no longer squares its drag share.
- Pass 16 (15 October):
  - `GlassTabBarStyle.touchLift` (new, off by default; on in dark `Calm()`): the bar's held lift
    also applies for any touch, a tap included, as iOS's whole bar lifts on every touch. A tap's
    travelling pill now reads 47-51 against iOS's 48-53 (38-43 without it); the bar at the tab a
    tap never visits follows iOS's from 50 ms on.
  - Calm held lift 13/255 (was 10), held lens tint 0x2B at .55 (was 0x3A) and resting pill black
    .58 (was .55): held bar 46 against iOS's 45-47, held lens over a black page 48 against 48 (was
    55), resting pill 14 on both pages (iOS 10 and 15).
  - Sample `PhoneScene`: tab labels Medium with 0.7 sp spacing and a wider keypad grid, measured
    against iOS (labels 11% wider at the same height; grid 72 x 69 px).
- Pass 17 (15 October): iOS 26's Phone search button presses, pulls and lands.
  - `GlassPullShape.Free` (new, opt-in): the body follows the finger, stretches and thins along
    the pull, carries its content, and on release lands squashed against its resting edge away from
    the pull before rebounding (`GlassMotion.FreeRelease`: 0.45 damping, 264 stiffness, fitted on the
    leading edge of four releases, 4.8 px rms).
  - `GlassInteraction.pullLiftShare` (new, default 1): the press glow settles to this share once the
    finger moves (the search button: 0.7, from 95 to 70 grey levels).
  - `GlassAccessoryLink`, `rememberGlassAccessoryLink()`, `liquidGlass(link = ...)`,
    `GlassTabBarAccessory(link = ...)` (new, opt-in): the bar's outline follows the accessory as it
    is pressed (a neck) and pulled away (the neck breaks), read while drawing so it never trails.
  - Sample `PhoneScene`: the search button uses them. Against iOS over seven presses: centre 1.9 /
    3.0 px rms (14.3 / 41.1 passive), height 4.3, width 2.8, brightness 8.0 levels (19.1).
- Pass 18 (15 October): what the glass shows of the page.
  - Calm dark resting pill and held lens are clear glass with an offset, not a tint. Over a black
    page they read as before; over content iOS's pass far more of the page. Pill: the bar less
    22/255 (the inset's originally measured law), docs/2 p10/50/75 12/29/47 against iOS's 15/25/51
    (14/21/29 at .58 black), black 10 against 10. Held lens: the bar plus 0.30 of the raw page less
    3/255, docs/2 52/75/135 against 62/80/135 (50/60/87), black 48/49 against 48/49 (the raw share
    was removed the same day after a regional score; see below).
  - `GlassStyle.softDetail` (new, default 0; 1 on dark Calm's bar and lens): page text under the
    glass is one soft copy, as iOS's is, instead of the along-axis high-pass whose taps drew ghost
    copies of every glyph and whose rotated scatter turned the rest into grain (horizontal detail
    2.72 against iOS's 2.77; 4.94 before).
- App Store comparison (17 October): sample `AppStoreScene` / `AppStoreGeometry`, iOS 26's App Store
  five-tab bar (348 x 62 pt, 64 pt slots, an 83.7 pt resting pill) over a dark card, measured on the
  owner's recording; the Atlas replay renders it (`appstore` variant). Its one-touch drag across all
  five tabs tracks iOS's at 10.1 px rms, best shift -8 ms, as the Phone bar does. No library change.
- Calibration-target check (17 October): `GlassCalibrationRenderTest.renderTheCalmBarOverTheDarkTarget`
  (opt-in) renders dark Calm's bar over the calibration target for comparison with the Photos-toolbar
  screenshots. Calm's bar reads 102.2 / 20.8 (mean / standard deviation) against iOS's 102.6 / 20.6 at
  the default tint: the display-colour re-fit agrees with the screenshot-measured law.
- Ports (17 October): the free button (`createFreeButton` in CanvasKit / the portable package, `FreeButton`
  in .NET): the Phone search button's press, pull and landing, replayed frame by frame against a new
  Kotlin-exported fixture (`ports/skia/fixtures/free-button.json`; worst 0.009 px). The bar's outline
  link stays Compose-only.
- `GlassTabBarStyle.slotWidth` (new, 17 October; unspecified by default, so every preset is
  unchanged): equal slots of a set width centred in the bar; where they are wider than the bar
  allows, the end tabs close up to where their resting pill sits `pillInset` inside the bar's end.
  Measured on WhatsApp's iOS bar (owner's recording, 348 pt bar): the inner tabs are 71.5 pt apart
  (its widest item, 63.3 pt, plus 8 pt), wider than a fifth of the bar, and the end tabs are pulled
  in; the rule puts all five centres within 2 px, where slots sized per item miss by 13. The App
  Store bar fits the same rule with 64 pt slots. Pill, lens, hit targets, drag and the V3 body all
  follow the slots. Sample `WhatsAppScene` / `WhatsAppGeometry` (neutral placeholders, a plain disc
  for the profile tab); Atlas replay variant `whatsapp`. The CanvasKit and .NET ports have it too (`calmSelectorBar({slotWidth})`, `CalmSelectorBar.Create(slotWidth:)`).
- Calm dark held lens (17 October): no raw page share (`rawShare` 0, was 0.30). Scored by region
  against docs/2 and IMG_6756, the raw share drew the page's text under the lens sharp and white
  (p90 155 against iOS's 102). Without it the lens veils text as the bar does: p90 91, p50 75
  against 82, five regions 9.7 rms against 24.9. A black page is unchanged (48 against 47). The
  lens's top and bottom bands still read 11-12 below iOS's over content.
- `GlassTabBarStyle.CalmClear()` (new preset, 17 October; `Calm()` unchanged): dark Calm with the
  clearer bar of WhatsApp's and the App Store's iOS bars, black at 0.10 (the Phone bar's 0.41) over
  the same 32/255 lift. Measured in display colours on the owner's recordings, iOS against
  CalmClear (Calm): WhatsApp over its grey card bar 54 against 53 (46), pill 30 against 31 (24);
  over its chats page bar 43 against 41 (38), pill 19 against 19 (16); the App Store's plain bar
  67/65/64/59 against 66/64/63/58 (54/53/52/49); over black both 32. The pill and held lens look
  through the bar and follow it. Light appearance returns `Calm(false)`. The sample's
  `WhatsAppScene` and `AppStoreScene` default to it; Atlas replay variants `whatsapp-clear`,
  `appstore-clear`, and `whatsapp+plain` (the bare page). Ports: `calmBarMaterial({clear})` /
  `drawCalmBar(..., {clear})` / scene material `clear` in CanvasKit, `CalmSelectorMaterial.Bar(...,
  clear:)` / `DrawCalmBar(..., clear:)` in .NET, from the clear bar's uniforms exported with the fixture.
  Residual: text under the App Store bar still reads brighter than iOS's (p75 107 against 80).
- Contact shadow of a selector (18 October): cast by the lens's own body (two-disk or pose capsule)
  instead of its node, which spans the whole bar; a lens given a shadow shaded the bar far from itself.
  No shipped preset gave a lens a shadow until now, so their output is unchanged.
- Light `Calm()` held lens (18 October): `contactShadow` 0.5, a soft shadow under the lens (iOS 242
  for ~15 px below it; ours 241-243, profile rms 2.6 levels; IMG_7048 at 38.5 and 42 s). It also greys
  the lens where it overhangs the page, as iOS's does. Compose only: the ports draw no contact shadows.
- Light `Calm()` (17 October), measured on the owner's light-mode recording (Phone, App Store, WhatsApp,
  Threads; display colours). The bar passes 0.59 of the page plus 115 (iOS: 0.58 + 116 over seven page
  levels; the light toolbar it used passed 0.42 + 140), with a darker rim (246 top and bottom, as iOS's)
  and a faint shadow (251-253 against 253); the resting pill is clear glass 13/255 below the bar (iOS
  246 on the Phone's keypad page, 236 on WhatsApp's; ours 242); the held lens is clear glass with a
  light outline (243/255) and no raw page; touch lift and dark Calm's tap optics. Before, light Calm
  carried dark-page measurements (a 0.55 grey veil and 0.22 raw page on the held lens, a half-black
  pill). The old light preset stays as `CalmRecordedSpace(false)`. Residuals: iOS's held lens has a soft
  ~12 px edge band and a shadow under it (the shadow since 18 October); the App Store's pill is invisible
  over white (255; the 217-228 first recorded was its cards seen through it). Sample:
  `PhonePalette.Light` and `light` on `WhatsAppScene` / `AppStoreScene` (iOS system colours); Atlas
  replay variants `calm-light`, `whatsapp-clear-light`, `appstore-clear-light`. Ports follow through
  the exported material.
- Physical device (17 October): the Calm tab bar on a Pixel 7 (Android 17), dark and light, through
  tap, hold, drag, throw and the search button's pulls, recorded on the device
  (`review/atlas/android-device-2026-10-17/`). It keeps the 90 Hz display rate (UI thread 6.9 ms, GPU 8.5 ms
  p50 per frame; 2-3% janky). Sample: `PhoneActivity` takes `--ez light true` for light Calm and
  `--ef renderScale <f>`; `PhoneScene` takes `renderScale`.
- Phone search button (17 October): `pullLiftShare` 0.6 in the sample, the replay and both ports
  (was 0.7), the share the library's own note measured ("settles at 70 once dragged"). Scored on
  IMG_6756's search catalogue (`searchscore.py`): brightness 7.1 levels rms (was 8.0), half-width 2.7
  px (2.8), centre and height unchanged. Width ratio 1.3 over-thins upward pulls (71 against iOS's 79)
  and a longer pull limit overshoots the rise (124 against 112); both stay as they were.
- `CalmClear()` (17 October): `legibility` 2.5 on its bar. iOS's App Store bar compresses bright text
  under it (band p50 / p75 69 / 80); a plain clear bar gave 73 / 107, this gives 67 / 98 and leaves a
  plain card and WhatsApp's levels unchanged (53 / 31).
- Ports (17 October): the Calm bar's fusion with a free button (Compose's `GlassTabBarAccessory.link`).
  CanvasKit `accessoryFuse`, `accessoryYield`, `inscribedStadium` and `drawCalmBar(..., {fuse})`; .NET
  `AccessoryFuse.Compute` / `Yield` and `DrawCalmBar(..., fuse:)`. Checked against a Kotlin-exported
  fixture (`ports/skia/fixtures/accessory-fuse.json`, 18 press/pull cases: worst 0.00003 px in .NET)
  and drawn: a pressed button closes the gap through a neck and the bar's middle is unchanged.
- `GlassStyle.rawBlur` (new, 17 October; 0 dp by default, so `rawShare` stays sharp everywhere it
  was): the raw backdrop that `rawShare` adds is blurred first. Dark `Calm()`'s held lens now adds
  0.22 of the raw page blurred 20 dp (was no share since earlier on 17 October). iOS's lens reads about
  10 above ours wherever the page has content and the same over black; a blurred page term does
  both, where a tint or glow lifts black too and a sharp share redraws the text. Scored by region
  (docs/2 5.75 s, IMG_6756 35.47 s), iOS / ours: text p50 82 / 83 (was 75), p90 102 / 99 (91),
  bottom band 75 / 70 (63), black interior 47 / 48 (48), top band 64 / 53 (52); five regions 5.6 rms
  (9.7). The top band near the rim stays 11 low. Both ports blur the share the same way (`rawBlur`
  exported with the material fixture). Atlas replay `+lens=` takes it as the eleventh value.
- WhatsApp motion checked (17 October; no motion change): a white-fill tracker for WhatsApp's bar
  and finger scripts inferred from its touches (14 in JGEY2190 49.8-67 s). Read by the same tracker
  on both sides, `CalmClear()` on `WhatsAppScene` lands on the same tab 14/14 (nine throws' lifts are
  inferred from iOS's landing) and settles a median 21 ms before iOS (p25 / p75 -31 / +75 ms); the
  selection path is within 3 px at the median, p90 140 px in throws' flights.

### Added

- Calm navigation selector for the CanvasKit and .NET ports: `CalmSelectorController`,
  `createCalmNavigation` / `CalmNavigation`, `scene.addNavigation`, `painter.drawSelector` /
  `GlassPainter.DrawSelector`. A port of the Compose pose controller and gesture protocol, checked
  against Kotlin-exported trajectories (`ports/skia/fixtures/selector-calm.json`); its material is
  the Compose Calm material (`ports/skia/fixtures/selector-material.json`, see Changed).
- `GlassStyle.rimMirrorGain` (`uRimMirrorGain`): how much deeper content the mirrored rim reaches
  per unit of band; 1 (default) is the plain mirror.
- `GlassPoseSpec.releaseTrailingInOmega`, `releaseTrailingInZeta`, `releaseTrailingCarry`,
  `releaseTrailingStiffening`, `releaseWallGive`, `releaseStillHoldSeconds` and the
  `CalmCriticalLanding` preset; all off by default.
- `GlassInteraction.dragPressShare`: the share of a press that remains while it drags something; 1 by default.
- `GlassStyle.heldEdgeTone` (`uHeldEdgeTone`): the held lens's outermost pixels as a grey line of
  this level; 0 (default) keeps the additive rim.
- `GlassStyle.heldGlowFloor` (`uHeldGlowFloor`): 0 (default) adds the held glow, 1 lays it as a
  floor that lifts only darker glass.
- `GlassPoseSpec.heldTravelStrain` / `heldTravelStrainOmega` and the `CalmWallLanding` preset; the
  strain is off by default. CanvasKit and .NET selector specs carry the same fields.
- `GlassTabBarAccessory(yieldsToLens = true)`: a formed pose selector takes the neck's place; off by default.
- CanvasKit `calmSelectorMaterial`, `calmBarMaterial`, `painter.drawCalmBar`; .NET
  `CalmSelectorMaterial`, `GlassPainter.DrawCalmBar`; `drawSelector` / `DrawSelector` take transit,
  held travel and a `through` image.

- `GlassInteraction.releaseStiffness` / `releaseDamping`: an optional release spring for the press.
- `GlassInteraction.pressReach`: while pressed, the side nearer the touch grows further; 0 by default.

- `GlassStyle.rimMirror` and `GlassStyle.foldAtCorners` (`uRimMirror`, `uFoldCorners`): a
  reflective rim map for the measured family and a corner-only fold; both 0 by default.
  `GlassTabBarStyle.tapOpticsFold` and `lensInkAll` select them for Calm.
- `GlassStyle.heldGlowReach` (and the `uHeldGlowReach` shader uniform): how far the held lens's
  rim glow reaches inward; 0 keeps the historical bevel-length glow. `heldGlow` now accepts 0–2.
- `GlassTabBarAccessory` and `GlassTabBar(trailingAccessory = …)`: a separate neighbouring
  glass control (iOS 26 Phone's search button) that the bar fuses with through a liquid neck
  while held. `GlassFuse(anchored = true)` keeps a fused rect fixed while the material grows.
- `PhoneScene` and the `:desktop:phoneReplay` task: deterministic 60 fps GPU replay of scripted
  gestures for side-by-side comparison with reference recordings, plus the tracking and script
  tools in `tools/reference-replay/`.

- Native .NET/SkiaSharp painter and source ownership, executable Windows/Linux renderer checks
  and a Windows Forms host. `CalmInteraction`/`GlassFeedback` provide timestamped whole-surface
  press/pull, release/re-grab, viewport containment, cancellation and reduced-motion/resize reset.
  The host keeps labels and hit targets anchored; native selector travel remains separate work.
- `LiquidGlass.WinForms.ControlBackdrop.Capture`: owned standard-control subtree snapshots,
  explicit UI-thread/lifetime rules and known-unsupported-control rejection. The executable
  example verifies real TextBox glyph updates and production-lens refraction without screen capture.
- Opt-in natural Atlas frame-recording and own-input/focus traces, separate from forced redraw
  and screenshot readback. Includes a retained intermittent selection failure and performance limits.
- Visual development/evidence index, native.NET guide and ongoing documentation/visual requirements
  in contributor and agent instructions. Historical/source measurements remain explicitly labelled.

- Opt-in Atlas native motion playback using its own AWT input component, with selection/layout
  assertions, app-owned phase snapshots and actual event/render timestamps. Each phase replays
  independently to keep slow readback out of subsequent input; an optional contact-sheet tool
  preserves source pixels. A readback-free control and an isolated Swing dispatcher prevent
  redraw from re-entering playback; pointer guards reject invalid traces. This is diagnostic
  evidence, not a presented-FPS benchmark.

- Portable `createGlassScene`: one borrowed backdrop, multiple surfaces/lenses, calm feedback and
  draw transforms behind a reusable host API. Logical coordinates and pixel density are explicit;
  input, semantics and labels remain with the host. Includes appearance updates and lifecycle cleanup.
- TypeScript declarations for high-level portable APIs, packaged LICENSE/NOTICE and a standalone
  runtime isolation check. The scene's motion bundle is generated from the existing portable source.

- `GlassScene` and its scoped `Modifier.glass`, connecting one recorded backdrop to multiple overlays.
- `GlassStyle.clearLens(magnification)` and `GlassProfile.Lens`: a continuous magnifier mapping shared
  by material and foreground, with input validation and rendered continuity regressions.
- `GlassInteraction.Calm` and `GlassResponse`: restrained press expansion and progressively resisted drag.
- `GlassTabBarStyle.Calm()`: separate whole-bar feedback and selector travel deformation.
- `GlassTabBarStyle.deformItemsWithBar`: shares one resolved bar drawing transform with ordinary
  and selected ink, using the bar pivot even in a moving selector node. Enabled by `Calm()`;
  older presets retain fixed item drawing. Layout, hit targets and generic card text are unchanged.
- Optional pull displacement in `GlassPressSource.press` for hosts that already own their gesture.
- Atlas Studio's sky, typography and grid scenes, native app-buffer captures and redraw timing output.
- Dependabot configuration, private security reporting instructions and tighter workflow permissions.
- Portable production SkSL bundle and CanvasKit adapter, with named uniform validation, source-drift
  checks and independent JVM/CanvasKit pixel fixtures for every material profile.
- `createGlassPainter` for portable clear lenses: source-image ownership, coordinate mapping and
  temporary shader cleanup behind one small API, with an executable terminal example.
- A browser Skia example with keyboard/pointer controls, backdrop replacement and context recreation;
  the hosted browser suite covers this independently from the lightweight WebGL preview.
- Framework-independent `createCalmInteraction` with timestamped press/pull, cancellation, viewport
  containment and reduced motion; optional press amplitude is independent of the Calm drag response.
- Portable `drawSurface` for rounded in-app material: light/dark appearance, tint amount, corner radii,
  density and accessibility preferences, with one cached tone image and Kotlin parameter-drift fixtures.
- `createGlassPainter(CanvasKit)` now includes production shader defaults. Terminal/browser examples
  need no manual shader loading; CI checks the generated module for drift. Explicit sources remain supported.

### Fixed

- Contact shadow under `renderScale` (17 October): the copy of a panel's contact shadow drawn
  into its own backdrop (the one its rim bends) was scaled by `renderScale` twice, and was sized to
  the padded recording rather than the panel. Below full resolution it shrank toward the panel's
  top-left corner (a 320 x 180 dp card at 0.5: interior darkening 1.4 / 0 / 0 / 0 by quadrant against
  35 at full resolution); at full resolution it reached 2 x pad past the panel's right and bottom
  edges. It now matches at every scale (35 in all four quadrants at 0.5);
  `GlassContactShadowScaleTest` checks it. The shadow cast on the page was already correct.
  Evidence: `review/atlas/contact-shadow-render-scale.png` (desktop JVM test render, white page,
  clear panel, contrast stretched 5x; branch `claude/parity-2` after c8c894d).

- JVM clipping no longer discards the offscreen wide-tone input, removing a hard vertical selector
  seam in native replays while retaining the kernel and through-bar/substrate behavior.
- Resting tint is limited to the actual bar substrate, avoiding rejected opaque protruding discs.
- Calm adjacent taps use a slower coupled travel/shape rate. Remaining repeated width/contour
  error is retained; centre-timing agreement alone is not full motion proof.
- Separate tap pigment/ink appearance follows projected area plus axial travel strain and gradual
  recovery. Long-tap calibration improves while exported pose/formation/protrusion stays unchanged.
- Atlas prefers a discrete adapter and reports the actual renderer. NVIDIA selection is verified;
  this does not claim that ordinary interaction lag or presentation timing is solved.

- Calm held travel now reaches both intermediate and flattened original selector shapes with less
  maximum horizontal extension. Travel contraction is separate from ordinary touch growth and forms
  with the material. Tap spring allocation is independent of held tuning, preserving the verified
  resting-tap curve. Whole-bar drag and historical presets retain their previous settings.

- Calm navigation's material-only directional bias now matches the selected original's top and bottom
  edges separately, capped below its measured displacement. Layout and ordinary labels stay fixed;
  the portable controller exposes the drawing bias as `state.offset`.

- Calm navigation now couples mild stretch with narrowing. Its area-preserving drag model matches
  conservative dimensions from original frames; generic cards retain adaptive strain and their extension cap.

- The free magnifier no longer uses a held-navigation profile that maps labels and backdrop differently.
- Atlas uses canvas-local bounded placement instead of conflicting mobile/desktop screen offsets.
- The calm navigation selector does not interpret perpendicular full-screen travel as free-button squeeze.
- Calm throws retain distance-dependent spine deformation as press formation fades; release no longer
  disables travel-driven shape response.
- Calm selector input is integrated at event timestamps to reduce frame-cadence dependence. Its settled
  size is checked against the original Phone reference independently of authored drag sensitivity.
- Calm resting taps keep a small compression/recovery on arrival. Shape damping and stretch gain are
  tuned separately from the critical centre and press springs; the previous preset had lost this phase.
- Calm resting-tap arrival exchanges spine compression for cap growth, retaining the original paired
  width/height recoil without altering held/throw behavior or the existing midpoint fit. Historical
  presets remain unchanged. Tests cover reverse travel, presentation cadence and reduced motion.
- Further outward movement beyond an end anchor no longer stretches a stationary Calm selector.
- Calm resting-tap height no longer waits through two springs before changing. A separate transit
  pressure response and reduced spine gain fit the original midpoint's height and width together;
  held pressure, optical formation and historical presets retain their existing response.
- Calm navigation restores the original ordinary touch growth with a 5% press while
  retaining restrained drag. Its held brightness no longer adds generic control lift a second time.
- Portable viewport-limited press targets animate gradually instead of clipping growth mid-animation.
- Portable wide-tone sampling preserves opaque edges even for tiny backdrops and fractional
  quarter-resolution dimensions; small sources no longer pick up transparent black during blur.
- Portable shader bindings cache compiled layout metadata, eliminating per-draw Wasm reflection
  while preserving named-input validation and independent pixel tests.
- Desktop Calm composes separate material and ink inputs before aperture rounding. The explicit ink
  filter offset preserves labels under native viewport clipping; the strict endpoint gate now passes.
- Atlas no longer reports an unverified Vulkan backend or invented live astronomical telemetry.
- Atlas's Type scene places its specimen under the initial lens, making the optical effect visible at launch.
- Portable shaders and reference pixels are declared Gradle test inputs, preventing stale cached checks
  after changes outside the Kotlin tree; renderer screenshots are retained with cached test outputs.

### Compatibility and limits

Existing `V3`, `Measured`, `Pullable` and modifier defaults remain available. New scene feedback is calm
by default. The new lens and dynamics are authored approximations. Full iOS equivalence, physical-device
verification of this revision and native support for every UI stack remain unproven.

## Review fixes (in 0.2.0)

- Glass panels follow a resize in place: the size feeding the drag policy, band, pad and path
  field is now tracked state rather than read once from the layout coordinates.
- `LiquidGlassContainer`: members leaving composition are removed (no ghost bodies, no slot
  exhaustion); the pad is whole pixels as for single panels; squircle members keep their corners.
- The cached panel effect is rebuilt when only `highlightChroma` changes; `heldEdgeRecovery`
  scales with render scale; generic-path fields are sampled bilinearly as their half-resolution
  storage assumes, and are rebuilt on a density or layout-direction change.
- Tab bar: the gesture follows a runtime style change; a declined selection returns the selector
  to the host's selection after an authored 400 ms grace; a tap aims at the tapped tab instead of
  briefly pulling back; a tab added and selected in one update rests on it; item content keeps
  its state through emphasis; a bar narrower than its insets no longer throws on drag.
- Accessibility: tab items report `Role.Tab` and their selected state inside a selectable group;
  decorative copies of the row are hidden from screen readers.
- Docs: links to research files that are not in the repository are no longer links, and CI
  checks GitHub links against tracked paths.
- Verified on the JVM suite only; no physical-device capture for the sampling change.

## Astra r14 (2026-10-01, in 0.2.0)

- Preserve touch expansion and the small-control drag response.
- Make large cards and pills much less elastic during dragging through a shared, size-adaptive strain policy.
- Add density, extreme-direction and actual desktop-render regression checks.
- Android APK built; new physical-device verification remains pending.

## Astra r13 review (2026-10-01, in 0.2.0)

- Shared anchored Pullable material: stationary layout/content, bounded deformation, level wide bars.
- Full-screen excursion fixes: unclipped Live material, perpendicular-axis strain and endpoint resistance.
- Reduced held squeeze, native selected ink, compact rest layout and opt-in held side recovery.
- Generic integration guide, physical review evidence and preserved alternative approaches.
- No claim of complete iOS parity and no Maven Central release.

## Earlier 0.2.0 development (September)

- **Reduced held-bubble drag sensitivity:** lower speed response, maximum spine stretch and
  squeeze, with a gentler bounded off-axis response. Small movements remain continuous;
  resting-tap dynamics, press expansion and standalone glass behavior are preserved.

- **A deforming tab-bar selector, opt-in** (`GlassTabBarStyle.selector = GlassSelectorSpec()`, or
  the `GlassTabBarStyle.V3(...)` preset; default null, which keeps the capsule selector this
  component shipped with). The selected Astra path uses a two-dimensional pose body with
  separate centre, spine, pressure and strain state. Its outline, normal, source coordinates
  and coverage share that body. Rest geometry fits compact end slots; taps deform within the
  bar and genuine holds can protrude. Resting taps have separate distance-dependent shape
  recovery. The earlier two-disk controller remains available through `poseMotion = false`.
  Dynamics are authored design constants, not recovered Apple constants; the pose path uses
  `GlassPoseSpec`, while `GlassSelectorSpec` retains the earlier controller's parameters.
- **Astra reference corrections:** calm expansion preserves the capsule's straight spine;
  native ink magnification no longer compounds with a second selected-item scale; dark Phone
  material follows the sampled 32→44 rest/hold plateaus. The raised rim is visible again,
  calibrated with production lighting fields and lossless physical captures. Rim colour and
  the side profile remain partial; see `docs/implementation-status.md` for current verification.
- **Semantic ink is composited as a complete endpoint** on that path. The selector's through-source
  carries the bar's material alone, with no icon or label in it, and the selected ink is composited
  once as `C = (1-m)·C0 + m·(c1 + (1-a1)·B1)` instead of being alpha-overed on separately mixed
  material. The difference is `m(1-m)(a1-a0)(B1-B0)`, which is largest exactly at a moving rim, and
  it is what made a label under the lens read as a ghosted second copy. Ink is rasterised at native
  layer resolution whatever `renderScale` the backdrop uses, and takes one sharp sample by default:
  a maximum over three channels' alphas is not correct coloured transmission
  (`GlassStyle.inkDispersion`).
- **A straight-run fold for the measured profile** (`GlassStyle.edgeFold`, default 0). `f(u) = u +
  a(1-u)^3` joins identity in value and both derivatives and, for `a > 1/3`, folds once at
  `1 - 1/sqrt(3a)`; page structure then appears twice near a straight edge with opposite
  orientation, which is what makes text bend and stretch into the edge instead of showing through
  it. The measured resting-corner table remains the authority at a corner and the two are blended
  through a geometric arc/run transition. `a` is authored, not measured.
- **The held lens's ink map is continuous through the middle of its band** when asked
  (`GlassStyle.heldInkContinuous`, default false). The shipped map steps by about 0.14 W at half
  the band; the captures never established that the step was a real discontinuity, and a smooth
  path across one source plane has a continuous source coordinate.
- **A measured resting-corner source map, opt-in** (`GlassStyle.restMap = GlassRestMap.Measured`;
  default `Legacy`). Fitted in the closeout on 190 display-to-source landmark pairs read off the
  Photos toolbar's 72 px ends in both appearances over both calibration targets, and compiled as a
  12-knot table in the shader's own band (`GlassRestMapTable`): the shipping map is 6-7 px off the
  phone at every depth from 6 to 26 px, the table within 1 px. Changes only the resting corner
  of the measured profile; the fold, the held family and everything else are untouched, and the
  default leaves every existing style pixel-identical. At any radius other than the one measured (72 reference px, carried as the 24 dp design unit) it is a documented scale extrapolation.
- **The content and container shaders take the panel's closed-form normal** for an analytic rounded
  rectangle on the measured profile (`GlassNormalSource.kt`, one shared text and one shared decision).
  They used to difference the field with a 0.3 W epsilon, which put a measured-profile content pass up
  to 1.5 px from the material it sits in at a corner; the three shaders now sample from the same place
  to the readback's 0.016 px, at every tested size, render scale, rest map and formation. The held lens's
  content pass and the legacy profile are unchanged.
- **A cancelled tab-bar gesture selects nothing.** A pointer taken over by another node, a vertical
  scroll, a second finger or the node going away no longer commits the tab under the lens; the lens
  settles back to the selection. A tap is the item's own click, once (the bar's handler no longer selects
  it a second time), and a drag commits from the lift of the pointer that drove it.
- **Two tab-bar release fixes** from the closeout's motion audit. The lift is now fed to the
  velocity tracker, so a finger that stops, holds and lifts releases from a standstill instead of
  with the velocity it had before it stopped (which could fling the lens a tab further). And a
  press or selection change while the lens is still settling retargets the spring from its
  running velocity instead of snapping it to zero.

Material calibration uses original iOS captures of known test images. Measured quantities,
inherited parameters and authored approximations remain distinct; not every constant is an
Apple measurement (`docs/research/measured-model.md`, `docs/implementation-status.md`).

- **The tab-bar lens under a finger** (`GlassProfile.Held`, `GlassStyle.heldLens`, `rawShare`;
  `GlassTabBarStyle.Measured` with `RestingInsetMeasured`, `HeldLensMeasured`, `pillWidth`,
  `heldScale`, `heldLift`, `selectedScale`). Measured from the iOS 27 recording of the Phone and
  App Store tab bars: the bar grows 1.05 and lifts under the finger, the tab under the lens grows
  1.18, the lens's outer band pulls the exterior in behind a seam, its interior shows the bar plus
  a share of the raw content, a 3-4 px edge line with a glow tail. `tintLift` may be negative for
  a non-adaptive lift.
- **The in-app rim re-read with matched outlines**: a bead peaking one pixel in at +60, a tight
  lobe, a glow tail; `inApp` and `toolbar` retuned to it. The calibration render's boxes now sit
  on the phone's pill (they were 19 px low).
- **The lens now takes as long to go as the phone's does.** `GlassTabBarStyle.subside` was a
  spring at stiffness 600, which dropped the lens in 83 ms: it blinked out, and a press read as
  though it had not happened. The reference takes 367 ms from nine tenths of the travel to one
  tenth. Now stiffness 60, measured at 383 ms on an S24+.
- **The held lens's colour fringe cut from 0.07 to 0.006.** Read at the rim rather than fitted on
  the band, the phone's channels separate by at most 16 levels on a line peaking at 65; the
  library was separating them by 98 and drawing a saturated blue stroke. Measured back on an
  S24+: 17 levels against the phone's 16, blue outside the line and red inside.
- **Fused outlines** (`GlassFuse` on `Modifier.liquidGlass`, `GlassTabBarStyle.lensFuse`). Two
  outlines can bow out to meet each other where they cross instead of making a crease, and the
  bevel, rim and refraction follow the merged silhouette. iOS 27's tab bar does this with its
  selection lens at about 16 pt. Off by default on the tab bar: at that width it reads as the bar
  swelling to make room rather than as the lens rising out of it.
- **The gel reference retuned from 1200 dp a second to 300**, which is what the recording shows:
  the lens stands 13 px proud of the bar at rest and 7 to 8 px above 300 pt a second. At the old
  reference the deformation never showed at any speed a finger drags at.
- **The fine term is a high-pass along the element's long axis**, not a share of a second
  blurred copy. The phone keeps detail that runs along a bar and none at all across it, so a
  boundary crossing a bar leaves no step; the old isotropic mix drew one. The target's black band
  under the calibration toolbar stepped 51.8 levels before and 10.1 after, against the phone's
  9.0, with the tone table and the 16 px stripe reading unchanged. Costs about 0.8 ms of record
  time on an S24+ (`docs/research/measured-model.md` section 2a).
- **Render effects cached across frames** (`GlassEffectCache`); no measurable change on the S24+,
  documented as such in `docs/performance.md`.
- **Inward fold lens.** The rim samples inward only, with the measured profile: identity from
  0.9 of the band, a 2x stretch, a stationary seam and a mirrored outer zone. The band is 0.6 of
  the corner radius by default (`refractionBand` unspecified). It forms only while a surface is
  tracked (`lensFormation`); chrome at rest and committed animations show none of it.
- **Two-kernel material.** A fine blur under a point carries detail; a 10 dp blur decides tone;
  `fineShare` mixes them and falls with Tint Amount. `blurRadius` is the fine kernel,
  `wideKernel` the wide one, rendered as a quarter-scale blurred copy of the backdrop.
- **Measured tint.** `tint` is `(1 - a) * backdrop + a * tint + tintLift`: light in-app glass is
  a constant 241/255 grey at an opacity that follows Tint Amount from 0.43 to 0.73, dark is black
  at that opacity plus a lift of 35/255. `liftAdaptivity` gives the system backdrops their
  content-driven lift. `backdropSigma` names a pre-blur by its Gaussian sigma on every platform.
- **Dispersion** is 0.07 of the band, in the mirrored zone only.
- **`GlassMaterial`**: the formulas — opacity, fine share, kernels, the system backdrop sigma, the
  cover-sheet dim curve and lens formation — for hosts that build their own styles.
- **Factories**: `GlassStyle.inApp(dark, tintAmount)`, `systemBackdrop`, `menu`, `shellIcon`,
  `dock`, `coverSheet`. `Regular` is `inApp(dark = false)`.
- **`GlassProfile.Legacy`** keeps the 0.1 optics; `Chrome`, `DarkChrome`, `Clear`, `Thick` and
  the tab bar styles use it, so nothing tuned against 0.1 moves.
- **`LiquidGlassCoverSheet`**: a sheet as iOS 27 draws one — opaque, its own content refracted
  by the fold at its bottom edge while a finger tracks it, the app beneath dimmed by the
  measured curve of pull distance.
- `LiquidGlassContainer` runs the measured optics too (two-kernel tone, lift, inward rest
  profile; no fold, because a container is never tracked).
- The sample gains a Calibration screen: the test image with the measured chrome at the
  reference positions, for screenshot-to-number comparison with the phone.
- Verified against the phone with the same tools: light in-app glass within 6 levels at every
  Tint Amount, edge profiles within 5 levels at every depth; dark over white 6-11 levels low;
  the Android path within 2 levels of the desktop path at every tint and appearance on a
  Pixel 7 and, at every slider position of the sweep, on a Galaxy S24+. The whole study is
  written up in `docs/research/ios27-calibration-study.md`.
- **The lens of chrome at rest.** Corner arcs at rest show the measured rest lens: the ring
  0.33 R inside mapped onto the whole outer third, easing to identity by 0.46 R, with the true
  radial normal so the rim maps onto the ring; straight runs show nothing but a few px of
  inward offset. Measured on the Photos toolbar's ends at every tint and appearance and on the
  Lock Screen's round buttons; the library measures back within 2 px at every depth.
  `lensFormation` still carries both toward the full fold under a tracked drag. Containers
  take the nearest member's arcs.
- **Toolbar role.** `GlassStyle.toolbar(dark, tintAmount)`: the Photos toolbar solved as its
  own role from the calibration stills — the pill's kernels with a white tint at 0.55-0.67
  opacity (light) or black at 0.41-0.63 over the 35/255 lift (dark), more opaque than the pill
  at Clear and less responsive to Tint Amount; within 4 levels of the phone at every slider
  position in both appearances (`GlassMaterial.toolbarOpacity`).
- `GlassTabBarStyle.Measured(dark, tintAmount)`: the bar on the measured toolbar role, with the
  indicator unchanged.
- The calibration render covers every slider position of the iOS sweep (light 0/31/50/62/100,
  dark 0/29/35/50/62/86/100), not just 0/50/100.
- The fine scatter uses five taps under 4 px instead of nineteen, and is skipped entirely when
  the fine share is zero. Six measured panels on a Pixel 7 went from 21 to 19 ms median; the
  material numbers did not move.
- The measured factories cast no contact shadow into the backdrop: the phone's pill over flat
  white reads 247-249 at every tint, and the 0.1 shadow darkened it by 20 levels at Clear.

## 0.1.0 (2026-09-10)

The first public release.

- `Modifier.liquidGlass(shape, style)` turns any composable into the material, and
  `Modifier.liquidGlassSource()` marks what it refracts. `LiquidGlassScene` provides the shared
  state, so neither needs to be handed one.
- Optics in a single SkSL shader shared by AGSL (Android 13+) and Skia (desktop JVM): signed
  distance geometry with a closed-form normal for analytic shapes, exact Snell refraction through
  a superellipse bevel, dispersion, an interior blur that tapers to zero at the rim, two-lobe rim
  lighting, a dark contour, a mirrored edge band, rim softening, tint as an absorbing medium, and
  a dome field for shapes whose refraction band reaches their own centre line.
- Presets: `Regular`, `Clear`, `Thick`, `Chrome`, and `DarkChrome`, the last measured from an
  iOS 26 tab bar in dark appearance.
- `GlassTabBar`, a selection lens measured against native iOS screenshots.
- `LiquidGlassContainer`, which fuses nearby panels into one body of glass.
- `GlassSquircleShape`, arbitrary shapes by measurement, touch response, materialize, morphing
  between styles, light/dark adaptation, the scroll edge effect, and the accessibility modes.
- Below Android 13 the material degrades to a tinted surface with the same rim lighting.
- A rendered test harness: the real shader compiled through Skia on the JVM, with optics asserted
  on pixels.

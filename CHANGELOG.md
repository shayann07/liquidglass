# Changelog

## 0.2.0 (unreleased)

The optics are now measured, not tuned. Every number in the default material comes from
calibration captures of iOS 27 on a real device, taken through a known test image so the
material could be measured rather than eyeballed (`docs/research/measured-model.md`).

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
- **The held lens's colour fringe cut from 0.07 to 0.006.** Read at the rim rather than fitted on
  the band, the phone's channels separate by at most 16 levels on a line peaking at 65; the
  library was separating them by 98 and drawing a saturated blue stroke. Measured back on an
  S24+: 17 levels against the phone's 16, blue outside the line and red inside.
- **Fused outlines** (`GlassFuse` on `Modifier.liquidGlass`, `GlassTabBarStyle.lensFuse`). A tab
  bar's selection lens is proud of the bar, and on iOS 27 the two are one silhouette: where the
  outlines cross the bar's edge bows out to meet the lens instead of making a crease. Measured at
  16 pt of smoothing; the bevel, rim and refraction all follow the fused outline.
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

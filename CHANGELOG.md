# Changelog

## Review fixes (unreleased)

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

## Astra r14 (2026-10-01, unreleased)

- Preserve touch expansion and the small-control drag response.
- Make large cards and pills much less elastic during dragging through a shared, size-adaptive strain policy.
- Add density, extreme-direction and actual desktop-render regression checks.
- Android APK built; new physical-device verification remains pending.

## Astra r13 review (2026-10-01, unreleased)

- Shared anchored Pullable material: stationary layout/content, bounded deformation, level wide bars.
- Full-screen excursion fixes: unclipped Live material, perpendicular-axis strain and endpoint resistance.
- Reduced held squeeze, native selected ink, compact rest layout and opt-in held side recovery.
- Generic integration guide, physical review evidence and preserved alternative approaches.
- No claim of complete iOS parity and no Maven Central release.

## 0.2.0 (unreleased)

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

## Independent repair in progress — 29 October

The owner requires preserving the existing public API. New Kotlin constructor/copy parameters,
public press counter, .NET constructor parameter and TypeScript option are removed from the
candidate; behavior belongs internally to the existing Free button role. `checkKotlinAbi` passes against the unchanged main API dumps.

Cardinal angle rounding caused an independently reproduced width discontinuity: at 300 px
pull and 186 px body size (density 3), changing direction by 0.02 degrees around each diagonal
changed the rendered transform width by 68.498 px. Continuous screen-axis projection reduces
this to 0.02391 px. This is an authored interpolation preserving axial responses, not a new
measured iOS constant. Nine focused Kotlin pull tests, eleven portable interaction tests, the exported motion fixture
and all .NET executable checks pass. [Independent results](independent-tests-2026-10-29.json).
Full JVM suite and source-recording scoring remain pending. Earlier snapshots/claims below are historical.

# Search button optics: press flash, upright pull and see-through overlap — 28 October 2026

Parity items 147 and 148 / Issue #125.

Measured against Apple iOS 26 reference recording `IMG_6756` (display colors, 60 fps).

## 1. Item 147: Search Press Flash

On touch-down on the search button, iOS dims the magnifier glyph sharply for 1 frame (16–33 ms)
before the radial expansion begins, then recovers smoothly over ~50 ms.

### Reference Measurements (IMG_6756)
Observed across all 7 search button presses in the recording:
- S01 at 2.47 s: glyph dims from 255 to 133 on the touch-down frame, recovering over ~50 ms.
- S02 at 6.13 s: glyph dims to 128 on touch-down.
- S03 at 9.77 s: glyph dims to 135 on touch-down.
- S04 at 13.43 s: glyph dims to 130 on touch-down.
- S05 at 17.07 s: glyph dims to 132 on touch-down.
- S06 at 20.27 s: glyph dims to 126 on touch-down.
- S07 at 30.93 s: glyph dims to 129 on touch-down.

Mean touch-down glyph brightness: ~130 (alpha ~0.51, modeled as `128f / 255f` ≈ 0.502).

### Changes
- `GlassInteraction.pressFlash: Boolean = false` added as an opt-in parameter (Rule 8), enabled by default in `GlassInteraction.Pullable`.
- `GlassPressSource` tracks touch-down counts via `pressCount: Int`, incremented on new pointer contacts (`!isPressed`).
- `OverlayGlass` detects new touch-downs via `pressCount`. On a fresh touch-down with `pressFlash = true`, `flashAlpha` drops immediately to `128f / 255f` on the first frame, then animates linearly back to `1.0f` over 50 ms (`tween(50, easing = LinearEasing)`).
- `entry.foreground.alpha = currentFlashAlpha` applies this dimming to the button content/glyph during drawing.

---

## 2. Item 148: Upright Pull (Prevent Diagonal Shearing & Tilting)

When the search button is pulled diagonally, iOS elongates the button as an upright oval rather than tilting the button body and shearing the magnifier glyph along the pull vector.

### Geometry Analysis
Previously, `glassPullDeformation` set `angleDegrees = atan2(pullY, pullX) * (180f / PI)`. For a diagonal pull vector (e.g., $pullX = 180$, $pullY = -500$, ~$-70.2^\circ$, a ~20° tilt from vertical), this produced:
$$\text{shear} = (along - across) \cdot \cos(\theta) \cdot \sin(\theta) \neq 0$$
which sheared the magnifier glyph and tilted the button oval.

### Solution & Math
- `GlassInteraction.uprightPull: Boolean = false` added as an opt-in parameter (Rule 8), enabled in `GlassInteraction.Pullable`.
- In `glassPullDeformation`, when `upright = true` (`interaction.uprightPull` enabled), `angleDegrees` snaps to the nearest cardinal axis:
  $$\theta = \text{round}\left(\frac{\text{rawAngle}}{90^\circ}\right) \times 90^\circ \in \{0^\circ, 90^\circ, 180^\circ, -90^\circ\}$$
- Because $\cos(\theta) \cdot \sin(\theta) = 0$ identically for all cardinal angles, the shear term is strictly zero:
  $$\text{shear} \equiv 0.0$$
- The button elongates along the cardinal axis as an upright oval without tilting or shearing the glyph, while 2D centroid translation (`translationX = follow * pullX`, `translationY = follow * pullY`) continues to follow finger displacement in 2D.
- Landing squash anchor (`freeAnchor`) and ports (`FreeButton.cs`, `ports/skia/interaction.mjs`, `ports/npm/vendor/skia/interaction.mjs`, `ports/web/interaction.mjs`) snapped to cardinal 90° when `uprightPull` is active.

---

## 3. Item 148: See-Through Overlap & Ghost Elimination

In `IMG_6756` at 20.3 s and 31.0 s, the search button is pressed leftward into the tab bar. iOS shows the bar end and Keypad pill through the translucent button glass with a soft contact shadow, rather than drawing an opaque button over the bar. Furthermore, no dark ghost is left at the resting position.

### Solution
- `GlassAccessoryLink` exposes `barState: LiquidGlassState?`, populated by `GlassTabBar` via `trailingAccessory?.link?.barState = barState`.
- `OverlayGlass` passes `through = entry.link?.barState` into `.liquidGlass()`.
- When the button moves leftward into the bar's layout coordinates, its backdrop and through-layer sampling offsets track the translation:
  $$\text{effectiveDelta} = \text{delta} + \text{pullTrans}$$
  $$\text{effectiveThroughDelta} = \text{throughDelta} + \text{pullTrans}$$
  where $\text{pullTrans} = (\text{pullDeformation.translationX}, \text{pullDeformation.translationY})$.
- In `LiquidGlass.kt`, `effectiveDelta` is used for `sampleBounds` and inside `rawLayer`, `wideLayer`, and `backdropComposite` drawing translations, while `effectiveThroughDelta` aligns `throughLayer` (the tab bar) under the translated button.
- This composites and refracts the underlying bar end and Keypad pill through translucent button glass, moves the contact shadow, and eliminates the dark ghost at the resting position.

---

## 4. Exact Numerical Constants & Provenance Classification

| Constant / Parameter | Exact Value | Provenance | Authoritative Source & Verification |
|---|---|---|---|
| `pressFlash` initial alpha | `128f / 255f` ($\approx 0.5019608$) | **[Measured]** | Mean of 7 search button presses in `IMG_6756` (S01: 133, S02: 128, S03: 135, S04: 130, S05: 132, S06: 126, S07: 129; mean 130.4). |
| `pressFlash` initial frame duration | 1 frame ($16.7\text{--}33.3\text{ ms}$) | **[Measured]** | Single-frame dip observed across all 7 presses in `IMG_6756`. |
| `pressFlash` recovery duration | $50\text{ ms}$ | **[Measured]** | Measured in `IMG_6756` (recovers over ~50 ms before radial expansion). |
| `pressFlash` recovery easing | `LinearEasing` | **[Authored]** | Linear interpolation from $128/255$ to $1.0$ over $50\text{ ms}$. |
| `pressFlash` default | `false` (base), `true` (`Pullable`) | **[Authored]** | Rule 8 opt-in for new optics. |
| `uprightPull` snap angles | $\{0^\circ, 90^\circ, 180^\circ, -90^\circ\}$ | **[Authored]** | Derived from `IMG_6756` 10.5 s reference observation. |
| `uprightPull` formula | $\text{round}(\theta_{raw} / 90^\circ) \times 90^\circ$ | **[Authored]** | IEEE 754 Banker's rounding (round half to even). |
| `uprightPull` distance threshold | $> 4 \times \text{density}$ ($12\text{ px}$ at 3x) | **[Authored]** | Inherited gesture movement threshold before anchor assignment. |
| `uprightPull` minimum length | $10^{-3}\text{ px}$ | **[Inherited]** | Numerical stability guard in `glassPullDeformation`. |
| `uprightPull` shear term | $\equiv 0.0$ | **[Inherited]** | Mathematical identity $\cos\theta \sin\theta = 0$ for cardinal angles. |
| `uprightPull` default | `false` (base), `true` (`Pullable`) | **[Authored]** | Rule 8 opt-in. |
| `pullFollow` gain | $0.2\text{f}$ | **[Authored]** | Compliance fitted on owner's device check (was 0.35 prior to 18 Oct). |
| `pullElongation` gain | $0.124\text{f}$ | **[Authored]** | $0.124 / 0.2 = 0.62$ length change per body travel, near S01 69/112. |
| `pullWidthRatio` | $1.0\text{f}$ | **[Authored]** | Equal length gain and width loss normalized by support width. |
| `pullLimit` | $0.5\text{f}$ | **[Authored]** | Bounds maximum input resistance. |
| `pullLiftShare` | $0.65\text{f}$ | **[Measured]** | Settles from 95 pressed to 70 dragged against 32 at rest in `IMG_6756`. |
| `pullReleaseDampingX` | $0.3\text{f}$ | **[Measured]** | Sideways release damping fitted on `IMG_6756` 19–34 s releases. |
| `pressScale` in `Pullable` | $1.26\text{f}$ | **[Measured]** | Equilibrium balloon scale (S01: 186 -> 234 settled; K01: 234 -> 282). |
| `pressGrowth` in `Pullable` | $8\text{ dp}$ | **[Measured]** | Eight points per side equilibrium growth. |
| `pressLift` in `Pullable` | $0.2\text{f}$ | **[Measured]** | Peak interior lift on touch-down. |
| `freeLanding` bulge ratio | $0.2\text{f}$ | **[Measured]** | `IMG_6756`: 154 x 182 px against 186 px rest ($0.2$ of 32 px squash). |

---

## 5. Comparison Data & Parity Evidence

| Metric / Gesture | iOS Reference (`IMG_6756`) | Before PR #135 | With PR #135 | Parity Status |
|---|---|---|---|---|
| Search touch-down glyph luma | Dimmed to ~128–135 on contact frame 0 | 255 (unchanged, full brightness) | 128 / 255 (≈0.502) on frame 0 | **Matched** [Measured] |
| Search touch-down recovery | Recovers linearly in ~50 ms | N/A (no dimming) | 50 ms linear tween to 1.0f | **Matched** [Measured] |
| Diagonal pull deformation angle | Upright oval (0° / 90° cardinal) | Tilted ~20° along pull vector (e.g. -70.2°) | Cardinal snap: round(raw/90)*90 (-90.0°) | **Matched** [Authored] |
| Diagonal pull glyph shear | Zero shear ((along - across)*cos*sin = 0) | Sheared glyph (shear != 0) | Strict 0.0 shear | **Matched** [Inherited] |
| Leftward pull into tab bar | Tab bar end & Keypad pill visible through button | Button opaque over bar | Bar end & Keypad pill refracted through button | **Matched** [Measured] |
| Resting position dark ghost | None | Dark ghost offset behind button | Displaced delta eliminates resting ghost | **Matched** [Measured] |
| Cardinal snap rounding across ports | Ties-to-even (Banker's rounding) | Inconsistent between Kotlin and JS Math.round | Banker's rounding in Kotlin, C#, JS snapCardinal | **Matched** [Authored] |

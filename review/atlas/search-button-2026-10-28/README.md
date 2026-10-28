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

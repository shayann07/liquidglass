# The motion model

> Verbatim output of the `liquid-glass-optical-spec` research run. Preserved unedited;
> see [README](README.md) for what it is and how far to trust it.

---

## Motion model

Apple publishes **no** duration, curve, spring constant, scale factor or shimmer rate anywhere — not in the HIG, not in the API reference, not in WWDC25 219/284/323/356. Every number here is ours; the *mechanisms* are Apple's and are cited as such.

### 1. Merge and split (container)

**Mechanism [apple]:** shapes blend as a function of edge-to-edge proximity, governed by the container's `spacing`; merging is droplet-like ("only if they get closer, they start merging like small droplets of water"); when frames overlap they combine into a single shape. Glass cannot sample glass, so members share one sampling region and are rendered in one pass.

**Implementation:** there is no merge animation. The members' distance fields are smooth-unioned before anything is sampled, and the bevel normal is the gradient of the *merged* field, so the neck between two panels is lit as one continuous rim. Fusion is therefore a consequence of geometry and stays correct at any speed, in any direction, under interruption, and at any frame rate — which an alpha crossfade or a keyframed morph does not.

- `k = 2 × spacing_px`. Derivation: with `smin(a,b,k) = mix(b,a,h) − k·h·(1−h)`, `h = clamp(0.5 + 0.5(b−a)/k, 0, 1)`, two shapes separated by an edge gap `g` have `a = b = g/2` on the midline, so the merged field there is `g/2 − k/4`, which reaches 0 — i.e. the silhouette closes — at `g = k/2`. Setting `k = 2·spacing` puts fusion exactly at `gap = spacing`, matching Apple's "morphs… when the nearest edge is less than or equal to the container's spacing".
- Maximum outward bulge into the gap: `k/4 = spacing/2`.
- Default `spacing = 40 dp` (Apple's own sample value). Keep container `spacing ≤` the interior stack's spacing, or members blend at rest.
- Cap 8 members; the SkSL loop bound must be the same compile-time constant as the array size.

**What animates is position and size, not the merge.** Move a member with whatever spring the layout uses and the neck grows and snaps on its own.

### 2. Morph (shape/size change)

**Mechanism [apple]:** identity-driven, not geometry-driven — `glassEffectID` associates an identity so shapes animate to and from each other; `matchedGeometry` is the default within the container's spacing, `materialize` beyond it. Apple also states the material's characteristics *change with size*: deeper shadows, more pronounced lensing and refraction, softer scattering.

**Implementation:** a morph must animate the **uniforms**, not only the rect. Over the morph, drive together:
- `uSize`, `uRadii` — layout;
- `uScale` = `clamp((min(w,h)_dp − 56)/264, 0, 1)`, recomputed per frame, which in turn moves lens gain `mix(0.85, 1.25)`, tint gain `mix(0.9, 1.25)` and inner-shadow gain `mix(0.9, 1.15)`;
- `uDepth`, `uBand`, `uBlur`, `uBevel` — interpolate the preset toward the destination preset (e.g. a 48 dp `Regular` button becoming a 280 dp menu interpolates 14→12 dp depth, 26→22 dp band, 10→16 dp blur, 4→5 dp bevel);
- the host drop shadow — 0.14→0.30 alpha range, blur `18 dp × (0.6 + 0.6·scale)`.

**Falsifiable:** a correct implementation animates `uDepth`, `uBlur` and shadow with the morph. Holding a constant edge width while the shape grows is the observable failure.

Timing: `spring(dampingRatio = 0.82, stiffness = 380)` on size and radius (≈420 ms settle, no visible overshoot on a large surface); the uniform interpolations follow the same animation clock so they cannot desynchronise from the geometry.

### 3. Materialize / dematerialize

**Mechanism [apple]:** "Instead of fading, Liquid Glass objects materialize in and out by gradually modulating the light bending and lensing, ensuring a graceful transition that preserves the optical integrity of the material." UIKit: assign/clear the effect inside an animation block; "always prefer setting the effect property over the alpha".

**Implementation:** one uniform, `uMaterialize ∈ [0,1]`, scaling displacement, blur, tint strength, mirror amplitude, specular and inner shadow simultaneously. At 0 the shader returns the backdrop bit-exactly, so the element is invisible **without touching alpha** — Apple's mechanism implemented literally.

- appear: 320 ms, cubic-out (`CubicBezierEasing(0.05, 0.7, 0.1, 1.0)`);
- disappear: 240 ms, cubic-in;
- optional accompanying scale 0.94→1.0 on appear only, same clock;
- under Reduce Motion: 160 ms linear, no scale.

Beyond the container's spacing, use this instead of a morph — matching Apple's rule that `materialize` replaces `matchedGeometry` for effects farther apart than `spacing`.

### 4. Press and drag (`interactive`)

**Mechanism [apple]:** opt-in. With it, the element "scales, bounces and shimmers", the material "illuminates from within" starting under the fingertip and spreading to nearby Liquid Glass elements, and the material has "gel-like flexibility… as it moves in tandem with your interaction". For sliders and toggles the *knob* becomes Liquid Glass for the duration of the gesture and reverts after.

**Implementation, three coupled channels:**

| Channel | Down | Up |
|---|---|---|
| scale | → 1.04, `spring(damping = 0.55, stiffness = 900)` (≈220 ms settle, one visible overshoot ≈ +1.3 %) | → 1.00, `spring(damping = 0.72, stiffness = 700)` |
| `uTouchAmt` | 0 → 1, 90 ms linear-out | 1 → 0, 260 ms ease-out |
| `uTouch` | tracks the pointer continuously in panel-local px | frozen at last position while fading |

The glow itself is a Gaussian of σ = min(w, h) around `uTouch`, worth **+0.06** luminance at the peak (device-measured; 0.2 reads as a camera flash), and it drags a 0.17-of-offset magnification of the backdrop with it. Because the falloff is evaluated in the *container's* space in the container shader, a press on one member lights its neighbours too — Apple's "spreads… onto any Liquid Glass elements nearby" — at whatever the Gaussian gives at that distance, with no extra term.

**Gel deformation during the gesture** (Apple's "moves in tandem with your interaction") is a host-side non-uniform scale on the glass layer: along the drag axis `1 + 0.06·min(|v|/1200 dp·s⁻¹, 1)`, across it the reciprocal, lagged by `spring(damping = 0.6, stiffness = 500)`. This is the least-sourced part of the model; Apple publishes no squash/stretch ratio and no lag constant.

**Reduce Motion [apple]:** "decreases the intensity of some effects and disables any elastic properties." So: scale off, bounce off, gel off, magnifier off; keep `uTouchAmt` and the glow at 50 % — feedback survives, elasticity does not.

### 5. Scroll response

**Mechanism [apple]:** as content scrolls under a glass element, its shadows become more prominent, and "the amount of tint and the dynamic range shift to always ensure buttons remain legible". Separately the *scroll edge effect* — a different effect from the material — dissolves content into the background beneath floating chrome, in a soft (gradient) or hard (uniform across the toolbar height) style, and switches to a subtle **dimming** instead of a fade when dark content pushes the glass into its dark style.

**Explicitly not modelled:** highlight direction does **not** track scroll offset. No Apple source ties highlight position or angle to scroll; what Apple documents as changing during scroll is shadow opacity, tint amount, dynamic range and the light/dark flip — all backdrop-driven.

**Implementation:**
- In-shader, per frame, free: `contrast = |luma(sharp) − luma(soft)|` raises `strength` by up to `0.25·uLegibility`. This is the "amount of tint and dynamic range shift", and it responds to text arriving under the element with no host involvement and no extra fetch.
- Host, ≤10 Hz from a 6×5 sparse read of the captured backdrop under the element: shadow alpha 0.14 (solid light ground) → 0.30 (text), and `uFlip` with hysteresis (enter at L > 0.60, leave at L < 0.44) crossfaded over 180 ms. The hysteresis exists because a single luma crossing with no band chatters when a headline scrolls past; Apple publishes no threshold and no band.
- Scroll edge effect: a **separate** sibling shader/gradient over the overlap band, one per view. Soft = quadratic opacity ramp 0 → 0.9 over `overlap + 12 dp` plus progressive blur; hard = uniform across the full toolbar height including any pinned accessory. When the element's own `uFlip` says dark, swap the fade for a 0.18 multiply dim.

### 6. Highlight motion

**Mechanism [apple], carefully bounded:** light sources live in a simulated environment and produce highlights that respond to geometry; during discrete system transitions such as lock/unlock "these lights move in space, causing light to travel around the material, defining its silhouette"; and — hedged — "**in some cases**, the lighting responds to device motion."

**Implementation:** `uLight` is a two-float uniform. Everything else is baked: the displacement field is translation-invariant (it depends only on local coordinates, half-extents and radii), so dragging an element changes only the backdrop sample origin, never the field. Highlight direction is the only thing that needs a live value.
- Default: static at (−0.35, −0.94).
- Optional sweep on a discrete transition (e.g. a sheet presenting): rotate `uLight` through 140° over 700 ms, ease-in-out. This is the "light travels around the silhouette" behaviour, scripted.
- Device motion: **off by default.** Apple's own claim is "in some cases", no Apple API exposes a motion input to `glassEffect`, and the material ships on macOS and tvOS which have no motion sensor at all. If enabled, drive from the fused gravity/attitude vector (not raw gyro, which is rad·s⁻¹ and drifts), low-passed at τ = 250 ms, mapping ±30° of device tilt to ±25° of light bearing, and gate it off when the app is not the foreground window.

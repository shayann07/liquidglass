# V3 model decisions

What was built, what every number in it is, and where the implementation departs from the seeds in
`V3-MODEL.md`. Written 2026-09-14 alongside the implementation, not after a failing test.

Three provenances are used throughout and never mixed:

- **measured** — read off the iOS 27 capture set through the calibration target, with a recorded
  method. Nothing in this pass added a measured number.
- **inherited** — an effective value carried from an earlier fit. It reproduces pixels; it is not
  a recovered physical property.
- **authored** — a design constant chosen to make the candidate concrete. Most of V3 is this, and
  says so.

## 1. Geometry

| Quantity | Value | Provenance |
|---|---|---|
| Body family | convex hull of two disks, `q = (cx, cy, L, r, k)` | authored (V3-MODEL section 4) |
| `rL, rR` | `r - k`, `r + k` | definition |
| Disk centres | `cx ∓ L/2 - k` | definition; horizontal extrema are exactly `cx ± (L/2 + r)` whatever `k` is |
| Half height | `r + |k|` | definition — **not** `r` |
| Optical band `W` | `0.6 · min(rL, rR)` | inherited (the 0.6 R a symmetric bar uses) |
| `rMin`, `ellMin` | 4 dp, 1 dp | authored |
| Containment polygon | inscribed, chord error ≤ 0.2 px, step count from the radius | authored |
| Projection metric | `diag(4, 4, 1, 1, 1)` | authored; a conditioning choice, not a mass |

The distance query is exact and constant-work: one branch decides arc or tangent run, and the
same query returns distance, normal and curvature so nothing recomputes them separately. The CPU
reference (`GlassSelectorGeometry.kt`) and the shader (`GlassBodySource.kt`) are written in two
different forms deliberately — an eligible-arc test and an even-capsule fold — and are scored
against each other and against a dense boundary oracle.

## 2. Dynamics

Every number below is **authored**. They are the seeds of V3-MODEL section 6 unless a deviation
is noted.

| State | Seed | Kept? |
|---|---|---|
| Tap centre | ω 22 /s, ζ 1.0 | yes |
| Shape modes L, r, k | ω 26 /s, ζ 0.80 | yes |
| Hold threshold | 120 ms after an eligible DOWN | yes |
| Formation | ω 36 /s, ζ 1 | yes |
| Size demand | ω 24 /s, ζ 0.90 | yes |
| Max extra length | 0.60 slot widths | yes |
| Max radius drop | 0.12 of base | yes |
| Max skew drive | 0.18 of base radius | yes |
| Release envelope decay | ω 18 /s | yes |
| Grasp attachment decay | ω 40 /s | yes |
| Admission hysteresis | 2 dp in, 4 dp out, 6 dp padding | yes |

Springs are stepped in closed form; the substep cap is 1/240 s. A `runComposeUiTest` check shows
that taking a 0.25 s interval in one step and in 240 substeps lands on the same number, and that
the critical response matches section 6.1's `D(1+ωt)e^{-ωt}`, `vPeak = Dω/e` at `t = 1/ω` and a
90 % time of `3.89/ω`.

### Declared deviations from the seeds

1. **The grasp is bounded in pixels as well as normalized.** Section 7.1 fixes `beta` and lets
   width changes happen around it. Implemented as written, a body that grows to two and a half
   slots puts its centre most of a slot away from the finger holding it — and the tab a drag
   commits is decided by that centre, so it changed what a drag selects. `beta` is additionally
   clamped so `|beta| · A` never exceeds the base body's half width, which is the same reach the
   capsule selector allowed.
2. **End asymmetry is carved out of the radius.** A body's half height is `r + |k|`, so aiming the
   mean radius at the full envelope and the asymmetry on top of it puts the two in competition:
   the wall wins, and the velocity projection then damps every attempt to grow `k`. The radius
   target is `heldRadius(1 - 0.12 tanh u) - |k_target|`, which keeps the declared envelope exactly
   and lets the ends differ inside it.
3. **The deformation drive is the finger's own speed while held, not the body's.** Section 6 says
   the drive is the pre-contact spring acceleration. A grasped body has no spring pulling its
   centre. Using the spring's own acceleration reads zero whenever the grasp is clamped at the end
   of the bar — which is exactly where the reference deforms most — and feeding it the body's
   velocity closes a loop through the radius target that chatters at the substep rate. So the
   drive is `-2ζω · v_finger`, and the body's actual centre velocity (the grasp's less the share
   the size change takes, section 7.1) is kept separately and is what release carries.
4. **The grasp clamp keeps the whole body inside the bar, not just its centre inside the slot
   range.** Without this a wide held body dragged to the end is handed a centre it cannot have,
   and the projection — whose metric would rather change the shape than the centre — answers by
   collapsing the body to its minimum radius instead of stopping it at the end.

## 3. Compositing

The exact endpoint form of V3-MODEL section 8, built as section 19's declared offscreen variant:

```
material pass, uEndpointAlpha = 1   ->  (B1, 1)        opaque endpoint material
ink pass, uEndpointAlpha = 1        ->  (c1, a1)       premultiplied, unmasked
source-over in one offscreen layer  ->  C1 = c1 + (1 - a1) B1
aperture pass                       ->  (m·C1, m)
source-over onto the canvas         ->  C = (1 - m) C0 + m·C1
```

Scored against the passes' own outputs: each step within one output code level, the whole chain
within two of the equation in exact arithmetic, and the naive alternative shown to differ by more
than eight levels on the same inputs, so the cross term is demonstrated rather than asserted.

- **Semantic ink takes one sharp sample.** A maximum over three channels' alphas is not correct
  coloured transmission over an arbitrary background; a white glyph found only by the red ray
  would block green and blue. Bulk ink dispersion is therefore 0 on the V3 lens
  (`GlassStyle.inkDispersion`), and only the page material splits.
- **Ink is rasterised at native layer resolution** on the endpoint path, whatever the backdrop's
  performance scale is. Verified at render scale 1 and 0.25: the stroke edge under the selector
  matches the same stroke beside it to within half a pixel at both.
- **The rim stays inside `B1`.** Section 8.4 allows a rim above ink; this implementation keeps the
  ordering the material already had, with ink over the rim. Declared, not silently chosen.

## 4. Transport

| Program | Form | Provenance |
|---|---|---|
| Resting corners | `GlassRestMapTable`, 12 knots, Hermite last interval, identity beyond 0.8333 | measured; the `d < 6` reference-px extension remains unmeasured and constant |
| Straight runs | `f(u) = u + a(1-u)^3`, `a = 0.8` | **authored** |
| Held ink | `u + w(u)(0.3u - 0.29)`, `w` fading across 0.45..0.55 | inherited constants, authored connection |
| Arc/run blend | geometric, from the shape's own nearest-feature coordinate | authored |

The straight-run candidate predicts a single fold at `uFold = 1 - 1/sqrt(3a) = 0.35450` with
minimum source depth `1 - 2/(3 sqrt(3a)) = 0.56972`. Read back from the implemented shader by
coordinate-coded readback, the turning point and the minimum both land within 1 % of `W`, the map
reverses exactly once, and paired images separate as the square root of a landmark's offset from
the fold, in the ratio 1 : 1.25 : 1.5 for offsets 4 : 6.25 : 9 px.

`a` is a design constant. It is not a measured optical property, and no claim is made that Apple's
edge profile is this polynomial.

## 5. What was deliberately not done

- No recovered index, thickness, gap, viscosity or conserved volume. Section 2 of V3-MODEL shows
  the observed displacement fixes only the derivative of an effective potential, and this pass
  added no constraint that would change that.
- No tangential transport (`tau = 0`). Section 9.2's potential-consistent correction is a
  deterministic alternative, not an aesthetic slider, and nothing in the evidence required it.
- No image-history buffer, no fixed-frame optical lag, no caustic brightness multiplier, no
  `1/|det J|`.
- No change to the measured page kernel, tint response or rim photometry.
- The three-disk extension of section 19 was not built: nothing in the references demanded a
  middle bulge that two disks cannot express.

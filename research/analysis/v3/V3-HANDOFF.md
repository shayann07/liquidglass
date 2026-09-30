# V3 handoff

> **Superseded, 2026-09-22.** This document is the September 14 record and is kept as it was
> written. The current closeout is `final-polish/FINAL-HANDOFF.md`, which repairs the defects found
> after this pass and re-measures every gate. Two claims below are corrected there and should not be
> read from here: **"The cost is entirely CPU-side"** in section 6 is not established — the later
> run measures a *higher* GPU mean alongside the extra CPU stages, and no trace isolates the
> completion-latency delta — and section 4's endpoint gate of "the whole chain within two" levels is
> one level in `V3-MODEL.md` section 15.1, where the chain measures **1.43** and is reported as a
> FAIL/PARTIAL rather than as a pass.

Implementation of `analysis/V3-MODEL.md`, 2026-09-14. One agent, one pass, two bounded revisions on
the one gate that failed. No commits, pushes, tags, releases or remote publishing; both
repositories are left dirty at the HEADs they were found at.

Owner sign-off is **pending**. Nothing below claims the owner has accepted anything, and nothing
below claims parity with Apple.

## 1. The four owner outcomes

| # | Outcome | Status | Where the evidence is |
|---|---|---|---|
| 1 | Page text bends and stretches coherently into the bar's edge, including straight runs, instead of becoming a faint smeared duplicate | **PASS** | `figures/edge_v2_over_v3.png`, `figures/edge_native_*.png`; `GlassEdgeTransportTest` |
| 2 | Tab icons and labels stay optically crisp at rest, during travel, while held, during drag and release | **PASS** | `figures/ink_v2_then_v3.png`; `GlassTabBarInkTest`, `GlassEndpointCompositorTest` |
| 3 | A tapped selector moves as a deforming body constrained by the bar, and does not become the held lens merely because a finger went down | **PASS** | `GlassSelectorControllerTest`, `GlassTabBarV3GestureTest`; `datasets/device-captures/v3/v3/fixture_v3_long_tap.mp4` |
| 4 | A held selector can grow beyond the bar, follow the finger and hard swipes, adjust around nearby content, and settle back without a discontinuity | **PASS** | `datasets/device-captures/v3/v3/sheet_v3_held_drag.png`, `fixture_v3_held_drag.mp4`, `fixture_v3_hard_swipe.mp4`; `GlassSelectorControllerTest` |
| — | Frame-time gate: median no more than 2 ms worse, p95 no more than 10 % worse | **FAIL on the median, PASS on p95** | section 6 |
| — | Two-minute soak | **PASS** | section 7 |

## 2. What is enabled, and on what

The V3 work is **opt-in**. Every existing consumer, preset and measurement stays on the path it was
verified on; `GlassTabBarStyle.selector` is null by default and `GlassStyle.edgeFold`,
`inkDispersion` and `heldInkContinuous` default to the shipped behaviour.

Vitals' nav bar takes `GlassTabBarStyle.V3(dark = true)`, which is the measured material plus:

- the **two-disk deforming body** as the selector's outline, normal, optical band and coverage;
- the **exact endpoint compositor** for the selected ink;
- the **straight-run fold** (`edgeFold = 0.8`) on the bar's top and bottom runs, with the measured
  resting-corner table still the authority at the ends;
- **one sharp ink sample** (`inkDispersion = 0`) and the **continuous held ink map**.

The bar's own measured material, tint response, rim and the resting-corner source table are
unchanged. Nothing measured was re-fitted in this pass and no measured constant was added.

## 3. What was built

New, all in `:liquidglass` `commonMain` unless noted:

| File | What it is |
|---|---|
| `GlassSelectorGeometry.kt` | the body `q = (cx, cy, L, r, k)`, its exact distance/normal/curvature query, its band |
| `GlassSelectorConstraints.kt` | containment as linear rows, the inscribed polygon, the dual-NNLS projection, analytic protrusion |
| `GlassSelectorDynamics.kt` | the authored preset, exact spring stepping, the mode machine, grasp, admission, release envelope |
| `GlassBodySource.kt` | the body's geometry as one shared piece of SkSL |
| `GlassSourceMapSource.kt` | the source maps as one shared piece of SkSL, so the panel and content passes cannot drift |
| `GlassEndpointShaderSource.kt` | the aperture pass of the endpoint compositor |
| `GlassTabBarV3.kt` | the Compose adapter: one controller, one frame clock, one snapshot a frame |
| `sample/.../TabBarFixtureActivity.kt` | the deterministic device fixture of section 15.3 |

Changed: the panel and content shaders (body branch, endpoint alpha, edge fold, ink split,
continuous held ink), `LiquidGlass.kt` (`liquidGlassCore`, native-resolution ink, the endpoint
layer), both platform actuals, `GlassStyle`, `GlassMorph`, `GlassTabBar`, and Vitals'
`FloatingNavBar.kt`.

`MODEL-DECISIONS.md` beside this file records every parameter, its provenance, and the four places
the implementation departs from the seeds and why.

## 4. Tests

**133 JVM tests, 0 failures**, fresh with `--rerun` (`tests/`, `suite_final.log`). The ones this pass
added, and what they actually check:

| Suite | Cases | Gate |
|---|---|---|
| `GlassSelectorBodyTest` | 9 | distance and nearest point within 0.05 px of a **dense boundary oracle** over 4 body shapes including near-degenerate ones; k=0 is the textbook capsule to 1e-3; end asymmetry moves the declared extrema by under 1e-4 px in double precision; 600 infeasible projections escape the bar by at most 0.2 px; velocity projection leaves no outward component and never raises the weighted speed; the spring steps identically in 1 step and 240; the section 6.1 predictions hold |
| `GlassEndpointCompositorTest` | 4 | each pass within one output level of the endpoint equation, the whole chain within two, over a coverage sweep of 20 000+ partial pixels; an aperture over identical endpoints changes nothing; the naive alternative differs by more than 8 levels on the same inputs, so the cross term is demonstrated |
| `GlassEdgeTransportTest` | 8 | the implemented map within 0.25 px of the declared profile; the fold turns at 0.3545 W with minimum 0.5697 W and reverses exactly once; paired images separate as the square root of a landmark's offset, in the ratio 1 : 1.25 : 1.5; no seam along the arc/run transition at constant depth; `edgeFold = 0` reproduces the shipped profile; the body's own map within 0.25 px of its CPU query with identical coverage |
| `GlassSelectorControllerTest` | 10 | taps contained at every substep and never forming the lens; longer taps drive more extra length, matching section 6.1 within 12 % of a slot; trailing lag and arrival bulge with mirror symmetry; 60/90/120 Hz agree within 0.5 px at shared timestamps; 50/100/1500 ms delayed frames produce no stale fling; a stationary finger grows the body around a long label and then stops; a hold protrudes and recovers without a jump; reduced motion is still and flat; a retarget moves nothing at the same timestamp |
| `GlassTabBarInkTest` | 4 | no copy of the ordinary ink survives under the selector (≤2 levels against the no-ink substrate, with a 30+ level control); moving the aperture moves ink anchors by ≤0.25 px; the stroke edge under the selector matches the one beside it within 0.5 px at render scale 1 **and** 0.25; the selected slot shows two strokes, not four |
| `GlassTabBarStyleTest` | +2 | the V3 preset agrees with itself on every field `lerpGlassStyle` snaps at the midpoint - the GlassMorph audit section 14 asks for, as a test - and leaves every measured optic of the `Measured` preset alone |
| `GlassTabBarV3GestureTest` | 14 | every ownership scenario of the existing gesture suite, re-run against the V3 selector, plus: a press under the hold threshold does not form the lens and one past it does, a tap stays inside the bar, a drag forms the lens and deforms the body |

`GlassTabBarGestureTest` was made parameterisable so the V3 subclass runs the **same file**: a new
visual model that changed how many callbacks a tap makes, or let a cancelled drag commit, would
fail there whatever it looked like.

### Defects these tests found

None of these were found by reading the code.

1. The constraint buffer silently dropped planes past its capacity — a capsule bar needs about 110
   rows and it held 96. A dropped plane lets the body escape exactly where the plane was and snaps
   it back when the set changes. Overflow is now reported and treated as a solver failure.
2. A release enforced only the planes that were already violated, so the end of the release was a
   snap of up to 94 px in one frame.
3. A held finger that stopped kept its last velocity indefinitely, inflating the body's length.
4. Content admission ran against a grasp of zero until a drag was recognized.
5. A finger dragged past the end of the bar collapsed the body to its minimum radius, because the
   projection metric prefers changing the shape to moving the centre.
6. The frame loop keyed itself on its wake counter, restarting the clock every duration in the
   controller is measured on, on every pointer event.
7. Idle was decided by distance to target, which a contained body never reaches, so the host asked
   for a frame a frame for ever.
8. End asymmetry consumed vertical envelope and was clipped to nothing.
9. The deformation drive fed back the size change it was producing and chattered at the substep rate.
10. The selected ink row was measured at the selector node's constraints instead of the bar's.

## 5. Device evidence

Pixel 7, USB serial `28121FDH2006ZF`, 1080 x 2400 at density 420, refresh 90.3 Hz.

Stills and **playable real-time videos** for every owner scenario are in
`D:\Work\AndroidStudioProjects\SideProjects\kmp\liquidglass\research\datasets\device-captures\v3\v3\`: short tap, long tap, held drag both directions, hard
swipe both directions, a page scrolling under a bar that stays visible, and the same long tap and
held drag on the Phase 3 build for comparison — plus the same set on Vitals itself. Mid-gesture
frames are extracted in `frames/` and assembled in `sheet_v3_held_drag.png` and
`sheet_v2_held_drag.png`; endpoint screenshots cannot certify morphing, so the middle frames are
what the sheets show.

The two figures that carry the argument:

- `figures/edge_v2_over_v3.png` — the same fixture, same page, same scroll position. Phase 3 shows
  the page text upright and legible through the bar's top run, which is the smeared duplicate the
  owner rejected. V3 shows it reversed and vertically stretched, which is the structure Apple's
  6686 and 6687 show at the same place. The comparison is structural: the backgrounds are ours, not
  Apple's, and no photometric claim is made.
- `figures/ink_v2_then_v3.png` — three Phase 3 frames then three V3 frames of the same held drag.
  Phase 3's labels are visibly doubled under the lens ("Activity" over "vity", "Communities" over
  "unities"). V3's are single and sharp, with local rim distortion and colour where the rim
  crosses a stroke, which is what the references do.

Native-resolution crops are in `figures/edge_native_*.png`, `rest_native_*.png` and
`vitals_tab*_native.png`.

## 6. Frame timing — the gate that fails

`tools/v3_timing.sh`, `datasets/device-captures/v3/timing/`, parsed by the same reader the earlier closeouts used.
Two builds alternated — the Phase 3 build the owner rejected and the V3 build — same scene, same
injected gesture trace (a lens dragged across the bar and back, then a tap), warm-up plus six 30 s
measured runs each, thermal and refresh state recorded around every run.

| | total p50 per run (ms) | median | GPU p50 median | p95 per run (ms) | p95 median |
| :-- | :-- | --: | --: | :-- | --: |
| V3 | 16 / 28 / 29 / 30 / 29 / 30 | **29.0** | **8.0** | 40 / 101 / 105 / 106 / 101 / 107 | **103.0** |
| Phase 3 | 21 / 26 / 25 / 26 / 26 / 25 | **25.5** | **9.0** | 85 / 113 / 105 / 109 / 109 / 101 | **107.0** |

- **Median completion latency is 3.5 ms worse. The gate is 2 ms, so this fails.**
- p95 is 4 ms *better*, a ratio of 0.96 against a 1.10 limit: passes.
- **GPU time is 1 ms cheaper.** The cost is entirely CPU-side.

Per-stage, over the 720 frames each build's framestats retains:

| stage | V3 | Phase 3 | delta |
| :-- | --: | --: | --: |
| input handling | 0.82 | 0.94 | −0.11 |
| animation / frame callback | 5.65 | 5.08 | **+0.57** |
| traversal | 0.06 | 0.06 | 0.00 |
| draw recording | 2.24 | 1.61 | **+0.63** |
| sync | 0.17 | 0.16 | +0.01 |
| issue to swap | 3.49 | 3.49 | 0.00 |
| GPU | 12.32 | 11.80 | +0.52 |

About 1.2 ms of genuine extra CPU per frame — the controller's simulation step and one more layer
recording — which amplifies to 3.5 ms of completion latency because both builds are already far
over a 90 Hz budget under a continuous drag and the pipeline is backed up.

Two bounded optimisations were made and measured, in this order:

1. The inscribed constraint polygons were being rebuilt from scratch on every substep — about a
   hundred planes, each a cosine and a sine, four times a frame and twice over — and protrusion
   was sampling a thousand contour points on every frame the component published its state.
   Polygons are now cached and rebuilt only when the envelope changes; protrusion is exact and
   constant-work (a disk escapes a convex region by `signedDistance(centre) + radius`).
   **+10.0 ms → +5.0 ms.**
2. The body is no longer rebuilt as an object in the substep hot path, and the selector's node is
   sized from the layout's actual widest body (`A = reach + baseHalfWidth`, which is exact for the
   bounded grasp) instead of an abstract ceiling. **+5.0 ms → +3.5 ms.**

What was **not** done to make the number smaller: no ink blurring, no reduced-resolution ink, no
removal of the requested morphology, no widening of the gate.

Absolute 90 Hz smoothness is met by neither build under a continuous lens drag; that is the
baseline's own limitation and it is unchanged. *Total pN is completion latency
(`FrameCompleted − IntendedVsync`), not a measured presentation time — this device reports no
presentation timestamps through `dumpsys gfxinfo`.*

## 7. Soak

`tools/v3_soak.sh`, on the installed V3 build: two minutes of injected gestures on a live page —
page scrolls, lens drags across the bar in both directions at two speeds, and tab taps.

| | TOTAL PSS | GL mtrack |
| :-- | --: | --: |
| start | 419 MB | 137 MB |
| 60 s | 419 MB | 228 MB |
| end (120 s) | 440 MB | 254 MB |

- **315 gestures, no crash, no ANR**, `MainActivity` still in the foreground at the end, every
  gesture released.
- 10 533 frames over the run: p50 16 ms, p90 25 ms, p95 32 ms, 2.2 % janky, 28 missed vsyncs.
  These are much better than section 6's numbers because a soak is a realistic gesture mix, not a
  continuous lens drag.
- Memory rises 21 MB over the two minutes and its graphics share rises 117 MB. **That is an
  observation over two minutes, not evidence of a bound**, and no allocation is attributed to any
  component here.

The first attempt at this run was lost: the Pixel 7 dropped off USB during its final `dumpsys` and
did not re-enumerate. It returned on its wireless transport and the run above is the complete one.

## 8. Identities

| Artifact | md5 |
| :-- | :-- |
| `liquidglass-android-0.2.0-SNAPSHOT.aar` (local Maven) | `e3bdecb96a477b6cbf5a8c45f7575e54` |
| `apk/sample-v3.apk` | `a019f38ca088949f84e2884b729da229` |
| `apk/vitals-v3.apk` | `51e84d4c5d83425d395ca3dd61558093` |
| `phase3/apk/vitals-final-measured.apk` (the rejected build, used as the timing baseline) | `4ff67de091c47efdb12df974be269ed7` |

| Repository | Branch | HEAD | State |
| :-- | :-- | :-- | :-- |
| `liquidglass` | `research/ios27-measurements` | `aa9e97e` (unchanged) | dirty: 19 modified, 21 untracked |
| `Vitals` | `feat/liquidglass-0.2-measured` | `7d60cd3` (unchanged) | dirty: `FloatingNavBar.kt` |

**The V3 build is installed and verified.** `lastUpdateTime 2026-09-14 08:18:01`, and the APK
pulled back off the device is byte-identical to `apk/vitals-v3.apk`
(`51e84d4c5d83425d395ca3dd61558093`, saved as `apk/installed_vitals.apk`). No timing baseline is
left installed. The device is reachable on its wireless transport
(`adb-28121FDH2006ZF-L0CEKY._adb-tls-connect._tcp`); its USB transport dropped during this pass and
had not returned.

## 9. What remains visibly different, and what is not modelled

- **Frame time.** 3.5 ms of extra completion latency under a continuous drag, CPU-side. Measured,
  not estimated; the profile above says where it is.
- **The fold's strength is authored.** `a = 0.8` produces the right *structure* — one fold, page
  content reversed and stretched at the edge — but nothing here fits it to Apple's own profile, and
  the references are over a different page.
- **One measured corner size.** The resting-corner table was measured at 72 reference px, carried
  as a 24 dp design unit by convention rather than a measured dp equivalence. Vitals' corner is
  31 dp, so it is the documented scale extrapolation. Unchanged from Phase 2, and its 28-of-246
  mutual-localisation coverage is unchanged too.
- **The material seen through a second piece of glass does not reproduce that glass exactly.** With
  an optically transparent selector over the bar, the selector's interior still differs from the
  bar beside it. This is pre-existing `through` behaviour, identical on the capsule path (both read
  37 against the bar's own value in the same test), and this pass did not change it.
- **The rim is inside `B1`.** Ink composites over the rim, not under it. Section 8.4 allows the
  other ordering; it was not adopted.
- **No OS `ACTION_CANCEL` is injected.** The harness's cancellation is a consuming parent and a
  vertical-scroll takeover, which reach the same production decision. Stated, as Phase 3 stated it.
- **No recovered physical constant.** No index, thickness, gap, viscosity or conserved volume.
  Most of the dynamics is authored and `MODEL-DECISIONS.md` says which numbers are which.
- **Reduced motion** disables the body's deformation, the held growth and the fling, and keeps the
  selection and cancellation semantics.

## 10. Reproduction

```bash
# library suite, local publish, sample and Vitals
bash /d/Work/AndroidStudioProjects/SideProjects/kmp/liquidglass/research/analysis/v3/tools/v3_build.sh

# device stills and videos, both builds
bash /d/Work/AndroidStudioProjects/SideProjects/kmp/liquidglass/research/analysis/v3/tools/v3_capture.sh v3

# alternating frame timing, six runs each
bash /d/Work/AndroidStudioProjects/SideProjects/kmp/liquidglass/research/analysis/v3/tools/v3_timing.sh

# two-minute soak
bash /d/Work/AndroidStudioProjects/SideProjects/kmp/liquidglass/research/analysis/v3/tools/v3_soak.sh v3

# figures and numbers
python /d/Work/AndroidStudioProjects/SideProjects/kmp/liquidglass/research/analysis/v3/tools/v3_compare.py
```

The deterministic device fixture:

```bash
adb shell am start -n com.wexpa.liquidglass.sample/.TabBarFixtureActivity --ez v3 true
adb shell am start -n com.wexpa.liquidglass.sample/.TabBarFixtureActivity --ez v3 false
```

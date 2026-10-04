# Atlas and material stabilization — 2026-10-03

## Plan and acceptance gates

Scope: LiquidGlass and Atlas Studio only. Work from `dd52d12`; preserve measured
and legacy presets. No Vitals changes, subagents, computer-use plugin, paid bots,
tag-triggered release or claim of full iOS equivalence.

1. Capture the existing native desktop app. Revisit original iOS evidence and
   distinguish measured dimensions from authored pointer response.
2. Reproduce the split rim with a synthetic coordinate target. Give standalone
   magnifiers one continuous source map; retain the tab selector's specialized
   mapping. Test monotonicity, centre gain and edge continuity in rendered pixels.
3. Add an opt-in calm interaction with separate press and drag response. Check
   ordinary travel, extreme directions, reversals, cancellation and recovery.
4. Provide a small shared scene API that owns backdrop recording and defaults to
   the calm response. Keep the explicit state/modifier API compatible.
5. Simplify Atlas into a usable glass demonstration. Use real rendered backdrop
   content, consistent coordinates, resize-safe placement, accessible controls
   and deterministic terminal-driven native captures.
6. Run relevant regressions, the full library suite, desktop and Android builds,
   strict docs and link checks. Publish reproducible visual evidence and limits.
7. Update README, API/research/contribution/security/license provenance records;
   add free dependency/security automation and enforce passing PR checks.

Full 1:1 parity remains an evidence gate, not a promised outcome. A desktop render
cannot prove Android frame pacing or reproduce Apple's private compositor.

## Initial diagnosis

The Atlas loupe uses `GlassProfile.Held`, an intentionally specialized tab lens:
its material does not magnify while its foreground does. Its outer band has a
separate source map. Using it as a free magnifier creates mismatched backdrop/ink
behaviour. The former 1.67x foreground zoom and authored bright rim amplify this.
The historical pull/press preset is also different from the calm response now
requested. Existing model constants are not silently relabelled as iOS timing.


## Goal update: selector travel and other stacks

The owner distinguished selector travel from whole-bar strain and requested a route for other UI
frameworks. `GlassTabBarStyle.Calm()` now makes that separation explicit. The portable web preview
implements the continuous lens with a caller-supplied texture; the porting contract describes the
remaining host requirements. Native adapters for every stack are not implemented or claimed.

## Findings and changes

- **Magnifier:** the previous Atlas used `Held`, whose material/ink and interior/rim mappings differ.
  The new `Lens` map converges to identity with identity first derivative at its elliptical boundary.
  Material and foreground share the function. Existing held-tab optics remain separate.
- **Feedback:** generic `Calm` separates press growth from drag. Press uses critical springs and at most 3%
  scale / 2dp growth per edge. Drag resists progressively; large surfaces retain the 2dp total extension
  limit. These rates and gains are authored. Ordinary labels and hit targets remain anchored.
- **Navigation:** the calm selector ignores perpendicular travel for strain and speed-driven squeeze.
  The whole bar receives the shared material feedback with a reference-backed 1.05 press. The old
  1.05 label scale is disabled in this preset. A slower centre and separately sprung spine produce
  more deformation over longer trips.
- **Integration:** `GlassScene` owns one backdrop and exposes the scene-bound modifier. Advanced callers
  retain the explicit state API. Gesture-owning hosts may supply cumulative pull through `GlassPressSource`.
- **Atlas:** compact compositions, three backdrop modes, real magnification, explicit reduced motion,
  canvas-local bounds and no invented telemetry. Native capture reads only its own Skia buffer.

## Original reference used as a bound

![Original Phone T04 extreme frame](atlas/reference-T04_extreme.png)

Owner-supplied `IMG_6756`, T04, frame n2717 at 49.772s, from the existing reference analysis. Its held
selector travels horizontally; this sequence does not establish a free-button off-axis squeeze law.
The yellow ellipse-fit width annotation is inherited analysis, not a trusted contour-width measurement:
it can overestimate the visible body. Do not fit pointer gain to that annotation.

![Original Phone T01 sequence](atlas/reference-T01_sheet.png)

The earlier original-frame audit records a 186px bar and approximately 225px settled held height
(about 1.21 bar heights, +/-4px edge uncertainty), versus a 284×162px resting selector. This supplies
a conservative **1.23 bar-height** ceiling for the new synthetic hold tests. The new preset aims at
1.20. The initial 1.16 choice failed the original settled-height tolerance: 215.76px instead of 225±4px.
Restoring 1.20 gives 223.20px height and 345.19px width, within the original 225±4px and 350±10px bounds.
This chosen bound is not a universal maximum for all Apple materials or a measured finger trace.
The existing reference fixture preserves the previous T01 height fit independently.

### Whole-bar bound, measured separately

The selector-height audit above does not bound the surrounding bar. A fresh decode of the original
`IMG_6756.MP4` at PTS 20819/600 (34.698333s, rest) and 29863/600 (49.771667s, T04 extreme hold)
gives the following outer-body measurements. These are full-density 1170×440 crops at `(0,2092)`,
not resized contact-sheet tiles.

| Rest | Ordinary hold | Extreme hold |
| --- | --- | --- |
| ![Original resting bar](atlas/reference-bar-rest.png) | ![Original held bar](atlas/reference-bar-held.png) | ![Original extreme whole bar](atlas/reference-bar-extreme.png) |

At unoccluded columns 400, 450, 500 and 550, thresholds 5/10/15 out of 255 give **186px** resting
height and **204–205px** held height. All four columns agree. The resting top/bottom rows are
191/376; the held top is 171–172 and bottom 375, relative to the crop. Thus most of the growth is
above the original bar. This contradicts treating the reference as a perfectly centre-symmetric
expansion. The current anchored material model is intentionally conservative; that asymmetric response
is still a parity difference, not silently dismissed as measurement noise. Neither frame reveals a
finger position, so these values cannot identify input gain or a timing curve.

The intervening ordinary hold at PTS 21019/600 (35.031667s) measures **196px**, rows 186–381,
at the same columns and all three thresholds. Its growth is approximately symmetric about the
resting centre. The extra extreme deformation is therefore 8–9px beyond ordinary hold, not the
full 18–19px beyond rest. This independently supports separating press from drag strain. The generic
Calm press alone gives 191.58px; a fail-first test rejected it against 196±1px. The navigation preset
now reuses the earlier rounded 1.05 scale, giving **195.3px**, on the material alone. Its label scale
stays 1.0. Generic cards retain 3%/2dp press growth. The Calm timing and all drag coefficients stay
unchanged; restoring normal touch amplitude must not increase drag sensitivity.

The portable Calm regression now uses the measured **205px** upper height as a separate bound. It
tests 72 pull directions, 10,000 logical-pixel excursions, and press/release frames with the 834×186px
bar at density three. Generic feedback peaks at **194.2524px**. With the navigation press override,
the maximum is **198.0169px**. A Kotlin test exercises the production deformation functions across
72 directions, six press amounts and five pull distances; independent capsule support gives
**198.01695px**. This passes a no-excess-stretch bound, but remains 6–7px below the original extreme
and does **not** establish matched iOS deformation. Twelve portable numerical tests pass, including
gradual viewport-limited press targets and unchanged generic defaults.

The actual Compose bar rendering also passes its five extreme-direction pulls, repeated outward
motion at an end anchor, release and fixed-layout assertions. Its software-rendered held state after
the pressure correction is shown below. This is an implementation proof over a synthetic backdrop,
not a like-for-like optical comparison with iOS:

![Calm bar with corrected normal touch expansion](atlas/bar-press-held.png)

### Avoiding duplicate held brightness

The Calm bar reused V3's reference-backed `heldLift` and also inherited generic `pressLift=0.04`.
`GlassTabBar` added the first to the style; `liquidGlass` added the second at draw time. The new
material regression combines both paths before rendering against the independent Phone plateau.
It failed at **54/255 versus 44±1**, then passed at **44/255** after setting the bar interaction's
additional `pressLift` to zero. Touch illumination, bar growth and all generic control presets
remain unchanged. This corrects duplicated ownership rather than refitting an optical constant.

As a separate original-frame check, the untouched interior patch `(330,250)` to `(369,284)` in
the full-density crops above contains only RGB `(32,32,32)` at rest and `(43,43,43)` in the ordinary
hold. Those decoded video values support the existing 44±1 held tolerance; they are not calibrated
display photometry. The extreme crop is excluded because its selector overlaps that patch.
The synthetic held-bar image above predates this brightness correction; it documents geometry only.

### Test scheduling failure and correction

The full local pressure run passed all **325 library tests** (298 passed, 27 intentionally skipped),
but failed overall after 19m20s: Atlas's interaction test exceeded six minutes while both CPU-rendering
suites ran together. The unchanged Atlas assertions passed alone in **3m28s**, including all scenes,
five extreme drags, recovery and reduced motion. Gradle now orders desktop tests after library tests
when both are requested. This adds no dependency for a desktop-only invocation and does not relax
timeouts, skip scenes or reduce render resolution. Native timing remains a separate measurement.
Hosted CI for the pressure correction `0e5b432` passed all seven required checks, including 325
library tests (298 passed, 27 skipped), both desktop tests, Android builds, twelve portable math
checks, six Skia tests and four browser checks. Its full build took 24m44s
([run 37189818625](https://github.com/shayann07/liquidglass/actions/runs/37189818625)).
This precedes the brightness and scheduling correction; their full run is recorded separately.

The corrected source also launches and captures its own native Direct3D buffer at 1920×1051:

![Native Atlas after bar corrections](atlas/brightness-native.png)

The idle grid scene confirms that the lens and selected ink still render. It does **not** exercise
held feedback. This run's static forced-redraw submission times were **18.97ms median, 21.92ms p95,
27.19ms maximum**, from 120 samples after ten warmups ([raw result](atlas/brightness-native-timing.json)).
They are slower than the preceding 9.21/12.46/14.90ms capture, so no performance improvement or
presented-frame budget is claimed. These are separate desktop observations, not an alternating
controlled benchmark; Android performance remains unverified.

Reproduction requires ffmpeg with zscale and Python with Pillow. Decode each timestamp using this
filter, preserving one native frame, then run the checked-in measurement tool:

```text
zscale=t=linear:npl=100,format=gbrpf32le,zscale=p=bt709:t=iec61966-2-1:m=bt709:r=full,format=rgb24,crop=1170:440:0:2092
```

```sh
python tools/measure_phone_bar.py review/atlas/reference-bar-rest.png review/atlas/reference-bar-held.png review/atlas/reference-bar-extreme.png
```

[Per-column measurements](atlas/reference-bar-bounds.json) preserve the threshold sensitivity.
This PQ-to-sRGB decode is suitable for these high-contrast geometric edges; it is not a display
photometric calibration. The native video PTS were checked with ffmpeg `-copyts` and `showinfo`.

## Desktop visual evidence

Both full-window images below are native Skia buffer captures on Windows, at 1920×1051, Direct3D.
The before image is from base `dd52d12`; the after image includes the new public APIs and calm bar.

| Before | After |
| --- | --- |
| ![Original Atlas](atlas/before-desktop.png) | ![Refined Atlas](atlas/after-desktop.png) |

![Native line-grid view showing continuous lens mapping](atlas/native-lines.png)

![Native typography view with real text under the lens](atlas/native-type.png)

The typography follow-up was launched and captured again on native Windows Direct3D after the clock
fix. Its text now crosses the default lens position; the earlier placement left it over empty space.

The grid is a useful falsifier: its lines stay connected through the new lens instead of skipping an
inner annulus. A thin outer lighting highlight remains intentional; removing every highlight is not
the defect being fixed.

| Held navigation mapping used as a free lens | Continuous lens mapping |
| --- | --- |
| ![Held map on a grid](atlas/held-grid.png) | ![Continuous map on a grid](atlas/continuous-grid.png) |

These two small images are production shader renders with identical synthetic input, not screenshots
of iOS. The ramp tests additionally check monotonicity, boundary identity and centre gain at three sizes
and four magnifications. The endpoint test checks material/foreground source agreement in rendered pixels.

The [extreme diagonal](atlas/test-extreme-diagonal.png), [type scene](atlas/test-scene-1.png) and
[recovered scene](atlas/test-recovered.png) are Compose test-renderer captures. They are deliberately
labelled separately from native window captures. They verify the app's actual pointer/control paths;
no physical Android performance claim follows from them.

### Whole-bar pull and throw follow-up

| Held | Full-screen upward pull | Full-screen corner pull |
| --- | --- | --- |
| ![Held navigation](atlas/bar-held.png) | ![Upward pull](atlas/bar-top.png) | ![Corner pull](atlas/bar-corner.png) |

These are actual production navigation renders in the Compose desktop test harness, 480×220,
not iOS screenshots or native-window screenshots. The same pointer stays down while travelling
through ±2000px in both axes. The test checks unchanged layout/hit bounds, valid selector geometry
and release ownership. Labels stay anchored and the bar's long axis stays level. The captures
show the selected item changing on horizontal travel, without a free-button vertical squeeze.

An additional defect was found in inherited release dynamics: it zeroed the speed driving the spine.
The calm preset now uses the body's release speed for travel deformation while press formation
continues fading. In the authored 360px-bar trace, short/long held trips add 3.93/12.09px of half-spine;
released trips add 13.38/26.37px after the arrival refinement below. Both return to 78×58px rest. The original-reference 1.23-height
ceiling remains satisfied. [Raw traces](atlas/selector-travel.csv) preserve every sampled frame.
These figures characterize this model; they are not measured Apple motion rates.

![Held and released motion trace](atlas/selector-travel.png)

The gray band shows the original settled-height tolerance, scaled to this fixture; the red dashed line
is the conservative 1.23 ceiling. Motion timing is authored. A separate frame-cadence test initially
found a 6.82px difference at 100ms between 30Hz and 120Hz presentations of the same input stream.
The calm controller now integrates the preceding target up to each input's timestamp and ignores stale
presentation timestamps. Reversal and throw traces pass at 30/60/90/120Hz within 1.5px geometry and
0.015 formation. Waking after a stationary hold also resumes the frame clock from the input clock,
so the next move does not freeze while the display catches up with an idle interval. Historical
controller clock/rebase behavior is preserved outside the calm path.

### Resting-tap arrival: a missed phase

| Original transient arrival (IMG_6695) | Original settled rest (IMG_6701) |
| --- | --- |
| ![Apple transient arrival](atlas/reference-6695.png) | ![Apple settled rest](atlas/reference-6701.png) |

These existing decoded crops belong to the owner's untimed 6690–6701 tap sequence. They support
the ordered shape change; they are not timestamped finger samples. No exact colour or timing fit is
claimed from them, and adjacent stills are not independent holdouts.

The existing historical recovery test was not exercising Calm. Adding it revealed a failure: at
252px rest width, the four-slot Calm trace peaked at 457.64px but never compressed below rest.
Reducing shape damping from 0.72 to 0.45 and target half-spine gain from 0.55 to 0.42 slots restores
that phase without increasing the peak. It now reaches 450.63px, compresses to 238.08px (5.5%) and
settles at 252px. The one-slot peak is 327.88px. These are authored model outcomes; the test's broad
2.5–14% compression bound is a review constraint, not a measured Apple spring constant. Centre,
press and held-travel settings stay unchanged.

![Production selector arrival trace](atlas/selector-arrival.png)

[Raw tap trace](atlas/selector-arrival.csv). All 38 focused controller/reference/lifecycle tests pass
after the correction, including cadence, idle resume, held extremes and throw recovery. The initial
failed Calm arrival check remains recorded in `verification.json`.

### End-anchor travel: movement outside the bar is not selector travel

The original T04 crop above and the owner's distinction between bar squeeze and selector travel
prompted an additional audit at the end anchors. The centre was already bounded, but the shape
still used raw finger speed. Reversing between two positions far beyond the same end therefore
stretched and squeezed a selector that had nowhere left to travel.

| 360×64px controller fixture, saturated left anchor | Before | After |
| --- | ---: | ---: |
| Maximum width change from the settled hold | 72.47px | 0.00px |
| Maximum height change from the settled hold | 7.30px | 0.00px |

The new regression initially failed. Calm now differentiates the bounded grasp target, using the
same anchor function for shape-driving speed and centre tracking. Both samples use the current
grasp offset, preventing a change in the shape itself from masquerading as finger velocity.
Further outward, reverse-outward and diagonal motion beyond a saturated anchor introduces no
new selector travel. Returning inside the tab range resumes deformation; raw selection intent and
whole-bar feedback keep their separate roles. The right-anchor test also records zero change.
This is a causal correctness check, not a fitted Apple pointer-speed measurement.

All 39 focused controller/reference/lifecycle tests pass, including the original height and arrival
gates. The short/long held and released travel measurements above remain unchanged. The actual
Compose navigation test also checks 12 in-flight frames beyond the left anchor, rather than waiting
for a wrong shape to recover before taking a screenshot. It passes in 52.46s; the production fling
test passes in 5.72s. The combined full build for this correction is pending.

![Production bar during continued outward pointer motion](atlas/bar-outward-motion.png)

This is a 480×220px Compose software-renderer capture with quarter-scale backdrop sampling. Its
selector is at the left anchor and the pointer keeps moving between x=-2000 and x=-4000. The bar's
layout bounds and valid selector geometry are asserted throughout. This is not a matched iOS render.

![Current Atlas native Lines scene](atlas/native-end-anchor.png)

The current Atlas application also launched and captured its own 1920×1051 Direct3D buffer.
The line-grid magnifier retains a continuous interior. Static redraw submissions over 120 samples
after ten warmups measured median 10.83ms, p95 14.66ms and maximum 18.31ms;
[raw timings](atlas/native-end-anchor-timing.json). These are not presented frames or input latency,
and the static native capture does not itself verify the edge gesture covered by the component test.

### Portable production rendering

`ports/skia` exports the evaluated production material/content/aperture shaders and provides a
CanvasKit adapter. Its terminal tests compile all three, render all four material profiles and
compare against independent JVM-generated premultiplied RGBA fixtures. Maximum channel difference
was 1/255 in each profile (mean 0.00098–0.00214 levels). This establishes software-backend agreement
for those fixtures. It does not verify every style, platform integration, browser GPU or navigation host.

The [web preview capture](atlas/web-preview-ci.png) is from headless Chromium in GitHub Actions.
The dependency-free GLSL lens and full Skia shader kit are separate integration choices.

The high-level `createGlassPainter` removes manual uniform binding for clear lenses. Its tests move
round and nonsquare lenses over a coordinate ramp, check page registration and centre gain, replace
the source, preserve the host transform and verify borrowed-image lifetime. It owns no gesture or
platform capture policy. `npm run example` produces the following grid using that same public API:

![Two positioned production-shader lenses on a grid](atlas/skia-painter.png)

This is a terminal CanvasKit software render, not a browser screenshot or an Apple reference. The
left lens uses 1.25× centre magnification; the right uses 1.6×. The example has no Compose dependency.

![Production Skia painter in hosted Chromium](atlas/skia-browser-ci.png)

The browser follow-up at `13e62fd` passed in [CI run 37151905671](https://github.com/shayann07/liquidglass/actions/runs/37151905671).
It explicitly creates a Skia WebGL surface and verifies keyboard/pointer movement, reset, source
replacement and exact rendered restoration after simulated context loss. All three browser tests pass,
including the separate lightweight WebGL renderer. This is headless Chromium evidence, not a physical
GPU performance measurement or verification of Safari, Firefox, mobile WebViews or native framework bindings.

## Verification at this stage

[Machine-readable results](atlas/verification.json).

- Focused optics and calm tests passed, including the shared material/foreground mapping and extreme
  perpendicular selector pulls. Short and long navigation trips produce different shape extents and recover.
- Full library run: 313 tests, 285 passed, 27 skipped, one new fixture expectation failed. It inherited the
  legacy capsule's grasp offset; pose navigation selects from finger intent. After correcting that fixture,
  all 14 calm gesture tests passed. The complete corrected build then passed in
  [CI run 37128280319](https://github.com/shayann07/liquidglass/actions/runs/37128280319), 22m23s.
  The later [CI run 37131042764](https://github.com/shayann07/liquidglass/actions/runs/37131042764)
  ran 316 library tests: 288 passed, 27 skipped and the new bar-render fixture exceeded its three-minute
  software-rendering timeout. That failure is retained in the record. The fixture now uses quarter-scale
  backdrop sampling and a six-minute budget, with the same full-size coverage, gestures and assertions.
  The corrected focused run passed all 50 tests, including the actual bar capture in 124.10 seconds,
  14 navigation gesture tests, original-height matching, cadence and idle-resume regressions.
  The complete correction passed in [CI run 37148115654](https://github.com/shayann07/liquidglass/actions/runs/37148115654):
  319 library tests, 292 passed, 27 explicitly skipped, zero failures; build job 12m50s. Web, Skia,
  dependency review, strict docs and both CodeQL languages also passed at `a7a929f`.
  The painter, test-cache declarations and Type-scene placement also passed in
  [CI run 37149121490](https://github.com/shayann07/liquidglass/actions/runs/37149121490) at `6d23039`:
  319 library tests, 292 passed, 27 skipped, zero failures, plus desktop tests and Android builds.
  Build job 16m45s; all seven required checks passed. The combined arrival-shape and Skia browser revision
  `13e62fd` passed [CI run 37151905671](https://github.com/shayann07/liquidglass/actions/runs/37151905671):
  320 library tests, 293 passed, 27 skipped, zero failures; two desktop tests and Android builds passed.
  Build job 12m57s. All seven required checks passed, including four Skia renderer tests and three
  hosted browser tests. The earlier arrival-only run was cancelled when this combined revision superseded it.
- Actual Atlas desktop tests: 2 passed. Earlier 60-second timeout runs are failures, not passes; the final
  fixture has an explicit six-minute budget for software rendering and completed in 165.5 seconds.
- Android sample assembled successfully. No physical-device verification of this revision.
- Four portable math tests and three CanvasKit production-renderer tests passed. The initial browser
  run passed interactive/keyboard checks but failed context restoration. The event-order correction passed
  the complete hosted browser job in run 37131042764, including context restoration and keyboard input.
  Local headless
  browser launch was blocked by automatic approval review (`blocked by policy`, no detailed reason).
- Strict documentation build passed. Repository-link verification and hosted checks are recorded when complete.

## Portable motion follow-up

Non-Compose hosts previously received only settled deformation math. `createCalmInteraction` now
provides timestamped press and bounded cumulative pull, cancellation, resize, viewport containment,
idle-resume and reduced-motion reset. It owns no UI events or timers. Its matrix deforms material
about the original centre; ordinary labels and hit targets stay in their host layout. This is
whole-surface feedback, not a port of the selector travel controller or its illumination.

Ten portable numerical tests pass, including independent 30/60/90/120/144Hz drawing schedules,
maximum finite over-pulls, all four edges and diagonal outline containment. Five CanvasKit tests pass;
the new test applies the controller to the production shader, measures centred rendered coverage,
checks the large-card envelope, and verifies exact pixel recovery after release. All four material
profiles retain maximum channel error 1/255 against independent JVM fixtures. The browser demo now
uses the controller for an anchored card with pointer/Space input and live reduced-motion changes;
all four hosted browser tests pass at `4ba6112` in
[CI run 37154026144](https://github.com/shayann07/liquidglass/actions/runs/37154026144), browser job 43 seconds.
The initial reduced-motion image comparison at `1f8f611` failed because keyboard focus decoration
intersected the canvas screenshot's rounded corners. The corrected check excludes that decoration
only during pixel comparison, still asserts keyboard focus, and preserves the real demo's focus ring.
Exact material recovery remains required; no image tolerance was loosened.
The combined revision also passed all seven required checks: 320 library tests (293 passed,
27 skipped, zero failures), two desktop tests and Android builds. Its full build took 12m45s.

![Production Skia card held toward the opposite canvas edge](atlas/skia-calm-browser.png)

This is hosted Chromium WebGL evidence. The small matrix response affects the lower card while its
label stays anchored; the upper lens remains an independently movable magnifier.

These tests verify the authored portable contract. They do not turn Calm timing into an Apple
measurement or establish native bindings for every stack. The endpoint quantisation limitation at
that revision is addressed for Desktop Calm by the follow-up below; Android/legacy remain unchanged.

## Endpoint precision follow-up

The original endpoint pipeline rounded the composed material/ink into an 8-bit intermediate before
applying aperture coverage. The separate-input path combines premultiplied ink with material in float
and applies coverage before one output write. It is opt-in through Desktop Calm navigation.

| Same independent endpoint equation | Worst output-level error | Strict one-level gate |
| --- | ---: | --- |
| Legacy composed intermediate | 1.4256 | Not met |
| Separate material/ink inputs | 0.9569 | Met |

Both use the original 18 combinations of three pages, two ink patterns and three outlines:
199,788 visible pixels, including 11,124 with fractional coverage. The oracle starts at the
rendered material and ink outputs, evaluates source-over in double precision, and reads coverage
from output alpha. It does not measure optical fidelity to Apple, display colour transforms, or
rounding from the final framebuffer write. The old gate and its 1.5-level quantisation budget remain
reported explicitly; no tolerance was increased to admit the new path.

The CanvasKit independent-input test also passes at 0.9668 levels over rounded, asymmetric and pose
outlines, with 1,376 fractional pixels. It verifies that the lower storage region stays transparent.
All six portable Skia tests pass, and all four production profiles retain at most 1/255 channel error
against the independent JVM reference pixels.

### Native failure found and corrected

The first Compose integration sampled an ink strip by adding a vertical offset inside the shader.
Its numerical and uncropped component tests passed, but the actual Direct3D capture lost the selected
"Lines" label: the strip lay outside the window and its dependency was invisible to Skia's filter bounds.

![Rejected native candidate: selected label missing](atlas/endpoint-native-rejected.png)

The corrected implementation declares the ink translation as an image-filter input. The final
shader samples both material and ink at the same coordinate. This follows the zero-radius
[Skia RuntimeShader contract](https://api.skia.org/classSkImageFilters.html); it also has a regression
that draws the production filter graph through viewport clipping at every edge.

![Corrected native candidate: selected label preserved](atlas/endpoint-native-corrected.png)

These are full native Atlas render-buffer captures at 1920×1051. The corrected capture measured
120 forced redraws after ten warmups: median 9.21ms, p95 12.46ms, maximum 14.90ms
([raw timing](atlas/endpoint-native-timing.json)). This is static redraw/submission cost, not
presented FPS, a performance improvement, or touch latency. No CPU renderer test was running
simultaneously with that capture.

The endpoint recording now has twice its previous area, before backend allocation overhead.
Material and semantic-ink layers retain their existing resolution. No per-frame readback or bitmap
upload is added. Android's Java API currently exposes one dynamic RuntimeShader input, so it retains
the existing composed intermediate and its known precision limit. The portable ABI adds a required
`ink` child and `uInkStrip`; the [host contract](../docs/porting.md#combining-refracted-ink-with-glass)
documents binding, coordinates, ownership and clipping. Free magnifiers do not use this compositor.

The full local suite was intentionally stopped after the failed native capture, before a completion
result; it is not reported as passed. The corrected source passes 25 focused JVM tests, including
all 14 Calm gesture tests, extreme bar pulls, the endpoint gates, clipped filter inputs and the
generated-shader identity check. The Android sample assembles successfully. The complete library
and desktop suites then passed at `fdae281`: 323 library tests (296 passed, 27 skipped, zero
failures), plus both desktop tests. The local run took 22m54s. All seven required GitHub checks
passed in [run 37168022770](https://github.com/shayann07/liquidglass/actions/runs/37168022770);
the full build job took 18m13s, with the library suite reporting 16m38.38s and desktop 3m22.55s.
That run also passed all four hosted browser tests. No current physical-device verification is claimed.

![Corrected production compositor during a hold](atlas/endpoint-bar-held.png)

This last image is a 480×220 Compose software-rendered fixture with quarter-scale backdrop
sampling and native-resolution semantic ink, not a physical-device or native-window capture.

## Native redraw cost

120 forced redraw submissions after ten warmups, 1920×1051, Direct3D:

| Scene | Median | p95 | Maximum |
| --- | --- | --- | --- |
| Sky | 9.36ms | 18.66ms | 29.10ms |
| Lines | 11.99ms | 16.68ms | 17.93ms |
| Type (follow-up) | 10.09ms | 13.72ms | 19.69ms |

Raw [sky timing](atlas/after-desktop-timing.json), [line timing](atlas/native-lines-timing.json) and
[type timing](atlas/native-type-timing.json).
The sky capture overlapped the CPU test run; the line capture followed it. These measurements are
native static-scene redraw/submission cost, **not presented frame times, FPS, interaction latency or
Android performance**. They do not establish a 60Hz guarantee. No before/after speedup is claimed.

## Repository and licensing

Main requires `build`, `docs-site`, `web`, `skia-port`, `dependency-review`, `codeql-java-kotlin` and
`codeql-javascript-typescript`, bound to GitHub Actions.
The one-review, strict branch-update and resolved-conversation requirements are preserved. Force push and
deletion remain prohibited. Dependency security fixes and private vulnerability reporting are enabled;
secret scanning/push protection were already enabled. Dependabot and CodeQL use free public-repository
features and standard hosted runners. Actions are pinned to exact upstream commit references;
automation does not approve or merge dependency updates.
Runner images are explicitly pinned to Ubuntu 24.04 to avoid an implicit OS migration during verification.
Gradle declares the portable shader/fixture files as test inputs so edits invalidate cached verification;
renderer evidence is a declared output for both library and desktop tests.
An intentional shader-only edit made the source-identity test rerun and fail; restoring the exact
bytes restored the successful cached result. The perturbation was removed before committing.
The build workflow now permits Gradle cache writes on same-repository pull requests as well as main;
forks remain read-only. This follows the [action's cache setting](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md#using-the-cache-read-only)
and [GitHub's branch/PR cache isolation](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching#restrictions-for-accessing-a-cache).
It is intended to avoid repeatedly rendering unchanged tests on later updates; no measured CI speedup
is claimed until a successful cache save and subsequent cache hit are observed. Run 37154921387 at
`15cd8bf` passed all seven checks and saved the updated Gradle task cache. Its slower 26m28s full build
still ran 320 library tests (293 passed, 27 skipped) and both desktop tests successfully; this is a
cache-save observation, not evidence of a speedup. CodeQL still forces
an uncached compilation so its analysis sees the source.

Original source remains Apache-2.0 with preserved third-party attributions. The research screenshots
are owner-supplied evidence, not bundled runtime assets or a license for Apple UI assets.

## Remaining requirements

Full 1:1 Apple parity is **not established**. Separate-input desktop/CanvasKit composition passes the
strict endpoint gate; the historical and Android path remains above it.
The new calm timing is authored; whole-bar extreme gestures need further matched original-frame comparisons.
Universal native bindings, complete web host/compositor integration, presented-frame motion performance,
and current physical-device verification remain incomplete. The active goal is not marked complete.

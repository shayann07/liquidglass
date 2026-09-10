# Reading the competition's source, properly

`competitive-landscape.md` is the positioning audit: who exists, what they claim, where we stand.
This document is the other half, and the one that changes code. Eight repositories are cloned and
**kept**, and every claim below carries the file and line it came from, so the next question has
something to consult instead of being asked again.

Read on 2026-09-11 against the versions in the table. Nothing was copied; every file quoted is
read-only analysis. Where a technique is worth adopting it is described so it can be implemented
from the description, and provenance is recorded so credit lands in `NOTICE`.

| Project | Version read | Licence | Stars |
| :--- | :--- | :--- | ---: |
| [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) | 2.0.1 (`65ab177`) | Apache-2.0 | 3736 |
| [chrisbanes/haze](https://github.com/chrisbanes/haze) | main @ 2026-09-10 | Apache-2.0 | 2518 |
| [skydoves/Cloudy](https://github.com/skydoves/Cloudy) | main @ 2026-09-08 | Apache-2.0 | 1266 |
| [iyinchao/liquid-glass-studio](https://github.com/iyinchao/liquid-glass-studio) | main @ 2026-09-08 | MIT | 683 |
| [elyesmansour/compose-floating-tab-bar](https://github.com/elyesmansour/compose-floating-tab-bar) | main @ 2026-06-07 | Apache-2.0 | 156 |
| [QWEA0/Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android) | v2.0.10 | MIT | 116 |
| [styropyr0/PrismalAGSL](https://github.com/styropyr0/PrismalAGSL) | v1.0.3 | MIT | 26 |
| [3bdul7akim/liquify](https://github.com/3bdul7akim/liquify) | main @ 2026-09-01 | Apache-2.0 | 4 |

All eight are permissively licensed, so there is no copyleft hazard in reading them.

---

## Four things this pass settled

**1. The field is three lineages, not eight implementations.** PrismalAGSL and liquify are
Kyant0's shader, not merely similar to it. liquify says so in its header — "Derived from Kyant0's
AndroidLiquidGlass (Apache-2.0)", `liquify/…/effects/Lens.kt:2` — and Prismal does not, but the
code settles it: the same height profile `1.0 - sqrt(1.0 - x * x)`
(`rect_refraction_dispersion.agsl:11`, `Shaders.kt:55`), the same inflated normal radius
`min(radius * 1.5, min(halfSize.x, halfSize.y))`, and the same seven dispersion taps with the
same hand-tuned `/3.5` and `/7.0` weights. So Kyant0's family is **one vote, not three**, and
the audit's "four implementations all made the same choice" overstates the independent evidence.

**2. Our central claim about sampling direction is correct, and now verified three ways.** This
is the claim the whole library rests on, so it was worth checking hard.

- **Kyant0**: exactly one negation exists in the repo, at the host —
  `setFloatUniform("refractionAmount", -refractionAmount)` (`effects/Lens.kt:49`), with the
  public parameter guarded positive. The shader's `gradSdRoundedRect` returns the *outward*
  normal and the displacement is added, so a negative amount samples inward. Two independent
  corroborations: their lens effect **deletes** the padding it would need to sample outward
  (`Lens.kt:25-27` reduces padding by `refractionHeight`, which zeroes it in every catalog
  configuration), and their SDF-texture variant subtracts an outward normal explicitly
  (`catalog/utils/SdfShader.kt:66`). Outward sampling is not merely unused there, it is
  structurally impossible.
- **QWEA0**: `refractDir` is `-1` by default (`LiquidGlassView.kt:526`), bound at
  `GlassLensRenderer.kt:522`, and their own comment glosses `-1` as "inward sampling (default,
  matching iOS: inner compressed mirror)".
- **Cloudy**: `sampleXY = xy - bend * curve * minDim * normal` with an outward normal
  (`LiquidGlassShaderSource.kt:77`, `:121`).

We remain the only implementation sampling outward. That is a genuine differentiator and it is
safe to say so. It is also the single claim most worth falsifying with the overhang capture,
because if it is wrong it is wrong at the foundation.

**3. QWEA0's "pixel-measured iOS replica" does not survive the code.** This matters because it
is the only competitor whose measurement claim rivals ours. The reality: colours eyedropped from
a screenshot into constants in a demo Activity (`ProfessionalDemoActivity.kt:204-220`), layout
coordinates hand-typed in iOS points and scaled by `widthPixels / 430f` (`:1383`), one light-axis
angle read off a screenshot (`LightSourceController.kt:29-31`), and one wallpaper asset that is
the *backdrop*, not a picture of glass. The entire test suite is three files: two IDE templates
and one real suite that tests **blur energy conservation only**. There is no golden image, no
pixel diff, no screenshot test, no regression guard.

Our frame-crop-and-measure workflow is a stronger method than anything in the field. The gap is
that we do not assert it in tests either. Closing that is worth more than any single optical
change, because it is what stops the material regressing.

**4. Blur-before-refraction is universal, and it is why nobody else has our rim.** Kyant0 chains
`vibrancy(); blur(...); lens(...)` in that order in every call site, so the lens bends an already
blurred image. QWEA0 does the same, and says so: their chain builds the blur as the *inner*
effect (`GlassLensRenderer.kt:550-558`). Their rims therefore cannot show a legible compressed
image of the surroundings, because the detail was destroyed before the warp ran. Ours refracts
sharp and scatters afterwards, keyed to rim distance. This is the second real differentiator and
it should be said far more loudly than it currently is.

haze blurs first too, and their workaround is worth understanding because it is the most
sophisticated answer anyone has: they punch a hole in the optical result at the rim
(`opticalColor *= 1.0 - detailAlpha`, `GlassShaders.kt:186`) and refill it from a second pass
that samples the **unblurred** capture (`GlassShaders.kt:410-510`). So they do recover a sharp
rim, at the cost of an extra pass, and it is off by default (`refractionDetailIntensity = 0f`).
Their architecture is the right one if the blur must be large, because a real separable Gaussian
destroys text where our nineteen rotated taps do not. Ours is the right one for a legible rim.
That is a genuine trade rather than a mistake on either side.

---

## What is worth taking, ranked

| # | Technique | From | Cost | Value |
| ---: | :--- | :--- | :--- | :--- |
| 1 | Render the whole glass graph at ~0.7 scale, adaptively | haze | Mechanical | Very high |
| 2 | Shadow drawn **into** the backdrop, so glass refracts its own contact shadow | studio | Host change | Very high |
| 3 | Highlights composited in a perceptual space with a chroma term | studio | ~20 ALU | Very high |
| 4 | GLSL ES translation for below API 33 | Cloudy | Large | Very high |
| 5 | Analytic blended normal, smooth across the medial axis and the corner joins | haze | Medium | High |
| 6 | Snapshot-observed effect re-evaluation | Kyant0 | Small | High |
| 7 | Re-render only a patch around the pointer during a press | haze | Medium | High |
| 8 | Progressive blur as blur + alpha-mask cross-fade | Kyant0 | ~30 lines | High |
| 9 | Two-texture depth-lerp instead of a 19-tap variable blur | studio | Medium | High |
| 10 | Corner-weighted dispersion | haze | 1 multiply | Medium |
| 11 | Quintic smootherstep in place of smoothstep at the fades | haze | 2 lines | Medium |
| 12 | A fold that reverses the rim derivative, replacing the mirrored tap | haze | Medium | Medium |
| 13 | Dither the specular body | Cloudy | 3 lines | Medium |
| 14 | Screen-blend the highlight instead of adding | Cloudy | 1 line | Medium |
| 15 | A debug cap that forces older API tiers on one device | QWEA0 | ~10 lines | Medium |
| 16 | Named trace sections for systrace | haze | Small | Medium |
| 17 | Guards on degenerate uniforms and fp16 traps | Cloudy, studio | Small | Medium |
| 18 | Scroll-driven minimize and a shared-element accessory | floating-tab-bar | Medium | High for the bar |
| 19 | Velocity-driven squash and stretch | Kyant0, studio | Small | Medium |

### Render fewer pixels

The largest performance win available, and it is mechanical rather than clever. haze renders the
entire glass graph at `sqrt(0.5)`, about 0.707, by default — half the pixels — and drops to 0.5
under load with 12.5% hysteresis (`haze-glass/.../GlassInputScalePolicy.kt:52-73`). Their own
figure for the step from 0.75 to 0.5 is a 39.7% cut in median frame time and 30.7% in peak GPU
memory (`docs/adr/0005-*.md:48-60`). They pair it with a render budget that caps dimensions at
4096 and retained pixels at 16.7M, bisecting the scale factor when over budget.

We render at full resolution, always. Implementing this means threading a scale factor into the
padded layer size and pre-multiplying every pixel uniform. Nothing about it is subtle.

### Put the shadow in the backdrop

`liquid-glass-studio` composites the drop shadow into the background texture **before** the blur
passes and before the main pass samples it (`src/shaders/fragment-bg.glsl:104-120`). Three
consequences we do not get:

- The shadow is inside the refracted image, so the rim shows a compressed, bent smear of the
  object's own contact shadow. That is what a real lens resting on a surface does, and it is the
  strongest single "sitting on something" cue there is.
- Shadow and backdrop share one defocus, because the same Gaussian blurs both.
- The falloff `exp(-|merged| · height / shadowExpand)` uses the **absolute** distance, so the
  shadow exists under the shape as well as around it and darkens the refracted interior.

The shadow is offset by moving the SDF centres rather than by translating a blurred alpha
(`fragment-bg.glsl:106-114`), so it stays correct through a merge.

We cannot do any of this: we draw elevation shadow outside the shader, and `edgeShadow` and
`innerShadow` are rim contours, not a cast shadow. This is an architectural change in the host
recording path, not a shader change, and it is the biggest single idea in the eight repositories.

### Composite highlights in a perceptual space

`liquid-glass-studio` converts to CIE LCH, raises `L*` by 20 for the fresnel band and 150 for the
glare, raises `C*` by 30, then converts back (`fragment-main.glsl:522-532`, `:562-573`).

Why it matters: adding white, which is what we do, **desaturates** every highlight. Raising
lightness keeps the hue, and raising chroma alongside makes the highlight *more* saturated than
the ground beneath it, which is what a specular on a tinted transparent medium actually looks
like. Their `L*` clamp is 120 against a legal maximum of 100, deliberately: the core of the lobe
clips to white while the shoulder stays chromatic. That asymmetry is most of the difference
between "glowing glass" and "white overlay", and "white overlay" is a fair description of a
complaint we have.

Full CIE Lab in AGSL costs a `pow(x, 2.4)` per channel each way plus a cube root. **Oklab is the
cheap substitute**: no piecewise transfer, one cube root, about 20 ALU operations round trip, and
closer to hue-linear than CIE Lab anyway. This is the highest value-per-instruction change
available to us.

### GLSL ES translation is the route below Android 13

The roadmap's P4 assumed the only path below API 33 was QWEA0's native one. It is not, and the
alternative is better.

Cloudy translates its AGSL kernels to `#version 300 es` GLSL ES and runs them on a dedicated GLES
context for API 29 to 32 (`internal/MirageGlslEs.kt`). Their summary is that AGSL is "very close
to GLSL ES 3.0 already" and the divergences are mechanical: `half`/`halfN` to `float`/`vecN`,
`floatN` to `vecN`, `uniform shader` to `sampler2D` plus a pixel-to-UV helper, `layout(color)` to
plain `vec4`, and the entry point rewritten to a `void main()` writing an `out vec4`. **The one
load-bearing trap is the Y-flip**: `gl_FragCoord` has a bottom-left origin where every Compose
kernel assumes top-left, so `fragCoord.y` becomes `uResolution.y - gl_FragCoord.y` and the content
sampler's V must derive from that same flipped coordinate. Get it wrong and everything renders
upside down.

Execution is one process-wide GLES environment on one dedicated thread owning one EGL context,
with per-size `ImageReader` targets and a zero-copy readback: `eglSwapBuffers` pushes into the
reader and `Bitmap.wrapHardwareBuffer` wraps the `HardwareBuffer` (`internal/GlEnv.kt:41-58`).
Two things they learned that we would not have to: `acquireLatestImage()` polled without the
listener returns null, so the listener bridge is required; and the render must be awaited under a
timeout, because a wedged GL thread cannot be interrupted from native code.

Compare QWEA0's native route, which is the alternative. Their C++ is real — about 2300 lines with
a Deriche recursive IIR Gaussian, scalar and NEON, whose cost is independent of sigma and which
vectorises beautifully because RGBA8888 is exactly one `float32x4_t` lane group. Their own figure
is 0.3 ms for 128 by 128 at sigma 12 on a Pixel 7 (`cpp/CMakeLists.txt:8-10`). **The blur half is
worth reimplementing from the 1993 Deriche paper.** The refraction half is not: it uses a
displacement map baked from a hardcoded normalised rounded rect that ignores the view's actual
corner radius (`DisplacementMapGenerator.kt:119-123`), radial-from-centre normals rather than the
real gradient, a magic constant the comment admits was raised 100-fold to make the effect visible,
and an aspect correction applied only to X. Every Liquid Glass 2.0 feature silently no-ops there,
and it ships arm64 and armv7 only, so emulators and Chromebooks get an unsatisfied link.

**Why this matters today.** The Huawei on the desk is a JKM-LX1 on Android 9, API 28, Kirin 710,
reporting **OpenGL ES 3.2**. It has the hardware for this and is missing only `RuntimeShader`.
Two caveats: `Bitmap.wrapHardwareBuffer` is API 29, so API 28 needs a `glReadPixels` copy and will
be slower; and the readback is asynchronous, so this is a route to a *correct* material on old
devices, not a 120 Hz one.

Three free lessons from their native work, whatever we choose: keep NDK debug builds at `-O2`,
because at `-O0` their NEON path measured 76 ms against 7.7 ms and made debug builds look like
9 FPS (`CMakeLists.txt:47-49`); set `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` on NDK r27 for
Android 16's 16 KB alignment; and if we ever want outward sampling under a `RenderEffect` in the
View system, their `layerNode` workaround (`GlassLensRenderer.kt:313-321`, `:408-419`) is the
answer to Android not exposing Skia's `childSampleRadius`.

### Let the snapshot system decide when to re-upload uniforms

The cleanest idea in Kyant0's codebase, and the cheapest for us to adopt. Their effects lambda is
read inside `observeReads { updateEffects() }` on an `ObserverModifierNode`
(`DrawBackdropModifier.kt:350-368`), so an animating value read inside that lambda invalidates
exactly the uniform upload and nothing else. Their scope's `update` returns true only when
density, font scale, size or layout direction changed (`BackdropEffectScope.kt:38-57`). They also
set `shouldAutoInvalidate = false` on the highlight and shadow nodes and invalidate manually.

Given the frame-rate work already done here, where a single looping animation was costing
everything, this is directly relevant.

### Progressive blur, cheaply

Kyant0's progressive blur is one uniform blur plus a vertical alpha ramp
(`catalog/destinations/ProgressiveBlurContent.kt:36-62`): because a copy of the backdrop is
painted over the live content, wherever the mask alpha goes to zero the unblurred original shows
through. So it is a cross-fade between one blurred and one sharp image rather than a
spatially-varying kernel. The transition region is a double exposure and reads slightly ghosted
on high-contrast content, which is the honest cost.

Ours has no equivalent at all: `GlassScrollEdge` is a gradient wash to the ground colour with no
blur in it. This is about thirty lines and we already own every piece.

### Two textures and a depth lerp, instead of a 19-tap disc

`getTextureDispersion` (`fragment-main.glsl:135-157`) samples the **sharp** and the
**pre-blurred** textures at the same three dispersed offsets and lerps per channel by depth from
the rim. Six taps, constant cost, and the blur radius can be anything because it was produced by
a separable Gaussian in an earlier pass.

Our standing objection to a pre-blurred backdrop — that the detail the rim exists to bend is
already gone — **does not apply to this design**, because they keep both textures and the rim
reads the sharp one. This is the first construction seen that gets rim-sharp and interior-soft
without a per-pixel variable-radius blur, and it is roughly three times cheaper than our 19-tap
rotated disc while giving a true Gaussian at any radius instead of noise. It costs an extra
offscreen layer. Worth measuring against ours in the rendered harness.

### An analytic normal that is smooth everywhere

The best construction seen for a problem we spend four field evaluations on. haze's
`gradSdRoundedRect` (`GlassShaders.kt:680-713`) never differences the distance field. It sums the
four edge normals weighted by `reversedSmootherstep((maxDistance - edgeDistance) / blendWidth)`
and mixes toward a corner gradient, with `blendWidth = max(refractionHeight, 1.0)` — the blend
scale is the refraction band itself, which is exactly the intuition behind our widened epsilon,
made analytic and branchless.

On the medial axis all four weights are equal, so the sum cancels smoothly to a short vector
rather than flipping sign, and they read that vector's length as a confidence term the same way
we do: `centerFade = smootherstep(clamp(gradientLength / 0.5, 0, 1))` (`GlassShaders.kt:882-888`).
Our `axisFade` is the same idea with a different threshold.

Note the constraint before anyone starts: it assumes circular corners, and we would have to
generalise it for `uCornerPower`. That is real work, but it removes four evaluations per pixel
and it is smooth at the corner-to-flat joins, where Cloudy's diagonal blend and Kyant0's inflated
radius are both approximations.

### Re-render only what the finger touches

During a press, haze re-renders a patch around the pointer sized
`ceil(2 * (radius + samplingPadding)) + 1`, feathered back in with a smoothstep over a quarter of
the radius (`GlassInteractionPatch.kt:47-74`). Our touch magnifier redraws the entire panel on
every frame of a press. On a large surface that is most of the cost of interaction, and this is
the fix.

Related and cheaper: their stage-level invalidation table has the rim ignore backdrop changes
entirely (`GlassStageInvalidation.kt:49-63`), so a scrolling backdrop never re-records the rim.

### Dispersion belongs at the corners

`cornerWeight = |x * y| / (halfWidth * halfHeight)` (`GlassShaders.kt:117-121`) makes fringing
vanish on the straight runs and peak at the corners, which is where a real bevel is most curved
and therefore most dispersive. Ours applies dispersion uniformly along the normal. This is one
multiply.

Kyant0's family independently arrived at the same normalised `x * y` product, which is some
evidence it looks right rather than merely defensible.

### Quintic instead of cubic at the fades

haze uses `smootherstep`, `t³(t(6t - 15) + 10)`, everywhere we use `smoothstep`
(`GlassShaders.kt:616-619`). It is C2 rather than C1 at the joins, which matters precisely where
a fade meets the unfaded region and a C1 join can show as a faint ring. Our `bend` fade is one
such place. Two lines.

### A fold instead of a mirrored tap

Our mirrored edge band costs a full extra backdrop sample per pixel. haze gets the edge reversal
by remapping the height field instead: `foldedRefractionHeightNorm` collapses the normalised
height toward a small residual inside a narrow envelope in the outer quarter of the band
(`GlassShaders.kt:849-865`), reversing the sampling derivative without pushing displacement
through zero. Zero extra samples.

It is not the same effect as ours and it should not silently replace it, but offering a fold as
an alternative to `mirror` would let `Chrome` drop a tap on the preset that most needs the
budget.

### Instrument it

haze carries 21 named trace sections wired into macrobenchmark metrics
(`GlassTraceSection.kt:7-53`). We have none, and the frame-rate work already done here was done
by measuring frame counts from the outside because there was nothing to read from the inside.

### The small ones

- **Dither.** Our shader has no dithering anywhere. Cloudy adds an ordered dither of one 255th to
  the specular body to break eight-bit Mach banding (`LiquidGlassShaderSource.kt:212-215`),
  bounding the coordinate with `fract` before the hash so it cannot blow up on a large panel. A
  large `Thick` sheet with a broad specular is exactly the case that bands.
- **Screen blend.** `pixel.rgb += (1 - pixel.rgb) * clamp(highlight, 0, 1)`
  (`LiquidGlassShaderSource.kt:220`) cannot exceed 1.0 and degrades gracefully over a bright
  backdrop instead of clipping.
- **A debug API cap.** QWEA0 carries `debugApiLevelCap` (`LiquidGlassView.kt:937`), a knob that
  makes the renderer pretend it is on an older API so every fallback tier is reachable on one
  device. We have three tiers and one of them is only reachable on a phone from 2018.
- **Guards.** Zero-width `smoothstep` is implementation-defined and becomes a hard step, so clamp
  the width; `normalize` needs an epsilon against a zero vector; `pow` bands in fp16 and a `sqrt`
  near one loses its significance, so both want `float` rather than `half`. The studio's WebGPU
  port had to add a `safeNormalize` with a 1e-8 floor, which is an admission that its GLSL path
  carries a latent NaN on the medial axis. Sweep ours for all of these.

### The tab bar behaviour, with a working reference

The roadmap lists three things Apple's tab bar does and ours does not.
`compose-floating-tab-bar` implements two of them in one file. The scroll behaviour is a
`NestedScrollConnection` that accumulates delta, **resets the accumulator on a direction change**,
and flips state past a threshold, returning `Offset.Zero` so the scroll still reaches the list
(`FloatingTabBar.kt:225-272`). The direction-change reset is the part that matters: without it a
wandering scroll accumulates noise and the bar flickers. The accessory is a
`SharedTransitionScope` element with inline and expanded forms, so the mini player physically
moves between layouts rather than cross-fading.

This lands directly on the complaint that our bottom bar is not flagship. It also passes our own
test for what earns a component: it is behaviour a modifier cannot express, so it belongs inside
`GlassTabBar`.

### Velocity into geometry

Two independent implementations do the same thing. Kyant0 divides `scaleX` and multiplies
`scaleY` by a velocity term clamped to a fifth (`LiquidBottomTabs.kt:270-272`), in a `layerBlock`
with no shader cost. The studio dilates the *shape extents* along the axis of motion from the
spring's instantaneous velocity (`App.tsx:618-631`). We read velocity nowhere. This is the
"liquid" part of the name and it is cheap.

---

## What we should defend

Worth stating plainly, because several of these are things we are better at than the market
leader and the README currently undersells all of them.

- **Outward sampling with real padding.** Nobody else can show anything from outside the element.
- **Exact Snell deviation with no transcendentals.** Kyant0's family uses `1 - sqrt(1 - x²)`, a
  hemispherical heuristic with a vertical tangent at the rim. The studio does solve Snell but
  spends three transcendentals per pixel on it (`fragment-main.glsl:468-471`) and pairs it with an
  ad-hoc `(x/R)²` surface profile. QWEA0's C++ carries the same solve **plus** the ad-hoc clamp
  our tangent-difference identity was written to avoid. Ours is self-bounding and needs no clamp.
- **Refraction before scatter**, keyed to rim distance. Universal difference, discussed above.
- **Medial-axis handling.** Kyant0 has none: their flat-run tie-break flips direction
  discontinuously across the axis, and for a wide capsule their corner branch reaches
  `normalize(float2(0.0, 0.0))`, dodged only by half-pixel sampling. Their `depthEffect` boolean
  is a crude version of the same idea, which is why every large demo turns it on. The studio has
  none either; its unnormalised gradient fades refraction and highlights together near the axis,
  which is functionally our `axisFade` but implicit, unbounded, and resolution-dependent — their
  gradient hardcodes a 1000-pixel canvas as unit scale, so every constant they ship is tuned for
  one resolution.
- **Squircle corners and arbitrary shapes.** Kyant0 is L2 only and throws for anything that is not
  a corner-based shape. Their per-corner radius selection is also **broken**: `radiusAt` is called
  with `coord` rather than the centred coordinate (`Shaders.kt:70`, `:107`, `:173`, `:195`), and
  since layer-local coordinates are always positive it always returns the bottom-right radius.
  Every catalog usage is a capsule or a uniform rounded rectangle, which is why nobody has noticed.
- **Tint as a medium, per-pixel, plus legibility and inversion.** Kyant0 has no tint in the shader
  at all; it is a `drawRect` on top. QWEA0's absorb-and-scatter model is the one we already
  implement and already credit.
- **Fresnel.** We ship Schlick. The studio wrote both Schlick and the full unpolarised Fresnel
  equations, then commented both out in favour of a `pow(depth, 5)` band. Their commented-out code
  is evidence they tried and disliked it, which is worth knowing before we assume ours is wrong.
- **Refraction physics at all.** haze has no index of refraction anywhere in its codebase and no
  Snell solve; its displacement is height times strength times scale along a negated gradient.
  They compensate with a `refractionFold` parameter that fakes the edge reversal our formulation
  produces for free.
- **Accessibility.** Nobody else has frost, contrast or reduced-motion handling that raises
  opacity. haze's reduce-transparency multiplies blur by 0.65 and changes no opacity at all,
  which is arguably backwards.
- **A rendered test harness.** Nobody else asserts optics on pixels at all.

Two corrections to our own materials fall out of this. Our audit says QWEA0 "multiplies by the
tint and adds a scattering term" as though it were an alternative to a lerp; in fact they multiply,
add scattering, and then **lerp toward that result by the tint alpha**, which is what we already
do. And the studio's per-channel dispersion sign is inverted relative to real dispersion: their
red is displaced further than their blue. Ours is the physical way round.

---

## Two facts about the hardware on the desk

Both checked, not assumed.

**The Huawei is not a mystery.** JKM-LX1, Android 9, API 28, Kirin 710, OpenGL ES 3.2. It is
below the API 33 floor for `RuntimeShader`, so it draws the flat fallback and always has. The
sample was installed and launched on it during this pass: no crash, no shader error in logcat,
the activity resumes normally.

Measured across a panel's left edge on the captured frame, the fallback does exactly what the
docs promise. Reading outward from the body: the interior sits flat at luminance 81, a lit rim
band reads 124 over about three pixels, and beyond it the blue backdrop reads 91. So there is a
rim, it is lit, and it is roughly 43 counts above the body.

The problem is not that it is broken, it is that "an opaque body with a three-pixel rim" is a
fair description of a plain surface, which is what the documentation honestly promises and what
the material actually delivers. Nothing here is a bug to fix. The gap is that a third of the
install base gets a material that cannot possibly read as glass, and the route to changing that
is item 3 above rather than anything in the fallback itself.

**The Pixel 7 cannot test the platform backdrop path, and now we know exactly why.** The
roadmap says it is "not testable on this device, which is on Android 16", which is out of date
but lands on the right answer for a better reason.

haze ships the path already, flag-gated and off by default
(`haze/.../HazeBackdropRenderer.android.kt`). It is `RenderNode.setBackdropRenderEffect(effect)`,
gated on `SDK_INT >= 36` and then `SDK_INT_FULL >= 3_700_002`, that is **Android 17.2**, or one
named 37.2 beta build. Their public API makes the fallback mandatory and the backend is a sticky
state machine with try-and-catch at every step, which is the right shape for an API this new.
One hard-won detail from their source, worth keeping: the render node must record a transparent
`drawColor` to keep the backdrop filter composited, because recording a clear operation instead
suppresses the backdrop entirely.

Checked here: the Pixel 7 reports `sdk_full` **37.0**, and the installed platform is
`android-37.0`, whose `android.graphics.RenderNode` exposes only `setRenderEffect` with no
backdrop method anywhere in `android.graphics`. So the device is one minor version short and the
SDK does not yet carry the API. The work is blocked on a 17.2 build, not on hardware, and haze's
gate is the reference for what to test against when one arrives.

---

## A bug in ours, found by comparison

haze inflates its capture by `blurRadius + refractionScale * strength * (1 + 0.5 * chroma) +
max(edgeSoftness, foregroundOutset)` (`GlassRenderParams.kt:59-80`). Ours is
`refractionDepth * 1.4 + max(blurRadius, backdropBlur)` (`LiquidGlass.kt:258-259`).

**There is no dispersion term in our padding.** Dispersion displaces each channel further than
the base bend, so a style combining a high `dispersion` with a high `refractionDepth` can push a
channel's sample past the recorded region. It will not crash, because `sampleBounds` clamps, but
it will clip the fringe asymmetrically at the rim. No shipped preset is anywhere near the
threshold, which is why nothing has been seen. It should still be fixed, and it wants a rendered
test at an exaggerated dispersion so it stays fixed.

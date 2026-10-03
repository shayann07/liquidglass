# Limitations

Read this before promising anyone parity with iOS. Most of these are not bugs and will not be
fixed, because they follow from what Android is.

## Android 13+ for the real material

`RuntimeShader` is API 33. Below that there is no optical path at all — the material degrades to
a tinted surface with the same rim lighting. Plainer, not broken, but a third or so of the
install base sees the plain version.

Supply a `fallbackSurface` whenever the glass carries labels, or they will be unreadable there.
Check `LiquidGlassSupport.hasShaders` if you want to change the design rather than the colour.

Apple's analogue is narrower and gentler: on tvOS only newer hardware gets the effects and older
devices simply keep their existing appearance.

## There is no system backdrop — every element pays for its own

Apple's material is composited by the OS. Android has no equivalent, so the host must *record*
the backdrop into a layer and hand it to the shader. Consequences:

- An extra full-screen layer per backdrop, and a `RenderEffect` per glass element.
- Glass can only sample **what your app drew**. Never the wallpaper, never another app, never
  the system UI beneath. A "translucent" status bar is not available to you.
- **Glass cannot sample glass.** Apple's rule against stacking is a guideline; here it is a hard
  constraint of the capture model. Stacked panels will not see each other.

## Glass inside a scroll

This one is worth understanding because it is silent when it goes wrong.

A glass element **inside** a scrolling container whose backdrop source is **outside** it cannot
discover its own offset. Compose applies a scroll offset when it *draws* rather than when it
*lays out*, and every layout API — `positionInRoot`, `positionOnScreen`, `localPositionOf` with
or without `includeMotionFrameOfReference` — reports the element's unscrolled position. There is
no public API that sees it.

The symptom is a backdrop that slides out from under the element as you scroll. On a repetitive
backdrop it is nearly invisible; once the element scrolls past the recorded height the sample
region collapses and it goes flat.

**Two supported arrangements:**

1. Glass **outside** the scroll, over a scrolling backdrop. This is what chrome is, and what the
   material is for.
2. Both **inside** the same scroll, sharing a frame.

Outside those, the material detects the empty sample region and degrades to its flat fallback
rather than drawing a hole. It will not crash and it will not look right.

## No vibrancy

Apple's biggest legibility mechanism for content *on* glass is automatic and lives inside their
compositor. A fragment shader drawn behind content cannot recolour that content.

The per-element `inversion` scalar is the workaround and it is coarser: one decision per element
per frame, not per glyph. See [Accessibility](accessibility.md#vibrancy-the-gap-you-cannot-close).

## No Reduce Transparency setting

Android has none, at any API level. `frost` must be driven by an in-app control. Reduce Motion
has a real analogue; Increase Contrast has a partial one that is about text rather than
materials.

## The interior blur is grainy at large radii

Nineteen taps cannot blur legible text, so the tap disc is rotated per pixel and the ghosting
becomes noise. Noise reads as blur — but at large radii it is visibly noise.

A separable Gaussian would be cleaner but has to run *before* the shader, which destroys the
sharp rim. That is the `backdropBlur` knob, and it is exactly the trade `GlassStyle.Chrome`
makes. Apple's composited pipeline has no such either/or.

## Chrome loses the signature edge, deliberately

Over a plain dark ground with sparse high-contrast text there is almost nothing to refract, and
the little there is arrives half-legible, smeared along the rim and sitting under the labels.
`GlassStyle.Chrome` uses a real pre-shader blur and a shallow bend, trading Apple's most
recognisable feature for legibility. This is a real cost, accepted knowingly, and a direct
consequence of the point above.

## Path shapes rebuild on resize

The distance field for an arbitrary path is measured, not computed — see [Shapes](shapes.md).
It is rebuilt whenever the shape or size changes, on the calling thread. Animating a path
shape's size continuously will rebuild every frame and you will feel it.

## No morph identity

Apple's morphing is identity-driven (`glassEffectID`, `glassEffectUnion`). Compose has
`LookaheadScope` and shared-element transitions, which can approximate matched geometry but do
not participate in the material's own union. Forcing shapes into one silhouette at rest has to
be emulated by giving members an artificially large `mergeDistance`, which also inflates their
bevels — an approximation, not an equivalent.

## Eight members per container

SkSL needs an unrollable loop, so the cap is compiled in. Every member is evaluated at every
pixel, which is precisely what lets the fields interact, and is also why the cap exists.

## The rim contains a caustic fold, and there is no LOD control

Where displacement is steep the sampling Jacobian passes through zero — that fold *is* the
compressed inverted rim image that defines the material. AGSL exposes no per-pixel mip bias over
a recorded layer, and an isotropic bias would blur the tangential direction and destroy the
sharp rim anyway. The mitigations are bounded displacement (the exact-Snell form self-bounds)
and a 1.5px rim guard, not filtering.

## Desktop is compiled and launched, not visually verified

The shader provably compiles on Skia and the desktop app starts without error, but nobody has
sat and looked at the material rendering on desktop. Treat desktop as "should work" rather than
"verified".

## Containers are chrome at rest

`LiquidGlassContainer` runs the measured optics too, but a container has no drag to track, so
its lens formation is fixed at 0: fused bodies get the two-kernel tone, the lift and the
inward rest profile, never the fold. Its members also share one style, so a container cannot
mix roles.

## The measured tone is within a few levels, not exact

Against the phone, same test image, same tools: light in-app glass lands within 6 levels of
the reference at every Tint Amount and the edge profile within 5; dark in-app glass over white
reads 6-11 levels low above the default Tint Amount. The phone's tone responds more to a
brightening neighbourhood than to a darkening one, which a symmetric blur in either sRGB or
linear light does not reproduce; the residual is documented in the measured model's section 11
and in [the calibration study](research/ios27-calibration-study.md).

## The rest lens is measured at one size, and selected per style

The corner lens of chrome at rest has two source maps, selected by `GlassStyle.restMap`.
`GlassRestMap.Legacy`, the default, is the map that shipped: the ring 0.55 W inside mapped onto
the outer band. `GlassRestMap.Measured` is the resting-corner table fitted in the closeout on
190 display-to-source landmark pairs read off the Photos toolbar's 72 px ends (24 pt on the phone, the library's 24 dp design unit by convention) in both
appearances over both calibration targets; against the same pairs the shipping map is 6-7 px
off at every depth from 6 to 26 px and the table is within 1 px. It changes only the resting
corner: the fully formed fold, the straight-run term, the held-lens family, coverage, padding,
normals, dispersion and lighting are untouched, and formation blends it toward the same fold.

What it does not establish: any other radius. The table is dimensionless in `u = d / W`, so at
another radius the shader evaluates the same curve scaled by the band — an explicit scale
extrapolation, verified for implementation consistency on the JVM and on a Pixel 7 at the
Vitals nav bar's 31 dp corner, not measured against the phone there. The shallow region under
the rim (`d < 6` reference px) is a constant extension of the first knot, also unmeasured. The
width of the blend between an arc and a straight run cannot be seen over vertical stripes; the
library blends over 0.2 R along the edge. Larger arcs (the dock's 126 px ends) did not measure
through the Home Screen wallpaper's warped reference. All three are on the capture list.

Two things the closeout's render comparison also shows and this map does not fix: the library's
interior stripe rendering under the toolbar has less contrast than the phone's, so the landmark
estimator cannot localise the render at depths past about 26 px (a photometry question, not a
geometry one); and the content and container shaders difference the distance field for their
normal where the panel shader uses the closed form, which puts the three within 1.5 px of each
other at a corner rather than within a quarter pixel.

The toolbar role (`GlassStyle.toolbar`) matches the phone within 4 levels at every slider
position, but over the stripes it was measured on; its opacity and fine share were solved with
the kernels held at the pill's, and a different split (a 230 grey at 0.70 with more fine
detail) fits the same stripes. Over a flat backdrop the two readings differ by up to 15
levels. A capture of a toolbar over a flat region settles it.

## The deforming selector costs several milliseconds a frame under a continuous drag

The opt-in deforming selector (`GlassTabBarStyle.selector`) steps a small constrained body on a
1/240 s clock and composites its ink through one more offscreen layer than the capsule selector
does. Measured on a Pixel 7 against the same scene and the same injected drag, alternated, 30 s
runs: its **median completion latency is 4 to 6 ms higher** than the capsule path's, and its p95
frame cost is higher too. Neither selector meets a 90 Hz budget while a lens is being dragged, and
that part is the baseline's own limitation.

Where the cost is has **not** been established, and an earlier version of this page said otherwise.
The extra time shows up in the animation/frame-callback stage (about +1.2 ms mean), in draw
recording (about +1.0 ms) and in GPU time (about +0.6 ms mean) — so it is not "entirely CPU-side",
and no trace attributes the completion-latency delta to any one of them. The idea that a small
per-frame cost is *amplified* by a pipeline already over budget is a plausible explanation, not a
measurement.

If those milliseconds matter more than the deformation, leave `selector` null.

## The straight-run fold's strength is authored

`GlassStyle.edgeFold` produces the right structure at a straight edge — one fold, page content
appearing twice with opposite orientation, which is what makes text bend into the edge instead of
showing through it — and its turning point and paired-image separation are verified against the
polynomial that defines it. But the strength itself is a design constant. It was not fitted to a
capture, and the reference stills it was compared against are over a different page than ours.

## The tab-bar lens carries its content a frame late while it moves

The lens draws the selected copy of the row through its own optics, pinned to the bar so it
lands on the resting copy; held still — on a tab or between two — the copies coincide. While
the lens is moving the copy inside it lags a frame, so a glyph shows two colours for that
frame. The reference does the same (its content sits 12-20 px toward the lens centre mid-drag
in the iOS 27 recording), so it is left as it is.

## The detail's sharp axis is assumed to be the element's long one

The material's fine term is a high-pass along a bar and nothing across it, which is what the
phone does (measured model, section 2a). Both elements it was measured on are horizontal bars,
so the captures cannot say whether the sharp axis is the screen's x or the element's own long
axis. The shader takes the long axis, which is the same thing for every bar and toolbar and a
guess for a tall element. A vertical glass element over a striped backdrop would settle it.

## The pill's dark contour is not drawn

The phone's pill has a one-pixel dark contour at its outermost pixel. It is not drawn, because
the shader's contour sits 1.5 px inside the edge where the measured bead peaks (measured model,
FINDINGS 21).

## Astra review limits

The shared anchored interaction is documented in [generic interaction](generic-interaction.md).
The nav pose controller and generic material transform remain distinct. Parent-driven
`GlassPressSource` does not expose a generic pull channel. Rim chroma and exact original
pointer timing remain unresolved. The strict endpoint error is 1.4256 code values against a
1.0 limit; the internal 1.5 tolerance is not a strict pass. Optional glass cards stay off by
default because their measured cost is high. See [implementation status](implementation-status.md).


## Continuous lens and calm response preview

`GlassStyle.clearLens` fixes the free magnifier's internal map discontinuity. It is an authored
elliptical magnifier field, not the measured held navigation profile or a full physical lens simulation.
Its optional foreground pass uses the same source mapping. The factory suppresses dispersion; setting
`dispersion` on this profile does not enable the navigation model's spectral bands.

`GlassInteraction.Calm` and `GlassTabBarStyle.Calm()` use authored input/timing choices. Original
reference videos do not expose finger coordinates; screen-edge travel is tested as an interaction
bound, not claimed as a recovered Apple input gain. Existing expressive presets remain available.

The web preview supports the continuous lens only. It accepts host-supplied textures and cannot sample
arbitrary DOM, protected video or other applications. Full native adapters for SwiftUI, Flutter, Qt,
Unity and React Native are not shipped. See [porting](porting.md).

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

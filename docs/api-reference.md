# API reference

Every public symbol in the library. The guides explain *why*; this is *what*.

---

## State

### `rememberLiquidGlassState`

```kotlin
@Composable
fun rememberLiquidGlassState(
    background: Color = Color.Black,
    inversion: Float = 0f,
    frost: Float = 0f,
    contrast: Float = 0f,
): LiquidGlassState
```

Links a backdrop to the panels that refract it. One state serves any number of panels: the
backdrop is recorded once per frame and each panel samples the region beneath itself.

| Parameter | |
| :--- | :--- |
| `background` | The **opaque colour behind the recorded backdrop**. Not decoration — a backdrop layer is transparent wherever your app painted nothing, and the shader needs this to reconstruct what a viewer sees there. Wrong value shows as a halo around every panel. |
| `inversion` | 0 to 1. How far elements that allow it flip light/dark. One decision per element, because labels drawn *on* the glass must flip with it. Leave at 0 for a permanently dark or light app. See [Adaptation](adaptation.md). |
| `frost` | 0 to 1. Reduce Transparency. Android has no system setting; drive it from your own. |
| `contrast` | 0 to 1. Increase Contrast. Takes the material predominantly black or white with a contrasting border. |

### `LiquidGlassState`

Opaque handle. Construct it with `rememberLiquidGlassState`; it exposes only:

```kotlin
val isReady: Boolean       // true once a backdrop has been recorded
val inversion: Float
val frost: Float
val contrast: Float
```

---

## Modifiers

### `Modifier.liquidGlassSource`

```kotlin
fun Modifier.liquidGlassSource(state: LiquidGlassState): Modifier
```

Marks this content as the backdrop that glass panels refract. Put it on the thing the glass
floats over — usually the scrolling body — and **not** on an ancestor that also contains the
glass, or a panel will sample itself.

### `Modifier.liquidGlass`

```kotlin
fun Modifier.liquidGlass(
    state: LiquidGlassState,
    shape: Shape = RectangleShape,
    style: GlassStyle = GlassStyle.Regular,
    light: GlassLight = GlassLight.Default,
    interaction: GlassInteraction? = null,
    materialize: Float = 1f,
    pressSource: GlassPressSource? = null,
    refractContent: Boolean = false,
    through: LiquidGlassState? = null,
    lensFormation: Float = 0f,
): Modifier
```

Draws this element as a piece of glass over `state`'s backdrop.

| Parameter | |
| :--- | :--- |
| `shape` | Rounded rects, capsules and circles are evaluated analytically; `GlassSquircleShape` carries its own corner exponent; anything else is measured. See [Shapes](shapes.md). |
| `interaction` | Non-null opts into touch response. See `GlassInteraction`. |
| `materialize` | 0 to 1. Drive this instead of `alpha` — at 0 the shader returns the backdrop untouched, so the element leaves by ceasing to bend light. |
| `pressSource` | Drive the press from outside, when another node owns the gesture. See `GlassPressSource`. |
| `refractContent` | Draw the element's content **inside** the glass — bent and colour-split through the same bevel the backdrop goes through, clipped to the shape — instead of on top. For content that is the material's subject: what a magnifier is over, the symbol a selection lens is crossing. See [Interaction](interaction.md#content-inside-the-glass). |
| `restMap` | `GlassRestMap.Legacy` (default) or `Measured`: which source map the resting corner lens of the measured profile uses. `Measured` is the resting-corner table fitted on the Photos toolbar's 72 px ends in the closeout; it changes only the resting-corner term and is a scale extrapolation at any other radius. Ignored by the legacy profile and by the held family. See [limitations](limitations.md#the-rest-lens-is-measured-at-one-size-and-selected-per-style). |
| `lensFormation` | 0 to 1. How far the measured edge fold has formed. At 0 the corner arcs show the measured rest lens (the ring 0.33 R inside, mapped onto the whole outer third) and straight runs show nothing but a few px of inward offset; on iOS 27 the full fold appears only while a finger tracks a surface past about 117 dp of pull and never on chrome at rest or a committed animation, so the default is 0; a host tracking a drag drives it from the pull with `GlassMaterial.lensFormation` and animates it back on release. Ignored by the legacy profile. |
| `through` | Glass this element looks **through**. A lens on a tab bar sees the bar, not past it: record the bar with `liquidGlassSource` into a second state and pass it here, and it is composited over the backdrop before this element's shader runs. The element must not be inside that source's subtree. See [Tab bar](tab-bar.md). |

Where the platform cannot run the shader this degrades to `style.fallbackSurface` with the same
rim lighting.

---

### `GlassFuse`

```kotlin
class GlassFuse(bounds: DpRect, cornerRadius: Dp, smoothing: Dp = 16.dp)
```

Passed to `Modifier.liquidGlass(fuse = ...)`. Another rounded rect, in this element's own
coordinates, that the element's outline **fuses** with. Not a union: where the two outlines cross
the silhouette bows out by about a quarter of `smoothing` to meet the other shape instead of
making a crease, and the bevel, rim and refraction all follow the fused outline from there on.

This is what iOS 27's tab bar does with its selection lens, which stands proud of the bar; on the
reference the bar's edge sits 12 px above both outlines where they cross and rejoins the flat run
about 40 px away, which is 16 pt of smoothing (measured model, section 2d). `GlassTabBarStyle` exposes it as
`lensFuse`, off by default: at the measured width it reads as the bar swelling to make room for
the lens rather than as the lens rising out of the bar.

Costs the closed-form normal, which cannot describe a fused outline, so the shader differences
the distance field instead. Null leaves the outline alone.

## Components

### `GlassTabBar`

```kotlin
@Composable
fun GlassTabBar(
    state: LiquidGlassState,
    itemCount: Int,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: GlassTabBarStyle = GlassTabBarStyle.Dark,
    motionEnabled: Boolean = true,
    materialize: Float = 1f,
    item: @Composable (index: Int, selected: Boolean) -> Unit,
)
```

A tab bar whose selection indicator is a piece of glass you can pick up: a flat inset at rest, a
lens while held that stands proud of the bar and looks *through* it, dragged 1:1 and committed on
release. Every default is measured off an iOS 26 tab bar; see [Tab bar](tab-bar.md).

| Parameter | |
| :--- | :--- |
| `item` | Called twice per tab on the shader path — `selected = false` for the copy on the bar, `true` for the copy inside the lens — and once, with the real selection, where the shader is unavailable. Change only colour between the two. |
| `motionEnabled` | False disables the lens, the gel and the fling; taps and drags still select. |
| `materialize` | Passed to the bar's glass. |

Put a shadow on `modifier` with `clip = false`; the held lens is taller than the bar.

### `GlassTabBarStyle`

`GlassTabBarStyle.Measured(dark, tintAmount)` is `Dark` with its bar on the measured material and, since
2026-09-12, the indicator and lens of the iOS 27 recording: `pill = RestingInsetMeasured`, `lens =
HeldLensMeasured`, `pillInset` 4dp, `pillWidth` 84dp, `lensOverflow` 5dp, `lensExtraWidth` 21dp, `heldScale`
1.05, `heldLift` 16/255, `selectedScale` 1.18 (see [the tab bar](tab-bar.md)). Its base
(`GlassStyle.inApp`); see [Tab bar](tab-bar.md#the-measured-bar). `Dark` and `Ios27` keep the
0.1 optics.

```kotlin
@Immutable
data class GlassTabBarStyle(
    val bar: GlassStyle = GlassStyle.DarkChrome,
    val pill: GlassStyle = RestingInset,
    val lens: GlassStyle = HeldLens,
    val light: GlassLight = GlassLight(0f, -1f),
    val shape: Shape = RoundedCornerShape(percent = 50),
    val height: Dp = 64.dp,
    val contentPadding: Dp = 6.dp,
    val pillInset: Dp = 3.dp,
    val lensOverflow: Dp = 4.dp,
    val lensExtraWidth: Dp = 16.dp,
    val lensRise: Dp = 0.dp,
    val lensInteraction: GlassInteraction = GlassInteraction(pressScale = 1f, illumination = 0.5f, gel = false),
    val form: AnimationSpec<Float>, val subside: AnimationSpec<Float>,
    val arrive: AnimationSpec<Float>, val settle: AnimationSpec<Float>,
    val flingVelocity: Dp = 420.dp,
    val gel: Float = 0.06f, val gelReference: Dp = 1200.dp, val gelSpring: AnimationSpec<Float>,
    val pillWidth: Dp = Dp.Unspecified,
    val heldScale: Float = 1f, val heldLift: Float = 0f, val selectedScale: Float = 1f,
    val lensFuse: Dp = 0.dp,
    val selector: GlassSelectorSpec? = null,
)
```

`Dark` is the measured preset. `RestingInset` and `HeldLens` are the two materials the indicator
morphs between, exposed so a host can start from them.

#### `selector` — the deforming body

Null keeps the capsule selector this component shipped with, and every preset above is on that
path. Non-null replaces it with the convex hull of two unequal disks, whose length, mean radius and
end asymmetry evolve separately; the selector's outline, normal, optical band and coverage all come
from that body, so the aperture really changes rather than a finished picture being stretched.
Nothing rasterised is scaled to fake the deformation.

Containment is by construction: both generating disks are projected into an inscribed polygon of
the bar, which puts their whole convex hull inside it. An ordinary tap therefore deforms *within*
the bar however fast it travels, and only a hold that outlives the threshold is granted the larger
envelope — touch-down alone does not start the held lens.

`GlassTabBarStyle.V3(dark, tintAmount, spec, edgeFold)` is the measured preset with this selector,
the exact ink compositor and the straight-run fold turned on together.

```kotlin
@Immutable
data class GlassSelectorSpec(
    val centreOmega: Float = 22f, val centreZeta: Float = 1.0f,
    val shapeOmega: Float = 26f, val shapeZeta: Float = 0.80f,
    val holdThresholdSeconds: Float = 0.120f,
    val formOmega: Float = 36f, val formZeta: Float = 1f,
    val sizeOmega: Float = 24f, val sizeZeta: Float = 0.90f,
    val maxExtraLength: Float = 0.60f,   // slot widths, at speed
    val maxRadiusDrop: Float = 0.12f,    // fraction of the base radius
    val maxSkew: Float = 0.18f,          // fraction of the base radius
    val releaseOmega: Float = 18f, val attachOmega: Float = 40f,
    val minRadiusDp: Float = 4f, val minLengthDp: Float = 1f,
    val admitInsetDp: Float = 2f, val admitReleaseDp: Float = 4f, val admitPaddingDp: Float = 6f,
    val maxWidthSlots: Float = 2.4f,
)
```

Every number here is a **design constant**. None is a recovered Apple time constant or a measured
physical property, and the component says so rather than implying otherwise.

---

## Styles

### `GlassStyle`

```kotlin
@Immutable
data class GlassStyle(
    val profile: GlassProfile = GlassProfile.Measured,
    val blurRadius: Dp = 1.5.dp,
    val backdropBlur: Dp = 0.dp,
    val backdropSigma: Dp = Dp.Unspecified,
    val refractionBand: Dp = Dp.Unspecified,
    val refractionDepth: Dp = 14.dp,
    val wideKernel: Dp = 10.dp,
    val fineShare: Float = 0.34f,
    val indexOfRefraction: Float = 1.5f,
    val bevelPower: Float = 2f,
    val cornerPower: Float = 2f,
    val dispersion: Float = 0.07f,
    val mirror: Float = 0f,
    val bevel: Dp = 1.5.dp,
    val tint: Color = Color(0xFFF1F1F1).copy(alpha = 0.565f),
    val tintLift: Float = 0f,
    val liftAdaptivity: Float = 0f,
    val adaptivity: Float = 0f,
    val legibility: Float = 0.6f,
    val specular: Float = 0.55f,
    val specularPower: Float = 6f,
    val counterLight: Float = 0.35f,
    val edgeLight: Float = 1f,
    val bevelPeak: Float = 0f,
    val edgeShadow: Float = 0f,
    val fresnel: Float = 0.10f,
    val highlightChroma: Float = 0.7f,
    val innerShadow: Float = 0.07f,
    val fallbackSurface: Color = Color(0xF2141416),
    val invertsWithBackdrop: Boolean = true,
    val dimmingLayer: Float = 0f,
    val restMap: GlassRestMap = GlassRestMap.Legacy,
    val edgeFold: Float = 0f,
    val inkDispersion: Float = Float.NaN,
    val heldInkContinuous: Boolean = false,
)
```

The knobs the shader consumes. `copy()` is the intended way to adjust one. The defaults are the
measured in-app material in a light appearance at the default Tint Amount; see
[the measured model](research/measured-model.md) and [How it works](how-it-works.md).

**`profile`** picks the optical model. `Measured` (default): an inward fold lens whose band is
0.6 of the corner radius when `refractionBand` is unspecified, a two-kernel backdrop (`blurRadius`
fine, `wideKernel` wide, mixed by `fineShare`), `tint` as `(1 - a) * backdrop + a * tint +
tintLift` with `liftAdaptivity` lowering the lift as the wide kernel's luma rises, and
`dispersion` as a fraction of the band in the mirrored zone only. `refractionDepth`, `mirror`,
`adaptivity` and `indexOfRefraction` are read by the `Legacy` profile only.

**`edgeFold`** is the straight-run source map on the measured profile: `f(u) = u + a(1-u)^3`,
which joins identity in value and both derivatives and, for `a > 1/3`, folds once at
`1 - 1/sqrt(3a)` with minimum source depth `1 - 2/(3 sqrt(3a))`. Page structure then appears twice
near a straight edge with opposite orientation, which is what makes text bend and stretch into the
edge instead of showing through it. The measured resting-corner table is still the authority at a
corner; the two are blended through a geometric arc/run transition. 0 keeps the shallow inward
offset a committed sheet pull measured, which is what every existing consumer has. Intended range
0.55 to 1.20. **Authored, not measured.**

**`inkDispersion`** is the per-channel split for [refracted content][], as a fraction of the
displacement. Unspecified means "whatever `dispersion` is", which is what the material has always
done; 0 takes one sharp sample instead, which is the only form whose alpha is exactly the glyph's
own. A maximum over three channels' alphas is not correct coloured transmission over an arbitrary
background — it makes a white glyph block green and blue light it never touched.

**`heldInkContinuous`** connects the held lens's **ink** map through the middle of its band
instead of stepping by about 0.14 W at half of it. The captures never established that the step
was a true discontinuity, and a smooth path across one source plane has a continuous source
coordinate. The page material's own map is unaffected.

**`backdropSigma`** is a pre-blur named by its Gaussian sigma and converted to each platform's
own radius at bind time, so it means the same blur on Android and the desktop; `backdropBlur` is
the legacy radius and is used when `backdropSigma` is unspecified.

**Factories** (every number measured): `inApp(dark, tintAmount)` for a navigation pill or
buttons; `toolbar(dark, tintAmount)` for a toolbar or tab bar, the pill's kernels with the
toolbar's own tint (white at 0.55-0.67 in light; black at 0.41-0.63 over the 35/255 lift in
dark, more opaque than the pill at Clear and less responsive to Tint Amount);
`systemBackdrop(tintAmount)` for a full-screen layer such as Control Center; `menu()`;
`shellIcon()`; `dock()`; `coverSheet()`. `Regular` is `inApp(dark = false)`.

**The two blurs are not interchangeable.** `blurRadius` runs inside the shader and tapers to
zero at the rim, preserving the signature edge; `backdropBlur` is chained ahead of the shader,
is higher quality, and destroys that edge. See [the two blurs](https://github.com/shayann07/liquidglass#the-two-blurs).

**The rim has five knobs and they are not interchangeable.** `specular` sets the lobe where the
bevel faces the light; `counterLight` how much of it reaches the face turned away (a bar lit from
overhead wants all of it, a free-standing panel a little); `edgeLight` the brightness of the
outermost line relative to that lobe (a hairline-edged bar wants it well above 1, a soft bead
near 0); `bevelPeak` whether the highlight sits on the edge (0, a chamfer) or inside it
(around 0.4, a bead); and `edgeShadow` the dark contour just inside the boundary.

`edgeShadow` is what defines a shape where nothing is lit. The edge line is **entirely
directional** — there is no floor, because a line of even brightness reads as a stroke drawn
round the shape and the reference has none. On a lens lit from overhead that leaves the left and
right sides dark, and the reference shows exactly that: a one-pixel dark step, then the interior.
`GlassTabBarStyle.HeldLens` sets it to 0.08, which measures on device as a dip to 21 against an
interior of 42, against the reference's 24 against 41. `fresnel` is the only omnidirectional
brightening term.

`highlightChroma` decides how the rim highlight is composited. At 0 it is added to the colour, which desaturates: adding white to a saturated backdrop pulls it toward grey, so a lit rim over deep blue reads as a milky smear. At 1 it is applied in Oklab instead, raising lightness and chroma together, so the rim brightens and keeps the hue of what is behind it. Measured on a device, the lit top edge of a panel over a blue ground moves from 0.38 saturation to 0.45. It is 0 on `DarkChrome`, which was measured with the additive highlight and keeps it until there is a capture that says otherwise.

Note that the contour is drawn 1.5px inside the boundary rather than on it. Coverage only
reaches 1 at 0.75px, so anything painted on the outermost pixel is multiplied by a partial alpha
and composited against whatever lies outside, which makes it invisible over a dark ground.

**Legacy presets** (`GlassProfile.Legacy`, the 0.1 optics, unchanged): `Chrome` (floating over an app's own content),
`DarkChrome` (a dark-appearance bar, every number measured off an iOS 26 recording — see
[reference measurements](research/reference-measurements.md)), `Clear` (Apple's non-adaptive
variant — its zeros are deliberate and it requires its dimming layer), `Thick` (sheets and
dialogs).

`heldEdgeRecovery: Dp = 0.dp` optionally extends the dark side contour inward on a formed
`GlassProfile.Held` lens. Zero preserves the historical narrow contour described above.
The V3 preset uses `1.dp`, an authored shoulder and recovery curve constrained by several
original Phone frames. It changes material darkening, not source or ink sharpness, and
interpolates with the style during formation. Other presets retain zero.

### `GlassProfile.Held`, `heldLens`, `rawShare`

The third optical family, for the lens a finger raises on a tab bar (measured model, section
2c). `GlassProfile.Held` with `heldLens = 0` is identity — no rest lens at the arcs, no fold —
and at `heldLens = 1` the outer half of the band pulls the exterior in, compressed 1.3x, with a
seam hiding the content between the band and the interior; the modifier records the extra
margin the outward sampling needs. A resting indicator and the lens it swells into both sit in
this family so the morph never crosses into the fold. `rawShare` adds that share of the raw
backdrop on top of the glass the element looks `through` (the measured lens: 0.11); it is
ignored without `through`. `tintLift` may be negative when `liftAdaptivity` is 0 (the resting
indicator is the bar's output at 0.857 minus 17/255).

### `GlassMaterial`

```kotlin
object GlassMaterial {
    const val DEFAULT_TINT_AMOUNT: Float = 50f
    const val BEVEL_RATIO: Float = 0.6f
    val wideKernel: Dp = 10.dp
    val lightTint: Color = Color(0xFFF1F1F1)
    const val DARK_LIFT: Float = 35f / 255f
    fun opacity(dark: Boolean, tintAmount: Float = 50f): Float
    val toolbarLightTint: Color = Color.White
    fun toolbarOpacity(dark: Boolean, tintAmount: Float = 50f): Float
    fun fineShare(tintAmount: Float = 50f): Float
    fun fineKernel(tintAmount: Float = 50f): Dp
    fun systemBackdropSigma(tintAmount: Float = 50f): Dp
    fun coverSheetDim(pull: Dp): Float
    fun lensFormation(trackedPull: Dp): Float
}
```

The measured material as formulas, for hosts that build their own styles: tint opacity and fine
share against the Tint Amount slider (and the toolbar role's own opacity and tint), the kernels, the system backdrop's blur, the dim a cover
sheet applies to what it covers as a function of pull, and the lens formation a tracked drag
should pass to `lensFormation`.

### `GlassLight`

```kotlin
@Immutable
data class GlassLight(val x: Float = -0.35f, val y: Float = -0.94f) {
    companion object { val Default = GlassLight() }
}
```

Where the key light sits, as a unit vector in the panel's own space. `y` is negative upward,
matching Compose, so the default is above and slightly to the left — the direction a highlight
has to come from for a surface to read as convex rather than concave.

### `lerpGlassStyle` / `animateGlassStyle`

```kotlin
fun lerpGlassStyle(start: GlassStyle, stop: GlassStyle, fraction: Float): GlassStyle

@Composable
fun animateGlassStyle(
    start: GlassStyle,
    stop: GlassStyle,
    morphed: Boolean,
    animationSpec: AnimationSpec<Float> = GlassMotion.Morph,
): GlassStyle
```

Carries the material with the geometry during a morph, because Apple's material changes *with*
size. Use the same spec your layout uses for the size, so the two cannot desynchronise.

`invertsWithBackdrop` and `fallbackSurface` snap at the midpoint rather than interpolating —
they are decisions, not quantities.

---

## Shapes

### `GlassSquircleShape`

```kotlin
@Immutable
data class GlassSquircleShape(
    val cornerRadius: Dp,
    val power: Float = 4f,
) : Shape
```

A rounded rectangle whose corners are superellipse arcs rather than circular ones — Apple's
actual corner geometry. An ordinary `Shape`, so it works for `clip`, `background` and `border`;
pass the same instance to `liquidGlass` and the material reads the exponent off it, so the field
and the clip agree.

`power = 2` is identical to `RoundedCornerShape`; `4` is Apple's squircle.

### `DefaultSquircleRadius`

```kotlin
val DefaultSquircleRadius: Dp = 28.dp
```

The squircle corner radius Apple's own containers land near, for a card-sized surface.

---

## Merging

### `LiquidGlassContainer`

```kotlin
@Composable
fun LiquidGlassContainer(
    state: LiquidGlassState,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.Regular,
    light: GlassLight = GlassLight.Default,
    mergeDistance: Dp = 24.dp,
    content: @Composable LiquidGlassContainerScope.() -> Unit,
)
```

Renders several panels as one body of glass. Members within `mergeDistance` of each other grow a
neck and fuse. Nothing animates the merge — it is a consequence of the geometry, which is why it
stays correct at any speed and under interruption.

Keep `mergeDistance` at or below your layout's own spacing, or members blend at rest.

### `LiquidGlassContainerScope`

```kotlin
interface LiquidGlassContainerScope {
    fun Modifier.glassMember(shape: Shape): Modifier
}
```

Marks a child as taking part in the container's merged field. It draws no material of its own —
it contributes its shape and the container renders all of them in one pass.

Capped at **eight members**: SkSL needs an unrollable loop, and every member is evaluated at
every pixel, which is exactly what lets the fields interact.

### `GlassMember`

The handle the container keeps per member. Created by `glassMember`; you never construct one.

---

## Sheets

### `LiquidGlassCoverSheet`

```kotlin
@Composable
fun LiquidGlassCoverSheet(
    pull: Dp,
    tracked: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(bottomStart = 47.dp, bottomEnd = 47.dp),
    background: Color = Color.Black,
    style: GlassStyle = GlassStyle.coverSheet(),
    light: GlassLight = GlassLight.Default,
    content: @Composable BoxScope.() -> Unit,
)
```

A cover sheet as iOS 27 draws one. The sheet is opaque and shows [content] — its own surface,
recorded as a backdrop of its own — with the measured fold at its bottom edge; what it covers
is dimmed by `GlassMaterial.coverSheetDim(pull)`, which saturates at 0.706 after about 230 dp.
`pull` is how far the sheet has come down; `tracked` is true while a finger holds it, which is
the only time the fold shows (`GlassMaterial.lensFormation(pull)`), and false once released,
when the lens eases out. The app beneath is neither sampled nor needed.

## Interaction

### `GlassInteraction`

```kotlin
@Immutable
data class GlassInteraction(
    val pressScale: Float = 1.04f,
    val illumination: Float = 1f,
    val gel: Boolean = true,
    val pressGrowth: Dp = 0.dp,
    val pressLift: Float = 0f,
    val pull: Boolean = false,
    val pullFollow: Float = 0.78f,
    val pullElongation: Float = 0.45f,
    val pullWidthRatio: Float = 1.1f,
    val pullLimit: Float = Float.POSITIVE_INFINITY,
    val response: GlassResponse = GlassResponse.Expressive,
    val pullShape: GlassPullShape = GlassPullShape.Adaptive,
) {
    companion object {
        val Default = GlassInteraction()
        val Calm: GlassInteraction // recommended control/card preset
        val Pullable: GlassInteraction // historical expressive response
        val ReducedMotion: GlassInteraction // no elastic geometry
    }
}
```

Opt-in, as Apple makes it. `ReducedMotion` is what Apple's setting asks for: no scale, no
bounce, no gel, glow at half — the feedback survives, the elasticity does not.

### `GlassPressSource`

```kotlin
@Stable
class GlassPressSource {
    fun press(localPosition: Offset, pullOffset: Offset = Offset.Zero)
    fun release()
}

@Composable
fun rememberGlassPressSource(): GlassPressSource
```

Drives a panel's press response from outside, for when **another node owns the gesture**.

`Modifier.liquidGlass` normally watches its own pointer, which is right for a panel that is also
the thing you touch. It is wrong for anything a *parent* manipulates — a selection indicator
inside a tab bar is the case that forced this to exist: the indicator sits beneath the tab
buttons, so it never sees a touch, and the bar has to drag it. Without a way in, such an element
can be moved but can never light up.

Pass it as `liquidGlass(..., pressSource = source)` and call `press()` on every pointer move as
well as on down, so the glow tracks rather than jumps. `release()` deliberately leaves the
position where it was: the glow fades from where the finger lifted rather than sliding back to
the middle of the panel.

Positions are in the **panel's** local pixels, not the gesture owner's.

### `GlassMotion`

Springs and timings, kept together so the press channels cannot drift apart:
`PressDown`, `PressUp`, `Morph`, `GlowIn`, `GlowOut`, `MaterializeIn`, `MaterializeOut`,
`MaterializeReduced`.

---

## Adaptation

### `rememberBackdropInversion`

```kotlin
@Composable
fun rememberBackdropInversion(
    state: LiquidGlassState,
    region: Rect? = null,
    sampleIntervalMillis: Long = 100L,
    enterAbove: Float = 0.60f,
    leaveBelow: Float = 0.44f,
): State<Float>
```

Decides whether glass over a region should flip to its light or dark style. Reads a sparse grid
off the main thread, applies hysteresis (the band stops it chattering when a headline scrolls
past), and crossfades over 180ms.

Hand the result to `rememberLiquidGlassState(inversion = …)` **and** to whatever draws the
content on the glass, so the two stay in step.

> Costs a bitmap read ten times a second. If your app is permanently dark or light, do not use
> it — leave `inversion` at 0.

### `glassShadow` / `glassShadowAlpha`

```kotlin
fun glassShadow(sizeFactor: Float, contrast: Float = 0f): GlassShadow
fun glassShadowAlpha(contrast: Float): Float

@Immutable
data class GlassShadow(val alpha: Float, val blurRadius: Dp, val offsetY: Dp)
```

Shadow parameters rather than a draw call, since Compose has no common shadow API taking a
colour and a blur. `sizeFactor` is 0 for small chrome and 1 for a large surface; `contrast` is
how busy the backdrop is.

### `GlassScrollEdge` / `GlassScrollEdgeStyle`

```kotlin
@Composable
fun GlassScrollEdge(
    ground: Color,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    style: GlassScrollEdgeStyle = GlassScrollEdgeStyle.Soft,
    inverted: Boolean = false,
    fromBottom: Boolean = false,
)

enum class GlassScrollEdgeStyle { Soft, Hard }
```

Content dissolving into the ground beneath floating chrome. **This is not the material** —
Apple ships it as a separate object because it solves a different problem. Place it as a sibling
beneath the chrome.

`inverted` swaps the fade for a dimming when dark content pushes the glass into its dark style;
a fade to the ground colour over dark content does nothing visible, so without it the effect
silently stops working exactly when it is needed.

---

## Platform

### `LiquidGlassSupport`

```kotlin
expect object LiquidGlassSupport {
    val hasShaders: Boolean       // the full optical path; Android 13+ (API 33)
    val hasBackdropBlur: Boolean  // at least a backdrop blur; Android 12+ (API 31)
}
```

Check it if you want to change the *design* below Android 13 rather than just the colour. If you
only need the colour, set `GlassStyle.fallbackSurface` and ignore this.

---

## Sample

### `GlassGallery`

```kotlin
@Composable
fun GlassGallery(modifier: Modifier = Modifier)
```

The tuning surface: every style, a squircle, a path shape, an interactive panel, the
accessibility modes and a fusing container, over saturated colour with hard edges and fine text.

It is in the published source deliberately. The material can only be judged against content
worth refracting — over a plain ground every style looks the same and so does every mistake —
and this is what it was tuned against.

## Anchored Pullable interaction (Astra r13)

`GlassInteraction.Pullable` deforms material around the fixed control centre. Ordinary content
and hit targets remain anchored; long bars do not rotate or shear. `pullFollow` controls input
compliance, `pullLimit` bounds it, and `pullElongation`/`pullWidthRatio` control the strain.
`Default` preserves Legacy behavior. `ReducedMotion` removes elastic response. External
`GlassPressSource` supplies local press coordinates and optional cumulative drag displacement through
`press(localPosition, pullOffset)`. The modifier applies the same resistance and springs as self-owned input.
See [generic interaction](generic-interaction.md) for integration, ownership and navigation details.

### Size-adaptive drag (r14)

Pullable preserves touch feedback and controls up to 80dp. Its drag-only surface policy
smoothly transitions by 160dp to one-fifth gain and a 2dp total extension cap. Both dimensions
use unpressed layout size in dp; density does not change the response. This is automatic in
`liquidGlass`, not a separate card implementation. See [generic interaction](generic-interaction.md).

## Scene, continuous lens and calm response (development)

These APIs require the current source snapshot; they are not in the published `0.1.0` artifact.

```kotlin
@Composable
fun GlassScene(
    background: Color,
    backdrop: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable GlassSceneScope.() -> Unit,
)

class GlassSceneScope : BoxScope {
    val state: LiquidGlassState
    fun Modifier.glass(
        shape: Shape = RoundedCornerShape(20.dp),
        style: GlassStyle = GlassStyle.Regular,
        interaction: GlassInteraction? = GlassInteraction.Calm,
        materialize: Float = 1f,
    ): Modifier
}
```

The host owns one state and backdrop recording. Size the host explicitly. Put page content in `backdrop`
and controls in `content`; do not nest the glass inside its own source. The exposed `state` supports
advanced tab-bar/container integration. `glass` does not make a button clickable or add a semantic role.

| API | Contract |
| --- | --- |
| `GlassStyle.clearLens(magnification: Float = 1.25f)` | Finite centre magnification 1–2.5; invalid values throw. Continuous map, zero blur and dispersion by default. |
| `GlassProfile.Lens` | Same smooth elliptical source map for material and refracted foreground; append-only enum addition. |
| `GlassInteraction.Calm` | Critical geometric springs; up to 3% press scale and 2dp growth per edge; gradual resisted drag with large-surface attenuation. |
| `GlassResponse` | `Expressive` preserves historical timing; `Calm` selects slower critical press/pull springs. |
| `GlassInteraction.response` | Timing parameter, default `Expressive`; existing positional source calls retain their meaning. |
| `GlassInteraction.pullShape` | Appended parameter, default `GlassPullShape.Adaptive`. `AreaPreserving` couples bounded stretch/narrowing for navigation without the card size policy; press and layout stay separate. See [generic interaction](generic-interaction.md). |
| `GlassTabBarStyle.Calm(dark: Boolean = true)` | V3 optical roles with calm selector travel, no off-axis selector squeeze, and material-only bar feedback. Its reference-backed 5% press is separate from the restrained drag response. |
| `GlassSelectorSpec.response` | Chooses the pose response family; default `Expressive` preserves V3. Other legacy fields still belong to the old horizontal controller. |
| `GlassTabBarStyle.barInteraction` | Optional whole-bar feedback, default null; `Calm()` enables it and keeps label layout fixed. |
| `GlassPressSource.press(localPosition, pullOffset = Offset.Zero)` | Local pixels; optional displacement since down for a gesture owned by the host. Rejects non-finite coordinates. Call `release()` on up **and cancellation**. |

`Lens` reuses `heldMagnification` internally as `1 - 1 / magnification`; prefer the factory to setting
that implementation parameter. It bypasses the held profile's stepped edge map and ignores dispersion.
It is an elliptical magnifier field: rectangular corners outside the inscribed ellipse remain identity.
The historical `Held` family intentionally retains its navigation optics.

These additions preserve existing presets at source level. Kotlin data-class constructor changes are
not a guarantee of binary compatibility for already compiled consumers; rebuild consumers against the
new snapshot. Keep your system accessibility preference wired to `ReducedMotion`/`motionEnabled`.

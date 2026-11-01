# API reference

This page covers the Kotlin API. Native and portable APIs are documented in the
[cross-stack guide](porting.md), with [current capabilities and visuals](development-status.md).
The .NET guide includes `GlassPainter`, `CalmInteraction`, `GlassFeedback` and `ControlBackdrop`;
these are source-preview APIs, not additions to the published Maven artifact.

Every public symbol in the library. The guides explain *why*; this is *what*.

> **Since 0.2.0.** The API in the first section replaced 0.1.0's state-and-modifier API. The APIs it
> replaced are listed under [Removed](#removed-18-october).

---

## Glass

The names follow SwiftUI's (`glassEffect(_:in:)`, `Glass`, `GlassEffectContainer`,
`glassEffectID(_:in:)`), in Kotlin casing.

```kotlin
LiquidGlassTheme {
    Box {
        Page()
        Icon(Icons.Default.Edit, null,
            Modifier.align(Alignment.TopEnd).size(44.dp).glassEffect(Glass.Regular.interactive(), CircleShape))
    }
}
```

### `LiquidGlassTheme`

```kotlin
@Composable
fun LiquidGlassTheme(
    dark: Boolean = isSystemInDarkTheme(),
    background: Color = if (dark) Color.Black else Color.White,
    glass: Glass = Glass.Regular,
    containerSpacing: Dp = 20.dp,
    renderScale: Float = 1f,
    reduceTransparency: Float = 0f,
    increaseContrast: Float = 0f,
    content: @Composable () -> Unit,
)
```

The root of Liquid Glass. Wrap the app in it once, as you would `MaterialTheme`. It records what
is drawn below it once per frame, and every `glassEffect` below it refracts what is behind it.
Nothing has to be marked as a backdrop. Glass draws nothing in place: it is drawn over the
recording with its own content on top, so no glass samples itself.

A theme nested inside another changes the defaults, the appearance and the accessibility settings
for its subtree. The outermost one records, and its `background` is the one used.

Views of that recording follow consumer lifetime: changing settings
releases the old view when no child still uses it, and disposing the last consumer removes it.
Children using the same settings share one view.

| Parameter | |
| :--- | :--- |
| `dark` | The appearance glass variants resolve in (`Glass.Regular`, `Glass.TabBar` and `Glass.Toolbar` differ between the two). Follows the system by default. |
| `background` | The **opaque colour behind the app** wherever it draws nothing. Not decoration: the recording is transparent wherever nothing was painted, and the shader needs this to reconstruct what a viewer sees there. A wrong value shows as a halo around every panel. |
| `glass` | What `glassEffect` draws when it is given no `Glass`. |
| `containerSpacing` | `GlassEffectContainer`'s default merge distance. |
| `renderScale` | 0.25 to 1. The fraction of full resolution the glass renders at. See [Performance](performance.md#rendering-at-reduced-resolution). |
| `reduceTransparency` | 0 to 1. Reduce Transparency: raises the material's opacity. Android has no system setting; drive it from your own. See [Accessibility](accessibility.md). |
| `increaseContrast` | 0 to 1. Increase Contrast: the material goes predominantly black or white with a contrasting border. |

### `Modifier.glassEffect`

```kotlin
fun Modifier.glassEffect(
    glass: Glass? = null,
    shape: Shape = CapsuleShape,
    link: GlassAccessoryLink? = null,
): Modifier
```

Draws this element as Liquid Glass, as SwiftUI's `glassEffect(_:in:)` does: the screen behind it
refracted through `shape`, with the element's own content on top. Layout, hit targets, clicks and
semantics stay the element's own; it does not make anything clickable or add a role.

| Parameter | |
| :--- | :--- |
| `glass` | What to draw. `LiquidGlassTheme`'s `glass` when null. `Glass.Identity` draws the content only. |
| `shape` | Rounded rects, capsules and circles are evaluated analytically; `GlassSquircleShape` carries its own corner exponent; anything else is measured. A capsule by default, as in SwiftUI. See [Shapes](shapes.md). |
| `link` | Publishes this glass's press and pull to a `GlassTabBar` whose `GlassTabBarAccessory` has the same link, so the bar's neck follows it. See [the search button](tab-bar.md#a-neighbouring-control-the-search-button). |

Needs a `LiquidGlassTheme` above it. Without one it draws a frosted, rim-lit stand-in and logs
one warning; it does not crash. Inside a `GlassEffectContainer` it joins the container's merged
body instead of drawing its own glass.

Current limits of the renderer: glass draws above content that is not glass, and it does not take
an ancestor's alpha. Do not fade glass by fading a parent; change its `Glass` instead (for example
to `Glass.Identity`, or through `animateGlassStyle`).

Where the platform cannot run the shader this degrades to `style.fallbackSurface` with the same
rim lighting.

### `Glass`

```kotlin
@Immutable
class Glass {
    fun tint(color: Color?): Glass
    fun interactive(enabled: Boolean = true, response: GlassInteraction = GlassInteraction.Calm): Glass
    val isIdentity: Boolean
    fun style(dark: Boolean): GlassStyle?
    fun light(dark: Boolean): GlassLight

    companion object {
        val Regular: Glass
        val Clear: Glass
        val Identity: Glass
        val TabBar: Glass
        val Toolbar: Glass
        fun Lens(magnification: Float = 1.4f): Glass
        operator fun invoke(style: GlassStyle, dark: GlassStyle? = null, light: GlassLight = GlassLight.Default): Glass
    }
}
```

A kind of glass, as SwiftUI's `Glass` is. Start from a variant and refine it:
`Glass.Regular.tint(Color.Blue).interactive()`.

| Variant | |
| :--- | :--- |
| `Regular` | Apple's regular glass: the measured in-app material (`GlassStyle.inApp(dark)`), for navigation pills, buttons and panels. |
| `Clear` | Apple's clear glass (`GlassStyle.Clear`): permanently more transparent, no adaptive behaviour. Needs content behind it that is dimmed or calm enough to stay legible. |
| `Identity` | No glass: the element draws its content only. For switching glass off without changing the layout. |
| `TabBar` | iOS 26's tab bar material, measured on recordings (`GlassTabBarStyle.Calm(dark).bar`, with its light). |
| `Toolbar` | A toolbar's material (`GlassStyle.toolbar(dark)`): the pill's, more opaque. |
| `Lens(magnification)` | A clear magnifier (`GlassStyle.clearLens`): `magnification` at the centre, easing back to none at the rim, 1 to 2.5. |
| `Glass(style, dark, light)` | Every optical parameter given: see `GlassStyle`. `dark` is used in a dark appearance when given, `style` otherwise; `light` is where the light comes from. |

`tint` colours the glass, as `.tint(_:)` does; use it to carry meaning, not as decoration. Null
removes a tint. `interactive` makes the glass respond to touch and pointer, as `.interactive()`
does: it lifts, glows where it is pressed and, with `GlassInteraction.Pullable`, stretches toward
the finger. It observes the pointer without consuming it, so the element keeps its own clicks and
scrolls. Apply it only to elements that act on a tap. `response` chooses how it responds; see
`GlassInteraction`. `style(dark)` and `light(dark)` resolve the variant for an appearance; `style`
is null for `Identity`.

### `CapsuleShape`

```kotlin
val CapsuleShape: RoundedCornerShape = RoundedCornerShape(percent = 50)
```

A capsule: SwiftUI's default glass shape, and `glassEffect`'s.

### `GlassEffectContainer`

```kotlin
@Composable
fun GlassEffectContainer(
    modifier: Modifier = Modifier,
    spacing: Dp = Dp.Unspecified,
    glass: Glass? = null,
    content: @Composable () -> Unit,
)
```

Groups glass so that nearby pieces merge, as SwiftUI's `GlassEffectContainer` does. Every
`glassEffect` inside joins one body of glass: pieces within `spacing` of each other grow a liquid
neck and fuse, and part again as they move apart. Nothing animates the merge; it is a consequence
of the geometry, which is why it stays correct at any speed and under interruption.

`spacing` is `LiquidGlassTheme`'s `containerSpacing` when unspecified; keep it at or below your
layout's own spacing, or pieces blend at rest. The container draws all its pieces with one `glass`
(the theme's default when null): the pieces' own variants are not mixed. It is optional; glass
outside a container works on its own. It needs a `LiquidGlassTheme` above it.

Capped at **eight pieces**: SkSL needs an unrollable loop, and every piece is evaluated at every
pixel, which is exactly what lets the fields interact.

### `Modifier.glassEffectID` / `rememberGlassNamespace`

```kotlin
fun Modifier.glassEffectID(id: Any, namespace: GlassNamespace): Modifier

@Composable
fun rememberGlassNamespace(): GlassNamespace

@Stable
class GlassNamespace
```

Names a piece of glass for morphing, as SwiftUI's `glassEffectID(_:in:)` does: when a piece with
this `id` appears in `namespace` where another with the same id was, it grows out of the old one's
outline instead of popping in. Use it inside a `GlassEffectContainer`, after `glassEffect` in the
modifier chain.

```kotlin
val ns = rememberGlassNamespace()
GlassEffectContainer(spacing = 20.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(..., Modifier.size(44.dp).glassEffect().glassEffectID("edit", ns))
        if (expanded) Icon(..., Modifier.size(44.dp).glassEffect().glassEffectID("share", ns))
    }
}
```

---

## Removed (18 October)

These were public in `0.1.0` or in the development snapshot before 18 October. They are internal
now; the library still uses several of them underneath the API above.

| Removed | Replacement |
| :--- | :--- |
| `rememberLiquidGlassState(background, inversion, frost, contrast)`, `LiquidGlassState` | `LiquidGlassTheme(background = …, reduceTransparency = …, increaseContrast = …)` around the app. Backdrop-driven inversion has no public replacement yet; choose the appearance with `dark`. |
| `Modifier.liquidGlassSource(state)` (both forms) | Nothing: `LiquidGlassTheme` records the screen. |
| `Modifier.liquidGlass(state, shape, style, …)` (both forms) | `Modifier.glassEffect(Glass(style), shape)`, or a named variant: `GlassStyle.inApp(dark)` ≈ `Glass.Regular`, `GlassStyle.Clear` = `Glass.Clear`, `GlassStyle.clearLens(m)` = `Glass.Lens(m)`, `GlassStyle.toolbar(dark)` = `Glass.Toolbar`, `GlassTabBarStyle.Calm(dark).bar` = `Glass.TabBar`. `interaction = I` becomes `.interactive(response = I)`; `light` moves into `Glass(style, light = …)`. `materialize`, `pressSource`, `refractContent`, `through`, `fuse`, `lensFormation` have no public replacement. |
| `GlassScene`, `GlassSceneScope`, `Modifier.glass()` | `LiquidGlassTheme(background = …) { Box { backdrop content; … Modifier.glassEffect(…) } }` |
| `LiquidGlassScene`, `ProvideLiquidGlassState`, `LocalLiquidGlassState`, `currentLiquidGlassState` | `LiquidGlassTheme` |
| `LiquidGlassContainer(state, mergeDistance = d) { Modifier.glassMember(shape) }`, `LiquidGlassContainerScope`, `GlassMember` | `GlassEffectContainer(spacing = d) { Modifier.glassEffect(shape = shape) }` |
| `rememberBackdropInversion` | None yet. |
| `GlassTabBar(state, itemCount, …)` | `GlassTabBar(itemCount, …)` inside a `LiquidGlassTheme`. |

---

### `GlassFuse`

```kotlin
class GlassFuse(bounds: DpRect, cornerRadius: Dp, smoothing: Dp = 16.dp, anchored: Boolean = false)
```

Another rounded rect, in an element's own coordinates, that the element's outline **fuses** with.
Still public, but the `fuse` parameter that took it is internal since 18 October; `GlassTabBar`
builds its fused outlines (`lensFuse`, `trailingAccessory`) from it. Not a union: where the two
outlines cross the silhouette bows out by about a quarter of `smoothing` to meet the other shape
instead of making a crease, and the bevel, rim and refraction all follow the fused outline from
there on.

This is what iOS 27's tab bar does with its selection lens, which stands proud of the bar; on the
reference the bar's edge sits 12 px above both outlines where they cross and rejoins the flat run
about 40 px away, which is 16 pt of smoothing (measured model, section 2d). `GlassTabBarStyle` exposes it as
`lensFuse`, off by default: at the measured width it reads as the bar swelling to make room for
the lens rather than as the lens rising out of the bar.

Costs the closed-form normal, which cannot describe a fused outline, so the shader differences
the distance field instead.

`anchored = true` keeps the rect where it was laid out while the element's material grows or
strains under touch, for a separate neighbouring control the material should reach for rather
than carry along; only the near side and the fusion are drawn by the element. `GlassTabBar`'s
`trailingAccessory` uses it.

## Components

### `GlassTabBar`

```kotlin
@Composable
fun GlassTabBar(
    itemCount: Int,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: GlassTabBarStyle = GlassTabBarStyle.Calm(dark),   // dark: the LiquidGlassTheme's appearance
    motionEnabled: Boolean = true,
    materialize: Float = 1f,
    trailingAccessory: GlassTabBarAccessory? = null,
    item: @Composable (index: Int, selected: Boolean) -> Unit,
)

class GlassTabBarAccessory(size: Dp, gap: Dp = 8.dp, fusion: Dp = 6.dp, yieldsToLens: Boolean = false,
    lensFusion: Dp = 0.dp, link: GlassAccessoryLink? = null)
```

Needs a `LiquidGlassTheme` above it, which supplies what the bar refracts; it takes no state
(the `state` parameter was removed on 18 October). Without a theme it draws its fallback and logs
one warning. The default style follows the theme's `dark`.

`trailingAccessory` describes a separate glass control beside the bar's trailing end (iOS 26
Phone's search button). The bar fuses with it through a liquid neck while held; you lay it out
and draw it. `yieldsToLens` lets a formed pose selector pressing into that end take the neck's
place. See [the search button](tab-bar.md#a-neighbouring-control-the-search-button).

A tab bar whose selection indicator is a piece of glass you can pick up: a flat inset at rest, a
lens while held that stands proud of the bar and looks *through* it, dragged 1:1 and committed on
release. Every default is measured off an iOS 26 tab bar; see [Tab bar](tab-bar.md).

| Parameter | |
| :--- | :--- |
| `item` | Called twice per tab on the shader path — `selected = false` for the copy on the bar, `true` for the copy inside the lens — and once, with the real selection, where the shader is unavailable. Change only colour between the two. |
| `motionEnabled` | False disables the lens, the gel and the fling; taps and drags still select. |
| `materialize` | 0 to 1, for the bar to materialize in and out. Drive this instead of `alpha`: at 0 the bar returns the backdrop untouched, so it leaves by ceasing to bend light. |

Put a shadow on `modifier` with `clip = false`; the held lens is taller than the bar.

### `GlassTabBarStyle`

`GlassTabBarStyle.Measured(dark, tintAmount)` is `Dark` with its bar on the measured material and, since
2026-09-12, the indicator and lens of the iOS 27 recording: `pill = RestingInsetMeasured`, `lens =
HeldLensMeasured`, `pillInset` 4dp, `pillWidth` 84dp, `lensOverflow` 5dp, `lensExtraWidth` 21dp, `heldScale`
1.05, `heldLift` 16/255, `selectedScale` 1.18 (see [the tab bar](tab-bar.md)). Its base
(`GlassStyle.inApp`); see [Tab bar](tab-bar.md#the-measured-bar). `Dark` keeps the 0.1 optics, and
`Ios27` is `Dark` with iOS 27's measured rim.

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
    val heldScale: Float = 1f, val heldLift: Float = 0f, val touchLift: Boolean = false,
    val selectedScale: Float = 1f,
    val lensFuse: Dp = 0.dp,
    val selector: GlassSelectorSpec? = null,
    val barInteraction: GlassInteraction? = null,
    val deformItemsWithBar: Boolean = false,
    val tapTintFade: Float = 0f,
    val tapSelectedScale: Float = 1f,
    val slotWidth: Dp = Dp.Unspecified,
)
```

`Dark` is the measured preset. `RestingInset` and `HeldLens` are the two materials the indicator
morphs between, exposed so a host can start from them.
`touchLift` applies `heldLift` for any touch, a tap included (rising within 50 ms of the press,
falling over 260 ms after the lift), as iOS's bar lifts on every touch; off by default, on in dark
`Calm()`. `CalmRecordedSpace()` is dark Calm as calibrated before 15 October, against recordings
decoded as if they were sRGB. `CalmClear()` is dark Calm with the clearer bar of WhatsApp's and the
App Store's iOS bars (black at 0.10 instead of 0.41 over the same lift, `legibility` 2 and a 7 dp blur
so text under it is one soft copy as iOS's is; in light appearance WhatsApp's resting pill;
[tab bar guide](tab-bar.md#a-clearer-bar)).
`Calm(dark = false)` is measured on the owner's light recording (17 October): a bar passing 0.59 of the
page plus 115 (white at 0.41 plus 10/255) with a darker rim and a faint shadow, a clear resting pill
10/255 below it, a clear held lens with a light outline (243/255) and a tight shadow under it, touch
lift and dark Calm's tap optics.
`CalmRecordedSpace(false)` keeps the light preset it replaced ([tab bar guide](tab-bar.md#light-appearance)).

`slotWidth` lays the tabs on equal slots of that width centred in the bar, instead of sharing the
bar out inside `contentPadding`. Slots wider than the bar allows close up at the ends: an end tab
moves in until its resting pill (`pillWidth`) sits `pillInset` inside the bar's end. The pill, the
lens, hit targets, drag and the V3 body all follow these centres. Unspecified (the default) keeps
the shared-out slots. Measured on iOS: WhatsApp's 348 pt bar uses 71.5 pt slots (its widest item
plus 8 pt) with a 78 pt pill and 4 pt inset; the App Store's uses 64 pt
([tab bar guide](tab-bar.md#tabs-on-set-slots)).

#### `selector` — the deforming body

Null keeps the original capsule selector. `V3()` and `Calm()` supply a non-null selector spec and
use its default pose controller: the outline, normal, optical band and coverage follow the same
deforming body. The historical two-disk controller remains available with `poseMotion = false`
for comparison. Most fields in `GlassSelectorSpec` tune that historical controller; the pose path
uses its internal preset, selected by `response`. They are not per-screen knobs for `Calm()`.

The historical two-disk controller projects both disks into an inscribed polygon of the bar.
The opt-in `Calm()` pose controller couples projected area and spine length for taps and moving
endpoint landings. During a moving endpoint release, its leading edge recovers toward the
resting endpoint while its centre follows from the changing width. This keeps translation and
shape connected without moving layout or hit targets. A released selector compresses as it
decelerates near an end tab; middle-tab and stationary releases retain their previous response.
In current `Calm()` the trailing edge of an end-tab landing travels on underdamped until it turns,
then recovers critically, and a still-formed lens may press past the bar's end by a share of the
bar height that recedes with its formation; the first two-edge landing is kept as
`GlassPoseSpec.CalmCriticalLanding` for comparison.
Setting `motionEnabled = false` during
recovery clears stored deformation on the next advance. Short moving taps can grow above and
below the resting bar, as in the owner's labelled Phone recording. This does not make that tap
a held gesture. Ordinary hold growth,
selector travel and the bar's anchored drag strain remain separate. Applications using `Calm()`
do not need to implement this deformation or tune it per item. See the
[motion evidence and limitations](research/atlas-stabilization.md#new-labelled-motion-recordings).

Calm's resting inset tint is limited to the underlying bar's shape, even while the recovering
body extends above or below it. Refraction, rim lighting and body coverage stay independent.
The mask follows the bar's drawing transform without changing its layout or hit targets;
applications do not supply a separate clipping shape.

Dark `Calm()` also enables a development tap-appearance response: `tapTintFade = 1f` and
`tapSelectedScale = 1.15f`. As the coupled body gains projected area, the inset pigment fades
and selected ink grows about its own anchor. The response is bounded below full material
formation and fades as held zoom takes over. It does not enable a hold, change the bar's lift
or strain, move hit targets, or drive the selector's held band/rim parameters.
Set these two fields to `0f` and `1f` respectively to retain the earlier tap appearance.
Light `Calm()` uses them too since 17 October (its light recording shows the same refracting
transit and growing ink); historical presets keep
those defaults. The gains are authored from measured
dark Phone patches and icon bounds; full optical parity remains unproven. See the
[candidate evidence](https://github.com/shayann07/liquidglass/blob/main/review/atlas/tap-optics-candidate-2026-10-11/README.md)
for current verification status.

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
    val outerContour: Float = 0f,
    val outerContourFloor: Float = 0f,
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

**`restMap`** is `GlassRestMap.Legacy` (the constructor's default) or `Measured` (the default of
`GlassStyle.inApp` and `toolbar` since 26 October): which source map the resting
corner lens of the measured profile uses. `Measured` is the resting-corner table fitted on the
Photos toolbar's 72 px ends in the closeout; it changes only the resting-corner term and is a scale
extrapolation at any other radius. Ignored by the legacy profile and by the held family. See
[limitations](limitations.md#the-rest-lens-is-measured-at-one-size-and-selected-per-style).

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

`outerContour` is iOS 27's version of that line, and it is a different mechanism: it lies
entirely **outside** the outline (the outermost wholly covered pixel is untouched), about 1.5 px
wide, and it is strongest where the rim is unlit and zero at the lit poles. Measured on iOS 27's
44 pt buttons over white, it removes 0.61 of the backdrop at the sides in a dark appearance and
0.31 in a light one, where about a quarter remains at the poles (`outerContourFloor`). It is drawn
as black at that opacity, so it shows over light content and vanishes over black. It is off (0)
on every core preset. `GlassMaterialDefaults` in `liquidglass-material3` turns it on with the
measured values, with the light straight overhead (`GlassLight(0f, -1f)`, measured at 0.0
degrees) and no dispersion. Lone elements and `GlassEffectContainer`'s merged shapes both draw
it. The tab bar's measured `Calm` styles leave it off, because they carry their own measured cap
contour (`capEdgeShadow`).

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

### `GlassProfile.Held`, `heldLens`, `rawShare`, `rawBlur`

The third optical family, for the lens a finger raises on a tab bar (measured model, section
2c). `GlassProfile.Held` with `heldLens = 0` is identity — no rest lens at the arcs, no fold —
and at `heldLens = 1` the outer half of the band pulls the exterior in, compressed 1.3x, with a
seam hiding the content between the band and the interior; the modifier records the extra
margin the outward sampling needs. A resting indicator and the lens it swells into both sit in
this family so the morph never crosses into the fold. `rawShare` adds that share of the raw
backdrop on top of the glass the element looks `through` (the measured lens: 0.11); it is
ignored without `through`. `rawBlur: Dp = 0.dp` blurs that raw backdrop before it is added: the
glass then lifts over content by what the content around it holds and adds nothing over black,
without drawing the content's edges a second time. Zero adds it sharp, as before; dark `Calm()`'s
held lens uses 0.5 blurred 50 dp (18 October; 0.22 at 20 dp on 17 October). `tintLift` may be negative when `liftAdaptivity` is 0 (the resting
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
reaches (`LiquidGlassCoverSheet` applies the last two; the modifier's `lensFormation` parameter is
internal since 18 October).

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
pass the same instance to `glassEffect` and the material reads the exponent off it, so the field
and the clip agree.

`power = 2` is identical to `RoundedCornerShape`; `4` is Apple's squircle.

### `DefaultSquircleRadius`

```kotlin
val DefaultSquircleRadius: Dp = 28.dp
```

The squircle corner radius Apple's own containers land near, for a card-sized surface.

---

## Merging

`GlassEffectContainer` is documented under [Glass](#glasseffectcontainer). The container it
replaced, `LiquidGlassContainer` with `glassMember`, is internal since 18 October; see
[Removed](#removed-18-october).

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
    val pressReach: Float = 0f,
    val releaseStiffness: Float = 0f,
    val releaseDamping: Float = 1f,
    val dragPressShare: Float = 1f,
    val pullLiftShare: Float = 1f,
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
`pressReach` (with `pull`): while pressed, the side nearer the touch grows by up to that share of
the width more, the far side growing as before; dark Calm navigation's bar uses 0.03. Drawing
only: layout and hit targets do not move. `releaseStiffness` / `releaseDamping` (when stiffness
is positive) replace the response's release spring; dark Calm navigation's bar uses 530 / 0.6.

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

Glass normally watches its own pointer, which is right for a panel that is also
the thing you touch. It is wrong for anything a *parent* manipulates — a selection indicator
inside a tab bar is the case that forced this to exist: the indicator sits beneath the tab
buttons, so it never sees a touch, and the bar has to drag it. Without a way in, such an element
can be moved but can never light up.

The `pressSource` parameter that took it is internal since 18 October; `GlassTabBar` uses it for
its selection lens, and no public modifier accepts it at present. The owner calls `press()` on
every pointer move as well as on down, so the glow tracks rather than jumps. `release()` deliberately leaves the
position where it was: the glow fades from where the finger lifted rather than sliding back to
the middle of the panel.

Positions are in the **panel's** local pixels, not the gesture owner's.

### `GlassMotion`

Springs and timings, kept together so the press channels cannot drift apart:
`PressDown`, `PressUp`, `Morph`, `GlowIn`, `GlowOut`, `MaterializeIn`, `MaterializeOut`,
`MaterializeReduced`.

---

## Adaptation

Backdrop-driven inversion (`rememberBackdropInversion`) is internal since 18 October and has no
public replacement yet. Choose the appearance with `LiquidGlassTheme(dark = …)`; see
[Adaptation](adaptation.md).

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
`glassEffect` with an interactive `Glass`, not a separate card implementation. See [generic interaction](generic-interaction.md).

## Continuous lens and calm response (development)

These APIs are in 0.2.0; they were not in 0.1.0.
The `GlassScene` host this section used to open with was removed on 18 October; use
`LiquidGlassTheme` ([Removed](#removed-18-october)).

| API | Contract |
| --- | --- |
| `GlassStyle.clearLens(magnification: Float = 1.25f)` | Finite centre magnification 1–2.5; invalid values throw. Continuous map, zero blur and dispersion by default. |
| `GlassProfile.Lens` | Same smooth elliptical source map for material and refracted foreground; append-only enum addition. |
| `GlassInteraction.Calm` | Critical geometric springs; up to 3% press scale and 2dp growth per edge; gradual resisted drag with large-surface attenuation. |
| `GlassResponse` | `Expressive` preserves historical timing; `Calm` selects slower critical press/pull springs. |
| `GlassInteraction.response` | Timing parameter, default `Expressive`; existing positional source calls retain their meaning. |
| `GlassInteraction.pullShape` | Appended parameter, default `GlassPullShape.Adaptive`. `AreaPreserving` couples bounded stretch/narrowing and a material-only directional bias for navigation, without the card size policy; press and layout stay separate. See [generic interaction](generic-interaction.md). |
| `GlassStyle.softDetail` | Appended parameter, default 0 (the measured along-axis high-pass with a dithered scatter). 1 draws the detail seen through the glass as one soft copy: the wide kernel's tone plus `fineShare` of an unrotated fine blur. Dark `Calm()` uses 1 on its bar and lens. |
| `GlassPullShape.Free` | Appended enum value. A free-standing button pulled off its place: the body follows the finger by `pullFollow` (resisted by `pullLimit`), stretches along the pull and thins across it, its content moves with it, and on release it squashes against its own resting edge away from the pull before settling (`GlassMotion.FreeRelease`). Drawing only; layout and hit targets stay put. Measured on iOS 26's Phone search button. |
| `GlassInteraction.pullLiftShare` | Appended parameter, default 1. Share of the press glow that remains once the finger has moved more than 4 dp (iOS's search button: 0.65 since 18 October; 0.6 on 17 October, 0.7 before). |
| `Modifier.glassEffect(..., link: GlassAccessoryLink? = null)`, `rememberGlassAccessoryLink()`, `GlassTabBarAccessory(..., link)` | Opt-in. The accessory's glass publishes its animated press and pull; a tab bar given the same link reaches for where the accessory is drawn, so pressing it joins the two through a neck and pulling it away breaks it. Null keeps the laid-out circle. |
| `GlassTabBarStyle.Calm(dark: Boolean = true)` | V3 optical roles with calm selector travel, no off-axis selector squeeze, and bounded bar drawing feedback shared with ordinary/selected ink. Its reference-backed 5% press is separate from the restrained drag response. |
| `GlassSelectorSpec.response` | Chooses the pose response family; default `Expressive` preserves V3. Other legacy fields still belong to the old horizontal controller. |
| `GlassTabBarStyle.barInteraction` | Optional whole-bar feedback, default null; `Calm()` enables it and keeps label layout fixed. |
| `GlassTabBarStyle.deformItemsWithBar` | Appended option, false for older presets and true in `Calm()`. Shares the material's resolved drawing transform with both ink variants; layout, hit targets and selector travel remain independent. Generic card foreground is unchanged. |
| `GlassTabBarStyle.itemsFollowBarPress` | Appended option, default true. False: the ink and the selector's anchor follow the bar's press growth only as far as the selector has formed, so a tap leaves the icons in place and a hold carries them. `Calm()` uses false (18 October). |
| `GlassTabBarStyle.tapTransitFold` | Appended option, default NaN (follows `tapOpticsFold`). The fold of a tapped selector while it travels; 0 shows the held lens's own optics, as a throw does. `Calm()` uses 0. |
| `GlassTabBarAccessory.lensFusion` | Appended parameter, default 0 dp. With `yieldsToLens`, a formed lens at the accessory's end joins it through a neck of this strength. |
| `GlassInteraction.pullReleaseDampingX` | Appended parameter, default NaN (the shared `FreeRelease` spring). A free button's release damping along x; `Pullable` uses 0.3. |
| `GlassStyle.capFold` | Appended parameter, default 0. With the measured profile, the outline's corner arcs lens what lies beyond them at rest, as the held lens's map does; straight runs keep their rest offset. `Calm()`'s bar uses 1. |
| `GlassStyle.capEdgeShadow` | Appended parameter, default 0. The edge contour times `1 + capEdgeShadow * nx^2` (nx the outline normal's horizontal component): a capsule's rounded ends darker than its runs. Light `Calm()`'s bar uses 2. |
| `GlassStyle.bodyBandScale` | Appended parameter, default 1. Scale on a selector body's refraction band (0.6 of its radius). `Calm()`'s lens uses 1.1 (0.3 before 26 October). |
| `GlassStyle.contactShadowInside`, `contactShadowBlur`, `contactShadowOffset` | Appended parameters, defaults 1, unspecified, unspecified. The share of the contact shadow drawn into the refracted backdrop, and the shadow's blur and downward offset (unspecified: sized from the panel). Light `Calm()`'s lens uses 0.5, 6 dp, 5 dp. |
| `GlassPressSource.press(localPosition, pullOffset = Offset.Zero)` | Local pixels; optional displacement since down for a gesture owned by the host. Rejects non-finite coordinates. Call `release()` on up **and cancellation**. |

`Lens` reuses `heldMagnification` internally as `1 - 1 / magnification`; prefer the factory to setting
that implementation parameter. It bypasses the held profile's stepped edge map and ignores dispersion.
It is an elliptical magnifier field: rectangular corners outside the inscribed ellipse remain identity.
The historical `Held` family intentionally retains its navigation optics.

These additions preserve existing presets at source level. Kotlin data-class constructor changes are
not a guarantee of binary compatibility for already compiled consumers; rebuild consumers against the
new snapshot. Keep your system accessibility preference wired to `ReducedMotion`/`motionEnabled`.

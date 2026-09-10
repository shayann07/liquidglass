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
| `through` | Glass this element looks **through**. A lens on a tab bar sees the bar, not past it: record the bar with `liquidGlassSource` into a second state and pass it here, and it is composited over the backdrop before this element's shader runs. The element must not be inside that source's subtree. See [Tab bar](tab-bar.md). |

Where the platform cannot run the shader this degrades to `style.fallbackSurface` with the same
rim lighting.

---

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
)
```

`Dark` is the measured preset. `RestingInset` and `HeldLens` are the two materials the indicator
morphs between, exposed so a host can start from them.

---

## Styles

### `GlassStyle`

```kotlin
@Immutable
data class GlassStyle(
    val blurRadius: Dp = 10.dp,
    val backdropBlur: Dp = 0.dp,
    val refractionBand: Dp = 26.dp,
    val refractionDepth: Dp = 14.dp,
    val indexOfRefraction: Float = 1.5f,
    val bevelPower: Float = 2f,
    val cornerPower: Float = 2f,
    val dispersion: Float = 0.018f,
    val mirror: Float = 0.18f,
    val bevel: Dp = 4.dp,
    val tint: Color = Color.White.copy(alpha = 0.10f),
    val adaptivity: Float = 0.65f,
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
)
```

The knobs the shader consumes. `copy()` is the intended way to adjust one.

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

**Presets:** `Regular` (the workhorse), `Chrome` (floating over an app's own content),
`DarkChrome` (a dark-appearance bar, every number measured off an iOS 26 recording — see
[reference measurements](research/reference-measurements.md)), `Clear` (Apple's non-adaptive
variant — its zeros are deliberate and it requires its dimming layer), `Thick` (sheets and
dialogs).

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

## Interaction

### `GlassInteraction`

```kotlin
@Immutable
data class GlassInteraction(
    val pressScale: Float = 1.04f,
    val illumination: Float = 1f,
    val gel: Boolean = true,
) {
    companion object {
        val Default = GlassInteraction()
        val ReducedMotion = GlassInteraction(pressScale = 1f, illumination = 0.5f, gel = false)
    }
}
```

Opt-in, as Apple makes it. `ReducedMotion` is what Apple's setting asks for: no scale, no
bounce, no gel, glow at half — the feedback survives, the elasticity does not.

### `GlassPressSource`

```kotlin
@Stable
class GlassPressSource {
    fun press(localPosition: Offset)
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

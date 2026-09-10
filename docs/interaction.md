# Interaction

## Press

Opt-in, as Apple makes it. The response is strong enough that applying it to everything would be
noise.

```kotlin
Modifier.liquidGlass(
    glass,
    RoundedCornerShape(30.dp),
    GlassStyle.Regular,
    interaction = GlassInteraction.Default,
)
```

Apple describes an interactive element as one that "scales, bounces and shimmers", where the
material "illuminates from within" starting under the fingertip and spreading to nearby glass,
with "gel-like flexibility… as it moves in tandem with your interaction".

Three channels do that, and they deliberately do **not** share a spring:

| Channel | Down | Up |
| :--- | :--- | :--- |
| scale | overshoots to 1.04, one visible bounce | settles calmly, no bounce |
| glow | rises in 90ms | falls over 260ms |
| touch point | tracks the finger continuously | freezes where it left, then fades |

The separation is the point. The scale is what the hand feels, so it overshoots; a bouncy
*release* reads as a rendering glitch. And freezing the touch point on release is what stops the
highlight snapping back to the middle of the panel as your finger lifts.

The glow is a Gaussian of σ = the panel's short edge, worth about a sixth of a stop at the peak.
That number is measured against a device; anything near 0.2 reads as a camera flash rather than
as glass responding to a touch. It drags a small magnification of the backdrop with it.

`interaction` observes but does not consume the gesture, so an element can be interactive glass
*and* a normal `clickable` without the two fighting.

### When something else owns the gesture

A panel that is also the thing you touch can watch its own pointer, and `interaction` alone is
enough. A panel that a *parent* manipulates cannot — a selection indicator inside a tab bar sits
beneath the tab buttons and never sees a touch, so it can be moved but never lights up, which is
exactly the half-finished feel of an indicator that animates without responding.

Hand it a `GlassPressSource` and drive it from wherever the gesture actually lives:

```kotlin
val press = rememberGlassPressSource()

Box(Modifier.liquidGlass(glass, shape, style, interaction = …, pressSource = press))

Box(Modifier.fillMaxSize().pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val grabbed = awaitHorizontalTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
            ?: return@awaitEachGesture           // a tap still reaches the buttons
        press.press(toPanelLocal(grabbed.position))
        horizontalDrag(grabbed.id) { change ->
            press.press(toPanelLocal(change.position))
            change.consume()
        }
        press.release()
    }
})
```

Two details worth copying. Claim nothing until the pointer crosses the touch slop, so a tap
stays a tap and only a drag becomes a drag. And convert into the **panel's** local pixels, not
the gesture owner's — the glow is positioned in the panel's own space.

### Content inside the glass

Content normally sits *on* a panel and is left alone: labels on a button, icons on a bar. Some
content is what the glass is looking *at*, and has to be bent with everything else it shows.
The clearest case is Apple's tab bar. The selection indicator there is a lens **above** the
tabs, and a tab looks selected because you are seeing it through the lens — slide the lens
halfway across a symbol and the symbol is half one colour and half the other, with the rim
compressing and colour-splitting its edges as it passes.

`refractContent = true` does that. The element's content is recorded into the backdrop under it
before the shader runs, so it is displaced at the rim, dispersed, echoed by the mirror band and
covered by the tint like the rest of the backdrop — and clipped to the shape, so a lens can carry
a copy of a whole row and show only the part it is over:

```kotlin
// The lens moves; the row inside it is pinned to the bar's origin so it does not.
Box(
    Modifier
        .offset { IntOffset(lensLeft, lensTop) }
        .size(lensWidth, lensHeight)
        .liquidGlass(glass, CircleShape, LensStyle, pressSource = press, refractContent = true),
) {
    Box(Modifier.layout { m, c ->
        val p = m.measure(Constraints.fixed(barWidth, barHeight))
        layout(c.maxWidth, c.maxHeight) { p.place(-lensLeft, -lensTop) }
    }) {
        TabRow(ink = accent)          // the same row the bar draws, in the selected colour
    }
}
```

Keep the two copies geometrically identical — same glyph, same stroke, same weight — or a tab
half-covered by the lens will not line up with itself.

Where the shader cannot run, the content is drawn on top as it always was; a host that relies
on the lens to colour its content should colour it directly on that path instead
(`LiquidGlassSupport.hasShaders`).

### Spill onto neighbours

Inside a `LiquidGlassContainer` the falloff is evaluated in the **container's** space, so a press
on one member lights its neighbours too — Apple's "spreads onto any Liquid Glass elements
nearby" — with no extra term and no extra cost. It falls out of the geometry.

### Reduce Motion

Apple's guidance is that Reduce Motion "decreases the intensity of some effects and disables any
elastic properties". `GlassInteraction.ReducedMotion` does exactly that: no scale, no bounce, no
gel, and the glow at half strength. The feedback survives; the elasticity does not.

```kotlin
val interaction = if (reduceMotion) GlassInteraction.ReducedMotion else GlassInteraction.Default
```

## Materialize

Apple: *"Instead of fading, Liquid Glass objects materialize in and out by gradually modulating
the light bending and lensing"*, and the UIKit guidance is to **prefer setting the effect over
setting alpha**.

So drive `materialize` rather than `alpha`:

```kotlin
val present by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = if (visible) GlassMotion.MaterializeIn else GlassMotion.MaterializeOut,
)

Modifier.liquidGlass(glass, shape, style, materialize = present)
```

One uniform scales displacement, scatter, tint, mirror, specular and inner shadow together. At 0
the shader returns the backdrop untouched, so the element is gone **without alpha being
involved** — Apple's mechanism implemented literally rather than approximated.

The visible difference: a fading element is a translucent picture of glass, and gets fainter. A
materializing one stops bending light, and the last thing to go is the edge.

## Morphing

An element changing role is not one shape growing. Apple states the material's characteristics
change *with* size — deeper shadows, more pronounced lensing, softer scattering — so a 48dp
button becoming a 280dp menu is a `Regular` becoming something nearer `Thick`, and the material
has to travel with the geometry.

```kotlin
val style = animateGlassStyle(
    start = GlassStyle.Regular,
    stop = GlassStyle.Thick,
    morphed = expanded,
)

Modifier
    .size(animatedSize)                       // same clock
    .liquidGlass(glass, shape, style)
```

Use the same spring for both, so the material cannot desynchronise from the shape it belongs to.
`GlassMotion.Morph` is a reasonable default: settles in about 420ms with no visible overshoot on
a large surface.

The observable failure, if you skip this: hold the edge parameters constant while the shape
grows and the rim stays exactly as wide as it was, which reads as a picture of glass being
stretched rather than as glass getting bigger.

`uScale` needs no help — it is derived from the measured size every frame and follows the
geometry on its own.

Decisions do not interpolate. `invertsWithBackdrop` and `fallbackSurface` snap at the midpoint,
because a half-inverted element is not a state anything wants to be in.

## Merging

`LiquidGlassContainer` renders several panels as one body of glass. Their distance fields are
combined with a smooth minimum, so panels within `mergeDistance` of each other grow a neck and
fuse, then separate as they move apart.

```kotlin
LiquidGlassContainer(glass, mergeDistance = 40.dp) {
    Box(Modifier.size(96.dp).glassMember(CircleShape))
    Box(Modifier.size(96.dp).glassMember(CircleShape))
}
```

**Nothing animates the merge.** It is a consequence of the geometry, which is why it stays
correct at any speed, in any direction, under interruption, and at any frame rate — none of
which is true of an alpha crossfade or a keyframed morph. Move the members with whatever spring
your layout uses and the neck grows and snaps on its own.

Capped at eight members: SkSL needs an unrollable loop, and every member is evaluated at every
pixel, which is exactly what lets the fields interact.

Keep `mergeDistance` at or below your layout's own spacing, or members will blend at rest.

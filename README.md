# liquidglass

A refracting glass material for Compose Multiplatform, drawn entirely in a shader. No images,
no nine-patches, no platform widgets. Drop it on any element and it becomes an optical object
sitting over whatever is behind it.

Runs on Android 13+ (AGSL) and Desktop/JVM (Skia). Below Android 13 it degrades to a tinted
surface with the same rim lighting — a plainer material, not a broken one.

**[Full documentation →](docs/README.md)** · [Getting started](docs/getting-started.md) ·
[Shapes](docs/shapes.md) · [Interaction](docs/interaction.md) ·
[API reference](docs/api-reference.md) · [Limitations](docs/limitations.md) ·
[The research](docs/research/README.md)

```kotlin
val glass = rememberLiquidGlassState(background = MyTheme.ground)

Box {
    Content(Modifier.liquidGlassSource(glass))          // what gets refracted

    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .liquidGlass(glass, CircleShape, GlassStyle.Chrome)
    ) { /* tab bar */ }
}
```

## What it actually does

Apple publishes no numbers for Liquid Glass — no blur radius, no index of refraction, no
falloff exponent. What it publishes is architecture and direction of effect. This implements
that architecture; the constants are ours, tuned against a test surface.

**The sample region is larger than the element.** The material samples "an area larger than
itself", which is what makes it lens rather than merely blur. The host records a padded slice
of backdrop and the panel occupies the inset rect, so displacement at the rim reaches real
content beyond the edge instead of clamping against it.

**The rim is sharper than the middle.** This is the most recognisable thing about the material
and the easiest to lose. The edge band shows a compressed, warped, *legible* image of what lies
just outside the element while the interior stays soft. A backdrop blurred before it reaches
the shader cannot do this — the detail the rim is supposed to bend has already been destroyed.
So the interior blur lives inside the shader, with its radius keyed to distance from the edge.

**Geometry is a signed distance field.** Distance to the edge drives refraction, so the bend is
a band hugging the rim that fades to nothing in the flat centre. The gradient of the field is
the surface normal, which gives refraction its direction and specular its angle, and stays
correct through corners where a per-edge normal pops.

That gradient is central-differenced at a *wide* epsilon, `clamp(band * 0.3, 1, 16)` px. At one
pixel the gradient magnitude collapses from 1 to 0 to 1 across the medial axis, leaving a
one-pixel seam of zero refraction down the spine of every capsule. The magnitude that falls out
of the wide difference is reused as a confidence term that fades the lens toward the axis
rather than letting its direction flip — which matters most in a container's neck, where the
whole region is medial axis.

**Dispersion.** A real bevel does not bend every wavelength equally, so the channels are
sampled fractionally apart and the rim carries a faint colour fringe. Blue carries the higher
index, so it lands furthest out. Faint on purpose — real UI glass shows almost no prismatic
rainbow, and past a few percent this reads as a broken colour channel rather than as glass.

**A mirrored edge band.** A broad, soft, upside-down echo of nearby content over roughly the
outer third of the surface, with a squared falloff. This is the detail that makes an edge read
as liquid rather than as a bevel; a thin band instead reads as a hard streak.

**Exact refraction, not a curve that looks about right.** The bevel is a superellipse height
field and the displacement is the exact Snell deviation for its slope, computed through the
tangent-difference identity so it contains no transcendental. It is self-bounding as the slope
goes vertical, which is why it needs none of the ad-hoc clamps the usual `slope * (1 - 1/n)`
shortcut requires. The index of refraction therefore shapes the falloff rather than scaling it.

**Tint is a tone mapping, not an overlay.** One colour generates a range of tones indexed by
backdrop brightness — lifting and desaturating over dark ground, darkening and saturating over
bright — the way a real pane of coloured glass does.

**Parameters are keyed to element size.** Larger glass reads more opaque with deeper shadow and
stronger lensing; smaller glass reads clearer and is allowed to invert light/dark to hold
contrast. See `elementSizeFactor`.

**Inversion is one decision per element, not per pixel.** Symbols and labels drawn *on* the
glass have to flip in lockstep with it, and a fragment shader behind them cannot reach them, so
the same scalar has to reach both. The app decides and passes it as
`rememberLiquidGlassState(inversion = ...)`; a permanently dark app leaves it at 0.

**Legibility rises with local contrast.** Apple shifts tint as text scrolls underneath. The
shader gets that signal for free: its rim sample and its interior sample already bracket the
backdrop's high frequencies, so their difference stands in for "something busy is under here".

**The edge is lit, not outlined.** A thin bright line where the edge turns into the light, and
no dark border. A dark border is the fastest way to make this read as a drawn rectangle.

**Content can sit inside the glass, not only on it.** Some content is what the glass is looking
*at* — the symbol a selection lens is crossing, whatever a magnifier is over. With
`refractContent = true` the element's content is recorded into its backdrop before the shader
runs, so the rim compresses it, the dispersion splits it and the shape clips it. This is how an
iOS tab bar changes a symbol's colour: the indicator is a lens *above* the tab, and a symbol
half under it is half one colour and half the other.

## Merging

`LiquidGlassContainer` renders several panels as one body of glass. Members do not each get
their own material: their shapes are combined into a single distance field with a smooth
minimum, so panels that come within `mergeDistance` of each other grow a neck and fuse, then
separate again as they move apart.

The merge is geometry, not an animation, so it stays correct at any speed or angle — which is
what makes it read as liquid rather than as two rectangles cross-fading.

```kotlin
LiquidGlassContainer(glass, mergeDistance = 40.dp) {
    Box(Modifier.size(96.dp).glassMember(CircleShape))
    Box(Modifier.size(96.dp).glassMember(CircleShape))
}
```

Capped at eight members, because SkSL needs unrollable loops and every member is evaluated at
every pixel — which is precisely what lets the fields interact.

## The two blurs

`GlassStyle` carries two, and they are not interchangeable.

| | `blurRadius` | `backdropBlur` |
| :--- | :--- | :--- |
| Where | inside the shader | chained before the shader |
| Rim detail | preserved | destroyed |
| Quality | slightly grainy at large radii | platform Gaussian |
| Use for | surfaces meant to show their backdrop | chrome floating over an app's own content |

`blurRadius` tapers to zero at the rim, which is what keeps the signature edge. It is a
nineteen-tap disc rotated by a per-pixel angle — too few taps to blur legible text outright, so
the pattern is rotated and the ghosts break up into noise the eye integrates as blur.

`backdropBlur` is the opt-in for chrome. Over a plain dark ground carrying sparse
high-contrast text there is almost nothing to refract, and the little there is arrives
half-legible, smeared along the rim and sitting under the labels. `GlassStyle.Chrome` trades
the signature for legibility on purpose.

## What else it does

- **Shapes.** Rounded rects, capsules and circles analytically; `GlassSquircleShape` for Apple's
  actual corner geometry; and anything else — a star, a blob, a hand-drawn `GenericShape` — by
  measuring it. See [Shapes](docs/shapes.md).
- **Touch.** Opt-in press response: scale with one overshoot, illumination from within under the
  fingertip, and a magnifier that drags the backdrop with it. Inside a container a press on one
  member lights its neighbours, because the falloff is evaluated in the container's space.
- **Materialize.** Elements arrive and leave by modulating the lensing rather than by fading,
  which is Apple's stated mechanism and their guidance over alpha.
- **Morph.** `animateGlassStyle` carries the material with the geometry, because Apple's material
  changes *with* size — a growing element that holds its edge parameters constant reads as a
  picture of glass being stretched.
- **Adaptation.** Per-pixel tone-mapped tint, free local-contrast legibility, and a per-element
  light/dark inversion with hysteresis that the app hands to its content too.
- **Accessibility.** Frost, high contrast, and reduced motion. See
  [Accessibility](docs/accessibility.md) for what Android does and does not have.
- **Scroll edge effect.** A separate sibling, as Apple ships it — it is not the material.

## Presets

- `Regular` — the workhorse. Carries controls.
- `Clear` — thinner and more transparent, for chrome that should mostly disappear. Its zeros
  are Apple's, not a tuning choice: clear "does not have adaptive behaviours, it is permanently
  more transparent", so it does not tone-map, does not react to contrast and never flips — and
  therefore *requires* the 35% dimming layer, the one number the HIG actually gives.
- `Thick` — a sheet or dialog that must hold a lot of content. Adapts but never flips polarity.
- `Chrome` — tab bars, toolbars, accessory pills over an app's own content.

## Two things the host must get right

Both were bugs here first, and both look like a shader problem when they are not.

**The recorded layer must be opaque.** A backdrop layer is transparent wherever the app painted
nothing — its ground is usually painted by an ancestor, not by the recorded subtree — and a
blur drags that transparency inward. Using the premultiplied result composites those pixels
toward black, which is a heavy dark halo around every panel; dividing the alpha out instead
blows partly-covered pixels to white. Neither is what a viewer sees. So the padded layer is
filled with `LiquidGlassState.background` before the backdrop goes into it, and the shader can
then treat any alpha shortfall as filtering error and reconstruct through it.

**The sample must be bounded by the backdrop, not by the layer.** The padded slice is
deliberately larger than the panel, and near a screen edge it hangs off the end of the
backdrop. Those pixels are not merely dark — they are the flat fill — and a panel that samples
them grows a band of solid colour at the rim. `sampleBounds` is the intersection of the
recorded backdrop with the layer.

## Cost

Measured on a Pixel 7 at 120 Hz with two glass elements over a live, continuously animating
app: 4.36% janky frames with the shader path enabled against 4.96% with it forced off — the
same within noise, on an app whose idle redraw already dominates. Adding the mirrored band,
the Schlick term and exact-Snell refraction did not move it (3.85%, p90 8 ms). The padded recording is sized
to what the shader actually reads (peak displacement plus blur radius), since every pixel of it
is re-recorded per frame.

`GlassGallery` is the tuning surface: saturated colour, hard stripes and fine text over which
the differences between the styles — and the mistakes — are obvious. A smooth gradient alone
hides almost every flaw in a lensing shader.

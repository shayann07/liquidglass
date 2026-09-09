# liquidglass

A refracting glass material for Compose Multiplatform, drawn entirely in a shader. No images,
no nine-patches, no platform widgets. Drop it on any element and it becomes an optical object
sitting over whatever is behind it.

Runs on Android 13+ (AGSL) and Desktop/JVM (Skia). Below Android 13 it degrades to a tinted
surface with the same rim lighting — a plainer material, not a broken one.

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

**Dispersion.** A real bevel does not bend every wavelength equally, so red and blue are
sampled fractionally apart from green and the rim carries a faint colour fringe.

**Tint is a tone mapping, not an overlay.** One colour generates a range of tones indexed by
backdrop brightness — lifting and desaturating over dark ground, darkening and saturating over
bright — the way a real pane of coloured glass does.

**Parameters are keyed to element size.** Larger glass reads more opaque with deeper shadow and
stronger lensing; smaller glass reads clearer and is allowed to invert light/dark to hold
contrast. See `elementSizeFactor`.

**The edge is lit, not outlined.** A thin bright line where the edge turns into the light, and
no dark border. A dark border is the fastest way to make this read as a drawn rectangle.

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

## Presets

- `Regular` — the workhorse. Carries controls.
- `Clear` — thinner and more transparent, for chrome that should mostly disappear. Ships with
  the HIG's 35% dimming layer, the one number Apple actually gives, because the clear variant
  does not adapt on its own.
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
same within noise, on an app whose idle redraw already dominates. The padded recording is sized
to what the shader actually reads (peak displacement plus blur radius), since every pixel of it
is re-recorded per frame.

`GlassGallery` is the tuning surface: saturated colour, hard stripes and fine text over which
the differences between the styles — and the mistakes — are obvious. A smooth gradient alone
hides almost every flaw in a lensing shader.

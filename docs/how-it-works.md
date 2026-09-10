# How it works

One fragment shader, in the SkSL dialect shared by AGSL (Android 13+) and Skia (Desktop), over a
backdrop the host records for it. This walks the shader in the order it runs.

The single sentence to keep in mind: **displacement first, blur second.** Apple's own framing is
that inversion — earlier materials scattered light, this one bends and concentrates it — and
almost every way an implementation looks wrong traces back to getting that ordering backwards.

## 0. Geometry — a distance field, not a mask

`d = sdShape(p)`, negative inside. `depth = max(-d, 0)` is distance inward from the rim, and
`e = depth / band` is normalised position across the refraction band: 0 at the rim, 1 at its
inner edge.

The field's **gradient is the surface normal**, which gives refraction its direction and
specular its angle, and stays correct through corners where a per-edge normal pops.

That gradient is central-differenced at a **wide epsilon**, `clamp(band * 0.3, 1, 16)` px, and
this is not an optimisation — it is a correctness fix. At one pixel the gradient magnitude of an
exact rounded-rect field collapses from 1 to 0 to 1 within two pixels across the medial axis,
leaving a one-pixel seam of zero refraction down the spine of every capsule. A wide epsilon lets
the two sides cancel gradually, and the magnitude that falls out doubles as a confidence term
(`axisFade`) that fades the lens toward the axis rather than letting its direction flip.

In a `LiquidGlassContainer` the neck between two fused members is *entirely* medial axis, so
this matters most exactly where the fusion is supposed to read.

## 1. Refraction — the exact Snell deviation

The bevel is a **superellipse height field**, and what the shader needs from it is the slope:
zero in the flat interior, diverging at the rim.

The displacement is the exact Snell deviation for that slope, computed through the
tangent-difference identity so it contains no transcendental at all:

```
sinθ  = slope/√(1+slope²)      cosθ  = 1/√(1+slope²)
sinθ' = sinθ/n                 cosθ' = √(1−sinθ'²)
bend  = tan(θ−θ') = (sinθ·cosθ' − cosθ·sinθ') / (cosθ·cosθ' + sinθ·sinθ')
```

Why not the near-universal shortcut `slope · (1 − 1/n)`? Because it overestimates by about a
fifth at this profile's own peak and **diverges at the rim**, which is why every shader that
uses it also carries an ad-hoc clamp and an ad-hoc fudge factor. The exact form is
self-bounding: as the slope goes vertical it tends to √(n²−1), so no clamp is needed and the
index of refraction becomes a knob on the *shape* of the falloff rather than on its magnitude.

The result is normalised by its own value at the rim, so `refractionDepth` is literally the peak
displacement in pixels, and faded over the inner quarter of the band so the warp field is C1
where it meets the flat interior. That last step matters: a C0 kink in a resampling field tears
the backdrop into a visible ring.

**Direction: outward.** The sample point moves *along the outward normal*, so the rim shows a
compressed image of what lies just **outside** the element. This is the point of genuine
disagreement between implementations — several published ones displace inward, magnifying the
interior instead. Apple's own statement settles it: the material "is achieved by sampling
content from an area larger than itself", and only outward sampling needs an area larger than
itself. It is also what forces the host contract below.

## 2. Dispersion

Red, green and blue sample at slightly different fractions of the same displacement, with blue
landing furthest out because it carries the higher index.

Kept faint — around 0.018 of the displacement. The one implementation calibrated side-by-side
against an iOS 26 device reports that real UI glass shows almost no prismatic rainbow, and a
larger split reads as a broken colour channel rather than as glass.

## 3. Scatter — the blur, keyed to depth

Radius `blurRadius × e`: **zero at the rim, full in the interior.**

This is the ordering claim and it is load-bearing. A backdrop blurred before the shader sees it
cannot produce the compressed legible edge band, because the detail the rim exists to bend has
already been destroyed. The rim/interior crossfade is squared, so the crisp compressed image
stays tight against the rim while the displacement itself still spans the whole band.

Implemented as nineteen taps — three rings of six plus the centre — with the whole pattern
rotated by a per-pixel hash angle. Nineteen taps cannot blur legible text on their own:
unrotated they land as discrete ghosts of it, rotated they land as noise, and noise reads as
blur.

Physically this term is not glass. A smooth plane-parallel slab does not blur. It stands in for
two real things at once — footprint integration through a curved surface, and Apple's "softer
scattering of light" on thicker glass — and it is an authored frost term, not a derived one.

## 4. The mirrored edge band

A broad, soft, upside-down echo of nearby content over roughly the outer third of the surface,
with a squared falloff.

This band's width and falloff shape is the one geometric figure in the whole model that comes
from a documented side-by-side comparison against a real device. A thin, sharply-falling band
instead reads as a hard streak rather than as liquid. It is the detail that makes an edge read
as glass rather than as a bevel.

## 5. Adaptation

Two mechanisms, deliberately not merged — see [Adaptation](adaptation.md). Tint is a per-pixel
tone mapping; light/dark inversion is one decision for the whole element, supplied by the host.

## 6. Legibility

Free: the rim sample and the interior sample already bracket the backdrop's high frequencies, so
`|luma(sharp) − luma(soft)|` stands in for "text is scrolling underneath" and raises the tint.

## 7. Lighting

An inner thickness line just inside the bevel, a key specular lobe where the normal faces the
light, a weaker counter-lobe opposite it, a Schlick rim term, and a thin bright edge line.

The counter-lobe is not decoration: a bead of glass lit from one side only reads as a gradient
rather than as a solid. And the edge is **lit, not outlined** — a dark border is the fastest way
to make a material like this read as a drawn rectangle.

## 8. Interaction and accessibility

The touch magnifier and glow, then Increase Contrast's solid-with-border treatment. See
[Interaction](interaction.md) and [Accessibility](accessibility.md).

One host-side variation belongs here too: with `refractContent` the element's own content is run
through a **second pass** that shares steps 0, 1 and 2 above — the same field, the same bevel,
the same dispersion — and nothing else, then composited over the material. It is bent like the
backdrop but not tinted, dimmed or scattered like it. See
[Content inside the glass](interaction.md#content-inside-the-glass).

A test pins the two passes together: the four functions that decide where a pixel samples from
must be identical text in both shaders, because a change to one that is not mirrored in the other
would refract the content through a different lens than its backdrop — which shows up on a device
as the symbol sliding against the content behind it as the element moves.

---

# The host contract

Two obligations, and both fail as *apparent shader bugs*. They were both bugs here first.

## The recorded layer must be opaque

A backdrop layer is transparent wherever the app painted nothing — its ground is normally
painted by an ancestor, not by the recorded subtree — and a blur drags that transparency inward.

Using the premultiplied result composites those pixels toward black: a heavy dark halo around
every panel. Dividing the alpha out instead blows partly-covered pixels to white. **Neither is
what a viewer sees.** What a viewer sees is the backdrop over the ground behind it.

So the padded layer is filled with `LiquidGlassState.background` *before* the backdrop is drawn
into it. Once it is opaque by construction, the shader can treat any alpha shortfall as
filtering error at the layer's own boundary and reconstruct through it.

## Samples must be bounded by the backdrop, not by the layer

The padded slice is deliberately larger than the panel — that is what gives the rim something to
reach — and near a screen edge that margin hangs off the end of the recorded backdrop. Those
pixels are not merely dark; they are the flat fill. A panel that samples them grows a band of
solid colour at the rim.

`sampleBounds` is the intersection of the recorded backdrop with the layer, inset half a pixel
so bilinear filtering stays off both boundaries. It is also guaranteed never to invert — an
inverted rect becomes a `clamp()` with min greater than max, which is undefined and draws a
solid block of nothing.

## Padding

`refractionDepth × 1.4 + max(blurRadius, backdropBlur)` px on every side. The 1.4 covers the
1.25× size gain plus dispersion reach. Every pixel of it is re-recorded per frame, so it is
sized to what the shader actually reads rather than rounded up for comfort.

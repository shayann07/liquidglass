# Shapes

The material's whole optical model runs off a signed distance field: distance from the rim
drives the refraction band, and the field's gradient is the surface normal. So the question
"what shapes are supported" is really "what shapes can we get a distance field for", and there
are two answers.

## Shapes with a closed form

Evaluated directly in the shader, exactly, at no extra cost.

```kotlin
Modifier.liquidGlass(glass, RoundedCornerShape(28.dp), GlassStyle.Regular)
Modifier.liquidGlass(glass, CircleShape, GlassStyle.Clear)          // capsule or circle
Modifier.liquidGlass(glass, RectangleShape, GlassStyle.Thick)
```

`CircleShape` needs no special handling — it is `RoundedCornerShape(50%)`, so a circle and a
capsule are both just rounded rectangles whose radius happens to reach the short edge.

## Squircles

Apple's shapes are squircles. `RoundedCornerShape` draws a **circular arc**, which meets the
straight edge with a visible change in curvature — the corner "starts" somewhere. A superellipse
corner blends into the edge continuously.

The difference is small, constant, and exactly where the eye checks.

```kotlin
val shape = GlassSquircleShape(cornerRadius = 28.dp, power = 4f)

Box(
    Modifier
        .clip(shape)                                    // the clip
        .liquidGlass(glass, shape, GlassStyle.Regular)  // and the material agree
)
```

Passing the shape to `liquidGlass` is enough — the material reads the exponent off it, so the
distance field and the clip describe the same outline. `power = 2` is identical to
`RoundedCornerShape`; `power = 4` is Apple's squircle; higher goes toward a rectangle with
rounded nubs.

`GlassSquircleShape` is an ordinary `Shape`, so it works for `clip`, `background`, `border` and
anything else that takes one.

> There is also `GlassStyle.cornerPower`, which sets the material's corner exponent
> independently. It exists for the case where you cannot change the clip. Prefer the shape:
> if the two disagree, the corner reads slightly fuller than the outline it is clipped to.

## Arbitrary paths

Anything else — a star, a blob, a hand-drawn `GenericShape` — has no closed-form distance field.
The library measures it rather than refusing it.

```kotlin
Modifier.liquidGlass(glass, MyStarShape, GlassStyle.Regular)
```

Nothing else is required; the shape being a path is detected and handled. What happens under it:

1. The outline is rasterised into the padded layer, once.
2. An exact Euclidean distance transform runs over the result.
3. The field is encoded into a texture and bound as a second shader input.
4. The shader reads the measurement instead of evaluating a formula.

It is rebuilt only when the **shape or the size** changes — never per frame, never on scroll,
never on press.

### What it costs, and what it costs you

The transform runs on the calling thread when the shape or size changes. For a phone-width
panel that is single-digit milliseconds; if you animate a path shape's *size* continuously it
will rebuild every frame and you will feel it. Animate the shape's contents, or use a
closed-form shape, or accept the rebuild if the resize is a one-off.

Two encoding choices are worth knowing about, because they bound the quality:

- The field is computed at **half resolution**. A distance field is smooth almost everywhere and
  bilinear sampling puts back more than the halving takes out.
- It is stored in **eight bits** over about one and a half refraction bands, which quantises
  distance to roughly a pixel. That is fine for the two questions the shader asks — how deep
  into the band a pixel is, and which way the surface faces — because it answers the second by
  differencing over a wide epsilon where a pixel of noise is nothing.

Sharp concave features (the inner points of a star) are the place to look if you want to see the
limit. They are correct, but a pixel of quantisation is proportionally more visible there than
on a long straight edge.

## What is not supported

**Stroked or open paths.** The field is signed, which requires an inside. Close your path.

**Shapes that change every frame.** See above — the rebuild is not free. A morph between two
closed-form shapes is much cheaper; see [Interaction](interaction.md#morphing).

# liquidglass

A liquid-glass material for Compose Multiplatform. Android and Desktop, no dependency on any
particular app.

```kotlin
val glass = rememberLiquidGlassState()

Box {
    // Whatever the glass should refract.
    Column(Modifier.liquidGlassSource(glass)) { /* scrolling content */ }

    // A floating panel made of glass.
    Row(
        Modifier
            .clip(shape)
            .liquidGlass(glass, shape, GlassStyle.Regular)
    ) { /* controls */ }
}
```

## How it works

The material is derived from a signed distance field of the panel's own shape. Everything
optical falls out of that one field:

- **Distance to the edge** drives refraction, so the bend is a band that hugs the rim and
  fades to nothing in the middle of the panel — what a bevel actually does. The falloff is
  cubic; a linear ramp reads as a gradient, not as glass.
- **The gradient of the field** is the surface normal. It gives refraction its direction and
  specular its angle, and it stays correct through the corners, where a per-edge normal pops.
- **Tint is adaptive.** It samples the backdrop's luminance and leans darker over bright
  content, which is what keeps a label on the glass legible while content scrolls beneath it.
- A dark inner line reads as thickness, and a weaker counter-highlight on the opposite rim
  stops the panel looking lit from one side only.

The backdrop is blurred *before* it is refracted. The other order aliases badly: near the rim,
neighbouring output pixels reach for widely separated input pixels.

## Merging

`LiquidGlassContainer` renders several panels as one body of glass. Their fields are combined
with a smooth minimum rather than a plain one, so panels within `mergeDistance` grow a neck
and fuse, then separate as they move apart. The merge is geometry, not an animation, so it
stays correct at any speed.

```kotlin
LiquidGlassContainer(glass, mergeDistance = 24.dp) {
    Box(Modifier.glassMember(CircleShape))
    Box(Modifier.glassMember(RoundedCornerShape(28.dp)))
}
```

## Platform support

| | refraction, specular, adaptive tint | backdrop blur |
| :--- | :--- | :--- |
| Android 13+ (API 33) | yes, via AGSL `RuntimeShader` | yes |
| Android 12 (API 31–32) | no | yes |
| Android 11 and below | no | no |
| Desktop (Skia) | yes, via `RuntimeEffect` | yes |

Both platforms run the same SkSL source. Query [LiquidGlassSupport] to branch a design.

Below API 31 there is no backdrop to sample at all, so the material paints only the parts that
do not need one — tint, lit rim, inner line. Pass `GlassStyle.fallbackSurface` with a colour
that suits your ground: a 10% tint over live content is unreadable with no blur to soften it.

## Caveats

- A panel in its own window — a `Dialog`, `DropdownMenu` or `ModalBottomSheet` — cannot sample
  the backdrop of the window behind it. Glass applies to in-window chrome.
- `MAX_GLASS_MEMBERS` caps a container at 8 panels, because SkSL loops must be unrollable and
  every member is evaluated per pixel.
- The shader is compile-tested (`GlassShaderTest`) on the Skia path, which catches syntax and
  uniform-contract errors without a device.

# Generic interactive glass

Astra puts press and pull feedback in the shared `liquidGlass` modifier. Vitals supplies
layout, content and click actions; it has no copied glass shader or Live-specific deformation.
Use the same `GlassInteraction.Pullable` for a settings button, status pill or custom control.
It is opt-in: `Default` retains its Legacy press behavior, and `ReducedMotion` disables elasticity.

## One backdrop, many controls

```kotlin
@Composable
fun GlassActions(onSettings: () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    val glass = rememberLiquidGlassState(background = background)
    Box {
        AppContent(Modifier.liquidGlassSource(glass))
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(56.dp)
                .liquidGlass(
                    state = glass,
                    shape = CircleShape,
                    style = GlassStyle.DarkChrome,
                    interaction = GlassInteraction.Pullable,
                )
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}
```

`AppContent` stands for your own composable. The example uses Compose foundation layout/clickable,
Material icons and the `com.wexpa.liquidglass` API. Record the backdrop separately from the controls;
recording glass into its own source creates feedback. Reuse the state for other panels over that backdrop.
For a wide pill, change the layout and shape to `RoundedCornerShape(22.dp)`; the interaction is identical.
When the app or system requests reduced motion, pass `GlassInteraction.ReducedMotion`.

## What stays anchored

The layout centre, normal foreground labels/icons and pointer coordinate frame stay fixed.
Press expansion and pull stretch affect material drawing only. The draw transform is restored
before ordinary foreground is drawn. Refracted content is an explicit exception:
`refractContent = true` sends content through the optical path because it is viewed through the lens.

Pull targets meet smooth resistance before entering their springs, so extreme pointer travel
does not accumulate a hidden return delay. Stretch and squeeze use the support widths of both axes.
A circle can stretch diagonally. As the aspect ratio approaches 2:1, shear fades to zero;
a long capsule stays level. This aspect policy and viewport containment are authored corrections
to owner-reported failures, not recovered Apple constants. Viewport constraints limit initially
contained controls without imposing placement rules on partially visible scrolling content.

The material may grow outside its resting bounds. Avoid a clipping ancestor. If you add a
Compose shadow, use `shadow(elevation, shape, clip = false)`. The layout size and click target
remain the size you supplied; provide at least 48dp for an interactive control.

## Separate touch expansion from drag stretch

R14 leaves touch expansion, illumination, spring timing and small-control drag unchanged.
The shared modifier now adapts only **drag strain** to the control's unpressed layout size:

- Up to 80dp on the longest side: the existing small-control response is preserved exactly.
- Between 80dp and 160dp: a smooth transition avoids a sudden change as a control resizes.
- From 160dp: initial drag strain is one fifth of the former response and smoothly saturates
  at 2dp of total extension (about 1dp per opposite edge). Compression is reduced with it.

The size thresholds, gain and cap are authored from owner feedback about large surfaces;
the original small-control captures do not measure a large-card response. Press expansion
is a separate channel and does not pass through this limiter. Cards and wide status pills
inherit this policy automatically through `Pullable`; no app-specific preset is needed.
R14 is verified with terminal-driven desktop Compose rendering. Physical Android verification
of this revision is pending because no device was available.

## Gesture ownership and navigation

The modifier observes pointer input without taking over the control's click action. A parent
scroll taking the gesture cancels the pull; release and cancellation return it to its resting shape.
`pullFollow` is retained as the input-compliance gain, not permission to translate the whole control.
`pullLimit` bounds that compliance budget; `pullElongation` and `pullWidthRatio` tune material strain.
Prefer the preset before overriding these authored input gains.

For navigation use `GlassTabBar(..., style = GlassTabBarStyle.V3())`. The bar and tab anchors
stay fixed while its selected lens follows selection intent. V3 owns the continuous pose,
selected ink, endpoint accommodation and cancellation, so applying a second pull transform
to that lens would duplicate motion. It shares bounded-travel primitives with generic controls,
but its pose renderer and generic material draw transform are still distinct implementations.

A caller-provided `GlassPressSource` drives illumination from a parent gesture. It currently
has no generic pull-displacement channel; do not claim it provides arbitrary parent-driven
pull automatically. Normal self-interacting controls need no external press source.

## Build and verification

R14 is `0.2.0-astra.14-SNAPSHOT`, built locally, not a Maven Central release. Publish this
library with `./gradlew publishToMavenLocal`; consume the same version from Maven Local.
The isolated owner workspace uses its private Gradle/Maven wrapper for both operations.

See [implementation status](implementation-status.md) for test and physical-review results.
Full iOS parity remains unproven: rim chroma, exact input timing and the strict endpoint
optical gate remain open. Android below API 33 uses the material fallback instead of AGSL.

# Generic interactive glass

Press and pull feedback lives in the shared `liquidGlass` modifier. Your application supplies
layout, content and click actions; no per-component shader or gesture copy is needed.
For new integrations use `GlassInteraction.Calm` or the scoped `GlassScene` API. `Pullable`
preserves the older, more expressive response for comparisons.
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
                    interaction = GlassInteraction.Calm,
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

The size policy introduced in R14 adapts **drag strain** to the control's unpressed layout size.
It applies to `Pullable` and `Calm`; each preset keeps its own independent press response:

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

For navigation use `GlassTabBar(..., style = GlassTabBarStyle.Calm())`. The bar and tab anchors
stay fixed while its selected lens follows selection intent. V3 owns the continuous pose,
selected ink, endpoint accommodation and cancellation, so applying a second pull transform
to that lens would duplicate motion. It shares bounded-travel primitives with generic controls,
but its pose renderer and generic material draw transform are still distinct implementations.

At the end tabs, selector deformation uses bounded travel rather than raw pointer speed. Continuing
to pull outside an already-reached anchor therefore cannot stretch a stationary selector. Whole-bar
strain still follows the separate anchored material response; returning inside the tab range restores
travel deformation. Selection intent uses the original pointer position, independently of that bound.

A caller-provided `GlassPressSource` drives feedback from a parent gesture, including cumulative
pull displacement through `press(point, pullOffset)`. Normal self-interacting controls need no
external press source. The source does not arbitrate pointer ownership for you.

## Build and verification

The new APIs are unreleased. Publish this source with `./gradlew publishToMavenLocal`; consume the
same `VERSION_NAME` from `gradle.properties` through Maven Local. R14's archived artifact predates
the calm preset, continuous lens and parent-driven pull API.
The isolated owner workspace uses its private Gradle/Maven wrapper for both operations.

See [implementation status](implementation-status.md) for test and physical-review results.
Full iOS parity remains unproven: rim chroma and exact input timing remain open. The strict
endpoint composition gate passes for separate-input Desktop Calm and CanvasKit; Android and legacy
retain the earlier rounding limit. See the [compositor contract](porting.md#combining-refracted-ink-with-glass).
Android below API 33 uses the material fallback instead of AGSL.


## Separate press, pull and navigation

Generic Calm press growth is limited to 3% scale and 2dp per edge; drag has a separate resisted target and
size attenuation. Critical geometry springs avoid the historical press overshoot. These rates are
authored responses, not timings recovered from untimed Apple screenshots.

`GlassTabBarStyle.Calm()` uses the same feedback engine with a **5% whole-bar press** and no per-edge
press cap, matching the original 186→196px ordinary hold within one pixel. This is a navigation-role
preset, not an increase to generic card feedback or drag sensitivity. Calm timing, resistance, the
large-surface drag cap and viewport containment still apply; ordinary labels remain anchored.
The bar also owns its existing held brightness, so its interaction sets `pressLift = 0` to avoid
adding the generic control lift a second time. Touch illumination remains active.
The selector's shape responds to along-bar travel; perpendicular swipes do not apply the
free-button squeeze model to it. Navigation intent still follows the finger and commits once on release.

If your component already owns a drag, send local coordinates and cumulative displacement to
`GlassPressSource.press(point, pullOffset)` and pass that source to `liquidGlass`. Always release on
up, cancellation, disposal or gesture handoff. The source only supplies input; the shared modifier
still owns resistance, springs, size attenuation and viewport containment.

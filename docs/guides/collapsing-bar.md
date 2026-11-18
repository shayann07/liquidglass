# The scroll edge effect and the collapsing bar

Two things happen at the top of an iOS 27 screen when content scrolls under the chrome: the content is blurred
(and, in a dark appearance, dimmed) where it passes under the bar, and the bar itself may collapse. The first is
a modifier, [`Modifier.glassScrollEdge`](../api-reference.md#glassscrolledge); the second is a recipe, because the
collapse is the app's own layout, not an effect.

Provenance, in every table below: **measured** is read from `Scr-safari-scroll-{dark,light}` (iOS 27 Safari, an
iPhone, 60 fps, HDR10 decoded); **fitted** is our parameterisation of a measurement; **authored** is not visible in
any recording we have.

## The scroll edge effect

Put the modifier on the scrolling container. It draws over the content and under the sibling chrome, and it never
consumes scroll:

```kotlin
val state = rememberLazyListState()
LazyColumn(
    modifier = Modifier.glassScrollEdge(state, ground = MyTheme.ground, inverted = isDark),
    state = state,
) { /* items */ }
```

With a `ScrollState` the call is the same. With Material 3, drive it from the app bar's own signal, so the effect and
Material's collapse share one number:

```kotlin
val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
LazyColumn(
    Modifier
        .nestedScroll(behavior.nestedScrollConnection)
        .glassScrollEdge(behavior, ground = MyTheme.ground, inverted = isDark),
) { /* items */ }
```

| | value | label |
| :-- | :-- | :-- |
| Blur | on in both appearances; edges stay smeared across the band and are sharp again 35 dp from the edge | measured: edges 4.4 px wide 22 dp in, 3.0 at 28, 1.5 at 32, 0.4 at 35 (3x pixels, rms over +-10 rows) |
| Blur masks | two blurred copies (3.3 dp and 1 dp) over the sharp content | fitted to those widths (within 1 px); above 20 dp the widths are only lower bounds |
| Dark tint | `0.41 (1 - smoothstep(10 dp, 34 dp, d))` toward grey 124, the same for every hue | measured, 0.8 level rms against the measured profile (the gate was 5) |
| Light tint | none | measured (1 to 3 levels, no consistent sign) |
| Fade-in with scroll | 24 dp of scroll, from 0 at rest | authored |
| Bottom edge | a mirror of the top | authored: the take's bottom edge sits under the glass toolbar |

Notes:

- `inverted = true` is the dark appearance: the tint. In a light appearance the effect is blur only. Drive it from the
  same decision that picks the glass's appearance.
- The blur needs a render effect: Android 12 and later, desktop and web. Before Android 12 only the tint draws.
- While the modifier's intensity is zero (at rest, at the end of a list) it draws exactly as if it were absent.
- `ground` is used by `GlassScrollEdgeStyle.Hard` only (a uniform band, the older composable's look). The measured
  effect does not fade toward the app's ground.
- A taller bar: iOS measures from the bar's own edge, which was not measured here. The `height` parameter scales the
  measured profile, so a taller bar can stretch it; that stretching is authored.

[`GlassScrollEdge`](../adaptation.md#the-scroll-edge-effect), the composable, stays for a chrome that does not sit on a
scrolling container. It is always drawn and its numbers are authored.

Before and after renders, with their captions and limits:
[`review/atlas/scroll-edge-2026-11-18`](https://github.com/shayann07/liquidglass/blob/main/review/atlas/scroll-edge-2026-11-18/README.md).

## A large title that collapses

Material already does this, scroll-driven, so there is nothing to measure and no new API: use
`LargeTopAppBar(scrollBehavior = exitUntilCollapsed)` with the modifier above. The recording has no large title;
the collapse is Material's.

## Safari's bar collapsing to a pill

In the take, Safari's bottom bar (back, address capsule, tabs) collapses to a compact address capsule while the page
scrolls, and expands again. What the take shows (measured, to within three frames at 60 fps):

| | value | label |
| :-- | :-- | :-- |
| Duration | 0.2 s, 0.15 to 0.25 s | measured, +-3 frames |
| Shape | the capsule narrows from about 208 pt to 122 pt and drops a few points; the buttons fade first | measured |
| Trigger (direction, distance) | not extracted: the scroll offset code was not decoded | authored below |
| Easing | not extracted | authored below |

A recipe that follows it: collapse when the user scrolls down, expand when they scroll up, one fraction animating
over 200 ms.

```kotlin
@Composable
fun rememberBarCollapse(): Pair<State<Float>, NestedScrollConnection> {
    var collapsed by remember { mutableStateOf(false) }
    // One event's delta is small: movement is summed in the current direction, and the sum is reset when it turns.
    var travel by remember { mutableFloatStateOf(0f) }
    val fraction = animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),  // 200 ms measured, easing authored
        label = "barCollapse",
    )
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy == 0f) return Offset.Zero
                travel = if (travel == 0f || (dy < 0f) == (travel < 0f)) travel + dy else dy
                if (travel < -8f) { collapsed = true; travel = 0f }    // finger moving up: content scrolling down (distance authored)
                if (travel > 8f) { collapsed = false; travel = 0f }
                return Offset.Zero
            }
        }
    }
    return fraction to connection
}
```

Read `fraction.value` inside `Modifier.layout` or `graphicsLayer` (so scrolling does not recompose), and lerp the
capsule's width, the buttons' alpha and the bar's offset with it. For the glass itself to morph rather than cross-fade,
draw the compact and full capsules as one `glassEffectID` in a `GlassEffectContainer`
(see [Interaction](../interaction.md)).

The trigger distance, the easing and the direction are authored until the offset code of the take is decoded. The
timing is not asserted by a test: the recording fixes it only to +-3 frames, which is wider than one frame.

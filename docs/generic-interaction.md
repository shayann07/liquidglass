# Generic interactive glass

Press and pull feedback lives in the shared glass modifier: `Modifier.glassEffect` with an
`interactive()` `Glass`. Your application supplies layout, content and click actions; no
per-component shader or gesture copy is needed.
`interactive()` uses `GlassInteraction.Fluid` by default (#196): the Phone search button's press and pull, measured on
iOS 26 (IMG_6756, seven presses; keypad K01 for the press alone). The body balloons 8 dp a side, follows the finger
and stretches toward it, holds its far edge on landing and rings on release, and its glow settles to 0.65 once
dragged. It falls with size (authored: no recording shows other glass pulled): controls up to 80 dp answer in full,
80 to 160 dp blend smoothly toward the anchored surface response, and surfaces of 160 dp and more press 2 dp a side,
do not leave their place and stretch at most 2 dp, as `Calm` does. A scroll that takes the gesture returns the press
and the pull critically damped, with no ring. Interactive glass follows the system's Reduce Motion
(`ReducedMotion`). Pass `GlassInteraction.Calm` for the restrained response `interactive()` used before 1.1.0.
`Pullable` preserves the older, more expressive response for comparisons.
It is opt-in: `Default` retains its Legacy press behavior, and `ReducedMotion` disables elasticity.

## One theme, many controls

```kotlin
// Inside the app's LiquidGlassTheme { … }
@Composable
fun GlassActions(onSettings: () -> Unit) {
    Box {
        AppContent()
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(56.dp)
                .glassEffect(Glass(GlassStyle.DarkChrome).interactive(), CircleShape)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}
```

`AppContent` stands for your own composable. The example uses Compose foundation layout/clickable,
Material icons and the `com.wexpa.liquidglass` API. The theme records the screen without the
glass, so no glass samples itself; every other glass element under the same theme refracts the
same recording. For a wide pill, change the layout and shape to `RoundedCornerShape(22.dp)`; the
interaction is identical. When the app or system requests reduced motion, pass
`interactive(response = GlassInteraction.ReducedMotion)`.

## What stays anchored

For generic controls/cards, the layout centre, normal foreground labels/icons and pointer coordinate frame stay fixed.
Press expansion and pull stretch affect material drawing only. The draw transform is restored
before ordinary foreground is drawn. Refracted content is an explicit exception: the tab bar's
lens sends its content through the optical path because it is viewed through the lens (the
`refractContent` option, internal since 18 October).

`GlassTabBarStyle.Calm()` is a navigation-specific exception for visible ink. Its
`deformItemsWithBar = true` draws both ordinary and selected items with the same bounded bar
response, around the bar's centre. Layout, accessibility bounds and pointer coordinates still stay
fixed. A same-gesture iOS audit shows that leaving visible glyphs fixed misses their vertical
position by up to13.5px. One shared animated result drives the material and both ink variants;
there are no duplicate springs or widget translations. Set `deformItemsWithBar = false` to retain
fixed item drawing. Older presets default to false. This does not change generic card text. Since
18 October `Calm()` also sets `itemsFollowBarPress = false`: the items follow only while the lens is
held, because iOS's icons hold still on a tap (within 0.1 px) and follow the bar on a hold.

`GlassPullShape.Free` is the exception for a free-standing button, such as iOS 26's Phone search
button: the body leaves its place with the finger (`pullFollow`, resisted by `pullLimit`), stretches
along the pull and thins across it, and its content travels with it. After the lift it returns
through `GlassMotion.FreeRelease` and its edge away from the pull holds at its resting position, so
the overshoot lands as a squash against that edge (the width bulges 0.2 as much), then rebounds.
`pullLiftShare` dims the press glow once the finger moves. Layout, hit targets and pointer
coordinates still stay fixed; the backdrop is read at the button's layout position. Measured on
IMG_6756 in display colours (seven presses: centre 2-3 px rms, size 3-5 px, brightness 8 levels).

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

`GlassTabBarStyle.Calm()` now selects `GlassPullShape.AreaPreserving` for its surrounding material.
This fits the original bar's coupled narrowing and height increase: the authored saturated response is
835.5×204.7px for a 834×186px bar after its separate 5% press. A bounded material-only bias now
places its top/bottom at 170.73/375.43px against the selected original's 171–172/375px edges.
The bias is at most 5.6% of the unpressed short side (10.42px at this geometry), below the selected
10.5px displacement. No translation is applied to layout, hit targets or ordinary labels.
This matches chosen visible edges, not Apple's internal mechanism or a complete motion trajectory.
Using the same law in other directions is an authored extrapolation. Ordinary press has no bias.
Since 18 November a long horizontal bar answers only the vertical part of a pull, resistance included: a
sideways drag leaves its shape alone, as iOS 27's held Phone bar does, and a diagonal lifts it as far as the
same pull straight up. A round surface keeps the whole pull. Pixel 7 frames before and after:
[tab-bar-stretch-2026-11-18](https://github.com/shayann07/liquidglass/blob/main/review/atlas/tab-bar-stretch-2026-11-18/README.md).

Generic `Calm` and `Pullable` keep `GlassPullShape.Adaptive`, including the large-card 2dp extension
cap. That policy uses `pullElongation` and `pullWidthRatio`. `AreaPreserving` instead uses a bounded
0.047 log-strain, at most 4.8% additional axis stretch before viewport constraints. `pullFollow` and
`pullLimit` still set the input resistance; use a finite positive `pullLimit` for this policy.
Shear fades in log space as surfaces get wider, keeping a long bar level and preserving material
area away from viewport limits. A constrained viewport may reduce that transform further.
Both policies share the same modifier, springs, gesture ownership and reduced-motion handling.
Use the navigation preset directly; there is no second drag handler to attach to the selector.

A caller-provided `GlassPressSource` drives feedback from a parent gesture, including cumulative
pull displacement through `press(point, pullOffset)`. Normal self-interacting controls need no
external press source. The source does not arbitrate pointer ownership for you.

## Build and verification

These APIs are in 0.2.0. To try `main`, publish it with `./gradlew publishToMavenLocal` and consume
the `VERSION_NAME` from `gradle.properties` through Maven Local. R14's archived artifact predates
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
viewport containment still apply; the large-surface drag cap belongs to generic Adaptive surfaces.
Ordinary labels remain anchored.
The bar also owns its existing held brightness, so its interaction sets `pressLift = 0` to avoid
adding the generic control lift a second time. Touch illumination remains active.
The selector's shape responds to along-bar travel; perpendicular swipes do not apply the
free-button squeeze model to it. Navigation intent still follows the finger and commits once on release.

A component that already owns a drag sends local coordinates and cumulative displacement to
`GlassPressSource.press(point, pullOffset)`, as `GlassTabBar` does for its lens. The parameter that
hands the source to the glass is internal since 18 October, so this path is not public at present.
Always release on up, cancellation, disposal or gesture handoff. The source only supplies input; the shared modifier
still owns resistance, springs, size attenuation and viewport containment.

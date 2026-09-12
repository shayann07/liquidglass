# Tab bar

`GlassTabBar` is the component the material was measured against, and the one place in the
library where every default is a measurement rather than a choice. If you want the iOS 26 tab
bar, use it as it is; if you want something else, read this first so you know what you are
changing.

```kotlin
val glass = rememberLiquidGlassState(background = MyTheme.ground)

Box {
    Content(Modifier.liquidGlassSource(glass))

    GlassTabBar(
        state = glass,
        itemCount = tabs.size,
        selectedIndex = selected,
        onSelected = { selected = it },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 14.dp)
            .shadow(15.dp, RoundedCornerShape(percent = 50), clip = false),
    ) { index, isSelected ->
        TabLabel(tabs[index], color = if (isSelected) accent else resting)
    }
}
```

Two rules for the `item` slot, both because the row is drawn twice — once on the bar, once
inside the lens — and the two copies must line up to the pixel where the lens crosses them:
change **only colour** between `selected = false` and `selected = true`, and do not put your own
click handling on it (the bar owns the gesture and calls `onSelected`).

Put the shadow on the bar's `modifier` with `clip = false`. The held lens is taller than the bar.

## What happens under a finger

The bar grows 1.05 about its centre and its material lifts 16/255; the tab under the lens grows
1.18 about its own centre; the resting indicator swells into a lens 5 pt proud of the bar and
21 pt wider. The lens stands out of the bar rather than pushing it aside: the bar keeps its own outline
and the lens rises through it, above and below. The reference also fillets the two together
where they cross, and `lensFuse` will do that, but it is off by default - at the measured 16 dp
it reads as the bar swelling to make room rather than as the lens emerging, which is the wrong
impression. It takes about four tenths of a second to subside once the finger leaves, which is what the
reference takes; at the library's old spring it went in under a tenth and the press did not
register as having happened. And the lens is a gel — it flattens by about 5% at 300 dp a second and rounds back up when it stops (`gel`,
`gelReference`).

## What it does, and where each part comes from

Everything below was read off native screenshots of an iOS 26 tab bar at 3px/pt, cross-checked
against a screen recording, and is written up with the numbers in
[reference measurements](research/reference-measurements.md).

### At rest the indicator is not glass

Its body is a flat 6 where the bar around it reads 20; its edge steps between them in two or
three pixels; it has no refraction band, no mirrored echo, no colour fringe and no lit bevel.
`GlassTabBarStyle.RestingInset` is exactly that: a dark tint over the bar with every optical term
at zero.

This is the finding that most separates the real thing from recreations. A resting indicator
with even a shallow bend compresses the bar's own body into a bright ring at its rim, and that
ring is the tell. Apple's rule for slider knobs — the knob "becomes Liquid Glass for the duration
of the gesture and reverts after" — turns out to be the rule for this too.

### Touch-down makes it a lens

Within two frames of a finger landing, the inset swells into `GlassTabBarStyle.HeldLens`: a
capsule 4pt taller than the bar above **and** below (`lensOverflow`), 16pt wider than one item
(`lensExtraWidth`), centred on the bar (`lensRise = 0`). Size and material travel on one spring
(`form`, about 110ms) so the rim cannot arrive after the shape, and subside on another
(`subside`, about 330ms).

It does not scale-bounce. `lensInteraction` has `pressScale = 1`; the reference's bar and lens
both keep their size under the finger.

### The lens looks through the bar

On iOS the lens sits above the bar, so what reaches the eye has already been through the bar's
frost. The component records the bar — plate and items — with `liquidGlassSource` into a second
state and hands it to the lens as `through`. Three measured facts follow from that and only that:

- the lens's interior reads as the bar does (52 against a bar of 47 beside it, the difference a
  faint centre glow), not as the un-frosted content behind;
- the bar's own hairline appears 11px in from where it is, pulled inward by the rim's outward
  refraction, and the dark gap around the bar becomes a dark band inside the lens's edge;
- the content behind the bar is no more visible through the lens than through the bar.

Without `through` the lens samples the app's content directly and punches a clear hole through
the bar, which is the one thing the reference never does.

### The tabs change colour because you look at them through it

The item row is drawn twice: in its resting state on the bar, and in its selected state inside
the lens as [refracted content](interaction.md#content-inside-the-glass). The lens shows only the
part it is over, so a tab half under it is half one colour and half the other, and its edges
fringe red and blue where the rim crosses them — `HeldLens.dispersion` is far past the other
presets on purpose.

### The lens is the thing you drag

Touch-down sends it to the tab under the finger (`arrive`). Past the touch slop it follows the
finger exactly, keeping whatever offset it had from the fingertip up to half its own width, so
grabbing the lens tracks and grabbing beside it brings it to you. It previews the tab it is over
and commits once, on release (`settle`); a release faster than `flingVelocity` carries it to the
next tab. At speed it stretches along the travel by up to `gel`, lagged by `gelSpring`, so the
deformation trails the movement.

## The 2026 material

`GlassTabBarStyle.Ios27` is `Dark` with a darker contour and a brighter highlight. Apple's 2026
revision rebuilt the material around readability: it diffuses busy content behind it more
effectively, and it gains a darkened edge and brighter speculars so an element stays distinct
over that content.

**Neither number in that preset is measured.** Everything in `Dark` is read off native
screenshots; `Ios27` is a starting point, because the captures this library was tuned against
predate the revision. The same release also replaces the earlier two-position transparency
toggle with a continuous slider whose default is its midpoint, so there is no longer a single
correct opacity to match and `tint` is the host's call.

## Where the shader is unavailable

Below Android 13 the lens is a plain surface (`fallbackSurface`), the items colour themselves —
`item` is called once per tab with the real selection — and everything about the gesture still
works.

## Reduce Motion

`motionEnabled = false` disables the lens, the gel and the fling. Taps and drags still select,
and the indicator jumps to the selection without animating.

## The measured bar

`GlassTabBarStyle.Measured(dark, tintAmount)` puts the bar on the measured material: the
Photos toolbar is the same role and was measured through the calibration target at every Tint
Amount in both appearances, so the bar is `GlassStyle.toolbar` — the pill's kernels with the
toolbar's own tint, white at 0.55-0.67 opacity in light and black at 0.41-0.63 over the 35/255
lift in dark, which is more opaque than the navigation pill at Clear and responds less to the
slider (the table is in the measured model, section 2).

The indicator and the lens were measured on 2026-09-12 from the iOS 27 screen recording of the
Phone and App Store tab bars being dragged (measured model, section 2c), and `Measured` carries
those numbers; `Dark` and `Ios27` keep this page's iOS 26 ones. What the recording shows, and
what the preset does about it:

- **The whole bar grows and lifts while a finger is down** — 1.05 about its centre and +16/255
  — and the tab under the lens grows 1.18 more about its own centre (`heldScale`, `heldLift`,
  `selectedScale`). The bar's plate is laid out at the grown size so its backdrop stays 1:1; the
  items scale, in both copies of the row, so they stay on top of each other.
- **The resting inset** is 4pt inside the bar (`pillInset`) and wider than a slot on a five-tab
  bar: iOS sizes it to the label plus about 57pt, 84pt on the App Store's bar, which is
  `pillWidth`'s default (the wider of it and the slot is used). It is the bar's output at 0.857
  minus 17/255, with no optics (`RestingInsetMeasured`, `GlassProfile.Held` at 0).
- **The held lens** stands 5pt proud of the bar (`lensOverflow`) and 21pt wider than the inset
  (`lensExtraWidth`). Its interior shows the bar as it is plus 0.11 of the raw content behind the
  bar (`rawShare`) and a 1% white. Its outer band, half of 0.6 of the corner radius, pulls the
  exterior in and compresses it 1.3x — the bar's own edge line lands a tenth of the radius inside
  the lens's rim — and a seam hides what lies between the band and the interior
  (`HeldLensMeasured`, `heldLens` = 1). A 3-4px edge line of +68 over black at the top and
  bottom with a glow trailing inward, a dark two-pixel step at the sides, colour fringes along
  the band.
- **What is not done**: the reference's lens carries its content a frame late while it moves;
  the library's copies of the row coincide when held still and lag by a frame mid-drag in the
  same way, which is left as it is. The light appearance of the inset and lens was not captured;
  `Measured(dark = false)` uses the dark numbers.

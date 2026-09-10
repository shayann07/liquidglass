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

## Where the shader is unavailable

Below Android 13 the lens is a plain surface (`fallbackSurface`), the items colour themselves —
`item` is called once per tab with the real selection — and everything about the gesture still
works.

## Reduce Motion

`motionEnabled = false` disables the lens, the gel and the fling. Taps and drags still select,
and the indicator jumps to the selection without animating.

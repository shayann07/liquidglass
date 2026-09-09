# Adaptation

Four separate mechanisms let the material respond to what is behind it. They are deliberately
not one knob, because they operate at different granularities and two of them are the host's job.

## Tint is a tone mapping, not an overlay

Per-pixel, and free. One tint colour generates a range of tones indexed by the backdrop's
brightness: lifted and desaturated over dark ground, darkened and saturated over bright, staying
recognisably the same colour throughout. That is how a pane of coloured glass behaves, and a
flat fill of the same colour is the failure mode Apple calls out by name.

`GlassStyle.adaptivity` scales it. At 0 the tint is fixed — which is what
`GlassStyle.Clear` uses, because Apple states the clear variant *"does not have adaptive
behaviours, it is permanently more transparent"*. That is also why `Clear` **requires** its
dimming layer: with no adaptation it has nothing else keeping content legible.

## Legibility rises with local contrast

Also per-pixel, also free. Apple: as text scrolls underneath, "the amount of tint and the
dynamic range shift to always ensure buttons remain legible".

The shader gets the signal for nothing — its rim sample and its interior sample already bracket
the backdrop's high frequencies, so their difference stands in for "something busy is under
here". `GlassStyle.legibility` scales how far that raises the tint. No host involvement, no
extra texture fetch.

## Light/dark inversion is one decision per element

This one is the host's, and it has to be.

Symbols and labels drawn **on** the glass flip in lockstep with it, and a fragment shader behind
them cannot reach them. So a per-pixel `smoothstep` on backdrop luminance is the wrong shape of
answer no matter how it is tuned: the glass would flip and the label on it would not. Only a
single scalar that the app hands to *both* keeps them in step.

```kotlin
val inversion by rememberBackdropInversion(glass)
val state = rememberLiquidGlassState(background = ground, inversion = inversion)

// and the same number decides the content's colours
Text(color = lerp(darkInk, lightInk, inversion))
```

`rememberBackdropInversion` reads a sparse grid — thirty samples, off the main thread, ten times
a second — and applies hysteresis: it enters the flip above 0.60 and leaves below 0.44. The band
is not decoration. A single threshold flickers every time a headline scrolls past, because
luminance crosses it repeatedly within one gesture. The result crossfades over 180ms.

Apple publishes no threshold, no band and no duration. Those three numbers are ours; the shape
of the mechanism is what matters.

Gate it by size with `GlassStyle.invertsWithBackdrop`: Apple flips small chrome to hold contrast
and lets large surfaces adapt without flipping, because a flip across that much area is
distracting. `Thick` and `Clear` never flip.

> **If your app is permanently dark or permanently light, leave `inversion` at 0.** The
> machinery costs a bitmap read ten times a second and buys nothing.

## Shadows deepen with size and with busy content

Apple keys the shadow to element size — a larger surface sits further off its background — and
raises it as content scrolls underneath.

Compose has no common shadow API that takes a colour and a blur, so the library hands back the
numbers rather than drawing it:

```kotlin
val shadow = glassShadow(sizeFactor = 0.4f, contrast = 0.8f)
// shadow.alpha, shadow.blurRadius, shadow.offsetY — apply however your platform allows
```

## The scroll edge effect

**This is not the material**, and conflating the two is the usual mistake. Apple ships it as a
separate object — `UIScrollEdgeEffect`, on the scroll view, not on the glass — because it solves
a different problem. The material makes chrome legible against what is behind it; the scroll
edge effect stops content appearing to slide out from under the chrome's edge. An app can want
either without the other.

```kotlin
Box(Modifier.align(Alignment.BottomCenter)) {
    GlassScrollEdge(ground = MyTheme.ground, fromBottom = true, inverted = inversion > 0.5f)
    NavBar(Modifier.liquidGlass(glass, CircleShape, GlassStyle.Chrome))
}
```

`Soft` ramps a quadratic gradient over the overlap; `Hard` is uniform across the band, for
chrome with a hard edge where a gradient reads as a smudge.

`inverted` matters: Apple swaps the fade for a dimming when dark content pushes the glass into
its dark style, because a fade to the ground colour over dark content does nothing visible — the
effect would silently stop working exactly when it is needed. Drive it from the same signal as
`inversion` so the two cannot drift.

It samples nothing and has no backdrop of its own. It costs a gradient.

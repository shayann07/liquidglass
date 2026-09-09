# Accessibility

Apple's material honours three system settings. Android has an exact analogue for one of them, a
partial one for another, and **none at all** for the third. That asymmetry is the whole content
of this page.

## Reduce Transparency — no Android setting exists

iOS has it. Android does not, at any API level. So `frost` is driven by whatever control **your
app** exposes, and an app that ships this material without offering one is giving users less
control than iOS does.

```kotlin
val state = rememberLiquidGlassState(
    background = ground,
    frost = if (prefs.reduceTransparency) 1f else 0f,
)
```

Frost raises the material's opacity rather than switching it off, which keeps the shape and the
edge lighting intact for anyone who wants to keep seeing them. It is a slider, not a boolean, if
you want to offer that.

## Increase Contrast — a partial analogue

`AccessibilityManager.isHighTextContrastEnabled` is the nearest thing, and it is about *text*
rather than materials, so treating it as Apple's setting is an approximation rather than the
same thing. Say so in your settings copy if you surface it.

```kotlin
val contrast = if (highContrastEnabled) 1f else 0f
val state = rememberLiquidGlassState(background = ground, contrast = contrast)
```

At 1 the material goes predominantly black or white — chosen against the backdrop's luminance —
with a contrasting border. That is Apple's stated behaviour: the effect becomes solid rather
than disappearing, so the element keeps its shape and its affordance.

## Reduce Motion — a real analogue

`Settings.Global.TRANSITION_ANIMATION_SCALE == 0` is a genuine equivalent.

Apple's guidance is that Reduce Motion "decreases the intensity of some effects and disables any
elastic properties", which maps cleanly:

```kotlin
val interaction = if (reduceMotion) GlassInteraction.ReducedMotion else GlassInteraction.Default
val materializeSpec = if (reduceMotion) GlassMotion.MaterializeReduced else GlassMotion.MaterializeIn
```

`ReducedMotion` drops the press scale, the bounce and the gel, and halves the glow. The feedback
survives, the elasticity does not — which is what the setting asks for, rather than removing the
response altogether.

## What is not covered

There is no Android equivalent of iOS 26.1's system-level Clear/Tinted "preferred look for
Liquid Glass", nor of 26.2's Lock Screen transparency slider. If your app wants those it has to
offer them itself.

## Vibrancy: the gap you cannot close

Apple's largest legibility mechanism for content *on* glass is automatic — a label "becomes
vibrant based on its textColor", and on the Regular variant everything placed on the glass gets
it, with symbols flipping light/dark in lockstep. That is a compositing mode inside Apple's own
renderer.

Compose has no equivalent, and a fragment shader drawn *behind* content cannot touch that
content. The nearest available approach is the per-element `inversion` scalar (see
[Adaptation](adaptation.md)) handed to both the glass and whatever draws the labels. It works,
and it is coarser: one decision per element per frame, not per glyph. A label straddling a
light/dark boundary in the backdrop will not track it the way Apple's vibrancy does.

Design around it. Do not put small text over glass that sits on a high-contrast backdrop and
expect the platform to save it.

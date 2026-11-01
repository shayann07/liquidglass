# Accessibility

Apple's material honours three system settings. Android has an exact analogue for one of them, a
partial one for another, and **none at all** for the third. That asymmetry is the whole content
of this page.

Since 1.1 the library reads what the platform offers by itself (#115):

| Setting | Android | Web | Desktop |
| :-- | :-- | :-- | :-- |
| Reduce Transparency | none exists | `prefers-reduced-transparency: reduce` | not read |
| Increase Contrast | system contrast above standard (API 34+) | `prefers-contrast: more` | not read |
| Reduce Motion | Remove animations (animator duration scale 0) | `prefers-reduced-motion: reduce` | not read |

They are the *defaults* of `LiquidGlassTheme(reduceTransparency, increaseContrast)` and
`GlassTabBar(motionEnabled)`. Pass your own values, as below, to drive them from your app's settings instead;
an explicit argument always wins. They are read when the theme or bar enters the composition (on Android
again after a configuration change), not watched.

## Reduce Transparency — no Android setting exists

iOS has it. Android does not, at any API level. So `reduceTransparency` is driven by whatever
control **your app** exposes, and an app that ships this material without offering one is giving
users less control than iOS does.

```kotlin
LiquidGlassTheme(
    background = ground,
    reduceTransparency = if (prefs.reduceTransparency) 1f else 0f,
) { App() }
```

It raises the material's opacity rather than switching it off, which keeps the shape and the
edge lighting intact for anyone who wants to keep seeing them. It is a slider, not a boolean, if
you want to offer that.

## Increase Contrast — a partial analogue

From Android 14 the system contrast level (`UiModeManager.getContrast()`, Settings → Accessibility →
Colour and motion → Contrast) is the nearest thing, and the library follows it by default: any level above
standard turns `increaseContrast` on. Before Android 14, `AccessibilityManager.isHighTextContrastEnabled` is
the nearest, and it is about *text* rather than materials, so wire it yourself if you want it and say so in your
settings copy.

```kotlin
LiquidGlassTheme(background = ground, increaseContrast = if (highContrastEnabled) 1f else 0f) { App() }
```

Both settings apply to every glass element under the theme. A theme nested inside another
changes them for its subtree only.

At 1 the material goes predominantly black or white — chosen against the backdrop's luminance —
with a contrasting border. That is Apple's stated behaviour: the effect becomes solid rather
than disappearing, so the element keeps its shape and its affordance.

## Reduce Motion — a real analogue

Android's Remove animations (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`) is a genuine equivalent, and
`GlassTabBar` follows it by default. Other glass controls take `GlassInteraction.ReducedMotion` from you:

Apple's guidance is that Reduce Motion "decreases the intensity of some effects and disables any
elastic properties", which maps cleanly:

```kotlin
val response = if (reduceMotion) GlassInteraction.ReducedMotion else GlassInteraction.Calm
Modifier.glassEffect(Glass.Regular.interactive(response = response), CircleShape)

GlassTabBar(..., motionEnabled = !reduceMotion)
val materializeSpec = if (reduceMotion) GlassMotion.MaterializeReduced else GlassMotion.MaterializeIn
```

`ReducedMotion` drops the press scale, the bounce and the gel, and halves the glow. The feedback
survives, the elasticity does not — which is what the setting asks for, rather than removing the
response altogether.

## Keyboard focus

`GlassTabBar`'s tabs draw a focus ring when focused from the keyboard, so a keyboard user can see where focus is.
Pointer input never shows it. Other glass controls take your own `indication` as ordinary Compose controls do.

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
content. The nearest available approach is one light/dark decision per element (see
[Adaptation](adaptation.md)) handed to both the glass and whatever draws the labels; today that
decision is the app's, through `LiquidGlassTheme(dark = …)`, since backdrop-driven inversion became
internal on 18 October. It works, and it is coarser: one decision per element per frame, not per glyph. A label straddling a
light/dark boundary in the backdrop will not track it the way Apple's vibrancy does.

Design around it. Do not put small text over glass that sits on a high-contrast backdrop and
expect the platform to save it.

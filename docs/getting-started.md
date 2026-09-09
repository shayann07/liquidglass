# Getting started

## Install

The module has no dependency on any app — copy `liquidglass/` into your project and add it to
`settings.gradle.kts`, or publish it to your own Maven repository. It needs Compose
Multiplatform (foundation and ui); it does **not** need material3.

```kotlin
// settings.gradle.kts
include(":liquidglass")

// your module's build.gradle.kts
implementation(project(":liquidglass"))
```

## The three-line version

```kotlin
val glass = rememberLiquidGlassState(background = MyTheme.ground)

Box {
    MyScrollingContent(Modifier.liquidGlassSource(glass))   // what gets refracted

    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .liquidGlass(glass, CircleShape, GlassStyle.Chrome)
    ) { /* tab bar */ }
}
```

Three things are happening:

- **`rememberLiquidGlassState(background)`** links a backdrop to the panels that refract it. The
  `background` is your app's opaque ground colour, and it is not decoration — see below.
- **`Modifier.liquidGlassSource`** marks what gets refracted. The content is recorded into a
  layer once per frame and read by every panel.
- **`Modifier.liquidGlass`** turns an element into an optical object over that backdrop.

## The one rule: where the backdrop goes

**Put `liquidGlassSource` on the content, and the glass outside it.**

That is not an arbitrary preference. Glass is chrome floating over content — that is what the
material is for and how Apple describes it — and it is also the only arrangement that always
works. A glass element *inside* a scrolling container whose source is *outside* it cannot find
its own offset, because Compose applies a scroll when it draws rather than when it lays out and
no layout API reports it. See [Limitations](limitations.md#glass-inside-a-scroll).

Both of these are fine:

```kotlin
// Chrome over a list. The usual case.
Box {
    LazyColumn(Modifier.liquidGlassSource(glass)) { … }
    NavBar(Modifier.align(BottomCenter).liquidGlass(glass, CircleShape, GlassStyle.Chrome))
}

// Everything inside one scroll, sharing a frame.
Box(Modifier.verticalScroll(state)) {
    Backdrop(Modifier.matchParentSize().liquidGlassSource(glass))
    Column { Card(Modifier.liquidGlass(glass, shape, GlassStyle.Regular)) }
}
```

## Why `background` matters

A backdrop layer is transparent wherever your app painted nothing — the ground is usually
painted by an ancestor, not by the recorded subtree — and the shader needs to know what shows
through there. Get it wrong and panels grow a halo of the wrong colour.

Pass the colour that is actually behind your content. For a dark app that is your near-black
ground; for a light one, your near-white.

## Picking a style

| Preset | For |
| :--- | :--- |
| `GlassStyle.Regular` | The workhorse. Cards and controls. |
| `GlassStyle.Chrome` | Tab bars, toolbars, accessory pills floating over your own content. |
| `GlassStyle.Clear` | Chrome that should mostly disappear. Requires its dimming layer. |
| `GlassStyle.Thick` | A sheet or dialog holding a lot of content. |

If your glass floats over your app's own ground rather than over photos or saturated colour,
**start with `Chrome`.** The others assume a backdrop worth refracting; over a plain dark ground
with sparse text they read as absent, and the little that does come through arrives smeared
along the rim and sitting under your labels. `Chrome` trades the signature edge for legibility
deliberately.

Every field is a `data class` parameter, so `GlassStyle.Regular.copy(tint = …)` is the intended
way to adjust one.

## Below Android 13

`RuntimeShader` — and therefore the whole optical path — is API 33+. Below that the material
degrades to a tinted surface with the same rim lighting: plainer, not broken. Supply a
`fallbackSurface` that suits your ground whenever the glass carries labels, or they will be
unreadable on a third of devices.

```kotlin
GlassStyle.Chrome.copy(fallbackSurface = MyTheme.navSurface)
```

Check `LiquidGlassSupport.hasShaders` if you want to change the design rather than the colour.

---
description: Install LiquidGlass in a Compose Multiplatform or Android app with one Gradle plugin line, then add iOS 26-style glass with one modifier or by changing Material 3 imports.
---

# Getting started

## Install

Three ways, from least to most control. The plugin, the BOM and `liquidglass-material3` are new in
0.2.0.

**The Gradle plugin**, one line. It adds `liquidglass` to `commonMain` (or `implementation`), and
`liquidglass-material3` too when the module already uses Material 3:

```kotlin
// build.gradle.kts
plugins {
    id("dev.shayxo.liquidglass") version "1.0.0"
}
```

The plugin is on Maven Central: `pluginManagement { repositories { mavenCentral() } }` in
`settings.gradle.kts` finds it, and new Android Studio and Kotlin Multiplatform projects already list it.

`./gradlew liquidGlassAdopt` then switches an existing app's Material 3 imports to the glass versions,
once (`--dry-run` lists the changes first). `liquidGlass { material3.set(false) }` keeps Material out.

**The BOM**, the convention Compose and Firebase use:

```kotlin
implementation(platform("dev.shayxo.liquidglass:liquidglass-bom:1.0.0"))
implementation("dev.shayxo.liquidglass:liquidglass")
implementation("dev.shayxo.liquidglass:liquidglass-material3")   // Material 3 apps
```

**Plain artifacts**:

```kotlin
// your module's build.gradle.kts — commonMain for a multiplatform module
implementation("dev.shayxo.liquidglass:liquidglass:1.0.0")
```

It is a Kotlin Multiplatform module with Android, desktop JVM and web (`wasmJs`) variants; Gradle picks the one
each target needs. It depends on Compose Multiplatform's foundation and ui, and on nothing else
— it does **not** need material3, and it does not care whether your app uses it.

**Not using Compose?** On an ordinary web page, or with React, Vue, Svelte or Angular, use the
[`<liquid-glass>` web component](web-component.md) instead (`npm install @shayxo.dev/liquidglass`). It refracts live HTML in Chromium, frosts elsewhere, and can draw the production shaders over a
source you give it.

**To try `main` before the next release**, build it yourself:

```bash
git clone https://github.com/shayann07/liquidglass && cd liquidglass
./gradlew publishToMavenLocal
```

and let your project see the local repository, restricted to this one group so nothing else in
`~/.m2` can shadow a real dependency:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal { content { includeGroup("dev.shayxo.liquidglass") } }
    }
}
```

## Which API you get

0.2.0 replaced 0.1.0's state-and-modifier API (`rememberLiquidGlassState`,
`Modifier.liquidGlassSource`, `Modifier.liquidGlass`) with the API below. The
[API reference](api-reference.md#removed-18-october) maps each removed name to its replacement.

## Set up once, at the root

```kotlin
setContent {
    LiquidGlassTheme(background = MyTheme.ground) {
        App()
    }
}
```

`LiquidGlassTheme` is to Liquid Glass what `MaterialTheme` is to Material: wrap the app in it
once. It records what the app draws below it once per frame, and every glass element below it
refracts what is behind that element. Nothing has to be marked as a backdrop.

## Make something glass

```kotlin
Box {
    MyScrollingContent()

    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .glassEffect(Glass.Regular, CircleShape)
    ) { /* toolbar items */ }
}
```

`Modifier.glassEffect(glass, shape)` draws the element as glass: the screen behind it, refracted
through `shape`, with the element's own content on top. It works at any depth in the tree, in a
scrolled list or under a `graphicsLayer`. The element keeps its own layout, hit targets, clicks
and semantics; `glassEffect` does not make anything clickable. Without a `LiquidGlassTheme` above
it, it draws a frosted, rim-lit stand-in and logs one warning rather than failing.

For a control that acts on a tap, make the glass respond to touch:

```kotlin
Icon(Icons.Default.Edit, "Edit",
    Modifier.size(44.dp).clickable(onClick = edit).glassEffect(Glass.Regular.interactive(), CircleShape))
```

Two limits of the current renderer: glass is drawn above content that is not glass, and an
ancestor's alpha does not fade it. See [Limitations](limitations.md#glass-draws-above-content-that-is-not-glass).

## Why `background` matters

The recording is transparent wherever your app painted nothing — the window background, for
instance, is not part of it — and the shader needs to know what shows through there. Get it wrong
and panels grow a halo of the wrong colour.

Pass the colour that is actually behind your content. For a dark app that is your near-black
ground; for a light one, your near-white. The default is black when `dark` is true and white
otherwise.

## Picking a glass

| Variant | For |
| :--- | :--- |
| `Glass.Regular` | The workhorse: the measured in-app material, for buttons, pills and panels. Follows the theme's `dark`. |
| `Glass.Clear` | Glass that should mostly disappear. Needs content behind it dimmed or calm enough to stay legible. |
| `Glass.Toolbar` | A toolbar: the pill's material, more opaque. |
| `Glass.TabBar` | iOS 26's tab bar material, for a control that sits beside a `GlassTabBar`. |
| `Glass.Lens(magnification)` | A clear magnifier. |
| `Glass.Identity` | No glass, the content only: to switch glass off without changing the layout. |
| `Glass(GlassStyle.Chrome)` | Any `GlassStyle` preset or your own: every optical parameter. |

Refine a variant with `.tint(color)` for a tint that carries meaning, and `.interactive()` for
touch response. `LiquidGlassTheme(glass = …)` sets what `glassEffect()` draws when it is given no
`Glass`.

If your glass floats over your app's own ground rather than over photos or saturated colour,
consider **`Glass(GlassStyle.Chrome)`**. The other presets assume a backdrop worth refracting; over
a plain dark ground with sparse text they read as absent, and the little that does come through
arrives smeared along the rim and sitting under your labels. `Chrome` trades the signature edge
for legibility deliberately.

Every `GlassStyle` field is a `data class` parameter, so `Glass(GlassStyle.Regular.copy(tint = …))`
is the intended way to adjust one.

## Merging and morphing

`GlassEffectContainer` makes the glass inside it one body: pieces within `spacing` of each other
join through a liquid neck. `Modifier.glassEffectID` with `rememberGlassNamespace()` lets a piece
that appears where another left grow out of it. Both are optional; see the
[API reference](api-reference.md#glasseffectcontainer).

## Navigation

```kotlin
GlassTabBar(itemCount = 3, selectedIndex = selected, onSelected = { selected = it }) { index, isSelected ->
    Tab(index, tint = if (isSelected) Accent else Ink)
}
```

It needs the `LiquidGlassTheme` above it and takes no state. See the [tab bar guide](tab-bar.md).

## Android XML Views

In an existing Android XML or ViewBinding layout (`Fragment`, `Activity`, `ConstraintLayout`, `CoordinatorLayout`, `RecyclerView`), use `LiquidGlassView` to wrap XML child views in a refracting glass container directly in XML, or `LiquidGlassHostView` to float interactive Compose glass controls (`GlassTabBar`, FABs, menus) over an Android `View` hierarchy with selective touch pass-through (`Modifier.glassHitTarget`). See [Liquid Glass for Android](for/android.md#android-xml-and-view-layouts) and the [API reference](api-reference.md#android-xml-view-interop).

## Below Android 13

`RuntimeShader` — and therefore the whole optical path — is API 33+. Below that the material
degrades to a tinted surface with the same rim lighting: plainer, not broken. Supply a
`fallbackSurface` that suits your ground whenever the glass carries labels, or they will be
unreadable on a third of devices.

```kotlin
Modifier.glassEffect(Glass(GlassStyle.Chrome.copy(fallbackSurface = MyTheme.navSurface)), CircleShape)
```

Check `LiquidGlassSupport.hasShaders` if you want to change the design rather than the colour.

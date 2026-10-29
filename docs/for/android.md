---
description: Liquid Glass for Android, Jetpack Compose and Compose Multiplatform. iOS 26's refracting glass, its tab bar lens and glass Material 3 components, measured against iOS recordings.
---

# Liquid Glass for Android and Jetpack Compose

iOS 26's Liquid Glass in Jetpack Compose: real refraction of whatever is behind a surface, the tab bar
whose selection becomes a lens you can drag and throw, and a glass version of every Material 3
component. The same code runs on desktop and on the web through Compose Multiplatform. The material
and the motion are fitted to iOS screen recordings and scored against them.

![A Material 3 app in glass, dark scheme: top bar, buttons, chips, a frosted mini player and the floating tab bar](../assets/material3-dark.png)

*Our render: the Material 3 showcase, desktop Skia, the same shader as Android's AGSL path.*

```kotlin
// build.gradle.kts
plugins { id("dev.shayxo.liquidglass") version "1.0.0" }
```

```kotlin
LiquidGlassTheme { App() }                       // once, as MaterialTheme is

Icon(Icons.Default.Edit, null,
    Modifier.size(44.dp).glassEffect(Glass.Regular.interactive(), CircleShape))
```

Or keep your Material 3 code and change the imports, `androidx.compose.material3.*` to
`com.wexpa.liquidglass.material3.*`; `./gradlew liquidGlassAdopt` does it for you.

## Android XML and `View` layouts

The library supports idiomatic Android XML layouts via **`GlassBackdrop`**, **`LiquidGlassView`**, **`LiquidGlassHostView`**, and the **`View.glassEffect`** extension:
1. **`GlassBackdrop`**: An XML container (`FrameLayout`) that records ordinary non-glass content (`ScrollView`, `RecyclerView`, canvas views) into a shared native `RenderNode` during the standard 6-step draw pipeline.
2. **`LiquidGlassView`**: A pure XML glass container that wraps Android XML children (`TextView`, `ImageView`, `Button`) and refracts the backdrop using native `RenderNode` and `RuntimeShader` without spinning up internal Compose compositions.
3. **`View.glassEffect(backdrop, glass, shape)`**: Applies the live glass effect directly behind any native Android `View`.
4. **`LiquidGlassHostView`**: Floats interactive Compose glass controls (such as `GlassTabBar`, FABs, or menus) over an existing `View` hierarchy with selective touch pass-through (`Modifier.glassHitTarget`).

```xml
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- Shared backdrop recording ordinary non-glass content -->
    <com.wexpa.liquidglass.GlassBackdrop
        android:id="@+id/backdropContainer"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <ScrollView
            android:layout_width="match_parent"
            android:layout_height="match_parent">
            <!-- Normal Android Views, text, cards, and scroll rows -->
        </ScrollView>
    </com.wexpa.liquidglass.GlassBackdrop>

    <!-- Wrap XML child views in a refracting glass surface -->
    <com.wexpa.liquidglass.LiquidGlassView
        android:id="@+id/headerGlassCard"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:liquidGlassBackdrop="@id/backdropContainer"
        app:liquidGlassPreset="regular"
        app:liquidGlassShape="squircle"
        app:liquidGlassCornerRadius="24dp"
        app:liquidGlassInteractive="true">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:padding="16dp">
            <!-- Ordinary TextViews, ImageViews, Buttons -->
        </LinearLayout>
    </com.wexpa.liquidglass.LiquidGlassView>

    <!-- Or host a Compose GlassTabBar / floating controls over an XML layout -->
    <com.wexpa.liquidglass.LiquidGlassHostView
        android:id="@+id/glassChromeOverlay"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:liquidGlassBackdrop="@id/backdropContainer" />
</FrameLayout>
```

```kotlin
// Convenience extension on any native Android View:
val effect = binding.myNativeCard.glassEffect(
    backdrop = binding.backdropContainer,
    glass = Glass.Regular,
    shape = RoundedCornerShape(16.dp),
)

// Compose overlay hosting:
binding.glassChromeOverlay.setThemedGlassContent { host ->
    Box(Modifier.fillMaxSize()) {
        GlassTabBar(
            itemCount = 4,
            selectedIndex = selectedTab,
            onSelected = { selectedTab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .glassHitTarget("tab_bar", host), // touches outside pass through to XML views below
        ) { index, isSelected -> /* ... */ }
    }
}
```

- **Android 13 and newer:** the full shader (AGSL), checked on a Samsung Galaxy S24+ ([Evidence](https://github.com/shayann07/liquidglass/blob/main/review/atlas/android-xml-interop-2026-10-26/README.md)).
- **Below Android 13:** a tinted fallback with rim lighting, no refraction.
- **Desktop and web:** the same shader on Skia. [Try it in your browser](../demo.md).

Next: [Getting started](../getting-started.md) · [Material 3, in glass](../material3.md) ·
[The tab bar](../tab-bar.md) · [Limitations](../limitations.md)


<!-- XML interop takeover, 2026-10-29 -->
Android View interop correction: interactive effects preserve application clickability, and drawable opacity fades the complete native/fallback output once. Independently checked on physical S24+ API 36 and focused API 28/32 emulators; shared JVM rendering and original calibration-target regressions pass. Qualification is local; see `review/atlas/android-xml-takeover-2026-10-29/README.md` for provenance and limits.

The completed motion regression compares 4,194 frames from full docs2 and IMG_6756 Search scripts against freshly rendered merge-base output: all frames and motion traces match exactly. The physical Android suite now passes 37 tests, including scrolling, child invalidation and detach/reattach across all three consumer paths. The existing Search brightness gap from iOS remains a shared-material limitation.

# LiquidGlass

A refracting material for Compose UI, with a shared backdrop, shaped glass surfaces and restrained touch feedback.

[![CI](https://github.com/shayann07/liquidglass/actions/workflows/ci.yml/badge.svg)](https://github.com/shayann07/liquidglass/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)
[Documentation](https://liquidglass.shayxo.dev/) · [Getting started](docs/getting-started.md) · [API](docs/api-reference.md) · [Research](docs/research/README.md)

![Atlas Studio running on Windows](review/atlas/after-desktop.png)

**Development preview.** The current work adds continuous magnifier optics, calmer press and drag feedback,
a simpler scene API and a redesigned Atlas Studio. These additions are not in the published `0.1.0` artifact.
They are authored models informed by iPhone references; **full 1:1 iOS parity is not established**.
See the [verification record](review/ATLAS-STABILIZATION.md) for rendered evidence and remaining work.

## Start with one scene

Record your page once, then place ordinary Compose controls over it. The scene connects each surface
to its backdrop; click handling, layout and accessibility stay with your normal UI components.

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wexpa.liquidglass.*

GlassScene(
    background = Color(0xFF09111E),
    modifier = Modifier.fillMaxSize(),
    backdrop = { YourPage() },
) {
    Row(
        Modifier.align(Alignment.BottomCenter)
            .padding(24.dp)
            .glass(style = GlassStyle.DarkChrome)
            .padding(16.dp),
    ) {
        YourNavigationItems()
    }
    // A real magnifier over the recorded page: no duplicate labels or separate zoom layer.
    Box(
        Modifier.align(Alignment.Center).size(180.dp)
            .glass(CircleShape, GlassStyle.clearLens(magnification = 1.25f)),
    )
}
```

`YourPage` and `YourNavigationItems` are your composables. Size the scene explicitly, keep its
backdrop opaque, and leave its glass overlays out of the backdrop. `glass()` uses calm feedback;
pass `interaction = null` for a passive panel or `GlassInteraction.ReducedMotion` for reduced motion.

Already manage a backdrop? Keep using `rememberLiquidGlassState`, `liquidGlassSource` and
`Modifier.liquidGlass`. Existing presets and default behaviour are preserved.

## Choose the right material

| Need | API | What it does |
| --- | --- | --- |
| Floating card or toolbar | `GlassStyle.Regular` / `DarkChrome` | Refracted edge, softened background and tint |
| Free magnifier | `GlassStyle.clearLens(1.25f)` | One continuous map for backdrop and foreground; no hidden inner band |
| Restrained touch feedback | `GlassInteraction.Calm` | Separate press and drag, gradual resistance, fixed ordinary labels |
| Navigation selector | `GlassTabBarStyle.Calm()` | Travel deformation on the selector; subtle drag deformation on the bar |
| Accessibility | `GlassInteraction.ReducedMotion` | Feedback without elastic geometry; host supplies system preference |
| Historical comparisons | `GlassTabBarStyle.V3()` / `GlassInteraction.Pullable` | Previous authored dynamics, preserved explicitly |

Glass does not move your controls unless **your layout** moves them. Stretch belongs to the material.
Long cards use a smaller drag response than compact controls. A navigation selector is a different
optical role from a magnifier; using the held-selector map on a free lens caused the recurring split rim.
Calm navigation keeps a reference-backed 5% whole-bar touch expansion, with its slower timing and
restrained drag independent of that expansion. Generic cards retain the 3% / 2dp-per-edge press cap.

Desktop Calm selectors also compose refracted ink and material together in the final shader, avoiding
one intermediate rounding step. The [porting guide](docs/porting.md#combining-refracted-ink-with-glass)
covers the shared compositor contract, GPU input bounds, memory cost and Android's remaining limitation.

## Platforms and installation

| Platform | Support |
| --- | --- |
| Android 13+ | AGSL shader renderer |
| Android below 13, within the project's minimum SDK | Tinted fallback; no shader refraction |
| Desktop JVM | Skia renderer; native Windows capture verified in the review record |
| Compose iOS / native SwiftUI | No packaged renderer yet |
| Web / JS frameworks | [Dependency-free lens preview](ports/web/README.md), or the [production Skia shader adapter](ports/skia/README.md); supply the backdrop explicitly |
| Other Skia hosts | Generated material, content and aperture shaders; native bindings still need host integration and tests |
| Other frameworks | See the [porting contract](docs/porting.md); native adapters remain work in progress |

The Skia preview includes `createGlassPainter`: set one source image, draw lenses or rounded glass surfaces,
then dispose. It manages shader uniforms and temporary resources; you retain your own layout, input,
accessible controls and backdrop capture. Run `npm ci --ignore-scripts && npm run example` in
`ports/skia` for an executable example without Compose or a browser.
`drawSurface(canvas, bounds, {dark: true, tintAmount: 50})` adds the production in-app material and
cached wide-tone blur without manual shader setup. [Surface options and lifecycle](ports/skia/README.md#draw-a-custom-material).
Shader layouts are inspected once at compilation; repeated draws reuse their binding metadata.
Clamped tone sampling preserves opaque backdrops even at tiny sizes and screen edges.
For motion, `createCalmInteraction` supplies framework-independent press/pull, cancellation, viewport
containment and reduced motion. Feed timestamps and cumulative displacement; apply its matrix to the
material drawing. [Portable feedback API and host example](docs/porting.md#portable-calm-feedback).

Published stable version, with the original API:

```kotlin
implementation("dev.shayxo.liquidglass:liquidglass:0.1.0")
```

For the new scene, lens and calm APIs, build this source revision:

```sh
./gradlew publishToMavenLocal
./gradlew :desktop:run
```

Use the `VERSION_NAME` from `gradle.properties` in the consuming project, with `mavenLocal()` scoped
to `dev.shayxo.liquidglass`. [Full installation and repository setup](docs/getting-started.md).
JDK 21 and Android SDK 37 are used for this repository. The owner's Windows setup uses the isolated
[workspace wrapper](WORKSPACE.md); contributors can use the checked-in Gradle wrapper directly.

## Explore Atlas Studio

Atlas is a standalone desktop demonstration, independent of Vitals. Drag the lens over the sky,
fine type or a line grid. Change magnification, test the floating navigation bar, pull the anchored
note card, or switch to reduced motion. The sky compositions are illustrative, not astronomical data.

```sh
./gradlew :desktop:run
# Capture only Atlas's native Skia buffer; no screenshots of other applications.
./gradlew :desktop:run -Patlas.capture=/absolute/path/atlas.png -Patlas.scene=2
```

The capture command also writes static redraw timings. They measure renderer submission cost,
not presented FPS, interaction latency or Android performance. [Atlas guide](docs/atlas-studio.md).

## Validate a change

```sh
./gradlew :liquidglass:jvmTest --rerun
./gradlew :desktop:desktopTest :sample:assembleDebug
python -m pip install -r requirements-docs.txt
python .github/scripts/check_repo_links.py
python -m mkdocs build --strict
```

Tests compile the production shaders through Skia, exercise gesture ownership and inspect rendered
pixels. They also distinguish ordinary travel from extreme drags. Native screenshots complement
these tests; a passing build alone does not prove optical parity. [Contributing](CONTRIBUTING.md)
describes evidence requirements and [security reporting](SECURITY.md) explains the trust boundaries.

## Research and limitations

Apple's public guidance describes the material's behaviour, not its private shader constants.
The project keeps measured observations, inherited approximations and authored responses separate.
A smooth magnifier source map is a mathematical continuity fix, not a claim to have recovered Apple's optics.

- [Current stabilization record and desktop proof](review/ATLAS-STABILIZATION.md)
- [Generic interaction and gesture ownership](docs/generic-interaction.md)
- [Measured model and provenance](docs/research/measured-model.md)
- [Platform, optical and performance limits](docs/limitations.md)
- [Historical implementations and restoration](archive/retired-workflows/2026-10-03/README.md)

## License and credits

Original library and sample code are [Apache-2.0](LICENSE). Preserve the [NOTICE](NOTICE) when
redistributing. Reference captures are research evidence, not runtime assets or an Apple asset license.
No Apple shader, system binary or SF Symbols asset is used by the runtime. This project is independent
and is not affiliated with or endorsed by Apple. See [contribution guidelines](CONTRIBUTING.md) and
[the changelog](CHANGELOG.md).

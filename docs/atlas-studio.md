# Atlas Studio

Atlas is the shared standalone sample in `:desktop` and the Android loupe activity. It illustrates
refraction over real backdrop pixels, including small labels and thin lines. It is not Vitals and
its illustrative sky is not an astronomical catalogue.

## Run and inspect

```sh
./gradlew :desktop:run
```

Choose a composition on wide windows, switch Sky / Type / Lines, drag the magnifier to an edge, change
its centre magnification or hide it. The floating navigation control uses `GlassTabBarStyle.Calm()`.
The note card uses the generic scene modifier; pull it in any direction to see subtle surface strain
while its words remain anchored. Motion reduced disables elasticity, including navigation fling.

The lens has one source image. Labels are part of the recorded backdrop and are magnified with their
surroundings; no duplicate foreground text is drawn inside it. Its position is clamped in canvas-local
coordinates when dragged or resized. Sidebars never enter that coordinate system.

## Reproducible desktop evidence

```sh
./gradlew :desktop:run -Patlas.capture=/absolute/path/atlas.png -Patlas.scene=0
./gradlew :desktop:run -Patlas.capture=/absolute/path/type.png -Patlas.scene=1
./gradlew :desktop:run -Patlas.capture=/absolute/path/grid.png -Patlas.scene=2
./gradlew :desktop:desktopTest
```

The application reads its own native Skia buffer and exits after capture. It never photographs the
owner's desktop, and it does not force itself above other applications. The adjacent timing JSON
records 120 forced redraw submissions after ten warmups. This is a static-scene diagnostic, not an
FPS benchmark, gesture-latency measurement or claim about Android performance.

Desktop tests send Compose pointer events and verify controls, edge placement and recovery. Their
screenshots use the test renderer; native app captures are labelled separately in the
[stabilization record](https://github.com/shayann07/liquidglass/blob/main/review/ATLAS-STABILIZATION.md).
The production window uses the operating system's available Skia backend; Atlas does not guess its name.

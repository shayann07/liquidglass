# Contributing

Thank you. A few things about how this project works will save you time.

## Build

Android SDK 37 and a JDK 17 or newer. `local.properties` with `sdk.dir`, or `ANDROID_HOME`.

```bash
./gradlew build                          # library for Android and JVM, tests, and the sample
./gradlew :liquidglass:jvmTest           # the shader and optics tests, no device needed
./gradlew :sample:installDebug           # the sample app onto a connected device
./gradlew publishToMavenLocal            # consume a local build from another project
```

## The material is measured, not tuned

Almost every constant in the shader has a provenance, recorded in
[docs/research/parameters.md](docs/research/parameters.md) and
[docs/research/reference-measurements.md](docs/research/reference-measurements.md). A change to
the optics should come with a measurement: either a reading from a real iOS device or a rendered
test in `liquidglass/src/jvmTest` that fails before the change and passes after it.
`GlassRender` compiles the real shader through Skia and hands back pixels, so most optical
claims can be asserted without a phone. Look at `GlassOpticsTest` for the pattern.

Two failure modes live in the host rather than the shader and look identical on screen. Read
[docs/how-it-works.md](docs/how-it-works.md) before deciding a shader change is needed.

## What we do not accept

- Apple's assets. No SF Symbols, extracted textures, or shaders lifted from a system binary.
  Apple's SDK agreement licenses those for Apple platforms only, and the whole point of this
  library is that its shader is its own.
- Unmeasured constants presented as measurements. If a number is a guess, say it is, the way the
  `Ios27` preset does.
- A component that is really a modifier. If it can be `Modifier.liquidGlass()` on a stock
  composable, it should be.

## Documentation

The docs in `docs/` are part of the change. A new parameter needs a line in
[docs/api-reference.md](docs/api-reference.md) and, if it came from somewhere, an entry in
`NOTICE`. Ideas taken from other implementations are credited there by project, with what was
taken and where it landed.

## Releasing

Maintainers: see [docs/publishing.md](docs/publishing.md).

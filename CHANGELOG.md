# Changelog

## 0.1.0

The first public release.

- `Modifier.liquidGlass(shape, style)` turns any composable into the material, and
  `Modifier.liquidGlassSource()` marks what it refracts. `LiquidGlassScene` provides the shared
  state, so neither needs to be handed one.
- Optics in a single SkSL shader shared by AGSL (Android 13+) and Skia (desktop JVM): signed
  distance geometry with a closed-form normal for analytic shapes, exact Snell refraction through
  a superellipse bevel, dispersion, an interior blur that tapers to zero at the rim, two-lobe rim
  lighting, a dark contour, a mirrored edge band, rim softening, tint as an absorbing medium, and
  a dome field for shapes whose refraction band reaches their own centre line.
- Presets: `Regular`, `Clear`, `Thick`, `Chrome`, and `DarkChrome`, the last measured from an
  iOS 26 tab bar in dark appearance.
- `GlassTabBar`, a selection lens measured against native iOS screenshots.
- `LiquidGlassContainer`, which fuses nearby panels into one body of glass.
- `GlassSquircleShape`, arbitrary shapes by measurement, touch response, materialize, morphing
  between styles, light/dark adaptation, the scroll edge effect, and the accessibility modes.
- Below Android 13 the material degrades to a tinted surface with the same rim lighting.
- A rendered test harness: the real shader compiled through Skia on the JVM, with optics asserted
  on pixels.

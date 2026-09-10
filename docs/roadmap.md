# Roadmap

Written 2026-09-10, after reading the source of every liquid-glass implementation that does real
optics. The audit is in [research/competitive-landscape.md](research/competitive-landscape.md);
this is what to do about it.

The strategic position, in one paragraph. Two libraries dominate this space and both refuse to
ship components: Kyant0's says so in one sentence in its README, and Haze has declined for three
years. The only project that ships components *and* real optics has 26 stars and is one month
old. Meanwhile the only measured tab bar in the field is ours. So the defensible ground is
optics fidelity plus finished components, and the thing that erodes it is reach: both leaders
build for iOS, macOS, web and desktop, and we build for Android and desktop JVM. Fix reach or
none of the rest matters.

---

## P0 — a defect two independent measurements agree on

**Done.** The direction-independent floor is gone from the edge line, which is now entirely
directional, and `GlassStyle.edgeShadow` draws the dark contour that defines a shape where
nothing is lit. Measured back on device: the lens's side rim reads a dark step at 21 against an
interior of 42, where the reference reads 24 against 41. `edgeLight` rose 1.14x on the two
affected presets to hold the lit line where it already measured correctly.

Two faults surfaced doing it, both now lessons. A contour drawn on the outermost pixel is
invisible over a dark ground, because coverage there is partial and the result is composited
against whatever lies outside; it has to sit inside the ramp. And the device harness was reading
the bar's edge rather than the lens's, because difference-based detection spans the whole bar
when every icon recolours on press.

## P1 — take what the competition proved

**The analytic gradient: done.** For a closed-form shape whose refraction band stops short of
the medial axis — which is the common case and both of our own presets — the surface normal now
has a closed form, saving four field evaluations per pixel. The wide central difference stays
for sampled paths and for bands that do reach the axis, because that is the only case its width
was ever for. Frame cost on the tab bar fell from 14ms to 13ms at p50 and 32ms to 29ms at p99,
with every measured parity number unchanged.

**The fingertip bulge: already present, and this item was an error.** The roadmap asked for
something the shader has had all along — `touchFall` displaces sampling toward the finger, on a
Gaussian whose sigma is the panel's short edge. The only real difference from QWEA0's is that
theirs is tighter and ours is deliberately broad, so a press on one member of a container lifts
its neighbours. Nothing to do.

**The dome field: deferred, with a measurement.** Haze replaces the distance field with a dome
field for elongated shapes, which is a cleaner answer to the medial-axis collapse than our
confidence fade. It engages only when the refraction band reaches the inradius, and a rendered
test at exactly that worst case now shows no seam with what we already have: the row on the axis
sits within 12 counts of its neighbours over a hard vertical stripe pattern. Our presets are
nowhere near that regime — a band of 42px against an inradius of 108 on the bar. The open
question is not the seam but how much refraction strength the fade costs down the spine of a
very wide pill, and that is worth measuring before adding a second field.

**The tint model: deferred, deliberately.** QWEA0 multiplies by the tint and adds a scattering
term, which preserves the backdrop's own light and shade where a lerp flattens it. Ours is a
lerp toward a tone-mapped colour — but its strength is *fitted to measurement*: 0.36 is the
value that both lifts pure black by 20 and passes 64% of white text, the two-measurement fit
that pins the material. Swapping the model would break that fit for a theoretical gain. If it is
done, it belongs behind a knob defaulting to the current behaviour, the way `edgeShadow` was.

## P2 — reach, which is the real gap

**Consumable: done.** The module publishes as `com.wexpa.liquidglass:liquidglass:0.1.0`, with
Android and JVM variants, Gradle module metadata, sources jars and a POM, under Apache-2.0. The
licence is the canonical text, and the README carries a statement of non-affiliation. The
artifact id is the plain descriptive one for now; if this ever takes a product name of its own,
that is a one-line change in `build.gradle.kts` before the first public publish.

**Targets: blocked on hardware, not on design.** Kotlin/Native cannot build iOS or macOS targets
from Windows, so adding them here would mean shipping code nobody has compiled. The desktop path
already runs the same shader through Skia, so the work when a Mac is available is mostly source
set plumbing: rename `jvmMain` to a shared Skia source set and let the native and web targets
depend on it. Two things to know before starting. Compose Multiplatform 1.11 turned the
non-Android shader into a Compose wrapper type rather than a raw Skia one, and our desktop
sources import Skia directly today. And the web targets *can* be built from here, so they are
the sensible first move.

**The platform backdrop: not started.** Android 17 QPR2 makes public a render node method that
applies an effect to the pixels already drawn behind a node, which is a correct backdrop at no
capture cost. It does no optics. Haze wires it in behind a feature flag with a capture fallback;
we should do the same. Not testable on this device, which is on Android 16.

## P3 — the moat is components

**The slider: done.** `GlassSlider` and `GlassSliderStyle`. The knob is an opaque shape at rest
and clear glass for the duration of the drag, it looks *through* the track rather than past it,
it does not scale on press, and it stretches along the direction of travel in proportion to
speed. Verified on device: the knob's centre falls from 217 to 73 as it becomes glass, the
track's own edges bend visibly inside it, and the backdrop text fringes red and blue where the
rim crosses it.

That leaves, in order of how often a real app needs them: a navigation bar, a bottom sheet, a
toggle, a search field, and a floating action button. And for the tab bar itself, the behaviour
Apple ships and we do not: minimize on scroll, a bottom accessory that moves inline when
minimized, and a dedicated trailing search tab.

## P4 — below Android 13

Everything good in this field needs API 33. We degrade to a tinted surface with rim lighting,
which is honest but plain. QWEA0 does real refraction and chromatic aberration down to API 24
through a NEON path in C++. That is a large piece of work and it is the difference between a
library that looks right for most users and one that looks right for all of them. Schedule it
after P2, not before.

---

## The measurement programme, revised

Parity to a single number is no longer meaningful. The 2026 release replaces the earlier
two-position toggle with a continuous transparency slider whose default is its midpoint, so the
material's opacity is now a user setting with a range. What stays measurable, and therefore
worth measuring, is everything the slider does not move: the geometry of the rim, the direction
it samples, the lighting lobes, the timing of formation and subsidence, and the shape of the
resting state.

Three specific captures would settle open questions:

1. **The same tab bar at both ends of the slider**, to separate what the material does from what
   the setting does. This resolves the discrepancy our measurements doc records but cannot
   explain, where two captures of the same bar differ by more than a factor of two in body
   brightness.
2. **A glass element overhanging a hard-edged boundary**, photographed on a device known to be
   on the 2026 release. That is the experiment that discriminates sampling direction, and it is
   the claim on which we differ from every other implementation.
3. **The 2026 edge treatment**, to replace the guessed numbers in the `Ios27` preset with
   measured ones. Right now that preset is the only thing in the library tagged as ours rather
   than measured.

---

## Naming and publishing

Apple has not registered "Liquid Glass" as a trademark; it is absent from Apple's own published
list, updated two weeks before this was written, fifteen months after launch. No takedown has
ever been filed over a reimplementation, and the largest projects in the field have carried the
name unchallenged for over a year, including one maintained by a consultancy with legal review.

The one real constraint is assets. Apple's SDK agreement licenses its system images only for
building apps for Apple platforms, which rules out shipping SF Symbols, extracted textures or
lifted shaders. Our shader is ours, so we are clear.

Follow the pattern the market leader already set: a descriptive repository name, a distinctive
artifact name, the Apple term used only descriptively in prose, and a short statement of
non-affiliation. None of this is legal advice, and the naming question is worth thirty minutes
of a trademark attorney's time before the library carries commercial weight.

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

**Remove the direction-independent floor in the edge line.** Our edge line is
`edge * (0.07 + 0.5 * directional)`. That 0.07 draws a faint bright ring all the way round,
including the sides, where the normal is perpendicular to the light and nothing should be lit.
Our own measurement says the reference lens has a line at top and bottom and *nothing but a
one-pixel dark step* at the sides. QWEA0's per-angle reading of the iOS 26 Control Center says
the same thing and their shader has no such term by design.

It is one constant. The work is not the edit, it is re-tuning `edgeLight` on the four presets
that were fitted with the floor present, then re-measuring the bar and lens on device. Do this
first, because every later measurement inherits it.

## P1 — take what the competition proved

**Adopt Haze's dome field for elongated shapes.** Our answer to the medial-axis collapse in a
capsule is a wide-epsilon gradient plus a confidence fade toward the axis, which costs
refraction strength down the spine of every pill. Haze blends the distance field into a dome
field built from a smooth rectangular radius, zero at the centre and one on every edge, with a
closed-form gradient. No seam, no fade, full strength. Gate it the way they do, on aspect ratio
past 1.5 and on the band approaching the inradius, so nothing square changes.

**Add Kyant0's analytic gradient as a fast path.** For a rounded rectangle the surface normal
has a closed form and we are spending four field evaluations per pixel to central-difference it.
Keep the difference for arbitrary paths, branch to the closed form for corner-based shapes, and
take their trick of evaluating it against an inflated corner radius capped at the half-size,
which smooths the direction discontinuity where the flat run meets the corner.

**Add a local bulge under the fingertip.** A Gaussian centred on the touch point that pulls
sampling toward the finger, on top of the rim refraction. We already track the touch for
illumination; this displaces there too. QWEA0 does it in about six lines and it is the single
most alive-looking thing in their demo.

**Model tint as absorption plus scattering.** We mix toward the tint, which flattens the
backdrop's own light and shade. Multiplying by the tint preserves that structure; a small
additive term scaled by how dark the backdrop is keeps the hue readable over black. This is a
better model than a tone-mapped mix and it is a contained change.

## P2 — reach, which is the real gap

**Ship iOS, macOS, web and desktop targets.** We build for Android and JVM. Kyant0 covers
Android, desktop, JS, Wasm, macOS ARM and iOS. Haze covers all of that plus the Intel variants.
Our desktop path already runs the same shader through Skia, so most of the work is source-set
plumbing and a Skia-backed backdrop capture per target. Note that Compose Multiplatform 1.11
turned the non-Android shader type into a Compose wrapper rather than a raw Skia type; our
desktop sources import Skia directly today, which compiles on 1.12 but is the first thing that
will bite when the target list grows.

**Make it consumable.** There is no license file, no group coordinate, no version and no
publishing plugin in the module. Nothing can be depended on. Pick a distinctive artifact name,
add a license, and publish to Maven Central.

**Adopt the platform backdrop behind a flag.** Android 17 QPR2 makes public a render node method
that applies an effect to the pixels already drawn behind a node. It is a correct backdrop with
no capture cost, it does no optics, and it cannot cross a dialog or popup boundary. Haze already
wires it in behind a feature flag with a capture fallback. Do the same. When it is widespread,
backdrop capture stops being anyone's advantage, which is an argument for getting our optics
lead documented before then rather than after.

## P3 — the moat is components

Nobody above 130 stars ships a published glass component. Kyant0 refuses on principle and keeps
its tab bar in the catalog app. Haze ships three functions that build a style. The best
behavioural tab bar in Compose has no optics at all and has been stale since June.

We ship one component. The list worth adding, in order of how often a real app needs it: a
navigation bar, a bottom sheet, a slider whose knob becomes glass during the drag, a toggle, a
search field, and a floating action button. The slider matters more than its size suggests,
because Apple names it as the canonical case of an element that is a shape at rest and glass
during the gesture, which is exactly the rule our tab bar indicator already implements.

For the tab bar itself, the behaviour Apple ships and we do not: minimize on scroll, a bottom
accessory that moves inline when minimized, and a dedicated trailing search tab.

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

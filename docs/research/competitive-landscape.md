# What everyone else built, read from their source

Not a survey of README claims. Eight repositories were cloned and their shaders read, on
2026-09-10. Where a project's documentation and its code disagree, the code is quoted.

The short version: about thirty projects claim this effect on Android, five do real optics, and
the four that matter all made the same choice we did not. That disagreement is the most useful
thing in this document, so it comes first.

---

## The one that matters: which way the rim samples

A refracting rim can show you what lies **outside** the element, compressed inward, or what lies
**inside** it, mirrored outward. The two look similar over a smooth wallpaper and completely
different over a hard edge. Every implementation has to pick one.

| | Direction | Evidence given |
| :--- | :--- | :--- |
| Kyant0 Backdrop 2.0.1 | inward | none stated |
| Haze `haze-glass` | inward | none stated |
| QWEA0 Liquid-Glass-Android | inward, by default | "matches iOS"; a pixel-measured Control Center replica |
| **this library** | **outward** | Apple's stated architecture, plus the hairline displacement below |

Kyant0's shader reads as outward if you only read the shader: its gradient points away from the
centre and it adds the displacement. The negation happens at the host, one file away, where the
uniform is set to the negative of the public parameter. That is worth knowing before anyone
cites it.

**Why we went the other way, and what would falsify it.** Apple states that the material samples
from an area larger than itself, which only describes outward sampling; inward sampling never
needs a padded backdrop at all. More usefully, the reference gives a discriminating test that a
wallpaper cannot. The held lens overhangs the tab bar by about four points at top and bottom, so
the bar's own hairline falls inside the lens, and the black gap around the bar falls outside it.
Measured on native screenshots:

- the hairline appears about eleven pixels further from the lens's edge inside the lens than
  outside it, which requires sampling from beyond the rim;
- the black gap, which is entirely outside the lens, appears as a dark band about twenty pixels
  wide just inside the lens's rim, which inward sampling cannot produce at all.

This is not a claim that QWEA0 measured badly. It is a claim that a tile floating over a
wallpaper is a poor experiment for this question and a lens overhanging a hard-edged bar is a
good one. Anyone can rerun ours: overhang a glass element past a high-contrast boundary and see
which side of the boundary ends up inside the rim.

---

## Who does what

| Project | Optics | Geometry | Refraction model | Dispersion | Components |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Kyant0 Backdrop | real | rounded rect only, analytic gradient | profile offset | 7 taps, quadrant-modulated | none, by policy |
| Haze `haze-glass` | real | rounded rect, plus a dome field | profile offset | 2 or 7 taps, quadrant-modulated | none |
| skydoves Cloudy | real | positional lens centre | profile offset | yes | none |
| QWEA0 | real | rounded rect, CPU-rasterised custom shapes | inverse-power falloff | per-channel scale | tab bar, dialog, FAB |
| PrismalAGSL | real | rounded rect | edge refraction | yes | button, slider, tab bar |
| liquify | real | rounded rect, smooth-min groups | profile offset | 7 taps | catalog only |
| elyesmansour tab bar | none | — | — | — | the tab bar |
| **this library** | real | **any path** | **exact Snell** | **uniform along the rim** | tab bar |

---

## Four things they do better

**A dome field for elongated shapes, from Haze.** Every implementation hits the same problem: in
a capsule, the distance-to-edge field collapses along the medial axis, its gradient flips, and a
seam runs down the spine. We solved it by widening the gradient's epsilon and fading the lens
toward the axis, which costs refraction strength exactly where a wide pill needs it. Haze
replaces the field instead. Past an aspect ratio of 1.5, and once the refraction band approaches
the inradius, it blends the signed distance into a *dome* distance built from a smooth
rectangular radius that is zero at the centre and one on every edge, with an analytic gradient
everywhere. No seam, no fade, no lost strength. This is better than our answer and we should
adopt it.

**An analytic gradient for the common case, from Kyant0.** Our surface normal is a central
difference, four extra field evaluations per pixel, because the field can be an arbitrary path.
For a rounded rectangle the gradient has a closed form, and Kyant0 uses it. They also evaluate
it against an inflated corner radius, capped at the half-size, which rounds off the direction
discontinuity where the flat run meets the corner. We should take both, on a fast path, and keep
the difference-based normal for paths.

**A local bulge under the fingertip, from QWEA0.** A Gaussian centred on the touch point that
pulls the sampling toward the finger, on top of the global rim refraction. Apple describes the
material illuminating from within, starting under the fingertip; QWEA0 also displaces there. It
is a few lines and it is the most obviously *alive* thing in their demo.

**Tint as absorption plus scattering, from QWEA0.** Most implementations, ours included, mix
toward a tint colour, which flattens the backdrop's own light and shade. They multiply by the
tint, which preserves that structure, and add a small scattering term scaled by how dark the
backdrop is, so the hue still reads over black. That is closer to what a coloured transparent
medium does.

---

## Four things we do that nobody else does

**Exact Snell refraction.** Every other implementation multiplies a height profile by a strength
constant and calls the result a displacement. None of them computes a refraction angle. Ours
solves the tangent-difference identity exactly. The practical difference is at the rim, where
the small-angle shortcut overshoots badly, and it is why our band can be pushed deep enough to
move a hairline a third of its width without the outermost pixels smearing.

**Dispersion that survives at the top and bottom of a pill.** Both leaders scale the colour
split by the product of the two centred coordinates, normalised. That expression is zero
wherever either coordinate is zero, which is to say along the entire vertical and horizontal
centre line. On a tab bar lens, that is precisely the top and bottom of the rim, where the split
is most visible in the reference. Ours is uniform along the rim. The research directory rejected
the quadrant form on argument before we knew two shipping libraries had it.

**Blur inside the shader, keyed to distance from the edge.** Kyant0 and Haze both blur the
backdrop in a pass before the lens, so the rim bends already-destroyed detail. The reference's
most recognisable property is a rim sharper than the middle, showing a compressed but legible
image of what lies outside. That is unreachable once the input is pre-blurred. Ours blurs inside
the shader with a radius keyed to depth, so the rim keeps its detail.

**Arbitrary shapes.** Kyant0 throws on anything that is not a corner-based shape. Liquify drops
refraction rather than render it wrong. Haze is rounded rectangles with a dome variant. QWEA0
rasterises custom shapes on the CPU. Ours evaluates a signed distance field sampled from any
path, on the GPU.

---

## What their code says about the 2026 material

Haze keeps a dated research note concluding that the 2026 revision adds a darkened edge and
brighter speculars. Apple's own Platforms State of the Union says the same, and adds better
diffusion of busy content. Both of those are now reachable here through
[`GlassStyle.edgeShadow`](../api-reference.md) and the `Ios27` tab bar preset, with the numbers
tagged as ours rather than measured, because the captures this library was tuned against predate
the revision.

QWEA0's lighting comments are per-angle readings off iOS 26 screenshots, and they independently
match what this library already does: two angular lobes of **equal peak**, the far side being
internal reflection off the back wall rather than a weak fill; a lobe narrow enough to halve by
thirty degrees off-axis; a bright hairline about a pixel inside the edge; a soft inward glow on
the lit side only; and no dark band inside the edge anywhere. Our `counterLight` at 1.0, our
`specularPower` at 4, and our `innerShadow` at 0 were arrived at separately from the same kind
of measurement on a different app. Two independent measurements agreeing on a lighting model is
the strongest corroboration anything in this directory has.

They also flag one defect in ours. Their note is explicit that there is no direction-independent
term in the edge line, so the sides, where the normal is perpendicular to the light, go to zero
and leave no fixed outline. Our edge line carries a floor of 0.07 that does exactly what they
warn against. Our own measurements said the same thing and we did not act on it: the reference
lens has a bright line top and bottom and nothing but a one-pixel dark step at the sides. See
the roadmap.

---

## Not competitors

Google. Material 3 Expressive contains no glass material, there is no backdrop modifier in
androidx, and the president of the Android ecosystem publicly ruled it out for Pixel. What Google
did ship is plumbing: Android 17 QPR2 makes a long-hidden render node method public that applies
an effect to the pixels already drawn behind a node. Haze already uses it behind a flag. It is a
free, correct backdrop with no capture cost, and it does no refraction, dispersion or specular
whatsoever. When it is widely available, backdrop capture stops being anybody's advantage and
optics plus components are all that is left.

The View-system libraries, the abandoned ones, the shader galleries, and the roughly thirty
single-commit repositories are not in this document. Neither are the wrappers around Apple's own
API on other platforms, which only run on Apple's platforms by construction.

---

## Sources

Read from source: [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) ·
[chrisbanes/haze](https://github.com/chrisbanes/haze) ·
[skydoves/Cloudy](https://github.com/skydoves/Cloudy) ·
[QWEA0/Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android) ·
[styropyr0/PrismalAGSL](https://github.com/styropyr0/PrismalAGSL) ·
[3bdul7akim/liquify](https://github.com/3bdul7akim/liquify) ·
[iyinchao/liquid-glass-studio](https://github.com/iyinchao/liquid-glass-studio) ·
[elyesmansour/compose-floating-tab-bar](https://github.com/elyesmansour/compose-floating-tab-bar)

All are Apache-2.0 or MIT. Nothing from any of them is vendored here; what was taken is
described above and reimplemented.

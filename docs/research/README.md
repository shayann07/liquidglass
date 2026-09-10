# The research behind the material

Apple ships Liquid Glass and documents almost none of it. There is no published shader, no
kernel, no filter graph, and **not one numeric rendering parameter** anywhere in the HIG, the
API reference, or WWDC25 sessions 219, 284, 323 and 356. The single number Apple gives is the
clear variant's 35% dimming layer.

What Apple does publish is architecture and direction of effect: that the material bends light
rather than scattering it, that it samples an area larger than itself, that it adapts to what is
behind it, that inversion is a whole-element decision, that objects materialize rather than
fade. Those claims are load-bearing and they are enough to build from — but only if you know
which of them are real, which are marketing, and which third-party reimplementations have got
wrong.

That is what this directory is.

## What was actually run

A 528-agent research workflow: five parallel derivations (Apple's own material, physical optics,
existing reimplementations, motion, legibility), then adversarial verification of every
individual claim, then synthesis. **27.6 million tokens. 135 claims survived verification; 39
were refuted and discarded.**

It was run deliberately **after** the material already worked on a device, as a check rather
than as a source. That ordering is the reason it was worth anything: it reached the same
architecture independently, which is evidence the architecture is right, and where it disagreed
it disagreed specifically enough to act on. Run first, it would have produced a shader nobody
had ever seen render.

## The files

| File | What it is |
| :--- | :--- |
| [optical-model.md](optical-model.md) | The layer-by-layer model, as the shader runs it. The core document. |
| [parameters.md](parameters.md) | Every constant, with a provenance tag: `[apple]`, `[measured]`, `[derived]` or `[ours]`. |
| [motion-model.md](motion-model.md) | Merge, morph, materialize, press, scroll response, highlight motion. |
| [divergences.md](divergences.md) | Eleven places Android cannot match Apple, and why. Read this before promising anyone parity. |
| [confidence.md](confidence.md) | How far to trust each claim, and what observation would falsify it. |
| [competitive-landscape.md](competitive-landscape.md) | What the other implementations actually do, read from their source rather than their READMEs. Four of them disagree with us about which way the rim samples. |
| [reference-measurements.md](reference-measurements.md) | The one first-hand source: numbers read off a screen recording of a real iOS 26 tab bar. Where it disagrees with `parameters.md`, it wins. |
| [synthesised-shader.agsl](synthesised-shader.agsl) | The reference shader the run produced. **Not what ships** — see below. |

## How to read the provenance tags

They matter more than the numbers.

- **`[apple]`** — stated by Apple. Applied to directions and rules, never to a number, because
  Apple states no numbers.
- **`[measured]`** — from a third party who compared side-by-side against a real iOS 26 device.
  The most trustworthy numbers here, and there are few of them.
- **`[derived]`** — follows from optics or geometry. Checkable on paper.
- **`[ours]`** — a taste constant with no external source. Most of them.

A number tagged `[ours]` is not worse than one tagged `[measured]`; it is differently
justified, and knowing which is which is what lets you change it without guessing.

## What the shipped library took, and what it did not

`synthesised-shader.agsl` is the reference, not the implementation. The shipped shader is
[`GlassShaderSource.kt`](https://github.com/shayann07/liquidglass/blob/main/liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/GlassShaderSource.kt).

**Adopted**, because the reasoning held and the device agreed: the wide-epsilon SDF gradient and
its axis-confidence fade; the superellipse bevel with exact Snell deviation; the C1 seam fade;
the device-matched displacement magnitude; the corrected dispersion direction and strength; the
mirrored edge band; the Schlick rim; per-element inversion; and Apple's stated zeros for the
clear variant.

**Already present independently**, which is the interesting part: displacement-first ordering,
outward sampling, the padded backdrop, in-shader scatter tapering to zero at the rim, the
squared rim/interior crossfade, the nineteen rotated taps, tint as tone mapping, and the lit
edge. The run also identified both host-side bugs the implementation had already hit — the
opaque-fill requirement and the coordinate-space hazard — as things any Android port must solve.

**Not adopted**: the gyroscope coupling, because Apple's own claim is hedged to "in some cases"
and the material ships on platforms with no motion sensor at all; and the seven-tap spectral
dispersion reconstruction, because it is a sevenfold fetch cost for a fringe that a device
comparison says is nearly invisible.

## The honest caveat

This is a reconstruction from public statements, physical optics and other people's
reimplementations. It is not Apple's implementation and nobody involved has seen Apple's
implementation. Where it is right, it is right because the physics and the stated behaviour
constrain the answer; where it is a guess, [confidence.md](confidence.md) says so.

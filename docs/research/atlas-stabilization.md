# Atlas stabilization: continuous optics and separate motion roles

This is an authored refinement informed by the owner's original iPhone captures. It does not replace
historical measured presets or claim recovery of Apple's private rendering implementation.

## Evidence changed the model

The previous Atlas magnifier reused `Held`, a navigation lens whose material and ink intentionally
sample different maps. Its central foreground zoom and separate edge band produced the recurring
split-face appearance when used as a free lens. That was a role mismatch, not merely an excessive rim colour.

The original Phone T04 sequence also contradicts transferring the free search-button pull model to
navigation: the selector travels horizontally, while its own off-axis squeeze was an inference. The new
calm preset assigns anchored strain to the bar and leaves travel/pressure in the selector model.

The [stabilization record](https://github.com/shayann07/liquidglass/blob/main/review/ATLAS-STABILIZATION.md)
contains the original T04 frame, T01 sequence, native desktop before/after images, line-grid proof and
machine-readable test results. The T01 audit's approximate 225px held height over a 186px bar informs a
conservative 1.23-height test bound. It does not identify finger distance or timing. The adopted 1.16
height and slower springs are explicitly authored choices.

## Why the magnifier is continuous

For normalized squared radius `r²`, the source offset is `-p*m*(1-r²)²`, with `m=1-1/zoom`.
At the ellipse boundary, the displacement and its first derivative vanish. The radial source derivative
remains positive for the supported 1–2.5× centre zoom: source detail is neither reversed nor skipped in
an internal annulus. Both production shader passes include the same mapping function.

Rendered ramp tests cover multiple sizes and zooms. A separate endpoint test compares material and ink
at every sampled pixel. These tests prove properties of this implementation; they do not prove pixel
identity with an iOS screenshot. The [porting guide](../porting.md) states the same source/alpha contract
for other renderers.

## Motion is not one scale animation

Press growth, illumination, drag resistance, selector centre and selector spine have separate states.
The calm whole-surface preset uses critical geometry springs, limits press growth and progressively
resists drag before integration. Large-surface strain stays subtle. Ordinary labels retain layout
coordinates, so dragging a long glass bar does not rotate its text.

The calm selector responds to horizontal travel distance through centre speed and a separate spine
spring; perpendicular travel contributes neither trace-free strain nor velocity squeeze. Release retains
state continuity. Historical expressive dynamics remain an explicit comparison path.

## Public sources and limits

Apple describes interactive glass, illumination and material roles in
[Meet Liquid Glass](https://developer.apple.com/videos/play/wwdc2025/219/) and distinguishes Clear/Regular
usage in its [materials guidance](https://developer.apple.com/design/human-interface-guidelines/materials).
Those sources provide design guidance, not the numerical spring constants used here. Pixel comparisons
must also account for source content, density, colour handling and time alignment.

The native desktop evidence uses Direct3D, not an assumed Vulkan pipeline. Its static redraw timings
are not presented-frame or Android performance. No new physical device capture was available. Full 1:1
parity, native ports for every framework and the historical strict endpoint gate remain open work.

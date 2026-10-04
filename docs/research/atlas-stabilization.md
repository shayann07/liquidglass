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
conservative 1.23-height test bound. It does not identify finger distance or timing. The adopted 1.20
settled-height ratio stays within that reference's tolerance. An initial 1.16 candidate measured
215.76px against 225±4px and was rejected by the original-reference test. The slower springs remain
authored choices; reducing drag sensitivity does not justify shrinking the settled hold.

A separate whole-bar audit decodes original PTS 20819/600 and 29863/600 from `IMG_6756`. Four
unoccluded columns and three edge thresholds give a 186px resting bar and 204–205px extreme held
bar. Growth is mostly above its resting top edge; the bottom changes only about one pixel. The
current centre-anchored response is therefore conservative, not a matched reconstruction. Its
portable 72-direction extreme-pull regression peaks at 194.2524px against the measured 205px ceiling.
This bound prevents excess deformation; it does not identify a finger gain or close the asymmetry gap.
Full-density original crops, PTS, thresholds and the reproducible measurement tool are in the
stabilization record. No spring or deformation constant was refitted in this audit.
An intervening ordinary-held frame at PTS 21019/600 measures 196px with symmetric growth. The
extra extreme deformation is only 8–9px beyond that hold, separating it from press expansion.
Generic Calm press is 191.58px at this geometry. A fail-first reference regression therefore rejected
that amplitude for the navigation bar. Its preset now reuses the earlier rounded 1.05 scale, applied
only to material, giving **195.3px against 196±1px**. Generic cards and controls retain 3%/2dp growth.
The bar's timing and drag coefficients are unchanged. With this press, both Kotlin and portable
geometry remain below the 205px extreme ceiling, peaking at **198.017px** in the reference-sized
fixture. Ordinary-held amplitude now fits; extreme asymmetry and the additional 6–7px deformation
remain unmatched. Passing an upper bound alone cannot establish a correct drag model.

The Calm integration also accidentally added generic press brightness on top of the navigation
bar's existing held lift. A rendered fail-first test measured 54/255 against the original 44±1
interior plateau. Keeping the bar's held lift and setting its extra `pressLift` to zero restores
44/255 without changing motion or generic controls. A fresh untouched patch of the decoded
ordinary-held reference is uniformly 43/255, within that tolerance. Touch illumination remains
separate; this plateau test does not score the fingertip glow or HDR display appearance.

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
state continuity and travel-driven spine deformation while pressed material fades. The inherited model
zeroed shape-driving velocity on release, making throws rigid; the calm preset now separates these roles.
Historical expressive dynamics remain an explicit comparison path.

Reapplying the original resting-tap recovery gate to Calm found a missing phase: a long tap stretched
but approached rest monotonically. Its shape damping is now 0.45 (from 0.72), while its maximum target
half-spine gain falls from 0.55 to 0.42 slots. The centre and press springs are unchanged. In the
1150×186px five-slot fixture, four-slot travel peaks at 450.63px width (previously 457.64), briefly
compresses to 238.08px and returns to 252px. The original 6690–6701 stills support the ordering of
stretch, compressed arrival and recovery; they do not establish those numerical gains or elapsed time.

Input targets are integrated on their timestamps in the calm path. Previously, multiple pointer
samples between display frames overwrote the preceding target, making the same gesture depend on
presentation cadence. The new reversal/throw regression compares 30, 60, 90 and 120Hz against the
same 120Hz input stream, within 1.5px geometry and 0.015 formation. This is simulation consistency,
not a measurement of display latency. After a stationary hold, the frame loop resumes from the latest
input time rather than replaying the idle interval as frozen frames.

An additional end-anchor regression found that raw horizontal finger speed still excited the selector
after its centre had exhausted its available travel. Alternating outward and diagonal pulls beyond
the same end changed its width by 72.47px and height by 7.30px in a 360×64px fixture, while its centre
was stationary. Calm now derives shape-driving speed from the same bounded target used by grasp
tracking. Further motion outside a saturated anchor adds no fictitious travel; returning to the tab
range resumes the ordinary deformation. Both end anchors report zero width/height change in the
corrected test. Whole-bar feedback and raw navigation intent remain separate. This is a causal
interaction correction, not a new fitted Apple speed or timing parameter.

The portable Skia adapter executes these same production shaders outside Compose. On the current software
ramp fixtures, all four material profiles differ from JVM Skia by at most one channel level out of 255.
This is a backend agreement measurement, not an iOS equivalence score or a browser GPU benchmark.

## Endpoint precision and native clipping

The legacy endpoint gate remains 1.4256 output levels against its strict one-level target. Its
material and ink are first combined in an 8-bit layer, then rounded again after coverage. The new
Desktop Calm path keeps those inputs separate until the final float composition and coverage pass.
Across the same 18 page/ink/body fixtures, its maximum error is **0.9569** levels (199,788 visible
pixels, including 11,124 fractional-coverage pixels). The independent oracle starts from the
material and ink outputs and evaluates source-over in double precision. It does not measure optical
agreement with Apple or the subsequent display framebuffer's rounding.

CanvasKit's separate-input test also passes: maximum error 0.9668 levels across rounded, asymmetric
and pose outlines, including 1,376 fractional-coverage pixels. Native validation caught a failed
first integration: sampling the stored ink through a shader offset let the GPU cull it outside the
window. Declaring that offset in the image-filter graph restores the selected label. The regression
now exercises the actual graph with viewport clipping, and storage rows must stay transparent.
Android retains the historical compositor because its current Java API does not expose the required
multiple dynamic inputs. This precision improvement is enabled only for Desktop Calm navigation.

See the [host compositor contract](../porting.md#combining-refracted-ink-with-glass) and the native
before/candidate/corrected captures in the stabilization record. A direct source shader and an
image filter have different input-bounds requirements; compilation alone cannot establish equivalence.

## Public sources and limits

Apple describes interactive glass, illumination and material roles in
[Meet Liquid Glass](https://developer.apple.com/videos/play/wwdc2025/219/) and distinguishes Clear/Regular
usage in its [materials guidance](https://developer.apple.com/design/human-interface-guidelines/materials).
Those sources provide design guidance, not the numerical spring constants used here. Pixel comparisons
must also account for source content, density, colour handling and time alignment.

The native desktop evidence uses Direct3D, not an assumed Vulkan pipeline. Its static redraw timings
are not presented-frame or Android performance. No new physical device capture was available. Full 1:1
parity and native ports for every framework remain open work. The strict endpoint gate now passes
for separate-input desktop/CanvasKit composition; the historical and Android path remains above it.

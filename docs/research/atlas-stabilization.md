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

A foreground audit also finds that the visible unselected Contacts icon changes from 69×69px at
rest to 73×73px on ordinary hold and 69×76px at extreme (threshold 200). Its bounds centre moves
11.5px upward from ordinary to extreme hold at all three tested thresholds. This is visible-pixel
evidence, not identification of layout motion versus optical/content warping. The current implementation
keeps layout and ordinary labels fixed; that choice does not itself prove matching visible output.
The review includes the original crops, threshold results and a Pillow-only reproduction tool.

A follow-up samples 47 native timestamps across the same T04 gesture. Its bar starts and ends at
186px with the same visible centre, holds at 196px, then reaches 205–206px with the centre 10.5–11px
higher during the prolonged drag. All 141 threshold/frame combinations have a strict majority of
unoccluded columns for both boundaries. The asymmetry persists within one gesture; it is not an
offset between recordings. The earlier 205px gate remains a conservative chosen-frame bound, not
the complete recording's maximum. The review supplies the plot, raw votes, original crops and an
exact decode/measurement command.

Central-row width probes also reject the earlier 872.98×198.02px drag shape. The clear late T04
crop is 833px wide and 206px high. Navigation now opts into an authored area-preserving log-strain
model, giving 835.49×204.70px against rounded 834±4 × 205±1.1px bounds. The height gate retains the
earlier selected frame; against the late 833×206px frame, residuals are +2.49px width and -1.30px
height. This is a conservative multi-frame fit, not exact same-frame parity. Generic cards keep the
size-adaptive law. The original's 10.5–11px visible-centre rise is still unmatched: fitting width and
height does not establish correct asymmetric geometry. The 0.047 strain bound is a spatial fit,
not identification of Apple's physical or input parameters. Full local and hosted verification at
`124ae17` passed: 303 library tests plus 27 skipped, both Atlas tests, 13 portable math/controller
tests, 12 Skia tests and five browser tests. The review includes fresh native and software gesture
captures, raw redraw timings and the remaining asymmetry.

A further candidate checks top and bottom separately against the selected original frame. The
centred model failed at181.15/385.85px; a bounded material-only bias gives170.73/375.43px against
171–172/375px. Bias is capped at5.6% of the short side (10.416px here), below the selected10.5px
shift. Layout, hit targets and ordinary icon pixels stay fixed. Other directions use an authored
extrapolation; timing and the original foreground warp remain unmatched. Twenty focused Kotlin
checks,13 portable math/controller checks and13 Skia checks pass; full verification is pending.
The review records failure-before evidence and actual software gesture captures separately from
prior completed native/CI runs.

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

An earlier correction reapplied the original resting-tap recovery gate to Calm and found a missing phase: a long tap stretched
but approached rest monotonically. Its shape damping is now 0.45 (from 0.72), while its maximum target
half-spine gain falls from 0.55 to 0.42 slots. The centre and press springs are unchanged. In the
1150×186px five-slot fixture, four-slot travel peaks at 450.63px width (previously 457.64), briefly
compresses to 238.08px and returns to 252px. The original 6690–6701 stills support the ordering of
stretch, compressed arrival and recovery; they do not establish those numerical gains or elapsed time.

A subsequent audit of original `IMG_6698` found that this correction still changed height too late.
Six unoccluded column scans give 171–173px height near 49% of the trip; the model gave 162.96px.
Optical formation and pressure had two consecutive slow springs. Calm now drives transit pressure
directly from the existing movement target through one authored critical spring (45/s). Its spine
gain is 0.36 slots because restoring cap height also adds width. The same spatial-phase test now gives
**329.74×171.36px**, against rounded gates of **330±12 × 172±4px**. The height-only candidate failed
the width gate at 344.81px; both dimensions must pass together. Optical formation, held/released
pressure and historical presets are unchanged. Untimed stills constrain spatial shape, not frequency.
At the separate 1150×186px fixture, long-travel peak/rebound are now 429.27/232.87px before settling
at 252px. These supersede the earlier model outcomes above, not the reference measurements.
The [review record](https://github.com/shayann07/liquidglass/blob/main/review/ATLAS-STABILIZATION.md#resting-tap-midpoint-height-was-late)
includes the unscaled original, pixel probes, reproduction command and failed candidates.

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
Its high-level `drawSurface` now builds rounded in-app material and the wide tone kernel. Eighteen
parameter fixtures generated by the real Kotlin factory guard appearance/tint drift. An independent
sinusoidal blur check measures 0.6137 amplitude against the Gaussian prediction 0.6176; coordinate
tests include negative local samples. The tone cache recognizes native-canvas aliases, so repeated
JavaScript wrappers do not force a blur each frame. These adapter tests do not establish native
framework support, identical host compositing or live-backdrop performance.

An edge audit then found transparent-black contamination with small opaque sources: a 1×1
`(64,128,192,255)` image produced `(4,8,11,15)` in the wide buffer. Extending the image with clamped
shader sampling **before** blur corrects it. Five source sizes, three sigmas and five locations each
now preserve the original constant within one code value; 12 portable Skia tests pass. The same
independent sinusoid check measures 0.6119 after correction, within 0.6176 ± 0.04. The earlier
surface revision also passed four hosted browser tests; its labelled captures are in the review.

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

# Astra implementation status

Astra is the selected development approach for the unreleased Liquid Glass parity work.
It combines original iPhone reference measurements, independently tested corrections and
useful techniques assessed from the Fable and Antigravity implementations. Review of
[Sam Asante's web implementation](https://github.com/samasante/liquid-glass) is also informing
the investigation. Competitor appearance and passing internal tests do not establish iOS parity.

## Current development: Atlas stabilization

The current source adds `GlassScene`, `GlassStyle.clearLens`, `GlassInteraction.Calm` and
`GlassTabBarStyle.Calm()`. Free magnifiers share one continuous backdrop/ink map. Navigation assigns
off-axis squeeze to the anchored whole bar, while its selector deforms with along-bar travel and throw.
Input is integrated by timestamp and the held height is checked against the original Phone reference.
The bar separately matches ordinary-held growth (195.3px against 196±1px) and keeps full-screen pulls
below the measured 205px extreme. Its duplicated held-brightness contribution has been removed.
Extreme asymmetric growth, exact timing and rim colour still differ; these are not parity claims.

Atlas Studio is the active sample, with native Windows screenshots and desktop gesture tests.
The [current verification record](https://github.com/shayann07/liquidglass/blob/main/review/ATLAS-STABILIZATION.md)
reports failures, corrected runs, original frames, authored timing and remaining gaps. Its Windows
redraw figures are not Android performance. Vitals and the following physical captures are historical.

For other stacks, [porting](porting.md) covers the dependency-free WebGL preview and the production
CanvasKit shader kit. A high-level painter draws clear lenses without manual uniform binding; full
native framework adapters and automatic backdrop capture are not implemented.

## Historical follow-up: r14

R14 separates large-surface drag strength from the already accepted touch expansion. Small
controls keep their prior response; larger cards and pills receive much subtler drag strain.
[Usage and parameter provenance](generic-interaction.md#separate-touch-expansion-from-drag-stretch)
explain the shared policy. [R14 verification](https://github.com/shayann07/liquidglass/blob/main/review/ASTRA-r14.md)
uses terminal-driven desktop rendering; no new Android physical or performance claim is made.
The following r13 material comparisons remain historical evidence, not r14 device results.

## What is implemented

- A two-dimensional pose selector with continuous hold, drag, squeeze and release.
- Separate resting-tap recovery, with a larger shape response for longer travel.
- A resting capsule that fits compact layouts without moving navigation anchors.
- Native selected ink and boundary refraction using a shared source-coordinate system.
- Opt-in press/pull behavior for standalone glass and unchanged Legacy defaults.
- Lifecycle, cancellation, second-pointer and long-uptime handling.

For the current development source, opt in using `GlassTabBar(..., style = GlassTabBarStyle.Calm())`.
`V3()` retains the earlier response for comparisons.
The app supplies the backdrop through `LiquidGlassState`; see [Tab bars](tab-bar.md).
Unreleased snapshot versions require building and publishing the matching library locally.
They are not advertised as available on Maven Central.

## Historical verification and open limits

R13 was the last physical-device delivery in this sequence. It removes whole-control translation and tilt
from generic Pullable glass. Material drawing deforms around a fixed centre; normal foreground
and pointer coordinates stay anchored. Long bars stay level and round controls retain diagonal
stretch. [Generic interaction](generic-interaction.md) explains the shared integration.
The [final review](https://github.com/shayann07/liquidglass/blob/main/review/ASTRA-r13.md)
records fresh validation, physical comparisons, performance and remaining limitations.

R11's side-recovery change was subsequently captured on physical Pixels; r12 repaired
clipping and full-screen overpull, and r12.1 added viewport constraints. R12.1 still moved
and tilted the entire Live bar, which the owner rejected. R13 supersedes that behavior.
R12.1 passed 247 library tests; passing that suite did not catch the rejected visual behavior.

R10 is reducing held-bubble sensitivity following owner feedback: both the speed response
and maximum squeeze/stretch are lower, while small movements remain continuous. Off-axis
strain is also reduced. Resting-tap motion, press expansion and standalone glass behavior
are unchanged. Thirty focused motion checks, a fresh full suite of 241 library tests and
17 app tests pass. Eight matching gesture scenarios were captured for r9.1 and r10 on the
same Pixel 7 ending 6ZF, with actual input timestamps and native frame PTS. R10 was left
installed and its pulled APK matched the labelled build. These gains are authored, not
recovered Apple constants.

Two alternating same-6ZF r9.1/r10 timing pairs measured 16.83/16.23 ms median and
24.07/24.55 ms p95 HWUI completion latency. This batch passes the +2 ms median and 1.10x
p95 regression limits. It does not prove a speed improvement or long-term thermal stability.

The preceding r9.1 checkpoint passed 237 library JVM tests and 17 Vitals tests.
Eight physical gesture scenarios, a lossless r8.1/r9.1 Phone comparison and two alternating
r8/r9.1 timing pairs were captured on Pixel 7 serial ending FT8. The final installed APK
was pulled and hash-verified. The preceding r8.1 checkpoint includes compact layout checks.

The raised rim's native peak is now 91 versus about 94 in the original Phone frame. Its
colour and side profile still differ. R9's isolated render initially used the wrong fixture
defaults; r9.1 explicitly uses the production edge-lighting and colour parameters. R9's full
suite had seven coroutine timeouts; the unchanged gesture assertions passed both an isolated
recheck and the fresh r9.1 suite. The failed run remains in the local evidence record.

Full 1:1 parity is not yet established. The rim/source map is being refined; exact original
pointer timing is unidentified. In these historical device revisions, the strict one-code-value endpoint
gate is above its limit (1.4256). An internal tolerance of 1.5 must not be read as a strict pass.
The later separate-input Desktop Calm path measures 0.9569 on the same gate; it does not change
Android's compositor or retrospectively upgrade these device results. See the
[current research update](research/atlas-stabilization.md#endpoint-precision-and-native-clipping).

In the FT8 pairs, r9.1 measured 15.60 ms median and 26.47 ms p95 HWUI completion latency,
versus r8's 15.72/26.12 ms. This batch passes the +2 ms median and 1.10x p95 regression
limits. R8.1's earlier batch on a different Pixel failed the median limit; it is retained,
not combined with these measurements. These are neither photon latency nor pure GPU time.
Optional glass cards remain off by default because their earlier cost was high.

Original private captures, actual input timestamps, native video PTS, labelled APK hashes and
exact source archives are retained locally for review. The final review links the validation and separately preserved alternatives. Historical review
revisions are checkpoints, not claims of complete parity.

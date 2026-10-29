## 29 October: XML interop takeover locally qualified

`codex/xml-interop-takeover` from `1fb561c`: clickability and grouped opacity fixed; full JVM 411 passed/27 skipped, physical S24+ 37/37, API 32 and 28 focused 3/3 each. Original defects reproduced by negative controls. Native/Compose interior RGB MAE 0.417/255; 24 calibration images unchanged. Fresh complete docs2 and Search motion runs: all 4,194 frames byte-identical to the freshly rendered merge base; zero motion trace differences. Decoded iOS measurements and inherited brightness gap recorded. See `review/atlas/android-xml-takeover-2026-10-29/README.md` for exact scope and source hashes. Owner authorized packaging/pushing the verified candidate for Claude's independent review and merge. No release authorized. Primary-checkout optical experiments untouched.

# LiquidGlass / Atlas progress and recovery

Updated 2026-10-12. **Goal active: full 1:1 iOS parity is not established.**
Read this first after an interruption. Detailed history lives in the
[review](../review/ATLAS-STABILIZATION.md) and [verification ledger](../review/atlas/verification.json).

## Current state

**27 October video-model pass 5 verified (Argon sessions 17-30):** all 30 sessions in the Argon suite are now fully verified and recorded in audit sections U and V. Confirmed and filed: fast scrub optical bridge across the inter-control gap to the search button (#119, #107; Y=44-68 green streak, iOS pure black), settled dark-mode selection pill bottom rim chromatic fringes (#111), search button two-stage press onset (16.7 ms glyph dimming 255 -> 133-142 preceding 133-150 ms radial expansion), trailing-edge pinning (pinned at x=491 on left pull, y=357 on up pull) and single-frame unlit release flash (#125). Issue checklists updated via `gh issue`.

**26 October squircle lens (#93):** the held lens's outline blends .7 toward a superellipse n = 3 in the pose
shader and the CPU query (`uPoseE`; agreement test at 0, .5, 1); width profile against IMG_6756 35.5 s 27.5 →
7.1 px rms. Ports regenerated and set `uPoseE`. Pixel 7 not connected, device check pending.
[Evidence](../review/atlas/lens-squircle-2026-10-26/README.md).

**26 October refraction and fringes (#73 #77 #92 #94):** audit of 102 differences filed as #92-#104; then
the held lens: band 1.1, seamless held map (`heldSourceSmooth`), split proportional to the shift, spectral rim,
selector split 0.08/0.14 (ink 0.3), transit stretch (mirror 0.3, gain 0.15, corners 0.2, fold back on). Rim pull
+10.8 vs iOS +10.9 px (was -2.2); fringe energy 0.9-1.4x iOS on four recordings, LOLL8185 0.2-0.6x. Pixel 7
checked by screenshot. [Evidence](../review/atlas/fringe-refraction-2026-10-26/README.md).

**24 October #78 web demo preload (cloud session, merged 26 Oct):** `.wasm` downloads start with the
page where Wasm GC exists; Skia ready 0.34-0.35 s sooner on emulated 4G, same unthrottled; transfer and
failure states unchanged. Shader compile not measurable here (software GL); brotli is the owner's call.
[Record](../review/atlas/web-demo-load-2026-10-24/README.md).

**24 October #81 off-composition field (cloud session, merged 26 Oct):** size-only rebuilds of
custom-path fields run on `Dispatchers.Default` with the previous field kept until published; the
first field and outline/origin changes stay synchronous. 7 new tests; `FieldAllocationBenchmarkTest`
pixels identical; full suite 437/410 passed/27 skips/0 failures. No device run, no ABI run locally
(no Android SDK). [Record](../review/atlas/pathfield-off-composition-2026-10-24/README.md).

**24 October #73 rim fringe score (measurement only):** new scorer
`tools/measure_rim_fringe.py` (private twin `.local/sxs/fringe.py`) counts off-ink chromatic pixels in the bar per
frame, iOS (HDR10 decoded) against the replay of the same inferred input, with page/badge hues excluded, a
4:2:0 codec control and a threshold sweep. All five recordings: iOS fringed in 22-90 % of frames, ours in 0-3 %;
peak E per touch ours/iOS 0.00-0.03; iOS grows 5-30x with speed, peaks at lift, fades +110-120 ms. docs/1, docs/2
re-rendered at `dc79d5b` (pixel-identical to p20); the other three use p20 (the JVM ran out of commit memory while
other builds ran). No optic changed. Record: `review/atlas/rim-fringe-score-2026-10-24/README.md`.

**24 October takeover of open PRs and issues:** #83, #84, #85, #86 merged (the maintainer rebased
#84-#86, corrected #84's test counts, made #85 "Part of #78" and kept that row open, and fixed #86's
pad floor so a fuse's neck margin is never held: `GlassPadContractTest.aFuseMarginIsNeverHeld`).
Dependabot #87/#88 merged after checking the Gradle checksum and action SHAs. #89 (slider RTL) fixed:
two RTL tests red before, green after, material3 22/22,
[evidence](../review/atlas/material3-slider-rtl-2026-10-24/README.md). #81: EDT without per-neighbour
closures; `FieldAllocationBenchmarkTest` pixels identical to the baseline, p50 ratio to the unchanged
baseline 0.84-0.85 after vs 0.93-1.05 before (512x256, 1080x300, 1080x720; desktop JVM, n=25 each).

**20 October web demo first load (#78, PR #85):** a loader replaces the black first load (shown in
0.05-0.2 s instead of 7.6 s unthrottled / 30 s on slow 4G, headless Chrome, cold caches); Compose mounts
in `#app`; no-Wasm-GC, failed-download/startup-error and lost-context states show on the page. Download
(4.46 MB gzip, 3.28 MB of it skiko.wasm) and first-visit shader compilation are unchanged, so the time to a
usable app is unchanged. Chromium only. [Evidence](../review/atlas/web-demo-load-2026-10-20/README.md).

**20 October first tap after install (#71):** traced on a Pixel 7: the selector's pad followed its
deforming body, so its layers were reallocated mid-tap and the first new size compiled a 265-286 ms GPU
pipeline for the glass shader. `GlassPadFloor` holds the largest pad; the first tap's slowest frame is
67-91 ms (was 326-352), cold start unchanged. The library baseline profile is removed (no measurable gain).
[Evidence](../review/atlas/first-tap-fix-2026-10-20/README.md).

**20 October Material 3 polish (#79, PR #84):** slider and range-slider tracks span the thumb's whole
travel; they stopped half a thumb short of each end (19 dp, derived from the authored 38 dp thumb;
`GlassSliderTrackTest` measured 19 px at density 1 before the fix, red before and green after). The
showcase's mini player and wide-window hero are fixed (authored layout values). Regression pass: material3
suite 20 of 20, ABI unchanged. Every component on the page, overlays included, inspected on desktop, a
Pixel 7 and in the browser. [Evidence](../review/atlas/material3-polish-2026-10-20/README.md).

**20 October CodeRabbit follow-up to `1654e90`:** removed the second rounding of `recordPad`;
the field now preserves the shader's fractional origin. Red regression: expected layer width 145,
actual 146 at scale 0.75. Green focused run: 21 tests/probes, zero failures/errors/skips in 41s.
Scales 1/0.75/0.6/0.5/0.25 match uncached reference fields exactly; within-grid style changes reuse
the field. Added inspected Skia field-texel evidence and documented the affected function contracts.
Full suite: 428 tests, 401 passed, 27 opt-in skips, zero failures/errors (28m45s). Android sample,
Wasm, ABI, strict MkDocs and all 139 repository links pass. No assertions or timeouts changed.

**20 October PR #83 review follow-up (`1654e90`, pad rounding superseded above):** capped retained storage at 256k pixels (~4.25 MiB);
key and build now use the same whole-pixel rounded size/pad. Added root-region replacement,
sub-pixel reuse/boundary and oversized/busy-pool regressions, explicit capability propagation
and direct coroutines dependency. All 22 focused tests/probes pass in 1m28s. Full renderer run:
427 tests, 400 passed, 27 opt-in skips, zero failures/errors (one worker, 27m04s). Android sample,
Wasm compilation, Kotlin ABI and strict docs/link checks pass.
Updated Java allocation reductions: 72.5% for a small pooled field, 30% for large temporary builds.
CPU medians are slower in this run. Docs now describe the implemented state and track #81's
bounded-resolution/off-composition follow-up separately.

**20 October initial external-report fixes (`c1d2b5a`, superseded pool cap):** adds reference-counted remembered view
leases (including abandonment), actual field-geometry keys, capability gating and bounded EDT
scratch reuse. Two original reproductions failed before the fix; focused regressions pass.
Paired large-field probe: about 72.5% less Java allocation after pool warmup, pixels unchanged in
the measured cases, no Android FPS claim. Android sample, Wasm and ABI checks pass. Full-suite
verification recorded 393 passes, 27 opt-in skips and three coroutine timeouts under four renderer
workers. All three pass on an unchanged-source single-worker rerun, along with the packaged
benchmark and inspected cache-field plot. The initial full invocation remains recorded as failed.
Evidence: `review/atlas/compose-cache-field-2026-10-20/README.md`.

**13 October documentation/pause checkpoint:** README and the documentation now provide a
current visual/evidence index, native.NET guide, updated platform/performance/limit statements,
Atlas diagnostic commands and changelog. Future changes must update documentation and inspected
visuals in the same checkpoint. Strict site/link checks and desktop/narrow rendered previews pass;
all ten b46207d runtime checks are green, including native Windows control capture. The owner
requested a pause once these docs are published. NEXT-SESSION records the exact resume point.

**13October native control backdrop:** the new WinForms adapter and executable example capture
real Panel/Label/TextBox/CheckBox content for the production painter. Native glyph updates,
identity/refraction, resize, thread ownership and failed-capture retention pass. Known unsupported
RichTextBox/ActiveX fail explicitly; custom control/Z-order limits remain. See the
[native-control packet](../review/atlas/dotnet-controls-2026-10-13/README.md). Alltenaf333fa checks
pass; currentadapterhosted verification followspublication. This closes a concrete host path,
not arbitrary-stack support. Return next to the original Video1 shape/optical mismatch.

**13October native.NET interaction:** reusable timestamped CalmInteraction plus optional
material-only painter feedback and real WinForms card input now pass144portable trajectory
states,107520outline points,12native check groups and9native phases. Labels/hit targets stay
anchored; release/recovery/reduced-motion pixel checks pass. See the
[native interaction packet](../review/atlas/dotnet-calm-2026-10-13/README.md). FullP1 remains open:
navigation-selector travel, arbitrary-widget capture and additional native hosts are missing.
Allten04d9016 hosted checks pass; this implementation's ownCI follows publication.

**Later13October resumed investigation:** allten required cea1913 checks pass; hosted XML has
344library passes27optional skips and3Atlas passes. The87ms first-input profile is inconclusive.
Another natural33-phase replay passes with zero external input/focus changes. Presentation trace
is unavailable because Windows denied ETW permission; no permission changes made. Renderer is
unchanged. See the [diagnostic evidence](../review/atlas/frame-cost-2026-10-13/README.md). Full A1
and selection reliability remain open; next independent implementation work is native.NET input.

**13October usage-limit handoff:** sample-only performance instrumentation is uncommitted;
production library remains cea1913. All local queues terminal. First natural replay failed
bottom-right selection; repeat passed33phases with536replay inputs and no external input/focus
loss. Failure cause remains unresolved. Repeat has87ms maximum input dispatch, requiring follow-up;
picture recording/submission are not presentation timing. Full handoff and backup paths are in
NEXT-SESSION.md. Exact-head hosted build38320819316 was still in progress at save time.

**13October long-tap checkpoint:** area+axial appearance and critical pigment recovery pass
all371library cases (344pass/27optional skips),3Atlas cases andAndroidassembly. All43NVIDIA
native phases inspected; rest/recovery match the preceding wide-input renderer exactly.
The original remaining-suite run had one V3gesture1minute timeout; same-source isolated retry
passed27.451s at the unchanged deadline. The failure/retry and exact case inventory are retained.
All local queues terminal. See the [long-tap proof](../review/atlas/long-tap-appearance-2026-10-12/README.md)
and NEXT-SESSION.md. Finite colour agreement is calibrated on allthree episodes, not independent
full-parity evidence. First-use redraw771ms, static wide-input cost and remaining TODOs stay open.
The preceding ebc12d3 revision passed allten hosted contexts; newer revisions need their own CI.

**12October verified recovery:**342library cases pass,27optional skips,3Atlas cases pass and
Android assembly succeeds; all43native phases/static inspected. The original run was interrupted;
330completed records were recovered,4focused cases reused and8required cases finished separately.
Exact369-case inventory and source hashes match. All queues terminal. Fullparity and the long-tap
opacity error remain open; controlled NVIDIA comparison found about3ms additional median submission cost.


**Latest handoff:** [NEXT-SESSION.md](NEXT-SESSION.md) contains the exact dirty-source state,
saved failures and resume commands; [TODO.md](TODO.md) preserves the complete delivery checklist.
The new JVM wide-input correction passes13 focused cases and removes the seam in all inspected
NVIDIA replays. The complete local test inventory and3Atlas cases now pass; all queues are
terminal. See the12October verified recovery above for exact totals and interruption provenance.
The long-tap optical mismatch remains open.

**Latest verified local short-tap checkpoint:** two consecutive adjacent-tap episodes expose a timing
error hidden by the previous single-frame shape gate. An authored0.75 adjacent-trip rate reduces
globally aligned centre RMS12.82/15.67px to2.55/2.54px, while long trips retain their previous
rate. Coupled shape rates/drives share the clock, and slower-tap settling now requires actual
contour velocity to be quiet. **333 library cases passed,27 optional skips,zero failures; both Atlas tests passed and Android
assembled.** The interrupted run supplied322 recovered passes; the exact unfinished cases passed
in a3m15s follow-up. The33-phase native replay and a separate10-phase adjacent-tap replay passed
and were inspected, along with the static studio. All local queues are terminal. This verifies a
checkpoint, not full iOS parity or owner acceptance. Read the
[short-tap evidence and failure history](../review/atlas/short-tap-timing-2026-10-10/README.md).
Previously completed full/native results below belong to the preceding runtime.

**Optical defect now measured:** the same tap's selector interior reaches50–52 RGB levels on
a51-level bar, while current shader probes give20–36 at the sampled formation strengths. The
visible Contacts icon is79–80px versus a69px rest; the central shader test disk remains69–71px
at those strengths and reaches78px at full strength. The [tap optical audit](../review/atlas/tap-optics-audit-2026-10-11/README.md)
records raw pixels, current shader output and limits. This establishes another remaining mismatch,
not an accepted appearance. The separate appearance candidate below addresses part of this mismatch; unknown hidden backdrop prevents
changing the earlier bar-lift calibration from these values alone.

**New local optical candidate:** a separate tap pigment/ink response improves the short sequence
while retaining held-band strength and whole-bar behavior.36focused checks plus actual79×79px
ink rendering pass; full local verification has338library passes/27optional skips and Android
assembly. Three split Atlas checks pass. The [candidate packet](../review/atlas/tap-optics-candidate-2026-10-11/README.md)
also preserves the independent long-tap failure: late opacity still differs by up to23.55 RGB
levels under nominal input. Native review also found an inherited vertical selector seam.
The new wide-input correction removes it in native replays; its complete local regression inventory now passes.
These changes form a development checkpoint; the long-tap counterexample prevents full optical acceptance.

**Earlier endpoint/material checkpoint, 10 October:** endpoint recovery follows the leading edge
while the body changes width, and the resting inset tint is limited to the actual bar shape.
The owner rejected the preceding dark-disc images; that visual sign-off remains withdrawn.
The corrected native images preserve the resting inset exactly and remove the dark exterior
caps on both backgrounds. A thin glass rim remains during recovery.

- [Material correction and rejected/current native images](../review/atlas/inset-material-2026-10-10/README.md).
- [Leading-edge motion calibration and remaining curvature error](../review/atlas/phone-edge-2026-10-09/README.md).
- [Consecutive source measurements in both directions](../review/atlas/phone-landing-sequence-2026-10-09/README.md).
- **329 library cases passed, 27 optional skips, zero remaining failures; both Atlas tests passed;
  Android assembled.** The full run initially failed one stale uniform inventory and an Android
  binding overload. Their targeted repair passed in 2m30s; unchanged renderer cases were reused.
- Native 21-phase replay passed selection/layout checks in 2m13s; all images inspected.
  Six focused material/coordinate checks and 20 portable Skia tests plus strict types passed.
  Native phase captures do not prove continuous timing or presented FPS. First-held redraw
  1647.7ms remains a diagnostic outlier, not an accepted performance result.
- No local verification process remains running. Sessions27266/50471 are terminal. Do not repeat
  these completed suites. Exact reports and source hashes are in the material packet/ledger.
- Endpoint/material correction **5a81393** passed its full hosted verification. Native integration
  **9545506** and font/notice correction **49c217f** are pushed. All ten required checks pass on
  **49c217f**, including all three CodeQL languages. CI build38s /
  Gradle17s, unchanged library and Atlas tests FROM-CACHE; source reports retained separately.
- [Expanded native replay](../review/atlas/all-gestures-2026-10-10/README.md):33 phases cover all eight
  app-window boundaries, held reversals and middle landing/recovery; selection/layout checks and
  both Atlas tests pass. All images inspected. Concurrent capture/tests prohibit performance claims.
- [Native .NET integration](../review/atlas/dotnet-2026-10-10/README.md):painter, owned/borrowed backdrop
  paths and runnable Windows Forms host. Ten native check groups pass; four JVM profiles match
  exactly. Host builds and app-owned drag/resize captures pass. Both Windows/Linux CI pass on49c217f;
  both artifacts inspected. Linux's initially blank labels are fixed by a font provider and
  guarded by a visible-text check. CanvasKit NOTICE drift is repaired and its check passes.
- Canonical checkout: `D:/Work/AndroidStudioProjects/SideProjects/kmp/liquidglass`.
- Branch: `codex/atlas-stabilization`; [draft PR #6](https://github.com/shayann07/liquidglass/pull/6).
- Previous baseline **e597b59** has all ten required checks passing. Hosted verification for the
  new pushed short-tap checkpoint **44f01de** failed CI38125880804 only on the combined Atlas test's
  six-minute timeout; its333library cases pass. Docs38125880909 and CodeQL38125880910 pass. The
  local test split retains all coverage and passed; hosted confirmation awaits the next revision.
- Commit/push/final merge authorized; no releases, tags or blanket PR6 review bypass.

The improved width/edge trajectory does not establish complete optical parity. Right/left width
RMS is2.31/2.39px and centre RMS1.08/1.28px; the visible right arc RMS is2.40px. Partial-cap radius
still recovers too early, with maximum errors12.59/11.58px. Original input timing, other gesture
classes, presented-frame performance and additional native integration paths remain open.
Historical checkpoint totals and rejected experiments are retained in the verification ledger,
review packets and live research log rather than repeated here.

## New primary motion recordings

The owner supplied `docs/1.MP4` (tap-only) and `docs/2.MP4` (hold, early/held throws, extremes and
soft/hard landings). Both hashes and metadata are verified. Raw files stay local and are excluded
from Git/MkDocs. **Read the [live research log](../research/analysis/MOTION-2026-10-08.md) next.**
Every decoding step, uncertainty, measured episode and model change is to be recorded there as it
happens. These labelled recordings supersede speculative gesture classification in older captures.

## Latest findings

**New recordings:** [failing baseline packet](../review/atlas/new-motion-2026-10-08/README.md) saved.
Native RGB probes give long-tap396x159 and short-tap329x204 inside186px outer-bar geometry.
The unchanged controller misses paired shape even across authored40..240ms touch alternatives.
Tap-only is the owner's class, not a known finger duration. Full contour/timing fit remains open;
The new local candidate springs projected area and derives radius from its actual spine, allowing
shorter trips to become rounder while longer trips flatten. Its three Phone pairs now pass the
declared spatial gates, alongside older five-slot midpoint/recoil gates. These are authored
spatial calibrations, not measured Apple rates. Read the live log's final entries for all failures
and the current validation state. Exact3180cec all seven hosted checks passed; library/Atlas
FROM-CACHE of verified0e5c1d6, build53s. That is the research baseline, not the new implementation.


**Diagnostics:** forced redraw re-entered the Compose playback coroutine, causing duplicate input
and a failed selection assertion. Static and motion diagnostics now use the plain Swing dispatcher
with capture/pointer guards. The earlier122.8ms timing is provisional, not a proven renderer defect.
Corrected snapshot/control/static runs all passed. Readback perturbs timing; neither submission
durations nor phase screenshots establish presented FPS or native input latency.
[Correction, rejected trace and inspected images](../review/ATLAS-STABILIZATION.md#native-diagnostic-correction-isolate-redraw-from-playback).

**Held motion:** current model reaches original326×174 and314×142 paired shapes. Tap allocation is
independent; all481 resting-tap samples retain the prior curve. Ordinary hold and selector off-axis
ownership are unchanged. Additional IMG6727/6729 shape pairs pass the existing6px gate without
retuning; IMG6728 width is unresolved because an icon contaminates the edge. These are spatial
checks, not recovery of one original trajectory or full contour.
[Additional original audit](../review/ATLAS-STABILIZATION.md#additional-held-shapes-audit-the-frozen-model).

**Release provenance:** fresh exact-PTS LOLL8185 crops still contain a refracting Calls lens
where the inherited E7 record labels trailing rest. Its314/367ms release attribution is withdrawn;
the historical spring's source comment is corrected, without changing its value.
[Original evidence and rejected assumption](../review/ATLAS-STABILIZATION.md#release-attribution-the-assumed-rest-still-contains-a-lens).

## Requirements still open

| ID | Requested outcome | Verified work / remaining gap |
| --- | --- | --- |
| M1 | Distance-dependent tap, hold and throw | Adjacent-tap timing improves across two episodes; endpoint width/edge recovery and inset tint corrected. Repeated short-tap width still misses by up to20.64px under nominal input. Full contour/optical timing across all gestures remains open. |
| M2 | Whole-bar squeeze, subtle corner extremes | Shared bounded transform and all eight native window-boundary snapshots; fixed layout/hit targets. Exact iOS envelope/optical behavior across directions remains incomplete. |
| M3 | Calm generic controls | Existing card limits, accessibility and gesture ownership verified. Preserve them through further changes. |
| O1 | Continuous glass without a split rim | New JVM wide-input candidate removes native selector seam and passes a fail-first deferred-layer regression. Local verification complete; full Apple optics and Android endpoint precision remain unproven. |
| A1 | Atlas UI and credible performance | Redesigned studio, native phase captures and stable input checks. Controlled wide-input comparison adds about3ms median submission; presented-frame performance, owner acceptance and physical-device verification remain. |
| P1 | Easy integration across UI stacks | Compose/CanvasKit scenes plus native .NET painter and runnable WinForms host. More native hosts, .NET motion and arbitrary-widget capture remain incomplete. |
| D1 | Open-source documentation | README, usage, research and evidence maintained; keep statements tied to the actual verified commit. |
| C1 | Free CI, licensing and protection | Apache-2.0/NOTICE, CodeQL, Dependabot and ten protected checks configured, including both .NET platforms and C#. Final review/merge remains after implementation acceptance. |

## Resume in this order

1. Read `NEXT-SESSION.md`, `TODO.md`, `WORKSPACE.md`, this file, `git status` and `.local/active-goal-checkpoint.txt`.
2. Inspect **existing live handles first**. Full library baseline and native diagnostic sessions
   58306/55336/50321 are finished. Observation timeout does not mean a job stopped; never duplicate it.
3. Exact0e5c1d6 hosted build37822979856 is finished and all required checks passed; reports inspected.
   Reuse its evidence. Inspect the latest checkpoint for the next pushed revision and live run.
4. Next M1 action: follow the final next-action section of the live log. Both new videos are
   indexed; first tap counterexamples and unchanged production baselines are saved. Do not resume the old T04 timing fit ahead of these labelled inputs.
   For older material, select a clean continuous original contour sequence with native timestamps,
   verify its edge track and compare production output. JGEY2190n176–182 is **not an isolated tap**;
   inherited rim/glyph tracks are neither reliable release contours nor finger trajectories.
   LOLL8185CFR1944–1988 is now also rejected as calibrated isolated release: its assumed rest
   retains a visible lens. Find a true resting endpoint before fitting a release time.
   Additional stills do not justify fitting timing. Reuse the current traces; do not rerun full
   tests for unchanged geometry or documentation. If a proposed motion change is unsupported,
   record the rejected hypothesis and proceed to another concrete M1/O1/P1 gap.
5. At each completed change save the exact revision, test result, evidence, failed attempts and
   next action in the review/ledger and local checkpoint. Keep this overview short; history stays there.

Work alone using the private wrapper, terminal commands and app-owned captures: **no subagents,
computer-use tools or Vitals work**. No physical device is available; later use requires the shared
lease. Do not publish private transcripts, credentials, raw desktop grabs or unrelated windows.
Passing regression tests does not complete the whole goal.

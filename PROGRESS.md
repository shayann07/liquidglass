# LiquidGlass / Atlas progress and recovery

Updated 2026-10-09. **Goal active: full 1:1 iOS parity is not established.**
Read this first after an interruption. Detailed history lives in the
[review](review/ATLAS-STABILIZATION.md) and [verification ledger](review/atlas/verification.json).

## Current state

**Latest verified local checkpoint, 10 October:** endpoint recovery follows the leading edge
while the body changes width, and the resting inset tint is limited to the actual bar shape.
The owner rejected the preceding dark-disc images; that visual sign-off remains withdrawn.
The corrected native images preserve the resting inset exactly and remove the dark exterior
caps on both backgrounds. A thin glass rim remains during recovery.

- [Material correction and rejected/current native images](review/atlas/inset-material-2026-10-10/README.md).
- [Leading-edge motion calibration and remaining curvature error](review/atlas/phone-edge-2026-10-09/README.md).
- [Consecutive source measurements in both directions](review/atlas/phone-landing-sequence-2026-10-09/README.md).
- **329 library cases passed, 27 optional skips, zero remaining failures; both Atlas tests passed;
  Android assembled.** The full run initially failed one stale uniform inventory and an Android
  binding overload. Their targeted repair passed in 2m30s; unchanged renderer cases were reused.
- Native 21-phase replay passed selection/layout checks in 2m13s; all images inspected.
  Six focused material/coordinate checks and 20 portable Skia tests plus strict types passed.
  Native phase captures do not prove continuous timing or presented FPS. First-held redraw
  1647.7ms remains a diagnostic outlier, not an accepted performance result.
- No local verification process remains running. Sessions27266/50471 are terminal. Do not repeat
  these completed suites. Exact reports and source hashes are in the material packet/ledger.
- Preparing commit/push of the verified work. Current preceding HEAD/origin **4a2c50f** passed all
  seven required hosted checks. Use the local checkpoint for the subsequent revision/run.
- Canonical checkout: `D:/Work/AndroidStudioProjects/SideProjects/kmp/liquidglass`.
- Branch: `codex/atlas-stabilization`; [draft PR #6](https://github.com/shayann07/liquidglass/pull/6).
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
from Git/MkDocs. **Read the [live research log](research/analysis/MOTION-2026-10-08.md) next.**
Every decoding step, uncertainty, measured episode and model change is to be recorded there as it
happens. These labelled recordings supersede speculative gesture classification in older captures.

## Latest findings

**New recordings:** [failing baseline packet](review/atlas/new-motion-2026-10-08/README.md) saved.
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
[Correction, rejected trace and inspected images](review/ATLAS-STABILIZATION.md#native-diagnostic-correction-isolate-redraw-from-playback).

**Held motion:** current model reaches original326×174 and314×142 paired shapes. Tap allocation is
independent; all481 resting-tap samples retain the prior curve. Ordinary hold and selector off-axis
ownership are unchanged. Additional IMG6727/6729 shape pairs pass the existing6px gate without
retuning; IMG6728 width is unresolved because an icon contaminates the edge. These are spatial
checks, not recovery of one original trajectory or full contour.
[Additional original audit](review/ATLAS-STABILIZATION.md#additional-held-shapes-audit-the-frozen-model).

**Release provenance:** fresh exact-PTS LOLL8185 crops still contain a refracting Calls lens
where the inherited E7 record labels trailing rest. Its314/367ms release attribution is withdrawn;
the historical spring's source comment is corrected, without changing its value.
[Original evidence and rejected assumption](review/ATLAS-STABILIZATION.md#release-attribution-the-assumed-rest-still-contains-a-lens).

## Requirements still open

| ID | Requested outcome | Verified work / remaining gap |
| --- | --- | --- |
| M1 | Distance-dependent tap, hold and throw | Consecutive endpoint width/edge recovery improved and dark exterior tint corrected. Full contour/optical timing across all supplied gestures remains open. |
| M2 | Whole-bar squeeze, subtle corner extremes | Shared bounded material/ink transform; fixed layout/hit targets; original vertical asymmetry audited. Horizontal ink drift and exact optical mechanism remain. |
| M3 | Calm generic controls | Existing card limits, accessibility and gesture ownership verified. Preserve them through further changes. |
| O1 | Continuous glass without a split rim | Standalone continuous lens and endpoint regressions passed. Full Apple optics and Android endpoint precision remain unproven. |
| A1 | Atlas UI and credible performance | Redesigned studio, native phase captures and stable input checks. Presented-frame performance, owner acceptance and later physical-device verification remain. |
| P1 | Easy integration across UI stacks | Compose scene and typed CanvasKit scene share a backdrop with multiple surfaces. More native bindings and automatic host backdrop capture remain absent. |
| D1 | Open-source documentation | README, usage, research and evidence maintained; keep statements tied to the actual verified commit. |
| C1 | Free CI, licensing and protection | Apache-2.0/NOTICE, CodeQL, Dependabot and seven protected checks configured. Final review/merge remains after implementation acceptance. |

## Resume in this order

1. Read `WORKSPACE.md`, this file, `git status` and `.local/active-goal-checkpoint.txt`.
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

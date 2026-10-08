# LiquidGlass / Atlas progress and recovery

Updated 2026-10-08. **Goal active; full 1:1 iOS parity is not established.**
Read this after an interruption. This file contains the current state; the
[review record](review/ATLAS-STABILIZATION.md) and [verification ledger](review/atlas/verification.json)
preserve detailed measurements and earlier attempts.

## Current checkpoint

- Canonical checkout: `D:/Work/AndroidStudioProjects/SideProjects/kmp/liquidglass`.
- Branch: `codex/atlas-stabilization`; [draft PR #6](https://github.com/shayann07/liquidglass/pull/6).
- Last fully verified library implementation: **`820659d`**, paired held-travel correction; all seven required checks passed.
- Portable scene **`014b16e` verified**: 20 Skia tests, 13 portable math tests, strict TypeScript
  consumers and all five hosted browser tests passed. All seven required checks passed.
  One scene combines the painter and calm controller; examples use it directly.
- Current arrival-recoil change **`8d9917a`: full local, inspected native and all seven required hosted checks passed**.
  Original paired width/height recoil now appears without changing the existing midpoint fit.
  Inspect `.local/active-goal-checkpoint.txt` and the actual process before starting another run.
- Commits/pushes authorized. No release, tag or implicit PR #6 review-rule bypass.

## Verified checkpoint: held travel

Original selectors measure 313.5×142px (IMG6735) and 326×174px (IMG6734), inside a 186px bar.
The verified prior model could not flatten enough: its selected-width state was 313.66×215.46px.
The new candidate reaches **314.03×144.19px** and **326.03×173.93px**, with a narrower maximum
spine and a concave travel response. Ordinary hold growth and off-axis ownership remain unchanged.

An initial one-frame fit failed the intermediate-width check. A second defect was caught by an
explicit regression: lowering the held gain also clipped resting taps through their shared safety
limit. Tap allocation now keeps its previous allowance, independently of held tuning. All 481
tap samples match the previous curve to 0.001px; the verified arrival/midpoint values are restored.

**Local, native and hosted verification complete at 820659d.** The full local run finished in 17m16s:
312 library tests passed, 27 optional showcases skipped, zero failures/errors; both Atlas tests
passed and Android assembled. All execution was fresh. Sessions 44574 and 42724 are finished.
The [native Atlas capture](review/atlas/held-travel-native.png) was inspected at 1920×1051 Direct3D.
Static redraw submission: 8.1551ms median, 13.4829ms p95, 54.0905ms maximum (120 samples / 10 warmups).
This is not presented FPS, motion capture or a controlled speedup. Failed candidates, two cropped
originals and the computed comparison are preserved in the [held-travel record](review/ATLAS-STABILIZATION.md#held-travel-two-shapes-instead-of-one-frame).
These untimed stills are calibration evidence, not measured timing or independent validation.
[Hosted run 37770922905](https://github.com/shayann07/liquidglass/actions/runs/37770922905) passed all seven
required checks. Fresh library: 312 passed / 27 skipped / zero failures in 23m43.86s; fresh Atlas:
both passed in 4m51.41s. Build job 30m52s; Gradle 30m18s. Reports and logs inspected.

## Current follow-up: native motion evidence

Atlas now has an opt-in own-window input playback mode. Sixteen native phase snapshots were inspected;
tap/drag/corner selection and fixed layout assertions passed. [Contact sheet and method](review/ATLAS-STABILIZATION.md#native-motion-phase-captures).
Each phase uses a fresh replay because synchronous readback costs 1.35–1.52s here. This is not a
continuous video or presented-FPS result. The first scene-switch redraw took 122.8ms; other sampled
redraws took 8.4–31.9ms. Profile that initial cost before claiming smoothness.

The tooling/shared-sample change passed both desktop regressions (181.033s) and Android assembly
in a fresh 3m37s run. Library source is unchanged from 820659d, so its full suite was not repeated.
Hosted verification of this follow-up is pending. All local playback/test jobs are finished;
consult the local checkpoint for the latest exact-head hosted run and the next concrete action.

## Completed and verified

| Work | Verification | Evidence |
| --- | --- | --- |
| Calm bar material stretch/narrowing and bounded directional bias | Local and all hosted checks passed at `06c9114`; 305 library passed, 27 skipped; 2 Atlas passed; Android built | [Original bar sequence](review/atlas/reference-bar-sequence.png), [measurements](review/atlas/reference-bar-sequence.json) |
| Navigation material and both ink variants share one drawing transform; layout/hit targets remain fixed | At `302e5e1`: 307 library passed, 27 skipped, zero failures; 2 Atlas passed; Android built; local 10m21s; all 7 required hosted checks passed | [Original foreground audit](review/atlas/reference-foreground-sequence.png), [CI](https://github.com/shayann07/liquidglass/actions/runs/37678836069) |
| Native Atlas after foreground correction | Inspected 1920×1051 Direct3D app capture; 120 static redraw submissions after 10 warmups: median 10.2552ms, p95 12.9977ms, max 21.4913ms | [Screenshot](review/atlas/checkpoint-302e5e1-native.png), [timings](review/atlas/checkpoint-302e5e1-native-timing.json) |
| Original resting-tap midpoint | 329.74×171.36px against 330±12×172±4px; full local/hosted verification recorded | [Reference](review/atlas/reference-6698.png), [scans](review/atlas/reference-tap-midpoint.json) |
| Continuous standalone magnifier map, scene API and portable painter defaults | Render/coverage regressions; one shared backdrop; terminal and hosted tests for bundled Skia shader defaults | [Review](review/ATLAS-STABILIZATION.md), [porting guide](docs/porting.md) |
| Open-source integration and repository maintenance | README/API/research guides, Apache-2.0/NOTICE; free CodeQL/Dependabot and 7 required checks; protected branch | [Contributing](CONTRIBUTING.md), [security](SECURITY.md) |

All 27 skipped JVM cases are opt-in showcase exports, confirmed from their assumption messages;
use `-Pliquidglass.showcase=<output-directory>` when exporting that separate gallery.

Static redraw submission is **not** presented FPS, native gesture latency or a controlled speedup.
The hosted foreground web job initially timed out downloading Ubuntu dependencies. Retrying only
that job passed in 48s; the successful 30m45s build was preserved. All those processes are finished.

## Current change: arrival recoil

Original IMG6694/6701 measure 227.5×174 / 235×164px: normalized width/height 0.968085 / 1.060976.
The old model compressed horizontally without corresponding vertical recovery. Calm now exchanges
spine compression for cap growth using an authored projected-area response; held/throw and historical
presets are unchanged. The existing midpoint remains 329.7385×171.3598px.

- Final negative control with the correction disabled fails the paired-state height gate.
- Corrected matched shape: 222.55×170.41px, ratios 0.967669 / 1.051907.
- 57 broader focused tests passed; the subsequent 3-test arrival class passed, including reverse
  trips, 30–120 Hz playback and reduced motion (two tests overlap the broader run).
- [Comparison](review/atlas/tap-arrival-comparison.png), [reference scans](review/atlas/reference-tap-arrival.json),
  [production trace](review/atlas/tap-arrival-trace.csv), [reproduction tool](tools/measure_tap_arrival.py).
- The chart is computed geometry, not a screenshot. Stills establish neither timing nor peak extrema.
  Rejected models and the corrected overstrict peak assertion remain in the review record.
- Full local verification passed in **10m 20s: 309 library passed, 27 skipped, zero failures;
  both Atlas tests passed; Android assembled**. All execution was fresh.
- [Native Atlas at rest](review/atlas/checkpoint-8d9917a-native.png) inspected: 1920×1051 Direct3D.
  [Static redraw submissions](review/atlas/checkpoint-8d9917a-native-timing.json):
  **11.2165ms median / 19.8045ms p95 / 23.3515ms maximum**, 120 samples after 10 warmups.
  This verifies native launch/rendering; it is not a capture of arrival motion or input latency.
- [Hosted run 37683713350](https://github.com/shayann07/liquidglass/actions/runs/37683713350): **all seven required checks passed**.
  Fresh library execution: 309 passed / 27 skipped / zero failures (17m23.81s); both Atlas tests passed
  freshly (2m55.19s). Build job: 22m19s. This verifies the named contracts, not full iOS parity.

## Remaining requirements

| ID | Requirement | Remaining work |
| --- | --- | --- |
| M1 | Distance-dependent tap, hold and throw | Arrival and paired held shapes verified locally/hosted. Native phases inspected; independent original trajectories/more phases and timing remain unresolved. |
| M2 | Whole-bar squeeze and restrained corner extremes | Current material/ink correction verified; independent trajectories, horizontal ink drift and full optical mechanism remain unresolved. |
| M3 | Generic calm interaction with stable controls | Preserve existing card limits, motion accessibility, hit targets and gesture ownership through subsequent changes. |
| O1 | Continuous glass without a split rim | Current lens continuity tested; complete Apple optical identity and Android endpoint precision remain unproven. |
| A1 | Atlas demonstration and performance | Own-window native gesture phases verified. Profile first scene-switch cost; presented-frame measurements, owner acceptance and later physical-device verification remain. |
| P1 | Easy integration across UI stacks | Typed CanvasKit scene verified in terminal and hosted browser; removes per-widget controller/matrix setup. Other native bindings and automatic host backdrop capture remain absent. |
| D1 | Useful open-source documentation | Keep the README, usage, research and evidence synchronized with actual verified behavior. |
| C1 | PR automation and protection | Keep required checks green; final review/merge when ready, without silently bypassing review. |

## Next steps and resume protocol

1. Read `WORKSPACE.md`, this file, `git status` and `.local/active-goal-checkpoint.txt`.
2. **Inspect existing live handles/logs first.** The full runs `.local/tap-arrival-full.log` and `.local/held-travel-full.log` are finished.
   Do not restart a long run because an observation timed out; do not rerun finished baselines.
3. The arrival full run and native capture are finished. Reuse their evidence; do not restart them.
   Hosted verification has also passed; preserve these completed runs when continuing new work.
4. Portable scene verification is finished at `014b16e` / CI 37732791140. The unchanged JVM/Atlas
   tests were restored from cache; their fresh execution is recorded at `8d9917a`. Do not rerun them
   for documentation-only updates. Check only the latest required-check status before final delivery.
5. Held-travel hosted verification is complete. Native motion playback/local sample checks are also
   finished. Inspect the latest hosted follow-up recorded in `.local/active-goal-checkpoint.txt`;
   do not duplicate completed runs. Profile initial native redraw cost separately from slow readback.
6. Continue M1/O1/P1 from their stated gaps. Passing regression tests does not complete the whole goal.

Use `tools/workspace.ps1` and the private caches described in `WORKSPACE.md`.
Work alone through terminal commands and app-owned captures: **no subagents or computer-use tools**.
Vitals is excluded. No physical device is available; any later device use requires the shared lease.
Never publish private logs/transcripts, credentials, raw desktop grabs or unrelated windows from `.local/`.
At each completed fix, save tests, failed candidates and the next action here and in the ledger.

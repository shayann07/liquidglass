# LiquidGlass / Atlas progress and recovery

Updated 2026-10-08. **Goal active; full 1:1 iOS parity is not established.**
Read this after an interruption. This file contains the current state; the
[review record](review/ATLAS-STABILIZATION.md) and [verification ledger](review/atlas/verification.json)
preserve detailed measurements and earlier attempts.

## Current checkpoint

- Canonical checkout: `D:/Work/AndroidStudioProjects/SideProjects/kmp/liquidglass`.
- Branch: `codex/atlas-stabilization`; [draft PR #6](https://github.com/shayann07/liquidglass/pull/6).
- Last fully verified implementation: **`8d9917a`**, paired arrival recoil and navigation foreground correction.
- New portable scene candidate: **20 terminal renderer tests and strict TypeScript consumers pass**.
  One scene now combines the painter and calm controller. Browser/hosted verification is pending.
- Current arrival-recoil change **`8d9917a`: full local, inspected native and all seven required hosted checks passed**.
  Original paired width/height recoil now appears without changing the existing midpoint fit.
  Inspect `.local/active-goal-checkpoint.txt` and the actual process before starting another run.
- Commits/pushes authorized. No release, tag or implicit PR #6 review-rule bypass.

## Completed and verified

| Work | Verification | Evidence |
| --- | --- | --- |
| Calm bar material stretch/narrowing and bounded directional bias | Local and all hosted checks passed at `06c9114`; 305 library passed, 27 skipped; 2 Atlas passed; Android built | [Original bar sequence](review/atlas/reference-bar-sequence.png), [measurements](review/atlas/reference-bar-sequence.json) |
| Navigation material and both ink variants share one drawing transform; layout/hit targets remain fixed | At `302e5e1`: 307 library passed, 27 skipped, zero failures; 2 Atlas passed; Android built; local 10m21s; all 7 required hosted checks passed | [Original foreground audit](review/atlas/reference-foreground-sequence.png), [CI](https://github.com/shayann07/liquidglass/actions/runs/37678836069) |
| Native Atlas after foreground correction | Inspected 1920Ã—1051 Direct3D app capture; 120 static redraw submissions after 10 warmups: median 10.2552ms, p95 12.9977ms, max 21.4913ms | [Screenshot](review/atlas/checkpoint-302e5e1-native.png), [timings](review/atlas/checkpoint-302e5e1-native-timing.json) |
| Original resting-tap midpoint | 329.74Ã—171.36px against 330Â±12Ã—172Â±4px; full local/hosted verification recorded | [Reference](review/atlas/reference-6698.png), [scans](review/atlas/reference-tap-midpoint.json) |
| Continuous standalone magnifier map, scene API and portable painter defaults | Render/coverage regressions; one shared backdrop; terminal and hosted tests for bundled Skia shader defaults | [Review](review/ATLAS-STABILIZATION.md), [porting guide](docs/porting.md) |
| Open-source integration and repository maintenance | README/API/research guides, Apache-2.0/NOTICE; free CodeQL/Dependabot and 7 required checks; protected branch | [Contributing](CONTRIBUTING.md), [security](SECURITY.md) |

Static redraw submission is **not** presented FPS, native gesture latency or a controlled speedup.
The hosted foreground web job initially timed out downloading Ubuntu dependencies. Retrying only
that job passed in 48s; the successful 30m45s build was preserved. All those processes are finished.

## Current change: arrival recoil

Original IMG6694/6701 measure 227.5Ã—174 / 235Ã—164px: normalized width/height 0.968085 / 1.060976.
The old model compressed horizontally without corresponding vertical recovery. Calm now exchanges
spine compression for cap growth using an authored projected-area response; held/throw and historical
presets are unchanged. The existing midpoint remains 329.7385Ã—171.3598px.

- Final negative control with the correction disabled fails the paired-state height gate.
- Corrected matched shape: 222.55Ã—170.41px, ratios 0.967669 / 1.051907.
- 57 broader focused tests passed; the subsequent 3-test arrival class passed, including reverse
  trips, 30â€“120 Hz playback and reduced motion (two tests overlap the broader run).
- [Comparison](review/atlas/tap-arrival-comparison.png), [reference scans](review/atlas/reference-tap-arrival.json),
  [production trace](review/atlas/tap-arrival-trace.csv), [reproduction tool](tools/measure_tap_arrival.py).
- The chart is computed geometry, not a screenshot. Stills establish neither timing nor peak extrema.
  Rejected models and the corrected overstrict peak assertion remain in the review record.
- Full local verification passed in **10m 20s: 309 library passed, 27 skipped, zero failures;
  both Atlas tests passed; Android assembled**. All execution was fresh.
- [Native Atlas at rest](review/atlas/checkpoint-8d9917a-native.png) inspected: 1920Ã—1051 Direct3D.
  [Static redraw submissions](review/atlas/checkpoint-8d9917a-native-timing.json):
  **11.2165ms median / 19.8045ms p95 / 23.3515ms maximum**, 120 samples after 10 warmups.
  This verifies native launch/rendering; it is not a capture of arrival motion or input latency.
- [Hosted run 37683713350](https://github.com/shayann07/liquidglass/actions/runs/37683713350): **all seven required checks passed**.
  Fresh library execution: 309 passed / 27 skipped / zero failures (17m23.81s); both Atlas tests passed
  freshly (2m55.19s). Build job: 22m19s. This verifies the named contracts, not full iOS parity.

## Remaining requirements

| ID | Requirement | Remaining work |
| --- | --- | --- |
| M1 | Distance-dependent tap, hold and throw | Arrival correction verified; test independent original trajectories and more phases. Original timing is unidentified. |
| M2 | Whole-bar squeeze and restrained corner extremes | Current material/ink correction verified; independent trajectories, horizontal ink drift and full optical mechanism remain unresolved. |
| M3 | Generic calm interaction with stable controls | Preserve existing card limits, motion accessibility, hit targets and gesture ownership through subsequent changes. |
| O1 | Continuous glass without a split rim | Current lens continuity tested; complete Apple optical identity and Android endpoint precision remain unproven. |
| A1 | Atlas demonstration and performance | Native gesture/presented-frame measurements, owner acceptance and later physical-device verification. |
| P1 | Easy integration across UI stacks | New typed CanvasKit scene removes per-widget controller/matrix setup; finish browser verification. Other native bindings and automatic host backdrop capture remain absent. |
| D1 | Useful open-source documentation | Keep the README, usage, research and evidence synchronized with actual verified behavior. |
| C1 | PR automation and protection | Keep required checks green; final review/merge when ready, without silently bypassing review. |

## Next steps and resume protocol

1. Read `WORKSPACE.md`, this file, `git status` and `.local/active-goal-checkpoint.txt`.
2. **Inspect existing live handles/logs first.** The full run `.local/tap-arrival-full.log` is finished.
   Do not restart a long run because an observation timed out; do not rerun finished baselines.
3. The arrival full run and native capture are finished. Reuse their evidence; do not restart them.
   Hosted verification has also passed; preserve these completed runs when continuing new work.
4. Finish browser/hosted verification of the portable scene for its exact head. Save outcomes here,
   in the ledger and PR #6. Reuse the completed Compose tests: this change does not alter Kotlin/shaders.
5. Continue M1/O1/P1 from their stated gaps. Passing regression tests does not complete the whole goal.

Use `tools/workspace.ps1` and the private caches described in `WORKSPACE.md`.
Work alone through terminal commands and app-owned captures: **no subagents or computer-use tools**.
Vitals is excluded. No physical device is available; any later device use requires the shared lease.
Never publish private logs/transcripts, credentials, raw desktop grabs or unrelated windows from `.local/`.
At each completed fix, save tests, failed candidates and the next action here and in the ledger.

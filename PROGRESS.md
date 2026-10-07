# LiquidGlass / Atlas progress and recovery

Last updated: 2026-10-08. **Goal active; full 1:1 iOS parity is not established.**
Read this first after an interruption. Detailed evidence belongs in
[the review record](review/ATLAS-STABILIZATION.md), not in repeated conversation summaries.

## Current checkpoint

**Current candidate: asymmetric material drawing.** A fail-first regression caught top/bottom
edges 181.15/385.85px against the selected original's 171–172/375px. A bounded material-only bias
now gives 170.73/375.43px, with unchanged size. Layout and ordinary icon pixels remain fixed in the
actual Compose render test. Twenty focused Kotlin tests, 13 portable math/controller tests and
13 Skia tests pass. Full library/Atlas/Android verification is running; inspect the live handle in
`.local/active-goal-checkpoint.txt` before starting any further build. This candidate is not yet
covered by the earlier full/native checkpoint below.

**Verified and pushed:** navigation now has a shared area-preserving drag policy. The dimensional
regression failed at 872.98×198.02px, then passed at 835.49×204.70px. Full local and hosted tests
passed for this implementation; fresh native and gesture images are inspected and saved below.
At this earlier checkpoint the visible-centre asymmetry was open. The width gate uses the clear late T04 frame; the
height ceiling deliberately retains the earlier selected frame, not a claim of an exact paired-frame fit.

**Portable integration follow-up:** `createGlassPainter(CanvasKit)` now bundles its production shader
defaults, removing manual shader-file loading. All 13 local Skia tests pass, including exact pixel
equivalence to explicit sources, and the terminal example rendered successfully. At `7906d9d`,
[hosted CI](https://github.com/shayann07/liquidglass/actions/runs/37300220285) passed 13 Skia tests,
13 portable math/controller tests and all five browser tests. The unchanged JVM/desktop tests were
restored from cache; this is not a new Compose run. All seven required checks passed, including CodeQL.

- One canonical checkout, branch `codex/atlas-stabilization`, [draft PR #6](https://github.com/shayann07/liquidglass/pull/6).
- Latest verified implementation: `124ae178e0635b4191257205833ecd5662db9c78`.
- Its [hosted run](https://github.com/shayann07/liquidglass/actions/runs/37237373943) passed all seven required checks.
  Fresh library execution: **303 passed, 27 skipped, zero failures**; **two Atlas tests passed**.
  Twelve Skia renderer tests, thirteen portable math tests and five browser tests passed.
- Full local library/desktop tests and Android sample build also passed in **20m10s**.
- Native Atlas launched successfully: Direct3D, 1920×1051. Static redraw submission median **9.3334ms**,
  p95 **15.1117ms**, max **21.271ms** (120 samples after ten warmups). This is not presented FPS or touch latency.
- Those earlier test and capture processes finished. The current candidate's full run is listed above.
- Evidence/documentation for this checkpoint are in this change. The PR is still draft; no release or merge occurred.

## Requirement tracker

| ID | Requirement | Verified work | Still needed |
| --- | --- | --- | --- |
| M1 | Correct distance-dependent tap, hold and throw deformation | Separate shape/centre response; throws retain shape; end-anchor over-pull does not excite a stationary selector; original tap midpoint now 329.74×171.36px against 330±12 × 172±4px | More independent original trajectories/shape phases; exact timing is not identified by untimed stills |
| M2 | Squeeze belongs on the whole bar; screen-corner extremes stay subtle | Separate selector travel/bar feedback; bounded growth/narrowing; current material-only bias fits selected top/bottom edges, preserves ordinary icon pixels and passes viewport checks | Full verification of the bias candidate; independent trajectories, foreground optical behaviour and exact timing remain unverified |
| M3 | Calm interaction on every glass surface, stable controls | Shared modifier and portable controller; separate press/pull; reduced motion; bounded large-card strain and viewport containment | Preserve generic card behavior while correcting navigation's measured response; verify all affected paths |
| O1 | Remove recurring magnifier split rim | Continuous shared material/ink map; gradient/coverage regressions; native line-grid proof | Full optical identity to Apple is unproven; Android compositor precision still differs |
| A1 | Better Atlas Studio and desktop visual proof | Shared sky/type/grid scenes, lens, note card, navigation; native capture and software gesture captures | Native gesture timing and presented-frame performance, plus later owner visual acceptance |
| P1 | Simple integration in any UI stack | Compose scene API; production CanvasKit painter with bundled shader defaults, rounded surfaces, dependency-free preview and timestamped interaction controller; defaults verified in terminal and hosted browser tests | Packaged/tested native adapters for other frameworks; automatic host backdrop capture is not provided |
| D1 | Open-source README, research, usage, licensing | README, API/porting/interaction guides, provenance and limitation records, Apache-2.0/NOTICE | Keep examples, measurements and status synchronized with each implementation change |
| C1 | Free PR automation and repository protection | Seven required checks, CodeQL/Dependabot, strict updates, review/conversation requirements, no force-push/deletion | Final review and authorized merge when the actual implementation is ready; do not bypass protection implicitly |

Passing tests establish their named contracts, not the complete goal. Do not mark parity complete from green CI.

## Next work, in order

1. **Finish verification of M2's asymmetric material candidate.** A 47-frame original T04 audit shows 186px rest,
   196px ordinary hold, 205–206px prolonged drag, then 186px rest again. The visible centre rises 10.5–11px
   during the same gesture. All 141 threshold/frame probes have majority agreement. The new shared
   `GlassPullShape.AreaPreserving` policy now gives 835.49×204.70px; generic cards stay Adaptive.
   The clean original width is 833px at PTS 31074/600, where height is 206px. Current width differs by
   2.49px and height by -1.30px from that late frame. Timing/input gain remain authored.
2. Inspect the current full run, hosted checks and actual captures. The candidate limits material bias
   to 5.6% of the short side, below the selected 10.5px bound. Its chosen-edge fit is not proof of
   Apple's underlying mechanism, other directions, foreground optics or a matched timed trajectory.
3. Run focused tests first. Once the change is stable, run the full suite **once**, then capture native Atlas
   after the CPU tests finish. Inspect the images, record what each proves, and push the reviewable result.
4. Continue M1/O1/P1 against their actual remaining requirements. A documented portability contract is
   useful but is not a shipped native adapter for every stack. No physical device is currently available.

## Evidence map and rejected assumptions

- [Tap midpoint](review/atlas/reference-6698.png), [pixel measurements](review/atlas/reference-tap-midpoint.json),
  [measurement tool](tools/measure_tap_midpoint.py), `GlassTapPhaseReferenceTest`.
  Before: height 162.96px failed. Height-only correction: width 344.81px failed. Current 329.74×171.36px passes.
  `tapPressureOmega=45/s` and spine gain 0.36 are **authored**, not recovered Apple spring constants.
- [Whole-bar sequence](review/atlas/reference-bar-sequence.png), [native timestamps and votes](review/atlas/reference-bar-sequence.json),
  [measurement tool](tools/measure_phone_bar_sequence.py). Decode command is in the review record.
  This is stronger evidence than an isolated extreme. The older 205px ceiling is a selected-frame bound,
  not the maximum of the entire recording. Do not fit motion to inherited yellow ellipse annotations.
- [Native Atlas](review/atlas/checkpoint-124ae17-native.png), [timings](review/atlas/checkpoint-124ae17-native-timing.json),
  [Atlas corner pull](review/atlas/checkpoint-124ae17-extreme.png), [bar corner pull](review/atlas/checkpoint-124ae17-corner.png),
  [bar upward pull](review/atlas/checkpoint-124ae17-top.png), [ordinary hold](review/atlas/checkpoint-124ae17-held.png).
  The first is a native resting capture; the last two are Compose software-rendered gesture tests.
- [Versioned verification ledger](review/atlas/verification.json) preserves failures and distinguishes fresh/cached execution.
- Historical Astra/Fable/Antigravity presets and evidence remain provenance. Do not restore retired workspaces.

## Resume efficiently

1. Read this file, `WORKSPACE.md`, current `git status`, and the PR's actual head/check state.
2. Check the machine-local `.local/active-goal-checkpoint.txt` for current process handles, dirty experiments
   and exact commands. A file alone is not proof that a process is still running: inspect the live handle.
3. Reuse existing evidence for unchanged code. Do not rerun expensive render suites for documentation-only edits.
4. At every completed fix or new measurement, update this checklist, the local checkpoint and the review ledger;
   commit/push supported work. Include failed candidates and pending checks explicitly.

Use `tools/workspace.ps1` for the private build caches. JDK 21 and Android SDK are described in `WORKSPACE.md`.
Work alone with terminal commands and app-owned screenshots: **no subagents or computer-use tools**.
Vitals is out of scope. No device operations without the shared lease. Do not publish private transcripts,
credentials, raw desktop screenshots or the unrelated-window capture under `.local/`.

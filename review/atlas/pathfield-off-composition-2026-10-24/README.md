# Custom-path fields off composition (#81) — 24 October 2026

`rememberGlassPathField` built the signed-distance field for a custom path inside `remember`, so every
cache miss ran the whole full-resolution transform during composition: 8 ms for a 512 × 256 px panel,
17-19 ms at 1080 × 300 and 43-49 ms at 1080 × 720 on this machine (below). This change moves the
case that repeats, a change of size alone, off composition. Nothing optical changed: the build is the
same function and its output is bit-identical.

## What changed

[`GlassPathField.kt`](../../../liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/GlassPathField.kt):

| What changed | Where the field is built | What the frame draws meanwhile |
| :-- | :-- | :-- |
| first composition | during composition | the new field (no frame without one) |
| size, from an empty size or one with no field (layout reports 0 × 0 first) | during composition | the new field |
| shape, pad, band, density or layout direction | during composition | the new field (the old one is another outline or another origin) |
| size alone, with a field already drawn | `Dispatchers.Default` | the previous field, same origin, old size, until the new one is published |

- The outline is still made on the composition thread (a shape's own code never runs in the
  background); only the rasterisation and distance transform move. The path is copied there, because
  a shape may reuse one mutable `Path` for every size and rewrite it while a build is queued
  (CodeRabbit review on PR #90).
- Sizes requested while a build runs are conflated: the running build finishes and is published, then
  the latest size is built; sizes in between are never built.
- A build still pending when the panel leaves composition, or when its outline changes, is cancelled
  and never published. A cancelled build that already started runs to completion on its worker and
  its result is dropped.
- `GlassPathFieldBufferPool` is unchanged: a background build borrows the shared workspace with the
  same non-blocking `tryLock`, and a build that finds it busy uses its own temporary arrays, so two
  builds never share writable scratch storage (`GlassPathFieldBufferTest.concurrentBuildsMatchIndependentWorkspaces`).
- On Wasm `Dispatchers.Default` is the browser's single thread: the build leaves composition and the
  frame it was requested in, but still runs on the main thread.

## Tests

[`GlassPathFieldOffCompositionTest`](../../../liquidglass/src/jvmTest/kotlin/com/wexpa/liquidglass/GlassPathFieldOffCompositionTest.kt)
(7 tests) replaces the build dispatcher with one that queues work until the test releases it, records what every
composition returns, and compares each published field texel by texel with an uncached build:

- `firstCompositionReturnsTheFieldSynchronously`: the first composition returns the finished field; no
  build is dispatched.
- `sizeChangeKeepsThePreviousFieldUntilTheBackgroundBuildIsPublished`: while the resize is queued every
  composed frame returns the previous field (never null, never a third field); after release the new
  field matches a fresh build exactly.
- `firstRealSizeAfterAnEmptyOneBuildsSynchronously`: 0 × 0 then 100 × 60 builds in composition (this is
  what `LiquidGlass` does before layout); the next resize goes to the background.
- `resizesDuringABuildAreConflatedToTheLatest`: four resizes during one build produce two builds (the
  first and the last), and the last matches a fresh build.
- `shapeAndPadChangesBuildSynchronously`: a new shape and a new pad dispatch nothing and match fresh
  builds immediately.
- `queuedBuildKeepsItsOwnOutlineWhenTheShapeReusesItsPath`: a shape that rewrites one `Path` for every
  size, rewritten while the 140 × 80 build is queued; the published field still matches a fresh 140 × 80
  build. Red without the copy, green with it.
- `pendingBuildIsDroppedWhenThePanelLeaves`: releasing a queued build after the panel left does not
  throw or publish.

Written before the fix for the empty-size and generation cases, two of these failed against the first
draft: the old effect built the new outline once more before being cancelled, and the first real size
after 0 × 0 was sent to the background (a frame with no field). Both are fixed in this change.

One existing assertion changed: `GlassPathFieldCacheRegressionTest.subpixelGeometryReusesFieldUntilRecordingGridInputsChange`
now waits (up to 5 s) for the 100 → 101 px resize to be published before comparing it, since a size-only
change is no longer synchronous. Its texel comparison is unchanged.

## Bit-identical output

`./gradlew -I tools/benchmarks/pathfield.init.gradle :liquidglass:jvmTest --tests '*FieldAllocationBenchmarkTest' --rerun`
asserts pixel equality of `buildPathField` (fresh and pooled) against `Baseline.kt` at three sizes, then
times 25 alternating builds each. Linux cloud container, 4 vCPU Intel Xeon 2.1 GHz, JDK 21. Mode 2 is
the production pooled build; times are p50 per build, in ms:

| Panel (px) | `main` `e5b90b3` | this change | allocated bytes p50 (both) | pixels identical |
| :-- | --: | --: | --: | :-: |
| 512 × 256 | 7.99 | 8.41 | 1,874,288 | yes |
| 1080 × 300 | 17.43 | 19.56 | 10,993,072 | yes |
| 1080 × 720 | 44.68 | 43.26 | 24,258,352 | yes |

The build function did not change, so the differences are run-to-run spread on a shared machine (a
repeat of the same source moved the 1080 × 720 p50 from 43.3 to 48.8 ms). What changed is the thread:
for a size-only change none of this time is spent in composition any more.

## Verification

- Focused: the benchmark above plus `GlassPathField*Test`, `GlassPadContractTest`,
  `GlassFieldPadConsumerTest`: 29 tests, 0 failures (after the path copy).
- Full renderer suite (`./gradlew :liquidglass:jvmTest --rerun`): 437 tests, 410 passed, 27 opt-in skips, 0 failures, 0 errors (22 min 16 s, one Gradle worker; 436/409 before the path copy and its test).
- Kotlin ABI (`./gradlew :liquidglass:checkKotlinAbi`): not run here: the task configures the Android target and this container has no Android SDK. CI runs `build checkKotlinAbi` on the PR. `:liquidglass:compileKotlinWasmJs` passes locally. Every new declaration is
  `internal` or `private`.

## Limits

- No device measurement. How many frames a resize now saves on a phone, and how many frames the rim
  lags a resize, were not measured; the JVM numbers above are build costs, not frame times.
- During a size-only rebuild the previous field is drawn at its old size, so the rim can trail a
  growing or shrinking edge for the frames the build takes (up to ~45 ms for a 1080 × 720 px panel on
  this CPU). This is the trade the issue proposed; a shape or origin change never shows a stale field.
- The first build and shape changes still cost the full synchronous build. A bounded-resolution build,
  the other proposal in #81, is not attempted here, so #81 stays open.

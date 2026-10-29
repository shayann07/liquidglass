## PAUSED at owner request — 29 October 2026

Current local candidate adds transit-only semantic ink share 0 (held ink share remains .3).
Same-input replays of docs1 and JGEY2190 give zero neon pixels in all four #124 windows;
previous 5b955d8 repair still peaked at 98-170. Source iOS also has zero in that mask.
The new blue-ink regression failed before (2 false-green pixels), passes after. All 12
focused shader/material/export checks pass. Fixtures and Skia/npm/React Native copies rebuilt.

IMPORTANT: full-suite 419 passes / 27 skips, 46 Skia and 16 .NET passes belong to prior
5b955d8, BEFORE this transit-ink change. The final candidate still needs the full suite,
remaining-recording checks (docs2/IMG_6756/LOLL8185), physical qualification, and #131
integration checks. It must not be marked merge-ready or claimed full parity.

Resume: finish final port checks, run full JVM suite, replay remaining recordings with
matched scripts and compare held/refraction/fringe/landing behavior. Reuse completed
replays under `.local/qualification-2026-10-29/replays[-zero]`; do not overwrite them.
Claude independently reviews and merges. No tag/release. No active Gradle job at pause.

See [matched sequence measurements](transit-ink-zero-sequences-2026-10-29.json).

# Comb Streaks (#124) and Channel Crush (#108) Fix — 27 October 2026

Issues [#124](https://github.com/shayann07/liquidglass/issues/124) (comb streaks during tap transits) and [#108](https://github.com/shayann07/liquidglass/issues/108) (channel crush at lens rim), from the [iOS parity audit](../ios-audit-2026-10-26/README.md).

## What Was Broken

### 1. Comb Streaks (#124)
During tap transits, green and black vertical combs appeared along the lens's top rim and under labels as the selector travelled and settled.
- **Root Cause**:
  In `GlassContentShaderSource.kt`, chromatic displacement `delta` was computed as:
  `float delta = uInkSplit * W * 0.5 * mirrorZone * mat;`
  With `W = 63.36 px` and transit `uInkSplit = 0.14`, `delta` was ~4.5 px along straight runs even when displacement `shift == 0`.
  When sampling blue accent ink (`0xFF0897FF`, `[8, 151, 255]`), Red was displaced by +4.5 px into empty transparent space (`r.r = 0`), Blue was displaced by -4.5 px into empty space (`b.b = 0`), while Green sampled the stroke core (`gg.g = 151`).
  With `min(half3(r.r, gg.g, b.b), half3(a))`, the output evaluated to `[8, 151, 1]` — pure neon green ghost teeth along icon and text strokes.
  Furthermore, `glassSelectorMaterial` in `GlassTabBar.kt` used full moving dispersion (`0.14`) for `inkDispersion` during tap transit instead of scaling by `GlassSelectorInkSplitShare` (0.3), compounding the split across text features.

### 2. Channel Crush (#108, item 111)
Just inside the rim, blue dropped to 0 for 2 px (olive `(23,31,0)`), while at the bottom rim red dropped to 0 (cyan `(0,29,30)`).
- **Root Cause**:
  When `shift - delta < 0`, unclamped chromatic displacement moved outward past the glass outline into the black background, crushing Blue (at the top rim) or Red (at the bottom rim) to 0.

### 3. Red-Channel Clamp Asymmetry in Initial Fix
An initial clamp attempt in `a254a87` clamped `pushR` and `pushB` to `max(shift - delta, 0.0)` outward displacement.
Because the held lens has negative outward `shift`, clamping red displacement to 0.0 prevented legitimate outward refraction on Red only, breaking optical correspondence and causing 2,576+ non-neutral pixels on a grayscale ramp with zero dispersion.

## What Changed

1. **`GlassShaderSource.kt` and `GlassContentShaderSource.kt`**:
   - Scaled `delta` with `abs(shift)` in the transit fold family:
     `delta = mix(uAberration * 4.0 * abs(shift) * mirrorZone * mat, uAberration * 4.0 * abs(shift) * held * mat, heldFamily);`
     `delta = mix(uInkSplit * 4.0 * abs(shift) * mirrorZone * mat, uInkSplit * 4.0 * abs(shift) * held * mat, heldFamily);`
   - Symmetrically bounded outward displacement by the recorded margin:
     `float dR = mix(delta, -delta, heldOrder);`
     `float maxOutward = mix(0.0, max(uPad, W), heldOrder);`
     `float shiftR = max(shift + dR, -maxOutward);`
     `float shiftB = max(shift - dR, -maxOutward);`
     `pushR = -n * shiftR;`
     `pushB = -n * shiftB;`
     For transit fold (`heldOrder == 0`), outward displacement is clamped to `0.0` preventing sampling outside the bar.
     For held lens (`heldOrder == 1`), outward displacement is allowed into the recorded margin `-maxOutward`, preserving optical correspondence and exact grayscale neutrality when dispersion is zero.
2. **`GlassTabBar.kt`**:
   - In `glassSelectorMaterial`, transit `inkDispersion` (`glassSelectorFold > 0f`) now scales moving dispersion by `GlassSelectorInkSplitShare`:
     `inkDispersion = moving * (fringe?.getOrNull(7) ?: GlassSelectorInkSplitShare)`
3. **Ports Synchronization**:
   - Re-exported portable SkSL shaders: `ports/skia/shaders/material.sksl`, `content.sksl`, `endpoint.sksl`.
   - Updated port fixtures: `ports/skia/fixtures/selector-material.json`, `selector-calm.json`, `profile-2.rgba`.
   - Synchronized Skia selector material: `ports/skia/selector-material.mjs`.
   - Corrected .NET material factory (`CalmSelectorMaterial.cs:121`) where transit `uInkSplit` assigned full moving dispersion instead of `movingDispersion * inkSplitShare` (0.042 vs 0.14). All 16 .NET checks pass.
   - Re-bundled Skia shaders and interaction for npm and React Native ports (`ports/npm/vendor/skia/`, `ports/react-native/vendor/skia/`).
   - Synchronized native shaders in `ports/flutter/shaders/liquid_glass.frag`, `ports/unity/.../LiquidGlassMaterial.hlsl`, and `ports/qt/.../liquidglass.frag`.
4. **Regression Test Suite**:
   - Added `GlassShaderNeutralityTest.kt` with 8 automated tests covering:
     - Neutral grayscale input with zero dispersion (held, transit, resting states).
     - Positive, negative, and zero displacement agreement across R, G, B channels.
     - Contrasting content near the rim and into the outer recorded margin (`uPad`).
     - Dark and light appearances maintaining exact neutrality.
     - Nonzero dispersion preserving intended chromatic fringes.
     - Semantic ink pass neutrality and comb streak prevention.

## Independent verification status — 29 October 2026

The saved PNGs below were introduced at `a254a87`, before the shader repair at `5b955d8`.
They are historical evidence of the original proposal, not captures of the repaired shader.
Independent pixel counting reproduces their crop totals (neon 198 to 36; crushed 172 to 50),
but this does not validate the explanation that the remaining neon pixels are legitimate
anti-aliasing. Fresh candidate replays and original-source comparisons remain required.

At `5b955d8`, eight shader-neutrality tests and all 46 Skia port tests passed independently
on 29 October. The full JVM suite passed: 446 tests, 419 passed, 27 skipped, zero failures/errors
in 20m21s (93 classes). [Independent totals](independent-tests-2026-10-29.json). Hosted CI at remote
`94417f4` fails the .NET material fixture (uInkSplit 0.14 versus expected 0.042000003).
The original remote shader at `94417f4`, compiled with the same test helpers, fails four
of eight neutrality tests (including 2,722 non-neutral pixels in the negative-displacement
case); repaired `5b955d8` passes all eight. Claude must review final code/evidence before merging.

## Historical verification claims (pending independent replay)

### 1. Defect Sequence Evaluation (#124)
Evaluated across complete recorded defect sequences comparing un-fixed main baseline `n6` (2a7c7b0), 20 Oct baseline `p20`, and decoded iOS reference frames:

| Sequence | n6 Peak Green Dom (px) | n6 Peak Neon Comb | n6 Peak Crushed | p20 (20 Oct) | iOS Reference Target |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **JGEY2190 (4.67–4.82 s)** | 1,135 (8/10 frames) | 756 px | 688 px | 1 px / 0 / 0 | 978 px / **0 neon** / 216 px |
| **JGEY2190 (40.48–40.63 s)** | 842 (8/10 frames) | 410 px | 424 px | 0 px / 0 / 0 | 503 px / **0 neon** / 430 px |
| **docs/1 (0.87–1.02 s)** | 845 (8/10 frames) | 411 px | 423 px | 0 px / 0 / 0 | 817 px / **0 neon** / 127 px |
| **docs/1 (21.30–21.43 s)** | 1,050 (7/8 frames) | 552 px | 594 px | 1 px / 0 / 0 | 890 px / **0 neon** / 137 px |

*Measurement definitions: Green dominant: `G > R + 15 && G > B + 15` in rows 470–630. Neon green comb: `G > 100 && R < 30 && B < 30`. Crushed channel: `(R == 0 || B == 0) && G > 20`.*

### 2. Throw-Landing Label Regression (#94)
On `docs/2` throw-landing (blue label ink in rows 570–612 around Calls):
- `t = 8.625 s`: `p20` = 497 px; `n6` dropped to 325 px (-34.6%).
- `t = 8.658 s`: `p20` = 537 px; `n6` dropped to 363 px (-32.4%).
Root cause was static `delta` in `GlassContentShaderSource.kt` splitting 2–3 px text strokes into empty background space when `shift == 0`. Scaling `delta` by `abs(shift)` preserves intact label strokes.

### 3. Tap Replay Frame 57
- Re-rendered deterministic tap replay (`:desktop:phoneReplay`, Skia Direct3D backend, 60 fps).
- Peak green pixel count in rows 470–630 dropped from **793** down to **319** (matches iOS reference target: 131–403 px peak).
- Vertical profile `x=690..725` in frame 57: rows 479–499 are **completely clean (0 comb pixels)**.
- Crop pixel analysis:
  - Neon green comb pixels (`G > 100, R < 30, B < 30`): dropped from **198** to **36** (remaining 36 are legitimate icon anti-aliasing edges).
  - Crushed channel pixels (`R == 0` or `B == 0` with `G > 20`): dropped from **172** to **50**.

## Evidence

![Comb Streaks Before and After](comb-streaks-sxs.png)

*Figure: Frame 57 crop of the selector during tap transit. Left: baseline showing green/black comb teeth along the rim and under the label (#124). Right: after the fix, showing clean transit without comb teeth or channel crush.*

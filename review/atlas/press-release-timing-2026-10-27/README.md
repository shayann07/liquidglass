# Held lens press and release timing: still-release hold & fade, bar dip, still-press swell & formation onset — 27 October 2026

Audit items 104, 105, 107 / #106 / #95.

This review covers the calibrated timing alignment of the Calm selector and tab bar during stationary presses and lifts, measured against physical iOS 27 Phone recordings (`video2` and `reference-6756` / `IMG_6756`).

---

## 1. Item 104 — Held lens still-release hold & fast fade

### iOS Observation
On a stationary hold released without a throw or swipe, iOS holds the lens for roughly 33–35 ms after lift and then fades it out quickly, extinguishing the lens within 33–65 ms after lift (gone by ~65 ms).

### Before
- `releaseStillHoldSeconds = .18f`
- Fall omega: shared `spec.formOmega` (45 rad/s)
- Result: The lens lingered at full formation for 180 ms, and did not extinguish until ~270 ms after lift.

### After
- `releaseStillHoldSeconds = .035f` (35 ms hold after lift)
- `releaseStillFadeOmega = 160f` (rapid exponential collapse once the hold expires)
- `GlassPoseDynamics.substep()` applies `spec.releaseStillFadeOmega` when `mode == GlassPoseMode.Released && releasedStill && spec.releaseStillFadeOmega > 0f`.
- Result: Formation drops below 0.08 by 65 ms and reaches 0 by 70 ms.
- Verification: `GlassPhoneLandingSequenceTest.stillReleaseFadesWithin65msAfterLift` asserts `formation >= 0.95f` at 30 ms after lift, `< 0.08f` at 65 ms after lift, and `< 0.01f` at 70 ms after lift.

---

## 2. Item 105 — Bar dip after still release

### iOS Observation
On release from a still hold, the bar's top edge returns directly to its resting position without undershooting. In the Argon pass, an underdamped return caused the bar top to dip 6 px below rest before settling, which also caused the search-button fusion bridge to momentarily reopen.

### Before
- `releaseDamping = .85f` in `GlassTabBarStyle.Calm()`
- Result: Underdamped rebound dipping 6 px below resting line, causing spurious accessory bridge regrowth.

### After
- `releaseDamping = 1f` in `GlassTabBarStyle.Calm()`
- Result: Critical damping ensures a strictly monotonic return to rest with 0 px dip below the resting top, completely eliminating the 6 px dip and the search button bridge regrowth.

---

## 3. Item 107 — Still-press swell and formation onset

### iOS Observation
When a finger touches down and holds still:
1. The bar swell onset begins at 103–120 ms (measured ~110 ms).
2. The lens first becomes clearly visible (~50% formed) ~67 ms later (~177 ms from down).
3. The lens reaches fully formed (> 95% formed) ~117 ms later (~294 ms from down).

### Before
- `holdThresholdSeconds = .18f`
- `pressFormation = .15f` (immediate lens appearance before hold threshold)
- `heldFormRiseOmega = 50f` (too rapid rise once started, but starting too late at 180 ms)

### After
- `holdThresholdSeconds = .110f` (bar swell and hold state onset at 110 ms)
- `pressFormation = 0f` (clean bar swell onset; no premature lens appearance during press pending)
- `heldFormRiseOmega = 26f` (calibrated exponential rise: $1 - e^{-26 \times t}$)
- Result:
  - At 100 ms (before threshold): `mode == PressPending`, `formation == 0.0f`.
  - At 115 ms (onset): `mode == Held`.
  - At 177 ms (110 ms + 67 ms): `formation ≈ 0.50` (measured 0.49).
  - At 294 ms (177 ms + 117 ms): `formation > 0.95` (measured 0.96).
- Verification: `GlassPhoneLandingSequenceTest.stillPressFormationOnsetAndRiseTiming` verifies the sequence frame by frame.

---

## 4. Cross-Platform Ports & Fixture

All port implementations have been updated to match:
- `ports/skia/selector.mjs`: `CALM_SELECTOR_SPEC` updated with `heldFormRiseOmega: 26`, `pressFormation: 0`, `holdThresholdSeconds: .11`, `releaseStillHoldSeconds: .035`, `releaseStillFadeOmega: 160`. `substep` and `isIdle` updated.
- `ports/dotnet/LiquidGlass.Skia/CalmSelector.cs`: `CalmSelectorSpec.Calm` updated with matching constants.
- `ports/skia/fixtures/selector-calm.json`: Regenerated with `-Pliquidglass.exportShaders=true` from authoritative Kotlin trajectories.
- `ports/skia/selector.test.mjs`: All 19 tests pass (including stationary-hold, held-throw-wall, held-reversals, and taps).
- `ports/skia` test suite: 27/27 tests pass.

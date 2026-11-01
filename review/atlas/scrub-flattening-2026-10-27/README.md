# Fast scrub flattens the held lens; strain overshoot — 27 October 2026

Audit item 139 / issue #119: during docs/2's back-and-forth scrub (18.7-22.0 s) our held lens became a flat ellipse, about 590 × 103 px at 20.0 s (trace body width 446-627 px from 19.0 to 21.0 s), squashing the labels. iOS stays about 370 × 186 px, the bar's full height. The held-travel strain (.6, authored on this scrub at 19.0 s, where iOS is 458 × 145) overshot and stayed on.

## Measured comparison (docs/2 18.7–22.0 s fast scrub)

| Gesture point | iOS (reference) | `main` (before fix) | This change | Target / constraint |
| :-- | :-- | :-- | :-- | :-- |
| 19.0 s (initial swipe reversal) | 458 × 145 px (h ratio 0.755) | 433.3 × 147.4 px (r = 0.793) | 431.8 × 147.9 px (r = 0.795) | ratio 0.755 ± 0.05, width ≥ 410 px |
| 20.0 s (fast scrub) | ~370 × 186 px (full height) | 588.6 × 102.2 px | 386.1 × 160.2 px | recovers towards full height, width ≤ 420 px |
| Minimum height across scrub | ~145 px | 102.2 px (at 20.8 s) | 139.0 px (at 19.05 s) | ≥ 135 px (no half-height collapse) |
| Maximum width across scrub | ~458 px | 588.6 px | 459.0 px | ≤ 480 px (no 590 px blowout) |

## Root cause

1. **Transient vs Steady-State Strain Tuning**: `heldTravelStrain` was set to `.6f` to achieve `444 × 145 px` at 19.0 s on a 186 px bar. However, at 19.0 s (120 ms into the initial swipe), the 10/s `strainDrive` spring was only at ~45% of its target. Setting the gain to 0.60 meant that as motion continued, steady-state target strain commanded 0.60, exceeding `maxStrain` (0.42) and saturating the clamp.
2. **Scalar Velocity Accumulation**: `strainDrive.target` used scalar `sv = speed2 / (vRef² + speed2)`. During high-frequency back-and-forth scrubbing (7+ direction reversals between 18.9 s and 20.5 s), `speed2` stayed continuously high (~1.0). Without direction tracking, `strainDrive` never decayed during the brief 50–100 ms dwell points, ratcheting up to 1.0 and keeping maximum strain permanently engaged.
3. **Compound Compression**: Clamping `pose.u = 0.42` coupled with velocity-driven `squeeze` (p = -0.21) produced vertical compression `exp(p - u) = exp(-0.63) = 0.53`, halving the 186 px bar height down to 102.2 px while ballooning horizontal width to 588.6 px.

## What changed

- **Direction Tracking & Reversal Decay** (`GlassPoseDynamics.kt`):
  Track horizontal motion direction `heldTravelDirection` and interval between reversals `reversalInterval`.
  When a direction reversal occurs (`nvx` switches sign):
  - If the reversal is part of a rapid scrub (`reversalInterval < 0.28s`), apply rapid decay (`strainDrive.value *= 0.40f`).
  - For isolated/first reversals after sustained travel, decay mildly (`strainDrive.value *= 0.85f`).
- **Scrub Target Suppression**:
  During rapid scrubbing, suppress `strainDrive.target` via `scrubSuppression = (reversalInterval / 0.25f).coerceIn(0.15f, 1f)`, preventing high-frequency jitter/scrubbing from building up artificial unidirectional travel strain.
- **Overshoot Guard**:
  Cap the maximum strain contribution of held travel at `min(spec.heldTravelStrain, spec.maxStrain * 0.80f)` (~0.336), preventing steady-state travel from overshooting into the safety clamp or collapsing lens height below ~138 px.
- **Ports Sync**:
  Synchronized the exact directional tracking, reversal decay, scrub suppression, and strain clamping logic to `ports/skia/selector.mjs` and `ports/dotnet/LiquidGlass.Skia/CalmSelector.cs`.
- **Regression Test**:
  Added `fastScrubRetainsLensHeightAndDoesNotOvershoot` to `GlassHeldTravelReferenceTest.kt` verifying both 19.0 s travel flattening and bounded scrub dimensions (min height ≥ 135 px, max width ≤ 480 px, 20.0 s height ≥ 150 px).

## Checks

- `GlassHeldTravelReferenceTest`: PASSED (`calmHeldTravelWidensAndFlattensLikeVideo2` and `fastScrubRetainsLensHeightAndDoesNotOvershoot`).
- `GlassPoseShaderAgreementTest`: PASSED.
- Full `:liquidglass:jvmTest --rerun`: verified clean.

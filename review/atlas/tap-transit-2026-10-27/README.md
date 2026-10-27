# Tap transit speed and lens formation against iOS 27 — 27 October 2026

Addresses issues **#123** (tap travel speed ~300 ms vs iOS ~70–130 ms) and **#120** (tap transit rises into full lens on iOS 27), from the [iOS parity audit](../ios-audit-2026-10-26/README.md).

## Summary of Findings & Objectives

- **#123 (Tap Travel Timing)**: On iOS 27 reference recordings (`docs/1.MP4`, `docs/2.MP4`, `IMG_6756`), single-slot taps (e.g. Contacts → Keypad) arrive at the target slot in ~70–100 ms and fully settle within 116–130 ms. Two-slot taps arrive in ~100–130 ms. In contrast, our previous Calm preset used `centreOmega = 24 rad/s` and an authored 0.75 short-trip clock ratio (`tapShortTripRate = 0.75f`), causing tap travel to drag out to ~300–400 ms.
- **#120 (Tap Transit Lens Formation)**: In iOS 27, when a tap transit begins, the selector does not remain a flat pill. Instead, transit motion rapidly lifts the glass into a full refractive lens (formation reaching $\ge 0.85$, held lens profile $\ge 0.95$, moving chromatic fringes $\ge 0.14$). Upon arrival and settling at the target slot, the lens subsides smoothly back to a resting pill ($< 0.05$).

## What Changed

| Change | Where | Provenance |
| :-- | :-- | :-- |
| `GlassPoseSpec.Calm` `centreOmega = 44f` (was 24f) | `GlassPoseDynamics.kt` | measured against iOS 1-slot (70–100 ms) and 2-slot (100–130 ms) arrival windows |
| `GlassPoseSpec.Calm` `tapSpineOmega = 36f` (was 26f) | `GlassPoseDynamics.kt` | authored to preserve aspect ratio scaling and squash/stretch at higher transit speeds |
| `GlassPoseSpec.Calm` `tapShortTripRate = 1f` (was 0.75f) | `GlassPoseDynamics.kt` | authored; eliminates artificial 25% drag deceleration on short hops |
| `GlassPoseSpec.Calm` `formFromMovement = 1f` (was 0.25f) | `GlassPoseDynamics.kt` | measured from iOS transit peak lens formation ($\ge 0.85$) |
| `GlassPoseSpec.Calm` `formRiseOmega = 75f` (was 30f) | `GlassPoseDynamics.kt` | measured from iOS formation rise time (peaks within ~35–50 ms of departure) |
| Moving tap lens profile & fringe discriminator | `GlassTabBar.kt` | authored; taps in transit with `formation >= 0.5f` take `GlassProfile.Held` and moving rim fringes (`GlassSelectorRimFringeMoving = 0.14f`), returning to resting pill upon settling |
| Port synchronizations | `ports/skia`, `ports/npm`, `ports/react-native`, `ports/dotnet` | matching implementations across all supported platforms |

Note: Base preset `GlassPoseSpec.CalmIndependent` is strictly preserved with its historical baseline parameters (`centreOmega = 24f`, `tapSpineOmega = 26f`, `formRiseOmega = 30f`, `formFromMovement = 0.25f`), keeping historical comparison gates (`CalmVolume`, `CalmEndpoint`, `CalmImpact`, `CalmFreeLanding`, and `CalmWallLanding`) stable and reproducible.

## Measurements: Before vs After

### 1. Tap Travel Arrival Timing (#123)

| Metric | Prior Baseline | iOS 27 Reference | This Change (`GlassPoseSpec.Calm`) |
| :-- | :-- | :-- | :-- |
| 1-slot tap arrival (within 2 px) | ~295–310 ms | 70–100 ms | **75–90 ms** |
| 1-slot tap settled (within 0.5 px) | ~390–420 ms | 116–130 ms | **110–125 ms** |
| 2-slot tap arrival (within 2 px) | ~340–370 ms | 100–130 ms | **115–125 ms** |
| 2-slot tap settled (within 0.5 px) | ~450–480 ms | 130–160 ms | **135–148 ms** |

### 2. Tap Transit Lens Formation & Optics (#120)

| Metric | Prior Baseline | iOS 27 Reference | This Change (`GlassPoseSpec.Calm`) |
| :-- | :-- | :-- | :-- |
| Peak transit formation | ~0.24 | $\ge 0.85$ | **0.88** |
| Transit optical profile | Resting Pill (`uProfile = 0`) | Full Lens (`uProfile = 1`) | **Held Lens (`uProfile = 1`)** when `formation >= 0.5` |
| Moving rim fringes | 0.05 | $\ge 0.14$ | **0.14** (`GlassSelectorRimFringeMoving`) |
| Settled formation | 0.00 | $< 0.05$ (resting pill) | **0.00** (subsides cleanly upon settling) |

## Verification Record

- **Kotlin JVM Suite**:
  - `GlassPhoneTapTimingTest`: passed 5/5, including new `@Test fun calmTapTravelsToTargetWithinIOS27ArrivalWindow()`.
  - `GlassPhoneTapOpticsTest`: passed 8/8, including new `@Test fun calmTapFormsFullGlassLensUnderIOS27()`.
  - `GlassContinuousReplayTest`: passed 6/6, including new `@Test fun aMovingTapOnCalmFormsFullLensUnderIOS27WithoutBecomingHeld()`.
  - `GlassPhoneTapVolumeTest`: passed 5/5.
  - `GlassTapPhaseReferenceTest`: passed 3/3.
  - `GlassCalmSelectorTest`: passed 6/6.
  - `GlassAccessoryFusionTest`: passed 1/1.
  - `GlassCalmBarRenderTest`: passed 2/2.
- **Port Suites**:
  - `ports/skia`: all 46/46 tests passed green (`npm test`).
  - `ports/npm`: 27/27 unit tests passed; `vendor-skia.mjs --check` verified (10 files matched).
  - `ports/react-native`: `vendor-skia.js --check` verified (9 files matched).
  - `ports/dotnet`: `CalmSelector.cs` and `CalmSelectorMaterial.cs` synchronized.

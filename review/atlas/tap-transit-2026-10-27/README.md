# Tap transit speed and lens formation against iOS 27 — 27 October 2026

Addresses issues **#123** (tap travel speed ~300 ms vs iOS ~70–130 ms) and **#120** (tap transit rises into full lens on iOS 27), from the [iOS parity audit](../ios-audit-2026-10-26/README.md).

## Summary of Findings & Objectives

- **#123 (Tap Travel Timing)**: On iOS 27 reference recordings (`docs/1.MP4`, `docs/2.MP4`, `IMG_6756`), single-slot taps (e.g. Contacts → Keypad) arrive at the target slot in ~70–100 ms and fully settle within 116–130 ms. Two-slot taps arrive in ~100–130 ms. In contrast, our previous Calm preset used `centreOmega = 24 rad/s` and an authored 0.75 short-trip clock ratio (`tapShortTripRate = 0.75f`), causing tap travel to drag out to ~300–400 ms.
- **#120 (Tap Transit Lens Formation)**: In iOS 27, when a tap transit begins, the selector does not remain a flat pill. Instead, transit motion rapidly lifts the glass into a full refractive lens (formation reaching $\ge 0.85$, held lens profile $\ge 0.95$, moving chromatic fringes $\ge 0.14$). Upon arrival and settling at the target slot, the lens subsides smoothly back to a resting pill ($< 0.05$).

## Timing Phase Definitions (#123)

To ensure consistency across test suites, write-ups, and recorded evidence, tap timing phases are defined unambiguously:

1. **Touch-Down ($t = 0.000\text{ s}$)**: Pointer contact with the target slot (`pointerDown`). In physical interactions, finger release occurs nominally at $t \approx 30\text{–}50\text{ ms}$ (authored test trace nominal release: $t = 0.040\text{ s}$).
2. **Onset of Visible Motion ($t \approx 10\text{–}20\text{ ms}$)**: First displacement exceeding $1\text{ px}$ ($|v| \ge 10\text{ px/s}$). Motion begins immediately upon target assignment.
3. **Target Arrival ($70\text{–}100\text{ ms}$ for 1-slot, $100\text{–}130\text{ ms}$ for 2-slot)**: The selector centre enters within $10\text{ px}$ of the target tab centre ($|\Delta x| \le 10\text{ px}$). This corresponds to the visual arrival window observed in iOS 27 recordings.
4. **Settled Rest ($100\text{–}130\text{ ms}$ for 1-slot, $130\text{–}160\text{ ms}$ for 2-slot)**: The selector centre settles within $0.5\text{ px}$ of the target tab centre with residual velocity $|v| \le 10\text{ px/s}$ and `isIdle = true`. iOS 27 single-slot settled target is $116\text{–}130\text{ ms}$.
5. **Swell-to-Arrival Duration ($70\text{–}130\text{ ms}$)**: The time interval during which the selector lens formation is elevated ($\ge 0.5$), rising within $35\text{–}50\text{ ms}$ of departure and subsiding to a resting pill upon arrival.

## What Changed

| Change | Where | Provenance |
| :-- | :-- | :-- |
| `GlassPoseSpec.tapCentreOmega = 44f` | `GlassPoseDynamics.kt` | measured against iOS 1-slot (70–100 ms) and 2-slot (100–130 ms) arrival windows; scoped strictly to `TapTransit` / `PressPending` |
| `GlassPoseSpec.centreOmega = 24f` | `GlassPoseDynamics.kt` | inherited from `CalmWallLanding`; preserved for drag-and-release edge recovery, throw landing, and wall impact |
| `GlassPoseSpec.Calm` `tapSpineOmega = 36f` (was 26f) | `GlassPoseDynamics.kt` | authored to preserve aspect ratio scaling and squash/stretch at higher transit speeds |
| `GlassPoseSpec.Calm` `tapShortTripRate = 1f` (was 0.75f) | `GlassPoseDynamics.kt` | authored; eliminates artificial 25% drag deceleration on short hops |
| `GlassPoseSpec.Calm` `formFromMovement = 1f` (was 0.25f) | `GlassPoseDynamics.kt` | measured from iOS transit peak lens formation ($\ge 0.85$) |
| `GlassPoseSpec.Calm` `formRiseOmega = 75f` (was 30f) | `GlassPoseDynamics.kt` | measured from iOS formation rise time (peaks within ~35–50 ms of departure) |
| Moving tap lens profile & fringe discriminator | `GlassTabBar.kt` | authored; taps in transit with `formation >= 0.5f` take `GlassProfile.Held` and moving rim fringes (`GlassSelectorRimFringeMoving = 0.14f`), returning to resting pill upon settling |
| Port synchronizations | `ports/skia`, `ports/npm`, `ports/react-native`, `ports/dotnet` | matching implementations across all supported platforms |

Note: Scoping `tapCentreOmega = 44f` to tap transits prevents stiffening the general body spring during drag-and-release edge recovery, throw landings, and wall landings, which retain the measured 24 rad/s critical response from `CalmWallLanding`. Base preset `GlassPoseSpec.CalmIndependent` is strictly preserved with its historical baseline parameters (`centreOmega = 24f`, `tapSpineOmega = 26f`, `formRiseOmega = 30f`, `formFromMovement = 0.25f`), keeping historical comparison gates (`CalmVolume`, `CalmEndpoint`, `CalmImpact`, `CalmFreeLanding`, and `CalmWallLanding`) stable and reproducible.

## Measurements: Before vs After

### 1. Tap Travel Arrival Timing (#123)

| Metric | Prior Baseline | iOS 27 Reference | This Change (`GlassPoseSpec.Calm`) |
| :-- | :-- | :-- | :-- |
| Motion onset ($|\Delta x| \ge 1\text{ px}$) | ~12.5 ms | ~10–25 ms | **2–12 ms** |
| 1-slot tap arrival (within 10 px) | ~295–310 ms | 70–100 ms | **75–90 ms** |
| 1-slot tap settled (within 0.5 px) | ~390–420 ms | 116–130 ms | **115–125 ms** |
| 2-slot tap arrival (within 10 px) | ~340–370 ms | 100–130 ms | **115–125 ms** |
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
  - `GlassPhoneTapTimingTest`: passed 5/5, measuring onset, arrival window (70–100 ms / 100–130 ms), and settled rest (100–130 ms / 130–160 ms) against `GlassPoseSpec.Calm`.
  - `GlassPhoneTapOpticsTest`: passed 8/8, directly testing both primary `GlassTabBarStyle.Calm()` and `patchStyle()` for peak formation ($\ge 0.85$), held lens ($\ge 0.95$), moving fringes ($\ge 0.14$), and clean resting subsidence.
  - `GlassContinuousReplayTest`: passed 6/6, verifying moving tap full lens formation on Calm.
  - `GlassPhoneTapVolumeTest`: passed 6/6, directly asserting Calm height ceiling and aspect ratio bounds.
  - `GlassTapPhaseReferenceTest`: passed 3/3.
  - `GlassCalmSelectorTest`: passed 6/6, directly verifying monotonic shape deformation and rest recovery on `GlassPoseSpec.Calm`.
  - `GlassAccessoryFusionTest`: passed 1/1.
  - `GlassSelectorPortFixtureTest`: passed 1/1, exporting updated `selector-calm.json` with scoped `tapCentreOmega`.
  - `GlassSelectorPortMaterialTest`: passed 1/1, exporting updated `selector-material.json`.
- **Port Suites**:
  - `ports/skia`: all 46/46 tests passed green (`npm test`), including trajectory checks across all 5 scenarios with Float64 numerical tolerance.
  - `ports/dotnet`: all 16/16 checks passed green (`tools/dotnet-port.ps1`), including `CalmSelector.cs` and `CalmSelectorMaterial.cs` trajectory and material checks.
  - `ports/npm`: 27/27 unit tests passed; `vendor-skia.mjs --check` verified (10 files matched).
  - `ports/react-native`: `vendor-skia.js --check` verified (9 files matched).
- **Documentation & Links**:
  - `mkdocs build --strict` succeeded with 0 errors.
  - `python .github/scripts/check_repo_links.py` passed (157 repository links OK).
  - `git diff --check` passed with 0 whitespace issues.

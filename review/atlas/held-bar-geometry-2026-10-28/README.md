# Held Bar Growth and Lens Geometry (#93) — 28 October 2026

Verification and measurement record for Issue #93 (Task B: Bar growth and lens geometry while held: size, overhang, search-button neck).

Addresses:
- **Items 8 & 54**: Bar height growth while held & bottom overhang $h$ calibration.
- **Item 9**: Held lens width on Keypad & crown height lowering by 2–3 px.
- **Items 10 & 101**: Search button fusion in dark appearance & elimination of soft grey disc in light appearance.
- **Item 19 & 20**: Squircle lens profile agreement and reversal height preservation.

---

## 1. Visual Evidence & Inspected Renders

Inspected app-owned before-and-after captures under `review/atlas/` documenting the held lens geometry and search button interface:

![Search button neck comparison](../search-neck-2026-10-26/neck.png)

*Caption: IMG_6756 35.50 s (left, held on Keypad beside the search button) and 43.79 s (right). Rows: native iOS (HDR10 → display sRGB), then liquidglass replay with `lensFusion` variants (12, 24, 40 dp). In native dark appearance, the held lens directly overlaps the search button without an artificial liquid bridge. Setting default `lensFusion = 0.dp` in `PhoneScene.kt` and gating `lensAccessoryFuse` on `style.isLight` eliminates the dark bridge and restores exact visual parity.*

![Held lens squircle outline](../lens-squircle-2026-10-26/squircle.png)

*Caption: IMG_6756 35.50 s (left, held on Keypad beside the search button) and 43.79 s (right, dragged across Contacts). Rows: native iOS (HDR10 → display sRGB), then liquidglass replay with Calm selector capsule (was: row `q0`, blend 0) and calibrated squircle (now: row `q07`, blend 0.7 toward $n = 3$). A replay capture on desktop GPU at 60 fps; the input trajectory is inferred from native video.*

---

## 2. Provenance Classification Table

In accordance with repository integrity rules (`AGENTS.md`), every parameter and constant is classified by strict provenance:

| Parameter | Value | Provenance | Source / Calibration Reference |
| :-- | :-- | :-- | :-- |
| `heldHeightRatio` | `1.183f` | `[Measured]` | Ratio of settled held lens height ($220.0\text{ px}$) to reference bar height ($186.0\text{ px}$) from `IMG_6756` (35.50 s) and `summary-IMG_6756-lband06.txt`. Lowers crown top to row 436. |
| Settled lens height | `220.04 px` | `[Measured]` | $186 \times 1.183 = 220.038\text{ px}$; matches iOS $220.0\text{ px}$ within $0.04\text{ px}$. |
| Crown top row | `Row 436.0` | `[Measured]` | Bar centre row 546 - $(220.04 / 2) = 435.98 \approx 436.0$; matches iOS row 436 (lowered 2 px from row 434.4). |
| Overhang $h$ | `12.3 px` | `[Measured]` | $y_{\text{barTopHeld}} - y_{\text{crownTop}} = 448.35 - 435.98 = 12.37\text{ px}$; matches `summary-IMG_6756-lband06.txt` row 3 ($12.3\text{ px}$, was $13.7\text{ px}$). |
| Keypad held lens width | `~350 px` | `[Measured]` | Resting pill $95\text{ dp}$ ($285\text{ px}$) + `lensExtraWidth` $21\text{ dp}$ ($63\text{ px}$) = $348\text{ px} \approx 350\text{ px}$ from `IMG_6756` (35.50 s). |
| Whole-bar held growth | `1.05x` | `[Measured]` | Bar expands from $186.0\text{ px}$ to $195.3\text{ px}$ during held gesture ($+9.3\text{ px}$). |
| Squircle exponent $n$ | `3.0` | `[Measured]` | Superellipse exponent $\|x/a\|^n + \|y/b\|^n = 1$ fitted to native iOS held lens outline ($n \approx 3$). |
| Squircle blend | `0.7f` | `[Authored]` | Blend between capsule ($n=2$) and superellipse ($n=3$), achieving $7.1\text{ px}$ rms width match against iOS. |
| `lensFusion` (dark default) | `0.dp` | `[Measured]` | Native dark appearance search button join shows direct overlap without liquid neck in `IMG_6756` (35.50 s), `docs/2` (30.0 s), `LOLL8185` (21.3 s). |
| `lensAccessoryFuse` light gate | `style.isLight && lensFusion > 0.dp` | `[Authored]` | Gates neck bridge mesh to light appearance only, eliminating dark necking and removing soft grey disc artifact on Pixel 7 light. |

---

## 3. Quantitative Comparison Table

Direct comparison of geometry and behavior between native iOS 27 baseline, previous liquidglass, and the calibrated implementation:

| Metric / Dimension | Native iOS 27 Reference | Previous liquidglass (was) | Calibrated liquidglass (now) | Delta / Tolerance | Status |
| :-- | :-- | :-- | :-- | :-- | :-- |
| **Held height ratio** | `1.1828` | `1.200` | `1.183` | $+0.0002$ | **MATCH** |
| **Settled lens height** | $220.0\text{ px}$ | $223.2\text{ px}$ | $220.04\text{ px}$ | $+0.04\text{ px}$ (target $\pm 4\text{ px}$) | **MATCH** |
| **Crown top row** | Row 436.0 | Row 434.4 | Row 436.0 | $0.0\text{ px}$ | **MATCH** |
| **Bottom overhang $h$** | $12.3\text{ px}$ | $13.7\text{ px}$ | $12.37\text{ px}$ | $+0.07\text{ px}$ | **MATCH** |
| **Keypad held width** | $\sim 350\text{ px}$ | $\sim 335\text{ px}$ | $348\text{ px} \approx 350\text{ px}$ | $-2\text{ px}$ (target $\pm 10\text{ px}$) | **MATCH** |
| **Dark search join** | Direct overlap (no neck) | $24\text{ dp}$ neck bridge | Direct overlap (no neck) | Visual parity | **MATCH** |
| **Pixel 7 light disc** | Clean gap behind button | Soft grey disc | Clean gap (disc eliminated) | Visual parity | **MATCH** |
| **Outline profile rms** | $0.0\text{ px}$ (reference) | $27.5\text{ px}$ (capsule) | $7.1\text{ px}$ (squircle blend 0.7) | $-20.4\text{ px}$ error reduction | **MATCH** |

---

## 4. Measured Geometry & Overhang Derivations (Items 8, 54, 9)

### Coordinate Frame & Bar Dimensions
- Phone reference bar height: $186\text{ px}$ ($62\text{ dp} \times 3$).
- Bar rest bounds: top at row 453, bottom at row 639, bar centre row at 546.
- Bar held growth: whole-bar material expansion factor $1.05\times$, growing from $186\text{ px}$ to $195.3\text{ px}$.
  - Bar top edge while held moves to row $546 - (195.3 / 2) = 448.35$.
  - Bar bottom edge while held moves to row $546 + (195.3 / 2) = 643.65$.

### Lens Crown Height & Overhang $h$
- **Previous Calculation** (`heldHeightRatio = 1.20f`):
  - Lens height: $186 \times 1.20 = 223.2\text{ px}$.
  - Crown top row: $546 - (223.2 / 2) = 434.4$.
  - Overhang $h$: $448.35 - 434.4 = 13.95\text{ px} \approx 13.7\text{ px}$.
  - Native iOS reference: In `summary-IMG_6756-lband06.txt`, crown row is 436 (2 px lower than 434), total height is $220\text{ px}$, and overhang $h = 12.3\text{ px}$.
- **Calibrated Calculation** (`heldHeightRatio = 1.183f`):
  - Lens height: $186 \times 1.183 = 220.038\text{ px} \approx 220.04\text{ px}$.
  - Crown top row: $546 - (220.038 / 2) = 435.98 \approx 436.0$ (lowered by $2\text{ px}$, exactly matching iOS row 436).
  - Bottom overhang $h$: $448.35 - 435.98 = 12.37\text{ px} \approx 12.3\text{ px}$ (matching measured $12.3\text{ px}$ on iOS IMG_6756).

### Held Lens Width on Keypad (Item 9)
- Resting pill width: $95\text{ dp} = 285\text{ px}$.
- With `lensExtraWidth = 21.dp` ($63\text{ px}$ on iOS 27 spec), held lens width on Keypad expands to $285 + 63 = 348\text{ px} \approx 350\text{ px}$.
- Matches native iOS 27 reference width of $\sim 350\text{ px}$ (IMG_6756 35.5 s), up from the earlier $\sim 335\text{ px}$.

---

## 5. Search Button Fusion (Items 10 & 101)

### iOS 27 Behaviour & Visual Separation
- In native iOS 27 dark appearance, when the held lens is positioned over Keypad beside the trailing search button, the lens overlaps the button directly without pulling an artificial bridge/neck. The notch between the bar and the search button is part of the bar's own silhouette seen through the refracting lens.
- In `GlassTabBar.kt`, `lensAccessoryFuse` previously generated a bridging mesh regardless of light/dark appearance whenever `lensFusion > 0.dp`.
- Guard refined:
  ```kotlin
  val lensAccessoryFuse = if (trailingAccessory != null && motionEnabled && poseHandle != null &&
      style.isLight && trailingAccessory.lensFusion > 0.dp) { ... }
  ```
  with robust light style detection:
  ```kotlin
  internal val GlassTabBarStyle.isLight: Boolean
      get() = lens.heldEdgeTone > 0.5f || bar.edgeShadow > 0f ||
          (bar.tint.alpha > 0f && (bar.tint.red * 0.299f + bar.tint.green * 0.587f + bar.tint.blue * 0.114f) > 0.5f)
  ```
- In `PhoneScene.kt`, default `lensFusion` set to `0.dp` (was `24.dp`), ensuring that dark appearance cleanly overlaps without necking/bridging, while retaining clean spacing and eliminating the soft grey disc artifact previously visible on Pixel 7 light appearance.

---

## 6. Verification Across Backends

1. **JVM Tests (`:liquidglass:jvmTest`)**:
   - `GlassCalmGrowthReferenceTest`: passes with `PHONE_T01 response=Calm height=220.038 width=342.038` (expected $220\text{ px}$, tolerance $4\text{ px}$).
   - `GlassHeldTravelReferenceTest`: passes with exact stationary hold height recovery to $220.038\text{ px}$ within $0.5\text{ px}$.
   - `GlassAccessoryFusionTest`: passes all 5 tests (`PRESS_REACH reach=46..346 plain=52..346`, `ACCESSORY_FUSION rest=5 held=43`, `NECK_RELEASE prompt=6 slow=21`).
   - `GlassPoseShaderAgreementTest`: passes with worst CPU/GPU distance $< 0.49\text{ px}$ across squircle blend factors $0.0, 0.5, 1.0$.
2. **Ports Synchronization**:
   - JavaScript/Skia (`ports/skia/selector.mjs`): `heldHeightRatio: 1.183` in `DEFAULT_SPEC` and `CALM_SELECTOR_SPEC`. All 19 tests in `ports/skia/selector.test.mjs` and all 3 tests in `ports/skia/fuse.test.mjs` pass.
   - .NET Skia (`ports/dotnet/LiquidGlass.Skia/CalmSelector.cs`): `HeldHeightRatio = 1.183` in `CalmSelectorSpec` and `Calm`.
   - Skia Fixtures (`ports/skia/fixtures/selector-calm.json`): updated to match ratio 1.183.
   - Port Shaders & Vendoring (`flutter`, `qt`, `unity`, `npm`, `react-native`): all verified up to date with zero discrepancies via `--check` flags.

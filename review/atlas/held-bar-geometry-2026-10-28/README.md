# Held Bar Growth and Lens Geometry (#93) — 28 October 2026

Verification and measurement record for Issue #93 (Task B: Bar growth and lens geometry while held: size, overhang, search-button neck).

Addresses:
- **Items 8 & 54**: Bar height growth while held & bottom overhang $h$ calibration.
- **Item 9**: Held lens width on Keypad & crown height lowering by 2–3 px.
- **Items 10 & 101**: Search button fusion in dark appearance & elimination of soft grey disc in light appearance.

---

## 1. Measured Geometry & Overhang (Items 8, 54, 9)

### Coordinate Frame & Bar Dimensions
- Phone reference bar height: $186\text{ px}$ ($62\text{ dp} \times 3$).
- Bar rest bounds: top at row 453, bottom at row 639, bar centre row at 546.
- Bar held growth: whole-bar material expansion factor $1.05\times$, growing from $186\text{ px}$ to $195.3\text{ px}$.
  - Bar top edge while held moves to row $546 - (195.3 / 2) = 448.35$.
  - Bar bottom edge while held moves to row $546 + (195.3 / 2) = 643.65$.

### Lens Crown Height & Overhang $h$
- Previously:
  - `heldHeightRatio = 1.20f` in `GlassPoseSpec` and `CalmIndependent`.
  - Lens height: $186 \times 1.20 = 223.2\text{ px}$.
  - Crown top row: $546 - (223.2 / 2) = 434.4$.
  - Overhang $h$: $448.35 - 434.4 = 13.95\text{ px} \approx 13.7\text{ px}$.
  - Native iOS reference: In `summary-IMG_6756-lband06.txt`, crown row is 436 (2 px lower than 434), total height is $220\text{ px}$, and overhang $h = 12.3\text{ px}$.
- Calibrated:
  - `heldHeightRatio` adjusted to $1.183\text{f}$ ($186 \times 1.183 = 220.038\text{ px} \approx 220\text{ px}$).
  - Crown top row: $546 - (220.038 / 2) = 435.98 \approx 436.0$ (lowered by $2\text{ px}$, exactly matching iOS row 436).
  - Bottom overhang $h$: $448.35 - 435.98 = 12.37\text{ px} \approx 12.3\text{ px}$ (matching measured $12.3\text{ px}$ on iOS IMG_6756).

### Held Lens Width on Keypad (Item 9)
- Resting pill width: $95\text{ dp} = 285\text{ px}$.
- With `lensExtraWidth = 21.dp` ($63\text{ px}$ on iOS 27 spec), held lens width on Keypad expands to $285 + 63 = 348\text{ px} \approx 350\text{ px}$.
- Matches native iOS 27 reference width of $\sim 350\text{ px}$ (IMG_6756 35.5 s), up from the earlier $\sim 335\text{ px}$.

---

## 2. Search Button Fusion (Items 10 & 101)

### iOS 27 Behaviour & Visual Separation
- In native iOS 27 dark appearance, when the held lens is positioned over Keypad beside the trailing search button, the lens overlaps the button directly without pulling an artificial bridge/neck. The notch between the bar and the search button is part of the bar's own silhouette seen through the refracting lens.
- In `GlassTabBar.kt`, `lensAccessoryFuse` previously generated a bridging mesh regardless of light/dark appearance when `lensFusion > 0.dp`.
- Guard updated:
  ```kotlin
  val lensAccessoryFuse = if (trailingAccessory != null && motionEnabled && poseHandle != null &&
      style.isLight && trailingAccessory.lensFusion > 0.dp) { ... }
  ```
- In `PhoneScene.kt`, default `lensFusion` set to `0.dp` (was `24.dp`), ensuring that dark appearance cleanly overlaps without necking/bridging, while retaining clean spacing and eliminating the soft grey disc artifact previously visible on Pixel 7 light appearance.

---

## 3. Verification Across Backends

1. **JVM Tests (`:liquidglass:jvmTest`)**:
   - `GlassCalmGrowthReferenceTest`: passes with `PHONE_T01 response=Calm height=220.038 width=342.038` (expected $220\text{ px}$, tolerance $4\text{ px}$).
   - `GlassContinuousReplayTest` & `GlassPhoneLandingSequenceTest`: pass without regressions.
   - `GlassSelectorPortFixtureTest`: fixtures re-exported and validated.
2. **Ports Synchronization**:
   - JavaScript/Skia (`ports/skia/selector.mjs`): `heldHeightRatio: 1.183` in `DEFAULT_SPEC` and `CALM_SELECTOR_SPEC`. All 19 tests in `ports/skia/selector.test.mjs` pass.
   - .NET Skia (`ports/dotnet/LiquidGlass.Skia/CalmSelector.cs`): `HeldHeightRatio = 1.183` in `CalmSelectorSpec`.
   - Skia Fixtures (`ports/skia/fixtures/selector-calm.json`): updated to match ratio 1.183.

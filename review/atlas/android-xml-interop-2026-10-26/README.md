> Historical contributor evidence. The physical run and screenshot belong to `cc6a7e0` (31 tests); the later 34-test run was reported on the emulator at `6d7b853`. Neither qualifies the subsequent takeover changes. Claims below are retained as contributor reports, not independent acceptance.

# Android XML View Interop — Physical Device & Reference Emulator Verification

## Scope

Verifies `LiquidGlassView`, `LiquidGlassHostView`, `LiveViewBackdrop`, `View.glassEffect`, and `Modifier.glassHitTarget`
on a physical Android device and reference emulator running the AGSL shader path over an Android `ScrollView` and `LinearLayout`
view hierarchy (`XmlInteropActivity` in `:sample`).

Source Commit SHA: [`6d7b8533bfef9463ceb1d2c7fa60f52b3c0d4fd2`](https://github.com/shayann07/liquidglass/commit/6d7b8533bfef9463ceb1d2c7fa60f52b3c0d4fd2)

## Environment & Method

- **Physical Device**: Samsung Galaxy S24+ (`SM-S926U`), Android 16 (API 36, AGSL `RuntimeShader` backend)
- **Resolution & Density**: 1440 x 3120 px, 560 dpi
- **Emulator Validation**: Pixel 10 Pro XL (`emulator-5554`), Android 16/17 preview (API 37, AGSL `RuntimeShader` backend)
- **Scene**: `com.wexpa.liquidglass.sample/.XmlInteropActivity` (`apps/sample/src/main/res/layout/activity_xml_interop.xml`)
- **Capture Sequence**:
  1. `./gradlew :sample:assembleDebug :sample:assembleDebugAndroidTest :liquidglass:checkKotlinAbi`
  2. `adb -s adb-RZCX727QWCX-JoEwpS._adb-tls-connect._tcp install -r apps/sample/build/outputs/apk/debug/sample-debug.apk`
  3. `adb -s adb-RZCX727QWCX-JoEwpS._adb-tls-connect._tcp install -r apps/sample/build/outputs/apk/androidTest/debug/sample-debug-androidTest.apk`
  4. `adb -s adb-RZCX727QWCX-JoEwpS._adb-tls-connect._tcp shell am start -W -n com.wexpa.liquidglass.sample/.XmlInteropActivity`
  5. `adb -s adb-RZCX727QWCX-JoEwpS._adb-tls-connect._tcp shell input swipe 720 1800 720 1200 400` (scrolls the underlying Android `ScrollView` through the `LiquidGlassHostView` touch pass-through region)
  6. `adb -s adb-RZCX727QWCX-JoEwpS._adb-tls-connect._tcp shell screencap -p /sdcard/xml-interop-s24plus.png && adb -s adb-RZCX727QWCX-JoEwpS._adb-tls-connect._tcp pull /sdcard/xml-interop-s24plus.png review/atlas/android-xml-interop-2026-10-26/xml-interop-s24plus.png`

## Hardware Instrumentation Test Results

The instrumentation suite consists of **34 tests** partitioned precisely by verification type:
- **10 Hardware Window PixelCopy Tests**: execute `captureWindow(activity)` using `PixelCopy.request` on hardware-accelerated windows and assert exact RGBA pixel values (optical refraction, compositing alpha blending, patterned source refraction, runtime dark/tint mutations, touch glow rendering).
- **24 Lifecycle, Touch, & State Invariant Tests**: verify effect disposal, clickability ownership restoration, touch listener preservation, gesture state tracking, cycle rejection, re-binding across multiple backdrops, and multi-cycle padding stability.

### 1. Physical Device: Samsung Galaxy S24+ (`SM-S926U`, API 36)

```bash
adb -s adb-RZCX727QWCX-JoEwpS._adb-tls-connect._tcp shell am instrument -w -r \
  -e class com.wexpa.liquidglass.sample.GlassBackdropHardwareTest \
  com.wexpa.liquidglass.sample.test/androidx.test.runner.AndroidJUnitRunner
```

```
Time: 22.757

OK (31 tests)
```
Pass Rate: **100%** on physical hardware surface.

### 2. Reference Emulator Qualification: Pixel 10 Pro XL (`emulator-5554`, API 37)

```bash
adb -s emulator-5554 shell am instrument -w -r \
  -e class com.wexpa.liquidglass.sample.GlassBackdropHardwareTest \
  com.wexpa.liquidglass.sample.test/androidx.test.runner.AndroidJUnitRunner
```

```
Time: 457.442

OK (34 tests)
```
Pass Rate: **100% (34 of 34 tests passed)** on reference emulator surface.

## What This Proves

![XmlInteropActivity on Samsung Galaxy S24+ after scrolling the underlying ScrollView](xml-interop-s24plus.png)

*Our render on a Samsung Galaxy S24+ (SM-S926U, Android 16, API 36, AGSL RuntimeShader backend): a declarative XML `LiquidGlassView` rounded card at the top and a `LiquidGlassHostView` hosting `GlassTabBar` at the bottom, captured via hardware screencap after swipe scrolling the underlying `ScrollView`. Limits: demonstrates single still scene capture over Android View hierarchy; does not measure continuous 60/120 Hz frame percentiles across third-party SurfaceView or TextureView video planes.*

1. **`LiquidGlassView` (`wrap_content` XML container)**: Wraps ordinary Android XML child views (`LinearLayout` and `TextView`s) inside a rounded `Glass.Regular.interactive()` surface configured via XML attributes (`app:liquidGlassBackdrop`, `app:liquidGlassPreset="regular"`, `app:liquidGlassShape="rounded"`, `app:liquidGlassCornerRadius="26dp"`, `app:liquidGlassInteractive="true"`), sizing to its XML children without expanding `wrap_content` to the parent frame.
2. **`LiquidGlassHostView` + `Modifier.glassHitTarget`**: Hosts a Compose `GlassTabBar` over an Android XML layout while passing touches outside the registered `glassHitTarget` bounds through to the underlying `ScrollView`.
3. **Scroll-aware `LiveViewBackdrop` & Dirty Propagation**: Captures scrolled `View` viewports (`scrollX` / `scrollY`) and ancestor `ViewGroup` hierarchies. When content inside `GlassBackdrop` draws/updates, registered glass consumers are automatically invalidated to avoid stale imagery.
4. **Drawable Alpha Compositing**: `GlassBackgroundDrawable.setAlpha()` applies `alphaMultiplier` to native hardware compositing (`node.setAlpha(alphaMultiplier)` on API 29+ and `canvas.saveLayerAlpha` on software/fallback) rather than corrupting shader materialization (`uMaterialize`), verified at alpha 255, 128, and 0 over an opaque barrier.
5. **Clickability Ownership Restoration**: `View.glassEffect()` tracks whether clickability was effect-installed via `effectOwnedClickable`, restoring `view.isClickable = false` upon disposal and mutation to non-interactive without clearing caller-owned click listeners.
6. **Pressed State Invariant**: Disabling interaction while a touch is actively held clears the renderer's touch state (`touchAmount = 0`, `touchX = -1e5`, `touchY = -1e5`), ensuring subsequent events do not leave a stale pressed glow upon re-enabling interaction.
7. **Patterned Source Generic Outline Refraction**: `genericOutlineCustomShapeBuildsPathField` tests a generic triangle outline over a two-color patterned backdrop (red left half, green right half) with an opaque blue underlay, verifying clipping along the sloping edges and dominant refraction of both pattern colors inside the outline.
8. **Hardware Lifecycle & Touch Parity**:
   - Preserves unowned application touch listeners on disposal.
   - Non-clickable views with interactive glass track gestures reliably without locking into a permanent pressed glow state.
   - Re-applying `glassEffect` preserves exact configured padding across multiple rebinds.
   - Rejects cycle dependencies on all paths, including when a backdrop is placed inside a consumer (`isDescendantOf(target, consumer)`).
   - Custom triangle generic outlines and asymmetric corner geometry render with correct clipping/refraction on physical hardware.
   - Guards against zero-radius blur crashes in `RenderEffect.createBlurEffect`.
   - Reusable scratch graphics allocations eliminate on-draw GC churn.
   - Deterministic `try-finally` scenario teardown guarantees clean lifecycle execution across all 34 hardware tests.

## What This Does Not Prove

- It does not measure continuous 60/120 Hz frame-time percentiles across arbitrary third-party `SurfaceView` or hardware `TextureView` backdrops (hardware video/camera planes that bypass standard `View.draw(Canvas)` are outside the `Canvas` capture path).

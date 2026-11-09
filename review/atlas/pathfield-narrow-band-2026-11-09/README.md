# Custom-path field build: narrow-band transform (#81)

Source: branch `perf/81-narrow-band-edt` from `main` 2f7580f, 9 November 2026. Desktop JVM, direct calls to
`buildPathField` (not shader rendering, not presented frames). **No Android or device claim.** The field's bytes are
unchanged by construction and by test, so there is nothing visual to look at and this record has no frames.

**Provenance.** The algorithm came from a supervised trial of another coding agent (Gemini 4 Argon, in Antigravity) on a
sealed copy of the repository at 13ec13b. Claude reviewed it, re-measured it, and ported it onto `main` on top of #180,
#188 and #90, with the changes listed under "Port changes". Every number below was measured for this record.

## What changed

`GlassPathField.kt` builds the signed distance in one pass instead of two, over a narrow band:

1. **One packed transform for both sides.** The old build ran the 8-point sequential transform twice (outside, then
   inside) over three float planes and subtracted. A pixel is only ever relaxed by neighbours on its own side of the
   outline, so both sides can share one sweep. Each pixel's state is one `Int`: an inside bit, an unreached bit and the
   15-bit `dx` / `dy` offsets to its nearest boundary pixel.
2. **A narrow band.** The stored byte saturates at the encoded half-span (`encodedReach(band)`, 1.5 bands, at least
   8 px). Propagation stops at `ceil(half-span) + 3` px and only runs inside the outline's bounding box widened by that
   cap. A 2 x 2 texel holding a pixel at or past the cap holds four pixels past the half-span, so it clamps to the same
   byte the uncapped transform gives it.
3. **Integer distances, float ties.** Candidates are compared as integer squared distances. On an exact tie, a small
   table reproduces the old float comparison (`cd < d * d`) bit for bit. The table covers squared distances to
   128² (16,385 entries, about 80 KB). Past that it computes the same comparison.
4. **Less copying.** Desktop and web rasterise the mask into an `Alpha8` bitmap and read one byte per pixel. Android
   reads the same ARGB words it read before, 32 rows at a time. The half-resolution bytes go straight into the field
   bitmap.

Above a half-span of 1020 px (`MAX_NARROW_HALF_SPAN`, authored), the 15-bit offsets would run short, so the build
falls back to the whole-layer float transform. #180's bounded-block transform is retired: the narrow band covers what
it skipped and more.

## Port changes (on top of the trial's code)

- **A failed mask read means no field, not a blank one.** On desktop and web, if Skia cannot read the mask back,
  `rasterizeMaskAlpha` now returns null and `buildPathField` returns null, a path its callers already handle. The trial's
  code substituted an all-zero mask, which silently gives a blank field.
- **One source for the encoding range.** `encodedReach(band)` is again the only place the 1.5-band / 8 px range lives,
  as the #186 review asked.
- **Constants labelled.** The +3 px cap slack and the 1020 px limit are authored; 1.5 and 8 are inherited from the
  encoder.
- **#180's 59 cases kept.** `GlassPathFieldBoundedTest` now compares every case with the whole-layer reference.
- **New limit test.** A 2148 x 308 px layer at bands 679, 680 and 681 (half-span just under, on and just over 1020 px)
  exercises the longest packed offsets and the fallback. Byte-identical on both sides.
- **#180 kept as a benchmark mode.** `tools/benchmarks/pathfield/PreviousBounded.kt` is main's previous transform,
  renamed, so the port is measured against it in the same JVM.

## Gates (all green)

| Gate | Result |
| :-- | :-- |
| Full `:liquidglass:jvmTest --rerun` | 496 tests, 0 failures, 0 errors, 27 skipped (opt-in harnesses) |
| `:liquidglass:checkKotlinAbi` | no public API change |
| `:liquidglass:compileAndroidMain`, `:liquidglass:compileKotlinWasmJs` | compile |
| `GlassPathFieldByteIdentityTest` (new, from the trial): raw Skia bytes plus `range`/`scale` bits against the whole-layer reference: notched panel, star, even-odd ring, islands, curve with a sliver, empty, full, pinhole, odd/even/fractional pads, bands 4-800, pooled and reused buffers, the three benchmark sizes; exhaustive integer-tie check | 6 / 6 |
| `GlassPathFieldBoundedTest`: #180's 59 cases against the reference, and the new limit test | 2 / 2 |
| `FieldBoundedBaselineTest` (opt-in): 59 cases against the unmodified upstream algorithm | identical |
| `FieldAllocationBenchmarkTest`: pixels of every mode equal to upstream, three sizes | identical |

## Timing and allocation

`FieldAllocationBenchmarkTest` with `tools/benchmarks/pathfield.init.gradle`. Notched panel, 24 px pad, band 48. Five
builders alternate in one JVM, 40 warm-ups, 61 samples each, median = sample 30. Two runs. Raw output:
[bench-raw.txt](bench-raw.txt).

| Size (px) | `main` 2f7580f, #180 (p50 ms, run 1 / 2) | This change (p50 ms) | Faster | Allocated per build, `main` to this |
| :-- | :-- | :-- | :-- | :-- |
| 512 x 256 | 6.26 / 6.13 | 2.91 / 2.90 | 2.1x | 1.88 MB to 0.34 MB (-82%) |
| 1080 x 300 | 13.31 / 13.43 | 5.68 / 5.71 | 2.35x | 11.00 MB to 2.45 MB (-78%) |
| 1080 x 720 | 22.78 / 22.88 | 10.05 / 10.09 | 2.27x | 24.28 MB to 5.42 MB (-78%) |

The same runs measured 13ec13b's transform (before #180) at 7.45-7.55, 17.56-17.60 and 38.64-38.71 ms. Windows desktop,
Microsoft OpenJDK 21.0.13. Wall time on a machine that was otherwise idle; the two runs agree within 2%.

## Limits

- Desktop JVM only. Android runs the same common code, but its mask read is different and **no test runs it**:
  byte-identity on Android is argued from the source (same canvas setup, same ARGB alpha byte as before) and compiles,
  but the device check is pending. No Android timing and no frame-rate claim.
- The tie table is built on first use (16,385 square roots). That cost was not measured separately; it is expected to be
  far below the first build's existing cold cost.
- Fields with a half-span above 1020 px (bands above 680) take the whole-layer transform and gain nothing.

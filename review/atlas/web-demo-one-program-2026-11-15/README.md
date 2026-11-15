# Web demo: one compiled glass program per shader (#78, PR 1) - 15 November 2026

Source: branch `perf/78-one-glass-program` (main `0114b379` plus the changes below), production
`:web:wasmJsBrowserDistribution` builds of that branch and of a clean `origin/main` worktree. Programs counted with
`DUMP_DIR=<dir> node tools/measure_web_demo_shaders.mjs <dist> <hash> 1` (installed Chrome, headless, ANGLE/D3D11, AMD
integrated GPU, Windows 11). **Chrome on one machine.** No Firefox, Safari or phone. No screenshots: nothing visible changes.

## What this does and does not claim

- Claimed (measured): the number of WebGL programs Skia compiles, per route, before and after.
- Claimed (measured): the desktop render of docs/1 is pixel-identical to main, and a star-shaped glass renders
  identically with the field image declared premultiplied or unpremultiplied.
- **Not claimed: any load-time saving.** Load time was NOT re-measured: no quiet machine was available, and the `ready`
  and link times in `raw/` were taken while other builds and tests ran, so they are not evidence. The expected saving
  (one fewer ~39k and one fewer ~22k link) is an estimate from the 7 November per-program link times, not a measurement.

## Cause

Skia wraps an image child declared unpremultiplied in a `ColorSpaceXform` that premultiplies after the texture read.
The path-field image (`createGreyscaleFieldBitmap`) and the 1x1 placeholder field (`placeholderFieldShader`) were both
`RGBA_8888` `UNPREMUL`, while a premultiplied child is also bound to the same runtime effects on the route, so Skia
generated, and the browser linked, two versions of each. The dumped sources of the two material programs differed in
exactly one helper: `ColorSpaceXform_S1_c0_c1` (texture read, then `_0_color.xyz *= _0_color.w`) against
`MatrixEffect_S1_c0_c1` (texture read only).

Change: both images are declared `PREMUL`. Every alpha byte is 255, so the bytes are the same image; premultiplying
and not premultiplying 255 is the identity. `createFieldBitmap` (caller-supplied alpha, used by tests only) stays
`UNPREMUL`. Android uses a different actual and is untouched. No shader source changes, so ports and fixtures are untouched.

## Programs (measured, visit 1, programs over 15k characters are "heavy")

| Route | main: heavy / total | this branch: heavy / total | heavy sizes (chars) after |
| :-- | --: | --: | :-- |
| `/#material3` | 5 / 49 | 3 / 46 | 40294 (wide-input material), 39086 (material), 22050 (content) |
| Atlas Studio (empty hash) | 6 / 36 | 4 / 33 | 39723, 40294, 39086, 22050 |

Main's heavy sizes: `/#material3` 40437, 39229, 39086, 22116, 22050; Atlas Studio 39866, 40437, 39229, 22116, 39086, 22050.
Raw probe output is in `raw/` (`probe-<build>-<route>.txt`); the dumped `.frag` sources are not committed.

What remains: the wide-input material variant (`GlassWideInput.skiko.kt`) is still a second material program (a third child,
`uclamp`, `udecalBounds` and an extra matrix). Folding it is PR 2, conditional on its own experiment (plan 78.md).
Atlas Studio also has a 39723-character program (the container pair) that this change does not touch.

## Identity (measured)

- docs/1 replay, calm, script3, 1934 frames (f00000-f01933; the 1935 files in the folder are those PNGs plus
  `trace.csv`): the branch against the main render `0114b379`, **0 frames differ** (`np.array_equal` on every PNG), and
  `trace.csv` is byte-identical. The desktop JVM runs the same Skiko code. docs/1 has no custom-path field.
- `GlassPathFieldAlphaTypeTest` (jvmTest, 2/2 green): every alpha byte of the production field is 255 and its alpha type is
  PREMUL; a star-shaped glass (`uShapeKind` 1) rendered with the same field bytes declared PREMUL and declared UNPREMUL
  gives equal pixels. This covers the path field that docs/1 lacks.
- Focused suite: `GlassPathField*` (7 classes), `GlassFieldPadConsumerTest`, `GlassPadContractTest`,
  `GlassWideInputBoundsTest`: 42 tests, 0 failures. The full suite is CI's gate.

## Limits

Chrome, one AMD iGPU. Program counts depend on the routes visited by the probe (initial load only). Timing, phones and other
browsers are open; quiet interleaved timing x3 (plan 78.md 3.B) is still to do.

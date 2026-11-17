# Colour transfer (#201) — 17 November 2026

Source revisions: main after #200 (`8b303111`) against the #201 branch `feat/201-colour` (`091c67d0`). In each image
the left half is main and the right half is #201, separated by a 6 px grey gap. The iOS lab stills the gains were measured
from are private, so only numbers measured from them appear here, never frames.

## What changed

Over flat colour, Regular glass now takes the grey tone of the colour's Rec. 709 luma and keeps the chroma about it at a
measured per-channel gain, `tone(y) + (g0 + g1 y) (x - y)`. Before this change it ran each channel through the grey
curve. The gains are measured at Tint 50 (section 2f of `docs/research/measured-model.md`) and derived from
`GlassStyle.detailLift`, so a style with no `detailLift` keeps the per-channel tone. "Default look changed" is listed in
the CHANGELOG.

## Images

### Colour fixture, `colour-transfer-<dark|light>.png`

The sample's `ColourTransferActivity` (`ColourTransferScene`, shared with the desktop app) has nine flat bands: red,
orange, yellow, green, cyan, blue, magenta, dark magenta and grey 128. A Regular pill (`Glass.Regular`, Tint 50, 220 x 48 dp)
sits at the centre of each band, with no content, so the pill's interior shows the material alone.

- **Source:** the app's own scene through the public `glassEffect` modifier, rendered by `ColourTransferRenderTest`
  (desktop, opt-in: `:desktop:desktopTest --tests "*ColourTransferRenderTest*" -Patlas.colourTransfer=true`; ordinary
  test runs skip it). PNGs land in `apps/desktop/build/reports/atlas/colour-transfer/`.
- **Method:** desktop JVM, Skia CPU raster in the Compose UI test harness, 390 x 844 dp at density 3 (1170 x 2532 px).
  The "before" render is the same scene and test over the library at `8b303111`. To reproduce it without touching your
  working tree or any existing worktree, make a fresh one in a new temporary directory, with this branch's sample and
  test files and main's library committed on a detached HEAD, and remove only that worktree:

  ```sh
  set -e
  dir="$(mktemp -d)/lg-before"
  git worktree add --detach "$dir" HEAD
  git -C "$dir" checkout 8b303111 -- liquidglass/src
  git -C "$dir" commit -q -m "Colour-transfer before render: main's library"
  (cd "$dir" && ./gradlew :desktop:desktopTest --tests "*ColourTransferRenderTest*" -Patlas.colourTransfer=true)
  out="$(mktemp -d)"
  cp "$dir"/apps/desktop/build/reports/atlas/colour-transfer/*.png "$out"
  git worktree remove "$dir"
  echo "before renders in $out"
  ```

  Compare the renders in that new directory with the left (main) half of the images here; the steps never write into the
  repository.

  `set -e` stops at the first failure, and `git worktree remove` without `--force` refuses a worktree with changes
  (build output is ignored, so it does not count).
- **Limits:**
  - These are not GPU or device frames and not a comparison with iOS.
  - The bands are 94 dp tall, so the 15.5 dp wide kernel mixes in a little of the neighbouring band near the pill's
    top and bottom edges.
  - The Pixel 7 capture of the same activity is pending:
    `adb shell am start -n com.wexpa.liquidglass.sample/.ColourTransferActivity [--ez dark false]`.

![Colour fixture, dark, desktop JVM Skia CPU raster: main | #201](colour-transfer-dark.png)

![Colour fixture, light, desktop JVM Skia CPU raster: main | #201](colour-transfer-light.png)

Mean of the pill's middle (330 x 72 px) in each render, R, G, B levels of 255. This is a regression read of the images
above, not a comparison with iOS:

| Page | Dark, main | Dark, #201 | Light, main | Light, #201 |
| :-- | :-- | :-- | :-- | :-- |
| red 255, 0, 0 | 185, 32, 32 | 255, 17, 15 | 255, 133, 133 | 255, 108, 107 |
| orange 255, 128, 0 | 185, 134, 32 | 255, 125, 0 | 255, 197, 133 | 255, 188, 67 |
| yellow 255, 255, 0 | 185, 185, 32 | 197, 198, 0 | 255, 255, 133 | 255, 255, 30 |
| green 0, 255, 0 | 32, 185, 32 | 0, 235, 0 | 133, 255, 133 | 54, 255, 51 |
| cyan 0, 255, 255 | 32, 185, 185 | 0, 222, 222 | 133, 255, 255 | 47, 255, 255 |
| blue 0, 0, 255 | 32, 32, 185 | 26, 27, 255 | 133, 133, 255 | 124, 124, 255 |
| magenta 255, 0, 255 | 185, 32, 185 | 255, 12, 255 | 255, 133, 255 | 255, 99, 255 |
| dark magenta 128, 0, 128 | 134, 32, 134 | 181, 22, 181 | 197, 133, 197 | 240, 116, 240 |
| grey 128 | 134, 134, 134 | 134, 134, 134 | 197, 197, 197 | 197, 197, 197 |

The grey band is unchanged, as the correction is zero over grey.

## Scores (private lab stills, Tint 50, toolbar pill, sRGB; measured on `091c67d0`)

| Gate | Dark | Light | Limit |
| :-- | :-- | :-- | :-- |
| 125-point RGB cube, rms / max | 1.05 / 7.1 | 0.85 / 4.8 | 3 / 10 |
| Colour-pair stripe stills (8), worst rms | 1.66 | 1.53 | 5 |
| Grey lab stills (89), rms | 1.43 | 1.42 | unchanged from #200 |

- One shared gain instead of per-channel gains reached 9.5 levels (model) and 10.1 (rendered) on dark magenta, which
  fails the max-10 gate.
- The cube's worst points are the zero channels of colours that iOS shows outside the sRGB gamut. Dark magenta's green,
  for example, reads 12 here against 19 on iOS.
- Public tests:
  - `GlassLabModelTest` holds 27 cube points within 2 levels of the model.
  - `GlassContainerMaterialTest` holds merged glass within 2 levels per channel of a lone piece over six saturated
    pages.
  - The docs/1 replay renders 0 of 1934 frames differently from main after #200, because every glass in that scene has
    weight 0.

## Morph midpoint

A dark-to-light morph of Regular glass (`lerpGlassStyle(toolbar(dark), toolbar(light), 0.5)`) has `detailLift` 0 at its
midpoint, so the transfer is off there.

- Over eight saturated pages, 20 of 24 channels read less saturated at the midpoint than at both ends, and none reads
  more saturated than both.
- Over red, the ends read 255, 17, 15 (dark) and 255, 108, 107 (light); the midpoint reads 222, 82, 82.
- These numbers are printed by `GlassLabModelTest.appearanceMorphMidpointColour`, and the CHANGELOG states the dip.

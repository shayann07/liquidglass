# Scroll edge effect on a Pixel 7 (#207)

`pixel7-top-edge-dark40-dark200-light40-light200.png`: the top 420 px of `ScrollEdgeActivity` (Android sample), left to right:
dark at scroll offset 40 px, dark at 200 px, light at 40 px, light at 200 px (item 2 first visible). Magenta separates the
frames. At offset 40 a flat grey-64 block runs under the edge; at offset 200 black and white stripes enter the band.

Capture: Pixel 7, Android 17, debug build of `feat/207-device` at `53784bd5` (main `3fe997d4` plus the fixture),
`adb shell screencap` (lossless PNG, 1080 x 2400, 2.625 px per dp), cropped and scaled to 0.5. Scores read from the full
frames with a script; not owner acceptance.

Against the measured iOS 27 profile (Safari top edge: blend toward grey 124 at alpha 0.41 x (1 - smoothstep(30, 102 px at
3 px per pt)), light: no tint), over the grey-64 block, columns 380 to 700, rows 0 to 139, converted by dp:

| Case | rms against the iOS profile | worst |
| :-- | --: | --: |
| dark, offset 0 | 0.3 levels | 0.8 |
| dark, offset 40 | 0.3 levels | 0.8 |
| light, offset 0 | 0.2 levels | 1.0 |
| light, offset 40 | 0.2 levels | 1.0 |

The gate was 5 levels rms. At offset 200 the block is only 52 px tall and the stripes below it blur up into it (worst 11
dark, 16 light at rows 40 to 47): that is the blur, which iOS also has, not the tint. Blur over the stripes at offset 200:
the edge spread falls from about 5.8 dp sigma (window-limited) 21 dp from the edge to 0.9 at 33 dp and 0.1 at 35 dp, sharp
from 37 dp; iOS is sharp by 34 to 36 pt. Dark and light agree.

Fixture note: the first device run had the dark tint missing because the fixture did not pass `inverted = dark`;
`53784bd5` fixes the fixture. The library was unchanged.

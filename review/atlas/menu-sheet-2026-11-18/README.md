# Dark menu and sheet on the measured pop-up and share-sheet material (#102, #204)

`pixel7-menu-light-before-after-dark-before-after.png` and `pixel7-sheet-light-before-after-dark-before-after.png`: the
Material 3 showcase of the Android sample (`MaterialShowcaseActivity`), `DropdownMenu` open and `ModalBottomSheet`
open. Left to right: light before, light after, dark before, dark after. Magenta separates the frames.

Capture: Pixel 7, Android 17, debug builds of the sample, `adb shell screencap` (lossless PNG, 1080 x 2400), status
bar cropped, scaled to 0.4. Before is `main` at `3fe997d4`; after is `feat/102-sheet-menu`. One screenshot each, read
by eye and with one crop mean; not a calibrated comparison and not owner acceptance.

What changed:
- Dark menu: from the Safari `<select>` panel (black reads 32) to iOS 27's pop-up menu, measured on the Quick Look
  title-pill menu (black reads 59, tone peaks near 129, 8.17 dp blur). The menu interior over the page lifts from a
  mean of 89.6 to 95.3 levels in the same crop. The light menu is unchanged (239.4 in both).
- Sheet: `ModalBottomSheet` draws `GlassMaterialDefaults.sheet`, measured on iOS 27's share sheet, instead of the card
  panel: a pure blur (7.17 dp dark, 8.67 dp light) with no detail term, so the artwork under it is frosted rather than
  sharp-edged, and the tone is compressed (dark black 59, light black 145).

Scores (jvmTest `GlassMenuModelTest`, the production modifier in a Compose scene, Skia raster, against the measured
grey tables; numbers only, no capture material committed): menu dark 2.80 levels rms over 17 greys (16 measured, the plateau repeated at 255) and 3.11 over 8 stripe
cases; sheet dark 3.00 and 2.79; sheet light 2.25 and 1.82; menu light (unchanged) 1.00 and 1.08.

Source measurements: `.local` decode of iOS 27 video takes (iPhone 13 class, HDR10 decoded to display sRGB), Quick Look
over the calibration chart: `sheet-dark`, `sheet-dark-long18s`, `sheet-light`, `menu-dark`, `titlepill-dark`. Not
measured: rim, outer shadow, corner, grabber, colour (chroma 0.67 to 0.69 dark was measured but is not fitted here),
a light pop-up menu (none recorded), and the wider blur tail near 11 to 12 dp.

Known difference, open: iOS does not dim the page behind the share sheet (ratio 1.000 measured); the sample's
`ModalBottomSheet` still draws its light scrim.

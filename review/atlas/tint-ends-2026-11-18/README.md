# Tint 0 and Tint 100 on the measured fit (#208)

`<appearance>-tint<N>-before-above-after.png`: the toolbar style (`GlassStyle.toolbar`) in the lab scene (`GlassLabScene`: the
production modifier and host in a Compose `ImageComposeScene`, Skia raster, jvmTest, 3x, 632 x 143 px capsule plus 10 px),
over a page of 12 px bands, black 30 px stems on grey 128 alternating with white. Top: before (source revision `0a1282fb`,
the earlier authored values at the ends). Bottom: after (the fitted ends). Magenta separates the two.

Zone luma in the pill's interior over flat grey 0 / 128 / 255 (jvmTest render), against the fit's own tone curve
`T0 + (T1 - T0) x + bow x (1 - x)` at those inputs:

| Case | before | after | fit (Photos chrome, Python) |
| :-- | :-- | :-- | :-- |
| dark Tint 0 | 35.0 / 93.0 / 150.0 | 32.0 / 134.0 / 185.0 | 31.9 / 134.0 / 184.6 |
| dark Tint 100 | 35.0 / 83.0 / 130.0 | 32.0 / 82.0 / 108.0 | 32.5 / 83.0 / 108.3 |
| light Tint 0 | 141.0 / 198.0 / 255.0 | 102.7 / 182.0 / 255.0 | 103.1 / 183.2 / 252.7 (white end clipped) |
| light Tint 100 | 170.0 / 213.0 / 255.0 | 180.0 / 220.0 / 255.0 | 179.4 / 220.5 / 253.7 (clipped) |

Provenance: the fit is **measured** (`research/datasets/mac-rig/captures/C1a-*`: iPhone 13, iOS 27, 1170 x 2532, lossless
SDR; rms 1.40 / 2.20 / 1.29 / 2.07 levels against 15.98 / 24.79 / 10.75 / 5.63 for the earlier authored values on the same
captures, in the order dark 0, light 0, dark 100, light 100). The interpolation between Tint 0, 50 and 100 is **authored**.
Tint 50 is the Safari-lab fit and unchanged.

Limits: the fit pools two elements (the Today pill and the toolbar capsule) from one backdrop family (stripes, a hard split,
flat anchors); the scores are Python forward-model scores, not shader renders; the light white end is clipped, so its slope is
not identifiable; the 2 px detail, the rim and colour at Tint 0 and 100 were not measured. The table is a render of flat
greys against the fit's curve: a regression check, not a comparison with a capture and not owner acceptance. The light fit
column at 128 is computed from the fitted T0, T1 and bow in the plan; the dark ones likewise.

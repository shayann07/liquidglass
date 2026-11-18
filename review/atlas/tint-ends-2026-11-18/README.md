# Tint 0 and Tint 100 on the measured fit (#208)

`<appearance>-tint<N>-before-above-after.png`: the toolbar style (`GlassStyle.toolbar`) in the lab scene (`GlassLabScene`: the
production modifier and host in a Compose `ImageComposeScene`, Skia raster, jvmTest, 3x, 632 x 143 px capsule plus 10 px),
over a page of 12 px bands, black 30 px stems on grey 128 alternating with white. Top: before (source revision `0a1282fb`,
the earlier authored values at the ends). Bottom: after (the fitted ends; branch `feat/parity-shader-bundle` at `8c867819` and `fe4b0414`, #208). Magenta separates the two.

Zone luma in the pill's interior over flat grey 0 / 128 / 255 (jvmTest render), against the fit's own tone curve
`T0 + (T1 - T0) x + bow x (1 - x)` at those inputs:

| Case | before | after | fit (Photos chrome, Python) |
| :-- | :-- | :-- | :-- |
| dark Tint 0 | 35.0 / 93.0 / 150.0 | 32.0 / 134.0 / 185.0 | 31.9 / 134.0 / 184.6 |
| dark Tint 100 | 35.0 / 83.0 / 130.0 | 32.0 / 82.0 / 108.0 | 32.5 / 83.0 / 108.3 |
| light Tint 0 | 141.0 / 198.0 / 255.0 | 102.7 / 182.0 / 255.0 | 103.1 / 179.9 / 252.7 (white end clipped) |
| light Tint 100 | 170.0 / 213.0 / 255.0 | 180.0 / 220.0 / 255.0 | 179.4 / 217.2 / 253.7 (clipped) |

Provenance: the fit is **measured** (`research/datasets/mac-rig/captures/C1a-*`: iPhone 13, iOS 27, 1170 x 2532, lossless
SDR). Fit scores (Python forward model, pooled over the Today pill and the toolbar capsule): rms 1.40 / 2.20 / 1.29 / 2.07
levels against 15.98 / 24.79 / 10.75 / 5.63 for the earlier authored values on the same captures, in the order dark 0,
light 0, dark 100, light 100. Per element (the same fit, scored on each zone; `.local` fit output, numbers only):

| Case | pooled | toolbar capsule (zone B) | Today pill (zone T) |
| :-- | --: | --: | --: |
| dark Tint 0 | 1.40 | 0.45 | 2.28 |
| light Tint 0 | 2.20 | 0.74 | **3.59** |
| dark Tint 100 | 1.29 | 0.58 | 2.03 |
| light Tint 100 | 2.07 | 0.79 | **3.32** |
| light Tint 50 (shipped lab fit, unchanged) | 2.07 | 0.76 | 3.35 |

Stills: each case has two captures, over the light and the dark chart file, whose scored zones hold the same greys; each
still scored on its own gives the same rms as the table (the fit output's "on repeat" pair, e.g. 1.40 and 1.40 for dark Tint 0),
so the table holds per still as well as pooled. They are rig repeats, not independent replicates.

The #208 gate (3 levels rms on the usable Tint 0 and 100 stills) was scored pooled over both elements, as the plan set it,
and passes that way. Per element it passes for the toolbar capsule everywhere and for the Today pill in dark, and **misses
for the Today pill in light** (3.59 and 3.32). The Today pill misses by the same amount at Tint 50 with the shipped Safari-lab
material (3.35), so the miss is the zone, not the new tint ends; it is an open known difference. A pooled rms does not bound a
single point's difference: the point differences between the render and the fit are the table above (the light renders sit 2.1
and 2.8 levels above the fit at grey 128). The interpolation between Tint 0, 50 and 100 is **authored**. Tint 50 is the
Safari-lab fit and unchanged.

Limits: the fit pools two elements (the Today pill and the toolbar capsule) from one backdrop family (stripes, a hard split,
flat anchors); the scores are Python forward-model scores, not shader renders; the light white end is clipped, so its slope is
not identifiable; the 2 px detail, the rim and colour at Tint 0 and 100 were not measured. The table is a render of flat
greys against the fit's curve: a regression check, not a comparison with a capture and not owner acceptance. The fit column at 128 is computed from the fitted T0, T1 and bow. The light renders sit 2.1 (Tint 0) and 2.8 (Tint 100) levels above it at 128: the light fit's
clipped white end and its small bow are not in the style (light bow 0, white end 255). That is a point difference, reported
separately from the pooled fit scores.

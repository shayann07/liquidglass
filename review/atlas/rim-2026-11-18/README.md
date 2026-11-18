# Rim and outline, 18 November 2026 (#202, #109)

Numbers only. iOS values are **measured** on private recordings and lossless stills (iPhone 13 class, 3x, HDR10
decoded to display sRGB); no iOS frame is published. Ours are **measured** on desktop renders of branch
`fix/202-rim` (Skia raster in jvmTest for the lab scenes, desktop GPU replay for the Phone scene). Not yet checked on
a Pixel 7.

## 1. Outline coverage

iOS 27's outermost glass pixel is fully covered and its anti-aliasing spans 1.00 px: over the Safari lab circles and
capsules its coverage across the outline matches a one-pixel box ramp `clamp(0.5 - d, 0, 1)` to 0.017 rms
(the earlier 1.5 px smoothstep: 0.025; source: Antigravity m202c, its port reproducing all five of our d0 readings
to the level). All four shaders now use the box ramp.

## 2. iOS 27 rim, Material 3 toolbar (`Ios27Rim`), Tint 50, overhead light

Rim excess over the interior at d px inside the outline, over flat greys (Safari lab pill for iOS):

| case | main d 0..3 | `fix/202-rim` d 0..3 | iOS d 0..3 |
| :-- | :-- | :-- | :-- |
| dark, grey 0 | not rendered | +67 +47 +28 +14 | +67 +47 +31 +13 |
| dark, grey 128 | +73 +65 +45 +24 | +67 +47 +28 +14 | +66 +46 +31 +12 |
| dark, grey 255 | not rendered | +67 +47 +28 +14 | +64 +45 +30 +12 |
| light, grey 0 | not rendered | +67 +47 +27 +14 | +65 +46 +30 +12 |
| light, grey 128 | not rendered | +58 +47 +28 +14 | +58 +45 +30 +12 |

Main was rendered under the overhead light over grey 128 dark only. d 4..12 is unchanged and within 1 level of iOS. The bottom rim reads the same as the top. A piece merged in
`GlassEffectContainer` reads the same as a lone piece in every row (before: +62 +53 +32 +12, then 0 from d 4,
because merged glass lacked the resting glow).

Scope: the falloff applies to resting glass drawing the 2026 outer contour (Material 3's iOS 27 rim,
`GlassTabBarStyle.Ios27`). On the recorded dark `Calm()` bar it dimmed the runs from 98 to 88 against iOS's 100,
so that bar keeps its own fitted rim.

## 3. Dark Calm bar and its search button (IMG_6756 34.2 s, `ref/rest-keypad.txt` replay)

| measure | main | `fix/202-rim` | iOS |
| :-- | :-- | :-- | :-- |
| bar top rim, rows 0..4 | 98 88 65 45 40 | 100 84 63 45 40 | 99 79 62 45 41 |
| bar top rim along x, rms | 4.9 | 5.3 (gate 5.4) | — |
| search button rim, 18 angles, rms (max) | 8.7 (19) | 2.5 (5) | — |
| search button rim peak | 104 | 98 | 96 |

Button rim by angle (0 = right, 90 = top):

| angle | 0 | 15 | 30 | 45 | 60 | 75 | 90 | 105 | 120 | 135 | 150 | 165 | 180 |
| :-- | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: |
| iOS | 32 | 34 | 42 | 50 | 59 | 73 | 96 | 70 | 58 | 52 | 42 | 35 | 32 |
| main | 32 | 35 | 39 | 47 | 64 | 88 | 104 | 88 | 64 | 47 | 39 | 35 | 32 |
| `fix/202-rim` | 32 | 37 | 44 | 55 | 60 | 73 | 98 | 73 | 60 | 55 | 45 | 38 | 34 |

The bar's caps now read 5 to 10 levels brighter than iOS's (x 160-200 and 800-820); the straight runs match.

## Limits

- Desktop only; the Pixel 7 check is owed (AGENTS rule 4).
- The Calm bar's specular (0.25 -> 0.226) and the round-button factor (0.97) are authored fits to the IMG_6756 rows
  above; the rim lobe (0.47 f^41 + 0.53 f^1.8) is a fit to the measured angular profile.
- #202's stills gate and cap scores are still open.

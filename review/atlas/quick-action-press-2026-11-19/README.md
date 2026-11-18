# Quick-action press (`GlassInteraction.QuickAction`, `Glass.ControlCenterTile`) - 19 November 2026

Numbers only; no frame of the private recording is in this repository. Source: `IMG_6667.MP4` (iPhone lock screen, flashlight off,
dark; one tap and one 1.9 s hold; display colours), read as in `review/atlas/flashlight-press-2026-11-18`. Fit scripts (private):
`.local/sxs/cc206b/press.py`, `press2.py`, and the spring and easing fits of 19 November over `press.json`.
The Control Center circles were not recorded pressed; they are assumed to take the lock-screen press (authored).

## Measured and fitted

| quantity | measured | preset |
| :-- | :-- | :-- |
| held radius over rest | 1.605 | `pressScale` 1.60 |
| peak (hold) | 1.694, 0.19 s after the first growth frame | spring damping 0.53, stiffness 300 (fit rms 0.015) |
| release | min 0.900 (tap 0.889) 0.15-0.17 s after the first shrinking frame | spring damping 0.49, stiffness 490 (hold rms 0.003, tap from 1.648 rms 0.003) |
| body level rise | 18 to 137 in a line over about 125 ms (rms 3.7 of 119; ease-out 3.9, ease-in-out 5.1) | linear 125 ms |
| body level at the lift | back at rest on the first frame | snap |
| white share | 0.502 over black (18 to 137), 0.525 over a 125 stripe band (133 to 197) | 0.51 (errors +1.9, -1.8 levels) |

## Ours (`GlassQuickActionPressTest`, jvmTest render at 2x, 60 Hz frames; not a device capture)

| quantity | ours | measured |
| :-- | :-- | :-- |
| held | 1.607 | 1.605 |
| peak, time | 1.691, 0.192 s | 1.694, 0.19 s |
| release min, time | 0.897, 0.144 s | 0.900, 0.16 s |
| settled | 1.000 | 1.0 to 1.012 |
| share of the way to white, black / flat 125 | 0.510 / 0.505 | 0.502 / 0.525 |
| level 5 to 95 percent | 0.112 s | 0.117 s |
| levels, black | 51 to 155 (+104) | 18 to 137 (+119) |
| levels, flat 125 | 164 to 210 (+46) | 133 to 197 (+64) |

The absolute levels differ because the Control Center tile rests at 51 over black where the lock-screen button rests at 18 (a different
material); the measured quantity, the share of the way to white, matches. A flat 125 stands in for the stripe band (mean 125).

## Not measured, authored

Other sizes (the 1.60 is a ratio from one 58 pt button), the light appearance, the flashlight on state and glyph, a moved or
cancelled press, Reduce Motion (the system response replaces it), the Control Center circles pressed. The ports and merged-glass
containers do not have the term.

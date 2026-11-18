# Lock-screen flashlight press against our round-button presses (#113 item 128) - 18 November 2026

Numbers only. The source, `IMG_6667.MP4` (iPhone, 1170 x 2532, HDR10, 5.17 s, 195 frames, variable rate), is a private recording and
no frame of it is in this repository. `MUQJ3680.MP4` has the same MD5 (1d82368b9c041b727afd5737542fc4bb): one clip, `IMG_6667` is
the one read. Decode: `.local/sxs/hdr.py` (display sRGB, white 255). Method: the lit disc's radius from a circle fit to its left and
right edges on the rows that cross the black band behind it (about 1 px), its body level as the median of the disc over that band
(and over the stripe band below, for the second backdrop). Scripts: `.local/sxs/cc206b/press.py`, `press2.py` (private).

The take has two presses of the same button: a quick tap (1.2-1.5 s) and a 1.9 s hold (2.47-4.32 s). Backdrop: a black band above
a vertical-stripe band (mean 125 outside the button).

## Measured (iOS 27 lock screen, flashlight off, dark)

| quantity | tap | hold |
| :-- | :-- | :-- |
| rest diameter | 58 pt (radius 86.8 px at 3x) | same |
| peak scale | 1.65 at 0.17 s after onset | 1.69 at 0.20 s |
| held scale | | 1.59-1.60 (93 pt), settled by 0.33 s |
| onset to full body level | | 0.15 s |
| release | shrinks through 1.0 in 0.12 s | 1.605 to 1.0 in 0.12 s, to a minimum of 0.90 (a 9 px smaller radius) at 0.17 s, back at 1.0 by 0.28 s |
| body level over the black band | 18 at rest, 137 pressed (+119) | same |
| body level over the stripe band | 133 at rest, 197 pressed (+64) | same |

Over the two backdrops the pressed body reads about 0.52 white plus 0.48 of the backdrop (137 = 0.54 x 255 over black; the slope
197 - 137 over a 125 backdrop is 0.48). The release undershoot agrees with the audit's "flashlight button release undershoot -10 px"
(`review/atlas/ios-audit-2026-10-26`, "Already tracked").

The earlier row of `docs/research/measured-model.md` section 7 ("+39 luma, ~170 ms in, ~200 ms out") has timing of the same order (170 ms in
against 0.15-0.20 s here; 200 ms out sits between the 0.12 s back to rest size and the 0.28 s to settled), but its +39 is not
reproduced by the HDR decode. A plain (non-HDR) decode of the same frames reads +63 over the black band and +20
over the stripes, whose mean is +41: the old figure looks like the pre-HDR reading averaged over a mixed backdrop (inferred, not confirmed). The measured-model row
keeps both and says which is current.

## Ours (computed from the preset values, not rendered)

| preset | scale for a 58 pt button | lightening |
| :-- | :-- | :-- |
| `GlassInteraction.Calm` (the default of `Glass.interactive()`) | (58 + 2 x 2) / 58 = 1.07 (`pressGrowth` 2 dp a side) | `pressLift` 0.04, about 10 levels |
| `GlassInteraction.Pullable` (the search button's, IMG_6756 186 to 238 px) | (58 + 2 x 8) / 58 = 1.28 | `pressLift` 0.2, about 51 levels |
| iOS lock-screen flashlight | 1.59 held, 1.69 peak | +119 over black, +64 over the stripes |

Verdict: the lock-screen quick-action button's press is a different, much larger response than either of ours, so a round glass
button does **not** match it by default, and the default is not changed here: a 1.6 x grow would apply to every free round glass
button, the measured search button (1.26-1.28) must keep its own press, and the port mirrors (`ports/skia/interaction.mjs`,
`FreeButton.cs`, `fixtures/free-button.json`) are out of reach of this change. Open owner question: should a named large-press
interaction (peak 1.69, held 1.60, 0.20 s in, 0.12 s out, undershoot 0.90, lighten to 0.54 white) be added as an additive
`GlassInteraction` preset for quick-action buttons? Not measured: the flashlight's on state, the glyph, light appearance, the
glow's direction.

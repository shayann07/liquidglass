# The iOS 27 calibration study

How the numbers in [the measured model](measured-model.md) were obtained, what went wrong on
the way, and what is still open. This is the record of the study; the model page is its
result, and [ios27-measurements-raw.md](ios27-measurements-raw.md) is the verbatim log of the
first measurement pass that this study superseded.

Dates: captures 2026-09-10 and 11, analysis and fitting 2026-09-11 and 12. Device: iPhone 13,
iOS 27.0 (24A5430a), 1170×2532 at 3×, Reduce Transparency, Increase Contrast and Reduce Motion
all off, Tint Amount at the default 50 unless stated. The raw captures, the frame catalogue,
the analysis tools and every intermediate number live in the capture repository
(`research/datasets/ios27-phone/`, kept in this repository but outside the published site); the file names below refer to it.

## 1. Why a calibration target

Every earlier attempt to tune this material — ours and everyone else's — worked by eye against
screenshots of arbitrary app content. That can tell you a rim is too bright; it cannot tell you
by how much, and it cannot separate blur from tint from displacement, because the content under
the glass is unknown.

So the phone was shown a **known test image** instead: a 1170×2532 PNG (one light, one dark
variant) laid out in bands, each band a probe for one property:

| band | what it probes |
| :--- | :--- |
| hard vertical black/white split | edge spread across a step: blur, and the tint's black and white levels |
| grey ramp 0→255 | the material's transfer curve |
| vertical stripes at 4, 8, 16, 32, 64 px | modulation transfer: how much detail survives, per frequency |
| horizontal stripes at the same periods | the same across the other axis, and vertical displacement |
| primaries band (R G B C M Y W, 166 px each) | colour boundaries whose displacement through a lens can be tracked, and dispersion |
| type specimens at 11-17 pt on black and on white | legibility, and text-shaped detail under the glass |
| checker and a labelled grid in points | geometry and registration |
| a labelled tab-bar zone | where app chrome sits |

The image was opened full-screen in Photos, set as the Lock Screen and Home Screen wallpaper,
and had every glass surface iOS offers dragged, opened or pulled over it, on video and in
lossless screenshots. Because the input is known pixel for pixel, the output can be compared to
it pixel for pixel, and each glass property becomes a fit rather than a guess.

Two flaws in the target were found in use and belong in the next revision: the `0123456789`
specimen parses as a phone number, so a long press near it opens iOS Live Text's data-detector
menu instead of whatever was intended; and there is no flat mid-grey patch large enough to
measure the material's response to a 50% backdrop directly.

## 2. What was captured

**By hand, with the phone's own recorder** (HEVC 10-bit PQ, 60 fps): four slow cover-sheet
pulls over each target, a minute each; Control Center over both targets in both appearances;
two Home Screen tours with Clear icons over the dark-target wallpaper; the Lock Screen at rest
with a button press; and 33 lossless stills of the Photos chrome. The slow pulls are the core of
the lens data: a real finger holds the sheet wherever it pauses, and only a real finger does.

**From a borrowed Mac, through WebDriverAgent** over the same phone (SDR H.264, 60 and 120 fps,
lossless PNG screenshots), with every setting read back from the accessibility tree before each
capture: the Tint Amount sweep (0, 29/31, 35, 50, 62, 86, 100, both appearances, both targets,
Photos chrome visible and Control Center open); the share sheet; the data-detector menu; eight
synthetic cover-sheet pulls; chrome show/hide and Control Center timing recordings; the Home
Screen set over both targets with light-appearance twins; and three finger pulls at Tint 0, 50
and 100 recorded by the Mac while the user's hand did the pulling.

**What the rig could not do, and why** (each a real limit, not a time limit): a timer banner
(the timer picker refuses synthetic input); setting a wallpaper (iOS 27 has no Settings picker
and the Lock Screen editor needs the phone locked, which WebDriverAgent cannot do); a keyboard
over the target (Photos exposes no text field); and any *tracked* gesture — synthetic drags
commit the cover sheet in about three seconds however slowly they are asked to move, and the
edge lens never forms on a committed pull. The rule that fell out: the rig is reliable for
screenshots and recordings of static states and unreliable for anything that must be held,
typed or has been moved by Apple. Plan captures around that split.

Every delivered file was verified against a sha256 manifest before the Mac was wiped; four
frame sets were withdrawn as invalid (a personal photo library as backdrop, a menu that never
opened, bare targets, an empty gesture) and are absent by design.

## 3. Two mistakes that nearly lost the dataset

**Registration by mean.** The first pass judged whether a frame was "the target" by the mean
over a region — a region that was supposed to change. That declared the videos hopeless. The
right test is exact match on uncovered pixels, which is what `scan2.py` does per row.

**The recordings are HDR.** The phone's own recorder writes HEVC PQ BT.2020. Decoded naively,
white reads 127 and the primaries go muddy, and every number taken from those frames — a "55%
match", a "-183 px displacement", "Control Center desaturates the screen" — was an artefact of
the transfer function. The decode chain that recovers the target within one level is
`zscale=t=linear:npl=100 → linear float → zscale to bt709/iec61966-2-1, full range`. The Mac's
recordings are SDR and need none of it. Everything measured before the decode was fixed was
thrown away and redone.

## 4. Tools

All in Python with numpy, scipy, Pillow and cupy, in the capture repository's `tools/`:

- `scan2.py` — every frame of a video: PQ-correct decode, per-row exact match against the
  target, a changepoint detector for the sheet edge, the dim factor from the white margin, the
  RGB band around the edge for every frame, a frame every second and at every rest.
- `steps.py` — the edge mapping: nine isolated horizontal steps of the target are tracked
  through the lens; every appearance of a step is a sample of (source depth, display depth,
  orientation), split by target and by pull progress.
- `lens_profile.py` — tone by depth inside the edge over flat black and flat white, and the
  ramp transfer, pooled by progress.
- `cc_backdrop.py` — edge-spread fits (erf) per column across the primaries band's edges, for
  the blur and tone of full-screen layers.
- `still_elements.py` — for a rounded-rect element on a 1:1 still: tone by depth over flat black
  and white, the edge spread across the hard split, and stripe MTF per period.
- `icon_primaries.py` and `wallpaper_ref.py` — small elements over the wallpaper: the
  wallpaper's own transform and rendered colours, then colour-boundary displacement through the
  icons along the edge normal.
- `fit_model.py`, `esf2.py`, `fit_material.py`, `fit_material2.py` — the fits: tint against
  the slider, kernels against the pill's pixels, the dim curve, the lens by progress.
- `fit_material3.py` — the two-kernel form against a luminance-adaptive lift, pixel level.
- `rest_lens.py` — the lens of chrome at rest: one displacement per ring of depth inside a
  rounded end or a round button, by correlation around the ring.
- `fit_toolbar.py` — the toolbar as its own role, with the library's renders as the instrument
  that reads the blurred backdrop under the capsule.
- `lib_compare.py` — the same measurements run on the library's own renders;
  `device_compare.py` the same on a phone's screenshots of the sample's Calibration screen.
- `tabbar_lens/` — the tab-bar lens under a finger, from the LOLL8185 drags: strip decode, capsule
  outline from the rim and the dark side step, dense block matching against the resting frame,
  the band's mapping, the App Store frames, the JVM render against frame 1380, and the pill's rim
  by angle and depth with matched outlines (`rimfit.py`). `framestats.py` — stage medians from
  `dumpsys gfxinfo framestats`.

## 5. What was found, in the order it was established

1. **Nothing outside an edge is ever sampled.** The stripes right below the cover sheet's edge
   are crisp and unshifted in every frame. Every published implementation, this library's 0.1
   included, sampled outward.
2. **The cover sheet's edge is a fold**, not a bevel: identity from 0.9 of a 90 px band, a 2×
   stretch showing 0.71-0.9 of the band, a stationary seam at 0.70, and the outer 0.28 showing
   0.71-1.07 upside down. Content in the innermost 0.69 of the band is never displayed. Red
   shows content 0.07 of the band deeper than blue, in the mirrored zone only. Measured on
   13,000 frames of the finger pulls and reproduced at Tint 0, 50 and 100 and in both recorders.
3. **The fold forms with the finger.** Absent for the first ~350 px of pull, fully formed after,
   slightly relaxed late; never on a synthetic three-second pull, which shows only a 2-7 px
   inward offset. What the sheet covers is dimmed by `1 - 0.311 (1 - e^(-pull/450 px))`,
   saturating at 0.706, identically at every tint.
4. **The sheet shows the Lock Screen, sharp and at 0.97**, not the app: the lens refracts the
   sheet's own content. With a notification or Live Activity on the sheet iOS dims its wallpaper
   to 0.64, which confounded the Tint 0 and 100 pulls (the Mac's accidental timer was still
   running) and leaves the sheet material's own tint dependence unmeasured.
5. **Bevel width scales with the corner radius**: 0.55 ± 0.11 of it from a 15 dp icon to the
   sheet's 47 dp corner, so a fixed band is wrong in kind.
6. **The material is per role.** Control Center's backdrop is a 7.7 dp sigma with a tone that
   adapts to the content behind it (black → 60 over the dark target, → 10 over the light one,
   gain 0.32 in both); App Library and the Today view share that blur; Spotlight is 21-27 dp;
   a menu is 9.5 dp at gain 0.24 on a screen dimmed to 0.52; Clear icons 2.2 dp at gain 0.5; the
   dock 1 dp at gain 0.85; glass text is near-opaque. None of these is one material.
7. **Tint Amount is a continuous slider**, not two variants, and it acts on the element
   material: opacity 0.42 → 0.73 and the fine share 0.95 → 0 from Clear to Tinted, in both
   appearances; the system backdrops barely move (23 → 33 px sigma). Every capture before this
   was found had been at the untouched default of 50.
8. **The in-app material is a blend with a lift**, `(1 - a) B + a C + L`: light C is a constant
   241/255 at every slider position, L 0; dark C is black, L 35/255. This came out of the flat
   black and white levels alone and held at every tint.
9. **Two kernels.** The tone across a hard edge inside the pill follows a kernel of about
   10 dp, while 16 px stripes under the same glass keep a third of their contrast — impossible
   for one blur. A pixel-level fit of the pill over the test image (46,000 pixels per still,
   label and rim masked) gives a fine sigma of 0.5-1.6 dp and a wide sigma of 26-38 px, with a
   two-kernel RMS of 5-7 against 7-8.5 for the best single kernel.
10. **Shell glass ignores Light/Dark.** Between appearance twins of the same Home Screen over the
    same wallpaper, only glyphs, labels and clock hands change (0.1-1.3% of pixels); Spotlight's
    filled panel shifts 17 levels. In-app glass follows the app's scheme strongly.
11. **Chrome at rest has no edge ring.** The pill's bottom rows show the ramp directly beneath
    them; the fold is a tracked-sheet phenomenon. The pill's edge is a lit line: +33 at 2 px
    over black decaying to nothing by 16 px, additive white, so invisible over white in a light
    appearance, and shown on both sides in a dark one. No dark inner line.
12. **Timing:** a round button press is +39 luma with ~170 ms in and ~200 ms out; Photos chrome
    shows and hides within 1-4 frames with no fade; the Home Screen wallpaper is placed at a
    1.1375 scale shifted 290 px up (Perspective Zoom), dimmed to 0.773 under Clear icons, with
    the colour patches rendered desaturated.

## 6. From findings to model, and what the implementation corrected

`fit_model.py` turned the tables into curves and `measured-model.md` states them. Implementing
them and running the library through the same tools then corrected three things:

- The wide kernel is **10 dp**, not the ≥ 25 dp the edge-spread estimate suggested; that
  estimate was limited by its ±40 px window.
- **Chrome at rest gets no fold along a straight run**; the "10-20 px inward sampling" read off
  the toolbar's stripe levels was the lit bevel's luma profile, not displacement. Static chrome
  sits at lens formation 0. (Half right, see the next section: the arcs do lens at rest.)
- The dark opacity curve is read from the **measured table**, because a quadratic through the
  same points overshoots by 0.02 at the default, which is 5 levels over white.

Two candidates were tried and rejected on the numbers: mixing in linear light (no better than
sRGB), and blurring the wide kernel in linear light (a lower pixel RMS in the fit, but 7-13
levels too bright over dark content once implemented, for almost nothing over light content).

## 7. Where the library stands against the phone

Same test image, same reference boxes, same tools, the shipped shader rendered through Skia:

| Tint | appearance | phone black → / white → | library black → / white → |
| ---: | :--- | :--- | :--- |
| 0 | light | 105 / 249 | 105 / 248 |
| 50 | light | 136 / 247 | 141 / 241 |
| 100 | light | 179 / 247 | 182 / 240 |
| 0 | dark | 31 / 180 | 36 / 183 |
| 50 | dark | 39 / 179 | 42 / 168 |
| 100 | dark | 38 / 105 | 40 / 97 |

Edge profiles match within 5 levels at every depth. On a Pixel 7 the Android path reads within
2 levels of the desktop path at every one of the six states above (the table with both is in the
measured model), so the two platforms draw the same material; getting there exposed two
host-side bugs the desktop harness could not see, a strip drawn under the ground fill and a
contact shadow the phone does not cast. Six measured panels re-rendering every frame cost 21 ms
median on the Pixel 7, with or without the wide strip (19 ms after the five-tap scatter). The
white-side deficit of 6-11 levels above Tint 50 is the wide kernel pulling in the darker content
around the pill's white half, which the phone does not do to the same degree; the phone's tone
responds more to a brightening neighbourhood than to a darkening one, and a symmetric blur in
either colour space does not reproduce that. Two things were tried on it and rejected on the
numbers: clamping the wide kernel to the panel (the ramp is inside the footprint, nothing
changes) and a luminance-adaptive lift, which is the two-kernel form with free gains and, fitted
per still, wants a 6 px fine kernel for the light pill that the toolbar's stripes rule out.

**The refraction was wrong, and the dataset already held the answer.** Side by side with the
phone, the library's rounded ends barely bent the stripes where the phone folds them into
concentric arcs. The "no fold at rest" finding had been read off the pill's straight bottom
edge over a horizontal ramp, which is blind to a vertical displacement, and the pill's ends sit
over flat black and white, where there is nothing to displace. Measured properly
(`rest_lens.py`: one Δ per ring of depth inside an end, by correlating the displayed luma
around the ring with the target sampled Δ inward — the shift grows with cos θ around the end,
so a wrong Δ modulo the stripe period cannot match), the Photos toolbar's ends show the ring
0.33 R inside across their whole outer third, easing to identity by 0.46 R, identically at
every tint and appearance; the Lock Screen's round buttons show the same plateau; and the
dock's straight top edge shows nothing — the black block's boundary 30 px inside it sits
exactly in place. So the rest lens belongs to the curved parts of an outline only, and it is
the same family as the tracked fold with the plateau nearer the edge and the mirror almost
gone. The library now applies it at corner arcs (with the true radial normal, so the rim maps
onto the ring) and nothing along straight runs, and measures back within 2 px at every depth.
Not measured: how the plateau scales past R = 92 (the dock's 126 px ends over the Home Screen
wallpaper did not correlate through the warped reference), and the width of the blend where an
arc meets a run, which vertical stripes cannot show; the library blends over 0.2 R.

**The toolbar is its own role.** It reads up to 20 levels from the pill at Tint 0, brighter in
light and darker in dark. Its capsule sits over stripes only, so its two medians per slider
position cannot separate opacity, tint colour and fine share; holding the kernels at the pill's,
`fit_toolbar.py` solves the other two per position, with the library's own renders of the box
as the instrument that reads the blurred backdrop under it. Light is white at 0.55-0.67
opacity; dark is black at 0.55 (Clear), 0.41 (default) and 0.63 (Tinted) over the pill's 35
lift. Rendered and measured back:

| Tint | light: phone / library, black stripes -> | white stripes -> | dark: phone / library, black -> | white -> |
| ---: | :--- | :--- | :--- | :--- |
| 0 | 142 / 144 | 253 / 252 | 32 / 38 | 152 / 147 |
| 29-31 | 165 / 167 | 230 / 228 | 62 / 61 | 137 / 138 |
| 35 | - | - | 67 / 66 | 135 / 137 |
| 50 | 177 / 180 | 218 / 216 | 80 / 80 | 130 / 131 |
| 62 | 184 / 188 | 214 / 210 | 81 / 84 | 117 / 114 |
| 86 | - | - | 79 / 82 | 90 / 88 |
| 100 | 208 / 208 | 208 / 208 | 76 / 77 | 79 / 78 |

Within 4 levels everywhere but the read-back dark Tint 0 point. A Galaxy S24+ draws the same
screen within 2 levels of the desktop render at every position for every box (one read-back
outlier, the dark Tint 86 toolbar, 6-8 low), so the role ships on the device path as it was
fitted. The opacity-versus-fine-share
split is a reading: white at 0.58 with the pill's share fits the default position as well as a
230 grey at 0.70 with a share of 0.52, and the two differ by up to 15 levels over a flat
backdrop, so the next target revision puts a flat region under the toolbar.

## 8. Open

- The sheet material's own dependence on Tint Amount (confounded by the notification dim).
- The wide term's asymmetry (above).
- Elements with corner radii under 15 dp, widget glass, the light-appearance sheet, and
  whether the glass highlights respond to device tilt — none captured.
- The toolbar role's opacity against its fine share: the stripes fix only their product.
- The rest lens above R = 92, and the width of the blend between an arc and a straight run;
  a still with an element whose corners sit over horizontal and vertical stripes settles both.
- Which axis the fine term is sharp along. It is one-dimensional (FINDINGS 22), but every
  element measured is a horizontal bar, so a vertical glass element over a striped backdrop is
  needed to tell the screen's x from the element's long axis.
- The pill's one-pixel dark contour, and the light appearance of the tab bar's indicator and
  lens (only the dark one was recorded).
- The tab-bar lens is measured from a 60 fps recording of two bars; a still of a held lens over
  the target would fix its band mapping and colour split to the pixel.
- The next target revision: break the digit run, add a large mid-grey patch, and put a flat
  region under the toolbar capsule.

## 9. Reproducing any number

Every figure in the model names the frames it came from (`FINDINGS.md` in the capture
repository, sections 0-16) and the tool that produced it; `CHECKPOINTS.md` says what is on
screen in every segment of every recording; `model.json` holds the fitted numbers;
`library_vs_ios.md` the library comparison. The capture rig itself is documented in the Mac's
hand-over notes (`MAC-SETUP.md`) with its scripts, so it can be rebuilt on another machine.

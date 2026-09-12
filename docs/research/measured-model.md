# The measured model

> The Liquid Glass material as fitted from calibration captures of iOS 27.0 on an iPhone 13,
> 2026-09-11/12. This is the document the 0.2 optics implement; the raw record of the capture
> run is [ios27-measurements-raw.md](ios27-measurements-raw.md), and the 0.1 model it replaces
> is [optical-model.md](optical-model.md). Where the two disagree, this one was measured.
>
> Additions made while implementing: the wide kernel is 10 dp, not 25 (a pixel-level fit of the
> pill over the test image; the 25 was an edge-spread estimate limited by its window), the fine
> kernel is 0.5-1.6 dp, the fine share is `0.95 (1 - t/100)^1.5`, the dark opacity is read from
> the measured table rather than a quadratic, and chrome at rest shows no edge ring along a
> straight run but a plateau lens at its corner arcs (the ring 0.33 R inside mapped onto the
> outer third, section 2b) — the full fold profile of section 5 belongs to a tracked cover sheet
> and static chrome sits at `lensFormation = 0`. The lens a finger raises on a tab bar is a third
> family (section 2c), and the rim numbers below were re-read with the outlines matched.


Everything below is fitted from the captures catalogued in CHECKPOINTS.md and the numbers in
FINDINGS.md; the fits are produced by `tools/fit_model.py` (tables in `model_tables.md`,
numbers in `model.json`) and `tools/esf2.py`. Units: the phone is 3x, so px / 3 = pt. Luma is
sRGB 0-255. "t" is the Tint Amount slider, 0-100, default 50. "R" is an element's corner
radius, "W" its bevel width, "d" the display depth inside an edge along the inward normal,
"s" the backdrop depth actually shown at d. Confidence notes are at the end.

## 1. Two families of glass, and what Light/Dark does

- **In-app glass** (navigation pill, round buttons, toolbar of an app): follows the app's colour
  scheme strongly. Light appearance lifts black to ~105-179; dark appearance keeps black at ~35
  and pulls white down. Section 2.
- **Shell glass** (Home Screen icons, dock, folders, App Library, Control Center tiles and
  backdrop, notification cards): takes its tone from the wallpaper behind it and ignores the
  Light/Dark setting. Between appearance twins of the same Home Screen, only glyphs, labels and
  clock hands change (0.1-1.3% of pixels); the glass numbers are identical. Filled shell panels
  (Spotlight) shift their base colour by ~17 levels with appearance but keep their blur.
  Section 3.
- Rule for the library: `appearance` is an input to in-app material tone and to filled panels,
  never to shell glass. Shell glass is driven by the backdrop and by Tint Amount.

## 2. In-app element glass (Photos pill 520x170 px r85, round buttons r70, toolbar 480x140 r70)

### 2a. Material equation

    out = (1 - a(t)) * [ w(t) * B_fine + (1 - w(t)) * B_wide ] + a(t) * C + L

**The lens's colour fringe (FINDINGS 24).** Read at the rim over black, the phone's channels
separate by at most 16 levels on a line peaking at 65, about 0.45 px, blue just outside the line
and red just inside. The 0.07 fitted on the band draws a saturated blue stroke instead; the
measured value is 0.006.

**The bar and its lens are one surface (FINDINGS 23).** The selection lens is proud of the bar
and the two outlines are fused, not overlapped: where they cross, the bar's edge bows out to meet
the lens rather than making a crease. At the settled lens the silhouette stands 12 px above both
outlines at the crossing and rejoins the flat run about 40 px away, a polynomial smooth minimum
at 48 px (16 pt). Implemented as `GlassFuse`, but off by default on the tab bar: at the measured
width the bar reads as swelling to make room for the lens rather than the lens rising out of the
bar, which is the wrong impression even though the silhouette is right. The lens is also gel: 13 px proud at rest, 7 to 8 px above 300 pt a second,
about 5% of its height, gained back along the travel. `GlassFuse` and `GlassTabBarStyle.lensFuse`
carry the first; `gel` and `gelReference` the second.

Corrected 2026-09-12. The fine term is **one-dimensional, along the element's long axis**. The
backdrop term is a wide kernel for tone plus a high-pass along the bar for detail:

    B = B_wide(26 px, both axes) + w(t) * [ B_fine(1.2 px) - B_line(along the bar) ]

Three readings of the Photos toolbar pill, which lies across the target's stripes so that depth
below its straight edge is a spatial axis. An 8 px stripe running along the edge keeps 0.49
(dark appearance) or 0.64 (light) of the material's transfer, flat from 9 px below the edge to
33 px, so nothing fades toward a straight edge. The target's 20-row black band running across
the pill fits a single 26 px Gaussian to 0.4 levels rms with a fine share of 0.02, so nothing
crosses the bar. And where the stripe band itself ends, the detail stops within two rows, so
nothing is averaged across either. Content that does not vary along the bar cancels in the
high-pass and is left to the wide kernel alone, which is why a boundary crossing a bar leaves no
step on the phone; content that does not vary across it gets the old two-kernel mix unchanged.

In the shader the along-bar average is five taps at 0.7 of the wide kernel's sigma, jittered per
pixel; the 0.7 calibrates them against the strip's own Gaussian, which at 1.0 leaves an 11-level
overshoot on a step. The calibration toolbar's step over the target's black band went from 51.8
levels to 10.5 against the phone's 9.0, the tone table and the 16 px stripe reading unchanged,
and the edge spread at the split improved at tint 50 (5.7 against 32.8, the phone 5.5). Costs
about 0.8 ms of record time on an S24+.

- `B_fine` = backdrop blurred with Gaussian sigma **1-2.5 px (0.4-0.8 pt)**; `B_wide` = backdrop
  blurred with sigma **>= 70 px (>= 25 pt)** (window-limited; treat as "local mean").
- `w(t)` = share of fine detail, from the toolbar's 16 px stripes: **w = 0.78 * (1 - t/100)**
  (fit exponent 1.06). Measured: 0.81/0.77 at t=0, 0.52 at 30, 0.36 at 50, 0.29 at 62,
  0.13 at 86, 0-0.05 at 100. Same curve in both appearances.
- `a(t)` = tint opacity, from the pill's flat-black and flat-white levels, a = 1 - (white-black)/255:

  | t | a light | a dark |
  | ---: | ---: | ---: |
  | 0 | 0.435 | 0.416 |
  | 29-31 | 0.514 | 0.439 |
  | 50 | 0.565 | 0.451 |
  | 62 | 0.600 | 0.510 |
  | 86 | - | 0.655 |
  | 100 | 0.733 | 0.737 |

  Light rises early (concave), dark rises late (convex); both end at ~0.73. Quadratics in
  u = t/100: light a = 0.436 + 0.219u + 0.078u^2; dark a = 0.421 - 0.122u + 0.442u^2.
- `C`, `L` (tint colour and fixed lift): **light: C = 241 (constant, 241-244 at every t), L = 0**;
  **dark: C = 0, L = 35 (31-39 at every t)**. Check: dark t=0 over white = 0.584*255+35 = 184
  (measured 180); light t=100 over black = 0.733*241 = 177 (measured 179).
- Consequence: at t=100 the material over white still reads 247 (light) / 105 (dark); at t=0 it
  is a nearly clear, sharp glass. The single-Gaussian "blur" one would fit across a hard edge
  (1.2 -> 40 px, `sigma = 1.2 + 39.4*(t/100)^3.3`) is the two kernels mixing, not a real blur:
  16 px stripes keep 36% contrast at t=50 where a 5.5 px blur would leave them at 3%.
- Buttons: same numbers as the pill within 3 levels at every t (black 102-178 light,
  30-33 dark; white 250, 182->106 dark). Toolbar: the same kernels with its own tint and
  opacity; see "The toolbar role" below.

### The toolbar role (solved 2026-09-12, `tools/fit_toolbar.py`)

The toolbar capsule reads up to 20 levels from the pill at Tint 0, in opposite directions per
appearance (light brighter, dark darker on its white side), so it is not the pill's material.
It sits over the target's 16 px stripes only, which is the difficulty: its two medians per
slider position (over the black stripes, over the white stripes) fix two numbers, and the
material has three — opacity, tint colour or lift, and fine share. With the kernels held at the
pill's (same blur pipeline, verified on the pill over flat black and white), the two remaining
unknowns are solved per position. The blurred backdrop under the box is not modelled from the
target; it is read back from the library's own renders of the box at known parameters, so the
numbers are what the shipped shader needs.

- **Light: white** (255, where the pill's tint is 241) at opacity 0.552 (t 0), 0.570 (31),
  0.579 (50), 0.595 (62), 0.667 (100). More opaque than the pill at Clear, less at Tinted;
  the tint colour is forced to white because with the pill's 241 the medians at 31-62 cannot
  be reached at any opacity.
- **Dark: black** over the pill's 35/255 lift at opacity 0.550 (0), 0.469 (29), 0.452 (35),
  0.408 (50), 0.452 (62), 0.567 (86), 0.626 (100). The dip toward the default is what the
  medians say — the toolbar brightens from Tint 0 to 50 while the pill does not — and a fixed
  lift of 35 (the pill's) reproduces every verified position within 2 levels; 30 or 32 would
  fit the read-back Tint 0 point better and the verified ones worse.
- Both are more opaque than the pill at Clear and respond less to Tint Amount.

Library against the phone, same boxes, same tools (medians 24-60 px inside the capsule):

| Tint | light: phone / library, black stripes -> | white stripes -> | dark: phone / library, black -> | white -> |
| ---: | :--- | :--- | :--- | :--- |
| 0 | 142 / 144 | 253 / 252 | 32 / 38 | 152 / 147 |
| 29-31 | 165 / 167 | 230 / 228 | 62 / 61 | 137 / 138 |
| 35 | - | - | 67 / 66 | 135 / 137 |
| 50 | 177 / 180 | 218 / 216 | 80 / 80 | 130 / 131 |
| 62 | 184 / 188 | 214 / 210 | 81 / 84 | 117 / 114 |
| 86 | - | - | 79 / 82 | 90 / 88 |
| 100 | 208 / 208 | 208 / 208 | 76 / 77 | 79 / 78 |

Within 4 levels everywhere except the read-back dark Tint 0 point (6/5). The split is a
reading, not a measurement: at the default, white at 0.58 with the pill's fine share fits the
two medians exactly as well as a 230 grey at 0.70 with a fine share of 0.52 (the earlier pixel
fit's answer), and only a toolbar over a flat region of the target can tell them apart. Over a
flat backdrop the two readings differ by up to 15 levels, so the next target revision carries
that region. `GlassStyle.toolbar(dark, tintAmount)` is the role; `GlassTabBarStyle.Measured`
puts a tab bar on it.

### 2b. Edge treatment (in-app, at t=50 unless stated)

**The lens of chrome at rest** (2026-09-12, `tools/rest_lens.py`; supersedes "chrome at rest
shows no edge ring" below and in section 11). It lives at the curved parts of the outline
only. Along a ring at depth d inside a rounded end, the displayed pixel shows the backdrop from
Δ further inside along the normal; over the target's vertical stripes that shift grows with
cos θ around the end, which makes one Δ per depth unambiguous by correlation. On the Photos
toolbar's 70 px ends, identical at Tint 0, 50 and 62, in both appearances and over both
targets (12 measurements, spread under 2 px):

| display depth d | 3 | 4 | 5 | 6-16 | 18-20 | 22 | 24-26 | 28 | 30 | 32+ |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| source depth s (px, R = 70) | 27 | 26 | 24 | 23 | 24 | 25.5 | 27 | 29 | 31 | = d |
| s / R | 0.39 | 0.37 | 0.34 | 0.33 | 0.34 | 0.36 | 0.39 | 0.41 | 0.44 | |

Every ring in the outer third shows the same source ring 0.33 R inside, which is why the
stripes turn into concentric arcs at an end; the mapping eases to identity by 0.46 R; the
outermost 6 px show slightly deeper content again, a vestige of the fold's mirror. As a
fraction of the band W = 0.6 R: a plateau at 0.55 W over 0.14-0.38 W, identity from 0.76 W. The
Lock Screen's round buttons (R 82-92) put their plateau at 0.24-0.36 R with a noisier fit. A
straight run shows none of it: the dock's top edge leaves the black block's boundary 30 px
inside exactly where it is and folds no stripes over the block, and the toolbar's straight
edge keeps the stripes' contrast flat from the rim inward. Tracked surfaces are different: a
finger-held sheet shows the full fold of section 5 along its straight edge, and folders
mid-swipe show the deeper sources of section 4d. So the lens has three states — a straight run
at rest (identity plus a few px), an arc at rest (the plateau above), and a tracked surface
(the fold) — and the same family joins them: as formation grows the plateau moves from 0.55 W
to 0.70 W, the mirrored zone widens from 0.14 W to 0.28 W and the stretch lengthens to 0.9 W.

The library reproduces the table within 2 px at every depth (`analysis/rest_lens/lib_r0_light.json`):
24, 20, 17, 15, 13, 11, 9, 7, 6, 4, 3, 2, 1, 0 against the phone's 25, 22, 17, 15, 13, 11, 9,
7, 6, 4, 3, 2, 1, 1, 1, 0 at depths 2-32 step 2, correlation 0.86-1.0.

- Refraction ring: inward sampling of ~10-20 px within 16 px of the edge (toolbar over 16 px
  stripes; a pixel 6-16 px inside shows the neighbouring stripe). Full bevel profile: section 6
  with R = 70-85 px, W ~ 0.6R = 42-51 px.
- Bright edge line, re-read 2026-09-12 with the outlines matched (`tools/tabbar_lens/rimfit.py`;
  the earlier boxes sat 19 px below the phone's pill, so the earlier reading started a pixel
  in): a bead peaking one pixel inside the rim at +60 over whatever is beneath (light t50 195
  over 136, dark t50 96 over 36, light t0 164 over 103), then +40, +22, +10 over the next three
  pixels, +6 at 6, +3 at 8; the bottom the same, one pixel further in. A tight lobe around the
  ends: +41 and +10 fifteen degrees either side of the top, +16 at 30, +8 at 45, nothing from 60
  to the tip. The outermost pixel is a dark contour (30 against 136 at the top). Over white in
  the light appearance the line clips away. The library: bevel 1.33 dp as a bead peaking at a
  quarter of it, specular 0.25 at power 6, no separate edge line, and a glow tail
  0.05 exp(-(d - 0.67 bevel)/bevel); it reads 194, 192, 169, 149, 144, 142 against the phone's
  195, 176, 158, 146, 144, 142 (light t50, top, depths 1-6) and 100, 90, 68, 48, 43, 41 against
  96, 76, 56, 45, 43, 41 (dark). The dark contour is not drawn.
- At the rim the material reads 3-11 levels brighter than the interior at every t
  (pill edge black/white vs interior, tint_sweep.md).

### 2c. The lens under a finger (tab bar; FINDINGS 19, `tools/tabbar_lens/`)

Measured from LOLL8185, the Phone app's tab bar over black and the App Store's over busy
content, the selection pill dragged slowly; the resting frame is the reference, the still
frames (1700-1780) the calibration. It is not the fold and not the rest lens: a third family.

- **The bar while any touch is down**: grown 1.05 about its centre (its ends move 21-24 px,
  its top edge 3-5 px, both bars) and lifted +16/255 over black, +18 over content, with a
  one-frame flash as the lens forms. The tab under the lens grows 1.18 more about its own
  centre; the others only the 1.05.
- **Geometry** (px at 3x): bar 186 tall; the resting indicator 4 pt inside the bar top and
  bottom and the label plus about 57 pt wide (284 px for Keypad, 252 for Today: wider than a
  slot on a five-tab bar); the lens a capsule 5 pt proud of the bar (218 tall still, 228
  while moving) and 21 pt wider than the indicator (346-354 on the Phone bar, 313 on the App
  Store's).
- **Resting indicator**: the bar's output x 0.857 - 17/255 (10 over black where the bar reads
  32; 40 over content where it reads 67). No optics, no rim, a 4 px soft edge.
- **Interior**: the bar's output where it is (the magnifications the first fits found were the
  1.05 x 1.18 growth), plus 0.11 of the raw content behind the bar and a 1% white: +2 over the
  held bar over black, +12 over the App Store's content. A 0.74 blend of the bar over the
  content reads 35 over black and was rejected.
- **Band**: the outer 0.5 W (0.30 R). A pixel at depth u shows the exterior from
  s = 1.3 u - 0.29 W: the bar's own edge line lands 0.1 R inside the lens's rim (display depth
  28 at R 114, 23-24.5 at R 109), exterior text becomes a 1.6x-compressed stripe along it; from
  0.5 W inward the content is shown where it is, and the source between 0.36 W and 0.5 W is
  never displayed — the Keypad lens hangs 41 px past the bar's end and shows neither the end
  nor the 25 px gap beside it. Between tabs a glyph in the band smears along the arc with a
  dark seam where the hidden zone begins.
- **Rim**: a 3-4 px line of +68 over black at the top and bottom runs alike, trailing a glow
  16 -> 1 over the next 15 px; nothing at the sides, where a 2 px dark step of -30 marks the
  outline; colour fringes along the band, blue's copy outermost.
- **Motion**: the lens's content lags a frame while it moves (12-20 px toward the lens centre at
  a slow drag); still frames show no offset.
- **Library** (`GlassProfile.Held`, `heldLens`, `rawShare`, `GlassTabBarStyle.Measured`): the
  JVM harness over the resting bar grown and lifted reads, along the top run, 5, 64, 60, 46,
  32, 19, 11, 9, 8, 7, 6 against the phone's 20, 68, 57, 37, 16, 11, 9, 8, 7, 6, 5; the pulled-in
  bar edge peaks one row deeper than the phone's; the seam at the tip lands on the same pixel;
  interior 50 against 50; the side step 20 against 17. On the S24+ the held lens shows the same
  band, seam and grown tab; held still between two tabs the two copies of the row coincide;
  mid-drag the copy lags a frame, as the phone's does.

## 3. Shell glass at Tint 50 (over the wallpaper; the wallpaper itself is dimmed x0.773 in Clear-icon mode)

| role | R px | blur sigma px (pt) | tone over black / white of the dimmed wallpaper | edge |
| :--- | ---: | :--- | :--- | :--- |
| Clear icon | 45-83 | 6-7 (2.2) at rest; stripe MTF 1.5-4 | 14->67, 197->165 (gain ~0.5, lift ~60) | bright rim on the top edge and upper corners, darker contour below |
| folder (Clear) | ~95 | (heavy, unmeasured) | | same rim |
| dock | 122-136 | 2.5-3.8 (~1) | 4->17, 254->230 (gain 0.85, lift 13) | bends stripes over ~100 px at the ends only |
| Control Center tile, inactive | 105 | | surround x2-3 (+50-60) | outermost 1-5 px NOT lifted: a dark line |
| Control Center tile, active | 105 | | opaque white 250 | |
| notification card | ~60 | text softened (no number) | +29 on a 168 backdrop, flat | none |
| Live Activity card | ~60 | | near-opaque dark (5 over black, 19 over white) | |
| glass clock digits | | near-opaque (5% of 18 px stripe contrast) | 0->210, 255->211 | +23 rim over dark only |
| context menu (system) | ~70 | 28-29 (9.5) | 0->48, 255->110 (gain 0.24) on a screen dimmed to 0.52 | |
| Customize sheet | ~70 | 30-40 (10-13) | 255->180 | |

Icon and folder bevels: section 6. Clear icons displace colour boundaries +5..+52 px inward at
48-56 px depth (C4b, both targets); folders +24..+36 px at 20-32 px depth, ~0 beyond 60 px.

## 4. System backdrops (full-screen layers)

| layer | blur sigma px (pt) | tone | notes |
| :--- | :--- | :--- | :--- |
| Control Center over app content | 23.2 (7.7) at t<=50; 24 at 62; 27.5 at 86; 32-33 (11) at 100 | gain 0.32; offset = 142 - 0.864 * mean luma behind (60 over the dark target, 10 over the light) | identical over both targets and both appearances |
| Control Center over the Home Screen | 16-21 (5.5-7) | | busier backdrop, same family |
| App Library | 15-26 (5-9) | | |
| Today view | 26-28 (9) | | |
| Customize sheet | 30-40 (10-13) | 255->180 | |
| context menu | 28-29 (9.5) | 0.24 gain, +48; whole screen behind dimmed 255->133 (x0.52) | tint does NOT adapt to content |
| Spotlight | 62-80 (21-27) | filled panel; +17 levels in light appearance | heaviest blur measured |

The backdrop tint is adaptive to the content (offset falls as the mean luma behind rises), not
to the appearance. Tint Amount moves the system blur only above t~60 (23 -> 33 px) and the
tone by ~10 levels at t=100.

## 5. Cover sheet (Notification Center pulled from the top)

What the sheet shows: the Lock Screen (its wallpaper in screen coordinates, 1:1, plus the
flashlight/camera buttons and any notifications riding with the edge). The app underneath is
visible only below the edge.

- **Sheet interior**: sharp (4 px stripes keep 246/254 contrast), x0.96-0.98 of the wallpaper,
  black stays black, when no notification is showing (Tint 50 take). With a notification or
  Live Activity present the wallpaper on the sheet is dimmed to x0.64 (the Tint 0 and 100 takes;
  this is the notification-list dim, not the tint). Within ~160 px of the edge at k < 0.8 the
  interior darkens to x0.75-0.88 (an edge-attached shade, absent at k > 0.84).
- **Dim below the edge** (the app content): multiplicative, `k = 1 - 0.311 * (1 - exp(-x / 450 px))`,
  x = edge row in px (150 pt), saturating at **0.706** from ~700 px (233 pt) of pull. Measured
  identically at Tint 0 and 50 and in both recorders (0.87 at 300 px, 0.81 at 400, 0.76 at 500,
  0.72 at 600, 0.706 from 700). No blur below the edge.
- **Edge lens** (fully formed): W = 90 px (30 pt); the sheet's bottom corner radius is
  133-151 px (44-50 pt, the display's own corner), so W/R = 0.63. Display depth d -> shown
  source depth s, G channel, both targets, all three tints, both recorders:

  | zone | d (px) | s (px) | in units of W |
  | :--- | ---: | ---: | :--- |
  | interior | >= 80 | d + 1 | identity from d = 0.9 W |
  | stretched | 78 / 72 / 66 / 59 / 54 / 47 | 80 / 76 / 72 / 68 / 66 / 64 | d 0.52-0.9 W shows s 0.71-0.9 W: 2x magnification |
  | fold | 25-47 | 62-64 | stationary seam at s = 0.70 W |
  | mirrored | 25 / 20 / 17 / 8 / 5 / 3 / 0 | 64 / 66 / 68 / 72 / 80 / 84 / 88-96 | d 0-0.28 W shows s 0.71-1.07 W upside down |

  Content 0-62 px (0.69 W) inside the edge is never displayed; content 62-96 px is shown twice.
  Nothing outside the edge is sampled. Dispersion only in the mirrored zone: R appears 5-7 px
  (0.07 W) deeper than B; none in the stretched zone.
- **When the lens exists**: not for the first ~350 px of pull (k > 0.84: identity only), fully
  formed at k 0.76-0.84 (edge 350-600 px) and slightly relaxed beyond (stretched zone starts
  nearer the edge, mirrored zone weaker) while the finger tracks the sheet. Synthetic drags that
  commit the sheet in 3 s show only a 2-4 px inward offset and no fold. Rule: lens strength 1
  while interactively tracked past ~350 px; treat a committed animation as lens-free.
- **Edge lines**: over bright backdrop a thin dark line (luma dips to ~100 within 8 px, recovers
  by 24 px) once k < 0.84; over black a faint bright rim (+10..40 within 30 px). Absent at
  k > 0.92. The screen's left and right edges show a 10 px rainbow fringe (the sheet's side
  edges lensing).
- Tint Amount changes none of the sheet geometry or the dim; its effect on the sheet material
  could not be separated from the notification dim (section 9).

## 6. Bevel geometry for any element

- **W = 0.6 R** (0.55 +/- 0.11 over icon R45 -> W30, folder R95 -> W55, dock R122 -> W50; sheet
  R142 -> W90 gives 0.63). No cap is needed in the measured range 15-50 pt.
- Displacement is along the local inward normal; straight edges bend nothing along themselves
  (dock's long edges pass vertical stripes undisturbed). Sampling is inward only.
- Profile: the sheet's table in section 5 scaled by W. For a folder (W~55) it predicts +38 px
  displacement at d = 0.36 W = 20 px; measured +24. For an icon (W~30) it predicts ~0 beyond
  d = 27 px; measured 0-7 at 48 px. Adequate for R >= 15 pt; below that the profile is
  unmeasured.
- Small-element fold: the icons at rest show the ring but the folder-sized elements are the
  smallest where a mirrored copy was seen; at icon size the band is 25-35 px and reads as a
  compressed rim, not a fold.

## 7. Timing

| event | measured | note |
| :--- | :--- | :--- |
| round button press | +39 luma, ~170 ms in, ~200 ms out, held while down | flashlight button |
| Photos chrome show/hide | 16-66 ms (1-4 frames), no fade | |
| Control Center formation | blur forms with pull progress, tiles appear at the end | durations gesture-bound |
| cover-sheet lens | forms past ~350 px of tracked pull | see section 5 |

## 8. Hosting facts

- Home Screen wallpaper: uniform scale 1.1375, target row 0 at screen row -290 (Perspective
  Zoom), dimmed x0.773 in Clear-icon mode; colour patches render desaturated (red 206,47,33;
  green 80,197,41). The Lock Screen / cover sheet shows the same wallpaper at 1:1.
- Device: iPhone 13, 1170x2532 @3x, 60 Hz. Settings at capture: Reduce Transparency off,
  Increase Contrast off, Reduce Motion off.

## 9. Confidence and gaps

- Strong (many frames, two recorders, both targets): sheet lens mapping, dim curve, W/R,
  system backdrop blur and adaptive tone, in-app tone vs t, the shell/in-app appearance rule.
- Medium: the two-kernel material (fits on one element pair; the wide kernel is
  window-limited, >= 70 px); small-element bevel profile (few depth bins).
- Not measured: sheet material vs t (confounded by the notification dim in the t=0/100 takes),
  light-appearance sheet, glass response to device tilt, elements with R < 15 pt, widget glass,
  in-app glass over a photographic backdrop.
- Target bug for the next revision: the "0123456789" specimen trips Live Text.

## 10. Mapping onto `GlassStyle` (what the library must change)

| parameter | current | measured | change |
| :--- | :--- | :--- | :--- |
| refractionBand | 26 dp fixed | W = 0.6 R | derive from the corner radius |
| refractionDepth | 14 dp | peak = W at the rim (the outer 0.28 W shows content 0.71-1.07 W inside) | derive from W; profile of section 5 |
| mirror | 0.18 amplitude | full-strength mirrored copy in the outer 0.28 W once formed; 0 before | amplitude 1 x formation |
| dispersion | 0.018 of displacement everywhere | 0.07 W, mirrored zone only | zone-gated |
| blurRadius / backdropBlur | one blur | fine 0.5 pt + wide >= 25 pt mixed by w(t) = 0.78(1 - t/100) | two-kernel mix |
| tint, adaptivity | white 10% + adaptivity 0.65 | light: C 241 at a(t) 0.44-0.73; dark: black at a(t) + lift 35; system backdrops: gain 0.32, offset 142 - 0.864 * mean luma | per-appearance constants; adaptive offset for full-screen layers |
| specular / edgeLight / bevel | tuned | pill +33 (2-4 px) over black in light, +32/+48 in dark; buttons +6 top; icons rim top + contour bottom; tiles dark 1-5 px line | per-role |
| edgeShadow | 0 | tiles 1-5 px unlifted ring; sheet dark line over bright at k < 0.84 | per-role |
| dimmingLayer | 0.35 for Clear | sheet: 1 - 0.311(1 - e^(-x/150pt)); menus: 0.52 behind | progress-driven for sheets |
| invertsWithBackdrop | true | shell glass never flips with appearance; in-app follows the app scheme | host decision by family |
| lens formation | static | 0 for the first ~350 px of tracked pull, 1 after, 0 on committed animations | new state input |


## Verifying the library against the phone

The comparison is number for number, with the same tools on both sides.

1. `./gradlew :liquidglass:jvmTest -Pliquidglass.calibration=<dir>` renders the shipped shader
   through Skia over `calibration-target-light.png` and `-dark.png` at the reference boxes, for
   both appearances at every slider position of the iOS sweep (light 0/31/50/62/100, dark
   0/29/35/50/62/86/100), into `<dir>/library/`.
2. `tools/lib_compare.py` in the capture repository measures those composites with
   `still_elements.py` exactly as it measured the phone's stills and writes
   `library_vs_ios.md`: pill and button levels over flat black and white, the edge-spread across
   the hard split, and the toolbar's stripe contrast.
3. On a device, the sample's Calibration screen draws the same scene scaled to the screen width;
   `adb shell am start -n com.wexpa.liquidglass.sample/.CalibrationActivity --ei tint 50 --ez dark
   false --es target light` then `screencap`, rescale the screenshot to 1170 px wide, and run the
   same measurement.

Where it stands (2026-09-12): light in-app glass within 6 levels of the phone at every tint, the
edge profile within 5 at every depth; dark in-app glass over white 6-11 low above Tint 50. On
a Pixel 7 (Android 15, AGSL, 1080x2400) the Calibration screen reads within 2 levels of the
JVM render at every tint and appearance, so the device path and the desktop path draw the same
material:

| Tint | appearance | phone black / white | JVM black / white | Pixel 7 black / white |
| ---: | :--- | :--- | :--- | :--- |
| 0 | light | 105 / 249 | 105 / 248 | 105 / 248 |
| 0 | dark | 31 / 180 | 36 / 183 | 35 / 183 |
| 50 | light | 136 / 247 | 141 / 241 | 141 / 242 |
| 50 | dark | 39 / 179 | 42 / 168 | 41 / 169 |
| 100 | light | 179 / 247 | 182 / 240 | 182 / 240 |
| 100 | dark | 38 / 105 | 40 / 97 | 40 / 98 |

At the intermediate slider positions the pill holds the same residuals: light +4/-5 at 31 and
+5/-6 at 62 (black/white), dark over white 6-9 low at 29-86. The toolbar role's table is in
section 2.

What was tried for the white side, and rejected (`tools/fit_material3.py`): clamping the wide
kernel to the panel's footprint changes nothing, because the ramp that pulls the white half
down lies inside the footprint; a luminance-adaptive lift (`out = (1 - a) G_fine + T0 + T1
G_wide / 255`) is algebraically the two-kernel form with free gains, and fitted per still it
prefers a 6 px fine kernel with a 60 px wide one for the light pill (RMS 2-4 against 5-5.6)
while the dark stills prefer 0.5 px with 20-32 px — and a 6 px fine kernel would flatten the
toolbar's 16 px stripes, which the phone keeps at a third of their contrast. The asymmetry is
real and still open.

Getting there took two host-side fixes the JVM harness could not have found: the wide strip has
to be drawn into the glass layer *after* the ground fill and the backdrop, or they cover it, and
the 0.1 contact shadow drawn into the backdrop under a panel darkened it by 20 levels at Clear,
which the phone does not do, so the measured factories set `contactShadow = 0`.

On a Galaxy S24+ (Android 16, 1440x3120, 560 dpi) the same screen at every slider position of
the sweep reads within 2 levels of the JVM render for the pill, the buttons and the toolbar
role, with one exception, the dark Tint 86 toolbar at 76 / 80 against the JVM's 82 / 88 (the
phone: 79 / 90) — a read-back position where the material is almost all wide kernel, and the
S24's kernel is 3.5 px per dp against the iPhone's 3, which the width rescale does not undo.
The table is `analysis/device_vs_ios.md` in the capture repository (`tools/device_compare.py`).

Two more things the matched outlines and the tab-bar frames settled (2026-09-12):

- The phone's toolbar shows no fine detail within about 25 px of a straight edge: a 122-level
  boundary 25 px inside its bottom rim (the target's black band under it) leaves no step, and
  the toolbar's tone drops 8 levels over the band where the library's drops 33. Clamping the
  wide kernel to the footprint changes nothing — the band lies inside the footprint — and the
  fine kernel at a third of the mix would show the boundary plainly. Whether the fine share
  falls toward the edge or the edge zone shows deeper content as the arcs do, the target cannot
  say; it has one such boundary. Unmodelled (FINDINGS 20).
- The calibration boxes sat 19 px below the phone's pill (its rim lines are at y 141 and 272,
  its tips at x 347 and 822; the buttons 128 px wide from x 50 and 992); the still-fitting tool
  found the top but not the bottom or the sides. The material tables were read inside each
  image's own outline and stand; the render test now places the pill at (346,141)-(822,273)
  r66 and the buttons at (50,141)-(178,273) and (992,141)-(1120,273) (FINDINGS 21).

Frame times on the Pixel 7 with six measured panels re-rendering every frame at full
resolution: 19 ms median after the five-tap scatter (21 before), 31 ms at the 90th percentile,
44 ms at the 99th, GPU 10 ms median, identical with the wide strip on and off, so the strip is
free at this scale and the cost is the per-panel layer work, as in 0.1. The S24+ at its full
1440x3120: 17 ms median, 28 ms at the 90th percentile, 38 ms at the 99th, GPU 7 ms median.
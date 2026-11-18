# The rim band of regular glass (#202)

`<appearance>-<page>-before-above-after.png`: the top-left 330 x 80 px (3x scene pixels, shown 2x) of the Safari lab pill
(632 x 143 px capsule, `GlassStyle.inApp`) in the lab scene (`GlassLabScene`: the production modifier and host in a
Compose `ImageComposeScene`, Skia raster, jvmTest). Top: before (source revision `0a1282fb`, the rest map constant below
6 px). Bottom: after (the measured rim band). Magenta separates the two. Pages: `stripes24` is 24 px horizontal
black/white stripes, `edge43` is white above and black from 43 px inside the top outline.

Reading the pill's centre column (x = 300 px from its left end), luma of rows 0 to 10 from the top outline:

| Case | before | after |
| :-- | :-- | :-- |
| dark, edge at 43 px | 211 225 206 184 179 176 174 173 171 170 169 | 211 171 152 131 179 176 174 173 171 170 169 |
| light, edge at 43 px | 255 255 255 255 255 253 251 250 248 247 246 | 255 255 255 243 255 253 251 250 248 247 246 |
| dark, stripes 24 px | 172 137 119 98 93 92 90 90 89 89 88 | 172 184 147 98 93 92 90 90 89 89 88 |
| light, stripes 24 px | 255 241 222 201 196 195 194 193 192 192 192 | 255 255 224 201 196 195 194 193 192 192 192 |

Before, the outer 3 px showed content from 38 px inside (the white above a 43 px edge); after, they show the content 48 to
61 px inside, so the step at 43 px shows at row 4 and not at its true row there, as the lab's moving-edge sweeps measured.

Provenance: the six rim knots are **measured** on the Safari lab pill (iPhone 13, iOS 27; 890 display-to-source
correspondences from moving edge, line and vertical-edge sweeps, both appearances, top and bottom, caps and round buttons;
run means 61.0, 53.1, 48.2, 44.3, 41.8, 39.4 px of source depth at d = 0.5 ... 5.5 px, i.e. 1.41 ... 0.91 W with W = 43.2);
the 12 Photos knots from 6 px inward are unchanged. The values below 0.5 px (constant) are **authored**. That the rim is
a fraction of the radius (a scale extrapolation, as for the Photos table) rather than a fixed width is **authored** and
unverified at other radii.

Gate: `GlassRimBandTest` (plan gate M, as a public render test): synthetic horizontal edges at source depths 13, 24, 33
and 43 px on the lab capsule, top and bottom, both appearances, with the outline pixel row left out; the steps at 43 and
33 px appear where the lab measured them (within 2 px) and those at 13 and 24 px, which iOS never displays, do not.

Limits and not done: this is the map gate (M) only. The issue's 6 level rms gate against the lab stills (edge, dot and chart
stills, the outline row excluded) needs the private lab scorer and its HDR-decoded stills and was NOT run; the rim light
(plan step 3), caps and junction scores were not fitted. The render is jvmTest, not a device capture, and not owner
acceptance.

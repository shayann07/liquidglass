# Lines no longer hook where a capsule's round end begins — 17 November 2026

Source revision: branch `fix/cap-cut` on main `d7733bd2`. Backend: desktop JVM, Skia. The before/after images are a Compose
render of `Glass.Regular` (`glassEffect`, `LiquidGlassTheme`) on a 220 x 62 dp capsule at 3x over black 3 dp lines on
white. "Before" is clean `origin/main` (`d7733bd2`); "after" is the branch. The same
render on main before #210 (`0e2f7a63`) shows the same hook, finer, so it is older than the measured material. The scores
below are against the 16 November Safari lab stills, which are private: only numbers appear here. The device check
below was made on a Pixel 7; its frames are not published, numbers only.

## The cause

At rest the measured profile lensed only the corner arcs: a straight run showed nearly what is under it
(`u + 0.06 (1-u)^2`), an arc showed the measured resting map (`GlassRestMap.Measured`), and `cornerWeight` blended the two
over 0.2 of the corner radius along the run. Near the rim the two maps show rows about 0.7 of the band apart, so a line
under the junction was dragged through those rows within a few pixels: a short vertical tick at each cap start, top and
bottom. Over flat grey the junction is clean (a source remap cannot draw on a flat backdrop), so nothing else contributes.

## What iOS shows

The Safari lab pill (R = 71.5 px, iPhone 13) over 24 px horizontal stripes: along the straight run the outer band is
displaced the same way at every column up to the cap start, and nothing changes where the round end begins. Stripe edges
in the run's outer band (rows below the pill's top): iOS 2297.5 and 2314.5; main 2302.5 and 2315.5 (5 px off); the
measured map run along the straight edge 2299.5 and 2313.5 (within 2 px). In the round end all three agree.

## What changed

A capsule on the measured resting map (`Glass.Regular`, `GlassStyle.inApp`, `GlassStyle.toolbar`, container members,
custom styles with `restMap = Measured`) now shows that map along its straight runs too (`restCorner` in the shared
source map; the container carries the same rule). Measured on a capsule only, so a shape whose smallest corner is short
of its half-height keeps the corner-only lens: the dock's and a resting sheet's straight edges were measured in place. The
last tenth of the radius ramps between the two (authored), so no shape pops. The legacy map, which every tab bar uses,
keeps the corner-only lens and its 0.2 R blend; the docs/1 replay is unchanged frame for frame (0 of 1934 frames differ
from main).

Scored on the lab stills, rendered by the branch's own shaders through `GlassLabSceneRender` (12, 24 and 48 px horizontal stripes, dark and light), luma rms against iOS in the outer 3-45 px
of the pill: the straight run 4.5-12.0 levels (main 12.0-25.6), the junction 13.0-16.5 (main 16.9-23.8), the round end
unchanged (13.6-19.1). `GlassCapJunctionTest` renders the map alone over a ramp: near each cap start the shown row moved
3.46 rows per pixel on main and 0.00 now; mid-run it now shows what the cap's tip shows at the same depth (1.0 rows; main
41). `aCardKeepsItsStraightRunsInPlace`: a 30 px-corner card's run moves content at most 1.65 rows (the capsule rule
would move it about 0.7 of the band). `aContainerCapsuleFoldsAlongItsRunAsALonePieceDoes`: over 24 px stripes the run's
outer band of a container member matches the lone piece within 2.97 / 2.44 levels rms (dark / light; 37.4 / 27.0 without
the container's copy of the rule).

![Capsule, before and after, dark](before-after-dark.png)

![Capsule, before and after, light](before-after-light.png)

*Our renders only. Top: main. Bottom: this branch. The hook at each cap start goes; the cap's folds carry on along the
run, as iOS's do.*

## Device check (Pixel 7, 18 November)

Android 17, `CalibrationActivity --ez restMap true`, main `b980300f` against the PR's head `ee88228b`, all four combinations
of dark/light glass on the light/dark chart, portrait, screen captures. At the pill's left cap start the largest
horizontal step across a chart edge falls from 26 to 3 levels (dark glass) and 12 to 3 (light glass). The change sits only
in the outer band of the capsules' straight runs (max 44, mean 3.2 on the pill and 7.2 on the toolbar); the run core and
the round ends are unchanged (max 0-1), and nothing outside the capsules changes. A labelled glass `OutlinedButton`
("Continue") over lines, rendered on the JVM before and after, shows the same: the hook at each cap start goes and the
label, drawn above the glass, is untouched.

## Limits

- One iOS element at one radius: the Safari pill, a toolbar capsule at R = 71.5 px. No non-capsule panel has been
  measured at rest over lines, so cards keep main's corner-only lens (and its tick at each corner, smaller than a
  capsule's); #202 can revisit them.
- The images above are JVM Skia renders; the device check is numbers only. Still captures, no motion.

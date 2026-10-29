# iOS parity audit — 26 October 2026

Every visible or measured difference found between iOS (26/27 recordings) and liquidglass `main`
(`34127d8` plus the uncommitted lens band 1.1 under test), state by state. The owner asked for a full list
before any more fixing (26 October: "first compare and find issues properly … at least 10+ mismatches";
then "keep finding until u find 100+ good mismatches").

**How it was compared.** iOS: the private recordings (docs/1, docs/2, IMG_6756, JGEY2190, LOLL8185,
COSG3073, IMG_7048, LQCO6923), HDR10 decoded to display sRGB with the `.local/sxs/hdr.py` chain. Ours:
`:desktop:phoneReplay` renders of the same inferred input (desktop GPU, 60 fps; folders `p20`,
`r77-lband*`, `r73-f0`, short-script renders), and Pixel 7 screenshots (`PhoneActivity`, light and dark;
`CalibrationActivity` over the same calibration chart iOS was filmed through). Side-by-side sheets were
inspected by eye at 1x-2x; numbers come from the scorers named per item (`levels.py`, `tapprofile.py`,
`tools/reference-replay/refraction.py`, `tools/measure_rim_fringe.py`, the replay scoreboard). No private
frame is committed: the call-list pages show real names.

Each item: **what iOS does / what we do** — evidence. *S* = scored (a number exists), *E* = by eye.
Severity: **H** obvious at a glance, **M** visible side by side, **L** subtle or measured only. **V** marks an
item seen in one frame or sheet only: confirm it on a second recording before fixing.
GitHub issues group these by area (see the end).

## A. Held lens, still (optics)

1. **H, S** **[done 26 Oct, see fringe-refraction-2026-10-26]** Rim colour fringes: iOS shows saturated rainbow fringes (red/green/blue, 2-6 px) along the held lens's top and bottom rims at rest; ours shows none. Fringe scorer: iOS fringed 22-90 % of frames, ours 0-3 % (#73).
2. **H, S** **[done 26 Oct, see fringe-refraction-2026-10-26]** Rim refraction strength: iOS pulls the bar's straight edge ~+11 px toward the lens centre; ours -2.2 px at band 0.3 (#77). Band 1.1 reaches +9.8-+11 but see 3-5.
3. **H, E** **[done 26 Oct, see fringe-refraction-2026-10-26]** Rim refraction *shape*: iOS bends the bar's edge into a curve that meets the lens rim at both ends (a crescent following the capsule); ours keeps it a straight horizontal line across the whole lens, at every band tried (IMG_6756 35.5 s, 43.8 s; LOLL8185 24.3 s).
4. **H, E** **[done 26 Oct, see fringe-refraction-2026-10-26]** With the band at 1.0-1.1, content near the rim (labels, icons) breaks into displaced fragments over content pages ("Contac" + "ts", Pixel 7, 26 Oct); iOS compresses and curls it smoothly along the rim. The band alone cannot be the fix.
5. **M, E** **[done 26 Oct, see fringe-refraction-2026-10-26]** The dark band inside ours at band 1.1 reads as a flat slab at the bottom; iOS's is a thin curved crescent, thicker at the top than at the bottom (IMG_6756 35.5 s).
6. **H, E** **[done 26 Oct, see rim-outline-2026-10-26]** Rim outline: iOS draws a thin, crisp, dark outline with a brighter inner line; ours is a soft grey ring 2-3x wider and lower in contrast.
7. **M, E** Rim specular: iOS's lens catches a bright highlight along its upper-left and lower-right rim; ours has no directional highlight.
8. **M, S** **[measured 26 Oct: matches at holds; the IMG_6756 drag gap is the inferred input, see search-neck-2026-10-26]** Bar growth while held: iOS's bar grows 4-13 px taller when a lens is held, so the lens overhangs it less at the bottom; ours does not grow, the lens hangs 1-3 px deeper (refraction record h 13.7 vs 12.3 px).
9. **M, E** **[measured 26 Oct: within a few px]** Lens size: on Keypad iOS's held lens is ~350 px wide (IMG_6756 35.5 s), ours ~335 px and its centre sits ~10 px left.
10. **M, E** **[done 26 Oct: iOS does join, with a shallow notch; sample 24 dp]** Fusion with the search button: ours joins the button through a neck when held at the trailing end (sample `lensFusion` 12 dp, from the light IMG_7048); iOS 27 dark (IMG_6756 35.5 s, docs/2 30.0 s, LOLL8185 21.3 s) shows the lens overlapping the button with no neck.
11. **M, S** Held lens over page content reads 5 levels darker than iOS (docs/2 avatar 61.4 vs 65.3; #72); iOS's lens brightens what it shows.
12. **L, E** Lens interior on black pages: iOS's inside reads slightly clearer/brighter at the centre with the bar texture visible to the rim; ours is a uniform grey.
13. **L, E** Ink magnification under the lens: iOS 1.22-1.24, ours 1.20-1.22 (refraction record, icon fit).

## B. Held lens, moving (drag)

14. **H, S** **[done 26 Oct, see fringe-refraction-2026-10-26]** Fringes do not grow with speed: iOS's fringe energy rises 5-30x from still to fast; ours stays flat (fringe scorer, all five recordings).
15. **H, E** Mirror swirl inside a moving lens: iOS mirrors the neighbouring icon into the lens's leading cap as a smooth curl with colour (LOLL8185 26.5 s, docs/2 8.6 s); ours draws a hard-edged blue blob and cuts labels ("Corτacs", "C...s", LOLL8185 22.2 s; docs/2 8.6 s), worse at band 1.1.
16. **M, E** Labels inside a moving lens: iOS keeps the label legible and bends its ends along the bottom rim; ours clips or squashes it at the bottom rim.
17. **M, E** **[likely the same script mismatch as 33; re-check against the recording's own script]** Lens formation onset in a drag-from-rest: iOS's lens is fully formed and moving 0.2 s after touch-down (LOLL8185 21.3 s); ours is still a pill at the start position at the same moment.
18. **M, S** Drag tracking: selector path vs iOS's ink rms 9.2-31.6 px depending on recording (scoreboard), best time shift -8 ms.
19. **M, E** Moving lens shape: iOS's lens stretches into a slight oval (taller in the middle) at speed; ours stays a straight-sided capsule.
20. **L, E** At reversals iOS's lens flattens to about the bar's height; ours keeps more height (refraction record: bimodal readings at fast reversals).
21. **L, S** Neck timing to the search button: forms -50/0/-67 ms, parts 0/-33/+100 ms against iOS (docs/2 scoreboard); 34.8 s 100 ms late.

## C. Tap (pill in transit)

22. **H, S** **[done 26 Oct, see fringe-refraction-2026-10-26]** Transit fringes: iOS's travelling pill shows large rainbow streaks on its top and bottom rims and over the ink; ours peaks at 0.09 of iOS's fringe energy (docs/1 at band 1.1; 0.02 at 0.3).
23. **H, E** Ink smear in transit: iOS stretches the destination's ink upward into "flames" along the top rim and streaks the label at the bottom (docs/1 1.00-1.12 s); ours barely deforms it.
24. **H, E** **[done 26 Oct, see fringe-refraction-2026-10-26]** Label clipping in transit: ours cuts the label at the pill's bottom rim ("Keypad" shown half height, docs/1 0.95-1.05 s); iOS's label stays whole.
25. **M, S** Plateau ends early: iOS's pill holds level 53 to ~183 ms after the ink leaves, ours drops from ~120 ms (47 at 150 ms, 42 at 183 ms; #70).
26. **M, E** Transit shape: iOS's pill bulges into an oval mid-flight (docs/1 0.95 s); ours stays capsule-shaped.
27. **M, E** Source tab during transit: iOS's leaving ink keeps colour and is mirrored inside the pill's trailing cap; ours shows a partial cut glyph.
28. **M, S** Tap timing: iOS's bar lights 33 ms before the ink leaves; our inferred press 16 ms before (scoreboard).
29. **L, S** Tap pill level docs/1 +0.8, JGEY2190 -2.0 (scoreboard).
30. **L, S** Repeated long tap: pigment returns ~50 ms (≈7 levels) later than the measured patches (scoreboard).
31. **L, S** Early-transit inset read 0.38 of the bar at video1 frame 6 vs 0.57-0.69 at rest: unresolved calibration conflict (scoreboard).
32. **M, S** Fringe timing in a tap: iOS onset +33 ms, peak +116 ms after down, fade +173 ms after lift; ours onset +83, peak +100, fade +124 (docs/1, band 1.1).

## D. Release, throw, landing

33. **H, E** **[withdrawn 26 Oct: the comparison used a render from a different inferred script; with docs/2's own script our lens is at Contacts at 8.56 s as iOS's is]** Throw start: iOS's lens has formed and left the source tab by docs/2 8.56 s; ours is still the resting pill there (later by ~40-60 ms).
34. **M, S** Wall landings: rms 6.0 px right / 5.4 px left; the 9.9 s landing passes its rest by 36.5 px vs 41 (scoreboard).
35. **M, E** Landing overshoot outside the bar: iOS's landing lens protrudes past the bar's end with a dark soft smear beyond it (docs/2 8.68 s, LOLL8185 33.25 s); ours protrudes but with a sharper outline.
36. **M, E** Pigment return after a throw: iOS's pill is still partly clear at docs/2 8.76 s; ours is already the dark pill.
37. **L, S** Release width series: first frames narrow up to 11.2 px less than iOS (scoreboard).
38. **L, E** Release fringes: iOS's fringe peaks within a frame of the lift and fades 110-120 ms later; ours has none to time (fringe record).

## E. Resting bar and pill

39. **M, E** **[corrected and done 26 Oct: iOS lights top and bottom equally, 99/79/63/45; ours now 98/88/65/45 (specular 0.25), levels on all five unchanged]** Bar outline: iOS's resting bar has a thin bright hairline mostly on the top edge; ours draws an even lighter outline all round.
40. **M, S** Resting pill on docs/2 3 levels darker than iOS (scoreboard).
41. **M, E** Page text under the bar is slightly more smeared in ours than iOS (scoreboard note).
42. **M, E, V** Resting pill size: iOS's resting pill on Keypad spans more of the bar height (docs/1 1.25 s, LOLL8185 21.1 s) than ours.
43. **L, E** Bar cap lensing (`capFold`) judged by eye only, not scored.
44. **L, S** Light bar ends: outline 177-186 vs 178-182, 5-10 overshoot (scoreboard).
45. **L, E** iOS's bar edge shows a faint coloured fringe at the caps over bright content; ours none.

## F. Search button (trailing accessory)

46. **M, S** Release overshoot: iOS's button body overshoots its rest by 6 px after a sideways release, ours 2 px; narrowest after release 83 vs 78 px.
47. **M, S** Brightness 6.4 levels rms vs iOS during pulls (scoreboard).
48. **M, E** Press stretch: iOS stretches the button into a tall ellipse when pressed and pulled (IMG_7048 36.5 s); ours stretches less (pull gain 0.2, scored only in rise).
49. **L, E** Button rim: iOS's search button has the same crisp thin outline as its lens; ours the soft ring (see 6).

## G. Light appearance (IMG_7048 vs Pixel 7 light)

50. **M, E** Held lens shadow: ours casts a visible grey shadow under the lens; iOS's is much fainter on the bar.
51. **M, E** Held lens rim in light: iOS shows a thin rainbow at the lens's right rim; ours none.
52. **M, E** Light resting pill: iOS 245 on white; ours reads close but its outline is softer.
53. **L, E** Light bar outline thickness: iOS's grey outline is thinner than ours.
54. **L, E** Light held lens: iOS's bar grows taller under the lens (as 8).

## H. Glass buttons and toolbars over the calibration chart (COSG3073 Photos viewer vs `CalibrationActivity`, Pixel 7)

55. **H, E** Interior contrast: iOS's buttons show the chart's stripes at medium contrast (white→~200, black→~90); ours flattens them to a narrow grey range (~120-160): too much frost/too little transmission.
56. **H, E** Edge bending: iOS bends straight stripes into arcs that follow the button's outline near the rim (strong, ~25 px band on 66 pt buttons); ours compresses them into straight lines with little curvature.
57. **H, E** **[improved 26 Oct: measured rest map default for in-app glass, see inapp-rest-map-2026-10-26]** Circular buttons: iOS shows concentric arcs and a magnified centre stripe inside round buttons; ours shows a near-uniform grey disc with faint lines.
58. **M, E** **[improved 26 Oct, same]** Pill caps: iOS's toolbar pill bends the stripes into arcs at both caps; ours keeps them straight to the cap.
59. **M, E** Rim highlight: iOS has a thin bright highlight on the upper rim of each button; ours a soft lighter band.
60. **M, E** Interior lift: iOS's buttons read brighter overall over the same stripes (stronger lift); ours darker.
61. **M, E** Bottom band: ours draws a dark band along the bottom of the pill; iOS's bottom edge is a lighter, refracted strip of the content below.
62. **L, E** Top dark band in ours (along the top rim) is not in iOS.
63. **M, E** Nav-bar title pill ("Today 12:53 AM") and the round back / more buttons: iOS's read as clear glass with the content refracted; we have no matching preset for a title pill.
64. **L, E, V** Small floating pills (rating, badges) on iOS are a lighter, more transparent glass than our smallest preset.

## I. Lock screen clear lenses (COSG3073 30 s)

65. **H, E** **[withdrawn 26 Oct: at full resolution (COSG3073 28, 30, 33 s) the content is heavily blurred and upright, not inverted; a frosted button, not a ball lens]** Clear round buttons (flashlight, app shortcut) act as ball lenses: the content inside is magnified and *inverted*; we have no lens that inverts.
66. **M, E, V** Their rim is a thin dark ring with a white specular dot; ours has no specular point.
67. **M, E** Wallpaper warping with rainbow at the stripe bends behind them (lock-screen depth effect) — not modelled (likely out of scope; listed for completeness).

## J. Ink, labels and icons

68. **M, E** **[done 26 Oct: sample accent 0xFF0897FF, renders (8, 151, 255) against iOS (7-8, 151, 255)]** Selected ink colour: iOS's is a brighter, more cyan blue (~#1AA3FF); ours a deeper blue (~#3B8EEA).
69. **M, E** **[won't fix in the sample: SF Pro cannot be redistributed; apps set their own font]** Label font: iOS uses SF Pro semibold with tighter tracking; the sample uses the platform font with wider spacing.
70. **M, E** Icons: iOS's Contacts glyph is a filled person in a circle with a cut-out; ours is a different person glyph (sample assets).
71. **L, E, V** Unselected ink: iOS's white glyphs are slightly brighter.
72. **L, E** Badge: iOS shows a red count badge on Calls; the sample has none.
73. **M, E, V** Ink scale in a hold: iOS's selected item grows smoothly; ours grows with a visible step when formation passes 0.5 (by eye, LOLL8185 21.3 s).
74. **L, E, V** **[withdrawn 26 Oct: measured, no fade to white on either side; instead the destination's ink turns blue ~33 ms early (docs/1 first tap: ours fully blue at 0.933 s, iOS 0.967 s, with a partial frame at 0.90 s) and its grow-and-settle peaks ~70 ms early (0.97 s against 1.03-1.07 s)]** Ink during transit: iOS keeps the selected ink's colour inside the pill all the way; ours fades it partly to white mid-transit.

## K. Timing and dynamics (scored)

75. **M, S** Docs/2 held drag tracking rms 31.6 px (highest of the five).
76. **M, S** JGEY2190 tracking rms 26.8 px.
77. **L, S** Hold onset aligned by construction (pass 7): untested against iOS.
78. **L, S** Search button rise 3-6 px short at gain 0.6 (pass 17).
79. **L, S** Short-tap first frame: the 17 ms lead in 28 comes from the inferred finger timing, not the renderer; the press-to-light latency of our bar has never been measured on the Pixel 7.
80. **L, S** Wall-landing trailing damping candidate rejected; landings remain 4.3/5.8 mean error.

## L. Device and platform

81. **H, S** First tap after install on Pixel 7: 70-119 ms slowest frame (was 326-352); iOS shows no hitch. Still 6-10 frames over budget.
82. **M, S** Second tap: 23-47 ms frames vs 11.1 ms budget at 90 Hz; nearly every frame of a tap is over budget on the Pixel 7 (gfxinfo: 55-69 frames over 11.1 ms per 1.5 s capture).
83. **M, S** Web demo: 4.46 MB transfer, ~2 s per opening frame compiling shaders, ~7.5 s to usable unthrottled (#78).
84. **M, E** Web demo sometimes never renders (not reproduced; #78).
85. **L, S** Custom-path field first build synchronous at full resolution (#81).
86. **M, E, V** On the Pixel 7 the page content shows through the bar more sharply than iOS (text legible under the bar, "Westbrook"), dark appearance.

## M. Scenes and coverage gaps

87. **M, E** No light-mode replay score exists for holds and drags (only rest and the lens shadow were scored).
88. **M, E** No score for content-page fringes (the fringe scorer excludes page hues).
89. **M, E** No measured reference for the Material 3 components (slider, switch, sheet, menu, dialog): they are authored to look iOS-like, not compared to recordings.
90. **M, E** **[radius done 26 Oct: 34 dp; material open — COSG3073 279.5 s reads light and strongly saturated, unlike the measured dark `GlassStyle.menu()`; needs the appearance and tint of that capture before changing]** Context menu (COSG3073 ~276 s: Copy/Duplicate/Hide, Set Rating …): iOS's menu platter is a light frosted glass with rounded 34 pt corners; our `GlassDropdownMenu` was not compared to it.
91. **M, E** Toolbar grouping: iOS groups three toolbar actions into one pill with separate round buttons beside it; we have no toolbar component.
92. **L, E** Status bar time pill (red recording pill) — out of scope.

## N. Our own inconsistencies found while comparing

93. **H, E** **[done 26 Oct, see fringe-refraction-2026-10-26]** The lens band, the rim mirror and the dispersion are tuned per state (fold vs held vs transit) with separate constants; the transitions between them are visible as a jump in fringe and refraction when formation crosses 0.5.
94. **M, E** **[done 26 Oct, see fringe-refraction-2026-10-26]** At band 1.1 the transit pill inherits the wider band through `GlassMorph` and clips labels more (items 15, 24).
95. **M, E** **[done 26 Oct, see fringe-refraction-2026-10-26]** `GlassSelectorRimDispersion` is never applied to taps (tap material takes the lens's dispersion through the formation lerp), so the transit fringe cannot be tuned independently.
96. **M, E** **[done 26 Oct: trace.csv logs transit and heldTravel, 4714e22]** Diagnostics: the replay trace (`trace.csv`) does not log transit appearance or held travel, so tap and fringe timing cannot be read from our side.
97. **L, E** The light preset's lens band was changed together with dark (1.1) without a light score.
98. **L, E** Replays of IMG_6756/JGEY2190/LOLL8185 use `script3` and docs/2 two different scripts (`v2-script.txt`, `script3.txt`); baselines must be re-rendered per script.
99. **L, E** `CalibrationActivity` shows no icons on the toolbar panels, so glyph legibility over glass is not compared.
100. **L, E** Our resting selector on Calls at app start differs from the recordings' Keypad start (sample state), so first-frame comparisons need a matched start.
101. **M, E, V** Pixel 7 light: our held lens also draws a soft grey disc behind the search button area when fused; iOS has a clean gap.
102. **L, E, V** The sample's search button shows a faint blue glyph ring under the magnifier in dark (content bleed); iOS's is clean.

## O. Second pass: hidden differences (26 October, afternoon)

The owner asked for the hidden mismatches. Compared at 2-4x and measured: fresh renders of all five recordings at
`2a7c7b0` (set `n6`, the squircle lens included), the light hold (`calm-light`), and five recordings the first pass
never opened (IMG_7049 light, PUSM9766 Control Center, IMG_6667/MUQJ3680 lock screen, the iOS 27 sample
reference). Numbers are display-sRGB luma or RGB unless stated.

103. **M, S** Held-lens ink lift: iOS brightens the selected ink under a held lens, (7,151,255) at rest to (26,174,255)
     (IMG_6756 34.9-36.1 s), (28,176,255) LOLL8185 21.6 s, (38,185,255) JGEY2190 5.0 s, (41,186,255) docs/2; light
     (0,132,250) to (23,146,255) (IMG_7048 40 s). It decays over 0.3-0.5 s after release. Ours never changes.
104. **H, S** Still release keeps its glass: after a stationary hold iOS 27's lens is gone 33-65 ms after the lift
     (IMG_6756, three holds); ours keeps it ~300 ms (`releaseStillHoldSeconds` .18 was authored from docs/2, where
     iOS takes ~130 ms). The recordings disagree; IMG_6756 is iOS 27.
105. **M, S** After a still release our bar's top dips 6 px below rest (459 vs 453) and recovers over ~150 ms; iOS's
     settles 451 → 453 without a dip (IMG_6756, three holds).
106. **L, E, V** Release rebound: on docs/2's 5.87 s lift iOS's lens drops to the pill, swells once (6.07 s) and
     settles by 6.13 s; ours collapses once.
107. **H, S** Still-press formation: iOS's bar starts to swell 103-120 ms after the inferred touch-down, the lens
     appears 67 ms after that and is formed 117 ms after it. Ours swells at 37-53 ms, the lens appears 184-216 ms
     after the swell and forms 250-266 ms after it (IMG_6756, four presses). Independent of where the inferred
     touch-down sits. (Item 77: hold onset was untested.)
108. **H, E** The held lens overlaps the search button on iOS: its dark outline crosses the button's left side and
     the notch belongs to the bar's own end, seen magnified through the lens (IMG_6756 35.5 s dark, IMG_7048 38.5 s
     light). Ours necks the lens itself into the button and draws the button over the lens. Re-reads item 10.
109. **L, E, V** Where the lens meets the button our button's edge carries a red/orange fringe; iOS shows a faint blue
     line where the lens crosses it.
110. **M, S** Spectral band at the refracted bar edge inside a held lens: ours ~9 px with two or three navy rows
     ((2,3,29), (2,3,41)); iOS ~5 px with one ((2,10,41)) (IMG_6756 47.5 s).
111. **M, S** Channel crush at the lens rim: just inside our rim the blue channel drops to 0 for 2 px (olive (23,31,0)),
     at the bottom rim red drops to 0 (cyan (0,29,30)); iOS stays neutral ((27,22,20)). Our dispersion samples the
     black outside the bar for one channel.
112. **L, S, V** Outer rim line hue: iOS's is warm at the bottom (orange (90,56,33) then green) and green/violet at the
     top; ours yellow-green at the top and cyan at the bottom.
113. **L, S, V** The dark crescent inside iOS's rim grades from 9 at the lens rim to 1 at the bar edge; ours is flat 3-5.
114. **M, S** Resting bar rim at the caps: iOS's highlight dims round the caps (85-87 at the cap's top and bottom,
     against 99 on the straight runs); ours stays 97-98 (IMG_6756 34.2 s).
115. **M, S** Search button rim: iOS peaks at 97 at top and bottom, ours 105; iOS's line is broader in angle (44 vs 39
     at 30° and 150°) and skewed toward the left; ours is symmetric.
116. **L, S** A faint lift just inside iOS's rim follows the highlight (34-35 at top and bottom, 32 at the sides);
     ours is flat 33.
117. **L, S, V** iOS's Keypad dots vary 117-130 in luma; ours are flat 120.
118. **M, S** Item layout: our icons sit 3-4 px higher (centre 521 vs 524-525) and labels 2-3 px higher (IMG_6756
     34.2 s); sizes match within 1-2 px.
119. **M, S** Search glyph: ours 72 × 72 px with 1486 ink px against iOS's 68 × 68 with 1183 (6 % larger, 26 % heavier).
120. **L, S** Unselected glyphs in dark: iOS's average 247, ours 251-253 (ours brighter; corrects item 71).
121. **M, S** Unselected glyphs in light: iOS's are dark grey (icons 29, labels 32-33, search glyph 18); ours pure
     black (IMG_7048 38.5 s against `calm-light`).
122. **M, S** Light accent: iOS 27's selected ink at rest is (0,132,250); the light sample renders (0,122,255).
123. **M, S** Transit pill outline: iOS's travelling pill carries a bright outline (99.5th-percentile grey 109-121 to
     1.01 s, 105-110 to 1.10 s, docs/1 first tap); ours 104 then 90-91, and the region reads 3-7 levels darker. At
     rest afterwards ours is the brighter one (98 vs 85-90).
124. **M, S** Landing lens lift: iOS's thrown lens is milky as it lands (docs/2 8.68 s interior 90 against our 72;
     8.76 s 53 vs 44).
125. **M, S** Item 16 holds in throws and in held drags over content: iOS stretches the label down along the bottom
     rim (blue label pixels to row 622-627, docs/2 8.62-8.76 s); ours stops at 599, cut at the rim (also JGEY2190 16 s).
126. **H, E** Search mode: tapping iOS's search button grows it (to ~1.15x) and lights it (19 → 60 in 66 ms), the tab
     bar collapses to one small circle carrying the selected tab's icon, the button becomes a wide search field that
     rides up with the keyboard, and the close button reverses it (iOS 27 sample reference 38.8-39.5 s). Not modelled.
127. **M, S** Control Center modules (PUSM9766 39 s, dark, over the calibration chart): round toggles lift a dark
     blurred backdrop (58-74) to 140-165 and desaturate their centre (chroma 16-50 against 98-118 behind) while a ring
     inside the rim keeps the colour (chroma 77-90). No preset reproduces this platter. The recording also shows a
     vertical slider with a frosted white fill, the Focus pill and the media platter: references for item 89.
128. **L, E** IMG_6667 and MUQJ3680 (lock screen over the chart) show the flashlight button pressed: it grows and
     lightens. Our round glass buttons have not been compared with it.
129. **L, S** Measured and matching (no change): resting bar and button geometry (63-600, 921-1106, rows 453-638),
     resting pill box and darkness, held lens and bar levels on all five recordings (`levels.py`, n6), the resting
     bar over a black page row by row.

## P. Third pass: beyond the pixels (26 October, evening)

Behaviour iOS ships that a still frame does not show: press lighting, accessibility, haptics, input on desktop and
web, and load. Pixel numbers as in O; the search-button render is `s6` (IMG_6756 search script at `2138aea`).

130. **M, S** Press lighting is directional: iOS's pressed search button grades from 97 at the top left to 84 at the
     bottom right (IMG_6756 1.85-2.6 s) and dims 2-3 levels while held still; ours is a flat 96-98 and constant.
     The pressed size matches within 2 px.
131. **M, E** System accessibility settings are not read: `LiquidGlassTheme`'s `reduceTransparency` and
     `increaseContrast` are parameters that default to 0, and nothing reads the platform's settings. iOS's glass
     follows Reduce Transparency and Increase Contrast by itself.
132. **M, E** Reduce Motion: `GlassTabBar`'s `motionEnabled` defaults to true and is not tied to the system setting
     (Android's animator duration scale 0, the desktop's reduce-motion). iOS drops the lens's stretch and travel.
133. **L, E** No Large Content Viewer equivalent for the tab bar at accessibility text sizes.
134. **M, E** Haptics: there is no haptic anywhere in the library; iOS ticks a selection haptic as a held lens crosses
     a tab and on a tap.
135. **M, E** Pointer hover: no glass control reacts to a hovering pointer on desktop or web (no `hoverable`, in the
     library or in Material 3); iPadOS's glass lifts under the pointer.
136. **M, E** Keyboard focus: tabs are focusable through `selectable` but draw no focus indication (`indication = null`),
     so a keyboard user cannot see which tab has focus; only Material 3's `SearchBar` reacts to focus.
137. **M, E** No automatic quality fallback: `renderScale` is manual only. On the Pixel 7 a tap misses its 11.1 ms
     budget on most frames (#100), and nothing lowers the cost by itself; iOS holds its frame rate.
138. **L, S** Checked and not differences: the screen recordings carry no HDR headroom (peaks 121-188 nits, white
     glyphs 110-128), so EDR highlights cannot be compared from them; IMG_7049's list keeps full text contrast down
     to the bar (no bottom scroll-edge fade there); the bar's width stays constant while those lists scroll (no
     minimise on scroll); our frames are bit-identical while nothing moves; label edges are equally crisp (10-90 %
     in 1 px). Tilt-driven highlights could not be tested: no recording holds the bar untouched long enough.

## Q. Video-model pass, verified (26 October, night)

A video-capable model (Gemini 4 Argon) watched iOS-over-ours videos built for it (`.local/gemini/`, private): the five
recordings in real time and eleven 8x slow-motion gesture clips, with ours rendered by `:desktop:phoneReplay` set
`n6`. That set comes from `2a7c7b0`, and nothing under `liquidglass/`, `apps/` or `ports/` has changed since, so the
renders are current `main`. It also watched the raw recordings. Each of its 18 side-by-side findings was checked here
against decoded frames, the replay's `trace.csv` and the replay script. Verdicts: 3 new (139-140), 7 already tracked (some worse
than described), 0 already fixed, 4 replay or scene artefacts, 4 wrong.

139. **H, S** Fast scrub flattens the held lens: during docs/2's back-and-forth scrub (18.7-22.0 s) our lens becomes
     a flat ellipse, about 590 × 103 px at 20.0 s (trace body width 446-627 px from 19.0 to 21.0 s), and squashes
     the labels. iOS's stays about 370 × 186 px, the bar's full height. The held-travel strain (.6, authored on this
     scrub at 19.0 s, where iOS is 458 × 145) overshoots and stays on.
140. **H, S** Tap transit should form a full lens on iOS 27: on every two-slot tap in JGEY2190 (4.62, 28.05, 30.53,
     33.13 and 34.41 s) iOS's travelling pill rises into a full glass lens that protrudes past the bar, fringes and
     magnifies the ink. Ours stays a flat dark pill (formation peaks at 0.25-0.28). docs/1, where the tap optics were
     authored, inflates far less, so the recordings disagree; JGEY2190 is iOS 27. On the same taps our lens trails:
     its centre is 77 px behind iOS's at 4.70 s.
141. **M, E** The lens's side caps cut content instead of bending it: as a moving lens passes an icon (LOLL8185
     21.38-21.50 s), iOS sweeps the icon round the cap (the Keypad dots smear along the right cap, Contacts bends
     along the left), while ours keeps it unrefracted to a hard vertical ink-stencil line (the dots half blue, half
     white). This is items 15 and 16, which were grouped under #77; #77 is closed, so they are re-homed here.
142. **M, S** Bottom band of the bar: rows 620-632 (just below the labels) carry 5-10x iOS's fine page detail
     (mean |Laplacian| 4.9 vs 0.5-1.0 on docs/1 12 s and docs/2 0.5 and 6.5 s). Items 41 and 86 are worse than described.
143. **L, S** Held magnification: horizontally ours matches iOS (Keypad dots 70 → 88 px on both, IMG_6756 35-36 s);
     vertically ours reaches 78 px against 81. Refines item 13; Gemini's "no central magnification" was wrong.
144. **L, E** Glass text: COSG3073's lock-screen clock digits are glass (each stroke refracts the chart like a tube,
     ~60-150 s). The library has no glass-text equivalent. COSG3073 is also the clearest edge-bending reference for
     item 56: a wide capsule dragged across the gratings wraps them into U-shaped bands round both caps (22, 40, 85 s).

Already tracked (with Gemini's added evidence):
- Search-button press response (#114, item 48).
- Release and onset timing, and tracking lag or surge (#106, #95; items 18, 75, 76, 104, 107).
- Milky lens fill (#105, #111; items 11, 12, 124).
- Side-cap specular (#109; items 7, 114).
- Wall overshoot on a throw (item 35).
- Neck cusp colour streaks (#107, #108).
- The resting pill reading 2-3 levels low on docs/2 and JGEY2190 (item 40). Its geometry matches iOS exactly
  (284 × 162 at IMG_6756 34.2 s), so "wider" was wrong.
- The light lens's drop shadow, which Gemini reads as prominent while item 50 says ours is the stronger one: to be
  measured before changing.

Replay, script or scene, not the library:
- The search episodes in IMG_6756 0-34 s are not in `script3.txt`; replayed with `search-script.txt`, our button
  grows to within 2 px of iOS's (`s6`).
- The finger parks exactly on the tab centres in `gen3.py` scripts (x 479.0 / 217.0 during every dwell, while iOS's
  ink wanders 464-485 px), hence the "rigid" lens in IMG_6756 43-50 s.
- The sample switches its page on touch-down (JGEY2190 4.75 s) where iOS switches on completion (5.00 s).
- Missing Contacts page, badge (item 72) and swipe-home in the sample.

Wrong:
- "The ink is not clipped to the lens": at the cited moments our lens had not arrived yet (the lag above). Once it is
  there, the in-lens blue split matches iOS.
- "Missed inflations, JGEY2190 11-25 s": those are held drags, and ours is formed throughout (formation 1.0).
- Comb streaks below the labels (docs/1 11-12 s, JGEY2190 34 and 39 s): absent in those windows. **Corrected by section
  R (item 146):** they are real, during tap transits, in windows this sweep did not cover.
- Lens "10-14 px right" at the search end: it is 27 px left (item 9).
- "Punch-through" defrosting in IMG_7048 2:37-2:47: the readable text there sits below the bar, not behind the lens.

Noted, out of scope (system UI): Control Center's backdrop blur leaving before its modules on dismissal and the
expanded flashlight slider's lighter background blur (PUSM9766), the glass keyboard (search mode, #112), and
UITabBarController committing a slow scrub's selection without switching the page (sample reference 14-23 s).

## R. Video-model pass, second set, verified (26 October, night)

Set 2 (`.local/gemini/set2/`, built by `.local/gemini/make2.sh`) has 23 more iOS-over-ours clips, still rendered at
current `main`: the search button's presses and pulls (our `s6` render of `search-script.txt`), one- and two-slot
taps, throws, held presses, the long drag and the scrub, plus 2x zoomed clips. The model reported 30 findings. Each
was checked here against the replay trace, the finger script and decoded frames, in scene pixels; the model's own
pixel figures run at about a third of scene scale. Verdicts: 4 new, 13 already tracked (several worse than
described), 1 replay script, 12 wrong.

145. **H, S** Tap travel is slow: on a one-slot tap iOS's pill is centred on the target about 70 ms after the inferred
     touch-down (docs/1 3.463 → 3.53 s) and settled into the pill by 3.70 s. Ours is still between tabs at 3.63 s and
     arrives about 300 ms after the down (trace: x within 20 px at +270 ms). On two-slot taps iOS takes about 130 ms
     and ours about 210 ms (JGEY2190 28.045 s). The gap is measured swell-to-arrival, so it does not depend on the
     inferred touch-down time. This is #70's "pill arrival", worse than described. Code: Calm's `centreOmega = 24`
     and the tap spine spring (`GlassPoseDynamics.kt:374-381`).
146. **M, S** Comb streaks during tap transits: green and black vertical combs along our lens's top rim and under the
     labels while it travels and settles (JGEY2190 4.67-4.82 s and 40.48-40.63 s; docs/1 0.87-1.02 s and 21.30-21.43 s).
     Green-dominant pixels in rows 470-630: ours in 14-15 frames per tap with peaks of 692-992 px; iOS in 2-9 frames
     with peaks of 131-403 px. Likely the rim-mirror and transit-stretch sampling (`GlassShaderSource.kt:311-334`)
     together with the channel crush (item 111). Corrects section Q's "comb streaks wrong".
147. **M, S** Search press flash: at the moment of contact iOS's magnifier dims from 255 to about 120 for one frame and
     recovers within about 50 ms, on all seven presses in IMG_6756 0-34 s. Ours stays at 255.
148. **M, E** Search pull and overlap: on a diagonal pull (IMG_6756 10.5 s) our button leans about 20° along the pull
     vector with a sheared glyph and a dark offset ghost of its body behind it, where iOS's stays an upright oval
     (`glassPullDeformation` takes the full 2D angle, `GlassPull.kt:103`). Pressed against the bar (20.3 and 31.0 s),
     iOS lets the bar's end show through the button's left side with a soft shadow on the Keypad pill; ours draws the
     button opaque over it (`GlassTabBarAccessory`, `GlassTabBar.kt:1014-1030` and `:2199`).

Already tracked (with the numbers this pass adds):
- Wide lens on taps and scrubs: 2-slot taps widen to 405-414 px against iOS's about 330 (#119, #120).
- Landing collapse 50-80 ms early: after a throw iOS keeps the lens to about 17.95 s, while ours is flat by 17.90 s
  (formation 0.94 → 0.38 within 100 ms). The release `formTarget` tracks speed alone (`GlassPoseDynamics.kt:1106-1116`).
  Item 36, #95.
- Taps stay inside the bar (#120).
- Search release overshoot: 22 px against iOS's 32 (item 46).
- Heavier search glyph: about 25 % more ink (#110).
- Bar edges and the black band inside the lens (#108).
- Side caps crush glyphs (#121).
- Interior bloom and ink ghosts (#105, #108).
- Late spring tails (#106).
- Resting pill contrast (item 40).
- iOS's bar edge dims toward the right end (99 → 73-92 over x 760-830) while ours stays flat at 98 (#109).

Replay script: the "detents" are the inferred finger, which `gen3.py` parks at exactly 217.0 and 479.0. Our lens
follows the finger to within 15 px throughout (LOLL8185 23-25.8 s; #122).

Wrong, by measurement:
- "Inconsistent tap inflation": ours peaks at formation 0.20-0.21 on every one-slot tap and 0.25-0.30 on two-slot taps.
- "0 px end-cap overhang": the left rims sit at 53-54 against iOS's 52-53 on Calls holds, and thrown lenses
  overhang 46-51 px.
- "Seam at x≈315": our bar's edges and pill are identical at rest and pressed.
- "Single-step release snap": ours springs with a 22 px overshoot and a 7 px rebound, against iOS's 32 and 10.
- "Search button frozen during a hold": ours settles too, matching iOS's width at 5 px taller.
- "Stroke-weight pop": the glyph grows 1.63x on ours and 1.76x on iOS.
- "Icon and label clip mismatch": the different spans come from the lens's rounded cap, on both.
- "Pre-magnified Keypad": 71 × 69 px on ours against 70 × 68 on iOS at 34.55 s.
- "Weaker magnification on docs/2": 84-85 px on both.
- "Flat central plateau": ours shifts the icon about 15 px across a pass, iOS about 7.
- "No width settle": ours undershoots to 280 px and returns to 291.
- "Bar and button nudge": the button holds at 959-1068 px on both.
- "Pitch-black resting button": fill 32 on both.
- "Cusps on leftward pulls".
- "3-5 px bar-to-button gap": 24 px on both.

Throw squash at docs/2 16.6-16.8 s: reversed. On that throw ours flattens (trace width 401-440 px) and iOS's stays tall; added to #119.

## S. Video-model pass, third set: light mode and our past work (26 October, night)

Set 3 (`.local/gemini/set3/`, built by `.local/gemini/make3.sh`) has light-mode pairs from IMG_7049's held drag (ours
is `calm-light` of an inferred script, set `l6`), plus three-row verification clips: iOS, ours on 20 October (`p20`,
about v1.0.0) and ours now (`n6`). Checked here against decoded frames.

- **The one claimed regression is wrong.** The "ghost of the label below the baseline" at IMG_6756 35.3-35.8 s is the
  descenders of "y" and "p". It is identical on 20 October and now (label rows 590-608, descender rows 611-616), and
  iOS has the same rows. Nothing got worse.
- **The improvements it reports** (fringes, neck width, squircle lens, blue, outline, resting rim, transit stretch)
  agree with the measured changes since 20 October. They are its by-eye judgement, not new measurements.
- **Light mode, wrong when measured:**
  - "No refraction or magnification": held icon 85-87 px against iOS's 85.
  - "Achromatic": rim colour 21/20 against iOS's 21/6.
  - "Lens stays inside the bar": not supported (see below).
  - "Milky haze": the lens interior is white 255 on both, and our black glyphs are 0 against iOS's 27, the opposite.
  - "Stray glass bubble": it is our page's avatar seen through the bar's end.
  - "No resting magnification": 69-73 px on both.
- **Light mode, real and already tracked:**
  - The lens shadow: ours darkens rows 646-660 by 7-16 levels, iOS's darkens rows 660-670 by 8-13. So ours is darker
    and sits closer to the bar (#104, item 50).
  - The slow release (#106).
  - The light accent (#110, item 122).
  - The specular crest and outline (#104).
- **Light mode, real and new (on #104):**
  - The lower third of our held lens is grey: rows 634-648 read 245-248, while iOS's is 255 down to the rim.
  - Ours draws a dark-bright-dark line at the bottom rim (240 / 250 / 237); iOS has a single soft shadow below it
    (250 → 242).
  - So the "haze" and "double line" claims were half right: they are real, but at the bottom and on ours.
- **Unverified:** the held lens's height in light. The edges are too faint to threshold reliably; a calibrated
  capture is needed (#126).

**Rerun with Gemini 4 Argon (same videos, 17 findings), checked against frames:**
- **Two real regressions since 20 October:**
  - The tap comb streaks: 0 green pixels on `p20`, up to 695-992 on `n6` (#124).
  - Reddish-brown colour at the search-end neck: 74 saturated pixels now, 0 on iOS and on `p20` (#108).
  - Its other regression item, the wider neck, is intended (sample `lensFusion` 24 dp).
- **Improvements confirmed with numbers:**
  - Rim fringes and refraction band (trough depth and fringes now match iOS's).
  - Blue: ours (7, 150, 255) against iOS's (9, 152, 255).
  - Resting rim peak: 97 against iOS's 97-99; it was 69 on 20 October.
  - Thin outline without the old halo.
  - Squircle shoulders.
  - Unchanged, as expected: the tap stretch geometry.
- **New, light mode (#128):**
  - A pale bridge between bar and search button at rest, where iOS has a clean gap.
  - A dark rim (196-201 against iOS's 216-217) while the lens collapses on release.
  - A top-to-bottom gradient in the resting pill (245 → 228, against iOS's roughly even 234-241).
  - A fainter held outline.
- **Wrong:** the one-frame "pop" at the lift (no large frame change at 48.30 s).
- **Replay script:** the "frozen lens while iOS glides" (39.4-39.9 s); the light script follows iOS's ink, which stays on Contacts while iOS's lens moves (#122).
- **Already tracked:** selected icons' inner glyph colour (sample icons, #99), end overhang at Keypad (#107), icons
  3-4 px high (#110), light rim hue (#104).

Its suggested fixes (a 125 ms release spring, a 52 px lift, "enable dispersion in light") rest on the wrong readings
above and are not adopted.

## T. Video-model passes, fourth round: GPT and Argon sessions 01-16 (27 October)

ChatGPT 6 Pro reviewed set 3 (verify and light clips; 14 findings), and Gemini 4 Argon ran sessions
01-16 of the 30-session kit (FIND, TIMING, OPTICS, INK, CONSISTENCY, TARGETS and SKEPTIC over sets 1-3,
the issue clips and the iOS-only tap and hold references). Each candidate was measured on decoded
frames against our `:desktop:phoneReplay` renders (n6 = main 2a7c7b0, p20 = 20 October, s6 = the search
script, l6 = `calm-light`), in scene pixels. The TIMING and TARGETS tables (sessions 02, 06, 11, 13-16)
are reference numbers for the fixes and are checked when each fix is scored, not here.

**Confirmed and filed**

| Finding | iOS | Ours | Issue |
| :-- | :-- | :-- | :-- |
| Light: held selected glyph's cut-out | (252, 255, 255) | (0, 57, 120) | #129 (new) |
| Unselected glyph cut-out at rest (dark) | (41, 41, 41) | (120, 120, 120) | #129 |
| Held drag off the trailing tab: bar edge and search gap (47.3-47.8 s) | x 59-60, gap 896-920 | x 38, fused | #93 |
| Bar pulled up by the finger in a held drag | top 460 → 440 | 452 (no y in our script) | #93, input-limited |
| Search-end bridge during a still release (lit rows at x 909) | 111 → 0 by 36.30 s | 50 → 36 → 68 → 0 at 36.47 s | #106 |
| Bar spread on a one-slot tap (left edge) | 60 → 42, fused | 63 → 59-61 | #120 |
| Held search press light (fill luma) | 87.5 → 71 | 97.7 → 88.5 | #125 |
| Search release squash | 179 × 164, top 485 | 187 × 170, top 475 | #125 |
| Throw landing label ink, docs/2 8.63-8.66 s (20 Oct: 841, 782) | 1160, 1218 px | 538, 485 px | #94, regression |
| Light: selected ink after landing on Keypad | 6242-6291 px, to row 617 | 5428-5741 px, to 606-611, regrows to 6528 | #128 |
| Light: neighbour pull of unselected Contacts | 481.7 / 476.8 | 485.3 / 474.7 | #128 |
| Held lens colour bands run straight (GPT R1, by eye) | follow the oval | straight strips | #124 |

**Already tracked:** fast scrub sausage (#119), tap onset and travel (#123), tap middle-icon
magnification and flat tap lens (#120), wall and throw landing overshoot and icon recoil (#95),
press brightening before formation (#106 item 107), still release two-stage (#106 item 104), search
rim 105 vs 97 at rest (#109 item 115), release bar dip (#106 item 105), lens overlapping the search
button on landing (#107), label split at the rim (refraction detail), selected blue (#99), lens shape
(#93), weaker light fringes (#120).

**Wrong or not from the renderer**

- Set 1's search-button clips (search press, vertical pull, glyph scaling) use the n6 render, which
  has no search gesture; set 2's s6 clips are the valid ones.
- "Now worse than 20 October: black trough erases the rim inside the held lens": iOS has the same dark
  band (row minimum luma 1.0 at 455-456 and 635-636); ours 3.0, 20 October 18.
- "Rim slices the top Keypad dots on press / drag": selected ink in icon rows is 2278-2789 px now,
  2402-3194 on 20 October, 2261-2827 on iOS; no loss against iOS.
- "Our throws overhang the bar's left end 30 px": at the measured landings iOS's lens reaches x 25-40,
  ours 39-45.
- "Search button goes dark ~190 ms early after release": fill luma matches from 8.10 s (35.7 vs 35.4).
- Light "double border inside the lens on Calls": iOS draws the same two lines (x 11-13 and 45-46).
- Light "frost and magnified page content": our page content differs from iOS's.
- Held lens "pinned while iOS's drifts", "page switches mid-travel", tap delays of 33-240 ms: the
  inferred finger input.

## U. Argon sessions 17-25: iOS-only reference measurements, spot-checked (27 October)

Sessions 17-25 measured iOS alone (zips G drags, H search, I light, J calibration chart), so they file nothing
new; their numbers are candidate targets. Each was spot-checked on the same reference clip Argon saw, at its own
frame numbers and clip pixels (`.local/gemini/ref/`), and where useful against the source in scene pixels:

| Session | Claim (clip px) | Measured | Verdict |
| :-- | :-- | :-- | :-- |
| 17 (drags, targets) | docs/2 13.0 rest bar 42-597; held Keypad lens rows 105-248, 103-250, 103-251 (F012-F035); landing bar left 24, 17, 39, 42, 44 (F044-F056); scrub touch-down bar from 28 (F012) | 42; 105-249, 103-250, 103-251, 103-250; 24, 17, 39, 41, 43; 28 | matches (±1 px) |
| 18 (drags, timing) | docs/2 8.3 landing: rim reaches x 0 (F023), 3 oscillations over 450 ms | rim reaches 33 (F023); ink centre 157, 142, 160, 161, 161 (F020-F047): one dip, settled by F031 (~130 ms) | wrong in size and timing |
| 19 (drags, optics) | docs/2 13.0 (440, 180) rest 9.4, held 54.4; (85, 180) 31.4 | 9.4, 51.4, 31.4 | matches |
| 20 (search, targets) | resting button 83 px, glyph 30, fill 32, press fill 89-94; glyph grows 1.30x on press (F069-F072) | 186 scene px, glyph 29 → 38 clip px (1.31x; source 68 → 86, 1.26x), held fill 87.5 | matches; ours also grows (72 → 89-92, 1.24-1.28x), no issue |
| 21 (search, timing) | one-frame glyph dim before expansion; release squash -14 px height | the #125 press flash; -33 scene px (-14.7 clip) after the 8.0 s release | matches |
| 22 (light, targets) | App Store bar rim rows 214 and 339 at x 455 (F178) | 250 at 214-215, 245 at 339 | matches |
| 23 (light, optics) | (42, 270) left-cap outline RGB(216, 218, 216) (F165) | (253, 255, 253); no outline in x 36-50 | wrong at that point |
| 24 (chart, optics) | clock numeral row 80, x 85-110: no colour split, bevel (88, 80) 231 / 228 (F550, F830) | max abs(R - B) 0; 231 and 228 | matches |
| 25 (chart, targets) | lock-screen sheet translation, button centres | not checked | - |

Session 17 is half answered (3 of 6 clips; the rest needs "continue"). Compared with our render (n6, s6), the
checked targets already match and must not break: held Keypad lens on docs/2 13.4-13.6 s rows 435-656 (ours
434-657); search button bottom edge in the upward pull 652, 628, 594 at 3, 5, 7 s (ours 652, 627, 595). The held
fill is 52 on iOS and 48 on ours (minor), and ours forms ~100 ms later at 13.2 s (#106). No new issues.

So Argon's iOS-only numbers are right far more often than not (6 of 8 checked; session 20's glyph was first marked
wrong here by mistake: its times are clip times, source = clip + 0.5 s). Re-measure a value before using it as a
fix target, as with sessions 18 and 23. Sessions 26-30 and the rest of session 17 are verified in section V.

## V. Video-model passes, fifth round: Argon sessions 26-30 and completed targets (27 October)

Gemini 4 Argon completed the remaining sessions 26-30 (TARGETS 26, 27; CONSISTENCY 28; TIMING 29; OPTICS 30)
and the second half of session 17, completing the full 30-session Argon kit. Each candidate was measured
on decoded frames against our `:desktop:phoneReplay` renders (n6 = main 2a7c7b0, p20 = 20 October, s6 = search script,
l6 = calm-light), in scene pixels. The targets (sessions 26, 27, 28) are reference numbers for future fixes
and lock-screen / Control Center components.

**Confirmed and filed**

| Finding | iOS | Ours | Issue |
| :-- | :-- | :-- | :-- |
| Fast scrub spawns green/cyan optical bridge to search button (gap x 590-625) | pure dark gap (y 35-40: luma 1.0-2.0, RGB [19, 19, 19] to [22, 22, 20]) | green/teal streak (y 188-194: luma 44-68, RGB [44, 78, 63] to [33, 87, 63]) | #119, #107 |
| Settled selection pill bottom rim chromatic fringes (Calls -> Contacts) | achromatic neutral grey (RGB [69, 71, 71] in F056, [51, 51, 51] in F072) | chromatic fringes at bottom rim (RGB [71, 71, 93] blue, [72, 54, 54] red) | #111 |
| Search button two-stage press onset (IMG_6756 0-34 s, 7/7 gestures) | F0 glyph dims 255 → 133-142; F+1 radial button expansion +25 px in 133-150 ms | passive / single-phase | #125 |
| Search button trailing-edge pinning under pull (left & up) | trailing edge pinned at rest (left: x 491, 118 × 93 px; up: y 357-358) | translates symmetrically | #125 |
| Search button single-frame unlit flash at release onset | single-frame dark dip (fill luma 32.0, 4/7 gestures) before snap rebound | monotonic / no dip | #125 |

**Already tracked:** fast scrub horizontal stroke truncation (-145 px amplitude loss) and turnaround lag (#119, #122), tap onset lag (66.7 ms) and travel duration ~300 ms vs iOS ~70-130 ms (#123), tap middle-icon magnification and flat tap lens (#120), travelling lens vertical deflation during drag (#119, #121), held lens elevated grey in rim moat (Y=7.4-10.5 vs iOS 0.6) (#108), held lens over content avatar brightness gap (Y=107 vs iOS 131) (#72), light mode held lens missing downward-refracted inner bar top outline (#104, #128), held lens vertical overhang 147 px vs 142 px and dimmed bottom specular highlight (#93, #92), refracted inner bar edge opposite motion during still release (#106), tab bar capsule stretching +30-36 px during held press and translating toward drag (#93), tab icons translating outward 10-14 px and magnifying 17%-35% under held lens (#110, #120), end-of-bar throw overshoot 30-37 px and 2-3 spring oscillations (#95), flashlight button release undershoot -10 px and Control Center velocity-dependent overshoot (#113).

**Wrong or not from the renderer**

- Missing elastic horizontal rebound overhang past left bar cap on landing in `124-comb-streaks-x8.mp4`: iOS's 14 px overhang (x 28..42, Y=58-64) at F108-F114 is not an elastic rebound, but a second touch-down/press on Calls present in the recording that was omitted from the replay script (inferred finger input, #122).
- Light mode "insufficient background blur in bar and lens leaves text readable (std=7.35 vs 0.41)": our sample's page layout places "Avery Westbrook" text directly behind the bar, whereas iOS's recording has white/empty space at those coordinates (different page content).
- Lateral pre-step (8-14 px) and opposite rebound in first frame of touch-down: thumb contact roll / touch-target snapping in the inferred finger input.
- Mid-drag vertical deflation and premature 166.7 ms snap to Contacts in `121-side-caps-x8.mp4`: tab-centre parking in the replay script, which snaps the finger directly to the tab center and triggers synthetic velocity-dependent lens flattening.

## W. Independent takeover verification (29 October, in progress)

The earlier statement that all 30 sessions are fully verified is too broad: section U
explicitly leaves session 25 unchecked, and section T defers target tables until fix scoring.
Codex is independently checking those claims; Claude must review these checks on return.

- **Corrected provenance:** session 25 clock samples omit the source clip start of 60 s.
  F594/F702/F835 map to COSG3073 82.349349/85.566016/89.966016 s, not 22–30 s.
  Selected bevel RGB probes match; complete geometry/trajectory verification remains pending.
- **Tracked, reproduced:** session 30's #111 rim chroma at F056/F072 matches decoded clip
  pixels. Its #123 clip source start is 3.40 s, which its source-time fields omit.
  Three original HDR-to-sRGB source frames confirm the iOS rim is neutral (channel spread
  1/2/1 levels in the mapped ROI). The renderer's third sampled clip frame is nearly neutral,
  so do not claim persistence across every settled frame.

Exact probes, methods and limits: [independent record](../agent-takeover-2026-10-29/README.md).
No new optical constants were adopted from these partial checks.

## Grouping into issues

| Issue | Items |
| :-- | :-- |
| Held-lens rim optics: shape, outline, highlight, slab (new) | 3, 5, 6, 7, 12, 49, 59 |
| Rim fringes (#73) | 1, 14, 22, 32, 38, 45, 51 |
| Refraction strength vs content break-up (#77, closed; 15 and 16 moved to #121) | 2, 4, 15, 16, 24, 94 |
| Bar growth and lens geometry while held (new) | 8, 9, 10, 19, 20, 54, 101 |
| Held lens over content (#72) | 11, 13, 86 |
| Tap transit: smear, shape, plateau, timing (#70 + new) | 23, 25, 26, 27, 28, 29, 30, 31, 73, 74 |
| Formation, throw and landing timing (new) | 17, 18, 21, 33-37, 75-80 |
| Resting bar, pill and search button (new) | 39-48, 52, 53 |
| Generic glass over the calibration chart (new) | 55-64, 99 |
| Clear lenses (ball-lens inversion) (new) | 65-67 |
| Ink, label and icon parity in the sample (new) | 68-72, 100, 102 |
| Device performance (new) + #78 #81 | 81-85 |
| Scoring and tooling gaps (new) | 87-89, 96, 98 |
| Components without references (new) | 89-92 |
| Internal consistency of the selector material (new) | 93, 95, 97 |
| Light appearance (new) | 50-54 |
| Selected ink lifts under the held lens (#105) | 103 |
| Held-lens press and release timing (#106) | 104-107 |
| Lens overlaps the search button (#107) | 108, 109 |
| Held-lens rim colour (#108) | 110-113 |
| Resting rim highlights (#109) | 114-116 |
| Glyphs and layout (#110) | 117-122 |
| Transit and landing appearance (#111) | 123-125 |
| Search mode (#112) | 126 |
| Control Center and lock-screen references (#113) | 127, 128 |
| Pressed glass lights directionally and dims while held (#114) | 130 |
| Follow the system's accessibility settings (Reduce Transparency, Increase Contrast, Reduce Motion, large text) (#115) | 131, 132, 133 |
| Haptic feedback for selection changes (#116) | 134 |
| Desktop and web: pointer hover and keyboard focus on glass controls (#117) | 135, 136 |
| Lower the glass's cost automatically when frames are missed (#118) | 137 |
| Fast scrub flattens the held lens to half the bar's height (#119) | 139 |
| Tap transit should rise into a full glass lens (iOS 27) (#120) | 140 |
| Lens side caps cut content instead of bending it round (items 15, 16, re-homed from #77) (#121) | 141 |
| Other section Q items: 142 → #96, 143 → #72, 144 → #113 and #97 | 142-144 |
| Tap travel takes ~300 ms; iOS's pill arrives in ~70-130 ms (#123) | 145 |
| Green/black comb streaks along the lens rim during tap transits (#124) | 146 |
| Search button: press flash, upright pull and see-through overlap (#125) | 147, 148 |
| Light mode: resting neck, dark rim on release, pill gradient, faint outline (#128) | section S |
| Light mode: the held selected glyph's cut-out is navy; iOS's is white (#129) | section T |

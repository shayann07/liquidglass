# PLAN-RESULTS — running log for MASTER-PLAN.md



Started 2026-09-13 by Claude Fable 5.1. One entry per experiment: id, what was measured, the

number, the threshold, the branch taken, the frames used. Scripts and JSON records live in

`analysis/inverse-v1/` (`tools/*.py`, `E*_*.json`); decoded seed frames and scratch in

`analysis/inverse-v1/scratch/` (compressed npz, never a PNG per frame).



Revision 2 (after Astra's review of packet 1): acceptance claims revised as instructed —

E1 establishes repeatability, not absolute outline accuracy; E5 is not a PASS; E2's amplitude

ratios are effective fits, not identified path weights. Repairs and new cards below.



Devices: the S24+ (the plan's transport 4) is absent. Astra assigned the **Pixel 7** for E1's

device leg (serial 28121FDH2006ZF; 1080×2400, density 420 = 2.625, Android 17, 60/90 Hz). The

Huawei JKM-LX1 is fallback-renderer regression only. No commits, no shader changes.



## Frame numbering and provenance (affects every video card)



The plan's frame numbers are the old index's zero-based frames. That index (`scan2.py`) decoded

with ffmpeg 4.4.1 default vsync = **CFR at r_frame_rate 60** for rawvideo output, while every

phone recording is **VFR**:



| file | source frames (packets) | CFR frames (old index) | duration s |

| :-- | --: | --: | --: |

| COSG3073 | 14,517 | 21,299 | 354.98 |

| LOLL8185 | 6,413 | 10,537 | 175.59 |

| LQCO6923 | 7,263 | 9,994 | |

| PUSM9766 | 1,787 | 2,811 | 46.88 |

| IMG_6667 | 195 | 310 | 5.15 |



`tools/decode.py` maps CFR k → source index → PTS (1/600 s) and decodes by keyframe seek with

`-vsync 0` through the PQ chain. The map is now **empirical and chronological**

(`cfr_verify.py`: signatures of a full old-style CFR decode matched in order against a

passthrough decode; identical pixels at different times keep their own PTS). Against the

ffmpeg-4.4 simulation it differs at 97 (LOLL) / 410 (COSG) duplicate boundaries by 1–3 frames.

Relabelled seed neighbours: CFR 1046 → source 490 (was 491), 1327 → 560, 1338 → 564, 1340 →

565; their band.npy pixels and edge metadata now match exactly. All primary seeds unchanged.

The passthrough muxer drops only the final frame of each file (dts tie); the simulation fills

it. Per-source-frame PTS and durations are in `scratch/<name>_packets.csv` for E7.

Holdout manifest note: E2 consumed the ±2 neighbourhoods of the S2 (7345, 7498, 7508, 7514)

and S4 (13425, 13434, 13475) seeds as well as S3's; those neighbourhoods (±30 frames per the

plan's S3 rule) are excluded from the locked geometry score.



## E0 — input identity, encoding, registration, cache audit — PASS (with gates)



Records: `E0_manifest.json`, `E0_s0.json`, `E0_b0.json`, `E0_stills.json`, `E0_cache.json`.



- Chain for every phone frame:

  `zscale=t=linear:npl=100,format=gbrpf32le,zscale=p=bt709:t=iec61966-2-1:m=bt709:r=full,format=rgb24`

  (ffmpeg 4.4.1-Jellyfin). Mac WDA files are declared SDR (h264 yuvj420p bt709 iec61966-2-1

  pc) and decoded plain; C6c-r0 is 120 fps, C6c-r50 60 fps, C9 60 fps nominal, all VFR.

- **S0** (COSG3073 CFR 180–282, 37 unique source frames, DARK): clean frames 197–277: textured

  pixels within 4 levels 98.3–98.4 % (gate ≥ 97), ramp residual (G) mean +0.26, SD 0.46

  (gates ±0.5, ≤ 1.0), white 255, black 0, translation 0.000 px, scale 1.0000. 180–196 are the

  chrome-hide fade (0.87–0.97), 278–282 already dim (white 251): excluded.

- **B0** (PUSM9766): 660–662 pass (0.97–0.98); 2646–2733 at 0.96 (intermediate: colours not

  used); 2640–2645 transition.

- **Stills**: all 32 C1a PNGs at translation 0.00 px, uncovered textured fraction 0.991–0.993,

  white 255. The ramp band lies under Photos' top scrim gradient in the chrome scene (ramp mean

  −4…−7, SD 21–33 in every chrome file, HEICs included): a scene property, not a decode error.

- **P0 HEIC** (IMG_6644–6647): Display P3 ICC, no HDR metadata. Raw decode 0.954–0.958 within

  4 levels, primaries (234,51,35)/(117,252,76)/(0,0,245); ICC-managed to sRGB 0.988–0.990,

  primaries exactly (255,0,0)/(0,255,0)/(0,0,255), white 255, black 0, translation 0.00. PASS

  with colour management. Each HEIC's uncovered statistics equal its PNG twin scene.

- **Tint aliases**: C1a-25 = C1a-35, C1a-50 = C1a-62, C1a-75 = C1a-86 byte-identical (both

  targets). C1a-50/62-dark-dark is pixel-identical to C1a-r62-dark-dark (metadata differs) and

  differs from r50 by 4.4 levels under the chrome. Branch: 35 and 86 admitted with log

  provenance; 50/62 is a duplicate of r62.

- **Cache audit**: fresh decodes match `band.npy` pixel-for-pixel at all 95 seed frames after

  the empirical relabelling; `white/black/c4` reproduce the cache to 0.1 level. Old

  `aggregate.npz` / plain-decode products are not reused.

- Not run: Home Screen wallpaper registration (I0, C4b at 0.11 as expected), Mac C9/S6 gate.



## E1 — comparison coordinate system — repeatability record; estimator audited; device leg in progress



Records: `E1_outline.json` (28 images), `E1_synth.json` (estimator audit).



**Estimator audit on synthetic pixel-integrated profiles** (`e1_synth.py`; 20 sub-pixel phases;

interior 36/103/200, rim bead +55…60 at 1 px, dark contour −13, antialias sigma 0.4):



| estimator | tip, no rim | top run with rim | with 0.4 px antialiasing |

| :-- | --: | --: | --: |

| 25 %-excursion crossing (v1) | bias −0.73…−1.00 px (outward) | −0.60…−0.90 | −0.84…−1.03 |

| boundary-pixel coverage (v2) | −0.006…+0.05 | −0.38…0 | −0.32…+0.04 |



So the v1 record was ≈ 0.85 px too large per side; the coverage estimator is unbiased where no

rim sits in the boundary pixel and up to −0.4 px where one does. On real profiles the two

estimators agree on the tips to 0.1 px after the bias correction; on oblique rows near the

top/bottom they disagree by up to 2.7 px (soft edge + rim), so circle radii from oblique rows

are not trusted. Outline record (tips from both estimators, rows from the straight runs'

first fully-lit pixel boundary; ± = SD over 28 images; absolute uncertainty ±0.5 px on rows,

±0.15 px on tips):



| element | x0 | y0 | x1 | y1 | R (= h/2) |

| :-- | --: | --: | --: | --: | --: |

| pill | 344.6 ±0.02 | 140.5 ±0.5 | 826.3 ±0.05 | 272.5 ±0.5 | 66 ±0.5 |

| toolbar | 341.6 ±0.1 | 2303.6 ±0.5 | 828.4 ±0.2 | 2447.4 ±0.5 | 72 ±0.5 |

| bottom-left button | 85.7–86.3 | 2303.5 | 228–231 | 2446.4–2447.3 | 71.2–72.4 |

| bottom-right button | 939.9–941.1 | 2303.4 | 1083.1–1083.6 | 2446.5 | 71.2–71.6 |

| top buttons | glyph-limited; not used | | | | |



Harness discrepancies remain as recorded (JVM boxes 1–2 px off the tips; Android sample and

scripts 19 px off). Per Astra, the harness boxes were **not** replaced with measured

coordinates. For the device leg the Android sample's calibration boxes were set to the JVM

harness boxes (so both declare one geometry) and the style-length adapter `3·S/D` was added

after the wide-switch override (host code, `sample/.../CalibrationActivity.kt`; not committed).

Device leg on the Pixel 7: see "E1 device leg" below (pending at the time of writing).



## E2 — paired RGB sign audit (PRED-2) — UNRESOLVED → monochrome transport



Record: `E2_sheet.json` (rerun after relabelling; numbers unchanged). Frames: COSG3073 seeds ±2

(S1a 1000/1018/1047, S1b 1328/1339/1350, S3a 11040/46/52, S3b 11166/72/80, S2a 7345, S2b

7498/7508/7514, S4a 13425/13434, S4b 13475), 95 CFR / 55 unique source frames; boundary after

row 389 (ramp→black) for "a", after 463 (black→white) for "b", source depth 67.5–75.5. Joint erf

edge per neutral column (common sigma, per-channel position, free gain/offset); windows upright

y∈[e−80,e−40], mirrored y∈[e−24,e−1]; gradient centroid as the alternative; uncovered control.



- Control: dR−dB = 0.0 in every frame, |p95| ≤ 0.4 px (one frame 1.0).

- Upright copy (display depth 55–77): dR−dB = 0.00, dR−dG = 0.00 in all 8 crossings; all

  channels localized in ≥ 88 % of columns; step-amplitude ratio −0.97…−0.99 for R, G, B alike.

- Mirrored copy (display depth 8–19): per-crossing medians −4.5 (S1a, 11 columns), −0.45,

  −0.1, −0.6, 0.0, +0.3, +2.05, +0.2; crossing-bootstrap median **−0.05 px, 95 % [−0.6, +0.3]**

  (8 crossings, two boundaries, both targets, four pulls). Neither FAIL (≥ +1.5, LB > +0.75)

  nor PASS (≤ −1, UB < 0) → **geometry monochrome; rim colour separate**. "Unresolved" is not

  "zero everywhere".

- Effective chromatic observation (fitted step-amplitude / source-step ratios, signed; not yet

  identified path weights): mirrored copy w_R ≈ 0.94–1.0, w_G ≈ 0.63–0.82, w_B ≈ 0.02–0.31 in

  every crossing; onset frames 11038, 13423, 13474 (e 455, 456, 529) have blue's copy 12–13 px

  shallower with inverted sign and are reported separately.

- Held cross-check (bar-edge landmark, E3): dR−dB = +0.55 (1380 top), +0.3 (1740 top), +0.55

  (App Store 2400 top); below the 1 px gate, same sign as FINDINGS 19.

- Held rim over black: 1380 top run peak luma 64.8 at 1.5 px inside, max R−B 17 (expected 64 /

  16); settled 1740: peak 85.5, R−B 48. The moving/settled difference is an E8/state question.



## E2b — single-source response vs constrained two-path mixture — two-path structure supported; constant weight fails the gate



Record: `E2b_mixture.json`; `tools/e2b.py`. Zone: rows d ∈ [2, d_copy−3] of each seed frame,

columns restricted to fixed intervals free of the sheet's own bottom-edge content (300–449,

550–699, 800–899; chosen once from the b-frame red map), neutral, every 3rd. Direct map

constrained to identity (the continuation of the upright branch, J = 0.62, gives the same target

values here). Models: S1 = a_c·M_c + b_c; S2 = w_c·M_c + (1−w_c)·D_c (unit gain); S2g = S2 with

free gain/offset. Fit on one polarity, predict the other, per pull:



| pull, direction | w_S2 (R,G,B) | predict RMS S1 (R,G,B) | predict RMS S2 |

| :-- | :-- | :-- | :-- |

| S1 a→b | 0.96, 0.80, 0.18 | 28, 79, 226 | 24, 39, 41 |

| S1 b→a | 0.93, 0.71, 0.12 | 122, 53, 199 | 9, 28, 21 |

| S2 a→b | 0.82, 0.38, 0.11 | 13, 83, 292 | 40, 95, 43 |

| S2 b→a | 0.97, 0.74, 0.15 | 29, 118, 227 | 23, 52, 37 |

| S3 a→b | 0.99, 0.80, 0.15 | 30, 93, 231 | 30, 52, 39 |

| S3 b→a | 0.93, 0.66, 0.11 | 126, 44, 207 | 9, 28, 18 |

| S4 a→b | 0.94, 0.66, 0.16 | 12, 64, 262 | 13, 44, 38 |

| S4 b→a | 0.98, 0.80, 0.17 | 16, 82, 213 | 13, 46, 37 |



Onset frames (separately): w ≈ (0.99, 0.96, 0.55). Reading: a single source with per-channel

gain cannot cross polarities (blue 199–292 levels); the two-path mixture can (18–43), so the

near-rim zone is a blend of two source paths with channel-dependent weight. A constant weight

per channel is still 20–95 levels off in G, so the weight varies with depth (and with the

rim/tone terms); the depth-dependent w_c(d) and its gain go to E9's photometric block. No

Fresnel claim.



## E3 — outward sampling (sign part) — PASS → M0 rejected for the held state



Record: `E3_sign.json`. Frames LOLL8185 1260 rest, 1380 Calls lens (moving), 1740 Keypad lens

(settled), 2342 App Store rest, 2400 App Store Games lens. Bar/lens outlines from coverage

crossings over black (rim dark contour where an end lies over the bar). Landmark: the bar's own

top/bottom edge; grown source depth s inside the lens vs displayed copy depth d (joint erf per

column, G). Delta = s − d = y_bar − y_copy (the lens-edge coordinate cancels):



| patch | s | d | Delta median | 95 % CI (column bootstrap) | column p05–p95 (distribution) | columns |

| :-- | --: | --: | --: | :-- | :-- | --: |

| 1380 top | 16.08 | 28.70 | **−12.6** | [−30.5, −11.7] (bimodal: 5 % of columns hit the glyph) | [−38.3, −10.9] | 120 |

| 1380 bottom | 16.30 | 27.79 | **−11.5** | [−11.8, −11.2] | [−35.2, −10.8] | 120 |

| 1740 top | 12.08 | 24.95 | **−12.9** | [−13.4, −12.5] | [−34.9, −12.4] | 41 |

| 1740 bottom | 12.02 | 25.10 | **−13.1** | [−26.9, −12.4] | [−36.9, −11.5] | 131 |

| App Store 2400 top | 12.80 | 24.41 | **−11.6** | [−11.6, −11.5] | [−11.6, −11.5] | 18 |



(The earlier "p95" figures were distribution percentiles, now labelled as such; the CIs are

column bootstraps and are wide where a minority of columns localized on a glyph.) Gate: median

< −5 px in ≥ 2 patches, upper bound < −2, landmarks visibly outside: PASS. **M0 rejected for

the held state.** Bar transform outside the lens, like-for-like ends: rest right end 897.2 →

held 1380 918.5 (+21.3); rest left end 61.8 → held 1740 41.6 (−20.2); height ratio 1.044 /

1.052; lift +15 / +13 over black; App Store bar top 2281.75 → 2277.89, lift +17.5 over content.

Lens outlines: 1380 228.2 tall (R 114.1), right end contour circle r 118.5 rms 0.17; 1740

221.4 tall, left end r 111.8 rms 0.05.



## E3 seam continuity + E4 ownership — INSUFFICIENT to discriminate; compositor retained, continuous map equally predictive



Record: `E3_seam.json`; `tools/e3_seam.py`. App Store Games lens 2400 top run, 30 text-free

columns (x 300–400; even = train, odd = test), rows d = 3…60 below the lens top 2265.09.

Source model: page above the bar from the rest frame 2342 (static, verified), bar rows from the

rest frame resampled by the 1.05 growth about the bar centre plus +17.5 lift (residual < 1 level

outside the lens). The raw page under the bar is never exposed in H0–H5, so the composited bar

is the interior source (plan E4 fallback branch).



| model | dof | train RMS | test RMS all d | test RMS seam d 20–50 | notes |

| :-- | --: | --: | --: | --: | :-- |

| C_cont (one source, 9-knot piecewise-linear s(d), gain, offset) | 11 | 6.43 | 8.39 | 6.63 | s = 15, 7, 0, 1, 7, 11, 12, 12, 15, 19, 27, 38, 46, 48, 51, 58, 65, 67, 68 at d = 3…60 step 3; max |ds/dd| 3.6; transition d ≈ 27–39 |

| C_switch (band map to d*, identity interior, smoothstep mask) | 10 | 6.58 | 8.57 | 6.71 | d* = 30.0, w = 4.0 (search widened to 12 px beyond the card's 3 px limit: recorded domain change, not an accepted width), skipped source 18.9–30.0 |



Caveat (external review): the seam fit has no rim term, so its s ≈ 0 at d ≈ 9 may be the
self-luminous rim captured as a landmark; the curve below d ≈ 6–9 is not to be used, and the fit
should be rerun with d < 6 masked before any model consumes it (not done; awaiting ruling).
Per-depth held-out RMS ≈ 1 level for d 27–57 under both; 8–17 for d 6–24 under both (the band's

tone is not captured by one gain/offset). C_switch does not improve held-out RMS (< 2 levels

gate) and the continuous map needs no jump: a compressed transition ≈ 12 px wide. Branch:

seam unresolved → per Astra, retain the effective held compositor with this uncertainty; the

continuous representation is equally predictive here and stays a live alternative for E9.

E4: interior (rows 2330–2420, cols 240–460, glyphs masked, 11,558 px) vs the composited-bar

source: gain 0.66, offset +59.9, RMS 9.7 after fit, mean delta +29.4; raw-page share

unobservable (never exposed) → composited-bar source retained, rawShare stays a display recipe.



## E5 — frozen tangential prediction (PRED-1) — INTERMEDIATE (not PASS); fold shape provisional



Record: `E5_tangential.json` (v3); `tools/e5.py`. Geometry from E1 v2 (R 72, centres 413.6 /

756.4, cy 2375.5, ±0.5 px). Protocol change kept and recorded: training = right end over the

whole 32-px stripe band (the ±20° tip sector has < 5 px of stripe phase); the left end is

withheld. Training estimator: NCC of the high-passed (sigma 6°) ring luma against the target on

the source ring, s ∈ [−6, 50] step 0.25 with a full local-maxima alias search; frozen by linear

interpolation; test: cross-ring NCC (gate ≥ 0.80) and arc RMS of stripe crossings predicted

from the SOURCE ring position (gate ≤ 1.5 px), plus the card's 20–70° sectors. C1a-r31 light

and dark-target stills are reserved, unread.



Training curve, right end (light-appearance images / dark-appearance images):

d = 4: 42 (weak) / 29 (weak, aliases); 6: 37.5 / 38.5; 8: 34.75 / 35.25; 10: 33 / 33.25;

12: 31.5 / 31; 14: 29 / 28.25; 16: 28.25 / 27.5; 18–22: 28 / 27–27.25; 24: 28.5 / 29.5;

28: 30 / 30.75; 32: 32.5 / 33.5; 36: 35.75 / 36.5. NCC 0.79–0.99 except d 4.



Frozen prediction on the left end (NCC_pred / arc RMS px / crossings matched):



| d | light group (4 images) | dark group (4 images) |

| --: | :-- | :-- |

| 6 | 0.85–0.86 / 0.4 or none / 0–4 | 0.84 / 0.7 / 1 |

| 8 | 0.79–0.83 / 1.6–1.9 / 2 | 0.95–0.96 / 0.3 / 1–2 |

| 10 | 0.61–0.80 / 1.4–2.3 / 2 | 0.90–0.91 / 1.1 / 2 |

| 12 | 0.54–0.79 / 1.4–2.4 / 1 | 0.98 / 0.0–0.1 / 1 |

| 14 | 0.91–0.95 / 0.5–0.7 / 2 | 0.70–0.72 / 1.8–1.9 / 2 |

| 16 | 0.89–0.93 / 0.5–0.7 / 2 | 0.71–0.72 / 1.7 / 2 |

| 18 | 0.91–0.94 / 0.5–0.6 / 3 | 0.73–0.74 / 1.5 / 3 |

| 22 | 0.95–0.97 / 0.3–0.4 / 4 | 0.75–0.76 / 1.4 / 4 |

| 28 | 0.94–0.95 / 0.6 / 10 | 0.92 / 0.4 / 8–9 |



The frozen map predicts the withheld end to ≈ 1 px (free left-end fits sit 0.75–1.75 px from

the frozen values; no alias peak within 0.8 of the best for d ≥ 8), but the gates are missed at

d 10–12 in the light group and d 14–22 in the dark group. Outcome: **INTERMEDIATE** — retain

radial (normal-only) geometry on capsules as the lowest-parameter approximation; tangential

residual ≤ 2.4 px; no shear term is supported or excluded at this precision. The old PRED-1

(plateau ring ratio) is retired, not counted. The fold shape (reversed zone d < ~14, minimum

s ≈ 27–28.5 at d 16–22, identity from ≈ 33) is the E9 rest candidate, provisionally.



## E9 (rest + sheet geometry, monochrome) — first pass: one shared fold-capable surface fits both roles; n not identified

Records: `E9_pairs.json` (landmark pairs), `E9_geometry.json` (M0 and EFF fits), `E9_held_M1.json`;
scripts `e9_data.py`, `e9_fit.py`. Data: rest (d, s) pairs from E5's ring estimator on both toolbar
ends of 8 images (NCC ≥ 0.8, d 6–36; 94 train = light-appearance images, 96 validate = dark
appearance); sheet (d, s) pairs = mirrored and upright copies of the row-389 boundary at edge rows
e = 400…530 (`e9_data.py`, G channel, ≥ 60 % of columns localized, SD ≤ 1.5 px) plus the E2 seed
crossings: 97 train (S1 + S2's one point), 90 validate (S3, S4 — the plan's locked sets, consumed
here as a first look and recorded as such). New sheet facts from the sweep: the boundary is
displayed nowhere for source depth s ≤ 60.5 (hidden), has a mirrored copy at d 6.6 (s 80.5),
12.9 (70.5), 14–19 (67.5) and an upright copy at d 65.0 (70.5), 79.4 (80.5), 90.5 (90.5) and
identity beyond; the four pulls agree to 0.2 px except S4's mirrored copy (14.9 vs 12.9).

Model M0 (plan section 10 family, exact planar-bottom Snell, section 4): shared n, β, c1, c2, τ,
γ; a_rest, a_sheet (parametrized as fractions of τ − 0.02 so h > 0 everywhere); R_rest = 72
(adopted outline), R_sheet free (a straight edge has no radius; band scale nuisance). 64 starts
(8 with n ≥ 2, 8 with n ≤ 1.3), Huber loss, bounds as in the plan.

| | rest src RMS | rest landmark RMS | sheet src RMS | sheet landmark RMS | missing roots |
| :-- | --: | --: | --: | --: | :-- |
| M0 train | 0.36 | 1.06 | 1.82 | 2.02 | rest 3 (plateau 0.2 px below model min), sheet 0 |
| M0 validate | 0.92 | 1.50 | 2.75 | 2.98 | rest 16 (dark-group plateau 27.0–27.5 vs model min 27.7), sheet 0 |
| EFF train (11 params) | 1.22 | | 1.83 | 2.22 | sheet 0 |
| EFF validate | 1.16 | | 3.25 | 2.85 | sheet 0 |

Best M0: n 1.845, β 0.514 (W_rest 37 px, W_sheet 92 px), c1 −0.53, c2 −0.02, τ 0.317, γ 0.25,
a_rest 0.297, a_sheet 0.218, R_sheet 179. Signed median error per 0.05R depth bin: rest train
≤ 0.34, rest validate −1.37…+0.74, sheet ≤ 0.6 (gate 1.5). Model curves: rest s(d) = 37.8, 33.9,
30.0, 28.3, 27.7, 28.3, 30.0, 32.6, 36.0 at d = 6, 8, 12, 16, 20, 24, 28, 32, 36; sheet s(d) =
73.6, 66.1, 63.0, 62.4, 64.0, 67.7, 73.4, 81.0, 90.0 at d = 10…90 step 10 (hidden source below
62.4, identity from 90). n profile (best of 12 starts per fixed n): cost 112.4 (1.1), 112.0
(1.3), 111.95 (1.5), 109.8 (1.7), 108.2 (1.9), 108.35 (2.2): flat within 4 %, τ and γ compensate
(τ 3.0 / γ 5.0 at the bounds for n ≤ 1.3): **n is not identified; equivalence class spans the
searched range**. Gauge per plan: the admissible solution nearest n = 1.5 will be exported once
E10's withheld-prediction equivalence test is run; no n is reported as measured.
Correction (external review, 13 Sep): this is one *family* with a shared normalized shape and a
role amplitude per element, not one curve scaled by the band width — the rest minimum sits at
u ≈ 0.5 of its band, the sheet's at u ≈ 0.2–0.4. Also, the sheet's "s = 67.5 at d 14–19" in the
E2/E9 records is a spread across frames with slightly different edge rows (per frame it is one
value), not a plateau.
Missing-root classification (Astra round 4, `E9_missing_roots.json`): every missing root is a plateau
observation whose source depth lies *below* the model's minimum (27.74 at d = 20): train 3 (d 18, s 27.5,
deficit 0.24 px); validate 16 = the four dark-appearance images at d 16/18/20/22 with s 27.5/27.0/27.0/27.25,
deficits 0.24 / 0.74 / 0.74 / 0.49 px. Eight of the sixteen are within the ring estimator's 0.5 px
correspondence uncertainty; the eight at d 18 and 20 (0.74 px) are counted as failures. All 16 stay in the
acceptance denominator: rest validate = 80 matched (landmark RMS 1.50) + 16 unmatched of 96.
E9 completion (Astra round 5; `E9_completion.json`, `tools/e9_complete.py`; no new fit — the two retained vectors
of `E10_gauge.json` scored on the same observables with image/pull, frame, element and branch provenance):

| branch / split | n | units | src RMS / p95 | landmark RMS / p95 (matched) | missing roots | n = 1.5: src RMS, landmark RMS |
| :-- | --: | --: | :-- | :-- | :-- | :-- |
| rest train | 94 | 4 images | 0.36 / 0.87 | 1.06 / 2.63 | 3 (d 18, 0.24 px below min) | 0.34, 0.95 |
| rest validate | 96 | 4 images | 0.92 / 1.37 | 1.50 / 2.62 | 16 (all 4 images, d 16–22) | 0.87, 1.53 |
| sheet mirrored train | 40 | 2 pulls | 2.82 / 7.64 | 3.12 / 7.70 | 0 | 2.90, 3.08 |
| **sheet mirrored validate** | 36 | 2 pulls | **4.34 / 9.32** | **4.67 / 7.54** | 0 | 4.00, 4.28 |
| sheet upright train | 57 | 2 pulls | 0.19 / 0.37 | 0.36 / 0.79 | 0 | 0.21, 0.39 |
| sheet upright validate | 54 | 2 pulls | 0.24 / 0.41 | 0.47 / 0.81 | 0 | 0.28, 0.60 |

The earlier combined sheet figures (2.75 / 2.98) hid the branch split: the **mirrored branch fails the sharing gate
on its own (validate 4.3 px source RMS, p95 9.3)** while the upright branch is at 0.24 px. Independent units are 2
pulls per split for the sheet and 4 images per split for the rest arcs, and within the rest sets 23–24 of 94–96
observations are signature duplicates across images (identical end, d, s). The 16 rest missing roots are the four
dark-appearance images' **right** ends at d 16/18/20/22 (s 27.5/27.0/27.0/27.25, deficits 0.24/0.74/0.74/0.49 px
below the model minimum 27.74 at d 19.9) with identical estimator values across images (IMG_6646 and IMG_6647 give
NCC equal to seven decimals: pixel-identical shots of one screen; the two Mac dark captures differ in the fourth
decimal): **one scene's right end, not sixteen or eight independent failures**. The same images' left ends at the
same depths read 28.25–28.75 (no deficit): a 1.25 px end-to-end difference inside one image, larger than the deficit
itself, which points at the ring estimator or the end registration before any missing physical branch; the NCC is
also lowest exactly there (0.84–0.86 at d 20–22 versus 0.91–0.95 elsewhere). The adopted-outline uncertainty (rows
0.5 px, R 0.5 px) moves d, and the curve is flat at its minimum, so destination-depth error alone is ineffective
there; end registration can also change the inferred source coordinate, so both are propagated in the assigned
rest source audit (Astra round 6) before the deficits are read either way.
E10 paired prediction differences (Astra round 6; `E10_paired_difference.json`, no refit): n = 1.845 minus
n = 1.5 on the same observations — source coordinate RMS difference: rest train 0.25 (max 0.53), rest validate
0.27 (0.53), mirrored train 0.21 (0.64), **mirrored validate 0.78 (max 4.29)**, upright train 0.08, upright
validate 0.12 (0.57); displayed-root RMS difference: rest train 0.49 (max 1.35), rest validate 0.29 (0.45),
mirrored train 0.37 (0.86), **mirrored validate 0.73 (max 3.4)**, upright train 0.14, upright validate 0.30
(1.89); no root matched by one gauge and not the other (3 train / 16 validate rest roots missing in both).
Against E10's ≤ 0.5 px withheld landmark criterion the gauges **pass the landmark-equivalence component on the rest
and upright branches** and fail it on the mirrored sheet branch (where the model itself fails); this is not complete
equivalence — pixel equivalence and the rest missing roots remain outstanding; the earlier "≤ 0.34 px" compared
RMS values, not paired predictions, and is withdrawn. Pixel-equivalence remains outstanding.
n = 1.5 versus 1.845: see the paired-difference record above (superseded: an earlier line here compared RMS values, not
paired predictions). Index: unresolved over the tested alternatives. Status: **M0 remains a candidate; the mirrored
sheet branch is its open failure; the rest right-end plateau is an estimator question to settle at the source**.
Reading: the shared-surface family reproduces both the resting arc and the sheet fold with one
normalized shape and role amplitudes (M0 beats EFF on rest with fewer parameters and matches it on the sheet;
bootstrap gate pending). Sheet validate RMS 2.75/2.98 is a combined figure that hid the branch split; see the E9 completion table: the
mirrored branch fails on its own (4.34 px) and the upright branch passes (0.24 px).

Held M1 (bounded, shared n/β/c/τ/γ frozen, one signed amplitude a_held ∈ [−3, τ − 0.02],
R per pose): fitted to the five bar-edge landmarks alone, a_held = −0.194 gives landmark RMS
1.07 px with no TIR and no sidewall hit — a concave edge reproduces the −12 px outward sign and
magnitude. Fitted jointly with the App Store seam curve (single pose, σ 3 px, d 3–40), a_held =
−0.071, seam RMS 9.2 px, 4 sidewall hits; the band's plateau-then-compression shape is not a
monotone concave edge. Per Astra's rule M1 does not pass the seam and cross-pose gates:
**compositor retained; held mechanism unresolved**; no M2.

## E6 — kernel / depth / drift on the toolbar stills — K0 and K1 tie; fold geometry on straight runs confirmed; drift not supported; absolute gates not met

Record: `E6_kernel.json`; `tools/e6.py`. ROI x 400–770 minus 560–620 and a fixed
appearance-difference glyph mask (dilated 3 px); rows with d ≥ 6 from both edges (rim excluded);
target periods from the target's own FFT: 8.00 and 32.60 px. Geometry hypothesis: the E5 arc map
s(d) applied on the straight top/bottom runs (identity beyond d 36), compared with identity.
Models K0 (positive mixture) and K1 (signed detail filter), q_W = map or identity, gain/offset
linear, Nelder–Mead from 3 starts. Dev: r0/r50 light appearance, r29/r50 dark appearance
(light target); holdouts tint 62 (both appearances) and IMG_6647 predicted with the tint-50
parameters of the same appearance.

| image | fold K0 | fold K1 | identity K0 | envelope 50 % pos (obs / pred) | notch drop (obs / pred) |
| :-- | --: | --: | --: | :-- | :-- |
| r0 light | 9.74 | 10.32 | 29.15 | 2341.47 / 2341.43 | 1.0 / 6.6 |
| r50 light | 4.54 | 4.54 | 10.42 | 2341.48 / 2341.43 | 0.8 / 8.0 |
| r29 dark | 8.82 | 8.76 | 17.17 | 2341.40 / 2341.43 | 39.3 / 9.9 |
| r50 dark | 6.31 | 6.27 | 10.99 | 2341.40 / 2341.43 | 41.7 / 11.4 |
| holdout r62 light | 3.76 | 3.76 | | −0.05 px | 0.7 / 6.0 |
| holdout r62 dark | 4.98 | 4.95 | | +0.03 px | 42.6 / 8.6 |
| holdout IMG_6647 | 6.31 | 6.27 | | +0.03 px | 41.7 / 11.4 |

(RMS in levels on the masked ROI.) Fitted K0 at tint 50: σF 0.85–0.86, σW 24 (light) / 40
(dark), w 0.38 (light) / 0.23 (dark); K1 σY 0.7–1.0, σFX 0.9, σAX 11–65, w 0.33–0.62.
- Kernel: K1 improves RMS by < 0.2 level everywhere → by the card's rule the anisotropy claim is
  not supported; K0 is the simpler equivalent. Neither passes the absolute gates (profile RMS
  ≤ 2), so the shipping baseline stays the current filter with the profile error documented.
- Geometry: the fold map on the straight runs beats identity by 2–3× in every image (t0 light:
  9.7 vs 29): straight runs carry the same fold (FINDINGS 18/20's "straight runs: nothing" is
  refuted for the toolbar). The envelope position is predicted within 0.05 px in all seven images.
- Drift: free δ0 gives −0.3…+1.8 px at tint 29/50 (both appearances) with ≤ 0.02 level
  improvement; 15 px (bound) at t0 light for 0.1 level. No 10–12 px source shift is supported.
- The residual is dominated by the dark appearance's tone gradient toward the bottom rim (row
  means fall ~40 levels over the last 25 rows in the dark appearance, ~1 level in the light):
  observed "notch drop" 39–43 vs predicted 9–17 comes from the rim/tone term, which this card
  excludes; the light appearance's notch (0.7–1.0) is hidden by the fold as predicted. Envelope
  width: light 2.5 px observed vs 2.4 predicted; dark 6.9–7.2 observed vs 2.4–2.5 predicted (not
  met). The photometric block (tone/rim, w(u)) is E9's.

## E7 — release and speed/shape on native frames — release INTERMEDIATE (314 ms), speed INSUFFICIENT

Records: `E7_traj.json` (1,193 native frames spanning CFR 1200–2600, each with PTS; bar
rows, lens outline, proudness, raised area; 0 time-ambiguous frames in the release window),
`E7_release_speed.json`; `tools/e7_traj.py`, `e7_analyze.py`. The lag subtest is not done.
- Release (CFR 1930–1988, 56 native frames, 16.67 ms spacing): raised area (zero = trailing
  rest 1968–1988, plateau 2,210 px² over 1934–1941) gives **T90–10 = 313.8 ms** (gate 334–400:
  missed by 20 ms). The fitted-silhouette proudness (13.46 px plateau) is lost below ≈ 5 px
  (CFR 1960) and cannot give its own T90–10; estimator discrepancy is therefore unbounded at the
  tail. Families on the area curve (normalized, error scaled to the 13.4 px plateau): critical
  spring ω 9.82 /s, v0 −0.83, RMS 0.63 px, max 1.10, no overshoot; first-order τ 167 ms, RMS
  0.87, max 1.88 (model T90–10 367 ms); monotone cubic RMS 0.33. The spring beats first-order by
  0.24 px (< 0.5): no mechanistic label. Branch: keep the current subside spring baseline, report
  the 313.8 ms raised-area duration as the measured quantity; the 367 ms figure is the fitted
  first-order model's T90–10, not a second (silhouette) measurement (corrected per Astra round 4).
- Speed/shape: the gesture selector found 2 gestures; every speed bin is populated by one
  gesture (bins n = 194, 262, 107, 55, 18, 4). Lens height minus bar growth 212.6 → 205.9 →
  202.7 px and proudness 12.4 → 9.3 → 7.7 px from the [0,60) to the [800,∞) bin — the same
  direction as the old table, from one gesture: **INSUFFICIENT**; the 5 % gel stays authored.

## E1 device leg (Pixel 7) — geometry PASS after the padding-origin repair; photometry over stripes FAIL; one case quarantined

Records: `E1_device_unit.json` (six 1:1 cells, final), `E1_device.json` (fill-width cells, superseded).
Screenshots `C\datasets\device-captures\inverse-v1\pixel7unit*_*.png`, JVM renders `C\datasets\device-captures\C1a-lib*.png` (01:19).

Host changes (library repo, uncommitted, to be committed separately per Astra): sample
`CalibrationActivity.kt` (harness boxes, `3*S/D` length adapter, `--ez unit true` 1:1 mode with
`--ei offsetY`; the 1170-px scene needs `wrapContentSize(unbounded)`); `LiquidGlass.kt`: the pad
is finalized once, before `sampleBounds`, the uniforms and the recording transforms, as a whole
number of layer pixels in the recorded space (`pad = ceil(pad * renderScale) / renderScale`), and
the sampled-field pad is rounded the same way. Astra's diagnosis of the interim state (shader pad
22.5 vs recording pad 23 → −0.5 px silhouette shift) is confirmed by the captures below.

Capture protocol: force stop, launch with extras, 7–8 s settle, two `exec-out screencap` shots.
QUARANTINED: the first `pixel7unit_t50_dark_light` record of the interim run (offset +15.19/−45.06,
peak 0.009, 23 % within 8 levels) was captured during the launch animation of the first start
after an install; every later capture of that cell registers at 0.00 px. Only settled captures
are kept. History of the tip/row agreement (Android − JVM, reference px): fractional pad:
rows −0.08…−0.18; late `ceil` (interim): rows −0.4…−0.65 (the shader/recording origin split);
final: rows 0.00…−0.06 on pill and toolbar in all six cells, tips −0.10…+0.03 (toolbar),
0.00…0.02 (pill), one outlier −3.2 on the pill's right tip in the light appearance over white
(both estimators; not understood, rim over white), and −0.43 on the toolbar left tip at tint 0.

- Registration at 1:1: offset 0.00 / −180.00, phase peak 1.00, uncovered textured pixels 99.4 %
  within 8 levels; uncovered stripes reproduce the target at modulation ratio 1.00.
- Photometry, neutral interior masks (gates mean ≤ 2, RMS ≤ 3, p95 ≤ 6): pill t50 dark 0.0/2.4/1,
  pill t50 light 0.5/2.1/1, pill t0 dark 0.2/**3.8**/1 (RMS gate FAIL), back button 0.3–0.4/
  1.6–1.8/1–2 (PASS); toolbar and bottom-left button over stripes: mean −6.3…+9.1, RMS
  10.5–13.3, p95 22–29 (FAIL). The earlier "blanket PASS" wording was wrong.
- Per-cell photometry after the origin repair (RMS levels, mean, p95; `E1_device_unit.json`): pill cells
  t50 dark/light 2.41 / 0.04 / 1, t50 light/light 2.15 / 0.46 / 1, t0 dark/light 3.76 / 0.22 / 1 (t0 pill above
  the 3-level gate); back button 1.63–1.81. Toolbar cells: t50 dark/light toolbar 11.72 (mean −6.33, p95 25),
  bottom-left button 10.47; t50 light/light 13.27 (mean +9.14, p95 26), button 10.62; t0 dark/light toolbar
  13.15 (p95 29), **bottom-left button 15.50 (p95 33)**. The earlier "10–13" summary understated the t0 cell.
- Stripe-modulation diagnostics (not sigma measurements): toolbar interior 8-px carrier
  Android/JVM 0.92–0.95, 32-px 0.95–0.99 (interim 0.89–0.94; fractional pad 0.75–0.80). The
  remaining deficit is a filtering question, to be investigated on the host separately; no
  shader or blur parameter is changed to compensate.
- Pixel-7 results close no S24+ gate; performance not measured.

## E0 Home Screen control (I0 f120, C4b) — geometry registered; fine texture not reproducible by a bilinear warp



Record: `E0_home.json`; `tools/e0_home.py`. Wallpaper = DARK target scaled s about the origin,

offset (ox, oy), dimmed: f120 (source 74, PTS 1210) s = 1.1375, offset (-0.02, -289.97), dim

0.992 (default icons); C4b dark-dark / light-dark: s = 1.1375, offset (-0.02, -289.97), dim

0.7725 (Clear icons). Geometry seeds confirmed to 0.03 px. Textured pixels within 4 levels only

0.53-0.60 (8 levels 0.67-0.71) because the phone's own resampling of the 4/8-px stripes is not

a bilinear warp; coarse features and landmarks register exactly (residual median 0-2 levels).

Branch: Home Screen scenes are landmark/qualitative holdouts; metric photometric fits on them

need the phone's resampling filter modelled first.



## E0 — bubble set (datasets/ios27-phone/bubble/JGEY2190.MP4, 2026-09-13) — PASS

Record: `E0_manifest.json` (set "bubble"), `scratch/JGEY2190_packets.csv`, `scratch/JGEY2190_cfrmap_empirical.npy`,
`scratch/JGEY2190_cfr_time_ambiguous.npy`. One file. hevc yuv420p10le, smpte2084 / bt2020 / bt2020nc (HDR PQ,
decoded through the zscale chain like every phone recording), 1170x2532, 5,779 packets, 102.04 s, sha256
337fccbf619139bdedbd3003568d81cdf6325c3a6befd64ef8d6e923da60f011. VFR: r_frame_rate 60000/1001, average 56.6 fps;
PTS steps (1/600 s ticks) 10 for 5,435 packets, 20 for 173, 30 for 67, 9–31 otherwise. Empirical chronological CFR-60
map: 6,116 CFR frames, 5,774 distinct sources matched, 12 CFR frames time-ambiguous (identical consecutive sources).
Decode check: every packet but the last decodes (`-vsync drop`; the passthrough variant silently dropped six mid-file
frames on this file, fixed in `decode.py`/`cfr_verify.py`); the final packet is never emitted by ffmpeg and is tolerated.
No registration target: the recording is over app pages, so E0 here is identity, encoding and timing only.

## E7b — bubble set (JGEY2190): catalogue, native-frame trajectories, descriptive tables — NO FIT

Records: `E7b_apps.json` (app per frame), `E7b_segments.json` (bar geometry per app, app periods), `E7b_traj.json`
(rim cue per frame), `E7b_glyph.json` (glyph cue per frame), `E7b_traj_merged.json` (one record per native frame:
present, x_centre, source, silhouette, bar rows, v5/v7), `E7b_bubble_descriptive.json`, `BUBBLE-CATALOGUE.md`;
scripts `tools/e7b_rim.py`, `e7b_glyph.py`, `e7b_merge.py`, `e7b_catalogue.py`, `e7b_cache.py`, `e7b_overlay.py`;
sheets `scratch/bubble_sheet_1s.png` (one full frame per second), `scratch/bubble_overlay_{Phone,WhatsApp,AppStore}.png`
(detections drawn on sampled gesture frames).
- Content (bar-signature classifier + contact sheet): Home 0–2.3 s; **Phone** 2.3–46.9 s (3 tabs Calls 218 / Contacts
  480 / Keypad 742 plus a separate search circle; pages: black keypad, dark Recents and Contacts lists with white text
  rows right above and below the bar; an app-switch swipe 13.5–15.3 s is excluded); switch 46.9–49.8; **WhatsApp**
  49.8–67.1 s (5 tabs 193/370/585/800/977; busy lists, avatars, coloured text); Messages/Home 67.3–72.1 (no tab bar);
  **App Store** 72.1–102.0 s (5 tabs 201/393/585/777/969; Today cards with artwork, Search/Games/Apps/Arcade lists). Bar
  rows are the same in all three apps, 2282.5 / 2468.0 (x 65–900 Phone, 65–1105 the others); an earlier Phone value of
  2264.5 was an avatar edge and was corrected before the merged records were built.
- What the bubble is: a transparent lens with a thin chromatic rim, protruding above and below the bar and magnifying
  the glyphs under it; the resting indicator is a darker pill. Two cues were needed. Rim cue: saturation top-hat
  (9×9) > 25 minus a 6-px dilation of the blue selected glyph; a ring is accepted only when it has rim near both bar
  edges, spans the bar (top ≤ 2294.5, bottom ≥ 2456) and is ≥ 120 px wide. Glyph cue: per tab, NCC of the high-passed
  glyph patch (±90 px, bar rows +15/−15, half resolution) against templates from the app's own static frames; a tab
  is covered when NCC < 0.6 (rest frames 0.94–1.0, lens over a tab 0.0–0.5). Merged: present = any covered tab;
  the ring is kept only when it overlaps a covered tab; velocities only from rim-tracked frames (the glyph centre is
  tab-quantised). Coverage: 1,782 of 5,778 frames present; 524 with a full silhouette (Phone 8 gestures with ≥ 10
  rim frames, WhatsApp 1, App Store 1); 739 rings rejected as page/glyph contamination. Busy pages defeat the rim cue
  (saturated page edges), so **shape is essentially Phone-only**; presence and tab-level timing hold everywhere.
- Gestures (present-runs ≥ 4 frames, split at ≥ 6 absent frames): 72 — Phone 33 (22 hops ≤ 300 px, 11 of 300–600),
  WhatsApp 14 (4 / 8 / 2 full passes > 600), App Store 25 (2 / 12 / 11 full Today↔Search passes of 768 px). Duration
  median 0.32 s in every app (Phone 0.12–2.75, WhatsApp 0.27–0.83, App Store 0.25–0.42). Net mean speed median:
  Phone 924, WhatsApp 1,001, App Store 1,819 px/s (max 2,425). Stops by the plan's rule (|v| < 60 for 6 frames after
  > 300): 1 (Phone gesture 14, src 1219); reversals: Phone 16, App Store 1, WhatsApp 0 (rim frames only). Landing tab
  from the blue selected glyph: known for 31/33 Phone and 25/25 App Store gestures, none in WhatsApp (no blue glyph;
  the darker-pill reading was unreliable and is not used). Release on the start tab: Phone 6, App Store 2.
- Speed bins over rim frames (n frames / n gestures; medians width, height, proud_top px): [0,60) 16/6: 229, 213.5,
  25.5; [60,150) 29/9: 218, 207, 10.5; [150,300) 46/16: 227.5, 194.5, 7.5; [300,500) 28/12: 222, 210, 13; [500,800)
  28/13: 230, 204, 10; ≥ 800 96/13: 250.5, 213.5, 18. No monotone trend; the bins mix gestures and pages.
- Shape vs travelled distance, gesture 14 (Phone, Recents page, src 1170–1333, 2.75 s, 133 rim frames, the one slow
  drag with a stop): width 133–334, height up to 235, proud_top up to 39.5 px. By speed inside this one gesture:
  |v| < 60: width 305, proud 26.5 (n 9); 60–150: 290, 29.5; 150–300: 222, 25.5; 300–500: 281.5, 21.5; 500–800: 272,
  24.5; ≥ 800: 248, 28.5 (n 47). By travelled path (50-px bins, medians): 0: 302.5 / 10.5; 100: 238.5 / 9; 150: 160 /
  2.5; 350: 274.5 / 10.5; 400: 310 / 18.5; 600: 177 / 16.5; 900: 289 / −1.5 (n 1–4 per bin). Across the rim gestures,
  proud_top_max is 10.5–39.5 px in Phone (net travel 4–262 px), 16 in WhatsApp (196 px), 49.5 in App Store gesture 60
  (327 px, 10 rim frames): no relation to net travel is visible at this coverage.
- Repairs per Astra round 4 (catalogue rebuilt, `tools/patch_catalogue_r4.py`): L(t) now accumulates only over
  consecutive rim-tracked frames (never bridged or substituted) and N(t) = |x − x0| on the rim track; gesture 14's
  earlier 4,993 px path was mixed-cue accumulation and is withdrawn — its rim-only L is 2,672 px over 133 rim frames
  with 7 track gaps (still not finger travel). Protrusion and the primary outcome, **height excess over the
  same-frame bar** (bubble height − bar height measured outside the bubble in that frame), replace the fixed-edge
  proud_top: gesture 14 start/end 13.5 → 16.5 px, gestures 9–13 start 2–12 → end 7–19 px. `shape_end` is labelled
  descriptive, not a terminal shape. Arrival shape (first rim frame within 5 px of the landing tab with two valid
  preceding rim frames) is **unavailable for every gesture except 7** (src ≈ arrival, height excess −18, −18, −18:
  already inside the bar, i.e. in the release transition) — the rim detector loses the ring before arrival, which
  is the case for the annotation pilot Astra ordered. Adopted split recorded in `E7b_bubble_descriptive.json`
  (`split_adopted`).
- Release: not measurable here. The raised area from the rim cue is intermittent (the ring drops in and out), so
  the T90–10 values the script prints (8–40 ms, one negative) are artefacts and are not reported; the silhouette is
  lost as soon as the ring sinks into the bar. H4 (LOLL8185) remains the only release record.
- Claim to be tested (owner's observation): **the bubble's final shape depends on the travelled distance, not only on
  instantaneous speed.** What bears on it so far: (a) the End-of-travel shape of the full App Store passes, the
  cleanest distance contrast (768 px vs 384 px hops), has no silhouette at all; (b) inside gesture 14 the slow frames
  are wider (305) than the fast frames (248) and the widest bins are not the farthest; (c) proud_top_max does not
  order by net travel. None of this is a test; the finger position and the page under the bar are confounded with
  both distance and speed in every gesture. **Not fitted, not decided.**

## Pre-declared split for the bubble set — proposal, not applied (awaiting Astra)

By app and gesture id (BUBBLE-CATALOGUE.md). Roles: train = Phone slow/held gestures 7, 9, 10, 11, 12, 13, 14 (the
only ones with a continuous silhouette; Recents page, text landmarks under the bar); validate = Phone hop/pass
gestures with rim frames 0, 16, 24, 25, 26, 27, 31 and WhatsApp 35; locked = all 25 App Store gestures (the full
768-px passes are the distance contrast the claim needs) and the remaining 13 WhatsApp gestures. Speed-bin coverage
with ≥ 2 independent gestures per bin holds over rim frames (6–16 gestures per bin) but only inside Phone; the
locked App Store passes populate the ≥ 800 px/s bin only at tab level. Markings: E7 lag candidates (non-periodic
landmarks under the bar) = every WhatsApp and App Store gesture and the Phone Recents-page gestures 7, 9–14; E3 held
poses = gesture 14's stop (src 1219) and the near-static gestures 10, 11, 12 (net ≤ 50 px, bubble held over one tab);
landmark-only = all (no calibration target in the recording). Shape on busy pages needs either a detector Astra
accepts (proposal: track the magnified glyph outline instead of the rim) or manual annotation of a few frames; until
then the locked sets carry timing and tab data only.

## E9 rest source-registration audit (Astra rounds 6/7) — discrepancy survives; correspondence ambiguity recorded

Record `E9_rest_audit.json`, script `tools/e9_rest_audit.py` (E5's estimator unchanged; nothing adjusted). Images
IMG_6646, IMG_6647, C1a-r50-light-dark, C1a-r62-light-dark; both toolbar ends; d 16/18/20/22 with 12/24 controls.
- Target registration independent of the glass: phase correlation over rows 400–2200 gives dx, dy = 0.000 /
  −0.001 px for all four (the E0 affine records: scale 1.0000, shift < 0.001 px). Not the cause.
- Ring centres: each image's own toolbar tips (E1 coverage box fit) are 341.58–341.61 / 828.38–828.39, equal to the
  adopted 341.6 / 828.4 within 0.02 px, so per-image centres reproduce the adopted values exactly. The two ends
  respond oppositely to a common x shift of the centres: +0.5 px moves the right end's s by +0.5…+0.75 and the left
  end's by −0.75; −0.5 px the reverse. A common tip error of ≈ 0.6 px would therefore remove the 1.25 px end
  asymmetry; the tips' stated uncertainty is 0.15 px and their cross-image agreement (0.02 px) cannot exclude a
  common systematic error. This is the one open ambiguity.
- Stripe identity / interpolation / alternative peaks: the direct crossing estimate (one-to-one arc RMS with
  unmatched counts penalised) agrees with the ring NCC within 0.25 px at every depth on both ends (right end
  27.5 / 27.25 / 27.25 / 27.25 with arc RMS 1.5–1.8 px, 5–6 matched, 0 unmatched; left end 28.75 / 28.25 / 28.5 /
  28.75, arc RMS 0.2–0.7); the NCC curve has a single maximum ≥ 0.8 of the best at d 16–18 and two at d 20–24 on
  the right end (width at −0.05: 0.25 px right, 0.75–1.5 left); the E0-verified stripe identities hold.
- Synthetic estimator check (ring luma synthesised from the target at a known s_true with the observed contrast
  gain 0.12–0.17, offset 81, blur σ 1.5 samples, noise 6 levels): the NCC estimator is unbiased to ≤ 0.25 px at d
  16–24 on both ends for the M0 curve truth (27.7–28.3) AND for a flat plateau truth (27.0 over d 16–22): it
  resolves the two. (d 12 on the right end aliases to s = 4 in the synthetic ring only — a synthetic artefact at
  the stripe-region boundary, not seen on the real images.) The direct crossing estimator is biased on synthetic
  rings (−4…−6 px) and is not used as the primary estimate.
- Reading: registration, stripe identity and estimator bias are excluded. The right-end plateau (27.0–27.25 at
  d 18–22) sits 0.5–0.75 px below the model minimum with an estimator uncertainty of 0.25 px → **retained as a
  model residual**; the left end (28.25–28.75) sits above the minimum; the only mechanism that explains both ends
  together is a common ≈ 0.6 px x error of the end centres, which the record can neither confirm nor exclude →
  **correspondence ambiguous; both ends' observations kept with that ambiguity; the rest branch is not promoted to
  PASS; M0 unchanged.**

## E9 S4 sheet audit (Astra round 7) — the mirrored-branch failure is a state/edge-identity assignment, not registration

Record `E9_S4_audit.json`; observations from `E9_completion.json` (exact frame records), no refit. Per pull, mirrored
displayed-landmark RMS before the audit: S1 1.80, S2 3.90, S3 2.03, S4 6.01 (upright 0.44 / 0.30 / 0.43 / 0.88).
Tabulated by edge row: at matched rows S4 agrees with S1–S3 within 0.2–0.5 px (e 456: S4 d 18.5 vs S1/S3 18.6;
e 531: S4 17.0 vs S2 17.0; e 534: 13.5 vs 13.5). The large residuals sit at three places, all E2-seed
observations: (a) rows 464–468 (s 74.5–78.5), where the light-target pulls S2 (frames 7343–7347) and S4
(13433–13436) localise a mirrored copy at d 13.8–16.9 with 72–88 columns while the dark-target pulls S1/S3 at the
same rows find no copy (n 0) or one at d 2.5–3.4 (n ≈ 20): residuals −5.9…−8.5 in both S2 and S4 — an edge-identity
difference between target polarities at the onset rows (Astra round 1 named 11038/13423/13474 as onset frames with
incompatible edge identities); (b) S4 frame 13423 (e 456, d 21.9, −3.5) duplicating 13424 (d 18.5, −0.1) at the same
row; (c) S4 frame 13473 (e 527, d 5.8, +18.4), whose E2 weight record is degenerate. Registration is not
implicated (E0 per-frame registration exact; the same rows agree across pulls elsewhere). 187 sheet observations
collapse to 123 unique (E2's ±2 neighbourhoods repeat identical values); 9 are flagged by the state/identity
rule above (not by residual). Rescoring the unchanged M0 vectors on the unique settled-state observations:

| branch / split | n | landmark RMS (≤ 2) | p95 (≤ 4) | max |bin bias| 5-px bins (≤ 1.5) | per pull RMS |
| :-- | --: | --: | --: | :-- | :-- |
| mirrored train (n 1.5 / 1.845) | 18 | 1.34 / 1.40 | 2.59 / 3.16 | 0.67 / 1.21 | S1 1.75, S2 0.45 |
| **mirrored validate** | 24 | **1.73 / 1.80** | **2.71 / 3.26** | 5.1 / 5.3 (the d 0–5 bin: two S3 copies at d 2.5–3.4, e 466–467); other bins ≤ 0.9 | S3 2.08, S4 0.88 / 0.99 |
| upright train | 29 | 0.37 / 0.34 | 0.71 / 0.73 | 0.36 / 0.17 | S1 0.42, S2 0.27 |
| upright validate | 43 | 0.63 / 0.49 | 1.02 / 0.84 | 2.02 / 0.13 (n 1.5: the d 45 bin, one observation) | S3 0.42, S4 0.90 / 0.66 |

Reading: with the onset/edge-identity observations assigned to their own state, S4's mirrored branch scores 0.88
px (from 6.01) and the mirrored branch as a whole passes the RMS and p95 gates on validation (1.73 / 2.71); the
signed-bias gate fails only in the d 0–5 bin, populated by two S3 observations at the hidden-zone edge (residual
+5.1), and, for n = 1.5 only, in one single-observation upright bin. **M0 unchanged, not promoted**: the
mirrored branch now fails one sparse-bin bias gate rather than the RMS gate; whether those two low-d copies are
settled-state observations or the same onset/hidden-zone identity question is for the ruling; the conditional
M3 → EFF branch was not started.

## E6 frozen photometry (Astra rounds 4/5): M0 geometry frozen, K0, appearance law frozen — absolute gates FAIL

Record `E6_frozen.json`, script `tools/e6_frozen.py`. Geometry: the M0 rest curve (E9) on both toolbar runs, identity
beyond d 36, monochrome, rim rows d < 6 excluded. K0 kernel (sF, sW, w) fitted jointly per appearance on the training
stills with one gain/offset per training image; the tint-62 holdouts and IMG_6647 take the kernel and the tint-50
gain/offset of their appearance **frozen** (no tone law for tint exists; the tint step is part of the prediction error).
Light law: sF 0.85, sW 16.4, w 0.73; dark law: sF 0.86, sW 41.0, w 0.31.

| image | role | clean-row RMS (gate ≤ 2) | notch obs / pred (gate ± 3) | envelope 50 % err px (≤ 1.5) | width obs / pred px (≤ 1) | amp8 ratio |
| :-- | :-- | --: | :-- | --: | :-- | --: |
| C1a-r0-light-light | train | 6.47 | 1.0 / 7.9 | 0.04 | 2.5 / 2.4 | — |
| C1a-r50-light-light | train | 4.84 | 0.8 / 2.9 | 0.05 | – / 2.4 | — |
| C1a-r29-light-dark | train | 7.56 | 39.3 / 11.6 | 0.03 | 6.9 / 2.4 | — |
| C1a-r50-light-dark | train | 6.34 | 41.7 / 7.8 | 0.03 | 7.2 / 2.4 | — |
| C1a-r62-light-light | holdout, frozen | 6.44 | 0.7 / 2.9 | −0.05 | – / 2.4 | 1.45 |
| C1a-r62-light-dark | holdout, frozen | 11.11 | 42.6 / 7.8 | 0.02 | 6.9 / 2.4 | 1.34 |
| IMG_6647 | holdout, frozen | 6.34 | 41.7 / 7.8 | 0.03 | 7.3 / 2.4 | 0.94 |

Reading: with the geometry frozen and no per-holdout refit, K0 meets only the envelope-position gate. The row RMS
is 5–11 levels on training and holdouts alike; the notch is **absent in the light appearance (0.7–1.0 levels) and
~40 levels in the dark appearance**, and a blurred/warped source under K0 predicts 3–12 in both — the dark notch is
not explained by the tested frozen M0/K0 plus affine appearance model and is assigned to E9's photometric
block (tone stage) per round 6; the measured dark envelope
width (~7 px) is three times the rendered one. Per the ruling: **K0 does not replace the shipping filter; the
baseline is retained**; K1 is not reconsidered (no reproducible advantage was shown). The conditional per-holdout
refit (the earlier E6 numbers) gives 5.2 / 6.2 / 7.0 RMS and is recorded as a diagnostic only.

## E8 pass 1 — raw rim profiles versus the plan's reference numbers (measurement only) — reference VERIFIED over black

Record `E8_profiles.json`, script `tools/e8_profiles.py`. Adopted pill outline (E1), inward normals, depths −2…16 at
0.5 px, cap angles 0…90° from the top point toward the tip, upper and lower halves separately; the straight runs
reported per source side because the pill straddles the target's black (x < 585) / white (x > 585) halves. Values
are luma minus the profile's own interior (median at depths 12–16); no glyph mask (the appearance-difference mask
is invalid on the pill, whose fill changes with appearance; column medians are used instead).
- **Over black, top run**: every image (light r0/r31/r50/r62, dark r29/r50/r62, IMG_6644–6647) gives peak +60.2…+64.0
  at depth 0.5, +9.7…+10.5 at 4, +6.0…+6.5 at 6, +3.5…+4.4 at 8, half-width 2.0 px — the plan's +60 / +10 / +6 / +3 is
  verified within 5 levels and 1 px, and the profile is **independent of appearance and tint**. Bottom run over
  black is brighter: peak +74…+84 (light), +92…+104 (dark), same +12…+13 / +7.5…+9 / +5…+6 tail.
- **Over white**: light appearance peak +3…+5 (top), +1…+13.5 (bottom) — the additive line nearly vanishes on a
  white source; dark appearance shows +73…+91 at depth −2 (that is the undimmed source outside the outline, not a
  rim) with the same +10.5 / +6.5 / +4 tail at 4/6/8 as over black.
- Cap re-registration (E8 card, "re-register, do not fix it blindly"): the rim line's peak at 5° steps fits a
  circle of centre (409.1–409.3, 206.5–206.7), R 64.3–64.7 on every image versus the adopted 410.6 / 206.5 / 66.0
  (1.4–1.7 px smaller radius, 1.3–1.7 px left); the right cap over white has no visible rim and keeps the adopted
  circle. Cap values below are on the re-registered circle (0° upper reads 45–56 there, versus 61–63 on the adopted
  top point, i.e. the adopted cap started 1 px outside the line).
- **Caps over black** (left cap), upper / lower half: 0° 61–63 / 72–95; 15° 32–37 / 41–69; 30° 28–30 / 37–54;
  45° 16–18 / 22–35; 60° 10–11 / 13–21; 75° 1–3 / 0–2; 90° 0 / 0. The tip is negligible as predicted; the plan's
  "+8 near 45°" is not: 45° reads 16–18 (upper) and 22–35 (lower), and the lower half (toward screen-bottom) is
  brighter at every angle — a top/bottom asymmetry consistent with the brighter bottom run. Caps over white: light
  2–10; dark 71–93 at all angles (the interior dimming step, not a rim).
- **Held over black (LOLL8185)**: moving H6 (CFR 1376–1384, 135 profiles) peak luma 65.3 at depth 2.0, max R−B 18.6
  (plan 64 / 16: within 5 levels); settled H2 (CFR 1738–1742) peak 85.8 at depth 1.5, R−B 42.0 — the two states
  differ, as Astra's 64/16 vs 85.5/48 note says; side-interior profiles not measured in this pass.
- Reading for E8 pass 2 (not run): a source-independent additive line (+62 top / +76–104 bottom over black, tail
  +10/+6/+4) with a smooth angular fall-off from the top point to zero at the tip and a top/bottom asymmetry is the
  shape a two-lobe environment reflected off the M0 normals must reproduce in position, width and asymmetry; the
  bounded environment fit with h, n, g frozen from both retained gauges is the next step.

## E8 pass 2 — two-lobe environment on the frozen M0 normals versus an authored rim (same 7 degrees of freedom)

Record `E8_fit.json`, script `tools/e8_fit.py`. Data: the pass-1 excess profiles over black (left cap 0…90°, upper
and lower, and the top/bottom runs), depths 0.5–10. Train on P1 light (r0, r50 averaged); validate on the dark
twins (r29, r50) with only the single appearance scale S fitted; locked P2 (r31/r62 light, r62 dark) reported with
everything frozen. Physical model: h(d) and the slope from each retained gauge (rest role, R 66), Fresnel at that
n, E(r) = E0 + A_top e^{k(r·l_top−1)} + A_bot e^{k(r·l_bot−1)}, η ∈ [15°, 85°], k ∈ [1, 100], roughness absorbed into k.
Authored comparator: (a0 + a1 c + a2 c²)·b^{[bottom]}·e^{−(d−0.5)/λ}, c = cos θ.

| model | train RMS | dark appearance-calibration RMS (≤ 5; the dark scale is learned here) | locked light / dark RMS (frozen: light scale 1, dark scale from the calibration set) | peak depth pred / obs (≤ 1 px) | angular half-width pred / obs (≤ 10°) |
| :-- | --: | --: | :-- | :-- | :-- |
| physical, n = 1.845 normals | 11.96 | 14.70 | 11.58 / 13.82 | 0.5 / 0.5 | none / 30–34° (flat 7.7-level prediction) |
| physical, n = 1.5 normals | 4.45 | **5.95** | 4.32 / 5.43 | 0.5 (boundary-limited) / 0.5 | 20.7° / 30.3–34.5° (A_bot at the 5000 bound, k 56, η 34°) |
| authored, 5 + 1 dark scale | 3.97 | **4.73** | 3.96 / 4.35 | 0.5 (boundary-limited) / 0.5 | 36.8° / 30.3–34.5° |

Frozen-scoring correction (Astra round 6): the first version fitted a scale on every scored set, locked sets
included; the table now uses the light scale 1 (training) and one dark scale learned on the dark
appearance-calibration set (r29/r50 dark; authored 1.239, physical-1.5 1.235, physical-1.845 1.54) and frozen.
Per image, authored: r29 dark 4.94, r50 dark 4.57, r31 light 3.97, r62 light 3.99, r62 dark 4.35; physical 1.5:
6.19 / 5.76 / 4.45 / 4.22 / 5.43. Parameter count: five shape/environment parameters plus one dark scale before
locked scoring. Limits carried: the physical expression is fitted to display-level excess (its failure rejects
that surrogate, not every physical reflection explanation); the 64.3–64.7 px cap radius is a rim-locus
registration, not a material boundary (E1's outline stands); the depth search starts at 0.5 px, so the 0.5
maxima are boundary-limited, not located peaks, and the full profiles are kept in `E8_profiles.json`. The
authored comparator has negative amplitudes at some angles: it is a signed rim/contour correction, not
nonnegative reflected light.

Reading: the reflection test does discriminate the gauges' normals where transmission could not — the 1.845
solution's steep, thin profile (τ 0.32, a 0.30) cannot produce the depth fall-off at all, the 1.5 solution's gentle
slab (τ 3.0, a 0.03) reproduces the depth profile but its lobe is too narrow in angle (21° vs 30–34°) and its
amplitudes run to the bounds. **Neither physical gauge passes the E8 gates; the authored rim with the same degrees
of freedom passes all three on the withheld dark twins and on the locked sets.** Per the card: the physical
coupling is not FAILed outright (RMS < 8, angle error < 20°) but does not pass, so the rim stays an **authored,
separately measured term**; rim evidence does not select n. Not yet measured: the side-interior profile of the
held lens (plan 47 → 17) and the upper-cap darkening at 60–75° (−0.5 to −15.7 levels: a dark contour toward the
tip, to keep separate from the bright line).

## Astra round 4 — decisions received (2026-09-13); status changes and work in progress

Full text: `ASTRA-RULING-round4.md`. Status after the ruling: M0 and K0 are **working candidates, not validated for
shipping**; held compositor retained; index "unresolved over the tested alternatives" (n = 1.5 provisional gauge,
1.845 sensitivity case); release measured quantity = raised-area T90–10 313.8 ms with the 334–400 ms interval retired
for the area observable; Android filtering deficit blocks material fitting from Android output only; two commits
authorized after checks (no push/tag); bubble split adopted with Phone 7 moved to validation; annotation pilot
before any detector; E2c card, V-map falsification test, bounded E3 source-map audit and a capture packet
authorized (targets do not sit behind Phone/App Store content: the held speckle test is unavailable).

Done since the ruling:
- Padding contract: one shared `GlassStyle.recordPad(radii, size, fuse, density, renderScale)` now feeds the
  sampled field at composition and the recording/uniforms/draw-back in the draw (`LiquidGlass.kt`); the field was
  built at ceil(padPx) while the layer used ceil(pad·rs)/rs (23 vs 24 px for 22.5 at half scale, and no held-band
  or fusion term). New `GlassPadContractTest` (whole layer pixels at rs 1/0.75/0.5/0.25 for Regular/Thick/Held,
  held margin ≥ 0.3 band, the 22.5 → 24 example); targeted run PASS (GlassPadContractTest, GlassGeometryTest,
  GlassShaderTest); full jvmTest run and the fresh Pixel comparison pending before the commit.
- E9 missing roots classified (above); 367 ms attribution corrected in E7; per-cell Android photometry recorded.
- Full JVM suite after the padding change: 46 tests, 0 failures, 0 errors (`scratch/jvmtest_full.log`). The fresh
  Pixel 7 comparison was blocked at the time (Pixel 7 off adb; only the Huawei JKM-LX1 and the Pixel 10 Pro XL
  emulator are attached); neither commit is made.
- Bubble annotation pilot (`E7b_annotation_pilot.json`; `scratch/annot14_zoom_*.png`, `annot_zoom_890.png`,
  `annot_zoom_2970.png`): Phone 14 native 1209/1218/1227/1235 read by eye on 6× nearest-neighbour crops with 1-px
  rulers, blind to the detector box. Boundaries are **visible** on the Phone Recents page: top rim 2255 / 2255 /
  2254.5 / 2250, bottom rim 2489 / 2488 / 2480 / 2480, left 482 / 453 / 451 / 430, right 811 / 788 / 783 / 759
  (height 234 / 233 / 225.5 / 230, width 329 / 335 / 332 / 329). Precision about 1 px on the top/bottom rim rows and
  1–2 px on the side columns (a 2× reading of 1209 differed by 1 / 1 / 3 / 2 px); one annotator with memory, so the
  shuffled repeat is not an independent test. The bar-bottom convention was undecided at the time (settled in round 5: outside-to-inside material boundary, highlight recorded separately): the inner highlight line
  sits at 2460 in every frame while the luma-gradient edge used so far is 2467.5. Detector versus manual: top within
  0–4 px, bottom off by 8–29 px (it takes the bar's highlight line at 1209), sides narrower by 16–37 px — the rim
  detector's height and width are **not fit for the 1-px height outcome**; manual or a detector trained on these
  annotations is required. Feasibility: Phone 10 frame 890 (black keypad page) boundaries visible (top ≈ 2260, bottom
  ≈ 2490); WhatsApp 35 frame 2970 boundaries visible where the page near the bar is dark (left ≈ 187, right ≈ 470,
  bottom ≈ 2480); busy coloured content is expected to hide the side rims. Half-day cap respected; no detector
  iteration run yet.
- Bounded E3 source-map audit (`E3_seam_dmin6.json`, `E3_seam_dmin8.json`; `E3_DMIN` in `e3_seam.py`): refitting the
  same two permitted models on pixels with d < 6 excluded removes the "s = 0 near d = 9" feature — the continuous
  map becomes flat at s ≈ 12 for d 6–24 (the band shows one source row, 12 px above the lens top) — and leaves both
  models failing: C_cont test RMS all/seam 8.39/6.63 → 7.56/6.25 (d ≥ 6) → 7.16/6.17 (d ≥ 8); C_switch 8.57/6.71 →
  7.79/6.37 → 7.42/6.30 (d* 30, w 4 unchanged); per-depth test RMS in the band stays 8–15 levels; the d ≥ 8 fit's
  knot at d 17 runs to the bound (−45), i.e. the band is not reproduced by any one-source map. The rim did drive the
  s = 0 feature; excluding it does not change the E3/E4 branch: **compositor retained, M1 evidentiary status
  unchanged**. Cross-pose (1260/1380/1740) needs a Phone-bar source model and is not done.
- Calibration targets generated (files only, nothing shot): `D:/Work/AndroidStudioProjects/SideProjects/kmp/liquidglass/research/datasets/calibration-targets/targets/r4/` — e2c_LL/HL/LH/HH
  (regions A rows 300–1000, D rows 1300–2000 at 64/192 on 128), speckle_dev (seed 20260913) and speckle_lock (seed
  9137152; not to be scored before selections and predictions are frozen), splitedge_rb, all 1170×2532 with 8-px
  checker registration strips and corner crosses; sha256 in `manifest.json`.
- Not started: M0/K0 photometry with E8 (item 2/4), E9 completion (p95, branch residuals, provenance restoration, n
  = 1.5 gauge comparison), E2c and speckle captures (need the phone), loop/direct recordings (deferred by ruling).

## Astra round 5 — decisions received; work done since

Full text: `ASTRA-RULING-round5.md`. Done in this pass (all offline, no commit, no device):
- **E9 completed** (section above): provenance restored, branch-specific residuals and p95, missing roots kept
  in the denominator and traced to one scene's right end, n = 1.5 scored on the same withheld observables, branch
  ownership unchanged. Open failure of M0: the sheet's mirrored branch (validate 4.3 px). M0 remains a candidate.
- **Frozen M0/K0 photometry** (`E6_frozen.json`): fails the absolute gates on training and holdouts; the dark
  appearance's ~40-level notch is not a kernel/geometry effect; the shipping filter stays the baseline.
- **E8 pass 1 and 2** (`E8_profiles.json`, `E8_fit.json`): the plan's rim reference is verified over black,
  independent of appearance and tint; the two-lobe environment on the frozen normals does not pass (the 1.845
  normals fail outright, the 1.5 normals give the depth profile but a lobe too narrow in angle, 21° vs 30–34°),
  the authored rim with the same 7 degrees of freedom passes all gates on withheld and locked sets: the rim
  stays an authored, separately measured term; rim evidence does not select n.
- **Padding contract, consumer level** (`GlassFieldPadConsumerTest`, JVM harness extended with a field child and
  the fusion uniforms): the sampled field, uPad and the layer size are exercised together at full and half scale,
  for closed-form, path-backed, held and fused geometry; a field built one pixel off the pad moves the silhouette
  (sentinel). The check exposed a second defect in the same family: with the field stored at half resolution, an
  **odd whole-pixel pad put the outline on a half texel and the boolean mask rounded it — the sampled silhouette
  sat 1.0 px (top) / 0.77 px (left) off the closed form for pads 23 and 25 and 0.00 for 22 and 24**. Fixed in
  `GlassPathField.buildPathField` by rasterising the mask at layer resolution and averaging the distance down to
  texels (same encoding, same range); after the fix every pad 22–25 is within 0.75 px. Full JVM suite: 51 tests,
  0 failures. What the JVM cannot exercise: the Compose recording and draw-back translate (Android shader path
  only); those share `recordPad` and wait for the Pixel comparison. Neither commit is cleared.
- **Bubble pilot corrections**: boundary convention adopted (outside-to-inside material boundary; highlight
  recorded separately); the Phone 10 / WhatsApp 35 readings recorded as feasibility readings, not annotations;
  the four Phone-14 readings flagged as rim-line (highlight) readings to be re-read under the convention; blind
  repeat still not done; 2,672 px labelled a repaired detector estimate; no distance law.
- E3 audit closed with its negative result (no further seam fitting).

- Fresh Pixel 7 comparison and the two commits (Pixel reconnected): fresh sample build with the library compiled
  in, install, tools/capture_pixel.sh (force stop, launch extras, 8 s settle, two shots per cell). Six 1:1 cells
  (E1_device_unit.json; the earlier record kept as E1_device_unit_pre_round5.json, screenshots in
  datasets/device-captures/inverse-v1/pre-round5/): registration 0.00 px, phase peak 1.0, 99.4 % of uncovered pixels within 8
  levels, rows Android minus JVM 0.00 to 0.07 px on pill and toolbar, tips within 0.22 px, photometry per cell
  identical to the corrected-origin record to the hundredth (pill 2.15 to 3.76, toolbar 11.7 to 13.3, bottom-left
  button 10.5 to 15.5, 8-px modulation 0.92 to 0.95): no regression from the recordPad/field change (the
  calibration scene has no path-backed shape, so the field fix is exercised on the JVM only). Default scaled
  cells (E1_device.json): the scene draws, but the 0.923 resample aliases the stripes (registration peak 0.56,
  42 % within 8 levels, outline landmarks scattering plus or minus 15 px on the toolbar row): not a measurement
  mode, recorded as such. Commits on research/ios27-measurements, no push, no tag: 28e2e82 (sample: calibration
  scene with 1:1 placement and the JVM harness boxes); 8890627 (fix(glass): one pad for the recording, the
  shader, the field and the draw-back).

Not done / blocked: E2c and speckle captures (need the
phone and the frozen predictions written first); the held lens's side-interior E8 profile; the ≤ 1 px height
uncertainty gate for the bubble annotations (needs a blind second reading).

## Astra round 6 — decisions received; work done since

Full text: `ASTRA-RULING-round6.md`. Done:
- **E8 frozen scoring** (table corrected above): light scale 1, one dark scale from the dark appearance-calibration
  set, nothing fitted on locked sets; per-image results in `E8_fit.json`. Conclusions unchanged: the authored
  signed rim/contour term meets the profile-RMS and angular-width gates (locked 3.96 / 4.35) in the black-backdrop pill domain,
  neither physical gauge passes.
- **E10 paired prediction differences** (`E10_paired_difference.json`, recorded in the E9 section): gauges equivalent
  within 0.5 px on the rest and upright branches, not on the mirrored branch (0.73 px root RMS difference, max 3.4).
- **Commit messages corrected** (message-only, same trees): 9443a9f (calibration scene), 4dcce72 (padding/field);
  0.07 px qualified as the selected top-edge row result, the −0.48 px pill bottom row and the flagged-invalid light
  pill coverage boxes stated, the path-field repair stated as JVM-verified only.
- **Blind bubble pilot** (`E7b_annotation_pilot_r6.json`; crops `scratch/pilot_r6_*`): fresh reading under the
  outside-to-inside convention, then a shuffled blind repeat at the same 6× magnification, on Phone 14 (1209, 1213,
  1217, 1221, 1225, 1229, 1233, 1235), Phone 10 (875, 890, 905) and WhatsApp 35 (2965, 2978). Readable on 10
  frames; 875 and 2965 unavailable (the crop centred on the glyph-cue centre missed the lens), 905 boundary-limited
  (lower bound), 2978 low confidence. H = (lens bottom − lens top) − (bar bottom − bar top): conv / blind = 36.5 /
  37.5, 37.0 / 37.0, 28.5 / 29.0, 29.0 / 29.5, 25.5 / 25.8, 25.3 / 29.0, 28.5 / 28.8, 28.8 / 29.0 (Phone 14),
  43.5 / 43.0, 43.5 / 44.0 (Phone 10). |ΔH| mean 0.75 px, max 3.7 px, SD 1.14; per-edge max differences 0.7 (lens
  top), 1.5 (lens bottom), 4.0 (bar top), 0.5 (bar bottom). **The ≤ 1 px gate is not met**: one frame's bar top was
  read at the outer edge of the bar's 4-px lighter top band once and at its inner edge once (2267 vs 2271); the
  lens edges themselves repeat within 0.7 / 1.5 px. Ambiguity intervals: ±0.5 px lens rows, ±1 px (±4 with the
  band choice) bar top, ±0.5 bar bottom; common bias: one annotator with memory and the same outer-edge choice on
  both readings, so repeatability does not bound accuracy. The boundary is visibly recoverable; the failing term
  is the bar-top band definition, not visibility — proposal for the ruling: fix the bar top as the outer edge of
  the light band (declared, then re-read blind), before any estimator iteration.
- **Held lens side-interior profiles** (`E8_held_side.json`): E7's lens columns come from the probe row near the
  lens top, so at mid-height they sit inside the lens (flat 49–50 luma); one registration repeat located the side
  contour per frame from the chromatic rim at mid-height (saturation top-hat, rows yc ± 20). H6 moving, left side
  over a black backdrop (outside luma 0): interior 48–50 (plan 47, within 5), luma 11.0 (d −1), 29.7 (d 0, the chromatic registration locus), 40.8 (d +1), 48 (d +2): 17 is crossed between
  d −1 and 0, so the interior reference 47 is approximately confirmed while "17 at the contour" is not established under
  the recorded origin; the measured registered profile replaces the old universal "47 → 17" (Astra round 7). H6 right
  side (backdrop = the bar, 46): interior 45–48 with a dark contour dip to 23 at d −3…−2. H2 settled, left side:
  unobservable (the contour column lands on the blue Calls glyph, luma 114–128, saturation 150–170); right side
  (bar backdrop 31–33): interior 49, a soft rise 35 → 44, no dip. Moving and settled kept separate; the side
  contour over the bar is a dip or absent, not the black-backdrop profile.
- **Path-backed device fixture** (`E1_fixture_path.json`, `tools/fixture_analyze.py`, captures in
  `datasets/device-captures/inverse-v1/fixture/`, sample code `PathFixture` in `CalibrationActivity.kt`, uncommitted): four 1:1
  panels over the target (closed capsule and the same capsule as a sampled-field path over black; a held-profile
  path capsule and a fused closed capsule over white), 200 × 80 R 40, pad set by padPx alone (wide kernel off).
  Cells: rs 1 pad 22.5 (layer pad 23, odd), rs 1 pad 23.5 (24), rs 0.5 pad 21.5 (layer 11, odd), rs 0.5 pad 23.5
  (12), plus rs 1 / rs 0.5 pad 100 and rs 0.25 pad 23.5 probes. Results (edges versus the declared box, px):
  closed capsule top/bottom −0.22/+0.22 (rs 1), −0.68/+0.68 (rs 0.5), −1.35/+1.35 (rs 0.25: the antialiasing band
  widens with reduced scale, symmetric), sides ±0.06–0.31, centre 0.00 at every scale, no clipping; **path capsule
  minus closed capsule: max |Δ| 0.49 (odd pad 23), 0.09 (24), 0.05 (rs 0.5, both parities), 0.58 (rs 0.25)** —
  within the 0.75 px gate at every pad phase and scale, no translation (centre ≤ 0.45 px at rs 0.25, 0.00
  otherwise). Two findings outside the gate for the special geometries: (a) **fused geometry at reduced scale**:
  the fused capsule's top/bottom edges sit +0.24/−0.24 at rs 1 but +3.25/−3.25 at rs 0.5 (+3.6 with pad 100,
  +2.72 at rs 0.25) — a symmetric 6.5–7 px height loss at half scale that does not depend on the pad size and that
  the JVM harness does not show (GlassFieldPadConsumerTest's fused case agrees within 0.75 px across scales): an
  Android host-path effect for fused elements at reduced scale, cause not yet located; (b) **held geometry versus
  pad**: with pad 22.5/23.5 the held-profile capsule is symmetrically inset 2.4–2.9 px top and bottom at rs 1 and
  0.5 (its own outline law, centre 0.00), but with pad 100 it reads +3.67/−0.24 (centre +1.7 px) at rs 1 and
  +3.33/−0.55 (centre +1.4) at rs 0.5, and at rs 0.25 pad 23.5 +1.08/−3.30 (centre −1.1): the held silhouette
  moves with the recorded pad and with quarter scale. Gate status: closed-form and path-backed PASS at all
  scales and parities; held and fused geometry show pad- and scale-dependent translation/inset of 1–3.6 px →
  **the padding commit is not cleared for held/fused geometry at reduced scale**; nothing fitted or changed.
- **E2c observability** (`datasets/calibration-targets/targets/r4/manifest_e2c_v2.json`, `tools/targets_e2c_v2.py`): at a destination pixel at
  depth d 8–16 in the mirrored zone the mirrored tap samples source row e − 68…76 and the direct tap e − 8…16
  (separation 52–68 px), so the first layout (A rows 300–1000, D rows 1300–2000) never excited the two taps of one
  pixel independently. Corrected local layout: A = rows 200–460, D = 460–720 (boundary at e − 40 for e = 500), four
  combinations e2c_v2_LL/HL/LH/HH with new hashes, registration controls outside the sheet's reach (rows 2140–2180,
  2300–2340, side columns). Per patch: q_mirror 424–432 (A, margin ≥ 28 px), q_direct 484–492 (D, margin ≥ 24 px),
  fine-path footprint ±3 px (excitable), wide-path footprint ±32 (light) / ±82 (dark) px straddles the boundary
  (not independently excitable): the test identifies the fine components only. Prediction-manifest draft written
  (I_c = 0 in the affine model; priors a ≈ (1.0, 0.88, 0.2), d ≈ (0, 0.12, 0.8) from E2b's w at d 8–16, ±0.1;
  development = first pull per target, locked = second pull; seeds 20260913 / 9137152 for the speckle pair). Nothing
  shot; the manifest is a draft for the ruling.

## Astra round 7 — decisions received; work done since

Full text: `ASTRA-RULING-round7.md`. Log/manifest corrections applied (stale ≤ 0.34 paragraph removed; E8 wording
narrowed to the profile/angular result in the black-backdrop pill domain; the held side profile stated at its
registered origin: 11.0 / 29.7 / 40.8 at d −1 / 0 / +1, "17 at the contour" withdrawn, interior 47 approximately
confirmed for moving H6-left over black; E2c v2 manifest amended: output rows y = e − d (484–492 at e 500),
"effective responses to regions A and D" instead of fine-path coefficients, E2b weights demoted to a conditional
hypothesis, LL/HH-normalised sum-to-one marked unidentifiable, state matching and frozen development coefficients
stated; manifest frozen; pixels unchanged).
- **Rest source audit**: done first (section above): registration exact, tips consistent, estimator unbiased in
  synthesis, the right-end plateau retained as a model residual, correspondence ambiguous by a possible common
  ≈ 0.6 px end-centre error; rest branch not promoted.
- **S4 audit** (section above): state/edge-identity assignment explains the mirrored failure; rescored M0 passes the
  mirrored RMS and p95 gates on validation (1.73 / 2.71) and fails the signed-bias gate only in the sparse d 0–5
  bin; M3/EFF not started (conditional on the ruling about the two low-d S3 copies).
- **Blind pilot, full re-read under the fixed convention** (`E7b_annotation_pilot_r7.json`; crops
  `scratch/pilot_r7_*`): all 13 assigned frames re-cropped (875, 905, 2965, 2978 re-centred from full-bar
  overviews), read twice in two shuffled orders at 6× with the bar top at the OUTER edge of the lighter band (inner
  edge annotated separately: it sits 2–2.5 px below the outer edge in every frame). Valid full-height frames: 11
  (Phone 14 × 8, Phone 10 × 890 and 905 — 905 now fully readable, WhatsApp 2978). Censored: 875 (lens not proud;
  its edges coincide with the bar band, H ≈ 0, not separable), 2965 (still boundary-limited after re-centring,
  H ≥ 8). H = (lens bottom − lens top) − (bar bottom − bar top): A / B = 42.5 / 42.2, 43.0 / 43.0, 36.5 / 36.5,
  36.2 / 36.2, 29.0 / 29.0, 29.0 / 28.5, 25.5 / 25.5, 25.3 / 25.3, 28.5 / 28.8, 28.3 / 28.5, 23.3 / 22.8.
  Repeatability: |ΔH| mean 0.16 px, max 0.5, SD(ΔH) 0.26 → single-reading SD 0.18 px. Boundary ambiguity (declared
  ±0.5 px per edge for the rim-line outer edge, the band outer edge and the bar bottom): RSS 1.0 px, linear 2.0 px;
  combined 1-σ uncertainty on H 1.02 px. **Gate status: repeatability is well inside 1 px; the ambiguity term alone
  reaches the 1 px limit** — the gate is met only if the per-edge boundary ambiguity is accepted as ≤ 0.4 px (the
  6× readability is ≈ 0.25 px, the physical edge width is 2–3 px); this is a definition for the ruling, not a
  measurement change. Common bias unchanged: one annotator, same convention both times. Note: the band's outer
  edge lies 2266.5–2271 (Phone) / 2279 (Phone keypad page) / 2283 (WhatsApp) while earlier gradient/highlight rows
  were 2281–2282.5, so H under this convention is 10–15 px larger than the round-6 readings and not comparable.
- Not done in this pass: the controlled device-fixture diagnostic (coverage render, test-only pad override), the
  E2c/speckle captures (the owner shoots on the iPhone), the tint-law tone work, M3/EFF.

## Open items

- Bubble set: rim detector not fit for the height outcome (pilot); manual annotation or one bounded detector iteration on training annotations next; bar-bottom definition (2460 highlight vs 2467.5 gradient) to declare.



- Android filtering: the 8-px modulation deficit (0.92–0.95) after the origin repair.
- E5 one-to-one matching now reports unmatched crossings: one unmatched observed crossing at d = 12 in every image and 1–2 unmatched predicted crossings at d 18–28 in the dark group (`E5_tangential.json`).
- Time provenance: `scratch/<name>_cfr_time_ambiguous.npy` flags CFR frames matched inside runs of identical source frames (COSG 62, LOLL 26); E7 must take timestamps from the native decode there.

- E5 scale generalization on I1/I2/I3/C4b using the Home Screen registration (landmarks only).

- E6 kernel/drift; rest/sheet E9 with the fold candidate and monochrome rays; held E9 after

  the compositor/M1 bounded fit; E7 with the PTS table; E8; E10.

- Pixel-7 performance is compared against a fresh Pixel baseline only; S24+ gates stay open.


## E1 controlled coverage/padding diagnostic — Xiaomi (Astra round 7, item 2), 2026-09-13

**Device: Xiaomi Redmi Note 13, adb serial 1a6f67f7, Android 15, Adreno 610, 1080x2400, density 2.75
(440 dpi). These results are recorded separately and do NOT close the Pixel-specific regression gates;
the Pixel 7 was absent from adb for this pass and was not touched.**

Provenance: library branch `research/ios27-measurements`, working tree (diagnostic patch set uncommitted);
`sample-debug.apk` built 18:06, installed on 1a6f67f7 at 18:18:02 and verified by `lastUpdateTime`; capture
`tools/capture_fixture_r7_xiaomi.sh`; analysis `tools/fixture_r7_analyze.py fixture_r7_xiaomi`; harness
`liquidglass/src/jvmTest/.../GlassCoverageDiagnosticTest.kt`; comparison `tools/fixture_r7_compare.py`.
Records: `E1_fixture_path_r7_xiaomi.json`, `E1_fixture_path_r7_jvm.json`, `E1_fixture_path_r7_compare.json`;
frames `datasets/device-captures/inverse-v1/fixture_r7_xiaomi/`, contact sheet `scratch/fixture_r7_xiaomi_sheet.png`.

**A first Xiaomi run was discarded before analysis.** The 18:09 cells captured the owner's own apps: the
capture script never checked what was in front of the screen. Those twenty PNGs are kept, unanalysed, in
`datasets/device-captures/inverse-v1/fixture_r7_xiaomi_invalid_1809/`. The script now verifies `topResumedActivity` is
`CalibrationActivity` before each screencap and skips the cell otherwise.

Design as assigned: the material is fixed (one style: wide kernel off, blur radius 22.5 px, bevel 6 px) and
only the *recorded* pad changes, through `LiquidGlassDiagnostics.recordPadOverridePx`, so blur radius and every
other material uniform stay put. `--ez coverage true` makes the panel shader return flat magenta times the
production coverage through the same geometry, field, recording and draw-back path. Four fixtures at 1:1 screen
px over the target's black half: closed capsule (40,150,240,210), the same capsule as a sampled path
(330,150,530,210), a held-profile path capsule (40,250,240,310) and a fused capsule (330,250,450,310) with its
sibling at 470–530. Cells: rs 1 pad 23/24/100, rs 0.5 pad 22/24/100, rs 0.25 pad 20/24, plus two material cells.

Estimator: the coverage cells composite premultiplied, so R = c + bg_r(1−c), G = bg_g(1−c), B = c + bg_b(1−c);
over a neutral backdrop **coverage = (R − G)/255 exactly**, and that is what is read (the bottom fixture row
overlaps the target's grey ramp, where the earlier "outside is black, coverage = R" reading would have been
wrong). The harness reads the layer's alpha, which is the same quantity.

Capture validity: every cell's two shots are **identical inside all five measurement windows** (only the status
bar clock differs across the frame); every coverage interior is saturated magenta (R−G = B−G = 255, G = 0);
the contact sheet confirms the declared layout with no neighbouring panel, band or sibling inside a window.

Result, coverage (full-resolution panel px, measured minus declared):

| cell | closed | path | held_path | fused_closed |
| :--- | ---: | ---: | ---: | ---: |
| rs 1 pad 23 | 0.00 | **+0.56 inset all four sides** | **+0.56 inset all four sides** | 0.00 |
| rs 1 pad 24 | 0.00 | 0.00 | 0.00 | 0.00 |
| rs 1 pad 100 | 0.00 | 0.00 | 0.00 | 0.00 |
| rs 0.5 pad 22 | ≤ 0.02 | 0.00 | ≤ 0.01 | ≤ 0.01 |
| rs 0.5 pad 24 | ≤ 0.02 | 0.00 | ≤ 0.01 | ≤ 0.01 |
| rs 0.5 pad 100 | ≤ 0.02 | 0.00 | ≤ 0.01 | ≤ 0.01 |
| rs 0.25 pad 20 | ≤ 0.03 | −0.23/−0.27/−0.36/−0.38 | −0.38 (centre dy −0.38) | ≤ 0.04 |
| rs 0.25 pad 24 | ≤ 0.03 | −0.23/−0.27/−0.36/0.00 | −0.38 (centre dy −0.38) | ≤ 0.04 |

- **Worst device deviation from the declared box: 0.562 px** (`cov_rs1_pad23/path/d_top`), gate 0.75 px.
- **Pad invariance on the device**, each cell against the pad-24 cell at the same scale: rs 1 pad 100 → 0.000,
  rs 0.5 pad 22 → 0.000, rs 0.5 pad 100 → 0.000, rs 0.25 pad 20 → 0.380 (`path/d_right`),
  rs 1 pad 23 → 0.562 (`path/d_top`). All within the 0.75 px gate.
- **JVM harness**, identical uniforms, geometry, fusion, field range/scale and layer dimensions, at the same
  scales and pads: worst deviation **0.014 px**; path-backed minus closed-form worst **0.010 px**; the fused
  union's right edge reads 0.00 where the pad covers it.
- **Android minus JVM: worst 0.552 px**, all of it the same rs 1 / pad 23 / path-backed cell.
- Held profile: no translation and no inset beyond the path-backed capsule's own; the held silhouette does not
  move with the pad. Fused: no movement; at rs 0.25 pad 20 the sibling lies outside the harness's recorded layer
  and the harness reading is the capsule's own edge, flagged, not compared (on the device the sibling is drawn
  in full at every pad, which is an Android/JVM difference in drawn extent, not in silhouette position).

**Branch (Astra round 7, item 2): coverage is within 0.75 px and pad-invariant at every scale and geometry, so
the coordinate question is closed.** The earlier device findings — fused geometry ±3.25 px at rs 0.5 and the
held silhouette moving +1.7 px at pad 100 and −1.1 px at rs 0.25 — are not present in coverage, and were
therefore appearance/estimator effects of the luma crossing and of the pad also having changed the blur radius.
No geometry compensation is warranted. The material cells confirm the estimator diagnosis directly: the same
closed capsule read by 50 % luma gives −0.22/+0.22 px at rs 1 and −0.68/+0.68 px at rs 0.5 (the luma silhouette
dilates with render scale while the coverage silhouette does not), and on the bottom row, where the panels
overlap the grey ramp, the luma crossing produces no reading at all (`held_path` and `fused_closed` INVALID in
both material cells).

**Residual, recorded not repaired:** at rs 1 with the odd recorded pad 23, the two *sampled-field* fixtures are
inset a symmetric 0.56 px on all four sides (centre unmoved) on the device but not on the harness (0.01 px),
and the closed-form fixtures are unaffected at the same pad. At rs 0.25 the sampled field carries a −0.23 to
−0.38 px offset and a −0.38 px centre shift on the device, again absent on the harness. Both are under the
gate and both are specific to the Android sampled-field path; no repair loop was started and no threshold was
moved. Open for a ruling on whether a sub-gate, Android-only, odd-pad field offset is worth a regression test.

Library state: no commit made. The follow-up commit Astra authorized is for a *confirmed defect*; coverage
shows none, so the only library change from this pass is the new JVM test
(`GlassCoverageDiagnosticTest.kt`, untracked). Full JVM suite after it: **52 tests, 0 failures**.

### Round 8 ruling applied — diagnostic accepted, instrumentation committed (2026-09-13)

Astra accepted the Xiaomi diagnostic as PASS for the tested pad and scale combinations, closed the padding
investigation unless a later test crosses the gate, and ruled the small residuals are to be left alone (no
geometry tuning, no threshold change, no further audit). Scope stays as measured: these crossings, in these
fixtures; clipped fused right edges remain excluded. **The Pixel comparison remains open** — Xiaomi's result
does not retrospectively establish the cause of the earlier Pixel discrepancy — and one short Pixel session on
the same controlled fixture is reserved for when that device returns.

Cleanup carried out as ruled, then committed:

- `uDebugCoverage` joins the uniform equality that keys the effect cache (hashing left as it was, which stays
  consistent with equality).
- The diagnostics are explicitly opt-in: a `@LiquidGlassDiagnosticApi` marker at ERROR level, both switches
  defaulting to off, `reset()` and a scoped `withDiagnostics { }`. The library's own two read sites and the
  calibration rig opt in where they are; an app that does not opt in cannot reach them.
- The JVM test no longer writes to the hardcoded research path. The record is written only when
  `-Dliquidglass.coverageRecord=<file>` names one, so an ordinary run stays portable.
- Pairwise pad assertions added: every eligible edge compared across pads at one scale at the same 0.75 px
  tolerance, with a fused right edge admitted only where both readings contain the union uncut. Harness worst
  pairwise difference **0.008 px** (`path rs 1, pad 23 vs 24, bottom`). No JVM test expects the Android-only
  0.56 px effect; that stays in the device record where it was measured.
- `diagnosticsOffLeaveTheProductionPathUntouched` asserts the production recorded pad (23 px for this fixture
  style) is unchanged by an override that has been reset.

**Runtime verification after the cleanup:** the sample was rebuilt, reinstalled on 1a6f67f7 (lastUpdateTime
18:39:03), and all ten cells re-captured
(`datasets/device-captures/inverse-v1/fixture_r7_xiaomi_postcleanup/`). Every cell is **bit-identical to the pre-cleanup capture
inside every measurement window** (max |difference| 0 over all ten cells and all five windows,
`E1_fixture_path_r7_xiaomi_cleanup_recheck.json`), so the cleanup changed no runtime behaviour and the
diagnostic results above stand unrepeated. Full JVM suite: 52 tests, 0 failures.

Commit `aa9e97e` on `research/ios27-measurements`: *test(glass): add controlled coverage and padding
diagnostics* — hooks, calibration fixture, harness support and the regression test together. Not a new optical
fix and not a resolution of the Pixel discrepancy. No push, tag or release.

The accidentally captured app frames stay excluded (`fixture_r7_xiaomi_invalid_1809/`) and the
foreground-activity check stays in the capture script. Next assignment: the shared tone-stage work with the
geometry frozen and its provisional status recorded. The low-depth sheet branch and the bubble uncertainty
remain unresolved; this diagnostic supplies no evidence bearing on either.

## E9 photometric block — shared tone stage (Astra round 6 item 3, assigned again round 8) — FAILS the holdout; baseline retained

> **Superseded in part by the correction section below** (Astra's review of this record): the stage-B numbers
> here were produced with a gauge that was not enforced, and the closing claim about photometry overreached.
> The stage-A numbers and the split stand.

Record `E9_tone.json`, scripts `tools/e9_tone.py` and `tools/e9_tone_figure.py`, figure
`scratch/E9_tone_residual.png`. **Geometry frozen and explicitly provisional** (the M0 rest curve from
`E9_geometry.json`, identity beyond d 36); the K0 kernel is taken from `E6_frozen.json` and **not refitted**;
displacement, blur, outlines and registration are untouched. Only the photometric map changed.

**Split declared before fitting.** Training — light appearance C1a-r0 / r31 / r50 / r100 (t 0, 31, 50, 100, all
read-back verified); dark appearance C1a-0-light-dark (0), C1a-r29 (29), C1a-r50 (50), C1a-100-light-dark (100).
Holdout, scored once and never fitted — C1a-r62-light-light and C1a-r62-light-dark. Locked — IMG_6647. All over
the light calibration target. Two provenance notes: the dark end points come from the earlier series whose mid
labels are off by one slider step (`datasets/mac-rig/captures/LOG.md`: 25 → 35 %, 50 → 62 %, 75 → 86 %), so only its extremes
are used and they are **label-only, not read-back verified** — without them the dark law has no anchor above
t = 50 and the dark holdout becomes extrapolation; and **IMG_6647 is a near-replicate of C1a-r50-light-dark**
(max 24 levels difference inside the toolbar ROI, mean 2.0 over the frame), so as a locked test it carries
little independent information. It is not a duplicate.

**Stage A — the shared tint law** (a(t), b(t) piecewise linear on the verified knots, one law per appearance,
replacing E6's one gain/offset per image; bounds gain [0, 1.2], offset [−64, 255], all satisfied):

| | gain at t = 0 / 29–31 / 50 / 100 | offset at the same knots |
| :-- | :-- | :-- |
| light | 0.579 / 0.334 / 0.209 / 0.004 | 124.4 / 156.5 / 173.0 / 210.2 |
| dark | 1.200 / 0.870 / 0.588 / 0.048 | −61.3 / 1.3 / 37.9 / 70.7 |

The law is monotone and ends near zero gain at full tint, which is the expected opaque limit.

**Stage B — one bounded depth-dependent tone correction**, a depth-varying affine tone map (1 + g(d)) on the
rendered contrast plus c(d) on the level, **shared across every training image and both polarities**, gauged to
zero mean over the depth range so it cannot trade with the tint law, bounds |g| ≤ 0.5 and |c| ≤ 16 levels,
solved by alternating least squares from stage A. Fitted: g = +0.117, +0.153, +0.166, +0.158, +0.083 at
d = 6, 12, 20, 32, 72; c = +3.6, −0.7, −2.6, −2.4, +2.1 levels. Both stay well inside their bounds.

Holdout, scored once, no refit (gates unchanged: RMS ≤ 2, notch ≤ 3, position ≤ 1.5 px, width ≤ 1 px):

| image | stage | clean-row RMS | notch obs / pred | env 50 % err px | width err px | verdict |
| :-- | :-- | --: | :-- | --: | --: | :-- |
| C1a-r62-light-light | A | 4.78 | 0.7 / 2.2 | −0.05 | – | RMS fails |
| C1a-r62-light-light | B | 4.53 | 0.7 / 3.7 | −0.06 | – | RMS fails |
| C1a-r62-light-dark | A | 6.01 | 42.6 / 6.1 | +0.02 | −4.52 | RMS, notch, width fail |
| C1a-r62-light-dark | B | 5.96 | 42.6 / 6.6 | +0.01 | −4.50 | RMS, notch, width fail |
| IMG_6647 (locked) | A / B | 6.34 / 6.30 | 41.7 / 7.8 | +0.03 | −4.8 | RMS, notch, width fail |

**Verdict: FAIL at both stages.** Only the envelope-position gate is met, as in E6. Per the ruling the
**shipping filter baseline is retained**; nothing was implemented, no comparison switch was added, no kernel was
widened and no threshold was moved.

What the stage did achieve, recorded because it is a real improvement inside a failing model: the shared tint
law **cuts the holdout residual without any per-image refit** — light 6.44 → 4.78 and dark **11.11 → 6.01**
against the E6 frozen numbers, the dark gain being the case E6 had to freeze at the tint-50 value. A shared
law across tint is therefore better than the frozen-nearest-tint treatment, and it is the part of this stage
worth keeping when the block is next revisited.

**Residual pattern (the reason a tone term cannot close it).** Binned by depth on the holdouts, the *bias* after
stage B is small everywhere — +2.4 → +0.2 levels (light), +0.9 → −0.3 levels (dark) across d 6 → 72 — while the
*RMS* in the same bins is 9.1 → 2.2 (light) and 11.4 → 3.0 (dark), largest near the rim and falling with depth.
The residual is therefore **spatial structure, not a depth-dependent level error**: there is almost no bias for
a tone correction to remove, which is why stage B moved the holdout RMS by only 0.05–0.25 levels. The shared
depth correction it did find is small and smooth, and it is the same sign in both polarities, as required.

The dark notch is untouched: observed 42.6 levels, predicted 6.1 (A) and 6.6 (B), an error of −36. A correction
shared across both polarities cannot reproduce it, because the same source produces a 42.6-level drop in the
dark appearance and a 0.7-level drop in the light one, a ratio of about 50, while the two appearances' gains
differ by a factor of about 2.8. **The dark notch is not a tone effect at all**, and this stage is the evidence:
with geometry and kernel frozen, no shared photometric map of the permitted form reaches it.

One limitation of the observable itself, reported rather than fixed: `notch_drop` is one-sided
(reference minus minimum). In the light appearance the observed profile *rises* about 39 levels across the
notch rows and the model stays flat, yet the drop statistic reads 0.7 against 2.2 and passes the ±3 gate. The
light-appearance notch gate is therefore passing on a statistic that does not see the actual mismatch; the
figure shows both curves. Threshold unchanged, definition unchanged — this is for the ruling.

Fallback taken, as specified: keep the baseline, report the residual pattern, start no further model search.

### Correction to the tone stage, after Astra's review (2026-09-13)

Astra raised two objections to the record above before its session ended. Both are correct and both are
answered here. Nothing above was deleted; the stage-B numbers there are superseded by these.

**1. The zero-mean gauge was not enforced, so stage B never tested a depth shape.** The gauge was two
penalty rows weighted 1e3 against ~100 000 data rows — negligible. The fitted correction came out
mean g = **+0.135**, and the stage-B tint gains were exactly the stage-A gains divided by 1 + mean g
(0.5788 / 0.5099 = 1.1352 = 1 + 0.135, and the same ratio at every knot). The whole "depth correction" was a
global gain rescale that the tint law immediately absorbed. The gauge is now imposed by construction, by
parameterising g and c in the null space of the constant vector (`null_basis` in `tools/e9_tone.py`), and
mean g = mean c = 0 to machine precision.

Re-run with the gauge actually enforced, g = −0.189, +0.056, +0.040, +0.101, −0.009 and
c = +8.0, −1.7, −2.9, −4.4, +1.0 levels at d = 6, 12, 20, 32, 72:

| holdout | stage A | stage B (first run, void) | stage B (gauge enforced) |
| :-- | --: | --: | --: |
| C1a-r62-light-light | 4.78 | 4.53 | **4.63** |
| C1a-r62-light-dark | 6.01 | 5.96 | **6.09** |

With the free global gain removed the shared depth correction gains essentially nothing — it *worsens* the
dark holdout — and it makes several training images worse (light r0 6.47 → 7.17, dark r29 7.56 → 8.41). The
earlier apparent improvement was entirely the unconstrained gain. Verdict unchanged: FAIL.

**2. "Photometry is ruled out" overreached, and the ruling's matched-source route is not available here.**
The ruling asks for the discrimination Y(d, B) = a(d)B + b(d) "via matched source changes". The obvious
source change is the second calibration target — and it does not exist at this ROI:
`calibration-target-light.png` and `calibration-target-dark.png` are **identical over rows 2310–2440**, the
toolbar band (max difference 0.0 there, against up to 255 levels elsewhere in the frame). Every
light-target/dark-target capture pair therefore scores identically inside the toolbar, which is exactly what
`E9_tone_discriminate.json` shows. The source *is* varied at fixed depth, but by the target's own horizontal
structure: the rendered-source span along a row is about 187 levels in the light appearance and 81 in the
dark, so a(d) is excited; the second target simply adds nothing to it at this ROI.

To bound the family rather than one member, the depth correction was then refitted **per appearance**
instead of shared across both polarities — a labelled diagnostic, not an adopted candidate, since the ruling
requires sharing (`tools/e9_tone_perapp.py`, merged into `E9_tone.json` as
`diagnostic_per_appearance_depth`). Holdout: light RMS 4.50, dark RMS 6.17, **dark notch predicted 5.2
against 42.6 observed**. Freeing the sharing does not help. So the restriction that failed is not the
sharing across polarities.

**The claim, restated at the width the evidence supports.** With the geometry and the K0 kernel frozen, no
depth-dependent affine tone map of the assigned family — shared across polarities or per appearance, with or
without the tint law — reaches the dark notch, on this toolbar ROI in luma. That is narrower than
"photometry is ruled out": it says nothing about a source-dependent or non-affine transfer, about the colour
domains the decomposition makes explicit, or about clipping and compositing, which E8 already flagged where
the rim line vanishes over white. Those remain open and are not tested here.

Records: `E9_tone.json` (stage A, corrected stage B, and the per-appearance diagnostic),
`E9_tone_discriminate.json` (the two-target check and the row-wise a(d), b(d) measurement), figure
`scratch/E9_tone_residual.png`. Scripts `tools/e9_tone.py`, `tools/e9_tone_discriminate.py`,
`tools/e9_tone_perapp.py`. Baseline still retained, nothing implemented, no commits.

## Phase 1 closeout index (2026-09-13)

The three-phase closeout contract (`THREE-PHASE-CLOSEOUT.md`) replaced the packet/ruling loop. Phase 1 ran to
completion in one session; the deliverable is `analysis/closeout/phase1/PHASE1-HANDOFF.md`, which carries one
primary and one fallback for every component. This is an index only — nothing above is rewritten or erased.

- **Pixel coverage fixture, reserved leg — PASS, coordinate gate CLOSED.** Worst vs declared 0.556 px,
  Android−JVM 0.546 px, pad-invariance 0.000 except the pad-23 cell. `E1_fixture_path_r7_pixel.json`,
  `E1_fixture_path_r7_compare_pixel.json`.
- **Pixel baseline preserved for same-device A/B**, `datasets/device-captures/closeout/pixel_baseline/` (+ `timing/`): total
  median 17/19/17 ms, GPU median 8/9/8 ms, 0 missed vsync, 90 Hz panel.
- **Tone, one corrected constrained fit — FAILS its gates; baseline kept.** `E9_tone_constrained.json`.
  Depth-weighted gauge enforced inside a bounded solve; four starts agree; 87.328 → 85.413. Bounded Stage A
  differs from the old clipped one (dark t=0 offset −41.92 not −61.26; that image 26.60 → 16.81 RMS).
- **The "dark notch" was the toolbar glyphs** — `e6.observables()` omits the glyph mask the RMS uses. Masked:
  dark 11.8, light −6.6, same sign. `E9_notch_v2.json`, `E9_notch_ownership.json`.
- **The envelope-width gate was never measurable** — the 10 % level is below the noise floor in every image;
  the "dark width 6.9 vs 2.4, FAIL" reading is withdrawn as insufficient. `E9_envelope_validity.json`.
- **Display vs linear-light — neither passes**, linear better on the dark by 0.75 levels (under the 2-level
  rule). `E9_domain.json`.
- **Rest source map — the one supported replacement.** Shipping `restSource` fails every geometry gate
  (validate RMS 6.80, p95 14.7, bin bias 14.7); M0 passes all (0.89 / 1.37 / 1.37). `E9_map_compare.json`,
  export + reference evaluator + verification in `closeout/phase1/exports/`.
- **Sheet map — comparison not established** (needs the cover sheet's own band); `foldSource` kept.
  `E9_map_compare_sheet.json`.
- **Content age/lag — INSUFFICIENT**, zero added delay. LOLL8185 H1/H3 cannot measure it (static page);
  JGEY2190 gives 3 episodes / 61 samples, 0 qualifying stops, CI [0, 33] ms, held-out 5.39 px vs a 2 px gate.
  `E7_lag.json`, `E7_lag_repaired.json`, `E7_lag_information.json`.
- **Bubble area conservation — REJECTED** as a description (ratios 0.79–1.28). `E7b_area.json`.
- **E2c** unexecuted, no new captures exist, none requested; **E5 size generalization** insufficient; **E10**
  closed with no unique h,n,g recovery, n = 1.5 a gauge whose τ and γ sit on their bounds.

# Scoring tools for the audit's tooling gaps (#101) — 7 November 2026

Tooling only: no library, shader, port or optics change. Source: branch `tools/101-scoring-gaps` from `main`
13ec13b. The iOS recordings are private HDR10 files read locally; this record holds our numbers and the
tools, not iOS frames. Numbers are from the **desktop replay** (Skia, GPU), not the Pixel.

| Audit item | State |
| :-- | :-- |
| 96 replay trace logs transit and held travel | done on 26 Oct (4714e22), unchanged |
| 98 two scripts per recording, baselines must match | tools below: scripts are recorded and checked |
| 87 no light-mode score for holds and drags | `tools/measure_light_hold.py`, first scores below |
| 88 no score for content-page fringes | `--content-split` in `tools/measure_rim_fringe.py`, first scores below |

## 98: a replay says which script it came from

- `tools/reference-replay/render.sh <abs script> <abs outdir> [variant]` runs `:desktop:phoneReplay` and writes
  `<outdir>/replay-meta.json` (script path and sha256, variant, commit, dirty flag). It is the tracked twin of
  the private `rrw.sh`, which writes nothing about the script.
- `tools/reference-replay/replay_meta.py` compares: `check <dir> <script>` and `pair <dir A> <dir B>`.
- `tools/measure_rim_fringe.py` calls it and refuses to score a replay whose recorded script differs from the
  recording's own (IMG_6756, JGEY2190 and LOLL8185 use `script3.txt`; docs/1 and docs/2 use `v1-script.txt` and
  `v2-script.txt`); `--allow-script-mismatch` overrides. A replay without metadata (every earlier render) is
  accepted unchanged, so nothing already rendered stops working.
- Checked on four fixtures: same script (ok), different script (exit 1 with both paths and hashes), a pair from
  two scripts (message), no metadata (ok). A real render (current main, `hold128.txt`, `calm-light`) wrote
  `replay-meta.json` with the script's hash and `dirty=true`: the checkout held these uncommitted tool edits.

## 87: light holds and drags against IMG_7049

`tools/measure_light_hold.py` pairs every frame of the iOS recording with ours for the same instant of
`hold128.txt` (IMG_7049's light-mode hold and drag across Keypad and Contacts: finger down, held, dragged right
and back, 5 s), reads one luma column that follows the finger, and scores it in four row bands: `top` 448-466
(bar and lens upper rim), `lower` 622-646 (lens lower rim), `shadow` 650-668 and `tail` 678-694. Frames are
phased from the script: rest, hold (finger under 60 px/s), drag. Levels are display luma.

**Alignment.** The script was cut with a 36.0 s shift, but `--scan-offset` finds the replay aligns with the
recording at about 35.7 s: all-band rms 7.3 at 35.5 and 8.6 at 36.0, shadow-band correlation +0.57 at 35.6-35.7
against +0.37 at 36.0 (the top band's anti-correlation peaks at 35.8: iOS dims there, ours does not). The
default is 35.7. The scores move by 1-1.3 levels across that range, so the comparisons below hold either way.

Current main (13ec13b, `calm-light`, replay), 259 paired frames, ours minus iOS in levels and the rms of the row
profile:

| Band | rest, 29 frames | hold, 144 | drag, 86 |
| :-- | --: | --: | --: |
| top 448-466 | +0.8 (rms 6.8) | **+6.5** (11.8) | **+5.0** (10.4) |
| lower 622-646 | +0.4 (4.4) | -0.8 (6.1) | -1.7 (6.3) |
| shadow 650-668 | +0.3 (0.7) | **-6.2** (9.5) | **-6.3** (9.3) |
| tail 678-694 | +1.3 (5.5) | -1.6 (3.1) | -1.2 (2.8) |

All frames, all bands: 7.80 levels rms. **What it shows that nothing did:** at rest the light bar matches
iOS to within a level and a half; while held and while dragged, iOS's lens dims the bar's upper rim band by
6.5 levels (ours does not: 254.6 against 248.2) and its shadow below is 6 levels lighter than ours.
The earlier scores (top rim 227 against 226 at one row, shadow rows 640-684 at 3.43 rms) were taken on 39.4-39.9 s
only; across the whole hold and drag these two bands are the gaps. Drags now have a score; they read the same as holds.

The tool tells candidates apart: the `h128m` and `h128o` replays rendered during #128 (before and with the held
outline's grey, same script) score hold shadow -5.6 and -7.5 and hold top +6.3 and +6.1.

Limits: one recording and script; the bands avoid the replay scene's page text and the recording's separator
lines (rows ~443 and ~673), so the icons and labels are not scored; luma only, not hue; alignment checked with a
scan, not with a marker in the recording.

## 88: page content seen through the lens

The fringe scorer drops every hue the page shows, so it cannot see colour fringes on the page's own content.
`--content-split` adds the R-B channel shift at the page's strong edges (the same estimator as the scorer's `R-B`
column), separately for edges under the lens (iOS's ink centroid ± 165 px, from `track.csv`) and away from it.

First scores, IMG_6756 (35 s of Phone-scene holds, drags and taps) against main's replay `n6` (same `script3.txt`),
re-run on 7 November with the edge counts and with only frames that have an iOS under-lens estimate (the first run
counted every lens-up frame). R-B p90 at strong page edges, median over frames (IQR), px, and under each value the
median number of edges that value rests on (an edge is a strong gradient where the R and B profiles correlate; at most
400 are sampled per region per frame, and a region with fewer than 10 gives no value):

| Frames | iOS under the lens | ours under the lens | iOS away from it | ours away from it |
| :-- | --: | --: | --: | --: |
| lens still, 487 | 0.61 (0.48-0.87), 109 edges | **3.80** (3.71-3.96), 153 edges | 0.00 (0.00-0.00), 377 edges | 0.00 (0.00-0.00), 400 edges |
| lens moving, 294 | 0.59 (0.48-0.95), 156 edges | **3.67** (3.41-3.85), 159 edges | 0.00 (0.00-0.00), 384 edges | 0.00 (0.00-0.00), 400 edges |

The 0.00 away from the lens is a real estimator output, not a missing one: it rests on 377-400 valid edges per frame
(400 is the sampling cap), and a p90 of 0.00 px means at least nine in ten of them show no measurable R-B shift,
which is what the page's own text and shapes should do. The 12 px border mask only trims the region's edges. So the
page itself has no channel split in either source, and the split under the lens is the lens's. Under the lens our
page edges read about six times iOS's channel shift.
**This cannot yet be attributed to the page content seen through the lens**: the same estimator also sees the lens's
own rim, and ours carries a spectral rim split (#73) that iOS's rim shows at 0.9-1.4x by the fringe-energy score. Treat
it as a flag for a follow-up (restrict the edges to those away from the rim band, then score), not as a finding about
content. It is the first number for this region either way; none existed.

Limits: it needs `track.csv` (IMG_6756, JGEY2190, LOLL8185); the lens width is fixed at 330 px, a held lens only;
a strong edge under a lens that is not content (the lens's own rim) also counts, so read the under-lens figure as
"page edges plus the rim", with the away-from-lens figure as the page's own baseline.

## 88 follow-up: edges away from the lens rim (16 November)

`--content-core PX` (with `--content-split`) adds a third figure: only the under-lens edges at least PX px inside the
lens outline, taken as a capsule (330 px wide, rows 436-655 in scene pixels, the held lens in both sources). With 40 px
the rim band and its 12 px estimator window are out. Same recording, replay and script as above (IMG_6756 against `n6`,
`script3.txt`; main replay rendered before this change, so the scorer is the only difference), run on 16 November.
R-B p90 at strong page edges, median over frames (IQR), px, with the median number of edges it rests on:

| Frames | iOS under the lens | iOS core | ours under the lens | ours core |
| :-- | --: | --: | --: | --: |
| lens still, 487 | 0.62 (0.48-0.88), 109 edges | 1.09 (0.46-4.61), 16 edges | 3.82 (3.70-3.98), 152 edges | **5.60** (5.15-6.00), 50 edges |
| lens moving, 294 | 0.60 (0.48-0.95), 155 edges | 1.24 (0.49-4.90), 19 edges | 3.66 (3.42-3.85), 158 edges | **5.27** (4.79-6.00), 41 edges |

(The "under the lens" columns reproduce the earlier run within 0.05 px; the core edge counts are medians over the frames that have
a core estimate, and the 16 November re-run corrects the core clipping near the image edge and that count.) **Reading:** at edges
at least 40 px inside the outline the page seen through our lens still reads a channel shift of 5.3-5.6 px against iOS's
1.1-1.2, so a split persists away from the rim. This does not show that the rim added nothing to the whole-lens figure
(3.7-3.8 px): the two figures use different edge populations (the core keeps fewer, and different, edges), so they are not
comparable as a subtraction. Confidence: iOS's core figure rests on 16-19 edges per frame and its IQR is wide (0.46-4.9), so its
level is poorly pinned; ours rests on 41-50 edges and is stable. Fewer edges survive in the core because the lens is magnified
page with few strong edges; a frame with fewer than 10 gives no value. The capsule is an approximation (a held lens, fixed
size; the bounds are not clipped to the image, only the sampling is), and the estimator cannot say whether the shift comes from
dispersion in the refraction or from a colour fringe the glass adds. Nothing measured was re-fitted.

Self-test, no recording needed: `python tools/test_measure_rim_fringe.py` (capsule geometry, also for a lens near the image edge; the core edge count; a split confined to the rim is
invisible in the core figure and visible in the plain under-lens figure).

## Addendum, 18 November 2026: three-row split, IMG_7048 geometry, continuity scorer, device replay

Tooling only; no library, shader or port change. Every number below is measured on a private recording, run from the
repository root against local renders of main `0114b379` (script `script3.txt`); nothing from the recordings is committed.

- **Item 88, `--content-split --rim-exclude 24`** (`--content-core` is kept as an alias). Under-lens page edges are now
  split by their distance to the lens outline (24 px is authored: about the 0.6 R refraction band of a 62 dp lens at 3x).
  Ours reads the outline from the replay's `trace.csv` (bar centre, extents, protrusion), iOS from `track.csv`
  (centre +- 165 px, fitted). IMG_6756 vs `m1115`, 781 lens-up frames: away from the lens iOS 0.00 px, ours 0.00 px (the
  control passes); inside the rim band iOS 0.57, ours 2.95; away from the rim iOS 0.62, ours 3.02. The two "under"
  rows differ for ours (edge counts 282 vs 24 per frame, frames with a value 780 vs 429), so the tool separates them, but the
  interior row stays near 3 px because part of it is the top bezel (y 460-463, 2.4-5.3 px) and the few text edges
  are themselves split; read it as "a split persists away from the rim", as the 16 November core figure did.
- **Item 87, second recording.** `measure_light_hold.py --geometry IMG_7049|IMG_7048|pixel7` defines the four bands
  relative to the bar's rest top (452, 322 and 2120 rows) so a cropped recording or a phone capture scores the same bands;
  `--sdr-video` reads an SDR capture, `--auto-bar-top` and `--bar-top` override the table. IMG_7048 (1168x570 crop,
  HDR10) holds all four bands (rows 318-564 of 570). The gate did not move: IMG_7049 `m101` still prints hold top +6.5,
  shadow -6.2, drag top +5.0, shadow -6.3, all bands 7.80 levels rms over 259 frames. The IMG_7048 hold script
  (`ref/light/IMG_7048-hold.txt`, 41.20-45.05 s) is cut but **not rendered**, so IMG_7048 has no score yet.
- **Item 87, device.** `tools/pixel/replay_script.sh <script>` replays a script's finger path with
  `adb shell input motionevent` in one shell session (scale 0.875 scene to Pixel 7, authored from the measured bar top);
  `--print-events` is the dry run. Dry runs only: `hold128.txt` 96 events over 5000 ms and the IMG_7048 script 33 events over
  3847 ms, zero timing drift. **No phone was involved**, so the +-25 ms / +-6 px device check is not run.
- **New: `tools/measure_formation_continuity.py`** (#103). For each frame pair where `trace.csv` `formation` crosses 0.5 it
  compares the change in fringe energy (dE) and in luma inside the selector box (dI, motion-compensated) against the
  neighbouring pairs; the finding on main and the result of the fix are in
  `review/atlas/formation-continuity-2026-11-18/README.md`.

Self-test: `python tools/test_measure_rim_fringe.py` (nine synthetic checks, including the rim/interior separation, the
trace outline and fallback, a lens near the image edge and the fast-path equivalences).

## Reproduce

```
tools/reference-replay/render.sh <abs>/hold128.txt <abs>/m101 calm-light     # JAVA_HOME / ANDROID_HOME as for Gradle
python tools/measure_light_hold.py <abs>/m101 --ref .local/sxs --video research/datasets/ios27-phone/light/IMG_7049.MP4 [--scan-offset]
python tools/measure_rim_fringe.py IMG_6756 n6 --content-split --ref .local/sxs
python tools/measure_rim_fringe.py IMG_6756 n6 --content-split --content-core 40 --ref .local/sxs   # adds the core figure
python tools/test_measure_rim_fringe.py
python tools/reference-replay/replay_meta.py pair <dir A> <dir B>
```

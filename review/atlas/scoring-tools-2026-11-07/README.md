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

First scores, IMG_6756 (35 s of Phone-scene holds, drags and taps) against main's replay `n6` (same `script3.txt`):
R-B p90 at strong page edges, median over frames (IQR), px:

| Frames | iOS under the lens | ours under the lens | both, away from the lens |
| :-- | --: | --: | --: |
| lens still, 487 | 0.61 (0.48-0.87) | **3.80** (3.71-3.96) | 0.00 |
| lens moving, 294 | 0.59 (0.48-0.95) | **3.67** (3.41-3.85) | 0.00 |

So under the lens our page edges read about six times iOS's channel shift, while away from it both are zero.
**This cannot yet be attributed to the page content**: the same estimator also sees the lens's own rim, and ours
carries a spectral rim split (#73) that iOS's rim shows at 0.9-1.4x by the fringe-energy score. Treat it as a
flag for a follow-up (restrict the edges to those away from the rim band, then score), not as a finding about
content. It is the first number for this region either way; none existed.

Limits: it needs `track.csv` (IMG_6756, JGEY2190, LOLL8185); the lens width is fixed at 330 px, a held lens only;
a strong edge under a lens that is not content (the lens's own rim) also counts, so read the under-lens figure as
"page edges plus the rim", with the away-from-lens figure as the page's own baseline.

## Reproduce

```
tools/reference-replay/render.sh <abs>/hold128.txt <abs>/m101 calm-light     # JAVA_HOME / ANDROID_HOME as for Gradle
python tools/measure_light_hold.py <abs>/m101 --ref .local/sxs --video research/datasets/ios27-phone/light/IMG_7049.MP4 [--scan-offset]
python tools/measure_rim_fringe.py IMG_6756 n6 --content-split --ref .local/sxs
python tools/reference-replay/replay_meta.py pair <dir A> <dir B>
```

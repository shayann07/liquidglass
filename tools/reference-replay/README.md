# Reference replay tools

Reproduce the continuous iOS comparison recorded in
[review/atlas/reference-replay-2026-10-13](../../review/atlas/reference-replay-2026-10-13/README.md).
The recordings (`docs/1.MP4`, `docs/2.MP4`) are private and git-ignored; everything here reads
them locally and writes only to the working directory. Requires ffmpeg and Python with numpy and
Pillow. Run from a scratch directory such as `.local/sxs/`.

| Step | Command |
| :-- | :-- |
| Track the selected ink | `python track.py ../../docs/1.MP4 v1.csv` (and `2.MP4 v2.csv`) |
| Tap events (video 1) | `python events.py v1.csv` |
| Input scripts | `python v1script.py 0 32.2 v1-script.txt`, `python v2script.py` (writes `v2-script.txt`, `v2-catalogue.txt`) |
| Render | `tools/reference-replay/render.sh <abs>/v1-script.txt <abs>/out-v1 [variant]` (Windows: a POSIX sh such as Git Bash, calling `powershell` and `tools/workspace.ps1`; runs `:desktop:phoneReplay` and writes `replay-meta.json`: the script's sha256, variant and commit; `calm-light` is light Calm) |
| Same script for both | `python tools/reference-replay/replay_meta.py pair <dir A> <dir B>`; `tools/measure_rim_fringe.py` refuses a replay whose script is not its recording's (`--allow-script-mismatch` overrides) |
| Light holds and drags | `python tools/measure_light_hold.py <ours-dir> --ref .local/sxs --video research/datasets/ios27-phone/light/IMG_7049.MP4` (`--scan-offset` checks the alignment) |
| Page content split under the lens | `python tools/measure_rim_fringe.py <name> <ours-subdir> --content-split` |
| Paired strips | `python strip.py ../../docs/2.MP4 out-v2 2.10 2.60 0.0333 throw.png` |
| Three-row video | `./sxs3.sh ../../docs/2.MP4 before-v2 out-v2 cmp-v2.mp4` (local only: contains the reference) |

`v1-script.txt`, `v2-script.txt` and `v2-catalogue.txt` are the inferred scripts (v2 revised on
14 October: held presses begin 137 ms before the visible shift, wall throws lift moving; second
revision: the finger leads the ink by 33 ms through throws, and a stationary hold lifts where the
iOS bar starts to return, read from a row dump of the recording when `v2-row495.gray` and
`v2-pts.txt` are present). The rebuilt reversal episode ends with a lift while the finger is
still sweeping toward Calls (22.288 s): the iOS bar is back at rest by 22.40 s and the lens
darkens on a throw's timing. The
generator splices the held-reversal episode (18.7–22.4 s) from `reversal-script.txt`, rebuilt from the episode's turning points because the ink centroid snaps
between tabs mid-sweep. They hold only finger times and x positions; **the timing is inferred, not recorded**.
On 15 October the rightward moves of the reversal's first second (18.7-19.6 s) were moved 20 ms
earlier, so the replayed lens reaches each icon when iOS's does (scored on the times the selected
ink first reaches each icon).
Script format: `select <tab>`, then `<ms> down|move|up <x> <y>` in scene pixels at 3 px/pt, and
`end <ms>`.

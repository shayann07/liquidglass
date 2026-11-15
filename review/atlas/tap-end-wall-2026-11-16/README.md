# Calm taps meet an end wall on the first and last tab (part of #70) — 16 November 2026

Source revision: branch `fix/70-leading-end-wall` (code at `273bfb56`) on main `0114b379`. Backend: desktop JVM, Skia GPU
render (`:desktop:phoneReplay`, `calm`, the full docs/1 finger script `script3.txt`, 32 s). Main is the render of
`0114b379`; this change is the final render of the branch. Both were scored against the iOS recording of docs/1.
That recording is private: only numbers measured from it appear here, no frames. Nothing here was captured on a phone.

## What changed

A Calm tap that lands on the first or last tab now meets an end wall. Its leading end may reach at most
`tapEndWallReach` = 0.03 bar heights past its resting end; the excess is taken out of the spine about the centre through
a soft knee (`tapEndWallKnee`, 0.025 bar heights). Before, the lens pressed about 10 px past the bar's end for three or
four frames. The tap's transit appearance reads a shadow spine without the wall, so the pigment timing does not move.
The take is per 1/960 s step and scaled for shorter steps, and the tap radius is re-derived from area after the wall,
so the result does not depend on frame rate. The wall is fitted on two-slot trips only (the recordings' bars have three
tabs) and fades out by three slots. Mirrored in the Skia and .NET Calm selectors. No public API change. See
[the tap-bar guide](../../../docs/tab-bar.md).

## Lens end, ten 0 to 2 taps (docs/1)

Luma of the lens at rows 500-590, at x 868 / 876 / 884 / 892 / 900 / 908 (the bar's end is near x 896):

| ms | iOS | main | this change |
| :-- | :-- | :-- | :-- |
| 163 | 38 / 42 / 43 / 30 / 0 / 0 | 43 / 43 / 43 / 38 / 20 / 0 | 43 / 42 / 41 / 27 / 0 / 0 |
| 197 | 37 / 37 / 36 / 17 / 0 / 0 | 38 / 38 / 38 / 31 / 10 / 0 | 38 / 37 / 36 / 25 / 0 / 0 |
| 230 | 32 / 31 / 30 / 15 / 0 / 0 | 34 / 33 / 34 / 21 / 0 / 0 | 34 / 33 / 36 / 24 / 0 / 0 |

Main's lens runs 8-10 px past iOS's end at 163-197 ms (x 900 reads 20 and 10 where iOS reads 0); with the wall it reads
0 at both, and x 892 moves toward iOS's.

## The travelling pill's level, per tap class (docs/1)

Median level at rows 470-490 (`split70.py`), by tap class. These rows read the destination's ink as much as the pill's
own pigment.

| ms | 133 | 150 | 167 | 183 | 200 | 217 |
| :-- | --: | --: | --: | --: | --: | --: |
| 0 to 2 (10 taps), iOS | 50.5 | 89.5 | 114.3 | 105.7 | 85.7 | 63.5 |
| 0 to 2, main | 47.3 | 69.6 | 74.5 | 42.0 | 41.0 | 40.0 |
| 0 to 2, this change | 47.7 | 82.6 | 114.9 | 38.0 | 37.0 | 36.0 |
| 2 to 0 (11 taps), iOS | 54.1 | 53.1 | 55.3 | 56.0 | 50.0 | 45.0 |
| 2 to 0, main | 60.2 | 59.7 | 66.1 | 49.9 | 49.5 | 47.9 |
| 2 to 0, this change | 61.2 | 57.4 | 55.4 | 50.9 | 49.2 | 46.2 |

2 to 0 at 167 ms falls from 66.1 to 55.4 (iOS 55.3), and 0 to 2 at 150-167 ms rises to 82.6 and 114.9 (iOS 89.5, 114.3).

**A regression, stated plainly:** on 0 to 2 at 183-217 ms this change reads 4 levels further from iOS than main
(38 / 37 / 36 against 42 / 41 / 40; iOS 105.7 / 85.7 / 63.5). Both are far from iOS there. That remainder is the
destination ink's magnification timing, tracked in #94 (item 23), not the wall.

One-tab taps (0 to 1, 1 to 0, 1 to 2, 2 to 1; 16 taps) are identical to main at every sampled time.

## Arrival

`taparrive.py`, when the selector comes within 20 px of the destination tab, ours minus iOS, median:

| recording | main | this change |
| :-- | --: | --: |
| docs/1 two-tab | +6 ms | +6 ms |
| docs/1 one-tab | unchanged | unchanged (identical tap for tap) |
| JGEY2190 two-tab (main render `m1115`, this change `w70f`) | +65 ms | +65 ms |
| JGEY2190 one-tab | +182 ms | +182 ms |

The tap's transit appearance is equal to main at every sampled frame from 100 to 300 ms on two-tab taps.

## Rejected: moving only the leading end

A one-sided wall (half the take from the spine, half from the centre; still in the spec as `tapEndWallOneSided`,
off in Calm) held the end but arrived 23 ms later than iOS in the median where main is 6 ms late, and the 2 to 0 level
at 167 ms rose to 75.3 (iOS 55.3). The shipped wall shortens the spine about the centre.

## Provenance

- **Measured:** every number in the tables above (iOS from the decoded recording; main and this change from our
  replays).
- **Authored:** `tapEndWallReach` 0.03 and `tapEndWallKnee` 0.025 bar heights, and the two-slot fade. The reach was
  taken midway between two rendered candidates (0.02 scored best on the 2 to 0 level, 0.04 on the 0 to 2 level; both pass the lens end), then rendered and scored itself, which is the table above; it is not a
  fitted constant.
- **Tests:** `GlassPhoneTapTimingTest` gains three: the wall holds the landing edge on both end tabs and leaves the
  appearance alone; the same peak at 30 / 60 / 90 / 120 / 240 Hz within 0.5 px; a middle-tab tap is step for step
  identical with the wall on and off. Focused Kotlin suite green, `ports/skia` `npm test` 46/46 at the unchanged 0.05 px
  tolerance, .NET checks 16/16. The full `jvmTest` suite is CI's gate.

## Limits

- Desktop replay only; no Pixel look at a tap to the last tab.
- Fitted on two-slot trips; no recording of a bar with four or more tabs being tapped across existed at the time, so
  longer trips are not checked (the wall fades out by three slots).
- JGEY2190's per-class levels were not scored; only its arrival.
- The 0 to 2 level after 183 ms stays far from iOS (see the regression above); #70 stays open for it and for #94.

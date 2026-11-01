# Bar press swell onset (#106, audit item 107)

Source: `GlassPoseSpec.Calm` on branch `fix/106-bar-swell-onset` (on top of the #106 press/release rework).
Backend: the desktop GPU replay (`:desktop:phoneReplay`, variant `calm`). Script: IMG_6756's three still holds
(holds-41) and JGEY2190's short taps. iOS values come from the private recordings, read with the same metric; no
iOS frames are published.

Metric: the bar's top edge, as the first scene row brighter than 22, over x 590-610 (away from the lens column).
Rest is 453. A smaller row means a taller bar.

## Still press (first hold, 34.628 s)

| after touch-down | iOS | before (press at touch-down) | hold-gated | 75 ms frame-clock delay (chosen) |
| :-- | :-- | :-- | :-- | :-- |
| 53 ms | 453 | 452 | 453 | 453 |
| 120 ms | 452 | 451 | 453 | 453 |
| 137 ms | 451 | 450 | 453 | 452 |
| 170 ms | 450 | 450 | 452 | 450 |
| 187 ms | 449 | 449 | 451 | 449 |
| 220 ms | 448 | 449 | 449 | 448 |

What each column does:
- **Before:** the bar pressed at touch-down, about 65 ms ahead of iOS, and rose slowly (448 only after 300 ms).
- **Hold-gated:** waiting for the controller's hold landed about 50 ms late.
- **Chosen:** the bar's press waits 75 ms of frame time (`barPressDelaySeconds`; a drag presses at once). It then
  rises on a critically damped spring of stiffness 1700 (`barPressStiffness`), within a frame of iOS throughout.

Both values are authored. The onset and the rise they reproduce are measured.

A first attempt waited with a coroutine `delay`. That counts wall time, which a replay (and any slow frame) runs
past in a few frames, so the gate waits on `withFrameNanos` instead.

## Tap

On a tap (JGEY2190 4.602 s, 60 ms down) iOS's bar does not swell; its top dips about 4 px and recovers. With the
delay, a tap shorter than 75 ms never presses the bar. Measured at x 600 over 4.57-4.89 s: main swells to
452, the change stays at 453, and iOS dips to 456-457 and recovers. That contraction on a tap is not modelled
and remains open.

Not physically qualified on a device.

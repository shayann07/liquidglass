# Interactive glass presses like the search button (#196) - 18 November 2026

Source revision: branch `p3/114` (child of `fix/202-rim` at `75443671`), the #196 commit. Backend: desktop Skiko, the
library's `jvmTest` render path through the production `Modifier.liquidGlass`, density 1. Capture method:
`GlassFluidEvidenceTest.framesBeforeAndAfter`, a manual clock, one pointer down, 600 ms, a 40 px upward move, 600 ms, up, 100 ms.
Own renders only; no iOS recording frame is in this folder.

Each control is a dark Regular glass over a striped page, drawn twice: `GlassInteraction.Calm` (what `interactive()` did
before) and `GlassInteraction.Fluid` (what it does now). Frames per control and response: `rest`, `held` (pressed, still),
`pulled` (held after the move) and `released-100ms`. Files are named `<control>-<response>-<state>.png`: `button56`
(56 dp circle), `chip120x48` (120 x 48 dp pill) and `card200x150` (200 x 150 dp card).

## Provenance

- Measured on iOS 26's Phone search button (IMG_6756, seven presses): the 8 dp-a-side press, the follow and stretch, the far edge that
  holds on landing, the ring on release, the glow that settles to 0.65 once dragged. `searchscore.py` on a replay of that
  script is identical before and after this change to the printed digit (the 62 dp search button is below 80 dp, so every value
  is unchanged); `.local/sxs/p114/score-m114.txt` and `score-c196.txt`.
- Inherited: the same response on the other small controls (no recording shows other glass pulled; the keypad key K01 shows the
  press alone, +8 pt a side).
- Authored: everything about size (full response to 80 dp, a smooth blend to 160 dp, from 160 dp a 2 dp press, no travel, at
  most 2 dp stretch), and the scroll cancel (critically damped return, no ring), and Reduce Motion (no scale, no pull).

## Limits

Desktop render only; not checked on a device. The press is a manual-clock snapshot, so the spring overshoot is not in these frames.
The card is drawn at the bottom edge of the 300 px scene. A pulled card does not leave its place by design.

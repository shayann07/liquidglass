# Held lens ink lift (#105, audit item 103)

Source: `GlassPoseSpec.Calm` on branch `fix/105-held-ink-lift`. Backend: the desktop GPU replay (`:desktop:phoneReplay`,
variant `calm`), IMG_6756's first still hold on Keypad (34.628-36.140 s). iOS values come from the private recording,
read with the same metric; no iOS frames are published.

Metric: the median RGB of the Keypad glyph's blue-dominant pixels (scene rows 474-560, x 700-790).

| time | iOS | before | lift 20/255, 150 ms rise | lift 22/255, 80 ms rise (chosen) |
| :-- | :-- | :-- | :-- | :-- |
| rest (34.40 s) | (6,151,255) | (8,151,255) | (8,151,255) | (8,151,255) |
| held .17 s | (20,171,255) | (8,151,255) | (11,154,255) | (22,165,255) |
| held .37 s | (28,176,255) | (8,151,255) | (28,171,255) | (30,173,255) |
| held 1.3 s | (24,174,255) | (8,151,255) | (28,171,255) | (30,173,255) |
| lift +.11 s | (25,163,255) | (8,151,255) | (24,167,255) | (25,168,255) |
| lift +.36 s | (8,155,255) | (8,151,255) | (8,151,255) | (8,151,255) |

iOS adds light to the ink under a held lens: +18-23 levels in red and green, with blue already at 255. The other
recordings agree: LOLL8185 21.6 s (28,176,255), JGEY2190 5.0 s (38,185,255), docs/2 (41,186,255); light IMG_7048
40 s (0,132,250) to (23,146,255). The lift lets go over about 0.35 s after the lift.

Ours adds `heldInkLift` to every channel of the ink seen through the lens, with a lighting colour filter, which
leaves transparent pixels transparent. It rises within 80 ms of the hold and falls over 350 ms. The level and
both times are authored against this table.

Not physically qualified on a device.

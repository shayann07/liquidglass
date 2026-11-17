# Tab bar tone on the real draw path (#203, PR A)

Source: `feat/203a-tone-table` on main fc1abf41. Backend: Skia, Direct3D on Windows, through `PhoneReplay` (the real
`GlassTabBar` inside `PhoneScene`, rendered on the GPU, 60 fps virtual clock, nothing touched). Measurement only: no
library change; the knob is replay and sample-scene code.

## Reproduce

```
# one replay per style steps through the backdrop ladder (500 ms per level)
printf 'select 2\nend 4000\n' > flat.txt
gradle :desktop:phoneReplay -Patlas.replay.script=flat.txt -Patlas.replay.out=out-calm \
  -Patlas.replay.variant='calm+flat=0;10;23;28;128;184;199;255'
python tools/tab_bar_tone.py Calm=out-calm CalmClear=out-calm-clear ...
python tools/tab_bar_tone.py --check review/atlas/tab-bar-tone-2026-11-18/tone.json Calm=out-calm ...   # baseline pin
```

Variants: `calm`, `calm-clear`, `measured`, `v3`, `calm-recorded` (CalmRecordedSpace), each with `+flat=...`; `+barlab` lays
the public `GlassStyle.toolbar(dark)` (the #200 lab material) on the bar and keeps Calm's `restMap`, `capFold`, `specular`,
`bevelPeak`, `edgeFold`, `softDetail`. `+flat=<levels>[+flatstep=<ms>]`: levels separated by `;`, each a grey `128` or
`r,g,b`; `flat.csv` in the output lists the level per frame.

## What is measured

Median Rec.601 luma of the display sRGB bytes, bar interior rows 462-482 (above the icons, three patches between them),
frame taken five frames before the end of each level. The pill is the selected Keypad pill, rows 470-482. Flat backdrops are
exact (spread of the bar patches is small, see `tone.json` `bar_spread`). Provenance: **measured** on our render; the iOS
column is **measured** on the recordings (plans/203, `docs/1`, JGEY2190), not re-measured here.

## Table (bar interior, dark styles)

| backdrop | iOS App Store / WhatsApp bar | Calm | CalmClear | Measured | V3 | CalmRecordedSpace | Calm + barlab |
| --: | --: | --: | --: | --: | --: | --: | --: |
| 0 | 32 | 32 | 32 | 35 | 32 | 45 | 32 |
| 10 | 43-47 | 38 | 41 | 41 | 38 | 51 | 42 |
| 23 | 54-55.4 | 46 | 53 | 49 | 46 | 59 | 54 |
| 28 | 57-60 | 49 | 57 | 52 | 49 | 62 | 59 |
| 128 | none | 108 | 147 | 111 | 108 | 121 | 134 |
| 184 | 157.6 (183.7 blue card) | 141 | 197 | 144 | 141 | 154 | 163 |
| 199 | 166.6 (199.1 orange card) | 149-150 | 211 | 153 | 150 | 163 | 169 |
| 255 | none | 183 | 255 | 186 | 183 | 196 | 185 |

Lab prediction from plans/203 (`T(b) = 31.72 + 254.40 x - 101.01 x^2`): 31.7, 41.5, 53.8, 58.4, 134, 162.6, 168.7, 185. The
real bar with `+barlab` lands within 1 level of every one. `Calm` and `CalmClear` reproduce the "today" columns of
plans/203 exactly (`Calm` 32/38/46/49/108/141/149/183; `CalmClear` 32/41/53/57/147/197/211/255), so the panel-shader test
helpers and the container-shader bar agree on tone. The pinned `V3` row equals `Calm` (same bar); `Measured` is 3 above
(`toolbarEarlier` 35/255 lift); `CalmRecordedSpace` is the pre-15-October recorded space (about 13 above).

## Card colours (per channel; `cards.md`, `cards.json`)

| backdrop | iOS | Calm | CalmClear | Calm + barlab |
| --: | --: | --: | --: | --: |
| 145,192,242 (luma 183.6) | 116,167,219 (157.6) | 118,146,175 (140.9) | 162,205,250 (197.3) | 122,170,220 (161.3) |
| 242,194,113 (luma 199.1) | 209,162,79 (166.6) | 175,147,99 (149.9) | 250,206,134 (210.9) | 211,164,85 (169.0) |

Against the 3.2 tolerances (luma +-6, channel +-8): `+barlab` passes both cards (blue luma +3.7, channels +6/+3/+1;
orange +2.4, channels +2/+2/+6). `CalmClear` fails both (luma +39.7 and +44.3). `Calm` fails both (-16.7 and -16.7; blue's
G is 21 low, B 44 low). These are flat-colour renders, not a recording (no recording covers the cards).

## Selected pill (`tone.md`)

| backdrop | 0 | 10 | 23 | 28 | 128 | 184 | 199 | 255 |
| :-- | --: | --: | --: | --: | --: | --: | --: | --: |
| Calm | 10 | 16 | 24 | 27 | 86 | 119 | 128 | 161 |
| CalmClear | 10 | 19 | 31 | 35 | 125 | 175 | 189 | 233 |
| Calm + barlab | 10 | 20 | 32 | 36 | 112 | 140 | 146 | 163 |

iOS pill reads (plans/203): 10 on black, 19 / 30 for CalmClear's pages 10 / 28. The `+barlab` pill is the bar minus a fixed
lift, so it follows the bar; the pill gate (section 2 of the plan) is a later PR's job.

## Files and limits

`tone-sheet.png`: bar crop of each replay at each level (columns 0, 10, 23, 28, 128, 184, 199, 255; rows as the table).
`tone.json` is the baseline `--check` pins (tolerance 0.5 level). Limits: Windows Direct3D render, not a device; one
selected tab; flat pages carry no text under the bar (the detail terms are not exercised); `+barlab` is a candidate, not a
shipped style; the plan's `+barshade` (inner band) is not built here, it needs a library shader change or a new knob in
PR B.

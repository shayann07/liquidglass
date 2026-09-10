# Measurements from an iOS 26 device recording

Everything else in this directory is derivation. This is measurement.

The source is a 41.7-second screen recording of the iOS Phone app's tab bar being pressed and
dragged, 720x1558 at ~30fps, in dark appearance, supplied by the app's author. It is the only
first-hand observation of the real material anywhere in this project, so where it disagrees with
a number in [parameters.md](parameters.md), it wins.

**Scale.** The bar is 115 px tall in the video. A standard iOS tab bar is 62.9 pt, so
**1 pt = 1.83 video px**. On the Galaxy S24+ used for comparison (density 3.5) one point of the
reference is 1.91 device px.

**Method.** All 1,250 frames were cropped to the bar strip and differenced to find the frames
where motion starts and stops. Luminance is Rec. 709 from the video's RGB, sampled along single
columns and rows rather than over blocks, because every interesting feature here is two to
fifteen pixels wide and a block average erases it. Frame 165 is the resting state with pure
black behind the bar; frame 240 is a press held on the first tab; frame 300 is mid-drag,
straddling two tabs.

---

## The bar

| | |
| :--- | :--- |
| Height | 115 px = 62.9 pt |
| Body over pure black | **20 / 255** |
| Peaks of white text behind it | pass at **64%** of their own brightness |
| Top and bottom edge | a **one-pixel hairline at 61**, i.e. 3x the body |
| Left and right edges | **no line at all** — the body simply ends |
| Corner | a true capsule: radius = half the height |

Two numbers, two unknowns. A material that adds 20 over black and passes 64% is a tint of about
**0x37 at a strength of 0.36** — not the reading the appearance first suggests, which is a dark
tint at high alpha. That version passes only a quarter of what is behind it and turns the bar
into a wall. It is the single easiest way to get a dark-mode bar wrong.

The edge asymmetry is equally specific and equally easy to miss: **lit top and bottom, unlit
sides**. That is a light from directly overhead reaching both horizontal faces equally and
neither vertical one — not a stroked border, which would run all the way round, and not the
default key-plus-weak-counter lighting, which would leave the bottom dimmer than the top.

## The resting indicator

| | |
| :--- | :--- |
| Size | one tab wide to the pixel; 102 px = 56 pt tall, 0.89 of the bar |
| Body over black | **flat 6**, against the bar's 20 — it passes about a third of what the bar does |
| Edge | steps from 20 to 6 in **two or three pixels** |
| Refraction band | **none** |
| Mirrored echo, colour fringe, lit bevel | **none, none, none** |

The resting indicator is not glass. It is a plain dark inset cut into the bar, and the vertical
profile is flat to within one count across its whole height.

This matters more than it sounds. Give it even a shallow refraction and its rim grows a bright
ring — the bar's own body, compressed inward — and that ring is the most obvious tell that an
implementation is a recreation. Apple's own rule for sliders and toggles is the explanation: the
knob "becomes Liquid Glass for the duration of the gesture and reverts after". The tab bar's
indicator does the same, and the reverted state is a shape, not a material.

## The held lens

| | |
| :--- | :--- |
| Size | 150 px = 82 pt tall (**1.30x the bar**), ~130 pt wide (**1.43 tabs**) |
| Position | centred **5.2 pt above** the bar's centre line; stands 14.8 pt proud above, 4.4 pt below |
| Interior where it overlaps the bar | **42, where the bar beside it reads 41** — it passes the bar through |
| Interior where it stands proud, over black | **0** |
| Rim, top | a bead: `0 16 32 53 78 101 107 110 102 78 46 20 4 0` — 14 px wide, peaking **+110** |
| Rim, bottom | peaks **+73** |
| Rim, sides | peaks **+5** over the bar, after a dip of **-17** |
| Formation | fully formed within **2 frames** (≤66 ms) of touch-down, before any drag |
| Release | subsides over **10–14 frames** (330–460 ms) |
| Symbols crossing it | fringe visibly red and blue at the rim |

Three things in that table are worth stating as claims rather than numbers.

**The lens is clear.** Over black it adds nothing at all. Over the bar it adds nothing either —
it passes what the bar already lifted, because on iOS the lens sits *above* the bar and the light
reaching the eye has been through both.

**Its rim is lit on all four sides, but very unevenly.** 110 at the top, 73 at the bottom, 5 at
the sides. The sides are the ones that catch an implementation out: with a light from overhead,
both the key lobe and its counter-lobe vanish where the surface normal points sideways, so the
only term that can light them is an omnidirectional one. Leave it out and the capsule reads as a
black jellybean with a highlight on top; turn it up and the capsule grows a halo.

**Its highlight sits inside the edge, not on it.** The outermost pixel is dark, the peak is about
seven points in, and it falls away again toward the interior. That is a bead, not a chamfer, and
it is what gives the rim its thickness.

## What could not be established

Whether the lens magnifies. Comparing glyph sizes inside and outside it is confounded by the
selected tab also changing colour, and the list text behind it moves between frames. The
straightforward reading of the profiles is that it does not magnify measurably, and the app's
implementation assumes none, but this is not a measurement and should not be quoted as one.

## Applying these to a host whose lens cannot sample the bar

This library cannot sample glass with glass — see [divergences.md](divergences.md) §1 — so a
selection lens samples the app's content directly and never sees the bar it sits in. Left clear,
it shows the un-lifted ground and reads *darker* than its surroundings, which is exactly the
failure the reference rules out. Giving the lens the bar's own tint reproduces the overlap
exactly and costs only a small lift where the lens stands proud of the bar. The lens still reads
as the clearer window, because it does not carry the bar's blur.

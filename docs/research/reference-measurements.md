# Measurements from iOS 26 device captures

Everything else in this directory is derivation. This is measurement.

Two sources, both supplied by the app's author from his own iPhone, both of the Phone app's tab
bar in dark appearance:

1. **Nine native screenshots** (1170×2532, exactly 3 px/pt), taken with a finger on the bar at
   various positions, plus three of Slack's tab bar for comparison. Uncompressed, unscaled. This
   is the primary source.
2. **A 41.7-second screen recording** (720×1558, ~30fps, H.264), of the same bar being pressed
   and dragged. Compressed and scaled; useful for timing and motion, and for the resting state,
   which the screenshots do not include. Where its numbers disagree with the screenshots, the
   screenshots win — and they do disagree in two places, noted below.

**Scale.** In the screenshots the bar is 193 px tall between its hairlines: **64.3 pt**, so
1 pt = 3 px exactly. In the video it is 115 px, which at the same 64 pt gives 1 pt ≈ 1.8 video
px. On the Galaxy S24+ used for comparison (density 3.5) one point is 3.5 device px at the app's
62 dp bar, which is within 4% of the reference's height.

**Method.** Luminance is Rec. 709 from RGB, sampled along single columns and rows rather than
over blocks, because every interesting feature here is one to twenty pixels wide and a block
average erases it. Video frames were extracted with ffmpeg; all 1,250 were differenced to find
the gestures.

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
knob "transforms into Liquid Glass during interaction" ([Adopting Liquid
Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass)). The
tab bar's indicator does the same, and the reverted state is a shape, not a material.

## The held lens

Measured off the native screenshots (IMG_6614–6618), with the video agreeing on timing:

| | |
| :--- | :--- |
| Size | 219 px = **73 pt** tall on a 64 pt bar (**1.135×**); about 125 pt wide, one item plus **16 pt** |
| Position | **centred** on the bar: stands **4.3 pt proud above and 4.3 pt below** |
| Interior where it overlaps the bar | **52–58, where the bar beside it reads 47–48** — the bar passed through, plus a faint glow that peaks in the middle |
| Edge | a **one-to-two-pixel line at +85 on top and +68 below**; a **one-pixel dark step** at the sides, reading 24 against an interior of 41, and nothing else |
| Refraction | **outward**: the bar's top hairline appears **11 px (3.7 pt) lower** inside the lens than outside it, the bottom hairline 9–11 px higher, and the black gap around the bar becomes a **~20 px dark band** just inside the lens's edge |
| Dispersion | strong: symbols and labels crossing the rim split visibly into red and blue |
| Bead, mirrored echo, inner shadow | **none** |
| Formation (video) | fully formed within **2 frames** (≤66 ms) of touch-down, before any drag |
| Release (video) | subsides over **10–14 frames** (330–460 ms) |

Four things in that table are worth stating as claims rather than numbers.

**The lens looks through the bar.** It sits above it on iOS, so what reaches the eye has already
been through the bar's frost: its interior reads as the bar does, the content behind the bar is
no more visible through the lens than through the bar, and — decisively — the bar's own hairlines
appear *inside* the lens, displaced toward its centre. A lens that sampled the content directly
would show a sharp hole through the bar. The reference never does.

**Its refraction is outward.** The displaced hairlines and the dark band settle the direction the
research derived and two published reimplementations contradict: pixels near the rim show what
lies *outside* the lens, compressed inward. Inward (magnifying) sampling would move the hairlines
the other way and could not produce the band.

**Its edge line is entirely directional, and the sides are dark.** There is no floor: where the
normal is perpendicular to an overhead light the rim carries no line at all, and what defines
the shape there is a dark contour just inside the boundary. This was implemented as
`GlassStyle.edgeShadow` and measured back on device at a dip to 21 against an interior of 42,
against the reference's 24 against 41. An independent per-angle reading of the iOS 26 Control
Center, published by another implementation, reaches the same conclusion from a different app:
no direction-independent term, and no fixed outline round the shape.

**Its edge is a line, not a bead.** The outermost one or two pixels are bright; the next twenty are
the compressed exterior (black, when the exterior is the gap around the bar); then the pulled-in
hairline; then the interior. An earlier pass fitted a soft bead peaking seven points inside the
edge from the video. That bead was this line, band and hairline blurred together by compression.

**It stands proud by the same amount above and below.** The video read 13 pt above and 4 pt
below. The screenshots read 4.3 and 4.3. The screenshots are 3 px/pt and unscaled; the video is
neither. The app follows the screenshots.

## The bar, revisited

The screenshots put the bar's body at **43 over black** where the video put it at **20**, with
text behind it visibly softer and dimmer than the video suggested. The two captures may come from
different builds or from iOS 26.1's Clear/Tinted preference; this project cannot tell. The
library's `DarkChrome` keeps the video's two-measurement fit (lift 20, 64% of text peaks) because
it is the only pair that pins both unknowns; a host wanting the screenshots' heavier look should
raise the tint toward 0x78 at the same strength.

## What could not be established

Whether the lens magnifies. Comparing glyph sizes inside and outside it is confounded by the
selected tab also changing colour, and the list text behind it moves between frames. The
hairline displacement says the rim samples outward, which is compression, not magnification; the
app assumes none.

Why the two sources disagree about the bar's lift and the lens's vertical offset. Different OS
builds, different device classes, or a different Liquid Glass preference are all possible.

## Applying these to a host whose lens cannot sample the bar directly

This library cannot sample glass with glass in one pass — see [divergences.md](divergences.md)
§1 — so `GlassTabBar` records the bar with a second `liquidGlassSource` and hands it to the lens
as the glass it looks `through`. That reproduces the interior, the pulled-in hairlines and the
dark band directly, at the cost of one extra layer per frame. Before that existed, the lens
sampled the app's content and punched a clear hole through the bar, which is the single visible
difference the author pointed to.

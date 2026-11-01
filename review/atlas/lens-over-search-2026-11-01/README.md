# Held lens reaching into the search button (#107) — 1 November 2026

![Desktop GPU replay of IMG_6756 at 35.5 s, the lens held on Keypad beside the search button: on main the lens stops short of the bar's end, with a fringed neck and a black gap before the button; with this change it leans and grows toward the button and runs into it](lens-over-search.png)

Our frames only: desktop JVM GPU replay (`:desktop:phoneReplay`, `calm`, IMG_6756's script from 34.0 s, a still hold on Keypad),
at 1.5 s into the clip (35.5 s in the recording). Scene px 520-1100 × 440-660. Main is above; this change is below. iOS's
frames are measured, not reproduced.

## Measured

Lens edges by scene row, iOS at 35.5 s against our replays:

| | left edge (row 546) | right end (rows 500 / 590) | between the lens and the button |
| :-- | --: | --: | :-- |
| iOS | 605 | 907 | the lens's outline crosses over the button's edge |
| main | 585 | 900 | a black gap 900-930 and a neck at the centre row |
| this change | 602 | 910 | the lens runs on under the button: no gap, no neck |

iOS's held lens beside the search button is about 350 px wide (603-955), against ours 320 at the same place. It leans
toward the button and grows into it.

## The change

Only beside an accessory that yields to the lens (`GlassTabBarAccessory.yieldsToLens`, the existing opt-in), and only as
the lens forms on the tab next to it:

- the drawn lens leans toward the accessory by 5.7 dp (17 px at 3x; measured) and grows by 10.7 dp (32 px; measured) at
  that end. Navigation, touch and selection keep the controller's frame;
- the sample drops its lens-to-button neck (`lensFusion`) in both appearances, since iOS overlaps rather than necks
  (IMG_7048 38.5 and 42 s in light as well).

No public API change.

## Not done

iOS draws its lens over the button, and the button shows through the lens, because the lens refracts the button's glass.
Ours keeps the button above the lens, since our lens samples the page and the bar but not the button: drawn on top, it
covered the button and its glyph. Drawing the lens's outline across the button needs the lens to sample the accessory's
glass, which is a separate change. Not yet checked on a device.

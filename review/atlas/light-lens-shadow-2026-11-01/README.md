# Light held lens shadow (#104 item 50) — 1 November 2026

![Desktop GPU replay of the light Calm bar with the lens held on Contacts, levels 225-255 stretched: main's shadow below the lens is short and ends close under it; with this change it sits lower and runs longer, as iOS's does](lens-shadow.png)

Our frames only: desktop JVM GPU replay (`:desktop:phoneReplay`, `calm-light`, IMG_7049's script from 36.0 s, the lens held on
Contacts) at 3.64 s (39.64 s in the recording). Scene px 280-720 × 560-700, levels 225-255 stretched so that a shadow a few
levels deep shows. Main is above; this change is below. iOS's frames are measured, not reproduced.

## Measured

Luma down the lens's lower half and the shadow below it (x 427-433 and 527-533), mean of 39.41, 39.64 and 39.87 s:

| row | 648 | 656 | 664 | 668 | 672 | 676 | 680 | 684 | rms 664-684 |
| :-- | --: | --: | --: | --: | --: | --: | --: | --: | --: |
| iOS | 254 | 250 | 245 | 243 | 242 | 244 | 246 | 249 | |
| main | 238 | 238 | 242 | 245 | 248 | 250 | 252 | 253 | 4.9 |
| this change | 237 | 238 | 240 | 242 | 243 | 245 | 247 | 248 | 2.2 |

## The change (light Calm only)

Light `Calm()`'s held lens casts its contact shadow 8.5 dp down with an 8 dp blur, was 5 dp and 6 dp. Fitted to the three
frames above; no public API change; dark Calm unchanged.

## Not changed

Rows 648 and 656 still read 237-238 where iOS reads 250-254: our lens draws a dark line at its lower rim that iOS does not.
That is the outline's geometry, not the shadow, and stays open on #104 and #128. Not yet checked on a device.

# CalmClear's dark bar on the measured material (#203, PR B)

Source: `feat/203b-calmclear` (c32a4a88 + the sample's `PhoneActivity` flat-page extras). Two renders of the same change.

**Desktop:** Skia, Direct3D on Windows, `PhoneReplay` (the real `GlassTabBar` in `PhoneScene`), `calm-clear+flat=...`, read
with `tools/tab_bar_tone.py` (bar interior rows 462-482, median Rec.601 luma).

**Pixel 7:** Android 17, release build, `adb shell am start -n com.wexpa.liquidglass.sample/.PhoneActivity --ez clear true --es flat
<level> --ei select 2`, screenshot, bar interior x 290-370, y 2140-2160 (between the first two tabs, above the icons).
Screenshots are not committed (they carry the phone's status bar). The page-0 Pixel value is a second capture: the first
caught a frame before the scene had drawn (128) and was retaken 5 s after launch.

| page | iOS (measured) | CalmClear desktop | CalmClear Pixel 7 | Calm (unchanged) desktop / Pixel |
| :-- | :-- | :-- | :-- | :-- |
| 0 | 32 | 32 | 32 | 32 / 32 |
| 10 | 43 (43-47) | 42 | 42 | 38 / 38 |
| 23 | 54 (54-55.4) | 54 | 54 | 46 / 46 |
| 28 | 58 (57-60) | 59 | 58 | 49 / 49 |
| blue card 145,192,242 | 116,167,219 (157.6) | 122,170,220 (161.3) | 122,169,220 (160.8) | 141 / 118,146,175 (140.9) |
| orange card 242,194,113 | 209,162,79 (166.6) | 211,164,85 (169.0) | 211,164,84 (168.9) | 150 / 175,147,99 (149.9) |

The iOS column is measured on the owner's App Store and WhatsApp recordings (plans and #203). The earlier CalmClear bar read 30-45
levels too bright over the cards (197 and 211). Limits: flat pages only, so no text under the bar. One selected tab. The text band
under the bar (where `legibility` 2 was dropped) is **not yet re-scored**: these flat-page results are no evidence for text behind the bar.

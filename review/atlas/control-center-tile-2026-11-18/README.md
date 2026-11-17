# Control Center tile glass (#206) - 18 November 2026

Source revision: branch `feat/206-control-center` on main `fc1abf41`. Backend: desktop Skia (the library's `jvmTest` render path,
through the production `Modifier.liquidGlass`, 3x). Capture method: `GlassControlCenterTileTest.evidenceSheet`
(`CC206_EVIDENCE_DIR=<dir> ./gradlew :liquidglass:jvmTest --tests "*GlassControlCenterTileTest*"`), one 632 x 143 px wide tile over
a flat page of each measured layer colour, shown at half size. The iOS recording the numbers come from is private
(`CC-over-lab-dark.MP4`), so only measured values appear here, never its frames; the right-hand column is a flat swatch of the
measured tile colour, not a frame.

![Tiles over the measured Control Center layer colours](control-center-tile.png)

Columns: the layer colour G under the tile; `GlassStyle.controlCenterTile()` with `chromaGain` unset (a plain black tint, the
material before #206); the same style at the shipped `chromaGain = 1.58`; the measured tile colour. Last row: the active tile
over red (the measured reading there is 255, 239, 238, a clipped near-white; the swatch drawn is the grey 250 body for reference).

## Provenance

- Measured (`CC-over-lab-dark.MP4`, 58 frames from 4 to 118 s): inactive interior `51.1 + 0.903 G` (46 grey frames), no blur inside
  the tile, chroma about luma multiplied by 1.58 (sd 0.06, 8 hues, n = 16), active body 250 (47 frames).
- Fitted in this change from the same take's active readings: active chroma 0.50 (0.504, sd 0.03, 8 samples, unclipped channels).
- Authored: the 5/255 lift that returns the white active body to 250 under the shader's 0.95 tint cap, the rim and contour
  (`bevel`, `specular`, `edgeLight`, `edgeShadow` 0.06 as `shellIcon`).
- Not measured: light appearance, press and hold, Tint Amount, the active tile's rim, slider fill and thumb, glyph vibrancy.

## Numbers asserted (`GlassControlCenterTileTest`, 0-255)

| Check | Result | Gate |
| :-- | :-- | :-- |
| Inactive interior over grey G 10, 34, 56, 78, 91, 102 | 60, 82, 102, 121, 133, 143 (measured 60, 82, 102, 122, 133, 143), rms 0.41 | max 2, rms 1.5 |
| Eight hues, Y error | at most 0.65 | 3 |
| Eight hues, largest channel error | at most 4 (yellow, blue clipping) | 10 |
| Eight hues, chroma ratio (yellow excluded: blue clips at 0) | 1.559 to 1.588 | 1.58 +/- 0.10 |
| Same tile with `chromaGain` unset, over red | 56 levels off the measured tile | shows the field is what makes it |
| Active over G 10, 56, 102 | 248, 250, 252 | 250 +/- 3 |
| Active over six colours | within 3 levels per unclipped channel | 8 |
| Container member against a lone piece, eight hues and grey | within 2 levels (yellow's clipped blue 5 against 0) | 2 |

Limits: flat pages only, so the lens and rim are not scored here; the radial-flatness, rim-peak and contour gates of the
measurement plan are not asserted because the rim is authored. Tint Amount and appearance have no measurements to compare with.

## Device check (Pixel 7, 18 November)

`ColourTransferActivity --ez cc true` (dark) on a Pixel 7 (Android 17, release build of this branch) against the desktop render of
the same scene at the phone's size (`ColourTransferRenderTest.controlCenterTile`, 411 x 914 dp at density 2.625). Tile interior,
median of a 60 x 20 px patch at each pill's centre:

| band | device | desktop |
| :-- | :-- | :-- |
| red | 255, 14, 14 | 255, 14, 14 |
| orange | 255, 154, 19 | 255, 154, 19 |
| yellow | 255, 255, 44 | 255, 255, 44 |
| green | 29, 255, 29 | 29, 255, 29 |
| cyan | 34, 255, 255 | 34, 255, 255 |
| blue | 39, 39, 255 | 38, 39, 255 |
| magenta | 255, 2, 255 | 255, 3, 255 |
| dark magenta | 229, 26, 229 | 229, 26, 229 |
| grey 128 | 167, 167, 167 | 167, 167, 167 |

Worst channel difference 1 level. Grey 128 reads 167 = 51.1 + 0.903 x 128 (the measured map). The saturated bands clip in both,
so they confirm the device path, not the 1.58 gain (the gain is scored in `GlassControlCenterTileTest` on unclipped colours).
Screenshots are not committed (they carry the phone's status bar). Not checked on device: the active tile, light appearance, press.


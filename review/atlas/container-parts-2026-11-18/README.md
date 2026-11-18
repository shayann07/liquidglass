# Merged glass carries the standalone material's parts (#211)

Crops (632 x 143 px pill plus 10 px, 3x scene, 900 x 480) of the lab scene (`GlassLabScene`, the production modifier and
host through Compose `ImageComposeScene`, Skia raster, jvmTest). `alone-*` is a lone piece, `before-*` the container on
source revision `0a1282fb`, `after-*` the container with this change. Dark in-app glass.

- `*-tint0-dark-stem.png` (historical): Tint 0 over a 30 px black stem on an off-white page, rendered with the earlier fit's
  legibility 0.6 (`before` on `0a1282fb`, `after` on `2d9ff415`, the #211 commit, both before #208). Container before against
  after: 1.15 levels rms over the crop, at most 26 levels (the legibility term raising the tint at the stem's edges). On that
  revision the container's stem profile matched the lone piece within 0.7 levels. Since #208 `inApp` at Tint 0 has legibility 0,
  so this crop no longer shows the current Tint 0 style. The current regression is separate:
  `GlassContainerMaterialTest.aContainerShowsOneSoftCopyOfText` renders Tint 0, 50 and 100 (dark and light, legibility 0 at
  Tint 0) and asserts the container's profile is the lone piece's within 2 levels; the legibility term itself is covered at 0.9
  by `GlassContainerPartsTest.legibilityRaisesTheTintUnderTextInAContainerToo`.
- `*-tint50-dark-split.png`: default tint over a white/black split. Before against after differ by 0.05 levels rms
  (at most 3): the rim band's colour-fringe branch is the only part of the change that reaches the default material.

Not in the images: the absorbing tint, the unrotated soft scatter and the container's Glass light are zero or unchanged
in the default style; they are covered by `GlassContainerPartsTest` (interior within 2 levels of a lone piece over flat
grey 128, flat white and the text page, dark and light; the light's rim ring within 3.5 levels rms).

Limits: a container still differs from a lone piece by 2.8 levels rms over the whole crop after this change (the rim and
junction geometry, the pre-existing 2.97 / 2.44 of the capsule-run test); this is regression evidence on the jvmTest
renderer, not a device capture and not owner acceptance.

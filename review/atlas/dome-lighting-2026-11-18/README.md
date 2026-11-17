# Dome field and lighting (#214)

`before-after.png`: a 480 x 80 px capsule (refraction band 40 px, the inradius, so the dome is fully weighted) over 7 px
stripes, rendered by the jvmTest renderer (`GlassRender`, Skia raster, shader text from `GLASS_SHADER_SOURCE`), shown 2x.
Top: before (the dome normal and the dome's slope also drove lighting). Bottom: after (refraction reads the dome, lighting
reads the true-distance normal and slope). Lighting parameters used: specular 0.6, fresnel 0.6, inner shadow 0.3, edge
shadow 0.25, highlight chroma 0.3 (authored test values, not a style).

`numbers.txt`: over flat grey 128 refraction cannot show, so every difference between before and after is lighting:
worst channel difference 83 levels, 3516 differing channel values. After the change the same comparison against the dome
switched off is exactly 0 (`GlassDomeLightingTest`).

Limits: synthetic parameters on the jvmTest renderer, source revision `0a1282fb` plus this change; no device capture, no
iOS reference. Regression evidence only, not owner acceptance. The change reaches only wide shapes (aspect over 1.5 whose
band approaches the inradius).

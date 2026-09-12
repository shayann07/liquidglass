# How it works

One fragment shader, in the SkSL dialect shared by AGSL (Android 13+) and Skia (Desktop), over a
backdrop the host records for it. This walks the shader in the order it runs.

Since 0.2 the shader carries two optical models and a switch, `GlassStyle.profile`. The default,
`Measured`, is the material fitted from calibration captures of iOS 27 on a real device (see
[the measured model](research/measured-model.md)); every number below in that path is a
measurement. `Legacy` is the 0.1 model, kept for the presets that were tuned against it and
described at the end.

## 0. Geometry — a distance field, not a mask

`d = sdShape(p)`, negative inside. `depth = max(-d, 0)` is distance inward from the rim, and
`u = depth / W` is normalised position across the refraction band: 0 at the rim, 1 at its inner
edge. `W` is `refractionBand`, and when that is unspecified it is **0.6 of the corner radius**:
the measured bevel scales with the corner from a 15 dp icon (band 9 dp) to a 47 dp sheet corner
(band 30 dp), and a sharp-cornered rectangle gets none.

The field's **gradient is the surface normal**, which gives refraction its direction and
specular its angle, and stays correct through corners where a per-edge normal pops. It is
central-differenced at a wide epsilon so the medial axis of a capsule does not leave a seam; see
the container notes below.

## 1. Refraction — an inward fold, measured

Nothing outside the edge is ever sampled. The stripes right below a cover sheet's edge stay
crisp and unshifted in every frame of every capture, so the rim shows content from *inside* the
element, and `foldSource(u)` says which content: a pixel at depth `u` shows backdrop from depth
`s = foldSource(u) · W`.

```
u >= 0.9          s = u                          identity
0.52 <= u < 0.9   s = 0.71 + 0.5 (u - 0.52)      a 2x stretch of 0.71-0.9 W
0.28 <= u < 0.52  s = 0.70                       a stationary seam
u < 0.28          s = 0.71 + 1.29 (0.28 - u)     0.71-1.07 W, upside down
```

Content 0-0.69 W inside the edge is never displayed and content 0.62-0.96 W is displayed twice,
once stretched and once mirrored: that fold is the whole visible signature of the sheet's edge,
and it comes out of one lookup with no separate "mirror" term.

**It is not always there.** The fold forms while a finger drags the surface, past about 117 dp
of pull, and never on a committed animation or on chrome at rest, which show only a slight
inward offset. `lensFormation` blends between the two; it is 0 by default, a host tracking a
drag drives it from the pull ([`GlassMaterial.lensFormation`](api-reference.md)) and animates it
back on release.

## 2. Dispersion

Red shows content 0.07 W deeper than blue, and only in the mirrored zone; the stretched zone has
no colour split at all. That is the rainbow fringe on the sheet's edge and nowhere else.
`dispersion` is that fraction of the band.

## 3. Two kernels — detail and tone

The measured material is not one blur. Across a hard black/white edge inside a navigation pill
the tone follows a kernel about 10 dp wide, while 16 px stripes under the same glass keep a
third of their contrast at the default Tint Amount, which a single 10 dp blur would leave at
nothing. So the backdrop term is

```
B = w · G_fine(backdrop) + (1 - w) · G_wide(backdrop)
```

with the fine sigma under a point (`blurRadius`, a rotating tap disc in the shader), the wide
sigma 10 dp (`wideKernel`, rendered once per frame as a quarter-scale blurred copy of the
padded backdrop in a strip beneath it, sampled with one tap), and `fineShare` w = 0.95 (1 −
t/100)^1.5 against Tint Amount t: 0.95 at Clear, 0.34 at the default, 0 at Tinted.

The fine term is one-dimensional. The phone keeps detail that runs **along** a bar and none at
all across it, so what the shader adds is not a share of a second blurred copy but a high-pass
along the element's long axis: the fine sample minus the backdrop averaged along that axis, five
taps at 0.7 of the wide sigma. Backdrop that does not vary along the bar cancels and is left to
the wide kernel, which is why a boundary crossing a bar leaves no step, on the phone or here.
Backdrop that does not vary across it behaves exactly as the mix above. It applies only where
`wideKernel` is on, so the fold and the held lens are untouched.

## 4. Tint — a blend with a lift

```
out = (1 - a) · B + a · tint + lift
```

Light in-app glass: `tint` is a constant 241/255 grey and `lift` is 0. Dark: `tint` is black and
`lift` is 35/255. The opacity `a` follows Tint Amount from about 0.43 to 0.73 in both
([`GlassMaterial.opacity`](api-reference.md)). System backdrops lift by 142/255 minus 0.864 of
the wide kernel's luma (`liftAdaptivity`), which is what makes Control Center read light over
dark content and dark over light; in-app glass does not adapt that way. Two facts the captures
settled: shell glass (icons, dock, Control Center, cards) ignores the Light/Dark setting and takes
its tone from the wallpaper, and in-app glass follows the app's scheme strongly. The legacy tone
mapping (`adaptivity`) is off on this path.

## 5. Lighting

The bevel is lit, not outlined. A navigation pill's edge over black reads +33 at 2 px, +13 at
4, +8 at 6 and nothing by 16, additive white, so over white in a light appearance it clips away
and there is no line at all; in a dark appearance the same line shows on both sides. There is no
dark inner line: the profile decays smoothly into the interior. The in-app factory sets the
`bevel`, `specular`, `edgeLight` and `highlightChroma` that reproduce that profile; shell roles
carry their own (a Control Center tile's outermost pixels are *unlifted*, a dark contour, which
is `edgeShadow`).

## 6. Legibility, interaction, accessibility

Unchanged from 0.1: the rim and interior samples bracket the backdrop's high frequencies, so
their luma difference raises the tint a little as text scrolls underneath; the touch magnifier
and glow, and Increase Contrast's solid-with-border treatment, sit on top of either optical
model. See [Interaction](interaction.md) and [Accessibility](accessibility.md).

With `refractContent` the element's own content is run through a **second pass** that shares
steps 0 and 1 above — the same field and the same fold — and nothing else, then composited
over the material. A test pins the two passes together: the functions that decide where a pixel
samples from, `foldSource` included, must be identical text in both shaders.

`through` adds a second layer to the padded backdrop — whatever glass sits between this panel
and the content — so a panel on top of other glass refracts that glass rather than seeing past
it. It is how a selection lens sees its bar.

## The legacy profile

At rest the lens lives at the corner arcs only. A pixel in an arc's own quadrant shows the
backdrop from the ring 0.33 R inside — the same ring for the whole outer third, which turns
anything under the corner into concentric arcs — easing to identity by 0.46 R; a pixel along a
straight run shows what is under it, plus a few px of inward offset. The blend between the two
runs over 0.2 R along the edge. `lensFormation` then carries both toward the full fold while a
finger tracks the surface.

`LiquidGlassContainer` runs the same two paths with one difference: a fused body is chrome at
rest, so its lens formation is always 0.

`GlassProfile.Legacy` is the 0.1 material, kept so `Chrome`, `DarkChrome`, `Clear`, `Thick` and
the tab bar styles keep the look they were tuned to: an *outward* Snell deviation through a
superellipse bevel normalised so `refractionDepth` is the peak displacement, a faint dispersion
everywhere in the band, a single interior scatter that tapers to zero at the rim, a soft
mirrored echo (`mirror`) over the outer third, and a tint tone-mapped against the backdrop's
brightness (`adaptivity`). The 0.1 write-up is preserved unedited in
[optical-model.md](research/optical-model.md). The measured captures contradicted its two
central claims — the rim samples inward, and the material is a two-kernel blend rather than a
displacement-first scatter — which is why the default moved.

---

# The host contract

Two obligations, and both fail as *apparent shader bugs*. They were both bugs here first.


The lens a finger raises on a tab bar is a third family (`GlassProfile.Held`). Its outer band —
half of the 0.6 R band — pulls the exterior inward and compresses it: the bar's own edge line
lands a tenth of the radius inside the lens's rim, and text above the bar becomes a thin stripe
along it. A seam then hides the content between the band and the interior, which is shown
where it is; the bar itself has grown 1.05 and lifted under the finger, and the tab under the
lens has grown again about its own centre, which is where the magnified look comes from. The
lens also adds a share of the raw content behind the bar to the bar's output, so it reads a
little clearer than the bar around it.
## The recorded layer must be opaque

A backdrop layer is transparent wherever the app painted nothing — its ground is normally
painted by an ancestor, not by the recorded subtree — and a blur drags that transparency inward.

Using the premultiplied result composites those pixels toward black: a heavy dark halo around
every panel. Dividing the alpha out instead blows partly-covered pixels to white. **Neither is
what a viewer sees.** What a viewer sees is the backdrop over the ground behind it.

So the padded layer is filled with `LiquidGlassState.background` *before* the backdrop is drawn
into it. Once it is opaque by construction, the shader can treat any alpha shortfall as
filtering error at the layer's own boundary and reconstruct through it.

## Samples must be bounded by the backdrop, not by the layer

The padded slice is deliberately larger than the panel, and near a screen edge that margin
hangs off the end of the recorded backdrop. Those pixels are not merely dark; they are the flat
fill. A panel that samples them grows a band of solid colour at the rim.

`sampleBounds` is the intersection of the recorded backdrop with the layer, inset half a pixel
so bilinear filtering stays off both boundaries. It is also guaranteed never to invert — an
inverted rect becomes a `clamp()` with min greater than max, which is undefined and draws a
solid block of nothing.

## Padding

Measured profile: `max(blurRadius, backdropBlur, 2 · backdropSigma, 0.75 · wideKernel)` px on
every side — the lens samples inward only, so only the blurs need a margin. Legacy profile:
`refractionDepth × 1.4 + max(blurRadius, backdropBlur)`, because its bevel reaches outward. Every
pixel of it is re-recorded per frame, so it is sized to what the shader actually reads rather
than rounded up for comfort.

## The wide strip

When the measured profile's `wideKernel` is on, the padded layer is taller by a quarter of its
height: the host records the same padded backdrop again at quarter scale under a Gaussian blur
of a quarter of the sigma and draws it into the strip below the sharp copy. The shader finds it
through `uWideStrip` and `uWideScale`, samples it with one bilinear tap, and returns transparent
there, so the strip is never seen. It costs one small blur per panel per frame.

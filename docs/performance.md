# Performance

## What it actually costs

Measured on a Pixel 7 at 120 Hz, with two glass elements over a live, continuously animating
app, sampling steady state across several windows:

| | janky frames | p90 | p95 |
| :--- | ---: | ---: | ---: |
| shader path enabled | 3.85% | 8ms | 10ms |
| `hasShaders` forced false | 4.96% | 9ms | 13ms |

The same within noise. **The material was free**, on an app whose own idle redraw already
dominated the frame. Adding the mirrored band, the Schlick term and exact-Snell refraction later
did not move it either.

That is one app on one device, so do not take the number — take the method. Which is:

> Before blaming the shader for jank, force `hasShaders = false`, rebuild, and measure again.
> One control run is worth any amount of reasoning about tap counts. It corrected a wrong
> conclusion here.

And: **discard the first `gfxinfo` window after launch.** Startup work lands in it. The first
sample here read 10.23% where three consecutive steady-state windows on the identical build read
3.2–3.9%.

## Where the cost actually is

Not usually the shader arithmetic. In order:

**1. The padded recording.** Every glass element records a padded slice of the backdrop *every
frame*. That is a layer allocation and a draw per element per frame, and it scales linearly with
the number of glass elements. This is the first thing to look at.

**2. `backdropBlur`.** A platform Gaussian over the padded layer, again per element per frame.
`GlassStyle.Chrome` uses 14dp of it.

**3. The in-shader scatter.** Nineteen taps, but only where `blurRadius` is non-zero and only
scaled by depth — at the rim it collapses to one tap. `Chrome` sets it to zero entirely.

**4. Everything else.** The Snell arithmetic, the dispersion, the mirror band, the lighting.
Immeasurable at these sizes.

## If it costs too much

**Use fewer glass elements.** Two or three pieces of chrome is the intended shape of this. It is
not a material to apply to every row in a list — and Apple says the same, for design reasons
that happen to coincide with the performance ones.

**Reach for `Chrome`.** Zero in-shader scatter, a shallow bend, a narrow band.

**Shrink the padding indirectly.** Padding is `refractionDepth × 1.4 + max(blurRadius,
backdropBlur)`, so dropping `refractionDepth` shrinks every recorded layer.

**Do not animate a path shape's size.** The distance field rebuilds on the calling thread when
shape or size changes. See [Shapes](shapes.md).

**Turn off `inversion` if you do not need it.** `rememberBackdropInversion` reads the backdrop
back into a bitmap ten times a second. A permanently dark or permanently light app should leave
it at 0.

**Check your app's idle redraw first.** In this app the material was free and a background
animation was redrawing the whole screen at ~90fps while nothing was happening. Quantising that
animation to whole pixels — it moved 1.5px per second and was invalidating at display rate —
recovered more than the shader ever cost, and let the app reach idle at all.

## What `through` costs

A panel that looks through other glass (`liquidGlass(through = …)`, which `GlassTabBar` uses so
its lens sees the bar) needs that glass recorded into a second layer every frame and composited
into the panel's padded slice before its shader runs. On a Galaxy S24+ at 120 Hz, over 1,291
frames of dragging the tab bar's lens, that moved the drag session from p50 10 ms / p95 14 ms to
**p50 14 ms / p95 22 ms**, with janky frames unchanged at about 1%. It is the price of the lens
reading correctly against the reference; a host that needs it cheaper could record the
intervening glass at half resolution, which this library does not yet do.

## Frame-rate hygiene that has nothing to do with glass

Two lessons from tuning this app that generalise:

- **A "reduce motion" flag must stop the animation, not zero its output.** Substituting zero for
  the drift while leaving the transition running costs exactly as much as the animation did.
- **Quantise slow animations.** Something moving a pixel and a half per second does not need to
  invalidate at 120 Hz. Reading it through a `derivedStateOf` that rounds means the draw is
  invalidated only when a whole pixel actually changes.

## Rendering at reduced resolution

The shader runs once per pixel of a padded layer per panel per frame, so resolution is the one
knob that changes the cost by a large factor rather than a small one. `renderScale` on
`LiquidGlassScene` and `rememberLiquidGlassState` records the layers smaller, tells the shader
smaller lengths, and scales the result back up. Geometry, position and colour are unaffected.

Measured on a Pixel 7, Android 17, scrolling the sample's Material screen — a list of glass
cards over a backdrop that does not scroll, which is the expensive arrangement:

| | Full resolution | `renderScale = 0.7` |
| :--- | ---: | ---: |
| Janky frames | 8.9% | 4.3% |
| 50th percentile | 26 ms | 16 ms |
| 90th percentile | 73 ms | 18 ms |
| 95th percentile | 85 ms | 27 ms |

That is roughly twice the frame budget back. What it costs is the thing this material is best
at. Measured across a panel's rim on the same device, the steepest luminance step the rim
carries falls by about half: 61 to 26, 38 to 20, 77 to 53 on successive rows. The panel's own
outline is unaffected, because that is geometry rather than refracted detail, but the compressed
image of the surroundings that makes the edge read as glass gets softer.

So the default is 1, and it should stay 1 unless a screen is actually missing frames. If it is,
0.7 is the value to try first: it halves the pixel count, and on a busy screen the rim detail it
gives up is competing with everything else for attention anyway. Below about 0.5 the rim stops
reading as a lens at all.

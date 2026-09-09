# Confidence and what would falsify it

> Verbatim output of the `liquid-glass-optical-spec` research run. Preserved unedited;
> see [README](README.md) for what it is and how far to trust it.

---

## What is well-sourced versus inferred

### Verified by compilation, not by argument
The shader in `agslShader` was compiled during this work by Skia's `RuntimeEffect.makeForShader` — the same SkSL front end AGSL uses — inside this repository's existing `:liquidglass:jvmTest` harness, and passes. It uses `half4 main(float2)`, no dynamic loop bounds, no arrays, no `discard`, and returns premultiplied colour. The two profile tables in `parameters` (normalised falloff at p = 2 and p = 4, and the √(n²−1) bound) were computed numerically, not quoted.

### Apple-stated (architecture and direction only — never a number)
High confidence, quoted or paraphrased from WWDC25 219/284/323/356 transcripts, the HIG, and SwiftUI/UIKit reference:
- Lensing is the primary behaviour and is *bending*, in explicit contrast to prior materials that scattered.
- The material samples from an area larger than itself. (This is what settles the outward-vs-inward displacement question.)
- Two-part render: a shape behind the view plus foreground effects over it; anchored to the view's bounds.
- Opacity, shadow depth, lensing strength and scatter softness all scale with element size.
- Small elements flip light/dark with the backdrop; large ones adapt but never flip; symbols flip in lockstep.
- Tint is a tone ramp indexed by backdrop brightness, not a flat colour; a solid fill breaks the material.
- Regular is adaptive; **Clear has no adaptive behaviour at all** and needs a dimming layer.
- Glass cannot sample glass; containers exist to share one sampling region and enforce uniform adaptation.
- `spacing` is an edge-to-edge merge threshold; merging is droplet-like.
- Elements materialize by modulating lensing, not by fading alpha.
- Shadow opacity rises over text and falls over solid light ground; it changes continuously as content scrolls.
- Interaction illuminates from within, starting under the fingertip, spreading to neighbours.
- Reduce Transparency = frostier; Increase Contrast = black/white + border; Reduce Motion = no elasticity.
- Default shape is a capsule; capsule radius = half the height; concentric radius = parent minus padding.
- The API surface is exactly style + `isInteractive` + `tintColor`, with no optical parameter of any kind.

**The one Apple number in the entire corpus is 35 % black dimming under Clear glass over bright content** (the SwiftUI sample's 30 % is the only competing figure). Everything else numeric is ours or third-party.

### Third-party device-measured (medium-high confidence, single source each)
From the one library documenting side-by-side comparison against a real iOS 26 device:
- press lifts luminance by **~6 %** (0.2 reads as a flash);
- chromatic dispersion sits at **0.018** of the displacement — real iOS UI glass shows almost no prismatic fringe;
- the mirrored edge band spans **~0.7 × half-min-dimension** (roughly the outer third per side) with squared falloff — a thin cubed band reads as a hard streak;
- clear glass sits at **1.62×** its backdrop luminance;
- edge refraction ≈ **9 dp regular / 11 dp clear**.

I weighted these above the other reimplementations wherever they conflicted, because they are the only figures claimed to have been compared against the device. They are still one person's eye, one wallpaper, one device.

### Derived (high confidence in the mathematics, no claim about Apple)
- Exact Snell deviation via the tangent-difference identity, and its self-bound at √(n²−1) = 1.118 for n = 1.5.
- The small-angle form `slope·(1 − 1/n)` overshoots by 21 % at the profile peak, 133 % at 80°, ∞ at the rim.
- Schlick's error vs exact Fresnel: −0.019 at 60–65°, +0.036 at 85°, F0 = 0.04 at n = 1.5.
- The superellipse height field and its slope; p = 2 is the circular arc, p = 4 the squircle bevel.
- `k = 2 × spacing` for the polynomial smin. The commonly copied `k := spacing` is off by 2× under this form and 4× under iq's normalised form.
- Beer–Lambert absorption at UI scale is ~0.005 % and cannot produce any visible edge darkening — so the dark contact line, wherever one is wanted, is authored.

### Ours, stated as ours (low external support, tuned by eye or by argument)
Every timing, every spring, every threshold, every intensity: the 0.60/0.44 flip hysteresis and 180 ms crossfade; the 188 dp small/large boundary; the 56→320 dp size ramp; the press springs (0.55/900 down, 0.72/700 up); the 320/240 ms materialize; all four presets' tint alphas and specular values; the light direction; the 0.25 legibility lift; the 0.18 mirror amplitude; the shadow alpha range and cadence; the gel deformation constants. None of these can be validated against Apple, only against a device by eye.

### Where I went against a source, and why
1. **Outward displacement** (against Kyant0 and react-native-liquid-glassmorphism, both of which displace inward). Apple's "samples content from an area larger than itself" only makes sense outward; inward sampling never needs padding.
2. **Displacement magnitude 9–18 dp** (against Kyant0's 24 dp and this repo's current 22 dp). The device-matched end, with a falsifiable failure mode: above ~16 dp over 17 sp text the rim smears legible glyphs.
3. **Dispersion 0.018** (against this repo's 0.06–0.09 and Kyant0's 7-tap spectral reconstruction). Device-matched, and 7 taps is a large fetch cost for a fringe this faint. I also rejected Kyant0's quadrant modulation, which zeroes the fringe on both centre axes — exactly the top and bottom of a pill.
4. **p = 2 default** (against Codrops/kube.io's p = 4 squircle). p = 2 lands between the two independently tuned shipped falloffs and is the physical circular case; p = 4 puts 95 % of the displacement in the outer third of the band.
5. **Per-element flip, not per-pixel** (against this repo's current shader). Apple's requirement that symbols flip in lockstep with the glass is unsatisfiable with a per-pixel decision.
6. **Wide-epsilon gradient** (against this repo's 1 px). A 1 px epsilon leaves a one-pixel medial-axis seam down every pill.
7. **Exact Snell rather than the clamped small-angle shortcut** (against sorrell.info, Codrops and every derivative). It is cheaper in practice (no transcendentals, no clamp) and removes the ad-hoc 0.5 gain.

### What I refuse to claim
This is a faithful implementation of Apple's *published architecture*, tuned to *third-party device measurements*. It is not a reconstruction of Apple's shader, because no such source exists: Apple has published no shader, no kernel, no filter graph, no layer definition and no rendering constant. Every Apple code sample across all four WWDC25 sessions and the whole documentation set is API-adoption Swift. Any spec that presents an Apple-specific refractive index, blur radius, spring constant or luminance threshold is presenting an invention as a citation.

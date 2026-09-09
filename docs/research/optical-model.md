# The optical model

> Verbatim output of the `liquid-glass-optical-spec` research run. Preserved unedited;
> see [README](README.md) for what it is and how far to trust it.

---

## Liquid Glass — one implementable optical model

Scope: one rounded-rect panel of arbitrary per-corner radii, one fragment shader, one padded backdrop bound as `uniform shader content`. Apple has published no shader, no kernel, no filter graph and no numeric rendering parameter for this material; what it has published is architecture and direction-of-effect. Everything below is that architecture, implemented, with the numbers named as ours.

### The one-sentence model

Liquid Glass is **displacement-first, blur-second**: a thin bevel around the rim of a shape bends a *sharp* backdrop inward, compressing the surroundings into a legible band at the edge, while the flat interior scatters and tints. Apple's own framing is the same inversion — "previous materials scattered light, this new set of materials dynamically bends, shapes, and concentrates light" — and it is the single property that separates the material from glassmorphism. Every implementation decision below follows from it.

### Layer order, as the shader runs

**0 — Geometry.** A signed distance field, not a mask. `d = sdRoundRect(p, halfSize, radii)` with per-corner radii; `depth = max(-d, 0)` is distance inward from the rim, `e = depth / uBand` is the normalised position across the refraction band (0 at the rim, 1 at its inner edge). The field's gradient is the surface direction and stays correct through corners, where a per-edge normal pops.

The gradient is central-differenced at a **wide epsilon**, `eps = clamp(uBand * 0.3, 1, 16)` px, not at 1 px. At 1 px the gradient magnitude of an exact rounded-rect SDF collapses from 1 to 0 to 1 within two pixels across the medial axis, leaving a one-pixel line of zero refraction down the middle of every pill. A wide epsilon lets the two sides cancel gradually, and the gradient magnitude that falls out — `conf = |g| / (2·eps)` — is reused as a confidence term (`axisFade = smoothstep(0.15, 0.80, conf)`) that fades the lens toward the axis rather than letting its direction flip.

**1 — Refraction (lensing).** The bevel is a **superellipse height field** `h(e) = T·(1 − (1−e)^p)^(1/p)`, and what the shader needs from it is the slope `dh/ds`, which is 0 in the flat interior and diverges at the rim. `p = 2` is a true circular arc; `p = 4` is the squircle-matched profile. The displacement is the **exact Snell deviation** for that slope, evaluated with the tangent-difference identity so it contains no transcendental at all:

```
sinθ = slope/√(1+slope²), cosθ = 1/√(1+slope²)
sinθ' = sinθ/n,           cosθ' = √(1−sinθ'²)
bendRaw = tan(θ−θ') = (sinθ·cosθ' − cosθ·sinθ')/(cosθ·cosθ' + sinθ·sinθ')
```

This is chosen over the near-universal shortcut `slope·(1 − 1/n)` because that form overestimates by 21 % at the profile's own peak, by 133 % at θ = 80°, and diverges at the rim — which is why every shader that uses it also carries an ad-hoc clamp and an ad-hoc 0.5. The exact form is **self-bounding**: as the slope goes vertical it tends to √(n²−1) = 1.118 at n = 1.5, so no clamp is needed and `n` becomes a *shape* knob rather than a magnitude knob. `bendRaw` is then normalised by its own value at the rim so `uDepth` is literally the peak displacement in pixels, and faded over the inner quarter of the band (`smoothstep(1.0, 0.75, e)`) so the warp field is C1 where it meets the flat interior. That last step matters: a C0 kink in a resampling field tears the backdrop into a visible ring, and it is exactly the artefact one published physically-derived shader reports at the bevel-to-flat junction and notes is "largely avoided" in Apple's version.

**Direction: outward.** The sample point moves *along the outward normal*, `coord + n·bend·uDepth`, so the rim shows a compressed image of what lies just **outside** the element. This is the point of genuine disagreement between implementations — Kyant0 and react-native-liquid-glassmorphism both displace inward, which magnifies the interior instead. I chose outward because Apple's own statement is decisive: the material "is achieved by sampling content from an area larger than itself", and only outward sampling requires an area larger than itself. It is also what forces the host contract in §"Host obligations" below.

**2 — Dispersion.** Red, green and blue sample at `(1−s)`, `1` and `(1+s)` of the same displacement vector, `s = uDispersion · bend`, blue landing furthest out because it carries the higher index. Kept very small (0.018 default): the one implementation calibrated side-by-side against an iOS 26 device reports that real UI glass shows almost no prismatic rainbow and that a larger split reads as a broken colour channel. I did not adopt Kyant0's seven-tap spectral reconstruction (a 7× fetch cost for a fringe this faint) nor its quadrant modulation `(x·y)/(hx·hy)`, which zeroes the fringe on both centre axes — precisely the top and bottom of a pill, where it is most visible on a device.

**3 — Scatter (the blur).** The blur radius is `uBlur · e`: **zero at the rim, full in the interior.** This is the ordering claim, and it is load-bearing. A backdrop blurred before the shader sees it cannot produce the compressed legible edge band, because the detail the rim exists to bend has already been destroyed. The rim/interior crossfade is `mix(soft, sharp, (1−e)²)` — squared, so the crisp compressed image stays tight against the rim while the displacement itself still spans the whole band. Implemented as 19 taps (three rings of six plus centre) with the whole pattern rotated by a per-pixel hash angle, because 19 taps cannot blur legible text on their own: unrotated they land as discrete ghosts, rotated they land as noise, and noise reads as blur.

Physically this term is not glass. A smooth plane-parallel slab does not blur. It stands in for two real things at once — footprint integration through a curved surface, and Apple's "softer scattering of light" on thicker glass — and it is an authored frost term, not a derived one.

**4 — Mirrored edge band.** A broad, soft, upside-down echo of nearby content over roughly the outer third of the surface on each side, with squared falloff: `mirrorW = max(14, 0.7·min(halfW, halfH))`, `mask = (1 − depth/mirrorW)²`, sampled at `base − n·mask·lens·3`. This band width and falloff shape is the one geometric figure in the whole model that comes from a documented side-by-side comparison against a real iOS 26 device; the amplitude (0.18) is mine. A thin, cubed band instead reads as a hard streak rather than as liquid.

**5 — Adaptation.** Two separate mechanisms, deliberately not merged:

- *Tint as tone mapping, per-pixel.* One colour generates a range of tones indexed by backdrop brightness — lifted and desaturated over dark ground, darkened and saturated over bright ground, staying recognisably the same colour. This is Apple's description of coloured glass and it is genuinely per-pixel, because coloured glass really does vary with what is behind each point. A flat opaque fill of the same colour is the failure mode Apple calls out by name.
- *Light/dark inversion, per-element.* `uFlip` is a **single scalar for the whole element**, supplied by the host, not a per-pixel `smoothstep` on backdrop luma. Apple's requirement forces this: symbols and glyphs on top of the glass flip in lockstep with it, and only one scalar that the host also hands to the content layer can keep them in step. The host derives it from a sparse read of the captured backdrop with hysteresis and a short crossfade (parameters below), and it is gated by element size — small chrome flips, large surfaces adapt without flipping.

**6 — Legibility.** Apple: "as text scrolls underneath, shadows become more prominent… the amount of tint and the dynamic range shift to always ensure buttons remain legible". The dynamic-range half is in-shader and free: the rim sample and the interior sample already bracket the backdrop's high frequencies, so `|luma(sharp) − luma(soft)|` is a local-contrast estimate at zero extra fetches, and it raises tint strength by up to 0.25. The shadow half is host-side (below), because a drop shadow is drawn outside the element's own bounds.

**7 — Lighting.** Four terms over the **lit bevel**, which is a different and much narrower length than the refraction band (`uBand` is 4–7× `uBevel`; conflating the two is why some implementations get either a hairline lens or a smeared edge):
- an inner dark line at `[uBevel, 2.2·uBevel]` reading as thickness — deliberately weak, because a dark border is the fastest way to make the material read as a drawn rectangle;
- a key specular lobe `pow(max(n·L, 0), 6)`;
- a counter-lobe at `pow(max(−n·L, 0), 9.6)·0.35`, giving the two-sided rim real glass has — a bead lit from one side only reads as a gradient;
- a mostly-directional edge line in the outermost 2 px with a small omnidirectional floor (0.07), so the shape stays defined all the way round without becoming a stroke;
- plus Schlick Fresnel `F0 + (1−F0)(1−cosθ)⁵`, `F0 = 0.04` at n = 1.5, evaluated against the same bevel slope, standing in for an environment reflection we do not have.

The whole lighting stack is 2D and geometric. It is **not** a GGX lobe against an area light, and it has no view vector, so there is no specular parallax.

**8 — Interaction.** Two coupled terms from one Gaussian under the fingertip, σ = min(w, h): the backdrop swells toward the touch point by 0.17 of the offset (a magnifier), and the material lifts in luminance by **6 %** at the peak. That 6 % is the second device-measured figure in the model — the same author reports 0.2 (≈50 % lift) reads as a flash, not a press.

**9 — Accessibility.** Reduce Transparency adds up to 0.35 of tint strength (and the host raises `uBlur` 1.6×) — frostier, obscuring more, still the same material. Increase Contrast collapses the fill 88 % toward black or white by backdrop polarity and adds a contrasting border on the edge line.

### Materialize, not fade

`uMaterialize` scales displacement, blur, tint strength, mirror, specular and inner shadow together. At 0 the shader is a bit-exact pass-through of the backdrop — indistinguishable from the element not being there — so the element appears and disappears by "gradually modulating the light bending and lensing" exactly as Apple describes, with alpha never touched. This is the one place where Apple's stated mechanism is implementable literally on Android at no cost.

### Host obligations (both fail as apparent shader bugs)

1. **Record a padded backdrop.** `uPad = uDepth·1.4 + max(uBlur, hostBlur)`. Without it, outward displacement at the rim clamps against the panel's own edge and there is no lensing — only a gradient.
2. **Fill the padded layer with an opaque ground before drawing the backdrop into it,** and pass that ground as `uBase`. A backdrop layer is transparent wherever the app painted nothing; a blur drags that transparency inward; the premultiplied result composites toward black (a dark halo on every panel) while dividing alpha out blows partly-covered pixels to white. Only an opaque-by-construction layer lets the shader treat alpha shortfall as filtering error and reconstruct through it.
3. **Bound samples by the backdrop, not by the layer** (`uBackdrop`). Near a screen edge the padded slice hangs off the end of the recording and those pixels are the flat fill; sampling them paints a band of solid colour at the rim.
4. **Never stack glass on glass.** Glass cannot sample glass. Elements that must interact go in one container, share one sampling region, and are unioned into one field *before* sampling.

### Container variant (merging)

Replace `sdRoundRect(...)` with a `field()` that smooth-unions up to 8 members, and take the gradient of the *merged* field so the bevel follows the fused outline through the neck:

```
float smin(float a, float b, float k) {
    if (k <= 0.0) return min(a, b);
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}
float field(float2 p) {
    float d = 1e6;
    for (int i = 0; i < 8; i++) {                 // constant bound: SkSL must unroll
        if (float(i) < uCount) {
            float s = sdRoundRectAt(p, uRect[i], uRadius[i]);
            d = (d > 1e5) ? s : smin(d, s, uMerge);   // never smin against the seed
        }
    }
    return d;
}
```

Merging is then a consequence of geometry, not an animation, and stays correct at any speed. See the motion model for the `uMerge` ↔ Apple `spacing` mapping.

### Two variants, and what Clear actually means

Regular is the workhorse: adaptive, tinted, blurred, flips polarity when small, carries text. Clear is **not** "Regular with lower alpha" — Apple states it has *no* adaptive behaviours at all, which in this model means `uAdaptive = 0`, `uFlip = 0`, `uLegibility = 0`, and a host-drawn dimming layer beneath it at 35 % black. Mixing the two variants in one interface is prohibited by Apple's guidance, and Clear is admissible only over media-rich content that can take a dim.

### What this model deliberately does not contain

No total internal reflection branch (there is no modelled back face). No participating medium or Beer–Lambert absorption (≈0.005 % over any UI-scale path — irrelevant). No wavelength-accurate Sellmeier dispersion (the physical crown-glass split is ~0.5–2 % of a displacement of a few px, i.e. sub-pixel; every published recreation exaggerates it, and so does this one). No area-light BRDF. No environment probe. No per-pixel LOD or mip bias — AGSL exposes none over a recorded layer, and an isotropic bias would blur the tangential direction and destroy the very rim it was meant to protect.

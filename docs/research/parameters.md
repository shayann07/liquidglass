# Parameters, with provenance

> Verbatim output of the `liquid-glass-optical-spec` research run. Preserved unedited;
> see [README](README.md) for what it is and how far to trust it.

---

All lengths are authored in **dp** and converted to px at the host (`dp × density`). Provenance tags: **[apple]** stated by Apple; **[measured]** third-party side-by-side measurement against a real iOS 26 device; **[derived]** follows from optics/geometry; **[ours]** a taste constant with no external source. Apple publishes **zero** numeric rendering parameters, so nothing below is tagged [apple] on a *number* — only on a direction or a rule.

## Optics

| Uniform | Value | Unit | Provenance |
|---|---|---|---|
| `uIor` | 1.5 | index | [derived] window glass; also the default in sorrell.info, kube.io, ybouane, Compose-Internals. Because `bend` is normalised by its own rim value, IOR sets the *shape* of the falloff, not its magnitude — raising it to 2.0 changes the curve by <4 %. |
| `uBevelPower` (p) | **2.0** default, 4.0 tight | exponent | [derived]. p = 2 is the true circular arc; p = 4 is the squircle-matched bevel (Codrops `bevelPower`, kube.io `(1−(1−x)⁴)^(1/4)`). Measured normalised falloff at n = 1.5: **p = 2** → 1.000 / 0.645 / 0.522 / 0.386 / 0.302 / 0.188 / 0.086 / 0.001 at e = 0 / .05 / .1 / .2 / .3 / .5 / .75 / 1. **p = 4** → 1.000 / 0.600 / 0.416 / 0.226 / 0.130 / 0.041 / 0.005 / 0.000. I default to p = 2 because it lands between the two independently tuned shipped falloffs (Kyant0's `1−√(1−x²)`, RN's `rim²` = 0.25 at mid-band, this repo's `t³` = 0.125) and is the physically derived case; p = 4 concentrates ~95 % of the displacement into the outer third of the band and needs a proportionally wider `uBand`. |
| rim shift bound | √(n²−1) = **1.118** at n = 1.5 | ratio | [derived]. The exact Snell form self-bounds here, so no slope clamp is required. Numerically the profile reaches 0.9885 (p = 2) / 1.0601 (p = 4) at the e = 0.002 clamp; normalisation divides it out. |
| Schlick `F0` | **0.04** at n = 1.5 (0.0200 at 1.33, 0.0338 at 1.45, 0.0426 at 1.52) | reflectance | [derived] `((n₁−n₂)/(n₁+n₂))²`. Known error vs exact Fresnel: −0.019 absolute (≈−21 % relative) at θ = 60–65°, +0.036 at 85°. Accepted; the term is a stand-in for an environment we do not have. |
| gradient epsilon | `clamp(uBand·0.3, 1, 16)` | px | [derived + graphics-writeup]. 1 px produces a one-pixel medial-axis seam down every pill. |
| `axisFade` | `smoothstep(0.15, 0.80, |g|/(2·eps))` | — | [graphics-writeup] |
| C1 seam fade | `smoothstep(1.0, 0.75, e)` | — | [ours], inner quarter of the band |
| rim guard | `smoothstep(0, 1.5, depth)` | px | [ours]. 1.5 px, below perception; only stops the outermost antialiased pixel fetching from beyond coverage. Do **not** widen this to 12 px — at a 14 dp band that is a visible dead ring. |

## Displacement magnitude — the largest disagreement in the corpus

Published tuned values for peak rim displacement, all on ~48 dp controls: **Kyant0** 24 dp (`refractionAmount`, `refractionHeight` 12 dp); **react-native-liquid-glassmorphism** 9 dp regular / 11 dp clear, ×1.35 with the refraction flag, explicitly eye-matched against iOS 26; **this repo today** 22 dp regular / 9 dp chrome. That is a 2.7× spread.

**I chose the 9–18 dp family**, i.e. the device-matched end, because (a) it is the only figure any source claims to have compared side-by-side against an iOS 26 device, and (b) it has a falsifiable failure mode at the other end: over 17 sp body text, rim displacement above ~16 dp yields glyphs that are still legible but smeared along the rim, which reads as a rendering defect rather than as glass — the same finding that forced this repo's `Chrome` preset to 9 dp.

## Presets (dp unless noted)

| | **Regular** | **Clear** | **Chrome** | **Thick** |
|---|---|---|---|---|
| `uBand` | 26 | 28 | 14 | 22 |
| `uDepth` | 14 | 16 | 9 | 12 |
| `uBevel` | 4 | 4 | 3 | 5 |
| band : bevel | 6.5 : 1 | 7 : 1 | 4.7 : 1 | 4.4 : 1 |
| `uBlur` (in-shader) | 10 | 4 | 0 | 16 |
| host pre-blur | 0 | 0 | **14** | 0 |
| `uDispersion` | 0.018 | 0.030 | 0.010 | 0.015 |
| `uMirror` | 0.18 | 0.22 | 0.08 | 0.14 |
| `uTint` α | 0.10 | 0.04 | 0.11 | 0.13 |
| `uAdaptive` | 0.65 | **0** | 0.90 | 0.80 |
| `uLegibility` | 0.6 | **0** | 0.9 | 0.5 |
| `uFlip` allowed | yes if small | **no** | yes if small | **no** |
| `uSpecular` | 0.55 | 0.70 | 0.50 | 0.45 |
| `uSpecularPow` | 6 | 6 | 6 | 7 |
| `uFresnel` | 0.10 | 0.14 | 0.08 | 0.08 |
| `uInnerShadow` | 0.07 | 0.05 | 0.06 | 0.10 |
| dimming layer (host) | 0 | **0.35** | 0 | 0 |

- Band : bevel ratio 4–7 : 1 — [derived]; the refraction band is an independent artistic length, not the lit bevel.
- `uDispersion` 0.018 — [measured]; 0.06–0.09 (this repo today) is ~4× the device-matched value and starts reading as channel breakup on a high-contrast edge.
- Clear's zeros are **[apple]**: "Clear… does not have adaptive behaviours. It is permanently more transparent." Clear therefore does not tone-map, does not flip, does not auto-tint content, and *requires* the dimming layer.
- Dimming 0.35 — **[apple]**, the single numeric parameter in the entire HIG ("a dark dimming layer of 35 % opacity" when the underlying content is bright). The SwiftUI `Glass.clear` code sample uses `.black.opacity(0.3)`; I take 0.35 as the specified value and treat 0.30 as sample drift. Skip it entirely when content is already dark or when the player supplies its own. For a small footprint, dim the element's footprint plus a falloff rather than the whole content layer [apple].
- Tint alphas — [ours], bracketed by [measured] 0.18 regular / 0.047 clear from the RN library (whose pipeline also carries a separate adaptive frost term, so the numbers are not directly interchangeable).

## Adaptation, size and the flip

| Quantity | Value | Provenance |
|---|---|---|
| element size factor | `clamp((min(w,h)_dp − 56) / (320 − 56), 0, 1)` | [ours]. 56 dp control = 0 (small, clear), 320 dp surface = 1 (large, opaque). |
| size → lens gain | `mix(0.85, 1.25, scale)` | [ours], direction [apple] ("more pronounced lensing and refraction" as glass morphs larger) |
| size → tint gain | `mix(0.9, 1.25, scale)` | [ours], direction [apple] ("larger size is more opaque, smaller is clearer") |
| size → inner shadow gain | `mix(0.9, 1.15, scale)` | [ours], direction [apple] ("deeper, richer shadows") |
| flip permitted when | size factor < 0.5 (min edge < 188 dp) | [ours]; the *rule* that small flips and large does not is [apple], the threshold is not published anywhere |
| flip decision | host: mean Rec.709 luma of a 6×5 sparse read of the captured backdrop under the element, ≤10 Hz | [ours]; the 30-sample grid is [measured] practice |
| flip hysteresis | enter dark-on-light at L > 0.60, leave at L < 0.44 | [ours]. Apple publishes no threshold, no sampling region, no cadence and no hysteresis band; a search of HIG Materials, HIG Color, Adopting/Applying Liquid Glass, UIGlassEffect, SwiftUI Glass and WWDC25 219/356 returns zero numeric thresholds. |
| flip crossfade | 180 ms linear on `uFlip` | [ours] |
| tone map excursion | value `mix(1.10, 0.88, L)`, saturation `mix(0.88, 1.06, L)`, both lerped by `uAdaptive` | [ours], direction [apple] |
| legibility lift | `+0.25 · uLegibility · contrast`, `contrast = clamp(|luma(sharp)−luma(soft)|·4, 0, 1)` | [ours], direction [apple] |
| strength ceiling | 0.95 | [ours] — the material must never become opaque |

## Lighting

| Quantity | Value | Provenance |
|---|---|---|
| key light | unit vector (−0.35, −0.94), y negative = above | [ours]. Above and slightly left is the direction a highlight must come from for a surface to read convex. |
| key lobe | `pow(max(n·L,0), 6)` over the bevel band | [ours] |
| counter lobe | `pow(max(−n·L,0), 9.6) × 0.35` | [ours]; two-sidedness is [apple]-consistent and matches LiquidGlassKit's doubled-bearing glare and Kyant0's `pow(|dot|, falloff)` |
| edge line | outermost 2 px, `0.07 + 0.5·max(n·L, 0)` | [ours] |
| inner thickness line | band `[uBevel, 2.2·uBevel]`, strength `uInnerShadow` | [ours] |

## Interaction

| Quantity | Value | Provenance |
|---|---|---|
| press luminance lift | **+0.06** at peak | [measured] against iOS 26; 0.2 gives ~50 % lift and reads as a flash |
| glow falloff | Gaussian, σ = min(w, h) px, from the touch point | [measured] form, [ours] σ |
| touch magnifier | sample offset −0.17 × (local − touch) × falloff | [measured] |
| glow spill to neighbours | container-level: evaluate the Gaussian in *container* space so neighbouring members light up too | [apple] behaviour ("spreads… onto any Liquid Glass elements nearby") |

## Container / merging

| Quantity | Value | Provenance |
|---|---|---|
| `MAX_GLASS_MEMBERS` | 8 | [ours]; SkSL needs an unrollable loop, so the cap is compiled in |
| `uMerge` (smin k) | **k = 2 × spacing_px** | [derived]. With the un-normalised polynomial smin (`h = clamp(0.5+0.5(b−a)/k)`, max inflation k/4), two shapes whose edge gap is g meet at the midline when g ≤ k/2. Setting k = 2·spacing makes the silhouette fuse exactly when the nearest edges are `spacing` apart, which is Apple's stated semantics. Note the widely-copied `k := spacing` is wrong by 2× under this form and by 4× under iq's normalised form. |
| default `spacing` | 40 dp | [apple] — the value in Apple's own `GlassEffectContainer(spacing: 40.0)` sample |
| container spacing vs layout spacing | container `spacing` ≤ interior stack spacing | [apple] — a larger container spacing "causes Liquid Glass effects to blend together at rest" |

## Host contract

| Quantity | Value | Provenance |
|---|---|---|
| `uPad` | `uDepth·1.4 + max(uBlur, hostBlur)` px | [derived]. Covers the 1.25× size gain and the 1.03× dispersion reach with margin. |
| `uBackdrop` | intersection of (padded layer) ∩ (recorded backdrop), inset 0.5 px | [derived]; half a pixel keeps bilinear filtering off both boundaries |
| `uBase` | the app's opaque ground colour | [derived] |
| shadow alpha | 0.14 over solid light ground → 0.30 over text | [ours]; the *inversion* (more over text, less over solid light) is [apple] |
| shadow blur / offset | `18 dp × (0.6 + 0.6·scale)` / `y = 4 dp × (0.5 + scale)` | [ours] |
| shadow update cadence | ≤10 Hz, same sparse backdrop read as the flip | [ours] |
| scroll edge — soft | band = overlap + 12 dp, opacity ramp 0 → 0.9 quadratic, plus progressive blur | [ours]; soft/gradient is [apple] |
| scroll edge — hard | uniform across the full toolbar height incl. pinned accessory | [apple] |
| scroll edge — dark mode | switch from fade-to-background to a multiply dim of 0.18 | [ours]; the *switch* is [apple] |
| Reduce Transparency | `uFrost = 1` (+0.35 strength) and `uBlur × 1.6` | [ours]; "frostier, obscures more" is [apple] |
| Increase Contrast | `uContrast = 1` → 88 % toward black/white + contrasting border | [ours]; "predominantly black or white… contrasting border" is [apple] |
| Reduce Motion | disable press scale/bounce and the touch magnifier; keep the glow at 50 % | [apple] ("decreases the intensity of some effects and disables any elastic properties") |

## Cost

23 backdrop fetches worst case per covered pixel: 19 (scatter) + 3 (dispersion) + 1 (echo). Zero extra fetches for local contrast. Falls to 4 when `uBlur·e < 0.5` (i.e. across the whole rim band, and everywhere in the `Chrome` preset). Measured on the existing 22-fetch ancestor of this shader: Pixel 7 at 120 Hz, two glass elements over live app content, **4.36 % janky frames with the shader on vs 4.96 % with it forced off** — the app's idle redraw dominates. Budget ≤3 simultaneous glass elements and one container per screen; Apple gives the same rule qualitatively ("limit the use of Liquid Glass effects onscreen at the same time").

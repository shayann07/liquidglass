# iOS 27 Liquid Glass: raw measurement log

Verbatim output of the measurement run of 2026-09-11. Nothing here is summarised, reworded or
selected: six analysts measured one optical property each from the pixel-registered captures, six
more tried to refute those measurements, and one synthesised the result. Every structured result
is reproduced exactly as it was returned.

The instrument was a calibration target displayed full screen at 1:1 on an iPhone 13 running iOS
27.0, captured by screenshot with the Photos chrome visible, so the glass sits over a backdrop
that is known pixel for pixel. Registration is identity at zero offset. Neutral greys come back
exact; saturated colours are colour-managed by the display pipeline, so colour readings take
their reference from an uncovered region of the same screenshot rather than from the target file.

Every opacity and tint number below is conditional on the position of the iOS transparency
slider on that device, which was not recorded.

Read the synthesis first: it carries the ranked change list and the contested numbers, which are
the ones where the measuring analyst and the refuting analyst did not agree. The individual
reports after it are the evidence.

---

## Synthesis

# iOS 27 Liquid Glass — direct measurement against a known backdrop

**Status:** research record. Six independent measurements, each adversarially verified by a second analyst. Two of the six headlines failed verification outright; four survived. Every number below is tagged MEASURED, INFERRED, or CONTESTED.

**Dataset:** four screenshots, iPhone 13, iOS 27.0, 1170×2532, of a calibration target displayed full-screen 1:1 in Photos with the app's own Liquid Glass chrome over it. Registration is identity (re-verified independently five times; MAD 0.004 against the target over the 4 px stripe band, 63–124 at ±1 px shift). 1 dp = 3 px throughout. Elements measured: the nav back/more circles (44 dp), the "Today" pill, the bottom toolbar capsule (162×48 dp, corner radius = half-height) and its circular buttons (~48 dp).

**Dataset correction worth recording:** the four captures are a 2×2 of *target* × *chrome appearance*, not two conditions. IMG_6644/6647 show the light target, IMG_6645/6646 the dark one; IMG_6644/6645 carry light-appearance chrome, IMG_6647/6646 dark. Two analysts got this backwards by diagnosing it in row bands where the two target PNGs are byte-identical (the two targets are identical over rows 0–389 and 2172–2499). Nobody's numbers were harmed, but half the design was discarded twice, and the IMG_6644 − IMG_6645 difference image — a pure impulse-response measurement with tint, shading and every glyph cancelling exactly — was never used.

---

## 1. The one global caveat

iOS 27 exposes a user transparency control. Its position on this device was not recorded. **Every opacity, tint, alpha and transmission number in this document is conditional on that unknown setting.** Ratios, directions, geometry, spatial profiles, the linearity results and the dispersion null are all immune. Section 4.3 is the section this caveat applies to; it is not restated there.

Two things bound the worry. First, all four frames were taken at the same setting, so the light-vs-dark comparison and every ratio is unaffected. Second, the dark-appearance transmission (0.584) is only 5 points below the 0.633 that an independent iOS 26 device recording produced, which is not the signature of a slider dragged toward opaque. If it were, both polarities would sit well below their published values, and the dark one does not.

---

## 2. Architecture verdict

Three of our five documented architectural claims are contradicted. This is the expensive kind of wrong and it is stated first.

### 2.1 We sample the wrong way. (Load-bearing. Settled.)

`GlassShaderSource.kt:360` — `float2 push = n * bend * lens;` with `n` the **outward** normal. The header comment at lines 15–20 makes this the library's central decision and justifies the entire `uPad`/padded-backdrop host architecture with it: *"Apple describes the material as sampling content from an area larger than itself, and only outward displacement needs that."*

**The pixels say the sample is taken inward, at every depth, on every element, in every capture.** MEASURED, by seven methods across four analysts, three of which need no model, no fit and no sign convention:

- Target rows 2422–2441 are exactly uniform black across all 1170 columns in **both** targets (min == max == 0.0, verified independently twice). The toolbar still renders the 32-px stripe pattern there at 100–108% of the amplitude it produced one row earlier where the stripes were real. That pattern exists only at rows 2342–2421 — *inside* the element.
- Target rows 2262–2341 carry an 8-px pattern and nothing else (amp32 = −0.75 in every one of those 80 rows). Screenshot rows 2305–2309 show the 32-px pattern with the 8-px pattern completely gone (amp8 collapsed from 12.5 to 0.0–0.2). The top rim pulls from *below*; the bottom rim pulls from *above*.
- Zero-mean normalised cross-correlation of each screenshot row against every target row, no assumed period, no filter: rows 2422–2446 best-match target row 2342 at NCC +0.985; rows 2305–2309 match the 32-px band at +0.91 to +0.97 and the *local* 8-px pattern at −0.008.
- Forward model over 10,507 fitted and 16,015 **held-out** pixels: every outward magnitude from −10 to −60 px scores 0.478–0.509 (chance is 0.5); inward scores 0.985–0.997 fitted, 0.952–0.968 held out.
- Independently on a different element, a different material and the other axis: the top pill's bottom rim shows *zero* response to a 255→153 backdrop step at y=260 that a local sample would render as a −15 level step.

The two anisotropic-blur and vertical-clamp alternatives that could have faked the bottom-rim result are both excluded (the material is vertically sharp — a 0→255 step at y=2342 lands as 187,187,189,208,221 — and the top-rim result cannot be produced by any clamp).

**Consequences.** `uPad` buys nothing the measured behaviour needs, at least for 44–48 dp elements: the maximum radius this material ever samples is 44–45 px inside a 73 px button, i.e. 0.60 R. It never touches a pixel from outside its own footprint. The padded backdrop stays harmless, but the argument for it is gone and the header comment must be rewritten rather than softened.

### 2.2 The interior blur does not taper, and it is not one kernel.

`GlassShaderSource.kt:392–394` — `blurredAt(base + push, uBlur * mat * e, …)` with `rimSharp = (1-e)*(1-e)`. The blur radius ramps from 0 at the rim to `uBlur` in the middle and the sharp sample is mixed back in with weight (1−e)². Header note 2 builds the whole design on it: *"the rim is sharper than the middle … so the scatter lives here, keyed to distance from the edge."*

**MEASURED: the sharp fraction is flat from 3.5 px off the rim to the centre.** Measured two ways, and the second cancels the confound the first missed:

- Toolbar, within the 32-px band, regressing transmitted amplitude on `[1, row, depth]` (depth and row are not collinear because the capsule's straight run folds about row 2375.5): depth coefficients −0.000115/px (light) and **+0.000193/px (dark)** — opposite signs, −3.5% and +5.2% over 45 px of depth. Consistent with zero.
- Top pill with the vertical gradient divided out exactly: the back button sits over uniform black and the more button over uniform white over the *identical* rows, so `jump(r) / (more(r) − back(r))` is a gradient-free sharp fraction. Light: 0.293, 0.289, 0.282, 0.281, 0.281, 0.280 at rows 147–166 and 0.284 → 0.300 at rows 214–262. Dark: 0.333–0.349 over the same rows. Flat from depth 6.5 to 58.5 px.

For comparison, our shader on a 146 px button with `refractionBand` 26 dp predicts a sharp fraction of 91.2% at depth 3.5 px falling to 0.4% at depth 73 px. The reference applies the *same mixture everywhere*.

**And it is two paths, not one.** INFERRED under a two-component model, but the model is forced by the data: ~30% of the backdrop arrives at σ ≤ 0.58 px — i.e. at the floor for a single bilinear fetch plus a 1 px capture aperture, √(0.5² + 1/12) = 0.577, so consistent with *zero* material blur — while ~70% arrives through a kernel of σ 38–46 px. A single disc cannot do this: at `blurRadius` 10 dp our per-axis equivalent σ is 15.6 px, which reduces a 32-px square wave to a predicted peak-to-trough ratio of 0.013 and an 8-px one to 0.000. The device passes both at full sharpness.

This is not a radius that needs retuning. It is a missing second path.

### 2.3 The sampling map folds; ours cannot.

MEASURED, both analysts, two estimators. The sampled radius `r_s(r)` rises to a maximum of 44–45 px at output radius r ≈ 51–53 (depth ≈ 21 px = 7 dp) and then **decreases** to 26–33 px at r = 68. `d(r_s)/dr` crosses zero at depth 21 ± 2 px. The outer ~21 px of the element therefore shows a mirrored copy of the annulus 26–45 px from the centre.

Our `bend` is clamped to [0,1] and multiplied by `smoothstep(1.0, 0.75, e)`, so |dD/d(depth)| stays below 1 and the map is monotone by construction. **We cannot produce the reference's signature outer ring at any setting of the current constants.** The compression factor inside the fold is contested (§5) and the peak-magnification figure is withdrawn as ill-posed, but the fold itself is not in doubt.

`uMirror` is modelling this phenomenon with the wrong mechanism — a blended additive echo over a 51 px band at amplitude 0.05–0.22, sampling `base − n * (mirrorMask * lens * 3.0)`, which at depth ~2 px reaches 126 px inward, roughly three times the deepest source the material ever reads. The measured band *replaces* the local content (98–99% substitution at the top rim; transmitted contrast ratio flat at 0.131–0.155 straight through the folded band, where a blend of two anti-correlated images would visibly wash out). Folding the displacement profile gives the mirror for free and removes a uniform.

### 2.4 What survives architecturally

- **Rim-referenced band with a flat 1:1 interior.** Confirmed. Everything deeper than ~36 px from the edge shows the backdrop undisplaced to within the ~0.3 px measurement floor. `uRefractBand` as a concept is right; only its width is wrong.
- **Displacement is normal-directed and dies in the interior.** Confirmed. On the capsule's straight runs, where the normal is vertical, horizontal stripe position matches the backdrop to within 0.2 px over hundreds of pixels; horizontal displacement appears only in the caps. Our SDF-plus-gradient model is the right shape of model.
- **Recording the backdrop unblurred and keeping the scatter inside the shader.** Confirmed, and it is the thing that makes 2.2 fixable at all. A pre-blurred backdrop could not produce a 0→255 step landing as 185 → 206 → 221 across a single transitional pixel.
- **Highlight factorised as depth-profile × angular-weight.** Structurally right at the poles; see §5 for the separability dispute.

---

## 3. What we got right — claims we can make publicly

These are measured confirmations, not absences of contradiction.

1. **Refraction magnitude.** `refractionDepth = 14.dp` = 42 px. Measured peak displacement is 40–47 px (13–16 dp) near the rim, from two independent methods on two dimensions. Our number sits inside the measured range. We tuned this by eye and hit it. Only the sign is wrong.
2. **The rim carries a sharp image, not a blurred one.** Genuinely refracted content at the rim reaches 30.1–30.4 levels/px of gradient; a 0→255 backdrop step lands with one transitional pixel (185 → 206 → 221 at y=2375, x 415–417). The rim's MTF is *better* than the interior's, because the fold compresses ~2× while transmitted contrast stays flat — so the rim passes ~12 px features at the amplitude the interior passes ~32 px features. Header note 2's *description* of the rim is right even though its mechanism is wrong.
3. **`bevelPeak = 0` is correct.** The rim is a chamfer, not a bead: the peak lies in the outermost device pixel and the profile falls monotonically inward at all 180 angles sampled, in both appearances and over both backdrops. There is no dark outermost pixel with the highlight inside it.
4. **The highlight is additive white.** +64.3 over a body of 137, +65.3 over a body of 37, +62.3 over a body of 181 — the same addition at three body levels spanning 37→181. Multiply would need ×2.78 and give +322 over white; screen would give +22. Both excluded by an order of magnitude. `DarkChrome`'s `highlightChroma = 0` is consistent with everything a neutral backdrop can show.
5. **`DarkChrome.counterLight = 1f` is right**, and it is the only preset that is. Measured on a circle — the first shape that samples every normal direction at once — the counter-lobe is roughly equal to the key lobe. The reading originally taken from a straight-edged bar transfers.
6. **`DarkChrome` is by far the closest preset overall.** Its combination (specular 0.06, edgeLight 5.1, counterLight 1.0, bevel 1.2 dp) evaluates to a peak highlight of 0.213 against a measured 0.256–0.298, and a half-amplitude depth of 1.8 px against a measured 2.34–2.41 px. Both within ~30%. Every other preset is off by 2.5–3×. Its predicted f(255) of 182.0 lands 0.5 levels from the measured 181.5, from a number nobody was tuning directly, on a different iOS version and a different device.
7. **`tintAbsorption = 0` (a blend, not an absorbing medium) is the right default**, so far as two anchor points can establish it. See §5 for what was *withdrawn* here.
8. **The material is achromatic.** Max |R−B| is exactly 0 over ~354,000 glass pixels at 8-bit *and* at 10-bit. Our neutral tints carry no hue error.
9. **`invertsWithBackdrop` as a whole-element, host-owned decision** matches what the captures show: the chrome flips polarity as a unit, glass and glyphs together. It never partially adapts.
10. **`legibility` exists for the right reason.** There is a real, measurable term that raises the material where the backdrop is busy: +14.4 levels at a hard black/white edge (light), +4.5–5.0 (dark), decaying to zero by ~120 px. Apple's description that the tint shifts to keep controls legible is confirmed quantitatively. Only our sign and mechanism are wrong (§4.3).
11. **The comment in `GlassStyle.kt:65`** — *"real UI glass shows almost no prismatic fringe, and past a few percent this stops reading as glass"* — is correct and understated. It is not "almost no" fringe; it is none, to a bound of <0.002 px of channel separation. Whoever wrote that had the right instinct and then set the constant as though they did not believe it.
12. **`GlassStyle.kt`'s note that published refraction depths span 9–24 dp and that 14 dp sits at the device-matched end** holds up.

---

## 4. Measured

### 4.1 Element geometry

| Element | Value | Tag |
|---|---|---|
| Nav circles (back, more) | d = 132.0 px = **44.0 dp**, centres (113.5, 206.5) and (1055.5, 206.5) | MEASURED (algebraic fit, residual RMS 0.035 px over 469 pts; cross-checked against pixel-aligned edges at y=140.5 / 272.5 and 132-px row-207 chords) |
| "Today" pill | rows 140.5–272.5, x ≈ 341–830; top and bottom edges land exactly on pixel boundaries | MEASURED |
| Toolbar capsule | 487 × 144 px = **162 × 48 dp**, corner radius = half-height = 24 dp (a true capsule) | MEASURED |
| Toolbar circles | R = **72.5–73.3 px** (48.3–48.9 dp), centres y ≈ 2375–2376.5 | MEASURED, with one outlier — see §5 |

The toolbar circles' radius was reported as 74.35 px by one analyst; its verifier traced that exactly to a faint drop shadow 5–17 px *below* the button, invisible over the black backdrop but darkening the target's white text by ~3 levels at rows 2448–2465, which a `|shot − target| > 2` boundary criterion counted as element edge. Adding those 52 points to a clean fit reproduces 74.497 / cy 2377.65 / residual 3.55; removing them gives 73.24 / 2374.97 / residual 0.79. Two other dimensions independently fit r = 72.5–73.0. **This is the "a boundary that is actually a shadow" failure mode, and it is worth remembering: the tell was the analyst's own residual of 3.3 px on a fit that should be sub-pixel.**

### 4.2 Refraction

| Quantity | Value | Tag |
|---|---|---|
| Direction | **INWARD**, everywhere, no measurable outward component | MEASURED |
| Max sampled radius | 44.2–45.5 px = **0.60–0.61 R**. Nothing from outside the footprint appears anywhere inside. | MEASURED |
| Rim band width | **35 ± 3 px = 11.7 ± 1.0 dp** (0.47 R on a 48 dp control) | INFERRED (three of four estimates: 34–38 held-out forward model, 35 ± 2 radial correlation, 34.5 ± 2 independent radial fit; see §5 for the withdrawn 28 px) |
| Peak displacement | **40–47 px = 13–16 dp** at depth 1–5 px | INFERRED; bounded ≥ 38 px by a model-free vertical argument |
| Displacement at depth 10 px | 21.9 / 23.3 / 27.0 px across three fits | INFERRED |
| Displacement at depth 20 px | 7.7 / 11.1 px | INFERRED |
| Fold point | depth **21 ± 2 px = 7 dp**, where d(r_s)/dr crosses zero | MEASURED (both analysts, two estimators) |
| Profile shape | monotone in \|D\|, no ringing, rim-maximal; best analytic form \|D\| = M(1 − depth/BW)^p with **p ≈ 2–2.5** (held-out fit) or 3.85 (single-element fit) | INFERRED, exponent contested in range |
| Transmitted contrast is flat with depth through the fold | ratio 0.131–0.155 at depths 38.4 → 6.4 px | MEASURED |

Two profiles agree within ~4 px at every depth despite completely different methods (phase-fit of a 32-px cosine on provably-black rows vs radial annulus correlation): 7 px → 29.6/29.5; 12 → 20.0/19.4; 18 → 14.0/10.0; 23 → 6.1/4.9.

**Do not extrapolate the profile to depth 0.** Rings within ~5 px of the rim are dominated by the specular line, which over provably-black source content reads +62 at the top edge, +47 at the bottom, and 212–214 at the caps where the backdrop is exactly 0 across all 1170 columns. One analyst's "~71 px at depth 0" and one analyst's "two routes agreeing" on the peak both rest on pixels inside that line and were withdrawn by their verifiers.

### 4.3 Transmission and tint  *(read §1 first)*

Anchor readings, both polarities, over provably uniform backdrops:

| | f(0) | f(255) | transmission (f(255)−f(0))/255 |
|---|---|---|---|
| **Light-appearance chrome** | 131.0–131.5 | 249.0–249.5 | **0.4627** |
| **Dark-appearance chrome** | 32.0 | 181.0–181.5 | **0.5843–0.5863** |

All MEASURED and reproduced to the level by two analysts on independently built masks. Transmission is model-free — an amplitude ratio.

**Three independent routes agree on the light figure**, which is the strongest cross-dimension check in the dataset: 0.4627 (matched row bands on the two nav circles), 0.465 (back vs more button asymptotes, rows 154–166), 0.456 (the "Today" pill's own left and right plateaus). The last two come from a different analyst measuring a different quantity for a different purpose.

INFERRED, under a blend model `out = in·(1−α) + T·α`:

- Light: **α = 0.537–0.549**, tint **T ≈ 245 (0xF5F5F5)**, neutral. Tint bracketed only to ±5 because f(0) and f(255) each drift 2–4 levels with position inside the element.
- Dark: **α = 0.414**, tint **T ≈ 77 (0x4D4D4D)**, neutral.

The 0.46 transmitted decomposes as **0.13 unblurred + 0.33 through the wide kernel** (light); 0.20 + 0.38 (dark). So the full model is: ~54% tint, ~13% sharp backdrop, ~33% wide-blurred backdrop.

**Structure transmission is a different number from level transmission**, and nothing in `GlassStyle` currently expresses that. Bottom toolbar, square-wave amplitude surviving:

| | 32 px period | 8 px period |
|---|---|---|
| Toolbar capsule, light | 0.121–0.142 | 0.074–0.112 |
| Toolbar capsule, dark | 0.140–0.169 | 0.112 |
| Toolbar circular button, light | 0.067 | 0.013 |
| Toolbar circular button, dark | 0.087 | 0.018 |

So a small toolbar control passes ~46% of the backdrop's *level* and ~1–3% of its *fine detail*. This makes "passes 64% of white text" an ambiguous fitting target: at the stroke width of 17 pt text on this device the measured pass-through is 3–19%, not 46–64%.

The 32-px transmission is **not constant over the element's own height**: 12.8% at row 2350 rising to 16.0% at row 2418 (light), 17.7% falling to 15.3% (dark). A single figure gets the middle right and both ends wrong.

**Adaptation to distant backdrop content: none.** MEASURED and decisive. IMG_6644 and IMG_6645 put the same chrome over the same local backdrop but differ enormously over rows 960–2130. Over the top chrome, exactly 985 pixels differ by more than 2 — and 100.0% of them lie in the clock box (12:53 AM vs 12:52 AM). Over the toolbar, max difference 3, mean 0.056.

**The contrast lift is real; its law is not determined.** Using the target's antisymmetry about x = 584.5 — S(u) = out(585−u) + out(584+u) must equal f(0) + f(255) for every u under *any* symmetric kernel if the response is affine — the excess peaks at **+14.4 (light) and +4.5–5.0 (dark)** and decays to zero by u ≈ 120 px. MEASURED, reproduced by both analysts. The proposed "≈6% of the tint colour, additive" law is CONTESTED (§5).

What survives regardless of the law is the **sign**: the output rises for *both* polarities. Our `strength += contrast * uLegibility * 0.25` raises the blend alpha, which for a tint darker than the backdrop pulls the output *down*. That is exactly the `DarkChrome` case, which is where legibility matters most.

### 4.4 Blur

| Quantity | Value | Tag |
|---|---|---|
| Sharp-path σ | **≤ 0.58 px**, consistent with zero material blur | MEASURED (folded 32-px LSF second moment 0.575/0.586 px against a resample-plus-aperture floor of 0.577) |
| Line-spread function | 3 taps wide, weights ≈ (0.40, 0.56, 0.04) — exactly one bilinear fetch at a fractional offset | MEASURED |
| Sharp fraction | **28–30% (light), 33–35% (dark)**, flat from depth 3.5 px to 58.5 px | MEASURED (gradient-cancelled ratio) |
| Wide-path σ | **38–46 px** — CONTESTED, see §5 | INFERRED |
| Wide-kernel *shape* | undetermined; Gaussian, Laplacian and Cauchy fit equally well | — |
| Vertical sharpness | same as horizontal (2-row transition on a 0→255 step at y=2342) | MEASURED — this closes the one loophole that could have faked the whole inward result |
| Chromatic dispersion in the interior | zero, R/G/B fits identical to 4 decimals | MEASURED |
| Compositing space | **encoded (non-linear) sRGB values**, not linear light | MEASURED (34 boundary pixels with coverage sweeping 0.09→0.93; at coverage 0.50 the pixel reads 57, encoded blending predicts 66, linear light predicts 96) |

The compositing-space result matters for every amplitude quoted here — they are all encoded levels.

### 4.5 Rim lighting

| Quantity | Value | Tag |
|---|---|---|
| Light direction | **0.0° ± 0.3°** — straight up the screen | MEASURED (six lobe centroids across two analysts: +0.19, −0.19, −0.25, +0.12, +0.12, +0.26) |
| Light type | **parallel/directional**, not a point light | MEASURED (identical lobe centroid for two buttons 942 px apart in x; a point light above screen centre would tilt them ±20°) |
| Peak brightness, outermost 1 px band | **+65 ± 3 levels** (0.255 ± 0.012 shader units) | MEASURED on a pixel-aligned straight edge, no antialiasing model |
| Peak extrapolated to the outline | 75 ± 5 (≈0.29) | INFERRED |
| Radial position of the peak | **the outermost device pixel**; monotone inward at all 180 angles | MEASURED |
| Half-amplitude depth | **2.34–2.41 px = 0.78–0.80 dp** | MEASURED |
| 20% / 10% crossings | 3.36 px / 5.42 px, then a 10–15% shoulder to d ≈ 12 px | MEASURED |
| Additive, backdrop-independent | to within ~5% | MEASURED |
| Colour | neutral, R=G=B exactly | MEASURED |
| Specular at ±90° | exactly zero (181.0 against a body of 181.0) | MEASURED |
| Counter-lobe | roughly **equal** to the key lobe — but see §5 for the tolerance | CONTESTED |
| Lobe HWHM | 30–45° depending on estimator | CONTESTED |
| Cosine-power fit | poor at any exponent (7–9% RMS); better form (1 − θ/90°)^k | MEASURED (that a cosine power fits poorly); k CONTESTED |

**The dark contour.** MEASURED, reproduced and extended:

- It sits **entirely outside the outline**. The outermost wholly-interior pixel matches the body to within 1 level.
- Width ~1.5 px, centred about 0.25 px outside the outline.
- **Zero at both poles, maximal at ±90°.** Full 360° profile at radius 66.5 (dark appearance over white): 0.0% at 0°, 5.7% at 20°, 20.5% at 40°, 36.2% at 60°, **61.2% at 90°**, 37.4% at 120°, 21.7% at 140°, 5.3% at 160°, 0.7% at 180°. Light appearance: 9.4% / 8.1% / 11.3% / 18.8% / **31.4%**.
- It is not a downward drop shadow, and it is not a uniform shadow filled in by highlight bleed: cancelling a −156 shadow at the pole would need an outward bleed ≥156 levels against a rim whose own peak is +62.
- Invisible over black, which is consistent with multiplicative but also with additive clamping at 0. Undetermined.

### 4.6 Dispersion

**Zero. This is the cleanest result in the dataset.**

- max(R,G,B) − min(R,G,B) = **0 at 100% of glass pixels** — ~354,000 pixels across every glass element in all four captures, unmasked, including the antialiased outline and the rim band. MEASURED.
- Still exactly 0 at **10-bit**, read from the original HEVC samples (which are 4:4:4, not 4:2:0 — so this is not a chroma-subsampling artefact). MEASURED.
- The encoder does record sub-8-bit-LSB chroma in near-neutral surroundings (1,779 such pixels in the same file), so the codec-deadzone objection is closed empirically.
- Per-channel edge centroids at six refracted rims: R = G = B to every printed decimal, because the RGB triples are bit-identical.
- Sensitivity is proven: the steepest gradient in genuinely refracted content is 30.1–30.4 levels/px, and 1,219 pixels exceed 20 levels/px.

**Bound: red-to-blue separation < 0.002 px** (INFERRED, conservative; the 8-bit figure — the 10-bit data supports <0.0002 px). Our default `dispersion = 0.018f` at `refractionDepth` 14 dp produces a 1.29–1.89 px split, which on this backdrop puts a peak of 29–43 levels of chroma on the rim and makes ~18,500 of ~70,000 rim pixels visibly non-neutral at chroma ≥ 8. **The reference puts zero on zero.**

---

## 5. Contested

A number here failed independent verification. Both values are given. Nothing has been quietly picked.

| # | Quantity | Analyst | Verifier | Why they disagree | Our read |
|---|---|---|---|---|---|
| C1 | **Refraction band width** | 28 px (26–30) = 9.3 dp | 34–38 px = 11.3–12.7 dp | The 28 comes from a linear extrapolation of a profile whose best-fit exponent is 2–2.5, which clips the tail; its stated corroboration ("the flat black band ends at x=369") describes a feature that is not in the pixels — that row reads a flat 4-level monotone drift with no boundary anywhere. Two *other* dimensions independently measured 34.5–35 px. | 35 ± 3 px. The 28 is superseded, not split. |
| C2 | **Peak displacement at depth ~6 px** | 41.9 ± 4 px | 36.5 ± 1.5 px | Different circle centres (cy 2377.3 vs 2374.9) and radii (74.35 vs 73.26), traced to drop-shadow contamination of the boundary criterion. A model-free bound on the true centreline (a flat black run at x 88–98) caps \|D\| at 39.5, which the 41.9 violates. Ring correlations are higher under the corrected centre at every r ≥ 44. | 36–42 px at depth 6; 40–47 px at depth 1–3 from the other dimension. Our 14 dp = 42 px sits inside either reading. |
| C3 | **Compression inside the fold** | 2.7× at depth 8 px | 1.0–1.7×, typically ~1.2× | The 2.7 is the slope between the two innermost rings, the same two that fail C2's model-free bound. | Close to a 1:1 mirror. Do not ship 2.7. |
| C4 | **Peak magnification on the rising limb** | 3.8× at depth 24 px | ill-posed | r_s is flat at 44.83–44.90 over r = 50–55, so 1/\|dr_s/dr\| formally diverges at the turning point; any grid spacing gives a different answer. | Withdrawn. Not a number. |
| C5 | **Wide-kernel σ** | 38 ± 5 px (12.7 dp) | 46 ± 3 px (15.3 dp) | The 38 is produced by a free linear-in-x nuisance term (0.042–0.060 levels/px) absorbing the kernel's tails. The material has no such gradient — the pill's own plateau fits +0.0032 levels/px, and the buttons over uniform backdrop show a symmetric centre-bright bump, not a ramp. Pinning the asymptotes and dropping the term gives 42–49; re-inserting it drags the answer straight back to 31–39. Model-free symmetrised tail favours ~47. | ~44–46 px = ~15 dp. But see C6 — the same nuisance term moves the transmissions. |
| C6 | **Total transmission, top chrome** | 0.395 light / 0.519 dark | 0.465 light / 0.588 dark | Same cause as C5. The analyst's own note that 0.395 "matches" a measured 0.447 is a 12% miss they absorbed into the nuisance term. | **0.465 / 0.588.** This is not really contested: it agrees to 2 levels with the tint dimension's completely independent 0.4627 / 0.5843. |
| C7 | **"Tone response is affine, verified at four inputs"** | affine to ~1 level at inputs 0, 204, 233, 255 | verified at **two** inputs only (0 and 255) | The two mid-tone points came from the ramp-band pills. Same element, same chrome, byte-identical local backdrop, but they read 231.90 in IMG_6644 and 205.46 in IMG_6645 — a 26-level swing driven entirely by a band starting 15 px *below* them. Had the other capture been opened, the same method would have concluded the response was violently non-affine. Those pills also pass only 3–20% of the expected gradient at pixel scale while showing normal DC transmission. | **Affinity is untested.** Two points cannot test it. Consequence: the derived bound `adaptivity ≤ 0.2` has no support and must not be shipped. |
| C8 | **The "≈6% of tint" contrast-lift law** | one constant fits both polarities | two curves of different shape | Light is peaked (+14.4 at u=3 → +0.4 at u=120); dark is *flat* at +4.5–5.0 from u=3 to u=40 then decays. The light/dark ratio falls from 3.2 at u=3 to 1.10 at u=80. Proportionality to tint holds only at u = 3–6. | The lift is real and its sign is established. Its law is not. |
| C9 | **Counter-light ratio** | 1.00 ± 0.05 | ~1.05–1.10, systematic ±0.2; estimator span 0.76–1.64 | Every θ=180 pole sits over the target's grey ramp, which begins at row 260 while every top-chrome element runs to row 272 — so the analyst's "backdrop uniform on both poles" is false. The ramp band also carries 1-px inverted tick columns 2.5 px and 3.5 px from the two circle centres, i.e. directly on the south pole, at ±205 levels. Those ticks are invisible in the body but produce a −31 level dip in the rim, proving rim and body sample different neighbourhoods. | Clearly ≫ 0.35 and roughly 1. The ±0.05 is not supportable. |
| C10 | **Rim separability / lobe HWHM** | 99.2% single SVD component; HWHM 35.6 ± 1.4° | not separable; HWHM 30–45° estimator-dependent | The 10–15% shoulder exists within ~±30° of the poles and is gone by θ=45° (0.311 vs 0.098 at d=3.2 px); the core is much narrower off-axis. An SVD variance fraction is dominated by the shallow high-amplitude rows and does not test separability — the verifier's own SVD returns 97.5–98.4% on visibly different profiles. | The factorisation is a good approximation at the poles and degrades off-axis. HWHM is 30–45°; anything in that range beats our 26.9°. |
| C11 | **Lobe exponent** | cos^2.8; (1−θ/90)^1.33 | cos^2.31; (1−θ/90)^1.12–1.20 or 1.5–1.7 | Same estimator sensitivity as C10. | Both agree a cosine power fits poorly at 7–9% RMS. Any value 2.3–3.4 is a large improvement on 6. |
| C12 | **Dispersion bound** | < 0.03 px | < 0.002 px | The analyst's detection-floor simulation shifted an already-integer image, so R and B round back to the same integer until d·grad exceeds 1.0 — it cannot detect below 1/max\|grad\| by construction, and the "independent analytic cross-check" 1/30.4 = 0.033 is the same quantity restated. Re-run with proper de-quantisation, d = 0.002 px would show 440 detectable pixels. | **< 0.002 px.** The error runs conservative; the conclusion is understated, not wrong. |
| C13 | **8-px vs 32-px contrast ratio** (the tint-independent MTF test) | 0.988 | 0.62 (different windows), and intrinsically row-confounded: 0.978–0.994 depending on extrapolation | The two stripe bands are at different rows by construction, so the ratio always mixes in the material's vertical gradient. | σ_sharp is 0.40–0.59 px either way. Consequence is small; do not quote three digits. |
| C14 | **Toolbar circle radius / centre** | R 74.35, cy 2377.3 | R 73.26, cy 2374.9 | Drop shadow counted as element boundary (see §4.1). Two other dimensions fit 72.5–73.0. | R ≈ 73 px. Shifts every quoted depth by ~1 px. |

---

## 6. Inferred, not measured

Stated separately because our credibility rests on this line.

- **α and tint colour.** `f(0)` and `f(255)` are measured; α and T come from inverting a blend model. If the material is not a plain blend the pair moves. The blend model is supported at two input levels only (C7).
- **Band width, peak displacement, and the whole displacement profile.** All come from fits — phase-fitting a cosine, radial annulus correlation, or a forward model scored on held-out pixels. The *direction* is measured; the *magnitudes* are inferred.
- **The two-path decomposition (30% sharp / 70% wide).** The flatness with depth is measured. The split into two components, and σ_wide, are model outputs.
- **σ_wide's kernel shape.** Gaussian, Laplacian and Cauchy fit within noise; only the scale is constrained, and the best-fit σ drifts monotonically with fit window (44.0 at ±110 px, 45.7 at ±145, 46.7 at ±175), which is the signature of a heavy tail. If Apple's kernel is heavy-tailed, the equivalent Gaussian understates how far light spreads.
- **Peak highlight at d = 0 (75 ± 5).** Extrapolation. Everything at d ≥ 0.5 px is model-free.
- **The uAberration bound.** Derived by propagating a simulated detection floor through our own shader equation.
- **Whether the rim is one warped sample or two composited layers.** A single non-monotone sample map fits (0.997 fitted / 0.968 held out), and flat transmitted contrast through the fold argues for it, but a 100%-opacity mirrored *replacement* over the outer band would look identical to every test run.
- **Whether the wide blur belongs to the material or to an OS-level effect applied under a bar.** Evidence for the material: it is confined to the element's bounds, and the bottom toolbar shows the same component with no bar-level effect nearby. Not proven.

---

## 7. Holes — what this dataset cannot answer

Listed so nobody mistakes silence for a null result.

1. **Size scaling — i.e. `uScale`, which is a whole architectural claim.** Both usable elements have the same ~73 px cap radius. The only smaller elements are opaque or a different style. **This dataset cannot test whether band or depth scale with element size.** Do not touch `uScale` on this data.
2. **Whether a *larger* element ever samples outside its own footprint.** We can say a 48 dp button does not, anywhere. That is all.
3. **Everything within 0–3.5 px of the rim.** The specular line and partial coverage destroy the phase estimate, and the material's own ~40 px inward reach lands in flat target content. This is exactly the band `uRimSoft`, `rimSharp` and `edgeLight` operate in.
4. **Glass over saturated colour.** No glass element in any capture overlaps the primaries band. `highlightChroma` is entirely untestable here, and dispersion-over-colour is untested (this is precisely where a dispersion term would most plausibly have been implemented).
5. **Mid-tone tone response.** There is no glass anywhere over a large uniform mid-grey. **The single highest-value future capture is a flat 50% grey field large enough to seat a whole toolbar button** — one patch retires C7, the adaptivity question, and most of §4.3's uncertainty.
6. **Any interaction, morph, drag or animation state.** These are four static frames at rest.
7. **The `Clear` variant.** Not present.
8. **Vertical extent of the wide kernel.** Measured across a vertical edge only, so only the horizontal profile. An anisotropic or two-pass blur would be invisible.
9. **Whether some iOS glass averages its backdrop per element rather than convolving.** The ramp-band pills pass ~46% of a DC change 15 px below them while passing ~5% of a gradient inside them. No convolution does that. They may simply be a different control style — they also carry a dark outline and *no rim highlight at all*, which by itself means "all glass elements carry this rim" is not a safe generalisation. Flagged, not actioned.

---

## 8. Change list

Ranked by how far each moves the material toward the reference, not by effort. Files are under `liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/`.

---

### 1. Flip the refraction sign to inward

- **File / symbol:** `GlassShaderSource.kt:360`, `float2 push = n * bend * lens;` → `n * bend * lens` negated (or `n` replaced by the inward normal). Plus the header comment at lines 15–20 and the comment at 357–359, which both assert the opposite and justify `uPad` with it.
- **Current:** outward. The rim compresses the surroundings inward.
- **Measured:** inward, at every depth, on three elements, in four captures, by seven methods. Outward scores at chance (0.478–0.509) in a held-out forward model where inward scores 0.952–0.968.
- **Confidence:** as high as anything in this document. Three of the seven methods are model-free.
- **What breaks if wrong:** the visual read inverts — the material would magnify its own backdrop where it should squeeze the surroundings in. `uPad` becomes dead weight for small elements (harmless; keep it). Every downstream constant tuned against the outward look (`mirror`, `refractionBand`, `rimSoftness`) would need re-tuning a second time. Given the evidence, the realistic risk is not that the direction is wrong but that it differs for elements much larger than 48 dp — untested (§7.1).

### 2. Rebuild the interior as a fixed two-path mix with no depth taper

- **File / symbol:** `GlassShaderSource.kt:392–394` (`blurredAt(base + push, uBlur * mat * e, …)`, `rimSharp = (1-e)*(1-e)`); `GlassStyle.blurRadius` (10 dp default, 16 dp Thick, 0 dp Chrome/DarkChrome) and `GlassStyle.backdropBlur` (14 dp Chrome, 3 dp DarkChrome).
- **Current:** one disc, radius ramping 0 → `uBlur` with depth, sharp sample mixed back at (1−e)². Predicts 91% sharp at the rim → 0.4% at centre.
- **Measured:** **flat** at 28–30% sharp (light) / 33–35% (dark) from depth 3.5 px to 58.5 px. Sharp path σ ≤ 0.58 px (indistinguishable from unblurred). Wide path σ 44–46 px ≈ **15 dp** (CONTESTED 38 vs 46 — C5).
- **Suggested shape:** `bg = mix(wide, sharp, sharpFraction)` with `sharpFraction ≈ 0.30`, constant in depth; `wide` from a σ ≈ 15 dp kernel. Our composite RMS second moment at `blurRadius = 10.dp` is already 32 px = 10.7 dp against the reference's 32.0 px equivalent — the *magnitude* was a good guess, the *shape* is wrong.
- **What breaks if wrong:** a 15 dp blur is expensive; if the wide path is actually an OS-level bar effect rather than the material (§6), we would be baking a system effect into every element including free-standing lenses. And if it is heavy-tailed rather than Gaussian, 15 dp under-spreads. Mitigation: land the flat-mixture change first (cheap, well-measured, independently confirmed twice) and treat σ_wide as a tunable.
- **Note:** this is the change that makes `Chrome`/`DarkChrome`'s deliberate legibility trade unnecessary. Apple gets both — the wide path destroys legible text while the sharp 30% keeps the material reading as glass. That trade is available to us and we are not taking it.

### 3. Raise the tint alpha  *(highest single-constant error; also the highest-risk item)*

- **File / symbol:** `GlassStyle.Chrome.tint` (`GlassStyle.kt:256`) and `GlassStyle.DarkChrome.tint`.
- **Current:** `Chrome` = `Color.White.copy(alpha = 0.11f)` → predicted f(0) = 28.1, transmission 0.878. `DarkChrome` = `0xFF373737` at α 0.36 → f(0) = 20.6, transmission 0.633.
- **Measured:** light f(0) = **131.5**, transmission **0.463**; dark f(0) = **32.0**, transmission **0.584**. Reproducing them needs `0xFFF5F5F5` at **α 0.537** and `0xFF4D4D4D` at **α 0.414**.
- **Magnitude:** our light chrome is **4.88× too transparent**. The device lifts pure black by 131.5; we lift it by 28.
- **What breaks if wrong:** this is the one item the transparency slider (§1) can move wholesale. If the device was set toward opaque, we would ship chrome that is far too solid and lose the material's whole point. Two mitigations: the dark figure is only 5 points from an independent iOS 26 device recording, which argues against an extreme slider position; and the *ratio* between the two polarities is slider-independent, so at minimum ship the light/dark relationship (light more opaque than dark, tint 245 vs 77) even if the absolute level is held. **Recommend: change it, document the conditionality in the preset's KDoc, and re-shoot one frame at a known slider position before 1.0.**
- **Direction of the `DarkChrome` fix is notable:** brighter tint (0x37 → 0x4D) *and* more of it (0.36 → 0.414). Both moves make the bar more solid. This may be a real iOS 26 → 27 change rather than an error in the old fit — the old numbers came from a 720×1558 compressed video, these from a lossless 1170×2532 capture against a known backdrop.

### 4. Halve the refraction band

- **File / symbol:** `GlassStyle.refractionBand` — 26 dp default, 28 dp Clear, 22 dp Thick, 14 dp Chrome, 12 dp DarkChrome.
- **Current:** 26 dp = 78 px.
- **Measured:** **35 ± 3 px = 11.7 ± 1.0 dp** (0.47 R on a 48 dp control). CONTESTED low estimate 9.3 dp (C1), superseded.
- **Magnitude:** 2.2× too wide. On a 50 dp control our band (78 px) exceeds the inradius (73 px), so the whole element refracts and the shader is pushed down the sampled-gradient + dome path. The reference leaves the middle 53% of the radius perfectly flat.
- **What breaks if wrong:** too narrow and the rim reads as a drawn stroke rather than as a lens; too wide and the interior never settles. Since the interior flatness is directly measured (undisplaced to within ~0.3 px beyond depth 36 px), the risk here is low.

### 5. Concentrate the falloff — replace the Snell profile

- **File / symbol:** `GlassShaderSource.kt:337–341` (`bend` from `snellShift`), `GlassStyle.indexOfRefraction` / `bevelPower`.
- **Current:** exact-Snell on a superellipse bevel. At 35% of the band it is still 55% of peak; at the midpoint, 31%.
- **Measured:** at 35% of the band, |D| is ~10% of peak. Best form `|D| = M·(1 − depth/BW)^p` with **p ≈ 2–2.5** (held-out) to 3.85 (single-element). Fitting our exact-Snell shape to the measurement needs band 34 px *and* peak 89.5 px and still leaves 3.14 px RMS — it cannot make both the tail and the peak.
- **What breaks if wrong:** even at the correct band width, our tail puts visible displacement across the whole element where the reference has none. Low risk; the exponent is the only uncertain part and the whole range 2–3.85 beats what we have.

### 6. Set the light straight up

- **File / symbol:** `GlassStyle.kt`, `GlassLight(val x: Float = -0.35f, val y: Float = -0.94f)`.
- **Current:** 20.4° left of vertical.
- **Measured:** **0.0° ± 0.3°**, parallel light. Six centroids across two analysts, spanning −0.25° to +0.26°; folded lobe symmetric to better than 0.02 at every angle.
- **What breaks if wrong:** nothing structural — it is a two-float change. Our rim currently peaks at 10–11 o'clock on every circular element where iOS peaks at 12. This is the single most visible cheap fix in the list.
- **Confidence:** very high, and it is the cleanest measurement in the specular set.

### 7. Add the outer dark contour — as a new mechanism, not a raised constant

- **File / symbol:** `GlassShaderSource.kt:502–503` (`rimDark = clamp(1.0 - abs(depth - 1.5)/1.5, …)`, subtracted inside `mat`); `GlassStyle.edgeShadow` (0 everywhere except `GlassTabBarStyle.HeldLens` 0.08 and `Ios27` 0.06/0.16).
- **Current:** zero on every shipping preset; where non-zero, drawn ~1.5 px **inside** the outline, isotropic, at 0.06–0.16.
- **Measured:** a real contour, **outside** the outline, ~1.5 px wide, reaching **0.31 (light) / 0.61 (dark)** of the backdrop at ±90° over white, and **zero at both poles** — strength roughly complementary to the specular lobe.
- **Three separate errors:** magnitude (4–8×), placement (inside vs outside), angular law (constant vs ≈1−lobe). Suggested form: `rimDark × uEdgeShadow × (1 − lobe)`, drawn outside `mat` — which needs either a host-drawn outset stroke or an inflated shape mask, because our shader only writes inside `mat`. **Flag this before anyone simply raises `uEdgeShadow` off zero and gets a dark ring in the wrong place.**
- **What breaks if wrong:** whether the contour is multiplicative or additive is undetermined (over black it is invisible, consistent with either). Getting that wrong shows up over mid-grey backdrops, of which there are none in this dataset. It is also unknown whether it belongs to all glass or only to nav chrome.
- **Why it ranks here:** this is what makes an iOS control read as a distinct object over bright content, and we ship nothing.

### 8. Broaden and dim the specular lobe

- **File / symbol:** `GlassStyle.specular` (0.55 Regular / 0.50 Chrome / 0.70 Clear / 0.45 Thick), `GlassStyle.specularPower` (6 / 7), `GlassShaderSource.kt:483–491`.
- **Current amplitude:** our expression at θ=0, d=0 with `bevelPeak=0` evaluates to `specular·(1 + 0.5·edgeLight) + fresnel` ≈ **0.83** (Regular), 0.76 (Chrome).
- **Measured amplitude:** **0.256** in the outermost 1 px band, ~0.29 extrapolated to the outline. → `specular` ≈ **0.19–0.20** with `edgeLight = 1`, `counterLight = 1`.
- **Current tightness:** cos^6, HWHM 26.9°, down to 0.016 at 60°.
- **Measured:** HWHM **30–45°** (CONTESTED, C10); the measurement reads 0.26 at 60°. Best single power 2.3–2.8, and a cosine power fits poorly at any exponent (7–9% RMS); `(1 − θ/90°)^1.3` fits to 3.5%.
- **Recommend:** `specularPower ≈ 3.0`, or switch the lobe to the `(1 − θ/90°)^k` form with k ≈ 1.3.
- **What breaks if wrong:** amplitude and width trade against each other visually, so getting one right and the other wrong is worse than getting both wrong in the same direction. Amplitude is well measured (three body levels, model-free, pixel-aligned edge); width is the contested half.

### 9. Raise `counterLight` toward 1 on every preset

- **File / symbol:** `GlassStyle.counterLight` — 0.35 on Regular, Chrome, Clear, Thick; already 1.0 on DarkChrome.
- **Current:** 0.35, and the counter-lobe *tightens* as it weakens (`counterPow = uSpecularPow * mix(1.6, 1.0, …)`), so 0.35 gives a narrower as well as dimmer lower lobe.
- **Measured:** roughly equal lobes — CONTESTED tolerance (C9: 1.00 ± 0.05 vs ~1.05–1.10 with systematic ±0.2). The measured lower lobe has the *same width* as the upper (half-amplitude 2.43 vs 2.49 px).
- **What breaks if wrong:** at 0.35 the bottom of a circular control reads ~a third as bright as the top. Even under the pessimistic verifier reading (0.76–1.64), 0.35 is outside the range. Move it to 1.0 and drop the width-tightening; the exact value between 0.8 and 1.2 is not resolvable from this data.

### 10. Narrow the lit bevel

- **File / symbol:** `GlassStyle.bevel` — 4 dp Regular/Clear, 5 dp Thick, 3 dp Chrome, 1.2 dp DarkChrome; consumed as `smoothstep(uBevel, 0, depth)`, half point at `uBevel/2`.
- **Measured:** half-amplitude at **2.34–2.41 px = 0.78–0.80 dp**, i.e. an equivalent `bevel` of ≈ **1.6 dp**.
- **Magnitude:** Chrome ~1.9× too wide, Regular ~2.5×. `DarkChrome`'s 1.2 dp is ~25% narrow and closest.
- **What breaks if wrong:** the measured profile is *not* a clean smoothstep — after the core it flattens into a 10–15% shoulder persisting to ~4 dp, present only within ±30° of the poles (C10). Nothing in `bevelBand` produces that, and narrowing `uBevel` without adding the shoulder will make the rim read harder than the reference at the poles.

### 11. Zero the dispersion default

- **File / symbol:** `GlassStyle.dispersion` — 0.018f default, 0.010f Chrome/DarkChrome, 0.030f Clear. Also `GlassShaderSource.kt:362–365` and the `GlassStyle.kt:65` KDoc, which read as if the reference shows a small rainbow.
- **Measured:** zero, bounded at **< 0.002 px** of channel separation (CONTESTED against a looser < 0.03 px, C12 — but the tighter bound is the sound one and the error runs conservative). Equivalent `uAberration` bound: **< 2.4e-5** at our lens, < 9.1e-5 scaled to the reference's own rim displacement.
- **Magnitude:** our default is **200× to 750× too large**, not the 12–50× first reported.
- **What breaks if wrong:** nothing at rest. Dispersion is untested over saturated colour and untested during interaction (§7.4, §7.6), which is where it would most plausibly live. **Recommend defaulting to 0.0f and keeping the knob**, rather than removing the code.
- **Also fix the arithmetic in the docs:** the split is `2·uAberration·bend²·lens`, not `2·uAberration·bend·lens`. Identical at the rim; the falloff inward goes as bend³ × gradient.

### 12. Rewrite the legibility term as additive

- **File / symbol:** `GlassShaderSource.kt:419–420`, `strength = uTint.a * … + contrast * uLegibility * 0.25`.
- **Current:** local contrast raises the blend alpha, pulling the output toward the tint.
- **Measured:** the output rises for **both** polarities: +14.4 (light tint 245) and +4.5–5.0 (dark tint 77). A stronger blend toward a dark tint moves the output *down* over a mid backdrop. **Our sign is wrong for any tint darker than the backdrop — which is the `DarkChrome` case, where legibility matters most.**
- **Recommend:** move it out of `strength` and apply additively after the blend, e.g. `col += tinted * (contrast * uLegibility * k)`.
- **What breaks if wrong:** the constant `k` is CONTESTED (C8): "≈6% of tint" is a two-point coincidence at u = 3–6, and the two polarities have different decay shapes. Ship the sign correction; treat `k` as unfitted and pick it by eye until there is a capture that constrains it.

### 13. Fold the mirror into the displacement, or rewrite it

- **File / symbol:** `GlassShaderSource.kt:396–402`, `GlassStyle.mirror` (0.18 default, 0.22 Clear, 0.05 DarkChrome).
- **Current:** a broad, weak, blended echo — `mirrorWidth = max(14, min(halfSize)·0.7)` = 51 px for a toolbar button, mixed at 0.05–0.22, sampling up to `3·lens` = 126 px inward.
- **Measured:** the mirroring **is** the displacement field. The band is ~35 px, it *replaces* rather than blends (98–99% substitution), and the deepest source the material ever reads is 44–45 px. Transmitted contrast is flat at 0.131–0.155 straight through the folded band, which a blend of two anti-correlated images could not do.
- **What breaks if wrong:** if the rim turns out to be two composited layers rather than one folded sample (§6, genuinely undetermined), removing `uMirror` removes the mechanism that would have been correct. **Recommend: fold the displacement profile (item 5's `p` plus a non-monotone tail) and reduce `uMirror` toward 0, but do not delete the uniform** until a capture distinguishes fold from composite.

### 14. Hold `adaptivity` — do not change it on this data

- **File / symbol:** `GlassStyle.adaptivity` — 0.65 Regular, 0.90 Chrome, 0.80 Thick, 0.40 DarkChrome.
- **A bound of `≤ 0.2` was proposed and it does not survive** (C7). Its supporting mid-tone point came from an element that is not the top-chrome material and whose reading swings 26 levels between two captures with identical local backdrops. The tone curve is verified at exactly two inputs, 0 and 255, and two points cannot test affinity.
- **Action: no change.** Record the finding and the reason. This is the item most cheaply retired by one more capture (§7.5).

---

## 9. Reproduction

All working scripts are under `…/scratchpad/caps/` in `work/`, `work2/`, `work3/`, `work_ref2/` and `refute/`. Nothing was written to `D:/liquidglass-captures` or into any repo.

Three method lessons worth carrying forward, because each one caused a published error in this round:

1. **A boundary is not an element edge until you have ruled out its shadow.** A residual of 3.3 px on a fit that should be sub-pixel is the tell.
2. **Diagnose a dataset's grouping in a region where the two conditions actually differ.** Both target PNGs are byte-identical over rows 0–389 and 2172–2499 — which is exactly where all the glass is — so any grouping test run there returns the wrong answer with high confidence.
3. **A free nuisance parameter will absorb the thing you are trying to measure.** One unjustified linear-in-x term produced σ_wide = 38 instead of 46 and made every transmission figure 15% low, and its own author noticed the resulting 12% mismatch and dismissed it.


### Architecture verdict

Yes — three architectural claims are contradicted, and one of them is the library's central decision.

(1) OUTWARD SAMPLING IS WRONG. `GlassShaderSource.kt:360` samples along the outward normal, and the header comment at lines 15-20 makes that the load-bearing claim and justifies the entire uPad/padded-backdrop host architecture with it. The measured material samples INWARD at every depth, on three elements, in all four captures. Seven methods agree, three of which need no model and no sign convention: over a backdrop that is provably exactly uniform black (rows 2422-2441, min==max==0.0 across all 1170 columns in both targets) the toolbar still renders the 32-px stripe pattern at 100-108% of the amplitude it produced one row earlier where the stripes were real, and that pattern exists only inside the element; the top rim shows a pattern from 33-38 rows further inside while the local pattern vanishes entirely; and a forward model scores outward at chance (0.478-0.509 at every magnitude from -10 to -60 px) against inward at 0.985-0.997 fitted and 0.952-0.968 on 16,015 held-out pixels. The maximum radius the material ever samples is 0.60 R — it never touches a pixel from outside its own footprint. uPad is not needed by the measured behaviour for 44-48 dp elements (it stays harmless), but the argument for it is gone and the header comment must be rewritten, not softened. Untested: whether this holds for elements much larger than 48 dp.

(2) RIM-SHARP / INTERIOR-SOFT IS WRONG. `rimSharp = (1-e)*(1-e)` with `uBlur * mat * e` predicts a sharp fraction of 91% at the rim falling to 0.4% at the centre. Measured: FLAT at 28-30% (light) / 33-35% (dark) from depth 3.5 px to 58.5 px, by two methods, the second of which cancels the vertical-gradient confound exactly (normalising each row's jump by the same row's back-button/more-button difference, since those sit over uniform black and uniform white across identical rows). The depth regression coefficients come out with OPPOSITE SIGNS in the two appearances (-0.000115/px and +0.000193/px), which is the signature of zero. Apple applies the same mixture everywhere. Header note 2 is solving a problem that does not exist.

(3) ONE BLUR KERNEL IS WRONG. The material runs two paths in parallel: ~30% of the backdrop arrives at sigma <= 0.58 px (the floor for a single bilinear fetch plus a 1 px capture aperture — i.e. indistinguishable from unblurred) and ~70% through sigma 44-46 px. A single disc cannot produce this at any radius: at blurRadius 10 dp our per-axis equivalent sigma of 15.6 px reduces a 32-px square wave to a predicted 0.013 and an 8-px one to 0.000, while the device passes both at full sharpness. This is a missing second path, not a radius that needs retuning.

(4) THE SAMPLING MAP FOLDS AND OURS CANNOT. Both analysts measured r_s peaking at 44-45 px at output radius 51-53 (depth 21 +/- 2 px) and then decreasing — the outer band is a mirrored copy of an inner annulus. Our bend is clamped to [0,1] and multiplied by smoothstep(1.0, 0.75, e), so the map is monotone by construction. We cannot reproduce the reference's signature outer ring at any setting of the current constants. uMirror is modelling this with the wrong mechanism (a blended additive echo over a 51 px band reaching 126 px inward, where the measured band is ~35 px, replaces rather than blends at 98-99% substitution, and never reads deeper than 45 px).

(5) EDGE SHADOW IS WRONG IN KIND, NOT DEGREE. Ours is drawn INSIDE the outline (rimDark centred 1.5 px in, subtracted within mat) and is isotropic. The measured contour lies entirely OUTSIDE the outline — the outermost wholly-interior pixel matches the body to within 1 level — and its strength is complementary to the specular lobe (zero at both poles, 61% below a white backdrop at +/-90 deg). Reproducing it needs a host-drawn outset stroke or an inflated shape mask. Anyone who simply raises uEdgeShadow off zero gets a dark ring in the wrong place.

WHAT SURVIVES: the rim-referenced band with a flat 1:1 interior; normal-directed displacement that dies in the interior; recording the backdrop unblurred and keeping the scatter in the shader (validated — a 0-to-255 step lands with one transitional pixel, which a pre-blurred backdrop could not do); the SDF-plus-gradient model shape; and the highlight factorised as depth-profile x angular-weight (good at the poles, degrades off-axis).

ALSO UNTESTED AND THEREFORE NOT ENDORSED: uScale. Both usable elements share the same ~73 px cap radius, so nothing here tests whether band or depth scale with element size. Do not touch it on this data.

### Contested numbers

```json
[
 "Refraction band width: 28 px (26-30) = 9.3 dp vs 34-38 px = 11.3-12.7 dp. The 28 comes from linearly extrapolating a profile whose best-fit exponent is 2-2.5, which clips the tail; its stated corroboration ('the flat black band ends at x=369') describes a feature that is not in the pixels. Two other dimensions independently measured 34.5-35 px. Our read: 35 +/- 3 px.",
 "Peak displacement at depth ~6 px: 41.9 +/- 4 px vs 36.5 +/- 1.5 px. Different fitted circle centres (cy 2377.3 vs 2374.9) and radii (74.35 vs 73.26), traced to drop-shadow contamination of the boundary criterion. A model-free bound on the true centreline caps |D| at 39.5, which 41.9 violates. Ring correlations are higher under the corrected centre at every r >= 44.",
 "Compression inside the fold: 2.7x vs 1.0-1.7x (typically ~1.2x). The 2.7 is the slope between the two innermost rings, the same two that fail the model-free bound. Do not ship 2.7.",
 "Peak magnification on the rising limb: 3.8x vs ill-posed. r_s is flat at 44.83-44.90 over r = 50-55, so 1/|dr_s/dr| formally diverges at the turning point and any grid spacing gives a different answer. Withdrawn as a number.",
 "Wide-kernel sigma: 38 +/- 5 px (12.7 dp) vs 46 +/- 3 px (15.3 dp). The 38 is produced by a free linear-in-x nuisance term that absorbs the kernel's tails; the material has no such gradient (plateau slope +0.0032 levels/px vs a fitted 0.042-0.060). Pinning the asymptotes gives 42-49; re-inserting the term drags it back to 31-39. Our read: ~44-46 px.",
 "Total transmission of the top chrome: 0.395 light / 0.519 dark vs 0.465 / 0.588. Same nuisance-term cause. Not really contested: 0.465/0.588 agrees to 2 levels with a completely independent dimension's 0.4627/0.5843.",
 "'Tone response is affine, verified at four inputs' vs verified at TWO inputs only (0 and 255). The two mid-tone points came from ramp-band pills that read 231.90 in one capture and 205.46 in another with byte-identical local backdrops, and that pass only 3-20% of the expected gradient at pixel scale. Consequence: the derived bound adaptivity <= 0.2 has no support and must not be shipped.",
 "The '~6% of tint' contrast-lift law: one constant for both polarities vs two curves of different shape. Light is peaked (+14.4 at u=3 to +0.4 at u=120); dark is flat at +4.5-5.0 from u=3 to u=40 then decays. The light/dark ratio falls from 3.2 at u=3 to 1.10 at u=80. The lift is real and its SIGN is established; its law is not.",
 "Counter-light ratio: 1.00 +/- 0.05 vs ~1.05-1.10 with systematic +/-0.2 (estimator span 0.76-1.64). Every theta=180 pole sits over the target's grey ramp (starts row 260, elements run to row 272), plus 1-px inverted tick columns 2.5 and 3.5 px from the two circle centres at +/-205 levels. Clearly much greater than our 0.35 and roughly 1; the +/-0.05 is not supportable.",
 "Rim separability and lobe HWHM: '99.2% single SVD component, HWHM 35.6 +/- 1.4 deg' vs 'not separable, HWHM 30-45 deg estimator-dependent'. The 10-15% shoulder exists within +/-30 deg of the poles and is gone by 45 deg; the verifier's own SVD returns 97.5-98.4% on visibly different profiles, so the variance fraction does not test separability.",
 "Lobe exponent: cos^2.8 and (1-theta/90)^1.33 vs cos^2.31 and (1-theta/90)^1.12-1.20 (template) or 1.5-1.7 (fixed-depth). Both agree a cosine power fits poorly at 7-9% RMS. Any value 2.3-3.4 beats our 6.",
 "Dispersion bound: < 0.03 px vs < 0.002 px. The looser figure came from shifting an already-integer image, which cannot detect below 1/max|grad| by construction, and its 'independent analytic cross-check' 1/30.4 = 0.033 is the same quantity restated. The error runs conservative, so the conclusion is understated rather than wrong.",
 "8-px vs 32-px contrast ratio (the tint-independent MTF test): 0.988 vs 0.62 on different windows, and 0.978-0.994 depending on how the row confound is extrapolated. The two stripe bands are at different rows by construction, so the ratio always mixes in the material's vertical gradient. Sigma_sharp is 0.40-0.59 px either way.",
 "Toolbar circle radius and centre: R 74.35 / cy 2377.3 vs R 73.26 / cy 2374.9. A faint drop shadow 5-17 px below the button, visible only over the target's white text, was counted as element boundary by a |shot - target| > 2 criterion. Two other dimensions fit 72.5-73.0. Shifts every quoted depth by ~1 px."
]
```

### Ranked change list

```json
[
 {
  "rank": 1,
  "what": "Flip the refraction sample direction from outward to inward, and rewrite the header comment that justifies uPad with the outward claim.",
  "file": "liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/GlassShaderSource.kt:360 (float2 push = n * bend * lens) and the doc comment at lines 15-20 / 357-359",
  "current": "Outward: push = n * bend * lens with n the outward normal; the host records uPad beyond each edge specifically to feed it.",
  "measured": "INWARD at every depth, on three elements, in four captures, by seven methods (three model-free). Over a provably uniform-black backdrop the toolbar renders the 32-px stripe pattern at 100-108% of the amplitude it produced where the stripes were real; that pattern exists only inside the element. A forward model scores outward at chance (0.478-0.509) and inward at 0.985-0.997 fitted / 0.952-0.968 held out. Max sampled radius 0.60 R: nothing from outside the footprint appears inside.",
  "risk": "If wrong the visual read inverts entirely. Realistic risk is not the direction (as settled as anything in the dataset) but whether it holds for elements much larger than 48 dp, which this data cannot test. Every constant tuned against the outward look (mirror, refractionBand, rimSoftness) needs re-tuning after the flip. uPad becomes dead weight for small elements but stays harmless."
 },
 {
  "rank": 2,
  "what": "Replace the depth-tapered single blur with a fixed two-path mix: ~30% unblurred plus ~70% through a sigma ~15 dp kernel, constant at every depth.",
  "file": "GlassShaderSource.kt:392-394 (blurredAt(base + push, uBlur * mat * e, ...), rimSharp = (1-e)*(1-e)); GlassStyle.blurRadius; GlassStyle.backdropBlur",
  "current": "One tap disc whose radius ramps 0 -> uBlur with depth, sharp sample mixed back at (1-e)^2. On a 146 px button this predicts 91.2% sharp at depth 3.5 px falling to 0.4% at depth 73 px.",
  "measured": "Sharp fraction FLAT at 28-30% (light) / 33-35% (dark) from depth 3.5 px to 58.5 px, with depth regression coefficients of opposite sign in the two appearances. Sharp path sigma <= 0.58 px (the resample floor, i.e. indistinguishable from unblurred). Wide path sigma 44-46 px = ~15 dp (CONTESTED 38 vs 46).",
  "risk": "A 15 dp blur is expensive. If the wide path is an OS-level bar effect rather than the material, we would bake a system effect into free-standing lenses too. If the kernel is heavy-tailed rather than Gaussian, 15 dp under-spreads. Mitigation: land the flat-mixture change first (cheap, twice-confirmed) and treat sigma_wide as tunable."
 },
 {
  "rank": 3,
  "what": "Raise the chrome tint alphas to the measured opacity. CONDITIONAL ON THE UNKNOWN TRANSPARENCY SLIDER.",
  "file": "GlassStyle.kt:256 GlassStyle.Chrome.tint; GlassStyle.DarkChrome.tint",
  "current": "Chrome = Color.White.copy(alpha = 0.11f), predicting f(0) = 28.1 and transmission 0.878. DarkChrome = 0xFF373737 at alpha 0.36, predicting f(0) = 20.6 and transmission 0.633.",
  "measured": "Light chrome f(0) = 131.5, f(255) = 249.5, transmission 0.4627 (three independent routes: 0.4627 / 0.465 / 0.456). Dark chrome f(0) = 32.0, f(255) = 181.5, transmission 0.5843-0.588. Implied: 0xFFF5F5F5 at alpha 0.537 and 0xFF4D4D4D at alpha 0.414. Our light chrome is 4.88x too transparent.",
  "risk": "Highest-risk item in the list. The transparency slider position on this device was not recorded, and it moves opacity wholesale; if it was set toward opaque we ship chrome that is far too solid. Two mitigations: the dark figure sits only 5 points from an independent iOS 26 device recording, and the light/dark RATIO is slider-independent. Change it, document the conditionality in the preset KDoc, and re-shoot one frame at a known slider position before 1.0."
 },
 {
  "rank": 4,
  "what": "Halve the refraction band.",
  "file": "GlassStyle.refractionBand (26.dp default, 28 Clear, 22 Thick, 14 Chrome, 12 DarkChrome)",
  "current": "26 dp = 78 px, which on a 48 dp control exceeds the inradius (73 px) so the whole element refracts and the shader is forced down the sampled-gradient + dome path.",
  "measured": "35 +/- 3 px = 11.7 +/- 1.0 dp (0.47 R). Three of four estimates cluster at 34-38 px; the fourth (28 px) was withdrawn by its own verifier. The reference leaves the middle 53% of the radius perfectly flat, undisplaced to within the ~0.3 px measurement floor beyond depth 36 px.",
  "risk": "Low. Interior flatness is directly measured. Too narrow reads as a drawn stroke rather than a lens; too wide and the interior never settles."
 },
 {
  "rank": 5,
  "what": "Replace the exact-Snell falloff with a concentrated power profile, |D| = M*(1 - depth/BW)^p with p about 2-2.5.",
  "file": "GlassShaderSource.kt:337-341 (bend from snellShift); GlassStyle.indexOfRefraction, GlassStyle.bevelPower",
  "current": "Exact Snell on a superellipse bevel, normalised to 1 at the rim. At 35% of the band it is still 55% of peak; at the band midpoint, 31%.",
  "measured": "At 35% of the band |D| is ~10% of peak. Best-fit exponent 2-2.5 (held-out) to 3.85 (single-element). Fitting our Snell shape to the measurement needs band 34 px AND peak 89.5 px and still leaves 3.14 px RMS: it cannot make both the tail and the peak.",
  "risk": "Low. The exponent is the only uncertain part and the whole contested range 2-3.85 beats what we ship. Even at the correct band width our current tail puts visible displacement across the whole element where the reference has none."
 },
 {
  "rank": 6,
  "what": "Set the default light straight up: GlassLight(0f, -1f).",
  "file": "GlassStyle.kt, GlassLight(val x: Float = -0.35f, val y: Float = -0.94f)",
  "current": "(-0.35, -0.94), a light 20.4 degrees left of vertical.",
  "measured": "0.0 +/- 0.3 degrees, parallel/directional. Six independent lobe centroids across two analysts span -0.25 to +0.26 deg; the folded lobe matches w(theta) vs w(-theta) to better than 0.02 at every angle. Directionality proved by identical centroids for two buttons 942 px apart in x (a point light above screen centre would tilt them by ~20 deg in opposite directions).",
  "risk": "None structural, a two-float change. Our rim currently peaks at 10-11 o'clock on every circular element where iOS peaks at 12. Cheapest visible fix in the list."
 },
 {
  "rank": 7,
  "what": "Add the outer dark contour as a new mechanism: outside the outline, ~1.5 px wide, modulated as (1 - lobe) rather than isotropic.",
  "file": "GlassShaderSource.kt:502-503 (rimDark = clamp(1.0 - abs(depth - 1.5)/1.5, ...), subtracted inside mat); GlassStyle.edgeShadow; GlassTabBarStyle.HeldLens (0.08) and Ios27 (0.06/0.16)",
  "current": "Zero on every shipping preset. Where non-zero, drawn ~1.5 px INSIDE the outline, isotropic, at 0.06-0.16.",
  "measured": "A real contour lying entirely OUTSIDE the outline (the outermost wholly-interior pixel matches the body to within 1 level), ~1.5 px wide, reaching 0.31 of the backdrop (light appearance) and 0.61 (dark) at +/-90 deg over white, and ZERO at both poles. Full profile at radius 66.5, dark over white: 0.0% at 0 deg, 20.5% at 40, 36.2% at 60, 61.2% at 90, 21.7% at 140, 0.7% at 180.",
  "risk": "Three separate errors to fix at once (magnitude 4-8x, placement, angular law), and our shader only writes inside mat, so this needs a host-drawn outset stroke or an inflated shape mask. Whether the contour is multiplicative or additive is undetermined (invisible over black, consistent with either), which shows up over mid-grey backdrops that this dataset does not contain. It is also unknown whether it belongs to all glass or only to nav chrome. Flag this loudly: anyone who simply raises uEdgeShadow off zero gets a dark ring in the wrong place."
 },
 {
  "rank": 8,
  "what": "Dim and broaden the specular lobe: specular ~0.19-0.20, specularPower ~3.0 (or switch the lobe to (1 - theta/90)^1.3).",
  "file": "GlassStyle.specular (0.55 Regular / 0.50 Chrome / 0.70 Clear / 0.45 Thick), GlassStyle.specularPower (6 / 7), GlassShaderSource.kt:483-491",
  "current": "At theta=0, d=0 with bevelPeak=0 our expression evaluates to specular*(1 + 0.5*edgeLight) + fresnel = ~0.83 (Regular), 0.76 (Chrome). Lobe is cos^6: HWHM 26.9 deg, down to 0.016 at 60 deg.",
  "measured": "Peak 0.256 in the outermost 1 px band (+65 +/- 3 levels, additive, measured at three body levels spanning 37-181), ~0.29 extrapolated to the outline. Lobe HWHM 30-45 deg (CONTESTED); the measurement reads 0.26 at 60 deg. Best single cosine power 2.3-2.8 and it fits poorly at any exponent (7-9% RMS); (1 - theta/90)^1.3 fits to 3.5%.",
  "risk": "Amplitude and width trade against each other visually, so getting one right and the other wrong is worse than getting both wrong the same way. Amplitude is well measured and model-free (pixel-aligned straight edge, no antialiasing model); the width is the contested half."
 },
 {
  "rank": 9,
  "what": "Raise counterLight from 0.35 to ~1.0 on Regular, Chrome, Clear and Thick, and drop the width-tightening that couples to it.",
  "file": "GlassStyle.counterLight (0.35 on Regular/Chrome/Clear/Thick; already 1f on DarkChrome); GlassShaderSource.kt:483 counterPow = uSpecularPow * mix(1.6, 1.0, ...)",
  "current": "0.35, and the counter-lobe tightens as it weakens, so 0.35 gives a narrower as well as a dimmer lower lobe.",
  "measured": "Roughly equal lobes. CONTESTED tolerance: 1.00 +/- 0.05 vs ~1.05-1.10 with systematic +/-0.2 (estimator span 0.76-1.64, because every theta=180 pole sits over the target's grey ramp plus a 1-px tick column near both circle centres). The measured lower lobe has the same WIDTH as the upper (half-amplitude 2.43 vs 2.49 px).",
  "risk": "Low. Even under the pessimistic reading (0.76-1.64) our 0.35 is outside the range. The exact value between 0.8 and 1.2 is not resolvable from this data; at 0.35 the bottom of a circular control reads about a third as bright as the top."
 },
 {
  "rank": 10,
  "what": "Narrow the lit bevel to an equivalent of ~1.6 dp, and add the off-peak shoulder the smoothstep cannot produce.",
  "file": "GlassStyle.bevel (4.dp Regular/Clear, 5 Thick, 3 Chrome, 1.2 DarkChrome), consumed as smoothstep(uBevel, 0, depth) with half point at uBevel/2",
  "current": "Chrome 3 dp, Regular 4 dp: lit band roughly 1.9x and 2.5x wider than measured. DarkChrome's 1.2 dp is closest, ~25% narrow.",
  "measured": "Half-amplitude depth 2.34-2.41 px = 0.78-0.80 dp, i.e. an equivalent bevel of ~1.6 dp. Normalised band means from a pixel-aligned edge: 1.000, 0.694, 0.464, 0.158, 0.142, 0.096 at d = 0.5 to 5.5 px.",
  "risk": "The measured profile is not a clean smoothstep: after the core it flattens into a 10-15% shoulder persisting to ~4 dp, present only within +/-30 deg of the poles (CONTESTED whether it is lighting at all). Narrowing uBevel without adding the shoulder makes the rim read harder than the reference at the poles."
 },
 {
  "rank": 11,
  "what": "Default dispersion to 0.0f (keep the knob), and fix the KDoc that implies a real fringe exists to hit.",
  "file": "GlassStyle.dispersion (0.018f default, 0.010f Chrome/DarkChrome, 0.030f Clear); GlassShaderSource.kt:362-365; GlassStyle.kt:65 KDoc",
  "current": "0.018f, producing a 1.29-1.89 px red-to-blue split at the rim.",
  "measured": "Zero. max(R,G,B)-min(R,G,B) = 0 at 100% of ~354,000 glass pixels across every element in all four captures, at 8-bit AND at 10-bit read from the original 4:4:4 HEVC samples. Per-channel edge centroids identical at six refracted rims. Sensitivity proven (steepest refracted gradient 30.4 levels/px). Bound < 0.002 px of separation (CONTESTED against a looser < 0.03 px, but the tighter figure is the sound one). Our default is 200x to 750x too large; on this backdrop it would put ~18,500 of ~70,000 rim pixels visibly non-neutral at chroma >= 8.",
  "risk": "None at rest. Dispersion is untested over saturated colour and untested during interaction or morph, which is where it would most plausibly have been implemented, so default it to 0 rather than removing the code. Also fix the arithmetic in the docs: the split is 2*uAberration*bend^2*lens, not 2*uAberration*bend*lens."
 },
 {
  "rank": 12,
  "what": "Move the legibility term out of the blend strength and apply it additively after the blend.",
  "file": "GlassShaderSource.kt:419-420, strength = uTint.a * mix(0.9, 1.25, scale) + contrast * uLegibility * 0.25",
  "current": "Local contrast raises the blend alpha, pulling the output toward the tint colour.",
  "measured": "At a hard black/white edge the output rises for BOTH polarities: +14.4 levels (light tint 245) and +4.5-5.0 (dark tint 77), decaying to zero by ~120 px. A stronger blend toward a dark tint moves the output DOWN over a mid backdrop. Our sign is therefore wrong for any tint darker than the backdrop, which is exactly the DarkChrome case.",
  "risk": "The sign correction is safe. The constant is not: the proposed 'about 6% of tint' law is a two-point coincidence at u = 3-6 (CONTESTED), and the two polarities have different decay shapes (light peaked, dark flat to u=40). Ship the sign, treat the magnitude as unfitted."
 },
 {
  "rank": 13,
  "what": "Fold the displacement profile so it produces the mirrored outer band, and reduce uMirror toward 0 without deleting it.",
  "file": "GlassShaderSource.kt:396-402 (mirrorWidth, mirrorMask, echo = backdropAt(base - n * (mirrorMask * lens * 3.0))); GlassStyle.mirror (0.18 default, 0.22 Clear, 0.05 DarkChrome)",
  "current": "A broad weak blended echo: mirrorWidth = max(14, min(halfSize) * 0.7) = 51 px on a toolbar button, blended at 0.05-0.22, sampling up to 3*lens = 126 px inward.",
  "measured": "The mirroring IS the displacement field. d(r_s)/dr crosses zero at depth 21 +/- 2 px and the outer band shows a mirrored copy of the annulus 26-45 px from the centre. The band is ~35 px, it REPLACES rather than blends (98-99% substitution at the top rim, where the local 8-px pattern falls to amp 0.0-0.2 from an interior 12.5 while the interior's 32-px pattern appears at 98-99% of its in-zone reference), and the deepest source ever read is 44-45 px. Transmitted contrast is flat at 0.131-0.155 straight through the folded band, which a blend of two anti-correlated images could not do.",
  "risk": "Whether the rim is one folded sample or two composited layers is genuinely undetermined: a 100%-opacity mirrored replacement would look identical to every test run, and row 2310 at the top rim is a ~25/75 mixture that reads more like a mask with a 1-2 px falloff than a continuous warp. Fold the profile and reduce uMirror, but keep the uniform until a capture distinguishes fold from composite. Also note the fold compression is CONTESTED (2.7x vs 1.0-1.7x) - target the low end."
 },
 {
  "rank": 14,
  "what": "Hold adaptivity. Change nothing, and record why.",
  "file": "GlassStyle.adaptivity (0.65 Regular, 0.90 Chrome, 0.80 Thick, 0.40 DarkChrome); GlassShaderSource.kt:249 toneMappedTint",
  "current": "0.65 / 0.90 / 0.80 / 0.40.",
  "measured": "NOT MEASURED. A bound of 'adaptivity <= 0.2' was proposed from a three-point fit whose mid-tone point came from an element that is not the top-chrome material: same element, same chrome, byte-identical local backdrop, but it reads 231.90 in one capture and 205.46 in another (a 26-level swing driven by a band 15 px below it), and it passes only 3-20% of the expected gradient at pixel scale while showing normal DC transmission. The tone curve is verified at exactly two inputs, 0 and 255, and two points cannot test affinity. What IS measured is that the material does not adapt to distant backdrop content at all: over the top chrome exactly 985 pixels differ between two captures with wildly different lower targets, and 100.0% of them are the clock digits.",
  "risk": "None from holding. The risk is acting on a bound with no support. This is the item most cheaply retired by one more capture: a flat 50% grey field large enough to seat a whole toolbar button pins the curve's midpoint directly and closes most of the tint section's uncertainty."
 }
]
```

---

## Measured: Which way the rim samples

Agent `a44f5323a1d60b6ef`.

```json
{
 "headline": "The iOS 27 rim samples INWARD: over a backdrop that is provably, exactly uniform black, the Photos toolbar's outer 26 px still renders the 32-px stripe pattern at 100% amplitude \u2014 that pattern exists nowhere but inside the element, so the rim is showing the element's own interior pushed outward, not outside content pulled in.",
 "confident": [
  {
   "quantity": "Direction of rim sampling (the headline result)",
   "value": "INWARD \u2014 the sample is taken from further inside the element than the pixel that displays it",
   "unit": "direction",
   "method": "Two independent, sign-convention-free tests on the Photos bottom toolbar's middle capsule (glass occupies x 342-828, y 2304-2447; capsule, centre (585.0, 2375.5), half-width 243.5, half-height 72, corner radius 72). TEST A (bottom rim): target rows 2422-2441 are EXACTLY uniform black over x 420-750 (verified min == max == 0.0 in both light and dark targets), yet the screenshot there carries the 32-px-period stripe pattern. That pattern exists in the backdrop ONLY at rows 2342-2421, which are inside the element. TEST B (top rim): target rows 2262-2341 carry an 8-px-period pattern and nothing else (amp32 = -0.75 for every one of those 80 rows), and vertical stripes are invariant under ANY purely vertical remapping \u2014 yet screenshot rows 2305-2309 show the 32-px pattern with the 8-px pattern completely gone. The 32-px pattern first occurs at row 2342, 33-38 px further inside.",
   "coordinates": "IMG_6644/45/47/46, x 420-750, rows 2304-2310 and 2422-2444",
   "captures": "All four captures, both appearance groups, both targets."
  },
  {
   "quantity": "Bottom-rim stripe amplitude over provably-black backdrop",
   "value": "20.3 rising to 21.9 (light appearance); 18.2 falling to 16.1 (dark appearance), vs 20.3 / 18.3 in the last row where the stripes actually exist",
   "unit": "8-bit levels, square-wave matched-filter amplitude",
   "method": "amp32(y) = mean over x in [420,750] (icon pixels masked) of (row - row_mean) * w32, where w32(x) = +1 if (x mod 32) < 16 else -1. Row 2421 is the last row whose backdrop actually holds the stripes; rows 2422-2441 are exactly black. Amplitude does not fall by even 1% across that boundary and then rises for 23 more rows. No averaging or blur kernel can synthesise a pattern absent from its input, let alone at 100-108% of the amplitude it produced when the pattern was present.",
   "coordinates": "rows 2421-2444, x 420-750",
   "captures": "The single most decisive number in the dataset."
  },
  {
   "quantity": "Outward hypothesis, quantitative exclusion",
   "value": "Outward sampling would need a source carrying square-wave amplitude 105-152; the only content outside the element carries at most 21 (below) and -19 (above)",
   "unit": "8-bit levels",
   "method": "Measured the material's contrast transfer where the stripes genuinely lie beneath it: transmission = mean(amp32_observed / amp32_target) over rows 2350-2419 = 13.94% (light appearance) / 16.18% (dark appearance). Dividing the observed rim amplitude by that transmission gives the amplitude the source must carry. True target amp32: 16-px stripe zone (INSIDE, rows 2342-2421) = 126.8; rows 2448-2470 (what outward would fetch below) = 4.2 mean, 21.2 max; rows 2140-2302 (what outward would fetch above) = -1.1 mean, -19.0 min, and of the WRONG SIGN. Outward fails by a factor of 5-7 in amplitude at the bottom and by amplitude AND sign at the top.",
   "coordinates": "x 420-750; source candidates rows 2140-2302 and 2448-2470",
   "captures": "Rules out the one alternative that could have mimicked the bottom result (the 17 pt text at rows 2450-2478)."
  },
  {
   "quantity": "Peak inward sample displacement at the rim",
   "value": "~40 (bounded >= 38; extrapolated 40-44)",
   "unit": "px at 3x  ==  ~13.3 dp",
   "method": "Two routes agreeing. (1) Vertical: screenshot rows 2304-2309 (depth 1-5 px below the glass's top edge at 2304) show the 32-px pattern, which requires a source at row >= 2342, i.e. source depth >= 38 px, so displacement >= 33-37 px; row 2311 (depth 7) has recovered the local 8-px pattern at full interior amplitude (12.4 vs 12.5), so the source has come back above 2342 there. (2) Horizontal direct inspection at the centre row y=2375: x 343-345 (depth 1.5-3.5) reads the material's WHITE level in both appearances (210/214/216 in IMG_6644 where white=221, black=184; 126/125/127 in IMG_6647 where white=128, black=82) while the local backdrop is black[336,351] \u2014 the nearest white lies at [384,399], i.e. +40 px inward. Extrapolating the measured profile below to depth 0 gives 40-44 px.",
   "coordinates": "IMG_6644 y=2375, x 342-431; and rows 2304-2311, x 420-750",
   "captures": "The magnitude is close to our shipped refractionDepth of 14 dp \u2014 it is the sign that is wrong."
  },
  {
   "quantity": "Inward displacement profile vs depth from the rim (middle capsule caps)",
   "value": "depth 7 px -> +29.6; 10 -> +21.9; 12 -> +20.0; 15 -> +17.0; 18 -> +14.0; 20 -> +11.1; 23 -> +6.1  (light appearance; dark appearance within 1.5 px throughout, always positive)",
   "unit": "px of along-normal sample offset, positive = from further inside",
   "method": "Measured ONLY on rows 2422-2441, where the backdrop is exactly uniform black, so 100% of the pattern seen there was fetched from elsewhere and the phase is unambiguous. For each pixel: capsule SDF gives depth and outward normal n; a 32-px window lying wholly inside the element is least-squares fitted to A + B*cos(2 pi x/32) + C*sin(2 pi x/32); phase difference against the true stripe phase (fitted the same way on target row 2400, x 569-601) gives the horizontal source offset delta, and m = -delta / n_x is the along-normal offset. Restricted to |n_x| > 0.25 and fit amplitude > 3. Medians per 1-px depth bucket, n = 6 to 36 points each.",
   "coordinates": "IMG_6644/45/47/46, rows 2422-2441, x 343-413 and 756-828",
   "captures": "Positive (inward) at every depth, both ends of the capsule, all four captures. Monotone, no sign change."
  },
  {
   "quantity": "Same profile replicated on the two circular toolbar buttons",
   "value": "left circle: depth 7 -> +33.0/+30.2, 12 -> +19.0/+16.9, 18 -> +12.2/+11.4, 23 -> +6.2/+6.4.  right circle: depth 7 -> +25.9/+24.1, 12 -> +17.6/+15.7, 18 -> +12.3/+12.9, 23 -> +7.6/+7.9  (light/dark appearance)",
   "unit": "px, positive = inward",
   "method": "Identical method, circles fitted as centre (156.5, 2375.5) r=72.5 and (1012.5, 2375.5) r=72.5, icon pixels excluded (glyphs identified by flipping polarity between the two appearance groups, |A-B| > 175, dilated 5 px).",
   "coordinates": "rows 2422-2441, x 84-229 and x 940-1085",
   "captures": "Three independent elements, two curvatures, same answer."
  },
  {
   "quantity": "Rim band width (depth over which the displacement is non-zero)",
   "value": "~28 (26-30)",
   "unit": "px at 3x  ==  ~9.3 dp",
   "method": "Linear fit to the measured profile over depths 10-23 (slope -1.22 px of displacement per px of depth) extrapolated to zero crossing: depth 28. Independently corroborated at the centre row y=2375: the observed matches the backdrop position exactly and at full material contrast for x >= 384 (bright[416,431] = white[416,431], grey[400,415] = black[400,415], single-pixel transitions), and x=384 sits at depth 42 \u2014 but the flat black band ends at x=369, depth 27.",
   "coordinates": "IMG_6644 y=2375 x 342-431; profile from rows 2422-2441",
   "captures": "Our default refractionBand is 26 dp (78 px) and our bar preset 14 dp (42 px) \u2014 both too wide."
  },
  {
   "quantity": "Depth of the interior slab that the rim displays",
   "value": "28 to 41 px inside the edge (9.3-13.7 dp), spread across the outer 28 px, orientation reversed",
   "unit": "px at 3x",
   "method": "source_depth = observed_depth + m(observed_depth) from the measured profile: depth 1-5 -> ~41; 7 -> 36.6; 10 -> 31.9; 15 -> 32.0; 20 -> 31.1; 23 -> 29.1; 28 -> 28 (identity). Source depth DECREASES as observed depth increases, i.e. the band is a mirrored image, magnified roughly 2x. Cross-checked against the centre-row appearance: the model predicts x 343-345 sees source ~384-386 (white) and x 346-368 sees source ~370-383 (black), and the screenshot shows exactly that \u2014 white for 3 px then a dead-flat black band to x=383.",
   "coordinates": "IMG_6644 y=2375, x 342-431",
   "captures": "Between depths 10 and 20 the source depth is nearly constant (~32 px), so over that stretch the mapping reads more as a smear from a fixed depth than as a clean mirror."
  },
  {
   "quantity": "Photos toolbar material: contrast transfer (opacity)",
   "value": "13.9% (light appearance) / 16.2% (dark appearance)",
   "unit": "fraction of backdrop square-wave amplitude that survives",
   "method": "mean over rows 2350-2419 of amp32_observed / amp32_target, x 420-750, icon-masked. Absolute levels at y=2375: light appearance renders backdrop black as 184 and backdrop white as 221; dark appearance renders them 82 and 128.",
   "coordinates": "rows 2350-2419, x 420-750",
   "captures": "So the toolbar is ~86% opaque. Caveat: the Liquid Glass transparency slider position for these captures is unknown."
  },
  {
   "quantity": "Effective blur of the backdrop under the toolbar",
   "value": "~1 (edge transition spread over ~2 px vs 1 px in the source)",
   "unit": "px at 3x",
   "method": "Direct edge profile at y=2375, IMG_6644: x=415 -> 185, x=416 -> 206, x=417 -> 221, against a backdrop step from 0 to 255 at x=415/416. One transitional pixel. Corroborated by the frequency ratio: the 8-px-period pattern transmits at 9.8% and the 32-px at 13.9%, implying a low-pass with sigma of order 1 px. The material is essentially SHARP; its low contrast is opacity, not blur.",
   "coordinates": "IMG_6644, y=2375, x 400-435",
   "captures": "Frequency-ratio sigma is approximate because amp32 is a square-wave matched filter, not a Fourier fundamental."
  },
  {
   "quantity": "Toolbar element geometry (for reproducing the depths)",
   "value": "middle capsule x 342-828, y 2304-2447: 487 x 144 px = 162 x 48 dp, corner radius 72 px = 24 dp (a true capsule); circles at (156.5, 2375.5) and (1012.5, 2375.5), r = 72.5 px = 24 dp",
   "unit": "px at 3x",
   "method": "Chrome mask built as |IMG_6644 - IMG_6647| > 3 (the two appearance groups differ wherever chrome renders, and the light target is common to both). Widest row 2374-2376 gives x 342-828; column x=500 gives the first and last glass rows as 2304 and 2447 (rows 2302-2303 are a faint dark contour/shadow, 2.0 and 4.0 over a black backdrop). Half-width at dy=63.5 matches a capsule with r=72 to within 1 px.",
   "coordinates": "rows 2300-2452, x 300-900",
   "captures": ""
  },
  {
   "quantity": "Registration and capture pairing (sanity checks)",
   "value": "identity registration confirmed; captures pair as {6644, 6645} and {6646, 6647} by appearance",
   "unit": "-",
   "method": "Uncovered rows 500-960 match the target with MAD 0.004 (IMG_6644/6647 vs light) and 0.005 (IMG_6645/6646 vs dark). IMG_6644 and IMG_6645 are bit-identical over rows 140-274 (MAD 0.435) and rows 2302-2450 (MAD 0.050) \u2014 and independently the two targets are bit-identical over exactly those bands (max diff 0.0), so this is the expected consequence of identity registration, not a duplicate file. IMG_6646/6647 form the other appearance group.",
   "coordinates": "full frame",
   "captures": "IMG_6644 and IMG_6645 are the same system appearance over different targets, not the same screenshot."
  },
  {
   "quantity": "The two capsules over the grey ramp (rows 347-405) are unusable as a probe",
   "value": "horizontal gradient attenuated to 5.5%; interior essentially flat at 231-233 (6644), 205 (6645), 108 (6647), 83 (6646)",
   "unit": "8-bit levels",
   "method": "At y=376 the backdrop ramp (verified slope 0.21814 levels/px, intercept ~0) rises from 189.8 at x=870 to 217.3 at x=996, a 27-level climb; the glass interior over that span moves by 1-2 levels. The 27-level difference between IMG_6644 and IMG_6645 at the same rows is driven entirely by the target's divergence at rows >= 390 (white vs black), 14+ px below the centre row, implying ~10.6% response to a 255-level step at that distance.",
   "coordinates": "y=376, x 860-1130, all four captures",
   "captures": "These elements are near-opaque; the brief's suggestion to read displacement direction off them cannot work."
  }
 ],
 "method_notes": "ALL WORK IS IN C:/Users/shaya/AppData/Local/Temp/claude/D--Work-AndroidStudioProjects-SideProjects-kmp-Vitals/d83608da-a0a9-446c-9f1b-ec02e5b88c50/scratchpad/caps/work/ (scripts s01..s25, plus proof_top.png and proof_bottom.png which show the target backdrop above and the screenshot below for the same crop).\n\nWHY THE EXPERIMENT AS BRIEFED COULD NOT ANSWER THE QUESTION. The \"Today\" pill is a capsule spanning x 345-826, y 142-274, centred at x=585.5 \u2014 essentially exactly on the target's hard split. But along a capsule's straight top and bottom runs the surface normal is VERTICAL, so the displacement there is vertical and cannot move a vertical boundary sideways. And the pill's two caps, where the normal IS horizontal, sit at x 345-411 (uniformly black on both sides) and x 760-826 (uniformly white). So the pill has no signal in the direction the test needs. I also tried the pill's bottom rim against the split-to-ramp boundary at y=260; it is inconclusive because the specular rim line saturates the white side at 255 (rows 269-272). The two round buttons at rows 347-405 turned out to be ~95% opaque. The decisive geometry was elsewhere: the Photos bottom toolbar.\n\nTHE INSTRUMENT THAT WORKED. The target's tab-bar zone changes pattern with ROW: rows 2262-2341 are vertical stripes of period 8 px, rows 2342-2421 of period 32 px, rows 2422-2441 solid black. The toolbar's middle capsule occupies rows 2304-2447, so it straddles two of those boundaries, and both are NON-PERIODIC in y. On the capsule's straight top and bottom runs the normal is exactly vertical, and vertical stripes are invariant under any purely vertical remapping. That removes every ambiguity: whatever the displacement does vertically, the observed horizontal PERIOD can only be the period of whichever row the sample was taken from.\n\nTEST B (top rim). Backdrop rows 2262-2341 have amp32 = -0.75 in every single row (checked all 80). Screenshot rows 2305-2309 have amp32 = 15.4 to 15.7 (light appearance) / 22.3 to 22.4 (dark) with amp8 collapsed from the interior's 12.5 to 0.0-0.2. The 32-px pattern first exists at row 2342, i.e. 33-38 rows further INSIDE. Row 2310 is a mixture (amp32 3.4, amp8 9.9) and row 2311 is back to normal (amp32 -0.1, amp8 12.4).\n\nTEST A (bottom rim). Backdrop rows 2422-2441 are exactly 0 everywhere in x 420-750 in BOTH targets (min == max == 0.0). Screenshot amp32 runs 20.3 (row 2422) to 21.9 (row 2444) in the light appearance and 18.2 to 16.1 in the dark, against 20.3 / 18.3 in row 2421 where the stripes genuinely are. proof_bottom.png shows this by eye.\n\namp32/amp8 DEFINITION: for a row, subtract its own mean over the x window, then take the mean of (signal * w) where w32(x) = +1 if (x mod 32) < 16 else -1, and w8(x) = +1 if (x mod 8) < 4 else -1. Both square waves are phase-locked to the target's own patterns. For a clean square wave of amplitude +-127.5 this returns 127.5. Icon glyphs are masked: they flip polarity between the two appearance groups, so icon = dilate(|IMG_6644 - IMG_6647| > 175, 4 or 5 px) OR the same for the 6645/6646 pair.\n\nTEST C (horizontal, and the profile). Using only rows 2422-2441 \u2014 where the backdrop is exactly black, so 100% of any pattern seen was fetched from elsewhere and its phase is unambiguous \u2014 I fitted A + B*cos(2 pi x/32) + C*sin(2 pi x/32) by least squares in a 32-px window lying wholly inside the element, took the phase difference against the true stripe phase, converted to a horizontal source offset delta (source_x = pixel_x + delta), and projected onto the surface normal as m = -delta / n_x, keeping |n_x| > 0.25 and fit amplitude > 3. m came out POSITIVE (sample from further inside) at every depth from 6 to 25 px, at BOTH ends of the capsule and on BOTH circles, in all four captures.\n\nTEST D (direct inspection, no fitting at all). IMG_6644, y=2375, left cap: the glass reads 210/214/216 at x 343-345 (its white level is 221) and then a DEAD-FLAT 181-185 (its black level) from x=346 to x=383, before matching the backdrop exactly and at full contrast from x=384 on. The backdrop underneath that flat band is black[336,351], WHITE[352,367], black[368,383]. A 38-px band that reads uniformly black over a backdrop containing a 16-px white stripe can only be sampling from within a single black run. Displacement must be zero at x=384 (proved by the exact 48-px match from there on) and must grow toward the rim, so the source run has to be black[368,383] \u2014 which lies INSIDE, to the right. The outward assignment (black[336,351]) would require the displacement to be largest at x=383 and smallest at the rim, i.e. backwards. Confirmed identically in IMG_6647 with its own levels (white 128, black 82).\n\nSIGN CONVENTION USED THROUGHOUT: observed(u) = Backdrop(s(u)), delta = s(u) - u. At the left rim of an element, delta > 0 means the sample is taken to the right, i.e. toward the centre, i.e. INWARD. Note that Tests A, B and D need no sign convention at all \u2014 they turn on which ROW or which RUN a pattern can physically come from.\n\nCONVERSION: 1 dp = 3 px on this device.",
 "confirms_us": [
  "The magnitude we tuned by eye is close to right. Our default refractionDepth is 14 dp; the measured peak displacement is ~40 px = 13.3 dp. Only the sign is wrong.",
  "The rim image really is sharp, not blurred. Inside the glass a 0-to-255 backdrop step lands as 185 -> 206 -> 221 across a single transitional pixel (y=2375, x 415-417), and the 8-px pattern transmits at 9.8% against the 32-px pattern's 13.9%, implying a low-pass with sigma of order 1 px. Recording the backdrop UNBLURRED and keeping the scatter inside the shader (GlassShaderSource.kt note 2) is validated; a pre-blurred backdrop could not produce this.",
  "Displacement is normal-directed and dies in the interior, exactly as our SDF-plus-gradient model assumes. On the capsule's straight runs, where the normal is vertical, the stripe pattern's horizontal position matches the backdrop to within 0.2 px over hundreds of pixels; the horizontal displacement is non-zero only in the caps.",
  "Our shape model is right for these elements. The Photos toolbar's middle element is a true capsule (corner radius = half-height = 24 dp), and its measured half-width at dy=63.5 matches a capsule to within 1 px.",
  "Our uMirror term already samples inward: `echo = backdropAt(base - n * (mirrorMask * lens * 3.0))`. The direction of that one term is correct \u2014 it is the main refraction term that points the wrong way."
 ],
 "contradicts_us": [
  {
   "ours": "GlassShaderSource.kt main(): `float2 push = n * bend * lens;` with n the OUTWARD normal, then `backdropAt(base + push)`. The rim samples away from the element, and the host records uPad beyond each edge specifically to feed it.",
   "measured": "The sample is taken INWARD at every depth measured, on three separate elements, in all four captures. Over the toolbar's bottom 26 px the backdrop is exactly uniform black (min == max == 0.0, rows 2422-2441) and the glass still renders the 32-px stripe pattern at 100-108% of the amplitude it produced one row earlier where the stripes were real. That pattern exists only at rows 2342-2421, inside the element.",
   "significance": "This is the load-bearing one. It inverts the sign of the main refraction term. The padded backdrop (uPad) is not needed by the measured behaviour, though it stays harmless."
  },
  {
   "ours": "The doc comment at GlassShaderSource.kt lines 15-20: 'Apple describes the material as sampling content from an area larger than itself, and only *outward* displacement needs that.'",
   "measured": "The rim shows the element's own interior, from a slab lying 28-41 px (9.3-13.7 dp) inside the edge, spread across the outer 28 px with its orientation reversed. Whatever Apple's wording means, it is not describing what the rim does.",
   "significance": "The argument we built the library's central decision on is not supported by the pixels. Every competitor that samples inward was right about the direction."
  },
  {
   "ours": "refractionBand default 26 dp (78 px); the bar preset uses 14 dp (42 px).",
   "measured": "~28 px = 9.3 dp, from a linear fit to the displacement profile over depths 10-23 px (slope -1.22 px per px, zero crossing at depth 28), corroborated by the centre-row band ending at x=369.",
   "significance": "Our band is 1.5x to 2.8x too wide. Combined with the wrong sign, the rim spreads a weak wrong-direction warp over three times the area iOS uses for a strong one."
  },
  {
   "ours": "uMirror is a broad, weak, blended echo: mirrorWidth = max(14, min(halfSize) * 0.7), which for this element is 50 px, and it is mixed in at amplitude 0.05-0.22.",
   "measured": "The inward content occupies ~28 px and REPLACES the local backdrop completely inside the band rather than blending with it. At the top rim (rows 2305-2309) the local 8-px pattern falls to amp8 = 0.0-0.2 from an interior value of 12.5, while the interior's 32-px pattern appears at 15.4-15.7 against an in-zone reference of 15.8 \u2014 98-99% substitution, not a partial mix.",
   "significance": "If the fix is to repurpose the mirror term as the main refraction, its width and its blend law both need rewriting, not just its weight."
  },
  {
   "ours": "Our presets assume a fairly transparent, visibly blurred material (blurRadius up to 16 dp).",
   "measured": "The Photos toolbar transmits only 13.9% (light appearance) / 16.2% (dark) of backdrop contrast, with an effective blur of about 1 px. It is a near-opaque, near-sharp material, not a soft translucent one.",
   "significance": "Secondary and caveated: the Liquid Glass transparency slider position for these captures is unknown, and the READ-ME-FIRST asks for that number precisely because it moves opacity. Do not retune opacity on this alone."
  }
 ],
 "uncertain": [
  "The peak displacement in the outermost 0-5 px is extrapolated, not measured. The specular rim line and partial pixel coverage destroy the phase estimate there, so the fit windows start at depth 6. The vertical test bounds it at >= 38 px and the white sliver at x 343-345 puts it near 40-43 px, giving 40-44 px; I would defend '~40' but not a second significant figure.",
  "Whether the rim is one warped sample or two composited layers. The band substitutes the interior content essentially completely (98-99%), and row 2310 at the top rim is a ~25/75 mixture of deep and local source, which reads more like a mask with a 1-2 px falloff than like a single continuous warp. A shader that composites a separate inward echo over a normally-refracted layer would fit the data equally well.",
  "Whether the mapping inside the band is a true mirror. Source depth runs ~41 px at the edge down to ~28 px at the band's inner limit, which is a reversal \u2014 but between observed depths 10 and 20 the source depth is nearly constant at ~32 px, so over most of the band it behaves like a smear from a fixed depth rather than a clean fold. I cannot separate those two with this data because the 32-px stripes are invariant in y, so I can never localise a source row inside the stripe zone.",
  "Whether ANY outward component exists at small amplitude. I measured none anywhere, but the phase noise floor is about +-0.3 px and dividing by |n_x| amplifies it, so an outward component below roughly 1-2 px would be invisible to me.",
  "The exact band width. 28 px comes from extrapolating a linear fit to zero; the profile is not exactly linear and the last well-sampled bucket is depth 23. Honest range 26-30 px.",
  "The absolute opacity number. The Liquid Glass transparency slider position was not recorded for these captures, and the READ-ME-FIRST flags that as the likely cause of a factor-of-two discrepancy already sitting in our measurements document. The 13.9%/16.2% figures are correct for THESE frames and may not generalise.",
  "Nothing here measures the top chrome. The 'Today' pill, the back button and the more button gave no usable displacement signal for the reasons in the method notes, so the result is established on the bottom toolbar only. If iOS treats bars and pills differently, this data cannot see it.",
  "The 'more' button (x 988-1123) and back button (x 48-179) sit over uniform white and uniform black respectively, so the briefed test of 'does a wholly-over-black button show anything bright' has no reach: the nearest contrasting content is ~400 px away, far beyond any displacement measured here. That test was never going to fire."
 ]
}
```

---

## Measured: Rim geometry and displacement

Agent `a488850707640317b`.

```json
{
 "headline": "The refraction on a 50pt iOS 27 toolbar button displaces INWARD, not outward \u2014 a ~35px (11.7dp) rim band that magnifies the element's own footprint up to 3.8x and then folds into a mirrored, 2.7x-compressed outer ring; nothing from outside the button's footprint appears anywhere inside it.",
 "confident": [
  {
   "quantity": "Bottom-toolbar button geometry (circle), IMG_6644",
   "value": "left button centre (156.0, 2377.3), R = 74.35; right button centre (1013.7, 2377.5), R = 74.39; centre pill's left cap centre (415.8, 2375.1), R = 74.87",
   "unit": "screenshot px",
   "method": "Per-row and per-column outermost pixel where |shot - target| > 2, taken as the boundary at \u00b10.5px, then an algebraic (Pearson/Kasa) circle fit. Residual std 0.67px for the pill cap (clean semicircular arc), ~3.3px for the full circles (the threshold misses boundary pixels where glass output happens to equal the black backdrop).",
   "coordinates": "search windows x 60-300 and x 920-1140, rows 2280-2470",
   "captures": "IMG_6644, 6645, 6646, 6647 \u2014 R reproduces to within 0.4px across all four"
  },
  {
   "quantity": "Button size in dp at 3x",
   "value": "R = 74.4 \u00b1 0.3 px = 24.8 dp; diameter 148.8 px = 49.6 dp",
   "unit": "px / dp",
   "method": "Circle fit above, divided by 3.",
   "coordinates": "as above",
   "captures": "a ~50pt control, which is the standard iOS toolbar button"
  },
  {
   "quantity": "Direction of the refraction displacement",
   "value": "INWARD (sampling offset points toward the element centre). D is negative throughout.",
   "unit": "sign",
   "method": "Model-free proof: target rows 2422-2441 are pure black at EVERY x. Inside the left button at row 2430 the observed values are 214 at x=128-136, 169-170 at x=144-156, 214 at x=164-184. Light content at those pixels cannot come from the backdrop at or below y=2422 (all black) and cannot come from sideways (row 2430 is black across the whole width), so the sample point moved upward \u2014 i.e. toward the button centre. The banding also lines up column-for-column with the 32px stripes that live at y=2342-2421, above.",
   "coordinates": "row 2430, x 128-184 (left button, centre y=2377.3, so this is 53px below centre, depth ~13px from the rim)",
   "captures": "identical structure in all four captures and in both buttons"
  },
  {
   "quantity": "Displacement D vs depth from the rim (D<0 = inward). R=74.37px.",
   "value": "depth_px/depth_dp/D_px/D_dp -- 38.4/12.8/0.00/0.00 | 36.4/12.1/+0.08/+0.03 | 34.4/11.5/-0.75/-0.25 | 32.4/10.8/-1.20/-0.40 | 30.4/10.1/-1.95/-0.65 | 28.4/9.5/-2.95/-0.98 | 26.4/8.8/-4.30/-1.43 | 24.4/8.1/-5.90/-1.97 | 22.4/7.5/-7.85/-2.62 | 20.4/6.8/-9.75/-3.25 | 18.4/6.1/-12.35/-4.12 | 16.4/5.5/-15.40/-5.13 | 14.4/4.8/-18.25/-6.08 | 12.4/4.1/-22.82/-7.61 | 10.4/3.5/-26.97/-8.99 | 8.4/2.8/-36.35/-12.12 | 6.4/2.1/-41.87/-13.96 | <6px unreadable (lighting-dominated)",
   "unit": "px and dp at 3x",
   "method": "Radial annulus correlation. For each output radius r, sample the screenshot on a 2880-point ring at radius r about the fitted centre (bilinear). Mask pixels <=110 (the opaque glyph drawn on top; the glass output range is 165-225, so the split is unambiguous). Least-squares remove angular harmonics 0-3 from the ring signal \u2014 this kills the tint offset and the two-lobe specular. For each candidate sampled radius r_s on a 0.1px grid spanning r\u00b146, sample the KNOWN target on the ring of radius r_s about the same centre, remove the same harmonics, and take the Pearson correlation. r_s = the sub-pixel parabolic peak. D = r_s - r. Values above are the mean of 4 independent fits (2 buttons x 2 captures) that agreed to within 0.1-0.7px over depth 10-38. Peak correlations 0.93-0.96 over depth 22-34, 0.75-0.85 over depth 6-18, with no competing local maximum within 5x the peak height.",
   "coordinates": "left button centred (156.0,2377.3), right button (1013.7,2377.5), rings r=34..70. Backdrop is the 8px stripes (rows 2262-2341), 32px stripes (rows 2342-2421) and solid black (rows 2422-2441).",
   "captures": "IMG_6644/6645 both buttons; IMG_6647/6646 reproduce the same profile with more scatter (their chrome sits 0.6px lower)"
  },
  {
   "quantity": "Width of the refraction band (depth at which displacement becomes non-zero)",
   "value": "35 \u00b1 2 px = 11.7 \u00b1 0.7 dp, i.e. 0.47 R",
   "unit": "px / dp",
   "method": "From the D table: the circle gives D = 0.00/+0.08 at depth 36.4-38.4 and -0.75 at 34.4 (spread across the 4 fits only 0.10px, correlation 0.85, so this is resolved, not noise). The centre pill's left cap independently gives D = 0.00 at depth 34.9 and -0.75 at 32.9. Measurement floor is about \u00b10.3px, so anything shallower than ~35px depth is flat within the instrument.",
   "coordinates": "rings r=36-42 about (156.0,2377.3) and about (415.8,2375.1)",
   "captures": "two different element shapes (circle and capsule cap) agree to 1.5px"
  },
  {
   "quantity": "Peak displacement and where it occurs",
   "value": "|D| >= 41.9 px = 14.0 dp, measured at depth 6.4 px (2.1 dp) and still climbing steeply. The fitted profile extrapolates to ~71 px (23.7 dp) at depth 0, but that is extrapolation, not measurement.",
   "unit": "px / dp",
   "method": "Innermost reliably-read ring is r=68 (depth 6.4), correlation 0.74, four-fit spread 8.4px so call it 42 \u00b1 4px. Rings r=70-73 return correlations of 0.31-0.67 and D values near 0, which is the partial-coverage antialias fringe blending in the true undisplaced backdrop, not a real measurement of refraction. The outermost ~5px is dominated by the rim lighting: the top rim (rows 2303-2306) is a washed-out bright band while the bottom rim (rows 2447-2450) is 0-1, an up/down asymmetry that is illumination, not refraction.",
   "coordinates": "ring r=68 about (156.0,2377.3); rim lighting checked at cols 120-200, rows 2302-2306 and 2446-2451",
   "captures": "all four"
  },
  {
   "quantity": "Profile shape, analytic fit",
   "value": "|D| = 71.0 * (1 - depth/49.8)^3.85 px, rms residual 0.71 px over depth 6.4-38.4",
   "unit": "px",
   "method": "Grid search over amplitude A (0.5px steps), band W (0.25px steps) and exponent k (0.05 steps) against the 17-point D table. The fitted W=49.8px (16.6dp) is where the extrapolated profile hits exactly zero; the profile is already under 1px by depth 32, which is why the *measurable* band is 35px.",
   "coordinates": "fit to the D table above",
   "captures": "IMG_6644/6645, both buttons"
  },
  {
   "quantity": "The profile is monotonic in |D| but the sampling map FOLDS",
   "value": "|D| increases monotonically all the way to the rim (no ringing). But r_s = r + D peaks at 44.2 px when r = 54 (depth 20.4 px = 6.8 dp) and then decreases to 26.1 px at r = 68. Fold point: depth \u2248 21 px = 7.0 dp.",
   "unit": "px / dp",
   "method": "Directly from the r_s column of the D table. d(r_s)/dr computed by central difference: +1.04 at depth 38, +0.26 at depth 26, +0.04 at depth 22, -0.12 at depth 20, -0.86 at depth 14, -2.73 at depth 8. It crosses zero at depth 21. A negative d(r_s)/dr means the outer ring shows a mirrored copy of the annulus r_s in [26, 44].",
   "coordinates": "rings r=34-68 about (156.0,2377.3)",
   "captures": "IMG_6644/6645 both buttons; the pill cap shows the same turn"
  },
  {
   "quantity": "Maximum sampled radius \u2014 the glass never reaches outside itself",
   "value": "max r_s = 44.2 px = 14.7 dp = 0.59 R. Nothing from beyond 44 px of the button's centre appears anywhere in a button of radius 74.4 px, so nothing from OUTSIDE the element is shown at all.",
   "unit": "px / fraction of R",
   "method": "Maximum of the r_s column. Corroborated model-free at the bottom rim: rows 2422-2441 of the backdrop are pure black across the full screen width, yet the bottom of the button renders light stripes down to y\u22482444 (e.g. col 168: 217 at row 2443, 210 at 2444, 202 at 2445, 166 at 2446, 1 at 2447). If the rim sampled outward it would be black there.",
   "coordinates": "col 168, rows 2411-2447; col 128, rows 2406-2443",
   "captures": "all four"
  },
  {
   "quantity": "Local magnification / compression of the backdrop image",
   "value": "1.00x (identity) for depth > 36 px; stretch rising to 3.8x at depth 24 px; sign flip at depth 21 px; then mirrored and compressed, reaching 2.7x compression at depth 8 px",
   "unit": "x (d(r_s)/dr)",
   "method": "Central difference of r_s against r from the D table. Magnification = 1 / |d(r_s)/dr|.",
   "coordinates": "rings r=36-68 about (156.0,2377.3)",
   "captures": "IMG_6644/6645"
  },
  {
   "quantity": "Transmitted backdrop contrast is flat with depth (the rim is not blurrier than the middle)",
   "value": "ring modulation / source ring modulation = 0.152, 0.139, 0.145, 0.145, 0.152, 0.150, 0.155, 0.137, 0.131, 0.154 at depths 38.4, 34.4, 30.4, 26.4, 22.4, 18.4, 14.4, 10.4, 8.4, 6.4",
   "unit": "ratio",
   "method": "Standard deviation of the harmonic-detrended observed ring divided by the standard deviation of the harmonic-detrended target sampled at the fitted r_s. Because the outer band compresses 2.7x, at depth 8.4 the material is transmitting a 12px output feature at the same 13% amplitude that the interior transmits a 32px feature \u2014 so the rim's MTF is materially better than the interior's.",
   "coordinates": "rings r=36,40,...,68 about (156.0,2377.3)",
   "captures": "IMG_6644"
  },
  {
   "quantity": "The two capsules over the grey ramp (rows 347-406) are effectively OPAQUE and carry no displacement information",
   "value": "backdrop transmission = -0.8% (left, x 875-993) and +2.8% (right, x 1025-1118), i.e. zero within noise",
   "unit": "% of backdrop gradient passed",
   "method": "Linear regression of shot value against target value over rows 360-394 inside each capsule, excluding pixels <150 (the black glyphs). Left: shot = -0.0080*target + 231.8 over n=3607. Right: shot = +0.0283*target + 229.7 over n=2466. Interior is a flat 231-233 / 239 while the backdrop ramp under it runs 190->217 / 223->244.",
   "coordinates": "rows 347-406, x 870-996 and x 1021-1121, IMG_6644",
   "captures": "the brief calls these 'two small round buttons' and the cleanest instrument; they are in fact capsules (127x60 and 101x60 px) and they are opaque, so the grey ramp yielded nothing"
  },
  {
   "quantity": "Registration sanity check on the region used",
   "value": "exact",
   "unit": "px",
   "method": "Uncovered grey-ramp pixels at row 320 match round(x*255/1169) exactly at x = 100, 237, 374, 511, 648, 785, 922, 1059 (22, 52, 82, 111, 141, 171, 201, 231). Row-mean |shot-target| > 3 isolates exactly the bands named in the brief: rows 0-108, 140-274, 354-404, 960-1119, 2157-2246, 2303-2448.",
   "coordinates": "row 320 across the ramp; whole-image row profile",
   "captures": "IMG_6644 vs calibration-target-light.png"
  }
 ],
 "confirms_us": [
  "Refraction really is confined to a band measured in from the rim, with a genuinely flat 1:1 interior. Everything deeper than 36px (12dp) from the edge shows the backdrop undisplaced to within the ~0.3px measurement floor \u2014 the architecture of uRefractBand as a rim-referenced band, zero in the middle, is correct.",
  "|D| is monotone in depth with no ringing or secondary lobe, and peaks at the rim. Our bend profile has the same qualitative shape (monotone, rim-maximal).",
  "Our peak magnitude is close to right. uRefractDepth = 14dp = 42px, and the deepest reliably-read ring gives 41.9px = 13.96dp at depth 6.4px. That is a near-exact hit on the magnitude, even though the direction and the falloff are both wrong.",
  "The rim carries a sharper image than the interior. Transmitted contrast stays at ~14% of source at every depth while the rim compresses 2.7x, so the rim passes ~12px features at the amplitude the interior passes ~32px features. Shader note 2 ('the rim is sharper than the middle', scatter keyed to distance from the edge) is confirmed on a real device.",
  "GlassStyle's comment that published tuned values for refraction depth span 9-24dp and that 14dp sits 'at the device-matched end' holds up: the measured value at the innermost readable depth is 14.0dp."
 ],
 "contradicts_us": [
  {
   "ours": "push = n * bend * lens with n the OUTWARD normal \u2014 we sample outward, pulling content from beyond the element inward and compressing it into the rim. Shader note 1 makes this the load-bearing claim, and the whole uPad / uBackdrop padded-backdrop architecture exists to serve it.",
   "measured": "The displacement points INWARD everywhere. D is negative at every measurable depth (-0.75px at depth 34 through -41.9px at depth 6). The maximum radius the material ever samples is 44.2px inside a 74.4px button \u2014 0.59 R. It never touches a single pixel from outside its own footprint.",
   "significance": "This is a sign flip on the core term, not a tuning error. If it generalises, uPad buys nothing for elements of this size and the material should be renderable from an unpadded backdrop. It also inverts the visual read: the real thing magnifies its own backdrop like a droplet; ours squeezes the surroundings in."
  },
  {
   "ours": "uRefractBand default 26dp = 78px on this device.",
   "measured": "35 \u00b1 2 px = 11.7 \u00b1 0.7 dp on a 49.6dp button (0.47 R). The pill cap independently gives 34px.",
   "significance": "Ours is 2.2x too wide. On a 50dp control our band (78px) exceeds the inradius (74px), so the whole element refracts and the shader is forced down the sampled-gradient + dome path; the real material leaves the middle 53% of the radius perfectly flat."
  },
  {
   "ours": "Exact-Snell falloff on a superellipse bevel (bevelPower 2, ior 1.5), normalised to 1 at the rim. At 35% of the band it is still 55% of peak; at the band's midpoint it is 31% of peak.",
   "measured": "Far more concentrated at the rim. At 35% of the measured band |D| is ~10% of peak; the profile is well described by |D| = 71.0*(1 - depth/49.8)^3.85 (rms 0.71px). Fitting our exact-Snell shape to the measurement needs band 34px and peak 89.5px and still leaves rms 3.14px \u2014 it cannot make both the tail and the peak.",
   "significance": "Our profile has a long tail that the real one does not. Even at the correct band width our shader would put visible displacement across the whole element where the reference has none. The measured exponent ~3.85 is a much better starting point than the Snell form."
  },
  {
   "ours": "bend is clamped to [0,1] and multiplied by smoothstep(1.0, 0.75, e), so |dD/d(depth)| stays below 1 and the sampling map is always monotone \u2014 it never folds.",
   "measured": "The map folds. d(r_s)/dr crosses zero at depth 21px (7dp) and reaches -2.73 by depth 8px, so the outer 21px of the button shows a mirrored, up-to-2.7x-compressed copy of the annulus 26-44px from the centre.",
   "significance": "The fold is what produces the reference's signature outer ring. We cannot reproduce it with the current clamp, no matter how the constants are set."
  },
  {
   "ours": "uMirror = 0.18 \u2014 a separate, additive, blended upside-down echo over the outer third, mixed in after the refraction sample.",
   "measured": "The mirroring is the displacement field itself, single-sample. The transmitted contrast ratio is flat at 0.131-0.155 from depth 38 straight through depth 6.4 \u2014 a blended superposition of two anti-correlated images would visibly wash the outer band out, and it does not.",
   "significance": "uMirror is modelling the right phenomenon with the wrong mechanism. Folding the displacement profile would give it for free and would also remove a separate uniform, and would keep rim contrast where the reference keeps it."
  },
  {
   "ours": "uRefractDepth documented as 'px of displacement at the rim' with lens = uRefractDepth * mix(0.85, 1.25, scale), i.e. 35.7-52.5px.",
   "measured": "41.9px at depth 6.4px and still rising; the fitted profile extrapolates to ~71px (23.7dp) at depth 0, which is outside our uScale range entirely.",
   "significance": "Magnitude is plausibly a little low at the rim, but this is the one number where we are close, and it is the one that is least worth changing until the direction and band are fixed."
  }
 ],
 "method_notes": "All work in C:/Users/shaya/AppData/Local/Temp/claude/D--Work-AndroidStudioProjects-SideProjects-kmp-Vitals/d83608da-a0a9-446c-9f1b-ec02e5b88c50/scratchpad/caps/work/ (radial2.py holds the helpers; side.png is the model-vs-observed comparison; model_render.png and obs_render.png the halves). Nothing was written into D:/liquidglass-captures or the repo.\n\nWHY THE GREY RAMP WAS ABANDONED. The two elements at rows 347-406 are 127x60 and 101x60 px capsules, not circles, and they are opaque: regressing shot against target over their interiors (rows 360-394, glyph pixels <150 excluded) gives slopes of -0.008 and +0.028, so essentially none of the ramp gradient survives. No displacement can be recovered from them. The instrument that worked is the bottom toolbar over the striped tab-bar zone.\n\nBACKDROP STRUCTURE ACTUALLY UNDER THE TOOLBAR (measured off calibration-target-light.png by finding rows where the transition set changes): rows 2172-2261 hard split at x=584.5; rows 2262-2341 8px square wave (transitions at x=3,7,11,...); rows 2342-2421 32px square wave (transitions at x=15,31,47,...); rows 2422-2441 solid black; rows 2442+ white text on black. The buttons span rows 2301-2451, so each one crosses three of those zones \u2014 which is what makes a 2D radial fit identifiable.\n\nGEOMETRY. Per-row and per-column outermost pixel with |shot - target| > 2 taken as the boundary at \u00b10.5px, algebraic circle fit to the resulting point set. Left button (156.0, 2377.3) R=74.35; right (1013.7, 2377.5) R=74.39; the centre pill's left cap, fitted from rows 2315-2440 of its left boundary only, (415.8, 2375.1) R=74.87 with residual std 0.67px.\n\nDISPLACEMENT FIELD \u2014 RADIAL ANNULUS CORRELATION. The warp is radial about the fitted centre, so parameterise it as r -> r_s(r). For each output radius r: sample the screenshot bilinearly on a 2880-point ring; drop pixels <=110 (the opaque SF Symbol drawn on top \u2014 glass output lives in 165-225 so the cut is unambiguous; below r=63 nothing is masked at all in the left/right buttons); least-squares subtract angular harmonics 0-3, which removes the tint pedestal and the two-lobe specular that otherwise bias the fit. Then for each candidate r_s on a 0.1px grid over r\u00b146, sample the KNOWN target on the ring of radius r_s about the same centre, subtract the same harmonics, and take the Pearson correlation. Take the sub-pixel parabolic peak. Repeat for 2 buttons x 2 captures and report the mean. Correlation is invariant to the material's affine intensity transfer, which is why no tint/opacity model is needed.\n\nVALIDATION OF THE RADIAL MODEL. Forward-rendered the left button from the fitted r_s(r) plus a single global affine intensity map (fit: out = 0.1169*src + 183.3): rms residual 14.4/255 over the non-glyph interior, and the render reproduces the fan of compressed stripes at the top shoulders, both dark lobes, and the light bands (work/side.png, model left, observation right). The only unmodelled structure is the glyph and the specular ring.\n\nRULING OUT OUTWARD DISPLACEMENT WITHOUT ANY MODEL. Backdrop rows 2422-2441 are pure black at every x on the screen. Inside the left button at row 2430 the observed values are 214 at x=128-136, 169-170 at x=144-156, 214 at x=164-184, and those bands line up column-for-column with the 32px stripes that live at rows 2342-2421 (white 128-143, black 144-159, white 160-175). The light can only have come from above, i.e. from further inside the button. Same at column 168: light (217) down to row 2443 where the backdrop went black at 2421.5.\n\nRULING OUT A BLENDED MIRROR ECHO IN FAVOUR OF A TRUE FOLD. Computed the ratio of observed ring modulation to the modulation of the target sampled at the fitted r_s, at ten radii. It is flat at 0.131-0.155 from depth 38.4 to depth 6.4. A superposition of a refracted and an anti-correlated mirrored image would drop that ratio sharply in the folded outer band; it does not move.\n\nREPRODUCIBILITY. Two buttons x two light captures agree to 0.1-0.7px over depth 10-38. IMG_6645/6646 (dark target) reproduce the light-target numbers to the 0.25px fit grid. IMG_6647/6646 have their chrome 0.6px lower and give noisier but consistent values. The centre pill's capsule cap (a different shape, same 74.9px cap radius) gives the same band onset within 1.5px and displacements systematically 15-20% smaller at matched depth.\n\nDP CONVERSION. 1dp = 3px on this device per the brief, so px/3 throughout. The button is a 49.6dp diameter control, consistent with a 50pt iOS toolbar button.",
 "uncertain": [
  "The outermost ~5px (depth 0-5px, 0-1.7dp) cannot be read at all. The rim lighting dominates there and is strongly up/down asymmetric (top rim rows 2303-2306 wash out to a uniform bright band; bottom rim rows 2447-2450 read 0-1), which is illumination, not refraction. So the true peak displacement and the exact behaviour at the outline are extrapolation. The fitted profile says ~71px (23.7dp) at depth 0; all I can defend is >=42px (14dp) at depth 6.4px and still rising.",
  "Whether the band and the peak scale with element size \u2014 the whole point of our uScale. Both usable elements have the same 74.5px cap radius, and the only smaller elements in the dataset (the ramp capsules, 30px half-height) are opaque. This dataset cannot answer it. Getting it would need a capture where a distinctly smaller or larger Liquid Glass element sits over the striped zone.",
  "Whether a larger element ever samples outside its own footprint. I can only say that this 50pt button does not, anywhere.",
  "The exact position of the fold (depth 21px = 7dp) rests on a central difference of a fitted curve near its turning point, where d(r_s)/dr is small; \u00b13px is a fair error bar. The existence of the fold is much better established than its location.",
  "Whether the fold is literally a single non-monotone sample or a two-sample construction that happens to be indistinguishable at this contrast. The flat transmitted-contrast ratio argues strongly for a single sample, but a 100%-opacity mirrored replacement (rather than a blend) over the outer band would look the same to this test.",
  "The systematic 15-20% gap between the circle's displacements and the capsule cap's at matched depth. It could be a real shape dependence (the capsule's distance field is not radial past the cap seam) or a small error in either radius fit. Not resolved.",
  "Nothing here measures chromatic dispersion, the blur radius, the bevel lighting profile, or the tint \u2014 only the geometry of the displacement.",
  "The top chrome (rows 140-274) was not usable for this: the back button sits over uniform black, the 'more' button over uniform white, and the hard split at x=584.5 falls in the dead centre of the 'Today' pill where displacement is zero by symmetry."
 ]
}
```

---

## Measured: Interior blur radius and rim sharpness

Agent `a92c07d24621eee65`.

```json
{
 "confident": [
  {
   "quantity": "Interior blur sigma of the SHARP transmission path (bottom toolbar glass)",
   "value": "0.5-0.8",
   "unit": "px (0.17-0.27 dp at 3x)",
   "method": "Per-row forward-model fit. Model: obs(x) = c0 + c1*x + a * [box-pixel target row, upsampled 16x, convolved with Gaussian(sigma), re-integrated over the 1px capture aperture], with a sub-pixel shift parameter. Grid-searched sigma in 0.05px steps. Fitter validated: on the uncovered stripe edges in the same screenshot it returns sigma = 0.100 (the search floor) with rms 0.00; on synthetic edges it recovers true sigma to 3 decimals up to sigma=8.",
   "coordinates": "IMG_6644 (light chrome) and IMG_6646 (dark chrome), bottom toolbar pill straight run, x 470-534, rows 2310-2420. Light: sigma 0.60-0.75. Dark: sigma 0.75-1.05."
  },
  {
   "quantity": "Model-free line-spread function of the interior, from the folded 32px stripe profile",
   "value": "3 px wide, normalised weights (0.036, 0.549, 0.415); second moment sigma = 0.55 px",
   "unit": "px",
   "method": "Folded all 32px stripe periods over the pill's straight run into one 32-bin period profile (phase = x mod 32), then differenced. The LSF is 3 pixels wide and its shape is exactly what a single bilinear texture fetch at a ~0.43px fractional offset produces (2-tap box, sigma 0.50). Any blur beyond resampling is <= 0.3 px.",
   "coordinates": "IMG_6644, rows 2344-2420, x 415-755, icons masked. Repeated in three depth bands (30-45, 40-52, 60-73 px): LSF shape identical, only the amplitude changes."
  },
  {
   "quantity": "Contrast ratio, 8px period vs 32px period, at matched depth - the tint-independent MTF test",
   "value": "0.988 (light), 0.990 (dark)",
   "unit": "dimensionless ratio",
   "method": "Amplitude ratio (peak-trough inside / 255) at 8px period divided by the same at 32px period, at essentially the same depth from the rim. Because both are divided by the same transmittance, tint and opacity cancel exactly. A ratio of 0.99 corresponds to sigma ~0.55 px; sigma=1.0 would give 0.891, sigma=2.0 would give 0.361, sigma=10 would give 0.000.",
   "coordinates": "IMG_6644/IMG_6646, x 470-534. 8px at row 2340 (depth 36.5px): amp/255 = 0.1316 light, 0.1819 dark. 32px at row 2346 (depth 42.5px): amp/255 = 0.1332 light, 0.1837 dark."
  },
  {
   "quantity": "TAPER of interior sharpness with depth from the rim - the architectural question",
   "value": "NONE. sigma flat at 0.45-0.75 px from depth 6.5 px to 72.5 px (the centre)",
   "unit": "px vs px of depth",
   "method": "sigma fitted independently at every row of the pill's straight run, where the rim normal is vertical so refraction displaces the sample vertically and the vertical stripes are invariant under it. Depth = 73 - |y - 2376.5|. The transmitted amplitude varies 0.132 -> 0.166 but MONOTONICALLY IN ROW, not in depth: at equal depth on the two sides of centre (row 2346, depth 42.5, amp 0.1332 vs row 2405, depth 44.5, amp 0.1570) the values differ by 18%, which is larger than the whole depth-range variation on one side (0.1430 -> 0.1662, 16%). So the gradient is a vertical tint/lighting gradient over the button, not a radial taper.",
   "coordinates": "IMG_6644/IMG_6646, rows 2310-2420, x 470-534. Depth sequence 6.5, 16.5, 26.5, 36.5, 42.5, 56.5, 72.5, 59.5, 44.5, 29.5 px."
  },
  {
   "quantity": "Michelson contrast surviving inside the glass (uncovered reference = 1.0000 exactly)",
   "value": "8px: 0.081-0.084 light, 0.205-0.217 dark. 32px: 0.084-0.109 light, 0.216-0.220 dark",
   "unit": "Michelson (max-min)/(max+min)",
   "method": "Means of target-white and target-black columns, dropping pixels within 1px (8px band) or 2px (32px band) of a target transition. The uncovered reference was measured in the SAME screenshots outside every glass shape (8px at rows 2312-2340 x 245-335; 32px at rows 2344-2420 x 850-932) and comes out at exactly white 255.00 / black 0.00, Michelson 1.0000 - the backdrop is rendered pixel-exact (MAD 0.0001, max 1 over rows 2262-2301).",
   "coordinates": "IMG_6644 / IMG_6646, x 470-534, rows 2312 (d 8.5), 2320 (16.5), 2330 (26.5), 2340 (36.5), 2346 (42.5), 2360 (56.5), 2376 (72.5), 2390 (59.5), 2405 (44.5), 2420 (29.5)."
  },
  {
   "quantity": "The material is NOT one kernel: a second, wide blurred component exists alongside the sharp one",
   "value": "sigma_wide = 38 +/- 5 px light (12.7 dp), 36 +/- 5 px dark (12.0 dp); weights a_sharp = 0.111, a_wide = 0.284 (light); a_sharp = 0.169, a_wide = 0.350 (dark)",
   "unit": "px, and fraction of backdrop luminance transmitted",
   "method": "Fit to the top-chrome pill's horizontal profile across the target's hard black/white split. Model: obs(x) = c0 + c1*x + a_sharp*step(x) + a_wide*Phi((x-x0)/sigma). Adding the sharp term drops the pooled rms from 4.62-5.71 (wide-only, which then prefers a compromise sigma of 10-15) to 3.58-4.91. Total transmission a_sharp+a_wide = 0.395 (light), matching the directly measured DC swing (249-135)/255 = 0.447 once the linear term is accounted for.",
   "coordinates": "IMG_6644 / IMG_6646, top pill (rows 140.5-272.5, x ~341-830), edge at x = 584.5, fitted over x 440-730, rows 144-170 and 213-226 (the rows free of the 'Today' and '12:53 AM' labels drawn on top)."
  },
  {
   "quantity": "SHARP FRACTION of the transmitted backdrop, as a function of depth from the rim",
   "value": "27.6-33.2% (light), 32.3-37.1% (dark) - flat from depth 3.5 px to 60.5 px",
   "unit": "% of total backdrop transmission arriving unblurred",
   "method": "Same top-pill fit with sigma_wide pinned (38 light / 36 dark), run row by row so each row is one depth. a_sharp/(a_sharp+a_wide) per row. Rows 141-143, 170 and 227 excluded (rim highlight and glyph antialiasing, rms 7-13 vs 2.4-4.9 elsewhere).",
   "coordinates": "IMG_6644 / IMG_6646, rows 144-169 (depth 3.5-28.5) and 213-226 (depth 45.5-59.5), x 440-730. At depth 3.5: 29.5% light / 32.6% dark. At depth 56.5: 30.9% light / 35.0% dark. The trend is slightly UPWARD with depth, i.e. marginally MORE sharp detail in the middle than at the rim."
  },
  {
   "quantity": "The wide component is present in the bottom toolbar too",
   "value": "sigma ~35-40 px; a_wide 0.14-0.21 (light), 0.21-0.30 (dark)",
   "unit": "px and transmitted fraction",
   "method": "The target's hard split (x=585) sits 40-130 px above the toolbar, so a wide kernel imprints a left-right DC step inside the toolbar. Took the toolbar's local DC (rows 2312-2340, 32px boxcar in x, icons masked) and regressed it on [1, x, Gaussian-blurred target]. Adding the wide term drops the residual from 0.322 to 0.174 code values (light) and 0.470 to 0.248 (dark), minimising at sigma 35-40. Weak signal (the whole feature is ~2-4 code values) but the right magnitude and the right shape.",
   "coordinates": "IMG_6644 / IMG_6646, rows 2312-2340, x 400-780. Observed DC 201.03 at x 456-488 rising to 203.16 at x 680-712; predicted for a_wide=0.30 at sigma=35 is +2.8, observed +1.8 (light) / +2.5 (dark)."
  },
  {
   "quantity": "Chromatic dispersion in the interior",
   "value": "0 (undetectable)",
   "unit": "px of per-channel displacement",
   "method": "Ran the same sigma/amplitude/sub-pixel-shift fit independently on the R, G and B planes.",
   "coordinates": "IMG_6644 and IMG_6646, row 2376 (depth 72.5) and row 2320 (depth 16.5) and rows 2436/2440/2443 (depth 13.5/9.5/6.5), x 470-534. R, G and B return identical sigma, identical amp/255 to 4 decimal places, and identical shift 0.00 in every case."
  },
  {
   "quantity": "Rim behaviour: direction and magnitude of the near-rim sample displacement (bonus, and it constrains where blur is measurable)",
   "value": "~35 px, directed INWARD (toward the element's interior), within roughly 6 px of the rim",
   "unit": "px",
   "method": "The backdrop has 8px stripes at rows 2262-2341, 32px stripes at rows 2342-2421 and solid black at rows 2422-2447. Projected each glass row's x-profile onto the 8px and the 32px pattern to identify which backdrop band that row is displaying.",
   "coordinates": "IMG_6644. Rows 2307-2309 (depth 3.5-5.5 from the top rim) display the 32px pattern (projection 30.4-30.7) with zero 8px content, so they sample from row >= 2342, i.e. >= 33 px further INSIDE. Rows 2311-2340 display the 8px pattern in place. Rows 2422-2444 (depth 27.5 down to 5.5 from the bottom rim) display the 32px pattern although their own backdrop is solid black, so they too sample from further inside."
  },
  {
   "quantity": "Glass geometry measured (so the depth field is not assumed)",
   "value": "circles 146 px diameter; pill 487 x 146 px; radius 73 px",
   "unit": "px (48.7 dp and 162.3 x 48.7 dp at 3x)",
   "method": "Coverage mask from |capture - target| > 12, run-length analysis row by row, then a signed-distance model (two circles + one capsule) checked against it.",
   "coordinates": "Bottom toolbar: left circle centre (156.5, 2376.5) r=73; capsule l,t,r,b = 341.5, 2303.5, 828.5, 2449.5 r=73; right circle centre (1012.5, 2376.5) r=73. Model/measured coverage agreement 97.7%, identical row-2376 span (84-1085). Top pill: rows 140.5-272.5, x ~341-830."
  }
 ],
 "confirms_us": [
  "Refraction at the rim is real, strong and worth having a dedicated band for. Within ~6 px of the rim the sampled backdrop is displaced by roughly 35 px, enough to pull in a completely different part of the image - at rows 2307-2309 the top of the pill shows the 32px stripe zone that physically lives 35+ px lower, and at rows 2422-2444 the bottom shows stripes while its own backdrop is solid black.",
  "The COMPOSITE spread of Apple's kernel is close to our blurRadius default. 0.29*delta + 0.71*Gaussian(38px) has an RMS second moment of 32.0 px = 10.7 dp, against our blurRadius = 10 dp. The magnitude was a good guess; only the shape and the taper are wrong.",
  "Transmission is low and tint is high, in the range our presets assume: total backdrop transmission a_sharp + a_wide = 0.40 (light chrome) and 0.52 (dark chrome), measured on the top pill; the bottom toolbar buttons pass 0.13-0.17 sharp plus a wide component.",
  "DarkChrome's documented 'no visible dispersion at rest' is confirmed: R, G and B return identical sigma, amplitude and sub-pixel shift to four decimal places at every depth tested, including 6.5 px from the rim.",
  "The material really is measurably different in light and dark appearance rather than being one material with a flipped tint: sharp transmission 0.111 vs 0.169, wide transmission 0.284 vs 0.350, blurred-path sigma 38 vs 36 px. Keeping DarkChrome as a separate preset is right."
 ],
 "contradicts_us": [
  {
   "ours": "GlassShaderSource.kt line 392: `blurredAt(base + push, uBlur * mat * e, ...)` with `rimSharp = (1-e)*(1-e)` on line 393 - the blur radius ramps from 0 at the rim to uBlur in the middle, and the sharp sample is mixed back in with weight (1-e)^2. On a 146px button with refractionBand 26dp=78px this predicts a sharp fraction of 91.2% at depth 3.5 px falling to 0.4% at depth 73 px.",
   "measured": "The sharp fraction is FLAT at 28% (light) / 33% (dark) from depth 3.5 px to 60.5 px. It does not taper at all, and its slight trend runs the wrong way (marginally higher deep inside). Measured on the top pill row by row; on the bottom toolbar the fitted blur sigma is flat at 0.45-0.75 px from depth 6.5 to 72.5 px.",
   "significance": "This is the load-bearing claim in the file's header comment ('The rim is sharper than the middle... so the scatter lives here, keyed to distance from the edge'). It is not what the device does. Apple applies the SAME mixture everywhere on the element. The architecture is solving a problem that does not exist - though see the note below about what does make the rim look different."
  },
  {
   "ours": "One kernel: uBlur is a single disc of 19 taps, radius 30 px at blurRadius=10dp. Its per-axis equivalent Gaussian sigma is 0.5538*R = 16.6 px at e=1 (15.6 px at the centre of a 146px button).",
   "measured": "TWO paths in parallel: ~30% of the backdrop arrives with sigma 0.5-0.8 px (i.e. effectively unblurred - one bilinear tap), and ~70% arrives with sigma 36-38 px. A single 15.6px disc destroys 32px stripes almost completely (predicted peak-to-trough ratio 0.013) whereas the device passes them at full sharpness, and it leaves 8px stripes at 0.000 whereas the device passes those too.",
   "significance": "Our shader cannot produce the device's look at any value of blurRadius. Too small and there is no softening; too large and the sharp copy that carries all the legible structure disappears. The fix is a two-path mix, not a different radius."
  },
  {
   "ours": "GlassStyle.kt: Chrome preset uses `backdropBlur = 14.dp` (42 px) with `blurRadius = 0.dp`, i.e. 100% of the backdrop passes through one blur; DarkChrome uses `backdropBlur = 3.dp` (9 px).",
   "measured": "The chrome elements in this capture pass roughly 70% through sigma 36-38 px (12-12.7 dp) AND 30% perfectly sharp. Chrome's 14dp is close for the blurred path's scale; DarkChrome's 3dp is a quarter of what the device does; but neither preset keeps the sharp path at all, which is where all the surviving stripe contrast comes from.",
   "significance": "Chrome and DarkChrome trade the signature away for legibility, and the doc comment says so deliberately. The measurement says Apple gets both: the blurred path destroys legible text while the sharp 30% keeps the material reading as glass. That trade is available to us and we are not taking it."
  },
  {
   "ours": "GlassShaderSource.kt header note 1 and line 360: `float2 push = n * bend * lens;` - sampling OUTWARD, pulling the surroundings inward, which is why the host records uPad beyond the edge.",
   "measured": "The near-rim sample is displaced INWARD, toward the element's own centre, by roughly 35 px within ~6 px of the rim. Rows 2307-2309 at the top of the bottom toolbar display the 32px pattern that lives at rows >= 2342 (below them, inside); rows 2422-2444 at the bottom display the 32px pattern that lives at rows <= 2421 (above them, inside) while their own backdrop is solid black.",
   "significance": "The sign of the lens is inverted relative to ours. Apple's rim magnifies the interior outward; ours compresses the exterior inward. This also means uPad may not be needed at all for the refraction (though it still is for the mirror term). This was not my assigned measurement and I found it as a by-product, so it deserves its own dedicated capture before anyone acts on it."
  },
  {
   "ours": "GlassStyle.kt `dispersion` / shader `uAberration`, 0.010-0.030 across the presets, with per-channel sample offsets on lines 379-387.",
   "measured": "Zero measurable per-channel displacement anywhere in the interior, including at 6.5 px from the rim: R, G, B fits agree to 4 decimal places in sigma, amplitude and sub-pixel shift.",
   "significance": "Weaker than it sounds - the backdrop under the toolbar is neutral, so this rules out a per-channel SHIFT but not a per-channel gain. Still, a shift is what uAberration produces, and there is none at these depths. It could still live in the outermost 3 px, which this dataset cannot reach."
  }
 ],
 "headline": "Apple's interior blur does not taper - the material applies one fixed mixture at every depth (about 30% of the backdrop arriving essentially unblurred at sigma 0.5-0.8 px, and about 70% through a wide sigma ~38 px), flat from 3.5 px off the rim all the way to the centre, so our rim-sharp/interior-soft taper is solving a problem that does not exist.",
 "method_notes": "SETUP. Working dir C:/Users/shaya/AppData/Local/Temp/claude/D--Work-AndroidStudioProjects-SideProjects-kmp-Vitals/d83608da-a0a9-446c-9f1b-ec02e5b88c50/scratchpad/caps/work/ (scripts s1..s36 plus common.py and geom.py). numpy + PIL only, no scipy. All work in the green channel unless stated; R and B were cross-checked.\n\nREGISTRATION. Re-verified on the exact region used, not the region in the brief: rows 2262-2301, x 100-1070, IMG_6644 vs calibration-target-light. MAD at zero shift = 0.0001; (0,-1) = 63.88, (0,+1) = 63.62, (-1,0) = 3.17, (+1,0) = 0.089. Identity, unambiguously.\n\nCAPTURE GROUPING - DEPARTS FROM THE BRIEF. The four captures do not group as 6644/6647 vs 6645/6646. They group as {IMG_6644, IMG_6645} (MAD 0.056 over the toolbar) and {IMG_6646, IMG_6647} (MAD 0.044), with MAD ~65 between the groups. The two target PNGs are byte-identical in the split band (rows 0-260), the primaries and the text zone, and differ only by ~1.85 mean in the tab-bar zone (in the 17pt text at rows 2442+, not in the stripes I used). So the split between capture groups is the app's own LIGHT vs DARK chrome appearance, not the target. I used IMG_6644 as \"light chrome\" and IMG_6646 as \"dark chrome\"; 6645 and 6647 are duplicates of those. Effectively two independent conditions, not four.\n\nWHERE THE BACKDROP IS TRUSTWORTHY. The display renders the target pixel-exact for neutral content: 64px stripes, 8px stripes, checkerboard, grid lines and horizontal transitions all match the target with no intermediate pixel, and the hard split at row 2255 reads 0 0 0 0 0 255 255 255 255 255. The one exception is the split edge in the TOP region (rows 0-138), where the capture shows a 4px ramp (0, 32, 95, 160, 223, 255 at x 582-587) where the target is hard. That spread is present at rows 118 through 138 and vanishes at the bottom of the screen, so something OS-level softens content near the top bar by about sigma 1.2 px. It is negligible next to the sigma-38 component measured there but it means the top region's absolute sharpness numbers carry a ~1px floor. Everything at the bottom toolbar is free of it.\n\nPRIMARY INSTRUMENT - the pill's straight run. The bottom toolbar capsule spans x 341.5-828.5, y 2303.5-2449.5, corner radius 73. On its straight top/bottom runs (x 414.5-755.5) the rim normal is vertical, so refraction displaces the sample vertically; the backdrop's stripes are vertical and constant in y within each band, so vertical displacement is harmless and a horizontal cut measures pure tangential MTF. Depth = 73 - |y - 2376.5|, constant along each row. That gives one clean depth per row, from 6.5 px to 72.5 px, over two spatial frequencies (8px stripes at rows 2262-2341, 32px at rows 2342-2421). Icons were masked by taking pixels extreme in either appearance ((light<90 | light>246 | dark<45 | dark>235)) and dilating 3-5 px.\n\nFIT. Forward model rather than deconvolution: take the target row as box pixels, upsample 16x, convolve with a Gaussian of trial sigma, re-integrate over a 1px capture aperture, sample at pixel centres with a free sub-pixel shift, then least-squares for offset, a linear-in-x term and amplitude. Grid search sigma in 0.05px steps. Validated two ways: on uncovered stripe edges in the same screenshot it returns the search floor (0.100) with rms 0.00, and on synthetic edges it recovers true sigma exactly up to 8 px.\n\nMODEL-FREE CROSS-CHECK. Folded every 32px stripe period over x 415-755 into a single 32-bin profile and differenced it. Result: a 3-pixel LSF with weights (0.036, 0.549, 0.415), second moment 0.55 px, and identical shape in three separate depth bands (30-45, 40-52, 60-73 px). That shape is exactly a single bilinear texture fetch at a fractional offset, so the material's own blur beyond resampling is at most ~0.3 px.\n\nTINT-FREE CROSS-CHECK. Because transmittance and tint cancel in a ratio, I compared the 8px amplitude at depth 36.5 (row 2340) with the 32px amplitude at depth 42.5 (row 2346): 0.1316 / 0.1332 = 0.988 (light) and 0.1819 / 0.1837 = 0.990 (dark). A ratio of 0.99 means sigma ~0.55 px; sigma=1 would give 0.891, sigma=2 would give 0.361, sigma=10 would give 0.000.\n\nSECOND INSTRUMENT - the top pill over the hard split. The 'Today' pill (rows 140.5-272.5) straddles the target's hard black/white split at x=584.5, which is 235 px from its own left rim, so the horizontal profile there is uncontaminated by end refraction. Rows 171-211 and 228-251 carry the 'Today' and '12:53 AM' glyphs and were excluded; rows 144-170 (depth 3.5-29.5) and 213-226 (depth 45.5-59.5) are clean. Fitting obs = c0 + c1*x + a_sharp*step + a_wide*Phi(x/sigma) separates the two paths. Beyond row 251 the sample can reach the grey ramp below row 260, so I did not use rows 252-259.\n\nWHY THE TAPER CONCLUSION IS SAFE. Every effect I can think of that would fake 'flat' biases the other way. Near the rim the refraction compresses the image along the normal, which lowers apparent contrast there; measuring flat-to-rising contrast in spite of that is conservative. And the residual variation that does exist tracks ROW rather than DEPTH: at depth 42.5 vs 44.5 on opposite sides of centre the amplitudes are 0.1332 and 0.1570, an 18% difference at the same depth, larger than the 16% spanned by the entire depth range on one side. So the gradient is a vertical tint/lighting gradient over the button.\n\nWHAT I COULD NOT REACH. Below depth ~3.5 px (top pill) and ~6.5 px (bottom toolbar) the refraction displaces the sample by ~35 px into a region of the target with no structure, so the instrument goes blind. Rows 2304-2309 of the toolbar and rows 141-143 of the pill are therefore excluded rather than reported as sharp or soft.",
 "uncertain": [
  "The blur in the outermost 0-3.5 px of the rim. Within about 6 px of the rim the material samples ~35 px inward, landing in a flat part of the target, so there is no signal to measure sharpness against. A target with fine structure at TWO different scales stacked within 40 px of each other would fix this. This matters because it is exactly the band our uRimSoft and rimSharp terms operate in.",
  "The SHAPE of the wide kernel. Gaussian (sigma 38.5, rms 4.753), Laplacian/exponential (scale 48, rms 4.727) and Cauchy (scale 44, rms 4.739) all fit the top pill's edge equally well - the differences are far inside the noise. Only the scale is constrained, and only in the RMS sense: sigma_gauss = 38 +/- 5 px. If Apple's kernel is heavy-tailed, the equivalent Gaussian understates how far light spreads.",
  "The wide kernel's VERTICAL extent. I measured it only across a vertical edge, so only the horizontal profile. If the kernel is anisotropic (a two-pass or directional blur) I would not see it.",
  "Whether the wide component belongs to the material or to an OS-level effect applied to content under a bar. Evidence it is the material: it is confined to the element's own bounds (the uncovered backdrop 2 px above the pill shows only a 4px spread, not a 38px one), and the bottom toolbar shows the same component with no bar-level effect anywhere near it (rows 2248-2301 are pixel-exact). But this dataset cannot prove it, because there is no fine structure under any of the top chrome to test independently.",
  "Whether the blur happens in linear light or in gamma-encoded code values. Nothing in this dataset separates them: the stripe measurement is a ratio and cancels the question, and the DC test that would answer it is spoiled by the near-rim sample displacement. This shifts the absolute a_wide by a few percent, not the taper conclusion.",
  "a_wide for the BOTTOM toolbar specifically. The signal is a 2-4 code-value left-right step imprinted by the split 40-130 px above it, and although adding the term halves the residual (0.322 -> 0.174 light, 0.470 -> 0.248 dark) and prefers sigma 35-40, it is degenerate with the button's own tint gradient. The confident wide-component numbers come from the top pill, where the edge is 117 code values.",
  "The ~5 code-value darkening seen at every backdrop stripe transition (residual -6 at each 32px transition, and a clean period-4 residual under the 8px stripes). It is small and consistent but I cannot tell whether it is a deliberate edge treatment, a dispersion artefact, or a resampling effect. It is not a blur.",
  "Whether iOS screenshots render the material identically to the live display. Everyone measures this way and there is no reason to think otherwise, but it is an untested assumption underneath every number here."
 ]
}
```

---

## Measured: Tint, opacity and tone response

Agent `a58a2b6cbb306204b`.

```json
{
 "confident": [
  {
   "quantity": "LIGHT-appearance chrome: output over a pure black backdrop, f(0)",
   "value": "131.5",
   "unit": "sRGB code value (0-255), all three channels equal",
   "method": "Back-button glass circle, centre (113.5, 206.5), radius 66 px. Sampled the eroded interior (r <= 48) in matched row bands, excluding glyph pixels as |v - median| >= 6. Row band 173-194 gives median 131.0; row band 166-208 gives 132.0; row band 215-243 gives 135.0 (the last is lifted by the grey ramp starting at row 260 bleeding upward, so 131 is the cleanest floor). Backdrop verified: target pixels over rows 150-260, x 48-180 are uniquely 0.0 in BOTH the light and dark target PNGs.",
   "coordinates": "IMG_6644 / IMG_6645, x 65-165, y 173-194 (plateau floor 131, sd 0.84 over n=1648)",
   "captures": "IMG_6644, IMG_6645 (bit-identical here)"
  },
  {
   "quantity": "LIGHT-appearance chrome: output over a pure white backdrop, f(255)",
   "value": "249.5",
   "unit": "sRGB code value (0-255)",
   "method": "'More' button glass circle, same size and shape as the back button, centre (1061, 206.5), radius 66. Same eroded-interior + glyph-rejection procedure, same row bands. Row band 173-194 gives 249.0; 215-243 gives 252.0. The interior is not flat: it runs 249 at the top and 252-253 at the bottom, because the status-bar icons (dark, rows 56-100, x 960-1170) sit above it and depress the top. Backdrop verified uniquely 255.0 over rows 150-260, x 995-1128 in both targets.",
   "coordinates": "IMG_6644 / IMG_6645, x 1005-1115, y 173-194 (249) and y 215-243 (252)",
   "captures": "IMG_6644, IMG_6645"
  },
  {
   "quantity": "LIGHT-appearance chrome: transmission (1 - alpha), i.e. how much of the backdrop survives",
   "value": "0.4627",
   "unit": "fraction, dimensionless",
   "method": "(f(255) - f(0)) / 255 using MATCHED row bands on the two same-size circles, so the local-bleed error cancels. Row band 173-194: (249-131)/255 = 0.4627. Row band 180-201: (250-132)/255 = 0.4627. Row band 215-243: (252-135)/255 = 0.4588. Row band 166-208: (249-132)/255 = 0.4588. Spread 0.4588-0.4627 across four independent bands; alpha is far more stable than the tint colour because both anchors move together under local bleed.",
   "coordinates": "back button (113.5,206.5) r66 vs more button (1061,206.5) r66, IMG_6644",
   "captures": "IMG_6644, IMG_6645"
  },
  {
   "quantity": "LIGHT-appearance chrome: implied blend alpha",
   "value": "0.537",
   "unit": "fraction, dimensionless (+/- 0.003)",
   "method": "alpha = 1 - transmission = 1 - 0.4627. Range across the four row bands 0.5373-0.5412.",
   "coordinates": "as above",
   "captures": "IMG_6644, IMG_6645"
  },
  {
   "quantity": "LIGHT-appearance chrome: implied tint colour",
   "value": "245 (0xF5F5F5), neutral",
   "unit": "sRGB code value per channel",
   "method": "T = f(0)/alpha = 131.5/0.537 = 244.8. Range across row bands 243.8-249.5. Hue: over thousands of interior pixels the max |R-B| is exactly 0 and the median RGB triple is (132,132,132) over black and (249,249,249) over white, so the tint is achromatic to within the 8-bit quantum. The value is only bracketed to about 245 +/- 5 because f(0) and f(255) each drift 2-4 counts with position inside the element.",
   "coordinates": "back button + more button interiors, IMG_6644",
   "captures": "IMG_6644, IMG_6645"
  },
  {
   "quantity": "DARK-appearance chrome: output over a pure black backdrop, f(0)",
   "value": "32.0",
   "unit": "sRGB code value (0-255)",
   "method": "Identical procedure and identical pixel masks as the light case, on the dark-appearance chrome. Row bands 173-194, 180-201 and 166-208 all give median 32.0; band 215-243 gives 35.0 (ramp bleed).",
   "coordinates": "IMG_6647 / IMG_6646, back button x 65-165, y 173-208",
   "captures": "IMG_6647, IMG_6646 (bit-identical here)"
  },
  {
   "quantity": "DARK-appearance chrome: output over a pure white backdrop, f(255)",
   "value": "181.5",
   "unit": "sRGB code value (0-255)",
   "method": "Same masks on the more button. Row bands 173-194 and 166-208 give 181.0; 180-201 gives 182.0; 215-243 gives 184.0.",
   "coordinates": "IMG_6647 / IMG_6646, more button x 1005-1115, y 173-208",
   "captures": "IMG_6647, IMG_6646"
  },
  {
   "quantity": "DARK-appearance chrome: transmission (1 - alpha)",
   "value": "0.5863",
   "unit": "fraction, dimensionless",
   "method": "(f(255)-f(0))/255 on matched row bands: 173-194 -> 0.5843, 180-201 -> 0.5882, 215-243 -> 0.5843, 166-208 -> 0.5843.",
   "coordinates": "as above",
   "captures": "IMG_6647, IMG_6646"
  },
  {
   "quantity": "DARK-appearance chrome: implied blend alpha and tint",
   "value": "alpha 0.414, tint 77 (0x4D4D4D), neutral",
   "unit": "fraction / sRGB code value",
   "method": "alpha = 1 - 0.5863 = 0.4137 (range 0.4118-0.4157). T = 32.0/0.4137 = 77.3 (range 77.0-84.2 across row bands). Max |R-B| exactly 0 over the sampled interiors; medians (32,32,32) and (181,181,181).",
   "coordinates": "back + more button interiors, IMG_6647",
   "captures": "IMG_6647, IMG_6646"
  },
  {
   "quantity": "Lift over pure black",
   "value": "LIGHT +131.5, DARK +32.0",
   "unit": "sRGB code values added to 0",
   "method": "Direct read of f(0) above; the backdrop is verified exactly 0 over the whole footprint plus a 60 px margin.",
   "coordinates": "back button interior, y 173-208",
   "captures": "all four"
  },
  {
   "quantity": "Drop from pure white",
   "value": "LIGHT -5.5, DARK -73.5",
   "unit": "sRGB code values subtracted from 255",
   "method": "255 - f(255). For the light material the honest bracket is -2 to -6 depending on where in the element you read, because the status bar above depresses the top of the button by ~4 counts; for the dark material it is -71.5 to -74.",
   "coordinates": "more button interior, y 173-243",
   "captures": "all four"
  },
  {
   "quantity": "Adaptation to distant backdrop content: none detectable",
   "value": "0",
   "unit": "sRGB code values of change",
   "method": "IMG_6644 (light target) and IMG_6645 (dark target) put the SAME chrome over the SAME local backdrop (the targets are byte-identical over rows 0-260 and rows 2172-2500), but differ enormously over rows 960-2130. Differenced the two captures. Bottom toolbar rows 2302-2449, x 60-1110: max channel difference 3, mean 0.056, only 1 pixel in 10^5 differing by more than 2. Top chrome rows 140-274: 985 pixels differ by >2 and 99.8% of them fall in the box x 513-807, y 227-255 -- the clock line, which reads 12:53 AM in one and 12:52 AM in the other. Same result for IMG_6647 vs IMG_6646 (100% of differing pixels in the clock box).",
   "coordinates": "rows 2302-2449 x 60-1110; rows 140-274",
   "captures": "IMG_6644 vs IMG_6645; IMG_6647 vs IMG_6646"
  },
  {
   "quantity": "Tone response is AFFINE on a smooth backdrop, verified at four inputs for the light material",
   "value": "in=0 -> 131.5; in=204 -> 232.0; in=233.4 -> 239.0; in=255 -> 249.5 (affine line predicts 225.9 and 239.5)",
   "unit": "sRGB code values",
   "method": "The two small round controls at rows 347-405 sit on the grey ramp, which the target defines exactly as round(x*255/1169) (verified: rows 281-389 match that formula to <=1 count at every x). A linear ramp is invariant under any symmetric blur kernel, so the effective input equals the ramp value at the element's centre regardless of how much the material blurs. Left control x 895-975, rows 355-385: output 232.0 (sd 0.46, n=2027), centre ramp value 204.0. Right control x 1035-1105, rows 355-385: output 239.0 (sd 0.47, n=1457), centre ramp value 233.4. The affine line fitted to the black/white anchors, f(in) = 131.5 + 0.4627*in, predicts 225.9 and 239.5. The right control matches to -0.5. The left control is +6.1 high, fully accounted for by the white band that starts at row 390, only 5-35 px below the sampled rows (12-20% kernel weight in white explains +3 to +6).",
   "coordinates": "IMG_6644, x 895-975 and x 1035-1105, y 355-385",
   "captures": "IMG_6644 (IMG_6647's versions of these two controls are a different, much more opaque material -- see uncertain)"
  },
  {
   "quantity": "Excess brightness over the affine line at a hard high-contrast boundary",
   "value": "LIGHT +13.8, DARK +4.2",
   "unit": "sRGB code values above the affine prediction",
   "method": "The 'Today' pill straddles the target's hard black/white split, which runs black for x<=584 and white for x>=585. Because the backdrop is antisymmetric about x=584.5 (and so is the grey ramp below it, and the split above it), the sum S(u) = out(585-u) + out(584+u) must equal f(0)+f(255) for EVERY u if the response is affine and the kernel symmetric -- this holds for any kernel width, so it is a blur-free test of linearity. Measured on glyph-free rows (chosen by requiring max|second difference| along the row < 15). Rows 156-168: pill far-field 133.78 over black (x 370-470) and 249.62 over white (x 700-800), affine midpoint 191.70. S(u)/2 = 205.5 at u=3, 205.0 at u=5, 201.0 at u=20, 195.7 at u=50, 191.5 at u=120, 191.2 at u=170. Peak excess +13.8, decaying to zero by u~120. Rows 214-226 give +12.5. Dark chrome, same rows: affine midpoint 107.73, S(u)/2 peaks at 112.4 (u=12), excess +4.2 (rows 214-226: +3.9).",
   "coordinates": "IMG_6644 and IMG_6647, x 584.5 +/- u, y 156-168 and y 214-226",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "The excess scales with the tint's own brightness, not with the blend strength",
   "value": "about 6% of the tint colour added",
   "method": "Light excess 13.8 with tint 245; dark excess 4.2 with tint 77. Excess/tint = 0.056 and 0.055 respectively (second row band: 12.5/245 = 0.051 and 3.9/90 = 0.043). A stronger BLEND toward the tint would move the dark material DOWN over a mid-grey backdrop (tint 77 is below the 107 midpoint); it moves UP. An ADDITIVE contribution of k*tint moves both up in proportion to the tint, which is what is observed. Crucially, the two round controls on the smooth grey ramp (low local contrast) show NO such excess: at in=233.4 the measured 239.0 sits on the affine line, whereas +6% of tint would put it at 254. So the excess is triggered by local backdrop contrast, not by input level -- it is a legibility term, not a curved tone response.",
   "coordinates": "pill x 584.5+/-u y 156-168 (high contrast) vs round controls x 1035-1105 y 355-385 (smooth ramp)",
   "unit": "fraction of the tint colour",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Square-wave contrast transmission of the bottom toolbar (how much backdrop structure survives)",
   "value": "see method",
   "unit": "fraction of the backdrop's 0-255 swing that reaches the output",
   "method": "The toolbar sits on the target's tab-bar zone: 8 px period stripes over rows 2262-2341 and 32 px period stripes over rows 2342-2421 (verified by run-length analysis of the target; means 127.72 and 129.03). Masked out glyphs by thresholding (<150 for light chrome, >150 for dark) and dilating 7x7, then averaged capture pixels over target-white and target-black columns separately. LIGHT centre pill (x 360-820): 32 px stripes 212.73 over white vs 181.95 over black = 0.1207; 8 px stripes 213.55 vs 194.60 = 0.0743. DARK centre pill: 32 px 0.1401, 8 px 0.1118. LIGHT small circular button (x 100-215): 32 px 0.0666, 8 px 0.0128. DARK small button: 32 px 0.0869, 8 px 0.0181. Against the DC transmission (0.463 light / 0.586 dark) that is an MTF of 0.26/0.24 at 32 px and 0.16/0.19 at 8 px for the wide pill, but only 0.14/0.15 and 0.03/0.03 for the small circular buttons.",
   "coordinates": "rows 2306-2338 and 2350-2418; x 360-820 (pill), 100-215 (left button), 950-1070 (right button)",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Upper bound on our shader's `adaptivity` parameter for this material",
   "value": "<= 0.2",
   "unit": "the GlassStyle.adaptivity parameter, 0-1",
   "method": "Brute-forced our own model out = in*(1-a) + clamp(T0*mix(1, mix(1.10,0.88,in/255), A))*a against the three light-chrome points measured on smooth backdrops (0 -> 131.5, 233.4 -> 239.0, 255 -> 249.5). RMS residual: A=0.00 -> 0.22, A=0.10 -> 0.21, A=0.20 -> 0.21, A=0.40 -> 4.28, A=0.65 -> 9.38, A=0.90 -> 14.26. Anything above ~0.2 bends the bright end of the curve more than the data allows. Best overall fit A=0.20, alpha=0.516, tint=250.",
   "coordinates": "back button y173-208, round control x1035-1105 y355-385, more button y173-194",
   "captures": "IMG_6644"
  }
 ],
 "confirms_us": [
  "The measured response IS a plain blend, not an absorbing medium: out = in*(1-a) + tint*a fits every point measured on a smooth backdrop to within about 1 code value, across the full 0-255 range for the light material. GlassStyle.tintAbsorption = 0 as the default is the right call, and the doc comment on it ('the blend's strength is fitted to measurement, and it is the pair of numbers that pins this material') is the right methodology.",
  "DarkChrome's f(255) is very nearly exact. Our shader with tint 0x373737 at alpha 0.36 and adaptivity 0.4 predicts 182.0 over pure white; the iPhone 13 measures 181.5. That is a 0.5-count agreement on a number nobody was tuning directly, from a completely independent capture (an iOS 26 Phone-app recording at 720x1558).",
  "The material really is achromatic. GlassStyle's neutral tints (White, 0x373737) carry no hue error: over thousands of interior pixels the maximum |R - B| is exactly 0, in both polarities.",
  "The doc comment on `invertsWithBackdrop` -- that the decision is the host's, size-gated, and that symbols must flip in lockstep -- matches what the captures show. The chrome flips polarity as a whole element (glass and glyphs together) between the two capture pairs; it never partially adapts.",
  "`legibility` exists for the right reason. There IS a real, measurable term that raises the material where the backdrop is busy: +13.8 counts at a hard black/white edge for the light material, decaying to zero over about 120 px. Apple's description that the tint shifts to keep controls legible is confirmed quantitatively. Only the sign convention is wrong (see contradicts)."
 ],
 "contradicts_us": [
  {
   "ours": "GlassStyle.Chrome: tint = White at alpha 0.11, adaptivity 0.90. Predicted f(0) = 28.1, f(255) = 252.0, transmission 0.878.",
   "measured": "iOS 27 light-appearance chrome: f(0) = 131.5, f(255) = 249.5, transmission 0.463. To reproduce it our preset needs tint 0xFFF5F5F5 at alpha 0.537 with adaptivity <= 0.2, which gives 131.6 / 249.6 / 0.463.",
   "significance": "This is the largest discrepancy in the whole measurement. Our light Chrome preset lifts pure black by 28; the device lifts it by 131.5 -- 4.7x. We ship a material that is nearly twice as transmissive as Apple's (88% vs 46%). Anything drawn on our light chrome over dark content is far less legible than the reference, and the material reads as a faint wash rather than as glass. Note the transparency-slider caveat below before changing the constant."
  },
  {
   "ours": "GlassStyle.DarkChrome: tint 0xFF373737 at alpha 0.36, fitted to lift pure black by 20 and pass 64% of white.",
   "measured": "f(0) = 32.0 (not 20), transmission 0.586 (not 0.633). Our shader's own formula with these constants yields f(0) = 20.6 and transmission 0.633. The constants that reproduce the iPhone 13 are tint 0xFF4D4D4D at alpha 0.414 (predicted 31.9 / 181.3 / 0.586).",
   "significance": "The lift over black is 60% too small and the material passes 8% too much of what is behind it. Direction of the fix: brighter tint (0x37 -> 0x4D) AND more of it (0.36 -> 0.414). Both moves make the bar more solid. The iOS 26 recording that produced the original numbers was 720x1558 with video compression; this is a lossless 1170x2532 screenshot against a known backdrop, so it should win -- but they are different iOS versions and possibly different transparency settings, so this may be a real change between 26 and 27 rather than an error in the old fit."
  },
  {
   "ours": "GlassStyle.adaptivity: 0.65 on Regular, 0.90 on Chrome, 0.80 on Thick, 0.40 on DarkChrome. toneMappedTint bends the tint's value by mix(1.10, 0.88, bgLuma).",
   "measured": "The transfer curve on smooth backdrops is affine to within ~1 count. Fitting our own model, adaptivity must be <= 0.2 (RMS 0.21); at 0.65 the RMS is 9.4 counts and at 0.90 it is 14.3.",
   "significance": "Our per-pixel tint tone-mapping is roughly 3-4x too strong. At adaptivity 0.90 our Chrome preset predicts 227.5 where the device reads 239.0 over the same backdrop. The adaptation Apple actually does is not a per-pixel function of backdrop luma at all -- it is the contrast-triggered term below."
  },
  {
   "ours": "The legibility term: `strength = uTint.a + contrast * uLegibility * 0.25`, i.e. local contrast raises the BLEND alpha, pulling the output further toward the tint colour.",
   "measured": "At a hard edge the output rises for BOTH polarities: +13.8 for the light material (tint 245) and +4.2 for the dark one (tint 77), and the two excesses are in the ratio of the tint brightnesses (0.056 and 0.055 of tint). A stronger blend toward a dark tint would move the dark material DOWN over a mid-grey backdrop, from 107.7 toward 77. It moves up.",
   "significance": "Our legibility term has the wrong sign for any tint darker than the backdrop -- which is exactly the DarkChrome case where legibility matters most. The measured behaviour is an ADDITIVE contribution of about 6% of the tint colour, not an increase in blend strength. Concretely: replace `strength += contrast * uLegibility * 0.25` with an additive `col += tinted * (contrast * uLegibility * 0.06)` applied after the blend. That reproduces both polarities with one constant."
  },
  {
   "ours": "Nothing in GlassStyle expresses a difference between how much backdrop STRUCTURE survives and how much backdrop LEVEL survives; tint alpha implies both.",
   "measured": "The DC transmission is 0.463 (light) but only 0.121 of a 32 px square wave and 0.074 of an 8 px square wave reaches the output through the toolbar's wide pill -- and only 0.067 / 0.013 through its small circular buttons. So the small controls pass 46% of the backdrop's level and 3% of its fine detail.",
   "significance": "Our blurRadius/backdropBlur are separate knobs from tint, which is correct in structure, but the presets are not calibrated against any measured MTF. It also means 'passes 64% of white text' as a fitting target is ambiguous: at the stroke width of 17 pt text on this device (roughly 8-10 px at 3x) the measured pass-through is 3-19% depending on the element, not 46-64%."
  }
 ],
 "headline": "iOS 27 light-appearance chrome is a plain neutral blend that lifts pure black by 131.5 and passes only 46.3% of what is behind it (tint ~0xF5 at alpha 0.537) -- 4.7x more opaque than our Chrome preset -- while the dark variant measures tint ~0x4D at alpha 0.414, and in both polarities the response is affine on smooth backdrops with an extra ~6%-of-tint lift that appears only where local backdrop contrast is high.",
 "method_notes": "ENVIRONMENT. Python 3.12, numpy + PIL, no scipy (had to hand-roll dilation via sliding_window_view). All scripts are in C:/Users/shaya/AppData/Local/Temp/claude/D--Work-AndroidStudioProjects-SideProjects-kmp-Vitals/d83608da-a0a9-446c-9f1b-ec02e5b88c50/scratchpad/caps/work/ (00_setup.py through 90_where.py). Nothing was written to D:/liquidglass-captures or to the repo.\n\nREGISTRATION SANITY CHECK (done once, as instructed). Compared IMG_6644 against calibration-target-light.png over the 4 px vertical stripes at rows 440-720, x 100-560, sweeping dx from -3 to +3. MAD is 0.004 at dx=0 and 63.4 at dx=+/-1. On a 4 px square wave a single-pixel error costs 63 counts, so this pins registration to identity with no ambiguity. Grey ramp rows 300-340, x 0-800 (uncovered) gives MAD 0.043-0.053 for all four captures.\n\nCOLOUR-MANAGEMENT CHECK. Spot-checked uncovered neutral pixels: (100,300) 22->22, (600,300) 131->131, (1100,310) 240->240, (300,2200) 0->0, (900,2200) 255->255, (600,2450) 0->0. Every measurement in this report is on a neutral backdrop and every measured output is neutral (max |R-B| = 0 across thousands of pixels), so the saturated-colour shift never enters.\n\nWHAT THE FOUR CAPTURES ACTUALLY ARE. The brief's light/dark split is about the TARGET, but the chrome pairs the other way: {IMG_6644, IMG_6645} carry LIGHT-appearance chrome (light glass, black glyphs) and {IMG_6647, IMG_6646} carry DARK-appearance chrome (dark glass, white glyphs). Within each pair the chrome is bit-identical apart from the clock digits, even though the targets underneath differ across rows 960-2130. That is the adaptivity experiment handed to us for free, and it is the cleanest result in the set. It also means we have both polarities of the same material over an identical backdrop, which is what makes the tint/alpha fit well-conditioned.\n\nGEOMETRY. Located every glass element from the capture-minus-target difference map (threshold >6) plus row/column span analysis, then verified against rendered crops. Back button: circle, centre (113.5, 206.5), radius 66 px (= 44 dp at 3x). 'More' button: same size, centre (1061, 206.5). 'Today' pill: x 345-826, y 141-274. Bottom toolbar: centre pill x 344-826, circular buttons x 86-227 and x 942-1083, y 2302-2447.\n\nSAMPLING PROTOCOL FOR THE ANCHORS. Eroded each circle to r <= 48 (18 px in from the rim, clear of the bevel), restricted to matched row bands so that the two circles are sampled at identical positions relative to their own centres, and rejected glyph pixels as |v - median| >= 6. Reported the median, not the mean. Ran four row bands (173-194, 180-201, 215-243, 166-208) to expose the vertical gradient inside each element. Verified from the target PNG that the backdrop is uniquely 0.0 under the whole back-button footprint and uniquely 255.0 under the whole more-button footprint, in BOTH targets. Key point: alpha is stable to +/-0.003 across row bands while the derived tint drifts by 6 counts, because local bleed shifts both anchors in the same direction -- so quote alpha with confidence and the tint with a bracket.\n\nTHE LINEARITY TEST, AND WHY IT IS BLUR-PROOF. The material has a large and partly non-local backdrop kernel (the pill's edge response spreads over about +/-100 px, and rows 2422-2441 of the toolbar show the 32 px stripes at full amplitude over a backdrop that is uniformly black -- so you cannot assume the effective input is the local target pixel). To get around that: the target's backdrop is ANTISYMMETRIC about x=584.5 through the entire vertical extent the top chrome can reach -- the hard split above (black x<=584, white x>=585) and the grey ramp below (value = round(x*255/1169), whose mean over any window centred on x=584.5 is exactly 127.5). Therefore S(u) = out(585-u) + out(584+u) must equal f(0) + f(255) for every u, for ANY symmetric kernel, if and only if the tone response is affine. Measured on glyph-free rows selected by max|second difference along the row| < 15 (rows 156-168 and 214-226 qualify; 170-212 and 228-252 are the 'Today' and clock text). S(u)/2 sits within 1 count of the affine midpoint for u >= 120 and rises to +13.8 (light) / +4.2 (dark) as u -> 3.\n\nSEPARATING A CURVED TONE RESPONSE FROM A CONTRAST-TRIGGERED LIFT. The S(u) excess alone cannot distinguish them. The discriminator is the grey ramp: a linear ramp is invariant under any symmetric blur, so a glass element sitting on it reads its own centre value as the effective input no matter how wide the kernel is. The two small round controls at rows 347-405 sit on the ramp at centre values 204.0 and 233.4 and read 232.0 and 239.0, against affine predictions of 225.9 and 239.5. A +6%-of-tint lift would have put them at 240.6 and 254.2. It is excluded. So the response is affine in input level and the excess is triggered by local contrast. (The +6.1 on the left control is explained by the white band beginning at row 390, only 5-35 px below the sampled rows.)\n\nWHAT I DELIBERATELY DID NOT USE. The brief suggested the round buttons at rows 347-405 as glass over the ramp. In the LIGHT capture they behave as glass and gave two of my four transfer points. In the DARK capture they read 108 and 117 where the top chrome's transfer predicts 151.6 and 168.8 -- 44 to 52 counts too dark -- so in dark appearance they are a different and much more opaque material (or a filled/selected state) and I dropped them. At the pixel scale both are essentially flat (sd 0.46 over a backdrop that varies 25 counts across their width), i.e. their blur is wider than the control itself; only the element-scale mean follows the affine transfer.\n\nTOOLBAR CAVEAT. I could not fit absolute tint and alpha for the bottom toolbar because no part of its footprint sits over a large uniform backdrop -- rows 2262-2341 are 8 px stripes, 2342-2421 are 32 px stripes, 2422-2441 are black but show the stripes from above via non-local sampling, and 2442+ is text. Its contrast-transmission numbers are reported; its DC transfer is not, and any number quoted for it would be a guess.\n\nTRANSPARENCY SLIDER. iOS 27 exposes a user transparency control and its position on this device is unknown. Every absolute alpha here is for whatever setting this iPhone 13 was on when these four frames were taken. Two things bound the worry: all four captures were taken at the same setting (so the light-vs-dark comparison and every ratio is unaffected), and the dark-appearance transmission of 0.586 is only 5 points below the 0.633 that an independent iOS 26 device recording produced, which is not the signature of a slider dragged far toward opaque. If it were, both polarities would be well below their published values, and the dark one is not.",
 "uncertain": [
  "The absolute alphas are conditional on the unknown iOS 27 transparency-slider position on this device. Ratios, the light-vs-dark comparison, the linearity result and the no-adaptation result are all immune; the two headline alphas (0.537 and 0.414) are not. Re-shooting one frame with the slider at a known/default position would settle it.",
  "The tint colour for the LIGHT material is only bracketed to 245 +/- 5 (0xF0 to 0xFA). f(0) reads 131 to 135 and f(255) reads 249 to 253 depending on where inside the element you sample, because the material carries its own vertical shading and because neighbouring content (the status bar above, the grey ramp below) bleeds in through a kernel that reaches roughly 100 px. Whether Apple's light tint is pure white or a very slightly off-white grey is not resolvable from this data. The dark tint (77, 0x4D) is better constrained relative to its own range.",
  "There is NO glass anywhere in these four captures over a large uniform mid-grey. The affine tone response is verified at inputs 0, 204, 233 and 255 for the light material and only at 0 and 255 for the dark one. The shape of the curve between 0 and 204 on a smooth backdrop is unmeasured. If one more capture were possible, the single highest-value change would be a flat 50% grey field large enough (say 400 px square) to put a whole toolbar button on -- that one patch would pin the curve's midpoint directly and retire most of this section.",
  "The `adaptivity <= 0.2` bound rests substantially on one point (the right round control at input 233.4), and on the assumption that that control is the same material as the top chrome. The evidence for that assumption is that it lands on the top chrome's affine line to within 0.5 counts -- which is good evidence but is also the thing being tested. Treat the bound as a strong indication rather than a measurement.",
  "I could not separate the 'blur' domain from the 'blend' domain. Fitting the two anchors in linear light instead of sRGB gives light alpha 0.254 / tint 243 and dark alpha 0.535 / tint 46. The near-split measurement favours a linear-light chain for the light material (predicts 202.7, measured 203-205) but decisively rejects it for the dark one (predicts 134.4, measured 110-112). Since one material cannot be composited in a different colour space from the other, the linear-light reading is wrong and the contrast-lift reading is right -- but I am inferring that by elimination, not measuring it.",
  "The toolbar's own tint and alpha are unmeasured, for the reason given in the method notes. Its mean level varies from 204.1 (rows 2306-2338) through 196.9 (2350-2418) to 192.1 (2424-2440) and I cannot tell how much of that is the material's own shading versus the changing effective backdrop.",
  "Rows 2422-2441 of the toolbar display the 32 px stripe pattern at nearly full amplitude (171 vs 214-215) over a backdrop that is uniformly black. Something -- edge clamping, a mirrored band, or refraction -- is sourcing that region's backdrop from 30-80 px above. I did not chase it because it is a geometry question, but it is a warning for anyone measuring near the bottom rim of a toolbar: the local target pixel is not the effective input there.",
  "The contrast-lift constant of 'about 6% of tint' comes from four numbers (13.8/245, 4.2/77, 12.5/245, 3.9/90) giving 0.056, 0.055, 0.051, 0.043. The first two agree suspiciously well and the second row band is noisier because the ramp below shifts the pill's far-field anchors. Call it 5-6% with the frequency/scale dependence entirely unmeasured -- I only ever probed it against one edge geometry (a single hard step)."
 ]
}
```

---

## Measured: Rim lighting geometry

Agent `a42c13e451953f128`.

```json
{
 "headline": "The rim is lit by a single parallel light pointing straight up the screen (0.0\u00b0 \u00b1 1\u00b0) with a counter-lobe of equal strength (1.00 \u00b1 0.05), delivering an additive, backdrop-independent white of +65/255 in the outermost device pixel that halves by 2.3 px inward \u2014 and there is a real dark contour, but it sits entirely *outside* the outline and is strongest exactly where the rim is unlit.",
 "confident": [
  {
   "quantity": "Light direction (angle of maximum rim brightness, 0\u00b0 = screen-up/-y, clockwise positive)",
   "value": "0.0 \u00b1 1.0",
   "unit": "degrees",
   "method": "SVD of the (angle \u00d7 depth) rim-excess matrix; first moment of component 0's angular weight over |\u03b8|\u226476\u00b0. Three independent measurements: BACK/black IMG_6644 = +0.19\u00b0, BACK/black IMG_6647 = \u22120.19\u00b0, MORE/white IMG_6647 = \u22120.25\u00b0. Folded curve w(\u03b8) vs w(\u2212\u03b8) agrees to <0.02 everywhere.",
   "coordinates": "BACK circle centre (113.50, 206.50) r=66.0; MORE circle centre (1055.50, 206.50) r=66.0",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Light is directional (parallel), not a point light at a screen position",
   "value": "lobe centroid identical within 0.5\u00b0 for two buttons 942 px apart in x",
   "unit": "degrees",
   "method": "Same SVD centroid computed for the BACK button (cx=113.5) and the MORE button (cx=1055.5). A point light above screen centre would tilt the two lobes in opposite directions by ~20\u00b0; measured tilt is +0.19\u00b0 and \u22120.25\u00b0.",
   "coordinates": "x=113.5 and x=1055.5, both at y=206.5",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Radial position of the peak (depth inside the outer edge)",
   "value": "0 to 1 (peak lies in the outermost device pixel; profile falls monotonically inward)",
   "unit": "px (1 dp = 3 px)",
   "method": "Measured on the 'Today' pill's straight top edge, which sits exactly at y=140.5, so every pixel row is either wholly inside or wholly outside \u2014 zero partial coverage, no antialiasing model needed. Row 141 is the brightest and every row inward is dimmer. Confirmed independently on the circles by a least-squares deconvolution over 8\u00d78-supersampled, wholly-interior pixels: the profile peaks at the innermost fitted depth (0.6 px) at every one of 180 angles sampled at 2\u00b0.",
   "coordinates": "IMG_6647 col x=470 and x=680, rows 140-152; circle wedges at r=66.0 about (113.50,206.50)",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Peak rim brightness above the local glass body, mean over the outermost 1 px band",
   "value": "65 \u00b1 3 (0.255 \u00b1 0.012 in shader units)",
   "unit": "levels of 255",
   "method": "'Today' pill top edge, pixel-aligned at y=140.5. Body = mean of rows 155-157. Light chrome over black (x=470): +64.3. Dark chrome over black (x=470): +65.3. Dark chrome over white (x=680): +62.3. Light chrome over white is clipped (body 249.3, rim 255).",
   "coordinates": "IMG_6644/IMG_6647, x=470 (backdrop 0) and x=680 (backdrop 255), rows 140-157",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Peak rim brightness extrapolated to the outline itself (d=0)",
   "value": "75 \u00b1 5 (0.294 \u00b1 0.020 in shader units)",
   "unit": "levels of 255",
   "method": "Quadratic through the three 1 px band means (65.3, 45.3, 30.3 at d = 0.5, 1.5, 2.5) evaluated at d=0. Gives 75.3 / 77.2 / 72.4 for the three uncontaminated capture-backdrop combinations.",
   "coordinates": "same as above",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Highlight is ADDITIVE and independent of backdrop and of light/dark appearance",
   "value": "+64.3 over black (body 137), +65.3 over black (body 37), +62.3 over white (body 181)",
   "unit": "levels of 255",
   "method": "Same element, same edge, same angle (\u03b8=0), three body levels spanning 37\u2192181. A multiply would need \u00d72.78 and would give +322 over white; a screen blend would give +22 over white. Both are excluded; addition with clipping at 255 fits all four cases.",
   "coordinates": "'Today' pill top edge, IMG_6644 and IMG_6647 at x=470 and x=680",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Width of the bright band \u2014 depth at which the excess falls to half the peak",
   "value": "2.34 (0.78 dp)",
   "unit": "px",
   "method": "Normalised 1 px band means from the pixel-aligned pill edge: 1.000, 0.694, 0.464, 0.158, 0.142, 0.096, 0.081, 0.066, 0.051, 0.035, 0.035, 0.020 at d = 0.5\u202611.5. Linear interpolation for the 50% crossing.",
   "coordinates": "IMG_6647 x=470, rows 141-152",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Width of the bright band \u2014 20% and 10% crossings",
   "value": "3.36 (1.12 dp) and 5.42 (1.81 dp)",
   "unit": "px",
   "method": "Same normalised band-mean profile, linear interpolation. Beyond d\u22484 the profile flattens into a 10-15% shoulder that decays to zero by d\u224812 px (4 dp).",
   "coordinates": "IMG_6647 x=470, rows 141-152",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Band width is independent of angle",
   "value": "2.5 at \u03b8=0 falling gently to 2.0 at \u03b8=60-90",
   "unit": "px (half-amplitude depth)",
   "method": "Deconvolved interior profile in \u00b13\u00b0 wedges at 2\u00b0 steps around the BACK circle; half-amplitude depth recorded per angle. Range 1.86-2.59 over all angles carrying a measurable lobe.",
   "coordinates": "circle centre (113.50,206.50) r=66.0",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "The rim is a single component (one depth profile scaled by one angular weight)",
   "value": "99.2% of variance in SVD component 0",
   "unit": "fraction of variance",
   "method": "SVD of the 180\u00d734 matrix of (rim excess above body) over angle \u00d7 depth, for d \u2264 9 px. Singular values 956.5, 77.8, 28.3 (IMG_6644) and 1029.5, 81.9, 29.2 (IMG_6647).",
   "coordinates": "BACK circle, 180 wedges at 2\u00b0 steps",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Counter-light ratio (brightness at \u03b8=180 divided by brightness at \u03b8=0)",
   "value": "1.00 \u00b1 0.05",
   "unit": "ratio",
   "method": "Peak-minus-body from the deconvolved interior profile. MORE button over white (cleanest, backdrop uniform on both poles): 52.07/54.03 = 0.964. BACK button over black, light chrome: 59.01/57.51 = 1.026. BACK over black, dark chrome gives 1.144 but its lower pole sits over the target's grey ramp (starts at row 260) which lifts the local body by 6 levels \u2014 discard.",
   "coordinates": "MORE (1055.5,206.5) r=66; BACK (113.5,206.5) r=66",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Angular falloff \u2014 half-width at half maximum of the lobe",
   "value": "35.2 / 37.2 / 34.5, mean 35.6 \u00b1 1.4",
   "unit": "degrees from the light direction",
   "method": "Folded, normalised angular weight from SVD component 0; linear interpolation of the 0.5 crossing. Three combinations: BACK/black light chrome, MORE/white dark chrome, BACK/black dark chrome.",
   "coordinates": "as above",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "The lobe is NOT a cosine power \u2014 the effective exponent varies across it",
   "value": "p_eff = 5.6 at 20\u00b0, 2.9 at 45\u00b0, 1.9 at 60\u00b0",
   "unit": "cosine exponent",
   "method": "p_eff = ln(w(\u03b8))/ln(cos \u03b8) evaluated on the folded normalised lobe. Measured w: 1.000, 0.864, 0.732, 0.706, 0.635, 0.550, 0.539, 0.490, 0.474, 0.453, 0.333, 0.307, 0.258, 0.262, 0.197, 0.136, 0.101, 0.080, 0.013 at \u03b8 = 0,12,16,20,24,28,32,36,40,44,48,52,56,60,64,68,72,76,80.",
   "coordinates": "BACK circle IMG_6644",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Best single-cosine-power fit to the lobe (if you must pick one uSpecularPow)",
   "value": "2.8 (IMG_6644), 2.9 (IMG_6647 BACK), 2.6 (IMG_6647 MORE)",
   "unit": "exponent",
   "method": "Grid search minimising RMS against the folded normalised lobe, 0-88\u00b0 in 4\u00b0 steps. Residual RMS 0.077-0.089 (8-9% of peak) \u2014 a visibly poor fit: it undershoots the crown and overshoots the shoulder. Matching HWHM alone instead gives p = 3.05-3.58.",
   "coordinates": "as above",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Best simple empirical form for the lobe",
   "value": "(1 \u2212 \u03b8/90\u00b0)^1.33, k = 1.27 to 1.38",
   "unit": "\u2014",
   "method": "Same grid search over k. Residual RMS 0.035-0.039 (3.5-3.9% of peak), less than half the error of the best cosine power.",
   "coordinates": "as above",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Dark contour \u2014 position",
   "value": "entirely OUTSIDE the outline; zero darkening inside",
   "unit": "\u2014",
   "method": "Both circles are exactly r=66.0 about y=206.5 (established from the pixel-aligned top and bottom edges of the MORE button at y=140.5 and y=272.5, and from the row-207 chords of both buttons, each exactly 132 px of body). The dark pixels sit at x=989 and x=1122 for the MORE button, i.e. wholly outside the boundaries at 989.5 and 1121.5. The outermost wholly-interior pixel reads 181.0 against a body of 181.0 (IMG_6647, \u03b8=90) \u2014 no interior darkening within 1 level of 255.",
   "coordinates": "IMG_6647 row 207, x=987-991 and x=1120-1124",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Dark contour \u2014 depth against a pure white backdrop, dark appearance",
   "value": "0.0% at \u03b8=0, 5.7% at 20\u00b0, 20.5% at 40\u00b0, 36.2% at 60\u00b0, 61.2% at 90\u00b0",
   "unit": "% below the 255 backdrop",
   "method": "Bilinear sample at radius 66.5 (0.5 px outside the outline) around the MORE button, restricted to rays that stay above row 260 where the backdrop is exactly 255. Symmetric to within 1% between the \u03b8 and \u2212\u03b8 sides (e.g. 61.2% at 90\u00b0 vs 61.6% at 270\u00b0).",
   "coordinates": "MORE button, centre (1055.5,206.5), sample radius 66.5",
   "captures": "IMG_6647"
  },
  {
   "quantity": "Dark contour \u2014 depth against a pure white backdrop, light appearance",
   "value": "9.4% at \u03b8=0, 8.1% at 20\u00b0, 11.3% at 40\u00b0, 18.8% at 60\u00b0, 31.4% at 90\u00b0",
   "unit": "% below the 255 backdrop",
   "method": "Same rays, IMG_6644. Note this appearance retains a small isotropic floor (~8%) that the dark appearance does not.",
   "coordinates": "MORE button, centre (1055.5,206.5), sample radius 66.5",
   "captures": "IMG_6644"
  },
  {
   "quantity": "Dark contour \u2014 width",
   "value": "1.5",
   "unit": "px",
   "method": "IMG_6647 row 207: 255 (x=987), 178 (988), 98 (989), 181 (990, body). The 178 is a partial blend, giving a ~1.5 px stroke centred about 0.25 px outside the outline. Same structure at every angle and in IMG_6644 (254, 215, 175, 249).",
   "coordinates": "IMG_6647 and IMG_6644, row 207, x=987-990",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Dark contour vanishes exactly where the rim is lit, and this is not a uniform shadow filled in by highlight bleed",
   "value": "255.0 exactly at \u03b8=0 (dark appearance over white)",
   "unit": "levels of 255",
   "method": "If a uniform \u2212156 shadow were being cancelled at the pole by an outward bleed of the specular, the bleed would have to be \u2265156 levels; the rim's own peak just inside the edge is only +62, and an outward glow cannot exceed its source. So the contour itself is angularly modulated.",
   "coordinates": "IMG_6647, (1055.5, 140.0), i.e. radius 66.5 at \u03b8=0",
   "captures": "IMG_6647"
  },
  {
   "quantity": "Dark contour is invisible over a black backdrop",
   "value": "0 at every angle",
   "unit": "levels of 255",
   "method": "Same outside-sampling around the BACK button, whose surroundings are the target's hard black. Because it darkens what is behind it, a black backdrop carries no information \u2014 this is why the contour cannot be seen on the BACK button at all.",
   "coordinates": "BACK button, centre (113.5,206.5), sample radius 66.5",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Highlight colour",
   "value": "neutral \u2014 R=G=B at every sample",
   "unit": "\u2014",
   "method": "Raw RGB up the top rim of the BACK button: (193,193,193), (179,179,179), (164,164,164), (145,145,145), (143,143,143) at rows 141-145. Backdrop is neutral, so this shows the highlight adds no tint of its own; it cannot test hue preservation over saturated content.",
   "coordinates": "IMG_6644, x=114, rows 141-145",
   "captures": "IMG_6644"
  },
  {
   "quantity": "Circle geometry (nav chrome)",
   "value": "diameter 132.0 px = 44.0 dp; centres (113.50, 206.50) and (1055.50, 206.50)",
   "unit": "px",
   "method": "Derivative-centroid edge locator on 720 rays, robust algebraic circle fit with 2\u03c3 trimming: cx=113.502, cy=206.503, r=65.934, residual RMS 0.035 px over 469 points. Cross-checked geometrically: the MORE button's pixel-aligned top edge at y=140.5 and bottom edge at y=272.5, and both buttons' row-207 chords of exactly 132 body pixels.",
   "coordinates": "IMG_6644/6645/6647/6646, rows 140-273",
   "captures": "all four"
  },
  {
   "quantity": "Compositing is done on encoded (non-linear) values, not in linear light",
   "value": "observed edge pixel tracks coverage\u00d7body, not sRGB(coverage\u00d7linear(body))",
   "unit": "\u2014",
   "method": "34 rows down the BACK button's left edge where the geometric coverage of the boundary pixel sweeps 0.09\u21920.93. At coverage 0.50 the pixel reads 57; encoded blending predicts 66, linear-light blending predicts 96. Matters because every amplitude above is quoted in encoded levels.",
   "coordinates": "IMG_6644, rows 190-223, x\u224848",
   "captures": "IMG_6644"
  },
  {
   "quantity": "Registration re-verified on the regions used",
   "value": "mean absolute difference 0.004 (stripe band), 0.124 (checkerboard), 0.226 (grid); best integer shift (0,0) in all three",
   "unit": "levels of 255",
   "method": "Direct MAD of IMG_6644 against calibration-target-light.png over uncovered row bands, plus a \u00b12 px shift search.",
   "coordinates": "rows 430-730, 1580-1780, 1780-2130",
   "captures": "IMG_6644"
  }
 ],
 "method_notes": "DATASET RE-READ (important, and different from the brief). The light/dark axis that matters for the chrome is NOT which target was displayed \u2014 it is the app's appearance. IMG_6644 and IMG_6645 have pixel-identical top chrome; so do IMG_6647 and IMG_6646. IMG_6644/6645 carry LIGHT chrome (light glass, black glyphs); IMG_6647/6646 carry DARK chrome (dark glass, white glyphs). The two target PNGs are identical over rows 0-260 and over rows 2302-2449, so every glass element sees the same backdrop in all four frames. The useful 2\u00d72 is therefore {light chrome, dark chrome} \u00d7 {black backdrop, white backdrop}, obtained from the appearance pair and from the target's hard black/white split at x=585.\n\nSUBJECT SELECTION. The brief names the bottom toolbar's circles as the best subjects. They are the worst. Their backdrop is 16/32-px vertical stripes, and the rim compresses those stripes into the edge: at \u03b8=270 on the left toolbar circle (IMG_6647, row 2376) the rim reads 126 over a body of 81 \u2014 a +45 \"highlight\" at the angle where the specular is provably zero. That is refraction of the white stripe at x\u226479, and it cannot be separated from the specular anywhere on those circles. I abandoned them for photometry (they are ~144 px = 48 dp diameter, larger than the nav circles). The two small capsules at rows 350-402 are also unusable: they are a different style with a dark outline and NO rim highlight at all (IMG_6644 col x=940 reads a flat 231-232 across the whole element against a 205 backdrop, where a +65 highlight would clip to 255).\n\nThe usable subjects are the two nav circles at rows 140-273, each 132.0 px = 44.0 dp across, one sitting on pure black (BACK, cx=113.50) and one on pure white (MORE, cx=1055.50), plus the \"Today\" pill whose straight top edge crosses the same black/white split. Because those backdrops are uniform, refraction moves nothing and everything left in the rim is lighting. I confirmed nothing bright lies above the BACK button (max luma 2/255 over rows 100-140, x 40-190), so the top rim cannot be refracted content.\n\nGEOMETRY. Circle centres and radii came from a derivative-centroid edge locator (centroid of |dL/dr| across the transition, which is amplitude-independent so a bright rim does not bias it) on 720 rays, followed by an algebraic circle fit with three rounds of 2\u03c3 trimming: cx=113.502, cy=206.503, r=65.934, residual RMS 0.035 px. I then cross-checked against pure geometry \u2014 the MORE button's top and bottom edges land exactly on pixel boundaries at y=140.5 and y=272.5, and both buttons' row-207 chords are exactly 132 body pixels \u2014 and adopted r=66.0, cy=206.5 for the photometry.\n\nPHOTOMETRY. Two independent methods, deliberately chosen so neither depends on an antialiasing model.\n(1) Circles: for each 2\u00b0 angle I took a \u00b13\u00b0 wedge and used only pixels whose 8\u00d78 supersample grid lies WHOLLY inside the outline and wholly within the wedge. For those, the observed value is the exact area-average of the radial profile with no backdrop and no coverage term. I solved a regularised least-squares system (0.25 px grid, second-difference smoothing, \u03bb=1-2) for the profile g(d) on d \u2208 [0.6, 20]. Body = mean of g over d \u2208 [12,20]; amplitude = max(g over d \u2264 9) \u2212 body. Glyph pixels were excluded by a per-depth-ring median/MAD mask (132 pixels in IMG_6644, 61 in IMG_6647) \u2014 without this the chevron sits inside the body window and inflates the amplitude by 1-2 levels at \u03b8\u224823\u00b0 and 157\u00b0.\n(2) The \"Today\" pill's top edge, which is straight and lands exactly at y=140.5. Every pixel row there is wholly inside or wholly outside, so the 1 px band means are read directly off the raster with no processing at all. This is the measurement I trust most and it is the source of the peak amplitude and width numbers. Rows 155-157 give the body.\n\nI checked that iOS composites in encoded (non-linear) values before quoting any amplitude \u2014 34 boundary pixels down the BACK button's left edge, where coverage sweeps 0.09\u21920.93, follow coverage\u00d7body rather than the linear-light prediction.\n\nLOBE EXTRACTION. I formed the 180\u00d734 matrix of (rim excess above body) over angle \u00d7 depth and took its SVD. Component 0 carries 99.2% of the variance, so the rim is one depth profile scaled by one angular weight \u2014 the depth shape does not change with angle. The light direction is the first moment of that angular weight over |\u03b8| \u2264 76\u00b0; the lobe shape is that weight folded about \u03b8=0 and normalised.\n\nOUTSIDE PROFILE. For the dark contour I bilinearly sampled at radius 66.5, 67.5, 68.5, 69.5 around the MORE button, restricted to rays staying above row 260 where the backdrop is exactly 255.\n\nAngle convention throughout: \u03b8=0 is screen-up (\u2212y), increasing clockwise, so 90\u00b0=right, 180\u00b0=down, 270\u00b0=left. Scale: 1 dp = 3 px (confirmed \u2014 the nav circles are 132 px = 44 dp).\n\nScripts are in C:/Users/shaya/AppData/Local/Temp/claude/D--Work-AndroidStudioProjects-SideProjects-kmp-Vitals/d83608da-a0a9-446c-9f1b-ec02e5b88c50/scratchpad/caps/work/ \u2014 the shared modules are rim.py (edge locator and circle fit), deconv.py (coverage-aware deconvolution) and ang.py (interior-only profile and per-angle statistics); the drivers are s1_setup.py through s34.py. Note that directory also holds files from other sessions.",
 "confirms_us": [
  "bevelPeak = 0 is right. The rim is a chamfer, not a bead: the peak lies in the outermost device pixel and the profile falls monotonically inward at every one of the 180 angles sampled, in both appearances and over both backdrops. There is no dark outermost pixel with the highlight sitting inside it.",
  "DarkChrome's counterLight = 1.0 is right. Measured 1.00 \u00b1 0.05.",
  "The shader's factorisation highlight = bevelBand(depth) \u00d7 lobe(angle) is structurally correct. The SVD puts 99.2% of the rim's variance in a single depth profile \u00d7 angular weight, and the measured half-amplitude depth stays within 1.86-2.59 px across every angle that carries a lobe.",
  "The highlight is additive white, so DarkChrome's highlightChroma = 0 is at least consistent with what a neutral backdrop can show. Amplitude is +64.3 over a body of 137, +65.3 over a body of 37 and +62.3 over a body of 181 \u2014 the same addition in all three, with multiply and screen both excluded by an order of magnitude.",
  "DarkChrome is by far the closest preset overall. Its combination (specular 0.06, edgeLight 5.1, counterLight 1.0, bevel 1.2 dp) evaluates to a peak highlight of 0.213 at the edge against a measured 0.256-0.298, and a half-amplitude depth of 1.8 px against a measured 2.34 px. Both are within ~30%; every other preset is off by 2.5-3x.",
  "The doc comment on counterLight \u2014 'a bar wants all of it ... which is exactly a light from directly above reaching both faces equally' \u2014 is exactly what a circle shows too. This is the first measurement on a shape that samples every normal direction at once, and it confirms the reading taken from a straight-edged bar.",
  "The rim highlight really is confined to a narrow band and does not bleed into the interior: by 4 dp inward the excess is below 2/255."
 ],
 "contradicts_us": [
  {
   "ours": "GlassLight.Default = (x = -0.35, y = -0.94), a light 20.4\u00b0 to the left of vertical",
   "measured": "straight up, 0.0\u00b0 \u00b1 1.0\u00b0. Three independent lobe centroids give +0.19\u00b0, \u22120.19\u00b0, \u22120.25\u00b0, and the folded lobe w(\u03b8) vs w(\u2212\u03b8) matches to better than 0.02 at every angle.",
   "significance": "Directly visible on any circular element: our rim currently peaks at the 10-11 o'clock position where iOS peaks at 12 o'clock. The correct default is (0, -1)."
  },
  {
   "ours": "counterLight = 0.35 on Regular, Chrome, Clear and Thick",
   "measured": "1.00 \u00b1 0.05",
   "significance": "At 0.35 the bottom of a circular control reads roughly a third as bright as the top; iOS renders them equal. Our shader also tightens the counter-lobe as it weakens (counterPow = uSpecularPow \u00d7 mix(1.6,1.0,...)), so 0.35 gives a narrower as well as dimmer lower lobe \u2014 the measured lower lobe has the same width as the upper one (half-amplitude 2.43 px vs 2.49 px at \u03b8=180 and \u03b8=0)."
  },
  {
   "ours": "specular = 0.55 (Regular) / 0.50 (Chrome), with edgeLight = 1",
   "measured": "peak highlight of 0.256 (outermost pixel band) to 0.298 (extrapolated to the outline)",
   "significance": "Evaluating our own expression at \u03b8=0, d=0 with bevelPeak=0 gives highlight = specular\u00d7(1 + 0.5\u00d7edgeLight) + fresnel term \u2248 0.83 for Regular and 0.76 for Chrome. That is 2.6-2.9x the measured value. To land on 0.29 with edgeLight = 1 and counterLight = 1, specular should be about 0.19-0.20."
  },
  {
   "ours": "specularPower = 6 (Regular, Chrome), 7 (Thick)",
   "measured": "half-width at half maximum 35.6\u00b0 \u00b1 1.4\u00b0",
   "significance": "cos^6 has a HWHM of 26.9\u00b0 and is down to 0.016 at 60\u00b0 where the measurement reads 0.26. Our lobe is far too tight and its shoulders are essentially absent. Matching the measured HWHM needs p \u2248 3.4; the best least-squares single power is 2.8, and even that leaves an 8-9% RMS residual because the real lobe is not a cosine power at all \u2014 its effective exponent runs from 5.6 at 20\u00b0 down to 1.9 at 60\u00b0. (1 \u2212 \u03b8/90\u00b0)^1.33 fits it to 3.5%."
  },
  {
   "ours": "bevel = 3 dp (Chrome) / 4 dp (Regular), consumed as bevelBand = smoothstep(uBevel, 0, depth), whose half point is at uBevel/2",
   "measured": "half-amplitude at 2.34 px = 0.78 dp, i.e. an equivalent uBevel of about 4.7 px = 1.6 dp",
   "significance": "Chrome's lit band is roughly twice as wide as measured and Regular's about 2.6x. DarkChrome's 1.2 dp is the closest but ~30% narrow. Separately, the measured profile is not a clean smoothstep: after the core it flattens into a 10-15% shoulder that persists to about 4 dp, which nothing in the current bevelBand produces."
  },
  {
   "ours": "edgeShadow = 0 on every preset",
   "measured": "a real dark contour reaching 61.2% below a white backdrop (dark appearance) and 31.4% (light appearance) at the unlit sides",
   "significance": "This is the single largest missing feature. It is what makes the iOS control read as a distinct object over bright content, and at 0 we have nothing there."
  },
  {
   "ours": "edgeShadow is drawn INSIDE the shape \u2014 rimDark is centred 1.5 px inside the edge and subtracted from col within mat",
   "measured": "the contour lies entirely OUTSIDE the outline; the outermost wholly-interior pixel matches the body to within 1 level of 255",
   "significance": "Our placement is wrong in kind, not just in magnitude. iOS darkens the ~1.5 px of backdrop immediately outside the boundary and leaves the glass itself untouched. Because our shader only writes inside mat, reproducing this needs either an outset stroke drawn by the host or an inflated shape mask \u2014 worth flagging before someone simply raises uEdgeShadow off zero and gets a dark ring in the wrong place."
  },
  {
   "ours": "edgeShadow is isotropic \u2014 rimDark depends only on depth, with no dependence on the light",
   "measured": "the contour is strongly modulated by the lighting: 0.0% at \u03b8=0 rising to 61.2% at \u03b8=90 (dark appearance over white)",
   "significance": "The doc comment already anticipates this role ('what defines a shape where nothing is lit'), but the implementation draws it all the way round at constant strength. The measurement says its strength is roughly complementary to the specular lobe, so the correct form is closer to rimDark \u00d7 uEdgeShadow \u00d7 (1 \u2212 lobe), not rimDark \u00d7 uEdgeShadow."
  },
  {
   "ours": "edgeShadow's only calibrated value is 0.08 (GlassTabBarStyle.HeldLens), described as a one-pixel dark step",
   "measured": "0.31 of the backdrop in the light appearance and 0.61 in the dark appearance, at the unlit sides, over white",
   "significance": "An order of magnitude larger than the value in the codebase. The 0.08 was read from a bar over dark content where a multiplicative contour is nearly invisible; over white it is one of the most prominent features of the material."
  },
  {
   "ours": "the doc on edgeLight says the resting bar is 'a hairline with almost no lobe behind it' \u2014 i.e. edgeLight well above 1 with a tiny specular",
   "measured": "a single component: one lobe whose peak is in the outermost pixel and which halves 2.3 px inward \u2014 there is no separate hairline with a different depth profile",
   "significance": "Our shader gives key/counter (bevelBand, half at uBevel/2) and edgeLine (smoothstep(2,0,depth), half at 1 px) different depth profiles, so raising edgeLight changes the shape of the rim. The SVD says iOS's rim has only one shape. Splitting brightness between two terms of different widths is not supported by this data; a single band of half-width 2.3 px carrying the whole amplitude is."
  }
 ],
 "uncertain": [
  "The exact peak value at the outline itself (d=0). Everything at d \u2265 0.5 px is measured without any antialiasing model, but the value AT the boundary can only be extrapolated (quadratic through the three band means gives 72-77 of 255). A coverage-aware deconvolution that does model the boundary pixel showed a systematic ~10-level offset at d=0 on the unlit sides, which I could not attribute to either a genuine edge darkening or an imperfect coverage model, so I did not use it for any quoted number.",
  "The mechanism behind the outer dark contour. I can rule out a plain drop shadow filled in by highlight bleed (at \u03b8=0 in the dark appearance the outside pixel reads exactly 255.0, which would need a bleed of \u2265156 levels against a rim whose own peak is +62). But whether iOS draws a lighting-modulated edge shadow, a stroke, or something else is not determinable from four frames. Note also that in the light appearance the contour keeps an ~8% floor at the lit poles while in the dark appearance it goes to exactly zero \u2014 I have no explanation for that difference.",
  "Whether the contour is multiplicative or additive. Over black it is invisible (which is consistent with multiplicative but also with an additive line clamping at 0), and there is no mid-grey backdrop under any element that carries a rim highlight. The pill capsules do sit over the grey ramp, but they are a different, unlit style whose contour reads a constant ~174/255 over backdrops of 190, 217 and 255 \u2014 suggestive of an opaque stroke, but they are not the same element type so I would not transfer that reading to the circles.",
  "highlightChroma cannot be tested at all. No glass element in any of the four captures sits over saturated colour \u2014 the primaries band is at rows 960-1120, far from every piece of chrome. The highlight is neutral over neutral backdrops (RGB exactly equal at every sample), which says it adds no tint of its own but says nothing about whether iOS preserves hue when brightening a coloured backdrop.",
  "A persistent 3-5% top/bottom asymmetry that I believe is contamination rather than lighting. Every element's lower edge sits at or below row 260 where the target's grey ramp begins, and the material's blur pulls that ramp in \u2014 the BACK button's body rises from 133 at the top to 140 at the bottom in the light appearance. The MORE button, whose lower surroundings differ from its upper by only ~25 levels, gives counter/key = 0.964; the BACK button in the dark appearance, whose lower surroundings differ by the full ramp step, gives 1.144. I quote 1.00 \u00b1 0.05 and treat the spread as systematic, not as evidence of an asymmetric light.",
  "Whether the light is strictly directional or a very distant point light. I could only test two horizontal positions (x=113.5 and x=1055.5, 942 px apart) at the same y. Both give a lobe centroid within 0.25\u00b0 of vertical, which excludes any point light closer than several screen heights, but does not distinguish a truly parallel light from a very distant one. I could not test the vertical axis at all, because the only elements 2170 px lower are the toolbar circles and those are refraction-dominated.",
  "The toolbar circles' radius, quoted as ~72 px (48 dp). It comes from the position of the outer dark contour at x=228 on the right of the left circle in IMG_6647 mirrored about the fitted centre. A disc-model fit over black-backdrop pixels gave 72.25 and a gradient-ridge fit gave 69-71; the stripes and the shadow both generate competing gradients. Call it 72 \u00b1 1.5.",
  "Why the two small capsules at rows 350-402 have no rim highlight whatsoever while the 'Today' pill and the nav circles do. Both were verified against backdrops where a +65 highlight would have been unmistakable. It appears to be a genuinely different control style, which means 'all glass elements carry this rim' is not a safe generalisation \u2014 but I cannot say from these frames which iOS style each one is.",
  "The long low shoulder in the depth profile (10-15% of peak from about d=4 to d=6, decaying to zero by d\u224812). It is well above noise and identical in both appearances and over both backdrops, but I cannot tell whether it is part of the lighting, the material's own vertical shading, or the interior scatter picking up the surroundings. If it is lighting, no smoothstep bevel of any width reproduces it."
 ]
}
```

---

## Measured: Chromatic dispersion at the rim

Agent `a505b949a8c10a0f4`.

```json
{
 "headline": "The iOS 27 material shows no chromatic dispersion at all: every one of ~74,000 glass pixels across four captures is exactly neutral (R=G=B, chroma 0), including refracted edges with 30 levels/px of contrast, which bounds any red/blue split at under 0.03 px \u2014 roughly 50x smaller than the 1.5 px split our uAberration=0.018 default produces.",
 "confident": [
  {
   "quantity": "Existence of a colour fringe at any glass rim",
   "value": "none \u2014 max(R,G,B) - min(R,G,B) = 0 at 100% of glass pixels",
   "unit": "8-bit levels",
   "method": "Per-pixel chroma = max(R,G,B) - min(R,G,B) over whole rectangles covering every glass element, in all four captures. Rectangles included the antialiased outline, the rim band and the interior \u2014 no masking, no thresholding.",
   "coordinates": "top chrome rows 135-285 x 20-1150; ramp buttons rows 340-412 x 860-1130; bottom toolbar rows 2296-2456 x 70-1100. All four captures IMG_6644/6645/6646/6647.",
   "captures": "IMG_6644, IMG_6645, IMG_6646, IMG_6647"
  },
  {
   "quantity": "Per-channel luminance-edge position at a refracted rim edge (R, G, B)",
   "value": "R = G = B = 1066.7614 px; separations R-G = 0.00000, B-G = 0.00000, B-R = 0.00000",
   "unit": "px (screen), subpixel",
   "method": "Subpixel edge position per channel = centroid of |d/dx| of the 14-px intensity profile across the edge. Repeated on six independent edges (listed in method_notes); all six returned identical positions in all three channels to every printed decimal, because the underlying RGB triples are bit-identical.",
   "coordinates": "y=2418, x=1058..1071 (trash button lower-left rim, refracted 32px-stripe edge, 167->212 over 2 px). Also y=2425 x962-975 (966.5122), y=2331 x951-961 (954.5180), y=2325 x983-996 (987.6935), y=163 x580-591 (585.1000, the hard b/w split seen through the Today pill), IMG_6647 y=147 x586-597 (590.6538).",
   "captures": "IMG_6644, IMG_6647"
  },
  {
   "quantity": "Which channel lands furthest out / is blue displaced more than red",
   "value": "neither \u2014 no channel is displaced relative to any other",
   "unit": "px",
   "method": "Direct comparison of the per-channel edge centroids above. All differences are exactly 0.00000 px, so the question of ordering does not arise.",
   "coordinates": "same six edges as above",
   "captures": "all four"
  },
  {
   "quantity": "Upper bound on red-to-blue separation at the rim (detection floor)",
   "value": "< 0.03",
   "unit": "px (screen-space)",
   "method": "Empirical: took the real toolbar glass region, resampled the luminance with bilinear interpolation to make a synthetic R shifted by -d/2 and B by +d/2, re-quantised to 8 bits, and counted pixels whose chroma would then be >= 1 LSB. Result: d=0.020 px -> 0 of 73,911 pixels detectable; d=0.033 px -> 243 pixels; d=0.050 px -> 4,427 pixels; d=1.51 px -> 37,415 pixels. Observed in the real capture: 0 pixels. Cross-check by the analytic route 1/max|grad| = 1/30.4 = 0.033 px, which agrees. Note the simulation shifts in x only, so it under-states sensitivity at rims whose normal is vertical; the true bound is at least this tight.",
   "coordinates": "toolbar rows 2296-2456 x 70-1100, mask = glass footprint eroded 3 px, drawn glyphs (L<130) dilated 4 px and excluded; N = 73,911 px",
   "captures": "IMG_6644"
  },
  {
   "quantity": "Steepest gradient in genuinely refracted backdrop content (proves the measurement has sensitivity)",
   "value": "30.4",
   "unit": "8-bit levels per px",
   "method": "hypot of central-difference gx, gy on the green channel, restricted to the eroded glass interior with drawn glyphs excluded. 1,219 pixels exceed 20 levels/px and 7,416 exceed 15 levels/px, so the null is not a null for lack of signal.",
   "coordinates": "argmax at y=2418, x=1064 (trash button lower-left rim). Top chrome equivalent: 12.5 levels/px at y=163, x=585.",
   "captures": "IMG_6644 (identical in IMG_6645)"
  },
  {
   "quantity": "Fringe our own shipped default would produce on this exact backdrop",
   "value": "peak 33-35 levels of chroma, mean 5-16 levels, 37,415 of 73,911 rim pixels visibly non-neutral",
   "unit": "8-bit levels",
   "method": "uAberration 0.018 with refractionDepth 14dp = 42 px at 3x gives R-to-B sample separation = 2 * 0.018 * 42 = 1.51 px at the rim (bend=1). Computed two ways: (a) sample-space \u2014 differenced the real target backdrop at two points 1.51 px apart, giving mean |dR-dB| = 96 levels over the 8px-stripe band and 24 levels over the 32px band, times the measured 0.17 transmission = 16.4 and 4.1 levels mean, 32.7 peak; (b) screen-space \u2014 shifted the observed composited image by 1.51 px, giving 34.7 peak / 4.9 mean. The two agree.",
   "coordinates": "8px stripe band rows 2262-2341, 32px stripe band rows 2342-2421, x 70-1100",
   "captures": "IMG_6644 + calibration-target-light.png"
  },
  {
   "quantity": "Equivalent bound on our uAberration parameter",
   "value": "< 0.0004 at our current refractionDepth (14dp/42px); < 0.0015 if iOS's own ~11px rim displacement is used instead",
   "unit": "fraction of displacement (dimensionless)",
   "method": "Our shader sets R-to-B separation = 2 * uAberration * bend * lens. Solving 2 * s * 42 < 0.03 px gives s < 3.6e-4. Using iOS's measured rim displacement of 9-13 px instead: 2 * s * 11 < 0.03 gives s < 1.4e-3. Our shipped default is 0.018, so it is 12x to 50x too large depending on which displacement you scale by.",
   "coordinates": "n/a \u2014 derived from the 0.03 px bound and GlassStyle.kt defaults",
   "captures": "n/a"
  },
  {
   "quantity": "Control: is 1-LSB chroma representable in these files at all?",
   "value": "yes \u2014 8,950 pixels at chroma=1 and 2,789 at chroma=2 in IMG_6644 alone",
   "unit": "pixel count",
   "method": "Histogram of per-pixel chroma over the whole 1170x2532 frame. 193,733 pixels have chroma > 0. So the capture pipeline is not snapping near-neutral colours to grey, and a 1-level fringe would have been recorded.",
   "coordinates": "whole frame; e.g. the green battery glyph at y=64, x=1020 records RGB (98,198,101), chroma 100",
   "captures": "all four (IMG_6645: 9,732 at chroma=1; IMG_6646: 10,512; IMG_6647: 10,675)"
  },
  {
   "quantity": "Material transmission (backdrop contrast retained), light mode",
   "value": "0.157 to 0.232",
   "unit": "fraction of backdrop contrast",
   "method": "(interior value over white backdrop - interior value over black backdrop) / 255, read on large flat interior areas away from rims and glyphs. Needed to convert a sample-space fringe into an observable one.",
   "coordinates": "Today pill: 235.0 at rows 210-222 x 600-620 (over white) vs 175.9 at rows 210-222 x 555-575 (over black) -> 0.232. Toolbar buttons: 221 vs 181 -> 0.157.",
   "captures": "IMG_6644"
  }
 ],
 "method_notes": "REGISTRATION SANITY CHECK (not re-derived, just confirmed once). Mean abs difference between IMG_6644 and calibration-target-light.png over row 320, x 0-800 = 0.028 levels. The target's 4px-period stripes at row 600, x 100-140 are bit-exact in the screenshot, as are the 8px stripes at x 300-340. So there is no resampling anywhere and target pixel (x,y) is screenshot pixel (x,y). Separately, rows 119-139 show the hard split blurred symmetrically over ~4 px about its true position x=584.5 \u2014 that is iOS's scroll-edge blur, not a registration offset, and I did not use those rows.\n\nCAPTURE PAIRING. IMG_6644 and IMG_6647 show the light target; IMG_6645 and IMG_6646 the dark one (MAD 0.00 vs 7.65 over rows 600-700). But the two targets are byte-identical in the two chrome bands, so the glass backdrop is the same neutral content in all four. IMG_6644/6645 are the same UI state (max diff 3 levels in the toolbar; the clock text differs in the top chrome); IMG_6647/6646 are a second, different state. So this is two distinct chrome states, each captured twice.\n\nTHE COLOUR-MANAGEMENT CONFOUND WAS NEVER ENGAGED. Every glass element in these captures sits over a region of the target that is pure neutral (the hard black/white split, the grey ramp, black/white square-wave stripes). Neutrals are pixel-exact through iOS's display transform, as the brief states and as I re-confirmed. I never used the target PNG as a colour reference; the primary measurement is intra-screenshot (chroma within a single pixel), which is immune to the confound entirely. A fringe would appear as chroma where the backdrop had none, and the display transform maps the neutral axis to itself, so it cannot manufacture or erase that.\n\nPRIMARY MEASUREMENT. Per-pixel chroma = max(R,G,B) - min(R,G,B), computed over whole rectangles covering each glass element, unmasked: top chrome rows 135-285 x 20-1150, ramp buttons rows 340-412 x 860-1130, bottom toolbar rows 2296-2456 x 70-1100. Maximum chroma = 0 in every rectangle in every capture. The only chroma anywhere near the chrome is in the status bar rows 0-118 (the green battery glyph, chroma 100), which is drawn UI, not glass, and which serves as proof the analysis would have seen colour had there been any.\n\nEDGE FIT. For each test edge I took a 9-14 px horizontal profile crossing it and computed, independently per channel, the centroid of |d/dx| as the subpixel edge position. Six edges: (1) y=2418 x1058-1071, refracted 32px-stripe edge at the trash button's lower-left rim, 167->212 in 2 px; (2) y=2425 x962-975, lower-right rim; (3) y=2331 x951-961, the rim itself crossing a stripe, profile 255,255,255,198,11,100,228 \u2014 every one of those a neutral triple; (4) y=2325 x983-996, a refracted arc at the button top; (5) y=163 x580-591, the target's hard black/white split seen through the Today pill (185->225, pure neutral either side by construction); (6) IMG_6647 y=147 x586-597. All six give identical R, G and B positions to every printed decimal, which is inevitable given the RGB triples are bit-identical, and that identity is itself the result.\n\nINTERIOR MASK, for the gradient statistics. Glass footprint = |shot - target| > 12 in any channel; eroded by a min-filter of radius r to drop the antialiased outline; drawn glyphs (green < 130) dilated by 4 px and excluded. Chroma stayed 0 at r = 2, 3, 4 and 6, so the null holds right into the 2px-from-outline band where our own shader's bend peaks. N = 74,746 px at r=2 down to 63,785 at r=6.\n\nDETECTION FLOOR. Rather than argue from quantisation theory I measured it: took the real green channel of the toolbar region, produced synthetic R and B by bilinear resampling at -d/2 and +d/2, rounded to 8 bits, and counted pixels with chroma >= 1. 0.020 px -> 0 detectable pixels; 0.033 px -> 243; 0.050 px -> 4,427; 0.100 px -> 18,865; 1.51 px -> 37,415. The observed capture yields 0. So the honest bound is that any red/blue separation is under about 0.03 px, and the data cannot distinguish 0.02 px from zero. The independent analytic bound 1/max|grad| = 1/30.4 = 0.033 px agrees. Because the simulation shifts only in x while a real dispersion acts along the local normal, this bound is conservative.\n\nRIM DISPLACEMENT (supporting number, less certain \u2014 see uncertain list). Matched observed stripe-edge positions inside the glass against the known backdrop edge positions, at rows where the rim normal is exactly horizontal. Trash button left rim, y=2376: observed dark->bright edge at x=950.33, backdrop edge at x=959.50, offset +9.17 px, at a depth of ~10 px inside the rim; the next edge at 35 px depth reads +0.35 px, so the displacement decays to nothing over about 35 px. Share button right rim, y=2376: observed edge at x=220.08 vs backdrop 207.50, offset -12.58 px at ~8 px depth. Both signs point inward, i.e. the material paints content drawn from deeper inside out toward the rim.\n\nSCRIPTS. All analysis scripts and crops are in C:/Users/shaya/AppData/Local/Temp/claude/D--Work-AndroidStudioProjects-SideProjects-kmp-Vitals/d83608da-a0a9-446c-9f1b-ec02e5b88c50/scratchpad/caps/work/ (s00.py through s21.py; s04.py is the primary chroma scan, s18.py the detection floor and the prediction for our own defaults, s20.py the per-channel edge fits). Nothing was written to D:/liquidglass-captures or to any repo.",
 "uncertain": [
  "iOS's own peak rim displacement. I measured +9.17 px at ~10 px depth (trash button left rim, y=2376) and -12.58 px at ~8 px depth (share button right rim, same row), both decaying to zero by ~35 px in. But the share button's LEFT rim on that same row is inconsistent with a simple radial displacement field of that magnitude \u2014 the backdrop's white bar at x96-111 shows as a weak, symmetric bump rather than the displaced hard band the other two rims show. So the field is not purely a radial sample offset (blur that varies with depth and a specular lobe are also in play) and I could not pin the peak value. I quote 9-13 px as the range, which makes the 'fraction of displacement' conversion uncertain by roughly a factor of 2. The absolute px bound of 0.03 does not depend on this at all.",
  "Whether dispersion appears on non-neutral backdrops. No glass element in these four captures overlaps the target's saturated-primaries band (rows 960-1120), so I could not test whether the material adds a fringe when the backdrop itself carries colour. I have no reason to expect it would, but it is untested.",
  "Whether dispersion appears during interaction or animation. These are static screenshots. If Apple modulates the material on touch \u2014 and their own description of it moving in tandem with interaction suggests something does change \u2014 a transient fringe would not appear here.",
  "Dark-mode material. The two 'dark target' captures have byte-identical neutral content underneath the chrome, so although IMG_6645/6646 show zero chroma too, that is not an independent test of a differently-tinted material. Whether the material is even in dark appearance in those frames I did not establish.",
  "The very tight statistical bound. Treating the ~7,400 pixels with gradient >= 15 levels/px as independent gives a bound near 3e-5 px, which is two to three orders below what I quote. I do not report that number, because it assumes the sub-LSB fractional parts are effectively random across a complex shader, and I have no way to check that assumption from a quantised capture. The 0.03 px figure is the one I would defend."
 ],
 "contradicts_us": [
  {
   "ours": "uAberration / GlassStyle.dispersion defaults to 0.018 (fraction of displacement) in D:/Work/AndroidStudioProjects/SideProjects/kmp/liquidglass/liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/GlassStyle.kt:68",
   "measured": "< 0.0004 at our own refractionDepth of 14dp, or < 0.0015 scaled by iOS's own rim displacement. Measurement is consistent with exactly zero.",
   "significance": "This is the headline disagreement. Our default is 12x to 50x too large. Concretely: on the stripe backdrop in these captures our 0.018 puts a peak of 33-35 levels of chroma and a mean of 5-16 levels on the rim, non-neutral on 37,415 of 73,911 rim pixels. iOS puts 0 on 0. That is not a subtle tuning difference \u2014 it is a visible rainbow versus none. The material is either monochromatic in its sampling or splits by an amount no 8-bit capture can resolve. Defaulting dispersion to 0.0f is the change this supports; if the effect is kept as a knob, 0.018 is far outside anything the real material does."
  },
  {
   "ours": "GlassShaderSource.kt:362-365 and GlassStyle.kt:65 both frame dispersion as a real if faint feature \u2014 'Blue carries the higher index, so it lands furthest out', 'Kept faint: this is a fringe'.",
   "measured": "There is no fringe to be faint. Red, green and blue edge positions are identical to 0.00000 px at every rim tested, so there is no channel ordering to get right and no physical direction to honour.",
   "significance": "Low practical weight \u2014 the ordering is physically correct and costs nothing if the amplitude is zero \u2014 but the comments read as though the reference material shows a small rainbow, and it shows none. Worth a wording fix so the next person tuning this does not assume there is a real target to hit."
  }
 ],
 "confirms_us": [
  "The direction of the claim in GlassStyle.kt:65 \u2014 'real UI glass shows almost no prismatic fringe' \u2014 is right, and if anything understated. It is not 'almost no' fringe; it is no fringe, to a bound of 0.03 px of channel separation. Whoever wrote that comment had the correct instinct and then set the constant as though they did not believe it.",
  "The warning in the same comment that 'past a few percent this stops reading as glass and starts reading as a broken colour channel' is confirmed quantitatively: 0.018 is already inside that failure mode on high-frequency backdrops. At our 42px rim displacement it separates red and blue by 1.51 px, which on the 8px stripes in this target is 19% of a full period \u2014 the two channels land on visibly different parts of the pattern.",
  "Our premise that the rim carries a sharp, compressed image of the backdrop rather than a blurred one is confirmed. Genuinely refracted content at the rim reaches 30.4 levels/px, and after dividing out the material's measured 0.157-0.232 transmission that corresponds to a near-unfiltered backdrop edge. The rim tap in the real material is sharp, exactly as GlassShaderSource.kt notes 2 and the rimSharp = (1-e)^2 weighting assume.",
  "Refraction at the rim is real and substantial, so the dispersion null is a null about dispersion specifically and not about the rim doing nothing. The toolbar buttons visibly bend the 8px stripes into radiating arcs, and stripe-edge matching gives a sample offset of 9-13 px near the rim decaying to zero by ~35 px in."
 ]
}
```

---

## Refutation attempt: Which way the rim samples

Agent `ad9f107fe0c74c80c`.

```json
{
 "survives": true,
 "reasoning": "HEADLINE SURVIVES \u2014 the rim samples INWARD. I reproduced it with four methods, three of which they did not use, and I could not break it. But two of their supporting arguments are invalid and one shipped-relevant number (rim band width) is ~25-30% too small.\n\nWHAT I REPRODUCED (all work in .../scratchpad/caps/work2/, scripts lib.py, t1-t9.py, fw.py, fw2.py, fw3.py, fw4.py)\n\nRegistration: identity, exactly. |screenshot - target| over rows 500-960 (the 4/8/16/32/64-px stripe block, the most shift-sensitive region in the frame) gives MAD 0.0042, max 3.0. A 1-px roll of the 4-px stripe band raises MAD to 123.7, so subpixel shift is ruled out. Pairing confirmed: 6644/6647 vs light MAD 0.004, vs dark 67.6.\n\nTarget ground truth re-derived from scratch: rows 2262-2341 are an exact 8-px square wave (matched-filter amplitude 127.50), rows 2342-2421 exact 32-px (127.38), rows 2422-2441 min==max==0 over the FULL 1170-px width in BOTH targets (not just x 420-750), rows 2442+ 17pt text.\n\nMETHOD 1 (mine, no filter, no assumed period, no sign convention). For every screenshot row 2300-2451 I ran zero-mean normalised cross-correlation against every target row 2100-2531 over an icon-free window, and asked which target row best explains it. The icon mask is irrelevant here: |6644-6647|>150 dilated 6 px covers ZERO pixels in rows 2414-2449 over x 420-750, so their bottom-rim window is clean.\n  - rows 2422-2446: best match = row 2342 (the 32-px pattern), NCC +0.985 (6644) / +0.948 (6647). NCC against their own row: nan (flat black) or +0.06..+0.11 (text). The 32-px pattern lives only at rows 2342-2421, inside the element.\n  - rows 2305-2309: NCC vs 32-px = +0.912..+0.972; NCC vs the LOCAL 8-px pattern = -0.008..+0.002. Row 2310 mixed, row 2311 back to 8-px (+0.916).\n  Top rim pulls from below, bottom rim pulls from above. Not a translation, not a blur. Inward.\n\nMETHOD 2 (mine, single-pixel, no fitting). Row 2424, backdrop exactly 0 across the whole frame: the capsule renders a clean 170/214 square wave, transitions at x=415/416, 431/432, 447/448, 463/464 \u2014 phase-locked to the target's 32-px stripes to the pixel.\n\nMETHOD 3 (mine, continuity at a specific edge, 27 px from the rim \u2014 nowhere near the specular line). y=2418, element left edge 355.9. Backdrop: white[352,367], black[368,383], white[384,399], black[400,415]. Observed: black 355-357, white 358-399, black 400-416, white 417-426. Identity holds for x>=400, so delta(384)=0. At x=383 the backdrop is BLACK and the pixel renders WHITE, so its source is white: inward needs delta(383)=+1 (source 384); outward needs delta(383)=-16 (source 367), a 16-px discontinuity against delta(384)=0. Inward. Same argument at y=2421 and y=2412.\n\nMETHOD 4 (mine, forward model, decisive). SDF for capsule (341.5-828.5, 2303.5-2447.5, r=72) + both circles; sample = pixel - outward_normal * m(depth), m = M(1-depth/BW)^p; predict black/white from the target; score against confidently-classified, non-icon pixels at depth>5 in rows 2422-2441 (backdrop provably 0, so 100% of the signal is fetched). n = 10,507 px.\n  M=0 (no rim): 0.4927 (chance).\n  Every NEGATIVE M (outward), -10 to -60, across four (BW,p) settings: 0.478-0.509. At or below chance.\n  Positive M 44-54: 0.985-0.997. Best 0.9970 at M=50, BW=34, p=2.\n  Cross-validated on 16,015 HELD-OUT pixels (rows 2344-2420 at the caps and circles, real striped backdrop, not used in the fit): identity 0.641/0.713, outward 0.656/0.656, inward 0.968/0.952.\n  Direction is not in doubt.\n\nMETHOD 5 (mine, a test they abandoned, on a different element and the other axis). They wrote off the top-chrome pill's bottom rim as saturated. At x=700 it is not: the backdrop steps 255 -> 153 at y=260, the pill transmits ~15% (measured at y=160 across the x=585 split: 184-188 over black, 224-226 over white), so a local sample must show about -15 levels. Rows 250-270 at x=700 read 244,244,244,244,244,243,244,243,244,243,244,244,244,244,245,246,246,247,249,251,255 \u2014 zero step. y=260 is depth ~12 from the pill's bottom edge (~272). Inward sends the source to y~240 (white, no step); outward sends it to y~280 (ramp 153 \u2014 the step would simply move up to y~240 and still be visible). It is visible nowhere. Same at x=400 (+87 step, observed +1) and x=380. Third element, different material, horizontal backdrop edge, same answer.\n\nI ALSO CLOSED THE ONE HOLE THEY LEFT. Their blur number was horizontal only, so an anisotropic vertical blur could in principle have faked the bottom-rim result (vertical stripes are invariant under vertical blur). It cannot: at x=484 the backdrop steps 0->255 at y=2342 and the glass renders it 187,187,189,208,221 over rows 2339-2343 \u2014 a 2-row transition, same as the horizontal 185/206/221 at x=415-417. The material is vertically sharp. In the SAME column, the 255->0 backdrop step at y=2422 produces literally nothing (216,215,215,215,215,215,215,214,214,214,214,214,214 over rows 2416-2428). Only a large inward displacement explains that.\n\nWHAT DOES NOT HOLD UP\n\n(a) Test D is invalid. \"x 343-345 ... the local backdrop is black[336,351] \u2014 the nearest white lies at [384,399], i.e. +40 px inward\" is factually wrong. In the same target row 2375, white[352,367] lies +7 to +24 px inward. Worse, those pixels sit inside the specular rim line, which on this same element reads 249 at the top edge (interior 187) and 219 at the bottom edge (interior 172) over provably black source content, and 212-214 at the left cap in row 2424 where the backdrop is exactly 0 across the entire frame. Three pixels at 210/214/216 with a black level of 184 are not a measurement of anything. \"Two routes agreeing\" should be withdrawn; the magnitude rests on Test B and the model fit alone.\n\n(b) \"The flat black band ends at x=369, depth 27\" describes a feature that does not exist. IMG_6644 y=2375 reads 182,182,181,181,181,181,181,181,181,181,181,181,181,181,182,181,182,182,182,182,182,182,182,182,182,182,183,183,183,183,183,183,183,184,183,183,183,185 across x=346-383 \u2014 a flat 4-level monotone drift matching the material's own gradient (compare the interior black runs at x 400-414 and 432-443, both 184-185). There is no boundary at 369. That \"independent corroboration\" of the 28-px band is not evidence.\n\n(c) Test A is not self-sufficient. \"The single most decisive number in the dataset\" \u2014 the bottom rim over provably-black backdrop \u2014 is on its own equally well explained by a vertical clamp/edge-extend of the backdrop sample at row 2421: sd runs 20.47 (2421) -> 21.85 (2441) in 6644, exactly continuing the pre-existing linear trend of +0.065/row measured over rows 2400-2420, with no discontinuity at all. It becomes decisive only when combined with Test B and the depth-resolved profile. Their framing overstates it.\n\nWHAT I GOT INSTEAD\n\nRim band width. Held-out scan (16,015 px, rows 2344-2420, caps and circles, not used in the fit), best score per fixed BW: BW=22 0.901, 24 0.922, 26 0.926, 28 0.943, 30 0.954, 32 0.954, 34 0.965, 36 0.956, 38 0.971, 40 0.963, 42 0.946, 46 0.924, 50 0.896. Peak at 34-38 px = 11.3-12.7 dp, not 26-30 px = 9.3 dp. Their 26-30 comes from a straight-line fit over depths 10-23 extrapolated to zero; the profile is not linear (best exponent p = 2 to 2.5), so linear extrapolation systematically clips the tail.\n\nPeak displacement. Best held-out family (M=50, BW=38, p=2.5) gives m(1)=46.8, m(5)=35.1, m(10)=23.3, m(20)=7.7, m(28)=1.8. At their quoted depth 1.5-3.5 that is 39-45 px. Their 40-44 is right; only the corroboration is bogus. Their whole profile reproduces within ~4 px at every depth (theirs vs mine: 7 -> 29.6/29.5, 10 -> 21.9/23.3, 12 -> 20.0/19.4, 15 -> 17.0/14.3, 18 -> 14.0/10.0, 20 -> 11.1/7.7, 23 -> 6.1/4.9).\n\nAmplitudes. Their bottom-rim numbers reproduce: I get sd 20.60 (row 2422) -> 21.85 (2441) light, 19.22 -> 17.55 dark, vs their 20.3->21.9 and 18.2->16.1. Top rim rows 2305-2309: sd 15.8-16.2 light, 23.0-23.3 dark, vs their 15.4-15.7 and 22.3-22.4.\n\nGeometry, blur and pairing all reproduce. Minor: \"IMG_6644 and IMG_6645 are bit-identical over rows 140-274 (MAD 0.435)\" \u2014 0.435 is not bit-identical (I reproduce 0.4350; that is encoding noise). The two TARGETS are bit-identical over rows 140-274 and 2302-2450 at full width (max diff 0.0), which I confirm.\n\nPRACTICAL UPSHOT for the shipped shader: sign is wrong (they are right about that), peak depth ~40-47 px = 13-16 dp is close to the shipped 14 dp, but the band should be ~36 px = 12 dp, not their 9.3 dp and not the shipped 26 dp default.",
 "corrections": [
  {
   "quantity": "Rim band width (depth over which displacement is non-zero)",
   "claimed": "~28 px (26-30) = 9.3 dp",
   "your_value": "34-38 px = 11.3-12.7 dp (best 38)",
   "why": "Their 26-30 comes from a linear fit over depths 10-23 extrapolated to a zero crossing, but the profile is not linear (best-fit exponent p = 2 to 2.5), so linear extrapolation clips the tail. Forward-model band-width scan on 16,015 HELD-OUT pixels (rows 2344-2420 at the caps and circles, real striped backdrop, not used in the fit): BW=26 scores 0.926, BW=28 0.943, BW=34 0.965, BW=38 0.971, BW=42 0.946, BW=50 0.896. Their second, 'independent' corroboration ('the flat black band ends at x=369, depth 27') rests on a feature that is not in the pixels."
  },
  {
   "quantity": "IMG_6644 y=2375 x 346-383: claimed boundary at x=369",
   "claimed": "'the flat black band ends at x=369, depth 27'",
   "your_value": "no boundary anywhere in x 346-383",
   "why": "The row reads 182,182,181,181,181,181,181,181,181,181,181,181,181,181,182,181,182,182,182,182,182,182,182,182,182,182,183,183,183,183,183,183,183,184,183,183,183,185 for x=346..383. That is a flat 4-level monotone drift, identical in character to the material's own gradient measured on interior black runs (x 400-414 = 184-185, x 432-443 = 184-185). Nothing happens at x=369."
  },
  {
   "quantity": "Test D: 'the nearest white' to x 343-345 at y=2375",
   "claimed": "white[384,399], i.e. +40 px inward",
   "your_value": "white[352,367], i.e. +7 to +24 px inward",
   "why": "Target row 2375 is a 32-px square wave: white 320-335, black 336-351, WHITE 352-367, black 368-383, white 384-399. They state the [352,367] white run themselves two sentences earlier. So the pixel is consistent with displacements of +8..+24 as well as +40..+56 and discriminates nothing. Compounding this, x 343-345 (depth 1.5-3.5) lies inside the specular rim line: the same element reads 249 at its top edge (interior 187) and 219 at its bottom edge (interior 172) over source content that is provably black, and 212-214 at the left cap in row 2424 where the backdrop is exactly 0 across the whole frame. The 'two routes agreeing' claim should be withdrawn; the magnitude survives on Test B and the model fit only."
  },
  {
   "quantity": "Headline: 'the Photos toolbar's outer 26 px'",
   "claimed": "outer 26 px",
   "your_value": "outer ~34-38 px",
   "why": "26 is not a measured band width, it is just the depth of the deepest row (2422) in the provably-black stretch \u2014 the black backdrop stops at row 2441 and the element bottom is 2447.5, so rows 2422-2441 can only ever probe depths 6-25.5. The band width has to be measured on the caps, where it comes out 34-38 px."
  },
  {
   "quantity": "Contrast transfer (opacity) of the Photos toolbar material",
   "claimed": "13.94% light / 16.18% dark, single numbers",
   "your_value": "14.21% light / 16.85% dark, and NOT constant: 12.8% (row 2350) rising to 16.0% (row 2418) light; 17.7% falling to 15.3% dark",
   "why": "Measured as row std / 127.5 over icon-free windows x 480-543 and x 640-703 (four exact 32-px periods), rows 2350-2419. The mean matches theirs, but a single figure hides a monotone +25% / -14% drift across the element's own height, which is the material's vertical gradient, not noise. Anyone reproducing the material from one number will get the middle of the element right and both ends wrong."
  },
  {
   "quantity": "IMG_6644 vs IMG_6645 over rows 140-274",
   "claimed": "bit-identical (MAD 0.435)",
   "your_value": "MAD 0.4350 \u2014 not bit-identical",
   "why": "Reproduced exactly, but a mean absolute difference of 0.435 is encoding noise, not bit-identity. The underlying point is right and stronger than they state: the two TARGETS are bit-identical over rows 140-274 and rows 2302-2450 at FULL width (max diff 0.0), which I verified, so the two captures should agree there up to codec noise."
  }
 ],
 "confounds_missed": [
  "The specular rim line, which invalidates their Test D. Over source content that is provably black, this element's own rim reads +62 at the top edge (row 2304 = 249 vs interior 187 at x=500), +47 at the bottom edge (row 2447 = 219 vs interior 172), and 212-214 at the left cap in rows 2422-2441 where the backdrop is exactly 0 across all 1170 columns. Their 'direct inspection, no fitting at all' pixels (x 343-345, depth 1.5-3.5, values 210/214/216 against a black level of 184) are inside that line and carry no refraction information.",
  "They never measured the material's VERTICAL sharpness \u2014 only a horizontal edge at y=2375 x=415/416 and a frequency ratio that is also horizontal. Vertical stripes are invariant under vertical blur, so an anisotropic vertical blur was the one mechanism that could have manufactured their entire bottom-rim result out of nothing. It is excluded, but they did not exclude it. I did: at x=484 the backdrop steps 0->255 at y=2342 and the glass renders 187,187,189,208,221 over rows 2339-2343 (2-row transition, same as horizontal), while in the SAME column the 255->0 step at y=2422 produces zero response (216,215,215,215,215,215,215,214,214,214,214,214,214 over rows 2416-2428).",
  "Test A alone is not decisive, contrary to 'the single most decisive number in the dataset'. A vertical clamp / edge-extend of the backdrop sample at row 2421 predicts rows 2422-2447 identically. The amplitude they cite as proof (20.3 -> 21.9) simply continues the linear trend already present at +0.065/row over rows 2400-2420 with no discontinuity whatsoever at 2421/2422, so it does not distinguish the two. It is Test B plus the depth-resolved fit that make the case.",
  "They abandoned a working experiment. They wrote off the top-chrome pill's bottom rim as 'inconclusive because the specular rim line saturates the white side at 255 (rows 269-272)'. At x=700 rows 260-268 read 244,244,244,244,245,246,246,247,249 \u2014 nowhere near saturation. The backdrop steps 255 -> 153 at y=260 and the pill transmits ~15% (184-188 over black vs 224-226 over white at y=160 across the x=585 split), so a local sample must show about -15 levels there. It shows zero. y=260 sits at depth ~12 from the pill's bottom edge; inward puts the source at y~240 (white, no step), outward puts it at y~280 (ramp 153, so the step merely relocates to y~240 and stays visible). It is visible nowhere in rows 240-270. That is an independent inward confirmation on a different element, a different material, and the vertical axis rather than the horizontal one.",
  "They never ran a direct sign test on a large pixel population. Their sign rests on a phase-unwrapping convention applied to a least-squares fit, which is exactly the kind of thing that flips silently. A forward model scoring predicted-vs-observed black/white over 10,507 fitted and 16,015 held-out pixels settles it without any convention: outward scores 0.478-0.509 (chance is 0.5) at every magnitude from -10 to -60 px, inward scores 0.985-0.997."
 ]
}
```

---

## Refutation attempt: Rim geometry and displacement

Agent `a62598ed0955477ed`.

```json
{
 "survives": false,
 "reasoning": "MECHANISM SURVIVES; THREE OF THE HEADLINE'S NUMBERS DO NOT.\n\nWhat I reproduced independently (different methods, my own geometry):\n- INWARD displacement. Confirmed by two model-free routes the analyst did not use. (a) The true horizontal centreline of the left button is row 2375 (see geometry below). Along it, IMG_6644 reads bright (204->217) from x=193 to x=219 with 50% crossings at x=191.9 and x=220.1, while the backdrop's white 32px stripe there is only x=192..207 (edges 191.5 / 207.5). A 27px-wide output band from a 16px source band, with the trailing edge 12.6px outboard of where identity puts it, requires the sample point to move toward the centre. (b) At (128, 2330) the observed value is 187 = the full \"black\" level, although x=128 is WHITE in both the 8px band (rows 2262-2341) and the 32px band (rows 2342-2421). Identity and outward sampling both predict white; inward sampling lands on (132.3, 2336.9), a black 8px stripe. Their row-2430 argument is also sound.\n- The ~35px band. My D crosses -0.5px at r = 38.8 => onset depth 34.5 +/- 2 px (11.5 dp) = 0.47R. Matches their 35 +/- 2.\n- The fold. r_s peaks at 44.9 +/- 0.5 px at r = 51-53, i.e. depth 21 +/- 2 px (7.0 dp). Matches their \"fold at depth ~21px\".\n- \"Nothing from outside the footprint appears inside.\" max r_s = 44.9 +/- 0.5 px = 0.61 R (they said 44.2 = 0.59R). Confirmed. Output edge widths are ~2px (row 2375: 185/204/219 at x=191/192/193), so the kernel is near-delta and the caveat about blur widening the true support is small.\n- The middle of their D table. Over r = 36..64 my four-fit mean D agrees with theirs to <=0.8 px at every ring.\n\nMETHOD (different from theirs). Glyph mask from the two material variants rather than a value threshold: the SF Symbol is dark in IMG_6644 and bright in IMG_6647, so glyph = (A<163)|(C>148), dilated 2px. Estimator: for each output ring r (2880 points, bilinear) I removed angular harmonics 0-3 and then scored each candidate r_s by the difference of the observed ring means over the BINARY target template sampled on the ring of radius r_s (a matched filter on a binary template, class-mean contrast), not Pearson on a detrended continuous signal. Score curves are clean and unimodal, no aliasing despite the 32px-periodic backdrop; at r=60 identity (r_s=60) scores -5 while the peak at r_s=41 scores +21. Four independent fits: left and right buttons (which have DIFFERENT 32px stripe phases: cx mod 32 = 28.25 vs 21.25) x two material variants. Cross-checked against direct sub-pixel edge crossings on the centreline of both buttons.\n\nWHERE IT FAILS.\n1. Geometry. R = 73.26 +/- 0.05 px, centre y = 2374.9 (not 74.35 / 2377.3). Diameter 146.5 px = 48.8 dp, not 148.8 px / 49.6 dp, so \"a 50pt control\" is off by ~1.2pt. Cause identified exactly: there is a faint drop shadow below the button, invisible over black backdrop but visible over the target's 17pt white text at rows 2448-2465 (e.g. x=137, rows 2448-2451: shot 252 vs target 255; x=141, rows 2457-2460: 252 vs 255). Their |shot-target|>2 per-column criterion picks 52 such points up as \"boundary\". Including them: cx=156.01, cy=2377.65, R=74.497, residual std 3.55 - which is their reported (156.0, 2377.3, 74.35, ~3.3). Dropping them: (155.85, 2374.97, 73.24), residual std 0.79. This is exactly the \"boundary that is actually a shadow\" failure mode. Independent confirmation: the row chords plateau at half-width 73.00 for y = 2366..2385, centred on 2375.0, and reach 72.0 at both y=2360 and y=2390.\n2. \"2.7x-compressed outer ring.\" Not reproduced. My d(r_s)/dr in the folded band: -0.65 (r=57), -0.69 (59), -1.24 (61), -1.04 (63), -1.74 (65), -1.47 (67). Compression 1.0-1.7x, typical ~1.2x - roughly half their figure. Their 2.73 comes entirely from their two innermost rings, which I cannot reproduce.\n3. Peak |D|. They claim \">= 41.9 px = 14.0 dp\". I get 36.5 +/- 1.5 px (12.2 dp) at r=68; their r_s(66)=29.65 and r_s(68)=26.13 are ~4px too small (mine: 33.4 and 30.5). Model-free bound: on row 2375 the left button reads a flat 182 (the full black level, identical to x=89..98) from x=88 to x=98; a black source at x=88 requires r_s in [28.75, 44.75), so r_s(68.25) >= 28.75 and |D(68.25)| <= 39.5. Their 41.87 violates it. Their number is also geometry-driven: re-running THEIR Pearson statistic under their centre gives r_s(66)=28.1 with rho 0.73; under my centre it gives 33.6 with rho 0.88. Peak correlation is higher under my centre at every ring r>=44 (0.85-0.96 vs 0.73-0.95), which is independent evidence that their centre is wrong.\n4. \"Magnifies up to 3.8x.\" Not a well-posed number. r_s is flat at 44.83-44.90 over r = 50..55, so 1/|d r_s/dr| diverges at the fold; \"3.8x\" is an artifact of their 2px grid. Away from the turning point I get max ~2.9x on the rising limb, and unbounded at the fold.\n5. Reproducibility claim is void. calibration-target-light.png and calibration-target-dark.png are IDENTICAL for rows 2132-2532 (they differ only at rows 390-464, 697-959, 1581-1615, 1781-2171, 2500-2526). Consequently IMG_6644 and IMG_6645 are pixel-identical over the toolbar: mean |diff| = 0.047 and only 0.07% of pixels differ by more than 1 over rows 2290-2465, x 60-1140. So \"2 buttons x 2 captures agree to 0.1-0.7px\" is 2 buttons x 1 image. Same for 6647/6646.\n6. \"IMG_6647/6646 have their chrome 0.6px lower\" is wrong. They are the DARK-appearance glass, not a shifted copy: mean |6644-6647| = 53 levels over the toolbar, interiors 85/131 vs 185/220. Fitting the button from 6647 alone gives cy=2375.00, R=73.257 vs 6644's 2374.84 / 73.254 - identical to 0.16px, no offset.\n7. \"The two capsules over the grey ramp are effectively OPAQUE\" is wrong in substance. Differencing IMG_6644 minus IMG_6645 over rows 347-406: outside the capsules the difference is exactly 0 (targets identical over the ramp), but inside capsule 1 (x 875-993) it is a uniform +26 and inside capsule 2 (x 1025-1118) +23..+25. The only nearby content that differs between the targets is the label band at rows 390-464 (255 vs 0). So those elements pass ~10% of a backdrop feature: not opaque, just essentially DC-only. Their regression measured the transmitted GRADIENT and concluded opacity from it. (Also: the grey ramp ends at row ~390, not 430, so rows 391-406 of those capsules sit over a label band, not the ramp.)\n8. Their glyph mask rationale is backwards. Glyph-antialias pixels above 110 exist and are not masked (row 2375 of IMG_6644: x=132 -> 119, x=158 -> 134, x=178 -> 154, x=184 -> 123). And the glyph is central, so masking is needed at SMALL radii: my valid ring fraction is 1.00 only for 44 <= r <= 67, and 0.36-0.63 for r <= 36. Their \"below r=63 nothing is masked at all\" is the wrong way round. It does not affect the rings they tabulate (r >= 36), but the justification is not sound.\n\nNot a confound here: the toolbar backdrop is pure 0/255 greyscale and the glass output is exactly neutral (R=G=B: 182,182,182 / 221,221,221 in 6644; 82,82,82 / 128,128,128 in 6647), so colour management does not touch this measurement. I also verified the target band structure independently (hard split at x=584.5 rows 2172-2261; 8px wave rows 2262-2341; 32px wave rows 2342-2421; solid black 2422-2441).\n\nVerdict: I mark survives=false because the headline is a numeric claim and the button size, the 3.8x, and the 2.7x all fail, and the geometry those numbers are expressed against is wrong by 1.1px in R and 2.4px in cy. The physical picture it describes - inward displacement, a ~35px rim band, a fold at ~21px depth, and no content from outside the footprint - does survive and I reproduce it.\n\nWork in .../scratchpad/caps/work3/ (lib.py, geom.py, ring.py, ring2.py). Nothing written to D:/liquidglass-captures or the repo.",
 "corrections": [
  {
   "quantity": "Button radius / diameter (left and right bottom-toolbar buttons, IMG_6644)",
   "claimed": "R = 74.35-74.39 px (+/- 0.3), diameter 148.8 px = 49.6 dp, 'a ~50pt control'",
   "your_value": "R = 73.26 +/- 0.05 px, diameter 146.5 px = 48.8 dp. Left button widest chord at row 2375 runs x = 83.5..229.5 (x=83 is exactly target 0, x=84 is 187; x=230 is exactly target 255), i.e. 146.0 px. Fit over rows 2306-2444: left cy=2374.93 R=73.286; right cy=2374.86 R=73.263.",
   "why": "Their per-column boundary criterion (|shot-target|>2) picked up the button's faint drop shadow 5-17 px BELOW the button, which is only visible where the target has white text (rows 2448-2465; e.g. x=137 rows 2448-2451 read 252 vs target 255). Adding those 52 points to my own point set reproduces their exact answer: cx=156.01, cy=2377.65, R=74.497, residual std 3.55 (they reported ~3.3). Removing them: (155.85, 2374.97, 73.240), residual std 0.79. Their own residual std of 3.3px was the tell."
  },
  {
   "quantity": "Circle centre y",
   "claimed": "cy = 2377.3 (left), 2377.5 (right)",
   "your_value": "cy = 2374.9 +/- 0.1 for both buttons and both material variants. The row-chord half-width plateaus at 73.00 for y = 2366..2385 (centre 2375.0) and is 72.0 at both y=2360 and y=2390.",
   "why": "Same shadow contamination. Consequence: re-running their own Pearson ring statistic under their centre versus mine gives systematically LOWER peak correlation under theirs at every ring r>=44 (e.g. r=66: rho 0.73 at rs=28.1 vs rho 0.88 at rs=33.6; r=64: 0.76 vs 0.85; r=60: 0.84 vs 0.89)."
  },
  {
   "quantity": "Peak displacement |D| near the rim",
   "claimed": "|D| >= 41.9 px = 14.0 dp at depth 6.4 px (their r = 68), 'still climbing steeply'",
   "your_value": "|D| = 36.5 +/- 1.5 px = 12.2 dp at r = 68 (r_s = 30.5 +/- 1.5). Their r_s(66)=29.65 and r_s(68)=26.13; I get 33.4 and 30.5 from four independent fits, with score-curve peaks 22-33% above the score at their values.",
   "why": "Model-free bound: on row 2375 (the true centreline) the left button reads a flat 182 - the full black level - across x = 88..98. Black source there requires r_s in [28.75, 44.75), so r_s(68.25) >= 28.75 and |D| <= 39.5. Their 41.87 violates it. Rings at r >= 69.5 are dominated by the rim specular (the angular signal at r=69.5 and 71 loses its stripe alternation and brightens broadly over the top half), so nothing deeper than r=68 is measurable at all."
  },
  {
   "quantity": "Local compression in the folded outer band (|d r_s/dr|)",
   "claimed": "2.7x compression at depth 8 px; headline 'a mirrored, 2.7x-compressed outer ring'",
   "your_value": "1.0-1.7x, typical ~1.2x. Central differences of my four-fit mean r_s: -0.65 (r=57), -0.69 (59), -1.24 (61), -1.04 (63), -1.74 (65), -1.47 (67).",
   "why": "Their 2.73 is the slope between their two innermost rings (r=64 -> 68), the two points I cannot reproduce and that violate the centreline black-level bound. The outer band is close to a 1:1 mirror, not a 2.7x squeeze."
  },
  {
   "quantity": "Peak local magnification on the rising limb",
   "claimed": "stretch rising to 3.8x at depth 24 px (r = 50)",
   "your_value": "Not a well-posed number. r_s is flat at 44.83-44.90 over r = 50..55, so 1/|d r_s/dr| formally diverges at the fold. At r=50 I get ~2.0-2.5x; the largest well-conditioned value away from the turning point is ~2.9x (r=45).",
   "why": "It is a finite difference of a function at its turning point, evaluated on a 2px grid. Any grid spacing gives a different answer; no error bar was quoted."
  },
  {
   "quantity": "Maximum sampled radius",
   "claimed": "max r_s = 44.2 px = 0.59 R",
   "your_value": "max r_s = 44.9 +/- 0.5 px = 0.61 R (per-fit peaks 44.20, 44.80, 45.10, 45.50).",
   "why": "Minor, and the conclusion (nothing from outside the footprint) holds. Independent corroboration on the left centreline: a partial-amplitude bright sliver peaking at 203 (vs the full white level 221) at x = 101..106 is r_s just barely crossing the source edge at x_s = 111.5, i.e. r_s = 44.75, at r ~ 52.8."
  },
  {
   "quantity": "Independent reproduction across captures",
   "claimed": "'Two buttons x two light captures agree to 0.1-0.7px'; 'IMG_6645/6646 (dark target) reproduce the light-target numbers'; 'IMG_6647/6646 have their chrome 0.6px lower'",
   "your_value": "IMG_6644 and IMG_6645 are pixel-identical over the toolbar (mean |diff| 0.047, 0.07% of pixels differ by >1, rows 2290-2465 x 60-1140). Same for 6647/6646. And 6647 is not offset from 6644: fitting the left button from 6647 alone gives cy=2375.00, R=73.257 against 6644's cy=2374.84, R=73.254.",
   "why": "calibration-target-light.png and calibration-target-dark.png are byte-identical for rows 2132-2532; they differ only at rows 390-464, 697-959, 1581-1615, 1781-2171 and 2500-2526. So the four captures give only TWO distinct toolbar images, and the pair difference is light-mode versus dark-mode glass (interiors 185/220 versus 85/131, mean |6644-6647| = 53), not a sub-pixel shift."
  },
  {
   "quantity": "Transmission of the two capsules over the grey ramp (rows 347-406)",
   "claimed": "'effectively OPAQUE', backdrop transmission -0.8% and +2.8%",
   "your_value": "Not opaque: they transmit about 10% of a non-local backdrop feature. IMG_6644 minus IMG_6645 over rows 349-388 is exactly 0 outside the capsules and a uniform +26 (x 875-993) / +23 (x 1025-1118) inside them, even though the ramp backdrop under them is identical between the two targets.",
   "why": "The only nearby content that differs between the two targets is the label band at rows 390-464 (255 in light, 0 in dark). Their regression measured only the transmitted GRADIENT; the interiors are flat (232 and 239) because the kernel is essentially DC, not because the element is opaque. Also the ramp ends at row ~390, not 430, so rows 391-406 of those capsules sit over the label band, not the ramp."
  }
 ],
 "confounds_missed": [
  "A drop shadow below the toolbar buttons that is invisible over the black backdrop but darkens the target's white text by ~3 levels for 5-17 px below the element (rows 2448-2465). Their |shot-target|>2 boundary criterion counted it as element edge, inflating R by 1.1px and pushing cy down 2.7px.",
  "calibration-target-light.png and calibration-target-dark.png are identical over the whole tab-bar zone (rows 2132-2532), so IMG_6644/6645 and IMG_6647/6646 are duplicate images there. Every 'reproduces across captures' claim about the bottom toolbar rests on two images, not four.",
  "The real difference between {6644,6645} and {6647,6646} is light-mode versus dark-mode glass, not a 0.6px registration offset. That pair is the single most useful instrument in the dataset (same backdrop, same geometry, two different material transfers, opposite glyph polarity) and it was mischaracterised and then discarded.",
  "The glyph mask was set at <=110 on the claim that glass output lives in 165-225. Glyph antialias pixels at 111-160 are common (row 2375 of IMG_6644: 119 at x=132, 134 at x=158, 154 at x=178, 123 at x=184) and are not masked. The companion claim that 'below r=63 nothing is masked at all' is inverted - the glyph is central, so masking bites at r<=42, and their rings are clean only because they start at r=36.",
  "Rings at r >= 69 are dominated by the rim specular, not by refraction: the angular signal at r=69.5 and r=71 loses its stripe alternation and brightens broadly over the top half of the button. Both buttons show a bright 2-4px band just inside the edge on BOTH left and right, which is lighting, not source content. Any extrapolation of the profile to depth 0 (their W=49.8px, A=71.0px fit) is unconstrained by data.",
  "1/|d r_s/dr| is being reported as a physical magnification at and near a turning point of r_s, where it is unbounded and grid-dependent. Both the 3.8x and the 2.7x are finite differences of a flat/nearly-flat function on a 2px grid, quoted without error bars.",
  "The horizontal centreline of the button is a clean, fully 1-D instrument for r_s(r) - along it the map is purely x -> x_s and the backdrop is a pure 32px square wave whose edges are known exactly - and it was never used. It is what makes the geometry error visible: their centreline would be row 2377.3, the true one is row 2375.0."
 ]
}
```

---

## Refutation attempt: Interior blur radius and rim sharpness

Agent `a61ef8aff3715b7ab`.

```json
{
 "survives": true,
 "reasoning": "The architectural headline survives and is in fact better supported than they made it. The blur-radius number in it does not.\n\nWHAT I REPRODUCED INDEPENDENTLY\n\n1. Sharp path is essentially unblurred. Model-free: folded every 32px stripe period over the toolbar capsule's straight run (IMG_6644/6646, rows 2344-2419, x 455-548 and 620-712 \u2014 the two icon-free windows; icons occupy x 388-451, 552-616, 716-780) into a 32-bin phase profile and differenced it. LSF = 3 taps, normalised (0.395, 0.562, 0.038) light / (0.555, 0.398, 0.047) dark, second-moment sigma 0.575 and 0.586 px. That is exactly the floor for one bilinear fetch plus a 1px capture aperture (sqrt(0.5^2 + 1/12) = 0.577), so the material's own extra blur on this path is consistent with ZERO. Their 0.5-0.8 px is right but its top half is not reachable by the data.\n\n2. The frequency-ratio test. I built a different estimator (robust regression of the capture row on the target row, alpha = transmitted fraction, iterative 3-sigma rejection) and closed the one hole in theirs: the 8px and 32px bands are at different rows by construction (2262-2341 vs 2342-2421), so the ratio is contaminated by the vertical gradient. Extrapolating both bands linearly to the common row 2341.5 gives 0.978-0.994 (light) / 0.990-0.992 (dark), depending on extrapolation window. Forward-modelling my own estimator on synthetically blurred target rows maps that to sigma 0.40-0.59 px. Consistent with them.\n\n3. Backdrop is pixel-exact. Rows 2262-2301 x 100-1070: MAD 0.00010, max 1. Rows 2342-2421 x 850-932: MAD 0.00000. Uncovered reference is exactly 255.00/0.00.\n\n4. NO TAPER \u2014 confirmed, by two methods stronger than theirs.\n   (a) Toolbar: within the 32px band the pill's straight run folds depth about row 2375.5, so depth and row are not collinear. Regressing alpha on [1, row, depth] over rows 2343-2421 gives depth coefficients of -0.000115/px (light) and +0.000193/px (dark) \u2014 OPPOSITE SIGNS, i.e. -3.5% and +5.2% over 45 px of depth, consistent with zero. Equal-depth row pairs about the centre differ by up to +20% (light) and -10% (dark), again opposite signs, which proves the variation is an appearance-dependent vertical gradient, not depth. Their argument for this was one pair of rows; the sign reversal between the two chrome appearances settles it.\n   (b) Top pill with the gradient cancelled: the back button (x 50-165) sits over uniform BLACK and the \"more\" button (x 995-1120) over uniform WHITE, over the identical rows 141-272. So (more - back) at row r is the total transmission at row r, and jump(r)/(more(r) - back(r)) is a sharp fraction with the vertical gradient divided out exactly. Result, light: 0.293, 0.289, 0.282, 0.281, 0.281, 0.280 at rows 147-166 and 0.284, 0.286, 0.287, 0.288, 0.291, 0.300, 0.300 at rows 214-262. Dark: 0.333-0.349 over the same rows. Flat from depth 6.5 to 58.5 px. Their \"flat\" holds, and their range (27.6-33.2%) is wider than the data warrants.\n\nWHAT BREAKS\n\n5. sigma_wide is not 38. Their fit gives 38 only because they let a free linear-in-x nuisance term run. That term is excluded by their own data: the pill's left plateau (x 390-435, rows 154-166) has slope +0.0032 code/px with residual sd 0.20, and the material's real intra-element shading, read off the back/more buttons over uniform backdrop, is a symmetric centre-bright bump of ~4-5 codes, not a ramp. Their c1 = 0.042 (light) / 0.060 (dark) code/px is 2-14x anything present. Pinning the asymptotes to the pill's own measured plateaus and dropping the linear term gives sigma 44.0-48.9 (light) and 41.7-46.1 (dark) across fit windows of \u00b1110 to \u00b1175 px and two row bands; adding a symmetric quadratic nuisance changes it by <0.1. Call it 46\u00b13 px light, 44\u00b12 px dark = 15.3 / 14.7 dp, not 12.7 / 12.0 dp.\n   The tell is that with the linear term the fitted swing misses the independently measured swing every time (103.6 fitted vs 115.6 measured, light; 126.6 vs 140.0, dark), and without it the fit lands within 1-4%. Model-free symmetrised tail T(d) = [W(-d) + 1-W(+d)]/2 (which cancels the centre bump to first order) reads 0.199, 0.105, 0.057, 0.027 at d = 40, 60, 80, 100 px; Phi(-d/38) predicts 0.146, 0.057, 0.018, 0.004 (off by 1.4x, 1.8x, 3.2x, 6.2x), Phi(-d/47) predicts 0.197, 0.101, 0.044, 0.017. Beyond ~120 px there is a further ~1% excess over any Gaussian, but that is the size of element-to-element tint differences, so I would not claim it.\n\n6. The transmission numbers are all ~12-18% low, for the same reason. They report \"total transmission a_sharp+a_wide = 0.395, matching the directly measured DC swing 0.447\". It does not match \u2014 it is 12% short, and they absorbed the difference into the nuisance term without checking. The true asymptotes are directly readable: back button 132.7 / more button 251.1 (light, rows 154-166) and 32.9 / 182.7 (dark), giving a_tot = 0.465 and 0.588. The pill's own plateaus give 0.456 and 0.579. The two agree to 2 codes, which also proves the pill's plateaus are real asymptotes.\n\n7. Their claim 7 (wide component detected in the toolbar DC) is invalid as run. Rows 2158-2246, x 62-629 of every capture is the Photos thumbnail scrubber drawn over the target \u2014 a filmstrip of photos, MAD 78 vs the target, identical between the two chrome appearances (MAD 2.8), so definitely app content and not the target. That block sits 58-146 px above the toolbar's top rim, squarely inside the reach of a 40-50 px kernel, and they regressed the toolbar's DC on the Gaussian-blurred TARGET over exactly it. Their whole signal is 2-4 code values; the backdrop error contributes ~0.5-1.5 of them, with the right sign to explain why they observed +1.8 where they predicted +2.8.\n\n8. Capture grouping is backwards. They wrote \"DEPARTS FROM THE BRIEF... they group as {6644, 6645} and {6646, 6647}... the split is the app's own LIGHT vs DARK chrome, not the target.\" Wrong: over rows 440-720 IMG_6644 matches calibration-target-light with MAD 0.004 and the dark target with 42.1; 6645 and 6646 are the reverse; 6647 matches light. The brief was right. They diagnosed it in rows 2300-2450, a band where the two target PNGs happen to be byte-identical \u2014 the classic wrong-region error. The dataset is a 2x2 (target x appearance), not two conditions, and they threw away half of it.\n\n9. Direction of the residual trend is backwards. They report \"slightly UPWARD with depth, marginally MORE sharp detail in the middle.\" With the vertical gradient properly divided out, both chromes go the other way: 0.300 at depth 10.5 vs 0.284 at 58.5 (light), 0.346 vs 0.340 (dark). Both effects are tiny; the point is that the sign they quote is an artefact of not controlling the gradient.\n\nBOTTOM LINE\nThe design conclusion \u2014 one fixed mixture at every depth, no rim-sharp/interior-soft taper \u2014 survives adversarial checking and is now on firmer ground than they left it. So does \"essentially unblurred sharp path\". The mixture is ~29% sharp / ~71% wide (light) and ~34/66 (dark), close to their 30/70. But the wide radius they would have you implement is 20-25% too small, and the absolute transmission is 15% too low, both traceable to one unjustified nuisance parameter. Fix sigma_wide to ~46 px (15.3 dp) and total transmission to ~0.46 (light) / ~0.58 (dark) before shipping anything from this.\n\nAll work in C:/Users/shaya/AppData/Local/Temp/claude/D--Work-AndroidStudioProjects-SideProjects-kmp-Vitals/d83608da-a0a9-446c-9f1b-ec02e5b88c50/scratchpad/caps/work_ref2/ (c.py, fit.py, amp.py, pill.py).",
 "corrections": [
  {
   "quantity": "sigma_wide, the wide transmission path's Gaussian radius (top 'Today' pill, horizontal profile across the target's hard split at x=584.5)",
   "claimed": "38 +/- 5 px light (12.7 dp), 36 +/- 5 px dark (12.0 dp)",
   "your_value": "46 +/- 3 px light (15.3 dp), 44 +/- 2 px dark (14.7 dp). IMG_6644/IMG_6646, rows 146-166 and 214-226, fit windows x 584.5 +/- 110/145/175. Six configurations: light 44.0, 45.7, 46.7, 44.9, 47.3, 48.9; dark 41.7, 43.2, 44.1, 42.9, 44.8, 46.1. Adding a symmetric quadratic nuisance changes each by <0.1.",
   "why": "Their 38 is produced by a free linear-in-x nuisance term (c1 = 0.042 code/px light, 0.060 dark) that absorbs the wide kernel's tails. That gradient does not exist in the material: the pill's own left plateau (x 390-435, rows 154-166) has slope +0.0032 code/px with residual sd 0.20, and the back/more buttons over uniform backdrop show a symmetric centre-bright bump of 4-5 codes, not a ramp. Re-running the identical fit with the asymptotes pinned to the pill's measured plateaus and no linear term moves sigma to 44-49 (light) / 42-46 (dark); re-inserting the linear term drags it straight back to 31.5-39.5, reproducing their number. Model-free check: symmetrised tail T(d) = [W(-d)+1-W(+d)]/2 reads 0.199 / 0.105 / 0.057 / 0.027 at d = 40 / 60 / 80 / 100 px, versus Phi(-d/38) = 0.146 / 0.057 / 0.018 / 0.004 (off by 1.4x to 6.2x) and Phi(-d/47) = 0.197 / 0.101 / 0.044 / 0.017."
  },
  {
   "quantity": "Total backdrop transmission a_sharp + a_wide (top chrome)",
   "claimed": "0.395 light, 0.519 dark; and 'matching the directly measured DC swing (249-135)/255 = 0.447'",
   "your_value": "0.465 light, 0.588 dark (back button 132.70 over uniform black vs more button 251.10 over uniform white, IMG_6644 rows 154-166, x 75-145 and 1030-1090; dark 32.91 vs 182.70). The pill's own plateaus give 0.456 and 0.579 (x 390-435 and 725-770, residual sd 0.20 and 0.09), agreeing to 2 code values.",
   "why": "0.395 does not match 0.447 - it is 12% short, and their own note says so before dismissing it. The deficit is exactly what the spurious linear term removes (0.042 code/px x 290 px window = 12.2 codes of a 116-code swing). The back and more buttons are the clean control they never used: same rows, same material, uniform black and uniform white backdrop 445 px and 410 px from the split, so no kernel of any plausible width mixes them."
  },
  {
   "quantity": "a_sharp, the unblurred fraction of the backdrop",
   "claimed": "0.111 light, 0.169 dark",
   "your_value": "0.131 light, 0.198 dark. Jump at x=585 measured by fitting lines to x 548-582 and x 588-622 (3-sigma glyph rejection) and evaluating both at 585: 33.40 and 50.38 code values, IMG_6644/6646 rows 154-166.",
   "why": "Same cause - the linear nuisance term scales both amplitudes down. Their ratio a_sharp/(a_sharp+a_wide) survives (0.111/0.395 = 0.281 vs my 0.131/0.465 = 0.282), so the '30% sharp' headline is fine; the absolute numbers are not."
  },
  {
   "quantity": "a_wide, the fraction going through the wide path",
   "claimed": "0.284 light, 0.350 dark",
   "your_value": "0.325 light (0.456 - 0.131 using the pill's own plateaus; 0.334 using the buttons), 0.381 dark",
   "why": "Follows from the corrected total and a_sharp."
  },
  {
   "quantity": "Sharp-path sigma",
   "claimed": "0.5-0.8 px (0.17-0.27 dp)",
   "your_value": "<= 0.58 px, and consistent with zero material blur. Folded 32px-period LSF second moment 0.575 px (light) / 0.586 px (dark) over IMG_6644/6646 rows 2344-2419, x 455-548 and 620-712; the resample-plus-aperture floor is sqrt(0.5^2 + 1/12) = 0.577. The 8px/32px MTF ratio extrapolated to the common row 2341.5 is 0.978-0.994 (light) / 0.990-0.992 (dark), mapping to 0.40-0.59 px through a forward model of my own estimator.",
   "why": "Not wrong so much as unreachable at the top of the stated range. Their own dark-chrome per-row fit going to 1.05 px cannot be right: the measured LSF second moment is already at the instrument floor, so any value above ~0.6 px is fitting the resampling kernel, not the material."
  },
  {
   "quantity": "Sharp fraction vs depth, top pill",
   "claimed": "27.6-33.2% light, 32.3-37.1% dark; 'the trend is slightly UPWARD with depth, i.e. marginally MORE sharp detail in the middle than at the rim'",
   "your_value": "28.0-30.4% light, 33.3-34.9% dark, and the residual trend runs the other way: 0.300 at depth 10.5 vs 0.284 at depth 58.5 (light), 0.346 vs 0.340 (dark). IMG_6644/6646, rows 147-166 and 214-262, jump normalised by (more_button(r) - back_button(r)) at the same row.",
   "why": "Their depth axis at the pill is perfectly confounded with row: depth 3.5-28.5 comes only from rows 144-169 and depth 45.5-59.5 only from rows 213-226, and they had already documented an 18% row gradient at the toolbar without carrying it over. Normalising each row's jump by that same row's back/more difference cancels the vertical gradient exactly, because those two buttons span rows 141-272 over uniform black and uniform white. The flatness conclusion is unchanged and tightened; the quoted direction is an artefact."
  },
  {
   "quantity": "Bottom toolbar element extent / radius",
   "claimed": "capsule top,bottom = 2303.5, 2449.5, r = 73; circles 146 px diameter",
   "your_value": "2303.5 to 2447.5, r = 72, centre row 2375.5. Coverage on the straight run (x 470-535, |capture - target| > 6) is rows 2304-2447 inclusive in both IMG_6644 and IMG_6646; row 2448 is bare backdrop.",
   "why": "2 px too tall, probably from including the outer rim/shadow in the coverage mask. Shifts every depth in their tables by up to 1 px. Immaterial to the conclusions but it means the quoted 48.7 dp diameter is 48.0 dp."
  }
 ],
 "confounds_missed": [
  "CAPTURE GROUPING IS BACKWARDS, diagnosed in a region where the two targets are byte-identical. They wrote that the four captures group {6644,6645} vs {6646,6647} and that 'the split between capture groups is the app's own LIGHT vs DARK chrome appearance, not the target', calling the brief wrong. Over rows 440-720 (vertical stripes, no glass anywhere) IMG_6644 matches calibration-target-light with MAD 0.004 and the dark target with 42.106; 6647 the same; 6645 and 6646 are the reverse. The brief was right. Their grouping test used rows 2300-2450, a band where the two target PNGs are byte-identical, so it could not possibly detect the target difference. Their supporting statement that the targets 'differ only by ~1.85 mean in the tab-bar zone' is also wrong: global MAD between the two targets is 60.7, with differing runs at rows 390-464, 697-959, 1581-1615, 1781-2171 and 2500-2526. Consequence for their numbers: none, because rows 0-389 and 2172-2499 (the regions they actually measured in) are identical between targets - but they concluded 'effectively two independent conditions, not four' and discarded half a 2x2 design.",
  "THE STRONGEST INSTRUMENT IN THE DATASET WAS NEVER USED, as a direct result of that error. IMG_6644 and IMG_6645 share the same chrome appearance and differ only in the target, and the two targets are identical for rows 0-389 and then differ by a full-width 255-amplitude band at rows 390-463. The difference image D = 6644 - 6645 is therefore a pure impulse-response measurement: tint, vertical shading, and every opaque icon and glyph cancel exactly, and D is provably zero outside glass. It reads 0.000 everywhere in the bare backdrop above row 390 and 0.000 inside the top pill (rows 141-272, 118 px above the band), bounding sigma_wide there; inside the two small round buttons (rows 348-405, x 870-996 and 1021-1118) it reads a flat 24.9 +/- 0.5 from row 352 to row 389 and steps cleanly from 0 to 25 within 2 px at the button edge. A free measurement of the material's linear response with zero modelling, sitting unused.",
  "THE PHOTOS THUMBNAIL SCRUBBER INVALIDATES THEIR CLAIM 7. Rows 2158-2246, x 62-629 of every capture is the Photos filmstrip drawn over the target - a row of photo thumbnails, MAD 78.4 against the target and MAD 2.80 between the two chrome appearances, so unambiguously app content. It sits 58-146 px above the toolbar's top rim, well inside the reach of a 38-47 px kernel. Their toolbar DC test regressed the observed DC on '[1, x, Gaussian-blurred target]' over exactly that region. Their whole claimed feature is 2-4 code values with residuals of 0.17-0.47; a vertical Gaussian(47) at row 2320 draws 6.8% of its weight from rows 2160-2250, where the signed backdrop error is roughly +70 codes over x 62-600 and about -150 over x 600-629, giving a systematic of order 0.5-1.5 codes with a left-right shape. That is also the right sign and size to explain their own unexplained shortfall (observed +1.8 where they predicted +2.8). The claim may be true; the measurement does not establish it.",
  "THE FREE LINEAR-IN-X NUISANCE TERM IS EXCLUDED BY THEIR OWN DATA. They never checked whether the material actually has a horizontal gradient. It does not: the pill's left plateau (x 390-435, rows 154-166) fits slope +0.0032 code/px with residual sd 0.20, the right plateau +0.0221 with sd 0.09 (and that one is the wide kernel's tail still rising, not shading), and the back and more buttons over perfectly uniform backdrop show a symmetric centre-bright bump of 4-5 codes rather than a ramp. Their fitted c1 of 0.042 / 0.060 code/px would put a 1.9 / 2.8 code ramp across a 46-px plateau window, which the plateaus rule out. This single unchecked parameter is responsible for sigma 38 instead of 46, for a_tot 0.395 instead of 0.465, and for both absolute amplitudes being ~15% low.",
  "NO SHARP PATH IS VISIBLE IN THE VERTICAL DIRECTION AT THE PILL, which undercuts their depth axis there. The backdrop under the pill changes from the hard black/white split to the grey ramp at row 259.5. At x 435-475 the backdrop jumps from 0 to 99 across that row; a locally-sampling 11-13% sharp path must produce a +11 code step. Observed: 158.05 at row 259, 158.80 at row 260 (+0.75). At x 685-725 the backdrop drops 255 to 154 and the observed change is +0.50, not -11. So rows 255-269 are not sampling their own backdrop rows at all - consistent with their own claim 10 of ~35 px inward displacement near a rim, which they applied only to the toolbar. Their rows 213-226 'depth 45.5-59.5' band is 46-60 px from the bottom rim by geometry but is inside the region where the vertical sample is already displaced. It does not corrupt their horizontal measurement (a vertical shift does not move a vertical edge) but it means their depth coordinate is a geometric label, not a measured sampling depth.",
  "THE 8PX-VS-32PX RATIO IS INTRINSICALLY ROW-CONFOUNDED and they gave it a precision it cannot have. The two bands are at different rows by construction (8px = rows 2262-2341, 32px = 2342-2421), so the ratio always mixes in the vertical gradient they themselves measured at 18% over 59 rows. They compared row 2340 with row 2346 and reported 0.988 to three digits. Extrapolating both bands linearly to the common boundary row 2341.5 gives 0.978, 0.987 or 0.994 (light) depending on whether you extrapolate from 10, 16 or 24 rows, and 0.990-0.992 (dark) - a spread that maps to sigma 0.40-0.59 px, not a determination of 0.55.",
  "THE WIDE KERNEL IS NOT A GAUSSIAN, which they never tested because they only ever fitted one. Even after symmetrising to cancel the centre-bright bump, the tail at d = 120 / 140 px is 0.0159 / 0.0066 where Phi(-d/47) predicts 0.0053 / 0.0014 (3x, 4.7x), and the best-fit sigma drifts monotonically with fit window (44.0 at +/-110 px, 45.7 at +/-145, 46.7 at +/-175) - the signature of a heavy-tailed kernel. In absolute terms the excess is 1-2% of the wide component, at the level of element-to-element tint differences (the back button reads 132.70 where the pill's left plateau reads 133.71), so I would not claim it is real - but it is why no single sigma fits below rms 2.3-2.8 codes when the data noise is 0.1-0.4."
 ]
}
```

---

## Refutation attempt: Tint, opacity and tone response

Agent `a192fb1584e157fa1`.

```json
{
 "survives": false,
 "reasoning": "VERDICT: the first half of the headline reproduces almost exactly; the second half (\"the response is affine on smooth backdrops with an extra ~6%-of-tint lift that appears only where local backdrop contrast is high\") is built on two UI elements that are not the material being measured, and it collapses when I run the analyst's own sampling protocol on a second capture they had in hand.\n\nWHAT I REPRODUCED (so I am not disputing these)\nRegistration: identity confirmed independently on the 4 px stripes, rows 440-720 x 100-560: MAD 0.0045 at dx=0 vs 63.4/63.9 at dx=-1/+1, and 1.80/1.81 at dy=+/-1. Pairing confirmed by checkerboard MAD: IMG_6644 and IMG_6647 show the LIGHT target, IMG_6645/6646 the DARK one.\nAnchors, my own masks (disc r<=48 about (113.5,206.5) and (1055.5,206.5), median with |v-med|<6 rejection):\n  LIGHT chrome  y173-194: f(0)=131.0, f(255)=249.0, transmission 0.4627, alpha 0.5373, tint 243.8\n  DARK chrome   y173-194: f(0)=32.0,  f(255)=181.0, transmission 0.5843, alpha 0.4157, tint 77.0\nNeutrality: max |R-B| = 0 over the sampled interiors, confirmed.\n\"No distant adaptation\": IMG_6644 vs IMG_6645, top chrome rows 140-274 x 40-1130 -> exactly 985 pixels differ by >2 per-pixel, and 100.0% of them fall in x 500-820 / y 220-260 (the clock). Toolbar rows 2302-2449: max diff 3, mean 0.056. Their number reproduces to the pixel.\nS(u) antisymmetry excess: I get light peak +14.4 at u=3 (they said +13.8), light rows 214-226 +13.0 (they: +12.5), dark +5.0 (they: +4.2), dark rows 214-226 +4.0 (they: +3.9). The effect is real.\nCompression is NOT a confound: colour transitions in the primaries band are pixel-sharp with no chroma subsampling (row 1040: x=145 is (234,51,35), x=146 is (117,251,77), no intermediate pixel), so the PNGs carry no lossy-HEIC signature. Their measurements are clean on that front.\n\nWHERE IT FAILS\n1. THE TWO \"SMOOTH BACKDROP\" TRANSFER POINTS ARE FROM THE WRONG ELEMENT, AND THE CHOSEN CAPTURE DECIDES THE ANSWER.\nThe controls at rows 347-405 are not \"small round buttons\"; they are two rounded pills (x 870-996 with a star + chevron, x 1021-1121 with a photo-hex + chevron). Running the analyst's EXACT windows (x895-975 and x1035-1105, rows 355-385, |v-med|<6 rejection) on all four captures:\n   IMG_6644 (light chrome / light target): L 231.90 (n=2093, sd 0.46)  R 239.07 (n=1487, sd 0.50)   <- matches their 232.0 / 239.0\n   IMG_6645 (light chrome / DARK target):  L 205.46                     R 214.69\n   IMG_6647 (dark chrome / light target):  L 108.10                     R 117.12\n   IMG_6646 (dark chrome / DARK target):   L  83.70                     R  94.46\nThe grey ramp under these pills is BYTE-IDENTICAL in the two targets (rows 281-389 match round(x*255/1169) exactly with max deviation 0.0 in both files). The only thing that changes is the band at rows 390-439, which is white 255 in the light target and black 0 in the dark target, starting 15 px below the pill's bottom edge (pills detected at rows 348-404).\nSo the same element, same chrome, same centre-of-element backdrop value (204.0 and 233.0) reads 231.90 or 205.46 depending only on a band below it. Their statement \"a linear ramp is invariant under any symmetric blur kernel, so the effective input equals the ramp value at the element's centre regardless of how much the material blurs\" is falsified by 26.4 counts of output. Had they used IMG_6645 instead of IMG_6644 - same material, same ramp - the right control would have read 214.69 against their affine prediction of 239.5, a -24.8 miss, and they would have concluded the response was violently non-affine. The \"affine, verified at four inputs\" result is an artifact of which of two interchangeable captures was opened.\nTheir own correction for this was \"the white band that starts at row 390 ... 12-20% kernel weight in white explains +3 to +6\", applied only to the left control to excuse it while the right control was treated as the clean confirming point. Measured white-band contribution: +26.44 (left) and +24.38 (right). Four to eight times their estimate, and it applies to both controls.\n\n2. THOSE PILLS TRANSMIT ESSENTIALLY NOTHING AT PIXEL SCALE, SO THEY CANNOT BE THE TOP-CHROME MATERIAL.\nInterior slope over the ramp (rows 350-357, above the glyphs), fitted per pixel:\n   left pill x912-948:  +0.0027 (6644), +0.0053 (6647), +0.0138 (6645), +0.0077 (6646) counts/px\n   right pill x1058-1094: +0.0199, -0.0124, +0.0121, -0.0085 counts/px\nThe backdrop ramp slope is 255/1169 = 0.2181 counts/px. Under the analyst's own transfer the interior slope must be 0.4627*0.2181 = 0.1009 counts/px (light) or 0.1278 (dark), because a linear ramp passes through any normalised linear blur unchanged. Measured: 3-20% of that, in all four captures, on both pills. Raw pixels are integer-flat: 232.00, 232.00, 232.00, 232.00 across x 915-945 while the ramp beneath runs 200 -> 206. Yet the same pills move 22-26 counts for a DC change below them, i.e. their DC transmission is normal. That is a per-element averaged backdrop, not a shift-invariant blur - and it is a different element from the top chrome, which does pass a gradient (top-chrome pill bottom, x400-470 rows 262-272: slope +0.039 counts/px with roughly 40% of its kernel on the ramp).\nThe analyst noticed the dark-appearance version was 44-52 counts off and dropped it, but kept the light-appearance version. Same elements, same session; the flatness test says they are wrong in both.\nConsequence: the light material's tone response is verified at exactly two inputs, 0 and 255. Two points do not test affinity. And the \"adaptivity <= 0.2\" bound was brute-forced against three points, the middle one (233.4 -> 239.0) being this bogus one, so that bound has no support either.\n\n3. \"~6% OF TINT\" IS A TWO-POINT COINCIDENCE AND THE TWO CURVES HAVE DIFFERENT SHAPES.\nMy S(u)/2 excess, midpoints from the pill's own far fields (light 133.28 / 248.96, dark 33.66 / 180.64, rows 156-168):\n   light: +14.4 (u=3), +13.8 (6), +12.6 (10), +9.9 (20), +6.6 (40), +4.6 (50), +2.0 (80), +0.4 (120)\n   dark:   +4.5 (u=3),  +4.8 (6),  +5.0 (10), +5.0 (20), +5.05 (40), +3.7 (50), +1.9 (80), +0.6 (120)\nLight is peaked and decays monotonically; dark is FLAT from u=3 to u=40 and only then decays. The light/dark ratio falls from 3.2 at u=3 to 1.31 at u=40 to 1.10 at u=80. The tint ratio is 245/77 = 3.18, so \"proportional to the tint\" holds only if you read the peak and ignore that beyond u~40 the two excesses are nearly equal in absolute counts. One scalar cannot describe two curves of different shape.\n\n4. \"A PLAIN NEUTRAL BLEND\" UNDERSTATES THE SPATIAL RESPONSE.\nThe pill's edge response at the target's hard split (rows 156-168) is a sharp step riding on a very wide skirt: light goes 189.5 (x=583) -> 191.7 -> 201.0 -> 215.8 (x=586), a ~26 count jump in 3 px, then climbs slowly to 249.0 only by x=730; dark jumps 85.5 -> 98.9 -> 121.4 -> 132.7 over x 582-585, ~37 counts. Roughly a tenth of the backdrop passes essentially unblurred while the rest is smeared over +/-150 px, which is why their +/-100 px estimate and \"60 px margin\" verification are both short. The sharp edge also lands at x~585.35 in light chrome and x~583.55 in dark chrome (weighted centroid of the first differences), a 1.8 px displacement between the two variants that nothing in their model accounts for.\n\n5. THE INTERIORS ARE NOT FLAT, AND THEIR STATED REASON IS CONTRADICTED BY THEIR OWN DATA.\nRow-by-row medians inside the discs: light back button 131.0 at y164-179 rising monotonically to 139.0 at y254; light more button 249.0 -> 252.0; dark back 31 -> 38; dark more 181 -> 184. Both buttons brighten DOWNWARD in both polarities, even though the ramp beneath moves +25 under the back button and -25 under the more button. So the drift is not the \"local bleed\" they invoked, and the two anchors do not \"move together\": +8.0 versus +3.0. Transmission by band: 0.4627 (y164-179), 0.4627 (y173-194), 0.4627 (y200-215), 0.4510 (y236-254); alpha 0.5373 to 0.5490. Their +/-0.003 on alpha should be about +/-0.006, and the tint bracket is 243.8 to 249.5.\n\nMINOR\n- \"alpha 0.537 ... 4.7x more opaque than our Chrome preset\": the Chrome preset is tint = Color.White.copy(alpha = 0.11f) at D:/Work/AndroidStudioProjects/SideProjects/kmp/liquidglass/liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/GlassStyle.kt line 256. 0.537/0.11 = 4.88x. The 4.7x comes from their fitted 0.516, not from the headline's own 0.537.\n- The captures are tagged Display P3 (536-byte ICC, \"Display P3\"), not \"converted back to sRGB\" as stated. Harmless for every neutral number here, since P3 and sRGB share the white point and transfer curve, but the stated mechanism is wrong.\n- Rows 0-56 of every capture carry a separate blurring layer (it visibly smears the target's own label text at rows 18-48; e.g. at x=100 the target is 0,0,255,255,0 down rows 0-56 while the capture reads 16,24,49,41,13). Nothing was measured there, but it is not clean backdrop.\n\nScripts: C:/Users/shaya/AppData/Local/Temp/claude/D--Work-AndroidStudioProjects-SideProjects-kmp-Vitals/d83608da-a0a9-446c-9f1b-ec02e5b88c50/scratchpad/caps/refute/r00.py through r22.py. Nothing written to D:/liquidglass-captures or the repo.",
 "corrections": [
  {
   "quantity": "White-band contribution to the two ramp-band controls (their 'the left control is +6.1 high, fully accounted for by the white band ... 12-20% kernel weight in white explains +3 to +6')",
   "claimed": "+3 to +6 counts, and only on the left control",
   "your_value": "+26.44 on the left control and +24.38 on the right control",
   "why": "Their own sampling windows (x895-975 and x1035-1105, rows 355-385, |v-med|<6 rejection) applied to IMG_6644 vs IMG_6645. Both captures carry the same light-appearance chrome and a byte-identical grey ramp under the pills (target rows 281-389 match round(x*255/1169) with max deviation 0.0 in BOTH target files); the only difference is the band at rows 390-439, white 255 in the light target and black 0 in the dark target, starting 15 px below the pill bottom edge (pills span rows 348-404). Left: 231.90 vs 205.46. Right: 239.07 vs 214.69."
  },
  {
   "quantity": "Effective input under the ramp-band controls ('a linear ramp is invariant under any symmetric blur kernel, so the effective input equals the ramp value at the element's centre regardless of how much the material blurs')",
   "claimed": "204.0 (left) and 233.4 (right), fixed by the ramp value at the element centre",
   "your_value": "Not determinable, and demonstrably not the centre ramp value: implied input is 218.1 in IMG_6644 and 162.5 in IMG_6645 for the same left control over the same ramp",
   "why": "Inverting their own transfer f = 131.0 + 0.4627*in on the measured outputs 231.90 and 205.46. A 56-count swing in implied input with the centre backdrop held byte-identical falsifies the invariance argument, which is the sole discriminator they used to separate a curved tone response from a contrast-triggered lift."
  },
  {
   "quantity": "Pixel-scale interior slope of the ramp-band controls (implicitly assumed to equal transmission x ramp slope)",
   "claimed": "implied 0.1009 counts/px for light chrome (0.4627 x 255/1169); reported only as 'essentially flat, sd 0.46'",
   "your_value": "+0.0027 (IMG_6644), +0.0138 (IMG_6645), +0.0053 (IMG_6647), +0.0077 (IMG_6646) counts/px on the left pill x912-948 rows 350-357; +0.0199, +0.0121, -0.0124, -0.0085 on the right pill x1058-1094",
   "why": "Least-squares slope of the row-averaged interior against x. Raw pixels are integer-flat (232.00 across x 915-945 while the ramp beneath runs 200->206). A linear ramp passes any normalised linear blur unchanged, so 3-20% of the expected slope means these elements transmit almost nothing per-pixel while still showing normal DC transmission - a per-element averaged backdrop, i.e. a different element from the top chrome, which does pass a gradient (top-chrome pill bottom x400-470 rows 262-272: +0.039 counts/px)."
  },
  {
   "quantity": "Shape and scaling of the high-contrast excess ('about 6% of the tint colour added', peak-only)",
   "claimed": "LIGHT +13.8, DARK +4.2; excess/tint = 0.056 and 0.055",
   "your_value": "LIGHT +14.4 at u=3 falling monotonically to +0.4 at u=120; DARK flat at +4.5 to +5.05 from u=3 to u=40 then falling to +0.6 at u=120. Light/dark ratio 3.2 at u=3, 1.31 at u=40, 1.10 at u=80",
   "why": "Same S(u)/2 test, midpoints taken from the pill's own far fields (light 133.28 / 248.96, dark 33.66 / 180.64, x400-440 and x700-760, rows 156-168). The tint ratio is 245/77 = 3.18, which the peak matches and the tail (ratio ~1.1) does not. Two curves of different shape cannot both be a single scalar fraction of the tint; the proportionality is a coincidence at u=3-6 only."
  },
  {
   "quantity": "Uncertainty on the light blend alpha",
   "claimed": "0.537 +/- 0.003, 'alpha is far more stable than the tint colour because both anchors move together under local bleed'",
   "your_value": "0.537 to 0.549 across row bands; the two anchors move by +8.0 and +3.0, not together",
   "why": "Row-band medians on my own r<=48 masks: back button 131.0 (y164-179) -> 137.0 (y236-254); more button 249.0 -> 252.0. Transmission 0.4627/0.4627/0.4627/0.4510 for bands y164-179 / y173-194 / y200-215 / y236-254. Both discs brighten downward in both polarities even though the ramp beneath them moves +25 under one and -25 under the other, so the drift is not local bleed and does not cancel."
  },
  {
   "quantity": "'4.7x more opaque than our Chrome preset'",
   "claimed": "4.7x, quoted alongside alpha 0.537",
   "your_value": "4.88x",
   "why": "GlassStyle.kt line 256 defines Chrome with tint = Color.White.copy(alpha = 0.11f). 0.537/0.11 = 4.88. The 4.7x figure corresponds to their brute-forced best fit alpha 0.516, not to the headline value; the headline's two numbers do not reproduce each other."
  },
  {
   "quantity": "Spatial character of the material ('a plain neutral blend'; edge response 'spreads over about +/-100 px'; backdrop 'verified ... plus a 60 px margin')",
   "claimed": "plain blend, +/-100 px spread, 60 px margin sufficient",
   "your_value": "A ~26-count step in 3 px (light) / ~37-count step (dark) superimposed on a skirt still climbing 150 px from the edge; sharp-edge centroid at x~585.35 (light) vs x~583.55 (dark)",
   "why": "Pill profile rows 156-168: light 189.5(583) -> 191.7 -> 201.0 -> 215.8(586), reaching 249.0 only by x=730; dark 85.5(582) -> 98.9 -> 121.4 -> 132.7(585). Roughly a tenth of the backdrop passes unblurred, so a 60 px verification margin does not bound the effective input, and the two chrome variants place their sharp edge 1.8 px apart."
  }
 ],
 "confounds_missed": [
  "The two elements used for the mid-tone transfer points are not the top-chrome material. They are rounded pills containing two glyphs each (star+chevron at x870-996, photo-hex+chevron at x1021-1121, rows 348-404), and their interiors are flat to 0.003-0.020 counts/px against an expected 0.101 - they do not transmit a gradient at pixel scale while still showing normal DC transmission. The report dropped them in dark appearance for reading 44-52 counts low but kept them in light appearance.",
  "The second capture of the identical scene (IMG_6645) was available and decides the question. Same light-appearance chrome, byte-identical grey ramp under the pills, yet the controls read 205.46 and 214.69 instead of 231.90 and 239.07. The 'affine on smooth backdrops' conclusion depends entirely on which of the two files was opened.",
  "The target has its own content within the material's reach that is not the hard split: a white-on-black / black-on-white label row at rows 18-48 (mean 69.9 over the back-button columns vs 0, and 255.0 over the more-button columns), and the ramp band starting at row 260 (mean 25.1 under the back button, 229.9 under the more button). Both perturbations push the two anchors in OPPOSITE directions, so they compound rather than cancel; the report asserted the opposite ('matched row bands, so the local-bleed error cancels'). Measured effect on the anchors is small (~1-2 counts, since the vertical cumulative kernel weight fitted from the four ramp-approach profiles is only ~0.28 even 4 px from a horizontal backdrop edge), but the stated cancellation argument is wrong and the sign of the residual bias is upward on alpha.",
  "Rows 0-56 of all four captures carry a separate blurring layer that smears the target's own label text (at x=100 the target reads 0,0,255,255,0 down rows 0-56 while the capture reads 16,24,49,41,13). Nothing measured sits there, but 'the status-bar icons ... sit above it and depress the top' of the more button is not a supported explanation: the status bar is not in the glass's backdrop, and both discs brighten downward in both polarities regardless of whether the backdrop below them is brighter or darker.",
  "The captures are tagged Display P3 (536-byte ICC profile), not converted to sRGB. It changes none of the neutral numbers - P3 shares sRGB's white point and transfer curve - but the stated mechanism for the saturated-colour shift is wrong, and it means the target PNG (untagged/sRGB) and the captures are in different colour spaces even though greyscale compares exactly.",
  "The material is not shift-invariant: the ramp pills respond ~46% to a DC backdrop change 15 px below them while passing ~5% of a gradient inside them. No convolution can do that. Every argument in the report that relies on 'any symmetric kernel' - the blur-proof linearity test and the ramp-invariance discriminator - assumes shift-invariance and inherits this."
 ]
}
```

---

## Refutation attempt: Rim lighting geometry

Agent `aaf4cd40f13640768`.

```json
{
 "reasoning": "I re-derived everything from the rasters with different methods (2-D polynomial body model + bilinear sampling at fixed depth; a per-angle template fit; raw-pixel reads on the pill's two pixel-aligned straight edges; a 64x64 supersampled coverage sweep; and an outside-darkening profile referenced to the known target rather than to a single row band).\n\nMOST OF THE REPORT SURVIVES, and several parts survive very cleanly:\n- Light direction. My lobe centroid (first moment of the d=2 px rim excess over |\u03b8|\u226476\u00b0, body from a degree-3 2-D polynomial fitted on the annulus r=34-57 px) gives +0.12\u00b0 (IMG_6644 BACK), +0.12\u00b0 (IMG_6647 BACK), +0.26\u00b0 (IMG_6647 MORE). Directional/parallel confirmed: no tilt between buttons 942 px apart in x.\n- Peak amplitude. My template fit on the pill's top edge gives 63.1-66.7 over black (x=440-500) and 59.7-62.5 over white (x=660-720). Their +64.3 / +65.3 / +62.3 reproduce to the level.\n- Half-width. My clean template (mean of 21 columns, x=440-500, IMG_6647, deep body = median of rows 168-190) is 1.000, 0.716, 0.479, 0.184, 0.154, 0.118, ... \u2192 50% crossing at 2.41 px vs their 2.34.\n- Additive, not screen/multiply: a screen blend predicts an excess ratio of 0.34 between the body=181 and body=37 cases; observed 0.95.\n- Highlight is strictly inside the outline: over black, the two rows outside read exactly 0.0; over white with dark chrome, exactly 255.\n- Zero specular at \u00b190\u00b0: IMG_6647 row 207, x=990 reads 181.0 against a body of 181.0.\n- Neutral colour: (193,193,193) at x=114 row 141 \u2014 exact.\n- Dark contour. I reproduced every number and extended the measurement below row 260 by referencing the known ramp instead of assuming 255. Full 360\u00b0 profile at radius 66.5 (IMG_6647 MORE): 0.0% at 0\u00b0, 5.7% at 20\u00b0, 20.5% at 40\u00b0, 36.2% at 60\u00b0, 61.2% at 90\u00b0, 37.4% at 120\u00b0, 21.7% at 140\u00b0, 5.3% at 160\u00b0, 0.7% at 180\u00b0. So it is zero at BOTH poles and maximal at \u00b190\u00b0 \u2014 it is not a downward drop shadow, and their claim is stronger than they could show.\n- Geometry, encoded compositing, registration, and both subject rejections (toolbar refraction, dark-outlined capsules) all reproduce exactly.\n\nWHAT FAILS is the counter-lobe conjunct of the headline, and it fails on a factual error about the dataset.\n\nThe target's grey ramp begins at row 260; the nav circles run to row 272 and the pill to row 272. So the bottom 13 px of every top-chrome element \u2014 the entire counter-lobe pole, \u03b8\u2208(144\u00b0,216\u00b0) \u2014 sits over the ramp, not over the uniform backdrop. At the MORE button the south-pole backdrop is 216-245 (230 at the pole), not 255; at BACK it is 10-40, not 0. The ramp also carries 1-px inverted tick lines (target rows 260-285) at x = 0, 116, 233, 350, 467, 701, 818, 935, 1052, 1169 \u2014 i.e. 2.5 px from the BACK centre (113.5) and 3.5 px from the MORE centre (1055.5), directly on the south pole, with amplitudes of +204 and \u2212205 levels. Those ticks are invisible in the glass body (IMG_6647 row 266 reads a flat 188 across x=1049-1055 despite the \u2212205 tick at 1052) but plainly visible in the rim (row 272 reads 224,224,225,207,232,238,241 \u2014 a \u221231 dip, ~15% transmission). So the rim and the body sample completely different neighbourhoods, which invalidates using a d\u2208[12,20] body window at an angle where the backdrop changes.\n\nThe decisive demonstration is the pill's own straight bottom edge, one element, one angle (\u03b8=180), no model needed: at x=470 the rim reads 157/126/98 at d=0.5/1.5/2.5 over a local body of ~57 \u2192 amplitude ~100; at x=680 it reads 221/205/194 over a body of ~171 \u2192 amplitude ~50. The only difference is that the ramp below (103) is brighter than the local body on the left and (148) darker than it on the right. A template fit gives A_bot/A_top = 1.40-1.64 on the black side and 0.76-0.92 on the white side of the same edge. The \u03b8=180 amplitude is not measurable to \u00b15% anywhere in this dataset.\n\nTheir own three ratios average 1.045; they discarded the 1.144 for grey-ramp contamination and then kept two measurements carrying exactly the same contamination \u2014 including the one they described as \"cleanest, backdrop uniform on both poles\", which is simply false.\n\nSeparately, the \"single component / 99.2% of variance\" claim is wrong as physics. Normalised radial profiles (identical in all three usable subject/capture combinations): at \u03b8=0, 1.000, 0.850, 0.690, 0.500, 0.311, 0.221, 0.202, 0.184 at d=1.6\u20264.4; at \u03b8=45, 1.000, 0.814, 0.573, 0.298, 0.098, 0.011, \u22120.003, \u22120.004. The 15-20% shoulder that runs out to d\u224812 px exists only within ~\u00b130\u00b0 of the poles and is gone by \u03b8=45\u00b0, and the core is much narrower off-axis. My own SVD on the same kind of matrix returns 97.5-98.4% for component 0 while those profiles visibly differ \u2014 the variance fraction is dominated by the shallow high-amplitude rows and is not evidence of separability. This is why HWHM is estimator-dependent: 33\u00b0/31\u00b0 at d=1.6, 30\u00b0/29\u00b0 at d=2.0, 19\u00b0 at d=2.8, 16\u00b0 at d=3.2, back to ~22\u00b0 at d=6-8, and 38-45\u00b0 from an all-depth template fit.\n\nBecause the headline asserts \"a counter-lobe of equal strength (1.00 \u00b1 0.05)\" as one of its four load-bearing quantities, and I cannot reproduce that number or anything like that tolerance, I mark it as not surviving \u2014 while noting that roughly three quarters of the report is solid and independently confirmed.",
 "survives": false,
 "corrections": [
  {
   "quantity": "Counter-light ratio (rim brightness at \u03b8=180 divided by \u03b8=0)",
   "claimed": "1.00 \u00b1 0.05",
   "your_value": "~1.05-1.10 central, systematic \u00b10.2 at best; defensible estimators span 0.76 to 1.64",
   "why": "Every \u03b8=180 measurement in this dataset sits over the target's grey ramp (rows 260+), not over the uniform backdrop. Decisive raw-pixel demonstration on the 'Today' pill's straight bottom edge (IMG_6647, edge at y=272.5, one element, one angle): at x=470 rows 272/271/270 read 157/126/98 over a local body of ~57 \u2192 amplitude ~100; at x=680 rows 272/271/270 read 221/205/194 over a body of ~171 \u2192 amplitude ~50. Template fit gives A(180)/A(0) = 1.40-1.64 on the black side (x=440-500) and 0.76-0.92 on the white side (x=660-720). Circle estimates: fixed-depth d=2 px with a degree-3 2-D polynomial body \u2192 1.068 (6644 BACK), 1.144 (6647 BACK), 1.065 (6647 MORE); the same with degree 2/4/5 or annuli 30-55/40-60/45-60/50-60 spans 0.86-1.18; a per-angle template fit gives 1.037, 1.177, 1.026. Their own three numbers average 1.045, not 1.00."
  },
  {
   "quantity": "Backdrop under the MORE button's lower pole ('cleanest, backdrop uniform on both poles')",
   "claimed": "uniform 255 at both poles",
   "your_value": "north pole 255; south pole is the grey ramp, 216 at x=990 rising to 245 at x=1121, 230 at the pole, plus a 1-px near-black (25) tick line at x=1052",
   "why": "calibration-target-light.png rows 0-259 are the hard split but the grey ramp starts at row 260, and the circle runs to row 272. Verified: target[270, 990/1055/1121] = 216/230/245. The ramp band also carries inverted 1-px tick columns spanning rows 260-285 at x = 0, 116, 233, 350, 467, 701, 818, 935, 1052, 1169 \u2014 3.5 px from the MORE centre (1055.5) and 2.5 px from the BACK centre (113.5), i.e. on the south pole. The same contamination they cited to discard the BACK/dark ratio of 1.144 applies to both cases they kept."
  },
  {
   "quantity": "The rim is a single component (one depth profile scaled by one angular weight)",
   "claimed": "99.2% of variance in SVD component 0; depth shape does not change with angle",
   "your_value": "not separable \u2014 the shoulder is present at \u03b8=0 and absent by \u03b8=45",
   "why": "Normalised radial profiles (bilinear at fixed depth, \u00b16\u00b0 wedges, degree-3 polynomial body; identical in IMG_6644 BACK, IMG_6647 BACK and IMG_6647 MORE). \u03b8=0: 1.000, 0.850, 0.690, 0.500, 0.311, 0.221, 0.202, 0.184, 0.161 at d = 1.6, 2.0, 2.4, 2.8, 3.2, 3.6, 4.0, 4.4, 4.8 px. \u03b8=45: 1.000, 0.814, 0.573, 0.298, 0.098, 0.011, \u22120.003, \u22120.004, \u22120.001. The 15-20% shoulder (\u22488 levels at \u03b8=0) is 0 \u00b1 0.6 levels at \u03b8=45, and the core is far narrower off-axis (0.311 vs 0.098 at d=3.2). My own SVD of the same matrix returns 97.5-98.4% for component 0, so that statistic does not test separability \u2014 it is dominated by the shallow high-amplitude rows."
  },
  {
   "quantity": "Angular falloff \u2014 half-width at half maximum of the lobe",
   "claimed": "35.2 / 37.2 / 34.5, mean 35.6 \u00b1 1.4 deg",
   "your_value": "30-45 deg depending on the estimator; not a single well-defined number",
   "why": "Because the lobe is not separable, HWHM depends on depth. IMG_6644 BACK, fixed-depth measurement: 33.3/31.4\u00b0 at d=1.6, 30.5/29.4\u00b0 at d=2.0, 28.1/26.0\u00b0 at d=2.4, 18.7/18.7\u00b0 at d=2.8, 15.6/15.5\u00b0 at d=3.2, rising back to ~22\u00b0 at d=6-8. IMG_6647 BACK and IMG_6647 MORE agree. A per-angle template fit that weights all depths instead gives 37.9/38.5\u00b0, 42.7/42.2\u00b0 and 45.1/45.3\u00b0. The \u00b11.4 quoted is roughly a tenth of the real spread."
  },
  {
   "quantity": "Best simple empirical form for the lobe, (1 \u2212 \u03b8/90)^k",
   "claimed": "k = 1.27 to 1.38",
   "your_value": "k = 1.12-1.20 from a per-angle template fit (RMS 0.029-0.040); k \u2248 1.5-1.7 from the fixed-depth d=2 lobe",
   "why": "Same grid search, but the answer moves with the estimator because of the non-separability above. Folded normalised lobe from the template fit (IMG_6644 BACK, \u03b8=0,4,\u2026,88): 1.000, 0.981, 0.873, 0.841, 0.771, 0.717, 0.692, 0.661, 0.579, 0.601, 0.509, 0.489, 0.423, 0.352, 0.306, 0.283, 0.230, 0.161, 0.129, 0.047, 0.002. From the fixed-depth d=2 lobe instead: 1.000, 0.979, 0.906, 0.831, 0.751, 0.664, 0.597, 0.566, 0.488, 0.470, 0.391, 0.370, 0.322, 0.269, 0.232, 0.214, 0.172, 0.126, 0.098, 0.039, 0.006."
  },
  {
   "quantity": "Best single-cosine-power fit (uSpecularPow)",
   "claimed": "2.8 / 2.9 / 2.6",
   "your_value": "2.31 / 2.27 / 2.09 from the per-angle template fit (RMS 0.072); ~3.5 at 40\u00b0 from the fixed-depth lobe",
   "why": "Same grid search on my folded lobes. The disagreement is the same estimator sensitivity; the qualitative conclusion (a cosine power fits poorly, RMS ~7-9% of peak) is confirmed."
  },
  {
   "quantity": "IMG_6644 and IMG_6645 have pixel-identical top chrome (same for IMG_6647/IMG_6646)",
   "claimed": "pixel-identical",
   "your_value": "not identical: 955 pixels differ by more than 3 levels (max 230) in IMG_6644 vs IMG_6645, and 1077 in IMG_6647 vs IMG_6646",
   "why": "Direct per-pixel difference over rows 130-290. The differences are confined to rows 227-255, x = 513-655 \u2014 the date subtitle inside the 'Today' pill. It does not touch any rim, so the conclusions are unaffected, but the claim as written is false and would mislead anyone reusing that band."
  },
  {
   "quantity": "Peak rim brightness extrapolated to the outline (d=0)",
   "claimed": "75 \u00b1 5, by quadratic through the three 1-px band means at d = 0.5, 1.5, 2.5",
   "your_value": "75-78, but the stated method does not justify it",
   "why": "The quadratic treats 1-px band means as point samples at the band midpoints. For a convex decreasing profile the band mean exceeds the midpoint value, so that fit is biased low. Under an exponential with the measured 2.4 px half-length, mean over [0,1] = 0.868\u00b7f(0), giving f(0) = 65.4/0.868 = 75.3 \u2014 the right answer for a different reason. The number is fine; the derivation is not the one that supports it."
  }
 ],
 "confounds_missed": [
  "The target's grey ramp starts at row 260 while every top-chrome glass element runs to row 272, so the bottom 13 px of both nav circles and the pill \u2014 the entire counter-lobe pole, \u03b8\u2208(144\u00b0,216\u00b0) \u2014 sits over the ramp rather than over the uniform black/white. This is asserted the other way in the report ('backdrop uniform on both poles').",
  "The ramp band carries 1-px inverted tick columns spanning target rows 260-285 at x = 0, 116, 233, 350, 467, 701, 818, 935, 1052, 1169. Two of them (x=116 and x=1052) fall 2.5 px and 3.5 px from the two nav-circle centres \u2014 directly on the south pole \u2014 with amplitudes of +204 and \u2212205 levels against the local ramp.",
  "The rim transmits high-frequency backdrop detail that the body does not, so 'rim' and 'body' sample different neighbourhoods and the d\u2208[12,20] body window is not a valid reference wherever the backdrop changes across the element. Measured on the x=1052 tick, IMG_6647 MORE: at d=6.5 px (row 266) the tick is invisible (flat 188 across x=1049-1055, <0.5% transmission of a \u2212205 step), while at d=0.5 px (row 272) it produces a \u221231 level dip (224,224,225,207,232,238,241), i.e. ~15% transmission. Same effect with opposite sign on the BACK button's bright tick: row 272 x=113-118 reads 203,203,197,197,172,157, roughly +12 at x=116.",
  "The 'Today' pill's top edge is only straight for x \u2273 450. At x=430 the brightest row is 142, not 141 (IMG_6644: 132, 185, 168, 147, 145; IMG_6647: 62, 85, 69, 46, 45), so the pixel-aligned-edge argument does not hold across the whole quoted edge \u2014 it does hold at the x=470 and x=680 columns actually used.",
  "The 'Today' pill body is crossed by the label and date glyphs at roughly x=500-580 (rows ~180-230) and x=513-655 (rows 227-255); any body reference taken by column in that range is contaminated. My first template attempt was corrupted this way before I restricted to x=440-500.",
  "The rim excess retains a small but real backdrop dependence even at \u03b8=0 (65.3 over black vs 62.3 over white for the same dark chrome, and 63.1-66.7 vs 59.7-62.5 across many columns in my template fit) \u2014 about 5%, inside the quoted \u00b13 but not literally 'backdrop-independent'.",
  "Scope: the two capsules at rows 349-404 are a different material with a dark 1-2 px outline and no bright rim at all (IMG_6644 x=940: row 349 = 174, rows 350-402 = flat 231-232, row 403 = 203, row 404 = 174). The report notes this, but the headline's 'the rim' is stated as if it described iOS glass generally when it describes only the nav chrome and toolbar circles in these captures."
 ]
}
```

---

## Refutation attempt: Chromatic dispersion at the rim

Agent `aee88eb3d582831cf`.

```json
{
 "survives": true,
 "reasoning": "The headline's factual claim survives and is understated. Its central number does not reproduce \u2014 but the error runs in the conservative direction, so I cannot call the claim false.\n\nWHAT REPRODUCES EXACTLY\n- Chroma = max(R,G,B)-min(R,G,B) = 0 at 100% of pixels in all three of their rectangles (top 135-285 x20-1150 = 169,500 px; ramp 340-412 x860-1130 = 19,440 px; toolbar 2296-2456 x70-1100 = 164,800 px), in all four captures. Not one nonzero pixel.\n- max|grad| in the eroded toolbar interior = 30.05 levels/px at y=2418, x=1064 (theirs 30.4; the gap is erosion implementation, my N=69,822 vs their 73,911).\n- Rim displacement: trash left rim y=2376, observed dark->bright edge x\u2248950.5 vs backdrop edge 959.5 = 9.0 px; share right rim, observed \u2248220.0 vs backdrop 207.5 = 12.5 px. Both pull content outward toward the rim.\n- Toolbar transmission 0.145 (184 over black / 221 over white at y=2376, read across the periodic 32px stripes where the element's own gradient cancels); theirs 0.157.\n- Shader arithmetic: GlassStyle.kt has refractionDepth=14.dp, dispersion=0.018f. GlassShaderSource.kt: push=n*bend*lens, cr=base+push*(1-split), cb=base+push*(1+split), split=uAberration*bend. At bend=1, lens=42px that is 1.512 px. Their predicted fringe reproduces: peak 35, mean 5.02 levels.\n- Registration: I re-checked independently and got bit-exact agreement with the target on uncovered stripes at y=600 (4px period), y=2331 (8px) and y=2376 (32px). Identity confirmed.\n\nI ALSO CHECKED THE THINGS THEY DID NOT, AND THEY HOLD\n- My first hypothesis was that the originals are lossy HEIC with 4:2:0 chroma subsampling, which would make \"chroma exactly 0\" a codec artefact. It is dead: the HEICs are 4:4:4, 10-bit. The PNGs are bit-identical to the 8-bit HEIC decode, so the conversion introduced nothing.\n- The 10-bit data is genuinely independent information \u2014 every 8-bit value maps to all four of its 10-bit sub-levels, so v8 = v10>>2 and two real extra bits exist below anything they examined. At 10-bit, chroma is STILL exactly 0 across all 353,740 rectangle pixels. That is a 4x finer null than they reported.\n- Codec deadzone objection closed empirically: 1,779 pixels in this same file carry 10-bit chroma of 1-3 levels while sitting >8 px from any pixel with chroma>=20. The encoder does record quarter-of-an-8-bit-LSB chroma in near-neutral surroundings. Luma is near-lossless (99.6% bit-exact vs target on the 4px stripes, MAD 0.004).\n- I confirmed their steepest-gradient site is genuinely refracted backdrop, not a drawn glyph and not a specular rim stroke: at y=2418 the interior reads 168 over black backdrop and 210 over white (ratio = the measured 0.16 transmission), with the transition displaced 8.5 px from the true backdrop edge at x=1055.5.\n\nWHAT DOES NOT REPRODUCE\n1. The 0.03 px bound is wrong and the method that produced it is biased. They shifted an already-integer-valued image. Because the base is integer and the perturbation is symmetric about it, R and B always round back to the same integer until d*grad exceeds 1.0 \u2014 so the simulation cannot detect anything below 1/max|grad| by construction. Their \"independent analytic cross-check\", 1/30.4 = 0.033, is therefore the same quantity, not a cross-check. Redone with proper de-quantisation (add U(-0.5,0.5) before shifting, since the real pipeline rounds once from a continuous value): d=0.002 px gives 440 detectable pixels, d=0.010 gives 2,155, at 8-bit. At 10-bit, d=0.0001 px gives ~90. Observed: 0. The honest conservative bound is <0.002 px.\n2. \"Roughly 50x\" is therefore wrong. 1.512/0.002 = >=750x. The headline understates its own result by more than an order of magnitude.\n3. They missed a glass element. A fourth circular glass button (Live Text / scan) at rows ~1985-2095, x ~1030-1135 is present in IMG_6645, 6646 and 6647 and lies outside all three rectangles \u2014 ~8,600 altered pixels per capture, with grid lines visibly refracting through it. \"Every one of ~74,000 glass pixels across four captures\" is not exhaustive as stated. I measured it: chroma 0 at 8-bit and 10-bit, so the conclusion is unaffected, but the coverage claim is false as written.\n4. Today-pill transmission 0.232 is contaminated. Their two windows sit on a continuous intrinsic brightness ramp of the pill itself: at row 216 the profile climbs monotonically 160->194 across x540-584 with the backdrop constant black, and 226->243 across x587-640 with it constant white \u2014 0.3 to 0.8 levels/px of the element's own gradient. Correcting gives ~0.19; extrapolating the two local trends to the split gives ~0.12. Honest range 0.12-0.23, not a three-significant-figure 0.232. Their toolbar figure is sound because the periodic backdrop cancels the intrinsic gradient.\n5. \"37,415 of 73,911 rim pixels visibly non-neutral\" conflates measurable with visible \u2014 that is the chroma>=1 count. I get 35,256 at chroma>=1 but 19,758 at >=4 and 18,529 at >=8. The substance (a genuinely visible fringe from our default) survives; the adverb is attached to the wrong number.\n6. Minor: they write the split as 2*uAberration*bend*lens. The shader gives 2*uAberration*bend^2*lens. Identical at bend=1, which is where they evaluated it, so the headline number is unharmed \u2014 but the falloff away from the rim goes as bend^3 times gradient, not bend^2.\n7. Minor: lens = uRefractDepth * mix(0.85,1.25,scale) * mat, so 42 px assumes that factor is exactly 1.0. The real range is 35.7-52.5 px, i.e. 1.29-1.89 px, not a single 1.51.\n8. Minor, and it is one of the exact errors they were told to avoid: their edge #3 (y=2331, x951-961, profile \"255,255,255,198,11,100,228\") is not a refracted backdrop edge. The 11 and 100 are the button's own drawn dark outline. It is mislabelled as \"the rim itself crossing a stripe\". Harmless here only because every channel is identical everywhere.\n\nBOTTOM LINE\nThe headline sentence \"the iOS 27 material shows no chromatic dispersion at all\" is correct for what was captured, and I would state the bound as <0.002 px rather than <0.03 px. The practical conclusion for the library \u2014 that uAberration=0.018 is far too large to match iOS at rest \u2014 stands and is understated: even scaling to iOS's own measured 9-13 px rim displacement rather than our 42 px lens, 2*s*11 < 0.002 gives s < 9e-5, so the default is ~200x too large by that route, not 12x. Where the claim overreaches is scope, not sign: this is two static UI states of one app, one glass variant, at rest, every element over a neutral backdrop, with the Liquid Glass transparency slider at an unrecorded position. It says nothing about the Clear variant, about in-flight morph or drag, or about glass over saturated colour.",
 "corrections": [
  {
   "quantity": "Upper bound on red-to-blue separation at the rim",
   "claimed": "< 0.03 px",
   "your_value": "< 0.002 px (conservative, 8-bit); < 0.0002 px if the 10-bit samples are used",
   "why": "Their detection-floor simulation shifted an already-integer-valued image, so R and B round back to the same integer until d*max|grad| exceeds 1.0 \u2014 it cannot detect anything below 1/max|grad| by construction, and the '1/30.4 = 0.033 agrees' cross-check is the same quantity restated, not independent. Redone with de-quantisation (add U(-0.5,0.5) before the bilinear shift, since the compositor rounds once from a continuous value): d=0.002 px -> 440 of 69,822 mask pixels would show chroma>=1; d=0.010 -> 2,155; at 10-bit d=0.0001 -> ~90. Observed 0 in every case."
  },
  {
   "quantity": "Ratio of iOS dispersion bound to our uAberration=0.018 default",
   "claimed": "roughly 50x smaller",
   "your_value": ">= 750x smaller (1.512 / 0.002)",
   "why": "Follows directly from the corrected bound. The headline understates its own finding by more than an order of magnitude."
  },
  {
   "quantity": "Equivalent bound on our uAberration parameter",
   "claimed": "< 0.0004 at 42px lens; < 0.0015 at iOS's ~11px displacement; 'so it is 12x to 50x too large'",
   "your_value": "< 2.4e-5 at 42px lens; < 9.1e-5 at 11px. The default 0.018 is 200x to 750x too large.",
   "why": "Same corrected 0.002 px bound propagated through their own equation 2*s*lens < d."
  },
  {
   "quantity": "Number of glass elements / pixels covered",
   "claimed": "every one of ~74,000 glass pixels across four captures; three rectangles cover every glass element",
   "your_value": "Three rectangles miss a fourth glass element: a circular Live Text / scan button at rows ~1985-2095, x ~1030-1135, present in IMG_6645, IMG_6646 and IMG_6647 (~8,600 altered px each)",
   "why": "Diffing each capture against its target and subtracting their three rectangles plus the known non-glass bands (status bar, colour-managed target rows, Photos filmstrip rows 2150-2255) leaves ~9,400 altered pixels in three of the four captures. Visual inspection shows a genuine glass button with grid lines refracting through it. I measured it: chroma 0 at 8-bit and 10-bit, so the conclusion is unaffected, but the coverage claim is false as written."
  },
  {
   "quantity": "Material transmission, Today pill (light mode)",
   "claimed": "0.232",
   "your_value": "0.12 to 0.23, best estimate ~0.19; quoting 0.232 as a measurement is not supportable",
   "why": "Their two 'flat interior' windows (rows 210-222, x555-575 and x600-620) sit on a continuous intrinsic brightness ramp of the pill itself. At row 216 the profile climbs monotonically 160->194 across x540-584 with the backdrop constant black, and 226->243 across x587-640 with it constant white \u2014 0.3-0.8 levels/px that has nothing to do with backdrop transmission. Their toolbar figure (0.157; I get 0.145 from 184 over black vs 221 over white at y=2376) is sound because the periodic stripe backdrop cancels the intrinsic gradient."
  },
  {
   "quantity": "Rim pixels our default would make 'visibly' non-neutral",
   "claimed": "37,415 of 73,911",
   "your_value": "35,256 at chroma>=1, but only 19,758 at chroma>=4 and 18,529 at chroma>=8",
   "why": "The quoted count is the chroma>=1 count, which is the measurability threshold, not a visibility threshold \u2014 roughly 11,000 of those pixels carry a single level of chroma and are invisible. The underlying point (a real, visible fringe) survives at chroma>=8."
  },
  {
   "quantity": "Shader split formula",
   "claimed": "R-to-B sample separation = 2 * uAberration * bend * lens",
   "your_value": "2 * uAberration * bend^2 * lens",
   "why": "GlassShaderSource.kt: split = uAberration*bend, push = n*bend*lens, cr/cb = base + push*(1 -/+ split). Identical at bend=1, which is where they evaluated it, so the 1.512 px headline figure is correct \u2014 but the falloff away from the rim goes as bend^3 times the local gradient, not bend^2."
  },
  {
   "quantity": "Lens displacement used for the prediction",
   "claimed": "refractionDepth 14dp = 42 px at 3x, giving a single 1.51 px split",
   "your_value": "lens = uRefractDepth * mix(0.85,1.25,scale) * mat = 35.7 to 52.5 px, giving 1.29 to 1.89 px (peak fringe 29 to 43 levels)",
   "why": "The mix(0.85,1.25,scale) factor was silently taken as 1.0. Does not change the conclusion, but the prediction is a range, not a point."
  },
  {
   "quantity": "Identity of their edge #3 (y=2331, x951-961)",
   "claimed": "'the rim itself crossing a stripe', a refracted rim edge",
   "your_value": "the button's own drawn dark outline/shadow, not refracted backdrop",
   "why": "The profile values 11 and 100 at x=956-957 are the element's drawn boundary against the uncovered backdrop, which is bit-exact 255,255,255,0,0,0,0 in the target up to x=954. This is precisely the 'assuming a boundary is the element edge when it is a shadow' error. Harmless to the result only because every channel is bit-identical everywhere."
  }
 ],
 "confounds_missed": [
  "The captures are lossy HEIC, not PNG screenshots \u2014 the brief and their write-up both treat the .png files as the primary artefact and never state that they were converted from 10-bit HEVC. The project's own READ-ME-FIRST.md says 'Screenshot anything I measure a colour or a distance on. PNG, lossless, exact', and that instruction was not followed. A chroma-is-exactly-zero claim rests entirely on the codec not having quantised the chroma planes to flat, and they never checked. (I checked: 4:4:4 not 4:2:0, near-lossless luma, and 1,779 pixels in the same file carry 1-3 levels of 10-bit chroma more than 8 px from any saturated pixel \u2014 so the deadzone objection is closed in their favour, but they did not close it.)",
  "They left the 10-bit source samples unused. The HEIC is 10-bit and every 8-bit value spans all four of its 10-bit sub-levels, so two real bits of precision sit below everything they measured. The null holds at 10-bit \u2014 a 4x stronger result than the one they reported.",
  "Sub-LSB rounding statistics. Their detection floor treats 8-bit quantisation as a hard threshold on a single pixel, when with N~70,000 pixels a sub-LSB dispersion is detected stochastically at a rate proportional to d*gradient. This is what makes their bound ~15-30x too loose, and it is the same reason their 'empirical' and 'analytic' floors agreed \u2014 they are the same calculation.",
  "Coverage was asserted rather than derived. They chose three rectangles by eye and reported a percentage over them; a whole-frame difference against the target finds a fourth glass button in three of the four captures that no rectangle touches.",
  "Element-intrinsic brightness gradient. The Today pill has its own 0.3-0.8 levels/px internal ramp, which their transmission windows sit on and attribute to backdrop transmission. Reading transmission against a periodic backdrop (as they correctly did for the toolbar) is the method that avoids this; reading it across a single step does not.",
  "Scope. Two static UI states of one app (Photos), one glass variant (Regular chrome), at rest, every element over a neutral backdrop, and \u2014 as the project's own capture brief demands and this analysis never records \u2014 an unknown Liquid Glass transparency slider position. The headline generalises to 'the iOS 27 material' without qualification. Nothing here constrains the Clear variant, in-flight morph or drag states, or glass over saturated colour, which is where a dispersion term would most plausibly have been implemented."
 ]
}
```

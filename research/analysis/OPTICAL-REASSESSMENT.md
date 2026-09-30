Optical reassessment — 12 September 2026

The measurements justify a strong image-formation model. They do not yet justify a unique three-dimensional material. I would replace the independently fitted displacement, normal, dispersion and lighting functions with a constrained surface model wherever it survives, and represent layer selection and image enhancement explicitly. I would not promise that all three effects are one convex lens. The held-lens measurements already contradict that simplest interpretation.

This is a written assessment, not a new fitted model or a device-validation report. No implementation or analysis code was written or run for it. The only new artifact is this document. The numerical observations below are inherited measurements, except for explicitly identified algebraic deductions and a read-only ffprobe check.

I read HANDOFF.md first in full, followed by WHAT-WORKED.md, MODEL.md and all of FINDINGS.md, then CHECKPOINTS.md and the library's measured-model, limitations and roadmap documents. I inspected the existing fold and held-lens composites, the step tables, and selected measurement scripts. I searched the five named transcripts with bounded grep excerpts and read selected surrounding message objects; I did not load any transcript whole or enumerate the analysis directory. The transcript passages that explain the directional kernel and the confounded width estimate were particularly useful. No repository changes, git operations, builds, device operations or releases were performed.

**What I would keep as evidence.**

The cover-sheet correspondence table is the strongest optical evidence: known horizontal steps, both orientations of their image, repeated across target alignments and pull states. The folded text visible in analysis/COSG3073/lens_zoom.png supports the interpretation independently of the numerical tracker. Keep the state-binned observations and their confidence, rather than replacing them with the pooled curve.

The resting toolbar's angular stripe correlations are also valuable. Varying the normal direction around an end breaks much of the ambiguity of a periodic target. Keep the approximately 23 px source-depth plateau on R = 70 px ends, the return to identity around d = 32 px, and the reported spread below 2 px across the twelve measurements. These are correspondences; they are not measurements of thickness or refractive index.

Keep the tint-sweep output levels, spatial modulation readings, corrected outlines, held-bar growth and tone, selected-item scale, and release trajectory. The distinction between geometry, content scaling and material response is essential: the 1.05 bar growth and 1.18 selected-item growth must not be fitted again as optical magnification.

Keep the measured residuals as well. Several places in MODEL.md still retain superseded geometry, kernel widths and rim statements alongside their corrections. In particular, the newer 20–44 px kernel estimates supersede the window-limited 70 px estimate; FINDINGS 21 supersedes the old pill box; FINDINGS 22 supersedes FINDINGS 20's conclusion, but not the underlying missing-boundary observation. The documents are a research history, not a single internally consistent specification.

**The first correction: screen-space displacement can be real refraction.**

A planar backdrop viewed through a virtual transparent body ultimately produces a mapping from an output pixel p to a source coordinate q. Representing that mapping in screen coordinates is appropriate. What is missing is a derivation tying q to surface shape, source depth, both refractive interfaces, and the same normals used for reflection.

Take a normal cross-section. Let d increase inward from the outline, let the top surface have height h(d) above a flat lower interface, and place the source plane a distance g below that interface. Assume an orthographic camera looking down, air outside, a homogeneous refractive index n > 1, and a ray that exits through the flat bottom rather than a sidewall. For backwards tracing from the camera:

    alpha = atan(h'(d))
    beta  = asin(sin(alpha) / n)
    theta = alpha - beta
    gamma = asin(n sin(theta))
    s(d)  = d + h(d) tan(theta) + g tan(gamma)

The final equation applies only when the bottom transmission exists, |n sin(theta)| < 1, and the ray remains inside the body until that interface. Otherwise the path must be traced through the actual boundary, including reflection or another exit. Treating the single-interface deviation as the entire displacement misses this condition and the source-plane geometry. Snell's law, total internal reflection and Fresnel weighting are standard; the cross-section and inverse-model deductions here are my application to these captures. [Physical reference: PBRT, Specular Reflection and Transmission](https://pbr-book.org/4ed/Reflection_Models/Specular_Reflection_and_Transmission).

For small slopes this becomes:

    s - d ≈ (n - 1) [h/n + g] h'

In two dimensions, with constant g:

    q(p) ≈ p + gradient(Psi(p))
    Psi  = (n - 1) [h²/(2n) + g h]

This gives a useful intermediate model: an optical potential, with a displacement field that is the gradient of one scalar. Its corresponding three-dimensional top normal is normalize((-h_x, -h_y, 1)). A two-dimensional outline normal alone is insufficient.

There is also an identifiability limit. Different combinations of n, h, g and surface slope yield the same displacement. In one cross-section, integrating the measured displacement already constructs a potential; doing so is not evidence that the resulting glass is Apple's geometry. Fix an explicit index/depth convention or report parameter families. Do not label the fitted result a measured IOR or measured thickness.

**The fold is plausible optics; the held seam needs another explanation.**

Write the normal-direction Jacobian as J_n = ds/dd. A source feature's image magnification in this direction is 1/J_n where the inverse exists. Positive J_n below one stretches the image; J_n near zero gives the stationary source-depth region; negative J_n reverses orientation. The sheet's upright and inverted copies are therefore compatible with a single continuous ray map whose derivative changes sign. They do not require an independently blended mirrored image.

In the small-slope cross-section:

    J_n ≈ 1 + (n - 1) [(h/n + g) h'' + (h')²/n]

A sufficiently negative h'' at the shoulder can create the fold. Changing height, gap or shoulder curvature can weaken it into the resting plateau. This is a credible unification to test. A generic quadratic lens giving only constant magnification would not be enough; the transition to the flat interior matters.

The held fit has a different sign. In dimensional coordinates its outer band is:

    s = 1.3 d - 0.29 W,    0 <= d < 0.5 W
    s = d,                d >= 0.5 W

Thus s - d = 0.3 d - 0.29 W is negative throughout the outer band. For W around 65–68 px, the rim samples roughly 19–20 px outside the outline. In the simple top-cap model, h' >= 0, h >= 0 and g >= 0 imply nonnegative displacement on this transmitted branch. Increasing its strength cannot turn it into this held mapping. A different surface slope, lower interface, optical path, layer transform or compositor operation is necessary. A differently curved shoulder is not sufficient merely because it is called a meniscus; the complete ray path must actually produce the required sign.

There is a second, stronger distinction. At d = 0.5 W, the fitted source coordinate jumps from 0.36 W to 0.5 W: approximately 9–10 px. A smooth, single transmitted path through smooth surfaces, meeting the same source plane transversely, has a continuous source coordinate. A fold changes its derivative, not its continuity. A true jump requires a visibility/path/layer switch, a discontinuous surface, or an extremely compressed continuous interval unresolved by the measurement.

The held-band evidence has not yet distinguished those possibilities. The App Store linear fit's reported correlation is 0.42, and the missing gap comes from a composite scene with different layers. These warrant a targeted remeasurement before promoting the piecewise function to a physical law.

The apparent fusion of bar and lens establishes a smooth two-dimensional silhouette. It does not establish one three-dimensional height surface or one optical medium. The fact that the fused outline matched yet looked like the bar swelling is compatible with missing internal normals, reflection or layer boundaries. I would couple those to the surface before changing a correct silhouette to compensate.

**The directional kernel is a viable image filter, but not an established material property.**

FINDINGS 22 correctly rules out the particular isotropic, unwarped two-blur implementation at those samples. It does not uniquely establish a globally stationary horizontal high-pass filter.

The three observations are made at different geometric depths. The 8 px stripe band terminates at y = 2341, about 37 px below the toolbar's top edge at 2304. The black notch occupies y = 2422–2441, approximately 25–6 px inside its bottom edge at 2447. A sharp band termination at depth 37 does not establish the vertical response at depth 6–25. Also, vertical stripes retain their horizontal contrast under a vertical displacement as long as their source remains in the same stripe band. Their survival at depths 9–33 therefore does not rule out normal displacement.

Consequently, a fine component warped near the bottom edge, together with a broad component carrying tone, remains an unexcluded explanation. It has to predict the notch and the stripe-band termination simultaneously. A constant 10 px shift is not automatically sufficient, and an arbitrary per-row warp would simply repeat the original overfitting. The reported 10–12 px shift should become a joint position constraint, rather than being omitted while kernel width absorbs its effect.

There is a useful physical discriminator. The proposed operator

    K = G_wide_2D + w (G_fine - G_wide_x)

has negative spatial lobes for the reported narrow and wide scales: subtracting the broad, row-local average is sharpening, not just scattering. A fixed passive incoherent blur kernel has nonnegative weights. If this operator is verified, it is evidence for an image-processing or compositing stage. It cannot be explained solely by a positive mixture of blurred rays through ordinary glass. That would be a useful result, not a reason to hide the filter behind a physical name.

My first comparison would be between (A) positive wide/fine source filters with a constrained, geometry-dependent fine ray map and (B) the signed directional-detail operator. Fit gain, position and blur together, across the whole available local image, and compare on withheld captures. Keep (B) if it wins. Neither a surface story nor aesthetic discomfort with anisotropy should overrule a discriminating measurement.

Do not force physical radiometry onto the tone controls. Preserve their measured response curves as display-domain transfer functions until broader data separate opacity, lift and fine share. The toolbar stripe medians do not uniquely determine that decomposition, as FINDINGS 17 itself demonstrates. Likewise, the reported rejection of a 6 px pill kernel using toolbar stripes is conditional on the shared-kernel assumption, even though the toolbar was subsequently declared a different role. That rejection does not exclude every role-dependent or nonlinear alternative.

The PQ decode remains mandatory for the HDR recordings. It recovers the intended sRGB comparison image; it does not make sRGB code values linear radiance. A prospective physical light-transport calculation would operate in linear light and then pass through an explicit appearance/compositing transform before comparison. Merely changing all current blends to linear light is not a justified fix.

**Dispersion must distinguish source separation, image separation and rim colour.**

For a source landmark s*, differentiate s(d,n) = s*:

    delta_d ≈ -(partial s / partial n) delta_n / (partial s / partial d)

This is the relevant Jacobian. Near a fold, a small chromatic difference in source mapping can produce a large difference between displayed landmark positions. Where ds/dd is 1.3–1.6, as in the held band's fitted compression, the same source-coordinate separation becomes a smaller image-coordinate separation. The claim that compression by itself magnifies the separation is backwards under the spec's own coordinate convention.

A single wavelength-dependent n should predict landmark positions on both sides of a fold, with no arbitrary mirrored-zone gate. Very close to a fold, use the complete ray maps rather than this linearization. Some apparent confinement to one zone can arise from geometry, source texture, blur or detection thresholds; equal RGB values on flat white cannot establish absence of dispersion because moving a constant image changes nothing.

The bright rim over black is also not the same optical probe as transmitted text. Shifting a uniformly black source cannot generate a coloured bright rim. Its light must come from reflection, a separately drawn highlight, scattered light or another source contribution. The estimate 16 levels divided by 33 levels/px gives about 0.48 px, close to the quoted 0.45 px, but only as an equivalent shift under the assumption of equal-shaped, equally scaled channel profiles. It is not an independent measurement of transmitted dispersion.

The read-only ffprobe check confirms LOLL8185 is 1170x2532, 60 fps, yuv420p10le, PQ, BT.2020. Chroma is spatially subsampled. That does not invalidate a subpixel estimate, but its uncertainty must include the capture/reconstruction response, measured against suitable uncovered neutral edges. Agreement with a 0.006 shader knob is evidence that that knob controls the rendered fringe appropriately, not evidence for an optical dispersion constant.

I would therefore estimate transmission dispersion from achromatic source landmarks and assess rim chromaticity separately. Retain an empirical rim term if a shared physical reflection model does not explain it.

**Geometry, lighting and motion need fewer unsupported interpretations.**

For an end with circular radius R and a normal source map s(d), the tangential Jacobian is also constrained:

    J_t = (R - s)/(R - d)

At the resting plateau s about 23 px, R = 70 px and d = 6–16 px, this is roughly 0.73–0.87. Thus the same normal mapping predicts tangential stretching by about 1.15–1.36. Angular landmark spacing is an independent test, beyond the normal-depth table. More generally, derive both Jacobian directions from the actual surface and geometry.

The footprint of an output pixel maps into the source through this Jacobian. Large singular values require filtering along the compressed source direction. A small J_n at a fold is locally magnification, not automatically a demand for a wide normal blur. Near a turning point the footprint is nonlinear and needs more care than a single local Jacobian. Image brightness must not simply be multiplied by 1/|det J|: geometric magnification of a viewed texture is not an arbitrary caustic-brightness multiplier. [Sampling reference: PBRT, Texture Sampling and Antialiasing](https://pbr-book.org/4ed/Textures_and_Materials/Texture_Sampling_and_Antialiasing).

Fit the outline independently from the optical normal. A capsule can have exact circular ends; a continuous corner can use a smooth fitted curve, without claiming it is Apple's exact squircle. Compute distance and normals consistently from that geometry. A circular arc meeting its straight tangent has continuous normal direction, although curvature changes. Inflating a separate normal radius hides an implementation mismatch and is not a surface derivation. Cost on Android is a later device question, not something proved by analytic simplicity.

Fresnel reflectance alone cannot explain strong top and bottom highlights with weak sides under a rotationally symmetric environment and equal edge slopes. Use the same surface normal with a specified virtual illumination environment and roughness. Broad lights above and below are a plausible minimal candidate, not a recovered scene. Different role highlights can remain authored if one environment cannot explain them. Do not infer device-tilt behaviour from stationary screen recordings.

The outline's flattening with speed is an observation. Constant three-dimensional volume is not. In the main transcript near line 14385, the predecessor recognizes that the apparent width change is confounded by measuring the raised-edge region rather than the full lens. Moreover, even accurate two-dimensional area preservation would not establish volume without thickness. Retain the flattening curve as kinematics, with possible acceleration and history dependence; withdraw the volume assertion.

The reported one-frame content lag suggests a separate transform or captured layer, but one displacement sample does not identify a fixed frame delay. Test signed offset against signed velocity over reversals and stops; a fixed delay predicts offset approximately proportional to velocity. The measured 367 ms release span is useful, but it does not identify a unique spring or a fluid viscosity. Synthetic committed pulls and finger-tracked pulls must remain different state conditions.

**The replacement I would actually take forward.**

Use a shared image-formation framework with explicit inputs: source layers and their transforms; a surface with an outline, thickness profile and source depth; the ray mapping and its footprint; transmission/reflection; and the measured appearance transform. The cover sheet samples its wallpaper layer, the resting chrome samples its host backdrop, and the held selection may sample an already composited bar as well as raw content. Those source identities are part of the model, not incidental shader plumbing.

Start with a falsifiable null model: a smooth monotone top bevel joining a flat interior, a flat underside, nonnegative source gap, and a fixed index convention. Use a small shared normalized bevel profile, with role/state changes limited to scale, height and gap. Let a fold emerge from the ray map. Do not introduce independent per-depth corrections or a curvature gate merely to make it fit. A height field varying along the boundary is an extension only if independent angular measurements require it.

Fit the green-channel geometry on the sheet and resting ends first. Then require it to predict withheld step identities, orientations, arc spacing and states. This tests the proposed common origin of fold and resting plateau. If it requires unrelated normalized profiles for each role, the promised collapse has failed and should be reported as such.

For the held lens, test whether consistent source-layer coordinates remove the apparent negative displacement or gap. If they do not, reject the simple convex-cap null model for that effect. Add a curved underside or additional ray branch only when the measured sign, visibility and reflection support it. If an explicit band/interior composite explains the data with fewer unsupported freedoms, use that. One framework can describe several compositing roles without pretending that they are one optical surface.

Separately compare the two kernel hypotheses described above. Retain appearance, accessibility tint and authored illumination as empirical controls where they are identifiable only as output responses. This is a constrained generative model, not an attempt to recreate every UI colour with bulk glass absorption.

**What existing data can confirm or kill it.**

| Test | Existing evidence | Prediction and rejection criterion |
| --- | --- | --- |
| Shared fold/rest surface | COSG3073 steps_split.md and the nine tracked steps; C9 tint pulls at matched states; C1a resting ends in FINDINGS 18 | A shared bevel law with limited state parameters predicts position, reversal and recovery to identity. Reject if it matches only by per-step/per-depth corrections, or fails withheld captures beyond registration and tracking uncertainty. |
| A continuous held ray path | LOLL8185 1700–1780, Calls tip, App Store 2185–2600, and mid_1440_2x.png | Track neutral and nonperiodic landmarks through the proposed seam after accounting for 1.05 bar growth and 1.18 item growth. A persistent approximately 0.14 W source jump kills the smooth single-branch model. Resolving a narrow continuous interval weakens the path-switch explanation. |
| Source-layer selection | Held Phone and App Store bar edges, glyphs, background and the already composited bar | One source transform must explain multiple landmark classes. Distinct motion/tone for glyphs and backdrop supports separate layers. Do not infer rawShare = 0.11 uniquely from two scene averages. |
| Warped fine detail versus directional high-pass | Four Photos stills and C1a sweep; the y = 2341 termination and y = 2422–2441 notch together; potentially the small Photos movements at COSG3073 15900–21299 after checking registration | A constrained ray map plus positive kernels must jointly predict modulation, phase, band termination and notch. If it cannot, while the signed filter predicts withheld placements, retain the image-filter explanation. A persistently missing step when geometry predicts identity kills the simple warp explanation. |
| Full two-dimensional normal field | C1a angular end samples; grid/text/checker crossings in COSG3073; moving icons/folders in LOLL8185 and LQCO6923 | Predicted normal and tangential landmark spacing must agree. The optical-potential approximation requires an approximately curl-free displacement after removing source transforms. Failure rejects that approximation, though not every possible thick-lens model. |
| Straight-edge displacement | The bottom-notch fit; dock f2300 boundary; sheet step crossings | Explain actual normal phase/landmark positions. Unchanged vertical stripes alone are no test of vertical displacement. The dock boundary constrains its particular depth and role, not every straight edge. |
| One transmission dispersion law | RGB step tracks on both branches of COSG3073, neutral edge controls outside glass, held bar-edge copies | With geometry fixed, one n-versus-wavelength relation should predict sign and branch-dependent separation. Failure outside codec/registration uncertainty rejects that law or its assumed path, rather than being repaired by a zone-specific RGB offset. |
| Surface-linked rim | Corrected FINDINGS 21 profiles, C1a tint sweep and held black-background frames | One surface/environment hypothesis must predict rim position and angular falloff alongside transmission. A fit to peak brightness alone is insufficient; colour on black is evaluated as reflection/highlight, not transmitted black. |
| Scale law | Existing icon/folder observations and R = 70, 82–92, 126, 133–151 px cases | Re-register the unreliable dock reference before using it. W = 0.6 R is a prior to test, not settled universality: the quoted dock W = 50 at R = 122 is already about 0.41. |
| State and temporal response | Four Phone press/drag cycles and frame 1944 release; four COSG3073 pulls and C9 | Delay should follow signed velocity if it is a capture lag; compare reversals and stops. Match pull position and interaction state before attributing geometry to speed or tint. Width/height data can test area trends, not recover volume. |

Use independent outline registration, glyph masks and the uncovered capture response as nuisance calibration. Fit on entire captures or passes and withhold others; random neighbouring pixels are not independent validation. Report landmark residuals, phase, orientation and tonal residuals separately. The existing rest-lens repeatability below 2 px and the recorded image residuals supply baselines, but the 0.4-level fit to a selected notch profile is not an uncertainty bound on the entire material.

**Which questions actually need new measurements.**

The first discriminating round above needs no new capture. Reanalysis can substantially strengthen or kill the proposed model with the data already present. Since the handoff says no further captures are expected, the following remain explicit limitations rather than prerequisites for proceeding:

| Missing discriminant | New measurement that would resolve it | What can be said without it |
| --- | --- | --- |
| Screen-horizontal versus element-aligned filtering | The same elongated glass role at 0 and 90 degrees over the same registered two-dimensional target, controlling state and layout | Current horizontal bars do not distinguish those axes. Round buttons may add local directional evidence but do not settle the long-axis rule. |
| Stationary kernel versus depth-dependent warp | Translate a neutral nonperiodic pattern and orthogonal step/stripe probes through the same fixed element at matched depths | Existing different-depth probes restrict the alternatives but may not identify one uniquely. Existing Photos motion is a possible partial substitute only after registration is verified. |
| Unique toolbar tone/detail decomposition | Same toolbar over uniform neutral fields at several luminances, with a controlled surrounding region, through the tint sweep | Keep effective gain/offset and modulation response; do not present opacity and fine share as independently measured. |
| Absolute virtual index, thickness and depth | Known changes to virtual source depth or camera/view geometry, if a controlled test environment can expose them | More ordinary head-on screenshots cannot uniquely recover all three. Moving a physical camera around a flat display does not change the shader's virtual view. Choose a modeling convention. |
| Held-lens transmission dispersion independent of codec | Lossless held-state captures over sharp neutral landmarks and controls | Current footage supports a visible fringe and approximate correspondence, with codec-aware uncertainty; it does not establish a precise material dispersion constant. |
| Tilt-sensitive reflection | Controlled device-orientation sequence with fixed content and interface state, captured digitally | Any tilt response is unmeasured. An external photograph alone also adds real display reflections and viewing-angle changes. |
| Light held state and unconfounded rise | Light-appearance held tab bar; stationary press on the already-selected tab, without dragging | Infer only as a declared design choice. The dark release measurement is stronger than the rise evidence. |

My recommended first action after this assessment is the joint geometry/visibility reanalysis, followed by the kernel comparison. A successful replacement must predict something the old fit did not consume. Only after that would I change the Kotlin library, publish its snapshot locally for Vitals, and compare the actual Android rendering on Galaxy S24+ transport 4. No claim that the proposed model works on that device is made here.

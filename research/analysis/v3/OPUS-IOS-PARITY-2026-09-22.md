# iOS glass interaction parity — implementation brief for Opus 5 High

2026-09-22. Owner: Shaya. This is a proposed implementation and verification model, not a recovered Apple implementation.

## 1. Outcome and authority

Implement and deliver a visibly closer, continuously deforming glass interaction. The owner rejected the previous motion despite passing tests. These owner observations override incompatible requirements in V3-MODEL.md, the final-polish brief and the owner-repair checkpoint:

1. Holding glass makes it protrude beyond its resting container.
2. A held body stretches and squeezes toward an arbitrary two-dimensional finger movement.
3. Release is light and springy; translation and shape recover continuously together.
4. Short taps, long taps, held movement and intermediate silhouettes each matter.
5. Native icons and labels retain sharp source detail in every state, while allowing the strong local optical stretching and colour separation actually visible at the rim. “Clear” does not mean unwarped or readable at every boundary pixel.

You own implementation, comparison, bounded revisions, tests and physical verification. Work as ONE agent. Do not stop to request another Astra ruling or produce another plan instead of code.

The target is 1:1 appearance and motion on the available references. Do not promise that result before comparison. Distinguish matched observations, authored behaviour where the data lack information, and failed gates. A still-image sequence cannot identify a unique dynamic system. No spring constant in this document is a measured Apple constant.

The architecture below is an observable low-dimensional shape model, not a fluid simulation or a claim about Apple's internals. Its job is to reproduce the visible response across gestures, including frames not fitted. Do not conceal structural failure with per-gesture constants.

No commits, pushes, tags, releases or remote publication. Local Maven publication for the review app is authorized.

## 2. Workspace and existing work

Library:
D:\Work\AndroidStudioProjects\SideProjects\kmp\liquidglass

App:
D:\Work\AndroidStudioProjects\SideProjects\kmp\Vitals

Research:
D:\Work\AndroidStudioProjects\SideProjects\kmp\liquidglass\research

Do not recreate the obsolete D:\liquidglass-captures tree.

Expected branches:
- research/ios27-measurements in the library
- feat/liquidglass-0.2-measured in Vitals

Last verified HEADs: aa9e97e / 7d60cd3. Both repositories have extensive authorized uncommitted work. Inspect current state, enter the proper repo before git commands, and preserve it. No reset, restore, clean, wholesale replacement or invented release.

Preserve Legacy as the public default and the measured resting-map work. Implement this interaction change on the opt-in V3 path. Vitals consumes the library; do not copy the glass implementation into Vitals.

Primary commonMain files under com/wexpa/liquidglass:
- GlassSelectorDynamics.kt
- GlassSelectorGeometry.kt
- GlassSelectorConstraints.kt
- GlassTabBarV3.kt
- GlassTabBar.kt
- GlassBodySource.kt
- GlassShaderSource.kt
- GlassContentShaderSource.kt
- GlassContainerShaderSource.kt
- GlassNormalSource.kt
- GlassSourceMapSource.kt
- GlassEndpointShaderSource.kt

Inspect actual signatures before adapting them. This document names responsibilities, not imaginary existing methods. Read only required dependencies, not the full corpus.

Evidence:
research/analysis/v3/owner-repair/CHECKPOINT.md
research/analysis/v3/owner-repair/pixel10/VERIFICATION.md
research/analysis/v3/owner-repair/pixel10/before/
research/analysis/v3/owner-repair/pixel10/after/
research/analysis/v3/owner-repair/pixel10/additional/
research/analysis/v3/owner-repair/pixel10/retarget-fixed/

Saved owner-repair APK:
research/analysis/v3/owner-repair/vitals-owner-repair.apk
SHA256 73050254C86A1FBC7A3EC2AAB9A77C387E153D261BC20FD08E18597DCC3C9FA9

Previous final-polish APK:
research/analysis/v3/final-polish/apk/vitals-v3.apk
SHA256 B1E80BACC0188D0A540000B3409DE04CF0D7B03ECD03E86D5E1E62B448CCD1C8

Physical Pixel 10 last verified:
- serial 56071FDCR00C4Y; adb localhost:58657
- Android 16/API 36, product frankel
- portrait 1080×2424, density 420
- 60 Hz reported in the last session
- owner-repair installed APK pulled back hash-identical.

Rediscover connectivity. Ports and transports change. Do not use the separately connected emulator or Huawei as physical parity evidence. Xiaomi/Pixel 7 are authorized alternatives when available; compare baseline and candidate on the SAME phone.

Tools:
- adb: D:\DevSetup\Android\Sdk\platform-tools\adb.exe
- Python: C:\Users\shaya\AppData\Local\Programs\Python\Python312\python.exe
- ffmpeg: C:\Users\shaya\AppData\Local\Programs\Stremio\ffmpeg.exe
- JDK: C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot
- GRADLE_USER_HOME: D:\DevSetup\.gradle
- Vitals module: :androidApp

Scratch/media belong on D:. Do not enumerate the huge analysis tree. Grep transcripts; never load an entire large JSONL.

For original HDR PQ iPhone videos, keep the established decode:
    zscale=t=linear:npl=100,format=gbrpf32le,zscale=p=bt709:t=iec61966-2-1:m=bt709:r=full,format=rgb24

Do not apply it automatically to Android screenrecord or already converted PNGs. Respect HEIC colour profiles/auxiliary HDR handling. Geometry-only display crops are not calibrated photometry.

## 3. Preserve real fixes; replace failed assumptions

KEEP:
- Double absolute pointer timestamps using /1000.0, narrowing only elapsed time after subtraction. Float seconds caused 125 ms quantization at approximately twenty days of uptime.
- Corrected coordinate ownership, node lifetime, cancellation, one selection callback and actual production gesture tests.
- Whole-pixel padding and half-resolution field-mask repair.
- Endpoint intent independent of visible body width.
- Native-ink separation and endpoint compositing, with its unresolved error disclosed.
- Conservative recording extent until a replacement passes touch-image and device checks.
- Existing Legacy and measured-rest outputs.

SUPERSEDE as universal requirements:
- Hard 1.9-slot ceiling on the complete held and strained silhouette.
- Held containment inside the resting bar.
- Horizontal-only movement with minor radius/skew adjustments.
- The critically damped 10/s release as an Apple-like final law.
- “Never encloses three icon centres” as a universal rule for every extreme pull.
- Full enclosure of all padded raw label rectangles as mandatory.
- Passing 176 JVM tests as proof of iOS similarity.

The last width repair addressed real runaway accommodation. Keep that intent: expansion must not recursively recruit more labels. Replace the broad clamp with separate pressure, accommodation and mechanical strain.

Do not withdraw real input/coordinate bugs because the first visual correction was insufficient.

Unresolved historical results:
- Endpoint compositor per-pass 0.988 / 0.957 levels; end-to-end 1.426 against 1.
- Earlier timing median +6 ms against +2; p95 ratio 1.30 against 1.10.
- These are not Pixel 10 timing measurements.
- The latest physical replay verified bounded motion and selection, not Apple parity.
- The owner has rejected its remaining feel and silhouette.

## 4. Reference packet and registration

Original Apple group already located under:
research/datasets/ios27-phone/iphone's-ref-bubble/

Resolve exact paths through existing records if migration differs; do not scan terabytes.

Use:
- Rest/short move: IMG_6699, IMG_6700, IMG_6701.
- Long tap: IMG_6690 → IMG_6698 → IMG_6700 / IMG_6695 / IMG_6693 / IMG_6694 → IMG_6701.
- Held/accommodation: IMG_6717, IMG_6718, IMG_6719, IMG_6721.
- Strong held movement: IMG_6734, IMG_6735.
- Ordered edge release: IMG_6724, IMG_6727, IMG_6728, IMG_6729, IMG_6730, IMG_6731, IMG_6732, IMG_6733.

IMG_6685/6686/6687 were Android optical examples, not Apple motion ground truth. Android IMG_6705…IMG_6711 show the rejected behaviour.

Prepared geometry crops:
research/analysis/v3/owner-repair/apple_*_display.png

Existing approximate manual readings, NOT final fit targets:
- 6717/6719: width ≈1.38 slots; height ≈1.13 times bar height.
- 6721: width ≈1.73 slots; label still refracts at the rim. The earlier almost-unchanged-centre observation refers to the preceding overlap state, not to 6717; do not treat 6717→6721 as a stationary-finger pair.
- 6734: width ≈1.55 slots; height ≈0.95 bar height.
- 6735: width ≈1.51 slots; height ≈0.79 bar height.
- 6698: width ≈1.58 slots; height ≈0.94 bar height.
- 6727→6728→6729→6730: widths ≈308→295→289→265 px; centres ≈290→239→230→207 px.
- Uncertainty about ±3–5 px. These do not supply velocities, precise extrema or spring constants.

For temporal identification, follow E7/E7b provenance to the continuous recordings. Use native presentation timestamps and empirical frame maps. CFR-60 labels are not automatically native frame IDs. The corrected 313.8 ms was a raised-area duration, not a width-recovery measurement. The old 367 ms assertion is superseded.

Create a SMALL reference ledger: exact file/native time, contour convention, alignment, visibility mask, uncertainty. Split by whole gesture/clip before fitting. Adjacent frames and near-duplicate stills are not independent holdouts.

Build a matched Android fixture: corresponding bar dimensions, item centres, label/icon extents, starting item and destination. Different Vitals and WhatsApp text cannot be meaningfully compared with full-image pixel RMS.

Register to the stable bar/scene, never to the moving bubble. Use one global scale/rigid alignment per clip; measure parent-bar deformation separately when present. No per-frame anisotropic registration that erases shape errors.

Inspect native full-speed footage AND active frame strips. Do not sample percentages dominated by idle time.

### 4.1 Final visual audit: exactly three inspected iOS references

The final edit was informed by viewing these existing geometry/display crops:
- research/analysis/v3/owner-repair/apple_6717_display.png
- research/analysis/v3/owner-repair/apple_6721_display.png
- research/analysis/v3/owner-repair/apple_6735_display.png

These are visual observations, not a fresh calibrated measurement or timing fit:

| Reference | What is visible | Implementation consequence |
|---|---|---|
| 6717, initial held Chats | The body crosses the bar's top and bottom modestly, remains approximately vertically centred, and has rounded ends with dark/chromatic bands near its upper/lower edge. Its central white icon and label are sharp and magnified. | Permit pressure expansion around the body; do not implement holding as a mandatory upward translation, enormous floating sphere, or an opaque white fill. Protrusion is modest, not unbounded. |
| 6721, expanded Communities/Chats overlap | Greater width with nearly the same overall height as 6717. The body retains rounded ends and a longer central span. The left label crosses the rim; bright coloured distortions follow nearby white content at the side edges. | Test cap-preserving spine extension before assuming uniform horizontal scaling. Raw-label enclosure is not mandatory. Preserve sharp interior ink AND boundary distortion. |
| 6735, hard pull at the right end | A markedly flatter, wider-than-tall body lies mostly inside the bar's vertical envelope despite being described by the owner as held. The portrait remains recognizable; the lower “You” text is strongly elongated into bright vertical features. | A held flag must not force a minimum raised height or permanent protrusion. Strong motion can squeeze the body back inside. Do not restore normal glyph shapes with an unwarped overlay, and do not replace optical streaking with blur. |

Confidence is high in protrusion/flattening and the distinction between central clarity and edge distortion; moderate in the preference for cap-preserving width growth until contour annotation. The three stills do not establish spring frequency, damping, finger lag, a temporal growth law, or diagonal response. Retain the owner's diagonal requirement, but label its symmetry-based generalization authored until temporal references support it.

6717 and 6721 occupy different horizontal positions: they must not be overlaid as if the finger never moved between them. Use 6719 and the continuous provenance when testing stationary attachment. Each selected still is now a design reference, not an untouched final holdout.

The coloured features do not identify physical dispersion, Fresnel reflection, or a particular refractive index. Their location changes with the underlying content. A fixed rainbow outline is not an acceptable substitute for the observed response; dark upper/lower bands must not be interpreted as holes in coverage without source evidence.

## 5. One body with changing external conditions

Keep one persistent state. Do not exchange capsules, nodes, springs, source layers or coordinate owners at mode transitions.

Modes:
1. Rest: rest pose, no grasp.
2. Tap transit: new selected item; motion excites deformation.
3. Press pending: existing gesture arbitration; no premature selection callback.
4. Held: pointer owns a material point; body can protrude.
5. Released from hold: preserve current shape/velocity and recover toward selection.
6. Cancelled: same continuous recovery toward the latest legitimate selection, without a new commit.

Keep the tested cancellation semantics. A second finger, consumption, vertical takeover or disposal is not a successful release.

Separate navigation intent from visible geometry. The first may stay within the item sequence while the second protrudes, tapers or rises. Never clamp intent by the body's half-width.

### 5.1 Containment

- Unpressed rest and ordinary tap transit remain inside the appropriate current bar envelope.
- Press growth and held drag are NOT constrained by the resting-bar outline.
- Genuine held release starts from its actual outside shape. Do not substitute a smaller feasible region on UP.
- Reacquire resting contact only after the body is inside a margin and its next predicted step is feasible.
- Re-grab replaces recovery/contact ownership, including stale shrinking walls.
- A finite texture/screen allocation is a safety limit, not a material wall. Enlarge source allocation when needed; do not shrink the body to hide missing pixels.

Free-held means permission to protrude, not a requirement to protrude in every held frame. Calm 6717/6721 are taller than the bar; moving 6735 is shorter. Remove any mode-only minimum-height or always-raised constraint that prevents this transition. Pressure expansion alone must not add an arbitrary vertical centre offset.

Audit parent clips, rounded-bar masks, fused-union eligibility and graphics-layer bounds. A correct controller can still be clipped by rendering.

A pressable bar/plate owns its own pressure transform. Do not apply that scale a second time to the selector. Other pressable glass surfaces can reuse the pressure component without inheriting tab selection logic.

## 6. Compact 2D geometry with an analytic inverse

Replace the horizontal-only visible-body restriction with an affine body plus bounded taper. The uniform accommodation map C below is candidate A; section 6.5 supplies the cap-preserving candidate B motivated by the inspected stills. Select between them with the cheap static contour test BEFORE building out motion tuning. Retain the existing path behind comparison until the selected model passes.

Canonical reference capsule:
    q = (qx,qy)
    d0(q) = length(q - (clamp(qx,-a,a),0)) - r
    B0 = {q: d0(q)<=0}
    a>=0, r>0, R=a+r

Reference full width W0=2(a+r); height H0=2r. Use the established resting-selector dimensions. The reference stays fixed during an equal-slot Vitals gesture. Layout changes follow continuous rebase.

Rendered body:
    C = diag(exp(w),1)
    z = C q
    F_k(z) = (1 + dot(k,z)/R) z

    M = [u, v; v, -u]
    A = exp(p I + M)

    x = c + A F_k(C q)

Eight scalar shape/position states and their velocities:
- c: 2D reference position.
- p: isotropic log expansion.
- u,v: trace-free directional strain.
- kx,ky: directional taper.
- w: horizontal content accommodation.

Selection and mode are separate discrete state.

p expands both axes; u,v stretch along arbitrary directions and squeeze perpendicularly; k differentiates leading/trailing ends; w permits stationary accommodation. c is the material origin, not necessarily the area centroid when k≠0. Measure rendered centroid separately.

The trace-free strain preserves area in isolation. This is a modelling decomposition, NOT evidence of projected-area conservation. p may vary with pressure/speed; thickness is unobserved. Do not impose a fictitious volume law.

### 6.1 CPU matrix evaluation

Let rho=sqrt(u²+v²), H=cosh(rho), S=sinh(rho)/rho:
    A = exp(p)*(H I + S M)
    A_inverse = exp(-p)*(H I - S M)

Near zero:
    H = 1 + rho²/2 + rho⁴/24
    S = 1 + rho²/6 + rho⁴/120

No angle/eigenvector state: no direction flip at zero speed or reversal. Compute once per frame.

### 6.2 Stable inverse

Given x:
    y = A_inverse*(x-c)
    b = k/R
    h = dot(b,y)
    lambda = 2/(1+sqrt(1+4h))
    z = lambda*y
    q = C_inverse*z

Derivation: y=(1+dot(b,z))*z, so z=lambda*y and h*lambda²+lambda=1. The selected root is continuous at h=0 and avoids cancellation.

Require over the canonical body:
    abs(dot(k,Cq)/R) <= eta

Start eta<=0.18. Conservative CPU enforcement:
    length(k)*max_q(length(Cq))/R <= eta

Then:
    s = 1+dot(b,z)
    DF_k(z) = s I + z*b_transpose
    det(DF_k) = (1+dot(b,z))*(1+2dot(b,z)) > 0

The deformation is invertible within the body. Optical source folds are separate.

Early-out outside conservative body bounds. A negative inverse discriminant outside the body means outside coverage, not NaN; assert it never occurs on/in valid body samples.

### 6.3 Normals and distance

    J = A * DF_k(Cq) * C
    gradient = inverse(transpose(J))*n0
    n_world = gradient/length(gradient)

Use ONE shared geometry source in panel/content/container, including the eligibility policy. Do not repeat their old normal discrepancy.

Near the boundary:
    coverageDistance ≈ d0(q)/length(gradient)

This is a first-order normal-corrected distance. It is NOT an exact deep Euclidean SDF. Keep separate names for coverageDistance and opticalCoordinate. Do not feed this approximation blindly into the measured deep source profile.

At A=I, C=I, k=0, recover the existing canonical path exactly. Initially transport the established canonical optical map consistently through the new shape and score the result. If evidence demands exact world depth, evaluate/validate that separately.

Handle capsule medial-axis normals safely. Rim effects must be zero there. No arbitrary interior normal may create a seam.

### 6.4 Geometry gate before motion fitting

Prove:
- identity preservation;
- inverse round-trip <=0.05 device px;
- transformed normal versus independent finite differences <=0.5 degrees away from declared singular locations;
- no NaNs, self-intersection or nonpositive determinant;
- correct directional tensor/taper response for horizontal, vertical and diagonal inputs;
- shared-path source-coordinate agreement under the existing strict gate;
- physical protrusion survives all later clips.

If the basis cannot fit static contours, do not tune springs to compensate. Follow section 15.

### 6.5 Cap-preserving accommodation: compare before committing to C

Horizontal affine scaling enlarges the horizontal radius of the old end caps along with the centre span. The inspected 6717/6721 shapes instead suggest added centre span at approximately stable height and cap scale. This is not yet a precise radius measurement, so test both explanations rather than asserting either is proven.

For a>0, define a second material map with added half-spine length ell>=0:

    phi(qx) = clamp(qx/a,-1,1)
    Q_ell(q) = (qx + ell*phi(qx), qy)

The centre strip stretches horizontally; each cap translates outward without being widened. Its image is exactly a capsule with half-spine a+ell and radius r. Width increases by 2*ell, height stays 2*r before the common A/taper transform.

Candidate B substitutes Q_ell for Cq everywhere:

    x = c + A*F_k(Q_ell(q))

The quadratic inverse for F_k is unchanged. After obtaining z, recover material q using:

    if abs(zx) <= a+ell:
        qx = zx*a/(a+ell)
    else:
        qx = zx-sign(zx)*ell
    qy = zy

For coverage, evaluate the expanded capsule DIRECTLY in z:

    d_ell(z) = length(z-(clamp(zx,-(a+ell),a+ell),0))-r
    J_geometry = A*DF_k(z)
    gradient = inverse(transpose(J_geometry))*n_ell
    coverageDistance ≈ d_ell(z)/length(gradient)

n_ell is that expanded capsule's canonical normal. This avoids feeding the piecewise material stretch derivative into boundary lighting. Both expressions agree on the actual contour; check the cap/spine joins independently for seams.

For the fixed material grasp point:

    zg = Q_ell(qg)
    zg_dot = (ell_dot*phi(qg.x),0)

Use those values in section 7's SAME c and c_dot equations. The state ell replaces w; there are still eight scalar pose states. ell is a length in the chosen design units, not a dimensionless log scale. Bounds/uniforms use a+ell; the invertibility guard uses max length(Q_ell(q)). At ell=0 the original reference body is recovered exactly.

Do not divide by a for circular reference bodies. For a below the declared geometry epsilon, retain candidate A for accommodation (or keep accommodation zero for a circle that needs none); pressure/2D strain still operate. Keep that explicit compatibility branch tested, not a hidden epsilon that changes resting coverage.

Static decision procedure:
1. Annotate 6717 and 6721 using the same outside-to-inside convention. Register their parent bar independently of the moving body; translation is a nuisance parameter, not a claim of stationary grasp.
2. Compare common-height/cap-profile residuals for A and B. At calm held design anchors start with M=k=0 and shared pressure; do not let p,u,w trade off to make uniform scaling unidentifiable. Allow additional asymmetry only when it exceeds annotation uncertainty.
3. Apply the section 14 contour gates to both. If A fails at the caps while B passes, adopt B. If A passes and B fails, adopt A. If both pass without a material residual difference, prefer B for this nav-bar accommodation because it adds span without unnecessarily altering end curvature. If neither passes, use section 15's bounded shape fallback before dynamics.
4. Freeze the choice and static parameters before temporal fitting. For B, section 9's one-dimensional accommodation search is over ell instead of w, and section 12's uniforms carry a+ell rather than C_inverse. For a desired added width dW, ell=dW/2 before A/taper; solve against the full transformed contour when those are active.

This test replaces an assumption within the planned static packet; it is not authorization for another broad research round. Static outlines alone do not independently identify all of p, horizontal strain and accommodation: declare the calm-state gauge and use moving trajectories to constrain strain.

## 7. Grasp: attach a material point, in two dimensions

At actual grasp acquisition, store qg from the inverse map of the touched point. Do this at real ownership acquisition, not indiscriminately on every DOWN.

For acquisition outside the current body, preserve the existing resolvePress/takeover semantics. Choose a point on the current body and initialize its rendered grasp position there, approaching the pointer continuously. Do not fabricate a remote material point or teleport the selector.

Maintain g, the rendered grasp position, and its velocity. It may track the raw finger through one short measured response. It must not be a slow filter followed by another slow centre spring.

While held:
    c = g - A*F_k(C*qg)

Stationary growth therefore preserves the touched material point. Growing around a frozen centre would make glass slide under a stationary finger.

Release velocity must differentiate the same map:
    zg = C*qg
    zg_dot = C_dot*qg

    shape_g_dot =
        A_dot*F_k(zg)
        + A*[DF_k(zg)*zg_dot + (dot(k_dot,zg)/R)*zg]

    c_dot = g_dot - shape_g_dot

Do not set c_dot=g_dot while shape changes: that injects a release kick.

Matrices generally do not commute. A_dot is not simply A*M_dot.

With T=u*u_dot+v*v_dot:
    rho_dot = T/rho
    H_dot = sinh(rho)*rho_dot
    S_dot = (rho*cosh(rho)-sinh(rho))/rho²*rho_dot

    A_dot = exp(p)*[
        p_dot*(H I+S M) + H_dot I + S_dot M + S M_dot
    ]

Near zero:
    H_dot = T*(1+rho²/6+rho⁴/120)
    S_dot = T*(1/3+rho²/30+rho⁴/840)

    C_dot = diag(exp(w)*w_dot,0)

All velocities use seconds.

When parent world mapping is world=o+P*x:
    worldVelocity = o_dot + P_dot*x + P*x_dot

Convert finger positions into the CURRENT frame and include parent derivatives at release/rebase. Do not misinterpret parent expansion as fresh finger acceleration. Inertial excitation comes from actual pointer history expressed consistently; attachment uses the complete frame kinematics.

Required regression: fixed world-space finger while parent scale, body pressure and accommodation change.

## 8. Dynamics: distance matters through the trajectory

Use one centre response for tap transit. Longer travel naturally creates more velocity/acceleration and therefore more strain; nearby taps create less. No separate screenshot-shaped timelines for short and long jumps.

For each scalar z about its equilibrium:
    z_ddot + 2*zeta*omega*z_dot + omega²*(z-z_eq) = forcing

Use verified closed-form spring integration for a locally constant target, or an equivalent integrator. Preserve velocities at transitions. No frame-count dynamics.

For homogeneous error e=z-z_eq, timestep h:
    beta=zeta*omega
    wd=omega*sqrt(1-zeta²)
    decay=exp(-beta*h)

Underdamped:
    e_new=decay*(e*cos(wd*h)+(v+beta*e)*sin(wd*h)/wd)
    v_new=decay*(v*cos(wd*h)-(beta*v+omega²*e)*sin(wd*h)/wd)

Critically damped:
    e_new=exp(-omega*h)*((1+omega*h)*e+h*v)
    v_new=exp(-omega*h)*((1-omega*h)*v-omega²*h*e)

Use stable real-root/matrix-exponential integration for overdamping. Check the near-critical limit. Moving targets and contact require substeps over real elapsed time. Do not discard elapsed intervals; preserve the established suspend/resume policy.

### 8.1 Smooth shared deformation targets

In common design units, let v be centre velocity during tap transit, or actual finger/grasp velocity while held. Let a be filtered acceleration. V is a shared reference speed.

    sv = |v|²/(V²+|v|²)
    Tv = (v*v_transpose - 0.5*|v|²*I)/(V²+|v|²)

    M_eq = 2*Kstretch*Tv

This is smooth at zero speed and extends along movement with perpendicular compression.

Pressure:
    p_eq = Pheld*pressureProgress - Psqueeze*sv

Psqueeze permits changing projected area at strong pull. Keep it zero if validation cannot establish a benefit.

Taper:
    k_eq = Ka*a/(Ascale+|a|) + Kv*v/(V+|v|)

Determine Ka/Kv signs from leading/trailing contour evidence. Do not assume which end is larger. Enforce invertibility continuously.

Filter acceleration in seconds, not event count. Avoid differentiating a noisy one-event velocity. Reuse the corrected tracker where useful and disclose filter latency. Do not create duplicate smoothing.

On genuine held release:
- preserve every pose coordinate and velocity;
- shift equilibria toward the selected rest body;
- stop feeding recovery centre speed into NEW strain;
- retain existing deformation velocity for measured rebound;
- keep the current outside body free of resting walls.

This prevents a moving centre from re-inflating the recovering body. It does not force all modes into the same heavy critical spring.

### 8.2 Formation on taps

A tap-transiting selector needs a motion-dependent material/shape response even if it never became a long-held gesture. A black resting pill translating unchanged is not sufficient evidence of long-tap parity.

Use a continuous formation state driven by press and transit motion, for example:
    formTarget = 1-(1-pressResponse)*(1-Kmove*sv)

Kmove is shared across distances and fitted/validated. Quick release must not instantly erase formation. Nor should any nonzero movement instantly enable maximum held formation.

Keep actual shape in the single body state. Formation may modulate established material behaviour, not exchange incompatible silhouettes or coordinate frames. Preserve native ink.

### 8.3 Initialization, not recovered physics

Starting values and bounded fitting ranges:
- Compute calm held pressure from the measured HEIGHT RATIO correctly:
      Pheld_start = log(Hheld/H0)
  If Hheld≈1.13*Hbar, use log(1.13*Hbar/H0), NOT log(1.13) unless H0=Hbar.
  Account separately for parent scaling, strain and taper during that reading.
- Calm held height initially bracket 1.06…1.22 of bar height, subject to source annotation.
- That height bracket applies ONLY to calm held pressure. The inspected hard-held 6735 is approximately 0.79 bar-height. Do not clamp the final held height to the calm bracket or assume conserved projected area. Fit the pressure/strain response jointly enough to allow the observed flattened state.
- Strain rho initially up to about 0.35; exploratory safety limit 0.5.
- Taper eta initially <=0.18; do not exceed 0.25 without re-proving geometry quality.
- Accommodation w initially 0…log(1.35); revise only from declared reference evidence.
- Centre/release frequencies: start ~24/s; search 16…55/s.
- Shape frequencies: start ~30/s; search 16…65/s.
- Pressure/accommodation frequencies: start ~28/s; search 16…60/s.
- Damping: start ~0.8; search 0.55…1.15.
- Grasp tracking if required: critical and fast, e.g. 60…100/s. Remove redundant filters first.
- Speed/acceleration scales come from real trajectories in slots/second and slots/second².

Do not fit everything simultaneously. Fit static shape first, centre traces next, directional deformation next, formation last. Remove unidentifiable parameters; share across gestures, directions and densities.

The old critical 10/s law is not frozen. Simply speeding everything up or adding bounce is also not acceptance: signed overshoot and intermediate contours must improve together.

## 9. Stationary accommodation without runaway growth

Eligibility must come from logical intent and the UNACCOMMODATED body, not the already-expanded contour. The primary item and at most its adjacent item on the entered side can contribute. Use hysteresis to avoid pair chatter.

Prohibit:
    expand → discover another label → expand again → discover a third label

That positive feedback made the old global cap seem necessary.

Use cached native icon/label bounds in the authoritative frame. Full padded raw-box enclosure is a soft preference: Apple labels can remain refracted at the rim. Select the smallest bounded w improving the eligible pair's coverage.

A one-dimensional bounded search is sufficient. Freeze eligibility during the search. Penalize unnecessary expansion. When preferred coverage is infeasible, keep the edge crossing; do not recruit another item or shrink the font.

The w state must continue approaching its target after the finger stops. Preserve the attached point during that growth.

Strong strain may produce a total width beyond 1.9 slots if the reference supports it. Accommodation alone must not be responsible. Report pressure, accommodation, strain and parent scale separately.

Do not replace this decomposition with a renamed total-width clamp.

## 10. Contact without visual clipping

For unheld rest/tap, enforce containment on the actual 2D contour, not its centre or bounding rectangle.

Practical CPU method:
- sample canonical cap arcs and straight runs;
- transform through the same body map;
- refine until unsampled contour error is below 0.25 device px at the maximum tested scale;
- compare against a denser offline boundary before accepting the sample count.

For each contour point xi:
    gi = signedOutsideDistanceToBar(xi) + margin <= 0

Use the existing safe solver or weighted state projection with the state Jacobian. Permit appropriate deformation rather than centre-only correction. At contact remove incoming normal velocity, preserving tangent/unconstrained mode velocities.

Held mode has NO resting-bar contact. Released-held mode gains it only after the entire body returns inside the margin and the next predicted step is feasible.

If a solver fails:
- retain the last finite valid state for that mode and recover continuously;
- never substitute a default capsule for one frame;
- never count legitimate held protrusion as solver failure;
- log a diagnostic counter and disclose every observed failure.

## 11. Ink and compositing

Keep native ink separate from blurred/tinted material. Never pass ink through material-only blur or a reduced-resolution field recording.

Audit in this order:
1. Native source resolution.
2. Correct endpoint source-coordinate mappings.
3. Duplicate parent scaling / half-texel resampling.
4. Premultiplied alpha and colour domain.
5. Coverage outside the resting bar.
6. Texture bounds and complete cache/uniform identity.

For endpoints with premultiplied ink Pi, alpha ai, and material Bi:
    E0 = P0 + (1-a0)*B0
    E1 = P1 + (1-a1)*B1
    E = (1-t)*E0 + t*E1

Only simplify a preblend when its material assumptions actually hold. Do not multiply alpha twice.

For opaque original scene S and body coverage m:
    output = m*E + (1-m)*S

Respect the project's colour-domain contract; do not change domains to improve one image's RMS.

Three 8-bit intermediate layers are a plausible source of the 1.426-level end-to-end residual, not proof that repair is impossible. One bounded option is a final runtime-shader endpoint mixture sampling the required child sources with explicit source-to-node transforms, removing an intermediate quantization while preserving authoritative local coordinates.

Attempt fusion only if the measured error persists and actual platform child-source wiring supports it. If one focused repair cannot pass, keep the clearer existing path and report PARTIAL. Do not spend the motion assignment on speculative renderer replacement.

The body affine deformation is not permission to scale/rotate glyphs as ordinary child UI. Ink follows the optical source map and endpoint compositor. Preserve source detail without forcing the final image to be locally unwarped: 6721 and 6735 visibly stretch, colour-separate and partly disrupt text at the rim. An unwarped sharp overlay that erases those effects is also a parity failure.

Add three source/region checks to the matched fixture: central Chats icon/text from 6717; Communities/Chats side-rim crossings from 6721; portrait plus lower You crossing from 6735. Score interior sharpness separately from rim displacement, elongated-feature geometry and channel separation. Do not pool them into one edge-width score: a long narrow refracted feature is not equivalent to blur. Reproduce content-dependent features through source sampling/compositing; do not paint these specific glyph streaks or a uniform coloured ring into the material.

Background optics are a separate axis. Better motion cannot hide wrong page-text refraction. Preserve measured-rest evidence limits and don't resurrect unsupported refractive-index/dispersion explanations.

## 12. Code boundaries and cost

Responsibilities:
- pointer history/timebase;
- gesture ownership/logical selection;
- persistent shape state and derivatives;
- mode-specific contact;
- uniforms/source maps.

GlassTabBar.kt keeps the corrected gesture/cancellation pipeline. GlassTabBarV3.kt adapts the authoritative frame, grasp and rendering extent. Replace competing transforms; do not stack another scale/offset on top.

Shared geometry belongs in one shader source fragment used by all three rendering paths. Include every render-affecting value in uniform equality/cache identity.

Suggested uniforms:
- reference centre c;
- A_inverse;
- C_inverse;
- k/R;
- canonical a,r;
- conservative bounds;
- existing formation/material parameters.

CPU per frame:
- eight scalar state updates;
- one 2×2 exponential/derivative;
- grasp kinematics;
- accommodation solve only when its inputs change;
- contact only when required;
- no allocation-heavy rebuilds.

GPU per pixel:
- affine inverse;
- stable quadratic inverse;
- canonical capsule evaluation;
- small Jacobian/normal transform as needed;
- existing optical samples/compositing.

Compute exp/cosh/sinh on CPU. No per-pixel dynamics, angle tracking, new full-screen texture or blur on ink. Use bounds early-out. Avoid rerasterizing a full distance field every animation frame when the analytic path suffices. Preserve validated pad/field contracts for paths that still need a field.

## 13. Tests that can falsify the implementation

Keep valid existing tests. Amend superseded visual constraints with explicit explanations; do not delete failures or loosen unrelated thresholds.

Geometry/kinematics:
- identity, inverse and independent finite-difference Jacobian tests;
- fixed finger while pressure/strain/accommodation change: attachment error <=0.1 device px;
- derivative check for grasp velocity, including changing strain orientation and parent scale;
- release continuity of actual rendered points, not only stored centre;
- re-grab ownership;
- density/layout rebase;
- pulls at 0, ±45, ±90 and 180 degrees;
- long uptime, 60/90/120 Hz, dropped frames, suspend/resume.

Behaviour:
- calm held body genuinely protrudes above and below in final rendering, while strong held motion is permitted to flatten within the bar as in 6735;
- taps remain contained;
- short versus long transit deformation follows references;
- stationary eligible-pair growth without recursive recruitment;
- both logical endpoints reachable;
- outside release without immediate clipping;
- actual production component callbacks for taps, fling, stop-lift, reverse, cancellation, second finger.

Rendering:
- real shader native ink through all new deformations;
- strict shared-path source-coordinate gate;
- Legacy/measured-rest identity unchanged;
- Android/JVM coverage agreement at existing tolerance;
- both appearances and parent scales;
- exclusions/localization failures reported in denominators.

Do not create a second implementation in tests and compare it with itself. Use independent derivatives, identity cases, real inputs, render readback and phone contours.

## 14. Numerical acceptance and fitting discipline

Parity is a table of results, not one success label.

### 14.1 Shape

Before exact scoring:
- outside-to-inside silhouette convention, not highlight ridge;
- repeated annotation RMS disagreement <=1 native Apple px;
- registration uncertainty <=0.5 px;
- masks for genuine occlusion, with excluded fraction reported.

If these cannot be achieved, mark affected measurements UNRESOLVED rather than broadening their tolerance until they pass.

On the matched fixture:
- signed-normal visible contour RMS <=1.5 native Apple px;
- p95 <=3 px;
- width/height/centroid errors <=2 px at clear anchor frames;
- maxima reported;
- each phase scored separately: rest, onset, acceleration, strongest strain, stopped hold, early release, overshoot, settle.

The rough prior ratios permit initial checks of ±0.05 slot width and ±0.04 bar-height. These are sanity checks, not 1:1 certification.

Split by whole gestures; include both directions and more than one size. Do not omit poorly localized frames silently.

### 14.2 Time

Only for continuous recordings with traceable time and adequate event/input information:
- onset, peak, crossing and settle timing within one native frame;
- full active contour/centroid traces meet spatial gates;
- signed overshoot within 0.02 slot;
- 90→10 recovery duration differs by <=max(one native frame, 5% measured duration), using the SAME observable;
- no nonlinear time warping.

One global clip offset may align a genuinely unknown start event; declare/freeze it. No independent alignment of each feature.

When finger history is absent, shape versus measured output trajectory can still be tested, but exact input-response recovery cannot be claimed. Untimed stills can pass shape anchors, never temporal parity.

### 14.3 Ink, material, performance

- Retain 1-level endpoint gate, per-pass and end-to-end separately.
- Native-ink sharpness must not regress under the established matched edge-width method.
- Separate ink/material/coverage scores.
- Separate interior ink sharpness from rim-warp geometry. Preserving a glyph's unwarped outline is NOT a requirement at the boundary; quantify the visible stretching/colour separation against matched sources. Do not claim exact photometric channel amplitudes from the display-converted geometry crops alone.
- Preserve matched timing gates: median regression <=2 ms, p95 ratio <=1.10 against the declared accepted reference.
- Also compare with the saved owner-repair build on the same phone.
- Authenticate the historical reference APK; if unavailable, mark that comparison unavailable.
- Report actual refresh budget: 16.67 ms at 60 Hz, 8.33 ms at 120 Hz. No borrowed 90 Hz claims.
- Screenrecord perturbs timing; use the established unrecorded timing procedure, matched repetitions and thermal reporting.

A visibly better candidate may be delivered with PARTIAL gates. It cannot be labelled 1:1/release-ready while required gates fail or cannot be measured.

### 14.4 Bounded fitting

First fit static shape/pressure/accommodation; then centre; then directional deformation; then formation. Share parameters across images, distances, directions and density.

Permit one training revision and one validation-driven structural decision. Once validation informs a change, it is no longer untouched validation: use remaining independent clips. If holdouts are exhausted, report that fact. No per-image correction or quiet threshold changes.

## 15. Decision branches and fallback

A. Free-held controller still renders inside the bar:
Trace coverage, parent clipping, layer extent and union eligibility. Fix the first pixel-removing stage. Do not increase amplitude against a clip.

B. Static contours fit, moving contours do not:
First verify event/frame alignment and parent transforms. If residuals show a repeatable missing mode across gestures, permit ONE extra shared contour mode selected by residual:
- symmetric end flattening/squareness; OR
- trailing-neck/bending.
Do not add both speculatively. Require valid inverse/contour evaluation and shared normals. Retain only with independent improvement beyond annotation uncertainty.

C. Compact analytic shape still fails:
Use a small normalized contour atlas for supported pressure/strain states:
- shared arclength/semantic landmark correspondence;
- interpolate radial/support representation with positivity and convexity checks as appropriate;
- preserve grasp and pose continuity;
- small declared anchor set, shared across sizes;
- no lookup by video frame number or screenshot identity.
Use one evaluator for contour, coverage and normals. Disclose interpolated/authored regions. This is an appearance model, not recovered fluid physics.

D. Held motion remains heavy:
Inspect serial low-pass filters, stale event time, duplicate centre springs and input-to-frame latency before frequencies. Remove redundant smoothing. Measure grasp lag. Do not replace heaviness with arbitrary oscillation.

E. Release kicks or reinflates:
Check c_dot with shape/parent derivatives, stale walls, target discontinuities and recovery velocity feeding new strain. Fix causes before fitting. Then fit complete recovery traces, not endpoint snapshots.

F. Accommodation swallows three items:
Freeze eligibility from intent/unaccommodated overlap, verify cached native bounds, soften raw-label containment. Do not reintroduce a universal 1.9-slot strain jail.

G. Quantitative parity remains unidentified:
Deliver best validated implementation, list the exact missing observable and PARTIAL/UNRESOLVED result. No new iPhone capture is required to finish this task. Do not invent evidence.

H. Ink or performance regresses:
Remove redundant layers/recording, cache CPU calculations, fuse only with exact coordinate/alpha contracts. If necessary expose a reversible quality choice and retain the prior path. State which build/path is installed. Never demonstrate one and benchmark another.

## 16. Execute without another planning round

1. Small audit/reference packet: reproduce the owner failures in active footage; identify the clip/constraint stage; annotate tens of representative frames, not the entire corpus.
2. Run the small A/B static accommodation decision in section 6.5, then implement the chosen shared 2D geometry, derivatives, grasp and free-held rendering. Focused tests plus matched fixture before dynamic fitting.
3. Identify shared motion in the order above; bounded revisions and honest held-out scoring.
4. Publish locally, build sample/Vitals, install authentic candidate, capture the SAME physical inputs as baseline; inspect full-speed and intermediate frames.
5. Only then address new rendering regressions and the bounded endpoint-fusion option. Run the full fresh JVM suite once code stabilizes; rebuild/install after any subsequent source edit.
6. Final physical gestures, matched timing, root-bar-verified soak, installed identity and delivery.

Do not burn the owner's tokens on subagents, broad searches, repeated premature full suites or long narration. Reuse the existing decoder, empirical frame maps, Skia harness, ink tests and device helper. Rapid sequences must run on the DEVICE: separate remote adb calls previously delayed a supposedly rapid second tap by 1.5 seconds.

Deliver to a NEW directory:
research/analysis/v3/ios-parity/

Required:
- IMPLEMENTATION.md: actual model, units, measured/authored parameters, superseded assumptions.
- REFERENCE-LEDGER.json: exact sources/native times, split, masks/uncertainty.
- RESULTS.json and RESULTS.md: every gate/phase/direction, failures retained.
- Native baseline/candidate videos, active strips and contour overlays.
- Fresh final tests and matched performance records.
- APK identity: build hash, pulled installed hash, physical fingerprint, root-bar/foreground check.
- CHECKPOINT.md sufficient to resume.
- Short explicit list of remaining visible differences.

Preserve previous evidence/APKs. Update required project memory without rewriting history. Leave the chosen final Vitals review build installed on the authorized physical device.

The owner is the visual auditor. Tests prove their specific assertions, not Apple parity. Do not claim “1:1”, “all physics fixed”, or “complete parity” unless applicable spatial, temporal, rendering and performance gates genuinely pass, with unidentifiable observations separately disclosed.

Start implementation now. This is the controlling correction to incompatible earlier motion assumptions. Follow the decision branches, complete the implementation and device comparison, and deliver the actual review build without asking for another Astra decision.

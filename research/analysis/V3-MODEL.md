# Liquid Glass V3 — geometry, image transport, and deformable interaction

Design and implementation authority: 14 September 2026.

This is a proposed V3 model, not an implemented or device-verified release. Astra inspected the current code, the new owner references, the prior reassessment and the closeout results, and independently cross-checked the geometry, dynamics and compositor derivations. No new capture fit or recovered physical constant is claimed. No repository was changed for this document.

## 0. The decision

Build V3 as a **layered image-transport system driven by a small constrained deformable body**. The same body supplies its outline, normals, optical coordinates, coverage, and contact constraints. Page material and semantic icon/text ink are distinct inputs. A tab selection is an interaction with that body, not a request to scale a screenshot of a capsule.

The central decisions are final enough to implement without another Astra round:

| Question | V3 decision |
|---|---|
| What moves? | A convex body with independently changing length and end radii; its center and shape have different dynamics. |
| Why does a long tap morph more? | The same center dynamics produce a larger peak velocity for a larger displacement; velocity and acceleration drive bounded shape modes. |
| Why can taps stay in the bar? | Both generating disks are constrained inside the bar. Their convex hull is therefore inside it. This becomes a five-variable convex projection, not visual clipping. |
| Why can a hold grow outside? | Held interaction has an explicitly larger feasible envelope and grasp-preserving size response. Release removes that allowance continuously. |
| Why can size change under a stationary finger? | The admitted icon/label bounds set a size target with its own settling state. No extra pointer move is needed. |
| Why should ink become clear? | The lens substrate contains no semantic ink. Selected ink is sampled sharply once and composited as a complete endpoint under lens coverage. |
| What produces edge adhesion? | A spatial source map with an isolated fold produces stretching and reversed text. No image-history delay is needed to produce this appearance. |
| Is this a recovered glass solid? | No. The corpus identifies transport and some shape/time responses more strongly than thickness, index or a unique physical surface. |
| How many parameters? | Several small blocks with distinct jobs, not a fictitious ten-parameter explanation of every UI behavior. Freeze each block after its own tests. |
| What remains from V2? | Validated measured resting-corner transport, baseline page material where not contradicted, padding/coverage fixes, production gesture ownership, local-publication workflow and default compatibility. |

This supersedes the open-ended shape suggestion and simplified ink-composition equation in `OWNER-FINAL-FABLE.md`. That document remains the owner-observation record. The exact endpoint compositor and tapered-body model below control V3 where they differ. It also supersedes any older claim that the complete product is visually accepted merely because resting geometry or callback tests pass.

V3 is a design name. It does not authorize a `v3` tag, a Maven release, a version-number change, commits, pushes, or removal of existing dirty work.

## 1. Scope, evidence and uncertainty

The minimum complete V3 implementation is a reusable library implementation exercised by the Vitals bar and a production-component sample fixture. Do not begin by rebuilding every button, sheet and container. Existing roles remain on their existing defaults until intentionally opted in. The architecture generalizes; the first adoption is deliberately testable.

The owner's four outcomes remain the product gates:

1. Coherent page-text stretching at both straight bar edges and corners.
2. Crisp icon/label strokes through all selector states, including strong local refraction.
3. Distance-sensitive, directionally deforming tap travel contained within the bar.
4. Held growth, partial-neighbor accommodation, hard swipe and continuous release recovery.

Apple's reference ink is sometimes strongly deformed and chromatically split near a rim. “Crisp” does not mean geometrically unchanged. Conversely, a wide ghost of a whole selected label is not justified by calling it dispersion.

### 1.1 Confirmed code facts

At the inspected working trees:

- `GlassTabBar.kt` records the plate and the unselected item row into `barState`; the selector samples that source and also draws a selected row through `refractContent`.
- `LiquidGlass.kt` includes the through-source in both the material's broad and sharp inputs. There is therefore a real path for unselected ink to remain filtered beneath replacement selected ink. The contribution of that path to any specific screenshot still needs the diagnostic isolation below.
- `VitalsShell.kt` calls `rememberLiquidGlassState(background = ...)` without a renderScale override. Its default is 1. Reduced resolution is **not** the established cause of Vitals' current artifact, although V3 must make sharp ink independent of backdrop scale for other consumers.
- The current global selector `graphicsLayer` stretch also transforms its rendered content. Pinning a row before that transform does not guarantee that its final source coordinates remain anchored to the bar.
- Every DOWN currently starts the held state. A quick tap can begin the protruding held shape before it is recognized as a tap.
- Current shape motion is a capsule plus a small absolute-speed scale response. It has no independent front/back curvature state or stable content-bound accommodation rule.
- `lerpGlassStyle` enumerates fields by hand and switches some identities/unspecified dimensions at a midpoint. V3 must resolve derived dimensions before animation and audit copied fields; it must not acquire a new midpoint shape/optical discontinuity through style interpolation.

### 1.2 Confidence ledger

| Proposition | Confidence | What would change the decision |
|---|---|---|
| Removing semantic ink from material inputs is architecturally necessary | High | An isolated source render proves no duplicate ink reaches any consumer; then retain the invariant and locate the artifact in transforms/sampling instead. |
| The exact endpoint compositor is required at partial coverage | Algebraic | Only its stated opaque-background/coverage assumptions can change the implementation form; the algebra is not a fit. |
| Current callbacks/cancellation fixes should survive | High | A production input test exposes a concrete defect; fix that defect without discarding the ownership contract. |
| A five-coordinate tapered body can satisfy the main motion complaints | High for containment/continuity; medium for Apple's extreme silhouettes | A reliable reference contour has a neck/concavity or a bulge this convex family cannot represent. Follow the bounded shape extension in section 19. |
| A spatial fold explains the described edge sticking | High as a mechanism; medium for the chosen straight-edge profile | A known-source fixture has correct compositing but the wrong source ordering/orientation. Revise the declared edge transport block, not a fake temporal lag. |
| Held expansion is caused by Apple's content-aware layout | Unresolved | These stills cannot settle causation. V3 implements the owner's requested behavior explicitly and labels it authored. |
| A unique refractive index/thickness/viscosity has been measured | Unsupported | New independent constraints would be required. They are not a prerequisite for V3. |

No still establishes frame intervals. No comparison against a different app/background is a valid whole-image pixel RMS. No short soak proves an indefinite memory bound.

## 2. Why a more literal physical model is not the best next implementation

Let d increase inward from an outline. Consider the restrictive physical model of a smooth monotone top surface h(d), a flat lower interface, homogeneous index n>1, nonnegative source gap g, orthographic viewing, and a backward ray that exits through the bottom. Set

```
alpha = atan(h')
beta  = asin(sin(alpha)/n)
theta = alpha - beta
gamma = asin(n sin(theta))
s - d = h tan(theta) + g tan(gamma).
```

These equations require bottom transmission, `|n sin(theta)| < 1`, and no earlier sidewall exit. They cannot silently continue through total internal reflection or a different boundary.

For small slopes, `alpha≈h'`, `beta≈h'/n`, `theta≈(n-1)h'/n`, and `gamma≈(n-1)h'`. Therefore

```
s-d ≈ (n-1)(h/n + g) h'
     = d/dd [(n-1)h²/(2n) + (n-1)gh].
```

Measuring displacement determines the derivative of an effective potential. It does not separately determine n, g and h. If `Psi(d)=integral(s-d) dd`, then a family of height profiles in this approximation is

```
h(d) = -n g + sqrt(n²g² + 2n[Psi(d)+C]/(n-1)),
```

for choices of n, g and integration constant C that make the expression real and the assumed geometry valid. Further boundary/height constraints can reduce this freedom; the observed mapping alone does not eliminate it. The prior n=1.5 versus n≈1.845 near-equivalence on transmission observables is consistent with this ambiguity.

The monotone top-cap model also has a sign restriction. With `h'≥0`, `h≥0`, `g≥0` on its transmitted branch, `s-d≥0`. The measured held outward-sampling sign violates that restricted model. It does **not** prove that every conceivable physical optical body is impossible; a curved underside or different path could change the sign. It does mean that stronger fitting of the same monotone cap cannot solve the problem.

A smooth ray path meeting one source plane transversely has a continuous source coordinate. A fold changes its derivative, not its continuity. The old hard held seam is therefore not a physical consequence of a smooth single path. Existing seam experiments did not decisively establish a true jump. V3 treats any path change as explicit image transport/compositing rather than a hidden discontinuity called refraction.

Finally, an operator with a negative high-pass lobe is an image-processing operation, not a probability mixture of passive incoherent light paths. Explicitly authored tone, readability and highlights are legitimate UI operations. Hiding them inside an IOR or Fresnel knob makes the system harder to reason about without improving the pixels.

**Consequence:** V3 shares a geometry and image-formation framework; it does not impose one unverified solid or one fitted curve on every source layer and UI role. Keep physical terminology only where the corresponding assumptions are actually implemented.

## 3. Coordinates, units and frame ownership

The CPU controller uses dp and seconds. Geometry for rendering is converted once into a stable bar-local physical-pixel frame. Let x increase right and y increase down. The bar frame's origin does not follow the selector. Root navigation translation/scale is an explicit transform, not part of the selector strain.

Keep continuous floating-point positions. Round raster allocation and the existing recording pad according to their contract; do not round the selector's moving optical origin to whole pixels. Do not assume iOS points equal measured Android dp: the inherited 72-reference-pixel/24-dp convention remains a design scale convention.

Every presented frame consumes one immutable snapshot:

```
FrameSnapshot = {
  time, controllerState, actualBarGeometry, selectorGeometry,
  layoutAndInkRevision, pageSourceRevision,
  all source-to-bar transforms, resolved material parameters
}.
```

Layout, geometry, source capture, coverage, ink, material and diagnostics must refer to this snapshot. Sources are recorded before consumers. A source unchanged since a prior frame may be cached; a changing page from a prior frame must not be combined inadvertently with current geometry. No synthetic image-history buffer or fixed-frame optical lag is introduced.

For a destination point p in bar pixels, source map Q, source origin o, recording pad p_pad and source scale S,

`q_texture = S [Q(p)-o] + p_pad`.

All four quantities must have named spaces. Equivalently, use explicit homogeneous source/destination transforms. Pixel-center conventions must agree with the Skia harness and platform renderer; do not add a guessed half pixel. Separate `pageScale`, `fieldScale` and `inkScale`; inkScale starts at one native physical pixel per texel.

**Identity invariant:** if displacement is zero and paint styles match, moving or deforming the selector changes no icon's position. It only changes the aperture. Deforming a completed selector texture violates this invariant.

## 4. The V3 body: convex hull of two unequal disks

Use five geometric state coordinates:

`q = (cx, cy, L, r, k)`.

Here `(cx,cy)` is the horizontal bounding-box center, L is the separation of two disk centers, r their mean radius, and k their signed radius difference divided by two. Define

```
rL = r-k;                    rR = r+k
CL = (cx-L/2-k, cy);          CR = (cx+L/2-k, cy)
Body = convexHull(disk(CL,rL), disk(CR,rR)).
```

Its left and right extrema are exactly `cx-(L/2+r)` and `cx+(L/2+r)`. Its width is `L+2r`, independent of k. Thus end asymmetry does not secretly change the declared horizontal center. Its top/bottom extents are `cy ± (r+|k|)`; do not incorrectly use 2r as the full height when k is nonzero.

At k=0 it is a capsule. Nonzero k creates a larger leading or trailing cap and sloping sides. L, r and k can evolve separately, producing stretch, flattening, tail lag and arrival bulging without stretching the ink texture. This is a reduced-order visual body, not a claim of conserved fluid volume.

Require

```
r-k >= rMin
r+k >= rMin
L >=  2k + ellMin
L >= -2k + ellMin.
```

The last two require `L>|rR-rL|` with a numerical margin, so neither disk contains the other and the external tangents remain defined. Initial authored limits: rMin=4 dp, ellMin=1 dp, overridden only for a genuinely smaller component with equivalent numerical margins. Near a circular selector, these limits suppress excessive asymmetry automatically.

This family is intentionally small. Its shape is convex and its outline C1, not C2. It cannot draw a verified concave neck. Do not disguise that limitation with a global blur or arbitrary polygon wiggles.

### 4.1 Exact boundary and distance query

Precompute on the CPU:

```
delta = (rR-rL)/L
gamma = sqrt(1-delta²)
nTop = (-delta,-gamma)
nBottom = (-delta,+gamma)
ATop = CL+rL*nTop;       BTop = CR+rR*nTop
ABottom = CL+rL*nBottom; BBottom = CR+rR*nBottom.
```

The top and bottom boundary segments join the corresponding A/B points. The exposed left circular arc has radial normal `nx<=-delta`; the exposed right arc has `nx>=-delta`.

For any point p:

1. Compute squared distance to each tangent segment using `t=clamp(dot(p-A,B-A)/|B-A|²,0,1)`, nearest point `A+t(B-A)`.
2. For each disk, its radial projection onto the circle is an eligible arc candidate only if its radial direction meets that disk's exposed-normal condition. The candidate distance is `abs(|p-C|-radius)`. If p=C, use a deterministic eligible direction; the distance is radius. Endpoints are already covered by the segments.
3. Select the smallest squared distance and its nearest boundary feature. Determine inside/outside from membership in either disk or the connecting tangent quadrilateral. Their union is exactly the convex hull. Quadrilateral membership is four consistently oriented half-plane tests; no guessed winding convention.
4. Signed distance D is negative inside. Inward depth is d=max(-D,0). The outward normal is the selected arc's radial normal or the selected segment's nTop/nBottom. At equal-distance medial-axis ties the normal is not unique; optics must already have faded to identity there.

This is a constant-size query: two circle lengths, two segment projections, a few comparisons and typically a final square root. Return distance, normal, boundary point, feature identity and local curvature together; do not recompute them independently in three shader strings. Curvature is 1/radius on an arc and zero on a segment, with a C1 tangent transition. A curvature-based role blend can be softened separately; do not replace the actual geometric normal with a broad differenced estimate.

Use one generated/shared SkSL helper and one tested CPU reference. CPU-precompute all tangent points and inverse segment lengths. The normal must match the **deformed** shape. No CPU full-size distance transform per moving frame and no iterative angular maximization in every pixel are needed for this primary model.

### 4.2 Why not a scaled capsule or a fluid solver?

A scale changes the entire raster and does not independently control cap shape. A two-disk body changes the actual aperture, normal field and optical map. Conversely, a general fluid simulation introduces many unmeasured degrees of freedom, numerical boundaries and GPU work. The requested front/back deformation and confinement can be expressed exactly in five coordinates. Use the smaller solvable model first.

## 5. Whole-body containment as linear inequalities

During ordinary tap travel the selector body must be inside the current inset bar outline. Construct an **inscribed** convex polygon P for that allowed outline. For a circular end of radius R, angular step deltaTheta has maximum inward chord error

`error = R [1-cos(deltaTheta/2)]`.

Choose steps so this is at most 0.2 physical px. Keep straight runs exact. Calculate the required count from radius/density; do not freeze a count that becomes insufficient at another size. The polygon is an inner approximation, so its chord error makes the constraint conservative. It cannot permit an unseen outward protrusion between tested angles.

Each polygon plane is `nx*x+ny*y <= b`, with unit outward normal. A disk lies inside that plane exactly when `n dot C + radius <= b`. Substituting the two disks gives

```
nx*cx + ny*cy - 0.5*nx*L + r - (nx+1)*k <= b
nx*cx + ny*cy + 0.5*nx*L + r + (1-nx)*k <= b.
```

If both disks are contained in the convex polygon, their entire convex hull is contained. Add the four radius/tangent inequalities and explicit upper size limits. All are linear in q. This is stronger than testing only the center, bounding rectangle, or a handful of contour points.

The visible bar may change size in held mode; its current contour belongs to the same frame snapshot as the solver. Ordinary taps do not invoke full held bar growth. Clip a selector-specific contact glow to the allowed tap bar if otherwise it creates a fake protruding outline; the bar's own exterior shadow is a different effect.

### 5.1 Position and velocity projection

After an unconstrained spring step q*, solve

`min 0.5*(q-q*)^T M (q-q*)  subject to A*q <= b`.

Normalize dimensions by slot width for conditioning. Start with the authored diagonal metric `M=diag(4,4,1,1,1)` in `(cx,cy,L,r,k)` order: preserving center is somewhat more costly to sacrifice than relaxing shape. This is a projection metric, not measured mass.

A tiny feasible active-set solver suffices: five unknowns, at most five independent active planes, small linear systems. Cap iterations, reject nearly dependent active rows, retain a known feasible state. Do not add a general optimization dependency solely for this solver. An equally well-tested bounded dual coordinate method is acceptable; correctness and worst-case time are the criterion.

Project velocity separately against active static constraints:

`min 0.5*(v-v*)^T M (v-v*) subject to A_active*v <= 0`.

This projection removes outward motion without adding a bounce. The feasible velocity cone includes zero, so weighted speed/kinetic norm cannot increase under this projection. This statement is about static-boundary velocity projection, not the total energy of an externally driven UI. Do not turn the position correction into `correction/dt` and thereby invent a velocity impulse.

For moving limits `b(t)`, use `A_active*v <= b'(t)`; a moving boundary can legitimately do work. Handle changes in A as well if the bar changes orientation: the derivative constraint is `A*v <= b' - A'*q`. Bar translation can alternatively be removed by working in its translating frame.

If the numerical solver fails, render no infeasible body. For a static unchanged feasible set, retain the last feasible q and remove outward velocity; log the failure and mark the controller test failed. For a layout change, construct a feasible rest state in the new bar rather than retaining an invalid old coordinate system. Repeated fallback is a defect to fix, not an accepted animation mode.

There is no hard clipping of the final selector picture. Shape and position comply before rendering, so the rim remains a rounded body.

## 6. Dynamics and distance response

Each scalar state follows a second-order response

`x'' + 2*zeta*omega*x' + omega²*(x-target) = 0`.

Center travel initially uses critical damping, zeta=1. Shape modes use lower damping and can overshoot internally while the body constraints prevent escape. Keep current values **and velocities** on every retarget.

Initial authored preset, not recovered Apple measurements:

| State/control | Seed | Bounded tuning range |
|---|---|---|
| Tap center omega | 22 /s | 18–28 /s |
| Tap center zeta | 1.0 | 0.95–1.1; implement the chosen analytic regime correctly |
| L, r, k omega | 26 /s | 20–32 /s |
| L, r, k zeta | 0.80 | 0.72–0.95 |
| Full-held formation threshold | 120 ms after eligible DOWN | 100–180 ms |
| Held formation omega/zeta | 36 /s, 1 | 28–44 /s, 1 |
| Held size-demand omega/zeta | 24 /s, 0.90 | 20–30 /s, 0.85–1 |
| Maximum velocity-driven extra L | 0.60 slot widths | 0.3–0.7, further limited by containment |
| Maximum radius reduction | 0.12 of base r | 0–0.15 |
| Maximum skew drive | 0.18 of base r | 0.10–0.25, further limited by tangent constraints |

Keep one shared preset across destinations and directions. Calibrate against short/long/held scenarios together. These seeds are intended to make the candidate concrete, not to substitute for viewing it on the device.

Let S be slot width, v the center velocity and a the **pre-contact spring acceleration**, bounded to the declared drive range. Define

```
u = abs(v)/(S*omegaCenter)
z = a/(S*omegaCenter²)
Ltarget = Lbase + 0.60*S*tanh(u)
rtarget = rbase*(1 - 0.12*tanh(u))
ktarget = -0.18*rbase*tanh(z).
```

In this convention k>0 makes the right end larger. Acceleration to the right initially leaves a larger trailing left end; braking excites a larger arriving right end. The independent k spring supplies the delayed shape response. Test mirror symmetry of the whole trajectory. Collision corrections are not acceleration input: using them would amplify contact chatter into shape kicks.

When the finger is stationary after a drag, decay the velocity drive using the actual current pointer/state time rather than retaining the last nonzero move forever. Preserve the terminal-sample fix in release tracking. During size-only accommodation, do not interpret width growth as a finger fling.

### 6.1 A numerical prediction before fitting

For a critical center spring starting at rest a distance D from its target,

```
remaining(t) = D*(1+omega*t)*exp(-omega*t)
v(t) = D*omega²*t*exp(-omega*t)
vPeak = D*omega/e at t=1/omega.
```

Thus the peak normalized speed is `D/(e*S)`. With the seed above, extra-L **targets** for distances 1, 2 and 4 slots are approximately 0.211 S, 0.376 S and 0.540 S. These are predicted drives before shape lag/contact, not asserted observed body widths. The actual constrained trajectory is what must be compared visually.

The 90% center-travel time of this critical response is about `3.89/omega`, approximately 177 ms at omega=22. This is a design prediction, not an Apple time constant. Longer distances take longer to meet the same absolute residual tolerance even when the fractional response shares the same clock.

This provides stronger distance response than a binary “full morph” on every tap. Constraints may saturate the extra length near an end; report saturation rather than secretly retuning that destination.

### 6.2 Exact stepping and frame handling

For a frozen target over dt, use exact scalar spring stepping. Set d=x-target. For zeta<1, let a=zeta*omega, b=omega*sqrt(1-zeta²), E=exp(-a*dt):

```
dNew = E*(d*cos(b*dt) + (v+a*d)*sin(b*dt)/b)
vNew = E*(v*cos(b*dt) - (a*v+omega²*d)*sin(b*dt)/b).
```

For zeta=1:

```
dNew = exp(-omega*dt)*((1+omega*dt)*d + dt*v)
vNew = exp(-omega*dt)*((1-omega*dt)*v - omega²*dt*d).
```

Either restrict this preset to these regimes or implement the real-root overdamped solution for zeta>1. Do not pass zeta>1 through the underdamped square root.

Use a common simulation clock with maximum substep 1/240 s for coupling and contacts. Exact stepping avoids artificial scalar integration damping; substeps resolve changing targets, grasp and constraints. Evaluate input events at their actual timestamps, subdividing at an event boundary. Do not apply fixed per-frame increments or conflate the display refresh interval with an input sampling interval.

For one 50/100 ms delayed frame, run the bounded required substeps and render the current state once. For application suspension/very long gaps, stop replaying stale gestures: cancel the abandoned interaction, clear stale input velocity and restore a feasible current-selection state. Treat that as lifecycle recovery, not a normal animation frame. Never synthesize future pointer samples.

## 7. Interaction modes, grasp and content accommodation

Use one state owner with Rest, PressPending, TapTravel, Held/Drag, Release/Settle and Cancel/Lifecycle states. Preserve the production callback/cancellation decisions; this model changes visual response, not who is allowed to select a tab.

- DOWN gives an immediate subtle press indication constrained inside the bar. It does not set full held protrusion.
- A short tap selects once through the existing clickable path and starts TapTravel. Do not delay navigation to wait for visual settling.
- A sustained eligible hold, or recognized deliberate horizontal drag, enters Held/Drag. Vertical takeover cancels. A pending hold timer must be cancelled on UP, cancellation or disposal.
- A drag commits once only from the driving pointer's genuine terminal UP. Second-pointer takeover, consumption and disposal do not select.
- A stop/hold/lift uses the terminal sample and current tracker semantics, not the last moving velocity. Visual center velocity and selection-projection velocity are distinct when the body is changing size.
- Reduced motion disables gel/held expansion/fling while preserving the established accessible selection/cancellation semantics.

### 7.1 A grasp that survives size changes

Let A=(L+2r)/2 be the current horizontal half-width. On held acquisition define a fixed normalized grasp beta in [-0.8,0.8]. Its relation is

`g = cx + beta*A`, hence `cx = g-beta*A`.

Choose beta from the current finger/body relation, clamped to the safe interval. Initialize g from the current geometry, **not** by snapping it to a distant finger. If a hold is acquired away from the current body, introduce an attachment offset o with

`g(t)=fingerX(t)+o(t)`.

Initialize o to preserve g and its velocity, and critically relax o toward zero using an authored fast attachment response (seed omega=40 /s). If the user grabbed the existing body directly, o is normally zero. This lets current finger movement act immediately without teleporting a remote selector on acquisition. The y grasp can use the same principle with a bounded held vertical displacement, or remain centered if the current component only supports horizontal drag; disclose which behavior is implemented.

When free of constraints, width changes occur around the grasp. The actual center velocity is

`vc = vg - beta*vA`.

Carry this velocity into visual release. Use the actual terminal **finger** velocity for deciding a navigation fling, so a stationary finger does not fling a tab because its label is being accommodated.

While a held envelope is active, grasp may be enforced as a linear equality `cx+beta*(L/2+r)=g` in the small projection, when feasible. If the finger requests an impossible position, use a bounded closest feasible grasp rather than rendering an invalid body or making the solver infeasible. Report the difference between requested and actual grasp; normal in-range dragging should keep it within one device pixel.

### 7.2 Stable admission of nearby content

Layout supplies final icon bounds and full icon+label bounds for every item in bar coordinates, including intended emphasis. Use layout geometry, not raster OCR or a guessed slot width. One layout snapshot serves ordinary/selected paint variants and both visual and accessibility state.

Candidate admission is based on a **fixed base interaction aperture**, not on the already expanded lens. Start with the item closest to the grasp and at most its nearest eligible adjacent neighbor. A seed hysteresis rule admits a neighbor when its icon center is at least 2 dp inside the base aperture and retains it until at least 4 dp outside. Scale these margins intentionally for tiny controls; do not add more neighbors recursively as the body grows. Resolve equal neighbors by distance then stable item order.

For admitted bounds union [Bleft,Bright], already padded by the declared design padding, the required half-width is

`Arequired = max(Abase, (g-Bleft)/(1+beta), (Bright-g)/(1-beta))`.

This follows directly from `left=g-(1+beta)A` and `right=g+(1-beta)A`. The beta bound avoids nearly singular denominators. Seed padding is 6 dp beyond the combined icon/label bounds; it is authored and should be compared to the references.

Spring the size demand toward Arequired **every simulation step**, including stationary-finger intervals. Limit width to a named maximum; start with `min(barWidth+2*heldOverflow, 2.4*S)` and never below the component's legitimate base width. If accessibility text needs more than the maximum, preserve readable layout and the declared clamped behavior; do not recursively swallow the whole bar or clip away labels as a success.

Allocate extra half-width primarily through L, and height through the held/base radius target. These are design choices, not incompressibility. Release admission with hysteresis and settle back gradually. Changing admission changes targets, not the current q or velocity.

### 7.3 Held release envelope

A protruding held body cannot be required to satisfy the tap envelope instantaneously on UP. At release, for each rest-bar constraint plane record initial excess `d0=max(0,A_j*q-b_j)` and outward support speed `u0=max(0,A_j*v)`, in the rest bar's frame. A useful authored allowance is

`a_j(t) = [d0 + (u0+omegaRelease*d0)*t]*exp(-omegaRelease*t)`.

It starts at the present excess and has initial derivative u0; it can briefly continue existing outward motion and then decays. Seed omegaRelease=18 /s, constrained by the available held envelope. Apply `A_j*q <= b_j+a_j(t)` and the matching time-derivative velocity constraint. Do not introduce a new outward speed if u0 was zero. If an existing extreme speed would exceed the maximum allowed held excursion, dissipate it as a declared envelope contact at release.

Keep this envelope synchronized with held optical amount, rim and shape recovery. Once the body actually fits the rest constraints and residual allowance is subpixel, finish the transition without a visible snap. Normal tap travel never receives this held allowance. Cancellation from a held state may use the same geometry recovery while committing no selection.

When retargeted or re-pressed mid-release, initialize from current state and allowance; do not restart from a nominal rest shape. External selection, layout/density changes and disposal must clear stale preview/held ownership exactly as in the existing tested handler.

## 8. Exact image composition: replace complete endpoints

Separate page material from semantic ink. Let the following quantities be evaluated at the same destination pixel p in one declared compositing color space:

- B0: the opaque page/bar material without any semantic icon or label.
- B1: the opaque material seen through the selector, also without semantic ink.
- U0=(c0,a0): ordinary premultiplied ink at its ordinary source position.
- U1=(c1,a1): selected premultiplied ink sampled at its declared refracted source position.
- m: selector aperture coverage, in [0,1].

Construct the complete endpoint images first:

```
C0 = c0 + (1-a0)*B0
C1 = c1 + (1-a1)*B1
C  = (1-m)*C0 + m*C1.
```

This is the exact endpoint coverage equation under the pixel-coverage approximation. If material/glyph values vary substantially within one pixel, integrate the whole expression at subpixel samples, not just m independently.

A convenient draw implementation is C0 as the base, followed by the fully composed selector endpoint with premultiplied output `(m*C1, m)`. Ordinary source-over then computes the equation. Do not first alpha-over masked ordinary ink and then alpha-over masked selected ink: the second copy attenuates the first again.

### 8.1 Correction to the earlier ink-only equation

Mixing ink alone and alpha-overing it on separately mixed material gives

```
Cnaive = (1-m)c0 + m*c1
       + [1-(1-m)a0-m*a1]*[(1-m)B0+m*B1].
```

Subtracting the correct C yields

`Cnaive-C = m*(1-m)*(a1-a0)*(B1-B0)`.

The error vanishes only in special cases. A moving rim changes both glyph coverage and material, precisely where this cross term matters. This is why V3 replaces complete endpoints instead of merely putting an apparently sharp row over a blended background.

If endpoint backgrounds are not opaque, represent each entire endpoint as premultiplied RGBA and coverage-mix those endpoint RGBA values consistently. Do not blindly output alpha=m for an endpoint that is itself translucent. The tab-bar implementation can use an explicitly opaque page ground; a general library node must state its alpha contract.

### 8.2 Source graph

The minimum graph is:

```
current page P
    -> ink-free bar material B0
    -> ink-free through-source for the selector -> selector material B1

one item layout -> ordinary native-resolution ink U0
                -> selected native-resolution ink U1

body geometry + source maps + U0/U1 + B0/B1 -> endpoint compositor -> final bar
```

The through-source includes the bar material over the correct raw page outside its outline. It includes neither the selector itself nor either semantic-ink variant. No capture may contain a consumer of itself.

Two deliberate optical/material operators can act on a page in a nested bar/lens system. That differs from accidentally blurring the same icons twice. Preserve the declared nested-material behavior first; do not remove a valid bar filter simply because there is a second lens. Keep raw-share and tint-gain contributions explicit. A measured additive gain is not necessarily a normalized optical path probability.

If evaluating nested maps directly from the page is used to avoid an intermediate texture, preserve ordering. `Qbar(Qlens(p))` is generally different from `Qlens(Qbar(p))`. Tone, blur and rim shading also do not commute with arbitrary warps. The simple safe implementation is a bounded ink-free bar intermediate from the same frame, sampled by the selector.

Ordinary and selected ink can be two cached paint variants of one layout. Keep exactly one accessible/clickable row and one source of layout/emphasis state. Selected/unselected glyphs may differ if the host intentionally supplies them, but both variants must have stable declared anchors and their union bounds must be used for accommodation. Do not assume every app uses identical outline glyphs in both states. Do not independently spring two copies of the same item.

### 8.3 Native ink, alpha and spectral split

Start semantic ink with one monochrome geometric sample. It receives no page blur or wide/high-pass material operator. Cache at native physical resolution and invalidate for text, font, density, palette, layout and intended emphasis changes. A source recording may span the full bar plus padding while its output aperture remains only the selector. Do not clip its source to a selected slot or evaluate selector coverage again at the sampled source coordinate.

The current union-alpha RGB split is not correct per-channel transmission over an arbitrary background. If a justified rim-local spectral split is retained, compose against the known selector endpoint material per channel:

`C1_c = ink_c(Q_c(p)) + [1-alpha(Q_c(p))]*B1_c`.

Then apply aperture coverage to that complete opaque endpoint. A white glyph found only by the red ray must not incorrectly block green and blue background light through a maximum-of-three-alpha value. Black ink over saturated backgrounds is a particularly decisive diagnostic.

Do not infer transmitted RGB displacement from a colored rim over a black source: shifting black cannot create that highlight. Rim chroma and transmission geometry are separate controls. Default bulk ink dispersion to zero until the sharp single-source baseline works; reintroduce only the localized split that improves reference similarity. No broad label ghost may be excused as a color fringe.

If converting a premultiplied source between nonlinear color spaces, unpremultiply at nonzero alpha, transform color, then premultiply. Prefer the framework's existing correct color-space path over a handwritten gamma fix. Existing measured display-domain page photometry is not silently replaced by linear-radiance blending. Physical and display-domain claims must remain separate.

### 8.4 Rim ordering

If a premultiplied rim R=(rR,aR) belongs above ink, form

`C1rim = rR + (1-aR)*C1`

before endpoint replacement. Apply aperture coverage once. Separate external contact shadow from this operation and give it an explicit support/occlusion mask. A strong rim can obscure a tiny stroke at the boundary in the references; it must not frost the entire label interior.

## 9. Source transport from the actual contour

Let b(ell) be the nearest boundary point parameterized by arclength, with outward normal n, tangent t and positive convex curvature kappa such that `n_ell=kappa*t`, `t_ell=-kappa*n`. Within the unique nearest-boundary region,

```
p = b(ell)-d*n
Q(p) = b(ell)-s(ell,d)*n + tau(ell,d)*t
     = p + [d-s(ell,d)]*n + tau(ell,d)*t.
```

s is source depth, not a displacement magnitude. A negative s samples outside the outline. tau is an explicit tangential shift, initially zero. Geometry provides d, n, t, b and feature identity once for every source-layer program.

Use a band that joins identity before the contour's inward normals meet. For the primary body, begin with `W=0.6*min(rL,rR)`; use the actual radii converted to layer pixels. For a symmetric bar this agrees with the existing 0.6R convention. Avoid arbitrary denominator clamps or a meaningless normal in the deep interior: the optical map is identity there. Root/medial-axis behavior is a geometry domain issue, not a reason to apply blur.

### 9.1 Full Jacobian, including shear

Differentiating the expressions above gives

```
p_ell = (1-kappa*d)*t
p_d   = -n
Q_ell = (1-kappa*s+tau_ell)*t - (s_ell+kappa*tau)*n
Q_d   = tau_d*t - s_d*n.
```

In the local destination/source bases `(t,-n)`, the map Jacobian is

```
J = [ (1-kappa*s+tau_ell)/(1-kappa*d)       tau_d ]
    [ (s_ell+kappa*tau)/(1-kappa*d)        s_d   ].
```

If tau=0 and s has no arclength dependence, its principal factors are

`Jt=(1-kappa*s)/(1-kappa*d)` and `Jn=s_d`.

Thus tangential stretch already follows from a normal source map around a curved edge. Conversely, blending straight and corner profiles introduces s_ell and therefore shear even when tau=0. The earlier simplified factor alone is insufficient to describe that transition.

The source coordinate itself remains meaningful even when source depth passes across a source-side geometric center, but the destination boundary coordinates must be well defined. Do not invert this chart beyond its destination medial axis. Verify the complete Q with coordinate readback; a scalar source-depth test does not establish correct two-dimensional transforms.

### 9.2 Optional physical-consistency test, not a required extra knob

If a small-slope single optical potential is asserted, `Q=p+gradient(Phi)` requires a symmetric Jacobian. A spatially varying normal-only map generally fails that condition. For a profile with identity at W(ell), one compatible potential is

`Phi(ell,d) = -integral from d to W(ell) of [s(ell,u)-u] du`.

Then `Phi_d=s-d`, and the corresponding tangential contribution is

`tau = Phi_ell/(1-kappa*d)`.

The upper-limit term vanishes because s(W)=W. This shows how a physical-gradient model would generate additional tangential transport when a bevel varies along its perimeter; it is not legitimate to call any arbitrary normal-only blend a recovered surface.

V3's default remains an **effective** image map with tau=0, because the available evidence does not require that potential correction. Do not add it as a free aesthetic slider. Only a verified transition-region residual or a specific physical-model experiment can justify this deterministic alternate construction. The principal V3 task does not reopen the global inverse problem.

## 10. Transport programs: preserve known corners, make straight folds explicit

A single shared mapping engine supports different declared source layers and roles. It does not require identical optical strength for semantic ink and a page behind a material plate.

### 10.1 Resting corners — retained evidence

The measured corner table remains authoritative in `GlassRestMapTable`. At reference R=72 px, W=43.2 px, its dimensionless knots are:

```
U = [0.138889, 0.162037, 0.185185, 0.219907, 0.254630, 0.300926,
     0.347222, 0.439815, 0.532407, 0.625000, 0.729167, 0.833333]
V = [0.875903, 0.823079, 0.784259, 0.741898, 0.711296, 0.681921,
     0.661713, 0.642755, 0.649884, 0.681898, 0.745278, 0.833333].
```

Use the actual source arrays as authority, not retyped rounded numbers if precision differs. Interpolate linearly except the final interval, which uses the existing Hermite construction: first derivative matches the preceding interval and ends at 1, with last value equal to last input. Identity beyond that join. Do not replace it with the old flat 23.8-pixel plateau.

The d<6-reference-pixel extension is unmeasured and currently constant; that provenance must remain explicit. It is not evidence for an extended physical plateau. Preserve it for regression by default. If it becomes a visible V3 rim artifact, an explicitly labeled unmeasured-rim interpolation may be changed while holding the measured 6..36 region fixed and reporting the difference. Do not reinterpret the old 1.5-pixel displacement fade as mandatory optical physics.

Scaling this table to other radii is a design extrapolation. The earlier 0.67/p95 1.41-pixel ring result applied to the mutually localizable subset; keep 28/246 localization coverage and the broader residuals visible. V3 cannot cite it as proof of perfect whole-bar optics.

### 10.2 Straight bar edges — one explicit fold candidate

For the newly exposed straight-edge behavior, start with a separate bar-role profile, not a fake held/sheet formation flag. Let u=d/W. For 0<=u<=1 use

`f(u)=u + a*(1-u)^3`, `s=W*f(u)`;

use identity beyond W. This profile joins identity in value, first derivative and second derivative. Its derivative is

`f'(u)=1-3a*(1-u)^2`.

For a>1/3 it has one isolated turning point

```
uFold = 1 - 1/sqrt(3a)
fMin  = 1 - 2/[3*sqrt(3a)].
```

**Authored seed:** a=0.8, W equal to the current bar optical band. It predicts uFold≈0.3545 and minimum source depth≈0.5697W. These are mathematical predictions of the proposed candidate, not measurements extracted from 6686/6687. Compare branch orientation and text structure before tuning its strength. Bounded a range for this card: 0.55–1.20.

This map samples each destination once. The same source landmark can appear at two destination depths because f reverses orientation. Do not automatically mix a second mirrored image on top and thereby duplicate a fold that already exists in the map.

If this one-parameter shape fails after correct source processing, allow only the following extra shape coefficient:

`f(u)=u + [a+b*u]*(1-u)^3`, with `|b|<=0.25*a`.

Here `f''(u)=6*(1-u)*(a-b+2b*u)`, positive in the interior under these bounds. With the stated a range, the derivative starts negative and ends at one, so there is still exactly one fold and a C2 identity join. This is a bounded revision of one model, not an unrestricted per-row spline fit.

Blend the straight and preserved corner profiles through a smooth **geometric** arc/run transition coordinate. The blend is shared across backgrounds, tab choices and travel direction. It must not turn on because the background contains a particular word. Account for the resulting s_ell in diagnostics and antialiasing. Do not step the profile merely because a nearest feature changes from arc to line.

The user's straight-edge reference is qualitative/source-structure evidence, not a calibrated copy of Vitals. If the base profile is too strong for one role, configure a role amplitude; do not quietly change the measured corner table or all library materials. New bar optics stay on the V3 opt-in.

### 10.3 Held semantic ink — a continuous outward map

Begin from the existing outward-band correspondence as an effective seed:

`sOuter/W = 1.3*u - 0.29`.

The old switch to identity at u=0.5 had an approximately 0.14W jump. Since the captures did not decisively establish a true discontinuity, do not preserve that jump in the new sharp ink map. Set

```
w(u) = 1 - smoothstep(0.45,0.55,u)
fHeld(u) = u + w(u)*(0.3*u-0.29)
sInk/W = u + hOptical*(fHeld(u)-u).
```

hOptical moves continuously from zero to one with the held material state. This is a single **coordinate blend**, not a blend of two whole glyph images. It connects the outward edge sample to the interior without a hard jump. Its transition compresses source detail and may require antialiasing, but it must not be replaced by material blur. At u>=0.55 it is identity unless an explicit, separately bounded interior magnification is adopted.

The constants 1.3 and 0.29 are inherited effective-map values, not recovered index/thickness. The 0.45..0.55 connection is an authored regularization. Test it against the new sharp-rim references; if it fails, adjust only the width/strength of this declared band within the existing held-map experiment. Keep source-plane identity explicit.

Intentional selected-item emphasis is applied once in the ink layout/paint model, around each item anchor, and its bounds feed accommodation. Do not combine a 1.18 raster scale, an independent 1.18 map magnification and a global gel scale. The current measured emphasis can be the initial design value, but duplicated magnification is a defect.

### 10.4 Held page material and path mixtures

The selector's page material may retain a declared mixture of bar output and raw backdrop as in the current held material. If two transport paths are required, write them explicitly:

`F_c(p) = gain_c * sum_j weight_jc(p) * Source_jc(Q_j(p)) + offset_c`.

For a positive mixture, normalized relative weights satisfy weight>=0 and sum_j weight=1 per channel; gain is separate. A reported approximately 1/.65/.1–.3 mirrored-channel response is not automatically a normalized probability or a wavelength-dependent source shift. Keep depth-dependent path weights in this block, with monochrome geometry unless a geometry experiment separates chromatic displacement.

Coordinate blending and image blending are different operations. A broad image mixture can produce ghosting even if both inputs are perfectly sharp. For semantic ink the default is one continuous map. For page material a mixture is allowed only where its visible branch behavior is required. Never mix the base bar's semantic ink into that material path.

## 11. What a fold predicts, and how to sample it

Near a nondegenerate fold at d*,

`s(d)=s* + 0.5*A*(d-d*)² + higher-order terms`, with A nonzero.

For a nearby source landmark at s*+epsilon on the side with two roots,

`dPlus/Minus = d* ± sqrt(2*epsilon/A)`.

The two displayed copies therefore separate as the square root of the source landmark's distance from the fold minimum. They have opposite normal orientation. This is a falsifiable property of a fold, and it explains the appearance of text adhering/stretching near an edge without a time delay. It is not a cusp: a cusp would additionally require vanishing second derivative and a second control direction.

A broad region with s_d=0 is different: it reads the same source row over a whole band and tends to produce a constant strip. Do not use that plateau as a substitute for the isolated turning point visible in a folding source map.

### 11.1 Footprint and brightness

The covariance of a linearly mapped unit output pixel is `Sigma=J*J^T/12`. Large singular values of J mean the pixel spans many source pixels and needs minification filtering. Small singular values mean magnification; they are not a reason to apply a wide blur at a fold.

If J has entries a,b,c,d, compute

```
T = a²+b²+c²+d²
D2 = (a*d-b*c)²
sigmaMax = sqrt((T+sqrt(max(T²-4*D2,0)))/2).
```

Near a fold, the center Jacobian alone can miss nonlinear source curvature across a pixel. Evaluate the complete map at subpixel positions where necessary. Use one native ink sample normally, and a bounded 2x2 output-subpixel integration in regions with strong compression or rapidly changing source coordinates. Each sample evaluates the complete endpoint color/alpha expression. This is antialiasing, not a broad material blur.

Compare the bounded production sampler against a higher-sample offline reference of the same map. If compression is too strong for four taps, a bounded prefiltered source level or a weaker declared compression map is preferable to uncontrolled shimmering. Verify that any added filtering applies only where source minification requires it; identity and magnified ink must remain sharp. Do not assume a GPU derivative or mip-LOD API exists without checking the actual runtime.

Do not multiply image brightness by `1/abs(det J)`. Magnifying a viewed texture does not justify that arbitrary radiance multiplier. Page gain, rim lighting and signed detail enhancement are separately declared operations.

## 12. Material and lighting remain explicit UI operators

Keep the currently validated page kernel/tint response as the initial baseline. Fix source duplication, transforms and source transport before changing its photometry. A rejected affine depth-tone family did not rule out every nonlinear/source-dependent compositor, and the earlier gauge bug/identical toolbar targets must not reappear as evidence of a universal theorem.

The rim remains an authored layer unless a single physically constrained reflection model actually passes. The n≈1.5 reflection normals were less inconsistent than n≈1.845 in one test, but neither physical candidate passed every gate; that result did not identify a measured index. A V3 highlight can be driven by the true 2D normal and a declared depth profile without pretending to be a full 3D Fresnel BRDF.

For example, a bounded rim can use a scalar depth envelope multiplied by an orientation envelope based on dot(n,lightDirection), plus an independently declared counter-light and chromatic profile. Preserve the measured baseline first. Add no screen/device-tilt sensor dependency absent a requested behavior and evidence.

The bar and selector are related layers in one component. Their visible overlap, mutual occlusion, sampled bar rim and synchronized deformation can make them read as connected without forcing every pixel through the SDF of their union. In particular, do not enlarge the tap bar's outline to conceal selector escape. If held fusion is retained, confine it to the held/contact treatment and derive it from the same two current outlines. Do not let a hidden fusion amount bypass the containment tests.

## 13. Runtime contract and cost

Per-frame CPU work is small and explicit:

1. Consume input and current layout revisions.
2. Step the handful of spring states on the common clock.
3. Build/refresh the small bar polygon only when its shape/size changes.
4. Project the five-coordinate state and active velocities.
5. Compute two disk centers/radii, two tangent normals, four tangent endpoints and inverse segment lengths.
6. Resolve transport parameters, material state and source transforms into one snapshot.

There must be no per-frame full-screen source re-record just because the selector moves, no CPU full-resolution distance transform for this analytic body, and no layout pass for every physics substep. Render once per display frame; simulation substeps change numbers, not Compose nodes.

Per selector pixel, the primary path is one constant-size geometry query, a short source-map evaluation, the existing material work, one native ink fetch, and endpoint composition. A justified spectral split costs three ink fetches. A selected edge pixel can cost four subpixel endpoint evaluations. These are operation counts, **not measured millisecond promises**. The device decides whether the budget is met.

Two 1080x240 RGBA8 ink variants occupy about 2 MiB in total. Actual allocations must match the component's bounds and padding; a tintable shared alpha mask can reduce this only when the host's glyph/palette semantics permit it. Palette/layout invalidation must be correct. Cache the stable ink when the lens moves; do not recapture the same text on each simulation substep.

Uniform/data groups should be explicit:

| Group | Examples | Update cadence |
|---|---|---|
| Body geometry | CL,CR,rL,rR,tangent endpoints/normals | per displayed frame while moving |
| Bar geometry | outline/allowed polygon/render transform | layout or actual bar change |
| Source transforms | page origin/scale, ink origin/pad/scale, texture extents | source/layout revisions |
| Transport | role, W, measured-table selector, straight a/b, held optical amount | style/state |
| Material | frozen tint/kernel/path weights, rim parameters | style/state |
| Ink | source revisions, paint variants, emphasis layout | actual content/style/layout change |

All rendering-affecting state participates in effect-cache equality/invalidation. Do not add a global mutable diagnostic override that leaks across nodes. Debug export stays explicitly opt-in and D:-scoped.

## 14. Integration into the current repository

Keep the existing public behavior default until the new candidate passes its intended checks. V3 can be an immutable opt-in profile/controller selection; it does not need an immediate semver release. New constructor controls go at the end or in a separate immutable configuration object so existing positional source calls remain valid. Do not promise binary compatibility merely because Kotlin source still compiles.

Suggested implementation boundaries; names can change if the repository has a better convention:

| Component | Responsibility |
|---|---|
| `GlassSelectorDynamics` | production mode state, spring stepping, admission and grasp; pure Kotlin math with an input adapter |
| `GlassSelectorGeometry` | two-disk body, exact shared query parameters and containment constraints |
| `GlassSelectorConstraints` | five-variable projection and feasibility diagnostics |
| `GlassInkSource` | one semantic layout, native-resolution paint variants, no duplicate accessibility row |
| `GlassSelectorCompositor` | complete endpoint composition and aperture replacement |
| `GlassTransportSource` | shared map evaluation/derivatives for explicit source roles |

These are conceptual boundaries, not a mandate to create six unnecessary files or expose all internals publicly.

- Replace the V3 branch of `GlassTabBar.kt`'s global capsule gel/lift coupling with the production controller and analytic body. Preserve tested input ownership, callbacks, stale-velocity repair and cancellation semantics.
- Refactor its `barState` capture so the selector's through-source is material-only. Ordinary semantic ink remains in C0; selected semantic ink is part of C1.
- Extend the material/content pipeline in `LiquidGlass.kt` only as needed to provide explicit source transforms and independent native ink. Do not rewrite generic `refractContent` for every consumer unless that change is intentional and regression-tested.
- Share actual V3 geometry/transport helpers across its panel/content/compositor code. Existing generic held/legacy paths are not evidence that new V3 nodes may disagree on coordinates.
- Keep `GlassRestMapTable` as authority for measured corners. Add the straight-edge program locally to the V3 bar role instead of globally changing full sheet formation.
- Resolve interpolated dimensions/material states continuously; audit `GlassMorph.kt` forwarding and any midpoint identity changes that could affect the new path.
- Vitals `FloatingNavBar.kt` opts into the new reusable behavior and retains its own routes, typography, palette and hide policy. It must not contain hardcoded WhatsApp coordinates or physics that belong in the library.

Below shader-capable Android, keep an honest simple accessible fallback. Do not crash because the analytical model has no supported runtime shader. Build the relevant KMP targets already supported by the project; do not invent a cross-platform parity claim from Android-only tests.

## 15. The verification matrix is part of the model

There are three distinct kinds of evidence. Keep separate columns for (1) equation/implementation correctness, (2) source/reference similarity and (3) owner visual acceptance. A synthetic test can prove that the shader implements a bad-looking equation. A nice still can hide a broken release handler. Neither alone completes V3.

New numeric targets below are engineering gates, not measured Apple tolerances. Freeze the cases and definitions before tuning. Do not hide failed/localization-missing cases by averaging only the successes. Retain prior gates for unaffected measured/default paths at their existing tolerances.

### 15.1 Cheap algebra and geometry tests, before rendering changes

| Test | Procedure and prediction | Acceptance / rejection and action |
|---|---|---|
| Disk-body limits | Sweep allowed L,r,k including near-degenerate boundaries; compare analytic arc/segment query to a dense CPU boundary oracle. k=0 must match the standard capsule. | Distance and nearest-point error <=0.05 physical px away from medial ties; finite values throughout. If wrong, fix geometry before optics. |
| Support containment | Check the two disk inequalities and independently densely sample the final contour against the original allowed bar. Include both ends, min/max widths and skew signs. | No positive physical protrusion beyond 0.2 px conservative-polygon/roundoff target. A solver “success” with an escaping contour is a failure. |
| No coupling hidden in center | Vary k at fixed cx,L,r. | Horizontal extrema remain cx±(L/2+r), within 1e-5 of the relevant coordinate scale in double-reference math; production Float tolerance measured separately. If not, fix parameter conversion. |
| Static velocity contact | Project arbitrary bounded velocities at active constraints. | No outward active-plane velocity beyond numerical tolerance; weighted velocity norm does not increase. If it does, fix projection; do not damp it away later. |
| Endpoint algebra | Render different black/white/saturated B0/B1 and different alpha glyphs at m=0,.25,.5,.75,1. | Within one output code level of the complete-endpoint equation, including edge pixels. If wrong, fix compositor before visual tuning. |
| Equal-endpoint invariant | Set C0=C1 while moving arbitrary coverage. | The output is unchanged to one output level. Any silhouette appearing solely from duplicated alpha is a failure. |

Do not write tests that merely copy the implementation expression and call agreement a proof. Use geometric oracles, synthetic compositions with known expected colors, and independent coordinate-coded readback.

### 15.2 Production controller scenarios

Use the **actual controller** connected to the real `GlassTabBar`. Preserve and run the existing actual-component input tests. Add deterministic trace assertions for the new shape state, not a second demonstration-only motion model.

Required scenarios:

- Selected-tab tap; adjacent tap; 2-slot and 4-slot tap, both directions.
- Rapid retarget while accelerating and while braking.
- Press during travel, hold without moving, then release.
- Slow held drag across an icon/label boundary; freeze the finger for 300 ms; continue; reverse.
- A two-second stationary hold after admission: no runaway neighbor capture or perpetual target changes.
- Hard held sweep both directions, release at each end, and a reversal before release.
- Move, hold stationary for 400 ms, lift: no stale fling.
- Cancellation before slop, during drag, after passing tabs; competing vertical scroll; second finger; external selection; node disposal/recreation; reduced motion.
- Font scale 1.0 and at least one larger supported setting; varied label lengths; density/size change.

Numeric gates:

1. At the same simulated timestamps under 60/90/120 Hz presentation, center and extents differ by <=0.5 physical px, with inputs carrying identical timestamps. A 50 ms and a 100 ms delayed frame must not produce nonfinite state or stale fling. Record the maximum, not just the last settled frame.
2. No event-induced discontinuity at the same timestamp greater than 0.25 physical px in position/extents, except an explicitly documented lifecycle/layout rebase. Normal frame-to-frame movement is not an event discontinuity.
3. Ordinary tap trajectories remain contained at all substeps, not only recorded frames. No full held protrusion during a short tap. A rendered glow must not defeat the visible constraint.
4. Unconstrained critical center traces follow the section 6 prediction. The long-move extra-length drive exceeds the short-move drive before saturation; actual body deformation and constraint saturation are reported separately.
5. Once a normal tap/release is finished, center and size are within 0.5 dp of their current target by 1 s, with no stale preview/held state. This is an authored settlement budget. If visual timing changes, keep the budget or explicitly report the failure.
6. In feasible held accommodation, the padded admitted label union is covered after the 300 ms stationary interval, the grasp error is <=1 physical px, and no unrelated third item is admitted through growth. If maximum width makes coverage infeasible, show the declared clamped behavior and classify that scenario as constrained rather than passed.
7. Existing callback counts and cancel/no-select behavior remain exact. A shape improvement never excuses a navigation regression.

A test harness that cannot deliver OS ACTION_CANCEL must say so. Consumption/vertical takeover and actual production code coverage remain useful but are not an injected OS event. Do not spend this pass inventing an entire Android input infrastructure solely to claim an unavailable test.

### 15.3 Ink clarity and spatial anchoring

Make a deterministic fixture using the production bar: fixed backdrop, five items, short and long labels, large and small stroke detail. Include black, white and colored ink. Keep the bar visible while the page source moves.

Capture diagnostic sources at rest, tap peak velocity, arrival deformation, held onset, partial overlap, stationary expansion, hard held swipe and release. Establish all of these:

- With semantic ink disabled, **neither** the bar material nor selector material contains any icon or label. If switching selected ink off reveals a blurred ordinary label beneath it, the source graph is still wrong.
- With all source displacement zero and identical paint variants, moving/deforming the aperture changes ink anchors by <=0.25 physical px on JVM and <=0.5 px on device. No global gel texture scale survives this test.
- Identity-map glyph 10–90% edge width is within 0.5 device px of direct native-resolution rendering at the same intended glyph scale; contrast is at least 90%. Score each visible label/stroke polarity, not the mean of an mostly empty ROI.
- In distorted regions, compare against native-resolution supersampling of the **same declared map**. Additional edge spread <=1 device px. Account for the map Jacobian: geometrical stretch changes width legitimately. If a stroke cannot be localized, retain its image and mark the measurement unavailable, not automatically passed.
- At normal viewing scale there are no duplicate selected/unselected ghosts, whole-label haze or a label that rides with the lens texture. Local crisp rim distortion and an intentional localized color split may remain.
- Fractional-pixel sweeps and moving-page frame markers show no source-origin snapping, stale-image trail or unrelated letter appearing from an improperly clamped source margin.

These quantitative controls do not overrule an obvious bad 1x motion video. Inspect that video after the controls pass.

### 15.4 Edge transport tests and predictions

Use the same known-source fixture to move a coordinate ramp, nonperiodic landmarks, stripes and text across the top/bottom straight runs and both corners. Page positions are exact fixture inputs; capture them with the corresponding output. Do not use a changing app page as the only calibration source.

1. JVM readback of implemented Q must match the CPU map within 0.25 reference px, including transition regions and branch identities. On device, target <=1 physical px where the source can be localized. Match the units in the record; reference px and device px are not interchangeable.
2. For the unmodified straight seed, record the predicted fold at 0.3545W and minimum source depth 0.5697W. Locate the turning point by the coordinate-coded map, not a bright highlight. Target <=0.01W or the established readback uncertainty, whichever is larger. A wrong turning point is implementation failure; a correctly rendered but visually wrong fold is model failure.
3. For three source landmarks close enough to the fold for the quadratic approximation, check the square-root growth of the paired-image separation. Declare their offsets before rendering. Use the full polynomial to calculate the exact predicted roots if the local approximation error exceeds 0.25 reference px; do not reject correct full-map rendering for an approximation outside its range.
4. A landmark in the measured corner table retains its intended source/branch placement under the V3 path. Keep the previous measured-interval gates and report shallow-rim extrapolation separately.
5. A smooth straight/corner transition must not show a new seam, direction kink or source jump. Read the full 2D map/Jacobian; do not use only a normal-depth plot to pass it.
6. The held ink map is continuous through its connection band. Its sharp source is compressed as declared rather than replaced with a broad opacity blend of two labels. Verify both source polarity and fractional position.

Finally compare the qualitative pattern with Apple's 6685–6687: readable stretched/reversed page structure along straight edges and a coherent transition into the interior. Backgrounds differ, so this visual comparison is not a precise photometric fit. If the implemented fold is correct but wrong for the reference pattern, follow the one-coefficient revision/fallback in section 19.

### 15.5 Real-device integration and performance

Build the library, run relevant tests, publish the verified local artifact, rebuild sample and Vitals, install on the discovered authorized physical device, and confirm package identity/update time. Re-test Vitals itself, including its real typography, accent color, nav hide behavior and page changes. Leave the final chosen candidate installed, not a timing baseline.

Record baseline/final alternating runs under the same scene and gesture trace after comparable warm-up. Gate: median frame cost no more than 2 ms worse and p95 no more than 10% worse. Record thermal state and the exact metric. Frame completion latency is not display presentation time; do not relabel it. Absolute 90 Hz smoothness remains a separate result even if the relative gate passes.

Measure the analytic body and ink compositor as distinct costs where possible. First optimize dirty-region bounds, shared snapshots, cached ink and redundant capture/layout work. Then reduce broad backdrop cost if needed. Do not restore reduced-resolution/blurred ink to make the number look better.

Two-minute active soak: no crash/ANR, leaked interaction ownership, stuck held state or uncontrolled repeated allocation. Record initial/mid/end memory and source/geometry cache behavior. If memory rises and then plateaus, state exactly that observation. Do not claim a two-minute test proves unlimited stability.

Save playable physical-device videos of every owner scenario, especially the middle frames. Endpoint screenshots cannot certify morphing. Include real-time playback and a labeled slowed diagnostic copy when useful; do not present slowed motion as the actual response speed.

## 16. Exact files and reference grouping

Read `D:\Work\AndroidStudioProjects\SideProjects\kmp\liquidglass\research\briefs\HANDOFF.md` first for the original toolchain/traps, then this document. The repositories are separate:

| Repository | Branch | Inspected base HEAD |
|---|---|---|
| `D:\Work\AndroidStudioProjects\SideProjects\kmp\liquidglass` | `research/ios27-measurements` | `aa9e97e` |
| `D:\Work\AndroidStudioProjects\SideProjects\kmp\Vitals` | `feat/liquidglass-0.2-measured` | `7d60cd3` |

Both were dirty. Rediscover state; do not assume HEAD or dirty files stayed unchanged while another model worked. Enter the correct repo before every git command. Read applicable AGENTS.md files. Preserve all existing work and record the starting manifest/diff. No resets, commits, pushes, tags or releases are authorized.

Read the Phase 3 result at `D:\Work\AndroidStudioProjects\SideProjects\kmp\liquidglass\research\analysis\closeout\phase3\FINAL-HANDOFF.md` for the installed baseline, prior regressions and its exact scope. The broader physical reassessment is `D:\Work\AndroidStudioProjects\SideProjects\kmp\liquidglass\research\analysis\OPTICAL-REASSESSMENT.md`; its old numerical readings were later revised, so use it for the derivational context, not as authority over later records. Do not reread the entire historical corpus.

New reference directories, small and explicitly authorized to inspect:

- `D:\Work\AndroidStudioProjects\SideProjects\kmp\liquidglass\research\datasets/ios27-phone\iphone's-ref-bubble`
- `D:\Work\AndroidStudioProjects\SideProjects\kmp\liquidglass\research\datasets/ios27-phone\our's-latest-bubble-glass`

The Apple page-edge stills are `IMG_6685.HEIC`, `IMG_6686.HEIC`, `IMG_6687.HEIC` in:

`C:\Users\shaya\AppData\Local\Packages\5319275A.WhatsAppDesktop_cv1g1gvanyjgm\LocalState\sessions\53A9D18659FD953F2A9B354BEB162BDC012B7995\transfers\2026-37\`.

Owner's current Vitals page-edge crops:

- `C:\Users\shaya\AppData\Local\Temp\codex-clipboard-49d20397-304c-4da7-8e5d-ec5c74ce3142.png`
- `C:\Users\shaya\AppData\Local\Temp\codex-clipboard-37fa6e45-6562-4ad6-8a13-0064521c48da.png`
- `C:\Users\shaya\AppData\Local\Temp\codex-clipboard-374137a5-7000-4505-b631-01a3ead7a7ae.png`

Preserve these few transient sources onto D: with original-byte hashes if not already preserved by the owner-final pass. Never overwrite originals.

Reference use:

| Case | Apple evidence | Android failure baseline |
|---|---|---|
| Straight page edge | 6685–6687 | three PNGs above; 6705, 6712–6714 |
| Short tap | 6699 then 6700/6701 | replay adjacent tabs |
| Long tap | 6690, 6698, owner-described 6700/6695/6693/6694, then 6701 | 6705–6709, 6711 |
| Held onset / partial overlap | 6717, 6718, 6719 | reproduce production path |
| Stationary-finger accommodation | 6721 and owner's account | new explicit production fixture |
| Hard held swipe | 6734, 6735; additional 6723/6726 | both directions |
| End release | 6724, 6727–6733 in owner-described progression | both ends |

Read all stills in the two directories for context, not just the favorable listed subset. Numeric filenames are not timestamps or consecutive-video-frame numbers. Where the owner supplies an order, preserve it as an asserted progression and do not manufacture equal timing.

### 16.1 Decoder contract

The extension `.HEIC` is not sufficient. Apple examples such as 6698 and 6685 are JPEG/MPO with ICC profiles and multiple embedded images. Our 6707 is actual HEIF; inspected files include 10-bit content. An auxiliary image may be a gain map, not a second temporal frame. Inspect signature, metadata, profile and auxiliary roles before choosing a decode.

The old original videos are PQ/BT.2020. Their required decode remains:

`zscale=t=linear:npl=100,format=gbrpf32le,zscale=p=bt709:t=iec61966-2-1:m=bt709:r=full,format=rgb24`.

That chain must not be applied blindly to an already display-referred JPEG base. Record whether still comparisons use an ICC-converted SDR base or correctly reconstructed HDR. Do not derive exact luminance from unverified previews, gain-map auxiliary images or JPEG ringing. Geometry/shape comparisons can still be useful with declared display rendering.

Visual-only contact sheets already exist at `D:\Work\AndroidStudioProjects\SideProjects\kmp\liquidglass\research\analysis\owner-final\apple-contact.jpg`, `ours-contact.jpg`, `page-edge-reference.jpg`, plus `image-metadata.json`. They are indexes, not calibrated photometric evidence. Inspect original-resolution crops before deciding sharpness.

Use D: for scratch/captures/APKs. Do not enumerate the 66 GB analysis tree or load whole transcript JSONL files. The ffmpeg path is in HANDOFF. The adb executable is `D:\DevSetup\Android\Sdk\platform-tools\adb.exe`; discover serials afresh. Pixel 7 is preferred when available, Xiaomi is authorized for routine work, Huawei/emulator are not substitute final evidence. No historical transport ID is assumed current.

## 17. Numerical claims that must not return under V3 names

- No recovered n, thickness, gap, viscosity or volume conservation from the current evidence. The approximately 1.845 value is a gauge/model member, not an independently measured index.
- No old constant resting plateau as a summary of the measured folded corner. The measured table, unmeasured shallow extension and localization coverage are distinct.
- No “single branch / every gate passed” claim when the mirrored sheet branch failed or only a localized ring subset was scored.
- No physical ten-pixel held jump inferred from seam models that tied or both missed the pixel data. The new connection is explicitly authored.
- No “a fold requires broad blur,” no caustic brightness multiplier `1/|det J|`, and no claim that normal-direction compression by itself enlarges chromatic landmark separation. For a landmark, `delta_d≈-delta_s/s_d` where the local linearization holds.
- No assumption that color in a rim over black measures transmitted wavelength dispersion. No maximum-alpha RGB union described as correct general colored transmission.
- No universal rejection of photometry from the invalid zero-mean penalty fit or from two targets that were identical in the toolbar source ROI. Preserve the corrected narrow result: the tested affine depth family with frozen geometry/kernel failed those data.
- No full-resolution Vitals artifact blamed on an unobserved renderScale override.
- No statement that `scaleX=1+e`, `scaleY=1-e` preserves area exactly; their product is `1-e²`, and area would not establish volume anyway.
- No image-history lag, travel-distance law or exact Apple spring inferred from unordered stills. V3's distance response follows its explicitly authored controller.
- No absolute frame-budget success inferred from a relative timing pass, and no claim that a frame completion counter is measured display presentation.
- No “owner approved” label before the owner sees the actual final motion.

## 18. Work order and bounded tuning

Work as one implementation agent. Reuse the current harness. The goal is an implemented V3 candidate, not another collection of research cards. No further Astra approval is required for routine technical decisions within this scope.

| Order | Work | Approximate effort, not a promise |
|---|---|---|
| 1 | Preserve baseline, inspect originals/source graph, write scenarios, make diagnostic ink/material fixture | Small: tens of minutes to a few hours depending on the existing fixture |
| 2 | Material-only source and exact endpoint compositor; prove native ink and identity anchoring | Medium: often the most valuable early implementation block |
| 3 | Pure geometry/controller, small constraints, production adapter, tap/held/accommodation tests | Largest block: several focused hours, potentially longer if the input adapter needs repair |
| 4 | Straight-edge program and continuous held map; retain corners; inspect real intermediate frames | Medium; bounded map tuning, not another inverse-model search |
| 5 | Full relevant suite, build/publish/install, matched device videos/performance, final cleanup | Medium; device availability is the main external dependency |

These ranges depend on implementation skill and actual defects. Do not tell the owner a deadline is guaranteed. Make useful checkpoints after each block so a model/context change loses no work.

Freeze and record the test scenes before fitting/tuning. Tune geometry and motion against both directions and all travel distances together. Tune transport against both source polarities and all relevant edge sectors together. Do not use a separate parameter set for a single bad screenshot. When a withheld scene is inspected and used to revise a candidate, label it validation/development data thereafter; do not keep claiming it is an untouched holdout.

Allow one primary implementation and at most two targeted revisions per issue family. An algebraic or coding bug is repaired rather than treated as a new physical theory. After the bounded revisions, keep the best verified components and explicitly classify remaining limitations. The limit prevents endless model search, not the completion of necessary builds, tests or straightforward fixes.

Do not turn this future V3 design into an uncontrolled same-turn rewrite if another owner-final implementation is already running. Read its latest checkpoint and diff, preserve working improvements, and apply this as the controlling technical design for the V3 work when executing the prompt. No concurrent writers to the same repository files.

## 19. Decision tree and fallback ownership

Every branch terminates in implementation, a targeted correction or an honest deliverable. None requires another Astra message.

**Ink still ghosts after source separation:** inspect the no-ink substrate, endpoint algebra and identity-map source coordinates. If the substrate has ink, repair capture ordering. If algebra fails, repair complete endpoint replacement. If anchors move, remove the residual post-render shape transform/rounding. If those pass but strokes smear, compare native one-tap and supersampled same-map references: reduce accidental resampling or correct minification filtering. Do not tune global page opacity to hide the glyph.

**The combined shader is too invasive:** build C1 completely in one small offscreen endpoint layer, then draw it with aperture alpha over C0. This costs an intermediate but implements the same correct algebra. Profile it; do not return to two translucent ink copies.

**The tapered body cannot match a verified reference shape:** first verify that highlight lines were not mistaken for the actual coverage boundary. If the boundary is convex but needs more than unequal end radii, the single bounded extension is the convex hull of three disks on the travel axis. Its containment still reduces to disk-plane inequalities; CPU precomputes exposed arcs and common tangents, and the shared distance query has a fixed small feature count. Admit the third radius only for a demonstrated middle-bulge residual. Do not build a general fluid solver. If reliable concavity remains essential, record that this convex model cannot match it and deliver the safe candidate as partial instead of inventing hidden parity. The owner may decide later whether a nonconvex V4 is worthwhile.

**Tap shape feels stiff despite containment passing:** retain the critical center and vary the bounded shape damping/drive within the preset ranges. Inspect k sign and independent end recovery. Do not reintroduce a globally bouncing center or a blanket texture scale. If the owner prefers a little center overshoot, it still must obey whole-body constraints; keep it a deliberate variant measured against the default.

**Content accommodation oscillates or grows indefinitely:** disable feedback from the expanded outline into admission, retain at most one adjacent neighbor, ensure bounds are from one stable layout/emphasis snapshot, and keep beta fixed for the grasp. If the label physically cannot fit the declared maximum, report a constrained layout state. Never expand all the way across the bar as an accidental chain reaction.

**The fold candidate renders correctly but looks wrong:** first distinguish source ordering from filter/contrast. If branch ordering is wrong, use the one b coefficient or adjust the bounded bar-only a/W parameters against all scenes. If geometry is correct but detail disappears, retain the map and repair the fine source/filter block. If a radiance mixture is required for page material, add only its declared path with explicit weights; the semantic ink stays single-map by default. No per-image correction or hidden depth-tone sweep.

**Frame-time gate fails:** profile state/geometry allocation, repeated source recording, duplicated item layout and the bounded compositor. Use analytic body geometry, native cached ink and reduced broad backdrop. Disable optional spectral split or extra edge supersamples only when their own reference gates remain satisfied. Do not blur ink or remove requested morphology to make a benchmark pass without marking that compromise.

**No physical device is available:** finish all offline code/tests and produce the APK, exact commands and pending device matrix. Do not claim device success, use an emulator as proof, or stop halfway through feasible implementation. If Pixel is absent but Xiaomi is present, use Xiaomi and state the device. A later Pixel comparison is then a remaining comparison, not a reason to hold all work.

**A remaining owner gate still fails after bounded work:** retain working improvements and produce a concrete review build with PASS/PARTIAL/FAIL per issue. Do not rename a conspicuous flaw “fine optical detail,” and do not restart E0–E10. The owner can judge the tradeoff from the actual video.

## 20. Delivery and execution prompt

Write implementation records to `D:\Work\AndroidStudioProjects\SideProjects\kmp\liquidglass\research\analysis\v3\` and large captures/APKs to a clearly named D: subdirectory. This document remains the model specification; do not rewrite it after a failing test to make that test appear to have passed. Add deviations/results separately.

The final packet contains:

- `V3-HANDOFF.md`: four owner issues, PASS/PARTIAL/FAIL, evidence links, known differences, exact reproduction steps and pending owner sign-off.
- `MODEL-DECISIONS.md`: final parameter values/units, choices made on the bounded branches, which are measured/inherited/authored, and results that invalidated a hypothesis.
- `MANIFEST.json`: starting/final repo HEADs and changed files, artifact hashes, installed package/device/update time, test/capture provenance. No credentials or unrelated personal data.
- Native-resolution before/after crops, playable real-time physical-device videos for all scenarios, and labeled slow diagnostic views where useful.
- Relevant fresh test results and matched performance/soak data, with all important failures/localization gaps retained.
- A compact checkpoint sufficient to resume after compaction; no long transcript dump or repeated requests for the next ruling.

Leave the chosen final Vitals candidate installed. No push, commit, tag, release or publish to a remote registry. Local Maven publication needed for the app is authorized. The final report must say what changed, why it changed, what was verified and what remains visibly different, without claiming an Apple-identical implementation.

Use this prompt to execute:

> Read `D:\Work\AndroidStudioProjects\SideProjects\kmp\liquidglass\research\briefs\HANDOFF.md` first, then read `D:\Work\AndroidStudioProjects\SideProjects\kmp\liquidglass\research\analysis\V3-MODEL.md` in full. This is my authorized V3 implementation brief and supersedes the earlier final-prompt technical suggestions where they differ. Preserve all existing dirty work and any completed owner-final improvements. You own the technical decisions and completion; no further Astra approval is required. Implement the shared tapered-body geometry and constraints, the exact material-only/ink endpoint compositor, the continuous interaction controller and bounded transport programs. Reuse the current harness and production component, validate the intermediate motion on an authorized physical device, follow the specified revisions/fallbacks, and deliver the installed review build with honest issue status. Do not stop after planning, invent measured constants, weaken gates or start another research cycle. Work as one agent and checkpoint through compaction. No commits, pushes, tags or releases. Start now.

The intended result is a maintainable, visibly better library with a precise model and falsifiable limits. The document supplies the reasoning needed to build and judge that result; the implementation and the owner's visual review must establish whether it succeeds.

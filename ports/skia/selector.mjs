/**
 * Apache-2.0. The Calm navigation selector, ported from the Compose library's pose controller
 * (GlassPoseDynamics.kt, GlassPoseGeometry.kt and GlassTabBar's gesture driver). Pure motion
 * and geometry: no DOM, no Skia. A line-by-line translation verified against trajectories the
 * Kotlin controller exports (fixtures/selector-*.json); the Kotlin source is authoritative.
 *
 * Units: bar-local pixels (origin at the bar's top-left), seconds.
 */

const PI = Math.PI;
const clamp = (x, lo, hi) => Math.min(Math.max(x, lo), hi);

/** Authored presets; see GlassPoseSpec in the Compose library for every value's provenance. */
const DEFAULT_SPEC = Object.freeze({
  centreOmega: 40, centreZeta: 1, spineOmega: 90, spineZeta: 1,
  tapSpineOmega: 36, tapSpineZeta: .43, tapElongationSlots: .36, tapReferenceSpeed: 15,
  spineReleaseOmega: 32, shapeOmega: 32, shapeZeta: .72, pressureOmega: 45, pressureZeta: 1,
  tapPressureOmega: 0, pressureFallOmega: 14, formRiseOmega: 60, heldFormRiseOmega: 0,
  formOmega: 18, formZeta: 1, holdThresholdSeconds: .12, referenceSpeed: 11.5,
  heldReferenceSpeed: 20, velocityTau: .035, referenceAcceleration: 26, elongationSlots: .5,
  acrossElongation: 0, stretchGain: 0, heldTravelStrain: 0, heldTravelStrainOmega: 10, maxStrain: .42, pullStrainPerDp: .0038,
  pullPressurePerDp: -.0007, pullOnsetDp: 12, pullMaxStrain: .12, pullFollow: .2,
  pullFollowCapRatio: .1, endTravelCapRatio: .08, pullTiltSeconds: .08, heldHeightRatio: 1.183,
  squeeze: .4, taperFromAcceleration: 0, taperFromVelocity: 0, maxTaper: 0,
  formFromMovement: .25, pressFormation: .3, maxAccommodationSlots: .2,
  accommodationFadeSpeed: 2.5, accommodationEngageSpeed: .35, accommodationReleaseSpeed: .8,
  accommodationDwellSeconds: .15, accommodationOmega: 16, admitPaddingDp: 6, admitInsetDp: 2,
  admitReleaseDp: 4, heldExcursionRatio: .5, graspFollowAcross: 1, graspOmega: 78,
  accelerationTau: .045, stopWindowSeconds: .04, travelOnly: false, releaseTravel: false,
  tapRecoilArea: false, heldTravelPressureOmega: 0, tapSpineLimitSlots: .36, tapAreaGain: 0,
  tapAreaOmega: 42, tapAreaReferenceSpeed: 2.4, tapBodyElongation: .15, tapSpineFallOmega: 12,
  tapAreaSpineOmega: 40, tapAreaSpineZeta: .38, tapAreaBodySpeed: 10.2, releaseImpactGain: 0,
  releaseEdgeAnchor: false, releaseRecoveryOmega: 0, releaseRecoveryZeta: 1,
  releaseTrailingEdge: false, releaseTrailingInOmega: 0, releaseTrailingInZeta: 1, releaseTrailingCarry: 1, releaseWallGive: 0,
  releaseTrailingStiffening: 0,
  tapShortTripRate: 1, tapAppearanceArea: 0, tapAppearanceSpine: 0,
  tapAppearanceFallOmega: 0, releaseFormationSpeed: 0, releaseStillHoldSeconds: 0,
  heldSquircle: 0, squircleExponent: 3,
});

/** GlassPoseSpec.Calm: CalmIndependent -> CalmVolume -> CalmEndpoint -> CalmImpact -> CalmFreeLanding -> Calm. */
export const CALM_SELECTOR_SPEC = Object.freeze({
  ...DEFAULT_SPEC,
  centreOmega: 24, spineOmega: 30, spineZeta: .78, tapSpineOmega: 26, tapSpineZeta: .45,
  tapElongationSlots: .36, tapReferenceSpeed: 10, spineReleaseOmega: 22, pressureOmega: 22,
  pressureFallOmega: 14, formRiseOmega: 30, tapPressureOmega: 45, heldHeightRatio: 1.183,
  heldReferenceSpeed: 8, elongationSlots: .28, squeeze: .47, pullStrainPerDp: 0,
  pullPressurePerDp: 0, pullFollow: 0, endTravelCapRatio: .025, maxAccommodationSlots: .15,
  travelOnly: true, releaseTravel: true, tapRecoilArea: true, heldTravelPressureOmega: 60,
  tapSpineLimitSlots: .45, tapAreaGain: .51, tapShortTripRate: .75, tapAppearanceArea: .44,
  tapAppearanceSpine: .13, tapAppearanceFallOmega: 45, releaseFormationSpeed: 3,
  graspOmega: 120, heldFormRiseOmega: 50, releaseTrailingEdge: true, releaseRecoveryOmega: 13,
  releaseTrailingInOmega: 9, releaseTrailingInZeta: .3, releaseTrailingCarry: .1, releaseWallGive: .35,
  releaseTrailingStiffening: .5, releaseStillHoldSeconds: .18, heldTravelStrain: .6, heldTravelStrainOmega: 10,
  heldSquircle: .7,
});

const MODE = Object.freeze({Rest: 'rest', TapTransit: 'tap', PressPending: 'pending', Held: 'held', Released: 'released'});
const CONTOUR_SAMPLES = 256, QUIET_PX = .02, QUIET_STEPS = 8, CONTACT_TOLERANCE = .05;
const MAX_CONTACT_ITERATIONS = 24, MAX_CONTACT_STEP = 12, MAX_CONTACT_PRESSURE_STEP = .06;
const CONTACT_REGAIN_MARGIN = 0, CONTACT_REGAIN_ACCOMMODATION = 1, ACCOMMODATION_RESOLVE_PX = 1;
const ACCOMMODATION_RESOLVE_PRESSURE = .01, COVERAGE_EPSILON = .5, CONTACT_REGAIN_FORMATION = .02;
const REGRAB_FORMATION = .15, GEOMETRY_EPSILON = 1e-3, BEVEL_RATIO = .6, WALL_PULL_HEIGHTS = 20, RELEASE_IMPACT_REACH = .3, RELEASE_STILL_SLOTS = .5;

class Spring {
  constructor(value = 0, velocity = 0) { this.value = value; this.velocity = velocity; this.target = value; }
  snapTo(x) { this.value = x; this.target = x; this.velocity = 0; }
  step(dt, omega, zeta) {
    if (dt <= 0 || omega <= 0) return;
    const d = this.value - this.target;
    if (d === 0 && this.velocity === 0) return;
    const z = clamp(zeta, 0, 1);
    if (z >= .9999) {
      const e = Math.exp(-omega * dt);
      const dNew = e * ((1 + omega * dt) * d + dt * this.velocity);
      const vNew = e * ((1 - omega * dt) * this.velocity - omega * omega * dt * d);
      this.value = this.target + dNew; this.velocity = vNew;
    } else {
      const a = z * omega, b = omega * Math.sqrt(1 - z * z), e = Math.exp(-a * dt);
      const cb = Math.cos(b * dt), sb = Math.sin(b * dt);
      const dNew = e * (d * cb + (this.velocity + a * d) * sb / b);
      const vNew = e * (this.velocity * cb - (a * this.velocity + omega * omega * d) * sb / b);
      this.value = this.target + dNew; this.velocity = vNew;
    }
  }
}

function boundedTravel(travel, limit) {
  if (!Number.isFinite(limit)) return travel;
  if (limit <= 0) return 0;
  const cap = Math.max(limit, 1e-3);
  return cap * Math.tanh(travel / cap);
}
function anchoredTravel(value, lower, upper, limit) {
  if (value < lower) return lower + boundedTravel(value - lower, limit);
  if (value > upper) return upper + boundedTravel(value - upper, limit);
  return value;
}
function heldPressure(ratio, barHeight, referenceHeight) {
  if (referenceHeight <= 1e-4 || barHeight <= 1e-4) return 0;
  return Math.log((ratio * barHeight) / referenceHeight);
}

/**
 * The bar as the selector sees it (buildGlassSelectorBar, pose path). Pixels. `inset` is the
 * item row's horizontal padding, `pillWidth` the resting selector width (default: one slot),
 * `pillInset` the bar showing above and below the resting selector, `density` px per dp.
 * `slotWidth` (GlassTabBarStyle.slotWidth): equal slots of that width centred in the bar, the end
 * tabs closed up until their pill sits `pillInset` inside the bar's end; omitted, the bar is
 * shared out inside `inset`.
 */
export function calmSelectorBar({width, height, count, inset = 6, pillWidth, pillInset = 4, density = 1, slotWidth}) {
  if (![width, height, inset, pillInset, density].every(Number.isFinite) || width <= 0 || height <= 0 ||
      !Number.isInteger(count) || count < 1 || density <= 0 || inset < 0 || pillInset < 0)
    throw new RangeError('Positive bar size, integer count and nonnegative insets required');
  const fixed = Number.isFinite(slotWidth) && slotWidth > 0;
  const slot = fixed ? slotWidth : Math.max((width - inset * 2) / count, 0);
  const pill = Number.isFinite(pillWidth) ? Math.max(pillWidth, slot) : slot;
  const fixedSlot = fixed ? slot : NaN, endReach = fixed ? pill / 2 + pillInset : 0;
  const first = fixed ? fixedCentre(width, count, slot, endReach, 0) : inset + slot / 2;
  const restHalfWidth = Math.min(pill / 2, Math.max(first - density, density));
  const pillHalfHeight = Math.min(height / 2 - pillInset, restHalfWidth);
  return Object.freeze({
    width, height, inset, count, cornerRadius: height / 2, baseHalfWidth: restHalfWidth,
    baseHalfHeight: pillHalfHeight, allowedHalfHeight: Math.max(pillHalfHeight, height / 2 - density),
    density, slotWidth: slot, fixedSlot, endReach,
  });
}
// glassFixedSlotCentre: equal slots centred in the bar, held endReach from either end.
function fixedCentre(width, count, slot, endReach, i) {
  const mid = width / 2, lo = Math.min(endReach, mid);
  return clamp(mid + (i - (count - 1) / 2) * slot, lo, width - lo);
}
export const centreOf = (bar, i) => bar.fixedSlot > 0
  ? fixedCentre(bar.width, bar.count, bar.fixedSlot, bar.endReach, i) : bar.inset + (i + .5) * bar.slotWidth;
/** The tab under `x`: the nearest centre for set slots, the slot under it otherwise. */
export function nearestTab(bar, x) {
  if (!(bar.fixedSlot > 0)) return clamp(Math.floor((x - bar.inset) / bar.slotWidth), 0, bar.count - 1);
  let i = 0;
  while (i < bar.count - 1 && x > (centreOf(bar, i) + centreOf(bar, i + 1)) / 2) i++;
  return i;
}

class Reference {
  constructor(a, r) { this.a = a; this.r = r; this.spine = a > GEOMETRY_EPSILON; }
  get width() { return 2 * (this.a + this.r); }
  get height() { return 2 * this.r; }
}

const newPose = () => ({cx: 0, cy: 0, p: 0, u: 0, v: 0, kx: 0, ky: 0, acc: 0, dx: 0, dy: 0});
const copyPose = (to, from) => Object.assign(to, from);
const resetPose = pose => Object.assign(pose, newPose());
const rho = pose => Math.sqrt(pose.u * pose.u + pose.v * pose.v);

/** GlassPoseFrame: the pose map's derived quantities for one frame. */
class Frame {
  constructor() {
    this.reference = new Reference(1, 1); this.a = [1, 0, 0, 1]; this.aInv = [1, 0, 0, 1];
    this.bx = 0; this.by = 0; this.spine = 0; this.radius = 0; this.maxAbsZ = 1; this.scaleX = 1;
    this.cx = 0; this.cy = 0; this.expP = 1; this.minStretch = 1; this.maxStretch = 1;
    this.cosTheta = 1; this.sinTheta = 0; this.halfSpine = 0;
    this.contourRef = null; this.contourPoints = null;
  }
  update(reference, pose) {
    this.reference = reference; this.cx = pose.cx; this.cy = pose.cy;
    const r = rho(pose); this.expP = Math.exp(pose.p);
    let h, s;
    if (r < 1e-3) { const r2 = r * r; h = 1 + r2 / 2 + r2 * r2 / 24; s = 1 + r2 / 6 + r2 * r2 / 120; }
    else { h = Math.cosh(r); s = Math.sinh(r) / r; }
    let ex = pose.dx, ey = pose.dy;
    if (ex * ex + ey * ey <= 1e-6) ex = reference.a * this.expP;
    if (reference.spine) ex += Math.max(pose.acc, 0);
    const dl = Math.sqrt(ex * ex + ey * ey);
    if (dl > 1e-3) {
      const c2 = ex / dl, s2 = ey / dl;
      this.cosTheta = Math.sqrt(Math.max((1 + c2) / 2, 0));
      const st = Math.sqrt(Math.max((1 - c2) / 2, 0)); this.sinTheta = s2 < 0 ? -st : st;
      this.halfSpine = dl;
    } else { this.cosTheta = 1; this.sinTheta = 0; this.halfSpine = 0; }
    const ct = this.cosTheta, sn = this.sinTheta, e = this.expP;
    const s00 = h + s * pose.u, s01 = s * pose.v, s11 = h - s * pose.u;
    this.a = [e * (ct * s00 - sn * s01), e * (ct * s01 - sn * s11), e * (sn * s00 + ct * s01), e * (sn * s01 + ct * s11)];
    const inv = 1 / e, i00 = h - s * pose.u, i01 = -s * pose.v, i11 = h + s * pose.u;
    this.aInv = [inv * (i00 * ct - i01 * sn), inv * (i00 * sn + i01 * ct), inv * (i01 * ct - i11 * sn), inv * (i01 * sn + i11 * ct)];
    if (reference.spine) {
      this.scaleX = 1; this.spine = Math.max(this.halfSpine * inv, 0); this.radius = reference.r;
      this.maxAbsZ = this.spine + this.radius;
    } else {
      this.scaleX = Math.exp(pose.acc); this.spine = Math.max(this.halfSpine * inv, 0) * this.scaleX;
      this.radius = reference.r;
      this.maxAbsZ = Math.max(this.scaleX * (this.halfSpine * inv + reference.r), Math.sqrt(this.spine ** 2 + this.radius ** 2));
    }
    const rs = Math.max(this.maxAbsZ, 1e-4); this.bx = pose.kx / rs; this.by = pose.ky / rs;
    const taper = Math.sqrt(pose.kx * pose.kx + pose.ky * pose.ky);
    this.minStretch = e * Math.exp(-r) * Math.max(1 - taper, 1e-3);
    this.maxStretch = e * Math.exp(r) * (1 + taper);
  }
  materialToZ(qx, qy, out) {
    const ref = this.reference;
    if (ref.spine) {
      const ell = this.spine - ref.a, phi = ref.a > GEOMETRY_EPSILON ? clamp(qx / ref.a, -1, 1) : 0;
      out[0] = qx + ell * phi; out[1] = qy;
    } else { out[0] = qx * this.scaleX; out[1] = qy; }
  }
  zToMaterial(zx, zy, out) {
    const ref = this.reference;
    if (ref.spine) {
      const ell = this.spine - ref.a;
      out[0] = Math.abs(zx) <= this.spine ? (this.spine > GEOMETRY_EPSILON ? zx * ref.a / this.spine : zx)
        : zx - (zx >= 0 ? ell : -ell);
      out[1] = zy;
    } else { out[0] = this.scaleX > 1e-6 ? zx / this.scaleX : zx; out[1] = zy; }
  }
  zToRendered(zx, zy, out) {
    const s = 1 + this.bx * zx + this.by * zy, fx = s * zx, fy = s * zy, a = this.a;
    out[0] = this.cx + a[0] * fx + a[1] * fy; out[1] = this.cy + a[2] * fx + a[3] * fy;
  }
  materialToRendered(qx, qy, out) { this.materialToZ(qx, qy, out); this.zToRendered(out[0], out[1], out); }
  renderedToZ(x, y, out) {
    const dx = x - this.cx, dy = y - this.cy, ai = this.aInv;
    const yx = ai[0] * dx + ai[1] * dy, yy = ai[2] * dx + ai[3] * dy;
    const h = this.bx * yx + this.by * yy, disc = 1 + 4 * h;
    if (disc < 0) { out[0] = yx; out[1] = yy; return false; }
    const lambda = 2 / (1 + Math.sqrt(disc)); out[0] = lambda * yx; out[1] = lambda * yy; return true;
  }
  /** Coverage distance only (negative inside), as the controller's probes use it. */
  coverage(x, y) {
    const z = [0, 0];
    if (!this.renderedToZ(x, y, z)) return Math.max(Math.hypot(z[0], z[1]), 1e-4);
    const zx = z[0], zy = z[1], ref = this.reference;
    let ex, ey, half, rad;
    if (ref.spine) { half = this.spine; rad = this.radius; ex = zx - clamp(zx, -half, half); ey = zy; }
    else {
      const q = [0, 0]; this.zToMaterial(zx, zy, q); half = ref.a; rad = ref.r;
      ex = q[0] - clamp(q[0], -half, half); ey = q[1];
    }
    const len = Math.sqrt(ex * ex + ey * ey), d = len - rad;
    let n0x = 0, n0y = 1;
    if (len > 1e-5) { n0x = ex / len; n0y = ey / len; }
    const s = 1 + this.bx * zx + this.by * zy;
    let j0 = s + zx * this.bx, j1 = zx * this.by, j2 = zy * this.bx, j3 = s + zy * this.by;
    if (!ref.spine) { j0 *= this.scaleX; j2 *= this.scaleX; }
    const a = this.a;
    const m0 = a[0] * j0 + a[1] * j2, m1 = a[0] * j1 + a[1] * j3, m2 = a[2] * j0 + a[3] * j2, m3 = a[2] * j1 + a[3] * j3;
    const det = m0 * m3 - m1 * m2, invDet = Math.abs(det) > 1e-9 ? 1 / det : 0;
    const gx = invDet * (m3 * n0x - m2 * n0y), gy = invDet * (-m1 * n0x + m0 * n0y);
    const gl = Math.sqrt(gx * gx + gy * gy);
    return gl > 1e-7 ? d / gl : d;
  }
  band() { return BEVEL_RATIO * this.radius * this.minStretch; }
  contour(n, into) {
    const ref = this.reference;
    if (this.contourRef !== ref || this.contourPoints.length !== n * 2) {
      this.contourRef = ref; this.contourPoints = new Float64Array(n * 2);
      for (let i = 0; i < n; i++) {
        const q = canonicalContourPoint(ref, i / n); this.contourPoints[i * 2] = q[0]; this.contourPoints[i * 2 + 1] = q[1];
      }
    }
    const out = [0, 0];
    for (let i = 0; i < n; i++) {
      this.materialToRendered(this.contourPoints[i * 2], this.contourPoints[i * 2 + 1], out);
      into[i * 2] = out[0]; into[i * 2 + 1] = out[1];
    }
  }
}

function canonicalContourPoint(ref, t) {
  const a = ref.a, r = ref.r, capLen = PI * r, runLen = 2 * a, total = 2 * capLen + 2 * runLen;
  if (total <= 1e-6) return [0, 0];
  let s = t * total;
  if (s < capLen) { const ang = -PI / 2 + (s / capLen) * PI; return [a + r * Math.cos(ang), r * Math.sin(ang)]; }
  s -= capLen;
  if (s < runLen) return [a - s, r];
  s -= runLen;
  if (s < capLen) { const ang = PI / 2 + (s / capLen) * PI; return [-a + r * Math.cos(ang), r * Math.sin(ang)]; }
  s -= capLen;
  return [-a + s, -r];
}

/** GlassPoseController, Calm-capable. Pointer facts in the bar frame; read state each frame. */
export class CalmSelectorController {
  constructor(spec = CALM_SELECTOR_SPEC) {
    this.spec = Object.freeze({...DEFAULT_SPEC, ...spec});
    this.pose = newPose(); this.velocity = newPose(); this.equilibrium = newPose();
    this.frame = new Frame(); this.reference = new Reference(1, 1); this.bar = null;
    this.mode = MODE.Rest; this.motionEnabled = true; this.started = false;
    this.form = new Spring(); this.transit = new Spring(); this.travel = new Spring(); this.tapArea = new Spring();
    this.strainDrive = new Spring();
    this.tapAreaActive = false; this.tapDistance = 0; this.releaseImpactActive = false;
    this.releaseEdge = new Spring(); this.releaseTrail = new Spring(); this.releaseEdgeActive = false; this.trailImpact = false;
    this.releasedAt = 0; this.releasedStill = false;
    this.now = 0; this.pressStart = 0; this.downX = 0; this.downY = 0; this.downOnBody = false;
    this.pressEligible = false; this.pointerDownActive = false; this.lastSampleAt = 0;
    this.graspQx = 0; this.graspQy = 0; this.hasGrasp = false; this.graspY0 = 0; this.slowFor = 0;
    this.accommodating = false; this.releasedFromHold = false; this.graspX = new Spring(); this.graspY = new Spring();
    this.pointerX = 0; this.pointerY = 0; this.pointerVx = 0; this.pointerVy = 0; this.lastPointerX = 0;
    this.lastPointerY = 0; this.lastPointerTime = 0; this.hasPointerTime = false; this.pointerClockOrigin = 0;
    this.pointerSimulationOrigin = 0; this.accelX = 0; this.accelY = 0; this.lastVx = 0; this.lastVy = 0;
    this.restIndex = 0; this.requestedIndex = 0; this.interactionId = 0;
    this.itemLeft = []; this.itemRight = []; this.itemCentre = []; this.inkHalfHeight = 0;
    this.admitted = -1; this.admittedNeighbour = -1; this.accommodationDemand = 0;
    this.accommodationValid = false; this.accommodationGrasp = NaN; this.accommodationGraspY = NaN;
    this.accommodationPressure = NaN; this.contour = new Float64Array(CONTOUR_SAMPLES * 2);
    this.contactFailures = 0; this.solverFailed = false; this.lastGood = newPose();
    this.lastGoodVelocity = newPose(); this.hasLastGood = false; this.quietSteps = 0;
    this.lastPublished = newPose(); this.formLast = 0; this.probePose = newPose(); this.probeFrame = new Frame();
    this.derivFrame = new Frame(); this.scratchPose = newPose(); this.shapeVel = [0, 0];
  }

  get formation() { return clamp(this.form.value, 0, 1); }
  get transitAppearance() { return clamp(this.transit.value, 0, .95); }
  get heldTravel() { return clamp(this.travel.value, 0, 1); }
  get isHeld() { return this.mode === MODE.Held; }
  get centreX() { return this.pose.cx; }
  get centreY() { return this.pose.cy; }
  get centreVelocityX() { return this.velocity.cx; }
  get isIdle() {
    return this.mode !== MODE.PressPending && !this.tapAreaActive && this.transitAppearance < 1e-4 &&
      this.heldTravel < 1e-4 && this.quietSteps >= QUIET_STEPS && this.strainDrive.value === 0 &&
      // A still release keeping its glass: nothing moves yet, but the formation still has to fall.
      !(this.mode === MODE.Released && this.releasedFromHold && this.releasedStill && this.form.value > .01 &&
        this.now - this.releasedAt < this.spec.releaseStillHoldSeconds + .05);
  }

  attach(bar, itemBounds = null, itemCentres = null, inkHalfHeight = this.inkHalfHeight) {
    const first = !this.bar;
    const relayout = first || !sameBar(this.bar, bar);
    this.bar = bar;
    if (this.inkHalfHeight !== Math.max(inkHalfHeight, 0)) this.accommodationValid = false;
    this.inkHalfHeight = Math.max(inkHalfHeight, 0);
    if (itemBounds && itemCentres && itemCentres.length) {
      for (let i = 0; i < itemCentres.length; i++) {
        if (this.itemLeft[i] !== itemBounds[i * 2] || this.itemRight[i] !== itemBounds[i * 2 + 1] ||
            this.itemCentre[i] !== itemCentres[i]) this.accommodationValid = false;
        this.itemLeft[i] = itemBounds[i * 2]; this.itemRight[i] = itemBounds[i * 2 + 1]; this.itemCentre[i] = itemCentres[i];
      }
      this.itemLeft.length = this.itemRight.length = this.itemCentre.length = itemCentres.length;
    }
    if (!relayout) return;
    const r = Math.max(bar.baseHalfHeight, 1);
    this.reference = new Reference(Math.max(bar.baseHalfWidth - r, 0), r);
    if (!first && this.started) { this.interactionId++; this.clearOwnership(); this.snapToRest(this.requestedIndex); }
  }

  snapToRest(index) {
    this.releaseEdgeActive = false; this.tapAreaActive = false; this.tapDistance = 0; this.releaseImpactActive = false;
    this.requestedIndex = index; this.restIndex = clamp(index, 0, Math.max(this.bar.count - 1, 0));
    resetPose(this.pose); resetPose(this.velocity);
    this.pose.cx = centreOf(this.bar, this.restIndex); this.pose.cy = this.bar.height / 2;
    this.pose.dx = this.reference.a; this.pose.dy = 0;
    this.form.snapTo(0); this.transit.snapTo(0);
    this.graspX.snapTo(this.pose.cx); this.graspY.snapTo(this.pose.cy); this.hasGrasp = false;
    this.admitted = -1; this.admittedNeighbour = -1; this.mode = MODE.Rest; this.started = true; this.solverFailed = false;
    this.frame.update(this.reference, this.pose); this.captureLastGood(); copyPose(this.lastPublished, this.pose);
    this.formLast = 0; this.quietSteps = QUIET_STEPS;
  }

  retarget(index) {
    const n = Math.max(this.bar.count - 1, 0);
    if (index !== this.requestedIndex && this.mode !== MODE.Held && this.mode !== MODE.Released)
      this.tapDistance = Math.abs(centreOf(this.bar, clamp(index, 0, n)) - this.pose.cx);
    this.requestedIndex = index; this.restIndex = clamp(index, 0, n);
    if (Math.abs(this.pose.cx - centreOf(this.bar, this.restIndex)) >= .25) {
      this.quietSteps = 0; if (this.mode === MODE.Rest) this.mode = MODE.TapTransit;
    }
  }

  clearOwnership() {
    this.releaseEdgeActive = false; this.releaseImpactActive = false; this.accommodationValid = false;
    this.pressEligible = false; this.pointerDownActive = false; this.hasGrasp = false;
    this.pointerVx = 0; this.pointerVy = 0; this.accelX = 0; this.accelY = 0; this.admitted = -1; this.admittedNeighbour = -1;
  }

  pointerDown(x, y, eventSeconds, eligible) {
    this.releaseEdgeActive = false; this.releaseImpactActive = false;
    this.pressStart = this.now; this.pressEligible = eligible; this.pointerDownActive = true;
    this.downX = x; this.downY = y;
    this.frame.update(this.reference, this.pose);
    this.downOnBody = this.frame.coverage(x, y) <= 0 && this.frame.renderedToZ(x, y, [0, 0]);
    this.pointerX = x; this.pointerY = y; this.lastPointerX = x; this.lastPointerY = y;
    this.lastPointerTime = eventSeconds; this.pointerClockOrigin = eventSeconds; this.pointerSimulationOrigin = this.now;
    this.hasPointerTime = true; this.pointerVx = 0; this.pointerVy = 0; this.accelX = 0; this.accelY = 0;
    this.lastVx = 0; this.lastVy = 0; this.lastSampleAt = this.now; this.quietSteps = 0;
    const regrab = this.mode === MODE.Released && eligible && this.motionEnabled && this.form.value > REGRAB_FORMATION;
    this.mode = MODE.PressPending;
    if (regrab) { this.acquireGrasp(x, y, this.downOnBody); this.mode = MODE.Held; }
  }

  pointerMove(x, y, eventSeconds) {
    const s = this.spec;
    if (s.travelOnly && this.hasPointerTime && Number.isFinite(eventSeconds)) {
      const eventTime = this.pointerSimulationOrigin + (eventSeconds - this.pointerClockOrigin);
      if (Number.isFinite(eventTime) && eventTime > this.now) this.advanceTo(eventTime);
    }
    this.pointerX = x; this.pointerY = y; this.lastSampleAt = this.now; this.quietSteps = 0;
    if (this.hasPointerTime) {
      const dt = eventSeconds - this.lastPointerTime;
      if (dt > 1e-5) {
        let vx;
        if (s.travelOnly && this.mode === MODE.Held && this.hasGrasp) {
          const o = [0, 0]; this.frame.materialToRendered(this.graspQx, this.graspQy, o);
          const offset = o[0] - this.pose.cx;
          vx = (this.anchoredCentre(x - offset) - this.anchoredCentre(this.lastPointerX - offset)) / dt;
        } else vx = (x - this.lastPointerX) / dt;
        const vy = (y - this.lastPointerY) / dt;
        const alpha = 1 - Math.exp(-dt / Math.max(s.accelerationTau, 1e-4));
        this.accelX += alpha * ((vx - this.lastVx) / dt - this.accelX);
        this.accelY += alpha * ((vy - this.lastVy) / dt - this.accelY);
        this.lastVx = vx; this.lastVy = vy;
        const va = 1 - Math.exp(-dt / Math.max(s.velocityTau, 1e-4));
        this.pointerVx += va * (vx - this.pointerVx); this.pointerVy += va * (vy - this.pointerVy);
        this.lastPointerX = x; this.lastPointerY = y; this.lastPointerTime = eventSeconds;
      }
    }
  }

  beginDrag(x, y) {
    this.pointerX = x; this.pointerY = y; this.quietSteps = 0;
    if (this.mode !== MODE.Held) { this.acquireGrasp(this.downX, this.downY, this.downOnBody); this.mode = MODE.Held; }
  }

  pointerUp(restIndex) {
    this.quietSteps = 0; this.interactionId++; this.requestedIndex = restIndex;
    this.restIndex = clamp(restIndex, 0, Math.max(this.bar.count - 1, 0));
    this.releasedFromHold = this.mode === MODE.Held; this.releasedAt = this.now;
    this.releasedStill = Math.abs(this.velocity.cx) < RELEASE_STILL_SLOTS * Math.max(this.bar.slotWidth, 1);
    const s = this.spec;
    this.releaseImpactActive = this.releasedFromHold && (s.releaseImpactGain > 0 || s.releaseTrailingEdge) &&
      (this.restIndex === 0 || this.restIndex === this.bar.count - 1) &&
      Math.abs(this.velocity.cx) > this.reference.width * .6;
    this.mode = this.releasedFromHold ? MODE.Released : MODE.TapTransit;
    this.pressEligible = false; this.pointerDownActive = false; this.hasGrasp = false;
    this.pointerVx = 0; this.pointerVy = 0; this.accelX = 0; this.accelY = 0;
  }
  cancel(restIndex) { this.pointerUp(restIndex); }

  acquireGrasp(x, y, onBody) {
    this.accommodationValid = false;
    this.frame.update(this.reference, this.pose);
    const z = [0, 0], q = [0, 0];
    this.frame.renderedToZ(x, y, z); this.frame.zToMaterial(z[0], z[1], q);
    const a = this.reference.a, r = Math.max(this.reference.r, 1e-3);
    let qx = q[0], qy = q[1];
    const ex = qx - clamp(qx, -a, a), len = Math.sqrt(ex * ex + qy * qy);
    if (len > r) { const sc = r / len; qx = clamp(qx, -a, a) + ex * sc; qy *= sc; }
    if (!onBody) { qx = 0; qy = (y - this.bar.height / 2) / this.frame.expP; }
    this.graspY0 = y; this.slowFor = 0; this.accommodating = false;
    this.graspQx = qx; this.graspQy = qy; this.hasGrasp = true;
    const o = [0, 0]; this.frame.materialToRendered(qx, qy, o);
    this.graspX.snapTo(o[0]); this.graspY.snapTo(o[1]); this.graspX.target = x; this.graspY.target = y;
  }

  advanceFrameTo(time) { if (this.spec.travelOnly && time < this.now) return; this.advanceTo(time); }
  resumedFrameTime(previous) { return this.spec.travelOnly ? Math.max(previous, this.now) : previous; }

  advanceTo(time, maxCatchUpSeconds = .5) {
    if (Number.isNaN(time)) return;
    if (time < this.now) {
      const shift = time - this.now; this.now = time; this.pressStart += shift; this.lastSampleAt += shift;
      this.pointerSimulationOrigin += shift; return;
    }
    let remaining = time - this.now;
    if (remaining <= 0) return;
    if (!this.motionEnabled) { this.snapToRest(this.restIndex); this.now = time; return; }
    if (remaining > maxCatchUpSeconds) {
      if (!this.pointerDownActive && (this.mode === MODE.Held || this.mode === MODE.PressPending)) {
        this.pointerVx = 0; this.pointerVy = 0; this.mode = MODE.Released;
      }
      remaining = maxCatchUpSeconds;
    }
    const s = this.spec;
    const maxStep = (this.releaseImpactActive || (s.tapAreaGain > 0 && this.tapDistance > .25 &&
      (this.mode === MODE.TapTransit || this.mode === MODE.PressPending))) ? 1 / 960 : 1 / 240;
    while (remaining > 1e-7) {
      const dt = Math.min(remaining, maxStep);
      this.substep(dt); remaining -= dt; this.now += dt;
    }
    this.now = time;
  }

  stepState(dt, value, velocity, target, omega, zeta) {
    const sp = this._scratchSpring ??= new Spring();
    sp.value = value; sp.velocity = velocity; sp.target = target; sp.step(dt, omega, zeta);
    return [sp.value, sp.velocity];
  }
  anchoredCentre(x) {
    return anchoredTravel(x, centreOf(this.bar, 0), centreOf(this.bar, this.bar.count - 1), this.spec.endTravelCapRatio * this.bar.height);
  }
  // The held travel strain (heldTravelStrain) widens the rendered body by e^u; zero leaves it 1.
  strainScale() { return this.spec.heldTravelStrain > 0 ? Math.exp(this.pose.u) : 1; }
  landingHalfWidth() { return (this.reference.r * Math.exp(this.pose.p) + this.pose.dx + Math.max(this.pose.acc, 0)) * this.strainScale(); }
  landingHalfWidthVelocity() {
    const eu = this.strainScale(), base = this.reference.r * Math.exp(this.pose.p) + this.pose.dx + Math.max(this.pose.acc, 0);
    return (this.reference.r * Math.exp(this.pose.p) * this.velocity.p + this.velocity.dx + (this.pose.acc > 0 ? this.velocity.acc : 0)) * eu +
      (eu !== 1 ? base * eu * this.velocity.u : 0);
  }

  substep(dt) {
    const s = this.spec, pose = this.pose, vel = this.velocity, eq = this.equilibrium, bar = this.bar, ref = this.reference;
    if (!this.motionEnabled && (this.mode === MODE.Held || this.mode === MODE.PressPending)) {
      this.interactionId++; this.clearOwnership(); this.mode = MODE.Rest;
    }
    const heldNow = this.motionEnabled && (this.mode === MODE.Held ||
      (this.mode === MODE.PressPending && this.pressEligible && this.now - this.pressStart >= s.holdThresholdSeconds));
    const slot = Math.max(bar.slotWidth, 1);
    let vx, vy;
    if (this.mode === MODE.Held) {
      if (this.now - this.lastSampleAt > s.stopWindowSeconds) {
        const decay = Math.exp(-dt / .06);
        this.pointerVx *= decay; this.pointerVy *= decay; this.accelX *= decay; this.accelY *= decay;
      }
      vx = this.pointerVx; vy = this.pointerVy;
    } else if (this.mode === MODE.Released) { vx = s.releaseTravel ? vel.cx : 0; vy = 0; }
    else { vx = vel.cx; vy = vel.cy; }
    const nvx = vx / slot, nvy = s.travelOnly ? 0 : vy / slot;
    const speed2 = nvx * nvx + nvy * nvy, speed = Math.sqrt(speed2);
    const tapMotion = this.mode !== MODE.Held && (this.mode !== MODE.Released || s.releaseTravel);
    const coupleTapArea = this.motionEnabled && s.tapAreaGain > 0 && this.tapDistance > .25 &&
      (this.mode === MODE.TapTransit || this.mode === MODE.PressPending);
    let tapClock = 1;
    if (coupleTapArea) {
      const phase = clamp(this.tapDistance / slot - 1, 0, 1), blend = phase * phase * (3 - 2 * phase);
      const shortRate = clamp(s.tapShortTripRate, .5, 1); tapClock = shortRate + (1 - shortRate) * blend;
    }
    const tapShapeVx = vx / tapClock;
    const coupleReleaseArea = this.motionEnabled && this.releaseImpactActive && this.mode === MODE.Released;
    const coupleReleaseEdge = coupleReleaseArea && (s.releaseEdgeAnchor || s.releaseTrailingEdge) && s.travelOnly &&
      s.stretchGain === 0 && s.taperFromAcceleration === 0 && s.taperFromVelocity === 0 && ref.spine;
    const dir = this.restIndex === 0 ? -1 : 1;
    if (coupleReleaseEdge && !this.releaseEdgeActive) {
      this.releaseEdge.value = pose.cx + dir * this.landingHalfWidth();
      this.releaseEdge.velocity = vel.cx + dir * this.landingHalfWidthVelocity();
      this.releaseEdge.target = centreOf(bar, this.restIndex) + dir * ref.width * .5;
      this.releaseTrail.value = pose.cx - dir * this.landingHalfWidth();
      this.releaseTrail.velocity = vel.cx - dir * this.landingHalfWidthVelocity(); this.trailImpact = false;
      this.releaseTrail.target = centreOf(bar, this.restIndex) - dir * ref.width * .5;
    }
    this.releaseEdgeActive = coupleReleaseEdge;
    const edgeVx = this.releaseEdge.velocity;
    const twoEdge = coupleReleaseEdge && s.releaseTrailingEdge;
    const coupleArea = coupleTapArea || (coupleReleaseArea && !s.releaseTrailingEdge);
    if (coupleArea && !this.tapAreaActive) {
      const radius = ref.r * Math.exp(pose.p), spine = Math.hypot(pose.dx, pose.dy);
      const spineVelocity = spine > 1e-5 ? (pose.dx * vel.dx + pose.dy * vel.dy) / spine : 0;
      this.tapArea.snapTo(PI * radius * radius + 4 * spine * radius);
      this.tapArea.velocity = (2 * PI * radius + 4 * spine) * radius * vel.p + 4 * radius * spineVelocity;
    }
    this.tapAreaActive = coupleArea;
    const distanceExcess = coupleTapArea ? Math.max(this.tapDistance / ref.width - 2, 0) : 0;
    const longTapDrive = 1 - Math.exp(-distanceExcess * distanceExcess / (distanceExcess + .5));
    const vRef = Math.max(this.mode === MODE.Held ? s.heldReferenceSpeed : tapMotion ? s.tapReferenceSpeed : s.referenceSpeed, 1e-3);
    const sv = speed2 / (vRef * vRef + speed2);
    this.travel.target = this.motionEnabled && (this.mode === MODE.Held ||
      (this.mode === MODE.Released && this.releasedFromHold)) ? sv : 0;
    this.travel.step(dt, 60, 1);
    if (Math.abs(this.travel.value) < 1e-4 && Math.abs(this.travel.velocity) < .002 && this.travel.target === 0) this.travel.snapTo(0);
    const pressResponse = heldNow ? 1 : (this.pointerDownActive && this.pressEligible ? s.pressFormation : 0);
    let formTarget;
    if (!this.motionEnabled) formTarget = 0;
    else if (this.mode === MODE.Released) {
      const v = s.releaseFormationSpeed;
      if (v > 0 && this.releasedFromHold) {
        const rs = vel.cx / slot;
        formTarget = this.releasedStill && this.now - this.releasedAt < s.releaseStillHoldSeconds
          ? this.form.value : Math.min(this.form.value, rs * rs / (v * v + rs * rs));
      }
      else formTarget = 0;
    } else formTarget = 1 - (1 - pressResponse) * (1 - s.formFromMovement * sv);
    this.form.target = formTarget;
    const riseOmega = heldNow && s.heldFormRiseOmega > 0 ? s.heldFormRiseOmega : s.formRiseOmega;
    this.form.step(dt, formTarget > this.form.value ? riseOmega : s.formOmega, s.formZeta);

    // ---- equilibria
    const formation = this.formation;
    const gain = this.motionEnabled ? 2 * s.stretchGain * Math.max(formation, .35) : 0;
    const denom = vRef * vRef + speed2;
    const density = Math.max(bar.density, 1e-3);
    const pullY = this.mode === MODE.Held && this.motionEnabled && this.hasGrasp ? this.graspY.value - this.graspY0 : 0;
    const distanceDp = Math.abs(pullY) / density;
    const effectiveDp = distanceDp * distanceDp / Math.max(distanceDp + s.pullOnsetDp, 1e-4);
    const strainLimit = Math.max(Math.min(s.pullMaxStrain, s.maxStrain * .95), 1e-4);
    const pullStrain = strainLimit * Math.tanh(s.pullStrainPerDp * effectiveDp / strainLimit);
    let pullU = 0, pullV = 0;
    if (pullStrain > 1e-5) {
      const tiltX = vx * s.pullTiltSeconds;
      const psi = Math.atan2(pullY, tiltX) - Math.atan2(this.frame.sinTheta, this.frame.cosTheta);
      pullU = pullStrain * Math.cos(2 * psi); pullV = pullStrain * Math.sin(2 * psi);
    }
    if (s.heldTravelStrain > 0) {
      // Sustained held travel builds the strain; a short flick releases before it has.
      this.strainDrive.target = this.mode === MODE.Held && this.motionEnabled ? sv * formation : 0;
      this.strainDrive.step(dt, s.heldTravelStrainOmega, 1);
      if (Math.abs(this.strainDrive.value) < 1e-4 && Math.abs(this.strainDrive.velocity) < .002 && this.strainDrive.target === 0) this.strainDrive.snapTo(0);
    }
    eq.u = gain * (nvx * nvx - .5 * speed2) / denom + pullU + s.heldTravelStrain * this.strainDrive.value;
    eq.v = gain * (nvx * nvy) / denom + pullV;
    const held = heldPressure(s.heldHeightRatio, bar.height, ref.height);
    const squeeze = this.mode === MODE.Held ? s.squeeze * sv : 0;
    const pullPressure = s.pullPressurePerDp * pullStrain / Math.max(s.pullStrainPerDp, 1e-4);
    const transitPressure = this.mode === MODE.TapTransit && s.tapPressureOmega > 0;
    const pressureFormation = transitPressure ? formTarget : formation;
    const formedSqueeze = s.heldTravelPressureOmega > 0 ? squeeze * pressureFormation : squeeze;
    eq.p = this.motionEnabled ? held * pressureFormation - formedSqueeze + pullPressure : 0;
    const recoilPressure = !coupleTapArea && transitPressure && s.tapRecoilArea && this.motionEnabled &&
      Math.hypot(pose.dx, pose.dy) < ref.a;
    if (recoilPressure) {
      const radius = ref.r * Math.exp(eq.p), restingSpine = ref.a, compressed = Math.hypot(pose.dx, pose.dy);
      if (compressed < restingSpine && radius > 0) {
        const area = PI * radius * radius + 4 * restingSpine * radius;
        const recovered = area / (Math.sqrt(4 * compressed * compressed + PI * area) + 2 * compressed);
        eq.p += Math.log(recovered / radius);
      }
    }
    const ax = this.accelX / slot, ay = this.accelY / slot, aMag = Math.sqrt(ax * ax + ay * ay);
    const aScale = Math.max(s.referenceAcceleration, 1e-3);
    if (this.motionEnabled) {
      eq.kx = s.taperFromAcceleration * ax / (aScale + aMag) + s.taperFromVelocity * nvx / (vRef + speed);
      eq.ky = s.taperFromAcceleration * ay / (aScale + aMag) + s.taperFromVelocity * nvy / (vRef + speed);
    } else { eq.kx = 0; eq.ky = 0; }
    if (this.mode === MODE.Held && this.motionEnabled) {
      if (speed < s.accommodationEngageSpeed) this.slowFor += dt;
      else if (speed > s.accommodationReleaseSpeed) { this.slowFor = 0; this.accommodating = false; }
      if (!this.accommodating && this.slowFor >= s.accommodationDwellSeconds) this.accommodating = true;
    } else { this.slowFor = 0; this.accommodating = false; }
    eq.acc = this.accommodating ? this.admittedSpine() : 0;
    let ex = ref.a;
    if (coupleTapArea) {
      const fraction = Math.abs(tapShapeVx) / Math.max(Math.abs(tapShapeVx) + s.tapAreaBodySpeed * ref.width, 1);
      const eighth = v => { const sq = v * v, f = sq * sq; return f * f; };
      const moving = eighth(fraction), still = eighth(1 - fraction);
      ex += s.tapBodyElongation * ref.width * (1 + .3 * longTapDrive) * moving / Math.max(moving + still, 1e-8);
    } else if (!coupleReleaseArea && this.motionEnabled && speed2 > 1e-9) {
      const h2 = nvx * nvx, along = h2 / (vRef * vRef + h2);
      const t = this.mode === MODE.Held && s.heldTravelPressureOmega > 0 ? along * (2 - along) : along;
      ex += (tapMotion ? s.tapElongationSlots : s.elongationSlots) * slot * t;
    }
    eq.dx = ex; eq.dy = 0;

    // ---- step the shape states
    for (const k of ['u', 'v', 'kx', 'ky']) [pose[k], vel[k]] = this.stepState(dt, pose[k], vel[k], eq[k], s.shapeOmega, s.shapeZeta);
    let pressureRate;
    if (recoilPressure) pressureRate = Math.max(s.tapPressureOmega, 4 * s.tapSpineOmega);
    else if (this.mode === MODE.Held && s.heldTravelPressureOmega > 0 && eq.p < pose.p && speed2 > 1e-9) pressureRate = s.heldTravelPressureOmega;
    else if (transitPressure) pressureRate = s.tapPressureOmega;
    else if (this.mode !== MODE.Held && eq.p < pose.p) pressureRate = s.pressureFallOmega;
    else pressureRate = s.pressureOmega;
    if (!coupleArea) [pose.p, vel.p] = this.stepState(dt, pose.p, vel.p, eq.p, pressureRate, s.pressureZeta);
    [pose.acc, vel.acc] = this.stepState(dt, pose.acc, vel.acc, eq.acc, s.accommodationOmega, 1);
    let spo, spz;
    if (coupleReleaseArea && s.releaseRecoveryOmega > 0) spo = s.releaseRecoveryOmega;
    else if (coupleTapArea && eq.dx < pose.dx) spo = s.tapSpineFallOmega * tapClock;
    else if (coupleTapArea) spo = s.tapAreaSpineOmega * tapClock;
    else if (this.mode === MODE.Released) spo = s.spineReleaseOmega;
    else if (tapMotion) spo = s.tapSpineOmega;
    else spo = s.spineOmega;
    if (coupleReleaseArea && s.releaseRecoveryOmega > 0) spz = s.releaseRecoveryZeta;
    else if (coupleTapArea) spz = s.tapAreaSpineZeta;
    else if (tapMotion) spz = s.tapSpineZeta;
    else spz = s.spineZeta;
    [pose.dx, vel.dx] = this.stepState(dt, pose.dx, vel.dx, eq.dx, spo, spz);
    [pose.dy, vel.dy] = this.stepState(dt, pose.dy, vel.dy, eq.dy, spo, spz);
    this.clampShape();
    if (coupleReleaseArea && pose.dx < .002) { pose.dx = .002; if (vel.dx < 0) vel.dx = 0; }
    if (coupleArea) {
      const r0 = ref.r, restArea = PI * r0 * r0 + 4 * ref.a * r0;
      const phaseSpeed = Math.abs(tapShapeVx) / Math.max(this.tapDistance, ref.width * .9);
      const q = phaseSpeed / Math.max(phaseSpeed + s.tapAreaReferenceSpeed, 1e-5);
      const excitation = q * q / Math.max(q * q + (1 - q) * (1 - q), 1e-8);
      this.tapArea.target = coupleReleaseArea ? restArea : restArea * (1 + s.tapAreaGain * (1 + longTapDrive) * excitation);
      this.tapArea.step(dt, coupleReleaseArea ? s.pressureFallOmega : s.tapAreaOmega * tapClock, 1);
      const area = Math.max(this.tapArea.value, restArea * .5), spine = Math.hypot(pose.dx, pose.dy);
      const spineVelocity = spine > 1e-5 ? (pose.dx * vel.dx + pose.dy * vel.dy) / spine : 0;
      const radius = area / (Math.sqrt(4 * spine * spine + PI * area) + 2 * spine);
      pose.p = Math.log(radius / r0);
      vel.p = (this.tapArea.velocity - 4 * radius * spineVelocity) / Math.max((2 * PI * radius + 4 * spine) * radius, 1e-5);
    }
    if (coupleTapArea && s.tapAppearanceArea > 0) {
      const restArea = PI * ref.r * ref.r + 4 * ref.a * ref.r;
      const extra = Math.max(this.tapArea.value / restArea - 1, 0), q = extra / s.tapAppearanceArea;
      const spine = Math.hypot(pose.dx, pose.dy), spineScale = s.tapAppearanceSpine * ref.width;
      const axial = spineScale > 0 ? Math.max(spine - ref.a, 0) / spineScale : 0;
      const axialVelocity = spineScale > 0 && spine > ref.a && spine > 1e-5 ? (pose.dx * vel.dx + pose.dy * vel.dy) / (spine * spineScale) : 0;
      const raw = q * q + axial * axial, shoulder = clamp((raw - .85) / .2, 0, 1);
      const demand = raw <= .85 ? raw : .85 + .2 * (shoulder - .5 * shoulder * shoulder);
      const slope = raw <= .85 ? 1 : 1 - shoulder;
      this.transit.target = demand;
      if (s.tapAppearanceFallOmega > 0 && demand < this.transit.value) this.transit.step(dt, s.tapAppearanceFallOmega, 1);
      else {
        this.transit.value = demand;
        this.transit.velocity = slope * 2 * (q / s.tapAppearanceArea * this.tapArea.velocity / restArea + axial * axialVelocity);
      }
    } else {
      this.transit.target = 0; this.transit.step(dt, s.formOmega, 1);
      if (Math.abs(this.transit.value) < 1e-4 && Math.abs(this.transit.velocity) < .002) this.transit.snapTo(0);
    }

    // ---- the centre
    if (this.mode === MODE.Held && this.hasGrasp) {
      this.frame.update(ref, pose);
      const o = [0, 0]; this.frame.materialToRendered(this.graspQx, this.graspQy, o);
      const shapeX = o[0] - pose.cx, shapeY = o[1] - pose.cy;
      this.graspX.target = this.anchoredCentre(this.pointerX - shapeX) + shapeX;
      this.graspY.target = this.pointerY;
      this.graspX.step(dt, s.graspOmega, 1); this.graspY.step(dt, s.graspOmega, 1);
      const cap = Math.max(s.pullFollowCapRatio * bar.height, 1e-4), pull = this.graspY.value - this.graspY0;
      const response = Math.tanh(s.pullFollow * pull / cap), follow = boundedTravel(s.pullFollow * pull, cap);
      pose.cx = this.graspX.value - shapeX; pose.cy = this.graspY0 + follow - shapeY;
      this.updateShapeVelocity();
      vel.cx = this.graspX.velocity - this.shapeVel[0];
      vel.cy = s.pullFollow * (1 - response * response) * this.graspY.velocity - this.shapeVel[1];
    } else if (coupleReleaseEdge) {
      const leadBefore = this.releaseEdge.value;
      this.releaseEdge.step(dt, s.centreOmega, s.centreZeta);
      if (twoEdge) {
        const give = s.releaseWallGive * bar.height;
        const wall = (dir > 0 ? bar.width : 0) + dir * give * clamp(this.form.value, 0, 1);
        const wallVelocity = this.form.value > 0 && this.form.value < 1 ? dir * give * this.form.velocity : 0;
        if (give > 0) {
          if (dir * (this.releaseEdge.value - wall) > 0) {
            const from = dir * (leadBefore - wall) > 0 ? leadBefore : wall;
            const pull = Math.min(dir * (from - wall), WALL_PULL_HEIGHTS * bar.height * dt);
            this.releaseEdge.value = from - dir * pull;
            this.releaseEdge.velocity = from === wall ? wallVelocity
              : pull < dir * (from - wall) ? -dir * WALL_PULL_HEIGHTS * bar.height : wallVelocity;
          }
        } else if (dir * this.releaseEdge.velocity > 0 && dir * (this.releaseEdge.value - wall) > 0) {
          this.releaseEdge.value = dir * (leadBefore - wall) > 0 ? leadBefore : wall; this.releaseEdge.velocity = 0;
        }
        // The leading edge meets the bar's end: the impact takes the trailing edge's speed, once.
        if (!this.trailImpact && dir * (this.releaseEdge.value - (dir > 0 ? bar.width : 0)) >= -RELEASE_IMPACT_REACH * bar.height) {
          this.trailImpact = true; this.releaseTrail.velocity *= s.releaseTrailingCarry;
        }
        if (s.releaseTrailingInOmega > 0 && dir * this.releaseTrail.velocity > 0) {
          const behind = Math.max(0, -dir * (this.releaseTrail.value - this.releaseTrail.target)) / Math.max(this.reference.width, 1);
          this.releaseTrail.step(dt, s.releaseTrailingInOmega * (1 + s.releaseTrailingStiffening * behind), s.releaseTrailingInZeta);
        } else this.releaseTrail.step(dt, s.releaseRecoveryOmega, s.releaseRecoveryZeta);
        const caps = ref.r * Math.exp(pose.p) + Math.max(pose.acc, 0);
        const capsVelocity = ref.r * Math.exp(pose.p) * vel.p + (pose.acc > 0 ? vel.acc : 0);
        const minimum = caps + .002, eu = this.strainScale();
        if (dir * (this.releaseEdge.value - this.releaseTrail.value) < 2 * minimum * eu) {
          this.releaseTrail.value = this.releaseEdge.value - dir * 2 * minimum * eu;
          this.releaseTrail.velocity = this.releaseEdge.velocity - dir * 2 * capsVelocity * eu;
        }
        // The edges are rendered extents; the strain scales the body by e^u along the bar.
        const half = dir * (this.releaseEdge.value - this.releaseTrail.value) * .5;
        pose.dx = half / eu - caps;
        vel.dx = (dir * (this.releaseEdge.velocity - this.releaseTrail.velocity) * .5 - (eu !== 1 ? half * vel.u : 0)) / eu - capsVelocity;
        pose.dy = 0; vel.dy = 0;
      }
      pose.cx = this.releaseEdge.value - dir * this.landingHalfWidth();
      vel.cx = this.releaseEdge.velocity - dir * this.landingHalfWidthVelocity();
      [pose.cy, vel.cy] = this.stepState(dt, pose.cy, vel.cy, bar.height / 2, s.centreOmega, s.centreZeta);
    } else {
      const tx = centreOf(bar, this.restIndex), ty = bar.height / 2;
      if (!this.motionEnabled) { pose.cx = tx; pose.cy = ty; vel.cx = 0; vel.cy = 0; }
      else {
        const co = s.centreOmega * tapClock;
        [pose.cx, vel.cx] = this.stepState(dt, pose.cx, vel.cx, tx, co, s.centreZeta);
        [pose.cy, vel.cy] = this.stepState(dt, pose.cy, vel.cy, ty, co, s.centreZeta);
      }
    }
    if (coupleReleaseArea && !twoEdge) {
      const driverBefore = coupleReleaseEdge ? edgeVx : vx;
      const driverAfter = coupleReleaseEdge ? this.releaseEdge.velocity : vel.cx;
      const displacement = coupleReleaseEdge ? this.releaseEdge.target - this.releaseEdge.value : centreOf(bar, this.restIndex) - pose.cx;
      const toward = driverBefore * displacement >= 0, distance = Math.abs(displacement) / (ref.width * .25);
      const proximity = Math.exp(-distance * distance);
      if (toward) vel.dx -= s.releaseImpactGain * proximity * Math.max(Math.abs(driverBefore) - Math.abs(driverAfter), 0);
    }
    this.frame.update(ref, pose);

    // ---- contact
    let wantsContact;
    if (this.mode === MODE.TapTransit || this.mode === MODE.PressPending) wantsContact = !coupleTapArea;
    else if (this.mode === MODE.Held) wantsContact = false;
    else if (this.mode === MODE.Released) wantsContact = !coupleReleaseArea && (!this.releasedFromHold || (
      this.form.value < CONTACT_REGAIN_FORMATION && pose.acc <= CONTACT_REGAIN_ACCOMMODATION && this.insideRestEnvelope(CONTACT_REGAIN_MARGIN)));
    else wantsContact = true;
    if (wantsContact) this.applyContact(); else this.solverFailed = false;

    this.frame.update(ref, pose);
    const spineLength = Math.sqrt(pose.dx * pose.dx + pose.dy * pose.dy);
    this.captureLastGood(); this.recordQuiet();
    if (this.mode === MODE.PressPending && heldNow) { this.acquireGrasp(this.downX, this.downY, this.downOnBody); this.mode = MODE.Held; }
    if (this.mode === MODE.Released || this.mode === MODE.TapTransit) {
      const settled = Math.abs(pose.cx - centreOf(bar, this.restIndex)) < .25 && Math.abs(vel.cx) < 1.5 && Math.abs(vel.cy) < 1.5 &&
        rho(pose) < .005 && Math.abs(pose.p) < .005 && this.form.value < .01 && Math.abs(spineLength - ref.a) < .25 &&
        Math.abs(vel.dx) < 1.5 && Math.abs(vel.dy) < 1.5;
      const radiusError = ref.r * (Math.exp(pose.p) - 1);
      const visuallySettledArea = coupleArea && this.quietSteps >= QUIET_STEPS && Math.abs(pose.cx - centreOf(bar, this.restIndex)) < .125 &&
        Math.abs(pose.cy - bar.height / 2) < .125 && this.form.value < .01 &&
        Math.abs(spineLength - ref.a + radiusError) < .125 && Math.abs(radiusError) < .125;
      const radiusVelocity = ref.r * Math.exp(pose.p) * vel.p;
      const slowTapQuiet = Math.max(Math.abs(vel.cx), Math.abs(radiusVelocity), Math.abs(vel.dx + radiusVelocity)) < ref.width * .002;
      const canFinish = coupleTapArea && tapClock < 1 ? visuallySettledArea && slowTapQuiet : settled || visuallySettledArea;
      if (canFinish) {
        if (coupleArea) this.snapToRest(this.restIndex);
        else { this.mode = MODE.Rest; this.tapDistance = 0; this.tapAreaActive = false; }
      }
    }
  }

  updateShapeVelocity() {
    const h = 1 / 4800, out = [0, 0];
    let loX = 0, loY = 0, hiX = 0, hiY = 0;
    for (const sign of [-1, 1]) {
      const sp = copyPose(this.scratchPose, this.pose), v = this.velocity;
      for (const k of ['u', 'v', 'kx', 'ky', 'p', 'acc', 'dx', 'dy']) sp[k] += sign * h * v[k];
      sp.cx = 0; sp.cy = 0;
      this.derivFrame.update(this.reference, sp);
      this.derivFrame.materialToZ(this.graspQx, this.graspQy, out);
      this.derivFrame.zToRendered(out[0], out[1], out);
      if (sign < 0) { loX = out[0]; loY = out[1]; } else { hiX = out[0]; hiY = out[1]; }
    }
    this.shapeVel[0] = (hiX - loX) / (2 * h); this.shapeVel[1] = (hiY - loY) / (2 * h);
  }

  insideRestEnvelope(margin) {
    this.frame.contour(CONTOUR_SAMPLES, this.contour);
    for (let i = 0; i < CONTOUR_SAMPLES; i++) if (this.barSignedDistance(this.contour[i * 2], this.contour[i * 2 + 1]) > -margin) return false;
    return true;
  }

  applyContact() {
    const pose = this.pose, vel = this.velocity, c = this.contour;
    let iterations = 0, worst;
    do {
      this.frame.update(this.reference, pose); this.frame.contour(CONTOUR_SAMPLES, c);
      worst = -Number.MAX_VALUE; let wx = 0, wy = 0;
      for (let i = 0; i < CONTOUR_SAMPLES; i++) {
        const d = this.barSignedDistance(c[i * 2], c[i * 2 + 1]);
        if (d > worst) { worst = d; wx = c[i * 2]; wy = c[i * 2 + 1]; }
      }
      if (worst <= 0) break;
      const e = .5;
      const gx = (this.barSignedDistance(wx + e, wy) - this.barSignedDistance(wx - e, wy)) / (2 * e);
      const gy = (this.barSignedDistance(wx, wy + e) - this.barSignedDistance(wx, wy - e)) / (2 * e);
      const gl = Math.sqrt(gx * gx + gy * gy);
      if (gl < 1e-5) break;
      const nx = gx / gl, ny = gy / gl;
      let opposite = -Number.MAX_VALUE;
      for (let i = 0; i < CONTOUR_SAMPLES; i++) {
        const x = c[i * 2], y = c[i * 2 + 1];
        if ((x - pose.cx) * nx + (y - pose.cy) * ny >= 0) continue;
        opposite = Math.max(opposite, this.barSignedDistance(x, y));
      }
      const bilateral = Math.min(worst, Math.max(opposite, 0)), oneSided = worst - bilateral;
      if (oneSided > 0) { const t = Math.min(oneSided, MAX_CONTACT_STEP); pose.cx -= nx * t; pose.cy -= ny * t; }
      if (bilateral > 0) {
        const spineLength = Math.sqrt(pose.dx * pose.dx + pose.dy * pose.dy), extra = spineLength - this.reference.a;
        if (extra > .5 && Math.abs(nx) > Math.abs(ny)) {
          const take = Math.min(bilateral, extra), sc = Math.max((spineLength - take) / spineLength, .5);
          pose.dx *= sc; pose.dy *= sc; vel.dx *= sc; vel.dy *= sc;
        } else {
          const reach = Math.max(Math.sqrt((wx - pose.cx) ** 2 + (wy - pose.cy) ** 2), 1);
          const shrink = Math.log(Math.max((reach - bilateral) / reach, .5));
          pose.p += Math.max(shrink, -MAX_CONTACT_PRESSURE_STEP);
          if (vel.p > 0) vel.p = 0;
        }
      }
      const vn = vel.cx * nx + vel.cy * ny;
      if (vn > 0) { vel.cx -= vn * nx; vel.cy -= vn * ny; }
      iterations++;
    } while (worst > CONTACT_TOLERANCE && iterations < MAX_CONTACT_ITERATIONS);
    this.frame.update(this.reference, pose); this.frame.contour(CONTOUR_SAMPLES, c);
    worst = -Number.MAX_VALUE;
    for (let i = 0; i < CONTOUR_SAMPLES; i++) worst = Math.max(worst, this.barSignedDistance(c[i * 2], c[i * 2 + 1]));
    if (worst > CONTACT_TOLERANCE) {
      this.contactFailures++; this.solverFailed = true;
      if (![pose.cx, pose.cy, pose.p].every(Number.isFinite)) this.restoreLastGood();
    } else this.solverFailed = false;
    this.frame.update(this.reference, pose);
  }

  barSignedDistance(px, py) {
    const bar = this.bar, hw = bar.width / 2, hh = bar.allowedHalfHeight, rad = Math.min(bar.cornerRadius, hh);
    const qx = Math.abs(px - bar.width / 2) - hw + rad, qy = Math.abs(py - bar.height / 2) - hh + rad;
    const outside = Math.sqrt(Math.max(qx, 0) ** 2 + Math.max(qy, 0) ** 2);
    return Math.min(Math.max(qx, qy), 0) + outside - rad;
  }
  captureLastGood() { copyPose(this.lastGood, this.pose); copyPose(this.lastGoodVelocity, this.velocity); this.hasLastGood = true; }
  restoreLastGood() { if (!this.hasLastGood) return; copyPose(this.pose, this.lastGood); copyPose(this.velocity, this.lastGoodVelocity); }

  admittedSpine() {
    if (!this.itemCentre.length) { this.accommodationDemand = 0; return 0; }
    const g = this.graspX.value, s = this.spec, bar = this.bar, ref = this.reference;
    if (this.accommodationValid && Math.abs(g - this.accommodationGrasp) <= ACCOMMODATION_RESOLVE_PX &&
        Math.abs(this.graspY.value - this.accommodationGraspY) <= ACCOMMODATION_RESOLVE_PX &&
        Math.abs(this.pose.p - this.accommodationPressure) <= ACCOMMODATION_RESOLVE_PRESSURE) return this.accommodationDemand;
    this.accommodationGrasp = g; this.accommodationGraspY = this.graspY.value; this.accommodationPressure = this.pose.p;
    this.accommodationValid = true;
    const d = bar.density;
    let nearest = 0, nearestDistance = Number.MAX_VALUE;
    this.itemCentre.forEach((c, i) => { const dist = Math.abs(c - g); if (dist < nearestDistance) { nearestDistance = dist; nearest = i; } });
    this.admitted = nearest;
    const aperture = ref.a + ref.r * Math.exp(heldPressure(s.heldHeightRatio, bar.height, ref.height));
    const admitInside = aperture - s.admitInsetDp * d, releaseOutside = aperture + s.admitReleaseDp * d;
    let neighbour = -1, neighbourDistance = Number.MAX_VALUE;
    this.itemCentre.forEach((c, i) => {
      if (i === nearest || Math.abs(i - nearest) !== 1) return;
      const dist = Math.abs(c - g), keep = this.admittedNeighbour === i ? dist <= releaseOutside : dist <= admitInside;
      if (keep && dist < neighbourDistance) { neighbourDistance = dist; neighbour = i; }
    });
    this.admittedNeighbour = neighbour;
    const pad = s.admitPaddingDp * d;
    let left = this.itemLeft[nearest] - pad, right = this.itemRight[nearest] + pad;
    if (neighbour >= 0) { left = Math.min(left, this.itemLeft[neighbour] - pad); right = Math.max(right, this.itemRight[neighbour] + pad); }
    const ceiling = s.maxAccommodationSlots * bar.slotWidth;
    let demand = this.bestSpineFor(left, right, ceiling);
    if (demand <= 0 && neighbour >= 0) {
      this.admittedNeighbour = -1;
      demand = this.bestSpineFor(this.itemLeft[nearest] - pad, this.itemRight[nearest] + pad, ceiling);
    }
    this.accommodationDemand = demand; return demand;
  }
  bestSpineFor(left, right, ceiling) {
    if (ceiling <= 0) return 0;
    let bestSpine = 0, bestDeficit = this.coverageDeficit(0, left, right);
    const steps = 16;
    for (let i = 1; i <= steps; i++) {
      const spine = ceiling * i / steps, deficit = this.coverageDeficit(spine, left, right);
      if (deficit < bestDeficit - COVERAGE_EPSILON) { bestDeficit = deficit; bestSpine = spine; }
    }
    if (bestSpine <= 0) return 0;
    let lo = Math.max(bestSpine - ceiling / steps, 0), hi = bestSpine;
    for (let i = 0; i < 8; i++) { const mid = (lo + hi) / 2; if (this.coverageDeficit(mid, left, right) <= bestDeficit + COVERAGE_EPSILON) hi = mid; else lo = mid; }
    return hi;
  }
  coverageDeficit(spineExtra, left, right) {
    const s = this.spec, bar = this.bar, ref = this.reference, probe = copyPose(this.probePose, this.pose), f = this.probeFrame;
    probe.acc = spineExtra; probe.p = Math.max(this.pose.p, heldPressure(s.heldHeightRatio, bar.height, ref.height));
    probe.dx = ref.a; probe.dy = 0; f.update(ref, probe);
    if (this.hasGrasp) {
      const o = [0, 0]; f.materialToRendered(this.graspQx, this.graspQy, o);
      probe.cx = this.graspX.value - (o[0] - probe.cx);
      const cap = Math.max(s.pullFollowCapRatio * bar.height, 1e-4);
      const follow = boundedTravel(s.pullFollow * (this.graspY.value - this.graspY0), cap);
      probe.cy = this.graspY0 + follow - (o[1] - probe.cy); f.update(ref, probe);
    }
    let deficit = 0;
    for (const px of [left, right]) for (const sy of [-1, 1]) {
      const d = f.coverage(px, bar.height / 2 + sy * this.inkHalfHeight);
      if (d > 0) deficit += d;
    }
    return deficit;
  }

  recordQuiet() {
    const p = this.pose, l = this.lastPublished, span = Math.max(this.reference.a + this.reference.r, 1);
    let moved = Math.max(Math.abs(p.cx - l.cx), Math.abs(p.cy - l.cy), Math.abs(p.acc - l.acc), Math.abs(p.dx - l.dx), Math.abs(p.dy - l.dy));
    for (const k of ['p', 'u', 'v', 'kx', 'ky']) moved = Math.max(moved, Math.abs(p[k] - l[k]) * span);
    moved = Math.max(moved, Math.abs(this.form.value - this.formLast) * 100);
    copyPose(l, p); this.formLast = this.form.value;
    this.quietSteps = moved <= QUIET_PX ? this.quietSteps + 1 : 0;
  }

  clampShape() {
    const s = this.spec, p = this.pose, v = this.velocity, r = rho(p);
    if (r > s.maxStrain) { const sc = s.maxStrain / r; p.u *= sc; p.v *= sc; v.u *= sc; v.v *= sc; }
    const taper = Math.sqrt(p.kx * p.kx + p.ky * p.ky);
    if (taper > s.maxTaper) { const sc = s.maxTaper / taper; p.kx *= sc; p.ky *= sc; v.kx *= sc; v.ky *= sc; }
    if (p.acc < 0) { p.acc = 0; v.acc = 0; }
    const maxSpine = this.reference.a + Math.max(s.elongationSlots, s.tapSpineLimitSlots) * Math.max(this.bar.slotWidth, 1);
    const length = Math.sqrt(p.dx * p.dx + p.dy * p.dy);
    if (length > maxSpine && length > 1e-6) { const sc = maxSpine / length; p.dx *= sc; p.dy *= sc; v.dx *= sc; v.dy *= sc; }
  }

  /** How far the contour protrudes past the resting bar, in px. */
  protrusion() {
    this.frame.contour(CONTOUR_SAMPLES, this.contour);
    let worst = 0;
    for (let i = 0; i < CONTOUR_SAMPLES; i++) worst = Math.max(worst, this.barSignedDistance(this.contour[i * 2], this.contour[i * 2 + 1]));
    return worst;
  }
  /** Rendered axis-aligned extents from the sampled contour. */
  extents() {
    this.frame.contour(CONTOUR_SAMPLES, this.contour);
    let left = Infinity, right = -Infinity, top = Infinity, bottom = -Infinity;
    for (let i = 0; i < CONTOUR_SAMPLES; i++) {
      const x = this.contour[i * 2], y = this.contour[i * 2 + 1];
      left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y);
    }
    return {left, right, top, bottom, width: right - left, height: bottom - top};
  }
  /** The pose uniforms for a node whose centred frame sits at (originX, originY) in bar px. */
  render(originX = 0, originY = 0) {
    const f = this.frame;
    return {a: [...f.a], aInv: [...f.aInv], c: [f.cx - originX, f.cy - originY, f.bx, f.by],
      d: [f.spine, f.radius, f.scaleX, f.band()],
      // uPoseE: the held lens's superellipse blend (#93), grown with the formation as Compose does.
      e: [this.spec.heldSquircle * this.formation, this.spec.squircleExponent, 0, 0]};
  }
}

function sameBar(a, b) {
  return ['width', 'height', 'inset', 'count', 'cornerRadius', 'baseHalfWidth', 'baseHalfHeight', 'allowedHalfHeight', 'density']
    .every(k => a[k] === b[k]);
}

/**
 * GlassTabBar's gesture protocol over a CalmSelectorController: touch-down aims the selector,
 * a hold or horizontal slop starts a drag, and the end commits the tab the release projects to.
 * Coordinates are bar-local px, times monotonic seconds. Selection is reported, never owned:
 * `onSelect(index)` is called on a tap or committed drag; the host then calls `select`.
 */
/** Compose's FastOutSlowInEasing, cubic-bezier(0.4, 0, 0.2, 1). */
function fastOutSlowIn(u) {
  if (u <= 0 || u >= 1) return clamp(u, 0, 1);
  const bez = (t, p1, p2) => 3 * (1 - t) * (1 - t) * t * p1 + 3 * (1 - t) * t * t * p2 + t * t * t;
  let lo = 0, hi = 1, t = u;
  for (let i = 0; i < 30; i++) { t = (lo + hi) / 2; if (bez(t, .4, .2) < u) lo = t; else hi = t; }
  return bez(t, 0, 1);
}

export function createCalmNavigation({bar, selected = 0, onSelect = () => {}, spec = CALM_SELECTOR_SPEC,
  touchSlop = 8 * bar.density, flingVelocity = 420 * bar.density, motion = true} = {}) {
  const controller = new CalmSelectorController(spec);
  controller.motionEnabled = motion; controller.attach(bar); controller.snapToRest(selected);
  let current = selected, gesture = null, clock = 0;
  // GlassTabBarStyle.touchLift: the bar lifts for any touch. As Compose's animateFloatAsState with
  // tween(50) on the press and tween(260) on the lift: FastOutSlowIn, restarting from where it is.
  let touch = {from: 0, to: 0, t0: 0, duration: 1};
  const touchAt = time => {
    const u = clamp((time - touch.t0) / touch.duration, 0, 1);
    return touch.from + (touch.to - touch.from) * fastOutSlowIn(u);
  };
  const touchTo = (to, time) => {
    if (touch.to === to) return;
    touch = {from: touchAt(time), to, t0: time, duration: to > 0 ? .05 : .26};
  };
  const nearest = x => nearestTab(bar, x);
  const firstCentre = () => centreOf(bar, 0), lastCentre = () => centreOf(bar, bar.count - 1);
  function velocity(samples) {
    // Least-squares slope over the last 100 ms, as a stand-in for Compose's VelocityTracker.
    const t1 = samples[samples.length - 1].t, recent = samples.filter(s => t1 - s.t <= .1);
    if (recent.length < 2) return 0;
    const n = recent.length, mt = recent.reduce((a, s) => a + s.t, 0) / n, mx = recent.reduce((a, s) => a + s.x, 0) / n;
    let num = 0, den = 0;
    for (const s of recent) { num += (s.t - mt) * (s.x - mx); den += (s.t - mt) ** 2; }
    return den > 1e-12 ? num / den : 0;
  }
  return {
    controller,
    get selected() { return current; },
    /** The host's selection changed (including in answer to onSelect). */
    select(index) { current = clamp(index, 0, bar.count - 1); if (!gesture) controller.retarget(current); },
    setMotion(enabled) { controller.motionEnabled = enabled; },
    down(x, y, time) {
      const index = nearest(x);
      gesture = {x0: x, y0: y, t0: time, lastIndex: index, dragging: false, samples: [{t: time, x}]};
      if (controller.motionEnabled) touchTo(1, time);
      controller.pointerDown(x, y, time, controller.motionEnabled);
      controller.retarget(index);
    },
    move(x, y, time) {
      if (!gesture) return;
      const g = gesture;
      g.samples.push({t: time, x});
      controller.pointerMove(x, y, time);
      if (!g.dragging) {
        if (!controller.motionEnabled) return;
        const dx = x - g.x0, dy = y - g.y0, held = time - g.t0 >= controller.spec.holdThresholdSeconds;
        const crossed = held ? Math.hypot(dx, dy) > touchSlop : Math.abs(dx) > touchSlop;
        // As GlassTabBar: the drag begins from the down, then the crossing move is applied again,
        // or beginDrag would leave the pointer at the down until the next move.
        if (crossed) { g.dragging = true; controller.beginDrag(g.x0, g.y0); controller.pointerMove(x, y, time); }
        else if (!held && Math.abs(dy) > touchSlop) { this.cancel(time); return; }
        else return;
      }
      g.lastIndex = nearest(clamp(x, firstCentre(), lastCentre()));
    },
    up(x, y, time) {
      if (!gesture) return;
      const g = gesture; gesture = null;
      touchTo(0, time);
      if (!g.dragging) {
        // A tap: the tab under the finger. The host's onSelect owns the selection.
        controller.cancel(g.lastIndex); current = g.lastIndex; onSelect(g.lastIndex); return;
      }
      g.samples.push({t: time, x});
      // The lift is the gesture's last sample, as in GlassTabBar.
      controller.pointerMove(x, y, time);
      const v = velocity(g.samples);
      const projected = clamp(v > flingVelocity ? g.lastIndex + 1 : v < -flingVelocity ? g.lastIndex - 1 : g.lastIndex, 0, bar.count - 1);
      controller.pointerUp(projected); current = projected; onSelect(projected);
    },
    /** Another owner took the pointer: settle back to the host's selection, commit nothing. */
    cancel(time) { gesture = null; touchTo(0, time ?? clock); controller.cancel(current); },
    /** Advance to the display time. Returns true while another frame is needed. */
    frame(time) {
      clock = controller.resumedFrameTime(Math.max(clock, time));
      controller.advanceFrameTo(clock);
      return !controller.isIdle || !!gesture || clock < touch.t0 + touch.duration;
    },
    state(originX = 0, originY = 0) {
      const e = controller.extents();
      return {centreX: controller.centreX, centreY: controller.centreY, extents: e,
        formation: controller.formation, transitAppearance: controller.transitAppearance, touch: touchAt(clock),
        heldTravel: controller.heldTravel, held: controller.isHeld, protrusion: controller.protrusion(),
        // GlassTabBarStyle.itemsFollowBarPress = false: items share the bar's press only as the lens forms.
        itemFollow: (() => { const f = clamp((controller.formation - .5) / .5, 0, 1); return f * f * (3 - 2 * f); })(),
        pose: controller.render(originX, originY)};
    },
  };
}

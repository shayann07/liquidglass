/** Apache-2.0. Framework-independent, anchored Calm material feedback. No DOM or timer ownership. */
import {stepSpring, calmPullTarget, calmDeformation, calmPressScale, springAt, fastOutSlowIn} from './core.mjs';

const identity = () => [1, 0, 0, 1];
const clamp = (value, low, high) => Math.max(low, Math.min(high, value));

/**
 * Logical pixels, monotonic timestamps in SECONDS. The host owns pointer arbitration and redraws.
 * This is whole-surface feedback, not the navigation-selector travel controller.
 * The spring stiffnesses and steady geometry match the opt-in Compose Calm choices; timing is authored.
 */
export function createCalmInteraction({width, height, radius = Math.min(width, height) / 2,
  x = 0, y = 0, viewportWidth, viewportHeight, reducedMotion = false,
  pressScale = 1.03, pressGrowth = 2, pullShape = 'adaptive'}) {
  if (![pressScale, pressGrowth].every(Number.isFinite) || pressScale < 1 || pressScale > 2 || pressGrowth < 0)
    throw new RangeError('Press scale must be 1..2 and per-edge growth must be nonnegative');
  if (!['adaptive','area-preserving'].includes(pullShape))
    throw new RangeError('Pull shape must be adaptive or area-preserving');
  let geometry, reduced = !!reducedMotion, clock = null, held = false;
  let pressure = {value: 0, velocity: 0}, horizontal = {...pressure}, vertical = {...pressure};
  let target = {x: 0, y: 0};
  function configure(options) {
    const next = {...geometry, ...options};
    if (![next.width, next.height, next.radius, next.x, next.y].every(Number.isFinite) ||
        next.width <= 0 || next.height <= 0 || next.radius < 0 ||
        next.width > 1e7 || next.height > 1e7)
      throw new RangeError('Finite position, positive dimensions up to 10 million, and nonnegative radius required');
    if ((next.viewportWidth !== undefined || next.viewportHeight !== undefined) &&
        (![next.viewportWidth, next.viewportHeight].every(Number.isFinite) ||
        next.viewportWidth <= 0 || next.viewportHeight <= 0))
      throw new RangeError('Supply both positive viewport dimensions, or neither');
    next.radius = Math.min(next.radius, next.width / 2, next.height / 2);
    geometry = next;
  }
  configure({width, height, radius, x, y, viewportWidth, viewportHeight});

  function pressTargets() {
    const {width:w, height:h, x, y, viewportWidth:vw, viewportHeight:vh} = geometry;
    let sx=calmPressScale(w,pressScale,pressGrowth), sy=calmPressScale(h,pressScale,pressGrowth);
    // Constrain the target before its spring, matching Compose; clipping the animated result
    // instead would reach an edge early and abruptly stop an otherwise gradual press.
    if (vw !== undefined && x >= 0 && x+w <= vw)
      sx=Math.min(sx,Math.max(1,2*Math.min(x+w/2,vw-x-w/2)/w));
    if (vh !== undefined && y >= 0 && y+h <= vh)
      sy=Math.min(sy,Math.max(1,2*Math.min(y+h/2,vh-y-h/2)/h));
    return [sx,sy];
  }

  function advance(time, staleFrame = false) {
    if (!Number.isFinite(time) || time < 0)
      throw new RangeError('Use monotonic nonnegative timestamps in seconds');
    if (clock !== null && time < clock) {
      if (staleFrame) return;
      throw new RangeError('Use monotonic nonnegative timestamps in seconds');
    }
    const dt = clock === null ? 0 : time - clock;
    clock = time;
    pressure = stepSpring(pressure.value, pressure.velocity, held && !reduced ? 1 : 0, dt, held ? 220 : 260);
    horizontal = stepSpring(horizontal.value, horizontal.velocity, target.x, dt, held ? 350 : 260);
    vertical = stepSpring(vertical.value, vertical.velocity, target.y, dt, held ? 350 : 260);
    // A settled controller can stop scheduling. Snap only below a sub-pixel threshold.
    if (!held && Math.abs(pressure.value) < 1e-5 && Math.abs(pressure.velocity) < 1e-4 &&
        Math.hypot(horizontal.value, vertical.value) < 1e-4 &&
        Math.hypot(horizontal.velocity, vertical.velocity) < 1e-3) clear();
  }
  function clear() {
    pressure = {value: 0, velocity: 0}; horizontal = {...pressure}; vertical = {...pressure};
    target = {x: 0, y: 0};
  }
  function snapshot() {
    const {width: w, height: h, radius: r, x, y, viewportWidth: vw, viewportHeight: vh} = geometry;
    if (reduced) return {matrix: identity(), offset: [0,0], press: 0, held, active: false};
    const amount = clamp(pressure.value, 0, 1);
    const [targetX,targetY] = pressTargets();
    const pressX = 1 + (targetX - 1) * amount;
    const pressY = 1 + (targetY - 1) * amount;
    const contained = vw !== undefined && x >= 0 && y >= 0 && x + w <= vw && y + h <= vh;
    const cx = x + w / 2, cy = y + h / 2;
    const shape = calmDeformation(w, h, horizontal.value, vertical.value, amount, r, pressX, pressY,
      pullShape,Math.min(w*targetX,h*targetY)*.5);
    const c = Math.cos(shape.angle), s = Math.sin(shape.angle);
    function matrix(gain) {
      const a = 1 + (shape.along - 1) * gain, b = 1 + (shape.across - 1) * gain;
      const shear = (a - b) * c * s;
      return [(a*c*c+b*s*s)*pressX, shear*pressY, shear*pressX, (a*s*s+b*c*c)*pressY];
    }
    function extent(gain) {
      const [a,b,c,d] = matrix(gain);
      // Support of a transformed rounded rectangle, including diagonals; never clip the glass.
      const hx = (w/2-r)*Math.abs(a) + (h/2-r)*Math.abs(b) + r*Math.hypot(a,b);
      const hy = (w/2-r)*Math.abs(c) + (h/2-r)*Math.abs(d) + r*Math.hypot(c,d);
      return [hx,hy];
    }
    function fits(gain) {
      const [hx,hy]=extent(gain);
      return hx <= Math.min(cx,vw-cx) && hy <= Math.min(cy,vh-cy);
    }
    let gain = 1;
    if (contained && !fits(1)) {
      let low = 0, high = 1;
      for (let i=0;i<24;i++) {const mid=(low+high)/2;if(fits(mid))low=mid;else high=mid;}
      gain = low;
    }
    let offset=[shape.offsetX??0,shape.offsetY??0];
    if(contained) {
      const [hx,hy]=extent(gain);
      // Same C1 edge resistance as Compose. The material may bias; layout never does.
      const travel=(value,before,after)=>{
        const room=Math.max(0,value<0?before:after),free=room/2;
        if(Math.abs(value)<=free)return value;
        const span=room-free, result=free+(span>0?span*Math.tanh((Math.abs(value)-free)/span):0);
        return value<0?-result:result;
      };
      offset=[travel(offset[0],cx-hx,vw-cx-hx),travel(offset[1],cy-hy,vh-cy-hy)];
    }
    const active = Math.abs(pressure.value-(held?1:0)) > 1e-5 || Math.abs(pressure.velocity) > 1e-4 ||
      Math.hypot(horizontal.value-target.x, vertical.value-target.y) > 1e-4 ||
      Math.hypot(horizontal.velocity, vertical.velocity) > 1e-3;
    return {matrix: matrix(gain), offset, press: amount, held, active};
  }
  // A long bar resists only the part of the pull it answers (Compose glassPullCompliance): the sideways part scaled
  // by its roundness, so a drag along the bar does not shorten its vertical pull. Every other shape resists it all.
  function pullTarget(dx, dy) {
    const {width: w, height: h} = geometry;
    if (pullShape !== 'area-preserving') return calmPullTarget(w, h, dx, dy, ...pressTargets());
    const ax = dx * Math.max(0, Math.min(1, 2 * Math.min(w, h) / Math.max(w, h) - 1));
    const resisted = calmPullTarget(w, h, ax, dy, ...pressTargets()), length = Math.hypot(ax, dy);
    const compliance = length ? Math.hypot(resisted.x, resisted.y) / length : 1;
    return {x: dx * compliance, y: dy * compliance};
  }
  return {
    /** Total displacement since pointer-down, never a per-event delta. Advance the OLD target first. */
    press(time, dx = 0, dy = 0) {
      if (![dx,dy].every(Number.isFinite)) throw new RangeError('Finite cumulative displacement required');
      advance(time); held = true;
      target = reduced ? {x: 0, y: 0} : pullTarget(dx, dy);
      return snapshot();
    },
    /** Use for both pointer-up and cancellation; navigation selection belongs to the host. */
    release(time) {advance(time); held = false; target = {x: 0,y: 0}; return snapshot();},
    // A queued animation frame can predate the latest input event; it must never rewind state.
    sample(time) {advance(time, true); return snapshot();},
    /** Reconfigure after a layout change. Cancel the current gesture rather than keeping stale coordinates. */
    resize(options, time) {advance(time); configure(options); held = false; clear(); return snapshot();},
    setReducedMotion(value, time) {advance(time); reduced = !!value; held = false; clear(); return snapshot();},
  };
}

// The free button: GlassInteraction.Pullable with GlassPullShape.Free, the Phone scene's search button
// (measured on iOS 26's, IMG_6756). Each value restarts from where it is when its target changes, as
// Compose's animateFloatAsState does; the laws are Modifier.liquidGlass's own (glassPressScale,
// glassPullTargetScale, glassPullDeformation without anchoring, glassFreeLanding). Checked frame by
// frame against the Kotlin-exported ports/skia/fixtures/free-button.json.
const FREE_SPECS = {
  glowIn: {tween: .09}, freeGlow: {tween: .35},
  balloonDown: {damping: .62, stiffness: 644}, pressUp: {damping: .72, stiffness: 700},
  pullFollow: {damping: 1, stiffness: 2500}, freeRelease: {damping: .45, stiffness: 264},
  calmDown: {damping: 1, stiffness: 220}, calmUp: {damping: 1, stiffness: 260},
};

// glassSizeMix: 0 for a control of 80 dp or less on its long side to 1 at 160 dp (smoothstep). Authored.
function sizeMix(width, height, density) {
  const t = Math.min(1, Math.max(0, (Math.max(width, height) / density - 80) / 80));
  return t * t * (3 - 2 * t);
}

function animated(initial) {
  const a = {start: 0, from: initial, velocity: 0, target: initial, spec: null};
  const at = time => {
    if (!a.spec) return {value: a.from, velocity: 0};
    // Compose evaluates its animations on whole elapsed milliseconds (nanoseconds / 1e6, truncated).
    // As (elapsed seconds * 1e9).toLong() / 1e6: truncated twice, as the Kotlin host and Compose do.
    const tm = Math.floor(Math.trunc(Math.max(0, time - a.start) * 1e9) / 1e6) / 1000;
    if (a.spec.tween !== undefined) {
      // A tween reads its elapsed time to the nanosecond; a spring to the whole millisecond.
      const u = Math.min(1, Math.trunc(Math.max(0, time - a.start) * 1e9) / 1e9 / a.spec.tween);
      return {value: a.from + (a.target - a.from) * fastOutSlowIn(u), velocity: 0};
    }
    return springAt(a.from, a.velocity, a.target, tm, a.spec.damping, a.spec.stiffness);
  };
  return {
    value: time => at(time).value,
    to(time, target, spec) {
      if (target === a.target && a.spec) return;
      const now = at(time);
      Object.assign(a, {start: time, from: now.value, velocity: now.velocity, target, spec});
    },
  };
}

/**
 * A free-standing round glass button pulled off its place (px; time in seconds). down / move (total
 * finger travel since down) / up drive it; sample returns the drawing transform: press scale, a
 * stretch along and across `angle` (degrees), the offset, and the glow and its lift.
 * `width` / `height` (default `size`) give a control that is not square and `radius` (default half its short side) its
 * corner radius: Compose's GlassInteraction.Fluid default, which falls with size (authored). A control of 80 dp or less
 * on its long side is the search button; from 160 dp it presses 2 dp a side and stretches at most 2 dp.
 */
export function createFreeButton({size, width = size, height = size, radius, density = 1, pressScale = 1.26,
  pressGrowth = 8, pressLift = .2, pullFollow = .2, pullElongation = .124, pullWidthRatio = 1, pullLimit = .5,
  pullLiftShare = .65, pullReleaseDampingX = .3} = {}) {
  if (!(size > 0) || !(width > 0) || !(height > 0) || !(density > 0)) throw new RangeError('Positive size and density required');
  const r = radius ?? Math.min(width, height) / 2;
  const mix = sizeMix(width, height, density);
  const base = pressGrowth * density;
  const growth = mix > 0 ? base + (2 * density - base) * mix : base;
  const balloonX = Math.min(1 + 2 * growth / Math.max(width, 1), pressScale);
  const balloonY = Math.min(1 + 2 * growth / Math.max(height, 1), pressScale);
  const limit = Math.min(width * balloonX, height * balloonY) * pullLimit;
  const press = animated(0), glow = animated(0), px = animated(0), py = animated(0);
  let anchor = NaN, moved = false;
  const extent = (sx, sy, x, y) => {
    const len = Math.hypot(x, y);
    if (len < 1e-5) return Math.min(width * sx, height * sy);
    const nx = Math.abs(x) / len, ny = Math.abs(y) / len;
    return (width - 2 * r) * sx * nx + (height - 2 * r) * sy * ny + 2 * r * Math.hypot(sx * nx, sy * ny);
  };
  // glassFreeSizeBlend: the free deformation toward the anchored surface response of the raw pull, by sizeMix.
  const upright = d => {
    const a = d.angle * Math.PI / 180, nx2 = Math.cos(a) ** 2, ny2 = Math.sin(a) ** 2;
    return {...d, along: d.along * ny2 + d.across * nx2, across: d.along * nx2 + d.across * ny2, angle: -90};
  };
  function surface(raw, e) {
    // glassSurfacePull, then glassAnchoredMaterialPull, then upright.
    let {along, across} = raw;
    const extension = (along - 1) * e;
    if (extension > 0) {
      const limitPx = 2 * density, x = .2 * extension / limitPx;
      const surfaceGain = x < 1e-3 ? .2 * (1 - x * x / 3) : limitPx * Math.tanh(x) / extension;
      const gain = 1 + (surfaceGain - 1) * mix;
      along = 1 + (along - 1) * gain; across = 1 + (across - 1) * gain;
    }
    const a = raw.angle * Math.PI / 180, c = Math.cos(a), s = Math.sin(a);
    const xx = along * c * c + across * s * s, yy = along * s * s + across * c * c;
    const roundness = Math.min(1, Math.max(0, 2 * Math.min(width, height) / Math.max(width, height) - 1));
    const xy = (along - across) * c * s * roundness, mean = (xx + yy) / 2;
    const rad = Math.sqrt((xx - yy) * (xx - yy) / 4 + xy * xy);
    return upright({along: mean + rad, across: mean - rad, angle: Math.atan2(2 * xy, xx - yy) * (90 / Math.PI)});
  }
  // glassFreeLanding: the edge away from the pull holds at rest; the overshoot lands as a squash.
  function landing(d) {
    if (!Number.isFinite(anchor)) return d;
    const a = anchor * Math.PI / 180, nx = Math.cos(a), ny = Math.sin(a);
    const restHalf = Math.hypot(width / 2 * nx, height / 2 * ny);
    const c = d.tx * nx + d.ty * ny, perp = -d.tx * ny + d.ty * nx;
    const dd = (d.angle - anchor) * Math.PI / 180, cd = Math.cos(dd), sd = Math.sin(dd);
    const stretched = c >= 0;
    const axis = stretched ? Math.sqrt(d.along ** 2 * cd * cd + d.across ** 2 * sd * sd) : 1;
    const side = stretched ? Math.sqrt(d.along ** 2 * sd * sd + d.across ** 2 * cd * cd) : 1;
    const half = restHalf * axis;
    if (c - half >= -restHalf) return d;
    const newHalf = Math.max((c + half + restHalf) / 2, restHalf * .5), centre = c + half - newHalf;
    const k = newHalf / half;
    return {along: axis * k, across: side * (1 + .2 * (1 - k)), angle: anchor,
      tx: centre * nx - perp * ny, ty: centre * ny + perp * nx};
  }
  return {
    down(time) {
      moved = false;
      glow.to(time, 1, FREE_SPECS.glowIn); press.to(time, 1, mix >= 1 ? FREE_SPECS.calmDown : FREE_SPECS.balloonDown);
      px.to(time, 0, FREE_SPECS.pullFollow); py.to(time, 0, FREE_SPECS.pullFollow);
    },
    move(time, dx, dy) {
      const len = Math.hypot(dx, dy);
      if (!moved && len > 4 * density) { moved = true; glow.to(time, pullLiftShare, FREE_SPECS.freeGlow); }
      if (len > 4 * density) {
        const raw = Math.atan2(dy, dx) * 180 / Math.PI;
        anchor = raw;
      }
      const travel = len * pullFollow;
      const k = !(len > 1e-5) || pullFollow <= 0 ? 1 : limit * Math.tanh(travel / limit) / travel;
      px.to(time, dx * k, FREE_SPECS.pullFollow); py.to(time, dy * k, FREE_SPECS.pullFollow);
    },
    up(time) {
      glow.to(time, 0, FREE_SPECS.freeGlow); press.to(time, 0, FREE_SPECS.pressUp);
      // Compose's GlassInteraction.pullReleaseDampingX: a separate x damping on release.
      const releaseX = Number.isFinite(pullReleaseDampingX)
        ? {...FREE_SPECS.freeRelease, damping: Math.min(Math.max(pullReleaseDampingX, .05), 2)} : FREE_SPECS.freeRelease;
      px.to(time, 0, releaseX); py.to(time, 0, FREE_SPECS.freeRelease);
    },
    /** A scroll took the gesture: everything returns critically damped, with no ring and no landing squash. */
    cancel(time) {
      anchor = NaN;
      glow.to(time, 0, FREE_SPECS.freeGlow); press.to(time, 0, FREE_SPECS.calmUp);
      px.to(time, 0, FREE_SPECS.calmUp); py.to(time, 0, FREE_SPECS.calmUp);
    },
    sample(time) {
      const amount = press.value(time), scale = 1 + (balloonX - 1) * amount, scaleY = 1 + (balloonY - 1) * amount;
      const x = px.value(time), y = py.value(time), len = Math.hypot(x, y);
      let d = {along: 1, across: 1, angle: 0, tx: 0, ty: 0}, raw = d, rawExtent = 1;
      if (len >= 1e-3) {
        const e = extent(scale, scaleY, x, y), across = extent(scale, scaleY, -y, x);
        const along = Math.min(1 + pullElongation * len / Math.max(e, 1), 1.6);
        const extension = (along - 1) * Math.max(e, 1);
        const narrow = Math.max(1 - pullWidthRatio * extension / Math.max(across, 1), .5);
        const nx2 = x * x / (len * len), ny2 = y * y / (len * len);
        // Continuous screen-axis projection, matching glassPullDeformation.
        d = {along: along * ny2 + narrow * nx2, across: along * nx2 + narrow * ny2,
          angle: -90, tx: pullFollow * x, ty: pullFollow * y};
        // The surface response works on the pull's own axes, not on the upright projection of them.
        raw = {along, across: narrow, angle: Math.atan2(y, x) * 180 / Math.PI}; rawExtent = e;

      }
      d = landing(d);
      const a = d.angle * Math.PI / 180, nx2 = Math.cos(a) ** 2, ny2 = Math.sin(a) ** 2;
      d = {...d, along: d.along * ny2 + d.across * nx2,
        across: d.along * nx2 + d.across * ny2, angle: -90};
      if (mix > 0 && len >= 1e-3) {
        const s = surface(raw, rawExtent);
        d = {...d, along: d.along + (s.along - d.along) * mix, across: d.across + (s.across - d.across) * mix,
          tx: d.tx * (1 - mix), ty: d.ty * (1 - mix)};
      }
      const g = glow.value(time);
      return {...d, pressScale: scale, pressScaleY: scaleY, glow: g, lift: g * pressLift, pullX: x};

    },
  };
}

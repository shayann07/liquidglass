/** Apache-2.0. Framework-independent, anchored Calm material feedback. No DOM or timer ownership. */
import {stepSpring, calmPullTarget, calmDeformation} from './core.mjs';

const identity = () => [1, 0, 0, 1];
const clamp = (value, low, high) => Math.max(low, Math.min(high, value));

/**
 * Logical pixels, monotonic timestamps in SECONDS. The host owns pointer arbitration and redraws.
 * This is whole-surface feedback, not the navigation-selector travel controller.
 * The spring stiffnesses and steady geometry match the opt-in Compose Calm choices; timing is authored.
 */
export function createCalmInteraction({width, height, radius = Math.min(width, height) / 2,
  x = 0, y = 0, viewportWidth, viewportHeight, reducedMotion = false}) {
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
    if (reduced) return {matrix: identity(), press: 0, held, active: false};
    const amount = clamp(pressure.value, 0, 1);
    let pressX = 1 + (Math.min(1.03, 1 + 4 / w) - 1) * amount;
    let pressY = 1 + (Math.min(1.03, 1 + 4 / h) - 1) * amount;
    const contained = vw !== undefined && x >= 0 && y >= 0 && x + w <= vw && y + h <= vh;
    const cx = x + w / 2, cy = y + h / 2;
    if (contained) {
      pressX = Math.min(pressX, 2 * Math.min(cx, vw - cx) / w);
      pressY = Math.min(pressY, 2 * Math.min(cy, vh - cy) / h);
    }
    const shape = calmDeformation(w, h, horizontal.value, vertical.value, amount, r, pressX, pressY);
    const c = Math.cos(shape.angle), s = Math.sin(shape.angle);
    function matrix(gain) {
      const a = 1 + (shape.along - 1) * gain, b = 1 + (shape.across - 1) * gain;
      const shear = (a - b) * c * s;
      return [(a*c*c+b*s*s)*pressX, shear*pressY, shear*pressX, (a*s*s+b*c*c)*pressY];
    }
    function fits(gain) {
      const [a,b,c,d] = matrix(gain);
      // Support of a transformed rounded rectangle, including diagonals; never clip the glass.
      const hx = (w/2-r)*Math.abs(a) + (h/2-r)*Math.abs(b) + r*Math.hypot(a,b);
      const hy = (w/2-r)*Math.abs(c) + (h/2-r)*Math.abs(d) + r*Math.hypot(c,d);
      return hx <= Math.min(cx,vw-cx) && hy <= Math.min(cy,vh-cy);
    }
    let gain = 1;
    if (contained && !fits(1)) {
      let low = 0, high = 1;
      for (let i=0;i<24;i++) {const mid=(low+high)/2;if(fits(mid))low=mid;else high=mid;}
      gain = low;
    }
    const active = Math.abs(pressure.value-(held?1:0)) > 1e-5 || Math.abs(pressure.velocity) > 1e-4 ||
      Math.hypot(horizontal.value-target.x, vertical.value-target.y) > 1e-4 ||
      Math.hypot(horizontal.velocity, vertical.velocity) > 1e-3;
    return {matrix: matrix(gain), press: amount, held, active};
  }
  return {
    /** Total displacement since pointer-down, never a per-event delta. Advance the OLD target first. */
    press(time, dx = 0, dy = 0) {
      if (![dx,dy].every(Number.isFinite)) throw new RangeError('Finite cumulative displacement required');
      advance(time); held = true;
      target = reduced ? {x: 0, y: 0} : calmPullTarget(geometry.width, geometry.height, dx, dy);
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

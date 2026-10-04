/** Apache-2.0. Portable authored lens/feedback math; no DOM or graphics dependency. */
export function lensSource(x, y, halfWidth, halfHeight, magnification = 1.25) {
  for (const n of [x, y, halfWidth, halfHeight, magnification])
    if (!Number.isFinite(n)) throw new RangeError('Lens inputs must be finite');
  if (halfWidth <= 0 || halfHeight <= 0 || magnification < 1 || magnification > 2.5)
    throw new RangeError('Positive size and magnification 1…2.5 required');
  const radius2 = Math.min(1, (x / halfWidth) ** 2 + (y / halfHeight) ** 2);
  const factor = 1 - (1 - 1 / magnification) * (1 - radius2) ** 2;
  return { x: x * factor, y: y * factor };
}

/** Exact critically damped step. Time in seconds, values in the caller's units. */
export function stepSpring(value, velocity, target, seconds, stiffness = 220) {
  if (![value, velocity, target, seconds, stiffness].every(Number.isFinite) || seconds < 0 || stiffness <= 0)
    throw new RangeError('Finite spring values, nonnegative time and positive stiffness required');
  const omega = Math.sqrt(stiffness), error = value - target;
  if (seconds === 0) return {value, velocity};
  if (omega * seconds > 750) return {value: target, velocity: 0};
  const b = velocity + omega * error, decay = Math.exp(-omega * seconds);
  return { value: target + (error + b * seconds) * decay,
    velocity: (velocity - omega * b * seconds) * decay };
}

/** Material deformation only. Keep text/hit targets outside the transformed drawing. */
export function calmFeedback(width, height, dx, dy, pressed = true, radius = Math.min(width, height) / 2) {
  if (![width, height, dx, dy, radius].every(Number.isFinite) || width <= 0 || height <= 0 || radius < 0)
    throw new RangeError('Finite displacement and positive dimensions required');
  if (!pressed) return { pressX: 1, pressY: 1, along: 1, across: 1, angle: 0 };
  const target = calmPullTarget(width, height, dx, dy);
  return calmDeformation(width, height, target.x, target.y, 1, radius);
}

/** Internal shared geometry for the timestamped controller. Inputs are validated by callers. */
export function calmPullTarget(width, height, dx, dy,
  pressX = calmPressScale(width), pressY = calmPressScale(height)) {
  const limit = Math.min(width * pressX, height * pressY) * .5;
  // Normalize before taking a length: even an enormous finite over-pull must not create NaN.
  const scale = Math.max(Math.abs(dx), Math.abs(dy));
  if (!scale) return {x: 0, y: 0};
  const length = Math.hypot(dx / scale, dy / scale);
  const resisted = limit * Math.tanh(scale * .2 / limit * length) / .2;
  return {x: dx / scale / length * resisted, y: dy / scale / length * resisted};
}

/** Per-edge growth is in logical pixels. Zero selects percentage-only growth, as in Compose. */
export function calmPressScale(side, scale = 1.03, growth = 2) {
  return growth > 0 ? Math.min(scale, 1 + 2 * growth / side) : scale;
}

/** Pull coordinates have already been resisted and animated; do not resist them a second time. */
export function calmDeformation(width, height, dx, dy, pressAmount, radius,
  pressX = 1 + (calmPressScale(width) - 1) * pressAmount,
  pressY = 1 + (calmPressScale(height) - 1) * pressAmount,
  pullShape = 'adaptive', targetLimit = Math.min(width*pressX,height*pressY)*.5) {
  const travel = Math.hypot(dx, dy);
  if (!travel) return { pressX, pressY, along: 1, across: 1, angle: 0 };
  const c = dx / travel, s = dy / travel, r = Math.min(radius, width / 2, height / 2);
  if (pullShape === 'area-preserving') {
    const amount = Math.max(0,Math.min(1,travel*.2/targetLimit));
    // Same authored .047 log-strain as Compose; input compliance/timing remain independent.
    const strain=.047*amount;
    const roundness=Math.max(0,Math.min(1,2*Math.min(width,height)/Math.max(width,height)-1));
    const u=strain*(c*c-s*s), v=2*strain*c*s*roundness, magnitude=Math.hypot(u,v);
    return {pressX,pressY,along:Math.exp(magnitude),across:Math.exp(-magnitude),angle:Math.atan2(v,u)/2};
  }
  const extent = (nx, ny) => (width - 2*r)*pressX*Math.abs(nx) + (height - 2*r)*pressY*Math.abs(ny) +
    2*r*Math.hypot(pressX*nx, pressY*ny);
  const alongExtent=extent(c,s), acrossExtent=extent(-s,c), extension=.03*travel;
  const t=Math.max(0,Math.min(1,(Math.max(width,height)-80)/80)), mix=t*t*(3-2*t);
  const surfaceGain=extension<.001 ? .2 : 2*Math.tanh(.2*extension/2)/extension;
  const gain=1+(surfaceGain-1)*mix;
  const along=1+extension/alongExtent*gain, across=1-extension/acrossExtent*gain;
  const roundness=Math.max(0,Math.min(1,2*Math.min(width,height)/Math.max(width,height)-1));
  const xx=along*c*c+across*s*s, yy=along*s*s+across*c*c, xy=(along-across)*c*s*roundness;
  const mean=(xx+yy)/2, eigenRadius=Math.hypot((xx-yy)/2,xy);
  return { pressX,pressY,along:mean+eigenRadius,across:mean-eigenRadius,angle:Math.atan2(2*xy,xx-yy)/2 };
}

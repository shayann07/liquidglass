/** Apache-2.0. A Calm navigation bar as the scene runs it: the driver that owns its motion, and the stateless draw. */
import {createCalmInteraction} from './interaction.mjs';
import {calmSelectorBar, createCalmNavigation} from './selector.mjs';
import {geometry} from './bounds.mjs';

/**
 * The motion of one navigation bar: the selector's gesture controller and the bar's own press feedback, driven
 * together. `bounds` are validated logical bounds, `view` the viewport; positions are logical page px and times
 * monotonic seconds. Each call returns the bar's feedback (or null where it is unchanged). The scene's navigation
 * layer and `bakeNavigation` both run this one driver, so a baked track is the scene's own motion.
 */
export function createNavigationDriver({bounds, view, count, selected = 0, inset = 6, pillWidth, pillInset = 4,
  onSelect = () => {}, reducedMotion = false, clock = 0}) {
  const bar = calmSelectorBar({width: bounds.width, height: bounds.height, count, inset, pillWidth, pillInset, density: 1});
  const nav = createCalmNavigation({bar, selected, onSelect, motion: !reducedMotion});
  const motion = createCalmInteraction({...geometry(bounds, view), reducedMotion, pressScale: 1.05, pressGrowth: 0});
  let down = null;
  const local = (x, y) => [x - bounds.x, y - bounds.y];
  return {
    nav, motion, initial: motion.sample(clock),
    down(x, y, time) { const [lx, ly] = local(x, y); down = [x, y]; nav.down(lx, ly, time); return motion.press(time, 0, 0); },
    move(x, y, time) {
      const [lx, ly] = local(x, y); nav.move(lx, ly, time);
      return down ? motion.press(time, x - down[0], y - down[1]) : null;
    },
    up(x, y, time) { const [lx, ly] = local(x, y); nav.up(lx, ly, time); down = null; return motion.release(time); },
    cancel(time) { nav.cancel(time); down = null; return motion.release(time); },
    /** Sample the press feedback and advance the selector to `time`: the bar's state for that frame and whether
     * another frame is needed. */
    advance(time) {
      const feedback = motion.sample(time), moving = nav.frame(time);
      return {feedback, active: feedback.active || moving};
    },
  };
}

// The bar's glass and selector material for a state: `dark` and the accessibility preferences carry over.
function navMaterial(material, selector, ratio) {
  const m = material;
  return {dark: m.dark ?? true, density: (m.density ?? 1) * ratio, formation: selector.formation, touch: selector.touch ?? 0,
    materialize: m.materialize ?? 1, reducedTransparency: m.reducedTransparency ?? 0, increasedContrast: m.increasedContrast ?? 0,
    clear: m.clear ?? false};
}

// The page with the bar's glass over it: the selector's lens looks through the bar, as in Compose.
function barThrough(kit, painter, canvas, source, feedback, deviceBounds, material) {
  const surface = canvas.makeSurface({width: source.width(), height: source.height(), colorType: kit.ColorType.RGBA_8888,
    alphaType: kit.AlphaType.Premul, colorSpace: kit.ColorSpace.SRGB});
  if (!surface) return null;
  try {
    const target = surface.getCanvas(); target.drawImage(source, 0, 0);
    const [a, b, c, d] = feedback.matrix;
    const cx = deviceBounds.x + deviceBounds.width / 2, cy = deviceBounds.y + deviceBounds.height / 2;
    target.save();
    target.translate(cx + feedback.offset[0] * material.ratio, cy + feedback.offset[1] * material.ratio);
    target.concat([a, b, 0, c, d, 0, 0, 0, 1]); target.translate(-cx, -cy);
    painter.drawCalmBar(target, deviceBounds, material.bar);
    target.restore(); surface.flush();
    return surface.makeImageSnapshot();
  } finally { surface.delete(); }
}

/**
 * Draw one navigation bar, its selector and optional selected ink from a state, with no clock and no layer: the
 * scene's own drawing, for any state a host holds (a `NavigationTrack` sample, or a live layer's). `view` is
 * `{bounds, pixelRatio, source}`: the bar's logical bounds, the canvas's pixel ratio and the page image (device px).
 * `sample` is `{selector, feedback}`: a CalmSelectorState and a GlassFeedback. Labels and the ink stay with the host.
 */
export function drawNavigation(kit, painter, canvas, {bounds, pixelRatio = 1, source}, sample, {material = {dark: true}, ink = null} = {}) {
  const ratio = pixelRatio, {selector: s, feedback} = sample;
  const deviceBounds = {x: bounds.x * ratio, y: bounds.y * ratio, width: bounds.width * ratio, height: bounds.height * ratio};
  const [a, b, c, d] = feedback.matrix;
  const bar = navMaterial(material, s, ratio);
  const cx = deviceBounds.x + deviceBounds.width / 2, cy = deviceBounds.y + deviceBounds.height / 2;
  canvas.save();
  try {
    canvas.translate(cx + feedback.offset[0] * ratio, cy + feedback.offset[1] * ratio);
    canvas.concat([a, b, 0, c, d, 0, 0, 0, 1]);
    canvas.translate(-cx, -cy);
    painter.drawCalmBar(canvas, deviceBounds, bar);
  } finally { canvas.restore(); }
  // The selector in device px: positions and lengths scale, the dimensionless map does not.
  const p = s.pose, r = ratio;
  // `e` (the held lens's superellipse blend) is dimensionless: it is passed on unchanged.
  const pose = {a: p.a, aInv: p.aInv, e: p.e, c: [p.c[0] * r, p.c[1] * r, p.c[2] / r, p.c[3] / r],
    d: [p.d[0] * r, p.d[1] * r, p.d[2], p.d[3] * r]};
  // As in Compose, the lens looks through the bar: the page with the bar's glass over it.
  const through = barThrough(kit, painter, canvas, source, feedback, deviceBounds, {ratio, bar});
  const state = {pose, formation: s.formation, transit: s.transitAppearance, heldTravel: s.heldTravel, formedTap: s.tapFormed};
  try {
    painter.drawSelector(canvas, deviceBounds, state, {...bar, through});
    if (ink) painter.drawSelectorInk(canvas, deviceBounds, state, ink, bar);
  } finally { through?.delete(); }
}

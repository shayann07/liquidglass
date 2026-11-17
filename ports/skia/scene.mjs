/** Apache-2.0. One backdrop, many calm glass surfaces, independent of UI framework. */
import {createGlassPainter} from './painter.mjs';
import {createCalmInteraction} from './interaction.mjs';
import {viewport, boundsFor, geometry} from './bounds.mjs';
import {createNavigationDriver, drawNavigation} from './navigation.mjs';

const copyBounds = bounds => ({...bounds, ...(Array.isArray(bounds.radius) ? {radius: [...bounds.radius]} : {})});

/**
 * Bounds and pointer displacement are logical pixels; timestamps are monotonic seconds.
 * The host supplies a pixelRatio-scaled Skia canvas and opaque image in the same coordinates.
 * Owns the painter and motion state, never the borrowed image, canvas, events or labels.
 */
export function createGlassScene(kit, options) {
  let size = viewport(options), reduced = !!options.reducedMotion, disposed = false, image = null, clock = 0;
  const painter = createGlassPainter(kit, options.sources);
  const layers = new Set();
  function alive() { if (disposed) throw new Error('Glass scene disposed'); }
  function timeAt(time, stale = false) {
    alive();
    if (!Number.isFinite(time) || time < 0 || (!stale && time < clock))
      throw new RangeError('Use monotonic nonnegative timestamps in seconds');
    return Math.max(clock, time);
  }
  function add(kind, input, material = {}) {
    alive();
    const bounds = boundsFor(kind, input);
    const motion = createCalmInteraction({...geometry(bounds, size), reducedMotion: reduced});
    const layer = {kind, bounds, material: {...material}, motion, state: motion.sample(clock)};
    layers.add(layer);
    function attached() { alive(); if (!layers.has(layer)) throw new Error('Glass layer removed'); }
    function update(time, action) {
      attached(); const current = timeAt(time);
      const state = action(current); clock = current; layer.state = state;
      return {...state, matrix: [...state.matrix], offset: [...state.offset]};
    }
    return {
      get bounds() { attached(); return copyBounds(layer.bounds); },
      get feedback() { attached(); const s = layer.state; return {...s, matrix: [...s.matrix], offset: [...s.offset]}; },
      press(time, dx = 0, dy = 0) { return update(time, t => motion.press(t, dx, dy)); },
      release(time) { return update(time, t => motion.release(t)); },
      /** Layout changes cancel the old gesture. Positions are owned by the host. */
      setBounds(next, time) {
        attached(); const bounds = boundsFor(kind, {...layer.bounds, ...next});
        update(time, t => motion.resize(geometry(bounds, size), t)); layer.bounds = bounds;
      },
      setMaterial(next) {
        attached();
        layer.material = {...layer.material, ...next};
      },
      remove() { layers.delete(layer); },
    };
  }
  // A lens's options (all optional): `lighting` opts in to the Compose clear-lens preset, scaled by the pixel ratio.
  const lensOptions = (m, ratio) => ({lighting: m.lighting ?? false, shadow: m.shadow ?? true,
    density: (m.density ?? 1) * ratio, ...(m.light ? {light: m.light} : {})});
  /** A Calm navigation bar: bar glass, the ported selector and its gestures. Logical px. */
  function addNavigation(input, {count, selected = 0, inset = 6, pillWidth, pillInset = 4,
    onSelect = () => {}, material = {dark: true}} = {}) {
    alive();
    const bounds = boundsFor('surface', {...input, radius: input.height / 2});
    const driver = createNavigationDriver({bounds, view: size, count, selected, inset, pillWidth, pillInset, onSelect,
      reducedMotion: reduced, clock});
    const {nav, motion} = driver;
    const layer = {kind: 'navigation', bounds, material: {...material}, motion, nav, driver, state: driver.initial};
    layers.add(layer);
    function attached() { alive(); if (!layers.has(layer)) throw new Error('Glass layer removed'); }
    return {
      get bounds() { attached(); return copyBounds(layer.bounds); },
      get selected() { attached(); return nav.selected; },
      get selector() { attached(); return nav.state(); },
      /** The bar's own press feedback (matrix, offset) as of the last event or draw. */
      get feedback() { attached(); const s = layer.state; return {...s, matrix: [...s.matrix], offset: [...s.offset]}; },
      /** Logical page coordinates and monotonic seconds, as for other layers. */
      down(x, y, time) { attached(); const t = timeAt(time); layer.state = driver.down(x, y, t); clock = t; },
      move(x, y, time) { attached(); const t = timeAt(time); layer.state = driver.move(x, y, t) ?? layer.state; clock = t; },
      up(x, y, time) { attached(); const t = timeAt(time); layer.state = driver.up(x, y, t); clock = t; },
      cancel(time) { attached(); const t = timeAt(time); layer.state = driver.cancel(t); clock = t; },
      /** The host's selection (after onSelect, or from elsewhere). */
      select(index) { attached(); nav.select(index); },
      /** The bar's items drawn in their selected appearance (transparent ground, device px, bar
       * origin), shown through the selector; null removes it. Borrowed: keep it alive while set. */
      setSelectedInk(image) { attached(); layer.ink = image ?? null; },
      remove() { layers.delete(layer); },
    };
  }

  return {
    addNavigation,
    setSource(source) { alive(); painter.setSource(source); image = source; },
    addSurface(bounds, material = {}) { return add('surface', bounds, material); },
    addLens(bounds, material = {}) { return add('lens', bounds, material); },
    resize(next, time) {
      const current = timeAt(time), view = viewport({...size, ...next});
      for (const layer of layers) layer.state = layer.motion.resize(geometry(layer.bounds, view), current);
      size = view; clock = current;
    },
    setReducedMotion(value, time) {
      const current = timeAt(time);
      for (const layer of layers) {
        layer.state = layer.motion.setReducedMotion(!!value, current);
        layer.nav?.setMotion(!value);
      }
      reduced = !!value; clock = current;
    },
    /** Draw in insertion order. True requests another frame; a settled hold can stop drawing. */
    draw(canvas, time, {drawBackdrop = true} = {}) {
      const current = timeAt(time, true);
      if (!image) throw new Error('Call setSource before drawing the scene');
      const ratio = size.pixelRatio;
      let active = false;
      for (const layer of layers) {
        if (layer.driver) { const frame = layer.driver.advance(current); layer.state = frame.feedback; active ||= frame.active; }
        else { layer.state = layer.motion.sample(current); active ||= layer.state.active; }
      }
      clock = current;
      if (drawBackdrop) canvas.drawImage(image, 0, 0);
      for (const layer of layers) {
        if (layer.driver) {
          drawNavigation(kit, painter, canvas, {bounds: layer.bounds, pixelRatio: ratio, source: image},
            {selector: layer.nav.state(), feedback: layer.state}, {material: layer.material, ink: layer.ink});
          continue;
        }
        const state = layer.state;
        const [a, b, c, d] = state.matrix;
        const bounds = layer.bounds;
        const cx = (bounds.x + bounds.width / 2) * ratio, cy = (bounds.y + bounds.height / 2) * ratio;
        const deviceBounds = {...bounds, x: bounds.x * ratio, y: bounds.y * ratio,
          width: bounds.width * ratio, height: bounds.height * ratio,
          radius: Array.isArray(bounds.radius) ? bounds.radius.map(r => r * ratio) : bounds.radius * ratio};
        canvas.save();
        try {
          canvas.translate(cx + state.offset[0] * ratio, cy + state.offset[1] * ratio);
          canvas.concat([a, b, 0, c, d, 0, 0, 0, 1]);
          canvas.translate(-cx, -cy);
          if (layer.kind === 'lens') painter.drawLens(canvas, deviceBounds, lensOptions(layer.material, ratio));
          else painter.drawSurface(canvas, deviceBounds,
            {...layer.material, density: (layer.material.density ?? 1) * ratio});
        } finally { canvas.restore(); }
      }
      return active;
    },
    dispose() { if (!disposed) { disposed = true; layers.clear(); image = null; painter.dispose(); } },
  };
}

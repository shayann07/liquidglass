/** Apache-2.0. One backdrop, many calm glass surfaces, independent of UI framework. */
import {createGlassPainter} from './painter.mjs';
import {createCalmInteraction} from './interaction.mjs';

const copyBounds = bounds => ({...bounds, ...(Array.isArray(bounds.radius) ? {radius: [...bounds.radius]} : {})});
function viewport({width, height, pixelRatio = 1}) {
  if (![width, height, pixelRatio].every(Number.isFinite) ||
      width <= 0 || height <= 0 || width > 1e7 || height > 1e7 || pixelRatio <= 0 || pixelRatio > 4)
    throw new RangeError('Positive logical viewport dimensions and pixelRatio in (0, 4] required');
  return {width, height, pixelRatio};
}
function boundsFor(kind, input) {
  const bounds = {...input};
  if (kind === 'lens') { bounds.height ??= bounds.width; bounds.magnification ??= 1.25; }
  if (![bounds.x, bounds.y, bounds.width, bounds.height].every(Number.isFinite) ||
      bounds.width <= 0 || bounds.height <= 0 || bounds.width > 1e7 || bounds.height > 1e7)
    throw new RangeError('Finite position and positive logical bounds required');
  if (kind === 'lens') {
    if (!Number.isFinite(bounds.magnification) || bounds.magnification < 1 || bounds.magnification > 2.5)
      throw new RangeError('Magnification must be 1–2.5');
    bounds.radius = Math.min(bounds.width, bounds.height) / 2;
  } else {
    bounds.radius ??= 20;
    const radii = Array.isArray(bounds.radius) ? bounds.radius : [bounds.radius];
    if ((Array.isArray(bounds.radius) && radii.length !== 4) || !radii.every(r => Number.isFinite(r) && r >= 0))
      throw new RangeError('Radius must be nonnegative, or four nonnegative corner radii');
    bounds.radius = Array.isArray(bounds.radius) ? [...bounds.radius] : bounds.radius;
  }
  return bounds;
}

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
  function geometry(bounds, view = size) {
    return {...bounds, radius: Array.isArray(bounds.radius) ? Math.min(...bounds.radius) : bounds.radius,
      viewportWidth: view.width, viewportHeight: view.height};
  }
  function add(kind, input, material = {}) {
    alive();
    const bounds = boundsFor(kind, input);
    const motion = createCalmInteraction({...geometry(bounds), reducedMotion: reduced});
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
        update(time, t => motion.resize(geometry(bounds), t)); layer.bounds = bounds;
      },
      setMaterial(next) {
        attached();
        if (kind !== 'surface') throw new TypeError('Use setBounds to change lens magnification');
        layer.material = {...layer.material, ...next};
      },
      remove() { layers.delete(layer); },
    };
  }
  return {
    setSource(source) { alive(); painter.setSource(source); image = source; },
    addSurface(bounds, material = {}) { return add('surface', bounds, material); },
    addLens(bounds) { return add('lens', bounds); },
    resize(next, time) {
      const current = timeAt(time), view = viewport({...size, ...next});
      for (const layer of layers) layer.state = layer.motion.resize(geometry(layer.bounds, view), current);
      size = view; clock = current;
    },
    setReducedMotion(value, time) {
      const current = timeAt(time);
      for (const layer of layers) layer.state = layer.motion.setReducedMotion(!!value, current);
      reduced = !!value; clock = current;
    },
    /** Draw in insertion order. True requests another frame; a settled hold can stop drawing. */
    draw(canvas, time, {drawBackdrop = true} = {}) {
      const current = timeAt(time, true);
      if (!image) throw new Error('Call setSource before drawing the scene');
      const ratio = size.pixelRatio;
      let active = false;
      for (const layer of layers) {
        layer.state = layer.motion.sample(current);
        active ||= layer.state.active;
      }
      clock = current;
      if (drawBackdrop) canvas.drawImage(image, 0, 0);
      for (const layer of layers) {
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
          if (layer.kind === 'lens') painter.drawLens(canvas, deviceBounds);
          else painter.drawSurface(canvas, deviceBounds,
            {...layer.material, density: (layer.material.density ?? 1) * ratio});
        } finally { canvas.restore(); }
      }
      return active;
    },
    dispose() { if (!disposed) { disposed = true; layers.clear(); image = null; painter.dispose(); } },
  };
}

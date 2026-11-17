import {test} from 'node:test';
import assert from 'node:assert/strict';
import CanvasKitInit from 'canvaskit-wasm';
import {createGlassScene, createGlassPainter, bakeNavigation, navigationTrack, drawNavigation} from 'liquidglass-skia-preview';
import {bakeNavigation as bakeSubpath} from 'liquidglass-skia-preview/bake';
import {createNavigationDriver} from './navigation.mjs';
import {boundsFor} from './bounds.mjs';

const kit = await CanvasKitInit();
const W = 200, H = 100, BAR = {x: 10, y: 30, width: 180, height: 44};
const info = (w, h) => ({width: w, height: h, colorType: kit.ColorType.RGBA_8888, alphaType: kit.AlphaType.Premul, colorSpace: kit.ColorSpace.SRGB});
function page(ratio) {
  const w = W * ratio, h = H * ratio, bytes = new Uint8Array(w * h * 4);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) bytes.set([40 + x % 170, 30 + y % 150, (x + y) % 200, 255], (y * w + x) * 4);
  const image = kit.MakeImage(info(w, h), bytes, w * 4), surface = kit.MakeSurface(w, h);
  return {image, surface, canvas: surface.getCanvas(), w, h,
    read() { surface.flush(); return Array.from(surface.getCanvas().readPixels(0, 0, info(w, h))); },
    close() { surface.delete(); image.delete(); }};
}
function inkImage(ratio) {
  const surface = kit.MakeSurface(BAR.width * ratio, BAR.height * ratio), c = surface.getCanvas();
  const paint = new kit.Paint(); paint.setColor(kit.Color4f(1, .2, .2, 1));
  for (let i = 0; i < 3; i++) c.drawCircle((BAR.width / 6 + i * BAR.width / 3) * ratio, BAR.height / 2 * ratio, 8 * ratio, paint);
  paint.delete(); surface.flush();
  const image = surface.makeImageSnapshot(); surface.delete(); return image;
}
// A tap; a hold that drags across the tabs and is flung; a hold that is cancelled; a select from the host.
const events = [
  {type: 'down', x: 95, y: 52, time: .1}, {type: 'up', x: 95, y: 52, time: .16},
  {type: 'down', x: 30, y: 52, time: .6}, ...[...Array(20)].map((_, i) => ({type: 'move', x: 30, y: 52, time: .62 + i * .02})),
  ...[...Array(24)].map((_, i) => ({type: 'move', x: 30 + 150 * (i + 1) / 24, y: 52, time: 1.05 + i * .015})),
  {type: 'up', x: 180, y: 52, time: 1.42},
  {type: 'down', x: 95, y: 52, time: 1.9}, ...[...Array(15)].map((_, i) => ({type: 'move', x: 95, y: 52, time: 1.92 + i * .02})),
  {type: 'cancel', time: 2.3}, {type: 'select', index: 0, time: 2.5},
];
const DURATION = 3, FLAT = s => JSON.stringify(s);
const config = (extra = {}) => ({bounds: BAR, count: 3, viewport: {width: W, height: H}, ...extra});

test('the stateless drawing and the track equal a live scene, frame for frame', () => {
  // A coarse step: a software-rendered scene frame takes a second or more. The equality does not depend on the step.
  const step = .2, picks = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14];
  const f = page(1), scene = createGlassScene(kit, {width: W, height: H}), painter = createGlassPainter(kit), grabbed = new Map();
  const track = bakeNavigation(config(), events, {step, duration: DURATION});
  try {
    scene.setSource(f.image); painter.setSource(f.image);
    const layer = scene.addNavigation(BAR, {count: 3, material: {dark: true}, onSelect: i => layer.select(i)});
    let next = 0;
    for (let k = 0; k < track.length; k++) {
      const t = k * step;
      for (; next < events.length && events[next].time <= t; next++) {
        const e = events[next];
        if (e.type === 'down') layer.down(e.x, e.y, e.time); else if (e.type === 'move') layer.move(e.x, e.y, e.time);
        else if (e.type === 'up') layer.up(e.x, e.y, e.time); else if (e.type === 'cancel') layer.cancel(e.time); else layer.select(e.index);
      }
      const active = scene.draw(f.canvas, t), s = track.sample(t);
      assert.deepEqual(s.selector, layer.selector, `selector at step ${k}`);
      assert.deepEqual(s.feedback, layer.feedback, `feedback at step ${k}`);
      assert.equal(s.selected, layer.selected, `selection at step ${k}`);
      assert.equal(s.active, active, `active at step ${k}`);
      if (picks.includes(k)) grabbed.set(k, f.read());
    }
    assert.equal(picks.length, 12);
    // The same frames, drawn with no scene and no clock, and out of order, equal the live ones.
    const view = {bounds: BAR, pixelRatio: 1, source: f.image};
    const render = k => { f.canvas.drawImage(f.image, 0, 0); drawNavigation(kit, painter, f.canvas, view, track.sample(k * step), {material: {dark: true}}); return f.read(); };
    const order = [...picks].reverse(); order.splice(3, 0, ...order.splice(8, 2)); // reversed, then shuffled
    for (const k of order) assert.deepEqual(render(k), grabbed.get(k), `stateless frame ${k} differs from the live scene`);
    // The track is read-only: asking again, in any order, gives the same frame.
    for (const k of [picks[5], picks[0], picks[5]]) assert.deepEqual(render(k), grabbed.get(k));
  } finally { scene.dispose(); painter.dispose(); f.close(); }
});

test('the stateless drawing equals the scene at pixel ratio 2, in light, with selected ink', () => {
  const ratio = 2, step = .4, picks = [2, 4, 6], ink = inkImage(ratio);
  const f = page(ratio), scene = createGlassScene(kit, {width: W, height: H, pixelRatio: ratio}), painter = createGlassPainter(kit), grabbed = new Map();
  const material = {dark: false, increasedContrast: .5};
  const track = bakeNavigation(config(), events, {step, duration: 2.4});
  try {
    scene.setSource(f.image); painter.setSource(f.image);
    const layer = scene.addNavigation(BAR, {count: 3, material, onSelect: i => layer.select(i)}); layer.setSelectedInk(ink);
    let next = 0;
    for (let k = 0; k < track.length; k++) {
      const t = k * step;
      for (; next < events.length && events[next].time <= t; next++) {
        const e = events[next];
        if (e.type === 'down') layer.down(e.x, e.y, e.time); else if (e.type === 'move') layer.move(e.x, e.y, e.time);
        else if (e.type === 'up') layer.up(e.x, e.y, e.time); else if (e.type === 'cancel') layer.cancel(e.time); else layer.select(e.index);
      }
      scene.draw(f.canvas, t);
      if (picks.includes(k)) grabbed.set(k, f.read());
    }
    for (const k of picks) {
      f.canvas.drawImage(f.image, 0, 0);
      drawNavigation(kit, painter, f.canvas, {bounds: BAR, pixelRatio: ratio, source: f.image}, track.sample(k * step), {material, ink});
      assert.deepEqual(f.read(), grabbed.get(k), `ratio 2 frame ${k}`);
    }
  } finally { scene.dispose(); painter.dispose(); f.close(); ink.delete(); }
});

test('a fine bake is deterministic and is the navigation motion at every step', () => {
  const a = bakeNavigation(config(), events, {duration: DURATION}), b = bakeNavigation(config(), events, {duration: DURATION});
  assert.equal(a.step, 1 / 240); assert.equal(a.length, 721);
  assert.deepEqual(a.toJSON(), b.toJSON());
  // The scene's navigation layer runs this same driver (the scene tests above compare the two frame for frame);
  // here every step of a 1/240 run is read straight from it, drawing nothing.
  const bounds = boundsFor('surface', {...BAR, radius: BAR.height / 2});
  let driver; driver = createNavigationDriver({bounds, view: {width: W, height: H}, count: 3, onSelect: i => driver.nav.select(i)});
  let feedback = driver.initial, next = 0;
  for (let k = 0; k < a.length; k++) {
    const t = k * a.step;
    for (; next < events.length && events[next].time <= t; next++) {
      const e = events[next];
      if (e.type === 'down') feedback = driver.down(e.x, e.y, e.time); else if (e.type === 'move') feedback = driver.move(e.x, e.y, e.time) ?? feedback;
      else if (e.type === 'up') feedback = driver.up(e.x, e.y, e.time); else if (e.type === 'cancel') feedback = driver.cancel(e.time); else driver.nav.select(e.index);
    }
    const frame = driver.advance(t), s = a.sample(t);
    assert.deepEqual(s.selector, driver.nav.state(), `selector at 1/240 step ${k}`);
    assert.deepEqual(s.feedback, frame.feedback, `feedback at 1/240 step ${k}`);
    assert.equal(s.selected, driver.nav.selected); assert.equal(s.active, frame.active);
  }
  assert.equal(next, events.length);
});

test('between steps the track interpolates, keeps decisions, inverts the pose and clamps at both ends', () => {
  const step = 1 / 60, track = bakeNavigation(config(), events, {step, duration: DURATION});
  assert.deepEqual(track.sample(-5), track.sample(0));
  assert.deepEqual(track.sample(99), track.sample((track.length - 1) * step));
  assert.throws(() => track.sample(NaN), /finite/);
  let moved = false;
  const numbers = (value, out = []) => { if (typeof value === 'number') out.push(value); else if (value && typeof value === 'object') Object.values(value).forEach(v => numbers(v, out)); return out; };
  for (let k = 0; k < track.length - 1; k++) {
    const lo = track.sample(k * step), hi = track.sample((k + 1) * step), mid = track.sample((k + .37) * step);
    // aInv is recomputed, not blended: it is asserted as an inverse below, so leave it out of the neighbour bounds.
    const strip = s => ({selector: {...s.selector, pose: {...s.selector.pose, aInv: []}}, feedback: s.feedback});
    const [x, y, z] = [lo, hi, mid].map(s => numbers(strip(s)));
    const [a0, a1, a2, a3] = mid.selector.pose.a, [i0, i1, i2, i3] = mid.selector.pose.aInv;
    for (let i = 0; i < z.length; i++) {
      if (Math.abs(x[i] - y[i]) > 1e-12) moved = true;
      assert(z[i] >= Math.min(x[i], y[i]) - 1e-9 && z[i] <= Math.max(x[i], y[i]) + 1e-9 , `value ${i} left its neighbours at step ${k}`);
    }
    const product = [a0 * i0 + a1 * i2, a0 * i1 + a1 * i3, a2 * i0 + a3 * i2, a2 * i1 + a3 * i3];
    [1, 0, 0, 1].forEach((v, i) => assert(Math.abs(product[i] - v) < 1e-9, `aInv is not the inverse of a at step ${k}`));
    assert.equal(mid.selector.held, lo.selector.held); assert.equal(mid.selected, lo.selected); assert.equal(mid.selector.tapFormed, lo.selector.tapFormed);
  }
  assert(moved, 'the gesture never moved the selector');
  // The script selected the last tab and then the host chose the first again.
  assert.equal(track.sample(DURATION).selected, 0);
  assert.equal(track.sample(1.6).selected, 2);
});

test('a track is plain data: a copy samples identically', () => {
  const track = bakeNavigation(config(), events, {step: 1 / 60, duration: DURATION});
  const json = JSON.parse(JSON.stringify(track));
  assert.equal(JSON.stringify(json), JSON.stringify(track.toJSON()), "toJSON is what stringify writes");
  const copy = navigationTrack(json);
  for (const t of [0, .1, .333, 1.07, 1.5, 2.9, 3]) assert.equal(FLAT(copy.sample(t)), FLAT(track.sample(t)));
  assert.equal(copy.length, track.length);
  assert.throws(() => navigationTrack({}), /track/);
  assert.throws(() => navigationTrack({...json, columns: json.columns.slice(1)}), /track/);
  assert.equal(typeof bakeSubpath, 'function');
});

test('bad scripts and bars are rejected', () => {
  const bad = (bar, script, options, pattern) => assert.throws(() => bakeNavigation(bar, script, options), pattern);
  bad(config(), [{type: 'tap', time: 0}], {duration: 1}, /event type/);
  bad(config(), [{type: 'down', x: 1, y: 1, time: .5}, {type: 'up', x: 1, y: 1, time: .2}], {duration: 1}, /nondecreasing/);
  bad(config(), [{type: 'down', x: NaN, y: 1, time: 0}], {duration: 1}, /finite x and y/);
  bad(config(), [{type: 'select', index: 1.5, time: 0}], {duration: 1}, /index/);
  bad(config(), [], {duration: -1}, /duration/);
  bad(config(), [], {duration: 1, step: 0}, /step/);
  bad(config({count: 0}), [], {duration: 1}, /Count/);
  bad(config({bounds: {x: 0, y: 0, width: 0, height: 10}}), [], {duration: 1}, /bounds/);
  assert.equal(bakeNavigation(config(), [], {duration: 0}).length, 1);
});

test('the held selector\'s superellipse blend (pose.e) reaches the painter unchanged', () => {
  const track = bakeNavigation(config(), events, {step: 1 / 60, duration: DURATION});
  // A hold that has formed the lens carries a non-default blend; the default [0, 3, 0, 0] is what a dropped `e` would give.
  const held = track.sample(1.0);
  assert(held.selector.held && held.selector.pose.e[0] > 0, `no held lens at 1.0 s: ${held.selector.pose.e}`);
  const seen = [];
  const painter = {drawCalmBar() {}, drawSelector(_c, _b, state) { seen.push(state); }, drawSelectorInk(_c, _b, state) { seen.push(state); }};
  const canvas = {save() {}, restore() {}, translate() {}, concat() {}, makeSurface: () => null};
  for (const pixelRatio of [1, 2]) {
    seen.length = 0;
    drawNavigation(kit, painter, canvas, {bounds: BAR, pixelRatio, source: {width: () => 1, height: () => 1}}, held, {ink: {}});
    assert.equal(seen.length, 2);
    for (const state of seen) assert.deepEqual(state.pose.e, held.selector.pose.e, `pose.e changed at ratio ${pixelRatio}`);
  }
});

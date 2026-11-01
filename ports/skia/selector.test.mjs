// Apache-2.0. The ported Calm selector against trajectories exported by the Kotlin controller.
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {CalmSelectorController, calmSelectorBar, centreOf, nearestTab, createCalmNavigation} from './selector.mjs';

const fixture = JSON.parse(await readFile(new URL('./fixtures/selector-calm.json', import.meta.url), 'utf8'));
const MODES = {Rest: 'rest', TapTransit: 'tap', PressPending: 'pending', Held: 'held', Released: 'released'};
const field = name => fixture.frameFields.indexOf(name);
const bar = () => {
  const b = fixture.bar;
  return calmSelectorBar({width: b.width, height: b.height, count: b.count, inset: b.inset,
    pillWidth: b.pillWidth, pillInset: b.pillInset, density: b.density});
};

/** Positions in px; shape and optics in their own units. Float32 (Kotlin) against float64 here. */
const TOLERANCE = {cx: .05, cy: .05, left: .05, right: .05, top: .05, bottom: .05, formation: 2e-4,
  transit: 1e-3, heldTravel: 1e-4, a0: 1e-4, a1: 1e-4, a2: 1e-4, a3: 1e-4, spine: .05, radius: .05, band: .05};

for (const scenario of fixture.scenarios) {
  test(`Calm selector matches Kotlin: ${scenario.name}`, () => {
    const c = new CalmSelectorController();
    c.attach(bar());
    const rest = scenario.calls.find(call => call[1] === 'rest');
    c.snapToRest(rest ? rest[2] : 0);
    let next = 0;
    const worst = {};
    for (const frame of scenario.frames) {
      const tf = frame[field('t')];
      while (next < scenario.calls.length && scenario.calls[next][0] <= tf) {
        const [t, op, ...a] = scenario.calls[next++];
        if (op === 'down') c.pointerDown(a[0], a[1], t, true);
        else if (op === 'move') c.pointerMove(a[0], a[1], t);
        else if (op === 'drag') c.beginDrag(a[0], a[1]);
        else if (op === 'retarget') c.retarget(a[0]);
        else if (op === 'up') c.pointerUp(a[0]);
      }
      c.advanceFrameTo(tf);
      const e = c.extents(), f = c.frame;
      const ours = {cx: c.centreX, cy: c.centreY, formation: c.formation, transit: c.transitAppearance,
        heldTravel: c.heldTravel, left: e.left, right: e.right, top: e.top, bottom: e.bottom,
        a0: f.a[0], a1: f.a[1], a2: f.a[2], a3: f.a[3], spine: f.spine, radius: f.radius, band: f.band()};
      assert.equal(c.mode, MODES[frame[field('mode')]], `${scenario.name} t=${tf} mode`);
      for (const [key, tol] of Object.entries(TOLERANCE)) {
        const error = Math.abs(ours[key] - frame[field(key)]);
        worst[key] = Math.max(worst[key] ?? 0, error);
        assert.ok(error <= tol, `${scenario.name} t=${tf} ${key}: ${ours[key]} vs ${frame[field(key)]}`);
      }
    }
  });
}

test('navigation: a tap selects the tab under the finger and settles there', () => {
  const selected = [];
  const nav = createCalmNavigation({bar: bar(), onSelect: i => selected.push(i)});
  nav.down(700, 93, .1); nav.up(700, 93, .16);
  assert.deepEqual(selected, [2]);
  let t = .16;
  while (nav.frame(t) && t < 3) t += 1 / 60;
  assert.ok(t < 2, 'tap never settled');
  assert.ok(Math.abs(nav.state().centreX - nav.controller.bar.inset - 2.5 * nav.controller.bar.slotWidth) < .5);
});

test('navigation: any touch lifts the bar (touchLift), rising in 50 ms and gone 260 ms after the lift', () => {
  const nav = createCalmNavigation({bar: bar()});
  nav.frame(0); assert.equal(nav.state().touch, 0);
  nav.down(700, 93, .1); nav.frame(.125);
  const rising = nav.state().touch;
  assert.ok(rising > 0 && rising < 1, `half-way through the rise: ${rising}`);
  nav.frame(.15); assert.equal(nav.state().touch, 1);
  nav.up(700, 93, .16); nav.frame(.29);
  const falling = nav.state().touch;
  assert.ok(falling > 0 && falling < 1, `half-way through the fall: ${falling}`);
  nav.frame(.42); assert.equal(nav.state().touch, 0);
  // A tap never forms the selector, so only the touch lifts the bar.
  const dark = calmBarMaterial({dark: true, formation: 0, touch: 1}).uLift;
  assert.ok(dark > calmBarMaterial({dark: true}).uLift + 12 / 255, 'dark Calm lifts for a touch');
  assert.ok(calmBarMaterial({dark: false, touch: 1}).uLift > calmBarMaterial({dark: false}).uLift, 'light Calm lifts too (authored)');
});

test('clear bar (GlassTabBarStyle.CalmClear): black at 0.10 over the same lift, legibility 2, blur 7 dp, light bar unchanged', () => {
  const calm = calmBarMaterial({dark: true}), clear = calmBarMaterial({dark: true, clear: true});
  assert.deepEqual([...clear.uTint].slice(0, 3), [0, 0, 0]);
  assert.ok(Math.abs(clear.uTint[3] - 26 / 255) < 1e-6, 'Compose stores the 0.10 alpha in 8 bits');
  assert.equal(clear.uLift, calm.uLift);
  assert.equal(clear.uLegibility, 2, 'it darkens over text (GlassStyle.legibility)');
  assert.equal(clear.uBlur, 7, 'it shows text as one soft copy');
  assert.deepEqual({...clear, uTint: calm.uTint, uLegibility: calm.uLegibility, uBlur: calm.uBlur}, calm, 'only tint, legibility and blur differ');
  assert.deepEqual(calmBarMaterial({dark: false, clear: true}), calmBarMaterial({dark: false}));
});

test('navigation: a hold-and-drag commits where the finger ends, a fling carries one further', () => {
  const b = bar(), selected = [];
  const nav = createCalmNavigation({bar: b, onSelect: i => selected.push(i)});
  nav.down(155, 93, 0);
  for (let t = .01; t <= .3; t += .01) { nav.move(155, 93, t); nav.frame(t); }
  assert.ok(nav.state().held, 'hold did not form');
  // Slow drag to the middle tab: commits the middle.
  for (let i = 1; i <= 50; i++) { const t = .3 + i * .02; nav.move(155 + 262 * i / 50, 93, t); nav.frame(t); }
  for (let i = 1; i <= 10; i++) { const t = 1.3 + i * .02; nav.move(417, 93, t); nav.frame(t); }
  nav.up(417, 93, 1.52);
  assert.deepEqual(selected, [1]);
  // A fast flick from the middle carries to the last tab.
  nav.down(417, 93, 3);
  for (let i = 1; i <= 15; i++) { const t = 3 + i * .02; nav.move(417, 93, t); nav.frame(t); }
  for (let i = 1; i <= 10; i++) { const t = 3.3 + i * .01; nav.move(417 + 25 * i, 93, t); nav.frame(t); }
  nav.up(667, 93, 3.4);
  assert.deepEqual(selected, [1, 2]);
});

test('navigation: a vertical scroll before the hold cancels without selecting', () => {
  const selected = [];
  const nav = createCalmNavigation({bar: bar(), onSelect: i => selected.push(i)});
  nav.down(417, 93, 0); nav.move(417, 140, .05); nav.up(417, 140, .08);
  assert.deepEqual(selected, []);
  assert.equal(nav.selected, 0);
});

test('reduced motion snaps to rest and never holds', () => {
  const nav = createCalmNavigation({bar: bar(), motion: false});
  nav.down(700, 93, 0);
  for (let t = .01; t <= .5; t += .01) { nav.move(700, 93, t); nav.frame(t); }
  assert.equal(nav.state().held, false);
  nav.up(700, 93, .5); nav.frame(.52);
  assert.equal(nav.state().formation, 0);
});

// ---- material: the Compose Calm selector's own uniforms (GlassSelectorPortMaterialTest)
import {calmSelectorMaterial, calmBarMaterial} from './selector-material.mjs';
import {createGlassPainter} from './painter.mjs';
import CanvasKitInit from 'canvaskit-wasm';

const material = JSON.parse(await readFile(new URL('./fixtures/selector-material.json', import.meta.url), 'utf8'));

test('material: every Kotlin-computed selector material is reproduced', () => {
  let worst = 0;
  for (const s of material.samples) {
    const ours = calmSelectorMaterial({dark: s.dark, formation: s.formation, transit: s.transit, heldTravel: s.heldTravel});
    assert.ok(Math.abs(ours.uFormation - s.uFormation) <= 1e-6, `uFormation at ${JSON.stringify(s)}`);
    for (const [key, value] of Object.entries(s.values)) {
      const a = [].concat(value), b = [].concat(ours.values[key]);
      assert.equal(b.length, a.length, key);
      a.forEach((x, i) => { const e = Math.abs(x - b[i]); worst = Math.max(worst, e);
        assert.ok(e <= 1e-5, `${key} at f=${s.formation} t=${s.transit} dark=${s.dark}: ${b[i]} vs ${x}`); });
    }
  }
  assert.ok(material.samples.length >= 96 && worst <= 1e-5);
});

test('material: the lens looks through the bar it sits on', async () => {
  const kit = await CanvasKitInit();
  const w = 360, h = 140, surface = kit.MakeSurface(w, h), canvas = surface.getCanvas();
  const pixels = new Uint8Array(w * h * 4);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    // A dark page, as in the recordings: the bar's lifted glass is what the lens sees.
    const i = (y * w + x) * 4; pixels[i] = x % 24; pixels[i + 1] = y % 24; pixels[i + 2] = (x + y) % 24; pixels[i + 3] = 255;
  }
  const image = kit.MakeImage({width: w, height: h, alphaType: kit.AlphaType.Unpremul, colorType: kit.ColorType.RGBA_8888,
    colorSpace: kit.ColorSpace.SRGB}, pixels, w * 4);
  const painter = createGlassPainter(kit);
  const c = new CalmSelectorController(); c.attach(calmSelectorBar({width: 300, height: 62, count: 3}));
  c.snapToRest(0);
  const rect = {x: 20, y: 40, width: 300, height: 62};
  const read = () => canvas.readPixels(0, 0, {width: w, height: h, colorType: kit.ColorType.RGBA_8888,
    alphaType: kit.AlphaType.Premul, colorSpace: kit.ColorSpace.SRGB});
  try {
    painter.setSource(image);
    canvas.drawImage(image, 0, 0); painter.drawCalmBar(canvas, rect, {dark: true});
    const through = surface.makeImageSnapshot();
    try {
      painter.drawSelector(canvas, rect, {pose: c.render(), formation: 0}, {dark: true, through});
      const seen = read();
      canvas.drawImage(through, 0, 0); painter.drawSelector(canvas, rect, {pose: c.render(), formation: 0}, {dark: true});
      const bare = read();
      const lum = (p, x, y) => { const i = (y * w + x) * 4; return p[i] + p[i + 1] + p[i + 2]; };
      // Inside the resting pill the bar's own glass (lift 45/255) shows through the selector.
      assert.ok([[70, 60], [80, 80], [100, 70]].every(([x, y]) => lum(seen, x, y) > lum(bare, x, y) + 20),
        'the lens did not look through the bar');
      assert.equal(lum(seen, 290, 71), lum(bare, 290, 71), 'the selector changed the far end of the bar');
    } finally { through.delete(); }
  } finally { painter.dispose(); image.delete(); surface.delete(); }
});

test('ink: the selected ink shows only inside the selector', async () => {
  const kit = await CanvasKitInit();
  const w = 360, h = 140, surface = kit.MakeSurface(w, h), canvas = surface.getCanvas();
  const page = new Uint8Array(w * h * 4).fill(10); for (let i = 3; i < page.length; i += 4) page[i] = 255;
  const info = {width: w, height: h, alphaType: kit.AlphaType.Unpremul, colorType: kit.ColorType.RGBA_8888, colorSpace: kit.ColorSpace.SRGB};
  const image = kit.MakeImage(info, page, w * 4);
  // The bar's items in their selected colour: a blue dot at every tab centre, bar-sized, transparent ground.
  const ink = new Uint8Array(300 * 62 * 4);
  for (const cx of [50, 150, 250]) for (let y = 21; y < 41; y++) for (let x = cx - 10; x < cx + 10; x++) ink.set([40, 110, 255, 255], (y * 300 + x) * 4);
  const inkImage = kit.MakeImage({...info, width: 300, height: 62}, ink, 300 * 4);
  const painter = createGlassPainter(kit);
  const c = new CalmSelectorController(); c.attach(calmSelectorBar({width: 300, height: 62, count: 3})); c.snapToRest(0);
  const rect = {x: 20, y: 40, width: 300, height: 62};
  try {
    painter.setSource(image); canvas.drawImage(image, 0, 0);
    painter.drawCalmBar(canvas, rect, {dark: true});
    painter.drawSelector(canvas, rect, {pose: c.render()}, {dark: true});
    painter.drawSelectorInk(canvas, rect, {pose: c.render()}, inkImage, {dark: true});
    const p = canvas.readPixels(0, 0, {...info, alphaType: kit.AlphaType.Premul});
    const at = (x, y) => { const i = (y * w + x) * 4; return [p[i], p[i + 1], p[i + 2]]; };
    const under = at(20 + 50, 40 + 31), beyond = at(20 + 250, 40 + 31);
    assert.ok(under[2] > 200 && under[2] > under[0] + 120, `selected ink missing inside the selector: ${under}`);
    assert.ok(beyond[2] < 120, `selected ink leaked outside the selector: ${beyond}`);
  } finally { painter.dispose(); inkImage.delete(); image.delete(); surface.delete(); }
});

test('set slots (GlassTabBarStyle.slotWidth): WhatsApp measured centres, taps and a drag to the end', () => {
  // 348 x 62 pt at 3x, 71.5 pt slots, a 78 pt pill 4 pt inside: as GlassTabBarGeometryTest.
  const b = calmSelectorBar({width: 1044, height: 186, count: 5, pillWidth: 234, pillInset: 12, density: 3, slotWidth: 214.5});
  const ios = [130, 307, 521.5, 736.25, 913];
  for (let i = 0; i < 5; i++) {
    assert.ok(Math.abs(centreOf(b, i) - ios[i]) <= 2, `centre ${i}: ${centreOf(b, i)}`);
    assert.equal(nearestTab(b, centreOf(b, i)), i);
  }
  assert.equal(centreOf(b, 0), 129); assert.equal(centreOf(b, 4), 915);
  // Unset, the bar is shared out exactly as before.
  const shared = calmSelectorBar({width: 1044, height: 186, count: 5, inset: 18, density: 3});
  assert.ok(Number.isNaN(shared.fixedSlot));
  assert.equal(centreOf(shared, 0), 18 + shared.slotWidth / 2);
  const selected = [];
  const nav = createCalmNavigation({bar: b, selected: 2, onSelect: i => selected.push(i)});
  nav.down(140, 93, .1); nav.up(140, 93, .16);
  assert.deepEqual(selected, [0]);
  let t = .16;
  while (nav.frame(t) && t < 3) t += 1 / 60;
  assert.ok(Math.abs(nav.state().centreX - 129) < .5, `tap landed at ${nav.state().centreX}`);
  nav.select(0);
  nav.down(129, 93, 3); t = 3;
  for (let k = 1; k <= 40; k++) { t = 3 + k / 60; nav.move(129 + k * (915 - 129) / 40, 93, t); nav.frame(t); }
  nav.up(915, 93, t + 1 / 60);
  while (nav.frame(t) && t < 7) t += 1 / 60;
  assert.equal(selected[selected.length - 1], 4);
  assert.ok(Math.abs(nav.state().centreX - 915) < .5, `drag landed at ${nav.state().centreX}`);
});

// ---- 18 October: the owner's device check, ported (lens neck, lens shadow, icons on a hold)
import {lensFuse} from './fuse.mjs';

function heldLens(barW, tab, t = .8) {
  const c = new CalmSelectorController(); const b = calmSelectorBar({width: barW, height: 62, count: 3});
  c.attach(b); c.snapToRest(tab);
  c.pointerDown(centreOf(b, tab), 31, 0, true);
  for (let s = 0; s <= t; s += 1 / 60) c.advanceFrameTo(s);
  return c;
}

test('lensFuse: a held lens takes a neck to the button; a resting or tapped one does not', () => {
  const bar = {width: 300, height: 62}, button = {x: 308, y: 0, width: 62, height: 62};
  assert.equal(lensFuse({bar, button, formation: .3, lensFusion: 36}), null);
  assert.equal(lensFuse({bar, button, formation: 1, lensFusion: 0}), null);
  const f = lensFuse({bar, button, formation: 1, lensFusion: 36});
  assert.deepEqual(f, [308 + 31 - 150, 0, 31, 31, 31, 36]);
  assert.ok(lensFuse({bar, button, formation: .75, lensFusion: 36})[5] < 36, 'the neck grows with formation');
  assert.throws(() => lensFuse({bar, button: {...button, x: NaN}, formation: 1, lensFusion: 36}), RangeError);
});

test('light held lens: its shadow lies below it, none above', async () => {
  const kit = await CanvasKitInit();
  const w = 420, h = 200, surface = kit.MakeSurface(w, h), canvas = surface.getCanvas();
  const page = new Uint8Array(w * h * 4).fill(255);
  const image = kit.MakeImage({width: w, height: h, alphaType: kit.AlphaType.Unpremul, colorType: kit.ColorType.RGBA_8888,
    colorSpace: kit.ColorSpace.SRGB}, page, w * 4);
  const painter = createGlassPainter(kit);
  const c = heldLens(300, 2);
  assert.ok(c.formation > .9, `the hold formed the lens (${c.formation})`);
  const rect = {x: 20, y: 70, width: 300, height: 62};
  const read = () => canvas.readPixels(0, 0, {width: w, height: h, colorType: kit.ColorType.RGBA_8888,
    alphaType: kit.AlphaType.Premul, colorSpace: kit.ColorSpace.SRGB});
  const lum = (p, x, y) => { const i = (y * w + x) * 4; return (p[i] + p[i + 1] + p[i + 2]) / 3; };
  const draw = options => {
    canvas.clear(kit.WHITE); canvas.drawImage(image, 0, 0);
    painter.drawCalmBar(canvas, rect, {dark: false});
    const through = surface.makeImageSnapshot();
    try { painter.drawSelector(canvas, rect, {pose: c.render(), formation: c.formation}, {dark: false, through, ...options}); }
    finally { through.delete(); }
    return read();
  };
  try {
    painter.setSource(image);
    const lit = draw({}), plain = draw({shadow: false});
    const e = c.extents(), x = Math.round(rect.x + (e.left + e.right) / 2);
    const below = Math.round(rect.y + e.bottom + 2), above = Math.round(rect.y + e.top - 6);
    assert.ok(lum(lit, x, below) < lum(plain, x, below) - 5, `no shadow below the lens: ${lum(lit, x, below)} / ${lum(plain, x, below)}`);
    assert.ok(Math.abs(lum(lit, x, above) - lum(plain, x, above)) <= 1, 'the shadow reached above the lens');
    assert.throws(() => draw({fuse: [1, 2, 3]}), RangeError);
  } finally { painter.dispose(); image.delete(); surface.delete(); }
});

test('dark held lens at the end: its neck fills the gap to the button', async () => {
  const kit = await CanvasKitInit();
  const w = 420, h = 200, surface = kit.MakeSurface(w, h), canvas = surface.getCanvas();
  const page = new Uint8Array(w * h * 4); for (let i = 0; i < page.length; i += 4) page.set([20, 20, 20, 255], i);
  const image = kit.MakeImage({width: w, height: h, alphaType: kit.AlphaType.Unpremul, colorType: kit.ColorType.RGBA_8888,
    colorSpace: kit.ColorSpace.SRGB}, page, w * 4);
  const painter = createGlassPainter(kit);
  const c = heldLens(300, 2), rect = {x: 20, y: 70, width: 300, height: 62};
  const read = () => canvas.readPixels(0, 0, {width: w, height: h, colorType: kit.ColorType.RGBA_8888,
    alphaType: kit.AlphaType.Premul, colorSpace: kit.ColorSpace.SRGB});
  const lum = (p, x, y) => { const i = (y * w + x) * 4; return p[i] + p[i + 1] + p[i + 2]; };
  const draw = fuse => {
    canvas.clear(kit.BLACK); canvas.drawImage(image, 0, 0); painter.drawCalmBar(canvas, rect, {dark: true});
    const through = surface.makeImageSnapshot();
    try { painter.drawSelector(canvas, rect, {pose: c.render(), formation: c.formation}, {dark: true, through, fuse}); }
    finally { through.delete(); }
    return read();
  };
  try {
    painter.setSource(image);
    const fuse = lensFuse({bar: rect, button: {x: rect.width + 8, y: 0, width: 62, height: 62}, formation: c.formation, lensFusion: 36});
    const plain = draw(null), fused = draw(fuse);
    for (const dx of [8, 12]) {
      const x = rect.x + rect.width + dx, y = rect.y + 31;
      assert.ok(lum(fused, x, y) > lum(plain, x, y) + 12, `no neck at ${x}: ${lum(fused, x, y)} / ${lum(plain, x, y)}`);
    }
  } finally { painter.dispose(); image.delete(); surface.delete(); }
});

test('navigation: items share the bar press only on a hold (itemFollow)', () => {
  const b = calmSelectorBar({width: 300, height: 62, count: 3});
  const nav = createCalmNavigation({bar: b, selected: 0});
  nav.down(centreOf(b, 1), 31, 0); nav.frame(.05); nav.up(centreOf(b, 1), 31, .1); nav.frame(.15);
  assert.equal(nav.state().itemFollow, 0, 'a tap moved the items');
  nav.down(centreOf(b, 1), 31, 2);
  for (let t = 2; t <= 2.9; t += 1 / 60) { nav.move(centreOf(b, 1), 31, t); nav.frame(t); }
  assert.ok(nav.state().itemFollow > .9, `a hold did not carry the items (${nav.state().itemFollow})`);
});

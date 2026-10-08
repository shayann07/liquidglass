import {test} from 'node:test';
import assert from 'node:assert/strict';
import CanvasKitInit from 'canvaskit-wasm';
import {createGlassScene, createGlassPainter, createCalmInteraction} from 'liquidglass-skia-preview';
import {createCalmInteraction as originalInteraction} from '../web/interaction.mjs';
import {mkdtemp, cp, rm, readFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve, join, dirname} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';

const kit = await CanvasKitInit();
function fixture(width = 240, height = 160) {
  const info = {width, height, colorType: kit.ColorType.RGBA_8888,
    alphaType: kit.AlphaType.Premul, colorSpace: kit.ColorSpace.SRGB};
  const bytes = new Uint8Array(width * height * 4);
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++)
    bytes.set([30 + x % 180, 25 + y % 180, (x + y) % 220, 255], (y * width + x) * 4);
  const image = kit.MakeImage(info, bytes, width * 4), surface = kit.MakeSurface(width, height);
  const canvas = surface.getCanvas();
  return {image, surface, canvas, read() { surface.flush(); return canvas.readPixels(0, 0, info); },
    close() { surface.delete(); image.delete(); }};
}

test('packaged runtime files work in isolation without a sibling web checkout', async () => {
  const base = fileURLToPath(new URL('.', import.meta.url));
  const manifest = JSON.parse(await readFile(new URL('package.json', import.meta.url), 'utf8'));
  const temporary = await mkdtemp(join(tmpdir(), 'liquidglass-package-'));
  const f = fixture();
  let scene;
  try {
    for (const file of [...manifest.files, 'package.json'])
      await cp(resolve(base, file), join(temporary, file), {recursive: true});
    for (const name of ['LICENSE', 'NOTICE'])
      assert.equal(await readFile(join(temporary, name), 'utf8'),
        await readFile(new URL(`../../${name}`, import.meta.url), 'utf8'), `${name} drifted from the project`);
    const standalone = await import(pathToFileURL(join(temporary, 'index.mjs')));
    scene = standalone.createGlassScene(kit, {width: 240, height: 160});
    scene.setSource(f.image); scene.addLens({x: 30, y: 30, width: 70});
    scene.draw(f.canvas, 0);
    assert(f.read().some((v, i) => i % 4 === 3 && v === 255), 'isolated runtime did not render');
    assert.equal(typeof standalone.createCalmInteraction, 'function');
  } finally {
    scene?.dispose(); f.close();
    // Only remove the unique directory allocated for this test, inside the resolved temp root.
    assert.equal(dirname(resolve(temporary)), resolve(tmpdir()));
    assert(resolve(temporary).startsWith(join(resolve(tmpdir()), 'liquidglass-package-')));
    await rm(temporary, {recursive: true, force: true});
  }
});

test('one scene matches the production painter in logical coordinates at both pixel ratios', () => {
  for (const pixelRatio of [1, 2]) {
    const f = fixture(240 * pixelRatio, 160 * pixelRatio);
    const scene = createGlassScene(kit, {width: 240, height: 160, pixelRatio});
    const painter = createGlassPainter(kit);
    try {
      scene.setSource(f.image); painter.setSource(f.image);
      scene.addLens({x: 10, y: 10, width: 60, magnification: 1.5});
      scene.addSurface({x: 20, y: 95, width: 190, height: 45, radius: [8, 14, 6, 12]}, {dark: true});
      assert.equal(scene.draw(f.canvas, 0), false);
      const actual = f.read();
      f.canvas.clear(kit.TRANSPARENT); f.canvas.drawImage(f.image, 0, 0);
      painter.drawLens(f.canvas, {x: 10 * pixelRatio, y: 10 * pixelRatio, width: 60 * pixelRatio, magnification: 1.5});
      painter.drawSurface(f.canvas, {x: 20 * pixelRatio, y: 95 * pixelRatio, width: 190 * pixelRatio,
        height: 45 * pixelRatio, radius: [8, 14, 6, 12].map(r => r * pixelRatio)}, {dark: true, density: pixelRatio});
      assert.deepEqual(actual, f.read(), `scene moved or changed material at ratio ${pixelRatio}`);
    } finally { scene.dispose(); painter.dispose(); f.close(); }
  }
});

test('surface input leaves layout and other glass stable, then exactly recovers on release', () => {
  const f = fixture(), scene = createGlassScene(kit, {width: 240, height: 160});
  try {
    scene.setSource(f.image);
    const lens = scene.addLens({x: 15, y: 12, width: 58});
    const card = scene.addSurface({x: 15, y: 95, width: 210, height: 45, radius: 16}, {dark: true});
    scene.draw(f.canvas, 0); const resting = f.read(), bounds = card.bounds;
    card.press(0, 10000, -10000);
    assert.equal(scene.draw(f.canvas, .1), true);
    scene.draw(f.canvas, 2); const pulled = f.read();
    assert.notDeepEqual(resting, pulled, 'surface did not react');
    assert.deepEqual(card.bounds, bounds, 'feedback changed layout');
    assert.deepEqual(resting.slice(0, 240 * 80 * 4), pulled.slice(0, 240 * 80 * 4), 'unrelated lens moved');
    assert.equal(card.feedback.held, true); assert.equal(lens.feedback.held, false);
    card.release(2); assert.equal(scene.draw(f.canvas, 5), false);
    assert.deepEqual(f.read(), resting, 'release left material changed');
    card.press(5, 1000, 1000); scene.draw(f.canvas, 5.5);
    scene.setReducedMotion(true, 5.5); assert.equal(scene.draw(f.canvas, 5.5), false);
    assert.deepEqual(f.read(), resting, 'reduced motion retained deformation');
    card.setMaterial({dark: false, reducedTransparency: 1}); scene.draw(f.canvas, 5.5);
    assert.notDeepEqual(f.read(), resting, 'appearance update did not reach the shared painter');
    assert.deepEqual(card.bounds, bounds, 'appearance update changed layout');
  } finally { scene.dispose(); f.close(); }
});

test('resize cancels stale gestures, failed geometry is atomic, and source/layer ownership is explicit', () => {
  const f = fixture(), scene = createGlassScene(kit, {width: 240, height: 160});
  const card = scene.addSurface({x: 10, y: 15, width: 100, height: 60, radius: [10, 11, 12, 13]});
  try {
    assert.throws(() => scene.draw(f.canvas, 0), /setSource/);
    scene.setSource(f.image); card.press(0); scene.draw(f.canvas, .4);
    assert.throws(() => card.setBounds({width: NaN}, .4), /bounds/);
    assert.equal(card.feedback.held, true);
    assert.throws(() => scene.resize({pixelRatio: 0}, .4), /pixelRatio/);
    assert.equal(card.feedback.held, true);
    const external = card.bounds; external.radius[0] = 99;
    assert.equal(card.bounds.radius[0], 10);
    card.setBounds({x: 20}, .4); assert.equal(card.feedback.held, false);
    card.press(.4, 500, 500); scene.resize({width: 220}, .5);
    assert.equal(card.feedback.held, false);
    scene.draw(f.canvas, .3); // queued stale frame must not rewind the shared clock
    assert.throws(() => card.press(.4), /monotonic/);
    card.remove(); card.remove();
    assert.throws(() => card.press(.6), /removed/);
    assert.equal(scene.draw(f.canvas, .6), false);
    scene.dispose(); scene.dispose();
    assert.equal(f.image.width(), 240, 'scene deleted the borrowed source');
    assert.throws(() => scene.draw(f.canvas, 1), /disposed/);
  } finally { scene.dispose(); f.close(); }
});

test('drawing restores the host canvas even when a material fails validation', () => {
  const f = fixture(), scene = createGlassScene(kit, {width: 240, height: 160});
  try {
    scene.setSource(f.image);
    const bad = scene.addSurface({x: 10, y: 20, width: 100, height: 60}, {tintAmount: NaN});
    const saved = f.canvas.getSaveCount();
    assert.throws(() => scene.draw(f.canvas, .5));
    assert.equal(f.canvas.getSaveCount(), saved, 'failed draw leaked a canvas transform');
    bad.remove(); assert.equal(scene.draw(f.canvas, .5), false);
  } finally { scene.dispose(); f.close(); }
});

test('source replacement reaches backdrop and glass, with optional backdrop drawing', () => {
  const f = fixture(), scene = createGlassScene(kit, {width: 240, height: 160});
  let replacement;
  try {
    scene.setSource(f.image); scene.addLens({x: 30, y: 30, width: 70});
    scene.addSurface({x: 120, y: 40, width: 100, height: 70}, {dark: true});
    scene.draw(f.canvas, 0); const old = f.read();
    f.canvas.clear(kit.RED); replacement = f.surface.makeImageSnapshot();
    scene.setSource(replacement); scene.draw(f.canvas, .1); const changed = f.read();
    assert.notDeepEqual(changed, old);
    assert.deepEqual(Array.from(changed.slice(0, 4)), [255, 0, 0, 255], 'background still uses the old source');
    assert.deepEqual(Array.from(changed.slice((65 * 240 + 65) * 4, (65 * 240 + 65) * 4 + 4)),
      [255, 0, 0, 255], 'lens still samples the old source');
    assert.throws(() => scene.setSource(null), /Source/);
    scene.draw(f.canvas, .2); assert.deepEqual(f.read(), changed, 'failed source update lost the valid source');
    f.canvas.clear(kit.TRANSPARENT); scene.draw(f.canvas, .3, {drawBackdrop: false});
    const overlays = f.read();
    assert.equal(overlays[3], 0, 'overlay-only draw repainted the backdrop');
    assert.equal(overlays[(65 * 240 + 65) * 4 + 3], 255, 'overlay-only draw lost glass');
  } finally { scene.dispose(); replacement?.delete(); f.close(); }
});

test('bundled package interaction preserves original timestamps and extreme feedback', () => {
  const options = {x: 10, y: 20, width: 220, height: 60, viewportWidth: 240, viewportHeight: 160};
  const original = originalInteraction(options), bundled = createCalmInteraction(options);
  for (const [method, args] of [['press', [0, 10000, -10000]], ['sample', [.2]],
    ['press', [.3, -10000, 10000]], ['sample', [.4]], ['release', [.4]], ['sample', [3]]])
    assert.deepEqual(bundled[method](...args), original[method](...args));
});

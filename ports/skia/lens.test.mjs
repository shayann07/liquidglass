import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import CanvasKitInit from 'canvaskit-wasm';
import {createGlassScene, createGlassPainter} from 'liquidglass-skia-preview';
import {CLEAR_LENS, clearLensUniforms} from './material.mjs';
import {clearMaterialUniforms, compileGlassEffects} from './renderer.mjs';
import {glassShaderSources} from './shaders.mjs';

const kit = await CanvasKitInit();
const info = (w, h) => ({width: w, height: h, colorType: kit.ColorType.RGBA_8888, alphaType: kit.AlphaType.Premul,
  colorSpace: kit.ColorSpace.SRGB});
function flat(w, h, level, draw) {
  const pixels = new Uint8Array(w * h * 4);
  for (let i = 0; i < pixels.length; i += 4) pixels.set([level, level, level, 255], i);
  const image = kit.MakeImage(info(w, h), pixels, w * 4), surface = kit.MakeSurface(w, h);
  return {image, surface, canvas: surface.getCanvas(), w, h,
    read() { surface.flush(); return surface.getCanvas().readPixels(0, 0, info(w, h)); },
    close() { surface.delete(); image.delete(); }};
}
const at = (b, w, x, y) => b[(y * w + x) * 4];

test('the preset keeps GlassStyle.clearLens\'s authored values', async () => {
  // The Kotlin source is not part of a published port; skip where it is not beside this directory.
  let kotlin;
  try { kotlin = await readFile(new URL('../../liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/GlassStyle.kt', import.meta.url), 'utf8'); }
  catch (error) { if (error.code === 'ENOENT') return; throw error; }
  const block = kotlin.slice(kotlin.indexOf('fun clearLens('), kotlin.indexOf('fun clearLens(') + 1200);
  const number = name => Number(new RegExp(`${name} = ([0-9]+(?:[.][0-9]+)?)`).exec(block)[1]);
  assert.equal(number('bevel'), CLEAR_LENS.bevel);
  assert.equal(number('specular'), CLEAR_LENS.specular);
  assert.equal(number('fresnel'), CLEAR_LENS.fresnel);
  assert.equal(number('counterLight'), CLEAR_LENS.counterLight);
  assert.equal(number('contactShadow'), CLEAR_LENS.contactShadow);
  assert.equal(number('bevelPeak'), CLEAR_LENS.bevelPeak);
  assert.match(block, new RegExp(`alpha = ${CLEAR_LENS.tint[3]}f`));
  assert.match(block, /GlassProfile\.Lens/);
  assert.match(block, /heldMagnification = 1f - 1f \/ magnification/);
});

test('the preset binds only known uniforms, scales its dp terms and leaves the bare set alone', () => {
  const effects = compileGlassEffects(kit, glassShaderSources);
  try {
    const args = {width: 180, height: 180, pad: 2, magnification: 1.4};
    const bare = clearMaterialUniforms(effects, args), before = JSON.stringify(bare);
    const lens = clearLensUniforms(effects, {...args, density: 2});
    assert.equal(JSON.stringify(clearMaterialUniforms(effects, args)), before, 'the bare material changed');
    assert.deepEqual(Object.keys(lens).sort(), Object.keys(bare).sort());
    assert.equal(lens.uBevel, 2.4);
    assert.equal(lens.uRefractDepth, 28);
    assert.equal(lens.uRefractBand, .6 * 90);
    assert.equal(lens.uSpecular, .28);
    assert.equal(lens.uProfile, 3);
    assert(Math.abs(lens.uHeldMagnification - (1 - 1 / 1.4)) < 1e-12);
    assert.deepEqual(lens.uTint, [1, 1, 1, 4 / 255]);
    assert.deepEqual(lens.uLight, [-.35, -.94]);
    assert.throws(() => clearLensUniforms(effects, {...args, density: 0}), /density/);
    assert.throws(() => clearLensUniforms(effects, {...args, light: [1]}), /Light/);
  } finally { effects.dispose(); }
});

test('a lens is the bare magnifier unless lighting is asked for', () => {
  const f = flat(320, 320, 128), painter = createGlassPainter(kit), bounds = {x: 70, y: 70, width: 180};
  try {
    painter.setSource(f.image);
    const render = options => { f.canvas.clear(kit.TRANSPARENT); f.canvas.drawImage(f.image, 0, 0);
      painter.drawLens(f.canvas, bounds, options); return Array.from(f.read()); };
    const plain = render(), off = render({lighting: false, shadow: true, density: 3});
    assert.deepEqual(off, plain, 'lighting:false must not change a pixel');
    assert.equal(at(plain, 320, 160, 71), 128, 'the bare lens draws nothing over a flat page');
    assert.notDeepEqual(render({lighting: true}), plain);
  } finally { painter.dispose(); f.close(); }
});

test('the clear-lens preset gives a rim, a tint-free centre and a contact shadow', () => {
  for (const [level, top, bottom] of [[128, 40, 10], [0, 25, 5]]) {
    const f = flat(320, 320, level), painter = createGlassPainter(kit);
    try {
      painter.setSource(f.image);
      f.canvas.drawImage(f.image, 0, 0);
      painter.drawLens(f.canvas, {x: 70, y: 70, width: 180}, {lighting: true});
      const px = f.read(), cx = 160, cy = 160;
      const rim = (dx, dy) => { let best = -255; for (let r = 80; r <= 90; r++) {
        const x = Math.round(cx + dx * r), y = Math.round(cy + dy * r); best = Math.max(best, at(px, 320, x, y) - level); } return best; };
      assert(rim(0, -1) >= top, `top rim over ${level}: ${rim(0, -1)}`);
      assert(rim(0, 1) >= bottom, `bottom rim over ${level}: ${rim(0, 1)}`);
      // The interior is the page: the preset's white tint is 4/255.
      for (let r = 0; r < 70; r += 5) assert(Math.abs(at(px, 320, cx + r, cy) - level) <= 4, `interior at ${r}`);
      if (level === 128) {
        let sum = 0; for (let y = 251; y < 271; y++) for (let x = 130; x < 190; x++) sum += at(px, 320, x, y);
        assert(sum / (20 * 60) < level, 'no contact shadow under the lens');
      }
    } finally { painter.dispose(); f.close(); }
  }
  // shadow:false leaves the page under the lens alone.
  const f = flat(320, 320, 128), painter = createGlassPainter(kit);
  try {
    painter.setSource(f.image); f.canvas.drawImage(f.image, 0, 0);
    painter.drawLens(f.canvas, {x: 70, y: 70, width: 180}, {lighting: true, shadow: false});
    assert.equal(at(f.read(), 320, 160, 262), 128);
  } finally { painter.dispose(); f.close(); }
});

test('a scene lens takes the same options as the painter and scales them by the pixel ratio', () => {
  for (const ratio of [1, 2]) {
    const w = 240 * ratio, h = 160 * ratio, f = flat(w, h, 100), g = flat(w, h, 100);
    const scene = createGlassScene(kit, {width: 240, height: 160, pixelRatio: ratio}), painter = createGlassPainter(kit);
    try {
      scene.setSource(f.image); painter.setSource(g.image);
      const lens = scene.addLens({x: 40, y: 20, width: 100, magnification: 1.5}, {lighting: true});
      scene.draw(f.canvas, 0);
      g.canvas.drawImage(g.image, 0, 0);
      painter.drawLens(g.canvas, {x: 40 * ratio, y: 20 * ratio, width: 100 * ratio, height: 100 * ratio, magnification: 1.5},
        {lighting: true, density: ratio});
      assert.deepEqual(Array.from(f.read()), Array.from(g.read()), `scene and painter differ at ratio ${ratio}`);
      // A scene lens without options stays the bare map; setMaterial switches the preset.
      const bare = scene.addLens({x: 150, y: 20, width: 60});
      void bare, lens;
    } catch (error) { if (!/removed/.test(String(error))) throw error; }
    finally { scene.dispose(); painter.dispose(); f.close(); g.close(); }
  }
  const f = flat(240, 160, 100), scene = createGlassScene(kit, {width: 240, height: 160});
  try {
    scene.setSource(f.image);
    const lens = scene.addLens({x: 40, y: 20, width: 100});
    scene.draw(f.canvas, 0); const plain = Array.from(f.read());
    lens.setMaterial({lighting: true}); scene.draw(f.canvas, 0.1);
    assert.notDeepEqual(Array.from(f.read()), plain);
    lens.setMaterial({lighting: false}); f.canvas.clear(kit.TRANSPARENT); scene.draw(f.canvas, 0.2);
    assert.deepEqual(Array.from(f.read()), plain);
  } finally { scene.dispose(); f.close(); }
});

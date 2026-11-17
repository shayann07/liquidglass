// Apache-2.0. LiquidGlass/material.js equals ports/skia's in-app material (the fixture the Flutter port
// is checked against, generated from ports/skia/material.mjs), and every uniform it sets is a member of
// the generated shaders' block.   node --test test/
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8');
// material.js is a QML JavaScript library (".pragma library"); run it as a plain script.
const sandbox = {module: {exports: {}}, Math};
vm.runInNewContext(read('../LiquidGlass/material.js').replace(/^\.pragma library\s*$/m, ''), sandbox);
const {uniforms} = sandbox.module.exports;
const fixture = JSON.parse(read('../../flutter/test/material_fixture.json'));
const frag = read('../LiquidGlass/shaders/liquidglass.frag');
const members = new Set([...frag.matchAll(/^\s+(?:float|vec2|vec3|vec4)\s+(\w+);/gm)].map(m => m[1]));

// Qt-only, as for Flutter: the backdrop is the live texture (unbounded here) and the wide tone is always on.
const qtOnly = new Set(['uBackdrop', 'uWideStrip']);

fixture.forEach((entry, i) => {
  test(`case ${i} matches the CanvasKit port's material`, () => {
    const c = entry.input, d = c.density ?? 1;
    const ours = uniforms({radius: c.radius, dark: c.dark, tintAmount: c.tintAmount, light: c.light, tint: c.tint ?? null,
      materialize: c.materialize ?? 1, reducedTransparency: c.reducedTransparency ?? 0,
      increasedContrast: c.increasedContrast ?? 0}, c.width * d, c.height * d, d);
    for (const [name, theirs] of Object.entries(entry.uniforms)) {
      assert.ok(members.has(name), `${name} is a member of the shader's uniform block`);
      if (qtOnly.has(name)) continue;
      const mine = ours[name] ?? theirs.map(() => 0);  // unset uniforms stay 0, as QML properties do
      assert.equal(mine.length, theirs.length, name);
      mine.forEach((v, j) => assert.ok(Math.abs(v - theirs[j]) <= 1e-6, `${name}[${j}]: ${v} vs ${theirs[j]}`));
    }
    for (const name of Object.keys(ours)) assert.ok(members.has(name), `${name} is set but not in the shader`);
    // QML skips absent keys, so a missing one keeps the previous material's value: all three are written every call.
    for (const name of ['uChromaGain', 'uChromaSlope', 'uChromaWeight']) assert.ok(ours[name], `${name} is always written`);
  });
});

// GlassEffect.qml sets only the keys it is handed and keeps the rest, so every key set at one tint is set at every
// tint: moving Tint from 50 to 0 or 100 must reset the lab's terms to the earlier fit's (GlassStyle.inApp).
test('every tint sets the same uniforms, with the earlier fit at the ends', () => {
  for (const dark of [true, false]) {
    const at = t => uniforms({radius: 20, dark, tintAmount: t}, 300, 120, 3);
    const keys = Object.keys(at(50)).sort();
    for (const t of [0, 1, 25, 75, 99, 100]) assert.deepEqual(Object.keys(at(t)).sort(), keys, `tint ${t}`);
    for (const t of [0, 100]) {
      const v = at(t);
      assert.deepEqual([v.uDetailLift[0], v.uToneCurve[0], v.uSoftDetail[0]], [0, 0, 0], `tint ${t} lab terms`);
      assert.equal(v.uChromaWeight[0], 0, `tint ${t} colour transfer`);
    }
  }
});

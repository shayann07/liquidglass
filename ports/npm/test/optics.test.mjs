import {test} from 'node:test';
import assert from 'node:assert/strict';
import {kotlin, haveKotlin, skslFunction} from './kotlin.mjs';
import {regularStyle, clearStyle} from '../material.mjs';
import {createFreeButton} from '../vendor/skia/interaction.mjs';
import {restSourceLegacy, bevelSlope, snellShift, smoothstep, sdRoundRect, gradRoundRect, cornerWeight,
  shapeFor, geometryAt, displacementAt, highlightAt, displacementMap, rimMap, decodeOffset} from '../optics.mjs';

const skip = haveKotlin ? false : 'the Kotlin library is not beside this package';
const near = (a, b, tol, label) => assert.ok(Math.abs(a - b) <= tol, `${label}: ${a} vs ${b}`);
const grid = (from, to, n) => Array.from({length: n + 1}, (_, i) => from + (to - from) * i / n);

test('scalar ports agree with the production SkSL text evaluated directly', {skip}, async () => {
  const rest = skslFunction(await kotlin('restMap'), 'restSourceLegacy');
  const shader = await kotlin('shader');
  const slope = skslFunction(shader, 'bevelSlope'), snell = skslFunction(shader, 'snellShift');
  for (const u of grid(0, 1, 400)) near(restSourceLegacy(u), rest(u), 1e-12, `restSourceLegacy(${u})`);
  for (const e of grid(0, 1, 200)) for (const p of [1.5, 2, 4]) near(bevelSlope(e, p), slope(e, p), 1e-9, `bevelSlope(${e}, ${p})`);
  for (const s of grid(0, 40, 200)) for (const ior of [1, 1.33, 1.5, 2]) near(snellShift(s, ior), snell(s, ior), 1e-12, `snellShift(${s})`);
  // The shapes and maps the ports implement are still the shipped ones.
  const sourceMap = await kotlin('sourceMap');
  assert.match(sourceMap, /: \(u \+ 0\.06 \* v \* v\);/, 'straight-run rest offset');
  assert.match(sourceMap, /float soft = max\(radius \* 0\.2, 1\.0\);\n    return smoothstep\(-soft, 0\.0, min\(q\.x, q\.y\)\);/, 'cornerWeight');
  assert.match(await kotlin('normal'), /return gradRoundRect\(p, halfSize, r, power, mix\(1\.5, 1\.0, step\(0\.5, profile\)\)\);/);
  assert.match(shader, /return min\(max\(q\.x, q\.y\), 0\.0\) \+ lnNorm\(max\(q, float2\(0\.0\)\), uCornerPower\) - radius;/);
  assert.match(shader, /shift = mix\(max\(shift, 0\.0\), shift, max\(heldFamily, capLens\)\) \* mat \* axisFade \* smoothstep\(0\.0, 1\.5, depth\);/);
  assert.match(shader, /float edge = smoothstep\(2\.0, 0\.0, depth\);/);
  assert.match(shader, /float glowReach = \(uHeldGlowReach > 0\.0\) \? mix\(max\(uBevel, 1\.0\), uHeldGlowReach, heldAmt\) : max\(uBevel, 1\.0\);/);
});

test('signed distance, normals and corner weight of a rounded rectangle', () => {
  near(sdRoundRect(0, 0, 150, 28, 28), -28, 1e-12, 'capsule centre');
  near(sdRoundRect(150, 0, 150, 28, 28), 0, 1e-12, 'capsule end');
  near(sdRoundRect(0, -28, 150, 28, 28), 0, 1e-12, 'capsule top');
  const n1 = gradRoundRect(0, -20, 150, 28, 28, 1), n2 = gradRoundRect(10, -27, 150, 28, 28, 1);
  near(n1[0], 0, 0, 'run normal x'); near(n1[1], -1, 0, 'run normal y');
  near(n2[0], 0, 0, 'run normal x'); near(n2[1], -1, 0, 'run normal y');
  const [nx, ny] = gradRoundRect(140, 20, 150, 28, 28, 1);
  near(Math.hypot(nx, ny), 1, 1e-12, 'unit normal'); assert.ok(nx > 0 && ny > 0);
  near(cornerWeight(0, -27, 150, 28, 28), 0, 1e-12, 'straight run');
  near(cornerWeight(145, 0, 150, 28, 28), 1, 1e-12, 'cap');
  near(smoothstep(1, 0.75, 0.9), 0.352, 1e-12, 'reversed smoothstep');
});

test('Regular samples inward only, from the rest map; identity deep inside and outside', () => {
  const style = regularStyle(false), shape = shapeFor(style, {width: 300, height: 56, radius: 28});
  assert.equal(shape.band, 0.6 * 28);
  for (const v of displacementAt(shape, 0, 0)) near(v, 0, 0, 'centre');
  for (const [x, y] of [[0, -27], [149, 0], [140, 20], [-140, -20], [0, 27]]) {
    const g = geometryAt(shape, x, y), [dx, dy] = displacementAt(shape, x, y);
    assert.ok(dx * g.n[0] + dy * g.n[1] <= 1e-12, `inward at ${x},${y}`);
  }
  // At the cap's middle (corner weight 1), 2 px in: u = 2 / 16.8, rest map 0.55 + 0.93 (0.14 - u).
  const u = 2 / 16.8, expected = (0.55 + 0.93 * (0.14 - u)) * 16.8 - 2;
  const [dx] = displacementAt(shape, 148, 0);
  near(-dx, expected * smoothstep(0, 1.5, 2), 1e-9, 'cap shift');
  // Straight run, 2 px in: u + 0.06 (1 - u)^2, a shallow inward offset.
  const [, dy] = displacementAt(shape, 0, -26);
  near(dy, (u + 0.06 * (1 - u) ** 2) * 16.8 - 2, 1e-9, 'run shift');
});

test('Clear bends outward by the Snell bevel, scaled by size as the legacy shader does', () => {
  const style = clearStyle(), small = shapeFor(style, {width: 300, height: 56, radius: 28});
  assert.equal(small.band, 28); assert.equal(small.scale, 0);
  const g = geometryAt(small, 0, -26), [dx, dy] = displacementAt(small, 0, -26);
  assert.ok(dx * g.n[0] + dy * g.n[1] > 0, 'samples outward');
  // push = n * bend * lens, lens = 16 dp x mix(0.85, 1.25, scale): recompute at a point of a large panel.
  const large = shapeFor(style, {width: 400, height: 400, radius: 28});
  assert.equal(large.scale, 1);
  const at = geometryAt(large, 100, -198), [lx, ly] = displacementAt(large, 100, -198);
  let bend = snellShift(bevelSlope(at.e, 2), 1.5) / snellShift(bevelSlope(0, 2), 1.5);
  bend *= smoothstep(1, 0.75, at.e) * at.axisFade * smoothstep(0, 1.5, at.depth);
  near(Math.hypot(lx, ly), Math.min(1, bend) * 16 * 1.25, 1e-9, 'lens depth at scale 1');
});

test('displacement map pixels are deterministic at known points', () => {
  const style = regularStyle(false);
  const map = displacementMap(style, {width: 300, height: 56, radius: 28, pad: 32});
  assert.equal(map.width, 364); assert.equal(map.height, 120);
  assert.equal(map.scale, 20); near(map.maxShift, 8.5802, 1e-3, 'maxShift');
  const px = (i, j) => Array.from(map.data.slice(4 * (j * map.width + i), 4 * (j * map.width + i) + 4));
  // Pad and interior: neutral (127.5 is not representable; 128 means +scale/510 = 0.039 px).
  assert.deepEqual(px(5, 5), [128, 128, 0, 255]);
  assert.deepEqual(px(182, 60), [128, 128, 0, 255]);
  // Left cap, on the centre line, 2.5 px inside: pulled inward (to the right): R above neutral.
  const [r, g] = px(32 + 2, 60);
  const [dx, dy] = displacementAt(shapeFor(style, {width: 300, height: 56, radius: 28}), -150 + 2.5, 0.5);
  assert.equal(r, Math.round(255 * (0.5 + dx / 20)));
  assert.equal(g, Math.round(255 * (0.5 + dy / 20)));
  assert.deepEqual([r, g], [213, 126]); // snapshot: 6.7 px inward at the cap
  near(decodeOffset(r, 20), dx, 20 / 255, 'decodes within one step');
  assert.ok(r > 128);
  // Mirror symmetry of the field.
  for (const [i, j] of [[34, 40], [60, 33], [100, 86]]) {
    const [r1, g1] = px(i, j), [r2, g2] = px(map.width - 1 - i, map.height - 1 - j);
    assert.ok(Math.abs((r1 - 127.5) + (r2 - 127.5)) <= 1 && Math.abs((g1 - 127.5) + (g2 - 127.5)) <= 1, `${i},${j}`);
  }
});

test('Clear map carries the crisp-rim weight (1 - e)^2 in blue', () => {
  const map = displacementMap(clearStyle(), {width: 200, height: 120, radius: 24, pad: 32});
  const blue = (i, j) => map.data[4 * (j * map.width + i) + 2];
  assert.equal(blue(5, 5), 0); // pad
  assert.equal(blue(132, 92), 0); // centre: deeper than the 28 px band
  const edge = blue(32 + 100, 32 + 1); // 1.5 px in from the top
  near(edge, Math.round(255 * (1 - 1.5 / 28) ** 2), 0, 'near the top edge');
  assert.ok(map.maxShift > 5, 'Clear refracts');
});

test('rim map: the measured edge light peaks on the lit edge, both edges lit, sides dark', () => {
  const style = regularStyle(false);
  const rim = rimMap(style, {width: 300, height: 56, radius: 28, density: 1});
  const alpha = (i, j) => rim.data[4 * (j * rim.width + i) + 3];
  const top = alpha(150, 0), bottom = alpha(150, 55), inside = alpha(150, 28);
  assert.equal(inside, 0);
  assert.ok(top > 30 && bottom > 30, `top ${top} bottom ${bottom}`);
  // Counter-light 1: the bottom edge is lit almost as much as the top (light is not straight down).
  near(bottom / top, 1, 0.1, 'top and bottom alike');
  assert.ok(alpha(0, 28) < top / 4, 'the cap end, facing sideways, stays dark');
  // The exact value at the top-centre pixel, from the ported formula.
  const shape = shapeFor(style, {width: 300, height: 56, radius: 28});
  const g = geometryAt(shape, 0.5, -27.5);
  assert.equal(top, Math.round(Math.min(1, highlightAt(shape, 0.5, -27.5, g)) * (1 - smoothstep(-0.75, 0.75, g.d)) * 255));
  // Density scales the dp bevel: at 3x the rim is three device px wider.
  const dense = rimMap(style, {width: 300, height: 56, radius: 28, density: 3});
  assert.equal(dense.width, 900); assert.equal(dense.height, 168);
  // At 3 px per dp the inApp edge is a bead: dark-ish outermost pixel, peak one pixel in, then falling
  // (GlassStyle.inApp's note: +60 one pixel in, then +40, +22, +10; the model gives 53, 34, 13).
  const column = [0, 1, 2, 3].map(j => dense.data[4 * (j * dense.width + 450) + 3]);
  assert.ok(column[1] > column[0] && column[1] > column[2] && column[2] > column[3], column.join(' '));
});

test('free button cardinal angle snapping uses ties-to-even', () => {
  const btn = createFreeButton({size: 186, density: 3});
  btn.down(0);
  // dx = -50, dy = -50 -> atan2(-50, -50) = -135 degrees.
  // With ties-to-even (-135 / 90 = -1.5 -> -2), anchor and angle snap to -180 degrees.
  btn.move(0.1, -50, -50);
  const sample = btn.sample(0.2);
  assert.equal(sample.angle, -180);
});

/** Apache-2.0. Generate the Unity package's HLSL from the production SkSL; never a second implementation.
 *
 *   node tool/generate_shader.mjs           write Runtime/Shaders/LiquidGlassMaterial.hlsl and Runtime/GlassUniforms.g.cs
 *   node tool/generate_shader.mjs --check   fail if either is stale
 *
 * The source is ports/skia/shaders/material.sksl, the export of the Compose library's material shader.
 * SkSL is close to HLSL already (float2, half4, saturate). The translation is mechanical:
 *
 * - half types become float types (Unity's desktop and console compilers treat half as float anyway);
 * - mix, fract and inversesqrt become lerp, frac and rsqrt;
 * - a vector constructor with one argument, `float3(x)`, becomes the cast `((float3)(x))`: HLSL has no
 *   splat constructor, and the cast means what GLSL's constructor means whether x is a scalar (splat)
 *   or a vector (copy or truncation);
 * - `content.eval(p)` and `field.eval(p)` call sampleInput(p) and sampleField(p), which the including
 *   shader defines before the include (the Built-in, URP and check harness shaders each read the
 *   backdrop their own way);
 * - `main(coord)` becomes `glassMain(coord)`, called with the padded layer's pixel;
 * - `wideAt`, the wide tone kernel, becomes thirteen Gaussian taps of the same sigma, as in the Flutter
 *   and Qt ports, because one backdrop texture is all a Unity pass has.
 */
import {readFile, writeFile, mkdir} from 'node:fs/promises';

const here = path => new URL(path, import.meta.url);
const source = (await readFile(here('../../skia/shaders/material.sksl'), 'utf8')).replace(/\r\n/g, '\n');

function replaceOnce(text, pattern, replacement, what) {
  const matches = text.match(new RegExp(pattern.source, pattern.flags.includes('g') ? pattern.flags : pattern.flags + 'g'));
  if (!matches || matches.length !== 1) throw new Error(`Shader contract changed: ${what} (${matches?.length ?? 0} matches)`);
  return text.replace(pattern, replacement);
}

let body = source;
body = replaceOnce(body, /^uniform shader content;.*\n/m, '', 'the content child');
body = replaceOnce(body, /^uniform shader field;.*\n/m, '', 'the field child');
body = replaceOnce(body, /half3 wideAt\(float2 p\) \{\n(?:.*\n)*?\}\n/m, `half3 wideAt(float2 p) {
    // Unity: thirteen taps of a Gaussian with the wide kernel's sigma (see generate_shader.mjs).
    float s = max(uWideKernel, 1.0);
    half3 sum = backdropAt(p);
    float w1 = 0.6065, w2 = 0.1353;
    for (int i = 0; i < 6; i++) {
        float a = float(i) * 1.0471976;
        float b = a + 0.5235988;
        sum += backdropAt(p + float2(cos(a), sin(a)) * s) * half(w1);
        sum += backdropAt(p + float2(cos(b), sin(b)) * (2.0 * s)) * half(w2);
    }
    return sum / half(1.0 + 6.0 * (w1 + w2));
}
`, 'wideAt');
body = body.replace(/\bcontent\.eval\(/g, 'sampleInput(').replace(/\bfield\.eval\(/g, 'sampleField(');
if (/\b(content|field)\.eval\b/.test(body) || /\buniform shader\b/.test(body)) throw new Error('Unhandled shader child');
body = replaceOnce(body, /^half4 main\(float2 coord\) \{/m, 'half4 glassMain(float2 coord) {', 'main');
for (const n of ['', '2', '3', '4']) body = body.replace(new RegExp(`\\bhalf${n}\\b`, 'g'), `float${n}`);
for (const [from, to] of [['mix', 'lerp'], ['fract', 'frac'], ['inversesqrt', 'rsqrt']])
  body = body.replace(new RegExp(`\\b${from}\\(`, 'g'), `${to}(`);
// SkSL identifiers that are HLSL keywords (geometry-shader primitives, interpolation modifiers, ...).
const hlslKeywords = ['line', 'point', 'triangle', 'lineadj', 'triangleadj', 'sample', 'linear', 'centroid',
  'noperspective', 'nointerpolation', 'precise', 'shared', 'groupshared', 'snorm', 'unorm', 'vector', 'matrix',
  'pass', 'technique', 'compile', 'string', 'sampler', 'register', 'packoffset'];
const keyword = new RegExp(`\\b(${hlslKeywords.join('|')})\\b`, 'g');
// Code only: the split keeps comments (odd indices) and their words as they are.
body = body.split(/(\/\*[\s\S]*?\*\/|\/\/[^\n]*)/).map((part, i) => i % 2 ? part : part.replace(keyword, 'lg_$1')).join('');
for (const unsupported of ['mod', 'atan', 'dFdx', 'dFdy', 'fwidth', 'float2x2', 'float3x3', 'texture', 'lessThan', 'greaterThan'])
  if (new RegExp(`\\b${unsupported}\\b`).test(body)) throw new Error(`Translate ${unsupported} before generating`);

/** `float3(x)` with one top-level argument becomes `((float3)(x))`. */
function castSingleArgumentConstructors(text) {
  const starts = [...text.matchAll(/\bfloat[234]\(/g)].map(m => m.index).reverse();
  for (const start of starts) {
    const open = text.indexOf('(', start);
    let depth = 0, commas = 0, close = -1;
    for (let i = open; i < text.length; i++) {
      const ch = text[i];
      if (ch === '(') depth++;
      else if (ch === ')') { if (--depth === 0) { close = i; break; } }
      else if (ch === ',' && depth === 1) commas++;
    }
    if (close < 0) throw new Error(`Unbalanced constructor at ${start}`);
    if (commas === 0) {
      const type = text.slice(start, open);
      text = `${text.slice(0, start)}((${type})(${text.slice(open + 1, close)}))${text.slice(close + 1)}`;
    }
  }
  return text;
}
body = castSingleArgumentConstructors(body);

const uniforms = [...body.matchAll(/^uniform\s+(float|float2|float3|float4)\s+(\w+)\s*;.*$/gm)]
  .map(([, type, name]) => ({type, name, size: {float: 1, float2: 2, float3: 3, float4: 4}[type]}));
if (uniforms.length < 60) throw new Error(`Expected the material's uniforms, found ${uniforms.length}`);
body = body.replace(/^uniform\s+(float|float2|float3|float4)\s+(\w+)\s*;(.*)$/gm, '$1 $2;$3');
if (/^uniform\b/m.test(body)) throw new Error('A uniform of an unhandled type remains');

const hlsl = `// Apache-2.0. Generated by ports/unity/tool/generate_shader.mjs from ports/skia/shaders/material.sksl.
// Do not edit: \`node tool/generate_shader.mjs --check\` fails when this drifts from the source.
//
// Include it after defining:
//   float4 sampleInput(float2 p)   the backdrop at the padded layer's pixel p (premultiplied, opaque)
//   float4 sampleField(float2 p)   the sampled distance field (unread while uShapeKind is 0, analytic)
// then return glassMain(layerPixel) from the fragment shader. GlassSurface.cs sets the uniforms.
#ifndef LIQUIDGLASS_MATERIAL_INCLUDED
#define LIQUIDGLASS_MATERIAL_INCLUDED

${body.trimEnd()}

#endif // LIQUIDGLASS_MATERIAL_INCLUDED
`;

const cs = `// Apache-2.0. Generated by ports/unity/tool/generate_shader.mjs; do not edit.
namespace LiquidGlass
{
    /// <summary>The material's uniforms in LiquidGlassMaterial.hlsl: name and float count.</summary>
    public static class GlassUniforms
    {
        public static readonly (string Name, int Size)[] All =
        {
${uniforms.map(u => `            ("${u.name}", ${u.size}),`).join('\n')}
        };
    }
}
`;

const outputs = [
  ['../com.shayxo.liquidglass/Runtime/Shaders/LiquidGlassMaterial.hlsl', hlsl],
  ['../com.shayxo.liquidglass/Runtime/GlassUniforms.g.cs', cs],
];
if (process.argv.includes('--check')) {
  const stale = [];
  for (const [path, text] of outputs) {
    const actual = await readFile(here(path), 'utf8').catch(() => null);
    if (actual?.replace(/\r\n/g, '\n') !== text) stale.push(path);
  }
  if (stale.length) throw new Error(`Generated files are stale (${stale.join(', ')}). Run node tool/generate_shader.mjs.`);
  console.log(`LiquidGlassMaterial.hlsl and GlassUniforms.g.cs match material.sksl (${uniforms.length} uniforms).`);
} else {
  for (const [path, text] of outputs) {
    await mkdir(new URL('.', here(path)), {recursive: true});
    await writeFile(here(path), text);
  }
  console.log(`Generated LiquidGlassMaterial.hlsl and GlassUniforms.g.cs (${uniforms.length} material uniforms).`);
}

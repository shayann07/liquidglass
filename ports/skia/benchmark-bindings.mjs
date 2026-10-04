/** Apache-2.0. Optional CPU binding microbenchmark; never a frame-rate benchmark or CI gate. */
import {readFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import CanvasKitInit from 'canvaskit-wasm';
import * as current from './renderer.mjs';

const baseline=process.argv[2]?await import(pathToFileURL(process.argv[2]).href):null;
const kit=await CanvasKitInit();
const sources=Object.fromEntries(await Promise.all(['material','content','endpoint'].map(async name=>
  [name,await readFile(new URL(`shaders/${name}.sksl`,import.meta.url),'utf8')])));
const image=kit.MakeImage({width:1,height:1,colorType:kit.ColorType.RGBA_8888,
  alphaType:kit.AlphaType.Premul,colorSpace:kit.ColorSpace.SRGB},new Uint8Array([128,128,128,255]),4);
const child=image.makeShaderOptions(kit.TileMode.Clamp,kit.TileMode.Clamp,kit.FilterMode.Linear,kit.MipmapMode.None);
const entries=[];
try {
  for(const [name,module] of [...(baseline?[['baseline',baseline]]:[]),['current',current]]) {
    const effects=module.compileGlassEffects(kit,sources);
    entries.push({name,effects,values:module.clearMaterialUniforms(effects,{width:160,height:160}),milliseconds:[]});
  }
  function batch(entry) {
    const start=performance.now();
    for(let i=0;i<2000;i++) {
      const shader=entry.effects.shader('material',entry.values,{content:child,field:child});shader.delete();
    }
    return performance.now()-start;
  }
  for(const entry of entries)batch(entry);
  for(let round=0;round<7;round++) {
    for(const entry of round%2?[...entries].reverse():entries)entry.milliseconds.push(batch(entry));
  }
  console.log(JSON.stringify({
    scope:'CPU uniform binding and native shader creation/deletion only; no draw, GPU submission, presented frames or FPS',
    node:process.version,platform:process.platform,bindingsPerBatch:2000,warmupBatches:1,
    measuredBatches:7,alternatingOrder:!!baseline,
    results:entries.map(({name,milliseconds})=>({name,milliseconds,medianMs:[...milliseconds].sort((a,b)=>a-b)[3]})),
  },null,2));
} finally {for(const entry of entries)entry.effects.dispose();child.delete();image.delete();}

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile, mkdir, writeFile} from 'node:fs/promises';
import CanvasKitInit from 'canvaskit-wasm';
import {compileGlassEffects, clearMaterialUniforms} from './renderer.mjs';

const kit = await CanvasKitInit();
const sources = Object.fromEntries(await Promise.all(['material','content','endpoint'].map(async name =>
  [name, await readFile(new URL(`shaders/${name}.sksl`, import.meta.url), 'utf8')])));

test('production material, ink and aperture compile without Compose', () => {
  const effects = compileGlassEffects(kit,sources);
  assert.ok(effects.layout('material').some(u=>u.name==='uHeldMagnification'));
  assert.ok(effects.layout('content').some(u=>u.name==='uHeldInk'));
  assert.ok(effects.layout('endpoint').some(u=>u.name==='uPoseD'));
  effects.dispose();effects.dispose();assert.throws(()=>effects.layout('material'), /disposed/);
});

test('actual Skia lens keeps ramp continuity, zoom, alpha and named binding validation', async () => {
  const effects = compileGlassEffects(kit,sources), w=224, h=224;
  const info = {width:w,height:h,colorType:kit.ColorType.RGBA_8888,
    alphaType:kit.AlphaType.Premul,colorSpace:kit.ColorSpace.SRGB};
  const pixels = new Uint8Array(w*h*4);
  for(let y=0;y<h;y++)for(let x=0;x<w;x++)pixels.set([x,y,64,255],(y*w+x)*4);
  const image = kit.MakeImage(info,pixels,w*4);
  assert.ok(image);
  const source = image.makeShaderOptions(kit.TileMode.Clamp,kit.TileMode.Clamp,kit.FilterMode.Linear,kit.MipmapMode.None);
  const surface = kit.MakeSurface(w,h), paint = new kit.Paint();
  assert.ok(surface);
  try {
    for(const magnification of [1,1.25,1.6,2.5]) {
      const uniforms=clearMaterialUniforms(effects,{width:160,height:160,magnification});
      const shader=effects.shader('material',uniforms,{content:source,field:source});
      try {
        paint.setShader(shader);surface.getCanvas().clear(kit.TRANSPARENT);surface.getCanvas().drawPaint(paint);surface.flush();
        const actual=surface.getCanvas().readPixels(0,0,info);
        assert.ok(actual);
        const channel=(x,y,c=0)=>actual[(y*w+x)*4+c];
        assert.equal(channel(0,0,3),0,'padding should be transparent');
        assert.equal(channel(112,112,3),255,'centre should be opaque');
        assert.ok(Math.abs(channel(112,112,1)-112)<=1,'source vertically flipped');
        for(let x=35;x<188;x++)assert.ok(channel(x+1,112)-channel(x,112)>=-1,'reversed inner source map');
        if(magnification===1.25) {
          assert.ok((channel(117,112)-channel(107,112))/10>=.7);
          assert.ok((channel(117,112)-channel(107,112))/10<=.9);
          await mkdir('build',{recursive:true});
          const snapshot=surface.makeImageSnapshot();
          try { await writeFile('build/skia-lens.png',snapshot.encodeToBytes()); } finally { snapshot.delete(); }
        }
      } finally { paint.setShader(null);shader.delete(); }
    }
    const valid=clearMaterialUniforms(effects,{width:160,height:160});
    assert.throws(()=>effects.shader('material',{...valid,uSize:[160,NaN]},{content:source,field:source}),/finite/);
    assert.throws(()=>effects.shader('material',{...valid,typo:1},{content:source,field:source}),/Unknown uniform/);
    assert.throws(()=>effects.shader('material',valid,{content:source}),/Missing shader child/);
  } finally { paint.delete();surface.delete();source.delete();image.delete();effects.dispose(); }
});

test('all four production profiles agree with independently rendered JVM pixels', async () => {
  const effects=compileGlassEffects(kit,sources), size=128;
  const info={width:size,height:size,colorType:kit.ColorType.RGBA_8888,
    alphaType:kit.AlphaType.Premul,colorSpace:kit.ColorSpace.SRGB};
  const pixels=new Uint8Array(size*size*4);
  for(let y=0;y<size;y++)for(let x=0;x<size;x++)pixels.set([x,y,64,255],(y*size+x)*4);
  const image=kit.MakeImage(info,pixels,size*4);
  const source=image.makeShaderOptions(kit.TileMode.Clamp,kit.TileMode.Clamp,kit.FilterMode.Linear,kit.MipmapMode.None);
  const surface=kit.MakeSurface(size,size),paint=new kit.Paint();
  try {
    for(let profile=0;profile<4;profile++) {
      const uniforms={...clearMaterialUniforms(effects,{width:64,height:64}),uProfile:profile};
      const shader=effects.shader('material',uniforms,{content:source,field:source});
      try {
        paint.setShader(shader);surface.getCanvas().clear(kit.TRANSPARENT);surface.getCanvas().drawPaint(paint);surface.flush();
        const actual=surface.getCanvas().readPixels(0,0,info);
        const expected=await readFile(new URL(`fixtures/profile-${profile}.rgba`,import.meta.url));
        assert.equal(actual.length,expected.length);
        let max=0,sum=0;
        for(let i=0;i<actual.length;i++){const delta=Math.abs(actual[i]-expected[i]);max=Math.max(max,delta);sum+=delta;}
        console.log(`Skia profile ${profile}: max channel error ${max}, mean ${sum/actual.length}`);
        assert.ok(max<=3,`profile ${profile} differs from production JVM pixels by ${max}`);
      } finally {paint.setShader(null);shader.delete();}
    }
  } finally {paint.delete();surface.delete();source.delete();image.delete();effects.dispose();}
});

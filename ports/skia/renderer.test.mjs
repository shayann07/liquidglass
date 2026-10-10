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
  const layout=effects.layout('material'),expected=structuredClone(layout);
  layout[0].name='corrupted';layout[0].slot=999999;layout.pop();
  assert.deepEqual(effects.layout('material'),expected,'public layout inspection corrupted later bindings');
  effects.dispose();effects.dispose();assert.throws(()=>effects.layout('material'), /disposed/);
});

test('packed endpoints meet one-level composition error and never expose the ink strip', () => {
  const effects=compileGlassEffects(kit,sources), width=100, height=60, pad=16;
  const w=width+2*pad, h=height+2*pad;
  const info={width:w,height:2*h,colorType:kit.ColorType.RGBA_8888,
    alphaType:kit.AlphaType.Premul,colorSpace:kit.ColorSpace.SRGB};
  const pixels=new Uint8Array(w*h*2*4);
  for(let y=0;y<h;y++)for(let x=0;x<w;x++) {
    const i=(y*w+x)*4, j=i+w*h*4, a=(x*13+y*7)%256;
    pixels.set([(x*19)%256,(y*23)%256,(x*7+y*11)%256,255],i);
    pixels.set([Math.floor(250*a/255),Math.floor(30*a/255),Math.floor(110*a/255),a],j);
  }
  const image=kit.MakeImage(info,pixels,w*4);
  const input=image.makeShaderOptions(kit.TileMode.Clamp,kit.TileMode.Clamp,kit.FilterMode.Linear,kit.MipmapMode.None);
  const inkImage=kit.MakeImage({...info,height:h},pixels.subarray(w*h*4),w*4);
  const ink=inkImage.makeShaderOptions(kit.TileMode.Clamp,kit.TileMode.Clamp,kit.FilterMode.Linear,kit.MipmapMode.None);
  const surface=kit.MakeSurface(w,2*h), paint=new kit.Paint();
  let worst=0, partial=0;
  try {
    for(const shape of [
      {},
      {uBodyKind:1,uBody:[-22,24,21,28],uBodyY:4},
      {uBodyKind:2,uPoseC:[0,3,.002,0],uPoseD:[23,24,1,12]},
      // Coverage would otherwise enter the lower storage half of this deliberately shifted body.
      {uBodyKind:1,uBody:[-20,20,29,29],uBodyY:47},
    ]) {
      const material=clearMaterialUniforms(effects,{width,height,pad});
      const uniforms=Object.fromEntries(effects.layout('endpoint').map(u=>[u.name,material[u.name]??0]));
      Object.assign(uniforms,{uInkStrip:h},shape);
      const shader=effects.shader('endpoint',uniforms,{endpoint:input,ink,field:input});
      try {
        paint.setShader(shader);surface.getCanvas().clear(kit.TRANSPARENT);
        surface.getCanvas().drawPaint(paint);surface.flush();
        const actual=surface.getCanvas().readPixels(0,0,info);
        assert.ok(actual);
        for(let i=0;i<w*h*4;i+=4) {
          const m=actual[i+3]/255, j=i+w*h*4, a=pixels[j+3]/255;
          if(m>0&&m<1)partial++;
          for(let c=0;c<3;c++) {
            // Independent double-precision source-over equation on declared premultiplied inputs.
            const expected=m*(pixels[j+c]+(1-a)*pixels[i+c]);
            worst=Math.max(worst,Math.abs(actual[i+c]-expected));
          }
        }
        assert.ok(actual.subarray(w*h*4).every(v=>v===0),'ink storage leaked into visible output');
      } finally {paint.setShader(null);shader.delete();}
    }
    assert.ok(partial>500,`insufficient fractional coverage: ${partial}`);
    assert.ok(worst<=1,`packed endpoint error ${worst} exceeds one level`);
    console.log(`PACKED_ENDPOINT worst=${worst.toFixed(4)} partial=${partial}`);
  } finally {paint.delete();surface.delete();ink.delete();inkImage.delete();input.delete();image.delete();effects.dispose();}
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

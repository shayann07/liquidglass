import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile, mkdir, writeFile} from 'node:fs/promises';
import CanvasKitInit from 'canvaskit-wasm';
import {createGlassPainter} from './painter.mjs';
import {createCalmInteraction} from '../web/interaction.mjs';
import {createBackdropSampler} from './backdrop.mjs';
import {inAppMaterialUniforms} from './material.mjs';
import {compileGlassEffects} from './renderer.mjs';

const kit = await CanvasKitInit();
const sources = Object.fromEntries(await Promise.all(['material','content','endpoint'].map(async name =>
  [name, await readFile(new URL(`shaders/${name}.sksl`, import.meta.url), 'utf8')])));

test('in-app surface parameters agree with the actual Kotlin factory across both appearances', async () => {
  const fixtures=JSON.parse(await readFile(new URL('fixtures/in-app-material.json',import.meta.url),'utf8'));
  const effects=compileGlassEffects(kit,sources);
  try {
    for(const {dark,tintAmount,values} of fixtures) {
      const actual=inAppMaterialUniforms(effects,{width:240,height:80,dark,tintAmount});
      for(const [key,value] of Object.entries(values)) {
        const expected=Array.isArray(value)?value:[value],got=Array.isArray(actual[key])?actual[key]:[actual[key]];
        expected.forEach((x,i)=>assert(Math.abs(x-got[i])<=1e-6,`${key}[${i}] dark${dark} tint${tintAmount}: ${got[i]} vs Kotlin${x}`));
      }
    }
  } finally {effects.dispose();}
});

test('wide-tone samples preserve global page coordinates outside a panels local origin', () => {
  const w=240,h=180,info={width:w,height:h,colorType:kit.ColorType.RGBA_8888,
    alphaType:kit.AlphaType.Premul,colorSpace:kit.ColorSpace.SRGB};
  const pixels=new Uint8Array(w*h*4);
  for(let y=0;y<h;y++)for(let x=0;x<w;x++)pixels.set([x,y,64,255],(y*w+x)*4);
  const image=kit.MakeImage(info,pixels,w*4),surface=kit.MakeSurface(1,1),sampler=createBackdropSampler(kit);
  const probe=kit.RuntimeEffect.Make('uniform shader source;uniform float2 point;half4 main(float2 p){return source.eval(point);}');
  const paint=new kit.Paint(),canvas=surface.getCanvas();
  let allocated=0;
  const allocate=canvas.makeSurface.bind(canvas);
  canvas.makeSurface=info=>{allocated++;return allocate(info);};
  try {
    for(const [ox,oy] of [[80,80],[-10,-10],[100,30]]) {
      const host=surface.getCanvas();
      host.makeSurface=canvas.makeSurface;
      const sampled=sampler.shader(host,image,ox,oy,10);
      try {
        for(const [x,y] of [[100,40],[140,120]]) {
          const shader=probe.makeShaderWithChildren(new Float32Array([(x-ox)/4,sampled.strip+(y-oy)/4]),[sampled.shader]);
          try {
            paint.setShader(shader);canvas.drawPaint(paint);surface.flush();
            const p=canvas.readPixels(0,0,{...info,width:1,height:1});
            // Gaussian blur preserves a linear ramp away from source edges.
            assert(Math.abs(p[0]-x)<=2&&Math.abs(p[1]-y)<=2,`wide sample moved: origin${ox},${oy}, wanted${x},${y}, got${p[0]},${p[1]}`);
          } finally {paint.setShader(null);shader.delete();}
        }
      } finally {sampled.shader.delete();}
    }
    assert.equal(allocated,1,'moving panels reblurred an unchanged backdrop');
    sampler.reset();const rebuilt=sampler.shader(canvas,image,0,0,10);rebuilt.shader.delete();
    assert.equal(allocated,2,'reset reused stale tone data');
  } finally {paint.delete();probe.delete();sampler.dispose();surface.delete();image.delete();}
});

test('quarter-sized tone cache retains the requested Gaussian scale', () => {
  const w=256,h=96,info={width:w,height:h,colorType:kit.ColorType.RGBA_8888,
    alphaType:kit.AlphaType.Premul,colorSpace:kit.ColorSpace.SRGB},pixels=new Uint8Array(w*h*4);
  const frequency=2*Math.PI/64,amplitude=80,sigma=10;
  for(let y=0;y<h;y++)for(let x=0;x<w;x++) {
    const v=Math.round(128+amplitude*Math.cos(frequency*(x+.5)));
    pixels.set([v,v,v,255],(y*w+x)*4);
  }
  const image=kit.MakeImage(info,pixels,w*4),surface=kit.MakeSurface(1,1),canvas=surface.getCanvas();
  const sampler=createBackdropSampler(kit),backdrop=sampler.shader(canvas,image,0,0,sigma);
  const effect=kit.RuntimeEffect.Make('uniform shader source;uniform float2 point;half4 main(float2 p){return source.eval(point);}');
  const paint=new kit.Paint();let projection=0;
  try {
    for(let x=80;x<144;x++) {
      const shader=effect.makeShaderWithChildren(new Float32Array([(x+.5)/4,backdrop.strip+48/4]),[backdrop.shader]);
      try {
        paint.setShader(shader);canvas.drawPaint(paint);surface.flush();
        const p=canvas.readPixels(0,0,{...info,width:1,height:1});
        projection+=(p[0]-128)*Math.cos(frequency*(x+.5));
      } finally {paint.setShader(null);shader.delete();}
    }
    const gain=2*projection/(64*amplitude),expected=Math.exp(-.5*(frequency*sigma)**2);
    assert(Math.abs(gain-expected)<.04,`tone blur gain${gain} differs from independent Gaussian${expected}`);
    console.log(`WIDE_KERNEL gain=${gain.toFixed(4)} Gaussian=${expected.toFixed(4)}`);
  } finally {paint.delete();effect.delete();backdrop.shader.delete();sampler.dispose();surface.delete();image.delete();}
});

test('in-app surfaces preserve measured plateaus, rounded coverage, source replacement and host transforms', async () => {
  const w=320,h=180,info={width:w,height:h,colorType:kit.ColorType.RGBA_8888,
    alphaType:kit.AlphaType.Premul,colorSpace:kit.ColorSpace.SRGB};
  const surface=kit.MakeSurface(w,h),canvas=surface.getCanvas(),painter=createGlassPainter(kit,sources);
  const blackPixels=new Uint8Array(w*h*4);for(let i=3;i<blackPixels.length;i+=4)blackPixels[i]=255;
  const black=kit.MakeImage(info,blackPixels,w*4),white=kit.MakeImage(info,new Uint8Array(w*h*4).fill(255),w*4);
  const bounds={x:32,y:30,width:240,height:110,radius:[24,16,8,0]};
  const pixel=(pixels,x,y,c=0)=>pixels[(y*w+x)*4+c];
  try {
    assert.throws(()=>painter.drawSurface(canvas,bounds),/setSource/);
    for(const [source,dark,expected] of [[black,true,35],[white,true,175],[black,false,136],[white,false,247]]) {
      painter.setSource(source);canvas.clear(kit.TRANSPARENT);
      const matrix=Array.from(canvas.getTotalMatrix()),saves=canvas.getSaveCount();
      painter.drawSurface(canvas,bounds,{dark});surface.flush();
      const actual=canvas.readPixels(0,0,info);
      assert(Math.abs(pixel(actual,152,85)-expected)<=1,`in-app plateau ${dark}: ${pixel(actual,152,85)} != ${expected}`);
      assert.equal(pixel(actual,32,30,3),0,'top-left radius lost');
      assert.equal(pixel(actual,34,138,3),255,'bottom-left square corner lost');
      assert.equal(pixel(actual,0,0,3),0,'surface escaped its bounds');
      assert.deepEqual(Array.from(canvas.getTotalMatrix()),matrix);assert.equal(canvas.getSaveCount(),saves);
    }
    // With no material, positioned sampling must be exact even after a host transform.
    const ramp=new Uint8Array(w*h*4);
    for(let y=0;y<h;y++)for(let x=0;x<w;x++)ramp.set([x%256,y,64,255],(y*w+x)*4);
    const grid=kit.MakeImage(info,ramp,w*4);
    try {
      painter.setSource(grid);canvas.clear(kit.TRANSPARENT);canvas.save();canvas.translate(7,9);
      painter.drawSurface(canvas,bounds,{materialize:0});canvas.restore();surface.flush();
      const actual=canvas.readPixels(0,0,info);
      assert(Math.abs(pixel(actual,159,94)-152)<=1);assert(Math.abs(pixel(actual,159,94,1)-85)<=1);
      assert.throws(()=>painter.drawSurface(canvas,bounds,{tintAmount:NaN}),/material options/);
      assert.throws(()=>painter.drawSurface(canvas,{...bounds,radius:-1}),/Radius/);
      painter.setSource(black);
    } finally {grid.delete();}
    painter.dispose();painter.dispose();
    assert.equal(black.width(),w);assert.equal(white.width(),w);
    assert.throws(()=>painter.drawSurface(canvas,bounds),/disposed/);
  } finally {painter.dispose();surface.delete();black.delete();white.delete();}
});

test('positioned lenses sample the page, preserve host transforms and borrow source ownership', async () => {
  const w=240,h=180,info={width:w,height:h,colorType:kit.ColorType.RGBA_8888,
    alphaType:kit.AlphaType.Premul,colorSpace:kit.ColorSpace.SRGB};
  const pixels=new Uint8Array(w*h*4);
  for(let y=0;y<h;y++)for(let x=0;x<w;x++)pixels.set([x,y,64,255],(y*w+x)*4);
  const image=kit.MakeImage(info,pixels,w*4),surface=kit.MakeSurface(w,h);
  const painter=createGlassPainter(kit,sources),canvas=surface.getCanvas();
  try {
    assert.throws(()=>painter.drawLens(canvas,{x:20,y:30,width:80}),/setSource/);
    painter.setSource(image);
    const channel=(buffer,x,y,c=0)=>buffer[(y*w+x)*4+c];
    for(const [x,y,width,height] of [[10,15,80,80],[100,75,110,60],[-20,-10,80,80]]) {
      canvas.clear(kit.TRANSPARENT);
      painter.drawLens(canvas,{x,y,width,height,magnification:1.25});
      surface.flush();
      const actual=canvas.readPixels(0,0,info),cx=x+width/2,cy=y+height/2;
      assert.ok(Math.abs(channel(actual,cx,cy)-cx)<=1,'lens sampled the wrong horizontal page origin');
      assert.ok(Math.abs(channel(actual,cx,cy,1)-cy)<=1,'lens sampled the wrong vertical page origin');
      assert.equal(channel(actual,cx,cy,3),255);
      const gain=(channel(actual,cx+5,cy)-channel(actual,cx-5,cy))/10;
      assert.ok(gain>=.7&&gain<=.9,`centre magnification was lost: gain=${gain}`);
      assert.equal(channel(actual,w-1,0,3),0,'lens painted outside its coverage');
    }
    canvas.clear(kit.TRANSPARENT);canvas.save();canvas.translate(7,11);
    const before=Array.from(canvas.getTotalMatrix());
    painter.drawLens(canvas,{x:20,y:30,width:80});
    assert.deepEqual(Array.from(canvas.getTotalMatrix()),before,'painter leaked its canvas transform');
    canvas.restore();
    assert.throws(()=>painter.drawLens(canvas,{x:NaN,y:0,width:80}),/finite/);
    assert.throws(()=>painter.drawLens(canvas,{x:0,y:0,width:0}),/positive/);
    assert.throws(()=>painter.drawLens(canvas,{x:0,y:0,width:80,magnification:3}),/magnification/);
    assert.throws(()=>painter.setSource(null),/Image/);
    // Render a source+glass composition through the same public path a host uses.
    canvas.clear(kit.TRANSPARENT);canvas.drawImage(image,0,0);
    painter.drawLens(canvas,{x:22,y:24,width:108});
    painter.drawLens(canvas,{x:145,y:55,width:80,height:105,magnification:1.6});
    surface.flush();
    await mkdir('build',{recursive:true});
    const capture=surface.makeImageSnapshot();
    try {await writeFile('build/skia-painter.png',capture.encodeToBytes());} finally {capture.delete();}
    // Replacing a backdrop must not reuse stale pixels or take ownership of either image.
    const replacement=kit.MakeImage(info,new Uint8Array(w*h*4).fill(255),w*4);
    try {
      painter.setSource(replacement);
      canvas.clear(kit.TRANSPARENT);
      painter.drawLens(canvas,{x:20,y:30,width:80});surface.flush();
      assert.equal(channel(canvas.readPixels(0,0,info),60,70),255,'backdrop replacement was ignored');
      painter.setSource(image);
      assert.equal(replacement.width(),w);
    } finally {replacement.delete();}
    painter.dispose();painter.dispose();
    assert.equal(image.width(),w,'painter deleted its borrowed image');
    assert.throws(()=>painter.setSource(image),/disposed/);
    assert.throws(()=>painter.drawLens(canvas,{x:0,y:0,width:80}),/disposed/);
  } finally {painter.dispose();surface.delete();image.delete();}
});

test('portable Calm transforms real material only, with anchored coverage and exact release recovery', () => {
  const w=400,h=220,info={width:w,height:h,colorType:kit.ColorType.RGBA_8888,
    alphaType:kit.AlphaType.Premul,colorSpace:kit.ColorSpace.SRGB};
  const image=kit.MakeImage(info,new Uint8Array(w*h*4).fill(255),w*4),surface=kit.MakeSurface(w,h);
  const painter=createGlassPainter(kit,sources),canvas=surface.getCanvas();painter.setSource(image);
  try {
    for(const bounds of [{x:172,y:82,width:56,height:56},{x:40,y:74,width:320,height:72}]) {
      const motion=createCalmInteraction({...bounds,viewportWidth:w,viewportHeight:h});
      const cx=bounds.x+bounds.width/2,cy=bounds.y+bounds.height/2;
      function render(state) {
        canvas.clear(kit.TRANSPARENT);canvas.save();
        try {
          const [a,b,c,d]=state.matrix;
          canvas.translate(cx,cy);canvas.concat([a,b,0,c,d,0,0,0,1]);canvas.translate(-cx,-cy);
          painter.drawLens(canvas,bounds);
        } finally {canvas.restore();}
        surface.flush();return canvas.readPixels(0,0,info);
      }
      const rest=render(motion.sample(0));motion.press(0,1e6,1e6);
      const held=render(motion.sample(2));assert.notDeepEqual(held,rest,'feedback never reached the renderer');
      let left=w,right=-1,top=h,bottom=-1;
      for(let y=0;y<h;y++)for(let x=0;x<w;x++)if(held[(y*w+x)*4+3]>=128) {
        left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
      }
      assert.ok(Math.abs((left+right+1)/2-cx)<=.5,'material centre drifted horizontally');
      assert.ok(Math.abs((top+bottom+1)/2-cy)<=.5,'material centre drifted vertically');
      if(bounds.width===320) {
        assert.ok(right-left+1<=326,'large card exceeded press + pull width envelope');
        assert.ok(bottom-top+1<=76,'large card exceeded press + pull height envelope');
      }
      motion.release(2);assert.deepEqual(render(motion.sample(5)),rest,'release did not restore the exact image');
    }
  } finally {painter.dispose();surface.delete();image.delete();}
});

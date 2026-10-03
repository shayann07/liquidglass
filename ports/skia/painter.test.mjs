import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile, mkdir, writeFile} from 'node:fs/promises';
import CanvasKitInit from 'canvaskit-wasm';
import {createGlassPainter} from './painter.mjs';

const kit = await CanvasKitInit();
const sources = Object.fromEntries(await Promise.all(['material','content','endpoint'].map(async name =>
  [name, await readFile(new URL(`shaders/${name}.sksl`, import.meta.url), 'utf8')])));

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

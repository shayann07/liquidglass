/** Terminal example. Replace this generated grid with your own opaque Skia Image. */
import {readFile, mkdir, writeFile} from 'node:fs/promises';
import CanvasKitInit from 'canvaskit-wasm';
import {createGlassPainter} from './painter.mjs';

const kit=await CanvasKitInit();
const sources=Object.fromEntries(await Promise.all(['material','content','endpoint'].map(async pass=>
  [pass,await readFile(new URL(`shaders/${pass}.sksl`,import.meta.url),'utf8')])));
const width=640,height=360,pixels=new Uint8Array(width*height*4);
for(let y=0;y<height;y++)for(let x=0;x<width;x++) {
  const line=x%24<2||y%24<2;
  const tile=(Math.floor(x/96)+Math.floor(y/96))%2;
  pixels.set(line?[111,185,202,255]:tile?[18,42,61,255]:[11,26,44,255],(y*width+x)*4);
}
const source=kit.MakeImage({width,height,colorType:kit.ColorType.RGBA_8888,
  alphaType:kit.AlphaType.Premul,colorSpace:kit.ColorSpace.SRGB},pixels,width*4);
const surface=kit.MakeSurface(width,height),glass=createGlassPainter(kit,sources);
try {
  glass.setSource(source);
  const canvas=surface.getCanvas();
  canvas.drawImage(source,0,0);
  glass.drawLens(canvas,{x:70,y:70,width:220,magnification:1.25});
  glass.drawLens(canvas,{x:365,y:105,width:200,height:145,magnification:1.6});
  surface.flush();
  const capture=surface.makeImageSnapshot();
  try {
    await mkdir('build',{recursive:true});
    await writeFile('build/example.png',capture.encodeToBytes());
    console.log('Rendered production SkSL lenses: build/example.png');
  } finally {capture.delete();}
} finally {glass.dispose();surface.delete();source.delete();}

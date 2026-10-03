import {createGlassPainter} from './painter.mjs';

const canvas=document.querySelector('#glass'),status=document.querySelector('#status');
const backdrop=document.createElement('canvas');backdrop.width=960;backdrop.height=480;
const context2d=backdrop.getContext('2d');
let kit,sources,handle,context,surface,image,glass,x=400,y=130,mode=0,down=false;
function destroy(abandon=false) {
  if(abandon) context?.releaseResourcesAndAbandonContext();
  glass?.dispose();glass=null;image?.delete();image=null;surface?.delete();surface=null;
  context?.delete();context=null;
}
function draw() {
  if(!surface)return;
  const target=surface.getCanvas();target.clear(kit.TRANSPARENT);target.drawImage(image,0,0);
  glass.drawLens(target,{x,y,width:200,magnification:1.4});surface.flush();
}
function setBackdrop() {
  context2d.fillStyle=mode?'#3d263b':'#102a3d';context2d.fillRect(0,0,960,480);
  context2d.strokeStyle=mode?'#a76d86':'#527e96';context2d.lineWidth=1;
  for(let i=0;i<=960;i+=24){context2d.beginPath();context2d.moveTo(i,0);context2d.lineTo(i,480);context2d.stroke();}
  for(let i=0;i<=480;i+=24){context2d.beginPath();context2d.moveTo(0,i);context2d.lineTo(960,i);context2d.stroke();}
  context2d.fillStyle=mode?'#3d263b':'#102a3d';context2d.fillRect(190,160,620,140);
  context2d.fillStyle='#eaf0f7';context2d.font='34px system-ui';context2d.fillText('One source. Continuous detail.',230,222);
  context2d.font='18px system-ui';context2d.fillText('Your layout and controls stay yours.',230,262);
  const replacement=kit.MakeImageFromCanvasImageSource(backdrop);
  glass.setSource(replacement);const previous=image;image=replacement;previous?.delete();draw();
}
function create() {
  context=kit.MakeWebGLContext(handle);
  if(!context)throw new Error('WebGL renderer is unavailable.');
  surface=kit.MakeOnScreenGLSurface(context,canvas.width,canvas.height,kit.ColorSpace.SRGB);
  if(!surface)throw new Error('Could not create the glass surface.');
  glass=createGlassPainter(kit,sources);setBackdrop();
  canvas.dataset.backend='webgl';status.textContent='Ready. Drag the lens, or use the arrow keys.';
}
function move(nx,ny){x=Math.max(4,Math.min(756,nx));y=Math.max(4,Math.min(276,ny));draw();}
canvas.addEventListener('pointerdown',event=>{if(event.button!==0)return;down=true;canvas.setPointerCapture(event.pointerId);});
canvas.addEventListener('pointermove',event=>{
  if(!down)return;const bounds=canvas.getBoundingClientRect();
  move((event.clientX-bounds.left)*960/bounds.width-100,(event.clientY-bounds.top)*480/bounds.height-100);
});
for(const event of ['pointerup','pointercancel','lostpointercapture'])canvas.addEventListener(event,()=>down=false);
canvas.addEventListener('keydown',event=>{
  const delta={ArrowLeft:[-24,0],ArrowRight:[24,0],ArrowUp:[0,-24],ArrowDown:[0,24]}[event.key];
  if(delta){event.preventDefault();move(x+delta[0],y+delta[1]);}
});
document.querySelector('#reset').onclick=()=>move(400,130);
document.querySelector('#backdrop').onclick=()=>{mode=1-mode;if(glass)setBackdrop();};
canvas.addEventListener('webglcontextlost',event=>{
  event.preventDefault();down=false;destroy(true);status.textContent='Graphics interrupted. Waiting to restore…';
});
canvas.addEventListener('webglcontextrestored',()=>{
  try{create();}catch(error){destroy();status.textContent=error.message;}
});
window.addEventListener('pagehide',()=>{destroy();if(handle)kit.deleteContext(handle);},{once:true});
try {
  kit=await CanvasKitInit({locateFile:file=>`./node_modules/canvaskit-wasm/bin/${file}`});
  sources=Object.fromEntries(await Promise.all(['material','content','endpoint'].map(async pass=>{
    const response=await fetch(`./shaders/${pass}.sksl`);
    if(!response.ok)throw new Error(`Could not load the ${pass} shader.`);
    return [pass,await response.text()];
  })));
  handle=kit.GetWebGLContext(canvas);
  if(!handle)throw new Error('WebGL is unavailable in this browser.');
  create();
}catch(error){destroy();status.textContent=error.message;}

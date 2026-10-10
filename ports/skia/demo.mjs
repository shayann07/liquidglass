import {createGlassScene} from './scene.mjs';

const canvas=document.querySelector('#glass'),status=document.querySelector('#status');
const backdrop=document.createElement('canvas');backdrop.width=960;backdrop.height=480;
const context2d=backdrop.getContext('2d');
const card={x:52,y:344,width:400,height:88};
const preference=matchMedia('(prefers-reduced-motion: reduce)');
let kit,handle,context,surface,image,foreground,scene,cardGlass,lens,x=400,y=130,mode=0,down=null,origin,frame=0;
const now=()=>performance.now()/1000;
function destroy(abandon=false) {
  cancelAnimationFrame(frame);frame=0;
  if(abandon) context?.releaseResourcesAndAbandonContext();
  scene?.dispose();scene=null;cardGlass=null;lens=null;image?.delete();image=null;surface?.delete();surface=null;
  foreground?.delete();foreground=null;
  context?.delete();context=null;
}
function draw() {
  if(!surface)return;
  const target=surface.getCanvas();target.clear(kit.TRANSPARENT);
  const active=scene.draw(target,now()),state=cardGlass.feedback;
  // Ordinary labels remain anchored; only the material receives the feedback transform.
  target.drawImage(foreground,0,0);surface.flush();
  canvas.dataset.feedback=state.active?'animating':state.held?'held':'rest';
  if(active&&!frame)frame=requestAnimationFrame(()=>{frame=0;draw();});
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
  scene.setSource(replacement);const previous=image;image=replacement;previous?.delete();
  context2d.clearRect(0,0,960,480);context2d.fillStyle='#eaf0f7';context2d.font='20px system-ui';
  context2d.fillText('Hold here. Pull gently, or to an edge.',76,384);
  context2d.fillStyle='#aac0ce';context2d.font='14px system-ui';context2d.fillText('The material responds. The label stays put.',76,409);
  foreground?.delete();foreground=kit.MakeImageFromCanvasImageSource(backdrop);draw();
}
function create() {
  context=kit.MakeWebGLContext(handle);
  if(!context)throw new Error('WebGL renderer is unavailable.');
  surface=kit.MakeOnScreenGLSurface(context,canvas.width,canvas.height,kit.ColorSpace.SRGB);
  if(!surface)throw new Error('Could not create the glass surface.');
  scene=createGlassScene(kit,{width:960,height:480,reducedMotion:preference.matches});
  lens=scene.addLens({x,y,width:200,magnification:1.4});
  cardGlass=scene.addSurface({...card,radius:24},{dark:true});setBackdrop();
  canvas.dataset.backend='webgl';status.textContent='Ready. Drag the lens or hold the card. Arrow keys move the lens; Space presses the card.';
}
function move(nx,ny){x=Math.max(4,Math.min(756,nx));y=Math.max(4,Math.min(276,ny));lens?.setBounds({x,y},now());draw();}
function point(event){const bounds=canvas.getBoundingClientRect();return {x:(event.clientX-bounds.left)*960/bounds.width,y:(event.clientY-bounds.top)*480/bounds.height};}
canvas.addEventListener('pointerdown',event=>{
  if(!scene||event.button!==0||down!==null)return;
  origin=point(event);down={id:event.pointerId,card:origin.x>=card.x&&origin.x<=card.x+card.width&&origin.y>=card.y&&origin.y<=card.y+card.height};
  canvas.setPointerCapture(event.pointerId);
  if(down.card){cardGlass.press(now());draw();}
});
canvas.addEventListener('pointermove',event=>{
  if(!down||event.pointerId!==down.id)return;const p=point(event);
  if(down.card){cardGlass.press(now(),p.x-origin.x,p.y-origin.y);draw();}else move(p.x-100,p.y-100);
});
function release(){down=null;cardGlass?.release(now());draw();}
for(const event of ['pointerup','pointercancel','lostpointercapture'])canvas.addEventListener(event,e=>{if(down?.id===e.pointerId)release();});
canvas.addEventListener('keydown',event=>{
  if(event.code==='Space'){event.preventDefault();if(!event.repeat){cardGlass?.press(now());draw();}return;}
  const delta={ArrowLeft:[-24,0],ArrowRight:[24,0],ArrowUp:[0,-24],ArrowDown:[0,24]}[event.key];
  if(delta){event.preventDefault();move(x+delta[0],y+delta[1]);}
});
canvas.addEventListener('keyup',event=>{if(event.code==='Space'){event.preventDefault();release();}});
canvas.addEventListener('blur',release);
const onPreference=()=>{down=null;scene?.setReducedMotion(preference.matches,now());draw();};
preference.addEventListener('change',onPreference);
document.querySelector('#reset').onclick=()=>move(400,130);
document.querySelector('#backdrop').onclick=()=>{mode=1-mode;if(scene)setBackdrop();};
canvas.addEventListener('webglcontextlost',event=>{
  event.preventDefault();down=null;destroy(true);status.textContent='Graphics interrupted. Waiting to restore…';
});
canvas.addEventListener('webglcontextrestored',()=>{
  try{create();}catch(error){destroy();status.textContent=error.message;}
});
window.addEventListener('pagehide',()=>{preference.removeEventListener('change',onPreference);destroy();if(handle)kit.deleteContext(handle);},{once:true});
try {
  kit=await CanvasKitInit({locateFile:file=>`./node_modules/canvaskit-wasm/bin/${file}`});
  handle=kit.GetWebGLContext(canvas);
  if(!handle)throw new Error('WebGL is unavailable in this browser.');
  create();
}catch(error){destroy();status.textContent=error.message;}

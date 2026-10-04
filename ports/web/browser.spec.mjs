import { test, expect } from '@playwright/test';
test('real WebGL contract, recovery and lifecycle',async({page})=>{
  await page.goto('/browser-test.html');
  await expect(page.locator('#result')).toHaveText(/^PASS:/,{timeout:15000});
});

test('production Skia WebGL surface preserves an opaque one-pixel backdrop',async({page})=>{
  await page.goto('/skia/');
  await expect(page.locator('#status')).toHaveText(/^Ready\./,{timeout:20000});
  const errors=await page.evaluate(async()=>{
    const {createGlassPainter}=await import('/skia/painter.mjs');
    const kit=await CanvasKitInit({locateFile:file=>`/skia/node_modules/canvaskit-wasm/bin/${file}`});
    const sources=Object.fromEntries(await Promise.all(['material','content','endpoint'].map(async pass=>
      [pass,await (await fetch(`/skia/shaders/${pass}.sksl`)).text()])));
    const element=document.createElement('canvas');element.width=160;element.height=96;
    const handle=kit.GetWebGLContext(element);
    if(!handle)throw new Error('WebGL context unavailable');
    let context,surface,painter,full,tiny;
    try {
      context=kit.MakeWebGLContext(handle);
      if(!context)throw new Error('Skia WebGL context unavailable');
      surface=kit.MakeOnScreenGLSurface(context,160,96,kit.ColorSpace.SRGB);
      if(!surface)throw new Error('Skia WebGL surface unavailable');
      painter=createGlassPainter(kit,sources);
      const info={width:160,height:96,colorType:kit.ColorType.RGBA_8888,
        alphaType:kit.AlphaType.Premul,colorSpace:kit.ColorSpace.SRGB};
      const pixels=new Uint8Array(160*96*4);
      for(let i=0;i<pixels.length;i+=4)pixels.set([64,128,192,255],i);
      full=kit.MakeImage(info,pixels,160*4);
      tiny=kit.MakeImage({...info,width:1,height:1},new Uint8Array([64,128,192,255]),4);
      const canvas=surface.getCanvas(),differences=[];
      for(const dark of [true,false]) {
        const results=[];
        for(const image of [full,tiny]) {
          painter.setSource(image);canvas.clear(kit.TRANSPARENT);
          painter.drawSurface(canvas,{x:16,y:16,width:128,height:64,radius:20},{dark});surface.flush();
          const result=canvas.readPixels(0,0,info);
          if(!result||result[(48*160+80)*4+3]!==255)throw new Error('Missing opaque rendered material');
          results.push(result);
        }
        let worst=0;for(let i=0;i<results[0].length;i++)worst=Math.max(worst,Math.abs(results[0][i]-results[1][i]));
        differences.push(worst);
      }
      return differences;
    } finally {
      painter?.dispose();full?.delete();tiny?.delete();surface?.delete();context?.delete();kit.deleteContext(handle);
    }
  });
  expect(errors).toHaveLength(2);
  for(const error of errors)expect(error).toBeLessThanOrEqual(1);
});

test('portable Calm controller drives the production material, recovers, and respects reduced motion',async({page},testInfo)=>{
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.goto('/skia/');const canvas=page.locator('#glass');
  await expect(page.locator('#status')).toHaveText(/^Ready\./,{timeout:20000});
  // Keyboard input activates :focus-visible; its rounded outline intersects the screenshot's
  // rectangular corners. Compare material pixels without that host decoration, not a looser tolerance.
  const capture=()=>canvas.screenshot({style:'#glass { outline: none !important; }'});
  const rest=await capture(),bounds=await canvas.boundingBox();
  await page.mouse.move(bounds.x+bounds.width*.25,bounds.y+bounds.height*.8);
  await page.mouse.down();
  await page.mouse.move(bounds.x+bounds.width-2,bounds.y+2,{steps:8});
  await expect(canvas).toHaveAttribute('data-feedback','held');
  expect(rest.equals(await capture())).toBe(false);
  await page.screenshot({path:testInfo.outputPath('skia-calm-held.png'),fullPage:true});
  await canvas.dispatchEvent('pointercancel',{pointerId:1});await page.mouse.up();
  await expect(canvas).toHaveAttribute('data-feedback','rest');
  expect(rest.equals(await capture())).toBe(true);
  await canvas.focus();await page.keyboard.down('Space');
  await expect(canvas).toBeFocused();
  await expect(canvas).toHaveAttribute('data-feedback','held');
  expect(rest.equals(await capture())).toBe(false);
  await page.emulateMedia({reducedMotion:'reduce'});
  await expect(canvas).toHaveAttribute('data-feedback','rest');
  expect(rest.equals(await capture())).toBe(true);
  await page.keyboard.up('Space');await page.keyboard.down('Space');
  await expect(canvas).toHaveAttribute('data-feedback','held');
  expect(rest.equals(await capture())).toBe(true);
  await page.keyboard.up('Space');expect(errors).toEqual([]);
});
test('framework-free example remains interactive and keyboard accessible',async({page},testInfo)=>{
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.goto('/index.html');
  const canvas=page.locator('#glass');
  await page.getByRole('button',{name:'Type & lines'}).click();
  const before=await canvas.screenshot();
  await canvas.focus();await page.keyboard.press('ArrowRight');await page.keyboard.press('ArrowDown');
  const after=await canvas.screenshot();expect(before.equals(after)).toBe(false);
  await expect(page.locator('#status')).toBeEmpty();expect(errors).toEqual([]);
  await page.screenshot({path:testInfo.outputPath('web-preview.png'),fullPage:true});
});

test('production Skia painter supports browser input, source replacement and context recovery',async({page},testInfo)=>{
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.goto('/skia/');
  const canvas=page.locator('#glass');
  await expect(page.locator('#status')).toHaveText(/^Ready\./,{timeout:20000});
  await expect(canvas).toHaveAttribute('data-backend','webgl');
  const before=await canvas.screenshot();
  await canvas.focus();await page.keyboard.press('ArrowLeft');await page.keyboard.press('ArrowDown');
  expect(before.equals(await canvas.screenshot())).toBe(false);
  const bounds=await canvas.boundingBox();
  await page.mouse.move(bounds.x+bounds.width*.5,bounds.y+bounds.height*.5);
  await page.mouse.down();
  await page.mouse.move(bounds.x+bounds.width-2,bounds.y+bounds.height-2,{steps:5});
  await page.mouse.up();
  expect(before.equals(await canvas.screenshot())).toBe(false);
  await page.getByRole('button',{name:'Reset lens'}).click();
  expect(before.equals(await canvas.screenshot())).toBe(true);
  await page.getByRole('button',{name:'Change backdrop'}).click();
  const changed=await canvas.screenshot();expect(changed.equals(before)).toBe(false);
  await page.evaluate(()=>{
    const element=document.querySelector('#glass');
    const gl=element.getContext('webgl2')||element.getContext('webgl');
    const extension=gl.getExtension('WEBGL_lose_context');
    if(!extension)throw new Error('Context-loss extension unavailable');
    element.addEventListener('webglcontextlost',()=>setTimeout(()=>extension.restoreContext(),500),{once:true});
    extension.loseContext();
  });
  await expect(page.locator('#status')).toHaveText(/^Graphics interrupted/);
  await expect(page.locator('#status')).toHaveText(/^Ready\./,{timeout:20000});
  expect(changed.equals(await canvas.screenshot())).toBe(true);
  expect(errors).toEqual([]);
  await page.screenshot({path:testInfo.outputPath('skia-browser.png'),fullPage:true});
});

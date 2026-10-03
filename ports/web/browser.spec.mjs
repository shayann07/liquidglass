import { test, expect } from '@playwright/test';
test('real WebGL contract, recovery and lifecycle',async({page})=>{
  await page.goto('/browser-test.html');
  await expect(page.locator('#result')).toHaveText(/^PASS:/,{timeout:15000});
});

test('portable Calm controller drives the production material, recovers, and respects reduced motion',async({page},testInfo)=>{
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.goto('/skia/');const canvas=page.locator('#glass');
  await expect(page.locator('#status')).toHaveText(/^Ready\./,{timeout:20000});
  const rest=await canvas.screenshot(),bounds=await canvas.boundingBox();
  await page.mouse.move(bounds.x+bounds.width*.25,bounds.y+bounds.height*.8);
  await page.mouse.down();
  await page.mouse.move(bounds.x+bounds.width-2,bounds.y+2,{steps:8});
  await expect(canvas).toHaveAttribute('data-feedback','held');
  expect(rest.equals(await canvas.screenshot())).toBe(false);
  await page.screenshot({path:testInfo.outputPath('skia-calm-held.png'),fullPage:true});
  await canvas.dispatchEvent('pointercancel',{pointerId:1});await page.mouse.up();
  await expect(canvas).toHaveAttribute('data-feedback','rest');
  expect(rest.equals(await canvas.screenshot())).toBe(true);
  await canvas.focus();await page.keyboard.down('Space');
  await expect(canvas).toHaveAttribute('data-feedback','held');
  expect(rest.equals(await canvas.screenshot())).toBe(false);
  await page.emulateMedia({reducedMotion:'reduce'});
  await expect(canvas).toHaveAttribute('data-feedback','rest');
  expect(rest.equals(await canvas.screenshot())).toBe(true);
  await page.keyboard.up('Space');await page.keyboard.down('Space');
  await expect(canvas).toHaveAttribute('data-feedback','held');
  expect(rest.equals(await canvas.screenshot())).toBe(true);
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

import { test, expect } from '@playwright/test';
test('real WebGL contract, recovery and lifecycle',async({page})=>{
  await page.goto('/browser-test.html');
  await expect(page.locator('#result')).toHaveText(/^PASS:/,{timeout:15000});
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

import {test, expect} from '@playwright/test';
import {mkdir} from 'node:fs/promises';

/** Mean absolute difference of two PNG screenshots, decoded in the page. */
async function difference(page, a, b) {
  return page.evaluate(async ([a, b]) => {
    const load = s => new Promise((resolve, reject) => {
      const image = new Image();
      image.onload = () => {
        const c = document.createElement('canvas'); c.width = image.width; c.height = image.height;
        const g = c.getContext('2d'); g.drawImage(image, 0, 0); resolve(g.getImageData(0, 0, c.width, c.height).data);
      };
      image.onerror = reject; image.src = 'data:image/png;base64,' + s;
    });
    const [x, y] = [await load(a), await load(b)];
    let sum = 0; for (let i = 0; i < x.length; i++) sum += Math.abs(x[i] - y[i]);
    return sum / x.length;
  }, [a.toString('base64'), b.toString('base64')]);
}

function watchErrors(page) {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  return errors;
}

test('the element upgrades and its slotted HTML stays visible, accessible and clickable', async ({page}) => {
  const errors = watchErrors(page);
  await page.goto('/index.html?scheme=light');
  expect(await page.evaluate(() => customElements.get('liquid-glass') === document.querySelector('#card').constructor)).toBe(true);
  expect(await page.evaluate(() => !!document.querySelector('#card').shadowRoot)).toBe(true);
  const share = page.getByRole('button', {name: 'Share'});
  await expect(share).toBeVisible();
  await share.click();
  await expect(page.locator('#status')).toHaveAttribute('data-clicked', 'share');
  await page.getByRole('button', {name: 'Details'}).click();
  await expect(page.locator('#status')).toHaveAttribute('data-clicked', 'details');
  // Keyboard reaches slotted controls; the glass layers are never in the tab order.
  await page.getByRole('button', {name: 'Primary action'}).focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('#status')).toHaveAttribute('data-clicked', 'primary');
  expect(await page.evaluate(() => document.querySelector('#card').shadowRoot.querySelector('.material').getAttribute('aria-hidden'))).toBe('true');
  // Slotted text is hit-testable at its own position (the glass takes no pointer events).
  const box = await page.locator('#card h3').boundingBox();
  expect(await page.evaluate(([x, y]) => document.elementFromPoint(x, y)?.tagName, [box.x + 10, box.y + box.height / 2])).toBe('H3');
  expect(errors).toEqual([]);
});

test('the html engine applies a backdrop-filter: SVG lensing in Chromium, blur when lensing is off', async ({page}) => {
  const errors = watchErrors(page);
  await page.goto('/index.html?scheme=light');
  const card = page.locator('#card');
  await expect(card).toHaveAttribute('data-engine', 'html');
  await expect(card).toHaveAttribute('data-optics', 'lens');
  const filter = () => page.evaluate(() => {
    const base = document.querySelector('#card').shadowRoot.querySelector('.base');
    return getComputedStyle(base).backdropFilter;
  });
  expect(await filter()).toMatch(/^url\("#liquid-glass-\d+"\)/);
  // The referenced filter exists and carries the generated displacement map.
  expect(await page.evaluate(() => {
    const root = document.querySelector('#card').shadowRoot;
    const href = root.querySelector('filter feImage')?.getAttribute('href') ?? '';
    return root.querySelector('filter feDisplacementMap') !== null && href.startsWith('data:image/png;base64,');
  })).toBe(true);
  // The glass changes what is behind it: compare with the backdrop-filter switched off.
  const toolbar = page.locator('#toolbar');
  const lens = await toolbar.screenshot();
  await page.evaluate(() => { document.querySelector('#toolbar').shadowRoot.querySelector('.base').style.backdropFilter = 'none'; });
  const none = await toolbar.screenshot();
  expect(await difference(page, lens, none)).toBeGreaterThan(2);
  await page.locator('#frost').check();
  await expect(card).toHaveAttribute('data-optics', 'frost');
  expect(await filter()).toMatch(/^blur\(0\.875px\)/);
  expect(await page.evaluate(() => {
    const wide = document.querySelector('#card').shadowRoot.querySelector('.wide');
    return [getComputedStyle(wide).backdropFilter, Number(getComputedStyle(wide).opacity).toFixed(3)];
  })).toEqual(['blur(10px)', '0.664']);
  expect(errors).toEqual([]);
});

test('scheme, tint and resize update the material', async ({page}) => {
  await page.goto('/index.html?scheme=light');
  const tint = sel => page.evaluate(sel => getComputedStyle(document.querySelector(sel).shadowRoot.querySelector('.tint')).backgroundColor, sel);
  // Light Regular: 241 grey at 144/255; dark: 35/115 grey at 115/255 (black at 0.451 plus the 35/255 lift).
  // Polled: the element styles itself after the page loads (CI once read it before: rgba(0, 0, 0, 0)).
  await expect.poll(() => tint('#card')).toBe('rgba(241, 241, 241, 0.565)');
  await page.getByRole('button', {name: 'Dark'}).click();
  await expect.poll(() => tint('#card')).toBe('rgba(78, 78, 78, 0.45)'); // Chromium stores alpha in 8 bits (115/255)
  await expect.poll(() => tint('#tinted')).toMatch(/^rgba\(/);
  const width = () => page.evaluate(() => document.querySelector('#card').shadowRoot.querySelector('filter').getAttribute('width'));
  const before = Number(await width());
  await page.evaluate(() => { document.querySelector('#card').style.width = '360px'; });
  await expect.poll(async () => Number(await width())).toBeCloseTo(before + 60, 0);
});

test('the canvas engine draws the production shader over the provided canvas', async ({page}) => {
  const errors = watchErrors(page);
  await page.goto('/index.html?scheme=dark');
  const card = page.locator('#c-card');
  await expect(card).toHaveAttribute('data-engine', 'canvas', {timeout: 30000});
  await expect(card).toHaveAttribute('data-optics', 'shader');
  const stats = () => page.evaluate(() => {
    const canvas = document.querySelector('#c-card').shadowRoot.querySelector('canvas.shader');
    const {width, height} = canvas, data = canvas.getContext('2d').getImageData(0, 0, width, height).data;
    let opaque = 0, min = 255, max = 0;
    for (let i = 0; i < data.length; i += 4) {
      if (data[i + 3] === 255) opaque++;
      const luma = (data[i] + data[i + 1] + data[i + 2]) / 3;
      if (data[i + 3] > 0) { min = Math.min(min, luma); max = Math.max(max, luma); }
    }
    const centre = Array.from(data.slice(((height >> 1) * width + (width >> 1)) * 4, ((height >> 1) * width + (width >> 1)) * 4 + 4));
    const mean = (centre[0] + centre[1] + centre[2]) / 3;
    return {width, height, opaque: opaque / (width * height), range: max - min, centre, mean};
  });
  const first = await stats();
  expect(first.width).toBeGreaterThan(100);
  expect(first.opaque).toBeGreaterThan(0.9); // the rounded corners are the only transparent pixels
  expect(first.centre[3]).toBe(255);
  expect(first.range).toBeGreaterThan(10); // refracted, not a flat fill
  // The source drives the pixels: repaint it and refresh.
  await page.evaluate(() => {
    const photo = document.querySelector('#photo'), g = photo.getContext('2d');
    g.fillStyle = '#ffffff'; g.fillRect(0, 0, photo.width, photo.height);
    for (const el of document.querySelectorAll('liquid-glass[engine=canvas]')) el.refresh();
  });
  await expect.poll(async () => (await stats()).mean).toBeGreaterThan(first.mean + 20);
  // Dark Regular over white at Tint 50 is the lab tone (#200): 31.72 + 254.40 - 101.01 = 185.1 of 255, within
  // rounding (it was (1 - 0.451) + 35/255 = 0.686 of white, 175, under the earlier fit).
  expect((await stats()).centre.slice(0, 3).every(v => Math.abs(v - 185) <= 3)).toBe(true);
  // A variant the canvas engine does not draw falls back to the html engine, and says why.
  await page.evaluate(() => { document.querySelector('#c-card').variant = 'clear'; });
  await expect(card).toHaveAttribute('data-engine', 'html');
  expect(await page.evaluate(() => document.querySelector('#c-card').renderState.reason)).toBe('canvas-variant');
  expect(errors).toEqual([]);
});

test('interactive glass presses its material only, and respects reduced motion', async ({page}) => {
  const errors = watchErrors(page);
  await page.goto('/index.html?scheme=light');
  const toolbar = page.locator('#toolbar');
  const transform = () => page.evaluate(() => document.querySelector('#toolbar').shadowRoot.querySelector('.material').style.transform);
  const glow = () => page.evaluate(() => document.querySelector('#toolbar').shadowRoot.querySelector('.highlight').style.opacity);
  const box = await toolbar.boundingBox();
  const layout = await page.locator('#share').boundingBox();
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await expect.poll(transform).toMatch(/matrix\(/);
  expect(await glow()).toBe('1');
  expect(await page.locator('#share').boundingBox()).toEqual(layout); // layout and hit targets never move
  await page.mouse.up();
  await expect.poll(transform).toBe('');
  expect(await glow()).toBe('0');
  await page.emulateMedia({reducedMotion: 'reduce'});
  await page.mouse.down();
  await page.waitForTimeout(200);
  expect(await transform()).toBe('');
  expect(await glow()).toBe('1'); // the glow is feedback, not motion
  await page.mouse.up();
  await page.emulateMedia({reducedMotion: 'no-preference'});
  // Space on a slotted control presses the glass too, and the control still gets its click.
  await page.locator('#share').focus();
  await page.keyboard.down(' ');
  await expect.poll(transform).toMatch(/matrix\(/);
  await page.keyboard.up(' ');
  await expect(page.locator('#status')).toHaveAttribute('data-clicked', 'share');
  await expect.poll(transform).toBe('');
  expect(errors).toEqual([]);
});

test('Increase Contrast mixes the glass toward a near-solid in both engines', async ({page}) => {
  await page.emulateMedia({contrast: 'more'});
  await page.goto('/index.html?scheme=light');
  await expect(page.locator('#c-card')).toHaveAttribute('data-engine', 'canvas', {timeout: 30000});
  expect(await page.evaluate(() => getComputedStyle(document.querySelector('#card').shadowRoot.querySelector('.tint')).backgroundImage))
    .toContain('rgba(247, 247, 247, 0.88)');
  const centre = () => page.evaluate(() => {
    const c = document.querySelector('#c-card').shadowRoot.querySelector('canvas.shader');
    const d = c.getContext('2d').getImageData(c.width >> 1, c.height >> 1, 1, 1).data;
    return (d[0] + d[1] + d[2]) / 3;
  });
  expect(await centre()).toBeGreaterThan(225); // light glass over a dark source reads near-solid light
});

test('elements clean up when removed and work again when re-inserted', async ({page}) => {
  const errors = watchErrors(page);
  await page.goto('/index.html?scheme=light');
  await expect(page.locator('#card')).toHaveAttribute('data-optics', /lens|frost/);
  await page.evaluate(() => {
    const card = document.querySelector('#card'), parent = card.parentNode;
    card.remove(); parent.append(card);
    const made = document.createElement('liquid-glass');
    made.id = 'made'; made.textContent = 'Made in script'; made.setAttribute('shape', 'rect');
    document.body.append(made);
  });
  await expect(page.locator('#made')).toHaveAttribute('data-engine', 'html');
  await expect(page.locator('#card')).toHaveAttribute('data-engine', 'html');
  await page.evaluate(() => document.querySelector('#made').remove());
  expect(errors).toEqual([]);
});

// Visual evidence: writes PNGs when LIQUIDGLASS_CAPTURE_DIR is set (see review/web-component-2026-10-18).
test('capture the example for review', async ({page, browser}, testInfo) => {
  const dir = process.env.LIQUIDGLASS_CAPTURE_DIR;
  test.skip(!dir, 'set LIQUIDGLASS_CAPTURE_DIR to write review captures');
  await mkdir(dir, {recursive: true});
  await page.setViewportSize({width: 1100, height: 1250});
  for (const [name, query] of [['light', '?scheme=light'], ['dark', '?scheme=dark'],
    ['light-frost', '?scheme=light&lensing=off'], ['dark-frost', '?scheme=dark&lensing=off']]) {
    await page.goto('/index.html' + query);
    await expect(page.locator('#c-card')).toHaveAttribute('data-engine', 'canvas', {timeout: 30000});
    await page.waitForTimeout(500);
    await page.locator('main').screenshot({path: `${dir}/${name}.png`});
  }
  // Crops at 2 device px per CSS px, where the rim and the refraction at the caps can be seen.
  const dense = await browser.newPage({viewport: {width: 1100, height: 1250}, deviceScaleFactor: 2});
  for (const [name, query] of [['light', '?scheme=light'], ['dark', '?scheme=dark'], ['light-frost', '?scheme=light&lensing=off']]) {
    await dense.goto('/index.html' + query);
    await expect(dense.locator('#c-card')).toHaveAttribute('data-engine', 'canvas', {timeout: 30000});
    await dense.waitForTimeout(500);
    const crop = async (selector, file, margin = 14) => {
      const box = await dense.locator(selector).boundingBox();
      await dense.screenshot({path: `${dir}/${file}`, clip: {x: box.x - margin, y: box.y - margin,
        width: box.width + 2 * margin, height: box.height + 2 * margin}});
    };
    await crop('#toolbar', `${name}-toolbar-2x.png`);
    await crop('#clear-pill', `${name}-clear-2x.png`);
    if (name.endsWith('frost')) continue; // the canvas engine and the card's lens do not depend on lensing
    await crop('#card', `${name}-card-2x.png`);
    await crop('#c-toolbar', `${name}-canvas-toolbar-2x.png`);
  }
  await dense.close();
  testInfo.annotations.push({type: 'captures', description: dir});
});

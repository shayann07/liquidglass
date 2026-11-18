// Apache-2.0. Renders review/atlas/skia-lens-and-bake-2026-11-18/lens.png: drawLens with lighting false and true,
// over a flat grey page and a striped page. Software CanvasKit. Run: node lens-evidence.mjs <output dir>
import {mkdir, writeFile} from 'node:fs/promises';
import CanvasKitInit from 'canvaskit-wasm';
import {createGlassPainter} from './index.mjs';

const kit = await CanvasKitInit();
const out = process.argv[2] ?? 'build';
const W = 256, H = 256, info = {width: W, height: H, colorType: kit.ColorType.RGBA_8888, alphaType: kit.AlphaType.Premul, colorSpace: kit.ColorSpace.SRGB};
const pages = {
  grey: (x, y) => [128, 128, 128],
  stripes: (x, y) => { const v = Math.floor(x / 8) % 2 ? 210 : 45; return [v, v, v]; },
};
const sheet = kit.MakeSurface(W * 2, H * 2), sc = sheet.getCanvas();
let row = 0;
for (const [, pixel] of Object.entries(pages)) {
  const bytes = new Uint8Array(W * H * 4);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) bytes.set([...pixel(x, y), 255], (y * W + x) * 4);
  const image = kit.MakeImage(info, bytes, W * 4), painter = createGlassPainter(kit);
  painter.setSource(image);
  [false, true].forEach((lighting, col) => {
    const tile = kit.MakeSurface(W, H), c = tile.getCanvas();
    c.drawImage(image, 0, 0);
    painter.drawLens(c, {x: 48, y: 48, width: 160, magnification: 1.4}, {lighting});
    tile.flush(); const snap = tile.makeImageSnapshot();
    sc.drawImage(snap, col * W, row * H); snap.delete(); tile.delete();
  });
  painter.dispose(); image.delete(); row++;
}
sheet.flush();
const snap = sheet.makeImageSnapshot();
await mkdir(out, {recursive: true});
await writeFile(`${out}/lens.png`, snap.encodeToBytes());
snap.delete(); sheet.delete();

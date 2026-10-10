import type {Canvas, CanvasKit, Image} from 'canvaskit-wasm';
import {createGlassScene, createGlassPainter, createCalmInteraction} from 'liquidglass-skia-preview';
import {createGlassScene as subpathScene} from 'liquidglass-skia-preview/scene';
import {createCalmInteraction as subpathInteraction} from 'liquidglass-skia-preview/interaction';

declare const kit: CanvasKit, canvas: Canvas, image: Image;
const scene = createGlassScene(kit, {width: 480, height: 320, pixelRatio: 2});
scene.setSource(image);
const card = scene.addSurface({x: 10, y: 180, width: 420, height: 100, radius: [10, 20, 10, 20]}, {dark: true});
const lens = scene.addLens({x: 100, y: 20, width: 130});
card.press(0, 20, -40); scene.draw(canvas, .2); card.release(.2);
card.setMaterial({reducedTransparency: 1}); lens.setBounds({magnification: 1.5}, .3);
const nextFrame: boolean = scene.draw(canvas, .3);
scene.resize({pixelRatio: 1}, .3); scene.setReducedMotion(true, .3);
const painter = createGlassPainter(kit); painter.setSource(image);
painter.drawSurface(canvas, card.bounds, {density: 2}); painter.drawLens(canvas, lens.bounds);
const controller = createCalmInteraction({width: 100, height: 50});
controller.press(0); controller.resize({width: 120}, .1); controller.sample(.3);
subpathScene(kit, {width: 10, height: 10}).dispose();
subpathInteraction({width: 10, height: 10}).release(0);
// @ts-expect-error A surface needs an explicit height.
scene.addSurface({x: 0, y: 0, width: 100});
// @ts-expect-error Corner radii must have exactly four entries.
scene.addSurface({x: 0, y: 0, width: 100, height: 50, radius: [4, 5]});
// @ts-expect-error The runtime accepts boolean appearance, not CSS strings.
card.setMaterial({dark: 'dark'});
// @ts-expect-error Scene feedback never changes layout through a public mutable bounds property.
card.bounds.x = 50;
// @ts-expect-error Lens appearance is controlled through its magnification bounds.
lens.setMaterial({dark: true});
scene.dispose(); painter.dispose(); void nextFrame;

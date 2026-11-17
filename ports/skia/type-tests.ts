import type {Canvas, CanvasKit, Image} from 'canvaskit-wasm';
import {createGlassScene, createGlassPainter, createCalmInteraction, calmSelectorBar, createCalmNavigation,
  CalmSelectorController, calmSelectorMaterial, calmBarMaterial, bakeNavigation, navigationTrack, drawNavigation} from 'liquidglass-skia-preview';
import {bakeNavigation as bakeSubpath} from 'liquidglass-skia-preview/bake';
import {createGlassScene as subpathScene} from 'liquidglass-skia-preview/scene';
import {createCalmInteraction as subpathInteraction} from 'liquidglass-skia-preview/interaction';

declare const kit: CanvasKit, canvas: Canvas, image: Image;
const scene = createGlassScene(kit, {width: 480, height: 320, pixelRatio: 2});
scene.setSource(image);
const card = scene.addSurface({x: 10, y: 180, width: 420, height: 100, radius: [10, 20, 10, 20]}, {dark: true});
const lens = scene.addLens({x: 100, y: 20, width: 130});
const glassLens = scene.addLens({x: 100, y: 20, width: 130}, {lighting: true}); glassLens.setMaterial({shadow: false});
card.press(0, 20, -40); scene.draw(canvas, .2); card.release(.2);
card.setMaterial({reducedTransparency: 1}); lens.setBounds({magnification: 1.5}, .3);
const nextFrame: boolean = scene.draw(canvas, .3);
scene.resize({pixelRatio: 1}, .3); scene.setReducedMotion(true, .3);
const painter = createGlassPainter(kit); painter.setSource(image);
painter.drawSurface(canvas, card.bounds, {density: 2}); painter.drawLens(canvas, lens.bounds);
painter.drawLens(canvas, lens.bounds, {lighting: true, shadow: false, density: 2, light: [0, -1]});
painter.drawSurface(canvas, card.bounds, {dark: true, tint: [0.04, 0.52, 1, 0.5]});
// @ts-expect-error A tint is four sRGB components.
painter.drawSurface(canvas, card.bounds, {tint: [1, 1, 1]});
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
// @ts-expect-error The lens preset's switch is a boolean.
painter.drawLens(canvas, lens.bounds, {lighting: 'yes'});
const nav = scene.addNavigation({x: 20, y: 250, width: 300, height: 62}, {count: 3, onSelect: i => nav.select(i)});
nav.down(60, 280, .4); nav.up(60, 280, .45); const formed: number = nav.selector.formation;
painter.drawSelector(canvas, {x: 0, y: 0, width: 300, height: 62}, nav.selector);
painter.drawCalmBar(canvas, {x: 0, y: 0, width: 300, height: 62}, {dark: true, formation: nav.selector.formation});
const calm: number = calmSelectorMaterial({formation: .5, transit: .2}).uFormation;
void calm; void calmBarMaterial({dark: false});
const bar = calmSelectorBar({width: 834, height: 186, count: 3, density: 3});
const standalone = createCalmNavigation({bar}); standalone.frame(.1);
new CalmSelectorController().attach(bar);
// @ts-expect-error A navigation layer needs its item count.
scene.addNavigation({x: 0, y: 0, width: 100, height: 40}, {});
const track = bakeNavigation({bounds: {x: 20, y: 250, width: 300, height: 62}, count: 3, viewport: {width: 480, height: 320}},
  [{type: 'down', x: 60, y: 280, time: .1}, {type: 'move', x: 200, y: 280, time: .9}, {type: 'up', x: 200, y: 280, time: 1}, {type: 'cancel', time: 1.5}], {duration: 2});
const moment = track.sample(1.3); const held: boolean = moment.selector.held; const same = navigationTrack(track.toJSON()).sample(1.3);
drawNavigation(kit, painter, canvas, {bounds: nav.bounds, pixelRatio: 2, source: image}, moment, {material: {dark: true}, ink: null});
drawNavigation(kit, painter, canvas, {bounds: nav.bounds, source: image}, {selector: nav.selector, feedback: nav.feedback});
void held; void same; void bakeSubpath;
// @ts-expect-error An event needs a known type.
bakeNavigation({bounds: nav.bounds, count: 3, viewport: {width: 1, height: 1}}, [{type: 'tap', time: 0}], {duration: 1});
scene.dispose(); painter.dispose(); void nextFrame; void formed;

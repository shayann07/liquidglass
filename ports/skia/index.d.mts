import type {Canvas, CanvasKit, Image} from 'canvaskit-wasm';
import type {CalmSelectorPose, CalmSelectorState} from './selector.mjs';
export type {CalmSelectorPose, CalmSelectorState, CalmSelectorBar, CalmNavigation} from './selector.mjs';
export {CalmSelectorController, calmSelectorBar, centreOf, nearestTab, createCalmNavigation} from './selector.mjs';
/** The Compose Calm selector material as shader uniforms (lengths in dp) and its band blend. */
export function calmSelectorMaterial(state?: {dark?: boolean; formation?: number; transit?: number; heldTravel?: number; formedTap?: boolean}):
  {values: Readonly<Record<string, number | readonly number[]>>; uFormation: number};
/** The Compose Calm bar material (lengths in dp), its held lift following `formation` (or `touch`, for any touch). */
export function calmBarMaterial(state?: {dark?: boolean; formation?: number; touch?: number; clear?: boolean}): Readonly<Record<string, number | readonly number[]>>;
export interface CalmSelectorDrawOptions {
  dark?: boolean; density?: number; materialize?: number; reducedTransparency?: number; increasedContrast?: number;
  /** The source-sized backdrop with the bar's glass drawn over it: the lens looks through the bar. */
  through?: Image | null;
  /** lensFuse's six numbers, device px: a held lens's neck to a free button (drawSelector only). */
  fuse?: readonly number[] | null;
  /** False skips the lens's contact shadow (light Calm's held lens casts one; drawSelector only). */
  shadow?: boolean;
}

export interface GlassShaderSources { material: string; content: string; endpoint: string }
export type CornerRadii = number | readonly [number, number, number, number];
export interface GlassRect { x: number; y: number; width: number; height: number }
export interface GlassSurfaceBounds extends GlassRect { radius?: CornerRadii }
export interface GlassLensBounds { x: number; y: number; width: number; height?: number; magnification?: number }
/** What `drawLens` takes beyond its bounds, and what a scene lens takes as its material. All optional; the default is the
 * clear-lens preset, and `lighting: false` the bare magnifier. */
export interface GlassLensMaterial {
  /** The Compose clear-lens preset (GlassStyle.clearLens, `Glass.Lens`): rim highlight, a thin white tint and a contact
   * shadow, so a free-standing lens reads as glass over flat or low-detail content. Authored, not an iOS measurement.
   * Default true; false is the bare magnification map. */
  lighting?: boolean;
  /** With `lighting`: false skips the contact shadow under the lens. Default true. */
  shadow?: boolean;
  /** Device px per dp, scaling the preset's dp terms (bevel, refraction depth). Default 1; a scene multiplies it by its pixelRatio. */
  density?: number;
  /** With `lighting`: toward the light, y up negative. Default Compose's GlassLight.Default [0, -1]. */
  light?: readonly [number, number];
}
export interface GlassMaterial {
  dark?: boolean; tintAmount?: number; density?: number; materialize?: number;
  reducedTransparency?: number; increasedContrast?: number;
  /** Navigation bars only: GlassTabBarStyle.CalmClear's clearer bar (WhatsApp's and the App Store's). */
  clear?: boolean;
  /** Surfaces only: Compose's `Glass.tint(color)`. Replaces the tint colour and opacity ([r, g, b, a],
   * sRGB 0..1, quantized to 8 bits as Compose's Color is); the dark appearance keeps its lift. */
  tint?: readonly [number, number, number, number] | null;
  /** Toward the light, y up negative. Default Compose's GlassLight.Default [0, -1], straight overhead, as every measured
   * resting iOS 27 rim is lit. */
  light?: readonly [number, number];
}
export interface GlassFeedback {
  matrix: [number, number, number, number]; offset: [number, number];
  press: number; held: boolean; active: boolean;
}
export interface CalmGeometry {
  width: number; height: number; radius?: number; x?: number; y?: number;
  viewportWidth?: number; viewportHeight?: number;
}
export interface CalmOptions extends CalmGeometry {
  reducedMotion?: boolean; pressScale?: number; pressGrowth?: number;
  pullShape?: 'adaptive' | 'area-preserving';
}
export interface CalmInteraction {
  /** Monotonic seconds and cumulative logical displacement from pointer-down. */
  press(time: number, dx?: number, dy?: number): GlassFeedback;
  release(time: number): GlassFeedback;
  sample(time: number): GlassFeedback;
  resize(geometry: Partial<CalmGeometry>, time: number): GlassFeedback;
  setReducedMotion(reduced: boolean, time: number): GlassFeedback;
}
export function createCalmInteraction(options: CalmOptions): CalmInteraction;

/** Compose's GlassInteraction.Pullable with GlassPullShape.Free (the Phone scene's search button). Px. */
export interface FreeButtonOptions {
  size: number; density?: number; pressScale?: number; pressGrowth?: number; pressLift?: number;
  pullFollow?: number; pullElongation?: number; pullWidthRatio?: number; pullLimit?: number; pullLiftShare?: number;
  /** Release damping along x (Compose `GlassInteraction.pullReleaseDampingX`), 0.3; y keeps 0.45. */
  pullReleaseDampingX?: number;
  /** A control that is not a square button, px (default `size`); `radius` defaults to half the short side. Compose's
   * `GlassInteraction.Fluid` falls with size: 80 dp or less is the search button, 160 dp and more presses 2 dp a side
   * and never leaves its place. Authored by size. */
  width?: number; height?: number; radius?: number;
}
export interface FreeButtonTransform {
  /** Press scale (x; `pressScaleY` is y), then a stretch along and across `angle` (degrees), then the offset. */
  pressScale: number; pressScaleY: number; along: number; across: number; angle: number; tx: number; ty: number;
  /** The press glow and the material lift it gives (fraction of white). */
  glow: number; lift: number; pullX: number;
}
export interface FreeButton {
  /** Monotonic seconds; `move` takes the total finger travel since `down`. */
  down(time: number): void;
  move(time: number, dx: number, dy: number): void;
  up(time: number): void;
  /** A scroll took the gesture: everything returns critically damped, with no ring and no landing squash. */
  cancel(time: number): void;
  sample(time: number): FreeButtonTransform;

}
export function createFreeButton(options: FreeButtonOptions): FreeButton;

export interface GlassPainter {
  /** Borrows an opaque source. Replace it before deleting the previous image. */
  setSource(image: Image): void;
  /** Low-level painter coordinates are device pixels. */
  drawLens(canvas: Canvas, bounds: GlassLensBounds, options?: GlassLensMaterial): void;
  drawSurface(canvas: Canvas, bounds: GlassSurfaceBounds, material?: GlassMaterial): void;
  /** A Calm navigation bar's glass in the Compose Calm bar material (device px). */
  drawCalmBar(canvas: Canvas, bar: GlassRect, options?: Omit<CalmSelectorDrawOptions, 'through'> & {formation?: number; touch?: number; clear?: boolean;
    /** accessoryFuse's six numbers, device px: the neck to a free button beside the bar. */
    fuse?: readonly number[] | null}): void;
  /** A navigation selector's pose body over the bar rect (device px), in the Compose Calm selector material. */
  drawSelector(canvas: Canvas, bar: GlassRect,
    selector: {pose: CalmSelectorPose; formation?: number; transit?: number; heldTravel?: number; formedTap?: boolean}, options?: CalmSelectorDrawOptions): void;
  /** The selected ink inside the selector (Compose's content pass): `ink` is the bar's items in their
   * selected appearance, transparent ground, device px, origin at the bar's top-left. */
  drawSelectorInk(canvas: Canvas, bar: GlassRect,
    selector: {pose: CalmSelectorPose; formation?: number; transit?: number; heldTravel?: number; formedTap?: boolean}, ink: Image,
    options?: Omit<CalmSelectorDrawOptions, 'through'>): void;
  dispose(): void;
}
export function createGlassPainter(kit: CanvasKit, sources?: GlassShaderSources): GlassPainter;

export interface GlassLayer<Bounds> {
  readonly bounds: Readonly<Bounds>;
  readonly feedback: GlassFeedback;
  press(time: number, dx?: number, dy?: number): GlassFeedback;
  release(time: number): GlassFeedback;
  /** Host-owned layout changes cancel the current gesture. */
  setBounds(bounds: Partial<Bounds>, time: number): void;
  remove(): void;
}
export interface GlassSurfaceLayer extends GlassLayer<GlassSurfaceBounds> {
  setMaterial(material: Partial<GlassMaterial>): void;
}
export interface GlassLensLayer extends GlassLayer<GlassRect & {magnification: number}> {
  setMaterial(material: Partial<GlassLensMaterial>): void;
}
export interface GlassNavigationOptions {
  count: number; selected?: number; inset?: number; pillWidth?: number; pillInset?: number;
  onSelect?: (index: number) => void; material?: GlassMaterial;
}
/** A Calm navigation bar: logical page coordinates and monotonic seconds. */
export interface GlassNavigationLayer {
  readonly bounds: Readonly<GlassRect>;
  readonly selected: number;
  readonly selector: CalmSelectorState;
  /** The bar's own press feedback (matrix, offset): with `selector`, the state `drawNavigation` draws. */
  readonly feedback: GlassFeedback;
  down(x: number, y: number, time: number): void;
  move(x: number, y: number, time: number): void;
  up(x: number, y: number, time: number): void;
  cancel(time: number): void;
  select(index: number): void;
  /** The bar's items in their selected appearance (transparent ground, device px, bar origin), shown through the selector. */
  setSelectedInk(image: Image | null): void;
  remove(): void;
}
export interface GlassViewport { width: number; height: number; pixelRatio?: number }
export interface GlassSceneOptions extends GlassViewport { reducedMotion?: boolean; sources?: GlassShaderSources }
export interface GlassScene {
  setSource(image: Image): void;
  /** Scene bounds and input displacements are logical pixels. */
  addSurface(bounds: GlassSurfaceBounds, material?: GlassMaterial): GlassSurfaceLayer;
  addLens(bounds: GlassLensBounds, material?: GlassLensMaterial): GlassLensLayer;
  addNavigation(bounds: GlassRect, options: GlassNavigationOptions): GlassNavigationLayer;
  resize(viewport: Partial<GlassViewport>, time: number): void;
  setReducedMotion(reduced: boolean, time: number): void;
  /** True requests another animation frame. Draw labels after this, then flush the host surface. */
  draw(canvas: Canvas, time: number, options?: {drawBackdrop?: boolean}): boolean;
  dispose(): void;
}
export function createGlassScene(kit: CanvasKit, options: GlassSceneOptions): GlassScene;

export type {NavigationEvent, NavigationBar, NavigationSample, NavigationTrack, NavigationTrackData} from './bake.mjs';
export {bakeNavigation, navigationTrack} from './bake.mjs';
/** Draw a navigation bar, its selector and optional selected ink from a state, with no clock and no scene: the scene's own
 * drawing for any state a host holds (a baked `NavigationTrack` sample, or a live layer's `{selector, feedback}`). `view` is the
 * bar's logical bounds, the canvas's pixel ratio (default 1) and the page image (device px); draw the page first, labels after. */
export function drawNavigation(kit: CanvasKit, painter: GlassPainter, canvas: Canvas,
  view: {bounds: GlassRect; pixelRatio?: number; source: Image},
  sample: {selector: CalmSelectorState; feedback: GlassFeedback},
  options?: {material?: GlassMaterial; ink?: Image | null}): void;

/** Compose's GlassTabBarAccessory link: the rect a Calm bar's outline fuses with, beside it, from a free
 * button's press and pull (createFreeButton's sample). Bar-local px; returns drawCalmBar's `fuse`:
 * [centre x, centre y from the bar's centre, half width, half height, corner radius, smoothing]. */
export function accessoryFuse(input: {
  bar: {width: number; height: number; pressX?: number; pressY?: number};
  button: GlassRect; transform?: Partial<FreeButtonTransform>; smoothing: number;
}): [number, number, number, number, number, number];
/** Compose's GlassTabBarAccessory.lensFusion: a held lens's own neck to the free button beside the bar.
 * Bar-local device px; returns drawSelector's `fuse`, or null while the lens is not formed (formation < 0.5). */
export function lensFuse(input: {
  bar: {width: number; height: number}; button: GlassRect; formation: number; lensFusion: number;
}): [number, number, number, number, number, number] | null;
/** How much of the neck a formed lens reaching the bar's end has taken over, 0 to 1 (glassAccessoryYield). */
export function accessoryYield(formation: number, endEdge: number, barWidth: number, barHeight: number): number;
/** The largest stadium inside an axis-aligned ellipse: [half width, half height, corner radius]. */
export function inscribedStadium(a: number, b: number): [number, number, number];

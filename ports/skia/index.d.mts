import type {Canvas, CanvasKit, Image} from 'canvaskit-wasm';

export interface GlassShaderSources { material: string; content: string; endpoint: string }
export type CornerRadii = number | readonly [number, number, number, number];
export interface GlassRect { x: number; y: number; width: number; height: number }
export interface GlassSurfaceBounds extends GlassRect { radius?: CornerRadii }
export interface GlassLensBounds { x: number; y: number; width: number; height?: number; magnification?: number }
export interface GlassMaterial {
  dark?: boolean; tintAmount?: number; density?: number; materialize?: number;
  reducedTransparency?: number; increasedContrast?: number;
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

export interface GlassPainter {
  /** Borrows an opaque source. Replace it before deleting the previous image. */
  setSource(image: Image): void;
  /** Low-level painter coordinates are device pixels. */
  drawLens(canvas: Canvas, bounds: GlassLensBounds): void;
  drawSurface(canvas: Canvas, bounds: GlassSurfaceBounds, material?: GlassMaterial): void;
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
export interface GlassLensLayer extends GlassLayer<GlassRect & {magnification: number}> {}
export interface GlassViewport { width: number; height: number; pixelRatio?: number }
export interface GlassSceneOptions extends GlassViewport { reducedMotion?: boolean; sources?: GlassShaderSources }
export interface GlassScene {
  setSource(image: Image): void;
  /** Scene bounds and input displacements are logical pixels. */
  addSurface(bounds: GlassSurfaceBounds, material?: GlassMaterial): GlassSurfaceLayer;
  addLens(bounds: GlassLensBounds): GlassLensLayer;
  resize(viewport: Partial<GlassViewport>, time: number): void;
  setReducedMotion(reduced: boolean, time: number): void;
  /** True requests another animation frame. Draw labels after this, then flush the host surface. */
  draw(canvas: Canvas, time: number, options?: {drawBackdrop?: boolean}): boolean;
  dispose(): void;
}
export function createGlassScene(kit: CanvasKit, options: GlassSceneOptions): GlassScene;

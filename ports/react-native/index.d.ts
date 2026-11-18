import type {ReactElement} from 'react';
import type {SkImage, SkPicture} from '@shopify/react-native-skia';

/** One radius, or four (top-left, top-right, bottom-right, bottom-left), in the canvas's units. */
export type CornerRadii = number | readonly [number, number, number, number];

export interface GlassSurfaceProps {
  /** What is behind the glass, at device resolution, its top-left at the canvas's origin. */
  source: SkImage | null | undefined;
  x: number; y: number; width: number; height: number;
  radius?: CornerRadii;
  dark?: boolean;
  /** 0 to 100; 50 by default. */
  tintAmount?: number;
  /** Replaces the tint ([r, g, b, a], sRGB 0..1), as Compose's `Glass.tint(color)`. */
  tint?: readonly [number, number, number, number] | null;
  /** Toward the light, y up negative; [0, -1] (straight overhead, as every measured resting iOS 27 rim is lit) by default. */
  light?: readonly [number, number];
  /** Whether the material is present, 0 to 1. */
  materialize?: number;
  reducedTransparency?: number;
  increasedContrast?: number;
  /** Device pixels per canvas unit; `PixelRatio.get()` by default. */
  pixelRatio?: number;
}

export interface GlassLensProps {
  source: SkImage | null | undefined;
  x: number; y: number; width: number; height?: number;
  /** 1 to 2.5; 1.25 by default. */
  magnification?: number;
  pixelRatio?: number;
}

/** A rounded piece of glass inside a React Native Skia `<Canvas>`. */
export function GlassSurface(props: GlassSurfaceProps): ReactElement | null;
/** A clear magnifier inside a React Native Skia `<Canvas>`. */
export function GlassLens(props: GlassLensProps): ReactElement | null;

/** The canvas `recordGlass` hands the painter. */
export interface GlassCanvas { readonly __glassCanvas: unique symbol }
export interface GlassRect { x: number; y: number; width: number; height: number }

/** The production painter (ports/skia) over React Native Skia. Device pixels, top-left origin. */
export interface GlassPainter {
  drawSurface(canvas: GlassCanvas, bounds: GlassRect & {radius?: number | readonly [number, number, number, number]},
    material?: Omit<GlassSurfaceProps, 'source' | 'x' | 'y' | 'width' | 'height' | 'radius' | 'pixelRatio'> & {density?: number}): void;
  /** With the Compose clear-lens preset (rim, thin tint, contact shadow) unless `lighting` is false. */
  drawLens(canvas: GlassCanvas, bounds: {x: number; y: number; width: number; height?: number; magnification?: number},
    options?: {lighting?: boolean; shadow?: boolean; density?: number; light?: readonly [number, number]}): void;
  drawCalmBar(canvas: GlassCanvas, bar: GlassRect, options?: {dark?: boolean; density?: number; formation?: number;
    touch?: number; clear?: boolean; materialize?: number}): void;
  dispose(): void;
}
export function createGlassPainter(): GlassPainter;
/** Record painter calls into a picture, in device pixels, over `source`. */
export function recordGlass(painter: GlassPainter, source: SkImage,
  draw: (painter: GlassPainter, canvas: GlassCanvas) => void): SkPicture;
export {bindLiquidGlass} from './core.js';

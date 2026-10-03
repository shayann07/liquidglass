/** Apache-2.0. High-level, framework-independent clear-lens drawing with production SkSL. */
import {compileGlassEffects, clearMaterialUniforms} from './renderer.mjs';

/**
 * Compile once, then draw any number of lenses over one caller-owned Skia Image.
 * Coordinates and image dimensions are device pixels with a top-left origin.
 * This object owns its effects and paint; it borrows, and never deletes, the source image.
 */
export function createGlassPainter(kit, sources) {
  const effects = compileGlassEffects(kit, sources);
  let paint;
  try { paint = new kit.Paint(); }
  catch (error) { effects.dispose(); throw error; }
  let source = null, disposed = false;
  function checkAlive() { if (disposed) throw new Error('Glass painter disposed'); }

  return {
    /** Replace after backdrop changes. Keep the image alive until replacement or disposal. */
    setSource(image) {
      checkAlive();
      if (!image || typeof image.makeShaderOptions !== 'function' ||
          typeof image.width !== 'function' || typeof image.height !== 'function' ||
          !(image.width() > 0) || !(image.height() > 0))
        throw new TypeError('Source must be a live, nonempty CanvasKit Image');
      source = image;
    },

    /** Draw a clear magnifier, preserving the host canvas transform and clip. */
    drawLens(canvas, {x, y, width, height = width, magnification = 1.25}) {
      checkAlive();
      if (!source) throw new Error('Call setSource before drawing glass');
      if (![x,y,width,height,magnification].every(Number.isFinite) || width <= 0 || height <= 0 ||
          magnification < 1 || magnification > 2.5)
        throw new RangeError('Lens requires finite geometry, positive dimensions and 1–2.5 magnification');
      // The material's local padded coordinates must sample the same global page coordinates
      // that the host drew. A local shader matrix is inverted by Skia during sampling.
      const pad = 2;
      const originX = x - pad, originY = y - pad;
      const backdrop = source.makeShaderOptions(
        kit.TileMode.Clamp, kit.TileMode.Clamp, kit.FilterMode.Linear, kit.MipmapMode.None,
        kit.Matrix.translated(-originX, -originY),
      );
      let material;
      try {
        const uniforms = clearMaterialUniforms(effects, {width, height, pad, magnification});
        uniforms.uBackdrop = [-originX, -originY, source.width()-originX, source.height()-originY];
        material = effects.shader('material', uniforms, {content: backdrop, field: backdrop});
        paint.setShader(material);
        canvas.save();
        try {
          canvas.translate(originX, originY);
          canvas.drawRect(kit.XYWHRect(0, 0, width+2*pad, height+2*pad), paint);
        } finally { canvas.restore(); }
      } finally {
        paint.setShader(null);
        material?.delete();
        backdrop.delete();
      }
    },

    dispose() {
      if (!disposed) {
        disposed = true;
        source = null;
        paint.delete();
        effects.dispose();
      }
    },
  };
}

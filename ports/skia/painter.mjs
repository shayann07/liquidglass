/** Apache-2.0. Framework-independent surfaces and lenses using production SkSL. */
import {compileGlassEffects, clearMaterialUniforms} from './renderer.mjs';
import {inAppMaterialUniforms} from './material.mjs';
import {createBackdropSampler} from './backdrop.mjs';
import {glassShaderSources} from './shaders.mjs';

/**
 * Compile once, then draw surfaces and lenses over one caller-owned Skia Image.
 * Coordinates and image dimensions are device pixels with a top-left origin.
 * Production shaders are included; optional sources support advanced/custom shader hosts.
 * This object owns its effects and paint; it borrows, and never deletes, the source image.
 */
export function createGlassPainter(kit, sources = glassShaderSources) {
  const effects = compileGlassEffects(kit, sources);
  let paint;
  try { paint = new kit.Paint(); }
  catch (error) { effects.dispose(); throw error; }
  let source = null, disposed = false, sampler=null;
  function checkAlive() { if (disposed) throw new Error('Glass painter disposed'); }
  // Consumes the temporary backdrop shader; both public drawing paths preserve identical
  // coverage, page coordinates, host canvas state and exception-safe shader lifetime.
  function draw(canvas,uniforms,backdrop,originX,originY) {
    let material;
    try {
      uniforms.uBackdrop=[-originX,-originY,source.width()-originX,source.height()-originY];
      material=effects.shader('material',uniforms,{content:backdrop,field:backdrop});
      paint.setShader(material);canvas.save();
      try {
        canvas.translate(originX,originY);
        canvas.drawRect(kit.XYWHRect(0,0,uniforms.uSize[0]+2*uniforms.uPad,
          uniforms.uSize[1]+2*uniforms.uPad),paint);
      } finally {canvas.restore();}
    } finally {paint.setShader(null);material?.delete();backdrop.delete();}
  }

  return {
    /** Replace after backdrop changes. Keep the image alive until replacement or disposal. */
    setSource(image) {
      checkAlive();
      if (!image || typeof image.makeShaderOptions !== 'function' ||
          typeof image.width !== 'function' || typeof image.height !== 'function' ||
          !(image.width() > 0) || !(image.height() > 0))
        throw new TypeError('Source must be a live, nonempty CanvasKit Image');
      sampler?.reset();source = image;
    },

    /** Draw a rounded in-app material, including its wide tone kernel. Labels stay with the host. */
    drawSurface(canvas,{x,y,width,height,radius=20},options={}) {
      checkAlive();
      if(!source)throw new Error('Call setSource before drawing glass');
      if(![x,y,width,height].every(Number.isFinite)||width<=0||height<=0)
        throw new RangeError('Surface requires finite position and positive dimensions');
      const pad=2,originX=x-pad,originY=y-pad;
      const uniforms=inAppMaterialUniforms(effects,{...options,width,height,radius,pad});
      sampler??=createBackdropSampler(kit);
      const backdrop=sampler.shader(canvas,source,originX,originY,uniforms.uWideKernel);
      uniforms.uWideStrip=backdrop.strip;
      draw(canvas,uniforms,backdrop.shader,originX,originY);
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
      const uniforms = clearMaterialUniforms(effects, {width, height, pad, magnification});
      const backdrop = source.makeShaderOptions(
        kit.TileMode.Clamp, kit.TileMode.Clamp, kit.FilterMode.Linear, kit.MipmapMode.None,
        kit.Matrix.translated(-originX, -originY),
      );
      draw(canvas,uniforms,backdrop,originX,originY);
    },

    dispose() {
      if (!disposed) {
        disposed = true;
        source = null;
        paint.delete();
        sampler?.dispose();sampler=null;
        effects.dispose();
      }
    },
  };
}

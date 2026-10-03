/** Apache-2.0. Experimental adapter for the production SkSL shaders, using CanvasKit 0.42. */
export function compileGlassEffects(kit, sources) {
  const effects = {}, children = {};
  let disposed = false;
  try {
    for (const name of ['material', 'content', 'endpoint']) {
      const source = sources[name];
      if (typeof source !== 'string') throw new TypeError(`Missing ${name} SkSL source`);
      let diagnostic = '';
      const effect = kit.RuntimeEffect.Make(source, message => { diagnostic = message; });
      if (!effect) throw new Error(`${name}: ${diagnostic}`);
      effects[name] = effect;
      children[name] = [...source.matchAll(/uniform\s+shader\s+(\w+)\s*;/g)].map(m => m[1]);
    }
  } catch (error) { Object.values(effects).forEach(e => e.delete()); throw error; }

  return {
    /** Every declared uniform and child must be supplied. Returned shader belongs to caller. */
    shader(name, values, inputs) {
      if (disposed) throw new Error('Glass effects disposed');
      const effect = effects[name];
      if (!effect) throw new RangeError(`Unknown pass: ${name}`);
      const uniforms = new Float32Array(effect.getUniformFloatCount()), known = new Set();
      for (let i = 0; i < effect.getUniformCount(); i++) {
        const key = effect.getUniformName(i), descriptor = effect.getUniform(i);
        known.add(key);
        const value = values[key];
        const components = typeof value === 'number' ? [value] : value;
        const length = descriptor.rows * descriptor.columns;
        if (!components || components.length !== length || !Array.from(components).every(Number.isFinite))
          throw new TypeError(`${key} requires ${length} finite components`);
        uniforms.set(components, descriptor.slot);
      }
      for (const key of Object.keys(values)) if (!known.has(key)) throw new TypeError(`Unknown uniform: ${key}`);
      const bound = children[name].map(key => {
        if (!inputs?.[key]) throw new TypeError(`Missing shader child: ${key}`);
        return inputs[key];
      });
      for (const key of Object.keys(inputs)) if (!children[name].includes(key)) throw new TypeError(`Unknown child: ${key}`);
      return effect.makeShaderWithChildren(uniforms, bound);
    },
    /** Describe the exact ABI for a host binding; no hardcoded byte offsets. */
    layout(name) {
      if (disposed) throw new Error('Glass effects disposed');
      const effect = effects[name];
      if (!effect) throw new RangeError(`Unknown pass: ${name}`);
      return Array.from({length: effect.getUniformCount()}, (_, i) => ({
        name: effect.getUniformName(i), ...effect.getUniform(i),
      }));
    },
    dispose() {
      if (!disposed) { disposed = true; Object.values(effects).forEach(e => e.delete()); }
    },
  };
}

/** Minimal clear material configuration. This is not the Compose toolbar style/host pipeline. */
export function clearMaterialUniforms(effects, {width, height, pad = 32, magnification = 1.25}) {
  if (![width, height, pad, magnification].every(Number.isFinite) || width <= 0 || height <= 0 ||
      pad < 0 || magnification < 1 || magnification > 2.5) throw new RangeError('Invalid material geometry');
  const values = Object.fromEntries(effects.layout('material').map(u =>
    [u.name, u.columns * u.rows === 1 ? 0 : Array(u.columns * u.rows).fill(0)]));
  Object.assign(values, {
    uSize: [width,height], uPad: pad, uBackdrop: [0,0,width+2*pad,height+2*pad],
    uRadii: Array(4).fill(Math.min(width,height)/2), uRefractBand: 24, uRefractDepth: 12,
    uIor: 1.5, uBevelPower: 2, uCornerPower: 2, uFieldRange: 1, uFieldScale: 1,
    uBevel: 2, uLight: [0,-1], uSpecularPow: 4, uCounterLight: 1, uEdgeLight: 1,
    uTint: [1,1,1,0], uProfile: 3, uFormation: 1, uHeldLens: 1,
    uHeldMagnification: 1-1/magnification, uFineShare: 1, uWideScale: .25,
    uTouch: [width/2,height/2], uMaterialize: 1,
    uPoseA: [1,0,0,1], uPoseAInv: [1,0,0,1], uPoseD: [1,1,1,0],
  });
  return values;
}

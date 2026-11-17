/** Apache-2.0. Validation of scene bounds and viewports, shared by the scene and the navigation driver. */

export function viewport({width, height, pixelRatio = 1}) {
  if (![width, height, pixelRatio].every(Number.isFinite) ||
      width <= 0 || height <= 0 || width > 1e7 || height > 1e7 || pixelRatio <= 0 || pixelRatio > 4)
    throw new RangeError('Positive logical viewport dimensions and pixelRatio in (0, 4] required');
  return {width, height, pixelRatio};
}

export function boundsFor(kind, input) {
  const bounds = {...input};
  if (kind === 'lens') { bounds.height ??= bounds.width; bounds.magnification ??= 1.25; }
  if (![bounds.x, bounds.y, bounds.width, bounds.height].every(Number.isFinite) ||
      bounds.width <= 0 || bounds.height <= 0 || bounds.width > 1e7 || bounds.height > 1e7)
    throw new RangeError('Finite position and positive logical bounds required');
  if (kind === 'lens') {
    if (!Number.isFinite(bounds.magnification) || bounds.magnification < 1 || bounds.magnification > 2.5)
      throw new RangeError('Magnification must be 1–2.5');
    bounds.radius = Math.min(bounds.width, bounds.height) / 2;
  } else {
    bounds.radius ??= 20;
    const radii = Array.isArray(bounds.radius) ? bounds.radius : [bounds.radius];
    if ((Array.isArray(bounds.radius) && radii.length !== 4) || !radii.every(r => Number.isFinite(r) && r >= 0))
      throw new RangeError('Radius must be nonnegative, or four nonnegative corner radii');
    bounds.radius = Array.isArray(bounds.radius) ? [...bounds.radius] : bounds.radius;
  }
  return bounds;
}

/** The geometry a Calm interaction takes for a layer's bounds in a viewport. */
export function geometry(bounds, view) {
  return {...bounds, radius: Array.isArray(bounds.radius) ? Math.min(...bounds.radius) : bounds.radius,
    viewportWidth: view.width, viewportHeight: view.height};
}

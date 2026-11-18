/** Apache-2.0. Liquid Glass for React Native Skia, independent of how React Native Skia is loaded.
 *
 * `index.js` binds this to `@shopify/react-native-skia` for apps; the tests bind it to React Native
 * Skia's headless build, so what they check is the code an app runs.
 */
import {createSkiaKit} from './kit.js';
import {createGlassPainter} from './vendor/skia/painter.js';

/**
 * @param {object} deps React Native Skia's `Skia`, its enums (`TileMode`, `FilterMode`, `MipmapMode`,
 *   `BlendMode`, `ClipOp`), its `Picture` and `Group` components, React, and `pixelRatio()`.
 */
export function bindLiquidGlass({Skia, TileMode, FilterMode, MipmapMode, BlendMode, ClipOp, Picture, Group, React,
  pixelRatio = () => 1}) {
  const kit = createSkiaKit(Skia, {TileMode, FilterMode, MipmapMode, BlendMode, ClipOp});

  /** The production painter (ports/skia) drawing through React Native Skia. Device pixels. */
  const createPainter = () => createGlassPainter(kit);

  /**
   * Record glass into an `SkPicture`, in device pixels. `draw(painter, canvas)` calls the painter's
   * `drawSurface`, `drawLens`, `drawCalmBar` or `drawSelector`; `source` is the backdrop they refract.
   */
  function recordGlass(painter, source, draw) {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, source.width(), source.height()));
    painter.setSource(kit.wrap(source));
    draw(painter, kit.canvas(canvas));
    return recorder.finishRecordingAsPicture();
  }

  function useGlass(source, ratio, draw, deps) {
    const painter = React.useMemo(createPainter, []);
    React.useEffect(() => () => painter.dispose(), [painter]);
    const picture = React.useMemo(() => source ? recordGlass(painter, source, (p, c) => draw(p, c, ratio)) : null,
      [painter, source, ratio, ...deps]);
    React.useEffect(() => () => picture?.dispose?.(), [picture]);
    if (!picture) return null;
    // Recorded in device pixels for a sharp rim; drawn back in the canvas's own units.
    return React.createElement(Group, {transform: [{scale: 1 / ratio}]},
      React.createElement(Picture, {picture}));
  }
  const px = (value, ratio) => typeof value === 'number' ? value * ratio : value.map(v => v * ratio);

  /**
   * A rounded piece of glass, inside a React Native Skia `<Canvas>`. Position and size are in the
   * canvas's units. `source` is an `SkImage` of what is behind the glass, at device resolution, its
   * top-left at the canvas's origin: `makeImageFromView` of the views underneath, or an image drawn
   * in the canvas. Labels on the glass stay ordinary React Native views or Skia text drawn after it.
   */
  function GlassSurface({source, x, y, width, height, radius = 20, dark = false, tintAmount = 50, tint = null,
    light, materialize = 1, reducedTransparency = 0, increasedContrast = 0, pixelRatio: ratio = pixelRatio()}) {
    return useGlass(source, ratio, (painter, canvas, r) => painter.drawSurface(canvas,
      {x: x * r, y: y * r, width: width * r, height: height * r, radius: px(radius, r)},
      {dark, tintAmount, tint, light, materialize, reducedTransparency, increasedContrast, density: r}),
    [x, y, width, height, String(radius), dark, tintAmount, String(tint), String(light), materialize,
      reducedTransparency, increasedContrast]);
  }

  /** A clear magnifier over `source`; `magnification` 1 to 2.5. Canvas units. */
  function GlassLens({source, x, y, width, height = width, magnification = 1.25, pixelRatio: ratio = pixelRatio()}) {
    return useGlass(source, ratio, (painter, canvas, r) => painter.drawLens(canvas,
      {x: x * r, y: y * r, width: width * r, height: height * r, magnification}, {density: r}),
    [x, y, width, height, magnification]);
  }

  return {kit, createPainter, recordGlass, GlassSurface, GlassLens};
}

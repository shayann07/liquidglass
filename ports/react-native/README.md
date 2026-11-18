# @shayxo.dev/liquidglass-react-native

Liquid Glass for React Native, drawn by [React Native Skia](https://shopify.github.io/react-native-skia/)
with the library's production shaders: the same SkSL the Compose library runs, measured against iOS
recordings. For Android and React Native Web. iOS is not a target. On iOS 26, use the system's own glass,
for example through `expo-glass-effect`.

```sh
npm install @shayxo.dev/liquidglass-react-native @shopify/react-native-skia
```

![Glass rendered by React Native Skia's headless build: a lens bending the lines, a light and a dark surface](../../review/atlas/react-native-2026-10-19/react-native-glass.png)

*Our render: `npm run example`, React Native Skia 2.14.0 headless (its JSI API over CanvasKit 0.41,
software), 960 × 640 at pixel ratio 2. On Android: the demo app on a Pixel 7 and the Android 17 emulator
([evidence](../../review/atlas/react-native-2026-10-19/README.md)).*

## Use

```jsx
import {Canvas, Image, makeImageFromView} from '@shopify/react-native-skia';
import {GlassSurface, GlassLens} from '@shayxo.dev/liquidglass-react-native';

// What the glass refracts: the views behind it, captured once their content is laid out.
const [backdrop, setBackdrop] = useState(null);
useEffect(() => { makeImageFromView(pageRef).then(setBackdrop); }, []);

<View ref={pageRef} collapsable={false}>{/* the page */}</View>
<Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
  <GlassSurface source={backdrop} x={16} y={height - 100} width={width - 32} height={84} radius={28} dark />
  <GlassLens source={backdrop} x={40} y={120} width={140} magnification={1.5} />
</Canvas>
```

- **`source`** is an **opaque** `SkImage` of what is behind the glass, at device resolution, with its
  top-left at the canvas's origin. Give the captured view a background colour: where the image is
  transparent the glass sees black. `makeImageFromView` of the views underneath gives exactly that. So does an image
  you draw in the same canvas. Capture again when the page changes, not on every frame.
- Positions and sizes are in the canvas's units. The glass is recorded at device resolution
  (`pixelRatio`, `PixelRatio.get()` by default), so the rim stays sharp.
- Labels and icons on the glass stay ordinary React Native views above the canvas, or Skia text drawn
  after the glass.

| `GlassSurface` prop | Default | |
| :-- | :-- | :-- |
| `radius` | 20 | one radius or four (top-left, top-right, bottom-right, bottom-left) |
| `dark` | false | the dark material |
| `tintAmount` | 50 | 0 to 100 |
| `tint` | none | `[r, g, b, a]` in sRGB 0..1, as Compose's `Glass.tint(color)` |
| `light` | `[0, -1]` | toward the light; straight overhead, as every measured resting iOS 27 rim is lit |
| `materialize`, `reducedTransparency`, `increasedContrast` | 1, 0, 0 | 0 to 1 |

`GlassLens` takes `magnification` (1 to 2.5, 1.25 by default).

For the navigation bar and its selector, use the painter directly. `createGlassPainter()` returns the
[CanvasKit port's painter](../skia/README.md) running on React Native Skia (`drawSurface`, `drawLens`,
`drawCalmBar`, `drawSelector`). `recordGlass(painter, source, draw)` records its calls into an
`SkPicture` you draw with `<Picture>`.

## How it works

The painter in [`ports/skia`](../skia/README.md) is written against CanvasKit. React Native Skia's
`Skia` object is nearly the same API. `kit.js` adapts the rest:
- `dispose()` in place of `delete()`;
- numeric enums;
- `SkMatrix` objects;
- offscreen surfaces from `Skia.Surface`.

The painter and the shaders therefore run unchanged; `vendor/skia` holds a byte-for-byte copy, checked
by `npm test`.

## Checks

`npm test` runs React Native Skia's headless build, which is its JSI API over CanvasKit:

- **The same glass, both ways.** Two lenses and two surfaces drawn through the adapter equal the same
  glass drawn by the painter on CanvasKit directly (no channel off by more than 1).
- **`GlassSurface` and `GlassLens` inside a React Native Skia tree.** They change the page under them
  and leave the rest alone.
- **Lifetime.** Disposing twice is safe, and a shader that does not compile is reported.
- **Types.** `tsc` compiles the declared types.

## Limits

- **Checked on one phone.** The automated checks run React Native Skia's web implementation; the
  demo app ran on a Pixel 7 and the Android 17 emulator, inspected by eye.
- **The backdrop is a snapshot.** Glass refracts the image you give it. A page that scrolls under the
  glass needs a new snapshot, or a backdrop drawn in the same Skia canvas.
- **No touch response yet.** The press and pull motion of the Compose library is in the painter's
  controllers (`createCalmInteraction` in `ports/skia`). It is not wired into these components.

Apache-2.0. LICENSE and NOTICE are the repository's copies.

# LiquidGlass for Unity

Liquid Glass for Unity: the liquidglass production material, the same shader the Compose library runs and
measures against iOS recordings, as a Unity shader. It works on a quad or a UI Image, with a
`GlassSurface` component that sets the material from the glass's size on screen. Built-in render
pipeline (through a GrabPass) and URP (through the opaque texture). iOS is not a target.

> **Unverified in Unity.** Unity is not installed where this package is developed. What is and is not
> checked is listed [below](#what-is-verified); please report what you see
> ([issue](https://github.com/shayann07/liquidglass/issues/new/choose)).

![The generated shader rendered on Direct3D 11's WARP software device: a light card and a dark bar over colour bands and a checker row](https://raw.githubusercontent.com/shayann07/liquidglass/main/review/atlas/unity-2026-10-20/unity-warp.png)

*Our render: the generated HLSL compiled by FXC (`ps_5_0`) and drawn on D3D11 WARP by `ports/unity/Checks`,
through the same screen mapping the Unity shaders use. This is not a Unity capture.*

## Install

Package Manager → **+** → **Add package from git URL**:

```
https://github.com/shayann07/liquidglass.git?path=/ports/unity/com.shayxo.liquidglass
```

Unity 2022.3 or newer. It depends on `com.unity.ugui`.

## Use

**Built-in pipeline.** Create a material with the shader **LiquidGlass/Surface**. Put it on a quad in
front of the camera, or on a UI **Image** on a **Screen Space - Camera** or **World Space** canvas. Add
**LiquidGlass → Glass Surface** to the same GameObject.

**URP.** Use **LiquidGlass/Surface (URP)** instead, and turn on **Opaque Texture** in the URP asset.
URP's glass refracts opaque geometry; transparent objects and other glass are not in that texture.

```csharp
var glass = quad.AddComponent<LiquidGlass.GlassSurface>();
glass.radius = 28;        // dp
glass.dark = true;
glass.tintAmount = 50;    // 0 to 100
glass.light = new Vector2(0, -1);   // iOS 27's measured overhead light
```

| `GlassSurface` | Default | |
| :-- | :-- | :-- |
| `radius` | 20 | corner radius in dp, clamped to half the shorter side |
| `dark` | false | the dark material |
| `tintAmount` | 50 | 0 to 100 |
| `light` | (-0.35, -0.94) | toward the light |
| `useTint`, `tint` | off | replace the tint; alpha is its opacity |
| `materialize`, `reducedTransparency`, `increasedContrast` | 1, 0, 0 | 0 to 1 |
| `density` | 0 | device pixels per dp; 0 uses `Screen.dpi / 160` |
| `targetCamera` | none | the camera that renders the glass; else the canvas's camera, else `Camera.main` |

The quad is the padded layer: the glass sits 2 device pixels inside its edge, room for the outer
contour. Labels on the glass are ordinary UI drawn after it.

A **Screen Space - Overlay** canvas draws after every camera, so it has nothing to refract; the
component turns itself off there with a warning.

## How it works

- **The shader is generated.** `tool/generate_shader.mjs` translates `ports/skia/shaders/material.sksl`,
  the export of the Compose library's material shader, into `Runtime/Shaders/LiquidGlassMaterial.hlsl`.
  The translation is mechanical: types, three intrinsics, single-argument constructors as casts, HLSL
  keywords renamed. `--check` fails when the copy drifts.
- **The backdrop is the screen.** The Built-in shader grabs the screen (`GrabPass`); the URP shader
  reads `_CameraOpaqueTexture`. `LiquidGlassScreen.hlsl` maps the material's layer pixels onto that
  texture through screen-space derivatives, so a refracted read lands in the right place at any scale
  or rotation.
- **One part is approximated: the wide tone kernel.** Thirteen Gaussian taps of the same sigma, as in
  the Flutter and Qt ports. It sets the material's tone, never its detail.
- **The material's numbers are shared.** `Runtime/GlassMaterial.cs` ports the in-app material and is
  checked against the fixture every port is checked against.

## What is verified

| Checked (CI, `ports/unity/Checks` on Windows) | Not checked |
| :-- | :-- |
| `GlassMaterial.cs` equals the CanvasKit port's material for three surfaces, to 1e-6 | Unity itself: the package has never been imported into a Unity project |
| The generated HLSL compiles with FXC as `ps_5_0`, the compiler and profile Unity uses for Direct3D 11 | The `.shader` wrappers (ShaderLab), and Unity's other targets: Vulkan, Metal, OpenGL ES |
| It renders on D3D11 WARP through `LiquidGlassScreen.hlsl`: glass changes the pixels under it, the dark bar darkens, the light card stays light, pixels outside are untouched | `GrabPass`, URP's opaque texture, and UI canvases |
| The generated files and the `.meta` files are current (`--check`) | `GlassSurface.cs` (it uses UnityEngine and does not compile without Unity) |

Help wanted: [import it into a Unity project and report](https://github.com/shayann07/liquidglass/issues?q=is%3Aopen+label%3Aneeds-device).

## Checks

```bash
node tool/generate_shader.mjs --check
node tool/write_meta.mjs --check
dotnet run --project Checks -- out     # Windows: material, FXC, WARP render; saves out/unity-warp.png
```

Apache-2.0. Not affiliated with Apple or Unity Technologies.

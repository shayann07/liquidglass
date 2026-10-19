// Apache-2.0. Shared by the Built-in and URP shaders: maps the material's layer pixels onto a screen
// texture (the GrabPass copy or URP's opaque texture) through screen-space derivatives, so a refracted
// read lands where the glass's own pixel grid says, whatever the quad's scale or rotation.
#ifndef LIQUIDGLASS_SCREEN_INCLUDED
#define LIQUIDGLASS_SCREEN_INCLUDED

float2 _LayerSize;  // the padded glass in device pixels (GlassSurface.cs)

static float2 lgScreenUV;   // this fragment's screen-texture uv
static float2 lgLayerPx;    // this fragment's layer pixel
static float2x2 lgJacobian; // d(screen uv) / d(layer px)

// The layer pixel for a mesh uv: x right, y down (uv's v points up in Unity).
float2 LiquidGlassLayerPixel(float2 uv) { return float2(uv.x, 1.0 - uv.y) * _LayerSize; }

// Call once per fragment, outside any branch or loop (it takes derivatives).
void LiquidGlassBeginFragment(float2 layerPx, float2 screenUV)
{
    float2 dpx = ddx(layerPx), dpy = ddy(layerPx), dux = ddx(screenUV), duy = ddy(screenUV);
    float det = dpx.x * dpy.y - dpy.x * dpx.y;
    float2x2 inverseP = float2x2(dpy.y, -dpy.x, -dpx.y, dpx.x) / (abs(det) > 1e-12 ? det : 1e-12);
    float2x2 u = float2x2(dux.x, duy.x, dux.y, duy.y);
    lgJacobian = mul(u, inverseP);
    lgScreenUV = screenUV;
    lgLayerPx = layerPx;
}

// Where the layer pixel p falls on the screen texture.
float2 LiquidGlassScreenUV(float2 p) { return lgScreenUV + mul(lgJacobian, p - lgLayerPx); }

#endif // LIQUIDGLASS_SCREEN_INCLUDED

package com.wexpa.liquidglass

/**
 * The glass shader, in the SkSL dialect shared by AGSL (Android 13+) and Skia (Desktop).
 *
 * The material is built from a signed distance field of the panel's own shape. Everything
 * optical falls out of that one field:
 *
 *  - **Distance** to the edge drives how strongly the backdrop is bent, so refraction is a
 *    band that hugs the rim and fades to nothing in the middle of the panel — which is what
 *    a thick piece of glass with a rounded bevel actually does.
 *  - **The gradient of the field** is the outward surface normal. It gives the refraction its
 *    direction and the specular its angle for free, and it stays correct around corners,
 *    where a per-edge approximation visibly breaks.
 *
 * On top of that sit three cosmetic layers the eye reads as glass: a bright rim where the
 * bevel faces the light, a dark inner line that reads as thickness, and a tint whose strength
 * responds to how bright the backdrop behind it is.
 */
internal const val GLASS_SHADER_SOURCE = """
uniform shader content;        // backdrop, already blurred by the caller

uniform float2  uSize;         // panel size, px
uniform float4  uRadii;        // corner radii: top-left, top-right, bottom-right, bottom-left
uniform float   uRefractBand;  // px from the rim over which refraction acts
uniform float   uRefractDepth; // peak displacement at the rim, px
uniform float   uBevel;        // px width of the lit bevel
uniform float2  uLight;        // unit vector toward the light
uniform float   uSpecular;     // rim highlight intensity, 0..1
uniform float   uSpecularPow;  // rim highlight tightness
uniform float4  uTint;         // rgb + strength
uniform float   uInnerShadow;  // strength of the inner thickness line
uniform float   uAdaptive;     // how much backdrop luminance modulates the tint

// Signed distance to a rounded rectangle. Negative inside, zero on the edge.
// Per-corner radii, so the shape can be a pill, a card, or anything between.
float sdRoundRect(float2 p, float2 halfSize, float4 r) {
    // Pick the radius for the quadrant this point is in.
    float2 rr = (p.x > 0.0) ? r.yz : r.xw;
    float radius = (p.y > 0.0) ? rr.y : rr.x;
    radius = min(radius, min(halfSize.x, halfSize.y));
    float2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - radius;
}

// Rec. 709 luminance; used to decide whether the glass should darken or lighten.
float luma(half3 c) {
    return dot(float3(c), float3(0.2126, 0.7152, 0.0722));
}

half4 main(float2 coord) {
    float2 halfSize = uSize * 0.5;
    float2 p = coord - halfSize;

    float d = sdRoundRect(p, halfSize, uRadii);

    // One pixel of feather on the outside edge, so the panel is antialiased without
    // needing a separate clip pass.
    float coverage = 1.0 - smoothstep(-1.0, 0.5, d);
    if (coverage <= 0.0) {
        return half4(0.0);
    }

    // Outward normal, from the gradient of the distance field. Central differences over one
    // pixel; correct through the corners, where a per-edge normal would pop.
    float e = 1.0;
    float dx = sdRoundRect(p + float2(e, 0.0), halfSize, uRadii)
             - sdRoundRect(p - float2(e, 0.0), halfSize, uRadii);
    float dy = sdRoundRect(p + float2(0.0, e), halfSize, uRadii)
             - sdRoundRect(p - float2(0.0, e), halfSize, uRadii);
    float2 n = normalize(float2(dx, dy) + float2(1e-6));

    // Depth into the panel from the nearest edge.
    float depth = max(-d, 0.0);

    // Refraction. The band is normalised so 1 sits on the rim and 0 at the inner limit, then
    // shaped so the bend accelerates toward the edge the way a bevel's curvature does. A
    // linear ramp here looks like a gradient, not like glass.
    float t = clamp(1.0 - depth / max(uRefractBand, 0.001), 0.0, 1.0);
    float bend = t * t * t;

    // Sample from beyond the rim, so the surroundings are drawn inward and compressed into
    // the bevel — the "the background wraps around the edge" read.
    float2 refracted = coord + n * bend * uRefractDepth;
    half4 bg = content.eval(refracted);

    // Tint. Adaptive: over a bright backdrop the glass leans darker to keep whatever sits on
    // it legible, and over a dark backdrop it lifts. uAdaptive of 0 gives a fixed tint.
    float bgLuma = luma(bg.rgb);
    float adapt = mix(1.0, 0.55 + bgLuma * 0.9, uAdaptive);
    float tintStrength = clamp(uTint.a * adapt, 0.0, 1.0);
    half3 col = mix(bg.rgb, half3(uTint.rgb), half(tintStrength));

    // Inner thickness line: a thin darkening just inside the rim. This is what stops the
    // panel reading as a flat translucent rectangle.
    float inner = smoothstep(uBevel * 2.0, 0.0, depth) - smoothstep(uBevel, 0.0, depth);
    col -= half3(half(inner * uInnerShadow));

    // Specular. The bevel is lit where its normal faces the light; the highlight is confined
    // to the bevel band and tightened by a power curve.
    float rim = smoothstep(uBevel, 0.0, depth);
    float facing = max(dot(n, uLight), 0.0);
    float spec = pow(facing, uSpecularPow) * rim * uSpecular;

    // A weaker counter-highlight on the opposite rim, which is what keeps a real bead of
    // glass from looking lit from one side only.
    float counter = pow(max(dot(n, -uLight), 0.0), uSpecularPow * 1.6) * rim * uSpecular * 0.35;

    col += half3(half(spec + counter));

    return half4(col, 1.0) * half(coverage);
}
"""

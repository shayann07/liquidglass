package com.wexpa.liquidglass

/** How many panels one container can fuse. Fixed because SkSL needs unrollable loops. */
internal const val MAX_GLASS_MEMBERS = 8

/**
 * The container shader: several panels rendered as one body of glass.
 *
 * This is the behaviour that makes the material read as liquid rather than as frosted
 * plastic. Each member contributes its own distance field and they are combined with a
 * *smooth* minimum instead of a plain one. A plain `min` is a hard union — two panels stay
 * two panels that happen to touch. The smooth minimum blends the fields within [uMerge] of
 * each other, so as panels approach, a neck grows between them and they fuse into a single
 * outline; as they separate, the neck thins and snaps. Nothing animates the merge — it is a
 * consequence of the geometry, which is why it stays correct at any speed or angle.
 *
 * Every member is evaluated on every pixel. That is what allows the fields to interact at
 * all, and it is why the member count is capped.
 */
internal val GLASS_CONTAINER_SHADER_SOURCE = """
uniform shader content;

uniform float2  uSize;
uniform float4  uRect[$MAX_GLASS_MEMBERS];   // x, y, w, h — panel-local
uniform float   uRadius[$MAX_GLASS_MEMBERS]; // corner radius per member
uniform float   uCount;                      // active members; the rest are collapsed
uniform float   uMerge;                      // px over which neighbouring fields fuse

uniform float   uRefractBand;
uniform float   uRefractDepth;
uniform float   uBevel;
uniform float2  uLight;
uniform float   uSpecular;
uniform float   uSpecularPow;
uniform float4  uTint;
uniform float   uInnerShadow;
uniform float   uAdaptive;

float sdRoundRectAt(float2 p, float4 rect, float radius) {
    float2 halfSize = rect.zw * 0.5;
    // A collapsed slot must never win the minimum.
    if (halfSize.x <= 0.0 || halfSize.y <= 0.0) {
        return 1e6;
    }
    float2 centre = rect.xy + halfSize;
    float2 q = abs(p - centre) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - radius;
}

// Polynomial smooth minimum. At k = 0 this is a plain union; as k grows the two surfaces
// reach for each other and meet in a fillet rather than a crease.
float smin(float a, float b, float k) {
    if (k <= 0.0) {
        return min(a, b);
    }
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float fieldAt(float2 p) {
    float d = 1e6;
    for (int i = 0; i < $MAX_GLASS_MEMBERS; i++) {
        if (float(i) < uCount) {
            d = smin(d, sdRoundRectAt(p, uRect[i], uRadius[i]), uMerge);
        }
    }
    return d;
}

float luma(half3 c) {
    return dot(float3(c), float3(0.2126, 0.7152, 0.0722));
}

half4 main(float2 coord) {
    float d = fieldAt(coord);

    float coverage = 1.0 - smoothstep(-1.0, 0.5, d);
    if (coverage <= 0.0) {
        return half4(0.0);
    }

    // Normal from the gradient of the *merged* field, so the bevel follows the fused outline
    // through the neck between two panels rather than tracing either one's own edge.
    float e = 1.0;
    float2 n = normalize(float2(
        fieldAt(coord + float2(e, 0.0)) - fieldAt(coord - float2(e, 0.0)),
        fieldAt(coord + float2(0.0, e)) - fieldAt(coord - float2(0.0, e))
    ) + float2(1e-6));

    float depth = max(-d, 0.0);
    float t = clamp(1.0 - depth / max(uRefractBand, 0.001), 0.0, 1.0);
    float bend = t * t * t;

    half4 bg = content.eval(coord + n * bend * uRefractDepth);

    float bgLuma = luma(bg.rgb);
    float adapt = mix(1.0, 0.55 + bgLuma * 0.9, uAdaptive);
    half3 col = mix(bg.rgb, half3(uTint.rgb), half(clamp(uTint.a * adapt, 0.0, 1.0)));

    float inner = smoothstep(uBevel * 2.0, 0.0, depth) - smoothstep(uBevel, 0.0, depth);
    col -= half3(half(inner * uInnerShadow));

    float rim = smoothstep(uBevel, 0.0, depth);
    float spec = pow(max(dot(n, uLight), 0.0), uSpecularPow) * rim * uSpecular;
    float counter = pow(max(dot(n, -uLight), 0.0), uSpecularPow * 1.6) * rim * uSpecular * 0.35;
    col += half3(half(spec + counter));

    return half4(col, 1.0) * half(coverage);
}
"""

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
 *
 * The optics match [GLASS_SHADER_SOURCE] — padded sampling, ground-composited reads, dispersion
 * at the rim and a lit rather than outlined edge — so a member looks like the same material
 * whether or not it happens to be inside a container.
 */
internal val GLASS_CONTAINER_SHADER_SOURCE = """
uniform shader content;

uniform float2  uSize;
uniform float   uPad;                        // px of backdrop recorded beyond each edge
uniform float4  uBackdrop;                   // region of the layer that holds recorded pixels
uniform float3  uBase;                       // opaque ground the backdrop is painted on
uniform float4  uRect[$MAX_GLASS_MEMBERS];   // x, y, w, h — panel-local
uniform float   uRadius[$MAX_GLASS_MEMBERS]; // corner radius per member
uniform float   uCount;                      // active members; the rest are collapsed
uniform float   uMerge;                      // px over which neighbouring fields fuse

uniform float   uRefractBand;
uniform float   uRefractDepth;
uniform float   uAberration;
uniform float   uBlur;
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
    float r = min(radius, min(halfSize.x, halfSize.y));
    float2 q = abs(p - centre) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
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
            // Only fuse against a slot that is really there: sminning against the 1e6 seed
            // would drag the first member's field outward by uMerge in every direction.
            float s = sdRoundRectAt(p, uRect[i], uRadius[i]);
            d = (d > 1e5) ? s : smin(d, s, uMerge);
        }
    }
    return d;
}

half3 backdropAt(float2 p) {
    float2 q = clamp(p, uBackdrop.xy, uBackdrop.zw);
    half4 c = content.eval(q);
    // The recorded layer is filled with the ground before the backdrop goes into it, so it is
    // opaque by construction and any shortfall here is filtering error at its own boundary
    // rather than real transparency. Dividing it out reconstructs the colour; compositing it
    // toward the ground instead paints a halo of the ground colour around every panel.
    return (c.a > half(0.004)) ? c.rgb / c.a : half3(uBase);
}

float2 rotate(float2 v, float2 rot) {
    return float2(v.x * rot.x - v.y * rot.y, v.x * rot.y + v.y * rot.x);
}

// A cheap per-pixel angle. Any hash with no visible structure will do; this one is the
// standard three-round float shuffle, chosen because it needs no texture and no state.
float2 tapRotation(float2 coord) {
    float3 q = fract(float3(coord.x, coord.y, coord.x) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    float a = fract((q.x + q.y) * q.z) * 6.2831853;
    return float2(cos(a), sin(a));
}

// See the note on blurredAt in GlassShaderSource: the blur belongs here rather than upstream,
// so the fused rim keeps a legible image of what lies just outside it, and the tap pattern is
// rotated per pixel so that too few taps read as noise rather than as ghosts.
half3 blurredAt(float2 p, float radius, float2 rot) {
    if (radius < 0.5) {
        return backdropAt(p);
    }
    float inner = radius * 0.55;
    half3 sum = backdropAt(p);
    sum += backdropAt(p + inner * rotate(float2( 1.000,  0.000), rot));
    sum += backdropAt(p + inner * rotate(float2( 0.500,  0.866), rot));
    sum += backdropAt(p + inner * rotate(float2(-0.500,  0.866), rot));
    sum += backdropAt(p + inner * rotate(float2(-1.000,  0.000), rot));
    sum += backdropAt(p + inner * rotate(float2(-0.500, -0.866), rot));
    sum += backdropAt(p + inner * rotate(float2( 0.500, -0.866), rot));
    sum += backdropAt(p + radius * rotate(float2( 0.866,  0.500), rot));
    sum += backdropAt(p + radius * rotate(float2( 0.000,  1.000), rot));
    sum += backdropAt(p + radius * rotate(float2(-0.866,  0.500), rot));
    sum += backdropAt(p + radius * rotate(float2(-0.866, -0.500), rot));
    sum += backdropAt(p + radius * rotate(float2( 0.000, -1.000), rot));
    sum += backdropAt(p + radius * rotate(float2( 0.866, -0.500), rot));
    float mid = radius * 0.8;
    sum += backdropAt(p + mid * rotate(float2( 0.966,  0.259), rot));
    sum += backdropAt(p + mid * rotate(float2( 0.259,  0.966), rot));
    sum += backdropAt(p + mid * rotate(float2(-0.707,  0.707), rot));
    sum += backdropAt(p + mid * rotate(float2(-0.966, -0.259), rot));
    sum += backdropAt(p + mid * rotate(float2(-0.259, -0.966), rot));
    sum += backdropAt(p + mid * rotate(float2( 0.707, -0.707), rot));
    return sum * half(1.0 / 19.0);
}

float luma(half3 c) {
    return dot(float3(c), float3(0.2126, 0.7152, 0.0722));
}

half3 toneMappedTint(half3 tint, float bgLuma) {
    float g = clamp(bgLuma, 0.0, 1.0);
    float value = mix(1.10, 0.88, g);
    float sat   = mix(0.88, 1.06, g);
    float mean  = dot(float3(tint), float3(1.0 / 3.0));
    half3 shifted = half3(half(mean)) + (tint - half3(half(mean))) * half(sat);
    return clamp(shifted * half(value), half3(0.0), half3(1.0));
}

half4 main(float2 coord) {
    float2 local = coord - float2(uPad);
    float d = fieldAt(local);

    float coverage = 1.0 - smoothstep(-0.75, 0.75, d);
    if (coverage <= 0.0) {
        return half4(0.0);
    }

    // Normal from the gradient of the *merged* field, so the bevel follows the fused outline
    // through the neck between two panels rather than tracing either one's own edge.
    float e = 1.0;
    float2 n = normalize(float2(
        fieldAt(local + float2(e, 0.0)) - fieldAt(local - float2(e, 0.0)),
        fieldAt(local + float2(0.0, e)) - fieldAt(local - float2(0.0, e))
    ) + float2(1e-6));

    float depth = max(-d, 0.0);
    float t = clamp(1.0 - depth / max(uRefractBand, 0.001), 0.0, 1.0);
    float bend = t * t * t;
    float2 push = n * bend * uRefractDepth;

    float split = uAberration * bend;
    half3 sharp = half3(
        backdropAt(coord + push * (1.0 + split)).r,
        backdropAt(coord + push).g,
        backdropAt(coord + push * (1.0 - split)).b
    );
    half3 bg = mix(blurredAt(coord + push, uBlur * (1.0 - t), tapRotation(coord)), sharp, half(t * t));

    float bgLuma = luma(bg);
    half3 tinted = toneMappedTint(half3(uTint.rgb), bgLuma);
    half3 col = mix(bg, tinted, half(clamp(uTint.a, 0.0, 1.0)));

    float facing = dot(n, uLight);
    float bevelBand = smoothstep(uBevel, 0.0, depth);

    float innerLine = max(smoothstep(uBevel * 2.2, 0.0, depth) - bevelBand, 0.0);
    col -= half3(half(innerLine * uInnerShadow));

    float spec = pow(max(facing, 0.0), uSpecularPow) * bevelBand * uSpecular;
    float counter = pow(max(-facing, 0.0), uSpecularPow * 1.6) * bevelBand * uSpecular * 0.35;
    float edge = smoothstep(2.0, 0.0, depth);
    float edgeLine = edge * uSpecular * (0.07 + 0.5 * max(facing, 0.0));
    col += half3(half(spec + counter + edgeLine));

    return half4(clamp(col, half3(0.0), half3(1.0)), 1.0) * half(coverage);
}
"""

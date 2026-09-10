package com.wexpa.liquidglass

/**
 * The content pass: an element's own content, seen through its glass.
 *
 * Some content is not *on* a panel but *in* it — the symbol a selection lens is crossing, what a
 * magnifier is held over. Apple's tab bar does this: as the indicator slides across a symbol,
 * the symbol is seen through the lens, bent and colour-split where the rim refracts it, and it
 * keeps its full colour while it is — the lens dims the list behind it, not the symbol inside it.
 *
 * So the content cannot simply be recorded into the backdrop: it would then be tinted, dimmed
 * and blurred with everything else the glass shows. It gets its own pass instead, through the
 * **same** distance field, bevel and Snell deviation as [GLASS_SHADER_SOURCE], with the same
 * dispersion and the same touch magnifier, and nothing else — no scatter, no tint, no lighting.
 * The result is premultiplied and clipped to the shape, and is drawn over the material.
 *
 * Kept in lockstep with the geometry half of the panel shader: any change to `sdShape`,
 * `bevelSlope`, `snellShift` or the `bend` construction there has to land here too, or the
 * content will bend differently from the backdrop it sits in.
 */
internal const val GLASS_CONTENT_SHADER_SOURCE = """
uniform shader content;        // the element's content, transparent, recorded with uPad around it
uniform shader field;          // sampled distance field, used only when uShapeKind is 1

uniform float2  uSize;
uniform float   uPad;
uniform float4  uRadii;
uniform float   uCornerPower;
uniform float   uShapeKind;
uniform float   uFieldRange;
uniform float   uFieldScale;

uniform float   uRefractBand;
uniform float   uRefractDepth;
uniform float   uIor;
uniform float   uBevelPower;
uniform float   uAberration;
uniform float   uScale;
uniform float   uMaterialize;

uniform float2  uTouch;
uniform float   uTouchAmt;

float lnNorm(float2 v, float n) {
    if (n <= 2.001 && n >= 1.999) {
        return length(v);
    }
    return pow(pow(v.x, n) + pow(v.y, n), 1.0 / n);
}

float sdRoundRect(float2 p, float2 halfSize, float4 r) {
    float2 rr = (p.x > 0.0) ? r.yz : r.xw;
    float radius = (p.y > 0.0) ? rr.y : rr.x;
    radius = min(radius, min(halfSize.x, halfSize.y));
    float2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + lnNorm(max(q, float2(0.0)), uCornerPower) - radius;
}

float sdShape(float2 p, float2 halfSize) {
    if (uShapeKind < 0.5) {
        return sdRoundRect(p, halfSize, uRadii);
    }
    float2 layerCoord = (p + halfSize + float2(uPad)) * uFieldScale;
    float stored = float(field.eval(layerCoord).r);
    return (stored - 0.5) * 2.0 * uFieldRange;
}

float bevelSlope(float e, float p) {
    float u = 1.0 - clamp(e, 0.002, 0.998);
    float up = pow(u, p);
    return pow(u, p - 1.0) / pow(max(1.0 - up, 1e-4), 1.0 - 1.0 / p);
}

float snellShift(float slope, float ior) {
    float inv = inversesqrt(1.0 + slope * slope);
    float si = slope * inv;
    float ci = inv;
    float st = si / max(ior, 1.0001);
    float ct = sqrt(max(1.0 - st * st, 0.0));
    return (si * ct - ci * st) / max(ci * ct + si * st, 1e-4);
}

half4 main(float2 coord) {
    float2 local = coord - float2(uPad);
    float2 halfSize = uSize * 0.5;
    float2 p = local - halfSize;

    float d = sdShape(p, halfSize);
    float coverage = 1.0 - smoothstep(-0.75, 0.75, d);
    if (coverage <= 0.0) {
        return half4(0.0);
    }

    float scale = clamp(uScale, 0.0, 1.0);

    float eps = clamp(uRefractBand * 0.3, 1.0, 16.0);
    float2 ex = float2(eps, 0.0);
    float2 ey = float2(0.0, eps);
    float2 g = float2(
        sdShape(p + ex, halfSize) - sdShape(p - ex, halfSize),
        sdShape(p + ey, halfSize) - sdShape(p - ey, halfSize));
    float gLen = length(g);
    float2 n = g / max(gLen, 1e-5);
    float axisFade = smoothstep(0.15, 0.80, gLen / (2.0 * eps));

    float depth = max(-d, 0.0);
    float e = clamp(depth / max(uRefractBand, 0.001), 0.0, 1.0);
    float pw = max(uBevelPower, 1.5);
    float slope = bevelSlope(e, pw);

    float bend = snellShift(slope, uIor) / max(snellShift(bevelSlope(0.0, pw), uIor), 1e-4);
    bend *= smoothstep(1.0, 0.75, e) * axisFade * smoothstep(0.0, 1.5, depth);
    bend = clamp(bend, 0.0, 1.0);

    float mat = clamp(uMaterialize, 0.0, 1.0);
    float lens = uRefractDepth * mix(0.85, 1.25, scale) * mat;

    float2 touchDelta = local - uTouch;
    float touchSigma = max(min(uSize.x, uSize.y), 1.0);
    float touchFall = exp(-dot(touchDelta, touchDelta) / (touchSigma * touchSigma))
        * clamp(uTouchAmt, 0.0, 1.0);
    float2 base = coord - touchDelta * (touchFall * 0.17);

    float2 push = n * bend * lens;
    float split = uAberration * bend;

    // Three taps of a transparent image, each channel from its own. Coverage is the widest of
    // the three, so where one channel's tap has left the glyph and another's has not, the
    // pixel keeps the colour that is still there — which is what a colour fringe is.
    half4 r = content.eval(base + push * (1.0 - split));
    half4 gg = content.eval(base + push);
    half4 b = content.eval(base + push * (1.0 + split));
    half a = max(max(r.a, gg.a), b.a);
    half3 rgb = half3(r.r, gg.g, b.b);
    return half4(min(rgb, half3(a)), a) * half(coverage);
}
"""

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
uniform float4  uFuse;
uniform float2  uFuseShape;
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
uniform float   uProfile;
uniform float   uFormation;
uniform float   uHeldLens;     // GlassProfile.Held: how far the tab-bar lens has formed, 0 to 1

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

float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float sdShape(float2 p, float2 halfSize) {
    if (uShapeKind < 0.5) {
        float d = sdRoundRect(p, halfSize, uRadii);
        if (uFuseShape.y > 0.0) {
            d = smin(d, sdRoundRect(p - uFuse.xy, uFuse.zw, float4(uFuseShape.x)), uFuseShape.y);
        }
        return d;
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

// The lens under a finger on a tab bar (FINDINGS 19; measured model, section 2c). Over the outer
// half of the band a pixel at depth u shows the exterior from 1.3u - 0.29 W: the content just
// outside the rim is pulled in and compressed 1.3x (the bar's own edge line lands 10 px inside
// the lens's rim, the header text above an App Store bar becomes a thin stripe along it). From
// half the band inward the content is shown where it is. The source content between 0.36 W and
// 0.5 W is never displayed: the seam hides the bar's end and the gap beside it when the lens
// hangs over the end of a bar. Blended from identity, so a resting indicator in the same family
// has no optics at all.
float heldSource(float u) {
    return (u < 0.5) ? (1.3 * u - 0.29) : u;
}

// The lens of chrome at rest, at the curved parts of an outline only (FINDINGS 18). Measured on
// the Photos toolbar's 70 px ends at Tint 0, 50 and 62 in both appearances and over both targets
// (identical, spread under 2 px) and on the Lock Screen's round buttons: a pixel at depth
// u = d/W inside a rounded end shows the ring 0.55 W (0.33 R) inside, the same ring for the
// whole of 0.14-0.38 W, then eases to identity by 0.76 W (0.46 R); the outer 0.14 W shows
// slightly deeper content again, a vestige of the fold's mirror. Every ring in the outer third
// showing the same source ring is what makes the stripes turn into concentric arcs at an end.
// A straight run shows none of it: the content under it sits where it is (the dock's top edge
// leaves a boundary 30 px inside in place, and folds nothing over the block above it).
float restSource(float u) {
    if (u >= 0.76) {
        return u;
    } else if (u >= 0.62) {
        return 0.64 + 0.857 * (u - 0.62);
    } else if (u >= 0.38) {
        return 0.55 + 0.375 * (u - 0.38);
    } else if (u >= 0.14) {
        return 0.55;
    }
    return 0.55 + 0.93 * (0.14 - u);
}

// Whether a point's nearest bit of outline is a corner arc (1) or a straight run (0), with a
// short blend along the run so the rest lens does not begin at a seam. Inside a corner's own
// quadrant both q components are positive; along a run one is negative by the distance to the
// arc. A circle is all arc. Radii are clamped as sdRoundRect clamps them.
float cornerWeight(float2 p, float2 halfSize, float4 r) {
    float2 rr = (p.x > 0.0) ? r.yz : r.xw;
    float radius = (p.y > 0.0) ? rr.y : rr.x;
    radius = min(radius, min(halfSize.x, halfSize.y));
    float2 q = abs(p) - halfSize + radius;
    float soft = max(radius * 0.2, 1.0);
    return smoothstep(-soft, 0.0, min(q.x, q.y));
}

float foldSource(float u, float formation, float corner) {
    float full;
    if (u >= 0.9) {
        full = u;
    } else if (u >= 0.52) {
        full = 0.71 + 0.5 * (u - 0.52);
    } else if (u >= 0.28) {
        full = 0.70;
    } else {
        full = 0.71 + 1.2857 * (0.28 - u);
    }
    // At rest a straight run shows almost nothing: a fast sheet pull shows only a 2-7 px inward
    // offset across its 90 px band (FINDINGS 1b-iii), and the dock's top edge leaves what is
    // under it in place. A corner arc at rest shows the measured rest lens (restSource). Both
    // give way to the full fold as the lens forms under a tracked drag.
    float straight = u + 0.06 * (1.0 - u) * (1.0 - u);
    float rest = mix(straight, restSource(u), clamp(corner, 0.0, 1.0));
    return mix(rest, full, clamp(formation, 0.0, 1.0));
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
    float2 pushR = push * (1.0 - split);
    float2 pushB = push * (1.0 + split);
    if (uProfile >= 0.5) {
        // The same inward fold the backdrop goes through; see the panel shader.
        float W = max(uRefractBand, 0.001);
        float u = clamp(depth / W, 0.0, 1.0);
        float corner = (uShapeKind < 0.5) ? cornerWeight(p, halfSize, uRadii) : 1.0;
        float heldFamily = step(1.5, uProfile);
        float held = heldFamily * clamp(uHeldLens, 0.0, 1.0);
        float src = mix(foldSource(u, uFormation, corner), mix(u, heldSource(u), held), heldFamily);
        float shift = (depth < W) ? (src * W - depth) : 0.0;
        shift = mix(max(shift, 0.0), shift, heldFamily) * mat * axisFade * smoothstep(0.0, 1.5, depth);
        float mirrorZone = mix((1.0 - smoothstep(0.24, 0.32, u)) * clamp(uFormation, 0.0, 1.0),
            (1.0 - smoothstep(0.45, 0.55, u)) * held, heldFamily);
        float delta = uAberration * W * 0.5 * mirrorZone * mat;
        push = -n * shift;
        // The fold shows red the deeper content; the held lens shows blue the deeper content,
        // so blue's copy of the pulled-in edge lands outermost (FINDINGS 19).
        float dR = mix(delta, -delta, heldFamily);
        pushR = -n * (shift + dR);
        pushB = -n * mix(max(shift - delta, 0.0), shift + delta, heldFamily);
    }

    // Three taps of a transparent image, each channel from its own. Coverage is the widest of
    // the three, so where one channel's tap has left the glyph and another's has not, the
    // pixel keeps the colour that is still there — which is what a colour fringe is.
    half4 r = content.eval(base + pushR);
    half4 gg = content.eval(base + push);
    half4 b = content.eval(base + pushB);
    half a = max(max(r.a, gg.a), b.a);
    half3 rgb = half3(r.r, gg.g, b.b);
    return half4(min(rgb, half3(a)), a) * half(coverage);
}
"""

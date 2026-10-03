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
internal val GLASS_CONTENT_SHADER_SOURCE = """
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
uniform float   uHeldMagnification; // interior zoom of the held lens at full formation (GlassStyle.heldMagnification)
uniform float   uRestMap;      // resting-corner source map: 0 legacy, 1 the measured table (GlassRestMap)

uniform float2  uTouch;
uniform float   uTouchAmt;

uniform float4  uBody;         // V3 body: left centre x, right centre x, left radius, right radius, centred frame
uniform float   uBodyY;
uniform float   uBodyKind;     // 1 = V3 two-disk body, 2 = the pose body, 0 = uRadii
uniform float4  uPoseA;        // the 2x2 pose matrix A, row major
uniform float4  uPoseAInv;     // its inverse
uniform float4  uPoseC;        // (cx, cy) reference centre, (bx, by) = k / R
uniform float4  uPoseD;        // (spine, radius, scaleX, band)
uniform float   uEndpointAlpha;// 1 emits the ink premultiplied and unmasked, for endpoint composition
uniform float   uEdgeFold;     // straight-run fold strength, shared with the panel pass
uniform float   uInkSplit;     // per-channel split for semantic ink; 0 means one sharp sample
uniform float   uHeldInk;      // 1 uses the continuous held ink map instead of the seamed one

$GLASS_ANALYTIC_NORMAL_SKSL

$GLASS_BODY_SKSL

$GLASS_POSE_SKSL

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
    if (uBodyKind > 1.5) {
        return poseQuery(p, uPoseA, uPoseAInv, uPoseC, uPoseD).x;
    }
    if (uBodyKind > 0.5) {
        return bodyQuery(p, uBody, uBodyY).x;
    }
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

$GLASS_SOURCE_MAP_SKSL

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

    // The measured fold profile takes the panel's closed-form normal under the panel's own decision, so
    // the content bends exactly as the material it sits in (closeout Phase 3). The held lens and the
    // legacy profile keep the differenced field they shipped with; their output is unchanged.
    float2 n;
    float axisFade;
    if (uBodyKind > 1.5) {
        // The pose body's own transformed normal, from the same shared query as its distance.
        float4 pq = poseQuery(p, uPoseA, uPoseAInv, uPoseC, uPoseD);
        n = float2(pq.y, pq.z);
        axisFade = 1.0;
    } else if (uBodyKind > 0.5) {
        // The body's own exact normal, from the same shared query as its distance.
        float4 bq = bodyQuery(p, uBody, uBodyY);
        n = float2(bq.y, bq.z);
        axisFade = 1.0;
    } else if (abs(uProfile - 1.0) < 0.5 && analyticNormalEligible(uShapeKind, uRefractBand, halfSize, uFuseShape.y) > 0.5) {
        n = analyticNormal(p, halfSize, uRadii, uCornerPower, uProfile);
        axisFade = 1.0;
    } else {
        float eps = clamp(uRefractBand * 0.3, 1.0, 16.0);
        float2 ex = float2(eps, 0.0);
        float2 ey = float2(0.0, eps);
        float2 g = float2(
            sdShape(p + ex, halfSize) - sdShape(p - ex, halfSize),
            sdShape(p + ey, halfSize) - sdShape(p - ey, halfSize));
        float gLen = length(g);
        n = g / max(gLen, 1e-5);
        axisFade = smoothstep(0.15, 0.80, gLen / (2.0 * eps));
    }

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
    // Interior magnification of the held lens ([GlassStyle.heldMagnification]): the aperture
    // zooms about the body's centre, so the held item reads larger and the bar's own edge is
    // carried outward inside the lens, which is what the calm held references show. Zero for
    // every other profile, and it rises and falls with the lens itself.
    if (uHeldMagnification > 0.0 && uProfile >= 1.5 && uProfile < 2.5) {
        float2 magCentre = (uBodyKind > 1.5) ? uPoseC.xy
            : ((uBodyKind > 0.5) ? float2((uBody.x + uBody.y) * 0.5, uBodyY) : float2(0.0));
        base -= (p - magCentre) * (uHeldMagnification * clamp(uHeldLens, 0.0, 1.0) * mat);
    }

    float2 push = n * bend * lens;
    float split = uInkSplit * bend;
    float2 pushR = push * (1.0 - split);
    float2 pushB = push * (1.0 + split);
    if (uProfile >= 0.5 && uProfile < 2.5) {
        // The same inward fold the backdrop goes through; see the panel shader.
        float W = max(uRefractBand, 0.001);
        float u = clamp(depth / W, 0.0, 1.0);
        float corner = (uBodyKind > 1.5)
            ? poseCorner(p, uPoseAInv, uPoseC, uPoseD)
            : ((uBodyKind > 0.5)
                ? bodyCorner(p, uBody, uBodyY)
                : ((uShapeKind < 0.5) ? cornerWeight(p, halfSize, uRadii) : 1.0));
        float heldFamily = step(1.5, uProfile);
        float held = heldFamily * clamp(uHeldLens, 0.0, 1.0);
        float heldMap = (uHeldInk > 0.5) ? heldSourceContinuous(u) : heldSource(u);
        float src = mix(foldSource(u, uFormation, corner), mix(u, heldMap, held), heldFamily);
        float shift = (depth < W) ? (src * W - depth) : 0.0;
        shift = mix(max(shift, 0.0), shift, heldFamily) * mat * axisFade * smoothstep(0.0, 1.5, depth);
        float mirrorZone = mix((1.0 - smoothstep(0.24, 0.32, u)) * clamp(uFormation, 0.0, 1.0),
            (1.0 - smoothstep(0.45, 0.55, u)) * held, heldFamily);
        float delta = uInkSplit * W * 0.5 * mirrorZone * mat;
        push = -n * shift;
        // The fold shows red the deeper content; the held lens shows blue the deeper content,
        // so blue's copy of the pulled-in edge lands outermost (FINDINGS 19).
        float dR = mix(delta, -delta, heldFamily);
        pushR = -n * (shift + dR);
        pushB = -n * mix(max(shift - delta, 0.0), shift + delta, heldFamily);
    }

    if (uProfile >= 2.5) {
        // Do not layer a tab fold or fingertip-centred warp over a free magnifier.
        base = coord + clearLensDelta(p, halfSize, uHeldMagnification * mat);
        push = float2(0.0);
        pushR = float2(0.0);
        pushB = float2(0.0);
        bend = 0.0;
    }
    // One sharp sample is the default for semantic ink: a maximum-of-three-alpha union is not
    // correct per-channel transmission over an arbitrary background, so bulk ink dispersion is
    // off unless a consumer asks for it (V3-MODEL section 8.3). uAberration still drives the
    // page material's split in the panel pass; uInkSplit is this pass's own, and 0 there means
    // the alpha below is exactly the glyph's own.
    half4 col;
    if (uInkSplit > 0.0001) {
        half4 r = content.eval(base + pushR);
        half4 gg = content.eval(base + push);
        half4 b = content.eval(base + pushB);
        half a = max(max(r.a, gg.a), b.a);
        col = half4(min(half3(r.r, gg.g, b.b), half3(a)), a);
    } else {
        col = content.eval(base + push);
    }
    // Endpoint mode returns the ink premultiplied and unmasked, to be composited once over the
    // opaque endpoint material before one aperture coverage is applied to the whole endpoint.
    float outAlpha = (uEndpointAlpha > 0.5) ? 1.0 : coverage;
    return col * half(outAlpha);
}
"""

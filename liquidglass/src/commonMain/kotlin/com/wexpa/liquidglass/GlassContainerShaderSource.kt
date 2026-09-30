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
 * The optics match [GLASS_SHADER_SOURCE] — both profiles, the measured fold and two-kernel tone
 * included — so a member looks like the same material whether or not it happens to be inside a
 * container. A container is chrome at rest, so its lens formation is 0.
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
uniform float   uIor;
uniform float   uBevelPower;
uniform float   uFresnel;
uniform float   uHiChroma;
uniform float   uBlur;
uniform float   uBevel;
uniform float2  uLight;
uniform float   uSpecular;
uniform float   uSpecularPow;
uniform float   uCounterLight;
uniform float   uEdgeLight;
uniform float   uBevelPeak;
uniform float   uEdgeShadow;   // dark separating contour at the outermost pixel, 0..1
uniform float   uRimSoft;      // px the compressed rim image is smeared along the normal
uniform float   uTintAbsorb;   // 0 tint as a blend, 1 tint as an absorbing medium
uniform float4  uTint;
uniform float   uInnerShadow;
uniform float   uAdaptive;
uniform float   uProfile;      // 0 the legacy Snell bevel, 1 the measured fold lens
uniform float   uFormation;    // measured lens formation; a container is chrome at rest, 0
uniform float   uHeldLens;     // GlassProfile.Held: how far the tab-bar lens has formed, 0 to 1
uniform float   uRestMap;      // resting-corner source map: 0 legacy, 1 the measured table (GlassRestMap)
uniform float   uWideStrip;    // layer row where the quarter-scale blurred copy starts; 0 disables it
uniform float   uWideScale;    // scale of that copy
uniform float   uFineShare;    // share of fine detail in the backdrop term
uniform float   uWideKernel;   // px sigma of the wide kernel; also the fine term's spread across the element
uniform float   uLift;         // fixed luminance lift after the tint
uniform float   uLiftAdapt;    // how far the lift falls with the wide-kernel luma

$GLASS_OKLAB_SOURCE

$GLASS_ANALYTIC_NORMAL_SKSL

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

// The corner weight of whichever member is nearest: in a fused body each member keeps its own
// runs and arcs, and the fillet between two members takes the nearer one's.
float nearestCornerWeight(float2 p) {
    float best = 1e6;
    float w = 1.0;
    for (int i = 0; i < $MAX_GLASS_MEMBERS; i++) {
        if (float(i) < uCount) {
            float s = sdRoundRectAt(p, uRect[i], uRadius[i]);
            if (s < best) {
                best = s;
                float2 halfSize = uRect[i].zw * 0.5;
                float r = min(uRadius[i], min(halfSize.x, halfSize.y));
                float2 q = abs(p - (uRect[i].xy + halfSize)) - halfSize + r;
                float soft = max(r * 0.2, 1.0);
                w = smoothstep(-soft, 0.0, min(q.x, q.y));
            }
        }
    }
    return w;
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
    if (radius < 4.0) {
        // The measured fine kernel is under a point, and a sub-point Gaussian needs no nineteen
        // taps: a rotated square at 0.8 of the radius plus a double-weighted centre is within
        // noise of it and costs a quarter as much. Measured on a Pixel 7 with six panels: the
        // frame time this saves is the difference between the material and the budget.
        float r = radius * 0.8;
        half3 sum = backdropAt(p) * half(2.0);
        sum += backdropAt(p + r * rotate(float2( 1.0,  0.0), rot));
        sum += backdropAt(p + r * rotate(float2( 0.0,  1.0), rot));
        sum += backdropAt(p + r * rotate(float2(-1.0,  0.0), rot));
        sum += backdropAt(p + r * rotate(float2( 0.0, -1.0), rot));
        return sum * half(1.0 / 6.0);
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

// The measured material's fine term is one-dimensional (MODEL.md section 2a, FINDINGS 22): it
// carries detail along a bar and none at all across it. Two readings of the Photos toolbar pill
// pin it down. Where the target's 8 px stripe band ends under the pill the detail stops within
// two rows, so nothing is averaged across; and the target's 20-row black band under the same
// pill leaves no step at all, though it is a 122-level boundary. Content that does not vary
// along the bar produces no fine term, which is what a high-pass along one axis does.
//
// So the detail is the fine sample minus the backdrop averaged along the element's long axis,
// five taps spanning the along-axis wide kernel, jittered per pixel so periodic content does not
// beat against the tap spacing. Five rather than seven: the pair at 2.25 sigma carries 5% of the
// weight and cost 1.5 ms a frame on an S24.
half3 axisMeanAt(float2 p, float span, float2 rot) {
    float2 t = (uSize.x >= uSize.y) ? float2(1.0, 0.0) : float2(0.0, 1.0);
    float k = span * (0.85 + 0.3 * abs(rot.x));
    half3 sum = backdropAt(p);
    half3 acc = (backdropAt(p + t * (0.75 * k)) + backdropAt(p - t * (0.75 * k))) * half(0.7548);
    acc += (backdropAt(p + t * (1.60 * k)) + backdropAt(p - t * (1.60 * k))) * half(0.4043);
    return (sum + acc) * half(1.0 / 3.3182);
}

float luma(half3 c) {
    return dot(float3(c), float3(0.2126, 0.7152, 0.0722));
}

// See GlassShaderSource for both of these: a superellipse bevel profile and the exact Snell
// deviation for its slope, so a fused body bends light the same way a single panel does.
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

// The measured fold lens; identical text to the panel shader, and checked to be.
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

$GLASS_REST_SOURCE_SKSL

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

// The wide tone kernel, from the strip the host records beneath the sharp copy.
half3 wideAt(float2 p) {
    float2 q = clamp(p, uBackdrop.xy, uBackdrop.zw);
    float2 c = float2(q.x * uWideScale, uWideStrip + q.y * uWideScale);
    half4 s = content.eval(c);
    return (s.a > half(0.004)) ? s.rgb / s.a : half3(uBase);
}

half3 toneMappedTint(half3 tint, float bgLuma, float adapt) {
    float g = clamp(bgLuma, 0.0, 1.0);
    float value = mix(1.0, mix(1.10, 0.88, g), adapt);
    float sat   = mix(1.0, mix(0.88, 1.06, g), adapt);
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
    // through the neck between two panels rather than tracing either one's own edge. The wide
    // epsilon matters more here than for a single panel: the neck between two members is all
    // medial axis, and at a one-pixel epsilon the gradient collapses right where the fusion is
    // supposed to read.
    //
    // A single, unfused member on the measured profile is exactly the panel's analytic rounded rectangle
    // (circular corners: the container's field is an L2 field), and takes the shared closed-form normal
    // under the same decision (closeout Phase 3). Fused bodies, several members and the legacy profile
    // keep the merged-field gradient.
    float2 n;
    float axisFade;
    float2 member0Half = uRect[0].zw * 0.5;
    float2 member0Centre = uRect[0].xy + member0Half;
    if (uCount < 1.5 && uProfile >= 0.5 && analyticNormalEligible(0.0, uRefractBand, member0Half, 0.0) > 0.5) {
        n = analyticNormal(local - member0Centre, member0Half, float4(uRadius[0]), 2.0, uProfile);
        axisFade = 1.0;
    } else {
        float eps = clamp(uRefractBand * 0.3, 1.0, 16.0);
        float2 g = float2(
            fieldAt(local + float2(eps, 0.0)) - fieldAt(local - float2(eps, 0.0)),
            fieldAt(local + float2(0.0, eps)) - fieldAt(local - float2(0.0, eps)));
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
    float measured = step(0.5, uProfile);
    float2 push = n * bend * uRefractDepth;
    float split = uAberration * bend;
    float2 pushR = push * (1.0 - split);
    float2 pushB = push * (1.0 + split);
    if (measured > 0.5) {
        // The measured profile: inward only, with the fold's dispersion in the mirrored zone.
        // See the panel shader.
        float W = max(uRefractBand, 0.001);
        float u = clamp(depth / W, 0.0, 1.0);
        float corner = nearestCornerWeight(local);
        float shift = (depth < W) ? (foldSource(u, uFormation, corner) * W - depth) : 0.0;
        shift = max(shift, 0.0) * axisFade * smoothstep(0.0, 1.5, depth);
        float mirrorZone = (1.0 - smoothstep(0.24, 0.32, u)) * clamp(uFormation, 0.0, 1.0);
        float delta = uAberration * W * 0.5 * mirrorZone;
        push = -n * shift;
        pushR = -n * (shift + delta);
        pushB = -n * max(shift - delta, 0.0);
    }
    half3 sharp = half3(
        backdropAt(coord + pushR).r,
        backdropAt(coord + push).g,
        backdropAt(coord + pushB).b
    );
    half3 soft = blurredAt(coord + push, uBlur * e, tapRotation(coord));
    float rimSharp = (1.0 - e) * (1.0 - e);
    half3 bg = mix(soft, sharp, half(rimSharp));

    // The measured two-kernel material; see the panel shader.
    half3 wide = bg;
    if (uWideStrip >= 1.0) {
        wide = wideAt(coord);
        // A high-pass along the long axis, not a share of a second blurred copy: see the note on
        // axisMeanAt and FINDINGS 22.
        half3 line = axisMeanAt(coord + push, uWideKernel * 0.7, tapRotation(coord));
        bg = wide + (bg - line) * half(clamp(uFineShare, 0.0, 1.0));
    }

    float bgLuma = luma(bg);
    half3 tinted = (measured > 0.5)
        ? half3(uTint.rgb)
        : toneMappedTint(half3(uTint.rgb), bgLuma, uAdaptive);
    half3 col = mix(bg, tinted, half(clamp(uTint.a, 0.0, 1.0)));
    float lift = uLift - uLiftAdapt * luma(wide);
    col += half3(half((uLiftAdapt > 0.0) ? max(lift, 0.0) : lift));

    float facing = dot(n, uLight);
    // Two rim geometries. At uBevelPeak 0 the bevel is a chamfer: brightest at the very edge,
    // fading inward, which is what a hairline-edged bar wants. Above 0 it is a bead: dark at
    // the edge, brightest a fraction of the bevel's width inside it, fading again toward the
    // interior — the profile the reference lens shows, rising over about three points from a
    // dark outermost pixel to its peak and falling over three more.
    float peakAt = clamp(uBevelPeak, 0.0, 0.9) * uBevel;
    float bevelBand = (peakAt < 0.5)
        ? smoothstep(uBevel, 0.0, depth)
        : smoothstep(0.0, peakAt, depth) * smoothstep(uBevel, peakAt, depth);

    float innerLine = max(smoothstep(uBevel * 2.2, 0.0, depth) - bevelBand, 0.0);
    col -= half3(half(innerLine * uInnerShadow));

    float cosI = inversesqrt(1.0 + slope * slope);
    float f0 = (uIor - 1.0) / (uIor + 1.0);
    f0 = f0 * f0;
    float fresnel = f0 + (1.0 - f0) * pow(max(1.0 - cosI, 0.0), 5.0);

    // See GlassShaderSource: a two-sided rim whose far side and outermost line the style sets.
    float counterLight = clamp(uCounterLight, 0.0, 1.0);
    float counterPow = uSpecularPow * mix(1.6, 1.0, smoothstep(0.35, 1.0, counterLight));
    float spec = pow(max(facing, 0.0), uSpecularPow) * bevelBand;
    float counter = pow(max(-facing, 0.0), counterPow) * bevelBand * counterLight;
    float edge = smoothstep(2.0, 0.0, depth);
    float edgeLine = edge * 0.5 * (max(facing, 0.0) + counterLight * max(-facing, 0.0));
    float highlight = (spec + counter) * uSpecular
        + edgeLine * uSpecular * uEdgeLight
        + fresnel * uFresnel * bevelBand;
    col = applyHighlight(col, highlight, uHiChroma);

    // The dark contour that separates glass from its backdrop. Apple's 2026 revision pairs it
    // with a brighter specular; the 2025 material has none, so this is 0 on every preset here.
    // See GlassShaderSource: centred just inside the edge, clear of the coverage ramp.
    float rimDark = clamp(1.0 - abs(depth - 1.5) / 1.5, 0.0, 1.0);
    col -= half3(half(rimDark * uEdgeShadow));

    return half4(clamp(col, half3(0.0), half3(1.0)), 1.0) * half(coverage);
}
"""

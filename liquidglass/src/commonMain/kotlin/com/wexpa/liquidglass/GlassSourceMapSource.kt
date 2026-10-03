package com.wexpa.liquidglass

/**
 * The source maps, as one shared piece of SkSL.
 *
 * A source map answers one question: a pixel at depth `d` inside an outline shows backdrop from
 * what depth `s`? The panel pass asks it of the page behind a material plate and the content pass
 * asks it of semantic ink, so both shaders include this exact text - the functions are compared
 * character for character by `GlassContentShaderTest`, and sharing the source is what makes that
 * comparison trivially true instead of a standing maintenance risk.
 *
 * The measured resting-corner table is the authority at a corner; the straight-run candidate
 * ([GlassStyle.edgeFold]) and the continuous connection through the held map's middle band are
 * authored, and say so where they are defined.
 */
internal val GLASS_SOURCE_MAP_SKSL = """
// One radial source field for a standalone lens, shared verbatim by material and ink.
// f(r) = r [1 - m (1-r^2)^2], 0<=r<=1. f(1)=1 and f'(1)=1;
// f'(r)>=1-m>0 for m<=0.6, so it cannot fold or hide an internal annulus.
float2 clearLensDelta(float2 p, float2 halfSize, float magnification) {
    float2 q = p / max(halfSize, float2(0.001));
    float r2 = clamp(dot(q, q), 0.0, 1.0);
    float shoulder = 1.0 - r2;
    return -p * (clamp(magnification, 0.0, 0.6) * shoulder * shoulder);
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

// The same outward correspondence, connected to the interior instead of cut at half the band.
//
// The captures never established that the 0.14 W step at u = 0.5 was a true discontinuity, and a
// smooth ray path meeting one source plane has a continuous source coordinate, so the sharp ink
// map does not carry that jump (V3-MODEL section 10.3). w(u) fades the outward term out across
// 0.45..0.55; below 0.45 this is exactly 1.3u - 0.29 and above 0.55 it is identity. The 1.3 and
// 0.29 are inherited effective-map values, not a recovered index or thickness, and the
// connection band is authored regularization.
float heldSourceContinuous(float u) {
    float w = 1.0 - smoothstep(0.45, 0.55, u);
    return u + w * (0.3 * u - 0.29);
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

// The measured cover-sheet lens, as a lookup: a pixel at depth u = d/W inside the edge shows
// backdrop content from depth s = foldSource(u) * W. Fitted on 13k frames of a real finger pull
// and reproduced at Tint 0, 50 and 100 (MODEL.md section 5): identity from 0.9 W inward; a 2x
// stretch over 0.52-0.9 W showing 0.71-0.9 W; a stationary seam at 0.70 W over 0.28-0.52 W;
// and the outer 0.28 W showing 0.71-1.07 W upside down. Content 0-0.69 W inside the edge is never
// displayed and nothing outside the edge is sampled. Static chrome and a committed (fast) pull
// show none of it, only a slight inward offset, so `formation` blends between the two.
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
    // The straight-run candidate of V3-MODEL section 10.2: f(u) = u + a (1-u)^3, which joins
    // identity in value, first and second derivative and, for a > 1/3, folds once at
    // uFold = 1 - 1/sqrt(3a) with minimum source depth 1 - 2/(3 sqrt(3a)). Page structure
    // therefore appears twice near a straight edge, with opposite orientation, which is the
    // mechanism the reference shows. `a` is authored, not measured. At 0 this degenerates to the
    // shallow inward offset the measured sheet pull showed, which every existing consumer keeps.
    float v = 1.0 - u;
    float straight = (uEdgeFold > 0.0)
        ? (u + uEdgeFold * v * v * v)
        : (u + 0.06 * v * v);
    float rest = mix(straight, restSource(u), clamp(corner, 0.0, 1.0));
    return mix(rest, full, clamp(formation, 0.0, 1.0));
}
"""

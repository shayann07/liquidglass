package com.wexpa.liquidglass

/**
 * The analytic outline normal and the decision that selects it, shared verbatim by the panel, content and
 * container shaders (closeout Phase 3, shared-path repair).
 *
 * The panel shader has always taken the closed-form normal for an analytic rounded rectangle whose band
 * stops short of its inradius and which is not fused; the content and container shaders differenced the
 * field instead, with an epsilon of 0.3 W, and a central difference of the rounded-rect field is not the
 * exact radial direction near the arc/run transition. On the measured profile that put the content pass up
 * to 1.5 px from the material it sits in at a corner. One text, one decision, for equivalent geometry:
 * the same radius clamping, corner exponent, coordinate origin and inradius as the panel.
 *
 * The container applies it only to a single, unfused member (its merged-field fallback stays for fused
 * bodies), and the content shader only on the measured fold profile: the held lens's content pass and the
 * legacy profile keep the differenced normal they shipped with, unchanged.
 */
internal const val GLASS_ANALYTIC_NORMAL_SKSL = """
// The Ln norm. At n = 2 this is `length`, so the rounded rect below is bit-identical to a
// circular-cornered one; at n = 4 the corner becomes the superellipse Apple actually uses.
float lnNorm(float2 v, float n) {
    if (n <= 2.001 && n >= 1.999) {
        return length(v);
    }
    return pow(pow(v.x, n) + pow(v.y, n), 1.0 / n);
}

// The outward unit normal of sdRoundRect, in closed form.
//
// Only valid away from the medial axis, where the true gradient is discontinuous; the caller
// checks that before using it. `gradRadius` is deliberately larger than the outline's radius,
// which rotates the normal through the corner over a wider arc than the outline turns and
// removes the direction kink where the corner meets the flat run. It does not move the outline.
//
// The closed-form gradient and the wider-radius trick are Kyant0/AndroidLiquidGlass's; the Ln
// corner, the degenerate guards and the caller's medial-axis handling are ours. See NOTICE.
float2 gradRoundRect(float2 p, float2 halfSize, float4 r, float power, float widen) {
    float2 rr = (p.x > 0.0) ? r.yz : r.xw;
    float radius = (p.y > 0.0) ? rr.y : rr.x;
    radius = min(radius, min(halfSize.x, halfSize.y));
    float gradRadius = min(radius * widen, min(halfSize.x, halfSize.y));
    float2 q = abs(p) - halfSize + gradRadius;
    float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
    if (q.x > 0.0 && q.y > 0.0) {
        float L = lnNorm(q, power);
        if (L <= 1e-5) {
            return s * float2(0.70710678, 0.70710678);
        }
        float2 w = float2(
            pow(q.x / L, power - 1.0),
            pow(q.y / L, power - 1.0));
        float wl = length(w);
        return (wl <= 1e-5) ? s * float2(0.70710678, 0.70710678) : s * (w / wl);
    }
    // Flat run: the nearest edge is whichever of the two is closer.
    float ax = step(q.y, q.x);
    return s * float2(ax, 1.0 - ax);
}

// Whether the closed-form normal applies: an analytic shape (no sampled field), a band that stops short
// of the inradius (three quarters of it) so the medial axis is never reached, and no fusion. The same
// decision in every shader; a shader that cannot describe its geometry this way must not call it.
float analyticNormalEligible(float shapeKind, float band, float2 halfSize, float fuseWidth) {
    float inradius = min(halfSize.x, halfSize.y);
    return (shapeKind < 0.5 && band < inradius * 0.75 && fuseWidth <= 0.0) ? 1.0 : 0.0;
}

// The measured lens maps a corner's rim onto the ring inside it, so its normal has to be the true one
// (radial from the corner's centre); the wider arc that smooths the legacy profile's direction kink would
// bend the ring. Axis fade is 1 wherever this applies.
float2 analyticNormal(float2 p, float2 halfSize, float4 r, float power, float profile) {
    return gradRoundRect(p, halfSize, r, power, mix(1.5, 1.0, step(0.5, profile)));
}
"""

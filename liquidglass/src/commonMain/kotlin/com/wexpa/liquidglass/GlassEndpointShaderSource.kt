package com.wexpa.liquidglass

/**
 * The aperture pass of the exact endpoint compositor.
 *
 * `V3-MODEL.md` section 8: with `B0` the page material without semantic ink, `B1` the same
 * material seen through the selector, `U0 = (c0, a0)` the ordinary ink and `U1 = (c1, a1)` the
 * selected ink at its refracted source position, the complete endpoint images are
 *
 * ```
 * C0 = c0 + (1 - a0) B0
 * C1 = c1 + (1 - a1) B1
 * C  = (1 - m) C0 + m C1
 * ```
 *
 * for aperture coverage `m`. The common shortcut — mix the ink alone and alpha-over it on
 * separately mixed material — differs from this by `m (1 - m) (a1 - a0) (B1 - B0)`, which is
 * largest exactly at a moving rim, where both glyph coverage and material are changing. That
 * cross term is the ghost.
 *
 * This shader is the last step of the construction. `C0` is already on the canvas, drawn
 * normally. `C1` has been built complete and opaque in one offscreen layer: the material pass
 * emitted `(B1, 1)` and the ink pass drew `(c1, a1)` over it, which is source-over's own
 * definition of `c1 + (1 - a1) B1`. Multiplying that layer by coverage yields `(m C1, m)`, and
 * an ordinary source-over of that onto `C0` computes the equation above exactly, once.
 *
 * It carries only the geometry it needs to recompute the same coverage the two passes used, so
 * no fourth transcription of the outline exists.
 */
internal val GLASS_ENDPOINT_SHADER_SOURCE = """
uniform shader endpoint;       // the complete opaque endpoint C1, padded like every other pass
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
uniform float4  uBody;
uniform float   uBodyY;
uniform float   uBodyKind;     // 1 = V3 two-disk body, 2 = the pose body, 0 = uRadii
uniform float4  uPoseA;        // the 2x2 pose matrix A, row major
uniform float4  uPoseAInv;     // its inverse
uniform float4  uPoseC;        // (cx, cy) reference centre, (bx, by) = k / R
uniform float4  uPoseD;        // (spine, radius, scaleX, band)

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

half4 main(float2 coord) {
    float2 local = coord - float2(uPad);
    float2 halfSize = uSize * 0.5;
    float2 p = local - halfSize;
    float d = sdShape(p, halfSize);
    float coverage = 1.0 - smoothstep(-0.75, 0.75, d);
    if (coverage <= 0.0) {
        return half4(0.0);
    }
    // The endpoint layer is opaque wherever coverage is non-zero, so this is (m * C1, m): a
    // premultiplied colour whose ordinary source-over completes the coverage equation.
    half4 c = endpoint.eval(coord);
    return c * half(coverage);
}
"""

package com.wexpa.liquidglass

/**
 * The V3 body's geometry, as one shared piece of SkSL.
 *
 * The panel pass, the content pass and the coverage mask all include this text, so there is
 * exactly one distance field, one normal and one curvature for the moving body rather than three
 * transcriptions that can drift apart. It mirrors [GlassBody]'s CPU query; a coordinate-coded
 * readback asserts the two agree.
 *
 * The body is the convex hull of two unequal disks on the x axis. `uBody` carries
 * `(leftCentreX, rightCentreX, leftRadius, rightRadius)` in the element's **centred** frame and
 * `uBodyY` the disks' shared y. The closed form below is the even-capsule construction: fold the
 * perpendicular coordinate to its absolute value, and the shape is one arc, one straight tangent
 * run and the other arc, with the branch decided by a single dot product. It is exact, not an
 * approximation of a squircle, and it is constant work: no distance transform, no iteration, no
 * angular search.
 *
 * `bodyQuery` returns `(signedDistance, normalX, normalY, curvature)` with distance negative
 * inside and the normal pointing out of the body. Curvature is `1/radius` on an arc and 0 on a
 * run, which is what the transport Jacobian needs to know whether an edge is bending the source
 * tangentially.
 */
internal val GLASS_BODY_SKSL = """
float4 bodyQuery(float2 p, float4 uBody, float uBodyY) {
    float clx = uBody.x;
    float crx = uBody.y;
    float rl  = uBody.z;
    float rr  = uBody.w;
    float h   = max(crx - clx, 1e-4);
    float b   = clamp((rl - rr) / h, -0.999999, 0.999999);
    float a   = sqrt(max(1.0 - b * b, 0.0));

    float along = p.x - clx;
    float signY = (p.y >= uBodyY) ? 1.0 : -1.0;
    float perp  = abs(p.y - uBodyY);

    float k = a * along - b * perp;
    if (k < 0.0) {
        float2 w = float2(along, perp);
        float len = length(w);
        float2 n = (len > 1e-5) ? (w / len) : float2(-1.0, 0.0);
        return float4(len - rl, n.x, n.y * signY, 1.0 / max(rl, 1e-4));
    }
    if (k > a * h) {
        float2 w = float2(along - h, perp);
        float len = length(w);
        float2 n = (len > 1e-5) ? (w / len) : float2(1.0, 0.0);
        return float4(len - rr, n.x, n.y * signY, 1.0 / max(rr, 1e-4));
    }
    // The straight tangent run: its outward normal is (b, ±a), which is exactly the direction
    // that makes one line touch both circles.
    return float4(b * along + a * perp - rl, b, a * signY, 0.0);
}

/** The optical band of a body: 0.6 of its smaller end radius, the same ratio a bar uses. */
float bodyBand(float4 uBody) {
    return ${GlassMaterial.BEVEL_RATIO} * min(uBody.z, uBody.w);
}

/**
 * How far the nearest boundary feature is an end arc rather than a straight tangent run, 1 to 0,
 * blended over a short geometric distance so the transport profile does not step where the
 * nearest feature changes identity. The blend coordinate is the same `k` the query branches on,
 * so it is geometry rather than a screen-space heuristic.
 */
float bodyCorner(float2 p, float4 uBody, float uBodyY) {
    float clx = uBody.x;
    float h   = max(uBody.y - clx, 1e-4);
    float b   = clamp((uBody.z - uBody.w) / h, -0.999999, 0.999999);
    float a   = sqrt(max(1.0 - b * b, 0.0));
    float k   = a * (p.x - clx) - b * abs(p.y - uBodyY);
    float soft = max(0.25 * min(uBody.z, uBody.w), 1.0);
    float left  = 1.0 - smoothstep(0.0, soft, k);
    float right = smoothstep(a * h - soft, a * h, k);
    return clamp(max(left, right), 0.0, 1.0);
}
"""

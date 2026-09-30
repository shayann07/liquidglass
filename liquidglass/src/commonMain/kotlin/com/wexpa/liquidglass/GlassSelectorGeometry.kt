package com.wexpa.liquidglass

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The V3 selector body: the convex hull of two unequal disks on the travel axis.
 *
 * Five coordinates, `q = (cx, cy, L, r, k)`, where `(cx, cy)` is the horizontal bounding-box
 * centre, `L` the separation of the two disk centres, `r` their mean radius and `k` half their
 * signed radius difference. The two disks are
 *
 * ```
 * rL = r - k   CL = (cx - L/2 - k, cy)
 * rR = r + k   CR = (cx + L/2 - k, cy)
 * ```
 *
 * so the horizontal extrema are exactly `cx ± (L/2 + r)` whatever `k` is: end asymmetry never
 * secretly moves the declared centre. The vertical extent is `cy ± (r + |k|)`, which is **not**
 * `2r` tall unless `k` is zero.
 *
 * At `k = 0` this is a capsule. Non-zero `k` gives one end a larger cap and the sides a slope,
 * which is what lets the body lag at the back and bulge at the front on arrival without
 * stretching the raster of anything drawn inside it. It is a reduced-order *visual* body: no
 * volume is conserved and no fluid is simulated.
 *
 * This class is the CPU authority for the same geometry the shader evaluates
 * ([GLASS_BODY_SKSL]); the two are asserted against each other and against a dense boundary
 * oracle in `GlassSelectorBodyTest`.
 */
@Immutable
internal data class GlassBody(
    val cx: Float,
    val cy: Float,
    val length: Float,
    val radius: Float,
    val skew: Float,
) {
    val leftRadius: Float get() = radius - skew
    val rightRadius: Float get() = radius + skew
    val leftCentreX: Float get() = cx - length / 2f - skew
    val rightCentreX: Float get() = cx + length / 2f - skew

    /** Horizontal half-width, `L/2 + r`. Section 7.1's `A`. */
    val halfWidth: Float get() = length / 2f + radius
    val halfHeight: Float get() = radius + abs(skew)

    val left: Float get() = cx - halfWidth
    val right: Float get() = cx + halfWidth
    val top: Float get() = cy - halfHeight
    val bottom: Float get() = cy + halfHeight

    /** `(rR - rL) / L`: the cosine-like tilt of the two external tangents. */
    val delta: Float get() = if (length > 1e-6f) (2f * skew) / length else 0f

    val isWellFormed: Boolean
        get() = length > 1e-4f && leftRadius > 0f && rightRadius > 0f && abs(delta) < 1f

    fun withCentre(x: Float, y: Float = cy): GlassBody = copy(cx = x, cy = y)

    companion object {
        /** A capsule of this size: the shape the V2 selector was, expressed in V3 coordinates. */
        fun capsule(cx: Float, cy: Float, width: Float, height: Float): GlassBody {
            val r = height / 2f
            return GlassBody(cx = cx, cy = cy, length = max(width - height, 1e-3f), radius = r, skew = 0f)
        }
    }
}

/** What one distance query returns: everything the optics need, computed once. */
internal class GlassBodyQuery {
    /** Signed distance; negative inside. */
    var distance: Float = 0f
    /** Outward unit normal at the nearest boundary feature. */
    var normalX: Float = 0f
    var normalY: Float = 0f
    /** Curvature of that feature: `1/radius` on an arc, 0 on a tangent run. */
    var curvature: Float = 0f
    /** 0 left arc, 1 right arc, 2 top run, 3 bottom run. */
    var feature: Int = 0
    /** The nearest boundary point. */
    var boundaryX: Float = 0f
    var boundaryY: Float = 0f
}

/**
 * Exact signed distance, outward normal, nearest boundary point, feature identity and local
 * curvature for [body] at `(px, py)`, filled into [out] so a sweep allocates nothing.
 *
 * Constant work: two circle tests, two segment projections, a handful of comparisons. There is
 * no distance transform and no iteration. At a medial-axis tie the normal is not unique; the
 * optics have already faded to identity there by construction (the band never reaches the
 * medial axis, see [glassBodyBand]).
 */
internal fun glassBodyQuery(body: GlassBody, px: Float, py: Float, out: GlassBodyQuery): GlassBodyQuery {
    val rL = body.leftRadius
    val rR = body.rightRadius
    val clx = body.leftCentreX
    val crx = body.rightCentreX
    val cy = body.cy
    val delta = body.delta.coerceIn(-0.999999f, 0.999999f)
    val gamma = sqrt(max(1f - delta * delta, 0f))

    var best = Float.MAX_VALUE
    var bestFeature = -1
    var bnx = 0f
    var bny = 0f
    var bbx = 0f
    var bby = 0f
    var bcurv = 0f

    // The two external tangent runs. Their shared outward normals are (-delta, ∓gamma):
    // n·(CR - CL) = rL - rR is what makes one line touch both circles.
    for (side in 0..1) {
        val ny = if (side == 0) -gamma else gamma
        val nx = -delta
        val ax = clx + rL * nx
        val ay = cy + rL * ny
        val bx = crx + rR * nx
        val by = cy + rR * ny
        val ex = bx - ax
        val ey = by - ay
        val len2 = ex * ex + ey * ey
        val t = if (len2 > 1e-12f) (((px - ax) * ex + (py - ay) * ey) / len2).coerceIn(0f, 1f) else 0f
        val qx = ax + t * ex
        val qy = ay + t * ey
        val dx = px - qx
        val dy = py - qy
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < best) {
            best = dist
            bestFeature = if (side == 0) 2 else 3
            bnx = nx
            bny = ny
            bbx = qx
            bby = qy
            bcurv = 0f
        }
    }

    // Each end arc, but only over the part of it that is actually exposed: the left arc carries
    // outward normals with nx <= -delta, the right arc nx >= -delta. The tangent points are the
    // join and are already covered by the segments above.
    for (side in 0..1) {
        val ccx = if (side == 0) clx else crx
        val rad = if (side == 0) rL else rR
        var ux = px - ccx
        var uy = py - cy
        var len = sqrt(ux * ux + uy * uy)
        if (len < 1e-6f) {
            // Dead centre of a disk: any eligible direction gives the same distance.
            ux = if (side == 0) -1f else 1f
            uy = 0f
            len = 1f
        } else {
            ux /= len
            uy /= len
        }
        val eligible = if (side == 0) ux <= -delta + 1e-6f else ux >= -delta - 1e-6f
        if (!eligible) continue
        val dist = abs(len - rad)
        if (dist < best) {
            best = dist
            bestFeature = side
            bnx = ux
            bny = uy
            bbx = ccx + rad * ux
            bby = cy + rad * uy
            bcurv = if (rad > 1e-6f) 1f / rad else 0f
        }
    }

    val inside = glassBodyContains(body, px, py, delta, gamma)
    out.distance = if (inside) -best else best
    out.normalX = bnx
    out.normalY = bny
    out.curvature = bcurv
    out.feature = bestFeature
    out.boundaryX = bbx
    out.boundaryY = bby
    return out
}

/**
 * Inside-ness by construction rather than by sign of a fitted field: the hull is exactly the
 * union of the two disks and the tangent quadrilateral between them, so four consistently
 * oriented half-plane tests and two circle tests settle it with no winding convention to guess.
 */
private fun glassBodyContains(body: GlassBody, px: Float, py: Float, delta: Float, gamma: Float): Boolean {
    val rL = body.leftRadius
    val rR = body.rightRadius
    val clx = body.leftCentreX
    val crx = body.rightCentreX
    val cy = body.cy
    val dlx = px - clx
    val dly = py - cy
    if (dlx * dlx + dly * dly <= rL * rL) return true
    val drx = px - crx
    val dry = py - cy
    if (drx * drx + dry * dry <= rR * rR) return true
    // The quadrilateral ATop, BTop, BBottom, ABottom. Both tangent planes and the two end
    // planes through the tangent points, all as outward half-spaces.
    val nx = -delta
    val atx = clx + rL * nx
    val aty = cy + rL * -gamma
    val btx = crx + rR * nx
    val bty = cy + rR * -gamma
    val abx = clx + rL * nx
    val aby = cy + rL * gamma
    val bbx = crx + rR * nx
    val bby = cy + rR * gamma
    return insidePolygon(px, py, atx, aty, btx, bty, bbx, bby, abx, aby)
}

private fun insidePolygon(
    px: Float,
    py: Float,
    x0: Float, y0: Float,
    x1: Float, y1: Float,
    x2: Float, y2: Float,
    x3: Float, y3: Float,
): Boolean {
    // The four vertices are given in one consistent rotation; a point inside keeps the same
    // cross-product sign against every edge.
    var positive = false
    var negative = false
    val xs = floatArrayOf(x0, x1, x2, x3)
    val ys = floatArrayOf(y0, y1, y2, y3)
    for (i in 0..3) {
        val j = (i + 1) and 3
        val cross = (xs[j] - xs[i]) * (py - ys[i]) - (ys[j] - ys[i]) * (px - xs[i])
        if (cross > 1e-5f) positive = true
        if (cross < -1e-5f) negative = true
    }
    return !(positive && negative)
}

/**
 * The optical band for a body: `0.6 * min(rL, rR)`, the same 0.6 R the measured material uses on
 * a symmetric bar. It stops short of where the inward normals meet, so the map reaches identity
 * before the medial axis and the normal is never queried where it is ambiguous.
 */
internal fun glassBodyBand(body: GlassBody): Float =
    GlassMaterial.BEVEL_RATIO * min(body.leftRadius, body.rightRadius)

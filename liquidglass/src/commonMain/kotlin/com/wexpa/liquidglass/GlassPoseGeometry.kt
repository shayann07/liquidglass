package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * The V3 selector's body as a **canonical capsule under a compact 2D pose map**.
 *
 * This replaces the horizontal-only five-coordinate body `(cx, cy, L, r, k)`. That body could
 * only move along one axis and adjust its length, mean radius and end asymmetry; it had no way to
 * stretch toward an arbitrary two-dimensional finger movement, which is the response the owner
 * rejected it for. The shape here is
 *
 * ```
 * x = c + A F_k(Z(q))            q on the canonical capsule (half-spine a, radius r)
 * F_k(z) = (1 + dot(k, z) / R) z
 * A      = exp(p I + M)          M = [[u, v], [v, -u]]
 * ```
 *
 * with eight scalar pose states: `c` (2), `p` isotropic log expansion, `u, v` trace-free
 * directional strain, `kx, ky` directional taper, and one accommodation state. `Z` is the
 * accommodation map and is the only part that differs between the two candidates of the brief's
 * section 6.5:
 *
 * - [GlassAccommodation.Uniform] (candidate A): `Z(q) = diag(exp(w), 1) q`. Horizontal affine
 *   scaling, which widens the end caps along with the centre span.
 * - [GlassAccommodation.Spine] (candidate B): `Z(q) = (qx + ell * clamp(qx/a, -1, 1), qy)`. The
 *   centre strip stretches and each cap **translates outward without being widened**, so the
 *   image is exactly a capsule of half-spine `a + ell` and the same radius `r`.
 *
 * Neither is asserted to be Apple's construction. Which one this library uses is decided by the
 * static contour test in `GlassAccommodationChoiceTest` against the inspected reference crops,
 * and frozen before any motion fitting.
 *
 * ## `R` and invertibility
 *
 * `R` is defined here as the **largest `|z|` the body reaches**, so the brief's conservative
 * guard `length(k) * max|z| / R <= eta` reduces to exactly `length(k) <= eta`. Inside that bound
 * `det(DF_k) = (1 + dot(b,z))(1 + 2 dot(b,z)) > 0` and the map is invertible over the body.
 *
 * ## What this is not
 *
 * A modelling decomposition, not a fluid simulation and not a claim about Apple's internals. The
 * trace-free strain preserves area in isolation; that is a property of the parameterisation, not
 * evidence that projected area is conserved. `p` is free to change it.
 */
internal enum class GlassAccommodation { Uniform, Spine }

/**
 * The canonical reference capsule and the accommodation family, fixed for a layout.
 *
 * [a] is the half-spine and [r] the radius, both in bar-local physical px. A circle is `a == 0`,
 * where candidate B's `qx / a` is undefined; [accommodation] falls back to [GlassAccommodation.Uniform]
 * there rather than hiding an epsilon inside the map.
 */
internal class GlassReference(
    val a: Float,
    val r: Float,
    accommodation: GlassAccommodation = GlassAccommodation.Spine,
) {
    /** The declared geometry epsilon below which a half-spine is not a spine. */
    val accommodation: GlassAccommodation =
        if (a <= GEOMETRY_EPSILON) GlassAccommodation.Uniform else accommodation

    val width: Float get() = 2f * (a + r)
    val height: Float get() = 2f * r

    fun sameAs(other: GlassReference): Boolean =
        a == other.a && r == other.r && accommodation == other.accommodation

    companion object {
        const val GEOMETRY_EPSILON = 1e-3f
    }
}

/**
 * The eight scalar pose states. Mutable and reused: this is stepped every substep and queried
 * per contour sample, so it allocates nothing.
 */
internal class GlassPose {
    var cx: Float = 0f
    var cy: Float = 0f
    /** Isotropic log expansion. `exp(p)` scales both axes. */
    var p: Float = 0f
    /** Trace-free directional strain. `rho = hypot(u, v)`; stretch along one axis, squeeze across. */
    var u: Float = 0f
    var v: Float = 0f
    /** Directional taper: differentiates the leading and trailing ends. */
    var kx: Float = 0f
    var ky: Float = 0f
    /**
     * Content accommodation. For [GlassAccommodation.Spine] this is `ell`, an added half-spine
     * **length in px**; for [GlassAccommodation.Uniform] it is `w`, a dimensionless log scale.
     * The two are not interchangeable and the reference says which one is in force.
     */
    var acc: Float = 0f
    /**
     * The spine as a **double-angle vector**, in rendered px: `d = L (cos 2θ, sin 2θ)`, where `L`
     * is the rendered half-spine and `θ` the capsule's orientation. The rendered body is the hull
     * of two discs of radius `r e^p` at `c ± L (cos θ, sin θ)`: the caps keep their curvature
     * however long the spine grows, which is what the tap-transit pill in the recording does
     * (JGEY2190 n176–182: the pill spans origin and destination at the same height).
     *
     * A capsule has 180° symmetry, so the doubled angle is what keeps the state continuous: a
     * reversal along the same axis leaves `d` where it is, and a pull across the axis shortens
     * `d` through a circle before growing it out along the new axis instead of snapping the
     * orientation. `(0, 0)` means "the reference's own spine, along the bar, scaled by `e^p`",
     * which is what the identity pose renders.
     */
    var dx: Float = 0f
    var dy: Float = 0f

    fun set(other: GlassPose) {
        cx = other.cx; cy = other.cy; p = other.p; u = other.u; v = other.v
        kx = other.kx; ky = other.ky; acc = other.acc; dx = other.dx; dy = other.dy
    }

    fun reset() {
        cx = 0f; cy = 0f; p = 0f; u = 0f; v = 0f; kx = 0f; ky = 0f; acc = 0f; dx = 0f; dy = 0f
    }

    val rho: Float get() = sqrt(u * u + v * v)
}

/** One point's worth of geometry, filled in place. */
internal class GlassPoseQuery {
    /** First-order normal-corrected signed distance; negative inside. NOT an exact deep SDF. */
    var coverageDistance: Float = 0f
    /** Outward unit normal in the rendered frame. */
    var normalX: Float = 0f
    var normalY: Float = 0f
    /** Transported curvature of the nearest canonical feature; 0 on a straight run. */
    var curvature: Float = 0f
    /** The canonical coordinate this point came from, for the optical source map. */
    var zx: Float = 0f
    var zy: Float = 0f
    /** False when the quadratic inverse has no real root, which can only happen outside coverage. */
    var valid: Boolean = true
}

/**
 * The pose map's derived quantities for one frame: the matrices, the bounds and the accommodation
 * geometry. Built once per frame on the CPU and shared by every query, the contour sampler, the
 * grasp and the uniforms, so no two of them can disagree.
 */
internal class GlassPoseFrame {
    var reference: GlassReference = GlassReference(1f, 1f)
        private set

    /** `A` and `A^-1`, row-major `(m00, m01, m10, m11)`. Both symmetric. */
    val a = FloatArray(4)
    val aInv = FloatArray(4)
    /** `b = k / R`. */
    var bx: Float = 0f
    var by: Float = 0f
    /** The half-spine of the capsule **in z**, and its radius. */
    var spine: Float = 0f
    var radius: Float = 0f
    /** `R`, the largest `|z|` the body reaches. */
    var maxAbsZ: Float = 1f
    /** Candidate A's horizontal scale, 1 for candidate B. */
    var scaleX: Float = 1f
    var cx: Float = 0f
    var cy: Float = 0f
    /** `exp(p)`, `cosh(rho)`, `sinh(rho)/rho`. */
    var expP: Float = 1f
    var coshRho: Float = 1f
    var sinchRho: Float = 1f
    var rho: Float = 0f

    /** The smallest and largest local stretch the map applies, for bands and bounds. */
    var minStretch: Float = 1f
    var maxStretch: Float = 1f

    /** The capsule's orientation, `(cos θ, sin θ)`, from the double-angle spine vector. */
    var cosTheta: Float = 1f
    var sinTheta: Float = 0f
    /** The rendered half-spine, in px, before accommodation is added to it. */
    var halfSpine: Float = 0f

    fun update(reference: GlassReference, pose: GlassPose) {
        this.reference = reference
        cx = pose.cx
        cy = pose.cy
        rho = pose.rho
        expP = exp(pose.p)
        // cosh and sinh(rho)/rho, with the series near zero so there is no 0/0 and no direction
        // state to flip when the strain passes through zero.
        if (rho < 1e-3f) {
            val r2 = rho * rho
            coshRho = 1f + r2 / 2f + r2 * r2 / 24f
            sinchRho = 1f + r2 / 6f + r2 * r2 / 120f
        } else {
            coshRho = cosh(rho)
            sinchRho = sinh(rho) / rho
        }
        val h = coshRho
        val s = sinchRho
        // Orientation and rendered half-spine from the double-angle vector. cos θ is never
        // negative, so θ lives in (-90°, 90°], which is every orientation a capsule can have.
        // Candidate B's accommodation is content laid out ALONG THE BAR, so it joins the vector
        // as a bar-axis term rather than lengthening whatever direction the spine happens to
        // have: for a horizontal spine it adds exactly `acc` px of half-spine, and for a spine
        // standing across the bar it shortens it toward a circle, which is the continuous
        // compromise a single capsule can offer.
        var ex = pose.dx
        var ey = pose.dy
        if (ex * ex + ey * ey <= 1e-6f) ex = reference.a * expP
        if (reference.accommodation == GlassAccommodation.Spine) ex += max(pose.acc, 0f)
        val dl = sqrt(ex * ex + ey * ey)
        if (dl > 1e-3f) {
            val c2 = ex / dl
            val s2 = ey / dl
            cosTheta = sqrt(max((1f + c2) / 2f, 0f))
            sinTheta = sqrt(max((1f - c2) / 2f, 0f)).let { if (s2 < 0f) -it else it }
            halfSpine = dl
        } else {
            cosTheta = 1f
            sinTheta = 0f
            halfSpine = 0f
        }
        // A = exp(p) R(θ) (H I + S M), M = [[u, v], [v, -u]]. The symmetric factor is the
        // trace-free strain (kept for the shader contract, no longer driven by the controller);
        // the rotation is the spine's orientation. A is a general 2x2 now, and every consumer -
        // the inverse below, the normal transform in [query] and the SkSL - multiplies it out
        // rather than assuming symmetry.
        val s00 = h + s * pose.u
        val s01 = s * pose.v
        val s11 = h - s * pose.u
        a[0] = expP * (cosTheta * s00 - sinTheta * s01)
        a[1] = expP * (cosTheta * s01 - sinTheta * s11)
        a[2] = expP * (sinTheta * s00 + cosTheta * s01)
        a[3] = expP * (sinTheta * s01 + cosTheta * s11)
        // A^-1 = exp(-p) (H I - S M) R(θ)^T; det(H I + S M) = H^2 - S^2 rho^2 = 1 exactly.
        val invP = 1f / expP
        val i00 = h - s * pose.u
        val i01 = -s * pose.v
        val i11 = h + s * pose.u
        aInv[0] = invP * (i00 * cosTheta - i01 * sinTheta)
        aInv[1] = invP * (i00 * sinTheta + i01 * cosTheta)
        aInv[2] = invP * (i01 * cosTheta - i11 * sinTheta)
        aInv[3] = invP * (i01 * sinTheta + i11 * cosTheta)

        when (reference.accommodation) {
            GlassAccommodation.Spine -> {
                scaleX = 1f
                // z is what A scales by exp(p): a rendered half-spine of L (accommodation
                // already in it) is L / exp(p) in z. At the identity pose this is exactly the
                // reference's `a + acc`.
                spine = max(halfSpine * invP, 0f)
                radius = reference.r
                maxAbsZ = spine + radius
            }
            GlassAccommodation.Uniform -> {
                scaleX = exp(pose.acc)
                spine = max(halfSpine * invP, 0f) * scaleX
                radius = reference.r
                // The z-body is an ellipse-capped capsule; its farthest point is on the x axis
                // whenever the horizontal scale is at least 1, and this bound is conservative
                // either way.
                maxAbsZ = max(scaleX * (halfSpine * invP + reference.r), sqrt(spine * spine + radius * radius))
            }
        }
        val rSafe = max(maxAbsZ, 1e-4f)
        bx = pose.kx / rSafe
        by = pose.ky / rSafe
        // A is symmetric with eigenvalues exp(p +- rho); F_k scales by (1 + dot(b, z)) in
        // [1 - |k|, 1 + |k|]. Both bounds are used for the optical band and the node extent.
        val taper = sqrt(pose.kx * pose.kx + pose.ky * pose.ky)
        minStretch = expP * exp(-rho) * max(1f - taper, 1e-3f)
        maxStretch = expP * exp(rho) * (1f + taper)
    }

    /** The canonical material point [qx], [qy] mapped into z. */
    fun materialToZ(qx: Float, qy: Float, out: FloatArray) {
        when (reference.accommodation) {
            GlassAccommodation.Spine -> {
                val aRef = reference.a
                val ell = spine - aRef
                val phi = if (aRef > GlassReference.GEOMETRY_EPSILON) (qx / aRef).coerceIn(-1f, 1f) else 0f
                out[0] = qx + ell * phi
                out[1] = qy
            }
            GlassAccommodation.Uniform -> {
                out[0] = qx * scaleX
                out[1] = qy
            }
        }
    }

    /** The inverse of [materialToZ]. */
    fun zToMaterial(zx: Float, zy: Float, out: FloatArray) {
        when (reference.accommodation) {
            GlassAccommodation.Spine -> {
                val aRef = reference.a
                val ell = spine - aRef
                out[0] = if (abs(zx) <= spine) {
                    if (spine > GlassReference.GEOMETRY_EPSILON) zx * aRef / spine else zx
                } else {
                    zx - (if (zx >= 0f) ell else -ell)
                }
                out[1] = zy
            }
            GlassAccommodation.Uniform -> {
                out[0] = if (scaleX > 1e-6f) zx / scaleX else zx
                out[1] = zy
            }
        }
    }

    /** `x = c + A F_k(z)`. */
    fun zToRendered(zx: Float, zy: Float, out: FloatArray) {
        val s = 1f + bx * zx + by * zy
        val fx = s * zx
        val fy = s * zy
        out[0] = cx + a[0] * fx + a[1] * fy
        out[1] = cy + a[2] * fx + a[3] * fy
    }

    /** The forward map from a canonical material point straight to the rendered frame. */
    fun materialToRendered(qx: Float, qy: Float, out: FloatArray) {
        materialToZ(qx, qy, out)
        zToRendered(out[0], out[1], out)
    }

    /**
     * The stable inverse of section 6.2: `y = A^-1 (x - c)`, then the continuous root of
     * `h lambda^2 + lambda = 1`. Writes `z` into [out] and returns false only when the
     * discriminant is negative, which cannot happen on or inside the body.
     */
    fun renderedToZ(x: Float, y: Float, out: FloatArray): Boolean {
        val dx = x - cx
        val dy = y - cy
        val yx = aInv[0] * dx + aInv[1] * dy
        val yy = aInv[2] * dx + aInv[3] * dy
        val h = bx * yx + by * yy
        val disc = 1f + 4f * h
        if (disc < 0f) {
            out[0] = yx
            out[1] = yy
            return false
        }
        // 2 / (1 + sqrt(1 + 4h)) is the root that is continuous at h = 0 and avoids the
        // cancellation the textbook form suffers there.
        val lambda = 2f / (1f + sqrt(disc))
        out[0] = lambda * yx
        out[1] = lambda * yy
        return true
    }

    /**
     * Signed distance, outward normal and transported curvature at a rendered point.
     *
     * The canonical capsule is evaluated **directly in z** for candidate B, which keeps the
     * piecewise material stretch out of boundary lighting; candidate A evaluates it in q and
     * carries `C` in the Jacobian, exactly as section 6.3 specifies. Both agree on the contour.
     */
    fun query(x: Float, y: Float, out: GlassPoseQuery): GlassPoseQuery {
        val z = scratchZ
        out.valid = renderedToZ(x, y, z)
        val zx = z[0]
        val zy = z[1]
        out.zx = zx
        out.zy = zy
        if (!out.valid) {
            // Outside coverage, not a NaN: report a large positive distance and a radial normal.
            val len = max(sqrt(zx * zx + zy * zy), 1e-4f)
            out.coverageDistance = len
            out.normalX = zx / len
            out.normalY = zy / len
            out.curvature = 0f
            return out
        }

        // The canonical capsule, in whichever space this candidate declares it in.
        val ex: Float
        val ey: Float
        val halfSpine: Float
        val rad: Float
        if (reference.accommodation == GlassAccommodation.Spine) {
            halfSpine = spine
            rad = radius
            ex = zx - zx.coerceIn(-halfSpine, halfSpine)
            ey = zy
        } else {
            zToMaterial(zx, zy, scratchQ)
            halfSpine = reference.a
            rad = reference.r
            ex = scratchQ[0] - scratchQ[0].coerceIn(-halfSpine, halfSpine)
            ey = scratchQ[1]
        }
        val len = sqrt(ex * ex + ey * ey)
        val d = len - rad
        // The canonical outward normal. On the spine the perpendicular direction is the normal;
        // at the medial axis it is not unique, and the optical band never reaches there.
        var n0x: Float
        var n0y: Float
        if (len > 1e-5f) {
            n0x = ex / len
            n0y = ey / len
        } else {
            n0x = 0f
            n0y = 1f
        }

        // J = A * DF_k(z) * (C for candidate A). gradient = J^-T n0.
        val s = 1f + bx * zx + by * zy
        // DF_k(z) = s I + z b^T
        var j0 = s + zx * bx
        var j1 = zx * by
        var j2 = zy * bx
        var j3 = s + zy * by
        if (reference.accommodation == GlassAccommodation.Uniform) {
            // Right-multiply by C = diag(scaleX, 1).
            j0 *= scaleX
            j2 *= scaleX
        }
        // Left-multiply by A.
        val m0 = a[0] * j0 + a[1] * j2
        val m1 = a[0] * j1 + a[1] * j3
        val m2 = a[2] * j0 + a[3] * j2
        val m3 = a[2] * j1 + a[3] * j3
        val det = m0 * m3 - m1 * m2
        val invDet = if (abs(det) > 1e-9f) 1f / det else 0f
        // J^-T = (1/det) * [[m3, -m2], [-m1, m0]]^T = (1/det) * [[m3, -m1], [-m2, m0]] ... written
        // out so the transpose is not left to the reader.
        val gx = invDet * (m3 * n0x - m2 * n0y)
        val gy = invDet * (-m1 * n0x + m0 * n0y)
        val gLen = sqrt(gx * gx + gy * gy)
        if (gLen > 1e-7f) {
            out.normalX = gx / gLen
            out.normalY = gy / gLen
            out.coverageDistance = d / gLen
        } else {
            out.normalX = n0x
            out.normalY = n0y
            out.coverageDistance = d
        }
        // Curvature, transported to first order by the tangential stretch. Exact for an affine
        // map; an approximation once the taper is nonzero, which is why it is named transported
        // rather than measured.
        val kappa0 = if (len > 1e-5f && rad > 1e-5f) 1f / rad else 0f
        if (kappa0 > 0f) {
            val tx = -n0y
            val ty = n0x
            val sx = m0 * tx + m1 * ty
            val sy = m2 * tx + m3 * ty
            val stretch = max(sqrt(sx * sx + sy * sy), 1e-5f)
            out.curvature = kappa0 / stretch
        } else {
            out.curvature = 0f
        }
        return out
    }

    /** The optical band: the canonical band under the **smallest** local stretch, so it can never
     *  reach the medial axis however the body is strained. */
    fun band(): Float = GlassMaterial.BEVEL_RATIO * radius * minStretch

    /** A conservative rendered half-extent about `c`, for node sizing and early-out. */
    fun boundingRadius(): Float = maxAbsZ * maxStretch

    /**
     * How far the body reaches above or below `c`, in rendered px, before the taper.
     *
     * Taken from the map itself rather than estimated from `p` and `rho`: `A` maps the z capsule,
     * so its bottom row alone decides the vertical extent — the disc of radius `r` reaches
     * `r |row2|` and the spine, along z's x axis, reaches `spine |A10|`. Exact for any `A`,
     * rotated or strained.
     */
    fun verticalHalfExtent(): Float =
        radius * sqrt(a[2] * a[2] + a[3] * a[3]) + spine * abs(a[2])

    /**
     * The rendered contour, sampled on the canonical capsule and pushed through the same map.
     * [into] receives `2 * count` interleaved x, y. Caps get twice the angular density of the
     * runs because they carry all the curvature.
     */
    fun contour(count: Int, into: FloatArray) {
        val n = max(count, 8)
        val out = scratchOut
        if (contourReference !== reference || contourPoints.size != n * 2) {
            contourReference = reference
            contourPoints = FloatArray(n * 2)
            for (i in 0 until n) {
                canonicalContourPoint(i.toFloat() / n, scratchQ)
                contourPoints[i * 2] = scratchQ[0]
                contourPoints[i * 2 + 1] = scratchQ[1]
            }
        }
        for (i in 0 until n) {
            materialToRendered(contourPoints[i * 2], contourPoints[i * 2 + 1], out)
            into[i * 2] = out[0]
            into[i * 2 + 1] = out[1]
        }
    }

    /**
     * A point on the canonical capsule's boundary at parameter [t] in [0, 1), traversed
     * counter-clockwise from the right cap. Arc length is distributed between the caps and the
     * runs so a long body does not starve its ends of samples.
     */
    private fun canonicalContourPoint(t: Float, out: FloatArray) {
        val aRef = reference.a
        val rRef = reference.r
        val capLen = kotlin.math.PI.toFloat() * rRef
        val runLen = 2f * aRef
        val total = 2f * capLen + 2f * runLen
        if (total <= 1e-6f) { out[0] = 0f; out[1] = 0f; return }
        var s = t * total
        if (s < capLen) {
            val ang = -kotlin.math.PI.toFloat() / 2f + (s / capLen) * kotlin.math.PI.toFloat()
            out[0] = aRef + rRef * kotlin.math.cos(ang)
            out[1] = rRef * kotlin.math.sin(ang)
            return
        }
        s -= capLen
        if (s < runLen) {
            out[0] = aRef - s
            out[1] = rRef
            return
        }
        s -= runLen
        if (s < capLen) {
            val ang = kotlin.math.PI.toFloat() / 2f + (s / capLen) * kotlin.math.PI.toFloat()
            out[0] = -aRef + rRef * kotlin.math.cos(ang)
            out[1] = rRef * kotlin.math.sin(ang)
            return
        }
        s -= capLen
        out[0] = -aRef + s
        out[1] = -rRef
    }

    private var contourReference: GlassReference? = null
    private var contourPoints = FloatArray(0)
    private val scratchZ = FloatArray(2)
    private val scratchQ = FloatArray(2)
    private val scratchOut = FloatArray(2)
}

/**
 * The rendered axis-aligned extents of a pose, from its sampled contour. Reported rather than
 * derived from the reference, because taper moves the rendered centroid away from `c`.
 */
internal class GlassPoseExtents {
    var left: Float = 0f
    var right: Float = 0f
    var top: Float = 0f
    var bottom: Float = 0f
    var centroidX: Float = 0f
    var centroidY: Float = 0f

    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

internal fun glassPoseExtents(frame: GlassPoseFrame, samples: Int, buffer: FloatArray, out: GlassPoseExtents): GlassPoseExtents {
    frame.contour(samples, buffer)
    var lo = Float.MAX_VALUE
    var hi = -Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var bot = -Float.MAX_VALUE
    var sx = 0.0
    var sy = 0.0
    for (i in 0 until samples) {
        val x = buffer[i * 2]
        val y = buffer[i * 2 + 1]
        lo = min(lo, x); hi = max(hi, x)
        top = min(top, y); bot = max(bot, y)
        sx += x.toDouble(); sy += y.toDouble()
    }
    out.left = lo; out.right = hi; out.top = top; out.bottom = bot
    out.centroidX = (sx / samples).toFloat()
    out.centroidY = (sy / samples).toFloat()
    return out
}


/**
 * One frame's pose, reduced to exactly what the shaders need, in the **element's centred frame**.
 *
 * The controller works in bar-local coordinates and the shaders in element-centred ones, so the
 * translation happens once, here, rather than in three passes that could disagree. Immutable so
 * it can key the uniform cache: a pose that has not changed must not rebuild fifty uniforms.
 */
internal class GlassPoseRender(
    val a: FloatArray,
    val aInv: FloatArray,
    /** `(cx, cy, bx, by)`: centre in the element's centred frame, and `b = k / R`. */
    val c: FloatArray,
    /** `(spine, radius, scaleX, band)`. */
    val d: FloatArray,
) {
    override fun equals(other: Any?): Boolean =
        other is GlassPoseRender && a.contentEquals(other.a) && aInv.contentEquals(other.aInv) &&
            c.contentEquals(other.c) && d.contentEquals(other.d)

    override fun hashCode(): Int =
        ((a.contentHashCode() * 31 + aInv.contentHashCode()) * 31 + c.contentHashCode()) * 31 +
            d.contentHashCode()
}

/**
 * Snapshot [frame] for rendering, translating its centre by [dx], [dy] into the element's frame.
 *
 * The translation is the node's own origin: the same rounded origin the node's placement, the
 * native ink and the touch point use, so the contour, the aperture and the finger cannot end up
 * in three slightly different frames.
 */
internal fun glassPoseRenderOf(frame: GlassPoseFrame, dx: Float, dy: Float): GlassPoseRender =
    GlassPoseRender(
        a = frame.a.copyOf(),
        aInv = frame.aInv.copyOf(),
        c = floatArrayOf(frame.cx - dx, frame.cy - dy, frame.bx, frame.by),
        d = floatArrayOf(frame.spine, frame.radius, frame.scaleX, frame.band()),
    )

package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Whole-body containment as linear inequalities in `q = (cx, cy, L, r, k)`.
 *
 * A disk of centre `C` and radius `R` lies inside a half-plane `n·x <= b` exactly when
 * `n·C + R <= b`. Substituting the body's two disks turns every plane of an **inscribed** polygon
 * of the allowed outline into two rows that are linear in `q`:
 *
 * ```
 * nx*cx + ny*cy - 0.5*nx*L + r - (nx + 1)*k <= b     (left disk)
 * nx*cx + ny*cy + 0.5*nx*L + r + (1 - nx)*k <= b     (right disk)
 * ```
 *
 * Both disks inside a convex region puts their whole convex hull inside it, so this is stronger
 * than testing a centre, a bounding box, or a handful of contour samples. The polygon is an inner
 * approximation, which makes the constraint conservative: it cannot permit a protrusion between
 * two tested angles.
 */
internal class GlassBodyConstraints(capacity: Int) {
    /** Row-major, five columns per row. */
    val a: FloatArray = FloatArray(capacity * 5)
    val b: FloatArray = FloatArray(capacity)
    var rows: Int = 0
        private set

    /**
     * True when a plane did not fit.
     *
     * A dropped plane is the worst kind of failure this class can have: the projection reports
     * success while the body escapes exactly where the missing plane was, and the escape appears
     * as a snap the moment the set changes. So overflow is recorded and the caller treats it as a
     * solver failure rather than discovering it on a device.
     */
    var overflowed: Boolean = false
        private set

    fun clear() {
        rows = 0
        overflowed = false
    }

    fun add(c0: Float, c1: Float, c2: Float, c3: Float, c4: Float, rhs: Float) {
        if ((rows + 1) * 5 > a.size) {
            overflowed = true
            return
        }
        val i = rows * 5
        a[i] = c0; a[i + 1] = c1; a[i + 2] = c2; a[i + 3] = c3; a[i + 4] = c4
        b[rows] = rhs
        rows++
    }

    /**
     * Change one row's right-hand side in place.
     *
     * A release's rest rows have fixed coefficients and a right-hand side that decays every
     * substep. Rewriting only `b` keeps the trigonometric polygon data - about a hundred planes,
     * each a cosine and a sine - out of the substep hot path, which is what rebuilding the whole
     * set every frame of a release used to put there.
     */
    fun setRhs(row: Int, rhs: Float) {
        if (row in 0 until rows) b[row] = rhs
    }

    /** Both disks inside the half-plane `n·x <= rhs`. */
    fun addHalfPlane(nx: Float, ny: Float, rhs: Float) {
        add(nx, ny, -0.5f * nx, 1f, -(nx + 1f), rhs)
        add(nx, ny, 0.5f * nx, 1f, 1f - nx, rhs)
    }

    fun value(row: Int, q: FloatArray): Float {
        val i = row * 5
        return a[i] * q[0] + a[i + 1] * q[1] + a[i + 2] * q[2] + a[i + 3] * q[3] + a[i + 4] * q[4]
    }

    /** How far row [row] is violated at [q]; 0 when satisfied. */
    fun excess(row: Int, q: FloatArray): Float = max(0f, value(row, q) - b[row])

    fun worstExcess(q: FloatArray): Float {
        var worst = 0f
        for (row in 0 until rows) worst = max(worst, excess(row, q))
        return worst
    }
}

/**
 * The allowed outline a tap-travelling body must stay inside, as an inscribed convex polygon.
 *
 * [halfWidth]/[halfHeight] are the rounded rect's half extents about [centreX]/[centreY] and
 * [cornerRadius] its corner. The angular step is chosen from the radius so the inward chord
 * error `R * (1 - cos(dTheta/2))` stays under [chordErrorPx]; straight runs stay exact. The
 * count is recomputed for every size rather than frozen, so a larger bar does not quietly get a
 * coarser polygon.
 */
internal fun GlassBodyConstraints.addInscribedRoundRect(
    centreX: Float,
    centreY: Float,
    halfWidth: Float,
    halfHeight: Float,
    cornerRadius: Float,
    chordErrorPx: Float = 0.2f,
) {
    val r = cornerRadius.coerceIn(0f, min(halfWidth, halfHeight))
    // Straight runs: exact, four axis-aligned planes.
    addHalfPlane(-1f, 0f, -(centreX - halfWidth))
    addHalfPlane(1f, 0f, centreX + halfWidth)
    addHalfPlane(0f, -1f, -(centreY - halfHeight))
    addHalfPlane(0f, 1f, centreY + halfHeight)
    if (r <= 0.5f) return
    // Corners: enough planes that the inward chord error of the arc stays under the target.
    val maxStep = 2f * acos((1f - chordErrorPx / r).coerceIn(-1f, 1f))
    val perQuadrant = max(2, ceil((kotlin.math.PI.toFloat() / 2f) / max(maxStep, 1e-3f)).toInt())
    for (quadrant in 0..3) {
        val sx = if (quadrant == 1 || quadrant == 2) 1f else -1f
        val sy = if (quadrant >= 2) 1f else -1f
        val ccx = centreX + sx * (halfWidth - r)
        val ccy = centreY + sy * (halfHeight - r)
        for (i in 1 until perQuadrant) {
            val t = i.toFloat() / perQuadrant.toFloat()
            val angle = t * (kotlin.math.PI.toFloat() / 2f)
            val nx = sx * cos(angle)
            val ny = sy * sin(angle)
            addHalfPlane(nx, ny, nx * ccx + ny * ccy + r)
        }
    }
}

/**
 * The body's own shape limits: neither disk may vanish or contain the other, and the body has a
 * declared maximum size. All linear in `q`, so they join the containment rows in one projection
 * rather than being clamped afterwards (a clamp after a projection can put the body back outside
 * the bar).
 */
internal fun GlassBodyConstraints.addShapeLimits(
    minRadius: Float,
    minLength: Float,
    maxWidth: Float,
    maxHalfHeight: Float,
) {
    add(0f, 0f, 0f, -1f, 1f, -minRadius)      // r - k >= rMin
    add(0f, 0f, 0f, -1f, -1f, -minRadius)     // r + k >= rMin
    add(0f, 0f, -1f, 0f, 2f, -minLength)      // L >= 2k + ellMin
    add(0f, 0f, -1f, 0f, -2f, -minLength)     // L >= -2k + ellMin
    add(0f, 0f, 1f, 2f, 0f, maxWidth)         // L + 2r <= maxWidth
    add(0f, 0f, 0f, 1f, 1f, maxHalfHeight)    // r + k <= H
    add(0f, 0f, 0f, 1f, -1f, maxHalfHeight)   // r - k <= H
}

internal fun GlassBody.toArray(out: FloatArray): FloatArray {
    out[0] = cx; out[1] = cy; out[2] = length; out[3] = radius; out[4] = skew
    return out
}

internal fun glassBodyOf(q: FloatArray): GlassBody = GlassBody(q[0], q[1], q[2], q[3], q[4])

/**
 * The five-variable projection: `min ½(q-q*)ᵀ M (q-q*)` subject to `A q <= b`.
 *
 * Its dual is a nonnegative least-squares problem. With `G = A M⁻¹ Aᵀ` and `c = A q* - b`,
 * the multipliers solve `min ½ λᵀ G λ - λᵀ c` over `λ >= 0`, and the projected point is then
 * `q = q* - M⁻¹ Aᵀ λ` exactly. Lawson-Hanson on `G` terminates finitely and, unlike a
 * hand-rolled add-one-plane loop, does not stall when several planes are violated at once (a
 * body asked to be wider than the bar, shorter than its own end gap and past its right wall all
 * at the same time is an ordinary request from the size demand, not a pathological one).
 *
 * Only rows that are violated, or that become violated as the solve proceeds, ever enter `G`, so
 * the matrix is a handful of rows wide in practice rather than the full polygon. Iterations are
 * capped, a dependent row is regularized rather than inverted, and a failure reports itself
 * through [failed] so the caller can keep the last feasible body instead of rendering an
 * impossible one.
 *
 * [metric] is the authored projection metric `diag(4,4,1,1,1)` in `(cx,cy,L,r,k)` order: giving
 * up the declared centre costs more than relaxing the shape. It is a conditioning choice, not a
 * measured mass.
 */
internal class GlassBodyProjector(private val maxCandidates: Int = 48) {
    private val candidate = IntArray(maxCandidates)
    private val multiplier = FloatArray(maxCandidates)
    private var candidates = 0
    private val gram = FloatArray(maxCandidates * maxCandidates)
    private val rhs = FloatArray(maxCandidates)
    private val lambda = FloatArray(maxCandidates)
    private val trial = FloatArray(maxCandidates)
    private val gradient = FloatArray(maxCandidates)
    private val passive = BooleanArray(maxCandidates)
    private val index = IntArray(maxCandidates)
    private val sub = FloatArray(maxCandidates * maxCandidates)
    private val subRhs = FloatArray(maxCandidates)
    private val chol = FloatArray(maxCandidates * maxCandidates)
    // Sized for the whole polygon, not for the working set: during a release most rest planes sit
    // within the active tolerance at once, and a dropped active row is a silently unenforced wall.
    private val velocityRows = GlassBodyConstraints(512)

    /** True when the last [project] could not reach a feasible point. */
    var failed: Boolean = false
        private set

    /** How many planes carried a non-zero multiplier at the end of the last solve. */
    var activeRows: Int = 0
        private set

    fun project(
        target: FloatArray,
        constraints: GlassBodyConstraints,
        out: FloatArray,
        metric: FloatArray = DefaultMetric,
        tolerance: Float = 1e-4f,
        maxPasses: Int = 40,
    ): Boolean {
        for (i in 0..4) out[i] = target[i]
        candidates = 0
        var pass = 0
        // Constraint generation: cut with the single worst violated plane, re-project exactly,
        // repeat. Every generated plane that is still doing work is kept; one that has gone
        // slack and carries no multiplier is dropped again, so the working set stays a handful
        // of rows wide even against a polygon with a hundred of them.
        while (pass++ < maxPasses) {
            var worst = tolerance
            var pick = -1
            for (row in 0 until constraints.rows) {
                if (isCandidate(row)) continue
                val e = constraints.value(row, out) - constraints.b[row]
                if (e > worst) {
                    worst = e
                    pick = row
                }
            }
            if (pick < 0) break
            prune(constraints, out, tolerance)
            if (!addCandidate(pick)) break
            solveDual(target, constraints, metric, out)
        }
        failed = constraints.worstExcess(out) > max(tolerance * 20f, 2e-3f)
        if (failed) for (i in 0..4) out[i] = target[i]
        return !failed
    }

    /** Forget generated planes that have gone slack and carry no multiplier. */
    private fun prune(constraints: GlassBodyConstraints, out: FloatArray, tolerance: Float) {
        var kept = 0
        for (i in 0 until candidates) {
            val row = candidate[i]
            val slack = constraints.b[row] - constraints.value(row, out)
            if (multiplier[i] > 0f || slack < tolerance * 100f) {
                candidate[kept] = row
                multiplier[kept] = multiplier[i]
                kept++
            }
        }
        candidates = kept
    }

    private fun isCandidate(row: Int): Boolean {
        for (i in 0 until candidates) if (candidate[i] == row) return true
        return false
    }

    private fun addCandidate(row: Int): Boolean {
        if (candidates >= maxCandidates) return false
        candidate[candidates++] = row
        return true
    }

    /** Lawson-Hanson on the dual, then the exact primal recovery. */
    private fun solveDual(
        target: FloatArray,
        constraints: GlassBodyConstraints,
        metric: FloatArray,
        out: FloatArray,
    ) {
        val n = candidates
        for (i in 0 until n) {
            val ri = candidate[i] * 5
            for (j in i until n) {
                val rj = candidate[j] * 5
                var g = 0f
                for (c in 0..4) g += constraints.a[ri + c] * constraints.a[rj + c] / metric[c]
                gram[i * maxCandidates + j] = g
                gram[j * maxCandidates + i] = g
            }
            rhs[i] = constraints.value(candidate[i], target) - constraints.b[candidate[i]]
            lambda[i] = 0f
            passive[i] = false
        }
        var iterations = 0
        val maxIterations = 3 * n + 12
        while (iterations++ < maxIterations) {
            // gradient = c - G lambda; a positive entry off the passive set can still improve.
            var best = 1e-7f
            var pick = -1
            for (i in 0 until n) {
                if (passive[i]) continue
                var g = rhs[i]
                for (j in 0 until n) g -= gram[i * maxCandidates + j] * lambda[j]
                gradient[i] = g
                if (g > best) {
                    best = g
                    pick = i
                }
            }
            if (pick < 0) break
            passive[pick] = true
            var inner = 0
            while (inner++ < n + 4) {
                var m = 0
                for (i in 0 until n) if (passive[i]) index[m++] = i
                if (m == 0) break
                for (a in 0 until m) {
                    for (b in 0 until m) sub[a * maxCandidates + b] = gram[index[a] * maxCandidates + index[b]]
                    sub[a * maxCandidates + a] += 1e-6f
                    subRhs[a] = rhs[index[a]]
                }
                if (!cholesky(m)) {
                    passive[index[m - 1]] = false
                    continue
                }
                var negative = false
                for (a in 0 until m) {
                    trial[index[a]] = subRhs[a]
                    if (subRhs[a] <= 0f) negative = true
                }
                for (i in 0 until n) if (!passive[i]) trial[i] = 0f
                if (!negative) {
                    for (i in 0 until n) lambda[i] = trial[i]
                    break
                }
                var alpha = 1f
                for (a in 0 until m) {
                    val i = index[a]
                    if (trial[i] <= 0f) {
                        val denominator = lambda[i] - trial[i]
                        if (denominator > 1e-9f) alpha = min(alpha, lambda[i] / denominator)
                    }
                }
                for (i in 0 until n) lambda[i] += alpha * (trial[i] - lambda[i])
                var dropped = false
                for (i in 0 until n) {
                    if (passive[i] && lambda[i] <= 1e-7f) {
                        lambda[i] = 0f
                        passive[i] = false
                        dropped = true
                    }
                }
                if (!dropped) break
            }
        }
        var active = 0
        for (c in 0..4) {
            var acc = 0f
            for (i in 0 until n) {
                if (lambda[i] <= 0f) continue
                acc += constraints.a[candidate[i] * 5 + c] * lambda[i]
            }
            out[c] = target[c] - acc / metric[c]
        }
        for (i in 0 until n) {
            if (lambda[i] > 0f) active++
            multiplier[i] = lambda[i]
        }
        activeRows = active
    }

    /** Cholesky solve of `sub` against `subRhs`, in place; false when not positive definite. */
    private fun cholesky(n: Int): Boolean {
        for (i in 0 until n) {
            for (j in 0..i) {
                var acc = sub[i * maxCandidates + j]
                for (k in 0 until j) acc -= chol[i * maxCandidates + k] * chol[j * maxCandidates + k]
                if (i == j) {
                    if (acc <= 1e-9f) return false
                    chol[i * maxCandidates + i] = sqrt(acc)
                } else {
                    chol[i * maxCandidates + j] = acc / chol[j * maxCandidates + j]
                }
            }
        }
        for (i in 0 until n) {
            var acc = subRhs[i]
            for (k in 0 until i) acc -= chol[i * maxCandidates + k] * subRhs[k]
            subRhs[i] = acc / chol[i * maxCandidates + i]
        }
        for (i in n - 1 downTo 0) {
            var acc = subRhs[i]
            for (k in i + 1 until n) acc -= chol[k * maxCandidates + i] * subRhs[k]
            subRhs[i] = acc / chol[i * maxCandidates + i]
        }
        return true
    }

    /**
     * Velocity against the planes that are currently active: `min ½(v-v*)ᵀ M (v-v*)` subject to
     * `A_act v <= limit`. This removes outward motion without adding a bounce - the feasible cone
     * contains zero, so the weighted speed cannot increase - and it is a projection, never a
     * position correction divided by dt.
     *
     * The limit is zero for a static wall and `b_j'(t)` for a moving one, which may legitimately
     * do work on the body. **Per row**, because different release planes have different speeds and
     * a single scalar cannot express them: requiring zero against a wall that is shrinking faster
     * than the body can follow is infeasible, and answering that infeasibility by zeroing every
     * velocity hides a real motion.
     *
     * Returns true when a feasible velocity was found. On failure [out] is left untouched and the
     * caller publishes a checked fallback; zero is **not** assumed to be feasible.
     */
    fun projectVelocity(
        target: FloatArray,
        constraints: GlassBodyConstraints,
        position: FloatArray,
        out: FloatArray,
        metric: FloatArray = DefaultMetric,
        activeTolerance: Float = 0.5f,
        limits: FloatArray? = null,
    ): Boolean {
        velocityRows.clear()
        for (row in 0 until constraints.rows) {
            if (constraints.value(row, position) >= constraints.b[row] - activeTolerance) {
                val i = row * 5
                velocityRows.add(
                    constraints.a[i], constraints.a[i + 1], constraints.a[i + 2],
                    constraints.a[i + 3], constraints.a[i + 4],
                    limits?.getOrNull(row) ?: 0f,
                )
            }
        }
        if (velocityRows.overflowed) return false
        if (velocityRows.rows == 0) {
            for (i in 0..4) out[i] = target[i]
            return true
        }
        // A corner of the inscribed polygon can make fifty rows active at once, so the generated
        // working set is given room; the optimum still carries only a handful of multipliers.
        if (project(target, velocityRows, out, metric, maxPasses = 96)) return true
        // The exact projection did not converge. Standing still is feasible exactly when every
        // active row's limit is nonnegative - true for a static wall, false for one that is
        // shrinking faster than a stationary body can follow - so it is **checked**, not assumed.
        for (i in 0..4) zero[i] = 0f
        if (velocityRows.worstExcess(zero) <= 1e-3f) {
            for (i in 0..4) out[i] = 0f
            return true
        }
        return false
    }

    private val zero = FloatArray(5)

    companion object {
        /** `diag(4,4,1,1,1)`: keeping the declared centre costs more than relaxing the shape. */
        val DefaultMetric = floatArrayOf(4f, 4f, 1f, 1f, 1f)
    }
}

/** True when every row of [constraints] holds at [q] to within [tolerance]. */
internal fun glassBodyFeasible(constraints: GlassBodyConstraints, q: FloatArray, tolerance: Float = 1e-3f): Boolean =
    constraints.worstExcess(q) <= tolerance

/**
 * The largest outward protrusion of [body]'s contour past the rounded rect it is meant to stay
 * inside, in px; 0 when it is contained.
 *
 * Exact and constant work. The body is the convex hull of two disks and the allowed region is
 * convex, so the deepest escape is attained at one of the disks, and a disk of radius R escapes a
 * convex region by exactly `signedDistance(centre) + R`. Sampling the contour instead - which is
 * what this used to do - costs a thousand square roots on a value that is read on every frame the
 * component publishes its state.
 */
internal fun glassBodyProtrusion(
    body: GlassBody,
    centreX: Float,
    centreY: Float,
    halfWidth: Float,
    halfHeight: Float,
    cornerRadius: Float,
): Float {
    val left = roundRectSigned(
        body.leftCentreX, body.cy, centreX, centreY, halfWidth, halfHeight, cornerRadius,
    ) + body.leftRadius
    val right = roundRectSigned(
        body.rightCentreX, body.cy, centreX, centreY, halfWidth, halfHeight, cornerRadius,
    ) + body.rightRadius
    return max(max(left, right), 0f)
}

/** Signed distance from a point to a rounded rect; negative inside. */
private fun roundRectSigned(
    px: Float,
    py: Float,
    centreX: Float,
    centreY: Float,
    halfWidth: Float,
    halfHeight: Float,
    cornerRadius: Float,
): Float {
    val r = cornerRadius.coerceIn(0f, min(halfWidth, halfHeight))
    val qx = abs(px - centreX) - halfWidth + r
    val qy = abs(py - centreY) - halfHeight + r
    val outside = sqrt(max(qx, 0f) * max(qx, 0f) + max(qy, 0f) * max(qy, 0f))
    return min(max(qx, qy), 0f) + outside - r
}


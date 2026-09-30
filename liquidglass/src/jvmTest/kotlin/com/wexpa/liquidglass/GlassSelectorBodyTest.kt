package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The cheap geometry and algebra gates of `V3-MODEL.md` section 15.1, run before anything is
 * rendered.
 *
 * Nothing here re-states the implementation's own expression and calls the agreement a proof.
 * The distance query is scored against a **dense boundary oracle** built from the hull's
 * definition rather than from its closed form; containment is scored against the body's actual
 * sampled contour rather than against the constraint rows that produced it; and the centre
 * invariant is checked in double precision so a parameter-conversion mistake cannot hide inside
 * Float noise.
 */
class GlassSelectorBodyTest {

    /**
     * The oracle: the hull's boundary, sampled finely, as points. A body is the convex hull of
     * two disks, so its boundary is the exposed part of each circle plus the two common
     * tangents. This builds those directly from the definition.
     */
    private fun boundaryOracle(body: GlassBody, samples: Int = 24000): DoubleArray {
        val pts = DoubleArray(samples * 2)
        val rL = body.leftRadius.toDouble()
        val rR = body.rightRadius.toDouble()
        val clx = body.leftCentreX.toDouble()
        val crx = body.rightCentreX.toDouble()
        val cy = body.cy.toDouble()
        val delta = (2.0 * body.skew) / body.length
        val gamma = sqrt(max(1.0 - delta * delta, 0.0))
        var i = 0
        val arcs = samples / 2
        while (i < arcs) {
            val t = i.toDouble() / arcs
            val angle = t * 2.0 * Math.PI
            val nx = cos(angle)
            val ny = sin(angle)
            if (nx <= -delta) {
                pts[i * 2] = clx + rL * nx
                pts[i * 2 + 1] = cy + rL * ny
            } else {
                pts[i * 2] = crx + rR * nx
                pts[i * 2 + 1] = cy + rR * ny
            }
            i++
        }
        val runs = samples - arcs
        var j = 0
        while (j < runs) {
            val t = (j / 2).toDouble() / (runs / 2).toDouble()
            val sny = if (j % 2 == 0) -gamma else gamma
            val ax = clx + rL * -delta
            val ay = cy + rL * sny
            val bx = crx + rR * -delta
            val by = cy + rR * sny
            pts[(arcs + j) * 2] = ax + t * (bx - ax)
            pts[(arcs + j) * 2 + 1] = ay + t * (by - ay)
            j++
        }
        return pts
    }

    private fun oracleDistance(pts: DoubleArray, px: Double, py: Double): Double {
        var best = Double.MAX_VALUE
        var i = 0
        while (i < pts.size) {
            val dx = pts[i] - px
            val dy = pts[i + 1] - py
            val d = dx * dx + dy * dy
            if (d < best) best = d
            i += 2
        }
        return sqrt(best)
    }

    private val bodies = listOf(
        // capsule, the V2 shape in V3 coordinates
        GlassBody(cx = 300f, cy = 80f, length = 160f, radius = 60f, skew = 0f),
        // mildly and strongly tapered, both signs
        GlassBody(cx = 300f, cy = 80f, length = 160f, radius = 60f, skew = 14f),
        GlassBody(cx = 300f, cy = 80f, length = 160f, radius = 60f, skew = -14f),
        GlassBody(cx = 120f, cy = 40f, length = 90f, radius = 34f, skew = 28f),
        // nearly degenerate: L only just larger than |2k|, and a tiny body
        GlassBody(cx = 50f, cy = 25f, length = 46f, radius = 30f, skew = 22f),
        GlassBody(cx = 10f, cy = 10f, length = 2f, radius = 5f, skew = 0.5f),
        // long and thin, the shape a fast tap makes
        GlassBody(cx = 500f, cy = 81f, length = 420f, radius = 52f, skew = -9f),
    )

    @Test
    fun theAnalyticQueryMatchesADenseBoundaryOracleAwayFromMedialTies() {
        var worstDistance = 0.0
        var worstPoint = 0.0
        var checked = 0
        for (body in bodies) {
            assertTrue(body.isWellFormed, "test body must satisfy the tangent constraint: $body")
            val oracle = boundaryOracle(body)
            val q = GlassBodyQuery()
            val minRadius = min(body.leftRadius, body.rightRadius)
            var gx = -1.4f
            while (gx <= 1.4f) {
                var gy = -1.6f
                while (gy <= 1.6f) {
                    val px = body.cx + gx * body.halfWidth
                    val py = body.cy + gy * body.halfHeight
                    glassBodyQuery(body, px, py, q)
                    // Away from the medial axis, where the nearest feature is not unique.
                    if (-q.distance < 0.7f * minRadius) {
                        val expected = oracleDistance(oracle, px.toDouble(), py.toDouble())
                        worstDistance = max(worstDistance, abs(abs(q.distance) - expected))
                        // The nearest boundary point must itself be on the boundary and at that
                        // distance from p, which is a stronger statement than the scalar alone.
                        val onBoundary = oracleDistance(oracle, q.boundaryX.toDouble(), q.boundaryY.toDouble())
                        val toPoint = sqrt(
                            ((q.boundaryX - px).toDouble() * (q.boundaryX - px)) +
                                ((q.boundaryY - py).toDouble() * (q.boundaryY - py)),
                        )
                        worstPoint = max(worstPoint, max(onBoundary, abs(toPoint - expected)))
                        checked++
                    }
                    gy += 0.05f
                }
                gx += 0.02f
            }
        }
        assertTrue(checked > 5000, "the sweep must actually cover the band: $checked points")
        assertTrue(worstDistance <= 0.05, "distance error $worstDistance px exceeds 0.05")
        assertTrue(worstPoint <= 0.05, "nearest-point error $worstPoint px exceeds 0.05")
    }

    @Test
    fun atZeroSkewTheBodyIsExactlyACapsule() {
        val body = GlassBody(cx = 240f, cy = 70f, length = 180f, radius = 55f, skew = 0f)
        val q = GlassBodyQuery()
        var worst = 0f
        var gx = -1.5f
        while (gx <= 1.5f) {
            var gy = -1.8f
            while (gy <= 1.8f) {
                val px = body.cx + gx * body.halfWidth
                val py = body.cy + gy * body.halfHeight
                glassBodyQuery(body, px, py, q)
                // The textbook capsule: distance to the centre segment minus the radius.
                val ax = body.leftCentreX
                val bx = body.rightCentreX
                val t = ((px - ax) / (bx - ax)).coerceIn(0f, 1f)
                val nx = ax + t * (bx - ax)
                val expected = sqrt((px - nx) * (px - nx) + (py - body.cy) * (py - body.cy)) - body.radius
                worst = max(worst, abs(q.distance - expected))
                gy += 0.03f
            }
            gx += 0.02f
        }
        assertTrue(worst <= 1e-3f, "capsule agreement off by $worst px")
    }

    @Test
    fun endAsymmetryNeverMovesTheDeclaredHorizontalCentre() {
        var worst = 0.0
        val cx = 317.25
        val l = 163.5
        val r = 58.75
        for (i in -40..40) {
            val k = i * 0.5
            if (l <= 2 * abs(k) + 1.0) continue
            val body = GlassBody(cx.toFloat(), 80f, l.toFloat(), r.toFloat(), k.toFloat())
            // Double-precision reference for the same parameters.
            val expectedLeft = cx - (l / 2 + r)
            val expectedRight = cx + (l / 2 + r)
            val actualLeft = (body.leftCentreX - body.leftRadius).toDouble()
            val actualRight = (body.rightCentreX + body.rightRadius).toDouble()
            worst = max(worst, max(abs(actualLeft - expectedLeft), abs(actualRight - expectedRight)))
            // And the vertical extent really is r + |k|, not 2r.
            assertEquals((r + abs(k)).toFloat(), body.halfHeight, 1e-3f)
        }
        assertTrue(worst <= 1e-4, "extrema moved with k by $worst px")
    }

    /** The bar of a five-item Vitals-sized nav bar, in physical px at 3x. */
    private fun barConstraints(halfHeight: Float = 75f): GlassBodyConstraints {
        val c = GlassBodyConstraints(160)
        c.addInscribedRoundRect(
            centreX = 503f,
            centreY = 81f,
            halfWidth = 503f,
            halfHeight = halfHeight,
            cornerRadius = halfHeight,
        )
        c.addShapeLimits(minRadius = 12f, minLength = 3f, maxWidth = 1006f, maxHalfHeight = halfHeight)
        return c
    }

    @Test
    fun theProjectionContainsTheWholeContourNotJustItsCentre() {
        val constraints = barConstraints()
        val projector = GlassBodyProjector()
        val out = FloatArray(5)
        var worstProtrusion = 0f
        var cases = 0
        // Deliberately infeasible requests: past both ends, taller than the bar, every skew sign,
        // and the widest and narrowest bodies the limits allow.
        for (cx in listOf(-120f, 0f, 60f, 503f, 950f, 1120f)) {
            for (length in listOf(4f, 60f, 200f, 620f, 1400f)) {
                for (radius in listOf(14f, 40f, 70f, 110f)) {
                    for (skew in listOf(-40f, -12f, 0f, 12f, 40f)) {
                        val q = floatArrayOf(cx, 81f, length, radius, skew)
                        val ok = projector.project(q, constraints, out)
                        assertTrue(ok, "solver failed for ${q.toList()}")
                        val body = glassBodyOf(out)
                        assertTrue(body.isWellFormed, "infeasible body produced: $body")
                        worstProtrusion = max(
                            worstProtrusion,
                            glassBodyProtrusion(body, 503f, 81f, 503f, 75f, 75f),
                        )
                        cases++
                    }
                }
            }
        }
        assertTrue(cases == 600, "expected the full sweep, ran $cases")
        assertTrue(
            worstProtrusion <= 0.2f,
            "contour escaped the bar by $worstProtrusion px, past the 0.2 px conservative target",
        )
    }

    @Test
    fun aFeasibleRequestIsReturnedUntouched() {
        val constraints = barConstraints()
        val projector = GlassBodyProjector()
        val out = FloatArray(5)
        val q = floatArrayOf(503f, 81f, 120f, 60f, 6f)
        assertTrue(projector.project(q, constraints, out))
        for (i in 0..4) assertEquals(q[i], out[i], 1e-4f, "coordinate $i was moved for a feasible body")
        assertEquals(0, projector.activeRows)
    }

    @Test
    fun velocityProjectionRemovesOutwardMotionAndNeverAddsSpeed() {
        val constraints = barConstraints()
        val projector = GlassBodyProjector()
        val metric = GlassBodyProjector.DefaultMetric
        val position = FloatArray(5)
        val out = FloatArray(5)
        // A body pressed into the right end of the bar and against its height limit.
        projector.project(floatArrayOf(1400f, 81f, 300f, 120f, 20f), constraints, position)
        var worstOutward = 0f
        var worstGrowth = 0f
        var seed = 12345L
        fun rnd(): Float {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((seed ushr 33).toFloat() / (1L shl 31).toFloat()) * 2f - 1f
        }
        repeat(400) {
            val v = floatArrayOf(rnd() * 4000f, rnd() * 600f, rnd() * 2000f, rnd() * 400f, rnd() * 300f)
            projector.projectVelocity(v, constraints, position, out)
            for (row in 0 until constraints.rows) {
                if (constraints.value(row, position) < constraints.b[row] - 0.5f) continue
                var s = 0f
                val i = row * 5
                for (c in 0..4) s += constraints.a[i + c] * out[c]
                worstOutward = max(worstOutward, s)
            }
            var before = 0f
            var after = 0f
            for (c in 0..4) {
                before += metric[c] * v[c] * v[c]
                after += metric[c] * out[c] * out[c]
            }
            worstGrowth = max(worstGrowth, sqrt(after) - sqrt(before))
        }
        assertTrue(worstOutward <= 1e-2f, "outward active-plane velocity survived: $worstOutward px/s")
        assertTrue(worstGrowth <= 1e-2f, "the projection added $worstGrowth to the weighted speed")
    }

    @Test
    fun theInscribedPolygonIsConservativeAtEveryRadius() {
        // The inner approximation must never permit a protrusion between tested angles, at any
        // size: the step count comes from the radius rather than being frozen.
        for (halfHeight in listOf(12f, 40f, 75f, 160f, 400f)) {
            val c = GlassBodyConstraints(2048)
            c.addInscribedRoundRect(200f, halfHeight, 200f, halfHeight, halfHeight)
            val projector = GlassBodyProjector()
            val out = FloatArray(5)
            var worst = 0f
            for (cx in listOf(-50f, 0f, 200f, 420f)) {
                for (radius in listOf(halfHeight * 0.3f, halfHeight, halfHeight * 1.6f)) {
                    val q = floatArrayOf(cx, halfHeight, 400f, radius, radius * 0.25f)
                    c.clear()
                    c.addInscribedRoundRect(200f, halfHeight, 200f, halfHeight, halfHeight)
                    c.addShapeLimits(4f, 1f, 400f, halfHeight)
                    assertTrue(projector.project(q, c, out))
                    worst = max(worst, glassBodyProtrusion(glassBodyOf(out), 200f, halfHeight, 200f, halfHeight, halfHeight))
                }
            }
            assertTrue(worst <= 0.2f, "at half height $halfHeight the contour escaped by $worst px")
        }
    }

    @Test
    fun theSpringStepsExactlyRatherThanByFixedIncrements() {
        // The closed form must not depend on how the interval is cut up: a critically damped
        // response taken in one step and in 240 substeps must land on the same number.
        for (zeta in listOf(1.0f, 0.8f, 0.72f)) {
            val omega = 26f
            val coarse = GlassSpring(100f, 0f).apply { target = 0f }
            val fine = GlassSpring(100f, 0f).apply { target = 0f }
            coarse.step(0.25f, omega, zeta)
            repeat(240) { fine.step(0.25f / 240f, omega, zeta) }
            assertEquals(coarse.value, fine.value, 0.02f, "zeta $zeta value drifted with the step size")
            assertEquals(coarse.velocity, fine.velocity, 0.5f, "zeta $zeta velocity drifted with the step size")
            // And against the analytic critical solution where one exists.
            if (zeta == 1.0f) {
                val t = 0.25f
                val expected = 100f * (1f + omega * t) * kotlin.math.exp(-omega * t)
                assertEquals(expected, coarse.value, 1e-3f)
            }
        }
    }

    @Test
    fun theCriticalCentrePredictionOfSectionSixHolds() {
        // remaining(t) = D (1 + wt) e^-wt, vPeak = D w / e at t = 1/w, and the 90 % travel time
        // is about 3.89/w. These are the document's own predictions of the authored preset.
        val omega = 22f
        val d = 300f
        val spring = GlassSpring(d, 0f).apply { target = 0f }
        var t = 0f
        var peak = 0f
        var peakAt = 0f
        var ninety = -1f
        val dt = 1f / 2400f
        while (t < 1f) {
            spring.step(dt, omega, 1f)
            t += dt
            if (abs(spring.velocity) > peak) {
                peak = abs(spring.velocity)
                peakAt = t
            }
            if (ninety < 0f && abs(spring.value) <= 0.1f * d) ninety = t
        }
        assertEquals(d * omega / kotlin.math.E.toFloat(), peak, d * omega * 0.01f)
        assertEquals(1f / omega, peakAt, 0.005f)
        assertEquals(3.89f / omega, ninety, 0.01f)
    }
}

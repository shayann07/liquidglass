package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The geometry gate of the iOS-parity brief's section 6.4, which has to pass **before** any
 * motion is fitted. If the basis cannot carry these, tuning springs on top of it would be tuning
 * around a broken shape.
 *
 * Every check here is against an independent construction: finite differences for the Jacobian,
 * a dense boundary oracle for the contour, and the forward map for the inverse. Nothing compares
 * the implementation with a second copy of itself.
 */
class GlassPoseGeometryTest {

    private fun reference(mode: GlassAccommodation) = GlassReference(a = 60f, r = 34f, accommodation = mode)

    /** A spread of poses that exercises every state, including several at once. */
    private fun poses(): List<GlassPose> = buildList {
        fun pose(block: GlassPose.() -> Unit) = add(GlassPose().apply { cx = 400f; cy = 90f; block() })
        pose { }                                                     // identity
        pose { p = 0.14f }                                           // pressure
        pose { u = 0.22f }                                           // horizontal stretch
        pose { u = -0.22f }                                          // vertical stretch
        pose { v = 0.20f }                                           // diagonal stretch
        pose { u = 0.15f; v = -0.18f }                               // arbitrary direction
        pose { kx = 0.14f }                                          // leading/trailing taper
        pose { ky = -0.12f }
        pose { kx = 0.10f; ky = 0.09f }
        pose { acc = 26f }                                           // accommodation (spine px)
        pose { p = 0.12f; u = 0.18f; v = 0.11f; kx = 0.09f; ky = -0.07f; acc = 18f }
        pose { p = -0.10f; u = -0.16f; v = 0.14f; kx = -0.11f; ky = 0.06f; acc = 8f }
        pose { dx = 110f }                                           // elongated along the bar
        pose { dx = 40f; dy = 50f }                                  // rotated spine
        pose { dx = -45f }                                           // spine across the bar
        pose { p = 0.12f; dx = 30f; dy = -70f; kx = 0.08f; acc = 12f }
    }

    private fun framesFor(mode: GlassAccommodation): List<Pair<GlassPose, GlassPoseFrame>> {
        val ref = reference(mode)
        return poses().map { pose ->
            val adjusted = GlassPose().apply {
                set(pose)
                // Candidate A's accommodation is a log scale, not a length.
                if (mode == GlassAccommodation.Uniform && pose.acc != 0f) acc = kotlin.math.ln(1f + pose.acc / 60f)
            }
            adjusted to GlassPoseFrame().apply { update(ref, adjusted) }
        }
    }

    /** Canonical material points covering the caps, the runs and the interior. */
    private fun materialPoints(ref: GlassReference): List<Pair<Float, Float>> = buildList {
        val n = 64
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val ang = t * 2f * kotlin.math.PI.toFloat()
            // On the boundary of the canonical capsule.
            val cx = if (cos(ang) >= 0f) ref.a else -ref.a
            add(cx + ref.r * cos(ang) to ref.r * sin(ang))
        }
        for (fx in listOf(-0.9f, -0.5f, 0f, 0.5f, 0.9f)) {
            for (fy in listOf(-0.8f, -0.3f, 0f, 0.3f, 0.8f)) {
                add(ref.a * fx to ref.r * fy)
            }
        }
    }

    // ------------------------------------------------------------------ identity

    @Test
    fun theIdentityPoseReproducesTheCanonicalBodyExactly() {
        for (mode in GlassAccommodation.entries) {
            val ref = reference(mode)
            val pose = GlassPose().apply { cx = 0f; cy = 0f }
            val frame = GlassPoseFrame().apply { update(ref, pose) }
            val out = FloatArray(2)
            var worst = 0f
            for ((qx, qy) in materialPoints(ref)) {
                frame.materialToRendered(qx, qy, out)
                worst = max(worst, abs(out[0] - qx))
                worst = max(worst, abs(out[1] - qy))
            }
            assertTrue(worst <= 1e-4f, "$mode: the identity pose moved a point by $worst px")
            // ...and its query reproduces the canonical capsule's own distance.
            val q = GlassPoseQuery()
            var worstD = 0f
            for (x in -140..140 step 7) {
                for (y in -60..60 step 5) {
                    frame.query(x.toFloat(), y.toFloat(), q)
                    val ex = x - x.toFloat().coerceIn(-ref.a, ref.a)
                    val canonical = sqrt(ex * ex + y.toFloat() * y) - ref.r
                    worstD = max(worstD, abs(q.coverageDistance - canonical))
                }
            }
            assertTrue(worstD <= 1e-3f, "$mode: identity distance differs by $worstD px")
        }
    }

    // ------------------------------------------------------------------- inverse

    @Test
    fun theInverseRoundTripsToWithinASmallFractionOfAPixel() {
        for (mode in GlassAccommodation.entries) {
            val ref = reference(mode)
            var worst = 0f
            var worstCase = ""
            for ((pose, frame) in framesFor(mode)) {
                val z = FloatArray(2)
                val back = FloatArray(2)
                for ((qx, qy) in materialPoints(ref)) {
                    frame.materialToZ(qx, qy, z)
                    val zx = z[0]
                    val zy = z[1]
                    frame.zToRendered(zx, zy, back)
                    val ok = frame.renderedToZ(back[0], back[1], z)
                    assertTrue(ok, "$mode: the inverse discriminant went negative on the body")
                    val e = max(abs(z[0] - zx), abs(z[1] - zy))
                    if (e > worst) {
                        worst = e
                        worstCase = "$mode p=${pose.p} u=${pose.u} v=${pose.v} k=(${pose.kx},${pose.ky}) acc=${pose.acc}"
                    }
                    // ...and the material inverse recovers q as well.
                    frame.zToMaterial(zx, zy, back)
                    worst = max(worst, max(abs(back[0] - qx), abs(back[1] - qy)))
                }
            }
            assertTrue(worst <= 0.05f, "inverse round trip is off by $worst px at $worstCase")
        }
    }

    // ------------------------------------------------------------------- normals

    @Test
    fun theTransformedNormalAgreesWithIndependentFiniteDifferences() {
        // The normal is claimed to be J^-T n0 normalised. The independent check builds the
        // rendered contour numerically and takes its tangent, which shares no code with the
        // analytic path.
        for (mode in GlassAccommodation.entries) {
            val ref = reference(mode)
            var worstDeg = 0f
            var worstAt = ""
            for ((pose, frame) in framesFor(mode)) {
                val q = GlassPoseQuery()
                val here = FloatArray(2)
                val ahead = FloatArray(2)
                val behind = FloatArray(2)
                val n = 96
                for (i in 0 until n) {
                    val t = i.toFloat() / n
                    // Skip the four join points, where the canonical normal is not differentiable.
                    val phase = (t * 4f) % 1f
                    if (phase < 0.03f || phase > 0.97f) continue
                    contourPoint(ref, t, here)
                    contourPoint(ref, t + 1f / (4f * n), ahead)
                    contourPoint(ref, t - 1f / (4f * n), behind)
                    val h = FloatArray(2)
                    val a2 = FloatArray(2)
                    val b2 = FloatArray(2)
                    frame.materialToRendered(here[0], here[1], h)
                    frame.materialToRendered(ahead[0], ahead[1], a2)
                    frame.materialToRendered(behind[0], behind[1], b2)
                    val tx = a2[0] - b2[0]
                    val ty = a2[1] - b2[1]
                    val tl = sqrt(tx * tx + ty * ty)
                    if (tl < 1e-4f) continue
                    // Outward normal from the tangent, oriented by the canonical outward normal.
                    var nx = ty / tl
                    var ny = -tx / tl
                    val canonical = canonicalNormal(ref, here[0], here[1])
                    val outward = FloatArray(2)
                    frame.materialToRendered(here[0] + canonical[0] * 0.5f, here[1] + canonical[1] * 0.5f, outward)
                    if ((outward[0] - h[0]) * nx + (outward[1] - h[1]) * ny < 0f) { nx = -nx; ny = -ny }

                    frame.query(h[0], h[1], q)
                    val dot = (q.normalX * nx + q.normalY * ny).coerceIn(-1f, 1f)
                    val deg = acos(dot) * 180f / kotlin.math.PI.toFloat()
                    if (deg > worstDeg) {
                        worstDeg = deg
                        worstAt = "$mode t=$t p=${pose.p} u=${pose.u} v=${pose.v} k=(${pose.kx},${pose.ky})"
                    }
                }
            }
            assertTrue(worstDeg <= 0.5f, "the transformed normal is $worstDeg deg off at $worstAt")
        }
    }

    private fun contourPoint(ref: GlassReference, tRaw: Float, out: FloatArray) {
        val t = ((tRaw % 1f) + 1f) % 1f
        val capLen = kotlin.math.PI.toFloat() * ref.r
        val runLen = 2f * ref.a
        val total = 2f * capLen + 2f * runLen
        var s = t * total
        if (s < capLen) {
            val ang = -kotlin.math.PI.toFloat() / 2f + (s / capLen) * kotlin.math.PI.toFloat()
            out[0] = ref.a + ref.r * cos(ang); out[1] = ref.r * sin(ang); return
        }
        s -= capLen
        if (s < runLen) { out[0] = ref.a - s; out[1] = ref.r; return }
        s -= runLen
        if (s < capLen) {
            val ang = kotlin.math.PI.toFloat() / 2f + (s / capLen) * kotlin.math.PI.toFloat()
            out[0] = -ref.a + ref.r * cos(ang); out[1] = ref.r * sin(ang); return
        }
        s -= capLen
        out[0] = -ref.a + s; out[1] = -ref.r
    }

    private fun canonicalNormal(ref: GlassReference, qx: Float, qy: Float): FloatArray {
        val ex = qx - qx.coerceIn(-ref.a, ref.a)
        val len = sqrt(ex * ex + qy * qy)
        return if (len > 1e-5f) floatArrayOf(ex / len, qy / len) else floatArrayOf(0f, 1f)
    }

    // ------------------------------------------------------------- well-formedness

    @Test
    fun theMapIsInvertibleAndOrientationPreservingOverTheWholeBody() {
        for (mode in GlassAccommodation.entries) {
            val ref = reference(mode)
            for ((pose, frame) in framesFor(mode)) {
                val taper = sqrt(pose.kx * pose.kx + pose.ky * pose.ky)
                assertTrue(taper <= 0.25f, "the test poses must stay inside the declared taper bound")
                val z = FloatArray(2)
                for ((qx, qy) in materialPoints(ref)) {
                    frame.materialToZ(qx, qy, z)
                    val s = 1f + frame.bx * z[0] + frame.by * z[1]
                    val det = s * (2f * s - 1f)
                    assertTrue(
                        det > 0f,
                        "$mode: det(DF_k) = $det at q=($qx,$qy) for k=(${pose.kx},${pose.ky})",
                    )
                    assertTrue(abs(frame.bx * z[0] + frame.by * z[1]) <= 0.25f, "$mode: taper bound exceeded")
                }
            }
        }
    }

    @Test
    fun nothingProducesANaNOrASelfIntersection() {
        for (mode in GlassAccommodation.entries) {
            val buffer = FloatArray(256 * 2)
            for ((_, frame) in framesFor(mode)) {
                frame.contour(256, buffer)
                for (v in buffer) assertTrue(v.isFinite(), "$mode: the contour produced $v")
                // A simple closed convex-ish curve never reverses its winding.
                var negative = 0
                for (i in 0 until 256) {
                    val a0 = i * 2
                    val a1 = ((i + 1) % 256) * 2
                    val a2 = ((i + 2) % 256) * 2
                    val cross = (buffer[a1] - buffer[a0]) * (buffer[a2 + 1] - buffer[a1 + 1]) -
                        (buffer[a1 + 1] - buffer[a0 + 1]) * (buffer[a2] - buffer[a1])
                    if (cross < -1e-4f) negative++
                }
                assertTrue(negative == 0, "$mode: the contour reversed its winding $negative times")
            }
        }
    }

    // --------------------------------------------------------- directional response

    @Test
    fun strainStretchesAlongItsDirectionAndSqueezesAcross() {
        // The whole point of replacing the horizontal-only body: an arbitrary 2D direction has to
        // work, not just the x axis.
        val ref = reference(GlassAccommodation.Spine)
        val buffer = FloatArray(256 * 2)
        val extents = GlassPoseExtents()
        val base = GlassPose().apply { cx = 0f; cy = 0f }
        val baseFrame = GlassPoseFrame().apply { update(ref, base) }
        val b = glassPoseExtents(baseFrame, 256, buffer, extents)
        val baseW = b.width
        val baseH = b.height

        // The strain tensor 2*Kstretch*(dd^T - I/2)/... in the brief stretches along d. For a
        // unit direction d = (cos, sin), M = rho * [[cos 2a, sin 2a], [sin 2a, -cos 2a]] has
        // eigenvector d at angle a with eigenvalue +rho.
        for (deg in listOf(0, 30, 45, 90, 135, 180)) {
            val ang = deg * kotlin.math.PI.toFloat() / 180f
            val rho = 0.25f
            val pose = GlassPose().apply {
                cx = 0f; cy = 0f
                u = rho * cos(2f * ang)
                v = rho * sin(2f * ang)
            }
            val frame = GlassPoseFrame().apply { update(ref, pose) }
            val e = glassPoseExtents(frame, 256, buffer, extents)
            // Project the contour onto d and onto its perpendicular.
            frame.contour(256, buffer)
            var alongMax = 0f
            var acrossMax = 0f
            for (i in 0 until 256) {
                val x = buffer[i * 2]
                val y = buffer[i * 2 + 1]
                alongMax = max(alongMax, abs(x * cos(ang) + y * sin(ang)))
                acrossMax = max(acrossMax, abs(-x * sin(ang) + y * cos(ang)))
            }
            // The canonical body's own extent along d, for comparison.
            baseFrame.contour(256, buffer)
            var baseAlong = 0f
            var baseAcross = 0f
            for (i in 0 until 256) {
                val x = buffer[i * 2]
                val y = buffer[i * 2 + 1]
                baseAlong = max(baseAlong, abs(x * cos(ang) + y * sin(ang)))
                baseAcross = max(baseAcross, abs(-x * sin(ang) + y * cos(ang)))
            }
            assertTrue(
                alongMax > baseAlong * 1.05f,
                "$deg deg: strain did not extend along its own direction ($alongMax vs $baseAlong)",
            )
            assertTrue(
                acrossMax < baseAcross * 0.99f,
                "$deg deg: strain did not squeeze across its own direction ($acrossMax vs $baseAcross)",
            )
            assertTrue(e.width.isFinite() && e.height.isFinite(), "$deg deg produced a non-finite extent")
        }
        assertTrue(baseW > 0f && baseH > 0f)
    }

    @Test
    fun taperMakesOneEndLargerThanTheOther() {
        val ref = reference(GlassAccommodation.Spine)
        val buffer = FloatArray(512 * 2)
        val pose = GlassPose().apply { cx = 0f; cy = 0f; kx = 0.16f }
        val frame = GlassPoseFrame().apply { update(ref, pose) }
        frame.contour(512, buffer)
        var leftHalf = 0f
        var rightHalf = 0f
        for (i in 0 until 512) {
            val x = buffer[i * 2]
            val y = abs(buffer[i * 2 + 1])
            if (x < -ref.a * 0.8f) leftHalf = max(leftHalf, y)
            if (x > ref.a * 0.8f) rightHalf = max(rightHalf, y)
        }
        assertTrue(
            rightHalf > leftHalf * 1.05f,
            "positive kx did not enlarge the leading end: left $leftHalf right $rightHalf",
        )
    }

    @Test
    fun accommodationAddsSpanAndCandidateBLeavesTheCapsAlone() {
        val buffer = FloatArray(512 * 2)
        val extents = GlassPoseExtents()
        val ref = reference(GlassAccommodation.Spine)
        val rest = GlassPoseFrame().apply { update(ref, GlassPose().apply { cx = 0f; cy = 0f }) }
        val r0 = glassPoseExtents(rest, 512, buffer, extents)
        val w0 = r0.width
        val h0 = r0.height
        val grown = GlassPoseFrame().apply { update(ref, GlassPose().apply { cx = 0f; cy = 0f; acc = 40f }) }
        val r1 = glassPoseExtents(grown, 512, buffer, extents)
        assertTrue(abs(r1.width - (w0 + 80f)) <= 0.5f, "spine accommodation added ${r1.width - w0} px, not 80")
        assertTrue(abs(r1.height - h0) <= 0.5f, "spine accommodation changed the height by ${r1.height - h0} px")

        // Candidate A widens the caps with the span, which is the difference the A/B test is about.
        val refA = reference(GlassAccommodation.Uniform)
        val restA = GlassPoseFrame().apply { update(refA, GlassPose().apply { cx = 0f; cy = 0f }) }
        val a0 = glassPoseExtents(restA, 512, buffer, extents).width
        val grownA = GlassPoseFrame().apply {
            update(refA, GlassPose().apply { cx = 0f; cy = 0f; acc = kotlin.math.ln(1.4f) })
        }
        val a1 = glassPoseExtents(grownA, 512, buffer, extents)
        assertTrue(a1.width > a0, "uniform accommodation did not widen the body")
        assertTrue(
            abs(a1.height - h0) <= 0.5f,
            "uniform accommodation changed the height by ${a1.height - h0} px, which it must not",
        )
    }

    @Test
    fun aCircularReferenceFallsBackToTheUniformBranch() {
        // Candidate B divides by the half-spine. A circle has none, and section 6.5 requires an
        // explicit tested compatibility branch rather than a hidden epsilon.
        val circle = GlassReference(a = 0f, r = 40f, accommodation = GlassAccommodation.Spine)
        assertTrue(
            circle.accommodation == GlassAccommodation.Uniform,
            "a circular reference must fall back to the uniform accommodation branch",
        )
        val frame = GlassPoseFrame().apply { update(circle, GlassPose().apply { cx = 0f; cy = 0f; p = 0.1f }) }
        val q = GlassPoseQuery()
        frame.query(0f, 0f, q)
        assertTrue(q.coverageDistance < 0f, "the circle's centre must be inside it")
        assertTrue(q.coverageDistance.isFinite(), "the circular branch produced ${q.coverageDistance}")
    }

    @Test
    fun theBandNeverReachesTheMedialAxis() {
        for (mode in GlassAccommodation.entries) {
            val ref = reference(mode)
            for ((_, frame) in framesFor(mode)) {
                // The band is a depth; the medial axis is at the canonical radius under the same
                // smallest stretch, so a band below that can never query an ambiguous normal.
                val deepest = frame.radius * frame.minStretch
                assertTrue(
                    frame.band() < deepest,
                    "$mode: band ${frame.band()} reaches the medial axis at $deepest",
                )
            }
        }
    }

    // --------------------------------------------------------------- spine vector

    @Test
    fun theSpineVectorOrientsTheCapsuleAndKeepsItsCaps() {
        // The rendered body is the hull of two discs of radius r at c +- L (cos t, sin t): its
        // extent along the spine is 2 (L + r) and across it exactly 2 r, whatever L is. A
        // double-angle vector of (-L, 0) is a spine standing across the bar.
        val ref = reference(GlassAccommodation.Spine)
        val buffer = FloatArray(256 * 2)
        for ((dx, dy) in listOf(110f to 0f, 40f to 50f, -45f to 0f, 0f to 60f)) {
            val pose = GlassPose().apply { cx = 400f; cy = 90f; this.dx = dx; this.dy = dy }
            val frame = GlassPoseFrame().apply { update(ref, pose) }
            val length = kotlin.math.sqrt(dx * dx + dy * dy)
            val theta = 0.5f * kotlin.math.atan2(dy, dx)
            frame.contour(256, buffer)
            var along = 0f
            var across = 0f
            for (i in 0 until 256) {
                val x = buffer[i * 2] - pose.cx
                val y = buffer[i * 2 + 1] - pose.cy
                along = max(along, abs(x * cos(theta) + y * sin(theta)))
                across = max(across, abs(-x * sin(theta) + y * cos(theta)))
            }
            assertTrue(
                abs(along - (length + ref.r)) <= 0.6f,
                "spine ($dx, $dy): reach along the spine is $along, expected ${length + ref.r}",
            )
            assertTrue(
                abs(across - ref.r) <= 0.6f,
                "spine ($dx, $dy): reach across the spine is $across, expected the cap radius ${ref.r}",
            )
        }
        // The identity pose renders the reference's own spine: (0, 0) is not a circle.
        val identity = GlassPoseFrame().apply { update(ref, GlassPose().apply { cx = 400f; cy = 90f }) }
        assertTrue(abs(identity.halfSpine - ref.a) < 1e-4f, "the identity pose lost the reference spine")
    }
}

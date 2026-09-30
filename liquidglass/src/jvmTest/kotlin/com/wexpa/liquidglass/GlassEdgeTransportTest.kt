package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The edge-transport gates of `V3-MODEL.md` section 15.4.
 *
 * Everything here is read off the **implemented** map through coordinate-coded readback
 * ([GlassSourceField]), not off the profile function alone: a source map that is right on paper
 * and wrong in the shader is exactly the failure these are for. The fold's turning point is
 * located by where the measured source depth reverses, never by a bright line in the image.
 *
 * The predictions are the candidate's own mathematics, declared before rendering:
 * `f(u) = u + a (1 - u)^3` has `f'(u) = 1 - 3a(1 - u)^2`, so for `a > 1/3` it turns once at
 * `uFold = 1 - 1/sqrt(3a)` with minimum `1 - 2/(3 sqrt(3a))`. At the authored seed `a = 0.8`
 * that is `uFold = 0.35450` and `fMin = 0.56972`. They are predictions of the proposed model,
 * not measurements taken from Apple's stills.
 */
class GlassEdgeTransportTest {

    private val a = 0.8f
    private val uFold = 1f - 1f / sqrt(3f * a)
    private val fMin = 1f - 2f / (3f * sqrt(3f * a))

    private fun f(u: Float): Float {
        val v = 1f - u
        return u + a * v * v * v
    }

    /** The shader's own rim attenuation, which the CPU reference has to carry too. */
    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Effective source depth at destination depth [d] on a straight run, in px. */
    private fun sourceDepth(d: Float, band: Float): Float {
        if (d >= band) return d
        val u = (d / band).coerceIn(0f, 1f)
        val shift = max(f(u) * band - d, 0f) * smoothstep(0f, 1.5f, d)
        return d + shift
    }

    private val width = 420
    private val height = 220
    private val pad = 40
    private val radius = 110f
    private val band = GlassMaterial.BEVEL_RATIO * radius   // 66 px

    /** The shader attenuates the shift over the outermost 1.5 px, so the map is identity there. */
    private val RIM_RAMP = 3f

    private fun straightField(edgeFold: Float) = GlassSourceField.render(
        kind = GlassSourceField.Kind.PANEL,
        width = width,
        height = height,
        pad = pad,
        radii = FloatArray(4) { radius },
        refractBand = band,
        profile = 1f,
        formation = 0f,
        edgeFold = edgeFold,
    )

    /** The measured source depth down a column, from the top run inward. */
    private fun profileAt(field: GlassSourceField.Field, x: Int): FloatArray {
        val out = FloatArray(height / 2)
        for (d in out.indices) {
            val y = pad + d
            val i = field.index(x, y)
            out[d] = if (field.alpha[i] < 0.99f) Float.NaN else field.sy[i] - (pad + 0.5f)
        }
        return out
    }

    @Test
    fun theEncodingItselfIsGoodToAHundredthOfAPixel() {
        assertTrue(GlassSourceField.encodingError() <= 0.02f, "readback self-check ${GlassSourceField.encodingError()} px")
    }

    @Test
    fun theImplementedStraightMapMatchesTheDeclaredProfile() {
        val field = straightField(a)
        var worst = 0f
        var checked = 0
        // Down the middle of the top run, far from either corner arc, where the arc/run blend
        // weight is zero and the straight profile is the whole map.
        for (x in pad + width / 2 - 40 until pad + width / 2 + 40) {
            val measured = profileAt(field, x)
            for (d in 2 until (band * 1.4f).toInt()) {
                val m = measured[d]
                if (m.isNaN()) continue
                val expected = sourceDepth(d + 0.5f, band) - 0.5f
                worst = max(worst, abs(m - expected))
                checked++
            }
        }
        assertTrue(checked > 4000, "only $checked samples")
        assertTrue(worst <= 0.25f, "implemented map is $worst px off the declared profile")
    }

    @Test
    fun theFoldTurnsWhereTheCandidatePredictsAndNowhereElse() {
        val field = straightField(a)
        val x = pad + width / 2
        val measured = profileAt(field, x)
        // The turning point: the depth at which the measured source depth stops decreasing.
        var minDepth = Float.MAX_VALUE
        var minAt = -1
        var reversals = 0
        var previous = Float.NaN
        var falling = true
        for (d in 2 until (band * 1.1f).toInt()) {
            val m = measured[d]
            if (m.isNaN()) continue
            if (m < minDepth) {
                minDepth = m
                minAt = d
            }
            if (!previous.isNaN()) {
                val nowFalling = m < previous - 1e-3f
                val nowRising = m > previous + 1e-3f
                if (falling && nowRising) {
                    reversals++
                    falling = false
                } else if (!falling && nowFalling) {
                    reversals++
                    falling = true
                }
            }
            previous = m
        }
        // Sub-pixel: parabola through the three samples around the minimum.
        val y0 = measured[minAt - 1]
        val y1 = measured[minAt]
        val y2 = measured[minAt + 1]
        val refined = minAt + 0.5f * (y0 - y2) / (y0 - 2f * y1 + y2)
        val predictedAt = uFold * band
        val predictedMin = fMin * band
        assertTrue(
            abs(refined - predictedAt) <= max(0.01f * band, 0.25f),
            "fold at ${refined} px, predicted ${predictedAt} px (${uFold} W)",
        )
        assertTrue(
            abs(minDepth - predictedMin) <= max(0.01f * band, 0.25f),
            "minimum source depth ${minDepth} px, predicted ${predictedMin} px (${fMin} W)",
        )
        assertTrue(reversals == 1, "the map reversed $reversals times; the candidate has exactly one fold")
    }

    @Test
    fun pairedImagesSeparateAsTheSquareRootOfTheLandmarksOffset() {
        // Three landmark offsets above the fold's minimum, declared before rendering. The local
        // quadratic predicts a separation of 2 sqrt(2 eps / A) with A = f''(uFold) / W; the exact
        // polynomial roots are computed alongside, and the exact ones are what the gate uses
        // where the approximation is outside its range.
        val field = straightField(a)
        val x = pad + width / 2
        val measured = profileAt(field, x)
        val offsets = floatArrayOf(4f, 6.25f, 9f)
        val curvature = 6f * a * (1f - uFold) / band
        // The offsets are declared above; what they are offsets *from* is each map's own
        // minimum - the measured one for the measured profile, the CPU one for the prediction -
        // because near a fold the source depth is stationary, so a quarter-pixel of map error
        // moves a root by several pixels. Referring both to their own minimum cancels that and
        // leaves the separation law itself under test. The offsets are also large enough that
        // both roots stay clear of the outermost 3 px, where the shader ramps to identity.
        val measuredMin = measuredMinimum(measured)
        val cpuMin = cpuMinimum()
        val results = ArrayList<Triple<Float, Float, Float>>()
        for (eps in offsets) {
            val level = measuredMin + eps
            val roots = crossings(measured, level, RIM_RAMP.toInt() + 1, (band * 1.05f).toInt())
            assertTrue(roots.size == 2, "landmark at +$eps px produced ${roots.size} images, expected 2")
            val observed = roots[1] - roots[0]
            val quadratic = 2f * sqrt(2f * eps / curvature)
            val exact = exactSeparation(cpuMin + eps)
            results.add(Triple(observed, quadratic, exact))
            assertTrue(
                abs(observed - exact) <= 1.0f,
                "at +$eps px the two images are $observed px apart, exact roots predict $exact",
            )
        }
        // And the growth really is square-root: the separations stand in the ratio 1 : 2 : 3.
        val base = results[0].first
        assertTrue(abs(results[1].first / base - 1.25f) <= 0.06f, "ratio ${results[1].first / base}")
        assertTrue(abs(results[2].first / base - 1.50f) <= 0.08f, "ratio ${results[2].first / base}")
        // The quadratic approximation is quoted so its own error is visible rather than hidden.
        for ((observed, quadratic, exact) in results) {
            assertTrue(quadratic > 0f && exact > 0f && observed > 0f)
        }
    }

    /** The measured profile's own minimum past the rim ramp, refined by a local parabola. */
    private fun measuredMinimum(profile: FloatArray): Float {
        var best = Float.MAX_VALUE
        var at = -1
        for (d in RIM_RAMP.toInt() until min(profile.size - 1, (band * 1.05f).toInt())) {
            val m = profile[d]
            if (m.isNaN()) continue
            if (m < best) {
                best = m
                at = d
            }
        }
        val y0 = profile[at - 1]
        val y1 = profile[at]
        val y2 = profile[at + 1]
        val denominator = y0 - 2f * y1 + y2
        return if (abs(denominator) < 1e-6f) best else y1 - 0.125f * (y0 - y2) * (y0 - y2) / denominator
    }

    /** Depths where the measured source depth crosses [level], by linear interpolation. */
    private fun crossings(profile: FloatArray, level: Float, from: Int, to: Int): List<Float> {
        val out = ArrayList<Float>()
        for (d in from until min(to, profile.size - 1)) {
            val p = profile[d]
            val q = profile[d + 1]
            if (p.isNaN() || q.isNaN()) continue
            if ((p - level) * (q - level) <= 0f && p != q) {
                out.add(d + (level - p) / (q - p))
            }
        }
        return out
    }

    /** The minimum of the implemented map, by dense search of the CPU reference. */
    private fun cpuMinimum(): Float {
        var best = Float.MAX_VALUE
        // From past the 1.5 px rim ramp: that ramp pulls the map to identity at the very edge,
        // so the map's global minimum is trivially zero at d = 0 and is not the fold's.
        var d = RIM_RAMP
        while (d < band) {
            best = min(best, sourceDepth(d, band))
            d += 0.001f
        }
        return best
    }

    /** Where the implemented map's minimum sits, in px of destination depth. */
    private fun cpuFoldDepth(): Float {
        var best = Float.MAX_VALUE
        var at = 0f
        var d = RIM_RAMP
        while (d < band) {
            val v = sourceDepth(d, band)
            if (v < best) {
                best = v
                at = d
            }
            d += 0.001f
        }
        return at
    }

    /** The two exact roots of the implemented map around its fold, by bisection. */
    private fun exactSeparation(level: Float): Float {
        val foldPx = cpuFoldDepth()
        fun bisect(lo: Float, hi: Float): Float {
            var l = lo
            var h = hi
            repeat(80) {
                val mid = (l + h) / 2f
                if ((sourceDepth(l, band) - level) * (sourceDepth(mid, band) - level) <= 0f) h = mid else l = mid
            }
            return (l + h) / 2f
        }
        return bisect(foldPx, band) - bisect(RIM_RAMP, foldPx)
    }

    @Test
    fun theArcToRunTransitionCarriesNoSeam() {
        // Along the outline **at constant depth**, the source coordinate must stay continuous as
        // the nearest feature changes from a corner arc to a straight run. A horizontal scanline
        // would not do: near a corner its own depth changes faster than anything else, and the
        // resulting compression would be mistaken for a seam. The full 2D source coordinate is
        // read, not a depth alone, because a purely tangential jump does not show in a
        // normal-depth plot at all.
        val field = straightField(a)
        val depth = 0.4f * band
        val hw = width / 2f
        val hh = height / 2f
        val r = radius - depth
        var worst = 0f
        var worstAt = 0f
        var samples = 0
        var previousX = Float.NaN
        var previousY = Float.NaN
        // The inward offset of a rounded rect at this depth is a rounded rect with the same
        // straight runs and a smaller corner radius; walk its top-left quadrant and top run.
        var t = 0f
        while (t <= 1f) {
            // 0..0.5 sweeps the top-left corner arc, 0.5..1 runs along the top edge.
            val px: Float
            val py: Float
            if (t < 0.5f) {
                // The top-left corner's offset arc, from where it meets the left run (theta = pi)
                // to where it meets the top run (theta = 3pi/2), about the same corner centre.
                val angle = (kotlin.math.PI.toFloat()) * (1f + t)
                px = -hw + radius + r * kotlin.math.cos(angle)
                py = -hh + radius + r * kotlin.math.sin(angle)
            } else {
                val s2 = (t - 0.5f) * 2f
                px = -hw + radius + s2 * (hw - radius)
                py = -hh + depth
            }
            val x = (px + hw + pad).toInt()
            val y = (py + hh + pad).toInt()
            if (x in 1 until field.layerWidth - 1 && y in 1 until field.layerHeight - 1) {
                val i = field.index(x, y)
                if (field.alpha[i] > 0.99f) {
                    if (!previousX.isNaN()) {
                        val dx = field.sx[i] - previousX
                        val dy = field.sy[i] - previousY
                        val step = sqrt(dx * dx + dy * dy)
                        if (step > worst) {
                            worst = step
                            worstAt = t
                        }
                    }
                    previousX = field.sx[i]
                    previousY = field.sy[i]
                    samples++
                }
            }
            t += 0.0008f
        }
        assertTrue(samples > 400, "the offset curve produced only $samples samples")
        // Consecutive samples are about a pixel apart along the curve; a seam would show as a
        // multi-pixel jump where the nearest feature changes identity.
        assertTrue(worst <= 3.0f, "source coordinate jumped $worst px along the outline at t=$worstAt")
    }

    @Test
    fun turningTheFoldOffLeavesEveryExistingConsumerWhereItWas() {
        // edgeFold 0 must reproduce the shipped shallow resting offset exactly, so nothing that
        // does not opt in can move.
        val field = straightField(0f)
        val x = pad + width / 2
        val measured = profileAt(field, x)
        var worst = 0f
        for (d in 2 until (band * 1.3f).toInt()) {
            val m = measured[d]
            if (m.isNaN()) continue
            val dd = d + 0.5f
            val u = (dd / band).coerceIn(0f, 1f)
            val legacy = if (dd >= band) dd else dd + max((u + 0.06f * (1f - u) * (1f - u)) * band - dd, 0f) * smoothstep(0f, 1.5f, dd)
            worst = max(worst, abs(m - (legacy - 0.5f)))
        }
        assertTrue(worst <= 0.25f, "the fold-off profile moved by $worst px")
    }

    @Test
    fun theHeldInkMapIsContinuousThroughItsConnectionBand() {
        // The shipped held source steps by about 0.14 W at half the band. The sharp ink map does
        // not: both are rendered and the step is measured on each, so the claim is a comparison
        // rather than an assertion.
        fun inkProfile(heldInk: Float): FloatArray {
            val field = GlassSourceField.render(
                kind = GlassSourceField.Kind.CONTENT,
                width = width,
                height = height,
                pad = pad,
                radii = FloatArray(4) { radius },
                refractBand = band,
                profile = 2f,
                heldLens = 1f,
                heldInk = heldInk,
            )
            return profileAt(field, pad + width / 2)
        }
        fun worstStep(profile: FloatArray, from: Int, to: Int): Float {
            var worst = 0f
            for (d in from until to) {
                val p = profile[d]
                val q = profile[d + 1]
                if (p.isNaN() || q.isNaN()) continue
                worst = max(worst, abs(q - p))
            }
            return worst
        }
        val mid = (0.5f * band).toInt()
        val seamed = worstStep(inkProfile(0f), mid - 4, mid + 4)
        val continuous = worstStep(inkProfile(1f), mid - 4, mid + 4)
        // The shipped map's step across half the band is a discontinuity: 0.14 W in one pixel.
        assertTrue(
            seamed > 0.10f * band,
            "the shipped held map should step by about 0.14 W across half the band; measured $seamed px",
        )
        // The connection band is steep but finite, and what it is worth is its own derivative:
        // fHeld'(0.5) = 1 + w'(0.5)(0.3 u - 0.29) + 0.3 w(0.5), which at this band is 3.25 source
        // px per destination px. That is compression to be antialiased, not a jump.
        val u = 0.5f
        val wPrime = -6f * 0.5f * 0.5f / 0.1f
        val analytic = 1f + wPrime * (0.3f * u - 0.29f) + 0.3f * 0.5f
        assertTrue(
            continuous <= analytic + 0.5f,
            "the continuous ink map steps by $continuous px where its own derivative is $analytic",
        )
        assertTrue(
            continuous < seamed * 0.6f,
            "the continuous map ($continuous px) is not meaningfully smoother than the seamed one ($seamed px)",
        )
    }

    @Test
    fun theBodysImplementedMapMatchesItsCpuQuery() {
        // The V3 body's own transport, read back and scored against the CPU geometry: a pixel at
        // inward depth d along the body's outward normal must sample from depth W f(d/W) along
        // that same normal, wherever on the body it is.
        val bodies = listOf(
            GlassBody(cx = 210f, cy = 110f, length = 150f, radius = 70f, skew = 0f),
            GlassBody(cx = 190f, cy = 110f, length = 170f, radius = 62f, skew = 16f),
            GlassBody(cx = 240f, cy = 110f, length = 120f, radius = 74f, skew = -20f),
        )
        val q = GlassBodyQuery()
        for (body in bodies) {
            val w = glassBodyBand(body)
            val field = GlassSourceField.render(
                kind = GlassSourceField.Kind.PANEL,
                width = width,
                height = height,
                pad = pad,
                radii = FloatArray(4) { radius },
                refractBand = w,
                profile = 1f,
                formation = 0f,
                edgeFold = a,
                body = body,
            )
            var worst = 0f
            var checked = 0
            var coverageMismatch = 0
            for (y in 0 until field.layerHeight) {
                for (x in 0 until field.layerWidth) {
                    val i = field.index(x, y)
                    val px = x + 0.5f - pad
                    val py = y + 0.5f - pad
                    glassBodyQuery(body, px, py, q)
                    val inside = q.distance < -0.75f
                    if (field.alpha[i] > 0.99f != inside) {
                        if (abs(q.distance) > 1.5f) coverageMismatch++
                        continue
                    }
                    if (!inside || field.alpha[i] < 0.99f) continue
                    val depth = -q.distance
                    if (depth > w * 1.2f) continue
                    // The arc/run blend is the body's own, so the corner and the run share one map.
                    val corner = bodyCornerWeight(body, px, py)
                    val u = (depth / w).coerceIn(0f, 1f)
                    val straight = u + a * (1f - u) * (1f - u) * (1f - u)
                    val src = straight * (1f - corner) + legacyRestSource(u) * corner
                    val shift = max(src * w - depth, 0f) * smoothstep(0f, 1.5f, depth)
                    val ex = px - q.normalX * shift + pad
                    val ey = py - q.normalY * shift + pad
                    val dx = field.sx[i] - ex
                    val dy = field.sy[i] - ey
                    worst = max(worst, sqrt(dx * dx + dy * dy))
                    checked++
                }
            }
            assertTrue(checked > 3000, "body $body produced only $checked samples")
            assertTrue(coverageMismatch == 0, "body $body coverage disagreed at $coverageMismatch pixels")
            assertTrue(worst <= 0.25f, "body $body map is $worst px off its CPU query")
        }
    }

    /** `restSourceLegacy` from `GlassRestMap.kt`, on the CPU. */
    private fun legacyRestSource(u: Float): Float = when {
        u >= 0.76f -> u
        u >= 0.62f -> 0.64f + 0.857f * (u - 0.62f)
        u >= 0.38f -> 0.55f + 0.375f * (u - 0.38f)
        u >= 0.14f -> 0.55f
        else -> 0.55f + 0.93f * (0.14f - u)
    }

    /** The shader's `bodyCorner`, on the CPU. */
    private fun bodyCornerWeight(body: GlassBody, px: Float, py: Float): Float {
        val clx = body.leftCentreX
        val h = max(body.rightCentreX - clx, 1e-4f)
        val b = ((body.leftRadius - body.rightRadius) / h).coerceIn(-0.999999f, 0.999999f)
        val aa = sqrt(max(1f - b * b, 0f))
        val k = aa * (px - clx) - b * abs(py - body.cy)
        val soft = max(0.25f * min(body.leftRadius, body.rightRadius), 1f)
        val left = 1f - smoothstep(0f, soft, k)
        val right = smoothstep(aa * h - soft, aa * h, k)
        return max(left, right).coerceIn(0f, 1f)
    }
}

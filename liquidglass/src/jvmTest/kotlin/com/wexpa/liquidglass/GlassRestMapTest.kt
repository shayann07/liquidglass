package com.wexpa.liquidglass

import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The measured resting-corner source map ([GlassRestMap.Measured]): the table itself, then the complete
 * caller path through the real shaders. Gates are the closeout's: compiled map within 0.25 reference px
 * of the fitted reference; the three shaders agreeing to RMS 0.05 / max 0.25 px with the same coverage
 * and the same source identity; selector off unchanged; sheet and held paths untouched by the selector.
 */
class GlassRestMapTest {

    private val W72 = 43.2f

    /**
     * The fitted curve the knots were chosen from (closeout `E9_geometry.json`, M0 rest curve, d 6..36
     * at 0.5 px, reference px at R = 72). This is the table's reference; the phone data behind it is
     * exposed validation and is scored separately in the closeout, not here.
     */
    private val fitD = FloatArray(61) { 6f + 0.5f * it }
    private val fitS = floatArrayOf(
        37.8387f, 36.6029f, 35.5571f, 34.6596f, 33.8803f, 33.1963f, 32.5907f, 32.0502f, 31.5646f, 31.1262f, 30.7283f,
        30.3664f, 30.0361f, 29.7345f, 29.4592f, 29.2079f, 28.9795f, 28.7724f, 28.5858f, 28.4188f, 28.2709f, 28.1419f,
        28.0310f, 27.9383f, 27.8635f, 27.8062f, 27.7668f, 27.7447f, 27.7401f, 27.7529f, 27.7829f, 27.8303f, 27.8947f,
        27.9762f, 28.0746f, 28.1899f, 28.3220f, 28.4706f, 28.6358f, 28.8173f, 29.0148f, 29.2284f, 29.4577f, 29.7027f,
        29.9629f, 30.2383f, 30.5287f, 30.8337f, 31.1532f, 31.4869f, 31.8345f, 32.1958f, 32.5705f, 32.9585f, 33.3594f,
        33.7729f, 34.1989f, 34.6369f, 35.0868f, 35.5483f, 36.0212f,
    )

    private fun fit(d: Float): Float {
        if (d <= fitD[0]) return fitS[0]
        if (d >= fitD.last()) return fitS.last()
        var i = 0
        while (fitD[i + 1] < d) i++
        val f = (d - fitD[i]) / (fitD[i + 1] - fitD[i])
        return fitS[i] + f * (fitS[i + 1] - fitS[i])
    }

    @Test
    fun testTheKnotsAreUniqueFiniteStrictlyIncreasingAndEndOnIdentity() {
        val t = GlassRestMapTable
        assertEquals(12, t.U.size); assertEquals(12, t.V.size)
        for (i in t.U.indices) assertTrue(t.U[i].isFinite() && t.V[i].isFinite(), "knot $i not finite")
        for (i in 1 until t.U.size) assertTrue(t.U[i] > t.U[i - 1], "knot inputs must strictly increase at $i")
        assertEquals(t.U.last(), t.V.last(), 0f, "the last knot is the identity join, exactly")
        assertEquals(36f / W72, t.IDENTITY_U, 1e-5f)
        assertEquals(6f / W72, t.U[0], 1e-5f)
    }

    @Test
    fun testEveryJoinIsContinuousAndTheIdentityJoinHasSlopeOneOnBothSides() {
        val t = GlassRestMapTable
        val eps = 1e-5f
        for (u in t.U) {
            val l = t.source(u - eps); val c = t.source(u); val r = t.source(u + eps)
            assertTrue(abs(l - c) < 1e-4f && abs(r - c) < 1e-4f, "discontinuity at u=$u: $l $c $r")
        }
        val h = 1e-3f
        val slopeLeft = (t.source(t.IDENTITY_U) - t.source(t.IDENTITY_U - h)) / h
        val slopeRight = (t.source(t.IDENTITY_U + h) - t.source(t.IDENTITY_U)) / h
        assertTrue(abs(slopeLeft - 1f) < 0.01f, "slope into the identity join must be 1; got $slopeLeft")
        assertTrue(abs(slopeRight - 1f) < 1e-4f, "slope out of the identity join must be 1; got $slopeRight")
        val hl = (t.source(t.HERMITE_U) - t.source(t.HERMITE_U - h)) / h
        val hr = (t.source(t.HERMITE_U + h) - t.source(t.HERMITE_U)) / h
        assertTrue(abs(hl - hr) < 0.01f, "the Hermite interval must start at the incoming slope; $hl vs $hr")
        // Units: the table is dimensionless; identity beyond the join at any scale.
        for (u in floatArrayOf(t.IDENTITY_U, 0.9f, 1f, 1.5f, 3f)) assertEquals(u, t.source(u), 1e-6f)
        // The unmeasured shallow extension is the first knot's value, flat.
        for (u in floatArrayOf(0f, 0.05f, 0.1f, t.U[0] - 1e-4f)) assertEquals(t.V0, t.source(u), 1e-6f)
    }

    @Test
    fun testTheCompiledTableIsWithinAQuarterPixelOfTheFittedReferenceOnADenseGrid() {
        var worst = 0f; var worstD = 0f
        var d = 6f
        while (d <= 36.0001f) {
            val s = GlassRestMapTable.source(d / W72) * W72
            val e = abs(s - fit(d))
            if (e > worst) { worst = e; worstD = d }
            d += 0.05f
        }
        println("rest map compiled-vs-fit max ${"%.4f".format(worst)} px at d ${"%.2f".format(worstD)}")
        assertTrue(worst <= 0.25f, "compiled table off the fitted reference by $worst px at d=$worstD (gate 0.25)")
    }

    @Test
    fun testTheOrientationIsOneMinimumWithSourceInsideTheDisplayDepthEverywhere() {
        val t = GlassRestMapTable
        var minU = 0f; var minV = Float.MAX_VALUE
        var u = t.U[0]
        while (u <= t.IDENTITY_U) { val v = t.source(u); if (v < minV) { minV = v; minU = u }; u += 1e-4f }
        assertTrue(abs(minU * W72 - 19f) < 1.0f, "the minimum sits at d ~ 19 px; got ${minU * W72}")
        assertTrue(abs(minV * W72 - 27.767f) < 0.05f, "the minimum is 27.767 px; got ${minV * W72}")
        var prev = t.source(t.U[0]); u = t.U[0] + 1e-3f
        while (u <= t.IDENTITY_U + 1e-6f) {
            val v = t.source(u)
            if (u < minU) assertTrue(v <= prev + 1e-6f, "must decrease before the minimum at u=$u")
            else if (u > minU + 1e-3f) assertTrue(v >= prev - 1e-6f, "must increase after the minimum at u=$u")
            // Source depth never shallower than display depth: the map samples inward only.
            assertTrue(v >= u - 1e-6f, "source shallower than display at u=$u: $v")
            prev = v; u += 1e-3f
        }
    }

    @Test
    fun testTheCoordinateEncodingRoundTrips() {
        val e = GlassSourceField.encodingError()
        println("coordinate encoding self-check: max ${"%.4f".format(e)} px")
        assertTrue(e <= 0.02f, "the coordinate encoding is off by $e px on its own")
    }

    /**
     * `restMap` is the last constructor parameter (closeout Phase 3): a positional call written against the
     * pre-existing prefix (profile, blurRadius, backdropBlur, backdropSigma, refractionBand, refractionDepth)
     * keeps its meaning, and the default is the shipping map. Source order only; nothing here claims binary
     * compatibility.
     */
    @Test
    fun testThePositionalConstructorPrefixIsUnchangedAndTheDefaultRestMapIsLegacy() {
        val s = GlassStyle(GlassProfile.Legacy, 3.dp, 5.dp, androidx.compose.ui.unit.Dp.Unspecified, 14.dp, 9.dp)
        assertEquals(GlassProfile.Legacy, s.profile)
        assertEquals(3.dp, s.blurRadius); assertEquals(5.dp, s.backdropBlur); assertEquals(14.dp, s.refractionBand); assertEquals(9.dp, s.refractionDepth)
        assertEquals(GlassRestMap.Legacy, s.restMap)
        assertEquals(GlassRestMap.Legacy, GlassStyle().restMap)
        assertEquals(GlassRestMap.Legacy, GlassStyle.inApp(dark = true).restMap)
        assertEquals(GlassRestMap.Measured, GlassStyle().copy(restMap = GlassRestMap.Measured).restMap)
    }

    @Test
    fun testTheGeneratedShaderTextCarriesEveryKnot() {
        val text = GlassRestMapTable.sksl
        for (i in GlassRestMapTable.U.indices) {
            val u = "%.6f".format(java.util.Locale.ROOT, GlassRestMapTable.U[i])
            assertTrue(text.contains(u), "knot input $u missing from the generated shader text")
        }
        assertTrue(GLASS_SHADER_SOURCE.contains(text) && GLASS_CONTENT_SHADER_SOURCE.contains(text) && GLASS_CONTAINER_SHADER_SOURCE.contains(text),
            "all three shaders must carry the generated table verbatim")
    }

    // ---- the complete caller path, through the real shaders ---------------------------------------------

    /** Source depth s and display depth d, both in layer px, along the ray from the right end's centre at angle 0. */
    private fun profileAlongRay(f: GlassSourceField.Field, radius: Float, band: Float): List<Triple<Float, Float, Float>> {
        val cx = f.pad + f.width - radius   // right end centre, x
        val cy = f.pad + f.height / 2f
        val out = ArrayList<Triple<Float, Float, Float>>()
        val row = (cy - 0.5f).toInt()       // the pixel row whose centre is cy
        for (x in (cx + 0.5f).toInt() until f.pad + f.width) {
            val px = x + 0.5f
            val d = (f.pad + f.width) - px  // display depth from the tip, along the normal
            val i = f.index(x, row)
            if (f.alpha[i] < 0.999f) continue
            val s = (f.pad + f.width) - f.sx[i]
            out.add(Triple(d, s, f.alpha[i]))
        }
        return out
    }

    @Test
    fun testThePanelShaderReproducesTheTableAtTheReferenceSizeAndTheVitalsSizeAtThreeRenderScales() {
        // R = 72 (the measured toolbar ends), R = 66 (the pill, a control), and the Vitals nav bar on the
        // Pixel 7: a 62 dp capsule at 2.625 px/dp is 162.75 px tall, radius 81.4 (1006.5 px wide). At sizes
        // other than 72 this proves implementation consistency, not phone parity.
        data class Case(val name: String, val width: Int, val height: Int, val r: Float)
        val cases = listOf(Case("toolbar R72", 486, 144, 72f), Case("pill R66", 476, 132, 66f), Case("vitals R81", 1006, 162, 81f))
        for (c in cases) for (rs in floatArrayOf(1f, 0.5f, 0.25f)) {
            val w = (c.width * rs).toInt(); val h = (c.height * rs).toInt()
            val r = (h / 2f).coerceAtMost(c.r * rs)
            val band = GlassMaterial.BEVEL_RATIO * r
            val pad = (8 * rs).toInt().coerceAtLeast(2)
            val f = GlassSourceField.render(GlassSourceField.Kind.PANEL, w, h, pad, r, band, restMap = 1f)
            var worst = 0f; var worstD = 0f; var n = 0
            for ((d, s, _) in profileAlongRay(f, r, band)) {
                if (d < 2f || d > band) continue   // the outermost 1.5 px carry the existing displacement fade; beyond W is identity
                val expect = GlassRestMapTable.source(d / band) * band
                val e = abs(s - expect) / rs      // in full-resolution px
                if (e > worst) { worst = e; worstD = d / rs }
                n++
            }
            println("${c.name} rs $rs: n $n, shader-vs-table max ${"%.4f".format(worst)} px at d ${"%.1f".format(worstD)}")
            if (worst > 0.25f) for ((d, s2, _) in profileAlongRay(f, r, band)) if (d >= 2f && d <= band) println("   d ${"%.2f".format(d / rs)} shader ${"%.3f".format(s2 / rs)} table ${"%.3f".format(GlassRestMapTable.source(d / band) * band / rs)}")
            assertTrue(n >= 8, "${c.name} rs $rs: too few samples ($n)")
            assertTrue(worst <= 0.25f, "${c.name} rs $rs: shader off the table by $worst px at d=$worstD")
        }
    }

    private class Agreement(val n: Int, val rms: Double, val max: Float, val alphaMax: Float, val branchMismatch: Int)

    /**
     * Pixelwise agreement of two source fields over the whole covered area (both ends, both runs, top and
     * bottom), excluding only the outermost 2 px where the existing displacement fade lives. Units are
     * layer px; the caller divides by the render scale for reference px.
     */
    private fun compare(a: GlassSourceField.Field, b: GlassSourceField.Field, label: String, minDepthPx: Float = 2f): Agreement {
        var sum = 0.0; var n = 0; var worst = 0f; var alphaWorst = 0f; var branchMismatch = 0
        val cxL = a.pad.toFloat(); val cxR = (a.pad + a.width).toFloat(); val cyT = a.pad.toFloat(); val cyB = (a.pad + a.height).toFloat()
        for (y in 0 until a.layerHeight) for (x in 0 until a.layerWidth) {
            val i = a.index(x, y)
            alphaWorst = max(alphaWorst, abs(a.alpha[i] - b.alpha[i]))
            if (a.alpha[i] < 0.999f || b.alpha[i] < 0.999f) continue
            // Distance to the bounding box is an upper bound on the depth; skip the fade zone conservatively.
            val px = x + 0.5f; val py = y + 0.5f
            val dBox = minOf(px - cxL, cxR - px, py - cyT, cyB - py)
            if (dBox < minDepthPx) continue
            val ex = a.sx[i] - b.sx[i]; val ey = a.sy[i] - b.sy[i]
            val e = sqrt(ex * ex + ey * ey)
            sum += e * e; n++; worst = max(worst, e)
            // Same source identity: both displaced (> 0.3 px from the pixel's own position) or both identity.
            val da = sqrt((a.sx[i] - px) * (a.sx[i] - px) + (a.sy[i] - py) * (a.sy[i] - py))
            val db = sqrt((b.sx[i] - px) * (b.sx[i] - px) + (b.sy[i] - py) * (b.sy[i] - py))
            if ((da > 0.3f && db < 0.05f) || (db > 0.3f && da < 0.05f)) branchMismatch++
        }
        val rms = sqrt(sum / max(n, 1))
        println("$label: n $n RMS ${"%.4f".format(rms)} max ${"%.4f".format(worst)} px, coverage max diff ${"%.5f".format(alphaWorst)}, branch mismatches $branchMismatch")
        return Agreement(n, rms, worst, alphaWorst, branchMismatch)
    }

    private data class Geometry(val name: String, val width: Int, val height: Int, val radii: FloatArray, val vitalsExtrapolation: Boolean = false)

    private val geometries = listOf(
        Geometry("pill R66", 476, 132, floatArrayOf(66f, 66f, 66f, 66f)),
        Geometry("toolbar R72", 486, 144, floatArrayOf(72f, 72f, 72f, 72f)),
        Geometry("vitals bar R81 (extrapolation)", 1006, 162, floatArrayOf(81f, 81f, 81f, 81f), true),
        Geometry("asymmetric 72/40/72/40", 486, 144, floatArrayOf(72f, 40f, 72f, 40f)),
    )

    /**
     * The strict shared-path gate on the supported candidate path (closeout Phase 3): on the measured
     * profile, for both rest maps, at formation 0 / 0.5 / 1, at R = 66, R = 72, the Vitals size and an
     * asymmetric-corner rectangle, at render scales 1 / 0.5 / 0.25, the panel, content and container
     * shaders must sample from the same place: RMS <= 0.05 and max <= 0.25 reference px over every covered
     * pixel beyond the 2 px fade, coverage within 1/255, no source-identity mismatch. The container has one
     * radius per member, so the asymmetric case is panel vs content only. The readback resolves 0.016 px,
     * so at render scale 0.25 the quantisation floor is 0.064 reference px on the max and ~0.01 on the RMS.
     */
    @Test
    fun testTheThreeShadersSampleFromTheSamePlaceOnTheMeasuredProfile() {
        val failures = ArrayList<String>()
        var cells = 0
        for (g in geometries) for (rs in floatArrayOf(1f, 0.5f, 0.25f)) for (restMap in floatArrayOf(0f, 1f)) for (formation in floatArrayOf(0f, 0.5f, 1f)) {
            val w = (g.width * rs).toInt(); val h = (g.height * rs).toInt()
            val radii = FloatArray(4) { (g.radii[it] * rs).coerceAtMost(h / 2f) }
            val band = GlassMaterial.BEVEL_RATIO * radii.max()
            val pad = (8 * rs).toInt().coerceAtLeast(2)
            val tag = "${g.name} rs $rs restMap ${restMap.toInt()} formation $formation"
            val p = GlassSourceField.render(GlassSourceField.Kind.PANEL, w, h, pad, radii, band, formation = formation, restMap = restMap)
            val c = GlassSourceField.render(GlassSourceField.Kind.CONTENT, w, h, pad, radii, band, formation = formation, restMap = restMap)
            val pairs = ArrayList<Pair<String, Agreement>>()
            pairs.add("panel vs content" to compare(p, c, "$tag: panel vs content"))
            if (radii.all { it == radii[0] }) {
                val k = GlassSourceField.render(GlassSourceField.Kind.CONTAINER, w, h, pad, radii, band, formation = formation, restMap = restMap)
                pairs.add("panel vs container" to compare(p, k, "$tag: panel vs container"))
                pairs.add("content vs container" to compare(c, k, "$tag: content vs container"))
            }
            for ((name, a) in pairs) {
                cells++
                if (a.n < 200) failures.add("$tag $name: too few pixels (${a.n})")
                if (a.rms / rs > 0.05) failures.add("$tag $name: RMS ${"%.4f".format(a.rms / rs)} reference px exceeds 0.05")
                if (a.max / rs > 0.25f) failures.add("$tag $name: max ${"%.4f".format(a.max / rs)} reference px exceeds 0.25")
                if (a.alphaMax > 1f / 255f + 1e-6f) failures.add("$tag $name: coverage differs by ${a.alphaMax}")
                if (a.branchMismatch != 0) failures.add("$tag $name: source identity differs on ${a.branchMismatch} pixels")
            }
        }
        println("strict shared-path gate: $cells comparisons, ${failures.size} failures")
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    /**
     * Provenance, not a gate: the legacy optical profile and the held family were deliberately left on
     * the differenced normal in the content shader (the repair is scoped to the measured fold profile),
     * so their panel/content disagreement is measured and printed here as it was before Phase 3.
     */
    @Test
    fun testTheLegacyAndHeldProfilesKeepTheirDifferencedContentNormalForTheRecord() {
        val w = 486; val h = 144; val r = 72f; val band = GlassMaterial.BEVEL_RATIO * r; val pad = 8
        val rr = floatArrayOf(r, r, r, r)
        val pl = GlassSourceField.render(GlassSourceField.Kind.PANEL, w, h, pad, rr, band, profile = 0f, refractDepth = 12f)
        val cl = GlassSourceField.render(GlassSourceField.Kind.CONTENT, w, h, pad, rr, band, profile = 0f, refractDepth = 12f)
        val legacy = compare(pl, cl, "legacy profile, panel vs content (differenced, unchanged)")
        val ph = GlassSourceField.render(GlassSourceField.Kind.PANEL, w, h, pad, r, band, profile = 2f, heldLens = 1f)
        val ch = GlassSourceField.render(GlassSourceField.Kind.CONTENT, w, h, pad, r, band, profile = 2f, heldLens = 1f)
        val held = compare(ph, ch, "held family at 1, panel vs content (differenced, unchanged)")
        assertTrue(legacy.n > 200 && held.n > 200)
        assertTrue(legacy.alphaMax <= 1f / 255f + 1e-6f && held.alphaMax <= 1f / 255f + 1e-6f, "coverage must still agree")
    }

    @Test
    fun testTheSelectorLeavesTheFullFoldAndTheHeldFamilyUntouched() {
        val w = 486; val h = 144; val r = 72f; val band = GlassMaterial.BEVEL_RATIO * r; val pad = 8
        fun identical(a: GlassSourceField.Field, b: GlassSourceField.Field, label: String) {
            var worst = 0f
            for (i in a.sx.indices) {
                worst = max(worst, abs(a.alpha[i] - b.alpha[i]) * 255f)
                if (a.alpha[i] < 1f || b.alpha[i] < 1f) continue
                worst = max(worst, max(abs(a.sx[i] - b.sx[i]), abs(a.sy[i] - b.sy[i])))
            }
            println("$label: max difference ${"%.6f".format(worst)}")
            assertTrue(worst <= 0.05f, "$label: the selector changed this path by $worst (px, or coverage levels)")
        }
        for (kind in GlassSourceField.Kind.values()) {
            identical(
                GlassSourceField.render(kind, w, h, pad, r, band, formation = 1f, restMap = 0f),
                GlassSourceField.render(kind, w, h, pad, r, band, formation = 1f, restMap = 1f),
                "$kind fully formed fold",
            )
        }
        for (kind in listOf(GlassSourceField.Kind.PANEL, GlassSourceField.Kind.CONTENT)) {
            for (held in floatArrayOf(0f, 0.5f, 1f)) identical(
                GlassSourceField.render(kind, w, h, pad, r, band, profile = 2f, heldLens = held, restMap = 0f),
                GlassSourceField.render(kind, w, h, pad, r, band, profile = 2f, heldLens = held, restMap = 1f),
                "$kind held family at $held",
            )
        }
    }

    @Test
    fun testFormationBlendsTheSelectedMapLinearlyTowardTheFoldWithoutAPop() {
        val w = 486; val h = 144; val r = 72f; val band = GlassMaterial.BEVEL_RATIO * r; val pad = 8
        val f0 = GlassSourceField.render(GlassSourceField.Kind.PANEL, w, h, pad, r, band, formation = 0f, restMap = 1f)
        val f1 = GlassSourceField.render(GlassSourceField.Kind.PANEL, w, h, pad, r, band, formation = 1f, restMap = 1f)
        val fh = GlassSourceField.render(GlassSourceField.Kind.PANEL, w, h, pad, r, band, formation = 0.5f, restMap = 1f)
        var worst = 0f; var n = 0
        for (i in fh.sx.indices) {
            if (f0.alpha[i] < 0.999f) continue
            val ex = fh.sx[i] - 0.5f * (f0.sx[i] + f1.sx[i]); val ey = fh.sy[i] - 0.5f * (f0.sy[i] + f1.sy[i])
            worst = max(worst, sqrt(ex * ex + ey * ey)); n++
        }
        println("formation 0.5 vs mean of 0 and 1: n $n max ${"%.4f".format(worst)} px")
        // Three independently quantised readbacks (0.06 px each): the blend is linear by construction, so
        // anything past a tenth of a pixel would be a pop.
        assertTrue(worst <= 0.15f, "the formation blend is not linear in the selected map: $worst px")
    }

    @Test
    fun testTheCandidateHasNoNewDiscontinuityAlongTheNormalOrAcrossTheCornerRunTransitionAndNeverSamplesOutward() {
        val w = 486; val h = 144; val r = 72f; val band = GlassMaterial.BEVEL_RATIO * r; val pad = 8
        val f = GlassSourceField.render(GlassSourceField.Kind.PANEL, w, h, pad, r, band, restMap = 1f)
        // Along the normal through the right end: the source position may move at most the table's own
        // steepest slope (2.28 source px per display px, first segment) plus half a pixel of sampling.
        var prevS = Float.NaN; var worstStep = 0f
        for ((d, s, _) in profileAlongRay(f, r, band)) {
            if (d >= 1.5f && !prevS.isNaN()) worstStep = max(worstStep, abs(s - prevS))
            prevS = s
            assertTrue(s >= d - 0.1f || d < 1.5f, "outward sample at d=$d: source depth $s")
        }
        println("candidate: max source step per display px along the normal ${"%.3f".format(worstStep)}")
        assertTrue(worstStep <= 3.0f, "a jump of $worstStep px along the normal")
        // Across the corner/run transition: walk the row 6.5 px inside the top edge from the straight run
        // into the arc, stopping 2 px short of the arc's own outline where the rim fade takes over. The
        // source y must vary smoothly: cornerWeight blends over 0.2 R = 14.4 px, the rest map moves the
        // source by up to 30 px across that blend, and smoothstep peaks at 1.5x the mean slope, so a step
        // above about 3.1 px per px would be a seam.
        val row = pad + 6
        val cxEnd = pad + w - r; val cyEnd = pad + h / 2f
        var prev = Float.NaN; var worstTan = 0f
        for (x in pad + w / 2 until pad + w - 4) {
            val i = f.index(x, row)
            if (f.alpha[i] < 0.999f) continue
            val dx = x + 0.5f - cxEnd; val dy = row + 0.5f - cyEnd
            if (dx > 0f && r - sqrt(dx * dx + dy * dy) < 2f) break
            val sy = f.sy[i]
            if (!prev.isNaN()) worstTan = max(worstTan, abs(sy - prev))
            prev = sy
        }
        println("candidate: max source-y step per px along the top edge across the run/arc transition ${"%.3f".format(worstTan)}")
        if (worstTan > 3.5f) {
            val sb = StringBuilder("   sy along row $row from x ${pad + w / 2}: ")
            for (x in pad + w / 2 until pad + w - 4 step 2) { val i = f.index(x, row); if (f.alpha[i] >= 1f) sb.append("${x}:${"%.1f".format(f.sy[i])} ") }
            println(sb)
        }
        assertTrue(worstTan <= 3.5f, "a seam of $worstTan px at the corner/run transition")
        // Every sample lies inside the recorded layer (no uncovered sample).
        for (i in f.sx.indices) if (f.alpha[i] >= 1f) {
            assertTrue(f.sx[i] >= 0f && f.sx[i] <= f.layerWidth && f.sy[i] >= 0f && f.sy[i] <= f.layerHeight, "sample outside the layer at $i")
        }
    }

    @Test
    fun testTheCandidateRimSamplesInwardOnlyOverAHardBoundary() {
        // GlassMeasuredOpticsTest's inward-only claim, on the candidate: a panel wholly inside the black
        // half of a boundary 6 px outside its edge must stay black, including under the d < 6 extension.
        val pad = 40; val w = 240; val h = 160; val boundary = pad - 6
        val px = GlassRender.render(
            width = w, height = h, pad = pad, refractBand = 30f, profile = 1f, formation = 0f, restMap = 1f,
            radii = FloatArray(4) { 50f },
            backdrop = { _, y -> if (y < boundary) 0xFFFFFF else 0x000000 },
        )
        val lw = w + pad * 2
        val column = pad + w / 2
        val inside = (pad + 1 until pad + 40).map { GlassRender.luma(px[it * lw + column]) }
        assertTrue(inside.all { it < 20.0 }, "inward sampling must never bring the white in; got $inside")
        val corner = pad + 8
        val insideCorner = (pad + 1 until pad + 30).map { GlassRender.luma(px[it * lw + corner]) }
        assertTrue(insideCorner.all { it < 20.0 }, "the corner must not fetch outside either; got $insideCorner")
    }
}

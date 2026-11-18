package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #208: the material at the ends of the Tint Amount slider is the measured fit (Photos chrome over the calibration target),
 * and Tint 50 is the Safari-lab fit it was before. Every number below is read from the fit table, not from the code under test.
 */
class GlassMeasuredTintEndsTest {
    private val eps = 1e-6f

    /** Colour channels are packed as half floats and an alpha of 10 bits, so a tint is exact only to about 4e-3 (an 8-bit alpha). */
    private fun near(expected: Float, actual: Float, what: String, tolerance: Float = eps) =
        assertTrue(abs(expected - actual) <= tolerance, "$what: expected $expected, got $actual")

    @Test
    fun tint50IsTheLabFitAsBefore() {
        // The values the lab fit gave at Tint 50 (#199, #200), as the factories produced them before this change.
        for (dark in listOf(true, false)) for (style in listOf(GlassStyle.inApp(dark), GlassStyle.toolbar(dark), if (dark) null else GlassStyle.Regular)) {
            style ?: continue
            val app = if (dark) "dark" else "light"
            near(if (dark) 0.277f else 0.273f, style.fineShare, "$app share")
            near(if (dark) -0.22f else 0.22f, style.detailLift, "$app c")
            near(if (dark) 101.01f / 255f else 0f, style.toneCurve, "$app bow")
            near(if (dark) 0.3985f else 0.498f, style.tint.alpha, "$app opacity", 4e-3f)
            near(if (dark) 0f else 1f, style.tint.red, "$app tint colour", 4e-3f)
            near(if (dark) 31.72f / 255f else 5.5f / 255f, style.tintLift, "$app lift")
            near(15.5f, style.wideKernel.value, "$app wide sigma")
            assertEquals(0f, style.legibility, "$app legibility")
            assertEquals(1f, style.softDetail, "$app softDetail")
        }
    }

    @Test
    fun theEndsHitTheFittedValues() {
        // (tint, dark) -> k, |c|, bow x 255, opacity: the fit's table, dark and light at Tint 0 and 100.
        data class Row(val tint: Float, val dark: Boolean, val k: Float, val c: Float, val bow: Float, val alpha: Float)
        val rows = listOf(
            Row(0f, true, 0.663f, 0.336f, 101.6f, 0.401f), Row(100f, true, 0f, 0f, 49.8f, 0.703f),
            Row(0f, false, 0.664f, 0.334f, 0f, 0.382f), Row(100f, false, 0f, 0f, 0f, 0.682f),
        )
        for (r in rows) for (style in listOf(GlassStyle.inApp(r.dark, r.tint), GlassStyle.toolbar(r.dark, r.tint))) {
            val what = "${if (r.dark) "dark" else "light"} Tint ${r.tint.toInt()}"
            near(r.k, style.fineShare, "$what share")
            near(if (r.dark) -r.c else r.c, style.detailLift, "$what c")
            near(r.bow / 255f, style.toneCurve, "$what bow")
            near(r.alpha, style.tint.alpha, "$what opacity", 4e-3f)
            near(15.5f, style.wideKernel.value, "$what wide sigma")
            assertEquals(0f, style.legibility, "$what legibility")
            assertEquals(1f, style.softDetail, "$what softDetail")
            assertTrue(GlassMaterial.usesDetailCross(style.blurRadius), "$what keeps the four-tap detail kernel")
        }
    }

    @Test
    fun betweenTheNodesTheMaterialMovesLinearly() {
        // Authored interpolation: halfway between Tint 0 and 50 is the mean of the nodes; so between 50 and 100.
        for (dark in listOf(true, false)) {
            val a = GlassStyle.inApp(dark, 0f); val m = GlassStyle.inApp(dark, 25f); val b = GlassStyle.inApp(dark, 50f)
            near((a.fineShare + b.fineShare) / 2f, m.fineShare, "share at 25")
            near((a.tint.alpha + b.tint.alpha) / 2f, m.tint.alpha, "opacity at 25", 4e-3f)
            val c = GlassStyle.inApp(dark, 75f); val d = GlassStyle.inApp(dark, 100f)
            near((b.toneCurve + d.toneCurve) / 2f, c.toneCurve, "bow at 75")
            near((b.detailLift + d.detailLift) / 2f, c.detailLift, "c at 75")
        }
    }
}

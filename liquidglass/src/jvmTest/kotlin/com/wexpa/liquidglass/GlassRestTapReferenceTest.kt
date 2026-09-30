package com.wexpa.liquidglass

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner's 6690–6701 resting-tap sequence, not the 6724–6733 held release. */
class GlassRestTapReferenceTest {
    private fun bar() = glassSelectorBarOf(
        Density(3f), 1150f, 186f, GlassTabBarStyle.V3(true).copy(height = 62.dp),
        5, 0f, GlassSelectorSpec(),
    )

    @Test fun settledRestAndTransitHaveDifferentEnvelopes() {
        val b = bar()
        // Original 6701: bar about184px, settled pill about160px (±4px).
        assertEquals(162f, b.baseHalfHeight * 2f, 4f, "settled WhatsApp/Phone height")
        assertTrue(b.allowedHalfHeight > b.baseHalfHeight + 3f,
            "the transit body needs room to change height inside its bar")
        assertTrue(b.allowedHalfHeight < b.height / 2f, "retain the bar's outer hairline")
    }

    @Test fun longTapHasBoundedShapeRecoveryWithoutBecomingAHeldGesture() {
        val b = bar()
        val traces = (1..4).associateWith { distance ->
            val c = GlassPoseController()
            c.attach(b, null, null); c.snapToRest(0); c.retarget(distance)
            val e = GlassPoseExtents()
            (0..240).map { i ->
                val t = i / 240f
                c.advanceTo(t); c.extents(e)
                assertTrue(!c.isHeld)
                assertTrue(!c.solverFailed, "contact failed at distance=$distance t=$t")
                assertTrue(c.protrusion() < .1f, "ordinary tap escaped its envelope")
                listOf(t, c.centreX, e.width, e.height)
            }
        }
        val output = File("build/reports/astra/rest-tap-traces.csv")
        output.parentFile.mkdirs()
        output.writeText("slots,t,cx,width,height\n" + traces.flatMap { (d, rows) ->
            rows.map { "$d," + it.joinToString(",") }
        }.joinToString("\n"))
        val restWidth = 2f * b.baseHalfWidth
        val short = traces.getValue(1)
        val long = traces.getValue(4)
        // Ordering from the owner's short/long examples. Bounds are an authored review gate,
        // not a fit to absent pointer timestamps or an assertion of Apple's spring values.
        val shortPeak = short.maxOf { it[2] }
        val longPeak = long.maxOf { it[2] }
        assertTrue(longPeak > shortPeak + .1f * b.slotWidth, "tap distance did not affect shape")
        assertTrue(longPeak < restWidth + b.slotWidth, "held-drag elongation leaked into taps")
        assertTrue(long.maxOf { it[3] } > 2f * b.baseHalfHeight + 3f,
            "tap height was pinned to the resting silhouette")
        val recovery = long.filter { it[0] > .2f }
        val low = recovery.minBy { it[2] }
        assertTrue(low[2] < restWidth * .975f, "long tap lost its visible shape rebound")
        assertTrue(low[2] > restWidth * .86f, "arrival collapsed instead of recovering")
        assertTrue(recovery.any { it[0] > low[0] && it[2] > low[2] + 1f },
            "the compressed arrival never recovered")
        assertTrue(abs(long.last()[2] - restWidth) < .5f)
        assertTrue(abs(long.last()[1] - b.centreOf(4)) < .5f)
    }
}

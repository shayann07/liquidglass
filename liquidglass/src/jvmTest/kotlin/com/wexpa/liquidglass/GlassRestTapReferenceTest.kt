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

    @Test fun longTapHasBoundedShapeRecoveryWithoutBecomingAHeldGesture() = checkRecovery(GlassPoseSpec())

    @Test fun calmLongTapKeepsTheOriginalArrivalRecovery() = checkRecovery(GlassPoseSpec.Calm)

    private fun checkRecovery(spec: GlassPoseSpec) {
        val b = bar()
        val traces = (1..4).associateWith { distance ->
            val c = GlassPoseController(spec)
            c.attach(b, null, null); c.snapToRest(0); c.retarget(distance)
            val e = GlassPoseExtents()
            (0..240).map { i ->
                val t = i / 240f
                c.advanceTo(t); c.extents(e)
                assertTrue(!c.isHeld)
                assertTrue(!c.solverFailed, "contact failed at distance=$distance t=$t")
                if (spec.tapAreaGain == 0f) {
                    assertTrue(c.protrusion() < .1f, "historical tap escaped its envelope")
                } else {
                    assertTrue(e.height <= b.height * 1.23f, "tap exceeded the original material envelope")
                }
                listOf(t, c.centreX, e.width, e.height)
            }
        }
        val output = File("build/reports/astra/rest-tap-${if (spec.travelOnly) "calm" else "traces"}.csv")
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
        val recovery = long.filter { it[0] > .2f }
        val low = recovery.minBy { it[2] }
        println("REST_TAP calm=${spec.travelOnly} rest=$restWidth short=$shortPeak long=$longPeak low=${low[2]} at=${low[0]}")
        assertTrue(longPeak > shortPeak + .1f * b.slotWidth, "tap distance did not affect shape")
        assertTrue(longPeak < restWidth + b.slotWidth, "held-drag elongation leaked into taps")
        assertTrue(long.maxOf { it[3] } > 2f * b.baseHalfHeight + 3f,
            "tap height was pinned to the resting silhouette")
        assertTrue(low[2] < restWidth * .975f, "long tap lost its visible shape rebound")
        assertTrue(low[2] > restWidth * .86f, "arrival collapsed instead of recovering")
        assertTrue(recovery.any { it[0] > low[0] && it[2] > low[2] + 1f },
            "the compressed arrival never recovered")
        assertTrue(abs(long.last()[2] - restWidth) < .5f)
        assertTrue(abs(long.last()[1] - b.centreOf(4)) < .5f)
    }
}

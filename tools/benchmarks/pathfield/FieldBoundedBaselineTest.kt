package com.wexpa.liquidglass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FieldBoundedBaselineTest {
    /** Every shape/size/pad/band of the bounded-transform suite, against the unmodified upstream algorithm. */
    @Test fun boundedFieldMatchesUpstreamOnEveryCase() {
        val cases = GlassPathFieldBoundedTest()
        val buffers = GlassPathFieldBuffers()
        for (c in cases.cases()) {
            val lw = c.w + 2 * c.pad
            val lh = c.h + 2 * c.pad
            val upstream = buildPathFieldBaseline(c.path, lw, lh, c.pad.toFloat(), c.band)
            val ours = buildPathField(c.path, lw, lh, c.pad.toFloat(), c.band, buffers)
            assertNotNull(upstream, c.name)
            assertNotNull(ours, c.name)
            assertEquals(cases.pixels(upstream.bitmap), cases.pixels(ours.bitmap), "${c.name} ${c.w}x${c.h} pad=${c.pad} band=${c.band}")
        }
        println("BOUNDED_BASELINE cases=${cases.cases().size} identical=true")
    }
}

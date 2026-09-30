package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/** Local original-video side profile, independent of a glyph or fitted Android screenshot. */
class GlassHeldSideBoundaryTest {
    @Test fun recoveryDoesNotChangeOtherProfilesOrAnUnformedLens() {
        for ((profile, held) in listOf(0f to 1f, 1f to 1f, 2f to 0f)) {
            fun render(recovery: Float) = GlassRender.render(
                140, 88, 12, profile = profile, heldLens = held,
                edgeShadow = .12f, specular = .49f, counterLight = 1f,
                heldEdgeRecovery = recovery, backdrop = { x, y ->
                    0xFF000000.toInt() or ((40 + (x + y) % 90) * 0x010101)
                },
            )
            assertContentEquals(render(0f), render(3f), "unrelated profile or unformed lens changed")
        }
    }

    @Test fun theHeldSideHasANarrowDarkStepWithoutAWhiteSideOutline() {
        // Original PQ->sRGB IMG_6756 T01, settled frames0050/0084: the side falls
        // from ~45 to16 codes and recovers within6px. ±6codes allows video/raster phase.
        // This flat source isolates the directional boundary term from refraction of ink.
        val style = GlassTabBarStyle.V3(dark = true).lens
        val width = 350
        val height = 224
        val pad = 24
        val row = width + 2 * pad
        val pixels = GlassRender.render(
            width, height, pad, profile = 2f, heldLens = 1f,
            edgeShadow = style.edgeShadow, specular = style.specular,
            counterLight = style.counterLight, heldGlow = style.heldGlow,
            heldEdgeRecovery = style.heldEdgeRecovery.value * 3f,
            bevel = style.bevel.value * 3f, specularPower = style.specularPower,
            edgeLight = style.edgeLight, highlightChroma = style.highlightChroma,
            endpointAlpha = 1f, backdrop = { _, _ -> 0xFF2D2D2D.toInt() },
        )
        fun luma(x: Int, y: Int) = GlassRender.luma(pixels[(y + pad) * row + x + pad])
        val side = (0..12).map { luma(it, height / 2) }
        println("HELD-SIDE $side")
        assertTrue(side.min() in 10.0..22.0, "held side needs the original narrow dark step: $side")
        val minimum = side.indices.minBy { side[it] }
        // Independent original settled frames0040/0050/0060/0070/0084/0090, median over
        // twelve rows. Compare depth from the dark minimum, allowing raster/video phase.
        // The previous assertion tested only that the tail ENDED, permitting no tail at all.
        val ranges = listOf(18.0..32.0, 31.0..41.0, 36.0..44.0, 39.0..46.0, 40.0..46.0)
        for ((i, range) in ranges.withIndex()) {
            assertTrue(side[minimum + i + 1] in range,
                "side recovery at ${i + 1}px is outside original-frame bounds: $side")
        }
        assertTrue(abs(side[minimum + 8] - 45.0) <= 1.0, "side tail must recover by8px")
        // The original top/bottom overhang DOES have lighting; the former blanket
        // no-top-outline rule was unsupported. Keep this gate scoped to the side and
        // interior; GlassHeldRimReferenceTest independently covers the raised boundary.
        assertTrue(side.max() <= 46.0, "a white side outline was introduced: $side")
        assertTrue(abs(luma(width / 2, height / 2) - 45.0) <= 1.0,
            "rim lighting must not become a full interior veil")
    }
}

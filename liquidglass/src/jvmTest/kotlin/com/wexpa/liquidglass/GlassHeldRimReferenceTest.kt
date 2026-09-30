package com.wexpa.liquidglass

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** IMG_6756 T01: the raised rim is visible over black, separately from the inset bar edge. */
class GlassHeldRimReferenceTest {
    @Test fun theHeldRimRemainsVisibleOverBlackWithoutLiftingTheWholeInterior() {
        // Source frame0050, x745..805: outer top at184..187 (peak~94), then~10
        // at189, ~3 at196 and~1 at202. The second chromatic bar edge is at208..211.
        // These broad luma limits test the missing boundary, not calibrated photometry or
        // exact RGB parity. A black source isolates lighting from the second refracted edge.
        val s = GlassTabBarStyle.V3(true).lens
        val width = 350; val height = 224; val pad = 24; val stride = width + 2 * pad
        val pixels = GlassRender.render(
            width, height, pad, profile = 2f, heldLens = 1f,
            bevel = s.bevel.value * 3f, specular = s.specular,
            specularPower = s.specularPower, counterLight = s.counterLight,
            edgeLight = s.edgeLight, highlightChroma = s.highlightChroma,
            edgeShadow = s.edgeShadow, heldGlow = s.heldGlow,
            heldEdgeRecovery = s.heldEdgeRecovery.value * 3f,
            backdrop = { _, _ -> 0xFF000000.toInt() },
        )
        fun luma(x: Int, y: Int) = GlassRender.luma(pixels[(y + pad) * stride + x + pad])
        val top = (0..24).map { luma(width / 2, it) }
        val bottom = (0..24).map { luma(width / 2, height - 1 - it) }
        val output = File("build/reports/astra/held-rim-profile.csv")
        output.parentFile.mkdirs()
        output.writeText("depth,top,bottom\n" + top.indices.joinToString("\n") { "$it,${top[it]},${bottom[it]}" })
        assertTrue(top.take(5).max() in 65.0..115.0, "missing or excessive top rim: $top")
        assertTrue(bottom.take(5).max() in 65.0..115.0, "missing or excessive bottom rim: $bottom")
        assertTrue(top[12] < 8.0 && top[20] < 3.0, "rim glow spreads too far: $top")
        assertTrue(luma(width / 2, height / 2) <= 1.0, "rim must not add an interior veil")
        assertTrue((0..12).maxOf { luma(it, height / 2) } <= 1.0,
            "the lit boundary must not turn into a white side outline")
    }
}

package com.wexpa.liquidglass

import kotlin.test.Test
import kotlin.test.assertEquals

/** Original 6756 T01: stable scene, untouched bar region, native PQ->sRGB values. */
class GlassPhoneMaterialReferenceTest {
    @Test fun theCalmBarDoesNotBrightenTwiceWhileHeld() {
        val calm = GlassTabBarStyle.Calm(dark = true)
        val s = calm.bar
        // GlassTabBar supplies heldLift to its style; LiquidGlass adds the interaction's
        // pressLift. Check their combined endpoint against the independent44-level reference.
        val pixels = GlassRender.render(
            width = 834, height = 186, pad = 24, profile = 2f,
            tintAlpha = s.tint.alpha,
            tint = Triple(s.tint.red, s.tint.green, s.tint.blue),
            lift = s.tintLift + calm.heldLift + checkNotNull(calm.barInteraction).pressLift,
            backdrop = { _, _ -> 0xFF000000.toInt() },
        )
        val value = GlassRender.luma(pixels[(93+24)*(834+48)+350+24])
        assertEquals(44.0, value, 1.0, "Phone held bar must not add generic control lift again")
    }

    @Test fun theDarkV3BarMatchesTheOriginalRestAndHeldPlateaus() {
        val v3 = GlassTabBarStyle.V3(dark = true)
        for ((held, expected) in listOf(false to 32.0, true to 44.0)) {
            val s = v3.bar
            val pixels = GlassRender.render(
                width = 834, height = 186, pad = 24, profile = 2f,
                tintAlpha = s.tint.alpha,
                tint = Triple(s.tint.red, s.tint.green, s.tint.blue),
                lift = s.tintLift + if (held) v3.heldLift else 0f,
                backdrop = { _, _ -> 0xFF000000.toInt() },
            )
            val value = GlassRender.luma(pixels[(93+24)*(834+48)+350+24])
            assertEquals(expected, value, 1.0, "6756 T01 bar plateau, held=$held")
        }
        // The original target-calibrated general material remains available unchanged.
        assertEquals(35f / 255f, GlassTabBarStyle.Measured(dark = true).bar.tintLift, 1e-6f)
        assertEquals(16f / 255f, GlassTabBarStyle.Measured(dark = true).heldLift, 1e-6f)
        val light = GlassTabBarStyle.Measured(dark = false)
        assertEquals(light.bar.tintLift, GlassTabBarStyle.V3(dark = false).bar.tintLift)
        assertEquals(light.heldLift, GlassTabBarStyle.V3(dark = false).heldLift)
    }
}

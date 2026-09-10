package com.wexpa.liquidglass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The presets, instantiated.
 *
 * The first build of [GlassTabBarStyle] crashed the app on launch with an NPE from its own
 * companion: `Dark` was declared before the two materials its constructor defaults read, so it
 * was built while they were still null. No test touched the class, so the JVM never saw it.
 * Constructing every preset here is the whole point.
 */
class GlassTabBarStyleTest {

    @Test
    fun testEveryPresetConstructsWithItsMaterialsInPlace() {
        val style = GlassTabBarStyle.Dark
        assertNotNull(style.pill)
        assertNotNull(style.lens)
        assertEquals(GlassTabBarStyle.RestingInset, style.pill)
        assertEquals(GlassTabBarStyle.HeldLens, style.lens)
        assertEquals(GlassStyle.DarkChrome, style.bar)
    }

    @Test
    fun testTheRestingInsetHasNoOptics() {
        // Measured: a flat dark inset with a two-pixel edge and nothing else. A bend here grows a
        // bright ring at the rim, which is the most visible tell of a recreation.
        val pill = GlassTabBarStyle.RestingInset
        assertEquals(0f, pill.refractionDepth.value)
        assertEquals(0f, pill.dispersion)
        assertEquals(0f, pill.mirror)
        assertEquals(0f, pill.specular)
        assertEquals(0f, pill.edgeLight)
        assertEquals(0f, pill.fresnel)
        assertEquals(0f, pill.innerShadow)
        assertEquals(0f, pill.backdropBlur.value, "the pill looks through the bar, which has already frosted")
    }

    @Test
    fun testTheHeldLensIsClearWithALitEdgeAndNoBead() {
        val lens = GlassTabBarStyle.HeldLens
        assertTrue(lens.tint.alpha < 0.05f, "the lens interior reads as the bar does; it adds almost nothing")
        assertTrue(lens.refractionDepth.value > 0f && lens.dispersion > 0.3f, "strong outward refraction with a visible colour split")
        assertEquals(0f, lens.bevelPeak, "an edge line, not a bead")
        assertEquals(0f, lens.mirror)
        assertEquals(0f, lens.innerShadow)
        assertTrue(lens.edgeLight > 1f, "the outermost line carries the light, not the bevel lobe")
        assertEquals(1f, GlassTabBarStyle.Dark.lensInteraction.pressScale, "the reference does not scale-bounce")
    }

    @Test
    fun testTheLensStandsProudByTheSameAmountAboveAndBelow() {
        val style = GlassTabBarStyle.Dark
        assertEquals(0f, style.lensRise.value, "the native reference is centred on the bar")
        assertTrue(style.lensOverflow.value > 0f)
    }
}

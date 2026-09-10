package com.wexpa.liquidglass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Presets are static state, and static state has an initialisation order.
 *
 * [GlassTabBarStyle] once shipped with its default declared before the two styles it refers to,
 * which meant the companion initialised those fields to null and the app crashed on launch with
 * an `ExceptionInInitializerError` that named nothing useful. No test had ever constructed a
 * preset, so nothing caught it. These do.
 */
class GlassSliderStyleTest {

    @Test
    fun testEveryPresetConstructs() {
        assertTrue(GlassSliderStyle.KnobRest.refractionDepth.value >= 0f)
        assertTrue(GlassSliderStyle.KnobHeld.refractionDepth.value >= 0f)
        val dark = GlassSliderStyle.Dark
        assertEquals(GlassSliderStyle.KnobRest, dark.knobRest)
        assertEquals(GlassSliderStyle.KnobHeld, dark.knobHeld)
    }

    @Test
    fun testTheRestingKnobIsAShapeAndNotAMaterial() {
        // Apple's rule for sliders: the knob transforms into Liquid Glass during interaction,
        // which means it is not Liquid Glass the rest of the time. Give a resting knob even a
        // shallow refraction and its rim grows a bright ring of compressed track, which is the
        // clearest tell that an implementation is a recreation.
        val rest = GlassSliderStyle.KnobRest
        assertEquals(0f, rest.refractionDepth.value, "no bend at rest")
        assertEquals(0f, rest.dispersion, "no colour split at rest")
        assertEquals(0f, rest.specular, "no highlight at rest")
        assertEquals(0f, rest.mirror)
        assertEquals(0f, rest.innerShadow)
        assertTrue(rest.tint.alpha > 0.8f, "a resting knob is a solid shape")
    }

    @Test
    fun testTheHeldKnobIsGlassWithALitRimAndADarkSideStep() {
        val held = GlassSliderStyle.KnobHeld
        assertTrue(held.tint.alpha < 0.1f, "held, it is clear enough to see the track through")
        assertTrue(held.refractionDepth.value > 0f && held.dispersion > 0.2f)
        assertTrue(held.edgeLight > 1f, "the outermost line carries the light")
        assertTrue(held.edgeShadow > 0f, "the sides are a dark step, the edge line being directional")
        assertEquals(0f, held.fresnel, "fresnel is omnidirectional and would light the sides")
        assertEquals(0f, held.innerShadow, "no bead")
    }

    @Test
    fun testTheKnobDoesNotScaleBounce() {
        // It grows into glass; it does not pop. The held style is already larger than the
        // resting one, so a press scale on top reads as a bounce the reference does not have.
        assertEquals(1f, GlassSliderStyle.Dark.knobInteraction.pressScale)
        assertTrue(!GlassSliderStyle.Dark.knobInteraction.gel, "stretch is driven by speed, not by press")
    }

    @Test
    fun testTheKnobStandsProudOfTheTrack() {
        val style = GlassSliderStyle.Dark
        assertTrue(
            style.knobSize > style.trackHeight,
            "a knob flush with its track cannot read as sitting on it",
        )
    }
}

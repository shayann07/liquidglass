package com.wexpa.liquidglass

import kotlin.test.Test
import kotlin.test.assertEquals

class GlassAstraMorphTest {
    @Test fun theProductionMorphCarriesTheLensFieldsAtEveryIntermediateState() {
        val style = GlassTabBarStyle.V3()
        for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val actual = lerpGlassStyle(style.pill, style.lens, fraction)
            assertEquals(style.lens.heldMagnification * fraction, actual.heldMagnification, 1e-6f)
            assertEquals(style.pill.heldGlow * (1f - fraction) + style.lens.heldGlow * fraction,
                actual.heldGlow, 1e-6f)
            assertEquals(style.lens.heldEdgeRecovery.value * fraction,
                actual.heldEdgeRecovery.value, 1e-6f)
        }
        assertEquals(style.pill, lerpGlassStyle(style.pill, style.lens, 0f))
        assertEquals(style.lens, lerpGlassStyle(style.pill, style.lens, 1f))
    }
}

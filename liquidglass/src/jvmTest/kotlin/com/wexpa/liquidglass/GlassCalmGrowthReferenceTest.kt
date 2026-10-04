package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Independent Phone T01 geometry; no pointer gain or rendered-Astra target is fitted here. */
class GlassCalmGrowthReferenceTest {
    @Test fun calmBarPressAndExtremeDragStayBelowTheOriginalWholeBarBound() {
        val interaction = checkNotNull(GlassTabBarStyle.Calm().barInteraction)
        val width = 834f; val height = 186f; val radius = 93f; val density = 3f
        var maximum = 0f
        for (degrees in 0 until 360 step 5) for (amount in listOf(0f, .1f, .25f, .5f, .75f, 1f)) {
            for (distance in listOf(0f, 15f, 60f, 200f, 10000f)) {
                val angle = Math.toRadians(degrees.toDouble()).toFloat()
                val rawX = cos(angle) * distance * density
                val rawY = sin(angle) * distance * density
                val scale = 1f + (interaction.pressScale - 1f) * amount
                val compliance = glassPullTargetScale(hypot(rawX, rawY), interaction.pullFollow,
                    height * interaction.pressScale * interaction.pullLimit)
                val x = rawX * compliance; val y = rawY * compliance
                val along = glassPullExtent(width, height, radius, scale, scale, x, y)
                val across = glassPullExtent(width, height, radius, scale, scale, -y, x)
                val raw = glassPullDeformation(x, y, along, interaction.pullElongation,
                    interaction.pullWidthRatio, interaction.pullFollow, acrossExtentPx = across)
                val shape = glassAnchoredMaterialPull(
                    glassSurfacePull(raw, width, height, density, along), width, height)
                val a = Math.toRadians(shape.angleDegrees.toDouble()).toFloat()
                val c = (shape.along - shape.across) * cos(a) * sin(a) * scale
                val d = (shape.along * sin(a) * sin(a) + shape.across * cos(a) * cos(a)) * scale
                // Independent capsule support, not the production bounds helper. The original
                // IMG_6756 PTS29863/600 gives204..205px across four columns and three thresholds.
                val bodyHeight = 2f * (abs(c) * (width / 2f - radius) + radius * hypot(c, d))
                maximum = maxOf(maximum, bodyHeight)
                assertTrue(bodyHeight <= 205f, "bar exceeded original extreme at $degrees: $bodyHeight")
                assertEquals(0f, shape.translationX); assertEquals(0f, shape.translationY)
            }
        }
        assertTrue(maximum > 195f, "test did not exercise held material")
        println("PHONE_T04_CALM_BAR maximum=$maximum reference=204..205")
    }

    @Test fun calmBarPreservesOrdinaryHeldGrowthSeparatelyFromDrag() {
        val style = GlassTabBarStyle.Calm()
        val press = checkNotNull(style.barInteraction)
        val density = 3f
        fun scale(side: Float) = if (press.pressGrowth.value > 0f) {
            glassPressScale(side, press.pressGrowth.value * density, press.pressScale)
        } else press.pressScale
        // IMG_6756 PTS 20819/600: rows191..376 (186px); PTS21019/600:186..381 (196px).
        // All four unoccluded columns agree at thresholds5/10/15. The +/-1px bound includes
        // raster coverage; no pointer gain or dynamic timing is inferred from these stills.
        assertEquals(196f, 186f * scale(186f), 1f, "ordinary-held bar height")
        assertEquals(GlassResponse.Calm, press.response)
        assertEquals(1f, style.heldScale, "do not apply a second layout/label expansion")
        assertEquals(GlassInteraction.Calm.pullElongation, press.pullElongation)
        assertEquals(GlassInteraction.Calm.pullLimit, press.pullLimit)
    }

    @Test fun aCalmPhoneHoldDoesNotMagnifyTheWholeRestingCapsule() = checkReference(GlassPoseSpec())
    @Test fun theRecommendedCalmPresetKeepsTheOriginalSettledHeight() = checkReference(GlassPoseSpec.Calm)

    private fun checkReference(spec: GlassPoseSpec) {
        // IMG_6756: resting bar186px, selected capsule284x162px. The settled held
        // capsule is approximately340–360x225px; a uniformly enlarged capsule is ~394px.
        // ±10px width admits edge visibility/held-frame variation, not the old model.
        val bar = GlassSelectorBar(
            width = 834f, height = 186f, inset = 18f, count = 3, cornerRadius = 93f,
            baseHalfWidth = 142f, baseHalfHeight = 81f, allowedHalfHeight = 81f,
            heldHalfWidth = 177f, heldHalfHeight = 112.5f, density = 3f, maxBodyWidth = 700f,
        )
        val c = GlassPoseController(spec)
        c.attach(bar, null, null); c.snapToRest(1)
        c.pointerDown(bar.centreOf(1), bar.centreY, 1_825_000.0, true)
        repeat(180) { c.advanceTo((it + 1) / 120f) }
        val e = GlassPoseExtents(); c.extents(e)
        assertTrue(c.isHeld)
        println("PHONE_T01 response=${spec.responseName()} height=${e.height} width=${e.width}")
        assertEquals(225f, e.height, 4f, "Phone calm height")
        assertEquals(350f, e.width, 10f, "Phone calm width: pressure must not magnify the spine")
    }

    private fun GlassPoseSpec.responseName() = if (this == GlassPoseSpec.Calm) "Calm" else "Historical"
}

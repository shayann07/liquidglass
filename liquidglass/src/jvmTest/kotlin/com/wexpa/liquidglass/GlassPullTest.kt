package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Preserve custom input gains separately from the measured body geometry. The earlier version
 * mistook the visible head for the finger, which the recording does not identify.
 */
class GlassPullTest {

    private val spec = GlassInteraction(pull = true)

    private fun pull(dx: Float, dy: Float, extent: Float) = glassPullDeformation(
        pullX = dx, pullY = dy, extentPx = extent,
        elongation = spec.pullElongation, widthRatio = spec.pullWidthRatio, follow = spec.pullFollow,
    )

    @Test
    fun customUnboundedInputGainsRemainCompatible() {
        val d = pull(0f, -145f, extent = 238f)
        println("PULL-LAW button: along %.3f across %.3f angle %.1f translation %.1f".format(d.along, d.across, d.angleDegrees, d.translationY))
        assertEquals(1f + 0.45f * 145f / 238f, d.along, 1e-5f)
        assertEquals(1f - 1.1f * (d.along - 1f), d.across, 1e-5f)
        assertEquals(-90f, d.angleDegrees, 0.01f)
        assertTrue(abs(d.translationY + 0.78f * 145f) < 0.01f, "the body follows 0.78 of the finger")
    }

    @Test
    fun observedCentroidTravelPredictsSearchAndKeyDeformation() {
        val s = GlassInteraction.Pullable
        for ((centre, extent, along, across) in listOf(
            listOf(112f, 234f, 303f / 234f, 165f / 234f),
            listOf(89f, 282f, 342f / 282f, 222f / 282f),
        )) {
            val d = glassPullDeformation(centre / s.pullFollow, 0f, extent,
                s.pullElongation, s.pullWidthRatio, s.pullFollow)
            assertEquals(centre, d.translationX, 0.01f)
            assertEquals(along, d.along, 0.025f)
            assertEquals(across, d.across, 0.025f)
        }
    }

    @Test
    fun theLawIsAbsoluteSoACardPulledTheSameDistanceStretchesByTheSamePixels() {
        val button = pull(0f, -145f, extent = 238f)
        val card = pull(0f, -145f, extent = 1600f)
        val buttonPx = (button.along - 1f) * 238f
        val cardPx = (card.along - 1f) * 1600f
        assertTrue(abs(buttonPx - cardPx) < 1f, "the button grew $buttonPx px, the card $cardPx px: the law is not absolute")
        assertTrue(card.along < 1.06f, "a 1600 px card pulled 145 px reads x${card.along}, not subtle")
        val far = pull(0f, -5000f, extent = 238f)
        assertTrue(far.along <= 1.6f + 1e-4f && far.across >= 0.5f - 1e-4f, "an enormous pull was not capped")
        assertEquals(GlassPullDeformation.None, pull(0f, 0f, extent = 238f))
    }

    @Test
    fun theBalloonGrowsByPointsNotByRatio() {
        val growth = 8.7f * 3f   // 8.7 pt a side at 3x
        val button = glassPressScale(sidePx = 186f, growthPx = growth, maxScale = 1.28f)
        val key = glassPressScale(sidePx = 234f, growthPx = growth, maxScale = 1.28f)
        val card = glassPressScale(sidePx = 1100f, growthPx = growth, maxScale = 1.28f)
        // A wide pill: a few dp taller, and only a few dp wider - not 28 % wider (r16 phone).
        val pillWide = glassPressScale(sidePx = 3156f, growthPx = growth, maxScale = 1.28f)
        val pillTall = glassPressScale(sidePx = 132f, growthPx = growth, maxScale = 1.28f)
        println("PULL-LAW balloon button %.3f key %.3f card %.3f pill %.3f x %.3f".format(button, key, card, pillWide, pillTall))
        assertTrue(abs(button - 1.28f) < 0.005f, "the 62 pt button balloons x$button (recording x1.28)")
        assertTrue(abs(key - 1.22f) < 0.02f, "the 78 pt key balloons x$key (recording x1.22)")
        assertTrue(card < 1.06f, "a card balloons x$card; it should grow by the same few dp and read subtle")
        assertTrue(pillWide < 1.02f && pillTall > 1.25f, "a wide pill should grow tall, not wide: x$pillWide by x$pillTall")
    }

    @Test
    fun thePullResponseIsOptInAndReducedMotionRemovesElasticity() {
        assertEquals(1.04f, GlassInteraction.Default.pressScale)
        assertEquals(1.26f, GlassInteraction.Pullable.pressScale)
        assertTrue(GlassInteraction.Pullable.pull)
        assertEquals(0f, GlassInteraction.Default.pressLift)
        assertTrue(GlassInteraction.Pullable.pressLift > 0.1f)
        assertTrue(!GlassInteraction.Default.pull)
        assertEquals(1f, GlassInteraction.ReducedMotion.pressScale)
        assertTrue(!GlassInteraction.ReducedMotion.pull)
    }
}

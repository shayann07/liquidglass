package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassBarShapeReferenceTest {
    @Test fun oppositeAndDiagonalPullsPreserveAreaAndDoNotReachTheLimitEarly() {
        val interaction = checkNotNull(GlassTabBarStyle.Calm().barInteraction)
        val w = 834f; val h = 186f; val press = 1.05f
        val limit = h * press * interaction.pullLimit
        fun shape(x: Float, y: Float): GlassPullDeformation {
            val compliance = glassPullTargetScale(hypot(x, y), interaction.pullFollow, limit)
            return glassMaterialPull(w,h,h/2,3f,press,press,x*compliance,y*compliance,limit,interaction)
        }
        for (degrees in 0 until 360 step 5) {
            val a = degrees * kotlin.math.PI.toFloat() / 180f
            val p = shape(cos(a)*10000, sin(a)*10000)
            assertEquals(1f, p.along*p.across, 1e-6f)
            assertTrue(p.along <= 1.049f && p.across >= .954f)
            assertEquals(0f, p.translationX); assertEquals(0f, p.translationY)
        }
        val up = shape(0f,-10000f); val down = shape(0f,10000f)
        assertEquals(up.along,down.along);assertEquals(up.across,down.across)
        val moderate = shape(0f,60f)
        assertTrue(moderate.along > 1f && moderate.along < 1.018f,
            "a20dp pull must not saturate the authored input response")
        assertEquals(GlassPullDeformation.None,shape(0f,0f))
        assertEquals(GlassPullShape.Adaptive,GlassInteraction.Calm.pullShape,
            "navigation's relative deformation must not change card defaults")
    }

    @Test fun prolongedBarPullMatchesBothReferenceDimensions() {
        val interaction = checkNotNull(GlassTabBarStyle.Calm().barInteraction)
        val w = 834f; val h = 186f; val density = 3f; val press = 1.05f
        val targetLimit = h * press * interaction.pullLimit
        val rawY = -10000f
        val dy = rawY * glassPullTargetScale(abs(rawY), interaction.pullFollow, targetLimit)
        val shape = glassMaterialPull(w, h, h / 2, density, press, press, 0f, dy, targetLimit, interaction)
        val a = shape.angleDegrees * (kotlin.math.PI.toFloat() / 180f)
        val xx = (shape.along * cos(a) * cos(a) + shape.across * sin(a) * sin(a)) * press
        val xy = (shape.along - shape.across) * sin(a) * cos(a) * press
        val yy = (shape.along * sin(a) * sin(a) + shape.across * cos(a) * cos(a)) * press
        val width = 2 * (abs(xx) * (w / 2 - h / 2) + h / 2 * hypot(xx, xy))
        val height = 2 * (abs(xy) * (w / 2 - h / 2) + h / 2 * hypot(xy, yy))
        println("PHONE_T04_DIMENSIONS width=$width height=$height")
        // Original T04 PTS31074/600: clean central-row width833px, height206px.
        // Earlier sustained frame has205px height. Bounds include one-pixel edge uncertainty
        // and the fixture's rounded834px base width. This does not fit input speed or centre.
        assertEquals(205f, height, 1.1f, "prolonged-pull height")
        assertEquals(834f, width, 4f, "the bar must narrow as it grows taller")
    }
}

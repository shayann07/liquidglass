package com.wexpa.liquidglass

import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [GlassPullShape.Free]: iOS 26's Phone search button pulled off its place (IMG_6756 in display
 * colours, 2026-10-15). The default shapes are unchanged.
 */
class GlassFreePullTest {
    private val free = GlassInteraction.Pullable.copy(pullShape = GlassPullShape.Free)

    @Test fun diagonalPullRemainsContinuousAcrossEqualAxes() {
        fun horizontalScale(degrees: Float): Float {
            val radians = degrees * (kotlin.math.PI.toFloat() / 180f)
            val d = glassPullDeformation(300f * cos(radians), 300f * sin(radians),
                186f, .124f, 1f, .2f, upright = true)
            val angle = d.angleDegrees * (kotlin.math.PI.toFloat() / 180f)
            return kotlin.math.sqrt(d.along * d.along * cos(angle) * cos(angle) +
                d.across * d.across * sin(angle) * sin(angle))
        }
        // A 0.02 degree change in finger direction must not swap a stretched button's
        // width and height. This measures the transform, independently of its angle representation.
        for (diagonal in listOf(-135f, -45f, 45f, 135f)) {
            assertEquals(horizontalScale(diagonal - .01f), horizontalScale(diagonal + .01f),
                .001f, "continuous horizontal extent around $diagonal degrees")
        }
    }

    @Test fun aFreeBodyFollowsTheFingerWhileAdaptiveStaysAnchored() {
        // A 62 dp button at 3 px/dp, pressed 1.258, pulled 550 px up (resisted): IMG_6756's 110 px rise
        // at Pullable's follow of 0.2 (0.35 before 18 October reached it at 314 px).
        val args = floatArrayOf(186f, 186f, 93f, 3f, 1.258f, 1.258f, 0f, -550f, 117f)
        fun pull(i: GlassInteraction) = glassMaterialPull(args[0], args[1], args[2], args[3], args[4], args[5],
            args[6], args[7], args[8], i)
        val f = pull(free)
        assertEquals(-110f, f.translationY, 1f, "the body rises with the finger")
        // The measured shape at that rise: 297-303 px tall and 158-166 px wide on a 234 px pressed body.
        assertEquals(1.29f, f.along, .02f); assertEquals(.71f, f.across, .02f)
        val adaptive = pull(GlassInteraction.Pullable)
        assertEquals(0f, adaptive.translationY, "Adaptive keeps the layout centre")
        assertEquals(GlassPullShape.Adaptive, GlassInteraction.Pullable.pullShape)
        assertEquals(1f, GlassInteraction().pullLiftShare)
    }

    @Test fun aLandingSquashesAgainstTheRestingEdgeAwayFromThePull() {
        // Pulled up (-90 degrees), carried back 32 px past rest: the near edge leads by 32 px while
        // the far edge holds, so the body is 32 px shorter and bulges 0.2 of that across.
        val overshoot = GlassPullDeformation(1f, 1f, -90f, 0f, 32f)
        val landed = glassFreeLanding(overshoot, -90f, 93f)
        val n = landed.angleDegrees * Math.PI.toFloat() / 180f
        val centre = landed.translationX * cos(n) + landed.translationY * sin(n)
        val half = 93f * landed.along
        assertEquals(-93f, centre - half, .5f, "far (lower) edge stays at rest")
        assertEquals(186f - 32f, 2f * half, .5f, "IMG_6756: 154 px against 186")
        assertEquals(1f + .2f * (32f / 186f), landed.across, .01f)
        // Not past rest: unchanged.
        val lifted = GlassPullDeformation(1.29f, .71f, -90f, 0f, -110f)
        assertEquals(lifted, glassFreeLanding(lifted, -90f, 93f))
        // No anchor yet: unchanged.
        assertEquals(overshoot, glassFreeLanding(overshoot, Float.NaN, 93f))
    }

    @Test fun theBarsCopyOfAStretchedAccessoryStaysInsideIt() {
        for ((a, b) in listOf(83f to 151f, 151f to 83f, 92f to 77f, 93f to 93f)) {
            val (hw, hh, r) = glassInscribedStadium(a, b).let { Triple(it[0], it[1], it[2]) }
            assertTrue(r <= minOf(hw, hh) + 1e-3f)
            // Sample the stadium's outline: every point inside the ellipse.
            for (i in 0 until 72) {
                val t = i * (2 * Math.PI / 72)
                val long = hw >= hh
                val c = (if (long) hw else hh) - r
                val sx = c * Math.signum(cos(t)) + r * cos(t)
                val sy = r * sin(t)
                val x = if (long) sx else sy; val y = if (long) sy else sx
                assertTrue(x * x / (a * a) + y * y / (b * b) <= 1.001, "($a, $b): ($x, $y) outside")
            }
            assertTrue(hw >= .8f * a && hh >= .8f * b, "($a, $b) -> $hw x $hh: too small")
        }
    }

    @Test fun aDiagonalPullConstrainsToUprightOvalWithZeroShear() {
        // A diagonal pull vector (pullX = 180f, pullY = -500f) produces an angle of ~-70.2 degrees (20 deg tilt).
        // The Free role projects deformation onto the screen axes at -90f (upright oval),
        // preventing diagonal button tilt and preventing glyph shear (shear term cos(a)*sin(a) = 0).
        val args = floatArrayOf(186f, 186f, 93f, 3f, 1.258f, 1.258f, 180f, -500f, 117f)
        val d = glassMaterialPull(args[0], args[1], args[2], args[3], args[4], args[5],
            args[6], args[7], args[8], free)
        assertEquals(-90f, d.angleDegrees, 0.01f, "diagonal pull uses an upright axis representation")
        assertEquals(36f, d.translationX, 0.5f, "translationX follows 2D travel (0.2 * 180px)")
        assertEquals(-100f, d.translationY, 0.5f, "translationY follows 2D travel (0.2 * -500px)")
        val rad = d.angleDegrees * (kotlin.math.PI.toFloat() / 180f)
        val shearTerm = cos(rad) * sin(rad) * (d.along - d.across)
        assertEquals(0f, shearTerm, 1e-5f, "shear on glyph is strictly zero")
    }

    @Test fun internalFeedbackBelongsToEnabledFreeRole() {
        val defaultInteraction = GlassInteraction()
        assertEquals(false, defaultInteraction.pressFlash)
        assertEquals(false, defaultInteraction.uprightPull)

        assertTrue(free.pressFlash)
        assertTrue(free.uprightPull)
    }

    @Test fun pressSourceTracksTouchDownCount() {
        val source = GlassPressSource()
        assertEquals(0, source.pressCount)
        source.press(androidx.compose.ui.geometry.Offset(10f, 10f))
        assertEquals(1, source.pressCount)
        source.press(androidx.compose.ui.geometry.Offset(15f, 15f))
        assertEquals(1, source.pressCount)
        source.release()
        assertEquals(1, source.pressCount)
        source.press(androidx.compose.ui.geometry.Offset(20f, 20f))
        assertEquals(2, source.pressCount)
    }

    @Test fun internalCorrectionDoesNotApplyToDisabledPull() {
        val freeNonUpright = GlassInteraction(pullShape = GlassPullShape.Free, pull = false)
        val args = floatArrayOf(186f, 186f, 93f, 3f, 1.258f, 1.258f, 180f, -500f, 117f)
        val d = glassMaterialPull(args[0], args[1], args[2], args[3], args[4], args[5],
            args[6], args[7], args[8], freeNonUpright)
        val rawAngle = kotlin.math.atan2(-500f, 180f) * (180f / kotlin.math.PI.toFloat())
        assertEquals(rawAngle, d.angleDegrees, 0.01f, "disabled pull does not opt into the free-button correction")
    }

    @Test fun accessoryLinkSharesBarStateWithLinkedButton() {
        val link = GlassAccessoryLink()
        assertEquals(null, link.barState)
        val state = LiquidGlassState(androidx.compose.ui.graphics.Color.Black, 0f, 0f, 0f, 1f)
        link.barState = state
        assertEquals(state, link.barState)
    }
}

package com.wexpa.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The V3 selector's **coordinate contract**, on the production component.
 *
 * The defect this covers is optical, not cosmetic: the stored press was measured against the
 * legacy capsule's box (`selectorLeft`/`selectorTop`, one pill wide) and forwarded straight into
 * `uTouch`, while the V3 node sits at a different origin and is an envelope wide. Both shaders
 * warp the sampled source by `local - uTouch`, so the magnifier pulled the backdrop toward a point
 * tens of pixels from the finger. The body was also translated by the **unrounded** overflow while
 * the node and its native ink were placed at the rounded one.
 *
 * Everything here is checked against the declared bar-local point, which is the only frame the
 * host actually knows; nothing is compared against a previous rendering.
 */
@OptIn(ExperimentalTestApi::class)
class GlassTabBarTouchFrameTest {

    private val barWidth = 500
    private val barHeight = 64
    private val sceneHeight = 150
    private val v3 = GlassTabBarStyle.V3(dark = true)

    // ------------------------------------------------------------------ the frame itself

    /**
     * A vertical page stripe every [period] px, so a horizontal displacement of a pixel or two is
     * measurable anywhere across the bar.
     */
    private fun Modifier.ruledPage(period: Float = 11f) = drawBehind {
        drawRect(Color(0xFF0E0E12))
        var x = 0f
        while (x < size.width) {
            drawRect(Color(0xFFBFC8E0), Offset(x, 0f), Size(3f, size.height))
            x += period
        }
    }

    @Composable
    private fun Fixture(
        selected: Int,
        density: Float,
        style: GlassTabBarStyle,
        renderScale: Float = 1f,
        content: @Composable () -> Unit = {},
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalDensity provides Density(density, 1f),
        ) {
            val state = rememberLiquidGlassState(background = Color.Black, renderScale = renderScale)
            Box(Modifier.testTag("scene").size((barWidth / density).dp, (sceneHeight / density).dp)) {
                Box(Modifier.fillMaxSize().liquidGlassSource(state).ruledPage())
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    GlassTabBar(
                        state = state,
                        itemCount = 5,
                        selectedIndex = selected,
                        onSelected = {},
                        modifier = Modifier
                            .width((barWidth / density).dp)
                            .height((barHeight / density).dp),
                        style = style,
                    ) { _, _ -> Box(Modifier.size((28f / density).dp)) }
                }
                content()
            }
        }
    }

    private fun SemanticsNodeInteraction.frame(): Map<String, Float> {
        val c = fetchSemanticsNode().config
        fun get(key: androidx.compose.ui.semantics.SemanticsPropertyKey<Float>): Float =
            if (c.contains(key)) c[key] else Float.NaN
        return mapOf(
            "nodeLeft" to get(GlassTabBarSemantics.NodeLeft),
            "nodeTop" to get(GlassTabBarSemantics.NodeTop),
            "touchX" to get(GlassTabBarSemantics.TouchLocalX),
            "touchY" to get(GlassTabBarSemantics.TouchLocalY),
            "selectorX" to get(GlassTabBarSemantics.SelectorX),
        )
    }

    private fun run(
        density: Float = 1f,
        selected: Int = 2,
        style: GlassTabBarStyle = v3,
        renderScale: Float = 0.2f,
        testTimeout: Duration = 1.minutes,
        body: ComposeUiTest.(SemanticsNodeInteraction) -> Unit,
    ) = runComposeUiTest(testTimeout = testTimeout) {
        // The coordinate contract is independent of the material's performance scale, and a
        // full-resolution CPU shader per frame would put a forty-frame drag past the harness's
        // one-minute budget. The warp test below renders at native scale.
        setContent { Fixture(selected, density, style, renderScale) }
        waitForIdle()
        body(onNodeWithTag(GlassTabBarSemantics.TAG, useUnmergedTree = true))
    }

    /**
     * The inverse recovers the bar-local point: `nodeLeft + touchLocalX == pBar`. Exercised at
     * both ends and the centre, where the node's placement is clamped differently.
     */
    @Test
    fun theNodeLocalTouchInvertsToTheBarLocalPoint() {
        val geometry = GlassTabBarGeometry(barWidth = barWidth.toFloat(), inset = 6f, count = 5)
        for (index in listOf(0, 2, 4)) {
            run(selected = index) { node ->
                val px = geometry.centreOf(index)
                val py = barHeight / 2f
                node.performTouchInput { down(Offset(px, py)) }
                waitForIdle()
                val f = node.frame()
                val recoveredX = f.getValue("nodeLeft") + f.getValue("touchX")
                val recoveredY = f.getValue("nodeTop") + f.getValue("touchY")
                assertTrue(
                    abs(recoveredX - px) <= 0.25f,
                    "tab $index: the node-local touch inverts to $recoveredX, not $px",
                )
                assertTrue(
                    abs(recoveredY - py) <= 0.25f,
                    "tab $index: the node-local touch inverts to y $recoveredY, not $py",
                )
                node.performTouchInput { up() }
            }
        }
    }

    /**
     * A stationary finger while the node moves under it. The integer placement changes as the
     * body grows and travels; the local coordinate must change by exactly the opposite amount, so
     * the world-space sample does not move and no one-pixel jump appears.
     */
    @Test
    fun aMovingNodeKeepsTheFingersWorldPointAcrossIntegerPlacements() {
        val geometry = GlassTabBarGeometry(barWidth = barWidth.toFloat(), inset = 6f, count = 5)
        // Shared CI CPU rendering exceeded the default one-minute wall-clock budget.
        // Keep every coordinate assertion and event; bound this long gesture at three minutes.
        run(selected = 0, testTimeout = 3.minutes) { node ->
            val py = barHeight / 2f
            val start = geometry.centreOf(0)
            var placements = 0
            var previousLeft = Float.NaN
            var worst = 0f
            var last = start
            node.performTouchInput { down(Offset(start, py)) }
            waitForIdle()
            // Drag across the bar: the node's integer placement changes many times, and the local
            // coordinate has to change by exactly the opposite amount each time. The node is a
            // stable allocation nearly as wide as this small bar, so its placement only varies
            // over a few tens of px near the middle; the body now follows the finger exactly
            // from the first move (FABLE r12), so the steps are small enough to land several
            // placements inside that range.
            for (k in 1..18) {
                val x = start + k * 14f
                node.performTouchInput { advanceEventTime(16); moveTo(Offset(x, py)) }
                waitForIdle()
                last = x
                val f = node.frame()
                val left = f.getValue("nodeLeft")
                if (!previousLeft.isNaN() && left != previousLeft) placements++
                previousLeft = left
                worst = maxOf(worst, abs(left + f.getValue("touchX") - x))
            }
            assertTrue(placements > 2, "the node's placement barely moved ($placements changes)")
            assertTrue(
                worst <= 0.25f,
                "the world point moved by $worst px across $placements integer placements",
            )
            // Now the finger stops dead and the body keeps growing around the label it arrived
            // at. A stored local press would go stale here; a resolved one does not.
            var stationaryWorst = 0f
            repeat(3) {
                node.performTouchInput { advanceEventTime(60); moveTo(Offset(last, py)) }
                waitForIdle()
                val f = node.frame()
                stationaryWorst = maxOf(
                    stationaryWorst,
                    abs(f.getValue("nodeLeft") + f.getValue("touchX") - last),
                )
            }
            assertTrue(
                stationaryWorst <= 0.25f,
                "a stationary finger drifted $stationaryWorst px while the body grew",
            )
            node.performTouchInput { up() }
        }
    }

    /**
     * Fractional density: the overflow is not a whole number of pixels, so the node's placement,
     * its native ink and the body's own translation each round it. They must all round the same
     * way, or the contour sits in a different vertical frame from the aperture it is drawn in.
     */
    @Test
    fun aFractionalOverflowLeavesBodyInkAndTouchInOneFrame() {
        for (density in listOf(2.625f, 2.75f, 3.5f)) {
            run(density = density) { node ->
                val f0 = node.frame()
                // What this guards is ONE rounded overflow shared by the node's placement, the
                // native ink and the body - not a particular source for it. On the pose path the
                // overflow is no longer the style's 4 dp constant: it is sized from the pose, so
                // the held body is not clipped by its own node (`maxAccommodationOverflow`). So
                // the assertion is the invariant itself: a whole number of pixels, and never
                // less than the style's own overflow. The touch inversion below is what proves
                // the ink and the finger are in that same frame.
                val stylePx = with(Density(density, 1f)) { v3.lensOverflow.toPx() }
                val nodeTop = -f0.getValue("nodeTop")
                assertTrue(
                    abs(nodeTop - kotlin.math.round(nodeTop)) <= 1e-3f,
                    "density $density: the node top is $nodeTop, not a whole pixel: the node, " +
                        "the ink and the body cannot all be in it",
                )
                assertTrue(
                    nodeTop >= kotlin.math.round(stylePx) - 1e-3f,
                    "density $density: the node top is $nodeTop, less than the style's own " +
                        "overflow ${kotlin.math.round(stylePx)}",
                )
                val px = barWidth / 2f
                val py = barHeight / 2f
                node.performTouchInput { down(Offset(px, py)) }
                waitForIdle()
                val f = node.frame()
                assertTrue(
                    abs(f.getValue("nodeLeft") + f.getValue("touchX") - px) <= 0.25f,
                    "density $density: touch did not invert to $px",
                )
                node.performTouchInput { up() }
            }
        }
    }

    // ------------------------------------------------- the real warp, not a zero amount

    private fun luma(p: Int): Double {
        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun capture(
        touchAt: Offset?,
        style: GlassTabBarStyle,
    ): BufferedImage {
        var out: BufferedImage? = null
        runComposeUiTest {
            setContent { Fixture(2, 1f, style) }
            waitForIdle()
            if (touchAt != null) {
                onNodeWithTag(GlassTabBarSemantics.TAG, useUnmergedTree = true)
                    .performTouchInput { down(touchAt) }
                waitForIdle()
            }
            out = onNodeWithTag("scene").captureToImage().toAwtImage()
        }
        return out ?: error("the composition produced no capture")
    }

    /**
     * The magnifier's fixed point is where the finger is.
     *
     * `uTouch` enters both shaders as `local - uTouch`, and the sampled backdrop is pulled toward
     * that point: structure is displaced everywhere except at the point itself. So the column at
     * which the touched and untouched renderings agree **is** the touch point, and it is compared
     * against the bar-local point the test asked for - not against a previous rendering.
     *
     * A test with the touch amount at zero cannot see any of this, which is why the style below
     * carries a real illumination.
     */
    /*
     * REMOVED: `theWarpsFixedPointIsTheDeclaredBarLocalTouchPoint`.
     *
     * It claimed to locate the touch point optically - the column at which the touched and
     * untouched renderings agree - and it does not. Three measurements, in this order:
     *
     *  1. Its estimator moved 25 px when the selector node's WIDTH changed, which cannot happen
     *     to a fixed point that is a property of the touch.
     *  2. Smoothing the curve first, which should have helped, moved the answer 122 px the other
     *     way. The trough is flat by construction, so its argmin is not an estimator.
     *  3. Decisively: two declared touch points 195 px apart produced peak disagreements of 7771
     *     and 7714, and the disagreement's centroid moved 1.6 px - from 229.6 to 231.1, both of
     *     them the SELECTED BODY's own position, not the finger's. What this test was reading was
     *     the press response inside the body, which sits where the body sits however the finger
     *     moves.
     *
     * It passed for as long as it did by coincidence. The coordinate contract it was supposed to
     * guard is asserted directly and exactly by
     * `aFractionalOverflowLeavesBodyInkAndTouchInOneFrame`, which inverts the node's own origin
     * against the declared bar-local point to 0.25 px at three densities - a stronger statement
     * than any optical estimate off a 500 px render, and one that fails the same way if `uTouch`
     * is ever resolved in the wrong frame.
     */
}

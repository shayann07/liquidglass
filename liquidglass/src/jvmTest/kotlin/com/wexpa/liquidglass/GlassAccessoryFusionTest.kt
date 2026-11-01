package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

/** The bar/search fusion from the 13 October video2 comparison (review/atlas/reference-replay-2026-10-13). */
@OptIn(ExperimentalTestApi::class)
class GlassAccessoryFusionTest {
    private fun forward(pull: GlassPullDeformation, px: Float, py: Float, pivot: Offset, q: Offset): Offset {
        // withGlassMaterialPull: translate, then R(a) S(along, across) R(-a), then press, about pivot.
        var x = (q.x - pivot.x) * px
        var y = (q.y - pivot.y) * py
        val a = pull.angleDegrees * Math.PI / 180.0
        val c = cos(a).toFloat(); val s = sin(a).toFloat()
        var u = c * x + s * y
        var v = -s * x + c * y
        u *= pull.along; v *= pull.across
        x = c * u - s * v; y = s * u + c * v
        return Offset(x + pivot.x + pull.translationX, y + pivot.y + pull.translationY)
    }

    @Test fun inverseUndoesTheMaterialTransform() {
        val pull = GlassPullDeformation(along = 1.08f, across = .93f, angleDegrees = 27f,
            translationX = 6f, translationY = -3f)
        val pivot = Offset(417f, 93f)
        for (q in listOf(Offset(0f, 0f), Offset(900f, 93f), Offset(950f, 120f), Offset(-40f, -20f))) {
            val back = glassMaterialPullInverse(pull, 1.05f, 1.04f, pivot, forward(pull, 1.05f, 1.04f, pivot, q))
            assertEquals(q.x, back.x, 1e-2f); assertEquals(q.y, back.y, 1e-2f)
        }
    }

    @Test fun anAnchoredFuseStaysWhereItWasLaidOutWhileTheMaterialGrows() {
        val density = Density(3f)
        val size = Size(834f, 186f)
        val fuse = GlassFuse(DpRect(286.dp, 0.dp, 348.dp, 62.dp), 31.dp, 6.dp, anchored = true)
        val pull = GlassPullDeformation(1f, 1f, 0f, 0f, 0f)
        val laid = fuse.toPx(size, density)
        val material = fuse.toMaterialPx(size, density, pull, 1.05f, 1.05f)
        // Drawn back through the 1.05 press about the centre, the rect is where it was laid out.
        assertEquals(laid[0], material[0] * 1.05f, 1e-2f)
        assertEquals(laid[2], material[2] * 1.05f, 1e-2f)
        assertEquals(laid[4], material[4] * 1.05f, 1e-2f)
        // A moving fuse is unchanged.
        val moving = GlassFuse(DpRect(286.dp, 0.dp, 348.dp, 62.dp), 31.dp, 6.dp)
        assertContentEquals(moving.toPx(size, density), moving.toMaterialPx(size, density, pull, 1.05f, 1.05f))
    }

    @Test fun theBarBridgesToItsAccessoryOnlyWhileHeld() = runComposeUiTest(testTimeout = 6.minutes) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val state = rememberLiquidGlassState(Color.Black, renderScale = .25f)
                Box(Modifier.size(400.dp, 160.dp).testTag("scene")) {
                    Box(Modifier.fillMaxSize().background(Color.Black).liquidGlassSource(state))
                    GlassTabBar(state, 3, 0, {}, Modifier.offset(20.dp, 49.dp).width(278.dp),
                        style = GlassTabBarStyle.Calm().copy(height = 62.dp),
                        trailingAccessory = GlassTabBarAccessory(62.dp, gap = 8.dp),
                    ) { _, _ -> Box(Modifier.size(20.dp).background(Color.Gray)) }
                    Box(Modifier.offset(306.dp, 49.dp).size(62.dp)
                        .liquidGlass(state, CircleShape, GlassTabBarStyle.Calm().bar))
                }
            }
        }
        waitForIdle()
        // The middle of the gap, on the bar's centre line.
        fun gapLevel(image: BufferedImage): Int = (image.getRGB(302, 80) shr 8) and 0xFF
        val rest = gapLevel(onNodeWithTag("scene").captureToImage().toAwtImage())
        assertTrue(rest < 8, "bar and accessory fused at rest: $rest")
        val node = onNodeWithTag(GlassTabBarSemantics.TAG, useUnmergedTree = true)
        node.performTouchInput { down(Offset(46f, 31f)); advanceEventTime(800); moveTo(Offset(46f, 31f)) }
        waitForIdle()
        val held = gapLevel(onNodeWithTag("scene").captureToImage().toAwtImage())
        println("ACCESSORY_FUSION rest=$rest held=$held")
        assertTrue(held > 30, "no neck while held: $held")
        node.performTouchInput { up() }
        waitForIdle()
        assertTrue(gapLevel(onNodeWithTag("scene").captureToImage().toAwtImage()) < 8, "neck stayed after release")
    }

    /** pressReach (2026-10-14): holding the first tab grows that side further; the far edge stays
     * where the ordinary press puts it. Bar edges are found on the bar's centre row over black.
     * Calm no longer reaches (pass 13: its lens, centred on the pressed tab, does), so the
     * reaching bar here is Calm with the opt-in .03. */
    @Test fun aHeldBarReachesTowardTheFingerAndKeepsItsFarEdge() = runComposeUiTest(testTimeout = 6.minutes) {
        var reach by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val state = rememberLiquidGlassState(Color.Black, renderScale = .25f)
                val calm = GlassTabBarStyle.Calm().copy(height = 62.dp)
                val style = calm.copy(barInteraction = calm.barInteraction?.copy(pressReach = if (reach) .03f else 0f))
                Box(Modifier.size(400.dp, 160.dp).testTag("scene")) {
                    Box(Modifier.fillMaxSize().background(Color.Black).liquidGlassSource(state))
                    GlassTabBar(state, 3, 0, {}, Modifier.offset(60.dp, 49.dp).width(278.dp), style = style,
                    ) { _, _ -> Box(Modifier.size(20.dp).background(Color.Gray)) }
                }
            }
        }
        fun heldEdges(): Pair<Int, Int> {
            val node = onNodeWithTag(GlassTabBarSemantics.TAG, useUnmergedTree = true)
            node.performTouchInput { down(Offset(46f, 31f)); advanceEventTime(800); moveTo(Offset(46f, 31f)) }
            waitForIdle()
            val image = onNodeWithTag("scene").captureToImage().toAwtImage()
            node.performTouchInput { up() }
            waitForIdle()
            fun lit(x: Int) = ((image.getRGB(x, 80) shr 8) and 0xFF) > 12
            val left = (0 until image.width).first { lit(it) }
            val right = (image.width - 1 downTo 0).first { lit(it) }
            return left to right
        }
        waitForIdle()
        val (reachLeft, reachRight) = heldEdges()
        reach = false
        waitForIdle()
        val (plainLeft, plainRight) = heldEdges()
        println("PRESS_REACH reach=$reachLeft..$reachRight plain=$plainLeft..$plainRight")
        assertTrue(plainLeft - reachLeft >= 3, "finger side did not reach: $reachLeft vs $plainLeft")
        assertTrue(kotlin.math.abs(reachRight - plainRight) <= 1, "far edge moved: $reachRight vs $plainRight")
    }

    /** releaseStiffness (2026-10-14): iOS's bar is back about 0.12 s after lift, so its neck to
     * the search button parts promptly; the historical Calm release still holds it at 150 ms. */
    @Test fun theNeckPartsPromptlyAfterLift() = runComposeUiTest(testTimeout = 6.minutes) {
        var fast by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val state = rememberLiquidGlassState(Color.Black, renderScale = .25f)
                val calm = GlassTabBarStyle.Calm().copy(height = 62.dp)
                val style = if (fast) calm else calm.copy(barInteraction = calm.barInteraction?.copy(releaseStiffness = 0f))
                Box(Modifier.size(400.dp, 160.dp).testTag("scene")) {
                    Box(Modifier.fillMaxSize().background(Color.Black).liquidGlassSource(state))
                    GlassTabBar(state, 3, 0, {}, Modifier.offset(20.dp, 49.dp).width(278.dp), style = style,
                        trailingAccessory = GlassTabBarAccessory(62.dp, gap = 8.dp),
                    ) { _, _ -> Box(Modifier.size(20.dp).background(Color.Gray)) }
                    Box(Modifier.offset(306.dp, 49.dp).size(62.dp).liquidGlass(state, CircleShape, calm.bar))
                }
            }
        }
        fun neckAfterLift(): Int {
            waitForIdle()
            val node = onNodeWithTag(GlassTabBarSemantics.TAG, useUnmergedTree = true)
            node.performTouchInput { down(Offset(46f, 31f)); advanceEventTime(800); moveTo(Offset(46f, 31f)) }
            waitForIdle()
            mainClock.autoAdvance = false
            node.performTouchInput { up() }
            mainClock.advanceTimeBy(150)
            val level = (onNodeWithTag("scene").captureToImage().toAwtImage().getRGB(302, 80) shr 8) and 0xFF
            mainClock.autoAdvance = true
            waitForIdle()
            return level
        }
        val prompt = neckAfterLift()
        fast = false
        val slow = neckAfterLift()
        println("NECK_RELEASE prompt=$prompt slow=$slow")
        assertTrue(prompt < 12, "neck still joined 150 ms after lift: $prompt")
        // Without releaseStiffness the neck is still joined (> 12). Since #106 the still-released lens
        // fades within ~70 ms, so the control reads ~17 where it read > 20.
        assertTrue(slow > 12, "historical release changed: $slow")
    }
}

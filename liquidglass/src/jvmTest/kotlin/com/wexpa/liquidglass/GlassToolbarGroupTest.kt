package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** [GlassToolbarGroup]: one capsule behind a row of actions, at least 44 dp tall, with the authored item padding. */
@OptIn(ExperimentalTestApi::class)
class GlassToolbarGroupTest {
    @Test fun theGroupIsOneCapsuleAroundItsActions() = runComposeUiTest(testTimeout = 4.minutes) {
        var density = 1f
        setContent {
            density = LocalDensity.current.density
            LiquidGlassTheme(dark = true, background = Color.Black) {
                Box(Modifier.size(300.dp, 120.dp).background(Color.DarkGray)) {
                    GlassToolbarGroup(Modifier.testTag("group")) {
                        repeat(3) { Box(Modifier.size(32.dp)) }
                    }
                }
            }
        }
        val size = onNodeWithTag("group").fetchSemanticsNode().size
        // Three 32 dp actions and the 4 dp ends; 44 dp tall, since the actions are shorter than that.
        assertEquals((3 * 32 + 2 * 4) * density, size.width.toFloat(), 1f, "width")
        assertEquals(44 * density, size.height.toFloat(), 1f, "height")
    }

    @Test fun aTallActionGrowsTheGroupAndAMinHeightRaisesIt() = runComposeUiTest(testTimeout = 4.minutes) {
        var density = 1f
        setContent {
            density = LocalDensity.current.density
            LiquidGlassTheme(dark = true, background = Color.Black) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassToolbarGroup(Modifier.testTag("tall")) { Box(Modifier.size(32.dp, 60.dp)) }
                    GlassToolbarGroup(Modifier.testTag("min"), minHeight = 52.dp) { Box(Modifier.size(32.dp)) }
                }
            }
        }
        assertEquals(60 * density, onNodeWithTag("tall").fetchSemanticsNode().size.height.toFloat(), 1f)
        assertEquals(52 * density, onNodeWithTag("min").fetchSemanticsNode().size.height.toFloat(), 1f)
    }

    @Test fun theCapsuleDrawsGlassOverItsBackdrop() = runComposeUiTest(testTimeout = 4.minutes) {
        setContent {
            LiquidGlassTheme(dark = true, background = Color.Black) {
                Box(Modifier.size(200.dp, 100.dp).background(Color.Black), contentAlignment = Alignment.Center) {
                    GlassToolbarGroup(Modifier.testTag("group")) { Box(Modifier.size(80.dp, 32.dp)) }
                }
            }
        }
        waitForIdle()
        val map = onRoot().captureToImage().toPixelMap()
        val node = onNodeWithTag("group").fetchSemanticsNode()
        val inside = map[node.positionInRoot.x.toInt() + node.size.width / 2, node.positionInRoot.y.toInt() + node.size.height / 2]
        val outside = map[2, 2]
        // The measured toolbar material lifts a black page: the capsule reads lighter than the page beside it.
        assertTrue(inside.red > outside.red + 0.03f, "capsule ${inside.red} over page ${outside.red}")
    }

    /** The group draws Glass.Toolbar unless told otherwise. Toolbar and Regular share the lab material by measurement (#200:
     * `GlassStyle.toolbar` and `GlassStyle.inApp` both resolve through `towardLab`), so the two cannot be told apart by pixels;
     * the check is that the default renders exactly as an explicit Glass.Toolbar, and visibly unlike Glass.Clear. */
    @Test fun theDefaultMaterialIsTheToolbars() = runComposeUiTest(testTimeout = 4.minutes) {
        setContent {
            LiquidGlassTheme(dark = true, background = Color.Black) {
                Row(Modifier.size(420.dp, 100.dp).background(Color.Black), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    GlassToolbarGroup(Modifier.testTag("default")) { Box(Modifier.size(80.dp, 32.dp)) }
                    GlassToolbarGroup(Modifier.testTag("toolbar"), glass = Glass.Toolbar) { Box(Modifier.size(80.dp, 32.dp)) }
                    GlassToolbarGroup(Modifier.testTag("clear"), glass = Glass.Clear) { Box(Modifier.size(80.dp, 32.dp)) }
                }
            }
        }
        waitForIdle()
        val map = onRoot().captureToImage().toPixelMap()
        fun centre(tag: String): Color {
            val node = onNodeWithTag(tag).fetchSemanticsNode()
            return map[node.positionInRoot.x.toInt() + node.size.width / 2, node.positionInRoot.y.toInt() + node.size.height / 2]
        }
        fun diff(a: Color, b: Color) = kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)
        val default = centre("default")
        val toolbar = centre("toolbar")
        val clear = centre("clear")
        assertTrue(diff(default, toolbar) < 0.004f, "default $default vs explicit Toolbar $toolbar")
        // Clear is the see-through material: if the default ever drifted to it, the group would read as a different glass.
        assertTrue(diff(default, clear) > 0.01f, "default $default vs Clear $clear: the check cannot tell materials apart")
    }
}

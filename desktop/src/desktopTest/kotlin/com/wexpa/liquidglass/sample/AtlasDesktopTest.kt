package com.wexpa.liquidglass.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalTestApi::class)
class AtlasDesktopTest {
    @Test fun boundsRemainValidAtEdgesAndInTinyWindows() {
        for (viewport in listOf(IntSize(1000, 700), IntSize(320, 600), IntSize(40, 20))) {
            for (point in listOf(Offset(-1e6f, -1e6f), Offset(1e6f, 1e6f), Offset(-1e6f, 1e6f))) {
                val bounded = boundedLens(point, viewport, 104f, 10f)
                assertTrue(bounded.x in 0f..viewport.width.toFloat())
                assertTrue(bounded.y in 0f..viewport.height.toFloat())
            }
        }
        assertEquals(Offset(114f, 114f), boundedLens(Offset.Zero, IntSize(1000, 700), 104f, 10f))
    }

    @Test fun actualAtlasControlsAndExtremeDragsRecoverAcrossAllScenes() = runComposeUiTest(testTimeout = 6.minutes) {
        val report = File("build/reports/atlas").apply { mkdirs() }
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(800.dp, 600.dp)) { LoupeScreen() }
            }
        }
        waitForIdle()
        fun capture(name: String) {
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(report, "$name.png"))
        }
        for (scene in 0..2) {
            onNodeWithTag("scene-$scene").performClick()
            waitForIdle()
            capture("scene-$scene")
        }
        val canvas = onNodeWithTag("atlas-canvas").fetchSemanticsNode().boundsInRoot
        val lens = onNodeWithTag("atlas-lens")
        lens.performTouchInput { down(center) }
        for ((direction, point) in listOf("right" to Offset(3000f, 0f), "left" to Offset(-3000f, 0f),
            "top" to Offset(0f, -3000f), "bottom" to Offset(0f, 3000f), "diagonal" to Offset(3000f, 3000f))) {
            lens.performTouchInput { moveTo(point, 150) }
            waitForIdle()
            val bounds = lens.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= canvas.left && bounds.right <= canvas.right, "$direction horizontal escape")
            assertTrue(bounds.top >= canvas.top && bounds.bottom <= canvas.bottom, "$direction vertical escape")
            capture("extreme-$direction")
        }
        lens.performTouchInput { up() }
        waitForIdle()
        onNodeWithTag("zoom-in").performClick()
        onNodeWithTag("atlas-lens").assertContentDescriptionEquals("Magnifier, 135 percent")
        onNodeWithTag("lens-toggle").performClick()
        onNodeWithTag("atlas-lens").assertDoesNotExist()
        onNodeWithTag("lens-toggle").performClick()
        onNodeWithTag("atlas-lens").assertExists()
        onNodeWithTag("motion").performClick()
        onNodeWithText("Motion reduced").assertExists()
        capture("recovered")
    }
}

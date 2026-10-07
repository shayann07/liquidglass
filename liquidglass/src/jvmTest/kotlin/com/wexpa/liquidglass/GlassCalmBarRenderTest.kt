package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

/** Actual navigation pointer path and production material; no fitted iOS pixel target. */
@OptIn(ExperimentalTestApi::class)
class GlassCalmBarRenderTest {
    @Test fun wholeBarSurvivesFullScreenPullsWithoutMovingItsLayout() = runComposeUiTest(testTimeout = 6.minutes) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                // Match the gesture suite's source scale: full-size layout/coverage and actual
                // shader optics, with less CPU backdrop sampling per simulated input frame.
                val state = rememberLiquidGlassState(Color(0xff0a1829), renderScale = .25f)
                var selected by remember { mutableIntStateOf(1) }
                Box(Modifier.size(480.dp, 220.dp).testTag("scene")) {
                    Row(Modifier.fillMaxSize().liquidGlassSource(state)) {
                        repeat(24) { i -> Box(Modifier.width(20.dp).fillMaxHeight()
                            .background(if (i % 2 == 0) Color(0xff16334c) else Color(0xff28566d))) }
                    }
                    GlassTabBar(state, 3, selected, { selected = it },
                        Modifier.align(Alignment.Center).width(400.dp).testTag("bar-layout"),
                        style = GlassTabBarStyle.Calm()) { index, _ ->
                        Box(Modifier.size((12 + index * 4).dp).background(Color(0xffbbe5ee)))
                    }
                }
            }
        }
        waitForIdle()
        val directory = File("build/reports/atlas").apply { mkdirs() }
        val node = onNodeWithTag(GlassTabBarSemantics.TAG, useUnmergedTree = true)
        val initial = onNodeWithTag("bar-layout").fetchSemanticsNode().boundsInRoot
        fun capture(name: String): BufferedImage {
            val image=onNodeWithTag("scene").captureToImage().toAwtImage()
            ImageIO.write(image, "png", File(directory, "bar-$name.png"))
            assertEquals(initial, onNodeWithTag("bar-layout").fetchSemanticsNode().boundsInRoot,
                "drag moved the bar's layout or hit target")
            assertEquals(true, node.fetchSemanticsNode().config[GlassTabBarSemantics.SelectorValid])
            return image
        }
        fun ordinaryInkBounds(image: BufferedImage, region: IntRange): List<Int> {
            val pixels=buildList {
                for(y in 0 until image.height) for(x in region) {
                    val color=image.getRGB(x,y)
                    if((color ushr 16 and 255)>=180 && (color ushr 8 and 255)>=215 &&
                        (color and 255)>=225) add(x to y)
                }
            }
            assertTrue(pixels.isNotEmpty(),"ordinary ink was lost")
            return listOf(pixels.minOf {it.first},pixels.minOf {it.second},
                pixels.maxOf {it.first},pixels.maxOf {it.second})
        }
        capture("rest")
        node.performTouchInput { down(Offset(200f,31f)); advanceEventTime(800); moveTo(Offset(200f,31f)) }
        waitForIdle();val held=capture("held")
        for ((label, position) in listOf(
            "top" to Offset(200f,-2000f), "bottom" to Offset(200f,2000f),
            "left" to Offset(-2000f,31f), "right" to Offset(2000f,31f),
            "corner" to Offset(-2000f,-2000f),
        )) {
            node.performTouchInput { advanceEventTime(250);moveTo(position) }
            waitForIdle();val pulled=capture(label)
            if(label=="top") for(region in listOf(80..140,335..400)) {
                assertEquals(ordinaryInkBounds(held,region),ordinaryInkBounds(pulled,region),
                    "material bias moved ordinary foreground pixels")
            }
        }
        val endWidth = node.fetchSemanticsNode().config[GlassTabBarSemantics.BodyWidth]
        mainClock.autoAdvance = false
        // Already at the left end, then keep dragging farther outside it. Observe in-flight
        // frames, not just a waitForIdle snapshot after the erroneous deformation has recovered.
        repeat(12) { i ->
            node.performTouchInput {
                advanceEventTime(16)
                moveTo(Offset(if (i % 2 == 0) -4000f else -2000f, -2000f))
            }
            mainClock.advanceTimeBy(16)
            waitForIdle()
            assertEquals(endWidth, node.fetchSemanticsNode().config[GlassTabBarSemantics.BodyWidth], .5f,
                "out-of-range pointer motion stretched the stationary selector")
        }
        capture("outward-motion")
        mainClock.autoAdvance = true
        node.performTouchInput { up() };waitForIdle();capture("released")
        assertEquals(false,node.fetchSemanticsNode().config[GlassTabBarSemantics.Held])
    }
}

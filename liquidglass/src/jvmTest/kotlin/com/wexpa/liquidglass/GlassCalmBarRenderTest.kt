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
                        Box(Modifier.size((32 + index * 4).dp).background(Color(0xffbbe5ee)))
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
                val h=ordinaryInkBounds(held,region)
                val p=ordinaryInkBounds(pulled,region)
                val heldCentre=(h[1]+h[3]+1)/2f
                val pulledCentre=(p[1]+p[3]+1)/2f
                // Original T04 Contacts ordinary hold -> extreme: centre262.5 ->251px
                // at density3. Same-gesture bar geometry independently predicts this motion.
                assertEquals(-11.5f/3f,pulledCentre-heldCentre,.8f,
                    "visible navigation ink must follow bounded bar drawing, not stay anchored")
                // Full-density glyph dimensions are checked separately below; a 32px
                // threshold box cannot resolve a ~1.5px height change as a precise ratio.
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

    @Test fun ordinaryGlyphMatchesOriginalHeldAndExtremeEnvelope() = runComposeUiTest(testTimeout = 6.minutes) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f)) {
                val state = rememberLiquidGlassState(Color.Black, renderScale = .25f)
                Box(Modifier.size(350.dp,140.dp).testTag("reference-scene")) {
                    Box(Modifier.fillMaxSize().background(Color.Black).liquidGlassSource(state))
                    GlassTabBar(state,3,0,{},Modifier.align(Alignment.Center).width(278.dp),
                        style=GlassTabBarStyle.Calm()) { _, _ ->
                        Box(Modifier.offset(y=(-20.5f/3f).dp).size(23.dp).background(Color.White))
                    }
                }
            }
        }
        waitForIdle()
        val node=onNodeWithTag(GlassTabBarSemantics.TAG,useUnmergedTree=true)
        fun glyph(label: String): Pair<Float,Int> {
            val image=onNodeWithTag("reference-scene").captureToImage().toAwtImage()
            val directory=File("build/reports/atlas").apply {mkdirs()}
            ImageIO.write(image,"png",File(directory,"nav-ink-$label.png"))
            val ys=buildList {
                for(y in 0 until image.height) for(x in 475..575) {
                    val c=image.getRGB(x,y)
                    if((c ushr 16 and 255)>=200 && (c ushr 8 and 255)>=200 && (c and 255)>=200) add(y)
                }
            }
            assertTrue(ys.isNotEmpty())
            return (ys.min()+ys.max()+1)/2f to (ys.max()-ys.min()+1)
        }
        val rest=glyph("rest")
        node.performTouchInput {down(Offset(139f,93f));advanceEventTime(800);moveTo(Offset(139f,93f))}
        waitForIdle();val held=glyph("held")
        node.performTouchInput {advanceEventTime(250);moveTo(Offset(139f,-6000f))}
        waitForIdle();val extreme=glyph("extreme")
        println("NAV_INK rest=$rest held=$held extreme=$extreme")
        // Full-density originals:69px rest,73px ordinary hold,76px extreme.
        // 1.5px covers the independently recorded 1.29px threshold/edge residual.
        assertEquals(69f,rest.second.toFloat(),1.5f)
        assertEquals(73f,held.second.toFloat(),1.5f)
        assertEquals(76f,extreme.second.toFloat(),1.5f)
        assertEquals(-11.5f,extreme.first-held.first,1.5f)
        node.performTouchInput {up()};waitForIdle()
        assertEquals(rest,glyph("released"),"visible ink must recover exactly after release")
    }
}

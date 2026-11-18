package com.wexpa.liquidglass

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The scroll edge effect against the measured top edge of iOS 27 Safari (#207, `Scr-safari-scroll-{dark,light}`),
 * and its behaviour (authored expectations): the dark tint profile within 5 levels rms of the measured median
 * profile, no tint in light, edge widths within 1 px of the measured +-10 row table, intensity zero drawing the
 * content untouched, the state overloads, and no recomposition while scrolling. Everything renders at 3 px per dp
 * on the desktop Skia backend.
 */
@OptIn(ExperimentalTestApi::class)
class GlassScrollEdgeTest {
    private val red = Color(255, 44, 85)

    private fun scene(widthPx: Int = 90, heightPx: Int = 420, block: SkikoComposeUiTest.() -> Unit) =
        runSkikoComposeUiTest(size = Size(widthPx.toFloat(), heightPx.toFloat()), density = Density(3f), testTimeout = 5.minutes) { block() }

    private fun SkikoComposeUiTest.frame(): PixelMap {
        waitForIdle()
        return onRoot().captureToImage().toPixelMap()
    }

    private fun PixelMap.channel(x: Int, y: Int, c: Int): Int {
        val v = this[x, y]
        return Math.round((when (c) { 0 -> v.red; 1 -> v.green; else -> v.blue }) * 255f)
    }

    // ---- the measured tint profile, dark ---------------------------------------------------------

    /** Measured median alpha by row (px from the screen top), dark take, 427 frames, 207-measured.md section 1. */
    private val measuredAlpha = listOf(0 to .416, 8 to .413, 16 to .413, 24 to .407, 32 to .396, 40 to .378, 48 to .348,
        56 to .296, 64 to .232, 72 to .165, 80 to .084, 88 to .035, 96 to .009, 104 to 0.0)

    @Test fun theDarkTintMatchesTheMeasuredProfileWithinFiveLevelsRms() = scene {
        setContent { Box(Modifier.fillMaxSize().glassScrollEdge(Color.Black, inverted = true, topIntensity = { 1f }).background(red)) }
        val pixels = frame()
        // out = ref + alpha (124 - ref): read alpha from the red channel (255), a 131-level swing.
        var sum = 0.0
        for ((y, a) in measuredAlpha) {
            val ours = (255 - pixels.channel(45, y, 0)) / 131.0
            val err = (ours - a) * 131.0
            sum += err * err
            println("EDGE row $y: alpha ours %.3f, measured %.3f".format(ours, a))
        }
        val rms = sqrt(sum / measuredAlpha.size)
        println("EDGE dark tint profile rms %.2f levels (gate 5)".format(rms))
        assertTrue(rms <= 5.0, "dark tint profile rms $rms levels")
    }

    @Test fun theTintBlendsTowardGrey124ForEveryHue() = scene {
        val hues = listOf(Color(255, 44, 85), Color(47, 210, 88), Color(191, 91, 242), Color(100, 211, 255))
        var hue by mutableStateOf(hues[0])
        setContent { Box(Modifier.fillMaxSize().glassScrollEdge(Color.Black, inverted = true, topIntensity = { 1f }).background(hue)) }
        for (h in hues) {
            hue = h
            val p = frame()
            for (ch in 0..2) {
                val ref = p.channel(45, 150, ch)
                if (abs(ref - 124) < 40) continue   // too close to the grey to tell it from the content
                // out = ref + 0.416 (grey - ref) at the edge row: solve for the grey.
                val grey = ref + (p.channel(45, 0, ch) - ref) / 0.416
                assertEquals(124.0, grey, 8.0, "channel $ch of $h blends toward ${"%.1f".format(grey)}, not 124")
            }
        }
    }

    @Test fun lightAppearanceHasNoTint() = scene {
        setContent { Box(Modifier.fillMaxSize().glassScrollEdge(Color.White, inverted = false, topIntensity = { 1f }).background(red)) }
        val pixels = frame()
        for (y in listOf(0, 20, 40, 60, 80, 100)) for (c in 0..2) {
            val ref = pixels.channel(45, 150, c)
            assertTrue(abs(pixels.channel(45, y, c) - ref) <= 3, "light tint at row $y channel $c: ${pixels.channel(45, y, c)} against $ref")
        }
    }

    // ---- the measured blur --------------------------------------------------------------------------

    /** Measured rms edge width over +-10 rows (px at 3x) at the centre of each 10 px bin, 207-measured.md section 2. */
    private val measuredWidth = listOf(65 to 4.4, 75 to 3.9, 85 to 3.0, 95 to 1.5, 105 to 0.4)

    /** The estimator of the measurement: rms width of the vertical derivative within +-10 rows of the edge. */
    private fun edgeWidth(column: IntArray, edge: Int): Double {
        val rows = (edge - 10)..(edge + 10)
        val d = rows.map { y -> abs(column[y + 1] - column[y]).toDouble() }
        val sum = d.sum()
        val mean = rows.zip(d).sumOf { (y, w) -> (y + .5) * w } / sum
        return sqrt(rows.zip(d).sumOf { (y, w) -> ((y + .5) - mean) * ((y + .5) - mean) * w } / sum)
    }

    @Test fun theEdgesAreSmearedAsWidelyAsTheMeasuredOnes() = scene(90, 420) {
        var edge by mutableFloatStateOf(65f)
        setContent {
            Box(Modifier.fillMaxSize().glassScrollEdge(Color.White, inverted = false, topIntensity = { 1f })
                .drawBehind {
                    drawRect(Color.Black, Offset.Zero, size)
                    drawRect(Color.White, Offset(0f, edge), Size(size.width, size.height - edge))
                })
        }
        for ((y, want) in measuredWidth) {
            edge = y.toFloat()
            val p = frame()
            val column = IntArray(p.height) { p.channel(45, it, 1) }
            val got = edgeWidth(column, y)
            println("EDGE blur at row $y: width %.2f px, measured %.2f".format(got, want))
            assertEquals(want, got, 1.0, "edge width at row $y px (measured ${want} px, +-10 rows)")
        }
        // Clear of the band the edge is sharp again (0.0-0.3 measured, 0.5 for a pixel step by this estimator).
        edge = 140f
        val p = frame()
        assertTrue(edgeWidth(IntArray(p.height) { p.channel(45, it, 1) }, 140) < 1.0, "edge beyond the band is blurred")
    }

    // ---- intensity and state ------------------------------------------------------------------------

    @Test fun zeroIntensityDrawsTheContentAsIfTheModifierWereAbsent() = scene {
        var withEdge by mutableStateOf(true)
        setContent {
            val m = if (withEdge) Modifier.glassScrollEdge(Color.Black, inverted = true, topIntensity = { 0f }) else Modifier
            Box(Modifier.fillMaxSize().then(m).background(red))
        }
        val a = frame()
        withEdge = false
        val b = frame()
        for (y in 0 until a.height step 7) for (c in 0..2) assertEquals(b.channel(45, y, c), a.channel(45, y, c), "row $y channel $c")
    }

    @Test fun intensityScalesTheTint() = scene {
        var intensity by mutableFloatStateOf(1f)
        setContent { Box(Modifier.fillMaxSize().glassScrollEdge(Color.Black, inverted = true, topIntensity = { intensity }).background(red)) }
        val full = 255 - frame().channel(45, 0, 0)
        intensity = .5f
        val half = 255 - frame().channel(45, 0, 0)
        assertEquals(full / 2.0, half.toDouble(), 3.0, "half intensity")
    }

    @Test fun theTopEdgeIsZeroAtRestAndFullAfterTheRampForAScrollState() = scene(90, 420) {
        lateinit var state: ScrollState
        setContent {
            state = rememberScrollState()
            Column(Modifier.fillMaxSize().glassScrollEdge(state, Color.Black, inverted = true).verticalScroll(state)) {
                Box(Modifier.fillMaxWidth().height(400.dp).background(red))
            }
        }
        val rest = frame()
        assertEquals(255, rest.channel(45, 0, 0), "an edge at rest dims the content")
        runBlocking { state.scrollTo(72) }       // 24 dp at 3x
        val ramped = frame()
        assertTrue(255 - ramped.channel(45, 0, 0) in 50..58, "full intensity after the ramp: ${255 - ramped.channel(45, 0, 0)} (0.416 x 131 = 54)")
        runBlocking { state.scrollTo(36) }
        val half = 255 - frame().channel(45, 0, 0)
        assertEquals(27.0, half.toDouble(), 4.0, "half way up the ramp")
    }

    @Test fun theBottomEdgeIsZeroAtTheEndAndFullBeforeItForAScrollState() = scene(90, 420) {
        lateinit var state: ScrollState
        setContent {
            state = rememberScrollState()
            Column(Modifier.fillMaxSize().glassScrollEdge(state, Color.Black, edges = GlassScrollEdges.Bottom, inverted = true).verticalScroll(state)) {
                Box(Modifier.fillMaxWidth().height(400.dp).background(red))
            }
        }
        val rest = frame()
        assertTrue(255 - rest.channel(45, 419, 0) in 50..58, "bottom edge with room to scroll: ${255 - rest.channel(45, 419, 0)}")
        assertEquals(255, rest.channel(45, 0, 0), "the top edge drew though only the bottom was asked for")
        runBlocking { state.scrollTo(state.maxValue) }
        assertEquals(255, frame().channel(45, 419, 0), "the bottom edge draws at the end of the content")
    }

    @Test fun transparentContentBlursWithoutASharpCopyUnderneath() = scene(90, 420) {
        // A 2 px black line on a transparent page over a white parent, inside the top band at full intensity: mixed, the
        // line spreads into grey; stacked over a sharp copy, its centre would stay black.
        setContent {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Box(Modifier.fillMaxSize().glassScrollEdge(Color.White, inverted = false, topIntensity = { 1f })) {
                    Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(2.dp / 3f).background(Color.Black))
                }
            }
        }
        val f = frame()
        val centre = f.channel(45, (3 * 3 + 1), 0)
        assertTrue(centre > 60, "the line's centre at the edge is blurred toward the white parent, not black ($centre)")
    }

    @Test fun aReversedLazyListPutsTheEffectsOnTheirPhysicalEdges() = scene(90, 420) {
        lateinit var state: androidx.compose.foundation.lazy.LazyListState
        setContent {
            state = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize().glassScrollEdge(state, Color.Black, edges = GlassScrollEdges.Both, inverted = true),
                state, reverseLayout = true) {
                items(30) { Box(Modifier.fillMaxWidth().height(60.dp).background(red)) }
            }
        }
        val rest = frame()
        assertEquals(255, rest.channel(45, 419, 0), "the bottom (the list's start) is clear at rest")
        assertTrue(255 - rest.channel(45, 0, 0) in 50..58, "the top (more items) shows the effect")
    }

    @Test fun aLazyListIsDrivenTheSameWay() = scene(90, 420) {
        lateinit var state: androidx.compose.foundation.lazy.LazyListState
        setContent {
            state = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize().glassScrollEdge(state, Color.Black, edges = GlassScrollEdges.Both, inverted = true), state) {
                items(30) { Box(Modifier.fillMaxWidth().height(60.dp).background(red)) }
            }
        }
        val rest = frame()
        assertEquals(255, rest.channel(45, 0, 0), "top edge at rest")
        assertTrue(255 - rest.channel(45, 419, 0) in 50..58, "bottom edge with items below")
        runBlocking { state.scrollToItem(3, 30) }  // 30 px into the fourth item
        assertTrue(255 - frame().channel(45, 0, 0) in 50..58, "top edge scrolled under")
        runBlocking { state.scrollToItem(29) }
        assertEquals(255, frame().channel(45, 419, 0), "bottom edge at the end of the list")
    }

    @Test fun scrollingDoesNotRecomposeTheHost() = scene(90, 420) {
        var compositions = 0
        lateinit var state: ScrollState
        setContent {
            SideEffect { compositions++ }
            state = rememberScrollState()
            Column(Modifier.fillMaxSize().glassScrollEdge(state, Color.Black, edges = GlassScrollEdges.Both, inverted = true).verticalScroll(state)) {
                Box(Modifier.fillMaxWidth().height(1200.dp).background(red))
            }
        }
        waitForIdle()
        val before = compositions
        for (i in 1..300) { runBlocking { state.scrollTo(i * 3) }; waitForIdle() }
        assertEquals(before, compositions, "scrolling recomposed the host: the intensity must be read in the draw phase")
    }

    @Test fun theHardStyleDrawsAUniformGroundBandAndNoBlur() = scene {
        setContent {
            Box(Modifier.fillMaxSize().glassScrollEdge(Color.Black, style = GlassScrollEdgeStyle.Hard, topIntensity = { 1f }).background(red))
        }
        val p = frame()
        val a = p.channel(45, 0, 0)
        val b = p.channel(45, 80, 0)
        assertEquals(a, b, "a uniform band")
        assertEquals(255 * (1 - .9), a.toDouble(), 3.0, "the old composable's strength")
    }
}

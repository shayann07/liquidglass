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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The ink-clarity and spatial-anchoring controls of `V3-MODEL.md` section 15.3, on the real
 * [GlassTabBar] with its real source graph, layout and compositor.
 *
 * The fixture is deterministic: a fixed busy backdrop, five items, and glyphs drawn as strokes of
 * known width rather than as text, so an edge can be measured rather than eyeballed. Nothing here
 * scores a mean over a mostly empty region — each control reads the structure it is about.
 */
@OptIn(ExperimentalTestApi::class)
class GlassTabBarInkTest {

    private val barWidth = 500
    private val barHeight = 64
    private val sceneHeight = 140

    /** A busy, high-contrast page, fixed for every capture. */
    private fun Modifier.page() = drawBehind {
        drawRect(Color(0xFF101014))
        var x = 0f
        var i = 0
        while (x < size.width) {
            val w = 7f + (i % 5) * 3f
            drawRect(
                // High contrast, but nothing as bright as the ink: a page stripe that rivalled a
                // white stroke would make an edge measurement read the page instead of the label.
                color = if (i % 3 == 0) Color(0xFF8A96B4) else Color(0xFF2A3F6B),
                topLeft = Offset(x, 0f),
                size = Size(w, size.height),
            )
            x += w + 11f
            i++
        }
        var y = 6f
        while (y < size.height) {
            drawRect(Color(0x66FF9A3C), Offset(0f, y), Size(size.width, 2f))
            y += 19f
        }
    }

    /**
     * One item's ink: a thick stroke and a thin one, so both a large and a small detail are
     * scored. [bright] is the selected variant's colour.
     */
    @Composable
    private fun Glyph(bright: Boolean) {
        val colour = if (bright) Color.White else Color(0xFFDADADA)
        Box(
            Modifier.size(40.dp, 30.dp).drawBehind {
                drawRect(colour, Offset(8f, 4f), Size(6f, size.height - 8f))
                drawRect(colour, Offset(24f, 4f), Size(1f, size.height - 8f))
            },
        )
    }

    @Composable
    private fun Fixture(
        selected: Int,
        style: GlassTabBarStyle,
        renderScale: Float = 1f,
        ordinaryInk: Boolean = true,
        selectedInk: Boolean = true,
        identicalVariants: Boolean = false,
    ) {
        val state = rememberLiquidGlassState(background = Color.Black, renderScale = renderScale)
        Box(Modifier.testTag("scene").size(barWidth.dp, sceneHeight.dp)) {
            // liquidGlassSource must WRAP the drawing, or the recorded backdrop is empty and
            // every panel silently samples the ground colour instead of the page.
            Box(Modifier.fillMaxSize().liquidGlassSource(state).page())
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                GlassTabBar(
                    state = state,
                    itemCount = 5,
                    selectedIndex = selected,
                    onSelected = {},
                    modifier = Modifier.width(barWidth.dp).height(barHeight.dp),
                    style = style,
                ) { _, sel ->
                    val show = if (sel) selectedInk else ordinaryInk
                    if (show) Glyph(if (identicalVariants) true else sel)
                }
            }
        }
    }

    private fun ComposeUiTest.capture(
        selected: Int,
        style: GlassTabBarStyle,
        renderScale: Float = 1f,
        ordinaryInk: Boolean = true,
        selectedInk: Boolean = true,
        identicalVariants: Boolean = false,
    ): BufferedImage {
        setContent {
            Fixture(selected, style, renderScale, ordinaryInk, selectedInk, identicalVariants)
        }
        waitForIdle()
        return onNodeWithTag("scene").captureToImage().toAwtImage()
    }

    /** One scene, captured in its own composition; the test body returns the image it wants. */
    private fun captured(block: ComposeUiTest.() -> BufferedImage): BufferedImage {
        var out: BufferedImage? = null
        runComposeUiTest { out = block() }
        return requireNonNull(out)
    }

    private fun requireNonNull(image: BufferedImage?): BufferedImage =
        image ?: error("the composition produced no capture")

    private fun luma(p: Int): Double {
        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** The V3 style, with the resting indicator where the selected item is. */
    private val v3 = GlassTabBarStyle.V3(dark = true)

    /**
     * Optically identity and visually absent: profile Legacy with no displacement, no tint, no
     * rim and no lighting, so the selector contributes nothing at all. Anything that moves when
     * it moves is then a transform or a rounding error rather than optics.
     */
    private val invisible: GlassTabBarStyle = run {
        val clear = GlassStyle(
            profile = GlassProfile.Legacy,
            refractionBand = 8.dp,
            refractionDepth = 0.dp,
            blurRadius = 0.dp,
            backdropBlur = 0.dp,
            wideKernel = 0.dp,
            dispersion = 0f,
            inkDispersion = 0f,
            mirror = 0f,
            tint = Color.Transparent,
            tintLift = 0f,
            bevel = 0.dp,
            bevelPeak = 0f,
            specular = 0f,
            counterLight = 0f,
            edgeLight = 0f,
            edgeShadow = 0f,
            fresnel = 0f,
            innerShadow = 0f,
            contactShadow = 0f,
            adaptivity = 0f,
            legibility = 0f,
            invertsWithBackdrop = false,
        )
        GlassTabBarStyle.V3(dark = true).copy(pill = clear, lens = clear, heldLift = 0f, heldScale = 1f)
    }

    /**
     * The ink alone, isolated by differencing the scene against the same scene with no ink at
     * all. Page structure seen through the material is far brighter than a label, so measuring a
     * glyph against an absolute threshold would keep finding a stripe; the difference carries
     * only what the ink contributed, wherever the map put it.
     */
    private fun inkOnly(
        selected: Int,
        style: GlassTabBarStyle,
        renderScale: Float = 1f,
        identicalVariants: Boolean = true,
    ): Array<DoubleArray> {
        val withInk = captured {
            capture(
                selected = selected, style = style, renderScale = renderScale,
                identicalVariants = identicalVariants,
            )
        }
        val without = captured {
            capture(
                selected = selected, style = style, renderScale = renderScale,
                ordinaryInk = false, selectedInk = false, identicalVariants = identicalVariants,
            )
        }
        // Absolute difference: ink lighter than the page behind it and ink darker than it are
        // both ink. A signed difference would flip sign across a stripe and pull a centroid about.
        return Array(sceneHeight) { y ->
            DoubleArray(barWidth) { x -> abs(luma(withInk.getRGB(x, y)) - luma(without.getRGB(x, y))) }
        }
    }

    private val inset = 6
    private val slot = (barWidth - inset * 2) / 5.0
    private fun slotCentre(index: Int) = inset + (index + 0.5) * slot
    private val rowY get() = sceneHeight - barHeight / 2

    @Test
    fun theSelectorsThroughSourceCarriesNoOrdinaryInk() {
        // The decisive form of the source-graph test: the ordinary variant is bright and the
        // selected variant draws nothing at all. If the material the selector looks through still
        // carried the bar's own ink, the ordinary glyph would show through the selector as a
        // filtered copy. It must not: what the selector shows is material only.
        val substrate = captured {
            capture(selected = 2, style = v3, ordinaryInk = false, selectedInk = false)
        }
        val ordinaryOnly = captured {
            capture(selected = 2, style = v3, ordinaryInk = true, selectedInk = false)
        }
        val both = captured {
            capture(selected = 2, style = v3, ordinaryInk = true, selectedInk = true)
        }
        // Under the selector, with no selected ink drawn, nothing may have changed against the
        // no-ink substrate: the ordinary glyph is outside the selector's source graph entirely.
        var worstGhost = 0.0
        var ghostAt = ""
        var samples = 0
        for (dx in -16..16) {
            for (dy in -10..10) {
                val x = (slotCentre(2) + dx).toInt()
                val y = rowY + dy
                val d = abs(luma(ordinaryOnly.getRGB(x, y)) - luma(substrate.getRGB(x, y)))
                if (d > worstGhost) {
                    worstGhost = d
                    ghostAt = "($x, $y)"
                }
                samples++
            }
        }
        assertTrue(samples > 500, "only $samples samples")
        assertTrue(
            worstGhost <= 2.0,
            "a copy of the ordinary ink survives under the selector: $worstGhost levels at $ghostAt",
        )
        // The same comparison one slot away, where there is no selector, must show the ordinary
        // glyph plainly - otherwise the control above is measuring an empty region.
        var control = 0.0
        for (dx in -16..16) {
            for (dy in -10..10) {
                val x = (slotCentre(3) + dx).toInt()
                val y = rowY + dy
                control = max(control, abs(luma(ordinaryOnly.getRGB(x, y)) - luma(substrate.getRGB(x, y))))
            }
        }
        assertTrue(control > 30.0, "the ordinary glyph is only $control levels; the fixture draws nothing")
        // And the selected ink really does appear where the selector is.
        var selectedInk = 0.0
        for (dx in -16..16) {
            for (dy in -10..10) {
                val x = (slotCentre(2) + dx).toInt()
                val y = rowY + dy
                selectedInk = max(selectedInk, abs(luma(both.getRGB(x, y)) - luma(ordinaryOnly.getRGB(x, y))))
            }
        }
        assertTrue(selectedInk > 30.0, "the selected ink is only $selectedInk levels under the selector")
    }

    @Test
    fun movingTheApertureDoesNotMoveTheInkItIsOver() {
        // Section 15.3's identity control, stated as it is written: with zero source displacement
        // and identical paint variants, moving or deforming the aperture must change **ink
        // anchors** by no more than a quarter of a pixel. A global scale applied to a rasterised
        // row - what the capsule selector did with its gel - fails this immediately, because the
        // row is stretched about the selector instead of staying pinned to the bar.
        //
        // It deliberately does not assert that the selector is invisible. Material composited
        // through a second piece of glass does not reproduce that glass exactly; that is
        // pre-existing behaviour of `through`, identical on the capsule path, and is not what
        // this control is about.
        val reference = HashMap<Int, Double>()
        val dump = ArrayList<String>()
        var worst = 0.0
        var worstAt = ""
        for (selected in listOf(0, 2, 4)) {
            val ink = inkOnly(selected, invisible)
            for (index in 0..4) {
                val centre = slotCentre(index)
                val at = thickStrokeEdge(ink[rowY], centre) ?: continue
                val offset = at - centre
                dump.add("s" + selected + "/i" + index + "=" + offset)
                val known = reference[index]
                if (known == null) {
                    reference[index] = offset
                } else {
                    val d = abs(offset - known)
                    if (d > worst) {
                        worst = d
                        worstAt = "slot $index with the selector on $selected"
                    }
                }
            }
        }
        assertTrue(reference.size == 5, "only " + reference.size + " slots produced a measurable stroke")
        assertTrue(worst <= 0.25, "ink moved $worst px at $worstAt when the aperture moved; offsets " + dump)
    }

    @Test
    fun theSelectedGlyphIsAsSharpThroughTheSelectorAsItIsDrawnDirectly() {
        // The 10-90 % width of the thick stroke's edge, under the selector and one slot away, on
        // the ink-only difference. Under an identity map the two must agree: a soft edge here
        // would mean the ink had been resampled through the backdrop's performance scale, which
        // is exactly what section 8.3 forbids. Running it at both render scales is the point.
        for (renderScale in listOf(1f, 0.25f)) {
            val ink = inkOnly(selected = 2, style = invisible, renderScale = renderScale)
            val under = edgeWidth(ink[rowY], slotCentre(2).toInt() - 10)
            val beside = edgeWidth(ink[rowY], slotCentre(3).toInt() - 10)
            assertTrue(under > 0.0 && beside > 0.0, "no stroke edge found at render scale $renderScale")
            assertTrue(
                abs(under - beside) <= 0.5,
                "at render scale $renderScale the stroke under the selector has a $under px edge " +
                    "against $beside px beside it",
            )
            // And it really is a sharp edge, not two soft ones that happen to match.
            assertTrue(under <= 1.6, "at render scale $renderScale the stroke edge is $under px wide")
        }
    }

    @Test
    fun theSelectedItemShowsOneGlyphNotTwo() {
        // A duplicate would show as extra strokes beside the real ones in the ink-only image.
        val ink = inkOnly(selected = 2, style = v3, identicalVariants = false)
        val under = peaks(ink[rowY], slotCentre(2).toInt())
        val beside = peaks(ink[rowY], slotCentre(3).toInt())
        assertTrue(beside == 2, "the control slot shows $beside strokes; the fixture draws two")
        assertTrue(under == 2, "the selected slot shows $under strokes against $beside beside it")
    }

    /**
     * The sub-pixel position of the thick stroke's left edge, as its 50 % crossing.
     *
     * One named edge of one named stroke, in a window that can only contain that stroke: an item
     * is 40 px wide and centred in its slot, and the thick stroke runs from 8 to 14 px inside it.
     * A centroid over the whole slot would wander between the two strokes as the page behind them
     * changes their relative contrast, which measures the fixture rather than the anchor.
     */
    private fun thickStrokeEdge(row: DoubleArray, slotCentre: Double): Double? {
        val from = (slotCentre - 18).toInt().coerceAtLeast(0)
        val to = (slotCentre - 2).toInt().coerceAtMost(row.size - 2)
        var peak = 0.0
        for (x in from..to) peak = max(peak, row[x])
        if (peak < 10.0) return null
        val threshold = 0.5 * peak
        for (x in from until to) {
            val a = row[x]
            val b = row[x + 1]
            if (a < threshold && b >= threshold && b != a) return x + (threshold - a) / (b - a)
        }
        return null
    }

    /** The 10-90 % rise of the strongest ink edge within half a slot of [centreX], in px. */
    private fun edgeWidth(row: DoubleArray, centreX: Int): Double {
        val from = (centreX - 24).coerceAtLeast(1)
        val to = (centreX + 24).coerceAtMost(row.size - 2)
        var peak = 0.0
        var peakAt = -1
        for (x in from..to) {
            if (row[x] > peak) {
                peak = row[x]
                peakAt = x
            }
        }
        if (peakAt < 0 || peak < 10.0) return 0.0
        var x = peakAt
        while (x > from && row[x - 1] <= row[x]) x--
        val floor = row[x]
        if (peak - floor < 10.0) return 0.0
        val low = floor + 0.1 * (peak - floor)
        val high = floor + 0.9 * (peak - floor)
        var lowAt = -1.0
        var highAt = -1.0
        for (i in x until peakAt) {
            val a = row[i]
            val b = row[i + 1]
            if (lowAt < 0 && a <= low && b >= low && b != a) lowAt = i + (low - a) / (b - a)
            if (highAt < 0 && a <= high && b >= high && b != a) highAt = i + (high - a) / (b - a)
        }
        return if (lowAt < 0 || highAt < 0) 0.0 else highAt - lowAt
    }

    /** How many distinct ink strokes cross the row within half a slot of [centreX]. */
    private fun peaks(row: DoubleArray, centreX: Int): Int {
        val from = (centreX - 22).coerceAtLeast(0)
        val to = (centreX + 22).coerceAtMost(row.size - 1)
        var peak = 0.0
        for (x in from..to) peak = max(peak, row[x])
        if (peak < 10.0) return 0
        val threshold = 0.4 * peak
        var count = 0
        var above = false
        for (x in from..to) {
            val v = row[x] > threshold
            if (v && !above) count++
            above = v
        }
        return count
    }
}

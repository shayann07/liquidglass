package com.wexpa.liquidglass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * [glassEffect] under [LiquidGlassTheme] (Phase 2, 18 October): glass anywhere in the tree refracts
 * the screen behind it, never itself; its content sits on top; clicks still reach the element.
 */
@OptIn(ExperimentalTestApi::class)
class GlassEffectTest {
    private val out = File("build/reports/atlas/glass-effect").apply { mkdirs() }

    private enum class Placement { TopLevel, UnderLayer, Scrolled }

    @Composable private fun Stripes(modifier: Modifier) = Canvas(modifier) {
        var x = 0f
        while (x < size.width) { drawRect(Color.White, Offset(x, 0f), Size(8f, size.height)); x += 16f }
    }

    /** Stripes, and an 80 dp lens at (110, 60) carrying a red square. */
    @Composable private fun Page(placement: Placement, glass: Glass, onClick: () -> Unit = {}) {
        val lens = @Composable {
            Box(Modifier.offset(110.dp, 60.dp).size(80.dp).testTag("glass").clickable(onClick = onClick)
                .glassEffect(glass, CircleShape)) {
                Box(Modifier.align(Alignment.Center).size(10.dp).background(Color.Red))
            }
        }
        Box(Modifier.size(300.dp, 200.dp).background(Color.Black)) {
            Stripes(Modifier.fillMaxSize())
            when (placement) {
                Placement.TopLevel -> lens()
                Placement.UnderLayer -> Box(Modifier.fillMaxSize().graphicsLayer { translationX = .001f }) { lens() }
                Placement.Scrolled -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(40))) {
                    Spacer(Modifier.height(40.dp)); Box(Modifier.size(300.dp, 200.dp)) { lens() }; Spacer(Modifier.height(200.dp))
                }
            }
        }
    }

    /** Stripe transitions along the row through the lens centre, inside its central 41 px. */
    private fun transitions(img: BufferedImage, cx: Int, cy: Int): Int {
        var n = 0; var last = -1
        for (x in cx - 20..cx + 20) {
            val b = if ((img.getRGB(x, cy) shr 8 and 0xFF) > 127) 1 else 0
            if (last >= 0 && b != last) n++
            last = b
        }
        return n
    }

    private fun red(img: BufferedImage, x: Int, y: Int): Boolean {
        val p = img.getRGB(x, y)
        return (p shr 16 and 0xFF) > 180 && (p shr 8 and 0xFF) < 80
    }

    private fun capture(name: String, content: @Composable () -> Unit): BufferedImage {
        lateinit var image: BufferedImage
        runComposeUiTest(testTimeout = 4.minutes) {
            setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) { Box(Modifier.testTag("scene")) { content() } } }
            waitForIdle()
            image = onNodeWithTag("scene").captureToImage().toAwtImage()
        }
        ImageIO.write(image, "png", File(out, "$name.png"))
        return image
    }

    @Test fun aLensAnywhereInTheTreeRefractsTheScreenBehindItAndNotItself() {
        for (p in Placement.entries) {
            val img = capture("lens-${p.name}") { LiquidGlassTheme(dark = true) { Page(p, Glass.Lens(2f)) } }
            val inside = transitions(img, 150, 96)
            val outside = transitions(img, 60, 96)
            println("GLASS_EFFECT ${p.name}: transitions inside=$inside outside=$outside")
            // Flat 8 px stripes cross ~5 times in 41 px; magnified 2x, about half as often.
            assertTrue(outside >= 4, "${p.name}: the stripes are not flat away from the lens ($outside)")
            assertTrue(inside <= outside - 2, "${p.name}: no magnification under the lens ($inside vs $outside)")
            assertTrue(red(img, 150, 100), "${p.name}: the lens's content is not drawn on top of it")
            // No self-sampling: the red square must not appear refracted elsewhere in the lens.
            assertTrue(!red(img, 150, 80) && !red(img, 130, 100), "${p.name}: the lens shows its own content")
        }
    }

    @Test fun interactiveGlassLeavesTheClickToTheElement() = runComposeUiTest(testTimeout = 4.minutes) {
        var clicks = 0
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                LiquidGlassTheme(dark = true) { Page(Placement.TopLevel, Glass.Regular.interactive()) { clicks++ } }
            }
        }
        waitForIdle()
        onNodeWithTag("glass").performClick()
        waitForIdle()
        assertEquals(1, clicks, "the click did not reach the element under interactive glass")
    }

    @Test fun identityDrawsTheContentOnly() {
        val img = capture("identity") { LiquidGlassTheme(dark = true) { Page(Placement.TopLevel, Glass.Identity) } }
        assertEquals(transitions(img, 60, 96), transitions(img, 150, 80), "Identity drew glass")
        assertTrue(red(img, 150, 100))
    }

    @Test fun withoutATheItFallsBackToAFrostedStandIn() {
        val img = capture("no-root") { Page(Placement.TopLevel, Glass.Regular) }
        // The stand-in is a tinted surface: no refraction, the content still on top.
        assertTrue(red(img, 150, 100), "the content is missing without a root")
        val flat = transitions(img, 60, 96)
        assertTrue(flat >= 4)
    }

    @Test fun glassVariantsResolvePerAppearance() {
        assertEquals(GlassStyle.inApp(true), Glass.Regular.style(dark = true))
        assertEquals(GlassStyle.inApp(false), Glass.Regular.style(dark = false))
        assertEquals(GlassTabBarStyle.Calm(false).bar, Glass.TabBar.style(dark = false))
        assertEquals(Color.Blue, Glass.Regular.tint(Color.Blue).style(true)!!.tint)
        assertEquals(null, Glass.Identity.style(true))
        assertTrue(Glass.Regular.interactive().isInteractive && !Glass.Regular.isInteractive)
        assertEquals(Glass.Regular.tint(Color.Red), Glass.Regular.tint(Color.Red))
    }

    /**
     * Two 60 dp pieces 12 dp apart over flat grey 64: is the gap between them glass? Not mid-grey: the measured dark
     * tone (#200) puts Regular glass over grey 128 at 134, too close to the page to tell glass from no glass.
     */
    private fun gapLevel(container: Boolean): Int {
        val pieces = @Composable {
            Box(Modifier.offset(60.dp, 70.dp).size(60.dp).glassEffect(Glass.Regular, CircleShape))
            Box(Modifier.offset(132.dp, 70.dp).size(60.dp).glassEffect(Glass.Regular, CircleShape))
        }
        val img = capture("container-$container") {
            LiquidGlassTheme(dark = true) {
                Box(Modifier.size(300.dp, 200.dp).background(Color(0xFF404040))) {
                    if (container) GlassEffectContainer(Modifier.fillMaxSize(), spacing = 24.dp) { Box(Modifier.fillMaxSize()) { pieces() } }
                    else pieces()
                }
            }
        }
        return img.getRGB(126, 100) shr 8 and 0xFF
    }

    @Test fun piecesInAContainerMergeAcrossASmallGapAndOthersDoNot() {
        val apart = gapLevel(container = false)
        val merged = gapLevel(container = true)
        println("GLASS_CONTAINER gap level: separate=$apart container=$merged (page 64)")
        assertTrue(apart in 60..68, "separate pieces touched the gap: $apart")
        assertTrue(kotlin.math.abs(merged - 64) > 6, "no neck between merged pieces: $merged")
    }

    private fun outsideSide(container: Boolean, contour: Float): Int {
        val glass = Glass(GlassStyle.inApp(dark = false).copy(outerContour = contour))
        val piece = @Composable { Box(Modifier.offset(60.dp, 70.dp).size(60.dp).glassEffect(glass, CircleShape)) }
        val img = capture("contour-$container-$contour") {
            LiquidGlassTheme(dark = false) {
                Box(Modifier.size(300.dp, 200.dp).background(Color.White)) {
                    if (container) GlassEffectContainer(Modifier.fillMaxSize(), glass = glass) { Box(Modifier.fillMaxSize()) { piece() } }
                    else piece()
                }
            }
        }
        // The first pixel wholly outside the circle's right edge, level with its centre.
        return img.getRGB(120, 100) shr 8 and 0xFF
    }

    @Test fun theOuterContourDrawsOutsideTheOutlineAloneAndInAContainer() {
        // GlassStyle.outerContour, off by default; on, it darkens the white page just outside the
        // circle's unlit side, through both shaders (a lone element and a container's merged body).
        val off = outsideSide(container = false, contour = 0f)
        val alone = outsideSide(container = false, contour = 0.61f)
        val merged = outsideSide(container = true, contour = 0.61f)
        println("GLASS_OUTER_CONTOUR outside the side: off=$off alone=$alone container=$merged (page 255)")
        assertTrue(off >= 250, "no contour by default: $off")
        assertTrue(alone < 215, "a lone element draws the contour: $alone")
        assertTrue(merged < 215, "a container draws the contour: $merged")
    }

    @Test fun anIdThatReappearsMorphsFromWhereItWas() = runComposeUiTest(testTimeout = 4.minutes) {
        var right by mutableStateOf(false)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                LiquidGlassTheme(dark = true) {
                    // Grey 64, not 128: see gapLevel.
                    Box(Modifier.size(300.dp, 200.dp).background(Color(0xFF404040)).testTag("scene")) {
                        val ns = rememberGlassNamespace()
                        GlassEffectContainer(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize()) {
                                if (!right) Box(Modifier.offset(20.dp, 70.dp).size(60.dp).glassEffect(Glass.Regular, CircleShape).glassEffectID("x", ns))
                                else Box(Modifier.offset(220.dp, 70.dp).size(60.dp).glassEffect(Glass.Regular, CircleShape).glassEffectID("x", ns))
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        right = true
        mainClock.advanceTimeBy(120)
        val mid = onNodeWithTag("scene").captureToImage().toAwtImage()
        ImageIO.write(mid, "png", File(out, "morph-mid.png"))
        mainClock.autoAdvance = true
        waitForIdle()
        val end = onNodeWithTag("scene").captureToImage().toAwtImage()
        ImageIO.write(end, "png", File(out, "morph-end.png"))
        fun glassAt(img: BufferedImage, x: Int) = kotlin.math.abs((img.getRGB(x, 100) shr 8 and 0xFF) - 64) > 6
        val midX = (0 until 300).filter { glassAt(mid, it) }
        println("GLASS_MORPH mid glass x ${midX.minOrNull()}..${midX.maxOrNull()}, end at 250: ${glassAt(end, 250)}")
        assertTrue(midX.isNotEmpty() && midX.average() in 60.0..240.0, "the piece did not travel from its old place: $midX")
        assertTrue(glassAt(end, 250) && !glassAt(end, 50), "the piece did not land at its new place")
    }
}

package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The public components under [LiquidGlassTheme] (Phase 2, 18 October): [GlassTabBar] composes in the
 * theme's overlay through a portal, laid out where it is called, and takes its own touches; a nested
 * theme changes the accessibility settings for its subtree over the same recording.
 */
@OptIn(ExperimentalTestApi::class)
class GlassThemeComponentsTest {
    private val out = File("build/reports/atlas/glass-effect").apply { mkdirs() }

    @Test fun theTabBarDrawsWhereItIsLaidOutAndTakesItsOwnTaps() = runComposeUiTest(testTimeout = 4.minutes) {
        var selected by mutableIntStateOf(0)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                LiquidGlassTheme(dark = true, background = Color(0xFF808080)) {
                    Box(Modifier.size(400.dp, 200.dp).background(Color(0xFF808080)).testTag("scene")) {
                        GlassTabBar(3, selected, { selected = it },
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp).width(300.dp).testTag("bar")) { _, isSelected ->
                            Box(Modifier.size(18.dp).background(if (isSelected) Color.Blue else Color.White))
                        }
                    }
                }
            }
        }
        waitForIdle()
        // The placeholder carries the caller's modifiers: aligned, padded and 300 wide.
        val bounds = onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
        assertEquals(50f, bounds.left, 1f); assertEquals(350f, bounds.right, 1f)
        assertEquals(180f, bounds.bottom, 1f)
        val img = onNodeWithTag("scene").captureToImage().toAwtImage()
        ImageIO.write(img, "png", File(out, "tabbar-portal.png"))
        // The bar is drawn there (it changes the grey page), and nowhere above it.
        val y = ((bounds.top + bounds.bottom) / 2).toInt()
        val onBar = (img.getRGB(200, y) shr 8) and 0xFF
        val above = (img.getRGB(200, 30) shr 8) and 0xFF
        println("GLASS_TABBAR bounds=$bounds onBar=$onBar above=$above")
        assertTrue(abs(onBar - 128) > 4, "no bar drawn at its place ($onBar)")
        assertTrue(abs(above - 128) <= 2, "the bar drew away from its place ($above)")
        // A tap on the third tab selects it: the overlay bar takes its own touches.
        val tab = onNodeWithTag(GlassTabBarSemantics.TAG, useUnmergedTree = true)
        tab.performTouchInput { val w = width / 3f; down(Offset(w * 2.5f, height / 2f)); advanceEventTime(60); up() }
        waitForIdle()
        assertEquals(2, selected, "the tap did not reach the bar")
    }

    /**
     * An accessory composed after a tab bar draws over the bar's portal, linked or not: its content
     * (a red square) stays visible. Before 18 October the overlay drew every glass element before every
     * portal, and the bar's portal covered Atlas's search icon.
     */
    @Test fun anAccessoryComposedAfterTheBarDrawsOverIt() = runComposeUiTest(testTimeout = 4.minutes) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                LiquidGlassTheme(dark = true, background = Color(0xFF808080)) {
                    Box(Modifier.size(400.dp, 120.dp).background(Color(0xFF808080)).testTag("scene")) {
                        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                            GlassTabBar(3, 0, {}, Modifier.width(260.dp),
                                trailingAccessory = GlassTabBarAccessory(62.dp, gap = 8.dp, yieldsToLens = true)) { _, _ ->
                                Box(Modifier.size(18.dp).background(Color.White))
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.size(62.dp).glassEffect(Glass.TabBar, CircleShape), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(20.dp).background(Color.Red))
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        val img = onNodeWithTag("scene").captureToImage().toAwtImage()
        ImageIO.write(img, "png", File(out, "tabbar-accessory-order.png"))
        // The accessory's centre: the bar (260) + gap (8) + half the button (31), centred in 400.
        val x = (400 - (260 + 8 + 62)) / 2 + 260 + 8 + 31
        val rgb = img.getRGB(x, 60)
        val r = (rgb shr 16) and 0xFF; val g = (rgb shr 8) and 0xFF
        println("GLASS_ACCESSORY_ORDER x=$x r=$r g=$g")
        assertTrue(r > 180 && g < 90, "the accessory's content is hidden under the bar (r=$r g=$g)")
    }

    /**
     * #107: a linked yielding accessory keeps its icon visible at rest (the bar stays underneath it)
     * and when the last tab's held lens reaches across it, the lens draws over the accessory and
     * refracts the accessory's own rendering rather than the bar's fused silhouette.
     */
    @Test fun aLinkedYieldingAccessoryStaysVisibleAndRefractsThroughHeldLens() = runComposeUiTest(testTimeout = 4.minutes) {
        lateinit var linkRef: GlassAccessoryLink
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                LiquidGlassTheme(dark = true, background = Color.Black) {
                    val link = rememberGlassAccessoryLink().also { linkRef = it }
                    Box(Modifier.size(400.dp, 120.dp).background(Color.Black).testTag("scene")) {
                        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                            GlassTabBar(3, 2, {}, Modifier.width(278.dp),
                                style = GlassTabBarStyle.Calm(dark = true),
                                trailingAccessory = GlassTabBarAccessory(62.dp, gap = 8.dp, yieldsToLens = true, link = link)) { _, _ ->
                                Box(Modifier.size(18.dp).background(Color.White))
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.size(62.dp).glassEffect(Glass(GlassTabBarStyle.Calm(dark = true).bar), CircleShape, link = link),
                                contentAlignment = Alignment.Center) {
                                Box(Modifier.size(20.dp).background(Color.Red))
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        val restImg = onNodeWithTag("scene").captureToImage().toAwtImage()
        // Bar (278) + gap (8) + button (62) = 348, centred in 400 -> left = 26, button x = 312..374, centre = 343.
        val restRgb = restImg.getRGB(343, 60)
        val rr = (restRgb shr 16) and 0xFF; val rg = (restRgb shr 8) and 0xFF
        println("LINKED_107_REST centre=(343,60) r=$rr g=$rg captured=${linkRef.captured} cap=${linkRef.capture != null}")
        assertTrue(rr > 180 && rg < 90, "linked accessory icon hidden at rest (r=$rr g=$rg)")

        val tab = onNodeWithTag(GlassTabBarSemantics.TAG, useUnmergedTree = true)
        tab.performTouchInput { down(Offset(width * 0.85f, height / 2f)); advanceEventTime(500); moveTo(Offset(width * 0.85f, height / 2f)) }
        waitForIdle()
        val heldImg = onNodeWithTag("scene").captureToImage().toAwtImage()
        tab.performTouchInput { up() }
        val heldCenter = heldImg.getRGB(343, 60)
        val hr = (heldCenter shr 16) and 0xFF; val hg = (heldCenter shr 8) and 0xFF
        val overBtn = (heldImg.getRGB(318, 60) shr 8) and 0xFF
        val row = (300..340 step 2).joinToString(" ") { x -> "${(heldImg.getRGB(x, 60) shr 8) and 0xFF}" }
        println("LINKED_107_HELD centre=(343,60) r=$hr g=$hg overBtn=$overBtn captured=${linkRef.captured} cap=${linkRef.capture != null}")
        println("LINKED_107_ROW x=300..340 step 2: $row")
        assertTrue(hr > 180 && hg < 90, "linked accessory icon hidden while held (r=$hr g=$hg)")
        assertTrue(overBtn in 20..40, "lens over accessory should refract accessory glass (30-36), not bar (49): got $overBtn")
    }

    @Test fun aNestedThemeChangesAccessibilityForItsSubtree() {
        fun level(reduce: Float): Int {
            lateinit var image: java.awt.image.BufferedImage
            runComposeUiTest(testTimeout = 4.minutes) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        LiquidGlassTheme(dark = true, background = Color.Black) {
                            Box(Modifier.size(200.dp, 120.dp).background(Color(0xFF808080)).testTag("scene")) {
                                LiquidGlassTheme(dark = true, reduceTransparency = reduce) {
                                    Box(Modifier.offset(40.dp, 20.dp).size(120.dp, 80.dp)
                                        .glassEffect(Glass.Regular, RoundedCornerShape(20.dp)))
                                }
                            }
                        }
                    }
                }
                waitForIdle()
                image = onNodeWithTag("scene").captureToImage().toAwtImage()
            }
            ImageIO.write(image, "png", File(out, "nested-reduce-$reduce.png"))
            return (image.getRGB(100, 60) shr 8) and 0xFF
        }
        val clear = level(0f)
        val frosted = level(1f)
        println("GLASS_NESTED reduceTransparency 0 -> $clear, 1 -> $frosted")
        assertTrue(abs(clear - frosted) > 4, "a nested reduceTransparency changed nothing ($clear vs $frosted)")
    }
}

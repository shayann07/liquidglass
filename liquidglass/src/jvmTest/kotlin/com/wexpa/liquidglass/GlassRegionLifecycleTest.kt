package com.wexpa.liquidglass

import kotlin.time.Duration.Companion.minutes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class GlassRegionLifecycleTest {
    /** Replacing root settings disposes old leases and attaches nested consumers to the new recording. */
    @Test fun rootSettingsReplacementReleasesOldRegionWithNestedConsumersAlive() = runComposeUiTest(testTimeout = 4.minutes) {
        var rootLevel by mutableFloatStateOf(0f)
        var show by mutableStateOf(true)
        lateinit var root: GlassRegion
        setContent {
            if (show) LiquidGlassTheme(dark = true, reduceTransparency = rootLevel) {
                root = LocalGlassRegion.current!!
                LiquidGlassTheme(reduceTransparency = .75f) {
                    Box(Modifier.size(12.dp).glassEffect())
                    Box(Modifier.size(8.dp).glassEffect())
                }
            }
        }
        waitForIdle()
        val old = root
        val oldView = old.views.values.single()
        runOnIdle { rootLevel = .25f }; waitForIdle()
        assertNotSame(old, root)
        assertTrue(old.views.isEmpty(), "Replacing the root must release every old lease")
        assertTrue(old.layers.isEmpty())
        val replacement = root.views.values.single()
        assertNotSame(oldView, replacement)
        assertSame(root.state.layer, replacement.layer)
        assertEquals(root.state.sourceSize, replacement.sourceSize)
        runOnIdle { show = false }; waitForIdle()
        assertTrue(root.views.isEmpty())
        assertTrue(root.layers.isEmpty())
    }

    /** Matching consumers share a live view whose recording follows root size changes. */
    @Test fun sharedSettingsSurviveUntilTheirLastConsumerLeaves() = runComposeUiTest(testTimeout = 4.minutes) {
        var first by mutableStateOf(true)
        var second by mutableStateOf(true)
        var firstLevel by mutableFloatStateOf(.25f)
        var width by mutableIntStateOf(20)
        lateinit var root: GlassRegion
        setContent {
            LiquidGlassTheme(dark = true) {
                root = LocalGlassRegion.current!!
                Box(Modifier.size(width.dp, 20.dp)) {
                    if (first) key("first") {
                        LiquidGlassTheme(reduceTransparency = firstLevel) {
                            Box(Modifier.size(4.dp).glassEffect())
                        }
                    }
                    if (second) key("second") {
                        LiquidGlassTheme(reduceTransparency = .25f) {
                            Box(Modifier.size(4.dp).glassEffect())
                        }
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(1, root.views.size)
        val shared = root.views.values.single()
        runOnIdle { firstLevel = .5f }; waitForIdle()
        assertEquals(2, root.views.size)
        assertTrue(root.views.values.any { it === shared })
        runOnIdle { first = false; width = 30 }; waitForIdle()
        assertEquals(1, root.views.size)
        assertSame(shared, root.views.values.single())
        assertSame(root.state.layer, shared.layer)
        assertEquals(root.state.sourceSize, shared.sourceSize)
        assertEquals(Size(30f, 20f), shared.sourceSize)
        runOnIdle { second = false }; waitForIdle()
        assertTrue(root.views.isEmpty())
    }

    /** Portal and overlay consumers release both their settings views and registered layers. */
    @Test fun portalAndOverlayConsumersReleaseTheirViews() = runComposeUiTest(testTimeout = 4.minutes) {
        var show by mutableStateOf(true)
        lateinit var root: GlassRegion
        setContent {
            LiquidGlassTheme(dark = true) {
                root = LocalGlassRegion.current!!
                Box(Modifier.size(20.dp)) {
                    if (show) LiquidGlassTheme(reduceTransparency = .4f) {
                        GlassPortal(Modifier.size(4.dp)) { Box(Modifier.size(4.dp)) }
                        GlassOverlay { Box(Modifier.size(4.dp).glassEffect()) }
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(1, root.views.size)
        runOnIdle { show = false }; waitForIdle()
        assertTrue(root.views.isEmpty())
        assertTrue(root.layers.isEmpty())
    }

    /** Abandonment releases exactly one reference even if Compose repeats the notification. */
    @Test fun abandonedLeaseReleasesOnlyItsOwnReference() {
        val region = GlassRegion(LiquidGlassState(Color.Black, 0f, 0f, 0f, 1f))
        val defaults = GlassDefaults(true, Glass.Regular, 20.dp, reduceTransparency = .5f)
        val active = GlassViewLease(region, defaults)
        active.onRemembered()
        val abandoned = GlassViewLease(region, defaults)
        assertSame(active.state, abandoned.state)
        abandoned.onAbandoned()
        assertSame(active.state, region.views.values.single())
        active.onForgotten()
        assertTrue(region.views.isEmpty())
        // Defensive idempotence must not release another consumer of the same key.
        val next = GlassViewLease(region, defaults)
        abandoned.onAbandoned()
        assertSame(next.state, region.views.values.single())
        next.onForgotten()
    }

    /** Repeated settings changes retain only current consumers, with no historical views after disposal. */
    @Test fun releasesHistoricalViewsAfterDisposal() = runComposeUiTest(testTimeout = 4.minutes) {
        var level by mutableFloatStateOf(0f)
        var show by mutableStateOf(true)
        lateinit var root: GlassRegion
        setContent {
            LiquidGlassTheme(dark = true) {
                root = LocalGlassRegion.current!!
                if (show) LiquidGlassTheme(reduceTransparency = level) {
                    Box(Modifier.size(12.dp).glassEffect())
                }
            }
        }
        waitForIdle()
        repeat(8) { i ->
            runOnIdle { level = (i + 1) / 31f }
            waitForIdle()
        }
        assertEquals(1, root.views.size)
        runOnIdle { show = false }
        waitForIdle()
        assertEquals(0, root.layers.size)
        // No state is retained after the final child leaves.
        assertEquals(0, root.views.size)
    }
}

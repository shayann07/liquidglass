package com.wexpa.liquidglass

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt
import kotlin.test.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable

/**
 * #81: a size-only change builds the field off composition, and no composed frame ever returns a
 * field for another shape, another origin, or nothing at all. The build dispatcher is held, so each
 * test sees exactly what composition returns before and after the background build is published.
 */
@OptIn(ExperimentalTestApi::class)
class GlassPathFieldOffCompositionTest {
    /** Queues builds until [runAll], so a test can look at composition while a build is pending. */
    private class HeldDispatcher : CoroutineDispatcher() {
        val queue = ArrayDeque<Runnable>()
        var dispatched = 0
        /** The job of the most recently queued build, to check that leaving composition cancels it. */
        var lastJob: Job? = null
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatched++; lastJob = context[Job]; synchronized(queue) { queue.addLast(block) }
        }
        fun runAll() { while (true) { (synchronized(queue) { queue.removeFirstOrNull() } ?: return).run() } }
        val pending get() = synchronized(queue) { queue.size }
    }

    private val density = Density(1f)
    private val notch = GenericShape { s, _ ->
        moveTo(0f, 0f); lineTo(s.width / 2, 12f)
        lineTo(s.width, 0f); lineTo(s.width, s.height)
        lineTo(0f, s.height); close()
    }
    private val wedge = GenericShape { s, _ ->
        moveTo(0f, 0f); lineTo(s.width, s.height / 3); lineTo(s.width, s.height); lineTo(0f, s.height); close()
    }

    private fun withHeldDispatcher(block: (HeldDispatcher) -> Unit) {
        val held = HeldDispatcher()
        glassPathFieldDispatcher = held
        try { block(held) } finally { held.runAll(); glassPathFieldDispatcher = kotlinx.coroutines.Dispatchers.Default }
    }

    /** Every texel against an uncached build at the shader's origin. */
    private fun assertMatchesFreshBuild(shape: Shape, size: Size, pad: Float, field: GlassPathField?) {
        val path = (shape.createOutline(size, LayoutDirection.Ltr, density) as Outline.Generic).path
        val fresh = buildPathField(path, (size.width + pad * 2).roundToInt(), (size.height + pad * 2).roundToInt(), pad, 16f)!!
        assertNotNull(field)
        assertEquals(fresh.layerWidth, field.layerWidth)
        assertEquals(fresh.layerHeight, field.layerHeight)
        val expected = fresh.bitmap.toPixelMap()
        val actual = field.bitmap.toPixelMap()
        for (y in 0 until expected.height) for (x in 0 until expected.width) {
            assertEquals(expected[x, y], actual[x, y], "size=$size pixel=$x,$y")
        }
    }

    /** The very first composition already returns the finished field: no frame draws without it. */
    @Test fun firstCompositionReturnsTheFieldSynchronously() = withHeldDispatcher { held ->
        runComposeUiTest {
            val composed = mutableListOf<GlassPathField?>()
            setContent {
                composed += rememberGlassPathField(notch, Size(100f, 60f), 24f, 16f, density, LayoutDirection.Ltr, enabled = true)
            }
            waitForIdle()
            assertEquals(0, held.dispatched, "the first field must not wait for a background build")
            assertTrue(composed.isNotEmpty())
            assertMatchesFreshBuild(notch, Size(100f, 60f), 24f, composed.first())
        }
    }

    /** A size-only change keeps the previous field, never null, until the background build publishes. */
    @Test fun sizeChangeKeepsThePreviousFieldUntilTheBackgroundBuildIsPublished() = withHeldDispatcher { held ->
        runComposeUiTest {
            var size by mutableStateOf(Size(100f, 60f))
            val composed = mutableListOf<GlassPathField?>()
            setContent {
                composed += rememberGlassPathField(notch, size, 24f, 16f, density, LayoutDirection.Ltr, enabled = true)
            }
            waitForIdle()
            val first = composed.last()
            runOnIdle { size = Size(140f, 80f) }
            waitForIdle()
            assertEquals(1, held.pending, "the resize must be built on the field dispatcher")
            assertTrue(composed.all { it === first }, "every frame before publication draws the previous field")
            held.runAll()
            waitUntil(timeoutMillis = 5_000) { composed.last() !== first }
            assertMatchesFreshBuild(notch, Size(140f, 80f), 24f, composed.last())
            assertTrue(composed.all { it != null }, "no frame may draw without a field")
            assertEquals(2, composed.distinct().size, "only the previous and the new field are ever drawn")
        }
    }

    /** Layout reports an empty size first; the first real size has no field to keep, so it builds now. */
    @Test fun firstRealSizeAfterAnEmptyOneBuildsSynchronously() = withHeldDispatcher { held ->
        runComposeUiTest {
            var size by mutableStateOf(Size.Zero)
            val composed = mutableListOf<GlassPathField?>()
            setContent {
                composed += rememberGlassPathField(notch, size, 24f, 16f, density, LayoutDirection.Ltr, enabled = true)
            }
            waitForIdle()
            assertNull(composed.last())
            runOnIdle { size = Size(100f, 60f) }
            waitForIdle()
            assertEquals(0, held.dispatched, "an empty field must not stay on screen while a build runs")
            assertMatchesFreshBuild(notch, Size(100f, 60f), 24f, composed.last())
            runOnIdle { size = Size(120f, 60f) }
            waitForIdle()
            assertEquals(1, held.pending, "with a field to keep, the next resize builds in the background")
        }
    }

    /** Resizes requested while a build runs collapse to the latest size: one stale build, then the last. */
    @Test fun resizesDuringABuildAreConflatedToTheLatest() = withHeldDispatcher { held ->
        runComposeUiTest {
            var size by mutableStateOf(Size(100f, 60f))
            var result: GlassPathField? = null
            setContent {
                val field = rememberGlassPathField(notch, size, 24f, 16f, density, LayoutDirection.Ltr, enabled = true)
                SideEffect { result = field }
            }
            waitForIdle()
            for (w in listOf(110f, 120f, 130f, 140f)) { runOnIdle { size = Size(w, 60f) }; waitForIdle() }
            assertEquals(1, held.dispatched, "later sizes wait for the running build")
            held.runAll()
            waitUntil(timeoutMillis = 5_000) { held.pending > 0 || result?.layerWidth == 140 + 48 }
            held.runAll()
            waitUntil(timeoutMillis = 5_000) { result?.layerWidth == 140 + 48 }
            assertEquals(2, held.dispatched, "110 is built, 120 and 130 are skipped, 140 is built")
            assertMatchesFreshBuild(notch, Size(140f, 60f), 24f, result)
        }
    }

    /** A different outline or origin cannot reuse the previous field, so it is built before the frame. */
    @Test fun shapeAndPadChangesBuildSynchronously() = withHeldDispatcher { held ->
        runComposeUiTest {
            var shape by mutableStateOf<Shape>(notch)
            var pad by mutableFloatStateOf(24f)
            val composed = mutableListOf<GlassPathField?>()
            setContent {
                composed += rememberGlassPathField(shape, Size(100f, 60f), pad, 16f, density, LayoutDirection.Ltr, enabled = true)
            }
            waitForIdle()
            runOnIdle { shape = wedge }
            waitForIdle()
            assertEquals(0, held.dispatched)
            assertMatchesFreshBuild(wedge, Size(100f, 60f), 24f, composed.last())
            runOnIdle { pad = 30f }
            waitForIdle()
            assertEquals(0, held.dispatched)
            assertMatchesFreshBuild(wedge, Size(100f, 60f), 30f, composed.last())
        }
    }

    /** A shape that rewrites one Path for every size must not change a build that is already queued. */
    @Test fun queuedBuildKeepsItsOwnOutlineWhenTheShapeReusesItsPath() = withHeldDispatcher { held ->
        runComposeUiTest {
            val reused = Path()
            val shape = object : Shape {
                override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                    reused.reset()
                    reused.moveTo(0f, 0f); reused.lineTo(size.width / 2, 12f)
                    reused.lineTo(size.width, 0f); reused.lineTo(size.width, size.height)
                    reused.lineTo(0f, size.height); reused.close()
                    return Outline.Generic(reused)
                }
            }
            var size by mutableStateOf(Size(100f, 60f))
            var result: GlassPathField? = null
            setContent {
                val field = rememberGlassPathField(shape, size, 24f, 16f, density, LayoutDirection.Ltr, enabled = true)
                SideEffect { result = field }
            }
            waitForIdle()
            runOnIdle { size = Size(140f, 80f) }; waitForIdle()
            // The 140 x 80 build is queued; the shape now rewrites its one Path for another size.
            shape.createOutline(Size(60f, 30f), LayoutDirection.Ltr, density)
            held.runAll()
            waitUntil(timeoutMillis = 5_000) { result?.layerWidth == 140 + 48 }
            assertMatchesFreshBuild(notch, Size(140f, 80f), 24f, result)
        }
    }

    /** A build still pending when the panel leaves composition is dropped without publishing. */
    @Test fun pendingBuildIsDroppedWhenThePanelLeaves() = withHeldDispatcher { held ->
        runComposeUiTest {
            var size by mutableStateOf(Size(100f, 60f))
            var shown by mutableStateOf(true)
            setContent {
                if (shown) rememberGlassPathField(notch, size, 24f, 16f, density, LayoutDirection.Ltr, enabled = true)
            }
            waitForIdle()
            runOnIdle { size = Size(150f, 60f) }
            waitForIdle()
            assertEquals(1, held.pending)
            runOnIdle { shown = false }
            waitForIdle()
            assertFalse(assertNotNull(held.lastJob).isActive, "leaving composition must cancel the queued build")
            held.runAll()
            waitForIdle()
        }
    }
}

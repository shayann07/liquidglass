package com.wexpa.liquidglass

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class GlassPathFieldCacheRegressionTest {
    /** Unsupported backends must skip even outline construction, not merely discard its field. */
    @Test fun disabledBackendDoesNotCreateAnOutline() = runComposeUiTest {
        val shape = object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
                error("A shader-incapable backend must not build a field")
        }
        var field: GlassPathField? = null
        setContent {
            field = rememberGlassPathField(shape, Size(1080f, 720f), 24f, 48f,
                Density(1f), LayoutDirection.Ltr, enabled = false)
        }
        waitForIdle()
        assertNull(field)
    }

    /** Equal bitmap extents must not hide changes to the inner outline and its translation. */
    @Test fun equalPaddedDimensionsReuseDifferentGeometry() = runComposeUiTest {
        val density = Density(1f)
        var outlineCalls = 0
        val shape = object : Shape {
            override fun createOutline(
                size: Size, layoutDirection: LayoutDirection, density: Density
            ): Outline {
                outlineCalls++
                return Outline.Generic(Path().apply {
                    moveTo(0f, 0f); lineTo(size.width / 2, 12f)
                    lineTo(size.width, 0f); lineTo(size.width, size.height)
                    lineTo(0f, size.height); close()
                })
            }
        }
        var second by mutableStateOf(false)
        var tick by mutableIntStateOf(0)
        var result: GlassPathField? = null
        setContent {
            val frame = tick
            val field = rememberGlassPathField(
                shape, if (second) Size(80f, 40f) else Size(100f, 60f),
                if (second) 20f else 10f, 16f, density, LayoutDirection.Ltr, enabled = true
            )
            SideEffect { if (frame >= 0) result = field }
        }
        waitForIdle()
        val first = result
        val calls = outlineCalls
        runOnIdle { second = true }
        waitForIdle()
        // A changed inner geometry must invalidate the field.
        assertNotSame(first, result)
        assertEquals(calls + 1, outlineCalls)
        val path = (shape.createOutline(
            Size(80f, 40f), LayoutDirection.Ltr, density
        ) as Outline.Generic).path
        val fresh = buildPathField(path, 120, 80, 20f, 16f)!!.bitmap.toPixelMap()
        val stale = result!!.bitmap.toPixelMap()
        var differences = 0
        for (y in 0 until stale.height) for (x in 0 until stale.width) {
            if (stale[x, y] != fresh[x, y]) differences++
        }
        assertEquals(0, differences)
        println("different_texels=$differences")
        val correct = result
        runOnIdle { tick++ }; waitForIdle()
        assertSame(correct, result)
    }

    /** Keeps cache reuse on the recording grid without moving fractional shader origins. */
    @Test fun subpixelGeometryReusesFieldUntilRecordingGridInputsChange() = runComposeUiTest {
        val density = Density(1f)
        val outlines = mutableListOf<Size>()
        val shape = object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                outlines += size
                return Outline.Generic(Path().apply {
                    moveTo(0f, 0f); lineTo(size.width / 2, 12f)
                    lineTo(size.width, 0f); lineTo(size.width, size.height)
                    lineTo(0f, size.height); close()
                })
            }
        }
        var size by mutableStateOf(Size(100.1f, 60.1f))
        var blur by mutableFloatStateOf(22.1f)
        var result: GlassPathField? = null
        /** Exercises production style padding, including its render-scale quantization. */
        fun recordedPad() = GlassStyle(blurRadius = blur.dp).recordPad(
            FloatArray(4), size, null, density, renderScale = 0.75f
        )
        setContent {
            val field = rememberGlassPathField(shape, size, recordedPad(), 16f, density,
                LayoutDirection.Ltr, enabled = true)
            SideEffect { result = field }
        }
        waitForIdle()
        val first = result
        assertEquals(17f / 0.75f, recordedPad())
        runOnIdle { blur = 22.4f }; waitForIdle()
        assertSame(first, result, "Style changes within a recording-grid bucket must reuse the EDT")
        runOnIdle { size = Size(100.4f, 60.4f) }; waitForIdle()
        assertSame(first, result)
        assertEquals(listOf(Size(100f, 60f)), outlines)

        /** Checks geometry and every texel against an uncached build at the shader's origin. */
        fun assertMatchesRecordedBuild(expectedSize: Size, expectedPad: Float) {
            assertEquals(expectedSize, outlines.last())
            val path = (shape.createOutline(expectedSize, LayoutDirection.Ltr, density) as Outline.Generic).path
            val fresh = buildPathField(path, (expectedSize.width + 2 * expectedPad).roundToInt(),
                (expectedSize.height + 2 * expectedPad).roundToInt(), expectedPad, 16f)!!
            assertEquals(fresh.layerWidth, result!!.layerWidth)
            assertEquals(fresh.layerHeight, result!!.layerHeight)
            val actual = result!!.bitmap.toPixelMap()
            val reference = fresh.bitmap.toPixelMap()
            for (y in 0 until reference.height) for (x in 0 until reference.width) {
                assertEquals(reference[x, y], actual[x, y])
            }
        }
        assertMatchesRecordedBuild(Size(100f, 60f), 17f / 0.75f)
        runOnIdle { blur = 22.8f }; waitForIdle()
        assertNotSame(first, result)
        assertMatchesRecordedBuild(Size(100f, 60f), 24f)
        val changedPad = result
        runOnIdle { size = Size(100.6f, 60.6f) }; waitForIdle()
        // A size-only change is built off composition (#81); the previous field stays until then.
        waitUntil(timeoutMillis = 5_000) { result !== changedPad }
        assertNotSame(changedPad, result)
        assertMatchesRecordedBuild(Size(101f, 61f), 24f)
    }

    /** Scale changes must preserve each recording-grid origin, including fractional layer pixels. */
    @Test fun fractionalPaddingMatchesFreshFieldAcrossRenderScales() = runComposeUiTest {
        val density = Density(1f)
        val size = Size(100f, 60f)
        val shape = GenericShape { s, _ ->
            moveTo(0f, 0f); lineTo(s.width / 2, 12f)
            lineTo(s.width, 0f); lineTo(s.width, s.height)
            lineTo(0f, s.height); close()
        }
        var scale by mutableFloatStateOf(1f)
        var result: GlassPathField? = null
        val style = GlassStyle(blurRadius = 22.1.dp)
        setContent {
            val pad = style.recordPad(FloatArray(4), size, null, density, scale)
            val field = rememberGlassPathField(shape, size, pad, 16f, density,
                LayoutDirection.Ltr, enabled = true)
            SideEffect { result = field }
        }
        for (next in listOf(1f, 0.75f, 0.6f, 0.5f, 0.25f)) {
            runOnIdle { scale = next }; waitForIdle()
            val pad = style.recordPad(FloatArray(4), size, null, density, next)
            val path = (shape.createOutline(size, LayoutDirection.Ltr, density) as Outline.Generic).path
            val fresh = buildPathField(path, (size.width + pad * 2).roundToInt(),
                (size.height + pad * 2).roundToInt(), pad, 16f)!!
            assertEquals(fresh.layerWidth, result!!.layerWidth, "renderScale=$next pad=$pad")
            assertEquals(fresh.layerHeight, result!!.layerHeight)
            val expected = fresh.bitmap.toPixelMap()
            val actual = result!!.bitmap.toPixelMap()
            for (y in 0 until expected.height) for (x in 0 until expected.width) {
                assertEquals(expected[x, y], actual[x, y], "renderScale=$next pixel=$x,$y")
            }
        }
    }
}

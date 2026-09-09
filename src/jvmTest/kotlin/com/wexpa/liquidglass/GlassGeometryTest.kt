package com.wexpa.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The geometry the shader depends on, checked on the JVM.
 *
 * None of this needs a device: the sample bounds are arithmetic, the distance field is a pure
 * transform over a bitmap, and the style interpolation is a data class. All three have already
 * shipped a bug that only showed up as a rendering artefact, which is the argument for pinning
 * them here rather than looking at screenshots.
 */
class GlassSampleBoundsTest {

    @Test
    fun testBoundsAreNeverInverted() {
        // A panel scrolled clear of the recorded backdrop: the naive intersection puts the
        // bottom edge above the top one, and the shader then calls clamp() with min > max,
        // which is undefined and drew a solid block of nothing on the device.
        val bounds = sampleBounds(
            pad = 78f,
            delta = Offset(53f, 2521f),
            panelSize = Size(974f, 289f),
            sourceSize = Size(1080f, 2400f),
        )
        assertTrue(bounds[2] >= bounds[0], "right must not precede left: ${bounds.toList()}")
        assertTrue(bounds[3] >= bounds[1], "bottom must not precede top: ${bounds.toList()}")
        assertTrue(!hasSampleRegion(bounds), "a panel off the end of the backdrop has no region")
    }

    @Test
    fun testAPanelOverTheBackdropHasARegion() {
        val bounds = sampleBounds(
            pad = 78f,
            delta = Offset(53f, 300f),
            panelSize = Size(974f, 289f),
            sourceSize = Size(1080f, 2400f),
        )
        assertTrue(hasSampleRegion(bounds), "bounds were ${bounds.toList()}")
        // Half a pixel of inset on every edge keeps bilinear filtering off the boundary.
        assertEquals(78f - 53f + 0.5f, bounds[0], 0.01f)
    }
}

class GlassPathFieldTest {

    @Test
    fun testTheDistanceFieldIsSignedAndZeroOnTheOutline() {
        // A 200x200 square inside a 300x300 layer, so there is margin on every side.
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(200f, 0f)
            lineTo(200f, 200f)
            lineTo(0f, 200f)
            close()
        }
        val field = buildPathField(path, layerWidth = 300, layerHeight = 300, pad = 50f, band = 60f)
        assertNotNull(field, "a closed path should produce a field")

        val pixels = field.bitmap.toPixelMap()
        fun distanceAt(layerX: Int, layerY: Int): Float {
            val x = (layerX * field.scale).toInt().coerceIn(0, pixels.width - 1)
            val y = (layerY * field.scale).toInt().coerceIn(0, pixels.height - 1)
            return (pixels[x, y].red - 0.5f) * 2f * field.range
        }

        // The square occupies layer coordinates 50..250 on both axes.
        val centre = distanceAt(150, 150)
        val outside = distanceAt(10, 150)
        assertTrue(centre < -20f, "the centre should be well inside, was $centre")
        assertTrue(outside > 10f, "a point clear of the shape should be outside, was $outside")

        // On the outline the field crosses zero. Half-resolution plus eight bits puts the
        // quantisation near a pixel, so a couple of pixels of tolerance is the honest bar.
        val onEdge = distanceAt(50, 150)
        assertTrue(abs(onEdge) < 6f, "the outline should be near zero, was $onEdge")
    }

    @Test
    fun testDistanceGrowsWithDepthRatherThanSaturating() {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(200f, 0f)
            lineTo(200f, 200f)
            lineTo(0f, 200f)
            close()
        }
        val field = buildPathField(path, layerWidth = 300, layerHeight = 300, pad = 50f, band = 60f)!!
        val pixels = field.bitmap.toPixelMap()
        fun distanceAt(layerX: Int, layerY: Int): Float {
            val x = (layerX * field.scale).toInt().coerceIn(0, pixels.width - 1)
            val y = (layerY * field.scale).toInt().coerceIn(0, pixels.height - 1)
            return (pixels[x, y].red - 0.5f) * 2f * field.range
        }
        // Stepping inward from the left edge, the field has to fall monotonically — this is
        // what the refraction band reads to know how far in it is, so a plateau here would
        // flatten the bend across the band instead of ramping it.
        val a = distanceAt(55, 150)
        val b = distanceAt(70, 150)
        val c = distanceAt(90, 150)
        assertTrue(a > b && b > c, "expected a monotone ramp inward, got $a, $b, $c")
    }
}

class GlassStyleMorphTest {

    @Test
    fun testInterpolationHitsBothEnds() {
        assertEquals(GlassStyle.Regular, lerpGlassStyle(GlassStyle.Regular, GlassStyle.Thick, 0f))
        assertEquals(GlassStyle.Thick, lerpGlassStyle(GlassStyle.Regular, GlassStyle.Thick, 1f))
    }

    @Test
    fun testTheEdgeParametersTravelWithTheMorph() {
        // The falsifiable claim from the motion model: an element that grows must widen its
        // band and deepen its scatter. Holding them constant is the observable failure, so a
        // midpoint that still reads as the start style would be a bug.
        val mid = lerpGlassStyle(GlassStyle.Regular, GlassStyle.Thick, 0.5f)
        assertTrue(mid.blurRadius > GlassStyle.Regular.blurRadius)
        assertTrue(mid.blurRadius < GlassStyle.Thick.blurRadius)
        assertTrue(mid.refractionBand != GlassStyle.Regular.refractionBand)
        assertTrue(mid.innerShadow > GlassStyle.Regular.innerShadow)
    }

    @Test
    fun testDecisionsSnapRatherThanBlend() {
        // invertsWithBackdrop is a decision, not a quantity; a half-inverted element is not a
        // state anything wants to be in.
        val early = lerpGlassStyle(GlassStyle.Regular, GlassStyle.Thick, 0.2f)
        val late = lerpGlassStyle(GlassStyle.Regular, GlassStyle.Thick, 0.8f)
        assertEquals(GlassStyle.Regular.invertsWithBackdrop, early.invertsWithBackdrop)
        assertEquals(GlassStyle.Thick.invertsWithBackdrop, late.invertsWithBackdrop)
    }
}

class GlassShadowTest {

    @Test
    fun testShadowDeepensWithSizeAndWithBusyContent() {
        // Apple's direction of effect: bigger sits further off its ground, and a shadow over
        // scrolling text is more prominent than one over a flat surface.
        assertTrue(glassShadow(sizeFactor = 1f).blurRadius > glassShadow(sizeFactor = 0f).blurRadius)
        assertTrue(glassShadowAlpha(contrast = 1f) > glassShadowAlpha(contrast = 0f))
    }
}

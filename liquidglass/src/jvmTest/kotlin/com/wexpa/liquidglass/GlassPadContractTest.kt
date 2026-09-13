package com.wexpa.liquidglass

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The padded layer has one origin. The recording, the shader's uPad, the sampled distance field
 * and the draw-back translate all take their pad from [GlassStyle.recordPad], and that pad is a
 * whole number of layer pixels in the space the layer is recorded in. A field rasterised at
 * ceil(padPx) while the layer was recorded at ceil(pad * renderScale) / renderScale sat 23 versus
 * 24 full-resolution pixels apart for a 22.5 px pad at half scale.
 */
class GlassPadContractTest {
    private val density = Density(density = 3f, fontScale = 1f)
    private val size = Size(480f, 132f)
    private val radii = FloatArray(4) { 66f }

    private fun wholeLayerPixels(pad: Float, rs: Float): Boolean {
        val layer = pad * rs
        return kotlin.math.abs(layer - kotlin.math.round(layer)) < 1e-3f
    }

    @Test
    fun recordPadIsWholeLayerPixelsAtEveryRenderScale() {
        for (rs in listOf(1f, 0.75f, 0.5f, 0.25f)) {
            for (style in listOf(GlassStyle.Regular, GlassStyle.Thick, GlassStyle.Regular.copy(profile = GlassProfile.Held))) {
                val pad = style.recordPad(radii, size, fuse = null, density = density, renderScale = rs)
                assertTrue(wholeLayerPixels(pad, rs), "pad $pad at renderScale $rs is not whole layer pixels")
                assertTrue(pad >= style.padPx(density), "pad $pad is below the kernel reach ${style.padPx(density)}")
            }
        }
    }

    @Test
    fun heldProfileRecordsTheOutwardSamplingMargin() {
        val plain = GlassStyle.Regular.recordPad(radii, size, null, density, 1f)
        val held = GlassStyle.Regular.copy(profile = GlassProfile.Held).recordPad(radii, size, null, density, 1f)
        val band = GlassStyle.Regular.copy(profile = GlassProfile.Held).bandPx(radii, size, density)
        assertTrue(held >= plain, "held pad $held must not be below the plain pad $plain")
        assertTrue(held >= band * 0.3f, "held pad $held must cover 0.3 of the band $band")
    }

    @Test
    fun theWorkedExampleRoundsUpInLayerSpace() {
        // 22.5 px at half scale is 11.25 layer px: rounds to 12 layer px = 24 full-resolution px,
        // for the field and the recording alike (never 23 for one and 24 for the other).
        val rs = 0.5f
        val pad = kotlin.math.ceil(22.5f * rs) / rs
        assertEquals(24f, pad)
        assertTrue(wholeLayerPixels(pad, rs))
    }
}

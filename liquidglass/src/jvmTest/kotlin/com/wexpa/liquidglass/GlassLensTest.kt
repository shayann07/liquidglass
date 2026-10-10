package com.wexpa.liquidglass

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.*

class GlassLensTest {
    @Test fun materialAndForegroundUseTheSameLensAtEveryPixel() {
        val ramp: (Int, Int) -> Int = { x, _ -> -0x1000000 or (x shl 16) or (x shl 8) or x }
        val image = GlassEndpointRender.render(160, 160, 32, profile = 3f, heldMagnification = .2f,
            backdrop = ramp, ink1 = ramp)
        for (y in 34..189) for (x in 34..189) {
            val i = image.index(x, y)
            assertTrue(abs(GlassRender.luma(image.b1[i]) - GlassRender.luma(image.ink[i])) <= 1.01,
                "backdrop/foreground split at $x,$y")
        }
    }
    private fun ramp(profile: Float, strength: Float, size: Int = 160) = GlassRender.render(
        width = size, height = size, profile = profile, heldLens = 1f,
        heldMagnification = strength, heldGlow = 0f,
        backdrop = { x, _ -> val c = (x * 255f / (size + 63)).roundToInt(); -0x1000000 or (c shl 16) or (c shl 8) or c })

    @Test fun continuousLensHasNoHiddenAnnulusOrReversedSourceAcrossItsDiameter() {
        for (size in listOf(80, 160, 320)) for (zoom in listOf(1f, 1.25f, 1.6f, 2.5f)) {
            val strength = 1f - 1f / zoom
            val pixels = ramp(3f, strength, size)
            val width = size + 64
            val row = (33 until size + 31).map { GlassRender.luma(pixels[(32 + size / 2) * width + it]) }
            val steps = row.zipWithNext { a, b -> b - a }
            assertTrue(steps.all { it >= -1.01 }, "backwards source: size=$size zoom=$zoom ${steps.min()}")
            assertTrue(steps.max() <= 255f / width * 1.9f + 1.1, "internal rim jump: ${steps.max()}")
            // The boundary converges to identity rather than jumping to another image band.
            for (x in listOf(34, size + 29)) {
                val expected = x * 255f / (size + 63)
                assertTrue(abs(GlassRender.luma(pixels[(32 + size / 2) * width + x]) - expected) < 2.1)
            }
        }
    }

    @Test fun magnifiesBackdropAtTheAdvertisedCentreGain() {
        val pixels = ramp(3f, .2f)
        val row = 112 * 224
        val observed = (GlassRender.luma(pixels[row + 117]) - GlassRender.luma(pixels[row + 107])) / (10 * 255.0 / 223)
        assertTrue(observed in .73.. .88, "1.25x should sample about .8 source pixels per output pixel: $observed")
    }

    @Test fun rendersRegressionEvidenceFromProductionShader() {
        val directory = File("build/reports/atlas").apply { mkdirs() }
        for ((name, profile) in listOf("held" to 2f, "continuous" to 3f)) {
            val pixels = GlassRender.render(240, 240, profile = profile, heldLens = 1f, heldMagnification = .2f,
                heldGlow = 0f, backdrop = { x, y ->
                    if (x % 16 < 2 || y % 16 < 2) 0xffa8d0de.toInt() else 0xff162a3a.toInt()
                })
            val image = BufferedImage(304, 304, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 0, 304, 304, pixels, 0, 304)
            ImageIO.write(image, "png", File(directory, "$name-grid.png"))
        }
    }

    @Test fun invalidZoomIsRejectedAtThePublicBoundary() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, .99f, 2.51f)) assertFailsWith<IllegalArgumentException> { GlassStyle.clearLens(value) }
    }
}

class GlassCalmTest {
    @Test fun responseIsGradualAndMonotoneWithoutSpringOvershoot() {
        val animation = TargetBasedAnimation(GlassMotion.CalmDown, Float.VectorConverter, 0f, 1f)
        val samples = (0..1200 step 10).map { animation.getValueFromNanos(it * 1_000_000L) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a - .0001f })
        assertTrue(samples.all { it in 0f..1.0001f })
        assertTrue(samples[10] in .3f.. .6f, "press should still be forming at 100ms")
        assertTrue(samples.last() > .999f)
    }

    @Test fun ordinaryTravelDoesNotReachTheExtremeAndHugeTravelStaysBounded() {
        val interaction = GlassInteraction.Calm
        fun strain(travel: Float): Float {
            val gain = glassPullTargetScale(abs(travel), interaction.pullFollow, 50f)
            return glassPullDeformation(travel * gain, 0f, 100f, interaction.pullElongation,
                interaction.pullWidthRatio, interaction.pullFollow).along - 1f
        }
        assertTrue(strain(100f) in .02f.. .031f)
        assertTrue(strain(300f) > strain(100f) * 1.5f)
        for (distance in listOf(1000f, 1000000f, -1000000f)) assertTrue(strain(distance) in 0f.. .0751f)
        assertEquals(strain(300f), strain(-300f))
    }
}

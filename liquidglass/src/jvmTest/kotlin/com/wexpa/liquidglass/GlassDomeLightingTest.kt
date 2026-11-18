package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #214: the dome field is a refraction device. It must bend the backdrop, and it must leave the lighting (facing,
 * highlights, edge shading, the Fresnel term) on the true-distance normal and slope.
 *
 * The same shader is rendered twice, once as shipped and once with the dome weight forced to zero. Over a flat grey
 * backdrop refraction cannot show, so every difference there is lighting; over stripes the refraction must differ.
 */
class GlassDomeLightingTest {

    /** The shipped shader with the aspect term of the dome weight multiplied by zero: no dome, nothing else changed. */
    private val domeOff = GLASS_SHADER_SOURCE.replace(
        "smoothstep(1.5, 3.0, aspect) *",
        "0.0 * smoothstep(1.5, 3.0, aspect) *",
    ).also { assertTrue(it != GLASS_SHADER_SOURCE, "the dome weight line must be found to be switched off") }

    private val pad = 30
    private val w = 480
    private val h = 80

    private fun render(source: String, backdrop: (Int, Int) -> Int) = GlassRender.render(
        width = w, height = h, pad = pad,
        refractBand = 40f, // the inradius: the band reaches the axis, so the dome is fully weighted
        refractDepth = 20f,
        specular = 0.6f, fresnel = 0.6f, innerShadow = 0.3f, edgeShadow = 0.25f, highlightChroma = 0.3f,
        shaderSource = source,
        backdrop = backdrop,
    )

    @Test
    fun testTheDomeDoesNotChangeTheLighting() {
        val grey = { _: Int, _: Int -> 0x808080 }
        val with = render(GLASS_SHADER_SOURCE, grey)
        val without = render(domeOff, grey)
        var worst = 0
        for (i in with.indices) {
            for (shift in intArrayOf(16, 8, 0)) {
                worst = maxOf(worst, abs(((with[i] shr shift) and 0xFF) - ((without[i] shr shift) and 0xFF)))
            }
        }
        assertEquals(0, worst, "lighting over a flat backdrop must not depend on the dome weight; worst channel difference $worst")
    }

    @Test
    fun testTheDomeStillChangesTheRefraction() {
        val stripes = { x: Int, _: Int -> if ((x / 7) % 2 == 0) 0xFFFFFF else 0x000000 }
        val with = render(GLASS_SHADER_SOURCE, stripes)
        val without = render(domeOff, stripes)
        val lw = w + pad * 2
        val axis = pad + h / 2
        var differing = 0
        for (x in pad + 40 until pad + w - 40) {
            if (abs(GlassRender.luma(with[axis * lw + x]) - GlassRender.luma(without[axis * lw + x])) > 20.0) differing++
        }
        assertTrue(differing > 20, "the dome must still reshape the refraction on the spine; only $differing pixels differ")
    }
}

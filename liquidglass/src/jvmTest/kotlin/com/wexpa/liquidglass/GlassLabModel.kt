package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp

/**
 * The measured forward model of iOS 27 regular glass at Tint 50, as `tools/glass_model.py` states it
 * (#199, #200): per channel in display code values, `lin = B + k (D - B) + c |D - B|`, then the
 * appearance's tone, B a Gaussian of 15.5 pt and D one of 0.83 px at 3x. Greys only; colour is #201.
 */
internal object GlassLabModel {
    val greyIn = intArrayOf(0, 16, 32, 48, 64, 80, 96, 112, 128, 144, 160, 176, 192, 208, 224, 240, 255)
    /** The dark glass's measured tone over flat grey, the 17 points of the lab stills. */
    val darkTone = doubleArrayOf(32.0, 47.0, 62.0, 76.0, 89.0, 102.0, 113.0, 124.0, 134.0, 143.0, 152.0, 159.0, 166.0, 172.0, 177.0, 182.0, 185.0)
    const val SIGMA_BASE_PX = 46.5
    const val SIGMA_DETAIL_PX = 0.83

    fun k(dark: Boolean) = if (dark) 0.277 else 0.273
    fun c(dark: Boolean) = if (dark) -0.22 else 0.22

    /** The measured tone: the dark table interpolated, the light law clipped at 255. */
    fun tone(v: Double, dark: Boolean): Double {
        val x = v.coerceIn(0.0, 255.0)
        if (!dark) return minOf(132.5 + 0.502 * x, 255.0)
        var i = 0
        while (i < greyIn.size - 2 && greyIn[i + 1] < x) i++
        val f = (x - greyIn[i]) / (greyIn[i + 1] - greyIn[i])
        return darkTone[i] + (darkTone[i + 1] - darkTone[i]) * f
    }

    /** The model over a grey backdrop [w] x [h], row-major code values; edges replicated, as the scorer does. */
    fun predict(grey: DoubleArray, w: Int, h: Int, dark: Boolean): DoubleArray {
        val b = blur(grey, w, h, SIGMA_BASE_PX)
        val d = blur(grey, w, h, SIGMA_DETAIL_PX)
        return DoubleArray(w * h) { i ->
            val diff = d[i] - b[i]
            tone(b[i] + k(dark) * diff + c(dark) * abs(diff), dark)
        }
    }

    private fun blur(src: DoubleArray, w: Int, h: Int, sigma: Double): DoubleArray {
        val r = ceil(3.5 * sigma).toInt()
        val kernel = DoubleArray(2 * r + 1) { exp(-0.5 * ((it - r) / sigma) * ((it - r) / sigma)) }
        val sum = kernel.sum()
        for (j in kernel.indices) kernel[j] /= sum
        val tmp = DoubleArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0.0
            for (j in kernel.indices) s += kernel[j] * src[y * w + (x + j - r).coerceIn(0, w - 1)]
            tmp[y * w + x] = s
        }
        val out = DoubleArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0.0
            for (j in kernel.indices) s += kernel[j] * tmp[(y + j - r).coerceIn(0, h - 1) * w + x]
            out[y * w + x] = s
        }
        return out
    }
}

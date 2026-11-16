package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The detail copy is skipped only when nothing reads it. The one-sided term c |D - B| reads the softened copy as the
 * share k does, so a style with k = 0 and c != 0 still needs the four-tap kernel: over 1 px stripes the kernel cancels
 * the stripes (its taps at +-1 px land on the other colour along x and the same colour along y, so D is their mean,
 * which is B), and c adds nothing; with the sharp copy in its place |D - B| is half the stripes' contrast and c lifts
 * the whole interior. Checked through the production host, for a lone piece and a container's member: with the sharp
 * copy c 0.2 lifted the interior 14.9 levels; with the kernel it leaves about 2, because the quarter-scale wide copy of
 * 1 px stripes does not land exactly on their mean, so |D - B| is not quite 0.
 */
class GlassDetailKernelGuardTest {
    private fun GlassLabScene.zoneMean(): Double {
        val out = render(IntArray(width * height) { i -> GlassLabScene.grey(if (i % width % 2 == 0) 0 else 255) })
        return zoneRows.flatMap { y -> zoneColumns.flatMap { xs -> xs.map { x -> (out[y * width + x] ushr 8) and 0xFF } } }
            .average()
    }

    @Test
    fun theOneSidedTermKeepsTheDetailKernelWhenTheShareIsZero() {
        val failures = mutableListOf<String>()
        for (container in listOf(false, true)) {
            val base = GlassStyle.inApp(dark = true).copy(fineShare = 0f, toneCurve = 0f)
            check(GlassMaterial.usesDetailCross(base.blurRadius)) { "the style must select the four-tap kernel" }
            val (off, on) = listOf(0f, 0.2f).map { c ->
                GlassLabScene(base.copy(detailLift = c), container = container).use { it.zoneMean() }
            }
            val ok = abs(on - off) <= 6.0
            val line = "${if (container) "container" else "alone"} 1 px stripes, k 0: c 0 %.2f, c 0.2 %.2f".format(off, on)
            println((if (ok) "GUARD ok   " else "GUARD FAIL ") + line)
            if (!ok) failures += line
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }
}

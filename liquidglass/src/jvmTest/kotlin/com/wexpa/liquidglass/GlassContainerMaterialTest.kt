package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Merged glass ([LiquidGlassContainer], under [GlassEffectContainer]) draws the same measured material as a lone
 * piece (#200): the same style at the same place, once through [liquidGlass] and once as a container's only member.
 * The container's shader once kept the along-axis detail filter after the panel shader dropped it, and none of the
 * three-term uniforms: over mid-grey its dark Regular glass sat about 25 levels darker (no tone bow) and a text stem
 * showed the stepped copies of #190.
 */
class GlassContainerMaterialTest {
    private val failures = mutableListOf<String>()

    private fun check(ok: Boolean, message: String) {
        println((if (ok) "CONTAINER ok   " else "CONTAINER FAIL ") + message)
        if (!ok) failures += message
    }

    private fun GlassLabScene.zoneMean(grey: Int): Double {
        val out = render(IntArray(width * height) { GlassLabScene.grey(grey) })
        return zoneRows.flatMap { y -> zoneColumns.flatMap { xs -> xs.map { x -> (out[y * width + x] ushr 8) and 0xFF } } }
            .average()
    }

    @Test
    fun aContainerDrawsTheSameMaterialAsALonePiece() {
        val greys = listOf(0, 128, 255)
        for (dark in listOf(true, false)) {
            val app = if (dark) "dark" else "light"
            // What Glass.Regular resolves to, and so what GlassEffectContainer draws by default.
            val style = GlassStyle.inApp(dark = dark)
            val alone = GlassLabScene(style).use { s -> greys.map { s.zoneMean(it) } }
            val merged = GlassLabScene(style, container = true).use { s -> greys.map { s.zoneMean(it) } }
            // Over black both materials lift the page: the container drew its member, where the lone piece is.
            check(merged[0] > 15.0, "$app container over black: %.1f (the member is not drawn)".format(merged[0]))
            greys.forEachIndexed { i, v ->
                check(
                    abs(alone[i] - merged[i]) <= 2.0,
                    "$app over grey $v: alone %.1f, container %.1f".format(alone[i], merged[i]),
                )
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} container checks failed:\n" + failures.joinToString("\n"))
    }

    @Test
    fun aContainerShowsOneSoftCopyOfText() {
        // A 30 px black stem on an off-white page at the pill's centre, at the measured Tint 50 and at the ends, where
        // the earlier fit's larger detail share made the old filter's copies plain (9 turning points at Tint 0). One soft
        // stem at every tint; and the container's profile across the interior is the lone piece's within 2 levels, except
        // at Tint 0: there the earlier fit's legibility (0.6) raises the panel's tint at the stem's edges, a term the
        // container shader does not carry (about 25 levels in the two edge columns).
        for (dark in listOf(true, false)) for (tint in listOf(0f, 50f, 100f)) {
            val app = "${if (dark) "dark" else "light"} tint ${tint.toInt()}"
            val style = GlassStyle.inApp(dark = dark, tintAmount = tint)
            val alone = GlassLabScene(style).use { it.stemProfile() }
            val merged = GlassLabScene(style, container = true).use { it.stemProfile() }
            val at = alone.indices.maxBy { abs(alone[it] - merged[it]) }
            val worst = abs(alone[at] - merged[at])
            val turns = GlassLabScene.turningPoints(merged)
            check(
                (worst <= 2.0 || tint == 0f) && turns <= 7,
                "$app text stem: container differs from the lone piece by up to %.1f levels (%d px from the stem's centre), %d turning points (alone %d)"
                    .format(worst, at + 100 - 316, turns, GlassLabScene.turningPoints(alone)),
            )
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }
}

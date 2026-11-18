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
        // stem at every tint; and the container's profile across the interior is the lone piece's within 2 levels at every tint,
        // Tint 0 included since the container carries the legibility term (#211; it was exempt, about 25 levels in the two edge columns).
        for (dark in listOf(true, false)) for (tint in listOf(0f, 50f, 100f)) {
            val app = "${if (dark) "dark" else "light"} tint ${tint.toInt()}"
            val style = GlassStyle.inApp(dark = dark, tintAmount = tint)
            val alone = GlassLabScene(style).use { it.stemProfile() }
            val merged = GlassLabScene(style, container = true).use { it.stemProfile() }
            val at = alone.indices.maxBy { abs(alone[it] - merged[it]) }
            val worst = abs(alone[at] - merged[at])
            val turns = GlassLabScene.turningPoints(merged)
            check(
                worst <= 2.0 && turns <= 7,
                "$app text stem: container differs from the lone piece by up to %.1f levels (%d px from the stem's centre), %d turning points (alone %d)"
                    .format(worst, at + 100 - 316, turns, GlassLabScene.turningPoints(alone)),
            )
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    @Test
    fun aContainerCapsuleFoldsAlongItsRunAsALonePieceDoes() {
        // #216: a capsule on the measured resting map rests on that map along its straight runs. Over 24 px horizontal
        // stripes the run's outer band of a container's member must match the lone piece's; before the container carried
        // the rule its run showed the stripes nearly in place, tens of levels from the lone piece.
        for (dark in listOf(true, false)) {
            val app = if (dark) "dark" else "light"
            val style = GlassStyle.inApp(dark = dark)
            fun band(scene: GlassLabScene): List<Int> {
                val stripes = IntArray(scene.width * scene.height) { i ->
                    if (((i / scene.width) / 24) % 2 == 0) GlassLabScene.grey(0) else GlassLabScene.grey(255)
                }
                val out = scene.render(stripes)
                val r = scene.pillH / 2
                val xs = scene.pillX + r + 30 until scene.pillX + r + 130
                val ys = (scene.pillY + 4 until scene.pillY + 40) + (scene.pillY + scene.pillH - 40 until scene.pillY + scene.pillH - 4)
                return ys.flatMap { y -> xs.map { x -> (out[y * scene.width + x] ushr 8) and 0xFF } }
            }
            val alone = GlassLabScene(style).use { band(it) }
            val merged = GlassLabScene(style, container = true).use { band(it) }
            val rms = kotlin.math.sqrt(alone.indices.sumOf { i -> (alone[i] - merged[i]).toDouble().let { it * it } } / alone.size)
            check(rms <= 3.0, "$app capsule run over stripes: container differs from the lone piece by %.2f levels rms".format(rms))
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    private fun GlassLabScene.zoneColour(argb: Int): DoubleArray {
        val out = render(IntArray(width * height) { argb })
        val zone = zoneRows.flatMap { y -> zoneColumns.flatMap { xs -> xs.map { x -> out[y * width + x] } } }
        return DoubleArray(3) { ch -> zone.map { ((it ushr (16 - 8 * ch)) and 0xFF).toDouble() }.average() }
    }

    @Test
    fun aContainerKeepsColourLikeALonePiece() {
        // The measured colour transfer (#201) over flat saturated colour: the container must carry the panel shader's
        // correction, or its member falls back to the per-channel grey tone (12 to 102 levels away on these pages, 17
        // November); without the bow taken on the luma, dark glass misses by up to 21.
        val pages = listOf(
            "red" to 0xFFFF0000.toInt(), "green" to 0xFF00FF00.toInt(), "blue" to 0xFF0000FF.toInt(),
            "magenta" to 0xFFFF00FF.toInt(), "yellow" to 0xFFFFFF00.toInt(), "dark magenta" to 0xFF800080.toInt(),
        )
        for (dark in listOf(true, false)) {
            val app = if (dark) "dark" else "light"
            val style = GlassStyle.inApp(dark = dark)
            val alone = GlassLabScene(style).use { s -> pages.map { s.zoneColour(it.second) } }
            val merged = GlassLabScene(style, container = true).use { s -> pages.map { s.zoneColour(it.second) } }
            pages.forEachIndexed { i, (name, _) ->
                val worst = (0..2).maxOf { abs(alone[i][it] - merged[i][it]) }
                check(
                    worst <= 2.0,
                    "$app over $name: alone %.1f %.1f %.1f, container %.1f %.1f %.1f".format(
                        alone[i][0], alone[i][1], alone[i][2], merged[i][0], merged[i][1], merged[i][2],
                    ),
                )
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} container colour checks failed:\n" + failures.joinToString("\n"))
    }
}

package com.wexpa.liquidglass

import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reference trajectories of the Calm selector for the CanvasKit and .NET ports
 * (ports/skia/selector.mjs, ports/dotnet/LiquidGlass.Skia/CalmSelector.cs). Each scenario is the
 * exact controller call sequence GlassTabBar issues for a gesture, sampled at 60 Hz. Regenerate
 * with -Pliquidglass.exportShaders=true; otherwise the committed fixture must match.
 */
class GlassSelectorPortFixtureTest {
    /** The Phone comparison bar at 3 px/dp: 278 x 62 dp, 8 dp item inset, 95 dp pill, 4 dp pill inset. */
    private val bar: GlassSelectorBar = run {
        val width = 834f; val height = 186f; val inset = 24f; val density = 3f
        val slot = (width - inset * 2f) / 3f
        val pill = max(285f, slot)
        val rest = min(pill / 2f, (inset + slot / 2f - density).coerceAtLeast(density))
        val half = min(height / 2f - 12f, rest)
        GlassSelectorBar(width, height, inset, 3, height / 2f, rest, half,
            max(half, height / 2f - density), half, rest, density, width)
    }

    private class Call(val t: Double, val op: String, val args: List<Float>)

    private fun scenario(name: String, end: Double, calls: List<Call>): String {
        val c = GlassPoseController(GlassPoseSpec.Calm)
        c.attach(bar, null, null)
        c.snapToRest(calls.firstOrNull { it.op == "rest" }?.args?.get(0)?.toInt() ?: 0)
        val frames = StringBuilder()
        var next = 0
        val e = GlassPoseExtents()
        val frameCount = (end * 60).toInt()
        for (i in 1..frameCount) {
            val tf = i / 60.0
            while (next < calls.size && calls[next].t <= tf) {
                val call = calls[next++]
                val a = call.args
                when (call.op) {
                    "rest" -> {}
                    "down" -> c.pointerDown(a[0], a[1], call.t, true)
                    "move" -> c.pointerMove(a[0], a[1], call.t)
                    "drag" -> c.beginDrag(a[0], a[1])
                    "retarget" -> c.retarget(a[0].toInt())
                    "up" -> c.pointerUp(a[0].toInt())
                    else -> error(call.op)
                }
            }
            c.advanceFrameTo(tf.toFloat())
            c.extents(e)
            val f = c.frame
            val d = floatArrayOf(f.spine, f.radius, f.scaleX, f.band())
            if (frames.isNotEmpty()) frames.append(",\n")
            frames.append("[${"%.5f".format(tf)},\"${c.mode.name}\",${c.centreX},${c.centreY},${c.formation},${c.transitAppearance},${c.heldTravel}," +
                "${e.left},${e.right},${e.top},${e.bottom},${f.a.joinToString(",")},${f.aInv.joinToString(",")},${f.bx},${f.by},${d.joinToString(",")}," +
                "${if (c.tapFormed) 1 else 0}]")
        }
        val callJson = calls.joinToString(",") { call -> "[${call.t},\"${call.op}\",${call.args.joinToString(",")}]".replace(",]", "]") }
        return "{\"name\":\"$name\",\"end\":$end,\"calls\":[$callJson],\"frames\":[\n$frames]}"
    }

    private fun centre(i: Int) = bar.centreOf(i)

    private fun scenarios(): List<String> {
        val y = bar.centreY
        val list = mutableListOf<String>()
        // Taps: down aims the selector, the lift (a tap) cancels to the tapped tab.
        list += scenario("tap-adjacent", 1.2, listOf(
            Call(.1, "down", listOf(centre(1), y)), Call(.1, "retarget", listOf(1f)), Call(.16, "up", listOf(1f))))
        list += scenario("tap-far", 1.4, listOf(
            Call(.1, "down", listOf(centre(2), y)), Call(.1, "retarget", listOf(2f)), Call(.17, "up", listOf(2f))))
        // A held throw into the right wall: hold, drag after the threshold, release moving.
        run {
            val calls = mutableListOf(Call(.1, "down", listOf(centre(0), y)), Call(.1, "retarget", listOf(0f)))
            var t = .1
            while (t < .35) { t += .008; calls += Call(t, "move", listOf(centre(0), y)) }
            calls += Call(t, "drag", listOf(centre(0), y))
            val start = t
            while (t < start + .2) {
                t += .008
                val u = ((t - start) / .2).coerceIn(0.0, 1.0).toFloat()
                calls += Call(t, "move", listOf(centre(0) + (centre(2) + 60f - centre(0)) * u, y + 6f * u))
            }
            calls += Call(t, "up", listOf(2f))
            list += scenario("held-throw-wall", 1.6, calls)
        }
        // Held reversals between the end tabs, released on the first.
        run {
            val calls = mutableListOf(Call(.05, "down", listOf(centre(0), y)), Call(.05, "retarget", listOf(0f)))
            var t = .05
            while (t < .25) { t += .008; calls += Call(t, "move", listOf(centre(0), y)) }
            calls += Call(t, "drag", listOf(centre(0), y))
            val start = t
            while (t < start + 1.2) {
                t += .008
                val phase = (t - start) / .3 * Math.PI
                val x = centre(0) + (centre(2) - centre(0)) * (1 - kotlin.math.cos(phase)).toFloat() / 2f
                calls += Call(t, "move", listOf(x, y))
            }
            calls += Call(t, "up", listOf(0f))
            list += scenario("held-reversals", 2.0, calls)
        }
        // A stationary hold on the middle tab, then a lift without dragging.
        list += scenario("stationary-hold", 1.4, listOf(Call(.0, "rest", listOf(1f)),
            Call(.1, "down", listOf(centre(1), y)), Call(.1, "retarget", listOf(1f)), Call(.6, "up", listOf(1f))))
        return list
    }

    @Test fun calmSelectorTrajectoriesForPorts() {
        val json = "{\"bar\":{\"width\":834,\"height\":186,\"count\":3,\"inset\":24,\"pillWidth\":285,\"pillInset\":12,\"density\":3},\n" +
            "\"frameFields\":[\"t\",\"mode\",\"cx\",\"cy\",\"formation\",\"transit\",\"heldTravel\",\"left\",\"right\",\"top\",\"bottom\"," +
            "\"a0\",\"a1\",\"a2\",\"a3\",\"i0\",\"i1\",\"i2\",\"i3\",\"bx\",\"by\",\"spine\",\"radius\",\"scaleX\",\"band\",\"tapFormed\"],\n" +
            "\"scenarios\":[\n" + scenarios().joinToString(",\n") + "]}\n"
        val file = File("../ports/skia/fixtures/selector-calm.json")
        if (System.getProperty("liquidglass.exportShaders") == "true") { file.parentFile.mkdirs(); file.writeText(json) }
        assertEquals(json.replace("\r\n", "\n"), file.readText().replace("\r\n", "\n"),
            "Regenerate the selector fixture with -Pliquidglass.exportShaders=true")
    }
}

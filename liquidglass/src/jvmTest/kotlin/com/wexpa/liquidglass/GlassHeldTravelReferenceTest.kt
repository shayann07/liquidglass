package com.wexpa.liquidglass

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spatial envelope only: IMG6735 has no recorded input timestamps. Since pass 12 (14 October) the
 * current Calm follows the owner's timed video2 instead, whose travelling lens flattens further
 * ([calmHeldTravelWidensAndFlattensLikeVideo2]); the IMG6735 envelope is kept on the retained
 * [GlassPoseSpec.CalmWallLanding].
 */
class GlassHeldTravelReferenceTest {
    private fun referenceBar() = GlassSelectorBar(1044f, 186f, 27f, 5, 93f, 115f, 81f, 90f,
        150f, 112.5f, 3f, 700f)

    @Test fun tuningHeldTravelDoesNotChangeTheRestingTapCurve() {
        val bar = referenceBar()
        fun controller(spec: GlassPoseSpec) = GlassPoseController(spec).also {
            it.attach(bar, null, null); it.snapToRest(0); it.retarget(4)
        }
        val current = controller(GlassPoseSpec.Calm)
        val previous = controller(GlassPoseSpec.Calm.copy(
            heldTravelPressureOmega = 0f, squeeze = .10f, elongationSlots = .45f))
        for (i in 0..480) {
            current.advanceTo(i / 480f); previous.advanceTo(i / 480f)
            val a = GlassPoseExtents().also(current::extents)
            val b = GlassPoseExtents().also(previous::extents)
            assertEquals(b.width, a.width, .001f, "held gain clipped independent tap shape")
            assertEquals(b.height, a.height, .001f, "held gain changed tap recoil")
        }
    }

    @Test fun ordinaryHoldGrowthIsUnchangedAndEarlyFastTravelStaysBounded() {
        val bar = referenceBar()
        fun controller(spec: GlassPoseSpec) = GlassPoseController(spec).also {
            it.attach(bar, null, null); it.snapToRest(0)
            it.pointerDown(bar.centreOf(0), bar.centreY, 0.0, true)
        }
        val current = controller(GlassPoseSpec.Calm)
        val previous = controller(GlassPoseSpec.Calm.copy(
            heldTravelPressureOmega = 0f, squeeze = .10f, elongationSlots = .45f))
        for (i in 0..480) {
            current.advanceTo(i / 480f); previous.advanceTo(i / 480f)
            val a = GlassPoseExtents().also(current::extents)
            val b = GlassPoseExtents().also(previous::extents)
            assertEquals(b.width, a.width, .001f, "ordinary press width changed")
            assertEquals(b.height, a.height, .001f, "ordinary press height changed")
        }
        for (wait in listOf(0f, .06f, .12f, .8f)) {
            val c = controller(GlassPoseSpec.CalmWallLanding)
            c.advanceTo(wait); c.beginDrag(bar.centreOf(0), bar.centreY)
            for (i in 1..120) {
                val elapsed = i / 480f
                val progress = (elapsed / .06f).coerceAtMost(1f)
                c.pointerMove(bar.centreOf(0) + (bar.centreOf(4) - bar.centreOf(0)) * progress,
                    bar.centreY, (wait + elapsed).toDouble())
                c.advanceFrameTo(wait + elapsed)
                val e = GlassPoseExtents().also(c::extents)
                assertTrue(e.height >= 136f, "early travel collapsed below the original bound")
                assertTrue(e.height <= bar.height * 1.23f)
                assertFalse(c.solverFailed)
            }
        }
    }

    @Test fun strongTravelCanReachTheOriginalFlattenedPairWithoutIncreasingOffAxisPull() {
        val bar = referenceBar()
        val rows = arrayListOf("duration,time,width,height,phase")
        var best = Float.POSITIVE_INFINITY
        var pairedWidth = 0f
        var pairedHeight = 0f
        var bestDuration = 0f
        var minimumHeight = Float.POSITIVE_INFINITY
        var middleError = Float.POSITIVE_INFINITY
        var middleWidth = 0f
        var middleHeight = 0f
        for (duration in listOf(.06f, .08f, .10f, .14f, .20f, .30f, .50f, 1f, 1.5f)) {
            val controller = GlassPoseController(GlassPoseSpec.CalmWallLanding)
            controller.attach(bar, null, null); controller.snapToRest(0)
            controller.pointerDown(bar.centreOf(0), bar.centreY, 0.0, true)
            for (i in 1..384) controller.advanceTo(i / 480f)
            controller.beginDrag(bar.centreOf(0), bar.centreY)
            val extent = GlassPoseExtents()
            for (i in 1..960) {
                val elapsed = i / 480f
                if (elapsed <= duration) {
                    val x = bar.centreOf(0) + (bar.centreOf(4) - bar.centreOf(0)) * elapsed / duration
                    controller.pointerMove(x, bar.centreY, (.8f + elapsed).toDouble())
                }
                controller.advanceFrameTo(.8f + elapsed); controller.extents(extent)
                assertTrue(controller.isHeld)
                assertFalse(controller.solverFailed)
                val phase = (controller.centreX - bar.centreOf(0)) / (bar.centreOf(4) - bar.centreOf(0))
                rows += "$duration,$elapsed,${extent.width},${extent.height},$phase"
                val error = abs(extent.width - 314f) + abs(extent.height - 142f)
                if (error < best) {
                    best = error; pairedWidth = extent.width; pairedHeight = extent.height
                    bestDuration = duration
                }
                val intermediate = abs(extent.width - 326f) + abs(extent.height - 174f)
                if (intermediate < middleError) {
                    middleError = intermediate; middleWidth = extent.width; middleHeight = extent.height
                }
                minimumHeight = minOf(minimumHeight, extent.height)
            }
            assertEquals(223.2f, extent.height, .5f, "stationary hold failed to recover")
        }
        File("build/reports/atlas").apply { mkdirs() }.resolve("held-travel-reference.csv")
            .writeText(rows.joinToString("\n") + "\n")
        println("HELD_PAIR width=$pairedWidth height=$pairedHeight duration=$bestDuration minHeight=$minimumHeight")
        println("HELD_INTERMEDIATE width=$middleWidth height=$middleHeight")
        // Untimed strong-travel IMG6735: outer bar186px, selector314x142px; optical
        // edge ambiguity admits6px. Authored speed sweep tests reachability, not input timing.
        assertEquals(314f, pairedWidth, 6f, "strong travel width")
        assertEquals(142f, pairedHeight, 6f, "strong travel cannot flatten to the original")
        assertTrue(minimumHeight >= 136f, "travel exceeds the selected conservative compression bound")
        assertEquals(326f, middleWidth, 6f, "intermediate travel remains too narrow")
        assertEquals(174f, middleHeight, 6f, "intermediate travel height")
    }

    /**
     * Video2's fast held reversal (18.58-19.05 s, the inferred finger in
     * tools/reference-replay/v2-script.txt, bar-local x = video x - 63) on the Phone bar: iOS's
     * travelling lens is 458 x 145 px at 19.0 s on a 192 px bar, 0.755 of its height, from a held
     * still lens of about 340 x 215. The held travel strain widens and flattens it at about
     * constant area; a still hold and an ordinary press are unchanged, and the first throw's quick
     * flick releases before the strain has built.
     */
    @Test fun calmHeldTravelWidensAndFlattensLikeVideo2() {
        val bar = run {
            val width = 834f; val height = 186f; val inset = 24f; val density = 3f
            val slot = (width - inset * 2f) / 3f
            val rest = minOf(285f / 2f, (inset + slot / 2f - density).coerceAtLeast(density))
            val half = minOf(height / 2f - 12f, rest)
            GlassSelectorBar(width, height, inset, 3, height / 2f, rest, half,
                maxOf(half, height / 2f - density), half, rest, density, width)
        }
        val events = File("../tools/reference-replay/v2-script.txt").readLines().mapNotNull { line ->
            val w = line.trim().split(Regex("""\s+"""))
            if (w.size == 4 && w[1] in setOf("down", "move", "up")) Triple(w[0].toInt(), w[1], w[2].toFloat()) else null
        }
        fun run(spec: GlassPoseSpec, from: Int, until: Int, selected: Int, sample: (Double, GlassPoseController) -> Unit) {
            val c = GlassPoseController(spec)
            c.attach(bar, null, null); c.snapToRest(selected)
            val window = events.filter { it.first in from..until }
            var next = 0
            var frame = (from / 1000.0 * 60).toInt()
            while (frame / 60.0 <= until / 1000.0) {
                val t = frame / 60.0
                while (next < window.size && window[next].first / 1000.0 <= t) {
                    val (ms, op, x) = window[next++]
                    when (op) {
                        "down" -> c.pointerDown(x - 63f, bar.centreY, ms / 1000.0, true)
                        "move" -> c.pointerMove(x - 63f, bar.centreY, ms / 1000.0)
                        "up" -> c.pointerUp(selected)
                    }
                }
                c.advanceFrameTo(t.toFloat())
                assertFalse(c.solverFailed)
                sample(t, c)
                frame++
            }
        }
        val e = GlassPoseExtents()
        var travelling: Pair<Float, Float>? = null
        var still: Pair<Float, Float>? = null
        // The held still lens: video2's first hold, at 1.8 s.
        run(GlassPoseSpec.Calm, 1203, 1800, 0) { t, c ->
            if (abs(t - 1.8) < 1e-3) { c.extents(e); still = e.width to e.height }
        }
        run(GlassPoseSpec.Calm, 18580, 19000, 0) { t, c ->
            if (abs(t - 19.0) < 1e-3) { c.extents(e); travelling = e.width to e.height }
        }
        val (w0, h0) = still!!
        val (w1, h1) = travelling!!
        println("VIDEO2_REVERSAL still=${w0}x$h0 travelling=${w1}x$h1")
        // Probe-scale targets: iOS 458 x 145 on its 192 px bar is 444 x 140 on this 186 px bar.
        assertEquals(.755f, h1 / bar.height, .05f, "travelling lens height against the bar")
        assertTrue(w1 >= 410f, "travelling lens width $w1")
        // Bounding-box area against the held still lens: iOS (458 x 145) / (340 x 215) = 0.91;
        // ours 444 x 145 / 346 x 223 = 0.83 (pass 13; our still lens is 2-4 % larger than iOS's).
        assertEquals(.91f, w1 * h1 / (w0 * h0), .12f, "travelling lens area against the held still lens")
        // Without the strain the same reversal stays nearly full height (the pass 11 Calm: 368 x 185).
        run(GlassPoseSpec.CalmWallLanding, 18580, 19000, 0) { t, c ->
            if (abs(t - 19.0) < 1e-3) { c.extents(e); assertTrue(e.height > h1 + 25f, "previous Calm already flattened") }
        }
    }

    @Test fun fastScrubRetainsLensHeightAndDoesNotOvershoot() {
        val bar = run {
            val width = 834f; val height = 186f; val inset = 24f; val density = 3f
            val slot = (width - inset * 2f) / 3f
            val rest = minOf(285f / 2f, (inset + slot / 2f - density).coerceAtLeast(density))
            val half = minOf(height / 2f - 12f, rest)
            GlassSelectorBar(width, height, inset, 3, height / 2f, rest, half,
                maxOf(half, height / 2f - density), half, rest, density, width)
        }
        val events = File("../tools/reference-replay/v2-script.txt").readLines().mapNotNull { line ->
            val w = line.trim().split(Regex("""\s+"""))
            if (w.size == 4 && w[1] in setOf("down", "move", "up")) Triple(w[0].toInt(), w[1], w[2].toFloat()) else null
        }
        fun run(spec: GlassPoseSpec, from: Int, until: Int, selected: Int, sample: (Double, GlassPoseController) -> Unit) {
            val c = GlassPoseController(spec)
            c.attach(bar, null, null); c.snapToRest(selected)
            val window = events.filter { it.first in from..until }
            var next = 0
            var frame = (from / 1000.0 * 60).toInt()
            while (frame / 60.0 <= until / 1000.0) {
                val t = frame / 60.0
                while (next < window.size && window[next].first / 1000.0 <= t) {
                    val (ms, op, x) = window[next++]
                    when (op) {
                        "down" -> c.pointerDown(x - 63f, bar.centreY, ms / 1000.0, true)
                        "move" -> c.pointerMove(x - 63f, bar.centreY, ms / 1000.0)
                        "up" -> c.pointerUp(selected)
                    }
                }
                c.advanceFrameTo(t.toFloat())
                assertFalse(c.solverFailed)
                sample(t, c)
                frame++
            }
        }
        val e = GlassPoseExtents()
        var minH = Float.MAX_VALUE; var maxH = -Float.MAX_VALUE
        var minW = Float.MAX_VALUE; var maxW = -Float.MAX_VALUE
        var at19W = 0f; var at19H = 0f
        var at20W = 0f; var at20H = 0f
        var minHTime = 0.0
        run(GlassPoseSpec.Calm, 18580, 22000, 0) { t, c ->
            c.extents(e)
            if (e.height < minH) { minH = e.height; minHTime = t }
            if (e.height > maxH) maxH = e.height
            if (e.width < minW) minW = e.width
            if (e.width > maxW) maxW = e.width
            if (abs(t - 19.0) < 1e-3) { at19W = e.width; at19H = e.height }
            if (abs(t - 20.0) < 1e-3) { at20W = e.width; at20H = e.height }
        }
        // At 19.0s initial swipe reversal, the lens matches video 2's measured travel flattening
        assertEquals(.755f, at19H / bar.height, .05f, "travelling lens height at 19.0s")
        assertTrue(at19W >= 410f, "travelling lens width at 19.0s: $at19W")

        // Issue #119 / audit item 139: during docs/2 fast scrub (18.7-22.0s), main collapsed
        // down to 102.2 px height and ballooned to 588.6 px width (trace 590x103 at 20.0s).
        // iOS stays about 370x186 px full height. The strain must not overshoot and stay on.
        assertTrue(at20H >= 150f, "height at 20.0s should recover towards full height, was $at20H")
        assertTrue(at20W <= 420f, "width at 20.0s should not balloon to 590px, was $at20W")
        assertTrue(minH >= 135f, "minimum scrub height should remain bounded, was $minH at $minHTime")
        assertTrue(maxW <= 480f, "maximum scrub width should remain bounded, was $maxW")
    }
}

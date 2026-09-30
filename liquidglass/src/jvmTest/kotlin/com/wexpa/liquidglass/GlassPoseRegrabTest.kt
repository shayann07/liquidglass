package com.wexpa.liquidglass

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A finger landing on a body that is still recovering from the last hold takes it back at once
 * (parity brief section 5.1: re-grab replaces recovery ownership). On the phone (FABLE run 2,
 * `human-FABLE-r5/regrab.mp4`) a press 80 ms after the lift went through the hold threshold
 * again and the lens fell to the pill and re-formed - a blink. This drives that exact sequence.
 */
class GlassPoseRegrabTest {

    private fun bar() = GlassSelectorBar(
        width = 1006f, height = 162f, inset = 18f, count = 5, cornerRadius = 81f,
        baseHalfWidth = 96f, baseHalfHeight = 69f, allowedHalfHeight = 69f,
        heldHalfHeight = 96f, heldHalfWidth = 128f, density = 3f, maxBodyWidth = 600f,
    )

    private fun boundsOf(b: GlassSelectorBar, width: Float = 150f): Pair<FloatArray, FloatArray> {
        val bounds = FloatArray(b.count * 2)
        val centres = FloatArray(b.count)
        for (i in 0 until b.count) {
            val c = b.centreOf(i)
            centres[i] = c
            bounds[i * 2] = c - width / 2f
            bounds[i * 2 + 1] = c + width / 2f
        }
        return bounds to centres
    }

    @Test
    fun aPressDuringRecoveryTakesTheHoldBackWithoutABlink() {
        val b = bar()
        val c = GlassPoseController()
        val (bounds, centres) = boundsOf(b)
        c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
        c.snapToRest(0)
        var x = b.centreOf(0)
        val y = b.centreY
        var t = 0.0
        var f = 0f
        fun step(seconds: Float, move: Boolean) {
            val end = f + seconds
            while (f < end - 1e-6f) {
                f += 1f / 240f
                t += 1.0 / 240.0
                if (move) c.pointerMove(x, y, t)
                c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
                c.advanceTo(f)
            }
        }
        // Drag from item 0 to item 2 in 300 ms.
        c.pointerDown(x, y, t, eligible = true)
        c.beginDrag(x, y)
        val target = b.centreOf(2)
        val start = x
        val steps = 72
        for (i in 1..steps) {
            x = start + (target - start) * i / steps
            f += 1f / 240f; t += 1.0 / 240.0
            c.pointerMove(x, y, t)
            c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
            c.advanceTo(f)
        }
        val formedBeforeLift = c.formation
        assertTrue(formedBeforeLift > 0.9f, "the drag never formed the lens ($formedBeforeLift)")
        c.pointerUp(2)
        step(0.080f, move = false)
        val atRegrab = c.formation
        // The re-press, 80 ms after the lift, at the same point.
        c.pointerDown(x, y, t, eligible = true)
        assertTrue(c.isHeld, "a press on a recovering body did not take the hold back at once")
        var lowest = c.formation
        step(0.4f, move = true)
        var probe = 0f
        while (probe < 0.4f) { probe += 1f / 240f }
        // Re-run the same 400 ms with sampling to find the lowest formation.
        val c2 = GlassPoseController()
        c2.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
        c2.snapToRest(0)
        var x2 = b.centreOf(0); var t2 = 0.0; var f2 = 0f
        c2.pointerDown(x2, y, t2, eligible = true); c2.beginDrag(x2, y)
        for (i in 1..steps) {
            x2 = start + (target - start) * i / steps
            f2 += 1f / 240f; t2 += 1.0 / 240.0
            c2.pointerMove(x2, y, t2); c2.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f); c2.advanceTo(f2)
        }
        c2.pointerUp(2)
        var lo = c2.formation
        for (i in 0 until 19) { f2 += 1f / 240f; t2 += 1.0 / 240.0; c2.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f); c2.advanceTo(f2); lo = minOf(lo, c2.formation) }
        c2.pointerDown(x2, y, t2, eligible = true)
        for (i in 0 until 96) { f2 += 1f / 240f; t2 += 1.0 / 240.0; c2.pointerMove(x2, y, t2); c2.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f); c2.advanceTo(f2); lo = minOf(lo, c2.formation) }
        lowest = lo
        println("POSE-REGRAB formation before lift %.3f, at re-press %.3f, lowest after %.3f, held=%s".format(formedBeforeLift, atRegrab, lowest, c.isHeld))
        assertTrue(lowest >= atRegrab - 0.02f, "the lens kept falling after the re-grab: lowest $lowest against $atRegrab at the re-press")
    }
}

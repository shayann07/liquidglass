package com.wexpa.liquidglass

import kotlin.test.*

/** #118: the governor behind LocalGlassAdaptiveQuality, fed synthetic 90 Hz frame streams. */
class GlassQualityGovernorTest {
    private val frame = 11_111_111L

    /**
     * Feeds [count] frames from [start], missing every [missEvery]th (0: none), then lets the glass rest long
     * enough for a pending step to land; returns the time after that rest.
     */
    private fun GlassQualityGovernor.feed(start: Long, count: Int, missEvery: Int): Long {
        var t = start
        repeat(count) { i -> onFrame(t, missEvery > 0 && i % missEvery == 0); t += frame }
        apply(t + 300_000_000L)
        return t + 300_000_000L
    }

    @Test fun onTimeFramesKeepFullScale() {
        val g = GlassQualityGovernor()
        g.feed(0, 900, 0)
        assertEquals(1f, g.factor)
    }

    /** The Pixel 7's taps (#100): one late first frame per tap, ~70 frames each. Never a step. */
    @Test fun oneSlowFramePerTapNeverSteps() {
        val g = GlassQualityGovernor()
        var t = 0L
        repeat(20) { t = g.feed(t, 70, 70) + 800_000_000L }
        assertEquals(1f, g.factor)
    }

    @Test fun sustainedMissesStepDownToTheFloorOneStepPerWindow() {
        val g = GlassQualityGovernor()
        // A step needs 20 frames in the window, half of them late here.
        var t = g.feed(0, 20, 2)
        assertEquals(0.75f, g.factor)
        // The frames before a step are discarded: the next step needs its own 20.
        t = g.feed(t, 19, 2)
        assertEquals(0.75f, g.factor)
        t = g.feed(t, 1, 2)
        assertEquals(0.5625f, g.factor, 1e-6f)
        t = g.feed(t, 20, 2)
        assertEquals(0.5f, g.factor)
        g.feed(t, 300, 2)
        assertEquals(0.5f, g.factor)
    }

    @Test fun aFifthMissedDoesNotStep() {
        val g = GlassQualityGovernor()
        g.feed(0, 400, 5)
        assertEquals(1f, g.factor)
    }

    @Test fun recoversOneStepAfterThreeQuietSeconds() {
        val g = GlassQualityGovernor()
        var t = g.feed(0, 20, 2)
        t = g.feed(t, 20, 2)
        assertEquals(0.5625f, g.factor, 1e-6f)
        assertFalse(g.recover(t + 2_900_000_000L))
        assertTrue(g.recover(t + 3_000_000_000L)); assertTrue(g.apply(t + 3_000_000_000L))
        assertEquals(0.75f, g.factor, 1e-6f)
        assertFalse(g.recover(t + 4_000_000_000L))
        assertTrue(g.recover(t + 6_000_000_000L)); assertTrue(g.apply(t + 6_000_000_000L))
        assertEquals(1f, g.factor)
        assertFalse(g.recover(t + 60_000_000_000L))
    }

    @Test fun recoveryRetracesTheStepsFromTheFloor() {
        val g = GlassQualityGovernor()
        var t = 0L
        repeat(3) { t = g.feed(t, 20, 2) }
        assertEquals(0.5f, g.factor)
        val levels = (1..3).map { k -> val now = t + k * 3_000_000_000L; g.recover(now); g.apply(now); g.factor }
        assertEquals(listOf(0.5625f, 0.75f, 1f), levels)
    }

    /** A step decided mid-gesture waits for the glass to rest: a rebuild under a finger would cancel its gesture. */
    @Test fun aStepWaitsForTheGlassToRest() {
        val g = GlassQualityGovernor()
        var t = 0L
        repeat(20) { i -> g.onFrame(t, i % 2 == 0); t += frame }
        assertEquals(0.75f, g.target); assertEquals(1f, g.factor)
        // Frames keep coming (the finger is still down): the step stays pending, and does not compound.
        repeat(200) { g.onFrame(t, true); t += frame }
        assertFalse(g.apply(t))
        assertEquals(0.75f, g.target); assertEquals(1f, g.factor)
        val last = t - frame
        assertFalse(g.apply(last + 299_000_000L))
        assertTrue(g.apply(last + 300_000_000L))
        assertEquals(0.75f, g.factor)
    }

    /** A step back up that has not landed yet is cancelled by a miss, and the next recovery starts over. */
    @Test fun aMissCancelsAPendingStepUp() {
        val g = GlassQualityGovernor()
        val t = g.feed(0, 20, 2)
        assertEquals(0.75f, g.factor)
        assertTrue(g.recover(t + 3_000_000_000L))
        assertEquals(1f, g.target)
        assertTrue(g.onFrame(t + 3_100_000_000L, true))
        assertEquals(0.75f, g.target)
        assertFalse(g.apply(t + 4_000_000_000L))
        assertEquals(0.75f, g.factor)
        assertFalse(g.recover(t + 5_000_000_000L))
        assertTrue(g.recover(t + 6_100_000_000L)); assertTrue(g.apply(t + 6_100_000_000L))
        assertEquals(1f, g.factor)
    }

    @Test fun aMissDuringRecoveryRestartsTheWait() {
        val g = GlassQualityGovernor()
        val t = g.feed(0, 20, 2)
        g.onFrame(t + 2_000_000_000L, true)
        assertFalse(g.recover(t + 3_500_000_000L))
        assertTrue(g.recover(t + 5_000_000_000L))
    }
}

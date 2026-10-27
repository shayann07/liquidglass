package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behaviour established by the 13 October continuous comparison with the owner's recordings
 * (review/atlas/reference-replay-2026-10-13). Rates are authored; these check the behaviour,
 * not Apple's constants.
 */
class GlassContinuousReplayTest {
    private val bar = GlassSelectorBar(834f,186f,21.75f,3,93f,142.5f,80f,90f,150f,150f,3f,800f)

    private fun thrown(spec: GlassPoseSpec): GlassPoseController {
        val c = GlassPoseController(spec)
        c.attach(bar, null, null); c.snapToRest(0)
        c.pointerDown(bar.centreOf(0), bar.centreY, 0.0, true)
        c.advanceTo(.8f, .9f); c.beginDrag(bar.centreOf(0), bar.centreY)
        for (i in 1..48) {
            val t = .8f + .1f * i / 48
            c.pointerMove(bar.centreOf(0) + .5f * bar.slotWidth * i / 48, bar.centreY, t.toDouble())
            c.advanceFrameTo(t)
        }
        c.pointerUp(2)
        return c
    }

    @Test fun aThrownLensStaysFormedWhileItTravelsAndOnlySubsides() {
        val c = thrown(GlassPoseSpec.Calm)
        val atRelease = c.formation
        assertTrue(atRelease > .9f, "released formed: $atRelease")
        var previous = atRelease
        var formedWhileFast = true
        for (i in 1..960) {
            c.advanceTo(.9f + i / 480f)
            assertFalse(c.solverFailed)
            assertTrue(c.formation <= previous + 1e-4f, "released formation re-inflated at step $i")
            if (abs(c.centreVelocityX) > 6f * bar.slotWidth && c.formation < .6f) formedWhileFast = false
            previous = c.formation
        }
        assertTrue(formedWhileFast, "lens collapsed while still travelling fast")
        assertTrue(c.formation < .01f && c.isIdle, "landing never settled")
        assertEquals(bar.lastCentre, c.centreX, .01f)

        // The historical release dropped formation at lift-off.
        val old = thrown(GlassPoseSpec.CalmImpact)
        old.advanceTo(.95f)
        val current = thrown(GlassPoseSpec.Calm)
        current.advanceTo(.95f)
        assertTrue(current.formation > old.formation + .1f)
    }

    @Test fun aWallLandingIsNeverTallerThanTheHeldLens() {
        val held = bar.height * GlassPoseSpec.Calm.heldHeightRatio
        for (spec in listOf(GlassPoseSpec.Calm, GlassPoseSpec.CalmImpact)) {
            val c = thrown(spec)
            var tallest = 0f
            val e = GlassPoseExtents()
            for (i in 0..960) { c.advanceTo(.9f + i / 480f); c.extents(e); tallest = maxOf(tallest, e.height) }
            if (spec == GlassPoseSpec.Calm) assertTrue(tallest <= held + 1f, "Calm landing grew to $tallest > $held")
            else println("IMPACT_LANDING tallest=$tallest held=$held")
        }
    }

    @Test fun aMovingTapShowsLensOpticsWithoutBecomingHeld() {
        val c = GlassPoseController(GlassPoseSpec.CalmWallLanding)
        c.attach(bar, null, null); c.snapToRest(0)
        c.pointerDown(bar.centreOf(2), bar.centreY, 0.0, true); c.retarget(2)
        c.advanceTo(.04f); c.pointerUp(2)
        val style = GlassTabBarStyle.Calm()
        var peak = 0f
        for (i in 1..120) {
            c.advanceTo(.04f + i / 480f)
            assertFalse(c.isHeld)
            assertTrue(c.formation < .25f)
            val material = glassSelectorMaterial(style, c.formation, c.transitAppearance)
            peak = maxOf(peak, material.heldLens)
            // Pigment keeps its own fade: identical tint to the optics-free composition.
            val pigment = glassTransitStyle(lerpGlassStyle(style.pill, style.lens, c.formation),
                c.transitAppearance, style.tapTintFade)
            assertEquals(pigment.tint, material.tint)
            assertEquals(pigment.tintLift, material.tintLift, 1e-6f)
        }
        assertTrue(peak > .8f, "transit optics never formed: $peak")
        assertTrue(peak <= .95f)
        // Historical styles are unchanged.
        val v3 = GlassTabBarStyle.V3()
        assertEquals(glassTransitStyle(lerpGlassStyle(v3.pill, v3.lens, .1f), .9f, v3.tapTintFade),
            glassSelectorMaterial(v3, .1f, .9f))
    }

    @Test fun aMovingTapOnCalmFormsFullLensUnderIOS27WithoutBecomingHeld() {
        val c = GlassPoseController(GlassPoseSpec.Calm)
        c.attach(bar, null, null); c.snapToRest(0)
        c.pointerDown(bar.centreOf(2), bar.centreY, 0.0, true); c.retarget(2)
        c.advanceTo(.04f); c.pointerUp(2)
        val style = GlassTabBarStyle.Calm()
        var peakFormation = 0f
        var peakLens = 0f
        for (i in 1..120) {
            c.advanceTo(.04f + i / 480f)
            assertFalse(c.isHeld)
            peakFormation = maxOf(peakFormation, c.formation)
            val material = glassSelectorMaterial(style, c.formation, c.transitAppearance)
            peakLens = maxOf(peakLens, material.heldLens)
        }
        assertTrue(peakFormation >= 0.85f, "iOS 27 tap transit formation must reach >= 0.85, was $peakFormation")
        assertTrue(peakLens >= 0.95f, "iOS 27 tap transit heldLens must reach full lens: $peakLens")
        c.advanceTo(0.6f)
        assertTrue(c.formation < 0.05f, "settled tap must subside back to resting pill, was ${c.formation}")
    }

    @Test fun aMovingSelectorMirrorsItsCapsAndAStillHoldKeepsTheHeldMap() {
        // Calm since 26 October (#94): a tap in transit folds again, with the narrow stretch (rim
        // mirror 0.3, gain 0.15) along the runs as well as the caps, as iOS's does (docs/1 0.95-1.12 s).
        val calm = GlassTabBarStyle.Calm()
        val calmTransit = glassSelectorMaterial(calm, 0.05f, 0.9f)
        assertEquals(GlassProfile.Measured, calmTransit.profile)
        assertEquals(GlassSelectorRimMirror, calmTransit.rimMirror)
        assertEquals(GlassSelectorTransitFoldCorners, calmTransit.foldAtCorners)
        assertEquals(0.9f, glassSelectorFormation(calm, 0.05f, 0.9f, 0f), 1e-6f)
        // A held lens's travel keeps its rim mirror at the caps.
        assertEquals(0.6f, glassSelectorFormation(calm, 1f, 0f, 0.6f), 1e-6f)
        // The fold it replaced stays on CalmRecordedSpace: the folding rim mirror at the caps.
        val style = GlassTabBarStyle.CalmRecordedSpace()
        val transit = glassSelectorMaterial(style, 0.05f, 0.9f)
        assertEquals(GlassProfile.Measured, transit.profile)
        assertEquals(GlassSelectorTransitFoldCorners, transit.foldAtCorners); assertTrue(transit.rimMirror > 0f)
        assertEquals(0.9f, glassSelectorFormation(style, 0.05f, 0.9f, 0f), 1e-6f)
        // A held lens: the measured held map, blended toward the mirror only with travel.
        val held = glassSelectorMaterial(style, 1f, 0f)
        assertEquals(GlassProfile.Held, held.profile)
        assertEquals(0f, glassSelectorFormation(style, 1f, 0f, 0f))
        assertEquals(0.6f, glassSelectorFormation(style, 1f, 0f, 0.6f), 1e-6f)
        assertTrue(calm.lensInkAll)
        // Historical styles never fold or mirror.
        val v3 = GlassTabBarStyle.V3()
        assertEquals(0f, glassSelectorFormation(v3, 0.05f, 0.9f, 0.6f))
        assertEquals(0f, glassSelectorMaterial(v3, 0.05f, 0.9f).rimMirror)
    }

    @Test fun heldTravelRisesOnlyWhileTheHeldLensMoves() {
        val c = GlassPoseController(GlassPoseSpec.Calm)
        c.attach(bar, null, null); c.snapToRest(0)
        c.pointerDown(bar.centreOf(0), bar.centreY, 0.0, true)
        c.advanceTo(.6f); c.beginDrag(bar.centreOf(0), bar.centreY)
        assertEquals(0f, c.heldTravel, 1e-4f, "a still hold is not travelling")
        var peak = 0f
        for (i in 1..48) {
            val t = .6f + .1f * i / 48
            c.pointerMove(bar.centreOf(0) + 2f * bar.slotWidth * i / 48, bar.centreY, t.toDouble())
            c.advanceFrameTo(t); peak = maxOf(peak, c.heldTravel)
        }
        assertTrue(peak > .5f, "fast held travel never registered: $peak")
        c.advanceTo(1.4f)
        assertTrue(c.heldTravel < .05f, "travel stayed after the finger stopped: ${c.heldTravel}")
    }
}

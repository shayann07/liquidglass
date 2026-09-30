package com.wexpa.liquidglass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The presets, instantiated.
 *
 * The first build of [GlassTabBarStyle] crashed the app on launch with an NPE from its own
 * companion: `Dark` was declared before the two materials its constructor defaults read, so it
 * was built while they were still null. No test touched the class, so the JVM never saw it.
 * Constructing every preset here is the whole point.
 */
class GlassTabBarStyleTest {

    @Test
    fun testEveryPresetConstructsWithItsMaterialsInPlace() {
        val style = GlassTabBarStyle.Dark
        assertNotNull(style.pill)
        assertNotNull(style.lens)
        assertEquals(GlassTabBarStyle.RestingInset, style.pill)
        assertEquals(GlassTabBarStyle.HeldLens, style.lens)
        assertEquals(GlassStyle.DarkChrome, style.bar)
    }

    @Test
    fun testTheV3PresetAgreesWithItselfAcrossEveryFieldThatSnapsAtTheMorphsMidpoint() {
        // lerpGlassStyle interpolates quantities and snaps identities at the midpoint (a half
        // inverted element, or a half-applied source map, is not a state anything wants to be in).
        // The tab bar morphs its indicator from `pill` to `lens`, so those two must agree on every
        // field that snaps, or the ink's source map changes shape halfway through the morph. This
        // is the GlassMorph audit V3-MODEL section 14 asks for, as a test rather than a reading.
        val v3 = GlassTabBarStyle.V3(dark = true)
        assertEquals(v3.pill.profile, v3.lens.profile, "profile snaps at the midpoint")
        assertEquals(v3.pill.restMap, v3.lens.restMap, "restMap snaps at the midpoint")
        assertEquals(v3.pill.heldInkContinuous, v3.lens.heldInkContinuous, "heldInkContinuous snaps")
        assertEquals(v3.pill.invertsWithBackdrop, v3.lens.invertsWithBackdrop, "inversion snaps")
        assertEquals(v3.pill.fallbackSurface, v3.lens.fallbackSurface, "the fallback surface snaps")
        assertEquals(
            v3.pill.inkDispersion.isNaN(),
            v3.lens.inkDispersion.isNaN(),
            "an unspecified ink split snaps rather than interpolating into NaN",
        )
        // And the midpoint really is continuous in everything that matters to the source map.
        val mid = lerpGlassStyle(v3.pill, v3.lens, 0.5f)
        assertEquals(v3.lens.profile, mid.profile)
        assertEquals(v3.lens.heldInkContinuous, mid.heldInkContinuous)
        assertEquals(v3.pill.edgeFold, mid.edgeFold, 1e-6f, "both ends carry the same fold strength")
        assertNotNull(v3.selector, "the V3 preset must actually select the deforming body")
    }

    @Test
    fun testTheV3PresetLeavesTheMeasuredOpticsAlone() {
        val measured = GlassTabBarStyle.Measured(dark = true)
        val v3 = GlassTabBarStyle.V3(dark = true)
        // Everything the material was measured for is unchanged; only the three opt-in controls
        // and the selector differ.
        assertEquals(measured.bar.copy(edgeFold = v3.bar.edgeFold, tintLift = 32f / 255f), v3.bar)
        assertEquals(12f / 255f, v3.heldLift)
        assertEquals(measured.pillWidth, v3.pillWidth)
        assertEquals(measured.heldScale, v3.heldScale)
        assertEquals(1f, v3.selectedScale)
        val inkMagnification = v3.heldScale * v3.selectedScale / (1f - v3.lens.heldMagnification)
        assertTrue(inkMagnification in 1.17f..1.23f, "native held ink should read about 1.19x, was $inkMagnification")
        // The V3 lens interior carries no white at all (r10 optics audit: +2..+8 over the bar in
        // every reference); the measured preset keeps its 1 %.
        assertEquals(0f, v3.lens.tint.alpha)
        assertEquals(0.01f, measured.lens.tint.alpha, 0.005f)   // 3/255 once packed
        assertEquals(measured.lens.bevel, v3.lens.bevel)
        // Original settled Phone frames have a narrow dark side step (~29codes) and lit
        // top/bottom boundaries over black. The measured preset remains unchanged; these
        // are V3's explicit departures. Separate render gates cover the side and raised rim.
        assertEquals(
            measured.lens.copy(
                edgeFold = v3.lens.edgeFold,
                // Ink crossing the band splits into colour fringes (6721, 6727); the split is
                // scaled by the bend, so the interior is still one sharp sample.
                inkDispersion = 0.03f,
                heldInkContinuous = false,
                heldMagnification = 0.12f,
                legibility = 0f,
                tint = Color.White.copy(alpha = 0f),
                rawShare = 0.01f,
                // The rim after the r10 optics audit: a thin lit chamfer over the page, the dark
                // step at the sides, a short glow, the crossing split at 2-3 px.
                bevel = 2.dp,
                bevelPeak = 0f,
                specular = 0.49f,
                specularPower = 1f,
                counterLight = 1f,
                edgeShadow = 0.12f,
                heldGlow = 0.4f,
                heldEdgeRecovery = 1.dp,
                dispersion = 0.05f,
                blurRadius = 0.5.dp,
            ),
            v3.lens,
        )
        assertEquals(0.30f, measured.lens.specular, "the measured lens keeps its lit rim")
        // The lens's interaction adds nothing of its own to the held lens: no balloon, no interior
        // lift, no pull (r12/r13 on the phone: the default lift put the interior +65 over the bar).
        assertEquals(1f, v3.lensInteraction.pressScale)
        assertEquals(0f, v3.lensInteraction.pressLift)
        assertTrue(!v3.lensInteraction.pull)
        assertEquals(1f, measured.lens.heldGlow)
        assertTrue(v3.bar.edgeFold > 0.33f, "a fold needs a > 1/3 to have a turning point at all")
        assertEquals(0f, v3.pill.inkDispersion, "the resting pill takes one sharp sample of its ink")
        assertEquals(0.03f, v3.lens.inkDispersion, "the held lens colour-splits ink in its band only")
        assertTrue(!v3.lens.heldInkContinuous, "the held ink map is the stepped one: a crossing glyph is swallowed, then a sliver")
        // And the default preset is untouched by all of it.
        assertEquals(null, measured.selector)
        assertEquals(0f, measured.bar.edgeFold)
        assertTrue(GlassTabBarStyle.Dark.lens.inkDispersion.isNaN())
    }

    @Test
    fun testTheRestingInsetHasNoOptics() {
        // Measured: a flat dark inset with a two-pixel edge and nothing else. A bend here grows a
        // bright ring at the rim, which is the most visible tell of a recreation.
        val pill = GlassTabBarStyle.RestingInset
        assertEquals(0f, pill.refractionDepth.value)
        assertEquals(0f, pill.dispersion)
        assertEquals(0f, pill.mirror)
        assertEquals(0f, pill.specular)
        assertEquals(0f, pill.edgeLight)
        assertEquals(0f, pill.fresnel)
        assertEquals(0f, pill.innerShadow)
        assertEquals(0f, pill.backdropBlur.value, "the pill looks through the bar, which has already frosted")
    }

    @Test
    fun testTheHeldLensIsClearWithALitEdgeAndNoBead() {
        val lens = GlassTabBarStyle.HeldLens
        assertTrue(lens.tint.alpha < 0.05f, "the lens interior reads as the bar does; it adds almost nothing")
        assertTrue(lens.refractionDepth.value > 0f && lens.dispersion > 0.3f, "strong outward refraction with a visible colour split")
        assertEquals(0f, lens.bevelPeak, "an edge line, not a bead")
        assertEquals(0f, lens.mirror)
        assertEquals(0f, lens.innerShadow)
        assertTrue(lens.edgeLight > 1f, "the outermost line carries the light, not the bevel lobe")
        assertEquals(1f, GlassTabBarStyle.Dark.lensInteraction.pressScale, "the reference does not scale-bounce")
    }

    @Test
    fun testTheLensSidesAreDarkBecauseTheEdgeLineIsDirectional() {
        // The reference lens is lit from overhead: a bright line top and bottom, and at the
        // sides, where the normal is perpendicular to the light, a one-pixel dark step reading
        // 24 against an interior of 41. Nothing omnidirectional may light those sides, so the
        // dark contour is the only thing that defines the shape there.
        val lens = GlassTabBarStyle.HeldLens
        assertTrue(lens.edgeShadow > 0f, "the sides are a dark step, not an unlit ramp")
        assertEquals(0f, lens.fresnel, "fresnel is omnidirectional and would light the sides")
    }

    @Test
    fun testTheIos27PresetOnlyDarkensTheContourAndBrightensTheHighlight() {
        val base = GlassTabBarStyle.Dark
        val revised = GlassTabBarStyle.Ios27
        assertTrue(revised.lens.edgeShadow > base.lens.edgeShadow, "2026 darkens the edge")
        assertTrue(revised.lens.specular > base.lens.specular, "and brightens the specular")
        assertEquals(base.height, revised.height, "geometry is unchanged; only the surface moved")
        assertEquals(base.lens.refractionDepth, revised.lens.refractionDepth)
    }

    @Test
    fun testTheLensStandsProudByTheSameAmountAboveAndBelow() {
        val style = GlassTabBarStyle.Dark
        assertEquals(0f, style.lensRise.value, "the native reference is centred on the bar")
        assertTrue(style.lensOverflow.value > 0f)
    }
}

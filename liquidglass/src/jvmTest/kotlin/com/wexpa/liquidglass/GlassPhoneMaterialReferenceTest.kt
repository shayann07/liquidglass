package com.wexpa.liquidglass

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Original 6756 T01: stable scene, untouched bar region, native PQ->sRGB values. */
class GlassPhoneMaterialReferenceTest {
    @Test fun theCalmBarDoesNotBrightenTwiceWhileHeld() {
        // GlassTabBar supplies heldLift to its style; LiquidGlass adds the interaction's
        // pressLift. Check their combined endpoint against the independent references.
        // Display colours (15 October; the recordings are HDR10, decoded with research/README.md
        // II.4): iOS's held bar reads 45 over black in docs/2, IMG_6756 and LOLL8185.
        assertEquals(45.0, heldBar(GlassTabBarStyle.Calm(dark = true)), 1.5,
            "Phone held bar must not add generic control lift again")
        // The calibration made against the recordings decoded as sRGB (13 October): 52-53 held.
        // The older 6756 T01 plateau (44 held) stays on V3, tested below.
        assertEquals(53.0, heldBar(GlassTabBarStyle.CalmRecordedSpace(dark = true)), 1.0,
            "Recorded-space held bar")
    }

    @Test fun onlyDarkCalmLiftsTheBarForAnyTouch() {
        // iOS's whole bar lifts on every touch, a tap included (docs/1 in display colours: +13 at
        // the tab a tap never visits). Opt-in: off by default and on the recorded-space preset.
        assertEquals(false, GlassTabBarStyle().touchLift)
        assertEquals(true, GlassTabBarStyle.Calm(dark = true).touchLift)
        // Light Calm lifts too (17 October; measured on the light recording's touches).
        assertEquals(true, GlassTabBarStyle.Calm(dark = false).touchLift)
        assertEquals(false, GlassTabBarStyle.CalmRecordedSpace(dark = true).touchLift)
    }

    @Test fun theClearBarPassesWhatsAppsAndTheAppStoresPage() {
        // Display colours: iOS's WhatsApp and App Store bars at rest over plain pages (App Store black 0, WhatsApp chats
        // 10, WhatsApp card 23, App Store card 28), with the #203 tolerances. The chats page carries text under the bar,
        // so it is given more room.
        val clear = GlassTabBarStyle.CalmClear(dark = true)
        for ((page, ios, tol) in listOf(Triple(0, 32.0, 2.0), Triple(10, 43.0, 3.0), Triple(23, 54.0, 2.0), Triple(28, 58.0, 3.0)))
            assertEquals(ios, restingBar(clear, page), tol, "CalmClear bar over page $page")
        // The App Store's bright cards (#203): blue (145, 192, 242) under the bar reads (116, 167, 219), luma 157.6;
        // orange (242, 194, 113) reads (209, 162, 79), luma 166.6. Luma within 6, each channel within 8.
        for ((card, ios) in listOf(
            Triple(145, 192, 242) to Triple(116.0, 167.0, 219.0),
            Triple(242, 194, 113) to Triple(209.0, 162.0, 79.0),
        )) {
            val ours = restingBarRgb(clear, card)
            val luma = { c: Triple<Double, Double, Double> -> .299 * c.first + .587 * c.second + .114 * c.third }
            assertEquals(luma(ios), luma(ours), 6.0, "CalmClear bar luma over card $card")
            assertEquals(ios.first, ours.first, 8.0, "red over card $card")
            assertEquals(ios.second, ours.second, 8.0, "green over card $card")
            assertEquals(ios.third, ours.third, 8.0, "blue over card $card")
        }
        // Calm keeps the Phone bar's glass: the same black level, 0.59 of the page.
        assertEquals(32.0, restingBar(GlassTabBarStyle.Calm(dark = true), 0), 1.5)
        assertEquals(46.0, restingBar(GlassTabBarStyle.Calm(dark = true), 23), 1.5)
        // Dark: the measured in-app material with Calm's bar-only fields; everything else is Calm.
        val calm = GlassTabBarStyle.Calm(dark = true)
        assertEquals(calm.copy(bar = GlassStyle.toolbar(dark = true).copy(restMap = calm.bar.restMap, capFold = calm.bar.capFold,
            specular = calm.bar.specular, bevelPeak = calm.bar.bevelPeak, edgeFold = calm.bar.edgeFold,
            softDetail = calm.bar.softDetail)), clear)
        // Light: the same bar, WhatsApp's pill (236 over white against the Phone's 245).
        val lightCalm = GlassTabBarStyle.Calm(dark = false)
        assertEquals(lightCalm.copy(pill = lightCalm.pill.copy(tintLift = -19f / 255f)), GlassTabBarStyle.CalmClear(dark = false))
    }

    @Test fun thePhoneBarKeepsItsEarlierMaterial() {
        // #203: only CalmClear's dark bar moved to the lab material. Calm, its recorded-space preset and every light bar
        // keep the earlier toolbar fit, which has no tone curve and no colour transfer.
        for (style in listOf(GlassTabBarStyle.Calm(dark = true), GlassTabBarStyle.Calm(dark = false),
            GlassTabBarStyle.CalmRecordedSpace(dark = true), GlassTabBarStyle.CalmRecordedSpace(dark = false),
            GlassTabBarStyle.CalmClear(dark = false))) {
            assertEquals(0f, style.bar.toneCurve, "toneCurve")
            assertEquals(0f, style.bar.detailLift, "detailLift")
        }
    }

    @Test fun theCalmLensAddsTheRawPageBlurred() {
        // 17 October: iOS's held lens reads ~10 above ours over content and the same over black;
        // the raw page returns blurred; 0.5 at 50 dp since 18 October (0.22 at 20 dp before; scored by
        // region, review record).
        val calm = GlassTabBarStyle.Calm(dark = true)
        assertEquals(0.5f, calm.lens.rawShare)
        assertEquals(50.dp, calm.lens.rawBlur)
        assertEquals(0f, calm.pill.rawShare)
        // The pill-to-lens morph carries the blur with the share; light Calm and V3 add it sharp.
        assertEquals(25f, lerpGlassStyle(calm.pill, calm.lens, .5f).rawBlur.value, 1e-4f)
        assertEquals(0.dp, GlassTabBarStyle.Calm(dark = false).lens.rawBlur)
        assertEquals(0.dp, GlassTabBarStyle.V3(dark = true).lens.rawBlur)
    }

    @Test fun lightCalmMatchesTheLightRecording() {
        // IMG_7048 (17 October, display colours): the App Store's light bar over its cards passes
        // 0.58 of the page plus ~116; over white it reads 255; resting pills 236-246 (Phone, WhatsApp).
        val light = GlassTabBarStyle.Calm(dark = false)
        for ((page, ios) in listOf(171 to 220.0, 199 to 232.0, 181 to 214.0, 198 to 226.0, 228 to 254.0))
            assertEquals(ios, restingBar(light, page), 9.0, "light bar over page $page")
        assertEquals(255.0, restingBar(light, 255), 1.0)
        assertEquals(0f, light.pill.tint.alpha)
        assertEquals(-10f / 255f, light.pill.tintLift, 1e-6f)
        assertEquals(0f, light.lens.tint.alpha)
        assertEquals(0f, light.lens.rawShare)
        // #128 (1 November): the held outline as dark as iOS's (top rim 227 against 226; 243 read 245).
        assertEquals(217f / 255f, light.lens.heldEdgeTone, 1e-6f)
        // #104 (1 November): no inner thickness line, and a lower, softer shadow outside the lens only.
        assertEquals(0f, light.lens.innerShadow)
        assertEquals(0.35f, light.lens.contactShadow)
        assertEquals(0f, light.lens.contactShadowInside)
        assertEquals(9.dp, light.lens.contactShadowBlur)
        assertEquals(11.dp, light.lens.contactShadowOffset)
        // The light preset it replaced stays reachable.
        assertEquals(false, GlassTabBarStyle.CalmRecordedSpace(dark = false).touchLift)
    }

    private fun restingBar(style: GlassTabBarStyle, page: Int): Double =
        restingBarRgb(style, Triple(page, page, page)).let { .299 * it.first + .587 * it.second + .114 * it.third }

    /** The bar's interior over a flat page, with the material's tone curve and colour transfer as the host passes them. */
    private fun restingBarRgb(style: GlassTabBarStyle, page: Triple<Int, Int, Int>): Triple<Double, Double, Double> {
        val s = style.bar
        val colour = (0xFF shl 24) or (page.first shl 16) or (page.second shl 8) or page.third
        val pixels = GlassRender.render(
            width = 834, height = 186, pad = 24, profile = 2f,
            tintAlpha = s.tint.alpha,
            tint = Triple(s.tint.red, s.tint.green, s.tint.blue),
            lift = s.tintLift,
            detailLift = s.detailLift.coerceIn(-1f, 1f),
            toneCurve = s.toneCurve,
            backdrop = { _, _ -> colour },
        )
        val p = pixels[(93+24)*(834+48)+350+24]
        return Triple(((p shr 16) and 0xFF).toDouble(), ((p shr 8) and 0xFF).toDouble(), (p and 0xFF).toDouble())
    }

    private fun heldBar(calm: GlassTabBarStyle): Double {
        val s = calm.bar
        val pixels = GlassRender.render(
            width = 834, height = 186, pad = 24, profile = 2f,
            tintAlpha = s.tint.alpha,
            tint = Triple(s.tint.red, s.tint.green, s.tint.blue),
            lift = s.tintLift + calm.heldLift + checkNotNull(calm.barInteraction).pressLift,
            backdrop = { _, _ -> 0xFF000000.toInt() },
        )
        return GlassRender.luma(pixels[(93+24)*(834+48)+350+24])
    }

    @Test fun theDarkV3BarMatchesTheOriginalRestAndHeldPlateaus() {
        val v3 = GlassTabBarStyle.V3(dark = true)
        for ((held, expected) in listOf(false to 32.0, true to 44.0)) {
            val s = v3.bar
            val pixels = GlassRender.render(
                width = 834, height = 186, pad = 24, profile = 2f,
                tintAlpha = s.tint.alpha,
                tint = Triple(s.tint.red, s.tint.green, s.tint.blue),
                lift = s.tintLift + if (held) v3.heldLift else 0f,
                backdrop = { _, _ -> 0xFF000000.toInt() },
            )
            val value = GlassRender.luma(pixels[(93+24)*(834+48)+350+24])
            assertEquals(expected, value, 1.0, "6756 T01 bar plateau, held=$held")
        }
        // The original target-calibrated general material remains available unchanged.
        assertEquals(35f / 255f, GlassTabBarStyle.Measured(dark = true).bar.tintLift, 1e-6f)
        assertEquals(16f / 255f, GlassTabBarStyle.Measured(dark = true).heldLift, 1e-6f)
        val light = GlassTabBarStyle.Measured(dark = false)
        assertEquals(light.bar.tintLift, GlassTabBarStyle.V3(dark = false).bar.tintLift)
        assertEquals(light.heldLift, GlassTabBarStyle.V3(dark = false).heldLift)
    }
}

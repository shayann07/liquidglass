package com.wexpa.liquidglass.material3

import com.wexpa.liquidglass.GlassStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** The menu material (#204): `DropdownMenu` draws the measured menu panel, in both appearances, and [GlassMaterialDefaults.panel] stays available. */
class GlassMenuDefaultsTest {
    @Test fun theMenuDefaultIsTheMeasuredMenuPanelInBothAppearances() {
        for (dark in listOf(true, false)) {
            val ours = GlassMaterialDefaults.menu.style(dark)!!
            val measured = GlassStyle.menu(dark)
            assertEquals(measured.wideKernel, ours.wideKernel, "wide kernel, dark=$dark")
            assertEquals(measured.tint, ours.tint, "tint, dark=$dark")
            assertEquals(measured.tintLift, ours.tintLift, "lift, dark=$dark")
            assertEquals(measured.toneCurve, ours.toneCurve, "bow, dark=$dark")
            assertEquals(0f, ours.fineShare, "no detail term, dark=$dark")
            // The iOS 27 rim of the other Material panels.
            assertEquals(GlassMaterialDefaults.panel.style(dark)!!.bevel, ours.bevel, "rim, dark=$dark")
            assertNotEquals(GlassMaterialDefaults.panel.style(dark)!!.tint, ours.tint, "the menu is not the card panel, dark=$dark")
        }
    }
}

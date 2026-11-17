package com.wexpa.liquidglass.sample.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.wexpa.liquidglass.Glass
import com.wexpa.liquidglass.GlassStyle
import com.wexpa.liquidglass.sample.ColourTransferScene
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

/**
 * The colour-transfer fixture (the sample's ColourTransferActivity), rendered at a phone's size for the review
 * evidence of #201: 390 x 844 dp at density 3, dark and light. Opt-in, so ordinary test runs skip both renders:
 * `:desktop:desktopTest --tests "*ColourTransferRenderTest*" -Patlas.colourTransfer=true`.
 */
@OptIn(ExperimentalTestApi::class)
class ColourTransferRenderTest {
    private val out = File("build/reports/atlas/colour-transfer")

    private val requested = System.getProperty("atlas.colourTransfer") == "true"

    private fun shoot(name: String, dark: Boolean, width: Int = 390, height: Int = 844, density: Float = 3f, glass: Glass = Glass.Regular) {
        if (!requested) return
        out.mkdirs()
        runDesktopComposeUiTest((width * density).toInt(), (height * density).toInt(), testTimeout = 6.minutes) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density)) {
                    Box(Modifier.size(width.dp, height.dp).testTag("app")) { ColourTransferScene(dark = dark, glass = glass) }
                }
            }
            waitForIdle()
            ImageIO.write(onNodeWithTag("app").captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
    }

    @Test fun dark() = shoot("colour-transfer-dark", dark = true)
    @Test fun light() = shoot("colour-transfer-light", dark = false)
    // #206: the Control Center tile glass on the same bands, at the Pixel 7's 411 x 914 dp and density 2.625, for the device check.
    @Test fun controlCenterTile() = shoot("colour-transfer-cc", dark = true, width = 411, height = 914, density = 2.625f,
        glass = Glass(GlassStyle.controlCenterTile()))
}

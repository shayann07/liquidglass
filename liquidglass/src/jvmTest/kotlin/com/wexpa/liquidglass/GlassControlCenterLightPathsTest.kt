package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The overhead light of [GlassStyle.controlCenterTile] (#206) reaches every host that draws a style, not only the lone
 * `liquidGlass` modifier the rim test uses: the merged-glass [LiquidGlassContainer] and the no-root fallback of
 * `Modifier.glassEffect`. In each, the tile with the default light must draw exactly what the same tile draws when the host
 * passes the vertical light explicitly, and must differ from an unmarked copy of the tile, which keeps the default (tilted) light.
 */
class GlassControlCenterLightPathsTest {
    private val overhead = GlassLight(0f, -1f)
    private val grey = IntArray(900 * 480) { GlassLabScene.grey(56) }

    private fun container(style: GlassStyle, light: GlassLight): IntArray =
        GlassLabScene(style, pillW = 201, pillH = 201, container = true, light = light).use { it.render(grey) }

    @Test
    fun theContainerDrawsTheTileLitFromAbove() {
        val tile = GlassStyle.controlCenterTile()
        val byDefault = container(tile, GlassLight.Default)
        assertContentEquals(container(tile, overhead), byDefault, "container: the tile with the default light equals the tile with the explicit vertical light")
        // copy() is unmarked (a pending owner decision); here it is only the control: with the default light it must render differently,
        // which proves the comparison above can tell the two lights apart in this host.
        assertFalse(container(tile.copy(), GlassLight.Default).contentEquals(byDefault), "container: an unmarked copy keeps the tilted default light and must differ")
    }

    private fun fallback(glass: Glass): IntArray {
        // No LiquidGlassTheme above: glassEffect draws the frosted stand-in (drawGlassFallback) with the style's light.
        val scene = ImageComposeScene(300, 300, Density(3f)) {
            Box(Modifier.fillMaxSize().background(Color(GlassLabScene.grey(56)))) {
                Box(Modifier.offset(10.dp, 10.dp).size(67.dp).glassEffect(glass, CircleShape))
            }
        }
        try {
            repeat(2) { scene.render(it * 16_000_000L) }
            return GlassLabScene.pixelsOf(scene.render(2 * 16_000_000L))
        } finally {
            scene.close()
        }
    }

    @Test
    fun theNoRootFallbackDrawsTheTileLitFromAbove() {
        val tile = GlassStyle.controlCenterTile()
        val byDefault = fallback(Glass(tile))
        assertContentEquals(fallback(Glass(tile, light = overhead)), byDefault, "fallback: the tile with the default light equals the tile with the explicit vertical light")
        assertFalse(fallback(Glass(tile.copy())).contentEquals(byDefault), "fallback: an unmarked copy keeps the tilted default light and must differ")
        assertTrue(byDefault.any { it != GlassLabScene.grey(56) }, "fallback: the stand-in drew something over the page")
    }
}

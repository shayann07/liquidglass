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
 * The default light is straight overhead (#206), in every host that draws a style, not only the lone `liquidGlass` modifier
 * the rim test uses: the merged-glass [LiquidGlassContainer] and the no-root fallback of `Modifier.glassEffect`. In each, the
 * tile with the default light must draw exactly what it draws when the host passes the vertical light explicitly, a copy of
 * the tile must draw the same (no per-style marker to lose), and the light before 1.1.0 must draw something else, which
 * proves the comparison can tell two lights apart in this host.
 */
class GlassControlCenterLightPathsTest {
    private val overhead = GlassLight(0f, -1f)
    private val tiltedBefore110 = GlassLight(-0.35f, -0.94f)
    private val grey = IntArray(900 * 480) { GlassLabScene.grey(56) }

    private fun container(style: GlassStyle, light: GlassLight): IntArray =
        GlassLabScene(style, pillW = 201, pillH = 201, container = true, light = light).use { it.render(grey) }

    @Test
    fun theContainerDrawsTheTileLitFromAbove() {
        val tile = GlassStyle.controlCenterTile()
        val byDefault = container(tile, GlassLight.Default)
        assertContentEquals(container(tile, overhead), byDefault, "container: the tile with the default light equals the tile with the explicit vertical light")
        assertContentEquals(byDefault, container(tile.copy(), GlassLight.Default), "container: a copy of the tile draws with the same light")
        assertFalse(container(tile, tiltedBefore110).contentEquals(byDefault), "container: the tilted light must render differently")
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
        assertContentEquals(byDefault, fallback(Glass(tile.copy())), "fallback: a copy of the tile draws with the same light")
        assertFalse(fallback(Glass(tile, light = tiltedBefore110)).contentEquals(byDefault), "fallback: the tilted light must render differently")
        assertTrue(byDefault.any { it != GlassLabScene.grey(56) }, "fallback: the stand-in drew something over the page")
    }
}

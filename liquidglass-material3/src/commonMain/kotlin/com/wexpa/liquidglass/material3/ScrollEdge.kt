@file:OptIn(ExperimentalMaterial3Api::class)

package com.wexpa.liquidglass.material3

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wexpa.liquidglass.GlassScrollEdgeStyle
import com.wexpa.liquidglass.GlassScrollEdges
import com.wexpa.liquidglass.glassScrollEdge

/**
 * The scroll edge effect ([com.wexpa.liquidglass.glassScrollEdge]) driven by a Material top app bar's
 * [scrollBehavior]: the top edge follows `scrollBehavior.state.overlappedFraction`, 0 while the content sits below
 * the bar and 1 once it has scrolled fully under it, so the effect and Material's own collapse share one signal.
 * Put it on the scrolling container, after `nestedScroll(scrollBehavior.nestedScrollConnection)`.
 *
 * `overlappedFraction` is experimental in Material 3, and this overload tracks it: a Material release that moves it
 * may need a patch release here. The bottom edge is not driven by the app bar; with [edges] of `Bottom` or `Both` it
 * stays off (use the scroll-state overloads of [com.wexpa.liquidglass.glassScrollEdge] for a bottom edge).
 */
fun Modifier.glassScrollEdge(
    scrollBehavior: TopAppBarScrollBehavior,
    ground: Color,
    edges: GlassScrollEdges = GlassScrollEdges.Top,
    height: Dp = 35.dp,
    style: GlassScrollEdgeStyle = GlassScrollEdgeStyle.Soft,
    inverted: Boolean = false,
): Modifier = glassScrollEdge(
    ground, edges, height, style, inverted,
    topIntensity = { scrollBehavior.state.overlappedFraction },
    bottomIntensity = { 0f },
)

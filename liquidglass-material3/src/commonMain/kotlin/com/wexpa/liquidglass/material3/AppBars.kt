@file:OptIn(ExperimentalMaterial3Api::class)

package com.wexpa.liquidglass.material3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wexpa.liquidglass.Glass
import com.wexpa.liquidglass.GlassToolbarGroup
import com.wexpa.liquidglass.glassEffect
import androidx.compose.material3.BottomAppBar as M3BottomAppBar
import androidx.compose.material3.CenterAlignedTopAppBar as M3CenterAlignedTopAppBar
import androidx.compose.material3.LargeTopAppBar as M3LargeTopAppBar
import androidx.compose.material3.MediumTopAppBar as M3MediumTopAppBar
import androidx.compose.material3.TopAppBar as M3TopAppBar

/** Inside a glass group (a bar's actions): buttons draw no glass of their own, so glass is not stacked. */
internal val LocalInGlassGroup = staticCompositionLocalOf { false }

/** Transparent bar colours: on iOS 26 a bar has no container, only its glass buttons. */
@Composable
private fun clearBarColors(colors: TopAppBarColors): TopAppBarColors = colors.copy(
    containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)

/** The navigation icon in a round glass button. */
private fun glassNavigation(icon: @Composable () -> Unit): @Composable () -> Unit = {
    Box(Modifier.padding(start = 8.dp).size(GlassButtonHeight).glassEffect(GlassMaterialDefaults.regular.interactive(), CircleShape),
        contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalInGlassGroup provides true) { icon() }
    }
}

/** The actions grouped on one glass capsule, as iOS groups bar buttons. */
private fun glassActions(actions: @Composable RowScope.() -> Unit): @Composable RowScope.() -> Unit = {
    GlassToolbarGroup(Modifier.padding(end = 8.dp), glass = GlassMaterialDefaults.regular, minHeight = GlassButtonHeight) {
        CompositionLocalProvider(LocalInGlassGroup provides true) { actions() }
    }
}

private fun barModifier(modifier: Modifier, layout: GlassLayout) =
    if (layout == GlassLayout.Material) modifier.glassEffect(GlassMaterialDefaults.toolbar, RectangleShape) else modifier

/**
 * Material 3's `TopAppBar`, iOS 26 style: no container; the navigation icon in a round glass button and
 * the actions grouped on one glass capsule. [layout] = [GlassLayout.Material] makes the whole bar glass.
 */
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    layout: GlassLayout = GlassLayout.Ios,
) = M3TopAppBar(
    title = title, modifier = barModifier(modifier, layout),
    navigationIcon = navigationIcon?.let { if (layout == GlassLayout.Ios) glassNavigation(it) else it } ?: {},
    actions = actions?.let { if (layout == GlassLayout.Ios) glassActions(it) else it } ?: {},
    expandedHeight = expandedHeight, windowInsets = windowInsets, colors = clearBarColors(colors),
    scrollBehavior = scrollBehavior,
)

/** Material 3's `CenterAlignedTopAppBar`, iOS 26 style (the title centred, as on iOS). */
@Composable
fun CenterAlignedTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    layout: GlassLayout = GlassLayout.Ios,
) = M3CenterAlignedTopAppBar(
    title = title, modifier = barModifier(modifier, layout),
    navigationIcon = navigationIcon?.let { if (layout == GlassLayout.Ios) glassNavigation(it) else it } ?: {},
    actions = actions?.let { if (layout == GlassLayout.Ios) glassActions(it) else it } ?: {},
    expandedHeight = expandedHeight, windowInsets = windowInsets, colors = clearBarColors(colors),
    scrollBehavior = scrollBehavior,
)

/** Material 3's `MediumTopAppBar`, iOS 26 style. */
@Composable
fun MediumTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.mediumTopAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    layout: GlassLayout = GlassLayout.Ios,
) = M3MediumTopAppBar(
    title = title, modifier = barModifier(modifier, layout),
    navigationIcon = navigationIcon?.let { if (layout == GlassLayout.Ios) glassNavigation(it) else it } ?: {},
    actions = actions?.let { if (layout == GlassLayout.Ios) glassActions(it) else it } ?: {},
    windowInsets = windowInsets, colors = clearBarColors(colors), scrollBehavior = scrollBehavior,
)

/** Material 3's `LargeTopAppBar`, iOS 26 style (iOS's large title). */
@Composable
fun LargeTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.largeTopAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    layout: GlassLayout = GlassLayout.Ios,
) = M3LargeTopAppBar(
    title = title, modifier = barModifier(modifier, layout),
    navigationIcon = navigationIcon?.let { if (layout == GlassLayout.Ios) glassNavigation(it) else it } ?: {},
    actions = actions?.let { if (layout == GlassLayout.Ios) glassActions(it) else it } ?: {},
    windowInsets = windowInsets, colors = clearBarColors(colors), scrollBehavior = scrollBehavior,
)

/**
 * Material 3's `BottomAppBar` with actions, iOS 26 style: the actions on a floating glass capsule
 * (iOS's toolbar) and the [floatingActionButton] beside it. [layout] = [GlassLayout.Material] keeps
 * Material's full-width bar, made of glass.
 */
@Composable
fun BottomAppBar(
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable () -> Unit)? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    windowInsets: WindowInsets = BottomAppBarDefaults.windowInsets,
    scrollBehavior: BottomAppBarScrollBehavior? = null,
    layout: GlassLayout = GlassLayout.Ios,
) {
    if (layout == GlassLayout.Material) {
        M3BottomAppBar(actions = actions, modifier = modifier.glassEffect(GlassMaterialDefaults.toolbar, RectangleShape),
            floatingActionButton = floatingActionButton, containerColor = Color.Transparent, contentColor = contentColor,
            tonalElevation = 0.dp, windowInsets = windowInsets, scrollBehavior = scrollBehavior)
        return
    }
    Row(modifier.fillMaxWidth().windowInsetsPadding(windowInsets).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GlassToolbarGroup(glass = GlassMaterialDefaults.toolbar, minHeight = 52.dp, contentPadding = PaddingValues(horizontal = 6.dp)) {
            CompositionLocalProvider(LocalInGlassGroup provides true, LocalContentColor provides contentColor) { actions() }
        }
        Spacer(Modifier.weight(1f))
        floatingActionButton?.invoke()
    }
}

/** Material 3's `BottomAppBar` with free content, on one floating glass capsule. */
@Composable
fun BottomAppBar(
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentPadding: PaddingValues = PaddingValues(horizontal = 6.dp),
    windowInsets: WindowInsets = BottomAppBarDefaults.windowInsets,
    content: @Composable RowScope.() -> Unit,
) {
    GlassToolbarGroup(modifier.fillMaxWidth().windowInsetsPadding(windowInsets).padding(horizontal = 20.dp, vertical = 8.dp),
        glass = GlassMaterialDefaults.toolbar, minHeight = 52.dp, contentPadding = contentPadding) {
        CompositionLocalProvider(LocalInGlassGroup provides true, LocalContentColor provides contentColor) { content() }
    }
}

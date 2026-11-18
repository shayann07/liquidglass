package com.wexpa.liquidglass.material3

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wexpa.liquidglass.Glass
import com.wexpa.liquidglass.GlassInteraction
import com.wexpa.liquidglass.GlassPullShape
import com.wexpa.liquidglass.GlassTabBar
import com.wexpa.liquidglass.GlassTabBarAccessory
import com.wexpa.liquidglass.GlassTabBarStyle
import com.wexpa.liquidglass.glassEffect
import com.wexpa.liquidglass.rememberGlassAccessoryLink
import androidx.compose.material3.NavigationBar as M3NavigationBar
import androidx.compose.material3.NavigationBarItem as M3NavigationBarItem

/**
 * Material 3's `NavigationBar`, as iOS 26's tab bar: a floating glass capsule whose selection is a
 * lens you can tap, hold, drag and throw, measured on recordings of iOS. Write the items exactly as
 * for Material 3:
 *
 * ```kotlin
 * NavigationBar {
 *     tabs.forEachIndexed { i, tab ->
 *         NavigationBarItem(selected = i == selected, onClick = { selected = i },
 *             icon = { Icon(tab.icon, null) }, label = { Text(tab.label) })
 *     }
 * }
 * ```
 *
 * [accessory] adds a round glass button beside the bar (iOS's search button) that the bar fuses with
 * while either is pressed; [onAccessoryClick] is its click. [layout] = [GlassLayout.Material] keeps
 * Material 3's full-width bar and indicator instead, made of glass. [containerColor] and
 * [tonalElevation] apply only to that layout: the iOS bar is glass, whatever is behind it.
 * Needs a glass [Scaffold] or `LiquidGlassTheme` above it.
 */
@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = NavigationBarDefaults.containerColor,
    contentColor: Color = MaterialTheme.colorScheme.contentColorFor(containerColor),
    tonalElevation: Dp = NavigationBarDefaults.Elevation,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    layout: GlassLayout = GlassLayout.Ios,
    accessory: (@Composable () -> Unit)? = null,
    onAccessoryClick: () -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    if (layout == GlassLayout.Material) {
        CompositionLocalProvider(LocalNavigationLayout provides GlassLayout.Material) {
            M3NavigationBar(modifier.glassEffect(GlassMaterialDefaults.forContainer(Color.Transparent), androidx.compose.ui.graphics.RectangleShape),
                Color.Transparent, contentColor, 0.dp, windowInsets, content)
        }
        return
    }
    val items = remember { NavigationItems() }
    // The items register themselves; nothing is laid out here.
    CompositionLocalProvider(LocalNavigationItems provides items, LocalNavigationLayout provides GlassLayout.Ios) {
        Row(Modifier.size(0.dp)) { content() }
    }
    val slots = items.slots
    if (slots.isEmpty()) return
    val dark = MaterialTheme.colorScheme.isDark
    val selected = slots.indexOfFirst { it.selected }.coerceAtLeast(0)
    val link = if (accessory != null) rememberGlassAccessoryLink() else null
    val barHeight = 62.dp
    val gap = 8.dp
    Row(
        modifier.fillMaxWidth().windowInsetsPadding(windowInsets).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassTabBar(
            itemCount = slots.size,
            selectedIndex = selected,
            onSelected = { i -> slots.getOrNull(i)?.let { if (it.enabled) it.onClick() } },
            modifier = Modifier.weight(1f),
            style = GlassTabBarStyle.Calm(dark).copy(height = barHeight),
            trailingAccessory = if (accessory != null) GlassTabBarAccessory(barHeight, gap = gap, yieldsToLens = true,
                lensFusion = 12.dp, link = link) else null,
        ) { index, isSelected ->
            slots.getOrNull(index)?.let { NavigationItemContent(it, isSelected) }
        }
        if (accessory != null) {
            Spacer(Modifier.width(gap))
            Box(
                Modifier.size(barHeight)
                    .glassEffect(Glass.TabBar.interactive(response = GlassInteraction.Fluid),

                        CircleShape, link = link)
                    .clip(CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                        role = Role.Button, onClick = onAccessoryClick),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColorOn(dark)) { accessory() }
            }
        }
    }
}

/**
 * Material 3's `NavigationBarItem`, inside a glass [NavigationBar]. The same parameters; [colors]'
 * selected colours draw the tab seen through the lens and its unselected colours the tabs on the bar.
 * The icon and label are drawn twice (on the bar and inside the lens), so keep them the same shape
 * either way and let [colors] change only their colour.
 */
@Composable
fun RowScope.NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    colors: NavigationBarItemColors = GlassNavigationDefaults.itemColors(),
    interactionSource: MutableInteractionSource? = null,
) {
    if (LocalNavigationLayout.current == GlassLayout.Material) {
        M3NavigationBarItem(selected, onClick, icon, modifier, enabled, label, alwaysShowLabel, colors, interactionSource)
        return
    }
    val items = LocalNavigationItems.current ?: error("NavigationBarItem must be inside a glass NavigationBar")
    val slot = remember { NavigationSlot() }
    slot.selected = selected; slot.onClick = onClick; slot.icon = icon; slot.enabled = enabled
    slot.label = label; slot.alwaysShowLabel = alwaysShowLabel; slot.colors = colors
    DisposableEffect(items, slot) {
        items.slots += slot
        onDispose { items.slots -= slot }
    }
}

/** Colours for glass navigation items: the scheme's primary for the selected tab, onSurface for the rest. */
object GlassNavigationDefaults {
    @Composable
    fun itemColors(
        selectedColor: Color = MaterialTheme.colorScheme.primary,
        unselectedColor: Color = MaterialTheme.colorScheme.onSurface,
        disabledColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
    ): NavigationBarItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = selectedColor, selectedTextColor = selectedColor, indicatorColor = Color.Transparent,
        unselectedIconColor = unselectedColor, unselectedTextColor = unselectedColor,
        disabledIconColor = disabledColor, disabledTextColor = disabledColor,
    )
}

// ---- Internals ------------------------------------------------------------------------------------

@Stable
internal class NavigationSlot {
    var selected by mutableStateOf(false)
    var onClick: () -> Unit by mutableStateOf({})
    var icon: @Composable () -> Unit by mutableStateOf({})
    var label: (@Composable () -> Unit)? by mutableStateOf(null)
    var enabled by mutableStateOf(true)
    var alwaysShowLabel by mutableStateOf(true)
    var colors: NavigationBarItemColors? by mutableStateOf(null)
}

@Stable
internal class NavigationItems { val slots = mutableStateListOf<NavigationSlot>() }

internal val LocalNavigationItems = staticCompositionLocalOf<NavigationItems?> { null }
internal val LocalNavigationLayout = staticCompositionLocalOf { GlassLayout.Ios }

@Composable
private fun NavigationItemContent(slot: NavigationSlot, selected: Boolean) {
    val c = slot.colors
    val iconColor = when {
        !slot.enabled -> c?.disabledIconColor ?: Color.Gray
        selected -> c?.selectedIconColor ?: MaterialTheme.colorScheme.primary
        else -> c?.unselectedIconColor ?: MaterialTheme.colorScheme.onSurface
    }
    val textColor = when {
        !slot.enabled -> c?.disabledTextColor ?: Color.Gray
        selected -> c?.selectedTextColor ?: MaterialTheme.colorScheme.primary
        else -> c?.unselectedTextColor ?: MaterialTheme.colorScheme.onSurface
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CompositionLocalProvider(LocalContentColor provides iconColor) { Box(Modifier.size(24.dp), Alignment.Center) { slot.icon() } }
        val label = slot.label
        if (label != null && (slot.alwaysShowLabel || slot.selected)) {
            Spacer(Modifier.height(2.dp))
            CompositionLocalProvider(LocalContentColor provides textColor) {
                ProvideTextStyle(MaterialTheme.typography.labelSmall) { label() }
            }
        }
    }
}

@Composable
internal fun contentColorOn(dark: Boolean): Color = if (dark) Color.White else Color.Black

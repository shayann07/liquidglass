package com.wexpa.liquidglass

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Several toolbar actions on one glass capsule, as iOS groups a toolbar's buttons: one pill, the
 * actions in a row inside it. A button that stands apart is its own round piece of glass beside the
 * group; put it next to this one in a [Row].
 *
 * ```kotlin
 * Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
 *     GlassToolbarGroup { IconButton(onShare) { ... }; IconButton(onFavourite) { ... }; IconButton(onInfo) { ... } }
 *     Box(Modifier.size(44.dp).glassEffect(Glass.Regular.interactive(), CircleShape)) { ... }
 * }
 * ```
 *
 * The actions draw no glass of their own: the capsule is the only glass here, so glass is not stacked.
 * The capsule is [glass] (the Photos toolbar's measured material by default) in a [CapsuleShape].
 * The height, the item padding and the gap to a neighbouring round button are authored: no iOS
 * recording measures a toolbar's group geometry as a rule, only the Photos screen's own elements.
 * Needs a [LiquidGlassTheme] above it, like [glassEffect].
 *
 * @param glass the capsule's material; [Glass.Toolbar] by default.
 * @param minHeight the group is at least this tall, which is also the touch target's height (44 dp).
 * @param contentPadding space between the capsule's ends and its first and last action.
 */
@Composable
fun GlassToolbarGroup(
    modifier: Modifier = Modifier,
    glass: Glass = Glass.Toolbar,
    minHeight: Dp = 44.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 4.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.heightIn(min = minHeight).glassEffect(glass, CapsuleShape).padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

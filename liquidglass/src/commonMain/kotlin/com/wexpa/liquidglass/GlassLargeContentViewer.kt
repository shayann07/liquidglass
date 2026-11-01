package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.roundToInt

/**
 * The text scale from which a held tab shows the [GlassLargeContentViewer] (#145). iOS shows its Large Content
 * Viewer at the accessibility text sizes, where tab labels stop growing; 1.5 is Android's largest common font
 * scale step below 2. Authored.
 */
internal const val GlassLargeContentFontScale = 1.5f

/** How much the viewer magnifies the tab's own content. Authored: iOS's HUD shows the glyph and label about 2x. */
internal const val GlassLargeContentScale = 2.2f

internal const val GlassLargeContentViewerTag = "glass-large-content-viewer"

/** Centred in the window, whatever the anchor. */
private object CentredInWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
                                   popupContentSize: IntSize): IntOffset =
        IntOffset((windowSize.width - popupContentSize.width) / 2, (windowSize.height - popupContentSize.height) / 2)
}

/**
 * iOS's Large Content Viewer for a tab bar (#145): while a user with an accessibility text size holds a tab,
 * a platter in the middle of the window shows that tab's icon and label magnified, following the finger
 * from tab to tab. [content] is the tab's own content, drawn selected. Hidden from accessibility services,
 * which already announce the tab.
 */
@Composable
internal fun GlassLargeContentViewer(dark: Boolean, content: @Composable () -> Unit) {
    Popup(popupPositionProvider = CentredInWindow) {
        Box(
            Modifier
                // Only its tag, for tests: the tab itself is what accessibility services announce.
                .clearAndSetSemantics { testTag = GlassLargeContentViewerTag }
                .defaultMinSize(160.dp, 160.dp)
                // iOS's HUD is the system's thick material: light grey in light appearance, dark grey in dark.
                .background(if (dark) Color(0xF02C2C2E) else Color(0xF0F2F2F7), RoundedCornerShape(28.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.layout { measurable, _ ->
                    val p = measurable.measure(Constraints())
                    val w = (p.width * GlassLargeContentScale).roundToInt()
                    val h = (p.height * GlassLargeContentScale).roundToInt()
                    layout(w, h) {
                        p.placeWithLayer(0, 0) {
                            scaleX = GlassLargeContentScale
                            scaleY = GlassLargeContentScale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                    }
                },
            ) { content() }
        }
    }
}

package com.wexpa.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Whether a [GlassTabBar]'s search button has expanded into its search field (#112), and how far.
 *
 * The app owns it: [expand] and [collapse] are the only writes. The bar's search button calls [expand]
 * when tapped and the close button calls [collapse]. Both can be called at any time, including
 * mid-transition: the progress reverses from where it is. Create one with [rememberGlassSearchState].
 */
@Stable
class GlassSearchState(initiallyExpanded: Boolean = false) {
    /** True from the moment search is asked to open until it is asked to close. */
    var expanded: Boolean by mutableStateOf(initiallyExpanded)
        private set

    internal val progress = Animatable(if (initiallyExpanded) 1f else 0f)

    /** The transition's progress, 0 (the bar and its search button) to 1 (the field and the close button). */
    val fraction: Float get() = progress.value

    fun expand() { expanded = true }

    fun collapse() { expanded = false }
}

/** A [GlassSearchState] that survives recomposition. */
@Composable
fun rememberGlassSearchState(initiallyExpanded: Boolean = false): GlassSearchState =
    remember { GlassSearchState(initiallyExpanded) }

/**
 * Timing of the search transition. All AUTHORED: the only measured fact is that the iOS 27 sample
 * reference (one recording, 29 fps) completes the morph within about 9 frames, about 0.3 s; the frames
 * in between were not resolved. Entering eases out; leaving eases in and is about a third shorter.
 */
internal object GlassSearchMotion {
    const val ExpandMillis = 300
    const val CollapseMillis = 200
    val Expand = tween<Float>(ExpandMillis, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))
    val Collapse = tween<Float>(CollapseMillis, easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f))
}

/**
 * Where the pieces of search mode are, in dp inside a row of a given width, at a transition progress.
 *
 * Rest (progress 0) is the bar, a gap and the search button, a circle as tall as the bar. Open
 * (progress 1) is the search field on the left and the close button on the right: MEASURED on the iOS 27
 * sample reference (`ios_27_liquid_ui_sample_ref.MP4` 40.5 s, one recording, 720 px wide at 1.846 px/dp,
 * tone-mapped display colours): field 47.7 dp tall, close button 47.1 dp across, 9.2 dp between them,
 * both centred where the bar was. In between everything is AUTHORED: the field grows out of the button
 * (same centre line, edges moving linearly in progress), the bar narrows to a circle by progress
 * [BarCircleBy] and gives up its material between [BarMaterialFrom] and [BarMaterialTo], and the close
 * button grows from nothing once [CloseFrom] has passed.
 */
internal class GlassSearchRects(
    val barWidth: Float,
    /** 0 to 1: how much of the bar's material is left. */
    val barMaterial: Float,
    val fieldLeft: Float, val fieldRight: Float, val fieldHeight: Float,
    val closeDiameter: Float, val closeCentreX: Float,
    val centreY: Float,
) {
    val fieldWidth: Float get() = fieldRight - fieldLeft
}

/** Measured, see [GlassSearchRects]. */
internal const val GlassSearchFieldHeight = 47.7f
/** Measured, see [GlassSearchRects]. */
internal const val GlassSearchCloseDiameter = 47.1f
/** Measured, see [GlassSearchRects]. */
internal const val GlassSearchCloseGap = 9.2f
/** Authored. */
private const val BarCircleBy = 0.6f
private const val CloseFrom = 0.25f
private const val BarMaterialFrom = 0.45f
private const val BarMaterialTo = 0.85f

internal fun glassSearchRects(width: Float, barHeight: Float, gap: Float, progress: Float): GlassSearchRects {
    val p = progress.coerceIn(0f, 1f)
    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    val restBar = width - barHeight - gap
    val barWidth = lerp(restBar, barHeight, (p / BarCircleBy).coerceIn(0f, 1f))
    val material = 1f - ((p - BarMaterialFrom) / (BarMaterialTo - BarMaterialFrom)).coerceIn(0f, 1f)
    val fieldRight = lerp(width, width - GlassSearchCloseDiameter - GlassSearchCloseGap, p)
    val closeScale = ((p - CloseFrom) / (1f - CloseFrom)).coerceIn(0f, 1f)
    return GlassSearchRects(
        barWidth = barWidth, barMaterial = material,
        fieldLeft = lerp(width - barHeight, 0f, p), fieldRight = fieldRight,
        fieldHeight = lerp(barHeight, GlassSearchFieldHeight, p),
        closeDiameter = GlassSearchCloseDiameter * closeScale,
        closeCentreX = width - GlassSearchCloseDiameter / 2f,
        centreY = barHeight / 2f,
    )
}

/**
 * A [GlassTabBar] with a search button beside it that expands into a search field (#112), as the iOS 27
 * Phone app's does: tap the button and the bar gives way while the button widens to the left into
 * [searchField] and a round close button grows out of the button's old place. The close button, Escape
 * or [GlassSearchState.collapse] reverses it, from wherever the transition is.
 *
 * On by default for any bar given a [searchState]: there is no flag. A bar without one is the plain
 * [GlassTabBar], unchanged.
 *
 * Lay this out as the whole row: [modifier] sets the row's width. At rest the bar is
 * `width - height - gap` wide, then the [gap], then the search button; at the end the field spans the
 * row up to the close button, so give the row the width you want the field to have.
 *
 * [searchField] is yours, normally a text field. It is laid out at its open width at all times, clipped
 * while the button is still narrow, and takes focus once search has opened. The magnifier at the
 * field's leading end is drawn here. The keyboard is the system's, and keeping the row above it (the
 * IME inset) is the host's, as with any bottom bar.
 *
 * Selection is untouched: the bar is only hidden while search is open. With [motionEnabled] false
 * (Reduce Motion) the two states swap with no travel.
 *
 * @param searchLabel the button's accessibility label.
 * @param closeLabel the close button's accessibility label.
 */
@Composable
fun GlassTabBar(
    itemCount: Int,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    searchState: GlassSearchState,
    searchField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassTabBarStyle = GlassTabBarStyle.Calm(LocalGlassDefaults.current.dark),
    motionEnabled: Boolean = !rememberGlassSystemAccessibility().reduceMotion,
    gap: Dp = 8.dp,
    searchLabel: String = "Search",
    closeLabel: String = "Close search",
    item: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    val expanded = searchState.expanded
    LaunchedEffect(searchState, expanded, motionEnabled) {
        val target = if (expanded) 1f else 0f
        if (!motionEnabled) searchState.progress.snapTo(target)
        else searchState.progress.animateTo(target, if (expanded) GlassSearchMotion.Expand else GlassSearchMotion.Collapse)
    }
    val p = searchState.fraction
    val link = rememberGlassAccessoryLink()
    val focus = remember { FocusRequester() }
    LaunchedEffect(searchState, expanded) {
        if (expanded) {
            // The field is composed and laid out at its open width a frame or two after the tap; then it takes focus.
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            focus.requestFocus()
        }
    }
    val ink = if (LocalGlassDefaults.current.dark) Color.White else Color(0xFF1D1D1D)
    BoxWithConstraints(
        modifier.height(style.height).onPreviewKeyEvent {
            if (searchState.expanded && it.type == KeyEventType.KeyDown && it.key == Key.Escape) { searchState.collapse(); true } else false
        },
    ) {
        val rects = glassSearchRects(maxWidth.value, style.height.value, gap.value, p)
        val h = style.height
        val rowWidth = maxWidth.value
        Box {
            if (rects.barMaterial > 0.001f) {
                Box(Modifier.width(rects.barWidth.dp).height(h)) {
                    GlassTabBar(itemCount, selectedIndex, onSelected, Modifier.fillMaxHeight(), style, motionEnabled,
                        materialize = rects.barMaterial,
                        trailingAccessory = GlassTabBarAccessory(h, gap = gap, yieldsToLens = true, link = link),
                    ) { index, isSelected ->
                        // Every tab but the selected one leaves as the bar narrows to a circle.
                        val keep = index == selectedIndex.coerceIn(0, itemCount - 1)
                        val alpha = if (keep) 1f else (1f - p / BarCircleBy).coerceIn(0f, 1f)
                        Box(Modifier.graphicsLayer { this.alpha = alpha }) { item(index, isSelected) }
                    }
                    // Once search has started, what is left of the bar is a button back to it.
                    if (p > 0.001f) Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null,
                        role = Role.Button, onClickLabel = closeLabel) { searchState.collapse() })
                }
            }
            // The search button, which becomes the field.
            val glass = remember(style) {
                Glass(style.bar, light = style.light).interactive(
                    response = GlassInteraction.Pullable.copy(pullShape = GlassPullShape.Free, pullLiftShare = 0.65f))
            }
            val openGlass = remember(style) { Glass(style.bar, light = style.light) }
            Box(
                Modifier.offset(rects.fieldLeft.dp, (rects.centreY - rects.fieldHeight / 2f).dp)
                    .size(rects.fieldWidth.dp, rects.fieldHeight.dp)
                    .then(if (!expanded) Modifier.clickable(remember { MutableInteractionSource() }, null,
                        role = Role.Button, onClickLabel = searchLabel) { searchState.expand() } else Modifier)
                    .glassEffect(if (expanded) openGlass else glass, CircleShape, link = link)
                    .semantics { if (!expanded) contentDescription = searchLabel }
                    .clipToBounds(),
            ) {
                val openWidth = (rowWidth - GlassSearchCloseDiameter - GlassSearchCloseGap).dp
                Canvas(Modifier.align(Alignment.CenterStart)
                    .offset(((rects.fieldHeight - 24.5f) / 2f).coerceAtLeast(0f).dp).size(24.5.dp)) { searchGlyph(ink) }
                if (p > 0.02f || expanded) {
                    val reveal = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
                    Row(Modifier.requiredWidth(openWidth).fillMaxHeight().padding(start = 40.dp)
                        .graphicsLayer { alpha = reveal }.focusRequester(focus).focusGroup(),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(openWidth - 48.dp)) { searchField() }
                    }
                }
            }
            if (rects.closeDiameter > 0.5f) {
                Box(
                    Modifier.offset((rects.closeCentreX - rects.closeDiameter / 2f).dp, (rects.centreY - rects.closeDiameter / 2f).dp)
                        .size(rects.closeDiameter.dp)
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.Button, onClickLabel = closeLabel) { searchState.collapse() }
                        .glassEffect(openGlass, CircleShape)
                        .semantics { contentDescription = closeLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size((rects.closeDiameter * 0.34f).dp)) { closeGlyph(ink) }
                }
            }
        }
    }
}

private fun DrawScope.searchGlyph(color: Color) {
    val r = size.minDimension * .32f
    val c = Offset(size.width * .42f, size.height * .42f)
    drawCircle(color, r, c, style = Stroke(size.minDimension * .089f))
    drawLine(color, Offset(c.x + r * .72f, c.y + r * .72f), Offset(size.width * .92f, size.height * .92f),
        size.minDimension * .098f, StrokeCap.Round)
}

private fun DrawScope.closeGlyph(color: Color) {
    val w = size.minDimension * .09f
    drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), w, StrokeCap.Round)
    drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
}
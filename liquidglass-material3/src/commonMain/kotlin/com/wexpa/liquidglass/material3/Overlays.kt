@file:OptIn(ExperimentalMaterial3Api::class)

package com.wexpa.liquidglass.material3

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.wexpa.liquidglass.CapsuleShape
import com.wexpa.liquidglass.Glass
import com.wexpa.liquidglass.GlassOverlay
import com.wexpa.liquidglass.glassEffect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.material3.DropdownMenuItem as M3DropdownMenuItem

private val Scrim = Color.Black.copy(alpha = .2f)

// ---- Dialogs ---------------------------------------------------------------------------------------

/**
 * Material 3's `AlertDialog` as an iOS 26 alert: a glass panel over a light scrim, drawn in the same
 * window as the app (in [GlassOverlay]) so the glass refracts the screen behind it. The same parameters.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(34.dp),
    glass: Glass = GlassMaterialDefaults.panel,
    iconContentColor: Color = MaterialTheme.colorScheme.secondary,
    titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
    textContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    properties: DialogProperties = DialogProperties(),
) = BasicAlertDialog(onDismissRequest, modifier, properties) {
    Column(Modifier.widthIn(min = 270.dp, max = 400.dp).glassEffect(glass, shape).padding(24.dp)) { OnGlassPanel {
        icon?.let {
            CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)) { it() }
            }
        }
        title?.let {
            CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                    Box(Modifier.align(if (icon != null) Alignment.CenterHorizontally else Alignment.Start).padding(bottom = 16.dp)) { it() }
                }
            }
        }
        text?.let {
            CompositionLocalProvider(LocalContentColor provides textContentColor) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) { Box(Modifier.padding(bottom = 24.dp)) { it() } }
            }
        }
        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dismissButton?.invoke()
            confirmButton()
        }
    } }
}

/** Material 3's `BasicAlertDialog`: [content] centred over a light scrim, in the app's window. */
@Composable
fun BasicAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) = GlassOverlay {
    DismissOnBack(properties.dismissOnBackPress) { onDismissRequest() }
    Box(Modifier.fillMaxSize().background(Scrim).clickable(interactionSource = remember { MutableInteractionSource() },
        indication = null) { if (properties.dismissOnClickOutside) onDismissRequest() })
    Box(modifier.align(Alignment.Center).padding(24.dp)
        // Swallow taps on the panel so they do not reach the scrim.
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) { content() }
}

// ---- Bottom sheet ----------------------------------------------------------------------------------

/**
 * The state of a glass [ModalBottomSheet], with Material 3's `SheetState` calls: [show], [hide],
 * [isVisible], [currentValue], [targetValue].
 */
@Stable
class SheetState internal constructor(
    internal val skipPartiallyExpanded: Boolean,
    internal val confirmValueChange: (SheetValue) -> Boolean,
) {
    /** 0 hidden, 1 fully expanded; the partially expanded stop lies at [partialProgress]. */
    internal val progress = Animatable(0f)

    /**
     * Where the partially expanded stop is, as [progress]: the sheet showing half the window, iOS's
     * medium detent. 1 when the sheet is no taller than that (as in Material, the stop is then the
     * expanded one). The sheet sets it once it has measured itself and the window.
     */
    internal var partialProgress by mutableFloatStateOf(1f)

    /** Whether this sheet has a partially expanded stop distinct from the expanded one. */
    val hasPartiallyExpandedState: Boolean get() = !skipPartiallyExpanded && partialProgress < .999f
    val hasExpandedState: Boolean get() = true

    var targetValue by mutableStateOf(SheetValue.Hidden); private set
    val currentValue: SheetValue get() = when {
        progress.value <= 0f -> SheetValue.Hidden
        hasPartiallyExpandedState && progress.value < (partialProgress + 1f) / 2f -> SheetValue.PartiallyExpanded
        else -> SheetValue.Expanded
    }
    val isVisible: Boolean get() = progress.value > 0f || targetValue != SheetValue.Hidden

    private suspend fun settle(value: SheetValue) {
        if (!confirmValueChange(value)) return
        targetValue = value
        val to = when (value) {
            SheetValue.Hidden -> 0f
            SheetValue.PartiallyExpanded -> partialProgress
            SheetValue.Expanded -> 1f
        }
        progress.animateTo(to, if (value == SheetValue.Hidden) spring(dampingRatio = 1f, stiffness = 500f)
        else spring(dampingRatio = .85f, stiffness = 380f))
    }

    /** Shows the sheet at its partially expanded stop, or expanded when it has none. */
    suspend fun show() = settle(if (hasPartiallyExpandedState) SheetValue.PartiallyExpanded else SheetValue.Expanded)

    suspend fun hide() = settle(SheetValue.Hidden)

    suspend fun expand() = settle(SheetValue.Expanded)

    /** Moves to the partially expanded stop. As in Material, a sheet that skips it cannot. */
    suspend fun partialExpand() {
        check(!skipPartiallyExpanded) { "This sheet skips the partially expanded state" }
        settle(if (hasPartiallyExpandedState) SheetValue.PartiallyExpanded else SheetValue.Expanded)
    }

    /** Where a drag released at [velocity] (px/s, positive downward) comes to rest. */
    internal fun restingValue(velocity: Float): SheetValue {
        val p = progress.value
        val partial = hasPartiallyExpandedState
        return when {
            velocity > 1200f -> if (partial && p > partialProgress + .02f) SheetValue.PartiallyExpanded else SheetValue.Hidden
            velocity < -1200f -> SheetValue.Expanded
            partial && p < partialProgress * .6f -> SheetValue.Hidden
            !partial && p < .7f -> SheetValue.Hidden
            partial && p < (partialProgress + 1f) / 2f -> SheetValue.PartiallyExpanded
            else -> SheetValue.Expanded
        }
    }
}

/** A [SheetState] for a glass [ModalBottomSheet], as Material 3's `rememberModalBottomSheetState`. */
@Composable
fun rememberModalBottomSheetState(
    skipPartiallyExpanded: Boolean = false,
    confirmValueChange: (SheetValue) -> Boolean = { true },
): SheetState = remember { SheetState(skipPartiallyExpanded, confirmValueChange) }

/**
 * Material 3's `ModalBottomSheet` as an iOS 26 sheet: a floating glass panel inset from the screen's
 * edges, over a light scrim, in the app's window. Drag it down or tap the scrim to dismiss.
 */
@Composable
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = 640.dp,
    shape: Shape = RoundedCornerShape(38.dp),
    glass: Glass = GlassMaterialDefaults.panel,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    scrimColor: Color = Scrim,
    dragHandle: @Composable (() -> Unit)? = { DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    var height by remember { mutableIntStateOf(0) }
    var window by remember { mutableIntStateOf(0) }
    // The partial stop shows half the window: the sheet's top at the window's middle. Set as soon
    // as both are measured, before the sheet is first shown.
    fun measured() {
        if (height > 0 && window > 0) {
            val partialOffset = (height - window / 2f).coerceAtLeast(0f)
            sheetState.partialProgress = (1f - partialOffset / (height + 64f)).coerceIn(0f, 1f)
        }
    }
    LaunchedEffect(sheetState) {
        snapshotFlow { height > 0 && window > 0 }.first { it }
        sheetState.show()
    }
    val dismiss: () -> Unit = { scope.launch { sheetState.hide(); onDismissRequest() } }
    val drag = rememberDraggableState { delta ->
        scope.launch { sheetState.progress.snapTo((sheetState.progress.value - delta / (height + 64f)).coerceIn(0f, 1f)) }
    }
    GlassOverlay {
        DismissOnBack { dismiss() }
        Box(Modifier.fillMaxSize().onSizeChanged { window = it.height; measured() }.graphicsLayer { alpha = sheetState.progress.value }.background(scrimColor)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss))
        Column(
            modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = sheetMaxWidth)
                .fillMaxWidth()
                .windowInsetsPadding(contentWindowInsets())
                .padding(8.dp)
                .onSizeChanged { height = it.height.coerceAtLeast(1); measured() }
                .offset { IntOffset(0, ((1f - sheetState.progress.value) * (height + 64)).roundToInt()) }
                .draggable(drag, Orientation.Vertical, onDragStopped = { velocity ->
                    when (sheetState.restingValue(velocity)) {
                        SheetValue.Hidden -> dismiss()
                        SheetValue.PartiallyExpanded -> sheetState.partialExpand()
                        SheetValue.Expanded -> sheetState.expand()
                    }
                })
                .glassEffect(glass, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(bottom = 16.dp),
        ) {
            dragHandle?.let { Box(Modifier.align(Alignment.CenterHorizontally)) { it() } }
            OnGlassPanel { CompositionLocalProvider(LocalContentColor provides contentColor) { content() } }
        }
    }
}

/** The sheet's grabber, as iOS draws it. */
@Composable
fun DragHandle(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = .3f)) =
    Box(modifier.padding(vertical = 10.dp).size(36.dp, 5.dp).background(color, CapsuleShape))

// ---- Menus -----------------------------------------------------------------------------------------

/**
 * Material 3's `DropdownMenu` as an iOS menu: a glass panel below its anchor (the layout it is
 * placed in, as Material's), in the app's window. Tap outside to dismiss. The panel is
 * [GlassMaterialDefaults.menu] (blurrier and more opaque than a card); pass [GlassMaterialDefaults.panel]
 * for the look before 1.1.0.
 */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
    // 34 dp (#102, 26 October): iOS 27's menu corner is ~100 px at 3x (COSG3073 279.5 s); was 24.
    shape: Shape = RoundedCornerShape(34.dp),
    glass: Glass = GlassMaterialDefaults.menu,
    content: @Composable ColumnScope.() -> Unit,
) {
    var anchor by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    // Where the anchor (this call's parent) is, in the window.
    Layout(Modifier.onGloballyPositioned { c -> c.parentLayoutCoordinates?.let { anchor = it.boundsInRoot() } }) { _, _ -> layout(0, 0) {} }
    if (!expanded) return
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var window by remember { mutableStateOf(IntSize.Zero) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    GlassOverlay {
        DismissOnBack { onDismissRequest() }
        Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot(); window = it.size }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismissRequest))
        Column(
            modifier
                .onSizeChanged { menuSize = it }
                .offset {
                    with(density) {
                        // Below the anchor, starting at its start, as Material's; where that would
                        // leave the window, ending at its end, and above it, then kept inside.
                        val dx = offset.x.toPx(); val dy = offset.y.toPx()
                        var x = anchor.left - origin.x + dx
                        if (x + menuSize.width > window.width) x = anchor.right - origin.x - menuSize.width - dx
                        var y = anchor.bottom - origin.y + dy
                        if (y + menuSize.height > window.height) y = anchor.top - origin.y - menuSize.height - dy
                        IntOffset(
                            x.roundToInt().coerceIn(0, maxOf(0, window.width - menuSize.width)),
                            y.roundToInt().coerceIn(0, maxOf(0, window.height - menuSize.height)),
                        )
                    }
                }
                .widthIn(min = 180.dp, max = 280.dp)
                .glassEffect(glass, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) { OnGlassPanel { content() } }
    }
}

/** Material 3's `DropdownMenuItem`, inside a glass [DropdownMenu]. */
@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource? = null,
) = M3DropdownMenuItem(text = text, onClick = onClick, modifier = modifier, leadingIcon = leadingIcon,
    trailingIcon = trailingIcon, enabled = enabled, colors = colors, contentPadding = contentPadding,
    interactionSource = interactionSource)

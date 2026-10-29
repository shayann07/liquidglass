package com.wexpa.liquidglass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.modifier.modifierLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The root of Liquid Glass: put it around the app once, as `MaterialTheme` is, and every
 * [glassEffect] below it refracts the screen behind it.
 *
 * ```kotlin
 * LiquidGlassTheme {
 *     App()
 * }
 * ```
 *
 * It records what is drawn below it once per frame. Glass inside it draws nothing in place: it is
 * drawn over the recording, with its own content on top, so no glass ever sees itself. A theme
 * nested inside another changes the defaults, appearance and accessibility settings for its
 * subtree; the outermost one records, and its [background] is the one used.
 *
 * @param dark the appearance glass variants use; follows the system by default.
 * @param background the colour behind the app wherever it draws nothing.
 * @param glass what [glassEffect] draws when it is given no [Glass].
 * @param containerSpacing [GlassEffectContainer]'s default merge distance.
 * @param renderScale the fraction of full resolution glass renders at, 0.25 to 1. 1 by default.
 * @param reduceTransparency Reduce Transparency, 0 to 1: raises the material's opacity.
 * @param increaseContrast Increase Contrast, 0 to 1: high-contrast material and border.
 */
@Composable
fun LiquidGlassTheme(
    dark: Boolean = isSystemInDarkTheme(),
    background: Color = if (dark) Color.Black else Color.White,
    glass: Glass = Glass.Regular,
    containerSpacing: Dp = 20.dp,
    renderScale: Float = 1f,
    reduceTransparency: Float = 0f,
    increaseContrast: Float = 0f,
    content: @Composable () -> Unit,
) {
    val defaults = GlassDefaults(dark, glass, containerSpacing, reduceTransparency, increaseContrast, renderScale)
    val parent = LocalGlassRegion.current
    if (parent != null) {
        CompositionLocalProvider(LocalGlassDefaults provides defaults, content = content)
        return
    }
    val state = rememberLiquidGlassState(background, 0f, reduceTransparency, increaseContrast, renderScale)
    val region = remember(state) { GlassRegion(state) }
    CompositionLocalProvider(LocalGlassRegion provides region, LocalGlassDefaults provides defaults) {
        Box(Modifier.onGloballyPositioned { region.root = it }, propagateMinConstraints = true) {
            Box(Modifier.liquidGlassSource(state) { region.views.values }, propagateMinConstraints = true) { content() }
            GlassOverlay(region, Modifier.matchParentSize())
        }
    }
}

/**
 * Draws this element as Liquid Glass, as SwiftUI's `glassEffect(_:in:)` does: the screen behind it
 * refracted through [shape], with the element's own content on top.
 *
 * ```kotlin
 * Icon(Icons.Default.Edit, null, Modifier.size(44.dp).glassEffect(Glass.Regular.interactive(), CircleShape))
 * ```
 *
 * Needs a [LiquidGlassTheme] (or a glass `Scaffold`) above it. Without one it draws a frosted,
 * rim-lit stand-in and logs one warning. Layout, hit targets and semantics stay the element's own.
 *
 * Limits of the current renderer: glass is drawn above content that is not glass, and it does not
 * take an ancestor's alpha. To take glass away, switch it to [Glass.Identity] rather than fading a parent.
 *
 * @param glass what to draw; [LiquidGlassTheme]'s default when null.
 * @param shape the outline; a capsule by default, as in SwiftUI.
 * @param link publishes this glass's press and pull to a [GlassTabBar] beside it, whose
 *   [GlassTabBarAccessory] has the same link, so the bar's neck follows it.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.glassEffect(glass: Glass? = null, shape: Shape = CapsuleShape, link: GlassAccessoryLink? = null): Modifier = composed {
    val defaults = LocalGlassDefaults.current
    val resolved = glass ?: defaults.glass
    val style = resolved.style(defaults.dark) ?: return@composed this
    val morph = remember { GlassMorphHandle() }
    val withMorph = modifierLocalProvider(ModifierLocalGlassMorph) { morph }
    LocalGlassMembership.current?.let { return@composed withMorph.then(it.member(shape, morph)) }
    val region = LocalGlassRegion.current
    if (region != null && LocalInGlassOverlay.current) {
        // In the overlay (a dialog, a sheet, a tab bar's portal) nothing is recorded: draw in place.
        return@composed withMorph.liquidGlass(region.rememberStateFor(defaults), shape, style, resolved.light(defaults.dark),
            interaction = if (resolved.isInteractive) resolved.interaction else null, link = link)
    }
    if (region == null) {
        GlassWarnings.missingRoot()
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        return@composed drawWithContent {
            drawGlassFallback(style, resolved.light(defaults.dark), shape.cornerRadiiPx(size, direction, density), with(density) { style.bevel.toPx() })
            drawContent()
        }
    }
    val foreground = rememberGraphicsLayer()
    val entry = remember(region, foreground) { GlassOverlayEntry(foreground) }
    entry.shape = shape
    entry.style = style
    entry.interaction = if (resolved.isInteractive) resolved.interaction else null
    entry.morph = morph
    entry.light = resolved.light(defaults.dark)
    entry.state = region.rememberStateFor(defaults)
    entry.link = link
    DisposableEffect(region, entry) {
        entry.disposed = false
        region.layers += entry
        onDispose { entry.disposed = true; region.layers -= entry }
    }
    var modifier = withMorph.onGloballyPositioned { entry.place(region.root, it) }
    if (resolved.isInteractive) modifier = modifier.pointerInput(entry) { observePress(entry.press) }
    // The content is drawn over the glass by the overlay, so nothing is drawn here: what is recorded
    // as the backdrop holds neither this glass nor what sits on it.
    modifier.drawWithContent { entry.foreground.record { this@drawWithContent.drawContent() } }
}

// ---- Internals ------------------------------------------------------------------------------------

internal val LocalGlassRegion = staticCompositionLocalOf<GlassRegion?> { null }
internal val LocalGlassDefaults = staticCompositionLocalOf {
    GlassDefaults(dark = true, glass = Glass.Regular, containerSpacing = 20.dp)
}

/** One recording of the screen and the glass drawn over it. */
@Stable
internal class GlassRegion(val state: LiquidGlassState) {
    var root: LayoutCoordinates? = null
    /** Only settings with live consumers are mirrored into the shared recording. */
    val views = HashMap<Triple<Float, Float, Float>, LiquidGlassState>()
    private val consumers = HashMap<Triple<Float, Float, Float>, Int>()

    /** Holds one view reference for this consumer until its keys change or composition ends. */
    @Composable
    fun rememberStateFor(d: GlassDefaults): LiquidGlassState =
        remember(this, d.reduceTransparency, d.increaseContrast, d.renderScale) {
            GlassViewLease(this, d)
        }.state

    /** Shares an active settings view, or uses the root state when its settings already match. */
    internal fun acquire(d: GlassDefaults): LiquidGlassState {
        if (d.reduceTransparency == state.frost && d.increaseContrast == state.contrast && d.renderScale == state.renderScale) return state
        val key = Triple(d.reduceTransparency, d.increaseContrast, d.renderScale)
        val view = views.getOrPut(key) {
            LiquidGlassState(state.background, 0f, d.reduceTransparency, d.increaseContrast, d.renderScale).also {
                it.layer = state.layer; it.sourceCoordinates = state.sourceCoordinates; it.sourceSize = state.sourceSize
            }
        }
        consumers[key] = (consumers[key] ?: 0) + 1
        return view
    }

    /** Drops one acquired reference and removes a settings view after its final consumer leaves. */
    internal fun release(view: LiquidGlassState) {
        if (view === state) return
        val key = Triple(view.frost, view.contrast, view.renderScale)
        check(views[key] === view)
        val remaining = consumers.getValue(key) - 1
        if (remaining == 0) {
            consumers.remove(key)
            views.remove(key)
        } else {
            consumers[key] = remaining
        }
    }
    /**
     * The glass elements ([GlassOverlayEntry]) and portals ([GlassPortalEntry]) in the order they
     * entered composition, which the overlay draws them in: a later sibling draws over an earlier one,
     * as in an ordinary layout, and a dialog opened later draws over everything before it.
     */
    val layers = mutableStateListOf<Any>()
}

/**
 * Acquire during remember so siblings in the same composition share a view. RememberObserver
 * releases it both on normal disposal/key replacement and when a composition is abandoned before
 * its effects run. A DisposableEffect alone would leave the latter acquisition behind.
 */
internal class GlassViewLease(private val region: GlassRegion, defaults: GlassDefaults) : RememberObserver {
    val state = region.acquire(defaults)
    private var released = false
    /** Acquisition already happened during construction so same-composition siblings share it. */
    override fun onRemembered() = Unit
    /** Releases the reference on disposal or replacement of a remember key. */
    override fun onForgotten() = release()
    /** Releases acquisitions from compositions that never reached the effects phase. */
    override fun onAbandoned() = release()
    /** Makes repeated lifecycle notifications harmless to other consumers of the same view. */
    private fun release() {
        if (!released) {
            released = true
            region.release(state)
        }
    }
}

/** A [glassEffect] element: where it is, what it looks like, and its content to draw on top. */
@Stable
internal class GlassOverlayEntry(
    /** The element's content, recorded where it is laid out and drawn by the overlay over the glass. */
    val foreground: GraphicsLayer,
) {
    var bounds by mutableStateOf(Rect.Zero)
    /** The part of [bounds] its ancestors leave visible (a scrolled list clips it). */
    var visible by mutableStateOf(Rect.Zero)
    var shape: Shape by mutableStateOf(CapsuleShape)
    var style: GlassStyle by mutableStateOf(GlassStyle.Regular)
    var interaction: GlassInteraction? by mutableStateOf(null)
    var light: GlassLight by mutableStateOf(GlassLight.Default)
    var state: LiquidGlassState? by mutableStateOf(null)
    var link: GlassAccessoryLink? by mutableStateOf(null)
    val press = GlassPressSource()
    /** The element left composition: its layers may already be released. */
    var disposed = false
    var morph: GlassMorphHandle? = null
    private var root: LayoutCoordinates? = null

    /** Where to draw: a morph's animated bounds while one runs, the laid-out bounds otherwise. */
    val drawn: Rect get() {
        val m = morph?.tracker?.shown ?: return bounds
        val r = root
        val origin = if (r != null && r.isAttached) r.positionInRoot() else Offset.Zero
        return m.translate(-origin)
    }

    fun place(root: LayoutCoordinates?, coordinates: LayoutCoordinates) {
        if (root == null || !root.isAttached || !coordinates.isAttached) return
        this.root = root
        val origin = root.localPositionOf(coordinates, Offset.Zero)
        bounds = Rect(origin, Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
        visible = coordinates.boundsInRoot().translate(-root.positionInRoot())
    }
}

@Composable
internal fun GlassOverlay(region: GlassRegion, modifier: Modifier) {
    Box(modifier) {
        for (layer in region.layers) key(layer) {
            when (layer) {
                is GlassOverlayEntry -> OverlayGlass(region, layer)
                is GlassPortalEntry -> PortalContent(region, layer)
            }
        }
    }
}

@Composable
private fun OverlayGlass(region: GlassRegion, entry: GlassOverlayEntry) {
    Box(
        Modifier
            .layout { measurable, _ ->
                val b = entry.drawn
                val w = b.width.roundToInt().coerceAtLeast(0)
                val h = b.height.roundToInt().coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { placeable.place(b.left.roundToInt(), b.top.roundToInt()) }
            }
            .drawWithContent {
                val b = entry.drawn
                val v = if (b == entry.bounds) entry.visible else b
                if (entry.disposed || b.width <= 0f || v.isEmpty) return@drawWithContent
                // Clip only where an ancestor cuts the element (a scrolled list): the glass's own
                // shadow reaches past its bounds and must not be cut elsewhere.
                if (v.left > b.left + .5f || v.top > b.top + .5f || v.right < b.right - .5f || v.bottom < b.bottom - .5f) {
                    clipRect(v.left - b.left, v.top - b.top, v.right - b.left, v.bottom - b.top) { this@drawWithContent.drawContent() }
                } else drawContent()
            }
            .liquidGlass(entry.state ?: region.state, entry.shape, entry.style, entry.light, interaction = entry.interaction,
                pressSource = entry.press, link = entry.link)
            // A released layer is recycled for other content: drawing it could draw anything, even
            // the recording this overlay sits over, so a departed element's layer is never drawn.
            .drawWithContent { drawContent(); if (!entry.disposed && !entry.foreground.isReleased) drawLayer(entry.foreground) },
    )
}

/** Watches the pointer without consuming it, so the element keeps its own clicks and scrolls. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.observePress(press: GlassPressSource) {
    awaitPointerEventScope {
        var start = Offset.Unspecified
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                if (change.pressed) {
                    if (!start.isSpecified) start = change.position
                    press.press(change.position, change.position - start)
                } else {
                    start = Offset.Unspecified
                    press.release()
                }
            }
        } finally {
            press.release()
        }
    }
}

private val Offset.isSpecified: Boolean get() = this != Offset.Unspecified

internal object GlassWarnings {
    private var warned = false
    fun missingRoot() {
        if (warned) return
        warned = true
        println("LiquidGlass: glassEffect has no LiquidGlassTheme above it, so it draws a frosted stand-in. " +
            "Wrap the app in LiquidGlassTheme { ... } for real glass.")
    }
}

package com.wexpa.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.isSpecified
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * Groups glass so that nearby pieces merge, as SwiftUI's `GlassEffectContainer` does.
 *
 * Every [glassEffect] inside joins one body of glass: pieces within [spacing] of each other grow a
 * liquid neck and fuse, and part again as they move apart. Give pieces a [glassEffectID] in a shared
 * [GlassNamespace] and one that appears where another with the same id left morphs out of it.
 *
 * ```kotlin
 * val ns = rememberGlassNamespace()
 * GlassEffectContainer(spacing = 20.dp) {
 *     Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
 *         Icon(..., Modifier.size(44.dp).glassEffect().glassEffectID("edit", ns))
 *         if (expanded) Icon(..., Modifier.size(44.dp).glassEffect().glassEffectID("share", ns))
 *     }
 * }
 * ```
 *
 * The container draws all its pieces with one [glass]: the pieces' own variants are not mixed.
 * It is optional: glass outside a container works on its own. It needs a [LiquidGlassTheme] above it.
 *
 * @param spacing how close pieces must come to merge; [LiquidGlassTheme]'s default when unspecified.
 * @param glass what the merged body draws; [LiquidGlassTheme]'s default when null.
 */
@Composable
fun GlassEffectContainer(
    modifier: Modifier = Modifier,
    spacing: Dp = Dp.Unspecified,
    glass: Glass? = null,
    content: @Composable () -> Unit,
) {
    val defaults = LocalGlassDefaults.current
    val resolved = glass ?: defaults.glass
    val merge = if (spacing.isSpecified) spacing else defaults.containerSpacing
    GlassPortal(modifier) { state ->
        val style = resolved.style(defaults.dark)
        if (style == null) {
            Box { content() }
        } else {
            LiquidGlassContainer(state, style = style, light = resolved.light(defaults.dark), mergeDistance = merge) {
                val scope = this
                CompositionLocalProvider(LocalGlassMembership provides GlassMembership { shape, morph -> with(scope) { Modifier.glassMember(shape, morph) } }) {
                    content()
                }
            }
        }
    }
}

/**
 * Names this piece of glass for morphing, as SwiftUI's `glassEffectID(_:in:)` does: when a piece with
 * this [id] appears in [namespace] where another with the same id was, it grows out of the old one's
 * outline instead of popping in. Use it inside a [GlassEffectContainer], after [glassEffect].
 */
fun Modifier.glassEffectID(id: Any, namespace: GlassNamespace): Modifier = composed {
    val tracked = remember(id, namespace) { MorphTracker(id, namespace) }
    DisposableEffect(tracked) { onDispose { tracked.leave() } }
    LaunchedEffect(tracked) { tracked.run() }
    then(GlassIdElement(tracked)).onGloballyPositioned { tracked.arrive(it) }
}

/** Hands the morph to the [glassEffect] to its left, through the modifier local that one provides. */
private data class GlassIdElement(val tracker: MorphTracker) : ModifierNodeElement<GlassIdNode>() {
    override fun create() = GlassIdNode(tracker)
    override fun update(node: GlassIdNode) { node.tracker = tracker; node.link() }
}

private class GlassIdNode(var tracker: MorphTracker) : Modifier.Node(), ModifierLocalModifierNode {
    override fun onAttach() = link()
    fun link() { if (isAttached) ModifierLocalGlassMorph.current?.tracker = tracker }
}

/** Where pieces of glass with a [glassEffectID] were last seen, so that the next one morphs out of it. */
@Stable
class GlassNamespace internal constructor() {
    internal val lastSeen = mutableStateMapOf<Any, Rect>()
}

/** A namespace for [glassEffectID], remembered for this composition. */
@Composable
fun rememberGlassNamespace(): GlassNamespace = remember { GlassNamespace() }

// ---- Internals ------------------------------------------------------------------------------------

/** Inside a container, [glassEffect] registers a member of the merged body instead of drawing its own glass. */
internal fun interface GlassMembership { fun member(shape: Shape, morph: GlassMorphHandle): Modifier }
internal val LocalGlassMembership = staticCompositionLocalOf<GlassMembership?> { null }

/** How [glassEffectID] (to the right in the chain) reaches the [glassEffect] it names. */
@Stable
internal class GlassMorphHandle { var tracker: MorphTracker? by mutableStateOf(null) }
internal val ModifierLocalGlassMorph = androidx.compose.ui.modifier.modifierLocalOf<GlassMorphHandle?> { null }

@Stable
internal class MorphTracker(private val id: Any, private val namespace: GlassNamespace) {
    /** The bounds to draw, in root coordinates: animated from where this id was last seen. */
    var shown: Rect? by mutableStateOf(null)
    private var target: Rect? by mutableStateOf(null)

    fun arrive(coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return
        target = coordinates.boundsInRoot()
    }

    fun leave() { (target ?: shown)?.let { namespace.lastSeen[id] = it } }

    suspend fun run() {
        val start = namespace.lastSeen.remove(id)
        // Wait for the first placement, without spinning.
        val end = androidx.compose.runtime.snapshotFlow { target }.first { it != null }!!
        if (start == null || start == end) { shown = end; return }
        val anim = Animatable(start, Rect.VectorConverter)
        anim.animateTo(end, spring(dampingRatio = .8f, stiffness = Spring.StiffnessMediumLow)) { shown = value }
        shown = null
    }
}

/**
 * Composes [content] in the [LiquidGlassTheme]'s overlay, above the recorded screen, at the place this
 * call lays out: for glass that draws itself from the recording (a tab bar, a container), so that it
 * is never part of what it samples. Layout, size and composition locals are this call site's.
 * Without a theme the content is composed in place over an empty backdrop, so it draws its fallback.
 */
@Composable
internal fun GlassPortal(modifier: Modifier, aboveLink: GlassAccessoryLink? = null, content: @Composable (LiquidGlassState) -> Unit) {
    val region = LocalGlassRegion.current
    if (region == null) {
        GlassWarnings.missingRoot()
        val orphan = rememberLiquidGlassState()
        Box(modifier) { content(orphan) }
        return
    }
    val entry = remember(region) { GlassPortalEntry() }
    entry.state = region.rememberStateFor(LocalGlassDefaults.current)
    entry.locals = currentCompositionLocalContext
    entry.content = content
    entry.aboveLink = aboveLink
    DisposableEffect(region, entry) {
        region.layers += entry
        onDispose { region.layers -= entry }
    }
    Layout(modifier.onGloballyPositioned { entry.place(region.root, it) }) { _, constraints ->
        entry.constraints = constraints
        val s = entry.size
        layout(constraints.constrainWidth(s.width), constraints.constrainHeight(s.height)) {}
    }
}

@Stable
internal class GlassPortalEntry {
    var constraints by mutableStateOf(Constraints())
    var position by mutableStateOf(IntOffset.Zero)
    var size by mutableStateOf(IntSize.Zero)
    var locals: CompositionLocalContext? by mutableStateOf(null)
    var state: LiquidGlassState? by mutableStateOf(null)
    var content: @Composable (LiquidGlassState) -> Unit by mutableStateOf({})
    /** Draw just above the glass that publishes this link, instead of in composition order (#107). */
    var aboveLink: GlassAccessoryLink? by mutableStateOf(null)
    /** Fill the theme's root instead of taking the call site's place (dialogs, sheets, menus). */
    var fillRoot = false

    fun place(root: LayoutCoordinates?, coordinates: LayoutCoordinates) {
        if (root == null || !root.isAttached || !coordinates.isAttached) return
        val p = root.localPositionOf(coordinates, androidx.compose.ui.geometry.Offset.Zero)
        position = IntOffset(p.x.roundToInt(), p.y.roundToInt())
    }
}

/** The overlay's half of [GlassPortal]: measures the content with the placeholder's constraints. */
@Composable
internal fun PortalContent(region: GlassRegion, entry: GlassPortalEntry) {
    val locals = entry.locals ?: return
    CompositionLocalProvider(locals) {
      // The overlay is not recorded, so glass composed here draws in place (see glassEffect).
      CompositionLocalProvider(LocalInGlassOverlay provides true) {
        // Laid out over the whole overlay and placed where the call site is, so that touches reach it.
        Layout(content = { entry.content(entry.state ?: region.state) }) { measurables, overlay ->
            if (entry.fillRoot) {
                val all = measurables.map { it.measure(Constraints.fixed(overlay.maxWidth, overlay.maxHeight)) }
                return@Layout layout(overlay.maxWidth, overlay.maxHeight) { all.forEach { it.place(0, 0) } }
            }
            val placeables = measurables.map { it.measure(entry.constraints) }
            val w = placeables.maxOfOrNull { it.width } ?: 0
            val h = placeables.maxOfOrNull { it.height } ?: 0
            if (entry.size != IntSize(w, h)) entry.size = IntSize(w, h)
            val p = entry.position
            layout(overlay.maxWidth, overlay.maxHeight) { placeables.forEach { it.place(p.x, p.y) } }
        }
      }
    }
}

/** Inside the theme's overlay: glass draws in place there, since nothing there is recorded. */
internal val LocalInGlassOverlay = staticCompositionLocalOf { false }

/**
 * Composes [content] above everything under the [LiquidGlassTheme], filling its root: the place for a
 * dialog, a sheet or a menu. Glass in it refracts the screen behind, in the same window, and stacks
 * as it is composed (a scrim, then a glass panel, then its text). Without a theme the content is
 * composed in place.
 */
@Composable
fun GlassOverlay(content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val region = LocalGlassRegion.current
    if (region == null) {
        Box(Modifier.fillMaxSize(), content = content)
        return
    }
    val entry = remember(region) { GlassPortalEntry().also { it.fillRoot = true } }
    entry.state = region.rememberStateFor(LocalGlassDefaults.current)
    entry.locals = currentCompositionLocalContext
    entry.content = { Box(Modifier.fillMaxSize(), content = content) }
    DisposableEffect(region, entry) {
        region.layers += entry
        onDispose { region.layers -= entry }
    }
}

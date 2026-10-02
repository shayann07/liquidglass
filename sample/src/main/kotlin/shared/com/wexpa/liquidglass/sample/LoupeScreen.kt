package com.wexpa.liquidglass.sample

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wexpa.liquidglass.GlassInteraction
import com.wexpa.liquidglass.GlassProfile
import com.wexpa.liquidglass.GlassStyle
import com.wexpa.liquidglass.GlassTabBar
import com.wexpa.liquidglass.GlassTabBarStyle
import com.wexpa.liquidglass.liquidGlass
import com.wexpa.liquidglass.liquidGlassSource
import com.wexpa.liquidglass.rememberGlassPressSource
import com.wexpa.liquidglass.rememberLiquidGlassState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

/**
 * Loupe: an astronomical star atlas read through a piece of physical liquid glass.
 *
 * Designed adaptively:
 * - On desktop / wide displays (>= 960 dp): transforms into "Atlas Studio", a three-pane astronomy workbench
 *   with a searchable catalog sidebar, a central high-DPI star canvas, floating glass tool capsule,
 *   a collapsible contextual inspector pane with full astronomical telemetry, and a live status bar.
 * - On mobile displays (< 960 dp): renders the compact touch layout with the top readout pill,
 *   touch-summoned held-lens magnifier, and bottom glass tab bar.
 */
@Composable
fun LoupeScreen(reduceMotion: Boolean = false, modifier: Modifier = Modifier, loupeStyle: GlassStyle = LoupeStyle) {
    val density = LocalDensity.current
    val glass = rememberLiquidGlassState(background = SkyGround)
    val atlas = remember { Atlas.generate(seed = 27) }
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(IntSize.Zero) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var mode by remember { mutableIntStateOf(0) }
    var loupeCentre by remember { mutableStateOf(Offset.Unspecified) }
    var loupeShown by remember { mutableStateOf(false) }
    val presence = remember { Animatable(0f) }
    val press = rememberGlassPressSource()

    var selectedObject by remember { mutableStateOf<Atlas.Named?>(null) }
    var showGrid by remember { mutableStateOf(true) }

    val loupeSizePx = with(density) { LoupeSize.toPx() }
    val skyPx = with(density) { Size(Atlas.WIDTH.dp.toPx(), Atlas.HEIGHT.dp.toPx()) }

    // Sky coordinates (0..1 across the atlas) to screen pixels, and back.
    val toScreen: (Offset) -> Offset = remember(pan, zoom, skyPx) {
        { p -> Offset(p.x * skyPx.width * zoom, p.y * skyPx.height * zoom) + pan }
    }
    val toSky: (Offset) -> Offset = remember(pan, zoom, skyPx) {
        { s -> Offset((s.x - pan.x) / (skyPx.width * zoom), (s.y - pan.y) / (skyPx.height * zoom)) }
    }
    fun clampPan(p: Offset): Offset {
        val minX = (screen.width - skyPx.width * zoom).coerceAtMost(0f)
        val minY = (screen.height - skyPx.height * zoom).coerceAtMost(0f)
        return Offset(p.x.coerceIn(minX, 0f), p.y.coerceIn(minY, 0f))
    }

    // Between the toolbar and the tab bar: over either, the loupe would take their touches.
    val topLimit = with(density) { (48 + 48 + 12).dp.toPx() } + loupeSizePx / 2f
    val bottomLimit = with(density) { (34 + 64 + 12).dp.toPx() } + loupeSizePx / 2f
    fun clampLoupe(c: Offset): Offset {
        if (screen == IntSize.Zero) return c
        val maxY = (screen.height - bottomLimit).coerceAtLeast(topLimit)
        return Offset(c.x.coerceIn(loupeSizePx / 2f, screen.width - loupeSizePx / 2f), c.y.coerceIn(topLimit, maxY))
    }

    fun summon(at: Offset) {
        loupeCentre = clampLoupe(at)
        loupeShown = true
        scope.launch {
            if (reduceMotion) presence.snapTo(1f) else presence.animateTo(1f, tween(380))
        }
    }

    fun dismiss() {
        scope.launch {
            if (reduceMotion) presence.snapTo(0f) else presence.animateTo(0f, tween(260))
            loupeCentre = Offset.Unspecified
            loupeShown = false
        }
    }

    // Named only when the loupe is actually over something: within half a loupe of it.
    val nearest = remember(atlas, mode, zoom, pan, skyPx, loupeSizePx) {
        derivedStateOf {
            if (!loupeCentre.isSpecified()) null
            else atlas.nearest(toSky(loupeCentre), mode, loupeSizePx / 2f / (skyPx.width * zoom))
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(SkyGround)
            .onSizeChanged {
                if (screen == IntSize.Zero) {
                    screen = it
                    val isDesk = it.width >= with(density) { 960.dp.toPx() }
                    val canvasW = if (isDesk) (it.width - with(density) { (280 + 300).dp.toPx() }).coerceAtLeast(300f) else it.width.toFloat()
                    val target = Offset(canvasW * 0.5f, it.height * 0.45f)
                    val c = atlas.figures.first().centre
                    pan = clampPan(target - Offset(c.x * skyPx.width, c.y * skyPx.height))
                    summon(target)
                } else {
                    screen = it
                }
            },
    ) {
        val isDesktop = maxWidth >= 960.dp

        if (isDesktop) {
            DesktopWorkbench(
                glass = glass,
                atlas = atlas,
                mode = mode,
                onModeChange = {
                    mode = it
                    selectedObject = null
                },
                zoom = zoom,
                onZoomChange = { zoom = it },
                pan = pan,
                onPanChange = { pan = it },
                clampPan = ::clampPan,
                toScreen = toScreen,
                toSky = toSky,
                skyPx = skyPx,
                loupeCentre = { loupeCentre },
                onLoupeDrag = { delta -> loupeCentre = clampLoupe(loupeCentre + delta) },
                onSetLoupeCentre = { loupeCentre = it },
                loupeShown = loupeShown,
                presence = presence.value,
                press = press,
                onSummon = ::summon,
                onDismiss = ::dismiss,
                nearest = { nearest.value },
                selected = selectedObject,
                onSelect = { named ->
                    selectedObject = named
                },
                showGrid = showGrid,
                onToggleGrid = { showGrid = !showGrid },
                reduceMotion = reduceMotion,
                density = density.density,
                measurer = measurer,
                loupeStyle = loupeStyle,
                loupeSizePx = loupeSizePx,
                canvasSize = screen,
            )
        } else {
            MobileWorkbench(
                glass = glass,
                atlas = atlas,
                mode = mode,
                onModeChange = { mode = it },
                zoom = zoom,
                onZoomIn = {
                    val c = Offset(screen.width / 2f, screen.height / 2f)
                    val before = toSky(c)
                    zoom = (zoom * 1.4f).coerceAtMost(3f)
                    pan = clampPan(c - Offset(before.x * skyPx.width * zoom, before.y * skyPx.height * zoom))
                },
                onZoomOut = {
                    val c = Offset(screen.width / 2f, screen.height / 2f)
                    val before = toSky(c)
                    zoom = (zoom / 1.4f).coerceAtLeast(1f)
                    pan = clampPan(c - Offset(before.x * skyPx.width * zoom, before.y * skyPx.height * zoom))
                },
                pan = pan,
                onPanChange = { pan = it },
                clampPan = ::clampPan,
                toScreen = toScreen,
                loupeCentre = { loupeCentre },
                onLoupeDrag = { delta -> loupeCentre = clampLoupe(loupeCentre + delta) },
                loupeShown = loupeShown,
                presence = presence.value,
                press = press,
                onSummon = ::summon,
                onDismiss = ::dismiss,
                nearest = { nearest.value },
                reduceMotion = reduceMotion,
                density = density.density,
                measurer = measurer,
                loupeStyle = loupeStyle,
                loupeSizePx = loupeSizePx,
                screen = screen,
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Desktop Architecture: Three-Pane Astronomy Workbench ("Atlas Studio")
// -------------------------------------------------------------------------------------------------

@Composable
private fun DesktopWorkbench(
    glass: com.wexpa.liquidglass.LiquidGlassState,
    atlas: Atlas,
    mode: Int,
    onModeChange: (Int) -> Unit,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    pan: Offset,
    onPanChange: (Offset) -> Unit,
    clampPan: (Offset) -> Offset,
    toScreen: (Offset) -> Offset,
    toSky: (Offset) -> Offset,
    skyPx: Size,
    loupeCentre: () -> Offset,
    onLoupeDrag: (Offset) -> Unit,
    onSetLoupeCentre: (Offset) -> Unit,
    loupeShown: Boolean,
    presence: Float,
    press: com.wexpa.liquidglass.GlassPressSource,
    onSummon: (Offset) -> Unit,
    onDismiss: () -> Unit,
    nearest: () -> Atlas.Named?,
    selected: Atlas.Named?,
    onSelect: (Atlas.Named) -> Unit,
    showGrid: Boolean,
    onToggleGrid: () -> Unit,
    reduceMotion: Boolean,
    density: Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
    loupeStyle: GlassStyle,
    loupeSizePx: Float,
    canvasSize: IntSize,
) {
    var searchFilter by remember { mutableStateOf("") }
    var isInspectorOpen by remember { mutableStateOf(true) }
    var centralCanvasSize by remember { mutableStateOf(IntSize.Zero) }

    val canvasW = if (centralCanvasSize.width > 0) centralCanvasSize.width.toFloat() else (canvasSize.width - 580f).coerceAtLeast(400f)
    val canvasH = if (centralCanvasSize.height > 0) centralCanvasSize.height.toFloat() else canvasSize.height.toFloat()

    fun clampDesktopPan(p: Offset): Offset {
        val minX = (canvasW - skyPx.width * zoom).coerceAtMost(0f)
        val minY = (canvasH - skyPx.height * zoom).coerceAtMost(0f)
        return Offset(p.x.coerceIn(minX, 0f), p.y.coerceIn(minY, 0f))
    }

    fun clampDesktopLoupe(c: Offset): Offset {
        val minX = loupeSizePx / 2f
        val maxX = (canvasW - loupeSizePx / 2f).coerceAtLeast(minX)
        val minY = loupeSizePx / 2f
        val maxY = (canvasH - loupeSizePx / 2f).coerceAtLeast(minY)
        return Offset(c.x.coerceIn(minX, maxX), c.y.coerceIn(minY, maxY))
    }

    fun focusItem(item: Atlas.Named) {
        onSelect(item)
        val target = Offset(canvasW * 0.5f, canvasH * 0.45f)
        val before = item.position
        onPanChange(clampDesktopPan(target - Offset(before.x * skyPx.width * zoom, before.y * skyPx.height * zoom)))
        val clampedTarget = clampDesktopLoupe(target)
        onSetLoupeCentre(clampedTarget)
        onSummon(clampedTarget)
    }

    Column(Modifier.fillMaxSize()) {
        // 1. TOP UNIFIED GLASS HEADER (52 dp)
        DesktopHeader(
            mode = mode,
            onModeChange = onModeChange,
            zoom = zoom,
            showGrid = showGrid,
            onToggleGrid = onToggleGrid,
            loupeShown = loupeShown,
            onToggleLoupe = {
                if (loupeShown) onDismiss()
                else {
                    val target = clampDesktopLoupe(Offset(canvasW * 0.5f, canvasH * 0.45f))
                    onSetLoupeCentre(target)
                    onSummon(target)
                }
            },
            onResetView = {
                onZoomChange(1f)
                onPanChange(Offset.Zero)
            },
            reduceMotion = reduceMotion,
        )

        // 2. MAIN WORKSPACE (Left Catalog Sidebar + Center Canvas + Right Inspector)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // Left Catalog Sidebar (280 dp)
            AtlasCatalogSidebar(
                glass = glass,
                atlas = atlas,
                mode = mode,
                searchFilter = searchFilter,
                onSearchFilterChange = { searchFilter = it },
                selected = selected ?: nearest(),
                onSelect = { focusItem(it) },
                reduceMotion = reduceMotion,
                modifier = Modifier.width(280.dp).fillMaxHeight(),
            )

            // Central Astronomy Canvas
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged { centralCanvasSize = it }
            ) {
                // Sky Canvas with optional celestial grid
                Sky(
                    glass = glass,
                    atlas = atlas,
                    mode = mode,
                    zoom = zoom,
                    toScreen = toScreen,
                    density = density,
                    measurer = measurer,
                    onPan = { delta -> onPanChange(clampDesktopPan(pan + delta)) },
                    onSummon = {
                        val clamped = clampDesktopLoupe(it)
                        onSetLoupeCentre(clamped)
                        onSummon(clamped)
                    },
                    showGrid = showGrid,
                )

                // The Loupe: optical held-lens magnifier
                Loupe(
                    glass = glass,
                    style = loupeStyle,
                    reduceMotion = reduceMotion,
                    shown = loupeShown,
                    presence = presence,
                    press = press,
                    getCentre = loupeCentre,
                    onDrag = { delta -> onSetLoupeCentre(clampDesktopLoupe(loupeCentre() + delta)) },
                    toScreen = toScreen,
                    zoom = zoom,
                    atlas = atlas,
                    mode = mode,
                    measurer = measurer,
                    density = density,
                    loupeSizePx = loupeSizePx,
                )

                // Floating Glass Canvas Tool HUD (Bottom Center)
                DesktopCanvasHUD(
                    glass = glass,
                    zoom = zoom,
                    onZoomIn = {
                        val c = Offset(canvasW * 0.5f, canvasH * 0.5f)
                        val before = toSky(c)
                        val newZoom = (zoom * 1.3f).coerceAtMost(3.5f)
                        onZoomChange(newZoom)
                        onPanChange(clampDesktopPan(c - Offset(before.x * skyPx.width * newZoom, before.y * skyPx.height * newZoom)))
                    },
                    onZoomOut = {
                        val c = Offset(canvasW * 0.5f, canvasH * 0.5f)
                        val before = toSky(c)
                        val newZoom = (zoom / 1.3f).coerceAtLeast(0.8f)
                        onZoomChange(newZoom)
                        onPanChange(clampDesktopPan(c - Offset(before.x * skyPx.width * newZoom, before.y * skyPx.height * newZoom)))
                    },
                    loupeShown = loupeShown,
                    onToggleLoupe = {
                        if (loupeShown) onDismiss()
                        else {
                            val target = clampDesktopLoupe(Offset(canvasW * 0.5f, canvasH * 0.45f))
                            onSetLoupeCentre(target)
                            onSummon(target)
                        }
                    },
                    isInspectorOpen = isInspectorOpen,
                    onToggleInspector = { isInspectorOpen = !isInspectorOpen },
                    reduceMotion = reduceMotion,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                )
            }

            // Right Contextual Inspector Pane (300 dp, collapsible)
            if (isInspectorOpen) {
                AtlasInspector(
                    glass = glass,
                    item = selected ?: nearest(),
                    loupeShown = loupeShown,
                    onFocusLoupe = { focusItem(it) },
                    onCollapse = { isInspectorOpen = false },
                    reduceMotion = reduceMotion,
                    modifier = Modifier.width(300.dp).fillMaxHeight(),
                )
            }
        }

        // 3. BOTTOM TELEMETRY STATUS BAR (26 dp)
        DesktopStatusBar(
            zoom = zoom,
            item = selected ?: nearest(),
            totalStars = atlas.stars.size,
            modifier = Modifier.fillMaxWidth().height(26.dp),
        )
    }
}

@Composable
private fun DesktopHeader(
    mode: Int,
    onModeChange: (Int) -> Unit,
    zoom: Float,
    showGrid: Boolean,
    onToggleGrid: () -> Unit,
    loupeShown: Boolean,
    onToggleLoupe: () -> Unit,
    onResetView: () -> Unit,
    reduceMotion: Boolean,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Color(0xFF090B14))
            .border(width = 0.5.dp, color = Color(0x22446699))
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // App Title & Observatory Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x334488FF)),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("◎", style = TextStyle(color = Color(0xFF8EC5FF), fontSize = 16.sp, fontWeight = FontWeight.Bold))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    BasicText(
                        "ATLAS STUDIO",
                        style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                    )
                    BasicText(
                        "OBSERVATORY // J2000",
                        style = TextStyle(color = Color(0xFF88A0C0), fontSize = 10.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }

            Spacer(Modifier.width(28.dp))

            // Center: Segmented Desktop Mode Switcher
            Row(
                Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101424))
                    .border(0.5.dp, Color(0x33446699), RoundedCornerShape(8.dp))
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Modes.forEachIndexed { index, title ->
                    val isSelected = mode == index
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0x334488FF) else Color.Transparent)
                            .clickable { onModeChange(index) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            title,
                            style = TextStyle(
                                color = if (isSelected) Color(0xFF8EC5FF) else Color.White.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Right Quick Controls
            DesktopHeaderButton(
                label = if (showGrid) "Grid ON" else "Grid OFF",
                active = showGrid,
                onClick = onToggleGrid,
            )
            Spacer(Modifier.width(8.dp))
            DesktopHeaderButton(
                label = if (loupeShown) "Loupe ON" else "Loupe OFF",
                active = loupeShown,
                onClick = onToggleLoupe,
            )
            Spacer(Modifier.width(8.dp))
            DesktopHeaderButton(
                label = "Reset View",
                active = false,
                onClick = onResetView,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(Color(0x1A223355))
                    .border(0.5.dp, Color(0x33446699), CircleShape)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("${(zoom * 100).roundToInt()}%", style = TextStyle(color = Color(0xFFCFE0FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun DesktopHeaderButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Color(0x334488FF) else Color(0x1A223355))
            .border(0.5.dp, if (active) Color(0x664488FF) else Color(0x22446699), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = if (active) Color(0xFF8EC5FF) else Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun AtlasCatalogSidebar(
    glass: com.wexpa.liquidglass.LiquidGlassState,
    atlas: Atlas,
    mode: Int,
    searchFilter: String,
    onSearchFilterChange: (String) -> Unit,
    selected: Atlas.Named?,
    onSelect: (Atlas.Named) -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val allItems = remember(atlas, mode) { atlas.itemsForMode(mode) }
    val filteredItems = remember(allItems, searchFilter) {
        if (searchFilter.isBlank()) allItems
        else allItems.filter {
            it.name.contains(searchFilter, ignoreCase = true) || it.detail.contains(searchFilter, ignoreCase = true)
        }
    }

    Column(
        modifier
            .background(Color(0xFF070912))
            .border(width = 0.5.dp, color = Color(0x22446699))
            .padding(12.dp),
    ) {
        // Search Field
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF101424))
                .border(1.dp, Color(0x33446699), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = searchFilter,
                onValueChange = onSearchFilterChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                cursorBrush = SolidColor(Color(0xFF8EC5FF)),
                decorationBox = { inner ->
                    if (searchFilter.isEmpty()) {
                        BasicText("Search catalog...", style = TextStyle(color = Color(0x66FFFFFF), fontSize = 12.sp))
                    }
                    inner()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(10.dp))

        // Section Title & Item Count
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val title = when (mode) {
                0 -> "CONSTELLATIONS"
                1 -> "SOLAR SYSTEM"
                else -> "DEEP SKY OBJECTS"
            }
            BasicText(
                title,
                style = TextStyle(color = Color(0xFF88A0C0), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                "${filteredItems.size} cataloged",
                style = TextStyle(color = Color(0x66FFFFFF), fontSize = 10.sp),
            )
        }

        Spacer(Modifier.height(4.dp))

        // Object List
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(filteredItems, key = { it.name }) { item ->
                val isSelected = selected?.name == item.name
                val dotColor = when (mode) {
                    0 -> Color(0xFF8EC5FF)
                    1 -> Color(0xFFFFB86B)
                    else -> Color(0xFFFF6EB4)
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0x2B4A77BB) else Color(0x0DFFFFFF))
                        .clickable { onSelect(item) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            item.name,
                            style = TextStyle(
                                color = if (isSelected) Color(0xFFCFE0FF) else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            maxLines = 1,
                        )
                        BasicText(
                            item.detail,
                            style = TextStyle(color = Color(0x99FFFFFF), fontSize = 10.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    BasicText(
                        item.magnitude,
                        style = TextStyle(color = Color(0x66AFC3FF), fontSize = 10.sp, fontWeight = FontWeight.Normal),
                    )
                }
            }
        }
    }
}

@Composable
private fun AtlasInspector(
    glass: com.wexpa.liquidglass.LiquidGlassState,
    item: Atlas.Named?,
    loupeShown: Boolean,
    onFocusLoupe: (Atlas.Named) -> Unit,
    onCollapse: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(Color(0xFF070912))
            .border(width = 0.5.dp, color = Color(0x22446699))
            .padding(16.dp),
    ) {
        // Inspector Header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "ASTRONOMICAL INSPECTOR",
                style = TextStyle(color = Color(0xFF88A0C0), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCollapse),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("✕", style = TextStyle(color = Color(0x88FFFFFF), fontSize = 12.sp))
            }
        }

        Spacer(Modifier.height(14.dp))

        if (item != null) {
            // Celestial Telemetry Card
            Column(
                Modifier
                    .fillMaxWidth()
                    .liquidGlass(glass, RoundedCornerShape(12.dp), GlassStyle.inApp(dark = true))
                    .padding(14.dp),
            ) {
                BasicText(
                    item.name.uppercase(),
                    style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                )
                BasicText(
                    item.detail,
                    style = TextStyle(color = Color(0xFF8EC5FF), fontSize = 11.sp, fontWeight = FontWeight.Medium),
                )

                Spacer(Modifier.height(14.dp))

                TelemetryRow("Classification", item.type)
                TelemetryRow("Coordinates", item.coords)
                TelemetryRow("Apparent Magnitude", item.magnitude)
                TelemetryRow("Distance", item.distance)

                Spacer(Modifier.height(16.dp))

                // Actions
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x334488FF))
                            .clickable { onFocusLoupe(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText("Focus Loupe", style = TextStyle(color = Color(0xFFCFE0FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "Select an object in the catalog\nor hover the loupe over a target\nto inspect astronomical data.",
                    style = TextStyle(color = Color(0x66FFFFFF), fontSize = 12.sp, lineHeight = 18.sp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Optical Loupe Telemetry Card
        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlass(glass, RoundedCornerShape(12.dp), GlassStyle.inApp(dark = true))
                .padding(14.dp),
        ) {
            BasicText(
                "LOUPE OPTICS (V3)",
                style = TextStyle(color = Color(0xFF88A0C0), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
            )
            Spacer(Modifier.height(10.dp))
            TelemetryRow("Optical Profile", "Held Lens Spherical")
            TelemetryRow("Magnification", "1.67x (Interior Zoom)")
            TelemetryRow("Lens Aperture", "150 dp (Circle)")
            TelemetryRow("Chromatic Rim", "Dispersion Refraction")
            TelemetryRow("State", if (loupeShown) "Active on Canvas" else "Stowed / Hidden")
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = TextStyle(color = Color(0x88FFFFFF), fontSize = 11.sp))
        Spacer(Modifier.weight(1f))
        BasicText(value, style = TextStyle(color = Color(0xFFEAF2FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun DesktopCanvasHUD(
    glass: com.wexpa.liquidglass.LiquidGlassState,
    zoom: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    loupeShown: Boolean,
    onToggleLoupe: () -> Unit,
    isInspectorOpen: Boolean,
    onToggleInspector: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .liquidGlass(glass, CircleShape, GlassStyle.inApp(dark = true))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .clickable(onClick = onZoomOut),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("−", style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold))
        }
        Box(
            Modifier.padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("${(zoom * 100).roundToInt()}%", style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
        }
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .clickable(onClick = onZoomIn),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("+", style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold))
        }

        Box(Modifier.width(1.dp).height(18.dp).background(Color(0x33FFFFFF)))

        Box(
            Modifier
                .height(36.dp)
                .clip(CircleShape)
                .background(if (loupeShown) Color(0x334488FF) else Color(0x15FFFFFF))
                .clickable(onClick = onToggleLoupe)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                if (loupeShown) "✕ Loupe" else "◎ Loupe",
                style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            )
        }

        Box(
            Modifier
                .height(36.dp)
                .clip(CircleShape)
                .background(if (isInspectorOpen) Color(0x334488FF) else Color(0x15FFFFFF))
                .clickable(onClick = onToggleInspector)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                "Inspector",
                style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun DesktopStatusBar(
    zoom: Float,
    item: Atlas.Named?,
    totalStars: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF04050A))
            .navigationBarsPadding(),
    ) {
        Row(
            modifier
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                "Focused: ${item?.name ?: "Sky Overview"}  ·  ${item?.coords ?: "RA 05h 35m / Dec -05°23'"}  ·  Mag: ${item?.magnitude ?: "1.2"}",
                style = TextStyle(color = Color(0x88FFFFFF), fontSize = 11.sp),
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                "Scale: ${(zoom * 100).roundToInt()}%  |  Catalog: $totalStars stars  |  Pipeline: SkSL Vulkan LiquidGlass",
                style = TextStyle(color = Color(0x66FFFFFF), fontSize = 10.sp),
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Mobile Architecture: Tuned Touch Star Atlas
// -------------------------------------------------------------------------------------------------

@Composable
private fun MobileWorkbench(
    glass: com.wexpa.liquidglass.LiquidGlassState,
    atlas: Atlas,
    mode: Int,
    onModeChange: (Int) -> Unit,
    zoom: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    pan: Offset,
    onPanChange: (Offset) -> Unit,
    clampPan: (Offset) -> Offset,
    toScreen: (Offset) -> Offset,
    loupeCentre: () -> Offset,
    onLoupeDrag: (Offset) -> Unit,
    loupeShown: Boolean,
    presence: Float,
    press: com.wexpa.liquidglass.GlassPressSource,
    onSummon: (Offset) -> Unit,
    onDismiss: () -> Unit,
    nearest: () -> Atlas.Named?,
    reduceMotion: Boolean,
    density: Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
    loupeStyle: GlassStyle,
    loupeSizePx: Float,
    screen: IntSize,
) {
    Box(Modifier.fillMaxSize()) {
        // 1. The sky: the backdrop the glass refracts.
        Sky(
            glass = glass,
            atlas = atlas,
            mode = mode,
            zoom = zoom,
            toScreen = toScreen,
            density = density,
            measurer = measurer,
            onPan = { delta -> onPanChange(clampPan(pan + delta)) },
            onSummon = onSummon,
            showGrid = false,
        )

        // 2. The loupe: magnifying glass whose content is the readable layer of the atlas.
        Loupe(
            glass = glass,
            style = loupeStyle,
            reduceMotion = reduceMotion,
            shown = loupeShown,
            presence = presence,
            press = press,
            getCentre = loupeCentre,
            onDrag = onLoupeDrag,
            toScreen = toScreen,
            zoom = zoom,
            atlas = atlas,
            mode = mode,
            measurer = measurer,
            density = density,
            loupeSizePx = loupeSizePx,
        )

        // 3. The mobile toolbar pill
        LoupeToolbar(
            glass = glass,
            nearest = nearest,
            loupeShown = loupeShown,
            mode = mode,
            reduceMotion = reduceMotion,
            onZoomOut = onZoomOut,
            onZoomIn = onZoomIn,
            onToggleLoupe = {
                if (loupeShown) onDismiss()
                else onSummon(Offset(screen.width / 2f, screen.height * 0.45f))
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // 4. Mobile bottom tab bar
        GlassTabBar(
            state = glass,
            itemCount = Modes.size,
            selectedIndex = mode,
            onSelected = onModeChange,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 28.dp, end = 28.dp, bottom = 34.dp)
                .widthIn(max = 420.dp),
            style = GlassTabBarStyle.V3(dark = true),
            motionEnabled = !reduceMotion,
        ) { index, selected ->
            BasicText(
                Modes[index],
                style = TextStyle(
                    color = if (selected) Color(0xFF8EC5FF) else Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Shared Components
// -------------------------------------------------------------------------------------------------

@Composable
private fun Sky(
    glass: com.wexpa.liquidglass.LiquidGlassState,
    atlas: Atlas,
    mode: Int,
    zoom: Float,
    toScreen: (Offset) -> Offset,
    density: Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
    onPan: (Offset) -> Unit,
    onSummon: (Offset) -> Unit,
    showGrid: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, delta ->
                    change.consume()
                    onPan(delta)
                }
            }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onSummon(it) }) }
            .liquidGlassSource(glass),
    ) {
        if (showGrid) {
            val gridColor = Color(0x184477AA)
            for (i in 1..9) {
                val y = i * 0.1f
                drawLine(gridColor, toScreen(Offset(0f, y)), toScreen(Offset(1f, y)), strokeWidth = 1f * density)
            }
            for (i in 1..9) {
                val x = i * 0.1f
                drawLine(gridColor, toScreen(Offset(x, 0f)), toScreen(Offset(x, 1f)), strokeWidth = 1f * density)
            }
        }
        drawSky(atlas, mode, toScreen, zoom, labelled = false, measurer = measurer, density = density)
    }
}

@Composable
private fun Loupe(
    glass: com.wexpa.liquidglass.LiquidGlassState,
    style: GlassStyle,
    reduceMotion: Boolean,
    shown: Boolean,
    presence: Float,
    press: com.wexpa.liquidglass.GlassPressSource,
    getCentre: () -> Offset,
    onDrag: (Offset) -> Unit,
    toScreen: (Offset) -> Offset,
    zoom: Float,
    atlas: Atlas,
    mode: Int,
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: Float,
    loupeSizePx: Float,
) {
    if (!shown || presence <= 0f) return

    val halfLoupe = loupeSizePx / 2f

    Box(
        Modifier
            .offset {
                val c = getCentre()
                if (!c.isSpecified()) IntOffset.Zero
                else IntOffset((c.x - halfLoupe).roundToInt(), (c.y - halfLoupe).roundToInt())
            }
            .size(LoupeSize)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { press.press(it) },
                    onDragEnd = { press.release() },
                    onDragCancel = { press.release() },
                ) { change, delta ->
                    change.consume()
                    onDrag(delta)
                    press.press(change.position)
                }
            }
            .liquidGlass(
                state = glass,
                shape = CircleShape,
                style = style,
                interaction = if (reduceMotion) GlassInteraction.ReducedMotion else GlassInteraction.Default,
                pressSource = press,
                materialize = presence,
                refractContent = true,
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = getCentre()
            if (c.isSpecified()) {
                val shift = Offset(halfLoupe - c.x, halfLoupe - c.y)
                drawSky(
                    atlas = atlas,
                    mode = mode,
                    map = { p -> toScreen(p) + shift },
                    zoom = zoom,
                    labelled = true,
                    measurer = measurer,
                    density = density,
                    onlyLabels = true,
                )
            }
        }
    }
}

@Composable
private fun LoupeToolbar(
    glass: com.wexpa.liquidglass.LiquidGlassState,
    nearest: () -> Atlas.Named?,
    loupeShown: Boolean,
    mode: Int,
    reduceMotion: Boolean,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onToggleLoupe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .padding(top = 48.dp, start = 16.dp, end = 16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassButton(glass, "−", reduceMotion, onClick = onZoomOut)
        Column(
            Modifier
                .weight(1f)
                .height(48.dp)
                .liquidGlass(glass, CircleShape, GlassStyle.inApp(dark = true)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val item = nearest()
            BasicText(
                item?.name ?: (if (loupeShown) "Drag the loupe" else "Long-press the sky"),
                style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            BasicText(
                item?.detail ?: (if (loupeShown) "over a ${listOf("constellation", "planet", "nebula")[mode]} to name it" else "to bring out the loupe"),
                style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp),
                maxLines = 1,
            )
        }
        GlassButton(glass, "+", reduceMotion, onClick = onZoomIn)
        GlassButton(glass, if (loupeShown) "×" else "◎", reduceMotion, onClick = onToggleLoupe)
    }
}

@Composable
private fun GlassButton(glass: com.wexpa.liquidglass.LiquidGlassState, label: String, reduceMotion: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .liquidGlass(
                state = glass,
                shape = CircleShape,
                style = GlassStyle.inApp(dark = true),
                interaction = if (reduceMotion) GlassInteraction.ReducedMotion else GlassInteraction.Pullable,
            )
            .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium))
    }
}

private fun Offset.isSpecified() = this != Offset.Unspecified && x.isFinite() && y.isFinite()

internal val SkyGround = Color(0xFF05060C)
internal val Modes = listOf("Stars", "Planets", "Deep sky")
internal val LoupeSize = 150.dp

/** Clear glass for the loupe: Regular with every blur removed. Authored for this demo, not measured. */
internal val LoupeStyleClear: GlassStyle = GlassStyle.Regular.copy(
    blurRadius = 0.dp,
    backdropBlur = 0.dp,
    wideKernel = 0.dp,
    tint = Color(0xFFD0E4FF).copy(alpha = 0.04f),
    tintLift = 0.025f,
    heldLens = 1f,
    heldMagnification = 0.4f,
    heldGlow = 0.85f,
    bevel = 3.5.dp,
    bevelPeak = 0.35f,
    specular = 0.70f,
    specularPower = 6f,
    counterLight = 0.45f,
    dispersion = 0.09f,
)

/**
 * The loupe: the clear glass above on the held-lens profile, whose interior magnifies the names it
 * carries (heldMagnification 0.4 is 1 / 0.6 = 1.67x) and splits them into colour at the rim.
 */
internal val LoupeStyle: GlassStyle = LoupeStyleClear.copy(profile = GlassProfile.Held)

// -------------------------------------------------------------------------------------------------
// The atlas: generated once from a seed, in sky coordinates (0..1 on both axes).
// -------------------------------------------------------------------------------------------------

internal class Star(val p: Offset, val r: Float, val color: Color)
internal class Figure(val name: String, val detail: String, val points: List<Offset>, val centre: Offset)
internal class Body(val name: String, val detail: String, val p: Offset, val r: Float, val color: Color, val orbit: Float)
internal class Nebula(val name: String, val detail: String, val p: Offset, val r: Float, val color: Color)

@androidx.compose.runtime.Immutable
internal class Atlas(
    val stars: List<Star>,
    val figures: List<Figure>,
    val bodies: List<Body>,
    val nebulae: List<Nebula>,
) {
    data class Named(
        val name: String,
        val detail: String,
        val type: String = "Constellation",
        val coords: String = "RA 05h 35m / Dec -05°23'",
        val magnitude: String = "1.2 mag",
        val distance: String = "Various",
        val position: Offset = Offset.Zero,
    )

    fun nearest(p: Offset, mode: Int, reach: Float): Named? {
        fun d(a: Offset) = (a - p).let { Offset(it.x, it.y * HEIGHT / WIDTH) }.getDistance()
        val hit = when (mode) {
            0 -> figures.minByOrNull { d(it.centre) }?.takeIf { d(it.centre) <= reach }?.let { f ->
                val meta = figureMeta[f.name] ?: CelestialMeta()
                Named(f.name, f.detail, "Constellation", meta.coords, meta.mag, meta.dist, f.centre)
            }
            1 -> bodies.minByOrNull { d(it.p) }?.takeIf { d(it.p) <= reach }?.let { b ->
                val meta = bodyMeta[b.name] ?: CelestialMeta()
                Named(b.name, b.detail, if (b.name in listOf("Jupiter", "Saturn", "Uranus", "Neptune")) "Gas / Ice Giant" else "Terrestrial Planet", meta.coords, meta.mag, meta.dist, b.p)
            }
            else -> nebulae.minByOrNull { d(it.p) }?.takeIf { d(it.p) <= reach }?.let { n ->
                val meta = nebulaMeta[n.name] ?: CelestialMeta()
                Named(n.name, n.detail, meta.type, meta.coords, meta.mag, meta.dist, n.p)
            }
        }
        return hit
    }

    fun itemsForMode(mode: Int): List<Named> {
        return when (mode) {
            0 -> figures.map { f ->
                val meta = figureMeta[f.name] ?: CelestialMeta()
                Named(f.name, f.detail, "Constellation", meta.coords, meta.mag, meta.dist, f.centre)
            }
            1 -> bodies.map { b ->
                val meta = bodyMeta[b.name] ?: CelestialMeta()
                Named(b.name, b.detail, if (b.name in listOf("Jupiter", "Saturn", "Uranus", "Neptune")) "Gas / Ice Giant" else "Terrestrial Planet", meta.coords, meta.mag, meta.dist, b.p)
            }
            else -> nebulae.map { n ->
                val meta = nebulaMeta[n.name] ?: CelestialMeta()
                Named(n.name, n.detail, meta.type, meta.coords, meta.mag, meta.dist, n.p)
            }
        }
    }

    data class CelestialMeta(
        val coords: String = "RA 00h 00m / Dec +00°00'",
        val mag: String = "—",
        val dist: String = "—",
        val type: String = "Deep Sky Object",
    )

    companion object {
        const val WIDTH = 1200
        const val HEIGHT = 2000

        val figureMeta = mapOf(
            "Orion" to CelestialMeta("RA 05h 35m / Dec -05°23'", "0.1 mag", "~1,300 ly", "Prominent Equatorial Constellation"),
            "Cassiopeia" to CelestialMeta("RA 01h 15m / Dec +60°43'", "2.2 mag", "~54 ly", "Circumpolar W-Shape"),
            "Ursa Major" to CelestialMeta("RA 11h 03m / Dec +61°45'", "1.8 mag", "~80 ly", "Great Bear & The Plough"),
            "Cygnus" to CelestialMeta("RA 20h 35m / Dec +45°16'", "1.3 mag", "~1,500 ly", "Northern Cross Asterism"),
            "Lyra" to CelestialMeta("RA 18h 36m / Dec +38°47'", "0.0 mag", "25 ly", "Lyre featuring Vega"),
            "Scorpius" to CelestialMeta("RA 16h 53m / Dec -30°44'", "1.0 mag", "~550 ly", "Scorpion featuring Antares"),
            "Taurus" to CelestialMeta("RA 04h 35m / Dec +16°30'", "0.9 mag", "~65 ly", "Bull featuring Aldebaran"),
            "Gemini" to CelestialMeta("RA 07h 34m / Dec +31°53'", "1.1 mag", "~34 ly", "Twins Castor & Pollux"),
            "Leo" to CelestialMeta("RA 10h 08m / Dec +11°58'", "1.4 mag", "~79 ly", "Lion featuring Regulus"),
            "Aquila" to CelestialMeta("RA 19h 50m / Dec +08°52'", "0.8 mag", "17 ly", "Eagle featuring Altair"),
            "Perseus" to CelestialMeta("RA 03h 24m / Dec +49°51'", "2.1 mag", "~93 ly", "Hero featuring Algol"),
            "Andromeda" to CelestialMeta("RA 00h 42m / Dec +41°16'", "2.1 mag", "~2.5M ly", "Princess harboring M31"),
        )

        val bodyMeta = mapOf(
            "Mercury" to CelestialMeta("RA 02h 10m / Dec +12°15'", "-0.4 mag", "0.39 AU", "Terrestrial Inner Planet"),
            "Venus" to CelestialMeta("RA 03h 45m / Dec +20°30'", "-4.4 mag", "0.72 AU", "Dense Atmosphere Sister Planet"),
            "Earth" to CelestialMeta("RA 00h 00m / Dec +00°00'", "—", "1.00 AU", "Home Planet & Sol Origin"),
            "Mars" to CelestialMeta("RA 05h 20m / Dec +24°10'", "-1.5 mag", "1.52 AU", "Red Planet / Iron Oxide"),
            "Jupiter" to CelestialMeta("RA 08h 15m / Dec +19°45'", "-2.7 mag", "5.20 AU", "Largest Gas Giant & Great Spot"),
            "Saturn" to CelestialMeta("RA 22h 30m / Dec -12°20'", "+0.6 mag", "9.58 AU", "Ringed Gas Giant Planet"),
            "Uranus" to CelestialMeta("RA 03h 15m / Dec +17°40'", "+5.7 mag", "19.2 AU", "Tilted Ice Giant"),
            "Neptune" to CelestialMeta("RA 23h 50m / Dec -03°10'", "+7.8 mag", "30.1 AU", "Outermost Methane Ice Giant"),
        )

        val nebulaMeta = mapOf(
            "Orion Nebula" to CelestialMeta("RA 05h 35m / Dec -05°23'", "4.0 mag", "1,344 ly", "Diffuse Emission & Reflection"),
            "Crab Nebula" to CelestialMeta("RA 05h 34m / Dec +22°00'", "8.4 mag", "6,500 ly", "Supernova Remnant (Pulsar)"),
            "Ring Nebula" to CelestialMeta("RA 18h 53m / Dec +33°01'", "8.8 mag", "2,570 ly", "Planetary White Dwarf Bubble"),
            "Andromeda Galaxy" to CelestialMeta("RA 00h 42m / Dec +41°16'", "3.4 mag", "2.5M ly", "Major Local Group Spiral"),
            "Lagoon Nebula" to CelestialMeta("RA 18h 03m / Dec -24°23'", "6.0 mag", "4,100 ly", "Giant Interstellar Cloud"),
            "Eagle Nebula" to CelestialMeta("RA 18h 18m / Dec -13°47'", "6.0 mag", "7,000 ly", "Pillars of Creation Cluster"),
            "Whirlpool Galaxy" to CelestialMeta("RA 13h 29m / Dec +47°11'", "8.4 mag", "31M ly", "Grand Design Interacting Spiral"),
        )

        private val figureNames = listOf(
            "Orion" to "The Hunter · 7 bright stars", "Cassiopeia" to "The Queen · W-shaped",
            "Ursa Major" to "Great Bear · The Plough", "Cygnus" to "The Swan · Northern Cross",
            "Lyra" to "The Lyre · Vega", "Scorpius" to "The Scorpion · Antares",
            "Taurus" to "The Bull · Aldebaran", "Gemini" to "The Twins · Castor & Pollux",
            "Leo" to "The Lion · Regulus", "Aquila" to "The Eagle · Altair",
            "Perseus" to "The Hero · Algol", "Andromeda" to "The Princess · M31 nearby",
        )
        private val bodyNames = listOf(
            "Mercury" to "0.39 AU · 88 days", "Venus" to "0.72 AU · 225 days", "Earth" to "1.00 AU · 365 days",
            "Mars" to "1.52 AU · 687 days", "Jupiter" to "5.20 AU · 11.9 years", "Saturn" to "9.58 AU · 29.5 years",
            "Uranus" to "19.2 AU · 84 years", "Neptune" to "30.1 AU · 165 years",
        )
        private val nebulaNames = listOf(
            "Orion Nebula" to "M42 · 1,344 ly", "Crab Nebula" to "M1 · 6,500 ly", "Ring Nebula" to "M57 · 2,570 ly",
            "Andromeda Galaxy" to "M31 · 2.5 million ly", "Lagoon Nebula" to "M8 · 4,100 ly",
            "Eagle Nebula" to "M16 · 7,000 ly", "Whirlpool Galaxy" to "M51 · 31 million ly",
        )

        fun generate(seed: Int): Atlas {
            val rnd = Random(seed)
            val tints = listOf(Color(0xFFFFFFFF), Color(0xFFCFE0FF), Color(0xFFFFF1D6), Color(0xFFFFD2A6), Color(0xFFB9D3FF))
            val stars = List(1500) {
                val t = rnd.nextFloat()
                val band = rnd.nextFloat() < 0.35f
                val x = if (band) t else rnd.nextFloat()
                val y = if (band) (0.15f + 0.7f * t + (rnd.nextFloat() - 0.5f) * 0.12f).coerceIn(0f, 1f) else rnd.nextFloat()
                val bright = rnd.nextFloat() < 0.03f
                Star(Offset(x, y), if (bright) 1.6f + rnd.nextFloat() * 1.2f else 0.4f + rnd.nextFloat() * 0.8f, tints[rnd.nextInt(tints.size)])
            }
            val figures = figureNames.mapIndexed { i, (name, detail) ->
                val c = Offset(0.15f + (i % 3) * 0.35f + (rnd.nextFloat() - 0.5f) * 0.08f, 0.08f + (i / 3) * 0.24f + rnd.nextFloat() * 0.06f)
                var p = c
                val pts = List(5 + rnd.nextInt(3)) {
                    p += Offset((rnd.nextFloat() - 0.5f) * 0.12f, (rnd.nextFloat() - 0.5f) * 0.07f)
                    p
                }
                Figure(name, detail, pts, Offset(pts.map { it.x }.average().toFloat(), pts.map { it.y }.average().toFloat()))
            }
            val sun = Offset(0.5f, 0.5f)
            val bodyColors = listOf(Color(0xFFB0A79A), Color(0xFFE8C77A), Color(0xFF4F8EF7), Color(0xFFD5603A),
                Color(0xFFD9A066), Color(0xFFE6CD8A), Color(0xFF8FD8E0), Color(0xFF4062D8))
            val bodies = bodyNames.mapIndexed { i, (name, detail) ->
                val orbit = 0.05f + i * 0.055f
                val a = rnd.nextFloat() * 2f * PI.toFloat()
                Body(name, detail, sun + Offset(orbit * cos(a), orbit * sin(a) * (WIDTH.toFloat() / HEIGHT)),
                    if (i in 4..5) 0.012f else 0.006f, bodyColors[i], orbit)
            }
            val nebulaColors = listOf(Color(0xFFFF5FA2), Color(0xFF6FD3FF), Color(0xFF9B7BFF), Color(0xFFFFB86B), Color(0xFF6BFFB8))
            val nebulae = nebulaNames.mapIndexed { i, (name, detail) ->
                Nebula(name, detail, Offset(0.12f + rnd.nextFloat() * 0.76f, 0.08f + i * 0.13f), 0.04f + rnd.nextFloat() * 0.05f,
                    nebulaColors[i % nebulaColors.size])
            }
            return Atlas(stars, figures, bodies, nebulae)
        }
    }
}

private val LabelBig = TextStyle(color = Color(0xFFEAF2FF), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
private val LabelSmall = TextStyle(color = Color(0xCCEAF2FF), fontSize = 9.sp)

/**
 * Draws one mode of the atlas through [map] (sky -> pixels). With [labelled] the names are drawn
 * readable; the sky itself carries them at a size meant to be unreadable without the loupe.
 */
internal fun DrawScope.drawSky(
    atlas: Atlas,
    mode: Int,
    map: (Offset) -> Offset,
    zoom: Float,
    labelled: Boolean,
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: Float,
    onlyLabels: Boolean = false,
) {
    val s = density * zoom
    if (!onlyLabels) {
        val a = map(Offset(0f, 0.15f)); val b = map(Offset(1f, 0.85f))
        drawLine(Brush.linearGradient(listOf(Color(0x00334477), Color(0x40556AAA), Color(0x00334477)), a, b),
            a, b, strokeWidth = 220f * s)
        atlas.stars.forEach { drawCircle(it.color, it.r * s, map(it.p)) }
    }
    fun label(name: String, detail: String, at: Offset) {
        if (labelled) {
            if (onlyLabels) {
                val half = size.width / 2f
                val dx = at.x - half
                val dy = at.y - half
                if (dx * dx + dy * dy > (half + 80f) * (half + 80f)) return
            }
            val n = measurer.measure(name, LabelBig)
            val d = measurer.measure(detail, LabelSmall)
            drawText(n, topLeft = at - Offset(n.size.width / 2f, n.size.height.toFloat()))
            drawText(d, topLeft = at - Offset(d.size.width / 2f, -2f * density))
        } else {
            drawText(measurer, name, at + Offset(4f * s, -4f * s), TextStyle(color = Color(0x66AFC3FF), fontSize = (5 * zoom).sp))
        }
    }
    when (mode) {
        0 -> atlas.figures.forEach { f ->
            if (!onlyLabels) {
                f.points.zipWithNext { p, q -> drawLine(Color(0x66B9CCFF), map(p), map(q), 0.8f * s) }
                f.points.forEach { drawCircle(Color.White, 1.8f * s, map(it)) }
            }
            label(f.name, f.detail, map(f.centre))
        }
        1 -> {
            val sun = map(Offset(0.5f, 0.5f))
            if (!onlyLabels) {
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3C4), Color(0xFFFFB347), Color(0x00FF8C00)), sun, 40f * s), 40f * s, sun)
                atlas.bodies.forEach { b ->
                    val rx = b.orbit * Atlas.WIDTH * s
                    drawOval(Color(0x3398B4FF), sun - Offset(rx, rx), Size(rx * 2f, rx * 2f), style = Stroke(0.8f * s))
                    drawCircle(b.color, b.r * Atlas.WIDTH * s, map(b.p))
                }
            }
            atlas.bodies.forEach { label(it.name, it.detail, map(it.p)) }
        }
        else -> {
            if (!onlyLabels) atlas.nebulae.forEach { n ->
                val c = map(n.p); val r = n.r * Atlas.WIDTH * s
                drawCircle(Brush.radialGradient(listOf(n.color.copy(alpha = 0.55f), n.color.copy(alpha = 0.12f), Color.Transparent), c, r), r, c)
            }
            atlas.nebulae.forEach { label(it.name, it.detail, map(it.p)) }
        }
    }
}

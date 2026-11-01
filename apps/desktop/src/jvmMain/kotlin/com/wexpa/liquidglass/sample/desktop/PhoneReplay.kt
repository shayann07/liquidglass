package com.wexpa.liquidglass.sample.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import com.wexpa.liquidglass.GlassTabBarStyle
import com.wexpa.liquidglass.sample.AppStoreGeometry
import com.wexpa.liquidglass.sample.AppStoreScene
import com.wexpa.liquidglass.sample.WhatsAppGeometry
import com.wexpa.liquidglass.sample.WhatsAppScene
import com.wexpa.liquidglass.sample.LoupeScreen
import com.wexpa.liquidglass.sample.PhoneGeometry
import com.wexpa.liquidglass.sample.PhonePalette
import com.wexpa.liquidglass.sample.PhoneScene
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.*
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import java.awt.Dimension
import java.io.File
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Deterministic reference replay on the GPU.
 *
 * Renders [PhoneScene] at exactly 60 frames per scripted second through Atlas's own Skiko layer
 * (Direct3D on Windows) and reads each frame back from that layer. Time is a virtual clock: frame
 * N is composed at N/60 s however long it takes to draw, and input arrives at its scripted
 * timestamp. The output is therefore reproducible and says nothing about interactive frame rate,
 * input latency or display presentation.
 *
 * Usage: `PhoneReplay <script> <outDir> [calm|v3|calm-natural]`. Script lines: `<ms> down|move|up <x> <y>`
 * in scene pixels at 3px/pt, optional `select <index>`, and `end <ms>`.
 */
fun main(args: Array<String>) {
    try { replay(args) } catch (e: Throwable) {
        File(args.getOrElse(1) { "." }, "replay-error.txt").apply { parentFile.mkdirs() }.writeText(e.stackTraceToString())
        e.printStackTrace()
        kotlin.system.exitProcess(1)
    }
}

@OptIn(InternalComposeUiApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun replay(args: Array<String>) {
    val script = File(args[0])
    val out = File(args[1]).apply { mkdirs() }
    val variant = args.getOrElse(2) { "calm" }
    // The style and palette name, without the "+" options ("calm-recorded+badge" is calm-recorded with a badge).
    val baseVariant = variant.substringBefore('+')
    // "+squircle=<amount>[,<exponent>]": the held lens's superellipse outline (#93).
    variant.substringAfter("+squircle=", "").substringBefore('+').split(',').mapNotNull { it.toFloatOrNull() }
        .takeIf { it.isNotEmpty() }?.let { @OptIn(com.wexpa.liquidglass.LiquidGlassDiagnosticApi::class) run { com.wexpa.liquidglass.LiquidGlassDiagnostics.lensSquircle = it.toFloatArray() } }
    // "+tapl=<rate>": the coupled tap's clock ratio for two-slot trips (#70).
    variant.substringAfter("+tapl=", "").substringBefore('+').toFloatOrNull()
        ?.let { @OptIn(com.wexpa.liquidglass.LiquidGlassDiagnosticApi::class) run { com.wexpa.liquidglass.LiquidGlassDiagnostics.tapLongTripRate = it } }
    // "+fringe=<still>,<moving>": the Calm selector's rim split, for fitting by replay (#73).
    variant.substringAfter("+fringe=", "").substringBefore('+').split(',').mapNotNull { it.toFloatOrNull() }
        .takeIf { it.size >= 2 }?.let { @OptIn(com.wexpa.liquidglass.LiquidGlassDiagnosticApi::class) run { com.wexpa.liquidglass.LiquidGlassDiagnostics.selectorFringe = it.toFloatArray() } }
    val events = script.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { it.split(Regex("\\s+")) }
    val end = events.first { it[0] == "end" }[1].toDouble()
    val initial = events.firstOrNull { it[0] == "select" }?.get(1)?.toInt() ?: 0
    val pointer = events.filter { it[0] !in setOf("end", "select") }
        .map { Triple(it[0].toDouble(), it[1], Offset(it[2].toFloat(), it[3].toFloat())) }
    // "atlas" renders Atlas Studio at 1200 x 750 dp (density 1.5) instead of the phone scene.
    val atlas = variant.startsWith("atlas")
    val sceneWidth = if (atlas) 1800 else 1170
    val sceneHeight = if (atlas) 1125 else 702
    val style = when (baseVariant) {
        "v3" -> GlassTabBarStyle.V3().copy(height = PhoneGeometry.barHeight)
        // The 2026-10-13 scene geometry (slot-wide pill, 6 dp ends), for before/after renders.
        "calm-slot" -> GlassTabBarStyle.Calm().copy(height = PhoneGeometry.barHeight)
        // The material calibrated on the wrongly decoded recordings, with their dimmed palette.
        "calm-recorded" -> PhoneGeometry.style(GlassTabBarStyle.CalmRecordedSpace())
        // Light appearance (authored): "calm-light", "whatsapp-light", "appstore-light".
        "calm-light" -> PhoneGeometry.style(GlassTabBarStyle.Calm(dark = false))
        // The clearer bar of WhatsApp and the App Store ("whatsapp-clear", "appstore-clear").
        else -> if ("-clear" in baseVariant) PhoneGeometry.style(GlassTabBarStyle.CalmClear(dark = "light" !in baseVariant))
            else PhoneGeometry.style(GlassTabBarStyle.Calm(dark = "light" !in baseVariant))
    }.let { base ->
        // "calm+lens=<alpha>,<grey 0-255>[,<rawShare>[,<lift /255>[,<fineShare>[,<wide dp>[,<heldGlow>[,<glowFloor>[,<glowReach dp>[,<blur dp>[,<rawBlur dp>[,<refraction depth dp>[,<innerShadow>[,<edge tone /255>]]]]]]]]]]]]": a lens candidate, for fitting by replay.
        val lens = variant.substringAfter("+lens=", "").substringBefore('+').split(',').mapNotNull { it.toFloatOrNull() }
        if (lens.size < 2) base else base.copy(lens = base.lens.copy(
            tint = androidx.compose.ui.graphics.Color(lens[1] / 255f, lens[1] / 255f, lens[1] / 255f, lens[0]),
            rawShare = lens.getOrElse(2) { base.lens.rawShare },
            tintLift = lens.getOrNull(3)?.let { it / 255f } ?: base.lens.tintLift,
            fineShare = lens.getOrElse(4) { base.lens.fineShare },
            wideKernel = lens.getOrNull(5)?.let { androidx.compose.ui.unit.Dp(it) } ?: base.lens.wideKernel,
            heldGlow = lens.getOrElse(6) { base.lens.heldGlow },
            heldGlowFloor = lens.getOrElse(7) { base.lens.heldGlowFloor },
            heldGlowReach = lens.getOrNull(8)?.let { androidx.compose.ui.unit.Dp(it) } ?: base.lens.heldGlowReach,
            blurRadius = lens.getOrNull(9)?.let { androidx.compose.ui.unit.Dp(it) } ?: base.lens.blurRadius,
            rawBlur = lens.getOrNull(10)?.let { androidx.compose.ui.unit.Dp(it) } ?: base.lens.rawBlur,
            refractionDepth = lens.getOrNull(11)?.let { androidx.compose.ui.unit.Dp(it) } ?: base.lens.refractionDepth,
            innerShadow = lens.getOrElse(12) { base.lens.innerShadow },
            heldEdgeTone = lens.getOrNull(13)?.let { it / 255f } ?: base.lens.heldEdgeTone,
        ))
    }.let { base ->
        // "+bar=<black alpha>,<lift /255>[,<fineShare>[,<blur dp>[,<softDetail>[,<wide dp>[,<legibility>]]]]]": a bar candidate, for fitting by replay.
        val bar = variant.substringAfter("+bar=", "").substringBefore('+').split(',').mapNotNull { it.toFloatOrNull() }
        if (bar.size < 2) base else base.copy(bar = base.bar.copy(
            tint = androidx.compose.ui.graphics.Color(0f, 0f, 0f, bar[0]), tintLift = bar[1] / 255f,
            fineShare = bar.getOrElse(2) { base.bar.fineShare },
            blurRadius = bar.getOrNull(3)?.let { androidx.compose.ui.unit.Dp(it) } ?: base.bar.blurRadius,
            softDetail = bar.getOrElse(4) { base.bar.softDetail },
            wideKernel = bar.getOrNull(5)?.let { androidx.compose.ui.unit.Dp(it) } ?: base.bar.wideKernel,
            legibility = bar.getOrElse(6) { base.bar.legibility }))
    }.let { base ->
        // "+barfold=<edgeFold>,<foldAtCorners>[,<refraction depth dp>[,<bevel dp>]]": bar edge refraction candidates.
        val bf = variant.substringAfter("+barfold=", "").substringBefore('+').split(',').mapNotNull { it.toFloatOrNull() }
        if (bf.size < 2) base else base.copy(bar = base.bar.copy(edgeFold = bf[0], foldAtCorners = bf[1],
            refractionDepth = bf.getOrNull(2)?.let { androidx.compose.ui.unit.Dp(it) } ?: base.bar.refractionDepth,
            bevel = bf.getOrNull(3)?.let { androidx.compose.ui.unit.Dp(it) } ?: base.bar.bevel))
    }.let { base ->
        // "+llift=<tintLift /255>[,<heldGlow>[,<heldGlowReach dp>]]": the held lens's lift and glow.
        val ll = variant.substringAfter("+llift=", "").substringBefore('+').split(',').mapNotNull { it.toFloatOrNull() }
        if (ll.isEmpty()) base else base.copy(lens = base.lens.copy(tintLift = ll[0] / 255f,
            heldGlow = ll.getOrElse(1) { base.lens.heldGlow },
            heldGlowReach = ll.getOrNull(2)?.let { androidx.compose.ui.unit.Dp(it) } ?: base.lens.heldGlowReach))
    }.let { base ->
        // "+lraw=<rawShare>,<rawBlur dp>": the held lens's raw page term.
        val lw = variant.substringAfter("+lraw=", "").substringBefore('+').split(',').mapNotNull { it.toFloatOrNull() }
        if (lw.size < 2) base else base.copy(lens = base.lens.copy(rawShare = lw[0], rawBlur = androidx.compose.ui.unit.Dp(lw[1])))
    }.let { base ->
        // "+lband=<body band scale>": the held lens's rim band.
        val lbs = variant.substringAfter("+lband=", "").substringBefore('+').split(',').mapNotNull { it.toFloatOrNull() }
        if (lbs.isEmpty()) base else base.copy(lens = base.lens.copy(bodyBandScale = lbs[0],
            heldMagnification = lbs.getOrElse(1) { base.lens.heldMagnification }))
    }.let { base ->
        // "+lring=<innerShadow>,<heldGlow>,<heldEdgeTone /255>[,<heldGlowFloor>[,<specular>]]": the held lens's rim terms.
        val lr = variant.substringAfter("+lring=", "").substringBefore('+').split(',').mapNotNull { it.toFloatOrNull() }
        if (lr.size < 3) base else base.copy(lens = base.lens.copy(innerShadow = lr[0], heldGlow = lr[1], heldEdgeTone = lr[2] / 255f,
            heldGlowFloor = lr.getOrElse(3) { base.lens.heldGlowFloor }, specular = lr.getOrElse(4) { base.lens.specular }))
    }.let { base ->
        // "+capfold=<c>[,<band scale>]": the bar's rounded ends folded at rest (GlassStyle.capFold).
        val cf = variant.substringAfter("+capfold=", "").substringBefore('+').split(',').mapNotNull { it.toFloatOrNull() }
        if (cf.isEmpty()) base else base.copy(bar = base.bar.copy(capFold = cf[0]))
    }.let { base ->
        // "+barrim=<specular>,<bevelPeak>[,<edgeLight>]": the resting bar's rim line (#96).
        val br = variant.substringAfter("+barrim=", "").substringBefore('+').split(',').mapNotNull { it.toFloatOrNull() }
        if (br.size < 2) base else base.copy(bar = base.bar.copy(specular = br[0], bevelPeak = br[1],
            edgeLight = br.getOrElse(2) { base.bar.edgeLight }))
    }.let { base ->
        // "+capedge=<c>": the bar's extra edge shadow where its outline faces sideways.
        val ce = variant.substringAfter("+capedge=", "").substringBefore('+').toFloatOrNull()
        if (ce == null) base else base.copy(bar = base.bar.copy(capEdgeShadow = ce))
    }.let { base ->
        // "+barrest": the bar on the measured resting-corner map (GlassRestMap.Measured).
        if ("+barrest" !in variant) base else base.copy(bar = base.bar.copy(restMap = com.wexpa.liquidglass.GlassRestMap.Measured))
    }.let { base ->
        // "+tap=<tapOptics>,<tapTransitFold>[,<tapSelectedScale>]": tap transit candidates.
        val tp = variant.substringAfter("+tap=", "").substringBefore('+').split(',').mapNotNull { it.toFloatOrNull() }
        if (tp.size < 2) base else base.copy(tapOptics = tp[0], tapTransitFold = tp[1],
            tapSelectedScale = tp.getOrElse(2) { base.tapSelectedScale })
    }.let { base ->
        // "+lshadow=<contactShadow>[,<inside share>[,<blur dp>[,<offset dp>]]]": the held lens's contact shadow, for fitting by replay.
        val ls = variant.substringAfter("+lshadow=", "").substringBefore('+').split(',').mapNotNull { it.toFloatOrNull() }
        if (ls.isEmpty()) base else base.copy(lens = base.lens.copy(contactShadow = ls[0],
            contactShadowInside = ls.getOrElse(1) { base.lens.contactShadowInside },
            contactShadowBlur = ls.getOrNull(2)?.let { androidx.compose.ui.unit.Dp(it) } ?: base.lens.contactShadowBlur,
            contactShadowOffset = ls.getOrNull(3)?.let { androidx.compose.ui.unit.Dp(it) } ?: base.lens.contactShadowOffset))
    }.let { base ->
        // "+pill=<black alpha>,<lift in /255>": a resting pill candidate, for fitting by replay.
        val pill = variant.substringAfter("+pill=", "").substringBefore('+').split(',').mapNotNull { it.toFloatOrNull() }
        if (pill.size < 2) base else base.copy(pill = base.pill.copy(
            tint = androidx.compose.ui.graphics.Color(0f, 0f, 0f, pill[0]), tintLift = pill[1] / 255f))
    }
    // Display colours measured on the correctly decoded recordings; "-recorded" reproduces the
    // passes that compared against the recordings decoded as sRGB (2026-10-15).
    val palette = if (baseVariant.endsWith("recorded")) PhonePalette.Recorded
        else if ("light" in baseVariant) PhonePalette.Light else PhonePalette.Natural

    lateinit var scene: ImageComposeScene
    lateinit var composeScene: ComposeScene
    lateinit var recomposer: FrameRecomposer
    var nanos = 0L
    var captured: Bitmap? = null
    var armed = false
    val layer = SkiaLayer()
    val frame = JFrame("LiquidGlass reference replay")
    SwingUtilities.invokeAndWait {
        scene = ImageComposeScene(sceneWidth, sceneHeight, Density(if (atlas) 1.5f else 3f), Dispatchers.Unconfined) {
            if (atlas) LoupeScreen(initialScene = initial)
            else {
                var selected by remember { mutableIntStateOf(initial) }
                // "+passive": the search button without press and pull, as rendered before 15 October.
                // "appstore": iOS 26's App Store five-tab bar over a dark card (AppStoreScene).
                if (variant.startsWith("appstore")) AppStoreScene(selected, { selected = it },
                    style = AppStoreGeometry.style(style, light = "light" in variant), light = "light" in variant)
                // "whatsapp": WhatsApp's five-tab bar on its measured slots (WhatsAppScene);
                // "+plain": over the bare page, without the settings card.
                else if (variant.startsWith("whatsapp")) WhatsAppScene(selected, { selected = it },
                    style = WhatsAppGeometry.style(style), card = "+plain" !in variant, light = "light" in variant)
                else PhoneScene(selected, { selected = it }, palette = palette, style = style,
                    callsBadge = if ("+badge" in variant) 2 else 0,
                    lensFusion = androidx.compose.ui.unit.Dp(variant.substringAfter("+lfuse=", "").substringBefore('+').toFloatOrNull() ?: if ("light" in variant) 24f else 0f),
                    search = if ("+passive" in variant) null else com.wexpa.liquidglass.GlassInteraction.Pullable.copy(
                        pullShape = com.wexpa.liquidglass.GlassPullShape.Free, pullLiftShare = 0.65f).let { base ->
                        // "+search=<follow>,<elongation>,<widthRatio>,<limit>,<liftShare>": a search-button candidate.
                        val k = variant.substringAfter("+search=", "").substringBefore('+').split(',').mapNotNull { it.toFloatOrNull() }
                        if (k.size < 5) base else base.copy(pullFollow = k[0], pullElongation = k[1], pullWidthRatio = k[2],
                            pullLimit = k[3], pullLiftShare = k[4], pullReleaseDampingX = k.getOrElse(5) { Float.NaN })
                    })
            }
        }
        // ImageComposeScene draws into a CPU raster. Reuse its scene and frame clock, but draw
        // into this layer's GPU canvas instead.
        composeScene = ImageComposeScene::class.java.getDeclaredField("scene").apply { isAccessible = true }
            .get(scene) as ComposeScene
        recomposer = ImageComposeScene::class.java.getDeclaredField("frameRecomposer").apply { isAccessible = true }
            .get(scene) as FrameRecomposer
        layer.renderDelegate = object : SkikoRenderDelegate {
            var target: Surface? = null
            override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
                // Draw into an offscreen render target on the layer's own GPU context and read it
                // back directly. SkiaLayer.screenshot() re-rasterizes on the CPU (seconds per frame)
                // and reading the swap-chain canvas stalls on Direct3D.
                if (!armed) return
                val surface = target ?: Surface.makeRenderTarget(checkNotNull(gpuContext(layer)) { "No GPU context" }, false,
                    ImageInfo.makeN32Premul(sceneWidth, sceneHeight)).also { target = it }
                surface.canvas.clear(Color.BLACK)
                recomposer.performFrame(nanos)
                composeScene.measureAndLayout()
                composeScene.draw(surface.canvas.asComposeCanvas())
                captured = Bitmap().apply {
                    allocPixels(ImageInfo.makeN32Premul(sceneWidth, sceneHeight))
                    check(surface.readPixels(this, 0, 0)) { "GPU readback failed" }
                }
                surface.makeImageSnapshot().use { canvas.drawImage(it, 0f, 0f) }
            }
        }
        frame.contentPane.add(layer)
        frame.isUndecorated = true
        // Never take focus or cover the owner's work: the frame only hosts the GPU surface.
        frame.focusableWindowState = false
        frame.isAutoRequestFocus = false
        frame.type = java.awt.Window.Type.UTILITY
        frame.pack()
        frame.setLocation(-8000, -8000)
        frame.isVisible = true
        val scale = layer.contentScale
        layer.preferredSize = Dimension(ceil(sceneWidth / scale).toInt(), ceil(sceneHeight / scale).toInt())
        frame.pack()
    }
    Thread.sleep(500)
    println("REPLAY renderer ${layer.renderApi} ${layer.renderInfo.lines().firstOrNull { "Video card" in it } ?: ""}")
    val trace = File(out, "trace.csv").bufferedWriter()
    trace.write("frame,ms,selectorX,bodyWidth,held,formation,protrusion,dragIndex,transit,heldTravel\n")
    var next = 0
    var pressed = false
    val frames = (end * 60 / 1000).toInt()
    armed = true
    val started = System.nanoTime()
    for (index in 0..frames) {
        val target = (index * 1000.0 / 60).roundToLong()
        SwingUtilities.invokeAndWait {
            while (next < pointer.size && pointer[next].first <= target) {
                val (ms, kind, at) = pointer[next++]
                val type = when (kind) { "down" -> PointerEventType.Press; "up" -> PointerEventType.Release; else -> PointerEventType.Move }
                pressed = kind != "up"
                scene.sendPointerEvent(
                    type, listOf(ComposeScenePointer(PointerId(1), at, pressed, PointerType.Touch)),
                    timeMillis = ms.roundToLong(),
                )
            }
            nanos = target * 1_000_000
            val t0 = System.nanoTime()
            layer.renderImmediately()
            val t1 = System.nanoTime()
            val bitmap = checkNotNull(captured).also { captured = null }
            val t2 = System.nanoTime()
            Image.makeFromBitmap(bitmap).use { image ->
                File(out, "f%05d.png".format(index)).writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
            bitmap.close()
            val t3 = System.nanoTime()
            if (index % 600 == 0) println("REPLAY cost render=${(t1 - t0) / 1e6} readback=${(t2 - t1) / 1e6} encode=${(t3 - t2) / 1e6}")
            val owner = scene.semanticsOwners.first()
            val node = owner.unmergedRootSemanticsNode.let { root ->
                fun find(n: androidx.compose.ui.semantics.SemanticsNode): androidx.compose.ui.semantics.SemanticsNode? =
                    if (n.config.any { it.key.name == "GlassTabBarSelectorX" }) n
                    else n.children.firstNotNullOfOrNull { find(it) }
                find(root)
            }
            val c = node?.config?.associate { it.key.name to it.value } ?: emptyMap()
            fun v(name: String) = c["GlassTabBar$name"]
            trace.write("$index,$target,${v("SelectorX")},${v("BodyWidth")},${v("Held")},${v("Formation")}," +
                "${v("Protrusion")},${v("DragIndex")},${v("Transit")},${v("HeldTravel")}\n")
        }
        if (index % 120 == 0) { trace.flush(); println("REPLAY frame $index/$frames ${(System.nanoTime() - started) / 1_000_000} ms") }
    }
    trace.close()
    SwingUtilities.invokeAndWait { frame.dispose(); scene.close() }
    println("REPLAY done $frames frames in ${(System.nanoTime() - started) / 1_000_000} ms")
    kotlin.system.exitProcess(0)
}

/** The DirectContext behind [layer]'s current redrawer. Skiko does not expose it publicly. */
private fun gpuContext(layer: SkiaLayer): DirectContext? {
    val redrawer = checkNotNull(layer.javaClass.getMethod("getRedrawer\$skiko").invoke(layer)) { "No redrawer" }
    val handler = redrawer.javaClass.getDeclaredField("contextHandler").apply { isAccessible = true }.get(redrawer)
    var type: Class<*>? = handler.javaClass
    while (type != null) {
        type.declaredFields.firstOrNull { it.name == "context" }?.let {
            it.isAccessible = true
            return it.get(handler) as DirectContext?
        }
        type = type.superclass
    }
    error("No DirectContext on ${handler.javaClass.name}")
}

package com.wexpa.liquidglass.sample.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.IRect
import org.jetbrains.skiko.SkiaLayer
import java.awt.Component
import java.awt.Container
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Sample-only observations. Playback never calls the selector controller or changes selection. */
internal class AtlasMotionProbe {
    var navigation: Rect = Rect.Zero
    var scene: Int = 0
}

/** Own-window AWT input and own-buffer captures; no global cursor, screen grab or OS automation. */
internal suspend fun captureAtlasMotion(window: Container, probe: AtlasMotionProbe, directory: File) {
    check(SwingUtilities.isEventDispatchThread())
    fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }
    val layer = descendants(window).filterIsInstance<SkiaLayer>().single()
    val input = descendants(layer).firstOrNull { component ->
        component.mouseListeners.any { it.javaClass.name.startsWith("androidx.compose.ui.scene.ComposeSceneMediator") }
    } ?: error("No Compose mouse input component in this window")
    val bounds = probe.navigation
    check(bounds.width > 0f && bounds.height > 0f) { "Atlas navigation needs a wide desktop window" }
    check(probe.scene == 0) { "Motion capture starts with the Sky scene" }
    check(directory.mkdirs() || directory.isDirectory)
    val scale = layer.contentScale
    val started = System.nanoTime()
    val rows = arrayListOf("label,elapsed_ms,event,x_px,y_px,scene,render_ms,readback_ms")
    val frames = ArrayList<Pair<String, Bitmap>>()
    var pressed = false
    var lastPoint = Offset.Zero
    val crop = IRect.makeLTRB(floor(bounds.left - bounds.height).toInt().coerceAtLeast(0),
        floor(bounds.top - bounds.height).toInt().coerceAtLeast(0),
        ceil(bounds.right + bounds.height).toInt(), ceil(bounds.bottom + bounds.height).toInt())
    fun elapsed() = (System.nanoTime() - started) / 1_000_000.0
    fun fixedLayout() {
        val current = probe.navigation
        check(abs(current.left - bounds.left) < .01f && abs(current.top - bounds.top) < .01f &&
            abs(current.width - bounds.width) < .01f && abs(current.height - bounds.height) < .01f) {
            "Navigation layout moved during material feedback: $bounds -> $current"
        }
    }
    fun send(id: Int, point: Offset, down: Boolean) {
        val local = SwingUtilities.convertPoint(layer,
            (point.x / scale).roundToInt(), (point.y / scale).roundToInt(), input)
        input.dispatchEvent(MouseEvent(input, id, System.currentTimeMillis(),
            if (down) InputEvent.BUTTON1_DOWN_MASK else 0, local.x, local.y,
            if (id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_RELEASED) 1 else 0,
            false, if (id == MouseEvent.MOUSE_DRAGGED) MouseEvent.NOBUTTON else MouseEvent.BUTTON1))
        rows += "input,${elapsed()},$id,${point.x},${point.y},${probe.scene},,"
        pressed = down
        lastPoint = point
    }
    fun capture(name: String) {
        fixedLayout()
        val start = System.nanoTime()
        layer.renderImmediately()
        val rendered = System.nanoTime()
        frames += name to checkNotNull(layer.screenshot())
        rows += "$name,${(start-started)/1e6},capture,,,${probe.scene},${(rendered-start)/1e6},${(System.nanoTime()-rendered)/1e6}"
    }
    val left = Offset(bounds.left + bounds.width / 6f, bounds.center.y)
    val right = Offset(bounds.right - bounds.width / 6f, bounds.center.y)
    suspend fun prepare(scene: Int) {
        if (pressed) send(MouseEvent.MOUSE_RELEASED, lastPoint, false)
        delay(700)
        val point = if (scene == 0) left else right
        send(MouseEvent.MOUSE_PRESSED, point, true)
        delay(50)
        send(MouseEvent.MOUSE_RELEASED, point, false)
        delay(700)
        check(probe.scene == scene) { "Native input failed to select scene $scene: ${probe.scene}" }
    }
    suspend fun drag(steps: Int) {
        prepare(2)
        send(MouseEvent.MOUSE_PRESSED, right, true)
        delay(650)
        for (index in 1..steps) {
            delay(10)
            send(MouseEvent.MOUSE_DRAGGED, right + (left-right) * (index / 12f), true)
        }
    }
    try {
        capture("00-rest")
        // Readback on this backend takes over a second. Each phase therefore replays
        // the gesture independently; no intermediate screenshot can slow its inputs.
        for (wait in listOf(25L, 70L, 140L, 490L)) {
            prepare(0)
            send(MouseEvent.MOUSE_PRESSED, right, true)
            delay(50)
            send(MouseEvent.MOUSE_RELEASED, right, false)
            delay(wait); capture("01-tap-${wait}ms")
            check(probe.scene == 2) { "Native tap did not select Lines: ${probe.scene}" }
        }
        prepare(2)
        send(MouseEvent.MOUSE_PRESSED, right, true)
        delay(650); capture("02-held")
        for (steps in listOf(4, 8, 12)) {
            drag(steps); capture("03-travel-$steps")
        }
        for (wait in listOf(25L, 70L, 160L, 760L)) {
            drag(12)
            send(MouseEvent.MOUSE_RELEASED, left, false)
            delay(wait); capture("04-release-${wait}ms")
            check(probe.scene == 0) { "Native held drag did not select Sky: ${probe.scene}" }
        }
        prepare(0)
        send(MouseEvent.MOUSE_PRESSED, left, true)
        delay(650)
        val topLeft = left + Offset(-4000f, -4000f)
        send(MouseEvent.MOUSE_DRAGGED, topLeft, true)
        delay(250); capture("05-extreme-top-left")
        prepare(0)
        send(MouseEvent.MOUSE_PRESSED, left, true)
        delay(650)
        val bottomRight = left + Offset(4000f, 4000f)
        send(MouseEvent.MOUSE_DRAGGED, bottomRight, true)
        delay(250); capture("06-extreme-bottom-right")
        send(MouseEvent.MOUSE_RELEASED, bottomRight, false)
        delay(1000); capture("07-recovered")
        check(probe.scene == 2) { "Extreme drag lost selection intent: ${probe.scene}" }
    } finally {
        // Do not leave a held pointer behind if a verification assertion fails.
        if (pressed) send(MouseEvent.MOUSE_RELEASED, lastPoint, false)
        File(directory, "events.csv").writeText(rows.joinToString("\n") + "\n")
        File(directory, "capture.json").writeText("""{
          "backend":"${layer.renderApi}","scale":$scale,
          "navigation":{"left":${bounds.left},"top":${bounds.top},"width":${bounds.width},"height":${bounds.height}},
          "input":"AWT events dispatched only to this application's Compose component",
          "timing":"Each phase uses a fresh gesture replay because native readback is slow. CSV capture timestamps are render-start times. PNG encoding follows input. Not a continuous video, presented FPS or measured iOS timing."
        }""")
        // Encoding can take much longer than drawing. It must not pause an active gesture.
        // Release every owned bitmap even if an assertion or encoder fails.
        try {
            withContext(Dispatchers.IO) {
                for ((name, bitmap) in frames) {
                    Bitmap().use { subset ->
                        val clipped = IRect.makeLTRB(crop.left, crop.top,
                            crop.right.coerceAtMost(bitmap.width), crop.bottom.coerceAtMost(bitmap.height))
                        check(bitmap.extractSubset(subset, clipped))
                        Image.makeFromBitmap(subset).use { image ->
                            checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).use {
                                File(directory, "$name.png").writeBytes(it.bytes)
                            }
                        }
                    }
                }
            }
        } finally { frames.forEach { it.second.close() } }
    }
    println("ATLAS_MOTION_CAPTURE ${directory.absolutePath} selection-and-layout-checks=passed")
}

package com.wexpa.liquidglass.sample.web

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import com.wexpa.liquidglass.sample.LoupeScreen
import com.wexpa.liquidglass.sample.MaterialShowcase
import kotlinx.browser.document
import kotlinx.browser.window

/**
 * The live demo: Atlas Studio (the Loupe screen) or, at `#material3`, a Material 3 app built from
 * `liquidglass-material3`. Each links to the other; the address keeps the choice. index.html shows a
 * loader until [awaitSettledFrames] returns.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // The Wasm has downloaded and started; what is left is the first frames.
    document.getElementById("loader-title")?.textContent = "Preparing the glass"
    // The loader's long-wait notice reads this: before it, the wait is the download, after it the shader compile (#78).
    document.getElementById("loader")?.setAttribute("data-stage", "started")
    // Not the body: Compose replaces its container's children, and the loader must outlive the first frames.
    ComposeViewport(document.getElementById("app")!!) {
        LaunchedEffect(Unit) {
            if (!awaitSettledFrames()) println("LiquidGlass demo: no frame settled within $SettleFrameCap frames; showing the app anyway")
            // A failure index.html reported while the frames settled stays on screen.
            val loader = document.getElementById("loader")
            if (loader?.getAttribute("data-state") != "failed") loader?.setAttribute("data-state", "ready")
        }
        var material by remember { mutableStateOf(window.location.hash == "#material3") }
        /** Shows the Material 3 app ([m]) or Atlas Studio, and keeps the choice in the address. */
        fun show(m: Boolean) { material = m; window.location.hash = if (m) "material3" else "" }
        if (material) MaterialShowcase(onSwitchScreen = { show(false) })
        else Box {
            LoupeScreen()
            Text(
                "Material 3 demo →", color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    .background(Color(0x99000000), RoundedCornerShape(50)).clickable { show(true) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private const val SettleFrameCap = 60

/** Consecutive quick frames that count as settled: one quick frame can come before a slow one. */
private const val SettledFrames = 3

/**
 * True once [SettledFrames] frames in a row each follow the previous one within 100 ms; false after
 * [SettleFrameCap] frames without that. On a first visit the opening frames compile the glass shaders,
 * about 2 s each on a laptop's integrated GPU (review/atlas/web-demo-load-2026-10-20); the page would
 * show them as a black or half-drawn canvas. False still reveals the app: a device that never draws
 * frames that fast (a slow GPU, a heavy scene) would otherwise keep the loader up for good.
 */
private suspend fun awaitSettledFrames(): Boolean {
    var last = withFrameNanos { it }
    var quick = 0
    repeat(SettleFrameCap) {
        val now = withFrameNanos { it }
        quick = if (now - last < 100_000_000L) quick + 1 else 0
        if (quick >= SettledFrames) return true
        last = now
    }
    return false
}

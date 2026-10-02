package com.wexpa.liquidglass.sample.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.wexpa.liquidglass.sample.LoupeScreen
import kotlinx.coroutines.delay
import java.awt.Toolkit

fun main() = application {
    val screenSize = Toolkit.getDefaultToolkit().screenSize
    println(">>> Starting Atlas Studio at native resolution: ${screenSize.width}x${screenSize.height}")

    val windowState = rememberWindowState(
        placement = WindowPlacement.Maximized
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "Atlas Studio - LiquidGlass",
        state = windowState,
        alwaysOnTop = true,
    ) {
        LaunchedEffect(Unit) {
            try {
                window.toFront()
                window.requestFocus()
                delay(2000)
                window.isAlwaysOnTop = false
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        LoupeScreen()
    }
}

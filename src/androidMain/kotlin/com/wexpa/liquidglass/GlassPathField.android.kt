package com.wexpa.liquidglass

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal actual fun createFieldBitmap(
    width: Int,
    height: Int,
    pixels: IntArray,
): ImageBitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    .asImageBitmap()

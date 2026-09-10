package com.wexpa.liquidglass.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.wexpa.liquidglass.GlassGallery

/**
 * The tuning surface, [GlassGallery], on a phone.
 *
 * A dark app gives the material almost nothing to refract, so every style looks alike there.
 * The gallery is content worth bending: saturated colour, hard stripes and fine text, over which
 * the differences between the presets, and the mistakes, are obvious.
 *
 *     adb shell am start -n com.wexpa.liquidglass.sample/.GalleryActivity
 */
class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GlassGallery() }
    }
}

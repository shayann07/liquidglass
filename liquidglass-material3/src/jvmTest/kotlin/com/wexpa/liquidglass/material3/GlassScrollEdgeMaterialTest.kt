@file:OptIn(ExperimentalMaterial3Api::class)

package com.wexpa.liquidglass.material3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/** The Material 3 overload of the scroll edge effect tracks the top app bar's `overlappedFraction` (#207, authored expectation). */
@OptIn(ExperimentalTestApi::class)
class GlassScrollEdgeMaterialTest {
    @Test fun theTopEdgeFollowsTheAppBarsOverlappedFraction() = runDesktopComposeUiTest(90, 300, testTimeout = 3.minutes) {
        lateinit var state: androidx.compose.material3.TopAppBarState
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f)) {
                state = rememberTopAppBarState()
                val behavior = TopAppBarDefaults.pinnedScrollBehavior(state)
                Box(Modifier.fillMaxSize().glassScrollEdge(behavior, Color.Black, inverted = true).background(Color(255, 44, 85)))
            }
        }
        fun tint(): Int { waitForIdle(); return 255 - (onRoot().captureToImage().toPixelMap()[45, 0].red * 255f).roundToInt() }
        assertEquals(0, tint(), "the effect draws before the content has scrolled under the bar")
        state.heightOffsetLimit = -100f
        state.contentOffset = -50f        // overlappedFraction 0.5
        assertEquals(.5f, state.overlappedFraction, 1e-4f)
        assertEquals(0.416 * 131 * .5, tint().toDouble(), 3.0, "half way under the bar")
        state.contentOffset = -200f       // fully under
        assertEquals(0.416 * 131, tint().toDouble(), 3.0, "fully under the bar")
    }
}

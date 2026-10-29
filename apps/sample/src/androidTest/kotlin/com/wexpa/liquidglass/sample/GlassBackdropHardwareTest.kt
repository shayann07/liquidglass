package com.wexpa.liquidglass.sample

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wexpa.liquidglass.CapsuleShape
import com.wexpa.liquidglass.Glass
import com.wexpa.liquidglass.GlassStyle
import com.wexpa.liquidglass.GlassBackdrop
import com.wexpa.liquidglass.GlassXmlDarkMode
import com.wexpa.liquidglass.GlassXmlPreset
import com.wexpa.liquidglass.LiquidGlassHostView
import com.wexpa.liquidglass.LiquidGlassView
import com.wexpa.liquidglass.GlassNativeRenderer
import com.wexpa.liquidglass.glassEffect
import com.wexpa.liquidglass.GlassViewEffect
import com.wexpa.liquidglass.R as LibR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class GlassBackdropHardwareTest {

    private fun waitForDraw(view: View) {
        val latch = CountDownLatch(1)
        view.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.viewTreeObserver.registerFrameCommitCallback {
                    latch.countDown()
                }
            } else {
                val listener = object : ViewTreeObserver.OnDrawListener {
                    override fun onDraw() {
                        view.post {
                            latch.countDown()
                        }
                    }
                }
                view.viewTreeObserver.addOnDrawListener(listener)
            }
            view.invalidate()
        }
        assertTrue("Timed out waiting for frame commit", latch.await(30, TimeUnit.SECONDS))
    }

    private fun captureWindow(activity: Activity): Bitmap {
        val decor = activity.window.decorView
        assertTrue("Window must be hardware accelerated", decor.isHardwareAccelerated)
        val width = decor.width.coerceAtLeast(1)
        val height = decor.height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var copyResult = -1

        activity.runOnUiThread {
            try {
                PixelCopy.request(
                    activity.window,
                    bmp,
                    { result ->
                        copyResult = result
                        latch.countDown()
                    },
                    Handler(Looper.getMainLooper()),
                )
            } catch (e: Throwable) {
                latch.countDown()
            }
        }

        assertTrue("Timed out waiting for PixelCopy", latch.await(3, TimeUnit.SECONDS))
        assertEquals("PixelCopy must succeed with SUCCESS (code 0)", PixelCopy.SUCCESS, copyResult)
        return bmp
    }



    @Test
    fun xmlInteropActivityHardwareWindowRenderingAndPixelAssertion() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            var backdropRef: GlassBackdrop? = null
            var headerCardRef: LiquidGlassView? = null
            var activityRef: Activity? = null

            scenario.onActivity { activity ->
                activityRef = activity
                backdropRef = activity.findViewById(R.id.xmlBackdrop)
                headerCardRef = activity.findViewById(R.id.xmlHeaderCard)
            }

            val activity = activityRef!!
            val backdrop = backdropRef!!
            val headerCard = headerCardRef!!

            // Finding 1: Ensure ViewGroup background-less defaults are set correctly
            assertNull("xmlBackdrop has no background by default in XML", backdrop.background)
            assertFalse("xmlBackdrop must have setWillNotDraw(false) for hardware display list recording", backdrop.willNotDraw())

            waitForDraw(activity.window.decorView)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val sourceNode = backdrop.sourceNode
                assertNotNull("sourceNode should be allocated on API 29+", sourceNode)
                assertTrue("sourceNode must have display list recorded from window traversal", sourceNode!!.hasDisplayList())
            }

            // Finding 14: Capture presented window pixels and assert non-empty, rendered glass output
            val bitmap = captureWindow(activity)
            assertNotNull(bitmap)

            val location = IntArray(2)
            scenario.onActivity {
                headerCard.getLocationInWindow(location)
            }
            val cardX = location[0] + headerCard.width / 2
            val cardY = location[1] + headerCard.height / 2

            val centerPixel = bitmap.getPixel(cardX, cardY)
            val alpha = AndroidColor.alpha(centerPixel)
            val red = AndroidColor.red(centerPixel)
            val green = AndroidColor.green(centerPixel)
            val blue = AndroidColor.blue(centerPixel)

            // Glass header over dark background with scroll items must be fully opaque and non-zero
            assertEquals("Center pixel under header card must be fully opaque", 255, alpha)
            assertTrue(
                "Center pixel under header card must contain rendered content (R=$red, G=$green, B=$blue)",
                red > 0 || green > 0 || blue > 0,
            )

        } finally {
            scenario.close()
        }
    }

    @Test
    fun rejectConsumersInsideOwnSourceSubtree() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val backdrop = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)

                // 1. Direct child added to backdrop cannot be registered as consumer
                val directChild = View(activity)
                backdrop.addView(directChild)
                try {
                    backdrop.registerGlassConsumer(directChild)
                    fail("registerGlassConsumer must throw IllegalStateException for direct child")
                } catch (expected: IllegalStateException) {
                    assertTrue(expected.message!!.contains("cannot be placed inside its own source backdrop"))
                }

                // 2. Registering first then adding to backdrop must throw during onViewAdded
                val pendingChild = View(activity)
                backdrop.registerGlassConsumer(pendingChild)
                try {
                    backdrop.addView(pendingChild)
                    fail("onViewAdded must throw IllegalStateException when registering descendant")
                } catch (expected: IllegalStateException) {
                    assertTrue(expected.message!!.contains("cannot be added inside its own source backdrop"))
                }

                // 3. Deeply nested child must also be rejected
                val subContainer = FrameLayout(activity)
                val nestedView = View(activity)
                subContainer.addView(nestedView)
                backdrop.addView(subContainer)
                try {
                    backdrop.registerGlassConsumer(nestedView)
                    fail("registerGlassConsumer must throw IllegalStateException for deeply nested child")
                } catch (expected: IllegalStateException) {
                    assertTrue(expected.message!!.contains("cannot be placed inside its own source backdrop"))
                }

                // 4. LiquidGlassView targeting ancestor backdrop must throw
                val nestedGlassView = LiquidGlassView(activity)
                try {
                    backdrop.addView(nestedGlassView)
                    nestedGlassView.backdropView = backdrop
                    fail("LiquidGlassView.backdropView must throw IllegalStateException when targeting ancestor backdrop")
                } catch (expected: IllegalStateException) {
                    assertTrue(
                        expected.message!!.contains("cannot target an ancestor GlassBackdrop") ||
                            expected.message!!.contains("cannot be added inside its own source backdrop")
                    )
                }
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun nativeClickAndLongClickDeliveryPreserved() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            var activityRef: Activity? = null
            var glassContainerRef: LiquidGlassView? = null
            var childButtonRef: TextView? = null
            var rootRef: ViewGroup? = null

            val containerClicked = AtomicBoolean(false)
            val containerLongClicked = AtomicBoolean(false)
            val childClicked = AtomicBoolean(false)

            scenario.onActivity { activity ->
                activityRef = activity
                val backdrop = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)
                val root = activity.findViewById<ViewGroup>(R.id.xmlRoot)
                rootRef = root

                val glassContainer = LiquidGlassView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(300, 200).apply {
                        gravity = Gravity.CENTER
                    }
                    backdropView = backdrop
                    isGlassInteractive = true
                    isClickable = true
                    isLongClickable = true
                    setOnClickListener {
                        containerClicked.set(true)
                    }
                    setOnLongClickListener {
                        containerLongClicked.set(true)
                        true
                    }
                }
                glassContainerRef = glassContainer

                val childButton = TextView(activity).apply {
                    text = "Child Clickable"
                    layoutParams = FrameLayout.LayoutParams(150, 80).apply {
                        gravity = Gravity.CENTER
                    }
                    isClickable = true
                    setOnClickListener {
                        childClicked.set(true)
                    }
                }
                childButtonRef = childButton

                glassContainer.addView(childButton)
                root.addView(glassContainer)
            }

            val activity = activityRef!!
            val glassContainer = glassContainerRef!!
            val childButton = childButtonRef!!
            val root = rootRef!!

            waitForDraw(activity.window.decorView)

            // Finding 5: performClick and performLongClick work natively
            scenario.onActivity {
                glassContainer.performClick()
            }
            assertTrue("LiquidGlassView container performClick must trigger OnClickListener", containerClicked.get())

            scenario.onActivity {
                glassContainer.performLongClick()
            }
            assertTrue("LiquidGlassView container performLongClick must trigger OnLongClickListener", containerLongClicked.get())

            scenario.onActivity {
                childButton.performClick()
            }
            assertTrue("Child clickable inside LiquidGlassView must receive click", childClicked.get())

            // Test MotionEvent dispatch
            containerClicked.set(false)
            val clickLatch = CountDownLatch(1)
            scenario.onActivity {
                glassContainer.setOnClickListener {
                    containerClicked.set(true)
                    clickLatch.countDown()
                }
                val now = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
                val up = MotionEvent.obtain(now, now + 150, MotionEvent.ACTION_UP, 10f, 10f, 0)

                glassContainer.dispatchTouchEvent(down)
                glassContainer.dispatchTouchEvent(up)
                down.recycle()
                up.recycle()
            }

            assertTrue("MotionEvent DOWN/UP sequence must trigger container OnClickListener", clickLatch.await(2, TimeUnit.SECONDS))
            assertTrue("Container clicked must be true", containerClicked.get())

            scenario.onActivity {
                root.removeView(glassContainer)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun runtimePropertyMutationUpdatesRendererWithoutDetaching() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            var activityRef: Activity? = null
            var headerCardRef: LiquidGlassView? = null
            scenario.onActivity { activity ->
                activityRef = activity
                headerCardRef = activity.findViewById(R.id.xmlHeaderCard)
            }
            val activity = activityRef!!
            val headerCard = headerCardRef!!

            waitForDraw(activity.window.decorView)
            val beforeBitmap = captureWindow(activity)
            val location = IntArray(2)
            scenario.onActivity {
                headerCard.getLocationInWindow(location)
            }
            val cardX = location[0] + headerCard.width / 2
            val cardY = location[1] + headerCard.height / 2
            val pixelBefore = beforeBitmap.getPixel(cardX, cardY)

            scenario.onActivity {
                headerCard.preset = GlassXmlPreset.Clear
                assertEquals(GlassXmlPreset.Clear, headerCard.preset)

                headerCard.cornerRadiusDp = 36.dp
                assertEquals(36.dp, headerCard.cornerRadiusDp)

                headerCard.glassTint = Color.Magenta
                assertEquals(Color.Magenta, headerCard.glassTint)
            }
            waitForDraw(activity.window.decorView)
            val afterBitmap = captureWindow(activity)
            val pixelAfter = afterBitmap.getPixel(cardX, cardY)

            assertTrue("Mutating glassTint to Magenta must alter rendered pixels", pixelBefore != pixelAfter)
            assertTrue("Magenta tint must produce red and blue components", AndroidColor.red(pixelAfter) > 0 && AndroidColor.blue(pixelAfter) > 0)

            scenario.onActivity {
                headerCard.darkMode = GlassXmlDarkMode.Light
                assertEquals(GlassXmlDarkMode.Light, headerCard.darkMode)

                headerCard.darkMode = GlassXmlDarkMode.Dark
                assertEquals(GlassXmlDarkMode.Dark, headerCard.darkMode)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun identityPresetRendersZeroMaterial() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            var activityRef: Activity? = null
            var testGlassRef: LiquidGlassView? = null

            scenario.onActivity { activity ->
                activityRef = activity
                val root = activity.findViewById<ViewGroup>(R.id.xmlRoot)
                val backdrop = GlassBackdrop(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(400, 400)
                }
                val greenContent = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(400, 400)
                    setBackgroundColor(AndroidColor.GREEN)
                }
                backdrop.addView(greenContent)
                root.addView(backdrop)

                val glassView = LiquidGlassView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(200, 200).apply {
                        leftMargin = 50
                        topMargin = 50
                    }
                    this.backdropView = backdrop
                    preset = GlassXmlPreset.Identity
                }
                root.addView(glassView)
                testGlassRef = glassView
            }
            val activity = activityRef!!
            val glassView = testGlassRef!!

            waitForDraw(activity.window.decorView)
            val bmp = captureWindow(activity)
            assertNotNull(bmp)

            val location = IntArray(2)
            scenario.onActivity {
                glassView.getLocationInWindow(location)
            }
            val centerX = location[0] + glassView.width / 2
            val centerY = location[1] + glassView.height / 2
            val pixel = bmp.getPixel(centerX, centerY)

            // Identity preset has style == null: zero material distortion, preserving exact backdrop green
            assertEquals("Identity preset renders zero glass material, matching pure backdrop green", AndroidColor.GREEN, pixel)

            // Also test on GlassViewEffect: Identity represents zero glass material (no blur/refraction)
            scenario.onActivity {
                val testView = TextView(activity)
                val effect = testView.glassEffect(
                    backdrop = activity.findViewById(R.id.xmlBackdrop),
                    glass = Glass.Identity,
                )
                assertTrue(effect.glass.isIdentity)
                assertNull("Glass.Identity must resolve style(dark=false) to null", effect.glass.style(false))
                assertNull("Glass.Identity must resolve style(dark=true) to null", effect.glass.style(true))
                effect.dispose()
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun glassViewEffectDisposeAndRebinding() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(R.id.xmlRoot)
                val bd1 = GlassBackdrop(activity)
                val bd2 = GlassBackdrop(activity)
                root.addView(bd1)
                root.addView(bd2)

                val targetView = TextView(activity).apply {
                    text = "Target View"
                }
                root.addView(targetView)

                // Finding 11: glassEffect clean rebind & dispose
                val effect1 = targetView.glassEffect(
                    backdrop = bd1,
                    glass = Glass.Regular,
                    shape = RoundedCornerShape(12.dp),
                )
                assertNotNull(effect1)
                assertEquals(bd1, effect1.backdrop)
                assertEquals(effect1, targetView.getTag(LibR.id.liquid_glass_view_effect_tag))

                // Rebind to bd2
                val effect2 = targetView.glassEffect(
                    backdrop = bd2,
                    glass = Glass.Clear,
                    shape = CapsuleShape,
                )
                assertNotNull(effect2)
                assertEquals(bd2, effect2.backdrop)
                assertEquals(effect2, targetView.getTag(LibR.id.liquid_glass_view_effect_tag))

                // Dispose
                effect2.dispose()
                assertNull("targetView tag must be cleared after dispose", targetView.getTag(LibR.id.liquid_glass_view_effect_tag))

                root.removeView(targetView)
                root.removeView(bd1)
                root.removeView(bd2)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun asymmetricCornerGeometryInLtrAndRtl() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            var activityRef: Activity? = null
            var viewRef: View? = null
            var effectRef: GlassViewEffect? = null

            scenario.onActivity { activity ->
                activityRef = activity
                val root = activity.findViewById<ViewGroup>(R.id.xmlRoot)

                // Setup blue underlay
                val underlay = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(300, 200).apply {
                        leftMargin = 50
                        topMargin = 50
                    }
                    setBackgroundColor(AndroidColor.BLUE)
                }
                // Setup backdrop with red content
                val backdrop = GlassBackdrop(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(300, 200).apply {
                        leftMargin = 50
                        topMargin = 50
                    }
                }
                val redContent = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(300, 200)
                    setBackgroundColor(AndroidColor.RED)
                }
                backdrop.addView(redContent)
                root.addView(backdrop)

                // Blue underlay covers backdrop in the window so clipped areas reveal blue
                root.addView(underlay)

                // Asymmetric shape: topStart=50.dp, topEnd=0.dp, bottomEnd=0.dp, bottomStart=0.dp
                val asymmetricShape = RoundedCornerShape(topStart = 50.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 0.dp)

                val glassView = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(300, 200).apply {
                        leftMargin = 50
                        topMargin = 50
                    }
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                }
                val effect = glassView.glassEffect(
                    backdrop = backdrop,
                    glass = Glass.Regular,
                    shape = asymmetricShape,
                )
                root.addView(glassView)

                viewRef = glassView
                effectRef = effect
            }

            val activity = activityRef!!
            val glassView = viewRef!!
            val effect = effectRef!!

            // In LTR: topStart is Top-Left (50dp ~ 100-150px curve). Top-Right is 0dp (sharp corner).
            waitForDraw(activity.window.decorView)
            val ltrBitmap = captureWindow(activity)
            assertNotNull(ltrBitmap)

            val loc = IntArray(2)
            scenario.onActivity {
                glassView.getLocationInWindow(loc)
            }
            val vx = loc[0]
            val vy = loc[1]

            // Top-left corner (vx + 5, vy + 5) is clipped by 50dp curve -> reveals blue underlay
            val ltrTopLeft = ltrBitmap.getPixel(vx + 5, vy + 5)
            assertTrue(
                "LTR: Top-left corner inside 50dp curve must reveal blue underlay (was R=${AndroidColor.red(ltrTopLeft)}, B=${AndroidColor.blue(ltrTopLeft)})",
                AndroidColor.blue(ltrTopLeft) > 180 && AndroidColor.red(ltrTopLeft) < 80,
            )

            // Top-right corner (vx + 295, vy + 5) has 0dp curve -> rendered red glass
            val ltrTopRight = ltrBitmap.getPixel(vx + 295, vy + 5)
            assertTrue(
                "LTR: Top-right corner (0dp radius) must render red glass (was R=${AndroidColor.red(ltrTopRight)}, B=${AndroidColor.blue(ltrTopRight)})",
                AndroidColor.red(ltrTopRight) > 220 && AndroidColor.red(ltrTopRight) > AndroidColor.blue(ltrTopRight) + 25,
            )

            // Switch to RTL: topStart becomes Top-Right (50dp curve). Top-Left becomes 0dp (sharp corner).
            scenario.onActivity {
                glassView.layoutDirection = View.LAYOUT_DIRECTION_RTL
                glassView.invalidate()
            }
            waitForDraw(activity.window.decorView)
            val rtlBitmap = captureWindow(activity)
            assertNotNull(rtlBitmap)

            // In RTL: Top-left corner (vx + 5, vy + 5) has 0dp curve -> rendered red glass
            val rtlTopLeft = rtlBitmap.getPixel(vx + 5, vy + 5)
            assertTrue(
                "RTL: Top-left corner (0dp radius) must render red glass (was R=${AndroidColor.red(rtlTopLeft)}, B=${AndroidColor.blue(rtlTopLeft)})",
                AndroidColor.red(rtlTopLeft) > 220 && AndroidColor.red(rtlTopLeft) > AndroidColor.blue(rtlTopLeft) + 25,
            )

            // In RTL: Top-right corner (vx + 295, vy + 5) has 50dp curve -> clipped, reveals blue underlay
            val rtlTopRight = rtlBitmap.getPixel(vx + 295, vy + 5)
            assertTrue(
                "RTL: Top-right corner inside 50dp curve must reveal blue underlay (was R=${AndroidColor.red(rtlTopRight)}, B=${AndroidColor.blue(rtlTopRight)})",
                AndroidColor.blue(rtlTopRight) > 180 && AndroidColor.red(rtlTopRight) < 80,
            )

            scenario.onActivity {
                effect.dispose()
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun sourceMappingRefreshesOnViewTranslation() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            var testGlassViewRef: LiquidGlassView? = null
            var activityRef: Activity? = null

            scenario.onActivity { activity ->
                activityRef = activity
                val root = activity.findViewById<ViewGroup>(R.id.xmlRoot)

                val backdrop = GlassBackdrop(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(600, 800)
                }
                val redStripe = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(600, 400).apply {
                        topMargin = 0
                    }
                    setBackgroundColor(AndroidColor.RED)
                }
                val blueStripe = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(600, 400).apply {
                        topMargin = 400
                    }
                    setBackgroundColor(AndroidColor.BLUE)
                }
                backdrop.addView(redStripe)
                backdrop.addView(blueStripe)
                root.addView(backdrop)

                // Opaque green barrier covering backdrop ensures test fails if glass rendering is absent
                val barrier = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(600, 800)
                    setBackgroundColor(AndroidColor.GREEN)
                }
                root.addView(barrier)

                val glassView = LiquidGlassView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(300, 200).apply {
                        leftMargin = 100
                        topMargin = 100
                    }
                    this.backdropView = backdrop
                    preset = GlassXmlPreset.Clear
                }
                root.addView(glassView)
                testGlassViewRef = glassView
            }

            val activity = activityRef!!
            val glassView = testGlassViewRef!!

            waitForDraw(activity.window.decorView)
            val beforeBitmap = captureWindow(activity)
            assertNotNull(beforeBitmap)

            val location = IntArray(2)
            scenario.onActivity {
                glassView.getLocationInWindow(location)
            }
            val origCenterX = location[0] + glassView.width / 2
            val origCenterY = location[1] + glassView.height / 2
            val pixelBefore = beforeBitmap.getPixel(origCenterX, origCenterY)

            // Glass samples RED stripe (0xFFFF0000) from backdrop over green barrier
            assertTrue(
                "Before translation, glass over RED stripe must sample high red and low green (was R=${AndroidColor.red(pixelBefore)}, G=${AndroidColor.green(pixelBefore)}, B=${AndroidColor.blue(pixelBefore)})",
                AndroidColor.red(pixelBefore) > 150 && AndroidColor.blue(pixelBefore) < 80 && AndroidColor.green(pixelBefore) < 80,
            )

            // Finding 13: Translation causes transformMatrix change detected by preDrawListener
            // Translate down by 350px so glassView is now completely over the BLUE stripe (y in 450..650)
            scenario.onActivity {
                glassView.translationY = 350f
            }
            waitForDraw(activity.window.decorView)

            val afterBitmap = captureWindow(activity)
            assertNotNull(afterBitmap)

            val newCenterY = origCenterY + 350
            val pixelAtOldCenter = afterBitmap.getPixel(origCenterX, origCenterY)
            val pixelAtNewCenter = afterBitmap.getPixel(origCenterX, newCenterY)

            // Old center now reveals the green barrier underneath without glass
            assertTrue(
                "Old center must reveal green barrier without glass (was R=${AndroidColor.red(pixelAtOldCenter)}, G=${AndroidColor.green(pixelAtOldCenter)}, B=${AndroidColor.blue(pixelAtOldCenter)})",
                AndroidColor.green(pixelAtOldCenter) > 200 && AndroidColor.red(pixelAtOldCenter) < 80 && AndroidColor.blue(pixelAtOldCenter) < 80,
            )

            // New center now samples the blue stripe underneath through translated glass mapping
            assertTrue(
                "After translation, glass over BLUE stripe must sample high blue and low green (was R=${AndroidColor.red(pixelAtNewCenter)}, G=${AndroidColor.green(pixelAtNewCenter)}, B=${AndroidColor.blue(pixelAtNewCenter)})",
                AndroidColor.blue(pixelAtNewCenter) > 150 && AndroidColor.red(pixelAtNewCenter) < 80 && AndroidColor.green(pixelAtNewCenter) < 80,
            )

        } finally {
            scenario.close()
        }
    }

    @Test
    fun composeHostSourceMappingRefreshesOnViewTranslation() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            var testHostRef: LiquidGlassHostView? = null
            var activityRef: Activity? = null

            scenario.onActivity { activity ->
                activityRef = activity
                val root = activity.findViewById<ViewGroup>(R.id.xmlRoot)

                val backdrop = GlassBackdrop(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(600, 800)
                }
                val redStripe = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(600, 400).apply {
                        topMargin = 0
                    }
                    setBackgroundColor(AndroidColor.RED)
                }
                val blueStripe = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(600, 400).apply {
                        topMargin = 400
                    }
                    setBackgroundColor(AndroidColor.BLUE)
                }
                backdrop.addView(redStripe)
                backdrop.addView(blueStripe)
                root.addView(backdrop)

                // Opaque green barrier covering backdrop ensures test fails if Compose glass rendering is absent
                val barrier = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(600, 800)
                    setBackgroundColor(AndroidColor.GREEN)
                }
                root.addView(barrier)

                val hostView = LiquidGlassHostView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(300, 200).apply {
                        leftMargin = 100
                        topMargin = 100
                    }
                    this.backdropView = backdrop
                }
                hostView.setThemedGlassContent {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .glassEffect(glass = Glass.Clear, shape = RoundedCornerShape(12.dp)),
                    )
                }
                root.addView(hostView)
                testHostRef = hostView
            }

            val activity = activityRef!!
            val hostView = testHostRef!!

            waitForDraw(activity.window.decorView)
            val beforeBitmap = captureWindow(activity)
            assertNotNull(beforeBitmap)

            val location = IntArray(2)
            scenario.onActivity {
                hostView.getLocationInWindow(location)
            }
            val origCenterX = location[0] + hostView.width / 2
            val origCenterY = location[1] + hostView.height / 2
            val pixelBefore = beforeBitmap.getPixel(origCenterX, origCenterY)

            assertTrue(
                "Before translation, compose host over RED stripe must sample high red and low green (was R=${AndroidColor.red(pixelBefore)}, G=${AndroidColor.green(pixelBefore)}, B=${AndroidColor.blue(pixelBefore)})",
                AndroidColor.red(pixelBefore) > 150 && AndroidColor.blue(pixelBefore) < 80 && AndroidColor.green(pixelBefore) < 80,
            )

            // Translate host down by 350px so hostView is now completely over the BLUE stripe (y in 450..650)
            scenario.onActivity {
                hostView.translationY = 350f
            }
            waitForDraw(activity.window.decorView)

            val afterBitmap = captureWindow(activity)
            assertNotNull(afterBitmap)

            val newCenterY = origCenterY + 350
            val pixelAtOldCenter = afterBitmap.getPixel(origCenterX, origCenterY)
            val pixelAtNewCenter = afterBitmap.getPixel(origCenterX, newCenterY)

            // Old center now reveals the green barrier underneath without glass
            assertTrue(
                "Old center must reveal green barrier without Compose glass (was R=${AndroidColor.red(pixelAtOldCenter)}, G=${AndroidColor.green(pixelAtOldCenter)}, B=${AndroidColor.blue(pixelAtOldCenter)})",
                AndroidColor.green(pixelAtOldCenter) > 200 && AndroidColor.red(pixelAtOldCenter) < 80 && AndroidColor.blue(pixelAtOldCenter) < 80,
            )
            assertTrue(
                "After translation, compose host over BLUE stripe must sample high blue and low green (was R=${AndroidColor.red(pixelAtNewCenter)}, G=${AndroidColor.green(pixelAtNewCenter)}, B=${AndroidColor.blue(pixelAtNewCenter)})",
                AndroidColor.blue(pixelAtNewCenter) > 150 && AndroidColor.red(pixelAtNewCenter) < 80 && AndroidColor.green(pixelAtNewCenter) < 80,
            )

        } finally {
            scenario.close()
        }
    }

    @Test
    fun effectDisposalRestoresOriginalBackground() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val backdrop = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)
                val testView = View(activity)
                val originalBg = android.graphics.drawable.ColorDrawable(android.graphics.Color.RED)
                testView.background = originalBg

                val effect = testView.glassEffect(backdrop)
                assertEquals(effect.drawable, testView.background)

                effect.dispose()
                assertEquals(originalBg, testView.background)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun backdropRejectsNestedConsumerInsideWrapperViewGroup() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val backdrop = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)
                val wrapper = FrameLayout(activity)
                val child = View(activity)

                backdrop.registerGlassConsumer(child)
                wrapper.addView(child)

                var rejected = false
                try {
                    backdrop.addView(wrapper)
                } catch (e: IllegalStateException) {
                    rejected = true
                }
                assertTrue("GlassBackdrop must reject wrapper containing a registered consumer", rejected)
                assertEquals("Rejected wrapper must be removed from backdrop", -1, backdrop.indexOfChild(wrapper))
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun backdropAllowsConsumerOfDifferentBackdrop() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val backdrop1 = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)
                val backdrop2 = GlassBackdrop(activity)
                val foreignConsumer = View(activity)
                val effect = foreignConsumer.glassEffect(backdrop2)

                val wrapper = FrameLayout(activity)
                wrapper.addView(foreignConsumer)

                // Must NOT throw because foreignConsumer targets backdrop2, not backdrop1
                backdrop1.addView(wrapper)
                assertTrue(backdrop1.indexOfChild(wrapper) != -1)

                backdrop1.removeView(wrapper)
                effect.dispose()
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun effectDisposalPreservesCustomPadding() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val backdrop = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)
                val testView = View(activity)
                testView.setPadding(12, 14, 16, 18)

                val effect = testView.glassEffect(backdrop)
                assertEquals(12, testView.paddingLeft)
                assertEquals(14, testView.paddingTop)
                assertEquals(16, testView.paddingRight)
                assertEquals(18, testView.paddingBottom)

                effect.dispose()
                assertEquals(12, testView.paddingLeft)
                assertEquals(14, testView.paddingTop)
                assertEquals(16, testView.paddingRight)
                assertEquals(18, testView.paddingBottom)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun backdropRejectsCrossBackdropDependencyCycle() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val backdropA = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)
                val backdropB = GlassBackdrop(activity)

                // Consumer B targets backdrop B, placed in backdrop A
                val consumerB = View(activity)
                consumerB.glassEffect(backdropB)
                backdropA.addView(consumerB)

                // Attempting to place consumer A (targeting backdrop A) into backdrop B must create a cycle and be rejected
                val consumerA = View(activity)
                consumerA.glassEffect(backdropA)

                var cycleRejected = false
                try {
                    backdropB.addView(consumerA)
                } catch (e: IllegalStateException) {
                    cycleRejected = true
                }
                assertTrue("Cross-backdrop dependency cycle A <-> B must be rejected", cycleRejected)
                assertEquals("Rejected cyclical consumer must be removed from backdropB", -1, backdropB.indexOfChild(consumerA))

                backdropA.removeView(consumerB)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun effectPreservesRelativePaddingInRtl() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val backdrop = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)
                val testView = View(activity)
                testView.layoutDirection = View.LAYOUT_DIRECTION_RTL
                testView.setPaddingRelative(20, 10, 40, 30)

                val effect = testView.glassEffect(backdrop)
                assertEquals(20, testView.paddingStart)
                assertEquals(40, testView.paddingEnd)

                effect.dispose()
                assertEquals(20, testView.paddingStart)
                assertEquals(40, testView.paddingEnd)
            }
        } finally {
            scenario.close()
        }
    }
    @Test
    fun backdropRejectsCrossBackdropDependencyCycleOnBindingPath() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val backdropA = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)
                val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
                val backdropB = GlassBackdrop(activity).apply { id = View.generateViewId() }
                val backdropC = GlassBackdrop(activity).apply { id = View.generateViewId() }
                contentRoot.addView(backdropB)
                contentRoot.addView(backdropC)

                // Consumer B targets backdrop B, placed in backdrop A (so A depends on B)
                val consumerB = View(activity)
                consumerB.glassEffect(backdropB)
                backdropA.addView(consumerB)

                // View is already inside backdrop B
                val consumerA = View(activity)
                backdropB.addView(consumerA)

                // Binding consumerA to backdrop A creates cycle (B -> A -> B) and must throw
                var cycleRejected = false
                try {
                    consumerA.glassEffect(backdropA)
                } catch (e: IllegalStateException) {
                    cycleRejected = true
                }
                assertTrue("Binding glassEffect that creates cycle with ancestor backdrop must be rejected", cycleRejected)

                // Also test LiquidGlassHostView inside backdrop B targeting backdrop A
                val hostA = LiquidGlassHostView(activity)
                hostA.backdropView = backdropC
                backdropB.addView(hostA)

                // 1. Failed retarget via backdropView must throw and restore previous resolved/explicit backdrop
                var hostCycleRejected = false
                try {
                    hostA.backdropView = backdropA
                } catch (e: IllegalStateException) {
                    hostCycleRejected = true
                }
                assertTrue("Setting backdropView on host that creates cycle with ancestor backdrop must be rejected", hostCycleRejected)
                assertEquals("Failed retarget via backdropView must restore previous backdrop", backdropC, hostA.backdropView)

                // 2. Failed retarget via backdropViewId must throw and restore previous backdrop
                hostA.backdropViewId = backdropC.id
                hostA.backdropView = null
                assertEquals(backdropC, hostA.backdropView)

                var idCycleRejected = false
                try {
                    hostA.backdropViewId = backdropA.id
                } catch (e: IllegalStateException) {
                    idCycleRejected = true
                }
                assertTrue("Setting backdropViewId on host that creates cycle with ancestor backdrop must be rejected", idCycleRejected)
                assertEquals("Failed retarget via backdropViewId must restore previous id", backdropC.id, hostA.backdropViewId)
                assertEquals("Failed retarget via backdropViewId must restore previous resolved backdrop", backdropC, hostA.backdropView)

                backdropB.removeView(hostA)
                backdropB.removeView(consumerA)
                backdropA.removeView(consumerB)
                contentRoot.removeView(backdropB)
                contentRoot.removeView(backdropC)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun effectPreservesAbsolutePaddingWhenSwitchingToRtl() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val backdrop = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)
                val testView = View(activity)
                // Set asymmetric absolute padding: left=10, top=20, right=30, bottom=40
                testView.setPadding(10, 20, 30, 40)
                assertFalse("Initial padding should not be relative", testView.isPaddingRelative)

                val effect = testView.glassEffect(backdrop)
                assertFalse("isPaddingRelative must remain false after effect installation", testView.isPaddingRelative)
                assertEquals(10, testView.paddingLeft)
                assertEquals(30, testView.paddingRight)

                // Switch to RTL: physical left and right padding must NOT swap
                testView.layoutDirection = View.LAYOUT_DIRECTION_RTL
                assertEquals("paddingLeft must remain 10 in RTL for absolute padding", 10, testView.paddingLeft)
                assertEquals("paddingRight must remain 30 in RTL for absolute padding", 30, testView.paddingRight)

                effect.dispose()
                assertFalse("isPaddingRelative must remain false after disposal", testView.isPaddingRelative)
                assertEquals("paddingLeft must remain 10 after disposal", 10, testView.paddingLeft)
                assertEquals("paddingRight must remain 30 after disposal", 30, testView.paddingRight)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun backdropRejectsSelfAsConsumer() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val backdrop = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)
                val origBg = backdrop.background
                var rejected = false
                try {
                    backdrop.glassEffect(backdrop)
                } catch (e: IllegalStateException) {
                    rejected = true
                    assertTrue(e.message?.contains("cannot be its own consumer") == true)
                }
                assertTrue("Self-targeting backdrop.glassEffect(backdrop) must be rejected", rejected)
                assertEquals("Backdrop background must remain intact upon rejection", origBg, backdrop.background)
                assertNull("Backdrop effect tag must remain null upon rejection", backdrop.getTag(LibR.id.liquid_glass_view_effect_tag))
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun interactiveDisposalAndNonInteractiveRebindClearsTouchListener() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val backdrop = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)
                val view = View(activity)

                val effect1 = view.glassEffect(backdrop, glass = Glass.Regular.interactive(true))
                assertNotNull("Interactive effect must have touchListener installed", effect1.touchListener)

                effect1.dispose()
                assertNull("TouchListener must be null after dispose", effect1.touchListener)

                val effect2 = view.glassEffect(backdrop, glass = Glass.Regular.interactive(false))
                assertNull("Non-interactive rebind must not install touchListener", effect2.touchListener)
                effect2.dispose()
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun consumerRebindingAcrossBackdropsAndMovingUnderPreviousBackdrop() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
                val backdropA = GlassBackdrop(activity)
                val backdropB = GlassBackdrop(activity)
                contentRoot.addView(backdropA)
                contentRoot.addView(backdropB)

                val consumer = LiquidGlassView(activity)
                consumer.backdropView = backdropA
                contentRoot.addView(consumer)

                assertTrue("consumer must be registered in backdropA", backdropA.isRegisteredConsumer(consumer))

                consumer.backdropView = backdropB
                assertFalse("consumer must be unregistered from backdropA after rebind", backdropA.isRegisteredConsumer(consumer))
                assertTrue("consumer must be registered in backdropB after rebind", backdropB.isRegisteredConsumer(consumer))

                contentRoot.removeView(consumer)
                backdropA.addView(consumer)
                assertEquals("consumer must be cleanly added under backdropA", backdropA, consumer.parent)

                backdropA.removeView(consumer)
                contentRoot.removeView(backdropA)
                contentRoot.removeView(backdropB)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun genericOutlineCustomShapeBuildsPathField() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            var activityRef: Activity? = null
            var viewRef: View? = null
            var effectRef: GlassViewEffect? = null

            scenario.onActivity { activity ->
                activityRef = activity
                val root = activity.findViewById<ViewGroup>(R.id.xmlRoot)

                // Setup blue underlay
                val underlay = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(160, 160).apply {
                        leftMargin = 50
                        topMargin = 50
                    }
                    setBackgroundColor(AndroidColor.BLUE)
                }
                // Setup backdrop with patterned content (left half RED, right half GREEN)
                val backdrop = GlassBackdrop(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(160, 160).apply {
                        leftMargin = 50
                        topMargin = 50
                    }
                }
                val patternedContent = LinearLayout(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(160, 160)
                    orientation = LinearLayout.HORIZONTAL
                }
                val redStripe = View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(80, 160)
                    setBackgroundColor(AndroidColor.RED)
                }
                val greenStripe = View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(80, 160)
                    setBackgroundColor(AndroidColor.GREEN)
                }
                patternedContent.addView(redStripe)
                patternedContent.addView(greenStripe)
                backdrop.addView(patternedContent)
                root.addView(backdrop)

                // Blue underlay covers backdrop in the window so clipped areas reveal blue
                root.addView(underlay)

                // Triangle shape: apex at top-center (80, 0), bottom-right (160, 160), bottom-left (0, 160)
                val triangleShape = object : Shape {
                    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width / 2f, 0f)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        return Outline.Generic(path)
                    }
                }

                val glassView = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(160, 160).apply {
                        leftMargin = 50
                        topMargin = 50
                    }
                }
                val effect = glassView.glassEffect(
                    backdrop = backdrop,
                    glass = Glass.Regular,
                    shape = triangleShape,
                )
                root.addView(glassView)

                viewRef = glassView
                effectRef = effect
            }

            val activity = activityRef!!
            val glassView = viewRef!!
            val effect = effectRef!!

            waitForDraw(activity.window.decorView)
            val bitmap = captureWindow(activity)
            assertNotNull(bitmap)

            val loc = IntArray(2)
            scenario.onActivity {
                glassView.getLocationInWindow(loc)
            }
            val vx = loc[0]
            val vy = loc[1]

            // 1. Outside triangle at top-left (vx + 15, vy + 15):
            val pixelOutsideLeft = bitmap.getPixel(vx + 15, vy + 15)
            assertTrue(
                "Outside triangle top-left must show blue underlay (was R=${AndroidColor.red(pixelOutsideLeft)}, B=${AndroidColor.blue(pixelOutsideLeft)})",
                AndroidColor.blue(pixelOutsideLeft) > 180 && AndroidColor.red(pixelOutsideLeft) < 80,
            )

            // 2. Outside triangle at top-right (vx + 145, vy + 15):
            val pixelOutsideRight = bitmap.getPixel(vx + 145, vy + 15)
            assertTrue(
                "Outside triangle top-right must show blue underlay (was R=${AndroidColor.red(pixelOutsideRight)}, B=${AndroidColor.blue(pixelOutsideRight)})",
                AndroidColor.blue(pixelOutsideRight) > 180 && AndroidColor.red(pixelOutsideRight) < 80,
            )

            // 3. Inside triangle on left half (vx + 40, vy + 120):
            // Inside the triangle outline over the RED half of the patterned source.
            val pixelInsideLeft = bitmap.getPixel(vx + 40, vy + 120)
            val rLeft = AndroidColor.red(pixelInsideLeft)
            val gLeft = AndroidColor.green(pixelInsideLeft)
            assertTrue(
                "Inside triangle left half must render refracted red pattern (was R=$rLeft, G=$gLeft)",
                rLeft > 200 && rLeft > gLeft + 40,
            )

            // 4. Inside triangle on right half (vx + 120, vy + 120):
            // Inside the triangle outline over the GREEN half of the patterned source.
            val pixelInsideRight = bitmap.getPixel(vx + 120, vy + 120)
            val rRight = AndroidColor.red(pixelInsideRight)
            val gRight = AndroidColor.green(pixelInsideRight)
            assertTrue(
                "Inside triangle right half must render refracted green pattern (was R=$rRight, G=$gRight)",
                gRight > 180 && gRight > rRight + 40,
            )

            scenario.onActivity {
                effect.dispose()
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun interactiveViewProcessesDownMoveUpCancelGestures() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val backdrop = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)
                val view = LiquidGlassView(activity).apply {
                    isGlassInteractive = true
                    backdropView = backdrop
                }
                activity.findViewById<ViewGroup>(android.R.id.content).addView(view)

                val downEvent = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
                view.dispatchTouchEvent(downEvent)
                assertEquals(1f, view.renderer?.touchAmount ?: 0f, 0.01f)

                val moveEvent = MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_MOVE, 80f, 90f, 0)
                view.dispatchTouchEvent(moveEvent)
                assertEquals(80f, view.renderer?.touchX ?: 0f, 0.01f)
                assertEquals(90f, view.renderer?.touchY ?: 0f, 0.01f)

                val upEvent = MotionEvent.obtain(0L, 20L, MotionEvent.ACTION_UP, 80f, 90f, 0)
                view.dispatchTouchEvent(upEvent)
                assertEquals(0f, view.renderer?.touchAmount ?: 0f, 0.01f)

                val cancelEvent = MotionEvent.obtain(0L, 30L, MotionEvent.ACTION_CANCEL, 80f, 90f, 0)
                view.dispatchTouchEvent(cancelEvent)
                assertEquals(0f, view.renderer?.touchAmount ?: 0f, 0.01f)

                activity.findViewById<ViewGroup>(android.R.id.content).removeView(view)
            }
        } finally {
            scenario.close()
        }
    }
    @Test
    fun unownedApplicationTouchListenerPreservedOnEffectDispose() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val backdrop = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)
                val view = View(activity)
                var appTouchCount = 0
                view.setOnTouchListener { _, _ ->
                    appTouchCount++
                    true
                }

                // Apply default non-interactive glass effect
                val effect1 = view.glassEffect(backdrop, glass = Glass.Regular)
                assertNull("Non-interactive glass must not install a touchListener", effect1.touchListener)

                // Dispose effect1: application's touch listener must remain intact
                effect1.dispose()

                val down1 = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
                view.dispatchTouchEvent(down1)
                assertEquals("Application touch listener must receive event after non-interactive dispose", 1, appTouchCount)

                // Reapply glass effect: application's touch listener must still remain intact
                val effect2 = view.glassEffect(backdrop, glass = Glass.Regular)
                val down2 = MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
                view.dispatchTouchEvent(down2)
                assertEquals("Application touch listener must receive event after rebind", 2, appTouchCount)

                effect2.dispose()
                val down3 = MotionEvent.obtain(0L, 20L, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
                view.dispatchTouchEvent(down3)
                assertEquals("Application touch listener must receive event after second dispose", 3, appTouchCount)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun applicationReplacementTouchListenerPreservedOnInteractiveEffectDispose() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val backdrop = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)
                val view = View(activity)

                // Apply interactive glass effect
                val effect = view.glassEffect(backdrop, glass = Glass.Regular.interactive(true))
                assertNotNull("Interactive glass must install touchListener", effect.touchListener)

                // Application installs a replacement listener over interactive view
                var replacementTouchCount = 0
                view.setOnTouchListener { _, _ ->
                    replacementTouchCount++
                    true
                }

                // Dispose interactive effect: replacement listener must NOT be cleared by dispose()
                effect.dispose()

                val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
                view.dispatchTouchEvent(down)
                assertEquals("Replacement application touch listener must receive event after interactive dispose", 1, replacementTouchCount)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun viewGlassEffectGestureHandlingOnNonClickableAndClickableViews() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(R.id.xmlRoot)
                val backdrop = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)

                // 1. Non-clickable view with interactive glass effect
                val nonClickableView = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(200, 100)
                    isClickable = false
                    isLongClickable = false
                }
                val effect = nonClickableView.glassEffect(
                    backdrop = backdrop,
                    glass = Glass.Regular.interactive(true),
                )
                root.addView(nonClickableView)

                val r = effect.renderer
                assertEquals("Initially touchAmount must be 0", 0f, r.touchAmount)

                val now = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
                val move = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_MOVE, 60f, 60f, 0)
                val up = MotionEvent.obtain(now, now + 100, MotionEvent.ACTION_UP, 60f, 60f, 0)

                // On non-clickable view, touchListener must consume DOWN and track through UP
                val downConsumed = nonClickableView.dispatchTouchEvent(down)
                assertTrue("Non-clickable view with interactive glass must consume ACTION_DOWN", downConsumed)
                assertEquals("touchAmount must be 1f on DOWN", 1f, r.touchAmount)

                nonClickableView.dispatchTouchEvent(move)
                assertEquals("touchAmount must remain 1f on MOVE", 1f, r.touchAmount)

                nonClickableView.dispatchTouchEvent(up)
                assertEquals("touchAmount must reset to 0f on UP (no permanent glow lockout)", 0f, r.touchAmount)

                down.recycle()
                move.recycle()
                up.recycle()

                effect.dispose()
                root.removeView(nonClickableView)

                // 2. Clickable view preserves click listener
                val clicked = AtomicBoolean(false)
                val clickableView = TextView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(200, 100)
                    isClickable = true
                    setOnClickListener { clicked.set(true) }
                }
                val clickableEffect = clickableView.glassEffect(
                    backdrop = backdrop,
                    glass = Glass.Regular.interactive(true),
                )
                root.addView(clickableView)

                clickableView.performClick()
                assertTrue("Click listener must fire on clickable view with interactive glass", clicked.get())
                assertEquals("touchAmount must reset to 0f after click", 0f, clickableEffect.renderer.touchAmount)

                clickableEffect.dispose()
                root.removeView(clickableView)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun wideKernelEffectSmallSigmaHardwareRenderingDoesNotCrash() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            var activityRef: Activity? = null
            var viewRef: View? = null
            var effectRef: GlassViewEffect? = null

            scenario.onActivity { activity ->
                activityRef = activity
                val root = activity.findViewById<ViewGroup>(R.id.xmlRoot)
                val backdrop = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)

                // Style with small wideKernel that produces radius <= 0f
                val smallWideGlass = Glass(GlassStyle.Regular.copy(wideKernel = 0.5.dp))

                val testView = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(200, 200).apply {
                        leftMargin = 100
                        topMargin = 100
                    }
                }
                val effect = testView.glassEffect(
                    backdrop = backdrop,
                    glass = smallWideGlass,
                )
                root.addView(testView)
                viewRef = testView
                effectRef = effect
            }

            val activity = activityRef!!
            val testView = viewRef!!
            val effect = effectRef!!

            // Must render cleanly on hardware window with PixelCopy.SUCCESS without crashing
            waitForDraw(activity.window.decorView)
            val bmp = captureWindow(activity)
            assertNotNull("Window capture with small wideKernel must succeed without crashing", bmp)

            scenario.onActivity {
                effect.dispose()
                (testView.parent as? ViewGroup)?.removeView(testView)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun rejectBackdropDescendantOfConsumer() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(R.id.xmlRoot)

                val container = FrameLayout(activity)
                val childBackdrop = GlassBackdrop(activity)
                container.addView(childBackdrop)
                root.addView(container)

                var rejected = false
                try {
                    container.glassEffect(childBackdrop)
                } catch (e: IllegalStateException) {
                    rejected = true
                    assertTrue(
                        "Must reject backdrop that is a descendant of its consumer",
                        e.message!!.contains("cannot be a descendant of its consumer")
                    )
                }
                assertTrue("Targeting child backdrop inside consumer must throw IllegalStateException", rejected)

                root.removeView(container)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun backdropContentDrawInvalidatesRegisteredConsumers() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            var activityRef: Activity? = null
            var backdropRef: GlassBackdrop? = null
            var consumerRef: View? = null
            var effectRef: GlassViewEffect? = null
            val consumerInvalidated = AtomicBoolean(false)

            scenario.onActivity { activity ->
                activityRef = activity
                val root = activity.findViewById<ViewGroup>(R.id.xmlRoot)
                val backdrop = GlassBackdrop(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(300, 300)
                }
                val content = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(300, 300)
                    setBackgroundColor(AndroidColor.RED)
                }
                backdrop.addView(content)
                root.addView(backdrop)
                backdropRef = backdrop

                val consumer = object : View(activity) {
                    override fun invalidate() {
                        super.invalidate()
                        consumerInvalidated.set(true)
                    }
                }.apply {
                    layoutParams = FrameLayout.LayoutParams(200, 200)
                }
                val effect = consumer.glassEffect(backdrop = backdrop)
                root.addView(consumer)
                consumerRef = consumer
                effectRef = effect
            }

            val activity = activityRef!!
            val backdrop = backdropRef!!
            val consumer = consumerRef!!
            val effect = effectRef!!

            waitForDraw(activity.window.decorView)

            scenario.onActivity {
                consumerInvalidated.set(false)
                // Invalidate backdrop to trigger re-recording
                backdrop.invalidate()
            }

            waitForDraw(activity.window.decorView)
            assertTrue("Backdrop draw must notify and invalidate registered consumers", consumerInvalidated.get())

            scenario.onActivity {
                effect.dispose()
                val root = activity.findViewById<ViewGroup>(R.id.xmlRoot)
                root.removeView(consumer)
                root.removeView(backdrop)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun multiCyclePaddingPreservedAcrossRebinds() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val backdrop = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)
                val testView = TextView(activity).apply {
                    setPadding(24, 12, 36, 18)
                }

                // Initial padding
                assertEquals(24, testView.paddingLeft)
                assertEquals(12, testView.paddingTop)
                assertEquals(36, testView.paddingRight)
                assertEquals(18, testView.paddingBottom)

                // Rebind 5 times in a loop
                for (i in 1..5) {
                    val effect = testView.glassEffect(backdrop = backdrop)
                    assertEquals("Padding left must not shrink on iteration $i", 24, testView.paddingLeft)
                    assertEquals("Padding top must not shrink on iteration $i", 12, testView.paddingTop)
                    assertEquals("Padding right must not shrink on iteration $i", 36, testView.paddingRight)
                    assertEquals("Padding bottom must not shrink on iteration $i", 18, testView.paddingBottom)
                    effect.dispose()
                    assertEquals("Padding left must remain 24 after dispose on iteration $i", 24, testView.paddingLeft)
                    assertEquals("Padding top must remain 12 after dispose on iteration $i", 12, testView.paddingTop)
                    assertEquals("Padding right must remain 36 after dispose on iteration $i", 36, testView.paddingRight)
                    assertEquals("Padding bottom must remain 18 after dispose on iteration $i", 18, testView.paddingBottom)
                }
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun backdropResizeInvalidatesConsumerGeometry() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            var activityRef: Activity? = null
            var backdropRef: GlassBackdrop? = null
            var effectRef: GlassViewEffect? = null
            var consumerRef: View? = null

            scenario.onActivity { activity ->
                activityRef = activity
                val root = activity.findViewById<ViewGroup>(R.id.xmlRoot)
                val backdrop = GlassBackdrop(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(300, 200)
                }
                root.addView(backdrop)
                backdropRef = backdrop

                val consumer = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(150, 100)
                }
                val effect = consumer.glassEffect(backdrop = backdrop)
                root.addView(consumer)
                consumerRef = consumer
                effectRef = effect
            }

            val activity = activityRef!!
            val backdrop = backdropRef!!
            val effect = effectRef!!
            val consumer = consumerRef!!

            waitForDraw(activity.window.decorView)

            scenario.onActivity {
                // Resize backdrop
                backdrop.layoutParams = FrameLayout.LayoutParams(450, 350)
                backdrop.requestLayout()
            }

            waitForDraw(activity.window.decorView)

            scenario.onActivity {
                assertEquals(450, backdrop.width)
                assertEquals(350, backdrop.height)
                effect.dispose()
                val root = activity.findViewById<ViewGroup>(R.id.xmlRoot)
                root.removeView(consumer)
                root.removeView(backdrop)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun dynamicEffectGlassMutationWiresTouchListener() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val backdrop = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)
                val testView = TextView(activity)

                // Non-interactive glass
                val effect = testView.glassEffect(
                    backdrop = backdrop,
                    glass = Glass.Regular,
                )
                assertNull("Non-interactive glass must not install touchListener", effect.touchListener)

                // Mutate to interactive
                effect.glass = Glass.Regular.interactive(true)
                assertNotNull("Mutating to interactive must install touchListener", effect.touchListener)
                assertFalse("Glass must not change application clickability", testView.isClickable)

                // Mutate back to non-interactive
                effect.glass = Glass.Regular
                assertNull("Mutating to non-interactive must clear touchListener", effect.touchListener)

                effect.dispose()
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun clickabilityRestoredOnDisablingInteractionAndDisposal() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(R.id.xmlRoot)
                val backdrop = GlassBackdrop(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(200, 200)
                }
                root.addView(backdrop)

                val container = FrameLayout(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(200, 200)
                }
                val child = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(100, 100)
                }
                container.addView(child)
                root.addView(container)

                container.measure(
                    View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
                )
                container.layout(0, 0, 200, 200)

                // 1. Before installation: child is not clickable, parent does not consume gesture
                assertFalse("Child must start non-clickable", child.isClickable)
                val down1 = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
                val handledBefore = container.dispatchTouchEvent(down1)
                down1.recycle()
                assertFalse("Before effect, container must not consume touch for non-clickable child", handledBefore)

                // 2. During interaction: child becomes clickable owned by effect, gesture is consumed
                val effect = child.glassEffect(backdrop = backdrop, glass = Glass.Regular.interactive())
                assertFalse("Interactive glass must preserve application clickability", child.isClickable)
                val down2 = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
                val handledDuring = container.dispatchTouchEvent(down2)
                down2.recycle()
                assertTrue("During interaction, child must consume touch through container", handledDuring)
                val up2 = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, 50f, 50f, 0)
                container.dispatchTouchEvent(up2)
                up2.recycle()

                // 3. After disabling: child clickability is restored to false, touch passes through container
                effect.glass = Glass.Regular
                assertFalse("Child must restore isClickable=false when interaction is disabled", child.isClickable)
                val down3 = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
                val handledAfterDisable = container.dispatchTouchEvent(down3)
                down3.recycle()
                assertFalse("After disabling, container must not consume touch for child", handledAfterDisable)

                // 4. After re-enabling: child becomes clickable again
                effect.glass = Glass.Regular.interactive()
                assertFalse("Re-enabling glass must preserve application clickability", child.isClickable)
                val down4 = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
                val handledReenable = container.dispatchTouchEvent(down4)
                down4.recycle()
                assertTrue("After re-enabling, child must consume touch through container", handledReenable)
                val up4 = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, 50f, 50f, 0)
                container.dispatchTouchEvent(up4)
                up4.recycle()

                // 5. After disposal: child clickability is restored to false
                effect.dispose()
                assertFalse("Child must restore isClickable=false on disposal", child.isClickable)
                val down5 = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
                val handledAfterDispose = container.dispatchTouchEvent(down5)
                down5.recycle()
                assertFalse("After disposal, container must not consume touch for child", handledAfterDispose)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun interactionDisabledDuringPressClearsTouchStateAndDoesNotStaleGlow() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            var activityRef: Activity? = null
            var viewRef: View? = null
            var effectRef: GlassViewEffect? = null
            var rendererRef: GlassNativeRenderer? = null

            scenario.onActivity { activity ->
                activityRef = activity
                val root = activity.findViewById<ViewGroup>(R.id.xmlRoot)
                val backdrop = GlassBackdrop(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(300, 300).apply {
                        leftMargin = 50
                        topMargin = 50
                    }
                }
                val content = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(300, 300)
                    setBackgroundColor(AndroidColor.RED)
                }
                backdrop.addView(content)
                root.addView(backdrop)

                val glassView = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(200, 200).apply {
                        leftMargin = 50
                        topMargin = 50
                    }
                }
                val effect = glassView.glassEffect(backdrop = backdrop, glass = Glass.Regular.interactive())
                root.addView(glassView)

                activityRef = activity
                viewRef = glassView
                effectRef = effect
                rendererRef = effect.renderer
            }

            val activity = activityRef!!
            val glassView = viewRef!!
            val effect = effectRef!!
            val renderer = rendererRef!!

            waitForDraw(activity.window.decorView)
            val unpressed = captureWindow(activity)
            val sampleLocation = IntArray(2)
            scenario.onActivity { glassView.getLocationInWindow(sampleLocation) }
            val sampleX = sampleLocation[0] + glassView.width / 2
            val sampleY = sampleLocation[1] + glassView.height / 2
            val unpressedPixel = unpressed.getPixel(sampleX, sampleY)

            // 1. Initial state: touchAmount = 0
            assertEquals(0f, renderer.touchAmount, 0.001f)

            // 2. Dispatch ACTION_DOWN on the view
            val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
            scenario.onActivity {
                glassView.dispatchTouchEvent(down)
            }
            down.recycle()
            assertEquals(1f, renderer.touchAmount, 0.001f)
            assertEquals(100f, renderer.touchX, 0.1f)
            assertEquals(100f, renderer.touchY, 0.1f)

            // 3. Disable interaction while finger is DOWN
            scenario.onActivity {
                effect.glass = Glass.Regular
            }
            assertEquals("touchAmount must be reset to 0 when interaction is disabled", 0f, renderer.touchAmount, 0.001f)
            assertEquals(-1e5f, renderer.touchX, 1f)

            // 4. Dispatch ACTION_UP while interaction is disabled
            val up = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, 100f, 100f, 0)
            scenario.onActivity {
                glassView.dispatchTouchEvent(up)
            }
            up.recycle()
            assertEquals(0f, renderer.touchAmount, 0.001f)

            // 5. Re-enable interaction
            scenario.onActivity {
                effect.glass = Glass.Regular.interactive()
            }
            assertEquals("touchAmount must remain 0 after re-enabling with no active gesture", 0f, renderer.touchAmount, 0.001f)
            assertEquals(-1e5f, renderer.touchX, 1f)

            // Compare the restored material to its pre-gesture output, not bitmap existence.
            waitForDraw(activity.window.decorView)
            val bitmap = captureWindow(activity)
            assertEquals("Re-enabled material must match the unpressed frame", unpressedPixel,
                bitmap.getPixel(sampleX, sampleY))

            scenario.onActivity {
                effect.dispose()
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun drawableAlphaControlsCompositingOpacityOnHardwareWindow() {
        val scenario = ActivityScenario.launch(XmlInteropActivity::class.java)
        try {
            var activityRef: Activity? = null
            var viewRef: View? = null
            var effectRef: GlassViewEffect? = null

            scenario.onActivity { activity ->
                activityRef = activity
                val root = activity.findViewById<ViewGroup>(R.id.xmlRoot)

                // 1. Backdrop sampling red content
                val backdrop = GlassBackdrop(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(300, 300).apply {
                        leftMargin = 50
                        topMargin = 50
                    }
                }
                val redContent = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(300, 300)
                    setBackgroundColor(AndroidColor.RED)
                }
                backdrop.addView(redContent)
                root.addView(backdrop)

                // 2. Opaque green barrier directly under the consumer in the window
                val greenBarrier = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(200, 200).apply {
                        leftMargin = 50
                        topMargin = 50
                    }
                    setBackgroundColor(AndroidColor.GREEN)
                }
                root.addView(greenBarrier)

                // 3. Glass consumer view positioned over the green barrier
                val glassView = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(200, 200).apply {
                        leftMargin = 50
                        topMargin = 50
                    }
                }
                val effect = glassView.glassEffect(
                    backdrop = backdrop,
                    glass = Glass.Regular,
                )
                root.addView(glassView)

                viewRef = glassView
                effectRef = effect
            }

            val activity = activityRef!!
            val glassView = viewRef!!
            val effect = effectRef!!
            val drawable = effect.drawable

            val loc = IntArray(2)
            scenario.onActivity {
                glassView.getLocationInWindow(loc)
            }
            val cx = loc[0] + 100
            val cy = loc[1] + 100

            // Phase A: alpha = 255 (Full opacity -> Opaque red glass covers green barrier)
            scenario.onActivity {
                drawable.alpha = 255
            }
            waitForDraw(activity.window.decorView)
            val bm255 = captureWindow(activity)
            assertNotNull(bm255)
            val p255 = bm255.getPixel(cx, cy)
            val r255 = AndroidColor.red(p255)
            val g255 = AndroidColor.green(p255)
            assertNotEquals("Opaque material must cover the green underlay", AndroidColor.GREEN, p255)

            // Phase B: alpha = 128 (~50% alpha -> Compositing blends red glass over green barrier)
            scenario.onActivity {
                drawable.alpha = 128
            }
            waitForDraw(activity.window.decorView)
            val bm128 = captureWindow(activity)
            assertNotNull(bm128)
            val p128 = bm128.getPixel(cx, cy)
            val r128 = AndroidColor.red(p128)
            val g128 = AndroidColor.green(p128)
            for (shift in listOf(0, 8, 16)) {
                val full = (p255 shr shift) and 255
                val under = (AndroidColor.GREEN shr shift) and 255
                val expected = (full * 128 + under * 127) / 255
                val actual = (p128 shr shift) and 255
                assertTrue("Hardware half alpha must composite once: $actual vs $expected",
                    kotlin.math.abs(actual - expected) <= 3)
            }

            // Phase C: alpha = 0 (Full transparent -> Glass returns early, only green barrier shows)
            scenario.onActivity {
                drawable.alpha = 0
            }
            waitForDraw(activity.window.decorView)
            val bm0 = captureWindow(activity)
            assertNotNull(bm0)
            val p0 = bm0.getPixel(cx, cy)
            val r0 = AndroidColor.red(p0)
            val g0 = AndroidColor.green(p0)
            assertTrue("At alpha=0, glass is fully transparent showing green barrier (was R=$r0, G=$g0)", g0 > 200 && r0 < 80)

            // Phase D: Fallback path assertion (software canvas at 255, 128, 0)
            val swBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            val swCanvas = Canvas(swBitmap)
            drawable.setBounds(0, 0, 200, 200)

            // At alpha 255 on fallback
            swBitmap.eraseColor(AndroidColor.GREEN)
            drawable.alpha = 255
            drawable.draw(swCanvas)
            val swP255 = swBitmap.getPixel(100, 100)
            assertNotEquals("Fallback at alpha=255 must draw content over green background", AndroidColor.GREEN, swP255)

            swBitmap.eraseColor(AndroidColor.GREEN)
            drawable.alpha = 128
            drawable.draw(swCanvas)
            val swHalf = swBitmap.getPixel(100, 100)
            for (shift in listOf(0, 8, 16)) {
                val full = (swP255 shr shift) and 255
                val under = (AndroidColor.GREEN shr shift) and 255
                val expected = (full * 128 + under * 127) / 255
                val actual = (swHalf shr shift) and 255
                assertTrue("Fallback half alpha must composite once: $actual vs $expected",
                    kotlin.math.abs(actual - expected) <= 2)
            }

            // At alpha 0 on fallback
            swBitmap.eraseColor(AndroidColor.GREEN)
            drawable.alpha = 0
            drawable.draw(swCanvas)
            val swP0 = swBitmap.getPixel(100, 100)
            assertEquals("Fallback at alpha=0 must not draw", AndroidColor.GREEN, swP0)

            scenario.onActivity {
                effect.dispose()
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun applicationClickInstalledAfterGlassSurvivesDisableAndDispose() {
        val clickCount = java.util.concurrent.atomic.AtomicInteger()
        ActivityScenario.launch(XmlInteropActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val source = activity.findViewById<GlassBackdrop>(R.id.xmlBackdrop)
                val parent = FrameLayout(activity)
                val child = View(activity)
                parent.addView(child, FrameLayout.LayoutParams(100, 100))
                activity.findViewById<ViewGroup>(R.id.xmlRoot).addView(parent,
                    FrameLayout.LayoutParams(100, 100))
                parent.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY))
                parent.layout(0, 0, 100, 100)
                val effect = child.glassEffect(source, Glass.Regular.interactive())
                child.setOnClickListener { clickCount.incrementAndGet() }
                fun tap() {
                    val now = SystemClock.uptimeMillis()
                    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                        val event = MotionEvent.obtain(now, now, action, 50f, 50f, 0)
                        parent.dispatchTouchEvent(event)
                        event.recycle()
                    }
                }
                effect.glass = Glass.Regular
                assertTrue(child.isClickable)
                tap()
                effect.dispose()
                assertTrue(child.isClickable)
                tap()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(2, clickCount.get())
        }
    }
    @Test
    fun nativeAndComposeRegularMatchOnStripedBackdrop() {
        ActivityScenario.launch(XmlInteropActivity::class.java).use { scenario ->
            lateinit var activity: Activity
            lateinit var native: View
            lateinit var host: LiquidGlassHostView
            scenario.onActivity { a ->
                activity = a
                val root = a.findViewById<ViewGroup>(R.id.xmlRoot)
                val source = GlassBackdrop(a)
                source.addView(object : View(a) {
                    private val paint = android.graphics.Paint()
                    override fun onDraw(canvas: Canvas) {
                        for (x in 0 until width step 16) {
                            paint.color = if ((x / 16) % 2 == 0) AndroidColor.rgb(35, 70, 110)
                                else AndroidColor.rgb(210, 160, 90)
                            canvas.drawRect(x.toFloat(), 0f, (x + 16).toFloat(), height.toFloat(), paint)
                        }
                    }
                }, FrameLayout.LayoutParams(600, 600))
                root.addView(source, FrameLayout.LayoutParams(600, 600))
                root.addView(View(a).apply { setBackgroundColor(AndroidColor.GREEN) },
                    FrameLayout.LayoutParams(600, 600))
                native = View(a)
                native.glassEffect(source, Glass.Regular, RoundedCornerShape(24.dp), dark = false,
                    backgroundColor = Color.White)
                root.addView(native, FrameLayout.LayoutParams(300, 200).apply {
                    leftMargin = 100; topMargin = 100
                })
                host = LiquidGlassHostView(a).apply {
                    backdropView = source
                    darkMode = GlassXmlDarkMode.Light
                    glassBackgroundColor = Color.White
                    visibility = View.INVISIBLE
                }
                host.setThemedGlassContent {
                    Box(Modifier.fillMaxSize().glassEffect(Glass.Regular, RoundedCornerShape(24.dp)))
                }
                root.addView(host, FrameLayout.LayoutParams(300, 200).apply {
                    leftMargin = 100; topMargin = 100
                })
            }
            waitForDraw(activity.window.decorView)
            val nativePixels = captureWindow(activity)
            val loc = IntArray(2)
            scenario.onActivity {
                native.getLocationInWindow(loc)
                native.visibility = View.INVISIBLE
                host.visibility = View.VISIBLE
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            waitForDraw(activity.window.decorView)
            val composePixels = captureWindow(activity)
            var error = 0L
            var count = 0
            // Interior comparison avoids exterior contact shadow, which is a separate layer in Compose.
            for (y in 70 until 130) for (x in 70 until 230) {
                val n = nativePixels.getPixel(loc[0] + x, loc[1] + y)
                val c = composePixels.getPixel(loc[0] + x, loc[1] + y)
                for (shift in listOf(0, 8, 16)) {
                    error += kotlin.math.abs(((n shr shift) and 255) - ((c shr shift) and 255))
                    count++
                }
            }
            val meanError = error.toDouble() / count
            for ((name, bitmap) in listOf("native" to nativePixels, "compose" to composePixels)) {
                java.io.File(activity.getExternalFilesDir(null), "parity-$name.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                putString("stream", "Native/Compose Regular interior RGB MAE=$meanError; samples=$count\n")
            })
            assertTrue("Native/Compose Regular interior RGB MAE=$meanError (8-bit), expected <= 3", meanError <= 3.0)
        }
    }

    @Test
    fun scrollingSourceRefreshesAllThreeStationaryConsumers() {
        ActivityScenario.launch(XmlInteropActivity::class.java).use { scenario ->
            lateinit var activity: Activity
            lateinit var scroll: android.widget.ScrollView
            val consumers = mutableListOf<View>()
            scenario.onActivity { a ->
                activity = a
                val root = a.findViewById<ViewGroup>(R.id.xmlRoot)
                val source = GlassBackdrop(a)
                scroll = android.widget.ScrollView(a)
                val stripes = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
                for (color in listOf(AndroidColor.RED, AndroidColor.BLUE)) {
                    stripes.addView(View(a).apply { setBackgroundColor(color) },
                        LinearLayout.LayoutParams(900, 600))
                }
                scroll.addView(stripes)
                source.addView(scroll, FrameLayout.LayoutParams(900, 400))
                root.addView(source, FrameLayout.LayoutParams(900, 400))
                root.addView(View(a).apply { setBackgroundColor(AndroidColor.GREEN) },
                    FrameLayout.LayoutParams(900, 400))
                consumers += View(a).apply { glassEffect(source, Glass.Clear) }
                consumers += LiquidGlassView(a).apply {
                    backdropView = source; preset = GlassXmlPreset.Clear
                }
                consumers += LiquidGlassHostView(a).apply {
                    backdropView = source
                    setThemedGlassContent { Box(Modifier.fillMaxSize().glassEffect(Glass.Clear)) }
                }
                consumers.forEachIndexed { index, v ->
                    root.addView(v, FrameLayout.LayoutParams(200, 100).apply {
                        leftMargin = 30 + index * 250; topMargin = 100
                    })
                }
            }
            fun assertSourceColor(blue: Boolean) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                waitForDraw(activity.window.decorView)
                val bitmap = captureWindow(activity)
                scenario.onActivity {
                    consumers.forEachIndexed { index, v ->
                        val loc = IntArray(2); v.getLocationInWindow(loc)
                        val pixel = bitmap.getPixel(loc[0] + v.width / 2, loc[1] + v.height / 2)
                        val wanted = if (blue) AndroidColor.blue(pixel) else AndroidColor.red(pixel)
                        val unwanted = if (blue) AndroidColor.red(pixel) else AndroidColor.blue(pixel)
                        assertTrue("Consumer $index must show ${if (blue) "blue" else "red"} source, pixel=$pixel",
                            wanted > 150 && unwanted < 80 && AndroidColor.green(pixel) < 80)
                    }
                }
            }
            assertSourceColor(false)
            scenario.onActivity { scroll.scrollTo(0, 650) }
            assertSourceColor(true)
            scenario.onActivity { scroll.scrollTo(0, 0) }
            assertSourceColor(false)
            // Child-only invalidation must refresh stationary consumers without moving the source.
            scenario.onActivity {
                (scroll.getChildAt(0) as ViewGroup).getChildAt(0).setBackgroundColor(AndroidColor.BLUE)
            }
            assertSourceColor(true)
            scenario.onActivity {
                consumers.forEach { v ->
                    val parent = v.parent as ViewGroup
                    val params = v.layoutParams
                    parent.removeView(v)
                    parent.addView(v, params)
                }
            }
            assertSourceColor(true)
            scenario.onActivity {
                (scroll.getChildAt(0) as ViewGroup).getChildAt(0).setBackgroundColor(AndroidColor.RED)
            }
            assertSourceColor(false)
        }
    }

}

package com.wexpa.liquidglass

import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified

/**
 * Controller for an active glass effect attached to an Android [View] via [View.glassEffect].
 */
class GlassViewEffect internal constructor(
    val view: View,
    val renderer: GlassNativeRenderer,
    val drawable: GlassBackgroundDrawable,
    val backdrop: GlassBackdrop,
) {
    internal var isDisposed = false
        private set

    internal var previousBackground: android.graphics.drawable.Drawable? = null
    internal var attachListener: View.OnAttachStateChangeListener? = null
    internal var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    var touchListener: View.OnTouchListener? = null
        internal set

    private val lastMatrix = android.graphics.Matrix()
    private val currentMatrix = android.graphics.Matrix()
    private var lastBackdropWidth: Int = -1
    private var lastBackdropHeight: Int = -1

    var glass: Glass
        get() = renderer.glass
        set(value) {
            val wasInteractive = renderer.glass.isInteractive
            renderer.glass = value
            if (value.isInteractive && !wasInteractive) {
                renderer.resetInteraction()
                val tl = GlassTouchListener(this, renderer)
                touchListener = tl
                view.setOnTouchListener(tl)
            } else if (!value.isInteractive && wasInteractive) {
                (touchListener as? GlassTouchListener)?.clear()
                touchListener = null
                renderer.resetInteraction()
            }
            view.invalidate()
        }

    var shape: Shape
        get() = renderer.shape
        set(value) {
            renderer.shape = value
            view.invalidate()
        }

    var dark: Boolean
        get() = renderer.dark
        set(value) {
            renderer.dark = value
            view.invalidate()
        }

    var backgroundColor: Color
        get() = renderer.backgroundColor
        set(value) {
            renderer.backgroundColor = value
            view.invalidate()
        }

    fun update() {
        view.invalidate()
    }

    /**
     * Deterministically disposes this effect, detaching observers and registrations.
     */
    fun dispose() {
        if (isDisposed) return
        isDisposed = true

        attachListener?.let { view.removeOnAttachStateChangeListener(it) }
        attachListener = null

        val vto = view.viewTreeObserver
        preDrawListener?.let {
            if (vto.isAlive) {
                vto.removeOnPreDrawListener(it)
            }
        }
        preDrawListener = null

        (touchListener as? GlassTouchListener)?.clear()
        touchListener = null
        renderer.resetInteraction()

        if (view.background === drawable) {
            val pad = getRawPadding(view)
            view.background = previousBackground
            pad.applyTo(view)
        }
        previousBackground = null

        backdrop.unregisterGlassConsumer(view)
        renderer.discard()

        if (view.getTag(R.id.liquid_glass_view_effect_tag) === this) {
            view.setTag(R.id.liquid_glass_view_effect_tag, null)
        }
    }

    internal fun checkGeometryChanged(): Boolean {
        val bd = backdrop
        if (bd.isAttachedToWindow && view.isAttachedToWindow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bw = bd.width
            val bh = bd.height
            val sizeChanged = bw != lastBackdropWidth || bh != lastBackdropHeight
            if (sizeChanged) {
                lastBackdropWidth = bw
                lastBackdropHeight = bh
            }
            currentMatrix.reset()
            bd.transformMatrixToGlobal(currentMatrix)
            view.transformMatrixToLocal(currentMatrix)
            val matrixChanged = currentMatrix != lastMatrix
            if (matrixChanged) {
                lastMatrix.set(currentMatrix)
            }
            if (sizeChanged || matrixChanged) {
                return true
            }
        }
        return false
    }
}

/**
 * Custom [Drawable] that renders the Liquid Glass effect using [GlassNativeRenderer].
 */
class GlassBackgroundDrawable(
    val renderer: GlassNativeRenderer,
) : Drawable() {
    private var alphaValue: Int = 255

    override fun draw(canvas: Canvas) {
        val b = bounds
        renderer.alphaMultiplier = alphaValue / 255f
        renderer.draw(canvas, b.width(), b.height())
    }

    override fun setAlpha(alpha: Int) {
        if (alphaValue != alpha) {
            alphaValue = alpha.coerceIn(0, 255)
            invalidateSelf()
        }
    }

    override fun getAlpha(): Int = alphaValue

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // Color filter handled by material/renderer
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Convenience API to apply a live Liquid Glass effect behind any Android [View] sampling from [backdrop].
 *
 * Traditional non-glass content and XML layouts execute Android's full native drawing and touch pipeline.
 * The glass effect renders natively via [GlassNativeRenderer] using hardware-accelerated RenderNodes and
 * Android 13+ RuntimeShaders.
 *
 * @param backdrop The [GlassBackdrop] container holding the content to refract.
 * @param glass The material style preset (default: [Glass.Regular]).
 * @param shape The outline shape for the glass panel (default: [CapsuleShape]).
 * @param dark Whether to force dark appearance, light appearance, or null to follow system dark mode.
 * @param backgroundColor Optional ground color behind the glass.
 * @param replaceBackground When true (default), sets the generated [GlassBackgroundDrawable] as the view's background.
 *
 * Note: If [glass] is interactive ([Glass.isInteractive]), this function installs a [View.OnTouchListener]
 * on the view to drive interaction state. Clickable/long-clickable views retain native dispatch;
 * decorative views consume the gesture through the listener without changing their clickability.
 * This takes ownership of the view's [View.setOnTouchListener] slot. Disposal makes that listener
 * inert without clearing a replacement installed by the application.
 */
fun View.glassEffect(
    backdrop: GlassBackdrop,
    glass: Glass = Glass.Regular,
    shape: Shape = CapsuleShape,
    dark: Boolean? = null,
    backgroundColor: Color? = null,
    replaceBackground: Boolean = true,
): GlassViewEffect {
    checkNoCycleWithAncestors(this, backdrop)

    // Finding 11: Release previous attachment when glassEffect is reapplied
    (getTag(R.id.liquid_glass_view_effect_tag) as? GlassViewEffect)?.dispose()

    val isDark = dark ?: run {
        val nightBits = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        nightBits == Configuration.UI_MODE_NIGHT_YES
    }
    val bg = backgroundColor?.takeIf { it.isSpecified } ?: if (isDark) Color.Black else Color.White

    val renderer = GlassNativeRenderer(
        targetView = this,
        backdrop = backdrop,
        glass = glass,
        shape = shape,
        dark = isDark,
        backgroundColor = bg,
    )
    val drawable = GlassBackgroundDrawable(renderer)
    val effect = GlassViewEffect(this, renderer, drawable, backdrop)
    setTag(R.id.liquid_glass_view_effect_tag, effect)

    if (replaceBackground) {
        effect.previousBackground = background
        val pad = getRawPadding(this)
        background = drawable
        pad.applyTo(this)
    }

    val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            checkNoCycleWithAncestors(v, backdrop)
            backdrop.registerGlassConsumer(v)
            val vto = v.viewTreeObserver
            effect.preDrawListener?.let { l ->
                if (vto.isAlive) vto.addOnPreDrawListener(l)
            }
        }

        override fun onViewDetachedFromWindow(v: View) {
            backdrop.unregisterGlassConsumer(v)
            renderer.resetInteraction()
            val vto = v.viewTreeObserver
            effect.preDrawListener?.let { l ->
                if (vto.isAlive) vto.removeOnPreDrawListener(l)
            }
        }
    }
    effect.attachListener = attachListener
    addOnAttachStateChangeListener(attachListener)

    // Finding 13: Refresh source mapping when a glass View moves
    val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (effect.isDisposed) return@OnPreDrawListener true
        if (effect.checkGeometryChanged()) {
            invalidate()
        }
        true
    }
    effect.preDrawListener = preDrawListener

    if (isAttachedToWindow) {
        backdrop.registerGlassConsumer(this)
        val vto = viewTreeObserver
        if (vto.isAlive) {
            vto.addOnPreDrawListener(preDrawListener)
        }
    }

    // Finding 9: Wire shared interaction state while preserving native gesture dispatch
    if (glass.isInteractive) {
        val touchListener = GlassTouchListener(effect, renderer)
        effect.touchListener = touchListener
        setOnTouchListener(touchListener)
    }

    return effect
}

private class SavedPadding(
    val isRelative: Boolean,
    val a: Int,
    val top: Int,
    val b: Int,
    val bottom: Int,
) {
    fun applyTo(view: View) {
        if (isRelative) {
            view.setPaddingRelative(a, top, b, bottom)
        } else {
            view.setPadding(a, top, b, bottom)
        }
    }
}

private fun getRawPadding(view: View): SavedPadding {
    return if (view.isPaddingRelative) {
        SavedPadding(
            isRelative = true,
            a = view.paddingStart,
            top = view.paddingTop,
            b = view.paddingEnd,
            bottom = view.paddingBottom,
        )
    } else {
        SavedPadding(
            isRelative = false,
            a = view.paddingLeft,
            top = view.paddingTop,
            b = view.paddingRight,
            bottom = view.paddingBottom,
        )
    }
}

private class GlassTouchListener(
    effect: GlassViewEffect,
    renderer: GlassNativeRenderer,
) : View.OnTouchListener {
    private var effectRef: java.lang.ref.WeakReference<GlassViewEffect>? = java.lang.ref.WeakReference(effect)
    private var rendererRef: java.lang.ref.WeakReference<GlassNativeRenderer>? = java.lang.ref.WeakReference(renderer)

    fun clear() {
        effectRef = null
        rendererRef = null
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val effect = effectRef?.get() ?: return false
        val r = rendererRef?.get() ?: return false
        if (effect.isDisposed) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                r.touchX = event.x
                r.touchY = event.y
                r.touchAmount = 1f
                v.invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                r.touchX = event.x
                r.touchY = event.y
                v.invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                r.resetInteraction()
                v.invalidate()
            }
        }
        // On non-clickable views, consume the gesture so Android does not drop subsequent MOVE/UP/CANCEL events.
        // On clickable/long-clickable views, return false so the native click listeners receive the gesture.
        return !v.isClickable && !v.isLongClickable
    }
}

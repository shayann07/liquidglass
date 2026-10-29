package com.wexpa.liquidglass

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Appearance mode for XML-hosted glass views (`app:liquidGlassDark`).
 */
enum class GlassXmlDarkMode {
    /** Follows the Android `Configuration.UI_MODE_NIGHT_MASK` of the host context. */
    Auto,
    /** Forces the light appearance (`dark = false`). */
    Light,
    /** Forces the dark appearance (`dark = true`). */
    Dark,
}

/**
 * Material preset selectable from Android layout XML (`app:liquidGlassPreset`).
 */
enum class GlassXmlPreset {
    /** [Glass.Regular]: the measured in-app material. */
    Regular,
    /** [Glass.Clear]: permanently more transparent glass. */
    Clear,
    /** [Glass.Toolbar]: toolbar material. */
    Toolbar,
    /** [Glass.TabBar]: measured tab bar material. */
    TabBar,
    /** [GlassStyle.Chrome]: higher-opacity chrome for sparse backdrops. */
    Chrome,
    /** [Glass.Lens]: clear magnifier lens. */
    Lens,
    /** [Glass.Identity]: draws content only, with no glass effect. */
    Identity,
}

/**
 * Surface outline selectable from Android layout XML (`app:liquidGlassShape`).
 */
enum class GlassXmlShape {
    /** [CapsuleShape]: pill with 50% corner radius. */
    Capsule,
    /** [CircleShape]: circular outline. */
    Circle,
    /** [RoundedCornerShape] with `app:liquidGlassCornerRadius`. */
    Rounded,
    /** [GlassSquircleShape] with `app:liquidGlassCornerRadius` and `app:liquidGlassCornerPower`. */
    Squircle,
}

internal class TouchGatedFrameLayout(context: Context) : FrameLayout(context) {
    init {
        clipChildren = false
        clipToPadding = false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean =
        super.dispatchTouchEvent(ev)
}

/**
 * Android `View` host for Compose Liquid Glass overlays (`GlassTabBar`, `GlassEffectContainer`,
 * `Modifier.glassEffect`) inside XML layouts (`ConstraintLayout`, `FrameLayout`, `CoordinatorLayout`).
 *
 * Responsibilities:
 * 1. **Selective touch pass-through**: Only dispatches touch gestures to the internal [composeView]
 *    when `ACTION_DOWN` lands inside a registered interactive glass target (via [glassHitTarget],
 *    padded by the platform `ViewConfiguration.scaledTouchSlop`) or when [interceptAllTouches] is active (for example,
 *    an expanded floating glass menu scrim). All other touches pass straight through to the
 *    underlying Android `View` hierarchy (`RecyclerView`, `NestedScrollView`, canvas views).
 * 2. **Live Android `View` backdrop**: Resolves `app:liquidGlassBackdrop="@id/..."` (or [backdropView])
 *    and mirrors the live underlying `View` hierarchy into [LiquidGlassTheme]'s recording layer via
 *    [LiveViewBackdrop].
 */
open class LiquidGlassHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    internal val composeContainer: TouchGatedFrameLayout = TouchGatedFrameLayout(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    val composeView: ComposeView = ComposeView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    private val hitRects = ConcurrentHashMap<String, Rect>()
    private val backdropRects = ConcurrentHashMap<String, Rect>()

    internal var backdropRectsVersion: Int by mutableIntStateOf(0)
        private set

    // Device scaled touch-slop so edge taps on rounded caps register reliably.
    private val touchSlopPx: Int = ViewConfiguration.get(context).scaledTouchSlop
    private val tempLocation = IntArray(2)

    /** Resource ID of the Android [View] to refract as the backdrop (`app:liquidGlassBackdrop`). */
    var backdropViewId: Int = View.NO_ID
        set(value) {
            val old = field
            val oldResolved = resolvedBackdropView
            field = value
            try {
                if (isAttachedToWindow || parent != null) {
                    refreshBackdropTarget()
                }
            } catch (t: Throwable) {
                field = old
                resolvedBackdropView = oldResolved
                throw t
            }
        }

    private var explicitBackdropView: View? by mutableStateOf<View?>(null)
    private var resolvedBackdropView: View? by mutableStateOf<View?>(null)

    /** Explicit Android [View] to refract as the backdrop. Overrides [backdropViewId] when set. */
    var backdropView: View?
        get() = explicitBackdropView ?: resolvedBackdropView
        set(value) {
            val old = explicitBackdropView
            val oldResolved = resolvedBackdropView
            explicitBackdropView = value
            try {
                if (isAttachedToWindow || parent != null) {
                    refreshBackdropTarget()
                }
            } catch (t: Throwable) {
                explicitBackdropView = old
                resolvedBackdropView = oldResolved
                throw t
            }
        }

    /**
     * Deprecated no-op. The live backdrop renders directly from the [GlassBackdrop.sourceNode] and ignores this margin.
     */
    @Deprecated("No effect. The live backdrop renders directly from GlassBackdrop.sourceNode.")
    var backdropMarginDp: Dp by mutableStateOf(36.dp)

    /** Deprecated no-op. The live backdrop renders directly from the full [GlassBackdrop.sourceNode]. */
    @Deprecated("No effect. The live backdrop renders directly from GlassBackdrop.sourceNode.")
    var captureFullHostBounds: Boolean by mutableStateOf(false)

    private var _darkMode by mutableStateOf(GlassXmlDarkMode.Auto)

    /** Appearance mode configured via `app:liquidGlassDark` or code. */
    var darkMode: GlassXmlDarkMode
        get() = _darkMode
        set(value) {
            if (_darkMode != value) {
                _darkMode = value
                onAppearanceChanged()
            }
        }

    private var _glassBackgroundColor by mutableStateOf<Color?>(null)

    /** Optional opaque ground color configured via pp:liquidGlassBackground or code. */
    var glassBackgroundColor: Color?
        get() = _glassBackgroundColor
        set(value) {
            if (_glassBackgroundColor != value) {
                _glassBackgroundColor = value
                onAppearanceChanged()
            }
        }

    protected open fun onAppearanceChanged() {}

    /** When true (e.g. a floating glass menu is expanded), all touches are routed to Compose. */
    var interceptAllTouches: Boolean = false

    /** Optional callback invoked when a touch outside all glass hit targets occurs while [interceptAllTouches] is true. */
    var onOutsideTouchWhenIntercepting: (() -> Unit)? = null

    private var activeGestureOwnedByCompose: Boolean = false

    init {
        clipChildren = false
        clipToPadding = false
        if (id != View.NO_ID) {
            composeView.id = id xor 0x5F474C53
        } else {
            composeView.id = View.generateViewId()
        }
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.LiquidGlassHostView, defStyleAttr, 0)
            try {
                backdropViewId = a.getResourceId(R.styleable.LiquidGlassHostView_liquidGlassBackdrop, View.NO_ID)
                if (a.hasValue(R.styleable.LiquidGlassHostView_liquidGlassBackdropMargin)) {
                    val px = a.getDimension(R.styleable.LiquidGlassHostView_liquidGlassBackdropMargin, 0f)
                    backdropMarginDp = (px / resources.displayMetrics.density).dp
                }
                captureFullHostBounds = a.getBoolean(
                    R.styleable.LiquidGlassHostView_liquidGlassCaptureFullBounds,
                    false,
                )
                darkMode = when (a.getInt(R.styleable.LiquidGlassHostView_liquidGlassDark, 0)) {
                    1 -> GlassXmlDarkMode.Light
                    2 -> GlassXmlDarkMode.Dark
                    else -> GlassXmlDarkMode.Auto
                }
                if (a.hasValue(R.styleable.LiquidGlassHostView_liquidGlassBackground)) {
                    glassBackgroundColor = Color(a.getColor(R.styleable.LiquidGlassHostView_liquidGlassBackground, 0))
                }
            } finally {
                a.recycle()
            }
        }
        clipChildren = false
        clipToPadding = false
        composeContainer.addView(composeView)
        super.addView(composeContainer, 0, composeContainer.layoutParams)
    }

    override fun setId(id: Int) {
        super.setId(id)
        if (id != View.NO_ID) {
            composeView.id = id xor 0x5F474C53
        }
    }

    private val lastHostMatrix = android.graphics.Matrix()
    private val currentHostMatrix = android.graphics.Matrix()
    internal val transformEpoch = mutableLongStateOf(0L)
    private val hostPreDrawListener = android.view.ViewTreeObserver.OnPreDrawListener {
        val target = backdropView
        if (composeContainer.visibility != View.GONE && target != null && target.isAttachedToWindow && composeView.isAttachedToWindow && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            currentHostMatrix.reset()
            target.transformMatrixToGlobal(currentHostMatrix)
            composeView.transformMatrixToLocal(currentHostMatrix)
            if (currentHostMatrix != lastHostMatrix) {
                lastHostMatrix.set(currentHostMatrix)
                transformEpoch.longValue++
                composeView.invalidate()
            }
        }
        true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshBackdropTarget()
        val vto = viewTreeObserver
        if (vto.isAlive) {
            vto.addOnPreDrawListener(hostPreDrawListener)
        }
    }

    override fun onDetachedFromWindow() {
        val vto = viewTreeObserver
        if (vto.isAlive) {
            vto.removeOnPreDrawListener(hostPreDrawListener)
        }
        super.onDetachedFromWindow()
        resolvedBackdropView = null
    }

    protected open fun refreshBackdropTarget() {
        if (explicitBackdropView != null) {
            resolvedBackdropView = null
        } else {
            resolvedBackdropView = if (backdropViewId != View.NO_ID) {
                findBackdropById(backdropViewId)
            } else {
                parent as? View
            }
        }
        val target = backdropView
        if (target != null) {
            if (target is GlassBackdrop) {
                checkNoCycleWithAncestors(this, target)
            } else {
                android.util.Log.w("LiquidGlass", "Target backdrop view ($target) is not a GlassBackdrop")
            }
        }
    }

    protected fun findBackdropById(id: Int): View? {
        if (id == View.NO_ID) return null
        var current: View? = parent as? View
        while (current != null) {
            current.findViewById<View>(id)?.let { return it }
            current = current.parent as? View
        }
        return rootView?.findViewById(id)
    }

    protected fun resolveIsDark(): Boolean = when (darkMode) {
        GlassXmlDarkMode.Dark -> true
        GlassXmlDarkMode.Light -> false
        GlassXmlDarkMode.Auto -> {
            val nightBits = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            nightBits == Configuration.UI_MODE_NIGHT_YES
        }
    }

    protected fun resolveBackgroundColor(dark: Boolean): Color =
        glassBackgroundColor ?: if (dark) Color.Black else Color.White

    /**
     * Sets raw Compose content on this host. Callers that use [setGlassContent] directly can
     * configure their own [LiquidGlassTheme] and [LiveViewBackdrop], or call [setThemedGlassContent]
     * to have both wired automatically from XML attributes.
     */
    fun setGlassContent(content: @Composable (LiquidGlassHostView) -> Unit) {
        composeContainer.visibility = View.VISIBLE
        composeView.setContent {
            content(this)
        }
    }

    /**
     * Sets Compose content wrapped in [LiquidGlassTheme] and backed by a recording-only [LiveViewBackdrop]
     * using the backdrop view, appearance, and ground color configured via XML attributes or properties.
     */
    fun setThemedGlassContent(
        glass: Glass = Glass.Regular,
        content: @Composable (LiquidGlassHostView) -> Unit,
    ) {
        composeContainer.visibility = View.VISIBLE
        composeView.setContent {
            val dark = resolveIsDark()
            val bg = resolveBackgroundColor(dark)
            ThemedGlassScaffold(
                host = this@LiquidGlassHostView,
                glass = glass,
                dark = dark,
                backgroundColor = bg,
                targetView = backdropView,
                marginDp = backdropMarginDp,
                captureFullHostBounds = captureFullHostBounds,
                content = content,
            )
        }
    }

    internal fun updateHitRect(id: String, rect: Rect?) {
        if (rect == null || rect.isEmpty) {
            hitRects.remove(id)
        } else {
            hitRects[id] = rect
        }
    }

    internal fun updateBackdropRect(id: String, rect: Rect?) {
        val changed = if (rect == null || rect.isEmpty) {
            backdropRects.remove(id) != null
        } else {
            val old = backdropRects.put(id, rect)
            old != rect
        }
        if (changed) {
            backdropRectsVersion++
        }
    }

    internal fun currentBackdropRects(): Collection<Rect> = backdropRects.values

    /** Returns true if screen-space coordinates `(rawX, rawY)` fall inside any registered glass hit target. */
    fun isPointInsideGlass(rawX: Float, rawY: Float): Boolean {
        getLocationOnScreen(tempLocation)
        val localX = (rawX - tempLocation[0]).roundToInt()
        val localY = (rawY - tempLocation[1]).roundToInt()
        return isLocalPointInsideGlass(localX, localY)
    }

    private fun isLocalPointInsideGlass(x: Int, y: Int): Boolean {
        for (rect in hitRects.values) {
            if (
                x >= rect.left - touchSlopPx &&
                x <= rect.right + touchSlopPx &&
                y >= rect.top - touchSlopPx &&
                y <= rect.bottom + touchSlopPx
            ) {
                return true
            }
        }
        return false
    }

    protected open fun shouldFilterTouches(): Boolean = true

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (visibility != VISIBLE || alpha <= 0.01f) return false
        if (!onFilterTouchEventForSecurity(ev)) return false
        if (!shouldFilterTouches()) {
            return super.dispatchTouchEvent(ev)
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = ev.x.roundToInt()
                val y = ev.y.roundToInt()
                val hitGlass = isLocalPointInsideGlass(x, y)
                if (hitGlass) {
                    activeGestureOwnedByCompose = true
                    return super.dispatchTouchEvent(ev)
                }
                if (interceptAllTouches) {
                    activeGestureOwnedByCompose = true
                    onOutsideTouchWhenIntercepting?.invoke()
                    super.dispatchTouchEvent(ev)
                    return true
                }
                activeGestureOwnedByCompose = false
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val owned = activeGestureOwnedByCompose
                activeGestureOwnedByCompose = false
                return if (owned) super.dispatchTouchEvent(ev) else false
            }
            else -> {
                return if (activeGestureOwnedByCompose) super.dispatchTouchEvent(ev) else false
            }
        }
    }
}

@Composable
internal fun ThemedGlassScaffold(
    host: LiquidGlassHostView,
    glass: Glass,
    dark: Boolean,
    backgroundColor: Color,
    targetView: View?,
    marginDp: Dp,
    captureFullHostBounds: Boolean,
    content: @Composable (LiquidGlassHostView) -> Unit,
) {
    val state = rememberLiquidGlassState(backgroundColor, 0f, 0f, 0f, 1f)
    val region = remember(state) { GlassRegion(state) }
    val defaults = GlassDefaults(dark = dark, glass = glass, containerSpacing = 20.dp)

    CompositionLocalProvider(
        LocalGlassRegion provides region,
        LocalGlassDefaults provides defaults,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { region.root = it },
            propagateMinConstraints = true,
        ) {
            val epoch = host.transformEpoch.longValue
            val backdropLayer = key(epoch) { rememberGraphicsLayer() }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .onGloballyPositioned { coords ->
                        val size = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
                        state.sourceCoordinates = coords
                        state.sourceSize = size
                        for (m in region.views.values) {
                            m.sourceCoordinates = coords
                            m.sourceSize = size
                        }
                    }
                    .drawWithContent {
                        @Suppress("UNUSED_VARIABLE")
                        val e = epoch
                        backdropLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        state.layer = backdropLayer
                        for (m in region.views.values) {
                            m.layer = backdropLayer
                        }
                    },
            ) {
                LiveViewBackdrop(
                    targetView = targetView,
                    host = host,
                    marginDp = marginDp,
                    captureFullHostBounds = captureFullHostBounds,
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                content(host)
            }

            GlassOverlay(region, Modifier.matchParentSize())
        }
    }
}

/**
 * Pure Android XML glass surface container (`<com.wexpa.liquidglass.LiquidGlassView>`).
 *
 * Wraps ordinary Android XML child views (`LinearLayout`, `ImageView`, `TextView`, etc.) and renders
 * a refracting Liquid Glass surface behind them using [GlassNativeRenderer] sampling directly from
 * [GlassBackdrop] without spinning up internal Compose compositions.
 */
class LiquidGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LiquidGlassHostView(context, attrs, defStyleAttr) {

    /** Material preset configured via `app:liquidGlassPreset` or code. */
    var preset: GlassXmlPreset = GlassXmlPreset.Regular
        set(value) {
            if (field != value) {
                field = value
                updateRendererState()
            }
        }

    /** Surface shape configured via `app:liquidGlassShape` or code. */
    var xmlShape: GlassXmlShape = GlassXmlShape.Capsule
        set(value) {
            if (field != value) {
                field = value
                updateRendererState()
            }
        }

    /** Corner radius used when [xmlShape] is [GlassXmlShape.Rounded] or [GlassXmlShape.Squircle]. */
    var cornerRadiusDp: Dp = 24.dp
        set(value) {
            if (field != value) {
                field = value
                updateRendererState()
            }
        }

    /** Superellipse exponent used when [xmlShape] is [GlassXmlShape.Squircle]. */
    var cornerPower: Float = 4f
        set(value) {
            if (field != value) {
                field = value
                updateRendererState()
            }
        }

    /** Optional surface tint color configured via `app:liquidGlassTint` or code. */
    var glassTint: Color? = null
        set(value) {
            if (field != value) {
                field = value
                updateRendererState()
            }
        }

    /** Whether touch interaction (press glow and lift) is enabled (`app:liquidGlassInteractive`). */
    var isGlassInteractive: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateRendererState()
            }
        }

    /** Magnification factor when [preset] is [GlassXmlPreset.Lens] (`app:liquidGlassLensMagnification`). */
    var lensMagnification: Float = 1.4f
        set(value) {
            if (field != value) {
                field = value
                updateRendererState()
            }
        }

    /** Optional custom [Glass] override. */
    var customGlass: Glass? = null
        set(value) {
            if (field != value) {
                field = value
                updateRendererState()
            }
        }

    /** Optional custom Compose [Shape] override. */
    var customShape: Shape? = null
        set(value) {
            if (field != value) {
                field = value
                updateRendererState()
            }
        }

    var renderer: GlassNativeRenderer? = null
        internal set

    private val lastTransformMatrix = android.graphics.Matrix()
    private val currentTransformMatrix = android.graphics.Matrix()
    private var lastBackdropWidth: Int = -1
    private var lastBackdropHeight: Int = -1
    private val preDrawListener = android.view.ViewTreeObserver.OnPreDrawListener {
        val bd = backdropView as? GlassBackdrop
        if (bd != null && bd.isAttachedToWindow && isAttachedToWindow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bw = bd.width
            val bh = bd.height
            val sizeChanged = bw != lastBackdropWidth || bh != lastBackdropHeight
            if (sizeChanged) {
                lastBackdropWidth = bw
                lastBackdropHeight = bh
            }
            currentTransformMatrix.reset()
            bd.transformMatrixToGlobal(currentTransformMatrix)
            transformMatrixToLocal(currentTransformMatrix)
            val matrixChanged = currentTransformMatrix != lastTransformMatrix
            if (matrixChanged) {
                lastTransformMatrix.set(currentTransformMatrix)
            }
            if (sizeChanged || matrixChanged) {
                invalidate()
            }
        }
        true
    }

    init {
        captureFullHostBounds = true
        composeContainer.visibility = View.GONE

        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.LiquidGlassView, defStyleAttr, 0)
            try {
                backdropViewId = a.getResourceId(R.styleable.LiquidGlassView_liquidGlassBackdrop, backdropViewId)
                darkMode = when (a.getInt(R.styleable.LiquidGlassView_liquidGlassDark, 0)) {
                    1 -> GlassXmlDarkMode.Light
                    2 -> GlassXmlDarkMode.Dark
                    else -> GlassXmlDarkMode.Auto
                }
                if (a.hasValue(R.styleable.LiquidGlassView_liquidGlassBackground)) {
                    glassBackgroundColor = Color(a.getColor(R.styleable.LiquidGlassView_liquidGlassBackground, 0))
                }
                preset = when (a.getInt(R.styleable.LiquidGlassView_liquidGlassPreset, 0)) {
                    1 -> GlassXmlPreset.Clear
                    2 -> GlassXmlPreset.Toolbar
                    3 -> GlassXmlPreset.TabBar
                    4 -> GlassXmlPreset.Chrome
                    5 -> GlassXmlPreset.Lens
                    6 -> GlassXmlPreset.Identity
                    else -> GlassXmlPreset.Regular
                }
                xmlShape = when (a.getInt(R.styleable.LiquidGlassView_liquidGlassShape, 0)) {
                    1 -> GlassXmlShape.Circle
                    2 -> GlassXmlShape.Rounded
                    3 -> GlassXmlShape.Squircle
                    else -> GlassXmlShape.Capsule
                }
                if (a.hasValue(R.styleable.LiquidGlassView_liquidGlassCornerRadius)) {
                    val px = a.getDimension(R.styleable.LiquidGlassView_liquidGlassCornerRadius, 0f)
                    cornerRadiusDp = (px / resources.displayMetrics.density).dp
                }
                cornerPower = a.getFloat(R.styleable.LiquidGlassView_liquidGlassCornerPower, 4f)
                if (a.hasValue(R.styleable.LiquidGlassView_liquidGlassTint)) {
                    glassTint = Color(a.getColor(R.styleable.LiquidGlassView_liquidGlassTint, 0))
                }
                isGlassInteractive = a.getBoolean(R.styleable.LiquidGlassView_liquidGlassInteractive, false)
                lensMagnification = a.getFloat(R.styleable.LiquidGlassView_liquidGlassLensMagnification, 1.4f)
            } finally {
                a.recycle()
            }
        }

        val dark = resolveIsDark()
        val bg = resolveBackgroundColor(dark)
        val r = GlassNativeRenderer(
            targetView = this,
            backdrop = backdropView as? GlassBackdrop,
            glass = customGlass ?: buildXmlGlass(),
            shape = customShape ?: buildXmlShape(),
            dark = dark,
            backgroundColor = bg,
        )
        renderer = r
        background = GlassBackgroundDrawable(r)
    }

    override fun onAppearanceChanged() {
        super.onAppearanceChanged()
        updateRendererState()
    }

    private var registeredBackdrop: GlassBackdrop? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val bd = backdropView
        if (bd is GlassBackdrop) {
            checkNoCycleWithAncestors(this, bd)
        }
        updateRendererState()
        val target = bd as? GlassBackdrop
        target?.registerGlassConsumer(this)
        registeredBackdrop = target

        val vto = viewTreeObserver
        if (vto.isAlive) {
            vto.addOnPreDrawListener(preDrawListener)
        }
    }

    override fun onDetachedFromWindow() {
        registeredBackdrop?.unregisterGlassConsumer(this)
        registeredBackdrop = null
        renderer?.discard()
        super.onDetachedFromWindow()
        val vto = viewTreeObserver
        if (vto.isAlive) {
            vto.removeOnPreDrawListener(preDrawListener)
        }
    }

    override fun refreshBackdropTarget() {
        super.refreshBackdropTarget()
        val bd = backdropView
        if (bd is GlassBackdrop) {
            checkNoCycleWithAncestors(this, bd)
        }
        val newTarget = bd as? GlassBackdrop
        if (isAttachedToWindow && registeredBackdrop !== newTarget) {
            registeredBackdrop?.unregisterGlassConsumer(this)
            newTarget?.registerGlassConsumer(this)
            registeredBackdrop = newTarget
        }
        if (renderer != null) {
            updateRendererState()
        }
    }

    private fun updateRendererState() {
        val r = renderer ?: return
        val dark = resolveIsDark()
        val bg = resolveBackgroundColor(dark)
        r.backdrop = backdropView as? GlassBackdrop
        r.glass = customGlass ?: buildXmlGlass()
        r.shape = customShape ?: buildXmlShape()
        r.dark = dark
        r.backgroundColor = bg
        invalidate()
    }

    private fun buildXmlGlass(): Glass {
        val base = when (preset) {
            GlassXmlPreset.Regular -> Glass.Regular
            GlassXmlPreset.Clear -> Glass.Clear
            GlassXmlPreset.Toolbar -> Glass.Toolbar
            GlassXmlPreset.TabBar -> Glass.TabBar
            GlassXmlPreset.Chrome -> Glass(GlassStyle.Chrome)
            GlassXmlPreset.Lens -> Glass.Lens(lensMagnification)
            GlassXmlPreset.Identity -> Glass.Identity
        }
        val tinted = if (glassTint != null) base.tint(glassTint) else base
        return if (isGlassInteractive) {
            tinted.interactive(enabled = true, response = GlassInteraction.Calm)
        } else {
            tinted
        }
    }

    private fun buildXmlShape(): Shape = when (xmlShape) {
        GlassXmlShape.Capsule -> CapsuleShape
        GlassXmlShape.Circle -> CircleShape
        GlassXmlShape.Rounded -> RoundedCornerShape(cornerRadiusDp)
        GlassXmlShape.Squircle -> GlassSquircleShape(cornerRadiusDp, power = cornerPower)
    }

    override fun shouldFilterTouches(): Boolean = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isGlassInteractive) return super.onTouchEvent(event)
        val r = renderer
        if (r != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    r.touchX = event.x
                    r.touchY = event.y
                    r.touchAmount = 1f
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    r.touchX = event.x
                    r.touchY = event.y
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    r.touchAmount = 0f
                    invalidate()
                }
            }
        }
        val handled = super.onTouchEvent(event)
        return handled || isGlassInteractive
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (visibility != VISIBLE || alpha <= 0.01f) return false
        if (!onFilterTouchEventForSecurity(ev)) return false
        return super.dispatchTouchEvent(ev)
    }
}

/**
 * Registers this composable's bounds with [host] so touches inside it are routed to Compose
 * and its region is included in the live Android `View` backdrop capture.
 */
@Composable
fun Modifier.glassHitTarget(
    id: String,
    host: LiquidGlassHostView,
    interactive: Boolean = true,
    captureBackdrop: Boolean = true,
): Modifier {
    val lastRectRef = remember { arrayOfNulls<Rect>(1) }
    DisposableEffect(id, host, interactive, captureBackdrop) {
        val current = lastRectRef[0]
        if (current != null) {
            if (interactive) {
                host.updateHitRect(id, Rect(current))
            }
            if (captureBackdrop) {
                host.updateBackdropRect(id, Rect(current))
            }
        }
        onDispose {
            host.updateHitRect(id, null)
            host.updateBackdropRect(id, null)
        }
    }
    val tempLoc = remember { IntArray(2) }
    return this.onGloballyPositioned { coords ->
        if (!coords.isAttached) return@onGloballyPositioned
        val bounds = coords.boundsInWindow()
        host.getLocationInWindow(tempLoc)
        val left = (bounds.left - tempLoc[0]).roundToInt()
        val top = (bounds.top - tempLoc[1]).roundToInt()
        val right = (bounds.right - tempLoc[0]).roundToInt()
        val bottom = (bounds.bottom - tempLoc[1]).roundToInt()
        val rect = Rect(left, top, right, bottom)
        lastRectRef[0] = rect
        if (interactive) {
            host.updateHitRect(id, rect)
        } else {
            host.updateHitRect(id, null)
        }
        if (captureBackdrop) {
            host.updateBackdropRect(id, rect)
        } else {
            host.updateBackdropRect(id, null)
        }
    }
}

/**
 * Mirrors the live pixel output of an underlying Android [targetView] into the active
 * [LiquidGlassTheme] backdrop layer using [GlassBackdrop.sourceNode].
 */
@Composable
internal fun LiveViewBackdrop(
    targetView: View?,
    host: LiquidGlassHostView,
    marginDp: Dp = 36.dp,
    captureFullHostBounds: Boolean = false,
) {
    if (targetView == null) return
    val epoch = host.transformEpoch.longValue
    val matrix = remember(epoch) { android.graphics.Matrix() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                @Suppress("UNUSED_VARIABLE")
                val e = epoch
                val bd = targetView as? GlassBackdrop
                val sourceNode = bd?.sourceNode
                if (sourceNode != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    matrix.reset()
                    targetView.transformMatrixToGlobal(matrix)
                    host.composeView.transformMatrixToLocal(matrix)

                    val nativeCanvas = drawContext.canvas.nativeCanvas
                    val saveCount = nativeCanvas.save()
                    try {
                        nativeCanvas.concat(matrix)
                        nativeCanvas.drawRenderNode(sourceNode)
                    } finally {
                        nativeCanvas.restoreToCount(saveCount)
                    }
                }
            },
    )
}

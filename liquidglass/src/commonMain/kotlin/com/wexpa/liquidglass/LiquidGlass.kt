package com.wexpa.liquidglass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/**
 * Shared state linking a backdrop to the glass panels drawn over it.
 *
 * One state serves any number of panels: the backdrop is recorded once per frame and each
 * panel samples the region beneath itself out of that single recording.
 */
@Stable
class LiquidGlassState internal constructor(
    /**
     * The opaque colour behind the recorded backdrop.
     *
     * A backdrop layer is transparent wherever the recorded subtree painted nothing, and the
     * glass has to reconstruct what a viewer actually sees there, which is this colour showing
     * through. Getting it wrong is visible: too dark and the panels grow a black halo, too
     * light and they bloom.
     */
    internal val background: Color,
    /**
     * How far elements that allow it should invert light/dark, 0 to 1.
     *
     * Apple treats this as one decision per element rather than a per-pixel one, because
     * symbols and labels drawn *on* the glass flip in lockstep with it, and a fragment shader
     * behind them cannot reach them. So the app decides — from whatever it knows about its own
     * backdrop — and hands the same number to the glass and to its content. A permanently dark
     * app leaves it at 0 and nothing ever flips.
     */
    val inversion: Float,
    /**
     * Reduce Transparency, 0 to 1.
     *
     * Android has no system setting for this — iOS does, and Apple's material honours it — so
     * it is whatever in-app control the app exposes. It raises the material's opacity rather
     * than switching it off, which keeps the shape and the edge lighting for anyone who wants
     * to keep seeing them.
     */
    val frost: Float,
    /**
     * Increase Contrast, 0 to 1.
     *
     * Maps to `AccessibilityManager.isHighTextContrastEnabled` on Android, which is about text
     * rather than materials, so it is an approximation of Apple's setting rather than the same
     * thing. The material goes predominantly black or white with a contrasting border.
     */
    val contrast: Float,
    /**
     * The fraction of full resolution the material is rendered at, 0.25 to 1.
     *
     * The shader is evaluated once per pixel of a padded layer per panel per frame, so this is
     * the one knob that changes the cost by a large factor rather than a small one: at 0.7 the
     * shader touches half as many pixels. The result is scaled back up, so geometry, position
     * and colour are unaffected; what is lost is fine detail in the rim, which is exactly the
     * part of this material worth having. Measure before lowering it.
     *
     * 1 renders at full resolution and is the default, because a library should not quietly
     * trade away its own signature.
     */
    val renderScale: Float,
) {
    internal var layer: GraphicsLayer? by mutableStateOf(null)
    internal var sourceCoordinates: LayoutCoordinates? by mutableStateOf(null)
    internal var sourceSize: Size by mutableStateOf(Size.Zero)

    /** True once a backdrop has been recorded and panels can sample it. */
    val isReady: Boolean get() = layer != null && sourceSize != Size.Zero
}

@Composable
fun rememberLiquidGlassState(
    background: Color = Color.Black,
    inversion: Float = 0f,
    frost: Float = 0f,
    contrast: Float = 0f,
    renderScale: Float = 1f,
): LiquidGlassState = remember(background, inversion, frost, contrast, renderScale) {
    LiquidGlassState(background, inversion, frost, contrast, renderScale)
}

/**
 * Marks this content as the backdrop that glass panels refract.
 *
 * The content is recorded into a layer and then drawn from it, so it is rasterised once and
 * read many times. Put this on the thing the glass floats over — usually the scrolling body —
 * and not on an ancestor that also contains the glass, or the panel would sample itself.
 */
fun Modifier.liquidGlassSource(state: LiquidGlassState): Modifier = composed {
    val layer = rememberGraphicsLayer()
    this
        .onGloballyPositioned { coords ->
            state.sourceCoordinates = coords
            state.sourceSize = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
        }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            state.layer = layer
            drawLayer(layer)
        }
}

/**
 * Draws this element as a piece of glass over the backdrop registered in [state].
 *
 * Where the platform can run the shader the panel is a real optical element: the backdrop is
 * blurred, bent through the bevel, tinted against its own brightness, and lit. Where it
 * cannot — see [LiquidGlassSupport] — it degrades to a tinted surface with the same rim
 * lighting, which is a plainer material rather than a broken one.
 */
// Reads LiquidGlassDiagnostics, which is off by default: with no rig running, debugCoverage is 0 and the
// pad is the style's own, so this path is exactly the production one.
@OptIn(LiquidGlassDiagnosticApi::class)
fun Modifier.liquidGlass(
    state: LiquidGlassState,
    shape: Shape = RectangleShape,
    style: GlassStyle = GlassStyle.Regular,
    light: GlassLight = GlassLight.Default,
    /**
     * Whether the element responds to touch. Opt-in, as Apple makes it: the response is strong
     * enough that applying it to everything would be noise. See [GlassInteraction].
     */
    interaction: GlassInteraction? = null,
    /**
     * Whether the material is present, 0 to 1, for materialising in and out.
     *
     * Apple: "instead of fading, Liquid Glass objects materialize in and out by gradually
     * modulating the light bending and lensing", and the UIKit guidance is to prefer setting
     * the effect over setting alpha. Drive this instead of `alpha` and the element arrives by
     * becoming glass rather than by becoming opaque.
     */
    materialize: Float = 1f,
    /**
     * Drive the press from outside instead of letting this element watch its own pointer.
     *
     * For when another node owns the gesture — an indicator inside a bar that the bar drags.
     * See [GlassPressSource].
     */
    pressSource: GlassPressSource? = null,
    /**
     * Draw this element's content *inside* the glass rather than on top of it.
     *
     * Normally content sits on the material: labels on a button, icons on a bar. Some content
     * is the material's subject instead — what a magnifier is held over, the symbol a slider
     * knob carries, the tab a selection lens has slid across — and that content has to be
     * bent, colour-split and lit along with everything else the glass shows. Apple's tab bar
     * does exactly this: as the selection indicator crosses a symbol, the symbol is seen
     * through the lens, and its edges fringe where the rim refracts them.
     *
     * With this on, the content gets its own pass through the same distance field, bevel and
     * dispersion the backdrop goes through, and is drawn over the material: bent and
     * colour-split where the rim refracts it, clipped to the shape, and otherwise untouched —
     * the glass dims what is behind it, not what is printed inside it, which is what the
     * reference shows. Anything positioned outside the shape simply is not seen, which is what
     * lets a lens carry a copy of a whole row and show only the part it is over.
     *
     * Where the shader cannot run the content is drawn on top instead, as it always was.
     */
    refractContent: Boolean = false,
    /**
     * Glass this element looks *through*.
     *
     * A panel normally sees only the backdrop. A panel that sits on top of other glass — a
     * selection lens on a tab bar — sees that glass too, and on iOS what reaches the eye has
     * already been through it: the lens's interior reads as the bar does, and its rim bends the
     * bar's own edge. Record the intervening glass with [liquidGlassSource] into a second state
     * and pass it here, and it is composited over the backdrop before this panel's shader runs.
     *
     * The element must not be inside that source's subtree, or it would sample itself.
     */
    through: LiquidGlassState? = null,
    /**
     * Another rounded rect that this element's outline **fuses** with, in this element's own
     * coordinates.
     *
     * Not a union: where the two outlines cross, the silhouette bows out to meet the other
     * shape instead of making a crease, and it is one surface from there on — the bevel, the
     * rim and the refraction all follow the fused outline. iOS 27's tab bar does this with its
     * selection lens, which is proud of the bar; measured on the reference, the bar's edge sits
     * 12 px above both outlines where they cross and rejoins the flat run about 40 px away.
     *
     * Costs the closed-form normal, which cannot describe a fused outline, so the shader
     * differences the field instead. Null leaves the outline alone.
     */
    fuse: GlassFuse? = null,
    /**
     * How far the measured edge lens has formed, 0 to 1.
     *
     * On iOS 27 the fold at a cover sheet's edge is not a fixed property of the edge: it is
     * absent for the first ~117 dp of a tracked pull, fully formed after, and never shown on a
     * committed animation or on chrome at rest, which draw only a shallow ring at the edge. So
     * the default is 0. A host tracking a drag drives it from the pull
     * ([GlassMaterial.lensFormation]) and animates it back to 0 on release. Ignored by the
     * legacy profile.
     */
    lensFormation: Float = 0f,
): Modifier = liquidGlassCore(
    state = state,
    shape = shape,
    style = style,
    light = light,
    interaction = interaction,
    materialize = materialize,
    pressSource = pressSource,
    refractContent = refractContent,
    through = through,
    fuse = fuse,
    lensFormation = lensFormation,
    body = null,
    endpointComposite = false,
)

/**
 * The material, with the two V3 controls the public modifier does not expose.
 *
 * [body] replaces the shape's outline with the deforming two-disk body of [GlassBody]: coverage,
 * normal, curvature and the optical band all come from it, so the aperture really changes rather
 * than a finished picture being stretched.
 *
 * [endpointComposite] turns the two passes into the exact endpoint compositor of `V3-MODEL.md`
 * section 8. The material emits the opaque endpoint `B1`, the content pass emits the selected ink
 * premultiplied and unmasked, the two are composed once in an offscreen layer, and the result is
 * multiplied by aperture coverage. That is `(m C1, m)`, whose ordinary source-over onto the `C0`
 * already on the canvas is `C = (1-m) C0 + m C1` exactly. The alternative, mixing ink alone and
 * alpha-overing it on separately mixed material, is off by `m (1-m) (a1 - a0) (B1 - B0)`, which
 * is largest precisely at a moving rim.
 *
 * Both are internal because they are one component's contract, not a general drawing API yet.
 */
@OptIn(LiquidGlassDiagnosticApi::class)
internal fun Modifier.liquidGlassCore(
    state: LiquidGlassState,
    shape: Shape,
    style: GlassStyle,
    light: GlassLight,
    interaction: GlassInteraction?,
    materialize: Float,
    pressSource: GlassPressSource?,
    refractContent: Boolean,
    through: LiquidGlassState?,
    fuse: GlassFuse?,
    lensFormation: Float,
    body: GlassBody?,
    /**
     * The pose body, when this selector uses the 2D deforming shape rather than the two-disk
     * one. It takes precedence over [body]: they are two descriptions of the same thing and
     * stacking them would be the competing-transform mistake the parity brief's section 12 warns
     * against.
     */
    poseBody: GlassPoseRender? = null,
    endpointComposite: Boolean,
): Modifier = composed {
    val glassLayer = rememberGraphicsLayer()
    val endpointLayer = rememberGraphicsLayer()
    val contentLayer = rememberGraphicsLayer()
    // The wide tone kernel: a quarter-scale copy of the padded backdrop under a real Gaussian
    // blur, drawn into a strip beneath the sharp copy so the one input shader carries both.
    val wideLayer = rememberGraphicsLayer()
    // The panel's own contact shadow, blurred in its own layer so it can be drawn *into* the
    // recorded backdrop and therefore refracted along with it. See GlassStyle.contactShadow.
    val shadowLayer = rememberGraphicsLayer()
    // Effects are rebuilt only when their inputs change: building one binds some fifty
    // uniforms and allocates, per panel, and on a scrolling screen the inputs are the same
    // frame after frame (the backdrop changes, the uniforms do not).
    val effects = remember { GlassEffectCache() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // The position, held separately as a value.
    //
    // `coordinates` is the same LayoutCoordinates instance on every placement, so assigning it
    // to snapshot state registers no change and never invalidates the draw below. A scroll
    // re-places this element's existing layer rather than redrawing it, so without a value that
    // actually changes, a panel inside a list keeps the refraction it was first drawn with and
    // carries it down the screen like a decal. Reading this in the draw is what ties the two
    // together.
    var panelPosition by remember { mutableStateOf(Offset.Unspecified) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    // The size, for the same reason: reading `coordinates.size` in composition never sees a
    // resize in place, so the field, the band, the pad and the drag policy kept the first size.
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    val (press, pressModifier) = rememberGlassPress(
        enabled = interaction != null,
        source = pressSource,
    )

    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val measured = Size(layoutSize.width.toFloat(), layoutSize.height.toFloat())
    // The field is rasterised at the same origin the layer is recorded at: one pad, computed by
    // one function, so the shader's uPad addresses the field where it was drawn (a field built
    // with ceil(padPx) while the layer used ceil(pad * rs) / rs put the two on different origins:
    // 23 versus 24 full-resolution pixels for a 22.5 px pad at half scale).
    val padForField = style.recordPad(
        radii = shape.glassRadii(measured, direction, density),
        size = measured,
        fuse = fuse,
        density = density,
        renderScale = state.renderScale,
        bandOverride = body?.let { glassBodyBand(it) },
    )
    // Measured once per shape and size, never per frame. Null for every shape that has a closed
    // form, which is all of them until someone reaches for a path.
    val pathField = if (shape.needsSampledField(measured, direction, density)) {
        rememberGlassPathField(
            shape = shape,
            size = measured,
            pad = padForField,
            band = with(density) { if (style.refractionBand.isSpecified) style.refractionBand.toPx() else 0f },
            density = density,
            layoutDirection = direction,
        )
    } else {
        null
    }

    // The scale is a separate channel from the glow on purpose: it is the part the hand feels,
    // so it overshoots going down and settles calmly coming back. A single spring for both
    // makes the highlight bounce, which reads as a rendering glitch rather than as a press.
    // The balloon grows by a few dp a side whatever the element's size, per axis
    // (glassPressScale); one animated amount drives both axes so they rise and fall together.
    val growthPx = with(density) { interaction?.pressGrowth?.toPx() ?: 0f }
    fun pressTarget(side: Float, origin: Float, viewport: Int): Float {
        val requested = if (interaction != null) {
            if (growthPx > 0f) glassPressScale(side, growthPx, interaction.pressScale) else interaction.pressScale
        } else 1f
        return if (interaction?.pull == true) glassPressScaleInViewport(requested, side, origin, viewport.toFloat()) else requested
    }
    val balloonX = pressTarget(measured.width, panelPosition.x, viewportSize.width)
    val balloonY = pressTarget(measured.height, panelPosition.y, viewportSize.height)
    val pressDown = glassPressIsDown(interaction, press)
    val pressAmount by animateFloatAsState(
        targetValue = if (pressDown) 1f else 0f,
        animationSpec = if (pressDown) { if (growthPx > 0f) GlassMotion.BalloonDown else GlassMotion.PressDown } else GlassMotion.PressUp,
        label = "glass_press_scale",
    )
    val animatedPressX = 1f + (balloonX - 1f) * pressAmount
    val animatedPressY = 1f + (balloonY - 1f) * pressAmount
    val pressScaleX = if (interaction?.pull == true) glassPressScaleInViewport(
        animatedPressX, measured.width, panelPosition.x, viewportSize.width.toFloat()) else animatedPressX
    val pressScaleY = if (interaction?.pull == true) glassPressScaleInViewport(
        animatedPressY, measured.height, panelPosition.y, viewportSize.height.toFloat()) else animatedPressY
    // Press-and-pull ([GlassInteraction.pull]): the finger's travel is followed stiffly while
    // it is down and released through the recording's under-damped spring; the deformation is
    // one function of the animated travel, so the shape and the position ring together the way
    // the free search button does (reference-6756 S01 n440-476).
    val pullEnabled = interaction?.pull == true && pressSource == null
    val pullCentreLimit = minOf(measured.width * balloonX, measured.height * balloonY) *
        (interaction?.pullLimit ?: Float.POSITIVE_INFINITY)
    val pullLength = kotlin.math.sqrt(press.pullX * press.pullX + press.pullY * press.pullY)
    val compliance = glassPullTargetScale(pullLength, interaction?.pullFollow ?: 0f, pullCentreLimit)
    val pullDown = pullEnabled && press.pulling
    // animateFloatAsState hands each retarget the running velocity. Cancelling a parent
    // LaunchedEffect first lets Animatable.endAnimation reset it to zero on EVERY move,
    // making tracking depend on the pointer packet rate and damping release momentum away.
    val pullX by animateFloatAsState(
        targetValue = if (pullDown) press.pullX * compliance else 0f,
        animationSpec = if (pullDown) GlassMotion.PullFollow else GlassMotion.PullRelease,
        label = "glass_pull_x",
    )
    val pullY by animateFloatAsState(
        targetValue = if (pullDown) press.pullY * compliance else 0f,
        animationSpec = if (pullDown) GlassMotion.PullFollow else GlassMotion.PullRelease,
        label = "glass_pull_y",
    )
    val pullDeformation = if (pullEnabled && interaction != null) {
        // The element's extent along the pull, as it is while pressed.
        val px = pullX
        val py = pullY
        val radius = shape.glassRadii(measured, direction, density).minOrNull() ?: 0f
        val extent = glassPullExtent(measured.width, measured.height, radius,
            pressScaleX, pressScaleY, px, py)
        val acrossExtent = glassPullExtent(measured.width, measured.height, radius,
            pressScaleX, pressScaleY, -py, px)
        val rawPull = glassPullDeformation(
            pullX = px,
            pullY = py,
            extentPx = extent,
            elongation = interaction.pullElongation,
            widthRatio = interaction.pullWidthRatio,
            follow = interaction.pullFollow,
            acrossExtentPx = acrossExtent,
        )
        // Drag strain scales with surface size; press expansion and illumination do not.
        val deformation = glassAnchoredMaterialPull(
            glassSurfacePull(rawPull, measured.width, measured.height, density.density, extent),
            measured.width, measured.height,
        )
        if (panelPosition.x.isFinite() && panelPosition.y.isFinite()) glassPullInViewport(
            deformation, measured.width, measured.height, radius, pressScaleX, pressScaleY,
            panelPosition.x + measured.width / 2f, panelPosition.y + measured.height / 2f,
            viewportSize.width.toFloat(), viewportSize.height.toFloat(),
        ) else deformation
    } else {
        GlassPullDeformation.None
    }

    this
        .onGloballyPositioned {
            coordinates = it
            layoutSize = it.size
            panelPosition = it.positionInRoot()
            viewportSize = it.findRootCoordinates().size
        }
        .then(pressModifier)
        // Preserve the Legacy press contract. Pullable transforms only material drawing;
        // layout, foreground content and pointer coordinates remain anchored.
        .graphicsLayer {
            scaleX = if (pullEnabled) 1f else pressScaleX
            scaleY = if (pullEnabled) 1f else pressScaleY
        }
        .drawWithContent {
            val materialPressX = if (pullEnabled) pressScaleX else 1f
            val materialPressY = if (pullEnabled) pressScaleY else 1f
            val source = state.layer
            val radii = shape.glassRadii(size, layoutDirection, this)

            // Read the position so this draw depends on it; see the note where it is declared.
            @Suppress("UNUSED_EXPRESSION") panelPosition
            val delta = panelOffsetInSource(state.sourceCoordinates, coordinates)

            if (source != null && delta != null && LiquidGlassSupport.hasShaders) {
                // Apple's material samples "an area larger than itself" — that is what makes it
                // lens rather than merely blur. Record that margin, or displacement at the rim
                // clamps against the panel's own edge and the lensing has nothing to bend.
                // A body carries its own band: 0.6 of its smaller end radius, the same 0.6 R a
                // symmetric bar uses, which stops short of the medial axis whatever the body is
                // currently doing.
                // The optical band belongs to the BODY, not to the node it is drawn in. The
                // pose body carries its own - the canonical band under the smallest local
                // stretch - and falling through to the node's radii would give the band of a
                // rounded rect several times the body's size.
                val bandPx = poseBody?.d?.get(3)
                    ?: body?.let { glassBodyBand(it) }
                    ?: style.bandPx(radii, size, this)
                // Coordinate contract: the recorded layer, the shader's pad uniform, the sampled
                // field and the draw-back translate all use this one pad (see recordPad).
                val rs = state.renderScale.coerceIn(0.25f, 1f)
                val pad = style.recordPad(radii, size, fuse, this, state.renderScale, bandPx)
                val useEndpoint = endpointComposite && refractContent && LiquidGlassSupport.hasShaders
                val sizeFactor = elementSizeFactor(size, this)
                val contactShadow = glassShadow(sizeFactor)
                val contactShadowAlpha = contactShadow.alpha * style.contactShadow.coerceIn(0f, 1f)
                val bounds = sampleBounds(pad, delta, size, state.sourceSize)
                if (!hasSampleRegion(bounds)) {
                    withGlassMaterialPull(pullDeformation, materialPressX, materialPressY) {
                        drawGlassFallback(style, light, radii, style.bevel.toPx())
                    }
                    drawContent()
                    return@drawWithContent
                }
                val fullUniforms = GlassUniforms(
                        width = size.width,
                        height = size.height,
                        pad = pad,
                        scale = sizeFactor,
                        flip = if (style.invertsWithBackdrop && sizeFactor < 0.5f) {
                            state.inversion
                        } else {
                            0f
                        },
                        radii = radii,
                        refractBand = bandPx,
                        refractDepth = style.refractionDepth.toPx(),
                        aberration = style.dispersion,
                        profile = style.profile.uniform,
                        formation = lensFormation.coerceIn(0f, 1f),
                        heldLens = style.heldLens.coerceIn(0f, 1f),
                        heldMagnification = style.heldMagnification.coerceIn(0f, 0.6f),
                        heldGlow = style.heldGlow.coerceIn(0f, 1f),
                        heldEdgeRecovery = style.heldEdgeRecovery.toPx().coerceAtLeast(0f),
                        restMap = style.restMap.uniform,
                        body = body?.let {
                            floatArrayOf(
                                it.leftCentreX - size.width / 2f,
                                it.rightCentreX - size.width / 2f,
                                it.leftRadius,
                                it.rightRadius,
                            )
                        } ?: EMPTY_BODY,
                        bodyY = body?.let { it.cy - size.height / 2f } ?: 0f,
                        bodyKind = if (poseBody != null) 2f else if (body != null) 1f else 0f,
                        poseA = poseBody?.a ?: IDENTITY_POSE_A,
                        poseAInv = poseBody?.aInv ?: IDENTITY_POSE_A,
                        poseC = poseBody?.c ?: EMPTY_POSE_C,
                        poseD = poseBody?.d ?: EMPTY_POSE_D,
                        endpointAlpha = if (useEndpoint) 1f else 0f,
                        edgeFold = style.edgeFold.coerceIn(0f, 2f),
                        inkSplit = if (style.inkDispersion.isNaN()) style.dispersion else style.inkDispersion,
                        heldInk = if (style.heldInkContinuous) 1f else 0f,
                        wideKernel = if (style.wideKernel.isSpecified) style.wideKernel.toPx() else 0f,
                        fuse = fuse?.toPx(size, this)?.copyOf(4) ?: EMPTY_FUSE,
                        fuseRadius = fuse?.toPx(size, this)?.get(4) ?: 0f,
                        fuseWidth = fuse?.toPx(size, this)?.get(5) ?: 0f,
                        fineShare = style.fineShare,
                        // The pressed interior lifts as a whole ([GlassInteraction.pressLift]).
                        tintLift = style.tintLift + press.amount * (interaction?.pressLift ?: 0f),
                        liftAdapt = style.liftAdaptivity,
                        backdropSigma = if (style.backdropSigma.isSpecified) style.backdropSigma.toPx() else 0f,
                        ior = style.indexOfRefraction,
                        bevelPower = style.bevelPower,
                        cornerPower = if (shape is GlassSquircleShape) {
                            shape.power
                        } else {
                            style.cornerPower
                        },
                        shapeKind = if (pathField != null) 1f else 0f,
                        fieldRange = pathField?.range ?: 1f,
                        fieldScale = pathField?.scale ?: 1f,
                        field = pathField?.bitmap,
                        mirror = style.mirror,
                        fresnel = style.fresnel,
                        highlightChroma = style.highlightChroma,
                        legibility = style.legibility,
                        bevel = style.bevel.toPx(),
                        backdrop = bounds,
                        background = state.background,
                        touchX = press.x,
                        touchY = press.y,
                        touchAmount = press.amount * (interaction?.illumination ?: 0f),
                        materialize = materialize.coerceIn(0f, 1f),
                        frost = state.frost,
                        contrast = state.contrast,
                        debugCoverage = if (LiquidGlassDiagnostics.coverageOnly) 1f else 0f,
                        lightX = light.x,
                        lightY = light.y,
                        specular = style.specular,
                        specularPower = style.specularPower,
                        tint = style.tint,
                        innerShadow = style.innerShadow,
                        adaptivity = style.adaptivity,
                        blurRadius = style.blurRadius.toPx(),
                        backdropBlur = style.backdropBlur.toPx(),
                        counterLight = style.counterLight,
                        edgeShadow = style.edgeShadow,
                        rimSoft = style.rimSoftness.toPx(),
                        tintAbsorb = style.tintAbsorption,
                        edgeLight = style.edgeLight,
                        bevelPeak = style.bevelPeak,
                    )
                // Everything below happens in the reduced space if one is asked for: the layers
                // are recorded smaller, the shader is told smaller lengths, and the result is
                // drawn back up. See LiquidGlassState.renderScale.
                // The padded slice of backdrop beneath this panel, in the panel's own
                // coordinates offset by the pad.
                val paddedSize = IntSize(
                    ((size.width + pad * 2f) * rs).toInt().coerceAtLeast(1),
                    ((size.height + pad * 2f) * rs).toInt().coerceAtLeast(1),
                )
                val scaled = fullUniforms.scaledBy(rs)
                val useWide = style.profile != GlassProfile.Legacy && scaled.wideKernel >= 1f
                val wideScale = 0.25f
                val stripSize = if (useWide) {
                    IntSize(
                        kotlin.math.ceil(paddedSize.width * wideScale).toInt().coerceAtLeast(1),
                        kotlin.math.ceil(paddedSize.height * wideScale).toInt().coerceAtLeast(1),
                    )
                } else {
                    IntSize.Zero
                }
                val uniforms = scaled.copy(
                    wideStrip = if (useWide) paddedSize.height.toFloat() else 0f,
                    wideScale = wideScale,
                )
                val effect = effects.panel(uniforms)
                if (effect != null) {
                    val fullPadded = IntSize(
                        (size.width + pad * 2f).toInt().coerceAtLeast(1),
                        (size.height + pad * 2f).toInt().coerceAtLeast(1),
                    )
                    if (refractContent) {
                        if (useEndpoint) {
                            // Semantic ink is rasterised at native layer resolution whatever the
                            // backdrop's performance scale is: a label must not soften because the
                            // page behind it is being filtered cheaply.
                            contentLayer.record(size = fullPadded) {
                                translate(pad, pad) { this@drawWithContent.drawContent() }
                            }
                        } else {
                            // The content, alone and transparent, at the same padded size as the
                            // backdrop so the two passes share one coordinate frame.
                            contentLayer.record(size = paddedSize) {
                                scale(rs, rs, pivot = Offset.Zero) {
                                    translate(pad, pad) { this@drawWithContent.drawContent() }
                                }
                            }
                        }
                    }
                    // The contact shadow, sized and offset the way glassShadow() already says a
                    // panel of this size should sit off its background, recorded into its own
                    // layer so a real blur can be applied before it goes into the backdrop.
                    if (contactShadowAlpha > 0f) {
                        val shadowBlur = contactShadow.blurRadius.toPx()
                        val shadowOffset = contactShadow.offsetY.toPx()
                        shadowLayer.record(size = paddedSize) {
                            scale(rs, rs, pivot = Offset.Zero) {
                            translate(pad, pad + shadowOffset) {
                                drawRoundRect(
                                    color = Color.Black.copy(alpha = contactShadowAlpha),
                                    cornerRadius = CornerRadius(radii.getOrElse(0) { 0f }),
                                    size = size,
                                )
                            }
                            }
                        }
                        shadowLayer.renderEffect = effects.shadow(shadowBlur * rs)
                    }
                    val throughLayer = through?.layer
                    val throughDelta = through?.let { panelOffsetInSource(it.sourceCoordinates, coordinates) }
                    if (useWide) {
                        wideLayer.record(size = stripSize) {
                            drawRect(state.background)
                            scale(wideScale * rs, wideScale * rs, pivot = Offset.Zero) {
                                translate(-delta.x + pad, -delta.y + pad) { drawLayer(source) }
                                if (throughLayer != null && throughDelta != null) {
                                    translate(-throughDelta.x + pad, -throughDelta.y + pad) { drawLayer(throughLayer) }
                                }
                            }
                        }
                        wideLayer.renderEffect = effects.wide(uniforms.wideKernel * wideScale)
                    }
                    glassLayer.record(size = IntSize(paddedSize.width, paddedSize.height + stripSize.height)) {
                      scale(rs, rs, pivot = Offset.Zero) {
                    // Fill with the ground first. The padded slice reaches past the backdrop
                    // near a screen edge, and the backdrop is itself transparent wherever the
                    // app painted nothing; blurring either kind of hole drags transparency
                    // inward and the panel grows a halo of whatever it composites against.
                    // Baking the ground in once leaves an opaque image for the blur to work
                    // on, which is both correct and cheaper than compensating downstream.
                        drawRect(state.background)
                        translate(-delta.x + pad, -delta.y + pad) { drawLayer(source) }
                        // The panel's own shadow, on the backdrop rather than over it, so the
                        // rim bends it and the interior scatter defocuses it with everything
                        // else. Drawn before `through` for the same reason a real shadow is
                        // under what sits between: it belongs to the ground.
                        if (contactShadowAlpha > 0f) {
                            drawLayer(shadowLayer)
                        }
                        // Whatever sits between this panel and the backdrop goes on top of it,
                        // in this panel's frame, so the rim refracts that too.
                        if (throughLayer != null && throughDelta != null) {
                            translate(-throughDelta.x + pad, -throughDelta.y + pad) { drawLayer(throughLayer) }
                            // And a share of the raw backdrop again, added on top (rawShare).
                            if (style.rawShare > 0.001f) {
                                drawAdded(source, -delta.x + pad, -delta.y + pad, style.rawShare)
                            }
                        }
                      }
                      // The strip goes in LAST: the ground fill and the backdrop above are drawn
                      // over the whole layer, strip rows included, and would paint over it.
                      if (useWide) {
                          translate(0f, paddedSize.height.toFloat()) { drawLayer(wideLayer) }
                      }
                    }
                    glassLayer.renderEffect = effect
                    // The same shadow again, this time on the page, so the panel actually casts
                    // one. The copy inside the recording is what the rim bends; this is what a
                    // viewer sees around the panel. Drawn first, so the material lands on top of
                    // it and the two never show as separate shadows.
                    withGlassMaterialPull(pullDeformation, materialPressX, materialPressY) {
                        if (contactShadowAlpha > 0f) {
                            scale(1f / rs, 1f / rs, pivot = Offset.Zero) {
                                translate(-pad * rs, -pad * rs) { drawLayer(shadowLayer) }
                            }
                        }
                        if (style.dimmingLayer > 0f) {
                            drawRoundRect(
                                color = Color.Black.copy(alpha = style.dimmingLayer),
                                cornerRadius = CornerRadius(radii.getOrElse(0) { 0f }),
                                size = size,
                            )
                        }
                    }
                    if (useEndpoint) {
                        val inkEffect = effects.content(fullUniforms)
                        val apertureEffect = effects.endpoint(fullUniforms)
                        if (inkEffect != null && apertureEffect != null) {
                            contentLayer.renderEffect = inkEffect
                            // C1, complete and opaque: the material this selector shows, then the
                            // selected ink over it once. Source-over is exactly c1 + (1-a1) B1.
                            endpointLayer.record(size = fullPadded) {
                                scale(1f / rs, 1f / rs, pivot = Offset.Zero) { drawLayer(glassLayer) }
                                drawLayer(contentLayer)
                            }
                            // Times aperture coverage: (m C1, m), replaced over C0 in one draw.
                            endpointLayer.renderEffect = apertureEffect
                            withGlassMaterialPull(pullDeformation, materialPressX, materialPressY) {
                                translate(-pad, -pad) { drawLayer(endpointLayer) }
                            }
                            return@drawWithContent
                        }
                    }
                    withGlassMaterialPull(pullDeformation, materialPressX, materialPressY) {
                        scale(1f / rs, 1f / rs, pivot = Offset.Zero) {
                            translate(-pad * rs, -pad * rs) { drawLayer(glassLayer) }
                        }
                    }
                    // The content pass: the same field and the same bend, over the material.
                    val contentEffect = if (refractContent) effects.content(uniforms) else null
                    if (contentEffect != null) {
                        contentLayer.renderEffect = contentEffect
                        withGlassMaterialPull(pullDeformation, materialPressX, materialPressY) {
                            scale(1f / rs, 1f / rs, pivot = Offset.Zero) {
                                translate(-pad * rs, -pad * rs) { drawLayer(contentLayer) }
                            }
                        }
                    } else {
                        drawContent()
                    }
                    return@drawWithContent
                }
            }

            withGlassMaterialPull(pullDeformation, materialPressX, materialPressY) {
                drawGlassFallback(style, light, radii, style.bevel.toPx())
            }
            drawContent()
        }
}

/**
 * A rounded rect that an element's outline fuses with, given in that element's own coordinates.
 *
 * [bounds] is where it sits relative to the element's top-left corner, [cornerRadius] its corner,
 * and [smoothing] how wide the fusion is: the surface bows out by about a quarter of it where the
 * two outlines cross, and beyond it each shape is untouched. iOS 27's tab bar fuses at 16 dp.
 */
@Immutable
class GlassFuse(
    val bounds: DpRect,
    val cornerRadius: Dp,
    val smoothing: Dp = 16.dp,
) {
    /** centre x, centre y (relative to the element's centre), half width, half height, radius, smoothing. */
    internal fun toPx(size: Size, density: Density): FloatArray = with(density) {
        val l = bounds.left.toPx()
        val t = bounds.top.toPx()
        val r = bounds.right.toPx()
        val b = bounds.bottom.toPx()
        floatArrayOf(
            (l + r) / 2f - size.width / 2f,
            (t + b) / 2f - size.height / 2f,
            (r - l) / 2f,
            (b - t) / 2f,
            cornerRadius.toPx(),
            smoothing.toPx(),
        )
    }

    override fun equals(other: Any?): Boolean =
        other is GlassFuse && bounds == other.bounds && cornerRadius == other.cornerRadius &&
            smoothing == other.smoothing

    override fun hashCode(): Int =
        (bounds.hashCode() * 31 + cornerRadius.hashCode()) * 31 + smoothing.hashCode()
}

internal val EMPTY_FUSE = FloatArray(4)

internal val EMPTY_BODY = FloatArray(4)

/** The pose map at identity: A = I, no taper, no accommodation. */
internal val IDENTITY_POSE_A = floatArrayOf(1f, 0f, 0f, 1f)
internal val EMPTY_POSE_C = FloatArray(4)
internal val EMPTY_POSE_D = floatArrayOf(1f, 1f, 1f, 0f)

/**
 * A [share] of a layer added (not blended) on top of what is already drawn: how the measured
 * tab-bar lens shows the raw content behind the bar over the bar's own output
 * ([GlassStyle.rawShare]).
 */
private fun DrawScope.drawAdded(layer: GraphicsLayer, dx: Float, dy: Float, share: Float) {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            alpha = share.coerceIn(0f, 1f)
            blendMode = BlendMode.Plus
        }
        // Bounds are a hint; Skia intersects them with the clip.
        canvas.saveLayer(Rect(-1e4f, -1e4f, 1e4f, 1e4f), paint)
        translate(dx, dy) { drawLayer(layer) }
        canvas.restore()
    }
}

/**
 * The render effects of one panel, rebuilt only when their inputs change. [GlassUniforms]
 * compares every field, so a frame whose backdrop moved but whose panel did not reuses the
 * effect it already has.
 */
internal class GlassEffectCache {
    private var panelUniforms: GlassUniforms? = null
    private var panelEffect: androidx.compose.ui.graphics.RenderEffect? = null
    private var contentUniforms: GlassUniforms? = null
    private var contentEffect: androidx.compose.ui.graphics.RenderEffect? = null
    private var endpointUniforms: GlassUniforms? = null
    private var endpointEffect: androidx.compose.ui.graphics.RenderEffect? = null
    private var wideSigma: Float = Float.NaN
    private var wideEffect: androidx.compose.ui.graphics.RenderEffect? = null
    private var shadowSigma: Float = Float.NaN
    private var shadowEffect: androidx.compose.ui.graphics.RenderEffect? = null

    fun panel(uniforms: GlassUniforms): androidx.compose.ui.graphics.RenderEffect? {
        if (uniforms != panelUniforms) {
            panelEffect = createGlassRenderEffect(uniforms)
            panelUniforms = uniforms
        }
        return panelEffect
    }

    fun content(uniforms: GlassUniforms): androidx.compose.ui.graphics.RenderEffect? {
        if (uniforms != contentUniforms) {
            contentEffect = createGlassContentRenderEffect(uniforms)
            contentUniforms = uniforms
        }
        return contentEffect
    }

    fun endpoint(uniforms: GlassUniforms): androidx.compose.ui.graphics.RenderEffect? {
        if (uniforms != endpointUniforms) {
            endpointEffect = createGlassEndpointRenderEffect(uniforms)
            endpointUniforms = uniforms
        }
        return endpointEffect
    }

    fun wide(sigmaPx: Float): androidx.compose.ui.graphics.RenderEffect? {
        if (sigmaPx != wideSigma) {
            wideEffect = createWideKernelEffect(sigmaPx)
            wideSigma = sigmaPx
        }
        return wideEffect
    }

    fun shadow(blurPx: Float): androidx.compose.ui.graphics.RenderEffect {
        if (blurPx != shadowSigma) {
            shadowEffect = BlurEffect(blurPx, blurPx, TileMode.Decal)
            shadowSigma = blurPx
        }
        return shadowEffect!!
    }
}

/** The profile as the shader reads it: 0 legacy, 1 measured fold, 2 the held-lens family. */
internal val GlassProfile.uniform: Float
    get() = when (this) {
        GlassProfile.Legacy -> 0f
        GlassProfile.Measured -> 1f
        GlassProfile.Held -> 2f
    }

/** What the current platform can do, so callers can choose a design that survives the gap. */
expect object LiquidGlassSupport {
    /** True when the full optical path — refraction, specular, adaptive tint — can run. */
    val hasShaders: Boolean

    /** True when at least the backdrop can be blurred. */
    val hasBackdropBlur: Boolean
}

/**
 * Corner radii in px, in the order the shader expects: TL, TR, BR, BL.
 *
 * A [GlassSquircleShape] produces a generic path outline, so the platform reader cannot see its
 * radii; it is intercepted here because a squircle *is* analytic — it is exactly what
 * `uCornerPower` describes — and routing it through the sampled field would be slower and less
 * exact for no reason.
 */
internal fun Shape.glassRadii(size: Size, layoutDirection: LayoutDirection, density: Density): FloatArray {
    if (this is GlassSquircleShape) {
        val r = with(density) { cornerRadius.toPx() }
            .coerceAtMost(minOf(size.width, size.height) / 2f)
        return floatArrayOf(r, r, r, r)
    }
    return cornerRadiiPx(size, layoutDirection, density)
}

/** True when the shape has no closed form and the shader has to read a measured field. */
internal fun Shape.needsSampledField(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
): Boolean {
    if (this is GlassSquircleShape) return false
    if (size.width <= 0f || size.height <= 0f) return false
    return createOutline(size, layoutDirection, density) is Outline.Generic
}

internal expect fun Shape.cornerRadiiPx(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
): FloatArray

/**
 * The same panel described in a smaller coordinate space.
 *
 * The shader runs on the pixels of the recorded layer, so rendering the material at a reduced
 * resolution means every length it is given has to move into that resolution with it. Anything
 * measured in pixels scales; anything dimensionless does not. `fieldScale` is the exception that
 * divides: it maps a layer pixel to a texel of a field bitmap that is still stored at full size,
 * so as layer pixels get larger it has to get smaller.
 *
 * Getting one of these wrong produces optics that are subtly off rather than obviously broken,
 * which is why a rendered test asserts that a half-scale render matches a full-scale one.
 */
internal fun GlassUniforms.scaledBy(s: Float): GlassUniforms =
    if (s >= 1f) {
        this
    } else {
        copy(
            width = width * s,
            height = height * s,
            pad = pad * s,
            radii = FloatArray(radii.size) { radii[it] * s },
            refractBand = refractBand * s,
            refractDepth = refractDepth * s,
            wideKernel = wideKernel * s,
            fuse = FloatArray(fuse.size) { fuse[it] * s },
            body = FloatArray(body.size) { body[it] * s },
            bodyY = bodyY * s,
            // A is dimensionless and does not move. `c` is a position and scales; `b = k / R`
            // has units of 1 / length and therefore scales the other way, exactly as fieldScale
            // does. `spine`, `radius` and `band` are lengths; `scaleX` is not.
            poseC = floatArrayOf(poseC[0] * s, poseC[1] * s, poseC[2] / s, poseC[3] / s),
            poseD = floatArrayOf(poseD[0] * s, poseD[1] * s, poseD[2], poseD[3] * s),
            fuseRadius = fuseRadius * s,
            fuseWidth = fuseWidth * s,
            backdropSigma = backdropSigma * s,
            bevel = bevel * s,
            backdrop = FloatArray(backdrop.size) { backdrop[it] * s },
            touchX = touchX * s,
            touchY = touchY * s,
            blurRadius = blurRadius * s,
            backdropBlur = backdropBlur * s,
            rimSoft = rimSoft * s,
            // A recovery length in layer pixels, compared against the scaled layer's depth.
            heldEdgeRecovery = heldEdgeRecovery * s,
            fieldRange = fieldRange * s,
            fieldScale = fieldScale / s,
        )
    }

@Stable
internal data class GlassUniforms(
    val width: Float,
    val height: Float,
    /** Backdrop recorded beyond each edge, so displacement near the rim has content to reach. */
    val pad: Float,
    /** 0 for small chrome, 1 for a large surface. Keys opacity, lensing and shadow depth. */
    val scale: Float,
    /** 1 when this element may invert light/dark with its backdrop. */
    val flip: Float,
    val radii: FloatArray,
    val refractBand: Float,
    val refractDepth: Float,
    val aberration: Float,
    /** 0 the legacy Snell bevel, 1 the measured fold lens. */
    val profile: Float = 0f,
    /** Measured lens formation, 0 committed to 1 fully formed. */
    val formation: Float = 1f,
    /** [GlassProfile.Held]: how far the tab-bar lens has formed, 0 to 1. */
    val heldLens: Float = 0f,
    /** [GlassStyle.heldMagnification], carried with the lens it belongs to. */
    val heldMagnification: Float = 0f,
    /** [GlassStyle.heldGlow]. */
    val heldGlow: Float = 1f,
    /** [GlassStyle.heldEdgeRecovery], in layer pixels. */
    val heldEdgeRecovery: Float = 0f,
    /** Resting-corner source map: 0 the legacy map, 1 the measured table ([GlassRestMap]). */
    val restMap: Float = 0f,
    /** V3 body: left centre x, right centre x, left radius, right radius, in the centred frame. */
    val body: FloatArray = EMPTY_BODY,
    /** That body's shared centre y, same frame. */
    val bodyY: Float = 0f,
    /** 1 when the outline is the V3 two-disk body, 2 when it is the pose body, 0 for [radii]. */
    val bodyKind: Float = 0f,
    /**
     * The pose body of `GlassPoseGeometry.kt`, already reduced on the CPU.
     *
     * [poseA] is the 2x2 matrix `A` row-major and [poseAInv] its inverse; both are dimensionless.
     * [poseC] is `(cx, cy, bx, by)` with the reference centre in px and `b = k / R` in 1/px.
     * [poseD] is `(spine, radius, scaleX, band)`: three lengths and one dimensionless scale.
     * Which of those scale with the render resolution is declared in [scaledBy].
     */
    val poseA: FloatArray = IDENTITY_POSE_A,
    val poseAInv: FloatArray = IDENTITY_POSE_A,
    val poseC: FloatArray = EMPTY_POSE_C,
    val poseD: FloatArray = EMPTY_POSE_D,
    /** 1 when this pass emits a complete endpoint instead of its coverage-premultiplied share. */
    val endpointAlpha: Float = 0f,
    /** Straight-run fold strength on the measured profile ([GlassStyle.edgeFold]). */
    val edgeFold: Float = 0f,
    /** Per-channel split for semantic ink; 0 takes one sharp sample. */
    val inkSplit: Float = 0f,
    /** 1 uses the continuous held ink map ([GlassStyle.heldInkContinuous]). */
    val heldInk: Float = 0f,
    /** Sigma of the wide tone kernel, px; 0 disables it. */
    val wideKernel: Float = 0f,
    /** A rounded rect the outline fuses with: centre x, centre y, half width, half height, px. */
    val fuse: FloatArray = EMPTY_FUSE,
    /** That rect's corner radius and the fusion width, px. Width 0 means no fusion. */
    val fuseRadius: Float = 0f,
    val fuseWidth: Float = 0f,
    /** Layer row where the quarter-scale blurred copy starts; 0 when there is none. */
    val wideStrip: Float = 0f,
    /** Scale of that copy relative to the layer. */
    val wideScale: Float = 0.25f,
    /** Share of the fine kernel in the backdrop term. */
    val fineShare: Float = 1f,
    /** Fixed luminance lift after the tint. */
    val tintLift: Float = 0f,
    /** How far the lift falls with the wide-kernel luma. */
    val liftAdapt: Float = 0f,
    /** Gaussian sigma of the pre-blur, px; 0 means use [backdropBlur] as the platform radius. */
    val backdropSigma: Float = 0f,
    val ior: Float,
    val bevelPower: Float,
    val cornerPower: Float,
    val shapeKind: Float,
    val fieldRange: Float,
    val fieldScale: Float,
    /** The measured field, when the shape has no closed form. */
    val field: androidx.compose.ui.graphics.ImageBitmap?,
    val mirror: Float,
    val fresnel: Float,
    /** Crossfade from an additive white highlight to an Oklab lightness lift. */
    val highlightChroma: Float,
    val legibility: Float,
    val bevel: Float,
    /** Where inside the padded layer real pixels exist; sampling past it would read nothing. */
    val backdrop: FloatArray,
    /** Opaque ground the recorded backdrop sits on, for the transparent parts of it. */
    val background: Color,
    val touchX: Float,
    val touchY: Float,
    val touchAmount: Float,
    val materialize: Float,
    val frost: Float,
    val contrast: Float,
    /** Diagnostic only (see [LiquidGlassDiagnostics]): 1 renders flat colour times coverage. */
    val debugCoverage: Float = 0f,
    val lightX: Float,
    val lightY: Float,
    val specular: Float,
    val specularPower: Float,
    val tint: Color,
    val innerShadow: Float,
    val adaptivity: Float,
    val blurRadius: Float,
    val backdropBlur: Float,
    val counterLight: Float,
    val edgeLight: Float,
    val bevelPeak: Float,
    val edgeShadow: Float,
    val rimSoft: Float,
    val tintAbsorb: Float,
) {
    override fun equals(other: Any?): Boolean =
        other is GlassUniforms &&
            width == other.width && height == other.height &&
            pad == other.pad && scale == other.scale && flip == other.flip &&
            radii.contentEquals(other.radii) &&
            refractBand == other.refractBand && refractDepth == other.refractDepth &&
            aberration == other.aberration && backdrop.contentEquals(other.backdrop) &&
            ior == other.ior && bevelPower == other.bevelPower && mirror == other.mirror &&
            cornerPower == other.cornerPower && shapeKind == other.shapeKind &&
            fieldRange == other.fieldRange && fieldScale == other.fieldScale &&
            field === other.field &&
            touchX == other.touchX &&
            touchY == other.touchY && touchAmount == other.touchAmount &&
            materialize == other.materialize && frost == other.frost &&
            contrast == other.contrast && debugCoverage == other.debugCoverage &&
            fresnel == other.fresnel && highlightChroma == other.highlightChroma &&
            legibility == other.legibility &&
            background == other.background &&
            bevel == other.bevel && lightX == other.lightX && lightY == other.lightY &&
            specular == other.specular && specularPower == other.specularPower &&
            tint == other.tint && innerShadow == other.innerShadow &&
            adaptivity == other.adaptivity && blurRadius == other.blurRadius &&
            backdropBlur == other.backdropBlur &&
            counterLight == other.counterLight && edgeLight == other.edgeLight &&
            bevelPeak == other.bevelPeak && edgeShadow == other.edgeShadow &&
            rimSoft == other.rimSoft && tintAbsorb == other.tintAbsorb &&
            profile == other.profile && formation == other.formation && heldLens == other.heldLens &&
            heldMagnification == other.heldMagnification && heldGlow == other.heldGlow &&
            heldEdgeRecovery == other.heldEdgeRecovery &&
            restMap == other.restMap &&
            body.contentEquals(other.body) && bodyY == other.bodyY && bodyKind == other.bodyKind &&
            poseA.contentEquals(other.poseA) && poseAInv.contentEquals(other.poseAInv) &&
            poseC.contentEquals(other.poseC) && poseD.contentEquals(other.poseD) &&
            endpointAlpha == other.endpointAlpha && edgeFold == other.edgeFold &&
            inkSplit == other.inkSplit && heldInk == other.heldInk &&
            wideKernel == other.wideKernel && fineShare == other.fineShare &&
            fuse.contentEquals(other.fuse) && fuseRadius == other.fuseRadius &&
            fuseWidth == other.fuseWidth &&
            wideStrip == other.wideStrip && wideScale == other.wideScale &&
            tintLift == other.tintLift && liftAdapt == other.liftAdapt &&
            backdropSigma == other.backdropSigma

    override fun hashCode(): Int = width.hashCode() * 31 + height.hashCode() + radii.contentHashCode()
}

internal expect fun createGlassRenderEffect(
    uniforms: GlassUniforms,
): androidx.compose.ui.graphics.RenderEffect?

/**
 * The wide tone kernel's effect: a Gaussian blur of [sigmaPx] of the strip, in the layer's own
 * (sRGB) space. A linear-light blur was tried against the phone and rejected: it lifts the
 * material over dark content by 7-13 levels more than the phone does while gaining almost
 * nothing over light content (measured model, section 11).
 */
internal expect fun createWideKernelEffect(sigmaPx: Float): androidx.compose.ui.graphics.RenderEffect?

/**
 * The platform's blur radius for a Gaussian sigma in px. Android's blur takes a radius and turns
 * it into a sigma of `0.57735 * radius + 0.5`; Skia takes the sigma itself. Every blur that a
 * measurement names by its sigma goes through this so it means the same thing everywhere.
 */
internal expect fun platformBlurRadiusForSigma(sigma: Float): Float

/**
 * The content pass — see [GLASS_CONTENT_SHADER_SOURCE]. Takes the same uniforms as the material
 * so the two passes cannot disagree about the geometry.
 */
internal expect fun createGlassContentRenderEffect(
    uniforms: GlassUniforms,
): androidx.compose.ui.graphics.RenderEffect?

/**
 * The aperture pass of the endpoint compositor - see [GLASS_ENDPOINT_SHADER_SOURCE]. Same
 * uniforms again, so the coverage it applies is the coverage the other two passes used.
 */
internal expect fun createGlassEndpointRenderEffect(
    uniforms: GlassUniforms,
): androidx.compose.ui.graphics.RenderEffect?

/**
 * How "large" a panel reads, 0 to 1.
 *
 * Apple keys the material to element geometry rather than fixing it: bigger glass renders more
 * opaque, with deeper shadow and stronger lensing, while smaller glass renders clearer and is
 * allowed to invert light/dark to hold contrast. The scale below treats a 56dp control as
 * small and a 320dp surface as large, which puts a tab bar near the clear end and a sheet near
 * the opaque one.
 */
/**
 * The region of the padded layer the shader may read, in layer coordinates.
 *
 * Two different things go wrong outside it, which is why the bound is the intersection of two
 * rectangles rather than either one alone.
 *
 * The padded slice is deliberately larger than the panel so displacement near the rim has
 * somewhere to reach, and near a screen edge that slice hangs off the end of the backdrop.
 * Those pixels are not merely dark, they are a flat fill, and a panel that samples them grows
 * a band of solid colour at the rim. So the bound stops at the backdrop.
 *
 * The layer is also filled with the ground before the backdrop is drawn into it. That is what
 * keeps the blur honest: a blur over any transparent margin returns partly-transparent pixels,
 * and those composite as a halo whichever way they are unpacked. So the fill has to be there
 * even though nothing is allowed to sample it.
 *
 * Half a pixel of inset keeps bilinear filtering from reaching across either boundary.
 */
/**
 * Where this panel sits inside the backdrop recording, in the recording's own coordinates.
 *
 * Subtracting two `positionInRoot` values would be the obvious way to compute this and is
 * wrong as soon as anything between the two applies a transform: root positions come back with
 * that transform folded in, while the recorded layer is drawn in untransformed local space, so
 * the two disagree by the transform. An app that scales its content away behind a modal - a
 * 2.5% shrink is typical - would slide every panel's sample off by 2.5% of its distance from
 * the backdrop's origin, which is tens of pixels for chrome at the bottom of a screen.
 *
 * Asking the source for the panel's position in *its* space is both simpler and correct under
 * any transform. Returns null while either node is detached, which happens for a frame around
 * composition changes.
 */
internal fun panelOffsetInSource(
    source: LayoutCoordinates?,
    panel: LayoutCoordinates?,
): Offset? {
    if (source == null || panel == null) return null
    if (!source.isAttached || !panel.isAttached) return null
    // includeMotionFrameOfReference matters more than it looks. Compose tags scroll offsets as
    // a "motion frame of reference" and leaves them out by default, so without this the panel
    // reports where it would be if the list had never scrolled — and every glass element samples
    // a backdrop that slides out from under it as you scroll. It is invisible on a repetitive
    // backdrop and catastrophic past the recorded height, where the sample region inverts.
    return source.localPositionOf(panel, Offset.Zero, includeMotionFrameOfReference = true)
}

internal fun sampleBounds(
    pad: Float,
    delta: Offset,
    panelSize: Size,
    sourceSize: Size,
): FloatArray {
    val layerWidth = panelSize.width + pad * 2f
    val layerHeight = panelSize.height + pad * 2f
    val backdropLeft = pad - delta.x
    val backdropTop = pad - delta.y
    val left = maxOf(0f, backdropLeft) + 0.5f
    val top = maxOf(0f, backdropTop) + 0.5f
    val right = minOf(layerWidth, backdropLeft + sourceSize.width) - 0.5f
    val bottom = minOf(layerHeight, backdropTop + sourceSize.height) - 0.5f
    // An element can sit entirely outside the recorded backdrop — see the note on
    // `hasSampleRegion`. Returning an inverted rect would hand the shader a clamp whose min
    // exceeds its max, which is undefined and reads on screen as a solid block of nothing.
    return floatArrayOf(left, top, maxOf(left, right), maxOf(top, bottom))
}

/**
 * Whether there is any recorded backdrop under this element at all.
 *
 * There is one arrangement where the answer is no: a glass element *inside* a scrolling
 * container whose backdrop source is *outside* it. Compose applies that scroll offset when it
 * draws rather than when it lays out, so every layout API — `positionInRoot`, `positionOnScreen`,
 * `localPositionOf` with or without the motion frame of reference — reports the element's
 * unscrolled position. The offset between the panel and the backdrop is therefore unknowable
 * from public API, and once the element scrolls past the recorded height the sample region
 * collapses entirely.
 *
 * The supported arrangement is the one the material is for and the one Apple describes: glass
 * is chrome, and the backdrop is the content scrolling beneath it. Put `liquidGlassSource` on
 * the scrolling body and the glass outside the scroll, or put both inside it so they share the
 * frame. When neither holds, this returns false and the caller degrades to the flat fallback
 * surface rather than drawing a hole.
 */
internal fun hasSampleRegion(bounds: FloatArray): Boolean =
    bounds[2] - bounds[0] > 1f && bounds[3] - bounds[1] > 1f

/**
 * The refraction band in px: the style's own if it set one, else the measured 0.6 of the corner
 * radius, capped at the inradius. A sharp-cornered rectangle, or a path shape whose radii cannot
 * be read, gets no band unless the style names one.
 */
internal fun GlassStyle.bandPx(radii: FloatArray, size: Size, density: Density): Float = with(density) {
    if (refractionBand.isSpecified) return refractionBand.toPx()
    val r = radii.maxOrNull() ?: 0f
    val cap = minOf(size.width, size.height) / 2f
    GlassMaterial.BEVEL_RATIO * minOf(r, cap)
}

/**
 * The pad every consumer of the padded layer agrees on: the recording, the shader's `uPad`, the
 * sampled distance field and the draw-back translate. It is the blur/kernel reach ([padPx]),
 * enlarged for the held lens's outward sampling (0.3 of the band) and for a fused outline, then
 * rounded up to a whole number of layer pixels in the space the layer is recorded in
 * (`pad * renderScale`), so no consumer sits half a pixel from another.
 */
@OptIn(LiquidGlassDiagnosticApi::class)
internal fun GlassStyle.recordPad(
    radii: FloatArray,
    size: Size,
    fuse: GlassFuse?,
    density: Density,
    renderScale: Float,
    bandOverride: Float? = null,
): Float {
    val bandPx = bandOverride ?: bandPx(radii, size, density)
    var pad = maxOf(padPx(density), if (profile == GlassProfile.Held) bandPx * 0.3f else 0f)
    if (fuse != null) {
        val f = fuse.toPx(size, density)
        pad = maxOf(
            pad,
            maxOf(
                maxOf(-(f[0] - f[2]) + size.width / 2f, (f[0] + f[2]) - size.width / 2f),
                maxOf(-(f[1] - f[3]) + size.height / 2f, (f[1] + f[3]) - size.height / 2f),
            ) + f[5] * 0.25f + bandPx,
        )
    }
    val rs = renderScale.coerceIn(0.25f, 1f)
    LiquidGlassDiagnostics.recordPadOverridePx?.let { return kotlin.math.ceil(it * rs) / rs }
    return kotlin.math.ceil(pad * rs) / rs
}

/**
 * Opting in to [LiquidGlassDiagnostics], which exists for measurement rigs and instrumented tests and
 * changes what the material draws. Nothing in a normal app should need it.
 */
@RequiresOptIn(
    message = "LiquidGlassDiagnostics changes what the material renders. It is for measurement rigs and tests, not for production code.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class LiquidGlassDiagnosticApi

/**
 * Switches for the padding/coordinate diagnostic (Astra round 7). Both default to off and the material
 * behaves exactly as it does in production until one is set: an override changes only the recorded pad,
 * leaving every material uniform (blur radius included) as the style defines it, and coverage-only
 * rendering replaces the material with a flat colour times coverage. Call [reset] when a measurement
 * finishes, so a rig cannot leak its settings into whatever renders next.
 */
@LiquidGlassDiagnosticApi
object LiquidGlassDiagnostics {
    /** When set, the recorded pad in px before render-scale rounding; null in production. */
    var recordPadOverridePx: Float? = null

    /** When true, the panel shader outputs flat magenta times its coverage. */
    var coverageOnly: Boolean = false

    /** Back to the production defaults: no pad override, no coverage-only rendering. */
    fun reset() {
        recordPadOverridePx = null
        coverageOnly = false
    }

    /** Runs [block] with these settings and resets them afterwards, whatever [block] does. */
    fun <T> withDiagnostics(recordPadOverridePx: Float? = null, coverageOnly: Boolean = false, block: () -> T): T {
        this.recordPadOverridePx = recordPadOverridePx
        this.coverageOnly = coverageOnly
        try {
            return block()
        } finally {
            reset()
        }
    }
}

/**
 * How much backdrop to record beyond each edge. The legacy bevel samples outward by up to
 * 1.4x its depth; the measured lens samples inward only, so its pad is whatever the blurs and
 * the wide kernel reach.
 */
internal fun GlassStyle.padPx(density: Density): Float = with(density) {
    val sigma = if (backdropSigma.isSpecified) backdropSigma.toPx() * 2f else 0f
    val blur = maxOf(blurRadius.toPx(), backdropBlur.toPx(), sigma)
    if (profile == GlassProfile.Legacy) {
        refractionDepth.toPx() * 1.4f + blur
    } else {
        val wide = if (wideKernel.isSpecified) wideKernel.toPx() * 0.75f else 0f
        maxOf(blur, wide, 2f)
    }
}

internal fun elementSizeFactor(size: Size, density: Density): Float {
    val minEdgeDp = with(density) { minOf(size.width, size.height).toDp().value }
    return ((minEdgeDp - 56f) / (320f - 56f)).coerceIn(0f, 1f)
}

/** Transform only material output. Ordinary foreground is deliberately drawn outside this scope. */
private inline fun DrawScope.withGlassMaterialPull(
    pull: GlassPullDeformation, pressX: Float, pressY: Float, block: DrawScope.() -> Unit,
) {
    withTransform({
        rotate(pull.angleDegrees, center)
        scale(pull.along, pull.across, center)
        rotate(-pull.angleDegrees, center)
        scale(pressX, pressY, center)
    }, block)
}

package com.wexpa.liquidglass

import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.exp

/** Shared geometry entry point for every interactive material surface. Targets have already
 * been resisted/animated; never resist them twice. Press remains a separate transform. */
internal fun glassMaterialPull(
    width: Float, height: Float, radius: Float, density: Float,
    pressX: Float, pressY: Float, pullX: Float, pullY: Float,
    targetLimit: Float, interaction: GlassInteraction,
): GlassPullDeformation {
    if (interaction.pullShape == GlassPullShape.AreaPreserving) {
        val length = kotlin.math.hypot(pullX, pullY)
        if (length < 1e-5f || width <= 0f || height <= 0f || targetLimit <= 0f || !targetLimit.isFinite())
            return GlassPullDeformation.None
        val amount = (length * interaction.pullFollow / targetLimit).coerceIn(0f, 1f)
        // Authored spatial fit: 834x186 pressed1.05, then exp(+/-.047) gives835.49x204.70.
        // The full T04 sequence couples narrowing with growth; the previous absolute-width
        // subtraction gave873x198 instead. Timing/input gain remain authored.
        // Do not apply this relative law to generic large cards.
        val strain = .047f * amount
        val nx = pullX / length; val ny = pullY / length
        val roundness = (2f * min(width, height) / maxOf(width, height) - 1f).coerceIn(0f, 1f)
        // Remove shear in log space, preserving determinant1 even for diagonal pulls on bars.
        // A long bar stays level; a round surface retains the full directional response.
        val u = strain * (nx * nx - ny * ny)
        val v = 2f * strain * nx * ny * roundness
        val magnitude = kotlin.math.hypot(u, v)
        // Selected T04: the visible centre rises10.5px on a186px-high bar, independently
        // of layout. An authored5.6% short-side bias stays just below that displacement.
        // This moves material output only, never layout or ordinary labels. Other directions
        // use the same bounded law as an explicit extrapolation, not an Apple measurement.
        val bias = .056f * min(width, height) * amount
        return GlassPullDeformation(exp(magnitude), exp(-magnitude),
            atan2(v, u) * (90f / kotlin.math.PI.toFloat()), bias * nx, bias * ny)
    }
    val extent = glassPullExtent(width, height, radius, pressX, pressY, pullX, pullY)
    val across = glassPullExtent(width, height, radius, pressX, pressY, -pullY, pullX)
    val raw = glassPullDeformation(pullX, pullY, extent, interaction.pullElongation,
        interaction.pullWidthRatio, interaction.pullFollow, acrossExtentPx = across)
    return glassAnchoredMaterialPull(glassSurfacePull(raw, width, height, density, extent), width, height)
}

/**
 * What a press-and-pull does to a free piece of glass, as one transform: the body follows the
 * finger by a fraction, stretches along the pull and thins across it.
 *
 * IMG_6756 S01 shows a 234 px settled held body reaching 303 by 165 px at 112 px of centroid
 * displacement. This identifies deformation against BODY displacement, not finger travel:
 * the video contains no pointer trace. Input compliance is authored separately below.
 */
internal data class GlassPullDeformation(
    /** Scale along the pull's direction. */
    val along: Float,
    /** Scale across it. */
    val across: Float,
    /** The pull's direction, degrees from +x, clockwise on screen. */
    val angleDegrees: Float,
    /** Material drawing offset in px; never a layout or ordinary-foreground translation. */
    val translationX: Float,
    val translationY: Float,
) {
    companion object {
        val None = GlassPullDeformation(1f, 1f, 0f, 0f, 0f)
    }
}

/**
 * [pullX]/[pullY] are the finger's travel since it went down, in px; [extentPx] is the element's
 * extent along the pull's direction as it is while pressed. [elongation] and [follow] are
 * input gains; their ratio describes length change per unit of body displacement. The Pullable
 * preset uses 0.62 for that ratio, near S01's 69/112 and K01's 60/89. These observations
 * do not establish a finger-follow gain. Bound the target BEFORE animating it, so a long
 * over-pull cannot store invisible travel that delays the release.
 */
internal fun glassPullDeformation(
    pullX: Float,
    pullY: Float,
    extentPx: Float,
    elongation: Float,
    widthRatio: Float,
    follow: Float,
    maxAlong: Float = 1.6f,
    acrossExtentPx: Float = extentPx,
): GlassPullDeformation {
    val length = sqrt(pullX * pullX + pullY * pullY)
    if (length < 1e-3f) return GlassPullDeformation.None
    val along = min(1f + elongation * length / extentPx.coerceAtLeast(1f), maxAlong)
    // The circular recordings constrain equal absolute length gain and width loss. Applying
    // that same fraction to both axes crushed a wide pill on a short-axis pull: 40px of
    // height gain could remove 300px of width. Normalize each axis by its own support width.
    val extension = (along - 1f) * extentPx.coerceAtLeast(1f)
    val across = (1f - widthRatio * extension / acrossExtentPx.coerceAtLeast(1f)).coerceAtLeast(0.5f)
    val angle = atan2(pullY, pullX) * (180f / kotlin.math.PI.toFloat())
    return GlassPullDeformation(
        along = along,
        across = across,
        angleDegrees = angle,
        translationX = follow * pullX,
        translationY = follow * pullY,
    )
}

/** Smooth radial compliance: linear at the origin, progressively resistant, with no dead zone. */
internal fun glassPullTargetScale(length: Float, follow: Float, centreLimit: Float): Float {
    if (!centreLimit.isFinite() || length <= 1e-5f || follow <= 0f) return 1f
    return glassBoundedTravel(length * follow, centreLimit) / (length * follow)
}

/** Shared displacement resistance, applied to the target so no hidden over-pull accumulates. */
internal fun glassBoundedTravel(travel: Float, limit: Float): Float {
    if (!limit.isFinite()) return travel
    if (limit <= 0f) return 0f
    val cap = limit.coerceAtLeast(1e-3f)
    return cap * tanh(travel / cap)
}

/** Free travel between anchors, then the same continuous resistance used by standalone glass. */
internal fun glassAnchoredTravel(value: Float, lower: Float, upper: Float, limit: Float): Float = when {
    value < lower -> lower + glassBoundedTravel(value - lower, limit)
    value > upper -> upper + glassBoundedTravel(value - upper, limit)
    else -> value
}

internal fun glassPressScaleInViewport(requested: Float, side: Float, origin: Float, viewport: Float): Float {
    if (viewport <= 0f || !origin.isFinite() || origin < 0f || origin + side > viewport || side <= 0f) return requested
    val centre = origin + side / 2f
    return min(requested, (2f * min(centre, viewport - centre) / side).coerceAtLeast(1f))
}

/** Keep a standalone interactive surface within its window without clipping its drawing.
 * Only constrains an initially contained layout; scrolling/oversized layouts keep their own
 * placement policy. The round reference controls are unchanged when there is enough room.
 * This is a host boundary, not an inferred Apple optical or motion parameter. */
internal fun glassPullInViewport(
    pull: GlassPullDeformation,
    width: Float, height: Float, radius: Float,
    pressX: Float, pressY: Float,
    centreX: Float, centreY: Float, viewportWidth: Float, viewportHeight: Float,
): GlassPullDeformation {
    if (viewportWidth <= 0f || viewportHeight <= 0f || width <= 0f || height <= 0f ||
        centreX - width / 2f < 0f || centreX + width / 2f > viewportWidth ||
        centreY - height / 2f < 0f || centreY + height / 2f > viewportHeight
    ) return pull
    val r = radius.coerceIn(0f, min(width, height) / 2f)
    val angle = pull.angleDegrees * (kotlin.math.PI.toFloat() / 180f)
    val c = cos(angle); val s = sin(angle)
    // Support of the actual rounded rectangle under R S R^-1 P, not its bounding box.
    fun extent(t: Float, horizontal: Boolean): Float {
        val along = 1f + (pull.along - 1f) * t
        val across = 1f + (pull.across - 1f) * t
        val shear = (along - across) * c * s
        val x = (if (horizontal) along * c * c + across * s * s else shear) * pressX
        val y = (if (horizontal) shear else along * s * s + across * c * c) * pressY
        return (width / 2f - r) * abs(x) + (height / 2f - r) * abs(y) +
            r * sqrt(x * x + y * y)
    }
    val roomX = min(centreX, viewportWidth - centreX).coerceAtLeast(0f)
    val roomY = min(centreY, viewportHeight - centreY).coerceAtLeast(0f)
    fun fits(t: Float) = extent(t, true) <= roomX && extent(t, false) <= roomY
    var amount = 1f
    if (!fits(1f) && fits(0f)) {
        var low = 0f; var high = 1f
        repeat(18) {
            val mid = (low + high) / 2f
            if (fits(mid)) low = mid else high = mid
        }
        amount = low
    }
    val halfX = extent(amount, true); val halfY = extent(amount, false)
    // Exact free response in the inner half of the available travel, followed by C1 resistance.
    fun travel(value: Float, before: Float, after: Float): Float {
        val room = (if (value < 0f) before else after).coerceAtLeast(0f)
        val free = room / 2f
        if (abs(value) <= free) return value
        val result = free + glassBoundedTravel(abs(value) - free, room - free)
        return if (value < 0f) -result else result
    }
    return pull.copy(
        along = 1f + (pull.along - 1f) * amount,
        across = 1f + (pull.across - 1f) * amount,
        translationX = travel(pull.translationX, centreX - halfX, viewportWidth - centreX - halfX),
        translationY = travel(pull.translationY, centreY - halfY, viewportHeight - centreY - halfY),
    )
}

/** Support width of a uniformly rounded rectangle after its per-axis press growth. */
internal fun glassPullExtent(
    width: Float, height: Float, radius: Float, scaleX: Float, scaleY: Float,
    pullX: Float, pullY: Float,
): Float {
    val length = sqrt(pullX * pullX + pullY * pullY)
    if (length < 1e-5f) return min(width * scaleX, height * scaleY)
    val nx = abs(pullX) / length
    val ny = abs(pullY) / length
    val r = radius.coerceIn(0f, min(width, height).coerceAtLeast(0f) / 2f)
    return (width - 2f * r) * scaleX * nx + (height - 2f * r) * scaleY * ny +
        2f * r * sqrt(scaleX * scaleX * nx * nx + scaleY * scaleY * ny * ny)
}

/** The opt-in geometric response follows ownership; its glow may fade independently. */
internal fun glassPressIsDown(interaction: GlassInteraction?, press: GlassPress): Boolean =
    interaction != null && if (interaction.pull || interaction.pressGrowth.value > 0f) {
        press.pulling
    } else {
        press.amount > 0.5f
    }

/**
 * The balloon of a pressed element: the recording's bodies grow by about [growthPx] on every
 * side whatever their size (the 62 pt button and 78 pt key both settle +24 px per side at 3x), a ratio only
 * because they are small. Per axis, so a wide pill grows a few dp taller and a few dp wider
 * rather than 28 % wider (r16: the Live pill swelled past the screen's edges). [maxScale] caps
 * either axis for tiny elements.
 */
internal fun glassPressScale(sidePx: Float, growthPx: Float, maxScale: Float): Float =
    min(1f + 2f * growthPx / sidePx.coerceAtLeast(1f), maxScale)

/** Anchored material response. A round control can stretch diagonally; a long bar keeps its
 * spine level. The aspect-ratio blend is authored layout policy, not an Apple measurement.
 * No pull changes the control centre, its foreground, or its hit-test coordinates. */
internal fun glassAnchoredMaterialPull(pull: GlassPullDeformation, width: Float, height: Float): GlassPullDeformation {
    if (width <= 0f || height <= 0f) return GlassPullDeformation.None
    val a = pull.angleDegrees * (kotlin.math.PI.toFloat() / 180f)
    val c = cos(a); val s = sin(a)
    val xx = pull.along * c * c + pull.across * s * s
    val yy = pull.along * s * s + pull.across * c * c
    val roundness = (2f * min(width, height) / maxOf(width, height) - 1f).coerceIn(0f, 1f)
    val xy = (pull.along - pull.across) * c * s * roundness
    val mean = (xx + yy) / 2f
    val radius = sqrt((xx - yy) * (xx - yy) / 4f + xy * xy)
    return GlassPullDeformation(mean + radius, mean - radius,
        atan2(2f * xy, xx - yy) * (90f / kotlin.math.PI.toFloat()), 0f, 0f)
}

/** Drag-only size response. The recorded small controls do not establish a large-card law.
 * Preserve controls up to 80dp; smoothly reach the authored surface response by 160dp:
 * one-fifth input gain, saturating at 2dp total extension (1dp per edge). Press is separate. */
internal fun glassSurfacePull(
    pull: GlassPullDeformation, width: Float, height: Float, density: Float, extent: Float,
): GlassPullDeformation {
    if (density <= 0f || !density.isFinite()) return pull
    val t = ((maxOf(width, height) / density - 80f) / 80f).coerceIn(0f, 1f)
    if (t <= 0f) return pull
    val extension = (pull.along - 1f) * extent
    if (extension <= 0f) return pull
    val mix = t * t * (3f - 2f * t)
    val limit = 2f * density
    // tanh(x) / x -> 1 as x -> 0, so the gain tends to 0.2 with no step at the origin; the
    // series is used where the division would lose precision.
    val x = 0.2f * extension / limit
    val surfaceGain = if (x < 1e-3f) 0.2f * (1f - x * x / 3f) else limit * tanh(x) / extension
    val gain = 1f + (surfaceGain - 1f) * mix
    return pull.copy(along = 1f + (pull.along - 1f) * gain,
        across = 1f + (pull.across - 1f) * gain)
}

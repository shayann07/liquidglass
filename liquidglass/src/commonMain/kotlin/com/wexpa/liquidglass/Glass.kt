package com.wexpa.liquidglass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * A kind of Liquid Glass, as SwiftUI's `Glass` is: what [glassEffect] draws.
 *
 * Start from a variant and refine it, the way Apple's API reads:
 *
 * ```kotlin
 * Modifier.glassEffect(Glass.Regular.tint(Color.Blue).interactive(), CircleShape)
 * ```
 *
 * [Regular], [Clear] and [Identity] are Apple's three. [Lens], [TabBar] and [Toolbar] are measured
 * materials of particular iOS 26 elements. `Glass(style = GlassStyle(...))` exposes every optical
 * parameter, for anything else.
 *
 * Variants that differ between dark and light appearances follow [LiquidGlassTheme]'s `dark`.
 */
@Immutable
class Glass private constructor(
    private val resolve: ((dark: Boolean) -> GlassStyle)?,
    internal val tintColor: Color?,
    internal val isInteractive: Boolean,
    internal val interaction: GlassInteraction,
    private val name: String,
    private val lightFor: (dark: Boolean) -> GlassLight = { GlassLight.Default },
) {
    /** Colours the glass, as `.tint(_:)` does. Use it to carry meaning (a primary action), not as
     * decoration. Null removes a tint. */
    fun tint(color: Color?): Glass = Glass(resolve, color, isInteractive, interaction, name, lightFor)

    /**
     * Makes the glass respond to touch and pointer: it lifts, glows where it is pressed and, with
     * [GlassInteraction.Pullable], stretches toward the finger. Apply it only to elements that act on
     * a tap, as Apple advises. [response] chooses how it responds; the default is the calm, measured one.
     */
    fun interactive(enabled: Boolean = true, response: GlassInteraction = GlassInteraction.Calm): Glass =
        Glass(resolve, tintColor, enabled, response, name, lightFor)

    /** No glass at all: [glassEffect] draws only the content. For switching glass off without
     * changing the layout. */
    val isIdentity: Boolean get() = resolve == null

    /** The optical parameters this glass draws with in the given appearance; null for [Identity]. */
    fun style(dark: Boolean): GlassStyle? = resolve?.invoke(dark)?.let { s ->
        if (tintColor == null) s else s.copy(tint = tintColor)
    }

    /** Where the light comes from in the given appearance (see [GlassLight]). */
    fun light(dark: Boolean): GlassLight = lightFor(dark)

    override fun equals(other: Any?): Boolean = other is Glass && other.name == name &&
        other.tintColor == tintColor && other.isInteractive == isInteractive && other.interaction == interaction
    override fun hashCode(): Int = ((name.hashCode() * 31 + tintColor.hashCode()) * 31 +
        isInteractive.hashCode()) * 31 + interaction.hashCode()
    override fun toString(): String = "Glass.$name" + (tintColor?.let { ".tint($it)" } ?: "") +
        (if (isInteractive) ".interactive()" else "")

    companion object {
        /** Apple's regular glass: the measured in-app material (navigation pills, buttons, panels). */
        val Regular: Glass = Glass({ dark -> GlassStyle.inApp(dark) }, null, false, GlassInteraction.Calm, "Regular")

        /** Apple's clear glass: permanently more transparent, no adaptive behaviour. Needs content
         * behind it that is dimmed or calm enough to stay legible. */
        val Clear: Glass = Glass({ GlassStyle.Clear }, null, false, GlassInteraction.Calm, "Clear")

        /** No glass: the element draws its content only. */
        val Identity: Glass = Glass(null, null, false, GlassInteraction.Calm, "Identity")

        /** iOS 26's tab bar material, measured on recordings (`GlassTabBarStyle.Calm`). */
        val TabBar: Glass = Glass({ dark -> GlassTabBarStyle.Calm(dark).bar }, null, false, GlassInteraction.Calm, "TabBar",
            { dark -> GlassTabBarStyle.Calm(dark).light })

        /** A toolbar's material: the pill's, more opaque (measured on the Photos toolbar). */
        val Toolbar: Glass = Glass({ dark -> GlassStyle.toolbar(dark) }, null, false, GlassInteraction.Calm, "Toolbar")

        /** A clear magnifier: [magnification] at the centre, easing back to none at the rim (1 to 2.5). */
        fun Lens(magnification: Float = 1.4f): Glass {
            val style = GlassStyle.clearLens(magnification)
            return Glass({ style }, null, false, GlassInteraction.Calm, "Lens($magnification)")
        }

        /** Glass with every optical parameter given: see [GlassStyle]. [dark] is used in a dark
         * appearance when given, [style] otherwise; [light] is where the light comes from. */
        operator fun invoke(style: GlassStyle, dark: GlassStyle? = null, light: GlassLight = GlassLight.Default): Glass =
            Glass({ isDark -> if (isDark && dark != null) dark else style }, null, false, GlassInteraction.Calm,
                "Custom(${style.hashCode()},${dark.hashCode()},${light.hashCode()})", { light })
    }
}

/** A capsule: SwiftUI's default glass shape. */
val CapsuleShape: RoundedCornerShape = RoundedCornerShape(percent = 50)

/** Defaults [LiquidGlassTheme] hands to everything below it. */
@Immutable
internal data class GlassDefaults(
    val dark: Boolean,
    val glass: Glass,
    val containerSpacing: Dp,
    val reduceTransparency: Float = 0f,
    val increaseContrast: Float = 0f,
    val renderScale: Float = 1f,
)

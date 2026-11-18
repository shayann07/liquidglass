package com.wexpa.liquidglass.material3

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.wexpa.liquidglass.Glass
import com.wexpa.liquidglass.GlassLight
import com.wexpa.liquidglass.GlassStyle

/**
 * How a glass component is laid out. [Ios] (the default) uses iOS 26's shapes and placement: a floating
 * capsule tab bar with a lens, capsule buttons, bars without a container. [Material] keeps Material 3's
 * own layout and proportions and only turns the surfaces into glass.
 */
enum class GlassLayout { Ios, Material }

/** True inside a glass panel's content; [GlassMaterialDefaults.regular] reads it. */
internal val LocalOnGlassPanel = staticCompositionLocalOf { false }

/** Marks [content] as sitting on a glass panel. */
@Composable
internal fun OnGlassPanel(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalOnGlassPanel provides true, content = content)

/** Glass for the app's colours: what the components draw by default. */
object GlassMaterialDefaults {
    /**
     * Plain glass: secondary buttons, icon buttons, chips, bars. On a glass panel (a card, dialog,
     * sheet, menu or snackbar) it is [panel]: glass on glass samples the page under the panel, not the
     * panel, so unfrosted it would show a sharp, bent copy of what the panel hides.
     */
    val regular: Glass @Composable @ReadOnlyComposable get() = if (LocalOnGlassPanel.current) panel else control

    /**
     * The in-app material ([GlassStyle.inApp], measured) with iOS 27's rim, also measured
     * (`docs/research/ios27-measurements-raw.md`): a chamfered edge highlight of +65 levels with a
     * broad lobe, the dark contour outside the outline ([GlassStyle.outerContour], 0.61 black in a dark
     * appearance, a plus-darker 0.30 with 0.13 left at the poles in a light one), light from straight
     * above (0.0 degrees), and no dispersion (iOS 27's glass has none to 0.002 px). Choosing iOS 27's rim for Material components is authored; the core's
     * [Glass.Regular] keeps the iOS 26 material.
     */
    val control: Glass = Glass(Ios27Rim(GlassStyle.inApp(dark = false), dark = false),
        dark = Ios27Rim(GlassStyle.inApp(dark = true), dark = true), light = Overhead)

    /**
     * Prominent glass, as SwiftUI's `.glassProminent` button style: glass tinted with [color] (the
     * scheme's primary by default), for the main action on a screen.
     */
    @Composable @ReadOnlyComposable
    fun prominent(color: Color = MaterialTheme.colorScheme.primary): Glass = regular.tint(color.copy(alpha = ProminentAlpha))

    /** Tinted glass for a container colour a component was given, or plain glass when it is transparent. */
    @Composable @ReadOnlyComposable
    fun forContainer(container: Color): Glass =
        if (container.alpha < .05f || container == Color.Unspecified) regular
        else regular.tint(container.copy(alpha = (container.alpha * ProminentAlpha).coerceAtMost(ProminentAlpha)))

    /**
     * A toolbar's or rail's material: the Photos toolbar's measured role ([GlassStyle.toolbar], the
     * pill's material more opaque) with the same iOS 27 rim as [control].
     */
    val toolbar: Glass = Glass(Ios27Rim(GlassStyle.toolbar(dark = false), dark = false),
        dark = Ios27Rim(GlassStyle.toolbar(dark = true), dark = true), light = Overhead)

    /**
     * The frost under [panel]: 7.7 dp, measured on iOS's system backdrops (Control Center, App
     * Library, the Today view; [GlassStyle.backdropSigma]). Using it for in-app panels is authored.
     */
    val PanelBackdropSigma = 7.7.dp

    /**
     * Glass for large surfaces: cards, dialogs, sheets, menus, snackbars and the search panel. The
     * same material as [regular] over a frosted backdrop, as iOS draws its sheets and alerts: without
     * it, a panel's wide rim band compresses the text beside it into a sharp, smeared copy along the
     * edge. The blur is the measured system-backdrop sigma ([PanelBackdropSigma]).
     */
    val panel: Glass = Glass(
        Ios27Rim(GlassStyle.inApp(dark = false), dark = false).copy(backdropSigma = PanelBackdropSigma),
        dark = Ios27Rim(GlassStyle.inApp(dark = true), dark = true).copy(backdropSigma = PanelBackdropSigma),
        light = Overhead,
    )

    /**
     * A menu's material ([GlassStyle.menu]): in the dark appearance iOS 27's pop-up menu (#102), in the light one the
     * Safari `<select>` panel (#204). A pure blur, then a compressed tone, with no detail term; more frosted and more
     * opaque than [control]. The rim is [control]'s iOS 27 rim, and no scrim is added: iOS dims the page behind the
     * Photos long-press menu (0.52, measured) but not behind the title-pill menu, and dimming is the host's job.
     * [DropdownMenu] draws it by default; pass [panel] for the earlier look.
     */
    val menu: Glass = Glass(Ios27Rim(GlassStyle.menu(dark = false), dark = false),
        dark = Ios27Rim(GlassStyle.menu(dark = true), dark = true), light = Overhead)

    /**
     * A sheet's material ([GlassStyle.sheet], measured on iOS 27's share sheet, #102): a pure blur, then a compressed
     * tone, with no detail term, in both appearances. The rim is [control]'s iOS 27 rim. [ModalBottomSheet] draws it
     * by default; pass [panel] for the earlier look.
     */
    val sheet: Glass = Glass(Ios27Rim(GlassStyle.sheet(dark = false), dark = false),
        dark = Ios27Rim(GlassStyle.sheet(dark = true), dark = true), light = Overhead)

    /** How strongly prominent glass carries its tint. Authored, not measured. */
    const val ProminentAlpha = 0.62f
}

/** iOS 27's measured light: parallel, straight up the screen (0.0 +- 0.3 degrees). */
private val Overhead = GlassLight(0f, -1f)

/**
 * [style] with iOS 27's measured rim (`docs/research/ios27-measurements-raw.md` 4.5 and 5, on 44 pt
 * buttons at 3x):
 * - a chamfer, brightest in the outermost device pixel, so `bevelPeak` 0;
 * - +65 levels there (0.255), halving 2.34-2.41 px in, so `bevel` 1.6 dp (smoothstep half point
 *   at 2.4 px at 3x) and `specular` 0.27 (0.255 after the chamfer's 0.957 in the first pixel);
 * - a lobe whose best single cosine power is 2.3-2.8 (half width 30-45 degrees, 0.26 at 60), so
 *   `specularPower` 2.6;
 * - a counter-lobe roughly equal to the key lobe ([GlassStyle.inApp] already has `counterLight` 1);
 * - the dark contour outside the outline ([GlassStyle.outerContour]) and no dispersion.
 */
@Suppress("FunctionName")
private fun Ios27Rim(style: GlassStyle, dark: Boolean): GlassStyle = style.copy(
    bevel = 1.6.dp,
    bevelPeak = 0f,
    specular = 0.27f,
    specularPower = 2.6f,
    outerContour = if (dark) 0.61f else -0.30f,
    outerContourFloor = if (dark) 0f else 0.13f,
    dispersion = 0f,
)

/** True when the scheme is a dark one (its surface is dark). */
internal val ColorScheme.isDark: Boolean get() = surface.luminance() < .5f

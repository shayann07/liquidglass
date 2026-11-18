// Apache-2.0. The Compose clear lens's authored values.
namespace LiquidGlass.Skia;

/// <summary>The values of Compose's <c>GlassStyle.clearLens</c> that the lens preset binds ("Authored, not an Apple
/// calibration"), read from that source: the port makes no iOS claim for them. Every other term is
/// <c>GlassStyle</c>'s default, as the Compose shader binding applies it.</summary>
public static class ClearLens
{
    /// <summary>Bevel width in dp.</summary>
    public const float Bevel = 1.2f;
    /// <summary>Refraction depth in dp (GlassStyle's default).</summary>
    public const float RefractDepth = 14;
    public const float Specular = .28f;
    public const float Fresnel = .02f;
    public const float CounterLight = .35f;
    /// <summary>The white tint's opacity, before Compose's 8-bit storage.</summary>
    public const float TintAlpha = .015f;
    /// <summary>Scales <c>glassShadow</c>'s 0.14 alpha for the lens's contact shadow.</summary>
    public const float ContactShadow = .15f;
    /// <summary>The refraction band as a share of the lens's radius (GlassMaterial.BEVEL_RATIO).</summary>
    public const float BandRatio = .6f;
    /// <summary>GlassLight.Default, toward the light, y up negative.</summary>
    public const float LightX = 0f, LightY = -1f;
}

// Apache-2.0. The Compose Calm selector's material, as shader uniforms: the production pill, lens
// and bar styles (exported by GlassSelectorPortMaterialTest into ports/skia/fixtures/
// selector-material.json, embedded here) blended by the rules of glassSelectorMaterial /
// glassSelectorFormation in GlassTabBar.kt.
using System.Text.Json;

namespace LiquidGlass.Skia;

/// <summary>A Calm material: shader uniforms with lengths in dp, the band blend
/// (<c>uFormation</c>), the share of raw backdrop added over the glass the lens looks through and that
/// backdrop's blur in dp (GlassStyle.rawBlur).</summary>
public sealed record CalmMaterial(IReadOnlyDictionary<string, float[]> Values, float Formation, float RawShare, float RawBlur = 0);

public static class CalmSelectorMaterial
{
    private sealed record Style(Dictionary<string, float[]> Pill, Dictionary<string, float[]> Lens, Dictionary<string, float[]> Bar,
        Dictionary<string, float[]> ClearBar, float HeldLift, bool TouchLift, bool PillInkFollows, bool LensInkFollows, float TapOptics, float TapOpticsFold, float TapTintFade, float TapTransitFold);

    private static readonly HashSet<string> Snap = ["uProfile", "uRestMap", "uHeldInk"];
    // Lengths that may be unset (-1): an unset end snaps at the midpoint (GlassMorph `d`).
    private static readonly HashSet<string> OptionalDp = ["contactShadowBlur", "contactShadowOffset"];
    private static readonly Lazy<(HashSet<string> Dp, Dictionary<string, float> Fold, Style Dark, Style Light)> Data = new(Load);

    /// <summary>The fields given in dp; multiply by the density for shader px.</summary>
    public static IReadOnlySet<string> DpFields => Data.Value.Dp;

    private static (HashSet<string>, Dictionary<string, float>, Style, Style) Load()
    {
        using var stream = typeof(CalmSelectorMaterial).Assembly.GetManifestResourceStream("LiquidGlass.SelectorMaterial.json")
            ?? throw new InvalidOperationException("Embedded selector material missing");
        using var json = JsonDocument.Parse(stream);
        var root = json.RootElement;
        var dp = root.GetProperty("dpFields").EnumerateArray().Select(e => e.GetString()!).ToHashSet();
        var fold = root.GetProperty("fold").EnumerateObject().ToDictionary(p => p.Name, p => p.Value.GetSingle());
        static Dictionary<string, float[]> Map(JsonElement e) => e.EnumerateObject().ToDictionary(p => p.Name,
            p => p.Value.ValueKind == JsonValueKind.Array ? p.Value.EnumerateArray().Select(x => x.GetSingle()).ToArray() : [p.Value.GetSingle()]);
        Style Read(string name)
        {
            var s = root.GetProperty("styles").GetProperty(name);
            return new(Map(s.GetProperty("pill")), Map(s.GetProperty("lens")), Map(s.GetProperty("bar")),
                Map(s.GetProperty("clearBar")), s.GetProperty("heldLift").GetSingle(),
                s.GetProperty("touchLift").GetBoolean(), s.GetProperty("pillInkFollows").GetBoolean(), s.GetProperty("lensInkFollows").GetBoolean(),
                s.GetProperty("tapOptics").GetSingle(), s.GetProperty("tapOpticsFold").GetSingle(), s.GetProperty("tapTintFade").GetSingle(),
                s.TryGetProperty("tapTransitFold", out var transitFold) ? transitFold.GetSingle() : s.GetProperty("tapOpticsFold").GetSingle());
        }
        return (dp, fold, Read("dark"), Read("light"));
    }

    // Compose's sRGB Color stores 8 bits per channel.
    private static float Q8(float x) => MathF.Floor(Math.Clamp(x, 0, 1) * 255 + .5f) / 255;

    private static float[] LerpTint(float[] a, float[] b, float t)
    {
        if (t <= 0) return (float[])a.Clone();
        if (t >= 1) return (float[])b.Clone();
        var alpha = a[3] + (b[3] - a[3]) * t;
        if (alpha <= 1e-5f) return [b[0], b[1], b[2], Q8(alpha)];
        float Ch(float x, float y) => Math.Clamp((x * a[3] + (y * b[3] - x * a[3]) * t) / alpha, 0, 1);
        return [Q8(Ch(a[0], b[0])), Q8(Ch(a[1], b[1])), Q8(Ch(a[2], b[2])), Q8(alpha)];
    }

    private static Dictionary<string, float[]> LerpStyle(Style style, float t)
    {
        t = Math.Clamp(t, 0, 1);
        var output = new Dictionary<string, float[]>();
        foreach (var (key, a) in style.Pill)
        {
            var b = style.Lens[key];
            output[key] = key == "uTint" ? LerpTint(a, b, t)
                : Snap.Contains(key) || (OptionalDp.Contains(key) && (a[0] < 0 || b[0] < 0)) ? (float[])(t < .5f ? a : b).Clone()
                : a.Zip(b, (x, y) => t <= 0 ? x : t >= 1 ? y : x + (y - x) * t).ToArray();
        }
        // An ink split that follows dispersion snaps with the identity (GlassMorph `s`).
        if (style.PillInkFollows || style.LensInkFollows)
            output["uInkSplit"] = (t < .5f ? style.PillInkFollows : style.LensInkFollows)
                ? (float[])output["uAberration"].Clone() : (float[])(t < .5f ? style.Pill : style.Lens)["uInkSplit"].Clone();
        return output;
    }

    /// <summary>The selector's material for a formation, tap transit appearance and held travel,
    /// as GlassTabBar draws it.</summary>
    public static CalmMaterial Selector(bool dark, double formation, double transit, double heldTravel)
    {
        if (!double.IsFinite(formation) || !double.IsFinite(transit) || !double.IsFinite(heldTravel))
            throw new ArgumentOutOfRangeException(nameof(formation));
        var (_, fold, darkStyle, lightStyle) = Data.Value;
        var style = dark ? darkStyle : lightStyle;
        float f = (float)formation, a = (float)transit, h = (float)heldTravel;
        // Pigment: the lens/pill blend, its tint faded by the transit (glassTransitStyle).
        var pigment = LerpStyle(style, f);
        var amount = Math.Clamp(a * style.TapTintFade, 0, .95f);
        if (amount > 0)
        {
            var tint = pigment["uTint"];
            pigment["uTint"] = [tint[0], tint[1], tint[2], Q8(tint[3] * (1 - amount))];
            pigment["uLift"] = [pigment["uLift"][0] * (1 - amount)];
        }
        var values = pigment;
        float folded = 0;
        if (style.TapOptics > 0)
        {
            var lift = Math.Clamp(Math.Max(f, a * style.TapOptics), 0, 1);
            values = LerpStyle(style, lift);
            values["uTint"] = pigment["uTint"]; values["uLift"] = pigment["uLift"];
            // A tapped selector's own fold (Compose's GlassTabBarStyle.tapTransitFold).
            if (style.TapTransitFold > 0 && !(h > 0 && f >= .5f))
            {
                var t = Math.Clamp(a * style.TapOptics, 0, 1);
                folded = (t > f || (h == 0 && t > 0)) ? t * Math.Clamp(style.TapTransitFold, 0, 1) : 0;
            }
            if (folded > 0 || (style.TapOpticsFold > 0 && (f >= .5f || h > 0)))
            {
                values["uFoldCorners"] = [fold["foldCorners"]]; values["uRimMirror"] = [fold["rimMirror"]];
                values["uRimMirrorGain"] = [fold["rimMirrorGain"]];
                // The rim split (Compose glassSelectorMaterial, #73): the moving value in transit; a held
                // lens's split rises from the still value with its travel, its ink taking a share of it.
                var split = fold["dispersion"] + (fold["heldMovingDispersion"] - fold["dispersion"]) * Math.Clamp(h, 0, 1);
                values["uAberration"] = [split]; values["uInkSplit"] = [split * fold["inkSplitShare"]];
                if (folded > 0)
                {
                    values["uAberration"] = [fold["movingDispersion"]]; values["uInkSplit"] = [fold["movingDispersion"]];
                    if (f < .5f) values["uProfile"] = [fold["measuredProfile"]];
                    values["uFoldCorners"] = [fold["transitFoldCorners"]];
                }
            }
        }
        var band = style.TapOpticsFold > 0 && h > 0 ? Math.Clamp(h * style.TapOpticsFold, 0, 1) : folded;
        return new(values, Math.Clamp(band, 0, 1), values["rawShare"][0], values["rawBlur"][0]);
    }

    /// <summary>The Calm bar's material; its held lift follows the selector's formation, or, where the
    /// style lifts for any touch (GlassTabBarStyle.touchLift), the larger of that and <paramref name="touch"/>
    /// (<see cref="CalmNavigation.Touch"/>). <paramref name="clear"/> is GlassTabBarStyle.CalmClear's bar: the
    /// clearer glass of WhatsApp's and the App Store's dark bars (the same in light appearance).</summary>
    public static CalmMaterial Bar(bool dark, double formation, double touch = 0, bool clear = false)
    {
        var (_, _, darkStyle, lightStyle) = Data.Value;
        var style = dark ? darkStyle : lightStyle;
        var values = (clear ? style.ClearBar : style.Bar).ToDictionary(p => p.Key, p => (float[])p.Value.Clone());
        var lift = style.TouchLift ? Math.Max(formation, touch) : formation;
        values["uLift"] = [values["uLift"][0] + style.HeldLift * Math.Clamp((float)lift, 0, 1)];
        return new(values, 0, values["rawShare"][0], values["rawBlur"][0]);
    }
}

// Apache-2.0. Native Skia host adapter; material mathematics remain in production SkSL.
using SkiaSharp;

namespace LiquidGlass.Skia;

public sealed record GlassSurfaceOptions(bool Dark = false, float TintAmount = 50, float Density = 1,
    float Materialize = 1, float ReducedTransparency = 0, float IncreasedContrast = 0);

/// <summary>Draw multiple glass surfaces over one borrowed opaque sRGB image on an SKCanvas.
/// Coordinates are device pixels. The host owns input, layout, semantics and frame scheduling.</summary>
public sealed class GlassPainter : IDisposable
{
    private readonly GlassEffectPass material = new("material");
    private readonly SKPaint paint = new();
    private SKImage? source;
    private SKImage? ownedSource;
    private SKImage? wideImage;
    private float wideSigma;
    private bool disposed;
    private static readonly SKSamplingOptions Sampling = new(SKFilterMode.Linear, SKMipmapMode.None);
    private static readonly (float X, float Y)[] DarkOpacity = [(0, .416f), (29, .439f), (50, .451f), (62, .510f), (86, .655f), (100, .737f)];
    private static readonly (float X, float Y)[] LightOpacity = [(0, .435f), (31, .514f), (50, .565f), (62, .600f), (100, .733f)];
    private static readonly SKRuntimeEffect BackdropEffect = SKRuntimeEffect.CreateShader("""
        uniform shader sharp;
        uniform shader wide;
        uniform float2 origin;
        uniform float strip;
        uniform float threshold;
        half4 main(float2 p) {
          if (p.y >= threshold) return wide.eval(float2(p.x,p.y-strip)*4.0+origin);
          return sharp.eval(p+origin);
        }
        """, out _) ?? throw new InvalidOperationException("Cannot compile backdrop adapter");

    public void SetSource(SKImage image)
    {
        Alive(); ArgumentNullException.ThrowIfNull(image);
        if (image.Handle == IntPtr.Zero || image.Width <= 0 || image.Height <= 0 || image.AlphaType != SKAlphaType.Opaque)
            throw new ArgumentException("Supply a live opaque sRGB backdrop image", nameof(image));
        source = image;
        if (!ReferenceEquals(ownedSource, image)) { ownedSource?.Dispose(); ownedSource = null; }
        wideImage?.Dispose(); wideImage = null;
    }

    /// <summary>Record host-owned page drawing once for all glass. Exclude glass overlays.
    /// The painter owns this snapshot; a failed callback leaves the previous source intact.</summary>
    public void RecordBackdrop(int width, int height, Action<SKCanvas> drawPage, SKColor background)
    {
        Alive(); ArgumentNullException.ThrowIfNull(drawPage);
        if (width <= 0) throw new ArgumentOutOfRangeException(nameof(width));
        if (height <= 0) throw new ArgumentOutOfRangeException(nameof(height));
        if (background.Alpha != 255) throw new ArgumentException("An opaque background is required", nameof(background));
        using var surface = SKSurface.Create(new SKImageInfo(width, height, SKColorType.Rgba8888, SKAlphaType.Opaque))
            ?? throw new InvalidOperationException("Cannot allocate backdrop");
        surface.Canvas.Clear(background);
        drawPage(surface.Canvas);
        surface.Canvas.Flush();
        var next = surface.Snapshot();
        try { SetSource(next); ownedSource = next; }
        catch { next.Dispose(); throw; }
    }

    public void DrawLens(SKCanvas canvas, SKRect bounds, float magnification = 1.25f)
    {
        Ready(canvas, bounds);
        if (!float.IsFinite(magnification) || magnification < 1 || magnification > 2.5)
            throw new ArgumentOutOfRangeException(nameof(magnification));
        var values = ClearUniforms(material, bounds.Width, bounds.Height, 2, magnification);
        var origin = new SKPoint(bounds.Left - 2, bounds.Top - 2);
        using var shader = source!.ToShader(SKShaderTileMode.Clamp, SKShaderTileMode.Clamp, Sampling,
            SKMatrix.CreateTranslation(-origin.X, -origin.Y));
        Draw(canvas, values, shader, origin);
    }

    public void DrawSurface(SKCanvas canvas, SKRect bounds, float radius = 20, GlassSurfaceOptions? options = null)
        => DrawSurface(canvas, bounds, [radius, radius, radius, radius], options);

    public void DrawSurface(SKCanvas canvas, SKRect bounds, IReadOnlyList<float> radii, GlassSurfaceOptions? options = null)
    {
        Ready(canvas, bounds); ArgumentNullException.ThrowIfNull(radii);
        options ??= new();
        if (radii.Count != 4 || radii.Any(r => !float.IsFinite(r) || r < 0)) throw new ArgumentException("Four nonnegative radii required", nameof(radii));
        var o = options;
        if (!float.IsFinite(o.Density) || o.Density <= 0 || !float.IsFinite(o.TintAmount) || o.TintAmount is < 0 or > 100 ||
            new[] { o.Materialize, o.ReducedTransparency, o.IncreasedContrast }.Any(v => !float.IsFinite(v) || v is < 0 or > 1))
            throw new ArgumentOutOfRangeException(nameof(options));
        var v = ClearUniforms(material, bounds.Width, bounds.Height, 2, 1);
        var r = radii.Select(x => Math.Min(x, Math.Min(bounds.Width, bounds.Height) / 2)).ToArray();
        var t = o.TintAmount / 100;
        var tint = o.Dark ? 0 : 241f / 255;
        Set(v, "uRadii", r); Set(v, "uRefractBand", .6f * r.Max()); Set(v, "uRefractDepth", 14 * o.Density);
        Set(v, "uProfile", 1); Set(v, "uFormation", 0); Set(v, "uHeldLens", 0); Set(v, "uHeldMagnification", 0);
        Set(v, "uHeldGlow", 1); Set(v, "uAberration", .07f); Set(v, "uBlur", (.5f + 1.5f * t * t) / .6f * o.Density);
        Set(v, "uWideKernel", 10 * o.Density); Set(v, "uFineShare", .95f * MathF.Pow(1 - t, 1.5f));
        Set(v, "uTint", tint, tint, tint, MathF.Round(Interpolate(o.Dark ? DarkOpacity : LightOpacity, o.TintAmount) * 255) / 255);
        Set(v, "uLift", o.Dark ? 35f / 255 : 0); Set(v, "uBevel", 1.33f * o.Density); Set(v, "uBevelPeak", .25f);
        Set(v, "uSpecular", .25f); Set(v, "uSpecularPow", 6); Set(v, "uEdgeLight", 0); Set(v, "uLegibility", .6f);
        Set(v, "uScale", Math.Clamp((Math.Min(bounds.Width, bounds.Height) / o.Density - 56) / 264, 0, 1));
        Set(v, "uMaterialize", o.Materialize); Set(v, "uFrost", o.ReducedTransparency); Set(v, "uContrast", o.IncreasedContrast);
        var origin = new SKPoint(bounds.Left - 2, bounds.Top - 2);
        using var shader = Backdrop(origin, 10 * o.Density, out var strip);
        Set(v, "uWideStrip", strip);
        Draw(canvas, v, shader, origin);
    }

    public static Dictionary<string, float[]> ClearUniforms(GlassEffectPass effect, float width, float height,
        float pad = 32, float magnification = 1.25f)
    {
        if (new[] { width, height, pad, magnification }.Any(v => !float.IsFinite(v)) || width <= 0 || height <= 0 || pad < 0 || magnification is < 1 or > 2.5f)
            throw new ArgumentOutOfRangeException(nameof(width));
        var v = effect.CreateUniforms();
        Set(v, "uSize", width, height); Set(v, "uPad", pad); Set(v, "uBackdrop", 0, 0, width + 2 * pad, height + 2 * pad);
        Set(v, "uRadii", Enumerable.Repeat(Math.Min(width, height) / 2, 4).ToArray());
        Set(v, "uRefractBand", 24); Set(v, "uRefractDepth", 12); Set(v, "uIor", 1.5f); Set(v, "uBevelPower", 2);
        Set(v, "uCornerPower", 2); Set(v, "uFieldRange", 1); Set(v, "uFieldScale", 1); Set(v, "uBevel", 2);
        Set(v, "uLight", 0, -1); Set(v, "uSpecularPow", 4); Set(v, "uCounterLight", 1); Set(v, "uEdgeLight", 1);
        Set(v, "uTint", 1, 1, 1, 0); Set(v, "uProfile", 3); Set(v, "uFormation", 1); Set(v, "uHeldLens", 1);
        Set(v, "uHeldMagnification", 1 - 1 / magnification); Set(v, "uFineShare", 1); Set(v, "uWideScale", .25f);
        Set(v, "uTouch", width / 2, height / 2); Set(v, "uMaterialize", 1);
        Set(v, "uPoseA", 1, 0, 0, 1); Set(v, "uPoseAInv", 1, 0, 0, 1); Set(v, "uPoseD", 1, 1, 1, 0);
        return v;
    }

    private SKShader Backdrop(SKPoint origin, float sigma, out float strip)
    {
        if (wideImage is null || wideSigma != sigma)
        {
            wideImage?.Dispose(); wideImage = null;
            using var surface = SKSurface.Create(new SKImageInfo((source!.Width + 3) / 4, (source.Height + 3) / 4, SKColorType.Rgba8888, SKAlphaType.Premul))
                ?? throw new InvalidOperationException("Cannot allocate wide tone image");
            using var sharp = source.ToShader(SKShaderTileMode.Clamp, SKShaderTileMode.Clamp, Sampling);
            using var filter = SKImageFilter.CreateBlur(sigma, sigma, SKShaderTileMode.Clamp);
            using var p = new SKPaint { Shader = sharp, ImageFilter = filter };
            surface.Canvas.Clear(SKColors.Transparent); surface.Canvas.Scale(.25f, .25f); surface.Canvas.DrawPaint(p); surface.Canvas.Flush();
            wideImage = surface.Snapshot(); wideSigma = sigma;
        }
        using var sharpShader = source!.ToShader(SKShaderTileMode.Clamp, SKShaderTileMode.Clamp, Sampling);
        using var wideShader = wideImage.ToShader(SKShaderTileMode.Clamp, SKShaderTileMode.Clamp, Sampling, SKMatrix.CreateScale(4, 4));
        var threshold = Math.Max(1, source.Height - origin.Y + 1);
        strip = threshold + Math.Max(0, origin.Y / 4) + 1;
        using var uniforms = new SKRuntimeEffectUniforms(BackdropEffect);
        uniforms["origin"] = origin; uniforms["strip"] = strip; uniforms["threshold"] = threshold;
        using var children = new SKRuntimeEffectChildren(BackdropEffect);
        children["sharp"] = sharpShader; children["wide"] = wideShader;
        return BackdropEffect.ToShader(uniforms, children);
    }

    private void Draw(SKCanvas canvas, Dictionary<string, float[]> v, SKShader backdrop, SKPoint origin)
    {
        Set(v, "uBackdrop", -origin.X, -origin.Y, source!.Width - origin.X, source.Height - origin.Y);
        using var shader = material.CreateShader(v, new Dictionary<string, SKShader> { { "content", backdrop }, { "field", backdrop } });
        paint.Shader = shader;
        canvas.Save();
        try { canvas.Translate(origin.X, origin.Y); canvas.DrawRect(0, 0, v["uSize"][0] + 2 * v["uPad"][0], v["uSize"][1] + 2 * v["uPad"][0], paint); }
        finally { canvas.Restore(); paint.Shader = null; }
    }
    private void Alive() => ObjectDisposedException.ThrowIf(disposed, this);
    private void Ready(SKCanvas canvas, SKRect bounds)
    {
        Alive(); ArgumentNullException.ThrowIfNull(canvas);
        if (source is null || source.Handle == IntPtr.Zero) throw new InvalidOperationException("Set a live source before drawing");
        if (new[] { bounds.Left, bounds.Top, bounds.Right, bounds.Bottom }.Any(v => !float.IsFinite(v)) || bounds.Width <= 0 || bounds.Height <= 0)
            throw new ArgumentOutOfRangeException(nameof(bounds));
    }
    private static void Set(Dictionary<string, float[]> v, string name, params float[] values) => v[name] = values;
    private static float Interpolate((float X, float Y)[] table, float x)
    {
        var i = 0; while (i < table.Length - 2 && table[i + 1].X <= x) i++;
        var a = table[i]; var b = table[i + 1]; return a.Y + (b.Y - a.Y) * (x - a.X) / (b.X - a.X);
    }
    public void Dispose() { if (!disposed) { disposed = true; source = null; ownedSource?.Dispose(); ownedSource = null; wideImage?.Dispose(); wideImage = null; paint.Dispose(); material.Dispose(); } }
}

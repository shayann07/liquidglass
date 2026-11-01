// Apache-2.0. Native Skia host adapter; material mathematics remain in production SkSL.
using SkiaSharp;

namespace LiquidGlass.Skia;

/// <summary>A surface's options. <c>LightX</c>/<c>LightY</c> point toward the light (y up is negative):
/// the default is Compose's <c>GlassLight.Default</c>, as <c>Glass.Regular</c> binds it; (0, -1) is
/// iOS 27's measured overhead light.</summary>
public sealed record GlassSurfaceOptions(bool Dark = false, float TintAmount = 50, float Density = 1,
    float Materialize = 1, float ReducedTransparency = 0, float IncreasedContrast = 0,
    float LightX = -.35f, float LightY = -.94f);

/// <summary>Draw multiple glass surfaces over one borrowed opaque sRGB image on an SKCanvas.
/// Coordinates are device pixels. The host owns input, layout, semantics and frame scheduling.</summary>
public sealed class GlassPainter : IDisposable
{
    private readonly GlassEffectPass material = new("material");
    private GlassEffectPass? contentPass;
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

    public void DrawSurface(SKCanvas canvas, SKRect bounds, float radius = 20, GlassSurfaceOptions? options = null,
        GlassFeedback? feedback = null)
        => DrawSurface(canvas, bounds, [radius, radius, radius, radius], options, feedback);

    public void DrawSurface(SKCanvas canvas, SKRect bounds, IReadOnlyList<float> radii, GlassSurfaceOptions? options = null,
        GlassFeedback? feedback = null)
    {
        Ready(canvas, bounds); ArgumentNullException.ThrowIfNull(radii);
        options ??= new();
        if (radii.Count != 4 || radii.Any(r => !float.IsFinite(r) || r < 0)) throw new ArgumentException("Four nonnegative radii required", nameof(radii));
        var v = SurfaceUniforms(bounds, radii, options);
        var origin = new SKPoint(bounds.Left - 2, bounds.Top - 2);
        using var shader = Backdrop(origin, 10 * options.Density, out var strip);
        Set(v, "uWideStrip", strip);
        Draw(canvas, v, shader, origin, bounds, feedback, options.Density);
    }

    private Dictionary<string, float[]> SurfaceUniforms(SKRect bounds, IReadOnlyList<float> radii, GlassSurfaceOptions o)
    {
        if (!float.IsFinite(o.Density) || o.Density <= 0 || !float.IsFinite(o.TintAmount) || o.TintAmount is < 0 or > 100 ||
            new[] { o.Materialize, o.ReducedTransparency, o.IncreasedContrast }.Any(v => !float.IsFinite(v) || v is < 0 or > 1) ||
            !float.IsFinite(o.LightX) || !float.IsFinite(o.LightY))
            throw new ArgumentOutOfRangeException(nameof(o));
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
        Set(v, "uLight", o.LightX, o.LightY);
        Set(v, "uScale", Math.Clamp((Math.Min(bounds.Width, bounds.Height) / o.Density - 56) / 264, 0, 1));
        Set(v, "uMaterialize", o.Materialize); Set(v, "uFrost", o.ReducedTransparency); Set(v, "uContrast", o.IncreasedContrast);
        return v;
    }

    /// <summary>Draw a Calm navigation bar's glass in the Compose Calm bar material
    /// (GlassTabBarStyle.Calm; its held lift follows the selector's <paramref name="formation"/>, or for
    /// any touch <paramref name="touch"/>, <see cref="CalmNavigation.Touch"/>).
    /// Device px; <c>options.Density</c> is px per dp, <c>options.TintAmount</c> is not used. <paramref name="clear"/>
    /// draws GlassTabBarStyle.CalmClear's clearer bar. <paramref name="fuse"/> (<see cref="AccessoryFuse.Compute"/>,
    /// device px) draws the neck to a free button beside the bar.</summary>
    public void DrawCalmBar(SKCanvas canvas, SKRect bar, float formation = 0, GlassSurfaceOptions? options = null, float touch = 0,
        bool clear = false, float[]? fuse = null)
    {
        Ready(canvas, bar);
        options ??= new(Dark: true);
        if (!float.IsFinite(formation) || !float.IsFinite(options.Density) || options.Density <= 0)
            throw new ArgumentOutOfRangeException(nameof(formation));
        if (fuse is not null && (fuse.Length != 6 || !fuse.All(float.IsFinite)))
            throw new ArgumentOutOfRangeException(nameof(fuse), "Six finite numbers required (AccessoryFuse.Compute)");
        // The fused outline reaches past the bar to the button: draw over it, and the neck's bow (a quarter
        // of the smoothing) beyond, as Compose's recordPad does.
        var fusing = fuse is not null && fuse[5] > 0;
        var pad = fusing
            ? MathF.Ceiling(Math.Max(2, Math.Max(Math.Abs(fuse![0]) + fuse[2] - bar.Width / 2, Math.Abs(fuse[1]) + fuse[3] - bar.Height / 2)) + fuse[5] / 4 + 2)
            : 2;
        var v = ClearUniforms(material, bar.Width, bar.Height, pad, 1);
        Apply(v, CalmSelectorMaterial.Bar(options.Dark, formation, touch, clear), options);
        Set(v, "uRadii", Enumerable.Repeat(bar.Height / 2, 4).ToArray()); Set(v, "uRefractBand", .6f * bar.Height / 2);
        Set(v, "uFormation", 0); Set(v, "uScale", Math.Clamp((Math.Min(bar.Width, bar.Height) / options.Density - 56) / 264, 0, 1));
        if (fusing) { Set(v, "uFuse", fuse![0], fuse[1], fuse[2], fuse[3]); Set(v, "uFuseShape", fuse[4], fuse[5]); }
        var origin = new SKPoint(bar.Left - pad, bar.Top - pad);
        using var shader = Backdrop(origin, v["uWideKernel"][0], out var strip);
        Set(v, "uWideStrip", v["uWideKernel"][0] >= 1 ? strip : 0);
        Draw(canvas, v, shader, origin);
    }

    /// <summary>Draw a navigation selector's deformed body over the bar rect (device px). <paramref name="pose"/>
    /// comes from <see cref="CalmSelectorController.Render"/> in the same device px as <paramref name="bar"/>;
    /// <paramref name="formation"/>, <paramref name="transit"/> and <paramref name="heldTravel"/> are the
    /// controller's. The material is the Compose Calm selector's own (<see cref="CalmSelectorMaterial"/>).
    /// Like Compose, the lens looks through the bar: pass <paramref name="through"/>, an image the size of the
    /// source holding the backdrop with the bar's glass drawn over it, and a share of the raw backdrop is added
    /// on top; without it the lens sees the backdrop alone. <c>options.TintAmount</c> is not used.</summary>
    public void DrawSelector(SKCanvas canvas, SKRect bar, CalmSelectorPose pose, float formation = 0, GlassSurfaceOptions? options = null,
        float transit = 0, float heldTravel = 0, SKImage? through = null, float[]? fuse = null, bool shadow = true)
    {
        Ready(canvas, bar);
        if (!float.IsFinite(formation) || !float.IsFinite(transit) || !float.IsFinite(heldTravel))
            throw new ArgumentOutOfRangeException(nameof(formation));
        if (new[] { pose.A, pose.AInv, pose.C, pose.D }.Any(v => v is null || v.Length != 4 || v.Any(x => !float.IsFinite(x))))
            throw new ArgumentException("Pose from CalmSelectorController.Render required", nameof(pose));
        if (through is not null && (through.Width != source!.Width || through.Height != source.Height))
            throw new ArgumentException("The through image must be the size of the source", nameof(through));
        options ??= new(Dark: true);
        if (!float.IsFinite(options.Density) || options.Density <= 0) throw new ArgumentOutOfRangeException(nameof(options));
        if (fuse is not null && (fuse.Length != 6 || !fuse.All(float.IsFinite)))
            throw new ArgumentOutOfRangeException(nameof(fuse), "Six finite numbers required (AccessoryFuse.LensFuse)");
        // The body may stand proud of the bar while held and protrude past its ends in transit.
        float mx = bar.Height * .6f, my = bar.Height * .4f;
        var node = SKRect.Create(bar.Left - mx, bar.Top - my, bar.Width + 2 * mx, bar.Height + 2 * my);
        // A neck to a free button (AccessoryFuse.LensFuse) reaches past the node: draw over it, and its bow beyond.
        var fusing = fuse is not null && fuse[5] > 0;
        var pad = fusing
            ? MathF.Ceiling(Math.Max(2, Math.Max(Math.Abs(fuse![0]) + fuse[2] - node.Width / 2, Math.Abs(fuse[1]) + fuse[3] - node.Height / 2)) + fuse[5] / 4 + 2)
            : 2;
        var calm = CalmSelectorMaterial.Selector(options.Dark, formation, transit, heldTravel);
        var v = ClearUniforms(material, node.Width, node.Height, pad, 1);
        Apply(v, calm, options);
        Set(v, "uFormation", calm.Formation);
        Set(v, "uBodyKind", 2); Set(v, "uPoseA", pose.A); Set(v, "uPoseAInv", pose.AInv); Set(v, "uPoseD", pose.D); Set(v, "uPoseE", pose.E ?? [0, 3, 0, 0]);
        Set(v, "uPoseC", pose.C[0] - bar.Width / 2, pose.C[1] - bar.Height / 2, pose.C[2], pose.C[3]);
        // GlassStyle.bodyBandScale: the lens refracts over this share of the body's own band.
        Set(v, "uRefractBand", pose.D[3] * BandScale(calm)); Set(v, "uBandBottom", BandScale(calm) > 1 ? .3f : 0f);
        Set(v, "uRadii", Enumerable.Repeat(Math.Min(node.Width, node.Height) / 2, 4).ToArray());
        var size = Math.Clamp((Math.Min(node.Width, node.Height) / options.Density - 56) / 264, 0, 1);
        Set(v, "uScale", size);
        if (fusing) { Set(v, "uFuse", fuse![0], fuse[1], fuse[2], fuse[3]); Set(v, "uFuseShape", fuse[4], fuse[5]); }
        // GlassStyle.contactShadow on the lens: cast by its body, 0.14 strong (glassShadow), on the page under it
        // and, at its inside share, in what it looks through (light Calm's held lens).
        Shadow? cast = null;
        var castAlpha = shadow ? .14f * Math.Clamp(Host(calm, "contactShadow", 0), 0, 1) : 0;
        if (castAlpha > 0)
        {
            float hx = pose.D[0] + pose.D[1] * pose.D[2], hy = pose.D[1];
            float ex = Math.Abs(pose.A[0]) * hx + Math.Abs(pose.A[1]) * hy, ey = Math.Abs(pose.A[2]) * hx + Math.Abs(pose.A[3]) * hy;
            var blur = Host(calm, "contactShadowBlur", -1); var offset = Host(calm, "contactShadowOffset", -1);
            cast = new Shadow(SKRect.Create(bar.Left + pose.C[0] - ex, bar.Top + pose.C[1] - ey, 2 * ex, 2 * ey), Math.Min(ex, ey),
                castAlpha, Math.Clamp(Host(calm, "contactShadowInside", 1), 0, 1),
                (blur >= 0 ? blur : 18 * (.6f + .6f * size)) * options.Density,
                (offset >= 0 ? offset : 4 * (.5f + .9f * size)) * options.Density, bar);
            DrawShadow(canvas, cast, castAlpha);
        }
        var origin = new SKPoint(node.Left - pad, node.Top - pad);
        using var view = through is null && cast is null ? null : ComposeThrough(through, calm.RawShare, calm.RawBlur * options.Density, cast);
        using var shader = Backdrop(origin, v["uWideKernel"][0], out var strip, view);
        Set(v, "uWideStrip", v["uWideKernel"][0] >= 1 ? strip : 0);
        Draw(canvas, v, shader, origin);
    }

    /// <summary>Draw the selected ink inside the selector, as Compose's content pass does. <paramref name="ink"/>
    /// is the bar's items drawn in their selected appearance on a transparent ground, in device px with its
    /// origin at the bar's top-left. Only what lies under the selector shows, refracted, colour-split and folded
    /// by the same body, map and band as <see cref="DrawSelector"/>; draw it right after.</summary>
    public void DrawSelectorInk(SKCanvas canvas, SKRect bar, CalmSelectorPose pose, SKImage ink, float formation = 0,
        GlassSurfaceOptions? options = null, float transit = 0, float heldTravel = 0)
    {
        Ready(canvas, bar); ArgumentNullException.ThrowIfNull(ink);
        if (!float.IsFinite(formation) || !float.IsFinite(transit) || !float.IsFinite(heldTravel))
            throw new ArgumentOutOfRangeException(nameof(formation));
        if (new[] { pose.A, pose.AInv, pose.C, pose.D }.Any(v => v is null || v.Length != 4 || v.Any(x => !float.IsFinite(x))))
            throw new ArgumentException("Pose from CalmSelectorController.Render required", nameof(pose));
        options ??= new(Dark: true);
        if (!float.IsFinite(options.Density) || options.Density <= 0) throw new ArgumentOutOfRangeException(nameof(options));
        float mx = bar.Height * .6f, my = bar.Height * .4f;
        var node = SKRect.Create(bar.Left - mx, bar.Top - my, bar.Width + 2 * mx, bar.Height + 2 * my);
        var calm = CalmSelectorMaterial.Selector(options.Dark, formation, transit, heldTravel);
        var m = ClearUniforms(material, node.Width, node.Height, 2, 1);
        Apply(m, calm, options);
        Set(m, "uFormation", calm.Formation);
        Set(m, "uBodyKind", 2); Set(m, "uPoseA", pose.A); Set(m, "uPoseAInv", pose.AInv); Set(m, "uPoseD", pose.D); Set(m, "uPoseE", pose.E ?? [0, 3, 0, 0]);
        Set(m, "uPoseC", pose.C[0] - bar.Width / 2, pose.C[1] - bar.Height / 2, pose.C[2], pose.C[3]);
        Set(m, "uRefractBand", pose.D[3] * BandScale(calm)); Set(m, "uBandBottom", BandScale(calm) > 1 ? .3f : 0f);
        Set(m, "uRadii", Enumerable.Repeat(Math.Min(node.Width, node.Height) / 2, 4).ToArray());
        Set(m, "uScale", Math.Clamp((Math.Min(node.Width, node.Height) / options.Density - 56) / 264, 0, 1));
        // The content pass shares the material's geometry and optics; it emits the ink premultiplied and
        // clipped to the body (uEndpointAlpha 0), drawn over the material.
        contentPass ??= new GlassEffectPass("content");
        var v = contentPass.CreateUniforms();
        foreach (var key in v.Keys.ToList()) if (m.TryGetValue(key, out var value)) v[key] = value;
        Set(v, "uEndpointAlpha", 0);
        Set(v, "uInkSplit", calm.Values["uInkSplit"]); Set(v, "uHeldInk", calm.Values["uHeldInk"]);
        var origin = new SKPoint(node.Left - 2, node.Top - 2);
        // The ink's origin is the bar's: in the padded node frame that is (mx + pad, my + pad).
        using var content = ink.ToShader(SKShaderTileMode.Decal, SKShaderTileMode.Decal, Sampling, SKMatrix.CreateTranslation(mx + 2, my + 2));
        using var shader = contentPass.CreateShader(v, new Dictionary<string, SKShader> { { "content", content }, { "field", content } });
        paint.Shader = shader;
        canvas.Save();
        try { canvas.Translate(origin.X, origin.Y); canvas.DrawRect(0, 0, node.Width + 4, node.Height + 4, paint); }
        finally { canvas.Restore(); paint.Shader = null; }
    }

    // The material pass's share of a Calm material, in px (ink-only terms belong to the content pass).
    private static void Apply(Dictionary<string, float[]> v, CalmMaterial calm, GlassSurfaceOptions o)
    {
        foreach (var (key, value) in calm.Values)
            // The bar's top shade (#128) is [strength, reach dp]: only its reach is a length.
            if (v.ContainsKey(key)) v[key] = key == "uTopShade" ? [value[0], value[1] * o.Density]
                : CalmSelectorMaterial.DpFields.Contains(key) ? value.Select(x => x * o.Density).ToArray() : value;
        Set(v, "uMaterialize", o.Materialize); Set(v, "uFrost", o.ReducedTransparency); Set(v, "uContrast", o.IncreasedContrast);
    }

    private sealed record Shadow(SKRect Rect, float Radius, float Alpha, float Inside, float Blur, float Offset, SKRect Bar);

    private static float Host(CalmMaterial calm, string key, float fallback) =>
        calm.Values.TryGetValue(key, out var v) && v.Length > 0 ? v[0] : fallback;
    private static float BandScale(CalmMaterial calm) => Math.Clamp(Host(calm, "bodyBandScale", 1), .25f, 4);

    // Compose's BlurEffect takes a radius; Skia's blur a sigma (Compose's convertRadiusToSigma).
    private static float Sigma(float radius) => radius > 0 ? .57735f * radius + .5f : 0;

    // A contact shadow: the caster's rounded rect in black at `alpha`, blurred and moved down (device px).
    private static void DrawShadow(SKCanvas target, Shadow s, float alpha, bool outsideBar = false)
    {
        if (!(alpha > 0)) return;
        using var blur = s.Blur > .01f ? SKImageFilter.CreateBlur(Sigma(s.Blur), Sigma(s.Blur), SKShaderTileMode.Decal) : null;
        using var paint = new SKPaint { Color = new SKColor(0, 0, 0, (byte)Math.Round(Math.Clamp(alpha, 0, 1) * 255)), IsAntialias = true, ImageFilter = blur };
        target.Save();
        if (outsideBar)
        {
            using var bar = new SKRoundRect(s.Bar, s.Bar.Height / 2, s.Bar.Height / 2);
            target.ClipRoundRect(bar, SKClipOperation.Difference, true);
        }
        var r = s.Rect; r.Offset(0, s.Offset);
        using var body = new SKRoundRect(r, s.Radius, s.Radius);
        target.DrawRoundRect(body, paint);
        target.Restore();
    }

    // The backdrop with the glass the lens looks through over it, plus rawShare of the raw page,
    // blurred by rawBlur (a Compose radius, device px) as Compose's GlassStyle.rawBlur. With a shadow,
    // the lens's own contact shadow at its inside share, where the bar does not cover it.
    private SKImage ComposeThrough(SKImage? through, float rawShare, float rawBlur = 0, Shadow? shadow = null)
    {
        using var surface = SKSurface.Create(new SKImageInfo(source!.Width, source.Height, SKColorType.Rgba8888, SKAlphaType.Premul))
            ?? throw new InvalidOperationException("Cannot allocate the selector backdrop");
        surface.Canvas.Clear(SKColors.Black);
        surface.Canvas.DrawImage(through ?? source, 0, 0, Sampling);
        if (shadow is not null) DrawShadow(surface.Canvas, shadow, shadow.Alpha * shadow.Inside, true);
        if (through is not null && rawShare > .001f)
        {
            using var blur = rawBlur > .01f ? SKImageFilter.CreateBlur(Sigma(rawBlur), Sigma(rawBlur), SKShaderTileMode.Clamp) : null;
            using var add = new SKPaint { BlendMode = SKBlendMode.Plus, Color = SKColors.White.WithAlpha((byte)Math.Round(rawShare * 255)), ImageFilter = blur };
            surface.Canvas.DrawImage(source, 0, 0, Sampling, add);
        }
        surface.Canvas.Flush();
        return surface.Snapshot();
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
        Set(v, "uPoseA", 1, 0, 0, 1); Set(v, "uPoseAInv", 1, 0, 0, 1); Set(v, "uPoseD", 1, 1, 1, 0); Set(v, "uPoseE", 0, 3, 0, 0);
        return v;
    }

    private SKShader Backdrop(SKPoint origin, float sigma, out float strip, SKImage? view = null)
    {
        // A one-off view (the selector looking through the bar) gets its own tone image.
        if (view is not null)
        {
            using var once = Tone(view, sigma);
            return Compose(view, once, origin, out strip);
        }
        if (wideImage is null || wideSigma != sigma)
        {
            wideImage?.Dispose(); wideImage = null;
            wideImage = Tone(source!, sigma); wideSigma = sigma;
        }
        return Compose(source!, wideImage, origin, out strip);
    }

    private static SKImage Tone(SKImage image, float sigma)
    {
        sigma = Math.Max(sigma, 1);
        using var surface = SKSurface.Create(new SKImageInfo((image.Width + 3) / 4, (image.Height + 3) / 4, SKColorType.Rgba8888, SKAlphaType.Premul))
            ?? throw new InvalidOperationException("Cannot allocate wide tone image");
        using var sharp = image.ToShader(SKShaderTileMode.Clamp, SKShaderTileMode.Clamp, Sampling);
        using var filter = SKImageFilter.CreateBlur(sigma, sigma, SKShaderTileMode.Clamp);
        using var p = new SKPaint { Shader = sharp, ImageFilter = filter };
        surface.Canvas.Clear(SKColors.Transparent); surface.Canvas.Scale(.25f, .25f); surface.Canvas.DrawPaint(p); surface.Canvas.Flush();
        return surface.Snapshot();
    }

    private static SKShader Compose(SKImage image, SKImage tone, SKPoint origin, out float strip)
    {
        using var sharpShader = image.ToShader(SKShaderTileMode.Clamp, SKShaderTileMode.Clamp, Sampling);
        using var wideShader = tone.ToShader(SKShaderTileMode.Clamp, SKShaderTileMode.Clamp, Sampling, SKMatrix.CreateScale(4, 4));
        var threshold = Math.Max(1, image.Height - origin.Y + 1);
        strip = threshold + Math.Max(0, origin.Y / 4) + 1;
        using var uniforms = new SKRuntimeEffectUniforms(BackdropEffect);
        uniforms["origin"] = origin; uniforms["strip"] = strip; uniforms["threshold"] = threshold;
        using var children = new SKRuntimeEffectChildren(BackdropEffect);
        children["sharp"] = sharpShader; children["wide"] = wideShader;
        return BackdropEffect.ToShader(uniforms, children);
    }

    private void Draw(SKCanvas canvas, Dictionary<string, float[]> v, SKShader backdrop, SKPoint origin,
        SKRect bounds = default, GlassFeedback? feedback = null, float density = 1)
    {
        var transform = SKMatrix.Identity;
        if (feedback is GlassFeedback f)
        {
            var values = new[] { f.A, f.B, f.C, f.D, f.OffsetX * density, f.OffsetY * density };
            if (values.Any(x => !double.IsFinite(x) || Math.Abs(x) > float.MaxValue) || f.A * f.D - f.B * f.C <= 0)
                throw new ArgumentOutOfRangeException(nameof(feedback), "Finite orientation-preserving feedback required");
            var tx = bounds.MidX + f.OffsetX * density - f.A * bounds.MidX - f.B * bounds.MidY;
            var ty = bounds.MidY + f.OffsetY * density - f.C * bounds.MidX - f.D * bounds.MidY;
            if (!double.IsFinite(tx) || !double.IsFinite(ty) || Math.Abs(tx) > float.MaxValue || Math.Abs(ty) > float.MaxValue)
                throw new ArgumentOutOfRangeException(nameof(feedback));
            transform = new SKMatrix((float)f.A, (float)f.B, (float)tx, (float)f.C, (float)f.D, (float)ty, 0, 0, 1);
        }
        Set(v, "uBackdrop", -origin.X, -origin.Y, source!.Width - origin.X, source.Height - origin.Y);
        using var shader = material.CreateShader(v, new Dictionary<string, SKShader> { { "content", backdrop }, { "field", backdrop } });
        paint.Shader = shader;
        canvas.Save();
        try
        {
            if (feedback.HasValue) canvas.Concat(transform);
            canvas.Translate(origin.X, origin.Y);
            canvas.DrawRect(0, 0, v["uSize"][0] + 2 * v["uPad"][0], v["uSize"][1] + 2 * v["uPad"][0], paint);
        }
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
    public void Dispose() { if (!disposed) { disposed = true; source = null; ownedSource?.Dispose(); ownedSource = null; wideImage?.Dispose(); wideImage = null; paint.Dispose(); material.Dispose(); contentPass?.Dispose(); } }
}

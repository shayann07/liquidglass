// Apache-2.0. The clear-lens preset (#191): on by default (Compose's GlassStyle.clearLens); Lighting false is the bare lens.
using System.Runtime.InteropServices;
using LiquidGlass.Skia;
using SkiaSharp;

internal static class LensChecks
{
    private static void Check(bool condition, string message) { if (!condition) throw new Exception(message); }
    private static void Throws<T>(Action action) where T : Exception
    {
        try { action(); } catch (T) { return; }
        throw new Exception($"Expected {typeof(T).Name}");
    }
    private static readonly SKSamplingOptions Linear = new(SKFilterMode.Linear, SKMipmapMode.None);

    private static byte[] Render(int level, Action<GlassPainter, SKCanvas> draw)
    {
        const int size = 320;
        using var bitmap = new SKBitmap(size, size, SKColorType.Rgba8888, SKAlphaType.Opaque);
        bitmap.Erase(new SKColor((byte)level, (byte)level, (byte)level));
        using var image = SKImage.FromBitmap(bitmap);
        using var surface = SKSurface.Create(new SKImageInfo(size, size, SKColorType.Rgba8888, SKAlphaType.Premul));
        using var painter = new GlassPainter();
        painter.SetSource(image);
        surface.Canvas.DrawImage(image, 0, 0, Linear);
        draw(painter, surface.Canvas);
        using var read = new SKBitmap(size, size, SKColorType.Rgba8888, SKAlphaType.Premul);
        Check(surface.ReadPixels(read.Info, read.GetPixels(), read.RowBytes, 0, 0), "Pixel read failed");
        var bytes = new byte[size * size * 4]; Marshal.Copy(read.GetPixels(), bytes, 0, bytes.Length); return bytes;
    }

    public static object Run()
    {
        var bounds = SKRect.Create(70, 70, 180, 180);
        // The uniforms: the preset's authored values, dp terms scaled, the bare set untouched.
        using (var effect = new GlassEffectPass("material"))
        {
            var bare = GlassPainter.ClearUniforms(effect, 180, 180, 2, 1.4f);
            var lens = GlassPainter.ClearLensUniforms(effect, 180, 180, 2, 1.4f, 2);
            Check(lens.Keys.OrderBy(k => k).SequenceEqual(bare.Keys.OrderBy(k => k)), "The preset binds other uniforms than the bare set");
            Check(Math.Abs(lens["uBevel"][0] - 2.4f) < 1e-5f && lens["uRefractDepth"][0] == 28 && Math.Abs(lens["uRefractBand"][0] - 54) < 1e-4f, "dp terms did not scale");
            Check(lens["uSpecular"][0] == .28f && lens["uFresnel"][0] == .02f && lens["uCounterLight"][0] == .35f && lens["uProfile"][0] == 3, "Preset values");
            Check(Math.Abs(lens["uTint"][3] - 4f / 255) < 1e-7f && lens["uTint"][0] == 1, "Preset tint");
            Check(lens["uLight"].SequenceEqual(new[] { 0f, -1f }), "Preset light");
            Check(bare["uBevel"][0] == 2 && bare["uSpecular"][0] == 0 && bare["uTint"][3] == 0, "The bare set changed");
            Throws<ArgumentOutOfRangeException>(() => GlassPainter.ClearLensUniforms(effect, 180, 180, 2, 1.4f, 0));
        }
        // On by default: the no-options overload and a default GlassLensOptions are the preset; Lighting false is the bare lens.
        var plain = Render(128, (p, c) => p.DrawLens(c, bounds, 1.4f));
        var dflt = Render(128, (p, c) => p.DrawLens(c, bounds, new GlassLensOptions(), 1.4f));
        var on = Render(128, (p, c) => p.DrawLens(c, bounds, new GlassLensOptions(Lighting: true), 1.4f));
        var bareLens = Render(128, (p, c) => p.DrawLens(c, bounds, new GlassLensOptions(Lighting: false), 1.4f));
        var bareExtras = Render(128, (p, c) => p.DrawLens(c, bounds, new GlassLensOptions(Lighting: false, Shadow: true, Density: 3), 1.4f));
        Check(plain.SequenceEqual(dflt) && dflt.SequenceEqual(on), "The default lens is not the preset");
        Check(!bareLens.SequenceEqual(dflt) && bareLens.SequenceEqual(bareExtras), "Lighting false is not the bare lens");
        Check(bareLens[160 * 320 * 4 + 160 * 4] == 128 && bareLens[(71 * 320 + 160) * 4] == 128, "The bare lens drew something over a flat page");
        Check(plain[(71 * 320 + 160) * 4] > 128, "The default lens has no rim");

        // On: a rim over flat grey and black, a clean interior, a contact shadow under the lens.
        int Rim(byte[] px, int level, int dy)
        {
            var best = -255;
            for (var r = 80; r <= 90; r++) best = Math.Max(best, px[((160 + dy * r) * 320 + 160) * 4] - level);
            return best;
        }
        foreach (var (level, top, bottom) in new[] { (128, 40, 10), (0, 25, 5) })
        {
            var px = Render(level, (p, c) => p.DrawLens(c, bounds, new GlassLensOptions(Lighting: true), 1.25f));
            Check(Rim(px, level, -1) >= top, $"Top rim over {level}: {Rim(px, level, -1)}");
            Check(Rim(px, level, 1) >= bottom, $"Bottom rim over {level}: {Rim(px, level, 1)}");
            for (var r = 0; r < 70; r += 5) Check(Math.Abs(px[(160 * 320 + 160 + r) * 4] - level) <= 4, $"Interior at {r} over {level}");
            if (level == 128)
            {
                double sum = 0;
                for (var y = 251; y < 271; y++) for (var x = 130; x < 190; x++) sum += px[(y * 320 + x) * 4];
                Check(sum / (20 * 60) < 128, "No contact shadow under the lens");
            }
        }
        var noShadow = Render(128, (p, c) => p.DrawLens(c, bounds, new GlassLensOptions(Lighting: true, Shadow: false), 1.25f));
        Check(noShadow[(262 * 320 + 160) * 4] == 128, "Shadow false still cast a shadow");
        Throws<ArgumentOutOfRangeException>(() => Render(128, (p, c) => p.DrawLens(c, bounds, new GlassLensOptions(Density: 0))));
        return new { check = "clear-lens preset: default, with Lighting false the bare lens, rim over grey and black, clean interior, contact shadow", passed = true };
    }
}

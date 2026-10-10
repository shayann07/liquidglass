// Apache-2.0. Native executable checks and example; no UI framework, browser or Compose.
using System.Runtime.InteropServices;
using System.Text.Json;
using LiquidGlass.Skia;
using SkiaSharp;

var output = Path.GetFullPath(args.FirstOrDefault() ?? "artifacts");
Directory.CreateDirectory(output);
var results = new List<object>();
void Check(bool condition, string message) { if (!condition) throw new Exception(message); }
void Throws<T>(Action action) where T : Exception
{
    try { action(); } catch (T) { return; }
    throw new Exception($"Expected {typeof(T).Name}");
}
SKImage Image(int width, int height, Func<int, int, SKColor> pixel)
{
    using var bitmap = new SKBitmap(new SKImageInfo(width, height, SKColorType.Rgba8888, SKAlphaType.Opaque));
    for (var y = 0; y < height; y++) for (var x = 0; x < width; x++) bitmap.SetPixel(x, y, pixel(x, y));
    return SKImage.FromBitmap(bitmap);
}
byte[] Pixels(SKSurface surface, int width, int height)
{
    using var bitmap = new SKBitmap(new SKImageInfo(width, height, SKColorType.Rgba8888, SKAlphaType.Premul));
    Check(surface.ReadPixels(bitmap.Info, bitmap.GetPixels(), bitmap.RowBytes, 0, 0), "Pixel read failed");
    var bytes = new byte[width * height * 4]; Marshal.Copy(bitmap.GetPixels(), bytes, 0, bytes.Length); return bytes;
}
void Save(SKSurface surface, string name)
{
    using var image = surface.Snapshot(); using var data = image.Encode(SKEncodedImageFormat.Png, 100);
    using var file = File.Create(Path.Combine(output, name)); data.SaveTo(file);
}

foreach (var pass in new[] { "material", "content", "endpoint" })
{
    using var effect = new GlassEffectPass(pass);
    var file = File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "shaders", pass + ".sksl"));
    Check(effect.Source.Replace("\r\n", "\n") == file.Replace("\r\n", "\n"), "Embedded production shader drift");
}
results.Add(new { check = "three embedded production passes compile and match source", passed = true });

using (var effect = new GlassEffectPass("material"))
using (var source = Image(128, 128, (x, y) => new SKColor((byte)x, (byte)y, 64)))
using (var shader = source.ToShader(SKShaderTileMode.Clamp, SKShaderTileMode.Clamp, new SKSamplingOptions(SKFilterMode.Linear, SKMipmapMode.None)))
using (var surface = SKSurface.Create(new SKImageInfo(128, 128, SKColorType.Rgba8888, SKAlphaType.Premul)))
using (var paint = new SKPaint())
{
    var inputs = new Dictionary<string, SKShader> { { "content", shader }, { "field", shader } };
    for (var profile = 0; profile < 4; profile++)
    {
        var u = GlassPainter.ClearUniforms(effect, 64, 64);
        u["uProfile"] = [profile]; u["uHeldMagnification"] = [.2f]; u["uHeldGlow"] = [0];
        using var glass = effect.CreateShader(u, inputs); paint.Shader = glass;
        surface.Canvas.Clear(SKColors.Transparent); surface.Canvas.DrawPaint(paint); surface.Canvas.Flush(); paint.Shader = null;
        var actual = Pixels(surface, 128, 128); var expected = File.ReadAllBytes(Path.Combine(AppContext.BaseDirectory, "fixtures", $"profile-{profile}.rgba"));
        var worst = actual.Zip(expected, (a, b) => Math.Abs(a - b)).Max();
        Check(actual.Length == expected.Length && worst <= 3, $"Profile{profile}: native JVM reference differs by{worst}");
        results.Add(new { check = $"profile{profile} versus independent JVM pixels", maxChannelError = worst, passed = true });
    }
    var invalid = GlassPainter.ClearUniforms(effect, 64, 64); invalid["uSize"] = [float.NaN, 64];
    Throws<ArgumentException>(() => effect.CreateShader(invalid, inputs));
    invalid = GlassPainter.ClearUniforms(effect, 64, 64); invalid["typo"] = [1];
    Throws<ArgumentException>(() => effect.CreateShader(invalid, inputs));
    Throws<ArgumentException>(() => effect.CreateShader(GlassPainter.ClearUniforms(effect, 64, 64), new Dictionary<string, SKShader> { { "content", shader } }));
    effect.Dispose(); Throws<ObjectDisposedException>(() => effect.CreateUniforms());
}
results.Add(new { check = "invalid uniform, missing child and disposed effect rejected", passed = true });

using (var source = Image(256, 192, (x, y) => new SKColor((byte)x, (byte)y, 64)))
using (var surface = SKSurface.Create(new SKImageInfo(256, 192, SKColorType.Rgba8888, SKAlphaType.Premul)))
using (var painter = new GlassPainter())
{
    Throws<InvalidOperationException>(() => painter.DrawLens(surface.Canvas, SKRect.Create(20, 20, 100, 100)));
    painter.SetSource(source);
    surface.Canvas.DrawImage(source, 0, 0, new SKSamplingOptions(SKFilterMode.Linear, SKMipmapMode.None));
    var before = Pixels(surface, 256, 192); var matrix = surface.Canvas.TotalMatrix; var saves = surface.Canvas.SaveCount;
    painter.DrawLens(surface.Canvas, SKRect.Create(40, 20, 120, 120), 1);
    var identity = Pixels(surface, 256, 192);
    Check(before.SequenceEqual(identity), "Identity lens changed the backdrop");
    painter.DrawLens(surface.Canvas, SKRect.Create(40, 20, 120, 120), 1.25f);
    var zoom = Pixels(surface, 256, 192);
    int Red(int x, int y) => zoom[(y * 256 + x) * 4];
    Check(Math.Abs(Red(100, 80) - 100) <= 1, "Lens local origin does not map to backdrop");
    var slope = (Red(105, 80) - Red(95, 80)) / 10f;
    Check(slope is >= .7f and <= .9f, "Unexpected centre magnification");
    for (var x = 43; x < 157; x++) Check(Red(x + 1, 80) - Red(x, 80) >= -1, "Reversed lens source map");
    Check(surface.Canvas.TotalMatrix == matrix && surface.Canvas.SaveCount == saves, "Painter changed host canvas state");
    Throws<ArgumentOutOfRangeException>(() => painter.DrawSurface(surface.Canvas, SKRect.Create(20, 20, 80, 40), 10, new(Density: 0)));
    Check(surface.Canvas.TotalMatrix == matrix && surface.Canvas.SaveCount == saves, "Failure leaked canvas state");
    painter.Dispose(); Check(source.Handle != IntPtr.Zero, "Disposal released caller-owned image");
    Throws<ObjectDisposedException>(() => painter.SetSource(source));
}
results.Add(new { check = "identity, zoom, monotonic source map, borrowed image and canvas state", passed = true });

using (var surface = SKSurface.Create(new SKImageInfo(320, 200, SKColorType.Rgba8888, SKAlphaType.Premul)))
using (var painter = new GlassPainter())
using (var first = Image(320, 200, (_, _) => new SKColor(32, 64, 96)))
using (var second = Image(320, 200, (_, _) => new SKColor(180, 130, 90)))
{
    var bounds = SKRect.Create(40, 40, 200, 100);
    painter.SetSource(first); surface.Canvas.DrawImage(first, 0, 0, new SKSamplingOptions(SKFilterMode.Linear, SKMipmapMode.None)); painter.DrawSurface(surface.Canvas, bounds, 20, new(Dark: true));
    var a = Pixels(surface, 320, 200); var p = (90 * 320 + 140) * 4;
    for (var c = 0; c < 3; c++) Check(Math.Abs(a[p + c] - ((32 + 32 * c) * (1 - 115f / 255) + 35)) <= 2, "Dark material unexpected measured tone");
    surface.Canvas.DrawImage(first, 0, 0, new SKSamplingOptions(SKFilterMode.Linear, SKMipmapMode.None)); painter.DrawSurface(surface.Canvas, bounds, 20, new(Dark: true));
    Check(a.SequenceEqual(Pixels(surface, 320, 200)), "Cached repeated draw changed pixels");
    painter.SetSource(second); surface.Canvas.DrawImage(second, 0, 0, new SKSamplingOptions(SKFilterMode.Linear, SKMipmapMode.None)); painter.DrawSurface(surface.Canvas, bounds, 20, new(Dark: true));
    var b = Pixels(surface, 320, 200); Check(b[p] > a[p] + 50, "Source replacement retained stale wide kernel");
    Check(first.Handle != IntPtr.Zero && second.Handle != IntPtr.Zero, "Painter consumed source");
    painter.DrawSurface(surface.Canvas, SKRect.Create(-20, -10, 90, 70), new float[] { 0, 9, 20, 30 }, new(ReducedTransparency: .5f));
    Save(surface, "source-replacement.png");
}
results.Add(new { check = "in-app material, cached redraw, source replacement, partial surface", passed = true });

using (var surface = SKSurface.Create(new SKImageInfo(80, 80, SKColorType.Rgba8888, SKAlphaType.Premul)))
using (var painter = new GlassPainter())
{
    painter.RecordBackdrop(80, 80, c => { using var paint = new SKPaint { Color = new SKColor(140, 80, 190) }; c.DrawRect(0, 0, 80, 80, paint); }, SKColors.Black);
    painter.DrawLens(surface.Canvas, SKRect.Create(10, 10, 60, 60), 1);
    var before = Pixels(surface, 80, 80);
    Throws<InvalidOperationException>(() => painter.RecordBackdrop(80, 80, _ => throw new InvalidOperationException("host paint failed"), SKColors.Black));
    surface.Canvas.Clear(SKColors.Transparent); painter.DrawLens(surface.Canvas, SKRect.Create(10, 10, 60, 60), 1);
    Check(before.SequenceEqual(Pixels(surface, 80, 80)), "Failed host recording replaced source");
}
results.Add(new { check = "host backdrop recording, owned snapshot and atomic failed callback", passed = true });

using (var backdrop = Image(640, 360, (x, y) =>
{
    var grid = x % 20 == 0 || y % 20 == 0;
    return grid ? new SKColor(135, 169, 181) : new SKColor((byte)(18 + x / 20), (byte)(37 + y / 8), (byte)(60 + x / 10));
}))
using (var surface = SKSurface.Create(new SKImageInfo(640, 360, SKColorType.Rgba8888, SKAlphaType.Premul)))
using (var painter = new GlassPainter())
{
    painter.SetSource(backdrop); surface.Canvas.DrawImage(backdrop, 0, 0, new SKSamplingOptions(SKFilterMode.Linear, SKMipmapMode.None));
    painter.DrawLens(surface.Canvas, SKRect.Create(375, 45, 180, 180), 1.4f);
    painter.DrawSurface(surface.Canvas, SKRect.Create(32, 225, 300, 95), 24, new(Dark: true, TintAmount: 50));
    using var paint = new SKPaint { Color = new SKColor(220, 240, 242), IsAntialias = true };
    var withoutText = Pixels(surface, 640, 360);
    using var font = new SKFont(SKTypeface.Default, 20);
    surface.Canvas.DrawText("LiquidGlass · native .NET", 48, 263, SKTextAlign.Left, font, paint);
    using var small = new SKFont(SKTypeface.Default, 13);
    surface.Canvas.DrawText("Shared source. Production SkSL.", 48, 289, SKTextAlign.Left, small, paint);
    var withText = Pixels(surface, 640, 360);
    var changedTextChannels = withoutText.Zip(withText, (a, b) => a != b ? 1 : 0).Sum();
    Check(changedTextChannels > 100, "Native example labels are blank; install a font provider and fonts");
    Save(surface, "atlas-native-dotnet.png");
}
results.Add(new { check = "native example and visible text rendered", passed = true });
File.WriteAllText(Path.Combine(output, "verification.json"), JsonSerializer.Serialize(new { runtime = Environment.Version.ToString(), platform = RuntimeInformation.OSDescription, checks = results }, new JsonSerializerOptions { WriteIndented = true }));
Console.WriteLine(JsonSerializer.Serialize(results, new JsonSerializerOptions { WriteIndented = true }));

// Apache-2.0. In-process .NET MAUI rendering only: the app's own visual tree, no screen capture.
using LiquidGlass.Skia;
using Microsoft.Maui.Media;
using SkiaSharp;
using SkiaSharp.Views.Maui;
using SkiaSharp.Views.Maui.Controls;
using SkiaSharp.Views.Maui.Controls.Hosting;

namespace LiquidGlass.Maui;

/// <summary>Registers SkiaSharp's MAUI handlers, which <see cref="GlassSurface"/> draws through.</summary>
public static class LiquidGlassHosting
{
    /// <summary><c>builder.UseLiquidGlass()</c> in <c>MauiProgram.CreateMauiApp</c>. Calling SkiaSharp's own
    /// <c>UseSkiaSharp()</c> instead is equivalent.</summary>
    public static MauiAppBuilder UseLiquidGlass(this MauiAppBuilder builder)
    {
        ArgumentNullException.ThrowIfNull(builder);
        return builder.UseSkiaSharp();
    }
}

/// <summary>The content glass refracts: a snapshot of one of the app's own MAUI elements (a page's
/// content, a list's content), drawn from its native view (Android View.Draw, WinUI RenderTargetBitmap). It captures by itself once the
/// element is loaded and drawn, and again when its size changes. Glass surfaces over it follow it as it
/// scrolls or moves without a new capture; capture again with <see cref="RefreshAsync"/> when its
/// content changes. Keep every <see cref="GlassSurface"/> outside the captured element, or the glass
/// would capture itself.</summary>
public sealed class GlassBackdrop : IDisposable
{
    private readonly List<ScrollView> scrollers = [];
    private SKImage? image;
    private bool disposed;

    /// <param name="element">The element to capture, usually a page's content or a ScrollView's content.</param>
    /// <param name="background">What shows where the element is transparent; the glass needs an opaque
    /// backdrop. Defaults to white.</param>
    public GlassBackdrop(VisualElement element, Color? background = null)
    {
        Element = element ?? throw new ArgumentNullException(nameof(element));
        Background = background ?? Colors.White;
        Element.SizeChanged += OnSizeChanged;
        Element.Loaded += OnLoaded;
        if (Element.IsLoaded) OnLoaded(Element, EventArgs.Empty);
    }

    /// <summary>The captured element.</summary>
    public VisualElement Element { get; }

    /// <summary>The colour under transparent parts of the element.</summary>
    public Color Background { get; }

    /// <summary>The last capture, opaque, in device pixels; null before the first.</summary>
    public SKImage? Image => image;

    /// <summary>Device pixels per MAUI unit in <see cref="Image"/>.</summary>
    public double Scale { get; private set; } = 1;

    /// <summary>Raised after a capture, and when an enclosing ScrollView scrolls the element: the glass
    /// over it redraws.</summary>
    public event EventHandler? Changed;

    /// <summary>Captures the element now (on the UI thread, after layout). Returns false when the element
    /// has no size yet or the platform could not capture it; the previous image stays.</summary>
    public async Task<bool> RefreshAsync()
    {
        ObjectDisposedException.ThrowIf(disposed, this);
        if (Element.Width <= 0 || Element.Height <= 0 || Element.Handler is null) return false;
        using var decoded = await CapturePlatformAsync();
        if (decoded is null || disposed) return false;
        var next = Flatten(decoded, Background.ToSKColor().WithAlpha(255));
        image?.Dispose();
        image = next;
        Scale = next.Width / Element.Width;
        Watch();
        Changed?.Invoke(this, EventArgs.Empty);
        return true;
    }

    // The element's own native view, drawn into a bitmap: only its subtree, at full size even where a
    // ScrollView shows part of it. MAUI's CaptureAsync is not used on Android: there it copies the
    // window's pixels over the element's bounds, overlays included, so glass would refract itself.
    private async Task<SKImage?> CapturePlatformAsync()
    {
#if ANDROID
        await Task.CompletedTask;
        if (Element.Handler?.PlatformView is not Android.Views.View view || view.Width <= 0 || view.Height <= 0) return null;
        using var bitmap = Android.Graphics.Bitmap.CreateBitmap(view.Width, view.Height, Android.Graphics.Bitmap.Config.Argb8888!)!;
        using (var canvas = new Android.Graphics.Canvas(bitmap)) view.Draw(canvas);
        return SkiaSharp.Views.Android.AndroidExtensions.ToSKImage(bitmap);
#elif WINDOWS
        if (Element.Handler?.PlatformView is not Microsoft.UI.Xaml.UIElement view) return null;
        var target = new Microsoft.UI.Xaml.Media.Imaging.RenderTargetBitmap();
        await target.RenderAsync(view);
        if (target.PixelWidth <= 0 || target.PixelHeight <= 0) return null;
        var pixels = System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBufferExtensions.ToArray(await target.GetPixelsAsync());
        return SKImage.FromPixelCopy(new SKImageInfo(target.PixelWidth, target.PixelHeight, SKColorType.Bgra8888, SKAlphaType.Premul), pixels);
#else
        var shot = await Element.CaptureAsync();
        if (shot is null || shot.Width <= 0 || shot.Height <= 0) return null;
        await using var png = await shot.OpenReadAsync(ScreenshotFormat.Png);
        using var data = SKData.Create(png);
        return SKImage.FromEncodedData(data);
#endif
    }

    /// <summary>A capture drawn over an opaque background, as the shaders expect.</summary>
    public static SKImage Flatten(SKImage image, SKColor background)
    {
        ArgumentNullException.ThrowIfNull(image);
        if (background.Alpha != 255) throw new ArgumentException("Supply an opaque background", nameof(background));
        using var surface = SKSurface.Create(new SKImageInfo(image.Width, image.Height, SKColorType.Rgba8888, SKAlphaType.Opaque))
            ?? throw new InvalidOperationException("Cannot allocate the backdrop");
        surface.Canvas.Clear(background);
        surface.Canvas.DrawImage(image, 0, 0, new SKSamplingOptions(SKFilterMode.Nearest));
        surface.Canvas.Flush();
        return surface.Snapshot();
    }

    /// <summary>Where <paramref name="view"/>'s top-left corner falls on the element, in MAUI units:
    /// both positions summed up the visual tree, less the scroll offsets of the ScrollViews on the way.</summary>
    public Point OffsetOf(VisualElement view)
    {
        var a = PositionInWindow(view);
        var b = PositionInWindow(Element);
        return new Point(a.X - b.X, a.Y - b.Y);
    }

    internal static Point PositionInWindow(VisualElement view)
    {
        double x = 0, y = 0;
        for (Element? e = view; e is not null; e = e.Parent)
        {
            if (e is VisualElement v) { x += v.X + v.TranslationX; y += v.Y + v.TranslationY; }
            if (e.Parent is ScrollView s) { x -= s.ScrollX; y -= s.ScrollY; }
        }
        return new Point(x, y);
    }

    private void Watch()
    {
        foreach (var s in scrollers) s.Scrolled -= OnScrolled;
        scrollers.Clear();
        for (var e = Element.Parent; e is not null; e = e.Parent)
            if (e is ScrollView s) { s.Scrolled += OnScrolled; scrollers.Add(s); }
    }

    private void OnScrolled(object? sender, ScrolledEventArgs e) => Changed?.Invoke(this, EventArgs.Empty);

    // The first capture waits for the element's first drawn frame; a capture taken during layout comes
    // back empty on Android. Retries for about two seconds.
    private async void OnLoaded(object? sender, EventArgs e)
    {
        for (var attempt = 0; attempt < 20 && !disposed && image is null; attempt++)
        {
            await Task.Delay(100);
            try { if (await RefreshAsync()) return; }
            catch (ObjectDisposedException) { return; }
        }
    }

    private async void OnSizeChanged(object? sender, EventArgs e)
    {
        if (disposed || image is null) return; // the first capture is the host's call
        try { await RefreshAsync(); }
        catch (ObjectDisposedException) { }
    }

    public void Dispose()
    {
        if (disposed) return;
        disposed = true;
        Element.SizeChanged -= OnSizeChanged;
        Element.Loaded -= OnLoaded;
        foreach (var s in scrollers) s.Scrolled -= OnScrolled;
        scrollers.Clear();
        image?.Dispose();
        image = null;
    }
}

/// <summary>A rounded piece of Liquid Glass with <see cref="ContentView.Content"/> on top. It refracts its
/// <see cref="Backdrop"/> where it sits over it. Put it over the backdrop in a Grid or AbsoluteLayout, as
/// iOS puts a tab bar or a toolbar over a page. The content stays ordinary MAUI views, with their layout,
/// input and accessibility. Until the backdrop is captured, the glass draws nothing.</summary>
public class GlassSurface : ContentView
{
    public static readonly BindableProperty BackdropProperty = BindableProperty.Create(nameof(Backdrop), typeof(GlassBackdrop),
        typeof(GlassSurface), propertyChanged: (b, o, n) => ((GlassSurface)b).OnBackdropChanged((GlassBackdrop?)o, (GlassBackdrop?)n));
    public static readonly BindableProperty RadiusProperty = Redraws(nameof(Radius), typeof(double), 20.0);
    public static readonly BindableProperty DarkProperty = Redraws(nameof(Dark), typeof(bool), false);
    public static readonly BindableProperty TintAmountProperty = Redraws(nameof(TintAmount), typeof(double), 50.0);
    public static readonly BindableProperty LightProperty = Redraws(nameof(Light), typeof(Point), new Point(0, -1));
    public static readonly BindableProperty MaterializeProperty = Redraws(nameof(Materialize), typeof(double), 1.0);
    public static readonly BindableProperty ReducedTransparencyProperty = Redraws(nameof(ReducedTransparency), typeof(double), 0.0);
    public static readonly BindableProperty IncreasedContrastProperty = Redraws(nameof(IncreasedContrast), typeof(double), 0.0);

    /// <summary>Space between the glass's edge and <see cref="ContentView.Content"/>. It insets the content
    /// only: the glass always fills the whole surface.</summary>
    public static new readonly BindableProperty PaddingProperty = BindableProperty.Create(nameof(Padding), typeof(Thickness),
        typeof(GlassSurface), default(Thickness), propertyChanged: (b, _, n) => ((GlassSurface)b).presenter.Padding = (Thickness)n);

    private readonly SKCanvasView canvas = new() { InputTransparent = true };
    private readonly ContentPresenter presenter = new();
    private readonly GlassPainter painter = new();
    private SKImage? painted;

    public GlassSurface()
    {
        canvas.PaintSurface += OnPaintSurface;
        ControlTemplate = new ControlTemplate(() =>
        {
            var root = new Grid();
            root.Add(canvas);
            root.Add(presenter);
            return root;
        });
        SizeChanged += (_, _) => canvas.InvalidateSurface();
    }

    /// <inheritdoc cref="PaddingProperty"/>
    public new Thickness Padding { get => (Thickness)GetValue(PaddingProperty); set => SetValue(PaddingProperty, value); }

    /// <summary>What the glass refracts. Several surfaces may share one backdrop.</summary>
    public GlassBackdrop? Backdrop { get => (GlassBackdrop?)GetValue(BackdropProperty); set => SetValue(BackdropProperty, value); }

    /// <summary>Corner radius, MAUI units. Default 20.</summary>
    public double Radius { get => (double)GetValue(RadiusProperty); set => SetValue(RadiusProperty, value); }

    /// <summary>The dark material.</summary>
    public bool Dark { get => (bool)GetValue(DarkProperty); set => SetValue(DarkProperty, value); }

    /// <summary>0 to 100. Default 50.</summary>
    public double TintAmount { get => (double)GetValue(TintAmountProperty); set => SetValue(TintAmountProperty, value); }

    /// <summary>Toward the light (y up is negative). (0, -1) is iOS 27's measured overhead light.</summary>
    public Point Light { get => (Point)GetValue(LightProperty); set => SetValue(LightProperty, value); }

    /// <summary>0 to 1: glass materializes in and out rather than fading. Default 1.</summary>
    public double Materialize { get => (double)GetValue(MaterializeProperty); set => SetValue(MaterializeProperty, value); }

    /// <summary>0 to 1, the accessibility setting.</summary>
    public double ReducedTransparency { get => (double)GetValue(ReducedTransparencyProperty); set => SetValue(ReducedTransparencyProperty, value); }

    /// <summary>0 to 1, the accessibility setting.</summary>
    public double IncreasedContrast { get => (double)GetValue(IncreasedContrastProperty); set => SetValue(IncreasedContrastProperty, value); }

    /// <summary>Redraw now, after moving the surface by means the backdrop cannot see (an animation of
    /// its own position, say).</summary>
    public void Invalidate() => canvas.InvalidateSurface();

    private static BindableProperty Redraws(string name, Type type, object value) =>
        BindableProperty.Create(name, type, typeof(GlassSurface), value, propertyChanged: (b, _, _) => ((GlassSurface)b).Invalidate());

    private void OnBackdropChanged(GlassBackdrop? old, GlassBackdrop? next)
    {
        if (old is not null) old.Changed -= OnBackdropMoved;
        if (next is not null) next.Changed += OnBackdropMoved;
        Invalidate();
    }

    private void OnBackdropMoved(object? sender, EventArgs e) => Invalidate();

    private void OnPaintSurface(object? sender, SKPaintSurfaceEventArgs e)
    {
        var c = e.Surface.Canvas;
        c.Clear(SKColors.Transparent);
        var backdrop = Backdrop;
        var image = backdrop?.Image;
        if (backdrop is null || image is null || Width <= 0 || Height <= 0) return;
        if (!ReferenceEquals(painted, image)) { painter.SetSource(image); painted = image; }
        var s = (float)backdrop.Scale;               // backdrop px per unit
        var offset = backdrop.OffsetOf(this);         // units
        var bounds = SKRect.Create((float)offset.X * s, (float)offset.Y * s, (float)Width * s, (float)Height * s);
        c.Save();
        c.Scale((float)(e.Info.Width / Width) / s);   // backdrop px -> this view's device px
        c.Translate(-bounds.Left, -bounds.Top);
        painter.DrawSurface(c, bounds, (float)Radius * s, new GlassSurfaceOptions(
            Dark: Dark, TintAmount: (float)TintAmount, Density: s, Materialize: (float)Materialize,
            ReducedTransparency: (float)ReducedTransparency, IncreasedContrast: (float)IncreasedContrast,
            LightX: (float)Light.X, LightY: (float)Light.Y));
        c.Restore();
    }
}

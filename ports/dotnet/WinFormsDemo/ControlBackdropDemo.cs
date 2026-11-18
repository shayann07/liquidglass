using System.Text.Json;
using LiquidGlass.Skia;
using LiquidGlass.WinForms;
using SkiaSharp;
using SkiaSharp.Views.Desktop;

/// <summary>Live native controls on the left; their captured pixels with glass on the right.
/// Both belong to this process. Only the left subtree is recorded, preventing glass recursion.</summary>
internal static class ControlBackdropDemo
{
    public static void Run(string? output)
    {
        using var form = new Form { Text = "LiquidGlass - native control backdrop", ClientSize = new(960, 440), MinimumSize = new(900, 480) };
        using var root = new Panel { BackColor = Color.FromArgb(26, 47, 67), Width = 420, Dock = DockStyle.Left, Padding = new(20) };
        using var titleFont = new Font(SystemFonts.DefaultFont.FontFamily, 20);
        using var title = new Label { Text = "Your native page", Location = new(20, 24), Size = new(360, 36), ForeColor = Color.White, Font = titleFont };
        using var entry = new TextBox { Text = "Edit this ordinary TextBox", Location = new(20, 96), Width = 350 };
        using var toggle = new CheckBox { Text = "Show highlighted state", Location = new(20, 150), AutoSize = true, ForeColor = Color.White };
        using var note = new Label { Text = "Native controls remain interactive.\nTheir pixels feed the glass preview.", Location = new(20, 215), Size = new(370, 60), ForeColor = Color.LightCyan };
        root.Controls.AddRange([title, entry, toggle, note]);
        using var preview = new SKControl { Dock = DockStyle.Fill, AccessibleName = "Glass preview of the native controls" };
        using var painter = new GlassPainter();
        var sampling = new SKSamplingOptions(SKFilterMode.Linear, SKMipmapMode.None);
        SKImage? snapshot = null;
        var pending = false;
        var captures = new List<object>();
        var verified = false;
        void RefreshSource()
        {
            if (root.IsDisposed || !root.IsHandleCreated || !root.Visible) return;
            var next = ControlBackdrop.Capture(root, root.BackColor);
            try { painter.SetSource(next); }
            catch { next.Dispose(); throw; }
            var previous = snapshot; snapshot = next; previous?.Dispose(); preview.Invalidate();
        }
        void ScheduleRefresh()
        {
            if (pending || !form.IsHandleCreated || form.IsDisposed) return;
            pending = true;
            form.BeginInvoke(() => { pending = false; RefreshSource(); });
        }
        entry.TextChanged += (_, _) => ScheduleRefresh();
        toggle.CheckedChanged += (_, _) => { root.BackColor = toggle.Checked ? Color.FromArgb(52, 85, 95) : Color.FromArgb(26, 47, 67); ScheduleRefresh(); };
        root.SizeChanged += (_, _) => ScheduleRefresh();
        preview.PaintSurface += (_, e) =>
        {
            e.Surface.Canvas.Clear(new SKColor(12, 24, 38));
            if (snapshot is null) return;
            var canvas = e.Surface.Canvas;
            canvas.DrawImage(snapshot, 0, 0, sampling);
            painter.DrawLens(canvas, SKRect.Create(34, 50, 200, 200), 1.4f);
            painter.DrawSurface(canvas, SKRect.Create(20, 285, Math.Min(370, snapshot.Width - 40), 90), 24, new(Dark: true));
            canvas.Flush();
        };
        form.Controls.Add(preview); form.Controls.Add(root);

        void Save(SKImage image, string name)
        {
            using var data = image.Encode(SKEncodedImageFormat.Png, 100);
            using var file = File.Create(Path.Combine(output!, name)); data.SaveTo(file);
        }
        byte[] Pixels(SKImage image)
        {
            using var bitmap = SKBitmap.FromImage(image);
            return bitmap.Bytes;
        }
        void Check(bool value, string message) { if (!value) throw new InvalidOperationException(message); }
        form.Shown += (_, _) => form.BeginInvoke(() =>
        {
            try
            {
                RefreshSource();
                if (output is null) return;
                Directory.CreateDirectory(output);
                var first = Pixels(snapshot!); Save(snapshot!, "controls-before.png");
                // Text content changes while bounds stay fixed; there is no hand-drawn substitute.
                entry.Text = "Fresh text from the native widget"; RefreshSource();
                var textPixels = Pixels(snapshot!);
                var changedText = 0;
                for (var y = entry.Top; y < entry.Bottom; y++) for (var x = entry.Left; x < entry.Right; x++)
                {
                    var index = (y * snapshot!.Width + x) * 4;
                    if (first[index] != textPixels[index] || first[index + 1] != textPixels[index + 1] || first[index + 2] != textPixels[index + 2]) changedText++;
                }
                Check(changedText > 30, "Actual TextBox glyph update was missing from the capture");
                Save(snapshot!, "controls-text-updated.png");
                toggle.Checked = true; RefreshSource();
                var after = Pixels(snapshot!); Save(snapshot!, "controls-after.png");
                Check(!first.SequenceEqual(after), "Native control updates did not reach the backdrop");
                Check(snapshot!.AlphaType == SKAlphaType.Opaque, "Backdrop must be opaque");
                var width = snapshot.Width; var height = snapshot.Height;
                using var rendered = SKSurface.Create(new SKImageInfo(width, height, SKColorType.Bgra8888, SKAlphaType.Opaque));
                rendered.Canvas.DrawImage(snapshot, 0, 0, sampling);
                painter.DrawLens(rendered.Canvas, SKRect.Create(34, 50, 200, 200), new GlassLensOptions(Lighting: false), 1);
                using (var identity = rendered.Snapshot()) Check(Pixels(identity).SequenceEqual(after), "Identity lens did not preserve actual widget pixels");
                painter.DrawLens(rendered.Canvas, SKRect.Create(34, 50, 200, 200), 1.4f);
                using (var glass = rendered.Snapshot()) { Check(!Pixels(glass).SequenceEqual(after), "Lens did not refract native pixels"); Save(glass, "controls-glass.png"); }
                var retained = snapshot;
                using (var unsupported = new RichTextBox { Text = "Unsupported path must fail", Size = new(100, 30), Location = new(20, 320) })
                {
                    root.Controls.Add(unsupported); unsupported.CreateControl();
                    try { using var unexpected = ControlBackdrop.Capture(root, root.BackColor); throw new Exception("Unsupported RichTextBox accepted"); }
                    catch (NotSupportedException) { }
                    root.Controls.Remove(unsupported);
                }
                Check(retained.Handle != IntPtr.Zero && Pixels(retained).SequenceEqual(after), "Failed capture damaged retained source");
                Exception? wrongThread = null;
                var worker = new Thread(() => { try { using var unexpected = ControlBackdrop.Capture(root, root.BackColor); } catch (Exception error) { wrongThread = error; } });
                worker.Start(); worker.Join();
                Check(wrongThread is InvalidOperationException, "Wrong-thread capture was not rejected");
                root.Width = 380; RefreshSource(); Check(snapshot!.Width == 380, "Resize retained old source dimensions");
                Save(snapshot, "controls-resized.png");
                verified = true;
                captures.Add(new { nativeControlUpdates = true, changedTextPixels = changedText, identityPixels = true, refraction = true,
                    unsupportedRejected = true, retainedSource = true, wrongThreadRejected = true, resize = true });
            }
            catch (Exception error)
            {
                Environment.ExitCode = 1;
                if (output is not null) { Directory.CreateDirectory(output); File.WriteAllText(Path.Combine(output, "failure.txt"), error.ToString()); }
                else throw;
            }
            finally
            {
                if (output is not null)
                {
                    File.WriteAllText(Path.Combine(output, "control-verification.json"), JsonSerializer.Serialize(new
                    {
                        passed = verified, checks = captures, input = "Own Control.DrawToBitmap; no screen capture",
                        limits = "WinForms Label/TextBox/CheckBox/Panel only verified; custom controls and overlapping Z-order require host validation."
                    }, new JsonSerializerOptions { WriteIndented = true }));
                    form.Close();
                }
            }
        });
        try { Application.Run(form); }
        finally { painter.Dispose(); snapshot?.Dispose(); }
    }
}

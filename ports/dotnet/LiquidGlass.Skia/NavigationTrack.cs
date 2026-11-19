// Apache-2.0. Record a navigation gesture once, then ask for the bar's state at any time, in any order.
namespace LiquidGlass.Skia;

public enum NavigationEventType { Down, Move, Up, Cancel, Select }

/// <summary>A scripted event for <see cref="NavigationBaker"/>: bar-local px (as <see cref="CalmNavigation"/> takes them) and
/// seconds. <c>Cancel</c> takes only a time; <c>Select</c> is the host's own selection change (<c>Index</c>).</summary>
public readonly record struct NavigationEvent(NavigationEventType Type, double Time, double X = 0, double Y = 0, int Index = 0);

/// <summary>The bar's state at one moment: what <c>GlassPainter.DrawCalmBar</c> and <c>GlassPainter.DrawSelector</c>
/// take, plus the bar's own press feedback. Pose lengths are bar-local logical px, as <see cref="CalmSelectorController.Render"/> returns them.</summary>
public sealed record NavigationSample(double CentreX, double CentreY, CalmSelectorExtents Extents, double Formation,
    double TransitAppearance, double Touch, double HeldTravel, double Protrusion, double ItemFollow, bool Held, bool TapFormed,
    CalmSelectorPose Pose, GlassFeedback Feedback, int Selected, bool Active, CalmHold? Hold = null);

/// <summary>A track as plain data (numbers only): store it, or hand it to another thread, and rebuild with
/// <see cref="NavigationTrack.FromData"/>.</summary>
public sealed record NavigationTrackData(int Version, double Step, double Duration, double[][] Columns);

/// <summary>A baked navigation: <see cref="Sample"/> is the bar's state at any time, whatever order it is asked in. Exact at a step;
/// in between, numbers interpolate linearly (the pose's inverse is recomputed from the interpolated matrix) and decisions
/// (<c>Held</c>, <c>TapFormed</c>, <c>Hold.Formed</c>, <c>Hold.Releasing</c>, <c>Selected</c>, <c>Active</c>) keep the earlier step's value. Before 0 it is the first step,
/// after the last the last. Immutable and safe to share between threads.</summary>
public sealed class NavigationTrack
{
    // Columns: numbers that interpolate, then decisions that snap, then the stored inverse (never interpolated).
    private const int Numbers = 8 + 4 + 7 + 16 + 2, Decisions = 8, Columns = Numbers + Decisions + 4;
    internal const int ColumnCount = Columns;
    private readonly double[][] columns;

    internal NavigationTrack(double step, double duration, double[][] columns)
    {
        Step = step; Duration = duration; this.columns = columns; Length = columns[0].Length;
    }

    public double Step { get; }
    public double Duration { get; }
    /// <summary>The number of stored steps.</summary>
    public int Length { get; }

    public NavigationTrackData ToData() => new(2, Step, Duration, columns.Select(c => (double[])c.Clone()).ToArray());

    public static NavigationTrack FromData(NavigationTrackData data)
    {
        ArgumentNullException.ThrowIfNull(data);
        // A version 1 track (before #103) lacks the hold columns: what the old code drew is no hold, the fill following the
        // formation (HoldLook = HoldRim = Formation, as Compose's default) and the lens neither hold-formed nor releasing.
        if (data is { Version: 1, Columns.Length: Columns - 4 } && data.Columns.All(c => c is not null))
            data = new NavigationTrackData(2, data.Step, data.Duration, UpgradeV1(data.Columns));
        if (data.Version != 2 || !(data.Step > 0) || !double.IsFinite(data.Duration) || data.Columns is not { Length: Columns } cols ||
            cols[0] is not { Length: >= 1 } || cols.Any(c => c is null || c.Length != cols[0].Length || c.Any(v => !double.IsFinite(v))))
            throw new ArgumentException("Not a navigation track", nameof(data));
        return new NavigationTrack(data.Step, data.Duration, cols.Select(c => (double[])c.Clone()).ToArray());
    }

    // Version 1 layout: 35 numbers, 6 decisions, 4 inverse. Version 2 adds holdLook and holdRim after the numbers and
    // holdFormed and holdReleasing after the decisions.
    private static double[][] UpgradeV1(double[][] v1)
    {
        var formation = v1[2];
        var zero = new double[formation.Length];
        return [.. v1[..35], (double[])formation.Clone(), (double[])formation.Clone(), .. v1[35..41], zero, (double[])zero.Clone(), .. v1[41..]];
    }

    internal static double[] Encode(CalmNavigation nav, GlassFeedback f, bool active, CalmSelectorController c)
    {
        var e = c.Extents(); var p = c.Render();
        var row = new List<double>(Columns)
        {
            c.CentreX, c.CentreY, c.Formation, c.TransitAppearance, nav.Touch, c.HeldTravel, c.Protrusion(), nav.ItemFollow,
            e.Left, e.Right, e.Top, e.Bottom,
        };
        row.AddRange(new[] { f.A, f.B, f.C, f.D, f.OffsetX, f.OffsetY, f.Press });
        row.AddRange(p.A.Select(v => (double)v)); row.AddRange(p.C.Select(v => (double)v));
        row.AddRange(p.D.Select(v => (double)v)); row.AddRange(p.E!.Select(v => (double)v));
        row.AddRange(new[] { c.HoldLook, c.HoldRim });
        row.AddRange(new double[] { c.TapFormed ? 1 : 0, c.IsHeld ? 1 : 0, f.Held ? 1 : 0, f.Active ? 1 : 0, active ? 1 : 0, nav.Selected, c.HoldFormed ? 1 : 0, c.HoldReleasing ? 1 : 0 });
        row.AddRange(p.AInv.Select(v => (double)v));
        return row.ToArray();
    }

    private static NavigationSample Decode(double[] r)
    {
        float[] Quad(int at) => [(float)r[at], (float)r[at + 1], (float)r[at + 2], (float)r[at + 3]];
        // Layout of the numbers: 8 selector, 4 extents, 7 feedback, then a, c, d, e (4 each), then holdLook and holdRim (#103).
        var pose = new CalmSelectorPose(Quad(19), Quad(Numbers + Decisions), Quad(23), Quad(27)) { E = Quad(31) };
        var d = Numbers;
        var feedback = new GlassFeedback(r[12], r[13], r[14], r[15], r[16], r[17], r[18], r[d + 2] != 0, r[d + 3] != 0);
        return new NavigationSample(r[0], r[1], new CalmSelectorExtents(r[8], r[9], r[10], r[11]), r[2], r[3], r[4], r[5], r[6], r[7],
            r[d + 1] != 0, r[d] != 0, pose, feedback, (int)r[d + 5], r[d + 4] != 0, new CalmHold(r[d + 6] != 0, r[d + 7] != 0, r[35], r[36]));
    }

    /// <summary>The state at <paramref name="seconds"/>.</summary>
    public NavigationSample Sample(double seconds)
    {
        if (!double.IsFinite(seconds)) throw new ArgumentOutOfRangeException(nameof(seconds));
        var u = Math.Clamp(seconds / Step, 0, Length - 1); var nearest = (int)Math.Round(u);
        double[] Row(int k) { var r = new double[Columns]; for (var c = 0; c < Columns; c++) r[c] = columns[c][k]; return r; }
        // An exact step returns the stored state unchanged; k / step may land a rounding error off the step.
        if (Math.Abs(u - nearest) < 1e-9) return Decode(Row(nearest));
        var i = (int)Math.Floor(u); var w = u - i; double[] a = Row(i), b = Row(i + 1), o = (double[])a.Clone();
        for (var c = 0; c < Numbers; c++) o[c] = a[c] + (b[c] - a[c]) * w;
        var m = Enumerable.Range(19, 4).Select(c => o[c]).ToArray();
        var det = m[0] * m[3] - m[1] * m[2];
        double[] inverse = [m[3] / det, -m[1] / det, -m[2] / det, m[0] / det];
        for (var k = 0; k < 4; k++) o[Numbers + Decisions + k] = inverse[k];
        return Decode(o);
    }
}

/// <summary>Run a scripted gesture through <see cref="CalmNavigation"/> and the bar's <see cref="CalmInteraction"/> (press scale 1.05,
/// no growth: the scene's navigation layer in the Skia port) at a fixed step and keep every step's state. The motion steps, never
/// jumps: a live run at another frame cadence differs slightly, as any two live runs do.</summary>
public static class NavigationBaker
{
    /// <param name="bar">The bar's geometry; its width and height are also the press feedback's bounds.</param>
    /// <param name="events">Bar-local px and seconds, nondecreasing in time.</param>
    /// <param name="duration">Seconds to record from 0.</param>
    /// <param name="step">Seconds per stored step (default 1/240).</param>
    /// <param name="selected">The tab selected at time 0.</param>
    /// <param name="reducedMotion">Snap to rest and never hold, as a host with reduced motion does.</param>
    /// <param name="viewportWidth">The page's logical size, which clamps the press feedback (0: unbounded).</param>
    /// <param name="viewportHeight">See <paramref name="viewportWidth"/>.</param>
    public static NavigationTrack Bake(CalmSelectorBar bar, IReadOnlyList<NavigationEvent> events, double duration,
        double step = 1.0 / 240, int selected = 0, bool reducedMotion = false, double viewportWidth = 0, double viewportHeight = 0)
    {
        ArgumentNullException.ThrowIfNull(bar); ArgumentNullException.ThrowIfNull(events);
        if (!(step > 0) || !double.IsFinite(step) || !(duration >= 0) || !double.IsFinite(duration))
            throw new ArgumentOutOfRangeException(nameof(duration), "Positive step and nonnegative duration required");
        var length = (int)Math.Ceiling(duration / step - 1e-9) + 1;
        if (length > 5_000_000) throw new ArgumentOutOfRangeException(nameof(step), "Too many steps: raise the step or shorten the duration");
        var last = 0.0;
        foreach (var e in events)
        {
            if (!Enum.IsDefined(e.Type)) throw new ArgumentException($"Unknown event type {e.Type}", nameof(events));
            if (!double.IsFinite(e.Time) || e.Time < 0 || e.Time < last) throw new ArgumentException("Event times must be finite, nonnegative and nondecreasing", nameof(events));
            last = e.Time;
            if (e.Type is NavigationEventType.Down or NavigationEventType.Move or NavigationEventType.Up && !(double.IsFinite(e.X) && double.IsFinite(e.Y)))
                throw new ArgumentException($"{e.Type} needs finite X and Y", nameof(events));
        }
        // A bake has no host: the selection a gesture commits is applied as a host's onSelect handler would.
        CalmNavigation? nav = null;
        nav = new CalmNavigation(bar, selected, i => nav!.Select(i), motion: !reducedMotion);
        var geometry = new CalmGeometry(bar.Width, bar.Height, bar.Height / 2, 0, 0,
            viewportWidth > 0 ? viewportWidth : null, viewportHeight > 0 ? viewportHeight : null);
        var motion = new CalmInteraction(geometry, new CalmOptions(PressScale: 1.05, PressGrowth: 0, ReducedMotion: reducedMotion));
        var feedback = motion.Sample(0);
        (double X, double Y)? down = null;
        var columns = Enumerable.Range(0, NavigationTrack.ColumnCount).Select(_ => new double[length]).ToArray();
        var next = 0;
        for (var k = 0; k < length; k++)
        {
            var t = k * step;
            for (; next < events.Count && events[next].Time <= t; next++)
            {
                var e = events[next];
                switch (e.Type)
                {
                    case NavigationEventType.Down: down = (e.X, e.Y); nav.Down(e.X, e.Y, e.Time); feedback = motion.Press(e.Time); break;
                    case NavigationEventType.Move:
                        nav.Move(e.X, e.Y, e.Time);
                        if (down is { } d) feedback = motion.Press(e.Time, e.X - d.X, e.Y - d.Y);
                        break;
                    case NavigationEventType.Up: nav.Up(e.X, e.Y, e.Time); down = null; feedback = motion.Release(e.Time); break;
                    case NavigationEventType.Cancel: nav.Cancel(e.Time); down = null; feedback = motion.Release(e.Time); break;
                    default: nav.Select(e.Index); break;
                }
            }
            feedback = motion.Sample(t); var moving = nav.Frame(t);
            var row = NavigationTrack.Encode(nav, feedback, feedback.Active || moving, nav.Controller);
            for (var c = 0; c < row.Length; c++) columns[c][k] = row[c];
        }
        return new NavigationTrack(step, duration, columns);
    }
}

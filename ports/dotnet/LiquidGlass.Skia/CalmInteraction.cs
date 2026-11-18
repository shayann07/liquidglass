// Apache-2.0. Native counterpart of ports/web/core.mjs and interaction.mjs.
// Cross-language trajectory fixtures guard the shared authored response; no Apple timing claim.
namespace LiquidGlass.Skia;

public enum GlassPullShape { Adaptive, AreaPreserving }

/// <summary>Logical pixel bounds. Supply both viewport dimensions or neither.</summary>
public sealed record CalmGeometry(double Width, double Height, double Radius,
    double X = 0, double Y = 0, double? ViewportWidth = null, double? ViewportHeight = null);

public sealed record CalmOptions(double PressScale = 1.03, double PressGrowth = 2,
    GlassPullShape PullShape = GlassPullShape.Adaptive, bool ReducedMotion = false);

/// <summary>Row-major 2x2 material transform around the original bounds centre, followed by a
/// logical-pixel offset. Keep labels, layout and hit targets outside this drawing transform.</summary>
public readonly record struct GlassFeedback(double A, double B, double C, double D,
    double OffsetX, double OffsetY, double Press, bool Held, bool Active)
{
    public static GlassFeedback Identity => new(1, 0, 0, 1, 0, 0, 0, false, false);
}

/// <summary>Framework-independent whole-surface feedback, not navigation-selector travel.
/// Supply monotonic seconds and cumulative displacement since pointer-down. The host owns
/// pointer arbitration, cancellation and frame scheduling. Use one instance per surface/thread.</summary>
public sealed class CalmInteraction
{
    private CalmGeometry geometry;
    private readonly CalmOptions options;
    private bool held, reduced;
    private double? clock;
    private Spring pressure, horizontal, vertical;
    private double targetX, targetY;

    public CalmInteraction(CalmGeometry geometry, CalmOptions? options = null)
    {
        this.geometry = Validate(geometry);
        this.options = options ?? new();
        if (!double.IsFinite(this.options.PressScale) || this.options.PressScale is < 1 or > 2 ||
            !double.IsFinite(this.options.PressGrowth) || this.options.PressGrowth < 0 ||
            !Enum.IsDefined(this.options.PullShape))
            throw new ArgumentOutOfRangeException(nameof(options));
        reduced = this.options.ReducedMotion;
    }

    public CalmGeometry Geometry => geometry;

    public GlassFeedback Press(double seconds, double dx = 0, double dy = 0)
    {
        if (!double.IsFinite(dx) || !double.IsFinite(dy)) throw new ArgumentOutOfRangeException(nameof(dx));
        Advance(seconds); held = true;
        var (sx, sy) = PressTargets();
        var limit = Math.Min(geometry.Width * sx, geometry.Height * sy) * .5;
        var scale = Math.Max(Math.Abs(dx), Math.Abs(dy));
        if (reduced || scale == 0) { targetX = 0; targetY = 0; }
        else
        {
            // Normalize before computing length so enormous finite pulls remain finite.
            var length = Hypot(dx / scale, dy / scale);
            var resisted = limit * Math.Tanh(scale * .2 / limit * length) / .2;
            targetX = dx / scale / length * resisted; targetY = dy / scale / length * resisted;
        }
        return Snapshot();
    }

    /// <summary>Use on pointer-up and cancellation. Selection remains the host's responsibility.</summary>
    public GlassFeedback Release(double seconds)
    {
        Advance(seconds); held = false; targetX = targetY = 0; return Snapshot();
    }

    /// <summary>Queued frames older than the latest input return current state without rewinding.</summary>
    public GlassFeedback Sample(double seconds) { Advance(seconds, staleFrame: true); return Snapshot(); }

    /// <summary>A layout change cancels the gesture. Invalid geometry leaves the controller intact.</summary>
    public GlassFeedback Resize(CalmGeometry next, double seconds)
    {
        next = Validate(next); Advance(seconds); geometry = next; held = false; Clear(); return Snapshot();
    }

    public GlassFeedback SetReducedMotion(bool value, double seconds)
    {
        Advance(seconds); reduced = value; held = false; Clear(); return Snapshot();
    }

    private static CalmGeometry Validate(CalmGeometry g)
    {
        ArgumentNullException.ThrowIfNull(g);
        if (!double.IsFinite(g.X) || !double.IsFinite(g.Y) || !double.IsFinite(g.Width) || !double.IsFinite(g.Height) ||
            g.Width is <= 0 or > 1e7 || g.Height is <= 0 or > 1e7 || !double.IsFinite(g.Radius) || g.Radius < 0)
            throw new ArgumentOutOfRangeException(nameof(g));
        if ((g.ViewportWidth.HasValue || g.ViewportHeight.HasValue) &&
            (!g.ViewportWidth.HasValue || !g.ViewportHeight.HasValue ||
             !double.IsFinite(g.ViewportWidth.Value) || !double.IsFinite(g.ViewportHeight.Value) ||
             g.ViewportWidth.Value <= 0 || g.ViewportHeight.Value <= 0))
            throw new ArgumentOutOfRangeException(nameof(g));
        return g with { Radius = Math.Min(g.Radius, Math.Min(g.Width, g.Height) / 2) };
    }

    private (double X, double Y) PressTargets()
    {
        var g = geometry;
        double Scale(double side) => options.PressGrowth > 0
            ? Math.Min(options.PressScale, 1 + 2 * options.PressGrowth / side) : options.PressScale;
        var sx = Scale(g.Width); var sy = Scale(g.Height);
        // Constrain the equilibrium before springing, not the animated result.
        if (g.ViewportWidth is double vw && g.X >= 0 && g.X + g.Width <= vw)
            sx = Math.Min(sx, Math.Max(1, 2 * Math.Min(g.X + g.Width / 2, vw - g.X - g.Width / 2) / g.Width));
        if (g.ViewportHeight is double vh && g.Y >= 0 && g.Y + g.Height <= vh)
            sy = Math.Min(sy, Math.Max(1, 2 * Math.Min(g.Y + g.Height / 2, vh - g.Y - g.Height / 2) / g.Height));
        return (sx, sy);
    }

    private readonly record struct Spring(double Value, double Velocity)
    {
        public Spring Step(double target, double dt, double stiffness)
        {
            if (dt == 0) return this;
            var omega = Math.Sqrt(stiffness);
            if (omega * dt > 750) return new(target, 0);
            var error = Value - target; var b = Velocity + omega * error; var decay = Math.Exp(-omega * dt);
            return new(target + (error + b * dt) * decay, (Velocity - omega * b * dt) * decay);
        }
    }

    private void Advance(double time, bool staleFrame = false)
    {
        if (!double.IsFinite(time) || time < 0) throw new ArgumentOutOfRangeException(nameof(time));
        if (clock.HasValue && time < clock.Value)
        {
            if (staleFrame) return;
            throw new ArgumentOutOfRangeException(nameof(time), "Use monotonic seconds");
        }
        var dt = clock.HasValue ? time - clock.Value : 0; clock = time;
        pressure = pressure.Step(held && !reduced ? 1 : 0, dt, held ? 220 : 260);
        horizontal = horizontal.Step(targetX, dt, held ? 350 : 260);
        vertical = vertical.Step(targetY, dt, held ? 350 : 260);
        if (!held && Math.Abs(pressure.Value) < 1e-5 && Math.Abs(pressure.Velocity) < 1e-4 &&
            Hypot(horizontal.Value, vertical.Value) < 1e-4 && Hypot(horizontal.Velocity, vertical.Velocity) < 1e-3)
            Clear();
    }

    private void Clear() { pressure = horizontal = vertical = default; targetX = targetY = 0; }

    private GlassFeedback Snapshot()
    {
        if (reduced) return GlassFeedback.Identity with { Held = held };
        var g = geometry; var w = g.Width; var h = g.Height; var r = g.Radius;
        var amount = Math.Clamp(pressure.Value, 0, 1);
        var (sx, sy) = PressTargets();
        var pressX = 1 + (sx - 1) * amount; var pressY = 1 + (sy - 1) * amount;
        var travel = Hypot(horizontal.Value, vertical.Value);
        double along = 1, across = 1, angle = 0, ox = 0, oy = 0;
        if (travel > 0)
        {
            var nx = horizontal.Value / travel; var ny = vertical.Value / travel;
            var roundness = Math.Clamp(2 * Math.Min(w, h) / Math.Max(w, h) - 1, 0, 1);
            if (options.PullShape == GlassPullShape.AreaPreserving)
            {
                // A long bar takes only the pull across its spine (Compose GlassPull): a sideways drag leaves iOS 27's
                // held Phone bar alone. A round surface keeps the whole pull.
                var ax = horizontal.Value * roundness; var spine = Hypot(ax, vertical.Value);
                if (spine > 0)
                {
                    var px = ax / spine; var py = vertical.Value / spine;
                    var pull = Math.Clamp(spine * .2 / (Math.Min(w * sx, h * sy) * .5), 0, 1);
                    var strain = .047 * pull;
                    var u = strain * (px * px - py * py); var v = 2 * strain * px * py * roundness;
                    var magnitude = Hypot(u, v);
                    along = Math.Exp(magnitude); across = Math.Exp(-magnitude); angle = Math.Atan2(v, u) / 2;
                    var bias = .056 * Math.Min(w, h) * pull; ox = bias * px; oy = bias * py;
                }
            }
            else
            {
                double Extent(double x, double y) => (w - 2 * r) * pressX * Math.Abs(x) +
                    (h - 2 * r) * pressY * Math.Abs(y) + 2 * r * Hypot(pressX * x, pressY * y);
                var extension = .03 * travel;
                var t = Math.Clamp((Math.Max(w, h) - 80) / 80, 0, 1); var mix = t * t * (3 - 2 * t);
                var surfaceGain = extension < .001 ? .2 : 2 * Math.Tanh(.2 * extension / 2) / extension;
                var gain = 1 + (surfaceGain - 1) * mix;
                var a = 1 + extension / Extent(nx, ny) * gain; var b = 1 - extension / Extent(-ny, nx) * gain;
                var xx = a * nx * nx + b * ny * ny; var yy = a * ny * ny + b * nx * nx;
                var xy = (a - b) * nx * ny * roundness;
                var mean = (xx + yy) / 2; var eigenRadius = Hypot((xx - yy) / 2, xy);
                along = mean + eigenRadius; across = mean - eigenRadius; angle = Math.Atan2(2 * xy, xx - yy) / 2;
            }
        }
        var c = Math.Cos(angle); var s = Math.Sin(angle);
        (double A, double B, double C, double D) Matrix(double gain)
        {
            var a = 1 + (along - 1) * gain; var b = 1 + (across - 1) * gain; var shear = (a - b) * c * s;
            return ((a * c * c + b * s * s) * pressX, shear * pressY, shear * pressX, (a * s * s + b * c * c) * pressY);
        }
        (double X, double Y) HalfExtent(double gain)
        {
            var m = Matrix(gain);
            return ((w / 2 - r) * Math.Abs(m.A) + (h / 2 - r) * Math.Abs(m.B) + r * Hypot(m.A, m.B),
                (w / 2 - r) * Math.Abs(m.C) + (h / 2 - r) * Math.Abs(m.D) + r * Hypot(m.C, m.D));
        }
        var cx = g.X + w / 2; var cy = g.Y + h / 2; var gainLimit = 1.0;
        if (g.ViewportWidth is double vw && g.ViewportHeight is double vh && g.X >= 0 && g.Y >= 0 && g.X + w <= vw && g.Y + h <= vh)
        {
            bool Fits(double gain) { var e = HalfExtent(gain); return e.X <= Math.Min(cx, vw - cx) && e.Y <= Math.Min(cy, vh - cy); }
            if (!Fits(1))
            {
                double low = 0, high = 1;
                for (var i = 0; i < 24; i++) { var mid = (low + high) / 2; if (Fits(mid)) low = mid; else high = mid; }
                gainLimit = low;
            }
            var extent = HalfExtent(gainLimit);
            static double Resist(double value, double before, double after)
            {
                var room = Math.Max(0, value < 0 ? before : after); var free = room / 2;
                if (Math.Abs(value) <= free) return value;
                var span = room - free; var result = free + (span > 0 ? span * Math.Tanh((Math.Abs(value) - free) / span) : 0);
                return value < 0 ? -result : result;
            }
            ox = Resist(ox, cx - extent.X, vw - cx - extent.X); oy = Resist(oy, cy - extent.Y, vh - cy - extent.Y);
        }
        var active = Math.Abs(pressure.Value - (held ? 1 : 0)) > 1e-5 || Math.Abs(pressure.Velocity) > 1e-4 ||
            Hypot(horizontal.Value - targetX, vertical.Value - targetY) > 1e-4 || Hypot(horizontal.Velocity, vertical.Velocity) > 1e-3;
        var m = Matrix(gainLimit);
        return new(m.A, m.B, m.C, m.D, ox, oy, amount, held, active);
    }

    private static double Hypot(double x, double y)
    {
        var scale = Math.Max(Math.Abs(x), Math.Abs(y));
        return scale == 0 ? 0 : scale * Math.Sqrt((x / scale) * (x / scale) + (y / scale) * (y / scale));
    }
}

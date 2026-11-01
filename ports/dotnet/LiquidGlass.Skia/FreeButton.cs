// Apache-2.0. The free button: Compose's GlassInteraction.Pullable with GlassPullShape.Free (the Phone
// scene's search button, measured on iOS 26's in IMG_6756). Each value restarts from where it is when its
// target changes, as Compose's animateFloatAsState does; the laws are Modifier.liquidGlass's own
// (glassPressScale, glassPullTargetScale, glassPullDeformation without anchoring, glassFreeLanding).
// Checked frame by frame against the Kotlin-exported ports/skia/fixtures/free-button.json.
namespace LiquidGlass.Skia;

/// <summary>The drawing transform of a <see cref="FreeButton"/>: press scale (both axes), a stretch
/// along and across <see cref="Angle"/> (degrees), then the offset; the glow and the lift it gives.</summary>
public readonly record struct FreeButtonTransform(double PressScale, double Along, double Across, double Angle,
    double OffsetX, double OffsetY, double Glow, double Lift, double PullX);

/// <summary>A free-standing round glass button pulled off its place. Pixels, monotonic seconds.</summary>
public sealed class FreeButton
{
    private sealed record Spec(double Tween, double Damping, double Stiffness)
    {
        public bool IsTween => Tween > 0;
    }
    private static readonly Spec GlowIn = new(.09, 0, 0), FreeGlow = new(.35, 0, 0);
    private static readonly Spec BalloonDown = new(0, .62, 644), PressUp = new(0, .72, 700);
    private static readonly Spec PullFollow = new(0, 1, 2500), FreeRelease = new(0, .45, 264);

    private sealed class Animated(double initial)
    {
        private double start, from = initial, velocity, target = initial;
        private Spec? spec;

        private (double Value, double Velocity) At(double time)
        {
            if (spec is null) return (from, 0);
            var ns = Math.Truncate(Math.Max(0, time - start) * 1e9);
            if (spec.IsTween)
            {
                // A tween reads its elapsed time to the nanosecond; a spring to the whole millisecond.
                var u = Math.Min(1, ns / 1e9 / spec.Tween);
                return (from + (target - from) * FastOutSlowIn(u), 0);
            }
            return SpringAt(from, velocity, target, Math.Floor(ns / 1e6) / 1000, spec.Damping, spec.Stiffness);
        }

        public double Value(double time) => At(time).Value;

        public void To(double time, double newTarget, Spec newSpec)
        {
            if (newTarget == target && spec is not null) return;
            var now = At(time);
            (start, from, velocity, target, spec) = (time, now.Value, now.Velocity, newTarget, newSpec);
        }
    }

    /// <summary>Compose's spring(dampingRatio, stiffness), unit mass, in closed form.</summary>
    internal static (double Value, double Velocity) SpringAt(double from, double velocity, double target, double t,
        double z, double stiffness)
    {
        double x0 = from - target, w = Math.Sqrt(stiffness);
        if (z < 1)
        {
            double wd = w * Math.Sqrt(1 - z * z), e = Math.Exp(-z * w * t), b = (velocity + z * w * x0) / wd;
            double c = Math.Cos(wd * t), s = Math.Sin(wd * t);
            return (target + e * (x0 * c + b * s), e * ((b * wd - z * w * x0) * c - (x0 * wd + z * w * b) * s));
        }
        if (z == 1)
        {
            double e = Math.Exp(-w * t), b = velocity + w * x0;
            return (target + (x0 + b * t) * e, (b - w * (x0 + b * t)) * e);
        }
        double q = w * Math.Sqrt(z * z - 1), r1 = -z * w + q, r2 = -z * w - q;
        double c2 = (velocity - r1 * x0) / (r2 - r1), c1 = x0 - c2;
        return (target + c1 * Math.Exp(r1 * t) + c2 * Math.Exp(r2 * t), c1 * r1 * Math.Exp(r1 * t) + c2 * r2 * Math.Exp(r2 * t));
    }

    /// <summary>Compose's FastOutSlowInEasing, cubic-bezier(0.4, 0, 0.2, 1).</summary>
    internal static double FastOutSlowIn(double u)
    {
        if (u <= 0 || u >= 1) return Math.Clamp(u, 0, 1);
        static double Bez(double t, double p1, double p2) => 3 * (1 - t) * (1 - t) * t * p1 + 3 * (1 - t) * t * t * p2 + t * t * t;
        double lo = 0, hi = 1, t = u;
        for (var i = 0; i < 40; i++) { t = (lo + hi) / 2; if (Bez(t, .4, .2) < u) lo = t; else hi = t; }
        return Bez(t, 0, 1);
    }

    private readonly double size, radius, density, balloon, limit, pressLift, follow, elongation, widthRatio, liftShare;
    private readonly Animated press = new(0), glow = new(0), px = new(0), py = new(0);
    private double anchor = double.NaN;
    private readonly Spec releaseX;
    private bool moved;

    public FreeButton(double size, double density = 1, double pressScale = 1.26, double pressGrowth = 8, double pressLift = .2,
        double pullFollow = .2, double pullElongation = .124, double pullWidthRatio = 1, double pullLimit = .5,
        double pullLiftShare = .65, double pullReleaseDampingX = .3)
    {
        if (!(size > 0) || !(density > 0)) throw new ArgumentOutOfRangeException(nameof(size), "Positive size and density required");
        (this.size, radius, this.density) = (size, size / 2, density);
        balloon = Math.Min(1 + 2 * pressGrowth * density / size, pressScale);
        limit = size * balloon * pullLimit;
        (this.pressLift, follow, elongation, widthRatio, liftShare) = (pressLift, pullFollow, pullElongation, pullWidthRatio, pullLiftShare);
        // Compose's GlassInteraction.pullReleaseDampingX: a separate x damping on release.
        releaseX = double.IsFinite(pullReleaseDampingX) ? new Spec(0, Math.Clamp(pullReleaseDampingX, .05, 2), 264) : FreeRelease;
    }

    public void Down(double time)
    {
        moved = false;
        glow.To(time, 1, GlowIn); press.To(time, 1, BalloonDown);
        px.To(time, 0, PullFollow); py.To(time, 0, PullFollow);
    }

    /// <summary>The total finger travel since <see cref="Down"/>.</summary>
    public void Move(double time, double dx, double dy)
    {
        var len = Math.Sqrt(dx * dx + dy * dy);
        if (!moved && len > 4 * density) { moved = true; glow.To(time, liftShare, FreeGlow); }
        if (len > 4 * density)
        {
            var raw = Math.Atan2(dy, dx) * 180 / Math.PI;
            anchor = raw;
        }
        var travel = len * follow;
        var k = !(len > 1e-5) || follow <= 0 ? 1 : limit * Math.Tanh(travel / limit) / travel;
        px.To(time, dx * k, PullFollow); py.To(time, dy * k, PullFollow);
    }

    public void Up(double time)
    {
        glow.To(time, 0, FreeGlow); press.To(time, 0, PressUp);
        px.To(time, 0, releaseX); py.To(time, 0, FreeRelease);
    }

    public FreeButtonTransform Sample(double time)
    {
        double scale = 1 + (balloon - 1) * press.Value(time), x = px.Value(time), y = py.Value(time);
        double len = Math.Sqrt(x * x + y * y);
        double along = 1, across = 1, angle = 0, tx = 0, ty = 0;
        if (len >= 1e-3)
        {
            double e = Extent(scale, x, y), acrossExtent = Extent(scale, -y, x);
            along = Math.Min(1 + elongation * len / Math.Max(e, 1), 1.6);
            var extension = (along - 1) * Math.Max(e, 1);
            across = Math.Max(1 - widthRatio * extension / Math.Max(acrossExtent, 1), .5);
            var nx2 = x * x / (len * len);
            var ny2 = y * y / (len * len);
            // Continuous screen-axis projection, matching glassPullDeformation.
            (along, across) = (along * ny2 + across * nx2, along * nx2 + across * ny2);
            angle = -90;
            tx = follow * x; ty = follow * y;
        }
        (along, across, angle, tx, ty) = Landing(along, across, angle, tx, ty);
        var landingAngle = angle * Math.PI / 180;
        var lx2 = Math.Pow(Math.Cos(landingAngle), 2);
        var ly2 = Math.Pow(Math.Sin(landingAngle), 2);
        (along, across) = (along * ly2 + across * lx2, along * lx2 + across * ly2);
        angle = -90;
        var g = glow.Value(time);
        return new(scale, along, across, angle, tx, ty, g, g * pressLift, x);
    }

    private double Extent(double s, double x, double y)
    {
        var len = Math.Sqrt(x * x + y * y);
        if (len < 1e-5) return size * s;
        double nx = Math.Abs(x) / len, ny = Math.Abs(y) / len;
        return 2 * radius * Math.Sqrt(s * s * nx * nx + s * s * ny * ny);
    }

    // glassFreeLanding: the edge away from the pull holds at rest; the overshoot lands as a squash.
    private (double, double, double, double, double) Landing(double along, double across, double angle, double tx, double ty)
    {
        if (!double.IsFinite(anchor)) return (along, across, angle, tx, ty);
        double a = anchor * Math.PI / 180, nx = Math.Cos(a), ny = Math.Sin(a);
        double restHalf = Math.Sqrt(radius * nx * radius * nx + radius * ny * radius * ny);
        double c = tx * nx + ty * ny, perp = -tx * ny + ty * nx;
        double d = (angle - anchor) * Math.PI / 180, cd = Math.Cos(d), sd = Math.Sin(d);
        var stretched = c >= 0;
        var axis = stretched ? Math.Sqrt(along * along * cd * cd + across * across * sd * sd) : 1;
        var side = stretched ? Math.Sqrt(along * along * sd * sd + across * across * cd * cd) : 1;
        var half = restHalf * axis;
        if (c - half >= -restHalf) return (along, across, angle, tx, ty);
        var newHalf = Math.Max((c + half + restHalf) / 2, restHalf * .5);
        double centre = c + half - newHalf, k = newHalf / half;
        return (axis * k, side * (1 + .2 * (1 - k)), anchor, centre * nx - perp * ny, centre * ny + perp * nx);
    }
}

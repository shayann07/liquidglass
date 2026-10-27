// Apache-2.0. The Calm navigation selector, ported from the Compose library's pose controller
// (GlassPoseDynamics.kt, GlassPoseGeometry.kt, GlassTabBar's gesture driver) by way of
// ports/skia/selector.mjs. Pure motion and geometry. Verified against the trajectories the Kotlin
// controller exports (ports/skia/fixtures/selector-calm.json); the Kotlin source is authoritative.
// Units: bar-local pixels (origin at the bar's top-left), seconds.
namespace LiquidGlass.Skia;

/// <summary>Authored presets; see GlassPoseSpec in the Compose library for every value's provenance.</summary>
public sealed record CalmSelectorSpec
{
    public double CentreOmega { get; init; } = 40; public double CentreZeta { get; init; } = 1;
    public double SpineOmega { get; init; } = 90; public double SpineZeta { get; init; } = 1;
    public double TapSpineOmega { get; init; } = 36; public double TapSpineZeta { get; init; } = .43;
    public double TapElongationSlots { get; init; } = .36; public double TapReferenceSpeed { get; init; } = 15;
    public double SpineReleaseOmega { get; init; } = 32; public double ShapeOmega { get; init; } = 32;
    public double ShapeZeta { get; init; } = .72; public double PressureOmega { get; init; } = 45;
    public double PressureZeta { get; init; } = 1; public double TapPressureOmega { get; init; } = 0;
    public double PressureFallOmega { get; init; } = 14; public double FormRiseOmega { get; init; } = 60;
    public double HeldFormRiseOmega { get; init; } = 0; public double FormOmega { get; init; } = 18;
    public double FormZeta { get; init; } = 1; public double HoldThresholdSeconds { get; init; } = .12;
    public double ReferenceSpeed { get; init; } = 11.5; public double HeldReferenceSpeed { get; init; } = 20;
    public double VelocityTau { get; init; } = .035; public double ReferenceAcceleration { get; init; } = 26;
    public double ElongationSlots { get; init; } = .5; public double StretchGain { get; init; } = 0;
    public double HeldTravelStrain { get; init; } = 0; public double HeldTravelStrainOmega { get; init; } = 10;
    public double MaxStrain { get; init; } = .42; public double PullStrainPerDp { get; init; } = .0038;
    public double PullPressurePerDp { get; init; } = -.0007; public double PullOnsetDp { get; init; } = 12;
    public double PullMaxStrain { get; init; } = .12; public double PullFollow { get; init; } = .2;
    public double PullFollowCapRatio { get; init; } = .1; public double EndTravelCapRatio { get; init; } = .08;
    public double PullTiltSeconds { get; init; } = .08; public double HeldHeightRatio { get; init; } = 1.183;
    public double Squeeze { get; init; } = .4; public double TaperFromAcceleration { get; init; } = 0;
    public double TaperFromVelocity { get; init; } = 0; public double MaxTaper { get; init; } = 0;
    public double FormFromMovement { get; init; } = .25; public double PressFormation { get; init; } = .3;
    public double MaxAccommodationSlots { get; init; } = .2; public double AccommodationEngageSpeed { get; init; } = .35;
    public double AccommodationReleaseSpeed { get; init; } = .8; public double AccommodationDwellSeconds { get; init; } = .15;
    public double AccommodationOmega { get; init; } = 16; public double AdmitPaddingDp { get; init; } = 6;
    public double AdmitInsetDp { get; init; } = 2; public double AdmitReleaseDp { get; init; } = 4;
    public double GraspOmega { get; init; } = 78; public double AccelerationTau { get; init; } = .045;
    public double StopWindowSeconds { get; init; } = .04; public bool TravelOnly { get; init; }
    public bool ReleaseTravel { get; init; } public bool TapRecoilArea { get; init; }
    public double HeldTravelPressureOmega { get; init; } = 0; public double TapSpineLimitSlots { get; init; } = .36;
    public double TapAreaGain { get; init; } = 0; public double TapAreaOmega { get; init; } = 42;
    public double TapAreaReferenceSpeed { get; init; } = 2.4; public double TapBodyElongation { get; init; } = .15;
    public double TapSpineFallOmega { get; init; } = 12; public double TapAreaSpineOmega { get; init; } = 40;
    public double TapAreaSpineZeta { get; init; } = .38; public double TapAreaBodySpeed { get; init; } = 10.2;
    public double ReleaseImpactGain { get; init; } = 0; public bool ReleaseEdgeAnchor { get; init; }
    public double ReleaseRecoveryOmega { get; init; } = 0; public double ReleaseRecoveryZeta { get; init; } = 1;
    public bool ReleaseTrailingEdge { get; init; } public double TapShortTripRate { get; init; } = 1;
    public double ReleaseTrailingInOmega { get; init; } = 0; public double ReleaseTrailingInZeta { get; init; } = 1;
    public double ReleaseTrailingCarry { get; init; } = 1; public double ReleaseWallGive { get; init; } = 0;
    public double ReleaseTrailingStiffening { get; init; } = 0;
    public double TapAppearanceArea { get; init; } = 0; public double TapAppearanceSpine { get; init; } = 0;
    public double TapAppearanceFallOmega { get; init; } = 0; public double ReleaseFormationSpeed { get; init; } = 0;
    public double ReleaseStillHoldSeconds { get; init; } = 0;
    /// <summary>The held lens's superellipse blend (#93) at full formation, and its exponent.</summary>
    public double HeldSquircle { get; init; } = 0; public double SquircleExponent { get; init; } = 3;

    /// <summary>GlassPoseSpec.Calm.</summary>
    public static CalmSelectorSpec Calm { get; } = new()
    {
        CentreOmega = 24, SpineOmega = 30, SpineZeta = .78, TapSpineOmega = 26, TapSpineZeta = .45,
        TapElongationSlots = .36, TapReferenceSpeed = 10, SpineReleaseOmega = 22, PressureOmega = 22,
        PressureFallOmega = 14, FormRiseOmega = 30, TapPressureOmega = 45, HeldHeightRatio = 1.183,
        HeldReferenceSpeed = 8, ElongationSlots = .28, Squeeze = .47, PullStrainPerDp = 0,
        PullPressurePerDp = 0, PullFollow = 0, EndTravelCapRatio = .025, MaxAccommodationSlots = .15,
        TravelOnly = true, ReleaseTravel = true, TapRecoilArea = true, HeldTravelPressureOmega = 60,
        TapSpineLimitSlots = .45, TapAreaGain = .51, TapShortTripRate = .75, TapAppearanceArea = .44,
        TapAppearanceSpine = .13, TapAppearanceFallOmega = 45, ReleaseFormationSpeed = 3,
        GraspOmega = 120, HeldFormRiseOmega = 50, ReleaseTrailingEdge = true, ReleaseRecoveryOmega = 13,
        ReleaseTrailingInOmega = 9, ReleaseTrailingInZeta = .3, ReleaseTrailingCarry = .1, ReleaseWallGive = .35,
        ReleaseTrailingStiffening = .5, ReleaseStillHoldSeconds = .18, HeldTravelStrain = .6, HeldTravelStrainOmega = 10,
        HeldSquircle = .7,
    };
}

/// <summary>The bar as the selector sees it (buildGlassSelectorBar, pose path). Pixels. A finite
/// <see cref="FixedSlot"/> (GlassTabBarStyle.slotWidth) lays equal slots of that width centred in the bar,
/// each centre held at least <see cref="EndReach"/> from either end; NaN shares the bar out inside <see cref="Inset"/>.</summary>
public sealed record CalmSelectorBar(double Width, double Height, double Inset, int Count, double CornerRadius,
    double BaseHalfWidth, double BaseHalfHeight, double AllowedHalfHeight, double Density, double SlotWidth,
    double FixedSlot = double.NaN, double EndReach = 0)
{
    private bool Fixed => double.IsFinite(FixedSlot) && FixedSlot > 0;

    public double CentreOf(int index) => Fixed ? FixedCentre(Width, Count, FixedSlot, EndReach, index) : Inset + (index + .5) * SlotWidth;

    /// <summary>The tab under a bar-local x: the nearest centre for set slots, the slot under it otherwise.</summary>
    public int NearestTab(double x)
    {
        if (!Fixed) return Math.Clamp((int)Math.Floor((x - Inset) / SlotWidth), 0, Count - 1);
        var i = 0;
        while (i < Count - 1 && x > (CentreOf(i) + CentreOf(i + 1)) / 2) i++;
        return i;
    }

    // glassFixedSlotCentre: equal slots centred in the bar, held endReach from either end.
    private static double FixedCentre(double width, int count, double slot, double endReach, int i)
    {
        double mid = width / 2, lo = Math.Min(endReach, mid);
        return Math.Clamp(mid + (i - (count - 1) / 2.0) * slot, lo, width - lo);
    }

    public static CalmSelectorBar Create(double width, double height, int count, double inset = 6,
        double? pillWidth = null, double pillInset = 4, double density = 1, double? slotWidth = null)
    {
        if (!double.IsFinite(width) || !double.IsFinite(height) || width <= 0 || height <= 0 || count < 1 ||
            !double.IsFinite(density) || density <= 0 || inset < 0 || pillInset < 0)
            throw new ArgumentOutOfRangeException(nameof(width), "Positive bar size, count and nonnegative insets required");
        var isFixed = slotWidth is double sw && double.IsFinite(sw) && sw > 0;
        var slot = isFixed ? slotWidth!.Value : Math.Max((width - inset * 2) / count, 0);
        var pill = pillWidth is double p && double.IsFinite(p) ? Math.Max(p, slot) : slot;
        double fixedSlot = isFixed ? slot : double.NaN, endReach = isFixed ? pill / 2 + pillInset : 0;
        var first = isFixed ? FixedCentre(width, count, slot, endReach, 0) : inset + slot / 2;
        var rest = Math.Min(pill / 2, Math.Max(first - density, density));
        var half = Math.Min(height / 2 - pillInset, rest);
        return new(width, height, inset, count, height / 2, rest, half, Math.Max(half, height / 2 - density), density, slot,
            fixedSlot, endReach);
    }
}

public enum CalmSelectorMode { Rest, TapTransit, PressPending, Held, Released }

/// <summary>Shader pose uniforms: A, A^-1 row-major, (cx, cy, bx, by) bar-local, (spine, radius, scaleX, band).</summary>
public readonly record struct CalmSelectorPose(float[] A, float[] AInv, float[] C, float[] D)
{
    /// <summary>uPoseE: (squircle amount, exponent, 0, 0); null draws the capsule.</summary>
    public float[]? E { get; init; }
}
public readonly record struct CalmSelectorExtents(double Left, double Right, double Top, double Bottom)
{
    public double Width => Right - Left;
    public double Height => Bottom - Top;
}

internal sealed class SelectorSpring
{
    public double Value, Velocity, Target;
    public void SnapTo(double x) { Value = x; Target = x; Velocity = 0; }
    public void Step(double dt, double omega, double zeta)
    {
        if (dt <= 0 || omega <= 0) return;
        var d = Value - Target;
        if (d == 0 && Velocity == 0) return;
        var z = Math.Clamp(zeta, 0, 1);
        if (z >= .9999)
        {
            var e = Math.Exp(-omega * dt);
            var dNew = e * ((1 + omega * dt) * d + dt * Velocity);
            var vNew = e * ((1 - omega * dt) * Velocity - omega * omega * dt * d);
            Value = Target + dNew; Velocity = vNew;
        }
        else
        {
            double a = z * omega, b = omega * Math.Sqrt(1 - z * z), e = Math.Exp(-a * dt);
            double cb = Math.Cos(b * dt), sb = Math.Sin(b * dt);
            var dNew = e * (d * cb + (Velocity + a * d) * sb / b);
            var vNew = e * (Velocity * cb - (a * Velocity + omega * omega * d) * sb / b);
            Value = Target + dNew; Velocity = vNew;
        }
    }
}

internal sealed class SelectorPose
{
    public double Cx, Cy, P, U, V, Kx, Ky, Acc, Dx, Dy;
    public void Set(SelectorPose o) { Cx = o.Cx; Cy = o.Cy; P = o.P; U = o.U; V = o.V; Kx = o.Kx; Ky = o.Ky; Acc = o.Acc; Dx = o.Dx; Dy = o.Dy; }
    public void Reset() { Cx = Cy = P = U = V = Kx = Ky = Acc = Dx = Dy = 0; }
    public double Rho => Math.Sqrt(U * U + V * V);
}

internal sealed record SelectorReference(double A, double R)
{
    public bool Spine => A > 1e-3;
    public double Width => 2 * (A + R);
    public double Height => 2 * R;
}

internal sealed class SelectorFrame
{
    public SelectorReference Reference = new(1, 1);
    public readonly double[] A = [1, 0, 0, 1], AInv = [1, 0, 0, 1];
    public double Bx, By, Spine, Radius, MaxAbsZ = 1, ScaleX = 1, Cx, Cy, ExpP = 1, MinStretch = 1, CosTheta = 1, SinTheta, HalfSpine;
    private SelectorReference? contourReference; private double[] contourPoints = [];

    public void Update(SelectorReference reference, SelectorPose pose)
    {
        Reference = reference; Cx = pose.Cx; Cy = pose.Cy;
        var r = pose.Rho; ExpP = Math.Exp(pose.P);
        double h, s;
        if (r < 1e-3) { var r2 = r * r; h = 1 + r2 / 2 + r2 * r2 / 24; s = 1 + r2 / 6 + r2 * r2 / 120; }
        else { h = Math.Cosh(r); s = Math.Sinh(r) / r; }
        double ex = pose.Dx, ey = pose.Dy;
        if (ex * ex + ey * ey <= 1e-6) ex = reference.A * ExpP;
        if (reference.Spine) ex += Math.Max(pose.Acc, 0);
        var dl = Math.Sqrt(ex * ex + ey * ey);
        if (dl > 1e-3)
        {
            double c2 = ex / dl, s2 = ey / dl;
            CosTheta = Math.Sqrt(Math.Max((1 + c2) / 2, 0));
            var st = Math.Sqrt(Math.Max((1 - c2) / 2, 0)); SinTheta = s2 < 0 ? -st : st; HalfSpine = dl;
        }
        else { CosTheta = 1; SinTheta = 0; HalfSpine = 0; }
        double ct = CosTheta, sn = SinTheta, e = ExpP;
        double s00 = h + s * pose.U, s01 = s * pose.V, s11 = h - s * pose.U;
        A[0] = e * (ct * s00 - sn * s01); A[1] = e * (ct * s01 - sn * s11); A[2] = e * (sn * s00 + ct * s01); A[3] = e * (sn * s01 + ct * s11);
        double inv = 1 / e, i00 = h - s * pose.U, i01 = -s * pose.V, i11 = h + s * pose.U;
        AInv[0] = inv * (i00 * ct - i01 * sn); AInv[1] = inv * (i00 * sn + i01 * ct); AInv[2] = inv * (i01 * ct - i11 * sn); AInv[3] = inv * (i01 * sn + i11 * ct);
        if (reference.Spine) { ScaleX = 1; Spine = Math.Max(HalfSpine * inv, 0); Radius = reference.R; MaxAbsZ = Spine + Radius; }
        else
        {
            ScaleX = Math.Exp(pose.Acc); Spine = Math.Max(HalfSpine * inv, 0) * ScaleX; Radius = reference.R;
            MaxAbsZ = Math.Max(ScaleX * (HalfSpine * inv + reference.R), Math.Sqrt(Spine * Spine + Radius * Radius));
        }
        var rs = Math.Max(MaxAbsZ, 1e-4); Bx = pose.Kx / rs; By = pose.Ky / rs;
        var taper = Math.Sqrt(pose.Kx * pose.Kx + pose.Ky * pose.Ky);
        MinStretch = e * Math.Exp(-r) * Math.Max(1 - taper, 1e-3);
    }
    public (double, double) MaterialToZ(double qx, double qy)
    {
        if (Reference.Spine)
        {
            var ell = Spine - Reference.A; var phi = Reference.A > 1e-3 ? Math.Clamp(qx / Reference.A, -1, 1) : 0;
            return (qx + ell * phi, qy);
        }
        return (qx * ScaleX, qy);
    }
    public (double, double) ZToMaterial(double zx, double zy)
    {
        if (Reference.Spine)
        {
            var ell = Spine - Reference.A;
            return (Math.Abs(zx) <= Spine ? (Spine > 1e-3 ? zx * Reference.A / Spine : zx) : zx - (zx >= 0 ? ell : -ell), zy);
        }
        return (ScaleX > 1e-6 ? zx / ScaleX : zx, zy);
    }
    public (double, double) ZToRendered(double zx, double zy)
    {
        var s = 1 + Bx * zx + By * zy; double fx = s * zx, fy = s * zy;
        return (Cx + A[0] * fx + A[1] * fy, Cy + A[2] * fx + A[3] * fy);
    }
    public (double, double) MaterialToRendered(double qx, double qy) { var (zx, zy) = MaterialToZ(qx, qy); return ZToRendered(zx, zy); }
    public bool RenderedToZ(double x, double y, out double zx, out double zy)
    {
        double dx = x - Cx, dy = y - Cy;
        double yx = AInv[0] * dx + AInv[1] * dy, yy = AInv[2] * dx + AInv[3] * dy;
        var disc = 1 + 4 * (Bx * yx + By * yy);
        if (disc < 0) { zx = yx; zy = yy; return false; }
        var lambda = 2 / (1 + Math.Sqrt(disc)); zx = lambda * yx; zy = lambda * yy; return true;
    }
    public double Coverage(double x, double y)
    {
        if (!RenderedToZ(x, y, out var zx, out var zy)) return Math.Max(Math.Sqrt(zx * zx + zy * zy), 1e-4);
        double ex, ey, half, rad;
        if (Reference.Spine) { half = Spine; rad = Radius; ex = zx - Math.Clamp(zx, -half, half); ey = zy; }
        else { var (qx, qy) = ZToMaterial(zx, zy); half = Reference.A; rad = Reference.R; ex = qx - Math.Clamp(qx, -half, half); ey = qy; }
        var len = Math.Sqrt(ex * ex + ey * ey); var d = len - rad;
        double n0x = 0, n0y = 1;
        if (len > 1e-5) { n0x = ex / len; n0y = ey / len; }
        var s = 1 + Bx * zx + By * zy;
        double j0 = s + zx * Bx, j1 = zx * By, j2 = zy * Bx, j3 = s + zy * By;
        if (!Reference.Spine) { j0 *= ScaleX; j2 *= ScaleX; }
        double m0 = A[0] * j0 + A[1] * j2, m1 = A[0] * j1 + A[1] * j3, m2 = A[2] * j0 + A[3] * j2, m3 = A[2] * j1 + A[3] * j3;
        var det = m0 * m3 - m1 * m2; var invDet = Math.Abs(det) > 1e-9 ? 1 / det : 0;
        double gx = invDet * (m3 * n0x - m2 * n0y), gy = invDet * (-m1 * n0x + m0 * n0y);
        var gl = Math.Sqrt(gx * gx + gy * gy);
        return gl > 1e-7 ? d / gl : d;
    }
    public double Band() => .6 * Radius * MinStretch;
    public void Contour(int n, double[] into)
    {
        if (!ReferenceEquals(contourReference, Reference) || contourPoints.Length != n * 2)
        {
            contourReference = Reference; contourPoints = new double[n * 2];
            for (var i = 0; i < n; i++) { var (qx, qy) = CanonicalPoint(Reference, (double)i / n); contourPoints[i * 2] = qx; contourPoints[i * 2 + 1] = qy; }
        }
        for (var i = 0; i < n; i++) { var (x, y) = MaterialToRendered(contourPoints[i * 2], contourPoints[i * 2 + 1]); into[i * 2] = x; into[i * 2 + 1] = y; }
    }
    private static (double, double) CanonicalPoint(SelectorReference reference, double t)
    {
        double a = reference.A, r = reference.R, capLen = Math.PI * r, runLen = 2 * a, total = 2 * capLen + 2 * runLen;
        if (total <= 1e-6) return (0, 0);
        var s = t * total;
        if (s < capLen) { var ang = -Math.PI / 2 + s / capLen * Math.PI; return (a + r * Math.Cos(ang), r * Math.Sin(ang)); }
        s -= capLen;
        if (s < runLen) return (a - s, r);
        s -= runLen;
        if (s < capLen) { var ang = Math.PI / 2 + s / capLen * Math.PI; return (-a + r * Math.Cos(ang), r * Math.Sin(ang)); }
        s -= capLen;
        return (-a + s, -r);
    }
}

/// <summary>GlassPoseController with the Calm preset. Pointer facts in the bar frame; read state each frame.
/// One instance per selector and thread.</summary>
public sealed class CalmSelectorController
{
    private const int ContourSamples = 256, QuietSteps = 8, MaxContactIterations = 24;
    private const double QuietPx = .02, ContactTolerance = .05, MaxContactStep = 12, MaxContactPressureStep = .06,
        ContactRegainMargin = 0, ContactRegainAccommodation = 1, AccommodationResolvePx = 1,
        AccommodationResolvePressure = .01, CoverageEpsilon = .5, ContactRegainFormation = .02, RegrabFormation = .15,
        WallPullHeights = 20, ReleaseImpactReach = .3, ReleaseStillSlots = .5;
    private double releasedAt; private bool releasedStill;
    private bool trailImpact;

    private readonly SelectorPose pose = new(), velocity = new(), equilibrium = new(), lastGood = new(), lastGoodVelocity = new(),
        lastPublished = new(), probePose = new(), scratchPose = new();
    private readonly SelectorFrame frame = new(), probeFrame = new(), derivFrame = new();
    private readonly SelectorSpring strainDrive = new();
    private readonly SelectorSpring form = new(), transit = new(), travel = new(), tapArea = new(), releaseEdge = new(),
        releaseTrail = new(), graspX = new(), graspY = new(), scratch = new();
    private readonly double[] contour = new double[ContourSamples * 2];
    private SelectorReference reference = new(1, 1);
    private CalmSelectorBar? bar;
    private bool tapAreaActive, releaseImpactActive, releaseEdgeActive, downOnBody, pressEligible, pointerDownActive,
        hasGrasp, accommodating, releasedFromHold, hasPointerTime, hasLastGood, accommodationValid;
    private double tapDistance, now, pressStart, downX, downY, lastSampleAt, graspQx, graspQy, graspY0, slowFor,
        pointerX, pointerY, pointerVx, pointerVy, lastPointerX, lastPointerY, lastPointerTime, pointerClockOrigin,
        pointerSimulationOrigin, accelX, accelY, lastVx, lastVy, inkHalfHeight, formLast,
        accommodationGrasp = double.NaN, accommodationGraspY = double.NaN, accommodationPressure = double.NaN, accommodationDemand;
    private int requestedIndex, quietSteps, admittedNeighbour = -1;
    private double[] itemLeft = [], itemRight = [], itemCentre = [];
    private double shapeVelX, shapeVelY;

    public CalmSelectorController(CalmSelectorSpec? spec = null) { Spec = spec ?? CalmSelectorSpec.Calm; }

    public CalmSelectorSpec Spec { get; }
    public CalmSelectorMode Mode { get; private set; } = CalmSelectorMode.Rest;
    public bool MotionEnabled { get; set; } = true;
    public int RestIndex { get; private set; }
    public double Formation => Math.Clamp(form.Value, 0, 1);
    public double TransitAppearance => Math.Clamp(transit.Value, 0, .95);
    public double HeldTravel => Math.Clamp(travel.Value, 0, 1);
    public bool IsHeld => Mode == CalmSelectorMode.Held;
    public double CentreX => pose.Cx;
    public double CentreY => pose.Cy;
    public CalmSelectorBar Bar => bar ?? throw new InvalidOperationException("Attach a bar first");
    public bool IsIdle => Mode != CalmSelectorMode.PressPending && !tapAreaActive && TransitAppearance < 1e-4 &&
        HeldTravel < 1e-4 && quietSteps >= QuietSteps && strainDrive.Value == 0 &&
        // A still release keeping its glass: nothing moves yet, but the formation still has to fall.
        !(Mode == CalmSelectorMode.Released && releasedFromHold && releasedStill && form.Value > .01 &&
          now - releasedAt < Spec.ReleaseStillHoldSeconds + .05);
    private bool started;

    public void Attach(CalmSelectorBar bar, IReadOnlyList<double>? itemBounds = null, IReadOnlyList<double>? itemCentres = null, double? inkHalfHeight = null)
    {
        ArgumentNullException.ThrowIfNull(bar);
        var first = this.bar is null; var relayout = first || this.bar != bar;
        this.bar = bar;
        var ink = Math.Max(inkHalfHeight ?? this.inkHalfHeight, 0);
        if (this.inkHalfHeight != ink) accommodationValid = false;
        this.inkHalfHeight = ink;
        if (itemBounds is not null && itemCentres is not null && itemCentres.Count > 0)
        {
            itemLeft = Enumerable.Range(0, itemCentres.Count).Select(i => itemBounds[i * 2]).ToArray();
            itemRight = Enumerable.Range(0, itemCentres.Count).Select(i => itemBounds[i * 2 + 1]).ToArray();
            itemCentre = itemCentres.ToArray(); accommodationValid = false;
        }
        if (!relayout) return;
        var r = Math.Max(bar.BaseHalfHeight, 1);
        reference = new SelectorReference(Math.Max(bar.BaseHalfWidth - r, 0), r);
        if (!first && started) { ClearOwnership(); SnapToRest(requestedIndex); }
    }

    public void SnapToRest(int index)
    {
        var b = Bar;
        releaseEdgeActive = false; tapAreaActive = false; tapDistance = 0; releaseImpactActive = false;
        requestedIndex = index; RestIndex = Math.Clamp(index, 0, Math.Max(b.Count - 1, 0));
        pose.Reset(); velocity.Reset();
        pose.Cx = b.CentreOf(RestIndex); pose.Cy = b.Height / 2; pose.Dx = reference.A; pose.Dy = 0;
        form.SnapTo(0); transit.SnapTo(0); graspX.SnapTo(pose.Cx); graspY.SnapTo(pose.Cy); hasGrasp = false;
        admittedNeighbour = -1; Mode = CalmSelectorMode.Rest; started = true;
        frame.Update(reference, pose); CaptureLastGood(); lastPublished.Set(pose); formLast = 0; quietSteps = QuietSteps;
    }

    public void Retarget(int index)
    {
        var b = Bar; var n = Math.Max(b.Count - 1, 0);
        if (index != requestedIndex && Mode != CalmSelectorMode.Held && Mode != CalmSelectorMode.Released)
            tapDistance = Math.Abs(b.CentreOf(Math.Clamp(index, 0, n)) - pose.Cx);
        requestedIndex = index; RestIndex = Math.Clamp(index, 0, n);
        if (Math.Abs(pose.Cx - b.CentreOf(RestIndex)) >= .25) { quietSteps = 0; if (Mode == CalmSelectorMode.Rest) Mode = CalmSelectorMode.TapTransit; }
    }

    private void ClearOwnership()
    {
        releaseEdgeActive = false; releaseImpactActive = false; accommodationValid = false; pressEligible = false;
        pointerDownActive = false; hasGrasp = false; pointerVx = pointerVy = 0; accelX = accelY = 0; admittedNeighbour = -1;
    }

    public void PointerDown(double x, double y, double seconds, bool eligible)
    {
        releaseEdgeActive = false; releaseImpactActive = false; pressStart = now; pressEligible = eligible; pointerDownActive = true;
        downX = x; downY = y; frame.Update(reference, pose);
        downOnBody = frame.Coverage(x, y) <= 0 && frame.RenderedToZ(x, y, out _, out _);
        pointerX = x; pointerY = y; lastPointerX = x; lastPointerY = y; lastPointerTime = seconds; pointerClockOrigin = seconds;
        pointerSimulationOrigin = now; hasPointerTime = true; pointerVx = pointerVy = 0; accelX = accelY = 0; lastVx = lastVy = 0;
        lastSampleAt = now; quietSteps = 0;
        var regrab = Mode == CalmSelectorMode.Released && eligible && MotionEnabled && form.Value > RegrabFormation;
        Mode = CalmSelectorMode.PressPending;
        if (regrab) { AcquireGrasp(x, y, downOnBody); Mode = CalmSelectorMode.Held; }
    }

    public void PointerMove(double x, double y, double seconds)
    {
        var s = Spec;
        if (s.TravelOnly && hasPointerTime && double.IsFinite(seconds))
        {
            var eventTime = pointerSimulationOrigin + (seconds - pointerClockOrigin);
            if (double.IsFinite(eventTime) && eventTime > now) AdvanceTo(eventTime);
        }
        pointerX = x; pointerY = y; lastSampleAt = now; quietSteps = 0;
        if (!hasPointerTime) return;
        var dt = seconds - lastPointerTime;
        if (dt <= 1e-5) return;
        double vx;
        if (s.TravelOnly && Mode == CalmSelectorMode.Held && hasGrasp)
        {
            var (ox, _) = frame.MaterialToRendered(graspQx, graspQy); var offset = ox - pose.Cx;
            vx = (AnchoredCentre(x - offset) - AnchoredCentre(lastPointerX - offset)) / dt;
        }
        else vx = (x - lastPointerX) / dt;
        var vy = (y - lastPointerY) / dt;
        var alpha = 1 - Math.Exp(-dt / Math.Max(s.AccelerationTau, 1e-4));
        accelX += alpha * ((vx - lastVx) / dt - accelX); accelY += alpha * ((vy - lastVy) / dt - accelY);
        lastVx = vx; lastVy = vy;
        var va = 1 - Math.Exp(-dt / Math.Max(s.VelocityTau, 1e-4));
        pointerVx += va * (vx - pointerVx); pointerVy += va * (vy - pointerVy);
        lastPointerX = x; lastPointerY = y; lastPointerTime = seconds;
    }

    public void BeginDrag(double x, double y)
    {
        pointerX = x; pointerY = y; quietSteps = 0;
        if (Mode != CalmSelectorMode.Held) { AcquireGrasp(downX, downY, downOnBody); Mode = CalmSelectorMode.Held; }
    }

    public void PointerUp(int restIndex)
    {
        var b = Bar; var s = Spec;
        quietSteps = 0; requestedIndex = restIndex; RestIndex = Math.Clamp(restIndex, 0, Math.Max(b.Count - 1, 0));
        releasedFromHold = Mode == CalmSelectorMode.Held; releasedAt = now;
        releasedStill = Math.Abs(velocity.Cx) < ReleaseStillSlots * Math.Max(Bar.SlotWidth, 1);
        releaseImpactActive = releasedFromHold && (s.ReleaseImpactGain > 0 || s.ReleaseTrailingEdge) &&
            (RestIndex == 0 || RestIndex == b.Count - 1) && Math.Abs(velocity.Cx) > reference.Width * .6;
        Mode = releasedFromHold ? CalmSelectorMode.Released : CalmSelectorMode.TapTransit;
        pressEligible = false; pointerDownActive = false; hasGrasp = false; pointerVx = pointerVy = 0; accelX = accelY = 0;
    }
    public void Cancel(int restIndex) => PointerUp(restIndex);

    private void AcquireGrasp(double x, double y, bool onBody)
    {
        accommodationValid = false; frame.Update(reference, pose);
        frame.RenderedToZ(x, y, out var zx, out var zy); var (qx, qy) = frame.ZToMaterial(zx, zy);
        double a = reference.A, r = Math.Max(reference.R, 1e-3);
        var ex = qx - Math.Clamp(qx, -a, a); var len = Math.Sqrt(ex * ex + qy * qy);
        if (len > r) { var sc = r / len; qx = Math.Clamp(qx, -a, a) + ex * sc; qy *= sc; }
        if (!onBody) { qx = 0; qy = (y - Bar.Height / 2) / frame.ExpP; }
        graspY0 = y; slowFor = 0; accommodating = false; graspQx = qx; graspQy = qy; hasGrasp = true;
        var (ox, oy) = frame.MaterialToRendered(qx, qy);
        graspX.SnapTo(ox); graspY.SnapTo(oy); graspX.Target = x; graspY.Target = y;
    }

    public void AdvanceFrameTo(double time) { if (Spec.TravelOnly && time < now) return; AdvanceTo(time); }
    public double ResumedFrameTime(double previous) => Spec.TravelOnly ? Math.Max(previous, now) : previous;

    public void AdvanceTo(double time, double maxCatchUpSeconds = .5)
    {
        if (double.IsNaN(time)) return;
        if (time < now) { var shift = time - now; now = time; pressStart += shift; lastSampleAt += shift; pointerSimulationOrigin += shift; return; }
        var remaining = time - now;
        if (remaining <= 0) return;
        if (!MotionEnabled) { SnapToRest(RestIndex); now = time; return; }
        if (remaining > maxCatchUpSeconds)
        {
            if (!pointerDownActive && (Mode == CalmSelectorMode.Held || Mode == CalmSelectorMode.PressPending)) { pointerVx = pointerVy = 0; Mode = CalmSelectorMode.Released; }
            remaining = maxCatchUpSeconds;
        }
        var maxStep = releaseImpactActive || (Spec.TapAreaGain > 0 && tapDistance > .25 &&
            (Mode == CalmSelectorMode.TapTransit || Mode == CalmSelectorMode.PressPending)) ? 1.0 / 960 : 1.0 / 240;
        while (remaining > 1e-7) { var dt = Math.Min(remaining, maxStep); Substep(dt); remaining -= dt; now += dt; }
        now = time;
    }

    private (double, double) StepState(double dt, double value, double vel, double target, double omega, double zeta)
    {
        scratch.Value = value; scratch.Velocity = vel; scratch.Target = target; scratch.Step(dt, omega, zeta);
        return (scratch.Value, scratch.Velocity);
    }
    private static double Bounded(double travel, double limit)
    {
        if (!double.IsFinite(limit)) return travel;
        if (limit <= 0) return 0;
        var cap = Math.Max(limit, 1e-3); return cap * Math.Tanh(travel / cap);
    }
    private double AnchoredCentre(double x)
    {
        var b = Bar; double lower = b.CentreOf(0), upper = b.CentreOf(b.Count - 1), limit = Spec.EndTravelCapRatio * b.Height;
        return x < lower ? lower + Bounded(x - lower, limit) : x > upper ? upper + Bounded(x - upper, limit) : x;
    }
    private static double HeldPressure(double ratio, double barHeight, double referenceHeight) =>
        referenceHeight <= 1e-4 || barHeight <= 1e-4 ? 0 : Math.Log(ratio * barHeight / referenceHeight);
    // The held travel strain (HeldTravelStrain) widens the rendered body by e^u; zero leaves it 1.
    private double StrainScale() => Spec.HeldTravelStrain > 0 ? Math.Exp(pose.U) : 1;
    private double LandingHalfWidth() => (reference.R * Math.Exp(pose.P) + pose.Dx + Math.Max(pose.Acc, 0)) * StrainScale();
    private double LandingHalfWidthVelocity()
    {
        var eu = StrainScale();
        var b = reference.R * Math.Exp(pose.P) + pose.Dx + Math.Max(pose.Acc, 0);
        return (reference.R * Math.Exp(pose.P) * velocity.P + velocity.Dx + (pose.Acc > 0 ? velocity.Acc : 0)) * eu +
            (eu != 1 ? b * eu * velocity.U : 0);
    }

    private void Substep(double dt)
    {
        var s = Spec; var b = Bar; var refr = reference;
        if (!MotionEnabled && (Mode == CalmSelectorMode.Held || Mode == CalmSelectorMode.PressPending)) { ClearOwnership(); Mode = CalmSelectorMode.Rest; }
        var heldNow = MotionEnabled && (Mode == CalmSelectorMode.Held ||
            (Mode == CalmSelectorMode.PressPending && pressEligible && now - pressStart >= s.HoldThresholdSeconds));
        var slot = Math.Max(b.SlotWidth, 1);
        double vx, vy;
        if (Mode == CalmSelectorMode.Held)
        {
            if (now - lastSampleAt > s.StopWindowSeconds) { var decay = Math.Exp(-dt / .06); pointerVx *= decay; pointerVy *= decay; accelX *= decay; accelY *= decay; }
            vx = pointerVx; vy = pointerVy;
        }
        else if (Mode == CalmSelectorMode.Released) { vx = s.ReleaseTravel ? velocity.Cx : 0; vy = 0; }
        else { vx = velocity.Cx; vy = velocity.Cy; }
        double nvx = vx / slot, nvy = s.TravelOnly ? 0 : vy / slot, speed2 = nvx * nvx + nvy * nvy, speed = Math.Sqrt(speed2);
        var tapMotion = Mode != CalmSelectorMode.Held && (Mode != CalmSelectorMode.Released || s.ReleaseTravel);
        var coupleTapArea = MotionEnabled && s.TapAreaGain > 0 && tapDistance > .25 && (Mode == CalmSelectorMode.TapTransit || Mode == CalmSelectorMode.PressPending);
        double tapClock = 1;
        if (coupleTapArea)
        {
            var phase = Math.Clamp(tapDistance / slot - 1, 0, 1); var blend = phase * phase * (3 - 2 * phase);
            var shortRate = Math.Clamp(s.TapShortTripRate, .5, 1); tapClock = shortRate + (1 - shortRate) * blend;
        }
        var tapShapeVx = vx / tapClock;
        var coupleReleaseArea = MotionEnabled && releaseImpactActive && Mode == CalmSelectorMode.Released;
        var coupleReleaseEdge = coupleReleaseArea && (s.ReleaseEdgeAnchor || s.ReleaseTrailingEdge) && s.TravelOnly &&
            s.StretchGain == 0 && s.TaperFromAcceleration == 0 && s.TaperFromVelocity == 0 && refr.Spine;
        double dir = RestIndex == 0 ? -1 : 1;
        if (coupleReleaseEdge && !releaseEdgeActive)
        {
            releaseEdge.Value = pose.Cx + dir * LandingHalfWidth(); releaseEdge.Velocity = velocity.Cx + dir * LandingHalfWidthVelocity();
            releaseEdge.Target = b.CentreOf(RestIndex) + dir * refr.Width * .5;
            releaseTrail.Value = pose.Cx - dir * LandingHalfWidth(); releaseTrail.Velocity = velocity.Cx - dir * LandingHalfWidthVelocity(); trailImpact = false;
            releaseTrail.Target = b.CentreOf(RestIndex) - dir * refr.Width * .5;
        }
        releaseEdgeActive = coupleReleaseEdge;
        var edgeVx = releaseEdge.Velocity;
        var twoEdge = coupleReleaseEdge && s.ReleaseTrailingEdge;
        var coupleArea = coupleTapArea || (coupleReleaseArea && !s.ReleaseTrailingEdge);
        if (coupleArea && !tapAreaActive)
        {
            double radius = refr.R * Math.Exp(pose.P), spine = Hypot(pose.Dx, pose.Dy);
            var spineVelocity = spine > 1e-5 ? (pose.Dx * velocity.Dx + pose.Dy * velocity.Dy) / spine : 0;
            tapArea.SnapTo(Math.PI * radius * radius + 4 * spine * radius);
            tapArea.Velocity = (2 * Math.PI * radius + 4 * spine) * radius * velocity.P + 4 * radius * spineVelocity;
        }
        tapAreaActive = coupleArea;
        var distanceExcess = coupleTapArea ? Math.Max(tapDistance / refr.Width - 2, 0) : 0;
        var longTapDrive = 1 - Math.Exp(-distanceExcess * distanceExcess / (distanceExcess + .5));
        var vRef = Math.Max(Mode == CalmSelectorMode.Held ? s.HeldReferenceSpeed : tapMotion ? s.TapReferenceSpeed : s.ReferenceSpeed, 1e-3);
        var sv = speed2 / (vRef * vRef + speed2);
        travel.Target = MotionEnabled && (Mode == CalmSelectorMode.Held ||
            (Mode == CalmSelectorMode.Released && releasedFromHold)) ? sv : 0;
        travel.Step(dt, 60, 1);
        if (Math.Abs(travel.Value) < 1e-4 && Math.Abs(travel.Velocity) < .002 && travel.Target == 0) travel.SnapTo(0);
        var pressResponse = heldNow ? 1 : pointerDownActive && pressEligible ? s.PressFormation : 0;
        double formTarget;
        if (!MotionEnabled) formTarget = 0;
        else if (Mode == CalmSelectorMode.Released)
        {
            var v = s.ReleaseFormationSpeed;
            if (v > 0 && releasedFromHold)
            {
                var rs = velocity.Cx / slot;
                formTarget = releasedStill && now - releasedAt < s.ReleaseStillHoldSeconds ? form.Value : Math.Min(form.Value, rs * rs / (v * v + rs * rs));
            }
            else formTarget = 0;
        }
        else formTarget = 1 - (1 - pressResponse) * (1 - s.FormFromMovement * sv);
        form.Target = formTarget;
        var riseOmega = heldNow && s.HeldFormRiseOmega > 0 ? s.HeldFormRiseOmega : s.FormRiseOmega;
        form.Step(dt, formTarget > form.Value ? riseOmega : s.FormOmega, s.FormZeta);

        var formation = Formation;
        var gain = MotionEnabled ? 2 * s.StretchGain * Math.Max(formation, .35) : 0;
        var denom = vRef * vRef + speed2;
        var density = Math.Max(b.Density, 1e-3);
        var pullY = Mode == CalmSelectorMode.Held && MotionEnabled && hasGrasp ? graspY.Value - graspY0 : 0;
        var distanceDp = Math.Abs(pullY) / density;
        var effectiveDp = distanceDp * distanceDp / Math.Max(distanceDp + s.PullOnsetDp, 1e-4);
        var strainLimit = Math.Max(Math.Min(s.PullMaxStrain, s.MaxStrain * .95), 1e-4);
        var pullStrain = strainLimit * Math.Tanh(s.PullStrainPerDp * effectiveDp / strainLimit);
        double pullU = 0, pullV = 0;
        if (pullStrain > 1e-5)
        {
            var psi = Math.Atan2(pullY, vx * s.PullTiltSeconds) - Math.Atan2(frame.SinTheta, frame.CosTheta);
            pullU = pullStrain * Math.Cos(2 * psi); pullV = pullStrain * Math.Sin(2 * psi);
        }
        if (s.HeldTravelStrain > 0)
        {
            // Sustained held travel builds the strain; a short flick releases before it has.
            strainDrive.Target = Mode == CalmSelectorMode.Held && MotionEnabled ? sv * formation : 0;
            strainDrive.Step(dt, s.HeldTravelStrainOmega, 1);
            if (Math.Abs(strainDrive.Value) < 1e-4 && Math.Abs(strainDrive.Velocity) < .002 && strainDrive.Target == 0) strainDrive.SnapTo(0);
        }
        equilibrium.U = gain * (nvx * nvx - .5 * speed2) / denom + pullU + s.HeldTravelStrain * strainDrive.Value;
        equilibrium.V = gain * (nvx * nvy) / denom + pullV;
        var held = HeldPressure(s.HeldHeightRatio, b.Height, refr.Height);
        var squeeze = Mode == CalmSelectorMode.Held ? s.Squeeze * sv : 0;
        var pullPressure = s.PullPressurePerDp * pullStrain / Math.Max(s.PullStrainPerDp, 1e-4);
        var transitPressure = Mode == CalmSelectorMode.TapTransit && s.TapPressureOmega > 0;
        var pressureFormation = transitPressure ? formTarget : formation;
        var formedSqueeze = s.HeldTravelPressureOmega > 0 ? squeeze * pressureFormation : squeeze;
        equilibrium.P = MotionEnabled ? held * pressureFormation - formedSqueeze + pullPressure : 0;
        var recoilPressure = !coupleTapArea && transitPressure && s.TapRecoilArea && MotionEnabled && Hypot(pose.Dx, pose.Dy) < refr.A;
        if (recoilPressure)
        {
            double radius = refr.R * Math.Exp(equilibrium.P), restingSpine = refr.A, compressed = Hypot(pose.Dx, pose.Dy);
            if (compressed < restingSpine && radius > 0)
            {
                var area = Math.PI * radius * radius + 4 * restingSpine * radius;
                var recovered = area / (Math.Sqrt(4 * compressed * compressed + Math.PI * area) + 2 * compressed);
                equilibrium.P += Math.Log(recovered / radius);
            }
        }
        double ax = accelX / slot, ay = accelY / slot, aMag = Math.Sqrt(ax * ax + ay * ay), aScale = Math.Max(s.ReferenceAcceleration, 1e-3);
        if (MotionEnabled)
        {
            equilibrium.Kx = s.TaperFromAcceleration * ax / (aScale + aMag) + s.TaperFromVelocity * nvx / (vRef + speed);
            equilibrium.Ky = s.TaperFromAcceleration * ay / (aScale + aMag) + s.TaperFromVelocity * nvy / (vRef + speed);
        }
        else { equilibrium.Kx = 0; equilibrium.Ky = 0; }
        if (Mode == CalmSelectorMode.Held && MotionEnabled)
        {
            if (speed < s.AccommodationEngageSpeed) slowFor += dt;
            else if (speed > s.AccommodationReleaseSpeed) { slowFor = 0; accommodating = false; }
            if (!accommodating && slowFor >= s.AccommodationDwellSeconds) accommodating = true;
        }
        else { slowFor = 0; accommodating = false; }
        equilibrium.Acc = accommodating ? AdmittedSpine() : 0;
        var ex = refr.A;
        if (coupleTapArea)
        {
            var fraction = Math.Abs(tapShapeVx) / Math.Max(Math.Abs(tapShapeVx) + s.TapAreaBodySpeed * refr.Width, 1);
            static double Eighth(double v) { var sq = v * v; var f = sq * sq; return f * f; }
            double moving = Eighth(fraction), still = Eighth(1 - fraction);
            ex += s.TapBodyElongation * refr.Width * (1 + .3 * longTapDrive) * moving / Math.Max(moving + still, 1e-8);
        }
        else if (!coupleReleaseArea && MotionEnabled && speed2 > 1e-9)
        {
            var h2 = nvx * nvx; var along = h2 / (vRef * vRef + h2);
            var t = Mode == CalmSelectorMode.Held && s.HeldTravelPressureOmega > 0 ? along * (2 - along) : along;
            ex += (tapMotion ? s.TapElongationSlots : s.ElongationSlots) * slot * t;
        }
        equilibrium.Dx = ex; equilibrium.Dy = 0;

        (pose.U, velocity.U) = StepState(dt, pose.U, velocity.U, equilibrium.U, s.ShapeOmega, s.ShapeZeta);
        (pose.V, velocity.V) = StepState(dt, pose.V, velocity.V, equilibrium.V, s.ShapeOmega, s.ShapeZeta);
        (pose.Kx, velocity.Kx) = StepState(dt, pose.Kx, velocity.Kx, equilibrium.Kx, s.ShapeOmega, s.ShapeZeta);
        (pose.Ky, velocity.Ky) = StepState(dt, pose.Ky, velocity.Ky, equilibrium.Ky, s.ShapeOmega, s.ShapeZeta);
        double pressureRate;
        if (recoilPressure) pressureRate = Math.Max(s.TapPressureOmega, 4 * s.TapSpineOmega);
        else if (Mode == CalmSelectorMode.Held && s.HeldTravelPressureOmega > 0 && equilibrium.P < pose.P && speed2 > 1e-9) pressureRate = s.HeldTravelPressureOmega;
        else if (transitPressure) pressureRate = s.TapPressureOmega;
        else if (Mode != CalmSelectorMode.Held && equilibrium.P < pose.P) pressureRate = s.PressureFallOmega;
        else pressureRate = s.PressureOmega;
        if (!coupleArea) (pose.P, velocity.P) = StepState(dt, pose.P, velocity.P, equilibrium.P, pressureRate, s.PressureZeta);
        (pose.Acc, velocity.Acc) = StepState(dt, pose.Acc, velocity.Acc, equilibrium.Acc, s.AccommodationOmega, 1);
        double spo, spz;
        if (coupleReleaseArea && s.ReleaseRecoveryOmega > 0) spo = s.ReleaseRecoveryOmega;
        else if (coupleTapArea && equilibrium.Dx < pose.Dx) spo = s.TapSpineFallOmega * tapClock;
        else if (coupleTapArea) spo = s.TapAreaSpineOmega * tapClock;
        else if (Mode == CalmSelectorMode.Released) spo = s.SpineReleaseOmega;
        else if (tapMotion) spo = s.TapSpineOmega;
        else spo = s.SpineOmega;
        if (coupleReleaseArea && s.ReleaseRecoveryOmega > 0) spz = s.ReleaseRecoveryZeta;
        else if (coupleTapArea) spz = s.TapAreaSpineZeta;
        else if (tapMotion) spz = s.TapSpineZeta;
        else spz = s.SpineZeta;
        (pose.Dx, velocity.Dx) = StepState(dt, pose.Dx, velocity.Dx, equilibrium.Dx, spo, spz);
        (pose.Dy, velocity.Dy) = StepState(dt, pose.Dy, velocity.Dy, equilibrium.Dy, spo, spz);
        ClampShape();
        if (coupleReleaseArea && pose.Dx < .002) { pose.Dx = .002; if (velocity.Dx < 0) velocity.Dx = 0; }
        if (coupleArea)
        {
            double r0 = refr.R, restArea = Math.PI * r0 * r0 + 4 * refr.A * r0;
            var phaseSpeed = Math.Abs(tapShapeVx) / Math.Max(tapDistance, refr.Width * .9);
            var q = phaseSpeed / Math.Max(phaseSpeed + s.TapAreaReferenceSpeed, 1e-5);
            var excitation = q * q / Math.Max(q * q + (1 - q) * (1 - q), 1e-8);
            tapArea.Target = coupleReleaseArea ? restArea : restArea * (1 + s.TapAreaGain * (1 + longTapDrive) * excitation);
            tapArea.Step(dt, coupleReleaseArea ? s.PressureFallOmega : s.TapAreaOmega * tapClock, 1);
            double area = Math.Max(tapArea.Value, restArea * .5), spine = Hypot(pose.Dx, pose.Dy);
            var spineVelocity = spine > 1e-5 ? (pose.Dx * velocity.Dx + pose.Dy * velocity.Dy) / spine : 0;
            var radius = area / (Math.Sqrt(4 * spine * spine + Math.PI * area) + 2 * spine);
            pose.P = Math.Log(radius / r0);
            velocity.P = (tapArea.Velocity - 4 * radius * spineVelocity) / Math.Max((2 * Math.PI * radius + 4 * spine) * radius, 1e-5);
        }
        if (coupleTapArea && s.TapAppearanceArea > 0)
        {
            var restArea = Math.PI * refr.R * refr.R + 4 * refr.A * refr.R;
            var extra = Math.Max(tapArea.Value / restArea - 1, 0); var q = extra / s.TapAppearanceArea;
            double spine = Hypot(pose.Dx, pose.Dy), spineScale = s.TapAppearanceSpine * refr.Width;
            var axial = spineScale > 0 ? Math.Max(spine - refr.A, 0) / spineScale : 0;
            var axialVelocity = spineScale > 0 && spine > refr.A && spine > 1e-5 ? (pose.Dx * velocity.Dx + pose.Dy * velocity.Dy) / (spine * spineScale) : 0;
            var raw = q * q + axial * axial; var shoulder = Math.Clamp((raw - .85) / .2, 0, 1);
            var demand = raw <= .85 ? raw : .85 + .2 * (shoulder - .5 * shoulder * shoulder);
            var slope = raw <= .85 ? 1 : 1 - shoulder;
            transit.Target = demand;
            if (s.TapAppearanceFallOmega > 0 && demand < transit.Value) transit.Step(dt, s.TapAppearanceFallOmega, 1);
            else { transit.Value = demand; transit.Velocity = slope * 2 * (q / s.TapAppearanceArea * tapArea.Velocity / restArea + axial * axialVelocity); }
        }
        else
        {
            transit.Target = 0; transit.Step(dt, s.FormOmega, 1);
            if (Math.Abs(transit.Value) < 1e-4 && Math.Abs(transit.Velocity) < .002) transit.SnapTo(0);
        }

        if (Mode == CalmSelectorMode.Held && hasGrasp)
        {
            frame.Update(refr, pose);
            var (ox, oy) = frame.MaterialToRendered(graspQx, graspQy); double shapeX = ox - pose.Cx, shapeY = oy - pose.Cy;
            graspX.Target = AnchoredCentre(pointerX - shapeX) + shapeX; graspY.Target = pointerY;
            graspX.Step(dt, s.GraspOmega, 1); graspY.Step(dt, s.GraspOmega, 1);
            var cap = Math.Max(s.PullFollowCapRatio * b.Height, 1e-4); var pull = graspY.Value - graspY0;
            var response = Math.Tanh(s.PullFollow * pull / cap); var follow = Bounded(s.PullFollow * pull, cap);
            pose.Cx = graspX.Value - shapeX; pose.Cy = graspY0 + follow - shapeY;
            UpdateShapeVelocity();
            velocity.Cx = graspX.Velocity - shapeVelX;
            velocity.Cy = s.PullFollow * (1 - response * response) * graspY.Velocity - shapeVelY;
        }
        else if (coupleReleaseEdge)
        {
            var leadBefore = releaseEdge.Value;
            releaseEdge.Step(dt, s.CentreOmega, s.CentreZeta);
            if (twoEdge)
            {
                var give = s.ReleaseWallGive * b.Height;
                var wall = (dir > 0 ? b.Width : 0) + dir * give * Math.Clamp(form.Value, 0, 1);
                var wallVelocity = form.Value > 0 && form.Value < 1 ? dir * give * form.Velocity : 0;
                if (give > 0)
                {
                    if (dir * (releaseEdge.Value - wall) > 0)
                    {
                        var from = dir * (leadBefore - wall) > 0 ? leadBefore : wall;
                        var pull = Math.Min(dir * (from - wall), WallPullHeights * b.Height * dt);
                        releaseEdge.Value = from - dir * pull;
                        releaseEdge.Velocity = from == wall ? wallVelocity
                            : pull < dir * (from - wall) ? -dir * WallPullHeights * b.Height : wallVelocity;
                    }
                }
                else if (dir * releaseEdge.Velocity > 0 && dir * (releaseEdge.Value - wall) > 0)
                { releaseEdge.Value = dir * (leadBefore - wall) > 0 ? leadBefore : wall; releaseEdge.Velocity = 0; }
                // The leading edge meets the bar's end: the impact takes the trailing edge's speed, once.
                if (!trailImpact && dir * (releaseEdge.Value - (dir > 0 ? b.Width : 0)) >= -ReleaseImpactReach * b.Height)
                { trailImpact = true; releaseTrail.Velocity *= s.ReleaseTrailingCarry; }
                if (s.ReleaseTrailingInOmega > 0 && dir * releaseTrail.Velocity > 0)
                {
                    var behind = Math.Max(0, -dir * (releaseTrail.Value - releaseTrail.Target)) / Math.Max(refr.Width, 1);
                    releaseTrail.Step(dt, s.ReleaseTrailingInOmega * (1 + s.ReleaseTrailingStiffening * behind), s.ReleaseTrailingInZeta);
                }
                else releaseTrail.Step(dt, s.ReleaseRecoveryOmega, s.ReleaseRecoveryZeta);
                var caps = refr.R * Math.Exp(pose.P) + Math.Max(pose.Acc, 0);
                var capsVelocity = refr.R * Math.Exp(pose.P) * velocity.P + (pose.Acc > 0 ? velocity.Acc : 0);
                var minimum = caps + .002;
                var eu = StrainScale();
                if (dir * (releaseEdge.Value - releaseTrail.Value) < 2 * minimum * eu)
                { releaseTrail.Value = releaseEdge.Value - dir * 2 * minimum * eu; releaseTrail.Velocity = releaseEdge.Velocity - dir * 2 * capsVelocity * eu; }
                // The edges are rendered extents; the held travel strain scales the body by e^u.
                var half = dir * (releaseEdge.Value - releaseTrail.Value) * .5;
                pose.Dx = half / eu - caps;
                velocity.Dx = (dir * (releaseEdge.Velocity - releaseTrail.Velocity) * .5 - (eu != 1 ? half * velocity.U : 0)) / eu - capsVelocity;
                pose.Dy = 0; velocity.Dy = 0;
            }
            pose.Cx = releaseEdge.Value - dir * LandingHalfWidth(); velocity.Cx = releaseEdge.Velocity - dir * LandingHalfWidthVelocity();
            (pose.Cy, velocity.Cy) = StepState(dt, pose.Cy, velocity.Cy, b.Height / 2, s.CentreOmega, s.CentreZeta);
        }
        else
        {
            double tx = b.CentreOf(RestIndex), ty = b.Height / 2;
            if (!MotionEnabled) { pose.Cx = tx; pose.Cy = ty; velocity.Cx = 0; velocity.Cy = 0; }
            else
            {
                var co = s.CentreOmega * tapClock;
                (pose.Cx, velocity.Cx) = StepState(dt, pose.Cx, velocity.Cx, tx, co, s.CentreZeta);
                (pose.Cy, velocity.Cy) = StepState(dt, pose.Cy, velocity.Cy, ty, co, s.CentreZeta);
            }
        }
        if (coupleReleaseArea && !twoEdge)
        {
            var driverBefore = coupleReleaseEdge ? edgeVx : vx;
            var driverAfter = coupleReleaseEdge ? releaseEdge.Velocity : velocity.Cx;
            var displacement = coupleReleaseEdge ? releaseEdge.Target - releaseEdge.Value : b.CentreOf(RestIndex) - pose.Cx;
            var distance = Math.Abs(displacement) / (refr.Width * .25); var proximity = Math.Exp(-distance * distance);
            if (driverBefore * displacement >= 0) velocity.Dx -= s.ReleaseImpactGain * proximity * Math.Max(Math.Abs(driverBefore) - Math.Abs(driverAfter), 0);
        }
        frame.Update(refr, pose);

        bool wantsContact = Mode switch
        {
            CalmSelectorMode.TapTransit or CalmSelectorMode.PressPending => !coupleTapArea,
            CalmSelectorMode.Held => false,
            CalmSelectorMode.Released => !coupleReleaseArea && (!releasedFromHold ||
                (form.Value < ContactRegainFormation && pose.Acc <= ContactRegainAccommodation && InsideRestEnvelope(ContactRegainMargin))),
            _ => true,
        };
        if (wantsContact) ApplyContact();
        frame.Update(refr, pose);
        var spineLength = Math.Sqrt(pose.Dx * pose.Dx + pose.Dy * pose.Dy);
        CaptureLastGood(); RecordQuiet();
        if (Mode == CalmSelectorMode.PressPending && heldNow) { AcquireGrasp(downX, downY, downOnBody); Mode = CalmSelectorMode.Held; }
        if (Mode == CalmSelectorMode.Released || Mode == CalmSelectorMode.TapTransit)
        {
            var settled = Math.Abs(pose.Cx - b.CentreOf(RestIndex)) < .25 && Math.Abs(velocity.Cx) < 1.5 && Math.Abs(velocity.Cy) < 1.5 &&
                pose.Rho < .005 && Math.Abs(pose.P) < .005 && form.Value < .01 && Math.Abs(spineLength - refr.A) < .25 &&
                Math.Abs(velocity.Dx) < 1.5 && Math.Abs(velocity.Dy) < 1.5;
            var radiusError = refr.R * (Math.Exp(pose.P) - 1);
            var visuallySettledArea = coupleArea && quietSteps >= QuietSteps && Math.Abs(pose.Cx - b.CentreOf(RestIndex)) < .125 &&
                Math.Abs(pose.Cy - b.Height / 2) < .125 && form.Value < .01 && Math.Abs(spineLength - refr.A + radiusError) < .125 && Math.Abs(radiusError) < .125;
            var radiusVelocity = refr.R * Math.Exp(pose.P) * velocity.P;
            var slowTapQuiet = Math.Max(Math.Max(Math.Abs(velocity.Cx), Math.Abs(radiusVelocity)), Math.Abs(velocity.Dx + radiusVelocity)) < refr.Width * .002;
            var canFinish = coupleTapArea && tapClock < 1 ? visuallySettledArea && slowTapQuiet : settled || visuallySettledArea;
            if (canFinish)
            {
                if (coupleArea) SnapToRest(RestIndex);
                else { Mode = CalmSelectorMode.Rest; tapDistance = 0; tapAreaActive = false; }
            }
        }
    }

    private static double Hypot(double x, double y) => Math.Sqrt(x * x + y * y);

    private void UpdateShapeVelocity()
    {
        const double h = 1.0 / 4800;
        double loX = 0, loY = 0, hiX = 0, hiY = 0;
        foreach (var sign in new[] { -1, 1 })
        {
            scratchPose.Set(pose);
            scratchPose.U += sign * h * velocity.U; scratchPose.V += sign * h * velocity.V;
            scratchPose.Kx += sign * h * velocity.Kx; scratchPose.Ky += sign * h * velocity.Ky;
            scratchPose.P += sign * h * velocity.P; scratchPose.Acc += sign * h * velocity.Acc;
            scratchPose.Dx += sign * h * velocity.Dx; scratchPose.Dy += sign * h * velocity.Dy;
            scratchPose.Cx = 0; scratchPose.Cy = 0;
            derivFrame.Update(reference, scratchPose);
            var (zx, zy) = derivFrame.MaterialToZ(graspQx, graspQy); var (x, y) = derivFrame.ZToRendered(zx, zy);
            if (sign < 0) { loX = x; loY = y; } else { hiX = x; hiY = y; }
        }
        shapeVelX = (hiX - loX) / (2 * h); shapeVelY = (hiY - loY) / (2 * h);
    }

    private bool InsideRestEnvelope(double margin)
    {
        frame.Contour(ContourSamples, contour);
        for (var i = 0; i < ContourSamples; i++) if (BarSignedDistance(contour[i * 2], contour[i * 2 + 1]) > -margin) return false;
        return true;
    }

    private void ApplyContact()
    {
        var iterations = 0; double worst;
        do
        {
            frame.Update(reference, pose); frame.Contour(ContourSamples, contour);
            worst = double.MinValue; double wx = 0, wy = 0;
            for (var i = 0; i < ContourSamples; i++)
            {
                var d = BarSignedDistance(contour[i * 2], contour[i * 2 + 1]);
                if (d > worst) { worst = d; wx = contour[i * 2]; wy = contour[i * 2 + 1]; }
            }
            if (worst <= 0) break;
            const double e = .5;
            var gx = (BarSignedDistance(wx + e, wy) - BarSignedDistance(wx - e, wy)) / (2 * e);
            var gy = (BarSignedDistance(wx, wy + e) - BarSignedDistance(wx, wy - e)) / (2 * e);
            var gl = Math.Sqrt(gx * gx + gy * gy);
            if (gl < 1e-5) break;
            double nx = gx / gl, ny = gy / gl, opposite = double.MinValue;
            for (var i = 0; i < ContourSamples; i++)
            {
                double x = contour[i * 2], y = contour[i * 2 + 1];
                if ((x - pose.Cx) * nx + (y - pose.Cy) * ny >= 0) continue;
                opposite = Math.Max(opposite, BarSignedDistance(x, y));
            }
            var bilateral = Math.Min(worst, Math.Max(opposite, 0)); var oneSided = worst - bilateral;
            if (oneSided > 0) { var t = Math.Min(oneSided, MaxContactStep); pose.Cx -= nx * t; pose.Cy -= ny * t; }
            if (bilateral > 0)
            {
                var spineLength = Math.Sqrt(pose.Dx * pose.Dx + pose.Dy * pose.Dy); var extra = spineLength - reference.A;
                if (extra > .5 && Math.Abs(nx) > Math.Abs(ny))
                {
                    var take = Math.Min(bilateral, extra); var sc = Math.Max((spineLength - take) / spineLength, .5);
                    pose.Dx *= sc; pose.Dy *= sc; velocity.Dx *= sc; velocity.Dy *= sc;
                }
                else
                {
                    var reach = Math.Max(Math.Sqrt((wx - pose.Cx) * (wx - pose.Cx) + (wy - pose.Cy) * (wy - pose.Cy)), 1);
                    pose.P += Math.Max(Math.Log(Math.Max((reach - bilateral) / reach, .5)), -MaxContactPressureStep);
                    if (velocity.P > 0) velocity.P = 0;
                }
            }
            var vn = velocity.Cx * nx + velocity.Cy * ny;
            if (vn > 0) { velocity.Cx -= vn * nx; velocity.Cy -= vn * ny; }
            iterations++;
        } while (worst > ContactTolerance && iterations < MaxContactIterations);
        frame.Update(reference, pose); frame.Contour(ContourSamples, contour);
        worst = double.MinValue;
        for (var i = 0; i < ContourSamples; i++) worst = Math.Max(worst, BarSignedDistance(contour[i * 2], contour[i * 2 + 1]));
        if (worst > ContactTolerance && !(double.IsFinite(pose.Cx) && double.IsFinite(pose.Cy) && double.IsFinite(pose.P))) RestoreLastGood();
        frame.Update(reference, pose);
    }

    private double BarSignedDistance(double px, double py)
    {
        var b = Bar; double hw = b.Width / 2, hh = b.AllowedHalfHeight, rad = Math.Min(b.CornerRadius, hh);
        double qx = Math.Abs(px - b.Width / 2) - hw + rad, qy = Math.Abs(py - b.Height / 2) - hh + rad;
        var outside = Math.Sqrt(Math.Max(qx, 0) * Math.Max(qx, 0) + Math.Max(qy, 0) * Math.Max(qy, 0));
        return Math.Min(Math.Max(qx, qy), 0) + outside - rad;
    }
    private void CaptureLastGood() { lastGood.Set(pose); lastGoodVelocity.Set(velocity); hasLastGood = true; }
    private void RestoreLastGood() { if (!hasLastGood) return; pose.Set(lastGood); velocity.Set(lastGoodVelocity); }

    private double AdmittedSpine()
    {
        if (itemCentre.Length == 0) { accommodationDemand = 0; return 0; }
        var s = Spec; var b = Bar; var g = graspX.Value;
        if (accommodationValid && Math.Abs(g - accommodationGrasp) <= AccommodationResolvePx &&
            Math.Abs(graspY.Value - accommodationGraspY) <= AccommodationResolvePx &&
            Math.Abs(pose.P - accommodationPressure) <= AccommodationResolvePressure) return accommodationDemand;
        accommodationGrasp = g; accommodationGraspY = graspY.Value; accommodationPressure = pose.P; accommodationValid = true;
        var d = b.Density; int nearest = 0; var nearestDistance = double.MaxValue;
        for (var i = 0; i < itemCentre.Length; i++) { var dist = Math.Abs(itemCentre[i] - g); if (dist < nearestDistance) { nearestDistance = dist; nearest = i; } }
        var aperture = reference.A + reference.R * Math.Exp(HeldPressure(s.HeldHeightRatio, b.Height, reference.Height));
        double admitInside = aperture - s.AdmitInsetDp * d, releaseOutside = aperture + s.AdmitReleaseDp * d;
        int neighbour = -1; var neighbourDistance = double.MaxValue;
        for (var i = 0; i < itemCentre.Length; i++)
        {
            if (i == nearest || Math.Abs(i - nearest) != 1) continue;
            var dist = Math.Abs(itemCentre[i] - g); var keep = admittedNeighbour == i ? dist <= releaseOutside : dist <= admitInside;
            if (keep && dist < neighbourDistance) { neighbourDistance = dist; neighbour = i; }
        }
        admittedNeighbour = neighbour;
        var pad = s.AdmitPaddingDp * d;
        double left = itemLeft[nearest] - pad, right = itemRight[nearest] + pad;
        if (neighbour >= 0) { left = Math.Min(left, itemLeft[neighbour] - pad); right = Math.Max(right, itemRight[neighbour] + pad); }
        var ceiling = s.MaxAccommodationSlots * b.SlotWidth;
        var demand = BestSpineFor(left, right, ceiling);
        if (demand <= 0 && neighbour >= 0) { admittedNeighbour = -1; demand = BestSpineFor(itemLeft[nearest] - pad, itemRight[nearest] + pad, ceiling); }
        accommodationDemand = demand; return demand;
    }
    private double BestSpineFor(double left, double right, double ceiling)
    {
        if (ceiling <= 0) return 0;
        double bestSpine = 0, bestDeficit = CoverageDeficit(0, left, right);
        const int steps = 16;
        for (var i = 1; i <= steps; i++)
        {
            var spine = ceiling * i / steps; var deficit = CoverageDeficit(spine, left, right);
            if (deficit < bestDeficit - CoverageEpsilon) { bestDeficit = deficit; bestSpine = spine; }
        }
        if (bestSpine <= 0) return 0;
        double lo = Math.Max(bestSpine - ceiling / steps, 0), hi = bestSpine;
        for (var i = 0; i < 8; i++) { var mid = (lo + hi) / 2; if (CoverageDeficit(mid, left, right) <= bestDeficit + CoverageEpsilon) hi = mid; else lo = mid; }
        return hi;
    }
    private double CoverageDeficit(double spineExtra, double left, double right)
    {
        var s = Spec; var b = Bar;
        probePose.Set(pose); probePose.Acc = spineExtra;
        probePose.P = Math.Max(pose.P, HeldPressure(s.HeldHeightRatio, b.Height, reference.Height));
        probePose.Dx = reference.A; probePose.Dy = 0; probeFrame.Update(reference, probePose);
        if (hasGrasp)
        {
            var (ox, oy) = probeFrame.MaterialToRendered(graspQx, graspQy);
            probePose.Cx = graspX.Value - (ox - probePose.Cx);
            var cap = Math.Max(s.PullFollowCapRatio * b.Height, 1e-4);
            var follow = Bounded(s.PullFollow * (graspY.Value - graspY0), cap);
            probePose.Cy = graspY0 + follow - (oy - probePose.Cy); probeFrame.Update(reference, probePose);
        }
        double deficit = 0;
        foreach (var px in new[] { left, right }) foreach (var sy in new[] { -1, 1 })
        {
            var d = probeFrame.Coverage(px, b.Height / 2 + sy * inkHalfHeight);
            if (d > 0) deficit += d;
        }
        return deficit;
    }
    private void RecordQuiet()
    {
        var span = Math.Max(reference.A + reference.R, 1);
        var moved = new[] { Math.Abs(pose.Cx - lastPublished.Cx), Math.Abs(pose.Cy - lastPublished.Cy), Math.Abs(pose.Acc - lastPublished.Acc),
            Math.Abs(pose.Dx - lastPublished.Dx), Math.Abs(pose.Dy - lastPublished.Dy), Math.Abs(pose.P - lastPublished.P) * span,
            Math.Abs(pose.U - lastPublished.U) * span, Math.Abs(pose.V - lastPublished.V) * span, Math.Abs(pose.Kx - lastPublished.Kx) * span,
            Math.Abs(pose.Ky - lastPublished.Ky) * span, Math.Abs(form.Value - formLast) * 100 }.Max();
        lastPublished.Set(pose); formLast = form.Value;
        quietSteps = moved <= QuietPx ? quietSteps + 1 : 0;
    }
    private void ClampShape()
    {
        var s = Spec; var r = pose.Rho;
        if (r > s.MaxStrain) { var sc = s.MaxStrain / r; pose.U *= sc; pose.V *= sc; velocity.U *= sc; velocity.V *= sc; }
        var taper = Math.Sqrt(pose.Kx * pose.Kx + pose.Ky * pose.Ky);
        if (taper > s.MaxTaper) { var sc = s.MaxTaper / taper; pose.Kx *= sc; pose.Ky *= sc; velocity.Kx *= sc; velocity.Ky *= sc; }
        if (pose.Acc < 0) { pose.Acc = 0; velocity.Acc = 0; }
        var maxSpine = reference.A + Math.Max(s.ElongationSlots, s.TapSpineLimitSlots) * Math.Max(Bar.SlotWidth, 1);
        var length = Math.Sqrt(pose.Dx * pose.Dx + pose.Dy * pose.Dy);
        if (length > maxSpine && length > 1e-6) { var sc = maxSpine / length; pose.Dx *= sc; pose.Dy *= sc; velocity.Dx *= sc; velocity.Dy *= sc; }
    }

    /// <summary>How far the contour protrudes past the resting bar, in px.</summary>
    public double Protrusion()
    {
        frame.Contour(ContourSamples, contour); var worst = 0.0;
        for (var i = 0; i < ContourSamples; i++) worst = Math.Max(worst, BarSignedDistance(contour[i * 2], contour[i * 2 + 1]));
        return worst;
    }
    public CalmSelectorExtents Extents()
    {
        frame.Contour(ContourSamples, contour);
        double l = double.MaxValue, r = double.MinValue, t = double.MaxValue, bt = double.MinValue;
        for (var i = 0; i < ContourSamples; i++)
        {
            double x = contour[i * 2], y = contour[i * 2 + 1];
            l = Math.Min(l, x); r = Math.Max(r, x); t = Math.Min(t, y); bt = Math.Max(bt, y);
        }
        return new(l, r, t, bt);
    }
    /// <summary>Pose uniforms with the centre made relative to (originX, originY) in bar px.</summary>
    public CalmSelectorPose Render(double originX = 0, double originY = 0) => new(
        frame.A.Select(v => (float)v).ToArray(), frame.AInv.Select(v => (float)v).ToArray(),
        [(float)(frame.Cx - originX), (float)(frame.Cy - originY), (float)frame.Bx, (float)frame.By],
        [(float)frame.Spine, (float)frame.Radius, (float)frame.ScaleX, (float)frame.Band()])
    {
        // The held lens's superellipse blend (#93), grown with the formation as Compose does.
        E = [(float)(Spec.HeldSquircle * Formation), (float)Spec.SquircleExponent, 0, 0],
    };
}

/// <summary>GlassTabBar's gesture protocol over a <see cref="CalmSelectorController"/>: touch-down aims the
/// selector, a hold or horizontal slop starts a drag, the end commits the projected tab. Bar-local px,
/// monotonic seconds. Selection is reported through the callback, never owned.</summary>
public sealed class CalmNavigation
{
    private readonly CalmSelectorBar bar;
    private readonly Action<int> onSelect;
    private readonly double touchSlop, flingVelocity;
    private Gesture? gesture;
    private double clock;
    // GlassTabBarStyle.touchLift: the bar lifts for any touch. As Compose's animateFloatAsState with
    // tween(50) on the press and tween(260) on the lift: FastOutSlowIn, restarting from where it is.
    private (double From, double To, double T0, double Duration) touch = (0, 0, 0, 1);
    private sealed class Gesture
    {
        public double X0, Y0, T0; public int LastIndex; public bool Dragging; public readonly List<(double T, double X)> Samples = [];
    }

    public CalmNavigation(CalmSelectorBar bar, int selected = 0, Action<int>? onSelect = null, CalmSelectorSpec? spec = null,
        double? touchSlop = null, double? flingVelocity = null, bool motion = true)
    {
        this.bar = bar ?? throw new ArgumentNullException(nameof(bar));
        this.onSelect = onSelect ?? (_ => { });
        this.touchSlop = touchSlop ?? 8 * bar.Density; this.flingVelocity = flingVelocity ?? 420 * bar.Density;
        Controller = new CalmSelectorController(spec) { MotionEnabled = motion };
        Controller.Attach(bar); Controller.SnapToRest(selected); Selected = selected;
    }

    public CalmSelectorController Controller { get; }
    public int Selected { get; private set; }
    private int Nearest(double x) => bar.NearestTab(x);

    public void Select(int index) { Selected = Math.Clamp(index, 0, bar.Count - 1); if (gesture is null) Controller.Retarget(Selected); }
    public void SetMotion(bool enabled) => Controller.MotionEnabled = enabled;

    public void Down(double x, double y, double seconds)
    {
        var index = Nearest(x);
        gesture = new Gesture { X0 = x, Y0 = y, T0 = seconds, LastIndex = index };
        gesture.Samples.Add((seconds, x));
        if (Controller.MotionEnabled) TouchTo(1, seconds);
        Controller.PointerDown(x, y, seconds, Controller.MotionEnabled); Controller.Retarget(index);
    }

    public void Move(double x, double y, double seconds)
    {
        if (gesture is not { } g) return;
        g.Samples.Add((seconds, x)); Controller.PointerMove(x, y, seconds);
        if (!g.Dragging)
        {
            if (!Controller.MotionEnabled) return;
            double dx = x - g.X0, dy = y - g.Y0; var held = seconds - g.T0 >= Controller.Spec.HoldThresholdSeconds;
            var crossed = held ? Math.Sqrt(dx * dx + dy * dy) > touchSlop : Math.Abs(dx) > touchSlop;
            // As GlassTabBar: the drag begins from the down, then the crossing move is applied again,
            // or BeginDrag would leave the pointer at the down until the next move.
            if (crossed) { g.Dragging = true; Controller.BeginDrag(g.X0, g.Y0); Controller.PointerMove(x, y, seconds); }
            else if (!held && Math.Abs(dy) > touchSlop) { Cancel(seconds); return; }
            else return;
        }
        g.LastIndex = Nearest(Math.Clamp(x, bar.CentreOf(0), bar.CentreOf(bar.Count - 1)));
    }

    public void Up(double x, double y, double seconds)
    {
        if (gesture is not { } g) return;
        gesture = null;
        TouchTo(0, seconds);
        if (!g.Dragging) { Controller.Cancel(g.LastIndex); Selected = g.LastIndex; onSelect(g.LastIndex); return; }
        g.Samples.Add((seconds, x));
        // The lift is the gesture's last sample, as in GlassTabBar.
        Controller.PointerMove(x, y, seconds);
        var v = Velocity(g.Samples);
        var projected = Math.Clamp(v > flingVelocity ? g.LastIndex + 1 : v < -flingVelocity ? g.LastIndex - 1 : g.LastIndex, 0, bar.Count - 1);
        Controller.PointerUp(projected); Selected = projected; onSelect(projected);
    }

    public void Cancel(double seconds) { gesture = null; TouchTo(0, seconds); Controller.Cancel(Selected); }

    /// <summary>The bar's touch lift amount at the last frame, 0 to 1 (GlassTabBarStyle.touchLift).</summary>
    public double Touch => TouchAt(clock);

    /// <summary>How much of a press transform the host applies to the bar its items should share, 0 to 1:
    /// 0 on a tap, rising to 1 as a hold forms the lens (Compose's GlassTabBarStyle.itemsFollowBarPress = false).</summary>
    public double ItemFollow
    {
        get { var f = Math.Clamp((Controller.Formation - .5) / .5, 0, 1); return f * f * (3 - 2 * f); }
    }

    private double TouchAt(double seconds)
    {
        var u = Math.Clamp((seconds - touch.T0) / touch.Duration, 0, 1);
        return touch.From + (touch.To - touch.From) * FastOutSlowIn(u);
    }

    private void TouchTo(double to, double seconds)
    {
        if (touch.To == to) return;
        touch = (TouchAt(seconds), to, seconds, to > 0 ? .05 : .26);
    }

    /// <summary>Compose's FastOutSlowInEasing, cubic-bezier(0.4, 0, 0.2, 1).</summary>
    private static double FastOutSlowIn(double u)
    {
        if (u <= 0 || u >= 1) return Math.Clamp(u, 0, 1);
        static double Bez(double t, double p1, double p2) => 3 * (1 - t) * (1 - t) * t * p1 + 3 * (1 - t) * t * t * p2 + t * t * t;
        double lo = 0, hi = 1, t = u;
        for (var i = 0; i < 30; i++) { t = (lo + hi) / 2; if (Bez(t, .4, .2) < u) lo = t; else hi = t; }
        return Bez(t, 0, 1);
    }

    /// <summary>Advance to the display time. True while another frame is needed.</summary>
    public bool Frame(double seconds)
    {
        clock = Controller.ResumedFrameTime(Math.Max(clock, seconds)); Controller.AdvanceFrameTo(clock);
        return !Controller.IsIdle || gesture is not null || clock < touch.T0 + touch.Duration;
    }

    /// <summary>Least-squares slope over the last 100 ms, as a stand-in for Compose's VelocityTracker.</summary>
    private static double Velocity(List<(double T, double X)> samples)
    {
        var t1 = samples[^1].T; var recent = samples.Where(s => t1 - s.T <= .1).ToList();
        if (recent.Count < 2) return 0;
        double mt = recent.Average(s => s.T), mx = recent.Average(s => s.X), num = 0, den = 0;
        foreach (var (t, x) in recent) { num += (t - mt) * (x - mx); den += (t - mt) * (t - mt); }
        return den > 1e-12 ? num / den : 0;
    }
}

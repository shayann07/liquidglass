// Apache-2.0. The in-app material's uniforms: a line-for-line port of the Flutter port's material.dart,
// itself a port of ports/skia's clearMaterialUniforms (renderer.mjs) and inAppMaterialUniforms
// (material.mjs), which carry Compose's GlassStyle.inApp. Lengths are device pixels; the density
// converts the dp constants. No UnityEngine types, so ports/unity/Checks tests it against the same
// fixture the Flutter and Qt ports are checked against.
using System;
using System.Collections.Generic;

namespace LiquidGlass
{
    /// <summary>A surface's look. Radius is in logical pixels (dp); colours are sRGB 0..1. Doubles, as in
    /// the JavaScript the uniforms are checked against: a float 0.9 rounds to 229/255, a double to 230.</summary>
    [Serializable]
    public struct GlassLook
    {
        public double Radius;
        public bool Dark;
        public double TintAmount;
        public double LightX, LightY;
        public bool HasTint;
        public double TintR, TintG, TintB, TintA;
        public double Materialize;
        public double ReducedTransparency;
        public double IncreasedContrast;

        public static GlassLook Default => new GlassLook
        {
            Radius = 20, TintAmount = 50, LightX = -.35f, LightY = -.94f, Materialize = 1,
        };
    }

    public static class GlassMaterial
    {
        static readonly double[,] LightOpacity = { { 0, .435 }, { 31, .514 }, { 50, .565 }, { 62, .600 }, { 100, .733 } };
        static readonly double[,] DarkOpacity = { { 0, .416 }, { 29, .439 }, { 50, .451 }, { 62, .510 }, { 86, .655 }, { 100, .737 } };

        static double Interpolate(double[,] table, double t)
        {
            var i = 0;
            while (i < table.GetLength(0) - 2 && table[i + 1, 0] <= t) i++;
            double x = table[i, 0], a = table[i, 1], y = table[i + 1, 0], b = table[i + 1, 1];
            return a + (b - a) * (t - x) / (y - x);
        }

        static double Clamp01(double v) => Math.Min(1, Math.Max(0, v));

        // GlassStyle.towardLab: the lab fit (#199, #200) laid over the earlier fit by a weight that is 1 at the
        // default Tint Amount and 0 at 0 and 100. The constants are GlassMaterial.lab* in the Compose library.
        const double LabWideKernel = 15.5;
        const double LabDetailReach = 1.0 / 3.0;

        static double LabWeight(double amount) => Clamp01(1 - Math.Abs(Math.Min(100, Math.Max(0, amount)) - 50) / 50);
        static double LabOpacity(bool dark) => dark ? .3985 : .498;
        static double LabLift(bool dark) => dark ? 31.72 / 255 : 5.5 / 255;
        static double LabDetailShare(bool dark) => dark ? .277 : .273;
        static double LabDetailLift(bool dark) => dark ? -.22 : .22;
        static double LabToneCurve(bool dark) => dark ? 101.01 / 255 : 0;

        // GlassMaterial.labChromaGain / labChromaSlope (#201): the colour transfer's g0 and g1 per channel (R, G, B).
        static double[] LabChromaGain(bool dark) => dark ? new[] { 1.317, 1.256, 1.314 } : new[] { .988, .952, .979 };
        static double[] LabChromaSlope(bool dark) => dark ? new[] { -.427, -.351, -.429 } : new[] { -.076, .028, -.045 };

        /// <summary>
        /// GlassMaterial.chromaOf: g0 (R, G, B), g1 (R, G, B) and the weight from GlassStyle.detailLift. The sign picks
        /// the appearance and |c| / |labDetailLift| is the weight; zero lift is seven zeros (the per-channel tone alone).
        /// </summary>
        public static double[] ChromaOf(double detailLift)
        {
            bool dark = detailLift < 0;
            double weight = Clamp01(Math.Abs(detailLift) / Math.Abs(LabDetailLift(dark)));
            if (weight <= 0) return new double[7];
            var g0 = LabChromaGain(dark);
            var g1 = LabChromaSlope(dark);
            return new[] { g0[0], g0[1], g0[2], g1[0], g1[1], g1[2], weight };
        }

        // Compose's Color lerp runs in Oklab and stores 8 bits per channel again; the Oklab intermediate keeps
        // alpha in 10 bits, truncated, which is the step the golden values show.
        static double SrgbToLinear(double c) => c <= .04045 ? c / 12.92 : Math.Pow((c + .055) / 1.055, 2.4);
        static double LinearToSrgb(double c) => c <= .0031308 ? c * 12.92 : 1.055 * Math.Pow(c, 1 / 2.4) - .055;
        static double Q8(double x) => Math.Floor(Clamp01(x) * 255 + .5) / 255;

        static double[] ToOklab(double[] c)
        {
            double r = SrgbToLinear(c[0]), g = SrgbToLinear(c[1]), b = SrgbToLinear(c[2]);
            double l = Math.Cbrt(.4122214708 * r + .5363325363 * g + .0514459929 * b);
            double m = Math.Cbrt(.2119034982 * r + .6806995451 * g + .1073969566 * b);
            double s = Math.Cbrt(.0883024619 * r + .2817188376 * g + .6299787005 * b);
            return new[]
            {
                .2104542553 * l + .793617785 * m - .0040720468 * s,
                1.9779984951 * l - 2.428592205 * m + .4505937099 * s,
                .0259040371 * l + .7827717662 * m - .808675766 * s,
            };
        }

        static double[] FromOklab(double[] c)
        {
            double l = Math.Pow(c[0] + .3963377774 * c[1] + .2158037573 * c[2], 3);
            double m = Math.Pow(c[0] - .1055613458 * c[1] - .0638541728 * c[2], 3);
            double s = Math.Pow(c[0] - .0894841775 * c[1] - 1.291485548 * c[2], 3);
            return new[]
            {
                LinearToSrgb(Clamp01(4.0767416621 * l - 3.3077115913 * m + .2309699292 * s)),
                LinearToSrgb(Clamp01(-1.2684380046 * l + 2.6097574011 * m - .3413193965 * s)),
                LinearToSrgb(Clamp01(-.0041960863 * l - .7034186147 * m + 1.707614701 * s)),
            };
        }

        static double[] LerpColor(double[] a, double[] b, double w)
        {
            double[] p = ToOklab(a), q = ToOklab(b);
            var rgb = FromOklab(new[] { p[0] + (q[0] - p[0]) * w, p[1] + (q[1] - p[1]) * w, p[2] + (q[2] - p[2]) * w });
            double alpha = Math.Floor(Clamp01(a[3] + (b[3] - a[3]) * w) * 1023) / 1023;
            return new[] { Q8(rgb[0]), Q8(rgb[1]), Q8(rgb[2]), Q8(alpha) };
        }

        /// <summary>Every material uniform the shader reads, by name, for a surface of
        /// <paramref name="width"/> × <paramref name="height"/> device pixels (without the 2 px padding) at
        /// <paramref name="density"/> device pixels per logical pixel. Uniforms not listed stay 0.</summary>
        public static Dictionary<string, double[]> Uniforms(GlassLook m, double width, double height, double density)
        {
            if (m.TintAmount < 0 || m.TintAmount > 100) throw new ArgumentOutOfRangeException(nameof(m), "TintAmount must be 0 to 100");
            var v = new Dictionary<string, double[]>
            {
                // clearMaterialUniforms (magnification 1).
                ["uSize"] = new[] { width, height },
                ["uPad"] = new[] { 2.0 },
                // The backdrop texture is sampled clamped at its edges, so the backdrop region is unbounded.
                ["uBackdrop"] = new[] { -1e5, -1e5, 1e5, 1e5 },
                ["uRefractBand"] = new[] { 24.0 },
                ["uRefractDepth"] = new[] { 12.0 },
                ["uIor"] = new[] { 1.5 },
                ["uBevelPower"] = new[] { 2.0 },
                ["uCornerPower"] = new[] { 2.0 },
                ["uFieldRange"] = new[] { 1.0 },
                ["uFieldScale"] = new[] { 1.0 },
                ["uBevel"] = new[] { 2.0 },
                ["uSpecularPow"] = new[] { 4.0 },
                ["uCounterLight"] = new[] { 1.0 },
                ["uEdgeLight"] = new[] { 1.0 },
                ["uTint"] = new[] { 1.0, 1, 1, 0 },
                ["uFineShare"] = new[] { 1.0 },
                ["uWideScale"] = new[] { .25 },
                ["uTouch"] = new[] { width / 2, height / 2 },
                ["uPoseA"] = new[] { 1.0, 0, 0, 1 },
                ["uPoseAInv"] = new[] { 1.0, 0, 0, 1 },
                ["uPoseD"] = new[] { 1.0, 1, 1, 0 },
                ["uPoseE"] = new[] { 0.0, 3, 0, 0 },
            };

            // inAppMaterialUniforms.
            double t = m.TintAmount / 100.0;
            double limit = Math.Min(width, height) / 2;
            double r = Math.Min(m.Radius * density, limit);
            double grey = m.Dark ? 0 : 241 / 255.0;
            double opacity = Math.Round(Interpolate(m.Dark ? DarkOpacity : LightOpacity, m.TintAmount) * 255, MidpointRounding.AwayFromZero) / 255;
            v["uRadii"] = new[] { r, r, r, r };
            v["uRefractBand"] = new[] { .6 * r };
            v["uRefractDepth"] = new[] { 14 * density };
            v["uProfile"] = new[] { 1.0 };
            v["uFormation"] = new[] { 0.0 };
            v["uHeldLens"] = new[] { 0.0 };
            v["uHeldMagnification"] = new[] { 0.0 };
            v["uHeldGlow"] = new[] { 1.0 };
            v["uAberration"] = new[] { .07 };
            v["uWideKernel"] = new[] { 10 * density };
            v["uFineShare"] = new[] { .95 * Math.Pow(1 - t, 1.5) };
            v["uTint"] = new[] { grey, grey, grey, opacity };
            v["uLift"] = new[] { m.Dark ? 35 / 255.0 : 0 };
            v["uBevel"] = new[] { 1.33 * density };
            v["uBevelPeak"] = new[] { .25 };
            v["uSpecular"] = new[] { .25 };
            v["uSpecularPow"] = new[] { 6.0 };
            v["uCounterLight"] = new[] { 1.0 };
            v["uEdgeLight"] = new[] { 0.0 };
            v["uLight"] = new double[] { m.LightX, m.LightY };
            v["uLegibility"] = new[] { .6 };
            v["uScale"] = new[] { Clamp01((Math.Min(width, height) / density - 56) / 264) };
            v["uMaterialize"] = new[] { Clamp01(m.Materialize) };
            v["uFrost"] = new[] { Clamp01(m.ReducedTransparency) };
            v["uContrast"] = new[] { Clamp01(m.IncreasedContrast) };
            // The lab fit (GlassStyle.towardLab). The detail reach is the lab's at every tint, which selects the
            // four-tap detail kernel (uDetailCross: 0 < blurRadius <= 1/3 dp, decided in dp).
            double w = LabWeight(m.TintAmount);
            v["uBlur"] = new[] { LabDetailReach * density };
            v["uDetailCross"] = new[] { 1.0 };
            if (w > 0)
            {
                double Mix(double a, double b) => a + (b - a) * w;
                double labGrey = m.Dark ? 0 : 1;
                v["uTint"] = LerpColor(v["uTint"], new[] { labGrey, labGrey, labGrey, Q8(LabOpacity(m.Dark)) }, w);
                v["uLift"] = new[] { Mix(v["uLift"][0], LabLift(m.Dark)) };
                v["uFineShare"] = new[] { Mix(v["uFineShare"][0], LabDetailShare(m.Dark)) };
                v["uWideKernel"] = new[] { Mix(v["uWideKernel"][0], LabWideKernel * density) };
                v["uDetailLift"] = new[] { LabDetailLift(m.Dark) * w };
                v["uToneCurve"] = new[] { LabToneCurve(m.Dark) * w };
                v["uLegibility"] = new[] { Mix(v["uLegibility"][0], 0) };
                v["uSoftDetail"] = new[] { Mix(0, 1) };
            }
            // The colour transfer (#201), always written, zeros included.
            var chroma = ChromaOf(v.TryGetValue("uDetailLift", out var lift) ? lift[0] : 0);
            v["uChromaGain"] = new[] { chroma[0], chroma[1], chroma[2] };
            v["uChromaSlope"] = new[] { chroma[3], chroma[4], chroma[5] };
            v["uChromaWeight"] = new[] { chroma[6] };
            if (m.HasTint)
            {
                double Q(double c) => Math.Round(c * 255.0, MidpointRounding.AwayFromZero) / 255;
                v["uTint"] = new[] { Q(m.TintR), Q(m.TintG), Q(m.TintB), Q(m.TintA) };
            }
            // Unity reads the wide tone from the backdrop directly (tool/generate_shader.mjs): switch it on.
            v["uWideStrip"] = new[] { 1.0 };
            return v;
        }
    }
}

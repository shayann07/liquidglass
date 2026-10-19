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
            v["uBlur"] = new[] { (.5 + 1.5 * t * t) / .6 * density };
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

using System.Text.Json;
using LiquidGlass.Skia;

/// <summary>The free button against the Kotlin-exported trajectories (ports/skia/fixtures/free-button.json).</summary>
internal static class FreeButtonChecks
{
    private static void Check(bool condition, string message) { if (!condition) throw new Exception(message); }

    public static object Run()
    {
        using var fixture = JsonDocument.Parse(File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "fixtures", "free-button.json")));
        var b = fixture.RootElement.GetProperty("button");
        double G(string key) => b.GetProperty(key).GetDouble();
        double worstPx = 0, worstScalar = 0; var frames = 0;
        foreach (var scenario in fixture.RootElement.GetProperty("scenarios").EnumerateArray())
        {
            var button = new FreeButton(G("size"), G("density"), G("pressScale"), G("pressGrowth"), G("pressLift"),
                G("pullFollow"), G("pullElongation"), G("pullWidthRatio"), G("pullLimit"), G("pullLiftShare"),
                b.TryGetProperty("pullReleaseDampingX", out var prd) ? prd.GetDouble() : .3);
            var calls = scenario.GetProperty("calls").EnumerateArray().ToList();
            var next = 0;
            foreach (var row in scenario.GetProperty("frames").EnumerateArray())
            {
                var f = row.EnumerateArray().Select(v => v.GetDouble()).ToArray();
                var t = f[0];
                while (next < calls.Count && calls[next][0].GetDouble() <= t + 1e-9)
                {
                    var c = calls[next++]; var ct = c[0].GetDouble();
                    switch (c[1].GetString())
                    {
                        case "down": button.Down(ct); break;
                        case "up": button.Up(ct); break;
                        default: button.Move(ct, c[2].GetDouble(), c[3].GetDouble()); break;
                    }
                }
                var o = button.Sample(t);
                worstPx = Math.Max(worstPx, Math.Max(Math.Abs(o.OffsetX - f[6]), Math.Max(Math.Abs(o.OffsetY - f[7]), Math.Abs(o.PullX - f[8]))));
                worstScalar = Math.Max(worstScalar, new[] { o.Glow - f[1], o.PressScale - f[2], o.Along - f[3], o.Across - f[4] }.Max(Math.Abs));
                frames++;
            }
        }
        // Compose ends a spring once it is within its 0.01 visibility threshold; a scale can sit 0.003 off then.
        Check(worstPx < .1, $"Free button offset or pull differs by {worstPx} px");
        Check(worstScalar < 3e-3, $"Free button scalar differs by {worstScalar}");
        return new { check = "free button (search pull) trajectories against Kotlin", frames, worstPx, worstScalar, passed = true };
    }
}

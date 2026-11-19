// Apache-2.0. Render a frame at any time (#193): a baked navigation equals the live motion, step for step, in any order.
using System.Text.Json;
using LiquidGlass.Skia;

internal static class NavigationTrackChecks
{
    private static void Check(bool condition, string message) { if (!condition) throw new Exception(message); }
    private static void Throws<T>(Action action) where T : Exception
    {
        try { action(); } catch (T) { return; }
        throw new Exception($"Expected {typeof(T).Name}");
    }

    // A tap; a hold that drags across the tabs and is flung; a hold that is cancelled; the host's own selection.
    private static List<NavigationEvent> Script()
    {
        var e = new List<NavigationEvent>
        {
            new(NavigationEventType.Down, .1, 160, 31), new(NavigationEventType.Up, .16, 160, 31),
            new(NavigationEventType.Down, .6, 50, 31),
        };
        for (var i = 0; i < 20; i++) e.Add(new(NavigationEventType.Move, .62 + i * .02, 50, 31));
        for (var i = 0; i < 24; i++) e.Add(new(NavigationEventType.Move, 1.05 + i * .015, 50 + 200.0 * (i + 1) / 24, 31));
        e.Add(new(NavigationEventType.Up, 1.42, 250, 31));
        e.Add(new(NavigationEventType.Down, 1.9, 150, 31));
        for (var i = 0; i < 15; i++) e.Add(new(NavigationEventType.Move, 1.92 + i * .02, 150, 31));
        e.Add(new(NavigationEventType.Cancel, 2.3)); e.Add(new(NavigationEventType.Select, 2.5, Index: 0));
        return e;
    }

    private static bool Same(NavigationSample a, NavigationSample b) =>
        a.CentreX == b.CentreX && a.CentreY == b.CentreY && a.Extents == b.Extents && a.Formation == b.Formation &&
        a.TransitAppearance == b.TransitAppearance && a.Touch == b.Touch && a.HeldTravel == b.HeldTravel && a.Protrusion == b.Protrusion &&
        a.ItemFollow == b.ItemFollow && a.Held == b.Held && a.TapFormed == b.TapFormed && a.Hold == b.Hold && a.Feedback == b.Feedback &&
        a.Selected == b.Selected && a.Active == b.Active && a.Pose.A.SequenceEqual(b.Pose.A) && a.Pose.AInv.SequenceEqual(b.Pose.AInv) &&
        a.Pose.C.SequenceEqual(b.Pose.C) && a.Pose.D.SequenceEqual(b.Pose.D) && a.Pose.E!.SequenceEqual(b.Pose.E!);

    public static object Run()
    {
        var bar = CalmSelectorBar.Create(300, 62, 3);
        var events = Script();
        const double duration = 3;
        var a = NavigationBaker.Bake(bar, events, duration); var b = NavigationBaker.Bake(bar, events, duration);
        Check(a.Length == 721 && a.Step == 1.0 / 240, $"Track length {a.Length}");
        Check(JsonSerializer.Serialize(a.ToData()) == JsonSerializer.Serialize(b.ToData()), "Two bakes of one script differ");

        // Every step equals a live CalmNavigation and CalmInteraction driven at the same step.
        CalmNavigation? live = null;
        live = new CalmNavigation(bar, 0, i => live!.Select(i));
        var motion = new CalmInteraction(new CalmGeometry(bar.Width, bar.Height, bar.Height / 2), new CalmOptions(PressScale: 1.05, PressGrowth: 0));
        var feedback = motion.Sample(0); (double X, double Y)? down = null; var next = 0; var worst = 0;
        for (var k = 0; k < a.Length; k++)
        {
            var t = k * a.Step;
            for (; next < events.Count && events[next].Time <= t; next++)
            {
                var e = events[next];
                switch (e.Type)
                {
                    case NavigationEventType.Down: down = (e.X, e.Y); live.Down(e.X, e.Y, e.Time); feedback = motion.Press(e.Time); break;
                    case NavigationEventType.Move: live.Move(e.X, e.Y, e.Time); if (down is { } d) feedback = motion.Press(e.Time, e.X - d.X, e.Y - d.Y); break;
                    case NavigationEventType.Up: live.Up(e.X, e.Y, e.Time); down = null; feedback = motion.Release(e.Time); break;
                    case NavigationEventType.Cancel: live.Cancel(e.Time); down = null; feedback = motion.Release(e.Time); break;
                    default: live.Select(e.Index); break;
                }
            }
            feedback = motion.Sample(t); var moving = live.Frame(t);
            var s = a.Sample(t); var c = live.Controller;
            Check(s.Formation == c.Formation && s.CentreX == c.CentreX && s.HeldTravel == c.HeldTravel && s.Touch == live.Touch &&
                s.Feedback == feedback && s.Selected == live.Selected && s.Active == (feedback.Active || moving) && s.Held == c.IsHeld &&
                s.Pose.A.SequenceEqual(c.Render().A) && s.Pose.D.SequenceEqual(c.Render().D), $"Baked step {k} differs from the live motion");
            worst = Math.Max(worst, k);
        }

        // Order independence, plain data, clamping and the space between steps.
        var step = 1.0 / 60; var coarse = NavigationBaker.Bake(bar, events, duration, step);
        var times = Enumerable.Range(0, 200).Select(i => i * 0.0173).ToList();
        var forward = times.Select(coarse.Sample).ToList();
        var order = Enumerable.Range(0, times.Count).OrderBy(i => (i * 7919) % 201).ToList();
        foreach (var i in order) Check(Same(coarse.Sample(times[i]), forward[i]), $"Sample at {times[i]} depends on the order");
        var copy = NavigationTrack.FromData(JsonSerializer.Deserialize<NavigationTrackData>(JsonSerializer.Serialize(coarse.ToData()))!);
        for (var i = 0; i < times.Count; i++) Check(Same(copy.Sample(times[i]), forward[i]), "A track copied through JSON samples differently");
        Check(Same(coarse.Sample(-5), coarse.Sample(0)) && Same(coarse.Sample(99), coarse.Sample((coarse.Length - 1) * step)), "Sample did not clamp");
        for (var k = 0; k < coarse.Length - 1; k++)
        {
            NavigationSample lo = coarse.Sample(k * step), hi = coarse.Sample((k + 1) * step), mid = coarse.Sample((k + .37) * step);
            foreach (var (x, y, m) in new[] { (lo.Formation, hi.Formation, mid.Formation), (lo.CentreX, hi.CentreX, mid.CentreX),
                (lo.Touch, hi.Touch, mid.Touch), (lo.Feedback.A, hi.Feedback.A, mid.Feedback.A), ((double)lo.Pose.A[0], hi.Pose.A[0], mid.Pose.A[0]) })
                Check(m >= Math.Min(x, y) - 1e-6 && m <= Math.Max(x, y) + 1e-6, $"A value left its neighbours at step {k}");
            float[] ma = mid.Pose.A, mi = mid.Pose.AInv;
            double[] product = [ma[0] * mi[0] + ma[1] * mi[2], ma[0] * mi[1] + ma[1] * mi[3], ma[2] * mi[0] + ma[3] * mi[2], ma[2] * mi[1] + ma[3] * mi[3]];
            Check(Math.Abs(product[0] - 1) < 1e-5 && Math.Abs(product[1]) < 1e-5 && Math.Abs(product[2]) < 1e-5 && Math.Abs(product[3] - 1) < 1e-5, $"AInv is not the inverse of A at step {k}");
            Check(mid.Held == lo.Held && mid.Selected == lo.Selected && mid.TapFormed == lo.TapFormed, "A decision did not keep the earlier step's value");
        }
        Check(coarse.Sample(duration).Selected == 0 && coarse.Sample(1.6).Selected == 2, "Committed and host selections");

        Throws<ArgumentException>(() => NavigationBaker.Bake(bar, [new(NavigationEventType.Down, .5, 1, 1), new(NavigationEventType.Up, .2, 1, 1)], 1));
        Throws<ArgumentException>(() => NavigationBaker.Bake(bar, [new(NavigationEventType.Move, 0, double.NaN, 1)], 1));
        Throws<ArgumentException>(() => NavigationBaker.Bake(bar, [new((NavigationEventType)9, 0)], 1));
        Throws<ArgumentOutOfRangeException>(() => NavigationBaker.Bake(bar, [], -1));
        Throws<ArgumentOutOfRangeException>(() => NavigationBaker.Bake(bar, [], 1, 0));
        // A version 1 track (before the hold terms) still loads and plays as it did: no hold, the fill following the formation.
        var v2Data = coarse.ToData();
        int[] added = [35, 36, 43, 44];
        var v1Data = new NavigationTrackData(1, v2Data.Step, v2Data.Duration, v2Data.Columns.Where((_, i) => !added.Contains(i)).ToArray());
        var old = NavigationTrack.FromData(JsonSerializer.Deserialize<NavigationTrackData>(JsonSerializer.Serialize(v1Data))!);
        Check(old.Length == coarse.Length && old.ToData().Version == 2, "A version 1 track did not load");
        foreach (var t in times)
        {
            var o = old.Sample(t); var n = coarse.Sample(t);
            Check(o.Hold == new CalmHold(false, false, o.Formation, o.Formation), $"A version 1 track has a hold at {t}");
            Check(Same(o with { Hold = n.Hold }, n with { Hold = n.Hold }), $"A version 1 track plays differently at {t}");
        }
        Throws<ArgumentException>(() => NavigationTrack.FromData(new NavigationTrackData(1, 1, 1, v1Data.Columns[1..])));
        Throws<ArgumentException>(() => NavigationTrack.FromData(new NavigationTrackData(3, 1, 1, v2Data.Columns)));
        Throws<ArgumentException>(() => NavigationTrack.FromData(new NavigationTrackData(2, 1, 1, [])));
        Throws<ArgumentOutOfRangeException>(() => coarse.Sample(double.NaN));
        Check(NavigationBaker.Bake(bar, [], 0).Length == 1, "A zero-length bake has one step");
        return new { check = "baked navigation: equals the live motion at 721 steps, any order, JSON copy, clamping, interpolation", passed = true };
    }
}

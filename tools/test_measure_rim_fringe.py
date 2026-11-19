"""Self-test for the content-split and rim-separation scorer (#101 item 88):
    python -B tools/test_measure_rim_fringe.py

Synthetic frames only, so it runs without any recording. Checks the lens-capsule
geometry, rim-band (< rim_exclude) vs interior (>= rim_exclude) separation,
unclipped-capsule behavior near an image edge, finite-frame summary counts,
and trace.csv outline lookup.
"""
import os, sys, unittest
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import measure_rim_fringe as m


def frame(split_rows, shift):
    """230 x 915 grey frame with vertical white bars every 40 px; in `split_rows` R is `shift` px right of B."""
    x = np.zeros((230, 915, 3), np.float32)
    for c in range(100, 800, 40):
        x[:, c:c + 20] = 255
    for r in split_rows:
        x[r, :, 0] = np.roll(x[r, :, 0], shift)
    return x


class CapsuleDepth(unittest.TestCase):
    def test_depth(self):
        d = m.capsule_depth([300, 300, 300, 10], [115.5, 6, 300, 115.5], 135, 465, 6, 225)
        self.assertAlmostEqual(d[0], 109.5, 3)   # centre
        self.assertAlmostEqual(d[1], 0.0, 3)     # on the top edge
        self.assertLess(d[2], 0)                 # below the lens
        self.assertLess(d[3], 0)                 # left of the lens's end


class RimSeparation(unittest.TestCase):
    def test_rim_only_split_separates_with_24px(self):
        # Capsule rows 6..225 -> top rim < 24 px is rows 6..30, bottom rim < 24 px is rows 201..225.
        # Put a 3 px R-B split strictly in rows < 28 or > 203 inside lens cols (135..465).
        lens = (135, 465)
        cap = (lens[0], lens[1], 6, 225)
        x = frame([], 0)
        for r in range(230):
            if r < 28 or r > 203:
                x[r, 135:465, 0] = np.roll(x[r, 135:465, 0], 3)
        rim, n_rim = m.rb_stats(x, np.random.default_rng(1), None, True, (*cap, 0.0, 24.0))
        core, n_core = m.rb_stats(x, np.random.default_rng(1), None, True, (*cap, 24.0))
        inside, _ = m.rb_stats(x, np.random.default_rng(1), lens, True)
        outside, n_out = m.rb_stats(x, np.random.default_rng(1), lens, False)
        self.assertGreater(n_rim, 10)
        self.assertGreater(n_core, 10)
        self.assertGreater(n_out, 10)
        self.assertAlmostEqual(rim, 3.0, delta=0.5)   # inside rim band sees the rim split
        self.assertAlmostEqual(core, 0.0, delta=0.5)  # away from rim reads ~0
        self.assertGreater(inside, 2.0)               # merged under-lens sees the rim split
        self.assertAlmostEqual(outside, 0.0, delta=0.5)

    def test_interior_only_split_separates_with_24px(self):
        # Put a 3 px R-B split only deep inside the lens (rows 60..170, cols 245..370 -> d >= 52 px).
        lens = (135, 465)
        cap = (lens[0], lens[1], 6, 225)
        x = frame([], 0)
        for r in range(60, 170):
            x[r, 245:370, 0] = np.roll(x[r, 245:370, 0], 3)
        rim, n_rim = m.rb_stats(x, np.random.default_rng(1), None, True, (*cap, 0.0, 24.0))
        core, n_core = m.rb_stats(x, np.random.default_rng(1), None, True, (*cap, 24.0))
        outside, n_out = m.rb_stats(x, np.random.default_rng(1), lens, False)
        self.assertGreater(n_rim, 10)
        self.assertGreater(n_core, 10)
        self.assertGreater(n_out, 10)
        self.assertAlmostEqual(core, 3.0, delta=0.5)  # away from rim sees the interior split
        self.assertAlmostEqual(rim, 0.0, delta=0.5)   # inside rim band reads ~0
        self.assertAlmostEqual(outside, 0.0, delta=0.5)


class LensNearImageEdge(unittest.TestCase):
    def test_measure_uses_unclipped_capsule(self):
        # Lens centre at x=20: full bounds (-145, 185), clipped to (0, 185) for column sampling.
        # Edge column x=155 at y=115.5 is 30 px inside the true outline (47 px inside clipped).
        # With rim_exclude=40, x=155 belongs in inside-rim-band (rb_rim) and NOT away-from-rim (rb_core).
        self.assertAlmostEqual(float(m.capsule_depth([155], [115.5], -145, 185, 6, 225)[0]), 30.0, 1)
        self.assertGreater(float(m.capsule_depth([155], [115.5], 0, 185, 6, 225)[0]), 40)
        x = np.zeros((230, 915, 3), np.float32)
        for r in range(40, 190, 2):
            x[r, 150:160, :] = 255
            x[r, :, 0] = np.roll(x[r, :, 0], 3)
        old = (m.CONTENT_SPLIT, m.RIM_EXCLUDE)
        m.CONTENT_SPLIT, m.RIM_EXCLUDE = True, 40.0
        try:
            out = m.measure(x, 40, np.random.default_rng(1), None, (0, 185), (-145, 185))
        finally:
            m.CONTENT_SPLIT, m.RIM_EXCLUDE = old
        self.assertTrue(np.isnan(out['rb_core']))
        self.assertEqual(out['rb_core_n'], 0)
        self.assertGreater(out['rb_rim_n'], 10)
        self.assertAlmostEqual(out['rb_rim'], 3.0, delta=0.5)


class FiniteFrameSummary(unittest.TestCase):
    def test_count_covers_only_frames_with_an_estimate(self):
        nan = float('nan')
        rows = [dict(i_rb_core=nan, i_rb_core_n=n) for n in (9, 9, 9)] + [dict(i_rb_core=2.0, i_rb_core_n=20)] * 2
        text, edges = m.rb_summary(rows, 'i_rb_core')
        self.assertEqual(edges, 20.0)
        self.assertIn('n=2', text)
        text2, edges2 = m.core_summary(rows, 'i')
        self.assertEqual((text2, edges2), (text, edges))
        self.assertEqual(m.rb_summary([dict(i_rb_core=nan, i_rb_core_n=9)], 'i_rb_core'), ('n=0', 0.0))


class OutlineSource(unittest.TestCase):
    def test_trace_outline_and_fallback(self):
        otr = {100: (200.0, 350.0, 20.0)}  # bar-local selectorX=200 -> scene cx=263
        lens, full, rows = m.ours_outline(otr, 100, 500.0)
        self.assertEqual(lens, (88, 438))
        self.assertEqual(full, (88.0, 438.0))
        self.assertEqual(rows, (436.0, 656.0))
        # Missing frame or missing trace falls back to track.csv cx +- 165, 436..655
        lens_fb, full_fb, rows_fb = m.ours_outline(otr, 999, 500.0)
        self.assertEqual(lens_fb, (335, 665))
        self.assertEqual(full_fb, (335.0, 665.0))
        self.assertEqual(rows_fb, m.LENS_ROWS)
        lens_fb2, full_fb2, rows_fb2 = m.ours_outline(None, 100, 500.0)
        self.assertEqual((lens_fb2, full_fb2, rows_fb2), (lens_fb, full_fb, rows_fb))


class FastPathEquivalence(unittest.TestCase):
    def test_hue_chroma_and_fringe_mask_match_naive(self):
        rng = np.random.default_rng(7)
        x = rng.integers(0, 256, (40, 80, 3), dtype=np.uint8)
        xf = x.astype(np.float32)
        r, g, b = xf[..., 0], xf[..., 1], xf[..., 2]
        mx, mn = xf.max(2), xf.min(2)
        c_ref = mx - mn
        h_ref = np.zeros_like(c_ref); nz = c_ref > 0
        i = nz & (mx == r); h_ref[i] = ((g - b)[i] / c_ref[i]) % 6
        i = nz & (mx == g) & (mx != r); h_ref[i] = (b - r)[i] / c_ref[i] + 2
        i = nz & (mx == b) & (mx != r) & (mx != g); h_ref[i] = (r - g)[i] / c_ref[i] + 4
        h_ref *= 60
        m_ref = (c_ref > 40) & ~((h_ref >= 180) & (h_ref <= 240))

        h_got, c_got = m.hue_chroma(x)
        self.assertTrue(np.allclose(c_got, c_ref))
        self.assertTrue(np.allclose(h_got, h_ref, atol=1e-4))
        m_got, hm_got, cm_got = m.fringe_mask(x, 40)
        self.assertTrue(np.array_equal(m_got, m_ref))
        self.assertTrue(np.allclose(hm_got[m_got], h_ref[m_ref], atol=1e-4))
        self.assertTrue(np.allclose(cm_got, c_ref))

    def test_rb_from_edges_matches_best_shift_loop(self):
        x = frame(range(230), 3).astype(np.uint8)
        xf, hm, ys, xs = m.prep_edges(x)
        r_vec, n_vec = m.rb_from_edges(xf, hm, ys, xs, np.random.default_rng(1))
        # Direct scalar reference via best_shift
        rng = np.random.default_rng(1)
        g = x.astype(np.float32)[..., 1]
        gx = np.abs(np.diff(g, axis=1))[:-1]; gy = np.abs(np.diff(g, axis=0))[:, :-1]
        gm = np.maximum(gx, gy); gm[:12] = 0; gm[-12:] = 0; gm[:, :12] = 0; gm[:, -12:] = 0
        ry, rx = np.nonzero(gm > 30)
        sel = rng.choice(len(ry), 400, replace=False)
        out = []
        for y, xx in zip(ry[sel], rx[sel]):
            pr, pb = (xf[y, xx - 12:xx + 13, 0], xf[y, xx - 12:xx + 13, 2]) if gx[y, xx] >= gy[y, xx] else (xf[y - 12:y + 13, xx, 0], xf[y - 12:y + 13, xx, 2])
            if pr.std() < 8 or pb.std() < 8: continue
            s = m.best_shift(pr, pb)
            if s is not None: out.append(s)
        self.assertEqual(n_vec, len(out))
        self.assertAlmostEqual(r_vec, float(np.percentile(out, 90)), places=5)

    def test_measure_width_matches_full_array_ndi(self):
        from scipy import ndimage as ndi
        rng = np.random.default_rng(11)
        x = np.zeros((230, 915, 3), np.uint8)
        # Put chromatic blobs touching an edge and in the interior
        x[0:6, 0:6] = [255, 40, 40]
        x[100:112, 400:415] = [40, 255, 40]
        x[200:203, 700:703] = [255, 255, 0]
        old = m.CONTENT_SPLIT
        m.CONTENT_SPLIT = False
        try:
            got = m.measure(x, 40, rng)
        finally:
            m.CONTENT_SPLIT = old
        mm, _, _ = m.fringe_mask(x, 40)
        lab, k = ndi.label(mm)
        dt = ndi.distance_transform_edt(mm)
        idx = np.arange(1, k + 1)
        mx = np.asarray(ndi.maximum(dt, lab, index=idx))
        ar = np.asarray(ndi.sum(mm, lab, index=idx))
        keep = ar >= 4; widths, areas = 2 * mx[keep], ar[keep]
        o = np.argsort(widths); cw = np.cumsum(areas[o])
        want_w = float(widths[o][np.searchsorted(cw, cw[-1] / 2)])
        self.assertAlmostEqual(got['width'], want_w, places=10)


if __name__ == '__main__':
    unittest.main()

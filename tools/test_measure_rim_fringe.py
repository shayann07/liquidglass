"""Self-test for the content-split scorer (#101 item 88): python tools/test_measure_rim_fringe.py

Synthetic frames only, so it runs without any recording. Checks the lens-capsule geometry and that
`rb_stats` counts a colour-split edge deep inside the lens but not one on the lens's rim.
"""
import os, sys, unittest
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import measure_rim_fringe as m


def frame(split_rows, shift):
    """230 x 915 grey frame with a vertical white bar every 40 px; in `split_rows` the R channel is `shift` px right of B."""
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


class CoreEdges(unittest.TestCase):
    def test_core_drops_the_rim(self):
        lens = (135, 465)
        x = frame(range(0, 230), 3)  # every row split by 3 px
        rng = np.random.default_rng(1)
        all_in, _ = m.rb_stats(x, rng, lens, True)
        core, n_core = m.rb_stats(x, np.random.default_rng(1), None, True, (lens[0], lens[1], 6, 225, 40))
        self.assertGreater(n_core, 10)
        self.assertAlmostEqual(core, 3.0, delta=.5)

    def test_rim_only_split_is_invisible_in_core(self):
        lens = (135, 465)
        x = frame([r for r in range(230) if r < 40 or r > 190], 3)  # split only near the lens's top and bottom rim
        core, n_core = m.rb_stats(x, np.random.default_rng(1), None, True, (lens[0], lens[1], 6, 225, 40))
        self.assertAlmostEqual(core, 0.0, delta=.5)  # clean edges in the core read 0 px
        inside, _ = m.rb_stats(x, np.random.default_rng(1), lens, True)
        self.assertGreater(inside, 2.0)              # the plain under-lens figure does see the rim's split


class LensNearImageEdge(unittest.TestCase):
    def test_core_uses_the_unclipped_capsule(self):
        # Lens centre at x=20: full bounds (-145, 185), clipped to (0, 185) for sampling. An edge column at x=155 on the
        # lens's middle row is 30 px inside the true outline (47 px inside the clipped one), so a 40 px core drops it.
        self.assertAlmostEqual(float(m.capsule_depth([155], [115.5], -145, 185, 6, 225)[0]), 30.0, 1)
        self.assertGreater(float(m.capsule_depth([155], [115.5], 0, 185, 6, 225)[0]), 40)
        x = np.zeros((230, 915, 3), np.float32)
        for r in range(40, 190, 2):  # many strong, colour-split edges in a thin band of columns around x=155
            x[r, 150:160, :] = 255
            x[r, :, 0] = np.roll(x[r, :, 0], 3)
        old = (m.CONTENT_SPLIT, m.CORE_RIM)
        m.CONTENT_SPLIT, m.CORE_RIM = True, 40
        try:
            out = m.measure(x, 40, np.random.default_rng(1), None, (0, 185), (-145, 185))
        finally:
            m.CONTENT_SPLIT, m.CORE_RIM = old
        self.assertTrue(np.isnan(out['rb_core']))  # nothing 40 px inside the true outline there
        self.assertEqual(out['rb_core_n'], 0)


class CoreSummary(unittest.TestCase):
    def test_count_covers_only_frames_with_an_estimate(self):
        nan = float('nan')
        rows = [dict(i_rb_core=nan, i_rb_core_n=n) for n in (9, 9, 9)] + [dict(i_rb_core=2.0, i_rb_core_n=20)] * 2
        text, edges = m.core_summary(rows, 'i')
        self.assertEqual(edges, 20.0)
        self.assertIn('n=2', text)
        self.assertEqual(m.core_summary([dict(i_rb_core=nan, i_rb_core_n=9)], 'i'), ('n=0', 0.0))


if __name__ == '__main__':
    unittest.main()

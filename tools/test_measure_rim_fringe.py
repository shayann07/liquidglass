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


if __name__ == '__main__':
    unittest.main()

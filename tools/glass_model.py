"""The measured forward model of iOS 27 regular glass at Tint 50 (#199, #200). Specification, no data.

Measured on 16 November 2026 on an iPhone 13 (iOS 27, 3x) from lossless screenshots of Safari's Liquid Glass
address bar over backdrops drawn at device pixels (17 greys, stripes 2-128 px both ways, edges, bars, checkers,
ramps, at nine grey-level pairs; 89 one-dimensional stills fitted per appearance). The same constants reproduce
the Photos toolbar over the calibration chart, so the toolbar, the address pill and plain in-app glass share it.
The stills are private; only these numbers are published.

Per channel, in display code values 0-255, for the interior of the glass (away from the rim band, #202):

    lin = B + k (D - B) + c |D - B|
    out = tone(lin)

    B     the backdrop under a Gaussian of sigma SIGMA_BASE_PT (the tone of the glass)
    D     the backdrop under a Gaussian of sigma SIGMA_DETAIL_PX at 3x (the detail copy)
    k     the detail share, K_DARK / K_LIGHT
    c     the one-sided term: positive on light glass (detail brighter than its surroundings is kept at
          k + c, darker detail at k - c), negative on dark glass (the reverse)
    tone  the appearance's curve over flat grey: DARK_TONE (17 measured points; dark_tone() is the
          quadratic the shader uses) or light_tone() (linear, clipped at 255)

Scores of this model (rms in levels of 255): the 89 fitted stills 1.31 dark / 1.30 light; held out, checkers
1.2-2.3, the calibration chart at 7 offsets 0.7-2.8 (colour-bar row excluded), the Photos toolbar 0.9 / 2.5.
Colour (#201). Over colour the mix above runs per channel as written, and the tone becomes a colour transfer
about the Rec. 709 luma y of the sRGB code values, with the chroma kept and boosted:

    y   = 0.2126 R + 0.7152 G + 0.0722 B
    out = clip(tone(y) + (g0 + g1 y / 255) (lin - y), 0, 255)       per channel, g0 and g1 per channel

    g0, g1  CHROMA_DARK / CHROMA_LIGHT (R, G, B), fitted on the 125-point RGB cube (5 levels per channel, flat colour)

The cube was drawn in sRGB and captured in Display P3 code values. It is converted once to sRGB (P3 code values to
linear light, the P3-to-sRGB matrix through D65 XYZ, sRGB encoding) and clipped to the sRGB gamut, which is all an
sRGB render can show. In sRGB the transfer is within 0.99 levels rms (at most 7.1) of the cube on dark glass and
0.83 (at most 5.3) on light; per-channel tone misses it by 36 / 29. Read back in P3 it is 4.0 / 2.9 rms, of which
3.9 / 2.8 is the sRGB gamut itself. Held out, 18 colours from another lab program: 1.5 / 1.0 rms (at most 7.1 / 5.3).
One gain for all three channels does almost as well (1.11 / 0.94 rms) but reaches 9.5 levels on dark magenta. Running
the detail term on luma instead of per channel misses the colour-pair stripe stills by 7-31 levels; per channel, 1.5.

Not covered: the rim band (#202), the tab bar (#203), the menu panel (#204), tints other than 50 (#208).

    python tools/glass_model.py            prints the predictions GlassLabModelTest asserts
"""
import math

SIGMA_BASE_PT = 15.5        # measured: edge-spread and stripe fit, flat from 44 to 52 px at 3x (46.5 px)
SIGMA_DETAIL_PX = 0.83      # measured at 3x only (0.28 pt): 2 px stripes vanish, 4 px keep ~49 %, 6 px ~73 %
K_DARK, K_LIGHT = 0.277, 0.273
C_DARK, C_LIGHT = -0.22, 0.22
GREY_IN = [0, 16, 32, 48, 64, 80, 96, 112, 128, 144, 160, 176, 192, 208, 224, 240, 255]
DARK_TONE = [32, 47, 62, 76, 89, 102, 113, 124, 134, 143, 152, 159, 166, 172, 177, 182, 185]
# Fit of DARK_TONE: rms 0.22, at most 0.42 levels from any measured point.
DARK_QUAD = (31.72, 254.40, -101.01)
LIGHT_TONE = (132.5, 0.502)
LUMA = (0.2126, 0.7152, 0.0722)
CHROMA_DARK = ((1.317, 1.256, 1.314), (-0.427, -0.351, -0.429))
CHROMA_LIGHT = ((0.988, 0.952, 0.979), (-0.076, 0.028, -0.045))


def dark_tone(v):
    x = min(max(v, 0.0), 255.0) / 255.0
    return DARK_QUAD[0] + DARK_QUAD[1] * x + DARK_QUAD[2] * x * x


def light_tone(v):
    return min(LIGHT_TONE[0] + LIGHT_TONE[1] * min(max(v, 0.0), 255.0), 255.0)


def tone(v, dark):
    return dark_tone(v) if dark else light_tone(v)


def colour(rgb, dark):
    """The transfer over a flat colour, sRGB code values 0-255 in and out (#201). Over grey it is tone()."""
    x = [min(max(v, 0.0), 255.0) for v in rgb]
    y = sum(w * v for w, v in zip(LUMA, x))
    g0, g1 = CHROMA_DARK if dark else CHROMA_LIGHT
    return [min(max(tone(y, dark) + (a + b * y / 255.0) * (v - y), 0.0), 255.0) for v, a, b in zip(x, g0, g1)]


def gaussian_1d(row, sigma):
    """A 1-D Gaussian with edge replication, for profiles that vary along one axis only."""
    if sigma <= 0:
        return list(row)
    r = int(math.ceil(3.5 * sigma))
    w = [math.exp(-0.5 * (i / sigma) ** 2) for i in range(-r, r + 1)]
    s = sum(w)
    n = len(row)
    return [sum(w[j] * row[min(max(i + j - r, 0), n - 1)] for j in range(2 * r + 1)) / s for i in range(n)]


def predict_1d(row, dark, px_per_pt=3.0):
    """The glass over a backdrop that varies along one axis: row in code values, one sample per device pixel."""
    k, c = (K_DARK, C_DARK) if dark else (K_LIGHT, C_LIGHT)
    B = gaussian_1d(row, SIGMA_BASE_PT * px_per_pt)
    D = gaussian_1d(row, SIGMA_DETAIL_PX * px_per_pt / 3.0)
    return [tone(b + k * (d - b) + c * abs(d - b), dark) for b, d in zip(B, D)]


def stripes(lo, hi, period, n=1200):
    return [hi if (i // (period // 2)) % 2 else lo for i in range(n)]


def plateaus(out, period, n=1200):
    """Mean of the low and high stripe centres in the middle third, away from the ends' replication."""
    lo, hi = [], []
    h = period // 2
    for i in range(n // 3, 2 * n // 3):
        if i % h == h // 2:
            (hi if (i // h) % 2 else lo).append(out[i])
    return sum(lo) / len(lo), sum(hi) / len(hi)


if __name__ == '__main__':
    for dark in (True, False):
        app = 'dark' if dark else 'light'
        print(app, 'flat', ' '.join(f'{v}:{tone(v, dark):.1f}' for v in GREY_IN))
        for lo, hi in ((0, 255), (64, 192), (0, 64)):
            for p in (4, 8, 16, 64):
                a, b = plateaus(predict_1d(stripes(lo, hi, p), dark), p)
                print(f'{app} stripes {lo}-{hi} period {p}: low {a:.1f} high {b:.1f}')
        for rgb in ((255, 0, 0), (0, 255, 0), (0, 0, 255), (255, 0, 255), (128, 128, 128)):
            print(f'{app} colour {rgb}: ' + ' '.join(f'{v:.1f}' for v in colour(rgb, dark)))

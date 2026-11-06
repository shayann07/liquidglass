"""The rim's chromatic fringes, iOS against our replay of the same input, per frame and per touch (#73).

Two identical copies: tools/measure_rim_fringe.py (git) and .local/sxs/fringe.py (private). Self-contained:
the reference recordings, the inferred input scripts and the replays are private and stay under the
reference directory (default .local/sxs); pass --ref to point elsewhere. Run from the repo root:

    python tools/measure_rim_fringe.py <name> <ours-subdir> [<name> <ours-subdir> ...] [--thr 40] [--csv]

<name> is one of docs1, docs2, IMG_6756, JGEY2190, LOLL8185; <ours-subdir> is a replay's frame folder
under <ref>/ref/<name>/ (f00000.png at 60 frames per scripted second, from rrw.sh / :desktop:phoneReplay).

Decode: the iOS recordings are HDR10 (PQ, BT.2020); frames are converted to display sRGB with the same
zscale chain as .local/sxs/hdr.py (research/README.md II.4), every source frame kept (VFR), and each is
paired with our frame at round(t x 60).

Region: rows 430-660, x 0-915 of the bottom 702 rows (the tab bar and the selector's protrusion above and
below it, not the search button). Per pixel, chroma C = max(R,G,B) - min(R,G,B) and hue H in degrees.
The Phone scene's only coloured content is the selected ink (system blue, hue 180-240 in both sources);
a rainbow fringe shows the other hues. A pixel is a *fringe pixel* when C > thr (40 levels) and H lies
outside 180-240 and outside every hue the page above the bar (rows 0-420, either source) shows in that
frame with chroma > thr (24 bins of 15 deg, >= 30 px, widened one bin), and outside hues the bar region
shows at rest in that recording (static_hues: IMG_6756's red missed-call badge): content is not fringe.
Per frame:
  n      fringe pixels (extent)
  E      sum of their chroma / 1000 (extent x strength)
  C      their mean chroma, levels (strength)
  warm   share of fringe pixels with H < 90 or H > 300 (red/orange/yellow/magenta)
  width  area-weighted median over fringe components (>= 4 px) of 2 x the largest distance to the
         component's edge, px (how thick a fringe stroke is)
  rb     R-B channel offset, px: at up to 400 strong edges (|grad G| > 30) in the region, the shift that
         best aligns the R and B derivative profiles (+-6 px, parabolic sub-pixel) along the edge's
         stronger axis; the frame's p90 of |shift|
Timing per touch (the script's down / up: the inferred input both sources received): a frame is
*fringed* when n exceeds that source's rest p99 + 50 px (rest = frames more than 0.6 s from any touch).
Window 0.2 s before down to 0.8 s after lift (or the next down). onset = first fringed frame, peak = max E,
fade = last fringed frame; onset and peak relative to down, peak and fade relative to lift.
"""
import sys, os, csv, re, argparse, subprocess
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'reference-replay'))
import replay_meta

ALLOW_SCRIPT_MISMATCH = False
CONTENT_SPLIT = False
LENS_HALF = 165  # px: half the held lens's width (about 330 px in scene pixels), for --content-split

VIDEO = {'docs1': 'docs/1.MP4', 'docs2': 'docs/2.MP4',
         'IMG_6756': 'research/datasets/ios27-phone/bubble/IMG_6756.MP4',
         'JGEY2190': 'research/datasets/ios27-phone/bubble/JGEY2190.MP4',
         'LOLL8185': 'research/datasets/ios27-phone/LOLL8185.MP4'}
SDR = ("zscale=t=linear:npl=100,format=gbrpf32le,"
       "zscale=p=bt709:t=iec61966-2-1:m=bt709:r=full,format=rgb24")
Y0, Y1, X1 = 430, 660, 915
REF = '.local/sxs'


def size(path):
    w, h = subprocess.run(['ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_entries',
                           'stream=width,height', '-of', 'csv=p=0', path],
                          capture_output=True, text=True).stdout.strip().split(',')[:2]
    return int(w), int(h)


def frames(path, t0, dur, crop):
    """(pts seconds, display-sRGB frame) for every source frame, cropped (w, h, x, y) before conversion."""
    vf = f"crop={crop[0]}:{crop[1]}:{crop[2]}:{crop[3]}," + SDR
    w, h = crop[0], crop[1]
    args = ['-ss', str(t0)] + (['-t', str(dur)] if dur else [])
    log = subprocess.run(['ffmpeg', '-v', 'info'] + args + ['-i', path, '-fps_mode', 'passthrough',
                          '-vf', 'showinfo', '-f', 'null', '-'], capture_output=True, text=True).stderr
    pts = [t0 + float(x) for x in re.findall(r'pts_time:([\d.]+)', log)]
    p = subprocess.Popen(['ffmpeg', '-v', 'error'] + args + ['-i', path, '-fps_mode', 'passthrough',
                          '-vf', vf, '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-'], stdout=subprocess.PIPE)
    n = w * h * 3
    for t in pts:
        b = p.stdout.read(n)
        if len(b) < n:
            break
        yield t, np.frombuffer(b, np.uint8).reshape(h, w, 3)
    p.stdout.close()
    p.wait()


def chroma420(x):
    """Our frame through the recordings' chroma sampling (BT.709 Y'CbCr, Cb/Cr averaged over 2x2 and
    repeated): a control for colour the codec alone would add at the ink's moving edges."""
    x = x.astype(np.float32)
    y = .2126 * x[..., 0] + .7152 * x[..., 1] + .0722 * x[..., 2]
    cb = (x[..., 2] - y) / 1.8556; cr = (x[..., 0] - y) / 1.5748
    h, w = y.shape; h2, w2 = h // 2 * 2, w // 2 * 2
    def sub(c):
        c = c.copy(); m = c[:h2, :w2].reshape(h2 // 2, 2, w2 // 2, 2).mean(axis=(1, 3))
        c[:h2, :w2] = np.repeat(np.repeat(m, 2, 0), 2, 1); return c
    cb, cr = sub(cb), sub(cr)
    r = y + 1.5748 * cr; b = y + 1.8556 * cb; g = (y - .2126 * r - .0722 * b) / .7152
    return np.clip(np.stack([r, g, b], -1) + .5, 0, 255).astype(np.uint8)


def script_path(name):
    return os.path.join(REF, {'docs1': 'v1-script.txt', 'docs2': 'v2-script.txt'}.get(name, os.path.join('ref', name, 'script3.txt')))


def script(name):
    p = script_path(name)
    downs, ups, end = [], [], None
    for l in open(p):
        w = l.split()
        if len(w) >= 2 and w[1] == 'down': downs.append(int(w[0]) / 1000)
        if len(w) >= 2 and w[1] == 'up': ups.append(int(w[0]) / 1000)
        if w and w[0] == 'end': end = int(w[1]) / 1000
    return list(zip(downs, ups)), end


def hue_chroma(x):
    x = x.astype(np.float32)
    r, g, b = x[..., 0], x[..., 1], x[..., 2]
    mx = x.max(2); mn = x.min(2); c = mx - mn
    h = np.zeros_like(c); m = c > 0
    i = m & (mx == r); h[i] = ((g - b)[i] / c[i]) % 6
    i = m & (mx == g) & (mx != r); h[i] = (b - r)[i] / c[i] + 2
    i = m & (mx == b) & (mx != r) & (mx != g); h[i] = (r - g)[i] / c[i] + 4
    return h * 60, c


def best_shift(pr, pb):
    dr = np.diff(pr); db = np.diff(pb)
    dr = dr - dr.mean(); db = db - db.mean()
    nr, nb = np.linalg.norm(dr), np.linalg.norm(db)
    if nr < 1e-6 or nb < 1e-6: return None
    k = 6; n = len(dr); cs = []
    for s in range(-k, k + 1):
        a = dr[max(0, s):n + min(0, s)]; bb = db[max(0, -s):n - max(0, s)]
        cs.append(float(np.dot(a, bb)) / (nr * nb))
    cs = np.array(cs); i = int(cs.argmax())
    if cs[i] < .5: return None
    f = 0.0
    if 0 < i < 2 * k:
        d = cs[i - 1] - 2 * cs[i] + cs[i + 1]
        if d < 0: f = .5 * (cs[i - 1] - cs[i + 1]) / d
    return abs(i - k + f)


def rb_offset(x, rng, xr=None, inside=True):
    """p90 of the R-B shift at strong edges; `xr` = (x0, x1) keeps only edges inside (or outside) that column range."""
    x = x.astype(np.float32); g = x[..., 1]
    gx = np.abs(np.diff(g, axis=1))[:-1]; gy = np.abs(np.diff(g, axis=0))[:, :-1]
    gm = np.maximum(gx, gy); gm[:12] = 0; gm[-12:] = 0; gm[:, :12] = 0; gm[:, -12:] = 0
    ys, xs = np.nonzero(gm > 30)
    if xr is not None:
        keep = (xs >= xr[0]) & (xs <= xr[1]) if inside else ~((xs >= xr[0]) & (xs <= xr[1]))
        ys, xs = ys[keep], xs[keep]
    if len(ys) == 0: return np.nan
    if len(ys) > 400:
        sel = rng.choice(len(ys), 400, replace=False); ys, xs = ys[sel], xs[sel]
    out = []
    for y, xx in zip(ys, xs):
        if gx[y, xx] >= gy[y, xx]: pr, pb = x[y, xx - 12:xx + 13, 0], x[y, xx - 12:xx + 13, 2]
        else: pr, pb = x[y - 12:y + 13, xx, 0], x[y - 12:y + 13, xx, 2]
        if pr.std() < 8 or pb.std() < 8: continue
        s = best_shift(pr, pb)
        if s is not None: out.append(s)
    return float(np.percentile(out, 90)) if len(out) >= 10 else np.nan


def fringe_mask(x, thr):
    h, c = hue_chroma(x)
    return (c > thr) & ~((h >= 180) & (h <= 240)), h, c


def content_hues(page, thr):
    """24 hue bins (15 deg) in which the page above the bar (rows 0-420) has coloured content in this frame
    (>= 30 px of chroma > thr outside the ink hues), widened by one bin either side."""
    m, h, c = fringe_mask(page, thr)
    hist, _ = np.histogram(h[m], bins=24, range=(0, 360))
    b = hist >= 30
    return b | np.roll(b, 1) | np.roll(b, -1)


def static_hues(name, eps, end, crop, thr):
    """Hue bins the recording shows in the bar region while nothing moves (a notification badge, say):
    up to 12 rest instants (more than 0.6 s from any touch), bins whose median count of off-ink chromatic
    pixels is >= 30, widened one bin either side. Content, not fringe, in both sources."""
    cand = [t for t in np.arange(0.3, end - 0.3, 0.5) if all(t < d - .6 or t > u + .6 for d, u in eps)]
    cand = cand[::max(1, len(cand) // 12)][:12]
    hs = []
    for t in cand:
        for _, a in frames(VIDEO[name], float(t), 0.03, crop):
            m, h, _ = fringe_mask(a[Y0:Y1, :X1], thr)
            hs.append(np.histogram(h[m], bins=24, range=(0, 360))[0]); break
    if not hs: return np.zeros(24, bool)
    b = np.median(np.array(hs), 0) >= 30
    return b | np.roll(b, 1) | np.roll(b, -1)


def measure(x, thr, rng, excl=None, lens=None):
    m, h, c = fringe_mask(x, thr)
    if excl is not None and excl.any():
        m &= ~excl[np.clip((h / 15).astype(int), 0, 23)]
    n = int(m.sum())
    extra = dict(rb_in=np.nan, rb_out=np.nan) if CONTENT_SPLIT else {}  # every row carries the keys, lens or not
    if lens is not None:
        # Content split (#101 item 88): the same R-B shift at the page's own strong edges, under the lens and away from it.
        extra = dict(rb_in=rb_offset(x, rng, lens, True), rb_out=rb_offset(x, rng, lens, False))
    if n == 0: return dict(n=0, E=0.0, C=0.0, warm=np.nan, width=0.0, rb=rb_offset(x, rng), **extra)
    cc = c[m]; hh = h[m]
    lab, k = ndi.label(m)
    widths = np.array([]); areas = np.array([])
    if k:
        dt = ndi.distance_transform_edt(m)
        idx = np.arange(1, k + 1)
        mx = np.asarray(ndi.maximum(dt, lab, index=idx)); ar = np.asarray(ndi.sum(m, lab, index=idx))
        keep = ar >= 4; widths, areas = 2 * mx[keep], ar[keep]
    if len(widths):
        o = np.argsort(widths); cw = np.cumsum(areas[o]); width = float(widths[o][np.searchsorted(cw, cw[-1] / 2)])
    else: width = 0.0
    return dict(n=n, E=float(cc.sum()) / 1000, C=float(cc.mean()), warm=float(((hh < 90) | (hh > 300)).mean()),
                width=width, rb=rb_offset(x, rng), **extra)


def track(name):
    p = os.path.join(REF, 'ref', name, 'track.csv')
    if not os.path.exists(p): return None
    return np.array([(float(x['t']), float(x['cx']), int(x.get('phone') or 1)) for x in csv.DictReader(open(p))])


def series(name, sub, thr, ours420=False):
    # Touch times come from this recording's script: a replay rendered from another one would be misaligned (#101, item 98).
    why = replay_meta.mismatch(os.path.join(REF, 'ref', name, sub), script_path(name))
    if why and not ALLOW_SCRIPT_MISMATCH:
        sys.exit(why + ' (--allow-script-mismatch scores it anyway)')
    eps, end = script(name)
    path = VIDEO[name]; _, hh = size(path)
    rng_i, rng_o = np.random.default_rng(1), np.random.default_rng(1)
    tr = track(name)
    static = static_hues(name, eps, end, (1170, 702, 0, hh - 702), thr)
    print(f"{name}: static content hue bins (15 deg) excluded: {[int(i) * 15 for i in np.nonzero(static)[0]]}", flush=True)
    rows = []
    for t, a in frames(path, 0.0, end, (1170, 702, 0, hh - 702)):
        p = os.path.join(REF, 'ref', name, sub, f'f{round(t * 60):05d}.png')
        if not os.path.exists(p): continue
        v = np.nan
        if tr is not None:
            j = int(np.abs(tr[:, 0] - t).argmin())
            if tr[j, 2] == 0: continue  # not the Phone scene
            # iOS's selected-ink centroid speed over +-50 ms (px/s), for binning only.
            k0 = np.searchsorted(tr[:, 0], t - .05); k1 = min(np.searchsorted(tr[:, 0], t + .05), len(tr) - 1)
            ok = tr[k0:k1 + 1]; ok = ok[ok[:, 1] > 0]
            if len(ok) >= 2: v = abs(ok[-1, 1] - ok[0, 1]) / max(ok[-1, 0] - ok[0, 0], 1e-3)
        b = np.array(Image.open(p).convert('RGB'))
        if ours420: b = chroma420(b)
        # Hues the page itself shows in this frame (either source) are not counted as fringe: red missed-call
        # names, coloured avatars magnified under a lens would otherwise read as dispersion.
        excl = content_hues(a[:Y0 - 10], thr) | content_hues(b[:Y0 - 10], thr) | static
        lens = None
        if CONTENT_SPLIT and tr is not None and tr[j, 1] > 0: lens = (int(tr[j, 1] - LENS_HALF), int(tr[j, 1] + LENS_HALF))
        mi = measure(a[Y0:Y1, :X1], thr, rng_i, excl, lens); mo = measure(b[Y0:Y1, :X1], thr, rng_o, excl, lens)
        rows.append(dict(t=t, v=v, excl=int(excl.sum()), **{'i_' + k: x for k, x in mi.items()}, **{'o_' + k: x for k, x in mo.items()}))
    return rows, eps


def phases(rows, eps):
    t = np.array([r['t'] for r in rows])
    dt = np.median(np.diff(t)) if len(t) > 1 else 1 / 60
    near = np.zeros(len(t), bool)
    for d, u in eps: near |= (t > d - .6) & (t < u + .6)
    base = {}
    for s in ('i', 'o'):
        n = np.array([r[s + '_n'] for r in rows], float)
        rest = n[~near] if (~near).sum() > 20 else n[n <= np.percentile(n, 20)]
        base[s + '_rest99'] = float(np.percentile(rest, 99)); base[s + '_thr'] = base[s + '_rest99'] + 50
    per = []
    for k, (d, u) in enumerate(eps):
        nxt = eps[k + 1][0] - .05 if k + 1 < len(eps) else 1e9
        w = (t >= d - .2) & (t <= min(u + .8, nxt))
        if w.sum() < 5: continue
        e = {'down': d, 'up': u}
        ix = np.nonzero(w)[0]; tt = t[w]
        for s in ('i', 'o'):
            n = np.array([rows[i][s + '_n'] for i in ix], float)
            E = np.array([rows[i][s + '_E'] for i in ix], float)
            above = n > base[s + '_thr']
            e[s + '_Esum'] = float(E.sum() * dt); e[s + '_Epk'] = float(E.max())
            e[s + '_on'] = None
            if above.any():
                i0 = int(np.argmax(above)); ip = int(E.argmax()); i1 = len(above) - 1 - int(np.argmax(above[::-1]))
                e.update({s + '_on': tt[i0] - d, s + '_pk': tt[ip] - d, s + '_pkl': tt[ip] - u,
                          s + '_off': tt[i1] - u, s + '_dur': tt[i1] - tt[i0]})
        per.append(e)
    return base, per


def q(a, f='+.3f'):
    a = np.asarray([x for x in a if x is not None and np.isfinite(x)], float)
    if len(a) == 0: return 'n=0'
    return f"{np.median(a):{f}} (IQR {np.percentile(a, 25):{f}}..{np.percentile(a, 75):{f}}, n={len(a)})"


def report(name, rows, eps):
    base, per = phases(rows, eps)
    print(f"\n== {name}: {len(rows)} paired frames, {len(eps)} touches; rest n p99 iOS {base['i_rest99']:.0f} ours {base['o_rest99']:.0f}")
    for s, lab in (('i', 'iOS '), ('o', 'ours')):
        a = [r for r in rows if r[s + '_n'] > base[s + '_thr']]
        g = lambda k: [r[s + k] for r in a]
        print(f"  {lab} fringed frames {len(a):4d} ({100 * len(a) / len(rows):4.1f}%)  in them: n {q(g('_n'), '.0f')}"
              f"  chroma {q(g('_C'), '.1f')}  warm {q(g('_warm'), '.2f')}  width {q(g('_width'), '.1f')} px"
              f"  R-B p90 {q(g('_rb'), '.2f')} px | all frames R-B {q([r[s + '_rb'] for r in rows], '.2f')}")
    if rows and 'i_rb_in' in rows[0]:
        # Frames where the lens is up (iOS fringed beyond its rest level), split by the ink's speed.
        for lo, hi, lab2 in ((0, 50, 'lens still'), (50, 1e9, 'lens moving')):
            sel = [r for r in rows if np.isfinite(r['v']) and lo <= r['v'] < hi and r['i_n'] > base['i_thr']]
            if len(sel) < 5: continue
            for s, lab in (('i', 'iOS '), ('o', 'ours')):
                print(f"  {lab} content split R-B p90, {lab2} ({len(sel)} frames): under the lens {q([r[s + '_rb_in'] for r in sel], '.2f')} px, "
                      f"away from it {q([r[s + '_rb_out'] for r in sel], '.2f')} px")
    v = np.array([r['v'] for r in rows], float)
    if np.isfinite(v).any():
        for lo, hi in ((0, 50), (50, 400), (400, 1500), (1500, 1e9)):
            sel = [r for r, x in zip(rows, v) if lo <= x < hi]
            if len(sel) < 5: continue
            print(f"  iOS ink speed {lo:4.0f}-{hi:<5.0f} px/s ({len(sel):4d} frames): mean E iOS {np.mean([r['i_E'] for r in sel]):6.2f} ours {np.mean([r['o_E'] for r in sel]):6.2f}"
                  f" | mean n iOS {np.mean([r['i_n'] for r in sel]):6.0f} ours {np.mean([r['o_n'] for r in sel]):6.0f}")
    both = [e for e in per if e['i_on'] is not None and e['o_on'] is not None]
    ni = sum(e['i_on'] is not None for e in per); no = sum(e['o_on'] is not None for e in per)
    print(f"  touches fringed: iOS {ni}/{len(per)}, ours {no}/{len(per)}, both {len(both)}")
    for s, lab in (('i', 'iOS '), ('o', 'ours')):
        f = [e for e in per if e[s + '_on'] is not None]
        print(f"  {lab} onset-down {q([e[s + '_on'] for e in f])}  peak-down {q([e[s + '_pk'] for e in f])}"
              f"  peak-lift {q([e[s + '_pkl'] for e in f])}  fade-lift {q([e[s + '_off'] for e in f])}")
    if both:
        print(f"  ours-iOS paired: onset {q([e['o_on'] - e['i_on'] for e in both])}  peak {q([e['o_pk'] - e['i_pk'] for e in both])}"
              f"  fade {q([e['o_off'] - e['i_off'] for e in both])}  duration {q([e['o_dur'] - e['i_dur'] for e in both])}")
    r = [e['o_Esum'] / e['i_Esum'] for e in per if e['i_Esum'] > .05]
    print(f"  integrated fringe ours/iOS per touch {q(r, '.2f')}; peak E ours/iOS {q([e['o_Epk'] / e['i_Epk'] for e in per if e['i_Epk'] > .5], '.2f')};"
          f" totals iOS {sum(e['i_Esum'] for e in per):.2f} ours {sum(e['o_Esum'] for e in per):.2f}")
    return base, per


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('pairs', nargs='+'); ap.add_argument('--thr', type=float, default=40)
    ap.add_argument('--ref', default=REF); ap.add_argument('--csv', action='store_true')
    ap.add_argument('--ours420', action='store_true', help='control: pass our frames through 4:2:0 chroma sampling')
    ap.add_argument('--content-split', action='store_true', help='also report the R-B shift at page edges under and away from the lens (#101 item 88); needs track.csv')
    ap.add_argument('--allow-script-mismatch', action='store_true', help='score a replay rendered from a different script than the recording uses')
    a = ap.parse_args(); REF = a.ref; ALLOW_SCRIPT_MISMATCH = a.allow_script_mismatch; CONTENT_SPLIT = a.content_split
    for name, sub in zip(a.pairs[::2], a.pairs[1::2]):
        rows, eps = series(name, sub, a.thr, a.ours420)
        if a.csv:
            with open(os.path.join(REF, 'ref', name, f'fringe-{sub}-{a.thr:g}{"-420" if a.ours420 else ""}.csv'), 'w', newline='') as f:
                wr = csv.DictWriter(f, fieldnames=list(rows[0])); wr.writeheader(); wr.writerows(rows)
        report(name, rows, eps)
        sys.stdout.flush()

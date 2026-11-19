"""Formation continuity across the 0.5 threshold (#103, plans/103.md section 3).

Measures whether the selector's fringe or appearance jumps when `formation`
crosses 0.5 in `trace.csv`, on both taps (`docs1`) and holds (`IMG_6756`),
against each source's own neighbouring non-crossing frame pairs and against iOS.

For each crossing pair (f, f+1) where formation crosses 0.5 and neighbouring
non-crossing pairs (f-2, f-1) and (f+1, f+2):
  - dE: |E[second] - E[first]| using `tools/measure_rim_fringe.py`'s exact
    per-frame fringe energy E (Y0=430, Y1=660, X1=915, thr=40, excl from both
    sources' page content above the bar plus static rest hues), evaluated both
    (a) on the paired iOS-VFR frame sequence (`measure_rim_fringe --csv` rows)
    and (b) directly on exact 60 fps replay frame pairs for ours (with
    straddling iOS VFR frames for iOS).
  - dI: motion-compensated mean absolute luma change (0.299 R + 0.587 G + 0.114 B)
    inside the selector bounding box (rows 430..660, x in [center - w/2, center + w/2]
    clipped to [0, 915]), after shifting the first frame horizontally by the
    selector's own motion via subpixel linear interpolation (`np.interp`).
    In `PhoneScene` (1170x702 at 3 px/dp), `GlassTabBar` sits at
    `PhoneGeometry.barLeft = 21.dp = 63 px`, so PNG scene center is
    `selectorX + 63.0` (`--bar-left`, default 63.0).

All iOS HDR10 frames are decoded exclusively via `.local/sxs/hdr.py` (`frames()` / `size()`).
Run from the repository root with `python -B` (`PYTHONDONTWRITEBYTECODE=1`, `PYTHONUTF8=1`):

    python -B tools/measure_formation_continuity.py --json out.json
"""
import sys
sys.dont_write_bytecode = True

import argparse
import csv
import importlib.util
import json
import math
import os
from collections import defaultdict

import numpy as np
from PIL import Image


DEFAULT_CASES = [
    'docs1:m1115',
    'docs1:p20:m1115',
    'IMG_6756:m1115',
    'IMG_6756:m1101',
    'IMG_6756:p20',
]


def _load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, os.path.abspath(path))
    if spec is None or spec.loader is None:
        raise ImportError(f'Cannot load {name} from {path}')
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def luma64(rgb):
    a = np.asarray(rgb, dtype=np.float64)
    return 0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]


def shift_horizontal(luma_band, dx):
    if abs(dx) < 1e-12:
        return luma_band
    w = luma_band.shape[1]
    xs = np.arange(w, dtype=np.float64)
    src = xs - dx
    return np.vstack([np.interp(src, xs, row) for row in luma_band])


def motion_dI(luma_a, luma_b, xa, xb, wb, x_limit):
    x0 = max(0, int(round(xb - wb / 2.0)))
    x1 = min(x_limit, int(round(xb + wb / 2.0)))
    if x1 <= x0:
        return float('nan'), float('nan')
    dx = xb - xa
    shifted_a = shift_horizontal(luma_a, dx)
    di_sh = float(np.abs(luma_b[:, x0:x1] - shifted_a[:, x0:x1]).mean())
    di_raw = float(np.abs(luma_b[:, x0:x1] - luma_a[:, x0:x1]).mean())
    return di_sh, di_raw


def fringe_energy(rgb_band, excl, mrf, thr):
    m, h, c = mrf.fringe_mask(rgb_band, thr)
    if excl is not None and excl.any():
        m &= ~excl[np.clip((h / 15.0).astype(int), 0, 23)]
    n = int(m.sum())
    if n == 0:
        return 0, 0.0
    return n, float(c[m].sum()) / 1000.0


def static_hues_hdr(hdr, mrf, name, eps, end, crop, thr):
    """Exact `measure_rim_fringe.static_hues` logic, decoded strictly through `hdr.frames()`."""
    cand = [t for t in np.arange(0.3, end - 0.3, 0.5)
            if all(t < d - 0.6 or t > u + 0.6 for d, u in eps)]
    cand = cand[::max(1, len(cand) // 12)][:12]
    hs = []
    vid = mrf.VIDEO[name]
    for t in cand:
        for _, a in hdr.frames(vid, float(t), 0.03, crop):
            m, h, _ = mrf.fringe_mask(a[mrf.Y0:mrf.Y1, :mrf.X1], thr)
            hs.append(np.histogram(h[m], bins=24, range=(0, 360))[0])
            break
    if not hs:
        return np.zeros(24, dtype=bool)
    b = np.median(np.array(hs), 0) >= 30
    return b | np.roll(b, 1) | np.roll(b, -1)


def load_trace(ref, name, sub):
    p = os.path.join(ref, 'ref', name, sub, 'trace.csv')
    with open(p, encoding='utf-8', newline='') as f:
        return list(csv.DictReader(f))


def crossing_and_neighbour_pairs(trace_rows, limit_len=None):
    n = len(trace_rows) if limit_len is None else min(len(trace_rows), limit_len)
    f = [float(r['formation']) for r in trace_rows[:n]]
    cross_starts = [
        i for i in range(n - 1)
        if (f[i] < 0.5 <= f[i + 1]) or (f[i] >= 0.5 > f[i + 1])
    ]
    cross_set = set(cross_starts)
    neigh = []
    seen = set()
    for c in cross_starts:
        for a, b in ((c - 2, c - 1), (c + 1, c + 2)):
            if 0 <= a and b < n and a not in cross_set and (a, b) not in seen:
                neigh.append((a, b))
                seen.add((a, b))
    return [(c, c + 1) for c in cross_starts], neigh


def interpolate_track_cx(all_cx, all_ph):
    cx = all_cx.copy()
    n = len(cx)
    for i in range(n):
        if all_ph[i] == 1 and cx[i] <= 0:
            # Linear interpolation across single-frame (or short) dropouts inside phone==1
            left = i - 1
            while left >= 0 and (all_ph[left] != 1 or cx[left] <= 0):
                left -= 1
            right = i + 1
            while right < n and (all_ph[right] != 1 or cx[right] <= 0):
                right += 1
            if left >= 0 and right < n and (right - left) <= 3:
                alpha = (i - left) / float(right - left)
                cx[i] = (1.0 - alpha) * cx[left] + alpha * cx[right]
    return cx


def map_crossings_to_vfr(vfr_times, cross_pairs_60):
    """Find VFR row pair (k, k+1) straddling (or closest to) (f + 0.5) / 60.0 and neighbours."""
    n = len(vfr_times)
    if n < 2:
        return [], []
    cross_vfr = []
    for f, _ in cross_pairs_60:
        t_mid = (f + 0.5) / 60.0
        if t_mid < vfr_times[0]:
            k = 0
        elif t_mid >= vfr_times[-1]:
            k = n - 2
        else:
            k = int(np.searchsorted(vfr_times, t_mid, side='right')) - 1
            k = max(0, min(k, n - 2))
        cross_vfr.append((k, k + 1))

    cross_k_set = {k for k, _ in cross_vfr}
    neigh_vfr = []
    seen = set()
    for k, _ in cross_vfr:
        for u, v in ((k - 2, k - 1), (k + 1, k + 2)):
            if 0 <= u and v < n and u not in cross_k_set and (u, v) not in seen:
                neigh_vfr.append((u, v))
                seen.add((u, v))
    return cross_vfr, neigh_vfr


def quantiles(vals):
    arr = np.asarray([x for x in vals if x is not None and math.isfinite(x)], dtype=np.float64)
    if len(arr) == 0:
        return dict(n=0, p50=None, p90=None)
    return dict(n=int(len(arr)), p50=float(np.median(arr)), p90=float(np.percentile(arr, 90)))


def safe_ratio(num, den):
    if num is None or den is None:
        return None
    if den == 0.0:
        return 0.0 if num == 0.0 else float('inf')
    return float(num / den)


def summarize_pair_metric(cross_vals, neigh_vals):
    cs = quantiles(cross_vals)
    ns = quantiles(neigh_vals)
    return dict(
        cross=cs,
        neigh=ns,
        ratio_p50=safe_ratio(cs['p50'], ns['p50']),
        ratio_p90=safe_ratio(cs['p90'], ns['p90']),
    )


def judge_metric(ours_summary, ios_summary):
    """Pass rule: ours <= 1.5x at median AND <= 2.0x at p90, OR <= iOS's same ratio."""
    op50 = ours_summary['ratio_p50']
    op90 = ours_summary['ratio_p90']
    ip50 = ios_summary['ratio_p50']
    ip90 = ios_summary['ratio_p90']

    def ok(ours_r, ios_r, limit):
        if ours_r is None:
            return False
        if not math.isfinite(ours_r):
            return False
        if ours_r <= limit:
            return True
        if ios_r is not None and math.isfinite(ios_r) and ours_r <= ios_r:
            return True
        return False

    pass_p50 = ok(op50, ip50, 1.5)
    pass_p90 = ok(op90, ip90, 2.0)
    return dict(pass_p50=pass_p50, pass_p90=pass_p90, passed=(pass_p50 and pass_p90))


def parse_case_spec(spec):
    parts = spec.split(':')
    if len(parts) == 2:
        return dict(name=parts[0], sub=parts[1], ref_cross_sub=None)
    if len(parts) == 3:
        return dict(name=parts[0], sub=parts[1], ref_cross_sub=parts[2] or None)
    raise ValueError(f'Invalid case spec: {spec!r}')


def process_recording(name, case_specs, ref, hdr, mrf, thr, bar_left, csv_dir):
    """Decode `name` once via `hdr.frames()` and compute all metrics for its cases."""
    eps, end = mrf.script(name)
    vid = mrf.VIDEO[name]
    _, hh = hdr.size(vid)
    crop = (1170, 702, 0, hh - 702)
    static = static_hues_hdr(hdr, mrf, name, eps, end, crop, thr)
    tr = mrf.track(name)
    if tr is None:
        raise FileNotFoundError(f'Missing track.csv for {name}')

    # Pre-load traces and crossing windows so we know which iOS frames need luma kept
    case_data = {}
    all_cross_times = []
    for c in case_specs:
        sub = c['sub']
        ref_sub = c['ref_cross_sub']
        trace_rows = load_trace(ref, name, sub)
        ref_rows = load_trace(ref, name, ref_sub) if ref_sub else trace_rows
        cross_60, neigh_60 = crossing_and_neighbour_pairs(ref_rows, limit_len=len(trace_rows))
        for a, b in cross_60:
            all_cross_times.append((a + 0.5) / 60.0)
        case_data[sub] = dict(
            spec=c,
            trace=trace_rows,
            ref_trace=ref_rows,
            cross_60=cross_60,
            neigh_60=neigh_60,
            vfr_rows=[],
        )

    # Stream iOS video ONCE via hdr.frames()
    vfr_t = []
    vfr_cx = []
    vfr_ph = []
    vfr_top_hues = []
    vfr_luma_win = {}  # k -> (230, 1170) float64, only near crossings (+-0.35s)

    for k, (t, a) in enumerate(hdr.frames(vid, 0.0, end, crop)):
        j = int(np.abs(tr[:, 0] - t).argmin())
        ph = int(tr[j, 2])
        cx = float(tr[j, 1])
        vfr_t.append(float(t))
        vfr_cx.append(cx)
        vfr_ph.append(ph)

        if ph != 1:
            vfr_top_hues.append(None)
            continue

        top_hue = mrf.content_hues(a[:mrf.Y0 - 10], thr)
        vfr_top_hues.append(top_hue)

        # Keep full-width bottom band luma around crossings for motion-compensated dI
        if any(abs(t - tc) <= 0.50 for tc in all_cross_times):
            vfr_luma_win[k] = luma64(a[mrf.Y0:mrf.Y1, :])

        # Compute paired VFR E rows for each subdir (only once per PNG frame)
        fidx = round(float(t) * 60)
        a_band = a[mrf.Y0:mrf.Y1, :mrf.X1]

        # Speed v for CSV compatibility with measure_rim_fringe
        k0 = int(np.searchsorted(tr[:, 0], t - 0.05))
        k1 = min(int(np.searchsorted(tr[:, 0], t + 0.05)), len(tr) - 1)
        ok_tr = tr[k0:k1 + 1]
        ok_tr = ok_tr[ok_tr[:, 1] > 0]
        speed_v = (
            abs(float(ok_tr[-1, 1] - ok_tr[0, 1])) / max(float(ok_tr[-1, 0] - ok_tr[0, 0]), 1e-3)
            if len(ok_tr) >= 2 else float('nan')
        )

        for sub, cd in case_data.items():
            png_path = os.path.join(ref, 'ref', name, sub, f'f{fidx:05d}.png')
            if not os.path.exists(png_path):
                continue
            b = np.array(Image.open(png_path).convert('RGB'))
            excl = top_hue | mrf.content_hues(b[:mrf.Y0 - 10], thr) | static
            i_n, i_E = fringe_energy(a_band, excl, mrf, thr)
            o_n, o_E = fringe_energy(b[mrf.Y0:mrf.Y1, :mrf.X1], excl, mrf, thr)
            cd['vfr_rows'].append(dict(
                k=k,
                t=float(t),
                fidx=fidx,
                v=speed_v,
                excl=int(excl.sum()),
                i_n=i_n,
                i_E=i_E,
                o_n=o_n,
                o_E=o_E,
            ))

    vfr_t_arr = np.asarray(vfr_t, dtype=np.float64)
    vfr_ph_arr = np.asarray(vfr_ph, dtype=int)
    vfr_cx_interp = interpolate_track_cx(np.asarray(vfr_cx, dtype=np.float64), vfr_ph_arr)

    # Helper to find nearest phone==1 VFR frame index for 60 fps frame f
    phone_ks = np.where(vfr_ph_arr == 1)[0]
    phone_ts = vfr_t_arr[phone_ks]

    results = []
    for sub, cd in case_data.items():
        vrows = cd['vfr_rows']
        trace_rows = cd['trace']
        cross_60 = cd['cross_60']
        neigh_60 = cd['neigh_60']

        if csv_dir:
            os.makedirs(csv_dir, exist_ok=True)
            csv_path = os.path.join(csv_dir, f'fringe-{name}-{sub}-{thr:g}.csv')
            cols = ['t', 'v', 'excl', 'i_n', 'i_E', 'o_n', 'o_E']
            with open(csv_path, 'w', encoding='utf-8', newline='') as fh:
                wr = csv.DictWriter(fh, fieldnames=cols)
                wr.writeheader()
                for r in vrows:
                    wr.writerow({c: r[c] for c in cols})

        # Map 60fps crossings -> VFR sequence rows
        vrow_times = np.asarray([r['t'] for r in vrows], dtype=np.float64)
        vfr_cross_pos, vfr_neigh_pos = map_crossings_to_vfr(vrow_times, cross_60)

        # Load all needed PNGs for ours once (for 60fps pairs and VFR dI pairs)
        needed_60 = set()
        for a, b in cross_60 + neigh_60:
            needed_60.add(a); needed_60.add(b)
        for u, v in vfr_cross_pos + vfr_neigh_pos:
            needed_60.add(vrows[u]['fidx']); needed_60.add(vrows[v]['fidx'])

        png_info = {}
        for fidx in sorted(needed_60):
            p = os.path.join(ref, 'ref', name, sub, f'f{fidx:05d}.png')
            im = np.array(Image.open(p).convert('RGB'))
            top_b = mrf.content_hues(im[:mrf.Y0 - 10], thr)
            # Compute ours exact-60fps E against nearest phone==1 iOS frame
            j_near = int(phone_ks[int(np.abs(phone_ts - fidx / 60.0).argmin())])
            top_a = vfr_top_hues[j_near]
            excl_60 = top_a | top_b | static
            _, o_E_60 = fringe_energy(im[mrf.Y0:mrf.Y1, :mrf.X1], excl_60, mrf, thr)
            png_info[fidx] = dict(
                E=o_E_60,
                luma=luma64(im[mrf.Y0:mrf.Y1, :]),  # full 1170 width
            )

        def eval_vfr_list(pos_pairs):
            ios_dE, ours_dE, ios_dI, ios_dI_raw, ours_dI, ours_dI_raw = [], [], [], [], [], []
            details = []
            for u, v in pos_pairs:
                ru, rv = vrows[u], vrows[v]
                ku, kv = ru['k'], rv['k']
                fu, fv = ru['fidx'], rv['fidx']
                i_de = abs(rv['i_E'] - ru['i_E'])
                o_de = abs(rv['o_E'] - ru['o_E'])
                ios_dE.append(i_de)
                ours_dE.append(o_de)

                wb = float(trace_rows[min(fv, len(trace_rows) - 1)]['bodyWidth'])
                cx0, cx1 = float(vfr_cx_interp[ku]), float(vfr_cx_interp[kv])
                if cx0 > 0 and cx1 > 0 and ku in vfr_luma_win and kv in vfr_luma_win:
                    i_di, i_di_r = motion_dI(vfr_luma_win[ku], vfr_luma_win[kv], cx0, cx1, wb, mrf.X1)
                else:
                    i_di, i_di_r = float('nan'), float('nan')
                ios_dI.append(i_di)
                ios_dI_raw.append(i_di_r)

                xa = float(trace_rows[min(fu, len(trace_rows) - 1)]['selectorX']) + bar_left
                xb = float(trace_rows[min(fv, len(trace_rows) - 1)]['selectorX']) + bar_left
                o_di, o_di_r = motion_dI(png_info[fu]['luma'], png_info[fv]['luma'], xa, xb, wb, mrf.X1)
                ours_dI.append(o_di)
                ours_dI_raw.append(o_di_r)

                details.append(dict(
                    vfr_idx=(u, v),
                    vfr_t=(ru['t'], rv['t']),
                    fidx=(fu, fv),
                    ios_dE=i_de, ours_dE=o_de,
                    ios_dI=i_di, ios_dI_raw=i_di_r,
                    ours_dI=o_di, ours_dI_raw=o_di_r,
                ))
            return dict(
                ios_dE=ios_dE, ours_dE=ours_dE,
                ios_dI=ios_dI, ios_dI_raw=ios_dI_raw,
                ours_dI=ours_dI, ours_dI_raw=ours_dI_raw,
                details=details,
            )

        def eval_60_list(pairs_60):
            ours_dE, ours_dI, ours_dI_raw, ours_dI_local = [], [], [], []
            details = []
            for a, b in pairs_60:
                o_de = abs(png_info[b]['E'] - png_info[a]['E'])
                ours_dE.append(o_de)
                wb = float(trace_rows[b]['bodyWidth'])
                xa_loc = float(trace_rows[a]['selectorX'])
                xb_loc = float(trace_rows[b]['selectorX'])
                o_di, o_di_r = motion_dI(
                    png_info[a]['luma'], png_info[b]['luma'],
                    xa_loc + bar_left, xb_loc + bar_left, wb, mrf.X1,
                )
                o_di_loc, _ = motion_dI(
                    png_info[a]['luma'], png_info[b]['luma'],
                    xa_loc, xb_loc, wb, mrf.X1,
                )
                ours_dI.append(o_di)
                ours_dI_raw.append(o_di_r)
                ours_dI_local.append(o_di_loc)
                details.append(dict(
                    frames=(a, b),
                    formation=(float(trace_rows[a]['formation']), float(trace_rows[b]['formation'])),
                    selectorX_scene=(xa_loc + bar_left, xb_loc + bar_left),
                    bodyWidth=wb,
                    ours_E=(png_info[a]['E'], png_info[b]['E']),
                    ours_dE=o_de,
                    ours_dI=o_di,
                    ours_dI_raw=o_di_r,
                    ours_dI_local=o_di_loc,
                ))
            return dict(
                ours_dE=ours_dE, ours_dI=ours_dI,
                ours_dI_raw=ours_dI_raw, ours_dI_local=ours_dI_local,
                details=details,
            )

        vfr_c = eval_vfr_list(vfr_cross_pos)
        vfr_n = eval_vfr_list(vfr_neigh_pos)
        o60_c = eval_60_list(cross_60)
        o60_n = eval_60_list(neigh_60)

        # Build summaries and pass/fail verdicts
        ios_dE_sum = summarize_pair_metric(vfr_c['ios_dE'], vfr_n['ios_dE'])
        ours_dE_vfr_sum = summarize_pair_metric(vfr_c['ours_dE'], vfr_n['ours_dE'])
        ours_dE_60_sum = summarize_pair_metric(o60_c['ours_dE'], o60_n['ours_dE'])

        ios_dI_sum = summarize_pair_metric(vfr_c['ios_dI'], vfr_n['ios_dI'])
        ios_dI_raw_sum = summarize_pair_metric(vfr_c['ios_dI_raw'], vfr_n['ios_dI_raw'])
        ours_dI_vfr_sum = summarize_pair_metric(vfr_c['ours_dI'], vfr_n['ours_dI'])
        ours_dI_60_sum = summarize_pair_metric(o60_c['ours_dI'], o60_n['ours_dI'])
        ours_dI_60_raw_sum = summarize_pair_metric(o60_c['ours_dI_raw'], o60_n['ours_dI_raw'])
        ours_dI_60_local_sum = summarize_pair_metric(o60_c['ours_dI_local'], o60_n['ours_dI_local'])

        verdict_60 = dict(
            dE=judge_metric(ours_dE_60_sum, ios_dE_sum),
            dI=judge_metric(ours_dI_60_sum, ios_dI_sum),
        )
        verdict_60['passed'] = verdict_60['dE']['passed'] and verdict_60['dI']['passed']

        verdict_vfr = dict(
            dE=judge_metric(ours_dE_vfr_sum, ios_dE_sum),
            dI=judge_metric(ours_dI_vfr_sum, ios_dI_sum),
        )
        verdict_vfr['passed'] = verdict_vfr['dE']['passed'] and verdict_vfr['dI']['passed']

        results.append(dict(
            name=name,
            sub=sub,
            ref_cross_sub=cd['spec']['ref_cross_sub'] or sub,
            static_hue_bins=[int(i) * 15 for i in np.nonzero(static)[0]],
            n_crossings=len(cross_60),
            n_neighbours=len(neigh_60),
            metrics=dict(
                ios_vfr_dE=ios_dE_sum,
                ours_vfr_dE=ours_dE_vfr_sum,
                ours_60fps_dE=ours_dE_60_sum,
                ios_vfr_dI=ios_dI_sum,
                ios_vfr_dI_unshifted=ios_dI_raw_sum,
                ours_vfr_dI=ours_dI_vfr_sum,
                ours_60fps_dI=ours_dI_60_sum,
                ours_60fps_dI_unshifted=ours_dI_60_raw_sum,
                ours_60fps_dI_raw_selectorX=ours_dI_60_local_sum,
            ),
            verdict_60fps=verdict_60,
            verdict_vfr=verdict_vfr,
            per_crossing_60fps=o60_c['details'],
            per_crossing_vfr=vfr_c['details'],
        ))

    return results


def _fmt_ratio(r):
    if r is None:
        return 'N/A'
    if not math.isfinite(r):
        return 'infx'
    return f'{r:.2f}x'


def _fmt_val(v):
    return 'N/A' if v is None else f'{v:7.3f}'


def print_summary(all_results):
    for res in all_results:
        hdr_line = (f"\n== {res['name']}/{res['sub']} "
                    f"(crossings from {res['ref_cross_sub']}: {res['n_crossings']}, "
                    f"neighbours: {res['n_neighbours']}, "
                    f"static hues: {res['static_hue_bins']}) ==")
        print(hdr_line)
        m = res['metrics']
        rows = [
            ('iOS VFR dE', m['ios_vfr_dE']),
            ('ours VFR dE (a)', m['ours_vfr_dE']),
            ('ours 60fps dE (b)', m['ours_60fps_dE']),
            ('iOS VFR dI', m['ios_vfr_dI']),
            ('iOS VFR dI (unshifted)', m['ios_vfr_dI_unshifted']),
            ('ours VFR dI (a)', m['ours_vfr_dI']),
            ('ours 60fps dI (b)', m['ours_60fps_dI']),
            ('ours 60fps dI (unshifted)', m['ours_60fps_dI_unshifted']),
            ('ours 60fps dI (raw_x)', m['ours_60fps_dI_raw_selectorX']),
        ]
        for label, s in rows:
            c, n = s['cross'], s['neigh']
            print(
                f"  {label:25s} | cross(n={c['n']:2d}) p50={_fmt_val(c['p50'])} p90={_fmt_val(c['p90'])} | "
                f"neigh(n={n['n']:2d}) p50={_fmt_val(n['p50'])} p90={_fmt_val(n['p90'])} | "
                f"ratio p50={_fmt_ratio(s['ratio_p50']):>6s} p90={_fmt_ratio(s['ratio_p90']):>6s}"
            )
        v60 = res['verdict_60fps']
        vvfr = res['verdict_vfr']
        print(
            f"  Verdict (60fps ours vs iOS VFR): {'PASS' if v60['passed'] else 'FAIL'} "
            f"[dE: {'PASS' if v60['dE']['passed'] else 'FAIL'} "
            f"(p50={'ok' if v60['dE']['pass_p50'] else 'FAIL'}, p90={'ok' if v60['dE']['pass_p90'] else 'FAIL'}), "
            f"dI: {'PASS' if v60['dI']['passed'] else 'FAIL'} "
            f"(p50={'ok' if v60['dI']['pass_p50'] else 'FAIL'}, p90={'ok' if v60['dI']['pass_p90'] else 'FAIL'})]"
        )
        print(
            f"  Verdict (paired VFR a):          {'PASS' if vvfr['passed'] else 'FAIL'} "
            f"[dE: {'PASS' if vvfr['dE']['passed'] else 'FAIL'}, dI: {'PASS' if vvfr['dI']['passed'] else 'FAIL'}]"
        )


def _sanitize_json(obj):
    if isinstance(obj, float):
        if math.isnan(obj):
            return None
        if math.isinf(obj):
            return 'inf' if obj > 0 else '-inf'
        return obj
    if isinstance(obj, dict):
        return {k: _sanitize_json(v) for k, v in obj.items()}
    if isinstance(obj, (list, tuple)):
        return [_sanitize_json(v) for v in obj]
    return obj


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('cases', nargs='*', default=DEFAULT_CASES,
                    help='Case specs <name>:<sub>[:<ref_cross_sub>]')
    ap.add_argument('--ref', default='.local/sxs', help='Reference root directory (default .local/sxs)')
    ap.add_argument('--thr', type=float, default=40.0, help='Fringe chroma threshold (default 40)')
    ap.add_argument('--bar-left', type=float, default=63.0,
                    help='GlassTabBar scene x offset in px (PhoneGeometry.barLeft = 21dp * 3 = 63.0)')
    ap.add_argument('--csv-dir', default=None,
                    help='Optional directory to write per-frame paired VFR CSVs (never writes into --ref)')
    ap.add_argument('--json', default=None, help='Optional path to write full JSON results')
    args = ap.parse_args()

    hdr = _load_module('hdr', os.path.join(args.ref, 'hdr.py'))
    mrf_path = os.path.join('tools', 'measure_rim_fringe.py')
    if not os.path.exists(mrf_path):
        mrf_path = os.path.join(args.ref, 'fringe.py')
    mrf = _load_module('measure_rim_fringe', mrf_path)
    mrf.REF = args.ref

    parsed = [parse_case_spec(c) for c in args.cases]
    grouped = defaultdict(list)
    for c in parsed:
        grouped[c['name']].append(c)

    all_results = []
    for name, cspecs in grouped.items():
        res_list = process_recording(
            name=name, case_specs=cspecs, ref=args.ref,
            hdr=hdr, mrf=mrf, thr=args.thr, bar_left=args.bar_left,
            csv_dir=args.csv_dir,
        )
        all_results.extend(res_list)

    # Preserve input case order
    order = {(c['name'], c['sub']): i for i, c in enumerate(parsed)}
    all_results.sort(key=lambda r: order[(r['name'], r['sub'])])

    print_summary(all_results)

    if args.json:
        os.makedirs(os.path.dirname(os.path.abspath(args.json)), exist_ok=True)
        payload = dict(
            method='Formation 0.5 continuity: fringe energy step dE (measure_rim_fringe thr=40) and motion-compensated box luma step dI',
            bar_left_px=args.bar_left,
            thr=args.thr,
            pass_rule='ours cross/neigh <= 1.5x at median AND <= 2.0x at p90, OR <= iOS same ratio',
            cases=all_results,
        )
        with open(args.json, 'w', encoding='utf-8') as f:
            json.dump(_sanitize_json(payload), f, indent=2, allow_nan=False)
            f.write('\n')
        print(f'\nWrote JSON: {args.json}')


if __name__ == '__main__':
    main()

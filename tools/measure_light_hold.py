"""Light-mode holds and drags, iOS or device captures against a replay (#101, item 87).

Until now only the light bar at rest, the pill and the held lens's top rim and shadow were scored, each by a
one-off script. This scores a whole light-mode replay: every frame of the source recording against our frame for
the same instant, down one column of the scene that follows the finger, in four row bands relative to bar_top.

    python measure_light_hold.py <ours-dir> [--geometry IMG_7049|IMG_7048|pixel7] [--script S] [--video V]
                                 [--offset 35.7] [--band NAME=y0,y1 ...] [--scan-offset] [--csv out.csv]
                                 [--ref .local/sxs] [--sdr-video] [--bar-top Y] [--auto-bar-top]

<ours-dir> holds the replay's frames (f00000.png at 60 per scripted second, from tools/reference-replay/render.sh
or rrw.sh, or device screenrecord frames) of `script`, a replay script (default <ref>/ref/IMG_7049/hold128.txt).
`--offset` is the recording's time of script second 0 (35.7 for IMG_7049 hold128.txt).

Decode: iOS HDR10 recordings (IMG_7049, IMG_7048) are decoded to display sRGB through `.local/sxs/hdr.py`
(`size`/`frames`, PQ BT.2020 -> display sRGB) with an identical fallback if `.local/sxs` is unavailable.
Non-HDR10 device captures (`--geometry pixel7` or `--sdr-video`) use plain sRGB `rgb24` without HDR10 `zscale`.

Geometries (all four default bands are defined relative to rest `bar_top`):
  - `IMG_7049`: 1170 px wide, bottom 702 rows, rest `bar_top = 452`
    (`top` 448-466 = -4..+14, `lower` 622-646 = +170..+194, `shadow` 650-668 = +198..+216,
     `tail` 678-694 = +226..+242; scored column `ROWS` 430-700 = -22..+248).
  - `IMG_7048`: 1168x570 crop, rest `bar_top = 322` (-130 px vs IMG_7049), bands mapped relative to
    `bar_top` (`top` 318-336, `lower` 492-516, `shadow` 520-538, `tail` 548-564; column 300-570).
  - `pixel7`: 1080x2400 screenrecord / screencap or cropped bar captures, device px = scene px * (2.625 / 3)
    with centered scene width (`x_offset = max(0, (W - 1170 * 0.875) / 2)` -> 28.125 on 1080 px). Rest `bar_top`
    is detected from rest frames (`~2120` on 1080x2400) and resampled vertically to scene-relative rows.

Levels are display luma 0-255 (0.299 R + 0.587 G + 0.114 B).
"""
import argparse
import csv
import importlib.util
import os
import re
import subprocess
import sys

import numpy as np
from PIL import Image

sys.dont_write_bytecode = True

SDR = ("zscale=t=linear:npl=100,format=gbrpf32le,"
       "zscale=p=bt709:t=iec61966-2-1:m=bt709:r=full,format=rgb24")
VIDEO = 'research/datasets/ios27-phone/light/IMG_7049.MP4'
IMG7048_VIDEO = 'research/datasets/ios27-phone/light/IMG_7048.MP4'
SCRIPT = 'ref/IMG_7049/hold128.txt'

SCENE_BAR_TOP = 452
ROWS_REL = (-22, 248)
BANDS_REL = {
    'top': (-4, 14),
    'lower': (170, 194),
    'shadow': (198, 216),
    'tail': (226, 242),
}
ROWS = (SCENE_BAR_TOP + ROWS_REL[0], SCENE_BAR_TOP + ROWS_REL[1])  # (430, 700)
BANDS = {k: (SCENE_BAR_TOP + v[0], SCENE_BAR_TOP + v[1]) for k, v in BANDS_REL.items()}
HALF = 6
HOLD_SPEED = 60.0
PIXEL7_SCALE = 2.625 / 3.0

GEOMETRIES = {
    'IMG_7049': dict(
        scale=1.0,
        bar_top=452,
        crop_h=702,
        center_x=False,
        sdr=False,
        probe_x=(294, 307),
        from_bottom=250,
        default_video=VIDEO,
    ),
    'IMG_7048': dict(
        scale=1.0,
        bar_top=322,
        crop_h=None,
        center_x=False,
        sdr=False,
        probe_x=(294, 307),
        from_bottom=248,
        default_video=IMG7048_VIDEO,
    ),
    'pixel7': dict(
        scale=PIXEL7_SCALE,
        bar_top=None,
        crop_h=None,
        center_x=True,
        sdr=True,
        probe_x=(340, 355),
        from_bottom=280,
        default_video=None,
    ),
}

_HDR_CACHE = {}


def _load_hdr(ref_dir='.local/sxs'):
    key = os.path.abspath(ref_dir)
    if key in _HDR_CACHE:
        return _HDR_CACHE[key]
    candidates = [os.path.join(ref_dir, 'hdr.py'), os.path.join('.local', 'sxs', 'hdr.py')]
    mod = None
    for cand in candidates:
        if os.path.isfile(cand):
            prev = sys.dont_write_bytecode
            sys.dont_write_bytecode = True
            try:
                spec = importlib.util.spec_from_file_location('_sxs_hdr', cand)
                if spec and spec.loader:
                    m = importlib.util.module_from_spec(spec)
                    spec.loader.exec_module(m)
                    mod = m
                    break
            finally:
                sys.dont_write_bytecode = prev
    _HDR_CACHE[key] = mod
    return mod


def size(path, ref_dir='.local/sxs'):
    hdr = _load_hdr(ref_dir)
    if hdr is not None:
        return hdr.size(path)
    w, h = subprocess.run(
        ['ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_entries',
         'stream=width,height', '-of', 'csv=p=0', path],
        capture_output=True, text=True
    ).stdout.strip().split(',')[:2]
    return int(w), int(h)


def _frames_hdr_fallback(path, t0=0.0, dur=None, crop=None):
    w, h = size(path)
    vf = (f"crop={crop[0]}:{crop[1]}:{crop[2]}:{crop[3]}," if crop else '') + SDR
    if crop:
        w, h = crop[0], crop[1]
    args = ['-ss', str(t0)] + (['-t', str(dur)] if dur is not None else [])
    log = subprocess.run(
        ['ffmpeg', '-v', 'info'] + args + ['-i', path, '-fps_mode', 'passthrough',
                                           '-vf', 'showinfo', '-f', 'null', '-'],
        capture_output=True, text=True
    ).stderr
    pts = [t0 + float(x) for x in re.findall(r'pts_time:([\d.]+)', log)]
    p = subprocess.Popen(
        ['ffmpeg', '-v', 'error'] + args + ['-i', path, '-fps_mode', 'passthrough',
                                            '-vf', vf, '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-'],
        stdout=subprocess.PIPE
    )
    n = w * h * 3
    for t in pts:
        b = p.stdout.read(n)
        if len(b) < n:
            break
        yield t, np.frombuffer(b, np.uint8).reshape(h, w, 3)
    p.stdout.close()
    p.wait()


def _frames_sdr(path, t0=0.0, dur=None, crop=None, ref_dir='.local/sxs'):
    """Plain display-sRGB rgb24 decode (no HDR10 zscale) for Android screenrecord captures."""
    w, h = size(path, ref_dir)
    vf_parts = []
    if crop:
        vf_parts.append(f"crop={crop[0]}:{crop[1]}:{crop[2]}:{crop[3]}")
        w, h = crop[0], crop[1]
    args = ['-ss', str(t0)] + (['-t', str(dur)] if dur is not None else [])
    log = subprocess.run(
        ['ffmpeg', '-v', 'info'] + args + ['-i', path, '-fps_mode', 'passthrough',
                                           '-vf', 'showinfo', '-f', 'null', '-'],
        capture_output=True, text=True
    ).stderr
    pts = [t0 + float(x) for x in re.findall(r'pts_time:([\d.]+)', log)]
    vf_args = ['-vf', ','.join(vf_parts)] if vf_parts else []
    p = subprocess.Popen(
        ['ffmpeg', '-v', 'error'] + args + ['-i', path, '-fps_mode', 'passthrough']
        + vf_args + ['-f', 'rawvideo', '-pix_fmt', 'rgb24', '-'],
        stdout=subprocess.PIPE
    )
    n = w * h * 3
    for t in pts:
        b = p.stdout.read(n)
        if len(b) < n:
            break
        yield t, np.frombuffer(b, np.uint8).reshape(h, w, 3)
    p.stdout.close()
    p.wait()


def frames(path, t0, dur, crop, sdr_video=False, ref_dir='.local/sxs'):
    """(pts seconds, display-sRGB frame) for every source frame in [t0, t0 + dur]."""
    if sdr_video:
        yield from _frames_sdr(path, t0, dur, crop, ref_dir=ref_dir)
        return
    hdr = _load_hdr(ref_dir)
    if hdr is not None:
        yield from hdr.frames(path, t0, dur, crop)
    else:
        yield from _frames_hdr_fallback(path, t0, dur, crop)


def parse_script(path):
    """(finger samples [(t, x)], first down t, last up t or None, end t), seconds, x in scene px."""
    pts, down, up, end = [], None, None, None
    with open(path) as f:
        for line in f:
            w = line.split()
            if len(w) >= 4 and w[1] in ('down', 'move', 'up'):
                t = int(w[0]) / 1000
                if w[1] == 'down' and down is None:
                    down = t
                if w[1] == 'up':
                    up = t
                # An `up` is not a finger position to interpolate through: phase() ignores every frame after the lift, so
                # the last `move` stays the finger's position for scoring. (An `up` before any other sample still starts the list.)
                if w[1] != 'up' or not pts:
                    pts.append((t, float(w[2])))
            elif w and w[0] == 'end':
                end = int(w[1]) / 1000
    return np.array(pts), down, up, end


def finger_x(pts, t):
    return float(np.interp(t, pts[:, 0], pts[:, 1]))


def finger_speed(pts, t):
    return abs(finger_x(pts, t + .05) - finger_x(pts, t - .05)) / .1


def luma(a):
    a = a.astype(np.float64)
    return a[..., 0] * .299 + a[..., 1] * .587 + a[..., 2] * .114


def compute_x_offset(width, scale, center_x):
    if not center_x:
        return 0.0
    return max(0.0, (width - 1170.0 * scale) / 2.0)


def find_bar_top(a, scale=1.0, center_x=False, probe_x=(294, 307), from_bottom=250):
    """Detect the upper hairline row (`bar_top`) of the light-mode Phone tab bar on a rest frame."""
    h, w = a.shape[:2]
    x_off = compute_x_offset(w, scale, center_x)
    px0 = max(0, int(round(x_off + probe_x[0] * scale)))
    px1 = min(w, max(px0 + 1, int(round(x_off + probe_x[1] * scale))))
    col = luma(a[:, px0:px1]).mean(axis=1)

    y_exp = h - int(round(from_bottom * scale))
    pad_top = max(6, int(round(12 * scale)))
    min_below = int(round(140 * scale))
    y_lo = max(pad_top, y_exp - int(round(90 * scale))) if y_exp >= pad_top else pad_top
    y_hi = min(h - min_below, y_exp + int(round(90 * scale))) if y_exp >= pad_top else h - min_below
    if y_hi <= y_lo:
        y_lo, y_hi = pad_top, max(pad_top + 1, h - int(round(80 * scale)))

    bar_h = int(round(187 * scale))
    margin_pre = max(2, int(round(6 * scale)))
    in_lo = max(2, int(round(5 * scale)))
    in_hi = max(in_lo + 2, int(round(20 * scale)))

    candidates = []
    for y in range(y_lo, y_hi):
        if col[y] < 248.5 and col[y] <= col[y - 1] and col[y] <= col[y + 1]:
            pre_mean = float(col[max(0, y - margin_pre):max(1, y - 1)].mean())
            if pre_mean < 247.0:
                continue
            in_mean = float(col[y + in_lo:min(h, y + in_hi)].mean())
            if in_mean < 235.0:
                continue
            bot = y + bar_h
            has_bot = (bot + 4 < h and float(col[max(0, bot - 4):min(h, bot + 5)].min()) < 253.5)
            score = (0 if has_bot else 1, abs(y - y_exp), col[y])
            candidates.append((score, y))

    if candidates:
        candidates.sort()
        return int(candidates[0][1])

    fallback = [
        (abs(y - y_exp), col[y], y)
        for y in range(y_lo, y_hi)
        if col[y] < 248.5 and col[y] <= col[y - 1] and col[y] <= col[y + 1]
    ]
    if fallback:
        fallback.sort()
        return int(fallback[0][2])
    if 0 <= y_exp < h:
        return int(y_exp)
    raise ValueError(f"Could not locate bar_top in frame of shape {a.shape}")


def column(a, x, bar_top=SCENE_BAR_TOP, dy_start=ROWS_REL[0], dy_end=ROWS_REL[1],
           scale=1.0, x_offset=0.0):
    """Extract luma column aligned to `bar_top + dy` for `dy in [dy_start, dy_end)`."""
    if scale == 1.0 and x_offset == 0.0:
        x0 = int(round(min(max(x, HALF), a.shape[1] - HALF - 1)))
        y0 = bar_top + dy_start
        y1 = bar_top + dy_end
        return luma(a[y0:y1, x0 - HALF:x0 + HALF + 1]).mean(axis=1)

    half = max(1, int(round(HALF * scale)))
    x_dev = x_offset + x * scale
    x0 = int(round(min(max(x_dev, half), a.shape[1] - half - 1)))
    dy = np.arange(dy_start, dy_end, dtype=np.float64)
    y_dev = bar_top + dy * scale
    iy0 = max(0, int(np.floor(y_dev[0])))
    iy1 = min(a.shape[0], int(np.ceil(y_dev[-1])) + 1)
    strip = luma(a[iy0:iy1, x0 - half:x0 + half + 1]).mean(axis=1)
    return np.interp(y_dev, np.arange(iy0, iy1, dtype=np.float64), strip)


def phase(pts, down, up, t):
    if down is None or t < down - 0.3 or (up is not None and t > up):
        return 'rest'
    if t < down:
        return 'rest'
    return 'drag' if finger_speed(pts, t) >= HOLD_SPEED else 'hold'


def _valid_dy_bounds(height, bar_top, scale, dy_lo, dy_hi):
    if scale == 1.0:
        lo = max(dy_lo, -bar_top)
        hi = min(dy_hi, height - bar_top)
    else:
        lo = max(dy_lo, int(np.ceil((0.0 - bar_top) / scale)))
        hi = min(dy_hi, int(np.floor((height - 1.0 - bar_top) / scale)) + 1)
    return lo, hi


def _ours_geometry(b):
    h, w = b.shape[:2]
    if w == 1170 and h >= 702:
        return b[-702:], 1.0, 0.0, SCENE_BAR_TOP
    if w in (1168, 1170) and h == 570:
        return b, 1.0, 0.0, 322
    if w <= 1080:
        scale = PIXEL7_SCALE
        x_off = compute_x_offset(w, scale, center_x=True)
        bt = find_bar_top(b, scale=scale, center_x=True, probe_x=GEOMETRIES['pixel7']['probe_x'],
                          from_bottom=GEOMETRIES['pixel7']['from_bottom'])
        return b, scale, x_off, bt
    bt = find_bar_top(b, scale=1.0, center_x=False, probe_x=(294, 307), from_bottom=250)
    return b, 1.0, 0.0, bt


def resolve_bands(spec, vid_bar_top, custom_bands):
    if custom_bands:
        rel_bands = {k: (y0 - vid_bar_top, y1 - vid_bar_top) for k, (y0, y1) in custom_bands.items()}
        disp_bands = dict(custom_bands)
        dy_lo = min(ROWS_REL[0], min(r0 for r0, _ in rel_bands.values()))
        dy_hi = max(ROWS_REL[1], max(r1 for _, r1 in rel_bands.values()))
    else:
        rel_bands = dict(BANDS_REL)
        disp_bands = {k: (vid_bar_top + r0, vid_bar_top + r1) for k, (r0, r1) in rel_bands.items()}
        dy_lo, dy_hi = ROWS_REL
    return rel_bands, disp_bands, dy_lo, dy_hi


def series(ours, script, video, offset, custom_bands=None, geometry='IMG_7049',
           sdr_video=False, bar_top_override=None, auto_bar_top=False, ref_dir='.local/sxs'):
    pts, down, up, end = parse_script(script)
    if end is None:
        sys.exit(f"{script} has no 'end <ms>' line, so the span to read from the recording is unknown")
    spec = GEOMETRIES[geometry]
    scale = spec['scale']
    sdr = sdr_video or spec['sdr']
    w, h = size(video, ref_dir=ref_dir)
    crop = (w, spec['crop_h'], 0, h - spec['crop_h']) if (spec['crop_h'] and h >= spec['crop_h']) else None

    vid_bar_top = bar_top_override
    if vid_bar_top is None and not auto_bar_top and spec['bar_top'] is not None:
        # When crop_h matches or no crop is expected, use the canonical rest bar_top; otherwise auto-detect.
        if crop is not None or (geometry == 'IMG_7048' and h == 570):
            vid_bar_top = spec['bar_top']

    rows = []
    rel_bands = None
    disp_bands = None
    dy_lo, dy_hi = ROWS_REL
    ours_geom_cache = None

    for pts_t, a in frames(video, offset, end, crop, sdr_video=sdr, ref_dir=ref_dir):
        t = pts_t - offset
        p = os.path.join(ours, f'f{round(t * 60):05d}.png')
        if not os.path.exists(p):
            continue
        if crop is not None and a.shape[0] != crop[1]:
            a = a[-crop[1]:]

        if vid_bar_top is None:
            vid_bar_top = find_bar_top(
                a, scale=scale, center_x=spec['center_x'],
                probe_x=spec['probe_x'], from_bottom=spec['from_bottom']
            )
        if rel_bands is None:
            rel_bands, disp_bands, dy_lo, dy_hi = resolve_bands(spec, vid_bar_top, custom_bands)

        b_raw = np.array(Image.open(p).convert('RGB'))
        if ours_geom_cache is None:
            b, ours_scale, ours_xoff, ours_bar_top = _ours_geometry(b_raw)
            ours_geom_cache = (b_raw.shape, ours_scale, ours_xoff, ours_bar_top)
        else:
            _, ours_scale, ours_xoff, ours_bar_top = ours_geom_cache
            if b_raw.shape[1] == 1170 and b_raw.shape[0] >= 702:
                b = b_raw[-702:]
            else:
                b = b_raw

        dy0_v, dy1_v = _valid_dy_bounds(a.shape[0], vid_bar_top, scale, dy_lo, dy_hi)
        dy0_o, dy1_o = _valid_dy_bounds(b.shape[0], ours_bar_top, ours_scale, dy_lo, dy_hi)
        dy0 = max(dy0_v, dy0_o)
        dy1 = min(dy1_v, dy1_o)
        if dy1 <= dy0:
            continue

        vid_xoff = compute_x_offset(a.shape[1], scale, spec['center_x'])
        x = finger_x(pts, t)
        ci = column(a, x, bar_top=vid_bar_top, dy_start=dy0, dy_end=dy1, scale=scale, x_offset=vid_xoff)
        co = column(b, x, bar_top=ours_bar_top, dy_start=dy0, dy_end=dy1, scale=ours_scale, x_offset=ours_xoff)

        r = dict(t=t, x=x, phase=phase(pts, down, up, t))
        d = co - ci
        for name, (r0, r1) in rel_bands.items():
            s0 = max(0, r0 - dy0)
            s1 = min(dy1 - dy0, r1 - dy0)
            if s1 <= s0:
                continue
            s = slice(s0, s1)
            r[name + '_ios'] = float(ci[s].mean())
            r[name + '_ours'] = float(co[s].mean())
            r[name + '_rms'] = float(np.sqrt((d[s] ** 2).mean()))
        rows.append(r)

    if disp_bands is None:
        bt = vid_bar_top if vid_bar_top is not None else (spec['bar_top'] or SCENE_BAR_TOP)
        _, disp_bands, _, _ = resolve_bands(spec, bt, custom_bands)
    return rows, disp_bands


def report(rows, bands, title):
    active_bands = {k: v for k, v in bands.items() if (k + '_rms') in rows[0]}
    print(f"\n== {title}: {len(rows)} paired frames")
    for ph in ('rest', 'hold', 'drag'):
        sel = [r for r in rows if r['phase'] == ph]
        if not sel:
            continue
        print(f"  {ph:4s} ({len(sel)} frames)")
        for name in active_bands:
            ii = np.mean([r[name + '_ios'] for r in sel])
            oo = np.mean([r[name + '_ours'] for r in sel])
            rms = np.sqrt(np.mean([r[name + '_rms'] ** 2 for r in sel]))
            print(f"    {name:7s} rows {active_bands[name][0]}-{active_bands[name][1]}: "
                  f"iOS {ii:6.1f}  ours {oo:6.1f}  ours-iOS {oo - ii:+6.1f}  rms {rms:5.2f}")
    allr = np.sqrt(np.mean([[r[n + '_rms'] ** 2 for n in active_bands] for r in rows]))
    print(f"  all frames, all bands: rms {allr:.2f} levels")
    worst = sorted(rows, key=lambda r: -max(r[n + '_rms'] for n in active_bands))[:5]
    for r in worst:
        n = max(active_bands, key=lambda k: r[k + '_rms'])
        print(f"  worst: script {r['t']:.3f} s ({r['phase']}, finger x {r['x']:.0f}) "
              f"band {n}: iOS {r[n + '_ios']:.1f} ours {r[n + '_ours']:.1f} rms {r[n + '_rms']:.1f}")


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('ours')
    ap.add_argument('--ref', default='.local/sxs')
    ap.add_argument('--geometry', choices=tuple(GEOMETRIES), default='IMG_7049')
    ap.add_argument('--script')
    ap.add_argument('--video')
    ap.add_argument('--offset', type=float, default=35.7)
    ap.add_argument('--band', action='append', default=[], help='NAME=y0,y1 (replaces the default bands)')
    ap.add_argument('--sdr-video', action='store_true', help='decode video as plain sRGB rgb24 without HDR10 zscale')
    ap.add_argument('--bar-top', type=int, help='override rest bar_top row in video coordinates')
    ap.add_argument('--auto-bar-top', action='store_true', help='auto-detect bar_top on the first paired frame')
    ap.add_argument('--scan-offset', action='store_true')
    ap.add_argument('--csv')
    a = ap.parse_args()

    spec = GEOMETRIES[a.geometry]
    video = a.video or spec['default_video']
    if not video:
        sys.exit(f"--video is required with --geometry {a.geometry}")
    script = a.script or os.path.join(a.ref, SCRIPT)
    custom_bands = {k: tuple(int(v) for v in s.split(',')) for k, s in (b.split('=') for b in a.band)}

    if a.scan_offset:
        for off in np.arange(a.offset - .8, a.offset + .81, .1):
            rows, scan_bands = series(
                a.ours, script, video, float(off), custom_bands=custom_bands, geometry=a.geometry,
                sdr_video=a.sdr_video, bar_top_override=a.bar_top, auto_bar_top=a.auto_bar_top, ref_dir=a.ref
            )
            if rows:
                bnames = [k for k in scan_bands if (k + '_rms') in rows[0]]
                rms = np.sqrt(np.mean([[r[n + '_rms'] ** 2 for n in bnames] for r in rows]))
                last = bnames[-2] if len(bnames) >= 2 else bnames[-1]
                c = np.corrcoef([r[last + '_ios'] for r in rows], [r[last + '_ours'] for r in rows])[0, 1]
                print(f"offset {off:6.2f} s: {len(rows)} frames, rms {rms:.2f}, {last} corr {c:+.2f}", flush=True)

    rows, used_bands = series(
        a.ours, script, video, a.offset, custom_bands=custom_bands, geometry=a.geometry,
        sdr_video=a.sdr_video, bar_top_override=a.bar_top, auto_bar_top=a.auto_bar_top, ref_dir=a.ref
    )
    if not rows:
        sys.exit('no frames paired: check <ours-dir>, --script and --offset')
    if a.csv:
        with open(a.csv, 'w', newline='') as f:
            wr = csv.DictWriter(f, fieldnames=list(rows[0]))
            wr.writeheader()
            wr.writerows(rows)
    report(rows, used_bands, os.path.basename(os.path.normpath(a.ours)))

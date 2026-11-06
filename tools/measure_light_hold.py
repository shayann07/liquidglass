"""Light-mode holds and drags, iOS against our replay of the same input (#101, item 87).

Until now only the light bar at rest, the pill and the held lens's top rim and shadow were scored, each by a
one-off script. This scores a whole light-mode replay: every frame of the iOS recording against our frame for
the same instant, down one column of the scene that follows the finger, in four row bands.

    python tools/measure_light_hold.py <ours-dir> [--script S] [--video V] [--offset 36.0] [--band NAME=y0,y1 ...]
                                       [--scan-offset] [--csv out.csv] [--ref .local/sxs]

<ours-dir> holds the replay's frames (f00000.png at 60 per scripted second, from tools/reference-replay/render.sh
or rrw.sh) of `script`, a replay script (default <ref>/ref/IMG_7049/hold128.txt: IMG_7049's light-mode hold and
drag on Keypad / Contacts, shifted so its first second is 0). `--offset` is the recording's time of script
second 0: 35.7 for that script. The script was cut with a 36.0 s shift, but `--scan-offset` (rms of all bands and
the correlation of the shadow band's two series, for offsets +-0.8 s) puts the best alignment at 35.5-35.7
(2026-11-07, current main: rms 7.3 at 35.5 against 8.6 at 36.0; shadow correlation +0.57 at 35.6-35.7 against
+0.37 at 36.0), so that is the default; the scan is there so the alignment is checked, not assumed. The recordings are private HDR10 and are read locally; nothing is written but
what `--csv` names.

Decode: HDR10 (PQ, BT.2020) to display sRGB, as tools/measure_rim_fringe.py (research/README.md II.4); the
scene is the bottom 702 rows of the recording, 1170 px wide, the replay's own size. Pairing: every source frame
(VFR kept) with our frame at round((pts - offset) x 60).

Measurement: the finger's x at that instant, from the script (linear between its samples); luma of the columns
x-6..x+6 averaged, rows 430-700 (the bar, the lens that sits on it and the shadow below). Four bands (rows),
chosen where the replay scene has no page content that the recording lacks: top 448-466 (the bar's and the
lens's upper rim), lower 622-646 (the lens's lower rim), shadow 650-668 (below it, deepest ~20 px down) and tail
678-694; override with --band. The rows between (the icons and labels) are not scored by default: the
replay scene's page text and the recording's separator lines (rows ~443 and ~673) differ, so a luma column there
measures the page, not the glass; a band placed on them reports that difference too. Per frame and band: the mean luma of each source and the rms of the row-by-row
difference. Frames are phased from the script: `hold` (finger speed under 60 px/s over +-50 ms, finger down),
`drag` (60 px/s or more), `rest` (finger up, or 0.3 s before the first down). Printed per phase: frames, and per
band iOS mean, ours mean, ours-iOS, rms of the profile difference, then the five worst frames.
Levels are display luma 0-255 (0.299 R + 0.587 G + 0.114 B).
"""
import argparse, csv, os, re, subprocess, sys
import numpy as np
from PIL import Image

SDR = ("zscale=t=linear:npl=100,format=gbrpf32le,"
       "zscale=p=bt709:t=iec61966-2-1:m=bt709:r=full,format=rgb24")
VIDEO = 'research/datasets/ios27-phone/light/IMG_7049.MP4'
SCRIPT = 'ref/IMG_7049/hold128.txt'
ROWS = (430, 700)
BANDS = {'top': (448, 466), 'lower': (622, 646), 'shadow': (650, 668), 'tail': (678, 694)}
HALF = 6
HOLD_SPEED = 60.0


def size(path):
    w, h = subprocess.run(['ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_entries',
                           'stream=width,height', '-of', 'csv=p=0', path], capture_output=True, text=True).stdout.strip().split(',')[:2]
    return int(w), int(h)


def frames(path, t0, dur, crop):
    """(pts seconds, display-sRGB frame) for every source frame in [t0, t0 + dur], cropped (w, h, x, y) first."""
    vf = f"crop={crop[0]}:{crop[1]}:{crop[2]}:{crop[3]}," + SDR
    w, h = crop[0], crop[1]
    args = ['-ss', str(t0), '-t', str(dur)]
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


def parse_script(path):
    """(finger samples [(t, x)], first down t, last up t or None, end t), seconds, x in scene px."""
    pts, down, up, end = [], None, None, None
    for line in open(path):
        w = line.split()
        if len(w) >= 4 and w[1] in ('down', 'move', 'up'):
            t = int(w[0]) / 1000
            if w[1] == 'down' and down is None: down = t
            if w[1] == 'up': up = t
            if w[1] != 'up' or not pts: pts.append((t, float(w[2])))
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


def column(a, x):
    x0 = int(round(min(max(x, HALF), a.shape[1] - HALF - 1)))
    return luma(a[ROWS[0]:ROWS[1], x0 - HALF:x0 + HALF + 1]).mean(axis=1)


def phase(pts, down, up, t):
    if down is None or t < down - 0.3 or (up is not None and t > up): return 'rest'
    if t < down: return 'rest'
    return 'drag' if finger_speed(pts, t) >= HOLD_SPEED else 'hold'


def series(ours, script, video, offset, bands):
    pts, down, up, end = parse_script(script)
    w, h = size(video)
    rows = []
    for pts_t, a in frames(video, offset, end, (w, 702, 0, h - 702)):
        t = pts_t - offset
        p = os.path.join(ours, f'f{round(t * 60):05d}.png')
        if not os.path.exists(p): continue
        b = np.array(Image.open(p).convert('RGB'))
        if b.shape[0] != 702: b = b[-702:]
        x = finger_x(pts, t)
        ci, co = column(a[-702:], x), column(b, x)
        r = dict(t=t, x=x, phase=phase(pts, down, up, t))
        d = co - ci
        for name, (y0, y1) in bands.items():
            s = slice(y0 - ROWS[0], y1 - ROWS[0])
            r[name + '_ios'] = float(ci[s].mean()); r[name + '_ours'] = float(co[s].mean())
            r[name + '_rms'] = float(np.sqrt((d[s] ** 2).mean()))
        rows.append(r)
    return rows


def report(rows, bands, title):
    print(f"\n== {title}: {len(rows)} paired frames")
    for ph in ('rest', 'hold', 'drag'):
        sel = [r for r in rows if r['phase'] == ph]
        if not sel: continue
        print(f"  {ph:4s} ({len(sel)} frames)")
        for name in bands:
            ii = np.mean([r[name + '_ios'] for r in sel]); oo = np.mean([r[name + '_ours'] for r in sel])
            rms = np.sqrt(np.mean([r[name + '_rms'] ** 2 for r in sel]))
            print(f"    {name:7s} rows {bands[name][0]}-{bands[name][1]}: iOS {ii:6.1f}  ours {oo:6.1f}  ours-iOS {oo - ii:+6.1f}  rms {rms:5.2f}")
    allr = np.sqrt(np.mean([[r[n + '_rms'] ** 2 for n in bands] for r in rows]))
    print(f"  all frames, all bands: rms {allr:.2f} levels")
    worst = sorted(rows, key=lambda r: -max(r[n + '_rms'] for n in bands))[:5]
    for r in worst:
        n = max(bands, key=lambda k: r[k + '_rms'])
        print(f"  worst: script {r['t']:.3f} s ({r['phase']}, finger x {r['x']:.0f}) band {n}: iOS {r[n + '_ios']:.1f} ours {r[n + '_ours']:.1f} rms {r[n + '_rms']:.1f}")


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('ours'); ap.add_argument('--ref', default='.local/sxs')
    ap.add_argument('--script'); ap.add_argument('--video', default=VIDEO)
    ap.add_argument('--offset', type=float, default=35.7)
    ap.add_argument('--band', action='append', default=[], help='NAME=y0,y1 (replaces the default bands)')
    ap.add_argument('--scan-offset', action='store_true'); ap.add_argument('--csv')
    a = ap.parse_args()
    script = a.script or os.path.join(a.ref, SCRIPT)
    bands = {k: tuple(int(v) for v in s.split(',')) for k, s in (b.split('=') for b in a.band)} or BANDS
    if a.scan_offset:
        for off in np.arange(a.offset - .8, a.offset + .81, .1):
            rows = series(a.ours, script, a.video, float(off), bands)
            if rows:
                rms = np.sqrt(np.mean([[r[n + '_rms'] ** 2 for n in bands] for r in rows]))
                last = list(bands)[-2]  # a band below the lens, where the page is blank
                c = np.corrcoef([r[last + '_ios'] for r in rows], [r[last + '_ours'] for r in rows])[0, 1]
                print(f"offset {off:6.2f} s: {len(rows)} frames, rms {rms:.2f}, {last} corr {c:+.2f}", flush=True)
    rows = series(a.ours, script, a.video, a.offset, bands)
    if not rows: sys.exit('no frames paired: check <ours-dir>, --script and --offset')
    if a.csv:
        with open(a.csv, 'w', newline='') as f:
            wr = csv.DictWriter(f, fieldnames=list(rows[0])); wr.writeheader(); wr.writerows(rows)
    report(rows, bands, os.path.basename(os.path.normpath(a.ours)))

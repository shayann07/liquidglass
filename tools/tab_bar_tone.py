#!/usr/bin/env python
"""Reads the resting tab bar's tone off flat-backdrop replays of the real GlassTabBar (#203 PR A).

Each replay is `PhoneReplay` with "+flat=<levels>" (see apps/desktop PhoneReplay.kt): the Keypad scene's page is
replaced by a flat colour that steps through the levels, one per 500 ms of scripted time, with nothing touched. This
script takes the last-but-five frame of each level (so any settling is over), reads the bar interior (rows 462-482,
between the icons, three patches) and the selected pill (rows 470-482, over the Keypad tab), and prints the table.

    python tools/tab_bar_tone.py <name>=<replay dir> ...           # prints a markdown table
    python tools/tab_bar_tone.py --json out.json <name>=<dir> ...  # also writes the numbers
    python tools/tab_bar_tone.py --check review/atlas/tab-bar-tone-2026-11-18/tone.json <name>=<dir> ...

--check compares with a stored table (tolerance 0.5 level) and exits 1 on a difference: the baseline pin for PR A, to be
re-recorded, with the reason stated, when a later PR moves the bar's material on purpose.
Levels are Rec.601 luma of the display sRGB bytes, as in the iOS measurements these are compared with.
"""
import csv, json, sys
import numpy as np
from PIL import Image

BAR_ROWS = (462, 483)
BAR_PATCHES = [(100, 170), (280, 380), (520, 590)]
PILL_ROWS = (470, 483)
PILL_COLS = (680, 800)
SETTLE = 5  # frames before the end of a level


def luma(rgb):
    return 0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]


def read(dir_):
    levels = {}
    for row in csv.DictReader(open(f"{dir_}/flat.csv")):
        levels.setdefault(int(row["level"]), []).append((int(row["frame"]), tuple(int(row[c]) for c in "rgb")))
    out = []
    for level, frames in sorted(levels.items()):
        frame, colour = frames[-1 - SETTLE] if len(frames) > SETTLE else frames[-1]
        img = np.asarray(Image.open(f"{dir_}/f{frame:05d}.png").convert("RGB")).astype(float)
        bar = np.concatenate([img[BAR_ROWS[0]:BAR_ROWS[1], a:b].reshape(-1, 3) for a, b in BAR_PATCHES])
        pill = img[PILL_ROWS[0]:PILL_ROWS[1], PILL_COLS[0]:PILL_COLS[1]].reshape(-1, 3)
        bg = img[100:200, 300:800].reshape(-1, 3)
        out.append({"backdrop": round(float(np.median(luma(bg))), 1), "colour": colour,
                    "bar": round(float(np.median(luma(bar))), 1), "bar_rgb": [round(float(v), 1) for v in np.median(bar, axis=0)],
                    "bar_spread": round(float(np.percentile(luma(bar), 95) - np.percentile(luma(bar), 5)), 1),
                    "pill": round(float(np.median(luma(pill))), 1), "frame": frame})
    return out


def main(argv):
    json_out = check = None
    args = []
    it = iter(argv)
    for a in it:
        if a == "--json":
            json_out = next(it)
        elif a == "--check":
            check = next(it)
        else:
            args.append(a)
    data = {}
    for a in args:
        name, _, d = a.partition("=")
        data[name] = read(d)
    names = list(data)
    backs = [r["backdrop"] for r in next(iter(data.values()))]
    print("Resting bar interior (median Rec.601 luma, rows 462-482), by flat backdrop level\n")
    print("| backdrop | " + " | ".join(names) + " |")
    print("| --: |" + " --: |" * len(names))
    for i, b in enumerate(backs):
        print(f"| {b:g} | " + " | ".join(f"{data[n][i]['bar']:g}" for n in names) + " |")
    print("\nSelected pill interior (rows 470-482, Keypad tab)\n")
    print("| backdrop | " + " | ".join(names) + " |")
    print("| --: |" + " --: |" * len(names))
    for i, b in enumerate(backs):
        print(f"| {b:g} | " + " | ".join(f"{data[n][i]['pill']:g}" for n in names) + " |")
    if any(r["colour"][0] != r["colour"][1] or r["colour"][1] != r["colour"][2] for r in next(iter(data.values()))):
        print("\nBar interior per channel (median of each channel), by flat backdrop colour\n")
        print("| backdrop | " + " | ".join(names) + " |")
        print("| --: |" + " --: |" * len(names))
        for i, b in enumerate(backs):
            c = next(iter(data.values()))[i]["colour"]
            print(f"| {c[0]},{c[1]},{c[2]} (luma {b:g}) | " + " | ".join(
                "{:g},{:g},{:g} (luma {:g})".format(*data[n][i]["bar_rgb"], data[n][i]["bar"]) for n in names) + " |")
    if json_out:
        json.dump(data, open(json_out, "w"), indent=1)
    if check:
        stored = json.load(open(check))
        bad = 0
        for n in names:
            for i, r in enumerate(data[n]):
                s = stored[n][i]
                for k in ("bar", "pill"):
                    if abs(r[k] - s[k]) > 0.5:
                        print(f"DIFF {n} backdrop {r['backdrop']} {k}: {s[k]} -> {r[k]}")
                        bad = 1
        print("check:", "FAILED" if bad else "ok")
        return bad
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

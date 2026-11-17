#!/usr/bin/env python
"""Contact sheet for tools/tab_bar_tone.py: the bar crop of each replay at each flat level, one row per replay.

    python tools/tab_bar_tone_montage.py out.png <name>=<replay dir> ...
"""
import csv, sys
from PIL import Image, ImageDraw

CROP = (40, 440, 1130, 650)  # the bar and the search button
SCALE = 0.32


def level_frames(d):
    levels = {}
    for r in csv.DictReader(open(f"{d}/flat.csv")):
        levels.setdefault(int(r["level"]), []).append(int(r["frame"]))
    return [f[-6] for _, f in sorted(levels.items())]


def main(out, args):
    rows = [(a.partition("=")[0], a.partition("=")[2]) for a in args]
    w, h = int((CROP[2] - CROP[0]) * SCALE), int((CROP[3] - CROP[1]) * SCALE)
    n = len(level_frames(rows[0][1]))
    sheet = Image.new("RGB", (110 + n * w, len(rows) * (h + 4)), (20, 20, 20))
    draw = ImageDraw.Draw(sheet)
    for y, (name, d) in enumerate(rows):
        draw.text((4, y * (h + 4) + h // 2), name, fill=(230, 230, 230))
        for x, f in enumerate(level_frames(d)):
            im = Image.open(f"{d}/f{f:05d}.png").convert("RGB").crop(CROP).resize((w, h), Image.LANCZOS)
            sheet.paste(im, (110 + x * w, y * (h + 4)))
    sheet.save(out)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])

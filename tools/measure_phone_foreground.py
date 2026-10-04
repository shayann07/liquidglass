"""Measure the unselected Contacts glyph in the published full-density Phone strips.

Requires Pillow. Bright, nearly neutral connected components in a fixed ROI isolate
the icon from material, text and both selectors. This measures visible pixels, not
layout, finger motion, or the mechanism that transforms the icon.
"""
import argparse
import json
from pathlib import Path

from PIL import Image


def components(points):
    while points:
        pending = [points.pop()]
        found = []
        while pending:
            x, y = pending.pop()
            found.append((x, y))
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    neighbor = (x + dx, y + dy)
                    if neighbor in points:
                        points.remove(neighbor)
                        pending.append(neighbor)
        yield found


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("images", nargs="+", type=Path)
args = parser.parse_args()
measurements = []
for path in args.images:
    with Image.open(path) as original:
        if original.size != (1170, 440):
            parser.error(f"{path}: expected an unscaled 1170x440 strip")
        image = original.convert("RGB")
    for threshold in (160, 200, 230):
        points = set()
        for y in range(190, 305):
            for x in range(400, 560):
                rgb = image.getpixel((x, y))
                if min(rgb) >= threshold and max(rgb) - min(rgb) < 15:
                    points.add((x, y))
        candidates = [c for c in components(points) if len(c) > 100]
        if len(candidates) != 1:
            parser.error(f"{path}: expected one Contacts glyph, got {len(candidates)}")
        glyph = candidates[0]
        xs, ys = zip(*glyph)
        left, top, right, bottom = min(xs), min(ys), max(xs), max(ys)
        measurements.append(dict(
            image=path.name, threshold=threshold,
            bounds=[left, top, right - left + 1, bottom - top + 1],
            center=[(left + right + 1) / 2, (top + bottom + 1) / 2],
            pixels=len(glyph),
        ))
print(json.dumps(measurements, indent=2))

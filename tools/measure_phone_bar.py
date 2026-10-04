"""Measure the unoccluded bar body in decoded IMG_6756 T01/T04 strips (requires Pillow).

Input is a 1170x440 crop at full-frame (0,2092), without resizing. This does not fit
selector outlines, finger gain or timing. The columns deliberately avoid both selectors.
"""
import argparse
import json
from pathlib import Path
from PIL import Image

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("images", nargs="+", type=Path)
args = parser.parse_args()
measurements = []
for path in args.images:
    with Image.open(path) as original:
        if original.size != (1170, 440):
            parser.error(f"{path}: expected an unscaled 1170x440 strip")
        image = original.convert("RGB")
    for threshold in (5, 10, 15):
        edges = []
        for x in (400, 450, 500, 550):
            ys = [y for y in range(130, 410) if max(image.getpixel((x, y))) >= threshold]
            if not ys:
                parser.error(f"{path}: no body at column {x}, threshold {threshold}")
            edges.append(dict(x=x, top=min(ys), bottom=max(ys), height=max(ys)-min(ys)+1))
        measurements.append(dict(image=path.name, threshold=threshold, edges=edges))
print(json.dumps(measurements, indent=2))

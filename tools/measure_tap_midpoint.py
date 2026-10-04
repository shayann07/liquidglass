"""Probe selector edges in the owner's unscaled IMG_6698 crop (Pillow only).

The selected scan ranges avoid labels and neighboring icons. This is a spatial
measurement of an untimed still, not a spring/timing fit or a full contour score.
"""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image


def measure(path):
    with Image.open(path) as source:
        if source.size != (1170, 324):
            raise ValueError("Expected the unscaled 1170x324 IMG_6698 crop")
        image = source.convert("RGB")

    def value(x, y, channel):
        rgb = image.getpixel((x, y))
        return sum(c * w for c, w in zip(rgb, (.2126, .7152, .0722))) if channel == "luma" else rgb[channel]

    vertical, horizontal = [], []
    for channel in ("luma", 0, 1, 2):
        for x in (510, 535, 560, 585, 610, 635):
            gradient = lambda y: value(x, y + 1, channel) - value(x, y, channel)
            top = min(range(80, 98), key=gradient)
            bottom = max(range(240, 257), key=gradient)
            vertical.append(dict(channel=channel, x=x, top=top, bottom=bottom, height=bottom-top))
    for y in (150, 155, 160, 165, 170, 175, 180):
        gradient = lambda x: value(x + 1, y, "luma") - value(x, y, "luma")
        left = min(range(408, 426), key=gradient)
        right = max(range(727, 744), key=gradient)
        horizontal.append(dict(y=y, left=left, right=right, width=right-left, centre=(right+left)/2))
    return dict(
        source=path.name, sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
        dimensions=[1170, 324],
        method="Strongest signed adjacent-pixel gradient; coordinates name the first pixel of each pair. Separation is bottom-top / right-left, with no inclusive +1.",
        limitation="Hand-selected unoccluded cross sections, not complete silhouette bounds. Untimed image; no elapsed motion time is identifiable.",
        vertical=vertical, horizontal=horizontal,
        reference_gate=dict(height_px=172, height_tolerance_px=4, width_px=330,
                            width_tolerance_px=12, travel_phase=.487,
                            note="Rounded authored test gates from these scans and approximate endpoint centres 189/981; not extra measurements."),
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = json.dumps(measure(args.image), indent=2) + "\n"
    if args.output:
        args.output.write_text(result, encoding="utf-8")
    else:
        print(result, end="")

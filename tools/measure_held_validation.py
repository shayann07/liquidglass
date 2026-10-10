"""Audit additional held-shape stills against an already frozen production trace.

Requires Pillow. Inputs are unscaled 1168x194 bar crops of IMG6727/6728/6729,
at (0,108,1168,302) in the existing display images. Geometry only, not HDR
photometry. No pointer timing or independent full-contour claim is made.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
from statistics import median

from PIL import Image


# Fixed windows selected by inspecting the original edges, before model scoring.
WINDOWS = {
    '6727': dict(xs=(260, 270, 280, 290, 300, 310), top=(0, 15), bottom=(182, 193),
                 left=(128, 145), right=(433, 450)),
    '6728': dict(xs=(200, 210, 220, 230, 240, 250), top=(10, 25), bottom=(168, 185),
                 left=(82, 101), right=(380, 399)),
    '6729': dict(xs=(195, 205, 215, 225, 235, 245), top=(14, 30), bottom=(167, 182),
                 left=(78, 95), right=(363, 380)),
}


def measure(path, windows):
    with Image.open(path) as source:
        if source.size != (1168, 194):
            raise ValueError(f'{path}: expected unscaled 1168x194 bar-only crop')
        image = source.convert('RGB')
    vertical, horizontal = [], []
    for channel in range(3):
        for x in windows['xs']:
            gradient = lambda y: image.getpixel((x, y + 1))[channel] - image.getpixel((x, y))[channel]
            top = max(range(*windows['top']), key=lambda y: abs(gradient(y)))
            bottom = max(range(*windows['bottom']), key=lambda y: abs(gradient(y)))
            vertical.append(dict(channel=channel, x=x, top=top, bottom=bottom, height=bottom-top))
        for y in (90, 94, 98, 102):
            gradient = lambda x: image.getpixel((x + 1, y))[channel] - image.getpixel((x, y))[channel]
            left = max(range(*windows['left']), key=lambda x: abs(gradient(x)))
            right = max(range(*windows['right']), key=lambda x: abs(gradient(x)))
            horizontal.append(dict(channel=channel, y=y, left=left, right=right, width=right-left))
    widths = [row['width'] for row in horizontal]
    heights = [row['height'] for row in vertical]
    # Carry the already-declared six-pixel spatial gate from the held calibration;
    # do not widen it when a contaminated image disagrees across probes.
    width_accepted = max(widths) - min(widths) <= 6
    height_accepted = max(heights) - min(heights) <= 6
    return dict(sha256=hashlib.sha256(path.read_bytes()).hexdigest(), windows=windows,
                width=median(widths), height=median(heights),
                widthRange=[min(widths), max(widths)], heightRange=[min(heights), max(heights)],
                widthAccepted=width_accepted, heightAccepted=height_accepted,
                horizontal=horizontal, vertical=vertical)


def audit(directory, trace):
    with trace.open(encoding='utf-8') as source:
        samples = list(csv.DictReader(source))
    if not samples:
        raise ValueError('Production trace is empty')
    results = {}
    for number, windows in WINDOWS.items():
        result = measure(directory / f'reference-{number}-bar.png', windows)
        if result['widthAccepted'] and result['heightAccepted']:
            closest = min(samples, key=lambda row:
                abs(float(row['width']) - result['width']) + abs(float(row['height']) - result['height']))
            result['closestAuthoredSample'] = {key: float(value) for key, value in closest.items()}
            result['withinExistingSixPixelGate'] = (
                abs(float(closest['width']) - result['width']) <= 6 and
                abs(float(closest['height']) - result['height']) <= 6)
        else:
            result['closestAuthoredSample'] = None
            result['withinExistingSixPixelGate'] = None
        results[number] = result
    return dict(
        method='Fixed windows, strongest absolute adjacent-pixel RGB gradient. First-pixel separation; no inclusive+1. Median and full probe range preserved.',
        traceSha256=hashlib.sha256(trace.read_bytes()).hexdigest(),
        sourceModel='820659d frozen production held-travel trace; constants not tuned to these audit results',
        limits='Additional stills, not a timed trajectory. Search over the existing authored speed sweep verifies paired shape reachability only. The frames were historically inspected but did not calibrate the820659d correction. No contour, finger speed, release class or optical identity inferred. IMG6728 side glyph contamination leaves width unresolved.',
        states=results)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('trace', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.directory, args.trace)
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    for number, state in result['states'].items():
        print(number, state['widthRange'], state['heightRange'],
              'gate=' + str(state['withinExistingSixPixelGate']))

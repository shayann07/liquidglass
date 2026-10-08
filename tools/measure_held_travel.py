"""Measure intermediate/strong travel in unscaled IMG6734/6735 bar-only crops.

Pillow only. Input is the 1168x194 crop (0,108,1168,302) of the existing
1168x360 display image. Geometry only: no HDR photometry, timestamps or finger speed.
"""
import argparse
import hashlib
import json
from pathlib import Path
from statistics import median
from PIL import Image


def measure(path, state='strong'):
    with Image.open(path) as source:
        if source.size != (1168, 194):
            raise ValueError('Expected unscaled IMG6734/6735 crop (0,108,1168,302)')
        image = source.convert('RGB')
    def value(x, y, channel):
        rgb = image.getpixel((x, y))
        return sum(a*b for a, b in zip(rgb, (.2126, .7152, .0722))) if channel == 'luma' else rgb[channel]
    vertical, horizontal, bar = [], [], []
    windows = dict(strong=dict(xs=(920, 930, 940, 960, 970, 980), top=(20, 32), bottom=(160, 174),
        left=(785, 820), right=(1095, 1120)),
        intermediate=dict(xs=(690, 700, 710, 720, 730, 740), top=(8, 17), bottom=(177, 186),
        left=(570, 585), right=(895, 910)))[state]
    for channel in ('luma', 0, 1, 2):
        for x in windows['xs']:
            gradient = lambda y: value(x, y+1, channel) - value(x, y, channel)
            top = max(range(*windows['top']), key=gradient)
            bottom = min(range(*windows['bottom']), key=gradient)
            vertical.append(dict(channel=channel, x=x, top=top, bottom=bottom, height=bottom-top))
        for y in (90, 94, 98, 102):
            gradient = lambda x: value(x+1, y, channel) - value(x, y, channel)
            left = max(range(*windows['left']), key=gradient)
            # IMG6734 has a dark boundary then light inside-edge, whereas the terminal
            # rim in IMG6735 transitions back to the dark surrounding page.
            right = (min if state == 'strong' else max)(range(*windows['right']), key=gradient)
            horizontal.append(dict(channel=channel, y=y, left=left, right=right, width=right-left))
        for x in (400, 450, 500, 550):
            gradient = lambda y: value(x, y+1, channel) - value(x, y, channel)
            top = max(range(0, 8), key=gradient)
            bottom = min(range(182, 194-1), key=gradient)
            bar.append(dict(channel=channel, x=x, top=top, bottom=bottom, height=bottom-top))
    return dict(image=path.name, state=state, windows=windows, sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
        method='Fixed unscaled scans; strongest signed adjacent-pixel gradient; first-pixel separation, no inclusive+1. Median over luma/R/G/B probes.',
        limits='Untimed owner travel still. Optical rim/ink makes width less certain; the selected 6px regression tolerance is authored. No pointer speed, peak or temporal calibration claimed.',
        width=median(p['width'] for p in horizontal), height=median(p['height'] for p in vertical),
        barHeight=median(p['height'] for p in bar), horizontal=horizontal, vertical=vertical, bar=bar)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('image', type=Path)
    parser.add_argument('--state', choices=['strong', 'intermediate'], default='strong')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = measure(args.image, args.state)
    args.output.write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({k:result[k] for k in ['width', 'height', 'barHeight']}))

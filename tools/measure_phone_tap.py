"""Measure explicitly annotated edge windows in native Phone bar crops.

Requires NumPy and Pillow. The annotations precede model scoring and are kept
with all RGB probes. These are cross-section edge separations, not a recovered
finger trajectory or full contour. Weak/disagreeing probes remain unresolved.
"""
import argparse
import hashlib
import json
from pathlib import Path
from statistics import median

import numpy as np
from PIL import Image, ImageDraw


def edge(line, window):
    low, high = window
    gradient = np.diff(line.astype(float))
    position = low + int(np.argmax(np.abs(gradient[low:high])))
    return dict(position=position + .5, contrast=float(gradient[position]),
                atWindowEdge=position in (low, high - 1))


def measure(directory, annotations, output):
    specification = json.loads(annotations.read_text(encoding='utf-8-sig'))
    index = {row['file']: row for row in json.loads(
        (directory / 'index.json').read_text(encoding='utf-8-sig'))}
    output.mkdir(parents=True, exist_ok=True)
    rows = []
    for annotation in specification['samples']:
        name = annotation['file']
        source = directory / name
        image = Image.open(source).convert('RGB')
        if image.size != (1170, 260):
            raise ValueError('Expected native 1170x260 crop at source (0,420)')
        pixels = np.asarray(image)
        overlay = image.copy()
        draw = ImageDraw.Draw(overlay)
        probes = []
        for axis, scanlines, sides in (
                ('horizontal', annotation['ys'], ('left', 'right')),
                ('vertical', annotation['xs'], ('top', 'bottom'))):
            for coordinate in scanlines:
                for channel in range(3):
                    line = pixels[coordinate, :, channel] if axis == 'horizontal' else pixels[:, coordinate, channel]
                    values = {side: edge(line, annotation[side]) for side in sides}
                    probes.append(dict(axis=axis, scanline=coordinate, channel=channel, **values,
                        separation=values[sides[1]]['position'] - values[sides[0]]['position']))
                    for value in values.values():
                        x, y = (value['position'], coordinate) if axis == 'horizontal' else (coordinate, value['position'])
                        draw.ellipse((x-1, y-1, x+1, y+1), fill='#ff5ce7' if axis == 'horizontal' else '#44ff88')
        summary = {}
        for axis in ('horizontal', 'vertical'):
            selected = [p for p in probes if p['axis'] == axis]
            values = [p['separation'] for p in selected]
            sides = ('left', 'right') if axis == 'horizontal' else ('top', 'bottom')
            flags = list(annotation.get('inspectionFlags', {}).get(axis, []))
            if max(values) - min(values) > 6:
                flags.append('RGB/scanline separation spread exceeds 6px')
            if any(p[s]['atWindowEdge'] for p in selected for s in sides):
                flags.append('edge lies at search-window boundary')
            if any(abs(p[s]['contrast']) < 2 for p in selected for s in sides):
                flags.append('edge contrast below 2/255')
            summary[axis] = dict(median=median(values), range=[min(values), max(values)],
                edgeMedians={s: median(p[s]['position'] for p in selected) for s in sides},
                usable=not flags, flags=flags)
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        if index[name].get('sha256', digest) != digest:
            raise ValueError(f'{name}: image differs from its decode index')
        rows.append(dict(index[name], sha256=digest,
            annotation=annotation, summary=summary, probes=probes))
        overlay.save(output / name)
    result = dict(sourceSha256=specification['sourceSha256'], crop=[0, 420, 1170, 260],
        method='Strongest absolute adjacent-pixel gradient per RGB channel within inspected fixed windows; edge at pixel-pair midpoint. All probes retained; no subpixel fit.',
        limits='Cross-sections, not complete bounding boxes or full contours. Six-pixel agreement is a measurement quality gate, not a model tolerance. Optical rim and background can be ambiguous. An accepted probe still requires visual overlay inspection. No finger times recovered.',
        samples=rows)
    (output / 'measurements.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    for row in rows:
        h, v = row['summary']['horizontal'], row['summary']['vertical']
        print(row['file'], row['seconds'], h['median'], h['range'], h['usable'],
              v['median'], v['range'], v['usable'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('annotations', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    measure(args.directory, args.annotations, args.output)

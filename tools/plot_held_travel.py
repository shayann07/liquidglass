"""Plot production-controller spatial sweeps against two untimed original silhouettes."""
import argparse
import csv
import json
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np
from PIL import Image

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--trace', type=Path, required=True)
parser.add_argument('--before', type=Path, required=True)
parser.add_argument('--strong', type=Path, required=True)
parser.add_argument('--intermediate', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
strong = json.loads(args.strong.read_text(encoding='utf-8'))
middle = json.loads(args.intermediate.read_text(encoding='utf-8'))
fig = plt.figure(figsize=(11, 6), layout='constrained')
grid = fig.add_gridspec(3, 1, height_ratios=[2.2, 1, 1])
ax = fig.add_subplot(grid[0, 0])
for path, color, style, label in [(args.before, '#92999d', '--', 'Before'),
                                  (args.trace, '#286e94', '-', 'Candidate')]:
    with path.open(encoding='utf-8') as source:
        rows = [{k: float(v) for k, v in r.items()} for r in csv.DictReader(source)]
    for i, duration in enumerate(sorted({r['duration'] for r in rows})):
        points = [r for r in rows if r['duration'] == duration]
        ax.plot([r['width'] / 186 for r in points], [r['height'] / 186 for r in points],
                color=color, linestyle=style, alpha=.65, linewidth=1, label=label if i == 0 else None)
for ref, label, marker in [(strong, 'IMG6735 strong travel', '*'), (middle, 'IMG6734 intermediate', 'D')]:
    ax.scatter([ref['width']/ref['barHeight']], [ref['height']/ref['barHeight']],
               color='#d3622a', marker=marker, s=75, label=label, zorder=3)
ax.set(xlabel='Selector width / bar height', ylabel='Selector height / bar height',
       title='Held travel: joint width/height reachability')
ax.spines[['right', 'top']].set_visible(False)
ax.grid(alpha=.18); ax.legend(fontsize=8, frameon=False, ncol=2)
for row, file, label in [(1, args.intermediate.parent / middle['image'], 'Original intermediate state'),
                         (2, args.strong.parent / strong['image'], 'Original strong-travel state')]:
    view = fig.add_subplot(grid[row, 0])
    with Image.open(file) as source:
        view.imshow(np.asarray(source), interpolation='nearest')
    view.set_title(label, fontsize=9); view.set_axis_off()
fig.text(.5, -.01, 'Curves are computed geometry from authored 0.06–1.5s travel inputs, not native screenshots or measured iOS timing.',
         ha='center', fontsize=8)
fig.savefig(args.output, dpi=170, bbox_inches='tight')

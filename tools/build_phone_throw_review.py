"""Reproduce the partial-cap fit and authored-controller comparison in the throw packet.

Requires NumPy, SciPy, Pillow and Matplotlib. Full peak height is unobservable;
fitting the visible cap must not be reported as measuring its hidden extrema.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image
from scipy.optimize import least_squares
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt


def main(packet):
    specification = json.loads((packet / 'cap-windows.json').read_text())
    fits = []
    for sample in specification['samples']:
        path = packet / sample['file']
        image = Image.open(path).convert('RGB')
        values = np.asarray(image).mean(axis=2)
        low, high = sample['xWindow']
        ys = [y for y in range(*sample['yRange']) if not any(
            a <= y <= b for a, b in sample['excludeRows'])]
        points = []
        for y in ys:
            gradient = np.diff(values[y])
            index = low + int(np.argmin(gradient[low:high]))
            points.append([index + .5, y, float(gradient[index])])
        points = np.asarray(points)
        def predicted(params):
            cx, cy, radius = params
            return cx - np.sqrt(np.maximum(radius * radius - (points[:, 1] - cy)**2, 1e-6))
        result = least_squares(lambda p: predicted(p)-points[:, 0], sample['initial'],
                               bounds=sample['bounds'], loss='soft_l1')
        if not result.success:
            raise RuntimeError(result.message)
        residuals = predicted(result.x)-points[:, 0]
        fits.append(dict(file=sample['file'], seconds=sample['seconds'], pts=sample['pts'],
            sha256=hashlib.sha256(path.read_bytes()).hexdigest(), annotation=sample,
            cx=float(result.x[0]), cy=float(result.x[1]), radius=float(result.x[2]),
            rms=float(np.sqrt(np.mean(residuals**2))), maxError=float(np.max(abs(residuals))),
            points=points.tolist(), residuals=residuals.tolist()))
    with (packet / 'gain-sweep.csv').open() as file:
        rows = [{k: float(v) for k, v in row.items()} for row in csv.DictReader(file)]
    # Same authored input as the paired production regression. No temporal source alignment.
    candidate = [r for r in rows if r['gain'] == 2.25 and r['hold'] == .8 and r['dragDuration'] == .1]
    baseline = [r for r in rows if r['gain'] == 0 and r['hold'] == .8 and r['dragDuration'] == .1]
    observed = fits[0]
    score = lambda r: max(abs(r['width']-242)/8, abs(r['height']/2-observed['radius'])/6)
    selected = min(candidate, key=score)
    old = min(baseline, key=lambda r: abs(r['time']-selected['time']))
    comparison = dict(sourceSha256=specification['sourceSha256'], capFits=fits,
        method='Strongest negative RGB-mean adjacent-pixel gradient in fixed windows; label rows excluded before robust circle fitting. Pixel-pair midpoints, soft_l1 loss. All points and residuals retained.',
        candidate=selected, baselineAtSameAuthoredTime=old, pairedDiagnosticScore=score(selected),
        limits='Native source crop is at (0,420). Circle fit estimates curvature from a partial arc, NOT full body height. Controller example uses an authored .8s hold/.1s two-slot drag; source UP time and force are unknown. Geometry panel aligns centres; original centre overshoot is not matched. No full motion, timing or optical parity claim.')
    (packet / 'comparison.json').write_text(json.dumps(comparison, indent=2)+'\n')
    fig, axes = plt.subplots(2, 2, figsize=(12, 7.5), constrained_layout=True)
    for axis, fit, title in zip(axes[0], fits, ['iOS landing • 2.556667 s', 'iOS rest • 2.990000 s']):
        axis.imshow(Image.open(packet / fit['file']))
        pts = np.asarray(fit['points'])
        axis.plot(pts[:, 0], pts[:, 1], '.', ms=2.5, color='#ff50b9', label='Probed contour')
        ys = np.linspace(pts[:, 1].min(), pts[:, 1].max(), 200)
        xs = fit['cx']-np.sqrt(fit['radius']**2-(ys-fit['cy'])**2)
        axis.plot(xs, ys, color='#60e1fa', lw=1, label='Partial-cap fit')
        axis.set(xlim=(565, 905), ylim=(240, 10), title=title)
        axis.axis('off')
    axis = axes[1, 0]
    for data, label, color in [(baseline, 'Before', '#818793'), (candidate, 'Endpoint compression', '#00798d')]:
        axis.plot([r['time'] for r in data], [r['width'] for r in data], color=color, label=label)
    axis.axhline(242, color='#b72c7f', ls=':', label='Observed landing width')
    axis.set(xlim=(0,.6), xlabel='Authored time after release (s)', ylabel='Body width (px)',
             title='Identical simulated input; original timing unknown')
    axis.legend(fontsize=8)
    axis.grid(alpha=.15)
    axis=axes[1, 1]
    for row, label, color in [(old, 'Before, same instant', '#818793'),
                             (selected, 'Candidate, same instant', '#00798d')]:
        radius=row['height']/2
        spine=(row['width']-row['height'])/2
        a=np.linspace(np.pi/2,3*np.pi/2,160)
        b=np.linspace(-np.pi/2,np.pi/2,160)
        xs=np.r_[radius*np.cos(a)-spine,radius*np.cos(b)+spine]
        ys=np.r_[radius*np.sin(a),radius*np.sin(b)]
        axis.plot(np.r_[xs,xs[0]],np.r_[ys,ys[0]],color=color,label=label)
    pts=np.asarray(observed['points'])
    axis.scatter(pts[:,0]-761.5,pts[:,1]-observed['cy'],s=7,color='#b72c7f',label='Visible source arc')
    axis.set(aspect='equal',title='Controller geometry, centres aligned',xlabel='x (px)',ylabel='y (px)')
    axis.legend(fontsize=8,loc='upper left',bbox_to_anchor=(1,1))
    fig.suptitle('Phone hard landing • measured width, inferred cap curvature',fontsize=14)
    fig.savefig(packet / 'comparison.png',dpi=150)
    plt.close(fig)
    print(json.dumps({k:comparison[k] for k in ['candidate','baselineAtSameAuthoredTime','pairedDiagnosticScore']}))
    print([(f['file'],f['radius'],f['rms']) for f in fits])


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('packet', type=Path)
    main(parser.parse_args().packet)

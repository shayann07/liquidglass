"""Compare frozen Phone tap observations with a separate candidate export.

Keep the earlier baseline immutable. No fitting, time shift or image resampling.
The original cross-sections and model bounds are different measurements.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def compare(baseline, candidate):
    source = baseline / 'baseline-comparison.json'
    trace_path = candidate / 'production-touch-candidate.csv'
    with trace_path.open(encoding='utf-8') as stream:
        trace = [{key: float(value) for key, value in row.items()} for row in csv.DictReader(stream)]
    rows = []
    for observed in json.loads(source.read_text(encoding='utf-8'))['observations']:
        matches = []
        for width in (285, 278, 271):
            for duration in (.04, .08, .116, .16, .24):
                alternatives = [r for r in trace if r['slots'] == observed['slots'] and
                                r['restWidth'] == width and abs(r['pressSeconds'] - duration) < 1e-6]
                matches.append(min(alternatives, key=lambda r: abs(r['phase'] - observed['phase'])))
        nominal = next(r for r in matches if r['restWidth'] == 278 and r['pressSeconds'] == .04)
        rows.append(dict(observed=observed, candidateMatches=matches, nominal=nominal))
    result = dict(
        sourceSha256=sha(source), traceSha256=sha(trace_path), samples=len(trace),
        method='Nearest observed normalized centre phase, unchanged from frozen baseline; no fit.',
        limits='Optical cross-sections versus model bounds, not full contours. The nominal 278x167.5 '
               'body and 40ms contact are authored alternatives, not recovered input. '
               'Short target centre479px remains provisional. Parameters were spatially calibrated '
               'on three of these observations; these are not independent parity validation.',
        observations=rows)
    (candidate / 'comparison.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    return result


def plot(result, candidate):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    plt.style.use('dark_background')
    fig, axes = plt.subplots(1, 2, figsize=(12, 4.8), layout='constrained')
    for ax, dimension in zip(axes, ('Width', 'Height')):
        for i, row in enumerate(result['observations']):
            observed = row['observed']
            lo, hi = observed[f'model{dimension}Range']
            ax.vlines(i-.18, lo, hi, color='#929aa6', linewidth=7, label='Previous alternatives' if i == 0 else None)
            alternatives = [r[dimension.lower()] for r in row['candidateMatches']]
            ax.vlines(i, min(alternatives), max(alternatives), color='#78b9fc', linewidth=7,
                      label='Candidate alternatives' if i == 0 else None)
            ax.plot(i, row['nominal'][dimension.lower()], 'o', color='white', markersize=4,
                    label='Candidate 40ms / middle body' if i == 0 else None)
            lo, hi = observed[f'observed{dimension}Range']
            value = observed[f'observed{dimension}']
            ax.errorbar(i+.18, value, yerr=[[value-lo], [hi-value]], fmt='o', color='#ffcf6b',
                        capsize=4, label='Original RGB probes' if i == 0 else None)
        ax.set_xticks(range(len(result['observations'])), [
            f"{'Long' if r['observed']['slots'] == 2 else 'Short'} {r['observed']['phase']:.0%}"
            for r in result['observations']])
        ax.set_ylabel(f'{dimension} in original source pixels')
        ax.grid(axis='y', alpha=.18)
        ax.legend(fontsize=8)
    fig.suptitle('Coupled tap area: paired shape improves; input and contour uncertainty remain')
    fig.supxlabel('Authored40–240ms contacts and three resting geometries. Ranges are alternatives, not confidence intervals.\n'
                  'Three calibration pairs; spatial diagnostic only. No claim of recovered Apple physics or full parity.', fontsize=9)
    fig.savefig(candidate / 'comparison.png', dpi=150)
    plt.close(fig)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('baseline', type=Path)
    parser.add_argument('candidate', type=Path)
    parser.add_argument('--plot', action='store_true')
    args = parser.parse_args()
    result = compare(args.baseline, args.candidate)
    if args.plot:
        plot(result, args.candidate)
    for row in result['observations']:
        print(row['observed']['seconds'], 'observed', row['observed']['observedWidth'],
              row['observed']['observedHeight'], 'candidate', row['nominal']['width'], row['nominal']['height'])

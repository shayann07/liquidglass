"""Preserve the pre-tuning production comparison for the labelled Phone taps.

The observed values are inspected cross-sections, not complete contours. Phase
alignment compares shape independently of unknown pointer timing. The short
tap's target centre is provisional; no statistical confidence is implied.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path


def compare(directory):
    trace_path = directory / 'production-touch-baseline.csv'
    with trace_path.open(encoding='utf-8') as source:
        trace = [{k:float(v) for k,v in row.items()} for row in csv.DictReader(source)]
    observations = []
    for name, frames, distance, start, target in (
        ('video1-long-first-measurements.json', {'frame-009.png','frame-010.png','frame-019.png'},2,221,748),
        ('video1-short-first-measurements.json', {'frame-013.png'},1,748,479)):
        measurements = json.loads((directory/name).read_text(encoding='utf-8'))
        for row in measurements['samples']:
            if row['file'] not in frames:
                continue
            h, v = row['summary']['horizontal'], row['summary']['vertical']
            if not h['usable'] or not v['usable']:
                raise ValueError('Do not score an unresolved observation')
            centre = sum(h['edgeMedians'].values())/2
            phase = (centre-start)/(target-start)
            matches = []
            for width in (285,278,271):
                for duration in (.04,.08,.116,.16,.24):
                    candidates = [r for r in trace if r['slots']==distance and
                        r['restWidth']==width and abs(r['pressSeconds']-duration)<1e-6]
                    matches.append(min(candidates,key=lambda r:abs(r['phase']-phase)))
            observations.append(dict(source=name, file=row['file'], pts=row['pts'],
                seconds=row['seconds'], slots=distance, phase=phase,
                observedWidth=h['median'], observedWidthRange=h['range'],
                observedHeight=v['median'], observedHeightRange=v['range'],
                modelWidthRange=[min(r['width'] for r in matches),max(r['width'] for r in matches)],
                modelHeightRange=[min(r['height'] for r in matches),max(r['height'] for r in matches)],
                matches=matches))
    result = dict(
        model='f24634d production controller, behavior unchanged from820659d',
        traceSha256=hashlib.sha256(trace_path.read_bytes()).hexdigest(),
        method='Nearest normalized centre phase for each declared fixture/press duration; no parameter optimization.',
        limits='Cross-section observations versus model bounding dimensions, not full contour agreement. Short target centre479px is provisional. Press40/80/116/160/240ms and three baseline body geometries are authored alternatives. Longer contacts mirror adapter hold acquisition at120ms. No original finger times, matched rendering or parity claim.',
        observations=observations)
    (directory/'baseline-comparison.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    return result


def plot(result, directory):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    plt.style.use('dark_background')
    fig,axes=plt.subplots(1,2,figsize=(12,4.8),layout='constrained')
    rows=result['observations']
    labels=[f"{'Long' if r['slots']==2 else 'Short'} {r['phase']:.0%}\n{r['seconds']:.6f}s" for r in rows]
    for ax, dimension in zip(axes,('Width','Height')):
        for i,row in enumerate(rows):
            a,b=row[f'model{dimension}Range']
            ax.vlines(i+.09,a,b,color='#78b9fc',linewidth=9,label='Production alternatives' if i==0 else None)
            low,high=row[f'observed{dimension}Range']
            value=row[f'observed{dimension}']
            ax.errorbar(i-.09,value,yerr=[[value-low],[high-value]],fmt='o',capsize=5,
                color='#ffcf6b',label='Original RGB probes' if i==0 else None)
        ax.set_xticks(range(len(rows)),labels,fontsize=9)
        ax.set_ylabel(f'{dimension} in source pixels')
        ax.grid(axis='y',alpha=.18)
        ax.legend(fontsize=9)
    fig.suptitle('New Phone recordings: unchanged model does not recover these paired shapes',fontsize=14)
    fig.supxlabel('Phase-aligned diagnostic; sampled model alternatives, not a confidence interval.\nCross-sections vs bounds; exact finger timing and full contour remain open.',fontsize=9)
    fig.savefig(directory/'baseline-comparison.png',dpi=150)
    plt.close(fig)


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory',type=Path)
    parser.add_argument('--plot',action='store_true')
    args=parser.parse_args()
    result=compare(args.directory)
    if args.plot:
        plot(result,args.directory)
    for row in result['observations']:
        print(row['seconds'],row['phase'],row['modelWidthRange'],row['modelHeightRange'])

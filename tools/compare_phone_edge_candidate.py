"""Score a frozen leading-edge candidate against consecutive native output.

Uses one alignment at width minimum for the complete series. Right sequence is
calibration; left is the independent direction (not retuned). No source finger-up
time is known. Requires NumPy/Matplotlib; never invokes production or changes data.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np


def main(packet, reference):
    csv_path=packet/'candidate-sweep.csv'
    with csv_path.open() as f:
        all_rows=[{k:float(v) for k,v in row.items()} for row in csv.DictReader(f)]
    selected=[r for r in all_rows if r['gain']==4 and r['recoveryOmega']==14 and r['hold']==.8 and r['dragDuration']==.1]
    peak=min(selected,key=lambda r:r['width'])
    times=np.array([r['time']-peak['time'] for r in selected])
    model=dict(width=np.array([r['width'] for r in selected]),
        radius=np.array([r['height']/2 for r in selected]),
        outwardCentre=np.array([r['cx']-680.5 for r in selected]))
    with (reference.parent/'phone-throw-2026-10-09/gain-sweep.csv').open() as f:
        previous=[{k:float(v) for k,v in row.items()} for row in csv.DictReader(f)]
    previous=[r for r in previous if r['gain']==2.25 and r['hold']==.8 and r['dragDuration']==.1]
    previous_peak=min(previous,key=lambda r:r['width'])
    previous_times=np.array([r['time']-previous_peak['time'] for r in previous])
    previous_model=dict(width=np.array([r['width'] for r in previous]),
        radius=np.array([r['height']/2 for r in previous]),
        outwardCentre=np.array([r['cx']-680.5 for r in previous]))
    result=dict(candidateMinimum=peak,candidateCsvSha256=hashlib.sha256(csv_path.read_bytes()).hexdigest(),directions={})
    fig,axes=plt.subplots(3,2,figsize=(12,9),sharex='col',layout='constrained')
    for column,(direction,rest,sign) in enumerate([('right',741.5,1),('left',217.5,-1)]):
        source_path=reference/f'{direction}-measurements.json'
        rows=[r for r in json.loads(source_path.read_text())['measurements'] if r['usable']]
        minimum=min(rows,key=lambda r:r['width'])
        rows=[r for r in rows if r['seconds']>=minimum['seconds']]
        t=np.array([r['seconds']-minimum['seconds'] for r in rows])
        observed=dict(width=np.array([r['width'] for r in rows]),radius=np.array([r['cap']['radius'] for r in rows]),
            outwardCentre=np.array([sign*(r['centre']-rest) for r in rows]))
        metrics={}
        aligned=[]
        for i,row in enumerate(rows):
            aligned.append(dict(file=row['file'],pts=row['pts'],relativeTime=float(t[i]),
                observed={k:float(v[i]) for k,v in observed.items()},
                model={k:float(np.interp(t[i],times,v)) for k,v in model.items()}))
        for row_index,key in enumerate(observed):
            ax=axes[row_index,column]
            ax.plot(t,observed[key],'.-',color='#b72c7f',label='Original output')
            mask=(times>=0)&(times<=t[-1])
            ax.plot(times[mask],model[key][mask],color='#00798d',label='Leading-edge candidate')
            old_mask=(previous_times>=0)&(previous_times<=t[-1])
            ax.plot(previous_times[old_mask],previous_model[key][old_mask],color='#92969c',label='Prior endpoint model')
            ax.set_ylabel(key+' (px)');ax.grid(alpha=.2)
            errors={}
            for name,tt,yy in [('candidate',times,model[key]),('previous',previous_times,previous_model[key])]:
                d=np.interp(t,tt,yy)-observed[key]
                errors[name]=dict(rms=float(np.sqrt(np.mean(d*d))),maxAbsolute=float(max(abs(d))))
            metrics[key]=errors
        axes[0,column].set_title(direction.title()+(' • calibration' if column==0 else ' • independent direction'))
        axes[-1,column].set_xlabel('Seconds after each width minimum')
        result['directions'][direction]=dict(sourceSha256=hashlib.sha256(source_path.read_bytes()).hexdigest(),
            metrics=metrics,aligned=aligned)
        if direction=='right':
            # Compare visible edge samples directly. One resting-x origin and an independently
            # observed bar centre; no per-frame shape/centre fitting to source points.
            contour_frames=[]
            all_errors={'candidate':[],'previous':[]}
            fig_contour,panels=plt.subplots(1,4,figsize=(12,4),layout='constrained')
            for i,source_row in enumerate(rows):
                pts=np.asarray(source_row['cap']['points'],dtype=float)
                bar_centre=float(np.median([(p['top']['position']+p['bottom']['position'])/2
                    for p in source_row['bar']['probes']]))
                assert source_row['bar']['usable']
                predictions={};errors={}
                for name,tt,data in [('candidate',times,model),('previous',previous_times,previous_model)]:
                    width=float(np.interp(t[i],tt,data['width']))
                    radius=float(np.interp(t[i],tt,data['radius']))
                    centre=rest+float(np.interp(t[i],tt,data['outwardCentre']))
                    if np.any(abs(pts[:,1]-bar_centre)>radius):
                        raise ValueError('Source scanline is outside model body; do not invent an edge')
                    predicted=centre-(width/2-radius)-np.sqrt(radius*radius-(pts[:,1]-bar_centre)**2)
                    difference=predicted-pts[:,0]
                    all_errors[name].extend(difference.tolist())
                    predictions[name]=predicted.tolist()
                    errors[name]=dict(rms=float(np.sqrt(np.mean(difference**2))),maxAbsolute=float(max(abs(difference))))
                contour_frames.append(dict(file=source_row['file'],pts=source_row['pts'],
                    barCentreY=bar_centre,observedPoints=pts[:,:2].tolist(),predictions=predictions,errors=errors))
                if i in (0,6,12,18):
                    ax=panels[[0,6,12,18].index(i)]
                    ax.scatter(pts[:,0],pts[:,1],s=9,color='#b72c7f',label='Observed edge')
                    for name,color in [('previous','#92969c'),('candidate','#00798d')]:
                        pred=np.array(predictions[name])
                        for n,group in enumerate((pts[:,1]<148,pts[:,1]>183)):
                            ax.plot(pred[group],pts[group,1],color=color,label=name if n==0 else None)
                    ax.set(title=f"{source_row['seconds']:.6f}s",xlabel='Source x (px)',ylabel='Source crop y (px)',aspect='equal')
                    ax.invert_yaxis();ax.grid(alpha=.15)
            panels[0].legend(fontsize=7)
            fig_contour.suptitle('Visible original left arc • same native rows and one baseline origin')
            fig_contour.savefig(packet/'visible-contours.png',dpi=150);plt.close(fig_contour)
            result['visibleRightContour']=dict(method='At every retained RGB-mean edge point, intersect the horizontal model capsule with the same native row. Source bar-centre125.5px from independent accepted bar probes; source resting x-centre741.5px fixed for the full sequence. Width minimum aligned once, as above. No per-frame x/y fitting.',
                points=len(all_errors['candidate']),frames=contour_frames,
                metrics={name:dict(rms=float(np.sqrt(np.mean(np.array(e)**2))),maxAbsolute=float(max(abs(np.array(e))))) for name,e in all_errors.items()},
                limits='Visible left arc only in one rightward episode. Controller capsule reconstructed from sampled bounds; not rendered optical pixels, hidden silhouette, entire source contour, or recovered input. Left episode lacks accepted independent bar-centre probes and is not given this direct-contour score.')
    axes[0,0].legend(fontsize=8)
    fig.suptitle('Continuous landing recovery • same authored input, one alignment per trace')
    fig.savefig(packet/'comparison.png',dpi=150);plt.close(fig)
    result['limits']='Authored .8s hold/.1s drag; no recovered finger-up or force. Right calibration and left direction check share the same recording. Compare optical cross-sections/partial arcs with model bounds/radius. Left exact rest is unproven;217.5px is last observed centre. Width/edge improved; early cap-curvature error persists. This is not full contour/optical or1:1 motion parity.'
    (packet/'comparison.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({direction:d['metrics'] for direction,d in result['directions'].items()},indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('packet',type=Path)
    parser.add_argument('--reference',type=Path,default=Path('review/atlas/phone-landing-sequence-2026-10-09'))
    args=parser.parse_args()
    main(args.packet,args.reference)

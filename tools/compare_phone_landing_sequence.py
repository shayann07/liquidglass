"""Compare one frozen authored input with consecutive observed landing output.

Aligns each width minimum once; never rematches individual frames. The alignment
is diagnostic, not recovered finger timing. Reads existing traces, no production
execution or tuning. Requires NumPy and Matplotlib.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
from statistics import median

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np


def main(measurements, sweep, output):
    output.mkdir(parents=True,exist_ok=True)
    source=json.loads(measurements.read_text())
    rows=source['measurements']
    assert all(r['usable'] and r['cap']['usable'] and r['bar']['usable'] for r in rows)
    # Earliest observed minimum, no subframe timing invented on the two-frame plateau.
    peak=min(rows,key=lambda r:r['width'])
    endpoint=median(r['centre'] for r in rows[-8:])
    source_time=np.array([r['seconds']-peak['seconds'] for r in rows])
    values=dict(width=np.array([r['width'] for r in rows]),
        centreOffset=np.array([r['centre']-endpoint for r in rows]),
        radius=np.array([r['cap']['radius'] for r in rows]))
    with sweep.open() as file:
        all_rows=[{k:float(v) for k,v in row.items()} for row in csv.DictReader(file)]
    comparisons=[]
    fig,axes=plt.subplots(3,1,figsize=(10,8),sharex=True,layout='constrained')
    keys=['width','centreOffset','radius']
    visible_ranges={k:list(values[k]) for k in keys}
    for ax,key,label in zip(axes,keys,['Width (px)','Centre relative to rest (px)','Partial-cap / model radius (px)']):
        ax.plot(source_time,values[key],'.-',label='Original output',color='#b72c7f')
        ax.set_ylabel(label);ax.grid(alpha=.2)
    for gain,label,color in [(0,'Prior response','#858b94'),(2.25,'Endpoint compression','#00798d')]:
        trace=[r for r in all_rows if r['gain']==gain and r['hold']==.8 and r['dragDuration']==.1]
        model_peak=min(trace,key=lambda r:r['width'])
        t=np.array([r['time']-model_peak['time'] for r in trace])
        data=dict(width=np.array([r['width'] for r in trace]),
            centreOffset=np.array([r['cx']-680.5 for r in trace]),
            radius=np.array([r['height']/2 for r in trace]))
        mask=(source_time>=max(0,t[0])) & (source_time<=t[-1])
        errors={}
        aligned=[]
        for i in np.flatnonzero(mask):
            aligned.append(dict(sourceFile=rows[i]['file'],relativeTime=float(source_time[i]),
                original={k:float(values[k][i]) for k in keys},
                model={k:float(np.interp(source_time[i],t,data[k])) for k in keys}))
        for ax,key in zip(axes,keys):
            ax.plot(t,data[key],label=label,color=color)
            visible_ranges[key].extend(data[key][(t>=-.04)&(t<=.47)])
            difference=np.interp(source_time[mask],t,data[key])-values[key][mask]
            errors[key]=dict(rms=float(np.sqrt(np.mean(difference**2))),
                            maxAbsolute=float(np.max(abs(difference))))
        comparisons.append(dict(gain=gain,hold=.8,dragDuration=.1,modelMinimum=model_peak,
            samples=len(aligned),errors=errors,aligned=aligned))
    sensitivity=[]
    for gain in (0,2.25):
        for hold in (.06,.8):
            for duration in (.06,.1,.24):
                trace=[r for r in all_rows if r['gain']==gain and r['hold']==hold and r['dragDuration']==duration]
                minimum=min(trace,key=lambda r:r['width'])
                times=np.array([r['time']-minimum['time'] for r in trace])
                model=dict(width=np.array([r['width'] for r in trace]),
                    centreOffset=np.array([r['cx']-680.5 for r in trace]),
                    radius=np.array([r['height']/2 for r in trace]))
                mask=(source_time>=0)&(source_time<=times[-1])
                rms={k:float(np.sqrt(np.mean((np.interp(source_time[mask],times,model[k])-values[k][mask])**2))) for k in keys}
                sensitivity.append(dict(gain=gain,hold=hold,dragDuration=duration,minimum=minimum,
                    rms=rms,maxPostMinimumCentreOffset=float(max(model['centreOffset'][times>=0]))))
    for ax,key in zip(axes,keys):
        low,high=min(visible_ranges[key]),max(visible_ranges[key])
        pad=max(2,(high-low)*.08)
        ax.set_ylim(low-pad,high+pad)
    axes[0].legend()
    axes[-1].set(xlim=(-.04,.47),xlabel='Time relative to each width minimum (s); one alignment per trace')
    fig.suptitle('Late landing: one fixed authored input, continuous output comparison')
    fig.savefig(output/'comparison.png',dpi=150);plt.close(fig)
    result=dict(sourceSha256=source['sourceSha256'],
        measurementsSha256=hashlib.sha256(measurements.read_bytes()).hexdigest(),
        sweepSha256=hashlib.sha256(sweep.read_bytes()).hexdigest(),
        originalMinimum={k:peak[k] for k in ['file','pts','seconds','width','centre']},
        originalRestCentre=endpoint,originalMaximumOffset=max(r['centre']-endpoint for r in rows),
        comparisons=comparisons,inputSensitivity=sensitivity,
        limits='Diagnostic, not a pass gate or new fitted rate. Original optical cross-sections/partial-circle fits versus controller bounds/radius. Each trace aligned once at its width minimum, not at finger-up; no per-frame rematching or time scaling. Source window begins during landing. The input .8s hold/.1s two-slot drag is authored; source force and pointer duration are unknown. Centre origin removed independently using rest. Circle-fit radius depends on visible arc window. No optical image/complete contour parity established.')
    (output/'comparison.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(dict(originalMinimum=result['originalMinimum'],
        restCentre=endpoint,maxOffset=result['originalMaximumOffset'],
        diagnostics=[{k:c[k] for k in ['gain','modelMinimum','samples','errors']} for c in comparisons]),indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('measurements',type=Path)
    parser.add_argument('sweep',type=Path)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    main(args.measurements,args.sweep,args.output)

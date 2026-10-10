"""Inspect a consecutive late-landing window without inventing pointer timestamps.

Uses existing native-PTS crops, checks their hashes, retains every RGB probe and
partial-cap residual, and plots observed output geometry. NumPy/SciPy/Pillow/
Matplotlib required. Crops/overlays stay local until visually reviewed.
"""
import argparse
import hashlib
import json
from pathlib import Path
from statistics import median

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np
from PIL import Image, ImageDraw
from scipy.optimize import least_squares


def edge(line, window, sign):
    low, high = window
    gradient = np.diff(line.astype(float))
    position = low + int(np.argmax(sign * gradient[low:high]))
    return dict(position=position + .5, contrast=float(gradient[position]),
                atWindowEdge=position in (low, high - 1))


def main(directory, output, annotations=None):
    if output.exists() and any(output.iterdir()):
        raise ValueError('Use a new output directory; retain earlier attempts')
    output.mkdir(parents=True, exist_ok=True)
    index = json.loads((directory/'index.json').read_text())
    decode = json.loads((directory/'decode.json').read_text())
    # Fixed before model scoring, from the already inspected late-landing sheets.
    specification = dict(firstFrame=78, lastFrame=108, ys=[125,130,135],
        left=[580,670], right=[872,890], capX=[580,707], capY=[59,195,3],
        excludedRows=[[148,183]], barXs=[340,350,360], barTop=[25,55], barBottom=[200,235],
        contrastFloor=2, crossSectionSpread=6, capRmsLimit=1.5, capMaxError=4,
        capInitial=[735,125,100], capBounds=[[650,100,60],[850,150,160]])
    if annotations:
        specification.update(json.loads(annotations.read_text()))
    cap_side=specification.get('capSide',-1)
    first,last=specification['firstFrame'],specification['lastFrame']
    if cap_side not in (-1,1) or not 1<=first<=last<=len(index):
        raise ValueError('Invalid cap side or frame interval')
    rows, panels = [], []
    for entry in index[first-1:last]:
        path = directory/entry['file']
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if digest != entry['sha256']:
            raise ValueError(f'{path.name}: decode hash mismatch')
        original = Image.open(path).convert('RGB')
        if original.size != (1170,260):
            raise ValueError('Expected native crop at (0,420)')
        pixels = np.asarray(original)
        overlay = original.copy()
        draw = ImageDraw.Draw(overlay)
        probes, bar_probes = [], []
        for y in specification['ys']:
            for channel in range(3):
                left=edge(pixels[y,:,channel],specification['left'],-1)
                right=edge(pixels[y,:,channel],specification['right'],1)
                probes.append(dict(y=y, channel=channel, left=left, right=right,
                    width=right['position']-left['position'],
                    centre=(left['position']+right['position'])/2))
                for point in (left, right):
                    x=point['position']
                    draw.ellipse((x-1,y-1,x+1,y+1),fill='#ff50b9')
        for x in specification['barXs']:
            for channel in range(3):
                top=edge(pixels[:,x,channel],specification['barTop'],1)
                bottom=edge(pixels[:,x,channel],specification['barBottom'],-1)
                bar_probes.append(dict(x=x,channel=channel,top=top,bottom=bottom,
                    height=bottom['position']-top['position']))
        flags=list(specification.get('manualRejections',{}).get(entry['file'],[]))
        widths=[p['width'] for p in probes]
        if max(widths)-min(widths)>specification['crossSectionSpread']:
            flags.append('cross-section RGB/row spread exceeds quality gate')
        if any(p[s]['atWindowEdge'] for p in probes for s in ('left','right')):
            flags.append('cross-section at search boundary')
        if any(abs(p[s]['contrast'])<specification['contrastFloor'] for p in probes for s in ('left','right')):
            flags.append('cross-section contrast below quality floor')
        mean=pixels.astype(float).mean(axis=2)
        points=[]
        for y in range(*specification['capY']):
            if any(a<=y<=b for a,b in specification['excludedRows']):
                continue
            sample=edge(mean[y],specification['capX'],cap_side)
            points.append([sample['position'],y,sample['contrast'],sample['atWindowEdge']])
            draw.ellipse((sample['position']-1,y-1,sample['position']+1,y+1),fill='#60e1fa')
        pts=np.asarray(points,dtype=float)
        def predicted(p):
            return p[0]+cap_side*np.sqrt(np.maximum(p[2]**2-(pts[:,1]-p[1])**2,1e-6))
        fit=least_squares(lambda p:predicted(p)-pts[:,0],specification['capInitial'],
            bounds=specification['capBounds'],loss='soft_l1')
        residual=predicted(fit.x)-pts[:,0]
        rms=float(np.sqrt(np.mean(residual**2)))
        cap_flags=[]
        if not fit.success: cap_flags.append('fit failed')
        if rms>specification['capRmsLimit'] or max(abs(residual))>specification['capMaxError']:
            cap_flags.append('cap residual exceeds authored quality gate')
        if any(abs(p[2])<specification['contrastFloor'] or p[3] for p in points):
            cap_flags.append('weak or boundary cap probe')
        cap=dict(cx=float(fit.x[0]),cy=float(fit.x[1]),radius=float(fit.x[2]),rms=rms,
            maxError=float(max(abs(residual))),points=points,residuals=residual.tolist(),
            usable=not cap_flags,flags=cap_flags)
        bar_flags=[]
        if any(abs(p[s]['contrast'])<specification['contrastFloor'] or p[s]['atWindowEdge'] for p in bar_probes for s in ('top','bottom')):
            bar_flags.append('weak or boundary bar probe')
        heights=[p['height'] for p in bar_probes]
        if max(heights)-min(heights)>specification['crossSectionSpread']:
            bar_flags.append('bar RGB/column spread exceeds quality gate')
        row=dict(entry,probes=probes,width=median(widths),widthRange=[min(widths),max(widths)],
            centre=median(p['centre'] for p in probes),usable=not flags,flags=flags,cap=cap,
            bar=dict(probes=bar_probes,height=median(heights),range=[min(heights),max(heights)],
                usable=not bar_flags,flags=bar_flags))
        rows.append(row)
        panel=Image.new('RGB',(350,256),'#14212b')
        crop_left=specification.get('panelLeft',560)
        panel.paste(overlay.crop((crop_left,10,crop_left+350,240)),(0,26))
        ImageDraw.Draw(panel).text((5,5),f"{entry['file']} {entry['seconds']:.6f}s",fill='white')
        panels.append(panel)
    for page in range((len(panels)+15)//16):
        selected=panels[page*16:(page+1)*16]
        sheet=Image.new('RGB',(1400,256*((len(selected)+3)//4)),'#14212b')
        for i,panel in enumerate(selected):sheet.paste(panel,((i%4)*350,(i//4)*256))
        sheet.save(output/f'overlay-{page+1}.png')
    result=dict(sourceSha256=decode['sourceSha256'],crop=decode['crop'],timeBase=decode['timeBase'],
        annotation=specification,measurements=rows,
        limits='Observed output across consecutive native frames, not finger-up timing. Width and centre are optical cross-sections, not full contours or mass centres. Circle fit estimates a visible partial arc, not hidden height. Fixed windows and authored quality gates precede model scoring. Every accepted probe still requires overlay inspection; failures remain explicit.')
    (output/'measurements.json').write_text(json.dumps(result,indent=2)+'\n')
    fig,axes=plt.subplots(4,1,figsize=(10,9),sharex=True,layout='constrained')
    for ax,key,label in zip(axes,['width','centre','cap','bar'],
            ['Cross-section width (px)','Cross-section centre (px)','Partial-cap radius (px)','Surrounding bar height (px)']):
        ys=[r[key] if key in ('width','centre') else r[key]['radius' if key=='cap' else 'height'] for r in rows]
        valid=[r['usable'] if key in ('width','centre') else r[key]['usable'] for r in rows]
        ax.plot([r['seconds'] for r in rows],[v if ok else np.nan for v,ok in zip(ys,valid)],'.-',color='#00798d')
        ax.scatter([r['seconds'] for r,ok in zip(rows,valid) if not ok],[v for v,ok in zip(ys,valid) if not ok],marker='x',color='#be3879',label='Rejected probe')
        ax.set_ylabel(label);ax.grid(alpha=.2)
        if not all(valid):ax.legend()
    axes[-1].set_xlabel('Original native presentation time (s), not time after finger-up')
    fig.suptitle('Original Phone landing — consecutive output geometry; no fitted controller')
    fig.savefig(output/'sequence.png',dpi=150);plt.close(fig)
    print(json.dumps(dict(frames=len(rows),usableWidths=sum(r['usable'] for r in rows),
        usableCaps=sum(r['cap']['usable'] for r in rows),usableBars=sum(r['bar']['usable'] for r in rows),
        widths=[min(r['width'] for r in rows),max(r['width'] for r in rows)],
        centres=[min(r['centre'] for r in rows),max(r['centre'] for r in rows)],
        caps=[min(r['cap']['radius'] for r in rows),max(r['cap']['radius'] for r in rows)]),indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory',type=Path)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--annotations',type=Path)
    args=parser.parse_args()
    main(args.directory,args.output,args.annotations)

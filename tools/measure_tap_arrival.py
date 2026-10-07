"""Measure paired width/height in two unscaled original WhatsApp arrival stills.

Pillow only. Fixed scan windows exclude the Chats glyph left of the pill and the
outer bar edge. These stills supply shape ratios, not elapsed time or peak extrema.
"""
import argparse
import hashlib
import json
from pathlib import Path
from statistics import median
from PIL import Image


def measure(path):
    with Image.open(path) as source:
        if source.size != (1170, 324):
            raise ValueError("Expected original unscaled1170x324 crop")
        image=source.convert("RGB")
    def value(x,y,channel):
        rgb=image.getpixel((x,y))
        return sum(a*b for a,b in zip(rgb,(.2126,.7152,.0722))) if channel=="luma" else rgb[channel]
    vertical,horizontal=[],[]
    for channel in ("luma",0,1,2):
        for x in (970,980,985,990):
            gradient=lambda y:value(x,y+1,channel)-value(x,y,channel)
            top=min(range(80,93),key=gradient)
            bottom=max(range(245,256),key=gradient)
            vertical.append(dict(channel=channel,x=x,top=top,bottom=bottom,height=bottom-top))
        for y in (160,165,170,175):
            gradient=lambda x:value(x+1,y,channel)-value(x,y,channel)
            left=min(range(855,900),key=gradient)
            right=max(range(1080,1104),key=gradient)
            horizontal.append(dict(channel=channel,y=y,left=left,right=right,width=right-left))
    return dict(image=path.name,sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                width=median(r["width"] for r in horizontal),
                height=median(r["height"] for r in vertical),vertical=vertical,horizontal=horizontal)


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("compressed",type=Path)
    parser.add_argument("settled",type=Path)
    parser.add_argument("--output",type=Path,required=True)
    parser.add_argument("--trace",type=Path,help="Optional production controller CSV from GlassTapPhaseReferenceTest")
    parser.add_argument("--before-trace",type=Path,help="Optional previous production trace with the same fixture")
    parser.add_argument("--plot",type=Path,help="Optional figure; requires --trace, matplotlib and numpy")
    args=parser.parse_args()
    compressed,settled=measure(args.compressed),measure(args.settled)
    result=dict(method="Strongest signed adjacent-pixel gradient; first pixel of each pair; median over4channels x4unoccluded scans per axis. Separation excludes inclusive+1.",
                limitation="Two untimed stills in owner's arrival sequence; neither a measured timing curve nor proof of peak extrema. Windows chosen by inspection exclude neighboring ink and the outside bar rim.",
                compressed=compressed,settled=settled,
                widthRatio=compressed["width"]/settled["width"],heightRatio=compressed["height"]/settled["height"])
    args.output.write_text(json.dumps(result,indent=2)+'\n',encoding="utf-8")
    print(json.dumps({k:v for k,v in result.items() if k.endswith("Ratio")},indent=2))
    if args.plot:
        if not args.trace:
            parser.error("--plot requires --trace")
        import csv
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        import numpy as np
        rows=[{k:float(v) for k,v in r.items()} for r in csv.DictReader(args.trace.open())]
        rest=rows[-1]
        arrival=[r for r in rows if r["phase"]>.97]
        fig=plt.figure(figsize=(10,5),layout="constrained")
        grid=fig.add_gridspec(2,2,width_ratios=[1.25,1])
        ax=fig.add_subplot(grid[:,0])
        if args.before_trace:
            with args.before_trace.open() as source:
                before=[{k:float(v) for k,v in r.items()} for r in csv.DictReader(source)]
            before_rest=before[-1]
            before_arrival=[r for r in before if r["phase"]>.97]
            ax.plot([r["width"]/before_rest["width"] for r in before_arrival],
                    [r["height"]/before_rest["height"] for r in before_arrival],
                    label="Previous production path",color="#888888",linestyle="--")
        ax.plot([r["width"]/rest["width"] for r in arrival],
                [r["height"]/rest["height"] for r in arrival],label="Production arrival path",color="#246b9d")
        ax.scatter([result["widthRatio"]],[result["heightRatio"]],marker="*",s=150,
                   color="#cf622b",label="Original paired shape")
        ax.scatter([1],[1],color="black",s=24,label="Settled shape")
        ax.set(xlim=(.90,1.07),ylim=(.99,1.1),xlabel="Width / settled width",ylabel="Height / settled height",
               title="Arrival exchanges width for height")
        ax.legend(frameon=False,fontsize=9)
        ax.spines[["right","top"]].set_visible(False)
        ax.grid(alpha=.2)
        for i,(path,label) in enumerate(((args.compressed,"Original compressed state"),(args.settled,"Original settled state"))):
            image=Image.open(path)
            view=fig.add_subplot(grid[i,1])
            view.imshow(np.asarray(image)[70:270,820:1115],interpolation="nearest")
            view.set_title(label,fontsize=10)
            view.set_axis_off()
        fig.suptitle("Resting-tap recoil: spatial comparison, not a timing fit")
        fig.text(.5,-.02,"Original IMG6694 / IMG6701. Same fixed crop; no per-frame scaling. Curve: computed production geometry, not a rendered screenshot.",
                 ha="center",fontsize=8)
        fig.savefig(args.plot,dpi=160,bbox_inches="tight")

"""Compare visible Contacts geometry with independently measured surrounding-bar geometry.

No motion coefficients are fitted. The resting glyph is the first T04 sample;
the bar's measured height and centre predict subsequent vertical glyph geometry.
Requires Pillow; --plot additionally requires matplotlib and numpy. Decode inputs
with the command documented in review/ATLAS-STABILIZATION.md.
"""
import argparse
import hashlib
import json
from pathlib import Path
from statistics import median

from PIL import Image
from measure_phone_foreground import measure_glyph


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--bar", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--plot", type=Path)
    parser.add_argument("--exclude-frames", type=int, nargs="*", default=[],
                        help="One-based samples whose glyph is visibly covered by the selector")
    args = parser.parse_args()
    bars = [r for r in json.loads(args.bar.read_text())["measurements"] if r["threshold"] == 10]
    rows, baselines = [], {}
    for number, bar in enumerate(bars, 1):
        path = args.directory / bar["image"]
        if hashlib.sha256(path.read_bytes()).hexdigest() != bar["sha256"]:
            parser.error(f"{path}: hash differs from the independently measured bar frame")
        with Image.open(path) as original:
            image = original.convert("RGB")
        for threshold in (160, 200, 230):
            row = dict(image=path.name, sha256=bar["sha256"], pts=bar["pts"],
                       seconds=bar["seconds"], threshold=threshold)
            try:
                if number in args.exclude_frames:
                    raise ValueError("manual visual rejection: selector covers part of the glyph")
                glyph = measure_glyph(image, threshold)
                left, top, width, height = glyph["bounds"]
                # A partial bright fragment under the travelling selector is not a glyph.
                # Preserve rejections, rather than filling gaps or fitting them as deformation.
                if glyph["pixels"] < 1800 or not (55 <= width <= 90 and 55 <= height <= 90):
                    raise ValueError("component is incomplete or outside the broad glyph envelope")
                if not bar["majority"]:
                    raise ValueError("surrounding bar has no majority boundary estimate")
                if number == 1:
                    baselines[threshold] = glyph, bar
                base_glyph, base_bar = baselines[threshold]
                scale_y = bar["height"] / base_bar["height"]
                # Bar edges are integer pixel indices. Glyph centre uses pixel extents;
                # convert the independently measured bar centre to that same convention.
                base_centre = base_bar["centre_y"] + .5
                bar_centre = bar["centre_y"] + .5
                predicted_y = bar_centre + (base_glyph["center"][1] - base_centre) * scale_y
                predicted_height = base_glyph["bounds"][3] * scale_y
                row.update(glyph, status="measured", barHeight=bar["height"],
                           barCentre=bar_centre, predictedCentreY=predicted_y,
                           predictedHeight=predicted_height,
                           centreError= glyph["center"][1] - predicted_y,
                           heightError=height - predicted_height,
                           anchoredCentreError=glyph["center"][1] - base_glyph["center"][1])
            except ValueError as error:
                if number == 1:
                    parser.error(f"Resting baseline must be measurable: {error}")
                row.update(status="rejected", reason=str(error))
            rows.append(row)
    valid = [r for r in rows if r["status"] == "measured"]
    # Report all accepted threshold/frame probes, not only the selected extreme frame.
    summary = dict(frames=len(bars), probes=len(rows), accepted=len(valid), rejected=len(rows)-len(valid),
                   acceptedFrames=len({r["image"] for r in valid}))
    for key in ("centreError", "heightError", "anchoredCentreError"):
        errors = sorted(abs(r[key]) for r in valid)
        summary[key] = dict(medianAbs=median(errors), maxAbs=max(errors))
    result = dict(source="IMG_6756.MP4", time_base="1/600", crop=[0,2092,1170,440],
                  barEvidence=args.bar.name, summary=summary,
                  visuallyExcludedFrames=args.exclude_frames,
                  method="One bright neutral connected component in fixed Contacts ROI; 3 thresholds. Vertical prediction uses only first-frame glyph and independently measured bar height/centre; no fitted motion coefficients.",
                  limitation="Visible output only, not input timing or proof of Apple's compositor. Selector-obscured/ambiguous fragments are rejected and retained in the record. X drift is measured but not predicted. A matched vertical envelope alone does not prove pixel identity.",
                  measurements=rows)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))
    if args.plot:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        import numpy as np
        fig = plt.figure(figsize=(11, 6), layout="constrained")
        grid = fig.add_gridspec(2, 5, height_ratios=[2, 1])
        ax_y = fig.add_subplot(grid[0, :3])
        ax_h = fig.add_subplot(grid[0, 3:])
        for threshold in (160, 200, 230):
            trace = [r for r in rows if r["threshold"] == threshold]
            t = [r["seconds"] for r in trace]
            observed = lambda key: [r[key] if r["status"] == "measured" else np.nan for r in trace]
            ax_y.plot(t, [p[1] if isinstance(p,list) else np.nan for p in observed("center")],
                      linewidth=1, label=f"Glyph threshold {threshold}")
            ax_h.plot(t, [b[3] if isinstance(b,list) else np.nan for b in observed("bounds")], linewidth=1)
            if threshold == 200:
                ax_y.plot(t, observed("predictedCentreY"), "k--", linewidth=1.3, label="From bar geometry")
                ax_h.plot(t, observed("predictedHeight"), "k--", linewidth=1.3)
        ax_y.axhline(baselines[200][0]["center"][1], color="gray", linestyle=":", label="Anchored glyph")
        ax_y.set(title="Visible icon centre", ylabel="Y in original crop (px)")
        ax_h.set(title="Visible icon height", ylabel="Height (px)")
        ax_y.legend(frameon=False, fontsize=8)
        for ax in (ax_y, ax_h):
            ax.set_xlabel("Original video time (seconds)")
            ax.spines[["right", "top"]].set_visible(False)
            ax.grid(alpha=.2)
        for column, number in enumerate((1, 4, 33, 41, 47)):
            ax = fig.add_subplot(grid[1, column])
            bar = bars[number-1]
            with Image.open(args.directory / bar["image"]) as im:
                ax.imshow(np.asarray(im)[190:305,400:560], interpolation="nearest")
            ax.set_title(f"{bar['seconds']:.3f}s", fontsize=10)
            ax.set_axis_off()
        fig.suptitle("Original iOS T04: ordinary icon geometry follows the surrounding bar")
        fig.text(.5, -.03, f"{len(valid)}/{len(rows)} usable glyph probes; gaps retain selector-obscured/rejected samples. Bottom: original crop pixels. No input trace inferred.",
                 ha="center", fontsize=9)
        fig.savefig(args.plot, dpi=160, bbox_inches="tight")


if __name__ == "__main__":
    main()

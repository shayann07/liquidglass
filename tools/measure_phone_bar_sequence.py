"""Measure the surrounding Phone bar across T04, excluding the travelling selector by voting.

Requires Pillow. Input: unscaled 1170x440 original crops and FFmpeg showinfo log;
see the exact decode command in review/ATLAS-STABILIZATION.md. Coordinates are
relative to full-frame crop (0,2092). This measures output, not finger input.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
from PIL import Image

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("directory", type=Path)
parser.add_argument("--output", type=Path)
parser.add_argument("--plot", type=Path, help="Optional research figure (requires matplotlib)")
args = parser.parse_args()
frames = sorted(args.directory.glob("frame-*.png"))
timestamps = re.findall(r"n:\s*(\d+)\s+pts:\s*(\d+)\s+pts_time:([\d.]+)",
                       (args.directory / "decode.log").read_text(errors="replace"))
if not frames or len(frames) != len(timestamps):
    parser.error("Frame files must match the showinfo entries one for one")
rows = []
for path, (index, pts, time) in zip(frames, timestamps):
    with Image.open(path) as source:
        if source.size != (1170, 440):
            parser.error(f"{path}: expected 1170x440, with no resizing")
        image = source.convert("RGB")
    for threshold in (5, 10, 15):
        top, bottom = [], []
        for x in range(200, 811, 5):
            ys = [y for y in range(130, 410) if max(image.getpixel((x, y))) >= threshold]
            if not ys:
                parser.error(f"{path}: no body at x={x}, threshold={threshold}")
            top.append(min(ys))
            bottom.append(max(ys))
        t, tn = Counter(top).most_common(1)[0]
        b, bn = Counter(bottom).most_common(1)[0]
        rows.append(dict(image=path.name, sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                         pts=int(pts), seconds=float(time), threshold=threshold,
                         top=t, bottom=b, height=b-t+1, centre_y=(t+b)/2,
                         top_votes=tn, bottom_votes=bn, columns=len(top),
                         majority=tn > len(top)/2 and bn > len(bottom)/2))
result = dict(source="IMG_6756.MP4", time_base="1/600", crop=[0,2092,1170,440],
              method="Modal first/last threshold crossing at 123 columns, x=200..810 step5, y=130..409. Top and bottom voted independently. Selector occlusion is rejected only when the surrounding bar has a strict majority for both edges.",
              limitation="Sparse output samples, not the complete motion envelope or input trajectory. Majority agreement does not identify optical warping versus layout movement.",
              measurements=rows)
text = json.dumps(result, indent=2) + "\n"
if args.output:
    args.output.write_text(text, encoding="utf-8")
else:
    print(text, end="")
if args.plot:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    fig, axes = plt.subplots(1, 2, figsize=(10, 3.7), layout="constrained")
    for threshold in (5, 10, 15):
        trace = [r for r in rows if r["threshold"] == threshold and r["majority"]]
        times = [r["seconds"] for r in trace]
        axes[0].plot(times, [r["height"] for r in trace], label=f"threshold {threshold}")
        axes[1].plot(times, [r["centre_y"] - 283.5 for r in trace])
    axes[0].set(title="Surrounding bar height", ylabel="Pixels in original capture")
    axes[1].set(title="Visible bar centre", ylabel="Pixels from resting centre")
    axes[0].legend(frameon=False, fontsize=8)
    for ax in axes:
        ax.set_xlabel("Original video time (seconds)")
        ax.spines[["right", "top"]].set_visible(False)
        ax.grid(alpha=.2)
    fig.suptitle("Original iOS T04: press, prolonged drag, then release")
    fig.text(.5, -.06, "47 native-timestamp samples. Output geometry only; finger positions and input gain are not identified.",
             ha="center", fontsize=9)
    fig.savefig(args.plot, dpi=160, bbox_inches="tight")

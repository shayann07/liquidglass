"""Plot the deterministic Kotlin controller trace; no iOS timing is inferred.

Run after GlassCalmSelectorTest, with matplotlib installed (verified with 3.11.2):
  python tools/plot_selector_travel.py
"""
import csv
from collections import defaultdict
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "liquidglass/build/reports/atlas/selector-travel.csv"
OUTPUT = ROOT / "review/atlas/selector-travel.png"
groups = defaultdict(list)
with SOURCE.open(newline="", encoding="utf-8") as stream:
    for row in csv.DictReader(stream):
        groups[(row["gesture"], int(row["target"]))].append(row)

fig, axes = plt.subplots(2, 2, figsize=(10, 6), sharex=True, layout="constrained")
for column, gesture in enumerate(("held", "throw")):
    for target, color, label in ((2, "#21828c", "1 slot"), (3, "#9463bf", "2 slots")):
        rows = groups[(gesture, target)]
        times = [float(row["time"]) for row in rows]
        axes[0, column].plot(times, [float(row["width"]) for row in rows], color=color, label=label)
        axes[1, column].plot(times, [float(row["height"]) / 64 for row in rows], color=color)
    axes[0, column].set_title("Held travel" if gesture == "held" else "Released travel")
    axes[0, column].legend(frameon=False)
    axes[1, column].axhspan(221 / 186, 229 / 186, color="#aaaaaa", alpha=.22,
                          label="Original settled height ±4px")
    axes[1, column].axhline(1.23, color="#bb5555", linestyle="--", linewidth=1)
    if column == 0:
        axes[1, column].legend(frameon=False, fontsize=8, loc="lower right")
    axes[1, column].set_xlabel("Seconds from travel / release")
    axes[1, column].set_ylim(.85, 1.26)
    for row in range(2):
        axes[row, column].set_xlim(0, 1.2)
        axes[row, column].grid(alpha=.15)
        axes[row, column].spines[["top", "right"]].set_visible(False)
axes[0, 0].set_ylabel("Selector width (px)")
axes[1, 0].set_ylabel("Selector height / bar height")
fig.suptitle("Calm selector: distance changes the shape; release does not re-press it", fontsize=13)
fig.text(.5, -.035, "Authored controller, 360 × 64px bar. Reference band constrains settled height only, not motion timing.",
         ha="center", fontsize=9)
fig.savefig(OUTPUT, dpi=160, bbox_inches="tight")
print(OUTPUT)

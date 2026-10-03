"""Plot the production controller's tap traces; rates are authored, not recovered iOS timing."""
import csv
from collections import defaultdict
from pathlib import Path
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

root = Path(__file__).resolve().parents[1]
source = root / "liquidglass/build/reports/astra/rest-tap-calm.csv"
traces = defaultdict(list)
with source.open(newline="", encoding="utf-8") as stream:
    for row in csv.DictReader(stream):
        traces[int(row["slots"])].append({key: float(value) for key, value in row.items()})

fig, axes = plt.subplots(1, 2, figsize=(10, 4), layout="constrained")
for slots, rows in traces.items():
    rest = rows[0]["width"]
    time = [row["t"] for row in rows]
    width = [row["width"] / rest for row in rows]
    for ax in axes:
        ax.plot(time, width, label=f"{slots} slot{'s' if slots != 1 else ''}")
        ax.axhline(1, color="#777777", alpha=.25, linewidth=.5)
        ax.spines[["right", "top"]].set_visible(False)
        ax.grid(alpha=.15)
        ax.set_xlabel("Seconds after selection")
axes[0].set(title="Travel deformation", xlim=(0, .6), ylabel="Width / resting width")
axes[0].legend(frameon=False)
axes[1].set(title="Arrival and recovery (detail)", xlim=(.2, .6), ylim=(.9, 1.1))
fig.suptitle("A longer trip deforms more; the shape compresses and settles on arrival")
fig.text(.5, -.06, "Production Calm controller, 1150 × 186px / 5-slot fixture. Authored dynamics; no Apple timing fit.",
         ha="center", fontsize=9)
output = root / "review/atlas/selector-arrival.png"
fig.savefig(output, dpi=160, bbox_inches="tight")
print(output)

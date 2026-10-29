"""Score the four reported #124 defect windows, retaining every sampled frame.

Counts are diagnostic proxies, not automatic visual acceptance: green accent content and
legitimate dispersion can also satisfy a mask. Original iOS frames are decoded to sRGB
using the shared reference helper; replay images are already SDR. No images are exported.
"""
import argparse
import importlib.util
import json
from pathlib import Path
import numpy as np
from PIL import Image


def counts(rgb):
    r, g, b = np.moveaxis(rgb.astype(np.int16), -1, 0)
    return {
        "green": int(((g > r + 15) & (g > b + 15)).sum()),
        "neon": int(((g > 100) & (r < 30) & (b < 30)).sum()),
        "crushed": int((((r == 0) | (b == 0)) & (g > 20)).sum()),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-root", type=Path, required=True)
    parser.add_argument("--replays", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location("reference", args.reference_root / "tools/reference-replay/refraction.py")
    reference = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(reference)
    windows = [("docs1", .87, 1.02), ("docs1", 21.30, 21.43),
               ("JGEY2190", 4.67, 4.82), ("JGEY2190", 40.48, 40.63)]
    results = []
    for recording, start, end in windows:
        rows = []
        for label in ("base", "candidate"):
            for frame in range(round(start * 60), round(end * 60) + 1):
                path = args.replays / (recording + "-" + label) / f"f{frame:05d}.png"
                with Image.open(path) as image:
                    rgb = np.asarray(image.convert("RGB"))[470:631]
                rows.append(dict(source=label, frame=frame, seconds=frame / 60, **counts(rgb)))
        video = args.reference_root / reference.VIDEO[recording]
        for seconds, rgb in reference.ios_frames(str(video), start, end - start + .02):
            if seconds <= end + .001:
                rows.append(dict(source="ios", seconds=seconds,
                                 **counts(rgb[470-reference.Y0:631-reference.Y0])))
        summary = {}
        for label in ("base", "candidate", "ios"):
            selected = [r for r in rows if r["source"] == label]
            if not selected:
                raise ValueError(f"No frames for {recording}/{label}")
            summary[label] = {"frames": len(selected),
                              **{key + "_peak": max(r[key] for r in selected)
                                 for key in ("green", "neon", "crushed")}}
        results.append(dict(recording=recording, window=[start, end], summary=summary, frames=rows))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({"roi_scene_rows_inclusive": [470, 630],
                                      "windows": results}, indent=2) + "\n", encoding="utf-8")
    for result in results:
        print(result["recording"], result["window"], result["summary"])


if __name__ == "__main__":
    main()

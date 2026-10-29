"""Compare two complete PhoneReplay outputs from the same input script.

Usage: python tools/compare_motion_replays.py BASE CANDIDATE --output result.json
Returns nonzero for any pixel/trace mismatch or incomplete output. Input-script
hashes and source identity must be recorded alongside the result by the caller.
"""
import argparse
import csv
import json
from pathlib import Path

import numpy as np
from PIL import Image


def compare(base, candidate):
    files = sorted(p.name for p in base.glob("f*.png"))
    other = sorted(p.name for p in candidate.glob("f*.png"))
    if not files or files != other:
        raise ValueError("Missing frames or unequal frame sets")
    if files != [f"f{i:05d}.png" for i in range(len(files))]:
        raise ValueError("Non-contiguous frame sequence")
    traces = []
    for folder in (base, candidate):
        with (folder / "trace.csv").open() as stream:
            traces.append(list(csv.DictReader(stream)))
    if any(len(t) != len(files) for t in traces):
        raise ValueError("Frame/trace count mismatch")
    byte_identical = 0
    pixel_identical = 0
    differences = []
    for name in files:
        left, right = base / name, candidate / name
        if left.read_bytes() == right.read_bytes():
            byte_identical += 1
            pixel_identical += 1
            continue
        with Image.open(left) as im:
            a = np.asarray(im.convert("RGBA"), dtype=np.int16)
        with Image.open(right) as im:
            b = np.asarray(im.convert("RGBA"), dtype=np.int16)
        if a.shape != b.shape:
            raise ValueError("Unequal image dimensions: " + name)
        delta = np.abs(a - b)
        if not delta.any():
            pixel_identical += 1
        else:
            differences.append(dict(frame=name, rgbaMAE=float(delta.mean()), max=int(delta.max())))
    trace_delta = {}
    if traces[0][0].keys() != traces[1][0].keys():
        raise ValueError("Unequal trace columns")
    for key in traces[0][0]:
        pairs = [(x[key], y[key]) for x, y in zip(*traces)]
        if all(x == y for x, y in pairs):
            trace_delta[key] = 0
        else:
            try:
                values = [abs(float(x) - float(y)) for x, y in pairs]
                if not np.isfinite(values).all():
                    raise ValueError("Non-finite trace values")
                trace_delta[key] = max(values)
            except ValueError:
                trace_delta[key] = sum(x != y for x, y in pairs)
    return dict(frames=len(files), byteIdenticalFrames=byte_identical,
                pixelIdenticalFrames=pixel_identical, traceMaxDifferences=trace_delta,
                differentFrames=differences,
                passed=pixel_identical == len(files) and not any(trace_delta.values()))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    result = compare(args.base, args.candidate)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({k: v for k, v in result.items() if k != "differentFrames"}))
    raise SystemExit(0 if result["passed"] else 1)

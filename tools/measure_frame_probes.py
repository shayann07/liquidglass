"""Read exact decoded frame indices and PTS; report RGB probes without exporting images.

For derived SDR clips, source time is clip PTS / slow factor + source start.
This does not decode HDR originals or infer touch timing. Decode sequentially because
OpenCV random frame seeking is unreliable for the variable-rate reference clips.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

import cv2


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("video", type=Path)
    parser.add_argument("--frames", required=True, help="Comma-separated zero-based indices")
    parser.add_argument("--points", required=True, help="Semicolon-separated x,y coordinates")
    parser.add_argument("--source-start", type=float, required=True)
    parser.add_argument("--slow-factor", type=float, default=1)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    indices = {int(n) for n in args.frames.split(",")}
    points = [tuple(map(int, point.split(","))) for point in args.points.split(";")]
    if not indices or min(indices) < 0 or args.slow_factor <= 0:
        parser.error("Nonnegative frame indices and a positive slow factor are required")
    metadata = json.loads(subprocess.check_output([
        "ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries",
        "frame=best_effort_timestamp_time", "-of", "json", str(args.video),
    ]))
    pts = [float(frame["best_effort_timestamp_time"]) for frame in metadata["frames"]]
    capture = cv2.VideoCapture(str(args.video))
    samples = []
    count = 0
    try:
        while True:
            ok, frame = capture.read()
            if not ok:
                break
            if count in indices:
                probes = []
                for x, y in points:
                    if not (0 <= x < frame.shape[1] and 0 <= y < frame.shape[0]):
                        raise ValueError(f"Probe {(x, y)} is outside the decoded frame")
                    rgb = frame[y, x, ::-1].tolist()
                    probes.append({"x": x, "y": y, "rgb": rgb,
                                   "luma_601": round(sum(a*b for a, b in zip(rgb, (.299, .587, .114))), 3)})
                samples.append({"frame": count, "clip_pts_seconds": pts[count],
                                "source_seconds": args.source_start + pts[count] / args.slow_factor,
                                "probes": probes})
            count += 1
    finally:
        capture.release()
    if count != len(pts) or {s["frame"] for s in samples} != indices:
        raise ValueError(f"Incomplete decode: {count}/{len(pts)} frames; requested {sorted(indices)}")
    with args.video.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    result = {"video_name": args.video.name, "sha256": digest,
              "decoded_frames": count, "source_start_seconds": args.source_start,
              "slow_factor": args.slow_factor, "method": "Sequential OpenCV SDR decode; ffprobe frame PTS",
              "samples": samples}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"Measured {len(samples)} frames, {len(points)} probes each -> {args.output}")


if __name__ == "__main__":
    main()

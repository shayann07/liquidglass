"""Index the owner's new 1170x702 Phone recordings without guessing finger events.

Requires FFmpeg, FFprobe and NumPy. Decodes native frames once into a bounded
memory buffer; only timestamps and coarse visible-ink signals are saved.
The proposed stable-ink runs guide manual inspection, not motion fitting.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import re
import subprocess

import numpy as np


def catalogue(video, output):
    output.mkdir(parents=True, exist_ok=True)
    probe = json.loads(subprocess.check_output([
        'ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_streams',
        '-of', 'json', str(video)], text=True))['streams'][0]
    if (probe['width'], probe['height']) != (1170, 702):
        raise ValueError('This inspection ROI is specific to the supplied 1170x702 Phone videos')
    log_path = output / 'decode.log'
    command = ['ffmpeg', '-hide_banner', '-copyts', '-i', str(video), '-an',
        '-vf', 'zscale=t=linear:npl=100,format=gbrpf32le,'
        'zscale=p=bt709:t=iec61966-2-1:m=bt709:r=full,format=rgb24,crop=1170:260:0:420,showinfo',
        '-fps_mode', 'passthrough', '-f', 'rawvideo', '-pix_fmt', 'rgb24', 'pipe:1']
    counts = []
    with log_path.open('wb') as log:
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=log)
        try:
            frame_bytes = 1170 * 260 * 3
            while True:
                frame = process.stdout.read(frame_bytes)
                if not frame:
                    break
                if len(frame) != frame_bytes:
                    raise ValueError('Truncated raw frame')
                image = np.frombuffer(frame, dtype=np.uint8).reshape(260, 1170, 3)
                scores = []
                for left, right in ((140, 295), (400, 555), (665, 820)):
                    patch = image[65:145, left:right].astype(np.int16)
                    blue = (patch[:, :, 2] - patch[:, :, 0] >= 50) & (
                        patch[:, :, 2] - patch[:, :, 1] >= 15) & (patch[:, :, 2] >= 80)
                    scores.append(int(blue.sum()))
                order = np.argsort(scores)
                winner = int(order[-1])
                margin = (scores[winner] - scores[int(order[-2])]) / max(scores[winner], 1)
                # Uncertain frames are retained, never turned into a resting selector.
                candidate = winner if scores[winner] >= 200 and margin >= .35 else -1
                counts.append((scores, candidate, margin))
            code = process.wait()
            if code:
                raise RuntimeError(f'FFmpeg exited {code}; see {log_path}')
        finally:
            process.stdout.close()
            if process.poll() is None:
                process.terminate()
                process.wait()
    timestamps = re.findall(r'n:\s*(\d+)\s+pts:\s*(-?\d+)\s+pts_time:([\d.-]+)',
                            log_path.read_text(encoding='utf-8', errors='replace'))
    if len(timestamps) != len(counts) or not counts:
        raise ValueError('Decoded frames and native timestamps must correspond one to one')
    previous = None
    records = []
    for index, ((n, pts, seconds), (scores, candidate, margin)) in enumerate(zip(timestamps, counts)):
        if int(n) != index or (previous is not None and int(pts) <= previous):
            raise ValueError('Nonmonotonic or misaligned decoded frame timestamps')
        previous = int(pts)
        records.append(dict(frame=index, pts=int(pts), seconds=float(seconds),
                            calls=scores[0], contacts=scores[1], keypad=scores[2],
                            candidate=candidate, margin=round(margin, 6)))
    with (output / 'ink-signals.csv').open('w', newline='', encoding='utf-8') as out:
        writer = csv.DictWriter(out, fieldnames=records[0].keys())
        writer.writeheader()
        writer.writerows(records)
    runs = []
    start = 0
    for end in range(1, len(records) + 1):
        if end < len(records) and records[end]['candidate'] == records[start]['candidate']:
            continue
        a, b = records[start], records[end - 1]
        duration = b['seconds'] - a['seconds']
        if a['candidate'] >= 0 and duration >= .12:
            runs.append(dict(candidate=('Calls', 'Contacts', 'Keypad')[a['candidate']],
                             startFrame=start, endFrame=end-1, start=a['seconds'], end=b['seconds'],
                             duration=round(duration, 6)))
        start = end
    manifest = dict(sourceName=video.name, sourceSha256=hashlib.sha256(video.read_bytes()).hexdigest(),
        metadataFrames=int(probe['nb_frames']), decodedFrames=len(records), timeBase=probe['time_base'],
        firstPts=records[0]['pts'], lastPts=records[-1]['pts'], crop=[0,420,1170,260],
        command=command,
        method='Blue visible-ink count in fixed icon ROIs; B-R>=50, B-G>=15, B>=80. Candidate requires200pixels and35% dominance; runs require120ms of consistent candidate.',
        limits='Proposals only. Refraction can distort/duplicate ink, so these are not finger events, target selection, body centres, silhouettes or verified gestures. No CFR resampling. All uncertain frames remain in CSV.',
        stableInkRuns=runs)
    (output / 'catalogue.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(f'{video.name}: {len(records)} decoded native frames; metadata {probe["nb_frames"]}; '
          f'{len(runs)} stable-ink runs for visual inspection')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('video', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    catalogue(args.video, args.output)

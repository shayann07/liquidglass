"""Decode a bounded native-PTS Phone episode; never resample its frame rate.

Outputs can contain private background text. Keep them under .local until
individual crops have been inspected. Requires FFmpeg, FFprobe and Pillow.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

from PIL import Image, ImageDraw


def decode(video, start, duration, output):
    if start < 0 or not 0 < duration <= 10:
        raise ValueError('Use a nonnegative start and a bounded episode up to 10 seconds')
    if output.exists() and any(output.iterdir()):
        raise ValueError('Use a new output directory; do not overwrite a previous decode')
    output.mkdir(parents=True, exist_ok=True)
    stream = json.loads(subprocess.check_output(['ffprobe', '-v', 'error',
        '-select_streams', 'v:0', '-show_streams', '-of', 'json', str(video)], text=True))['streams'][0]
    if (stream['width'], stream['height'], stream['time_base']) != (1170,702,'1/600'):
        raise ValueError('This decoder targets the supplied Phone sources and their time base')
    # Trim the filter stream on ORIGINAL timestamps. Output -t with -copyts would
    # compare the duration with absolute PTS and can silently encode zero frames.
    command = ['ffmpeg', '-hide_banner', '-copyts', '-ss', str(start), '-i', str(video),
        '-an', '-vf', f'trim=start={start}:end={start+duration},zscale=t=linear:npl=100,format=gbrpf32le,'
        'zscale=p=bt709:t=iec61966-2-1:m=bt709:r=full,format=rgb24,crop=1170:260:0:420,showinfo',
        '-fps_mode', 'passthrough', str(output / 'frame-%03d.png')]
    with (output / 'decode.log').open('wb') as log:
        subprocess.run(command, stdout=log, stderr=log, check=True)
    times = re.findall(r'n:\s*(\d+)\s+pts:\s*(-?\d+)\s+pts_time:([\d.-]+)',
        (output / 'decode.log').read_text(encoding='utf-8', errors='replace'))
    images = sorted(output.glob('frame-*.png'))
    if not times or len(times) != len(images):
        raise ValueError('Native timestamps and PNGs must correspond one to one')
    rows = []
    for i, (path, (n, pts, seconds)) in enumerate(zip(images, times)):
        if int(n) != i or (rows and int(pts) <= rows[-1]['pts']):
            raise ValueError('Frame indexing or timestamp order mismatch')
        rows.append(dict(file=path.name, pts=int(pts), seconds=float(seconds),
            sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
    (output / 'index.json').write_text(json.dumps(rows,indent=2)+'\n', encoding='utf-8')
    (output / 'decode.json').write_text(json.dumps(dict(
        sourceSha256=hashlib.sha256(video.read_bytes()).hexdigest(), command=command,
        source=video.name, crop=[0,420,1170,260], timeBase='1/600', frames=len(rows),
        firstPts=rows[0]['pts'], lastPts=rows[-1]['pts']),indent=2)+'\n', encoding='utf-8')
    for page in range((len(rows)+23)//24):
        group = rows[page*24:(page+1)*24]
        sheet = Image.new('RGB',(1755,150*((len(group)+2)//3)),'#14212b')
        draw = ImageDraw.Draw(sheet)
        for i,row in enumerate(group):
            x,y=(i%3)*585,(i//3)*150
            with Image.open(output/row['file']) as image:
                sheet.paste(image.resize((585,130)),(x,y+20))
            draw.text((x+6,y+4),f"{row['file']}  {row['seconds']:.6f}s",fill='white')
        sheet.save(output/f'sheet-{page+1}.png')
    print(f'{video.name}: {len(rows)} native frames {rows[0]["seconds"]}..{rows[-1]["seconds"]}s')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('video', type=Path)
    parser.add_argument('--start', type=float, required=True)
    parser.add_argument('--duration', type=float, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    decode(args.video,args.start,args.duration,args.output)

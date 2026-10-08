"""Arrange app-owned Atlas phase snapshots in event order, preserving their pixel scale.

Requires Pillow. This is a contact sheet of separate gesture replays, not a video.
Actual event-to-render-start and render submission times are displayed beside each image.
"""
import argparse
import csv
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('directory', type=Path)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--font', default='DejaVuSans.ttf')
args = parser.parse_args()
try:
    font = ImageFont.truetype(args.font, 16)
except OSError:
    font = ImageFont.load_default()
entries = []
last_input = None
with (args.directory / 'events.csv').open(encoding='utf-8') as source:
    for row in csv.DictReader(source):
        if row['event'] != 'capture':
            last_input = float(row['elapsed_ms'])
            continue
        gap = float(row['elapsed_ms']) - last_input if last_input is not None else None
        with Image.open(args.directory / (row['label'] + '.png')) as image:
            entries.append((row, gap, image.convert('RGB')))
if not entries:
    raise ValueError('No phase captures in events.csv')
width = max(image.width for _, _, image in entries)
height = max(image.height for _, _, image in entries) + 52
columns = min(4, len(entries))
rows = (len(entries) + columns - 1) // columns
sheet = Image.new('RGB', (width * columns, height * rows + 46), '#101820')
draw = ImageDraw.Draw(sheet)
draw.text((12, 10), 'Native Atlas: independent gesture replays, one snapshot per phase', font=font, fill='white')
for index, (row, gap, image) in enumerate(entries):
    x, y = (index % columns) * width, (index // columns) * height + 46
    draw.text((x + 8, y + 4), row['label'], font=font, fill='white')
    timing = ('Rest' if gap is None else f'After input {gap:.1f}ms') + f" | render {float(row['render_ms']):.1f}ms"
    draw.text((x + 8, y + 27), timing, font=font, fill='#bbdad8')
    sheet.paste(image, (x, y + 52))
args.output.parent.mkdir(parents=True, exist_ok=True)
sheet.save(args.output)
print(f'{len(entries)} phase snapshots; {sheet.width}x{sheet.height}; source pixels unscaled')

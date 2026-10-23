#!/usr/bin/env python3
"""Export the approved pixel SVG directly to the game's 40-frame texture.
Requires Python 3.10+, Pillow and Inkscape. --check never writes production files.
"""
from pathlib import Path
import argparse
import copy
import io
import json
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from PIL import Image, ImageChops, ImageStat

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SRC = HERE / 'manka_talisman.svg'
TARGET = ROOT / 'src/main/resources/assets/maid_weapon/textures/item/manka_talisman.png'
NS = '{http://www.w3.org/2000/svg}'
FRAMES = 40


def render_frame(source, frame):
    root = copy.deepcopy(source)
    for parent in root.iter():
        assert parent.tag not in [NS + t for t in ('image', 'script', 'foreignObject', 'filter')]
        for key, value in parent.attrib.items():
            assert 'href' not in key and 'data:' not in value
        for animation in list(parent):
            kind = animation.tag.removeprefix(NS)
            if kind not in ('animate', 'animateTransform'):
                continue
            values = animation.attrib['values'].split(';')
            assert len(values) == FRAMES
            value = values[frame]
            if kind == 'animateTransform':
                parent.set('transform', f"{animation.attrib['type']}({value})")
            else:
                parent.set(animation.attrib['attributeName'], value)
            parent.remove(animation)
    with tempfile.TemporaryDirectory(prefix='manka-frame-') as temp:
        svg, png = Path(temp)/'frame.svg', Path(temp)/'frame.png'
        svg.write_bytes(ET.tostring(root))
        subprocess.run(['inkscape', str(svg), '--export-type=png',
                        '--export-width=32', '--export-height=32',
                        f'--export-filename={png}'], check=True, capture_output=True)
        image = Image.open(io.BytesIO(png.read_bytes())).convert('RGBA')
    assert image.size == (32, 32)
    assert {value for _, value in image.getchannel('A').getcolors()} == {0, 255}, 'Pixel edges must stay crisp'
    assert all(image.getchannel('A').crop(box).getextrema()[1] == 0
               for box in [(0,0,32,1),(0,31,32,32),(0,0,1,32),(31,0,32,32)])
    return image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--preview', action='store_true', help='Also write the offline GIF in art/danmaku/previews')
    args = parser.parse_args()
    source = ET.parse(SRC).getroot()
    assert source.get('viewBox') == '0 0 32 32'
    frames = [render_frame(source, i) for i in range(FRAMES)]
    assert len({frame.tobytes() for frame in frames}) > 10, 'Animation must visibly change'
    def delta(a, b):
        return sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 4
    adjacent = [delta(frames[i], frames[i+1]) for i in range(FRAMES-1)]
    assert delta(frames[-1], frames[0]) <= max(adjacent), 'Loop seam exceeds normal motion'
    sprite = Image.new('RGBA', (32, 32*FRAMES))
    for index, frame in enumerate(frames):
        sprite.paste(frame, (0, index*32))
    metadata = {'animation': {'frametime': 2, 'interpolate': False}}
    meta_path = TARGET.with_suffix('.png.mcmeta')
    if args.check:
        with Image.open(TARGET) as existing:
            assert existing.size == sprite.size and existing.convert('RGBA').tobytes() == sprite.tobytes()
        assert json.loads(meta_path.read_text()) == metadata
    else:
        sprite.save(TARGET, compress_level=9)
        meta_path.write_text(json.dumps(metadata, indent=2)+'\n', encoding='utf-8')
    if args.preview:
        preview = []
        for frame in frames:
            image = Image.new('RGBA', (32, 32), '#252130')
            image.alpha_composite(frame)
            preview.append(image.convert('RGB').resize((256, 256), Image.Resampling.NEAREST))
        palette = Image.new('RGB', (256, 256*FRAMES))
        for i, image in enumerate(preview):
            palette.paste(image, (0, i*256))
        palette = palette.quantize(colors=256)
        preview = [image.quantize(palette=palette, dither=Image.Dither.NONE) for image in preview]
        preview[0].save(HERE/'previews/manka_item_pixel_loop.gif', save_all=True,
                        append_images=preview[1:], duration=100, loop=0, disposal=2)
    print('MANKA_PIXEL_PASS: 40 frames / 32x1280 / 2 ticks / transparent pixel edges / loop seam'
          + (' / reproduced every pixel' if args.check else ' / exported to game resource'))


if __name__ == '__main__':
    main()

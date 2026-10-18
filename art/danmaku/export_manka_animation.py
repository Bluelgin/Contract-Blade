#!/usr/bin/env python3
"""Rebuild animated Manka Talisman PNG from hand-drawn SVG (no AI images).
Requires Pillow and CairoSVG. Run from anywhere; writes next to this script.
"""
from pathlib import Path
import copy
import io
import json
import xml.etree.ElementTree as ET
import cairosvg
from PIL import Image, ImageChops

HERE = Path(__file__).resolve().parent
SRC = HERE / 'manka_talisman.svg'
NS = '{http://www.w3.org/2000/svg}'
FRAMES = 40

def render_frame(source, frame):
    root = copy.deepcopy(source)
    for parent in root.iter():
        for animation in list(parent):
            kind = animation.tag.removeprefix(NS)
            if kind not in ('animate', 'animateTransform'):
                continue
            values = animation.attrib['values'].split(';')
            if len(values) != FRAMES:
                raise ValueError(f'Expected {FRAMES} SVG animation steps, got {len(values)}')
            value = values[frame]
            if kind == 'animateTransform':
                parent.set('transform', f"{animation.attrib['type']}({value})")
            else:
                parent.set(animation.attrib['attributeName'], value)
            parent.remove(animation)
    png = cairosvg.svg2png(bytestring=ET.tostring(root), output_width=160, output_height=160)
    return Image.open(io.BytesIO(png)).convert('RGBA').resize((32, 32), Image.Resampling.LANCZOS)

def main():
    source = ET.parse(SRC).getroot()
    frames = [render_frame(source, i) for i in range(FRAMES)]
    assert all(ImageChops.difference(frames[i], frames[(i+1) % FRAMES]).getbbox()
               for i in range(FRAMES)), 'Animation contains duplicate adjacent frames'
    sprite = Image.new('RGBA', (32, 32 * FRAMES), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        sprite.paste(frame, (0, index * 32))
    # Indexed PNG reduces final size; preserves timing, motion and 32px readability.
    sprite.quantize(colors=256, method=Image.Quantize.FASTOCTREE,
                    dither=Image.Dither.NONE).save(HERE / 'manka_talisman.png',
                                                  optimize=True, compress_level=9)
    (HERE / 'manka_talisman.png.mcmeta').write_text(
        json.dumps({'animation': {'frametime': 2, 'interpolate': False}}, indent=2) + '\n',
        encoding='utf-8')
    print('Generated 40 frames / 32x1280 / 2 ticks per frame')

if __name__ == '__main__':
    main()

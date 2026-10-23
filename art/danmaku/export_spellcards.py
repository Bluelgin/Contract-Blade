#!/usr/bin/env python3
"""Export/verify approved original SVG assets. No Java or item-animation writes.

Python 3.10+, Pillow 12.3.0, Inkscape 1.2.2.
python art/danmaku/export_spellcards.py [--check]
--check renders in a temporary directory and compares decoded RGBA pixels.
"""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from PIL import Image, ImageChops, ImageStat

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
NS = '{http://www.w3.org/2000/svg}'


def validate_source(path, size):
    root = ET.parse(path).getroot()
    assert root.tag == NS + 'svg', path
    assert len(root.attrib['viewBox'].split()) == 4, path
    for element in root.iter():
        assert element.tag not in [NS + name for name in ('image', 'text', 'script', 'foreignObject', 'filter')], path
        for key, value in element.attrib.items():
            assert 'href' not in key and 'url(' not in value and 'data:' not in value, path
    assert size in (32, 64, 128, 256), size


def export(source, destination, size):
    subprocess.run(['inkscape', str(source), '--export-type=png',
                    f'--export-filename={destination}', f'--export-width={size}',
                    f'--export-height={size}'], check=True, capture_output=True)
    with Image.open(destination) as image:
        rgba = image.convert('RGBA')
    # Strip machine-specific renderer metadata; write deterministic RGBA PNG.
    rgba.save(destination, compress_level=9)
    return rgba


def validate_png(image, size, name):
    assert image.mode == 'RGBA' and image.size == (size, size), name
    alpha = image.getchannel('A')
    assert alpha.getextrema()[0] == 0 and alpha.getextrema()[1] > 0, name
    # Every texture has padding; atlas cells have their own padding as well.
    for box in [(0, 0, size, 1), (0, size-1, size, size),
                (0, 0, 1, size), (size-1, 0, size, size)]:
        assert alpha.crop(box).getextrema()[1] == 0, (name, 'clipped border')
    if name == 'seal_guard':
        assert alpha.crop((23, 23, 41, 41)).getextrema()[1] == 0, 'Ward centre must stay empty'
    if name == 'fantasy_layers':
        for x, y in [(0, 0), (64, 0), (0, 64), (64, 64)]:
            tile = image.crop((x, y, x+64, y+64))
            validate_png(tile, 64, 'fantasy_layer')
            assert tile.getchannel('A').crop((27, 27, 37, 37)).getextrema()[1] == 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    manifest = json.loads((HERE / 'spellcard_assets.json').read_text())
    target = ROOT / manifest['texture_root']
    if not args.check:
        target.mkdir(parents=True, exist_ok=True)
    names = [entry['name'] for entry in manifest['entries']]
    assert len(names) == len(set(names))
    report = []
    with tempfile.TemporaryDirectory(prefix='manka-export-') as tmp:
        for entry in manifest['entries']:
            name, size = entry['name'], entry['size']
            assert name.replace('_', '').isalnum(), name
            source = HERE / 'spellcards' / (name + '.svg')
            destination = target / (name + '.png')
            validate_source(source, size)
            image = export(source, Path(tmp) / (name + '.png'), size)
            validate_png(image, size, name)
            if args.check:
                with Image.open(destination) as existing:
                    assert existing.mode == 'RGBA' and existing.size == image.size, name
                    assert existing.tobytes() == image.tobytes(), f'Stale PNG: {destination}'
            else:
                image.save(destination, compress_level=9)
            report.append({'path': str(destination.relative_to(ROOT)), 'size': image.size,
                           'sha256': hashlib.sha256(destination.read_bytes()).hexdigest()})
    actual = {path.stem for path in target.glob('*.png')}
    assert actual == set(names), ('Unlisted/missing production PNG', actual ^ set(names))
    assert {p.stem for p in (HERE / 'spellcards').glob('*.svg')} == set(names)
    item_path = ROOT / 'src/main/resources/assets/maid_weapon/textures/item/manka_talisman.png'
    assert hashlib.sha256(item_path.read_bytes()).hexdigest() == manifest['item']['sha256'], 'Approved item differs from manifest'
    with Image.open(item_path) as item:
        item = item.convert('RGBA')
    assert item.size == (32, 1280)
    animation = json.loads(item_path.with_suffix('.png.mcmeta').read_text())['animation']
    assert animation == {'frametime': 2, 'interpolate': False}
    frames = [item.crop((0, i*32, 32, (i+1)*32)) for i in range(40)]
    def delta(a, b):
        return sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 4
    seam = delta(frames[-1], frames[0])
    adjacent = [delta(frames[i], frames[i+1]) for i in range(39)]
    assert seam <= max(adjacent), 'Animation seam exceeds largest ordinary transition'
    result = {'approved_plan': 'C', 'baseline': manifest['baseline'],
              'renderer': subprocess.check_output(['inkscape', '--version'], text=True).strip(),
              'item': {'sha256': manifest['item']['sha256'], 'size': item.size, 'frames': 40,
                       'ticks_per_frame': 2, 'interpolate': False,
                       'loop_seam_mean_rgba_delta': seam, 'max_adjacent_mean_rgba_delta': max(adjacent)},
              'textures': report}
    if not args.check:
        (HERE / 'spellcard_validation.json').write_text(json.dumps(result, indent=2) + '\n')
    print(f'PASS: {len(report)} SVG/PNG pairs; RGBA, dimensions, padding, empty centres, approved item and loop seam.'
          + (' Re-export matches every pixel.' if args.check else ''))


if __name__ == '__main__':
    main()

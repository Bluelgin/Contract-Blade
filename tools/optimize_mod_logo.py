"""Export the existing mod-list illustration at its display-sized resolution."""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
source = ROOT / 'art/mod_list_logo/original-485x340.png'
target = ROOT / 'src/main/resources/white-fox-mod-list.png'
with Image.open(source) as image:
    image = image.convert('RGB')
    image.thumbnail((256, 256), Image.Resampling.LANCZOS)
    image.save(target, optimize=True, compress_level=9)
print(f'Mod-list logo: {image.size}, {target.stat().st_size} bytes; original retained')

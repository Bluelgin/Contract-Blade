"""Render hand-drawn SVG source and a 32/16px preview. Requires cairosvg and Pillow."""
from pathlib import Path
import cairosvg
from PIL import Image, ImageDraw
root = Path(__file__).resolve().parents[1]
art = root / 'art/resonance'
textures = root / 'src/main/resources/assets/maid_weapon/textures/item'
names = ['awakening_vein', 'flowing_vein', 'endless_vein', 'endless_contract']
preview = Image.new('RGBA', (768, 240), '#242134')
draw = ImageDraw.Draw(preview)
for i, name in enumerate(names):
    cairosvg.svg2png(url=str(art / f'{name}.svg'), write_to=str(textures / f'{name}.png'), output_width=32, output_height=32)
    icon = Image.open(textures / f'{name}.png')
    preview.alpha_composite(icon.resize((128, 128), Image.Resampling.NEAREST), (i * 192 + 32, 12))
    small = icon.resize((16, 16), Image.Resampling.LANCZOS).resize((48, 48), Image.Resampling.NEAREST)
    preview.alpha_composite(small, (i * 192 + 72, 153))
    draw.text((i * 192 + 20, 218), name, fill='white')
preview.save(art / 'item-preview.png')

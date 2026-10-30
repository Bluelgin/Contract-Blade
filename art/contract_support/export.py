"""Export original 32px pixel SVGs; preview uses nearest-neighbour scaling only."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import io
import resvg_py

ROOT = Path(__file__).resolve().parents[2]
ART = Path(__file__).resolve().parent
TEXTURES = ROOT / 'src/main/resources/assets/maid_weapon/textures/item'
preview = Image.new('RGB', (720, 280), '#20232d')
draw = ImageDraw.Draw(preview)
font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 20)
for i, (name, label) in enumerate([('contract_emblem', '同契之证 · 驻留增伤'), ('guard_knot', '护主结 · 受伤显现')]):
    png = resvg_py.svg_to_bytes(svg_string=(ART / (name + '.svg')).read_text(encoding='utf-8'), width=32, height=32)
    (TEXTURES / (name + '.png')).write_bytes(png)
    icon = Image.open(io.BytesIO(png)).convert('RGBA')
    for y in range(32):
        for x in range(32):
            c = '#303340' if (x//4+y//4)%2 else '#292c36'
            draw.rectangle((i*360+84+x*6, 24+y*6, i*360+89+x*6, 29+y*6), fill=c)
    enlarged = icon.resize((192, 192), Image.Resampling.NEAREST)
    preview.paste(enlarged, (i*360+84, 24), enlarged)
    draw.text((i*360+70, 237), label, font=font, fill='#f2dfc1')
preview.save(ART / 'preview.png')

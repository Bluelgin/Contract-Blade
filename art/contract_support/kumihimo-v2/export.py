from pathlib import Path
import io
import resvg_py
from PIL import Image, ImageDraw, ImageFont
p=Path(__file__).resolve().parent
b=resvg_py.svg_to_bytes(svg_string=(p/'knot.svg').read_text(),width=32,height=32)
(p/'knot.png').write_bytes(b)
icon=Image.open(io.BytesIO(b)).convert('RGBA')
sheet=Image.new('RGB',(1050,560),'#20232d')
d=ImageDraw.Draw(sheet)
f=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',22)
small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',17)
d.text((28,18),'护主结 · 组纽重绘 / 生图概念 → 32×32 像素贴图',font=f,fill='#f2dfc1')
concept=Image.open(p/'concept.png').convert('RGB').resize((400,400),Image.Resampling.LANCZOS)
sheet.paste(concept,(28,74))
for y in range(32):
 for x in range(32):
  d.rectangle((470+x*12,74+y*12,481+x*12,85+y*12),fill='#303440' if (x//4+y//4)%2 else '#292c36')
large=icon.resize((384,384),Image.Resampling.NEAREST)
sheet.paste(large,(470,74),large)
sheet.paste(icon,(923,140),icon)
double=icon.resize((64,64),Image.Resampling.NEAREST)
sheet.paste(double,(907,250),double)
d.text((893,187),'原尺寸',font=small,fill='#c4ccda')
d.text((893,334),'2 倍显示',font=small,fill='#c4ccda')
d.text((145,492),'生图模型概念',font=f,fill='#f2dfc1')
d.text((544,492),'参考概念手工重绘',font=f,fill='#f2dfc1')
sheet.save(p/'comparison.png')

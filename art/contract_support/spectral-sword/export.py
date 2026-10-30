from pathlib import Path
import resvg_py
from PIL import Image, ImageDraw, ImageFont
import io
p=Path(__file__).resolve().parent
b=resvg_py.svg_to_bytes(svg_string=(p/'emblem.svg').read_text(),width=32,height=32)
(p/'emblem.png').write_bytes(b)
im=Image.open(io.BytesIO(b)).convert('RGBA')
out=Image.new('RGB',(640,440),'#20232e')
d=ImageDraw.Draw(out)
f=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',20)
d.text((28,22),'同契之证 · 幻灵注刃 / 32×32 贴图草案',font=f,fill='#efe4d3')
for y in range(32):
 for x in range(32):
  d.rectangle((36+x*10,75+y*10,45+x*10,84+y*10),fill='#303541' if (x//4+y//4)%2 else '#292d39')
out.paste(im.resize((320,320),Image.Resampling.NEAREST),(36,75),im.resize((320,320),Image.Resampling.NEAREST))
out.paste(im,(460,123),im)
out.paste(im.resize((64,64),Image.Resampling.NEAREST),(444,206),im.resize((64,64),Image.Resampling.NEAREST))
d.text((427,162),'原尺寸',font=f,fill='#c7cfdf')
d.text((425,282),'2 倍显示',font=f,fill='#c7cfdf')
out.save(p/'preview.png')

"""Pixel-SVG frames -> Minecraft texture strips, animated vector previews and GIF review.
Requires: cairosvg, Pillow. Run from anywhere; writes only project textures and art/resonance outputs.
"""
from pathlib import Path
import math,copy,json,io,os,argparse
from xml.etree import ElementTree as E
import cairosvg
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/'art/resonance';BASE=ART
TEXTURES=ROOT/'src/main/resources/assets/maid_weapon/textures/item'
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--export-svg',action='store_true',help='Also bake standalone SMIL SVG previews')
parser.add_argument('--individual',action='store_true',help='Also export one GIF per item')
args=parser.parse_args()
VECTORS=ART/'animated-svg';GIFS=ART/'individual-gifs'
for p in (ART,TEXTURES):p.mkdir(parents=True,exist_ok=True)
if args.export_svg:VECTORS.mkdir(exist_ok=True)
if args.individual:GIFS.mkdir(exist_ok=True)
NS='http://www.w3.org/2000/svg';E.register_namespace('',NS)
def tag(n):return '{'+NS+'}'+n
COUNT=40;DELAY=100;DURATION=4.0
NAMES=['awakening_vein','flowing_vein','endless_vein','endless_contract']
LABELS=['初醒灵脉','流转灵脉','无尽灵脉','无尽契约']
DESC=['灵晶呼吸 · 灵脉流光','金环循光 · 青蓝灵流','双脉循行 · 契印明灭','晶心呼吸 · 双流苏错相轻摆']
PALETTE={'#a66bce':.6,'#c998ed':1,'#e9c7fc':.5,'#389ebc':.45,'#82deed':.6}

def shade(c,amount):
 rgb=[int(c[i:i+2],16) for i in (1,3,5)]
 return '#'+''.join(f'{max(0,min(255,round(v+amount))):02x}' for v in rgb)
def point(points,u):
 lengths=[math.dist(a,b) for a,b in zip(points,points[1:])]; dist=u*sum(lengths)
 for a,b,l in zip(points,points[1:],lengths):
  if dist<=l:
   q=dist/l if l else 0;return round(a[0]+(b[0]-a[0])*q),round(a[1]+(b[1]-a[1])*q)
  dist-=l
 return points[-1]
def rect(parent,x,y,color,opacity=1):
 return E.SubElement(parent,tag('rect'),{'x':str(x),'y':str(y),'width':'1','height':'1','fill':color,'opacity':f'{opacity:.3f}'})

def bend_tassels(root,phase):
 children=list(root)
 left=[children[i] for i in (8,9,10,14,15,16)]
 right=[children[i] for i in (11,12,13,17,18,19)]
 insert=8
 for n in left+right:root.remove(n)
 defs=E.Element(tag('defs'))
 for side,nodes,lag in [('left',left,0),('right',right,.70)]:
  group=E.Element(tag('g'),{'id':'silk-'+side})
  # The attachment stays fixed; only the cloth tips bend. Integer offsets preserve pixel edges.
  for y in range(19,32):
   cid=f'tassel-{side}-{y}'
   clip=E.SubElement(defs,tag('clipPath'),{'id':cid})
   E.SubElement(clip,tag('rect'),{'x':'0','y':str(y),'width':'32','height':'1'})
   depth=max(0,(y-23)/8)
   dx=round(1.35*depth**1.3*math.sin(phase-lag-depth*.35))
   row=E.SubElement(group,tag('g'),{'clip-path':f'url(#{cid})'})
   shifted=E.SubElement(row,tag('g'),{'transform':f'translate({dx} 0)'})
   for node in nodes:shifted.append(copy.deepcopy(node))
  root.insert(insert,group);insert+=1
 root.insert(0,defs)

def frame_svg(name,index):
 root=E.parse(BASE/f'{name}.svg').getroot();phase=2*math.pi*index/COUNT
 if name=='endless_contract':bend_tassels(root,phase)
 # Tiny facet luminance changes; outlines, gold mass and overall silhouette never pulse/scale.
 breath=6*(.5-.5*math.cos(phase))
 for node in root.iter():
  c=node.get('fill')
  if c in PALETTE:node.set('fill',shade(c,breath*PALETTE[c]))
 # Restrict every moving light to existing material pixels; no floating rectangular sparks.
 defs=E.SubElement(root,tag('defs'))
 clip=E.SubElement(defs,tag('clipPath'),{'id':'material-silhouette'})
 original=E.parse(BASE/f'{name}.svg').getroot()
 for node in original:
  if node.tag!=tag('title'):clip.append(copy.deepcopy(node))
 lights=E.SubElement(root,tag('g'),{'clip-path':'url(#material-silhouette)','id':'flow-lights'})
 if name=='awakening_vein':
  pts=[(19,7),(20,9),(19,12),(18,15),(18,19),(16,22),(13,24),(10,25)]
  u=index/COUNT; x,y=point(pts,u)
  a=math.sin(math.pi*u)**1.2
  rect(lights,x,y,'#fff7f4',a*.95);rect(lights,x+1,y,'#b5edff',a*.45)
 elif name=='flowing_vein':
  pts=[(14,3),(11,5),(8,8),(6,12),(5,18),(7,23),(12,26),(18,27),(23,25),(26,21),(26,17)]
  u=index/COUNT;x,y=point(pts,u)
  rect(lights,x,y,'#fff6cd',math.sin(math.pi*u)**.7)
  x,y=point([(9,12),(6,13),(4,14)],(u+.2)%1)
  rect(lights,x,y,'#dafbff',.75)
 elif name=='endless_vein':
  pts=[(16,4),(20,5),(24,7),(27,11),(28,16),(27,21),(24,25),(20,27),(16,28),(12,27),(8,25),(5,21),(4,16),(5,11),(8,7),(12,5),(16,4)]
  for offset,color in [(0,'#c7f7ff'),(.5,'#ffe4f0')]:
   x,y=point(pts,(index/COUNT+offset)%1);rect(lights,x,y,color,.95)
  for k,(x,y) in enumerate([(16,4),(28,16),(16,28),(4,16)]):
   rect(lights,x,y,'#fff9e8',.15+.65*(.5+.5*math.sin(phase-k*math.pi/2))**3)
 else:
  rect(lights,16,16,'#fff0ff',.15+.50*(.5-.5*math.cos(phase)))
  rect(lights,16,3,'#fff9e8',.1+.75*max(0,math.sin(phase-.7))**4)
  # Glints walk only along the upper gold arch, while cloth motion remains asynchronous.
  x,y=point([(7,8),(10,5),(14,3),(18,3),(22,5),(25,8)],index/COUNT)
  rect(lights,x,y,'#fff3cc',math.sin(math.pi*index/COUNT)*.75)
 return E.tostring(root,encoding='unicode')

def animated_svg(name,frames):
 root=E.Element(tag('svg'),{'width':'32','height':'32','viewBox':'0 0 32 32','shape-rendering':'crispEdges'})
 E.SubElement(root,tag('title')).text=name+' · 4 second loop, 40 frames'
 # Prefix fragment identifiers per frame to avoid clipPath collisions across visibility groups.
 for i,svg in enumerate(frames):
  child=E.fromstring(svg)
  for node in child.iter():
   if node.get('id'):node.set('id',f'f{i}-'+node.get('id'))
   for key,val in list(node.attrib.items()):
    if 'url(#' in val:node.set(key,val.replace('url(#',f'url(#f{i}-'))
  group=E.SubElement(root,tag('g'),{'visibility':'hidden','id':f'frame-{i:02d}'})
  for node in child:group.append(node)
  if i==0:times='0;0.025;1';values='visible;hidden;hidden'
  elif i==COUNT-1:times=f'0;{i/COUNT:.3f};1';values='hidden;visible;visible'
  else:times=f'0;{i/COUNT:.3f};{(i+1)/COUNT:.3f};1';values='hidden;visible;hidden;hidden'
  E.SubElement(group,tag('animate'),{'attributeName':'visibility','values':values,'keyTimes':times,'dur':'4s','repeatCount':'indefinite','calcMode':'discrete'})
 (VECTORS/f'{name}.svg').write_text(E.tostring(root,encoding='unicode'))

all_frames={}
for name in NAMES:
 svgs=[frame_svg(name,i) for i in range(COUNT)]
 images=[Image.open(io.BytesIO(cairosvg.svg2png(bytestring=s.encode(),output_width=32,output_height=32))).convert('RGBA') for s in svgs]
 all_frames[name]=images
 strip=Image.new('RGBA',(32,32*COUNT))
 for i,im in enumerate(images):strip.paste(im,(0,32*i))
 strip.save(TEXTURES/f'{name}.png')
 (TEXTURES/f'{name}.png.mcmeta').write_text(json.dumps({'animation':{'frametime':2,'interpolate':False,'width':32,'height':32}},indent=2)+'\n')
 if args.export_svg:animated_svg(name,svgs)
 singles=[]
 for im in images:
  canvas=Image.new('RGB',(256,256),'#252231');big=im.resize((256,256),Image.Resampling.NEAREST);canvas.paste(big,(0,0),big);singles.append(canvas)
 if args.individual:singles[0].save(GIFS/f'{name}.gif',save_all=True,append_images=singles[1:],duration=DELAY,loop=0,optimize=False)

font_path=Path(os.environ.get('CONTRACT_ART_FONT','/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'))
CJK=font_path.exists()
if not CJK:
 LABELS=['Awakening Vein','Flowing Vein','Endless Vein','Endless Contract']
 DESC=['Crystal breath / vein shimmer','Gold rim glint / cyan flow','Twin orbit / seal shimmer','Heart breath / soft tassel sway']
def font(sz):
 if CJK:return ImageFont.truetype(str(font_path),sz)
 try:return ImageFont.truetype('DejaVuSans.ttf',sz)
 except OSError:return ImageFont.load_default()
preview=[]
for f in range(COUNT):
 canvas=Image.new('RGB',(1440,660),'#171621');d=ImageDraw.Draw(canvas)
 d.text((48,27),'契约共鸣 · 第二版动态预览' if CJK else 'Contract Resonance / Animated Items v2',font=font(32),fill='#f5e8dc')
 d.text((50,80),'4 秒无缝循环  /  晶体流光 · 双脉循行 · 流苏轻摆' if CJK else '4 second loop / crystal light / twin orbit / soft tassel sway',font=font(19),fill='#b4adbb')
 for i,(name,label,desc) in enumerate(zip(NAMES,LABELS,DESC)):
  x=48+i*348
  d.rounded_rectangle((x,130,x+327,490),radius=14,fill='#252231',outline='#3a3449',width=1)
  im=all_frames[name][f];big=im.resize((256,256),Image.Resampling.NEAREST);canvas.paste(big,(x+36,145),big)
  box=d.textbbox((0,0),label,font=font(26));d.text((x+(328-box[2])/2,424),label,font=font(26),fill='#f6e9db')
  d.text((x+38,464),desc,font=font(14),fill='#b9aabc')
  d.text((x+20,515),'16 × 16',font=font(15),fill='#a99eae')
  small=im.resize((16,16),Image.Resampling.NEAREST).resize((80,80),Image.Resampling.NEAREST);canvas.paste(small,(x+112,511),small)
 d.text((50,615),'像素动画 · 40 帧 / 4 秒循环 · 含 16×16 检查' if CJK else 'Pixel animation / 40 frames / 4 seconds / 16x16 readability check',font=font(17),fill='#c6b7ca')
 preview.append(canvas)
# One shared palette prevents static-caption color churn and keeps review GIFs compact.
training=Image.new('RGB',(1568,1280),'#171621');training.paste(preview[0],(128,0))
for i,name in enumerate(NAMES):
 for j,im in enumerate(all_frames[name]):
  training.paste(im,(i*32,j*32),im)
palette=training.quantize(colors=256)
quantized=[im.quantize(palette=palette,dither=Image.Dither.NONE) for im in preview]
quantized[0].save(ART/'item-preview.gif',save_all=True,append_images=quantized[1:],duration=DELAY,loop=0,optimize=True,disposal=1)
preview[10].save(ART/'item-preview.png')
# A short phase sheet makes it possible to inspect the moving parts without an animated viewer.
# Separate unambiguous accessory phase comparison.
contact=Image.new('RGB',(1024,310),'#252231');d=ImageDraw.Draw(contact)
for j,idx in enumerate([0,10,20,30]):
 im=all_frames['endless_contract'][idx].resize((224,224),Image.Resampling.NEAREST);contact.paste(im,(j*256+16,10),im);d.text((j*256+75,255),f'{idx/10:.1f} s',font=font(18),fill='white')
contact.save(ART/'motion-phases.png')
print('Rendered 4 x 40 SVG frames, native 32x1280 strips and review previews.')

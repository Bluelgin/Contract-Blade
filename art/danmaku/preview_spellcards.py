#!/usr/bin/env python3
"""Build review board and illustrative motion GIF from production textures.
Requires Python 3.10+, Pillow 12.3.0, DejaVu Sans. No game assets are written.
"""
from pathlib import Path
import math
from PIL import Image, ImageDraw, ImageFont, ImageEnhance

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
TEX = ROOT / 'src/main/resources/assets/maid_weapon/textures/gui/danmaku'
OUT = HERE / 'previews'
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
NEAREST = Image.Resampling.NEAREST


def load(name):
    return Image.open(TEX / (name+'.png')).convert('RGBA')


def paste(board, image, xy, size=None):
    board.alpha_composite(image.resize(size, NEAREST) if size else image, xy)


def label(board, xy, text, size=18, color='#ead9cb'):
    ImageDraw.Draw(board).text(xy, text, font=ImageFont.truetype(FONT, size), fill=color)


def opacity(image, amount):
    image = image.copy()
    image.putalpha(image.getchannel('A').point(lambda a: round(a*amount)))
    return image


def fantasy(phase):
    result = Image.new('RGBA', (192,192))
    atlas = load('fantasy_layers')
    for i,(x,y) in enumerate([(0,0),(64,0),(0,64),(64,64)]):
        tile = atlas.crop((x,y,x+64,y+64))
        tile = tile.rotate(phase*72*(-1 if i%2 else 1), resample=NEAREST)
        size = round(180*(1-.52*phase))
        paste(result, tile, ((192-size)//2,(192-size)//2), (size,size))
    if phase>.7:
        paste(result,opacity(load('final_seal'),(phase-.7)/.3),(64,64))
    return result


def ward(phase):
    result = Image.new('RGBA',(192,192))
    size = round(118+48*math.sin(math.pi*phase))
    tile = load('seal_guard').rotate(phase*45, resample=NEAREST)
    if phase>.8: tile=opacity(tile,max(.15,(1-phase)/.2))
    paste(result,tile,((192-size)//2,(192-size)//2),(size,size))
    if .45<phase<.8:paste(result,load('intercept_flash'),(113,12),(40,40))
    return result


def main():
    OUT.mkdir(exist_ok=True)
    item_sheet=Image.open(ROOT/'src/main/resources/assets/maid_weapon/textures/item/manka_talisman.png').convert('RGBA')
    item=item_sheet.crop((0,0,32,32))
    kinds=['needle','flower','guard','fantasy']
    wheel=load('wheel_base')
    for k,pos in zip(kinds,[(112,40),(184,112),(40,112),(112,184)]):paste(wheel,load('icon_'+k),pos)
    paste(wheel,item,(104,104),(48,48))
    board=Image.new('RGBA',(1440,1600),'#161320')
    label(board,(45,28),'MANKA / APPROVED PLAN C',32)
    label(board,(45,78),'Production texture review / offline composition, not a game capture',17)
    label(board,(45,126),'01  FOUR FORMS / native 32 px + 4x nearest-neighbour',20)
    for i,k in enumerate(kinds):
        x=65+i*344
        paste(board,load('icon_'+k),(x,175),(128,128))
        paste(board,load('icon_'+k),(x+164,223))
        label(board,(x,320),k.upper())
    label(board,(45,386),'02  WHEEL / real item centre + independent state overlays',20)
    paste(board,wheel,(65,445),(448,448))
    for i,state in enumerate(['UNSELECTED','SELECTED','LOCKED']):
        x=575+255*i;label(board,(x,454),state,16)
        icon=load('icon_flower' if i<2 else 'icon_fantasy')
        if i==1:paste(board,load('selection'),(x-8,490),(128,128))
        if i==2:icon=opacity(ImageEnhance.Color(icon).enhance(0),.35)
        paste(board,icon,(x+24,522),(64,64))
        if i==2:paste(board,load('lock'),(x+65,565))
    label(board,(575,665),'ORIGINAL TALISMAN / SHARED PALETTE',18)
    paste(board,item,(590,720),(128,128))
    for i,color in enumerate(['#fff0d5','#c44a64','#f7b4ce','#e9c580','#35263f','#c5f5ff']):
        ImageDraw.Draw(board).rounded_rectangle((770+i*92,750,830+i*92,806),6,fill=color)
    label(board,(45,939),'03  WARD + FANTASY / four illustrative stages',20)
    for row,renderer in enumerate([ward,fantasy]):
        for stage,phase in enumerate([0,.33,.66,1]):
            x,y=65+344*stage,991+row*245
            ImageDraw.Draw(board).rounded_rectangle((x,y,x+255,y+192),6,fill='#242535')
            paste(board,renderer(phase),(x+32,y))
            label(board,(x,y+201),(['Awaken','Stabilize','Intercept','Fade'] if row==0 else ['Four arrays','Offset rotation','Converge','Final seal'])[stage],16)
    label(board,(45,1514),'Original SVG paths / transparent textures / hollow combat centres / no shader required',16)
    label(board,(45,1549),'Animation below is an integration study; it does not define gameplay timing or unlock rules.',15)
    board.convert('RGB').save(OUT/'manka_spellcards_review.png')
    frames=[]
    for index in range(40):
        t=index/39
        frame=Image.new('RGBA',(768,300),'#161320')
        label(frame,(22,12),'OFFLINE MOTION STUDY / 4 seconds, not in-game footage',17)
        paste(frame,ward(t),(12,65));paste(frame,fantasy(t),(256,65))
        icon=load('selection').rotate(index*9,resample=NEAREST)
        paste(frame,icon,(565,91),(128,128))
        paste(frame,item_sheet.crop((0,index*32,32,(index+1)*32)),(605,131),(48,48))
        label(frame,(54,266),'WARD',15);label(frame,(305,266),'FANTASY',15);label(frame,(556,266),'ORIGINAL ITEM',15)
        frames.append(frame.convert('RGB').quantize(colors=64))
    # Nonlooping, finite spell demonstration; item animation itself stays a seamless loop.
    frames[0].save(OUT/'manka_motion_study.gif',save_all=True,append_images=frames[1:],duration=100,optimize=True)
    print('Review PNG + finite 40-frame motion-study GIF exported.')


if __name__=='__main__':main()

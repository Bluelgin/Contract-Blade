"""Capture a running, initialized real 1280x720 Minecraft client. Never draws or synthesizes frames.
Start in first person, on slot1 of /mankatest setup's disposable scene. Requires cheats, xdotool and FFmpeg.
"""
import argparse,os,subprocess,time
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=['full','fantasy','performance']);p.add_argument('output',type=Path);a=p.parse_args()
if not os.environ.get('DISPLAY'):raise SystemExit('DISPLAY required')
out=a.output.resolve();out.parent.mkdir(parents=True,exist_ok=True)
def x(*args):subprocess.run(['xdotool',*map(str,args)],check=True)
def key(k):x('key','--clearmodifiers',k)
def wait(s):time.sleep(s)
def command(text):
 key('t');wait(.3);x('type','--clearmodifiers','--delay','2',text);key('Return');wait(.45)
def tap():x('mousedown',2);wait(.12);x('mouseup',2);wait(.2)
def wheel(direction,dwell=.7):
 x('mousedown',2);wait(.65)
 px,py={'needle':(640,255),'flower':(744,360),'guard':(536,360),'fantasy':(640,464)}[direction]
 x('mousemove',px,py);wait(dwell);x('mouseup',2);wait(.35)
command('/gamerule sendCommandFeedback false');command('/gamerule announceAdvancements false')
command('/maidweapon resonance 1000');key('F3+d');wait(1)
log=out.with_suffix('.capture.log').open('w')
record=subprocess.Popen(['ffmpeg','-hide_banner','-nostdin','-y','-f','x11grab','-video_size','1280x720','-framerate','30','-i',os.environ['DISPLAY'],
 '-an','-c:v','libx264','-preset','veryfast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(out)],stdout=log,stderr=subprocess.STDOUT)
start=time.monotonic()
try:
 if a.mode=='full':
  key('2');key('e');wait(1);x('mousemove',532,496);wait(10);key('e');key('1');wait(2)
  for direction in ['needle','flower','guard','fantasy']:wheel(direction,2)
  wait(2)
  for i in range(6):x('click',1);wait(.9)
  key('F5');wheel('needle',1);tap();wait(5);tap();wait(5)
  wheel('flower',1);tap();wait(6);tap();wait(6)
  wheel('guard',1);tap();wait(.6);command('/mankatest arrows');wait(6)
  wheel('fantasy',1);tap();wait(5)
  for i in range(3):x('mousemove_relative','--',40,0);wait(1.5)
  command('/tp @s 0.5 65 0.5 0 0');wait(1);tap();wait(5)
  key('F5');wait(1);key('F5');wait(1)
  command('/maidweapon resonance 1000');tap();wait(5)
  # Remains a running live game while the final frame timing sample completes.
  wait(max(0,135-(time.monotonic()-start)))
 elif a.mode=='fantasy':
  wheel('fantasy',.7);tap();wait(5);key('F5');wait(2)
  command('/maidweapon resonance 1000');tap();wait(5)
  x('mousemove_relative','--',55,-12);wait(3);key('F5');wait(.5);key('F5');wait(2)
 elif a.mode=='performance':
  command('/mankatest bauble 2');command('/maidweapon resonance 0');key('F3');wait(3)
  for i in range(36):
   command('/mankatest cast 3' if i%4!=3 else '/mankatest cast 1');wait(.9)
   if i%8==0:command('/mankatest metrics')
   if i==18:command('/tp @s 0.5 65 0.5 0 -65')
  command('/mankatest metrics');wait(7)
  command('/tp @s 0.5 65 0.5 0 0');key('F3');command('/mankatest bauble 1');command('/maidweapon resonance 1000');wait(2)
finally:
 # FFmpeg SIGINT finalizes the genuine raw capture at its original playback speed.
 record.send_signal(__import__('signal').SIGINT)
 try:record.wait(timeout=30)
 except subprocess.TimeoutExpired:record.terminate();record.wait(timeout=10)
 log.close()
 if record.returncode not in [0,255]:raise SystemExit('FFmpeg capture failed: '+str(out.with_suffix('.capture.log')))
print(out,round(time.monotonic()-start,2),flush=True)

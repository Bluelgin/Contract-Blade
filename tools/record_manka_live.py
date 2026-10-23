"""Live X11 XGetImage capture -> FFmpeg H.264. Reads only the running display; never synthesizes artwork.
Wall-clock timestamps preserve original game playback speed even when the recorder cannot keep 30 FPS.
Requires mss==10.1.0 and FFmpeg. Output MP4 belongs outside the source repository.
"""
import argparse,datetime,json,os,signal,subprocess,time
from pathlib import Path
import mss
p=argparse.ArgumentParser(description=__doc__);p.add_argument('output',type=Path);p.add_argument('--seconds',type=float);p.add_argument('--fps',type=int,default=30);a=p.parse_args()
if not os.environ.get('DISPLAY'):raise SystemExit('A real X11 display running Minecraft is required')
out=a.output.resolve();out.parent.mkdir(parents=True,exist_ok=True)
running=True
def stop(*_):
 global running
 running=False
signal.signal(signal.SIGINT,stop);signal.signal(signal.SIGTERM,stop)
with out.with_suffix('.ffmpeg.log').open('w') as log, mss.mss() as screen:
 monitor={'left':0,'top':0,'width':1280,'height':720}
 encoder=subprocess.Popen(['ffmpeg','-hide_banner','-nostdin','-y','-use_wallclock_as_timestamps','1',
  '-probesize','32','-analyzeduration','0','-f','rawvideo','-pixel_format','bgra','-video_size','1280x720',
  '-framerate',str(a.fps),'-i','pipe:0','-an','-fps_mode','vfr','-c:v','libx264','-threads','4',
  '-preset','veryfast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(out)],
  stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT)
 started=time.monotonic();utc=datetime.datetime.now(datetime.timezone.utc).isoformat();frames=0;first=None;last=None
 try:
  while running and (a.seconds is None or time.monotonic()-started<a.seconds):
   now=time.monotonic();shot=screen.grab(monitor);stamp=time.monotonic()
   if first is None:first=stamp
   last=stamp;encoder.stdin.write(shot.bgra);frames+=1
   remaining=1/a.fps-(time.monotonic()-now)
   if remaining>0:time.sleep(remaining)
 finally:
  encoder.stdin.close();code=encoder.wait(timeout=30);elapsed=time.monotonic()-started
  info={'source':'live X11 XGetImage (MSS); no image synthesis','display':os.environ['DISPLAY'],
   'startedUTC':utc,'width':1280,'height':720,'requestedFPS':a.fps,'framesCaptured':frames,
   'captureSeconds':None if first is None else last-first,'wallSecondsIncludingFinalize':elapsed,
   'timestamps':'FFmpeg input wall-clock; VFR, original speed','encoderExit':code}
  out.with_suffix('.capture.json').write_text(json.dumps(info,indent=2)+'\n')
  print(json.dumps(info),flush=True)
  if code or frames==0:raise SystemExit('Live capture failed: '+str(out.with_suffix('.ffmpeg.log')))

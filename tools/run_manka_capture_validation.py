"""Attach to a real, freshly launched 1280x720 client; capture three raw gameplay MP4s.
Requires run_manka_client.py's disposable opt-in world. No offline or generated frames are accepted.
"""
import argparse,subprocess,time,sys,json
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--log',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
a.output.mkdir(parents=True,exist_ok=True);root=Path(__file__).resolve().parents[1]
def text():return a.log.read_text(errors='replace') if a.log.exists() else ''
def wait_for(test,seconds=600):
 deadline=time.monotonic()+seconds
 while time.monotonic()<deadline:
  content=text()
  if test(content):return
  if 'BUILD FAILED' in content or 'MANKA_NATIVE_FAIL' in content:raise RuntimeError('Client failed; inspect '+str(a.log))
  time.sleep(.5)
 raise TimeoutError('Client state timeout: '+str(a.log))
def x(*args):subprocess.run(['xdotool',*map(str,args)],check=True)
def command(t):
 x('key','t');time.sleep(.3);x('type','--clearmodifiers','--delay',2,t);x('key','Return');time.sleep(.4)
# This confirmation is the observed experimental-settings screen of this disposable Forge fixture.
wait_for(lambda s:'MANKA_SCREEN ConfirmScreen' in s or 'MANKA_SCREEN InGame' in s)
if 'MANKA_SCREEN InGame' not in text():x('mousemove',480,332);x('click',1)
wait_for(lambda s:'MANKA_SCREEN InGame' in s);time.sleep(2)
command('/mankatest setup');wait_for(lambda s:'MANKA_SCENE_READY' in s,30);time.sleep(2)
results=[]
for mode,name,minimum in [('full','manka_full_gameplay.mp4',120),('fantasy','manka_fantasy_closeup.mp4',12),('performance','manka_performance_test.mp4',40)]:
 out=a.output/name
 subprocess.run([sys.executable,str(root/'tools/capture_manka_gameplay.py'),mode,str(out)],check=True,cwd=root)
 meta=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration,size','-show_entries','stream=codec_name,width,height,avg_frame_rate,nb_frames','-of','json',str(out)]))
 assert meta['streams'][0]['codec_name']=='h264' and float(meta['format']['duration'])>=minimum,meta
 capture=json.loads(out.with_suffix('.capture.json').read_text());assert capture['encoderExit']==0 and capture['framesCaptured']>0
 results.append({'name':name,'video':meta,'capture':capture});(a.output/'video-validation.json').write_text(json.dumps(results,indent=2))
 print('MANKA_REAL_CAPTURE_PASS',name,flush=True)

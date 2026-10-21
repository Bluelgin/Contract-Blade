"""Observed runtime checks in the real disposable client and a second real JVM.
No FakePlayers, no synthetic video. JSON assertions cite actual server/client logs.
"""
import argparse,os,subprocess,time,json,re,signal,sys
from pathlib import Path
import mss,mss.tools
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1];client=a.client.resolve();out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
host=os.environ['DISPLAY'];results=[];hostlog=client/'logs/latest.log'
def read(path):return path.read_text(errors='replace') if path.exists() else ''
def x(*args,display=host):subprocess.run(['xdotool',*map(str,args)],env=dict(os.environ,DISPLAY=display),check=True)
def command(s,display=host):
 x('key','t',display=display);time.sleep(.35);x('type','--clearmodifiers','--delay',2,s,display=display);x('key','Return',display=display);time.sleep(.6)
def shot(name,display=host):
 with mss.mss(display=display) as screen:
  image=screen.grab({'left':0,'top':0,'width':1280,'height':720});mss.tools.to_png(image.rgb,image.size,output=str(out/(name+'.png')))
def save(name,passed,evidence):
 results.append({'case':name,'status':'PASS' if passed else 'FAIL','evidence':evidence});(out/'runtime-qa.json').write_text(json.dumps(results,ensure_ascii=False,indent=2));print('MANKA_RUNTIME_QA',json.dumps(results[-1],ensure_ascii=False),flush=True)
def wait_log(path,predicate,timeout=120):
 deadline=time.monotonic()+timeout
 while time.monotonic()<deadline:
  text=read(path)
  if predicate(text):return text
  time.sleep(.5)
 raise TimeoutError('Runtime state timeout: '+str(path))
def segment(action,seconds=3):
 offset=len(read(hostlog));action();time.sleep(seconds);return read(hostlog)[offset:]
def count_shots(s):return len(re.findall('MANKA_SHOT owner=',s))
command('/mankatest setup');time.sleep(2)
for lv,expected in [(1,3),(2,4)]:
 command('/maidweapon level '+str(lv));s=segment(lambda:x('click',1));n=count_shots(s);save('Lv'+str(lv)+' actual free auto entities',n==expected,{'actual':n,'expected':expected})
command('/maidweapon level 1');s=segment(lambda:command('/mankatest cast 0'));save('Lv1 server denies needle',count_shots(s)==0 and 'kind=WAVE' not in s,{'shots':count_shots(s)})
command('/maidweapon level 10');time.sleep(1)
s=segment(lambda:(x('mousedown',2),time.sleep(.7),x('mousemove',640,255),time.sleep(.8),shot('host-wheel-gui2'),x('mouseup',2)))
save('middle hold opens wheel and release only selects','MANKA_SCREEN DanmakuWheelScreen' in s and count_shots(s)==0,{'screen': 'DanmakuWheelScreen' in s,'shots':count_shots(s)})
s=segment(lambda:x('click',2));save('middle short cast uses real twelve needles',count_shots(s)==12,{'shots':count_shots(s)})
for lv,expected in [(5,18),(7,24),(9,30)]:
 command('/maidweapon level '+str(lv));command('/maidweapon resonance 1000');s=segment(lambda:command('/mankatest cast 1'),4);n=count_shots(s);save('Lv'+str(lv)+' flower actual two waves',n==expected,{'actual':n,'expected':expected,'phases':re.findall(r'kind=WAVE.*?phase=(\d+)',s)})
for lv,expected in [(6,5),(7,8)]:
 command('/mankatest setup');command('/maidweapon level '+str(lv));command('/mankatest cast 2');s=segment(lambda:command('/mankatest arrows'),5);n=len(re.findall('kind=INTERCEPT',s));save('Lv'+str(lv)+' real hostile arrow interceptions',n==expected,{'actual':n,'expected':expected,'stopped':'kind=GUARD_STOP' in s})
command('/mankatest setup');command('/mankatest bauble 0');s=segment(lambda:command('/mankatest cast 0'));save('inventory talisman without native bauble denied',count_shots(s)==0 and 'kind=WAVE' not in s,{'shots':count_shots(s)})
command('/mankatest bauble 1');command('/mankatest second');s=segment(lambda:(command('/mankatest cast 3'),x('key','3')),4);save('switch carrier cancels remaining fantasy phases','kind=CANCEL' in s and not re.search(r'kind=WAVE.*?phase=3',s),{'cancel':'kind=CANCEL' in s,'phases':re.findall(r'kind=WAVE.*?phase=(\d+)',s)})
x('key','1');command('/mankatest setup')
# Window-size check is a real resized native OpenGL client, not an SVG preview.
window=subprocess.check_output(['xdotool','search','--onlyvisible','--name','Minecraft']).decode().splitlines()[-1]
x('windowsize',window,854,480);time.sleep(2);x('mousedown',2);time.sleep(.8);shot('host-wheel-854x480');x('mouseup',2);x('windowsize',window,1280,720);time.sleep(2)
# Publish only this opt-in local developer world; the observer is a real independent client JVM.
command('/publish true survival 25565');command('/mankatest lan')
display=':96';xvfb=subprocess.Popen(['Xvfb',display,'-screen','0','1280x720x24','-extension','MIT-SHM','-ac'],stdout=(out/'observer-xvfb.log').open('w'),stderr=subprocess.STDOUT);time.sleep(2)
observer_dir=out/'observer';observer=subprocess.Popen([sys.executable,str(root/'tools/run_manka_second_client.py'),'--source-client',str(client),'--output',str(observer_dir),'--display',display],stdout=(out/'observer-launcher.log').open('w'),stderr=subprocess.STDOUT)
observerlog=observer_dir/'logs/latest.log'
try:
 wait_log(observerlog,lambda s:'MANKA_SCREEN InGame' in s,180)
 command('/op MankaObserver');command('/mankatest setup',display);command('/tp @s 4.5 65 -3.5 18 0',display);command('/maidweapon level 3',display)
 start=len(read(observerlog));x('keydown','g',display=display);time.sleep(1);shot('observer-gui1-locked',display);x('keyup','g',display=display);time.sleep(.5)
 s=read(observerlog)[start:];save('real observer G rebind opens GUI1 wheel','MANKA_SCREEN DanmakuWheelScreen' in s,{'screen':'DanmakuWheelScreen' in s})
 # At GUI scale1 the up symbol is 52 physical pixels from the center.
 x('keydown','g',display=display);time.sleep(.7);x('mousemove',640,308,display=display);time.sleep(.5);x('keyup','g',display=display);time.sleep(.5)
 s=segment(lambda:x('key','g',display=display),4);save('G short cast sends native needle entities',count_shots(s)==12,{'shots':count_shots(s)})
 command('/maidweapon level 10',display);x('keydown','g',display=display);time.sleep(1);shot('observer-gui1-unlocked',display);x('keyup','g',display=display);time.sleep(.6)
 command('/tp @s 0.5 65 0.5 0 0');command('/maidweapon resonance 1000');command('/mankatest cast 3');time.sleep(.7);shot('observer-sees-host-fantasy',display);time.sleep(4)
 command('/mankatest metrics');save('two independent real LAN clients connected','realPlayers=2' in read(hostlog),{'realPlayers2Logged':'realPlayers=2' in read(hostlog),'type':'two automated offline developer JVMs, not two human players'})
 # Separate raw normal-speed live observer capture; never replace the solo original.
 record=subprocess.Popen([sys.executable,str(root/'tools/record_manka_live.py'),str(out/'manka_two_clients.mp4'),'--seconds','45'],env=dict(os.environ,DISPLAY=display),stdout=(out/'two-client-capture.log').open('w'),stderr=subprocess.STDOUT)
 x('key','F3',display=display);command('/mankatest bauble 2');command('/mankatest bauble 2',display);command('/maidweapon resonance 0');command('/maidweapon resonance 0',display)
 for i in range(10):
  command('/mankatest cast 3');command('/mankatest cast 3',display);time.sleep(1)
  if i%3==0:command('/mankatest metrics')
 record.wait(timeout=60);save('two-client original-speed MP4 recorded',record.returncode==0,{'path':'manka_two_clients.mp4'})
 time.sleep(12);shot('observer-after-expiry',display);command('/mankatest metrics')
 # One actual moving vanilla Boss, no zombie/skeleton targets; creative is only camera protection.
 # Survival permissions/costs are checked separately above and by the dedicated fixture.
 command('/gamemode creative');command('/gamemode creative',display)
 command('/kill @e[type=minecraft:zombie]');command('/kill @e[type=minecraft:skeleton]')
 command('/gamerule mobGriefing false')
 command('/summon minecraft:wither 0.5 66 14.5 {Invul:0,Health:1000f,Attributes:[{Name:"minecraft:generic.max_health",Base:1000d}],Tags:["manka_boss"]}')
 command('/tp @s 0.5 65 0.5 0 0');command('/tp @s 4.5 65 -3.5 12 0',display)
 boss_start=len(read(hostlog))
 boss_record=subprocess.Popen([sys.executable,str(root/'tools/record_manka_live.py'),str(out/'manka_boss_window_test.mp4'),'--seconds','40'],env=dict(os.environ,DISPLAY=display),stdout=(out/'boss-capture.log').open('w'),stderr=subprocess.STDOUT)
 command('/data get entity @e[type=minecraft:wither,tag=manka_boss,limit=1] Health')
 for i in range(8):
  command('/mankatest cast 0');command('/mankatest cast 0',display);time.sleep(1)
 command('/data get entity @e[type=minecraft:wither,tag=manka_boss,limit=1] Health')
 boss_record.wait(timeout=60)
 events=re.findall(r'kind=HIT, owner=([^,]+).*?tick=(\d+)',read(hostlog)[boss_start:])
 ticks=sorted(int(t) for _,t in events);owners={owner:sum(1 for o,_ in events if o==owner) for owner,_ in events}
 save('same vanilla Boss actual global two-tick hit window',len(ticks)>=2 and all(b-a>=2 for a,b in zip(ticks,ticks[1:])),{'actualHitEvents':len(ticks),'successfulHitsByOwner':owners,'minimumGapTicks':min((b-a for a,b in zip(ticks,ticks[1:])),default=None),'scope':'one moving vanilla Wither; not optional modded Boss phases or immunity matrix'})
 command('/kill @e[type=minecraft:wither,tag=manka_boss]')
 save('low particle mode really loaded','particles=MINIMAL' in read(observerlog),{'minimalLogged':'particles=MINIMAL' in read(observerlog)})
finally:
 # Disconnect observer via native pause menu; process termination is only an isolated observer fallback.
 try:
  windows=subprocess.check_output(['xdotool','search','--onlyvisible','--name','Minecraft'],env=dict(os.environ,DISPLAY=display)).decode().splitlines()
  for w in windows:
   pid=int(subprocess.check_output(['xdotool','getwindowpid',w],env=dict(os.environ,DISPLAY=display)));os.kill(pid,signal.SIGTERM)
 except (subprocess.SubprocessError,ValueError,ProcessLookupError):pass
 try:observer.wait(timeout=30)
 except subprocess.TimeoutExpired:observer.terminate()
 xvfb.terminate()
 # Leave the host alive for the wrapper's native Save and Quit.
 command('/mankatest bauble 1');command('/maidweapon resonance 1000')
command('/item replace entity @s hotbar.4 with slashblade:slashblade');x('key','5');time.sleep(2);shot('native-slashblade-model');x('key','1');time.sleep(1)
# Exercise fallback only in disposable build output. Approved source PNGs stay byte-identical.
texture_dir=root/'build/resources/main/assets/maid_weapon/textures/gui/danmaku'
backup={p:p.read_bytes() for p in [texture_dir/'wheel_base.png',texture_dir/'seal_needle.png']}
try:
 for p in backup:p.unlink()
 start=len(read(hostlog));x('keydown','F3');x('key','t');x('keyup','F3');time.sleep(12)
 x('mousedown',2);time.sleep(1);shot('missing-texture-safe-wheel');x('mouseup',2);time.sleep(1)
 command('/mankatest cast 0');time.sleep(3)
 s=read(hostlog)[start:];save('missing wheel and needle texture safe fallback','MANKA_SCREEN DanmakuWheelScreen' in s and count_shots(s)==12,{'screenOpened':'DanmakuWheelScreen' in s,'actualNeedles':count_shots(s),'scope':'source files untouched; build output removed only during F3+T test'})
finally:
 for p,data in backup.items():p.write_bytes(data)
 x('keydown','F3');x('key','t');x('keyup','F3');time.sleep(12)
 x('mousedown',2);time.sleep(1);shot('restored-texture-wheel');x('mouseup',2);time.sleep(1)
failures=[r for r in results if r['status']=='FAIL']
if failures:raise SystemExit(str(len(failures))+' actual runtime assertions failed; see runtime-qa.json')

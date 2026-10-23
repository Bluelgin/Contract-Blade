"""Start a second real Forge JVM from the running dev client's launch recipe (Linux).
Only for the opt-in isolated acceptance LAN. Does not print or persist launch credentials.
"""
import argparse,os,subprocess,uuid,shutil
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--source-client',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
p.add_argument('--display',default=':96');a=p.parse_args();source=a.source_client.resolve();out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
recipe=None;env=None
for proc in Path('/proc').iterdir():
 if not proc.name.isdigit():continue
 try:
  args=proc.joinpath('cmdline').read_bytes().decode().strip('\0').split('\0')
  if '--launchTarget' in args and 'forgeclientuserdev' in args:
   recipe=args;env=dict(x.split('=',1) for x in proc.joinpath('environ').read_bytes().decode().strip('\0').split('\0') if '=' in x);break
 except (PermissionError,FileNotFoundError,ProcessLookupError,UnicodeError):continue
if recipe is None:raise SystemExit('No running Forge userdev client found')
# Use the exact running classpath and source-set environment; avoid another Gradle invocation/cache lock.
def replace(flag,value):
 if flag in recipe:recipe[recipe.index(flag)+1]=value
 else:recipe.extend([flag,value])
for flag in ['--quickPlaySingleplayer','--quickPlayMultiplayer']:
 if flag in recipe:
  i=recipe.index(flag);del recipe[i:i+2]
replace('--username','MankaObserver');replace('--uuid',uuid.UUID(bytes=__import__('hashlib').md5(b'OfflinePlayer:MankaObserver').digest(),version=3).hex)
replace('--gameDir',str(out));replace('--quickPlayMultiplayer','127.0.0.1:25565')
for folder in ['config','defaultconfigs']:
 if (source/folder).exists():shutil.copytree(source/folder,out/folder)
(out/'options.txt').write_text('version:3465\nrenderDistance:3\nsimulationDistance:5\nmaxFps:20\nenableVsync:false\nguiScale:1\nlang:zh_cn\nfullscreen:false\nsoundCategory_master:0.0\nautoJump:false\ntutorialStep:none\ngraphicsMode:0\nparticles:2\nkey_key.maid_weapon.danmaku:key.keyboard.g\n')
env['DISPLAY']=a.display;env['LIBGL_ALWAYS_SOFTWARE']='1';env['JAVA_TOOL_OPTIONS']=env.get('JAVA_TOOL_OPTIONS','')+' -Dcontractblade.danmaku.acceptance=true'
with (out/'observer.log').open('w') as log:raise SystemExit(subprocess.call(recipe,cwd=out,env=env,stdout=log,stderr=subprocess.STDOUT))

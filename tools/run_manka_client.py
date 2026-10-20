"""Launch an actual Forge client using a copied stopped fixture world. Requires an X11 OpenGL display."""
import argparse,os,subprocess,shutil
import nbtlib
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--gradle',default='gradle');p.add_argument('--seed-world',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--skip-assets',action='store_true');a=p.parse_args()
if not os.environ.get('DISPLAY'):raise SystemExit('DISPLAY must identify a tested OpenGL X11 display')
out=a.output.resolve();run=out/'client';world=run/'saves/manka_acceptance';world.parent.mkdir(parents=True,exist_ok=True);shutil.copytree(a.seed_world.resolve(),world)
level=nbtlib.load(world/'level.dat');data=level['Data']
data['allowCommands']=nbtlib.Byte(1);data['GameType']=nbtlib.Int(1);data['DayTime']=nbtlib.Long(6000)
for name in ['doMobSpawning','doDaylightCycle','doWeatherCycle']:
 data['GameRules'][name]=nbtlib.String('false')
level.save()
(run/'options.txt').write_text('renderDistance:3\nsimulationDistance:3\nmaxFps:30\nenableVsync:false\nguiScale:2\nlang:zh_cn\nfullscreen:false\nsoundCategory_master:0.0\nautoJump:false\n')
init=out/'capture.init.gradle';init.write_text("allprojects { afterEvaluate { if (plugins.hasPlugin('net.minecraftforge.gradle')) { minecraft.runs.client { args '--width','1280','--height','720' } } } }\n")
env=dict(os.environ,TZ='UTC');env['JAVA_TOOL_OPTIONS']=env.get('JAVA_TOOL_OPTIONS','')+' -Dcontractblade.danmaku.acceptance=true'
cmd=[a.gradle,'--no-daemon','runClient','-Pci=true','-PtlmRuntimeTest=true','-PcompatTest=slashblade','-PakatsukiVisualWorld=manka_acceptance',f'-PcompatRunDir={run}','-I',str(init)]
if a.skip_assets:cmd+=['-x','downloadAssets']
with (out/'client.log').open('w') as log:raise SystemExit(subprocess.call(cmd,cwd=Path(__file__).resolve().parents[1],env=env,stdout=log,stderr=subprocess.STDOUT))

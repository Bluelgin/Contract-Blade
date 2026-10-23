"""Disposable real dedicated Forge/TLM run with an explicitly labelled FakePlayer fixture."""
import argparse,os,subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--gradle',default='gradle');p.add_argument('--output',type=Path,required=True)
a=p.parse_args();out=a.output.resolve();run=out/'server';run.mkdir(parents=True,exist_ok=False)
(run/'eula.txt').write_text('eula=true\n')
(run/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nlevel-type=minecraft:flat\nlevel-name=Manka Native Validation\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\n')
env=dict(os.environ,TZ='UTC');env['JAVA_TOOL_OPTIONS']=env.get('JAVA_TOOL_OPTIONS','')+' -Dcontractblade.danmaku.nativeValidation=true'
with (out/'server.log').open('w') as log:
 result=subprocess.run([a.gradle,'--no-daemon','runServer','-Pci=true','-PtlmRuntimeTest=true','-PcompatTest=slashblade',f'-PcompatRunDir={run}','-x','downloadAssets'],cwd=Path(__file__).resolve().parents[1],env=env,stdout=log,stderr=subprocess.STDOUT,timeout=900)
text=(out/'server.log').read_text()
runtime=run/'logs/latest.log'
if runtime.exists():text+='\n'+runtime.read_text()
if result.returncode or 'MANKA_NATIVE_PASS' not in text or 'MANKA_NATIVE_FAIL' in text: raise SystemExit('FAIL: '+str(out/'server.log'))
print('PASS: '+str(out/'server.log'))

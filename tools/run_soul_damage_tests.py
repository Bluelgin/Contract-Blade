"""Run real Forge HP comparisons in a new disposable world, requiring SOUL_SERVER_PASS.

Needs the existing TLM/SlashBlade/BladeTetra development runtimes. --bosses also
needs tmp/compat-test/{cataclysm,legendary,lionfish,curios}-soul-test-1.jar;
--goety needs {goety,revelation,ending,curios}-soul-test-1.jar. These are read-only
local test inputs and are never bundled. See docs/archive/akatsuki/AKATSUKI_SOUL_DAMAGE.md.
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--gradle', default='gradle')
p.add_argument('--bosses', action='store_true')
p.add_argument('--torches', action='store_true', help='Add official Torches 0.5.0-hotfix2 and its dependencies')
p.add_argument('--mowzie', action='store_true', help='Exact Mowzie 1.8.2 native weakness/death fixture')
p.add_argument('--goety', action='store_true')
p.add_argument('--goety-only',action='store_true')
p.add_argument('--output', type=Path)
args = p.parse_args()
root = args.output or Path(tempfile.mkdtemp(prefix='contract-soul-'))
root.mkdir(parents=True, exist_ok=True)
if (root / 'soul-validation').exists():
    raise SystemExit('Refusing to reuse a world; specify a new output directory')
(root / 'eula.txt').write_text('eula=true\n')
(root / 'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n'
    'level-type=minecraft:flat\nlevel-name=soul-validation\ngenerate-structures=false\n'
    'view-distance=2\nsimulation-distance=2\nspawn-protection=0\n')
command = [args.gradle, '--no-daemon', 'runServer', '-x', 'downloadAssets', '-Pci=true',
    '-PtlmRuntimeTest=true', '-PfoxBladeTetraTest=true', '-PsoulTest=true', f'-PcompatRunDir={root}']
if args.torches:
    command.append('-PtorchesTest=true')
if args.bosses:
    command.append('-PsoulBossTest=true')
if args.mowzie:
    command.append('-PsoulMowzieTest=true')
if args.goety_only:
    command.append('-PsoulGoetyOnlyTest=true')
if args.goety:
    command.append('-PsoulGoetyTest=true')
log = root / 'server.log'
print(f'Real HP fixture: {root}', flush=True)
with log.open('w') as out:
    result = subprocess.run(command, cwd=ROOT, env=os.environ.copy(), stdout=out,
                            stderr=subprocess.STDOUT, timeout=1200)
content = log.read_text(errors='replace')
for line in content.splitlines():
    if any(marker in line for marker in ('SOUL_WROUGHTNAUT_', 'SOUL_RECOVERY_LEVEL','SOUL_REAL_BOSS_PROVENANCE','SOUL_HIGH_BUDGET','SOUL_PROVENANCE_BENCHMARK','SOUL_HP_COMPARISON', 'SOUL_REAL_BOSS_COMPARISON',
                                         'SOUL_BOSS_NOT_INSTALLED', 'SOUL_SERVER_')):
        print(line)
if result.returncode or 'SOUL_SERVER_PASS' not in content or 'SOUL_SERVER_FAIL' in content:
    raise SystemExit(f'Fixture failed: {log}')
print(f'SOUL_SERVER_PASS; full raw evidence: {log}')

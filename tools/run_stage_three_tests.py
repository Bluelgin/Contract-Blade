"""Run stage-three fixtures in fresh disposable Forge worlds; require gameplay PASS markers.

Requires Java 17, Gradle 8.7, local TLM 1.5.3 and optional SlashBlade 1.9.65.
Nothing opens a user save. Logs/worlds are outside the repository.
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--gradle', default='gradle')
parser.add_argument('--offline', action='store_true')
parser.add_argument('--fixture', choices=['recovery','rebirth','fox','shrine','sword','blessing','white-se','architecture'])
args = parser.parse_args()
root = Path(tempfile.mkdtemp(prefix='contract-stage-three-'))
print(f'Disposable fixtures: {root}', flush=True)
fixtures = [
    ('architecture', ['-ParchitectureTest=true'], ['CONTRACT_ARCHITECTURE_PASS']),
    ('recovery', ['-PstageThreeTest=true'], ['CONTRACT_STAGE_THREE_PASS']),
    ('rebirth', ['-PcontractRebirthTest=true'], ['CONTRACT_REBIRTH_PASS','CONTRACT_DESTRUCTION_PASS','MULTI_CARRIER_LOSS_VALIDATION_PASSED','COMPANION_VALIDATION_PASS']),
    ('fox', ['-PmasteryFoxTest=true'], ['BLACK_FOX_SHRINE_RETURN_PASS','CONTRACT_MASTERY_FOX_PASS']),
    ('shrine', ['-PfoxTest=true'], ['SHRINE_NATIVE_PASS','FOX_TRANSFER_NATIVE_PASS','FOX_COMPANION_NATIVE_PASS']),
    ('white-se', ['-PwhiteFoxSeTest=true'], ['WHITE_FOX_SE_PASS']),
    ('blessing', ['-PwhiteBlessingTest=true'], ['WHITE_BLESSING_PASS']),
    ('sword', ['-PswordCoreTest=true'], ['SWORD_CORE_NATIVE_PASS','SWORD_CORE_RELEASE_PASS','SWORD_CORE_MASTERY_CREDIT_PASS','SWORD_CORE_GROWTH_PASS','SWORD_CORE_MULTI_CREDIT_PASS']),
]
failed = []
for name, flags, markers in fixtures:
    if args.fixture and args.fixture != name:
        continue
    directory = root / name
    directory.mkdir()
    (directory / 'eula.txt').write_text('eula=true\n')
    (directory / 'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n'
        'level-type=minecraft:flat\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\n')
    cmd = [args.gradle,'--no-daemon','runServer','-x','downloadAssets','-Pci=true','-PtlmRuntimeTest=true',
           '-PcompatTest=slashblade',f'-PcompatRunDir={directory}',*flags]
    if args.offline:
        cmd.append('--offline')
    log = root / f'{name}.log'
    try:
        with log.open('w') as output:
            result = subprocess.run(cmd,cwd=ROOT,env=os.environ.copy(),stdout=output,stderr=subprocess.STDOUT,timeout=900)
        content = log.read_text()
        runtime_log = directory / 'logs' / 'latest.log'
        if runtime_log.exists(): content += '\n' + runtime_log.read_text()
        missing = [marker for marker in markers if marker not in content]
        if result.returncode or missing or '_FAIL' in content:
            failed.append(name); print(f'{name}: FAIL missing={missing}; {log}',flush=True)
        else:
            print(f'{name}: PASS ({", ".join(markers)})',flush=True)
            if name in ['recovery','blessing']:
                restart_log = root / f'{name}-restart.log'
                with restart_log.open('w') as output:
                    resumed = subprocess.run([*cmd, '-PstageThreeResume=true' if name == 'recovery' else '-PwhiteBlessingResume=true'],cwd=ROOT,env=os.environ.copy(),stdout=output,stderr=subprocess.STDOUT,timeout=900)
                resumed_text = restart_log.read_text()
                if runtime_log.exists(): resumed_text += '\n' + runtime_log.read_text()
                if resumed.returncode or ('CONTRACT_STAGE_THREE_RESTART_PASS' if name == 'recovery' else 'WHITE_BLESSING_RESTART_PASS') not in resumed_text or '_FAIL' in resumed_text:
                    failed.append(f'{name}-restart');print(f'{name}-restart: FAIL; {restart_log}',flush=True)
                else:
                    print(f'{name}-restart: PASS',flush=True)
    except subprocess.TimeoutExpired:
        failed.append(name); print(f'{name}: TIMEOUT; {log}',flush=True)
if failed:
    raise SystemExit(f'Failed fixtures: {", ".join(failed)}')

"""Run isolated Forge servers and require explicit fixture PASS markers, not Gradle's exit status alone.

Requires Java 17 and Gradle 8.7. --native additionally requires the local TLM and
SlashBlade jars documented in CONTRACT_MASTERY.md. Never accepts an existing world.
"""
import argparse
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--gradle', default='gradle')
parser.add_argument('--offline', action='store_true')
parser.add_argument('--native', action='store_true')
args = parser.parse_args()
run_root = Path(tempfile.mkdtemp(prefix='contract-mastery-'))
print(f'Disposable fixtures and logs: {run_root}', flush=True)
failures = []


def fixture(name, flags, expected, resume=False):
    directory = run_root / ('mastery' if resume else name)
    if not resume:
        directory.mkdir()
        (directory / 'eula.txt').write_text('eula=true\n')
        (directory / 'server.properties').write_text(
            'online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n'
            'level-type=minecraft:flat\ngenerate-structures=false\n'
            'view-distance=2\nsimulation-distance=2\nspawn-protection=0\n')
    command = [args.gradle, '--no-daemon', 'runServer', '-x', 'downloadAssets',
               '-Pci=true', f'-PcompatRunDir={directory}', *flags]
    if args.offline:
        command.append('--offline')
    log = run_root / f'{name}.log'
    with log.open('w') as output:
        result = subprocess.run(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT,
                                timeout=900, env=os.environ.copy())
    content = log.read_text()
    if result.returncode or expected not in content or '_FAIL' in content:
        failures.append(name)
        print(f'{name} FAILED; inspect {log}', flush=True)
        return False
    print(f'{name}: {expected}', flush=True)
    return True


if not fixture('mastery', ['-PgalleryPreview=true', '-PmasteryTest=true'], 'CONTRACT_MASTERY_VALIDATION_PASS'):
    raise SystemExit('Initial mastery fixture failed; restart test cannot proceed')
fixture('restart', ['-PgalleryPreview=true', '-PmasteryTest=true', '-PmasteryResume=true'],
        'CONTRACT_MASTERY_RESTART_PASS', resume=True)
if args.native:
    optional = ['-PtlmRuntimeTest=true', '-PcompatTest=slashblade']
    for name, flag, marker in [
            ('sword-core', 'swordCoreTest', 'SWORD_CORE_NATIVE_PASS'),
            ('fox-reward', 'masteryFoxTest', 'CONTRACT_MASTERY_FOX_PASS'),
            ('black-fox', 'blackFoxTest', 'BLACK_FOX_BOSS_PASS'),
            ('fox-transfer', 'foxTest', 'SHRINE_NATIVE_PASS'),
            ('fox-arena', 'foxChallengeTest', 'FOX_CHALLENGE_PASS')]:
        fixture(name, [*optional, f'-P{flag}=true'], marker)

if failures:
    raise SystemExit('Failed fixtures: ' + ', '.join(failures))

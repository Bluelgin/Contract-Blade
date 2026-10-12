"""Run phase-two native fixtures in fresh disposable worlds; require explicit PASS markers.

Requires Java 17, Gradle 8.7, local TLM 1.5.3 and SlashBlade Resharped 1.9.65
at the paths documented in BLACK_FOX_SWORD_CORE_GROWTH.md. Never opens a user world.
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
parser.add_argument('--fixture', choices=['sword-core','fox-reward','mastery'], help='Run one fixture after a scoped fix')
args = parser.parse_args()
root = Path(tempfile.mkdtemp(prefix='sword-core-growth-'))
print(f'Disposable fixtures/logs: {root}', flush=True)
failed = []
fixtures = [
    ('sword-core', ['-PtlmRuntimeTest=true', '-PcompatTest=slashblade', '-PswordCoreTest=true'],
     ['SWORD_CORE_NATIVE_PASS', 'SWORD_CORE_RELEASE_PASS', 'SWORD_CORE_MASTERY_CREDIT_PASS',
      'SWORD_CORE_GROWTH_PASS', 'SWORD_CORE_MULTI_CREDIT_PASS']),
    ('fox-reward', ['-PtlmRuntimeTest=true', '-PcompatTest=slashblade', '-PmasteryFoxTest=true'],
     ['BLACK_FOX_SHRINE_RETURN_PASS', 'CONTRACT_MASTERY_FOX_PASS']),
    ('mastery', ['-PgalleryPreview=true', '-PmasteryTest=true'], ['CONTRACT_MASTERY_VALIDATION_PASS']),
]
for name, flags, markers in fixtures:
    if args.fixture and args.fixture != name:
        continue
    directory = root / name
    directory.mkdir()
    (directory / 'eula.txt').write_text('eula=true\n')
    (directory / 'server.properties').write_text(
        'online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n'
        'level-type=minecraft:flat\ngenerate-structures=false\n'
        'view-distance=2\nsimulation-distance=2\nspawn-protection=0\n')
    command = [args.gradle, '--no-daemon', 'runServer', '-x', 'downloadAssets', '-Pci=true',
               f'-PcompatRunDir={directory}', *flags]
    if args.offline:
        command.append('--offline')
    log = root / f'{name}.log'
    try:
        with log.open('w') as output:
            result = subprocess.run(command, cwd=ROOT, env=os.environ.copy(), stdout=output,
                                    stderr=subprocess.STDOUT, timeout=900)
        text = log.read_text()
        missing = [marker for marker in markers if marker not in text]
        if result.returncode or missing or '_FAIL' in text:
            failed.append(name)
            print(f'{name}: FAIL (missing={missing}); inspect {log}', flush=True)
        else:
            print(f'{name}: PASS ({", ".join(markers)})', flush=True)
    except subprocess.TimeoutExpired:
        failed.append(name)
        print(f'{name}: TIMEOUT; inspect {log}', flush=True)
if failed:
    raise SystemExit(f'Failed fixtures: {", ".join(failed)}')

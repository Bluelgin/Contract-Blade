"""Launch the real graphical Akatsuki fixture in a disposable save and capture rendered frames.

Requires local TLM, SlashBlade 1.9.65, BladeTetra 1.5.9, Tetra and mutil runtime JARs
(the same paths as core CI). Requires a real DISPLAY or a software OpenGL X server.
No user save is opened. A complete run takes about 30 seconds after world loading.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--gradle', default='gradle')
parser.add_argument('--output', type=Path)
parser.add_argument('--skip-assets', action='store_true', help='Use preseeded assets; audio may be incomplete')
args = parser.parse_args()
root = args.output or Path(tempfile.mkdtemp(prefix='contract-akatsuki-visual-'))
root.mkdir(parents=True, exist_ok=True)
server = root / 'seed-server'
client = root / 'client'
server.mkdir(exist_ok=True)
client.mkdir(exist_ok=True)
(server / 'eula.txt').write_text('eula=true\n')
(server / 'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n'
    'level-type=minecraft:flat\nlevel-name=Akatsuki Visual Validation\ngenerate-structures=false\n'
    'view-distance=2\nsimulation-distance=2\nspawn-protection=0\n')
common = [args.gradle, '--no-daemon', '-Pci=true', '-PtlmRuntimeTest=true', '-PcompatTest=slashblade']
print(f'Disposable graphical fixture: {root}', flush=True)
seed_env = os.environ.copy()
seed_env.pop('DISPLAY', None)
with (root / 'seed-server.log').open('w') as log:
    result = subprocess.run([*common, 'runServer', '-x', 'downloadAssets', '-PakatsukiDomainTest=true',
        f'-PcompatRunDir={server}'], cwd=ROOT, env=seed_env, stdout=log, stderr=subprocess.STDOUT, timeout=900)
if result.returncode or 'AKATSUKI_DOMAIN_SERVER_PASS' not in (root / 'seed-server.log').read_text():
    raise SystemExit(f'World seed fixture failed; see {root / "seed-server.log"}')
save = client / 'saves' / 'akatsuki-visual-validation'
if save.exists():
    raise SystemExit(f'Refusing to replace existing save: {save}')
shutil.copytree(server / 'Akatsuki Visual Validation', save)
(client / 'options.txt').write_text('renderDistance:3\nsimulationDistance:3\nmaxFps:30\n'
    'enableVsync:false\ngamma:1.0\nguiScale:2\nonboardAccessibility:false\n'
    'tutorialStep:none\npauseOnLostFocus:false\nsoundCategory_master:0.0\n')
with (root / 'client.log').open('w') as log:
    try:
        result = subprocess.run([*common, 'runClient', *(['-x', 'downloadAssets'] if args.skip_assets else []), '-PfoxBladeTetraTest=true',
            '-PakatsukiVisualTest=true', '-PakatsukiVisualWorld=akatsuki-visual-validation',
            f'-PcompatRunDir={client}'], cwd=ROOT, env=os.environ.copy(), stdout=log,
            stderr=subprocess.STDOUT, timeout=900)
    except subprocess.TimeoutExpired:
        raise SystemExit(f'Client timed out; inspect {root / "client.log"}')
print(f'Rendered frames: {client / "screenshots"}', flush=True)
if result.returncode:
    raise SystemExit(f'Client failed; see {root / "client.log"}')

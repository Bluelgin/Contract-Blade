#!/usr/bin/env python3
"""Prepare ONLY a clean disposable checkout. Never installs into a regular branch."""
import argparse
import shutil
import subprocess
from pathlib import Path

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--master', type=Path, required=True)
p.add_argument('--target', type=Path, required=True)
p.add_argument('--platform', choices=['forge', 'neo'], required=True)
p.add_argument('--disposable-checkout', action='store_true', required=True)
a = p.parse_args()
target, master = a.target.resolve(), a.master.resolve()
def git(*args):
    return subprocess.check_output(['git', '-C', str(target), *args], text=True).strip()
if target == master or git('status', '--porcelain'):
    p.error('target must be a distinct clean disposable checkout')
if git('branch', '--show-current'):
    p.error('target must be detached; do not experiment on a maintenance branch')
expected = '2955432290cb6475e8dfceb960e9a0c388be085f' if a.platform == 'neo' else 'd1429a46492783c4e773d6ccb17b606ca48f5113'
if git('rev-parse', 'HEAD') != expected:
    p.error('unexpected target HEAD; review prototype against the new APIs first')
if subprocess.check_output(['git', '-C', str(master), 'rev-parse', 'HEAD'], text=True).strip() != 'd1429a46492783c4e773d6ccb17b606ca48f5113':
    p.error('unexpected master resource source')
here = Path(__file__).resolve().parent
pack_name = 'contract-fox-1.0.1.zip' if a.platform == 'neo' else 'contract-blade-fox-1.0.0.zip'
runtime = target / 'run/tlm_custom_pack' / pack_name
if runtime.exists():
    p.error('runtime model pack already exists; refusing to replace a user pack')
name = 'NeoVisualAudit' if a.platform == 'neo' else 'ForgeVisualAudit'
package = 'src/port/java/com/maidweapon/port/audit' if a.platform == 'neo' else 'src/main/java/com/maidweapon/forge/compat/fox'
destination = target / package / (name + '.java')
destination.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(here / (name + '.java'), destination)
resources = master / 'src/main/resources'
if a.platform == 'neo':
    for relative in ('shaders/core/black_fox_energy.json', 'shaders/core/black_fox_energy.vsh',
                     'shaders/core/black_fox_energy.fsh', 'textures/effect/black_fox_rift_energy.png',
                     'textures/item/awakening_vein.png', 'textures/item/awakening_vein.png.mcmeta',
                     'textures/gui/danmaku/wheel_base.png'):
        out = target / 'src/port/resources/assets/maid_weapon' / relative
        out.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(resources / 'assets/maid_weapon' / relative, out)
    # Only the disposable dev item is redirected. No formal registration is claimed.
    shutil.copy2(resources / 'assets/maid_weapon/models/item/awakening_vein.json',
                 target / 'src/port/resources/assets/maid_weapon_port_probe/models/item/vein_one.json')
runtime.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(resources / 'modelpacks/contract-fox-1.0.1.zip', runtime)
print('Prepared isolated overlay; do not publish its JAR. Run instructions: visual/README.md')

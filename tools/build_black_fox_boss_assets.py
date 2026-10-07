"""Extract the standalone Boss assets; never edit or reinstall the companion model pack."""
from pathlib import Path
from copy import deepcopy
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
SOURCE = RES / 'modelpacks/contract-fox-1.0.1.zip'


def write_json(path, value):
    target = RES / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')


def pose(bones):
    return {'loop': 'hold_on_last_frame', 'animation_length': 1,
            'bones': {name: {'rotation': rotation} for name, rotation in bones.items()}}


def armed_walk(value):
    # Original run has maid-only head/item queries. Boss always holds a blade and faces its target.
    if isinstance(value, str):
        if value == '0.4*ysm.head_yaw':
            return 0
        if value.startswith('ysm.has_mainhand?'):
            return float(value.split('?', 1)[1].split(':', 1)[0])
        return value
    if isinstance(value, list):
        return [armed_walk(v) for v in value]
    if isinstance(value, dict):
        return {k: armed_walk(v) for k, v in value.items()}
    return value


with zipfile.ZipFile(SOURCE) as source:
    main = json.loads(source.read('assets/contract_fox/animation/fox_black.main.animation.json'))
    animations = {'contract_fox_boss.walk': armed_walk(deepcopy(main['animations']['run']))}
    animations['contract_fox_boss.walk']['bones']['LeftArm'] = {'rotation': [0, 0, -8]}
    animations['contract_fox_boss.walk']['bones']['RightArm'] = {'rotation': [0, 0, 8]}
    animations['contract_fox_boss.ready'] = pose({'Root': [0, 0, 0], 'UpperBody': [0, 0, 0],
        'LeftArm': [0, 0, -8], 'RightArm': [0, 0, 8], 'LeftLeg': [0, 0, -4], 'RightLeg': [0, 0, 4]})
    animations['contract_fox_boss.stagger'] = pose({'Root': [20, 0, 0], 'UpperBody': [15, 0, -8],
        'Head': [15, 0, 0], 'RightArm': [-25, 0, 35], 'LeftArm': [-15, 0, -30], 'LeftLeg': [-20, 0, 0]})
    animations['contract_fox_boss.defeated'] = pose({'Root': [12, 0, 0], 'UpperBody': [15, 0, 0],
        'Head': [20, 0, 0], 'LeftArm': [0, 0, -10], 'RightArm': [0, 0, 10], 'LeftLeg': [-25, 0, 0]})
    # Leaving native B must not retain its shoulder/elbow/head pose in a mental attack.
    for clip in animations.values():
        for bone in ('AllBody', 'Head', 'RightForeArm', 'LeftForeArm', 'RightHand', 'LeftHand',
                     'BladeLocator', 'LeftHandLocator', 'LongHair'):
            clip['bones'].setdefault(bone, {'rotation': [0, 0, 0]})
    animations['contract_fox_boss.tails'] = {'loop': True, 'animation_length': 3, 'bones': {
        name: {'rotation': {'0.0': [0, -angle, 0], '1.5': [0, angle, 0], '3.0': [0, -angle, 0]}}
        for name, angle in [('Tail', 3), ('LeftSpirit_Tail', 4), ('RightSpirit_Tail', -4)]}}
    geometry = json.loads(source.read('assets/contract_fox/models/entity/fox_black.json'))
    geometry['minecraft:geometry'][0]['description']['identifier'] = 'geometry.maid_weapon.black_fox_boss'
    # Battle-only hip attachment. The companion's SheathLocator follows its hand, not its waist.
    geometry['minecraft:geometry'][0]['bones'].append({
        'name': 'BossSheathLocator', 'parent': 'DownBody', 'pivot': [4.5, 21, 1]})
    write_json('assets/maid_weapon/geo/black_fox_boss.json', geometry)
    write_json('assets/maid_weapon/animations/black_fox_boss.animation.json',
               {'format_version': '1.8.0', 'animations': animations})
    for target, original in {
        'assets/maid_weapon/textures/entity/black_fox_boss.png': 'assets/contract_fox/textures/entity/fox_black.png',
        'licenses/black_fox/CREDITS.md': 'CREDITS.md',
        'licenses/black_fox/LICENSE-CC-BY-NC-SA-4.0.txt': 'LICENSE-CC-BY-NC-SA-4.0.txt'
    }.items():
        path = RES / target
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(source.read(original))
print('BLACK_FOX_COMBAT_ASSETS_PASS: standalone geometry, original texture, five passive animations and credits')

"""Source progression guards and optional complete artifact inspection (not a playtest)."""
import argparse
import io
import json
import re
import struct
import tomllib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
VERSION = re.search(r'^mod_version=(.+)$', (ROOT / 'gradle.properties').read_text(), re.M)[1].strip()

def class_references(data):
    """Read CONSTANT_Class names, not arbitrary strings or external dependencies."""
    assert data[:4] == b'\xca\xfe\xba\xbe'
    count = struct.unpack_from('>H', data, 8)[0]
    offset, index = 10, 1
    strings, classes = {}, []
    widths = {3: 4, 4: 4, 5: 8, 6: 8, 8: 2, 9: 4, 10: 4, 11: 4,
              12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        tag = data[offset]
        offset += 1
        if tag == 1:
            length = struct.unpack_from('>H', data, offset)[0]
            offset += 2
            strings[index] = data[offset:offset + length].decode('utf-8', errors='replace')
            offset += length
        elif tag == 7:
            classes.append(struct.unpack_from('>H', data, offset)[0])
            offset += 2
        else:
            offset += widths[tag]
            if tag in (5, 6):
                index += 1
        index += 1
    return {strings[i].lstrip('[').removeprefix('L').removesuffix(';')
            if strings[i].startswith('[') else strings[i] for i in classes}

def check_sources():
    recipes = {}
    for path in (RES / 'data/maid_weapon/recipes/altar').glob('*.json'):
        recipe = json.loads(path.read_text())
        recipes[recipe['output']['nbt']['Item']['id']] = recipe
    def available(item, trail=()):
        assert item not in trail, f'Circular altar progression: {trail + (item,)}'
        if item.startswith('minecraft:'):
            return
        assert item in recipes, f'No survival altar source: {item}'
        for ingredient in recipes[item]['ingredients']:
            available(ingredient['item'], trail + (item,))
    for item in ('awakening_vein', 'flowing_vein', 'endless_vein', 'endless_contract', 'manka_talisman'):
        available('maid_weapon:' + item)
    crystal = recipes['maid_weapon:spirit_crystal']
    assert crystal['power'] == 0.2 and len(crystal['ingredients']) == 6
    assert crystal['output']['nbt']['Item']['Count'] == 1
    policy = (ROOT / 'src/main/java/com/maidweapon/forge/system/deployment/ContractWorkPolicy.java').read_text()
    for method in ('combat', 'feeding'):
        assert re.search(r'boolean ' + method + r'\([^)]*\)\s*\{\s*return false;', policy)
    assert 'AUTO_COMBAT.get()' not in policy and 'AUTO_FEED.get()' not in policy
    assert 'ORIGINAL_TASK_TAG' in policy and 'ORIGINAL_SCHEDULE_TAG' in policy
    defense = (ROOT / 'src/main/java/com/maidweapon/forge/system/fox/challenge/BlackFoxDefense.java').read_text()
    controller = (ROOT / 'src/main/java/com/maidweapon/forge/system/fox/challenge/BlackFoxController.java').read_text()
    boss = (ROOT / 'src/main/java/com/maidweapon/forge/entity/BlackFoxBossEntity.java').read_text()
    assert all(token not in defense for token in ('damageAt', 'HIT_INTERVAL', 'OPENING_INTERVAL'))
    assert 'defense.accepted()' not in controller
    assert 'invulnerableTime =' not in boss and 'invulnerableTime =' not in defense
    assert 'now() < guardingUntil' in defense and 'COUNTER_INTERVAL = 60' in defense
    for mechanism in ('corruption.finalStand()', 'seals.active()', 'BlackFoxDimensionTimeline.submerged', 'parryGuarded()'):
        assert mechanism in controller, mechanism
    build = (ROOT / 'build.gradle').read_text(encoding='utf-8')
    assert 'tools/prepare_stable_release.py' not in build, '1.2 must not trim encounters'
    for locale in ('zh_cn', 'en_us'):
        strings = json.loads((RES / f'assets/maid_weapon/lang/{locale}.json').read_text(encoding='utf-8'))
        for page in (6, 7):
            assert strings[f'patchouli.maid_weapon.entry.growth_values.page.{page}']
    print('RELEASE_SOURCE_PASS: survival progression, manual work, current release inputs')

def check_jar(path):
    with zipfile.ZipFile(path) as jar:
        names = set(jar.namelist())
        metadata = tomllib.loads(jar.read('META-INF/mods.toml').decode())
        info = next(m for m in metadata['mods'] if m['modId'] == 'maid_weapon')
        assert info['version'] == VERSION and '${' not in str(metadata)
        assert 'EpicBattle_J' not in info['description'] and '(music)' not in metadata['license']
        required = [
            'com/maidweapon/forge/system/fox/challenge/BlackFoxEncounters.class',
            'com/maidweapon/forge/system/resonance/ResonancePoolSavedData.class',
            'com/maidweapon/forge/system/danmaku/DanmakuCombatService.class',
            'com/maidweapon/forge/system/deployment/ContractCarrierLossService.class',
            'com/maidweapon/forge/system/deployment/ContractDurabilityBridge.class',
            'com/maidweapon/forge/mixin/ContractDurabilityMixin.class',
            'com/maidweapon/forge/compat/fox/mixin/SlashBladeDurabilityMixin.class',
            'com/maidweapon/forge/compat/tlm/ContractMaidRebirthBridge.class',
            'data/maid_weapon/recipes/altar/spirit_crystal.json',
            'data/maid_weapon/contract_ability_progression/sealed_sword_core.json',
            'data/maid_weapon/contract_ability_progression/white_fox_blessing.json',
            'licenses/fox_models/LICENSE-CC-BY-NC-SA-4.0.txt',
        ]
        assert all(n in names for n in required), sorted(set(required)-names)
        assert 'com/maidweapon/forge/system/deployment/ContractCarrierTransferGuard.class' not in names
        assert any(n.startswith('data/maid_weapon/dimension/') and 'challenge' in n for n in names)
        for name in names:
            if name.endswith('.mixins.json'):
                config = json.loads(jar.read(name))
                for cls in config.get('mixins', []) + config.get('client', []) + config.get('server', []):
                    assert (config['package'] + '.' + cls).replace('.', '/') + '.class' in names
        for name in ('spirit_crystal', 'awakening_vein', 'flowing_vein', 'endless_vein', 'endless_contract', 'manka_talisman'):
            resource = f'data/maid_weapon/recipes/altar/{name}.json'
            assert jar.read(resource) == (RES / resource).read_bytes(), resource
        for name in ('flowing_vein', 'endless_vein'):
            resource = f'assets/maid_weapon/textures/item/{name}.png'
            assert jar.read(resource) == (RES / resource).read_bytes()
        for locale in ('zh_cn', 'en_us'):
            resource = f'assets/maid_weapon/lang/{locale}.json'
            assert jar.read(resource) == (RES / resource).read_bytes()
        with zipfile.ZipFile(io.BytesIO(jar.read('modelpacks/contract-fox-1.0.1.zip'))) as pack:
            models = json.loads(pack.read('assets/contract_fox/maid_model.json'))['model_list']
            assert {'contract_fox:fox_white', 'contract_fox:fox_black'}.issubset({m['model_id'] for m in models})
            assert not any('akatsuki' in n for n in pack.namelist())
            assert {m['model_id'] for m in models} == {'contract_fox:fox_white', 'contract_fox:fox_black'}
        assert 'modelpacks/contract-akatsuki-1.0.0.zip' not in names
        assert 'modelpacks/contract-fox-1.0.0.zip' not in names
        assert not any('epicbattle_j_' in n or n.startswith('licenses/black_fox_music/') for n in names)
        sounds = json.loads(jar.read('assets/maid_weapon/sounds.json'))
        assert 'black_fox_music' not in sounds
        development = set()
        for path in (ROOT / 'src/main/java').rglob('*.java'):
            if path.stem.endswith('Validation') or path.stem in {
                'AkatsukiVisualCapture', 'MankaClientMetrics', 'MankaServerMetrics',
                'MankaAcceptanceCommands', 'DevelopmentValidationBootstrap', 'ContractInteriorValidationCommands'
            }:
                prefix = path.relative_to(ROOT / 'src/main/java').with_suffix('').as_posix()
                development.add(prefix)
                assert not any(n == prefix + '.class' or n.startswith(prefix + '$') for n in names), prefix
        for name in names:
            if name.endswith('.class'):
                for reference in class_references(jar.read(name)):
                    assert reference.split('$')[0] not in development, (name, 'Missing development class', reference)
                    assert not reference.startswith('com/maidweapon/forge/client/BlackFoxMusic'), (name, reference)
    print(f'RELEASE_ARTIFACT_PASS: {VERSION}, full encounters, recipes, models, art, licenses, Mixin classes')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path)
    args = parser.parse_args()
    check_sources()
    if args.jar:
        check_jar(args.jar)

"""Validate the shipped artifact, not just the source release switches."""
import io
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / 'build/stable-release/libs/contract-blade-forge-1.20.1-1.1.1.jar'
with zipfile.ZipFile(JAR) as jar:
    names = set(jar.namelist())
    forbidden = [n for n in names if any(s in n for s in
        ['BlackFox', 'FoxChallenge', '/fox/challenge/', 'black_fox', 'fox_challenge', 'fox_passage.json', 'WhiteFoxPurifyingEdge', 'WhiteFoxSpecialEffectCompat'])]
    assert not forbidden, forbidden
    assert 'version="1.1.1"' in jar.read('META-INF/mods.toml').decode().replace(' ', '')
    recipe = json.loads(jar.read('data/maid_weapon/recipes/altar/heartbound_knot.json'))
    items = [i.get('item') for i in recipe['ingredients']]
    assert 'minecraft:ender_pearl' in items and not any(i and 'crystal' in i for i in items)
    for config in ['maid_weapon.mixins.json', 'maid_weapon.slashblade.mixins.json']:
        data = json.loads(jar.read(config))
        for name in data.get('mixins', []) + data.get('client', []) + data.get('server', []):
            assert (data['package'] + '.' + name).replace('.', '/') + '.class' in names
    with zipfile.ZipFile(io.BytesIO(jar.read('modelpacks/contract-fox-1.0.1.zip'))) as pack:
        meta = json.loads(pack.read('assets/contract_fox/maid_model.json'))
        assert {m['model_id'] for m in meta['model_list']} == {'contract_fox:fox_white', 'contract_fox:fox_black'}
    for lang in ['zh_cn', 'en_us']:
        data = json.loads(jar.read(f'assets/maid_weapon/lang/{lang}.json'))
        assert not any('.fox.challenge.' in k or '.fox_passage.' in k for k in data)
        story = (ROOT / 'build/stable-release/release-inputs/java/com/maidweapon/forge/system/fox/ShrineFoxStory.java').read_text(encoding='utf-8')
        assert all('maid_weapon.fox.' + k in data for k in re.findall(r'say\(player, "([^"]+)"', story))
    assert 'com/maidweapon/forge/system/deployment/ContractCarrierLossService.class' in names
    assert 'com/maidweapon/forge/system/deployment/ContractDurabilityBridge.class' in names
    assert 'com/maidweapon/forge/mixin/ContractDurabilityMixin.class' in names
    assert 'com/maidweapon/forge/compat/fox/mixin/SlashBladeDurabilityMixin.class' in names
    assert 'com/maidweapon/forge/system/deployment/ContractCarrierTransferGuard.class' not in names
    assert 'com/maidweapon/forge/compat/tlm/ContractMaidRebirthBridge.class' in names
print('STABLE_RELEASE_ARTIFACT_PASS: 1.1.1, fixed recipe, polished fox models, repairs, no experimental encounter or orphaned Mixins')

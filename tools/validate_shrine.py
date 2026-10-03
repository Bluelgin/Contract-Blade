"""Structure/data guardrails; runtime load/placement still requires Minecraft."""
import json
from pathlib import Path
from inspect_shrine import read_structure

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
structure = read_structure(RES / 'data/maid_weapon/structures/shinkitsu_shrine.nbt')
assert structure['size'] == [47, 15, 47]
assert len(structure['entities']) == 1
entity = structure['entities'][0]['nbt']
assert entity['id'] == 'slashblade:blade_stand_entity'
assert entity['Item']['tag']['MaidWeaponShrineWhiteFox'] == 1
assert entity['Item']['tag']['bladeState']['translationKey'] == 'item.slashblade.fox_white'
assert entity['Item']['ForgeCaps']['Parent']['maxDamage'] == 70
assert len(entity['Item']['tag']['Enchantments']) == 5
assert all(entry['Name'].startswith('minecraft:') for entry in structure['palette'])
sign = next(b['nbt'] for b in structure['blocks'] if b['pos'] == [6, 1, 23])
assert json.loads(sign['front_text']['messages'][0])['text'] == '神狐神社'
for locale in ('zh_cn', 'en_us'):
    text = json.loads((RES / f'assets/maid_weapon/lang/{locale}.json').read_text(encoding='utf-8'))
    for key in ('white.greeting', 'akatsuki.recognize', 'white.answer', 'white.intro',
                'white.request', 'akatsuki.request', 'white.echo'):
        assert text['maid_weapon.fox.' + key]
data = json.loads((RES / 'data/maid_weapon/worldgen/structure/shinkitsu_shrine.json').read_text())
assert data['type'] == 'maid_weapon:shinkitsu_shrine'
biomes = json.loads((RES / 'data/maid_weapon/tags/worldgen/biome/has_shinkitsu_shrine.json').read_text())
assert biomes['values'] == ['minecraft:snowy_plains'], 'Shrine naturally generates only in snowy plains'
for locale in ('zh_cn', 'en_us'):
    text = json.loads((RES / f'assets/maid_weapon/lang/{locale}.json').read_text(encoding='utf-8'))
    for key in ('kills', 'progress', 'cake', 'offer_cake', 'accepted', 'empty_hand', 'stop_sneaking', 'reserved'):
        assert text['maid_weapon.fox.offering.' + key]
    assert '%s' not in text['maid_weapon.fox.offering.kills'], 'Dialogue must not recite numerical progress'
    assert text['maid_weapon.fox.offering.progress'].count('%s') == 2
source = (ROOT / 'src/main/java/com/maidweapon/forge/worldgen/ShinkitsuShrineStructure.java').read_text()
assert 'if (!SlashBladeCompat.isLoaded()) return Optional.empty()' in source
assert 'BladeTetra' not in source, 'Shrine generation must not depend on BladeTetra'
print('SHRINE_RESOURCES_PASS: template, offering state, optional gate, signs and bilingual text')

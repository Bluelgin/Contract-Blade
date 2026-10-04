import json
import struct
from pathlib import Path

root = Path(__file__).resolve().parents[1]
java = root / 'src/main/java/com/maidweapon/forge'
def read(path):
    return (java / path).read_text(encoding='utf-8')

native = read('compat/tlm/ContractBaubleExtension.java')
slots = read('compat/tlm/TlmProjectionBaubles.java')
runtime = read('system/deployment/ContractMaidRuntimeService.java')
projection = read('compat/ContractEquipmentProjection.java')
work = read('system/deployment/ContractWorkPolicy.java')
assert '@LittleMaidExtension' in native and 'implements ILittleMaid' in native
assert all(item in native for item in ('RESONANCE_SWORD_TASSEL', 'GUARDIAN_RIBBON', 'HEARTBOUND_KNOT'))
assert 'getMaidBauble' in slots and 'getStackInSlot' in slots
assert 'CuriosApi' not in slots and 'getInventory' not in slots
assert 'if (!mode.weapon()) return;' in runtime
assert 'ContractWorkPolicy.release(weapon, maid)' in runtime
assert 'restoreOriginalTask(carrier, maid)' in work and 'clearOriginalTask(carrier)' in work
assert 'owner.getAttributeValue' not in projection
assert 'MaidWeaponOriginalAttack' in projection and 'MaidWeaponOriginalArmor' in projection
assert 'mode.weapon() : mode.armor()' in projection
assert 'returnOriginal(maid, original)' in projection
assert 'isUsingItem()) return' in projection
assert 'ContractCarrierData.clearMaidContract(copy)' in read('compat/TripleMagicCompat.java')
knot = json.loads((root / 'src/main/resources/data/maid_weapon/recipes/altar/heartbound_knot.json').read_text())
assert knot['ingredients'] == [
    {'item': 'maid_weapon:resonance_sword_tassel'}, {'item': 'maid_weapon:guardian_ribbon'},
    {'item': 'minecraft:diamond'}, {'item': 'minecraft:gold_ingot'},
    {'item': 'minecraft:amethyst_shard'}, {'item': 'minecraft:ender_pearl'},
]
assert knot['power'] == 0.3
for item in ('resonance_sword_tassel', 'guardian_ribbon', 'heartbound_knot'):
    assets = root / 'src/main/resources/assets/maid_weapon'
    assert (assets / f'textures/item/{item}.png').is_file()
    animation = json.loads((assets / f'textures/item/{item}.png.mcmeta').read_text())['animation']
    assert animation['width'] == animation['height'] == 32
    expected_frames = 80
    expected_ticks = 2
    assert animation['frametime'] == expected_ticks and animation['interpolate'] is False
    png = (assets / f'textures/item/{item}.png').read_bytes()
    assert struct.unpack('>II', png[16:24]) == (32, 32 * expected_frames)
    recipe = json.loads((root / f'src/main/resources/data/maid_weapon/recipes/altar/{item}.json').read_text())
    assert recipe['type'] == 'touhou_little_maid:altar_crafting'
    assert recipe['output']['nbt']['Item'] == {'id': 'maid_weapon:' + item, 'Count': 1}
    assert recipe['output']['type'] == 'minecraft:item'
    assert len(recipe['ingredients']) == 6 and 0 < recipe['power'] <= 0.3
    assert not (root / f'src/main/resources/data/maid_weapon/recipes/{item}.json').exists()
    model = json.loads((assets / f'models/item/{item}.json').read_text())
    assert model['textures']['layer0'] == f'maid_weapon:item/{item}'
    for locale in ('zh_cn', 'en_us'):
        strings = json.loads((assets / f'lang/{locale}.json').read_text(encoding='utf-8'))
        assert f'item.maid_weapon.{item}' in strings
        assert not strings[f'item.maid_weapon.{item}'].startswith(('item.', 'maid_weapon:'))
        # Native JEI/REI and altar Patchouli output labels use the recipe namespace,
        # not the item translation key, even for normal item outputs.
        assert strings['jei.maid_weapon.altar_craft.item_craft.result']
        assert strings[f'jei.maid_weapon.altar_craft.{item}.result'] == strings[f'item.maid_weapon.{item}']
        for suffix in ('weapon', 'armor', 'combined', 'slot', 'exclusive', 'altar'):
            assert f'maid_weapon.tooltip.projection.{suffix}' in strings
        for suffix in ('weapon.flavor', 'armor.flavor', 'combined.flavor', 'auto_work', 'manual_work'):
            assert strings[f'maid_weapon.tooltip.projection.{suffix}']
        # Player-facing descriptions should not expose implementation/balancing notes.
        for key, value in strings.items():
            if key.startswith(('maid_weapon.tooltip.projection.', 'patchouli.maid_weapon.entry.projection_baubles.')):
                assert not any(term in value for term in ('基础攻击', '护甲数值', '重复计算', '靠前槽位', 'stat copy', 'base attack', 'first equipped slot'))
key = 'contract_interior_key'
animation = json.loads((assets / f'textures/item/{key}.png.mcmeta').read_text())['animation']
assert animation == {'frametime': 2, 'interpolate': False, 'width': 32, 'height': 32}
assert struct.unpack('>II', (assets / f'textures/item/{key}.png').read_bytes()[16:24]) == (32, 32 * 80)
assert json.loads((assets / f'models/item/{key}.json').read_text())['textures']['layer0'] == f'maid_weapon:item/{key}'
print('Native maid baubles, approved four-item animations, projection exclusivity, manual work, stat migration and equipment returns validated')

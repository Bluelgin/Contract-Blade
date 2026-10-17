"""Static boundary/resource regression guards; behavioral tests live in JUnit and the native fixture."""
import json
from pathlib import Path
from xml.etree import ElementTree
from PIL import Image
root=Path(__file__).resolve().parents[1]
java=root/'src/main/java/com/maidweapon'
service=(java/'forge/system/resonance/ResonanceService.java').read_text()
recovery=(java/'forge/system/resonance/ResonanceRecoveryEvents.java').read_text()
bond=(java/'forge/event/MaidBondCombatHandler.java').read_text()
assert 'RESONANCE_PASSIVE_RECOVERY' not in bond, 'old passive recovery must not stack'
assert 'ResonanceContract.resolve' in service and 'attempt.completed' in service
assert 'ContractChannelStorage.channels' in recovery and 'getGameTime()' in recovery
assert 'currentTimeMillis' not in recovery
resolver=(java/'forge/compat/tlm/StoredMaidBaubleResolver.java').read_text()
assert 'ContractMaidStorage.read' in resolver and 'payload.equals' in resolver
assert 'MaidBaubleInventory' in resolver and 'getInventory()' not in resolver
progress=(java/'forge/system/resonance/ResonanceProgressSavedData.java').read_text()
assert 'UUID owner' in progress and 'future.copy()' in progress
for name in ['awakening_vein','flowing_vein','endless_vein','endless_contract']:
    recipe=json.loads((root/f'src/main/resources/data/maid_weapon/recipes/altar/{name}.json').read_text())
    assert recipe['type']=='touhou_little_maid:altar_crafting' and len(recipe['ingredients'])==6
    assert all(x['item'].split(':')[0] in ('minecraft','maid_weapon') for x in recipe['ingredients'])
    assets=root/'src/main/resources/assets/maid_weapon'
    for locale in ['zh_cn','en_us']:
        lang=json.loads((assets/f'lang/{locale}.json').read_text())
        assert lang[f'item.maid_weapon.{name}']
    texture=Image.open(assets/f'textures/item/{name}.png')
    assert texture.size==(32,32) and texture.mode=='RGBA' and texture.getbbox()
    assert texture.getextrema()[3][0]==0 and texture.getextrema()[3][1]==255
    ElementTree.parse(root/f'art/resonance/{name}.svg')
    model=json.loads((assets/f'models/item/{name}.json').read_text())
    assert model['textures']['layer0']==f'maid_weapon:item/{name}'
print('RESONANCE_FOUNDATION_STATIC_PASS: single recovery authority, payment boundary, stored baubles and complete resources')

"""Static resource/client-boundary checks. Does not stand in for visual acceptance."""
import json,hashlib
from pathlib import Path
r=Path(__file__).resolve().parents[1];j=r/'src/main/java/com/maidweapon/forge'
manifest=json.loads((r/'art/danmaku/spellcard_assets.json').read_text())
textures=r/'src/main/resources/assets/maid_weapon/textures/gui/danmaku'
expected={'wheel_base','icon_needle','icon_flower','icon_guard','icon_fantasy','seal_needle','seal_flower','seal_guard','selection','lock','fantasy_layers','petal','intercept_flash','final_seal'}
assert {p.stem for p in textures.glob('*.png')}==expected
approved=json.loads((r/'art/danmaku/spellcard_validation.json').read_text())
hashes={Path(t['path']).name:t['sha256'] for t in approved['textures']}
for p in textures.glob('*.png'):
 assert hashlib.sha256(p.read_bytes()).hexdigest()==hashes[p.name],p
item=r/'src/main/resources/assets/maid_weapon/textures/item/manka_talisman.png'
assert hashlib.sha256(item.read_bytes()).hexdigest()==manifest['item']['sha256']
world=(j/'client/DanmakuWorldPainter.java').read_text();state=(j/'client/DanmakuWorldVisuals.java').read_text()
assert 'tile%2' in world and 'tile/2' in world and 'span=tile<0?1:.5f' in world
assert 'FRAMES.size() < 128' in state and 'PROJECTILES.size() < 256' in state
assert 'subject().equals(entity.getUUID())' in state
mix=json.loads((r/'src/main/resources/maid_weapon.mixins.json').read_text())
assert 'client.ContractDanmakuRenderMixin' in mix['client']
assert all('client.' not in x for x in mix['mixins'])
client_mixin=(j/'mixin/client/ContractDanmakuRenderMixin.java').read_text()
assert 'm_114384_' in client_mixin and 'remap=false' in client_mixin
for locale in ['zh_cn','en_us']:
 v=json.loads((r/f'src/main/resources/assets/maid_weapon/lang/{locale}.json').read_text())
 for form in ['needle','flower','guard','fantasy']: assert v['maid_weapon.danmaku.short.'+form]
assert 'PLAY_TO_CLIENT' in (j/'network/DanmakuNetwork.java').read_text()
print('DANMAKU_VISUAL_STATIC_PASS: approved bytes, item animation hash, atlas UV, bounded timelines, client mixin, locales')

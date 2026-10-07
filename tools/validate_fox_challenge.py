"""Resource and layering checks; real transport/sleep checks live in the native fixture."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'src/main/resources'


def resource(path):
    return json.loads((RESOURCES / path).read_text(encoding='utf-8'))


dimension = resource('data/maid_weapon/dimension/fox_challenge.json')
kind = resource('data/maid_weapon/dimension_type/fox_challenge.json')
biome = resource('data/maid_weapon/worldgen/biome/sealed_rift.json')
assert dimension['type'] == 'maid_weapon:fox_challenge'
settings = dimension['generator']['settings']
assert dimension['generator']['type'] == 'minecraft:flat'
assert settings['layers'] == [{'block': 'minecraft:air', 'height': 1}]
assert settings['biome'] == 'maid_weapon:sealed_rift' and settings['structure_overrides'] == []
assert kind['bed_works'] and not kind['natural'] and not kind['has_raids']
assert kind['effects'] == 'maid_weapon:fox_challenge'
assert not biome['has_precipitation'] and not biome['spawners']
for locale in ('zh_cn', 'en_us'):
    lang = resource(f'assets/maid_weapon/lang/{locale}.json')
    for suffix in ('white.jump', 'white.dream', 'entered', 'leave', 'recall_first'):
        assert lang[f'maid_weapon.fox.challenge.{suffix}']
book = resource('assets/maid_weapon/patchouli_books/contract_fragments/en_us/entries/compatibility/fox_passage.json')
assert book['flag'] == 'mod:slashblade' and len(book['pages']) == 6
java = ROOT / 'src/main/java/com/maidweapon/forge'
atmosphere = (java / 'client/FoxChallengeAtmosphere.java').read_text(encoding='utf-8')
assert 'value = Dist.CLIENT' in atmosphere
for path in (java / 'system/fox/challenge').glob('*.java'):
    source = path.read_text(encoding='utf-8')
    assert 'net.minecraft.client.' not in source and 'dev.bladetetra.' not in source
events = (java / 'event/FoxChallengeEvents.java').read_text(encoding='utf-8')
assert 'FoxChallengeService.LEVEL' in events and 'BucketItem' in events
print('FOX_CHALLENGE_RESOURCES_PASS: void arena, safe dimension, optional book and client/server boundaries')

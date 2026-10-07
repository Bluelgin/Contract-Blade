"""Package the supplied Blockbench model using native exports and adapted TLM animations."""
import base64
import copy
import hashlib
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'art/bladesmith'
SOURCE = ART / 'source/travelling-bladesmith.bbmodel'
TARGET = ART / 'archive/contract-bladesmith-1.0.0.zip'
TARGET.parent.mkdir(parents=True, exist_ok=True)
source = json.loads(SOURCE.read_text(encoding='utf-8'))
assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == '57c91ab7f6fba18f0fc70ffcc395712eaa89f8a389d3b25176181603f789a3d7'
geometry = json.loads((ART / 'runtime/travelling_bladesmith.geo.json').read_text(encoding='utf-8'))
names = {bone['name'] for bone in geometry['minecraft:geometry'][0]['bones']}
assert len(names) == 136 and all(name in names for name in ('Head', 'LeftHandLocator', 'RightHandLocator', 'Tail'))
geometry['minecraft:geometry'][0]['description']['identifier'] = 'geometry.contract_bladesmith.travelling_bladesmith'
own = json.loads((ART / 'runtime/source.animation.json').read_text(encoding='utf-8'))['animations']
domain = 'assets/contract_bladesmith/'
files = {}

def put(name, value):
    files[name] = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')

def adapt(clips):
    clips = copy.deepcopy(clips)
    aliases = {'MAllBody': 'MAllbody', 'Arms': 'Arm', 'Skirt': 'clothe',
               'LeftSkirt': 'LeftClothe', 'RightSkirt': 'RightClothe', 'BackSkirt': 'BackClothe'}
    for clip in clips.values():
        clip['bones'] = {aliases.get(name, name): track for name, track in clip.get('bones', {}).items()
                         if aliases.get(name, name) in names}
        # This model has no alternate quadruped. Never hide it at low health.
        clip['bones'].get('AllBody', {}).pop('scale', None)
    return clips

with zipfile.ZipFile(ROOT / 'src/main/resources/modelpacks/contract-fox-1.0.0.zip') as fox:
    # White Fox's native activity library already adapts the original Wine Fox rig.
    clips_by_label = {label: adapt(json.loads(fox.read(f'assets/contract_fox/animation/fox_white.{label}.animation.json'))['animations'])
                      for label in ('main', 'tac', 'iss', 'im')}
    files['LICENSE-CC-BY-NC-SA-4.0.txt'] = fox.read('LICENSE-CC-BY-NC-SA-4.0.txt')
main = clips_by_label['main']
# Use the supplied single-tail curves rather than the black/white three-tail proposal.
for name, clip in own.items():
    if name.startswith('tail_preview.'):
        activity = name.removeprefix('tail_preview.')
        collection = clips_by_label['tac'] if activity.startswith('tac:') else main
        collection.setdefault(activity, {'loop': True, 'bones': {}})['bones'].update(adapt({name: clip})[name]['bones'])
        if activity == 'pre_parallel0':
            collection[activity]['animation_length'] = clip['animation_length']
# Keep native facial/look controllers and add only the supplied gentle body breathing.
casual = adapt({'idle': own['bladesmith.casual_idle']})['idle']
for bone, track in casual['bones'].items():
    if bone in {'MUpBody', 'UpBody', 'MUpperBody', 'UpperBody', 'MLeftArm', 'MRightArm', 'SakeGourd'}:
        main['idle']['bones'][bone] = track
main['idle']['animation_length'] = 3
main['idle']['loop'] = True
for label, clips in clips_by_label.items():
    for clip in clips.values():
        for bone in list(clip.get('bones', {})):
            assert bone in names
# Preview-only drink expression must stay hidden in ordinary native activities.
main['parallel3']['bones']['DrinkFace'] = {'scale': [0, 0, 0]}
main['parallel3']['bones']['GourdSuspension'] = {'scale': [1, 1, 1]}
special = adapt({name: clip for name, clip in own.items() if name.startswith('bladesmith.')})
clips_by_label['special'] = special
for label, clips in clips_by_label.items():
    put(domain + f'animation/travelling_bladesmith.{label}.animation.json', {'format_version': '1.8.0', 'animations': clips})
put(domain + 'models/entity/travelling_bladesmith.json', geometry)
texture = source['textures'][0]['source']
assert texture.startswith('data:image/png;base64,')
files[domain + 'textures/entity/travelling_bladesmith.png'] = base64.b64decode(texture.split(',', 1)[1])
files[domain + 'textures/maid_icon.png'] = (ROOT / 'art/fox_model_pack/bladesmith_pixel_icon.png').read_bytes()
put(domain + 'maid_model.json', {
    'pack_name': '{maid_pack.contract_bladesmith.name}', 'version': '1.0.0',
    'author': ['Very Many Authors'],
    'description': ['{maid_pack.contract_bladesmith.desc}'], 'date': '2026-10-03',
    'icon': 'contract_bladesmith:textures/maid_icon.png', 'model_list': [{
        'model_id': 'contract_bladesmith:travelling_bladesmith', 'name': '{model.contract_bladesmith.name}',
        'is_gecko': True, 'render_entity_scale': 0.65, 'render_item_scale': 1.0, 'show_backpack': False,
        'description': ['{model.contract_bladesmith.desc}'],
        'animation': [f'contract_bladesmith:animation/travelling_bladesmith.{label}.animation.json'
                      for label in clips_by_label]}]})
for locale, strings in {
    'zh_cn': ['行旅刀匠 · 酒狐', '带着锻锤与刀谱，走过风雪，也记得为故人留一壶酒。',
              '刀匠酒狐', '浅玫瑰色的狐尾，旧围裙上的针脚，腰间的酒葫芦——都是旅途的陪伴。'],
    'en_us': ['Travelling Bladesmith · Wine Fox', 'A hammer, blade notes, and a flask kept for old friends along the snowy road.',
              'Bladesmith Wine Fox', 'Rose-colored fox fur, the stitching of a worn apron, and a sake gourd at her waist accompany her travels.']
}.items():
    put(domain + f'lang/{locale}.json', dict(zip(('maid_pack.contract_bladesmith.name', 'maid_pack.contract_bladesmith.desc',
                                               'model.contract_bladesmith.name', 'model.contract_bladesmith.desc'), strings)))
put('pack.mcmeta', {'pack': {'pack_format': 15, 'description': 'Travelling Bladesmith Wine Fox'}})
files['CREDITS.md'] = b'''# Credits\n\nOriginal Wine Fox: TartaricAcid / Touhou Little Maid model-pack contributors.\nSource adaptation: Blade Tetra contributors, Akatsuki Wine Fox.\nTravelling Bladesmith character: user-supplied travelling-bladesmith-v10.bbmodel.\nRuntime adaptation: Contract Blade, native Blockbench export and TLM activity mapping.\nOriginal 1024x512 character texture and geometry are preserved.\nAnimations derive from the supplied model and the credited White Fox native TLM activity library.\nIcon: hand-authored SVG pixel portrait of Bladesmith Wine Fox. Full attribution remains here; the UI author field is abbreviated.\nAssets remain CC BY-NC-SA 4.0; attribution and this license must accompany derivatives.\nThis pack does not implement a Boss or divine-domain rescue quest.\n'''
files['SHA256SUMS.txt'] = ''.join(f'{hashlib.sha256(data).hexdigest()}  {name}\n' for name, data in sorted(files.items())).encode()
directories = sorted({str(parent).replace('\\', '/') + '/' for name in files
                      for parent in Path(name).parents if str(parent) != '.'})
with zipfile.ZipFile(TARGET, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
    for name in directories:
        archive.writestr(zipfile.ZipInfo(name, (2026, 10, 3, 0, 0, 0)), b'')
    for name, data in sorted(files.items()):
        archive.writestr(zipfile.ZipInfo(name, (2026, 10, 3, 0, 0, 0)), data, compress_type=zipfile.ZIP_DEFLATED)
print(f'BLADESMITH_PACK_BUILT: {len(names)} bones; {sum(len(c["animations"]) for c in [json.loads(files[domain+f"animation/travelling_bladesmith.{l}.animation.json"]) for l in clips_by_label])} animations; original texture preserved')

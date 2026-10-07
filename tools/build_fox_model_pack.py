"""Build a credited runtime derivative without touching the supplied 1.0.0 archive."""
import hashlib
import base64
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'src/main/resources/modelpacks/contract-fox-1.0.0.zip'
TARGET = ROOT / 'src/main/resources/modelpacks/contract-fox-1.0.1.zip'
ICON = ROOT / 'art/fox_model_pack/fox_pixel_icon.png'
assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == '7fc3517d572a09964e97fe42ae68f45c3ad98b108a44b170039341dc0afbe308'
with zipfile.ZipFile(SOURCE) as archive:
    directories = [entry.filename for entry in archive.infolist() if entry.is_dir()]
    files = {entry.filename: archive.read(entry) for entry in archive.infolist() if not entry.is_dir()}

def replace_json(name, data):
    files[name] = (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode('utf-8')

for color in ('white', 'black'):
    name = f'assets/contract_fox/models/entity/fox_{color}.json'
    geometry = json.loads(files[name])
    if '--original-look' not in sys.argv:
        preview = ROOT / 'art/fox_polish_preview/sleeve'
        optimized = json.loads((preview / f'fox_{color}_optimized.bbmodel').read_text(encoding='utf-8'))
        exported = json.loads((preview / f'{color}-optimized.geo.json').read_text(encoding='utf-8'))
        exported['minecraft:geometry'][0]['description']['identifier'] = geometry['minecraft:geometry'][0]['description']['identifier']
        old_names = {b['name'] for b in geometry['minecraft:geometry'][0]['bones']}
        assert old_names <= {b['name'] for b in exported['minecraft:geometry'][0]['bones']}
        geometry = exported
        files[f'assets/contract_fox/textures/entity/fox_{color}.png'] = base64.b64decode(optimized['textures'][0]['source'].split(',', 1)[1])
    crown = next(b for b in geometry['minecraft:geometry'][0]['bones'] if b['name'] == 'FoxMoonCrown')
    assert crown['parent'] == 'AllHead' and len(crown['cubes']) == 6
    # Preserve the named bone for animation compatibility, but remove its visible cubes.
    crown['cubes'] = []
    replace_json(name, geometry)

metadata_path = 'assets/contract_fox/maid_model.json'
metadata = json.loads(files[metadata_path])
metadata['version'] = '1.0.1'
metadata['author'] = ['Very Many Authors']
replace_json(metadata_path, metadata)
for locale, descriptions in {
    'zh_cn': ['相伴而行的黑白双狐，银白与墨紫交织，守望彼此的归途。',
              '银白长发与朱色结饰，映着三条柔软的狐尾。',
              '墨发与紫衣相衬，一双明亮的眼睛静静望向你。'],
    'en_us': ['Two fox spirits, silver-white and violet-black, watching over one another on the journey home.',
              'Silver hair, scarlet knots, and three soft fox tails.',
              'Dark hair, violet robes, and bright eyes quietly watching you.']
}.items():
    name = f'assets/contract_fox/lang/{locale}.json'
    language = json.loads(files[name])
    for key, value in zip(('maid_pack.contract_fox.desc', 'model.contract_fox.fox_white.desc',
                           'model.contract_fox.fox_black.desc'), descriptions):
        language[key] = value
    replace_json(name, language)
files['assets/contract_fox/textures/maid_icon.png'] = ICON.read_bytes()
files['CREDITS.md'] += ('\nRuntime derivative for Contract Blade: crown geometry removed, '
                       'player-facing descriptions and a hand-authored SVG pixel portrait icon. '
                       'Full author attribution remains above despite the abbreviated UI author field. Character textures '
                       'and animations are unchanged. The original attribution and CC BY-NC-SA 4.0 '
                       'license above remain applicable.\n').encode('utf-8')
if '--original-look' not in sys.argv:
    files['CREDITS.md'] += ('\n2026-10-06 visual derivative: fox hair and garment textures refined, '
                           'waist ornaments fitted to clothing, and identical texture regions repacked '
                           'without downsampling detail into a 512x512 atlas. White Fox fully enclosed '
                           'opaque faces are omitted; Black Fox retains all faces for translucent rendering. '
                           'Existing runtime animation files, named bones, model IDs, author attribution '
                           'and license are preserved. Native Blockbench geometry exports are used.\n').encode('utf-8')
files.pop('SHA256SUMS.txt')
notice = ROOT / 'src/main/resources/licenses/fox_models/ASSET-LICENSE-NOTICE.md'
files['ASSET-LICENSE-NOTICE.md'] = notice.read_bytes()
files['MODEL_ASSET_SHA256SUMS.txt'] = ''.join(
    f'{hashlib.sha256(data).hexdigest()}  {name}\n' for name, data in sorted(files.items())
    if name.startswith('assets/contract_fox/') and ('/models/' in name or '/textures/' in name or '/animation/' in name)
).encode('utf-8')
files['CREDITS.md'] += ('\n2026-10-07: bilingual asset license/source notice and model checksums added. '
                       'Earlier "textures unchanged" statements describe only the first crown-only derivative; '
                       'the later garment/atlas refinement is described above. This is provenance documentation, '
                       'not a new authorization or commercial license.\n').encode('utf-8')
license_dir = ROOT / 'src/main/resources/licenses/fox_models'
for name in ('CREDITS.md', 'LICENSE-CC-BY-NC-SA-4.0.txt', 'MODEL_ASSET_SHA256SUMS.txt'):
    (license_dir / name).write_bytes(files[name])
files['SHA256SUMS.txt'] = ''.join(f'{hashlib.sha256(data).hexdigest()}  {name}\n'
                                for name, data in sorted(files.items())).encode('utf-8')
with zipfile.ZipFile(TARGET, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
    # Native resource-pack namespace enumeration also depends on explicit directory entries.
    for name in directories:
        archive.writestr(zipfile.ZipInfo(name, (2026, 10, 3, 0, 0, 0)), b'')
    for name, data in sorted(files.items()):
        archive.writestr(zipfile.ZipInfo(name, (2026, 10, 3, 0, 0, 0)), data,
                         compress_type=zipfile.ZIP_DEFLATED)
print(f'Built {TARGET.name}; source untouched; crowns removed, pixel icon and metadata applied')

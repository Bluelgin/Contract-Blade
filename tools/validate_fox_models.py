"""Verify the original archive and narrowly scoped runtime derivative; not a client visual test."""
import hashlib
import json
import struct
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'src/main/resources/modelpacks/contract-fox-1.0.0.zip'
PACK = ROOT / 'src/main/resources/modelpacks/contract-fox-1.0.1.zip'
assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == (
    '7fc3517d572a09964e97fe42ae68f45c3ad98b108a44b170039341dc0afbe308'
), 'Supplied model archive must remain unchanged, including attribution'

with zipfile.ZipFile(PACK) as archive:
    with zipfile.ZipFile(SOURCE) as original:
        changed = {name for name in original.namelist() if not name.endswith('/')
                   and archive.read(name) != original.read(name)}
        assert changed == {'assets/contract_fox/models/entity/fox_white.json',
                           'assets/contract_fox/models/entity/fox_black.json',
                           'assets/contract_fox/lang/zh_cn.json', 'assets/contract_fox/lang/en_us.json',
                           'assets/contract_fox/maid_model.json', 'assets/contract_fox/textures/maid_icon.png',
                           'CREDITS.md', 'SHA256SUMS.txt'}, changed
        assert archive.read('CREDITS.md').startswith(original.read('CREDITS.md'))
        assert archive.read('LICENSE-CC-BY-NC-SA-4.0.txt') == original.read('LICENSE-CC-BY-NC-SA-4.0.txt')
    for name in ('CREDITS.md', 'LICENSE-CC-BY-NC-SA-4.0.txt', 'SHA256SUMS.txt'):
        assert archive.getinfo(name).file_size > 0
    for line in archive.read('SHA256SUMS.txt').decode('utf-8-sig').splitlines():
        if not line.strip():
            continue
        digest, name = line.split(maxsplit=1)
        name = name.lstrip('*').removeprefix('./')
        assert hashlib.sha256(archive.read(name)).hexdigest() == digest.lower(), name
    metadata = json.loads(archive.read('assets/contract_fox/maid_model.json'))
    assert metadata['version'] == '1.0.1'
    assert metadata['author'] == ['Very Many Authors']
    assert {model['model_id'] for model in metadata['model_list']} == {
        'contract_fox:fox_white', 'contract_fox:fox_black'
    }
    for model in metadata['model_list']:
        assert model['is_gecko'] is True and model['render_entity_scale'] == 0.65
        color = model['model_id'].split(':')[1]
        geometry = json.loads(archive.read(f'assets/contract_fox/models/entity/{color}.json'))
        assert geometry['minecraft:geometry']
        crown = next(b for b in geometry['minecraft:geometry'][0]['bones'] if b['name'] == 'FoxMoonCrown')
        assert not crown.get('cubes')
        texture = archive.read(f'assets/contract_fox/textures/entity/{color}.png')
        assert texture.startswith(b'\x89PNG\r\n\x1a\n')
        assert struct.unpack('>II', texture[16:24]) == (512, 512)
        count = 0
        for animation in model['animation']:
            namespace, path = animation.split(':', 1)
            clips = json.loads(archive.read(f'assets/{namespace}/{path}'))['animations']
            assert clips
            count += len(clips)
        assert count >= 180, (color, count)
        print(f'FOX_MODEL_ASSETS_PASS: {color}, {count} animation resources, 512x512 texture')

print('FOX_ARCHIVE_PASS: preserved source, crowns removed, player descriptions, pixel icon, checksums and license')

"""Check the actual jar's Mod-list metadata, logo and separate asset notices."""
import struct
import re
import sys
import tomllib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
stable = '--stable' in sys.argv
version = re.search(r'^mod_version=(.+)$', (ROOT / 'gradle.properties').read_text(), re.M)[1].strip()
jar_path = ROOT / ('build/stable-release/libs' if stable else 'build/libs') / f'contract-blade-forge-1.20.1-{version}.jar'
with zipfile.ZipFile(jar_path) as jar:
    metadata = tomllib.loads(jar.read('META-INF/mods.toml').decode('utf-8'))
    info = next(mod for mod in metadata['mods'] if mod['modId'] == 'maid_weapon')
    assert '${' not in str(metadata), 'Unexpanded metadata'
    assert info['logoFile'] == 'white-fox-mod-list.png' and info['logoBlur'] is True
    picture = jar.read(info['logoFile'])
    assert picture[:8] == b'\x89PNG\r\n\x1a\n' and picture[12:16] == b'IHDR'
    assert struct.unpack('>II', picture[16:24]) == (256, 179)
    assert picture == (ROOT / 'src/main/resources' / info['logoFile']).read_bytes()
    assert 'MIT (code)' in metadata['license']
    assert 'CC BY-NC-SA-4.0 (fox models)' in metadata['license']
    assert 'licenses/fox_models/' in info['description']
    for name in ['ASSET-LICENSE-NOTICE.md', 'CREDITS.md', 'LICENSE-CC-BY-NC-SA-4.0.txt', 'MODEL_ASSET_SHA256SUMS.txt']:
        assert jar.read('licenses/fox_models/' + name)
    assert 'EpicBattle_J' not in info['description'] and '(music)' not in metadata['license']
    assert not any('epicbattle_j_' in name or name.startswith('licenses/black_fox_music/') for name in jar.namelist())
print('MOD_PRESENTATION_PASS: compact logo, separate code/model licenses and bundled notices; no soundtrack')

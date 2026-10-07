"""Check the actual jar's Mod-list metadata, logo and separate asset notices."""
import struct
import sys
import tomllib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
stable = '--stable' in sys.argv
jar_path = ROOT / ('build/stable-release/libs' if stable else 'build/libs') / 'contract-blade-forge-1.20.1-1.1.1.jar'
with zipfile.ZipFile(jar_path) as jar:
    metadata = tomllib.loads(jar.read('META-INF/mods.toml').decode('utf-8'))
    info = next(mod for mod in metadata['mods'] if mod['modId'] == 'maid_weapon')
    assert '${' not in str(metadata), 'Unexpanded metadata'
    assert info['logoFile'] == 'white-fox-mod-list.png' and info['logoBlur'] is True
    picture = jar.read(info['logoFile'])
    assert picture[:8] == b'\x89PNG\r\n\x1a\n' and picture[12:16] == b'IHDR'
    assert struct.unpack('>II', picture[16:24]) == (485, 340)
    assert picture == (ROOT / 'src/main/resources' / info['logoFile']).read_bytes()
    assert 'MIT (code)' in metadata['license']
    assert 'CC BY-NC-SA-4.0 (fox models)' in metadata['license']
    assert 'licenses/fox_models/' in info['description']
    for name in ['ASSET-LICENSE-NOTICE.md', 'CREDITS.md', 'LICENSE-CC-BY-NC-SA-4.0.txt', 'MODEL_ASSET_SHA256SUMS.txt']:
        assert jar.read('licenses/fox_models/' + name)
    if stable:
        assert 'EpicBattle_J' not in info['description']
        assert 'CC BY-4.0 (music)' not in metadata['license']
        assert not any('black_fox_music/' in name for name in jar.namelist())
    else:
        assert 'EpicBattle_J' in info['description'] and 'CC BY-4.0 (music)' in metadata['license']
        assert jar.read('licenses/black_fox_music/LICENSE-CC-BY-4.0.txt')
print('MOD_PRESENTATION_PASS: cropped logo, code/model license split, bundled notices, ' + ('stable music exclusion' if stable else 'independent music attribution'))

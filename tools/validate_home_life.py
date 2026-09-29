"""Behavioral pure-Java checks plus architecture tripwires for the home boundary."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/com/maidweapon/forge'
HOME = JAVA / 'system/interior/home'
with tempfile.TemporaryDirectory(prefix='contract-home-tests-') as out:
    subprocess.run(['javac', '-d', out, *[str(HOME / f'{name}.java') for name in
        ('ContractHomeClock', 'ContractHomeActivity', 'ContractHomeActivityResolver')],
        str(ROOT / 'tools/tests/HomeClockResolverTest.java')], check=True)
    subprocess.run(['java', '-cp', out, 'HomeClockResolverTest'], check=True)

runtime = (HOME / 'ContractHomeRuntime.java').read_text()
registry = (HOME / 'ContractHomeFurnitureRegistry.java').read_text()
for path in HOME.glob('*.java'):
    source = path.read_text()
    for forbidden in ('setChunkForced(', 'addRegionTicket(', 'createAppearanceProxy(', 'saveWithoutId(',
                      'ContractLifecycleService.manifest(', 'ContractLifecycleService.capture('):
        assert forbidden not in source, f'{path}: unexpected authority/loading API {forbidden}'
assert 'getChunkNow' in registry and 'getChunk(' not in registry.replace('getChunk(),', '')
assert 'getBlockEntities()' in registry and 'MAX_BLOCK_ENTITIES' in registry
assert 'DECISION_INTERVAL = 600' in runtime and 'PATH_TIMEOUT = 400' in runtime
assert 's.failed.add' in runtime and 'adapter.valid' in runtime
terrain = (JAVA / 'system/interior/ContractInteriorTerrainBuilder.java').read_text()
assert 'ContractInteriorBuilder' not in terrain and 'Favorability' not in terrain
saved = (JAVA / 'system/interior/ContractInteriorSavedData.java').read_text()
assert 'HomeLife' in saved and 'value.home.save()' in saved
service = (JAVA / 'system/interior/ContractInteriorService.java').read_text()
assert 'resumeHome' in service and 'ContractLifecycleService.manifest' in service
care = (JAVA / 'system/MaidCareTaskSystem.java').read_text()
assert 'ContractInteriorService.INTERIOR_LEVEL' in care
print('Home Life authority, loaded-only indexing, cadence, terrain and save boundaries validated.')

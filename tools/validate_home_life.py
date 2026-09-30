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
behavior = (JAVA / 'compat/tlm/TlmHomeBehaviorController.java').read_text()
board = (JAVA / 'compat/tlm/TlmHomeBoardGameAdapter.java').read_text()
joy = (JAVA / 'compat/tlm/TlmHomeJoyAdapter.java').read_text()
picnic = (JAVA / 'compat/tlm/TlmHomePicnicAdapter.java').read_text()
events = (JAVA / 'system/interior/ContractInteriorEvents.java').read_text()
for path in HOME.glob('*.java'):
    source = path.read_text()
    for forbidden in ('setChunkForced(', 'addRegionTicket(', 'createAppearanceProxy(', 'saveWithoutId(',
                      'ContractLifecycleService.manifest(', 'ContractLifecycleService.capture('):
        assert forbidden not in source, f'{path}: unexpected authority/loading API {forbidden}'
assert 'getChunkNow' in registry and 'getChunk(' not in registry.replace('getChunk(),', '')
assert 'getBlockEntities()' in registry and 'MAX_BLOCK_ENTITIES' in registry
assert 'DECISION_INTERVAL = 600' in runtime and 'PATH_TIMEOUT = 400' in runtime
assert 'getChunkNow(origin.getX() >> 4, origin.getZ() >> 4)' in runtime
assert 'resident maid plot escape' in (JAVA / 'compat/tlm/ContractHomeValidation.java').read_text()
assert 's.failed.add' in runtime and 'adapter.valid' in runtime
assert 'new TlmHomeBoardGameAdapter()' in registry
assert 'new TlmHomeJoyAdapter()' in registry
assert 'new TlmHomePicnicAdapter()' in registry
assert 'ContractHomeActivity.MEAL' in picnic
assert 'hasFood(tile)' in picnic and 'getStackInSlot' in picnic
assert 'MaidHomeMealTask' in behavior and 'homeMealSupported' in behavior
for block_id in ('bookshelf', 'computer', 'keyboard'):
    assert block_id in joy
assert 'holdManagedSeat' in behavior and 'shouldKeepManagedSeat' in behavior
assert 'maid.hurtTime <= 0' in behavior and 'maid.getAirSupply() >= 200' in behavior
assert 'managed seat yields immediately to emergency behavior' in (JAVA / 'compat/tlm/ContractHomeValidation.java').read_text()
assert 'onManagedSeatDismount' in events and 'EntityMountEvent' in events
assert 'public static boolean restore(Mob maid)' in behavior
assert 'beginBoardGame' in behavior and 'touhou_little_maid:board_games' in behavior
for block_id in ('gomoku', 'cchess', 'wchess'):
    assert block_id in board
terrain = (JAVA / 'system/interior/ContractInteriorTerrainBuilder.java').read_text()
assert 'ContractInteriorBuilder' not in terrain and 'Favorability' not in terrain
saved = (JAVA / 'system/interior/ContractInteriorSavedData.java').read_text()
assert 'HomeLife' in saved and 'value.home.save()' in saved
assert 'public Plot find(String bindingId)' in saved
service = (JAVA / 'system/interior/ContractInteriorService.java').read_text()
deployment = (JAVA / 'system/InfusedMaidDeploymentSystem.java').read_text()
assert 'resumeHome' in service and 'ContractLifecycleService.manifest' in service
assert 'ContractHomeRuntime.prepareCapture(maid)' in service and 'homeReleased' in service
assert 'player.containerMenu.getCarried()' in service and 'player.containerMenu.slots' in service
assert 'rescueActiveContractFromContainer' in service
assert 'player.containerMenu.setCarried(ItemStack.EMPTY)' in service
assert 'player.containerMenu.setCarried(displaced)' in service
assert 'ContractInteriorService.rescueActiveContractFromContainer(serverPlayer)' in deployment
container_close = deployment.split('public static void onContainerClose', 1)[1].split('private static boolean deliverEmergencyFilm', 1)[0]
assert 'ContractInteriorService.INTERIOR_LEVEL' in container_close and 'return;' in container_close
assert 'Same-dimension teleport mods' in service and 'insidePlot(player.blockPosition(), plot)' in service
assert 'recoverDestroyedActiveContract' in service
assert 'createEmergencyResurrectionFilm' in service
interior_events = (JAVA / 'system/interior/ContractInteriorEvents.java').read_text()
assert 'PlayerDestroyItemEvent' in interior_events
assert 'ContractTransferSafetyService.rescueSelfStoredContract(player)' in interior_events
assert 'ContractInteriorService.rescueActiveContractFromContainer(player)' in interior_events
guide = (HOME / 'ContractInteriorGuideService.java').read_text()
assert 'ClickEvent.Action.RUN_COMMAND' in guide
assert '/contractinterior clock minecraft' in guide and '/contractinterior timezone +08:00' in guide
care = (JAVA / 'system/MaidCareTaskSystem.java').read_text()
assert 'ContractInteriorService.INTERIOR_LEVEL' in care
print('Home Life authority, loaded-only indexing, cadence, terrain and save boundaries validated.')

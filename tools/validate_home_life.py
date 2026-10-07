"""Behavioral pure-Java checks plus architecture tripwires for the home boundary."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/com/maidweapon/forge'
HOME = JAVA / 'system/interior/home'
with tempfile.TemporaryDirectory(prefix='contract-home-tests-') as out:
    subprocess.run(['javac', '-d', out, *[str(HOME / f'{name}.java') for name in
        ('ContractHomeClock', 'ContractHomeActivity', 'ContractHomeActivityResolver', 'ContractHomeArrivalPlanner')],
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
    source = path.read_text(encoding='utf-8')
    for forbidden in ('setChunkForced(', 'addRegionTicket(', 'createAppearanceProxy(', 'saveWithoutId(',
                      'ContractLifecycleService.manifest(', 'ContractLifecycleService.capture('):
        assert forbidden not in source, f'{path}: unexpected authority/loading API {forbidden}'
assert 'getChunkNow' in registry and 'getChunk(' not in registry.replace('getChunk(),', '')
assert 'getBlockEntities()' in registry and 'MAX_BLOCK_ENTITIES' in registry
assert 'SNAPSHOT_INTERVAL = 100' in runtime
for removed in ('private static void resolve(', 'private static boolean approach(',
                'private static void fail(', 'new WalkTarget(', 'nextMovement', 'nativeRequested'):
    assert removed not in runtime, f'legacy online control returned: {removed}'
assert 'installHomeBrain' not in behavior and 'new Activity("contract_blade_home")' not in behavior
assert 'getChunkNow(origin.getX() >> 4, origin.getZ() >> 4)' in runtime
assert 'resident maid plot escape' in (JAVA / 'compat/tlm/ContractHomeValidation.java').read_text()
assert 'entry.adapter().valid' in runtime
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
assert 'usesCustomClock(maid)' in behavior, 'default native seats must not be forcibly retained'
assert 'onManagedSeatDismount' in events and 'EntityMountEvent' in events
assert 'public static boolean restore(Mob maid)' in behavior
assert 'beginBoardGame' not in behavior, 'online board-game task hijacking must remain removed'
assert 'public boolean start(' not in board and 'public boolean start(' not in picnic
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
assert 'ContractInteriorService.rescueActiveContractFromContainer(serverPlayer, true)' in deployment
container_close = deployment.split('public static void onContainerClose', 1)[1].split('private static boolean deliverEmergencyFilm', 1)[0]
assert 'ContractInteriorService.INTERIOR_LEVEL' in container_close and 'return;' in container_close
assert 'Same-dimension teleport mods' in service and 'insidePlot(player.blockPosition(), plot)' in service
assert 'recoverDestroyedActiveContract' in service
assert 'createEmergencyResurrectionFilm' in service
interior_events = (JAVA / 'system/interior/ContractInteriorEvents.java').read_text()
assert 'PlayerDestroyItemEvent' not in interior_events
assert 'ContractTransferSafetyService.rescueSelfStoredContract(player)' in interior_events
assert 'ContractInteriorService.rescueActiveContractFromContainer(player)' in interior_events
guide = (HOME / 'ContractInteriorGuideService.java').read_text()
assert 'ClickEvent' not in guide and 'RUN_COMMAND' not in guide
assert 'public static final int GUIDE_VERSION = 4' in guide
assert 'public static final int PAGE_COUNT = 7' in guide
import json
for language in ('zh_cn', 'en_us'):
    translations = json.loads((ROOT / f'src/main/resources/assets/maid_weapon/lang/{language}.json').read_text())
    pages = [translations[f'maid_weapon.home.guide.page.{page}'] for page in range(1, 8)]
    assert all(len(page) <= 280 for page in pages), 'letter pages must stay readable'
    assert 'maid_weapon.home.guide.page.8' not in translations
    if language == 'zh_cn':
        assert '这里就是我们的家' in ''.join(pages)
        assert '灵体显现' in ''.join(pages)
        assert not any(word in ''.join(pages) for word in ('系统说明', '时区', '幻化', '开发者', '契约等级'))
care = (JAVA / 'system/MaidCareTaskSystem.java').read_text()
entity_adapter = (JAVA / 'compat/tlm/TlmEntityAdapter.java').read_text()
assert 'ContractInteriorService.INTERIOR_LEVEL' in care
assert 'ORIGINAL_SCHEDULE_TAG' in care
assert 'TlmEntityAdapter.scheduleName(maid)' in care and 'TlmEntityAdapter.setSchedule(maid, schedule)' in care
assert 'public static String scheduleName(Entity entity)' in entity_adapter
assert 'deployment original schedule restored' in (JAVA / 'compat/tlm/ContractHomeValidation.java').read_text()
equipment = (JAVA / 'compat/tlm/ContractHomeEquipmentGuard.java').read_text()
assert 'mayPlace(ItemStack stack)' in equipment and 'mayPickup(Player player)' in equipment
assert 'PlayerContainerEvent.Open' in equipment and 'EntityInteractSpecific' in equipment
assert 'ContractResidentPositionService.remember(s.maid)' in runtime
lifecycle = (JAVA / 'compat/tlm/ContractMaidLifecycleService.java').read_text()
assert lifecycle.index('ContractResidentPositionService.restore(maid, plot)') < lifecycle.index('!player.level().addFreshEntity(maid)')
assert 'beforeSpawn.accept(maid)' in lifecycle and 'manifested[0]' in service
assert 'ContractHomeRuntime.startOnVisit' in service and 'session.arrivalAt' in runtime
assert 'ContractHomeClock.skyTime' in events and 'ClientboundSetTimePacket' in events
assert 'setDayTime(' not in events, 'home clock must not mutate other players\' shared world clock'
assert 'ContractInteriorGuideVersion' in guide and 'write(existing)' in guide
assert 'else if (arrival)' in runtime and 'prepareArrival(session, player)' in runtime
assert 'enableNativeLiving' in behavior and 'refreshBrain' in behavior
assert 'Observation only: TLM owns walking' in runtime
assert 'syncNativeClock' in behavior and 'ScheduleBuilder' in behavior
assert 'if (s.state.mode != ContractHomeClock.Mode.MINECRAFT_TIME)' in runtime
assert 's.customNight == null || s.customNight != night' in runtime
assert 'Keep the actual native seat/sleep pose' in runtime
assert 'default native schedule retains night' in (JAVA / 'compat/tlm/ContractHomeValidation.java').read_text()
assert 'Anchor native home range to the restored resident' in runtime
assert 'attempts++ < 4' in runtime and 'level.noCollision(s.maid, box)' in runtime
assert 'ContractHomeArrivalPlanner.choose' in runtime
assert 'restored' in service and 'plot, !resumeInteriorMaid)' in service
assert 'DepartedAt' in (HOME / 'ContractHomeOfflineState.java').read_text()
assert 'arrival never steals an occupied chair' in (JAVA / 'compat/tlm/ContractHomeValidation.java').read_text()
# Contract runtime settings are temporary and must not leak into copied/cleared contracts.
runtime_service = (JAVA / 'system/deployment/ContractMaidRuntimeService.java').read_text()
weapon_item = (JAVA / 'system/contract/ContractCarrierData.java').read_text()
intrinsic = (JAVA / 'system/contract/ContractChannelStorage.java').read_text()
prepare_body = runtime_service.split('public static ContractProjectionMode prepare', 1)[1]
assert prepare_body.index('ContractWorkPolicy.prepare(weapon, maid, mode)') < prepare_body.index('setAllDaySchedule(maid)')
assert 'MaidInfusionOriginalSchedule' in weapon_item
assert 'MaidInfusionOriginalSchedule' in intrinsic
print('Home Life authority, loaded-only indexing, cadence, terrain, runtime restoration and save boundaries validated.')

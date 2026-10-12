"""Source boundaries only. Behavioral coverage lives in ContractMasteryTest and opt-in server validation."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
java = root / 'src/main/java'
def read(path): return (java / ('com/maidweapon/' + path)).read_text()
carrier = read('forge/system/contract/ContractCarrierData.java')
writer = carrier.split('public static void setMaidData', 1)[1].split('public static boolean hasMaidData', 1)[0]
for key in ['KEY_LEVEL', 'KEY_TOTAL_KILLS', 'KEY_UNLOCKED_TIER', 'KEY_ENDER_DRAGON_KILLS', 'KEY_WITHER_KILLS', 'KEY_CONFIGURED_PROGRESS']:
    assert key not in writer, f'relationship writer reintroduced item growth authority: {key}'
assert '.copy()' in writer and 'ContractSchema' in writer
combat = read('forge/event/ContractCombatEventHandler.java')
assert 'ContractCombatCredits.settle' in combat and 'ContractMasteryService.tryAdvance' in combat
assert 'data.tryUpgrade' not in combat and 'ChallengeTracker.hasDragon' not in combat
network = read('forge/network/ContractMasteryNetwork.java')
assert 'PLAY_TO_CLIENT' in network and 'PLAY_TO_SERVER' not in network
service = read('forge/system/mastery/ContractMasteryService.java')
assert 'hasPermission(2)' in service and 'ContractMasterySavedData' in service
assert 'ClientSuppliedGrowth' in read('forge/system/mastery/ContractMasteryMigration.java')
assert 'ContractMasteryCreativeGuard' in read('forge/mixin/ContractCreativeGrowthMixin.java')
for name in ['BlackFoxSwordCoreEntity', 'BlackFoxSwordActorEntity']:
    assert 'ContractAbilityProgressionService.query' not in read('forge/entity/' + name + '.java'), 'Entity executor must consume the entry point snapshot, not query its own growth'
for path in (java / 'com/maidweapon/forge/api/ability').glob('*.java'):
    source = path.read_text()
    assert 'BlackFox' not in source and 'mods.flammpfeil' not in source and 'Class.forName' not in source
print('MASTERY_BOUNDARIES_PASS: player authority, archival item fields, read-only sync, optional SA independence')

"""Companion intent, native residence and server-authoritative key guardrails."""
from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
java = root / 'src/main/java/com/maidweapon/forge'
def read(name):
    return (java / name).read_text(encoding='utf-8')
policy = read('system/deployment/ContractCompanionService.java')
deployment = read('system/InfusedMaidDeploymentSystem.java')
network = read('network/ContractCompanionNetwork.java')
keys = read('client/ContractCompanionKeys.java')
tick = deployment.split('public static void onPlayerTick', 1)[1].split('@SubscribeEvent', 1)[0]
assert 'ContractCompanionService.tick(player)' in tick
assert 'manifestDesired(' not in tick and 'PENDING_DEPLOYMENTS' not in deployment
assert 'GLFW_KEY_RIGHT_BRACKET' in keys and 'RegisterKeyMappingsEvent' in keys
assert 'ctx.enqueueWork' in network and 'ctx.getSender()' in network
assert 'NetworkDirection.PLAY_TO_SERVER' in network and 'readNbt' not in network
assert 'NEXT_REQUEST' in policy and 'owner.containerMenu != owner.inventoryMenu' in policy
assert 'owner.getHealth() < injury.health()' in policy and 'owner.isAlive()' in policy
assert 'attacker == owner' in policy and 'attacker instanceof LivingEntity' in policy
assert 'RECALL_RANGE = 32' in policy and 'GUARD_TICKS = 400' in policy
assert 'canFollow(owner, carrier)' in policy and 'canFollow(owner, held)' in policy
assert 'TlmResidenceAdapter.restoreResidence(maid)' in policy
assert 'ContractRulesConfig.MAX_FOLLOWERS.get()' in policy
assert 'ContractActiveDeployments.forgetMaid' in deployment and 'ACTIVE_WEAPONS' not in deployment
assert 'maid.level() != owner.level()' in policy
assert 'MaidInfusion.containsMaid(held)' in policy and 'loaded(owner, held) == null' in policy
assert 'if (resident) continue;' in policy and 'Mode.RESIDENT' in policy
assert 'getChunk(' not in policy and 'addRegionTicket' not in policy
native = read('compat/tlm/TlmResidenceAdapter.java')
assert 'isHomeModeEnable' in native and 'isOrderedToSit' in native
runtime = read('system/deployment/ContractMaidRuntimeService.java')
assert 'TlmResidenceAdapter.isResident(maid)' in runtime
assert 'ContractProjectionMode.NONE' in runtime
fixture = read('compat/tlm/ContractCompanionValidation.java')
attention = read('system/MaidAttentionSystem.java')
assert 'ContractCompanionState.mode(maid)' in attention and 'Mode.GUARD' in attention
voice = attention.split('private static void playPendingVoice', 1)[1].split('private static boolean isStoredOwnedContract', 1)[0]
assert '!isSafeToNotify(owner)' in voice
bond = read('event/MaidBondCombatHandler.java')
bond_tick = bond.split('public static void onPlayerTick', 1)[1].split('private static void tickResonance', 1)[0]
assert 'followingContracts(player)' in bond_tick and 'getMainHandItem' not in bond_tick
assert 'MAID_HITS.get(key + ":" + ContractCarrierData.ensureBindingId(weapon))' in bond
assert 'LAST_COOP_REWARD.put(bindingKey, time)' in bond
for case in ('switched-away companion still recovers resonance',
             'switched-away companion still drains combat resonance',
             'resident stays excluded from following resonance ticks',
             'protective manifestation never queues attention idle voice'):
    assert case in fixture
for case in ('holding a contract never auto manifests', 'resident survives owner logout',
             'unloaded resident never clones', 'lethal injury', 'cancelled damage',
             'exactly 32 blocks', 'safe expired guard', 'cross-world recall'):
    assert case in fixture
for locale in ('zh_cn', 'en_us'):
    lang = json.loads((root / f'src/main/resources/assets/maid_weapon/lang/{locale}.json').read_text(encoding='utf-8'))
    for name in ('hold_contract', 'unavailable', 'interior', 'interior_resident', 'other_world',
                 'not_loaded', 'too_far', 'failed', 'recalled', 'already_accompanied',
                 'dialogue.protect', 'dialogue.greeting', 'dialogue.hurt', 'dialogue.safe'):
        assert lang[f'maid_weapon.companion.{name}']
    for name in ('call_companion', 'recall_companion'):
        assert '%s' in lang[f'maid_weapon.tooltip.{name}']
print('Companion trigger, recall range, native residence, persistence, input and resource guardrails validated')

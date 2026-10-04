from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/maidweapon/forge"
deployment = (JAVA / "system/InfusedMaidDeploymentSystem.java").read_text(encoding="utf-8")
locator = (JAVA / "system/deployment/ContractWeaponLocator.java").read_text(encoding="utf-8")
companion = (JAVA / "system/deployment/ContractCompanionState.java").read_text(encoding="utf-8")
film_service = (JAVA / "compat/tlm/TlmFilmService.java").read_text(encoding="utf-8")

for token in (
    "PlayerDestroyItemEvent",
    "CARRIER_LOSS_CONFIRM_TICKS = 40",
    "PENDING_CARRIER_LOSSES",
    "scheduleCarrierLoss(player, maidId, bindingId, destroyed)",
    "processCarrierLossCandidate(player)",
    "findBoundWeaponByBinding",
    "carrier_destroyed_maid_released",
):
    assert token in deployment, f"missing carrier-loss safeguard: {token}"

destroy_handler = deployment[
    deployment.index("public static void onContractCarrierDestroyed")
    : deployment.index("@SubscribeEvent", deployment.index("public static void onContractCarrierDestroyed") + 20)
]
assert "scheduleCarrierLoss" in destroy_handler
assert "maid.discard()" not in destroy_handler
assert "createEmergencyResurrectionFilm" not in destroy_handler

resolver = deployment[
    deployment.index("private static boolean resolveConfirmedCarrierLoss")
    : deployment.index("public static boolean manifestRequested")
]
assert "ContractLifecycleService.manifest(player, transientCarrier, false)" in resolver
assert "ContractMaidKeys.EMERGENCY_FILM_PROGRESS" in resolver
assert 'snapshot.getTag().getCompound("MaidData").copy()' in resolver
assert "ContractMaidRuntimeService.cleanupBeforeRecall(player, snapshot, maid)" in resolver
assert "remove(TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID)" in resolver
assert "ContractCompanionState.clear(maid)" in resolver
assert "maid.discard()" not in resolver
assert "createEmergencyResurrectionFilm" in resolver, (
    "recovery film must remain a last-resort fallback when stored manifestation fails"
)

assert "public static ItemStack findBoundWeaponByBinding" in locator
for carrier_surface in (
    "player.getInventory().getContainerSize()",
    "player.getOffhandItem()",
    "player.containerMenu.getCarried()",
    "player.containerMenu.slots",
):
    assert carrier_surface in locator, f"binding lookup misses carrier surface: {carrier_surface}"

assert "public static void clear(Entity maid)" in companion
assert 'Map<UUID, Map<String, CarrierLossCandidate>>' in deployment
assert 'cancelCarrierLoss(player, active.bindingId())' in deployment
assert 'pending.get(bindingId)' in deployment and 'pending.put(bindingId' in deployment
cleanup = deployment.split('private static void clearCarrierLossRuntime', 1)[1].split(
    'private static long carrierLossClock', 1)[0]
assert 'active.bindingId().equals(candidate.bindingId())' in cleanup
assert 'candidate.maidId().equals(ContractRecoveryService.currentMaidId(player))' in cleanup
assert "maid.getPersistentData().remove(KEY)" in companion

film_method = film_service[film_service.index("public static ItemStack createEmergencyResurrectionFilm") :]
for inventory_key in ("MaidInventory", "MaidBaubleInventory", "MaidExperience", "ArmorItems", "HandItems"):
    assert f'remove("{inventory_key}")' not in film_method, (
        f"fallback recovery film must retain {inventory_key}"
    )

assert "maid.discard();" not in deployment, (
    "carrier loss must never delete a live maid; losing a weapon is not maid death"
)

print("Carrier-loss grace period, exact binding recovery, live detach and stored-maid fallback validated.")
